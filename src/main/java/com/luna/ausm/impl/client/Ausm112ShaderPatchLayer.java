package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reusable orchestration for AUSM's 1.12.2 visual and optimization patches.
 * Shader-family knowledge lives in adapters registered here.
 */
final class Ausm112ShaderPatchLayer {
    private static final List<ShaderPackFamilyAdapter> ADAPTERS = List.of(
            ComplementaryShaderPackAdapter.INSTANCE,
            BslShaderPackAdapter.INSTANCE,
            MakeupUltraFastShaderPackAdapter.INSTANCE
    );

    private Ausm112ShaderPatchLayer() {
    }

    static boolean supportsSource(String sourcePackName) {
        return ADAPTERS.stream().anyMatch(adapter -> adapter.supportsSource(sourcePackName));
    }

    static String familyId(String sourcePackName) throws IOException {
        return sourceAdapter(sourcePackName).familyId();
    }

    static void apply(Path packRoot, String sourcePackName) throws IOException {
        ShaderPackFamilyAdapter adapter = sourceAdapter(sourcePackName);
        ShaderPackPatchContext context = new ShaderPackPatchContext(packRoot, sourcePackName);
        if (!adapter.recognizes(context)) {
            throw new IOException("Shaderpack '" + sourcePackName + "' does not match the "
                    + adapter.familyId() + " adapter structure");
        }
        adapter.applyVisualPatches(context);
        adapter.applyOptimizationPatches(context);
        MainMod.LOGGER.info("[AUSM112] Applied reusable visual and optimization layers through the '{}' adapter",
                adapter.familyId());
    }

    private static ShaderPackFamilyAdapter sourceAdapter(String sourcePackName) throws IOException {
        return ADAPTERS.stream()
                .filter(adapter -> adapter.supportsSource(sourcePackName))
                .findFirst()
                .orElseThrow(() -> new IOException("No AUSM 1.12.2 shader-family adapter for '"
                        + sourcePackName + "'"));
    }
}
