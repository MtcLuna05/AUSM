package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.api.pipeline.shader.ComputeProgramSource;

import java.util.List;

/** Static source-selection rules for indexed program arrays. */
final class PipelineProgramArrayRules {
    private PipelineProgramArrayRules() { }
    static int computeSourceCount(ShaderComputeDirectives directives) {
        if (directives == null) return 0;
        int count = directives.shadowComputes().size() + directives.finalComputes().size();
        for (List<ComputeProgramSource> sources : directives.computeArrays().values()) count += sources.size();
        return count;
    }
    static int index(ProgramArrayId arrayId, String sourceName) {
        ShaderProperties.ProgramArrayKey key = ShaderProperties.ProgramArrayKey.parse(sourceName);
        return key == null || key.arrayId() != arrayId ? 0 : key.index();
    }
    static boolean shouldCompile(ProgramArrayId arrayId, int index) {
        return switch (arrayId) { case SETUP, BEGIN, SHADOWCOMP -> true; case PREPARE -> index >= 1; case DEFERRED -> index >= RenderPass.DEFERRED_PASSES.length; case COMPOSITE -> index >= RenderPass.COMPOSITE_PASSES.length; };
    }
    static RenderPass bindingPass(ProgramArrayId arrayId) {
        return switch (arrayId) { case SETUP, BEGIN, PREPARE -> RenderPass.PREPARE; case DEFERRED -> RenderPass.DEFERRED; case COMPOSITE -> RenderPass.COMPOSITE; case SHADOWCOMP -> RenderPass.SHADOW; };
    }
}
