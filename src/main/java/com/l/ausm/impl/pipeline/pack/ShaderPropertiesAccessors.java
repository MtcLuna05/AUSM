package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.l.ausm.api.pipeline.pack.ShaderImageDirective;
import com.l.ausm.api.pipeline.pack.ShaderOitSettings;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.l.ausm.api.pipeline.pack.ShaderRawTextureDirective;
import com.l.ausm.api.pipeline.pack.ShaderRenderSettings;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderScreen;
import com.l.ausm.api.pipeline.pack.ShaderStorageBufferDirective;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderIndirectPointer;
import com.l.ausm.api.pipeline.shader.ShaderProgramArrayKey;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import net.minecraft.util.ResourceLocation;

abstract class ShaderPropertiesAccessors extends ShaderPropertiesBase {
    public Map<RenderPass, List<Attachment>> drawBuffers() {
        return drawBuffers;
    }

    public Map<ShaderProperties.ProgramKey, String> programEnabledExpressions() {
        return programEnabledExpressions;
    }

    public ShaderOptions options() {
        return options;
    }

    public Map<String, ShaderScreen> screens() {
        return screens;
    }

    public Map<String, String> profiles() {
        return profiles;
    }

    public ShaderRenderTargetSettings renderTargets() {
        return renderTargets;
    }

    public List<ShaderCustomTextureBinding> globalTextures() {
        return globalTextures;
    }

    public Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures() {
        return passTextures;
    }

    public Map<RenderPass, Map<Attachment, Boolean>> explicitFlips() {
        return explicitFlips;
    }

    public Map<RenderPass, ShaderViewportScale> viewportScales() {
        return viewportScales;
    }

    public Map<String, String> translations() {
        return translations;
    }

    public ShaderBlockIdMap.BlockIdRules blockIds() {
        return blockIds;
    }

    public Map<ResourceLocation, Integer> entityIds() {
        return entityIds;
    }

    public ShaderItemIdMap.ItemIdRules itemIds() {
        return itemIds;
    }

    public ShaderRenderSettings renderSettings() {
        return renderSettings;
    }

    public Map<RenderPass, ShaderAlphaTest> alphaTests() {
        return alphaTests;
    }

    public Map<RenderPass, ShaderBlendMode> blendModes() {
        return blendModes;
    }

    public Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentBlendModes() {
        return attachmentBlendModes;
    }

    public Map<ProgramId, ShaderProgramDirectives> programDirectives() {
        return programDirectives;
    }

    public ShaderTextureDirectives textureDirectives() {
        return textureDirectives;
    }

    public CustomUniformSet customUniforms() {
        return customUniforms;
    }

    public ShaderPackDirectives packDirectives() {
        return packDirectives;
    }

    public ShaderOitSettings oitSettings() {
        return oitSettings;
    }

    public Map<ShaderProperties.ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives() {
        return programArrayDirectives;
    }

    public Map<ShaderProperties.ProgramArrayKey, String> programArrayEnabledExpressions() {
        return programArrayEnabledExpressions;
    }

    public Map<String, ShaderIndirectPointer> indirectPointers() {
        return indirectPointers;
    }

    @Override
    public boolean equals(Object value) {
        if (self() == value) {
            return true;
        }
        if (!(value instanceof ShaderProperties other)) {
            return false;
        }
        return Objects.equals(drawBuffers, other.drawBuffers)
                && Objects.equals(programEnabledExpressions, other.programEnabledExpressions)
                && Objects.equals(options, other.options)
                && Objects.equals(screens, other.screens)
                && Objects.equals(profiles, other.profiles)
                && Objects.equals(renderTargets, other.renderTargets)
                && Objects.equals(globalTextures, other.globalTextures)
                && Objects.equals(passTextures, other.passTextures)
                && Objects.equals(explicitFlips, other.explicitFlips)
                && Objects.equals(viewportScales, other.viewportScales)
                && Objects.equals(translations, other.translations)
                && Objects.equals(blockIds, other.blockIds)
                && Objects.equals(entityIds, other.entityIds)
                && Objects.equals(itemIds, other.itemIds)
                && Objects.equals(renderSettings, other.renderSettings)
                && Objects.equals(alphaTests, other.alphaTests)
                && Objects.equals(blendModes, other.blendModes)
                && Objects.equals(attachmentBlendModes, other.attachmentBlendModes)
                && Objects.equals(programDirectives, other.programDirectives)
                && Objects.equals(textureDirectives, other.textureDirectives)
                && Objects.equals(customUniforms, other.customUniforms)
                && Objects.equals(packDirectives, other.packDirectives)
                && Objects.equals(oitSettings, other.oitSettings)
                && Objects.equals(programArrayDirectives, other.programArrayDirectives)
                && Objects.equals(programArrayEnabledExpressions, other.programArrayEnabledExpressions)
                && Objects.equals(indirectPointers, other.indirectPointers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(drawBuffers, programEnabledExpressions, options, screens, profiles, renderTargets, globalTextures, passTextures, explicitFlips, viewportScales, translations, blockIds, entityIds, itemIds, renderSettings, alphaTests, blendModes, attachmentBlendModes, programDirectives, textureDirectives, customUniforms, packDirectives, oitSettings, programArrayDirectives, programArrayEnabledExpressions, indirectPointers);
    }

    @Override
    public String toString() {
        return "ShaderProperties[" + "drawBuffers=" + drawBuffers + ", " + "programEnabledExpressions=" + programEnabledExpressions + ", " + "options=" + options + ", " + "screens=" + screens + ", " + "profiles=" + profiles + ", " + "renderTargets=" + renderTargets + ", " + "globalTextures=" + globalTextures + ", " + "passTextures=" + passTextures + ", " + "explicitFlips=" + explicitFlips + ", " + "viewportScales=" + viewportScales + ", " + "translations=" + translations + ", " + "blockIds=" + blockIds + ", " + "entityIds=" + entityIds + ", " + "itemIds=" + itemIds + ", " + "renderSettings=" + renderSettings + ", " + "alphaTests=" + alphaTests + ", " + "blendModes=" + blendModes + ", " + "attachmentBlendModes=" + attachmentBlendModes + ", " + "programDirectives=" + programDirectives + ", " + "textureDirectives=" + textureDirectives + ", " + "customUniforms=" + customUniforms + ", " + "packDirectives=" + packDirectives + ", " + "oitSettings=" + oitSettings + ", " + "programArrayDirectives=" + programArrayDirectives + ", " + "programArrayEnabledExpressions=" + programArrayEnabledExpressions + ", " + "indirectPointers=" + indirectPointers + "]";
    }

    public static ShaderProperties load(ShaderPack pack) {
        return ShaderProperties.load(pack, Map.of());
    }

    public static ShaderProperties load(ShaderPack pack, Map<String, String> optionOverrides) {
        Map<RenderPass, List<Attachment>> drawBuffers = new EnumMap<>(RenderPass.class);
        Map<ProgramId, List<Attachment>> programDrawBuffers = new EnumMap<>(ProgramId.class);
        Map<ShaderProperties.ProgramArrayKey, List<Attachment>> programArrayDrawBuffers = new LinkedHashMap<>();
        Map<RenderPass, Map<Attachment, Boolean>> explicitFlips = new EnumMap<>(RenderPass.class);
        Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips = new EnumMap<>(ProgramId.class);
        Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> programArrayExplicitFlips = new LinkedHashMap<>();
        Map<RenderPass, ShaderViewportScale> viewportScales = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderViewportScale> programViewportScales = new EnumMap<>(ProgramId.class);
        Map<ShaderProperties.ProgramArrayKey, ShaderViewportScale> programArrayViewportScales = new LinkedHashMap<>();
        Map<ShaderProperties.ProgramKey, String> programEnabledExpressions = new LinkedHashMap<>();
        Map<ShaderProperties.ProgramArrayKey, String> programArrayEnabledExpressions = new LinkedHashMap<>();
        Map<String, ShaderScreen> screens = new LinkedHashMap<>();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        Map<String, String> translations = ShaderLang.load(pack, layout);
        ShaderBlockIdMap.BlockIdRules blockIds = ShaderBlockIdMap.load(pack, layout);
        Map<ResourceLocation, Integer> entityIds = ShaderEntityIdMap.load(pack, layout);
        ShaderItemIdMap.ItemIdRules itemIds = ShaderItemIdMap.load(pack, layout);
        Map<String, String> profiles = ShaderProperties.loadProfilesInFileOrder(pack, layout);

        if (!pack.hasResource(layout.propertiesPath())) {
            ShaderOptions options = ShaderOptionScanner.scan(pack, new Properties(), optionOverrides);
            programDrawBuffers.putAll(ShaderDrawBuffersScanner.scanProgramIds(pack, layout, options));
            ShaderProperties.adaptProgramDrawBuffers(programDrawBuffers, drawBuffers);
            ShaderRenderTargetSettings renderTargets = ShaderBufferFormatScanner.scan(pack, options);
            ShaderOitSettings oitSettings = ShaderOitSettings.empty();
            Map<ProgramId, ShaderProgramDirectives> programDirectives = ShaderProperties.inheritProgramDirectiveFallbacks(
                    ShaderProperties.buildProgramDirectives(programDrawBuffers, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), renderTargets)
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
                    null,
                    programDirectives,
                    CustomUniformSet.empty()
            );
            packDirectives = packDirectives.withCapabilities(ShaderPipelineCapabilities.from(packDirectives));
            return new ShaderProperties(
                    drawBuffers,
                    programEnabledExpressions,
                    options,
                    ShaderProperties.defaultScreens(options),
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
                RenderPass pass = ShaderProperties.resolveProgramName(programName);
                ProgramId programId = ShaderProperties.resolveProgramId(programName);
                if (programId == null) {
                    ShaderProperties.ProgramArrayKey arrayKey = ShaderProperties.ProgramArrayKey.parse(programName);
                    if (arrayKey == null) {
                        MainMod.LOGGER.warn("[ShaderProperties] Ignoring drawBuffers for unknown program: {}", programName);
                        continue;
                    }
                    List<Attachment> attachments = ShaderProperties.parseDrawBuffers(properties.getProperty(key));
                    if (!attachments.isEmpty()) {
                        programArrayDrawBuffers.put(arrayKey, attachments);
                        MainMod.LOGGER.debug("[ShaderProperties] {} draw buffers: {}", programName, attachments);
                    }
                    continue;
                }

                List<Attachment> attachments = ShaderProperties.parseDrawBuffers(properties.getProperty(key));
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
                ShaderProperties.parseExplicitFlip(key, properties.getProperty(key), explicitFlips, programExplicitFlips, programArrayExplicitFlips);
                continue;
            }

            if (key.startsWith("scale.")) {
                ShaderProperties.parseViewportScale(key, properties.getProperty(key), viewportScales, programViewportScales, programArrayViewportScales);
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
            RenderPass pass = ShaderProperties.resolveProgramName(programName);
            if (pass == null) {
                ShaderProperties.ProgramArrayKey arrayKey = ShaderProperties.ProgramArrayKey.parse(programName);
                if (arrayKey != null) {
                    programArrayEnabledExpressions.put(arrayKey, properties.getProperty(key));
                } else {
                    MainMod.LOGGER.warn("[ShaderProperties] Ignoring enabled expression for unknown program: {}", programName);
                }
                continue;
            }
            programEnabledExpressions.put(ShaderProperties.ProgramKey.parse(programName, pass.programId()), properties.getProperty(key));
        }

        ShaderProperties.parseScreens(properties, screens);
        if (screens.isEmpty()) {
            screens.putAll(ShaderProperties.defaultScreens(options));
        }

        ShaderDrawBuffersScanner.scanProgramIds(pack, layout, options).forEach(programDrawBuffers::putIfAbsent);
        ShaderProperties.adaptProgramDrawBuffers(programDrawBuffers, drawBuffers);

        Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures = new EnumMap<>(RenderPass.class);
        Map<ProgramId, List<ShaderCustomTextureBinding>> programTextures = new EnumMap<>(ProgramId.class);
        Map<ShaderProgramArrayKey, List<ShaderCustomTextureBinding>> programArrayTextures = new LinkedHashMap<>();
        List<ShaderRawTextureDirective> rawTextures = new ArrayList<>();
        Map<ProgramId, List<ShaderRawTextureDirective>> programRawTextures = new EnumMap<>(ProgramId.class);
        Map<ShaderProgramArrayKey, List<ShaderRawTextureDirective>> programArrayRawTextures = new LinkedHashMap<>();
        List<ShaderCustomTextureBinding> globalTextures = ShaderProperties.parseCustomTextures(
                pack,
                layout,
                properties,
                passTextures,
                programTextures,
                programArrayTextures,
                rawTextures,
                programRawTextures,
                programArrayRawTextures
        );
        ShaderTextureDirectives textureDirectives = new ShaderTextureDirectives(
                globalTextures,
                ShaderProperties.copyProgramTextureMap(programTextures),
                ShaderProperties.copyProgramArrayTextureMap(programArrayTextures),
                List.copyOf(rawTextures),
                ShaderProperties.copyProgramRawTextureMap(programRawTextures),
                ShaderProperties.copyProgramArrayRawTextureMap(programArrayRawTextures)
        );

        ShaderProperties.BlendModes blendModes = ShaderProperties.parseBlendModes(properties, options);
        ShaderProperties.AlphaTests alphaTests = ShaderProperties.parseAlphaTests(properties, options);
        ShaderOitSettings oitSettings = ShaderProperties.parseOitSettings(properties);
        ShaderRenderTargetSettings renderTargets = ShaderProperties.applyOitRenderTargets(
                ShaderBufferFormatScanner.scan(pack, options),
                oitSettings
        );
        Map<ProgramId, ShaderProgramDirectives> programDirectives = ShaderProperties.inheritProgramDirectiveFallbacks(ShaderProperties.buildProgramDirectives(
                programDrawBuffers,
                programViewportScales,
                alphaTests.programModes(),
                blendModes.programModes(),
                blendModes.programAttachmentModes(),
                ShaderProperties.copyProgramFlipMap(programExplicitFlips),
                renderTargets
        ));
        Map<ShaderProperties.ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives = ShaderProperties.buildProgramArrayDirectives(
                programArrayDrawBuffers,
                programArrayViewportScales,
                alphaTests.arrayModes(),
                blendModes.arrayModes(),
                blendModes.arrayAttachmentModes(),
                ShaderProperties.copyProgramArrayFlipMap(programArrayExplicitFlips),
                renderTargets
        );
        ShaderRenderSettings renderSettings = ShaderRenderSettings.parse(properties);
        CustomUniformSet customUniforms = ShaderProperties.parseCustomUniforms(properties);
        List<ShaderImageDirective> images = ShaderProperties.parseImages(properties, options);
        Map<Integer, ShaderStorageBufferDirective> storageBuffers = ShaderProperties.parseStorageBuffers(properties, options);
        Map<String, ShaderIndirectPointer> indirectPointers = ShaderProperties.parseIndirectPointers(properties);
        ShaderFeatureSet features = ShaderFeatureSet.parse(properties);
        int noiseTextureResolution = ShaderProperties.parsePositiveInt(properties.getProperty("noiseTextureResolution"), 256);
        ShaderCustomTextureBinding noiseTexture = ShaderProperties.parseNoiseTexture(pack, layout, properties);
        ShaderPackDirectives packDirectives = new ShaderPackDirectives(
                renderTargets,
                renderSettings,
                textureDirectives,
                ShaderComputeDirectives.empty(),
                images,
                storageBuffers,
                features,
                noiseTextureResolution,
                noiseTexture,
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
                ShaderProperties.copyTextureMap(passTextures),
                ShaderProperties.copyFlipMap(explicitFlips),
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
                Map.copyOf(programArrayEnabledExpressions),
                indirectPointers
        );
    }

    public ShaderIndirectPointer indirectPointer(String sourceName) {
        return indirectPointers.get(sourceName);
    }

    public String translate(String key, String fallback) {
        return translations.getOrDefault(key, fallback);
    }

    public Map<Attachment, ColorBufferFormat> bufferFormats() {
        return renderTargets.formats();
    }

    public boolean isProgramEnabled(RenderPass pass) {
        String expression = null;
        for (int dimensionId : ShaderProperties.dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            expression = programEnabledExpressions.get(new ShaderProperties.ProgramKey(dimensionId, pass.programId()));
            if (expression != null) {
                break;
            }
        }
        if (expression == null) {
            expression = programEnabledExpressions.get(new ShaderProperties.ProgramKey(null, pass.programId()));
        }
        return ShaderExpression.evaluate(expression, options::booleanValue);
    }

    public boolean isProgramArrayEnabled(ProgramArrayId arrayId, String sourceName) {
        ShaderProperties.ProgramArrayKey sourceKey = ShaderProperties.ProgramArrayKey.parse(sourceName);
        int index = sourceKey == null ? 0 : sourceKey.index();
        String expression = null;
        for (int dimensionId : ShaderProperties.dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            expression = programArrayEnabledExpressions.get(new ShaderProperties.ProgramArrayKey(dimensionId, arrayId, index));
            if (expression != null) {
                break;
            }
        }
        if (expression == null) {
            expression = programArrayEnabledExpressions.get(new ShaderProperties.ProgramArrayKey(null, arrayId, index));
        }
        return ShaderExpression.evaluate(expression, options::booleanValue);
    }

    public ShaderProgramDirectives directivesFor(RenderPass pass) {
        return self().directivesFor(pass.programId());
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

        ShaderProperties.ProgramArrayKey sourceKey = ShaderProperties.ProgramArrayKey.parse(sourceName == null ? arrayId.sourcePrefix() : sourceName);
        int index = sourceKey == null ? 0 : sourceKey.index();
        ShaderProgramDirectives directives = null;
        for (int dimensionId : ShaderProperties.dimensionFallbackOrder(ShaderDimensionContext.currentDimensionId())) {
            directives = programArrayDirectives.get(new ShaderProperties.ProgramArrayKey(dimensionId, arrayId, index));
            if (directives != null) {
                break;
            }
        }
        if (directives == null) {
            directives = programArrayDirectives.get(new ShaderProperties.ProgramArrayKey(null, arrayId, index));
        }
        return directives == null ? ShaderProgramDirectives.empty(ShaderProperties.bindingPassForProgramArray(arrayId).programId()) : directives;
    }

    protected static int[] dimensionFallbackOrder(int dimensionId) {
        if (dimensionId == 0) {
            return new int[]{0};
        }
        return new int[]{dimensionId, 0};
    }

    protected static RenderPass resolveProgramName(String programName) {
        ProgramId programId = ShaderProperties.resolveProgramId(programName);
        return programId == null ? null : RenderPass.fromProgramId(programId);
    }

    protected static ProgramId resolveProgramId(String programName) {
        programName = ShaderProperties.normalizeProgramName(programName);
        ProgramId programId = ProgramId.fromSourceName(programName);
        if (programId != null) {
            return programId;
        }
        String indexedName = ShaderProperties.normalizeCompactIndexedProgramName(programName);
        return indexedName.equals(programName) ? null : ProgramId.fromSourceName(indexedName);
    }

    protected static String normalizeProgramName(String programName) {
        int slash = programName.lastIndexOf('/');
        if (slash >= 0 && slash < programName.length() - 1) {
            return programName.substring(slash + 1);
        }
        return programName;
    }

    protected static String normalizeCompactIndexedProgramName(String programName) {
        for (String prefix : List.of("deferred", "composite")) {
            if (programName.startsWith(prefix) && programName.length() > prefix.length()) {
                String suffix = programName.substring(prefix.length());
                if (suffix.chars().allMatch(Character::isDigit)) {
                    return prefix + "_" + suffix;
                }
            }
        }
        return programName;
    }

    protected static void adaptProgramDrawBuffers(Map<ProgramId, List<Attachment>> source, Map<RenderPass, List<Attachment>> target) {
        for (RenderPass pass : RenderPass.values()) {
            List<Attachment> attachments = source.get(pass.programId());
            if (attachments != null && !attachments.isEmpty()) {
                target.putIfAbsent(pass, attachments);
            }
        }
    }

    protected static void parseExplicitFlip(
            String key,
            String value,
            Map<RenderPass, Map<Attachment, Boolean>> explicitFlips,
            Map<ProgramId, Map<Attachment, Boolean>> programExplicitFlips,
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, Boolean>> programArrayExplicitFlips
    ) {
        String suffix = key.substring("flip.".length());
        int dot = suffix.lastIndexOf('.');
        if (dot <= 0 || dot >= suffix.length() - 1) {
            MainMod.LOGGER.warn("[ShaderProperties] Ignoring malformed flip directive: {}", key);
            return;
        }

        String programName = suffix.substring(0, dot);
        RenderPass pass = ShaderProperties.resolveProgramName(programName);
        ProgramId programId = ShaderProperties.resolveProgramId(programName);
        ShaderProperties.ProgramArrayKey arrayKey = programId == null ? ShaderProperties.ProgramArrayKey.parse(programName) : null;
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

    protected static void parseViewportScale(
            String key,
            String value,
            Map<RenderPass, ShaderViewportScale> viewportScales,
            Map<ProgramId, ShaderViewportScale> programViewportScales,
            Map<ShaderProperties.ProgramArrayKey, ShaderViewportScale> programArrayViewportScales
    ) {
        String programName = key.substring("scale.".length());
        RenderPass pass = ShaderProperties.resolveProgramName(programName);
        ProgramId programId = ShaderProperties.resolveProgramId(programName);
        ShaderProperties.ProgramArrayKey arrayKey = programId == null ? ShaderProperties.ProgramArrayKey.parse(programName) : null;
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

    protected static ShaderProperties.AlphaTests parseAlphaTests(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderAlphaTest> alphaTests = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderAlphaTest> programAlphaTests = new EnumMap<>(ProgramId.class);
        Map<ShaderProperties.ProgramArrayKey, ShaderAlphaTest> arrayAlphaTests = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("alphaTest.")) {
                continue;
            }

            String programName = key.substring("alphaTest.".length());
            RenderPass pass = ShaderProperties.resolveProgramName(programName);
            ProgramId programId = ShaderProperties.resolveProgramId(programName);
            ShaderProperties.ProgramArrayKey arrayKey = programId == null ? ShaderProperties.ProgramArrayKey.parse(programName) : null;
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
        return new ShaderProperties.AlphaTests(Map.copyOf(alphaTests), Map.copyOf(programAlphaTests), Map.copyOf(arrayAlphaTests));
    }

    protected static ShaderProperties.BlendModes parseBlendModes(Properties properties, ShaderOptions options) {
        Map<RenderPass, ShaderBlendMode> blendModes = new EnumMap<>(RenderPass.class);
        Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes = new EnumMap<>(RenderPass.class);
        Map<ProgramId, ShaderBlendMode> programBlendModes = new EnumMap<>(ProgramId.class);
        Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes = new EnumMap<>(ProgramId.class);
        Map<ShaderProperties.ProgramArrayKey, ShaderBlendMode> arrayBlendModes = new LinkedHashMap<>();
        Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> arrayAttachmentModes = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("blend.")) {
                continue;
            }

            String suffix = key.substring("blend.".length());
            int targetSeparator = suffix.lastIndexOf('.');
            String programName = targetSeparator < 0 ? suffix : suffix.substring(0, targetSeparator);
            RenderPass pass = ShaderProperties.resolveProgramName(programName);
            ProgramId programId = ShaderProperties.resolveProgramId(programName);
            ShaderProperties.ProgramArrayKey arrayKey = programId == null ? ShaderProperties.ProgramArrayKey.parse(programName) : null;
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
        return new ShaderProperties.BlendModes(
                Map.copyOf(blendModes),
                ShaderProperties.copyBlendAttachmentMap(attachmentModes),
                Map.copyOf(programBlendModes),
                ShaderProperties.copyProgramBlendAttachmentMap(programAttachmentModes),
                Map.copyOf(arrayBlendModes),
                ShaderProperties.copyProgramArrayBlendAttachmentMap(arrayAttachmentModes)
        );
    }
}
