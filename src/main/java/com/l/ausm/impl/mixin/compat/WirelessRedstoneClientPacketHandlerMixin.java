package com.l.ausm.impl.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.INetHandlerPlayClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "codechicken.wirelessredstone.network.WRClientPH", remap = false)
public class WirelessRedstoneClientPacketHandlerMixin {
    @Inject(
            method = "handlePacket(Lcodechicken/lib/packet/PacketCustom;Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/play/INetHandlerPlayClient;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$ignorePacketWithoutPlayer(@Coerce Object packet, Minecraft mc, INetHandlerPlayClient handler,
                                                CallbackInfo ci) {
        if (mc == null || mc.world == null || mc.player == null) {
            ci.cancel();
        }
    }
}
