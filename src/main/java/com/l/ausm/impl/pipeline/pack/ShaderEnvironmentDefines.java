package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderEnvironmentDefines {
    private ShaderEnvironmentDefines() {
    }

    public static Map<String, String> defineMap(ShaderOptions options) {
        Map<String, String> defines = baseDefineMap();
        for (ShaderOption option : options.all().values()) {
            if (option.toggle()) {
                if (option.asBoolean()) {
                    defines.put(option.name(), "1");
                } else {
                    defines.remove(option.name());
                }
            } else {
                defines.put(option.name(), option.value());
            }
        }
        return defines;
    }

    public static Map<String, String> baseDefineMap() {
        return baseDefineMap(120);
    }

    public static Map<String, String> baseDefineMap(int glslVersion) {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("MC_VERSION", "11202");
        defines.put("MC_GL_VERSION", "320");
        defines.put("MC_GLSL_VERSION", Integer.toString(Math.max(120, glslVersion)));
        defines.put("MC_RENDER_QUALITY", "1.0");
        defines.put("MC_SHADOW_QUALITY", "1.0");
        defines.put("MC_HAND_DEPTH", "1.0");
        defines.put("IS_IRIS", "1");
        defines.put("IRIS_VERSION", "10902");
        addSupportedFeatureDefines(defines);

        switch (ShaderDimensionContext.currentDimensionId()) {
            case -1 -> defines.put("NETHER", "1");
            case 1 -> defines.put("THE_END", "1");
            default -> defines.put("OVERWORLD", "1");
        }
        return defines;
    }

    public static String shaderSourceDefines() {
        return shaderSourceDefines(ShaderOptions.empty());
    }

    public static String shaderSourceDefines(ShaderOptions options) {
        return shaderSourceDefines(options, 120);
    }

    public static String shaderSourceDefines(ShaderOptions options, int glslVersion) {
        StringBuilder defines = new StringBuilder();
        baseDefineMap(glslVersion).forEach((name, value) -> {
            appendDefine(defines, name, value);
        });
        for (ShaderOption option : options.all().values()) {
            if (!option.changed()) {
                continue;
            }
            if (option.toggle()) {
                if (option.asBoolean()) {
                    appendDefine(defines, option.name(), "1");
                }
            } else {
                appendDefine(defines, option.name(), option.value());
            }
        }
        return defines.toString();
    }

    private static void appendDefine(StringBuilder defines, String name, String value) {
        defines.append("#define ").append(name);
        if (value != null && !value.isBlank()) {
            defines.append(' ').append(value);
        }
        defines.append('\n');
    }

    public static boolean isDefined(String symbol, ShaderOptions options) {
        return defineMap(options).containsKey(symbol);
    }

    public static double numericValue(String symbol, ShaderOptions options) {
        Map<String, String> defines = defineMap(options);
        String value = defines.get(symbol);
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        return numericValue(value, defines);
    }

    public static double numericValue(String value, Map<String, String> defines) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return 1.0;
        }
        try {
            String token = trimmed.split("\\s+", 2)[0];
            if (token.startsWith("0x") || token.startsWith("0X") || token.startsWith("-0x") || token.startsWith("-0X")) {
                return Long.decode(token);
            }
            return Double.parseDouble(token);
        } catch (NumberFormatException ignored) {
            return switch (trimmed.toLowerCase()) {
                case "true", "on" -> 1.0;
                case "false", "off" -> 0.0;
                default -> defines.containsKey(trimmed) ? numericValue(defines.get(trimmed), defines) : 0.0;
            };
        }
    }

    private static void addSupportedFeatureDefines(Map<String, String> defines) {
        for (ShaderFeatureFlag flag : ShaderFeatureFlag.values()) {
            if (flag == ShaderFeatureFlag.UNKNOWN || !flag.implemented() || !isFeatureAvailable(flag)) {
                continue;
            }
            defines.put("IRIS_FEATURE_" + flag.name(), "1");
        }
    }

    private static boolean isFeatureAvailable(ShaderFeatureFlag flag) {
        try {
            return flag.hardwareSupported();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
