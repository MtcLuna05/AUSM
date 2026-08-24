package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies AUSM's owned lower-sky dome correction in the final present pass.
 */
public final class AusmOfficialSkyDomeTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM_OFFICIAL_SKY_DOME_TRANSFORM";
    private static final Pattern MAIN_DECLARATION =
            Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");
    private static final Pattern FRAG_DATA_ZERO_ASSIGNMENT =
            Pattern.compile("\\bgl_FragData\\s*\\[\\s*0\\s*]\\s*=");
    private static final Pattern FRAG_COLOR_ASSIGNMENT =
            Pattern.compile("\\bgl_FragColor\\s*=");
    private static final Pattern OFFICIAL_FINAL_COLOR_DECLARATION =
            Pattern.compile("(?m)^\\s*vec4\\s+ausmOfficialFinalColor\\s*;");
    private static final Pattern VERSION_OR_EXTENSION =
            Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader() || parameters.pass() != RenderPass.FINAL) {
            return source;
        }
        if (source.contains(MARKER) || source.contains("AUSM_SKY_TEST_ENABLED")) {
            return source;
        }

        Matcher mainMatcher = MAIN_DECLARATION.matcher(source);
        if (!mainMatcher.find()) {
            return source;
        }

        String transformed = mainMatcher.replaceFirst("void ausmOriginalFinalMain()");
        transformed = FRAG_DATA_ZERO_ASSIGNMENT.matcher(transformed)
                .replaceAll(Matcher.quoteReplacement("ausmOfficialFinalColor ="));
        transformed = FRAG_COLOR_ASSIGNMENT.matcher(transformed)
                .replaceAll(Matcher.quoteReplacement("ausmOfficialFinalColor ="));
        transformed = insertPreamble(transformed);
        return transformed + "\n" + officialWrapperSource();
    }

    public static String builtinFinalFragmentSource() {
        return """
                #version 120

                #define AUSM_OFFICIAL_SKY_DOME_TRANSFORM

                uniform sampler2D colortex0;
                uniform sampler2D depthtex0;
                uniform sampler2D depthtex1;
                uniform sampler2D depthtex2;
                uniform vec3 skyColor;
                uniform float rainFactor;
                uniform int worldTime;
                uniform int hasSkylight;
                uniform int ausmSimpleVoidWorld;
                uniform int ausmSkyboxRepair;
                uniform int ausmUiSkyRepair;
                uniform int hideGUI;
                uniform int ausmGuiScreen;
                uniform float viewWidth;
                uniform float viewHeight;
                uniform vec3 sunPosition;
                uniform vec3 upPosition;
                uniform mat4 gbufferProjectionInverse;

                float ausmOfficial01(float value) {
                    return clamp(value, 0.0, 1.0);
                }

                float ausmOfficialSmoother(float value) {
                    float t = ausmOfficial01(value);
                    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
                }

                vec3 ausmOfficialDesaturate(vec3 color, float saturation) {
                    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
                    return mix(vec3(luminance), color, ausmOfficial01(saturation));
                }

                vec3 ausmOfficialSkyColor(vec2 uv) {
                    if (ausmSimpleVoidWorld <= 0) {
                        float horizonY = 0.50;
                        float softness = 0.70;
                        vec3 source = max(skyColor, vec3(0.0));
                        vec3 topColor = clamp(source * 1.08, 0.0, 1.0);
                        vec3 horizonColor = mix(source, ausmOfficialDesaturate(source, 0.55), 0.35);
                        vec3 lowerColor = mix(horizonColor, source, 0.20);
                        float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                        return mix(lowerColor, topColor, ausmOfficialSmoother(band));
                    }

                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;

                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);

                    vec3 topColor = dayTop;
                    vec3 lowerColor = dayLower;

                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    vec3 result = mix(lowerColor, topColor, ausmOfficialSmoother(band));

                    float rainAmount = ausmOfficial01(rainFactor);
                    vec3 rainyDome = mix(lowerColor, topColor, 0.48);
                    result = mix(result, rainyDome, ausmOfficial01(rainAmount * 0.75));

                     vec3 rainColor = min(result, vec3(0.17, 0.185, 0.235));
                     return mix(result, rainColor, ausmOfficial01(rainAmount * 0.50));
                 }

                vec3 ausmOfficialUiSkyColor(vec2 uv) {
                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;
                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);
                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    return mix(dayLower, dayTop, ausmOfficialSmoother(band));
                }
                """ + officialCelestialFunctionsSource() + """

                 float ausmOfficialSkyRepairAmount(vec2 uv, vec3 color) {
                     float minChannel = min(min(color.r, color.g), color.b);
                    float skyMax = max(max(skyColor.r, skyColor.g), skyColor.b);
                    bool whiteClear = minChannel > 0.985 && skyMax > 0.012;
                    return whiteClear ? 1.0 : 0.0;
                }

                float ausmOfficialSceneDepth(vec2 uv);

                bool ausmOfficialHasNearbySceneGeometry(vec2 uv) {
                    vec2 texel = 1.0 / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    for (int y = -1; y <= 1; y++) {
                        for (int x = -1; x <= 1; x++) {
                            if (x == 0 && y == 0) {
                                continue;
                            }
                            vec2 sampleUv = clamp(uv + vec2(float(x), float(y)) * texel, vec2(0.0), vec2(1.0));
                            if (ausmOfficialSceneDepth(sampleUv) < 0.999) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                bool ausmOfficialShouldRepairSkyPixel(float depth, vec3 color, vec2 uv) {
                    float maxChannel = max(max(color.r, color.g), color.b);
                    float minChannel = min(min(color.r, color.g), color.b);
                    if (ausmSkyboxRepair > 0) {
                        bool missingSkyColor = maxChannel < 0.006 || minChannel > 0.985;
                        return depth > 0.999 && missingSkyColor && !ausmOfficialHasNearbySceneGeometry(uv);
                    }
                    return false;
                }

                bool ausmOfficialShouldRepairUiSkyPixel(float depth, vec3 color, vec2 uv) {
                    float maxChannel = max(max(color.r, color.g), color.b);
                    float minChannel = min(min(color.r, color.g), color.b);
                    float liveDepth = texture2D(depthtex0, uv).r;
                    bool cleanSkyDepth = liveDepth > 0.999;
                    bool missingSkyColor = maxChannel < 0.012 || minChannel > 0.985;
                    bool staleDarkSky = maxChannel < 0.12 && liveDepth > 0.98;
                    return cleanSkyDepth || missingSkyColor || staleDarkSky;
                }

                float ausmOfficialSceneDepth(vec2 uv) {
                    float liveDepth = texture2D(depthtex0, uv).r;
                    float preTranslucentDepth = texture2D(depthtex1, uv).r;
                    float postHandDepth = texture2D(depthtex2, uv).r;
                    float sceneDepth = liveDepth;
                    if (preTranslucentDepth > 0.0001 && preTranslucentDepth < 0.999) {
                        sceneDepth = min(sceneDepth, preTranslucentDepth);
                    }
                    if (postHandDepth > 0.0001 && postHandDepth < 0.999) {
                        sceneDepth = min(sceneDepth, postHandDepth);
                    }
                    return sceneDepth;
                }

                void main() {
                    vec2 uv = gl_FragCoord.xy / max(vec2(viewWidth, viewHeight), vec2(1.0));
                     vec4 color = texture2D(colortex0, uv);
                     float depth = ausmOfficialSceneDepth(uv);
                     bool ausmOfficialSkyPixel = depth > 0.999 && !ausmOfficialHasNearbySceneGeometry(uv);
                    if (ausmUiSkyRepair > 0 && ausmOfficialSkyPixel
                            && ausmOfficialShouldRepairUiSkyPixel(depth, color.rgb, uv)) {
                         color.rgb = ausmOfficialUiSkyColor(uv);
                     } else if (ausmOfficialShouldRepairSkyPixel(depth, color.rgb, uv) && ausmSkyboxRepair > 0) {
                         color.rgb = ausmOfficialSkyColor(uv);
                     } else if (depth > 0.999 && ausmSkyboxRepair > 0) {
                         float repairAmount = ausmOfficialSkyRepairAmount(uv, color.rgb);
                         color.rgb = mix(color.rgb, ausmOfficialSkyColor(uv), repairAmount);
                    }
                    gl_FragData[0] = vec4(color.rgb, 1.0);
                }
                """;
    }

    private static String insertPreamble(String source) {
        StringBuilder preamble = new StringBuilder();
        appendUniformIfMissing(preamble, source, "sampler2D", "colortex0");
        appendUniformIfMissing(preamble, source, "sampler2D", "depthtex0");
        appendUniformIfMissing(preamble, source, "sampler2D", "depthtex1");
        appendUniformIfMissing(preamble, source, "sampler2D", "depthtex2");
        appendUniformIfMissing(preamble, source, "vec3", "skyColor");
        appendUniformIfMissing(preamble, source, "float", "rainFactor");
        appendUniformIfMissing(preamble, source, "int", "worldTime");
        appendUniformIfMissing(preamble, source, "int", "hasSkylight");
        appendUniformIfMissing(preamble, source, "int", "ausmSimpleVoidWorld");
        appendUniformIfMissing(preamble, source, "int", "ausmSkyboxRepair");
        appendUniformIfMissing(preamble, source, "int", "ausmUiSkyRepair");
        appendUniformIfMissing(preamble, source, "int", "hideGUI");
        appendUniformIfMissing(preamble, source, "int", "ausmGuiScreen");
        appendUniformIfMissing(preamble, source, "float", "viewWidth");
        appendUniformIfMissing(preamble, source, "float", "viewHeight");
        appendUniformIfMissing(preamble, source, "vec3", "sunPosition");
        appendUniformIfMissing(preamble, source, "vec3", "upPosition");
        appendUniformIfMissing(preamble, source, "mat4", "gbufferProjectionInverse");
        if (!OFFICIAL_FINAL_COLOR_DECLARATION.matcher(source).find()) {
            preamble.append("vec4 ausmOfficialFinalColor;\n");
        }
        if (preamble.length() == 0) {
            return source;
        }

        Matcher matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + preamble + source.substring(insertAt);
    }

    private static void appendUniformIfMissing(StringBuilder uniforms, String source, String type, String name) {
        Pattern declaration = Pattern.compile("(?m)^\\s*uniform\\s+\\w+\\s+" + Pattern.quote(name) + "\\s*;");
        if (!declaration.matcher(source).find()) {
            uniforms.append("uniform ").append(type).append(' ').append(name).append(";\n");
        }
    }

    private static String officialCelestialFunctionsSource() {
        return """

                #ifndef SUN_MOON_STYLE
                #define SUN_MOON_STYLE 2
                #endif
                #ifndef AUSM_VOID_CELESTIALS
                #define AUSM_VOID_CELESTIALS 0
                #endif
                #ifndef AUSM_VOID_CELESTIAL_BRIGHTNESS
                #define AUSM_VOID_CELESTIAL_BRIGHTNESS 100
                #endif

                float ausmOfficialMax0(float value) {
                    return max(value, 0.0);
                }

                float ausmOfficialPow2(float value) {
                    return value * value;
                }

                vec3 ausmOfficialSafeNormalize(vec3 value, vec3 fallback) {
                    float len2 = dot(value, value);
                    return len2 > 0.000001 ? value * inversesqrt(len2) : fallback;
                }

                vec3 ausmOfficialViewDir(vec2 uv) {
                    vec4 clip = vec4(uv * 2.0 - 1.0, 1.0, 1.0);
                    vec4 view = gbufferProjectionInverse * clip;
                    return ausmOfficialSafeNormalize(view.xyz, vec3(0.0, 0.0, -1.0));
                }

                float ausmOfficialHorizonFactor(float sdotu) {
                    return ausmOfficial01(abs(sdotu) * 6.0 + 0.35);
                }

                vec3 ausmOfficialApplyVoidCelestials(vec3 sky, vec2 uv) {
                #if AUSM_VOID_CELESTIALS == 0
                    return sky;
                #else
                    vec3 viewDir = ausmOfficialViewDir(uv);
                    vec3 sunVec = ausmOfficialSafeNormalize(sunPosition, vec3(0.0, 1.0, 0.0));
                    vec3 upVec = ausmOfficialSafeNormalize(upPosition, vec3(0.0, 1.0, 0.0));
                    float sdotu = dot(sunVec, upVec);
                    float rain2 = ausmOfficialPow2(ausmOfficial01(rainFactor));

                #if SUN_MOON_STYLE == 2
                    float sunSizeFactor1 = 0.9975;
                    float sunSizeFactor2 = 400.0;
                #elif SUN_MOON_STYLE >= 3
                    float sunSizeFactor1 = 0.9983;
                    float sunSizeFactor2 = 588.235;
                #else
                    float sunSizeFactor1 = 0.9966;
                    float sunSizeFactor2 = 294.0;
                #endif

                    float vdotSun = dot(viewDir, sunVec);
                    if (vdotSun > sunSizeFactor1) {
                        float mixer = ausmOfficialPow2(sqrt(sunSizeFactor2 * (vdotSun - sunSizeFactor1)));
                        mixer *= 1.0 - rain2;
                        mixer *= ausmOfficialHorizonFactor(sdotu);
                        sky = mix(sky, vec3(0.9, 0.5, 0.3) * 25.0 * (AUSM_VOID_CELESTIAL_BRIGHTNESS * 0.01), ausmOfficial01(mixer));
                    }

                    return sky;
                #endif
                }
                """;
    }

    private static String officialWrapperSource() {
        return """

                #ifndef AUSM_OFFICIAL_SKY_DOME_TRANSFORM
                #define AUSM_OFFICIAL_SKY_DOME_TRANSFORM

                float ausmOfficial01(float value) {
                    return clamp(value, 0.0, 1.0);
                }

                float ausmOfficialSmoother(float value) {
                    float t = ausmOfficial01(value);
                    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
                }

                vec3 ausmOfficialDesaturate(vec3 color, float saturation) {
                    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
                    return mix(vec3(luminance), color, ausmOfficial01(saturation));
                }

                vec3 ausmOfficialSkyColor(vec2 uv) {
                    if (ausmSimpleVoidWorld <= 0) {
                        float horizonY = 0.50;
                        float softness = 0.70;
                        vec3 source = max(skyColor, vec3(0.0));
                        vec3 topColor = clamp(source * 1.08, 0.0, 1.0);
                        vec3 horizonColor = mix(source, ausmOfficialDesaturate(source, 0.55), 0.35);
                        vec3 lowerColor = mix(horizonColor, source, 0.20);
                        float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                        return mix(lowerColor, topColor, ausmOfficialSmoother(band));
                    }

                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;

                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);

                    vec3 topColor = dayTop;
                    vec3 lowerColor = dayLower;

                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    vec3 result = mix(lowerColor, topColor, ausmOfficialSmoother(band));

                    float rainAmount = ausmOfficial01(rainFactor);
                    vec3 rainyDome = mix(lowerColor, topColor, 0.48);
                    result = mix(result, rainyDome, ausmOfficial01(rainAmount * 0.75));

                     vec3 rainColor = min(result, vec3(0.17, 0.185, 0.235));
                     return mix(result, rainColor, ausmOfficial01(rainAmount * 0.50));
                 }

                vec3 ausmOfficialUiSkyColor(vec2 uv) {
                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;
                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);
                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    return mix(dayLower, dayTop, ausmOfficialSmoother(band));
                }
                """ + officialCelestialFunctionsSource() + """

                float ausmOfficialSkyRepairAmount(vec2 uv, vec3 color) {
                    float minChannel = min(min(color.r, color.g), color.b);
                    float skyMax = max(max(skyColor.r, skyColor.g), skyColor.b);
                    bool whiteClear = minChannel > 0.985 && skyMax > 0.012;
                    return whiteClear ? 1.0 : 0.0;
                }

                float ausmOfficialSceneDepth(vec2 uv);

                bool ausmOfficialHasNearbySceneGeometry(vec2 uv) {
                    vec2 texel = 1.0 / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    for (int y = -1; y <= 1; y++) {
                        for (int x = -1; x <= 1; x++) {
                            if (x == 0 && y == 0) {
                                continue;
                            }
                            vec2 sampleUv = clamp(uv + vec2(float(x), float(y)) * texel, vec2(0.0), vec2(1.0));
                            if (ausmOfficialSceneDepth(sampleUv) < 0.999) {
                                return true;
                            }
                        }
                    }
                    return false;
                }

                bool ausmOfficialShouldRepairSkyPixel(float depth, vec3 color, vec2 uv) {
                    float maxChannel = max(max(color.r, color.g), color.b);
                    float minChannel = min(min(color.r, color.g), color.b);
                    if (ausmSkyboxRepair > 0) {
                        bool missingSkyColor = maxChannel < 0.006 || minChannel > 0.985;
                        return depth > 0.999 && missingSkyColor && !ausmOfficialHasNearbySceneGeometry(uv);
                    }
                    return false;
                }

                bool ausmOfficialShouldRepairUiSkyPixel(float depth, vec3 color, vec2 uv) {
                    float maxChannel = max(max(color.r, color.g), color.b);
                    float minChannel = min(min(color.r, color.g), color.b);
                    float liveDepth = texture2D(depthtex0, uv).r;
                    bool cleanSkyDepth = liveDepth > 0.999;
                    bool missingSkyColor = maxChannel < 0.012 || minChannel > 0.985;
                    bool staleDarkSky = maxChannel < 0.12 && liveDepth > 0.98;
                    return cleanSkyDepth || missingSkyColor || staleDarkSky;
                }

                float ausmOfficialSceneDepth(vec2 uv) {
                    float liveDepth = texture2D(depthtex0, uv).r;
                    float preTranslucentDepth = texture2D(depthtex1, uv).r;
                    float postHandDepth = texture2D(depthtex2, uv).r;
                    float sceneDepth = liveDepth;
                    if (preTranslucentDepth > 0.0001 && preTranslucentDepth < 0.999) {
                        sceneDepth = min(sceneDepth, preTranslucentDepth);
                    }
                    if (postHandDepth > 0.0001 && postHandDepth < 0.999) {
                        sceneDepth = min(sceneDepth, postHandDepth);
                    }
                    return sceneDepth;
                }

                void main() {
                    vec2 uv = gl_FragCoord.xy / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    vec4 ausmOfficialSourceColor = texture2D(colortex0, uv);
                    ausmOfficialFinalColor = ausmOfficialSourceColor;
                      ausmOriginalFinalMain();
                     if (ausmSkyboxRepair > 0) {
                         float ausmOfficialSourceMax = max(max(ausmOfficialSourceColor.r, ausmOfficialSourceColor.g), ausmOfficialSourceColor.b);
                        float ausmOfficialFinalMax = max(max(ausmOfficialFinalColor.r, ausmOfficialFinalColor.g), ausmOfficialFinalColor.b);
                        if (ausmOfficialSourceMax > 0.005 && ausmOfficialFinalMax <= 0.001) {
                            ausmOfficialFinalColor.rgb = ausmOfficialSourceColor.rgb;
                         }
                     }
                     float depth = ausmOfficialSceneDepth(uv);
                      bool ausmOfficialSkyPixel = depth > 0.999 && !ausmOfficialHasNearbySceneGeometry(uv);
                    if (ausmUiSkyRepair > 0 && ausmOfficialSkyPixel
                            && ausmOfficialShouldRepairUiSkyPixel(depth, ausmOfficialFinalColor.rgb, uv)) {
                         ausmOfficialFinalColor.rgb = ausmOfficialUiSkyColor(uv);
                     } else if (ausmOfficialShouldRepairSkyPixel(depth, ausmOfficialFinalColor.rgb, uv) && ausmSkyboxRepair > 0) {
                         ausmOfficialFinalColor.rgb = ausmOfficialSkyColor(uv);
                     } else if (depth > 0.999 && ausmSkyboxRepair > 0) {
                         float repairAmount = ausmOfficialSkyRepairAmount(uv, ausmOfficialFinalColor.rgb);
                         ausmOfficialFinalColor.rgb = mix(ausmOfficialFinalColor.rgb, ausmOfficialSkyColor(uv), repairAmount);
                    }
                    gl_FragData[0] = vec4(ausmOfficialFinalColor.rgb, 1.0);
                }
                #endif
                """;
    }
}
