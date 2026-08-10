package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives BOP ground leaf piles thin-card lighting without inheriting
 * Complementary's animated lily-pad geometry.
 */
public final class LeafPileCompatibilityTransformStage implements ShaderTransformStage {
    private static final String TERRAIN_MARKER = "AUSM_BOP_LEAF_PILE_TERRAIN";
    private static final String SHADOW_MARKER = "AUSM_BOP_LEAF_PILE_SHADOW";
    private static final String WAVE_MARKER = "AUSM_THIN_CARD_NO_WATER_BOB";
    private static final String DEPTH_LIFT_MARKER = "AUSM_BOP_LEAF_PILE_FACE_SEPARATION";
    private static final Pattern LIGHTING_CALL = Pattern.compile(
            "(?m)^(\\s*)DoLighting\\s*\\(\\s*color\\s*,"
    );
    private static final Pattern MAIN_OPEN = Pattern.compile(
            "(?m)^(\\s*void\\s+main\\s*\\(\\s*\\)\\s*\\{\\s*)$"
    );
    private static final Pattern WAVE_CALL = Pattern.compile(
            "DoWave\\s*\\(\\s*([^,;\\r\\n]+)\\s*,\\s*mat\\s*\\)\\s*;"
    );
    private static final Pattern VERTEX_POSITION_ASSIGNMENT = Pattern.compile(
            "(?m)^(\\s*)vertexPos\\s*=\\s*position\\.xyz\\s*;"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.vertexShader() && isTerrainPass(parameters.pass())) {
            return liftLeafPileCard(suppressThinCardWaterBob(source));
        }
        if (!parameters.fragmentShader()) {
            return source;
        }

        if (parameters.pass().stage() == ProgramStage.SHADOW) {
            return suppressShadowCard(source);
        }
        if (!isTerrainPass(parameters.pass())) {
            return source;
        }
        return applyTerrainLighting(source);
    }

    private static String liftLeafPileCard(String source) {
        if (source.contains(DEPTH_LIFT_MARKER)) {
            return source;
        }
        Matcher matcher = VERTEX_POSITION_ASSIGNMENT.matcher(source);
        if (!matcher.find()) {
            return source;
        }
        String indent = matcher.group(1);
        String replacement = indent + "// " + DEPTH_LIFT_MARKER + "\n"
                + indent + "if (mat == " + ShaderBlockIdMap.BOP_LEAF_PILE_MATERIAL_ID + ") {\n"
                + indent + "    // BOP's flat_on_floor model authors its up and down faces at the exact same Y.\n"
                + indent + "    // Lift the card clear of the support block, then pull only the lower face away.\n"
                + indent + "    position.y += 0.00390625;\n"
                + indent + "    if (dot(normal, upVec) < -0.5) position.y -= 0.0005;\n"
                + indent + "}\n"
                + matcher.group(0);
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String applyTerrainLighting(String source) {
        if (source.contains(TERRAIN_MARKER)) {
            return source;
        }
        Matcher matcher = LIGHTING_CALL.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        String indent = matcher.group(1);
        String compatibility = indent + "// " + TERRAIN_MARKER + "\n"
                + indent + "if (mat == 10489 || mat == " + ShaderBlockIdMap.BOP_LEAF_PILE_MATERIAL_ID + ") {\n"
                + indent + "    normalM = upVec;\n"
                + indent + "    geoNormal = upVec;\n"
                + indent + "    worldGeoNormal = normalize(ViewToPlayer(upVec * 10000.0));\n"
                + indent + "    noSmoothLighting = true;\n"
                + indent + "    noDirectionalShading = true;\n"
                + indent + "    noVanillaAO = true;\n"
                + indent + "    subsurfaceMode = 4;\n"
                + indent + "    emission = 0.0;\n"
                + indent + "}\n\n";
        return source.substring(0, matcher.start()) + compatibility + source.substring(matcher.start());
    }

    private static String suppressShadowCard(String source) {
        if (source.contains(SHADOW_MARKER) || !source.matches("(?s).*\\bmat\\b.*")) {
            return source;
        }
        Matcher matcher = MAIN_OPEN.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        String replacement = matcher.group(1)
                + "\n    // " + SHADOW_MARKER
                + "\n    if (mat == 10489 || mat == " + ShaderBlockIdMap.BOP_LEAF_PILE_MATERIAL_ID + ") discard;";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static String suppressThinCardWaterBob(String source) {
        if (source.contains(WAVE_MARKER)) {
            return source;
        }
        Matcher matcher = WAVE_CALL.matcher(source);
        if (!matcher.find()) {
            return source;
        }

        String replacement = "// " + WAVE_MARKER + "\n"
                + "        if (mat != 10489 && mat != " + ShaderBlockIdMap.BOP_LEAF_PILE_MATERIAL_ID + ") "
                + "DoWave(" + matcher.group(1).trim() + ", mat);";
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static boolean isTerrainPass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
    }
}
