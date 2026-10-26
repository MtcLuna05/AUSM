package com.luna.ausm.impl.client;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** In-game controls for AUSM's asynchronous pipeline GPU trace. */
public final class AusmTraceCommand {
    public static void register() {
        MinecraftForge.EVENT_BUS.register(new AusmTraceCommand());
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (!"/ausmtrace".equals(message) && !message.startsWith("/ausmtrace ")) {
            return;
        }
        event.setCanceled(true);

        String[] args = message.trim().split("\\s+");
        String action = args.length == 1 ? "status" : args[1].toLowerCase(java.util.Locale.ROOT);
        PipelineContext pipeline = PipelineContext.getInstance();
        if ("start".equals(action)) {
            pipeline.startGpuTraceCapture();
            reply("AUSM GPU trace requested; it starts on the next rendered frame and reports every 90 frames.");
        } else if ("stop".equals(action)) {
            pipeline.stopGpuTraceCapture();
            reply("AUSM GPU trace will stop after pending GPU timestamps are collected; see latest.log for the summary.");
        } else if ("status".equals(action)) {
            reply("AUSM GPU trace="
                    + (pipeline.isGpuTraceCaptureRequested() ? "running or draining" : "idle")
                    + "; last summary: " + pipeline.getLastGpuTraceSummary());
        } else {
            reply("Usage: /ausmtrace <start|stop|status>");
        }
    }

    private static void reply(String message) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        GuiIngame ingameGui = minecraft != null
                ? MinecraftReflectionCompat.field(minecraft, GuiIngame.class, null, "field_71456_v", "ingameGUI")
                : null;
        GuiNewChat chat = ingameGui != null
                ? MinecraftReflectionCompat.call(ingameGui, GuiNewChat.class, null,
                        new String[]{"func_146158_b", "getChatGUI"}, MinecraftReflectionCompat.NO_PARAMETERS)
                : null;
        if (chat != null) {
            MinecraftReflectionCompat.invoke(chat, new String[]{"func_146227_a", "printChatMessage"},
                    new Class<?>[]{ITextComponent.class}, new TextComponentString("[AUSM] " + message));
        }
    }
}
