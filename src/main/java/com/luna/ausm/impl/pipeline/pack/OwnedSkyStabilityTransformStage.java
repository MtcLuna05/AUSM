package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps AUSM's shader-owned procedural sky stable under camera motion.
 */
public final class OwnedSkyStabilityTransformStage implements ShaderTransformStage {
    private static final String MARKER = "AUSM_OWNED_SKY_STABILITY_TRANSFORM";
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^\\s*#version\\b.*\\R");
    private static final Pattern OWNED_STAR_FUNCTION = Pattern.compile(
            "float\\s+AusmOwnedSkyStars\\s*\\(\\s*vec3\\s+ray\\s*,\\s*float\\s+scale\\s*,\\s*float\\s+threshold\\s*\\)\\s*\\{.*?\\n\\}",
            Pattern.DOTALL
    );
    private static final Pattern OWNED_SKY_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)(color\\.rgb\\s*=\\s*AusmOwnedSkyColor\\([^\\n;]+;)\\s*$"
    );
    private static final Pattern OWNED_CELESTIAL_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)(color\\.rgb\\s*=\\s*AusmApplyComplementaryCelestials\\([^\\n;]+;)\\s*$"
    );

    private static final String FILTERED_STAR_FUNCTION = """
            float AusmOwnedSkyStars(vec3 ray, float scale, float threshold) {
                vec3 grid = ray * scale;
                vec3 cell = floor(grid);
                vec3 local = fract(grid) - 0.5;
                float radius = length(local);
                vec3 pixelFootprint = fwidth(grid);
                float footprint = max(max(pixelFootprint.x, pixelFootprint.y), pixelFootprint.z);
                float filterWidth = clamp(footprint * 0.35, 0.015, 0.16);
                float core = 1.0 - smoothstep(0.10 - filterWidth, 0.20 + filterWidth, radius);
                float seed = AusmOwnedSkyHash(cell);
                float selected = smoothstep(threshold - 0.008, 1.0, seed);
                float resolved = 1.0 - smoothstep(0.22, 0.62, footprint);
                return core * selected * resolved;
            }
            """;

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        String transformed = source;
        if (parameters.fragmentShader()
                && parameters.pass() == RenderPass.GBUFFERS_SKYBASIC
                && transformed.contains("float VdotU = dot(nViewPos, upVec);")
                && transformed.contains("color.rgb = GetSky(VdotU, VdotS, dither, true, false);")
                && !transformed.contains("AUSM_LOWER_SKY_HORIZON_CLAMP")) {
            transformed = transformed.replace(
                    "float VdotU = dot(nViewPos, upVec);",
                    "float VdotU = dot(nViewPos, upVec); // AUSM_LOWER_SKY_HORIZON_CLAMP"
            ).replace(
                    "color.rgb = GetSky(VdotU, VdotS, dither, true, false);",
                    "color.rgb = GetSky(max(VdotU, 0.0), VdotS, dither, true, false);"
            );
        }
        if (!parameters.fragmentShader()
                || parameters.pass() != RenderPass.GBUFFERS_SKYBASIC
                || transformed.contains(MARKER)
                || !transformed.contains("AusmOwnedSkyStars")) {
            return transformed;
        }

        transformed = OWNED_STAR_FUNCTION.matcher(transformed)
                .replaceFirst(Matcher.quoteReplacement(FILTERED_STAR_FUNCTION.stripTrailing()));
        if (!transformed.contains("if (ausmSimpleVoidWorld > 0)")) {
            transformed = OWNED_SKY_ASSIGNMENT.matcher(transformed)
                    .replaceAll("$1if (ausmSimpleVoidWorld > 0) $2");
            transformed = OWNED_CELESTIAL_ASSIGNMENT.matcher(transformed)
                    .replaceAll("$1if (ausmSimpleVoidWorld > 0) $2");
        }
        return insertPreamble(transformed);
    }

    private static String insertPreamble(String source) {
        String preamble = "// " + MARKER + "\n"
                + (source.contains("uniform int ausmSimpleVoidWorld;")
                ? ""
                : "uniform int ausmSimpleVoidWorld;\n");
        Matcher version = VERSION_LINE.matcher(source);
        if (version.find()) {
            return source.substring(0, version.end()) + preamble + source.substring(version.end());
        }
        return preamble + source;
    }
}
