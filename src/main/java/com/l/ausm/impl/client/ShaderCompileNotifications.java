package com.l.ausm.impl.client;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;

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

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString("[AUSM] " + message));
        }
    }

    public static void finishReload(String packName) {
        if (FAILURES.isEmpty()) {
            return;
        }

        String detail = FAILURES.size() == 1 ? FAILURES.get(0) : FAILURES.size() + " shaders";
        String message = "Shader failed to compile: " + detail + ". Check latest.log for details.";
        showOverlayMessage(message);
        MainMod.LOGGER.warn("[ShaderCompiler] Shaderpack '{}' loaded with compile failures: {}", packName, FAILURES);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentString("[AUSM] " + message));
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

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || resolution == null) {
            return;
        }

        FontRenderer font = mc.fontRenderer;
        int textWidth = font.getStringWidth(overlayText);
        int x = Math.max(6, (resolution.getScaledWidth() - textWidth) / 2);
        int y = 8;
        Gui.drawRect(x - 6, y - 4, x + textWidth + 6, y + 13, 0xCC300C0C);
        font.drawStringWithShadow(overlayText, x, y, 0xFFFFD0D0);
    }
}
