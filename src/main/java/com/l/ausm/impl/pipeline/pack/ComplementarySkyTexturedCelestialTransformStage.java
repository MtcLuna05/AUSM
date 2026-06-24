package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keep Complementary's procedural celestial path authoritative. Its
 * skytextured pass still receives vanilla sun/moon quads from Minecraft 1.12,
 * so discard those when the pack is configured to render procedural bodies.
 */
public final class ComplementarySkyTexturedCelestialTransformStage implements ShaderTransformStage {
    private static final Pattern CELESTIAL_STYLE_GUARD = Pattern.compile(
            "(if\\s*\\(isSun\\s*\\|\\|\\s*isMoon\\)\\s*\\{\\s*)#if\\s+SUN_MOON_STYLE\\s*>=\\s*2"
    );
    private static final String CELESTIAL_BRANCH = "        if (isSun || isMoon) {\n";
    private static final String CELESTIAL_BRANCH_WITH_MASK =
            "        if (isSun || isMoon) {\n"
                    + "            float ausmCelestialMask = max(max(color.r, color.g), color.b);\n"
                    + "            float ausmCelestialCutoff = isSun ? 0.16 : 0.16;\n"
                    + "            float ausmCelestialFadeEnd = isSun ? 0.20 : 0.24;\n"
                    + "            if (ausmCelestialMask < ausmCelestialCutoff) discard; // AUSM: remove opaque dark 1.12 celestial padding\n"
                    + "            float ausmCelestialCoverage = smoothstep(ausmCelestialCutoff, ausmCelestialFadeEnd, ausmCelestialMask);\n"
                    + "            color.a *= ausmCelestialCoverage;\n"
                    + "            color.rgb = isSun ? vec3(0.85 + 0.15 * ausmCelestialCoverage) : vec3(ausmCelestialMask * (0.12 + 0.28 * ausmCelestialCoverage)); // AUSM: vanilla 1.12 celestial textures are masks here\n";
    private static final String HORIZON_FACTOR_LINE = "            color.rgb *= GetHorizonFactor(VdotU);\n";
    private static final String HORIZON_FACTOR_LINE_SOFT =
            "            float ausmCelestialHorizon = smoothstep(-0.22, 0.10, VdotU);\n"
                    + "            color.a *= ausmCelestialHorizon; // AUSM: soften the narrow 1.12 celestial horizon split\n"
                    + "            color.rgb *= ausmCelestialHorizon;\n";
    private static final String MOON_BRANCH =
            "            if (isMoon) {\n"
                    + "                color.rgb *= smoothstep1(min1(length(color.rgb))) * 1.3;\n"
                    + "            }\n";
    private static final String MOON_BRANCH_WITH_GLOW =
            "            if (isMoon) {\n"
                    + "                color.rgb *= smoothstep1(min1(length(color.rgb))) * 1.3;\n"
                    + "                #if SUN_MOON_STYLE == 1\n"
                    + "                    color.rgb += vec3(0.18, 0.20, 0.28) * ausmCelestialCoverage * (1.0 - 0.6 * rainFactor); // AUSM: Reimagined textured moon glow\n"
                    + "                #endif\n"
                    + "            }\n";
    private static final String CUSTOM_SKY_BRANCH = "        } else { // Custom Sky\n";
    private static final String CUSTOM_SKY_BRANCH_WITH_ASTRAL =
            "        } else { // Custom Sky\n"
                    + "            bool ausmLegacyCustomSkybox = renderStage == MC_RENDER_STAGE_VOID\n"
                    + "                                      || renderStage == MC_RENDER_STAGE_CUSTOM_SKY\n"
                    + "                                      || renderStage == MC_RENDER_STAGE_SKY_TEXTURED; // AUSM: preserve 1.12 custom skyboxes\n"
                    + "            if (renderStage == MC_RENDER_STAGE_ASTRAL_STARS) {\n"
                    + "                float ausmAstralStarLuma = max(max(color.r, color.g), color.b);\n"
                    + "                float ausmAstralTextureAlpha = color.a;\n"
                    + "                float ausmAstralVertexFade = max(glColor.a, 0.35); // AUSM: Astral's display-list stars can lose fixed-function alpha in shaders\n"
                    + "                float ausmAstralStarAlpha = ausmAstralTextureAlpha * ausmAstralVertexFade;\n"
                    + "                float ausmAstralStarMask = max(max(ausmAstralStarLuma, ausmAstralTextureAlpha), ausmAstralStarAlpha);\n"
                    + "                if (ausmAstralStarMask < 0.0005) discard; // AUSM: Astral stars can be alpha-only quads\n"
                    + "                float ausmAstralStarCoverage = smoothstep(0.001, 0.075, ausmAstralStarMask);\n"
                    + "                color.a = max(ausmAstralStarAlpha, ausmAstralStarCoverage * 0.70); // AUSM: treat texture alpha as coverage when vertex alpha is unavailable\n"
                    + "                color.rgb = vec3(0.72, 0.80, 1.0) * (0.70 + 2.80 * ausmAstralStarCoverage); // AUSM: treat Astral star texture as coverage, not albedo\n"
                    + "            } else if (renderStage == MC_RENDER_STAGE_ASTRAL_SOLAR_ECLIPSE) {\n"
                    + "                #if SUN_MOON_STYLE >= 2\n"
                    + "                    discard; // AUSM: Unbound eclipse is procedural in skybasic\n"
                    + "                #endif\n"
                    + "                float ausmEclipseMask = max(max(color.r, color.g), color.b);\n"
                    + "                if (ausmEclipseMask < 0.02) discard;\n"
                    + "                color.rgb *= smoothstep(0.02, 0.25, ausmEclipseMask) * 1.4; // AUSM: Reimagined Astral solar eclipse corona overlay\n"
                    + "                color.a *= glColor.a;\n"
                    + "            } else if (ausmLegacyCustomSkybox) {\n"
                    + "                color.a *= glColor.a; // AUSM: Complementary discards old-MC custom skyboxes by default\n"
                    + "                if (color.a < 0.01) discard;\n"
                    + "            } else {\n";
    private static final String CUSTOM_SKY_END =
            "            #endif\n"
                    + "        }\n"
                    + "\n"
                    + "        if (isEyeInWater == 1)";
    private static final String CUSTOM_SKY_END_WITH_ASTRAL =
            "            #endif\n"
                    + "            }\n"
                    + "        }\n"
                    + "\n"
                    + "        if (isEyeInWater == 1)";
    private static final String RAIN_ALPHA =
            "        #ifdef SUN_MOON_DURING_RAIN\n"
                    + "            color.a *= 1.0 - 0.8 * rainFactor;\n"
                    + "        #else\n"
                    + "            color.a *= 1.0 - rainFactor;\n"
                    + "        #endif\n";
    private static final String RAIN_ALPHA_WITH_SKYBOX_GUARD =
            "        if (renderStage != MC_RENDER_STAGE_VOID\n"
                    + "                && renderStage != MC_RENDER_STAGE_CUSTOM_SKY\n"
                    + "                && renderStage != MC_RENDER_STAGE_SKY_TEXTURED) { // AUSM: do not fade legacy custom skyboxes\n"
                    + "            #ifdef SUN_MOON_DURING_RAIN\n"
                    + "                color.a *= 1.0 - 0.8 * rainFactor;\n"
                    + "            #else\n"
                    + "                color.a *= 1.0 - rainFactor;\n"
                    + "            #endif\n"
                    + "        }\n";
    private static final String SIMPLE_VOID_BRANCH = "        #ifdef AUSM_SIMPLE_VOID_WORLD\n";
    private static final String SIMPLE_VOID_BRANCH_WITH_ASTRAL =
            "        #ifdef AUSM_SIMPLE_VOID_WORLD\n"
                    + "            bool ausmAstralStarStage = renderStage == MC_RENDER_STAGE_ASTRAL_STARS;\n"
                    + "            bool ausmAstralStarTexture = abs(tSize.x - 32.0) < 0.5 && abs(tSize.y - 32.0) < 0.5;\n"
                    + "            if (ausmAstralStarTexture) {\n"
                    + "                float ausmAstralCornerAlpha = max(max(texture2D(tex, vec2(0.015625, 0.015625)).a,\n"
                    + "                                                   texture2D(tex, vec2(0.984375, 0.015625)).a),\n"
                    + "                                               max(texture2D(tex, vec2(0.015625, 0.984375)).a,\n"
                    + "                                                   texture2D(tex, vec2(0.984375, 0.984375)).a));\n"
                    + "                float ausmAstralEdgeAlpha = max(max(texture2D(tex, vec2(0.5, 0.015625)).a,\n"
                    + "                                                 texture2D(tex, vec2(0.5, 0.984375)).a),\n"
                    + "                                             max(texture2D(tex, vec2(0.015625, 0.5)).a,\n"
                    + "                                                 texture2D(tex, vec2(0.984375, 0.5)).a));\n"
                    + "                float ausmAstralCoreAlpha = max(max(texture2D(tex, vec2(0.5, 0.5)).a,\n"
                    + "                                                 texture2D(tex, vec2(0.4375, 0.5)).a),\n"
                    + "                                             max(texture2D(tex, vec2(0.5625, 0.5)).a,\n"
                    + "                                                 texture2D(tex, vec2(0.5, 0.4375)).a));\n"
                    + "                ausmAstralCoreAlpha = max(ausmAstralCoreAlpha, texture2D(tex, vec2(0.5, 0.5625)).a);\n"
                    + "                ausmAstralStarTexture = ausmAstralCornerAlpha < 0.02 && ausmAstralEdgeAlpha < 0.10 && ausmAstralCoreAlpha > 0.50;\n"
                    + "            }\n"
                    + "            bool ausmAstralStarFallback = ausmAstralStarTexture\n"
                    + "                                       && renderStage != MC_RENDER_STAGE_SUN\n"
                    + "                                       && renderStage != MC_RENDER_STAGE_ASTRAL_SOLAR_ECLIPSE;\n"
                    + "            if (ausmAstralStarStage || ausmAstralStarFallback) {\n"
                    + "                float ausmAstralStarLuma = max(max(color.r, color.g), color.b);\n"
                    + "                float ausmAstralTextureAlpha = color.a;\n"
                    + "                float ausmAstralVertexFade = max(glColor.a, 0.35); // AUSM: Astral's display-list stars can lose fixed-function alpha in shaders\n"
                    + "                float ausmAstralStarAlpha = ausmAstralTextureAlpha * ausmAstralVertexFade;\n"
                    + "                float ausmAstralStarMask = max(max(ausmAstralStarLuma, ausmAstralTextureAlpha), ausmAstralStarAlpha);\n"
                    + "                if (ausmAstralStarMask < 0.0005) discard; // AUSM: keep alpha-backed Astral star quads, reject padding\n"
                    + "                float ausmAstralStarCoverage = smoothstep(0.001, 0.075, ausmAstralStarMask);\n"
                    + "                vec3 ausmAstralStarTint = mix(vec3(0.72, 0.80, 1.0), glColor.rgb, step(0.01, max(max(glColor.r, glColor.g), glColor.b)));\n"
                    + "                #if ASTRAL_CONSTELLATION_STYLE > 0\n"
                    + "                    ausmAstralStarTint = AusmConstellationTint(glColor.rgb);\n"
                    + "                #endif\n"
                    + "                color.a = max(ausmAstralStarAlpha, ausmAstralStarCoverage * 0.70);\n"
                    + "                color.rgb = ausmAstralStarTint * (0.70 + 2.80 * ausmAstralStarCoverage);\n"
                    + "                if (isEyeInWater == 1) color.rgb *= 0.25;\n"
                    + "                #ifdef COLOR_CODED_PROGRAMS\n"
                    + "                    ColorCodeProgram(color, -1);\n"
                    + "                #endif\n"
                    + "                /* DRAWBUFFERS:0 */\n"
                    + "                gl_FragData[0] = color;\n"
                    + "                return;\n"
                    + "            }\n"
                    + "            if (renderStage == MC_RENDER_STAGE_ASTRAL_SOLAR_ECLIPSE) discard; // AUSM: procedural eclipse is handled by skybasic\n";
    private static final String SIMPLE_VOID_MOON_DETAIL =
            "                        vec2 ausmMoonCoord = texCoord * 1.7 + vec2(0.137, 0.617);\n"
                    + "                        float ausmMoonNoiseA = texture2D(noisetex, ausmMoonCoord).g;\n"
                    + "                        float ausmMoonNoiseB = texture2D(noisetex, ausmMoonCoord * 4.0 + 0.137).g;\n"
                    + "                        float ausmMoonSurface = clamp(max0(ausmMoonNoiseA + ausmMoonNoiseB * 0.65 - 0.54) * 1.45, 0.0, 1.0);\n"
                    + "                        float ausmMoonCraters = smoothstep(0.62, 1.0, ausmMoonNoiseB)\n"
                    + "                                              * smoothstep(0.18, 0.72, ausmMoonSurface);\n"
                    + "                        vec3 ausmMoonColor = vec3(0.30, 0.33, 0.42) * (1.05 - 0.42 * ausmMoonSurface);\n"
                    + "                        ausmMoonColor *= 1.0 - 0.34 * ausmMoonCraters;\n"
                    + "                        ausmMoonColor += vec3(0.035, 0.04, 0.055) * ausmMoonSurface;\n";
    private static final String SIMPLE_VOID_MOON_DETAIL_DISC_LOCAL =
            "                        vec3 ausmMoonCenter = normalize(-sunVec);\n"
                    + "                        vec3 ausmMoonTangentRaw = cross(upVec, ausmMoonCenter);\n"
                    + "                        vec3 ausmMoonTangent = normalize(dot(ausmMoonTangentRaw, ausmMoonTangentRaw) > 0.0001 ? ausmMoonTangentRaw : vec3(1.0, 0.0, 0.0));\n"
                    + "                        vec3 ausmMoonBitangent = cross(ausmMoonCenter, ausmMoonTangent);\n"
                    + "                        vec2 ausmMoonLocal = vec2(dot(nViewPos, ausmMoonTangent), dot(nViewPos, ausmMoonBitangent)) * 82.0;\n"
                    + "                        float ausmMoonNoiseA = texture2D(noisetex, ausmMoonLocal * 0.085 + vec2(0.137, 0.617)).g;\n"
                    + "                        float ausmMoonNoiseB = texture2D(noisetex, ausmMoonLocal * 0.210 + vec2(0.731, 0.271)).g;\n"
                    + "                        float ausmMoonSurface = clamp(max0(ausmMoonNoiseA + ausmMoonNoiseB * 0.55 - 0.48) * 1.85, 0.0, 1.0);\n"
                    + "                        vec2 ausmMoonTileUvA = texCoord * 2.0 - 1.0;\n"
                    + "                        vec2 ausmMoonTileUvB = fract(texCoord * vec2(4.0, 2.0)) * 2.0 - 1.0;\n"
                    + "                        vec2 ausmMoonCrater0 = ausmMoonLocal - vec2(-1.45, 0.88);\n"
                    + "                        vec2 ausmMoonCrater1 = ausmMoonLocal - vec2(1.18, -0.56);\n"
                    + "                        vec2 ausmMoonCrater2 = ausmMoonLocal - vec2(0.10, 1.72);\n"
                    + "                        vec2 ausmMoonUvCrater0 = ausmMoonTileUvA - vec2(-0.34, 0.24);\n"
                    + "                        vec2 ausmMoonUvCrater1 = ausmMoonTileUvA - vec2(0.36, -0.16);\n"
                    + "                        vec2 ausmMoonUvCrater2 = ausmMoonTileUvB - vec2(-0.18, -0.42);\n"
                    + "                        float ausmMoonCraters = (1.0 - smoothstep(0.36, 0.88, dot(ausmMoonCrater0, ausmMoonCrater0))) * smoothstep(0.10, 0.30, dot(ausmMoonCrater0, ausmMoonCrater0));\n"
                    + "                        ausmMoonCraters += (1.0 - smoothstep(0.22, 0.56, dot(ausmMoonCrater1, ausmMoonCrater1))) * smoothstep(0.055, 0.20, dot(ausmMoonCrater1, ausmMoonCrater1));\n"
                    + "                        ausmMoonCraters += (1.0 - smoothstep(0.14, 0.34, dot(ausmMoonCrater2, ausmMoonCrater2))) * smoothstep(0.035, 0.12, dot(ausmMoonCrater2, ausmMoonCrater2));\n"
                    + "                        ausmMoonCraters += (1.0 - smoothstep(0.13, 0.22, dot(ausmMoonUvCrater0, ausmMoonUvCrater0))) * smoothstep(0.018, 0.055, dot(ausmMoonUvCrater0, ausmMoonUvCrater0));\n"
                    + "                        ausmMoonCraters += (1.0 - smoothstep(0.08, 0.16, dot(ausmMoonUvCrater1, ausmMoonUvCrater1))) * smoothstep(0.012, 0.040, dot(ausmMoonUvCrater1, ausmMoonUvCrater1));\n"
                    + "                        ausmMoonCraters += (1.0 - smoothstep(0.06, 0.12, dot(ausmMoonUvCrater2, ausmMoonUvCrater2))) * smoothstep(0.010, 0.032, dot(ausmMoonUvCrater2, ausmMoonUvCrater2));\n"
                    + "                        ausmMoonCraters = clamp(ausmMoonCraters + ausmMoonSurface * 0.18, 0.0, 1.0);\n"
                    + "                        vec3 ausmMoonColor = mix(vec3(0.46, 0.48, 0.58), vec3(0.18, 0.19, 0.27), ausmMoonSurface);\n"
                    + "                        ausmMoonColor *= 1.0 - 0.68 * ausmMoonCraters;\n"
                    + "                        ausmMoonColor += vec3(0.060, 0.066, 0.082) * (1.0 - ausmMoonSurface) + vec3(0.050, 0.054, 0.070) * ausmMoonCraters * (1.0 - ausmMoonSurface);\n";
    private static final String SIMPLE_VOID_SKY_MOON_DETAIL =
            "                            vec2 ausmSkyMoonCoord = texCoord * 1.7 + vec2(0.137, 0.617);\n"
                    + "                            float ausmSkyMoonNoiseA = texture2D(noisetex, ausmSkyMoonCoord).g;\n"
                    + "                            float ausmSkyMoonNoiseB = texture2D(noisetex, ausmSkyMoonCoord * 4.0 + 0.137).g;\n"
                    + "                            float ausmSkyMoonSurface = clamp(max0(ausmSkyMoonNoiseA + ausmSkyMoonNoiseB * 0.65 - 0.54) * 1.45, 0.0, 1.0);\n"
                    + "                            float ausmSkyMoonCraters = smoothstep(0.62, 1.0, ausmSkyMoonNoiseB)\n"
                    + "                                                     * smoothstep(0.18, 0.72, ausmSkyMoonSurface);\n"
                    + "                            vec3 ausmSkyMoonColor = vec3(0.30, 0.33, 0.42) * (1.05 - 0.42 * ausmSkyMoonSurface);\n"
                    + "                            ausmSkyMoonColor *= 1.0 - 0.34 * ausmSkyMoonCraters;\n"
                    + "                            ausmSkyMoonColor += vec3(0.035, 0.04, 0.055) * ausmSkyMoonSurface;\n";
    private static final String SIMPLE_VOID_SKY_MOON_DETAIL_DISC_LOCAL =
            "                            vec3 ausmSkyMoonCenter = normalize(-sunVec);\n"
                    + "                            vec3 ausmSkyMoonTangentRaw = cross(upVec, ausmSkyMoonCenter);\n"
                    + "                            vec3 ausmSkyMoonTangent = normalize(dot(ausmSkyMoonTangentRaw, ausmSkyMoonTangentRaw) > 0.0001 ? ausmSkyMoonTangentRaw : vec3(1.0, 0.0, 0.0));\n"
                    + "                            vec3 ausmSkyMoonBitangent = cross(ausmSkyMoonCenter, ausmSkyMoonTangent);\n"
                    + "                            vec2 ausmSkyMoonLocal = vec2(dot(nViewPos, ausmSkyMoonTangent), dot(nViewPos, ausmSkyMoonBitangent)) * 82.0;\n"
                    + "                            float ausmSkyMoonNoiseA = texture2D(noisetex, ausmSkyMoonLocal * 0.085 + vec2(0.137, 0.617)).g;\n"
                    + "                            float ausmSkyMoonNoiseB = texture2D(noisetex, ausmSkyMoonLocal * 0.210 + vec2(0.731, 0.271)).g;\n"
                    + "                            float ausmSkyMoonSurface = clamp(max0(ausmSkyMoonNoiseA + ausmSkyMoonNoiseB * 0.55 - 0.48) * 1.85, 0.0, 1.0);\n"
                    + "                            vec2 ausmSkyMoonTileUvA = texCoord * 2.0 - 1.0;\n"
                    + "                            vec2 ausmSkyMoonTileUvB = fract(texCoord * vec2(4.0, 2.0)) * 2.0 - 1.0;\n"
                    + "                            vec2 ausmSkyMoonCrater0 = ausmSkyMoonLocal - vec2(-1.45, 0.88);\n"
                    + "                            vec2 ausmSkyMoonCrater1 = ausmSkyMoonLocal - vec2(1.18, -0.56);\n"
                    + "                            vec2 ausmSkyMoonCrater2 = ausmSkyMoonLocal - vec2(0.10, 1.72);\n"
                    + "                            vec2 ausmSkyMoonUvCrater0 = ausmSkyMoonTileUvA - vec2(-0.34, 0.24);\n"
                    + "                            vec2 ausmSkyMoonUvCrater1 = ausmSkyMoonTileUvA - vec2(0.36, -0.16);\n"
                    + "                            vec2 ausmSkyMoonUvCrater2 = ausmSkyMoonTileUvB - vec2(-0.18, -0.42);\n"
                    + "                            float ausmSkyMoonCraters = (1.0 - smoothstep(0.36, 0.88, dot(ausmSkyMoonCrater0, ausmSkyMoonCrater0))) * smoothstep(0.10, 0.30, dot(ausmSkyMoonCrater0, ausmSkyMoonCrater0));\n"
                    + "                            ausmSkyMoonCraters += (1.0 - smoothstep(0.22, 0.56, dot(ausmSkyMoonCrater1, ausmSkyMoonCrater1))) * smoothstep(0.055, 0.20, dot(ausmSkyMoonCrater1, ausmSkyMoonCrater1));\n"
                    + "                            ausmSkyMoonCraters += (1.0 - smoothstep(0.14, 0.34, dot(ausmSkyMoonCrater2, ausmSkyMoonCrater2))) * smoothstep(0.035, 0.12, dot(ausmSkyMoonCrater2, ausmSkyMoonCrater2));\n"
                    + "                            ausmSkyMoonCraters += (1.0 - smoothstep(0.13, 0.22, dot(ausmSkyMoonUvCrater0, ausmSkyMoonUvCrater0))) * smoothstep(0.018, 0.055, dot(ausmSkyMoonUvCrater0, ausmSkyMoonUvCrater0));\n"
                    + "                            ausmSkyMoonCraters += (1.0 - smoothstep(0.08, 0.16, dot(ausmSkyMoonUvCrater1, ausmSkyMoonUvCrater1))) * smoothstep(0.012, 0.040, dot(ausmSkyMoonUvCrater1, ausmSkyMoonUvCrater1));\n"
                    + "                            ausmSkyMoonCraters += (1.0 - smoothstep(0.06, 0.12, dot(ausmSkyMoonUvCrater2, ausmSkyMoonUvCrater2))) * smoothstep(0.010, 0.032, dot(ausmSkyMoonUvCrater2, ausmSkyMoonUvCrater2));\n"
                    + "                            ausmSkyMoonCraters = clamp(ausmSkyMoonCraters + ausmSkyMoonSurface * 0.18, 0.0, 1.0);\n"
                    + "                            vec3 ausmSkyMoonColor = mix(vec3(0.46, 0.48, 0.58), vec3(0.18, 0.19, 0.27), ausmSkyMoonSurface);\n"
                    + "                            ausmSkyMoonColor *= 1.0 - 0.68 * ausmSkyMoonCraters;\n"
                    + "                            ausmSkyMoonColor += vec3(0.060, 0.066, 0.082) * (1.0 - ausmSkyMoonSurface) + vec3(0.050, 0.054, 0.070) * ausmSkyMoonCraters * (1.0 - ausmSkyMoonSurface);\n";

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_SKYTEXTURED) {
            return source;
        }
        if (!parameters.fragmentShader()) {
            return source;
        }
        if (!source.contains("renderStage == MC_RENDER_STAGE_SUN")
                || !source.contains("renderStage == MC_RENDER_STAGE_MOON")) {
            return source;
        }
        if (source.contains("AUSM_SIMPLE_VOID_WORLD")) {
            String transformed = source;
            transformed = allowSimpleVoidAstralStars(transformed);
            transformed = addSimpleVoidMoonDetail(transformed);
            return transformed;
        }

        String transformed = source;
        transformed = discardBlackCelestialPadding(transformed);
        transformed = boostReimaginedMoon(transformed);
        transformed = softenCelestialHorizon(transformed);
        transformed = allowAstralStars(transformed);
        transformed = preserveLegacySkyboxAlpha(transformed);

        if (!usesProceduralCelestialStyle(transformed)) {
            return transformed;
        }

        Matcher matcher = CELESTIAL_STYLE_GUARD.matcher(transformed);
        if (!matcher.find()) {
            return transformed;
        }

        transformed = matcher.replaceFirst("$1#if 1 // AUSM: skybasic renders Complementary procedural sun/moon");
        return transformed;
    }

    private static String discardBlackCelestialPadding(String source) {
        if (source.contains("opaque dark 1.12 celestial padding")) {
            return source;
        }
        return source.replace(CELESTIAL_BRANCH, CELESTIAL_BRANCH_WITH_MASK);
    }

    private static String softenCelestialHorizon(String source) {
        if (source.contains("soften the narrow 1.12 celestial horizon split")) {
            return source;
        }
        return source.replace(HORIZON_FACTOR_LINE, HORIZON_FACTOR_LINE_SOFT);
    }

    private static String boostReimaginedMoon(String source) {
        if (source.contains("Reimagined textured moon glow")) {
            return source;
        }
        return source.replace(MOON_BRANCH, MOON_BRANCH_WITH_GLOW);
    }

    private static String allowAstralStars(String source) {
        if (source.contains("treat texture alpha as coverage")) {
            return source;
        }

        int branch = source.indexOf(CUSTOM_SKY_BRANCH);
        if (branch < 0) {
            return source;
        }
        int end = source.indexOf(CUSTOM_SKY_END, branch);
        if (end < 0) {
            return source;
        }

        String transformed = source.substring(0, branch)
                + CUSTOM_SKY_BRANCH_WITH_ASTRAL
                + source.substring(branch + CUSTOM_SKY_BRANCH.length(), end)
                + CUSTOM_SKY_END_WITH_ASTRAL
                + source.substring(end + CUSTOM_SKY_END.length());
        return transformed;
    }

    private static boolean usesProceduralCelestialStyle(String source) {
        int explicitStyle = intDefine(source, "SUN_MOON_STYLE_DEFINE", -1);
        if (explicitStyle >= 0) {
            return explicitStyle >= 2;
        }

        // Complementary Unbound defaults to procedural celestial bodies, while
        // Reimagined style 1 intentionally keeps the textured vanilla quads.
        int shaderStyle = intDefine(source, "SHADER_STYLE", -1);
        return shaderStyle == 4;
    }

    private static String preserveLegacySkyboxAlpha(String source) {
        if (source.contains("do not fade legacy custom skyboxes")) {
            return source;
        }
        return source.replace(RAIN_ALPHA, RAIN_ALPHA_WITH_SKYBOX_GUARD);
    }

    private static String allowSimpleVoidAstralStars(String source) {
        if (source.contains("AusmVoidAstralStarColor") || source.contains("keep alpha-backed Astral star quads")) {
            return source;
        }
        return source.replace(SIMPLE_VOID_BRANCH, SIMPLE_VOID_BRANCH_WITH_ASTRAL);
    }

    private static String addSimpleVoidMoonDetail(String source) {
        if (source.contains("ausmMoonTangentRaw") || source.contains("ausmSkyMoonTangentRaw")) {
            return source;
        }
        return source
                .replace(SIMPLE_VOID_MOON_DETAIL, SIMPLE_VOID_MOON_DETAIL_DISC_LOCAL)
                .replace(SIMPLE_VOID_SKY_MOON_DETAIL, SIMPLE_VOID_SKY_MOON_DETAIL_DISC_LOCAL)
                .replace("                                ausmMoonColor *= 8.5;\n", "                                ausmMoonColor *= 4.8;\n")
                .replace("                            } else ausmMoonColor *= 10.0;\n", "                            } else ausmMoonColor *= 5.6;\n")
                .replace("                                    ausmSkyMoonColor *= 8.5;\n", "                                    ausmSkyMoonColor *= 4.8;\n")
                .replace("                                } else ausmSkyMoonColor *= 10.0;\n", "                                } else ausmSkyMoonColor *= 5.6;\n");
    }

    private static int intDefine(String source, String name, int fallback) {
        Pattern pattern = Pattern.compile("(?m)^\\s*#define\\s+" + Pattern.quote(name) + "\\s+(-?\\d+)\\b");
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
