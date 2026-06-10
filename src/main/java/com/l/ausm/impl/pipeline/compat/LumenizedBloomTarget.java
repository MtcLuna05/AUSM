package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Isolates Lumenized's vanilla-style bloom pass from AUSM's ping-pong color target.
 */
public final class LumenizedBloomTarget {
    private Framebuffer target;
    private Framebuffer view;
    private int width = -1;
    private int height = -1;
    private boolean prepared;
    private final FloatBuffer clearColorBuffer = BufferUtils.createFloatBuffer(4);

    public Framebuffer prepare(DeferredFramebuffer source) {
        if (source == null) {
            return null;
        }

        int targetWidth = source.getAttachmentWidth(Attachment.COLOR);
        int targetHeight = source.getAttachmentHeight(Attachment.COLOR);
        ensureTarget(targetWidth, targetHeight);
        if (target == null || view == null) {
            return null;
        }

        clearTargetColor();
        source.blitDepthTo(target.framebufferObject, targetWidth, targetHeight);
        prepared = true;
        return view;
    }

    public void blendInto(DeferredFramebuffer destination) {
        if (!prepared || target == null || destination == null) {
            prepared = false;
            return;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);

        destination.bindAsExternalTarget(Attachment.COLOR, true);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        OpenGlHelper.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(target.framebufferTexture);
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        OpenGlHelper.glUseProgram(previousProgram);
        GL13.glActiveTexture(previousActiveTexture);
        GlStateManager.bindTexture(previousTexture);

        destination.bindAsExternalTarget(Attachment.COLOR, false);
        prepared = false;
    }

    public void delete() {
        if (target != null) {
            target.deleteFramebuffer();
            target = null;
        }
        if (view != null) {
            view.deleteFramebuffer();
            view = null;
        }
        width = -1;
        height = -1;
        prepared = false;
    }

    public boolean isPrepared() {
        return prepared;
    }

    private void ensureTarget(int targetWidth, int targetHeight) {
        if (target != null && targetWidth == width && targetHeight == height) {
            updateView();
            return;
        }

        if (target == null) {
            target = new Framebuffer(targetWidth, targetHeight, true);
            target.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            target.createBindFramebuffer(targetWidth, targetHeight);
        }
        target.setFramebufferFilter(GL11.GL_LINEAR);
        width = targetWidth;
        height = targetHeight;

        if (view == null) {
            view = new Framebuffer(1, 1, false);
            view.deleteFramebuffer();
            view.useDepth = true;
            view.framebufferColor = new float[]{0.0F, 0.0F, 0.0F, 0.0F};
        }
        updateView();
    }

    private void updateView() {
        if (target == null || view == null) {
            return;
        }

        view.framebufferTextureWidth = width;
        view.framebufferTextureHeight = height;
        view.framebufferWidth = width;
        view.framebufferHeight = height;
        view.framebufferObject = target.framebufferObject;
        view.framebufferTexture = target.framebufferTexture;
        view.depthBuffer = target.depthBuffer;
        view.framebufferFilter = GL11.GL_LINEAR;
    }

    private void clearTargetColor() {
        target.bindFramebuffer(true);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        clearColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColorBuffer);
        float previousRed = clearColorBuffer.get(0);
        float previousGreen = clearColorBuffer.get(1);
        float previousBlue = clearColorBuffer.get(2);
        float previousAlpha = clearColorBuffer.get(3);

        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glClearColor(previousRed, previousGreen, previousBlue, previousAlpha);
    }
}
