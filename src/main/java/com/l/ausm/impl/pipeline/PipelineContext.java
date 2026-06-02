package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.fbo.PingPongManager;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.resource.ShaderImageSet;
import com.l.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.impl.pipeline.render.FullscreenQuad;
import com.l.ausm.impl.pipeline.render.IrisLightmapTexture;
import com.l.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.ComputeProgram;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import com.l.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.api.pipeline.shader.FogMode;
import com.l.ausm.api.pipeline.shader.LightingModel;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.pipeline.shader.ShaderCompiler;
import com.l.ausm.impl.pipeline.shader.ShaderKey;
import com.l.ausm.impl.pipeline.shader.ShaderLoadingMap;
import com.l.ausm.impl.pipeline.shader.ShaderMap;
import com.l.ausm.impl.pipeline.shader.ShaderProgramSet;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.pipeline.shader.UniformRegistry;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GLContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The central hub for the active render pipeline.
 * Replaces the monolithic Shaders class with a cleaner context object.
 */
public class PipelineContext {

    private static final PipelineContext INSTANCE = new PipelineContext();
    private static final FloatBuffer IRIS_LIGHTMAP_TEXTURE_MATRIX = createIrisLightmapTextureMatrix();
    private static final Pattern CONST_SETTING_PATTERN = Pattern.compile("^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+).*$");
    private static final Pattern DEFINE_SETTING_PATTERN = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+([^/\\s]+))?.*$");

    private final PingPongManager pingPongManager = new PingPongManager();
    private final IrisLightmapTexture irisLightmapTexture = new IrisLightmapTexture();
    private final Map<RenderPass, PipelineProgram> programs = new EnumMap<>(RenderPass.class);
    private final Map<RenderPass, List<LoadedCustomTexture>> customTextures = new EnumMap<>(RenderPass.class);
    private final UniformRegistry uniformRegistry = new UniformRegistry();
    private ShadowFramebuffer shadowFramebuffer;
    private ShaderProperties shaderProperties = emptyShaderProperties();
    private ShaderPackDirectives packDirectives = emptyShaderProperties().packDirectives();
    private ShaderProgramSet programSet;
    private ShaderMap shaderMap;
    private ShaderImageSet shaderImages = ShaderImageSet.empty();
    private ShaderStorageBufferSet shaderStorageBuffers = ShaderStorageBufferSet.empty();
    private final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays = new EnumMap<>(ProgramArrayId.class);
    private List<ComputeProgram> shadowComputePrograms = List.of();
    private List<ComputeProgram> finalComputePrograms = List.of();
    private final Map<ProgramArrayId, FullscreenProgramArray> fullscreenProgramArrays = new EnumMap<>(ProgramArrayId.class);

    private final Deque<PassScope> passStack = new ArrayDeque<>();
    private RenderPass activePass = null;
    private ShaderKey activeShaderKey = null;
    private WorldRenderingPhase activePhase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase = null;
    private boolean isPipelineActive = false;
    private String activePackName = "(internal)";
    private float centerDepth = 1.0f;
    private float centerDepthSmooth = 1.0f;
    private int centerDepthSmoothTexture = -1;
    private int noiseTexture = -1;
    private final FloatBuffer centerDepthTextureBuffer = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private int currentEntityId = 0;
    private float[] currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private float currentAlphaTestReference = 0.1f;
    private float shadowMapDistance = 128.0f;
    private float shadowDistanceRenderMul = -1.0f;
    private float shadowIntervalSize = 2.0f;
    private float sunPathRotation = 0.0f;
    private float centerDepthHalfLife = 1.0f;
    private float eyeBrightnessHalfLife = 3.0f;
    private float wetnessHalfLife = 600.0f;
    private float drynessHalfLife = 200.0f;
    private final float[] eyeBrightnessSmooth = new float[]{0.0f, 0.0f};
    private boolean eyeBrightnessSmoothInitialized = false;
    private float wetnessSmooth = 0.0f;
    private boolean wetnessSmoothInitialized = false;
    private boolean shadowPolygonOffset = true;
    private float shadowPolygonOffsetFactor = 1.1f;
    private float shadowPolygonOffsetUnits = 4.0f;
    private int shadowFrameCount = 1_000_000;
    private long lastShadowFrameId = -1L;
    private long pipelineFrameId = 0L;
    private final long pipelineStartNanos = System.nanoTime();
    private long lastPipelineFrameNanos = pipelineStartNanos;
    private float currentFrameTime = 0.016f;
    private float frameTimeCounter = 0.0f;
    private float frameTimeSmooth = 0.016f;
    private boolean frameTimeSmoothInitialized = false;
    private final float[] cameraPosition = {0.0f, 0.0f, 0.0f};
    private final float[] previousCameraPosition = {0.0f, 0.0f, 0.0f};
    private final double[] cameraPositionUnshifted = {0.0, 0.0, 0.0};
    private final double[] previousCameraPositionUnshifted = {0.0, 0.0, 0.0};
    private double cameraShiftX = 0.0;
    private double cameraShiftZ = 0.0;
    private boolean deferredPassesRenderedThisFrame = false;
    private boolean preTranslucentDepthCopiedThisFrame = false;
    private boolean preHandDepthCopiedThisFrame = false;
    private boolean terrainCullOverrideActive = false;
    private boolean previousTerrainCullEnabled = true;
    private boolean worldFrameActive = false;
    private boolean renderingShadowMap = false;
    private boolean renderingDeferredIngameHud = false;
    private boolean renderingGui = false;
    private boolean shadowMapPopulated = false;
    private int guiRenderDepth = 0;
    private final IntBuffer viewportBuffer = org.lwjgl.BufferUtils.createIntBuffer(16);

    private PipelineContext() {
        registerBaseUniforms();
    }

    private record PassScope(boolean bound, RenderPass previousPass, ShaderKey previousShaderKey, WorldRenderingPhase previousPhase) {
    }

    public static PipelineContext getInstance() {
        return INSTANCE;
    }

    private void registerBaseUniforms() {
        Minecraft mc = Minecraft.getMinecraft();

        // --- 1. Global / Engine Uniforms ---
        uniformRegistry.registerInt("worldTime", () -> {
            if (mc.world != null) {
                return (int) (mc.world.getWorldTime() % 24000L);
            }
            return 0;
        });

        uniformRegistry.registerFloat("viewWidth", () -> (float) mc.displayWidth);
        uniformRegistry.registerFloat("viewHeight", () -> (float) mc.displayHeight);
        uniformRegistry.registerFloat("pixelSizeX", () -> 1.0f / Math.max(1, mc.displayWidth));
        uniformRegistry.registerFloat("pixelSizeY", () -> 1.0f / Math.max(1, mc.displayHeight));
        uniformRegistry.registerFloat("aspectRatio", () -> (float) mc.displayWidth / (float) mc.displayHeight);
        uniformRegistry.registerFloat("aspectRatioInverse", () -> (float) mc.displayHeight / (float) mc.displayWidth);
        uniformRegistry.registerFloat("screenBrightness", () -> mc.gameSettings.gammaSetting);
        uniformRegistry.registerInt("hideGUI", () -> mc.gameSettings.hideGUI ? 1 : 0);
        uniformRegistry.registerInt("isRightHanded", () -> mc.gameSettings.mainHand == EnumHandSide.RIGHT ? 1 : 0);
        uniformRegistry.registerInt("firstPersonCamera", () -> mc.gameSettings.thirdPersonView == 0 ? 1 : 0);
        uniformRegistry.registerFloat("near", () -> 0.05f);
        uniformRegistry.registerFloat("far", () -> (float) Math.max(16, mc.gameSettings.renderDistanceChunks * 16));
        uniformRegistry.registerFloat("fogStart", () -> GL11.glGetFloat(GL11.GL_FOG_START));
        uniformRegistry.registerFloat("fogEnd", () -> GL11.glGetFloat(GL11.GL_FOG_END));
        uniformRegistry.registerFloat("fogDensity", () -> GL11.glGetFloat(GL11.GL_FOG_DENSITY));
        uniformRegistry.registerFloat("iris_FogStart", () -> GL11.glGetFloat(GL11.GL_FOG_START));
        uniformRegistry.registerFloat("iris_FogEnd", () -> GL11.glGetFloat(GL11.GL_FOG_END));
        uniformRegistry.registerFloat("iris_FogDensity", () -> Math.max(0.0f, GL11.glGetFloat(GL11.GL_FOG_DENSITY)));
        uniformRegistry.registerInt("fogMode", () -> switch (GL11.glGetInteger(GL11.GL_FOG_MODE)) {
            case GL11.GL_LINEAR -> 0;
            case GL11.GL_EXP -> 1;
            case GL11.GL_EXP2 -> 2;
            default -> -1;
        });
        uniformRegistry.registerInt("fogShape", () -> 1);
        uniformRegistry.registerFloat("rainStrength", () -> mc.world != null ? mc.world.getRainStrength(mc.getRenderPartialTicks()) : 0.0f);
        uniformRegistry.registerFloat("thunderStrength", () -> mc.world != null ? mc.world.getThunderStrength(mc.getRenderPartialTicks()) : 0.0f);
        uniformRegistry.registerFloat("wetness", () -> wetnessSmooth);
        uniformRegistry.registerFloat("blindness", () -> blindness(mc));
        uniformRegistry.registerFloat("darknessFactor", () -> 0.0f);
        uniformRegistry.registerFloat("darknessLightFactor", () -> 0.0f);
        uniformRegistry.registerFloat("nightVision", () -> nightVision(mc));
        uniformRegistry.registerFloat("blindFactor", () -> {
            float value = clamp01(blindness(mc) * 2.0f - 1.0f);
            return value * value;
        });
        uniformRegistry.registerInt("is_sneaking", () -> mc.player != null && mc.player.isSneaking() ? 1 : 0);
        uniformRegistry.registerInt("is_sprinting", () -> mc.player != null && mc.player.isSprinting() ? 1 : 0);
        uniformRegistry.registerInt("is_hurt", () -> mc.player != null && mc.player.hurtTime > 0 ? 1 : 0);
        uniformRegistry.registerInt("is_invisible", () -> mc.player != null && mc.player.isInvisible() ? 1 : 0);
        uniformRegistry.registerInt("is_burning", () -> mc.player != null && mc.player.isBurning() ? 1 : 0);
        uniformRegistry.registerInt("is_on_ground", () -> mc.player != null && mc.player.onGround ? 1 : 0);
        uniformRegistry.registerFloat("playerMood", () -> 0.0f);
        uniformRegistry.registerFloat("constantMood", () -> 0.0f);
        uniformRegistry.registerFloat("eyeAltitude", () -> cameraPosition[1]);
        uniformRegistry.registerFloat("centerDepth", () -> centerDepth);
        uniformRegistry.registerFloat("centerDepthSmooth", () -> centerDepthSmooth);
        uniformRegistry.registerInt("iris_centerDepthSmooth", () -> TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        uniformRegistry.registerInt("moonPhase", () -> mc.world != null ? mc.world.getMoonPhase() : 0);
        uniformRegistry.registerInt("frameCounter", () -> (int) (pipelineFrameId % 720720L));
        uniformRegistry.registerInt("frameMod", () -> (int) (pipelineFrameId & 15L));
        uniformRegistry.registerFloat("framemod2", () -> (float) (pipelineFrameId & 1L));
        uniformRegistry.registerVec2("taaOffset", () -> taaOffset(mc));
        uniformRegistry.registerInt("worldDay", () -> mc.world != null ? (int) (mc.world.getWorldTime() / 24000L) : 0);
        uniformRegistry.registerInt("isSpectator", () -> mc.player != null && mc.player.isSpectator() ? 1 : 0);
        uniformRegistry.registerInt("seaLevel", () -> mc.world != null ? mc.world.getSeaLevel() : 63);
        uniformRegistry.registerInt("renderStage", () -> getPhase().ordinal());
        uniformRegistry.registerFloat("dayMoment", () -> dayMoment(mc));
        uniformRegistry.registerFloat("timeAngle", () -> dayMoment(mc));
        uniformRegistry.registerFloat("timeBrightness", () -> Math.max((float) Math.sin(dayMoment(mc) * Math.PI * 2.0), 0.0f));
        uniformRegistry.registerFloat("moonBrightness", () -> Math.max((float) Math.sin(dayMoment(mc) * Math.PI * -2.0), 0.0f));
        uniformRegistry.registerFloat("shadowFade", () -> shadowFade(mc, 0.23f, 100.0f));
        uniformRegistry.registerFloat("dayMixer", () -> dayMixer(mc));
        uniformRegistry.registerFloat("nightMixer", () -> nightMixer(mc));
        uniformRegistry.registerFloat("dayNightMix", () -> dayNightMix(mc));
        uniformRegistry.registerFloat("volumetricDayMixer", () -> volumetricDayMixer(mc));
        uniformRegistry.registerFloat("day", () -> dayHelper(mc));
        uniformRegistry.registerFloat("night", () -> nightHelper(mc));
        uniformRegistry.registerFloat("dawnDusk", () -> (1.0f - dayHelper(mc)) - nightHelper(mc));
        uniformRegistry.registerFloat("shdFade", () -> shadowFade(mc, 0.225f, 40.0f));
        uniformRegistry.registerFloat("rainFactor", () -> wetnessSmooth);
        uniformRegistry.registerFloat("rainStrengthS", () -> wetnessSmooth);
        uniformRegistry.registerFloat("rainStrengthShiningStars", () -> wetnessSmooth);
        uniformRegistry.registerFloat("rainStrengthS2", () -> wetnessSmooth);
        uniformRegistry.registerInt("entityId", () -> currentEntityId);
        uniformRegistry.registerFloat("alphaTestRef", () -> currentAlphaTestReference);
        uniformRegistry.registerFloat("iris_currentAlphaTest", () -> currentAlphaTestReference);
        uniformRegistry.registerVec4("entityColor", () -> currentEntityColor);
        uniformRegistry.registerInt("heldItemId", () -> heldItemId(heldMainStack(mc)));
        uniformRegistry.registerInt("heldItemId2", () -> heldItemId(mc.player != null ? mc.player.getHeldItemOffhand() : ItemStack.EMPTY));
        uniformRegistry.registerInt("heldBlockLightValue", () -> heldBlockLightValue(heldMainStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue2", () -> heldBlockLightValue(mc.player != null ? mc.player.getHeldItemOffhand() : ItemStack.EMPTY));
        uniformRegistry.registerInt("currentSelectedBlockId", () -> currentSelectedBlockId(mc));
        uniformRegistry.registerVec3("currentSelectedBlockPos", () -> currentSelectedBlockPos(mc));
        uniformRegistry.registerInt("isEyeInWater", () -> eyeFluidState(mc));
        uniformRegistry.registerVec2i("eyeBrightness", () -> eyeBrightness(mc));
        uniformRegistry.registerVec2i("eyeBrightnessSmooth", this::smoothedEyeBrightness);
        uniformRegistry.registerFloat("eyeBrightnessM", () -> eyeBrightness(mc)[1] / 240.0f);
        uniformRegistry.registerFloat("currentPlayerHealth", () -> mc.player != null ? mc.player.getHealth() : 0.0f);
        uniformRegistry.registerFloat("maxPlayerHealth", () -> mc.player != null ? mc.player.getMaxHealth() : 20.0f);
        uniformRegistry.registerFloat("currentPlayerHunger", () -> mc.player != null ? (float) mc.player.getFoodStats().getFoodLevel() : 0.0f);
        uniformRegistry.registerFloat("maxPlayerHunger", () -> 20.0f);
        uniformRegistry.registerFloat("currentPlayerAir", () -> mc.player != null ? (float) mc.player.getAir() : 0.0f);
        uniformRegistry.registerFloat("maxPlayerAir", () -> 300.0f);
        uniformRegistry.registerFloat("currentPlayerArmor", () -> mc.player != null ? (float) mc.player.getTotalArmorValue() : 0.0f);
        uniformRegistry.registerFloat("maxPlayerArmor", () -> 20.0f);
        uniformRegistry.registerFloat("pi", () -> (float) Math.PI);
        uniformRegistry.registerInt("anisotropicFiltering", () -> 0);
        uniformRegistry.registerInt("blockEntityId", () -> -1);
        uniformRegistry.registerInt("currentRenderedItemId", () -> -1);

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
        uniformRegistry.registerMatrix4("gbufferProjectionInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("projectionMatrixInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("iris_ProjectionMatrixInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("iris_ProjMatInverse", MatrixState::projectionInverse);
        uniformRegistry.registerMatrix4("gbufferPreviousProjection", MatrixState::previousProjection);
        uniformRegistry.registerFloat("fovYInverse", PipelineContext::fovYInverse);
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
        uniformRegistry.registerInt("gtextureId", PipelineContext::boundTexture2d);
        uniformRegistry.registerInt("textureReloadCount", () -> 0);
        uniformRegistry.registerVec2i("atlasSize", PipelineContext::boundTextureSize);
        uniformRegistry.registerVec2i("gtextureSize", PipelineContext::boundTextureSize);
        uniformRegistry.registerVec4i("blendFunc", PipelineContext::blendFunc);

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
        registerAttachmentSizeUniforms();

        uniformRegistry.registerInt("shadow", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("watershadow", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex0", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex0HW", () -> TextureBinder.SHADOWTEX0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex1", () -> TextureBinder.SHADOWTEX1_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowtex1HW", () -> TextureBinder.SHADOWTEX1_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowcolor", () -> TextureBinder.SHADOWCOLOR0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowcolor0", () -> TextureBinder.SHADOWCOLOR0_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowcolor1", () -> TextureBinder.COLORTEX4_TEXTURE_UNIT);
        uniformRegistry.registerInt("shadowMapResolution", this::shadowResolution);
        uniformRegistry.registerVec2i("shadowtex0Size", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowtex1Size", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowSize", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowcolor0Size", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowcolor1Size", () -> shadowSize());
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

        uniformRegistry.registerVec3("cameraPosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("previousCameraPosition", () -> previousCameraPosition.clone());
        uniformRegistry.registerVec3i("cameraPositionInt", () -> cameraPositionInt(cameraPositionUnshifted));
        uniformRegistry.registerVec3("cameraPositionFract", () -> cameraPositionFract(cameraPositionUnshifted));
        uniformRegistry.registerVec3i("previousCameraPositionInt", () -> cameraPositionInt(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("previousCameraPositionFract", () -> cameraPositionFract(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("eyePosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("relativeEyePosition", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("playerLookVector", () -> playerLookVector(mc));
        uniformRegistry.registerFloat("velocity", this::cameraVelocity);

        uniformRegistry.registerVec3("upPosition", PipelineContext::upPosition);
        uniformRegistry.registerVec3("skyColor", () -> {
            Entity viewEntity = mc.getRenderViewEntity();
            if (mc.world != null && viewEntity != null) {
                return vec3(mc.world.getSkyColor(viewEntity, mc.getRenderPartialTicks()));
            }
            return new float[]{0.5f, 0.7f, 1.0f};
        });
        uniformRegistry.registerVec3("fogColor", () -> {
            Entity viewEntity = mc.getRenderViewEntity();
            if (mc.world != null && viewEntity != null) {
                return vec3(mc.world.getSkyColor(viewEntity, mc.getRenderPartialTicks()));
            }
            return new float[]{0.5f, 0.7f, 1.0f};
        });
        uniformRegistry.registerVec4("iris_FogColor", () -> {
            Entity viewEntity = mc.getRenderViewEntity();
            if (mc.world != null && viewEntity != null) {
                float[] color = vec3(mc.world.getSkyColor(viewEntity, mc.getRenderPartialTicks()));
                return new float[]{color[0], color[1], color[2], 1.0f};
            }
            return new float[]{0.5f, 0.7f, 1.0f, 1.0f};
        });

        // --- Sun & Moon Position ---
        uniformRegistry.registerFloat("sunAngle", () -> sunAngle(mc));
        uniformRegistry.registerFloat("shadowAngle", () -> shadowAngle(mc));
        uniformRegistry.registerVec3("endFlashPosition", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("sunPosition", () -> {
            if (mc.world != null) {
                return shaderLightPosition(mc, false);
            }
            return new float[]{0, 100, 0};
        });
        uniformRegistry.registerVec3("moonPosition", () -> {
            if (mc.world != null) {
                return shaderLightPosition(mc, true);
            }
            return new float[]{0, -100, 0};
        });
        uniformRegistry.registerVec3("shadowLightPosition", () -> {
            if (mc.world != null) {
                float celestialAngle = mc.world.getCelestialAngle(mc.getRenderPartialTicks());
                float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
                return legacyShadowLightVector(mc, sunAngle > 0.5F);
            }
            return new float[]{0.0f, 100.0f, 0.0f};
        });
        // --- TAA / History Matrices ---
        /*uniformRegistry.registerMatrix4("gbufferPreviousModelView", MatrixState::getPreviousModelViewMatrix);
        uniformRegistry.registerMatrix4("gbufferPreviousProjection", MatrixState::getPreviousProjectionMatrix);*/
    }

    private void registerAttachmentSizeUniforms() {
        for (Attachment attachment : Attachment.values()) {
            int index = attachment.getIndex();
            uniformRegistry.registerVec2i("colortex" + index + "Size", () -> attachmentSize(attachment));
        }
        uniformRegistry.registerVec2i("gcolorSize", () -> attachmentSize(Attachment.COLOR));
        uniformRegistry.registerVec2i("gdepthSize", () -> attachmentSize(Attachment.DEPTH));
        uniformRegistry.registerVec2i("gnormalSize", () -> attachmentSize(Attachment.NORMAL));
        uniformRegistry.registerVec2i("compositeSize", () -> attachmentSize(Attachment.COMPOSITE));
        uniformRegistry.registerVec2i("gaux1Size", () -> attachmentSize(Attachment.AUX1));
        uniformRegistry.registerVec2i("gaux2Size", () -> attachmentSize(Attachment.AUX2));
        uniformRegistry.registerVec2i("gaux3Size", () -> attachmentSize(Attachment.AUX3));
        uniformRegistry.registerVec2i("gaux4Size", () -> attachmentSize(Attachment.AUX4));
        uniformRegistry.registerVec2i("depthtex0Size", () -> framebufferSize());
        uniformRegistry.registerVec2i("depthtex1Size", () -> framebufferSize());
        uniformRegistry.registerVec2i("depthtex2Size", () -> framebufferSize());
    }

    private int[] attachmentSize(Attachment attachment) {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = Minecraft.getMinecraft();
            return new int[]{Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight)};
        }
        return new int[]{
                Math.max(1, pingPongManager.attachmentWidth(attachment)),
                Math.max(1, pingPongManager.attachmentHeight(attachment))
        };
    }

    private int[] framebufferSize() {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = Minecraft.getMinecraft();
            return new int[]{Math.max(1, mc.displayWidth), Math.max(1, mc.displayHeight)};
        }
        return new int[]{Math.max(1, pingPongManager.width()), Math.max(1, pingPongManager.height())};
    }

    private int shadowResolution() {
        return shadowFramebuffer != null ? Math.max(1, shadowFramebuffer.resolution()) : 1;
    }

    private int[] shadowSize() {
        int resolution = shadowResolution();
        return new int[]{resolution, resolution};
    }

    private static int[] eyeBrightness(Minecraft mc) {
        Entity viewEntity = mc.getRenderViewEntity();
        if (mc.world == null || viewEntity == null) {
            return new int[]{0, 0};
        }

        BlockPos pos = new BlockPos(viewEntity.posX, viewEntity.posY + viewEntity.getEyeHeight(), viewEntity.posZ);
        int combinedLight = mc.world.getCombinedLight(pos, 0);
        int block = combinedLight >> 4 & 0xF;
        int sky = combinedLight >> 20 & 0xF;
        if (eyeFluidState(mc) == 1) {
            sky = underwaterSurfaceSkyLight(mc.world, pos, sky);
        }
        return new int[]{block * 16, sky * 16};
    }

    private static int underwaterSurfaceSkyLight(World world, BlockPos eyePos, int fallbackSky) {
        int maxY = Math.min(world.getHeight(), 255);
        int sky = fallbackSky;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(eyePos);
        for (int y = eyePos.getY(); y <= maxY; y++) {
            probe.setPos(eyePos.getX(), y, eyePos.getZ());
            IBlockState state = world.getBlockState(probe);
            if (state.getMaterial() != Material.WATER) {
                return Math.max(sky, world.getLightFor(EnumSkyBlock.SKY, probe));
            }
            sky = Math.max(sky, world.getLightFor(EnumSkyBlock.SKY, probe));
        }
        return sky;
    }

    private static float blindness(Minecraft mc) {
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity instanceof EntityLivingBase living && living.isPotionActive(MobEffects.BLINDNESS)) {
            PotionEffect effect = living.getActivePotionEffect(MobEffects.BLINDNESS);
            if (effect == null) {
                return 1.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, effect.getDuration() / 20.0f));
        }
        return 0.0f;
    }

    private static float nightVision(Minecraft mc) {
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity instanceof EntityLivingBase living && living.isPotionActive(MobEffects.NIGHT_VISION)) {
            PotionEffect effect = living.getActivePotionEffect(MobEffects.NIGHT_VISION);
            if (effect == null) {
                return 1.0f;
            }
            int duration = effect.getDuration();
            return duration > 200 ? 1.0f : 0.7f + (float) Math.sin((duration - mc.getRenderPartialTicks()) * (float) Math.PI * 0.2f) * 0.3f;
        }
        return 0.0f;
    }

    private int[] smoothedEyeBrightness() {
        return new int[]{
                Math.round(eyeBrightnessSmooth[0]),
                Math.round(eyeBrightnessSmooth[1])
        };
    }

    private void updateSmoothedEyeBrightness(Minecraft mc) {
        int[] current = eyeBrightness(mc);
        if (!eyeBrightnessSmoothInitialized) {
            eyeBrightnessSmooth[0] = current[0];
            eyeBrightnessSmooth[1] = current[1];
            eyeBrightnessSmoothInitialized = true;
            return;
        }

        float smoothingFactor = smoothingFactor(eyeBrightnessHalfLife, currentFrameTime);
        eyeBrightnessSmooth[0] += (current[0] - eyeBrightnessSmooth[0]) * smoothingFactor;
        eyeBrightnessSmooth[1] += (current[1] - eyeBrightnessSmooth[1]) * smoothingFactor;
    }

    private void updateSmoothedWetness(Minecraft mc) {
        float current = mc.world != null ? mc.world.getRainStrength(mc.getRenderPartialTicks()) : 0.0f;
        if (!wetnessSmoothInitialized) {
            wetnessSmooth = current;
            wetnessSmoothInitialized = true;
            return;
        }

        float halfLife = current > wetnessSmooth ? wetnessHalfLife : drynessHalfLife;
        wetnessSmooth += (current - wetnessSmooth) * smoothingFactor(halfLife, currentFrameTime);
    }

    private void updateSmoothedFrameTime() {
        if (!frameTimeSmoothInitialized) {
            frameTimeSmooth = currentFrameTime;
            frameTimeSmoothInitialized = true;
            return;
        }
        frameTimeSmooth += (currentFrameTime - frameTimeSmooth) * smoothingFactor(5.0f, currentFrameTime);
    }

    private static float smoothingFactor(float halfLifeDeciseconds, float frameTimeSeconds) {
        if (halfLifeDeciseconds <= 0.0f) {
            return 1.0f;
        }
        float halfLifeSeconds = halfLifeDeciseconds * 0.1f;
        float decay = (float) (Math.log(2.0) / halfLifeSeconds);
        return 1.0f - (float) Math.exp(-decay * Math.max(0.0f, frameTimeSeconds));
    }

    private static FloatBuffer createIrisLightmapTextureMatrix() {
        FloatBuffer buffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
        buffer.put(new float[]{
                0.00390625f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.00390625f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.00390625f, 0.0f,
                0.03125f, 0.03125f, 0.03125f, 1.0f
        });
        buffer.flip();
        return buffer;
    }

    private static FloatBuffer irisLightmapTextureMatrix() {
        IRIS_LIGHTMAP_TEXTURE_MATRIX.position(0);
        return IRIS_LIGHTMAP_TEXTURE_MATRIX;
    }

    private static float fovYInverse() {
        FloatBuffer projection = MatrixState.projection();
        float projectionY = projection.get(5);
        if (Math.abs(projectionY) < 1.0E-6f) {
            return 1.0f;
        }
        return 1.0f / (float) Math.atan(1.0f / projectionY) * 0.5f;
    }

    private static int boundTexture2d() {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static int[] boundTextureSize() {
        int texture = boundTexture2d();
        if (texture == 0) {
            return new int[]{0, 0};
        }

        int width = Math.max(0, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
        int height = Math.max(0, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        return new int[]{width, height};
    }

    private static int[] blendFunc() {
        if (!GL11.glIsEnabled(GL11.GL_BLEND)) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        };
    }

    private static int[] cameraPositionInt(double[] position) {
        return new int[]{
                (int) Math.floor(position[0]),
                (int) Math.floor(position[1]),
                (int) Math.floor(position[2])
        };
    }

    private static float[] cameraPositionFract(double[] position) {
        return new float[]{
                (float) (position[0] - Math.floor(position[0])),
                (float) (position[1] - Math.floor(position[1])),
                (float) (position[2] - Math.floor(position[2]))
        };
    }

    private int currentSelectedBlockId(Minecraft mc) {
        BlockPos pos = currentSelectedBlockPosition(mc);
        if (mc.world == null || pos == null) {
            return 0;
        }
        return blockEntityId(mc.world.getBlockState(pos));
    }

    private static float[] currentSelectedBlockPos(Minecraft mc) {
        BlockPos pos = currentSelectedBlockPosition(mc);
        if (pos == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return new float[]{pos.getX(), pos.getY(), pos.getZ()};
    }

    private static BlockPos currentSelectedBlockPosition(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    private static float[] playerLookVector(Minecraft mc) {
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            return new float[]{0.0f, 0.0f, 1.0f};
        }
        Vec3d look = viewEntity.getLook(mc.getRenderPartialTicks());
        return vec3(look);
    }

    private static float[] upPosition() {
        return MatrixState.transformModelViewDirection(0.0f, 100.0f, 0.0f);
    }

    private float cameraVelocity() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float y = cameraPosition[1] - previousCameraPosition[1];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private float[] taaOffset(Minecraft mc) {
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
                offset[0] / Math.max(1, mc.displayWidth),
                offset[1] / Math.max(1, mc.displayHeight)
        };
    }

    private static float[] vec3(Vec3d vec) {
        return new float[]{(float) vec.x, (float) vec.y, (float) vec.z};
    }

    private float[] viewSpaceLightVector(Minecraft mc, boolean moon) {
        float[] world = worldSpaceLightVector(mc, moon);
        return MatrixState.transformModelViewDirection(world[0], world[1], world[2]);
    }

    private float[] shaderLightPosition(Minecraft mc, boolean moon) {
        return viewSpaceLightVector(mc, moon);
    }

    private float[] worldSpaceLightVector(Minecraft mc, boolean moon) {
        float skyAngle = mc.world.getCelestialAngle(mc.getRenderPartialTicks()) * (float) (Math.PI * 2.0);
        if (moon) {
            skyAngle += (float) Math.PI;
        }
        float path = sunPathRotation * (float) (Math.PI / 180.0);

        // Iris derives celestial uniforms from the same transform chain used by
        // sky rendering: rotate Y -90, rotate Z by sunPathRotation, then rotate X
        // by the current sun/moon angle.  The initial vector is the vanilla
        // celestial body position in sky model space.
        float x = 0.0f;
        float y = 100.0f * (float) Math.cos(skyAngle);
        float z = 100.0f * (float) Math.sin(skyAngle);

        float pathX = x * (float) Math.cos(path) - y * (float) Math.sin(path);
        float pathY = x * (float) Math.sin(path) + y * (float) Math.cos(path);
        x = -z;
        y = pathY;
        z = pathX;
        return new float[]{x, y, z};
    }

    private float sunAngle(Minecraft mc) {
        if (mc.world == null) {
            return 0.0f;
        }
        float angle = mc.world.getCelestialAngle(mc.getRenderPartialTicks()) + 0.25f;
        if (angle >= 1.0f) {
            angle -= 1.0f;
        }
        return angle;
    }

    private float shadowAngle(Minecraft mc) {
        if (mc.world == null) {
            return 0.0f;
        }
        float angle = sunAngle(mc);
        return angle < 0.5f ? angle : angle - 0.5f;
    }

    private float shadowFade(Minecraft mc, float threshold, float scale) {
        float angle = sunAngle(mc);
        return clamp01(1.0f - (Math.abs(Math.abs(angle - 0.5f) - 0.25f) - threshold) * scale);
    }

    private float[] legacyShadowLightVector(Minecraft mc, boolean moon) {
        return viewSpaceLightVector(mc, moon);
    }

    private float dayMoment(Minecraft mc) {
        if (mc.world == null) {
            return 0.25f;
        }
        return (float) ((mc.world.getWorldTime() % 24000L) / 24000.0);
    }

    private float adjustedDayTime(Minecraft mc) {
        return Math.abs(((((mc.world != null ? mc.world.getWorldTime() % 24000L : 0L) / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
    }

    private float dayHelper(Minecraft mc) {
        return clamp01(5.4f - adjustedDayTime(mc));
    }

    private float nightHelper(Minecraft mc) {
        return clamp01(adjustedDayTime(mc) - 6.0f);
    }

    private float dayMixer(Minecraft mc) {
        float moment = dayMoment(mc) - 0.25f;
        return clamp01(-(moment * moment) * 20.0f + 1.25f);
    }

    private float nightMixer(Minecraft mc) {
        float moment = dayMoment(mc) - 0.75f;
        return clamp01(-(moment * moment) * 50.0f + 3.125f);
    }

    private float dayNightMix(Minecraft mc) {
        if (mc.world == null) {
            return 1.0f;
        }
        float worldTime = mc.world.getWorldTime() % 24000L;
        float day = worldTime < 12485.0f || worldTime >= 23515.0f ? 1.0f : 0.0f;
        float dusk = worldTime >= 12485.0f && worldTime < 13085.0f
                ? 1.0f - ((worldTime - 12485.0f) * 0.0016666667f)
                : 0.0f;
        float dawn = worldTime >= 22915.0f && worldTime < 23515.0f
                ? (worldTime - 22915.0f) * 0.0016666667f
                : 0.0f;
        return Math.max(Math.max(day, dusk), dawn);
    }

    private float volumetricDayMixer(Minecraft mc) {
        float moment = dayMoment(mc);
        float day = (moment * 4.0f) - 1.0f;
        float night = (moment * 4.0f) - 3.0f;
        float dayValue = clamp((-(day * day * day * day) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        float nightValue = clamp((-(night * night * night * night) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        return Math.max(dayValue, nightValue);
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void initialize(ShaderPack pack) {
        initialize(pack, Map.of());
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides) {
        initialize(pack, optionOverrides, null);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        cleanup(); // Clear previous state
        shaderProperties = emptyShaderProperties();
        activePackName = pack.getName();

        MainMod.LOGGER.info("[Pipeline] Initializing with pack: {}", pack.getName());

        if (pack.getName().equals("(internal)")) { // NoneShaderPack
            MainMod.LOGGER.info("[Pipeline] Internal None pack selected. Pipeline is inactive.");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
        programSet = ShaderProgramSet.load(pack, properties);
        packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
        rebuildFullscreenProgramArrays();
        packDirectives = packDirectives.withCapabilities(
                ShaderPipelineCapabilities.from(packDirectives)
                        .withExtraProgramArrayEntries(hasExtraProgramArrayEntries())
        );
        ShaderLoadingMap loadingMap = new ShaderLoadingMap();
        shaderProperties = properties;
        shadowMapDistance = parseFloatSettingWithComment(pack, properties, "shadowDistance", "SHADOWHPL", 128.0f);
        shadowDistanceRenderMul = parseFloatSetting(pack, properties, "shadowDistanceRenderMul", -1.0f);
        shadowIntervalSize = parseFloatSetting(pack, properties, "shadowIntervalSize", 2.0f);
        sunPathRotation = parseFloatSetting(pack, properties, "sunPathRotation", 0.0f);
        centerDepthHalfLife = parseFloatSetting(pack, properties, "centerDepthHalflife", 1.0f);
        eyeBrightnessHalfLife = parseFloatSetting(pack, properties, "eyeBrightnessHalflife", 3.0f);
        wetnessHalfLife = parseFloatSetting(pack, properties, "wetnessHalflife", 600.0f);
        drynessHalfLife = parseFloatSetting(pack, properties, "drynessHalflife", 200.0f);
        shadowPolygonOffset = parseBooleanSetting(pack, properties, "shadowPolygonOffset", true);
        shadowPolygonOffsetFactor = parseFloatSetting(pack, properties, "shadowPolygonOffsetFactor", 1.1f);
        shadowPolygonOffsetUnits = parseFloatSetting(pack, properties, "shadowPolygonOffsetUnits", 4.0f);
        shadowFrameCount = 1_000_000;
        lastShadowFrameId = -1L;
        pingPongManager.initialize(mc.displayWidth, mc.displayHeight, packDirectives.renderTargets());
        initializeBlankShadowFramebuffer(pack, properties);
        MainMod.LOGGER.debug(
                "[Pipeline] Shadow config: framebuffer={} distance={} renderMul={} interval={} sunPathRotation={} hardwareFiltering={} tex0Nearest={} tex1Nearest={} polygonOffset={} factor={} units={}",
                shadowFramebuffer != null ? shadowFramebuffer.resolution() : 0,
                shadowMapDistance,
                shadowDistanceRenderMul,
                shadowIntervalSize,
                sunPathRotation,
                packDirectives.renderTargets().shadowHardwareFiltering(),
                packDirectives.renderTargets().shadowDepthNearest(0),
                packDirectives.renderTargets().shadowDepthNearest(1),
                shadowPolygonOffset,
                shadowPolygonOffsetFactor,
                shadowPolygonOffsetUnits
        );
        if (isMakeUpPack()) {
            MainMod.LOGGER.debug(
                    "[Pipeline] MakeUp shadow settings detail: shadowDistance={} option={} activeConst={} shadowDistanceRenderMul={} activeConst={} shadowIntervalSize={} option={} activeConst={}",
                    shadowMapDistance,
                    optionValue(properties, "shadowDistance"),
                    activeConstSetting(pack, properties, "shadowDistance"),
                    shadowDistanceRenderMul,
                    activeConstSetting(pack, properties, "shadowDistanceRenderMul"),
                    shadowIntervalSize,
                    optionValue(properties, "shadowIntervalSize"),
                    activeConstSetting(pack, properties, "shadowIntervalSize")
            );
        }
        if (packDirectives.computeDirectives().hasComputes()) {
            MainMod.LOGGER.debug(
                    "[Pipeline] Loaded compute metadata: arrays={} shadow={} final={}",
                    packDirectives.computeDirectives().computeArrays().values().stream().mapToInt(List::size).sum(),
                    packDirectives.computeDirectives().shadowComputes().size(),
                    packDirectives.computeDirectives().finalComputes().size()
                );
        }
        shaderImages = ShaderImageSet.load(packDirectives.images());
        shaderImages.resize(mc.displayWidth, mc.displayHeight);
        shaderStorageBuffers = ShaderStorageBufferSet.load(pack, packDirectives.storageBuffers());
        shaderStorageBuffers.resize(mc.displayWidth, mc.displayHeight);
        compileComputePrograms(pack, properties);
        logRequestedFeaturesAndCapabilities();
        initializeNoiseTexture(properties);
        loadCustomTextures(pack, properties);
        lastPipelineFrameNanos = System.nanoTime() - 1_000_000_000L;
        currentFrameTime = 1.0f;

        for (RenderPass pass : RenderPass.values()) {
            PipelineProgram pipelineProgram = new PipelineProgram(pass, programSet.source(pass.programId()).directives());
            boolean enabled = properties.isProgramEnabled(pass);
            pipelineProgram.setEnabled(enabled);

            if (enabled) {
                ShaderProgram program = ShaderCompiler.compilePass(pack, pass, properties, programSet.source(pass.programId()));
                if (program != null) {
                    pipelineProgram.setShaderProgram(program);
                    loadingMap.put(pipelineProgram.shaderKey(), program);
                    MainMod.LOGGER.debug("[Pipeline] Added program for pass: {}", pass);
                }
            } else {
                MainMod.LOGGER.debug("[Pipeline] Program disabled by properties: {}", pass.getProgramName());
            }
            programs.put(pass, pipelineProgram);
        }
        shaderMap = new ShaderMap(loadingMap);

        isPipelineActive = pingPongManager.isInitialized();
        long loadedProgramCount = programs.values().stream().filter(PipelineProgram::hasOwnProgram).count();
        MainMod.LOGGER.info("[Pipeline] Initialization complete. Pipeline Active: {}, Loaded Programs: {}", isPipelineActive, loadedProgramCount);
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    public ShaderProperties getShaderProperties() {
        return shaderProperties;
    }

    private void initializeBlankShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        if (!shouldCreateShadowFramebuffer(pack, properties)) {
            return;
        }

        String resolutionValue = settingValueWithComment(pack, properties, "shadowMapResolution", "SHADOWRES");
        int resolution = parseIntValue(resolutionValue, 1024);
        resolution = Math.max(16, Math.min(8192, resolution));
        shadowFramebuffer = new ShadowFramebuffer(resolution, packDirectives.renderTargets());
        shadowMapPopulated = false;
        MainMod.LOGGER.debug(
                "[Pipeline] Blank shadow textures initialized: {}x{} (shadowMapResolution={} option={} changedOption={} activeConst={} profile={} distanceSlider={} qualitySlider={})",
                resolution,
                resolution,
                resolutionValue,
                optionValue(properties, "shadowMapResolution"),
                changedOptionValue(properties, "shadowMapResolution"),
                activeConstSetting(pack, properties, "shadowMapResolution"),
                optionValue(properties, "<profile>"),
                optionValue(properties, "SHADOW_DISTANCE_SLIDER"),
                optionValue(properties, "SHADOW_QTY_SLIDER")
        );
    }

    private static boolean shouldCreateShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        return hasEffectiveShadowProgram(properties)
                || properties.options().booleanValue("SHADOW_CASTING")
                || properties.options().booleanValue("ENABLE_SHADOWS")
                || hasShadowProgramFiles(pack);
    }

    private static boolean hasEffectiveShadowProgram(ShaderProperties properties) {
        for (RenderPass pass : RenderPass.values()) {
            if (pass.stage() == ProgramStage.SHADOW && properties.isProgramEnabled(pass)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasShadowProgramFiles(ShaderPack pack) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        for (RenderPass pass : RenderPass.values()) {
            if (pass.stage() != ProgramStage.SHADOW) {
                continue;
            }
            for (String base : layout.programBases(pass)) {
                if (pack.hasResource(base + ".vsh") || pack.hasResource(base + ".fsh") || pack.hasResource(base + ".gsh")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String optionValue(ShaderProperties properties, String name) {
        var option = properties.options().get(name);
        return option == null ? null : option.value();
    }

    private static String changedOptionValue(ShaderProperties properties, String name) {
        var option = properties.options().get(name);
        return option == null || !option.changed() ? null : option.value();
    }

    private static int parseIntOption(ShaderProperties properties, String name, int fallback) {
        String value = optionValue(properties, name);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseIntSettingWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName, int fallback) {
        return parseIntValue(settingValueWithComment(pack, properties, optionName, commentName), fallback);
    }

    private static float parseFloatOption(ShaderProperties properties, String name, float fallback) {
        String value = optionValue(properties, name);
        if (value == null) {
            return fallback;
        }

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloatSetting(ShaderPack pack, ShaderProperties properties, String name, float fallback) {
        return parseFloatValue(settingValue(pack, properties, name), fallback);
    }

    private static float parseFloatSettingWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName, float fallback) {
        return parseFloatValue(settingValueWithComment(pack, properties, optionName, commentName), fallback);
    }

    private static boolean optionBoolean(ShaderProperties properties, String name, boolean fallback) {
        var option = properties.options().get(name);
        return option == null ? fallback : option.asBoolean();
    }

    private static boolean parseBooleanSetting(ShaderPack pack, ShaderProperties properties, String name, boolean fallback) {
        String value = settingValue(pack, properties, name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static String settingValueWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName) {
        String value = settingValue(pack, properties, optionName);
        if (value != null) {
            return value;
        }
        value = rawShaderProperty(pack, commentName);
        if (value != null) {
            return value;
        }
        return scanCommentDirective(pack, commentName);
    }

    private static String settingValue(ShaderPack pack, ShaderProperties properties, String name) {
        String value = changedOptionValue(properties, name);
        if (value != null) {
            return value;
        }
        value = rawShaderProperty(pack, name);
        if (value != null) {
            return value;
        }
        value = activeConstSetting(pack, properties, name);
        return value != null ? value : optionValue(properties, name);
    }

    private static int parseIntValue(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloatValue(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String rawShaderProperty(ShaderPack pack, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        if (!pack.hasResource(layout.propertiesPath())) {
            return null;
        }
        Properties properties = new Properties();
        try (var stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream == null) {
                return null;
            }
            properties.load(stream);
        } catch (IOException ignored) {
            return null;
        }
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String scanCommentDirective(ShaderPack pack, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        String value = null;
        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                value = lastCommentDirectiveValue(pack, base + ".vsh", name, value);
                value = lastCommentDirectiveValue(pack, base + ".fsh", name, value);
                value = lastCommentDirectiveValue(pack, base + ".gsh", name, value);
            }
        }
        value = lastCommentDirectiveValue(pack, layout.rootPath("shader.h"), name, value);
        return value;
    }

    private static String lastCommentDirectiveValue(ShaderPack pack, String path, String name, String fallback) {
        if (!pack.hasResource(path)) {
            return fallback;
        }
        try (var stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return fallback;
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String prefix = "/* " + name + ":";
            int start = source.lastIndexOf(prefix);
            if (start < 0) {
                return fallback;
            }
            int valueStart = start + prefix.length();
            int end = source.indexOf("*/", valueStart);
            if (end < 0) {
                return fallback;
            }
            return source.substring(valueStart, end).trim();
        } catch (IOException ignored) {
            return fallback;
        }
    }

    private static String activeConstSetting(ShaderPack pack, ShaderProperties properties, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        ActiveConstScan scan = new ActiveConstScan(pack, properties, name);
        scan.scan(layout.rootPath("lib/config.glsl"));
        scan.scan(layout.rootPath("lib/settings.glsl"));
        scan.scan(layout.rootPath("settings.glsl"));
        scan.scan(layout.rootPath("shader.h"));
        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                scan.scan(base + ".vsh");
                scan.scan(base + ".fsh");
                scan.scan(base + ".gsh");
            }
        }
        return scan.value();
    }

    private static String includePath(String includeLine, String currentFile) {
        int firstQuote = includeLine.indexOf('"');
        int lastQuote = includeLine.lastIndexOf('"');
        if (firstQuote == -1 || lastQuote == -1 || firstQuote >= lastQuote) {
            return null;
        }

        String path = includeLine.substring(firstQuote + 1, lastQuote);
        if (path.startsWith("/")) {
            return currentFile.startsWith("shaders/") ? "shaders" + path : path.substring(1);
        }
        int lastSlash = currentFile.lastIndexOf('/');
        return lastSlash == -1 ? path : currentFile.substring(0, lastSlash + 1) + path;
    }

    private static final class ActiveConstScan {
        private final ShaderPack pack;
        private final ShaderProperties properties;
        private final String targetName;
        private final Map<String, String> defines = new HashMap<>();
        private final Set<String> visited = new HashSet<>();
        private final Deque<ConditionFrame> conditions = new ArrayDeque<>();
        private String value;

        private ActiveConstScan(ShaderPack pack, ShaderProperties properties, String targetName) {
            this.pack = pack;
            this.properties = properties;
            this.targetName = targetName;
        }

        private String value() {
            return value;
        }

        private void scan(String path) {
            if (!pack.hasResource(path) || !visited.add(path)) {
                return;
            }
            try (var stream = pack.getResourceAsStream(path)) {
                if (stream == null) {
                    return;
                }
                String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : source.split("\\R", -1)) {
                    scanLine(path, line);
                }
            } catch (IOException ignored) {
            } finally {
                visited.remove(path);
            }
        }

        private void scanLine(String currentFile, String line) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include ")) {
                if (active()) {
                    String includePath = includePath(trimmed, currentFile);
                    if (includePath != null) {
                        scan(includePath);
                    }
                }
                return;
            }
            if (trimmed.startsWith("#if ")) {
                pushCondition(evaluateCondition(trimmed.substring(4)));
                return;
            }
            if (trimmed.startsWith("#ifdef ")) {
                pushCondition(defines.containsKey(trimmed.substring(7).trim()));
                return;
            }
            if (trimmed.startsWith("#ifndef ")) {
                pushCondition(!defines.containsKey(trimmed.substring(8).trim()));
                return;
            }
            if (trimmed.startsWith("#elif ")) {
                replaceCondition(evaluateCondition(trimmed.substring(6)));
                return;
            }
            if (trimmed.startsWith("#else")) {
                replaceCondition(true);
                return;
            }
            if (trimmed.startsWith("#endif")) {
                if (!conditions.isEmpty()) {
                    conditions.pop();
                }
                return;
            }
            if (!active()) {
                return;
            }

            String withoutComment = stripLineComment(line);
            Matcher defineMatcher = DEFINE_SETTING_PATTERN.matcher(withoutComment);
            if (defineMatcher.matches()) {
                applyDefine(defineMatcher.group(1), defineMatcher.group(2));
                return;
            }

            Matcher constMatcher = CONST_SETTING_PATTERN.matcher(withoutComment);
            if (constMatcher.matches()) {
                defines.put(constMatcher.group(1), constMatcher.group(2));
                if (targetName.equals(constMatcher.group(1))) {
                    value = constMatcher.group(2);
                }
            }
        }

        private void applyDefine(String name, String value) {
            var option = properties.options().get(name);
            if (option != null && option.toggle() && !option.asBoolean()) {
                defines.remove(name);
            } else if (option != null) {
                defines.put(name, option.toggle() ? "1" : option.value());
            } else {
                defines.put(name, value == null ? "1" : value);
            }
        }

        private void pushCondition(boolean condition) {
            boolean parentActive = active();
            boolean branchActive = parentActive && condition;
            conditions.push(new ConditionFrame(parentActive, branchActive, condition));
        }

        private void replaceCondition(boolean condition) {
            if (conditions.isEmpty()) {
                return;
            }
            ConditionFrame previous = conditions.pop();
            boolean branchActive = previous.parentActive() && !previous.branchMatched() && condition;
            conditions.push(new ConditionFrame(previous.parentActive(), branchActive, previous.branchMatched() || condition));
        }

        private boolean active() {
            return conditions.isEmpty() || conditions.peek().active();
        }

        private boolean evaluateCondition(String expression) {
            String normalized = stripLineComment(expression)
                    .replace("(", " ")
                    .replace(")", " ")
                    .trim();
            if (normalized.isEmpty()) {
                return false;
            }
            for (String orPart : normalized.split("\\|\\|")) {
                boolean andValue = true;
                for (String andPart : orPart.split("&&")) {
                    andValue &= evaluateSimpleCondition(andPart.trim());
                }
                if (andValue) {
                    return true;
                }
            }
            return false;
        }

        private boolean evaluateSimpleCondition(String expression) {
            if (expression.startsWith("defined ")) {
                return defines.containsKey(expression.substring("defined ".length()).trim());
            }
            if (expression.startsWith("!defined ")) {
                return !defines.containsKey(expression.substring("!defined ".length()).trim());
            }
            if (expression.contains("==")) {
                String[] parts = expression.split("==", 2);
                return valueOf(parts[0]).equals(valueOf(parts[1]));
            }
            if (expression.contains("!=")) {
                String[] parts = expression.split("!=", 2);
                return !valueOf(parts[0]).equals(valueOf(parts[1]));
            }
            if (expression.startsWith("!")) {
                return !truthy(valueOf(expression.substring(1)));
            }
            return truthy(valueOf(expression));
        }

        private String valueOf(String token) {
            String trimmed = token.trim();
            return defines.getOrDefault(trimmed, trimmed);
        }

        private boolean truthy(String value) {
            if (value == null || value.isBlank()) {
                return false;
            }
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "0", "false", "off" -> false;
                default -> true;
            };
        }

        private String stripLineComment(String line) {
            int commentStart = line.indexOf("//");
            return commentStart < 0 ? line : line.substring(0, commentStart);
        }
    }

    private record ConditionFrame(boolean parentActive, boolean active, boolean branchMatched) {
    }

    private void rebuildFullscreenProgramArrays() {
        fullscreenProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            FullscreenProgramArray array = FullscreenProgramArray.fromProgramSet(arrayId, programSet);
            fullscreenProgramArrays.put(arrayId, array);
            if (array.hasExtraPrograms()) {
                MainMod.LOGGER.debug(
                        "[Pipeline] Program array {} declares {} programs; current 1.12 adapter exposes {} fixed slots.",
                        arrayId.sourcePrefix(),
                        array.declaredProgramCount(),
                        array.fixedPasses().size()
                );
            }
        }
    }

    private boolean hasExtraProgramArrayEntries() {
        return fullscreenProgramArrays.values().stream().anyMatch(FullscreenProgramArray::hasExtraPrograms);
    }

    private void compileComputePrograms(ShaderPack pack, ShaderProperties properties) {
        computeProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            List<ComputeProgram> compiled = compileComputeList(pack, properties, packDirectives.computeDirectives().computeArrays().getOrDefault(arrayId, List.of()));
            if (!compiled.isEmpty()) {
                computeProgramArrays.put(arrayId, compiled);
            }
        }
        shadowComputePrograms = compileComputeList(pack, properties, packDirectives.computeDirectives().shadowComputes());
        finalComputePrograms = compileComputeList(pack, properties, packDirectives.computeDirectives().finalComputes());
    }

    private static List<ComputeProgram> compileComputeList(ShaderPack pack, ShaderProperties properties, List<ComputeProgramSource> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<ComputeProgram> compiled = new ArrayList<>();
        for (ComputeProgramSource source : sources) {
            ComputeProgram program = ComputeProgram.compile(pack, properties, source);
            if (program != null) {
                compiled.add(program);
            }
        }
        return List.copyOf(compiled);
    }

    private void deleteComputePrograms() {
        computeProgramArrays.values().stream()
                .flatMap(List::stream)
                .forEach(ComputeProgram::delete);
        shadowComputePrograms.forEach(ComputeProgram::delete);
        finalComputePrograms.forEach(ComputeProgram::delete);
    }

    private void logRequestedFeaturesAndCapabilities() {
        if (!packDirectives.features().required().isEmpty() || !packDirectives.features().optional().isEmpty()) {
            MainMod.LOGGER.debug(
                    "[Pipeline] Pack feature flags: required={} optional={}",
                    packDirectives.features().required(),
                    packDirectives.features().optional()
            );
        }

        ShaderPipelineCapabilities capabilities = packDirectives.capabilities();
        if (capabilities.compute()
                || capabilities.images()
                || capabilities.storageBuffers()
                || capabilities.customUniforms()
                || capabilities.customTextures()
                || capabilities.extraProgramArrayEntries()) {
            MainMod.LOGGER.debug("[Pipeline] Pack capabilities: {}", capabilities);
        }
        if (shaderImages.active()) {
            MainMod.LOGGER.debug("[Pipeline] Loaded {} Iris custom image directives", shaderImages.count());
        }
        if (shaderStorageBuffers.active()) {
            MainMod.LOGGER.debug("[Pipeline] Loaded {} Iris SSBO directives", shaderStorageBuffers.count());
        }
        if (packDirectives.textureDirectives().rawTextureCount() > 0) {
            MainMod.LOGGER.debug(
                    "[Pipeline] Loaded {} Iris raw custom texture directives",
                    packDirectives.textureDirectives().rawTextureCount()
            );
        }
        if (capabilities.images() && !packDirectives.features().requires("custom_images") && !packDirectives.features().optional("custom_images")) {
            MainMod.LOGGER.warn("[Pipeline] Pack declares image directives without iris.features custom_images");
        }
        if (capabilities.storageBuffers() && !packDirectives.features().requires("ssbo") && !packDirectives.features().optional("ssbo")) {
            MainMod.LOGGER.warn("[Pipeline] Pack declares SSBO directives without iris.features ssbo");
        }
    }

    public int blockEntityId(IBlockState state) {
        if (state == null) {
            return 0;
        }
        if (shaderProperties.blockIds().isEmpty()) {
            return 0;
        }
        return shaderProperties.blockIds().idFor(state);
    }

    public boolean shouldSeparateBlockAo(IBlockState state) {
        if (!shouldSeparateAo() || state == null) {
            return false;
        }

        Block block = state.getBlock();
        return block != null && block.getRenderLayer() != BlockRenderLayer.TRANSLUCENT;
    }

    public boolean shouldSeparateAo() {
        return isPipelineActive && shaderProperties.renderSettings().separateAo();
    }

    public float ambientOcclusionLevel() {
        return isPipelineActive ? shaderProperties.renderSettings().ambientOcclusionLevel() : 1.0f;
    }

    public boolean shouldDisableDirectionalShading() {
        return isPipelineActive && !shaderProperties.renderSettings().oldLighting();
    }

    public boolean shouldRenderWeather() {
        return !isPipelineActive || shaderProperties.renderSettings().weather();
    }

    public boolean shouldRenderWeatherParticles() {
        return !isPipelineActive || shaderProperties.renderSettings().weatherParticles();
    }

    public boolean shouldRenderVignette() {
        return !isPipelineActive || shaderProperties.renderSettings().vignette();
    }

    public boolean shouldRenderUnderwaterOverlay() {
        return !isPipelineActive || shaderProperties.renderSettings().underwaterOverlay();
    }

    public boolean shouldRenderSkyDisc() {
        return !isPipelineActive || shaderProperties.renderSettings().sky();
    }

    public boolean shouldRenderClouds() {
        return !isPipelineActive || !"off".equals(shaderProperties.renderSettings().clouds());
    }

    public void applySkySunPathRotation() {
        if (isPipelineActive && sunPathRotation != 0.0f) {
            GlStateManager.rotate(sunPathRotation, 0.0F, 0.0F, 1.0F);
        }
    }

    public void applyTerrainCulling(WorldRenderingPhase phase) {
        if (!isPipelineActive || terrainCullOverrideActive || !shouldDisableCullForPhase(phase)) {
            return;
        }
        previousTerrainCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        terrainCullOverrideActive = true;
        GlStateManager.disableCull();
    }

    public void restoreTerrainCulling() {
        if (!terrainCullOverrideActive) {
            return;
        }
        terrainCullOverrideActive = false;
        if (previousTerrainCullEnabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
    }

    private boolean shouldDisableCullForPhase(WorldRenderingPhase phase) {
        return switch (phase) {
            case TERRAIN_SOLID -> shaderProperties.renderSettings().backFaceSolid();
            case TERRAIN_CUTOUT -> shaderProperties.renderSettings().backFaceCutout();
            case TERRAIN_CUTOUT_MIPPED -> shaderProperties.renderSettings().backFaceCutoutMipped();
            case TERRAIN_TRANSLUCENT -> shaderProperties.renderSettings().backFaceTranslucent();
            default -> false;
        };
    }

    public boolean shouldUsePipelineEntityFormat() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingGuiScreen()) {
            return false;
        }
        if (activePass.stage() == ProgramStage.SHADOW) {
            return true;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesEntityFormat();
        }
        return activePass.stage() == ProgramStage.SHADOW
                || switch (activePass) {
                    case GBUFFERS_ITEM, GBUFFERS_ENTITIES, GBUFFERS_ENTITIES_GLOWING, GBUFFERS_HAND,
                            GBUFFERS_HAND_WATER, GBUFFERS_BLOCK, GBUFFERS_BLOCK_TRANSLUCENT,
                            GBUFFERS_ENTITIES_TRANSLUCENT -> true;
                    default -> false;
                };
    }

    public boolean isShadowPassActive() {
        return isPipelineActive && (renderingShadowMap || activePass != null && activePass.stage() == ProgramStage.SHADOW);
    }

    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : activePhase;
    }

    public ShaderKey getShaderKey() {
        return activeShaderKey;
    }

    public FogMode getFogMode() {
        return activeShaderKey == null ? FogMode.OFF : activeShaderKey.fogMode();
    }

    public LightingModel getLightingModel() {
        return activeShaderKey == null ? LightingModel.LIGHTMAP : activeShaderKey.lightingModel();
    }

    public void setPhase(WorldRenderingPhase phase) {
        activePhase = phase == null ? WorldRenderingPhase.NONE : phase;
    }

    public void clearPhase(WorldRenderingPhase expectedPhase) {
        if (expectedPhase == null || activePhase == expectedPhase) {
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    public void setOverridePhase(WorldRenderingPhase phase) {
        overridePhase = phase;
    }

    public void clearOverridePhase() {
        overridePhase = null;
    }

    public int entityId(Entity entity) {
        if (entity == null) {
            return 0;
        }

        ResourceLocation entityKey = EntityList.getKey(entity);
        if (entityKey != null) {
            Integer alias = shaderProperties.entityIds().get(entityKey);
            if (alias != null) {
                return alias;
            }
        }

        return 0;
    }

    public int currentEntityId() {
        return currentEntityId;
    }

    private int heldItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        int id = Item.getIdFromItem(stack.getItem());
        return shaderProperties.itemIds().getOrDefault(id, 0);
    }

    private ItemStack heldMainStack(Minecraft mc) {
        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack mainHand = mc.player.getHeldItemMainhand();
        if (!shaderProperties.renderSettings().oldHandLight()) {
            return mainHand;
        }

        ItemStack offHand = mc.player.getHeldItemOffhand();
        return heldBlockLightValue(offHand) > heldBlockLightValue(mainHand) ? offHand : mainHand;
    }

    private int heldBlockLightValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null) {
            return 0;
        }
        return block.getLightValue(block.getDefaultState());
    }

    private float[] entityColor(Entity entity) {
        if (entity instanceof EntityLivingBase living) {
            if (living.hurtTime > 0 || living.deathTime > 0) {
                float hurtRatio = living.hurtTime / Math.max(1.0f, living.maxHurtTime);
                float deathRatio = Math.min(1.0f, living.deathTime / 20.0f);
                float alpha = Math.max(hurtRatio, deathRatio) * 0.25f;
                return new float[]{1.0f, 0.0f, 0.0f, alpha};
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    public void setCurrentEntity(Entity entity) {
        currentEntityId = entityId(entity);
        currentEntityColor = entityColor(entity);
        uploadEntityUniforms();
    }

    public void clearCurrentEntity() {
        currentEntityId = 0;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        uploadEntityUniforms();
    }

    public void applyWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        GlStateManager.depthMask(true);
    }

    public void restoreWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        GlStateManager.depthMask(true);
    }

    public void applyWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        GL30.glEnablei(GL11.GL_BLEND, Attachment.COLOR.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.DEPTH.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.NORMAL.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.COMPOSITE.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.AUX1.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.AUX2.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.AUX3.getIndex());
        GL30.glDisablei(GL11.GL_BLEND, Attachment.AUX4.getIndex());
        // Final passes reconstruct the current water pixel from depthtex0, so
        // water must update the live depth buffer.
        GlStateManager.depthMask(true);
    }

    public void restoreWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        GL30.glEnablei(GL11.GL_BLEND, Attachment.COLOR.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.DEPTH.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.NORMAL.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.COMPOSITE.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.AUX1.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.AUX2.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.AUX3.getIndex());
        GL30.glEnablei(GL11.GL_BLEND, Attachment.AUX4.getIndex());
        GlStateManager.depthMask(true);
    }

    private void uploadEntityUniforms() {
        ShaderProgram program = activeProgram();
        if (program == null) {
            return;
        }

        uniformRegistry.upload(program, "entityId");
        uniformRegistry.upload(program, "entityColor");
    }

    public void beginPass(RenderPass pass) {
        beginPass(pass, WorldRenderingPhase.NONE);
    }

    private void beginPass(RenderPass pass, WorldRenderingPhase phase) {
        if (!isPipelineActive || !worldFrameActive) {
            return;
        }

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        activePhase = phase;
        boolean bound = bindPass(pass);
        passStack.push(new PassScope(bound, previousPass, previousShaderKey, previousPhase));
    }

    public void beginPhase(WorldRenderingPhase phase) {
        if (renderingGuiScreen()) {
            return;
        }
        RenderPass pass = passForPhase(phase);
        if (pass != null) {
            beginPass(pass, phase);
        }
    }

    public boolean beginItemRenderPhase() {
        if (!shouldRouteRenderItemThroughPipeline()) {
            return false;
        }
        beginPhase(WorldRenderingPhase.ITEM);
        return true;
    }

    public boolean beginItemGlintPhase() {
        if (!shouldRouteItemGlintThroughPipeline()) {
            return false;
        }
        beginPhase(WorldRenderingPhase.ARMOR_GLINT);
        return true;
    }

    private boolean shouldRouteRenderItemThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase != WorldRenderingPhase.HAND_SOLID
                && phase != WorldRenderingPhase.HAND_TRANSLUCENT
                && phase != WorldRenderingPhase.ARMOR_GLINT;
    }

    private boolean shouldRouteItemGlintThroughPipeline() {
        return isPipelineActive && worldFrameActive && !renderingGuiScreen();
    }

    private boolean renderingGuiScreen() {
        return renderingGui || guiRenderDepth > 0 || renderingDeferredIngameHud;
    }

    private RenderPass passForPhase(WorldRenderingPhase phase) {
        return renderingShadowMap ? phase.shadowPass() : phase.mainPass();
    }

    private boolean bindPass(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        if (pipelineProgram == null || !pipelineProgram.enabled()) {
            return false;
        }

        ShaderProgram program = pipelineProgram.effectiveProgram(programs);
        if (program == null) {
            return false;
        }

        activeShaderKey = pipelineProgram.shaderKey();
        applyAlphaTest(pass);
        List<Attachment> drawBuffers = effectiveDrawBuffersForCurrentPhase(pipelineProgram);
        applyBlendMode(pass, drawBuffers);
        configureGbufferDrawBuffers(pipelineProgram, drawBuffers);
        if (pipelineProgram.stage() == ProgramStage.GBUFFERS) {
            restoreVanillaWorldTextureBindings();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableTexture2D();
            GL11.glColorMask(true, true, true, true);
            boolean blockAtlasPass = usesBlockAtlas(pass);
            if (blockAtlasPass) {
                bindBlockAtlas();
            }
            TextureBinder.bindGbufferRenderTargetSamplers();
            if (blockAtlasPass) {
                bindBlockAtlas();
            }
        }
        if (pipelineProgram.stage().readsDeferredTextures()) {
            TextureBinder.bindDeferredTextures();
        } else {
            TextureBinder.bindNoiseTexture();
        }
        if (pipelineProgram.stage() != ProgramStage.SHADOW) {
            TextureBinder.bindShadowTextures();
        }
        if (pipelineProgram.stage() == ProgramStage.GBUFFERS) {
            TextureBinder.bindMaterialFallbackTextures();
        }

        program.bind();
        bindProgramResources(pass, program);
        if (pipelineProgram.stage() == ProgramStage.SHADOW && getPhase().usesBlockAtlas()) {
            bindBlockAtlas();
        }
        activePass = pass;
        return true;
    }

    private void bindProgramResources(RenderPass pass, ShaderProgram program) {
        bindCustomTextures(pass, program);
        shaderImages.bind(program);
        shaderStorageBuffers.bind();
        uniformRegistry.uploadAll(program);
        packDirectives.customUniforms().upload(program, uniformRegistry.scalarValues());
    }

    private List<Attachment> effectiveDrawBuffersForCurrentPhase(PipelineProgram pipelineProgram) {
        List<Attachment> drawBuffers = pipelineProgram.effectiveDrawBuffers(programs);
        if (pipelineProgram.stage() != ProgramStage.GBUFFERS || getPhase() != WorldRenderingPhase.STARS || !drawBuffers.contains(Attachment.AUX4)) {
            return drawBuffers;
        }

        List<Attachment> filtered = new ArrayList<>(drawBuffers.size());
        for (Attachment attachment : drawBuffers) {
            if (attachment != Attachment.AUX4) {
                filtered.add(attachment);
            }
        }
        return filtered.isEmpty() ? drawBuffers : List.copyOf(filtered);
    }

    private boolean usesBlockAtlas(RenderPass pass) {
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesBlockAtlas();
        }
        return switch (pass) {
            case GBUFFERS_TERRAIN, GBUFFERS_TERRAIN_SOLID, GBUFFERS_TERRAIN_CUTOUT_MIP,
                    GBUFFERS_TERRAIN_CUTOUT, GBUFFERS_WATER, GBUFFERS_DAMAGEDBLOCK -> true;
            default -> false;
        };
    }

    private boolean isMakeUpPack() {
        return activePackName.toLowerCase(java.util.Locale.ROOT).contains("makeup");
    }

    private void bindBlockAtlas() {
        TextureBinder.restoreDefaultTextureUnit();
        Minecraft mc = Minecraft.getMinecraft();
        ITextureObject texture = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (texture == null) {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            texture = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        }
        if (texture != null) {
            int textureId = texture.getGlTextureId();
            GlStateManager.bindTexture(textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        } else {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        }
    }

    private void applyAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderAlphaTest alphaTest = pipelineProgram == null ? null : pipelineProgram.directives().alphaTestOverride();
        if (alphaTest == null) {
            alphaTest = defaultAlphaTest(pass);
        }

        currentAlphaTestReference = alphaTest.reference();
        if (alphaTest.function() == GL11.GL_ALWAYS) {
            GlStateManager.disableAlpha();
        } else {
            GlStateManager.enableAlpha();
        }
        GlStateManager.alphaFunc(alphaTest.function(), alphaTest.reference());
    }

    private static ShaderAlphaTest defaultAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = INSTANCE.programs.get(pass);
        ShaderKey key = pipelineProgram == null ? ShaderKey.fromRenderPass(pass) : pipelineProgram.shaderKey();
        return key == null ? ShaderAlphaTest.ALWAYS : key.alphaTest();
    }

    private void applyBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderBlendMode blendMode = pipelineProgram == null ? null : pipelineProgram.directives().blendModeOverride();
        if (blendMode == null) {
            blendMode = defaultBlendMode(pass);
        }
        Map<Attachment, ShaderBlendMode> attachmentModes = attachmentBlendModesFor(pass);
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }

        if (blendMode != null && !blendMode.enabled()) {
            GlStateManager.disableBlend();
            return;
        }

        GlStateManager.enableBlend();
        if (blendMode != null) {
            GlStateManager.tryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
        if (attachmentModes.isEmpty()) {
            return;
        }

        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            ShaderBlendMode attachmentMode = attachmentModes.get(attachment);
            if (attachmentMode != null) {
                applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            }
        }
    }

    private static ShaderBlendMode defaultBlendMode(RenderPass pass) {
        return switch (pass) {
            case SHADOW, SHADOW_SOLID, SHADOW_CUTOUT, SHADOW_WATER, SHADOW_ENTITIES, SHADOW_LIGHTNING, SHADOW_BLOCK -> ShaderBlendMode.OFF;
            case GBUFFERS_SPIDEREYES -> new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            default -> null;
        };
    }

    private Map<Attachment, ShaderBlendMode> attachmentBlendModesFor(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        Map<Attachment, ShaderBlendMode> attachmentModes = pipelineProgram == null ? null : pipelineProgram.directives().attachmentBlendModes();
        return attachmentModes == null ? Map.of() : attachmentModes;
    }

    private void applyIndexedBlendMode(int drawBufferIndex, ShaderBlendMode blendMode) {
        if (!blendMode.enabled()) {
            GL30.glDisablei(GL11.GL_BLEND, drawBufferIndex);
            return;
        }

        GL30.glEnablei(GL11.GL_BLEND, drawBufferIndex);
        if (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_draw_buffers_blend) {
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(
                    drawBufferIndex,
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        } else {
            GlStateManager.tryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
    }

    private void configureGbufferDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (!pingPongManager.isInitialized() || pipelineProgram.stage() != ProgramStage.GBUFFERS) {
            return;
        }

        if (!drawBuffers.isEmpty()) {
            pingPongManager.bindForGbuffers(drawBuffers.toArray(new Attachment[0]));
        }
    }

    public void endPass() {
        if (!isPipelineActive || passStack.isEmpty()) {
            return;
        }

        PassScope scope = passStack.pop();
        activePhase = scope.previousPhase();
        if (!scope.bound()) {
            activeShaderKey = scope.previousShaderKey();
            return;
        }

        PipelineProgram pipelineProgram = programs.get(activePass);
        ShaderProgram program = pipelineProgram != null ? pipelineProgram.effectiveProgram(programs) : null;
        if (program != null) {
            program.unbind();
        }

        activePass = null;
        activeShaderKey = null;
        if (scope.previousPass() != null) {
            bindPass(scope.previousPass());
        } else {
            activeShaderKey = scope.previousShaderKey();
        }
    }

    public void resize(int width, int height) {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        resizeFramebuffer(width, height, true);
    }

    public void beginFrame() {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            return;
        }
        worldFrameActive = true;
        if (pingPongManager.width() != mc.displayWidth || pingPongManager.height() != mc.displayHeight) {
            resizeFramebuffer(mc.displayWidth, mc.displayHeight, true);
        }

        long now = System.nanoTime();
        boolean paused = mc.isGamePaused();
        currentFrameTime = paused ? 0.0f : Math.min(Math.max((now - lastPipelineFrameNanos) / 1_000_000_000.0f, 0.001f), 1.0f);
        lastPipelineFrameNanos = now;
        if (!paused) {
            pipelineFrameId++;
            frameTimeCounter += currentFrameTime;
            if (frameTimeCounter >= 3600.0f) {
                frameTimeCounter = 0.0f;
            }
        }
        deferredPassesRenderedThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        updateCameraPosition(mc);
        if (paused) {
            System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
            System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);
        } else {
            updateSmoothedFrameTime();
            updateSmoothedEyeBrightness(mc);
            updateSmoothedWetness(mc);
        }
        pingPongManager.beginFrame(frameClearAttachments());
    }

    private void updateCameraPosition(Minecraft mc) {
        System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
        System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            cameraPosition[0] = 0.0f;
            cameraPosition[1] = 0.0f;
            cameraPosition[2] = 0.0f;
            cameraPositionUnshifted[0] = 0.0;
            cameraPositionUnshifted[1] = 0.0;
            cameraPositionUnshifted[2] = 0.0;
            return;
        }

        float partialTicks = mc.getRenderPartialTicks();
        Vec3d eyePosition = viewEntity.getPositionEyes(partialTicks);
        double x = eyePosition.x;
        double y = eyePosition.y;
        double z = eyePosition.z;
        cameraPositionUnshifted[0] = x;
        cameraPositionUnshifted[1] = y;
        cameraPositionUnshifted[2] = z;
        updateCameraOffset(viewEntity, x, y, z);

        cameraPosition[0] = (float) (x + cameraShiftX);
        cameraPosition[1] = (float) y;
        cameraPosition[2] = (float) (z + cameraShiftZ);
    }

    private void updateCameraOffset(Entity viewEntity, double x, double y, double z) {
        double adjustedX = x + cameraShiftX;
        double adjustedZ = z + cameraShiftZ;
        double adx = Math.abs(adjustedX - previousCameraPosition[0]);
        double adz = Math.abs(adjustedZ - previousCameraPosition[2]);
        double apx = Math.abs(adjustedX);
        double apz = Math.abs(adjustedZ);
        double shiftX = irisCameraShift(adjustedX, adx, apx);
        double shiftZ = irisCameraShift(adjustedZ, adz, apz);
        if (shiftX != 0.0 || shiftZ != 0.0) {
            cameraShiftX += shiftX;
            cameraShiftZ += shiftZ;
            previousCameraPosition[0] += (float) shiftX;
            previousCameraPosition[2] += (float) shiftZ;
        }
        if (Math.abs(viewEntity.posX - x) > 1000.0 || Math.abs(viewEntity.posZ - z) > 1000.0) {
            previousCameraPosition[0] = (float) (x + cameraShiftX);
            previousCameraPosition[1] = (float) y;
            previousCameraPosition[2] = (float) (z + cameraShiftZ);
        }
    }

    private static double irisCameraShift(double adjusted, double delta, double absoluteAdjusted) {
        final double walkRange = 30000.0;
        final double teleportRange = 1000.0;
        if (absoluteAdjusted > walkRange || delta > teleportRange) {
            return -(adjusted - (adjusted % walkRange));
        }
        return 0.0;
    }

    private void resizeFramebuffer(int width, int height, boolean preservePersistentAttachments) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (preservePersistentAttachments) {
            pingPongManager.resize(width, height, packDirectives.renderTargets().clearDisabled());
        } else {
            pingPongManager.resize(width, height);
        }
        shaderImages.resize(width, height);
        shaderStorageBuffers.resize(width, height);
    }

    private Attachment[] frameClearAttachments() {
        java.util.Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            if (!clearDisabled.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    private boolean hasActiveShadowProgram() {
        for (PipelineProgram program : programs.values()) {
            if (program.stage() == ProgramStage.SHADOW && program.effectiveProgram(programs) != null) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldDisableVanillaEntityShadows() {
        return isPipelineActive && shadowFramebuffer != null && hasActiveShadowProgram();
    }

    public void renderShadowMap(float partialTicks) {
        if (!isPipelineActive || shadowFramebuffer == null || lastShadowFrameId == pipelineFrameId) {
            return;
        }
        if (!hasActiveShadowProgram()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewEntity = mc.getRenderViewEntity();
        if (mc.world == null || viewEntity == null || mc.renderGlobal == null) {
            return;
        }

        lastShadowFrameId = pipelineFrameId;
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousRenderChunksMany = mc.renderChunksMany;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        try {
            setupShadowCamera(viewEntity, partialTicks);
            ICamera shadowCamera = createShadowCamera(viewEntity, partialTicks);
            ((RenderGlobalAccessor) mc.renderGlobal).ausm$setDisplayListEntitiesDirty(true);
            // Iris disables chunk occlusion culling while building the shadow terrain list.
            // The 1.12 equivalent is renderChunksMany; leaving it enabled lets the normal
            // camera visibility graph leak into the light-space pass.
            mc.renderChunksMany = false;
            mc.renderGlobal.setupTerrain(
                    viewEntity,
                    partialTicks,
                    shadowCamera,
                    nextShadowFrameCount(),
                    mc.player != null && mc.player.isSpectator()
            );

            if (shaderProperties.renderSettings().shadowTerrain()
                    && !hasShadowTerrainCandidates(mc, viewEntity, partialTicks)) {
                return;
            }

            shaderImages.clearSmallImages();
            shadowFramebuffer.bindForRendering();
            shadowFramebuffer.clear();
            configureShadowTerrainRenderState();
            if (shadowPolygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(shadowPolygonOffsetFactor, shadowPolygonOffsetUnits);
            }
            TextureBinder.restoreDefaultTextureUnit();
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

            renderingShadowMap = true;
            int solidCount = -1;
            int cutoutMippedCount = -1;
            int cutoutCount = -1;
            int translucentCount = -1;
            if (shaderProperties.renderSettings().shadowTerrain()) {
                solidCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
                cutoutMippedCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
                cutoutCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            }
            if (shaderProperties.renderSettings().shadowEntities()
                    || shaderProperties.renderSettings().shadowPlayer()
                    || shaderProperties.renderSettings().shadowBlockEntities()
                    || shaderProperties.renderSettings().shadowLightBlockEntities()) {
                beginPhase(WorldRenderingPhase.ENTITIES);
                mc.renderGlobal.renderEntities(viewEntity, shadowCamera, partialTicks);
                renderShadowEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            shadowFramebuffer.copyDepthToSnapshot();
            if (shaderProperties.renderSettings().shadowTranslucent()) {
                translucentCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            }
            if (solidCount > 0 || cutoutMippedCount > 0 || cutoutCount > 0 || translucentCount > 0) {
                shadowMapPopulated = true;
            }
            shadowFramebuffer.generateShadowColorMipmaps();
            runComputePrograms(shadowComputePrograms, RenderPass.SHADOW);
            runFullscreenPasses(ProgramArrayId.SHADOWCOMP);
        } finally {
            mc.renderChunksMany = previousRenderChunksMany;
            renderingShadowMap = false;
            activePass = null;
            activeShaderKey = null;
            activePhase = WorldRenderingPhase.NONE;
            overridePhase = null;
            passStack.clear();
            OpenGlHelper.glUseProgram(0);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(true, true, true, true);
            if (previousCull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFramebuffer);
            viewportBuffer.position(0);
            GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    private boolean hasShadowTerrainCandidates(Minecraft mc, Entity viewEntity, float partialTicks) {
        RenderGlobalAccessor renderGlobal = (RenderGlobalAccessor) mc.renderGlobal;
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null || viewFrustum.renderChunks == null) {
            return true;
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (RenderChunk renderChunk : viewFrustum.renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            BlockPos position = renderChunk.getPosition();
            double dx = position.getX() + 8.0D - cameraX;
            double dy = position.getY() + 8.0D - cameraY;
            double dz = position.getZ() + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            if (!renderChunk.getCompiledChunk().isLayerEmpty(BlockRenderLayer.SOLID)
                    || !renderChunk.getCompiledChunk().isLayerEmpty(BlockRenderLayer.CUTOUT_MIPPED)
                    || !renderChunk.getCompiledChunk().isLayerEmpty(BlockRenderLayer.CUTOUT)
                    || (shaderProperties.renderSettings().shadowTranslucent()
                    && !renderChunk.getCompiledChunk().isLayerEmpty(BlockRenderLayer.TRANSLUCENT))) {
                return true;
            }
        }
        return false;
    }

    private static void configureShadowTerrainRenderState() {
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    }

    private int renderShadowTerrainLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        beginPhase(phase);
        configureShadowTerrainRenderState();
        boolean previousPolygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT && previousPolygonOffset) {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            GlStateManager.disableBlend();
            GL11.glDepthFunc(GL11.GL_ALWAYS);
        }
        try {
            int count = renderShadowBlockLayer(mc, layer, partialTicks, viewEntity);
            return count;
        } finally {
            GL11.glDepthFunc(previousDepthFunc);
            if (previousBlend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (previousPolygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            endPass();
        }
    }

    private int renderShadowBlockLayer(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        int count = mc.renderGlobal.renderBlockLayer(layer, partialTicks, 2, viewEntity);
        if (count != 0) {
            return count;
        }
        return renderShadowBlockLayerFromViewFrustum(mc, layer, partialTicks, viewEntity);
    }

    private int renderShadowBlockLayerFromViewFrustum(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        RenderGlobalAccessor renderGlobal = (RenderGlobalAccessor) mc.renderGlobal;
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        ChunkRenderContainer renderContainer = renderGlobal.ausm$renderContainer();
        if (viewFrustum == null || viewFrustum.renderChunks == null || renderContainer == null) {
            return 0;
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        renderContainer.initialize(cameraX, cameraY, cameraZ);
        int fallbackCount = 0;
        for (RenderChunk renderChunk : viewFrustum.renderChunks) {
            if (renderChunk == null || renderChunk.getCompiledChunk().isLayerEmpty(layer)) {
                continue;
            }
            BlockPos position = renderChunk.getPosition();
            double dx = position.getX() + 8.0D - cameraX;
            double dy = position.getY() + 8.0D - cameraY;
            double dz = position.getZ() + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            renderContainer.addRenderChunk(renderChunk, layer);
            fallbackCount++;
        }

        if (fallbackCount > 0) {
            renderContainer.renderChunkLayer(layer);
        }
        return fallbackCount;
    }

    private double shadowRenderCullDistance() {
        if (shadowDistanceRenderMul < 0.0f) {
            return -1.0D;
        }
        return Math.max(32.0D, shadowMapDistance * shadowDistanceRenderMul + 32.0D);
    }

    private int nextShadowFrameCount() {
        if (shadowFrameCount == Integer.MAX_VALUE) {
            shadowFrameCount = 1_000_000;
        }
        return shadowFrameCount++;
    }

    private void setupShadowCamera(Entity viewEntity, float partialTicks) {
        double x = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double y = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double z = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(-shadowMapDistance, shadowMapDistance, -shadowMapDistance, shadowMapDistance, 0.05F, 256.0F);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -100.0F);
        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

        float celestialAngle = Minecraft.getMinecraft().world.getCelestialAngle(partialTicks);
        float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
        float angle = celestialAngle * -360.0F;
        if (sunAngle <= 0.5F) {
            GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
        } else {
            GL11.glRotatef(angle + 180.0F, 0.0F, 0.0F, 1.0F);
        }
        GL11.glRotatef(sunPathRotation, 1.0F, 0.0F, 0.0F);

        double interval = Math.max(0.001F, shadowIntervalSize);
        double snapX = centeredRemainder(x, interval);
        double snapY = centeredRemainder(y, interval);
        double snapZ = centeredRemainder(z, interval);
        GL11.glTranslatef((float) snapX, (float) snapY, (float) snapZ);
        MatrixState.captureShadowMatrices();
    }

    private static float shadowAngle(float partialTicks) {
        float celestialAngle = Minecraft.getMinecraft().world.getCelestialAngle(partialTicks);
        float angle = celestialAngle + 0.25F;
        if (angle >= 1.0F) {
            angle -= 1.0F;
        }
        return angle;
    }

    private ICamera createShadowCamera(Entity viewEntity, float partialTicks) {
        return new ICamera() {
            @Override
            public boolean isBoundingBoxInFrustum(AxisAlignedBB box) {
                return true;
            }

            @Override
            public void setPosition(double x, double y, double z) {
            }
        };
    }

    private void renderShadowEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowEntities() && !shaderProperties.renderSettings().shadowPlayer()) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);

        renderManager.cacheActiveRenderInfo(mc.world, mc.fontRenderer, viewEntity, mc.pointedEntity, mc.gameSettings, partialTicks);
        renderManager.setRenderPosition(cameraX, cameraY, cameraZ);
        mc.entityRenderer.enableLightmap();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        for (Entity entity : mc.world.getLoadedEntityList()) {
            if (!shouldRenderEntityInShadowMap(mc, renderManager, entity, viewEntity, shadowCamera, cameraX, cameraY, cameraZ)) {
                continue;
            }

            renderManager.renderEntityStatic(entity, partialTicks, false);
            if (renderManager.isRenderMultipass(entity)) {
                renderManager.renderMultipass(entity, partialTicks);
            }
        }
    }

    private boolean shouldRenderEntityInShadowMap(Minecraft mc, RenderManager renderManager, Entity entity, Entity viewEntity,
                                                  ICamera shadowCamera, double cameraX, double cameraY, double cameraZ) {
        if (entity == null || entity.isDead || !entity.shouldRenderInPass(0)) {
            return false;
        }
        if (entity instanceof AbstractClientPlayer player && player.isSpectator()) {
            return false;
        }
        if (entity == viewEntity
                && !shaderProperties.renderSettings().shadowEntities()
                && !shaderProperties.renderSettings().shadowPlayer()) {
            return false;
        }
        if (entity != viewEntity && !shaderProperties.renderSettings().shadowEntities()) {
            return false;
        }
        if (!renderManager.shouldRender(entity, shadowCamera, cameraX, cameraY, cameraZ)
                && (mc.player == null || !entity.isRidingOrBeingRiddenBy(mc.player))) {
            return false;
        }
        if (entity.posY >= 0.0D && entity.posY < 256.0D && !mc.world.isBlockLoaded(new BlockPos(entity))) {
            return false;
        }
        return entity.isInRangeToRender3d(cameraX, cameraY, cameraZ);
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static int eyeFluidState(Minecraft mc) {
        Entity viewEntity = mc.getRenderViewEntity();
        if (mc.world == null || viewEntity == null) {
            return 0;
        }

        Material cameraMaterial = ActiveRenderInfo
                .getBlockStateAtEntityViewpoint(mc.world, viewEntity, mc.getRenderPartialTicks())
                .getMaterial();
        if (cameraMaterial == Material.WATER) {
            return 1;
        }
        if (cameraMaterial == Material.LAVA && (mc.player == null || !mc.player.isSpectator())) {
            return 2;
        }
        return 0;
    }

    private static double centeredRemainder(double value, double interval) {
        return value % interval - interval * 0.5;
    }

    public void bindWorldFramebuffer() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        pingPongManager.bindForGbuffers(Attachment.COLOR);
        restoreVanillaWorldTextureBindings();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
    }

    private void restoreVanillaWorldTextureBindings() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.entityRenderer != null) {
            mc.entityRenderer.enableLightmap();
            DynamicTexture lightmapTexture = ((EntityRendererAccessor) mc.entityRenderer).ausm$getLightmapTexture();
            if (lightmapTexture != null) {
                int irisLightmapTextureId = irisLightmapTexture.updateFrom(lightmapTexture);
                if (irisLightmapTextureId > 0) {
                    TextureBinder.bindIrisLightmap(irisLightmapTextureId);
                } else {
                    TextureBinder.bindIrisLightmap(lightmapTexture.getGlTextureId());
                }
            } else {
                TextureBinder.mirrorVanillaLightmapToIrisUnit();
            }
        } else {
            TextureBinder.mirrorVanillaLightmapToIrisUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void renderPreparePass() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    public void snapshotOpaqueTerrainDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        centerDepth = pingPongManager.getReadBuffer().readCenterDepth();
        if (Float.isFinite(centerDepth)) {
            centerDepthSmooth += (centerDepth - centerDepthSmooth) * smoothingFactor(centerDepthHalfLife, currentFrameTime);
            if (Math.abs(centerDepth - centerDepthSmooth) < 0.00001f) {
                centerDepthSmooth = centerDepth;
            }
            updateCenterDepthSmoothTexture();
        }
    }

    public int getCenterDepthSmoothTexture() {
        ensureCenterDepthSmoothTexture();
        return centerDepthSmoothTexture;
    }

    private void ensureCenterDepthSmoothTexture() {
        if (centerDepthSmoothTexture != -1) {
            return;
        }

        centerDepthSmoothTexture = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, centerDepthSmoothTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        centerDepthTextureBuffer.clear();
        centerDepthTextureBuffer.put(centerDepthSmooth).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, 1, 1, 0, GL11.GL_RED, GL11.GL_FLOAT, centerDepthTextureBuffer);
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void updateCenterDepthSmoothTexture() {
        ensureCenterDepthSmoothTexture();
        centerDepthTextureBuffer.clear();
        centerDepthTextureBuffer.put(centerDepthSmooth).flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, centerDepthSmoothTexture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL11.GL_RED, GL11.GL_FLOAT, centerDepthTextureBuffer);
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void deleteCenterDepthSmoothTexture() {
        if (centerDepthSmoothTexture != -1) {
            GL11.glDeleteTextures(centerDepthSmoothTexture);
            centerDepthSmoothTexture = -1;
        }
    }

    public int getNoiseTexture() {
        if (noiseTexture == -1) {
            noiseTexture = ShaderTextureLoader.createNoiseTexture(256);
        }
        return noiseTexture;
    }

    private void initializeNoiseTexture(ShaderProperties properties) {
        int resolution = parseIntOption(properties, "noiseTextureResolution", packDirectives.noiseTextureResolution());
        noiseTexture = ShaderTextureLoader.createNoiseTexture(resolution);
    }

    private void deleteNoiseTexture() {
        if (noiseTexture != -1) {
            GL11.glDeleteTextures(noiseTexture);
            noiseTexture = -1;
        }
    }

    private void copyPreTranslucentDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        if (!preTranslucentDepthCopiedThisFrame) {
            pingPongManager.copyPreTranslucentDepth();
            preTranslucentDepthCopiedThisFrame = true;
        }
    }

    public void beginTranslucents() {
        if (!isPipelineActive || !pingPongManager.isInitialized() || deferredPassesRenderedThisFrame) {
            return;
        }

        copyPreTranslucentDepth();
        runFullscreenPasses(ProgramArrayId.DEFERRED);
        deferredPassesRenderedThisFrame = true;
        bindWorldFramebuffer();
    }

    public void beginHand() {
        beginTranslucents();
        if (!isPipelineActive || !pingPongManager.isInitialized() || preHandDepthCopiedThisFrame) {
            return;
        }

        pingPongManager.copyPreHandDepth();
        preHandDepthCopiedThisFrame = true;
    }

    public void blitWorldFramebufferToMinecraft() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer target = mc.getFramebuffer();

        beginTranslucents();
        runFullscreenPasses(ProgramArrayId.COMPOSITE);
        runComputePrograms(finalComputePrograms, RenderPass.FINAL);

        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram != null && finalProgram.hasOwnProgram()) {
            renderFinalPass(target);
            finishWorldFramebuffer(target);
            return;
        }

        pingPongManager.getReadBuffer().blitTo(
                target.framebufferObject,
                target.framebufferWidth,
                target.framebufferHeight
        );

        target.bindFramebuffer(false);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        finishWorldFramebuffer(target);
    }

    private void finishWorldFramebuffer(Framebuffer target) {
        target.bindFramebuffer(false);
        GlStateManager.clearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        resetPipelineState();
        worldFrameActive = false;
    }

    private void runFullscreenPasses(RenderPass[] passes) {
        for (RenderPass pass : passes) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                runFullscreenPass(program);
            }
        }
    }

    private void runFullscreenPasses(ProgramArrayId arrayId) {
        runComputePrograms(computeProgramArrays.getOrDefault(arrayId, List.of()), computeBindingPass(arrayId));
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        if (array == null) {
            return;
        }
        for (RenderPass pass : array.fixedPasses()) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                runFullscreenPass(program);
            }
        }
    }

    private RenderPass computeBindingPass(ProgramArrayId arrayId) {
        return switch (arrayId) {
            case PREPARE -> RenderPass.PREPARE;
            case DEFERRED -> RenderPass.DEFERRED;
            case COMPOSITE -> RenderPass.COMPOSITE;
            case SHADOWCOMP -> RenderPass.SHADOW;
            default -> RenderPass.FINAL;
        };
    }

    private void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        applyShaderMemoryBarrier();
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int width = framebuffer != null ? framebuffer.getWidth() : Minecraft.getMinecraft().displayWidth;
        int height = framebuffer != null ? framebuffer.getHeight() : Minecraft.getMinecraft().displayHeight;
        for (ComputeProgram compute : computes) {
            compute.bind();
            TextureBinder.bindDeferredTextures();
            TextureBinder.bindShadowTextures();
            bindProgramResources(bindingPass, compute.program());
            int[] groups = compute.workGroups(width, height);
            GL43.glDispatchCompute(groups[0], groups[1], groups[2]);
            applyShaderMemoryBarrier();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void applyShaderMemoryBarrier() {
        GL42.glMemoryBarrier(
                GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                        | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                        | GL42.GL_TEXTURE_FETCH_BARRIER_BIT
                        | GL42.GL_FRAMEBUFFER_BARRIER_BIT
        );
    }

    private void runFullscreenPass(PipelineProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        generateReadMipmaps(program);

        setupFullscreenState();
        applyFullscreenViewport(program, drawBuffers);
        beginPass(program.pass());
        FullscreenQuad.draw();
        endPass();
        restoreFullscreenState();

        pingPongManager.flipWrittenTextures(program.directives().flippedAttachments(drawBuffers));
    }

    private void applyViewportScale(PipelineProgram program, int width, int height) {
        ShaderViewportScale scale = program.directives().viewportScale();
        GlStateManager.viewport(scale.x(width), scale.y(height), scale.width(width), scale.height(height));
    }

    private void applyFullscreenViewport(PipelineProgram program, List<Attachment> drawBuffers) {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();
        if (!drawBuffers.isEmpty()) {
            Attachment first = drawBuffers.get(0);
            width = framebuffer.getAttachmentWidth(first);
            height = framebuffer.getAttachmentHeight(first);
            for (Attachment attachment : drawBuffers) {
                if (framebuffer.getAttachmentWidth(attachment) != width || framebuffer.getAttachmentHeight(attachment) != height) {
                    MainMod.LOGGER.warn("[Pipeline] Pass {} writes differently sized buffers; using {} size {}x{} for viewport",
                            program.pass().getProgramName(), first, width, height);
                    break;
                }
            }
        }
        applyViewportScale(program, width, height);
    }

    private void renderFinalPass(Framebuffer target) {
        Minecraft mc = Minecraft.getMinecraft();

        pingPongManager.getReadBuffer().blitDepthTo(
                target.framebufferObject,
                target.framebufferWidth,
                target.framebufferHeight
        );

        target.bindFramebuffer(false);
        GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        generateReadMipmaps(finalProgram);

        setupFullscreenState();
        applyViewportScale(finalProgram, target.framebufferWidth, target.framebufferHeight);
        beginPass(RenderPass.FINAL);
        FullscreenQuad.draw();
        endPass();
        restoreFullscreenState();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
    }

    private void generateReadMipmaps(PipelineProgram program) {
        if (program != null && !program.directives().mipmappedBuffers().isEmpty()) {
            pingPongManager.getReadBuffer().generateMipmaps(program.directives().mipmappedBuffers());
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    private void setupFullscreenState() {
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColorMask(true, true, true, true);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, 0.0, 1.0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    private void restoreFullscreenState() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
    }

    public void cleanup() {
        resetPipelineState();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);

        pingPongManager.cleanup();
        if (shadowFramebuffer != null) {
            shadowFramebuffer.delete();
            shadowFramebuffer = null;
        }
        shadowMapPopulated = false;
        deleteCenterDepthSmoothTexture();
        deleteNoiseTexture();
        deleteCustomTextures();
        shaderImages.delete();
        shaderImages = ShaderImageSet.empty();
        shaderStorageBuffers.delete();
        shaderStorageBuffers = ShaderStorageBufferSet.empty();
        deleteComputePrograms();
        for (PipelineProgram program : programs.values()) {
            program.delete();
        }
        programs.clear();
        programSet = null;
        shaderMap = null;
        fullscreenProgramArrays.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        packDirectives = emptyShaderProperties().packDirectives();
        isPipelineActive = false;
        activePackName = "(internal)";
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        centerDepthHalfLife = 1.0f;
        centerDepth = 1.0f;
        centerDepthSmooth = 1.0f;
        pipelineFrameId = 0L;
        frameTimeCounter = 0.0f;
        currentFrameTime = 0.016f;
        frameTimeSmooth = 0.016f;
        frameTimeSmoothInitialized = false;
        cameraShiftX = 0.0;
        cameraShiftZ = 0.0;
        for (int i = 0; i < 3; i++) {
            cameraPosition[i] = 0.0f;
            previousCameraPosition[i] = 0.0f;
            cameraPositionUnshifted[i] = 0.0;
            previousCameraPositionUnshifted[i] = 0.0;
        }
        eyeBrightnessHalfLife = 3.0f;
        wetnessHalfLife = 600.0f;
        drynessHalfLife = 200.0f;
        eyeBrightnessSmooth[0] = 0.0f;
        eyeBrightnessSmooth[1] = 0.0f;
        eyeBrightnessSmoothInitialized = false;
        wetnessSmooth = 0.0f;
        wetnessSmoothInitialized = false;
        passStack.clear();
    }


    public boolean isActive() {
        return isPipelineActive;
    }

    public void prepareFramebufferPresentation() {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GL11.glColorMask(true, true, true, true);
    }

    public void prepareGuiRendering() {
        if (!isPipelineActive) {
            return;
        }

        renderingGui = true;
        bindGuiTarget();
        prepareGuiState();
    }

    public void beginGuiRendering() {
        if (!isPipelineActive) {
            return;
        }

        guiRenderDepth++;
        prepareGuiRendering();
    }

    public void finishGuiRendering() {
        if (guiRenderDepth > 0) {
            guiRenderDepth--;
        }
        if (guiRenderDepth == 0) {
            renderingGui = false;
        }
    }

    private void bindGuiTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        if (renderingDeferredIngameHud) {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        } else if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    private void prepareGuiState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GL11.glDepthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL30.glEnablei(GL11.GL_BLEND, 0);
    }

    public boolean shouldDirectPresentFramebuffer() {
        Minecraft mc = Minecraft.getMinecraft();
        return isPipelineActive && mc.gameSettings.thirdPersonView != 0;
    }

    public boolean shouldDeferIngameHud() {
        Minecraft mc = Minecraft.getMinecraft();
        return shouldDirectPresentFramebuffer() && mc.currentScreen == null && !renderingDeferredIngameHud;
    }

    public void beginDeferredIngameHud() {
        renderingDeferredIngameHud = true;
        prepareGuiRendering();
    }

    public void endDeferredIngameHud() {
        renderingDeferredIngameHud = false;
        renderingGui = false;
        guiRenderDepth = 0;
        TextureBinder.restoreDefaultTextureUnit();
        GL11.glColorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void presentFramebufferDirectly(Framebuffer target, int width, int height) {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (target != mc.getFramebuffer()) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        OpenGlHelper.glUseProgram(0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GL11.glColorMask(true, true, true, false);
        GlStateManager.viewport(0, 0, width, height);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GL30.glBlitFramebuffer(
                0,
                0,
                target.framebufferWidth,
                target.framebufferHeight,
                0,
                0,
                width,
                height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.depthMask(true);
        GL11.glColorMask(true, true, true, true);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        TextureBinder.restoreDefaultTextureUnit();
    }

    public ShaderProgram getProgram(RenderPass pass) {
        PipelineProgram program = programs.get(pass);
        return program != null ? program.effectiveProgram(programs) : null;
    }

    private ShaderProgram activeProgram() {
        if (activePass == null) {
            return null;
        }
        return getProgram(activePass);
    }

    public PingPongManager getPingPongManager() {
        return pingPongManager;
    }

    public int getShadowDepthTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthTextureId() : -1;
    }

    public int getShadowDepthSnapshotTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthSnapshotTextureId() : -1;
    }

    public int getShadowColor0Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId() : -1;
    }

    public void configureShadowDepthTextureCompareMode() {
        if (shadowFramebuffer != null) {
            shadowFramebuffer.configureDepthTextureCompareMode();
        }
    }

    public void setActive(boolean active) {
        isPipelineActive = active && pingPongManager.isInitialized();
        if (isPipelineActive) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null) {
                resizeFramebuffer(mc.displayWidth, mc.displayHeight, true);
            }
        } else {
            resetPipelineState();
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    private void resetPipelineState() {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        passStack.clear();
        currentEntityId = 0;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        for (int i = 0; i < 8; i++) {
            GL30.glDisablei(GL11.GL_BLEND, i);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        }
    }

    private void loadCustomTextures(ShaderPack pack, ShaderProperties properties) {
        Map<String, Integer> loadedByPath = new HashMap<>();
        Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures = new HashMap<>();
        Map<String, Integer> customUnitsBySampler = new HashMap<>();
        int[] nextCustomUnit = {com.l.ausm.impl.pipeline.shader.ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT};

        for (RenderPass pass : RenderPass.values()) {
            List<LoadedCustomTexture> textures = new ArrayList<>();
            for (var directive : packDirectives.textureDirectives().rawTexturesFor(pass.programId())) {
                int textureUnit = customUnitsBySampler.computeIfAbsent(directive.samplerName(), ignored -> nextCustomUnit[0]++);
                try {
                    ShaderTextureLoader.RawTexture rawTexture = loadedRawTextures.computeIfAbsent(directive, raw -> {
                        try {
                            return ShaderTextureLoader.loadRawTexture(pack, raw);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    textures.add(new LoadedCustomTexture(
                            directive.samplerName(),
                            directive.replacementSamplerName(),
                            directive.resourcePath(),
                            textureUnit,
                            rawTexture.textureId(),
                            rawTexture.textureTarget()
                    ));
                } catch (UncheckedIOException e) {
                    MainMod.LOGGER.warn("[ShaderTextures] Failed to load raw {}", directive.resourcePath(), e.getCause());
                }
            }
            for (var binding : packDirectives.textureDirectives().texturesFor(pass.programId())) {
                int textureUnit = TextureBinder.textureUnitForSampler(binding.samplerName());
                if (textureUnit < 0) {
                    textureUnit = customUnitsBySampler.computeIfAbsent(binding.samplerName(), ignored -> nextCustomUnit[0]++);
                }

                try {
                    int textureId = loadedByPath.computeIfAbsent(binding.resourcePath(), path -> {
                        try {
                            return ShaderTextureLoader.loadTexture(pack, path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, textureId, GL11.GL_TEXTURE_2D));
                    MainMod.LOGGER.debug(
                            "[ShaderTextures] Prepared {} for sampler '{}' on unit {} in pass {} as texture {}",
                            binding.resourcePath(),
                            binding.samplerName(),
                            textureUnit,
                            pass.getProgramName(),
                            textureId
                    );
                } catch (UncheckedIOException e) {
                    MainMod.LOGGER.warn("[ShaderTextures] Failed to load {}", binding.resourcePath(), e.getCause());
                }
            }
            if (!textures.isEmpty()) {
                customTextures.put(pass, List.copyOf(textures));
            }
        }
    }

    private void bindCustomTextures(RenderPass pass, ShaderProgram program) {
        List<LoadedCustomTexture> textures = customTextures.get(pass);
        if (textures == null || textures.isEmpty()) {
            return;
        }

        for (LoadedCustomTexture texture : textures) {
            TextureBinder.bindTexture(texture.textureTarget(), texture.textureUnit(), texture.textureId());
            int location = program.getUniformLocation(texture.samplerName());
            if (location != -1) {
                OpenGlHelper.glUniform1i(location, texture.textureUnit());
            }
            if (!texture.replacementSamplerName().equals(texture.samplerName())) {
                int replacementLocation = program.getUniformLocation(texture.replacementSamplerName());
                if (replacementLocation != -1) {
                    OpenGlHelper.glUniform1i(replacementLocation, texture.textureUnit());
                }
            }
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void deleteCustomTextures() {
        customTextures.values().stream()
                .flatMap(List::stream)
                .mapToInt(LoadedCustomTexture::textureId)
                .distinct()
                .forEach(GL11::glDeleteTextures);
        customTextures.clear();
    }

    private record LoadedCustomTexture(String samplerName, String replacementSamplerName, String resourcePath, int textureUnit, int textureId, int textureTarget) {
    }

    private static ShaderProperties emptyShaderProperties() {
        return new ShaderProperties(
                Map.of(),
                Map.of(),
                com.l.ausm.api.pipeline.pack.ShaderOptions.empty(),
                Map.of(),
                Map.of(),
                ShaderRenderTargetSettings.empty(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new ShaderBlockIdMap.BlockIdRules(Map.of(), List.of()),
                Map.of(),
                Map.of(),
                com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                ShaderTextureDirectives.empty(),
                CustomUniformSet.empty(),
                new ShaderPackDirectives(
                        ShaderRenderTargetSettings.empty(),
                        com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                        ShaderTextureDirectives.empty(),
                        ShaderComputeDirectives.empty(),
                        List.of(),
                        Map.of(),
                        ShaderFeatureSet.empty(),
                        256,
                        ShaderPipelineCapabilities.from(new ShaderPackDirectives(
                                ShaderRenderTargetSettings.empty(),
                                com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                                ShaderTextureDirectives.empty(),
                                ShaderComputeDirectives.empty(),
                                List.of(),
                                Map.of(),
                                ShaderFeatureSet.empty(),
                                256,
                                null,
                                Map.of(),
                                CustomUniformSet.empty()
                        )),
                        Map.of(),
                        CustomUniformSet.empty()
                ),
                Map.of()
        );
    }
}
