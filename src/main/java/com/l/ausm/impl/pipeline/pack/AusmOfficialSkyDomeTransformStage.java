package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

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
        transformed = insertPreamble(transformed);
        return transformed + "\n" + officialWrapperSource();
    }

    public static String builtinFinalFragmentSource() {
        return """
                #version 120

                #define AUSM_OFFICIAL_SKY_DOME_TRANSFORM

                uniform sampler2D colortex0;
                uniform sampler2D depthtex0;
                uniform vec3 skyColor;
                uniform float rainFactor;
                uniform int worldTime;
                uniform int hasSkylight;
                uniform int ausmSimpleVoidWorld;
                uniform float viewWidth;
                uniform float viewHeight;

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

                float ausmOfficialNightFactor() {
                    float timeAngle = fract(float(worldTime) / 24000.0);
                    return max(sin(timeAngle * -6.28318530718), 0.0);
                }

                vec3 ausmOfficialSkyColor(vec2 uv) {
                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;
                    float dayFactor = 1.0 - ausmOfficial01(ausmOfficialNightFactor());

                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);

                    vec3 nightSky = max(skyColor, vec3(0.004, 0.006, 0.014));
                    vec3 nightBase = mix(vec3(0.0), nightSky, 0.75);

                    vec3 topColor = mix(nightBase, dayTop, dayFactor);
                    vec3 lowerColor = mix(nightBase, dayLower, dayFactor);

                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    vec3 result = mix(lowerColor, topColor, ausmOfficialSmoother(band));

                    float rainAmount = ausmOfficial01(rainFactor);
                    vec3 rainyDome = mix(lowerColor, topColor, 0.48);
                    result = mix(result, rainyDome, ausmOfficial01(rainAmount * 0.75));

                    vec3 rainColor = min(result, vec3(0.17, 0.185, 0.235));
                    return mix(result, rainColor, ausmOfficial01(rainAmount * 0.50));
                }

                void main() {
                    vec2 uv = gl_FragCoord.xy / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    vec4 color = texture2D(colortex0, uv);
                    float depth = texture2D(depthtex0, uv).r;
                    if (ausmSimpleVoidWorld > 0 && hasSkylight > 0 && depth > 0.999) {
                        color.rgb = ausmOfficialSkyColor(uv);
                    }
                    gl_FragData[0] = vec4(color.rgb, 1.0);
                }
                """;
    }

    private static String insertPreamble(String source) {
        StringBuilder preamble = new StringBuilder();
        appendUniformIfMissing(preamble, source, "sampler2D", "colortex0");
        appendUniformIfMissing(preamble, source, "sampler2D", "depthtex0");
        appendUniformIfMissing(preamble, source, "vec3", "skyColor");
        appendUniformIfMissing(preamble, source, "float", "rainFactor");
        appendUniformIfMissing(preamble, source, "int", "worldTime");
        appendUniformIfMissing(preamble, source, "int", "hasSkylight");
        appendUniformIfMissing(preamble, source, "int", "ausmSimpleVoidWorld");
        appendUniformIfMissing(preamble, source, "float", "viewWidth");
        appendUniformIfMissing(preamble, source, "float", "viewHeight");
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

                float ausmOfficialNightFactor() {
                    float timeAngle = fract(float(worldTime) / 24000.0);
                    return max(sin(timeAngle * -6.28318530718), 0.0);
                }

                vec3 ausmOfficialSkyColor(vec2 uv) {
                    float horizonY = 0.50;
                    float softness = 0.70;
                    float lowerMix = 0.08;
                    float dayFactor = 1.0 - ausmOfficial01(ausmOfficialNightFactor());

                    vec3 dayTop = max(skyColor, vec3(0.45, 0.62, 0.86));
                    vec3 dayHorizon = mix(ausmOfficialDesaturate(dayTop, 0.35), vec3(0.84, 0.90, 1.0), 0.62);
                    vec3 dayLower = mix(dayHorizon, dayTop, lowerMix);

                    vec3 nightSky = max(skyColor, vec3(0.004, 0.006, 0.014));
                    vec3 nightBase = mix(vec3(0.0), nightSky, 0.75);

                    vec3 topColor = mix(nightBase, dayTop, dayFactor);
                    vec3 lowerColor = mix(nightBase, dayLower, dayFactor);

                    float band = (uv.y - (horizonY - softness)) / (softness * 2.0);
                    vec3 result = mix(lowerColor, topColor, ausmOfficialSmoother(band));

                    float rainAmount = ausmOfficial01(rainFactor);
                    vec3 rainyDome = mix(lowerColor, topColor, 0.48);
                    result = mix(result, rainyDome, ausmOfficial01(rainAmount * 0.75));

                    vec3 rainColor = min(result, vec3(0.17, 0.185, 0.235));
                    return mix(result, rainColor, ausmOfficial01(rainAmount * 0.50));
                }

                void main() {
                    vec2 uv = gl_FragCoord.xy / max(vec2(viewWidth, viewHeight), vec2(1.0));
                    ausmOfficialFinalColor = texture2D(colortex0, uv);
                    ausmOriginalFinalMain();
                    float depth = texture2D(depthtex0, uv).r;
                    if (ausmSimpleVoidWorld > 0 && hasSkylight > 0 && depth > 0.999) {
                        ausmOfficialFinalColor.rgb = ausmOfficialSkyColor(uv);
                    }
                    gl_FragData[0] = vec4(ausmOfficialFinalColor.rgb, 1.0);
                }
                #endif
                """;
    }
}
