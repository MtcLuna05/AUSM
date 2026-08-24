package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.client.ThaumcraftParticleBridge;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
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
        Object value = MinecraftReflectionCompat.invoke(event, new String[]{"getWorld"}, new Class<?>[0]);
        World world = value instanceof World ? (World) value : null;
        if (world != null && (!MinecraftReflectionCompat.fieldBoolean(world, false, "field_72995_K", "isRemote") || !ThaumcraftParticleBridge.isParticleEngineAvailable())) {
            ci.cancel();
        }
    }
}
