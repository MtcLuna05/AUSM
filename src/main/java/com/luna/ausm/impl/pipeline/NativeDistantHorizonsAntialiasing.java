package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.DistantHorizonsCompat;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Gives native DH and ordinary terrain the same Complementary temporal resolve. */
public final class NativeDistantHorizonsAntialiasing {
    private static int framebuffer;
    private static boolean logged;

    private NativeDistantHorizonsAntialiasing() { }

    public static void composeBeforeResolve(String packName, PipelineProgram program, DeferredFramebuffer scene) {
        if (!DistantHorizonsCompat.installed() || scene == null || program.pass() != RenderPass.COMPOSITE6
                || program.shaderProgram() == null) {
            return;
        }
        String name = packName == null ? "" : packName.toLowerCase(Locale.ROOT);
        // These packs tonemap into colortex3 in COMPOSITE5, then resolve
        // colortex3 against their colortex2 history in COMPOSITE6.
        if (!(name.contains("complementaryunbound") || name.contains("complementaryreimagined"))
                || name.contains("euphoria") || !program.drawBuffers().contains(Attachment.COMPOSITE)
                || !program.drawBuffers().contains(Attachment.NORMAL)
                || program.shaderProgram().getUniformLocation("colortex3") < 0
                || program.shaderProgram().getUniformLocation("colortex2") < 0
                || scene.getAttachmentWidth(Attachment.COMPOSITE) != scene.getWidth()
                || scene.getAttachmentHeight(Attachment.COMPOSITE) != scene.getHeight()) {
            return;
        }
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer == 0) framebuffer = GL30.glGenFramebuffers();
        try {
            // Color only: sampling the saved scene depth must not create a
            // feedback loop with a depth attachment on the draw framebuffer.
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, scene.getReadTexture(Attachment.COMPOSITE), 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return;
            }
            NativeDistantHorizonsCompositor.draw(
                    scene.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                    scene.getWidth(), scene.getHeight());
            if (!logged) {
                logged = true;
                MainMod.LOGGER.info("[AUSMDistantHorizons] Native LODs enter Complementary before temporal antialiasing.");
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        }
    }

    public static void release() {
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        framebuffer = 0;
        logged = false;
    }
}
