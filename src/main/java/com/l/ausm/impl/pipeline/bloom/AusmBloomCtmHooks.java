package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

import java.util.ArrayList;
import java.util.Collections;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public final class AusmBloomCtmHooks {
    private static boolean loggedEnabled;
    private static boolean loggedForcedLayer;
    private static boolean loggedMergedQuads;
    private static boolean loggedExposedBloomLayerQuads;
    private static boolean loggedFailure;
    private static final ThreadLocal<Boolean> mergingBloomQuads = new ThreadLocal<>();
    private static Method canRenderInLayerMethod;
    private static Method getModelMethod;
    private static Method modelCanRenderInLayerMethod;
    private static Field layersField;
    private static Field genQuadsField;
    private static Field faceQuadsField;
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
            
        }
        if (enabled) {
            return true;
        }

        if (model instanceof IBakedModel bakedModel && hasBloomLayerQuads(bakedModel, state)) {
            if (!loggedForcedLayer) {
                loggedForcedLayer = true;
                
            }
            return true;
        }
        return false;
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
                
            }
            return merged;
        } catch (RuntimeException | LinkageError error) {
            logFailure(error);
            return original;
        }
    }

    public static boolean hasBloomLayerQuads(IBakedModel model, IBlockState state) {
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (model == null
                || state == null
                || bloomLayer == null
                || mergingBloomQuads.get() != null) {
            return false;
        }

        BlockRenderLayer restoreLayer = MinecraftForgeClient.getRenderLayer();
        try {
            if (hasBloomLayerQuads(model, state, null, restoreLayer)) {
                return true;
            }
            for (EnumFacing side : EnumFacing.values()) {
                if (hasBloomLayerQuads(model, state, side, restoreLayer)) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError error) {
            logFailure(error);
        }
        return false;
    }

    private static boolean hasBloomLayerQuads(IBakedModel model,
                                              IBlockState state,
                                              EnumFacing side,
                                              BlockRenderLayer restoreLayer) {
        List<BakedQuad> quads = getBloomLayerQuads(model, state, side, 0L, restoreLayer);
        return quads != null && !quads.isEmpty();
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
        } catch (NoSuchMethodException ignored) {
            return invokeCtmModelCanRenderInLayer(model, state, layer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
            return invokeCtmModelCanRenderInLayer(model, state, layer);
        }
    }

    private static boolean invokeCtmModelCanRenderInLayer(Object bakedModel, IBlockState state, BlockRenderLayer layer) {
        try {
            Method getter = getModelMethod;
            if (getter == null || !getter.getDeclaringClass().isInstance(bakedModel)) {
                getter = bakedModel.getClass().getMethod("getModel");
                getter.setAccessible(true);
                getModelMethod = getter;
            }
            Object ctmModel = getter.invoke(bakedModel);
            if (ctmModel == null) {
                return false;
            }
            Method method = modelCanRenderInLayerMethod;
            if (method == null || !method.getDeclaringClass().isInstance(ctmModel)) {
                method = ctmModel.getClass().getMethod("canRenderInLayer", IBlockState.class, BlockRenderLayer.class);
                method.setAccessible(true);
                modelCanRenderInLayerMethod = method;
            }
            Object result = method.invoke(ctmModel, state, layer);
            return result instanceof Boolean && (Boolean) result;
        } catch (NoSuchMethodException ignored) {
            return false;
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
        } catch (NoSuchFieldException ignored) {
            return ctmLayerMaskFromQuadMaps(model);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
            return ctmLayerMaskFromQuadMaps(model);
        }
    }

    private static int ctmLayerMaskFromQuadMaps(Object model) {
        int mask = 0;
        try {
            Field field = genQuadsField;
            if (field == null || !field.getDeclaringClass().isInstance(model)) {
                field = findField(model.getClass(), "genQuads");
                field.setAccessible(true);
                genQuadsField = field;
            }
            Object genQuads = field.get(model);
            if (genQuads instanceof Map<?, ?> map) {
                mask |= layerMask(map.keySet());
            } else if (genQuads != null) {
                try {
                    Method keySet = genQuads.getClass().getMethod("keySet");
                    Object keys = keySet.invoke(genQuads);
                    if (keys instanceof Iterable<?> iterable) {
                        mask |= layerMask(iterable);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Some Guava multimaps only expose their keys through asMap().
                    Method asMap = genQuads.getClass().getMethod("asMap");
                    Object asMapValue = asMap.invoke(genQuads);
                    if (asMapValue instanceof Map<?, ?> map) {
                        mask |= layerMask(map.keySet());
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
        }

        try {
            Field field = faceQuadsField;
            if (field == null || !field.getDeclaringClass().isInstance(model)) {
                field = findField(model.getClass(), "faceQuads");
                field.setAccessible(true);
                faceQuadsField = field;
            }
            Object faceQuads = field.get(model);
            if (faceQuads != null) {
                try {
                    Method rowKeySet = faceQuads.getClass().getMethod("rowKeySet");
                    Object keys = rowKeySet.invoke(faceQuads);
                    if (keys instanceof Iterable<?> iterable) {
                        mask |= layerMask(iterable);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Older/newer table implementations are allowed to skip this optimization.
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            logFailure(error);
        }
        return mask;
    }

    private static int layerMask(Iterable<?> layers) {
        int mask = 0;
        if (layers == null) {
            return 0;
        }
        for (Object layer : layers) {
            if (layer instanceof BlockRenderLayer blockRenderLayer) {
                int ordinal = blockRenderLayer.ordinal();
                if (ordinal >= 0 && ordinal < Integer.SIZE) {
                    mask |= 1 << ordinal;
                }
            }
        }
        return mask;
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
