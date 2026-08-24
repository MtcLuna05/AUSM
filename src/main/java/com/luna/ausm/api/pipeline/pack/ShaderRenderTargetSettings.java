package com.luna.ausm.api.pipeline.pack;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public record ShaderRenderTargetSettings(
        Map<Attachment, ColorBufferFormat> formats,
        Map<RenderPass, Set<Attachment>> clearDisabledByPass,
        Map<RenderPass, Set<Attachment>> mipmapEnabledByPass,
        Map<Attachment, ShaderTextureScale> textureScales,
        Map<Attachment, float[]> clearColors,
        Map<Integer, Boolean> shadowDepthNearest,
        Map<Integer, Boolean> shadowDepthMipmap,
        Map<Integer, Boolean> shadowColorClear,
        Map<Integer, Boolean> shadowColorNearest,
        Map<Integer, Boolean> shadowColorMipmap,
        boolean shadowHardwareFiltering
) {
    public static final int SHADOW_COLOR_TARGET_COUNT = 8;

    public static ShaderRenderTargetSettings empty() {
        return new ShaderRenderTargetSettings(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), false);
    }

    public ShaderRenderTargetSettings withFormats(Map<Attachment, ColorBufferFormat> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return this;
        }

        EnumMap<Attachment, ColorBufferFormat> mergedFormats = new EnumMap<>(Attachment.class);
        mergedFormats.putAll(formats);
        mergedFormats.putAll(overrides);
        return new ShaderRenderTargetSettings(
                Map.copyOf(mergedFormats),
                clearDisabledByPass,
                mipmapEnabledByPass,
                textureScales,
                clearColors,
                shadowDepthNearest,
                shadowDepthMipmap,
                shadowColorClear,
                shadowColorNearest,
                shadowColorMipmap,
                shadowHardwareFiltering
        );
    }

    public ShaderRenderTargetSettings withClearColors(Map<Attachment, float[]> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return this;
        }

        EnumMap<Attachment, float[]> mergedClearColors = new EnumMap<>(Attachment.class);
        clearColors.forEach((attachment, color) -> mergedClearColors.put(attachment, new float[]{color[0], color[1], color[2], color[3]}));
        overrides.forEach((attachment, color) -> {
            if (color != null && color.length >= 4) {
                mergedClearColors.put(attachment, new float[]{color[0], color[1], color[2], color[3]});
            }
        });
        return new ShaderRenderTargetSettings(
                formats,
                clearDisabledByPass,
                mipmapEnabledByPass,
                textureScales,
                Map.copyOf(mergedClearColors),
                shadowDepthNearest,
                shadowDepthMipmap,
                shadowColorClear,
                shadowColorNearest,
                shadowColorMipmap,
                shadowHardwareFiltering
        );
    }

    public Set<Attachment> clearDisabled() {
        EnumSet<Attachment> attachments = EnumSet.noneOf(Attachment.class);
        clearDisabledByPass.values().forEach(attachments::addAll);
        return attachments;
    }

    public Attachment[] clearAttachmentsForPass(RenderPass pass, Iterable<Attachment> drawBuffers) {
        Set<Attachment> clearDisabled = clearDisabledForPass(pass);
        EnumSet<Attachment> attachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : drawBuffers) {
            if (!clearDisabled.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(Attachment[]::new);
    }

    public Set<Attachment> clearDisabledForPass(RenderPass pass) {
        return clearDisabledByPass.getOrDefault(pass, Set.of());
    }

    public Set<Attachment> mipmapEnabled(RenderPass pass) {
        return mipmapEnabledByPass.getOrDefault(pass, Set.of());
    }

    public float[] clearColor(Attachment attachment) {
        float[] color = clearColors.get(attachment);
        if (color != null && color.length >= 4) {
            return new float[]{color[0], color[1], color[2], color[3]};
        }
        if (attachment == Attachment.DEPTH) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        if (attachment == Attachment.COLOR) {
            return new float[]{0.0f, 0.0f, 0.0f, 1.0f};
        }
        if (attachment.getIndex() == 1) {
            return new float[]{1.0f, 1.0f, 1.0f, 1.0f};
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    public boolean shadowDepthNearest(int index) {
        return shadowDepthNearest.getOrDefault(index, false);
    }

    public boolean shadowDepthMipmap(int index) {
        return shadowDepthMipmap.getOrDefault(index, false);
    }

    public boolean shadowColorClear(int index) {
        return shadowColorClear.getOrDefault(index, true);
    }

    public boolean shadowColorNearest(int index) {
        return shadowColorNearest.getOrDefault(index, false);
    }

    public boolean shadowColorMipmap(int index) {
        return shadowColorMipmap.getOrDefault(index, false);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Attachment, ColorBufferFormat> formats = new EnumMap<>(Attachment.class);
        private final Map<RenderPass, Set<Attachment>> clearDisabledByPass = new EnumMap<>(RenderPass.class);
        private final Map<RenderPass, Set<Attachment>> mipmapEnabledByPass = new EnumMap<>(RenderPass.class);
        private final Map<Attachment, ShaderTextureScale> textureScales = new EnumMap<>(Attachment.class);
        private final Map<Attachment, float[]> clearColors = new EnumMap<>(Attachment.class);
        private final Set<Attachment> explicitFormats = EnumSet.noneOf(Attachment.class);
        private final Map<Integer, Boolean> shadowDepthNearest = new HashMap<>();
        private final Map<Integer, Boolean> shadowDepthMipmap = new HashMap<>();
        private final Map<Integer, Boolean> shadowColorClear = new HashMap<>();
        private final Map<Integer, Boolean> shadowColorNearest = new HashMap<>();
        private final Map<Integer, Boolean> shadowColorMipmap = new HashMap<>();
        private boolean shadowHardwareFiltering = false;

        public void setFormat(Attachment attachment, ColorBufferFormat format) {
            formats.put(attachment, format);
            explicitFormats.add(attachment);
        }

        public void setDefaultFormat(Attachment attachment, ColorBufferFormat format) {
            if (!explicitFormats.contains(attachment)) {
                formats.putIfAbsent(attachment, format);
            }
        }

        public void setClearEnabled(RenderPass pass, Attachment attachment, boolean enabled) {
            Set<Attachment> attachments = clearDisabledByPass.computeIfAbsent(pass, ignored -> EnumSet.noneOf(Attachment.class));
            if (enabled) {
                attachments.remove(attachment);
            } else {
                attachments.add(attachment);
            }
        }

        public void setMipmapEnabled(RenderPass pass, Attachment attachment, boolean enabled) {
            Set<Attachment> attachments = mipmapEnabledByPass.computeIfAbsent(pass, ignored -> EnumSet.noneOf(Attachment.class));
            if (enabled) {
                attachments.add(attachment);
            } else {
                attachments.remove(attachment);
            }
        }

        public void setTextureScale(Attachment attachment, ShaderTextureScale scale) {
            textureScales.put(attachment, scale);
        }

        public void setClearColor(Attachment attachment, float[] color) {
            if (color != null && color.length >= 4) {
                clearColors.put(attachment, new float[]{color[0], color[1], color[2], color[3]});
            }
        }

        public void setShadowDepthNearest(int index, boolean nearest) {
            shadowDepthNearest.put(index, nearest);
        }

        public void setShadowDepthMipmap(int index, boolean mipmap) {
            shadowDepthMipmap.put(index, mipmap);
        }

        public void setShadowColorClear(int index, boolean clear) {
            shadowColorClear.put(index, clear);
        }

        public void setShadowColorNearest(int index, boolean nearest) {
            shadowColorNearest.put(index, nearest);
        }

        public void setShadowColorMipmap(int index, boolean mipmap) {
            shadowColorMipmap.put(index, mipmap);
        }

        public void setAllShadowColorMipmap(boolean mipmap) {
            for (int i = 0; i < SHADOW_COLOR_TARGET_COUNT; i++) {
                shadowColorMipmap.put(i, mipmap);
            }
        }

        public void setShadowHardwareFiltering(boolean enabled) {
            shadowHardwareFiltering = enabled;
        }

        public ShaderRenderTargetSettings build() {
            return new ShaderRenderTargetSettings(
                    Map.copyOf(formats),
                    copyPassMap(clearDisabledByPass),
                    copyPassMap(mipmapEnabledByPass),
                    Map.copyOf(textureScales),
                    copyClearColors(clearColors),
                    Map.copyOf(shadowDepthNearest),
                    Map.copyOf(shadowDepthMipmap),
                    Map.copyOf(shadowColorClear),
                    Map.copyOf(shadowColorNearest),
                    Map.copyOf(shadowColorMipmap),
                    shadowHardwareFiltering
            );
        }

        private static Map<Attachment, float[]> copyClearColors(Map<Attachment, float[]> source) {
            Map<Attachment, float[]> copy = new EnumMap<>(Attachment.class);
            source.forEach((attachment, color) -> copy.put(attachment, new float[]{color[0], color[1], color[2], color[3]}));
            return Map.copyOf(copy);
        }

        private static Map<RenderPass, Set<Attachment>> copyPassMap(Map<RenderPass, Set<Attachment>> source) {
            Map<RenderPass, Set<Attachment>> copy = new EnumMap<>(RenderPass.class);
            source.forEach((pass, attachments) -> {
                if (!attachments.isEmpty()) {
                    copy.put(pass, Set.copyOf(attachments));
                }
            });
            return Map.copyOf(copy);
        }
    }
}
