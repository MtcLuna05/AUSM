package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

/** Preserves native station skies without applying terrestrial light shafts in space. */
public final class AdvancedRocketrySkyTransform {
    private AdvancedRocketrySkyTransform() { }

    public static int activeSpaceWorld() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        return world != null && MinecraftReflectionCompat.worldProvider(world) != null
                && "zmaster587.advancedRocketry.world.provider.WorldProviderSpace".equals(
                MinecraftReflectionCompat.worldProvider(world).getClass().getName()) ? 1 : 0;
    }

    public static String apply(String source, ShaderTransformParameters parameters) {
        if (!parameters.fragmentShader()) {
            return source;
        }
        if (source.contains("GetVolumetricLight") && !source.contains("AUSM_AR_SPACE_LIGHTSHAFTS")) {
            // Declarations must follow extension directives. Put this beside
            // the function instead of immediately after the GLSL version.
            String declaration = source.contains("uniform int ausmAdvancedRocketrySky;")
                    ? "" : "uniform int ausmAdvancedRocketrySky;\n";
            String patched = source.replaceFirst("(vec4\\s+GetVolumetricLight\\s*\\([^)]*\\)\\s*\\{)",
                    declaration + "$1\n    // AUSM_AR_SPACE_LIGHTSHAFTS\n"
                            + "    if (ausmAdvancedRocketrySky != 0) return vec4(0.0);\n");
            if (!patched.equals(source)) {
                source = patched;
            }
        }
        if (parameters.pass() != RenderPass.GBUFFERS_SKYTEXTURED
                || !source.contains("Old mc custom skyboxes are weirdly broken")
                || source.contains("uniform int ausmAdvancedRocketrySky;")) {
            return source;
        }
        // Keep the pack's celestial processing elsewhere. AR draws its planet,
        // atmosphere and celestial textures inside the CUSTOM_SKY phase.
        return source.replaceFirst("void\\s+main\\s*\\(\\s*\\)\\s*\\{",
                "uniform int ausmAdvancedRocketrySky;\nvoid main() {\n"
                        + "    if (ausmAdvancedRocketrySky != 0 && renderStage == MC_RENDER_STAGE_CUSTOM_SKY) {\n"
                        + "        gl_FragData[0] = texture2D(tex, texCoord) * glColor;\n"
                        + "        return;\n"
                        + "    }\n");
    }
}
