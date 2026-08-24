package com.luna.ausm.api.pipeline.pack;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Iris-style per-program directive bundle.
 *
 * <p>AUSM still stores the source maps during the migration, but new pipeline
 * code should prefer this ProgramId-keyed view over raw RenderPass lookups.</p>
 */
public record ShaderProgramDirectives(
        ProgramId programId,
        List<Attachment> drawBuffers,
        ShaderViewportScale viewportScale,
        ShaderAlphaTest alphaTestOverride,
        ShaderBlendMode blendModeOverride,
        Map<Attachment, ShaderBlendMode> attachmentBlendModes,
        Set<Attachment> clearDisabledBuffers,
        Set<Attachment> mipmappedBuffers,
        Map<Attachment, Boolean> explicitFlips
) {
    public Attachment[] clearAttachments(Iterable<Attachment> buffers) {
        EnumSet<Attachment> attachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : buffers) {
            if (!clearDisabledBuffers.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(Attachment[]::new);
    }

    public Attachment[] flippedAttachments(Iterable<Attachment> buffers) {
        List<Attachment> flipped = new ArrayList<>();
        for (Attachment attachment : buffers) {
            if (explicitFlips.get(attachment) != Boolean.FALSE) {
                flipped.add(attachment);
            }
        }
        explicitFlips.forEach((attachment, shouldFlip) -> {
            if (shouldFlip && !flipped.contains(attachment)) {
                flipped.add(attachment);
            }
        });
        return flipped.toArray(Attachment[]::new);
    }

    public static ShaderProgramDirectives empty(ProgramId programId) {
        return new ShaderProgramDirectives(
                programId,
                List.of(),
                ShaderViewportScale.DEFAULT,
                null,
                null,
                Map.of(),
                Set.of(),
                Set.of(),
                Map.of()
        );
    }
}
