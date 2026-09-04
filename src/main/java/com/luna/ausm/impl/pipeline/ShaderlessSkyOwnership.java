package com.luna.ausm.impl.pipeline;

final class ShaderlessSkyOwnership {
    private ShaderlessSkyOwnership() {
    }

    static boolean shouldReplaceVanillaSky(boolean pipelineActive) {
        // Native shaderless skies own their complete geometry. AUSM only owns
        // shadered sky output and the shaderless GUI presentation boundary.
        return false;
    }
}
