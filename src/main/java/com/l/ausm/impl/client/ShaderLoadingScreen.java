package com.l.ausm.impl.client;

import com.l.ausm.impl.MainMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

public final class ShaderLoadingScreen {
    private static final long MIN_RENDER_INTERVAL_NANOS = 50_000_000L;

    private static boolean active;
    private static boolean disabledAfterFailure;
    private static boolean rendering;
    private static String packName = "";
    private static String status = "";
    private static int currentStep;
    private static int totalSteps = 1;
    private static long lastRenderNanos;
    private static final IntBuffer VIEWPORT = org.lwjgl.BufferUtils.createIntBuffer(16);

    private ShaderLoadingScreen() {
    }

    public static void begin(String pack, int estimatedSteps) {
        if (disabledAfterFailure) {
            return;
        }
        packName = pack == null ? "" : pack;
        status = "Starting shader reload";
        currentStep = 0;
        totalSteps = Math.max(1, estimatedSteps);
        active = true;
        lastRenderNanos = 0L;
        renderNow(true);
    }

    public static void setTotalSteps(int steps) {
        if (!active) {
            return;
        }
        totalSteps = Math.max(Math.max(1, steps), currentStep + 1);
        renderNow(false);
    }

    public static void step(String label) {
        if (!active) {
            return;
        }
        status = label == null || label.isBlank() ? "Loading shaders" : label;
        currentStep = Math.min(totalSteps, currentStep + 1);
        renderNow(false);
    }

    public static void finish() {
        if (!active) {
            return;
        }
        status = "Shader reload complete";
        currentStep = totalSteps;
        renderNow(true);
        active = false;
    }

    private static void renderNow(boolean force) {
        if (disabledAfterFailure || rendering) {
            return;
        }
        long now = System.nanoTime();
        if (!force && now - lastRenderNanos < MIN_RENDER_INTERVAL_NANOS) {
            return;
        }
        lastRenderNanos = now;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null || mc.entityRenderer == null || !Display.isCreated()) {
            return;
        }

        rendering = true;
        try {
            draw(mc);
            mc.updateDisplay();
        } catch (RuntimeException e) {
            disabledAfterFailure = true;
            MainMod.LOGGER.warn("[ShaderLoadingScreen] Disabled shader loading overlay after render failure.", e);
        } finally {
            rendering = false;
        }
    }

    private static void draw(Minecraft mc) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        FontRenderer font = mc.fontRenderer;

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        VIEWPORT.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.clearColor(0.06f, 0.07f, 0.08f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        mc.entityRenderer.setupOverlayRendering();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        drawContent(font, width, height);

        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        GlStateManager.viewport(VIEWPORT.get(0), VIEWPORT.get(1), VIEWPORT.get(2), VIEWPORT.get(3));
    }

    private static void drawContent(FontRenderer font, int width, int height) {
        Gui.drawRect(0, 0, width, height, 0xEE101114);

        int maxTextWidth = Math.max(80, width - 40);
        String title = "Loading shaders";
        String packLine = fit(font, "Pack: " + packName, maxTextWidth);
        String statusLine = fit(font, status, maxTextWidth);
        String countLine = currentStep + " / " + totalSteps;

        int barWidth = Math.min(260, Math.max(80, width - 40));
        int barHeight = 8;
        int barX = (width - barWidth) / 2;
        int barY = height / 2 + 14;
        float progress = totalSteps <= 0 ? 1.0f : Math.min(1.0f, Math.max(0.0f, currentStep / (float) totalSteps));
        int fillWidth = Math.round(barWidth * progress);

        int titleY = height / 2 - 38;
        drawCentered(font, title, width / 2, titleY, 0xFFFFFFFF);
        drawCentered(font, packLine, width / 2, titleY + 15, 0xFFC8C8D4);
        drawCentered(font, statusLine, width / 2, titleY + 30, 0xFFE4E4EC);

        Gui.drawRect(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF44444C);
        Gui.drawRect(barX, barY, barX + barWidth, barY + barHeight, 0xFF202026);
        Gui.drawRect(barX, barY, barX + fillWidth, barY + barHeight, 0xFF7DB4FF);
        drawCentered(font, countLine, width / 2, barY + 14, 0xFFC8C8D4);
    }

    private static void drawCentered(FontRenderer font, String text, int centerX, int y, int color) {
        font.drawStringWithShadow(text, centerX - font.getStringWidth(text) / 2, y, color);
    }

    private static String fit(FontRenderer font, String text, int width) {
        if (font.getStringWidth(text) <= width) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = font.getStringWidth(suffix);
        return font.trimStringToWidth(text, Math.max(0, width - suffixWidth)) + suffix;
    }
}
