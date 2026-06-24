package com.l.ausm.impl.mixin.compat;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderAstralSkybox", remap = false)
public class AstralSorcerySkyboxMixin {
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
    private static boolean logged;

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            remap = false
    )
    private void ausm$beginAstralUpperSky(float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.setAstralSolarEclipseFactor(ausm$solarEclipseFactor(partialTicks));
        context.beginPhase(WorldRenderingPhase.SKY);
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void ausm$endAstralUpperSky(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            ),
            require = 0,
            remap = false
    )
    private void ausm$beginAstralLowerSky(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SKY_GROUND);
    }

    @Inject(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void ausm$endAstralLowerSky(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralSun(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.SUN);
        if (context.shouldSuppressVanillaSunGeometry()) {
            ausm$logSuppression();
            context.endPass();
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSun(CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderSolarEclipseSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralSolarEclipseSun(@Coerce Object skyHandler, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.ASTRAL_SOLAR_ECLIPSE);
    }

    @Inject(method = "renderSolarEclipseSun", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSolarEclipseSun(@Coerce Object skyHandler, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralMoon(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.MOON);
        if (context.shouldSuppressVanillaMoonGeometry()) {
            ausm$logSuppression();
            context.endPass();
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("RETURN"), remap = false)
    private void ausm$endAstralMoon(CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderStars(Lnet/minecraft/world/World;F)V", at = @At("HEAD"), remap = false)
    private void ausm$beginAstralStars(World world, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.beginPhase(WorldRenderingPhase.ASTRAL_STARS);
    }

    @Inject(method = "renderStars(Lnet/minecraft/world/World;F)V", at = @At("RETURN"), remap = false)
    private void ausm$endAstralStars(World world, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Redirect(
            method = "renderStars(Lnet/minecraft/world/World;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72820_D()J"
            ),
            remap = false
    )
    private long ausm$voidWorldAstralStarTime(World world) {
        return ausm$starRenderTime(world);
    }

    @Redirect(
            method = "renderStars(Lnet/minecraft/world/World;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72880_h(F)F"
            ),
            remap = false
    )
    private float ausm$voidWorldAstralStarBrightness(World world, float partialTicks) {
        if (ausm$isSimpleVoidWorld(world)) {
            return 1.0f;
        }
        return world.getStarBrightness(partialTicks);
    }

    @Redirect(
            method = "renderConstellations(Lnet/minecraft/world/World;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72820_D()J"
            ),
            remap = false
    )
    private static long ausm$voidWorldAstralConstellationTime(World world) {
        return ausm$starRenderTime(world);
    }

    @Redirect(
            method = "renderConstellations(Lnet/minecraft/world/World;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72880_h(F)F"
            ),
            remap = false
    )
    private static float ausm$voidWorldAstralConstellationBrightness(World world, float partialTicks) {
        if (ausm$isSimpleVoidWorld(world)) {
            return 1.0f;
        }
        return world.getStarBrightness(partialTicks);
    }

    @Inject(method = "renderSunsetToBackground", at = @At("HEAD"), remap = false)
    private void ausm$beginAstralSunset(float[] colors, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SUNSET);
    }

    @Inject(method = "renderSunsetToBackground", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSunset(float[] colors, float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    private static void ausm$logSuppression() {
        if (!logged) {
            logged = true;
            MainMod.LOGGER.info("[AstralCompat] Disabled Astral Sorcery sun/moon quads because the active shaderpack disables that celestial.");
        }
    }

    private static boolean ausm$isSimpleVoidWorld(World world) {
        return world != null
                && world.provider != null
                && world.provider.getDimension() == SIMPLE_VOID_WORLD_DIMENSION_ID;
    }

    private static long ausm$starRenderTime(World world) {
        if (!ausm$isSimpleVoidWorld(world)) {
            return world != null ? world.getWorldTime() : 0L;
        }
        return (long) ausm$astralDayLength() / 2L + 1L;
    }

    private static int ausm$astralDayLength() {
        try {
            Class<?> configClass = Class.forName("hellfirepvp.astralsorcery.common.data.config.Config");
            return Math.max(2, configClass.getField("dayLength").getInt(null));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 24000;
        }
    }

    private static float ausm$solarEclipseFactor(float partialTicks) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.world == null) {
                return 0.0f;
            }

            Class<?> handlerClass = Class.forName("hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler");
            Object handler = handlerClass.getMethod("getInstance").invoke(null);
            Object worldHandler = handlerClass.getMethod("getWorldHandler", World.class).invoke(handler, minecraft.world);
            if (worldHandler == null) {
                return 0.0f;
            }

            Object activeEvent = worldHandler.getClass().getMethod("getCurrentlyActiveEvent").invoke(worldHandler);
            if (activeEvent == null || !"SOLAR_ECLIPSE".equals(String.valueOf(activeEvent))) {
                return 0.0f;
            }

            int tick = worldHandler.getClass().getField("solarEclipseTick").getInt(worldHandler);
            int prevTick = worldHandler.getClass().getField("prevSolarEclipseTick").getInt(worldHandler);
            int halfDuration = ((Number) handlerClass.getMethod("getSolarEclipseHalfDuration").invoke(null)).intValue();
            if (halfDuration <= 0) {
                return 1.0f;
            }

            float interpolated = prevTick + (tick - prevTick) * partialTicks;
            float distanceFromPeak = Math.abs(interpolated - halfDuration) / halfDuration;
            return Math.max(0.0f, Math.min(1.0f, 1.0f - distanceFromPeak));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 0.0f;
        }
    }
}
