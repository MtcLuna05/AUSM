package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.impl.MainMod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compatibility fix for MakeUp's true volumetric-light path on the 1.12.2
 * backport. The pack's composite pass expects live depthtex0 to contain scene
 * depth, but in this pipeline depthtex1 is the stable pre-hand world snapshot.
 */
public final class MakeUpVolumetricLightTransformStage implements ShaderTransformStage {
    private static final Pattern VOL2_SHADOWTEX1_DECLARATION = Pattern.compile(
            "(#if\\s+VOL_LIGHT\\s*==\\s*2\\s*&&\\s*defined\\s+SHADOW_CASTING\\s*&&\\s*!defined\\s+NETHER\\b(?:(?!#endif).)*?)(\\s*uniform\\s+sampler2DShadow\\s+shadowtex1\\s*;)",
            Pattern.DOTALL
    );
    private static final Pattern VOL2_CALLS = Pattern.compile("""
            (?s)#if\\s+defined\\s+COLORED_SHADOW\\s*\
            vec3\\s+volumetricLight\\s*=\\s*get_volumetric_color_light\\s*\\(\\s*dither\\s*,\\s*screen_distance\\s*,\\s*modeli_times_projectioni\\s*\\)\\s*;\\s*\
            #else\\s*\
            float\\s+volumetricLight\\s*=\\s*get_volumetric_light\\s*\\(\\s*dither\\s*,\\s*screen_distance\\s*,\\s*modeli_times_projectioni\\s*\\)\\s*;\\s*\
            #endif""");
    private static final String PATCHED_VOL2_CALLS = """
                float volumetricScreenDistance = screen_distance;
                float snapshotDepth = texture2DLod(depthtex1, texcoord, 0).r;
                if(snapshotDepth > 0.0 && snapshotDepth < 1.0) {
                    volumetricScreenDistance = min(volumetricScreenDistance, ld(snapshotDepth) * far * 0.5);
                }

                #if defined COLORED_SHADOW
                    vec3 volumetricLight = get_volumetric_color_light(dither, volumetricScreenDistance, modeli_times_projectioni);
                #else
                    float volumetricLight = get_volumetric_light(dither, volumetricScreenDistance, modeli_times_projectioni);
                #endif""";
    private static final Pattern ORIGINAL_VOL2_MIX = Pattern.compile(
            "mix\\s*\\(\\s*blockColor\\.rgb\\s*,\\s*volumetricLightColor\\s*\\*\\s*volumetricLight\\s*,\\s*volumetricIntensity\\s*\\*\\s*\\(\\s*volumetricLight\\s*\\*\\s*0\\.5\\s*\\+\\s*0\\.5\\s*\\)\\s*\\*\\s*\\(\\s*1\\.0\\s*-\\s*rainStrength\\s*\\)\\s*\\)");
    private static final String PATCHED_VOL2_MIX =
            "mix(blockColor.rgb, volumetricLightColor * volumetricLight, volumetricIntensity * volumetricLight * (1.0 - rainStrength))";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null || !parameters.fragmentShader()) {
            return source;
        }
        if (parameters.pass().stage() != ProgramStage.COMPOSITE) {
            return source;
        }
        if (!looksLikeMakeUpVolumetricComposite(source)) {
            return source;
        }

        String transformed = addDepthtex1ToVolumetricBlock(source);
        transformed = VOL2_CALLS.matcher(transformed).replaceFirst(Matcher.quoteReplacement(PATCHED_VOL2_CALLS));
        transformed = ORIGINAL_VOL2_MIX.matcher(transformed).replaceFirst(Matcher.quoteReplacement(PATCHED_VOL2_MIX));
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Applied MakeUp VOL_LIGHT=2 depth compatibility to {}", parameters.pass().getProgramName());
        }
        return transformed;
    }

    private static String addDepthtex1ToVolumetricBlock(String source) {
        Matcher matcher = VOL2_SHADOWTEX1_DECLARATION.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        if (matcher.group(1).contains("uniform sampler2D depthtex1;")) {
            return source;
        }
        String replacement = matcher.group(1) + "    uniform sampler2D depthtex1;" + matcher.group(2);
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static boolean looksLikeMakeUpVolumetricComposite(String source) {
        return source.contains("VOL_LIGHT == 2")
                && source.contains("get_volumetric_color_light")
                && source.contains("get_volumetric_light")
                && VOL2_CALLS.matcher(source).find()
                && ORIGINAL_VOL2_MIX.matcher(source).find();
    }
}
