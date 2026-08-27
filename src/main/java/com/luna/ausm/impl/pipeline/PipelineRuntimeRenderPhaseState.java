package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_NON_SOLID_REPAIR_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_SPARSE_MAIN_REPAIR_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_WATER_ATTACHMENT_DELTA_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_NON_SOLID_REPAIR_COOLDOWN_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_MAX_CHUNKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_MAX_CHUNKS;

abstract class PipelineRuntimeDiagnosticsState4 extends PipelineRuntimeDiagnosticsState3 {
    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : activePhase;
    }

    public void logSkyPipelineProbe(String stage) {
        self().freshSkyProbe("sky-" + stage, "");
    }

    protected String skyProbeWorldSummary() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        Entity view = mc != null ? MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (world == null) {
            return "null";
        }
        return "dim=" + self().safeDimensionId(world)
                + ",time=" + MinecraftReflectionCompat.worldTime(world)
                + ",celestial=" + MinecraftReflectionCompat.worldCelestialAngle(world, 0.0f)
                + ",rain=" + MinecraftReflectionCompat.worldRainStrength(world, 0.0f)
                + ",thunder=" + MinecraftReflectionCompat.worldThunderStrength(world, 0.0f)
                + ",viewYaw=" + (view != null ? MinecraftReflectionCompat.rotationYaw(view) : Float.NaN)
                + ",viewPitch=" + (view != null ? MinecraftReflectionCompat.rotationPitch(view) : Float.NaN);
    }

    protected static String skyProbeGlStateSummary() {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int activeTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture0Binding = activeTextureBinding;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0Binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } finally {
            GL13.glActiveTexture(activeTexture);
        }
        return PipelineRuntimeState.glStateSummary()
                + ",texActiveBinding=" + activeTextureBinding
                + ",tex0Binding=" + texture0Binding;
    }

    public int renderNothiriumTerrainLayer(BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        int visibleCount = self().renderNothiriumVisibleTerrainLayer(layer, partialTicks, viewEntity);
        if (!self().shouldRetrySparseNothiriumTerrainAfterSetup(layer, visibleCount) || viewEntity == null) {
            return visibleCount;
        }

        // Nothirium's normal RenderGlobal hook has already populated the lists
        // for the current frame. Only rebuild its camera/frustum lists after a
        // sparse draw; doing setup before every layer made shadered terrain pay
        // the full renderer update cost even when the existing lists were valid.
        if (!self().setupNothiriumShaderedMainTerrainLists(false)) {
            return visibleCount;
        }

        return self().renderNothiriumVisibleTerrainLayer(layer, partialTicks, viewEntity);
    }

    protected int renderNothiriumVisibleTerrainLayer(BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (viewEntity == null || !self().shouldUseNothiriumMainTerrainBridge()) {
            return -1;
        }
        double cameraX = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity),
                MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity),
                MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        int rendererDrawn = nothiriumShadowRenderer.renderVisibleLayerAllowingVanillaStride(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                self().nothiriumFallbackBlockEntityId(layer),
                self().nothiriumFallbackRenderType(layer)
        );
        if (rendererDrawn > 0 || !self().shouldSupplementSparseNothiriumTerrainFromProvider(layer, rendererDrawn)) {
            return rendererDrawn;
        }

        // Nothirium can finish a chunk compile after its renderer-owned setup
        // pass has already built empty visibility lists. Draw the same ready
        // provider chunks directly until a later setup pass repopulates them.
        return nothiriumShadowRenderer.renderProviderLayerSchedulingCompiles(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                self().nothiriumProviderSparseTerrainDistance(layer),
                self().nothiriumFallbackBlockEntityId(layer),
                self().nothiriumFallbackRenderType(layer),
                false
        );
    }

    public void beginWaterAttachmentDeltaProbe(BlockRenderLayer layer) {
        waterAttachmentDeltaProbeActive = false;
        if (layer != BlockRenderLayer.TRANSLUCENT
                || activePass != RenderPass.GBUFFERS_WATER
                || waterAttachmentDeltaProbeLogs >= MAX_WATER_ATTACHMENT_DELTA_PROBE_LOGS
                || !pingPongManager.isInitialized()) {
            return;
        }
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        PipelineProgram program = programs.get(RenderPass.GBUFFERS_WATER);
        if (framebuffer == null || program == null) {
            return;
        }
        List<Attachment> attachments = self().effectiveDrawBuffersForCurrentPhase(program);
        int count = Math.min(waterAttachmentBefore.length, attachments.size());
        for (int slot = count; slot < waterAttachmentBefore.length; slot++) {
            waterAttachmentProbeWidths[slot] = 0;
            waterAttachmentProbeHeights[slot] = 0;
            waterAttachmentProbeIndices[slot] = -1;
        }
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            for (int slot = 0; slot < count; slot++) {
                Attachment attachment = attachments.get(slot);
                int width = framebuffer.getAttachmentWidth(attachment);
                int height = framebuffer.getAttachmentHeight(attachment);
                int bytes = width * height * 4;
                ByteBuffer buffer = waterAttachmentBefore[slot];
                if (buffer == null || buffer.capacity() < bytes) {
                    buffer = BufferUtils.createByteBuffer(bytes);
                    waterAttachmentBefore[slot] = buffer;
                }
                buffer.clear();
                buffer.limit(bytes);
                waterAttachmentProbeIndices[slot] = attachment.getIndex();
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + attachment.getIndex());
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                waterAttachmentProbeWidths[slot] = width;
                waterAttachmentProbeHeights[slot] = height;
            }
            waterAttachmentDeltaProbeActive = count > 0;
        } finally {
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    public void finishWaterAttachmentDeltaProbe() {
        if (!waterAttachmentDeltaProbeActive) {
            return;
        }
        waterAttachmentDeltaProbeActive = false;
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        StringBuilder result = new StringBuilder();
        try {
            for (int slot = 0; slot < waterAttachmentBefore.length; slot++) {
                ByteBuffer before = waterAttachmentBefore[slot];
                int width = waterAttachmentProbeWidths[slot];
                int height = waterAttachmentProbeHeights[slot];
                if (before == null || width <= 0 || height <= 0) {
                    continue;
                }
                int bytes = width * height * 4;
                ByteBuffer after = waterAttachmentAfter[slot];
                if (after == null || after.capacity() < bytes) {
                    after = BufferUtils.createByteBuffer(bytes);
                    waterAttachmentAfter[slot] = after;
                }
                after.clear();
                after.limit(bytes);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + waterAttachmentProbeIndices[slot]);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, after);
                long changedPixels = 0L;
                long totalDelta = 0L;
                int maxDelta = 0;
                for (int pixel = 0; pixel < width * height; pixel++) {
                    boolean changed = false;
                    int base = pixel * 4;
                    for (int channel = 0; channel < 4; channel++) {
                        int delta = Math.abs((before.get(base + channel) & 0xFF) - (after.get(base + channel) & 0xFF));
                        if (delta != 0) {
                            changed = true;
                            totalDelta += delta;
                            maxDelta = Math.max(maxDelta, delta);
                        }
                    }
                    if (changed) {
                        changedPixels++;
                    }
                }
                if (result.length() > 0) {
                    result.append(';');
                }
                result.append("slot").append(slot)
                        .append("/colortex").append(waterAttachmentProbeIndices[slot])
                        .append('=').append(width).append('x').append(height)
                        .append(",changedPixels=").append(changedPixels)
                        .append(",totalDelta=").append(totalDelta)
                        .append(",maxDelta=").append(maxDelta);
            }
        } finally {
            GL11.glReadBuffer(previousReadBuffer);
        }
        waterAttachmentDeltaProbeLogs++;
        MainMod.LOGGER.warn("[AUSMWaterAttachmentDelta] call={} {} gl={}",
                waterAttachmentDeltaProbeLogs, result, PipelineRuntimeState.glStateSummary());
    }

    protected boolean shouldRefreshNothiriumNonSolidListsBeforeDraw(BlockRenderLayer layer) {
        return PipelineRuntimeState.isNothiriumNonSolidTerrainLayer(layer)
                && self().isNothiriumNonSolidMainTerrainPass(layer)
                && nothiriumShaderedMainPostCompileSetupFrame != pipelineFrameId;
    }

    protected boolean shouldRetrySparseNothiriumTerrainAfterSetup(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected boolean shouldRepairSparseNothiriumMainTerrain(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected boolean isNothiriumSparseMainTerrainRepairPass(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                && self().getPhase() == WorldRenderingPhase.TERRAIN_SOLID
                && (activePass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || activePass == RenderPass.GBUFFERS_TERRAIN);
    }

    protected boolean shouldDrawSparseNothiriumMainLayerFromProvider(BlockRenderLayer layer, int visibleCount) {
        return visibleCount >= 0
                && visibleCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                && self().isNothiriumSparseMainProviderDrawPass(layer);
    }

    protected boolean isNothiriumSparseMainProviderDrawPass(BlockRenderLayer layer) {
        WorldRenderingPhase phase = self().getPhase();
        if (layer == BlockRenderLayer.SOLID) {
            return phase == WorldRenderingPhase.TERRAIN_SOLID
                    && (activePass == RenderPass.GBUFFERS_TERRAIN_SOLID
                    || activePass == RenderPass.GBUFFERS_TERRAIN);
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT;
        }
        return false;
    }

    protected void enableNothiriumSparseMainProviderDraw() {
        nothiriumSparseMainProviderDrawUntilFrame = Math.max(
                nothiriumSparseMainProviderDrawUntilFrame,
                pipelineFrameId + NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_FRAMES
        );
    }

    protected double nothiriumSparseMainProviderDrawDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                ? NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_DISTANCE
                : NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_DISTANCE;
    }

    protected int nothiriumSparseMainProviderDrawMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_MAX_CHUNKS;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_MAX_CHUNKS;
        }
        return 0;
    }

    protected PipelineRuntimeState.NothiriumSparseMainRepairResult repairSparseNothiriumMainTerrain(int visibleCount,
                                                                                                    double cameraX,
                                                                                                    double cameraY,
                                                                                                    double cameraZ) {
        nothiriumSparseMainRepairFrame = pipelineFrameId;
        int solid = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.SOLID,
                cameraX,
                cameraY,
                cameraZ,
                192.0D,
                96
        );
        int cutoutMipped = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.CUTOUT_MIPPED,
                cameraX,
                cameraY,
                cameraZ,
                160.0D,
                64
        );
        int cutout = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.CUTOUT,
                cameraX,
                cameraY,
                cameraZ,
                160.0D,
                64
        );
        nothiriumShadowRenderer.drainUploads();
        boolean setup = self().forceSetupNothiriumShaderedMainTerrainListsAfterRepair();
        PipelineRuntimeState.NothiriumSparseMainRepairResult result = new PipelineRuntimeState.NothiriumSparseMainRepairResult(solid, cutoutMipped, cutout, setup);
        self().logNothiriumSparseMainRepair(visibleCount, result, cameraX, cameraY, cameraZ);
        return result;
    }

    protected boolean shouldSupplementSparseNothiriumTerrainFromProvider(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected double nothiriumProviderSparseTerrainDistance(BlockRenderLayer layer) {
        // Nothirium's provider fallback must not hide a ready VBO behind a
        // second AUSM distance cap. Nothirium has already validated the chunk
        // and the draw path still rejects invalid buffers and ranges.
        return -1.0D;
    }

    protected int nothiriumProviderSparseTerrainMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 128;
        }
        if (layer == BlockRenderLayer.SOLID) {
            return 384;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return 256;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return 192;
        }
        return 0;
    }

    protected boolean shouldScheduleNothiriumProviderSupplementCompiles(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                || layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT
                || layer == BlockRenderLayer.TRANSLUCENT;
    }

    protected boolean shouldRepairEmptyNothiriumNonSolidLayer(BlockRenderLayer layer, int visibleCount) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || visibleCount != 0
                || !PipelineRuntimeState.isNothiriumNonSolidTerrainLayer(layer)
                || !self().isNothiriumNonSolidMainTerrainPass(layer)) {
            return false;
        }
        long lastFrame = self().nothiriumNonSolidRepairFrame(layer);
        return lastFrame == Long.MIN_VALUE
                || pipelineFrameId - lastFrame >= NOTHIRIUM_NON_SOLID_REPAIR_COOLDOWN_FRAMES;
    }

    protected static boolean isNothiriumNonSolidTerrainLayer(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT
                || layer == BlockRenderLayer.TRANSLUCENT;
    }

    protected boolean isNothiriumNonSolidMainTerrainPass(BlockRenderLayer layer) {
        WorldRenderingPhase phase = self().getPhase();
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT;
        }
        return layer == BlockRenderLayer.TRANSLUCENT
                && phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                && activePass == RenderPass.GBUFFERS_WATER;
    }

    protected long nothiriumNonSolidRepairFrame(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return nothiriumNonSolidRepairCutoutMippedFrame;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return nothiriumNonSolidRepairCutoutFrame;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return nothiriumNonSolidRepairTranslucentFrame;
        }
        return Long.MIN_VALUE;
    }

    protected void markNothiriumNonSolidRepairAttempt(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            nothiriumNonSolidRepairCutoutMippedFrame = pipelineFrameId;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            nothiriumNonSolidRepairCutoutFrame = pipelineFrameId;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            nothiriumNonSolidRepairTranslucentFrame = pipelineFrameId;
        }
    }

    protected void enableNothiriumNonSolidProviderDraw(BlockRenderLayer layer) {
        long untilFrame = pipelineFrameId + NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_FRAMES;
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            nothiriumNonSolidProviderDrawCutoutMippedUntilFrame = untilFrame;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            nothiriumNonSolidProviderDrawCutoutUntilFrame = untilFrame;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            nothiriumNonSolidProviderDrawTranslucentUntilFrame = untilFrame;
        }
    }

    protected boolean shouldDrawEmptyNothiriumNonSolidLayerFromProvider(BlockRenderLayer layer, int visibleCount) {
        return visibleCount == 0
                && PipelineRuntimeState.isNothiriumNonSolidTerrainLayer(layer)
                && self().isNothiriumNonSolidMainTerrainPass(layer);
    }

    protected long nothiriumNonSolidProviderDrawUntilFrame(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return nothiriumNonSolidProviderDrawCutoutMippedUntilFrame;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return nothiriumNonSolidProviderDrawCutoutUntilFrame;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return nothiriumNonSolidProviderDrawTranslucentUntilFrame;
        }
        return Long.MIN_VALUE;
    }

    protected double nothiriumNonSolidRepairDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT ? 128.0D : 160.0D;
    }

    protected int nothiriumNonSolidRepairMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 64;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return 96;
        }
        return 0;
    }

    protected double nothiriumNonSolidProviderDrawDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT ? 96.0D : 128.0D;
    }

    protected int nothiriumNonSolidProviderDrawMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 96;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return 96;
        }
        return 0;
    }

    protected void logNothiriumSparseMainRepair(int visibleCount, PipelineRuntimeState.NothiriumSparseMainRepairResult repair,
                                                double cameraX, double cameraY, double cameraZ) {
        if (nothiriumSparseMainRepairLogs++ >= MAX_NOTHIRIUM_SPARSE_MAIN_REPAIR_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumSparseMainRepair] call={} visible={} solidWork={} cutoutMippedWork={} cutoutWork={} setup={} frame={} activePass={} phase={} camera={}/{}/{} gl={}",
                nothiriumSparseMainRepairLogs,
                visibleCount,
                repair.solidWork,
                repair.cutoutMippedWork,
                repair.cutoutWork,
                repair.setup,
                pipelineFrameId,
                String.valueOf(activePass),
                self().getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                PipelineRuntimeState.glStateSummary()
        );
    }

    protected void logNothiriumNonSolidRepair(BlockRenderLayer layer, int scheduled, boolean setup,
                                              double cameraX, double cameraY, double cameraZ) {
        if (nothiriumNonSolidRepairLogs++ >= MAX_NOTHIRIUM_NON_SOLID_REPAIR_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumNonSolidRepair] call={} layer={} scheduled={} setup={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} gl={}",
                nothiriumNonSolidRepairLogs,
                layer,
                scheduled,
                setup,
                pipelineFrameId,
                String.valueOf(activePass),
                self().getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                self().nothiriumNonSolidRepairMaxChunks(layer),
                self().nothiriumNonSolidRepairDistance(layer),
                PipelineRuntimeState.glStateSummary()
        );
    }

    protected void logNothiriumNonSolidProviderDraw(BlockRenderLayer layer, int providerCount,
                                                    double cameraX, double cameraY, double cameraZ) {
        if (nothiriumNonSolidProviderDrawLogs++ >= MAX_NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumNonSolidProviderDraw] call={} layer={} providerCount={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} untilFrame={} gl={}",
                nothiriumNonSolidProviderDrawLogs,
                layer,
                providerCount,
                pipelineFrameId,
                String.valueOf(activePass),
                self().getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                self().nothiriumNonSolidProviderDrawMaxChunks(layer),
                self().nothiriumNonSolidProviderDrawDistance(layer),
                self().nothiriumNonSolidProviderDrawUntilFrame(layer),
                PipelineRuntimeState.glStateSummary()
        );
    }
}
