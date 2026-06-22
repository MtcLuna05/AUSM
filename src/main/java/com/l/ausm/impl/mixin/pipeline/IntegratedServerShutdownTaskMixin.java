package com.l.ausm.impl.mixin.pipeline;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.integrated.IntegratedServer$3")
public class IntegratedServerShutdownTaskMixin {
    @Shadow
    @Final
    private IntegratedServer this$0;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void ausm$logoutAllWhenClientPlayerMissing(CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.player != null) {
            return;
        }

        PlayerList playerList = this$0.getPlayerList();
        for (EntityPlayerMP player : Lists.newArrayList(playerList.getPlayers())) {
            playerList.playerLoggedOut(player);
        }
        ci.cancel();
    }
}
