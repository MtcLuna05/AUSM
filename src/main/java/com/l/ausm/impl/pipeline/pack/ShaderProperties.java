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
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
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
        ShaderItemIdMap.ItemIdRules itemIds,
        ShaderRenderSettings renderSettings,
        Map<RenderPass, ShaderAlphaTest> alphaTests,
        Map<RenderPass, ShaderBlendMode> blendModes,
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
        Map<ProgramId, ShaderProgramDirectives> programDirectives,
        ShaderTextureDirectives textureDirectives,
        CustomUniformSet customUniforms,
        ShaderPackDirectives packDirectives,
        ShaderOitSettings oitSettings,
        Map<ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives,
        Map<ProgramArrayKey, String> programArrayEnabledExpressions
) {

    public static ShaderProperties load(ShaderPack pack) {
        return load(pack, Map.of());
    }

    public static ShaderProperties load(ShaderPack pack, Map<String, String> optionOverrides) {
        Map<RenderPass, List<Attachment>> drawBuffers = new EnumMap<>(RenderPass.class);
        Map<ProgramId, List<Attachment>> programDrawBuffers = new EnumMap<>(ProgramId.class);
        Map<ProgramArrayKey, List<Attachment>> programArrayDrawBuffers = new java.util.LinkedHashMap<>();
        Map<RenderPass, Map<Attachment, Boolean>> explicitFlips = new EnumMap<>(RenderPass.class);
        Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips = new EnumMap<>(ProgramId.class);
        Map<ProgramArrayKey, Map<Attachment, Boolean>> programArrayExplicitFlips = new java.util.LinkedHashMap<>();
        Map<RenderPass, ShaderViewportScale> viewportScales = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderViewportScale> programViewportScales = new EnumMap<>(ProgramId.class);
        Map<ProgramArrayKey, ShaderViewportScale> programArrayViewportScales = new java.util.LinkedHashMap<>();
        Map<ProgramKey, String> programEnabledExpressions = new java.util.LinkedHashMap<>();
        Map<ProgramArrayKey, String> programArrayEnabledExpressions = new java.util.LinkedHashMap<>();
        Map<String, ShaderScreen> screens = new java.util.LinkedHashMap<>();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        Map<String, String> translations = ShaderLang.load(pack, layout);
        ShaderBlockIdMap.BlockIdRules blockIds = ShaderBlockIdMap.load(pack, layout);
        Map<ResourceLocation, Integer> entityIds = ShaderEntityIdMap.load(pack, layout);
        ShaderItemIdMap.ItemIdRules itemIds = ShaderItemIdMap.load(pack, layout);
        Map<String, String> profiles = loadProfilesInFileOrder(pack, layout);

        if (!pack.hasResource(layout.propertiesPath())) {
            ShaderOptions options = ShaderOptionScanner.scan(pack, new Properties(), optionOverrides);
            programDrawBuffers.putAll(ShaderDrawBuffersScanner.scanProgramIds(pack, layout, options));
            adaptProgramDrawBuffers(programDrawBuffers, drawBuffers);
            ShaderRenderTargetSettings renderTargets = ShaderBufferFormatScanner.scan(pack, options);
            ShaderOitSettings oitSettings = ShaderOitSettings.empty();
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
                    oitSettings,
                    Map.of(),
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
                    ProgramArrayKey arrayKey = ProgramArrayKey.parse(programName);
                    if (arrayKey == null) {
                        MainMod.LOGGER.warn("[ShaderProperties] Ignoring drawBuffers for unknown program: {}", programName);
                        continue;
                    }
                    List<Attachment> attachments = parseDrawBuffers(properties.getProperty(key));
                    if (!attachments.isEmpty()) {
                        programArrayDrawBuffers.put(arrayKey, attachments);
                        MainMod.LOGGER.debug("[ShaderProperties] {} draw buffers: {}", programName, attachments);
                    }
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
                parseExplicitFlip(key, properties.getProperty(key), explicitFlips, programExplicitFlips, programArrayExplicitFlips);
                continue;
            }

            if (key.startsWith("scale.")) {
                parseViewportScale(key, properties.getProperty(key), viewportScales, programViewportScales, programArrayViewportScales);
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
        ShaderOitSettings oitSettings = parseOitSettings(properties);
        ShaderRenderTargetSettings renderTargets = applyOitRenderTargets(
                ShaderBufferFormatScanner.scan(pack, options),
                oitSettings
        );
        Map<ProgramId, ShaderProgramDirectives> programDirectives = inheritProgramDirectiveFallbacks(buildProgramDirectives(
                programDrawBuffers,
                programViewportScales,
                alphaTests.programModes(),
                blendModes.programModes(),
                blendModes.programAttachmentModes(),
                copyProgramFlipMap(programExplicitFlips),
                renderTargets
        ));
        Map<ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives = buildProgramArrayDirectives(
                programArrayDrawBuffers,
                programArrayViewportScales,
                alphaTests.arrayModes(),
                blendModes.arrayModes(),
                blendModes.arrayAttachmentModes(),
                copyProgramArrayFlipMap(programArrayExplicitFlips),
                renderTargets
        );
        ShaderRenderSettings renderSettings = ShaderRenderSettings.parse(properties);
        CustomUniformSet customUniforms = parseCustomUniforms(properties);
        List<ShaderImageDirective> images = parseImages(properties, options);
        Map<Integer, ShaderStorageBufferDirective> storageBuffers = parseStorageBuffers(properties, options);
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
                oitSettings,
                programArrayDirectives,
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
        String expression = null;
        for (int dimensionId : dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            expression = programEnabledExpressions.get(new ProgramKey(dimensionId, pass.programId()));
            if (expression != null) {
                break;
            }
        }
        if (expression == null) {
            expression = programEnabledExpressions.get(new ProgramKey(null, pass.programId()));
        }
        return ShaderExpression.evaluate(expression, options::booleanValue);
    }

    public boolean isProgramArrayEnabled(ProgramArrayId arrayId, String sourceName) {
        ProgramArrayKey sourceKey = ProgramArrayKey.parse(sourceName);
        int index = sourceKey == null ? 0 : sourceKey.index();
        String expression = null;
        for (int dimensionId : dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            expression = programArrayEnabledExpressions.get(new ProgramArrayKey(dimensionId, arrayId, index));
            if (expression != null) {
                break;
            }
        }
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

    public ShaderProgramDirectives directivesFor(ProgramArrayId arrayId, String sourceName) {
        if (arrayId == null) {
            return ShaderProgramDirectives.empty(ProgramId.PREPARE);
        }

        ProgramArrayKey sourceKey = ProgramArrayKey.parse(sourceName == null ? arrayId.sourcePrefix() : sourceName);
        int index = sourceKey == null ? 0 : sourceKey.index();
        ShaderProgramDirectives directives = null;
        for (int dimensionId : dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            directives = programArrayDirectives.get(new ProgramArrayKey(dimensionId, arrayId, index));
            if (directives != null) {
                break;
            }
        }
        if (directives == null) {
            directives = programArrayDirectives.get(new ProgramArrayKey(null, arrayId, index));
        }
        return directives == null ? ShaderProgramDirectives.empty(bindingPassForProgramArray(arrayId).programId()) : directives;
    }

    private static int[] dimensionFallbackOrder(int dimensionId) {
        if (dimensionId == 0) {
            return new int[]{0};
        }
        return new int[]{dimensionId, 0};
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
            Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips,
            Map<ProgramArrayKey, Map<Attachment, Boolean>> programArrayExplicitFlips
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
        ProgramArrayKey arrayKey = programId == null ? ProgramArrayKey.parse(programName) : null;
        Attachment attachment = Attachment.fromName(suffix.substring(dot + 1));
        if ((programId == null && arrayKey == null) || attachment == null) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring flip directive for unknown target: {}", key);
            return;
        }

        boolean parsedValue = Boolean.parseBoolean(value);
        if (pass != null) {
            explicitFlips
                    .computeIfAbsent(pass, ignored -> new EnumMap<>(Attachment.class))
                    .put(attachment, parsedValue);
        }
        if (programId != null) {
            programExplicitFlips
                    .computeIfAbsent(programId, ignored -> new EnumMap<>(Attachment.class))
                    .put(attachment, parsedValue);
        } else {
            programArrayExplicitFlips
                    .computeIfAbsent(arrayKey, ignored -> new EnumMap<>(Attachment.class))
                    .put(attachment, parsedValue);
        }
    }

    private static void parseViewportScale(
            String key,
            String value,
            Map<RenderPass, ShaderViewportScale> viewportScales,
            Map<ProgramId, ShaderViewportScale> programViewportScales,
            Map<ProgramArrayKey, ShaderViewportScale> programArrayViewportScales
    ) {
        String programName = key.substring("scale.".length());
        RenderPass pass = resolveProgramName(programName);
        ProgramId programId = resolveProgramId(programName);
        ProgramArrayKey arrayKey = programId == null ? ProgramArrayKey.parse(programName) : null;
        if (programId == null && arrayKey == null) {
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
            if (programId != null) {
                programViewportScales.put(programId, viewportScale);
            } else {
                programArrayViewportScales.put(arrayKey, viewportScale);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed scale directive: {}={}", key, value);
        }
    }

    private static AlphaTests parseAlphaTests(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderAlphaTest> alphaTests = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderAlphaTest> programAlphaTests = new EnumMap<>(ProgramId.class);
        Map<ProgramArrayKey, ShaderAlphaTest> arrayAlphaTests = new java.util.LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("alphaTest.")) {
                continue;
            }

            String programName = key.substring("alphaTest.".length());
            RenderPass pass = resolveProgramName(programName);
            ProgramId programId = resolveProgramId(programName);
            ProgramArrayKey arrayKey = programId == null ? ProgramArrayKey.parse(programName) : null;
            ShaderAlphaTest alphaTest = ShaderAlphaTest.parse(properties.getProperty(key), options);
            if ((programId == null && arrayKey == null) || alphaTest == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed alphaTest directive: {}={}", key, properties.getProperty(key));
                continue;
            }
            if (pass != null) {
                alphaTests.put(pass, alphaTest);
            }
            if (programId != null) {
                programAlphaTests.put(programId, alphaTest);
            } else {
                arrayAlphaTests.put(arrayKey, alphaTest);
            }
        }
        return new AlphaTests(Map.copyOf(alphaTests), Map.copyOf(programAlphaTests), Map.copyOf(arrayAlphaTests));
    }

    private static BlendModes parseBlendModes(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderBlendMode> blendModes = new EnumMap<>(RenderPass.class);
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderBlendMode> programBlendModes = new EnumMap<>(ProgramId.class);
        Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes = new EnumMap<>(ProgramId.class);
        Map<ProgramArrayKey, ShaderBlendMode> arrayBlendModes = new java.util.LinkedHashMap<>();
        Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> arrayAttachmentModes = new java.util.LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("blend.")) {
                continue;
            }

            String suffix = key.substring("blend.".length());
            int targetSeparator = suffix.lastIndexOf('.');
            String programName = targetSeparator < 0 ? suffix : suffix.substring(0, targetSeparator);
            RenderPass pass = resolveProgramName(programName);
            ProgramId programId = resolveProgramId(programName);
            ProgramArrayKey arrayKey = programId == null ? ProgramArrayKey.parse(programName) : null;
            ShaderBlendMode blendMode = ShaderBlendMode.parse(properties.getProperty(key), options);
            if (programId == null && programName.startsWith("clrwl_")) {
                continue;
            }
            if ((programId == null && arrayKey == null) || blendMode == null) {
                MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed blend directive: {}={}", key, properties.getProperty(key));
                continue;
            }

            if (targetSeparator < 0) {
                if (pass != null) {
                    blendModes.put(pass, blendMode);
                }
                if (programId != null) {
                    programBlendModes.put(programId, blendMode);
                } else {
                    arrayBlendModes.put(arrayKey, blendMode);
                }
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
            if (programId != null) {
                programAttachmentModes
                        .computeIfAbsent(programId, ignored -> new EnumMap<>(Attachment.class))
                        .put(attachment, blendMode);
            } else {
                arrayAttachmentModes
                        .computeIfAbsent(arrayKey, ignored -> new EnumMap<>(Attachment.class))
                        .put(attachment, blendMode);
            }
        }
        return new BlendModes(
                Map.copyOf(blendModes),
                copyBlendAttachmentMap(attachmentModes),
                Map.copyOf(programBlendModes),
                copyProgramBlendAttachmentMap(programAttachmentModes),
                Map.copyOf(arrayBlendModes),
                copyProgramArrayBlendAttachmentMap(arrayAttachmentModes)
        );
    }

    private static ShaderOitSettings parseOitSettings(Properties properties) {
        boolean enabled = parseBooleanProperty(properties.getProperty("oit"), false);
        List<Integer> coefficientRanks = parseIntegerList(properties.getProperty("oit.gbuffers.coefficientRanks"));
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
            enabled = enabled || parseBooleanProperty(properties.getProperty("oit.gbuffers"), false);
        }
        return new ShaderOitSettings(
                enabled,
                List.copyOf(coefficientRanks),
                Map.copyOf(gbufferBuffers),
                Map.copyOf(gbufferFormats)
        );
    }

    private static ShaderRenderTargetSettings applyOitRenderTargets(ShaderRenderTargetSettings renderTargets, ShaderOitSettings oitSettings) {
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

    private static boolean parseBooleanProperty(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "on", "1", "yes" -> true;
            case "false", "off", "0", "no" -> false;
            default -> fallback;
        };
    }

    private static List<Integer> parseIntegerList(String value) {
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

    private static List<ShaderImageDirective> parseImages(Properties properties, ShaderOptions options) {
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
                            parseDirectiveFloat(parts[6], options),
                            parseDirectiveFloat(parts[7], options)
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
                            parseDirectiveInt(parts[6], options),
                            parts.length > 7 ? parseDirectiveInt(parts[7], options) : 0,
                            parts.length > 8 ? parseDirectiveInt(parts[8], options) : 0,
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

    private static Map<Integer, ShaderStorageBufferDirective> parseStorageBuffers(Properties properties, ShaderOptions options) {
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
                long size = parseDirectiveLong(parts[0], options);
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
                            parseDirectiveFloat(parts[2], options),
                            parseDirectiveFloat(parts[3], options),
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

    private static Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> copyProgramArrayBlendAttachmentMap(
            Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> source
    ) {
        Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> copy = new java.util.LinkedHashMap<>();
        source.forEach((arrayKey, modes) -> {
            if (!modes.isEmpty()) {
                copy.put(arrayKey, Map.copyOf(modes));
            }
        });
        return Map.copyOf(copy);
    }

    private record BlendModes(
            Map<RenderPass, ShaderBlendMode> passModes,
            Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes,
            Map<ProgramId, ShaderBlendMode> programModes,
            Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes,
            Map<ProgramArrayKey, ShaderBlendMode> arrayModes,
            Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> arrayAttachmentModes
    ) {
    }

    private record AlphaTests(
            Map<RenderPass, ShaderAlphaTest> passModes,
            Map<ProgramId, ShaderAlphaTest> programModes,
            Map<ProgramArrayKey, ShaderAlphaTest> arrayModes
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

    private static Map<ProgramArrayKey, ShaderProgramDirectives> buildProgramArrayDirectives(
            Map<ProgramArrayKey, List<Attachment>> drawBuffers,
            Map<ProgramArrayKey, ShaderViewportScale> viewportScales,
            Map<ProgramArrayKey, ShaderAlphaTest> alphaTests,
            Map<ProgramArrayKey, ShaderBlendMode> blendModes,
            Map<ProgramArrayKey, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
            Map<ProgramArrayKey, Map<Attachment, Boolean>> explicitFlips,
            ShaderRenderTargetSettings renderTargets
    ) {
        java.util.LinkedHashSet<ProgramArrayKey> keys = new java.util.LinkedHashSet<>();
        keys.addAll(drawBuffers.keySet());
        keys.addAll(viewportScales.keySet());
        keys.addAll(alphaTests.keySet());
        keys.addAll(blendModes.keySet());
        keys.addAll(attachmentBlendModes.keySet());
        keys.addAll(explicitFlips.keySet());

        Map<ProgramArrayKey, ShaderProgramDirectives> directives = new java.util.LinkedHashMap<>();
        for (ProgramArrayKey key : keys) {
            RenderPass bindingPass = bindingPassForProgramArray(key.arrayId());
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

    private static RenderPass bindingPassForProgramArray(ProgramArrayId arrayId) {
        return switch (arrayId) {
            case SETUP, BEGIN, PREPARE -> RenderPass.PREPARE;
            case DEFERRED -> RenderPass.DEFERRED;
            case COMPOSITE -> RenderPass.COMPOSITE;
            case SHADOWCOMP -> RenderPass.SHADOW;
        };
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
        public static ProgramArrayKey parse(String rawName) {
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

    private static int parseDirectiveInt(String token, ShaderOptions options) {
        double value = parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        int rounded = (int) Math.round(value);
        if (Math.abs(value - rounded) > 0.0001d) {
            throw new NumberFormatException(token);
        }
        return rounded;
    }

    private static long parseDirectiveLong(String token, ShaderOptions options) {
        double value = parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > 0.0001d) {
            throw new NumberFormatException(token);
        }
        return rounded;
    }

    private static float parseDirectiveFloat(String token, ShaderOptions options) {
        double value = parseDirectiveDouble(token, options);
        if (!Double.isFinite(value)) {
            throw new NumberFormatException(token);
        }
        return (float) value;
    }

    private static double parseDirectiveDouble(String token, ShaderOptions options) {
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

    private static Map<ProgramArrayKey, Map<Attachment, Boolean>> copyProgramArrayFlipMap(
            Map<ProgramArrayKey, Map<Attachment, Boolean>> source
    ) {
        Map<ProgramArrayKey, Map<Attachment, Boolean>> copy = new java.util.LinkedHashMap<>();
        source.forEach((arrayKey, flips) -> {
            if (!flips.isEmpty()) {
                copy.put(arrayKey, Map.copyOf(flips));
            }
        });
        return Map.copyOf(copy);
    }
}
