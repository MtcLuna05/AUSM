package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public record ShaderProperties(
        Map<RenderPass, List<Attachment>> drawBuffers,
        Map<ProgramKey, String> programEnabledExpressions,
        ShaderOptions options,
        Map<String, ShaderScreen> screens,
        Map<String, String> profiles,
        ShaderRenderTargetSettings renderTargets,
        List<ShaderCustomTextureBinding> globalTextures,
        Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures,
        Map<RenderPass, Map<Attachment, Boolean>> explicitFlips,
        Map<RenderPass, ShaderViewportScale> viewportScales,
        Map<String, String> translations,
        ShaderBlockIdMap.BlockIdRules blockIds,
        Map<ResourceLocation, Integer> entityIds,
        Map<Integer, Integer> itemIds,
        ShaderRenderSettings renderSettings,
        Map<RenderPass, ShaderAlphaTest> alphaTests,
        Map<RenderPass, ShaderBlendMode> blendModes,
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
        Map<ProgramId, ShaderProgramDirectives> programDirectives,
        ShaderTextureDirectives textureDirectives,
        CustomUniformSet customUniforms,
        ShaderPackDirectives packDirectives,
        Map<ProgramArrayKey, String> programArrayEnabledExpressions
) {

    public static ShaderProperties load(ShaderPack pack) {
        return load(pack, Map.of());
    }

    public static ShaderProperties load(ShaderPack pack, Map<String, String> optionOverrides) {
        Map<RenderPass, List<Attachment>> drawBuffers = new EnumMap<>(RenderPass.class);
        Map<ProgramId, List<Attachment>> programDrawBuffers = new EnumMap<>(ProgramId.class);
        Map<RenderPass, Map<Attachment, Boolean>> explicitFlips = new EnumMap<>(RenderPass.class);
        Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips = new EnumMap<>(ProgramId.class);
        Map<RenderPass, ShaderViewportScale> viewportScales = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderViewportScale> programViewportScales = new EnumMap<>(ProgramId.class);
        Map<ProgramKey, String> programEnabledExpressions = new java.util.LinkedHashMap<>();
        Map<ProgramArrayKey, String> programArrayEnabledExpressions = new java.util.LinkedHashMap<>();
        Map<String, ShaderScreen> screens = new java.util.LinkedHashMap<>();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        Map<String, String> translations = ShaderLang.load(pack, layout);
        ShaderBlockIdMap.BlockIdRules blockIds = ShaderBlockIdMap.load(pack, layout);
        Map<ResourceLocation, Integer> entityIds = ShaderEntityIdMap.load(pack, layout);
        Map<Integer, Integer> itemIds = ShaderItemIdMap.load(pack, layout);
        Map<String, String> profiles = loadProfilesInFileOrder(pack, layout);

        if (!pack.hasResource(layout.propertiesPath())) {
            ShaderOptions options = ShaderOptionScanner.scan(pack, new Properties(), optionOverrides);
            programDrawBuffers.putAll(ShaderDrawBuffersScanner.scanProgramIds(pack, layout, options));
            adaptProgramDrawBuffers(programDrawBuffers, drawBuffers);
            ShaderRenderTargetSettings renderTargets = ShaderBufferFormatScanner.scan(pack, options);
            Map<ProgramId, ShaderProgramDirectives> programDirectives = inheritProgramDirectiveFallbacks(
                    buildProgramDirectives(programDrawBuffers, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), renderTargets)
            );
            ShaderRenderSettings renderSettings = ShaderRenderSettings.defaults();
            ShaderPackDirectives packDirectives = new ShaderPackDirectives(
                    renderTargets,
                    renderSettings,
                    ShaderTextureDirectives.empty(),
                    ShaderComputeDirectives.empty(),
                    List.of(),
                    Map.of(),
                    ShaderFeatureSet.empty(),
                    256,
                    null,
                    programDirectives,
                    CustomUniformSet.empty()
            );
            packDirectives = packDirectives.withCapabilities(ShaderPipelineCapabilities.from(packDirectives));
            return new ShaderProperties(
                    drawBuffers,
                    programEnabledExpressions,
                    options,
                    defaultScreens(options),
                    profiles,
                    renderTargets,
                    List.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    translations,
                    blockIds,
                    entityIds,
                    itemIds,
                    renderSettings,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    programDirectives,
                    ShaderTextureDirectives.empty(),
                    CustomUniformSet.empty(),
                    packDirectives,
                    Map.of()
            );
        }

        Properties rawProperties = new Properties();
        try (InputStream stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream != null) {
                rawProperties.load(stream);
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderProperties] Failed to read shaders.properties", e);
        }

        ShaderOptions options = ShaderOptionScanner.scan(pack, rawProperties, optionOverrides);
        Properties properties = ShaderPropertiesPreprocessor.load(pack, layout, options);

        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("drawBuffers.")) {
                String programName = key.substring("drawBuffers.".length());
                RenderPass pass = resolveProgramName(programName);
                ProgramId programId = resolveProgramId(programName);
                if (programId == null) {
                    MainMod.LOGGER.warn("[ShaderProperties] Ignoring drawBuffers for unknown program: {}", programName);
                    continue;
                }

                List<Attachment> attachments = parseDrawBuffers(properties.getProperty(key));
                if (!attachments.isEmpty()) {
                    programDrawBuffers.put(programId, attachments);
                    if (pass != null) {
                        drawBuffers.put(pass, attachments);
                    }
                    MainMod.LOGGER.debug("[ShaderProperties] {} draw buffers: {}", programName, attachments);
                }
                continue;
            }

            if (key.startsWith("flip.")) {
                parseExplicitFlip(key, properties.getProperty(key), explicitFlips, programExplicitFlips);
                continue;
            }

            if (key.startsWith("scale.")) {
                parseViewportScale(key, properties.getProperty(key), viewportScales, programViewportScales);
                continue;
            }

            if (key.startsWith("alphaTest.")) {
                continue;
            }

            if (key.startsWith("blend.")) {
                continue;
            }

            if (key.startsWith("size.buffer.")) {
                continue;
            }

            if (key.startsWith("profile.")) {
                continue;
            }

            if (!key.startsWith("program.") || !key.endsWith(".enabled")) {
                continue;
            }

            String programName = key.substring("program.".length(), key.length() - ".enabled".length());
            RenderPass pass = resolveProgramName(programName);
            if (pass == null) {
                ProgramArrayKey arrayKey = ProgramArrayKey.parse(programName);
                if (arrayKey != null) {
                    programArrayEnabledExpressions.put(arrayKey, properties.getProperty(key));
                } else {
                    MainMod.LOGGER.warn("[ShaderProperties] Ignoring enabled expression for unknown program: {}", programName);
                }
                continue;
            }
            programEnabledExpressions.put(ProgramKey.parse(programName, pass.programId()), properties.getProperty(key));
        }

        parseScreens(properties, screens);
        if (screens.isEmpty()) {
            screens.putAll(defaultScreens(options));
        }

        ShaderDrawBuffersScanner.scanProgramIds(pack, layout, options).forEach(programDrawBuffers::putIfAbsent);
        adaptProgramDrawBuffers(programDrawBuffers, drawBuffers);

        Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures = new EnumMap<>(RenderPass.class);
        Map<ProgramId, List<ShaderCustomTextureBinding>> programTextures = new EnumMap<>(ProgramId.class);
        List<ShaderRawTextureDirective> rawTextures = new ArrayList<>();
        Map<ProgramId, List<ShaderRawTextureDirective>> programRawTextures = new EnumMap<>(ProgramId.class);
        List<ShaderCustomTextureBinding> globalTextures = parseCustomTextures(
                layout,
                properties,
                passTextures,
                programTextures,
                rawTextures,
                programRawTextures
        );
        ShaderTextureDirectives textureDirectives = new ShaderTextureDirectives(
                globalTextures,
                copyProgramTextureMap(programTextures),
                List.copyOf(rawTextures),
                copyProgramRawTextureMap(programRawTextures)
        );

        BlendModes blendModes = parseBlendModes(properties, options);
        AlphaTests alphaTests = parseAlphaTests(properties, options);
        ShaderRenderTargetSettings renderTargets = ShaderBufferFormatScanner.scan(pack, options);
        Map<ProgramId, ShaderProgramDirectives> programDirectives = inheritProgramDirectiveFallbacks(buildProgramDirectives(
                programDrawBuffers,
                programViewportScales,
                alphaTests.programModes(),
                blendModes.programModes(),
                blendModes.programAttachmentModes(),
                copyProgramFlipMap(programExplicitFlips),
                renderTargets
        ));
        ShaderRenderSettings renderSettings = ShaderRenderSettings.parse(properties);
        CustomUniformSet customUniforms = parseCustomUniforms(properties);
        List<ShaderImageDirective> images = parseImages(properties);
        Map<Integer, ShaderStorageBufferDirective> storageBuffers = parseStorageBuffers(properties);
        ShaderFeatureSet features = ShaderFeatureSet.parse(properties);
        int noiseTextureResolution = parsePositiveInt(properties.getProperty("noiseTextureResolution"), 256);
        ShaderPackDirectives packDirectives = new ShaderPackDirectives(
                renderTargets,
                renderSettings,
                textureDirectives,
                ShaderComputeDirectives.empty(),
                images,
                storageBuffers,
                features,
                noiseTextureResolution,
                null,
                programDirectives,
                customUniforms
        );
        packDirectives = packDirectives.withCapabilities(ShaderPipelineCapabilities.from(packDirectives));

        return new ShaderProperties(
                drawBuffers,
                programEnabledExpressions,
                options,
                screens,
                profiles,
                renderTargets,
                globalTextures,
                copyTextureMap(passTextures),
                copyFlipMap(explicitFlips),
                Map.copyOf(viewportScales),
                translations,
                blockIds,
                entityIds,
                itemIds,
                renderSettings,
                alphaTests.passModes(),
                blendModes.passModes(),
                blendModes.attachmentModes(),
                programDirectives,
                textureDirectives,
                customUniforms,
                packDirectives,
                Map.copyOf(programArrayEnabledExpressions)
        );
    }

    public String translate(String key, String fallback) {
        return translations.getOrDefault(key, fallback);
    }

    public Map<Attachment, ColorBufferFormat> bufferFormats() {
        return renderTargets.formats();
    }

    public boolean isProgramEnabled(RenderPass pass) {
        String expression = programEnabledExpressions.get(new ProgramKey(currentDimensionId(), pass.programId()));
        if (expression == null) {
            expression = programEnabledExpressions.get(new ProgramKey(null, pass.programId()));
        }
        return ShaderExpression.evaluate(expression, options::booleanValue);
    }

    public boolean isProgramArrayEnabled(ProgramArrayId arrayId, String sourceName) {
        ProgramArrayKey sourceKey = ProgramArrayKey.parse(sourceName);
        int index = sourceKey == null ? 0 : sourceKey.index();
        String expression = programArrayEnabledExpressions.get(new ProgramArrayKey(currentDimensionId(), arrayId, index));
        if (expression == null) {
            expression = programArrayEnabledExpressions.get(new ProgramArrayKey(null, arrayId, index));
        }
        return ShaderExpression.evaluate(expression, options::booleanValue);
    }

    public ShaderProgramDirectives directivesFor(RenderPass pass) {
        return directivesFor(pass.programId());
    }

    public ShaderProgramDirectives directivesFor(ProgramId requestedProgramId) {
        ProgramId programId = requestedProgramId;
        if (programId == null) {
            return ShaderProgramDirectives.empty(ProgramId.BASIC);
        }
        ShaderProgramDirectives directives = programDirectives.get(programId);
        return directives == null ? ShaderProgramDirectives.empty(requestedProgramId) : directives;
    }

    private static int currentDimensionId() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.world.provider == null) {
            return 0;
        }
        return mc.world.provider.getDimension();
    }

    private static RenderPass resolveProgramName(String programName) {
        int slash = programName.lastIndexOf('/');
        if (slash >= 0 && slash < programName.length() - 1) {
            programName = programName.substring(slash + 1);
        }
        return RenderPass.fromName(programName);
    }

    private static ProgramId resolveProgramId(String programName) {
        int slash = programName.lastIndexOf('/');
        if (slash >= 0 && slash < programName.length() - 1) {
            programName = programName.substring(slash + 1);
        }
        return ProgramId.fromSourceName(programName);
    }

    private static void adaptProgramDrawBuffers(Map<ProgramId, List<Attachment>> source, Map<RenderPass, List<Attachment>> target) {
        for (RenderPass pass : RenderPass.values()) {
            List<Attachment> attachments = source.get(pass.programId());
            if (attachments != null && !attachments.isEmpty()) {
                target.putIfAbsent(pass, attachments);
            }
        }
    }

    private static void parseExplicitFlip(
            String key,
            String value,
            Map<RenderPass, Map<Attachment, Boolean>> explicitFlips,
            Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips
    ) {
        String suffix = key.substring("flip.".length());
        int dot = suffix.lastIndexOf('.');
        if (dot <= 0 || dot >= suffix.length() - 1) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed flip directive: {}", key);
            return;
        }

        String programName = suffix.substring(0, dot);
        RenderPass pass = resolveProgramName(programName);
        ProgramId programId = resolveProgramId(programName);
        Attachment attachment = Attachment.fromName(suffix.substring(dot + 1));
        if (programId == null || attachment == null) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring flip directive for unknown target: {}", key);
            return;
        }

        boolean parsedValue = Boolean.parseBoolean(value);
        if (pass != null) {
            explicitFlips
                    .computeIfAbsent(pass, ignored -> new EnumMap<>(Attachment.class))
                    .put(attachment, parsedValue);
        }
        programExplicitFlips
                .computeIfAbsent(programId, ignored -> new EnumMap<>(Attachment.class))
                .put(attachment, parsedValue);
    }

    private static void parseViewportScale(
            String key,
            String value,
            Map<RenderPass, ShaderViewportScale> viewportScales,
            Map<ProgramId, ShaderViewportScale> programViewportScales
    ) {
        String programName = key.substring("scale.".length());
        RenderPass pass = resolveProgramName(programName);
        ProgramId programId = resolveProgramId(programName);
        if (programId == null) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring scale directive for unknown program: {}", key);
            return;
        }

        String[] parts = value.trim().split("\\s+");
        try {
            float scale = Float.parseFloat(parts[0]);
            float offsetX = parts.length > 1 ? Float.parseFloat(parts[1]) : 0.0f;
            float offsetY = parts.length > 2 ? Float.parseFloat(parts[2]) : 0.0f;
            ShaderViewportScale viewportScale = new ShaderViewportScale(scale, offsetX, offsetY);
            if (pass != null) {
                viewportScales.put(pass, viewportScale);
            }
            programViewportScales.put(programId, viewportScale);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed scale directive: {}={}", key, value);
        }
    }

    private static AlphaTests parseAlphaTests(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderAlphaTest> alphaTests = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderAlphaTest> programAlphaTests = new EnumMap<>(ProgramId.class);
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("alphaTest.")) {
                continue;
            }

            String programName = key.substring("alphaTest.".length());
            RenderPass pass = resolveProgramName(programName);
            ProgramId programId = resolveProgramId(programName);
            ShaderAlphaTest alphaTest = ShaderAlphaTest.parse(properties.getProperty(key), options);
            if (programId == null || alphaTest == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed alphaTest directive: {}={}", key, properties.getProperty(key));
                continue;
            }
            if (pass != null) {
                alphaTests.put(pass, alphaTest);
            }
            programAlphaTests.put(programId, alphaTest);
        }
        return new AlphaTests(Map.copyOf(alphaTests), Map.copyOf(programAlphaTests));
    }

    private static BlendModes parseBlendModes(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderBlendMode> blendModes = new EnumMap<>(RenderPass.class);
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderBlendMode> programBlendModes = new EnumMap<>(ProgramId.class);
        Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes = new EnumMap<>(ProgramId.class);
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("blend.")) {
                continue;
            }

            String suffix = key.substring("blend.".length());
            int targetSeparator = suffix.lastIndexOf('.');
            String programName = targetSeparator < 0 ? suffix : suffix.substring(0, targetSeparator);
            RenderPass pass = resolveProgramName(programName);
            ProgramId programId = resolveProgramId(programName);
            ShaderBlendMode blendMode = ShaderBlendMode.parse(properties.getProperty(key), options);
            if (programId == null || blendMode == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed blend directive: {}={}", key, properties.getProperty(key));
                continue;
            }

            if (targetSeparator < 0) {
                if (pass != null) {
                    blendModes.put(pass, blendMode);
                }
                programBlendModes.put(programId, blendMode);
                continue;
            }

            Attachment attachment = Attachment.fromName(suffix.substring(targetSeparator + 1));
            if (attachment == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring blend directive for unknown target: {}", key);
                continue;
            }
            if (pass != null) {
                attachmentModes
                        .computeIfAbsent(pass, ignored -> new EnumMap<>(Attachment.class))
                        .put(attachment, blendMode);
            }
            programAttachmentModes
                    .computeIfAbsent(programId, ignored -> new EnumMap<>(Attachment.class))
                    .put(attachment, blendMode);
        }
        return new BlendModes(Map.copyOf(blendModes), copyBlendAttachmentMap(attachmentModes), Map.copyOf(programBlendModes), copyProgramBlendAttachmentMap(programAttachmentModes));
    }

    private static Map<RenderPass, Map<Attachment, ShaderBlendMode>> copyBlendAttachmentMap(Map<RenderPass, Map<Attachment, ShaderBlendMode>> source) {
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(pass, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    private static CustomUniformSet parseCustomUniforms(Properties properties) {
        Map<String, String> expressions = new java.util.LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("uniform.") || key.startsWith("variable.")) {
                expressions.put(key, properties.getProperty(key));
            }
        }
        return CustomUniformSet.parse(Map.copyOf(expressions));
    }

    private static List<ShaderImageDirective> parseImages(Properties properties) {
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
                            Float.parseFloat(parts[6]),
                            Float.parseFloat(parts[7])
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
                            Integer.parseInt(parts[6]),
                            parts.length > 7 ? Integer.parseInt(parts[7]) : 0,
                            parts.length > 8 ? Integer.parseInt(parts[8]) : 0,
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

    private static Map<Integer, ShaderStorageBufferDirective> parseStorageBuffers(Properties properties) {
        Map<Integer, ShaderStorageBufferDirective> buffers = new java.util.TreeMap<>();
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
                long size = Long.parseLong(parts[0]);
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
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3]),
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

    private static Map<ProgramId, Map<Attachment, ShaderBlendMode>> copyProgramBlendAttachmentMap(Map<ProgramId, Map<Attachment, ShaderBlendMode>> source) {
        Map<ProgramId, Map<Attachment, ShaderBlendMode>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(programId, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    private record BlendModes(
            Map<RenderPass, ShaderBlendMode> passModes,
            Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes,
            Map<ProgramId, ShaderBlendMode> programModes,
            Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes
    ) {
    }

    private record AlphaTests(
            Map<RenderPass, ShaderAlphaTest> passModes,
            Map<ProgramId, ShaderAlphaTest> programModes
    ) {
    }

    private static Map<ProgramId, ShaderProgramDirectives> buildProgramDirectives(
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

    private static Map<ProgramId, ShaderProgramDirectives> inheritProgramDirectiveFallbacks(Map<ProgramId, ShaderProgramDirectives> source) {
        Map<ProgramId, ShaderProgramDirectives> inherited = new EnumMap<>(ProgramId.class);
        for (ProgramId programId : ProgramId.values()) {
            inherited.put(programId, inheritProgramDirective(programId, source, inherited));
        }
        return Map.copyOf(inherited);
    }

    private static ShaderProgramDirectives inheritProgramDirective(
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
            fallback = inheritProgramDirective(fallbackId, source, inherited);
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

    private static ShaderProgramDirectives mergeProgramDirectives(ShaderProgramDirectives existing, ShaderProgramDirectives next) {
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

    public record ProgramKey(Integer dimensionId, ProgramId programId) {
        private static ProgramKey parse(String rawName, ProgramId fallbackProgramId) {
            int dimensionId = Integer.MIN_VALUE;
            String programName = rawName;
            if (rawName.startsWith("world")) {
                int slash = rawName.indexOf('/');
                if (slash > "world".length()) {
                    try {
                        dimensionId = Integer.parseInt(rawName.substring("world".length(), slash));
                        programName = rawName.substring(slash + 1);
                    } catch (NumberFormatException ignored) {
                        dimensionId = Integer.MIN_VALUE;
                        programName = rawName;
                    }
                }
            }

            ProgramId programId = ProgramId.fromSourceName(programName);
            if (programId == null) {
                programId = fallbackProgramId;
            }
            Integer dimension = dimensionId == Integer.MIN_VALUE ? null : dimensionId;
            return new ProgramKey(dimension, programId);
        }
    }

    public record ProgramArrayKey(Integer dimensionId, ProgramArrayId arrayId, int index) {
        private static ProgramArrayKey parse(String rawName) {
            int dimensionId = Integer.MIN_VALUE;
            String programName = rawName;
            if (rawName.startsWith("world")) {
                int slash = rawName.indexOf('/');
                if (slash > "world".length()) {
                    try {
                        dimensionId = Integer.parseInt(rawName.substring("world".length(), slash));
                        programName = rawName.substring(slash + 1);
                    } catch (NumberFormatException ignored) {
                        dimensionId = Integer.MIN_VALUE;
                        programName = rawName;
                    }
                }
            }

            for (ProgramArrayId arrayId : ProgramArrayId.values()) {
                String prefix = arrayId.sourcePrefix();
                if (programName.equals(prefix)) {
                    return new ProgramArrayKey(dimensionId == Integer.MIN_VALUE ? null : dimensionId, arrayId, 0);
                }
                if (programName.startsWith(prefix)) {
                    String suffix = programName.substring(prefix.length());
                    if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                        return new ProgramArrayKey(
                                dimensionId == Integer.MIN_VALUE ? null : dimensionId,
                                arrayId,
                                Integer.parseInt(suffix)
                        );
                    }
                }
            }
            return null;
        }
    }

    private static void parseScreens(Properties properties, Map<String, ShaderScreen> screens) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals("screen") && !key.startsWith("screen.")) {
                continue;
            }
            if (key.endsWith(".columns")) {
                continue;
            }

            String id = key.equals("screen") ? "screen" : key.substring("screen.".length());
            List<ShaderScreenEntry> entries = parseScreenEntries(properties.getProperty(key));
            screens.put(id, new ShaderScreen(id, entries));
        }
    }

    private static List<ShaderScreenEntry> parseScreenEntries(String value) {
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

    private static Map<String, ShaderScreen> defaultScreens(ShaderOptions options) {
        List<ShaderScreenEntry> entries = options.all().keySet().stream()
                .map(name -> new ShaderScreenEntry(ShaderScreenEntry.Type.OPTION, name))
                .toList();
        return Map.of("screen", new ShaderScreen("screen", entries));
    }

    private static Map<String, String> loadProfilesInFileOrder(ShaderPack pack, ShaderPackLayout layout) {
        Map<String, String> profiles = new java.util.LinkedHashMap<>();
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
        Map<String, String> values = new java.util.LinkedHashMap<>();
        applyProfile(profileName, values, new java.util.HashSet<>());
        return values;
    }

    private void applyProfile(String profileName, Map<String, String> values, java.util.Set<String> visited) {
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
                applyProfile(token.substring("profile.".length()), values, visited);
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

    private static List<Attachment> parseDrawBuffers(String value) {
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

    private static List<ShaderCustomTextureBinding> parseCustomTextures(
            ShaderPackLayout layout,
            Properties properties,
            Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures,
            Map<ProgramId, List<ShaderCustomTextureBinding>> programTextures,
            List<ShaderRawTextureDirective> rawTextures,
            Map<ProgramId, List<ShaderRawTextureDirective>> programRawTextures
    ) {
        List<ShaderCustomTextureBinding> globalTextures = new ArrayList<>();
        int generatedTextureIndex = 0;

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("texture.")) {
                if (key.startsWith("customTexture.")) {
                    String samplerName = normalizeSamplerName(key.substring("customTexture.".length()));
                    String value = properties.getProperty(key);
                    ShaderRawTextureDirective rawTexture = parseRawTextureDirective(layout, samplerName, samplerName, value);
                    if (rawTexture != null) {
                        rawTextures.add(rawTexture);
                        continue;
                    }

                    String resourcePath = layout.normalizeTexturePath(value);
                    if (resourcePath != null) {
                        globalTextures.add(new ShaderCustomTextureBinding(samplerName, resourcePath));
                    }
                }
                continue;
            }

            String suffix = key.substring("texture.".length());
            String value = properties.getProperty(key);

            int dot = suffix.indexOf('.');
            if (dot < 0) {
                String resourcePath = layout.normalizeTexturePath(value);
                if (resourcePath == null) {
                    continue;
                }
                String samplerName = normalizeSamplerName(suffix);
                globalTextures.add(new ShaderCustomTextureBinding(samplerName, resourcePath));
                continue;
            }

            String scope = suffix.substring(0, dot);
            List<ProgramId> programIds = resolveTextureProgramScope(scope);
            if (programIds.isEmpty()) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring texture binding for unknown program: {}", suffix);
                continue;
            }

            String samplerName = normalizeSamplerName(suffix.substring(dot + 1));
            ShaderRawTextureDirective rawTexture = parseRawTextureDirective(
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
                continue;
            }

            String resourcePath = layout.normalizeTexturePath(value);
            if (resourcePath == null) {
                continue;
            }
            ShaderCustomTextureBinding binding = new ShaderCustomTextureBinding(samplerName, resourcePath);
            for (ProgramId programId : programIds) {
                programTextures.computeIfAbsent(programId, ignored -> new ArrayList<>()).add(binding);
            }
            for (RenderPass pass : adaptTextureScopeToRenderPasses(programIds)) {
                passTextures.computeIfAbsent(pass, ignored -> new ArrayList<>()).add(binding);
            }
        }

        return List.copyOf(globalTextures);
    }

    private static ShaderRawTextureDirective parseRawTextureDirective(
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
            case 7 -> parseRawTextureTarget(parts[1], ShaderImageTarget.TEXTURE_2D);
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
                        parts[5]
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
                        parts[6]
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
                        parts[7]
                );
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring raw texture directive with malformed size for sampler '{}': {}", samplerName, value);
            return null;
        }
    }

    private static ShaderImageTarget parseRawTextureTarget(String token, ShaderImageTarget fallback) {
        if (token == null) {
            return fallback;
        }
        return switch (token.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "TEXTURE_1D" -> ShaderImageTarget.TEXTURE_1D;
            case "TEXTURE_2D" -> ShaderImageTarget.TEXTURE_2D;
            case "TEXTURE_3D" -> ShaderImageTarget.TEXTURE_3D;
            default -> fallback;
        };
    }

    private static int parsePositiveInt(String value, int fallback) {
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

    private static List<RenderPass> adaptTextureScopeToRenderPasses(List<ProgramId> programIds) {
        List<RenderPass> passes = new ArrayList<>();
        for (ProgramId programId : programIds) {
            RenderPass pass = RenderPass.fromProgramId(programId);
            if (pass != null) {
                passes.add(pass);
            }
        }
        return List.copyOf(passes);
    }

    private static List<ProgramId> resolveTextureProgramScope(String scope) {
        ProgramId programId = resolveProgramId(scope);
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
            List<ProgramId> ids = java.util.Arrays.stream(RenderPass.values())
                    .filter(candidate -> candidate.stage() == stage)
                    .map(RenderPass::programId)
                    .distinct()
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if ("gbuffers".equals(scope)) {
                java.util.Arrays.stream(RenderPass.values())
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

    private static String normalizeSamplerName(String name) {
        return switch (name) {
            case "noise" -> "noisetex";
            default -> name;
        };
    }

    private static Map<ProgramId, List<ShaderCustomTextureBinding>> copyProgramTextureMap(Map<ProgramId, List<ShaderCustomTextureBinding>> source) {
        Map<ProgramId, List<ShaderCustomTextureBinding>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(programId, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<ProgramId, List<ShaderRawTextureDirective>> copyProgramRawTextureMap(Map<ProgramId, List<ShaderRawTextureDirective>> source) {
        Map<ProgramId, List<ShaderRawTextureDirective>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(programId, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<RenderPass, List<ShaderCustomTextureBinding>> copyTextureMap(Map<RenderPass, List<ShaderCustomTextureBinding>> source) {
        Map<RenderPass, List<ShaderCustomTextureBinding>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, textures) -> {
            if (!textures.isEmpty()) {
                copy.put(pass, List.copyOf(textures));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<RenderPass, Map<Attachment, Boolean>> copyFlipMap(Map<RenderPass, Map<Attachment, Boolean>> source) {
        Map<RenderPass, Map<Attachment, Boolean>> copy = new EnumMap<>(RenderPass.class);
        source.forEach((pass, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(pass, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<ProgramId, Map<Attachment, Boolean>> copyProgramFlipMap(Map<ProgramId, Map<Attachment, Boolean>> source) {
        Map<ProgramId, Map<Attachment, Boolean>> copy = new EnumMap<>(ProgramId.class);
        source.forEach((programId, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(programId, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }
}
