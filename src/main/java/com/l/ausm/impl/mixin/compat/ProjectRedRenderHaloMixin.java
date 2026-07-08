package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.ProjectRedHaloRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(targets = "mrtjp.projectred.core.RenderHalo$", remap = false)
public class ProjectRedRenderHaloMixin {
    @Inject(
            method = "onRenderWorldLast(Lnet/minecraftforge/client/event/RenderWorldLastEvent;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedWorldHalo(RenderWorldLastEvent event, CallbackInfo ci) {
        Object renderList = ausm$renderList();
        if (!ausm$hasNext(renderList)) {
            return;
        }

        ci.cancel();
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Entity viewEntity = minecraft != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(minecraft) : null;
        if (minecraft == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) == null || viewEntity == null) {
            ausm$clearProjectRedHaloQueue();
            return;
        }

        float partialTicks = event.getPartialTicks();
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179094_E", "pushMatrix"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
        try {
            double viewX = com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity);
            double viewY = com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity);
            double viewZ = com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity);
            double lastViewX = com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity);
            double lastViewY = com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity);
            double lastViewZ = com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity);
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179109_b", "translate"},
                new Class<?>[] {float.class, float.class, float.class}, (viewX - (viewX - lastViewX) * partialTicks - lastViewX), (viewY - (viewY - lastViewY) * partialTicks - lastViewY), (viewZ - (viewZ - lastViewZ) * partialTicks - lastViewZ));;

            ProjectRedHaloRenderer.beginImmediateHalo();
            try {
                Object iterator = ausm$iterator(renderList);
                int limit = ausm$haloLimit(renderList);
                int rendered = 0;
                while (iterator != null && rendered < limit && ausm$iteratorHasNext(iterator)) {
                    ausm$renderQueuedHalo(ausm$iteratorNext(iterator), viewEntity);
                    rendered++;
                }
            } finally {
                ProjectRedHaloRenderer.endImmediateHalo();
            }
        } finally {
            ausm$clearProjectRedHaloQueue();
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179121_F", "popMatrix"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
        }
    }

    @Inject(
            method = "renderHalo(Lcodechicken/lib/vec/Cuboid6;ILcodechicken/lib/vec/Transformation;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedDirectHalo(@Coerce Object cuboid, int color, @Coerce Object transformation, CallbackInfo ci) {
        ci.cancel();
        ProjectRedHaloRenderer.beginImmediateHalo();
        try {
            ProjectRedHaloRenderer.renderImmediateHalo(cuboid, color, transformation);
        } finally {
            ProjectRedHaloRenderer.endImmediateHalo();
        }
    }

    @Inject(
            method = "prepareRenderState()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedPrepareState(CallbackInfo ci) {
        ci.cancel();
        ProjectRedHaloRenderer.beginImmediateHalo();
    }

    @Inject(
            method = "restoreRenderState()V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ausm$replaceProjectRedRestoreState(CallbackInfo ci) {
        ci.cancel();
        ProjectRedHaloRenderer.endImmediateHalo();
    }

    @Unique
    private Object ausm$renderList() {
        try {
            return ausm$field(this, "renderList").get(this);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static void ausm$renderQueuedHalo(Object lightCache, Entity viewEntity) {
        if (lightCache == null || viewEntity == null) {
            return;
        }
        try {
            BlockPos pos = (BlockPos) ausm$invoke(lightCache, "pos");
            int color = ((Number) ausm$invoke(lightCache, "color")).intValue();
            Object cuboid = ausm$invoke(lightCache, "cube");
            Object transformation = ausm$translation(
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) - com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) - com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) - com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity)
            );
            ProjectRedHaloRenderer.renderImmediateHalo(cuboid, color, transformation);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Object ausm$translation(double x, double y, double z) throws ReflectiveOperationException {
        Class<?> translationClass = Class.forName("codechicken.lib.vec.Translation", false, ProjectRedRenderHaloMixin.class.getClassLoader());
        Constructor<?> constructor = translationClass.getConstructor(double.class, double.class, double.class);
        return constructor.newInstance(x, y, z);
    }

    @Unique
    private static Object ausm$iterator(Object collection) {
        if (collection == null) {
            return null;
        }
        try {
            return collection.getClass().getMethod("iterator").invoke(collection);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static boolean ausm$hasNext(Object collection) {
        Object iterator = ausm$iterator(collection);
        return iterator != null && ausm$iteratorHasNext(iterator);
    }

    @Unique
    private static boolean ausm$iteratorHasNext(Object iterator) {
        try {
            return (Boolean) iterator.getClass().getMethod("hasNext").invoke(iterator);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static Object ausm$iteratorNext(Object iterator) {
        try {
            return iterator.getClass().getMethod("next").invoke(iterator);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static int ausm$haloLimit(Object renderList) {
        int configuredLimit = ausm$configuredHaloLimit();
        if (configuredLimit >= 0) {
            return configuredLimit;
        }
        try {
            return ((Number) renderList.getClass().getMethod("size").invoke(renderList)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    @Unique
    private static int ausm$configuredHaloLimit() {
        try {
            Class<?> configurator = Class.forName("mrtjp.projectred.core.Configurator$", false, ProjectRedRenderHaloMixin.class.getClassLoader());
            Object module = configurator.getField("MODULE$").get(null);
            return ((Number) configurator.getMethod("lightHaloMax").invoke(module)).intValue();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    @Unique
    private void ausm$clearProjectRedHaloQueue() {
        try {
            Class<?> vectorModuleClass = Class.forName("scala.collection.immutable.Vector$", false, ProjectRedRenderHaloMixin.class.getClassLoader());
            Object module = vectorModuleClass.getField("MODULE$").get(null);
            Object emptyVector = vectorModuleClass.getMethod("empty").invoke(module);
            ausm$field(this, "renderList").set(this, emptyVector);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @Unique
    private static Object ausm$invoke(Object owner, String name) throws ReflectiveOperationException {
        Method method = owner.getClass().getMethod(name);
        return method.invoke(owner);
    }

    @Unique
    private static Field ausm$field(Object owner, String name) throws ReflectiveOperationException {
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
