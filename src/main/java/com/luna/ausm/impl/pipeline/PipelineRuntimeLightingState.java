package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.ShaderProgramSource;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.ShaderCompileNotifications;
import com.luna.ausm.impl.client.ShaderLoadingScreen;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import com.luna.ausm.impl.pipeline.pack.ShaderFeatureValidator;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.render.ShaderSamplerState;
import com.luna.ausm.impl.pipeline.resource.ShaderImageSet;
import com.luna.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.luna.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderCompiler;
import com.luna.ausm.impl.pipeline.shader.ShaderLoadingMap;
import com.luna.ausm.impl.pipeline.shader.ShaderMap;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderProgramSet;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;

import static com.luna.ausm.impl.pipeline.PipelineGlState.markShaderStorageBuffersBound;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.parseBooleanSetting;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.parseFloatSetting;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.parseFloatSettingWithComment;

abstract class PipelineRuntimeLightingState extends PipelineRuntimeEnvironmentState {
    protected static float[] vec3(Vec3d vec) {
        return new float[]{
                (float) MinecraftReflectionCompat.vecX(vec),
                (float) MinecraftReflectionCompat.vecY(vec),
                (float) MinecraftReflectionCompat.vecZ(vec)
        };
    }

    protected float[] viewSpaceLightVector(Minecraft mc, boolean moon) {
        float[] world = self().worldSpaceLightVector(mc, moon);
        return MatrixState.transformModelViewDirection(world[0], world[1], world[2]);
    }

    protected float[] shaderLightPosition(Minecraft mc, boolean moon) {
        return self().viewSpaceLightVector(mc, moon);
    }

    protected float[] worldSpaceLightVector(Minecraft mc, boolean moon) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null) {
            return new float[]{0.0f, moon ? -100.0f : 100.0f, 0.0f};
        }
        float skyAngle = MinecraftReflectionCompat.worldCelestialAngle(world, MinecraftReflectionCompat.renderPartialTicks(mc)) * (float) (Math.PI * 2.0);
        if (moon) {
            skyAngle += (float) Math.PI;
        }
        float path = sunPathRotation * (float) (Math.PI / 180.0);

        // Iris derives celestial uniforms from the same transform chain used by
        // sky rendering: rotate Y -90, rotate Z by sunPathRotation, then rotate X
        // by the current sun/moon angle.  The initial vector is the vanilla
        // celestial body position in sky model space.
        float x = 0.0f;
        float y = 100.0f * (float) Math.cos(skyAngle);
        float z = 100.0f * (float) Math.sin(skyAngle);

        float pathX = x * (float) Math.cos(path) - y * (float) Math.sin(path);
        float pathY = x * (float) Math.sin(path) + y * (float) Math.cos(path);
        x = -z;
        y = pathY;
        z = pathX;
        return new float[]{x, y, z};
    }

    protected float sunAngle(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null) {
            return 0.0f;
        }
        float angle = MinecraftReflectionCompat.worldCelestialAngle(world, MinecraftReflectionCompat.renderPartialTicks(mc)) + 0.25f;
        if (angle >= 1.0f) {
            angle -= 1.0f;
        }
        return angle;
    }

    protected float shadowAngle(Minecraft mc) {
        if (PipelineRuntimeState.renderWorld(mc) == null) {
            return 0.0f;
        }
        float angle = self().sunAngle(mc);
        return angle < 0.5f ? angle : angle - 0.5f;
    }

    protected float shadowFade(Minecraft mc, float threshold, float scale) {
        float angle = self().sunAngle(mc);
        return self().clamp01(1.0f - (Math.abs(Math.abs(angle - 0.5f) - 0.25f) - threshold) * scale);
    }

    protected float[] legacyShadowLightVector(Minecraft mc, boolean moon) {
        return self().viewSpaceLightVector(mc, moon);
    }

    protected float dayMoment(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null) {
            return 0.25f;
        }
        return world != null ? (float) ((MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0) : 0.25f;
    }

    /**
     * Matches SkyblockSkyRenderer's day-seeded java.util.Random placement.
     */
    protected float[] botaniaRainbowRotation(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        long worldTime = world != null ? MinecraftReflectionCompat.worldTime(world) : 0L;
        Random random = new Random(((worldTime + 1000L) / 24000L) * 255L);
        return new float[]{random.nextFloat() * 360.0f, random.nextFloat() * 360.0f};
    }

    protected float adjustedDayTime(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        long worldTime = world != null ? MinecraftReflectionCompat.worldTime(world) % 24000L : 0L;
        return Math.abs(((((worldTime) / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
    }

    protected float dayHelper(Minecraft mc) {
        return self().clamp01(5.4f - self().adjustedDayTime(mc));
    }

    protected float nightHelper(Minecraft mc) {
        return self().clamp01(self().adjustedDayTime(mc) - 6.0f);
    }

    protected float dayMixer(Minecraft mc) {
        float moment = self().dayMoment(mc) - 0.25f;
        return self().clamp01(-(moment * moment) * 20.0f + 1.25f);
    }

    protected float nightMixer(Minecraft mc) {
        float moment = self().dayMoment(mc) - 0.75f;
        return self().clamp01(-(moment * moment) * 50.0f + 3.125f);
    }

    protected float dayNightMix(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null) {
            return 1.0f;
        }
        float worldTime = MinecraftReflectionCompat.worldTime(world) % 24000L;
        float day = worldTime < 12485.0f || worldTime >= 23515.0f ? 1.0f : 0.0f;
        float dusk = worldTime >= 12485.0f && worldTime < 13085.0f
                ? 1.0f - ((worldTime - 12485.0f) * 0.0016666667f)
                : 0.0f;
        float dawn = worldTime >= 22915.0f && worldTime < 23515.0f
                ? (worldTime - 22915.0f) * 0.0016666667f
                : 0.0f;
        return Math.max(Math.max(day, dusk), dawn);
    }

    protected float volumetricDayMixer(Minecraft mc) {
        float moment = self().dayMoment(mc);
        float day = (moment * 4.0f) - 1.0f;
        float night = (moment * 4.0f) - 3.0f;
        float dayValue = self().clamp((-(day * day * day * day) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        float nightValue = self().clamp((-(night * night * night * night) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        return Math.max(dayValue, nightValue);
    }

    protected float clamp01(float value) {
        return Math.clamp(value, 0.0f, 1.0f);
    }

    protected float clamp(float value, float min, float max) {
        return Math.clamp(value, min, max);
    }

    public void initialize(ShaderPack pack) {
        self().initialize(pack, Map.of());
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides) {
        self().initialize(pack, optionOverrides, null);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        self().initialize(pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                           ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
        self().initializeInternal(null, pack, optionOverrides, preloadedProperties, loadingBackgroundMode);
    }

    public void initializeCached(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        self().initializeInternal(cacheKey, pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    protected void initializeInternal(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                                      ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
        nothiriumShadowRenderer.resetPipelineProgramState();
        terrainRebuiltDuringLastInitialization = false;
        terrainCacheReusableDuringLastInitialization = false;
        boolean wasPipelineActive = isPipelineActive;
        boolean replacingActiveCacheKey = cacheKey != null && cacheKey.equals(activeCompiledPipelineCacheKey);
        PipelineRuntimeState.CompiledPipelineState cachedPrograms = replacingActiveCacheKey ? null : self().removeCachedCompiledPipeline(cacheKey);
        if (cacheKey == null) {
            self().cleanupRuntimeState(true, true);
        } else {
            if (replacingActiveCacheKey) {
                self().deleteCachedCompiledPipeline(cacheKey);
                activeCompiledPipelineCacheKey = null;
            } else {
                self().cacheActiveCompiledPipeline();
            }
            self().cleanupRuntimeState(true, false, !wasPipelineActive);
        }
        shaderProperties = PipelineRuntimeState.emptyShaderProperties();
        activePackName = pack.getName();
        self().resetHardwareCompatibilityState();

        MainMod.LOGGER.info("[Pipeline] Initializing with pack: {}", pack.getName());
        self().logHardwareCapabilities("initialize:" + pack.getName(), preloadedProperties != null ? preloadedProperties.packDirectives() : null);

        if (pack.getName().equals("(internal)")) { // NoneShaderPack
            MainMod.LOGGER.info("[Pipeline] Internal None pack selected. Pipeline is inactive.");
            return;
        }

        self().releaseMouseForShaderLoad(MinecraftReflectionCompat.minecraft());
        boolean usingCachedPrograms = cachedPrograms != null;
        boolean restoredCachedPrograms = false;
        ShaderLoadingScreen.begin(pack.getName(), usingCachedPrograms ? 9 : 12, loadingBackgroundMode);
        try {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            ShaderLoadingScreen.step("Loading shader properties");
            ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
            self().logHardwareCapabilities("properties:" + pack.getName(), properties.packDirectives());
            ShaderCompileNotifications.beginReload();
            ShaderLoadingScreen.step("Scanning shader programs");
            programSet = usingCachedPrograms ? cachedPrograms.programSet : ShaderProgramSet.load(pack, properties);
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            self().rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
                            .withGeometry(programSet.hasGeometrySources())
                            .withTessellation(programSet.hasTessellationSources())
                            .withExtraProgramArrayEntries(self().hasExtraProgramArrayEntries())
            );
            ShaderFeatureValidator.Result featureValidation = ShaderFeatureValidator.validate(packDirectives);
            for (String warning : featureValidation.warnings()) {
                MainMod.LOGGER.warn("[Pipeline] {}", warning);
            }
            if (!featureValidation.supported()) {
                String summary = featureValidation.summary();
                MainMod.LOGGER.error("[Pipeline] Shaderpack '{}' disabled: {}", pack.getName(), summary);
                ShaderCompileNotifications.reportLoadFailure(pack.getName(), summary);
                return;
            }
            ShaderLoadingScreen.setTotalSteps(usingCachedPrograms ? 9 : self().shaderLoadingStepCount(properties));
            ShaderLoadingMap loadingMap = usingCachedPrograms ? null : new ShaderLoadingMap();
            shaderProperties = properties;
            bloomRenderer.configure(pack, properties);
            ShaderBlockLayerOverrides.install(properties.blockIds());
            ShaderSamplerState.setBreaksAnisotropy(properties.renderSettings().breaksAnisotropy());
            shadowMapDistance = parseFloatSettingWithComment(pack, properties, "shadowDistance", "SHADOWHPL", 128.0f);
            voxelDistance = parseFloatSetting(pack, properties, "voxelDistance", 0.0f);
            shadowDistanceRenderMul = parseFloatSetting(pack, properties, "shadowDistanceRenderMul", -1.0f);
            shadowIntervalSize = parseFloatSetting(pack, properties, "shadowIntervalSize", 2.0f);
            sunPathRotation = parseFloatSetting(pack, properties, "sunPathRotation", 0.0f);
            centerDepthHalfLife = parseFloatSetting(pack, properties, "centerDepthHalflife", 1.0f);
            eyeBrightnessHalfLife = parseFloatSetting(pack, properties, "eyeBrightnessHalflife", 3.0f);
            wetnessHalfLife = parseFloatSetting(pack, properties, "wetnessHalflife", 600.0f);
            drynessHalfLife = parseFloatSetting(pack, properties, "drynessHalflife", 200.0f);
            shadowPolygonOffset = parseBooleanSetting(pack, properties, "shadowPolygonOffset", true);
            shadowPolygonOffsetFactor = parseFloatSetting(pack, properties, "shadowPolygonOffsetFactor", 1.1f);
            shadowPolygonOffsetUnits = parseFloatSetting(pack, properties, "shadowPolygonOffsetUnits", 4.0f);
            shadowFrameCount = 1_000_000;
            lastShadowFrameId = -1L;
            self().resetShadowRenderCache();
            shadowHealthLogged = false;
            shadowHealthLogAttempts = 0;
            ShaderLoadingScreen.step("Preparing framebuffers");
            pingPongManager.initialize(MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), packDirectives.renderTargets());
            self().initializeBlankShadowFramebuffer(pack, properties);
            MainMod.LOGGER.debug(
                    "[Pipeline] Shadow config: framebuffer={} distance={} voxelDistance={} renderMul={} interval={} sunPathRotation={} hardwareFiltering={} tex0Nearest={} tex1Nearest={} polygonOffset={} factor={} units={}",
                    shadowFramebuffer != null ? shadowFramebuffer.resolution() : 0,
                    shadowMapDistance,
                    voxelDistance,
                    shadowDistanceRenderMul,
                    shadowIntervalSize,
                    sunPathRotation,
                    packDirectives.renderTargets().shadowHardwareFiltering(),
                    packDirectives.renderTargets().shadowDepthNearest(0),
                    packDirectives.renderTargets().shadowDepthNearest(1),
                    shadowPolygonOffset,
                    shadowPolygonOffsetFactor,
                    shadowPolygonOffsetUnits
            );
            if (packDirectives.computeDirectives().hasComputes()) {
                MainMod.LOGGER.debug(
                        "[Pipeline] Loaded compute metadata: arrays={} shadow={} final={}",
                        packDirectives.computeDirectives().computeArrays().values().stream().mapToInt(List::size).sum(),
                        packDirectives.computeDirectives().shadowComputes().size(),
                        packDirectives.computeDirectives().finalComputes().size()
                );
            }
            ShaderLoadingScreen.step("Preparing shader resources");
            shaderImages = ShaderImageSet.load(packDirectives.images());
            shaderImages.resize(MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc));
            self().clearColoredLightImages();
            shaderStorageBuffers = ShaderStorageBufferSet.load(pack, packDirectives.storageBuffers());
            shaderStorageBuffers.resize(MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc));
            if (shaderStorageBuffers.active()) {
                markShaderStorageBuffersBound();
            }
            if (!usingCachedPrograms) {
                ShaderLoadingScreen.step("Compiling compute shaders");
                self().compileComputePrograms(pack, properties);
                setupComputePending = !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
            }
            self().logRequestedFeaturesAndCapabilities();
            ShaderLoadingScreen.step("Loading noise texture");
            self().initializeNoiseTexture(pack, properties);
            ShaderLoadingScreen.step("Loading custom textures");
            customTextures.load(pack, packDirectives.textureDirectives(), fullscreenArrayPrograms);
            lastPipelineFrameNanos = System.nanoTime() - 1_000_000_000L;
            currentFrameTime = 1.0f;

            if (usingCachedPrograms) {
                ShaderLoadingScreen.step("Restoring cached shader programs");
                self().restoreCompiledPipeline(cachedPrograms);
                restoredCachedPrograms = true;
                MainMod.LOGGER.info("[Pipeline] Reused cached compiled shader programs for pack: {}", pack.getName());
            } else {
                for (RenderPass pass : RenderPass.values()) {
                    PipelineProgram pipelineProgram = new PipelineProgram(pass, programSet.source(pass.programId()).directives());
                    self().applyFallbackDefaultDrawBuffers(pipelineProgram);
                    ShaderProgramSource source = programSet.source(pass.programId());
                    boolean hasOfficialFinalSource = pass == RenderPass.FINAL
                            && (source.fragmentPath() != null || source.fragmentSource() != null);
                    boolean enabled = properties.isProgramEnabled(pass) || hasOfficialFinalSource;
                    pipelineProgram.setEnabled(enabled);

                    if (enabled) {
                        ShaderLoadingScreen.step("Compiling " + pass.getProgramName());
                        ShaderProgram program = ShaderCompiler.compilePass(pack, pass, properties, source, packDirectives);
                        if (program != null) {
                            pipelineProgram.setShaderProgram(program);
                            loadingMap.put(pipelineProgram.shaderKey(), program);
                            MainMod.LOGGER.debug("[Pipeline] Added program for pass: {}", pass);
                        }
                    } else {
                        MainMod.LOGGER.debug("[Pipeline] Program disabled by properties: {}", pass.getProgramName());
                    }
                    programs.put(pass, pipelineProgram);
                }
                self().compileFullscreenArrayPrograms(pack, properties);
                ShaderLoadingScreen.step("Building shader pipeline");
                shaderMap = new ShaderMap(loadingMap);
            }
            setupComputePending = self().hasSetupPrograms();

            isPipelineActive = pingPongManager.isInitialized();
            activeSkyPipelineProbeLogs = 0;
            compositeChainProbeLogs = 0;
            fullscreenSamplerProbeLogs = 0;
            self().resetChunkFadeState(true);
            activeCompiledPipelineCacheKey = cacheKey;
            long loadedProgramCount = programs.values().stream().filter(PipelineProgram::hasOwnProgram).count();
            long loadedArrayProgramCount = fullscreenArrayPrograms.values().stream()
                    .flatMap(List::stream)
                    .filter(FullscreenArrayProgram::hasProgram)
                    .count();
            self().clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad("initialize:" + pack.getName());
            self().applyPackStartupTerrainFallback("initialize:" + pack.getName());
            MainMod.LOGGER.info(
                    "[Pipeline] Initialization complete. Pipeline Active: {}, Loaded Programs: {} (+{} indexed fullscreen)",
                    isPipelineActive,
                    loadedProgramCount,
                    loadedArrayProgramCount
            );
            ShaderCompileNotifications.finishReload(pack.getName());
            syntheticLightCandidates.clear();
            // Startup can build a provisional Nothirium mesh before this pack's
            // block-id rules are installed. Preserve a fresh probe window for
            // the real rebuild below, which is the data the shader consumes.
            self().resetFramedMaterialProbes();
            if (wasPipelineActive) {
                // A single full refresh below invalidates and rebuilds both the
                // shader terrain metadata and native BLOOM geometry. Dirtying
                // Nothirium here as well starts an avoidable first compilation
                // that is discarded by the scheduled renderer rebuild.
                self().scheduleWorldTerrainRefresh(true, true, 0);
                ShaderLoadingScreen.step("Refreshing terrain metadata");
            } else {
                boolean nothiriumFormatChanged = self().updateNothiriumPipelineBlockFormatMode();
                ShaderLoadingScreen.step("Rebuilding terrain");
                self().rebuildTerrainRenderers(nothiriumFormatChanged, true);
                terrainRebuiltDuringLastInitialization = true;
            }
        } finally {
            if (cachedPrograms != null && !restoredCachedPrograms) {
                cachedPrograms.delete();
            }
            ShaderLoadingScreen.finish();
        }
    }

    protected void releaseMouseForShaderLoad(Minecraft mc) {
        if (mc != null && MinecraftReflectionCompat.fieldBoolean(mc, false, "field_71415_G", "inGameHasFocus")) {
            MinecraftReflectionCompat.invoke(mc, new String[]{"func_71364_i", "setIngameNotInFocus"}, MinecraftReflectionCompat.NO_PARAMETERS);
        }
        try {
            if (Mouse.isCreated()) {
                Mouse.setGrabbed(false);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public ShaderProperties getShaderProperties() {
        return shaderProperties;
    }

    public boolean activateCachedCompiledPipeline(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides,
                                                  ShaderProperties preloadedProperties) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return false;
        }
        if (cacheKey.equals(activeCompiledPipelineCacheKey) && programSet != null && shaderMap != null && isPipelineActive) {
            return true;
        }
        if (!pingPongManager.isInitialized()) {
            return false;
        }

        PipelineRuntimeState.CompiledPipelineState cachedPrograms = self().removeCachedCompiledPipeline(cacheKey);
        if (cachedPrograms == null) {
            return false;
        }

        try {
            self().cacheActiveCompiledPipeline();
            ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
            programSet = cachedPrograms.programSet;
            shaderProperties = properties;
            bloomRenderer.configure(pack, properties);
            ShaderBlockLayerOverrides.install(properties.blockIds());
            ShaderSamplerState.setBreaksAnisotropy(properties.renderSettings().breaksAnisotropy());
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            self().rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
                            .withGeometry(programSet.hasGeometrySources())
                            .withTessellation(programSet.hasTessellationSources())
                            .withExtraProgramArrayEntries(self().hasExtraProgramArrayEntries())
            );
            self().restoreCompiledPipeline(cachedPrograms);
            activePackName = pack.getName();
            activeCompiledPipelineCacheKey = cacheKey;
            setupComputePending = self().hasSetupPrograms();
            self().resetTransientWorldRenderState();
            isPipelineActive = true;
            activeSkyPipelineProbeLogs = 0;
            self().resetChunkFadeState(true);
            self().clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad("activate-cache:" + pack.getName());
            self().applyPackStartupTerrainFallback("activate-cache:" + pack.getName());
            MainMod.LOGGER.debug("[Pipeline] Activated cached compiled shader programs: {}", cacheKey);
            return true;
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[Pipeline] Failed to activate cached compiled shader programs: {}", cacheKey, e);
            cachedPrograms.delete();
            return false;
        }
    }

    public void clearCompiledPipelineCache() {
        self().deleteCachedCompiledPipelines();
        activeCompiledPipelineCacheKey = null;
    }

    public boolean consumeTerrainRebuiltDuringLastInitialization() {
        boolean rebuilt = terrainRebuiltDuringLastInitialization;
        terrainRebuiltDuringLastInitialization = false;
        return rebuilt;
    }

    public boolean consumeTerrainCacheReusableDuringLastInitialization() {
        boolean reusable = terrainCacheReusableDuringLastInitialization;
        terrainCacheReusableDuringLastInitialization = false;
        return reusable;
    }

    protected void cacheActiveCompiledPipeline() {
        if (activeCompiledPipelineCacheKey == null || programSet == null || shaderMap == null || self().isInternalPipelinePack()) {
            return;
        }

        PipelineRuntimeState.CompiledPipelineState previous = compiledPipelineCache.put(activeCompiledPipelineCacheKey, self().detachCompiledPipeline());
        if (previous != null) {
            previous.delete();
        }
        MainMod.LOGGER.debug("[Pipeline] Cached compiled shader programs: {}", activeCompiledPipelineCacheKey);
        activeCompiledPipelineCacheKey = null;
    }

    protected PipelineRuntimeState.CompiledPipelineState detachCompiledPipeline() {
        PipelineRuntimeState.CompiledPipelineState state = new PipelineRuntimeState.CompiledPipelineState(
                programSet,
                shaderMap,
                programs,
                computeProgramArrays,
                shadowComputePrograms,
                finalComputePrograms,
                fullscreenArrayPrograms
        );
        programs.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        fullscreenArrayPrograms.clear();
        programSet = null;
        shaderMap = null;
        setupComputePending = false;
        return state;
    }

    protected void restoreCompiledPipeline(PipelineRuntimeState.CompiledPipelineState state) {
        programs.clear();
        programs.putAll(state.programs);
        computeProgramArrays.clear();
        computeProgramArrays.putAll(state.computeProgramArrays);
        shadowComputePrograms = state.shadowComputePrograms;
        finalComputePrograms = state.finalComputePrograms;
        fullscreenArrayPrograms.clear();
        fullscreenArrayPrograms.putAll(state.fullscreenArrayPrograms);
        programSet = state.programSet;
        shaderMap = state.shaderMap;
        self().applyFallbackDefaultDrawBuffers();
    }
}
