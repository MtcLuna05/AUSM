package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Entree's final pass adds a screen-space noisetex dither. In AUSM's final
 * wrapper path a bad noisetex binding turns that into visible full-screen noise,
 * so keep the shader's local variable but neutralize the color delta.
 */
public final class FinalDitherNeutralizeTransformStage implements ShaderTransformStage {
    private static final Pattern ENTREE_FINAL_DITHER = Pattern.compile(
            "float\\s+dither\\s*=\\s*texture2DLod\\s*\\(\\s*noisetex\\s*,\\s*texCoord\\s*\\*\\s*view\\s*/\\s*128\\.0\\s*,\\s*0\\.0\\s*\\)\\.b\\s*;\\s*"
                    + "color\\s*\\+=\\s*vec3\\s*\\(\\s*\\(\\s*dither\\s*-\\s*0\\.25\\s*\\)\\s*/\\s*128\\.0\\s*\\)\\s*;"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()
                || parameters.pass() != RenderPass.FINAL
                || source == null
                || !source.contains("texture2DLod(noisetex")
                || !source.contains("color += vec3((dither - 0.25) / 128.0)")) {
            return source;
        }

        Matcher matcher = ENTREE_FINAL_DITHER.matcher(source);
        return matcher.replaceAll(Matcher.quoteReplacement("""
                float dither = 0.25;
                    color += vec3(0.0);"""));
    }
}
