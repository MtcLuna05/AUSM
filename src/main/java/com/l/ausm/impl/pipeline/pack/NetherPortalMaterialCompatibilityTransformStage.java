package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs the legacy Entree translucent-material decision tree where the
 * Nether portal material (30020) is nested below a mat >= 32000 guard and can
 * therefore never reach its special portal shader.
 */
public final class NetherPortalMaterialCompatibilityTransformStage implements ShaderTransformStage {
    static final String MARKER = "AUSM_NETHER_PORTAL_MATERIAL_ROUTE";
    private static final Pattern OUTER_MATERIAL_GUARD = Pattern.compile(
            "else\\s+if\\s*\\(\\s*mat\\s*>=\\s*32000\\s*\\)"
    );
    private static final Pattern WATER_MATERIAL_GUARD = Pattern.compile(
            "if\\s*\\(\\s*mat\\s*<\\s*32004\\s*\\)"
    );
    private static final Pattern PORTAL_MATERIAL_GUARD = Pattern.compile(
            "else\\s+if\\s*\\(\\s*mat\\s*==\\s*30020\\s*\\)"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() != RenderPass.GBUFFERS_WATER
                || !parameters.fragmentShader()
                || source.contains(MARKER)) {
            return source;
        }
        return repairMaterialRoute(source);
    }

    static String repairMaterialRoute(String source) {
        Matcher outerMatcher = OUTER_MATERIAL_GUARD.matcher(source);
        while (outerMatcher.find()) {
            Matcher waterMatcher = WATER_MATERIAL_GUARD.matcher(source);
            if (!waterMatcher.find(outerMatcher.end())) {
                continue;
            }

            Matcher portalMatcher = PORTAL_MATERIAL_GUARD.matcher(source);
            if (!portalMatcher.find(waterMatcher.end())) {
                continue;
            }

            // Keep the match scoped to one material decision tree. Expanded
            // includes can make the water body large, but another outer guard
            // before the portal branch means this is not the broken tree.
            Matcher interveningOuter = OUTER_MATERIAL_GUARD.matcher(source);
            if (interveningOuter.find(outerMatcher.end())
                    && interveningOuter.start() < portalMatcher.start()) {
                continue;
            }

            String repairedOuter = "else if (mat == 30020 || mat >= 32000) /* " + MARKER + " */";
            String repairedWater = "if (mat >= 32000 && mat < 32004)";
            return source.substring(0, outerMatcher.start())
                    + repairedOuter
                    + source.substring(outerMatcher.end(), waterMatcher.start())
                    + repairedWater
                    + source.substring(waterMatcher.end());
        }
        return source;
    }
}
