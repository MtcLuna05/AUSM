package com.l.ausm.api.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import java.util.List;
import java.util.Map;

public record ShaderOitSettings(
        boolean enabled,
        List<Integer> gbufferCoefficientRanks,
        Map<Attachment, BufferMode> gbufferBuffers,
        Map<Attachment, ColorBufferFormat> gbufferFormats
) {
    public ShaderOitSettings {
        gbufferCoefficientRanks = gbufferCoefficientRanks == null ? List.of() : List.copyOf(gbufferCoefficientRanks);
        gbufferBuffers = gbufferBuffers == null ? Map.of() : Map.copyOf(gbufferBuffers);
        gbufferFormats = gbufferFormats == null ? Map.of() : Map.copyOf(gbufferFormats);
    }

    public static ShaderOitSettings empty() {
        return new ShaderOitSettings(false, List.of(), Map.of(), Map.of());
    }

    public boolean activeForGbuffers() {
        return enabled && !gbufferBuffers.isEmpty();
    }

    public boolean coefficientBuffer(Attachment attachment) {
        BufferMode mode = gbufferBuffers.get(attachment);
        return mode != null && mode.type() == BufferMode.Type.COEFFICIENT;
    }

    public boolean frontmostBuffer(Attachment attachment) {
        BufferMode mode = gbufferBuffers.get(attachment);
        return mode != null && mode.type() == BufferMode.Type.FRONTMOST;
    }

    public record BufferMode(Type type, int coefficientIndex) {
        public enum Type {
            COEFFICIENT,
            FRONTMOST
        }

        public static BufferMode coefficient(int index) {
            return new BufferMode(Type.COEFFICIENT, Math.max(0, index));
        }

        public static BufferMode frontmost() {
            return new BufferMode(Type.FRONTMOST, -1);
        }
    }
}
