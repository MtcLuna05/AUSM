package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
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

    @Inject(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onBeforeGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        PipelineContext.getInstance().beginGuiRendering();
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
        PipelineContext.getInstance().finishGuiRendering();
    }

    @Inject(
            method = "updateCameraAndRender(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void onBeforeIngameOverlay(float partialTicks, long nanoTime, CallbackInfo ci) {
        PipelineContext.getInstance().renderShaderlessBloomBeforeGui();
    }

    @Inject(method = "renderWorldPass", at = @At("HEAD"))
    private void onRenderWorldPassHead(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
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
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().ensureVanillaTerrainRenderer();
        if (PipelineContext.getInstance().shouldRenderShadowMapBeforeTerrainSetup()) {
            PipelineContext.getInstance().renderShadowMap(partialTicks);
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
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        if (PipelineContext.getInstance().shouldRenderShadowMapAfterTerrainSetup()) {
            PipelineContext.getInstance().renderShadowMap(partialTicks);
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
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
        }
        context.beginTranslucents();
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
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
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
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
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
        if (PipelineContext.getInstance().shouldBypassWorldPassRendering()) {
            return;
        }

        PipelineContext.getInstance().endPass();
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
            context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
        context.restoreWaterRenderState();
        context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
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

        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderWorldPass", at = @At("RETURN"))
    private void onRenderWorldPassReturn(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext.getInstance().finishWorldPassRendering();
    }
}
