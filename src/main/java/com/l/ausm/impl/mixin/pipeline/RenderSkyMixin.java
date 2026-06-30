package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Inject(method = "loadRenderers", at = @At("HEAD"))
    private void ausm$logLoadRenderers(CallbackInfo ci) {
        PipelineContext.getInstance().handleRenderGlobalLoadRenderers((RenderGlobal) (Object) this);
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void ausm$afterLoadRenderers(CallbackInfo ci) {
        PipelineContext.getInstance().handleRenderGlobalLoadRenderersComplete((RenderGlobal) (Object) this);
    }

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSkyHead(float partialTicks, int pass, CallbackInfo ci) {
        // Some nested/custom sky paths can leave the shared Tessellator open.
        // Vanilla sky immediately calls BufferBuilder.begin(), which hard-crashes
        // if the previous buffer was not closed.
        ausm$forceResetTessellator();
        PipelineContext.getInstance().setAstralSolarEclipseFactor(0.0f);
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

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/IRenderHandler;render(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V"
            ),
            require = 0
    )
    private void ausm$renderOrSuppressCustomSkyRenderer(IRenderHandler skyRenderer,
                                                        float partialTicks,
                                                        WorldClient world,
                                                        Minecraft minecraft) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.CUSTOM_SKY);
        try {
            if (!context.shouldSuppressVoidWorldCustomSkyRenderer(skyRenderer, world)) {
                skyRenderer.render(partialTicks, world, minecraft);
            }
        } finally {
            context.endPass();
            context.finishExternalWorldOverlayRender("custom sky renderer");
        }
    }

    @Inject(method = "renderSkyEnd()V", at = @At("HEAD"), require = 0)
    private void ausm$beforeVoidSkybox(CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSkyEnd()V", at = @At("RETURN"), require = 0)
    private void ausm$afterVoidSkybox(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.finishExternalWorldOverlayRender("void skybox");
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void ausm$beforeSunriseSunsetFan(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SUNSET);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 0
            )
    )
    private void ausm$drawOrSuppressSunriseSunsetFan(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        try {
            if (context.shouldSuppressVanillaSunsetGeometry()) {
                ausm$forceResetTessellator(tessellator);
                return;
            }
            tessellator.draw();
        } finally {
            context.endPass();
        }
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
    private void ausm$beforeSunTextureBind(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.SUN);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$afterSunTextureBind(float partialTicks, int pass, CallbackInfo ci) {
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
    private void ausm$beforeMoonTextureBind(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.MOON);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$afterMoonTextureBind(float partialTicks, int pass, CallbackInfo ci) {
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaSun(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaSunGeometry()) {
            ausm$forceResetTessellator(tessellator);
            return;
        }
        tessellator.draw();
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 2
            )
    )
    private void ausm$drawOrSuppressVanillaMoon(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaMoonGeometry()) {
            ausm$forceResetTessellator(tessellator);
            return;
        }
        tessellator.draw();
    }

    private static void ausm$forceResetTessellator() {
        ausm$forceResetTessellator(Tessellator.getInstance());
    }

    private static void ausm$forceResetTessellator(Tessellator tessellator) {
        if (tessellator == null) {
            return;
        }
        BufferBuilder buffer = tessellator.getBuffer();
        if (buffer instanceof IBufferBuilderExtension extension) {
            extension.ausm$forceResetDrawingState();
        } else if (buffer != null) {
            buffer.reset();
        }
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

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaStarsVbo(VertexBuffer vertexBuffer, int mode) {
        if (PipelineContext.getInstance().shouldSuppressVanillaStarsGeometry()) {
            return;
        }
        vertexBuffer.drawArrays(mode);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaStarsList(int list) {
        if (PipelineContext.getInstance().shouldSuppressVanillaStarsGeometry()) {
            return;
        }
        GlStateManager.callList(list);
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
