package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.pipeline.pack.ShaderPipelineWorldLoadGate;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Hot-reloadable owner of the GUI render boundary. Mixins only forward stable
 * lifecycle hooks here; screen and HUD implementations merely submit content.
 */
public final class AusmGuiRenderController {
    private static final long LIVE_RESOURCE_CHECK_INTERVAL_NANOS = 500_000_000L;
    private static long nextLiveResourceCheckNanos;
    private static String liveShaderPackName = "";
    private static String liveShaderPackFingerprint = "";

    private AusmGuiRenderController() {
    }

    public static void beginFrame(long nanoTime) {
        compilePipelineBeforeFirstPlayableWorldFrame();
        PipelineContext.getInstance().beginClientRenderFrame(nanoTime);
    }

    public static void completeWorldBeforeGui() {
        PipelineContext context = PipelineContext.getInstance();
        context.captureShaderlessWorldFramebufferForUi();
        context.syncShaderlessWorldFramebufferBeforeGui();
        context.renderShaderlessBloomBeforeGui();
        context.snapshotShaderlessWorldFramebufferForGui();
        context.prepareShaderlessUiRenderingBoundary();
    }

    public static void beginScreen() {
        PipelineContext context = PipelineContext.getInstance();
        context.beginGuiItemRenderScope();
        context.beginOwnedGuiScreenRendering();
    }

    public static void endScreen() {
        PipelineContext context = PipelineContext.getInstance();
        context.endGuiItemRenderScope();
        context.finishOwnedGuiRendering();
    }

    public static void beginHud() {
        if (isHudHidden()) {
            return;
        }
        try {
            Class.forName("me.decce.gnetum.Gnetum", false, AusmGuiRenderController.class.getClassLoader());
            // Gnetum replaces the entire Forge HUD render with a multi-pass
            // cache. Its bind/unbind window begins after this injected HEAD
            // callback, so an AUSM HUD boundary cannot safely be nested here.
            // Let Gnetum own this render entirely; AUSM's world presentation
            // has already completed before the HUD begins.
            return;
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Gnetum is optional; use AUSM's normal HUD path otherwise.
        }
        PipelineContext context = PipelineContext.getInstance();
        context.beginGuiItemRenderScope();
        context.beginOwnedHudRendering();
    }

    public static void endHud() {
        if (isHudHidden()) {
            return;
        }
        try {
            Class.forName("me.decce.gnetum.Gnetum", false, AusmGuiRenderController.class.getClassLoader());
            return;
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Gnetum is optional; use AUSM's normal HUD path otherwise.
        }
        try {
            PipelineContext context = PipelineContext.getInstance();
            context.endGuiItemRenderScope();
            context.finishOwnedGuiRendering();
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            if (minecraft != null) {
                ShaderCompileNotifications.renderOverlay(new ScaledResolution(minecraft));
            }
        } finally {
            // No Gnetum state is active on the normal AUSM HUD path.
        }
    }

    public static boolean drawOwnedWorldBackground(GuiScreen screen) {
        Minecraft minecraft = MinecraftReflectionCompat.guiScreenMinecraft(screen);
        if (minecraft == null || MinecraftReflectionCompat.world(minecraft) == null) {
            return false;
        }

        int width = Math.max(1, MinecraftReflectionCompat.guiScreenWidth(screen));
        int height = Math.max(1, MinecraftReflectionCompat.fieldInt(screen, 1, "field_146295_m", "height"));
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        // The world depth buffer must not decide which pixels receive the GUI
        // dim layer. Otherwise clear-depth sky darkens while nearer terrain
        // rejects the quad, producing a sky-only rectangle behind inventory.
        MinecraftReflectionCompat.glStateDisableDepth();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthMask(false);
        MinecraftReflectionCompat.glStateDisableTexture2D();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        MinecraftReflectionCompat.glStateEnableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        MinecraftReflectionCompat.glStateDisableAlpha();
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        color(0xC0101010);
        GL11.glVertex3f(0.0F, 0.0F, 0.0F);
        GL11.glVertex3f(width, 0.0F, 0.0F);
        color(0xD0101010);
        GL11.glVertex3f(width, height, 0.0F);
        GL11.glVertex3f(0.0F, height, 0.0F);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateDisableBlend();
        GL11.glDisable(GL11.GL_BLEND);
        MinecraftReflectionCompat.glStateEnableAlpha();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (previousDepthTest) {
            MinecraftReflectionCompat.glStateEnableDepth();
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            MinecraftReflectionCompat.glStateDisableDepth();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
        GL11.glDepthMask(previousDepthMask);
        return true;
    }

    private static void color(int argb) {
        float alpha = (argb >>> 24 & 255) / 255.0F;
        float red = (argb >>> 16 & 255) / 255.0F;
        float green = (argb >>> 8 & 255) / 255.0F;
        float blue = (argb & 255) / 255.0F;
        GL11.glColor4f(red, green, blue, alpha);
    }

    private static boolean isHudHidden() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        return minecraft != null
                && MinecraftReflectionCompat.currentScreen(minecraft) == null
                && MinecraftReflectionCompat.gameSettings(minecraft) != null
                && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(minecraft));
    }

    private static void compilePipelineBeforeFirstPlayableWorldFrame() {
        ShaderPackManager manager = MainMod.getShaderPackManager();
        if (manager == null || !manager.compilePendingPipelineBeforeFirstWorldFrame()) {
            return;
        }
        liveShaderPackName = manager.getSelectedPackName();
        liveShaderPackFingerprint = manager.currentPackFingerprint();
        nextLiveResourceCheckNanos = System.nanoTime() + LIVE_RESOURCE_CHECK_INTERVAL_NANOS;
    }

    private static boolean isPlayableWorldReady() {
        return ShaderPipelineWorldLoadGate.isPlayableWorldReady();
    }

}
