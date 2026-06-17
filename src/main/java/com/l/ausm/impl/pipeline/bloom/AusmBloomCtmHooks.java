package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;

import java.util.ArrayList;
import java.util.Collections;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class AusmBloomCtmHooks {
    private static boolean loggedEnabled;
    private static boolean loggedForcedLayer;
    private static boolean loggedMergedQuads;
    private static boolean loggedExposedBloomLayerQuads;
    private static boolean loggedFailure;
    private static final ThreadLocal<Boolean> mergingBloomQuads = new ThreadLocal<>();
    private static Method canRenderInLayerMethod;
    private static Field layersField;
    private static Field lumenizedCtmEnableField;
    private static boolean lumenizedCtmEnableResolved;

    private AusmBloomCtmHooks() {
    }

    public static boolean canRenderInLayer(Object model, IBlockState state, BlockRenderLayer layer) {
        boolean original = invokeOriginalCanRenderInLayer(model, state, layer);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (original || model == null || layer == null || layer != bloomLayer) {
            return original;
        }

        int mask = ctmLayerMask(model);
        int ordinal = layer.ordinal();
        boolean enabled = ordinal >= 0 && ordinal < Integer.SIZE && ((mask >>> ordinal) & 1) == 1;
        if (enabled && !loggedEnabled) {
            loggedEnabled = true;
            MainMod.LOGGER.info("[AUSMBloom] CTM model layer mask exposes BLOOM ordinal {}; enabling BLOOM chunk geometry.", ordinal);
        }
        if (enabled) {
            return true;
        }

        if (!loggedForcedLayer) {
            loggedForcedLayer = true;
            MainMod.LOGGER.info("[AUSMBloom] Forcing CTM BLOOM layer visibility; models without BLOOM quads still return empty geometry.");
        }
        return true;
    }

    public static List<BakedQuad> getQuadsWithAusmBloom(List<BakedQuad> original,
                                                        BlockRenderLayer layer,
                                                        IBakedModel model,
                                                        IBlockState state,
                                                        EnumFacing side,
                                                        long rand) {
        if (original == null) {
            original = Collections.emptyList();
        }
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (model == null
                || layer == null
                || bloomLayer == null
                || mergingBloomQuads.get() != null) {
            return original;
        }

        if (layer == bloomLayer) {
            List<BakedQuad> bloomQuads = getBloomLayerQuads(model, state, side, rand, layer);
            if (bloomQuads != null && !bloomQuads.isEmpty()) {
                if (!loggedExposedBloomLayerQuads) {
                    loggedExposedBloomLayerQuads = true;
                    MainMod.LOGGER.info("[AUSMBloom] Exposed CTM BLOOM quads through the standalone BLOOM layer.");
                }
                return bloomQuads;
            }
            return original;
        }

        try {
            if (!shouldMergeBloomIntoLayer(original, state, layer)) {
                return original;
            }

            List<BakedQuad> bloomQuads = getBloomLayerQuads(model, state, side, rand, layer);
            if (bloomQuads == null || bloomQuads.isEmpty()) {
                return original;
            }

            List<BakedQuad> merged = new ArrayList<>(original.size() + bloomQuads.size());
            merged.addAll(original);
            merged.addAll(bloomQuads);
            if (!loggedMergedQuads) {
                loggedMergedQuads = true;
                MainMod.LOGGER.info("[AUSMBloom] Merged CTM BLOOM quads into base layer {} for emissive visibility.", layer);
            }
            return merged;
        } catch (RuntimeException | LinkageError error) {
            logFailure(error);
            return original;
        }
    }

    private static boolean shouldMergeBloomIntoLayer(List<BakedQuad> original, IBlockState state, BlockRenderLayer layer) {
        if (PipelineContext.getInstance().isActive() || original == null || !original.isEmpty()) {
            return true;
        }
        if (state == null || state.getBlock() == null) {
            return false;
        }

        try {
            BlockRenderLayer naturalLayer = state.getBlock().getRenderLayer();
            return naturalLayer == layer;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static List<BakedQuad> getBloomLayerQuads(IBakedModel model,
                                                      IBlockState state,
                                                      EnumFacing side,
                                                      long rand,
                                                      BlockRenderLayer restoreLayer) {
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (model == null || bloomLayer == null) {
            return Collections.emptyList();
        }

        LumenizedCtmBypass bypass = pushLumenizedCtmBypass();
        try {
            mergingBloomQuads.set(Boolean.TRUE);
            ForgeHooksClient.setRenderLayer(bloomLayer);
            return model.getQuads(state, side, rand);
        } finally {
            ForgeHooksClient.setRenderLayer(restoreLayer);
            mergingBloomQuads.remove();
            bypass.close();
        }
    }

    private static boolean invokeOriginalCanRenderInLayer(Object model, IBlockState state, BlockRenderLayer layer) {
        if (model == null) {
            return false;
        }

        try {
            Method method = canRenderInLayerMethod;
            if (method == null || !method.getDeclaringClass().isInstance(model)) {
                method = model.getClass().getMethod("canRenderInLayer", IBlockState.class, BlockRenderLayer.class);
                method.setAccessible(true);
                canRenderInLayerMethod = method;
            }
            Object result = method.invoke(model, state, layer);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
            return false;
        }
    }

    private static int ctmLayerMask(Object model) {
        try {
            Field field = layersField;
            if (field == null || !field.getDeclaringClass().isInstance(model)) {
                field = findField(model.getClass(), "layers");
                field.setAccessible(true);
                layersField = field;
            }
            return field.getByte(model) & 0xFF;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
            return 0;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void logFailure(Throwable throwable) {
        if (!loggedFailure) {
            loggedFailure = true;
            MainMod.LOGGER.warn("[AUSMBloom] Failed to inspect CTM model layer mask for BLOOM support", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private static LumenizedCtmBypass pushLumenizedCtmBypass() {
        ThreadLocal<Object> enable = lumenizedCtmEnableThreadLocal();
        if (enable == null) {
            return LumenizedCtmBypass.NOOP;
        }

        Object previous = enable.get();
        enable.set(Boolean.TRUE);
        return new LumenizedCtmBypass(enable, previous);
    }

    @SuppressWarnings("unchecked")
    private static ThreadLocal<Object> lumenizedCtmEnableThreadLocal() {
        try {
            if (!lumenizedCtmEnableResolved) {
                lumenizedCtmEnableResolved = true;
                Class<?> hooks = Class.forName("gregtech.asm.hooks.CTMHooks", false, AusmBloomCtmHooks.class.getClassLoader());
                lumenizedCtmEnableField = hooks.getDeclaredField("ENABLE");
                lumenizedCtmEnableField.setAccessible(true);
            }
            if (lumenizedCtmEnableField == null) {
                return null;
            }
            Object value = lumenizedCtmEnableField.get(null);
            return value instanceof ThreadLocal<?> ? (ThreadLocal<Object>) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            lumenizedCtmEnableField = null;
            return null;
        }
    }

    private static final class LumenizedCtmBypass {
        private static final LumenizedCtmBypass NOOP = new LumenizedCtmBypass(null, null);

        private final ThreadLocal<Object> enable;
        private final Object previous;

        private LumenizedCtmBypass(ThreadLocal<Object> enable, Object previous) {
            this.enable = enable;
            this.previous = previous;
        }

        private void close() {
            if (enable == null) {
                return;
            }
            if (previous == null) {
                enable.remove();
            } else {
                enable.set(previous);
            }
        }
    }
}
