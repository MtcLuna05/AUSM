package com.l.ausm.impl.client;

import com.l.ausm.impl.Reference;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.mojang.realmsclient.gui.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = Reference.MODID)
public final class AusmDebugOverlay {

    private AusmDebugOverlay() {
    }

    @SubscribeEvent
    public static void onDebugOverlay(RenderGameOverlayEvent.Text event) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null
                || !MinecraftReflectionCompat.showDebugInfo(MinecraftReflectionCompat.gameSettings(minecraft))) {
            return;
        }
        event.getRight().add(ChatFormatting.AQUA + "AUSM Rendering: v" + Reference.VERSION);
    }
}
