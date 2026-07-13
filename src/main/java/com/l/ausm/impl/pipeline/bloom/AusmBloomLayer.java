package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.impl.MainMod;
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

    private static BlockRenderLayer bloomLayer;
    private static boolean standaloneCreateAttempted;
    private static boolean nothiriumLayerPatched;
    private static boolean loggedAvailable;
    private static boolean loggedUnavailable;
    private static boolean loggedCreateFailure;
    private static boolean loggedLumenizedInitFailure;
    private static boolean loggedNothiriumPatchFailure;
    private static boolean loggedNativeLayerDisabledForNothirium;
    private static volatile Boolean nothiriumLoaded;

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
        if (isNothiriumLoaded()) {
            sanitizeNothiriumLayerArrays();
            if (!loggedNativeLayerDisabledForNothirium) {
                loggedNativeLayerDisabledForNothirium = true;
                MainMod.LOGGER.info("[AUSMBloom] Disabled native BLOOM render-layer hook because Nothirium cannot index the added BLOOM layer.");
            }
            return false;
        }
        return isAvailable();
    }

    public static boolean shouldUseShaderlessNativeHook() {
        if (isNothiriumLoaded()) {
            sanitizeNothiriumLayerArrays();
            return false;
        }
        return shouldUseNativeHook();
    }

    private static boolean isNothiriumLoaded() {
        Boolean cached = nothiriumLoaded;
        if (cached != null) {
            return cached;
        }
        boolean loaded = Loader.isModLoaded(NOTHIRIUM_MOD_ID);
        nothiriumLoaded = loaded;
        return loaded;
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
            if (existingLayer() == null) {
                ensureEnumConstant(BlockRenderLayer.class, "BLOOM");
            }
        } catch (LinkageError | RuntimeException error) {
            if (!loggedLumenizedInitFailure) {
                loggedLumenizedInitFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] Failed to initialize Lumenized BLOOM layer early", error);
            }
        }
    }

    private static void sanitizeNothiriumLayerArrays() {
        if (nothiriumLayerPatched || !isNothiriumLoaded()) {
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
