package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(targets = "de.johni0702.minecraft.view.impl.net.CreateWorld$Handler", remap = false)
public class BetterPortalsCreateWorldHandlerMixin {
    private static final int MAX_CREATE_WORLD_RETRIES = 200;

    @Inject(
            method = "onMessage(Lde/johni0702/minecraft/view/impl/net/CreateWorld;Lnet/minecraftforge/fml/common/network/simpleimpl/MessageContext;)Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$deferCreateWorldUntilClientReady(@Coerce Object message, MessageContext ctx,
                                                       CallbackInfoReturnable<IMessage> cir) {
        if (ausm$isReadyForCreateWorld(message)) {
            return;
        }

        cir.setReturnValue(null);
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(mc, () -> ausm$retryCreateWorld(message, ctx, 1));
        }
    }

    private void ausm$retryCreateWorld(Object message, MessageContext ctx, int attempt) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (!ausm$isReadyForCreateWorld(message)) {
            if (mc != null && attempt < MAX_CREATE_WORLD_RETRIES) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(mc, () -> ausm$retryCreateWorld(message, ctx, attempt + 1));
            } else {
                MainMod.LOGGER.warn("[BetterPortalsCompat] Dropped CreateWorld packet after {} deferred attempts; client never became ready", attempt);
            }
            return;
        }

        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            IMessageHandler<IMessage, IMessage> handler = (IMessageHandler) this;
            handler.onMessage((IMessage) message, ctx);
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Deferred CreateWorld packet failed during replay", e);
        }
    }

    private boolean ausm$isReadyForCreateWorld(Object message) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return message instanceof IMessage
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.call((mc), net.minecraft.client.network.NetHandlerPlayClient.class, null, new String[] {"func_147114_u", "getConnection"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), Object.class, null, "field_71452_i", "effectRenderer") != null
                && ausm$invokeGetter(message, "getProviderID") != null
                && ausm$invokeGetter(message, "getDifficulty") != null
                && ausm$invokeGetter(message, "getGameType") != null
                && ausm$invokeGetter(message, "getWorldType") != null;
    }

    private Object ausm$invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
