package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

abstract class PipelineBloomRendering extends PipelinePortalDiagnostics {
    public int renderShaderlessVisibleBloomLayerFromWorldPass(float partialTicks, int pass) {
        boolean nativeHook = AusmBloomLayer.shouldUseNativeHook();
        boolean nestedBetterPortalsPass = self().isRenderingBetterPortalsRenderPass() && self().isRenderingBetterPortalsNestedView();
        if (isPipelineActive
                || nativeHook
                || bloomLayerRenderedThisWorldPass
                || renderingGuiScreen()
                || renderingShadowMap
                || nestedBetterPortalsPass) {
            return 0;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null || bloomLayer == null) {
            return 0;
        }

        Framebuffer bloomTarget = self().isRenderingBetterPortalsRenderPass()
                ? BetterPortalsCompat.currentRenderPassFramebuffer()
                : null;
        if (bloomTarget == null) {
            bloomTarget = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        }

        int bloomRendered = 0;
        if (bloomTarget != null) {
            bloomRendered = bloomRenderer.renderBloomLayer(
                    MinecraftReflectionCompat.renderGlobal(mc),
                    partialTicks,
                    pass,
                    MinecraftReflectionCompat.renderViewEntity(mc),
                    null,
                    bloomTarget,
                    false
            );
        }

        int rendered = bloomRendered;
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        }

        return rendered;
    }

    protected void logVisibleBloomDiag(String stage, int pass, int rendered, String detail) {
        // Diagnostic disabled.
    }

    public int renderAusmBloomLayer(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity) {
        if (renderingGuiScreen() || renderingShadowMap) {
            return 0;
        }
        if (!AusmBloomLayer.shouldUseNativeHook()) {
            return 0;
        }
        if (self().isRenderingBetterPortalsRenderPass()) {
            self().requestDeferredNativeBloom(partialTicks, pass);
            return 0;
        }
        if (bloomLayerRenderedThisWorldPass) {
            return 0;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return 0;
        }

        pendingDeferredNativeBloom = false;
        Entity renderEntity = entity != null ? entity : MinecraftReflectionCompat.renderViewEntity(mc);
        Framebuffer minecraftTarget = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        DeferredFramebuffer pipelineDepthSource = isPipelineActive && worldFrameActive && pingPongManager.isInitialized()
                ? pingPongManager.getReadBuffer()
                : null;
        boolean deferComposite = pipelineDepthSource != null;

        int rendered = bloomRenderer.renderBloomLayer(
                renderGlobal,
                partialTicks,
                pass,
                renderEntity,
                pipelineDepthSource,
                minecraftTarget,
                deferComposite
        );
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        }
        self().recordBloomRenderResult(rendered);
        return rendered;
    }

    /**
     * Emits Nothirium-owned mesh data without invoking Nothirium's renderer.
     * AUSM keeps the active program, framebuffer, and fixed-function state.
     * A negative result means the caller should use vanilla RenderGlobal data.
     */
    public int renderAusmOwnedNothiriumBloomGeometry(double partialTicks, Entity viewEntity) {
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null
                || viewEntity == null
                || !PipelineContext.isNothiriumLoaded()
                || !NothiriumShadowRenderer.isAvailable()
                || self().shouldForceVanillaTerrainRenderer()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return -1;
        }
        if (AusmBloomLayer.consumeNothiriumRendererRecreateRequest()) {
            boolean recreated = NothiriumBypass.recreateRenderer();
            MainMod.LOGGER.info("[AUSMBloom] Recreated Nothirium mesh backend for BLOOM pass: {}", recreated);
            return 0;
        }

        double cameraX = interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity),
                MinecraftReflectionCompat.posX(viewEntity), (float) partialTicks);
        double cameraY = interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity),
                MinecraftReflectionCompat.posY(viewEntity), (float) partialTicks);
        double cameraZ = interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                MinecraftReflectionCompat.posZ(viewEntity), (float) partialTicks);
        nothiriumShadowRenderer.drainUploads();

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        shaderlessWorldPassActive = true;
        activePhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        try {
            return PipelineContext.positiveCount(nothiriumShadowRenderer.renderVisibleLayerAllowingVanillaStride(
                    bloomLayer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    nothiriumFallbackBlockEntityId(bloomLayer),
                    nothiriumFallbackRenderType(bloomLayer)
            ));
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    /**
     * Replays Nothirium's already-uploaded translucent mesh into AUSM's
     * bloom-transmission target. The caller owns the framebuffer, shader and
     * blend/depth state; Nothirium only supplies vertex data and chunk offsets.
     */
    public int renderAusmOwnedNothiriumTranslucentGeometry(double partialTicks, Entity viewEntity) {
        if (viewEntity == null
                || !PipelineContext.isNothiriumLoaded()
                || !NothiriumShadowRenderer.isAvailable()
                || self().shouldForceVanillaTerrainRenderer()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return -1;
        }

        double cameraX = interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity),
                MinecraftReflectionCompat.posX(viewEntity), (float) partialTicks);
        double cameraY = interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity),
                MinecraftReflectionCompat.posY(viewEntity), (float) partialTicks);
        double cameraZ = interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                MinecraftReflectionCompat.posZ(viewEntity), (float) partialTicks);
        nothiriumShadowRenderer.drainUploads();

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        bloomTranslucentAttenuationPass = true;
        shaderlessWorldPassActive = true;
        activePhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        try {
            BlockRenderLayer layer = BlockRenderLayer.TRANSLUCENT;
            return PipelineContext.positiveCount(nothiriumShadowRenderer.renderVisibleLayerAllowingVanillaStride(
                    layer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    nothiriumFallbackBlockEntityId(layer),
                    nothiriumFallbackRenderType(layer)
            ));
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
            bloomTranslucentAttenuationPass = false;
        }
    }

    public boolean isBloomTranslucentAttenuationPass() {
        return bloomTranslucentAttenuationPass;
    }

    protected void requestDeferredNativeBloom(double partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        pendingDeferredNativeBloom = true;
        pendingDeferredBloomPartialTicks = partialTicks;
        pendingDeferredBloomPass = pass;
    }

    protected void renderDeferredNativeBloomIfNeeded() {
        if (!pendingDeferredNativeBloom
                || bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        double partialTicks = pendingDeferredBloomPartialTicks;
        int pass = pendingDeferredBloomPass;
        pendingDeferredNativeBloom = false;
        self().renderAusmBloomLayer(MinecraftReflectionCompat.renderGlobal(mc), partialTicks, pass, MinecraftReflectionCompat.renderViewEntity(mc));
    }

    protected void recordBloomRenderResult(int rendered) {
        if (rendered > 0) {
            bloomZeroGeometryFrames = 0;
            return;
        }
        if (isPipelineActive) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
            return;
        }
        if (AusmBloomLayer.shouldUseNativeHook()) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
            return;
        }
        if (!bloomRenderer.hasBloomResources()) {
            return;
        }

        bloomZeroGeometryFrames++;
        if (bloomZeroGeometryFrames < 20 || bloomZeroGeometryRefreshCooldown > 0) {
            return;
        }

        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 120;
        self().scheduleBloomTerrainRefresh("zero-bloom-geometry");
    }

    protected void renderPostWorldBloom(Framebuffer target, boolean externalTarget) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (target == null
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.renderViewEntity(mc) == null
                || externalTarget
                || renderingGuiScreen()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        self().renderDeferredNativeBloomIfNeeded();
        if (bloomLayerRenderedThisWorldPass || bloomLayerRenderedThisWorldFrame) {
            DeferredFramebuffer handMaskSource = isPipelineActive && pingPongManager.isInitialized()
                    ? pingPongManager.getReadBuffer()
                    : null;
            int preHandDepthTexture = handMaskSource != null
                    ? handMaskSource.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT)
                    : 0;
            int postHandDepthTexture = handMaskSource != null
                    ? handMaskSource.getDepthTexture()
                    : 0;
            bloomRenderer.renderPostWorldBloom(target, preHandDepthTexture, postHandDepthTexture);
            return;
        }
        bloomRenderer.renderPostWorldBloom(target);
    }

    public void renderDepthTestedOwnedSkyRepair(Framebuffer target, Minecraft mc) {
        WorldClient world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        if (target == null
                || mc == null
                || world == null
                || !isPipelineActive
                || !self().shouldUseOwnedSkyOverrideWorld(world)
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        boolean uiRepair = MinecraftReflectionCompat.currentScreen(mc) != null
                || MinecraftReflectionCompat.isGamePaused(mc);
        if (!self().isSimpleVoidWorld(world) && !uiRepair) {
            return;
        }
        self().logOwnedSkyBackingProbe("presentation-depth-repair", mc);

        Vec3d skyColor = null;
        try {
            skyColor = MinecraftReflectionCompat.call(world, Vec3d.class, null,
                    new String[]{"func_72833_a", "getSkyColor"},
                    new Class<?>[]{Entity.class, float.class},
                    MinecraftReflectionCompat.renderViewEntity(mc),
                    currentWorldPartialTicks);
        } catch (RuntimeException | LinkageError ignored) {
            skyColor = null;
        }
        self().drawOwnedSkyDepthRepairGradient(
                MinecraftReflectionCompat.framebufferWidth(target),
                MinecraftReflectionCompat.framebufferHeight(target),
                skyColor,
                mc,
                target);
    }

    public void renderShaderlessBloomBeforeGui() {
        // Only texture-defined native BLOOM geometry is composited here.
        if (isPipelineActive) {
            return;
        }
        if (shaderlessBloomRenderedThisWorldPass) {
            self().logShaderlessBloomHook("skip already-rendered");
            return;
        }
        if (externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()
                || renderingGuiScreen()) {
            self().logShaderlessBloomHook("skip state external=" + self().describeFramebufferTarget(externalWorldFramebufferTarget)
                    + " nested=" + self().isRenderingBetterPortalsNestedView()
                    + " renderPass=" + self().isRenderingBetterPortalsRenderPass()
                    + " gui=" + renderingGuiScreen());
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderViewEntity(mc) == null || MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            self().logShaderlessBloomHook("skip missing-minecraft-state mc=" + (mc != null)
                    + " world=" + (mc != null && MinecraftReflectionCompat.world(mc) != null)
                    + " entity=" + (mc != null && MinecraftReflectionCompat.renderViewEntity(mc) != null)
                    + " framebuffer=" + (mc != null && MinecraftReflectionCompat.minecraftFramebuffer(mc) != null));
            return;
        }
        boolean hasBloomResources = bloomRenderer.hasBloomResources();
        boolean nativeBloom = AusmBloomLayer.shouldUseShaderlessNativeHook();
        Entity renderViewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        self().logShaderlessBloomHook("render target=" + self().describeFramebufferTarget(MinecraftReflectionCompat.minecraftFramebuffer(mc))
                + " bloomResources=" + hasBloomResources
                + " nativeBloom=" + nativeBloom
                + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                + " renderPass=" + self().isRenderingBetterPortalsRenderPass());
        if (!nativeBloom) {
            shaderlessBloomRenderedThisWorldPass = true;
            shaderlessBloomRenderedThisWorldFrame = true;
            self().sealShaderlessWorldFramebufferAlpha("no-bloom-before-gui");
            self().restoreShaderlessBloomExitState(mc);
            return;
        }
        self().renderNativeBloomLayerIfNeeded();
        self().renderPostWorldBloom(MinecraftReflectionCompat.minecraftFramebuffer(mc), false);
        shaderlessBloomRenderedThisWorldPass = true;
        shaderlessBloomRenderedThisWorldFrame = true;
        self().sealShaderlessWorldFramebufferAlpha("post-bloom-before-gui");
        self().restoreShaderlessBloomExitState(mc);
    }

    public void snapshotShaderlessWorldFramebufferForGui() {
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Framebuffer target = mc != null
                ? MinecraftReflectionCompat.minecraftFramebuffer(mc)
                : null;
        if (target == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        self().snapshotPresentationTargetForDirectPresentation(target, "shaderless-world-before-ui");
    }

    protected boolean shouldRenderShaderlessCustomSkyBacking(Minecraft mc) {
        if (clientRenderFrameNanos != Long.MIN_VALUE) {
            return shaderlessCustomSkyBackingThisFrame;
        }
        return self().shouldRenderShaderlessCustomSkyBackingNow(mc);
    }

    protected boolean shouldRenderShaderlessCustomSkyBackingNow(Minecraft mc) {
        // Native shaderless skies own both domes again.  A screen-space AUSM
        // underlay cannot follow the native horizon/camera transform and made
        // a visible hard join below the native upper dome.  GUI presentation
        // now restores depth state independently, so it no longer needs this
        // visual safety backing.
        return false;
    }

    protected Vec3d desaturateSkyColor(Vec3d color, double saturation) {
        return PipelineSkyColorMath.desaturate(color, saturation);
    }

    protected Vec3d mixSkyColors(Vec3d from, Vec3d to, double factor) {
        return PipelineSkyColorMath.mix(from, to, factor);
    }

    protected double clamp01(double value) {
        return PipelineSkyColorMath.clamp01(value);
    }

    protected static int clampInt(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    protected static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    public void renderOwnedSkyBackingBeforeSky(float partialTicks) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        boolean external = externalWorldFramebufferTarget != null;
        boolean bpNested = self().isRenderingBetterPortalsNestedView();
        boolean bpPass = self().isRenderingBetterPortalsRenderPass();
        boolean hasView = mc != null && MinecraftReflectionCompat.renderViewEntity(mc) != null;
        boolean hasTarget = mc != null && MinecraftReflectionCompat.minecraftFramebuffer(mc) != null;
        boolean owned = self().shouldUseOwnedSkyOverrideWorld(world);
        // Sky rendering can run before the world-frame cache is refreshed,
        // especially after F1 or GUI state changes. Evaluate this route from
        // the current world so a stale cached false cannot suppress both the
        // owned backing and Botania's intentionally disabled base dome.
        boolean shaderless = self().shouldRenderShaderlessCustomSkyBackingNow(mc);
        boolean shadered = self().shouldRenderShaderedOwnedSkyBacking(mc);
        boolean renderBacking = isPipelineActive ? (owned || shadered) : shaderless;
        self().logOwnedSkyBackingDecisionProbe("before-sky", mc, world, external, bpNested, bpPass,
                hasView, hasTarget, owned, shaderless, shadered);
        if (externalWorldFramebufferTarget != null
                || self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()) {
            return;
        }
        if (mc == null
                || world == null
                || !hasView
                || !hasTarget
                || !renderBacking) {
            return;
        }

        Vec3d skyColor = null;
        try {
            skyColor = MinecraftReflectionCompat.call(MinecraftReflectionCompat.world(mc), Vec3d.class, null, new String[]{"func_72833_a", "getSkyColor"},
                    new Class<?>[]{Entity.class, float.class}, MinecraftReflectionCompat.renderViewEntity(mc), partialTicks);
        } catch (RuntimeException | LinkageError ignored) {
            skyColor = null;
        }

        try {
            if (shaderless) {
                bindMinecraftFramebufferForGui(mc);
                self().logOwnedSkyBackingProbe("shaderless", mc);
                self().drawOwnedSkyBackingGradient(
                        MinecraftReflectionCompat.framebufferWidth(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        MinecraftReflectionCompat.framebufferHeight(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        skyColor,
                        mc);
            } else {
                self().logOwnedSkyBackingProbe("shadered", mc);
                self().drawOwnedSkyBackingGradient(
                        Math.max(1, MinecraftReflectionCompat.displayWidth(mc)),
                        Math.max(1, MinecraftReflectionCompat.displayHeight(mc)),
                        skyColor,
                        mc);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep sky rendering on vanilla's path if the optional backing pass fails.
        } finally {
            if (shaderless) {
                self().restoreShaderlessBloomExitState(mc);
            }
        }
    }

    public void renderCompleteOwnedSkyOverride(float partialTicks, int pass) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || !shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        // This is intentionally the only sky draw for AUSM-owned worlds. The
        // normal RenderGlobal path is cancelled by RenderSkyMixin, so vanilla
        // lower sky, sun, moon, stars, custom sky renderers, and skybox lists
        // cannot write into the world or GUI presentation buffers.
        self().renderOwnedSkyBackingBeforeSky(partialTicks);
        WorldClient world = MinecraftReflectionCompat.world(mc);
        renderCompleteOwnedVoidSkyDetails(partialTicks, world, mc);
        if (!isPipelineActive) {
            self().restoreShaderlessBloomExitState(mc);
        }
    }
}
