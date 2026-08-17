package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl", remap = false)
public abstract class BetterPortalsServerWorldsManagerMixin {
    private static final String WORLDS_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl";
    private static final String WORLD_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldManager";
    private static final String VIEW_ENTITY_CLASS = "de.johni0702.minecraft.view.impl.server.ViewEntity";

    @Shadow(remap = false)
    public abstract Map getWorldManagers();

    @Shadow(remap = false)
    public abstract EntityPlayerMP getPlayer();

    @Inject(method = "beforeTransferToDimension", at = @At("HEAD"))
    private void ausm$repairStaleManagersBeforeTransfer(WorldServer destination, CallbackInfo ci) {
        ausm$removeStaleNonViewManagers("before-transfer", destination);
    }

    @Inject(method = "updateActiveViews", at = @At("HEAD"))
    private void ausm$repairMissedDimensionTransfer(CallbackInfo ci) {
        EntityPlayerMP player = getPlayer();
        if (player == null || MinecraftReflectionCompat.playerIsSpectator(player)) {
            return;
        }

        WorldServer playerWorld = MinecraftReflectionCompat.playerServerWorld(player);
        Map worldManagers = getWorldManagers();
        if (playerWorld == null || worldManagers == null || worldManagers.containsKey(playerWorld)) {
            return;
        }

        try {
            worldManagers.put(playerWorld, ausm$createWorldManager(playerWorld, player));
            MainMod.LOGGER.warn("[BetterPortalsCompat] Repaired missed Better Portals server view transfer for player={} world={}",
                    MinecraftReflectionCompat.entityName(player),
                    MinecraftReflectionCompat.worldProvider(playerWorld) != null ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(playerWorld)) : "null");
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to repair missed Better Portals server view transfer", e);
        }
    }

    private void ausm$removeStaleNonViewManagers(String reason, WorldServer destination) {
        EntityPlayerMP player = getPlayer();
        if (player == null) {
            return;
        }

        WorldServer currentWorld = MinecraftReflectionCompat.playerServerWorld(player);
        Map worldManagers = getWorldManagers();
        if (currentWorld == null || worldManagers == null || worldManagers.size() <= 1) {
            return;
        }

        int removed = 0;
        try {
            Iterator iterator = worldManagers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry) iterator.next();
                Object world = entry.getKey();
                Object manager = entry.getValue();
                if (world == currentWorld || manager == null) {
                    continue;
                }
                Object managerPlayer = ausm$invoke(manager, "getPlayer");
                if (managerPlayer != player || ausm$isViewEntity(managerPlayer) || !ausm$hasNoViews(manager)) {
                    continue;
                }
                iterator.remove();
                removed++;
            }
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to prune stale Better Portals world managers before {}", reason, e);
            return;
        }

        if (removed > 0) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Removed {} stale Better Portals server world manager(s) before {} player={} currentDim={} destinationDim={} remaining={}",
                    removed,
                    reason,
                    MinecraftReflectionCompat.entityName(player),
                    MinecraftReflectionCompat.worldProvider(currentWorld) != null ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(currentWorld)) : "null",
                    destination != null && MinecraftReflectionCompat.worldProvider(destination) != null ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(destination)) : "null",
                    worldManagers.size());
        }
    }

    private boolean ausm$hasNoViews(Object manager) {
        Object views = ausm$invoke(manager, "getViews");
        return views instanceof List && ((List) views).isEmpty();
    }

    private boolean ausm$isViewEntity(Object player) {
        if (player == null) {
            return false;
        }
        try {
            Class<?> viewEntityClass = Class.forName(VIEW_ENTITY_CLASS, false, getClass().getClassLoader());
            return viewEntityClass.isInstance(player);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private Object ausm$invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Unable to invoke Better Portals " + methodName, e);
        }
    }

    private Object ausm$createWorldManager(WorldServer world, EntityPlayerMP player) {
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> worldsManagerClass = Class.forName(WORLDS_MANAGER_CLASS, false, loader);
            Class<?> worldManagerClass = Class.forName(WORLD_MANAGER_CLASS, false, loader);
            Constructor<?> constructor = worldManagerClass.getConstructor(worldsManagerClass, WorldServer.class, EntityPlayerMP.class);
            return constructor.newInstance(this, world, player);
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("Unable to construct Better Portals ServerWorldManager", e);
        }
    }
}
