package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PipelineGpuTimingTest {
    @Test
    void classifiesMainAndShadowGeometrySeparately() {
        assertEquals("gbufferTerrain", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForPass(
                RenderPass.GBUFFERS_TERRAIN_SOLID, WorldRenderingPhase.TERRAIN_SOLID, false)));
        assertEquals("shadowTerrain", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForPass(
                RenderPass.SHADOW_SOLID, WorldRenderingPhase.TERRAIN_SOLID, true)));
        assertEquals("gbufferEntities", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForPass(
                RenderPass.GBUFFERS_ENTITIES, WorldRenderingPhase.ENTITIES, false)));
        assertEquals("shadowBlockEntities", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForPass(
                RenderPass.SHADOW_BLOCK, WorldRenderingPhase.BLOCK_ENTITIES, true)));
    }

    @Test
    void classifiesFullscreenAndComputeFamilies() {
        assertEquals("prepare", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForProgramArray(ProgramArrayId.BEGIN)));
        assertEquals("deferred", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForProgram(RenderPass.DEFERRED3)));
        assertEquals("composite", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForProgram(RenderPass.COMPOSITE5)));
        assertEquals("shadowPost", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForProgramArray(ProgramArrayId.SHADOWCOMP)));
        assertEquals("final", PipelineGpuTiming.scopeName(PipelineGpuTiming.scopeForProgram(RenderPass.FINAL)));
    }
}
