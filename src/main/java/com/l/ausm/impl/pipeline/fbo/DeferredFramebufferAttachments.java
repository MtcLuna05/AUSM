package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.l.ausm.api.pipeline.pack.ShaderTextureScale;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class DeferredFramebufferAttachments extends DeferredFramebufferBase {
    protected void createFBO() {
        if (!MinecraftReflectionCompat.isFramebufferEnabled()) {
            MainMod.LOGGER.warn("Framebuffers not supported! Pipeline will fail.");
            usable = false;
            return;
        }

        fboId = MinecraftReflectionCompat.glGenFramebuffers();
        fullscreenFboId = MinecraftReflectionCompat.glGenFramebuffers();
        readFboId = MinecraftReflectionCompat.glGenFramebuffers();
        depthCopyFboId = MinecraftReflectionCompat.glGenFramebuffers();
        self().bindFramebuffer(fboId);

        // Standard depth buffer. Shader depth samplers are views/copies of this texture.
        depthTextureId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(depthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);

        self().attachFramebufferDepthTexture(depthTextureId);
        for (int i = 0; i < DEPTH_SNAPSHOT_COUNT; i++) {
            depthSnapshotTextureIds[i] = self().allocateDepthTexture();
        }

        // Pre-allocate the color attachments currently supported by the 1.12 backport.
        for (Attachment attachment : Attachment.values()) {
            self().allocateColorAttachment(attachment, formats.getOrDefault(attachment, ColorBufferFormat.RGBA8));
        }

        self().bindFramebuffer(0);
    }

    protected void allocateColorAttachment(Attachment attachment, ColorBufferFormat format) {
        int attachmentWidth = self().attachmentWidth(attachment);
        int attachmentHeight = self().attachmentHeight(attachment);
        int[] texIds = {
                self().allocateColorTexture(format, attachmentWidth, attachmentHeight),
                self().allocateColorTexture(format, attachmentWidth, attachmentHeight)
        };

        colorTextures.put(attachment, texIds);
        colorWidths.put(attachment, attachmentWidth);
        colorHeights.put(attachment, attachmentHeight);
        flippedTextures.put(attachment, false);
    }

    protected int allocateColorTexture(ColorBufferFormat format, int textureWidth, int textureHeight) {
        int texId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                format.internalFormat(),
                textureWidth,
                textureHeight,
                0,
                format.pixelFormat(),
                format.pixelType(),
                (ByteBuffer) null
        );
        return texId;
    }

    protected int attachmentWidth(Attachment attachment) {
        ShaderTextureScale scale = textureScales.get(attachment);
        return scale != null ? scale.width(width) : width;
    }

    protected int attachmentHeight(Attachment attachment) {
        ShaderTextureScale scale = textureScales.get(attachment);
        return scale != null ? scale.height(height) : height;
    }

    protected int allocateDepthTexture() {
        int texId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);
        return texId;
    }

    public void bind() {
        self().bindFramebuffer(fboId);
        GL11.glViewport(0, 0, width, height);

        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        self().invalidateDrawBufferState(fboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    public void bindForGbuffers(Attachment... drawTargets) {
        self().bindPipelineFramebuffer(fboId, true, true, drawTargets);
        GL11.glViewport(0, 0, width, height);
    }

    public void forceGbufferDrawBuffers(Attachment... drawTargets) {
        self().invalidateDrawBufferState(fboId);
        self().bindForGbuffers(drawTargets);
    }

    public void bindAsExternalTarget(Attachment attachment, boolean setViewport) {
        self().bindPipelineFramebuffer(fboId, true, true, attachment);
        if (setViewport) {
            GL11.glViewport(0, 0, self().getAttachmentWidth(attachment), self().getAttachmentHeight(attachment));
        }
    }

    public void bindForFullscreenWrite(Attachment... drawTargets) {
        self().bindPipelineFramebuffer(fullscreenFboId, false, false, drawTargets);
    }

    protected void bindPipelineFramebuffer(int framebufferId, boolean withDepth, boolean readTextures, Attachment... drawTargets) {
        self().bindFramebuffer(framebufferId);
        boolean attachmentsChanged;
        if (withDepth) {
            attachmentsChanged = self().attachDepthTextureInternal();
        } else {
            attachmentsChanged = self().detachDepthTextureInternal();
        }
        attachmentsChanged |= self().attachTextures(readTextures, drawTargets);
        self().setDrawBuffers(drawTargets);
        if (drawTargets.length > 0) {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        } else {
            GL11.glReadBuffer(GL11.GL_NONE);
        }
        if (attachmentsChanged) {
            self().checkStatus("bindPipelineFramebuffer:" + framebufferId + ", depth=" + withDepth + ", read=" + readTextures + ", targets=" + Arrays.toString(drawTargets));
        }
    }

    public void attachDepthTexture() {
        self().attachDepthTextureInternal();
    }

    public void detachDepthTexture() {
        self().detachDepthTextureInternal();
    }

    public int getFramebufferId() {
        return fboId;
    }

    public boolean isUsable() {
        return fboId > -1 && usable;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getAttachmentWidth(Attachment attachment) {
        return colorWidths.getOrDefault(attachment, width);
    }

    public int getAttachmentHeight(Attachment attachment) {
        return colorHeights.getOrDefault(attachment, height);
    }

    public void copyColorStateFrom(DeferredFramebuffer source, Set<Attachment> attachments) {
        if (source == null || attachments.isEmpty()) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        for (Attachment attachment : attachments) {
            int[] sourceTextures = source.colorTextures.get(attachment);
            int[] targetTextures = colorTextures.get(attachment);
            if (sourceTextures == null || targetTextures == null) {
                continue;
            }

            flippedTextures.put(attachment, source.flippedTextures.getOrDefault(attachment, false));
            int sourceWidth = source.getAttachmentWidth(attachment);
            int sourceHeight = source.getAttachmentHeight(attachment);
            int targetWidth = self().getAttachmentWidth(attachment);
            int targetHeight = self().getAttachmentHeight(attachment);

            for (int i = 0; i < sourceTextures.length && i < targetTextures.length; i++) {
                self().blitColorTexture(sourceTextures[i], sourceWidth, sourceHeight, targetTextures[i], targetWidth, targetHeight);
            }
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }

    protected void blitColorTexture(int sourceTexture, int sourceWidth, int sourceHeight, int targetTexture, int targetWidth, int targetHeight) {
        self().bindFramebuffer(readFboId);
        self().detachDepthTexture();
        self().attachFramebufferColorTexture(0, sourceTexture);

        self().bindFramebuffer(fullscreenFboId);
        self().detachDepthTexture();
        self().attachFramebufferColorTexture(0, targetTexture);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        self().invalidateDrawBufferState(fullscreenFboId);
        GL30.glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_LINEAR
        );
    }

    public void blitTo(int targetFramebuffer, int targetWidth, int targetHeight) {
        self().blitTo(Attachment.COLOR, targetFramebuffer, targetWidth, targetHeight);
    }

    public void blitTo(Attachment sourceAttachment, int targetFramebuffer, int targetWidth, int targetHeight) {
        Attachment attachment = sourceAttachment == null ? Attachment.COLOR : sourceAttachment;
        int sourceWidth = self().getAttachmentWidth(attachment);
        int sourceHeight = self().getAttachmentHeight(attachment);
        self().bindFramebuffer(readFboId);
        self().attachDepthTexture();
        self().attachReadTextures(attachment);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);

        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        self().invalidateDrawBufferState(targetFramebuffer);

        GL30.glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );
    }

    public void blitDepthTo(int targetFramebuffer, int targetWidth, int targetHeight) {
        self().blitDepthTextureTo(depthTextureId, targetFramebuffer, targetWidth, targetHeight);
    }

    public void blitDepthSnapshotTo(int index, int targetFramebuffer, int targetWidth, int targetHeight) {
        int texture = index >= 0 && index < depthSnapshotTextureIds.length
                ? depthSnapshotTextureIds[index]
                : -1;
        if (texture <= 0) {
            self().blitDepthTo(targetFramebuffer, targetWidth, targetHeight);
            return;
        }
        self().blitDepthTextureTo(texture, targetFramebuffer, targetWidth, targetHeight);
    }

    protected void blitDepthTextureTo(int texture, int targetFramebuffer, int targetWidth, int targetHeight) {
        self().bindFramebuffer(readFboId);
        self().attachFramebufferDepthTexture(texture);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);

        GL30.glBlitFramebuffer(
                0,
                0,
                width,
                height,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_DEPTH_BUFFER_BIT,
                GL11.GL_NEAREST
        );
    }

    /**
     * Sets which attachments the current fragment shader should draw to.
     * Maps to the glDrawBuffers standard.
     */
    public void setDrawBuffers(Attachment... attachments) {
        int maxSlots = DeferredFramebuffer.maxDrawBufferSlots();
        if (currentFramebufferId >= 0) {
            int[] cached = drawBuffersByFramebuffer.get(currentFramebufferId);
            if (self().drawBufferStateMatches(cached, attachments, maxSlots)) {
                return;
            }
        }
        if (drawBuffers == null || drawBuffers.capacity() < Math.min(attachments.length, maxSlots)) {
            drawBuffers = BufferUtils.createIntBuffer(Math.clamp(attachments.length, 8, maxSlots));
        }
        drawBuffers.clear();
        for (int i = 0; i < attachments.length; i++) {
            if (i >= maxSlots) {
                break;
            }
            Attachment attachment = attachments[i];
            if (!self().hasColorAttachment(attachment)) {
                MainMod.LOGGER.warn("Skipping unallocated framebuffer attachment: {}", attachment);
                continue;
            }
            drawBuffers.put(MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL30.GL_COLOR_ATTACHMENT0, "field_153200_g", "GL_COLOR_ATTACHMENT0") + i);
        }
        drawBuffers.flip();
        int[] uploadedState = new int[drawBuffers.remaining()];
        for (int i = 0; i < uploadedState.length; i++) {
            uploadedState[i] = drawBuffers.get(drawBuffers.position() + i);
        }
        if (drawBuffers.hasRemaining()) {
            GL20.glDrawBuffers(drawBuffers);
        } else {
            GL11.glDrawBuffer(GL11.GL_NONE);
        }
        if (currentFramebufferId >= 0) {
            drawBuffersByFramebuffer.put(currentFramebufferId, uploadedState);
        }
    }

    protected boolean drawBufferStateMatches(int[] cached, Attachment[] attachments, int maxSlots) {
        if (cached == null) {
            return false;
        }
        int count = 0;
        int colorAttachment0 = MinecraftReflectionCompat.fieldInt(
                OpenGlHelper.class,
                GL30.GL_COLOR_ATTACHMENT0,
                "field_153200_g",
                "GL_COLOR_ATTACHMENT0");
        for (int i = 0; i < attachments.length && i < maxSlots; i++) {
            Attachment attachment = attachments[i];
            if (!self().hasColorAttachment(attachment)) {
                continue;
            }
            int expected = colorAttachment0 + i;
            if (count >= cached.length || cached[count] != expected) {
                return false;
            }
            // Vanilla and mod renderers can change GL_DRAW_BUFFER* without
            // changing the framebuffer object. The Java-side cache is only a
            // hint; verify the live state before skipping glDrawBuffers.
            if (GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + count) != expected) {
                return false;
            }
            count++;
        }
        return count == cached.length;
    }

    protected void invalidateDrawBufferState(int framebufferId) {
        drawBuffersByFramebuffer.remove(framebufferId);
    }

    public int getTexture(Attachment attachment) {
        return self().getReadTexture(attachment);
    }

    public int getReadTexture(Attachment attachment) {
        int[] textures = colorTextures.get(attachment);
        if (textures == null) {
            return -1;
        }
        return textures[self().readIndex(attachment)];
    }

    public int getWriteTexture(Attachment attachment) {
        int[] textures = colorTextures.get(attachment);
        if (textures == null) {
            return -1;
        }
        return textures[self().writeIndex(attachment)];
    }

    public void resetFlips() {
        for (Attachment attachment : Attachment.values()) {
            if (colorTextures.containsKey(attachment)) {
                flippedTextures.put(attachment, false);
            }
        }
    }

    public void attachReadTextures(Attachment... attachments) {
        self().attachTextures(true, attachments);
    }

    public void attachWriteTextures(Attachment... attachments) {
        self().attachTextures(false, attachments);
    }

    public void copyReadToWrite(Attachment... attachments) {
        for (Attachment attachment : attachments) {
            int width = self().getAttachmentWidth(attachment);
            int height = self().getAttachmentHeight(attachment);

            self().bindFramebuffer(readFboId);
            self().detachDepthTexture();
            self().attachReadTextures(attachment);

            self().bindFramebuffer(fullscreenFboId);
            self().detachDepthTexture();
            self().attachWriteTextures(attachment);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            self().invalidateDrawBufferState(fullscreenFboId);

            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        }
    }

    public void copyReadAttachmentToReadAttachment(Attachment source, Attachment target) {
        if (source == null || target == null || source == target || !self().hasColorAttachment(source) || !self().hasColorAttachment(target)) {
            return;
        }
        self().copyReadAttachmentToWriteAttachment(source, target);
        self().flip(target);
    }

    public void copyReadAttachmentToWriteAttachment(Attachment source, Attachment target) {
        if (source == null || target == null || !self().hasColorAttachment(source) || !self().hasColorAttachment(target)) {
            return;
        }

        int sourceWidth = self().getAttachmentWidth(source);
        int sourceHeight = self().getAttachmentHeight(source);
        int targetWidth = self().getAttachmentWidth(target);
        int targetHeight = self().getAttachmentHeight(target);

        self().bindFramebuffer(readFboId);
        self().detachDepthTexture();
        self().attachReadTextures(source);

        self().bindFramebuffer(fullscreenFboId);
        self().detachDepthTexture();
        self().attachWriteTextures(target);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        self().invalidateDrawBufferState(fullscreenFboId);

        GL30.glBlitFramebuffer(
                0,
                0,
                sourceWidth,
                sourceHeight,
                0,
                0,
                targetWidth,
                targetHeight,
                GL11.GL_COLOR_BUFFER_BIT,
                sourceWidth == targetWidth && sourceHeight == targetHeight ? GL11.GL_NEAREST : GL11.GL_LINEAR
        );
    }

    public boolean snapshotReadAttachmentToRecoveryColor(Attachment source) {
        if (source == null || !self().hasColorAttachment(source)) {
            recoveryColorValid = false;
            return false;
        }

        int sourceWidth = self().getAttachmentWidth(source);
        int sourceHeight = self().getAttachmentHeight(source);
        self().ensureRecoveryColorTexture(sourceWidth, sourceHeight);
        if (recoveryColorTextureId == -1) {
            recoveryColorValid = false;
            return false;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            self().bindFramebuffer(readFboId);
            self().detachDepthTexture();
            self().attachReadTextures(source);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateBindTexture(recoveryColorTextureId);
            GL11.glCopyTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Math.min(sourceWidth, recoveryColorWidth),
                    Math.min(sourceHeight, recoveryColorHeight)
            );
            recoveryColorValid = true;
            return true;
        } finally {
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
            MinecraftReflectionCompat.glStateBindTexture(previousTexture);
        }
    }

    public boolean restoreRecoveryColorToReadAttachment(Attachment target) {
        if (!recoveryColorValid || target == null || !self().hasColorAttachment(target) || recoveryColorTextureId == -1) {
            return false;
        }

        int targetWidth = self().getAttachmentWidth(target);
        int targetHeight = self().getAttachmentHeight(target);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            self().bindFramebuffer(readFboId);
            self().detachDepthTexture();
            self().attachFramebufferColorTexture(0, recoveryColorTextureId);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateBindTexture(self().getWriteTexture(target));
            GL11.glCopyTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Math.min(recoveryColorWidth, targetWidth),
                    Math.min(recoveryColorHeight, targetHeight)
            );
            self().flip(target);
            return true;
        } finally {
            self().restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
            MinecraftReflectionCompat.glStateBindTexture(previousTexture);
        }
    }

    public boolean hasRecoveryColorSnapshot() {
        return recoveryColorValid && recoveryColorTextureId != -1;
    }

    public void clearRecoveryColorSnapshot() {
        recoveryColorValid = false;
    }

    public int getRecoveryColorWidth() {
        return recoveryColorWidth;
    }

    public int getRecoveryColorHeight() {
        return recoveryColorHeight;
    }

    public float[] readRecoveryColorAt(int x, int y) {
        return self().readColorAtTexture(recoveryColorTextureId, Math.max(1, recoveryColorWidth), Math.max(1, recoveryColorHeight), x, y);
    }

    protected void ensureRecoveryColorTexture(int textureWidth, int textureHeight) {
        int safeWidth = Math.max(1, textureWidth);
        int safeHeight = Math.max(1, textureHeight);
        if (recoveryColorTextureId != -1 && recoveryColorWidth == safeWidth && recoveryColorHeight == safeHeight) {
            return;
        }
        if (recoveryColorTextureId != -1) {
            GL11.glDeleteTextures(recoveryColorTextureId);
        }
        recoveryColorTextureId = self().allocateColorTexture(ColorBufferFormat.RGBA8, safeWidth, safeHeight);
        recoveryColorWidth = safeWidth;
        recoveryColorHeight = safeHeight;
        recoveryColorValid = false;
    }

    public void flip(Attachment... attachments) {
        for (Attachment attachment : attachments) {
            if (self().hasColorAttachment(attachment)) {
                flippedTextures.put(attachment, !flippedTextures.getOrDefault(attachment, false));
            }
        }
    }

    protected boolean attachTextures(boolean readTextures, Attachment... attachments) {
        boolean changed = false;
        int maxSlots = DeferredFramebuffer.maxDrawBufferSlots();
        for (int i = 0; i < maxSlots; i++) {
            changed |= self().attachFramebufferColorTexture(i, 0);
        }

        for (int i = 0; i < attachments.length; i++) {
            if (i >= maxSlots) {
                break;
            }
            Attachment attachment = attachments[i];
            int textureId = readTextures ? self().getReadTexture(attachment) : self().getWriteTexture(attachment);
            if (textureId == -1) {
                continue;
            }
            changed |= self().attachFramebufferColorTexture(i, textureId);
        }
        return changed;
    }

    protected void bindFramebuffer(int framebufferId) {
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), framebufferId);
        currentFramebufferId = framebufferId;
    }

    protected boolean attachDepthTextureInternal() {
        return self().attachFramebufferDepthTexture(depthTextureId);
    }

    protected boolean detachDepthTextureInternal() {
        return self().attachFramebufferDepthTexture(0);
    }

    protected boolean attachFramebufferDepthTexture(int textureId) {
        if (currentFramebufferId >= 0) {
            Integer currentTexture = attachedDepthTexturesByFramebuffer.get(currentFramebufferId);
            if (currentTexture != null && currentTexture == textureId) {
                return false;
            }
            attachedDepthTexturesByFramebuffer.put(currentFramebufferId, textureId);
        }
        MinecraftReflectionCompat.glFramebufferTexture2D(
                MinecraftReflectionCompat.glFramebuffer(),
                MinecraftReflectionCompat.glDepthAttachment(),
                GL11.GL_TEXTURE_2D,
                textureId,
                0
        );
        return true;
    }

    protected boolean attachFramebufferColorTexture(int slot, int textureId) {
        int maxSlots = DeferredFramebuffer.maxDrawBufferSlots();
        if (slot < 0 || slot >= maxSlots) {
            return false;
        }
        if (currentFramebufferId >= 0) {
            int[] currentTextures = attachedColorTexturesByFramebuffer.computeIfAbsent(currentFramebufferId, ignored -> {
                int[] textures = new int[maxSlots];
                Arrays.fill(textures, UNKNOWN_ATTACHMENT_TEXTURE);
                return textures;
            });
            if (slot < currentTextures.length && currentTextures[slot] == textureId) {
                return false;
            }
            if (slot < currentTextures.length) {
                currentTextures[slot] = textureId;
            }
        }
        MinecraftReflectionCompat.glFramebufferTexture2D(
                MinecraftReflectionCompat.glFramebuffer(),
                MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL30.GL_COLOR_ATTACHMENT0, "field_153200_g", "GL_COLOR_ATTACHMENT0") + slot,
                GL11.GL_TEXTURE_2D,
                textureId,
                0
        );
        return true;
    }

    protected boolean hasColorAttachment(Attachment attachment) {
        return colorTextures.containsKey(attachment);
    }

    protected static int maxDrawBufferSlots() {
        if (maxDrawBufferSlots < 0) {
            int maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
            int maxColorAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
            int detected = Math.min(Math.min(maxDrawBuffers, maxColorAttachments), COLOR_ATTACHMENT_SLOTS);
            maxDrawBufferSlots = detected > 0 ? detected : COLOR_ATTACHMENT_SLOTS;
        }
        return maxDrawBufferSlots;
    }

    protected int readIndex(Attachment attachment) {
        return flippedTextures.getOrDefault(attachment, false) ? WRITE_TEXTURE_INDEX : READ_TEXTURE_INDEX;
    }

    protected int writeIndex(Attachment attachment) {
        return flippedTextures.getOrDefault(attachment, false) ? READ_TEXTURE_INDEX : WRITE_TEXTURE_INDEX;
    }

    public int getDepthTexture() {
        return depthTextureId;
    }

    public int getDepthSamplerTexture(int index) {
        if (index >= 0 && index < depthSnapshotTextureIds.length && depthSnapshotTextureIds[index] != -1) {
            return depthSnapshotTextureIds[index];
        }
        return depthTextureId;
    }
}
