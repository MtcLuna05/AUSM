package com.luna.ausm.impl.client;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Read-only diagnostics at Gnetum's actual framebuffer cache boundaries. */
public final class GnetumRenderProbe {
    private static final int MAX_SAMPLES = 64;
    private static int samples;
    private static boolean resolved;
    private static Field renderingField;
    private static Field passManagerField;
    private static Field passIndexField;
    private static Field configField;
    private static Field configuredPassesField;
    private static Field backFramebufferField;
    private static Field frontFramebufferField;

    private GnetumRenderProbe() {
    }

    public static void record(String boundary, Object manager) {
        if (samples >= MAX_SAMPLES || !resolve(manager)) {
            return;
        }
        try {
            boolean rendering = renderingField.getBoolean(null);
            Object passManager = passManagerField.get(null);
            int pass = passManager == null ? -1 : passIndexField.getInt(passManager);
            Object config = configField.get(null);
            int configuredPasses = config == null ? -1 : configuredPassesField.getInt(config);
            Framebuffer back = (Framebuffer) backFramebufferField.get(manager);
            Framebuffer front = (Framebuffer) frontFramebufferField.get(manager);
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            Framebuffer main = minecraft == null ? null : MinecraftReflectionCompat.minecraftFramebuffer(minecraft);
            samples++;
            MainMod.LOGGER.info(
                    "[AUSMGnetumProbe] sample={} boundary={} rendering={} pass={}/{} pipeline={} drawFbo={} readFbo={} drawBuffer={} readBuffer={} program={} main={}/{}/{} back={}/{}/{} front={}/{}/{}",
                    samples,
                    boundary,
                    rendering,
                    pass,
                    configuredPasses,
                    PipelineContext.getInstance().isActive(),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                    GL11.glGetInteger(GL11.GL_READ_BUFFER),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    framebufferId(main),
                    framebufferTexture(main),
                    framebufferCenterColor(main),
                    framebufferId(back),
                    framebufferTexture(back),
                    framebufferCenterColor(back),
                    framebufferId(front),
                    framebufferTexture(front),
                    framebufferCenterColor(front)
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            samples = MAX_SAMPLES;
            MainMod.LOGGER.warn("[AUSMGnetumProbe] Disabled after {} failed: {}", boundary, e.toString());
        }
    }

    private static boolean resolve(Object manager) {
        if (resolved) {
            return renderingField != null;
        }
        resolved = true;
        try {
            ClassLoader loader = manager.getClass().getClassLoader();
            Class<?> gnetum = Class.forName("me.decce.gnetum.Gnetum", false, loader);
            Class<?> passManager = Class.forName("me.decce.gnetum.PassManager", false, loader);
            renderingField = gnetum.getDeclaredField("rendering");
            passManagerField = gnetum.getDeclaredField("passManager");
            configField = gnetum.getDeclaredField("config");
            passIndexField = passManager.getDeclaredField("current");
            Class<?> config = Class.forName("me.decce.gnetum.GnetumConfig", false, loader);
            configuredPassesField = config.getDeclaredField("numberOfPasses");
            backFramebufferField = manager.getClass().getDeclaredField("backFramebuffer");
            frontFramebufferField = manager.getClass().getDeclaredField("frontFramebuffer");
            renderingField.setAccessible(true);
            passManagerField.setAccessible(true);
            configField.setAccessible(true);
            passIndexField.setAccessible(true);
            configuredPassesField.setAccessible(true);
            backFramebufferField.setAccessible(true);
            frontFramebufferField.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError e) {
            MainMod.LOGGER.warn("[AUSMGnetumProbe] Gnetum probe unavailable: {}", e.toString());
            renderingField = null;
        }
        return renderingField != null;
    }

    private static int framebufferId(Framebuffer framebuffer) {
        return framebuffer == null ? -1 : MinecraftReflectionCompat.framebufferObject(framebuffer);
    }

    private static int framebufferTexture(Framebuffer framebuffer) {
        return framebuffer == null ? -1 : MinecraftReflectionCompat.framebufferTexture(framebuffer);
    }

    private static int framebufferCenterColor(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return -1;
        }
        int framebufferId = framebufferId(framebuffer);
        int width = MinecraftReflectionCompat.framebufferWidth(framebuffer);
        int height = MinecraftReflectionCompat.framebufferHeight(framebuffer);
        if (framebufferId <= 0 || width <= 0 || height <= 0) {
            return -1;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(width / 2, height / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return (pixel.get(0) & 0xFF) << 24 | (pixel.get(1) & 0xFF) << 16 | (pixel.get(2) & 0xFF) << 8 | pixel.get(3) & 0xFF;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            if (previousReadFramebuffer == 0) {
                GL11.glReadBuffer(previousReadBuffer == 0 ? GL11.GL_BACK : previousReadBuffer);
            } else if (previousReadBuffer != 0) {
                GL11.glReadBuffer(previousReadBuffer);
            }
        }
    }
}
