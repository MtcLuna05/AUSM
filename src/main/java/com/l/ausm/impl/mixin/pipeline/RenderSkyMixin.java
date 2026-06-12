package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to bind the sky shader programs.
 */
@Mixin(RenderGlobal.class)
public class RenderSkyMixin {
    @Inject(method = "markBlocksForUpdate", at = @At("HEAD"))
    private void ausm$ensureViewFrustumBeforeBlockUpdate(int minX, int minY, int minZ,
                                                         int maxX, int maxY, int maxZ,
                                                         boolean updateImmediately,
                                                         CallbackInfo ci) {
        PipelineContext.getInstance().ensureRenderGlobalViewFrustum((RenderGlobal) (Object) this);
    }

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSkyHead(float partialTicks, int pass, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderSkyDisc()) {
            ci.cancel();
            return;
        }
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SKY);
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void onRenderSkyReturn(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderSkyAfterBaseRotation(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().applySkySunPathRotation();
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyBeforeSun(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SUN);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyBeforeMoon(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.MOON);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyBeforeStars(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.STARS);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableBlend()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyAfterStars(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("HEAD"), cancellable = true)
    private void onRenderCloudsHead(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderClouds()) {
            ci.cancel();
            return;
        }
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.CLOUDS);
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("RETURN"))
    private void onRenderCloudsReturn(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
