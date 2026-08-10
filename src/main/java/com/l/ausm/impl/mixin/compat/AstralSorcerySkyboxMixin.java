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
import org.lwjgl.opengl.GL11;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderAstralSkybox", remap = false)
public class AstralSorcerySkyboxMixin {
    private static boolean logged;
    private static boolean ausm$loggedShaderlessSkyRepair;
    private static int ausm$nativeSkyOcclusionProbeCount;
    private boolean ausm$sunsetPhaseActive;

    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 0
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawOrSuppressAstralUpperSky(int displayList, float partialTicks) {
        PipelineContext context = PipelineContext.getInstance();
        context.setAstralSolarEclipseFactor(ausm$solarEclipseFactor(partialTicks));
        context.beginPhase(WorldRenderingPhase.SKY);
        try {
            if (context.shouldSuppressAstralUpperSkyGeometry()) {
                return;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class,
                    new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, displayList);
        } finally {
            context.endPass();
        }
    }

    @Redirect(
            method = "renderSky",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 1
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawOrSuppressAstralLowerSky(int displayList) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressAstralLowerSkyGeometry()) {
            return;
        }
        context.beginPhase(WorldRenderingPhase.SKY_GROUND);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class,
                    new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, displayList);
        } finally {
            context.endPass();
        }
    }

    @Inject(method = "renderSunsetToBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$suppressAstralSunsetFan(float[] sunsetColors, float partialTicks, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldSuppressAstralUpperSkyGeometry()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralSun(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.setSkyDetailKind(6);
        context.beginPhase(WorldRenderingPhase.SUN);
        if (context.shouldSuppressVanillaSunGeometry()) {
            ausm$logSuppression();
            context.endPass();
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSun(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.clearSkyDetailAsset();
    }

    @Inject(method = "renderSolarEclipseSun", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralSolarEclipseSun(@Coerce Object skyHandler, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.setSkyDetailKind(6);
        context.beginPhase(WorldRenderingPhase.ASTRAL_SOLAR_ECLIPSE);
        if (context.shouldSuppressVanillaSunGeometry()) {
            ausm$logSuppression();
            context.endPass();
            ci.cancel();
        }
    }

    @Inject(method = "renderSolarEclipseSun", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSolarEclipseSun(@Coerce Object skyHandler, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.clearSkyDetailAsset();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralMoon(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.setSkyDetailKind(6);
        context.beginPhase(WorldRenderingPhase.MOON);
        if (context.shouldSuppressVanillaMoonGeometry()) {
            ausm$logSuppression();
            context.endPass();
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("RETURN"), remap = false)
    private void ausm$endAstralMoon(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.clearSkyDetailAsset();
    }

    @Inject(method = "renderStars(Lnet/minecraft/world/World;F)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralStars(World world, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressShaderedAstralStars()) {
            context.forensicGlTrace("astral-sky-stars-suppressed", "partialTicks=" + partialTicks);
            ausm$logNativeSkyOcclusionProbe("stars-suppressed");
            ci.cancel();
            return;
        }
        context.setSkyDetailKind(4);
        context.beginPhase(WorldRenderingPhase.ASTRAL_STARS);
        if (context.shouldSuppressShaderlessOwnedSkyBaseGeometry()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        }
    }

    @Inject(method = "renderConstellations(Lnet/minecraft/world/World;F)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ausm$suppressShaderedAstralConstellations(World world, float partialTicks, CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldSuppressShaderedAstralConstellations()) {
            PipelineContext.getInstance().forensicGlTrace("astral-sky-constellations-suppressed", "partialTicks=" + partialTicks);
            ausm$logNativeSkyOcclusionProbe("constellations-suppressed");
            ci.cancel();
        }
    }

    @Inject(method = "renderStars(Lnet/minecraft/world/World;F)V", at = @At("RETURN"), remap = false)
    private void ausm$endAstralStars(World world, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.clearSkyDetailAsset();
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
            return ausm$voidWorldAstralStarBrightnessInput(world);
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((world), new String[] {"func_72880_h", "getStarBrightness"},
                new Class<?>[] {float.class}, 0.0F, (partialTicks));
    }

    @Redirect(
            method = "renderStars(Lnet/minecraft/world/World;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179131_c(FFFF)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$boostVoidWorldAstralStarColor(float red, float green, float blue, float alpha, World world, float partialTicks) {
        if (ausm$isSimpleVoidWorld(world)) {
            float visibility = ausm$voidWorldAstralNightVisibility(world);
            float boosted = Math.min(1.0F, Math.max(red, 0.62F * visibility));
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(boosted, boosted, boosted, alpha);
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(red, green, blue, alpha);
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
            return ausm$voidWorldAstralConstellationBrightnessInput(world);
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((world), new String[] {"func_72880_h", "getStarBrightness"},
                new Class<?>[] {float.class}, 0.0F, (partialTicks));
    }

    @Inject(method = "renderSunsetToBackground", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$beginAstralSunset(float[] colors, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        ausm$sunsetPhaseActive = false;
        context.beginPhase(WorldRenderingPhase.SUNSET);
        ausm$sunsetPhaseActive = true;
    }

    @Inject(method = "renderSunsetToBackground", at = @At("RETURN"), remap = false)
    private void ausm$endAstralSunset(float[] colors, float partialTicks, CallbackInfo ci) {
        if (ausm$sunsetPhaseActive) {
            ausm$sunsetPhaseActive = false;
            PipelineContext.getInstance().endPass();
        }
    }

    private static void ausm$logSuppression() {
        if (!logged) {
            logged = true;
            MainMod.LOGGER.info("[AstralCompat] Disabled Astral Sorcery sun/moon quads because the active shaderpack disables that celestial.");
        }
    }

    /**
     * Native Astral sky geometry is rendered after the shader-owned canvas and
     * therefore cannot be occluded per planet/celestial in GLSL.  Keep this
     * small proof at the cancellation boundary so a later native route is not
     * mistaken for a planet-alpha problem.
     */
    private static void ausm$logNativeSkyOcclusionProbe(String route) {
        // Probe disabled.
    }

    private static boolean ausm$usesCustomVoidSky(World world) {
        return PipelineContext.getInstance().isCustomVoidWorldSkyEnabled(world);
    }

    private static boolean ausm$isSimpleVoidWorld(World world) {
        net.minecraft.world.WorldProvider provider = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(provider) == 43;
    }

    private static long ausm$starRenderTime(World world) {
        if (!ausm$usesCustomVoidSky(world)) {
            Object time = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                    world,
                    new String[] {"func_72820_D", "getWorldTime"},
                    new Class<?>[0]
            );
            return time instanceof Number ? ((Number) time).longValue() : 0L;
        }
        return (long) ausm$astralDayLength() / 2L + 1L;
    }

    private static float ausm$voidWorldAstralNightVisibility(World world) {
        return ausm$usesCustomVoidSky(world) ? 1.0F : ausm$smoothstep(0.08F, 0.35F, ausm$voidWorldAstralNightFactor(world));
    }

    private static float ausm$voidWorldAstralStarBrightnessInput(World world) {
        return 0.38F * ausm$voidWorldAstralNightVisibility(world);
    }

    private static float ausm$voidWorldAstralConstellationBrightnessInput(World world) {
        return 0.5F * ausm$voidWorldAstralNightVisibility(world);
    }

    private static float ausm$voidWorldAstralNightFactor(World world) {
        float timeAngle = (com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0F;
        return Math.max((float) Math.sin(timeAngle * -6.2831855F), 0.0F);
    }

    private static float ausm$smoothstep(float edge0, float edge1, float value) {
        float t = Math.max(0.0F, Math.min(1.0F, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
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
            Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            if (minecraft == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) == null) {
                return 0.0f;
            }

            Class<?> handlerClass = Class.forName("hellfirepvp.astralsorcery.common.constellation.distribution.ConstellationSkyHandler");
            Object handler = handlerClass.getMethod("getInstance").invoke(null);
            Object worldHandler = handlerClass.getMethod("getWorldHandler", World.class).invoke(handler, com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft));
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

    private static String ausm$colors(float[] colors) {
        if (colors == null || colors.length < 4) {
            return "null";
        }
        return colors[0] + "," + colors[1] + "," + colors[2] + "," + colors[3];
    }
}
