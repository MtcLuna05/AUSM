package com.luna.ausm.impl.pipeline.fbo;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import java.util.EnumMap;

/**
 * A custom Framebuffer implementation designed for the deferred pipeline.
 * Unlike vanilla's Framebuffer which handles a single color + depth,
 * this supports up to 8 Multiple Render Targets (MRT).
 */
public class DeferredFramebuffer extends DeferredFramebufferDepthReadback {
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
}
