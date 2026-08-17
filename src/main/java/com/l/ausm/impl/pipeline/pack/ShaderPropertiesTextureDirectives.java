package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.l.ausm.api.pipeline.pack.ShaderImageTarget;
import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.pack.ShaderRawTextureDirective;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderProgramArrayKey;
import com.l.ausm.impl.MainMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract class ShaderPropertiesTextureDirectives extends ShaderPropertiesParsing {
    protected static ShaderRawTextureDirective parseRawTextureDirective(
            ShaderPack pack,
            ShaderPackLayout layout,
            String samplerName,
            String replacementSamplerName,
            String value
    ) {
        if (value == null) {
            return null;
        }

        String[] parts = value.trim().split("\\s+");
        if (parts.length <= 1) {
            return null;
        }

        ShaderImageTarget target = switch (parts.length) {
            case 6 -> ShaderImageTarget.TEXTURE_1D;
            case 7 -> ShaderProperties.parseRawTextureTarget(parts[1], ShaderImageTarget.TEXTURE_2D);
            case 8 -> ShaderImageTarget.TEXTURE_3D;
            default -> null;
        };
        if (target == null) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed raw texture directive for sampler '{}': {}", samplerName, value);
            return null;
        }

        String resourcePath = layout.normalizeTexturePath(parts[0]);
        if (resourcePath == null) {
            return null;
        }

        try {
            ShaderProperties.TextureFiltering filtering = ShaderProperties.textureFiltering(pack, resourcePath, true, true);
            return switch (target) {
                case TEXTURE_1D -> new ShaderRawTextureDirective(
                        samplerName,
                        replacementSamplerName,
                        target,
                        resourcePath,
                        parts[2],
                        Integer.parseInt(parts[3]),
                        0,
                        0,
                        parts[4],
                        parts[5],
                        filtering.blur(),
                        filtering.clamp()
                );
                case TEXTURE_2D -> new ShaderRawTextureDirective(
                        samplerName,
                        replacementSamplerName,
                        target,
                        resourcePath,
                        parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        0,
                        parts[5],
                        parts[6],
                        filtering.blur(),
                        filtering.clamp()
                );
                case TEXTURE_3D -> new ShaderRawTextureDirective(
                        samplerName,
                        replacementSamplerName,
                        target,
                        resourcePath,
                        parts[2],
                        Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]),
                        parts[6],
                        parts[7],
                        filtering.blur(),
                        filtering.clamp()
                );
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring raw texture directive with malformed size for sampler '{}': {}", samplerName, value);
            return null;
        }
    }

    protected static ShaderProperties.TextureFiltering textureFiltering(ShaderPack pack, String resourcePath, boolean defaultBlur, boolean defaultClamp) {
        String metaPath = resourcePath + ".mcmeta";
        if (resourcePath.indexOf(':') >= 0 || !pack.hasResource(metaPath)) {
            return new ShaderProperties.TextureFiltering(defaultBlur, defaultClamp);
        }

        try (InputStream stream = pack.getResourceAsStream(metaPath)) {
            if (stream == null) {
                return new ShaderProperties.TextureFiltering(defaultBlur, defaultClamp);
            }
            String meta = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new ShaderProperties.TextureFiltering(
                    ShaderProperties.parseMcmetaBoolean(meta, "blur", defaultBlur),
                    ShaderProperties.parseMcmetaBoolean(meta, "clamp", defaultClamp)
            );
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Failed to read texture metadata {}", metaPath, e);
            return new ShaderProperties.TextureFiltering(defaultBlur, defaultClamp);
        }
    }

    protected static boolean parseMcmetaBoolean(String content, String key, boolean fallback) {
        Matcher matcher = Pattern
                .compile("\"" + key + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE)
                .matcher(content);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    protected static ShaderImageTarget parseRawTextureTarget(String token, ShaderImageTarget fallback) {
        if (token == null) {
            return fallback;
        }
        return switch (token.trim().toUpperCase(Locale.ROOT)) {
            case "TEXTURE_1D" -> ShaderImageTarget.TEXTURE_1D;
            case "TEXTURE_2D" -> ShaderImageTarget.TEXTURE_2D;
            case "TEXTURE_3D" -> ShaderImageTarget.TEXTURE_3D;
            default -> fallback;
        };
    }

    protected static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    protected static int parseDirectiveInt(String token, ShaderOptions options) {
        double value = ShaderProperties.parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        int rounded = (int) Math.round(value);
        if (Math.abs(value - rounded) > 0.0001d) {
            throw new NumberFormatException(token);
        }
        return rounded;
    }

    protected static long parseDirectiveLong(String token, ShaderOptions options) {
        double value = ShaderProperties.parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > 0.0001d) {
            throw new NumberFormatException(token);
        }
        return rounded;
    }

    protected static float parseDirectiveFloat(String token, ShaderOptions options) {
        double value = ShaderProperties.parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        return (float) value;
    }

    protected static double parseDirectiveDouble(String token, ShaderOptions options) {
        if (token == null || token.isBlank()) {
            throw new NumberFormatException(String.valueOf(token));
        }

        String trimmed = token.trim();
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            ShaderOption option = options.get(trimmed);
            if (option == null || option.value() == null || option.value().isBlank()) {
                throw new NumberFormatException(trimmed);
            }
            return Double.parseDouble(option.value().trim());
        }
    }

    protected static List<RenderPass> adaptTextureScopeToRenderPasses(List<ProgramId> programIds) {
        List<RenderPass> passes = new ArrayList<>();
        for (ProgramId programId : programIds) {
            RenderPass pass = RenderPass.fromProgramId(programId);
            if (pass != null) {
                passes.add(pass);
            }
        }
        return List.copyOf(passes);
    }

    protected static List<ProgramId> resolveTextureProgramScope(String scope) {
        ProgramId programId = ShaderProperties.resolveProgramId(scope);
        if (programId != null) {
            return List.of(programId);
        }

        ProgramStage stage = switch (scope) {
            case "gbuffers" -> ProgramStage.GBUFFERS;
            case "shadow" -> ProgramStage.SHADOW;
            case "prepare" -> ProgramStage.PREPARE;
            case "deferred" -> ProgramStage.DEFERRED;
            case "deferred_all" -> ProgramStage.DEFERRED;
            case "composite" -> ProgramStage.COMPOSITE;
            case "composite_all" -> ProgramStage.COMPOSITE;
            default -> null;
        };
        if (stage != null) {
            List<ProgramId> ids = Arrays.stream(RenderPass.values())
                    .filter(candidate -> candidate.stage() == stage)
                    .map(RenderPass::programId)
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
            if ("gbuffers".equals(scope)) {
                Arrays.stream(RenderPass.values())
                        .filter(candidate -> candidate.stage() == ProgramStage.SHADOW)
                        .map(RenderPass::programId)
                        .filter(id -> !ids.contains(id))
                        .forEach(ids::add);
            }
            if ("composite".equals(scope) || "composite_all".equals(scope)) {
                if (!ids.contains(ProgramId.FINAL)) {
                    ids.add(ProgramId.FINAL);
                }
            }
            return List.copyOf(ids);
        }

        return switch (scope) {
            case "begin", "setup", "shadowcomp" -> List.of();
            default -> List.of();
        };
    }

    protected static ShaderProgramArrayKey resolveTextureProgramArrayScope(String scope) {
        ShaderProperties.ProgramArrayKey key = ShaderProperties.ProgramArrayKey.parse(scope);
        if (key == null || key.dimensionId() != null) {
            return null;
        }
        return new ShaderProgramArrayKey(key.arrayId(), key.index());
    }

    protected static String normalizeSamplerName(String name) {
        return switch (name) {
            case "noise" -> "noisetex";
            default -> name;
        };
    }

    protected static Map<ProgramId, List<ShaderCustomTextureBinding>> copyProgramTextureMap(Map<ProgramId, List<ShaderCustomTextureBinding>> source) {
        Map<ProgramId, List<ShaderCustomTextureBinding>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(programId, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ShaderProgramArrayKey, List<ShaderCustomTextureBinding>> copyProgramArrayTextureMap(
            Map<ShaderProgramArrayKey, List<ShaderCustomTextureBinding>> source
    ) {
        Map<ShaderProgramArrayKey, List<ShaderCustomTextureBinding>> copy = new LinkedHashMap<>();
        source.forEach((key, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(key, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ProgramId, List<ShaderRawTextureDirective>> copyProgramRawTextureMap(Map<ProgramId, List<ShaderRawTextureDirective>> source) {
        Map<ProgramId, List<ShaderRawTextureDirective>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(programId, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ShaderProgramArrayKey, List<ShaderRawTextureDirective>> copyProgramArrayRawTextureMap(
            Map<ShaderProgramArrayKey, List<ShaderRawTextureDirective>> source
    ) {
        Map<ShaderProgramArrayKey, List<ShaderRawTextureDirective>> copy = new LinkedHashMap<>();
        source.forEach((key, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(key, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<RenderPass, List<ShaderCustomTextureBinding>> copyTextureMap(Map<RenderPass, List<ShaderCustomTextureBinding>> source) {
        Map<RenderPass, List<ShaderCustomTextureBinding>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(pass, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<RenderPass, Map<Attachment, Boolean>> copyFlipMap(Map<RenderPass, Map<Attachment, Boolean>> source) {
        Map<RenderPass, Map<Attachment, Boolean>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(pass, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ProgramId, Map<Attachment, Boolean>> copyProgramFlipMap(Map<ProgramId, Map<Attachment, Boolean>> source) {
        Map<ProgramId, Map<Attachment, Boolean>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(programId, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> copyProgramArrayFlipMap(
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> source
    ) {
        Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> copy = new LinkedHashMap<>();
        source.forEach((arrayKey, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(arrayKey, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }
}
