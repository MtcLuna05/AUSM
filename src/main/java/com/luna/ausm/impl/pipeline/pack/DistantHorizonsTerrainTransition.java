package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import org.lwjgl.opengl.GL20;
import java.util.regex.Matcher;

/** Keeps Complementary's DH coverage, shadow reception and sky depth coherent. */
public final class DistantHorizonsTerrainTransition {
    private DistantHorizonsTerrainTransition() { }

    public static String transform(String source, RenderPass pass, int shaderType) {
        if (shaderType != GL20.GL_FRAGMENT_SHADER || pass == null) return source;
        if (pass == RenderPass.COMPOSITE1) {
            // Vanilla depth remains sky at DH pixels. Preserve the DH distance
            // already reconstructed by composite1 rather than resetting it.
            return source.replace("if (z1 == 1.0) lViewPos1 = rainbowLength;",
                    "if (z1 == 1.0 && texture2D(dhDepthTex1, texCoord).r >= 1.0) lViewPos1 = rainbowLength;")
                    // The rainbow is a sky effect; an opaque DH surface must
                    // occlude it even beyond vanilla's much shorter far plane.
                    .replace("color += GetRainbow(translucentMult, nViewPos, z0, z1, lViewPos, lViewPos1, VdotL, VdotU, dither);",
                            "if (z1lod >= 1.0) color += GetRainbow(translucentMult, nViewPos, z0, z1, lViewPos, lViewPos1, VdotL, VdotU, dither);");
        }
        if (pass == RenderPass.DH_TERRAIN || pass == RenderPass.DH_WATER) {
            // AUSM's far uniform is twice the chunk radius. Finish bringing
            // LODs in before ordinary terrain begins its coverage transition.
            source = source.replace("smoothstep(far * 0.4, far * 0.6, lengthCylinder)",
                    "smoothstep(far * 0.2, far * 0.3, lengthCylinder)");
            // The pack normally excludes DH from shadow-map sampling. Enable
            // its sampler helpers without enabling terrain-only vertex inputs
            // in the ordinary lighting block, then supply receiver visibility.
            source = source.replace("#if SHADOW_QUALITY > -1 && (defined OVERWORLD || defined END) && !defined DH_TERRAIN && !defined DH_WATER && !defined VOXY_PATCH",
                    "#if SHADOW_QUALITY > -1 && (defined OVERWORLD || defined END) && !defined VOXY_PATCH");
            String receiver = """
                    #if SHADOW_QUALITY > -1 && (defined OVERWORLD || defined END)
                        float ausmDhShadowWeight = clamp((shadowDistance * 0.9166667 - lViewPos) / 16.0, 0.0, 1.0);
                        if (ausmDhShadowWeight > 0.0) {
                            float ausmDhBias = 0.12 + 0.0008 * pow(dot(playerPos, playerPos), 0.75);
                            vec3 ausmDhShadowPos = GetShadowPos(playerPos + worldGeoNormal * ausmDhBias);
                            if (all(greaterThan(ausmDhShadowPos, vec3(0.0)))
                                    && all(lessThan(ausmDhShadowPos, vec3(1.0)))) {
                                int ausmDhSamples = SHADOW_QUALITY >= 2 ? (1 << (SHADOW_QUALITY - 1)) : 0;
                                vec3 ausmDhVisibility = GetShadow(ausmDhShadowPos, lmCoordM.y, 0.00075,
                                        ausmDhSamples, subsurfaceMode == 2, playerPos);
                                shadowMult *= mix(vec3(1.0), ausmDhVisibility, ausmDhShadowWeight);
                            }
                        }
                    #endif

                    """;
            return source.replace("    DoLighting(color, shadowMult, playerPos, viewPos, lViewPos,", receiver
                    + "    DoLighting(color, shadowMult, playerPos, viewPos, lViewPos,");
        }
        boolean terrain = switch (pass) {
            case GBUFFERS_TERRAIN, GBUFFERS_TERRAIN_SOLID, GBUFFERS_TERRAIN_CUTOUT,
                    GBUFFERS_TERRAIN_CUTOUT_MIP, GBUFFERS_DAMAGEDBLOCK -> true;
            default -> false;
        };
        if (!terrain) return source;
        String original = "float fog = max(length(playerPos.xz), abs(playerPos.y)) / far;";
        if (!source.contains(original)) return source;
        // Keep the pack's dithering. Only transition when a real DH
        // surface exists at this pixel, so missing LODs cannot expose the sky.
        String replacement = """
                float fog = 1.0;
                if (ausmDhTransitionActive != 0
                        && texture2D(dhDepthTex1, gl_FragCoord.xy / vec2(viewWidth, viewHeight)).r < 0.999999) {
                    float ausmChunkRadius = far * 0.5;
                    // Loaded chunks extend vertically; flying above them must
                    // not replace the terrain directly underneath the camera.
                    float ausmDistance = length(playerPos.xz);
                    // A broad screen-door blend leaves a large visible band of
                    // Bayer noise where full terrain hands off to DH LODs.
                    // Keep dithering confined to the last part of chunk coverage.
                    fog = 1.0 - smoothstep(ausmChunkRadius * 0.86, ausmChunkRadius * 0.98, ausmDistance);
                }
                """;
        int declaration = source.indexOf("#define FRAGMENT_SHADER");
        if (declaration < 0) return source;
        int end = source.indexOf('\n', declaration);
        if (end < 0) return source;
        // The pack otherwise enables its coverage block only with TAA. Keep
        // the distance handoff available with static dithering when TAA is off.
        source = source.substring(0, end + 1)
                + "uniform int ausmDhTransitionActive;\n#define DH_BLENDING\n"
                + source.substring(end + 1);
        String block = java.util.regex.Pattern.quote(original)
                + "\\s+" + java.util.regex.Pattern.quote("fog = pow2(pow2(pow2(pow2(fog))));")
                + "\\s+" + java.util.regex.Pattern.quote("fog = exp(-3.0 * fog);");
        return source.replaceAll(block, Matcher.quoteReplacement(replacement))
                .replace("if (newDither > fog) discard;", "if (newDither >= fog) discard;");
    }
}
