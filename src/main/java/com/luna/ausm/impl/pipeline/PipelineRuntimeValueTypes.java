package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.shader.ComputeProgram;
import com.luna.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderKey;
import com.luna.ausm.impl.pipeline.shader.ShaderMap;
import com.luna.ausm.impl.pipeline.shader.ShaderProgramSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

abstract class PipelineRuntimeValueTypes extends PipelineRuntimeDiagnosticsState {
    protected static final class PassScope {
        protected final boolean bound;
        final RenderPass previousPass;
        final ShaderKey previousShaderKey;
        final WorldRenderingPhase previousPhase;
        final boolean previousProgramTessellated;
        final boolean previousProgramGeometric;

        PassScope(boolean bound, RenderPass previousPass, ShaderKey previousShaderKey, WorldRenderingPhase previousPhase, boolean previousProgramTessellated, boolean previousProgramGeometric) {
            this.bound = bound;
            this.previousPass = previousPass;
            this.previousShaderKey = previousShaderKey;
            this.previousPhase = previousPhase;
            this.previousProgramTessellated = previousProgramTessellated;
            this.previousProgramGeometric = previousProgramGeometric;
        }

        boolean bound() {
            return bound;
        }

        RenderPass previousPass() {
            return previousPass;
        }

        ShaderKey previousShaderKey() {
            return previousShaderKey;
        }

        WorldRenderingPhase previousPhase() {
            return previousPhase;
        }

        boolean previousProgramTessellated() {
            return previousProgramTessellated;
        }

        boolean previousProgramGeometric() {
            return previousProgramGeometric;
        }
    }

    protected static final class CompiledPipelineState {
        protected final ShaderProgramSet programSet;
        final ShaderMap shaderMap;
        final Map<RenderPass, PipelineProgram> programs;
        final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays;
        final List<ComputeProgram> shadowComputePrograms;
        final List<ComputeProgram> finalComputePrograms;
        final Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms;

        CompiledPipelineState(
                ShaderProgramSet programSet,
                ShaderMap shaderMap,
                Map<RenderPass, PipelineProgram> programs,
                Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays,
                List<ComputeProgram> shadowComputePrograms,
                List<ComputeProgram> finalComputePrograms,
                Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms
        ) {
            this.programSet = programSet;
            this.shaderMap = shaderMap;
            this.programs = new EnumMap<>(programs);
            this.computeProgramArrays = new EnumMap<>(computeProgramArrays);
            this.shadowComputePrograms = List.copyOf(shadowComputePrograms);
            this.finalComputePrograms = List.copyOf(finalComputePrograms);
            this.fullscreenArrayPrograms = new EnumMap<>(fullscreenArrayPrograms);
        }

        void delete() {
            programs.values().forEach(PipelineProgram::delete);
            computeProgramArrays.values().stream()
                    .flatMap(List::stream)
                    .forEach(ComputeProgram::delete);
            shadowComputePrograms.forEach(ComputeProgram::delete);
            finalComputePrograms.forEach(ComputeProgram::delete);
            fullscreenArrayPrograms.values().stream()
                    .flatMap(List::stream)
                    .forEach(FullscreenArrayProgram::delete);
        }
    }

    protected record VoidSkyRepairSamples(boolean needsRepair, String summary) {
    }

    protected record VoidSkyRepairPixel(int r, int g, int b, int a, float depth) {
        protected boolean skyDepth() {
            return depth >= 0.999F;
        }

        int brightness() {
            return Math.max(r, Math.max(g, b));
        }

        String summary(int x, int y) {
            return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ") depth=" + depth;
        }
    }

    protected static final class NothiriumSparseMainRepairResult {
        protected final int solidWork;
        final int cutoutMippedWork;
        final int cutoutWork;
        final boolean setup;

        NothiriumSparseMainRepairResult(int solidWork, int cutoutMippedWork, int cutoutWork, boolean setup) {
            this.solidWork = solidWork;
            this.cutoutMippedWork = cutoutMippedWork;
            this.cutoutWork = cutoutWork;
            this.setup = setup;
        }

        int totalWork() {
            return Math.max(0, solidWork) + Math.max(0, cutoutMippedWork) + Math.max(0, cutoutWork);
        }
    }
}
