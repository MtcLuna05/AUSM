package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;

public final class NothiriumBypass {
    private static final String CHUNK_RENDER_MANAGER = "meldexun.nothirium.mc.renderer.ChunkRenderManager";
    private static final ThreadLocal<Integer> FORCED_BYPASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static boolean reflectionResolved;
    private static boolean reflectionFailed;
    private static Method getRendererMethod;
    private static Method getProviderMethod;
    private static Method getTaskDispatcherMethod;
    private static Method setDirtyMethod;
    private static Method allChangedMethod;
    private static Method disposeMethod;
    private static Method setupMethod;
    private static Method renderUtilCameraXMethod;
    private static Method renderUtilCameraYMethod;
    private static Method renderUtilCameraZMethod;
    private static Method renderUtilFrustumMethod;
    private static Method renderUtilFrameMethod;
    private static int blockUpdateLogs;
    private static int rendererRecoveryLogs;
    private static int rendererSetupLogs;
    private static int rendererSetupFailureLogs;
    private static int hybridMaintenanceLogs;
    private static int renderUtilProbeLogs;
    private static long lastIsolatedMainSetupNanos;
    private static final int BLOCK_UPDATE_LOG_LIMIT = 0;
    private static final int RENDERER_RECOVERY_LOG_LIMIT = 0;
    private static final int RENDERER_SETUP_LOG_LIMIT = 0;
    private static final int RENDER_UTIL_PROBE_LOG_LIMIT = 0;
    private static final long ISOLATED_MAIN_SETUP_INTERVAL_NANOS = 15_000_000L;

    private NothiriumBypass() {
    }

    public static boolean shouldBypass() {
        try {
            return shouldUseVanillaRenderGlobalForCurrentPass();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldBypassSetupTerrain() {
        try {
            if (shouldUseVanillaRenderGlobalForCurrentPass()) {
                return true;
            }
            if (!PipelineContext.getInstance().shouldUseNothiriumHybridVanillaMaintenance()) {
                return false;
            }
            boolean setup = setupNothiriumRendererNow();
            logHybridMaintenance("setupTerrain", setup);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldBypassChunkUpdates() {
        try {
            if (shouldUseVanillaRenderGlobalForCurrentPass()) {
                return true;
            }
            boolean bypass = PipelineContext.getInstance().shouldUseNothiriumHybridVanillaMaintenance();
            if (bypass) {
                logHybridMaintenance("updateChunks", true);
            }
            return bypass;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldBypassBlockUpdates() {
        return shouldBypassBlockUpdates(0, 0, 0, -1, -1, -1);
    }

    public static boolean shouldBypassBlockUpdates(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        try {
            if (!ensureRendererReady()) {
                logBlockUpdateDecision("disposed-unrecovered", minX, minY, minZ, maxX, maxY, maxZ, false, true);
                return true;
            }
            boolean vanillaRenderPath = shouldUseVanillaRenderGlobalForCurrentPass()
                    || shouldUseVanillaForShaderlessBetterPortalsBlockUpdates();
            if (!vanillaRenderPath) {
                logBlockUpdateDecision("native-nothirium", minX, minY, minZ, maxX, maxY, maxZ, false, false);
                return false;
            }
            boolean marked = markNothiriumChunksDirty(minX, minY, minZ, maxX, maxY, maxZ);
            boolean vanillaAvailable = !BetterPortalsCompat.isInstalled() || hasVanillaViewFrustum();
            boolean shaderlessBetterPortals = shouldUseVanillaForShaderlessBetterPortalsBlockUpdates();
            boolean bypass = vanillaAvailable && (marked || shaderlessBetterPortals);
            logBlockUpdateDecision(shaderlessBetterPortals ? "shaderless-bp-bypass-check" : "bypass-check",
                    minX, minY, minZ, maxX, maxY, maxZ, marked, bypass);
            return bypass;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean markAllChanged() {
        if (!resolveReflection() || allChangedMethod == null) {
            return false;
        }

        try {
            allChangedMethod.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    public static boolean ensureRendererReady() {
        if (!resolveReflection()) {
            return false;
        }
        if (!isNothiriumRendererDisposed()) {
            return true;
        }
        boolean marked = markAllChanged();
        boolean ready = marked && !isNothiriumRendererDisposed();
        logRendererRecovery(marked, ready);
        return ready;
    }

    public static boolean setupForIsolatedShaderlessMainPass() {
        if (!resolveReflection() || setupMethod == null) {
            return false;
        }
        if (!ensureRendererReady()) {
            return false;
        }

        long now = System.nanoTime();
        if (now - lastIsolatedMainSetupNanos < ISOLATED_MAIN_SETUP_INTERVAL_NANOS) {
            return true;
        }
        lastIsolatedMainSetupNanos = now;

        try {
            setupMethod.invoke(null);
            logRendererSetup(true, null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logRendererSetup(false, error);
            return false;
        }
    }

    public static boolean setupForShaderedMainTerrainBridge() {
        return setupNothiriumRendererNow();
    }

    private static boolean setupNothiriumRendererNow() {
        if (!resolveReflection() || setupMethod == null) {
            return false;
        }
        if (!ensureRendererReady()) {
            return false;
        }
        try {
            logRenderUtilState("before-setup");
            setupMethod.invoke(null);
            logRenderUtilState("after-setup");
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static void logRenderUtilState(String stage) {
        if (renderUtilProbeLogs >= RENDER_UTIL_PROBE_LOG_LIMIT) {
            return;
        }
        try {
            Class<?> renderUtil = Class.forName("meldexun.renderlib.util.RenderUtil", false,
                    NothiriumBypass.class.getClassLoader());
            if (renderUtilCameraXMethod == null) {
                renderUtilCameraXMethod = renderUtil.getMethod("getCameraX");
                renderUtilCameraYMethod = renderUtil.getMethod("getCameraY");
                renderUtilCameraZMethod = renderUtil.getMethod("getCameraZ");
                renderUtilFrustumMethod = renderUtil.getMethod("getFrustum");
                renderUtilFrameMethod = renderUtil.getMethod("getFrame");
            }
            renderUtilProbeLogs++;
            MainMod.LOGGER.info(
                    "[AUSMNothiriumRenderUtilProbe] call={} stage={} camera={}/{}/{} frustum={} frame={}",
                    renderUtilProbeLogs,
                    stage,
                    renderUtilCameraXMethod.invoke(null),
                    renderUtilCameraYMethod.invoke(null),
                    renderUtilCameraZMethod.invoke(null),
                    renderUtilFrustumMethod.invoke(null) == null ? "null" : "present",
                    renderUtilFrameMethod.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean markNothiriumChunksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!resolveReflection() || setDirtyMethod == null || maxX < minX || maxY < minY || maxZ < minZ) {
            return false;
        }

        try {
            Object provider = getProviderMethod.invoke(null);
            if (provider == null) {
                return false;
            }

            int dirtySections = 0;
            for (int x = minX >> 4; x <= (maxX >> 4); x++) {
                for (int y = minY >> 4; y <= (maxY >> 4); y++) {
                    for (int z = minZ >> 4; z <= (maxZ >> 4); z++) {
                        setDirtyMethod.invoke(provider, x, y, z);
                        dirtySections++;
                    }
                }
            }
            return dirtySections > 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to the original Nothirium handler if reflection fails.
            return false;
        }
    }

    public static boolean recreateRenderer() {
        if (!resolveReflection()) {
            return false;
        }

        boolean disposed = false;
        if (disposeMethod != null) {
            try {
                disposeMethod.invoke(null);
                disposed = true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return markAllChanged();
            }
        }

        if (allChangedMethod == null) {
            return disposed;
        }

        try {
            allChangedMethod.invoke(null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return disposed;
        }
    }

    public static void pushForcedBypass() {
        FORCED_BYPASS_DEPTH.set(FORCED_BYPASS_DEPTH.get() + 1);
    }

    public static void popForcedBypass() {
        int depth = FORCED_BYPASS_DEPTH.get();
        if (depth <= 1) {
            FORCED_BYPASS_DEPTH.remove();
        } else {
            FORCED_BYPASS_DEPTH.set(depth - 1);
        }
    }

    private static boolean shouldUseVanillaRenderGlobalForCurrentPass() {
        if (FORCED_BYPASS_DEPTH.get() > 0) {
            return true;
        }

        if (!PipelineContext.getInstance().isActive()) {
            if (BetterPortalsCompat.isInstalled()) {
                return true;
            }
            if (!ensureRendererReady()) {
                return true;
            }
            if (BetterPortalsCompat.isRenderingRenderPass()) {
                if (!BetterPortalsCompat.isRenderingNestedView()) {
                    setupForIsolatedShaderlessMainPass();
                }
                return true;
            }
            if (BetterPortalsCompat.shouldUseVanillaRenderGlobalForNestedView()) {
                return true;
            }
            return false;
        }

        if (PipelineContext.getInstance().shouldForceVanillaTerrainRenderer()) {
            return true;
        }
        // AUSM renders shaderpack shadows through vanilla RenderGlobal. Letting
        // Nothirium handle setupTerrain here replaces its main-camera lists
        // with the light-space frustum, leaving only the camera section visible
        // when the later gbuffer terrain passes consume those lists.
        if (PipelineContext.getInstance().isShadowPassActive()) {
            return true;
        }
        if (isNothiriumRendererDisposed()) {
            return true;
        }
        if (BetterPortalsCompat.isRenderingRenderPass()) {
            return true;
        }
        if (BetterPortalsCompat.shouldUseVanillaRenderGlobalForNestedView()) {
            return true;
        }
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return true;
        }
        return !NothiriumShadowRenderer.isAvailable();
    }

    private static boolean hasVanillaViewFrustum() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor
                && ((RenderGlobalAccessor) com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc)).ausm$viewFrustum() != null;
    }

    private static boolean shouldUseVanillaForShaderlessBetterPortalsBlockUpdates() {
        return BetterPortalsCompat.isInstalled()
                && !PipelineContext.getInstance().isActive()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    private static boolean isNothiriumRendererDisposed() {
        if (!resolveReflection()) {
            return false;
        }

        try {
            return getRendererMethod.invoke(null) == null
                    || getProviderMethod.invoke(null) == null
                    || getTaskDispatcherMethod.invoke(null) == null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void logRendererRecovery(boolean marked, boolean ready) {
        if (rendererRecoveryLogs >= RENDERER_RECOVERY_LOG_LIMIT) {
            return;
        }
        rendererRecoveryLogs++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumRecovery] call={} marked={} ready={} active={} bpPass={} bpNested={}",
                rendererRecoveryLogs,
                marked,
                ready,
                PipelineContext.getInstance().isActive(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isRenderingNestedView()
        );
    }

    private static boolean resolveReflection() {
        if (reflectionResolved) {
            return !reflectionFailed;
        }

        reflectionResolved = true;
        try {
            Class<?> manager = Class.forName(CHUNK_RENDER_MANAGER, false, NothiriumBypass.class.getClassLoader());
            getRendererMethod = manager.getMethod("getRenderer");
            getProviderMethod = manager.getMethod("getProvider");
            getTaskDispatcherMethod = manager.getMethod("getTaskDispatcher");
            Class<?> provider = Class.forName(
                    "meldexun.nothirium.api.renderer.chunk.IRenderChunkProvider",
                    false,
                    NothiriumBypass.class.getClassLoader()
            );
            setDirtyMethod = provider.getMethod("setDirty", int.class, int.class, int.class);
            allChangedMethod = manager.getMethod("allChanged");
            setupMethod = manager.getMethod("setup");
            try {
                disposeMethod = manager.getMethod("dispose");
            } catch (NoSuchMethodException ignored) {
                disposeMethod = null;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            reflectionFailed = true;
            return false;
        }
    }

    private static void logRendererSetup(boolean success, Throwable error) {
        if (success) {
            if (rendererSetupLogs >= RENDERER_SETUP_LOG_LIMIT) {
                return;
            }
            rendererSetupLogs++;
            MainMod.LOGGER.info(
                    "[AUSMNothiriumSetup] call={} result=ok active={} bpPass={} bpNested={}",
                    rendererSetupLogs,
                    PipelineContext.getInstance().isActive(),
                    BetterPortalsCompat.isRenderingRenderPass(),
                    BetterPortalsCompat.isRenderingNestedView()
            );
            return;
        }

        if (rendererSetupFailureLogs >= RENDERER_SETUP_LOG_LIMIT) {
            return;
        }
        rendererSetupFailureLogs++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumSetup] call={} result=failed active={} bpPass={} bpNested={}",
                rendererSetupFailureLogs,
                PipelineContext.getInstance().isActive(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isRenderingNestedView(),
                error
        );
    }

    private static void logBlockUpdateDecision(String reason, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                                               boolean marked, boolean bypass) {
        if (blockUpdateLogs >= BLOCK_UPDATE_LOG_LIMIT) {
            return;
        }
        blockUpdateLogs++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumBlockUpdate] call={} reason={} range=({}, {}, {})..({}, {}, {}) marked={} bypass={} active={} bpPass={} bpNested={}",
                blockUpdateLogs,
                reason,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                marked,
                bypass,
                PipelineContext.getInstance().isActive(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isRenderingNestedView()
        );
    }

    private static void logHybridMaintenance(String stage, boolean setup) {
        if (hybridMaintenanceLogs >= 0) {
            return;
        }
        hybridMaintenanceLogs++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumHybrid] call={} stage={} setup={} reason='{}' active={} forceVanilla={} bpPass={} bpNested={}",
                hybridMaintenanceLogs,
                stage,
                setup,
                PipelineContext.getInstance().nothiriumHybridVanillaMaintenanceReason(),
                PipelineContext.getInstance().isActive(),
                PipelineContext.getInstance().shouldForceVanillaTerrainRenderer(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isRenderingNestedView()
        );
    }
}
