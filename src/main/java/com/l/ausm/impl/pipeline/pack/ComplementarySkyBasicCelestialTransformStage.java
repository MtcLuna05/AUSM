package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

/**
 * Small 1.12-specific corrections for Complementary skybasic celestial bodies.
 * Astral replaces the vanilla sky renderer, so AUSM routes its sky dome through
 * this pass to preserve Complementary's procedural Unbound sun/moon.
 */
public final class ComplementarySkyBasicCelestialTransformStage implements ShaderTransformStage {
    private static final String GLSL_MARKER = "AUSM: Unbound celestial compatibility";
    private static final String GL_COLOR_DECL = "flat in vec4 glColor;\n";
    private static final String GL_COLOR_DECL_WITH_UNIFORM =
            "flat in vec4 glColor;\n"
                    + "\n"
                    + "uniform float ausmAstralSolarEclipse; // " + GLSL_MARKER + "\n";
    private static final String SKY_COLOR =
            "    color.rgb = GetSky(VdotU, VdotS, dither, true, false);\n";
    private static final String SKY_COLOR_WITH_STAGE =
            "    bool ausmSkyGroundStage = renderStage == MC_RENDER_STAGE_SKY_GROUND || renderStage == MC_RENDER_STAGE_SUNSET;\n"
                    + "    bool ausmSkyCelestialStage = renderStage == MC_RENDER_STAGE_SKY;\n"
                    + "    color.rgb = GetSky(VdotU, VdotS, dither, true, ausmSkyGroundStage); // " + GLSL_MARKER + "\n"
                    + "    if (renderStage == MC_RENDER_STAGE_SUNSET) color.a = glColor.a; // AUSM: Astral's horizon fan is translucent geometry\n";
    private static final String STARS =
            "        color.rgb += GetStars(starCoord, VdotU, VdotS);\n";
    private static final String STARS_STAGE_GATED =
            "        if (ausmSkyCelestialStage) color.rgb += GetStars(starCoord, VdotU, VdotS); // " + GLSL_MARKER + "\n";
    private static final String PROCEDURAL_STYLE_GUARD =
            "        #if SUN_MOON_STYLE >= 2\n"
                    + "            float absVdotS = abs(VdotS);\n";
    private static final String PROCEDURAL_STYLE_GUARD_EXPLICIT =
            "        #if SUN_MOON_STYLE >= 2 && SUN_MOON_STYLE_DEFINE != 1 // AUSM: respect explicit Reimagined sun/moon override\n"
                    + "            float absVdotS = (renderStage == MC_RENDER_STAGE_SKY || renderStage == MC_RENDER_STAGE_SUN || renderStage == MC_RENDER_STAGE_MOON) ? abs(VdotS) : 0.0;\n";
    private static final String SUN_MIX =
            "                    color.rgb = mix(color.rgb, vec3(0.9, 0.5, 0.3) * 25.0, sunMoonMixer);\n";
    private static final String SUN_MIX_WITH_ECLIPSE =
            "                    float ausmEclipse = clamp(ausmAstralSolarEclipse, 0.0, 1.0);\n"
                    + "                    float ausmEclipseCore = ausmEclipse * smoothstep(sunSizeFactor1, 1.0, VdotS);\n"
                    + "                    float ausmEclipseCorona = ausmEclipse\n"
                    + "                                           * smoothstep(sunSizeFactor1 - 0.0015, sunSizeFactor1 + 0.0008, VdotS)\n"
                    + "                                           * (1.0 - smoothstep(0.9992, 1.0, VdotS));\n"
                    + "                    vec3 ausmSunColor = mix(vec3(0.9, 0.5, 0.3) * 25.0, vec3(0.018, 0.011, 0.008), ausmEclipseCore);\n"
                    + "                    ausmSunColor += vec3(1.0, 0.44, 0.16) * 18.0 * ausmEclipseCorona;\n"
                    + "                    color.rgb = mix(color.rgb, ausmSunColor, max(sunMoonMixer, max(ausmEclipseCore * 0.98, ausmEclipseCorona * 0.55)));\n";
    private static final String MOON_NOISE =
            "                    float moonNoise = texture2DLod(noisetex, starCoord, 0.0).g\n"
                    + "                                    + texture2DLod(noisetex, starCoord * 2.5, 0.0).g * 0.7\n"
                    + "                                    + texture2DLod(noisetex, starCoord * 5.0, 0.0).g * 0.5;\n"
                    + "                    moonNoise = max0(moonNoise - 0.75) * 1.7;\n"
                    + "                    vec3 moonColor = vec3(0.38, 0.4, 0.5) * (1.2 - (0.2 + 0.2 * sqrt1(nightFactor)) * moonNoise);\n";
    private static final String MOON_NOISE_WITH_DETAIL =
            "                    vec3 ausmMoonCenter = normalize(-sunVec);\n"
                    + "                    vec3 ausmMoonTangentRaw = cross(upVec, ausmMoonCenter);\n"
                    + "                    vec3 ausmMoonTangent = normalize(dot(ausmMoonTangentRaw, ausmMoonTangentRaw) > 0.0001 ? ausmMoonTangentRaw : vec3(1.0, 0.0, 0.0));\n"
                    + "                    vec3 ausmMoonBitangent = cross(ausmMoonCenter, ausmMoonTangent);\n"
                    + "                    vec2 ausmMoonLocal = vec2(dot(nViewPos, ausmMoonTangent), dot(nViewPos, ausmMoonBitangent)) * 82.0;\n"
                    + "                    float moonNoise = texture2D(noisetex, ausmMoonLocal * 0.085 + vec2(0.137, 0.617)).g\n"
                    + "                                    + texture2D(noisetex, ausmMoonLocal * 0.210 + vec2(0.731, 0.271)).g * 0.55;\n"
                    + "                    float ausmMoonSurface = clamp(max0(moonNoise - 0.48) * 1.85, 0.0, 1.0);\n"
                    + "                    vec2 ausmMoonCrater0 = ausmMoonLocal - vec2(-1.45, 0.88);\n"
                    + "                    vec2 ausmMoonCrater1 = ausmMoonLocal - vec2(1.18, -0.56);\n"
                    + "                    vec2 ausmMoonCrater2 = ausmMoonLocal - vec2(0.10, 1.72);\n"
                    + "                    float ausmMoonCraters = (1.0 - smoothstep(0.36, 0.88, dot(ausmMoonCrater0, ausmMoonCrater0))) * smoothstep(0.10, 0.30, dot(ausmMoonCrater0, ausmMoonCrater0));\n"
                    + "                    ausmMoonCraters += (1.0 - smoothstep(0.22, 0.56, dot(ausmMoonCrater1, ausmMoonCrater1))) * smoothstep(0.055, 0.20, dot(ausmMoonCrater1, ausmMoonCrater1));\n"
                    + "                    ausmMoonCraters += (1.0 - smoothstep(0.14, 0.34, dot(ausmMoonCrater2, ausmMoonCrater2))) * smoothstep(0.035, 0.12, dot(ausmMoonCrater2, ausmMoonCrater2));\n"
                    + "                    ausmMoonCraters = clamp(ausmMoonCraters + ausmMoonSurface * 0.18, 0.0, 1.0);\n"
                    + "                    vec3 moonColor = mix(vec3(0.46, 0.48, 0.58), vec3(0.18, 0.19, 0.27), ausmMoonSurface);\n"
                    + "                    moonColor *= 1.0 - 0.68 * ausmMoonCraters;\n"
                    + "                    moonColor += vec3(0.060, 0.066, 0.082) * (1.0 - ausmMoonSurface) + vec3(0.050, 0.054, 0.070) * ausmMoonCraters * (1.0 - ausmMoonSurface);\n";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_SKYBASIC || !parameters.fragmentShader()) {
            return source;
        }
        if (!source.contains("SUN_MOON_STYLE >= 2") || !source.contains("moonPhaseFactor1")) {
            return source;
        }

        String transformed = source;
        if (!transformed.contains(GLSL_MARKER)) {
            transformed = transformed.replace(GL_COLOR_DECL, GL_COLOR_DECL_WITH_UNIFORM);
        }
        transformed = transformed.replace(SKY_COLOR, SKY_COLOR_WITH_STAGE);
        transformed = transformed.replace(STARS, STARS_STAGE_GATED);
        transformed = transformed.replace(PROCEDURAL_STYLE_GUARD, PROCEDURAL_STYLE_GUARD_EXPLICIT);
        transformed = transformed.replace(SUN_MIX, SUN_MIX_WITH_ECLIPSE);
        transformed = transformed.replace(MOON_NOISE, MOON_NOISE_WITH_DETAIL);
        transformed = transformed.replace("                            moonColor *= 8.5;\n", "                            moonColor *= 4.8;\n");
        transformed = transformed.replace("                        } else moonColor *= 10.0;\n", "                        } else moonColor *= 5.6;\n");
        transformed = transformed.replace("                    } else moonColor *= 4.0;\n", "                    } else moonColor *= 3.0;\n");

        return transformed;
    }
}
