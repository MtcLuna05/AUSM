package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.shader.ShaderPackController;
import com.luna.ausm.api.shader.ShaderPackInfo;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldProvider;

public class ShaderPackManager implements ShaderPackController {
    private static final String OFF_PACK_NAME = "OFF";
    private static final String INTERNAL_PACK_NAME = "(internal)";
    private static final int SHADER_TOGGLE_TIMING_PROBE_LIMIT = 0;

    private final ShaderPackRepository repository;
    private final ShaderPackConfigurationStore configurationStore;
    private ShaderPack currentPack = NoneShaderPack.INSTANCE;
    private Map<String, String> currentOptionOverrides = Map.of();
    private final ShaderPropertiesCache shaderPropertiesCache = new ShaderPropertiesCache();
    private String selectedPackName = OFF_PACK_NAME;
    private boolean shadersEnabled = false;
    private boolean pendingPipelineReload = false;
    private int compiledDimensionId = Integer.MIN_VALUE;
    private int pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
    private int pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
    private final ShaderPipelineWorldLoadGate firstWorldFrameCompileGate = new ShaderPipelineWorldLoadGate();
    private final Set<String> betterPortalsPrewarmedCacheKeys = new HashSet<>();
    private String lastDeferredBetterPortalsCacheKey = "";
    private String compiledPackName = OFF_PACK_NAME;
    private int shaderToggleTimingProbeLogs;

    public ShaderPackManager(Path minecraftRunDir) {
        repository = new ShaderPackRepository(minecraftRunDir.resolve("shaderpacks"));
        Path configDir = minecraftRunDir.resolve("config").resolve("ausm");
        configurationStore = new ShaderPackConfigurationStore(configDir);
        repository.ensureDirectoryExists();
    }

    /**
     * Loads a shader pack by its folder/zip name.
     */
    public boolean loadPack(String packName) {
        if (isOffPack(packName)) {
            MainMod.LOGGER.info("Disabling shaderpack.");
            selectedPackName = OFF_PACK_NAME;
            shadersEnabled = false;
            saveShaderConfig();
            closeCurrentPack();
            currentPack = NoneShaderPack.INSTANCE;
            currentOptionOverrides = Map.of();
            clearShaderPropertiesCache();
            betterPortalsPrewarmedCacheKeys.clear();
            compiledDimensionId = Integer.MIN_VALUE;
            pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
            pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
            firstWorldFrameCompileGate.clear();
            compiledPackName = OFF_PACK_NAME;
            pendingPipelineReload = false;
            PipelineContext.getInstance().cleanup();
            rebuildInactiveVanillaRenderers();
            return true;
        }

        if (!isPackAvailable(packName)) {
            fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", packName);
            return false;
        }

        boolean compileNow = shadersEnabled;
        selectedPackName = packName;
        currentOptionOverrides = loadOptionOverrides(packName);
        clearShaderPropertiesCacheExcept(packName);
        betterPortalsPrewarmedCacheKeys.clear();
        PipelineContext.getInstance().clearCompiledPipelineCache();
        pendingPipelineReload = true;
        saveShaderConfig();
        if (!compileNow) {
            closeCurrentPack();
            currentPack = NoneShaderPack.INSTANCE;
            compiledDimensionId = Integer.MIN_VALUE;
            pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
            pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
            compiledPackName = OFF_PACK_NAME;
            PipelineContext.getInstance().cleanup();
            rebuildInactiveVanillaRenderers();
            MainMod.LOGGER.info("Selected shaderpack '{}' for manual enable.", packName);
            return true;
        }

        if (!ensureSelectedPackLoaded()) {
            return false;
        }
        int currentDimensionId = getEffectiveRenderDimensionId();
        ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides, currentDimensionId);
        if (!initializeCurrentPipeline(properties, true)) {
            return false;
        }
        MainMod.LOGGER.info("Successfully loaded shaderpack: {}", currentPack.getName());
        return true;
    }

    public void loadSavedConfiguration() {
        SavedShaderConfiguration saved = configurationStore.load(OFF_PACK_NAME);
        selectedPackName = saved.selectedPackName();
        boolean savedEnabled = saved.enabled();
        boolean packAvailable = !isOffPack(selectedPackName) && isPackAvailable(selectedPackName);
        shadersEnabled = !automaticShaderDisablingEnabled() && savedEnabled && packAvailable;
        currentPack = NoneShaderPack.INSTANCE;
        currentOptionOverrides = isOffPack(selectedPackName) ? Map.of() : loadOptionOverrides(selectedPackName);
        pendingPipelineReload = packAvailable;
        compiledDimensionId = Integer.MIN_VALUE;
        pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
        pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
        firstWorldFrameCompileGate.clear();
        betterPortalsPrewarmedCacheKeys.clear();
        compiledPackName = OFF_PACK_NAME;
        PipelineContext.getInstance().setActive(false);
        MainMod.LOGGER.info(
                "Loaded shader state: selected='{}' savedEnabled={} automaticShaderDisabling={} restoreOnWorldLoad={}",
                selectedPackName,
                savedEnabled,
                automaticShaderDisablingEnabled(),
                shadersEnabled
        );
    }

    private ShaderPack openPack(String packName) {
        return repository.open(packName);
    }

    private void setPack(ShaderPack newPack) {
        if (newPack == null) {
            newPack = NoneShaderPack.INSTANCE;
        }

        closeCurrentPack();
        this.currentPack = newPack;
        this.currentOptionOverrides = isInternalPack(newPack) ? Map.of() : loadOptionOverrides(newPack.getName());
        clearShaderPropertiesCacheExcept(newPack.getName());
        betterPortalsPrewarmedCacheKeys.clear();

        if (!shadersEnabled && !isInternalPack(newPack)) {
            PipelineContext.getInstance().cleanup();
            this.compiledDimensionId = Integer.MIN_VALUE;
            this.pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
            this.pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
            this.compiledPackName = OFF_PACK_NAME;
            this.pendingPipelineReload = true;
            rebuildInactiveVanillaRenderers();
            return;
        }

        // Notify the pipeline to reload and compile shaders
        ShaderProperties properties = getShaderProperties(newPack.getName(), currentOptionOverrides);
        initializeCurrentPipeline(properties, shadersEnabled && !isOffPack(selectedPackName));
    }

    public ShaderPack getCurrentPack() {
        return currentPack;
    }

    public Path getShaderpacksDir() {
        return repository.directory();
    }

    public String importShaderPack(Path source) throws IOException {
        return repository.importPack(source);
    }

    @Override
    public ShaderPackInfo getCurrentShaderPack() {
        String name = isOffPack(selectedPackName) ? OFF_PACK_NAME : selectedPackName;
        return new ShaderPackInfo(name, areShadersEnabled(), isOffPack(name) || isPackAvailable(name));
    }

    public String getSelectedPackName() {
        return selectedPackName;
    }

    public String currentPackFingerprint() {
        return repository.fingerprint(selectedPackName);
    }

    public boolean areShadersEnabled() {
        return shadersEnabled && !isOffPack(selectedPackName);
    }

    public boolean shouldProtectDistantHorizonsNativeApply() {
        return !isOffPack(selectedPackName)
                && (shadersEnabled || pendingPipelineReload || PipelineContext.getInstance().isActive());
    }

    /**
     * Lists available packs in the shaderpacks folder.
     */
    public List<String> getAvailablePacks() {
        return repository.availablePacks(OFF_PACK_NAME);
    }

    public void reloadPack() {
        PipelineContext.getInstance().clearCompiledPipelineCache();
        betterPortalsPrewarmedCacheKeys.clear();
        if (isOffPack(selectedPackName)) {
            setPack(NoneShaderPack.INSTANCE);
            return;
        }

        boolean wasEnabled = shadersEnabled;
        closeCurrentPack();
        currentPack = null;
        clearShaderPropertiesCache();
        if (!ensureSelectedPackLoaded()) {
            return;
        }
        shadersEnabled = wasEnabled;
        if (shadersEnabled) {
            ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
            initializeCurrentPipeline(properties, true);
        } else {
            pendingPipelineReload = true;
            PipelineContext.getInstance().setActive(false);
        }
    }

    public void reloadIfDimensionChanged() {
        if (PipelineContext.getInstance().isRenderingBetterPortalsExternalWorldFrame()) {
            return;
        }
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }
        boolean nestedBetterPortalsView = BetterPortalsCompat.isRenderingNestedView();
        boolean quietBetterPortalsReloadRequest = BetterPortalsCompat.consumeQuietDimensionReloadLogRequest();
        boolean quietBetterPortalsReload = nestedBetterPortalsView || quietBetterPortalsReloadRequest;
        if (quietBetterPortalsReload && !BetterPortalsCompat.shouldRenderNestedViewWithShaders()) {
            return;
        }
        if (!areShadersEnabled() || isOffPack(selectedPackName)) {
            return;
        }

        if (!isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' disappeared before world rendering; disabling shaders.", selectedPackName);
            return;
        }

        int currentDimensionId = getEffectiveRenderDimensionId();
        if (currentDimensionId == Integer.MIN_VALUE || currentDimensionId == compiledDimensionId) {
            return;
        }

        if (!ensureSelectedPackLoaded()) {
            return;
        }

        switchCompiledPipelineDimension(currentDimensionId, quietBetterPortalsReload);
    }

    public void restoreAfterBetterPortalsNestedRender(int parentDimensionId) {
        if (!BetterPortalsCompat.isNestedShaderPipelineAvailable()
                || !areShadersEnabled()
                || isOffPack(selectedPackName)
                || parentDimensionId == Integer.MIN_VALUE) {
            return;
        }
        if (isBetterPortalsPipelineBusy()) {
            pendingBetterPortalsParentRestoreDimensionId = parentDimensionId;
            MainMod.LOGGER.info("[BetterPortalsPipeline] restore-parent-queued parent={} compiled={} pending={} pack={}",
                    parentDimensionId,
                    compiledDimensionId,
                    pendingBetterPortalsDimensionCompileId,
                    selectedPackName);
            return;
        }
        if (!ensureSelectedPackLoaded()) {
            return;
        }
        MainMod.LOGGER.info("[BetterPortalsPipeline] restore-parent-dimension parent={} compiled={} pending={} pack={}",
                parentDimensionId,
                compiledDimensionId,
                pendingBetterPortalsDimensionCompileId,
                selectedPackName);
        switchCompiledPipelineDimension(parentDimensionId, true);
    }

    public void scheduleBetterPortalsDimensionPrewarm(int dimensionId) {
        if (!BetterPortalsCompat.isNestedShaderPipelineAvailable()
                || dimensionId == Integer.MIN_VALUE
                || dimensionId == compiledDimensionId
                || dimensionId == pendingBetterPortalsDimensionCompileId
                || !areShadersEnabled()
                || isOffPack(selectedPackName)) {
            return;
        }
        String cacheKey = compiledPipelineCacheKey(selectedPackName, currentOptionOverrides, dimensionId);
        if (!betterPortalsPrewarmedCacheKeys.add(cacheKey)) {
            return;
        }

        pendingBetterPortalsDimensionCompileId = dimensionId;
        MainMod.LOGGER.info("[BetterPortalsPipeline] queued-dimension-prewarm dimension={} compiled={} pack={} cacheKey={}",
                dimensionId,
                compiledDimensionId,
                selectedPackName,
                cacheKey);
    }

    private void switchCompiledPipelineDimension(int currentDimensionId, boolean quietBetterPortalsReload) {
        if (currentDimensionId == Integer.MIN_VALUE || currentDimensionId == compiledDimensionId) {
            return;
        }

        ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides, currentDimensionId);
        String cacheKey = compiledPipelineCacheKey(currentPack.getName(), currentOptionOverrides, currentDimensionId);
        if (PipelineContext.getInstance().activateCachedCompiledPipeline(cacheKey, currentPack, currentOptionOverrides, properties)) {
            if (quietBetterPortalsReload) {
                MainMod.LOGGER.info("[BetterPortalsPipeline] dimension-switch-cached from={} to={} pack={} cacheKey={}",
                        compiledDimensionId, currentDimensionId, selectedPackName, cacheKey);
            } else {
                MainMod.LOGGER.info("Shader dimension changed from {} to {}; restored cached shaderpack '{}'",
                        compiledDimensionId, currentDimensionId, selectedPackName);
            }
            compiledDimensionId = currentDimensionId;
            compiledPackName = currentPack.getName();
            lastDeferredBetterPortalsCacheKey = "";
            pendingPipelineReload = false;
            if (currentDimensionId == getClientDimensionId()) {
                PipelineContext context = PipelineContext.getInstance();
                context.scheduleFullWorldTerrainRefresh();
                context.scheduleWorldLoadLightRecalculation();
            }
            return;
        }

        boolean nestedBetterPortalsView = BetterPortalsCompat.isRenderingNestedView();
        boolean nestedShaderPipeline = nestedBetterPortalsView && BetterPortalsCompat.shouldRenderNestedViewWithShaders();
        if (quietBetterPortalsReload && nestedBetterPortalsView && !nestedShaderPipeline) {
            pendingBetterPortalsDimensionCompileId = currentDimensionId;
            if (!cacheKey.equals(lastDeferredBetterPortalsCacheKey)) {
                lastDeferredBetterPortalsCacheKey = cacheKey;
                MainMod.LOGGER.info("[BetterPortalsPipeline] dimension-switch-deferred nestedDimension={} compiled={} pack={} cacheKey={}",
                        currentDimensionId,
                        compiledDimensionId,
                        selectedPackName,
                        cacheKey);
            }
            return;
        }

        if (nestedShaderPipeline) {
            MainMod.LOGGER.info("[BetterPortalsPipeline] dimension-switch-compile-nested from={} to={} pack={} cacheKey={}",
                    compiledDimensionId,
                    currentDimensionId,
                    selectedPackName,
                    cacheKey);
        } else {
            MainMod.LOGGER.info("Shader dimension changed from {} to {}; compiling shaderpack '{}' for this dimension",
                    compiledDimensionId, currentDimensionId, selectedPackName);
        }
        boolean fullTerrainRefresh = currentDimensionId == getClientDimensionId() && !quietBetterPortalsReload;
        initializeCurrentPipeline(properties, true, currentDimensionId, fullTerrainRefresh);
        lastDeferredBetterPortalsCacheKey = "";
    }

    public void runPendingBetterPortalsDimensionCompile() {
        if (!BetterPortalsCompat.isNestedShaderPipelineAvailable()) {
            pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
            pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
            return;
        }
        runPendingBetterPortalsParentRestore();

        int pendingDimensionId = pendingBetterPortalsDimensionCompileId;
        if (pendingDimensionId == Integer.MIN_VALUE || isBetterPortalsPipelineBusy()) {
            return;
        }
        pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
        if (!areShadersEnabled() || isOffPack(selectedPackName) || pendingDimensionId == compiledDimensionId) {
            return;
        }
        if (!ensureSelectedPackLoaded()) {
            return;
        }

        int previousDimensionId = compiledDimensionId;
        MainMod.LOGGER.info("Compiling deferred Better Portals shader dimension {} for shaderpack '{}'",
                pendingDimensionId,
                selectedPackName);
        switchCompiledPipelineDimension(pendingDimensionId, true);

        if (previousDimensionId != Integer.MIN_VALUE && previousDimensionId != compiledDimensionId) {
            ShaderProperties previousProperties = getShaderProperties(currentPack.getName(), currentOptionOverrides, previousDimensionId);
            String previousCacheKey = compiledPipelineCacheKey(currentPack.getName(), currentOptionOverrides, previousDimensionId);
            if (PipelineContext.getInstance().activateCachedCompiledPipeline(previousCacheKey, currentPack, currentOptionOverrides, previousProperties)) {
                compiledDimensionId = previousDimensionId;
                compiledPackName = currentPack.getName();
                pendingPipelineReload = false;
                MainMod.LOGGER.info("[BetterPortalsPipeline] restored-parent-after-prewarm parent={} pack={} cacheKey={}",
                        previousDimensionId,
                        compiledPackName,
                        previousCacheKey);
            }
        }
    }

    private void runPendingBetterPortalsParentRestore() {
        int parentDimensionId = pendingBetterPortalsParentRestoreDimensionId;
        if (parentDimensionId == Integer.MIN_VALUE || isBetterPortalsPipelineBusy()) {
            return;
        }
        pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
        if (!areShadersEnabled() || isOffPack(selectedPackName) || parentDimensionId == compiledDimensionId) {
            return;
        }
        if (!ensureSelectedPackLoaded()) {
            return;
        }

        MainMod.LOGGER.info("[BetterPortalsPipeline] restore-parent-from-queue parent={} compiled={} pending={} pack={}",
                parentDimensionId,
                compiledDimensionId,
                pendingBetterPortalsDimensionCompileId,
                selectedPackName);
        switchCompiledPipelineDimension(parentDimensionId, true);
    }

    private boolean isBetterPortalsPipelineBusy() {
        return BetterPortalsCompat.isRenderingNestedView()
                || PipelineContext.getInstance().isRenderingBetterPortalsExternalWorldFrame()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    public String describeBetterPortalsPipelineState() {
        return "selected=" + selectedPackName
                + ", enabled=" + shadersEnabled
                + ", current=" + (currentPack != null ? currentPack.getName() : "null")
                + ", compiled=" + compiledPackName + "@" + compiledDimensionId
                + ", pendingReload=" + pendingPipelineReload
                + ", pendingFirstFrame=" + firstWorldFrameCompileGate.isPending() + "@" + firstWorldFrameCompileGate.pendingDimensionId()
                + ", pendingBp=" + pendingBetterPortalsDimensionCompileId
                + ", pendingParent=" + pendingBetterPortalsParentRestoreDimensionId
                + ", prewarmed=" + betterPortalsPrewarmedCacheKeys.size();
    }

    public void preparePipelineForWorldLoad(int dimensionId) {
        if (isOffPack(selectedPackName)) {
            shadersEnabled = false;
            clearBetterPortalsPendingState();
            PipelineContext.getInstance().setActive(false);
            return;
        }

        if (!isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' disappeared during world load; disabling shaders.", selectedPackName);
            return;
        }

        if (!automaticShaderDisablingEnabled()) {
            clearBetterPortalsPendingState();
            if (!shadersEnabled) {
                captureShaderlessBloomMaterialRules(dimensionId);
                PipelineContext.getInstance().setActive(false);
                return;
            }
            if (!ensureSelectedPackLoaded()) {
                return;
            }
            queuePipelineForFirstWorldFrame(dimensionId);
            return;
        }

        if (shadersEnabled || PipelineContext.getInstance().isActive()) {
            MainMod.LOGGER.info("Forcing shaders inactive on world load; selected shaderpack remains '{}'", selectedPackName);
        }
        shadersEnabled = false;
        pendingPipelineReload = !isOffPack(selectedPackName);
        compiledDimensionId = Integer.MIN_VALUE;
        clearBetterPortalsPendingState();
        firstWorldFrameCompileGate.clear();
        compiledPackName = OFF_PACK_NAME;
        clearShaderPropertiesCache();
        captureShaderlessBloomMaterialRules(dimensionId);
        PipelineContext.getInstance().cleanup();
        rebuildInactiveVanillaRenderers();
    }

    /**
     * A selected pack remains useful while its programs are disabled: its
     * block.properties file classifies terrain bloom sources.  Parse it once
     * on Minecraft's client thread, then publish only the immutable rules to
     * shaderless terrain workers.  In particular, do not call this from a
     * Nothirium compile task; evaluating pack feature conditions reads GL
     * capabilities and therefore requires the current client context.
     */
    private void captureShaderlessBloomMaterialRules(int dimensionId) {
        if (shadersEnabled || isOffPack(selectedPackName) || !isMinecraftClientThread()) {
            return;
        }
        if (!ensureSelectedPackLoaded()) {
            return;
        }

        ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides, dimensionId);
        PipelineContext context = PipelineContext.getInstance();
        context.captureShaderlessBloomBlockIds(properties);
        context.rebuildShaderlessBloomTerrain("selected shaderpack material rules");
    }

    private static boolean isMinecraftClientThread() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        return minecraft != null && MinecraftReflectionCompat.callBoolean(
                minecraft,
                new String[]{"func_152345_ab", "isCallingFromMinecraftThread"},
                MinecraftReflectionCompat.NO_PARAMETERS,
                false
        );
    }

    private boolean automaticShaderDisablingEnabled() {
        return MainMod.getClientSettingsConfig() == null
                || MainMod.getClientSettingsConfig().automaticShaderDisablingEnabled();
    }

    public void compilePipelineForDimensionSwitch(int dimensionId) {
        if (dimensionId == Integer.MIN_VALUE || dimensionId == compiledDimensionId) {
            return;
        }
        if (!areShadersEnabled() || isOffPack(selectedPackName)) {
            return;
        }
        if (!isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' disappeared during dimension switch; disabling shaders.", selectedPackName);
            return;
        }
        if (!ensureSelectedPackLoaded()) {
            return;
        }
        queuePipelineForFirstWorldFrame(dimensionId);
    }

    /**
     * Compiles a pipeline only once the client has a playable world and just
     * before EntityRenderer begins that world's first frame. This keeps menu
     * startup and the download-terrain screen shaderless while preserving the
     * no-unshaded-world-frame guarantee.
     */
    public boolean compilePendingPipelineBeforeFirstWorldFrame() {
        if (!firstWorldFrameCompileGate.isPending() || !ShaderPipelineWorldLoadGate.isPlayableWorldReady()) {
            return false;
        }
        if (!areShadersEnabled() || isOffPack(selectedPackName)) {
            firstWorldFrameCompileGate.clear();
            return false;
        }
        if (!isPackAvailable(selectedPackName)) {
            firstWorldFrameCompileGate.clear();
            fallbackToOff("Selected shaderpack '{}' disappeared before its first world frame; disabling shaders.", selectedPackName);
            return false;
        }
        if (!ensureSelectedPackLoaded()) {
            return false;
        }

        int dimensionId = firstWorldFrameCompileGate.pendingDimensionId();
        if (dimensionId == Integer.MIN_VALUE) {
            dimensionId = getClientDimensionId();
        }
        if (dimensionId == Integer.MIN_VALUE) {
            return false;
        }
        ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides, dimensionId);
        if (!initializeCurrentPipelineNow(properties, true, dimensionId, true)) {
            return false;
        }
        firstWorldFrameCompileGate.clear();
        MainMod.LOGGER.info("Compiled shaderpack '{}' for world dimension {} immediately before its first client frame.",
                selectedPackName, dimensionId);
        return true;
    }

    public void setShadersEnabled(boolean enabled) {
        long startedNanos = System.nanoTime();
        boolean wasEnabled = shadersEnabled;
        boolean wasActive = PipelineContext.getInstance().isActive();
        boolean wasPendingReload = pendingPipelineReload;
        long afterConfigNanos = startedNanos;
        long afterPackLoadNanos = startedNanos;
        long afterPipelineNanos = startedNanos;
        long afterCleanupNanos = startedNanos;
        long afterVanillaNanos = startedNanos;
        try {
            if (isOffPack(selectedPackName)) {
                shadersEnabled = false;
            } else if (enabled && !isPackAvailable(selectedPackName)) {
                fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", selectedPackName);
                return;
            } else {
                shadersEnabled = enabled;
            }
            saveShaderConfig();
            afterConfigNanos = System.nanoTime();
            if (shadersEnabled && (pendingPipelineReload || currentPack == null || !selectedPackName.equals(currentPack.getName()))) {
                if (pendingPipelineReload && currentPack != null && selectedPackName.equals(currentPack.getName()) && !isInternalPack(currentPack)) {
                    closeCurrentPack();
                    currentPack = null;
                    clearShaderPropertiesCache();
                }
                if (!ensureSelectedPackLoaded()) {
                    return;
                }
                afterPackLoadNanos = System.nanoTime();
                ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
                if (!initializeCurrentPipeline(properties, true)) {
                    return;
                }
                afterPipelineNanos = System.nanoTime();
            }
            if (shadersEnabled) {
                if (firstWorldFrameCompileGate.isPending()) {
                    PipelineContext.getInstance().setActive(false);
                } else {
                    PipelineContext.getInstance().setActive(true);
                }
                if (!PipelineContext.getInstance().isActive()) {
                    if (!firstWorldFrameCompileGate.isPending()) {
                        markPipelineInactive();
                    }
                }
            } else {
                PipelineContext.getInstance().cleanup();
                afterCleanupNanos = System.nanoTime();
                compiledDimensionId = Integer.MIN_VALUE;
                clearBetterPortalsPendingState();
                compiledPackName = OFF_PACK_NAME;
                pendingPipelineReload = currentPack != null && !isInternalPack(currentPack);
                rebuildInactiveVanillaRenderers();
                afterVanillaNanos = System.nanoTime();
                PipelineContext.getInstance().recoverShaderlessBloomAfterShaderDisable("shader-toggle-off");
            }
        } finally {
            logShaderToggleTiming(enabled, wasEnabled, wasActive, wasPendingReload,
                    startedNanos, afterConfigNanos, afterPackLoadNanos,
                    afterPipelineNanos, afterCleanupNanos, afterVanillaNanos);
        }
    }

    private void logShaderToggleTiming(boolean requestedEnabled, boolean wasEnabled, boolean wasActive,
                                       boolean wasPendingReload, long startedNanos, long afterConfigNanos,
                                       long afterPackLoadNanos, long afterPipelineNanos,
                                       long afterCleanupNanos, long afterVanillaNanos) {
        if (shaderToggleTimingProbeLogs >= SHADER_TOGGLE_TIMING_PROBE_LIMIT) {
            return;
        }
        shaderToggleTimingProbeLogs++;
        long now = System.nanoTime();
        MainMod.LOGGER.info(
                "[AUSMShaderToggleTiming] call={} requestEnabled={} previousEnabled={} previousActive={} previousPendingReload={} finalEnabled={} finalActive={} pack={} totalMs={} configMs={} packLoadMs={} pipelineInitMs={} cleanupMs={} vanillaRebuildMs={} postVanillaMs={}",
                shaderToggleTimingProbeLogs,
                requestedEnabled,
                wasEnabled,
                wasActive,
                wasPendingReload,
                shadersEnabled,
                PipelineContext.getInstance().isActive(),
                selectedPackName,
                toggleMillis(now - startedNanos),
                toggleMillis(afterConfigNanos - startedNanos),
                toggleMillis(afterPackLoadNanos - afterConfigNanos),
                toggleMillis(afterPipelineNanos - afterPackLoadNanos),
                toggleMillis(afterCleanupNanos - afterPipelineNanos),
                toggleMillis(afterVanillaNanos - afterCleanupNanos),
                toggleMillis(now - afterVanillaNanos)
        );
    }

    private static double toggleMillis(long nanos) {
        return Math.max(0.0D, nanos / 1_000_000.0D);
    }

    public Map<String, String> getCurrentOptionOverrides() {
        return currentOptionOverrides;
    }

    public Map<String, String> getOptionOverrides(String packName) {
        return loadOptionOverrides(packName);
    }

    public ShaderProperties getShaderProperties(String packName) {
        return getShaderProperties(packName, getOptionOverrides(packName));
    }

    public ShaderProperties getShaderProperties(String packName, Map<String, String> overrides) {
        return getShaderProperties(packName, overrides, getEffectiveRenderDimensionId());
    }

    private ShaderProperties getShaderProperties(String packName, Map<String, String> overrides, int dimensionId) {
        if (isOffPack(packName)) {
            return ShaderProperties.load(NoneShaderPack.INSTANCE, Map.of());
        }

        Map<String, String> safeOverrides = overrides == null || overrides.isEmpty() ? Map.of() : Map.copyOf(overrides);
        int safeDimensionId = dimensionId == Integer.MIN_VALUE ? ShaderDimensionContext.currentDimensionId() : dimensionId;
        String cacheKey = ShaderPropertiesCache.key(
                packName, safeOverrides, safeDimensionId, shaderPackFingerprint(packName));
        ShaderProperties cached = shaderPropertiesCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean useCurrentPack = isCurrentPack(packName) && currentPack != null && !isInternalPack(currentPack);
        ShaderPack pack = useCurrentPack ? currentPack : openPack(packName);
        if (pack == null) {
            return ShaderProperties.load(NoneShaderPack.INSTANCE, Map.of());
        }

        try {
            ShaderProperties properties = ShaderDimensionContext.withDimension(
                    safeDimensionId,
                    () -> ShaderProperties.load(pack, safeOverrides)
            );
            shaderPropertiesCache.put(cacheKey, properties);
            return properties;
        } finally {
            if (!useCurrentPack) {
                try {
                    pack.close();
                } catch (IOException e) {
                    MainMod.LOGGER.error("Failed to close inspected shaderpack '{}'", packName, e);
                }
            }
        }
    }

    public void setShaderOption(String name, String value) {
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>(currentOptionOverrides);
        values.put(name, value);
        setShaderOptions(values);
    }

    public void setShaderOptions(Map<String, String> values) {
        if (isInternalPack(currentPack)) {
            return;
        }

        setShaderOptions(currentPack.getName(), values);
    }

    public void setShaderOptions(String packName, Map<String, String> values) {
        if (isOffPack(packName)) {
            return;
        }

        Map<String, String> copy = values == null || values.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
        boolean currentPackTarget = isCurrentPack(packName);
        if (currentPackTarget && currentOptionOverrides.equals(copy)) {
            return;
        }

        ShaderProperties properties = getShaderProperties(packName, copy);
        saveOptionOverrides(packName, copy);
        if (currentPackTarget) {
            currentOptionOverrides = copy;
            PipelineContext.getInstance().clearCompiledPipelineCache();
            clearBetterPortalsPendingState();
            if (!shadersEnabled) {
                pendingPipelineReload = true;
                PipelineContext.getInstance().cleanup();
                rebuildInactiveVanillaRenderers();
                return;
            }
            initializeCurrentPipeline(properties, true);
        }
    }

    public void resetShaderOptions() {
        if (isInternalPack(currentPack)) {
            return;
        }

        resetShaderOptions(currentPack.getName());
    }

    public void resetShaderOptions(String packName) {
        if (isOffPack(packName)) {
            return;
        }

        configurationStore.resetOptions(packName);
        if (isCurrentPack(packName)) {
            if (currentOptionOverrides.isEmpty()) {
                return;
            }
            currentOptionOverrides = Map.of();
            PipelineContext.getInstance().clearCompiledPipelineCache();
            clearBetterPortalsPendingState();
            if (!shadersEnabled) {
                pendingPipelineReload = true;
                PipelineContext.getInstance().cleanup();
                rebuildInactiveVanillaRenderers();
                return;
            }
            ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
            initializeCurrentPipeline(properties, true);
        }
    }

    private boolean initializeCurrentPipeline(ShaderProperties properties, boolean activate) {
        return initializeCurrentPipeline(properties, activate, getEffectiveRenderDimensionId(), true);
    }

    private boolean initializeCurrentPipeline(ShaderProperties properties, boolean activate, int dimensionId) {
        return initializeCurrentPipeline(properties, activate, dimensionId, true);
    }

    private boolean initializeCurrentPipeline(ShaderProperties properties, boolean activate, int dimensionId, boolean fullTerrainRefresh) {
        if (activate && !ShaderPipelineWorldLoadGate.isPlayableWorldReady()) {
            queuePipelineForFirstWorldFrame(dimensionId);
            return true;
        }
        return initializeCurrentPipelineNow(properties, activate, dimensionId, fullTerrainRefresh);
    }

    private boolean initializeCurrentPipelineNow(ShaderProperties properties, boolean activate, int dimensionId, boolean fullTerrainRefresh) {
        try {
            PipelineContext context = PipelineContext.getInstance();
            String cacheKey = activate
                    ? compiledPipelineCacheKey(currentPack.getName(), currentOptionOverrides, dimensionId)
                    : null;
            ShaderDimensionContext.runWithDimension(dimensionId, () -> {
                if (cacheKey != null) {
                    context.initializeCached(cacheKey, currentPack, currentOptionOverrides, properties);
                } else {
                    context.initialize(currentPack, currentOptionOverrides, properties);
                }
            });
            context.setActive(activate);
            if (activate && !context.isActive()) {
                MainMod.LOGGER.warn("Shaderpack '{}' did not produce an active pipeline.", currentPack.getName());
                markPipelineInactive();
                return false;
            }
            boolean terrainAlreadyRebuilt = context.consumeTerrainRebuiltDuringLastInitialization();
            boolean terrainCacheReusable = context.consumeTerrainCacheReusableDuringLastInitialization();
            compiledDimensionId = dimensionId;
            if (pendingBetterPortalsDimensionCompileId == dimensionId) {
                pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
            }
            if (pendingBetterPortalsParentRestoreDimensionId == dimensionId) {
                pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
            }
            compiledPackName = currentPack.getName();
            pendingPipelineReload = false;
            if (activate && !terrainAlreadyRebuilt && !terrainCacheReusable) {
                if (fullTerrainRefresh && dimensionId == getClientDimensionId()) {
                    context.scheduleSingleFullWorldTerrainRefresh();
                } else {
                    context.scheduleWorldTerrainRefresh();
                }
            }
            return true;
        } catch (RuntimeException e) {
            String packName = currentPack == null ? "<none>" : currentPack.getName();
            MainMod.LOGGER.error("Failed to initialize shaderpack '{}'", packName, e);
            PipelineContext.getInstance().cleanup();
            markPipelineInactive();
            return false;
        }
    }

    private void queuePipelineForFirstWorldFrame(int dimensionId) {
        firstWorldFrameCompileGate.queue(dimensionId);
        pendingPipelineReload = true;
        PipelineContext.getInstance().setActive(false);
        MainMod.LOGGER.info("Deferred shaderpack '{}' compilation until the first playable world frame (dimension {}).",
                selectedPackName, dimensionId);
    }


    private void markPipelineInactive() {
        compiledDimensionId = Integer.MIN_VALUE;
        clearBetterPortalsPendingState();
        compiledPackName = OFF_PACK_NAME;
        pendingPipelineReload = currentPack != null && !isInternalPack(currentPack);
        rebuildInactiveVanillaRenderers();
    }

    private String compiledPipelineCacheKey(String packName, Map<String, String> optionOverrides, int dimensionId) {
        Map<String, String> safeOverrides = optionOverrides == null || optionOverrides.isEmpty()
                ? Map.of()
                : Map.copyOf(optionOverrides);
        return packName
                + "|world" + dimensionId
                + "|pack=" + shaderPackFingerprint(packName)
                + "|options=" + safeOverrides.hashCode();
    }

    private String shaderPackFingerprint(String packName) {
        if (packName == null || isOffPack(packName) || INTERNAL_PACK_NAME.equals(packName)) {
            return "internal";
        }
        return repository.fingerprint(packName);
    }

    private void rebuildInactiveVanillaRenderers() {
        PipelineContext.getInstance().setActive(false);
    }

    private boolean ensureSelectedPackLoaded() {
        if (isOffPack(selectedPackName)) {
            return false;
        }
        if (currentPack != null && selectedPackName.equals(currentPack.getName())) {
            return true;
        }

        ShaderPack newPack = openPack(selectedPackName);
        if (newPack == null) {
            fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", selectedPackName);
            return false;
        }

        closeCurrentPack();
        currentPack = newPack;
        currentOptionOverrides = loadOptionOverrides(newPack.getName());
        clearShaderPropertiesCacheExcept(newPack.getName());
        pendingPipelineReload = true;
        compiledDimensionId = Integer.MIN_VALUE;
        clearBetterPortalsPendingState();
        compiledPackName = OFF_PACK_NAME;
        return true;
    }

    private void closeCurrentPack() {
        try {
            if (this.currentPack != null && this.currentPack != NoneShaderPack.INSTANCE) {
                this.currentPack.close();
            }
        } catch (IOException e) {
            String previousName = this.currentPack != null ? this.currentPack.getName() : "<none>";
            MainMod.LOGGER.error("Failed to close previous shaderpack '{}'", previousName, e);
        }
    }

    private Map<String, String> loadOptionOverrides(String packName) {
        return configurationStore.loadOptions(packName, INTERNAL_PACK_NAME);
    }

    private void saveOptionOverrides(String packName, Map<String, String> values) {
        configurationStore.saveOptions(packName, values);
    }

    private void clearShaderPropertiesCache() {
        shaderPropertiesCache.clear();
    }

    private void clearShaderPropertiesCacheExcept(String packName) {
        shaderPropertiesCache.clearExcept(packName);
    }

    private void saveShaderConfig() {
        configurationStore.save(
                isOffPack(selectedPackName) ? OFF_PACK_NAME : selectedPackName,
                shadersEnabled);
    }

    private boolean isCurrentPack(String packName) {
        if (packName == null) {
            return false;
        }
        return selectedPackName.equals(packName);
    }

    private boolean isPackAvailable(String packName) {
        return !isOffPack(packName) && repository.isAvailable(packName);
    }

    private void fallbackToOff(String message, String packName) {
        MainMod.LOGGER.warn(message, packName);
        selectedPackName = OFF_PACK_NAME;
        shadersEnabled = false;
        clearBetterPortalsPendingState();
        saveShaderConfig();
        setPack(NoneShaderPack.INSTANCE);
    }

    private void clearBetterPortalsPendingState() {
        pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
        pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
        betterPortalsPrewarmedCacheKeys.clear();
    }

    private int getClientDimensionId() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        if (provider == null) {
            return Integer.MIN_VALUE;
        }
        return MinecraftReflectionCompat.providerDimension(provider);
    }

    private int getEffectiveRenderDimensionId() {
        int portalDimensionId = BetterPortalsCompat.currentShaderRenderPassDimensionId();
        if (portalDimensionId != Integer.MIN_VALUE) {
            return portalDimensionId;
        }
        return getClientDimensionId();
    }

    private static boolean isOffPack(String packName) {
        return packName == null || packName.isBlank() || packName.equalsIgnoreCase(OFF_PACK_NAME);
    }

    private static boolean isInternalPack(ShaderPack pack) {
        return pack == null || INTERNAL_PACK_NAME.equals(pack.getName());
    }

}
