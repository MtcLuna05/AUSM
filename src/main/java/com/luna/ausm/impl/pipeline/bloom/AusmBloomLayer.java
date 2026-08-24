package com.luna.ausm.impl.pipeline.bloom;

import com.luna.ausm.impl.MainMod;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Loader;

public final class AusmBloomLayer {
    private static final String LUMENIZED_MOD_ID = "lumenized";
    private static final String LUMENIZED_BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
    private static final String NOTHIRIUM_MOD_ID = "nothirium";
    private static final String NOTHIRIUM_CHUNK_RENDER_PASS = "meldexun.nothirium.api.renderer.chunk.ChunkRenderPass";
    private static final String NOTHIRIUM_BLOCK_RENDER_LAYER_UTIL = "meldexun.nothirium.mc.util.BlockRenderLayerUtil";

    private static BlockRenderLayer bloomLayer;
    private static boolean standaloneCreateAttempted;
    private static boolean nothiriumBloomPassPatched;
    private static boolean nothiriumRendererRecreatePending;
    private static boolean loggedAvailable;
    private static boolean loggedUnavailable;
    private static boolean loggedCreateFailure;
    private static boolean loggedLumenizedInitFailure;
    private static boolean loggedNothiriumPatchFailure;

    private AusmBloomLayer() {
    }

    public static void initialize() {
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
            initializeNothiriumBloomLayerSupport();
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
            return initializeNothiriumBloomLayerSupport() && isAvailable();
        }
        return isAvailable();
    }

    public static boolean shouldUseShaderlessNativeHook() {
        if (Loader.isModLoaded(NOTHIRIUM_MOD_ID)) {
            return initializeNothiriumBloomLayerSupport() && isAvailable();
        }
        return shouldUseNativeHook();
    }

    /**
     * Nothirium allocates per-pass renderer lists before AUSM can extend its
     * enum. Recreate that data backend once from the world render thread.
     */
    public static boolean consumeNothiriumRendererRecreateRequest() {
        if (!nothiriumRendererRecreatePending) {
            return false;
        }
        nothiriumRendererRecreatePending = false;
        return true;
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

    private static boolean initializeNothiriumBloomLayerSupport() {
        if (nothiriumBloomPassPatched) {
            return true;
        }
        if (!Loader.isModLoaded(NOTHIRIUM_MOD_ID) || existingLayer() == null) {
            return false;
        }

        try {
            Class<?> blockRenderLayerUtilClass = Class.forName(NOTHIRIUM_BLOCK_RENDER_LAYER_UTIL, true, AusmBloomLayer.class.getClassLoader());
            Class<?> chunkRenderPassClass = Class.forName(NOTHIRIUM_CHUNK_RENDER_PASS, true, AusmBloomLayer.class.getClassLoader());
            ensureEnumConstant(chunkRenderPassClass, "BLOOM");

            Object renderPasses = invokeValues(chunkRenderPassClass);
            BlockRenderLayer[] renderLayers = BlockRenderLayer.values();
            if (Array.getLength(renderPasses) != renderLayers.length) {
                throw new IllegalStateException("Nothirium BLOOM pass count does not match block render layers");
            }
            Object bloomPass = Array.get(renderPasses, bloomLayer.ordinal());
            if (!(bloomPass instanceof Enum<?>) || !"BLOOM".equals(((Enum<?>) bloomPass).name())) {
                throw new IllegalStateException("Nothirium BLOOM pass is not aligned with BlockRenderLayer.BLOOM");
            }

            // Nothirium maps layers and passes solely by ordinal. Extending both
            // snapshots before its renderer is created gives BLOOM its own VBO
            // part and visible list instead of merging it into another pass.
            writeStaticField(blockRenderLayerUtilClass, "ALL", renderLayers);
            writeStaticField(chunkRenderPassClass, "ALL", renderPasses);
            nothiriumBloomPassPatched = true;
            nothiriumRendererRecreatePending = true;
            MainMod.LOGGER.info("[AUSMBloom] Added native Nothirium BLOOM render pass at ordinal {}.", bloomLayer.ordinal());
            return true;
        } catch (Throwable error) {
            if (!loggedNothiriumPatchFailure) {
                loggedNothiriumPatchFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] Failed to add Nothirium BLOOM render-pass support", error);
            }
            return false;
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
