package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static com.luna.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SOFT_VANILLA_LAYER_TIMING_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_WATER_DUPLICATE_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_WORLD_LAYER_DIAG_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES;

abstract class PipelineFrameLifecycle1 extends PipelineFrameLifecycle0 {
    protected static String formatProbeColor(float[] color) {
        if (color == null || color.length < 4) {
            return "(nan,nan,nan,nan)";
        }
        return "("
                + PipelineWorldRenderScope.formatProbeFloat(color[0]) + ','
                + PipelineWorldRenderScope.formatProbeFloat(color[1]) + ','
                + PipelineWorldRenderScope.formatProbeFloat(color[2]) + ','
                + PipelineWorldRenderScope.formatProbeFloat(color[3]) + ')';
    }

    protected static String formatProbeFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public int renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        PipelineFrameLayerCapture.beginWorldIfRequested(pipelineFrameId);
        if (renderGlobal == null) {
            self().logWorldLayerDiag("skip-null-render-global", layer, pass, 0, viewEntity);
            return 0;
        }
        if (shouldSkipAllMainGbufferRendering()) {
            self().recordTerrainLayerCount(layer, 0);
            self().logWorldLayerDiag("skip-all-rendering", layer, pass, 0, viewEntity);
            return 0;
        }
        if (self().shouldSuppressDuplicatePipelineTranslucentLayer(layer)) {
            self().logWorldLayerDiag("skip-duplicate-translucent", layer, pass, 0, viewEntity);
            return 0;
        }

        boolean prepareVanillaState = self().shouldPrepareShaderlessBlockLayerState();
        if (prepareVanillaState) {
            self().prepareShaderlessBlockLayerState(layer);
        }

        try {
            boolean forceVanillaRenderer = self().shouldForceVanillaTerrainRenderer();
            int nothiriumCount = forceVanillaRenderer
                    ? -1
                    : renderNothiriumTerrainLayer(layer, (float) partialTicks, viewEntity);
            if (nothiriumCount >= 0) {
                self().rebindActiveTerrainPassAfterNothiriumNativeDraw();
                if (nothiriumCount > 0) {
                    self().markNothiriumPipelineTranslucentBridge(layer);
                }
                self().recordTerrainLayerCount(layer, nothiriumCount, true);
                self().recordShaderlessTerrainLayerCount(layer, nothiriumCount);
                self().probePositiveNothiriumTerrainDraw(layer, nothiriumCount);
                self().logWorldLayerDiag("nothirium", layer, pass, nothiriumCount, viewEntity);
                return nothiriumCount;
            }
            boolean forceVanillaFallback = isPipelineActive
                    && (forceVanillaRenderer || NothiriumBypass.shouldBypass());
            if (forceVanillaFallback) {
                int count = self().renderForcedVanillaTerrainLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
                self().recordTerrainLayerCount(layer, count);
                self().recordShaderlessTerrainLayerCount(layer, count);
                self().probePositiveVanillaTerrainDraw(layer, count);
                self().logWorldLayerDiag("vanilla-forced-bypass", layer, pass, count, viewEntity);
                return count;
            }
            int count = NothiriumBypass.shouldBypass()
                    ? self().renderForcedVanillaTerrainLayer(renderGlobal, layer, partialTicks, pass, viewEntity)
                    : MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
            self().rebindActiveTerrainPassForForcedVanillaFallback();
            self().recordTerrainLayerCount(layer, count);
            self().recordShaderlessTerrainLayerCount(layer, count);
            self().logWorldLayerDiag("vanilla", layer, pass, count, viewEntity);
            return count;
        } finally {
            if (prepareVanillaState) {
                self().finishShaderlessBlockLayerState(layer);
            }
            // The shaderless main framebuffer's depth is cleared by the
            // later world-pass presentation path. Render Bloom immediately
            // after the translucent terrain boundary, while that depth is
            // still attached and can reject emitters behind opaque geometry.
            if (!isPipelineActive && layer == BlockRenderLayer.TRANSLUCENT) {
                PipelineContext.getInstance().renderNativeAusmBloomLayerFromWorldPass((float) partialTicks, pass);
            }
        }
    }

    protected int renderForcedVanillaTerrainLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks,
                                                  int pass, Entity viewEntity) {
        boolean timingProbe = isComplementarySoftVanillaStartupFallbackActive();
        long startNanos = timingProbe ? System.nanoTime() : 0L;
        long afterEnsureNanos = startNanos;
        long afterRebindNanos = startNanos;
        int count = Integer.MIN_VALUE;
        self().ensureVanillaTerrainRenderer(renderWorld(MinecraftReflectionCompat.minecraft()), false);
        if (timingProbe) {
            afterEnsureNanos = System.nanoTime();
        }
        if (!self().shouldUseHardwareSafeVanillaBlockLayerState()) {
            self().rebindActiveTerrainPassForForcedVanillaFallback();
        }
        if (timingProbe) {
            afterRebindNanos = System.nanoTime();
        }
        NothiriumBypass.pushForcedBypass();
        try {
            count = MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
            return count;
        } finally {
            NothiriumBypass.popForcedBypass();
            if (timingProbe) {
                self().logSoftVanillaLayerTiming(layer, pass, count, startNanos, afterEnsureNanos, afterRebindNanos, System.nanoTime(), viewEntity);
            }
        }
    }

    protected void logSoftVanillaLayerTiming(BlockRenderLayer layer, int pass, int count, long startNanos,
                                             long afterEnsureNanos, long afterRebindNanos, long endNanos,
                                             Entity viewEntity) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaLayerTimingLogs >= MAX_SOFT_VANILLA_LAYER_TIMING_LOGS) {
            return;
        }
        double totalMs = PipelineWorldRenderScope.nanosToMillis(endNanos - startNanos);
        if (softVanillaLayerTimingLogs >= 32 && totalMs < 8.0D) {
            return;
        }
        softVanillaLayerTimingLogs++;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaTiming] call={} stage=renderBlockLayer layer={} pass={} count={} totalMs={} ensureMs={} rebindMs={} drawMs={} frame={} frameTime={} opaqueLayers={} opaqueDraws={} view={} glProgram={}",
                softVanillaLayerTimingLogs,
                layer,
                pass,
                count,
                PipelineWorldRenderScope.formatMillis(totalMs),
                PipelineWorldRenderScope.formatMillis(PipelineWorldRenderScope.nanosToMillis(afterEnsureNanos - startNanos)),
                PipelineWorldRenderScope.formatMillis(PipelineWorldRenderScope.nanosToMillis(afterRebindNanos - afterEnsureNanos)),
                PipelineWorldRenderScope.formatMillis(PipelineWorldRenderScope.nanosToMillis(endNanos - afterRebindNanos)),
                pipelineFrameId,
                PipelineWorldRenderScope.formatMillis(currentFrameTime * 1000.0D),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                viewEntity != null ? viewEntity.getClass().getName() : "null",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected void probePositiveVanillaTerrainDraw(BlockRenderLayer layer, int count) {
        if (!(shaderedNothiriumGlobalBypass || nothiriumMainVanillaDrawPathFrames > 0)
                || count <= 0
                || positiveVanillaTerrainProbeLogs >= MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS
                || layer == null
                || layer == BlockRenderLayer.TRANSLUCENT) {
            return;
        }
        positiveVanillaTerrainProbeLogs++;
        self().logColorBufferProbe("after-positive-vanilla-" + layer, true);
    }

    protected void probePositiveNothiriumTerrainDraw(BlockRenderLayer layer, int count) {
        if (!isPipelineActive
                || count <= 0
                || positiveNothiriumTerrainProbeLogs >= MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS
                || layer == null
                || layer == BlockRenderLayer.TRANSLUCENT) {
            return;
        }
        positiveNothiriumTerrainProbeLogs++;
        self().logColorBufferProbe("after-positive-nothirium-" + layer, true);
    }

    protected void rebindActiveTerrainPassForForcedVanillaFallback() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase == WorldRenderingPhase.NONE || !phase.usesBlockAtlas()) {
            return;
        }
        bindPass(activePass);
    }

    protected void rebindActiveTerrainPassAfterNothiriumNativeDraw() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        bindPass(activePass);
    }

    protected void logWorldLayerDiag(String stage, BlockRenderLayer layer, int pass, int count, Entity viewEntity) {
        if (worldLayerDiagLogs >= MAX_WORLD_LAYER_DIAG_LOGS) {
            return;
        }
        if (!isPipelineActive && !"skip-null-render-global".equals(stage)) {
            return;
        }
        if (!stage.startsWith("vanilla") && !"nothirium".equals(stage) && !"skip-all-rendering".equals(stage)) {
            return;
        }
        worldLayerDiagLogs++;
        MainMod.LOGGER.info(
                "[AUSMWorldLayer] call={} stage={} layer={} pass={} count={} active={} safeVanilla={} reason='{}' nothiriumBypass={} activePass={} phase={} frame={} worldFrame={} view={} gl={}",
                worldLayerDiagLogs,
                stage,
                layer,
                pass,
                count,
                isPipelineActive,
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                NothiriumBypass.shouldBypass(),
                activePass,
                getPhase(),
                pipelineFrameId,
                worldFrameActive,
                viewEntity != null ? viewEntity.getClass().getName() : "null",
                glStateSummary()
        );
    }

    protected void markNothiriumPipelineTranslucentBridge(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || !isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass != RenderPass.GBUFFERS_WATER
                || getPhase() != WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            return;
        }

        nothiriumPipelineTranslucentFrame = pipelineFrameId;
        nothiriumPipelineTranslucentWorldPassSerial = currentWorldPassSerial;
        nothiriumPipelineTranslucentDrawnFrame = pipelineFrameId;
        self().logWaterDuplicateProbe("nothirium-mark", layer, false);
    }

    protected boolean shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer layer) {
        boolean sameWorldPass = currentWorldPassSerial != Long.MIN_VALUE
                && nothiriumPipelineTranslucentWorldPassSerial == currentWorldPassSerial
                && nothiriumPipelineTranslucentFrame == pipelineFrameId;
        boolean samePipelineFrame = nothiriumPipelineTranslucentDrawnFrame == pipelineFrameId;
        boolean suppress = layer == BlockRenderLayer.TRANSLUCENT
                && isPipelineActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && (worldFrameActive || samePipelineFrame)
                && (sameWorldPass || samePipelineFrame)
                && !self().isPipelineTranslucentTerrainPhase();
        self().logWaterDuplicateProbe("duplicate-evaluate", layer, suppress);
        return suppress;
    }

    protected void logWaterDuplicateProbe(String stage, BlockRenderLayer layer, boolean suppress) {
        if (!isPipelineActive
                || layer != BlockRenderLayer.TRANSLUCENT
                || waterDuplicateProbeLogs >= MAX_WATER_DUPLICATE_PROBE_LOGS) {
            return;
        }
        waterDuplicateProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMWaterDuplicateProbe] call={} stage={} suppress={} frame={} worldPass={} markedFrame={} markedWorldPass={} drawnFrame={} activePass={} phase={} worldFrame={} shadow={} gui={}",
                waterDuplicateProbeLogs,
                stage,
                suppress,
                pipelineFrameId,
                currentWorldPassSerial,
                nothiriumPipelineTranslucentFrame,
                nothiriumPipelineTranslucentWorldPassSerial,
                nothiriumPipelineTranslucentDrawnFrame,
                activePass,
                getPhase(),
                worldFrameActive,
                renderingShadowMap,
                renderingGuiScreen());
    }

    protected boolean isPipelineTranslucentTerrainPhase() {
        return activePass == RenderPass.GBUFFERS_WATER
                && getPhase() == WorldRenderingPhase.TERRAIN_TRANSLUCENT;
    }

    protected void clearNothiriumPipelineTranslucentBridge() {
        nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;
        nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;
    }

    protected void beginWorldPassDuplicateTracking() {
        worldPassSerialStack.push(currentWorldPassSerial);
        nothiriumPipelineTranslucentFrameStack.push(nothiriumPipelineTranslucentFrame);
        nothiriumPipelineTranslucentWorldPassSerialStack.push(nothiriumPipelineTranslucentWorldPassSerial);
        currentWorldPassSerial = ++nextWorldPassSerial;
        self().clearNothiriumPipelineTranslucentBridge();
    }

    protected void finishWorldPassDuplicateTracking() {
        currentWorldPassSerial = worldPassSerialStack.isEmpty() ? Long.MIN_VALUE : worldPassSerialStack.pop();
        nothiriumPipelineTranslucentFrame = nothiriumPipelineTranslucentFrameStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentFrameStack.pop();
        nothiriumPipelineTranslucentWorldPassSerial = nothiriumPipelineTranslucentWorldPassSerialStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentWorldPassSerialStack.pop();
    }

    protected boolean shouldPrepareShaderlessBlockLayerState() {
        return !isPipelineActive || shouldBypassWorldPassRendering() || self().shouldUseHardwareSafeVanillaBlockLayerState();
    }

    protected boolean shouldUseHardwareSafeVanillaBlockLayerState() {
        return isPipelineActive
                && ENABLE_SAFE_TERRAIN_FALLBACKS
                && hardwareSafeVanillaTerrain
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen();
    }

    protected void prepareShaderlessBlockLayerState(BlockRenderLayer layer) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (!shaderlessBloomExtractionActive) {
            MinecraftReflectionCompat.glUseProgram(0);
        }
        if (self().shouldUseHardwareSafeVanillaBlockLayerState() && pingPongManager.isInitialized()) {
            pingPongManager.bindForGbuffers(fallbackColorAttachment());
        }
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        if (!shaderlessBloomExtractionActive) {
            disablePipelineVertexAttributes();
        }
        unbindShaderStorageBuffers();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        self().restoreVanillaFixedFunctionTextureState(mc);
        PipelineWorldRenderScope.restoreShaderlessTerrainClientTextureArrays();
        MinecraftReflectionCompat.glStateEnableDepth();

        if (PipelineWorldRenderScope.shouldRenderLayerWithTranslucentState(layer)) {
            FixedFunctionGlState.prepareTranslucentDepthBlendState();
            FixedFunctionGlState.forceTranslucentBlockLayer();
            return;
        }

        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateDisableBlend();
        if (layer == BlockRenderLayer.SOLID) {
            MinecraftReflectionCompat.glStateDisableAlpha();
        } else {
            MinecraftReflectionCompat.glStateEnableAlpha();
            MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    protected void finishShaderlessBlockLayerState(BlockRenderLayer layer) {
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        if (PipelineWorldRenderScope.shouldRenderLayerWithTranslucentState(layer)) {
            MinecraftReflectionCompat.glStateDepthMask(true);
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateEnableAlpha();
            MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    protected void beginShaderlessTerrainLightmapCoords() {
        if (isPipelineActive || shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        shaderlessTerrainPreviousLightmapX = MinecraftReflectionCompat.fieldFloat(OpenGlHelper.class, 0.0F, "lastBrightnessX", "lastBrightnessX");
        shaderlessTerrainPreviousLightmapY = MinecraftReflectionCompat.fieldFloat(OpenGlHelper.class, 0.0F, "lastBrightnessY", "lastBrightnessY");
        shaderlessTerrainLightmapCoordsSaved = true;
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_77475_a", "setLightmapTextureCoords"},
                new Class<?>[]{int.class, float.class, float.class}, MinecraftReflectionCompat.lightmapTexUnit(), 0.0F, 240.0F);
    }

    protected void restoreShaderlessTerrainLightmapCoords() {
        if (!shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        MinecraftReflectionCompat.invoke(OpenGlHelper.class, new String[]{"func_77475_a", "setLightmapTextureCoords"},
                new Class<?>[]{int.class, float.class, float.class}, MinecraftReflectionCompat.lightmapTexUnit(), shaderlessTerrainPreviousLightmapX, shaderlessTerrainPreviousLightmapY);
        shaderlessTerrainLightmapCoordsSaved = false;
    }

    protected static boolean shouldRenderLayerWithTranslucentState(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT || AusmBloomLayer.isBloomLayer(layer);
    }

    protected void recordTerrainLayerCount(BlockRenderLayer layer, int count) {
        self().recordTerrainLayerCount(layer, count, false);
    }

    protected void recordTerrainLayerCount(BlockRenderLayer layer, int count, boolean nothiriumMainTerrain) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || layer == null
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        if (terrainLayerCountFrame != pipelineFrameId) {
            terrainLayerCountFrame = pipelineFrameId;
            terrainOpaqueLayerCount = 0;
            terrainOpaqueDrawCount = 0;
        }

        if (layer == BlockRenderLayer.SOLID
                || layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT) {
            terrainOpaqueLayerCount++;
            terrainOpaqueDrawCount += Math.max(0, count);
            if (count > 0) {
                zeroOpaqueTerrainFrames = 0;
                zeroOpaqueTerrainRecoveryRequested = false;
            }
        }

        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            zeroOpaqueTerrainFrames = 0;
            sparseOpaqueTerrainFrames = 0;
            return;
        }

        if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount == 0) {
            if (self().hasLoadedTerrainNearPlayer()) {
                self().markSparseNothiriumMainTerrainFrame(nothiriumMainTerrain);
                zeroOpaqueTerrainFrames++;
                self().logHardwareTerrainFallback(
                        "zero-opaque-frame",
                        "frames=" + zeroOpaqueTerrainFrames
                                + ", activePass=" + activePass
                                + ", phase=" + getPhase()
                                + ", bypass=" + NothiriumBypass.shouldBypass()
                );
                if (zeroOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES) {
                    zeroOpaqueTerrainFrames = 0;
                    if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
                        zeroOpaqueTerrainRecoveryRequested = true;
                        self().logHardwareTerrainFallback(
                                "zero-opaque-nothirium-only",
                                "safe terrain fallback disabled; keeping Nothirium-only terrain path"
                        );
                        return;
                    }
                    if (softVanillaTerrainRenderer) {
                        self().logHardwareTerrainFallback(
                                "zero-opaque-soft-vanilla-failed",
                                "soft vanilla terrain still produced zero opaque draws; escalating to hardware-safe vanilla terrain"
                        );
                        self().activateHardwareSafeVanillaTerrain("soft-vanilla-zero-opaque");
                        return;
                    }
                    zeroOpaqueTerrainRecoveryRequested = true;
                    self().logHardwareTerrainFallback(
                            "zero-opaque-soft-vanilla",
                            "switching main terrain away from Nothirium after repeated zero opaque shader frames"
                    );
                    self().activateSoftVanillaTerrainRenderer("zero-opaque-nothirium-main");
                    return;
                }
                if (nothiriumMainTerrain && !softVanillaTerrainRenderer) {
                    sparseOpaqueTerrainFrames++;
                    if (sparseOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES) {
                        self().logHardwareTerrainFallback(
                                "zero-sparse-opaque-soft-vanilla",
                                "switching main terrain away from zero/sparse Nothirium after weak frames="
                                        + sparseOpaqueTerrainFrames
                        );
                        sparseOpaqueTerrainFrames = 0;
                        self().activateSoftVanillaTerrainRenderer("zero-sparse-opaque-nothirium-main");
                        return;
                    }
                } else if (!softVanillaTerrainRenderer) {
                    sparseOpaqueTerrainFrames = 0;
                }
            } else {
                sparseOpaqueTerrainFrames = 0;
                self().logHardwareTerrainFallback("zero-opaque-no-loaded-terrain", "world=" + self().describeWorld(MinecraftReflectionCompat.minecraft() != null ? MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) : null));
            }
        } else if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && (nothiriumMainTerrain || softVanillaTerrainRenderer)
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                && self().hasLoadedTerrainNearPlayer()) {
            self().markSparseNothiriumMainTerrainFrame(nothiriumMainTerrain);
            if (softVanillaTerrainRenderer
                    && isComplementarySoftVanillaStartupPack()
                    && terrainOpaqueDrawCount > 0) {
                sparseOpaqueTerrainFrames = 0;
                return;
            }
            sparseOpaqueTerrainFrames++;
            self().logHardwareTerrainFallback(
                    softVanillaTerrainRenderer ? "sparse-opaque-soft-vanilla-frame" : "sparse-opaque-frame",
                    "frames=" + sparseOpaqueTerrainFrames
                            + ", opaqueDraws=" + terrainOpaqueDrawCount
                            + ", minOpaqueDraws=" + HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                            + ", activePass=" + activePass
                            + ", phase=" + getPhase()
                            + ", bypass=" + NothiriumBypass.shouldBypass()
            );
            if (sparseOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES) {
                if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
                    sparseOpaqueTerrainFrames = 0;
                    self().logHardwareTerrainFallback(
                            "sparse-opaque-nothirium-only",
                            "safe terrain fallback disabled; keeping Nothirium-only terrain path"
                    );
                    return;
                }
                if (softVanillaTerrainRenderer) {
                    self().logHardwareTerrainFallback(
                            "sparse-opaque-soft-vanilla-failed",
                            "soft vanilla terrain stayed sparse after frames="
                                    + sparseOpaqueTerrainFrames
                                    + ", opaqueDraws=" + terrainOpaqueDrawCount
                                    + "; escalating to hardware-safe vanilla terrain"
                    );
                    sparseOpaqueTerrainFrames = 0;
                    self().activateHardwareSafeVanillaTerrain("soft-vanilla-sparse-opaque");
                    return;
                }
                self().logHardwareTerrainFallback(
                        "sparse-opaque-soft-vanilla",
                        "switching main terrain away from sparse Nothirium after frames="
                                + sparseOpaqueTerrainFrames
                                + ", opaqueDraws=" + terrainOpaqueDrawCount
                );
                sparseOpaqueTerrainFrames = 0;
                self().activateSoftVanillaTerrainRenderer("sparse-opaque-nothirium-main");
            }
        } else if (layer == BlockRenderLayer.CUTOUT && terrainOpaqueLayerCount >= 3) {
            sparseOpaqueTerrainFrames = 0;
        }
    }
}
