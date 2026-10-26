package com.luna.ausm.impl.pipeline.pack;

/** Adapts OptiFine material attributes to the GLSL 1.20 vertex input model. */
final class LegacyVertexAttributeTransformStage implements ShaderTransformStage {
    private static final String INTEGER_BLOCK_ENTITY_ATTRIBUTE = "attribute int blockEntityId;";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.vertexShader()
                || !parameters.compatibilityProfile()
                || !source.contains(INTEGER_BLOCK_ENTITY_ATTRIBUTE)) {
            return source;
        }
        return source.replace(INTEGER_BLOCK_ENTITY_ATTRIBUTE,
                "// AUSM GLSL 1.20 vertex attribute compatibility\nattribute float blockEntityId;");
    }
}
