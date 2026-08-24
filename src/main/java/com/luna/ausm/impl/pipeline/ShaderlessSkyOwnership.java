package com.luna.ausm.impl.pipeline;

final class ShaderlessSkyOwnership {
    private ShaderlessSkyOwnership() {
    }

    static boolean shouldReplaceVanillaSky(boolean pipelineActive) {
        return !pipelineActive;
    }
}
