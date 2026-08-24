package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import meldexun.nothirium.api.renderer.IVBOPart;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

abstract class NothiriumShadowCompileScheduling extends NothiriumShadowVisibleLayerRendering {
    protected void auditCompileCandidates(NothiriumShadowRenderer.Reflection reflection, Object pass, List<NothiriumShadowRenderer.CompileCandidate> candidates,
                                          double cameraX, double cameraY, double cameraZ,
                                          BlockRenderLayer layer, NothiriumShadowRenderer.CompileStats stats) throws ReflectiveOperationException {
        if (shaderedCompileCandidateProbeAttempts >= MAX_SHADERED_COMPILE_CANDIDATE_PROBE_LOGS
                || candidates.isEmpty()
                || (stats.scheduled > 0 && stats.cannotCompile == 0 && stats.running == 0)) {
            return;
        }

        shaderedCompileCandidateProbeAttempts++;
        StringBuilder details = new StringBuilder();
        int limit = Math.min(6, candidates.size());
        for (int index = 0; index < limit; index++) {
            Object chunk = candidates.get(index).chunk;
            int sectionX = reflection.getSectionX(chunk);
            int sectionZ = reflection.getSectionZ(chunk);
            int loaded = 0;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (reflection.worldUtilIsChunkLoaded.invoke(null, sectionX + dx, sectionZ + dz) instanceof Boolean value && value) {
                        loaded++;
                    }
                }
            }
            Object part = reflection.getVboPart(chunk, pass);
            String partState = part == null
                    ? "missing"
                    : "valid=" + reflection.isValid(part)
                    + ",count=" + reflection.getCount(part)
                    + ",vbo=" + reflection.getVbo(part);
            Object future = reflection.lastCompileTaskResult(chunk);
            if (details.length() > 0) {
                details.append(';');
            }
            details.append(index)
                    .append("@")
                    .append(reflection.getX(chunk)).append(',')
                    .append(reflection.getY(chunk)).append(',')
                    .append(reflection.getZ(chunk))
                    .append(" dist=").append(String.format(Locale.ROOT, "%.1f", Math.sqrt(candidates.get(index).distanceSquared)))
                    .append(" loaded3x3=").append(loaded).append("/9")
                    .append(" dirty=").append(reflection.isChunkDirty(chunk))
                    .append(" empty=").append(reflection.isChunkEmpty(chunk))
                    .append(" canCompile=").append(reflection.canCompile(chunk))
                    .append(" task=").append(reflection.lastCompileTask(chunk) != null)
                    .append(" future=").append(NothiriumShadowRenderer.DrawStats.futureState(future))
                    .append(" part=").append(partState);
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumCompileCandidateProbe] call={} layer={} candidates={} scheduled={} running={} throttled={} cannotCompile={} camera={}/{}/{} details={}",
                shaderedCompileCandidateProbeAttempts,
                layer,
                candidates.size(),
                stats.scheduled,
                stats.running,
                stats.throttled,
                stats.cannotCompile,
                cameraX,
                cameraY,
                cameraZ,
                details);
    }

    protected static int mainTerrainCompileBudget(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return MAX_MAIN_TERRAIN_SOLID_COMPILES_PER_FRAME;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return MAX_MAIN_TERRAIN_TRANSLUCENT_COMPILES_PER_FRAME;
        }
        return MAX_MAIN_TERRAIN_CUTOUT_COMPILES_PER_FRAME;
    }

    protected void pruneMainTerrainCompileAttempts(long now) {
        if (mainTerrainCompileAttempts.size() < 256) {
            return;
        }
        Iterator<Map.Entry<Object, Long>> iterator = mainTerrainCompileAttempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Long> entry = iterator.next();
            if (now - entry.getValue() > MAIN_TERRAIN_COMPILE_TRACK_TTL_MS) {
                iterator.remove();
            }
        }
    }

    protected static boolean futureIsRunning(Object futureObject) {
        return futureObject instanceof CompletableFuture<?> future && !future.isDone();
    }

    protected NothiriumShadowRenderer.DrawStats drawChunksWithLayerState(BlockRenderLayer layer, NothiriumShadowRenderer.Reflection reflection, Object pass, Iterable<?> chunks,
                                                                         double cameraX, double cameraY, double cameraZ, double maxDistance, boolean collectState,
                                                                         int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        boolean externalTranslucentState = layer == BlockRenderLayer.TRANSLUCENT
                && PipelineContext.getInstance().isBloomTranslucentAttenuationPass();
        NothiriumShadowRenderer.LayerGlState layerState = externalTranslucentState ? null : NothiriumShadowRenderer.LayerGlState.prepare(layer);
        try {
            if (layer == BlockRenderLayer.TRANSLUCENT && !externalTranslucentState) {
                PipelineContext.getInstance().restoreActiveGbufferRenderState();
            }
            return self().drawChunks(layer, reflection, pass, chunks, cameraX, cameraY, cameraZ, maxDistance, collectState,
                    fallbackBlockEntityId, fallbackRenderType, requirePipelineStride);
        } finally {
            if (layerState != null) {
                layerState.restore();
            }
        }
    }

    protected NothiriumShadowRenderer.DrawStats drawChunks(BlockRenderLayer layer, NothiriumShadowRenderer.Reflection reflection, Object pass, Iterable<?> chunks,
                                                           double cameraX, double cameraY, double cameraZ, double maxDistance, boolean collectState,
                                                           int fallbackBlockEntityId, short fallbackRenderType, boolean requirePipelineStride)
            throws ReflectiveOperationException {
        NothiriumShadowRenderer.DrawStats stats = new NothiriumShadowRenderer.DrawStats();
        int previousVbo = -1;
        int previousVboSize = 0;
        int previousStride = -1;
        double maxDistanceSquared = maxDistance >= 0.0D ? maxDistance * maxDistance : -1.0D;
        NothiriumShadowRenderer.ShadowSelection activeSelection = shadowSelection;
        // beginShadowSelection has already applied this exact distance test to
        // its list. Avoid repeating three double-vector calculations for each
        // chunk across the solid and two cutout layers.
        boolean selectionAlreadyDistanceCulled = shadowSelectionActive
                && activeSelection != null
                && chunks == activeSelection.chunks
                && maxDistance == activeSelection.maxDistance;
        PipelineContext context = PipelineContext.getInstance();
        boolean disableCullForMainTerrain = context.shouldDisableNothiriumChunkCulling(layer);
        // Shadered shadow terrain never uses the shaderless bloom metadata.
        // Avoid an extra context dispatch and metadata lookup for every
        // Nothirium section in the normal shadow path.
        boolean shaderlessBloomExtraction = context.isShaderlessBloomExtractionActive();
        int shaderlessBloomDimension = shaderlessBloomExtraction
                ? context.shaderlessBloomExtractionDimensionId()
                : Integer.MIN_VALUE;
        if (selectionAlreadyDistanceCulled
                && !shaderlessBloomExtraction
                && !collectState
                && requirePipelineStride) {
            return self().drawPreparedPipelineChunks(pass, chunks, cameraX, cameraY, cameraZ,
                    fallbackBlockEntityId, fallbackRenderType, context);
        }
        boolean previousCull = false;
        int previousMatrixMode = -1;
        // A shadow terrain layer keeps one program bound for its complete
        // chunk loop. Querying it and resolving the same uniform for every
        // section was visible client-thread overhead in movement captures.
        int activeProgram = USE_CHUNK_OFFSET_UNIFORM
                ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                : 0;
        int activeChunkOffsetUniform = activeProgram > 0
                ? self().chunkOffsetUniformLocation(activeProgram)
                : -1;
        boolean useChunkOffsetUniform = activeChunkOffsetUniform >= 0;
        int activeDrawMode = context.drawModeForActiveProgram(GL11.GL_QUADS);

        try {
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            if (disableCullForMainTerrain) {
                previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            for (Object chunk : chunks) {
                stats.total++;
                if (chunk == null) {
                    stats.nullChunks++;
                    continue;
                }

                NothiriumShadowRenderer.ChunkOrigin origin = self().chunkOrigin(reflection, chunk);
                int chunkX = origin.x;
                int chunkY = origin.y;
                int chunkZ = origin.z;
                stats.captureFirstChunk(chunkX, chunkY, chunkZ);
                if (collectState) {
                    stats.captureState(reflection, chunk, chunkX, chunkY, chunkZ);
                }
                if (!selectionAlreadyDistanceCulled && maxDistanceSquared >= 0.0D) {
                    double dx = chunkX + 8.0D - cameraX;
                    double dy = chunkY + 8.0D - cameraY;
                    double dz = chunkZ + 8.0D - cameraZ;
                    if (dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                        stats.distanceCulled++;
                        continue;
                    }
                }
                stats.withinDistance++;
                if (shaderlessBloomExtraction && !context.shouldRenderShaderlessBloomChunkLayer(
                        layer, chunkX, chunkY, chunkZ, shaderlessBloomDimension)) {
                    continue;
                }

                Object part = reflection.getVboPart(chunk, pass);
                if (part == null) {
                    stats.missingPart++;
                    continue;
                }
                stats.partPresent++;
                if (!reflection.isValid(part)) {
                    stats.invalidPart++;
                    continue;
                }
                stats.validPart++;

                int count = reflection.getCount(part);
                if (count <= 0) {
                    stats.emptyCount++;
                    continue;
                }
                stats.positiveCount++;

                int vbo = reflection.getVbo(part);
                if (vbo <= 0) {
                    stats.badVbo++;
                    continue;
                }
                stats.positiveVbo++;

                int offset = reflection.getOffset(part);
                int size = reflection.getSize(part);
                int stride = NothiriumShadowRenderer.vertexStride(size, count);
                if (stride <= 0) {
                    stats.badStride++;
                    continue;
                }
                boolean pipelineStride = NothiriumShadowRenderer.isPipelineBlockStride(stride);
                if (!pipelineStride) {
                    stats.unsupportedStride++;
                    stats.captureUnsupportedPipelineChunk(chunk);
                    if (requirePipelineStride) {
                        continue;
                    }
                }

                if (vbo != previousVbo || stride != previousStride) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                    NothiriumShadowRenderer.setupArrayPointers(stride, fallbackBlockEntityId, fallbackRenderType);
                    previousVbo = vbo;
                    previousStride = stride;
                    previousVboSize = VALIDATE_NOTHIRIUM_VBO_DRAW_RANGES
                            ? GL15.glGetBufferParameteri(GL15.GL_ARRAY_BUFFER, GL15.GL_BUFFER_SIZE)
                            : -1;
                }

                int first = reflection.getFirst(part);
                stats.captureFirstPart(vbo, first, count, offset, size, stride, previousVboSize);
                if (previousVboSize >= 0 && !NothiriumShadowRenderer.validDrawRange(offset, size, previousVboSize)) {
                    stats.invalidRange++;
                    continue;
                }

                // Chunk fading is disabled by the production pipeline. The
                // finalizer below resets the uniform once, so do not issue a
                // redundant uniform reset for every shadow section.
                if (useChunkOffsetUniform) {
                    GL20.glUniform3f(activeChunkOffsetUniform,
                            (float) (chunkX - cameraX),
                            (float) (chunkY - cameraY),
                            (float) (chunkZ - cameraZ));
                    GL11.glDrawArrays(activeDrawMode, first, count);
                } else {
                    if (previousMatrixMode < 0) {
                        previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                    }
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ);
                        GL11.glDrawArrays(activeDrawMode, first, count);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                stats.drawn++;
            }
        } finally {
            // Shadow programs can be reused immediately for entities and
            // block entities. Never let the final terrain section's offset
            // leak into those draws.
            if (useChunkOffsetUniform) {
                GL20.glUniform3f(activeChunkOffsetUniform, 0.0F, 0.0F, 0.0F);
            }
            if (previousMatrixMode >= 0) {
                GL11.glMatrixMode(previousMatrixMode);
            }
            if (disableCullForMainTerrain) {
                if (previousCull) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                } else {
                    GL11.glDisable(GL11.GL_CULL_FACE);
                }
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            NothiriumShadowRenderer.resetClientArrayState();
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
            PipelineContext.getInstance().resetChunkFadeUniform();
        }

        return stats;
    }

    /**
     * Production shadow selection is populated exclusively by Nothirium
     * RenderChunks carrying {@link NothiriumShadowChunkAccess}.  The generic
     * renderer above is retained for diagnostics and non-standard providers;
     * this path removes its per-section reflection, origin allocation,
     * distance test and statistics formatting from the common draw loop.
     */
    protected NothiriumShadowRenderer.DrawStats drawPreparedPipelineChunks(Object pass, Iterable<?> chunks,
                                                                             double cameraX, double cameraY, double cameraZ,
                                                                             int fallbackBlockEntityId, short fallbackRenderType,
                                                                             PipelineContext context) {
        NothiriumShadowRenderer.DrawStats stats = new NothiriumShadowRenderer.DrawStats();
        int previousVbo = -1;
        int activeProgram = USE_CHUNK_OFFSET_UNIFORM
                ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                : 0;
        int activeChunkOffsetUniform = activeProgram > 0
                ? self().chunkOffsetUniformLocation(activeProgram)
                : -1;
        boolean useChunkOffsetUniform = activeChunkOffsetUniform >= 0;
        int activeDrawMode = context.drawModeForActiveProgram(GL11.GL_QUADS);
        int previousMatrixMode = -1;

        try {
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            for (Object chunk : chunks) {
                stats.total++;
                if (!(chunk instanceof NothiriumShadowChunkAccess access)) {
                    // Do not risk drawing an unknown provider with the
                    // pipeline format. The generic path remains available
                    // for that provider on its next render call.
                    stats.unsupportedStride++;
                    continue;
                }

                IVBOPart part = access.ausm$vboPart(pass) instanceof IVBOPart value ? value : null;
                if (part == null) {
                    stats.missingPart++;
                    continue;
                }
                if (!part.isValid()) {
                    stats.invalidPart++;
                    continue;
                }

                int count = part.getCount();
                int size = part.getSize();
                if (count <= 0) {
                    stats.emptyCount++;
                    continue;
                }
                int stride = NothiriumShadowRenderer.vertexStride(size, count);
                if (!NothiriumShadowRenderer.isPipelineBlockStride(stride)) {
                    stats.unsupportedStride++;
                    stats.captureUnsupportedPipelineChunk(chunk);
                    continue;
                }

                int vbo = part.getVBO();
                if (vbo <= 0) {
                    stats.badVbo++;
                    continue;
                }
                if (vbo != previousVbo) {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                    NothiriumShadowRenderer.setupArrayPointers(stride, fallbackBlockEntityId, fallbackRenderType);
                    previousVbo = vbo;
                }

                int chunkX = access.ausm$blockX();
                int chunkY = access.ausm$blockY();
                int chunkZ = access.ausm$blockZ();
                int first = part.getFirst();
                if (useChunkOffsetUniform) {
                    GL20.glUniform3f(activeChunkOffsetUniform,
                            (float) (chunkX - cameraX),
                            (float) (chunkY - cameraY),
                            (float) (chunkZ - cameraZ));
                    GL11.glDrawArrays(activeDrawMode, first, count);
                } else {
                    if (previousMatrixMode < 0) {
                        previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
                    }
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPushMatrix();
                    try {
                        GL11.glTranslated(chunkX - cameraX, chunkY - cameraY, chunkZ - cameraZ);
                        GL11.glDrawArrays(activeDrawMode, first, count);
                    } finally {
                        GL11.glPopMatrix();
                    }
                }
                stats.drawn++;
            }
        } finally {
            if (useChunkOffsetUniform) {
                GL20.glUniform3f(activeChunkOffsetUniform, 0.0F, 0.0F, 0.0F);
            }
            if (previousMatrixMode >= 0) {
                GL11.glMatrixMode(previousMatrixMode);
            }
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBindVertexArray(0);
            }
            NothiriumShadowRenderer.resetClientArrayState();
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(NOTHIRIUM_OFFSET_ATTRIBUTE);
            context.resetChunkFadeUniform();
        }
        return stats;
    }

    protected int chunkOffsetUniformLocation(int program) {
        if (program <= 0) {
            return -1;
        }
        Integer cached = chunkOffsetUniformLocations.get(program);
        if (cached != null) {
            return cached;
        }
        int location = GL20.glGetUniformLocation(program, "ausm_ChunkOffset");
        chunkOffsetUniformLocations.put(program, location);
        return location;
    }

    protected void refreshUnsupportedPipelineChunks(NothiriumShadowRenderer.Reflection reflection, List<Object> chunks)
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
            if (NothiriumShadowRenderer.futureIsRunning(reflection.lastCompileTaskResult(chunk))) {
                continue;
            }

            if (!Boolean.TRUE.equals(reflection.canCompile(chunk))) {
                continue;
            }

            // This is a forced stride repair, but still must retain a valid
            // old VBO until the replacement task has been accepted.
            reflection.markDirty.invoke(chunk);
            reflection.compileAsync.invoke(chunk, renderer, dispatcher);
            scheduled++;
        }

        if (scheduled > 0) {
            NothiriumShadowRenderer.drainQueuedUploads(reflection, dispatcher);
        }
    }

    protected static int vertexStride(int size, int count) {
        if (size <= 0 || count <= 0 || size % count != 0) {
            return VANILLA_BLOCK_STRIDE;
        }
        int stride = size / count;
        return stride >= LIGHT_COORD_OFFSET + 4 ? stride : -1;
    }

    protected static boolean validDrawRange(int offset, int size, int bufferSize) {
        if (offset < 0 || size <= 0 || bufferSize <= 0) {
            return false;
        }
        long endByte = (long) offset + size;
        return endByte >= 0L && endByte <= bufferSize;
    }

    /**
     * The fertile lily pad shadow discard did not change the reported square.
     * Read only bounded shadow VBO spans to prove whether the Nothirium bridge
     * actually supplies its mapped material id to shadow.glsl.
     */
    protected void probeLilyShadowMaterial(PipelineContext context, BlockRenderLayer layer,
                                           int chunkX, int chunkY, int chunkZ,
                                           int offset, int size, int stride, boolean pipelineStride) {
        boolean shadowActive = context != null && context.isShadowPassActive();
        boolean knownLilySection = context != null && context.isKnownLilyPadShadowProbeChunk(chunkX, chunkY, chunkZ);
        // The section key remains false even beside material-10489 terrain:
        // Nothirium's actual draw origins do not correspond to the compiled
        // BlockPos section key. Record every gate and scan the complete active
        // shadow CUTOUT route for the material itself; this is the final
        // evidence needed before touching lily geometry or lighting again.
        if (shadowActive && layer == BlockRenderLayer.CUTOUT && lilyShadowGateProbeLogs < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
            lilyShadowGateProbeLogs++;
            MainMod.LOGGER.info(
                    "[AUSMLilyShadowGateProbe] call={} known={} pipelineStride={} offset={} size={} stride={} chunk={}/{}/{} program={}",
                    lilyShadowGateProbeLogs,
                    knownLilySection,
                    pipelineStride,
                    offset,
                    size,
                    stride,
                    chunkX,
                    chunkY,
                    chunkZ,
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
            );
            context.forensicGlTrace("lily-shadow-vbo-gate", "known=" + knownLilySection
                    + ", pipelineStride=" + pipelineStride + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ
                    + ", offset=" + offset + ", size=" + size + ", stride=" + stride);
        }
        if (context == null
                || !shadowActive
                || !pipelineStride
                || layer != BlockRenderLayer.CUTOUT
                || lilyShadowVertexScanCalls >= MAX_LILY_SHADOW_VERTEX_SCAN_CALLS
                || lilyShadowVertexProbeLogs >= MAX_LILY_SHADOW_VERTEX_PROBE_LOGS
                || stride < ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2
                || offset < 0
                || size <= 0) {
            return;
        }

        lilyShadowVertexScanCalls++;
        int bytes = Math.min(size, lilyShadowVertexProbe.capacity());
        bytes -= bytes % stride;
        if (bytes <= 0) {
            return;
        }

        try {
            lilyShadowVertexProbe.clear();
            lilyShadowVertexProbe.limit(bytes);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, offset, lilyShadowVertexProbe);
            for (int vertexOffset = 0; vertexOffset < bytes; vertexOffset += stride) {
                int material = lilyShadowVertexProbe.getShort(
                        vertexOffset + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET) & 0xFFFF;
                if (material != 10489) {
                    continue;
                }
                lilyShadowVertexProbeLogs++;
                MainMod.LOGGER.info(
                        "[AUSMLilyShadowMaterialProbe] hit={} scan={} layer={} chunk={}/{}/{} material={} renderType={} stride={} program={}",
                        lilyShadowVertexProbeLogs,
                        lilyShadowVertexScanCalls,
                        layer,
                        chunkX,
                        chunkY,
                        chunkZ,
                        material,
                        lilyShadowVertexProbe.getShort(vertexOffset + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2) & 0xFFFF,
                        stride,
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                );
                context.forensicGlTrace("lily-shadow-vbo-hit", "scan=" + lilyShadowVertexScanCalls + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ + ", material=" + material + ", stride=" + stride);
                return;
            }
            if (lilyShadowVertexProbeLogs++ < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
                MainMod.LOGGER.info(
                        "[AUSMLilyShadowMaterialProbe] miss={} scan={} layer={} chunk={}/{}/{} stride={} program={}",
                        lilyShadowVertexProbeLogs,
                        lilyShadowVertexScanCalls,
                        layer,
                        chunkX,
                        chunkY,
                        chunkZ,
                        stride,
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                );
                context.forensicGlTrace("lily-shadow-vbo-miss", "scan=" + lilyShadowVertexScanCalls + ", chunk=" + chunkX + "/" + chunkY + "/" + chunkZ + ", stride=" + stride);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (lilyShadowVertexProbeLogs++ < MAX_LILY_SHADOW_VERTEX_PROBE_LOGS) {
                MainMod.LOGGER.info("[AUSMLilyShadowMaterialProbe] scan={} failed={}",
                        lilyShadowVertexScanCalls, exception.getClass().getSimpleName());
            }
        } finally {
            lilyShadowVertexProbe.clear();
        }
    }

    protected NothiriumShadowRenderer.DrawProbe captureVisibleTerrainDrawProbe(BlockRenderLayer layer, int chunkX, int chunkY, int chunkZ,
                                                                               double cameraX, double cameraY, double cameraZ,
                                                                               int vbo, int first, int count, int offset, int size,
                                                                               int stride, int vboSize, boolean pipelineStride) {
        if (!PipelineContext.getInstance().isPipelineActive()
                || visibleTerrainDrawProbeAttempts >= MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS
                || count <= 0
                || stride <= 0
                || offset < 0) {
            return null;
        }
        visibleTerrainDrawProbeAttempts++;

        String vertex = "unread";
        try {
            int readSize = Math.min(stride, visibleTerrainVertexProbe.capacity());
            visibleTerrainVertexProbe.clear();
            visibleTerrainVertexProbe.limit(readSize);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, offset, visibleTerrainVertexProbe);
            vertex = self().formatVertexProbe(stride);
        } catch (RuntimeException | LinkageError exception) {
            vertex = "error=" + exception.getClass().getSimpleName();
        } finally {
            visibleTerrainVertexProbe.clear();
        }

        String matrix = "unread";
        String clip = "unread";
        try {
            visibleTerrainMatrixProbe.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, visibleTerrainMatrixProbe);
            visibleTerrainProjectionProbe.clear();
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, visibleTerrainProjectionProbe);
            matrix = "mv=" + NothiriumShadowRenderer.matrixSummary(visibleTerrainMatrixProbe)
                    + ",proj=" + NothiriumShadowRenderer.matrixSummary(visibleTerrainProjectionProbe);
            clip = self().clipSummaryForFirstVertex(stride);
        } catch (RuntimeException | LinkageError exception) {
            matrix = "error=" + exception.getClass().getSimpleName();
            clip = "error=" + exception.getClass().getSimpleName();
        }

        return new NothiriumShadowRenderer.DrawProbe(
                visibleTerrainDrawProbeAttempts,
                layer,
                chunkX,
                chunkY,
                chunkZ,
                cameraX,
                cameraY,
                cameraZ,
                vbo,
                first,
                count,
                offset,
                size,
                stride,
                vboSize,
                pipelineStride,
                vertex,
                matrix + ",clip=" + clip,
                self().terrainUniformSummary(),
                NothiriumShadowRenderer.glStateSummary()
        );
    }

    protected String clipSummaryForFirstVertex(int stride) {
        if (stride < POSITION_OFFSET + 12) {
            return "no-position";
        }
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);

        float viewX = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 0, x, y, z, 1.0F);
        float viewY = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 1, x, y, z, 1.0F);
        float viewZ = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 2, x, y, z, 1.0F);
        float viewW = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainMatrixProbe, 3, x, y, z, 1.0F);

        float clipX = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 0, viewX, viewY, viewZ, viewW);
        float clipY = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 1, viewX, viewY, viewZ, viewW);
        float clipZ = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 2, viewX, viewY, viewZ, viewW);
        float clipW = NothiriumShadowRenderer.multiplyMatrixVector(visibleTerrainProjectionProbe, 3, viewX, viewY, viewZ, viewW);
        if (!Float.isFinite(clipW) || Math.abs(clipW) < 1.0E-6F) {
            return "view=" + NothiriumShadowRenderer.formatVec4(viewX, viewY, viewZ, viewW)
                    + ",clip=" + NothiriumShadowRenderer.formatVec4(clipX, clipY, clipZ, clipW)
                    + ",ndc=invalid-w";
        }

        return "view=" + NothiriumShadowRenderer.formatVec4(viewX, viewY, viewZ, viewW)
                + ",clip=" + NothiriumShadowRenderer.formatVec4(clipX, clipY, clipZ, clipW)
                + ",ndc=" + NothiriumShadowRenderer.formatVec3(clipX / clipW, clipY / clipW, clipZ / clipW);
    }

    protected static float multiplyMatrixVector(FloatBuffer matrix, int row, float x, float y, float z, float w) {
        return matrix.get(row) * x
                + matrix.get(4 + row) * y
                + matrix.get(8 + row) * z
                + matrix.get(12 + row) * w;
    }

    protected static String matrixSummary(FloatBuffer matrix) {
        return "m00=" + NothiriumShadowRenderer.formatFloat(matrix.get(0))
                + ",m11=" + NothiriumShadowRenderer.formatFloat(matrix.get(5))
                + ",m22=" + NothiriumShadowRenderer.formatFloat(matrix.get(10))
                + ",m23=" + NothiriumShadowRenderer.formatFloat(matrix.get(14))
                + ",m32=" + NothiriumShadowRenderer.formatFloat(matrix.get(11))
                + ",m33=" + NothiriumShadowRenderer.formatFloat(matrix.get(15))
                + ",t=" + NothiriumShadowRenderer.formatVec3(matrix.get(12), matrix.get(13), matrix.get(14));
    }

    protected static String formatVec4(float x, float y, float z, float w) {
        return NothiriumShadowRenderer.formatFloat(x) + '/' + NothiriumShadowRenderer.formatFloat(y) + '/' + NothiriumShadowRenderer.formatFloat(z) + '/' + NothiriumShadowRenderer.formatFloat(w);
    }

    protected static String formatVec3(float x, float y, float z) {
        return NothiriumShadowRenderer.formatFloat(x) + '/' + NothiriumShadowRenderer.formatFloat(y) + '/' + NothiriumShadowRenderer.formatFloat(z);
    }

    protected static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    protected int beginProbeQuery(NothiriumShadowRenderer.DrawProbe probe) {
        if (probe == null || !GLContext.getCapabilities().OpenGL15) {
            return 0;
        }
        try {
            int query = GL15.glGenQueries();
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
            return query;
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    protected void finishVisibleTerrainDrawProbe(NothiriumShadowRenderer.DrawProbe probe, int query) {
        if (probe == null) {
            return;
        }

        String samples = "unavailable";
        if (query > 0) {
            try {
                GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                samples = Integer.toString(GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT));
            } catch (RuntimeException | LinkageError exception) {
                samples = "error=" + exception.getClass().getSimpleName();
            } finally {
                try {
                    GL15.glDeleteQueries(query);
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }

        MainMod.LOGGER.warn(
                "[AUSMNothiriumDrawProbe] call={} layer={} chunk={}/{}/{} camera={}/{}/{} translate={}/{}/{} vbo={} first={} count={} offset={} size={} stride={} vboSize={} pipelineStride={} vertex={} modelView={} uniforms={} samples={} gl={}",
                probe.call(),
                probe.layer(),
                probe.chunkX(),
                probe.chunkY(),
                probe.chunkZ(),
                probe.cameraX(),
                probe.cameraY(),
                probe.cameraZ(),
                probe.chunkX() - probe.cameraX(),
                probe.chunkY() - probe.cameraY(),
                probe.chunkZ() - probe.cameraZ(),
                probe.vbo(),
                probe.first(),
                probe.count(),
                probe.offset(),
                probe.size(),
                probe.stride(),
                probe.vboSize(),
                probe.pipelineStride(),
                probe.vertex(),
                probe.modelView(),
                probe.uniforms(),
                samples,
                probe.gl()
        );
    }

    protected String terrainUniformSummary() {
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (program <= 0) {
            return "program=0";
        }
        return "gbufferModelView=" + self().matrixUniformTranslation(program, "gbufferModelView")
                + ",gbufferModelViewInverse=" + self().matrixUniformTranslation(program, "gbufferModelViewInverse")
                + ",gbufferProjection=" + self().matrixUniformTranslation(program, "gbufferProjection")
                + ",modelViewMatrix=" + self().matrixUniformTranslation(program, "modelViewMatrix");
    }

    protected String matrixUniformTranslation(int program, String name) {
        try {
            int location = GL20.glGetUniformLocation(program, name);
            if (location < 0) {
                return "missing";
            }
            visibleTerrainUniformProbe.clear();
            GL20.glGetUniform(program, location, visibleTerrainUniformProbe);
            return "m03=" + visibleTerrainUniformProbe.get(12)
                    + ",m13=" + visibleTerrainUniformProbe.get(13)
                    + ",m23=" + visibleTerrainUniformProbe.get(14)
                    + ",m33=" + visibleTerrainUniformProbe.get(15);
        } catch (RuntimeException | LinkageError exception) {
            return "error=" + exception.getClass().getSimpleName();
        }
    }

    protected String formatVertexProbe(int stride) {
        float x = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET);
        float y = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 4);
        float z = visibleTerrainVertexProbe.getFloat(POSITION_OFFSET + 8);
        int r = visibleTerrainVertexProbe.get(COLOR_OFFSET) & 0xFF;
        int g = visibleTerrainVertexProbe.get(COLOR_OFFSET + 1) & 0xFF;
        int b = visibleTerrainVertexProbe.get(COLOR_OFFSET + 2) & 0xFF;
        int a = visibleTerrainVertexProbe.get(COLOR_OFFSET + 3) & 0xFF;
        float u = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET) : Float.NaN;
        float v = stride >= TEX_COORD_OFFSET + 8 ? visibleTerrainVertexProbe.getFloat(TEX_COORD_OFFSET + 4) : Float.NaN;
        int lightU = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET) & 0xFFFF : -1;
        int lightV = stride >= LIGHT_COORD_OFFSET + 4 ? visibleTerrainVertexProbe.getShort(LIGHT_COORD_OFFSET + 2) & 0xFFFF : -1;
        StringBuilder builder = new StringBuilder();
        builder.append("pos=").append(x).append('/').append(y).append('/').append(z)
                .append(",color=").append(r).append('/').append(g).append('/').append(b).append('/').append(a)
                .append(",uv=").append(u).append('/').append(v)
                .append(",light=").append(lightU).append('/').append(lightV);
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 4) {
            builder.append(",normal=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET + 2));
        }
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 8) {
            builder.append(",mcEntity=")
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 2) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 4) & 0xFFFF)
                    .append('/')
                    .append(visibleTerrainVertexProbe.getShort(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET + 6) & 0xFFFF);
        }
        if (stride >= ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 4) {
            builder.append(",midBlock=")
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 1))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 2))
                    .append('/')
                    .append(visibleTerrainVertexProbe.get(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET + 3));
        }
        return builder.toString();
    }
}
