package com.l.ausm.impl.pipeline.compat;

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
    private static Method allChangedMethod;

    private NothiriumBypass() {
    }

    public static boolean shouldBypass() {
        try {
            return shouldUseVanillaRenderGlobalForCurrentPass();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean shouldBypassBlockUpdates() {
        try {
            if (isNothiriumRendererDisposed()) {
                return true;
            }
            if (!shouldUseVanillaRenderGlobalForCurrentPass()) {
                return false;
            }
            return !BetterPortalsCompat.isInstalled() || hasVanillaViewFrustum();
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
        if (isNothiriumRendererDisposed()) {
            return true;
        }
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return true;
        }
        if (BetterPortalsCompat.shouldUseVanillaRenderGlobalForNestedView()) {
            return true;
        }
        if (!PipelineContext.getInstance().isActive()) {
            return false;
        }
        return true;
    }

    private static boolean hasVanillaViewFrustum() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null
                && mc.renderGlobal instanceof RenderGlobalAccessor
                && ((RenderGlobalAccessor) mc.renderGlobal).ausm$viewFrustum() != null;
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
            allChangedMethod = manager.getMethod("allChanged");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            reflectionFailed = true;
            return false;
        }
    }
}
