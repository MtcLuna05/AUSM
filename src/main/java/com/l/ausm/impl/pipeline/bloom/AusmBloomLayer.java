package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class AusmBloomLayer {
    private static final String LUMENIZED_MOD_ID = "lumenized";
    private static final String LUMENIZED_BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
    private static final String NOTHIRIUM_MOD_ID = "nothirium";
    private static final String NOTHIRIUM_CHUNK_RENDER_PASS = "meldexun.nothirium.api.renderer.chunk.ChunkRenderPass";
    private static final String NOTHIRIUM_BLOCK_RENDER_LAYER_UTIL = "meldexun.nothirium.mc.util.BlockRenderLayerUtil";
    private static final String CTM_MOD_ID = "ctm";
    private static final String CTM_ABSTRACT_BAKED_MODEL = "team.chisel.ctm.client.model.AbstractCTMBakedModel";
    private static final int BLOOM_BUFFER_SIZE = 131072;

    private static BlockRenderLayer bloomLayer;
    private static boolean standaloneCreateAttempted;
    private static boolean nothiriumLayerPatched;
    private static boolean ctmLayerPatched;
    private static boolean loggedAvailable;
    private static boolean loggedUnavailable;
    private static boolean loggedBufferInitialized;
    private static boolean loggedBufferOutOfRange;
    private static boolean loggedCreateFailure;
    private static boolean loggedLumenizedInitFailure;
    private static boolean loggedNothiriumPatchFailure;
    private static boolean loggedCtmPatchFailure;
    private static boolean loggedNativeLayerDisabledForNothirium;

    private AusmBloomLayer() {
    }

    public static void initialize() {
        sanitizeNothiriumLayerArrays();
        layer();
    }

    public static BlockRenderLayer layer() {
        if (bloomLayer != null) {
            return bloomLayer;
        }

        bloomLayer = existingLayer();
        if (bloomLayer == null && Loader.isModLoaded(LUMENIZED_MOD_ID) && !standaloneCreateAttempted) {
            standaloneCreateAttempted = true;
            initializeLumenizedBloomLayer();
            bloomLayer = existingLayer();
        }

        if (bloomLayer != null) {
            sanitizeNothiriumLayerArrays();
            patchCtmBloomLayer();
            if (!loggedAvailable) {
                loggedAvailable = true;
                MainMod.LOGGER.info("[AUSMBloom] BLOOM render layer available at ordinal {}", bloomLayer.ordinal());
            }
        } else if (!loggedUnavailable) {
            loggedUnavailable = true;
            MainMod.LOGGER.info("[AUSMBloom] BLOOM render layer unavailable; using framebuffer-only bloom.");
        }
        return bloomLayer;
    }

    public static boolean isAvailable() {
        return layer() != null;
    }

    public static boolean isBloomLayer(BlockRenderLayer layer) {
        return layer != null && "BLOOM".equals(layer.name());
    }

    public static boolean shouldUseNativeHook() {
        if (Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            sanitizeNothiriumLayerArrays();
            return isAvailable();
        }
        return isAvailable();
    }

    public static boolean shouldUseShaderlessNativeHook() {
        if (Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            sanitizeNothiriumLayerArrays();
            return false;
        }
        return shouldUseNativeHook();
    }

    public static void ensureRegionBuffer(BufferBuilder[] worldRenderers) {
        if (!shouldUseNativeHook() || worldRenderers == null) {
            return;
        }

        BlockRenderLayer layer = bloomLayer;
        int ordinal = layer.ordinal();
        if (ordinal < 0 || ordinal >= worldRenderers.length) {
            if (!loggedBufferOutOfRange) {
                loggedBufferOutOfRange = true;
                MainMod.LOGGER.warn("[AUSMBloom] BLOOM layer ordinal {} is outside RegionRenderCacheBuilder array length {}", ordinal, worldRenderers.length);
            }
            return;
        }

        if (worldRenderers[ordinal] == null) {
            worldRenderers[ordinal] = new BufferBuilder(BLOOM_BUFFER_SIZE);
            if (!loggedBufferInitialized) {
                loggedBufferInitialized = true;
                MainMod.LOGGER.info("[AUSMBloom] Initialized BLOOM chunk buffer at layer ordinal {}", ordinal);
            }
        }
    }

    private static BlockRenderLayer existingLayer() {
        try {
            return BlockRenderLayer.valueOf("BLOOM");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void initializeLumenizedBloomLayer() {
        try {
            Class<?> bloomEffectUtil = Class.forName(LUMENIZED_BLOOM_EFFECT_UTIL, true, AusmBloomLayer.class.getClassLoader());
            if (existingLayer() == null) {
                bloomEffectUtil.getMethod("init").invoke(null);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (!loggedLumenizedInitFailure) {
                loggedLumenizedInitFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] Failed to initialize Lumenized BLOOM layer early", error);
            }
        }
    }

    private static void sanitizeNothiriumLayerArrays() {
        if (nothiriumLayerPatched || !Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            return;
        }

        try {
            Class<?> blockRenderLayerUtilClass = Class.forName(NOTHIRIUM_BLOCK_RENDER_LAYER_UTIL, true, AusmBloomLayer.class.getClassLoader());
            writeStaticField(blockRenderLayerUtilClass, "ALL", nonBloomBlockRenderLayers());
            Class<?> chunkRenderPassClass = Class.forName(NOTHIRIUM_CHUNK_RENDER_PASS, true, AusmBloomLayer.class.getClassLoader());
            writeStaticField(chunkRenderPassClass, "ALL", nonBloomEnumArray(chunkRenderPassClass));

            nothiriumLayerPatched = true;
            MainMod.LOGGER.info("[AUSMBloom] Sanitized Nothirium layer/pass snapshots to vanilla terrain layers.");
        } catch (Throwable error) {
            if (!loggedNothiriumPatchFailure) {
                loggedNothiriumPatchFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] Failed to sanitize Nothirium terrain layer support", error);
            }
        }
    }

    private static BlockRenderLayer[] nonBloomBlockRenderLayers() {
        List<BlockRenderLayer> layers = new ArrayList<>();
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer != null && !"BLOOM".equals(layer.name())) {
                layers.add(layer);
            }
        }
        return layers.toArray(new BlockRenderLayer[0]);
    }

    private static Object nonBloomEnumArray(Class<?> enumClass) {
        try {
            Field all = enumClass.getDeclaredField("ALL");
            all.setAccessible(true);
            Object values = all.get(null);
            if (values != null && values.getClass().isArray()) {
                return nonBloomEnumArray(values);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return nonBloomEnumArray(invokeValues(enumClass));
    }

    private static Object nonBloomEnumArray(Object values) {
        if (values == null || !values.getClass().isArray()) {
            return values;
        }

        Class<?> componentType = values.getClass().getComponentType();
        List<Enum<?>> entries = new ArrayList<>();
        int length = Array.getLength(values);
        for (int i = 0; i < length; i++) {
            Object value = Array.get(values, i);
            if (value instanceof Enum<?> enumValue && !"BLOOM".equals(enumValue.name())) {
                entries.add(enumValue);
            }
        }
        entries.sort((left, right) -> Integer.compare(left.ordinal(), right.ordinal()));

        Object filtered = Array.newInstance(componentType, entries.size());
        for (int i = 0; i < entries.size(); i++) {
            Array.set(filtered, i, entries.get(i));
        }
        return filtered;
    }

    private static void patchCtmBloomLayer() {
        if (ctmLayerPatched || bloomLayer == null || !Loader.isModLoaded(CTM_MOD_ID)) {
            return;
        }

        try {
            Class<?> abstractBakedModelClass = Class.forName(CTM_ABSTRACT_BAKED_MODEL, true, AusmBloomLayer.class.getClassLoader());
            BlockRenderLayer[] layers = BlockRenderLayer.values();
            writeStaticField(abstractBakedModelClass, "LAYERS", layers);
            invalidateCtmModelCaches(abstractBakedModelClass);
            ctmLayerPatched = true;
            MainMod.LOGGER.info("[AUSMBloom] Patched CTM baked-model layer snapshot for BLOOM ordinal {} (layers={}).",
                    bloomLayer.ordinal(),
                    layers.length);
        } catch (Throwable error) {
            if (!loggedCtmPatchFailure) {
                loggedCtmPatchFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] Failed to patch CTM BLOOM baked-model layer support", error);
            }
        }
    }

    private static void invalidateCtmModelCaches(Class<?> abstractBakedModelClass) {
        try {
            Method invalidateCaches = abstractBakedModelClass.getMethod("invalidateCaches");
            invalidateCaches.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Cache invalidation is best-effort; new worlds still compile against the patched layer array.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void ensureEnumConstant(Class<?> enumClass, String name) {
        Object values = invokeValues(enumClass);
        if (arrayContainsEnum(values, name)) {
            return;
        }
        EnumHelper.addEnum((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name, new Class<?>[0]);
    }

    private static Object invokeValues(Class<?> enumClass) {
        try {
            Method values = enumClass.getMethod("values");
            return values.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read enum values for " + enumClass.getName(), e);
        }
    }

    private static boolean arrayContainsEnum(Object values, String name) {
        if (values == null || !values.getClass().isArray()) {
            return false;
        }

        int length = Array.getLength(values);
        for (int i = 0; i < length; i++) {
            Object value = Array.get(values, i);
            if (value instanceof Enum<?> enumValue && enumValue.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void writeStaticField(Class<?> owner, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        try {
            field.set(null, value);
            return;
        } catch (Throwable reflectionFailure) {
            writeStaticFieldWithUnsafe(field, value, reflectionFailure);
        }
    }

    private static void writeStaticFieldWithUnsafe(Field field, Object value, Throwable reflectionFailure) throws ReflectiveOperationException {
        try {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            Object unsafe = theUnsafeField.get(null);

            Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
            long offset = ((Number) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field)).longValue();
            unsafeClass.getMethod("putObjectVolatile", Object.class, long.class, Object.class)
                    .invoke(unsafe, base, offset, value);
        } catch (ReflectiveOperationException | RuntimeException unsafeFailure) {
            unsafeFailure.addSuppressed(reflectionFailure);
            throw unsafeFailure;
        }
    }
}
