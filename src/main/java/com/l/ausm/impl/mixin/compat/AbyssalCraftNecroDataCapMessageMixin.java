package com.l.ausm.impl.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.shinoow.abyssalcraft.common.network.client.NecroDataCapMessage", remap = false)
public class AbyssalCraftNecroDataCapMessageMixin {
    @Inject(method = "process", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$ignoreNecroDataWithoutPlayer(EntityPlayer player, Side side, CallbackInfo ci) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (player == null || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            ci.cancel();
        }
    }
}
