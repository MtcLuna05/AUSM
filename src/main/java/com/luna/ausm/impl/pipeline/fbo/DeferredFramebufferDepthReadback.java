package com.luna.ausm.impl.pipeline.fbo;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Set;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class DeferredFramebufferDepthReadback extends DeferredFramebufferAttachments {
    public void snapshotCurrentDepth(int index) {
        if (index < 0 || index >= depthSnapshotTextureIds.length || depthSnapshotTextureIds[index] == -1) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);

        try {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthSnapshotTextureIds[index],
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            self().invalidateDrawBufferState(depthCopyFboId);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public void copyDepthSnapshot(int sourceIndex, int targetIndex) {
        if (sourceIndex < 0
                || sourceIndex >= depthSnapshotTextureIds.length
                || targetIndex < 0
                || targetIndex >= depthSnapshotTextureIds.length
                || depthSnapshotTextureIds[sourceIndex] == -1
                || depthSnapshotTextureIds[targetIndex] == -1
                || sourceIndex == targetIndex) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);

        try {
            self().bindFramebuffer(readFboId);
            self().attachFramebufferDepthTexture(depthSnapshotTextureIds[sourceIndex]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthSnapshotTextureIds[targetIndex],
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            self().invalidateDrawBufferState(depthCopyFboId);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public void restoreDepthSnapshotToCurrentDepth(int sourceIndex) {
        if (sourceIndex < 0
                || sourceIndex >= depthSnapshotTextureIds.length
                || depthSnapshotTextureIds[sourceIndex] == -1
                || depthTextureId == -1) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);

        try {
            self().bindFramebuffer(readFboId);
            self().attachFramebufferDepthTexture(depthSnapshotTextureIds[sourceIndex]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthTextureId,
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            self().invalidateDrawBufferState(depthCopyFboId);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readCenterDepth() {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            return self().readDepthAt(width / 2, height / 2);
        } finally {
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readDepthAtPixel(int x, int y) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            return self().readDepthAt(
                    Math.clamp(x, 0, width - 1),
                    Math.clamp(y, 0, height - 1)
            );
        } finally {
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readDepthSamplerAtPixel(int index, int x, int y) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            self().bindFramebuffer(readFboId);
            self().attachFramebufferDepthTexture(self().getDepthSamplerTexture(index));
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            return self().readDepthAt(
                    Math.clamp(x, 0, width - 1),
                    Math.clamp(y, 0, height - 1)
            );
        } finally {
            self().bindFramebuffer(readFboId);
            self().attachDepthTexture();
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float[] readCenterColor(Attachment attachment) {
        return self().readColorAt(attachment, self().getAttachmentWidth(attachment) / 2, self().getAttachmentHeight(attachment) / 2);
    }

    public float[] readColorAt(Attachment attachment, int x, int y) {
        return self().readColorAtTexture(self().getReadTexture(attachment), self().getAttachmentWidth(attachment), self().getAttachmentHeight(attachment), x, y);
    }

    public float[] readWriteColorAt(Attachment attachment, int x, int y) {
        return self().readColorAtTexture(self().getWriteTexture(attachment), self().getAttachmentWidth(attachment), self().getAttachmentHeight(attachment), x, y);
    }

    protected float[] readColorAtTexture(int textureId, int attachmentWidth, int attachmentHeight, int x, int y) {
        if (textureId == -1) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            self().bindFramebuffer(readFboId);
            self().detachDepthTexture();
            self().attachFramebufferColorTexture(0, textureId);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            colorReadBuffer.clear();
            GL11.glReadPixels(
                    Math.clamp(x, 0, attachmentWidth - 1),
                    Math.clamp(y, 0, attachmentHeight - 1),
                    1,
                    1,
                    GL11.GL_RGBA,
                    GL11.GL_FLOAT,
                    colorReadBuffer
            );
            return new float[]{
                    colorReadBuffer.get(0),
                    colorReadBuffer.get(1),
                    colorReadBuffer.get(2),
                    colorReadBuffer.get(3)
            };
        } finally {
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    protected float readDepthAt(int x, int y) {
        depthReadBuffer.clear();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthReadBuffer);
        return depthReadBuffer.get(0);
    }

    protected void restoreFramebufferBindings(int readFramebuffer, int drawFramebuffer, int readBuffer, int drawBuffer) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL11.glReadBuffer(self().safeReadBuffer(readFramebuffer, readBuffer));
        GL11.glDrawBuffer(self().safeDrawBuffer(drawFramebuffer, drawBuffer));
    }

    protected int safeReadBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        if (framebuffer == 0) {
            return buffer == GL11.GL_FRONT || buffer == GL11.GL_BACK ? buffer : GL11.GL_BACK;
        }
        return self().isColorAttachmentBuffer(buffer) ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    protected int safeDrawBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        if (framebuffer == 0) {
            return buffer == GL11.GL_FRONT || buffer == GL11.GL_BACK ? buffer : GL11.GL_BACK;
        }
        return self().isColorAttachmentBuffer(buffer) ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    protected boolean isColorAttachmentBuffer(int buffer) {
        return buffer >= GL30.GL_COLOR_ATTACHMENT0 && buffer < GL30.GL_COLOR_ATTACHMENT0 + DeferredFramebuffer.maxDrawBufferSlots();
    }

    public void generateMipmaps(Set<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            int texId = self().getTexture(attachment);
            if (texId == -1) {
                continue;
            }

            MinecraftReflectionCompat.glStateBindTexture(texId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
    }

    protected void checkStatus() {
        self().checkStatus("unspecified");
    }

    protected void checkStatus(String stage) {
        int status = MinecraftReflectionCompat.glCheckFramebufferStatus(MinecraftReflectionCompat.glFramebuffer());
        if (status != MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL30.GL_FRAMEBUFFER_COMPLETE, "field_153202_i", "GL_FRAMEBUFFER_COMPLETE")) {
            usable = false;
            if (framebufferStatusLogs < MAX_FRAMEBUFFER_STATUS_LOGS) {
                framebufferStatusLogs++;
                MainMod.LOGGER.error(
                        "[AUSMFramebuffer] DeferredFramebuffer is not complete stage={} status={} readFbo={} drawFbo={} fbo={} fullscreenFbo={} size={}x{} maxDrawBuffers={} maxColorAttachments={} formats={} scales={}",
                        stage,
                        status,
                        GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                        GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                        fboId,
                        fullscreenFboId,
                        width,
                        height,
                        DeferredFramebuffer.safeGetInteger(GL20.GL_MAX_DRAW_BUFFERS),
                        DeferredFramebuffer.safeGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS),
                        formats,
                        textureScales
                );
            }
        }
    }

    protected static int safeGetInteger(int parameter) {
        try {
            return GL11.glGetInteger(parameter);
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    public void delete() {
        if (fboId > -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (fullscreenFboId > -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(fullscreenFboId);
            fullscreenFboId = -1;
        }
        if (readFboId > -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(readFboId);
            readFboId = -1;
        }
        if (depthCopyFboId > -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(depthCopyFboId);
            depthCopyFboId = -1;
        }
        if (depthTextureId > -1) {
            GL11.glDeleteTextures(depthTextureId);
            depthTextureId = -1;
        }
        if (recoveryColorTextureId > -1) {
            GL11.glDeleteTextures(recoveryColorTextureId);
            recoveryColorTextureId = -1;
            recoveryColorValid = false;
        }
        for (int i = 0; i < depthSnapshotTextureIds.length; i++) {
            if (depthSnapshotTextureIds[i] > -1) {
                GL11.glDeleteTextures(depthSnapshotTextureIds[i]);
                depthSnapshotTextureIds[i] = -1;
            }
        }
        for (int[] textures : colorTextures.values()) {
            for (int texId : textures) {
                if (texId > -1) {
                    GL11.glDeleteTextures(texId);
                }
            }
        }
        colorTextures.clear();
        colorWidths.clear();
        colorHeights.clear();
        flippedTextures.clear();
        attachedColorTexturesByFramebuffer.clear();
        attachedDepthTexturesByFramebuffer.clear();
        drawBuffersByFramebuffer.clear();
        currentFramebufferId = -1;
    }

    /**
     * Clears the specified attachments and the depth buffer.
     */
    public void clear(Attachment... attachmentsToClear) {
        self().clearColor(true, attachmentsToClear);
    }

    public void clearWrite(Attachment... attachmentsToClear) {
        self().clearColor(false, attachmentsToClear);
    }

    public void clearDepth() {
        self().bindFramebuffer(readFboId);
        self().attachDepthTexture();
        self().attachReadTextures();
        GL11.glDrawBuffer(GL11.GL_NONE);
        self().invalidateDrawBufferState(readFboId);
        GL11.glViewport(0, 0, width, height);
        MinecraftReflectionCompat.glStateClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    protected void clearColor(boolean readTextures, Attachment... attachmentsToClear) {
        clearColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColorBuffer);
        float previousClearRed = clearColorBuffer.get(0);
        float previousClearGreen = clearColorBuffer.get(1);
        float previousClearBlue = clearColorBuffer.get(2);
        float previousClearAlpha = clearColorBuffer.get(3);

        GL11.glDisable(GL11.GL_BLEND);

        self().bindFramebuffer(fullscreenFboId);
        self().detachDepthTexture();
        for (Attachment attachment : attachmentsToClear) {
            self().attachTextures(readTextures, attachment);
            self().setDrawBuffers(attachment);
            GL11.glViewport(0, 0, self().getAttachmentWidth(attachment), self().getAttachmentHeight(attachment));
            float[] clearColor = clearColors.getOrDefault(attachment, new float[]{0.0f, 0.0f, 0.0f, 0.0f});
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        if (readTextures) {
            self().bindFramebuffer(readFboId);
            self().attachReadTextures();
            self().attachDepthTexture();
            GL11.glDrawBuffer(GL11.GL_NONE);
            self().invalidateDrawBufferState(readFboId);
            GL11.glViewport(0, 0, width, height);
            MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }

        GL11.glClearColor(previousClearRed, previousClearGreen, previousClearBlue, previousClearAlpha);
    }
}
