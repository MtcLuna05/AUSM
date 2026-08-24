package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives interior, double-sided terrain cards a tiny physical thickness in the
 * shader. Flowers, thin bars, and scaffolding may author front and back faces
 * on the same plane; moving each face along its own normal prevents equal-depth
 * rasterization while leaving ordinary block boundary faces at 0.5 untouched.
 * Wave-displaced foliage needs the same separation even when its card lies on
 * a block boundary. The offset scales with view distance so its depth-space
 * effect survives distant precision loss without inflating nearby geometry.
 */
public final class ThinQuadFaceSeparationTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_THIN_QUAD_FACE_SEPARATION";
    private static final Pattern AT_MID_BLOCK_DECLARATION = Pattern.compile(
            "(?m)^\\s*(?:attribute|in)\\s+\\w+\\s+at_midBlock\\s*;"
    );
    private static final Pattern ATTRIBUTE_ANCHOR = Pattern.compile(
            "(?m)^(\\s*attribute\\s+vec4\\s+(?:mc_midTexCoord|mc_Entity)\\s*;)"
    );
    private static final Pattern VERTEX_POSITION_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)vertexPos\\s*=\\s*position\\.xyz\\s*;"
    );
    private static final Pattern FINAL_POSITION_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)gl_Position\\s*=\\s*gl_ProjectionMatrix\\s*\\*\\s*gbufferModelView\\s*\\*\\s*position\\s*;"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.vertexShader() || !isTerrainPass(parameters.pass())) {
            return source;
        }
        return injectFaceSeparation(source);
    }

    static String injectFaceSeparation(String source) {
        if (source.contains(MARKER)) {
            return source;
        }

        Matcher vertexPositionMatcher = VERTEX_POSITION_ASSIGNMENT.matcher(source);
        if (!vertexPositionMatcher.find()) {
            return source;
        }

        String transformed = ensureMidBlockDeclaration(source);
        if (!AT_MID_BLOCK_DECLARATION.matcher(transformed).find()) {
            return source;
        }
        vertexPositionMatcher = VERTEX_POSITION_ASSIGNMENT.matcher(transformed);
        if (!vertexPositionMatcher.find()) {
            return source;
        }

        Matcher finalPositionMatcher = FINAL_POSITION_ASSIGNMENT.matcher(transformed);
        Matcher insertionMatcher = finalPositionMatcher.find() ? finalPositionMatcher : vertexPositionMatcher;
        String indent = insertionMatcher.group(1);
        String separation = indent + "// " + MARKER + "\n"
                + indent + "float ausmThinQuadNormalLength2 = dot(gl_Normal, gl_Normal);\n"
                + indent + "if (ausmThinQuadNormalLength2 > 0.25) {\n"
                + indent + "    vec3 ausmThinQuadNormal = gl_Normal * inversesqrt(ausmThinQuadNormalLength2);\n"
                + indent + "    float ausmThinQuadCenterDistance = abs(dot(at_midBlock.xyz / 64.0, ausmThinQuadNormal));\n"
                + indent + "    int ausmThinQuadMaterial = int(mc_Entity.x + 0.5);\n"
                + indent + "    bool ausmWaveFoliageSurface = (ausmThinQuadMaterial >= 10000 && ausmThinQuadMaterial <= 10011)\n"
                + indent + "            || ausmThinQuadMaterial == 10013 || ausmThinQuadMaterial == 10923\n"
                + indent + "            || ausmThinQuadMaterial == 10021 || ausmThinQuadMaterial == 10023;\n"
                + indent + "    if (ausmThinQuadCenterDistance < 0.4995 || ausmWaveFoliageSurface) {\n"
                + indent + "        float ausmThinQuadViewDistance = length((gl_ModelViewMatrix * gl_Vertex).xyz);\n"
                + indent + "        float ausmThinQuadOffset = min(0.0125, 0.0015 + ausmThinQuadViewDistance * ausmThinQuadViewDistance * 0.0000015);\n"
                + indent + "        position.xyz += ausmThinQuadNormal * ausmThinQuadOffset;\n"
                + indent + "    }\n"
                + indent + "}\n";
        return transformed.substring(0, insertionMatcher.start())
                + separation
                + transformed.substring(insertionMatcher.start());
    }

    private static String ensureMidBlockDeclaration(String source) {
        if (AT_MID_BLOCK_DECLARATION.matcher(source).find()) {
            return source;
        }
        Matcher anchorMatcher = ATTRIBUTE_ANCHOR.matcher(source);
        if (!anchorMatcher.find()) {
            return source;
        }
        return anchorMatcher.replaceFirst(Matcher.quoteReplacement(
                anchorMatcher.group(1) + "\nattribute vec4 at_midBlock;"
        ));
    }

    private static boolean isTerrainPass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
    }
}
