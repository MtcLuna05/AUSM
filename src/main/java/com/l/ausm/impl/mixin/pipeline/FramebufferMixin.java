package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Framebuffer.class)
public class FramebufferMixin {
    private static boolean ausm$disableImmediateFramebufferPresentation = true;
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
        int framebufferTexture = MinecraftReflectionCompat.framebufferTexture(framebuffer);
        int framebufferWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int framebufferHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int textureWidth = Math.max(framebufferWidth, MinecraftReflectionCompat.fieldInt(framebuffer, framebufferWidth, "field_147622_a", "framebufferTextureWidth"));
        int textureHeight = Math.max(framebufferHeight, MinecraftReflectionCompat.fieldInt(framebuffer, framebufferHeight, "field_147620_b", "framebufferTextureHeight"));
        if (framebufferTexture <= 0 || width <= 0 || height <= 0) {
            return false;
        }

        PipelineContext context = PipelineContext.getInstance();
        boolean active = context.isActive();
        if (active) {
            context.logFramebufferPresentationBoundary("framebufferExt-immediate-before-prepare",
                    framebuffer,
                    width,
                    height,
                    true);
            context.prepareFramebufferPresentation();
        }
        ausm$repairClientArrayState();
        if (!ausm$loggedFramebufferImmediatePresentation) {
            ausm$loggedFramebufferImmediatePresentation = true;
            MainMod.LOGGER.warn("[AUSMFramebufferSafe] Presenting framebuffer with immediate quad to bypass vanilla uploader crash. size="
                    + width + "x" + height
                    + " fb=" + framebufferWidth + "x" + framebufferHeight
                    + " tex=" + textureWidth + "x" + textureHeight
                    + " texture=" + framebufferTexture);
        }

        float u = framebufferWidth / (float) textureWidth;
        float v = framebufferHeight / (float) textureHeight;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_VIEWPORT_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glFrontFace(GL11.GL_CCW);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebufferTexture);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex3f(0.0F, height, 0.0F);
            GL11.glTexCoord2f(u, 0.0F);
            GL11.glVertex3f(width, height, 0.0F);
            GL11.glTexCoord2f(u, v);
            GL11.glVertex3f(width, 0.0F, 0.0F);
            GL11.glTexCoord2f(0.0F, v);
            GL11.glVertex3f(0.0F, 0.0F, 0.0F);
            GL11.glEnd();
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
            ausm$repairClientArrayState();
        }
        if (active) {
            context.logFramebufferPresentationBoundary("framebufferExt-immediate-after-present",
                    framebuffer,
                    width,
                    height,
                    true);
        }
        return true;
    }
}
