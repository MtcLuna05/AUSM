package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject the core deferred pipeline stages into the main render loop.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Shadow
    protected void renderRainSnow(float partialTicks) {
    }

    @Shadow
    protected void renderHand(float partialTicks, int pass) {
    }

    @Inject(method = "updateCameraAndRender(FJ)V", at = @At("HEAD"))
    private void onUpdateCameraAndRenderHead(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$prepareNoWorldCustomMainMenu();
        PipelineContext.getInstance().beginClientRenderFrame(nanoTime);
    }

    @Redirect(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V"
            )
    )
    private void ausm$renderGameOverlayIfPlayerReady(GuiIngame guiIngame, float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        guiIngame.renderGameOverlay(partialTicks);
    }

    @Inject(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterWorldBeforeUi(float partialTicks, long nanoTime, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.renderShaderlessBloomBeforeGui();
        context.prepareShaderlessUiRenderingBoundary();
    }

    @Inject(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onBeforeGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        if (ausm$shouldUseVanillaGuiScreen()) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            return;
        }
        context.beginGuiRendering();
    }

    @Inject(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        if (ausm$shouldUseVanillaGuiScreen()) {
            return;
        }
        PipelineContext.getInstance().finishGuiRendering();
    }

    private boolean ausm$shouldUseVanillaGuiScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null
                || minecraft.world == null
                || minecraft.currentScreen instanceof GuiContainer;
    }

    private void ausm$prepareNoWorldCustomMainMenu() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world != null || !ausm$isCustomMainMenu(minecraft)) {
            return;
        }

        if (minecraft.getFramebuffer() != null) {
            minecraft.getFramebuffer().bindFramebuffer(false);
            GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
        }
        OpenGlHelper.glUseProgram(0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(0);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean ausm$isCustomMainMenu(Minecraft minecraft) {
        return minecraft.currentScreen != null
                && "lumien.custommainmenu.gui.GuiCustom".equals(minecraft.currentScreen.getClass().getName());
    }

    @Inject(method = "renderWorldPass", at = @At("HEAD"), cancellable = true)
    private void onRenderWorldPassHead(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.world == null || minecraft.getRenderViewEntity() == null) {
            ci.cancel();
            return;
        }

        PipelineContext context = PipelineContext.getInstance();
        MainMod.getShaderPackManager().reloadIfDimensionChanged();
        context.beginWorldPassRendering(pass, partialTicks);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;setupCameraTransform(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCameraTransform(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        MatrixState.captureGbufferMatrices();
        PipelineContext.getInstance().renderPreparePass();
        PipelineContext.getInstance().bindWorldFramebuffer();
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.runPendingClientChunkRenderRefreshesForCurrentRenderPass();
        context.updateShaderlessVanillaViewFrustumForCamera();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.ensureVanillaTerrainRenderer();
        if (context.shouldRenderShadowMapBeforeTerrainSetup()) {
            context.renderShadowMap(partialTicks);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;setupTerrain(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        if (context.shouldRenderShadowMapAfterTerrainSetup()) {
            context.renderShadowMap(partialTicks);
        }
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V")
    )
    private void ausm$renderEntitiesIfGbufferRenderingEnabled(RenderGlobal renderGlobal, Entity renderViewEntity, ICamera camera, float partialTicks) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldSkipAllMainGbufferRendering()) {
            renderGlobal.renderEntities(renderViewEntity, camera, partialTicks);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().bindWorldFramebuffer();
        PipelineContext.getInstance().applyTerrainCulling(WorldRenderingPhase.TERRAIN_SOLID);
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.TERRAIN_SOLID);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().restoreTerrainCulling();
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().applyTerrainCulling(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().restoreTerrainCulling();
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 2,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().applyTerrainCulling(WorldRenderingPhase.TERRAIN_CUTOUT);
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().restoreTerrainCulling();
        PipelineContext.getInstance().snapshotOpaqueTerrainDepth();
        if (PipelineContext.getInstance().shouldRenderShadowMapAfterOpaqueTerrain()) {
            PipelineContext.getInstance().renderShadowMap(partialTicks);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.ENTITIES);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderEntities(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldRenderParticlesWithVanillaState()) {
            context.beginTranslucents();
            context.prepareVanillaParticleRenderingState();
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
        }
        if (context.shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase.PARTICLES)) {
            context.beginTranslucents();
        }
        context.beginPhase(WorldRenderingPhase.PARTICLES);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering() || context.shouldRenderParticlesWithVanillaState()) {
            return;
        }

        context.endPass();
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldRenderParticlesWithVanillaState()) {
            context.prepareVanillaParticleRenderingState();
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
        }
        if (context.shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase.PARTICLES_TRANSLUCENT)) {
            context.beginTranslucents();
        }
        context.beginPhase(WorldRenderingPhase.PARTICLES_TRANSLUCENT);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering() || context.shouldRenderParticlesWithVanillaState()) {
            return;
        }

        context.endPass();
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderLitParticles(Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$renderLitParticlesIfGbufferRenderingEnabled(ParticleManager particleManager, Entity entity, float partialTicks) {
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            particleManager.renderLitParticles(entity, partialTicks);
        }
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;renderParticles(Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$renderParticlesIfGbufferRenderingEnabled(ParticleManager particleManager, Entity entity, float partialTicks) {
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            particleManager.renderParticles(entity, partialTicks);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.DESTROY);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBlockDamageTexture(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$drawBlockDamageIfGbufferRenderingEnabled(RenderGlobal renderGlobal, Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks) {
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            renderGlobal.drawBlockDamageTexture(tessellator, bufferBuilder, entity, partialTicks);
        }
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }
        if (!PipelineContext.getInstance().shouldRenderWeather()) {
            return;
        }

        PipelineContext.getInstance().beginTranslucents();
        PipelineContext.getInstance().applyWeatherRenderState();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.RAIN_SNOW);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }
        if (!PipelineContext.getInstance().shouldRenderWeather()) {
            return;
        }

        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().restoreWeatherRenderState();
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderRainSnow(F)V")
    )
    private void ausm$renderWeatherIfEnabled(EntityRenderer renderer, float partialTicks) {
        if (PipelineContext.getInstance().shouldRenderWeather()) {
            renderRainSnow(partialTicks);
        }
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V")
    )
    private void ausm$renderHandIfGbufferRenderingEnabled(EntityRenderer renderer, float partialTicks, int pass) {
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            renderHand(partialTicks, pass);
        }
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;updateChunks(J)V")
    )
    private void ausm$skipBetterPortalsNestedChunkUpdates(RenderGlobal renderGlobal, long finishTimeNano) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.prepareRenderGlobalChunkUpdates(renderGlobal)) {
            try {
                renderGlobal.updateChunks(finishTimeNano);
            } catch (NullPointerException e) {
                if (!context.handleBetterPortalsChunkUpdateFailure(renderGlobal, e)) {
                    throw e;
                }
            }
        }
    }

    @Redirect(
            method = "renderWorldPass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I")
    )
    private int ausm$renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        return PipelineContext.getInstance().renderWorldBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 3,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().beginTranslucents();
        PipelineContext.getInstance().applyWaterRenderState();
        PipelineContext.getInstance().applyTerrainCulling(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            context.renderShaderlessVisibleBloomLayerFromWorldPass(partialTicks, pass);
            context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
        context.restoreWaterRenderState();
        context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
        context.renderShaderlessVisibleBloomLayerFromWorldPass(partialTicks, pass);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().beginHand();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.HAND_SOLID);
    }

    @Inject(
            method = "renderWorldPass",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext context = PipelineContext.getInstance();
        context.finishHand();
        context.probeHandGbufferAfterRender();
        context.endPass();
    }

    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void onRenderWorldPassReturn(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext.getInstance().finishWorldPassRendering();
    }
}
