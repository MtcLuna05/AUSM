package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketTeams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
    @Shadow
    private WorldClient world;

    @Inject(method = "handleTeams", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreTeamPacketWithoutWorld(SPacketTeams packetIn, CallbackInfo ci) {
        if (world == null) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSoundEffect", at = @At("HEAD"), cancellable = true)
    private void ausm$ignoreSoundPacketWithoutRenderViewEntity(SPacketSoundEffect packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (world == null || mc == null || mc.getRenderViewEntity() == null) {
            ci.cancel();
        }
    }
}
