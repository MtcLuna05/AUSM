package com.l.ausm.impl.pipeline.pack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some Complementary/Euphoria player ray-tracing includes trip NVIDIA's GLSL
 * 1.20 parser after preprocessing and report the local quadTexCoord as
 * undefined. Inline that single-use value at the texture fetch site.
 */
public final class PlayerRayTracerQuadTexCoordTransformStage implements ShaderTransformStage {
    private static final Pattern PLAYER_ATLAS_QUAD_TEXCOORD = Pattern.compile(
            "(?m)^(\\s*)vec2\\s+quadTexCoord\\s*=\\s*([^;]+);\\s*\\R\\s*vec4\\s+playerAtlasSample\\s*=\\s*texelFetch\\s*\\(\\s*playerAtlas_sampler\\s*,\\s*ivec2\\s*\\(\\s*64\\s*\\*\\s*quadTexCoord\\s*\\)\\s*,\\s*0\\s*\\)\\s*;"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || !source.contains("quadTexCoord")
                || !source.contains("playerAtlas_sampler")) {
            return source;
        }

        Matcher matcher = PLAYER_ATLAS_QUAD_TEXCOORD.matcher(source);
        return matcher.replaceAll("$1vec4 playerAtlasSample = texelFetch(playerAtlas_sampler, ivec2(64 * ($2)), 0);");
    }
}
