package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;

import java.util.Set;

/**
 * Handles Iris-style per-attachment texture flipping.
 * Fullscreen passes read the current A texture for every colortex sampler,
 * write selected draw buffers into B textures, then flip only those outputs.
 */
public class PingPongManager {

    private DeferredFramebuffer framebuffer;
    private ShaderRenderTargetSettings currentSettings = ShaderRenderTargetSettings.empty();

    public void initialize(int width, int height) {
        initialize(width, height, ShaderRenderTargetSettings.empty());
    }

    public void initialize(int width, int height, ShaderRenderTargetSettings settings) {
        if (framebuffer != null) {
            framebuffer.delete();
        }

        currentSettings = settings;
        framebuffer = new DeferredFramebuffer(width, height, settings);
        framebuffer.clear(Attachment.values());
        framebuffer.clearWrite(Attachment.values());
    }

    /**
     * The buffer that shaders should read FROM.
     */
    public DeferredFramebuffer getReadBuffer() {
        return framebuffer;
    }

    public boolean isInitialized() {
        return framebuffer != null && framebuffer.isUsable();
    }

    public void snapshotReadBufferDepth(int index) {
        framebuffer.snapshotCurrentDepth(index);
    }

    public void copyPreTranslucentDepth() {
        snapshotReadBufferDepth(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT);
    }

    public void copyPreHandDepth() {
        snapshotReadBufferDepth(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT);
    }

    public void copyPreTranslucentDepthToPreHandDepth() {
        if (framebuffer != null) {
            framebuffer.copyDepthSnapshot(
                    DeferredFramebuffer.DEPTHTEX1_SNAPSHOT,
                    DeferredFramebuffer.DEPTHTEX2_SNAPSHOT
            );
        }
    }

    public void resize(int width, int height) {
        initialize(width, height, currentSettings);
    }

    public void resize(int width, int height, Set<Attachment> persistentAttachments) {
        DeferredFramebuffer previous = framebuffer;
        currentSettings = currentSettings == null ? ShaderRenderTargetSettings.empty() : currentSettings;
        framebuffer = new DeferredFramebuffer(width, height, currentSettings);
        framebuffer.clear(Attachment.values());
        framebuffer.clearWrite(Attachment.values());
        if (previous != null) {
            framebuffer.copyColorStateFrom(previous, persistentAttachments);
            previous.delete();
        }
    }

    public void cleanup() {
        if (framebuffer != null) {
            framebuffer.delete();
        }
        framebuffer = null;
    }

    /**
     * Called at the very beginning of a new frame, before ANY shaders are bound.
     * Clears all buffers to prevent ghosting/smearing from the previous frame.
     */
    public void beginFrame() {
        beginFrame(Attachment.values());
    }

    public void beginFrame(Attachment... clearAttachments) {
        if (framebuffer == null) {
            return;
        }

        if (clearAttachments.length > 0) {
            framebuffer.clear(clearAttachments);
            framebuffer.clearWrite(clearAttachments);
        } else {
            framebuffer.clearDepth();
        }

        bindForGbuffers(Attachment.COLOR);
    }

    public void clear(Attachment... clearAttachments) {
        if (framebuffer != null && clearAttachments.length > 0) {
            framebuffer.clear(clearAttachments);
        }
    }

    public void clearWrite(Attachment... clearAttachments) {
        if (framebuffer != null && clearAttachments.length > 0) {
            framebuffer.clearWrite(clearAttachments);
        }
    }

    public void copyReadToWrite(Attachment... attachments) {
        if (framebuffer != null && attachments.length > 0) {
            framebuffer.copyReadToWrite(attachments);
        }
    }

    public void bindForGbuffers(Attachment... drawTargets) {
        framebuffer.bindForGbuffers(drawTargets);
    }

    public int width() {
        return framebuffer != null ? framebuffer.getWidth() : 0;
    }

    public int height() {
        return framebuffer != null ? framebuffer.getHeight() : 0;
    }

    public int attachmentWidth(Attachment attachment) {
        return framebuffer != null ? framebuffer.getAttachmentWidth(attachment) : 0;
    }

    public int attachmentHeight(Attachment attachment) {
        return framebuffer != null ? framebuffer.getAttachmentHeight(attachment) : 0;
    }

    public void bindForFullscreenWrite(Attachment... drawTargets) {
        framebuffer.bindForFullscreenWrite(drawTargets);
    }

    public void flipWrittenTextures(Attachment... drawTargets) {
        framebuffer.flip(drawTargets);
    }
}
