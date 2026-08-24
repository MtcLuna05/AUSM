package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.ClientSettingsConfig;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.render.ShaderSamplerState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_BASALT_DELTAS_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_CRIMSON_FOREST_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_NETHER_WASTES_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_PALE_GARDEN_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_SOUL_SAND_VALLEY_ID;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.BIOME_WARPED_FOREST_ID;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CHUNK_FADE_DURATION_SECONDS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_CHUNK_FADE;

abstract class PipelineRuntimeUniformState extends PipelineRuntimeValueTypes {
    protected static ICamera createAlwaysVisibleCamera() {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("func_78546_a".equals(name) || "isBoundingBoxInFrustum".equals(name)) {
                return true;
            }
            if ("func_78547_a".equals(name) || "setPosition".equals(name)) {
                return null;
            }
            if ("toString".equals(name)) {
                return "AUSM_ALWAYS_VISIBLE_CAMERA";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name)) {
                return args != null && args.length == 1 && proxy == args[0];
            }
            return null;
        };
        return (ICamera) Proxy.newProxyInstance(
                PipelineRuntimeState.class.getClassLoader(),
                new Class<?>[]{ICamera.class},
                handler
        );
    }

    public static PipelineContext getInstance() {
        return INSTANCE;
    }

    public void beginFramedMaterialCompileCache() {
    }

    public void endFramedMaterialCompileCache() {
    }

    protected void registerBaseUniforms() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();

        // --- 1. Global / Engine Uniforms ---
        uniformRegistry.registerInt("worldTime", () -> {
            World world = PipelineRuntimeState.renderWorld(mc);
            return world != null ? (int) (MinecraftReflectionCompat.worldTime(world) % 24000L) : 0;
        });

        uniformRegistry.registerFloat("viewWidth", () -> (float) self().worldTargetWidth(mc));
        uniformRegistry.registerFloat("viewHeight", () -> (float) self().worldTargetHeight(mc));
        uniformRegistry.registerFloat("pixelSizeX", () -> 1.0f / self().worldTargetWidth(mc));
        uniformRegistry.registerFloat("pixelSizeY", () -> 1.0f / self().worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatio", () -> (float) self().worldTargetWidth(mc) / (float) self().worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatioInverse", () -> (float) self().worldTargetHeight(mc) / (float) self().worldTargetWidth(mc));
        uniformRegistry.registerFloat("screenBrightness", () -> MinecraftReflectionCompat.fieldFloat(MinecraftReflectionCompat.gameSettings(mc), 0.0F, "field_74333_Y", "gammaSetting"));
        uniformRegistry.registerInt("hideGUI", () -> MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)) ? 1 : 0);
        uniformRegistry.registerInt("ausmGuiScreen", () -> self().renderingGuiScreen() ? 1 : 0);
        uniformRegistry.registerInt("isRightHanded", () -> MinecraftReflectionCompat.field(MinecraftReflectionCompat.gameSettings(mc), EnumHandSide.class, EnumHandSide.RIGHT, "field_186715_A", "mainHand") == EnumHandSide.RIGHT ? 1 : 0);
        uniformRegistry.registerInt("firstPersonCamera", () -> MinecraftReflectionCompat.thirdPersonView(MinecraftReflectionCompat.gameSettings(mc)) == 0 ? 1 : 0);
        uniformRegistry.registerInt("currentColorSpace", () -> 0);
        uniformRegistry.registerFloat("near", () -> 0.05f);
        uniformRegistry.registerFloat("far", () -> PipelineRuntimeState.shaderFarPlaneDistance(mc));
        uniformRegistry.registerFloat("fogStart", () -> self().effectiveFogStart(mc));
        uniformRegistry.registerFloat("fogEnd", () -> self().effectiveFogEnd(mc));
        uniformRegistry.registerFloat("fogDensity", () -> self().effectiveFogDensity(mc));
        uniformRegistry.registerFloat("iris_FogStart", () -> self().effectiveFogStart(mc));
        uniformRegistry.registerFloat("iris_FogEnd", () -> self().effectiveFogEnd(mc));
        uniformRegistry.registerFloat("iris_FogDensity", () -> Math.max(0.0f, self().effectiveFogDensity(mc)));
        uniformRegistry.registerInt("fogMode", () -> self().effectiveFogMode(mc));
        uniformRegistry.registerInt("fogShape", () -> 1);
        uniformRegistry.registerFloat("rainStrength", () -> self().rainStrength(mc));
        uniformRegistry.registerFloat("thunderStrength", () -> PipelineRuntimeState.renderWorld(mc) != null ? MinecraftReflectionCompat.worldThunderStrength(PipelineRuntimeState.renderWorld(mc), MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f);
        uniformRegistry.registerFloat("wetness", () -> wetnessSmooth);
        uniformRegistry.registerInt("biome", () -> self().currentBiomeExpressionId(mc));
        uniformRegistry.registerInt("biome_category", () -> self().currentBiomeCategory(mc));
        uniformRegistry.registerInt("biome_precipitation", () -> self().currentBiomePrecipitation(mc));
        uniformRegistry.registerFloat("rainfall", () -> self().currentBiomeRainfall(mc));
        uniformRegistry.registerFloat("temperature", () -> self().currentBiomeTemperature(mc));
        uniformRegistry.registerFloat("BiomeTemp", () -> self().currentBiomeTemperature(mc));
        uniformRegistry.registerInt("BIOME_NETHER_WASTES", () -> BIOME_NETHER_WASTES_ID);
        uniformRegistry.registerInt("BIOME_CRIMSON_FOREST", () -> BIOME_CRIMSON_FOREST_ID);
        uniformRegistry.registerInt("BIOME_WARPED_FOREST", () -> BIOME_WARPED_FOREST_ID);
        uniformRegistry.registerInt("BIOME_BASALT_DELTAS", () -> BIOME_BASALT_DELTAS_ID);
        uniformRegistry.registerInt("BIOME_SOUL_SAND_VALLEY", () -> BIOME_SOUL_SAND_VALLEY_ID);
        uniformRegistry.registerInt("BIOME_PALE_GARDEN", () -> BIOME_PALE_GARDEN_ID);
        uniformRegistry.registerFloat("blindness", () -> PipelineRuntimeState.blindness(mc));
        uniformRegistry.registerFloat("darknessFactor", () -> 0.0f);
        uniformRegistry.registerFloat("darknessLightFactor", () -> 0.0f);
        uniformRegistry.registerInt("heavyFog", () -> PipelineRuntimeState.blindness(mc) > 0.0f ? 1 : 0);
        uniformRegistry.registerFloat("nightVision", () -> PipelineRuntimeState.nightVision(mc));
        uniformRegistry.registerFloat("blindFactor", () -> {
            float value = self().clamp01(PipelineRuntimeState.blindness(mc) * 2.0f - 1.0f);
            return value * value;
        });
        uniformRegistry.registerInt("is_sneaking", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70093_af", "isSneaking"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_sprinting", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70051_ag", "isSprinting"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_hurt", () -> MinecraftReflectionCompat.fieldInt(MinecraftReflectionCompat.player(mc), 0, "field_70737_aN", "hurtTime") > 0 ? 1 : 0);
        uniformRegistry.registerInt("is_invisible", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_82150_aj", "isInvisible"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_burning", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70027_ad", "isBurning"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_on_ground", () -> MinecraftReflectionCompat.fieldBoolean(MinecraftReflectionCompat.player(mc), false, "field_70122_E", "onGround") ? 1 : 0);
        uniformRegistry.registerInt("isRiding", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_184218_aH", "isRiding"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("isElytraFlying", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_184613_cA", "isElytraFlying"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("feetInWater", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70090_H", "isInWater"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("inSwimmingAnimation", () -> 0);
        uniformRegistry.registerInt("vehicleInWater", () -> self().vehicleInWater(mc) ? 1 : 0);
        uniformRegistry.registerInt("vehicleId", () -> self().vehicleId(mc));
        uniformRegistry.registerFloat("sneakSmooth", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70093_af", "isSneaking"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("burningSmooth", () -> MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.player(mc), new String[]{"func_70027_ad", "isBurning"}, MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("touchmybody", () -> MinecraftReflectionCompat.fieldInt(MinecraftReflectionCompat.player(mc), 0, "field_70737_aN", "hurtTime") > 0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("effectStrength", () -> 0.0f);
        uniformRegistry.registerFloat("playerMood", () -> 0.0f);
        uniformRegistry.registerFloat("constantMood", () -> 0.0f);
        uniformRegistry.registerFloat("starter", () -> 1.0f);
        uniformRegistry.registerFloat("eyeAltitude", () -> cameraPosition[1]);
        uniformRegistry.registerFloat("centerDepth", () -> centerDepth);
        uniformRegistry.registerFloat("centerDepthSmooth", () -> centerDepthSmooth);
        uniformRegistry.registerInt("iris_centerDepthSmooth", () -> TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        uniformRegistry.registerInt("moonPhase", () -> PipelineRuntimeState.renderWorld(mc) != null ? MinecraftReflectionCompat.callInt(PipelineRuntimeState.renderWorld(mc), new String[]{"func_72853_d", "getMoonPhase"}, MinecraftReflectionCompat.NO_PARAMETERS, 0) : 0);
        uniformRegistry.registerInt("frameCounter", () -> (int) (pipelineFrameId % 720720L));
        uniformRegistry.registerInt("frameMod", () -> (int) (pipelineFrameId & 15L));
        uniformRegistry.registerFloat("framemod2", () -> (float) (pipelineFrameId & 1L));
        // The generated 1.12.2 patch folders inject this opt-in helper into
        // terrain and water stages. It is harmless for packs that do not use it.
        uniformRegistry.registerInt("ausmLodFallbackEnabled", () -> 1);
        uniformRegistry.registerFloat("ausmLod1RadiusBlocks", () -> shaderedLodRadius(1));
        uniformRegistry.registerFloat("ausmLod2RadiusBlocks", () -> shaderedLodRadius(2));
        uniformRegistry.registerFloat("ausmLod3RadiusBlocks", () -> shaderedLodRadius(3));
        uniformRegistry.registerFloat("ausmLod4RadiusBlocks", () -> shaderedLodRadius(4));
        uniformRegistry.registerVec2("taaOffset", () -> self().taaOffset(mc));
        uniformRegistry.registerInt("worldDay", () -> {
            World world = PipelineRuntimeState.renderWorld(mc);
            return world != null ? (int) (MinecraftReflectionCompat.worldTime(world) / 24000L) : 0;
        });
        uniformRegistry.registerVec2("ausmBotaniaRainbowRotation", () -> self().botaniaRainbowRotation(mc));
        uniformRegistry.registerInt("isSpectator", () -> MinecraftReflectionCompat.playerIsSpectator(MinecraftReflectionCompat.player(mc)) ? 1 : 0);
        uniformRegistry.registerInt("seaLevel", () -> PipelineRuntimeState.renderWorld(mc) != null ? MinecraftReflectionCompat.callInt(PipelineRuntimeState.renderWorld(mc), new String[]{"func_181545_F", "getSeaLevel"}, MinecraftReflectionCompat.NO_PARAMETERS, 63) : 63);
        uniformRegistry.registerInt("bedrockLevel", () -> 0);
        uniformRegistry.registerInt("heightLimit", () -> PipelineRuntimeState.renderWorld(mc) != null ? MinecraftReflectionCompat.callInt(PipelineRuntimeState.renderWorld(mc), new String[]{"func_72800_K", "getHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, 256) : 256);
        uniformRegistry.registerInt("logicalHeightLimit", () -> PipelineRuntimeState.renderWorld(mc) != null ? MinecraftReflectionCompat.callInt(PipelineRuntimeState.renderWorld(mc), new String[]{"func_72940_L", "getActualHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, MinecraftReflectionCompat.callInt(PipelineRuntimeState.renderWorld(mc), new String[]{"func_72800_K", "getHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, 256)) : 256);
        uniformRegistry.registerFloat("cloudHeight", () -> PipelineRuntimeState.cloudHeight(mc));
        uniformRegistry.registerInt("hasCeiling", () -> self().isNetherRenderWorld(mc) ? 1 : 0);
        uniformRegistry.registerInt("hasSkylight", () -> PipelineRuntimeState.hasSkylight(mc) ? 1 : 0);
        uniformRegistry.registerFloat("ambientLight", () -> self().isNetherRenderWorld(mc) ? 0.1f : 0.0f);
        uniformRegistry.registerFloat("isDry", () -> self().currentBiomePrecipitation(mc) == 0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isRainy", () -> self().currentBiomePrecipitation(mc) == 1 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isSnowy", () -> self().currentBiomePrecipitation(mc) == 2 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isPrecipitationRain", () -> self().currentBiomePrecipitation(mc) == 1 && cameraPositionUnshifted[1] < 96.0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isEyeInCave", () -> self().isEyeInCave(mc) ? 1.0f : 0.0f);
        uniformRegistry.registerInt("renderStage", () -> self().getPhase().ordinal());
        uniformRegistry.registerFloat("mc_chunkFade", () -> ENABLE_CHUNK_FADE ? currentChunkFade : 1.0f);
        uniformRegistry.registerVec3("ausmAstralConstellationColor", () -> currentAstralConstellationColor.clone());
        uniformRegistry.registerVec3("ausmAstralTierColor", () -> currentAstralTierColor.clone());
        uniformRegistry.registerFloat("ausmAstralSolarEclipse", () -> currentAstralSolarEclipseFactor);
        uniformRegistry.registerInt("ausmAstralEffectOverlay", () -> astralEffectOverlayActive ? 1 : 0);
        uniformRegistry.registerInt("ausmSkyDetailKind", () -> currentSkyDetailKind);
        uniformRegistry.registerVec2i("ausmSkyDetailTextureSize", PipelineGlState::boundTextureSize);
        uniformRegistry.registerVec4("ausmVoidSkyParams", () -> new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        uniformRegistry.registerInt("ausmSimpleVoidWorld", () -> self().isSimpleVoidWorld(PipelineRuntimeState.renderWorld(mc)) ? 1 : 0);
        uniformRegistry.registerInt("ausmSkyboxRepair", () -> self().shouldRepairCurrentSkybox(mc) ? 1 : 0);
        uniformRegistry.registerInt("ausmUiSkyRepair", () -> self().shouldForceUiSkyboxRepair(mc) ? 1 : 0);
        uniformRegistry.registerFloat("dayMoment", () -> self().dayMoment(mc));
        uniformRegistry.registerFloat("timeAngle", () -> self().dayMoment(mc));
        uniformRegistry.registerFloat("timeBrightness", () -> Math.max((float) Math.sin(self().dayMoment(mc) * Math.PI * 2.0), 0.0f));
        uniformRegistry.registerFloat("moonBrightness", () -> Math.max((float) Math.sin(self().dayMoment(mc) * Math.PI * -2.0), 0.0f));
        uniformRegistry.registerFloat("shadowFade", () -> self().shadowFade(mc, 0.23f, 100.0f));
        uniformRegistry.registerFloat("dayMixer", () -> self().dayMixer(mc));
        uniformRegistry.registerFloat("nightMixer", () -> self().nightMixer(mc));
        uniformRegistry.registerFloat("dayNightMix", () -> self().dayNightMix(mc));
        uniformRegistry.registerFloat("volumetricDayMixer", () -> self().volumetricDayMixer(mc));
        uniformRegistry.registerFloat("day", () -> self().dayHelper(mc));
        uniformRegistry.registerFloat("night", () -> self().nightHelper(mc));
        uniformRegistry.registerFloat("dawnDusk", () -> (1.0f - self().dayHelper(mc)) - self().nightHelper(mc));
        uniformRegistry.registerFloat("shdFade", () -> self().shadowFade(mc, 0.225f, 40.0f));
        uniformRegistry.registerFloat("rainFactor", () -> self().rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthS", () -> self().rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthShiningStars", () -> self().rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthS2", () -> self().rainStrength(mc));
        uniformRegistry.registerInt("entityId", () -> currentEntityId);
        uniformRegistry.registerFloat("alphaTestRef", () -> currentAlphaTestReference);
        uniformRegistry.registerFloat("iris_currentAlphaTest", () -> currentAlphaTestReference);
        uniformRegistry.registerVec4("entityColor", () -> currentEntityColor);
        uniformRegistry.registerInt("heldItemId", () -> self().heldItemId(self().heldMainStack(mc)));
        uniformRegistry.registerInt("heldItemId2", () -> self().heldItemId(self().heldOffhandStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue", () -> self().heldBlockLightValue(self().heldMainStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue2", () -> self().heldBlockLightValue(self().heldOffhandStack(mc)));
        uniformRegistry.registerVec3("heldBlockLightColor", () -> self().heldBlockLightColor(self().heldMainStack(mc)));
        uniformRegistry.registerVec3("heldBlockLightColor2", () -> self().heldBlockLightColor(self().heldOffhandStack(mc)));
        uniformRegistry.registerInt("currentSelectedBlockId", () -> self().currentSelectedBlockId(mc));
        uniformRegistry.registerVec3("currentSelectedBlockPos", () -> PipelineRuntimeState.currentSelectedBlockPos(mc, cameraPositionUnshifted));
        uniformRegistry.registerInt("isEyeInWater", () -> PipelineRuntimeState.eyeFluidState(mc));
        uniformRegistry.registerVec2i("eyeBrightness", () -> PipelineRuntimeState.eyeBrightness(mc));
        uniformRegistry.registerVec2i("eyeBrightnessSmooth", self()::smoothedEyeBrightness);
        uniformRegistry.registerFloat("eyeBrightnessM", () -> PipelineRuntimeState.eyeBrightness(mc)[1] / 240.0f);
        uniformRegistry.registerFloat("currentPlayerHealth", () -> self().currentPlayerHealth(mc));
        uniformRegistry.registerFloat("maxPlayerHealth", () -> self().maxPlayerHealth(mc));
        uniformRegistry.registerFloat("currentPlayerHunger", () -> self().currentPlayerHunger(mc));
        uniformRegistry.registerFloat("maxPlayerHunger", () -> 20.0f);
        uniformRegistry.registerFloat("currentPlayerAir", () -> self().currentPlayerAir(mc));
        uniformRegistry.registerFloat("maxPlayerAir", () -> self().maxPlayerAir(mc));
        uniformRegistry.registerFloat("currentPlayerArmor", () -> self().currentPlayerArmor(mc));
        uniformRegistry.registerFloat("maxPlayerArmor", () -> 50.0f);
        uniformRegistry.registerFloat("pi", () -> (float) Math.PI);
        uniformRegistry.registerInt("anisotropicFiltering", ShaderSamplerState::anisotropicFilteringUniform);
        uniformRegistry.registerInt("blockEntityId", () -> dynamicBlockEntityId);
        uniformRegistry.registerInt("currentRenderedItemId", () -> currentRenderedItemId);

        // --- 2. Matrix Uniforms ---
        uniformRegistry.registerMatrix4("gbufferModelView", MatrixState::modelView);
        uniformRegistry.registerMatrix4("modelViewMatrix", MatrixState::modelView);
        uniformRegistry.registerMatrix4("iris_ModelViewMatrix", MatrixState::modelView);
        uniformRegistry.registerMatrix4("iris_ModelViewMat", MatrixState::modelView);
        uniformRegistry.registerMatrix4("gbufferModelViewInverse", MatrixState::modelViewInverse);
        uniformRegistry.registerMatrix4("modelViewMatrixInverse", MatrixState::modelViewInverse);
        uniformRegistry.registerMatrix4("iris_ModelViewMatrixInverse", MatrixState::modelViewInverse);
        uniformRegistry.registerMatrix4("iris_ModelViewMatInverse", MatrixState::modelViewInverse);
        uniformRegistry.registerMatrix4("gbufferPreviousModelView", MatrixState::previousModelView);
        uniformRegistry.registerMatrix4("gbufferProjection", MatrixState::projection);
        uniformRegistry.registerMatrix4("projectionMatrix", MatrixState::projection);
        uniformRegistry.registerMatrix4("iris_ProjectionMatrix", MatrixState::projection);
        uniformRegistry.registerMatrix4("iris_ProjMat", MatrixState::projection);
        uniformRegistry.registerMatrix4("u_ModelViewProjectionMatrix", MatrixState::modelViewProjection);
        uniformRegistry.registerMatrix4("gbufferProjectionInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("projectionMatrixInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("iris_ProjectionMatrixInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("iris_ProjMatInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("gbufferPreviousProjection", MatrixState::previousProjection);
        uniformRegistry.registerMatrix4("dhProjection", distantHorizonsMatrices::projection);
        uniformRegistry.registerMatrix4("dhProjectionInverse", distantHorizonsMatrices::projectionInverse);
        uniformRegistry.registerMatrix4("dhPreviousProjection", distantHorizonsMatrices::projection);
        uniformRegistry.registerMatrix4("dhModelView", distantHorizonsMatrices::modelView);
        uniformRegistry.registerMatrix4("dhModelViewProjection", distantHorizonsMatrices::modelViewProjection);
        uniformRegistry.registerVec3("dhModelOffset", distantHorizonsMatrices::modelOffset);
        uniformRegistry.registerInt("dhMaterialId", () -> 0);
        uniformRegistry.registerInt("dhRenderDistance", () -> mc != null && MinecraftReflectionCompat.gameSettings(mc) != null ? MinecraftReflectionCompat.renderDistanceChunks(mc) * 16 : 0);
        uniformRegistry.registerFloat("fovYInverse", PipelineGlState::fovYInverse);
        uniformRegistry.registerMatrix4("textureMatrix", MatrixState::identity);
        uniformRegistry.registerMatrix4("iris_TextureMat", MatrixState::identity);
        uniformRegistry.registerMatrix3("iris_NormalMat", MatrixState::normalMatrix);
        uniformRegistry.registerMatrix3("iris_NormalMatrix", MatrixState::normalMatrix);
        uniformRegistry.registerMatrix3("normalMatrix", MatrixState::normalMatrix);
        uniformRegistry.registerMatrix3("gl_NormalMatrix", MatrixState::normalMatrix);
        uniformRegistry.registerMatrix4("shadowModelView", MatrixState::shadowModelView);
        uniformRegistry.registerMatrix4("shadowModelViewInverse", MatrixState::shadowModelViewInverse);
        uniformRegistry.registerMatrix4("shadowProjection", MatrixState::shadowProjection);
        uniformRegistry.registerMatrix4("shadowProjectionInverse", MatrixState::shadowProjectionInverse);
        uniformRegistry.registerMatrix4("iris_LightmapTextureMatrix", PipelineContext::irisLightmapTextureMatrix);

        // =========================================================
        // --- OPTIFINE STANDARD TEXTURE UNIT MAPPINGS ---
        // =========================================================

        // --- 3. G-Buffer Pass Inputs (Terrain / Entities) ---
        // These expect the game to have bound the Minecraft Atlas/Lightmap to these units
        uniformRegistry.registerInt("gtexture", () -> 0); // GL_TEXTURE0 (Block Atlas)
        uniformRegistry.registerInt("texture", () -> 0);
        uniformRegistry.registerInt("tex", () -> 0);
        uniformRegistry.registerInt("u_MainSampler", () -> 0);
        uniformRegistry.registerInt("lightmap", () -> 2); // Iris reserves GL_TEXTURE2 for the shader lightmap sampler.
        uniformRegistry.registerInt("iris_overlay", () -> 1);
        uniformRegistry.registerInt("normals", () -> 3);
        uniformRegistry.registerInt("specular", () -> TextureBinder.SPECULAR_TEXTURE_UNIT);
        uniformRegistry.registerInt("gtextureId", PipelineGlState::boundTexture2d);
        uniformRegistry.registerInt("textureReloadCount", () -> textureReloadCount);
        uniformRegistry.registerInt("textureFilteringMode", ShaderSamplerState::textureFilteringModeUniform);
        uniformRegistry.registerVec2i("atlasSize", PipelineGlState::boundTextureSize);
        uniformRegistry.registerVec2i("gtextureSize", PipelineGlState::boundTextureSize);
        uniformRegistry.registerVec4i("blendFunc", PipelineGlState::blendFunc);
        uniformRegistry.registerVec2("iris_ScreenSize", () -> new float[]{(float) self().worldTargetWidth(mc), (float) self().worldTargetHeight(mc)});
        uniformRegistry.registerVec3("iris_CameraTranslation", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("iris_ModelOffset", distantHorizonsMatrices::modelOffset);
        uniformRegistry.registerVec4("iris_ColorModulator", () -> new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        uniformRegistry.registerFloat("iris_ModelScale", () -> 1.0f);
        uniformRegistry.registerFloat("iris_TextureScale", () -> 1.0f);
        uniformRegistry.registerFloat("iris_GlintAlpha", () -> 1.0f);
        uniformRegistry.registerFloat("ausmItemAlphaTestRef", () -> currentAlphaTestReference);
        uniformRegistry.registerInt("ausmItemGlintBaseAtlas", () -> TextureBinder.ITEM_GLINT_BASE_ATLAS_TEXTURE_UNIT);
        uniformRegistry.registerInt("ausmItemGlintMask", () -> itemGlintMaskDepth > 0 ? 1 : 0);
        uniformRegistry.registerVec3("u_ModelScale", () -> new float[]{1.0f, 1.0f, 1.0f});
        uniformRegistry.registerVec2("u_TextureScale", () -> new float[]{1.0f, 1.0f});
        uniformRegistry.registerVec3("u_RegionOffset", () -> new float[]{0.0f, 0.0f, 0.0f});

        // --- 4. Legacy Screen Samplers (Deferred / Composite Passes) ---
        // These read from your FBO attachments
        uniformRegistry.registerInt("gcolor", () -> 0);
        uniformRegistry.registerInt("gdepth", () -> 1);
        uniformRegistry.registerInt("gnormal", () -> 2);
        uniformRegistry.registerInt("composite", () -> 3);
        uniformRegistry.registerInt("gdepthtex", () -> TextureBinder.DEPTHTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("depthtex0", () -> TextureBinder.DEPTHTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("depthtex1", () -> TextureBinder.DEPTHTEX1_TEXTURE_UNIT);
        uniformRegistry.registerInt("depthtex2", () -> TextureBinder.DEPTHTEX2_TEXTURE_UNIT);
        uniformRegistry.registerInt("gaux1", () -> TextureBinder.COLORTEX4_TEXTURE_UNIT);
        uniformRegistry.registerInt("gaux2", () -> TextureBinder.COLORTEX5_TEXTURE_UNIT);
        uniformRegistry.registerInt("gaux3", () -> TextureBinder.COLORTEX6_TEXTURE_UNIT);
        uniformRegistry.registerInt("gaux4", () -> TextureBinder.COLORTEX7_TEXTURE_UNIT);

        // --- 5. Modern Screen Samplers (OptiFine colortexN) ---
        // Modern OptiFine packs use colortex0-7 instead of gcolor/gnormal/gaux
        uniformRegistry.registerInt("colortex0", () -> 0);
        uniformRegistry.registerInt("colortex1", () -> 1);
        uniformRegistry.registerInt("colortex2", () -> 2);
        uniformRegistry.registerInt("colortex3", () -> 3);
        uniformRegistry.registerInt("colortex4", () -> TextureBinder.COLORTEX4_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex5", () -> TextureBinder.COLORTEX5_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex6", () -> TextureBinder.COLORTEX6_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex7", () -> TextureBinder.COLORTEX7_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex8", () -> TextureBinder.COLORTEX8_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex9", () -> TextureBinder.COLORTEX9_TEXTURE_UNIT);
        uniformRegistry.registerInt("colortex16", () -> TextureBinder.COLORTEX16_TEXTURE_UNIT);
        self().registerAttachmentSizeUniforms();

        uniformRegistry.registerInt("shadow", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("watershadow", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex0", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex0HW", () -> TextureBinder.textureUnitForSampler("shadowtex0HW"));
        uniformRegistry.registerInt("shadowtex1", () -> TextureBinder.SHADOWTEX1_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex1HW", () -> TextureBinder.textureUnitForSampler("shadowtex1HW"));
        uniformRegistry.registerInt("shadowcolor", () -> TextureBinder.SHADOWCOLOR0_TEXTURE_UNIT);
        for (int i = 0; i < ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT; i++) {
            int shadowColorIndex = i;
            uniformRegistry.registerInt("shadowcolor" + shadowColorIndex, () -> TextureBinder.shadowColorTextureUnit(shadowColorIndex));
        }
        uniformRegistry.registerInt("shadowMapResolution", self()::shadowResolution);
        uniformRegistry.registerVec2i("shadowtex0Size", () -> self().shadowSize());
        uniformRegistry.registerVec2i("shadowtex1Size", () -> self().shadowSize());
        uniformRegistry.registerVec2i("shadowSize", () -> self().shadowSize());
        for (int i = 0; i < ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT; i++) {
            int shadowColorIndex = i;
            uniformRegistry.registerVec2i("shadowcolor" + shadowColorIndex + "Size", () -> self().shadowSize());
        }
        uniformRegistry.registerInt("dhDepthTex", () -> TextureBinder.DEPTHTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("dhDepthTex0", () -> TextureBinder.DEPTHTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("dhDepthTex1", () -> TextureBinder.DEPTHTEX1_TEXTURE_UNIT);
        uniformRegistry.registerInt("dhDepthTex2", () -> TextureBinder.DEPTHTEX2_TEXTURE_UNIT);
        uniformRegistry.registerInt("noisetex", () -> TextureBinder.NOISETEX_TEXTURE_UNIT);

        // Iris wraps frameTimeCounter hourly to avoid large float precision loss in pack animations.
        uniformRegistry.registerFloat("frameTimeCounter", () -> frameTimeCounter);
        uniformRegistry.registerFloat("frameTime", () -> currentFrameTime);
        uniformRegistry.registerFloat("lastFrameTime", () -> currentFrameTime);
        uniformRegistry.registerFloat("frameTimeSmooth", () -> frameTimeSmooth);
        uniformRegistry.registerFloat("cloudTime", () -> PipelineRuntimeState.cloudTime(mc));
        uniformRegistry.registerFloat("chunkFadeTimeInv", () -> 1.0f / CHUNK_FADE_DURATION_SECONDS);
        uniformRegistry.registerVec3i("currentDate", PipelineContext::currentDate);
        uniformRegistry.registerVec3i("currentTime", PipelineContext::currentTime);
        uniformRegistry.registerVec2i("currentYearTime", PipelineContext::currentYearTime);

        uniformRegistry.registerVec3("cameraPosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("previousCameraPosition", () -> previousCameraPosition.clone());
        uniformRegistry.registerVec3i("cameraPositionInt", () -> PipelineRuntimeState.cameraPositionInt(cameraPositionUnshifted));
        uniformRegistry.registerVec3("cameraPositionFract", () -> PipelineRuntimeState.cameraPositionFract(cameraPositionUnshifted));
        uniformRegistry.registerVec3i("previousCameraPositionInt", () -> PipelineRuntimeState.cameraPositionInt(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("previousCameraPositionFract", () -> PipelineRuntimeState.cameraPositionFract(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("eyePosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("relativeEyePosition", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("playerLookVector", () -> PipelineRuntimeState.playerLookVector(mc));
        uniformRegistry.registerVec3("playerBodyVector", () -> PipelineRuntimeState.bodyVector(mc != null ? MinecraftReflectionCompat.renderViewEntity(mc) : null));
        uniformRegistry.registerVec3("vehicleLookVector", () -> self().vehicleLookVector(mc));
        uniformRegistry.registerVec3("relativeVehiclePosition", () -> self().relativeVehiclePosition(mc));
        uniformRegistry.registerVec4("lightningBoltPosition", () -> self().lightningBoltPosition(mc));
        uniformRegistry.registerFloat("velocity", self()::cameraVelocity);

        uniformRegistry.registerVec3("upPosition", PipelineContext::upPosition);
        uniformRegistry.registerVec3("skyColor", () -> self().shaderSkyColor(mc));
        uniformRegistry.registerVec3("fogColor", () -> self().effectiveFogColor(mc));
        uniformRegistry.registerVec4("iris_FogColor", () -> {
            float[] color = self().effectiveFogColor(mc);
            return new float[]{color[0], color[1], color[2], 1.0f};
        });

        // --- Sun & Moon Position ---
        uniformRegistry.registerFloat("sunAngle", () -> self().sunAngle(mc));
        uniformRegistry.registerFloat("shadowAngle", () -> self().shadowAngle(mc));
        uniformRegistry.registerVec3("endFlashPosition", () -> endFlashPosition.clone());
        uniformRegistry.registerFloat("endFlashIntensity", () -> endFlashIntensity);
        uniformRegistry.registerFloat("previousEndFlashIntensity", () -> previousEndFlashIntensity);
        uniformRegistry.registerVec3("sunPosition", () -> {
            if (PipelineRuntimeState.renderWorld(mc) != null) {
                return self().shaderLightPosition(mc, false);
            }
            return new float[]{0, 100, 0};
        });
        uniformRegistry.registerVec3("moonPosition", () -> {
            if (PipelineRuntimeState.renderWorld(mc) != null) {
                return self().shaderLightPosition(mc, true);
            }
            return new float[]{0, -100, 0};
        });
        uniformRegistry.registerVec3("shadowLightPosition", () -> {
            World world = PipelineRuntimeState.renderWorld(mc);
            if (world != null) {
                if (self().useEndFlashShadowLight(world)) {
                    return endFlashPosition.clone();
                }
                float celestialAngle = MinecraftReflectionCompat.worldCelestialAngle(world, MinecraftReflectionCompat.renderPartialTicks(mc));
                float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
                return self().legacyShadowLightVector(mc, sunAngle > 0.5F);
            }
            return new float[]{0.0f, 100.0f, 0.0f};
        });
        // --- TAA / History Matrices ---
            /*uniformRegistry.registerMatrix4("gbufferPreviousModelView", MatrixState::getPreviousModelViewMatrix);
            uniformRegistry.registerMatrix4("gbufferPreviousProjection", MatrixState::getPreviousProjectionMatrix);*/
    }

    private static float shaderedLodRadius(int level) {
        ClientSettingsConfig settings = MainMod.getClientSettingsConfig();
        if (settings == null) {
            return switch (level) {
                case 1 -> 96.0F;
                case 2 -> 144.0F;
                case 3 -> 192.0F;
                default -> 240.0F;
            };
        }
        return switch (level) {
            case 1 -> settings.shaderedLod1RadiusBlocks();
            case 2 -> settings.shaderedLod2RadiusBlocks();
            case 3 -> settings.shaderedLod3RadiusBlocks();
            default -> settings.shaderedLod4RadiusBlocks();
        };
    }

    protected void registerAttachmentSizeUniforms() {
        for (Attachment attachment : Attachment.values()) {
            int index = attachment.getIndex();
            uniformRegistry.registerVec2i("colortex" + index + "Size", () -> self().attachmentSize(attachment));
        }
        uniformRegistry.registerVec2i("gcolorSize", () -> self().attachmentSize(Attachment.COLOR));
        uniformRegistry.registerVec2i("gdepthSize", () -> self().attachmentSize(Attachment.DEPTH));
        uniformRegistry.registerVec2i("gnormalSize", () -> self().attachmentSize(Attachment.NORMAL));
        uniformRegistry.registerVec2i("compositeSize", () -> self().attachmentSize(Attachment.COMPOSITE));
        uniformRegistry.registerVec2i("gaux1Size", () -> self().attachmentSize(Attachment.AUX1));
        uniformRegistry.registerVec2i("gaux2Size", () -> self().attachmentSize(Attachment.AUX2));
        uniformRegistry.registerVec2i("gaux3Size", () -> self().attachmentSize(Attachment.AUX3));
        uniformRegistry.registerVec2i("gaux4Size", () -> self().attachmentSize(Attachment.AUX4));
        uniformRegistry.registerVec2i("depthtex0Size", () -> self().framebufferSize());
        uniformRegistry.registerVec2i("depthtex1Size", () -> self().framebufferSize());
        uniformRegistry.registerVec2i("depthtex2Size", () -> self().framebufferSize());
    }

    protected Framebuffer currentWorldFramebufferTarget(Minecraft mc) {
        return externalWorldFramebufferTarget != null ? externalWorldFramebufferTarget : mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
    }

    protected static World renderWorld(Minecraft mc) {
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }
        return mc != null ? MinecraftReflectionCompat.world(mc) : null;
    }

    protected static int[] currentDate() {
        return PipelineFrameValues.currentDate();
    }

    protected static int[] currentTime() {
        return PipelineFrameValues.currentTime();
    }

    protected static int[] currentYearTime() {
        return PipelineFrameValues.currentYearTime();
    }

    protected boolean isExternalWorldFramebufferTarget(Framebuffer target) {
        return externalWorldFramebufferTarget != null && target == externalWorldFramebufferTarget;
    }

    protected boolean isBetterPortalsExternalWorldTarget() {
        return externalWorldFramebufferTarget != null && self().isRenderingBetterPortalsNestedView();
    }

    protected int worldTargetWidth(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, MinecraftReflectionCompat.framebufferWidth(target)) : Math.max(1, MinecraftReflectionCompat.displayWidth(mc));
    }

    protected int worldTargetHeight(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, MinecraftReflectionCompat.framebufferHeight(target)) : Math.max(1, MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int framebufferWidth(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, MinecraftReflectionCompat.framebufferWidth(target)) : Math.max(1, MinecraftReflectionCompat.displayWidth(mc));
    }

    protected int framebufferHeight(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, MinecraftReflectionCompat.framebufferHeight(target)) : Math.max(1, MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int[] attachmentSize(Attachment attachment) {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            return new int[]{self().worldTargetWidth(mc), self().worldTargetHeight(mc)};
        }
        return new int[]{
                Math.max(1, pingPongManager.attachmentWidth(attachment)),
                Math.max(1, pingPongManager.attachmentHeight(attachment))
        };
    }

    protected int[] framebufferSize() {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            return new int[]{self().worldTargetWidth(mc), self().worldTargetHeight(mc)};
        }
        return new int[]{Math.max(1, pingPongManager.width()), Math.max(1, pingPongManager.height())};
    }

    protected int shadowResolution() {
        return shadowFramebuffer != null ? Math.max(1, shadowFramebuffer.resolution()) : 1;
    }

    protected int[] shadowSize() {
        int resolution = self().shadowResolution();
        return new int[]{resolution, resolution};
    }

    protected static int[] eyeBrightness(Minecraft mc) {
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null || viewEntity == null) {
            return new int[]{0, 0};
        }

        BlockPos pos = new BlockPos(MinecraftReflectionCompat.posX(viewEntity), MinecraftReflectionCompat.posY(viewEntity) + MinecraftReflectionCompat.eyeHeight(viewEntity), MinecraftReflectionCompat.posZ(viewEntity));
        int combinedLight = MinecraftReflectionCompat.callInt(world, new String[]{"func_175626_b", "getCombinedLight"},
                new Class<?>[]{BlockPos.class, int.class}, 0, pos, 0);
        int block = combinedLight >> 4 & 0xF;
        int sky = combinedLight >> 20 & 0xF;
        if (PipelineRuntimeState.eyeFluidState(mc) == 1) {
            sky = PipelineRuntimeState.underwaterSurfaceSkyLight(world, pos, sky);
        }
        return new int[]{block * 16, sky * 16};
    }

    protected static float[] skyColor(Minecraft mc) {
        Entity viewEntity = mc == null ? null : MinecraftReflectionCompat.renderViewEntity(mc);
        World world = PipelineRuntimeState.renderWorld(mc);
        if (mc != null && world != null && viewEntity != null) {
            return PipelineRuntimeState.vec3(MinecraftReflectionCompat.call(world, Vec3d.class, null, new String[]{"func_72833_a", "getSkyColor"},
                    new Class<?>[]{Entity.class, float.class}, viewEntity, MinecraftReflectionCompat.renderPartialTicks(mc)));
        }
        return new float[]{0.5f, 0.7f, 1.0f};
    }
}
