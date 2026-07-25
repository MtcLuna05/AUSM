package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderTextureScale;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A custom Framebuffer implementation designed for the deferred pipeline.
 * Unlike vanilla's Framebuffer which handles a single color + depth,
 * this supports up to 8 Multiple Render Targets (MRT).
 */
public class DeferredFramebuffer {
    public static final int DEPTHTEX1_SNAPSHOT = 0;
    public static final int DEPTHTEX2_SNAPSHOT = 1;
    private static final int DEPTH_SNAPSHOT_COUNT = 2;
    private static final int COLOR_ATTACHMENT_SLOTS = 8;
    private static final int READ_TEXTURE_INDEX = 0;
    private static final int WRITE_TEXTURE_INDEX = 1;
    private static final int MAX_FRAMEBUFFER_STATUS_LOGS = 16;
    private static final int UNKNOWN_ATTACHMENT_TEXTURE = Integer.MIN_VALUE;
    private static int maxDrawBufferSlots = -1;
    private static int framebufferStatusLogs;

    private int fboId = -1;
    private int fullscreenFboId = -1;
    private int readFboId = -1;
    private int depthCopyFboId = -1;
    private int depthTextureId = -1;
    private final int[] depthSnapshotTextureIds = {-1, -1};
    private int recoveryColorTextureId = -1;
    private int recoveryColorWidth;
    private int recoveryColorHeight;
    private boolean recoveryColorValid;
    private int width;
    private int height;

    // Iris keeps a main/alt texture pair per color attachment and flips only
    // attachments written by the current fullscreen program.
    private final Map<Attachment, int[]> colorTextures = new EnumMap<>(Attachment.class);
    private final Map<Attachment, Integer> colorWidths = new EnumMap<>(Attachment.class);
    private final Map<Attachment, Integer> colorHeights = new EnumMap<>(Attachment.class);
    private final Map<Attachment, Boolean> flippedTextures = new EnumMap<>(Attachment.class);

    // The GL_COLOR_ATTACHMENTx constants for currently active draw buffers
    private IntBuffer drawBuffers;
    private final Map<Attachment, ColorBufferFormat> formats;
    private final Map<Attachment, ShaderTextureScale> textureScales;
    private final Map<Attachment, float[]> clearColors;
    private final FloatBuffer depthReadBuffer = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private final FloatBuffer colorReadBuffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
    private final FloatBuffer clearColorBuffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
    private final Map<Integer, int[]> attachedColorTexturesByFramebuffer = new HashMap<>();
    private final Map<Integer, Integer> attachedDepthTexturesByFramebuffer = new HashMap<>();
    private final Map<Integer, int[]> drawBuffersByFramebuffer = new HashMap<>();
    private int currentFramebufferId = -1;
    private boolean usable = true;

    public DeferredFramebuffer(int width, int height) {
        this(width, height, ShaderRenderTargetSettings.empty());
    }

    public DeferredFramebuffer(int width, int height, ShaderRenderTargetSettings settings) {
        this.width = width;
        this.height = height;
        this.formats = new EnumMap<>(Attachment.class);
        this.formats.putAll(settings.formats());
        this.textureScales = new EnumMap<>(Attachment.class);
        this.textureScales.putAll(settings.textureScales());
        this.clearColors = new EnumMap<>(Attachment.class);
        for (Attachment attachment : Attachment.values()) {
            this.clearColors.put(attachment, settings.clearColor(attachment));
        }
        createFBO();
    }

    private void createFBO() {
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.isFramebufferEnabled()) {
            MainMod.LOGGER.warn("Framebuffers not supported! Pipeline will fail.");
            usable = false;
            return;
        }

        fboId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        fullscreenFboId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        readFboId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        depthCopyFboId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        bindFramebuffer(fboId);

        // Standard depth buffer. Shader depth samplers are views/copies of this texture.
        depthTextureId = GL11.glGenTextures();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(depthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);

        attachFramebufferDepthTexture(depthTextureId);
        for (int i = 0; i < DEPTH_SNAPSHOT_COUNT; i++) {
            depthSnapshotTextureIds[i] = allocateDepthTexture();
        }

        // Pre-allocate the color attachments currently supported by the 1.12 backport.
        for (Attachment attachment : Attachment.values()) {
            allocateColorAttachment(attachment, formats.getOrDefault(attachment, ColorBufferFormat.RGBA8));
        }

        bindFramebuffer(0);
    }

    private void allocateColorAttachment(Attachment attachment, ColorBufferFormat format) {
        int attachmentWidth = attachmentWidth(attachment);
        int attachmentHeight = attachmentHeight(attachment);
        int[] texIds = {
                allocateColorTexture(format, attachmentWidth, attachmentHeight),
                allocateColorTexture(format, attachmentWidth, attachmentHeight)
        };

        colorTextures.put(attachment, texIds);
        colorWidths.put(attachment, attachmentWidth);
        colorHeights.put(attachment, attachmentHeight);
        flippedTextures.put(attachment, false);
    }

    private int allocateColorTexture(ColorBufferFormat format, int textureWidth, int textureHeight) {
        int texId = GL11.glGenTextures();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texId);
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

    private int attachmentWidth(Attachment attachment) {
        ShaderTextureScale scale = textureScales.get(attachment);
        return scale != null ? scale.width(width) : width;
    }

    private int attachmentHeight(Attachment attachment) {
        ShaderTextureScale scale = textureScales.get(attachment);
        return scale != null ? scale.height(height) : height;
    }

    private int allocateDepthTexture() {
        int texId = GL11.glGenTextures();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);
        return texId;
    }

    public void bind() {
        bindFramebuffer(fboId);
        GL11.glViewport(0, 0, width, height);

        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        invalidateDrawBufferState(fboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    public void bindForGbuffers(Attachment... drawTargets) {
        bindPipelineFramebuffer(fboId, true, true, drawTargets);
        GL11.glViewport(0, 0, width, height);
    }

    public void forceGbufferDrawBuffers(Attachment... drawTargets) {
        invalidateDrawBufferState(fboId);
        bindForGbuffers(drawTargets);
    }

    public void bindAsExternalTarget(Attachment attachment, boolean setViewport) {
        bindPipelineFramebuffer(fboId, true, true, attachment);
        if (setViewport) {
            GL11.glViewport(0, 0, getAttachmentWidth(attachment), getAttachmentHeight(attachment));
        }
    }

    public void bindForFullscreenWrite(Attachment... drawTargets) {
        bindPipelineFramebuffer(fullscreenFboId, false, false, drawTargets);
    }

    private void bindPipelineFramebuffer(int framebufferId, boolean withDepth, boolean readTextures, Attachment... drawTargets) {
        bindFramebuffer(framebufferId);
        boolean attachmentsChanged;
        if (withDepth) {
            attachmentsChanged = attachDepthTextureInternal();
        } else {
            attachmentsChanged = detachDepthTextureInternal();
        }
        attachmentsChanged |= attachTextures(readTextures, drawTargets);
        setDrawBuffers(drawTargets);
        if (drawTargets.length > 0) {
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        } else {
            GL11.glReadBuffer(GL11.GL_NONE);
        }
        if (attachmentsChanged) {
            checkStatus("bindPipelineFramebuffer:" + framebufferId + ", depth=" + withDepth + ", read=" + readTextures + ", targets=" + java.util.Arrays.toString(drawTargets));
        }
    }

    public void attachDepthTexture() {
        attachDepthTextureInternal();
    }

    public void detachDepthTexture() {
        detachDepthTextureInternal();
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
            int targetWidth = getAttachmentWidth(attachment);
            int targetHeight = getAttachmentHeight(attachment);

            for (int i = 0; i < sourceTextures.length && i < targetTextures.length; i++) {
                blitColorTexture(sourceTextures[i], sourceWidth, sourceHeight, targetTextures[i], targetWidth, targetHeight);
            }
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }

    private void blitColorTexture(int sourceTexture, int sourceWidth, int sourceHeight, int targetTexture, int targetWidth, int targetHeight) {
        bindFramebuffer(readFboId);
        detachDepthTexture();
        attachFramebufferColorTexture(0, sourceTexture);

        bindFramebuffer(fullscreenFboId);
        detachDepthTexture();
        attachFramebufferColorTexture(0, targetTexture);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        invalidateDrawBufferState(fullscreenFboId);
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
        blitTo(Attachment.COLOR, targetFramebuffer, targetWidth, targetHeight);
    }

    public void blitTo(Attachment sourceAttachment, int targetFramebuffer, int targetWidth, int targetHeight) {
        Attachment attachment = sourceAttachment == null ? Attachment.COLOR : sourceAttachment;
        int sourceWidth = getAttachmentWidth(attachment);
        int sourceHeight = getAttachmentHeight(attachment);
        bindFramebuffer(readFboId);
        attachDepthTexture();
        attachReadTextures(attachment);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);

        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        invalidateDrawBufferState(targetFramebuffer);

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
        blitDepthTextureTo(depthTextureId, targetFramebuffer, targetWidth, targetHeight);
    }

    public void blitDepthSnapshotTo(int index, int targetFramebuffer, int targetWidth, int targetHeight) {
        int texture = index >= 0 && index < depthSnapshotTextureIds.length
                ? depthSnapshotTextureIds[index]
                : -1;
        if (texture <= 0) {
            blitDepthTo(targetFramebuffer, targetWidth, targetHeight);
            return;
        }
        blitDepthTextureTo(texture, targetFramebuffer, targetWidth, targetHeight);
    }

    private void blitDepthTextureTo(int texture, int targetFramebuffer, int targetWidth, int targetHeight) {
        bindFramebuffer(readFboId);
        attachFramebufferDepthTexture(texture);
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
        int maxSlots = maxDrawBufferSlots();
        if (currentFramebufferId >= 0) {
            int[] cached = drawBuffersByFramebuffer.get(currentFramebufferId);
            if (drawBufferStateMatches(cached, attachments, maxSlots)) {
                return;
            }
        }
        if (drawBuffers == null || drawBuffers.capacity() < Math.min(attachments.length, maxSlots)) {
            drawBuffers = org.lwjgl.BufferUtils.createIntBuffer(Math.max(8, Math.min(attachments.length, maxSlots)));
        }
        drawBuffers.clear();
        for (int i = 0; i < attachments.length; i++) {
            if (i >= maxSlots) {
                break;
            }
            Attachment attachment = attachments[i];
            if (!hasColorAttachment(attachment)) {
                MainMod.LOGGER.warn("Skipping unallocated framebuffer attachment: {}", attachment);
                continue;
            }
            drawBuffers.put(com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, "field_153200_g", "GL_COLOR_ATTACHMENT0") + i);
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

    private boolean drawBufferStateMatches(int[] cached, Attachment[] attachments, int maxSlots) {
        if (cached == null) {
            return false;
        }
        int count = 0;
        int colorAttachment0 = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(
                net.minecraft.client.renderer.OpenGlHelper.class,
                org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0,
                "field_153200_g",
                "GL_COLOR_ATTACHMENT0");
        for (int i = 0; i < attachments.length && i < maxSlots; i++) {
            Attachment attachment = attachments[i];
            if (!hasColorAttachment(attachment)) {
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

    private void invalidateDrawBufferState(int framebufferId) {
        drawBuffersByFramebuffer.remove(framebufferId);
    }

    public int getTexture(Attachment attachment) {
        return getReadTexture(attachment);
    }

    public int getReadTexture(Attachment attachment) {
        int[] textures = colorTextures.get(attachment);
        if (textures == null) {
            return -1;
        }
        return textures[readIndex(attachment)];
    }

    public int getWriteTexture(Attachment attachment) {
        int[] textures = colorTextures.get(attachment);
        if (textures == null) {
            return -1;
        }
        return textures[writeIndex(attachment)];
    }

    public void resetFlips() {
        for (Attachment attachment : Attachment.values()) {
            if (colorTextures.containsKey(attachment)) {
                flippedTextures.put(attachment, false);
            }
        }
    }

    public void attachReadTextures(Attachment... attachments) {
        attachTextures(true, attachments);
    }

    public void attachWriteTextures(Attachment... attachments) {
        attachTextures(false, attachments);
    }

    public void copyReadToWrite(Attachment... attachments) {
        for (Attachment attachment : attachments) {
            int width = getAttachmentWidth(attachment);
            int height = getAttachmentHeight(attachment);

            bindFramebuffer(readFboId);
            detachDepthTexture();
            attachReadTextures(attachment);

            bindFramebuffer(fullscreenFboId);
            detachDepthTexture();
            attachWriteTextures(attachment);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            invalidateDrawBufferState(fullscreenFboId);

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
        if (source == null || target == null || source == target || !hasColorAttachment(source) || !hasColorAttachment(target)) {
            return;
        }
        copyReadAttachmentToWriteAttachment(source, target);
        flip(target);
    }

    public void copyReadAttachmentToWriteAttachment(Attachment source, Attachment target) {
        if (source == null || target == null || !hasColorAttachment(source) || !hasColorAttachment(target)) {
            return;
        }

        int sourceWidth = getAttachmentWidth(source);
        int sourceHeight = getAttachmentHeight(source);
        int targetWidth = getAttachmentWidth(target);
        int targetHeight = getAttachmentHeight(target);

        bindFramebuffer(readFboId);
        detachDepthTexture();
        attachReadTextures(source);

        bindFramebuffer(fullscreenFboId);
        detachDepthTexture();
        attachWriteTextures(target);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fullscreenFboId);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        invalidateDrawBufferState(fullscreenFboId);

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
        if (source == null || !hasColorAttachment(source)) {
            recoveryColorValid = false;
            return false;
        }

        int sourceWidth = getAttachmentWidth(source);
        int sourceHeight = getAttachmentHeight(source);
        ensureRecoveryColorTexture(sourceWidth, sourceHeight);
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
            bindFramebuffer(readFboId);
            detachDepthTexture();
            attachReadTextures(source);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(recoveryColorTextureId);
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
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(previousTexture);
        }
    }

    public boolean restoreRecoveryColorToReadAttachment(Attachment target) {
        if (!recoveryColorValid || target == null || !hasColorAttachment(target) || recoveryColorTextureId == -1) {
            return false;
        }

        int targetWidth = getAttachmentWidth(target);
        int targetHeight = getAttachmentHeight(target);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            bindFramebuffer(readFboId);
            detachDepthTexture();
            attachFramebufferColorTexture(0, recoveryColorTextureId);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(getWriteTexture(target));
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
            flip(target);
            return true;
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(previousTexture);
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
        return readColorAtTexture(recoveryColorTextureId, Math.max(1, recoveryColorWidth), Math.max(1, recoveryColorHeight), x, y);
    }

    private void ensureRecoveryColorTexture(int textureWidth, int textureHeight) {
        int safeWidth = Math.max(1, textureWidth);
        int safeHeight = Math.max(1, textureHeight);
        if (recoveryColorTextureId != -1 && recoveryColorWidth == safeWidth && recoveryColorHeight == safeHeight) {
            return;
        }
        if (recoveryColorTextureId != -1) {
            GL11.glDeleteTextures(recoveryColorTextureId);
        }
        recoveryColorTextureId = allocateColorTexture(ColorBufferFormat.RGBA8, safeWidth, safeHeight);
        recoveryColorWidth = safeWidth;
        recoveryColorHeight = safeHeight;
        recoveryColorValid = false;
    }

    public void flip(Attachment... attachments) {
        for (Attachment attachment : attachments) {
            if (hasColorAttachment(attachment)) {
                flippedTextures.put(attachment, !flippedTextures.getOrDefault(attachment, false));
            }
        }
    }

    private boolean attachTextures(boolean readTextures, Attachment... attachments) {
        boolean changed = false;
        int maxSlots = maxDrawBufferSlots();
        for (int i = 0; i < maxSlots; i++) {
            changed |= attachFramebufferColorTexture(i, 0);
        }

        for (int i = 0; i < attachments.length; i++) {
            if (i >= maxSlots) {
                break;
            }
            Attachment attachment = attachments[i];
            int textureId = readTextures ? getReadTexture(attachment) : getWriteTexture(attachment);
            if (textureId == -1) {
                continue;
            }
            changed |= attachFramebufferColorTexture(i, textureId);
        }
        return changed;
    }

    private void bindFramebuffer(int framebufferId) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), framebufferId);
        currentFramebufferId = framebufferId;
    }

    private boolean attachDepthTextureInternal() {
        return attachFramebufferDepthTexture(depthTextureId);
    }

    private boolean detachDepthTextureInternal() {
        return attachFramebufferDepthTexture(0);
    }

    private boolean attachFramebufferDepthTexture(int textureId) {
        if (currentFramebufferId >= 0) {
            Integer currentTexture = attachedDepthTexturesByFramebuffer.get(currentFramebufferId);
            if (currentTexture != null && currentTexture == textureId) {
                return false;
            }
            attachedDepthTexturesByFramebuffer.put(currentFramebufferId, textureId);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(
                com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(),
                com.l.ausm.impl.util.MinecraftReflectionCompat.glDepthAttachment(),
                GL11.GL_TEXTURE_2D,
                textureId,
                0
        );
        return true;
    }

    private boolean attachFramebufferColorTexture(int slot, int textureId) {
        int maxSlots = maxDrawBufferSlots();
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
        com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(
                com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(),
                com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0, "field_153200_g", "GL_COLOR_ATTACHMENT0") + slot,
                GL11.GL_TEXTURE_2D,
                textureId,
                0
        );
        return true;
    }

    private boolean hasColorAttachment(Attachment attachment) {
        return colorTextures.containsKey(attachment);
    }

    private static int maxDrawBufferSlots() {
        if (maxDrawBufferSlots < 0) {
            int maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
            int maxColorAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
            int detected = Math.min(Math.min(maxDrawBuffers, maxColorAttachments), COLOR_ATTACHMENT_SLOTS);
            maxDrawBufferSlots = detected > 0 ? detected : COLOR_ATTACHMENT_SLOTS;
        }
        return maxDrawBufferSlots;
    }

    private int readIndex(Attachment attachment) {
        return flippedTextures.getOrDefault(attachment, false) ? WRITE_TEXTURE_INDEX : READ_TEXTURE_INDEX;
    }

    private int writeIndex(Attachment attachment) {
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

    public void snapshotCurrentDepth(int index) {
        if (index < 0 || index >= depthSnapshotTextureIds.length || depthSnapshotTextureIds[index] == -1) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);

        try {
            bindFramebuffer(readFboId);
            attachDepthTexture();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthSnapshotTextureIds[index],
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            invalidateDrawBufferState(depthCopyFboId);
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
            bindFramebuffer(readFboId);
            attachDepthTexture();
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
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
            bindFramebuffer(readFboId);
            attachFramebufferDepthTexture(depthSnapshotTextureIds[sourceIndex]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthSnapshotTextureIds[targetIndex],
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            invalidateDrawBufferState(depthCopyFboId);
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
            bindFramebuffer(readFboId);
            attachDepthTexture();
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
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
            bindFramebuffer(readFboId);
            attachFramebufferDepthTexture(depthSnapshotTextureIds[sourceIndex]);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D,
                    depthTextureId,
                    0
            );
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            invalidateDrawBufferState(depthCopyFboId);
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
            bindFramebuffer(readFboId);
            attachDepthTexture();
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readCenterDepth() {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            bindFramebuffer(readFboId);
            attachDepthTexture();
            return readDepthAt(width / 2, height / 2);
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readDepthAtPixel(int x, int y) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            bindFramebuffer(readFboId);
            attachDepthTexture();
            return readDepthAt(
                    Math.max(0, Math.min(width - 1, x)),
                    Math.max(0, Math.min(height - 1, y))
            );
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float readDepthSamplerAtPixel(int index, int x, int y) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            bindFramebuffer(readFboId);
            attachFramebufferDepthTexture(getDepthSamplerTexture(index));
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            return readDepthAt(
                    Math.max(0, Math.min(width - 1, x)),
                    Math.max(0, Math.min(height - 1, y))
            );
        } finally {
            bindFramebuffer(readFboId);
            attachDepthTexture();
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    public float[] readCenterColor(Attachment attachment) {
        return readColorAt(attachment, getAttachmentWidth(attachment) / 2, getAttachmentHeight(attachment) / 2);
    }

    public float[] readColorAt(Attachment attachment, int x, int y) {
        return readColorAtTexture(getReadTexture(attachment), getAttachmentWidth(attachment), getAttachmentHeight(attachment), x, y);
    }

    public float[] readWriteColorAt(Attachment attachment, int x, int y) {
        return readColorAtTexture(getWriteTexture(attachment), getAttachmentWidth(attachment), getAttachmentHeight(attachment), x, y);
    }

    private float[] readColorAtTexture(int textureId, int attachmentWidth, int attachmentHeight, int x, int y) {
        if (textureId == -1) {
            return new float[]{Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            bindFramebuffer(readFboId);
            detachDepthTexture();
            attachFramebufferColorTexture(0, textureId);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFboId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            colorReadBuffer.clear();
            GL11.glReadPixels(
                    Math.max(0, Math.min(attachmentWidth - 1, x)),
                    Math.max(0, Math.min(attachmentHeight - 1, y)),
                    1,
                    1,
                    GL11.GL_RGBA,
                    GL11.GL_FLOAT,
                    colorReadBuffer
            );
            return new float[] {
                    colorReadBuffer.get(0),
                    colorReadBuffer.get(1),
                    colorReadBuffer.get(2),
                    colorReadBuffer.get(3)
            };
        } finally {
            restoreFramebufferBindings(previousReadFramebuffer, previousDrawFramebuffer, previousReadBuffer, previousDrawBuffer);
        }
    }

    private float readDepthAt(int x, int y) {
        depthReadBuffer.clear();
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depthReadBuffer);
        return depthReadBuffer.get(0);
    }

    private void restoreFramebufferBindings(int readFramebuffer, int drawFramebuffer, int readBuffer, int drawBuffer) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GL11.glReadBuffer(safeReadBuffer(readFramebuffer, readBuffer));
        GL11.glDrawBuffer(safeDrawBuffer(drawFramebuffer, drawBuffer));
    }

    private int safeReadBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        if (framebuffer == 0) {
            return buffer == GL11.GL_FRONT || buffer == GL11.GL_BACK ? buffer : GL11.GL_BACK;
        }
        return isColorAttachmentBuffer(buffer) ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    private int safeDrawBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        if (framebuffer == 0) {
            return buffer == GL11.GL_FRONT || buffer == GL11.GL_BACK ? buffer : GL11.GL_BACK;
        }
        return isColorAttachmentBuffer(buffer) ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    private boolean isColorAttachmentBuffer(int buffer) {
        return buffer >= GL30.GL_COLOR_ATTACHMENT0 && buffer < GL30.GL_COLOR_ATTACHMENT0 + maxDrawBufferSlots();
    }

    public void generateMipmaps(Set<Attachment> attachments) {
        for (Attachment attachment : attachments) {
            int texId = getTexture(attachment);
            if (texId == -1) {
                continue;
            }

            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
    }

    private void checkStatus() {
        checkStatus("unspecified");
    }

    private void checkStatus(String stage) {
        int status = com.l.ausm.impl.util.MinecraftReflectionCompat.glCheckFramebufferStatus(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer());
        if (status != com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_COMPLETE, "field_153202_i", "GL_FRAMEBUFFER_COMPLETE")) {
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
                        safeGetInteger(GL20.GL_MAX_DRAW_BUFFERS),
                        safeGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS),
                        formats,
                        textureScales
                );
            }
        }
    }

    private static int safeGetInteger(int parameter) {
        try {
            return GL11.glGetInteger(parameter);
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    public void delete() {
        if (fboId > -1) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (fullscreenFboId > -1) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(fullscreenFboId);
            fullscreenFboId = -1;
        }
        if (readFboId > -1) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(readFboId);
            readFboId = -1;
        }
        if (depthCopyFboId > -1) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(depthCopyFboId);
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
        clearColor(true, attachmentsToClear);
    }

    public void clearWrite(Attachment... attachmentsToClear) {
        clearColor(false, attachmentsToClear);
    }

    public void clearDepth() {
        bindFramebuffer(readFboId);
        attachDepthTexture();
        attachReadTextures();
        GL11.glDrawBuffer(GL11.GL_NONE);
        invalidateDrawBufferState(readFboId);
        GL11.glViewport(0, 0, width, height);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    private void clearColor(boolean readTextures, Attachment... attachmentsToClear) {
        clearColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColorBuffer);
        float previousClearRed = clearColorBuffer.get(0);
        float previousClearGreen = clearColorBuffer.get(1);
        float previousClearBlue = clearColorBuffer.get(2);
        float previousClearAlpha = clearColorBuffer.get(3);

        GL11.glDisable(GL11.GL_BLEND);

        bindFramebuffer(fullscreenFboId);
        detachDepthTexture();
        for (Attachment attachment : attachmentsToClear) {
            attachTextures(readTextures, attachment);
            setDrawBuffers(attachment);
            GL11.glViewport(0, 0, getAttachmentWidth(attachment), getAttachmentHeight(attachment));
            float[] clearColor = clearColors.getOrDefault(attachment, new float[]{0.0f, 0.0f, 0.0f, 0.0f});
            GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }

        if (readTextures) {
            bindFramebuffer(readFboId);
            attachReadTextures();
            attachDepthTexture();
            GL11.glDrawBuffer(GL11.GL_NONE);
            invalidateDrawBufferState(readFboId);
            GL11.glViewport(0, 0, width, height);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }

        GL11.glClearColor(previousClearRed, previousClearGreen, previousClearBlue, previousClearAlpha);
    }
}
