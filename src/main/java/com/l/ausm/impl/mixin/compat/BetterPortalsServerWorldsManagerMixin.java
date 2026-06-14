package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.util.Map;

@Mixin(targets = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl", remap = false)
public abstract class BetterPortalsServerWorldsManagerMixin {
    private static final String WORLDS_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl";
    private static final String WORLD_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldManager";

    @Shadow
    public abstract Map getWorldManagers();

    @Shadow
    public abstract EntityPlayerMP getPlayer();

    @Inject(method = "updateActiveViews", at = @At("HEAD"))
    private void ausm$repairMissedDimensionTransfer(CallbackInfo ci) {
        EntityPlayerMP player = getPlayer();
        if (player == null || player.isSpectator()) {
            return;
        }

        WorldServer playerWorld = player.getServerWorld();
        Map worldManagers = getWorldManagers();
        if (playerWorld == null || worldManagers == null || worldManagers.containsKey(playerWorld)) {
            return;
        }

        try {
            worldManagers.put(playerWorld, ausm$createWorldManager(playerWorld, player));
            MainMod.LOGGER.warn("[BetterPortalsCompat] Repaired missed Better Portals server view transfer for player={} world={}",
                    player.getName(),
                    playerWorld.provider != null ? playerWorld.provider.getDimension() : "null");
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to repair missed Better Portals server view transfer", e);
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
