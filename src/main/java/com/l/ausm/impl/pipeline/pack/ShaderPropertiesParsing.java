package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.l.ausm.api.pipeline.pack.ShaderImageDirective;
import com.l.ausm.api.pipeline.pack.ShaderImageTarget;
import com.l.ausm.api.pipeline.pack.ShaderOitSettings;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.l.ausm.api.pipeline.pack.ShaderRawTextureDirective;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderScreen;
import com.l.ausm.api.pipeline.pack.ShaderScreenEntry;
import com.l.ausm.api.pipeline.pack.ShaderStorageBufferDirective;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderIndirectPointer;
import com.l.ausm.api.pipeline.shader.ShaderProgramArrayKey;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

abstract class ShaderPropertiesParsing extends ShaderPropertiesAccessors {
    protected static ShaderOitSettings parseOitSettings(Properties properties) {
        boolean enabled = ShaderProperties.parseBooleanProperty(properties.getProperty("oit"), false);
        List<Integer> coefficientRanks = ShaderProperties.parseIntegerList(properties.getProperty("oit.gbuffers.coefficientRanks"));
        Map<Attachment, ShaderOitSettings.BufferMode> gbufferBuffers = new EnumMap<>(Attachment.class);
        Map<Attachment, ColorBufferFormat> gbufferFormats = new EnumMap<>(Attachment.class);

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("oit.gbuffers.colortex")) {
                continue;
            }

            String targetName = key.substring("oit.gbuffers.".length());
            boolean formatDirective = targetName.endsWith(".format");
            if (formatDirective) {
                targetName = targetName.substring(0, targetName.length() - ".format".length());
            }

            Attachment attachment = Attachment.fromName(targetName);
            if (attachment == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring OIT directive for unknown target: {}", key);
                continue;
            }

            String value = properties.getProperty(key, "").trim();
            if (formatDirective) {
                ColorBufferFormat format = ColorBufferFormat.fromName(value.toUpperCase(Locale.ROOT));
                if (format == null) {
                    MainMod.LOGGER.warn("[ShaderProperties] Ignoring OIT format directive with unknown format: {}={}", key, value);
                    continue;
                }
                gbufferFormats.put(attachment, format);
                continue;
            }

            if ("frontmost".equalsIgnoreCase(value)) {
                gbufferBuffers.put(attachment, ShaderOitSettings.BufferMode.frontmost());
                continue;
            }

            try {
                gbufferBuffers.put(attachment, ShaderOitSettings.BufferMode.coefficient(Integer.parseInt(value)));
            } catch (NumberFormatException e) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed OIT buffer directive: {}={}", key, value);
            }
        }

        if (!gbufferBuffers.isEmpty()) {
            enabled = enabled || ShaderProperties.parseBooleanProperty(properties.getProperty("oit.gbuffers"), false);
        }
        return new ShaderOitSettings(
                enabled,
                List.copyOf(coefficientRanks),
                Map.copyOf(gbufferBuffers),
                Map.copyOf(gbufferFormats)
        );
    }

    protected static ShaderRenderTargetSettings applyOitRenderTargets(ShaderRenderTargetSettings renderTargets, ShaderOitSettings oitSettings) {
        if (oitSettings == null || !oitSettings.activeForGbuffers()) {
            return renderTargets;
        }

        Map<Attachment, float[]> clearColors = new EnumMap<>(Attachment.class);
        oitSettings.gbufferBuffers().forEach((attachment, mode) -> {
            if (mode.type() == ShaderOitSettings.BufferMode.Type.COEFFICIENT) {
                clearColors.put(attachment, new float[]{0.0f, 0.0f, 0.0f, 0.0f});
            }
        });
        return renderTargets
                .withFormats(oitSettings.gbufferFormats())
                .withClearColors(clearColors);
    }

    protected static boolean parseBooleanProperty(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "1", "yes" -> true;
            case "false", "off", "0", "no" -> false;
            default -> fallback;
        };
    }

    protected static List<Integer> parseIntegerList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<Integer> parsed = new ArrayList<>();
        for (String part : value.trim().split("[,\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            try {
                parsed.add(Integer.parseInt(part));
            } catch (NumberFormatException e) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed OIT coefficient rank: {}", part);
            }
        }
        return parsed;
    }

    protected static Map<RenderPass, Map<Attachment, ShaderBlendMode>> copyBlendAttachmentMap(Map<RenderPass, Map<Attachment, ShaderBlendMode>> source) {
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(pass, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    protected static CustomUniformSet parseCustomUniforms(Properties properties) {
        Map<String, String> expressions = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("uniform.") || key.startsWith("variable.")) {
                expressions.put(key, properties.getProperty(key));
            }
        }
        return CustomUniformSet.parse(Map.copyOf(expressions));
    }

    protected static List<ShaderImageDirective> parseImages(Properties properties, ShaderOptions options) {
        List<ShaderImageDirective> images = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("image.")) {
                continue;
            }
            if (images.size() >= 16) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring image directive beyond Iris limit of 16 images: {}", key);
                continue;
            }

            String name = key.substring("image.".length());
            String[] parts = properties.getProperty(key, "").trim().split("\\s+");
            if (parts.length < 6) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed image directive: {}={}", key, properties.getProperty(key));
                continue;
            }

            try {
                String samplerName = "none".equals(parts[0]) ? null : parts[0];
                boolean clear = Boolean.parseBoolean(parts[4]);
                boolean relative = Boolean.parseBoolean(parts[5]);
                ShaderImageDirective image;
                if (relative) {
                    if (parts.length < 8) {
                        MainMod.LOGGER.warn("[ShaderProperties] Ignoring relative image directive without relative size: {}={}", key, properties.getProperty(key));
                        continue;
                    }
                    image = new ShaderImageDirective(
                            name,
                            samplerName,
                            ShaderImageTarget.TEXTURE_2D,
                            parts[1],
                            parts[2],
                            parts[3],
                            clear,
                            true,
                            0,
                            0,
                            0,
                            ShaderProperties.parseDirectiveFloat(parts[6], options),
                            ShaderProperties.parseDirectiveFloat(parts[7], options)
                    );
                } else {
                    ShaderImageTarget target = switch (parts.length) {
                        case 7 -> ShaderImageTarget.TEXTURE_1D;
                        case 8 -> ShaderImageTarget.TEXTURE_2D;
                        case 9 -> ShaderImageTarget.TEXTURE_3D;
                        default -> null;
                    };
                    if (target == null) {
                        MainMod.LOGGER.warn("[ShaderProperties] Ignoring image directive with unsupported dimension count: {}={}", key, properties.getProperty(key));
                        continue;
                    }
                    image = new ShaderImageDirective(
                            name,
                            samplerName,
                            target,
                            parts[1],
                            parts[2],
                            parts[3],
                            clear,
                            false,
                            ShaderProperties.parseDirectiveInt(parts[6], options),
                            parts.length > 7 ? ShaderProperties.parseDirectiveInt(parts[7], options) : 0,
                            parts.length > 8 ? ShaderProperties.parseDirectiveInt(parts[8], options) : 0,
                            0.0f,
                            0.0f
                    );
                }
                images.add(image);
            } catch (NumberFormatException e) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed image directive: {}={}", key, properties.getProperty(key));
            }
        }
        return List.copyOf(images);
    }

    protected static Map<Integer, ShaderStorageBufferDirective> parseStorageBuffers(Properties properties, ShaderOptions options) {
        Map<Integer, ShaderStorageBufferDirective> buffers = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("bufferObject.")) {
                continue;
            }

            String rawIndex = key.substring("bufferObject.".length());
            String[] parts = properties.getProperty(key, "").trim().split("\\s+");
            if (parts.length == 0 || parts[0].isBlank()) {
                continue;
            }

            try {
                int index = Integer.parseInt(rawIndex);
                long size = ShaderProperties.parseDirectiveLong(parts[0], options);
                if (index > 12) {
                    MainMod.LOGGER.warn("[ShaderProperties] Ignoring SSBO index above Iris reserved limit: {}", key);
                    continue;
                }
                if (size < 1) {
                    continue;
                }

                ShaderStorageBufferDirective directive;
                if (parts.length <= 2) {
                    directive = new ShaderStorageBufferDirective(
                            index,
                            size,
                            false,
                            0.0f,
                            0.0f,
                            parts.length == 2 ? parts[1] : null
                    );
                } else {
                    directive = new ShaderStorageBufferDirective(
                            index,
                            size,
                            Boolean.parseBoolean(parts[1]),
                            ShaderProperties.parseDirectiveFloat(parts[2], options),
                            ShaderProperties.parseDirectiveFloat(parts[3], options),
                            null
                    );
                }
                buffers.put(index, directive);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed SSBO directive: {}={}", key, properties.getProperty(key));
            }
        }
        return Map.copyOf(buffers);
    }

    protected static Map<String, ShaderIndirectPointer> parseIndirectPointers(Properties properties) {
        Map<String, ShaderIndirectPointer> pointers = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("indirect.")) {
                continue;
            }

            String sourceName = key.substring("indirect.".length()).trim();
            String[] parts = properties.getProperty(key, "").trim().split("\\s+");
            if (sourceName.isEmpty() || parts.length < 2) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed indirect directive: {}={}", key, properties.getProperty(key));
                continue;
            }

            try {
                pointers.put(sourceName, new ShaderIndirectPointer(Integer.parseInt(parts[0]), Long.parseLong(parts[1])));
            } catch (NumberFormatException e) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed indirect directive: {}={}", key, properties.getProperty(key));
            }
        }
        return Map.copyOf(pointers);
    }

    protected static Map<ProgramId, Map<Attachment, ShaderBlendMode>> copyProgramBlendAttachmentMap(Map<ProgramId, Map<Attachment, ShaderBlendMode>> source) {
        Map<ProgramId, Map<Attachment, ShaderBlendMode>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(programId, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> copyProgramArrayBlendAttachmentMap(
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> source
    ) {
        Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> copy = new LinkedHashMap<>();
        source.forEach((arrayKey, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(arrayKey, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    protected static Map<ProgramId, ShaderProgramDirectives> buildProgramDirectives(
            Map<ProgramId, List<Attachment>> drawBuffers,
            Map<ProgramId, ShaderViewportScale> viewportScales,
            Map<ProgramId, ShaderAlphaTest> alphaTests,
            Map<ProgramId, ShaderBlendMode> blendModes,
            Map<ProgramId, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
            Map<ProgramId, Map<Attachment, Boolean>> explicitFlips,
            ShaderRenderTargetSettings renderTargets
    ) {
        Map<ProgramId, ShaderProgramDirectives> directives = new EnumMap<>(ProgramId.class);
        for (RenderPass pass : RenderPass.values()) {
            ProgramId programId = pass.programId();
            ShaderProgramDirectives next = new ShaderProgramDirectives(
                    programId,
                    drawBuffers.getOrDefault(programId, List.of()),
                    viewportScales.getOrDefault(programId, ShaderViewportScale.DEFAULT),
                    alphaTests.get(programId),
                    blendModes.get(programId),
                    attachmentBlendModes.getOrDefault(programId, Map.of()),
                    renderTargets.clearDisabledForPass(pass),
                    renderTargets.mipmapEnabled(pass),
                    explicitFlips.getOrDefault(programId, Map.of())
            );
            directives.merge(programId, next, ShaderProperties::mergeProgramDirectives);
        }
        return Map.copyOf(directives);
    }

    protected static Map<ShaderProperties.ProgramArrayKey, ShaderProgramDirectives> buildProgramArrayDirectives(
            Map<ShaderProperties.ProgramArrayKey, List<Attachment>> drawBuffers,
            Map<ShaderProperties.ProgramArrayKey, ShaderViewportScale> viewportScales,
            Map<ShaderProperties.ProgramArrayKey, ShaderAlphaTest> alphaTests,
            Map<ShaderProperties.ProgramArrayKey, ShaderBlendMode> blendModes,
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> explicitFlips,
            ShaderRenderTargetSettings renderTargets
    ) {
        LinkedHashSet<ShaderProperties.ProgramArrayKey> keys = new LinkedHashSet<>();
        keys.addAll(drawBuffers.keySet());
        keys.addAll(viewportScales.keySet());
        keys.addAll(alphaTests.keySet());
        keys.addAll(blendModes.keySet());
        keys.addAll(attachmentBlendModes.keySet());
        keys.addAll(explicitFlips.keySet());

        Map<ShaderProperties.ProgramArrayKey, ShaderProgramDirectives> directives = new LinkedHashMap<>();
        for (ShaderProperties.ProgramArrayKey key : keys) {
            RenderPass bindingPass = ShaderProperties.bindingPassForProgramArray(key.arrayId());
            directives.put(key, new ShaderProgramDirectives(
                    bindingPass.programId(),
                    drawBuffers.getOrDefault(key, List.of()),
                    viewportScales.getOrDefault(key, ShaderViewportScale.DEFAULT),
                    alphaTests.get(key),
                    blendModes.get(key),
                    attachmentBlendModes.getOrDefault(key, Map.of()),
                    renderTargets.clearDisabledForPass(bindingPass),
                    renderTargets.mipmapEnabled(bindingPass),
                    explicitFlips.getOrDefault(key, Map.of())
            ));
        }
        return Map.copyOf(directives);
    }

    protected static RenderPass bindingPassForProgramArray(ProgramArrayId arrayId) {
        return switch (arrayId) {
            case SETUP, BEGIN, PREPARE -> RenderPass.PREPARE;
            case DEFERRED -> RenderPass.DEFERRED;
            case COMPOSITE -> RenderPass.COMPOSITE;
            case SHADOWCOMP -> RenderPass.SHADOW;
        };
    }

    protected static Map<ProgramId, ShaderProgramDirectives> inheritProgramDirectiveFallbacks(Map<ProgramId, ShaderProgramDirectives> source) {
        Map<ProgramId, ShaderProgramDirectives> inherited = new EnumMap<>(ProgramId.class);
        for (ProgramId programId : ProgramId.values()) {
            inherited.put(programId, ShaderProperties.inheritProgramDirective(programId, source, inherited));
        }
        return Map.copyOf(inherited);
    }

    protected static ShaderProgramDirectives inheritProgramDirective(
            ProgramId programId,
            Map<ProgramId, ShaderProgramDirectives> source,
            Map<ProgramId, ShaderProgramDirectives> inherited
    ) {
        ShaderProgramDirectives current = source.getOrDefault(programId, ShaderProgramDirectives.empty(programId));
        ProgramId fallbackId = programId.fallback();
        if (fallbackId == null) {
            return current;
        }

        ShaderProgramDirectives fallback = inherited.get(fallbackId);
        if (fallback == null) {
            fallback = ShaderProperties.inheritProgramDirective(fallbackId, source, inherited);
            inherited.put(fallbackId, fallback);
        }

        return new ShaderProgramDirectives(
                current.programId(),
                current.drawBuffers().isEmpty() ? fallback.drawBuffers() : current.drawBuffers(),
                current.viewportScale().equals(ShaderViewportScale.DEFAULT) ? fallback.viewportScale() : current.viewportScale(),
                current.alphaTestOverride() == null ? fallback.alphaTestOverride() : current.alphaTestOverride(),
                current.blendModeOverride() == null ? fallback.blendModeOverride() : current.blendModeOverride(),
                current.attachmentBlendModes().isEmpty() ? fallback.attachmentBlendModes() : current.attachmentBlendModes(),
                current.clearDisabledBuffers().isEmpty() ? fallback.clearDisabledBuffers() : current.clearDisabledBuffers(),
                current.mipmappedBuffers().isEmpty() ? fallback.mipmappedBuffers() : current.mipmappedBuffers(),
                current.explicitFlips().isEmpty() ? fallback.explicitFlips() : current.explicitFlips()
        );
    }

    protected static ShaderProgramDirectives mergeProgramDirectives(ShaderProgramDirectives existing, ShaderProgramDirectives next) {
        return new ShaderProgramDirectives(
                existing.programId(),
                next.drawBuffers().isEmpty() ? existing.drawBuffers() : next.drawBuffers(),
                next.viewportScale().equals(ShaderViewportScale.DEFAULT) ? existing.viewportScale() : next.viewportScale(),
                next.alphaTestOverride() == null ? existing.alphaTestOverride() : next.alphaTestOverride(),
                next.blendModeOverride() == null ? existing.blendModeOverride() : next.blendModeOverride(),
                next.attachmentBlendModes().isEmpty() ? existing.attachmentBlendModes() : next.attachmentBlendModes(),
                next.clearDisabledBuffers().isEmpty() ? existing.clearDisabledBuffers() : next.clearDisabledBuffers(),
                next.mipmappedBuffers().isEmpty() ? existing.mipmappedBuffers() : next.mipmappedBuffers(),
                next.explicitFlips().isEmpty() ? existing.explicitFlips() : next.explicitFlips()
        );
    }

    protected static void parseScreens(Properties properties, Map<String, ShaderScreen> screens) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals("screen") && !key.startsWith("screen.")) {
                continue;
            }
            if (key.endsWith(".columns")) {
                continue;
            }

            String id = key.equals("screen") ? "screen" : key.substring("screen.".length());
            List<ShaderScreenEntry> entries = ShaderProperties.parseScreenEntries(properties.getProperty(key));
            screens.put(id, new ShaderScreen(id, entries));
        }
    }

    protected static List<ShaderScreenEntry> parseScreenEntries(String value) {
        List<ShaderScreenEntry> entries = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return entries;
        }

        for (String token : value.trim().split("\\s+")) {
            if (token.equals("<empty>")) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.EMPTY, token));
            } else if (token.equals("<profile>")) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.PROFILE, token));
            } else if (token.startsWith("[") && token.endsWith("]") && token.length() > 2) {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.SCREEN, token.substring(1, token.length() - 1)));
            } else {
                entries.add(new ShaderScreenEntry(ShaderScreenEntry.Type.OPTION, token));
            }
        }
        return entries;
    }

    protected static Map<String, ShaderScreen> defaultScreens(ShaderOptions options) {
        List<ShaderScreenEntry> entries = options.all().keySet().stream()
                .map(name -> new ShaderScreenEntry(ShaderScreenEntry.Type.OPTION, name))
                .toList();
        return Map.of("screen", new ShaderScreen("screen", entries));
    }

    protected static Map<String, String> loadProfilesInFileOrder(ShaderPack pack, ShaderPackLayout layout) {
        Map<String, String> profiles = new LinkedHashMap<>();
        if (!pack.hasResource(layout.propertiesPath())) {
            return profiles;
        }

        try (InputStream stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream == null) {
                return profiles;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }

                    int equals = trimmed.indexOf('=');
                    if (equals <= 0) {
                        continue;
                    }

                    String key = trimmed.substring(0, equals).trim();
                    if (!key.startsWith("profile.")) {
                        continue;
                    }

                    String profileName = key.substring("profile.".length());
                    String profileValue = trimmed.substring(equals + 1).trim();
                    profiles.put(profileName, profileValue);
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Failed to read ordered profiles from {}", layout.propertiesPath(), e);
        }
        return profiles;
    }

    public Map<String, String> profileOverrides(String profileName) {
        Map<String, String> values = new LinkedHashMap<>();
        self().applyProfile(profileName, values, new HashSet<>());
        return values;
    }

    protected void applyProfile(String profileName, Map<String, String> values, Set<String> visited) {
        if (!visited.add(profileName)) {
            return;
        }

        String profile = profiles.get(profileName);
        if (profile == null) {
            return;
        }

        for (String token : profile.trim().split("\\s+")) {
            if (token.isBlank()) {
                continue;
            }
            if (token.startsWith("profile.")) {
                self().applyProfile(token.substring("profile.".length()), values, visited);
            } else if (token.startsWith("!")) {
                values.put(token.substring(1), "false");
            } else {
                int equals = token.indexOf('=');
                if (equals > 0) {
                    values.put(token.substring(0, equals), token.substring(equals + 1));
                } else {
                    values.put(token, "true");
                }
            }
        }
    }

    protected static List<Attachment> parseDrawBuffers(String value) {
        List<Attachment> attachments = new ArrayList<>();
        if (value == null) {
            return attachments;
        }

        String normalized = value.replaceAll("[,\\s]+", "");
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (!Character.isDigit(ch)) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring invalid draw buffer token: {}", ch);
                continue;
            }

            Attachment attachment = Attachment.fromColorIndex(Character.digit(ch, 10));
            if (attachment != null) {
                attachments.add(attachment);
            }
        }

        return attachments;
    }

    protected static List<ShaderCustomTextureBinding> parseCustomTextures(
            ShaderPack pack,
            ShaderPackLayout layout,
            Properties properties,
            Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures,
            Map<ProgramId, List<ShaderCustomTextureBinding>> programTextures,
            Map<ShaderProgramArrayKey, List<ShaderCustomTextureBinding>> programArrayTextures,
            List<ShaderRawTextureDirective> rawTextures,
            Map<ProgramId, List<ShaderRawTextureDirective>> programRawTextures,
            Map<ShaderProgramArrayKey, List<ShaderRawTextureDirective>> programArrayRawTextures
    ) {
        List<ShaderCustomTextureBinding> globalTextures = new ArrayList<>();
        int generatedTextureIndex = 0;

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("texture.")) {
                if (key.startsWith("customTexture.")) {
                    String samplerName = ShaderProperties.normalizeSamplerName(key.substring("customTexture.".length()));
                    String value = properties.getProperty(key);
                    ShaderRawTextureDirective rawTexture = ShaderProperties.parseRawTextureDirective(pack, layout, samplerName, samplerName, value);
                    if (rawTexture != null) {
                        rawTextures.add(rawTexture);
                        continue;
                    }

                    String resourcePath = layout.normalizeTexturePath(value);
                    if (resourcePath != null) {
                        ShaderProperties.TextureFiltering filtering = ShaderProperties.textureFiltering(pack, resourcePath, false, false);
                        globalTextures.add(new ShaderCustomTextureBinding(samplerName, resourcePath, filtering.blur(), filtering.clamp()));
                    }
                }
                continue;
            }

            String suffix = key.substring("texture.".length());
            String value = properties.getProperty(key);
            if ("noise".equals(suffix)) {
                continue;
            }

            int dot = suffix.indexOf('.');
            if (dot < 0) {
                String resourcePath = layout.normalizeTexturePath(value);
                if (resourcePath == null) {
                    continue;
                }
                String samplerName = ShaderProperties.normalizeSamplerName(suffix);
                ShaderProperties.TextureFiltering filtering = ShaderProperties.textureFiltering(pack, resourcePath, false, false);
                globalTextures.add(new ShaderCustomTextureBinding(samplerName, resourcePath, filtering.blur(), filtering.clamp()));
                continue;
            }

            String scope = suffix.substring(0, dot);
            List<ProgramId> programIds = ShaderProperties.resolveTextureProgramScope(scope);
            ShaderProgramArrayKey arrayKey = ShaderProperties.resolveTextureProgramArrayScope(scope);
            if (programIds.isEmpty() && arrayKey == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring texture binding for unknown program: {}", suffix);
                continue;
            }

            String samplerName = ShaderProperties.normalizeSamplerName(suffix.substring(dot + 1));
            ShaderRawTextureDirective rawTexture = ShaderProperties.parseRawTextureDirective(
                    pack,
                    layout,
                    samplerName,
                    "customtex" + generatedTextureIndex,
                    value
            );
            if (rawTexture != null) {
                generatedTextureIndex++;
                for (ProgramId programId : programIds) {
                    programRawTextures.computeIfAbsent(programId, ignored -> new ArrayList<>()).add(rawTexture);
                }
                if (arrayKey != null) {
                    programArrayRawTextures.computeIfAbsent(arrayKey, ignored -> new ArrayList<>()).add(rawTexture);
                }
                continue;
            }

            String resourcePath = layout.normalizeTexturePath(value);
            if (resourcePath == null) {
                continue;
            }
            ShaderProperties.TextureFiltering filtering = ShaderProperties.textureFiltering(pack, resourcePath, false, false);
            ShaderCustomTextureBinding binding = new ShaderCustomTextureBinding(samplerName, resourcePath, filtering.blur(), filtering.clamp());
            for (ProgramId programId : programIds) {
                programTextures.computeIfAbsent(programId, ignored -> new ArrayList<>()).add(binding);
            }
            if (arrayKey != null) {
                programArrayTextures.computeIfAbsent(arrayKey, ignored -> new ArrayList<>()).add(binding);
            }
            for (RenderPass pass : ShaderProperties.adaptTextureScopeToRenderPasses(programIds)) {
                passTextures.computeIfAbsent(pass, ignored -> new ArrayList<>()).add(binding);
            }
        }

        return List.copyOf(globalTextures);
    }

    protected static ShaderCustomTextureBinding parseNoiseTexture(ShaderPack pack, ShaderPackLayout layout, Properties properties) {
        String value = properties.getProperty("texture.noise");
        String resourcePath = layout.normalizeTexturePath(value);
        if (resourcePath == null) {
            return null;
        }

        ShaderProperties.TextureFiltering filtering = ShaderProperties.textureFiltering(pack, resourcePath, false, false);
        return new ShaderCustomTextureBinding("noisetex", resourcePath, filtering.blur(), filtering.clamp());
    }
}
