package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.luna.gpom.compat.betterportals.BetterPortalsClientWorldCleanup", remap = false)
public class GpomBetterPortalsClientWorldCleanupMixin {
    private static final String TARGET_CLASS = "com.luna.gpom.compat.betterportals.BetterPortalsClientWorldCleanup";
    private static final AtomicBoolean AUSM_CLEANUP_SCHEDULED = new AtomicBoolean();

    @Inject(method = "cleanup", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ausm$scheduleCleanupOnClientThread(String reason, CallbackInfo ci) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.callBoolean(mc, new String[]{"func_152345_ab", "isCallingFromMinecraftThread"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            PipelineContext.getInstance().clearClientParticles("gpom-better-portals-cleanup:" + reason);
            return;
        }

        ci.cancel();
        if (mc == null) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Skipped GPOM BetterPortals cleanup off-thread with no Minecraft instance: {}", reason);
            return;
        }

        if (AUSM_CLEANUP_SCHEDULED.compareAndSet(false, true)) {
            MinecraftReflectionCompat.addScheduledTask(mc, () -> ausm$invokeCleanup(reason));
            MainMod.LOGGER.info("[BetterPortalsCompat] Scheduled GPOM BetterPortals cleanup on client thread: {}", reason);
        }
    }

    private static void ausm$invokeCleanup(String reason) {
        try {
            PipelineContext.getInstance().clearClientParticles("gpom-better-portals-cleanup:" + reason);
            Class<?> cleanupClass = Class.forName(TARGET_CLASS, false, GpomBetterPortalsClientWorldCleanupMixin.class.getClassLoader());
            Method cleanup = cleanupClass.getMethod("cleanup", String.class);
            cleanup.invoke(null, reason + " (client thread)");
        } catch (ReflectiveOperationException | RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to run scheduled GPOM BetterPortals cleanup", e);
        } finally {
            AUSM_CLEANUP_SCHEDULED.set(false);
        }
    }
}
