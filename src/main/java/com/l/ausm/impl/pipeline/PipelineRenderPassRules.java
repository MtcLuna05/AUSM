package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.api.pipeline.fbo.Attachment;
import org.lwjgl.opengl.GL11;

/** Stateless render-pass classification and default blend policy. */
final class PipelineRenderPassRules {
    private PipelineRenderPassRules() { }
    static boolean defaultWaterBlendTarget(Attachment attachment) { return attachment == Attachment.COLOR || attachment == Attachment.COMPOSITE; }
    static ShaderBlendMode defaultBlendMode(RenderPass pass) {
        if (isOpaqueTerrainPass(pass) || pass == RenderPass.SHADOW || pass == RenderPass.SHADOW_SOLID || pass == RenderPass.SHADOW_CUTOUT || pass == RenderPass.SHADOW_WATER || pass == RenderPass.SHADOW_ENTITIES || pass == RenderPass.SHADOW_LIGHTNING || pass == RenderPass.SHADOW_BLOCK) return ShaderBlendMode.OFF;
        return pass == RenderPass.GBUFFERS_SPIDEREYES ? new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE) : null;
    }
    static boolean isOpaqueTerrainPass(RenderPass pass) { return pass == RenderPass.GBUFFERS_TERRAIN || pass == RenderPass.GBUFFERS_TERRAIN_SOLID || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT || pass == RenderPass.DH_TERRAIN; }
    static boolean isOitPhase(WorldRenderingPhase phase) { return switch (phase) { case TRIPWIRE, ENTITIES_TRANSLUCENT, BLOCK_ENTITIES_TRANSLUCENT, PARTICLES_TRANSLUCENT, RAIN_SNOW, CLOUDS, LIGHTNING, BEACON_BEAM -> true; default -> false; }; }
}
