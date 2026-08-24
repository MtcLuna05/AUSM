package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public final class ProjectRedHaloRenderer {
    private static final int HALO_ALPHA = 112;
    private static final int[] HALO_TEXTURE_UNITS = {
            MinecraftReflectionCompat.defaultTexUnit(),
            MinecraftReflectionCompat.lightmapTexUnit(),
            GL13.GL_TEXTURE2
    };

    private static final int[] savedTexture2D = new int[HALO_TEXTURE_UNITS.length];
    private static final boolean[] savedTexture2DEnabled = new boolean[HALO_TEXTURE_UNITS.length];
    private static int savedProgram;
    private static int savedActiveTexture;
    private static boolean savedBlend;
    private static boolean savedLighting;
    private static boolean savedCull;
    private static boolean savedDepthMask;
    private static int savedBlendSrc;
    private static int savedBlendDst;
    private static double activeScale = 1.0D;
    private static int stateDepth;
    private static boolean originalRendererFailureLogged;

    private ProjectRedHaloRenderer() {
    }

    public static void beginImmediateHalo() {
        beginImmediateHalo(1.0D);
    }

    public static void beginImmediateItemHalo() {
        beginImmediateHalo();
    }

    private static void beginImmediateHalo(double scale) {
        if (stateDepth++ > 0) {
            return;
        }

        activeScale = scale;
        savedProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        savedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        savedLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        savedCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        savedDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        savedBlendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        savedBlendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);

        MinecraftReflectionCompat.glUseProgram(0);
        for (int i = 0; i < HALO_TEXTURE_UNITS.length; i++) {
            GL13.glActiveTexture(HALO_TEXTURE_UNITS[i]);
            savedTexture2D[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            savedTexture2DEnabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL13.glActiveTexture(savedActiveTexture);

        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179112_b", "blendFunc"},
                new Class<?>[]{int.class, int.class}, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        MinecraftReflectionCompat.glStateDisableTexture2D();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateDepthMask(false);
    }

    public static void renderImmediateHalo(Object cuboid, int color, Object transformation) {
        if (cuboid == null) {
            return;
        }

        int rgba = enumColourRgba(color);
        int red = (rgba >>> 24) & 255;
        int green = (rgba >>> 16) & 255;
        int blue = (rgba >>> 8) & 255;
        int alpha = HALO_ALPHA;

        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUADS, MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181706_f", "POSITION_COLOR"));
        emitCuboid(buffer, cuboid, transformation, red, green, blue, alpha, 0.03D, activeHaloScale());
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    public static void renderImmediateLampItem(int color) {
        int rgba = enumColourRgba(color);
        int red = (rgba >>> 24) & 255;
        int green = (rgba >>> 16) & 255;
        int blue = (rgba >>> 8) & 255;

        beginImmediateHalo();
        try {
            Tessellator tessellator = MinecraftReflectionCompat.tessellator();
            BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
            MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUADS, MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181706_f", "POSITION_COLOR"));
            emitBox(buffer, -0.03D, -0.03D, -0.03D, 1.03D, 1.03D, 1.03D,
                    red, green, blue, HALO_ALPHA, activeHaloScale());
            MinecraftReflectionCompat.tessellatorDraw(tessellator);
        } finally {
            endImmediateHalo();
        }
    }

    public static boolean renderSolidProjectRedIlluminationItem(ItemStack stack) {
        if (!hasItem(stack)) {
            return false;
        }

        ResourceLocation name = itemName(stack);
        if (!isProjectRedIlluminationItem(stack, name)) {
            return false;
        }

        int metadata = MinecraftReflectionCompat.itemStackMetadata(stack);
        int color = Math.floorMod(metadata, 16);
        renderImmediateLampItem(color);
        return true;
    }

    public static void renderOriginalCclItem(Object renderer, ItemStack stack, Object model) {
        if (renderer == null || model == null) {
            return;
        }
        try {
            Method method = renderer.getClass().getMethod(
                    "func_180454_a",
                    ItemStack.class,
                    Class.forName("net.minecraft.client.renderer.block.model.IBakedModel", false, ProjectRedHaloRenderer.class.getClassLoader())
            );
            method.invoke(renderer, stack, model);
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!originalRendererFailureLogged) {
                originalRendererFailureLogged = true;
                MainMod.LOGGER.warn("[ProjectRedHalo] Failed to call original CodeChicken item renderer for {}", itemName(stack), e);
            }
        }
    }

    public static void renderSolidProjectRedRendererItem(ItemStack stack, String source) {
        int metadata = !MinecraftReflectionCompat.itemStackIsEmpty(stack) ? MinecraftReflectionCompat.itemStackMetadata(stack) : 0;
        int color = Math.floorMod(metadata, 16);
        renderImmediateLampItem(color);
    }

    private static boolean isProjectRedIlluminationItem(ItemStack stack, ResourceLocation name) {
        if (name != null && "projectred-illumination".equals(MinecraftReflectionCompat.resourceNamespace(name)) && isProjectRedLightItem(MinecraftReflectionCompat.resourcePath(name))) {
            return true;
        }

        String itemClass = itemClassName(stack);
        if ("mrtjp.projectred.illumination.ItemBlockLamp".equals(itemClass)
                || "mrtjp.projectred.illumination.ItemBaseLight".equals(itemClass)) {
            return true;
        }

        return false;
    }

    private static boolean isProjectRedLightItem(String path) {
        return "lamp".equals(path)
                || "inverted_lamp".equals(path)
                || "fixture_light".equals(path)
                || "fixture".equals(path)
                || "inverted_fixture_light".equals(path)
                || "inverted_fixture".equals(path)
                || "lantern".equals(path)
                || "inverted_lantern".equals(path)
                || "cage_lamp".equals(path)
                || "cage".equals(path)
                || "inverted_cage_lamp".equals(path)
                || "inverted_cage".equals(path)
                || "fallout_lamp".equals(path)
                || "fallout".equals(path)
                || "inverted_fallout_lamp".equals(path)
                || "inverted_fallout".equals(path);
    }

    private static ResourceLocation itemName(ItemStack stack) {
        Item item = MinecraftReflectionCompat.itemStackItem(stack);
        return item != null ? MinecraftReflectionCompat.call(item, ResourceLocation.class, null, new String[]{"getRegistryName"}, MinecraftReflectionCompat.NO_PARAMETERS) : null;
    }

    private static String itemClassName(ItemStack stack) {
        Item item = MinecraftReflectionCompat.itemStackItem(stack);
        return item != null ? item.getClass().getName() : "";
    }

    private static boolean hasItem(ItemStack stack) {
        return !MinecraftReflectionCompat.itemStackIsEmpty(stack) && MinecraftReflectionCompat.itemStackItem(stack) != null;
    }

    public static void endImmediateHalo() {
        if (stateDepth <= 0) {
            return;
        }
        if (--stateDepth > 0) {
            return;
        }

        MinecraftReflectionCompat.glStateDepthMask(savedDepthMask);
        if (savedCull) {
            MinecraftReflectionCompat.glStateEnableCull();
        } else {
            MinecraftReflectionCompat.glStateDisableCull();
        }
        if (savedLighting) {
            MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179145_e", "enableLighting"}, MinecraftReflectionCompat.NO_PARAMETERS);
        } else {
            MinecraftReflectionCompat.glStateDisableLighting();
        }
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179112_b", "blendFunc"},
                new Class<?>[]{int.class, int.class}, savedBlendSrc, savedBlendDst);
        if (savedBlend) {
            MinecraftReflectionCompat.glStateEnableBlend();
        } else {
            MinecraftReflectionCompat.glStateDisableBlend();
        }

        for (int i = 0; i < HALO_TEXTURE_UNITS.length; i++) {
            GL13.glActiveTexture(HALO_TEXTURE_UNITS[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, savedTexture2D[i]);
            if (savedTexture2DEnabled[i]) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
        }
        GL13.glActiveTexture(savedActiveTexture);
        MinecraftReflectionCompat.glUseProgram(savedProgram);
        activeScale = 1.0D;
    }

    private static int enumColourRgba(int color) {
        try {
            ClassLoader loader = ProjectRedHaloRenderer.class.getClassLoader();
            Class<?> enumColourClass = Class.forName("codechicken.lib.colour.EnumColour", false, loader);
            Object[] values = (Object[]) enumColourClass.getMethod("values").invoke(null);
            if (values.length == 0) {
                return 0xFFFFFFFF;
            }
            Object value = values[Math.floorMod(color, values.length)];
            return ((Number) value.getClass().getMethod("rgba").invoke(value)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0xFFFFFFFF;
        }
    }

    private static void emitCuboid(BufferBuilder buffer, Object cuboid, Object transformation,
                                   int red, int green, int blue, int alpha) {
        emitCuboid(buffer, cuboid, transformation, red, green, blue, alpha, 0.0D, activeHaloScale());
    }

    private static void emitCuboid(BufferBuilder buffer, Object cuboid, Object transformation,
                                   int red, int green, int blue, int alpha, double inflate, double scale) {
        try {
            Object min = field(cuboid, "min").get(cuboid);
            Object max = field(cuboid, "max").get(cuboid);
            double x0 = field(min, "x").getDouble(min) - inflate;
            double y0 = field(min, "y").getDouble(min) - inflate;
            double z0 = field(min, "z").getDouble(min) - inflate;
            double x1 = field(max, "x").getDouble(max) + inflate;
            double y1 = field(max, "y").getDouble(max) + inflate;
            double z1 = field(max, "z").getDouble(max) + inflate;

            double centerX = (x0 + x1) * 0.5D;
            double centerY = (y0 + y1) * 0.5D;
            double centerZ = (z0 + z1) * 0.5D;
            x0 = scaleAround(centerX, x0, scale);
            x1 = scaleAround(centerX, x1, scale);
            y0 = scaleAround(centerY, y0, scale);
            y1 = scaleAround(centerY, y1, scale);
            z0 = scaleAround(centerZ, z0, scale);
            z1 = scaleAround(centerZ, z1, scale);

            emitFace(buffer, transformation, red, green, blue, alpha,
                    x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            emitFace(buffer, transformation, red, green, blue, alpha,
                    x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
            emitFace(buffer, transformation, red, green, blue, alpha,
                    x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
            emitFace(buffer, transformation, red, green, blue, alpha,
                    x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
            emitFace(buffer, transformation, red, green, blue, alpha,
                    x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
            emitFace(buffer, transformation, red, green, blue, alpha,
                    x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void emitFace(BufferBuilder buffer, Object transformation,
                                 int red, int green, int blue, int alpha,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 double x3, double y3, double z3) {
        emitVertex(buffer, transformation, x0, y0, z0, red, green, blue, alpha);
        emitVertex(buffer, transformation, x1, y1, z1, red, green, blue, alpha);
        emitVertex(buffer, transformation, x2, y2, z2, red, green, blue, alpha);
        emitVertex(buffer, transformation, x3, y3, z3, red, green, blue, alpha);
    }

    private static void emitBox(BufferBuilder buffer,
                                double x0, double y0, double z0,
                                double x1, double y1, double z1,
                                int red, int green, int blue, int alpha, double scale) {
        double centerX = (x0 + x1) * 0.5D;
        double centerY = (y0 + y1) * 0.5D;
        double centerZ = (z0 + z1) * 0.5D;
        x0 = scaleAround(centerX, x0, scale);
        x1 = scaleAround(centerX, x1, scale);
        y0 = scaleAround(centerY, y0, scale);
        y1 = scaleAround(centerY, y1, scale);
        z0 = scaleAround(centerZ, z0, scale);
        z1 = scaleAround(centerZ, z1, scale);

        emitFace(buffer, null, red, green, blue, alpha,
                x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        emitFace(buffer, null, red, green, blue, alpha,
                x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        emitFace(buffer, null, red, green, blue, alpha,
                x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        emitFace(buffer, null, red, green, blue, alpha,
                x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        emitFace(buffer, null, red, green, blue, alpha,
                x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        emitFace(buffer, null, red, green, blue, alpha,
                x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
    }

    private static double activeHaloScale() {
        return stateDepth > 0 ? activeScale : 1.0D;
    }

    private static double scaleAround(double center, double value, double scale) {
        return center + (value - center) * scale;
    }

    private static void emitVertex(BufferBuilder buffer, Object transformation,
                                   double x, double y, double z, int red, int green, int blue, int alpha) {
        try {
            Object vertex = vectorConstructor().newInstance(x, y, z);
            if (transformation != null) {
                vectorApplyMethod().invoke(vertex, transformation);
            }
            double transformedX = field(vertex, "x").getDouble(vertex);
            double transformedY = field(vertex, "y").getDouble(vertex);
            double transformedZ = field(vertex, "z").getDouble(vertex);
            MinecraftReflectionCompat.bufferPosColorEnd(buffer, transformedX, transformedY, transformedZ, red, green, blue, alpha);
        } catch (ReflectiveOperationException ignored) {
            MinecraftReflectionCompat.bufferPosColorEnd(buffer, x, y, z, red, green, blue, alpha);
        }
    }

    private static Constructor<?> vectorConstructor() throws ReflectiveOperationException {
        Class<?> vectorClass = Class.forName("codechicken.lib.vec.Vector3", false, ProjectRedHaloRenderer.class.getClassLoader());
        return vectorClass.getConstructor(double.class, double.class, double.class);
    }

    private static Method vectorApplyMethod() throws ReflectiveOperationException {
        Class<?> vectorClass = Class.forName("codechicken.lib.vec.Vector3", false, ProjectRedHaloRenderer.class.getClassLoader());
        Class<?> transformationClass = Class.forName("codechicken.lib.vec.Transformation", false, ProjectRedHaloRenderer.class.getClassLoader());
        return vectorClass.getMethod("apply", transformationClass);
    }

    private static Field field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

}
