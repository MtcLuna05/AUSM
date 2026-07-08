package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(targets = "thebetweenlands.common.network.clientbound.MessageSyncChunkStorage", remap = false)
public class BetweenlandsMessageSyncChunkStorageMixin {
    private static final String WORLD_STORAGE_CLASS = "thebetweenlands.common.world.storage.WorldStorageImpl";
    private static boolean warningLogged;

    @Shadow(remap = false)
    private ChunkPos pos;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipMissingChunkStorage(CallbackInfo ci) {
        if (pos == null) {
            ci.cancel();
            return;
        }

        WorldClient world = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) : null;
        if (world == null) {
            ci.cancel();
            return;
        }

        Chunk chunk;
        try {
            chunk = com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.world.chunk.Chunk.class, null, new String[] {"func_72964_e", "getChunk"},
                new Class<?>[] {int.class, int.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((pos), 0, "field_77276_a", "x")), (com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((pos), 0, "field_77275_b", "z")));
        } catch (RuntimeException e) {
            logSkippedPacket("chunk lookup failed", e);
            ci.cancel();
            return;
        }

        if (chunk == null || com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((chunk), new String[] {"func_76621_g", "isEmpty"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            ci.cancel();
            return;
        }

        Object chunkStorage = chunkStorage(world, chunk);
        if (chunkStorage == null) {
            logSkippedPacket("chunk storage missing", null);
            ci.cancel();
        }
    }

    private static Object chunkStorage(World world, Chunk chunk) {
        try {
            Class<?> storageClass = Class.forName(WORLD_STORAGE_CLASS, false, BetweenlandsMessageSyncChunkStorageMixin.class.getClassLoader());
            Method getCapability = storageClass.getMethod("getCapability", World.class);
            Object worldStorage = getCapability.invoke(null, world);
            if (worldStorage == null) {
                return null;
            }
            Method getChunkStorage = findMethod(worldStorage.getClass(), "getChunkStorage", Chunk.class);
            return getChunkStorage.invoke(worldStorage, chunk);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            logSkippedPacket("storage reflection failed", e);
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Method method = cursor.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void logSkippedPacket(String reason, Throwable throwable) {
        if (warningLogged) {
            return;
        }
        warningLogged = true;
        if (throwable != null) {
            MainMod.LOGGER.warn("[BetweenlandsCompat] Skipped chunk-storage sync with unavailable client chunk storage: {}", reason, throwable);
        } else {
            MainMod.LOGGER.warn("[BetweenlandsCompat] Skipped chunk-storage sync with unavailable client chunk storage: {}", reason);
        }
    }
}
