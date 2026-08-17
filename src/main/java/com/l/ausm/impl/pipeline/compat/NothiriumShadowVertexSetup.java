package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

abstract class NothiriumShadowVertexSetup extends NothiriumShadowCompileScheduling {
    protected static void setupArrayPointers(int stride, int fallbackBlockEntityId, short fallbackRenderType) {
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);

        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187420_d", "glVertexPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 3, GL11.GL_FLOAT, stride, POSITION_OFFSET);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187406_e", "glColorPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 4, GL11.GL_UNSIGNED_BYTE, stride, COLOR_OFFSET);
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, stride, TEX_COORD_OFFSET);

        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, stride, LIGHT_COORD_OFFSET);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());

        if (NothiriumShadowRenderer.isPipelineBlockStride(stride)) {
            NothiriumShadowRenderer.setupPipelineAttributes(stride);
        } else {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
            NothiriumShadowRenderer.setGenericAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                    fallbackBlockEntityId & 0xFFFF,
                    fallbackRenderType,
                    0.0F,
                    0.0F);
            NothiriumShadowRenderer.setGenericAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 1.0F);
            NothiriumShadowRenderer.setGenericAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1.0F, 0.0F, 0.0F, 1.0F);
            NothiriumShadowRenderer.setGenericAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        }
        ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
        NothiriumShadowRenderer.setGenericAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    protected static boolean isPipelineBlockStride(int stride) {
        NothiriumShadowRenderer.ensurePipelineBlockFormat();
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && stride == ExtendedVertexFormats.size(ExtendedVertexFormats.PIPELINE_BLOCK);
    }

    protected static void ensurePipelineBlockFormat() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
    }

    protected static void setupPipelineAttributes(int stride) {
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

    protected static boolean shouldAuditSparseVisibleBridge(NothiriumShadowRenderer.DrawStats stats) {
        if (stats.drawn >= 64) {
            return false;
        }
        return stats.total > 0
                && (stats.partPresent > 0
                || stats.validPart > 0
                || stats.positiveCount > 0
                || stats.positiveVbo > 0
                || stats.missingPart > 0
                || stats.invalidPart > 0
                || stats.emptyCount > 0
                || stats.badVbo > 0
                || stats.badStride > 0
                || stats.unsupportedStride > 0
                || stats.invalidRange > 0);
    }

    protected void auditVisibleTerrainFailure(BlockRenderLayer layer, NothiriumShadowRenderer.DrawStats stats,
                                              int fallbackBlockEntityId, short fallbackRenderType) {
        if (visibleTerrainFailureAttempts >= MAX_VISIBLE_TERRAIN_FAILURE_LOGS) {
            return;
        }

        visibleTerrainFailureAttempts++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumVisibleTerrain] call={} layer={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleTerrainFailureAttempts,
                layer,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart,
                NothiriumShadowRenderer.glStateSummary()
        );
    }

    protected void auditNonSolidVisibleTerrainFailure(BlockRenderLayer layer, NothiriumShadowRenderer.DrawStats stats,
                                                      int fallbackBlockEntityId, short fallbackRenderType) {
        if (layer == null
                || layer == BlockRenderLayer.SOLID
                || stats.drawn > 0
                || visibleNonSolidTerrainFailureAttempts >= MAX_VISIBLE_NON_SOLID_TERRAIN_FAILURE_LOGS) {
            return;
        }

        visibleNonSolidTerrainFailureAttempts++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumNonSolidVisible] call={} layer={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleNonSolidTerrainFailureAttempts,
                layer,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart,
                NothiriumShadowRenderer.glStateSummary()
        );
    }

    protected void auditVisibleTranslucentLayer(BlockRenderLayer layer, NothiriumShadowRenderer.DrawStats stats,
                                                int fallbackBlockEntityId, short fallbackRenderType, String stage) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || visibleTranslucentAuditAttempts >= MAX_VISIBLE_TRANSLUCENT_DIAG_LOGS) {
            return;
        }

        visibleTranslucentAuditAttempts++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumTranslucent] call={} stage={} total={} null={} within={} distCull={} missingPart={} part={} invalidPart={} valid={} emptyCount={} count={} badVbo={} vbo={} badStride={} unsupportedStride={} rangeSkip={} drawn={} fallbackBlock={} fallbackRenderType={} firstChunk={} firstPart={} gl={}",
                visibleTranslucentAuditAttempts,
                stage,
                stats.total,
                stats.nullChunks,
                stats.withinDistance,
                stats.distanceCulled,
                stats.missingPart,
                stats.partPresent,
                stats.invalidPart,
                stats.validPart,
                stats.emptyCount,
                stats.positiveCount,
                stats.badVbo,
                stats.positiveVbo,
                stats.badStride,
                stats.unsupportedStride,
                stats.invalidRange,
                stats.drawn,
                fallbackBlockEntityId,
                fallbackRenderType,
                stats.firstChunk,
                stats.firstPart,
                NothiriumShadowRenderer.glStateSummary()
        );
    }

    protected static void resetClientArrayState() {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
    }

    protected static void setGenericAttribute(int index, float x, float y, float z, float w) {
        if (index >= 0 && index < GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)) {
            GL20.glVertexAttrib4f(index, x, y, z, w);
        }
    }

    protected static void forceTranslucentFixedFunctionState() {
        FixedFunctionGlState.forceTranslucentBlockLayer();
    }

    protected static void logVisibleTranslucentState(String stage) {
        // Probe disabled.
    }

    protected static String glStateSummary() {
        StringBuilder builder = new StringBuilder(FixedFunctionGlState.summary())
                .append(",matrixMode=").append(NothiriumShadowRenderer.matrixModeName(GL11.glGetInteger(GL11.GL_MATRIX_MODE)))
                .append(",cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE))
                .append(",colorMask=").append(NothiriumShadowRenderer.colorMaskSummary())
                .append(",drawBuffer=").append(GL11.glGetInteger(GL11.GL_DRAW_BUFFER))
                .append(",readBuffer=").append(GL11.glGetInteger(GL11.GL_READ_BUFFER))
                .append(",arrayBuffer=").append(GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING))
                .append(",elementBuffer=").append(GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING));
        if (GLContext.getCapabilities().OpenGL30) {
            builder.append(",drawFbo=").append(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))
                    .append(",readFbo=").append(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING))
                    .append(",vao=").append(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        }
        return builder.toString();
    }

    protected static String matrixModeName(int mode) {
        return switch (mode) {
            case GL11.GL_MODELVIEW -> "modelview";
            case GL11.GL_PROJECTION -> "projection";
            case GL11.GL_TEXTURE -> "texture";
            default -> Integer.toString(mode);
        };
    }

    protected static String colorMaskSummary() {
        ByteBuffer mask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, mask);
        return (mask.get(0) != 0) + "/" + (mask.get(1) != 0) + "/" + (mask.get(2) != 0) + "/" + (mask.get(3) != 0);
    }

    protected void auditCompileStats(NothiriumShadowRenderer.CompileStats stats) {
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

    protected void auditMainCompileStats(NothiriumShadowRenderer.CompileStats stats) {
        if (mainCompileAuditAttempts >= 8) {
            return;
        }
        if (stats.total <= 0 || (stats.scheduled <= 0 && stats.running <= 0 && stats.dirty <= 0)) {
            return;
        }
        mainCompileAuditAttempts++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumMainCompileProbe] attempt={} total={} within={} distCull={} dirty={} clean={} canCompile={} cannotCompile={} running={} scheduled={} throttled={} firstChunk={}",
                mainCompileAuditAttempts,
                stats.total,
                stats.withinDistance,
                stats.distanceCulled,
                stats.dirty,
                stats.clean,
                stats.canCompile,
                stats.cannotCompile,
                stats.running,
                stats.scheduled,
                stats.throttled,
                stats.firstChunk
        );
    }

    protected void auditChunkRefresh(int chunkX, int chunkZ, int total, int nullChunks, int matched, int alreadyDirty,
                                     int running, int released, int marked, int canCompile, int cannotCompile,
                                     int noDispatcher, int scheduled, int deferred) {
        if (matched <= 0) {
            return;
        }
        if (chunkRefreshAuditAttempts >= MAX_CHUNK_REFRESH_AUDIT_LOGS) {
            return;
        }
        chunkRefreshAuditAttempts++;
        MainMod.LOGGER.debug(
                "[NothiriumShadowBridge] refreshedChunkColumn attempt={} chunk={},{} total={} null={} matched={} alreadyDirty={} running={} released={} marked={} canCompile={} cannotCompile={} noDispatcher={} scheduled={} deferred={}",
                chunkRefreshAuditAttempts,
                chunkX,
                chunkZ,
                total,
                nullChunks,
                matched,
                alreadyDirty,
                running,
                released,
                marked,
                canCompile,
                cannotCompile,
                noDispatcher,
                scheduled,
                deferred
        );
    }

    protected void auditUploadDrain(Object dispatcher, int before, int after) {
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

    protected void warnOnce(Exception e) {
        if (warned) {
            return;
        }
        warned = true;
        MainMod.LOGGER.warn("[NothiriumCompat] Disabled shadow VBO bridge after an error", e);
    }

    protected void auditEmpty(BlockRenderLayer layer, Object renderer, Object pass, List<?> chunks) {
        if (MAX_EMPTY_LIST_AUDIT_LOGS <= 0) {
            return;
        }
        if (emptyAuditLogged) {
            return;
        }
        emptyAuditLogged = true;

        NothiriumShadowRenderer.Reflection reflection = NothiriumShadowRenderer.reflection();
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

        MainMod.LOGGER.info(
                "[NothiriumShadowBridge] layer={} renderer={} listSize={} renderedChunks={} renderedSections={} totalRenderedSections={}",
                layer,
                renderer != null ? renderer.getClass().getName() : "null",
                chunks != null ? chunks.size() : -1,
                renderedChunks,
                renderedSections,
                totalRenderedSections
        );
    }

    protected static NothiriumShadowRenderer.Reflection reflection() {
        NothiriumShadowRenderer.Reflection existing = reflection;
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
        NothiriumShadowRenderer.Reflection loaded = NothiriumShadowRenderer.Reflection.load();
        if (loaded != null) {
            reflection = loaded;
        }
        return loaded;
    }
}
