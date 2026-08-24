package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.lwjgl.opengl.GL11;

import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_BASALT_DELTAS_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_CRIMSON_FOREST_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_NETHER_WASTES_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_PALE_GARDEN_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_SOUL_SAND_VALLEY_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_WARPED_FOREST_ID;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.NETHER_SHADER_FOG_COLOR_SCALE;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.PORTAL_NETHER_FOG_COLOR;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.PORTAL_NETHER_FOG_DENSITY;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.SHADER_OVERWORLD_FOG_START_RATIO;

abstract class PipelineRuntimeEnvironmentState extends PipelineRuntimeUniformState {
    protected float[] shaderSkyColor(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (!self().isSimpleVoidWorld(world)) {
            return PipelineRuntimeState.skyColor(mc);
        }
        float daylight = PipelineRuntimeState.simpleVoidDaylight(world, mc);
        return new float[]{0.5f * daylight, 0.66275f * daylight, daylight};
    }

    protected float[] simpleVoidOverworldFogColor(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        float daylight = PipelineRuntimeState.simpleVoidDaylight(world, mc);
        return new float[]{
                0.7529412f * (daylight * 0.94f + 0.06f),
                0.84705883f * (daylight * 0.94f + 0.06f),
                daylight * 0.91f + 0.09f
        };
    }

    protected static float simpleVoidDaylight(World world, Minecraft mc) {
        if (world == null) {
            return 1.0f;
        }
        float partialTicks = mc != null
                ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f;
        float angle = MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks);
        return Math.clamp((float) Math.cos(angle * Math.PI * 2.0) * 2.0f + 0.5f, 0.0f, 1.0f);
    }

    protected float effectiveFogStart(Minecraft mc) {
        if (self().shouldUseNestedPortalFogFallback(mc)) {
            return self().isNetherRenderWorld(mc) ? 0.0f : self().portalFogFar(mc) * 0.75f;
        }
        if (self().isNetherRenderWorld(mc)) {
            return GL11.glIsEnabled(GL11.GL_FOG) ? GL11.glGetFloat(GL11.GL_FOG_START) : 0.0f;
        }
        return PipelineRuntimeState.shaderFarPlaneDistance(mc) * SHADER_OVERWORLD_FOG_START_RATIO;
    }

    protected float effectiveFogEnd(Minecraft mc) {
        if (self().shouldUseNestedPortalFogFallback(mc)) {
            return self().portalFogFar(mc);
        }
        if (self().isNetherRenderWorld(mc)) {
            return GL11.glIsEnabled(GL11.GL_FOG)
                    ? Math.max(GL11.glGetFloat(GL11.GL_FOG_END), PipelineRuntimeState.shaderRenderDistance(mc))
                    : PipelineRuntimeState.shaderRenderDistance(mc);
        }
        return PipelineRuntimeState.shaderFarPlaneDistance(mc);
    }

    protected float effectiveFogDensity(Minecraft mc) {
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return GL11.glGetFloat(GL11.GL_FOG_DENSITY);
        }
        if (self().isNetherRenderWorld(mc)) {
            return PORTAL_NETHER_FOG_DENSITY;
        }
        if (!self().shouldUseNestedPortalFogFallback(mc)) {
            return 0.0f;
        }
        return self().isNetherRenderWorld(mc) ? PORTAL_NETHER_FOG_DENSITY : 0.0f;
    }

    protected int effectiveFogMode(Minecraft mc) {
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return PipelineRuntimeState.currentGlFogMode();
        }
        if (!self().shouldUseNestedPortalFogFallback(mc)) {
            return self().isNetherRenderWorld(mc) ? 2 : 0;
        }
        return self().isNetherRenderWorld(mc) ? 2 : 0;
    }

    protected float[] effectiveFogColor(Minecraft mc) {
        if (self().isNetherRenderWorld(mc)) {
            if (self().shouldUseNestedPortalFogFallback(mc)) {
                return self().netherFogColor(mc);
            }
            float[] fogColor = self().currentGlFogColor();
            return self().isProbablyUnsetFogColor(fogColor) ? self().netherFogColor(mc) : self().dampenNetherFogColor(fogColor);
        }
        if (self().isSimpleVoidWorld(PipelineRuntimeState.renderWorld(mc))) {
            return self().simpleVoidOverworldFogColor(mc);
        }
        float[] fogColor = GL11.glIsEnabled(GL11.GL_FOG) ? self().currentGlFogColor() : null;
        return self().isProbablyUnsetFogColor(fogColor) ? self().overworldFogColor(mc) : fogColor;
    }

    protected float[] overworldFogColor(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world != null) {
            return PipelineRuntimeState.vec3(MinecraftReflectionCompat.call(world, Vec3d.class, null, new String[]{"func_72948_g", "getFogColor", "func_72824_f"},
                    new Class<?>[]{float.class}, mc != null ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f));
        }
        return PipelineRuntimeState.skyColor(mc);
    }

    protected float[] netherFogColor(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world != null) {
            return self().dampenNetherFogColor(PipelineRuntimeState.vec3(MinecraftReflectionCompat.call(world, Vec3d.class, null, new String[]{"func_72948_g", "getFogColor", "func_72824_f"},
                    new Class<?>[]{float.class}, mc != null ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f)));
        }
        return self().dampenNetherFogColor(PORTAL_NETHER_FOG_COLOR);
    }

    protected float[] dampenNetherFogColor(float[] color) {
        if (color == null || color.length < 3) {
            color = PORTAL_NETHER_FOG_COLOR;
        }
        return new float[]{
                self().clamp01(color[0] * NETHER_SHADER_FOG_COLOR_SCALE),
                self().clamp01(color[1] * NETHER_SHADER_FOG_COLOR_SCALE),
                self().clamp01(color[2] * NETHER_SHADER_FOG_COLOR_SCALE)
        };
    }

    protected float[] currentGlFogColor() {
        fogColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, fogColorBuffer);
        return new float[]{
                self().clamp01(fogColorBuffer.get(0)),
                self().clamp01(fogColorBuffer.get(1)),
                self().clamp01(fogColorBuffer.get(2))
        };
    }

    protected boolean isProbablyUnsetFogColor(float[] color) {
        return color == null
                || color.length < 3
                || (color[0] <= 0.0001f && color[1] <= 0.0001f && color[2] <= 0.0001f);
    }

    protected boolean shouldUseNestedPortalFogFallback(Minecraft mc) {
        return self().isBetterPortalsExternalWorldTarget()
                && !GL11.glIsEnabled(GL11.GL_FOG)
                && PipelineRuntimeState.renderWorld(mc) != null;
    }

    protected boolean isNetherRenderWorld(Minecraft mc) {
        return self().safeDimensionId(PipelineRuntimeState.renderWorld(mc)) == -1;
    }

    protected static float cloudHeight(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null || MinecraftReflectionCompat.worldProvider(world) == null) {
            return 128.0f;
        }
        return MinecraftReflectionCompat.callFloat(MinecraftReflectionCompat.worldProvider(world), new String[]{"func_76571_f", "getCloudHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, 128.0F);
    }

    protected static boolean hasSkylight(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        return world != null && MinecraftReflectionCompat.worldProvider(world) != null && MinecraftReflectionCompat.providerHasSkyLight(MinecraftReflectionCompat.worldProvider(world));
    }

    protected boolean shouldRepairCurrentSkybox(Minecraft mc) {
        return false;
    }

    protected boolean shouldForceUiSkyboxRepair(Minecraft mc) {
        return false;
    }

    protected static float cloudTime(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        Object time = MinecraftReflectionCompat.invoke(
                world,
                new String[]{"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        return time instanceof Number ? (float) (((Number) time).longValue() + (mc != null ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f)) : 0.0f;
    }

    protected boolean isEyeInCave(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null || PipelineRuntimeState.eyeFluidState(mc) != 0) {
            return false;
        }
        BlockPos pos = self().currentCameraBlockPos();
        return MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos) <= 1 && MinecraftReflectionCompat.blockPosY(pos) < MinecraftReflectionCompat.callInt(world, new String[]{"func_181545_F", "getSeaLevel"}, MinecraftReflectionCompat.NO_PARAMETERS, 63);
    }

    protected float portalFogFar(Minecraft mc) {
        return PipelineRuntimeState.shaderFarPlaneDistance(mc);
    }

    protected static float shaderFarPlaneDistance(Minecraft mc) {
        return PipelineRuntimeState.shaderRenderDistance(mc) * 2.0f;
    }

    protected static float shaderRenderDistance(Minecraft mc) {
        return Math.max(16.0f, mc != null ? MinecraftReflectionCompat.renderDistanceChunks(mc) * 16.0f : 16.0f);
    }

    protected static int currentGlFogMode() {
        return switch (GL11.glGetInteger(GL11.GL_FOG_MODE)) {
            case GL11.GL_LINEAR -> 0;
            case GL11.GL_EXP -> 1;
            case GL11.GL_EXP2 -> 2;
            default -> -1;
        };
    }

    protected int currentBiomeExpressionId(Minecraft mc) {
        Biome biome = self().currentCameraBiome(mc);
        if (biome == null) {
            return -1;
        }
        int irisId = PipelineRuntimeState.irisBiomeId(biome);
        return irisId >= 0 ? irisId : MinecraftReflectionCompat.callInt(Biome.class, new String[]{"func_185362_a", "getIdForBiome"},
                new Class<?>[]{Biome.class}, -1, biome);
    }

    protected int currentBiomePrecipitation(Minecraft mc) {
        Biome biome = self().currentCameraBiome(mc);
        if (biome == null) {
            return 0;
        }

        BlockPos pos = self().currentCameraBlockPos();
        boolean canRain = MinecraftReflectionCompat.callBoolean(biome, new String[]{"func_76738_d", "canRain"}, MinecraftReflectionCompat.NO_PARAMETERS, false);
        if (MinecraftReflectionCompat.callBoolean(biome, new String[]{"func_76746_c", "getEnableSnow"}, MinecraftReflectionCompat.NO_PARAMETERS, false)
                || MinecraftReflectionCompat.callBoolean(biome, new String[]{"func_150559_j", "isSnowyBiome"}, MinecraftReflectionCompat.NO_PARAMETERS, false)
                || (canRain && MinecraftReflectionCompat.callFloat(biome, new String[]{"func_180626_a", "getTemperature"},
                new Class<?>[]{BlockPos.class}, 0.0F, pos) < 0.15f)) {
            return 2;
        }
        return canRain ? 1 : 0;
    }

    protected int currentBiomeCategory(Minecraft mc) {
        Biome biome = self().currentCameraBiome(mc);
        Object category = biome != null
                ? MinecraftReflectionCompat.invoke(biome, new String[]{"func_150561_m", "getTempCategory"}, MinecraftReflectionCompat.NO_PARAMETERS)
                : null;
        return category instanceof Enum<?> ? ((Enum<?>) category).ordinal() : -1;
    }

    protected float currentBiomeRainfall(Minecraft mc) {
        Biome biome = self().currentCameraBiome(mc);
        return biome != null ? MinecraftReflectionCompat.callFloat(biome, new String[]{"func_76727_i", "getRainfall"}, MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) : 0.0f;
    }

    protected float currentBiomeTemperature(Minecraft mc) {
        Biome biome = self().currentCameraBiome(mc);
        return biome != null ? MinecraftReflectionCompat.callFloat(biome, new String[]{"func_180626_a", "getTemperature"},
                new Class<?>[]{BlockPos.class}, 0.0F, self().currentCameraBlockPos()) : 0.0f;
    }

    protected Biome currentCameraBiome(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (mc == null || world == null) {
            return null;
        }
        return MinecraftReflectionCompat.call(world, Biome.class, null, new String[]{"func_180494_b", "getBiome"},
                new Class<?>[]{BlockPos.class}, self().currentCameraBlockPos());
    }

    protected BlockPos currentCameraBlockPos() {
        return new BlockPos(cameraPositionUnshifted[0], cameraPositionUnshifted[1], cameraPositionUnshifted[2]);
    }

    protected static int irisBiomeId(Biome biome) {
        ResourceLocation name = MinecraftReflectionCompat.call(biome, ResourceLocation.class, null, new String[]{"getRegistryName"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (name == null) {
            return -1;
        }
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        if ("hell".equals(path) || "nether".equals(path) || "nether_wastes".equals(path)) {
            return BIOME_NETHER_WASTES_ID;
        }
        if (path.contains("crimson") && path.contains("forest")) {
            return BIOME_CRIMSON_FOREST_ID;
        }
        if (path.contains("warped") && path.contains("forest")) {
            return BIOME_WARPED_FOREST_ID;
        }
        if (path.contains("basalt") && path.contains("delta")) {
            return BIOME_BASALT_DELTAS_ID;
        }
        if ((path.contains("soul") && path.contains("valley")) || path.contains("soulsand_valley")) {
            return BIOME_SOUL_SAND_VALLEY_ID;
        }
        if (path.contains("pale") && path.contains("garden")) {
            return BIOME_PALE_GARDEN_ID;
        }
        return -1;
    }

    protected static int underwaterSurfaceSkyLight(World world, BlockPos eyePos, int fallbackSky) {
        int maxY = Math.min(MinecraftReflectionCompat.callInt(world, new String[]{"func_72800_K", "getHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, 256), 255);
        int sky = fallbackSky;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(eyePos);
        for (int y = MinecraftReflectionCompat.blockPosY(eyePos); y <= maxY; y++) {
            MinecraftReflectionCompat.mutableBlockPosSet(probe, MinecraftReflectionCompat.blockPosX(eyePos), y, MinecraftReflectionCompat.blockPosZ(eyePos));
            IBlockState state = MinecraftReflectionCompat.worldBlockState(world, probe);
            if (!MinecraftReflectionCompat.stateMaterialIsWater(state)) {
                return Math.max(sky, MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, probe));
            }
            sky = Math.max(sky, MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, probe));
        }
        return sky;
    }

    protected static float blindness(Minecraft mc) {
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity instanceof EntityLivingBase living && MinecraftReflectionCompat.livingPotionActive(living, MinecraftReflectionCompat.field(MobEffects.class, Potion.class, null, "field_76440_q", "BLINDNESS"))) {
            PotionEffect effect = MinecraftReflectionCompat.livingActivePotionEffect(living, MinecraftReflectionCompat.field(MobEffects.class, Potion.class, null, "field_76440_q", "BLINDNESS"));
            if (effect == null) {
                return 1.0f;
            }
            return Math.clamp(MinecraftReflectionCompat.callInt(effect, new String[]{"func_76459_b", "getDuration"}, MinecraftReflectionCompat.NO_PARAMETERS, 0) / 20.0f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    protected static float nightVision(Minecraft mc) {
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity instanceof EntityLivingBase living && MinecraftReflectionCompat.livingPotionActive(living, MinecraftReflectionCompat.field(MobEffects.class, Potion.class, null, "field_76439_r", "NIGHT_VISION"))) {
            PotionEffect effect = MinecraftReflectionCompat.livingActivePotionEffect(living, MinecraftReflectionCompat.field(MobEffects.class, Potion.class, null, "field_76439_r", "NIGHT_VISION"));
            if (effect == null) {
                return 1.0f;
            }
            int duration = MinecraftReflectionCompat.callInt(effect, new String[]{"func_76459_b", "getDuration"}, MinecraftReflectionCompat.NO_PARAMETERS, 0);
            return duration > 200 ? 1.0f : 0.7f + (float) Math.sin((duration - MinecraftReflectionCompat.renderPartialTicks(mc)) * (float) Math.PI * 0.2f) * 0.3f;
        }
        return 0.0f;
    }

    protected int[] smoothedEyeBrightness() {
        return new int[]{
                Math.round(eyeBrightnessSmooth[0]),
                Math.round(eyeBrightnessSmooth[1])
        };
    }

    protected void updateSmoothedEyeBrightness(Minecraft mc) {
        int[] current = PipelineRuntimeState.eyeBrightness(mc);
        if (!eyeBrightnessSmoothInitialized) {
            eyeBrightnessSmooth[0] = current[0];
            eyeBrightnessSmooth[1] = current[1];
            eyeBrightnessSmoothInitialized = true;
            return;
        }

        float smoothingFactor = PipelineRuntimeState.smoothingFactor(eyeBrightnessHalfLife, currentFrameTime);
        eyeBrightnessSmooth[0] += (current[0] - eyeBrightnessSmooth[0]) * smoothingFactor;
        eyeBrightnessSmooth[1] += (current[1] - eyeBrightnessSmooth[1]) * smoothingFactor;
    }

    protected void updateSmoothedWetness(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        float current = world != null ? MinecraftReflectionCompat.worldRainStrength(world, MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f;
        if (!wetnessSmoothInitialized) {
            wetnessSmooth = current;
            wetnessSmoothInitialized = true;
            return;
        }

        float halfLife = current > wetnessSmooth ? wetnessHalfLife : drynessHalfLife;
        wetnessSmooth += (current - wetnessSmooth) * PipelineRuntimeState.smoothingFactor(halfLife, currentFrameTime);
    }

    protected float rainStrength(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        return world != null ? MinecraftReflectionCompat.worldRainStrength(world, MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f;
    }

    protected void updateSmoothedFrameTime() {
        if (!frameTimeSmoothInitialized) {
            frameTimeSmooth = currentFrameTime;
            frameTimeSmoothInitialized = true;
            return;
        }
        frameTimeSmooth += (currentFrameTime - frameTimeSmooth) * PipelineRuntimeState.smoothingFactor(5.0f, currentFrameTime);
    }

    protected static float smoothingFactor(float halfLifeDeciseconds, float frameTimeSeconds) {
        return PipelineFrameValues.smoothingFactor(halfLifeDeciseconds, frameTimeSeconds);
    }

    protected static FloatBuffer irisLightmapTextureMatrix() {
        return PipelineFrameValues.irisLightmapTextureMatrix(IRIS_LIGHTMAP_TEXTURE_MATRIX);
    }

    protected static int[] cameraPositionInt(double[] position) {
        return PipelineFrameValues.cameraPositionInt(position);
    }

    protected static float[] cameraPositionFract(double[] position) {
        return PipelineFrameValues.cameraPositionFract(position);
    }

    protected int currentSelectedBlockId(Minecraft mc) {
        BlockPos pos = PipelineRuntimeState.currentSelectedBlockPosition(mc);
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null || pos == null) {
            return 0;
        }
        return self().blockEntityId(MinecraftReflectionCompat.worldBlockState(world, pos), world, pos);
    }

    protected static float[] currentSelectedBlockPos(Minecraft mc, double[] cameraPosition) {
        BlockPos pos = PipelineRuntimeState.currentSelectedBlockPosition(mc);
        if (pos == null) {
            return new float[]{-256.0f, -256.0f, -256.0f};
        }
        return new float[]{
                (float) (MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraPosition[0]),
                (float) (MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraPosition[1]),
                (float) (MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraPosition[2])
        };
    }

    protected static BlockPos currentSelectedBlockPosition(Minecraft mc) {
        RayTraceResult hit = MinecraftReflectionCompat.field(mc, RayTraceResult.class, null, "field_71476_x", "objectMouseOver");
        if (hit == null || MinecraftReflectionCompat.field(hit, RayTraceResult.Type.class, null, "field_72313_a", "typeOfHit") != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return MinecraftReflectionCompat.rayTraceBlockPos(hit);
    }

    protected static boolean playerSurvivalStatsVisible(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.player(mc) == null || MinecraftReflectionCompat.field(mc, PlayerControllerMP.class, null, "field_71442_b", "playerController") == null) {
            return false;
        }
        GameType gameType = MinecraftReflectionCompat.call(MinecraftReflectionCompat.field(mc, PlayerControllerMP.class, null, "field_71442_b", "playerController"), GameType.class, null, new String[]{"func_178889_l", "getCurrentGameType"}, MinecraftReflectionCompat.NO_PARAMETERS);
        int id = MinecraftReflectionCompat.callInt(gameType, new String[]{"func_77148_a", "getID"}, MinecraftReflectionCompat.NO_PARAMETERS, -1);
        return id == 0 || id == 2;
    }

    protected float currentPlayerHealth(Minecraft mc) {
        if (!PipelineRuntimeState.playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        float maxHealth = Math.max(0.001f, MinecraftReflectionCompat.callFloat(MinecraftReflectionCompat.player(mc), new String[]{"func_110138_aP", "getMaxHealth"}, MinecraftReflectionCompat.NO_PARAMETERS, 0.0F));
        return self().clamp01(MinecraftReflectionCompat.callFloat(MinecraftReflectionCompat.player(mc), new String[]{"func_110143_aJ", "getHealth"}, MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) / maxHealth);
    }

    protected float maxPlayerHealth(Minecraft mc) {
        return PipelineRuntimeState.playerSurvivalStatsVisible(mc) ? MinecraftReflectionCompat.callFloat(MinecraftReflectionCompat.player(mc), new String[]{"func_110138_aP", "getMaxHealth"}, MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) : -1.0f;
    }

    protected float currentPlayerHunger(Minecraft mc) {
        if (!PipelineRuntimeState.playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        Object foodStats = MinecraftReflectionCompat.invoke(MinecraftReflectionCompat.player(mc), new String[]{"func_71024_bL", "getFoodStats"}, MinecraftReflectionCompat.NO_PARAMETERS);
        return self().clamp01(MinecraftReflectionCompat.callInt(foodStats, new String[]{"func_75116_a", "getFoodLevel"}, MinecraftReflectionCompat.NO_PARAMETERS, 0) / 20.0f);
    }

    protected float currentPlayerAir(Minecraft mc) {
        if (!PipelineRuntimeState.playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return self().clamp01(MinecraftReflectionCompat.callInt(MinecraftReflectionCompat.player(mc), new String[]{"func_70086_ai", "getAir"}, MinecraftReflectionCompat.NO_PARAMETERS, 0) / 300.0f);
    }

    protected float maxPlayerAir(Minecraft mc) {
        return PipelineRuntimeState.playerSurvivalStatsVisible(mc) ? 300.0f : -1.0f;
    }

    protected float currentPlayerArmor(Minecraft mc) {
        if (!PipelineRuntimeState.playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return self().clamp01(MinecraftReflectionCompat.callInt(MinecraftReflectionCompat.player(mc), new String[]{"func_70658_aO", "getTotalArmorValue"}, MinecraftReflectionCompat.NO_PARAMETERS, 0) / 50.0f);
    }

    protected static float[] playerLookVector(Minecraft mc) {
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            return new float[]{0.0f, 0.0f, 1.0f};
        }
        Vec3d look = MinecraftReflectionCompat.look(viewEntity, MinecraftReflectionCompat.renderPartialTicks(mc));
        return PipelineRuntimeState.vec3(look);
    }

    protected static float[] upPosition() {
        return MatrixState.transformModelViewDirection(0.0f, 100.0f, 0.0f);
    }

    protected float cameraVelocity() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float y = cameraPosition[1] - previousCameraPosition[1];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    protected float[] taaOffset(Minecraft mc) {
        float[][] offsets = {
                {0.5f, 0.5f},
                {-0.5f, -0.5f},
                {-0.5f, 0.5f},
                {0.5f, -0.5f},
                {0.5f, 0.5f},
                {-0.5f, -0.5f},
                {-0.5f, 0.5f},
                {0.5f, -0.5f},
                {0.5f, 0.5f},
                {-0.5f, -0.5f},
                {-0.5f, 0.5f},
                {0.5f, -0.5f},
                {0.5f, 0.5f},
                {-0.5f, -0.5f},
                {-0.5f, 0.5f},
                {0.5f, -0.5f}
        };
        float[] offset = offsets[(int) (pipelineFrameId & 15L)];
        return new float[]{
                offset[0] / self().worldTargetWidth(mc),
                offset[1] / self().worldTargetHeight(mc)
        };
    }
}
