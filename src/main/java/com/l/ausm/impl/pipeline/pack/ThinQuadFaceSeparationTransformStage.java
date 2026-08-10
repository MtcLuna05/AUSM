package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives interior, double-sided terrain cards a tiny physical thickness in the
 * shader. Flowers, thin bars, and scaffolding may author front and back faces
 * on the same plane; moving each face along its own normal prevents equal-depth
 * rasterization while leaving ordinary block boundary faces at 0.5 untouched.
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

        Matcher positionMatcher = VERTEX_POSITION_ASSIGNMENT.matcher(source);
        if (!positionMatcher.find()) {
            return source;
        }

        String transformed = ensureMidBlockDeclaration(source);
        if (!AT_MID_BLOCK_DECLARATION.matcher(transformed).find()) {
            return source;
        }
        positionMatcher = VERTEX_POSITION_ASSIGNMENT.matcher(transformed);
        if (!positionMatcher.find()) {
            return source;
        }

        String indent = positionMatcher.group(1);
        String separation = indent + "// " + MARKER + "\n"
                + indent + "float ausmThinQuadNormalLength2 = dot(gl_Normal, gl_Normal);\n"
                + indent + "if (ausmThinQuadNormalLength2 > 0.25) {\n"
                + indent + "    vec3 ausmThinQuadNormal = gl_Normal * inversesqrt(ausmThinQuadNormalLength2);\n"
                + indent + "    float ausmThinQuadCenterDistance = abs(dot(at_midBlock.xyz / 64.0, ausmThinQuadNormal));\n"
                + indent + "    if (ausmThinQuadCenterDistance < 0.4995) position.xyz += ausmThinQuadNormal * 0.0005;\n"
                + indent + "}\n";
        return transformed.substring(0, positionMatcher.start())
                + separation
                + transformed.substring(positionMatcher.start());
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
