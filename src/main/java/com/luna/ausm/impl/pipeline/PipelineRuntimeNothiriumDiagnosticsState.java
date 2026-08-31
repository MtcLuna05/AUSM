package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_FOG_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_RENDER_PROBE_LOGS;

abstract class PipelineRuntimeDiagnosticsState3 extends PipelineRuntimeDiagnosticsState2 {
    public void logNothiriumRenderProbe(String renderer, String stage, Object pass) {
        if (nothiriumRenderProbeLogs >= MAX_NOTHIRIUM_RENDER_PROBE_LOGS) {
            return;
        }
        nothiriumRenderProbeLogs++;
        MainMod.LOGGER.info("[AUSMNothiriumRender] renderer={} stage={} pass={} active={} bpPass={} gl={}",
                renderer,
                stage,
                String.valueOf(pass),
                isPipelineActive,
                BetterPortalsCompat.isRenderingRenderPass(),
                PipelineRuntimeState.glStateSummary());
    }

    public void logNothiriumFogProbe(String stage, boolean enabled, int mode, float start, float end, float density,
                                     float[] original, float[] adjusted) {
        if (nothiriumFogProbeLogs >= MAX_NOTHIRIUM_FOG_PROBE_LOGS) {
            return;
        }
        nothiriumFogProbeLogs++;
        MainMod.LOGGER.info("[AUSMNothiriumFog] stage={} enabled={} mode={} start={} end={} density={} original={} adjusted={}",
                stage,
                enabled,
                mode,
                PipelineRuntimeState.formatProbeFloat(start),
                PipelineRuntimeState.formatProbeFloat(end),
                PipelineRuntimeState.formatProbeFloat(density),
                PipelineRuntimeState.formatNothiriumProbeColor(original),
                PipelineRuntimeState.formatNothiriumProbeColor(adjusted));
    }

    protected static String formatNothiriumProbeColor(float[] color) {
        if (color == null || color.length < 4) {
            return "(nan,nan,nan,nan)";
        }
        return "("
                + PipelineRuntimeState.formatProbeFloat(color[0]) + ','
                + PipelineRuntimeState.formatProbeFloat(color[1]) + ','
                + PipelineRuntimeState.formatProbeFloat(color[2]) + ','
                + PipelineRuntimeState.formatProbeFloat(color[3]) + ')';
    }

    protected boolean shouldDisableShaderlessNothiriumTerrainFog() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        return !isPipelineActive
                && world != null
                && self().isOverworldShaderEnvironment(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public int repairShaderlessVoidWorldPackedLight(IBlockAccess blockAccess, BlockPos pos, int packedLight) {
        if (!self().shouldRepairShaderlessVoidWorldSkyLight(pos)) {
            return packedLight;
        }
        int skyLight = packedLight >> 20 & 15;
        if (skyLight >= 15) {
            return packedLight;
        }
        int repaired = packedLight | 0x00F00000;
        self().logShaderlessVoidLightRepair("packed", blockAccess, pos, packedLight, repaired, 0);
        return repaired;
    }

    public int repairShaderlessVoidWorldCombinedLight(BlockPos pos, int lightValue, int packedLight) {
        if (!self().shouldRepairShaderlessVoidWorldSkyLight(pos)) {
            return packedLight;
        }
        int skyLight = packedLight >> 20 & 15;
        int blockLight = packedLight >> 4 & 15;
        int requestedBlockLight = PipelineRuntimeState.clampInt(lightValue, 0, 15);
        if (skyLight >= 15 && blockLight >= requestedBlockLight) {
            return packedLight;
        }
        int repaired = packedLight | 0x00F00000;
        if (requestedBlockLight > blockLight) {
            repaired = (repaired & ~0xF0) | (requestedBlockLight << 4);
        }
        self().logShaderlessVoidLightRepair("combined", null, pos, packedLight, repaired, lightValue);
        return repaired;
    }

    protected boolean shouldRepairShaderlessVoidWorldSkyLight(BlockPos pos) {
        if (isPipelineActive
                || pos == null
                || !shaderlessVoidWorldSkyLightEligible
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return false;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        if (world == null) {
            return false;
        }
        try {
            BlockPos skyProbePos = MinecraftReflectionCompat.blockPosUp(pos);
            return MinecraftReflectionCompat.worldIsBlockLoaded(world, skyProbePos)
                    && MinecraftReflectionCompat.worldCanSeeSky(world, skyProbePos);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    protected void refreshShaderlessVoidWorldSkyLightEligibility() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        shaderlessVoidWorldSkyLightEligible = !isPipelineActive
                && world != null
                && self().isOverworldShaderEnvironment(world);
    }

    protected void logShaderlessVoidLightRepair(String source, IBlockAccess blockAccess, BlockPos pos, int before, int after, int lightValue) {
        // Probe disabled.
    }

    public void probeShaderlessVoidSkyFramebufferPixels(String stage) {
        // Probe disabled.
    }

    public void probeWorldPassSkyDome(String stage) {
        self().logWorldPassSkyDomeProbe(stage);
    }

    public void probeShaderlessSolidTerrainSky(String stage) {
        // Probe disabled.
    }

    public void captureShaderlessWorldFramebufferForUi() {
        // EntityRenderer invokes this immediately after renderWorld returns.
        // finishShaderlessWorldPassRendering has necessarily closed the scope
        // by then, so shaderlessWorldPassActive must already be false here.
        if (isPipelineActive || self().isRenderingBetterPortalsNestedView() || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            shaderlessWorldFramebufferForUi = 0;
            shaderlessWorldFramebufferFrame = Long.MIN_VALUE;
            return;
        }

        int drawFramebuffer = self().currentDrawFramebufferBinding();
        if (drawFramebuffer <= 0) {
            return;
        }

        shaderlessWorldFramebufferForUi = drawFramebuffer;
        shaderlessWorldFramebufferWidth = Math.max(1, MinecraftReflectionCompat.displayWidth(mc));
        shaderlessWorldFramebufferHeight = Math.max(1, MinecraftReflectionCompat.displayHeight(mc));
        shaderlessWorldFramebufferFrame = clientRenderFrameNanos;
        self().logShaderlessWorldFramebufferHandoff(
                "capture",
                "drawFramebuffer=" + drawFramebuffer
                        + ", mcFramebuffer=" + self().describeFramebufferTarget(MinecraftReflectionCompat.minecraftFramebuffer(mc)));
    }

    public void syncShaderlessWorldFramebufferBeforeGui() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (isPipelineActive
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()
                || shaderlessWorldFramebufferForUi <= 0
                || shaderlessWorldFramebufferFrame != clientRenderFrameNanos) {
            return;
        }

        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (MinecraftReflectionCompat.framebufferObject(target) == shaderlessWorldFramebufferForUi) {
            return;
        }

        self().logShaderlessWorldFramebufferHandoff(
                "sync-before-blit",
                "source=" + shaderlessWorldFramebufferForUi
                        + ", target=" + self().describeFramebufferTarget(target));
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, shaderlessWorldFramebufferForUi);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(target));
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    shaderlessWorldFramebufferWidth,
                    shaderlessWorldFramebufferHeight,
                    0,
                    0,
                    MinecraftReflectionCompat.framebufferWidth(target),
                    MinecraftReflectionCompat.framebufferHeight(target),
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            self().logShaderlessWorldFramebufferHandoff(
                    "sync-after-blit-bound",
                    "source=" + shaderlessWorldFramebufferForUi
                            + ", target=" + self().describeFramebufferTarget(target));
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            self().restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
        }
        self().logShaderlessWorldFramebufferHandoff(
                "sync-after-blit-restored",
                "source=" + shaderlessWorldFramebufferForUi
                        + ", target=" + self().describeFramebufferTarget(target));
    }

    protected void logShaderlessWorldFramebufferHandoff(String stage, String detail) {
        // Explicit frame-handoff diagnostics are disabled outside focused investigations.
    }

    protected String sampleFramebufferForHandoff(int framebuffer, int width, int height) {
        if (framebuffer <= 0 || width <= 0 || height <= 0) {
            return "invalid";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return self().sampleBoundReadFramebuffer(width, height, true);
        } catch (RuntimeException | LinkageError ignored) {
            return "unreadable";
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            self().restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    public void repairShaderlessVoidSkyBeforeGui(float partialTicks) {
        // Old sky repair/probe path intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    protected void renderShaderlessVoidSkyRepair(Minecraft mc, float partialTicks) {
        // Probe disabled.
    }

    protected PipelineRuntimeState.VoidSkyRepairSamples sampleVoidSkyRepairPixels(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new PipelineRuntimeState.VoidSkyRepairSamples(false, "invalid-size");
        }
        int[] xs = new int[]{width / 4, width / 2, Math.max(0, width * 3 / 4)};
        int[] ys = new int[]{height / 4, height / 2, Math.max(0, height * 3 / 4)};
        StringBuilder summary = new StringBuilder();
        boolean needsRepair = false;
        for (int y : ys) {
            for (int x : xs) {
                PipelineRuntimeState.VoidSkyRepairPixel pixel = self().readFramebufferRepairPixel(x, y);
                if (summary.length() > 0) {
                    summary.append(';');
                }
                summary.append(pixel.summary(x, y));
                if (pixel.skyDepth() && pixel.brightness() <= 12) {
                    needsRepair = true;
                }
            }
        }
        return new PipelineRuntimeState.VoidSkyRepairSamples(needsRepair, summary.toString());
    }

    protected PipelineRuntimeState.VoidSkyRepairPixel readFramebufferRepairPixel(int x, int y) {
        try {
            IntBuffer color = BufferUtils.createIntBuffer(1);
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            int rgba = color.get(0);
            int r = rgba & 0xFF;
            int g = rgba >> 8 & 0xFF;
            int b = rgba >> 16 & 0xFF;
            int a = rgba >> 24 & 0xFF;
            float z = depth.get(0);
            return new PipelineRuntimeState.VoidSkyRepairPixel(r, g, b, a, z);
        } catch (RuntimeException | LinkageError ignored) {
            return new PipelineRuntimeState.VoidSkyRepairPixel(-1, -1, -1, -1, -1.0F);
        }
    }

    protected void logShaderlessVoidSkyRepair(String stage, String detail) {
        // Diagnostic disabled.
    }

    protected String readFramebufferPixelSummary(int x, int y) {
        return self().readFramebufferPixelSummary(x, y, true);
    }

    protected String readFramebufferPixelSummary(int x, int y, boolean includeDepth) {
        try {
            IntBuffer color = BufferUtils.createIntBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            int rgba = color.get(0);
            int r = rgba & 0xFF;
            int g = rgba >> 8 & 0xFF;
            int b = rgba >> 16 & 0xFF;
            int a = rgba >> 24 & 0xFF;
            if (!includeDepth) {
                return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ")";
            }
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ") depth=" + depth.get(0);
        } catch (RuntimeException | LinkageError ignored) {
            return x + "," + y + "=unreadable";
        }
    }

    protected String formatFloatArray(float[] values) {
        if (values == null || values.length < 4) {
            return "null";
        }
        return values[0] + "," + values[1] + "," + values[2] + "," + values[3];
    }

    public boolean shouldRenderClouds() {
        return !isPipelineActive || !self().shouldSkipAllMainGbufferRendering() && !"off".equals(shaderProperties.renderSettings().clouds());
    }

    public boolean shouldSkipAllMainGbufferRendering() {
        return isPipelineActive
                && !renderingShadowMap
                && shaderProperties.renderSettings().skipAllRendering();
    }

    public void applyTerrainOcclusionCullingSetting() {
        if (!isPipelineActive
                || terrainOcclusionOverrideActive
                || shaderProperties.renderSettings().occlusionCulling()) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        previousRenderChunksManyForOcclusion = MinecraftReflectionCompat.fieldBoolean(mc, false, "field_175612_E", "renderChunksMany");
        terrainOcclusionOverrideActive = true;
        MinecraftReflectionCompat.setRenderChunksMany(mc, false);
    }

    public void restoreTerrainOcclusionCullingSetting() {
        if (!terrainOcclusionOverrideActive) {
            return;
        }
        terrainOcclusionOverrideActive = false;
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null) {
            MinecraftReflectionCompat.setRenderChunksMany(mc, previousRenderChunksManyForOcclusion);
        }
    }

    public ICamera mainFrustumCullingCamera(ICamera camera) {
        if (!isPipelineActive || shaderProperties.renderSettings().frustumCulling()) {
            return camera;
        }
        return ALWAYS_VISIBLE_CAMERA;
    }

    public boolean shouldCullShadowTerrain() {
        return !isPipelineActive || shaderProperties.renderSettings().shadowCulling();
    }

    public void applySkySunPathRotation() {
        if (isPipelineActive && sunPathRotation != 0.0f) {
            MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179114_b", "rotate"},
                    new Class<?>[]{float.class, float.class, float.class, float.class}, sunPathRotation, 0.0F, 0.0F, 1.0F);
        }
    }

    public void applyTerrainCulling(WorldRenderingPhase phase) {
        if (!isPipelineActive || terrainCullOverrideActive || !self().shouldDisableCullForPhase(phase)) {
            return;
        }
        previousTerrainCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        terrainCullOverrideActive = true;
        MinecraftReflectionCompat.glStateDisableCull();
    }

    public void restoreTerrainCulling() {
        if (!terrainCullOverrideActive) {
            return;
        }
        terrainCullOverrideActive = false;
        if (previousTerrainCullEnabled) {
            MinecraftReflectionCompat.glStateEnableCull();
        } else {
            MinecraftReflectionCompat.glStateDisableCull();
        }
    }

    public boolean shouldDisableNothiriumChunkCulling(BlockRenderLayer layer) {
        if (renderingShadowMap || layer == null) {
            return false;
        }
        // Shaderless AUSM bloom runs with the main shader pipeline inactive, but
        // it still consumes the same copied Blockcraftery overlay VBOs. Those
        // custom-shape faces are not guaranteed to retain vanilla winding.
        if (AusmBloomLayer.isBloomLayer(layer)) {
            return true;
        }
        if (!isPipelineActive) {
            return false;
        }
        return self().shouldDisableCullForPhase(self().getPhase());
    }

    protected boolean shouldDisableCullForPhase(WorldRenderingPhase phase) {
        if (phase == WorldRenderingPhase.TERRAIN_SOLID) {
            return shaderProperties.renderSettings().backFaceSolid();
        }
        if (phase == WorldRenderingPhase.TERRAIN_CUTOUT) {
            return shaderProperties.renderSettings().backFaceCutout();
        }
        if (phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED) {
            return shaderProperties.renderSettings().backFaceCutoutMipped();
        }
        return phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                && shaderProperties.renderSettings().backFaceTranslucent();
    }

    public boolean shouldUsePipelineEntityFormat() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || !MinecraftReflectionCompat.callBoolean(mc, new String[]{"func_152345_ab", "isCallingFromMinecraftThread"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            return false;
        }
        RenderPass pass = activePass;
        if (!isPipelineActive || !worldFrameActive || pass == null || self().renderingGuiScreen()) {
            return false;
        }
        if (PipelineRuntimeState.isBetweenlandsEntity(currentEntityKey) || currentEntityKey == null && PipelineRuntimeState.isBetweenlandsRenderStack()) {
            return false;
        }
        if (pass.stage() == ProgramStage.SHADOW) {
            return true;
        }
        WorldRenderingPhase phase = self().getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesEntityFormat();
        }
        return pass.stage() == ProgramStage.SHADOW
                || pass == RenderPass.GBUFFERS_ITEM
                || pass == RenderPass.GBUFFERS_ENTITIES
                || pass == RenderPass.GBUFFERS_ENTITIES_GLOWING
                || pass == RenderPass.GBUFFERS_HAND
                || pass == RenderPass.GBUFFERS_HAND_WATER
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT;
    }

    public boolean shouldUsePipelineBlockFormat() {
        return self().pipelineTerrainFormatSupported();
    }

    public boolean isPipelineActive() {
        return isPipelineActive;
    }

    /**
     * Records an entire affected render route without changing render state.
     * This is intentionally much broader than the former one-digit probes:
     * the event stream is scoped to an active world frame and carries enough
     * state to reconstruct ordering, timing, and target ownership.
     */
    public void forensicTrace(String route, String detail) {
        // Probe disabled.
    }

    /**
     * Render-thread variant including the complete mutable GL target state.
     */
    public void forensicGlTrace(String route, String detail) {
        // Probe disabled.
    }

    protected boolean shouldUseShaderlessBloomVertexMetadata() {
        return self().shouldUsePipelineBlockFormat()
                && !isPipelineActive
                && !AusmBloomLayer.shouldUseShaderlessNativeHook()
                && bloomRenderer.hasBloomResources();
    }

    public boolean isShadowPassActive() {
        return isPipelineActive && (renderingShadowMap || activePass != null && activePass.stage() == ProgramStage.SHADOW);
    }
}
