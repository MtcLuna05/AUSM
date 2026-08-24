package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.luna.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.luna.ausm.api.pipeline.pack.ShaderOitSettings;
import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import com.luna.ausm.api.pipeline.pack.ShaderRenderSettings;
import com.luna.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.luna.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.luna.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.luna.ausm.impl.pipeline.pack.ShaderItemIdMap;
import com.luna.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.luna.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.shader.CustomUniformSet;
import java.util.List;
import java.util.Map;

abstract class PipelineDeferredPassOrchestration5 extends PipelineDeferredPassOrchestration4 {
    protected static ShaderProperties emptyShaderProperties() {
        return new ShaderProperties(
                Map.of(),
                Map.of(),
                ShaderOptions.empty(),
                Map.of(),
                Map.of(),
                ShaderRenderTargetSettings.empty(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new ShaderBlockIdMap.BlockIdRules(Map.of(), List.of(), Map.of()),
                Map.of(),
                new ShaderItemIdMap.ItemIdRules(Map.of(), Map.of()),
                ShaderRenderSettings.defaults(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                ShaderTextureDirectives.empty(),
                CustomUniformSet.empty(),
                new ShaderPackDirectives(
                        ShaderRenderTargetSettings.empty(),
                        ShaderRenderSettings.defaults(),
                        ShaderTextureDirectives.empty(),
                        ShaderComputeDirectives.empty(),
                        List.of(),
                        Map.of(),
                        ShaderFeatureSet.empty(),
                        256,
                        null,
                        ShaderPipelineCapabilities.from(new ShaderPackDirectives(
                                ShaderRenderTargetSettings.empty(),
                                ShaderRenderSettings.defaults(),
                                ShaderTextureDirectives.empty(),
                                ShaderComputeDirectives.empty(),
                                List.of(),
                                Map.of(),
                                ShaderFeatureSet.empty(),
                                256,
                                null,
                                null,
                                Map.of(),
                                CustomUniformSet.empty()
                        )),
                        Map.of(),
                        CustomUniformSet.empty()
                ),
                ShaderOitSettings.empty(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }
}
