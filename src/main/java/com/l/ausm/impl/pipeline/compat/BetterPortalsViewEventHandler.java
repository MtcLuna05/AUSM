package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.Reference;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class BetterPortalsViewEventHandler {
    private BetterPortalsViewEventHandler() {
    }

    @SubscribeEvent
    public static void onForgeEvent(Event event) {
        if (BetterPortalsCompat.handleRenderPassEvent(event)) {
            WorldClient parentWorld = BetterPortalsCompat.consumePendingParentRenderWorld();
            ShaderPackManager shaderPackManager = MainMod.getShaderPackManager();
            if (shaderPackManager != null && parentWorld != null
                    && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(parentWorld) != null) {
                shaderPackManager.restoreAfterBetterPortalsNestedRender(com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(parentWorld)));
            }
        }
    }
}
