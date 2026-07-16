package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import net.minecraftforge.fml.common.Loader;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderEnvironmentDefines {
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;

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
        return baseDefineMap(glslVersion, null);
    }

    public static Map<String, String> baseDefineMap(int glslVersion, ShaderPackDirectives directives) {
        Map<String, String> defines = new LinkedHashMap<>();
        defines.put("MC_VERSION", "11202");
        defines.put("MC_GL_VERSION", "320");
        defines.put("MC_GLSL_VERSION", Integer.toString(Math.max(120, glslVersion)));
        defines.put("MC_RENDER_QUALITY", "1.0");
        defines.put("MC_SHADOW_QUALITY", "1.0");
        defines.put("MC_HAND_DEPTH", "1.0");
        defines.put("IS_IRIS", "1");
        defines.put("IRIS_VERSION", "10902");
        addDistantHorizonsDefines(defines);
        addSupportedFeatureDefines(defines, directives);

        int dimensionId = ShaderDimensionContext.currentDimensionId();
        if (dimensionId == SIMPLE_VOID_WORLD_DIMENSION_ID) {
            defines.put("AUSM_SIMPLE_VOID_WORLD", "1");
        }

        switch (dimensionId) {
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
        return shaderSourceDefines(options, glslVersion, null);
    }

    public static String shaderSourceDefines(ShaderOptions options, int glslVersion, ShaderPackDirectives directives) {
        StringBuilder defines = new StringBuilder();
        baseDefineMap(glslVersion, directives).forEach((name, value) -> {
            appendDefine(defines, name, value);
        });
        for (ShaderOption option : options.all().values()) {
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

    public static boolean distantHorizonsInstalled() {
        try {
            return Loader.isModLoaded("distanthorizons") || Loader.isModLoaded("DistantHorizons");
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
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

    private static void addSupportedFeatureDefines(Map<String, String> defines, ShaderPackDirectives directives) {
        for (ShaderFeatureFlag flag : ShaderFeatureFlag.values()) {
            if (flag == ShaderFeatureFlag.UNKNOWN
                    || !flag.implemented()
                    || !isFeatureAvailable(flag)
                    || !isFeatureEnabledForPack(flag, directives)) {
                continue;
            }
            defines.put("IRIS_FEATURE_" + flag.name(), "1");
        }
    }

    private static void addDistantHorizonsDefines(Map<String, String> defines) {
        boolean installed = distantHorizonsInstalled();
        defines.put("DISTANT_HORIZON", installed ? "1" : "0");
        if (installed) {
            // Shader packs commonly gate DH declarations with #ifdef DISTANT_HORIZONS.
            defines.put("DISTANT_HORIZONS", "1");
        }
    }

    private static boolean isFeatureAvailable(ShaderFeatureFlag flag) {
        try {
            return flag.hardwareSupported();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isFeatureEnabledForPack(ShaderFeatureFlag flag, ShaderPackDirectives directives) {
        if (directives == null || directives.capabilities() == null) {
            return true;
        }
        String featureName = flag.name().toLowerCase(java.util.Locale.ROOT);
        if (directives.features().requires(featureName) || directives.features().optional(featureName)) {
            return true;
        }
        ShaderPipelineCapabilities capabilities = directives.capabilities();
        return switch (flag) {
            case CUSTOM_IMAGES -> capabilities.images();
            case COMPUTE_SHADERS -> capabilities.compute();
            case SSBO -> capabilities.storageBuffers();
            case PER_BUFFER_BLENDING -> capabilities.perBufferBlending();
            case TESSELLATION_SHADERS -> capabilities.tessellation();
            default -> true;
        };
    }
}
