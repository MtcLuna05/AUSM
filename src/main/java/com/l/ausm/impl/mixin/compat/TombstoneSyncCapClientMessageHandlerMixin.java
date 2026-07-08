package com.l.ausm.impl.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "ovh.corail.tombstone.network.SyncCapClientMessage$Handler", remap = false)
public class TombstoneSyncCapClientMessageHandlerMixin {
    @Inject(
            method = "onMessage(Lovh/corail/tombstone/network/SyncCapClientMessage;Lnet/minecraftforge/fml/common/network/simpleimpl/MessageContext;)Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$ignoreSyncWithoutPlayer(@Coerce Object message, MessageContext ctx,
                                              CallbackInfoReturnable<IMessage> cir) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            cir.setReturnValue(null);
        }
    }
}
