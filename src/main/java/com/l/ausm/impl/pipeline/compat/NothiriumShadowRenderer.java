package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Draws Nothirium's prepared chunk VBOs with AUSM's active shader.
 *
 * Nothirium owns the normal terrain visibility lists. Calling its setup from
 * the shadow camera path corrupts that state, while calling its render method
 * binds Nothirium's own shader and normal camera matrix. This bridge only reads
 * the already prepared lists and emits vanilla-layout VBO draws.
 */
public final class NothiriumShadowRenderer {

    private static final String NOTHIRIUM_MOD_ID = "nothirium";
    private static final int VANILLA_BLOCK_STRIDE = 28;
    private static final int POSITION_OFFSET = 0;
    private static final int COLOR_OFFSET = 12;
    private static final int TEX_COORD_OFFSET = 16;
    private static final int LIGHT_COORD_OFFSET = 24;
    private static final int MAX_SHADOW_COMPILES_PER_FRAME = 8;
    private static final int MAX_PENDING_SHADOW_COMPILES = 64;
    private static final int MAX_CHUNK_REFRESH_COMPILES = 16;
    private static final int MAX_CHUNK_REFRESH_AUDIT_LOGS = 16;
    private static final long REFLECTION_RETRY_DELAY_MS = 1000L;
    private static Reflection reflection;
    private static long nextReflectionAttemptMillis;

    private boolean disabled;
    private boolean warned;
    private boolean emptyAuditLogged;
    private boolean providerAuditLogged;
    private boolean providerSuccessAuditLogged;
    private boolean fallbackAuditLogged;
    private boolean uploadNonEmptyLogged;
    private int providerZeroAuditAttempts;
    private int uploadAuditAttempts;
    private int compileAuditAttempts;
    private int chunkRefreshAuditAttempts;
    private int visibleTranslucentAuditAttempts;

    public static boolean isAvailable() {
        return reflection() != null;
    }

    public void drainUploads() {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return;
        }

        try {
            Object dispatcher = reflection.getTaskDispatcher.invoke(null);
            if (dispatcher == null) {
                return;
            }

            int before = reflection.dispatcherQueueSize(dispatcher);
            reflection.dispatcherUpdate.invoke(dispatcher);
            int after = reflection.dispatcherQueueSize(dispatcher);
            auditUploadDrain(dispatcher, before, after);
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
        }
    }

    public boolean refreshChunkColumn(int chunkX, int chunkZ) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return false;
        }

        try {
            Object provider = reflection.getProvider.invoke(null);
            if (provider == null) {
                return false;
            }

            Object chunksObject = reflection.providerChunks.get(provider);
            if (!(chunksObject instanceof Object[] chunks) || chunks.length == 0) {
                return false;
            }

            Object renderer = reflection.getRenderer.invoke(null);
            Object dispatcher = reflection.getTaskDispatcher.invoke(null);
            ChunkRefreshStats stats = new ChunkRefreshStats(chunkX, chunkZ);
            for (Object chunk : chunks) {
                stats.total++;
                if (chunk == null) {
                    stats.nullChunks++;
                    continue;
                }

                int sectionX = ((Integer) reflection.getX.invoke(chunk)) >> 4;
                int sectionZ = ((Integer) reflection.getZ.invoke(chunk)) >> 4;
                if (sectionX != chunkX || sectionZ != chunkZ) {
                    continue;
                }

                stats.matched++;
                if (reflection.isChunkDirty(chunk)) {
                    stats.alreadyDirty++;
                }
                if (futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
                    stats.running++;
                }

                reflection.releaseBuffers.invoke(chunk);
                stats.released++;
                reflection.markDirty.invoke(chunk);
                stats.marked++;

                if (renderer == null || dispatcher == null) {
                    stats.noDispatcher++;
                    continue;
                }
                if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                    stats.cannotCompile++;
                    continue;
                }
                stats.canCompile++;
                if (stats.scheduled >= MAX_CHUNK_REFRESH_COMPILES) {
                    stats.deferred++;
                    continue;
                }

                reflection.compileAsync.invoke(chunk, renderer, dispatcher);
                stats.scheduled++;
            }

            if (stats.scheduled > 0 && dispatcher != null) {
                reflection.dispatcherUpdate.invoke(dispatcher);
            }
            auditChunkRefresh(stats);
            return stats.matched > 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return false;
        }
    }

    public int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance) {
        return renderLayer(layer, cameraX, cameraY, cameraZ, maxDistance, false, true, false);
    }

    public int renderVisibleLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                  int fallbackBlockEntityId, short fallbackRenderType) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return -1;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return -1;
        }

        try {
            Object renderer = reflection.getRenderer.invoke(null);
            if (renderer == null) {
                return -1;
            }

            Object chunksByPass = reflection.chunks.get(renderer);
            if (chunksByPass == null) {
                return -1;
            }

            Object chunksObject = reflection.enumMapGet.invoke(chunksByPass, pass);
            if (!(chunksObject instanceof List<?> chunks)) {
                return -1;
            }

            boolean requirePipelineStride = layer != BlockRenderLayer.TRANSLUCENT;
            DrawStats stats = drawChunks(reflection, pass, chunks, cameraX, cameraY, cameraZ, -1.0D, false,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
            if (stats.unsupportedStride > 0) {
                refreshUnsupportedPipelineChunks(reflection, stats.unsupportedPipelineChunks);
            }
            auditVisibleTranslucentLayer(layer, stats, fallbackBlockEntityId, fallbackRenderType);
            if (stats.drawn == 0 && stats.unsupportedStride > 0) {
                return -1;
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return -1;
        }
    }

    private int renderLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ, double maxDistance,
                            boolean scheduleCompiles, boolean audit, boolean visibleOnly) {
        Reflection reflection = reflection();
        if (disabled || reflection == null) {
            return 0;
        }

        Object pass = reflection.passFor(layer);
        if (pass == null) {
            return 0;
        }

        try {
            if (!visibleOnly) {
                Object provider = reflection.getProvider.invoke(null);
                if (provider != null) {
                    Object chunksArray = reflection.providerChunks.get(provider);
                    if (chunksArray instanceof Object[] chunks && chunks.length > 0) {
                        if (scheduleCompiles && layer == BlockRenderLayer.SOLID) {
                            scheduleShadowCompiles(reflection, Arrays.asList(chunks), cameraX, cameraY, cameraZ, maxDistance);
                        }
                        boolean collectState = audit && layer == BlockRenderLayer.SOLID
                                && (!providerSuccessAuditLogged || providerZeroAuditAttempts < 8);
                        DrawStats stats = drawChunks(reflection, pass, Arrays.asList(chunks), cameraX, cameraY, cameraZ,
                                maxDistance, collectState, 0, (short) 0, false);
                        if (audit) {
                            auditDrawStats("provider", layer, stats);
                        }
                        if (stats.drawn > 0) {
                            return stats.drawn;
                        }
                    }
                }
            }

            Object renderer = reflection.getRenderer.invoke(null);
            if (renderer == null) {
                if (audit) {
                    auditEmpty(layer, null, null, null);
                }
                return 0;
            }

            Object chunksByPass = reflection.chunks.get(renderer);
            if (chunksByPass == null) {
                if (audit) {
                    auditEmpty(layer, renderer, pass, null);
                }
                return 0;
            }

            Object chunksObject = reflection.enumMapGet.invoke(chunksByPass, pass);
            if (!(chunksObject instanceof List<?> chunks) || chunks.isEmpty()) {
                if (audit) {
                    auditEmpty(layer, renderer, pass, chunksObject instanceof List<?> list ? list : null);
                }
                return 0;
            }

            DrawStats stats = drawChunks(reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, false,
                    0, (short) 0, false);
            if (audit) {
                auditDrawStats("fallback", layer, stats);
            }
            return stats.drawn;
        } catch (ReflectiveOperationException | RuntimeException e) {
            disabled = true;
            warnOnce(e);
            return 0;
        }
    }

    private void scheduleShadowCompiles(Reflection reflection, Iterable<?> chunks,
                                        double cameraX, double cameraY, double cameraZ, double maxDistance)
            throws ReflectiveOperationException {
        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return;
        }

        CompileStats stats = new CompileStats();
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        for (Object chunk : chunks) {
            stats.total++;
            if (chunk == null) {
                stats.nullChunks++;
                continue;
            }

            int chunkX = (Integer) reflection.getX.invoke(chunk);
            int chunkY = (Integer) reflection.getY.invoke(chunk);
            int chunkZ = (Integer) reflection.getZ.invoke(chunk);
            stats.captureFirstChunk(chunkX, chunkY, chunkZ);
            if (maxDistanceSquared >= 0.0D) {
                double dx = chunkX + 8.0D - cameraX;
                double dy = chunkY + 8.0D - cameraY;
                double dz = chunkZ + 8.0D - cameraZ;
                if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                    stats.distanceCulled++;
                    continue;
                }
            }
            stats.withinDistance++;

            Object future = reflection.lastCompileTaskResult(chunk);
            if (futureIsRunning(future)) {
                stats.running++;
                if (stats.running >= MAX_PENDING_SHADOW_COMPILES) {
                    break;
                }
                continue;
            }

            if (!reflection.isChunkDirty(chunk)) {
                stats.clean++;
                continue;
            }
            stats.dirty++;

            if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                stats.cannotCompile++;
                continue;
            }
            stats.canCompile++;

            reflection.compileAsync.invoke(chunk, renderer, dispatcher);
            stats.scheduled++;
            if (stats.scheduled >= MAX_SHADOW_COMPILES_PER_FRAME) {
                break;
            }
        }
        auditCompileStats(stats);
    }

    private static boolean futureIsRunning(Object futureObject) {
        return futureObject instanceof CompletableFuture<?> future && !future.isDone();
    }

    private DrawStats drawChunks(Reflection reflection, Object pass, Iterable<?> chunks,
                                 double cameraX, double cameraY, double cameraZ, double maxDistance, boolean collectState,
                                 int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        DrawStats stats = new DrawStats();
        int previousVbo = -1;
        int previousVboSize = 0;
        int previousStride = -1;
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;

        try {
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            for (Object chunk : chunks) {
                stats.total++;
                if (chunk == null) {
                    stats.nullChunks++;
                    continue;
                }

                int chunkX = (Integer) reflection.getX.invoke(chunk);
                int chunkY = (Integer) reflection.getY.invoke(chunk);
                int chunkZ = (Integer) reflection.getZ.invoke(chunk);
                stats.captureFirstChunk(chunkX, chunkY, chunkZ);
                if (collectState) {
                    stats.captureState(reflection, chunk, chunkX, chunkY, chunkZ);
                }
                if (maxDistanceSquared >= 0.0D) {
                    double dx = chunkX + 8.0D - cameraX;
                    double dy = chunkY + 8.0D - cameraY;
                    double dz = chunkZ + 8.0D - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                        stats.distanceCulled++;
                        continue;
                    }
                }
                stats.withinDistance++;

                Object part = reflection.getVboPart.invoke(chunk, pass);
                if (part == null) {
                    stats.missingPart++;
                    continue;
                }
                stats.partPresent++;
                if (!(Boolean) reflection.isValid.invoke(part)) {
                    stats.invalidPart++;
                    continue;
                }
                stats.validPart++;

                int count = (Integer) reflection.getCount.invoke(part);
                if (count <= 0) {
                    stats.emptyCount++;
                    continue;
                }
                stats.positiveCount++;

                int vbo = (Integer) reflection.getVbo.invoke(part);
                if (vbo <= 0) {
                    stats.badVbo++;
                    continue;
                }
                stats.positiveVbo++;

                int offset = (Integer) reflection.getOffset.invoke(part);
                int size = (Integer) reflection.getSize.invoke(part);
                int stride = vertexStride(size, count);
                if (stride <= 0) {
                    stats.badStride++;
                    continue;
                }
                boolean pipelineStride = isPipelineBlockStride(stride);
                if (!pipelineStride) {
                    stats.unsupportedStride++;
                    stats.captureUnsupportedPipelineChunk(chunk);
                    if (requirePipelineStride) {
                        continue;
                    }
                }

                if (vbo != previousVbo || stride != previousStride) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                    setupArrayPointers(stride, fallbackBlockEntityId, fallbackRenderType);
                    previousVbo = vbo;
                    previousStride = stride;
                    previousVboSize = GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE);
                }

                int first = (Integer) reflection.getFirst.invoke(part);
                stats.captureFirstPart(vbo, first, count, offset, size, stride, previousVboSize);
                if (!validDrawRange(offset, size, previousVboSize)) {
                    stats.invalidRange++;
                    continue;
                }

                GL11.glPushMatrix();
                try {
                    PipelineContext.getInstance().applyChunkFade(chunkX, chunkY, chunkZ);
                    GL11.glTranslated(chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ);
                    GL11.glDrawArrays(GL11.GL_QUADS, first, count);
                } finally {
                    GL11.glPopMatrix();
                }
                stats.drawn++;
            }
        } finally {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            resetClientArrayState();
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            PipelineContext.getInstance().resetChunkFadeUniform();
        }

        return stats;
    }

    private void refreshUnsupportedPipelineChunks(Reflection reflection, List<Object> chunks)
            throws ReflectiveOperationException {
        if (chunks.isEmpty()) {
            return;
        }

        Object renderer = reflection.getRenderer.invoke(null);
        Object dispatcher = reflection.getTaskDispatcher.invoke(null);
        if (renderer == null || dispatcher == null) {
            return;
        }

        int scheduled = 0;
        for (Object chunk : chunks) {
            if (chunk == null || scheduled >= MAX_CHUNK_REFRESH_COMPILES) {
                continue;
            }
            if (futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
                continue;
            }

            reflection.releaseBuffers.invoke(chunk);
            reflection.markDirty.invoke(chunk);
            if (Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                reflection.compileAsync.invoke(chunk, renderer, dispatcher);
                scheduled++;
            }
        }

        if (scheduled > 0) {
            reflection.dispatcherUpdate.invoke(dispatcher);
        }
    }

    private static int vertexStride(int size, int count) {
        if (size <= 0 || count <= 0 || size % count != 0) {
            return VANILLA_BLOCK_STRIDE;
        }
        int stride = size / count;
        return stride >= LIGHT_COORD_OFFSET + 4 ? stride : -1;
    }

    private static boolean validDrawRange(int offset, int size, int bufferSize) {
        if (offset < 0 || size <= 0 || bufferSize <= 0) {
            return false;
        }
        long endByte = (long) offset + size;
        return endByte >= 0L && endByte <= bufferSize;
    }

    private static void setupArrayPointers(int stride, int fallbackBlockEntityId, short fallbackRenderType) {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, stride, POSITION_OFFSET);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, COLOR_OFFSET);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, stride, TEX_COORD_OFFSET);

        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, stride, LIGHT_COORD_OFFSET);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        if (isPipelineBlockStride(stride)) {
            setupPipelineAttributes(stride);
        } else {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            setGenericAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                    fallbackBlockEntityId & 0xFFFF,
                    fallbackRenderType,
                    0.0F,
                    0.0F);
            setGenericAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 1.0F);
            setGenericAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1.0F, 0.0F, 0.0F, 1.0F);
            setGenericAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        }
    }

    private static boolean isPipelineBlockStride(int stride) {
        ensurePipelineBlockFormat();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && stride == ExtendedVertexFormats.PIPELINE_BLOCK.getSize();
    }

    private static void ensurePipelineBlockFormat() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
    }

    private static void setupPipelineAttributes(int stride) {
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET
        );
    }

    private void auditVisibleTranslucentLayer(BlockRenderLayer layer, DrawStats stats,
                                             int fallbackBlockEntityId, short fallbackRenderType) {
        if (layer != BlockRenderLayer.TRANSLUCENT || visibleTranslucentAuditAttempts >= 8) {
            return;
        }

        visibleTranslucentAuditAttempts++;
        MainMod.LOGGER.info(
                "[NothiriumWaterAudit] attempt={} total={} null={} part={} valid={} count={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={}",
                visibleTranslucentAuditAttempts,
                stats.total,
                stats.nullChunks,
                stats.partPresent,
                stats.validPart,
                stats.positiveCount,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart
        );
    }

    private static void resetClientArrayState() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    private static void setGenericAttribute(int index, float x, float y, float z, float w) {
        if (index >= 0 && index < GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)) {
            GL20.glVertexAttrib4f(index, x, y, z, w);
        }
    }

    private void auditCompileStats(CompileStats stats) {
        if (compileAuditAttempts >= 8) {
            return;
        }
        if (stats.scheduled <= 0 && stats.running <= 0 && stats.dirty <= 0 && stats.canCompile <= 0) {
            return;
        }
        compileAuditAttempts++;
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] scheduledCompiles attempt={} total={} null={} within={} distCull={} dirty={} clean={} canCompile={} cannotCompile={} running={} scheduled={} firstChunk={}",
                compileAuditAttempts,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.dirty,
                stats.clean,
                stats.canCompile,
                stats.cannotCompile,
                stats.running,
                stats.scheduled,
                stats.firstChunk
        );
    }

    private void auditChunkRefresh(ChunkRefreshStats stats) {
        if (stats.matched <= 0) {
            return;
        }
        if (chunkRefreshAuditAttempts >= MAX_CHUNK_REFRESH_AUDIT_LOGS) {
            return;
        }
        chunkRefreshAuditAttempts++;
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] refreshedChunkColumn attempt={} chunk={},{} total={} null={} matched={} alreadyDirty={} running={} released={} marked={} canCompile={} cannotCompile={} noDispatcher={} scheduled={} deferred={}",
                chunkRefreshAuditAttempts,
                stats.chunkX,
                stats.chunkZ,
                stats.total,
                stats.nullChunks,
                stats.matched,
                stats.alreadyDirty,
                stats.running,
                stats.released,
                stats.marked,
                stats.canCompile,
                stats.cannotCompile,
                stats.noDispatcher,
                stats.scheduled,
                stats.deferred
        );
    }

    private void auditUploadDrain(Object dispatcher, int before, int after) {
        if (before > 0 || after > 0) {
            if (uploadNonEmptyLogged) {
                return;
            }
            uploadNonEmptyLogged = true;
        } else {
            if (uploadAuditAttempts >= 8) {
                return;
            }
            uploadAuditAttempts++;
        }
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] drainedUploads attempt={} dispatcher={} queueBefore={} queueAfter={}",
                uploadAuditAttempts,
                dispatcher.getClass().getName(),
                before,
                after
        );
    }

    private void auditDrawStats(String source, BlockRenderLayer layer, DrawStats stats) {
        if (source.equals("provider")) {
            if (layer != BlockRenderLayer.SOLID) {
                return;
            }
            if (stats.drawn > 0) {
                if (providerSuccessAuditLogged) {
                    return;
                }
                providerSuccessAuditLogged = true;
            } else {
                if (providerZeroAuditAttempts >= 8) {
                    return;
                }
                providerZeroAuditAttempts++;
            }
        } else {
            if (fallbackAuditLogged) {
                return;
            }
            fallbackAuditLogged = true;
        }

        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] source={} layer={} attempt={} total={} null={} within={} distCull={} part={} valid={} count={} vbo={} badStride={} rangeSkip={} drawn={} firstChunk={} firstPart={} state={}",
                source,
                layer,
                source.equals("provider") ? providerZeroAuditAttempts : -1,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.partPresent,
                stats.validPart,
                stats.positiveCount,
                stats.positiveVbo,
                stats.badStride,
                stats.invalidRange,
                stats.drawn,
                stats.firstChunk,
                stats.firstPart,
                stats.stateSummary()
        );
    }

    private void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        MainMod.LOGGER.warn("[NothiriumCompat] Disabled shadow VBO bridge after an error", e);
    }

    private void auditEmpty(BlockRenderLayer layer, Object renderer, Object pass, List<?> chunks) {
        if (emptyAuditLogged) {
            return;
        }
        emptyAuditLogged = true;

        Reflection reflection = reflection();
        int renderedChunks = -1;
        int renderedSections = -1;
        int totalRenderedSections = -1;
        try {
            if (reflection != null && renderer != null && pass != null) {
                renderedChunks = (Integer) reflection.renderedChunks.invoke(renderer, pass);
                renderedSections = (Integer) reflection.renderedSections.invoke(null, pass);
                totalRenderedSections = (Integer) reflection.renderedSectionsAll.invoke(null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MainMod.LOGGER.debug("[NothiriumShadowBridge] Empty-list audit failed", e);
        }

        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] layer={} renderer={} listSize={} renderedChunks={} renderedSections={} totalRenderedSections={}",
                layer,
                renderer != null ? renderer.getClass().getName() : "null",
                chunks != null ? chunks.size() : -1,
                renderedChunks,
                renderedSections,
                totalRenderedSections
        );
    }

    private static Reflection reflection() {
        Reflection existing = reflection;
        if (existing != null) {
            return existing;
        }
        if (!Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now < nextReflectionAttemptMillis) {
            return null;
        }
        nextReflectionAttemptMillis = now + REFLECTION_RETRY_DELAY_MS;
        Reflection loaded = Reflection.load();
        if (loaded != null) {
            reflection = loaded;
        }
        return loaded;
    }

    private static final class CompileStats {
        private int total;
        private int nullChunks;
        private int withinDistance;
        private int distanceCulled;
        private int dirty;
        private int clean;
        private int canCompile;
        private int cannotCompile;
        private int running;
        private int scheduled;
        private String firstChunk = "n/a";

        private void captureFirstChunk(int x, int y, int z) {
            if (firstChunk.equals("n/a")) {
                firstChunk = x + "," + y + "," + z;
            }
        }
    }

    private static final class ChunkRefreshStats {
        private final int chunkX;
        private final int chunkZ;
        private int total;
        private int nullChunks;
        private int matched;
        private int alreadyDirty;
        private int running;
        private int released;
        private int marked;
        private int canCompile;
        private int cannotCompile;
        private int noDispatcher;
        private int scheduled;
        private int deferred;

        private ChunkRefreshStats(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static final class DrawStats {
        private int total;
        private int nullChunks;
        private int withinDistance;
        private int distanceCulled;
        private int missingPart;
        private int partPresent;
        private int invalidPart;
        private int validPart;
        private int emptyCount;
        private int positiveCount;
        private int badVbo;
        private int positiveVbo;
        private int badStride;
        private int unsupportedStride;
        private int invalidRange;
        private int drawn;
        private String firstChunk = "n/a";
        private String firstPart = "n/a";
        private String firstState = "n/a";
        private int dirtyChunks;
        private int cleanChunks;
        private int emptyChunks;
        private int nonEmptyChunks;
        private int canCompileChunks;
        private int cannotCompileChunks;
        private int taskPresent;
        private int futureNull;
        private int futureRunning;
        private int futureDone;
        private int futureCancelled;
        private int futureExceptional;
        private int recordedChunks;
        private int enqueuedChunks;
        private int maxRecorded = -1;
        private int maxEnqueued = -1;
        private int nonemptyMaskChunks;
        private final List<Object> unsupportedPipelineChunks = new ArrayList<>();

        private void captureFirstChunk(int x, int y, int z) {
            if (firstChunk.equals("n/a")) {
                firstChunk = x + "," + y + "," + z;
            }
        }

        private void captureFirstPart(int vbo, int first, int count, int offset, int size, int stride, int vboSize) {
            if (firstPart.equals("n/a")) {
                firstPart = "vbo=" + vbo
                        + " first=" + first
                        + " count=" + count
                        + " offset=" + offset
                        + " size=" + size
                        + " stride=" + stride
                        + " vboSize=" + vboSize;
            }
        }

        private void captureUnsupportedPipelineChunk(Object chunk) {
            if (unsupportedPipelineChunks.size() < MAX_CHUNK_REFRESH_COMPILES) {
                unsupportedPipelineChunks.add(chunk);
            }
        }

        private void captureState(Reflection reflection, Object chunk, int x, int y, int z)
                throws ReflectiveOperationException {
            boolean dirty = reflection.isChunkDirty(chunk);
            boolean empty = reflection.isChunkEmpty(chunk);
            Boolean canCompile = reflection.canCompile(chunk);
            Object task = reflection.lastCompileTask(chunk);
            Object futureObject = reflection.lastCompileTaskResult(chunk);
            int recorded = reflection.lastTimeRecorded(chunk);
            int enqueued = reflection.lastTimeEnqueued(chunk);
            int nonemptyMask = reflection.nonemptyVboParts(chunk);

            if (dirty) {
                dirtyChunks++;
            } else {
                cleanChunks++;
            }
            if (empty) {
                emptyChunks++;
            } else {
                nonEmptyChunks++;
            }
            if (Boolean.TRUE.equals(canCompile)) {
                canCompileChunks++;
            } else if (Boolean.FALSE.equals(canCompile)) {
                cannotCompileChunks++;
            }
            if (task != null) {
                taskPresent++;
            }
            String future = futureState(futureObject);
            switch (future) {
                case "null" -> futureNull++;
                case "running" -> futureRunning++;
                case "done" -> futureDone++;
                case "cancelled" -> futureCancelled++;
                case "exceptional" -> futureExceptional++;
                default -> {
                }
            }
            if (recorded >= 0) {
                recordedChunks++;
                maxRecorded = Math.max(maxRecorded, recorded);
            }
            if (enqueued >= 0) {
                enqueuedChunks++;
                maxEnqueued = Math.max(maxEnqueued, enqueued);
            }
            if (nonemptyMask != 0) {
                nonemptyMaskChunks++;
            }
            if (firstState.equals("n/a")) {
                firstState = "pos=" + x + "," + y + "," + z
                        + " dirty=" + dirty
                        + " empty=" + empty
                        + " canCompile=" + canCompile
                        + " task=" + (task != null)
                        + " future=" + future
                        + " recorded=" + recorded
                        + " enqueued=" + enqueued
                        + " nonemptyMask=" + nonemptyMask;
            }
        }

        private String stateSummary() {
            if (firstState.equals("n/a")) {
                return "n/a";
            }
            return "dirty=" + dirtyChunks
                    + " clean=" + cleanChunks
                    + " empty=" + emptyChunks
                    + " nonEmpty=" + nonEmptyChunks
                    + " canCompile=" + canCompileChunks
                    + " cannotCompile=" + cannotCompileChunks
                    + " task=" + taskPresent
                    + " future=null/" + futureNull
                    + ",running/" + futureRunning
                    + ",done/" + futureDone
                    + ",cancelled/" + futureCancelled
                    + ",exceptional/" + futureExceptional
                    + " recorded=" + recordedChunks + "(max=" + maxRecorded + ")"
                    + " enqueued=" + enqueuedChunks + "(max=" + maxEnqueued + ")"
                    + " nonemptyMask=" + nonemptyMaskChunks
                    + " first={" + firstState + "}";
        }

        private static String futureState(Object futureObject) {
            if (!(futureObject instanceof CompletableFuture<?> future)) {
                return futureObject == null ? "null" : futureObject.getClass().getSimpleName();
            }
            if (future.isCancelled()) {
                return "cancelled";
            }
            if (future.isCompletedExceptionally()) {
                return "exceptional";
            }
            return future.isDone() ? "done" : "running";
        }
    }

    private static final class Reflection {
        private final Method getRenderer;
        private final Method getProvider;
        private final Method getTaskDispatcher;
        private final Method dispatcherUpdate;
        private final Method enumMapGet;
        private final Method renderedChunks;
        private final Method renderedSections;
        private final Method renderedSectionsAll;
        private final Method getVboPart;
        private final Method getVbo;
        private final Method getFirst;
        private final Method getCount;
        private final Method getOffset;
        private final Method getSize;
        private final Method isValid;
        private final Method isDirty;
        private final Method isEmpty;
        private final Method markDirty;
        private final Method releaseBuffers;
        private final Method canCompile;
        private final Method compileAsync;
        private final Method getX;
        private final Method getY;
        private final Method getZ;
        private final Field chunks;
        private final Field providerChunks;
        private final Field dispatcherQueue;
        private final Field lastCompileTask;
        private final Field lastCompileTaskResult;
        private final Field lastTimeRecorded;
        private final Field lastTimeEnqueued;
        private final Field nonemptyVboParts;
        private final Object solid;
        private final Object cutout;
        private final Object cutoutMipped;
        private final Object translucent;

        private Reflection(Method getRenderer, Method getProvider, Method getTaskDispatcher, Method dispatcherUpdate,
                           Method enumMapGet, Method renderedChunks, Method renderedSections,
                           Method renderedSectionsAll, Method getVboPart, Method getVbo, Method getFirst,
                           Method getCount, Method getOffset, Method getSize, Method isValid, Method isDirty,
                           Method isEmpty, Method markDirty, Method releaseBuffers, Method canCompile,
                           Method compileAsync, Method getX, Method getY, Method getZ, Field chunks,
                           Field providerChunks, Field dispatcherQueue, Field lastCompileTask,
                           Field lastCompileTaskResult, Field lastTimeRecorded, Field lastTimeEnqueued, Field nonemptyVboParts,
                           Object solid, Object cutout, Object cutoutMipped, Object translucent) {
            this.getRenderer = getRenderer;
            this.getProvider = getProvider;
            this.getTaskDispatcher = getTaskDispatcher;
            this.dispatcherUpdate = dispatcherUpdate;
            this.enumMapGet = enumMapGet;
            this.renderedChunks = renderedChunks;
            this.renderedSections = renderedSections;
            this.renderedSectionsAll = renderedSectionsAll;
            this.getVboPart = getVboPart;
            this.getVbo = getVbo;
            this.getFirst = getFirst;
            this.getCount = getCount;
            this.getOffset = getOffset;
            this.getSize = getSize;
            this.isValid = isValid;
            this.isDirty = isDirty;
            this.isEmpty = isEmpty;
            this.markDirty = markDirty;
            this.releaseBuffers = releaseBuffers;
            this.canCompile = canCompile;
            this.compileAsync = compileAsync;
            this.getX = getX;
            this.getY = getY;
            this.getZ = getZ;
            this.chunks = chunks;
            this.providerChunks = providerChunks;
            this.dispatcherQueue = dispatcherQueue;
            this.lastCompileTask = lastCompileTask;
            this.lastCompileTaskResult = lastCompileTaskResult;
            this.lastTimeRecorded = lastTimeRecorded;
            this.lastTimeEnqueued = lastTimeEnqueued;
            this.nonemptyVboParts = nonemptyVboParts;
            this.solid = solid;
            this.cutout = cutout;
            this.cutoutMipped = cutoutMipped;
            this.translucent = translucent;
        }

        private static Reflection load() {
            try {
                Class<?> managerClass = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager");
                Class<?> rendererBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractChunkRenderer");
                Class<?> providerBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunkProvider");
                Class<?> abstractChunkClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunk");
                Class<?> chunkRendererClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IChunkRenderer");
                Class<?> dispatcherClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunkDispatcher");
                Class<?> renderChunkClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunk");
                Class<?> vboPartClass = Class.forName("meldexun.nothirium.api.renderer.IVBOPart");
                Class<?> passClass = Class.forName("meldexun.nothirium.api.renderer.chunk.ChunkRenderPass");
                Class<?> enumMapClass = Class.forName("meldexun.nothirium.util.collection.Enum2ObjMap");

                Method getRenderer = managerClass.getMethod("getRenderer");
                Method getProvider = managerClass.getMethod("getProvider");
                Method getTaskDispatcher = managerClass.getMethod("getTaskDispatcher");
                Method dispatcherUpdate = dispatcherClass.getMethod("update");
                Method renderedSections = managerClass.getMethod("renderedSections", passClass);
                Method renderedSectionsAll = managerClass.getMethod("renderedSections");
                Method enumMapGet = enumMapClass.getMethod("get", Enum.class);
                Method renderedChunks = rendererBaseClass.getMethod("renderedChunks", passClass);
                Method getVboPart = renderChunkClass.getMethod("getVBOPart", passClass);
                Method getVbo = vboPartClass.getMethod("getVBO");
                Method getFirst = vboPartClass.getMethod("getFirst");
                Method getCount = vboPartClass.getMethod("getCount");
                Method getOffset = vboPartClass.getMethod("getOffset");
                Method getSize = vboPartClass.getMethod("getSize");
                Method isValid = vboPartClass.getMethod("isValid");
                Method isDirty = abstractChunkClass.getMethod("isDirty");
                Method isEmpty = renderChunkClass.getMethod("isEmpty");
                Method markDirty = abstractChunkClass.getMethod("markDirty");
                Method releaseBuffers = abstractChunkClass.getMethod("releaseBuffers");
                Method canCompile = abstractChunkClass.getDeclaredMethod("canCompile");
                canCompile.setAccessible(true);
                Method compileAsync = abstractChunkClass.getMethod("compileAsync", chunkRendererClass, dispatcherClass);
                Method getX = renderChunkClass.getMethod("getX");
                Method getY = renderChunkClass.getMethod("getY");
                Method getZ = renderChunkClass.getMethod("getZ");
                Field chunks = findField(rendererBaseClass, "chunks");
                chunks.setAccessible(true);
                Field providerChunks = findField(providerBaseClass, "chunks");
                providerChunks.setAccessible(true);
                Field dispatcherQueue = findOptionalDispatcherQueueField();
                Field lastCompileTask = findField(abstractChunkClass, "lastCompileTask");
                lastCompileTask.setAccessible(true);
                Field lastCompileTaskResult = findField(abstractChunkClass, "lastCompileTaskResult");
                lastCompileTaskResult.setAccessible(true);
                Field lastTimeRecorded = findField(abstractChunkClass, "lastTimeRecorded");
                lastTimeRecorded.setAccessible(true);
                Field lastTimeEnqueued = findField(abstractChunkClass, "lastTimeEnqueued");
                lastTimeEnqueued.setAccessible(true);
                Field nonemptyVboParts = findField(abstractChunkClass, "nonemptyVboParts");
                nonemptyVboParts.setAccessible(true);

                Object solid = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "SOLID");
                Object cutout = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT");
                Object cutoutMipped = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT_MIPPED");
                Object translucent = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "TRANSLUCENT");
                return new Reflection(
                        getRenderer,
                        getProvider,
                        getTaskDispatcher,
                        dispatcherUpdate,
                        enumMapGet,
                        renderedChunks,
                        renderedSections,
                        renderedSectionsAll,
                        getVboPart,
                        getVbo,
                        getFirst,
                        getCount,
                        getOffset,
                        getSize,
                        isValid,
                        isDirty,
                        isEmpty,
                        markDirty,
                        releaseBuffers,
                        canCompile,
                        compileAsync,
                        getX,
                        getY,
                        getZ,
                        chunks,
                        providerChunks,
                        dispatcherQueue,
                        lastCompileTask,
                        lastCompileTaskResult,
                        lastTimeRecorded,
                        lastTimeEnqueued,
                        nonemptyVboParts,
                        solid,
                        cutout,
                        cutoutMipped,
                        translucent
                );
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        private boolean isChunkDirty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isDirty.invoke(chunk);
        }

        private boolean isChunkEmpty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isEmpty.invoke(chunk);
        }

        private Boolean canCompile(Object chunk) throws ReflectiveOperationException {
            return (Boolean) canCompile.invoke(chunk);
        }

        private Object lastCompileTask(Object chunk) throws IllegalAccessException {
            return lastCompileTask.get(chunk);
        }

        private Object lastCompileTaskResult(Object chunk) throws IllegalAccessException {
            return lastCompileTaskResult.get(chunk);
        }

        private int lastTimeRecorded(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeRecorded.get(chunk);
        }

        private int lastTimeEnqueued(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeEnqueued.get(chunk);
        }

        private int nonemptyVboParts(Object chunk) throws IllegalAccessException {
            return (Integer) nonemptyVboParts.get(chunk);
        }

        private int dispatcherQueueSize(Object dispatcher) throws IllegalAccessException {
            if (dispatcherQueue == null || !dispatcherQueue.getDeclaringClass().isInstance(dispatcher)) {
                return -1;
            }

            Object queue = dispatcherQueue.get(dispatcher);
            return queue instanceof Collection<?> collection ? collection.size() : -1;
        }

        private Object passFor(BlockRenderLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case CUTOUT_MIPPED -> cutoutMipped;
                case TRANSLUCENT -> translucent;
            };
        }

        private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }

        private static Field findOptionalDispatcherQueueField() {
            try {
                Class<?> dispatcherImplClass = Class.forName("meldexun.nothirium.mc.renderer.chunk.RenderChunkDispatcher");
                Field field = findField(dispatcherImplClass, "taskQueue");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }
}
