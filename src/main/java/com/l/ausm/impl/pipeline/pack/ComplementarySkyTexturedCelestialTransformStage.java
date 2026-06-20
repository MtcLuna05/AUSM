package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Complementary renders procedural curved sun/moon geometry from skybasic when
 * SUN_MOON_STYLE >= 2. If the skytextured pass misses that option state, the
 * vanilla textured quad remains visible as a second square sun/moon.
 */
public final class ComplementarySkyTexturedCelestialTransformStage implements ShaderTransformStage {
    private static final Pattern CELESTIAL_STYLE_GUARD = Pattern.compile(
            "(if\\s*\\(isSun\\s*\\|\\|\\s*isMoon\\)\\s*\\{\\s*)#if\\s+SUN_MOON_STYLE\\s*>=\\s*2"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_SKYTEXTURED || !parameters.fragmentShader()) {
            return source;
        }
        if (!source.contains("renderStage == MC_RENDER_STAGE_SUN")
                || !source.contains("renderStage == MC_RENDER_STAGE_MOON")) {
            return source;
        }
        if (source.contains("AUSM_SIMPLE_VOID_WORLD")) {
            return source;
        }

        Matcher matcher = CELESTIAL_STYLE_GUARD.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        String transformed = matcher.replaceFirst("$1#if 1 // AUSM: skybasic renders Complementary procedural sun/moon");
        MainMod.LOGGER.debug("[ShaderTransform] Forced Complementary skytextured celestial discard");
        return transformed;
    }
}
