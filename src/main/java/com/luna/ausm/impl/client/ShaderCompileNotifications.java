package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public final class ShaderCompileNotifications {
    private static final List<String> FAILURES = new ArrayList<>();
    private static String overlayText;
    private static int overlayTicks;

    private ShaderCompileNotifications() {
    }

    public static void beginReload() {
        FAILURES.clear();
        overlayText = null;
        overlayTicks = 0;
    }

    public static void reportFailure(String shaderName) {
        if (shaderName == null || shaderName.isBlank()) {
            shaderName = "unknown shader";
        }
        if (!FAILURES.contains(shaderName)) {
            FAILURES.add(shaderName);
        }
    }

    public static void reportLoadFailure(String packName, String reason) {
        String message = "Shaderpack cannot be loaded: " + (reason == null || reason.isBlank() ? "unsupported features" : reason);
        showOverlayMessage(message);
        MainMod.LOGGER.warn("[ShaderCompiler] Shaderpack '{}' rejected before compilation: {}", packName, reason);

        postChatMessage(message);
    }

    public static void finishReload(String packName) {
        if (FAILURES.isEmpty()) {
            return;
        }

        String detail = FAILURES.size() == 1 ? FAILURES.get(0) : FAILURES.size() + " shaders";
        String message = "Shader failed to compile: " + detail + ". Check latest.log for details.";
        showOverlayMessage(message);
        MainMod.LOGGER.warn("[ShaderCompiler] Shaderpack '{}' loaded with compile failures: {}", packName, FAILURES);

        postChatMessage(message);
    }

    private static void postChatMessage(String message) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        GuiIngame ingameGui = mc != null ? MinecraftReflectionCompat.field(mc, GuiIngame.class, null, "field_71456_v", "ingameGUI") : null;
        GuiNewChat chat = ingameGui != null ? MinecraftReflectionCompat.call(ingameGui, GuiNewChat.class, null, new String[]{"func_146158_b", "getChatGUI"}, MinecraftReflectionCompat.NO_PARAMETERS) : null;
        if (chat != null) {
            MinecraftReflectionCompat.invoke(chat, new String[]{"func_146227_a", "printChatMessage"}, new Class<?>[]{ITextComponent.class}, new TextComponentString("[AUSM] " + message));
        }
    }

    private static void showOverlayMessage(String message) {
        overlayText = message;
        overlayTicks = 20 * 8;
    }

    public static void renderOverlay(ScaledResolution resolution) {
        if (overlayTicks <= 0 || overlayText == null || overlayText.isEmpty()) {
            return;
        }
        overlayTicks--;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.fontRenderer(mc) == null || resolution == null) {
            return;
        }

        FontRenderer font = MinecraftReflectionCompat.fontRenderer(mc);
        int textWidth = MinecraftReflectionCompat.fontStringWidth(font, overlayText);
        int x = Math.max(6, (MinecraftReflectionCompat.scaledResolutionWidth(resolution) - textWidth) / 2);
        int y = 8;
        MinecraftReflectionCompat.guiDrawRect(x - 6, y - 4, x + textWidth + 6, y + 13, 0xCC300C0C);
        MinecraftReflectionCompat.fontDrawStringWithShadow(font, overlayText, x, y, 0xFFFFD0D0);
    }
}
