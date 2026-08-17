package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.shader.ComputeProgramSource;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderProgramSource;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderLoadingScreen;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.shader.ComputeProgram;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.l.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.pipeline.shader.ShaderCompiler;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.activeConstSetting;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.changedOptionValue;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.optionValue;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.parseIntValue;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.settingValueWithComment;

abstract class PipelineRuntimeFramebufferState extends PipelineRuntimeLightingState {
    protected void applyFallbackDefaultDrawBuffers() {
        for (PipelineProgram program : programs.values()) {
            self().applyFallbackDefaultDrawBuffers(program);
        }
        for (Map.Entry<ProgramArrayId, List<FullscreenArrayProgram>> entry : fullscreenArrayPrograms.entrySet()) {
            for (FullscreenArrayProgram program : entry.getValue()) {
                self().applyFallbackDefaultDrawBuffers(program);
            }
        }
    }

    protected void applyFallbackDefaultDrawBuffers(PipelineProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(self().defaultDrawBuffers(program.stage()));
    }

    protected void applyFallbackDefaultDrawBuffers(FullscreenArrayProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(program.arrayId() == ProgramArrayId.SHADOWCOMP
                ? List.of(Attachment.COLOR)
                : List.of(self().fallbackColorAttachment()));
    }

    protected List<Attachment> defaultDrawBuffers(ProgramStage stage) {
        return switch (stage) {
            case PREPARE, GBUFFERS, DEFERRED, COMPOSITE -> List.of(self().fallbackColorAttachment());
            case SHADOW -> List.of(Attachment.COLOR);
            case FINAL, NONE -> List.of();
        };
    }

    protected Attachment fallbackColorAttachment() {
        int index = shaderProperties != null ? shaderProperties.renderSettings().fallbackTex() : 0;
        Attachment attachment = Attachment.fromColorIndex(index);
        return attachment != null ? attachment : Attachment.COLOR;
    }

    protected PipelineRuntimeState.CompiledPipelineState removeCachedCompiledPipeline(String cacheKey) {
        return cacheKey == null ? null : compiledPipelineCache.remove(cacheKey);
    }

    protected void deleteCachedCompiledPipeline(String cacheKey) {
        PipelineRuntimeState.CompiledPipelineState state = self().removeCachedCompiledPipeline(cacheKey);
        if (state != null) {
            state.delete();
        }
    }

    protected void deleteCachedCompiledPipelines() {
        compiledPipelineCache.values().forEach(PipelineRuntimeState.CompiledPipelineState::delete);
        compiledPipelineCache.clear();
    }

    protected boolean isInternalPipelinePack() {
        return "(internal)".equals(activePackName);
    }

    protected void applyPackStartupTerrainFallback(String stage) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS || !isPipelineActive || !self().shouldStartWithSoftVanillaTerrain()) {
            return;
        }
        self().activateSoftVanillaTerrainRenderer("pack-startup-" + self().terrainFallbackPackKey() + ":" + stage);
    }

    protected boolean shouldStartWithSoftVanillaTerrain() {
        return ENABLE_SAFE_TERRAIN_FALLBACKS && self().isComplementarySoftVanillaStartupPack();
    }

    protected boolean shouldPresentPreCompositeForSoftVanillaStartupPack() {
        return ENABLE_SAFE_TERRAIN_FALLBACKS && self().isComplementarySoftVanillaStartupFallbackActive();
    }

    protected boolean shouldPresentPreCompositeForNothiriumCompositeLoss() {
        return false;
    }

    protected boolean shouldSuppressShadowMapForSoftVanillaStartupPack() {
        return false;
    }

    protected boolean isComplementarySoftVanillaStartupFallbackActive() {
        return isPipelineActive && softVanillaTerrainRenderer && self().isComplementarySoftVanillaStartupPack();
    }

    protected boolean isComplementarySoftVanillaStartupPack() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        return name.contains("complementaryunbound")
                || name.contains("complimentary entree")
                || name.contains("complementary entree");
    }

    protected String terrainFallbackPackKey() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        if (name.contains("complementaryunbound")) {
            return "unbound";
        }
        if (name.contains("complimentary entree") || name.contains("complementary entree")) {
            return "entree";
        }
        return "shaderpack";
    }

    protected void resetTransientWorldRenderState() {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        passStack.clear();
        dynamicBlockEntityIdStack.clear();
        dynamicBlockEntityId = -1;
        worldPassBypassStack.clear();
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        currentWorldPassSerial = Long.MIN_VALUE;
        shaderlessWorldPassActive = false;
        worldFrameActive = false;
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        sparseStartupPresentationHoldFrames = 0;
        self().clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
    }

    protected void initializeBlankShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        if (!PipelineRuntimeState.shouldCreateShadowFramebuffer(pack, properties)) {
            return;
        }

        String resolutionValue = settingValueWithComment(pack, properties, "shadowMapResolution", "SHADOWRES");
        int resolution = parseIntValue(resolutionValue, 1024);
        resolution = Math.clamp(resolution, 16, 8192);
        shadowFramebuffer = new ShadowFramebuffer(resolution, packDirectives.renderTargets());
        shadowMapPopulated = false;
        shadowMapUsable = false;
        shadowMapSparseForSampling = false;
        shadowMapCoverageStableFrames = 0;
        shadowMapCoverageRegressionLogs = 0;
        invalidShadowTerrainFrames = 0;
        invalidShadowTerrainSuppressedFrames = 0;
        nothiriumShadowInvalidFrames = 0;
        nothiriumShadowSuppressedFrames = 0;
        self().resetShadowRenderCache();
        MainMod.LOGGER.debug(
                "[Pipeline] Blank shadow textures initialized: {}x{} (shadowMapResolution={} option={} changedOption={} activeConst={} profile={} distanceSlider={} qualitySlider={})",
                resolution,
                resolution,
                resolutionValue,
                optionValue(properties, "shadowMapResolution"),
                changedOptionValue(properties, "shadowMapResolution"),
                activeConstSetting(pack, properties, "shadowMapResolution"),
                optionValue(properties, "<profile>"),
                optionValue(properties, "SHADOW_DISTANCE_SLIDER"),
                optionValue(properties, "SHADOW_QTY_SLIDER")
        );
    }

    protected static boolean shouldCreateShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        return PipelineRuntimeState.hasEffectiveShadowProgram(properties)
                || properties.options().booleanValue("SHADOW_CASTING")
                || properties.options().booleanValue("ENABLE_SHADOWS")
                || PipelineRuntimeState.hasShadowProgramFiles(pack);
    }

    protected static boolean hasEffectiveShadowProgram(ShaderProperties properties) {
        for (RenderPass pass : RenderPass.values()) {
            if (pass.stage() == ProgramStage.SHADOW && properties.isProgramEnabled(pass)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasShadowProgramFiles(ShaderPack pack) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        for (RenderPass pass : RenderPass.values()) {
            if (pass.stage() != ProgramStage.SHADOW) {
                continue;
            }
            for (String base : layout.programBases(pass)) {
                if (pack.hasResource(base + ".vsh") || pack.hasResource(base + ".fsh") || pack.hasResource(base + ".gsh")) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void rebuildFullscreenProgramArrays() {
        fullscreenProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            FullscreenProgramArray array = FullscreenProgramArray.fromProgramSet(arrayId, programSet);
            fullscreenProgramArrays.put(arrayId, array);
            if (PipelineRuntimeState.hasUnsupportedFullscreenArrayEntries(array)) {
                MainMod.LOGGER.debug(
                        "[Pipeline] Program array {} declares {} programs; current 1.12 adapter exposes {} fixed slots and cannot run the remaining entries yet.",
                        arrayId.sourcePrefix(),
                        array.declaredProgramCount(),
                        array.fixedPasses().size()
                );
            }
        }
    }

    protected boolean hasExtraProgramArrayEntries() {
        return fullscreenProgramArrays.values().stream()
                .anyMatch(PipelineContext::hasUnsupportedFullscreenArrayEntries);
    }

    protected static boolean hasUnsupportedFullscreenArrayEntries(FullscreenProgramArray array) {
        if (!array.hasExtraPrograms()) {
            return false;
        }
        return !PipelineRuntimeState.supportsIndexedFullscreenArray(array.arrayId());
    }

    protected int shaderLoadingStepCount(ShaderProperties properties) {
        return 9
                + PipelineRuntimeState.computeProgramSourceCount(packDirectives.computeDirectives())
                + PipelineRuntimeState.enabledProgramCount(properties)
                + self().enabledFullscreenArrayProgramSourceCount(properties);
    }

    protected static int computeProgramSourceCount(ShaderComputeDirectives directives) {
        return PipelineProgramArrayRules.computeSourceCount(directives);
    }

    protected int enabledFullscreenArrayProgramSourceCount(ShaderProperties properties) {
        if (programSet == null) {
            return 0;
        }
        int count = 0;
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            for (ShaderProgramSource source : programSet.programArray(arrayId)) {
                int index = PipelineRuntimeState.indexForFullscreenArraySource(arrayId, source.name());
                if (source.hasAnyStage()
                        && PipelineRuntimeState.shouldCompileIndexedFullscreenArraySource(arrayId, index)
                        && properties.isProgramArrayEnabled(arrayId, source.name())) {
                    count++;
                }
            }
        }
        return count;
    }

    protected static int enabledProgramCount(ShaderProperties properties) {
        int count = 0;
        for (RenderPass pass : RenderPass.values()) {
            if (properties.isProgramEnabled(pass)) {
                count++;
            }
        }
        return count;
    }

    protected void compileComputePrograms(ShaderPack pack, ShaderProperties properties) {
        computeProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            List<ComputeProgram> compiled = PipelineRuntimeState.compileComputeList(
                    pack,
                    properties,
                    arrayId,
                    packDirectives.computeDirectives().computeArrays().getOrDefault(arrayId, List.of()),
                    packDirectives
            );
            if (!compiled.isEmpty()) {
                computeProgramArrays.put(arrayId, compiled);
            }
        }
        shadowComputePrograms = PipelineRuntimeState.compileComputeList(pack, properties, null, packDirectives.computeDirectives().shadowComputes(), packDirectives);
        finalComputePrograms = PipelineRuntimeState.compileComputeList(pack, properties, null, packDirectives.computeDirectives().finalComputes(), packDirectives);
    }

    protected void compileFullscreenArrayPrograms(ShaderPack pack, ShaderProperties properties) {
        fullscreenArrayPrograms.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            List<FullscreenArrayProgram> compiled = self().compileFullscreenArrayList(pack, properties, arrayId);
            if (!compiled.isEmpty()) {
                fullscreenArrayPrograms.put(arrayId, compiled);
            }
        }
    }

    protected List<FullscreenArrayProgram> compileFullscreenArrayList(
            ShaderPack pack,
            ShaderProperties properties,
            ProgramArrayId arrayId
    ) {
        List<ShaderProgramSource> sources = programSet.programArray(arrayId);
        if (sources.isEmpty()) {
            return List.of();
        }

        List<FullscreenArrayProgram> compiled = new ArrayList<>();
        RenderPass bindingPass = PipelineRuntimeState.fullscreenArrayBindingPass(arrayId);
        for (ShaderProgramSource source : sources) {
            if (!source.hasAnyStage()) {
                continue;
            }
            if (!properties.isProgramArrayEnabled(arrayId, source.name())) {
                MainMod.LOGGER.debug("[Pipeline] Program array source disabled by properties: {}", source.name());
                continue;
            }
            int index = PipelineRuntimeState.indexForFullscreenArraySource(arrayId, source.name());
            if (!PipelineRuntimeState.shouldCompileIndexedFullscreenArraySource(arrayId, index)) {
                continue;
            }

            FullscreenArrayProgram arrayProgram = new FullscreenArrayProgram(
                    arrayId,
                    index,
                    source.name(),
                    bindingPass,
                    properties.directivesFor(arrayId, source.name())
            );
            self().applyFallbackDefaultDrawBuffers(arrayProgram);
            ShaderLoadingScreen.step("Compiling " + source.name());
            ShaderProgram shaderProgram = ShaderCompiler.compileSource(pack, properties, source, bindingPass, packDirectives);
            if (shaderProgram != null) {
                arrayProgram.setShaderProgram(shaderProgram);
                compiled.add(arrayProgram);
                MainMod.LOGGER.debug("[Pipeline] Added indexed fullscreen program: {}", source.name());
            }
        }
        return List.copyOf(compiled);
    }

    protected static int indexForFullscreenArraySource(ProgramArrayId arrayId, String sourceName) {
        return PipelineProgramArrayRules.index(arrayId, sourceName);
    }

    protected static boolean supportsIndexedFullscreenArray(ProgramArrayId arrayId) {
        return switch (arrayId) {
            case SETUP, BEGIN, PREPARE, DEFERRED, COMPOSITE, SHADOWCOMP -> true;
        };
    }

    protected static boolean shouldCompileIndexedFullscreenArraySource(ProgramArrayId arrayId, int index) {
        return PipelineProgramArrayRules.shouldCompile(arrayId, index);
    }

    protected static RenderPass fullscreenArrayBindingPass(ProgramArrayId arrayId) {
        return PipelineProgramArrayRules.bindingPass(arrayId);
    }

    protected boolean hasSetupPrograms() {
        return !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
    }

    protected static List<ComputeProgram> compileComputeList(
            ShaderPack pack,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            List<ComputeProgramSource> sources,
            ShaderPackDirectives directives
    ) {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<ComputeProgram> compiled = new ArrayList<>();
        for (ComputeProgramSource source : sources) {
            if (arrayId != null && !properties.isProgramArrayEnabled(arrayId, source.name())) {
                MainMod.LOGGER.debug("[Pipeline] Compute array source disabled by properties: {}", source.name());
                continue;
            }
            ShaderLoadingScreen.step("Compiling " + source.name());
            ComputeProgram program = ComputeProgram.compile(pack, properties, source, directives);
            if (program != null) {
                compiled.add(program);
            }
        }
        return List.copyOf(compiled);
    }

    protected void deleteComputePrograms() {
        computeProgramArrays.values().stream()
                .flatMap(List::stream)
                .forEach(ComputeProgram::delete);
        shadowComputePrograms.forEach(ComputeProgram::delete);
        finalComputePrograms.forEach(ComputeProgram::delete);
    }

    protected void deleteFullscreenArrayPrograms() {
        fullscreenArrayPrograms.values().stream()
                .flatMap(List::stream)
                .forEach(FullscreenArrayProgram::delete);
    }

    protected void logRequestedFeaturesAndCapabilities() {
        if (!packDirectives.features().required().isEmpty() || !packDirectives.features().optional().isEmpty()) {
            MainMod.LOGGER.info(
                    "[Pipeline] Pack feature flags: required={} optional={}",
                    packDirectives.features().required(),
                    packDirectives.features().optional()
            );
        }

        ShaderPipelineCapabilities capabilities = packDirectives.capabilities();
        if (capabilities.compute()
                || capabilities.images()
                || capabilities.storageBuffers()
                || capabilities.customUniforms()
                || capabilities.customTextures()
                || capabilities.geometry()
                || capabilities.tessellation()
                || capabilities.extraProgramArrayEntries()) {
            MainMod.LOGGER.info("[Pipeline] Pack capabilities: {}", capabilities);
        }
        if (shaderImages.active()) {
            MainMod.LOGGER.info("[Pipeline] Loaded {} Iris custom image directives", shaderImages.count());
        }
        if (shaderStorageBuffers.active()) {
            MainMod.LOGGER.info("[Pipeline] Loaded {} Iris SSBO directives", shaderStorageBuffers.count());
        }
        if (packDirectives.textureDirectives().rawTextureCount() > 0) {
            MainMod.LOGGER.info(
                    "[Pipeline] Loaded {} Iris raw custom texture directives",
                    packDirectives.textureDirectives().rawTextureCount()
            );
        }
        if (capabilities.images() && !packDirectives.features().requires("custom_images") && !packDirectives.features().optional("custom_images")) {
            MainMod.LOGGER.warn("[Pipeline] Pack declares image directives without iris.features custom_images");
        }
        if (capabilities.storageBuffers() && !packDirectives.features().requires("ssbo") && !packDirectives.features().optional("ssbo")) {
            MainMod.LOGGER.warn("[Pipeline] Pack declares SSBO directives without iris.features ssbo");
        }
    }

    protected void resetHardwareCompatibilityState() {
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        pipelineTerrainFormatSupported = self().detectPipelineTerrainFormatSupport();
        hardwareSafeVanillaTerrain = ENABLE_SAFE_TERRAIN_FALLBACKS && !pipelineTerrainFormatSupported;
        hardwareSafeVanillaTerrainReason = hardwareSafeVanillaTerrain ? "missing-pipeline-terrain-format" : "";
    }

    protected void clearShaderedTerrainFallbackState() {
        hardwareSafeVanillaTerrain = false;
        hardwareSafeVanillaTerrainReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        self().clearShaderedNothiriumGlobalBypassState(true);
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
    }

    protected void clearShaderedNothiriumGlobalBypassState() {
        self().clearShaderedNothiriumGlobalBypassState(false);
    }

    protected void clearShaderedNothiriumGlobalBypassState(boolean clearTemporaryDrawPaths) {
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        if (clearTemporaryDrawPaths) {
            nothiriumHybridVanillaMaintenanceFrames = 0;
            nothiriumHybridVanillaMaintenanceReason = "";
            nothiriumMainVanillaDrawPathFrames = 0;
            nothiriumMainVanillaDrawPathReason = "";
        }
    }
}
