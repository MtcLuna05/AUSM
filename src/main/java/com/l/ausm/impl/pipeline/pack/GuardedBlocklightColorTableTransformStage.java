package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.regex.Pattern;

/**
 * Some packs reference RGB blocklight/tint lookup tables outside the same
 * preprocessor guard that declares them. Keep those pure constant tables
 * available without forcing the guarded lighting feature itself on.
 */
public final class GuardedBlocklightColorTableTransformStage implements ShaderTransformStage {
    private static final Pattern GUARDED_RGB_TABLES = Pattern.compile(
            "(?m)^\\s*#if\\s+defined\\s+MULTICOLORED_BLOCKLIGHT\\s*\\|\\|\\s*defined\\s+MCBL_SS\\s*\\R(?=\\s*vec3\\s*\\[\\s*50\\s*]\\s+lightColorsRGB\\b)"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!source.contains("lightColorsRGB[colorIndex]")
                || !source.contains("tintColorsRGB[colorIndex]")
                || !source.contains("vec3[50] lightColorsRGB")
                || !source.contains("vec3[25] tintColorsRGB")) {
            return source;
        }

        String transformed = GUARDED_RGB_TABLES.matcher(source).replaceFirst(
                "#if 1 // AUSM: guarded blocklight color tables are referenced unconditionally\n"
        );
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Unguarded referenced blocklight color tables");
        }
        return transformed;
    }
}
