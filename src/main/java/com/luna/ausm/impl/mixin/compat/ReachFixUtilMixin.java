package com.luna.ausm.impl.mixin.compat;

import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.reachfix.util.ReachFixUtil", remap = false)
public class ReachFixUtilMixin {
    @Inject(method = "updateBaseReachModifier", at = @At("HEAD"), cancellable = true)
    private static void ausm$skipNullPlayerReachUpdate(EntityPlayer player, CallbackInfo ci) {
        if (player == null) {
            ci.cancel();
        }
    }
}
