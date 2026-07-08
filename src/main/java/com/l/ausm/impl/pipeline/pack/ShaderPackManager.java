package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.shader.ShaderPackController;
import com.l.ausm.api.shader.ShaderPackInfo;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

public class ShaderPackManager implements ShaderPackController {
    private static final String OFF_PACK_NAME = "OFF";
    private static final String INTERNAL_PACK_NAME = "(internal)";
    private static final int SHADER_PROPERTIES_CACHE_LIMIT = 24;

    private final Path shaderpacksDir;
    private final Path optionOverridesDir;
    private final Path shaderConfigFile;
    private ShaderPack currentPack = NoneShaderPack.INSTANCE;
    private Map<String, String> currentOptionOverrides = Map.of();
    private final Map<String, ShaderProperties> shaderPropertiesCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ShaderProperties> eldest) {
            return size() > SHADER_PROPERTIES_CACHE_LIMIT;
        }
    };
    private String selectedPackName = OFF_PACK_NAME;
    private boolean shadersEnabled = false;
    private boolean pendingPipelineReload = false;
    private int compiledDimensionId = Integer.MIN_VALUE;
    private int pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
    private int pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
    private final Set<String> betterPortalsPrewarmedCacheKeys = new HashSet<>();
    private String lastDeferredBetterPortalsCacheKey = "";
    private String compiledPackName = OFF_PACK_NAME;

    public ShaderPackManager(Path minecraftRunDir) {
        this.shaderpacksDir = minecraftRunDir.resolve("shaderpacks");
        Path configDir = minecraftRunDir.resolve("config").resolve("ausm");
        this.optionOverridesDir = configDir.resolve("shader-options");
        this.shaderConfigFile = configDir.resolve("shaders.properties");
        ensureDirectoryExists();
    }

    private void ensureDirectoryExists() {
        if (!Files.exists(shaderpacksDir)) {
            try {
                MainMod.LOGGER.info("Shaderpacks directory not found, creating at: {}", shaderpacksDir.toAbsolutePath());
                Files.createDirectories(shaderpacksDir);
                Files.createDirectories(optionOverridesDir);
            } catch (IOException e) {
                MainMod.LOGGER.error("Failed to create shaderpacks directory!", e);
            }
        }
        try {
            Files.createDirectories(optionOverridesDir);
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to create shader option config directory!", e);
        }
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
        Properties properties = new Properties();
        if (Files.isRegularFile(shaderConfigFile)) {
            try (InputStream stream = Files.newInputStream(shaderConfigFile)) {
                properties.load(stream);
            } catch (IOException e) {
                MainMod.LOGGER.error("Failed to read shader configuration", e);
            }
        }

        selectedPackName = properties.getProperty("selectedPack", OFF_PACK_NAME);
        shadersEnabled = false;
        currentPack = NoneShaderPack.INSTANCE;
        currentOptionOverrides = isOffPack(selectedPackName) ? Map.of() : loadOptionOverrides(selectedPackName);
        pendingPipelineReload = !isOffPack(selectedPackName) && isPackAvailable(selectedPackName);
        compiledDimensionId = Integer.MIN_VALUE;
        pendingBetterPortalsDimensionCompileId = Integer.MIN_VALUE;
        pendingBetterPortalsParentRestoreDimensionId = Integer.MIN_VALUE;
        betterPortalsPrewarmedCacheKeys.clear();
        compiledPackName = OFF_PACK_NAME;
        PipelineContext.getInstance().setActive(false);
    }

    private ShaderPack openPack(String packName) {
        Path packPath = resolveShaderPackPath(packName);
        if (packPath == null) {
            MainMod.LOGGER.warn("Attempted to load shaderpack with invalid path name '{}'; disabling shaders.", packName);
            return null;
        }
        if (!Files.exists(packPath)) {
            MainMod.LOGGER.warn("Attempted to load shaderpack '{}', but it does not exist at '{}'", packName, packPath.toAbsolutePath());
            return null;
        }

        try {
            if (Files.isDirectory(packPath)) {
                MainMod.LOGGER.info("Loading folder shaderpack: {}", packName);
                return new FolderShaderPack(packPath);
            }
            if (packName.endsWith(".zip")) {
                MainMod.LOGGER.info("Loading zip shaderpack: {}", packName);
                return new ZipShaderPack(packPath);
            }

            MainMod.LOGGER.warn("Cannot load shaderpack '{}' because it is neither a folder nor a zip file.", packName);
            return null;
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to load shaderpack '{}'", packName, e);
            return null;
        }
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
        return shaderpacksDir;
    }

    public String importShaderPack(Path source) throws IOException {
        if (source == null || !isValidPackPath(source)) {
            return null;
        }

        Files.createDirectories(shaderpacksDir);
        String name = source.getFileName().toString();
        Path target = shaderpacksDir.resolve(name);
        if (Files.exists(target)) {
            if (Files.isSameFile(source, target)) {
                return name;
            }
            throw new FileAlreadyExistsException(target.toString());
        }

        if (Files.isDirectory(source)) {
            copyDirectory(source, target);
        } else {
            Files.copy(source, target);
        }
        return name;
    }

    @Override
    public ShaderPackInfo getCurrentShaderPack() {
        String name = isOffPack(selectedPackName) ? OFF_PACK_NAME : selectedPackName;
        return new ShaderPackInfo(name, areShadersEnabled(), isOffPack(name) || isPackAvailable(name));
    }

    public String getSelectedPackName() {
        return selectedPackName;
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
        List<String> packs = new ArrayList<>();
        packs.add(OFF_PACK_NAME);

        if (!Files.exists(shaderpacksDir)) {
            return packs;
        }

        try (Stream<Path> stream = Files.list(shaderpacksDir)) {
            stream.forEach(path -> {
                if (isValidPackPath(path)) {
                    packs.add(path.getFileName().toString());
                }
            });
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to list available shaderpacks!", e);
        }
        packs.subList(1, packs.size()).sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        return packs;
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

        if (shadersEnabled || PipelineContext.getInstance().isActive()) {
            MainMod.LOGGER.info("Forcing shaders inactive on world load; selected shaderpack remains '{}'", selectedPackName);
        }
        shadersEnabled = false;
        pendingPipelineReload = !isOffPack(selectedPackName);
        compiledDimensionId = Integer.MIN_VALUE;
        clearBetterPortalsPendingState();
        compiledPackName = OFF_PACK_NAME;
        clearShaderPropertiesCache();
        PipelineContext.getInstance().cleanup();
        rebuildInactiveVanillaRenderers();
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

        switchCompiledPipelineDimension(dimensionId, false);
    }

    public void setShadersEnabled(boolean enabled) {
        if (isOffPack(selectedPackName)) {
            shadersEnabled = false;
        } else if (enabled && !isPackAvailable(selectedPackName)) {
            fallbackToOff("Selected shaderpack '{}' is no longer available; disabling shaders.", selectedPackName);
            return;
        } else {
            shadersEnabled = enabled;
        }
        saveShaderConfig();
        if (shadersEnabled && (pendingPipelineReload || currentPack == null || !selectedPackName.equals(currentPack.getName()))) {
            if (pendingPipelineReload && currentPack != null && selectedPackName.equals(currentPack.getName()) && !isInternalPack(currentPack)) {
                closeCurrentPack();
                currentPack = null;
                clearShaderPropertiesCache();
            }
            if (!ensureSelectedPackLoaded()) {
                return;
            }
            ShaderProperties properties = getShaderProperties(currentPack.getName(), currentOptionOverrides);
            if (!initializeCurrentPipeline(properties, true)) {
                return;
            }
        }
        if (shadersEnabled) {
            PipelineContext.getInstance().setActive(true);
            if (!PipelineContext.getInstance().isActive()) {
                markPipelineInactive();
            }
        } else {
            PipelineContext.getInstance().cleanup();
            compiledDimensionId = Integer.MIN_VALUE;
            clearBetterPortalsPendingState();
            compiledPackName = OFF_PACK_NAME;
            pendingPipelineReload = currentPack != null && !isInternalPack(currentPack);
            rebuildInactiveVanillaRenderers();
            PipelineContext.getInstance().recoverShaderlessBloomAfterShaderDisable("shader-toggle-off");
        }
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
        String cacheKey = shaderPropertiesCacheKey(packName, safeOverrides, safeDimensionId, shaderPackFingerprint(packName));
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

        try {
            Files.deleteIfExists(optionFile(packName));
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to reset shader options for '{}'", packName, e);
        }
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

        Path path = resolveShaderPackPath(packName);
        if (path == null) {
            return "invalid";
        }
        if (!Files.exists(path)) {
            return "missing";
        }

        if (Files.isRegularFile(path)) {
            return Long.toString(safePathFingerprint(path));
        }

        try (Stream<Path> stream = Files.walk(path)) {
            long fingerprint = stream
                    .filter(Files::isRegularFile)
                    .mapToLong(this::safePathFingerprint)
                    .reduce(17L, (current, value) -> current * 31L + value);
            return Long.toString(fingerprint);
        } catch (IOException e) {
            MainMod.LOGGER.warn("Failed to fingerprint shaderpack '{}'; falling back to directory timestamp.", packName, e);
            return Long.toString(safePathFingerprint(path));
        }
    }

    private long safePathFingerprint(Path path) {
        try {
            long modified = Files.getLastModifiedTime(path).toMillis();
            long size = Files.isRegularFile(path) ? Files.size(path) : 0L;
            return modified * 31L + size;
        } catch (IOException e) {
            return 0L;
        }
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
        if (packName == null || INTERNAL_PACK_NAME.equals(packName)) {
            return Map.of();
        }

        Path file = optionFile(packName);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }

        Properties properties = new Properties();
        try (InputStream stream = Files.newInputStream(file)) {
            properties.load(stream);
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to read shader options for '{}'", packName, e);
            return Map.of();
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        return values;
    }

    private void saveOptionOverrides(String packName, Map<String, String> values) {
        Properties properties = new Properties();
        values.forEach(properties::setProperty);

        try {
            Files.createDirectories(optionOverridesDir);
            try (OutputStream stream = Files.newOutputStream(optionFile(packName))) {
                properties.store(stream, "AUSM shader option overrides");
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to save shader options for '{}'", packName, e);
        }
    }

    private Path optionFile(String packName) {
        String safeName = packName.replaceAll("[^A-Za-z0-9._-]", "_");
        return optionOverridesDir.resolve(safeName + ".properties");
    }

    private void clearShaderPropertiesCache() {
        shaderPropertiesCache.clear();
    }

    private void clearShaderPropertiesCacheExcept(String packName) {
        if (packName == null || packName.isBlank()) {
            clearShaderPropertiesCache();
            return;
        }
        shaderPropertiesCache.keySet().removeIf(key -> !isShaderPropertiesCacheKeyForPack(key, packName));
    }

    private void saveShaderConfig() {
        Properties properties = new Properties();
        properties.setProperty("selectedPack", isOffPack(selectedPackName) ? OFF_PACK_NAME : selectedPackName);
        properties.setProperty("enabled", Boolean.toString(shadersEnabled));

        try {
            Files.createDirectories(shaderConfigFile.getParent());
            try (OutputStream stream = Files.newOutputStream(shaderConfigFile)) {
                properties.store(stream, "AUSM shader configuration");
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("Failed to save shader configuration", e);
        }
    }

    private boolean isCurrentPack(String packName) {
        if (packName == null) {
            return false;
        }
        return selectedPackName.equals(packName);
    }

    private boolean isPackAvailable(String packName) {
        if (isOffPack(packName)) {
            return false;
        }

        Path packPath = resolveShaderPackPath(packName);
        if (packPath == null) {
            return false;
        }
        return Files.isDirectory(packPath) || Files.isRegularFile(packPath);
    }

    private Path resolveShaderPackPath(String packName) {
        if (packName == null || packName.isEmpty()) {
            return null;
        }
        try {
            Path direct = shaderpacksDir.resolve(packName);
            if (Files.exists(direct)) {
                return direct;
            }
            Path aliasMatch = findShaderPackPathByAlias(packName);
            return aliasMatch != null ? aliasMatch : direct;
        } catch (InvalidPathException e) {
            Path aliasMatch = findShaderPackPathByAlias(packName);
            if (aliasMatch != null) {
                MainMod.LOGGER.warn("Resolved shaderpack '{}' through directory scan because the JVM filesystem encoding rejected the saved name.", packName);
                return aliasMatch;
            }
            MainMod.LOGGER.warn("Ignoring shaderpack name with invalid filesystem encoding: '{}'", packName);
            return null;
        }
    }

    private Path findShaderPackPathByAlias(String packName) {
        if (!Files.exists(shaderpacksDir)) {
            return null;
        }

        String targetAlias = shaderPackAlias(packName);
        if (targetAlias.isEmpty()) {
            return null;
        }

        Path bestPath = null;
        int bestScore = Integer.MAX_VALUE;
        int bestLengthDelta = Integer.MAX_VALUE;
        try (Stream<Path> stream = Files.list(shaderpacksDir)) {
            for (Path path : stream.toList()) {
                if (!isValidPackPath(path)) {
                    continue;
                }
                Path fileName = path.getFileName();
                String candidateName = fileName != null ? fileName.toString() : "";
                int score = shaderPackAliasDistance(targetAlias, shaderPackAlias(candidateName));
                if (score < 0) {
                    continue;
                }
                int lengthDelta = Math.abs(candidateName.length() - packName.length());
                if (score < bestScore
                        || (score == bestScore && lengthDelta < bestLengthDelta)
                        || (score == bestScore && lengthDelta == bestLengthDelta && Files.isDirectory(path) && bestPath != null && !Files.isDirectory(bestPath))) {
                    bestPath = path;
                    bestScore = score;
                    bestLengthDelta = lengthDelta;
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("Failed to scan shaderpacks for alias match for '{}'", packName, e);
        }
        return bestPath;
    }

    private static int shaderPackAliasDistance(String targetAlias, String candidateAlias) {
        if (targetAlias.equals(candidateAlias)) {
            return 0;
        }
        int distance = boundedEditDistance(targetAlias, candidateAlias, 2);
        return distance <= 2 ? distance : -1;
    }

    private static String shaderPackAlias(String name) {
        if (name == null) {
            return "";
        }
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if (ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9') {
                builder.append(ch);
            }
        }
        String alias = builder.toString();
        if (alias.endsWith("zip")) {
            alias = alias.substring(0, alias.length() - 3);
        }
        return alias;
    }

    private static int boundedEditDistance(String left, String right, int maxDistance) {
        if (Math.abs(left.length() - right.length()) > maxDistance) {
            return maxDistance + 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            int rowMin = current[0];
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMin = Math.min(rowMin, current[j]);
            }
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private boolean isValidPackPath(Path path) {
        if (path == null || !Files.exists(path)) {
            return false;
        }
        if (Files.isDirectory(path)) {
            return true;
        }
        String fileName = path.getFileName().toString().toLowerCase();
        return Files.isRegularFile(path) && fileName.endsWith(".zip");
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path path : stream.sorted().toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
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
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        net.minecraft.client.multiplayer.WorldClient world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        net.minecraft.world.WorldProvider provider = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world);
        if (provider == null) {
            return Integer.MIN_VALUE;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(provider);
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

    private static String shaderPropertiesCacheKey(String packName, Map<String, String> overrides, int dimensionId, String packFingerprint) {
        String safePackName = packName != null ? packName : "";
        StringBuilder builder = new StringBuilder(safePackName.length() + 32);
        builder.append(safePackName).append('\0').append(dimensionId).append('\0').append(packFingerprint != null ? packFingerprint : "");
        if (overrides != null && !overrides.isEmpty()) {
            new java.util.TreeMap<>(overrides).forEach((key, value) ->
                    builder.append('\0')
                            .append(key != null ? key : "")
                            .append('=')
                            .append(value != null ? value : ""));
        }
        return builder.toString();
    }

    private static boolean isShaderPropertiesCacheKeyForPack(String cacheKey, String packName) {
        String safePackName = packName != null ? packName : "";
        return cacheKey != null && cacheKey.startsWith(safePackName + '\0');
    }
}
