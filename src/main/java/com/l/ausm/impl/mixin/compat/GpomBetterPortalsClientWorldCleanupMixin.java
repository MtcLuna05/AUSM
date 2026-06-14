package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(targets = "com.l.gpom.compat.betterportals.BetterPortalsClientWorldCleanup", remap = false)
public class GpomBetterPortalsClientWorldCleanupMixin {
    private static final String TARGET_CLASS = "com.l.gpom.compat.betterportals.BetterPortalsClientWorldCleanup";
    private static final AtomicBoolean AUSM_CLEANUP_SCHEDULED = new AtomicBoolean();

    @Inject(method = "cleanup", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ausm$scheduleCleanupOnClientThread(String reason, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.isCallingFromMinecraftThread()) {
            return;
        }

        ci.cancel();
        if (mc == null) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Skipped GPOM BetterPortals cleanup off-thread with no Minecraft instance: {}", reason);
            return;
        }

        if (AUSM_CLEANUP_SCHEDULED.compareAndSet(false, true)) {
            mc.addScheduledTask(() -> ausm$invokeCleanup(reason));
            MainMod.LOGGER.info("[BetterPortalsCompat] Scheduled GPOM BetterPortals cleanup on client thread: {}", reason);
        }
    }

    private static void ausm$invokeCleanup(String reason) {
        try {
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
