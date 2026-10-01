package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.pack.ShaderBlendMode;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PipelineRenderPassRulesTest {
    @Test
    void glintAddsColorWithoutReplacingDestinationAlpha() {
        assertEquals(ShaderBlendMode.parse("SRC_COLOR ONE ZERO ONE"),
                PipelineRenderPassRules.defaultBlendMode(RenderPass.GBUFFERS_ARMOR_GLINT));
    }

    @Test
    void baseItemAndOpaqueTerrainKeepTheirExistingDefaults() {
        assertNull(PipelineRenderPassRules.defaultBlendMode(RenderPass.GBUFFERS_ITEM));
        assertEquals(ShaderBlendMode.OFF,
                PipelineRenderPassRules.defaultBlendMode(RenderPass.GBUFFERS_TERRAIN));
    }
}
