package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.client.ThaumcraftParticleBridge;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "thecodex6824.thaumcraftfix.client.ClientEventHandler", remap = false)
public abstract class ThaumcraftFixClientEventHandlerMixin {
    @Inject(method = "onClientWorldUnload", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ausm$skipServerWorldParticleCleanup(WorldEvent.Unload event, CallbackInfo ci) {
        World world = event == null ? null : event.getWorld();
        if (world != null && (!world.isRemote || !ThaumcraftParticleBridge.isParticleEngineAvailable())) {
            ci.cancel();
        }
    }
}
