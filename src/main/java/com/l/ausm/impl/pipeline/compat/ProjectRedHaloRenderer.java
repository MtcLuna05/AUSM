package com.l.ausm.impl.pipeline.compat;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ProjectRedHaloRenderer {
    private static final int HALO_ALPHA = 112;
    private static final boolean ITEM_AUDIT_LOGGING = Boolean.getBoolean("ausm.projectred.audit");

    private static final int[] HALO_TEXTURE_UNITS = {
            OpenGlHelper.defaultTexUnit,
            OpenGlHelper.lightmapTexUnit,
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
    private static int solidItemLogCount;
    private static int skippedItemLogCount;
    private static int itemAuditLogCount;
    private static String lastHeldAuditMain = "";
    private static String lastHeldAuditOffhand = "";

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

        OpenGlHelper.glUseProgram(0);
        for (int i = 0; i < HALO_TEXTURE_UNITS.length; i++) {
            GL13.glActiveTexture(HALO_TEXTURE_UNITS[i]);
            savedTexture2D[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            savedTexture2DEnabled[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
        GL13.glActiveTexture(savedActiveTexture);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
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

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        emitCuboid(buffer, cuboid, transformation, red, green, blue, alpha, 0.03D, activeHaloScale());
        tessellator.draw();
    }

    public static void renderImmediateLampItem(int color) {
        int rgba = enumColourRgba(color);
        int red = (rgba >>> 24) & 255;
        int green = (rgba >>> 16) & 255;
        int blue = (rgba >>> 8) & 255;

        beginImmediateHalo();
        try {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            emitBox(buffer, -0.03D, -0.03D, -0.03D, 1.03D, 1.03D, 1.03D,
                    red, green, blue, HALO_ALPHA, activeHaloScale());
            tessellator.draw();
        } finally {
            endImmediateHalo();
        }
    }

    public static boolean renderSolidProjectRedIlluminationItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return false;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        if (!isProjectRedIlluminationItem(stack, name)) {
            return false;
        }

        int color = Math.floorMod(stack.getMetadata(), 16);
        if (solidItemLogCount++ < 12) {
            com.l.ausm.impl.MainMod.LOGGER.info("[ProjectRedHalo] Replaced item render with solid geometry: item={} meta={} color={}",
                    name, stack.getMetadata(), color);
        }
        renderImmediateLampItem(color);
        return true;
    }

    public static void auditHeldItems(Minecraft minecraft) {
        if (!ITEM_AUDIT_LOGGING) {
            return;
        }
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        EntityPlayer player = minecraft.player;
        lastHeldAuditMain = auditHeldItem(player.getHeldItemMainhand(), "held_main", lastHeldAuditMain);
        lastHeldAuditOffhand = auditHeldItem(player.getHeldItemOffhand(), "held_offhand", lastHeldAuditOffhand);
    }

    public static void auditRenderItem(ItemStack stack, String source, Object detail) {
        if (!ITEM_AUDIT_LOGGING) {
            return;
        }
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        String itemClass = stack.getItem().getClass().getName();
        boolean candidate = isAuditCandidate(name, itemClass);
        if (!candidate && itemAuditLogCount >= 4) {
            return;
        }

        logItemAudit(source, stack, name, itemClass, detail, isProjectRedIlluminationItem(stack, name));
    }

    private static String auditHeldItem(ItemStack stack, String source, String previousKey) {
        String key = itemAuditKey(stack);
        if (key.equals(previousKey)) {
            return previousKey;
        }

        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            if (!previousKey.isEmpty() && itemAuditLogCount++ < 32) {
                com.l.ausm.impl.MainMod.LOGGER.info("[ProjectRedHaloAudit] source={} item=empty", source);
            }
            return key;
        }

        ResourceLocation name = stack.getItem().getRegistryName();
        String itemClass = stack.getItem().getClass().getName();
        logItemAudit(source, stack, name, itemClass, "held-change", isProjectRedIlluminationItem(stack, name));
        return key;
    }

    private static void logItemAudit(String source, ItemStack stack, ResourceLocation name,
                                     String itemClass, Object detail, boolean matched) {
        if (!ITEM_AUDIT_LOGGING) {
            return;
        }
        if (itemAuditLogCount++ >= 32) {
            return;
        }

        com.l.ausm.impl.MainMod.LOGGER.info(
                "[ProjectRedHaloAudit] source={} item={} meta={} count={} class={} display='{}' matched={} detail={}",
                source,
                name,
                stack.getMetadata(),
                stack.getCount(),
                itemClass,
                safeDisplayName(stack),
                matched,
                detail
        );
    }

    private static boolean isAuditCandidate(ResourceLocation name, String itemClass) {
        if (name != null && name.getNamespace() != null && name.getNamespace().contains("projectred")) {
            return true;
        }
        return itemClass != null && itemClass.toLowerCase(java.util.Locale.ROOT).contains("projectred");
    }

    private static String itemAuditKey(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return "";
        }
        ResourceLocation name = stack.getItem().getRegistryName();
        return String.valueOf(name) + ':' + stack.getMetadata() + ':' + stack.getCount();
    }

    private static String safeDisplayName(ItemStack stack) {
        try {
            return stack.getDisplayName();
        } catch (RuntimeException e) {
            return "<error>";
        }
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
            if (skippedItemLogCount++ < 8) {
                com.l.ausm.impl.MainMod.LOGGER.warn("[ProjectRedHalo] Failed to call original CodeChicken item renderer for {}", itemName(stack), e);
            }
        }
    }

    public static void renderSolidProjectRedRendererItem(ItemStack stack, String source) {
        int metadata = stack != null && !stack.isEmpty() ? stack.getMetadata() : 0;
        int color = Math.floorMod(metadata, 16);
        if (solidItemLogCount++ < 24) {
            ResourceLocation name = stack != null && !stack.isEmpty() && stack.getItem() != null
                    ? stack.getItem().getRegistryName()
                    : null;
            com.l.ausm.impl.MainMod.LOGGER.info("[ProjectRedHalo] Replaced {} item render with solid geometry: item={} meta={} color={}",
                    source, name, metadata, color);
        }
        renderImmediateLampItem(color);
    }

    private static boolean isProjectRedIlluminationItem(ItemStack stack, ResourceLocation name) {
        if (name != null && "projectred-illumination".equals(name.getNamespace()) && isProjectRedLightItem(name.getPath())) {
            return true;
        }

        String itemClass = stack.getItem().getClass().getName();
        if ("mrtjp.projectred.illumination.ItemBlockLamp".equals(itemClass)
                || "mrtjp.projectred.illumination.ItemBaseLight".equals(itemClass)) {
            return true;
        }

        if (name != null && "projectred-illumination".equals(name.getNamespace()) && skippedItemLogCount++ < 12) {
            com.l.ausm.impl.MainMod.LOGGER.info("[ProjectRedHalo] Saw unhandled ProjectRed illumination item: item={} meta={} class={}",
                    name, stack.getMetadata(), itemClass);
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
        return stack != null && !stack.isEmpty() && stack.getItem() != null ? stack.getItem().getRegistryName() : null;
    }

    public static void endImmediateHalo() {
        if (stateDepth <= 0) {
            return;
        }
        if (--stateDepth > 0) {
            return;
        }

        GlStateManager.depthMask(savedDepthMask);
        if (savedCull) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        if (savedLighting) {
            GlStateManager.enableLighting();
        } else {
            GlStateManager.disableLighting();
        }
        GlStateManager.blendFunc(savedBlendSrc, savedBlendDst);
        if (savedBlend) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
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
        OpenGlHelper.glUseProgram(savedProgram);
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
            buffer.pos(transformedX, transformedY, transformedZ).color(red, green, blue, alpha).endVertex();
        } catch (ReflectiveOperationException ignored) {
            buffer.pos(x, y, z).color(red, green, blue, alpha).endVertex();
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
