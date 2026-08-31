package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

abstract class BetterPortalsReflection extends BetterPortalsRenderState {
    protected static Framebuffer renderPassFramebuffer(Object pass) {
        if (pass == null) {
            return null;
        }

        try {
            Object framebuffer = pass.getClass().getMethod("getFramebuffer").invoke(pass);
            if (framebuffer instanceof Framebuffer) {
                return (Framebuffer) framebuffer;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    protected static Object portalConfig(String portalClassName) {
        try {
            Class<?> configClass = Class.forName(BETTER_PORTALS_CONFIG_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            String fieldName = BetterPortalsCompat.portalConfigFieldName(portalClassName);
            if (fieldName == null) {
                return null;
            }
            return configClass.getField(fieldName).get(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BetterPortalsCompat.logConfigReflectionFailure(e);
            return null;
        }
    }

    protected static String portalConfigFieldName(String portalClassName) {
        String name = portalClassName.toLowerCase();
        if (name.contains("aether")) {
            return "aetherPortals";
        }
        if (name.contains("nether")) {
            return "netherPortals";
        }
        if (name.equals("net.minecraft.block.blockportal") || name.endsWith(".blockportal")) {
            return "netherPortals";
        }
        if (name.contains("end")) {
            return "endPortals";
        }
        if (name.contains("twilight") || name.contains("tfportal")) {
            return "twilightForestPortals";
        }
        if (name.contains("mekanism")) {
            return "mekanismPortals";
        }
        if (name.contains("abyss") || name.contains("dreadlands") || name.contains("omothol")) {
            return "abyssalcraftPortals";
        }
        if (name.contains("travelhuts") || name.contains("travel_huts")) {
            return "travelHutsPortals";
        }
        return null;
    }

    protected static String describeRenderPassFramebuffer(Object pass) {
        Framebuffer framebuffer = BetterPortalsCompat.renderPassFramebuffer(pass);
        return BetterPortalsCompat.describeFramebuffer(framebuffer);
    }

    protected static String describeFramebuffer(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        return MinecraftReflectionCompat.framebufferObject(framebuffer)
                + "("
                + MinecraftReflectionCompat.framebufferWidth(framebuffer)
                + "x"
                + MinecraftReflectionCompat.framebufferHeight(framebuffer)
                + ")";
    }

    protected static int dimensionId(WorldClient world) {
        return world != null && MinecraftReflectionCompat.worldProvider(world) != null ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world)) : Integer.MIN_VALUE;
    }

    protected static String externalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName()) || className.equals(BetterPortalsCompat.class.getName())) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    protected static String hex(int value) {
        return "0x" + Integer.toHexString(value);
    }

    protected static void setCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    protected static boolean glBoolean(int parameter) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(1);
        GL11.glGetBoolean(parameter, buffer);
        return buffer.get(0) != 0;
    }

    protected static boolean[] glBoolean4(int parameter) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(parameter, buffer);
        return new boolean[]{
                buffer.get(0) != 0,
                buffer.get(1) != 0,
                buffer.get(2) != 0,
                buffer.get(3) != 0
        };
    }

    protected static float[] glFloat4(int parameter) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(parameter, buffer);
        return new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    protected static int[] glInt4(int parameter) {
        IntBuffer buffer = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(parameter, buffer);
        return new int[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
    }

    protected static boolean resolveViewPlanReflection() {
        if (viewPlanReflectionResolved) {
            return !viewPlanReflectionFailed;
        }

        viewPlanReflectionResolved = true;
        try {
            Class<?> viewRenderPlanClass = Class.forName(VIEW_RENDER_PLAN_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            currentViewPlanField = viewRenderPlanClass.getDeclaredField("CURRENT");
            currentViewPlanField.setAccessible(true);
            mainViewPlanField = viewRenderPlanClass.getDeclaredField("MAIN");
            mainViewPlanField.setAccessible(true);
            renderPassParentMethod = viewRenderPlanClass.getMethod("getParent");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BetterPortalsCompat.logViewPlanReflectionFailure(e);
            return false;
        }
    }

    protected static Class<?> portalEntityClass() {
        if (portalEntityClassResolved) {
            return portalEntityClass;
        }

        portalEntityClassResolved = true;
        try {
            portalEntityClass = Class.forName(PORTAL_ENTITY_CLASS, false, BetterPortalsCompat.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            portalEntityClass = null;
        }
        return portalEntityClass;
    }

    protected static boolean resolveConfigReflection() {
        if (configReflectionResolved) {
            return !configReflectionFailed;
        }

        configReflectionResolved = true;
        try {
            Class<?> configClass = Class.forName(BETTER_PORTALS_CONFIG_CLASS, false, BetterPortalsCompat.class.getClassLoader());
            seeThroughPortalsField = configClass.getDeclaredField("seeThroughPortals");
            seeThroughPortalsField.setAccessible(true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BetterPortalsCompat.logConfigReflectionFailure(e);
            return false;
        }
    }

    protected static boolean classPresent(String className) {
        try {
            Class.forName(className, false, BetterPortalsCompat.class.getClassLoader());
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    protected static void logViewPlanReflectionFailure(Throwable throwable) {
        if (!viewPlanReflectionFailed) {
            viewPlanReflectionFailed = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Better Portals view detection unavailable; nested views will use AUSM normally", throwable);
        }
    }

    protected static void logConfigReflectionFailure(Throwable throwable) {
        if (!configReflectionFailed) {
            configReflectionFailed = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Better Portals config detection unavailable; portal shader views will be disabled", throwable);
        }
    }
}
