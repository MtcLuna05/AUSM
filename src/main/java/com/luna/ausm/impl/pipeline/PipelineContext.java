package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import org.lwjgl.opengl.GL20;

public class PipelineContext extends PipelineDeferredPassOrchestration5 {
    /** Curvy Pipes supplies legacy arrays, not AUSM's extended terrain stride. */
    public void prepareCurvyPipesDraw() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }
        if (renderingShadowMap) {
            restorePassAfterDistantHorizons();
        } else {
            restoreActiveWorldPassAfterExternalShader();
        }
        PipelineGlState.disablePipelineVertexAttributes();
        // Disabled generic arrays retain their last constant values. Do not
        // classify pipes as the last terrain block or reuse its tangent.
        GL20.glVertexAttrib4f(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, 0, 0, 0, 0);
        GL20.glVertexAttrib2f(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0, 0);
        GL20.glVertexAttrib4f(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1, 0, 0, 1);
        GL20.glVertexAttrib4f(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0, 0, 0, 0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    public boolean canCaptureDistantHorizonsFrame() {
        return isPipelineActive && worldFrameActive && !renderingShadowMap
                && !isBetterPortalsExternalWorldTarget() && pingPongManager.isInitialized();
    }

    /** Also restores the shadow pass when DH is called by vanilla's shadow fallback. */
    public void restorePassAfterDistantHorizons() {
        if (isPipelineActive && worldFrameActive && activePass != null
                && (activePass.stage() == ProgramStage.GBUFFERS || activePass.stage() == ProgramStage.SHADOW)) {
            if (renderingShadowMap) {
                shadowFramebuffer.bindForRendering();
            } else {
                bindWorldFramebuffer();
            }
            self().bindPass(activePass);
        }
    }

    /**
     * Public compatibility entrypoint for optional mods that resolve the
     * pipeline through reflection. Keep this method declared on the public
     * facade rather than relying on inheritance from a package-private part.
     */
    public static PipelineContext getInstance() {
        return INSTANCE;
    }

    /** Starts a detail timer for the shadered main-view solid-terrain draw route. */
    public int beginTerrainSolidGpuProbe(boolean indirect) {
        if (!isPipelineActive || renderingShadowMap || getPhase() != WorldRenderingPhase.TERRAIN_SOLID) {
            return -1;
        }
        return PipelineGpuTiming.begin(indirect
                ? PipelineGpuTiming.TERRAIN_SOLID_INDIRECT_DRAWS
                : PipelineGpuTiming.TERRAIN_SOLID_SCALAR_DRAWS);
    }

    public void endTerrainSolidGpuProbe(int token) {
        PipelineGpuTiming.end(token);
    }

    public void startGpuTraceCapture() {
        PipelineGpuTiming.startCapture();
    }

    public void stopGpuTraceCapture() {
        PipelineGpuTiming.stopCapture();
    }

    public boolean isGpuTraceCaptureRequested() {
        return PipelineGpuTiming.isCaptureRequested();
    }

    public String getLastGpuTraceSummary() {
        return PipelineGpuTiming.lastSummary();
    }

}
