package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.impl.MainMod;
import java.util.Locale;

record CustomUniformKey(boolean uniform, String type, String name) {
    static CustomUniformKey parse(String key) {
        String prefix;
        boolean uniform;
        if (key.startsWith("uniform.")) {
            prefix = "uniform.";
            uniform = true;
        } else if (key.startsWith("variable.")) {
            prefix = "variable.";
            uniform = false;
        } else {
            return null;
        }

        String suffix = key.substring(prefix.length());
        int separator = suffix.indexOf('.');
        if (separator <= 0 || separator >= suffix.length() - 1) {
            MainMod.LOGGER.warn("[CustomUniforms] Ignoring malformed custom uniform key: {}", key);
            return null;
        }
        return new CustomUniformKey(
                uniform,
                suffix.substring(0, separator).toLowerCase(Locale.ROOT),
                suffix.substring(separator + 1));
    }
}
