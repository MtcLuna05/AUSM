package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.luna.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.luna.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.luna.ausm.api.pipeline.pack.ShaderImageDirective;
import com.luna.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.luna.ausm.api.pipeline.pack.ShaderRenderSettings;
import com.luna.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.luna.ausm.api.pipeline.pack.ShaderStorageBufferDirective;
import com.luna.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.luna.ausm.api.pipeline.shader.ProgramId;
import com.luna.ausm.impl.pipeline.shader.CustomUniformSet;
import java.util.List;
import java.util.Map;

/**
 * Pack-level directive bundle shaped after Iris' PackDirectives.
 */
public record ShaderPackDirectives(
        ShaderRenderTargetSettings renderTargets,
        ShaderRenderSettings renderSettings,
        ShaderTextureDirectives textureDirectives,
        ShaderComputeDirectives computeDirectives,
        List<ShaderImageDirective> images,
        Map<Integer, ShaderStorageBufferDirective> storageBuffers,
        ShaderFeatureSet features,
        int noiseTextureResolution,
        ShaderCustomTextureBinding noiseTexture,
        ShaderPipelineCapabilities capabilities,
        Map<ProgramId, ShaderProgramDirectives> programDirectives,
        CustomUniformSet customUniforms
) {
    public ShaderPackDirectives withComputeDirectives(ShaderComputeDirectives computeDirectives) {
        return new ShaderPackDirectives(
                renderTargets,
                renderSettings,
                textureDirectives,
                computeDirectives,
                images,
                storageBuffers,
                features,
                noiseTextureResolution,
                noiseTexture,
                capabilities,
                programDirectives,
                customUniforms
        );
    }

    public ShaderPackDirectives withCapabilities(ShaderPipelineCapabilities capabilities) {
        return new ShaderPackDirectives(
                renderTargets,
                renderSettings,
                textureDirectives,
                computeDirectives,
                images,
                storageBuffers,
                features,
                noiseTextureResolution,
                noiseTexture,
                capabilities,
                programDirectives,
                customUniforms
        );
    }
}
