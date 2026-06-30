package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;

import java.util.Map;

/**
 * Pack-level directive bundle shaped after Iris' PackDirectives.
 */
public record ShaderPackDirectives(
        ShaderRenderTargetSettings renderTargets,
        ShaderRenderSettings renderSettings,
        ShaderTextureDirectives textureDirectives,
        ShaderComputeDirectives computeDirectives,
        java.util.List<ShaderImageDirective> images,
        java.util.Map<Integer, ShaderStorageBufferDirective> storageBuffers,
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
