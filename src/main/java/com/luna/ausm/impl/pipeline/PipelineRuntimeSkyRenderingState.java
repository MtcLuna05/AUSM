package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.ASTRAL_NATIVE_CONSTELLATIONS_OPTION;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.ASTRAL_NATIVE_STARS_OPTION;
import static com.luna.ausm.impl.pipeline.PipelineSkyConstants.ASTRAL_SKYBOX_CLASS;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionBoolean;

abstract class PipelineRuntimeDiagnosticsState2 extends PipelineRuntimeDiagnosticsState1 {
    public boolean shouldRenderSkyDisc() {
        return !isPipelineActive || shaderProperties.renderSettings().sky();
    }

    public boolean shouldUseCompleteOwnedSkyOverride() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        return mc != null
                && world != null
                && !isPipelineActive
                && self().isSimpleVoidWorld(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    /**
     * Shadered overworld-like dimensions use a single AUSM canvas. Entree is
     * the sole owner of the visible sky, celestials, and modded sky details.
     */
    public boolean shouldUseShaderOwnedSkyOverride() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        return self().shouldUseShaderOwnedSkyOverride(world);
    }

    public boolean shouldUseShaderOwnedSkyOverride(World world) {
        return isPipelineActive
                && world != null
                && self().isOverworldShaderEnvironment(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public void renderCompleteOwnedVoidSkyDetails(float partialTicks, WorldClient world, Minecraft mc) {
        if (mc == null
                || world == null
                || !self().shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        Object renderer = MinecraftReflectionCompat.worldProviderSkyRenderer(
                MinecraftReflectionCompat.worldProvider(world));
        if (renderer == null
                || (!self().isActualBotaniaVoidWorld(world)
                && !ASTRAL_SKYBOX_CLASS.equals(renderer.getClass().getName()))) {
            return;
        }
        try {
            if (ASTRAL_SKYBOX_CLASS.equals(renderer.getClass().getName())) {
                // Keep Astral's wrapper intact. Its own compatibility mixin
                // routes the delegated Botania renderer and constellation
                // pass, while avoiding the recursive vanilla sky branch for
                // dimensions that require Astral's sky handling.
                MinecraftReflectionCompat.invoke(
                        renderer,
                        new String[]{"render"},
                        new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                        partialTicks,
                        world,
                        mc);
                return;
            }
            if (!"vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(renderer.getClass().getName())) {
                return;
            }
            // Botania's base dome and sunset fan are suppressed by its AUSM
            // compatibility mixin; its planet/rainbow details remain intact.
            MinecraftReflectionCompat.invoke(
                    renderer,
                    new String[]{"render"},
                    new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                    partialTicks,
                    world,
                    mc);
        } catch (RuntimeException | LinkageError ignored) {
            // Optional detail path; the owned gradient remains authoritative.
        }
    }

    public void prepareShaderlessHiddenGuiFramebufferPresentation() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && MinecraftReflectionCompat.gameSettings(mc) != null
                && MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && MinecraftReflectionCompat.isGamePaused(mc);
        if (mc == null
                || !self().shouldUseShaderlessOwnedSky(mc)
                || (!hideGui && !paused && screen == null)) {
            return;
        }
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target != null
                && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                == MinecraftReflectionCompat.framebufferObject(target)) {
            // Framebuffer.framebufferRenderExt draws the attached texture into
            // the currently bound draw target. Ensure shaderless GUI/F1
            // presentation cannot sample and overwrite its own source FBO.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
        }
        MinecraftReflectionCompat.glUseProgram(0);
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableAlpha();
        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void restoreShaderlessHiddenGuiFramebufferTarget() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && MinecraftReflectionCompat.gameSettings(mc) != null
                && MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && MinecraftReflectionCompat.isGamePaused(mc);
        if (mc == null
                || !self().shouldUseShaderlessOwnedSky(mc)
                || (!hideGui && !paused && screen == null)) {
            return;
        }
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target == null) {
            return;
        }
        MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0
                ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
    }

    public boolean shouldUseShaderlessHiddenGuiPresentation() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && MinecraftReflectionCompat.gameSettings(mc) != null
                && MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && MinecraftReflectionCompat.isGamePaused(mc);
        return mc != null
                && self().shouldUseShaderlessOwnedSky(mc)
                && (hideGui || paused || screen != null);
    }

    public Object detachNonVanillaSkyRendererForVanillaSky() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft == null ? null : MinecraftReflectionCompat.world(minecraft);
        if (world == null || self().isRenderingBetterPortalsNestedView() || self().isRenderingBetterPortalsRenderPass()) {
            return null;
        }
        WorldProvider provider = MinecraftReflectionCompat.worldProvider(world);
        if (!isPipelineActive) {
            // In shaderless mode every provider keeps its registered native
            // renderer. This includes mod dimensions such as Twilight, Astral,
            // Botania, and the End; AUSM only supplies shared render-state
            // safety beneath their output.
            return null;
        }
        Object renderer = MinecraftReflectionCompat.worldProviderSkyRenderer(provider);
        if (renderer == null || !MinecraftReflectionCompat.setWorldProviderSkyRenderer(provider, null)) {
            return null;
        }
        return renderer;
    }

    public void restoreNonVanillaSkyRenderer(Object renderer) {
        if (renderer == null) {
            return;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft == null ? null : MinecraftReflectionCompat.world(minecraft);
        if (world == null) {
            return;
        }
        MinecraftReflectionCompat.setWorldProviderSkyRenderer(
                MinecraftReflectionCompat.worldProvider(world), renderer);
    }

    public boolean shouldSuppressVanillaUpperSkyGeometry() {
        return self().shouldUseShaderOwnedSkyOverride() || self().shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressVanillaSunGeometry() {
        return self().shouldSuppressShaderedVoidCelestialGeometry();
    }

    public boolean shouldSuppressVanillaMoonGeometry() {
        return self().shouldSuppressShaderedVoidCelestialGeometry();
    }

    protected boolean shouldSuppressShaderedVoidCelestialGeometry() {
        return self().shouldUseShaderOwnedSkyOverride() || self().shouldSuppressShaderedSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressVanillaStarsGeometry() {
        return self().shouldUseShaderOwnedSkyOverride()
                || isPipelineActive && !shaderProperties.renderSettings().stars();
    }

    public boolean shouldSuppressVanillaLowerSkyGeometry() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        boolean result = world != null
                && (isPipelineActive && self().shouldUseOwnedSkyOverrideWorld(world)
                || self().isCustomVoidWorldSkyEnabled(world)
                || self().isSimpleVoidWorld(world) && self().shouldUseShaderlessOwnedSky(mc)
                || self().shouldUseShaderedF1LowerSkyRepair(mc, world))
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
        self().logSkySuppressionDecision("vanilla-lower", mc, world, result);
        return result;
    }

    protected boolean areShaderpacksEnabled() {
        return MainMod.getShaderPackManager() != null
                && MainMod.getShaderPackManager().areShadersEnabled();
    }

    protected boolean shouldUseShaderlessOwnedSky(Minecraft mc) {
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        // External sky renderers are detached for the compatibility route. Keep
        // both dome hemispheres under one AUSM backing so F1 and GUI renders do
        // not inherit the Void World's dark vanilla lower hemisphere.
        return !isPipelineActive
                && world != null
                && self().isSimpleVoidWorld(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public boolean shouldSuppressShaderlessOwnedSkyBaseGeometry() {
        return self().shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressBotaniaVoidSkyBaseGeometry() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        return world != null
                && (self().shouldUseShaderOwnedSkyOverride(world)
                || self().shouldUseCompleteOwnedSkyOverride())
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public boolean shouldSuppressVanillaSunsetGeometry() {
        return self().shouldUseShaderOwnedSkyOverride()
                || self().shouldUseShaderlessOwnedSky(MinecraftReflectionCompat.minecraft());
    }

    public boolean shouldSuppressVoidWorldCustomSkyRenderer(Object skyRenderer, WorldClient world) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        return skyRenderer != null
                && world != null
                && self().shouldUseShaderlessOwnedSky(mc)
                && self().shouldUseOwnedSkyOverrideWorld(world)
                && self().isSimpleVoidWorld(world)
                && !PipelineRuntimeState.isAstralSkyRenderer(skyRenderer);
    }

    protected static boolean isAstralSkyRenderer(Object skyRenderer) {
        if (skyRenderer == null) {
            return false;
        }
        Class<?> type = skyRenderer.getClass();
        while (type != null) {
            if (ASTRAL_SKYBOX_CLASS.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /**
     * Astral's outer renderer temporarily removes the world's sky renderer and
     * re-enters RenderGlobal to delegate the Void World sky. That recursion is
     * what makes its output diverge in F1. Route directly to Botania's selected
     * renderer while keeping the invocation owned by AUSM's sky boundary.
     */
    public boolean renderShaderlessOwnedVoidCompatibilitySky(Object skyRenderer, float partialTicks,
                                                             WorldClient world, Minecraft minecraft) {
        if (isPipelineActive
                || skyRenderer == null
                || world == null
                || minecraft == null
                || !self().isSimpleVoidWorld(world)
                || !PipelineRuntimeState.isAstralSkyRenderer(skyRenderer)) {
            return false;
        }
        Object delegated = MinecraftReflectionCompat.field(
                skyRenderer, Object.class, null, "otherSkyRenderer");
        if (delegated == null
                || !"vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(delegated.getClass().getName())) {
            return false;
        }
        try {
            MinecraftReflectionCompat.invoke(
                    delegated,
                    new String[]{"render"},
                    new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                    partialTicks,
                    world,
                    minecraft);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public void renderShaderlessBotaniaVoidDetailsIfNeeded(float partialTicks, WorldClient world, Minecraft mc) {
        // The shaderless decorative overlays are depth-disabled and can be
        // rendered before the GUI screen is installed, so a GUI-only guard is
        // too late to prevent their bands from entering the presented world FBO.
        // Keep the owned sky gradient; defer these optional decorations until
        // their render boundary can be made depth-safe.
        return;
    }

    /**
     * The owned shaderless dome deliberately blocks both external base skies.
     * Re-add only their decorative passes after vanilla has drawn the regular
     * sun and moon, so neither mod can replace the continuous AUSM backdrop.
     */
    public void renderShaderlessOwnedSkyDetailsAfterCelestials(float partialTicks) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        if (mc == null || world == null || !self().shouldUseShaderlessOwnedSky(mc)) {
            return;
        }

        // Optional Botania/Astral decoration is disabled here for the same
        // reason as the shaderless compatibility path above: it is not
        // contained by the world depth buffer in the current presentation.
    }

    protected boolean isShaderlessSkyDecorationSuppressedForGui(Minecraft mc) {
        return mc != null
                && MinecraftReflectionCompat.currentScreen(mc) != null;
    }

    protected boolean isActualBotaniaVoidWorld(World world) {
        if (!self().isSimpleVoidWorld(world)) {
            return false;
        }
        Object renderer = MinecraftReflectionCompat.worldProviderSkyRenderer(
                MinecraftReflectionCompat.worldProvider(world));
        if (renderer == null) {
            return false;
        }
        String rendererClass = renderer.getClass().getName();
        if ("vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(rendererClass)) {
            return true;
        }
        if (!ASTRAL_SKYBOX_CLASS.equals(rendererClass)) {
            return false;
        }
        Object delegated = MinecraftReflectionCompat.field(
                renderer, Object.class, null, "otherSkyRenderer");
        return delegated != null
                && "vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(delegated.getClass().getName());
    }

    public boolean shouldSuppressShaderedAstralLowerSky() {
        return self().shouldUseShaderOwnedSkyOverride() || self().shouldSuppressShaderedSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressShaderedAstralStars() {
        return self().shouldUseShaderOwnedSkyOverride()
                || self().shouldSuppressNativeAstralVoidSkyDetail()
                || isPipelineActive
                && !optionBoolean(shaderProperties, ASTRAL_NATIVE_STARS_OPTION, true);
    }

    public boolean shouldSuppressShaderedAstralConstellations() {
        return self().shouldUseShaderOwnedSkyOverride()
                || self().shouldSuppressNativeAstralVoidSkyDetail()
                || isPipelineActive
                && !optionBoolean(shaderProperties, ASTRAL_NATIVE_CONSTELLATIONS_OPTION, true);
    }

    /**
     * Void World uses the shader-owned star field so planet compositing occurs
     * after it. Native Astral geometry draws later and therefore cannot be
     * selectively occluded by those planet textures.
     */
    protected boolean shouldSuppressNativeAstralVoidSkyDetail() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : MinecraftReflectionCompat.world(mc);
        return isPipelineActive
                && world != null
                && self().isSimpleVoidWorld(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public boolean shouldSuppressShaderedAstralStarsAndConstellations() {
        return self().shouldSuppressShaderedAstralStars() || self().shouldSuppressShaderedAstralConstellations();
    }

    public boolean shouldSuppressAstralUpperSkyGeometry() {
        return isPipelineActive && self().shouldSuppressShaderedAstralLowerSky();
    }

    public boolean shouldSuppressAstralLowerSkyGeometry() {
        return isPipelineActive && self().shouldSuppressShaderedAstralLowerSky();
    }

    protected boolean shouldSuppressShaderlessSimpleVoidSkyBaseGeometry() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        return self().shouldUseShaderlessOwnedSky(mc) && self().isSimpleVoidWorld(world);
    }

    protected boolean shouldSuppressShaderedSimpleVoidSkyBaseGeometry() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        return isPipelineActive
                && world != null
                && self().isSimpleVoidWorld(world)
                && self().isCustomVoidWorldSkyEnabled(world)
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    protected void logSkySuppressionDecision(String route, Minecraft mc, World world, boolean result) {
        if (mc == null
                || !MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc))
                || ownedSkyBackingDecisionProbeLogs++ >= 36) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMSkySuppressionProbe] route={} result={} active={} world={} dim={} simpleVoid={} customVoid={} owned={} shaderlessOwned={} bpNested={} bpPass={} screen={} hideGui={} paused={}",
                route,
                result,
                isPipelineActive,
                world == null ? "null" : world.getClass().getName(),
                world == null || MinecraftReflectionCompat.worldProvider(world) == null
                        ? Integer.MIN_VALUE
                        : MinecraftReflectionCompat.providerDimension(
                        MinecraftReflectionCompat.worldProvider(world)),
                self().isSimpleVoidWorld(world),
                self().isCustomVoidWorldSkyEnabled(world),
                self().shouldUseOwnedSkyOverrideWorld(world),
                self().shouldUseShaderlessOwnedSky(mc),
                self().isRenderingBetterPortalsNestedView(),
                self().isRenderingBetterPortalsRenderPass(),
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null,
                mc != null && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && MinecraftReflectionCompat.isGamePaused(mc)
        );
    }

    public boolean shouldForceShaderlessAstralVoidLowerSky(WorldClient world) {
        return false;
    }

    public boolean shouldFlattenShaderlessVoidVanillaLowerSky(WorldClient world) {
        return false;
    }

    public void logShaderlessVoidVanillaLowerSky(String stage, WorldClient world, float partialTicks, int pass, double originalHorizon, double adjustedHorizon, double eyeY) {
        // Old sky probe intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    public Vec3d forcedShaderlessAstralVoidBaseSkyColor() {
        return null;
    }

    protected Vec3d forcedShaderlessAstralVoidBaseSkyColor(WorldClient world) {
        if (world == null) {
            return null;
        }
        double time = (MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0D;
        double dayFactor = (Math.cos((time - 0.25D) * Math.PI * 2.0D) + 1.0D) * 0.5D;
        dayFactor = Math.clamp(dayFactor, 0.0D, 1.0D);
        double smoothDay = dayFactor * dayFactor * (3.0D - 2.0D * dayFactor);
        double red = 0.012D + 0.105D * smoothDay;
        double green = 0.014D + 0.145D * smoothDay;
        double blue = 0.030D + 0.235D * smoothDay;
        return new Vec3d(red, green, blue);
    }

    public void logAstralVoidSkyRenderEntry(float partialTicks) {
        // Old sky probe intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    protected void logShaderlessAstralSkyColor(String stage, WorldClient world, Entity entity, float partialTicks, Vec3d originalSkyColor, Vec3d effectiveSkyColor, double originalMax, boolean guiWorldRender) {
        // Old sky probe intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    protected static String formatVec3(Vec3d value) {
        if (value == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f",
                MinecraftReflectionCompat.vecX(value),
                MinecraftReflectionCompat.vecY(value),
                MinecraftReflectionCompat.vecZ(value));
    }

    protected void logAstralVoidSkyProbe(String stage, WorldClient world, double originalHorizon, double adjustedHorizon, float partialTicks) {
        // Probe disabled.
    }

    public boolean shouldSanitizeShaderlessNothiriumFog() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        WorldClient world = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        return !isPipelineActive
                && world != null
                && !self().isRenderingBetterPortalsNestedView()
                && !self().isRenderingBetterPortalsRenderPass();
    }

    public void beginShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        shaderlessNothiriumFogGuard.begin(self().shouldDisableShaderlessNothiriumTerrainFog());
    }

    public void endShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        shaderlessNothiriumFogGuard.end();
    }
}
