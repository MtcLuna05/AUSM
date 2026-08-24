package com.luna.ausm.impl.pipeline;

public class PipelineContext extends PipelineDeferredPassOrchestration5 {
    /**
     * Public compatibility entrypoint for optional mods that resolve the
     * pipeline through reflection. Keep this method declared on the public
     * facade rather than relying on inheritance from a package-private part.
     */
    public static PipelineContext getInstance() {
        return INSTANCE;
    }

}
