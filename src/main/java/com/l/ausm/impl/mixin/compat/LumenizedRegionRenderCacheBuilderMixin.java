package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mixin(RegionRenderCacheBuilder.class)
public class LumenizedRegionRenderCacheBuilderMixin {
    private static final String BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
    private static Method getBloomLayer;
    private static Method initBloomRenderLayer;
    private static Boolean available;
    private static boolean loggedInitialized;
    private static boolean loggedUnavailable;
    private static boolean loggedOutOfRange;
    private static boolean loggedFailure;

    @Shadow
    @Final
    private BufferBuilder[] worldRenderers;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ausm$initializeLumenizedBloomLayer(CallbackInfo ci) {
        if (worldRenderers == null || !resolve()) {
            return;
        }

        try {
            Object layerObject = getBloomLayer.invoke(null);
            if (!(layerObject instanceof BlockRenderLayer)) {
                return;
            }

            BlockRenderLayer bloomLayer = (BlockRenderLayer) layerObject;
            int ordinal = bloomLayer.ordinal();
            if (ordinal < 0 || ordinal >= worldRenderers.length) {
                if (!loggedOutOfRange) {
                    loggedOutOfRange = true;
                    MainMod.LOGGER.warn("[LumenizedBloom] BLOOM layer ordinal {} is outside RegionRenderCacheBuilder array length {}", ordinal, worldRenderers.length);
                }
                return;
            }

            if (worldRenderers[ordinal] == null) {
                initBloomRenderLayer.invoke(null, (Object) worldRenderers);
            }

            if (!loggedInitialized) {
                loggedInitialized = true;
                MainMod.LOGGER.info("[LumenizedBloom] Initialized Lumenized BLOOM chunk buffer at layer ordinal {}", ordinal);
            }
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException error) {
            if (!loggedFailure) {
                loggedFailure = true;
                Throwable cause = error instanceof InvocationTargetException && ((InvocationTargetException) error).getCause() != null
                        ? ((InvocationTargetException) error).getCause()
                        : error;
                MainMod.LOGGER.warn("[LumenizedBloom] Failed to initialize Lumenized BLOOM chunk buffer", cause);
            }
        }
    }

    private static boolean resolve() {
        if (available != null) {
            return available;
        }

        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> bloomUtil = Class.forName(BLOOM_EFFECT_UTIL, false, loader);
            getBloomLayer = bloomUtil.getMethod("getBloomLayer");
            initBloomRenderLayer = bloomUtil.getMethod("initBloomRenderLayer", BufferBuilder[].class);
            available = true;
        } catch (ClassNotFoundException ignored) {
            available = false;
            if (!loggedUnavailable) {
                loggedUnavailable = true;
                MainMod.LOGGER.info("[LumenizedBloom] Lumenized not found; RegionRenderCacheBuilder bridge disabled");
            }
        } catch (ReflectiveOperationException | LinkageError error) {
            available = false;
            if (!loggedFailure) {
                loggedFailure = true;
                MainMod.LOGGER.warn("[LumenizedBloom] Failed to resolve Lumenized RegionRenderCacheBuilder bridge", error);
            }
        }
        return available;
    }
}
