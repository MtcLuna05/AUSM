package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramStage;

public class PipelineContext extends PipelineDeferredPassOrchestration5 {
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

}
