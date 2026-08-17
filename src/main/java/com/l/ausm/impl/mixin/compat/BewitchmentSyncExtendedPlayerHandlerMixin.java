package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.bewitchment.api.message.SyncExtendedPlayer$Handler", remap = false)
public class BewitchmentSyncExtendedPlayerHandlerMixin {
    @Inject(
            method = "onMessage(Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;Lnet/minecraftforge/fml/common/network/simpleimpl/MessageContext;)Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$skipSyncWithoutPlayer(IMessage message, MessageContext context,
                                            CallbackInfoReturnable<IMessage> cir) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null || MinecraftReflectionCompat.player(minecraft) == null) {
            cir.setReturnValue(null);
        }
    }
}
