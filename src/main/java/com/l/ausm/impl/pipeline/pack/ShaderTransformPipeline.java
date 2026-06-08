package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.List;

/**
 * Iris-style transform pipeline facade.
 */
public final class ShaderTransformPipeline {
    private static final List<ShaderTransformStage> STAGES = List.of(
            new FragmentOutputTransformStage(),
            new LegacySamplerAliasTransformStage(),
            new GbuffersBuiltinTransformStage(),
            new EntityAdvancedMaterialFallbackTransformStage(),
            new ComplementaryBlockIpbrTransformStage(),
            new FullscreenBuiltinTransformStage(),
            new CompositeDepthSmoothTransformStage(),
            new MakeUpVolumetricLightTransformStage(),
            new UnderwaterFogCompatibilityTransformStage(),
            new LegacyIntModuloTransformStage(),
            new ImageStoreCompatibilityTransformStage(),
            new CustomImageSamplerDeclarationTransformStage(),
            new BslBlocklightColorTableTransformStage(),
            new ComplementaryActDetailCompatibilityTransformStage(),
            new CompatibilityTextureFunctionTransformStage(),
            new MidTexCoordAliasTransformStage()
    );

    private ShaderTransformPipeline() {
    }

    public static String transform(String source, int shaderType, RenderPass pass) {
        ShaderTransformParameters parameters = ShaderTransformParameters.fromSource(source, shaderType, pass);
        String transformed = source;
        for (ShaderTransformStage stage : STAGES) {
            transformed = stage.apply(transformed, parameters);
        }
        return transformed;
    }
}
