package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.client.GnetumCompatibility;
import com.luna.ausm.impl.client.GnetumRenderProbe;
import com.luna.ausm.impl.pipeline.PipelineFrameLayerCapture;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Coordinates Gnetum's HUD cache with AUSM's final world presentation. */
@Mixin(targets = "me.decce.gnetum.FramebufferManager", remap = false)
public class GnetumFramebufferManagerMixin {
    @Inject(method = "bind(Z)V", at = @At("HEAD"), require = 0)
    private void ausm$beforeGnetumBind(boolean clear, CallbackInfo ci) {
        GnetumRenderProbe.record("bind-before", this);
    }

    @Inject(method = "bind(Z)V", at = @At("RETURN"), require = 0)
    private void ausm$afterGnetumBind(boolean clear, CallbackInfo ci) {
        GnetumRenderProbe.record("bind-after", this);
    }

    @Inject(method = "unbind()V", at = @At("HEAD"), require = 0)
    private void ausm$beforeGnetumUnbind(CallbackInfo ci) {
        GnetumRenderProbe.record("unbind-before", this);
    }

    @Inject(method = "unbind()V", at = @At("RETURN"), require = 0)
    private void ausm$afterGnetumUnbind(CallbackInfo ci) {
        GnetumRenderProbe.record("unbind-after", this);
    }

    @Inject(method = "swapFramebuffers()V", at = @At("HEAD"), require = 0)
    private void ausm$beforeGnetumSwap(CallbackInfo ci) {
        GnetumRenderProbe.record("swap-before", this);
    }

    @Inject(method = "blit(DD)V", at = @At("HEAD"), require = 0)
    private void ausm$beforeGnetumBlit(double width, double height, CallbackInfo ci) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(minecraft);
        PipelineFrameLayerCapture.recordExternalFramebufferForensics("gnetum-before-hud-blit", target);
        GnetumCompatibility.restoreShaderedWorldBeforeCacheBlit();
        PipelineFrameLayerCapture.recordExternalFramebufferForensics("gnetum-after-world-restore", target);
        GnetumRenderProbe.record("blit-before", this);
    }

    @Inject(method = "blit(DD)V", at = @At("RETURN"), require = 0)
    private void ausm$afterGnetumBlit(double width, double height, CallbackInfo ci) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        PipelineFrameLayerCapture.recordExternalFramebufferForensics("gnetum-after-hud-blit",
                MinecraftReflectionCompat.minecraftFramebuffer(minecraft));
        GnetumRenderProbe.record("blit-after", this);
    }
}
