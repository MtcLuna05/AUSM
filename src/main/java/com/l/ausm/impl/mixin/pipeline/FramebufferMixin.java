package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class FramebufferMixin {

    @Inject(
            method = "framebufferRenderExt(IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;bindFramebufferTexture()V")
    )
    private void ausm$beforeBindFramebufferTexture(int width, int height, boolean disableBlend, CallbackInfo ci) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            return;
        }

        context.logFramebufferPresentationBoundary("framebufferExt-before-prepare",
                (Framebuffer) (Object) this,
                width,
                height,
                true);
        context.prepareFramebufferPresentation();
        context.logFramebufferPresentationBoundary("framebufferExt-after-prepare-before-bind-texture",
                (Framebuffer) (Object) this,
                width,
                height,
                true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
    }
}
