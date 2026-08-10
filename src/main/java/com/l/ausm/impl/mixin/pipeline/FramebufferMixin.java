package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class FramebufferMixin {
    private static boolean ausm$disableImmediateFramebufferPresentation = false;
    private static boolean ausm$loggedFramebufferStateRepair;
    private static boolean ausm$loggedFramebufferImmediatePresentation;

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
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (framebuffer != com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            return;
        }

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
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
    }

    private static void ausm$repairClientArrayState() {
        if (!ausm$loggedFramebufferStateRepair) {
            ausm$loggedFramebufferStateRepair = true;
            MainMod.LOGGER.warn("[AUSMFramebuffer] Repairing client-array/VAO state before framebuffer presentation.");
        }
        FixedFunctionGlState.resetClientArrayState(true);
    }

    private boolean ausm$presentFramebufferImmediately(int width, int height, boolean disableBlend) {
        if (ausm$disableImmediateFramebufferPresentation) {
            return false;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return false;
        }
        if (!MinecraftReflectionCompat.isFramebufferEnabled()) {
            return false;
        }

        Framebuffer framebuffer = (Framebuffer) (Object) this;
        if (framebuffer != MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return false;
        }
        int framebufferObject = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int framebufferWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int framebufferHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        if (framebufferObject <= 0 || width <= 0 || height <= 0) {
            return false;
        }

        PipelineContext context = PipelineContext.getInstance();
        boolean active = context.isActive();
        if (active || !context.shouldUseShaderlessHiddenGuiPresentation()) {
            return false;
        }
        ausm$repairClientArrayState();
        if (!ausm$loggedFramebufferImmediatePresentation) {
            ausm$loggedFramebufferImmediatePresentation = true;
            MainMod.LOGGER.warn("[AUSMFramebufferSafe] Presenting framebuffer with immediate quad to bypass vanilla uploader crash. size="
                    + width + "x" + height
                    + " fb=" + framebufferWidth + "x" + framebufferHeight
                    + " fbo=" + framebufferObject);
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        try {
            context.logFramebufferPresentationBoundary("shaderless-immediate-before-quad",
                    framebuffer, width, height, true);
            FixedFunctionGlState.resetClientArrayState(true);
            MinecraftReflectionCompat.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferObject);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glViewport(0, 0, width, height);
            GL30.glBlitFramebuffer(0, 0, framebufferWidth, framebufferHeight,
                    0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            context.logFramebufferPresentationBoundary("shaderless-immediate-after-quad",
                    framebuffer, width, height, true);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            if (previousReadFramebuffer == 0) {
                GL11.glReadBuffer(previousReadBuffer == GL11.GL_NONE ? GL11.GL_BACK : previousReadBuffer);
            } else {
                GL11.glReadBuffer(previousReadBuffer == GL11.GL_NONE ? GL30.GL_COLOR_ATTACHMENT0 : previousReadBuffer);
            }
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            ausm$repairClientArrayState();
        }
        return true;
    }
}
