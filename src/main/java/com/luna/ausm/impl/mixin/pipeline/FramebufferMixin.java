package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.client.GnetumCompatibility;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.PipelineFrameLayerCapture;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class FramebufferMixin {
    @Inject(method = "framebufferRenderExt(IIZ)V", at = @At("HEAD"))
    private void ausm$repairClientArrayStateBeforeFramebufferRender(int width, int height, boolean disableBlend, CallbackInfo ci) {
        ausm$repairClientArrayState();
        PipelineContext.getInstance().prepareShaderlessHiddenGuiFramebufferPresentation();
    }

    @Inject(method = "func_178038_a(IIZ)V", at = @At("HEAD"), remap = false, require = 0)
    private void ausm$repairClientArrayStateBeforeFramebufferRenderSrg(int width, int height, boolean disableBlend, CallbackInfo ci) {
        ausm$repairClientArrayState();
        PipelineContext.getInstance().prepareShaderlessHiddenGuiFramebufferPresentation();
    }

    @Inject(method = "framebufferRenderExt(IIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void ausm$presentFramebufferWithoutVanillaUploader(int width, int height, boolean disableBlend, CallbackInfo ci) {
        if (ausm$presentFramebufferImmediately(width, height, disableBlend)) {
            ci.cancel();
        }
    }

    @Inject(method = "func_178038_a(IIZ)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ausm$presentFramebufferWithoutVanillaUploaderSrg(int width, int height, boolean disableBlend, CallbackInfo ci) {
        if (ausm$presentFramebufferImmediately(width, height, disableBlend)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "framebufferRenderExt(IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebufferTexture()V")
    )
    private void ausm$beforeBindFramebufferTexture(int width, int height, boolean disableBlend, CallbackInfo ci) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (framebuffer != MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            return;
        }

        PipelineFrameLayerCapture.mirrorExternalPresentation(framebuffer);
        context.logFramebufferPresentationBoundary("framebufferExt-before-prepare",
                framebuffer,
                width,
                height,
                true);
        context.prepareFramebufferPresentation();
        context.logFramebufferPresentationBoundary("framebufferExt-after-prepare-before-bind-texture",
                framebuffer,
                width,
                height,
                true);
        FixedFunctionGlState.resetClientArrayState(true);
        MinecraftReflectionCompat.glStateBindTexture(0);
    }

    @Inject(method = "framebufferRenderExt(IIZ)V", at = @At("RETURN"), require = 0)
    private void ausm$captureVanillaWindowPresentation(int width, int height, boolean disableBlend, CallbackInfo ci) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (minecraft != null && framebuffer == MinecraftReflectionCompat.minecraftFramebuffer(minecraft)
                && PipelineContext.getInstance().isActive()) {
            PipelineFrameLayerCapture.captureVanillaWindowPresentation(width, height);
        }
    }

    private static void ausm$repairClientArrayState() {
        FixedFunctionGlState.resetClientArrayState(true);
    }

    private boolean ausm$presentFramebufferImmediately(int width, int height, boolean disableBlend) {
        // Gnetum caches the HUD in alternating transparent framebuffers then
        // relies on vanilla's textured final presentation. A raw FBO blit here
        // creates a second presentation route after that composition, causing
        // frames with and without the cache to alternate.
        if (GnetumCompatibility.isInstalled()) {
            return false;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || !MinecraftReflectionCompat.isFramebufferEnabled()) {
            return false;
        }
        Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (framebuffer != MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return false;
        }
        PipelineFrameLayerCapture.mirrorExternalPresentation(framebuffer);
        PipelineContext context = PipelineContext.getInstance();
        ausm$repairClientArrayState();
        context.prepareFramebufferPresentation();
        return context.presentFramebufferDirectly(framebuffer, width, height);
    }
}
