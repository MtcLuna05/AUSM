package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.regex.Pattern;

/**
 * BSL 10.x references its RGB blocklight/tint color tables from shadowcomp even
 * when the table declaration guard is false in some option combinations. Iris'
 * preprocessor path still leaves those declarations available; keep the tables
 * compiled without forcing the rest of the multicolored blocklight feature on.
 */
public final class BslBlocklightColorTableTransformStage implements ShaderTransformStage {
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
                "#if 1 // AUSM: BSL shadowcomp color tables are referenced unconditionally\n"
        );
        if (!transformed.equals(source)) {
            MainMod.LOGGER.debug("[ShaderTransform] Unguarded BSL shadowcomp blocklight color tables");
        }
        return transformed;
    }
}
