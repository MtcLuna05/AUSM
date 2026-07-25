package com.l.ausm.impl.pipeline;

import static com.l.ausm.impl.pipeline.PipelineCompatConstants.*;
import static com.l.ausm.impl.pipeline.PipelineDistantHorizonsConstants.*;
import static com.l.ausm.impl.pipeline.PipelineLightConstants.*;
import static com.l.ausm.impl.pipeline.PipelinePresentationConstants.*;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.*;
import static com.l.ausm.impl.pipeline.PipelineRenderConstants.*;
import static com.l.ausm.impl.pipeline.PipelineSkyConstants.*;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.*;
import static com.l.ausm.impl.pipeline.PipelineGlState.*;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.*;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.client.ShaderLoadingScreen;
import com.l.ausm.impl.client.ThaumcraftParticleBridge;
import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.bloom.BloomExtractionPlan;
import com.l.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.GpomFramedMaterialCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.l.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.l.ausm.impl.pipeline.compat.ShaderlessNothiriumFogGuard;
import com.l.ausm.impl.pipeline.dh.DistantHorizonsInternalShaders;
import com.l.ausm.impl.pipeline.dh.DistantHorizonsMatrixState;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.fbo.PingPongManager;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.impl.mixin.pipeline.ViewFrustumAccessor;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderItemIdMap;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.pack.ShaderFeatureValidator;
import com.l.ausm.impl.pipeline.pack.ShaderEnvironmentDefines;
import com.l.ausm.impl.pipeline.pack.ShaderExpressionEvaluator;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.pack.PipelineShaderSettings;
import com.l.ausm.impl.pipeline.resource.ShaderImageSet;
import com.l.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.impl.pipeline.render.FullscreenQuad;
import com.l.ausm.impl.pipeline.render.IrisLightmapTexture;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import com.l.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.ComputeProgram;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
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
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.ConcurrentLongSet;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.ListChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.VboChunkFactory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBTessellationShader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The central hub for the active render pipeline.
 * Replaces the monolithic Shaders class with a cleaner context object.
 */
abstract class PipelineRuntimeState {
    protected static final PipelineContext INSTANCE = new PipelineContext();
    protected static final ICamera ALWAYS_VISIBLE_CAMERA = createAlwaysVisibleCamera();
    protected static final FloatBuffer IRIS_LIGHTMAP_TEXTURE_MATRIX = PipelineFrameValues.createIrisLightmapTextureMatrix();
    protected static final boolean FRAMED_BLOCK_DIAGNOSTICS_ENABLED =
            MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS > 0
                    || MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS > 0
                    || MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS > 0;
    protected static final boolean CURRENT_PROBLEM_PROBES_ENABLED = MAX_CURRENT_PROBLEM_PROBE_LOGS > 0;
    protected static boolean celeritasShadowCameraWarningLogged;
    protected volatile boolean shaderlessVoidWorldSkyLightEligible;

    private static ICamera createAlwaysVisibleCamera() {
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

    protected final PingPongManager pingPongManager = new PingPongManager();
    protected final IrisLightmapTexture irisLightmapTexture = new IrisLightmapTexture();
    protected final Map<RenderPass, PipelineProgram> programs = new EnumMap<>(RenderPass.class);
    protected final PipelineCustomTextures customTextures = new PipelineCustomTextures();
    protected final UniformRegistry uniformRegistry = new UniformRegistry();
    protected final Map<String, float[]> customUniformScalarScratch = new HashMap<>();
    protected ShadowFramebuffer shadowFramebuffer;
    protected ShaderProperties shaderProperties = emptyShaderProperties();
    protected ShaderPackDirectives packDirectives = emptyShaderProperties().packDirectives();
    protected ShaderProgramSet programSet;
    protected ShaderMap shaderMap;
    protected ShaderImageSet shaderImages = ShaderImageSet.empty();
    protected ShaderStorageBufferSet shaderStorageBuffers = ShaderStorageBufferSet.empty();
    protected final ConcurrentMap<Long, BlockPos> syntheticLightCandidates = new ConcurrentHashMap<>();
    protected final Set<String> coloredLightAuditKeys = ConcurrentHashMap.newKeySet();
    protected final AtomicInteger coloredLightAuditCount = new AtomicInteger();
    protected final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays = new EnumMap<>(ProgramArrayId.class);
    protected List<ComputeProgram> shadowComputePrograms = List.of();
    protected List<ComputeProgram> finalComputePrograms = List.of();
    protected final Map<ProgramArrayId, FullscreenProgramArray> fullscreenProgramArrays = new EnumMap<>(ProgramArrayId.class);
    protected final Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms = new EnumMap<>(ProgramArrayId.class);
    protected final Map<RenderGlobal, Map<World, ViewFrustum>> vanillaViewFrustums = new IdentityHashMap<>();
    protected final Map<RenderGlobal, Map<World, Integer>> vanillaViewFrustumRenderDistances = new IdentityHashMap<>();
    protected final Map<ViewFrustum, Long> vanillaViewFrustumChunkPositionKeys = new IdentityHashMap<>();
    protected final Deque<Object[]> vanillaViewFrustumStateStack = new ArrayDeque<>();
    protected final Map<String, CompiledPipelineState> compiledPipelineCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompiledPipelineState> eldest) {
            if (size() <= COMPILED_PIPELINE_CACHE_LIMIT) {
                return false;
            }
            eldest.getValue().delete();
            MainMod.LOGGER.info("[Pipeline] Evicted cached compiled shader programs: {}", eldest.getKey());
            return true;
        }
    };
    protected final NothiriumShadowRenderer nothiriumShadowRenderer = new NothiriumShadowRenderer();
    protected final AusmBloomRenderer bloomRenderer = new AusmBloomRenderer();
    protected final PipelineBlockEmission blockEmission = new PipelineBlockEmission();
    protected final PipelineBloomResourceClassifier bloomResourceClassifier = new PipelineBloomResourceClassifier(bloomRenderer);
    protected final Set<ShaderChunkRefresh> pendingShaderChunkRefreshes = new LinkedHashSet<>();
    protected final Set<ClientChunkRenderRefresh> pendingClientChunkRenderRefreshes = new LinkedHashSet<>();
    protected final Map<WorldClient, Map<Long, ClientChunkRenderRefresh>> pendingClientChunkRenderRefreshLookupByWorld = new IdentityHashMap<>();
    protected final Map<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>> pendingClientChunkRenderRefreshesByWorld = new IdentityHashMap<>();
    protected final Map<WorldClient, Map<Long, Long>> recentlyCompletedClientChunkRenderRefreshes = new IdentityHashMap<>();
    protected long recentlyCompletedClientChunkRenderRefreshLastPruneFrame = Long.MIN_VALUE;
    protected final Set<String> blockcrafteryTransparencyProbeKeys = ConcurrentHashMap.newKeySet();
    protected final AtomicInteger blockcrafteryTransparencyProbeCount = new AtomicInteger();
    protected int pendingWorldLoadLightRecalculationAttempts = 0;
    protected int pendingWorldLoadLightRecalculationDelay = 0;
    protected int pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
    protected int pendingWorldTerrainRefreshAttempts = 0;
    protected int pendingWorldTerrainRefreshDelay = 0;
    protected int pendingWorldTerrainRefreshDimension = Integer.MIN_VALUE;
    protected boolean pendingWorldTerrainRendererReset = false;
    protected boolean pendingWorldTerrainFullRendererReset = false;
    protected boolean pendingWorldTerrainVanillaReload = false;
    protected String activeCompiledPipelineCacheKey;
    protected boolean celeritasShadowCameraResolved;
    protected Class<?> celeritasViewportProviderClass;
    protected Constructor<?> celeritasViewportConstructor;
    protected Constructor<?> celeritasVectorConstructor;
    protected Object celeritasAlwaysVisibleFrustum;

    protected final Deque<PassScope> passStack = new ArrayDeque<>();
    protected final Deque<Integer> renderedItemIdStack = new ArrayDeque<>();
    protected final Deque<Boolean> worldPassBypassStack = new ArrayDeque<>();
    protected final Deque<Long> worldPassSerialStack = new ArrayDeque<>();
    protected final Deque<Long> nothiriumPipelineTranslucentFrameStack = new ArrayDeque<>();
    protected final Deque<Long> nothiriumPipelineTranslucentWorldPassSerialStack = new ArrayDeque<>();
    protected final Deque<Boolean> untouchedBetterPortalsVanillaRendererStack = new ArrayDeque<>();
    protected RenderPass activePass = null;
    protected ShaderKey activeShaderKey = null;
    protected WorldRenderingPhase activePhase = WorldRenderingPhase.NONE;
    protected boolean activeProgramTessellated = false;
    protected boolean activeProgramGeometric = false;
    protected WorldRenderingPhase overridePhase = null;
    protected volatile boolean isPipelineActive = false;
    protected boolean shaderlessWorldPassActive = false;
    protected int vanillaParticleRecoveryFrames = 0;
    protected String activePackName = "(internal)";
    protected float centerDepth = 1.0f;
    protected float centerDepthSmooth = 1.0f;
    protected int centerDepthSmoothTexture = -1;
    protected int noiseTexture = -1;
    protected final FloatBuffer centerDepthTextureBuffer = BufferUtils.createFloatBuffer(1);
    protected final FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);
    protected final DistantHorizonsMatrixState distantHorizonsMatrices = new DistantHorizonsMatrixState();
    protected RenderPass currentDistantHorizonsPass = RenderPass.DH_TERRAIN;
    protected ShaderProgram currentDistantHorizonsProgram = null;
    protected boolean currentDistantHorizonsFallbackProgram = false;
    protected boolean renderingDistantHorizonsPresentation = false;
    protected Framebuffer distantHorizonsPresentationTarget = null;
    protected float latestDistantHorizonsPartialTicks = 0.0F;
    protected int distantHorizonsVertexArray = -1;
    protected int distantHorizonsFallbackProgramId = 0;
    protected int distantHorizonsFallbackCombinedMatrixUniform = -1;
    protected int distantHorizonsFallbackProjectionMatrixUniform = -1;
    protected int distantHorizonsFallbackModelViewMatrixUniform = -1;
    protected int distantHorizonsFallbackModelOffsetUniform = -1;
    protected int distantHorizonsFallbackWorldYOffsetUniform = -1;
    protected int distantHorizonsFallbackMircoOffsetUniform = -1;
    protected int distantHorizonsFallbackEarthRadiusUniform = -1;
    protected boolean distantHorizonsFallbackProgramFailed = false;
    protected int distantHorizonsFramebufferId = 0;
    protected int distantHorizonsColorTextureId = 0;
    protected int distantHorizonsDepthTextureId = 0;
    protected boolean distantHorizonsTexturesOwned = false;
    protected int distantHorizonsFramebufferWidth = 0;
    protected int distantHorizonsFramebufferHeight = 0;
    protected long distantHorizonsFramebufferClearFrame = Long.MIN_VALUE;
    protected int distantHorizonsTextureReadbackFramebufferId = 0;
    protected int distantHorizonsCompositeProgramId = 0;
    protected int distantHorizonsCompositeTextureUniform = -1;
    protected int distantHorizonsCompositeDepthUniform = -1;
    protected boolean distantHorizonsCompositeProgramFailed = false;
    protected boolean distantHorizonsFramebufferPendingComposite = false;
    protected int distantHorizonsDiagnosticLogs = 0;
    protected int terrainColorProbeLogs = 0;
    protected int finalColorProbeLogs = 0;
    protected int compositeChainProbeLogs = 0;
    protected int deferredBoundaryProbeLogs = 0;
    protected int fullscreenSamplerProbeLogs = 0;
    protected int preDeferredColorRestoreLogs = 0;
    protected int terrainGridProbeLogs = 0;
    protected int distantHorizonsColorProbeLogs = 0;
    protected int distantHorizonsPassColorProbeLogs = 0;
    protected int pausedPostRenderGlErrorLogs = 0;
    protected int directRecoveredWindowRefreshLogs = 0;
    protected int directPresentationTextureRefreshLogs = 0;
    protected int directPresentationSnapshotLogs = 0;
    protected int guiRecoveredBackgroundLogs = 0;
    protected int preFinalDirectPresentLogs = 0;
    protected boolean preDeferredColorSnapshotThisFrame = false;
    protected DeferredFramebuffer directRecoveredWindowSource = null;
    protected Attachment directRecoveredWindowAttachment = null;
    protected long directRecoveredWindowFrame = Long.MIN_VALUE;
    protected int directRecoveredWindowTargetWidth = 0;
    protected int directRecoveredWindowTargetHeight = 0;
    protected float directRecoveredWindowColorScale = 1.0F;
    protected int directPresentationTexture = -1;
    protected int directPresentationFbo = -1;
    protected int directPresentationWidth = 0;
    protected int directPresentationHeight = 0;
    protected boolean directPresentationValid = false;
    protected long directPresentationFrame = Long.MIN_VALUE;
    protected String directPresentationReason = "";
    protected final ByteBuffer distantHorizonsReadbackPixel = BufferUtils.createByteBuffer(4);
    protected final ByteBuffer terrainProbeColorPixel = BufferUtils.createByteBuffer(4);
    protected final FloatBuffer terrainProbeDepthPixel = BufferUtils.createFloatBuffer(1);
    protected final FloatBuffer guiModelMatrixProbe = BufferUtils.createFloatBuffer(16);
    protected final ByteBuffer terrainProbeBooleanBuffer = BufferUtils.createByteBuffer(16);
    protected int currentEntityId = 0;
    protected int currentRenderedItemId = -1;
    protected String currentRenderedItemDebugName = "";
    protected ResourceLocation currentEntityKey = null;
    protected float[] currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    protected final float[] currentAstralConstellationColor = new float[]{1.0f, 1.0f, 1.0f};
    protected final float[] currentAstralTierColor = new float[]{1.0f, 1.0f, 1.0f};
    protected float currentAstralSolarEclipseFactor;
    /** Shader-visible kind of the currently submitted modded sky detail. */
    protected int currentSkyDetailKind;
    protected float currentAlphaTestReference = 0.1f;
    protected float shadowMapDistance = 128.0f;
    protected float voxelDistance = 0.0f;
    protected float shadowDistanceRenderMul = -1.0f;
    protected float shadowIntervalSize = 2.0f;
    protected float sunPathRotation = 0.0f;
    protected float centerDepthHalfLife = 1.0f;
    protected float eyeBrightnessHalfLife = 3.0f;
    protected float wetnessHalfLife = 600.0f;
    protected float drynessHalfLife = 200.0f;
    protected final float[] eyeBrightnessSmooth = new float[]{0.0f, 0.0f};
    protected boolean eyeBrightnessSmoothInitialized = false;
    protected float wetnessSmooth = 0.0f;
    protected boolean wetnessSmoothInitialized = false;
    protected final float[] endFlashPosition = {0.0f, 0.0f, 0.0f};
    protected float endFlashIntensity = 0.0f;
    protected float previousEndFlashIntensity = 0.0f;
    protected float endFlashYawDegrees = 0.0f;
    protected float endFlashPitchDegrees = 0.0f;
    protected boolean shadowPolygonOffset = true;
    protected float shadowPolygonOffsetFactor = 1.1f;
    protected float shadowPolygonOffsetUnits = 4.0f;
    protected int shadowFrameCount = 1_000_000;
    protected long lastShadowFrameId = -1L;
    protected int lastShadowRenderDimensionId = Integer.MIN_VALUE;
    protected long lastShadowRenderWorldTime = Long.MIN_VALUE;
    protected double lastShadowRenderX = Double.NaN;
    protected double lastShadowRenderY = Double.NaN;
    protected double lastShadowRenderZ = Double.NaN;
    protected long pipelineFrameId = 0L;
    protected int worldLoadPresentationGuardFrames = 0;
    protected World cpuLightTileEntitySnapshotWorld = null;
    protected long cpuLightTileEntitySnapshotFrame = Long.MIN_VALUE;
    protected List<TileEntity> cpuLightTileEntitySnapshot = Collections.emptyList();
    protected int cpuLightTileEntityScanCursor = 0;
    protected final int[] cpuLightProjectRedVoxelIds = new int[8];
    protected final Set<Long> cpuLightWrittenVoxels = new HashSet<>();
    protected int coloredLightInjectionProbeLogs = 0;
    protected int heldColoredLightProbeLogs = 0;
    protected World shadowBlockEntityBoundsCacheWorld = null;
    protected final Map<TileEntity, ShadowBlockEntityBounds> shadowBlockEntityBoundsCache = new IdentityHashMap<>();
    protected World cpuLightBlockScanWorld = null;
    protected int cpuLightBlockScanCursor = 0;
    protected final long pipelineStartNanos = System.nanoTime();
    protected long lastPipelineFrameNanos = pipelineStartNanos;
    protected float currentFrameTime = 0.016f;
    protected int textureReloadCount = 0;
    protected float currentChunkFade = 1.0f;
    protected long chunkFadeWarmupUntilFrame = 0L;
    protected final Map<ChunkFadeKey, ChunkFadeState> chunkFadeStates = new LinkedHashMap<>();
    protected boolean terrainRebuiltDuringLastInitialization = false;
    protected boolean terrainCacheReusableDuringLastInitialization = false;
    protected float frameTimeCounter = 0.0f;
    protected float frameTimeSmooth = 0.016f;
    protected boolean frameTimeSmoothInitialized = false;
    protected final float[] cameraPosition = {0.0f, 0.0f, 0.0f};
    protected final float[] previousCameraPosition = {0.0f, 0.0f, 0.0f};
    protected final double[] cameraPositionUnshifted = {0.0, 0.0, 0.0};
    protected final double[] previousCameraPositionUnshifted = {0.0, 0.0, 0.0};
    protected double cameraShiftX = 0.0;
    protected double cameraShiftZ = 0.0;
    protected boolean temporalHistoryInitialized = false;
    protected int temporalHistoryDimensionId = Integer.MIN_VALUE;
    protected float previousTemporalYaw = 0.0f;
    protected float previousTemporalPitch = 0.0f;
    protected float accumulatedTemporalYaw = 0.0f;
    protected float accumulatedTemporalPitch = 0.0f;
    protected int temporalHistoryResetLogs = 0;
    protected String temporalHistoryResetReason = "";
    protected float temporalHistoryResetVelocity = 0.0f;
    protected float temporalHistoryResetYaw = 0.0f;
    protected float temporalHistoryResetPitch = 0.0f;
    protected int mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
    protected boolean pendingPersistentHistoryClear = false;
    protected String pendingPersistentHistoryClearReason = "";
    protected int persistentHistoryClearLogs = 0;
    protected long terrainLayerCountFrame = Long.MIN_VALUE;
    protected int terrainOpaqueLayerCount = 0;
    protected int terrainOpaqueDrawCount = 0;
    protected int hardwareCapabilityLogs = 0;
    protected int hardwareTerrainFallbackLogs = 0;
    protected int zeroOpaqueTerrainFrames = 0;
    protected int sparseOpaqueTerrainFrames = 0;
    protected boolean hardwareSafeVanillaTerrain = false;
    protected String hardwareSafeVanillaTerrainReason = "";
    protected boolean softVanillaTerrainRenderer = false;
    protected String softVanillaTerrainRendererReason = "";
    protected boolean shaderedNothiriumGlobalBypass = false;
    protected String shaderedNothiriumGlobalBypassReason = "";
    protected int shaderedNothiriumGlobalBypassLogs = 0;
    protected World shaderedNothiriumGlobalBypassPrimedWorld = null;
    protected RenderGlobal shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
    protected int positiveVanillaTerrainProbeLogs = 0;
    protected int positiveNothiriumTerrainProbeLogs = 0;
    protected boolean zeroOpaqueTerrainRecoveryRequested = false;
    protected int hardwareSafeVanillaTerrainRefreshCooldown = 0;
    protected int compositeInvalidFallbackFrames = 0;
    protected long compositeInvalidFallbackSnapshotFrame = Long.MIN_VALUE;
    protected boolean compositeInvalidFallbackSnapshotHasScene = false;
    protected int compositeInvalidFallbackLogs = 0;
    protected int compositeInvalidRestoreLogs = 0;
    protected int sparseStartupPresentationHoldLogs = 0;
    protected int sparseStartupPresentationHoldFrames = 0;
    protected int softVanillaPresentationProbeLogs = 0;
    protected int softVanillaLayerTimingLogs = 0;
    protected int softVanillaSpecialBlockProbeLogs = 0;
    protected int softVanillaFrameTimingLogs = 0;
    protected long currentWorldFrameStartNanos = Long.MIN_VALUE;
    protected long currentWorldFrameReadyNanos = Long.MIN_VALUE;
    protected long currentWorldFrameFinishStartNanos = Long.MIN_VALUE;
    protected long currentWorldFrameAfterNativeBloomNanos = Long.MIN_VALUE;
    protected long currentWorldFrameBlitStartNanos = Long.MIN_VALUE;
    protected int nothiriumHybridVanillaMaintenanceFrames = 0;
    protected int nothiriumHybridVanillaMaintenanceLogs = 0;
    protected String nothiriumHybridVanillaMaintenanceReason = "";
    protected int nothiriumMainVanillaDrawPathFrames = 0;
    protected int nothiriumMainVanillaDrawPathLogs = 0;
    protected String nothiriumMainVanillaDrawPathReason = "";
    protected int nothiriumMainSetupBridgeLogs = 0;
    protected long nothiriumShaderedMainSetupFrame = Long.MIN_VALUE;
    protected long nothiriumSparseMainTerrainFrame = Long.MIN_VALUE;
    protected long nothiriumShaderedMainPostCompileSetupFrame = Long.MIN_VALUE;
    protected long nothiriumProviderSupplementCompileFrame = Long.MIN_VALUE;
    protected int nothiriumProviderSupplementCompileLayerMask = 0;
    protected long nothiriumNonSolidRepairCutoutMippedFrame = Long.MIN_VALUE;
    protected long nothiriumNonSolidRepairCutoutFrame = Long.MIN_VALUE;
    protected long nothiriumNonSolidRepairTranslucentFrame = Long.MIN_VALUE;
    protected long nothiriumSparseMainRepairFrame = Long.MIN_VALUE;
    protected long nothiriumNonSolidProviderDrawCutoutMippedUntilFrame = Long.MIN_VALUE;
    protected long nothiriumNonSolidProviderDrawCutoutUntilFrame = Long.MIN_VALUE;
    protected long nothiriumNonSolidProviderDrawTranslucentUntilFrame = Long.MIN_VALUE;
    protected long nothiriumSparseMainProviderDrawUntilFrame = Long.MIN_VALUE;
    protected int nothiriumNonSolidRepairLogs = 0;
    protected int nothiriumSparseMainRepairLogs = 0;
    protected int nothiriumNonSolidProviderDrawLogs = 0;
    protected int nothiriumSparseMainProviderDrawLogs = 0;
    protected World lastHardwareSafeVanillaTerrainRefreshWorld = null;
    protected int lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
    protected int lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
    protected boolean lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
    protected boolean pipelineTerrainFormatSupported = false;
    protected boolean deferredPassesRenderedThisFrame = false;
    protected boolean preparePassesRenderedBeforeShadowThisFrame = false;
    protected boolean preTranslucentDepthCopiedThisFrame = false;
    protected boolean preHandDepthCopiedThisFrame = false;
    protected boolean setupComputePending = false;
    protected boolean terrainCullOverrideActive = false;
    protected boolean previousTerrainCullEnabled = true;
    protected boolean terrainOcclusionOverrideActive = false;
    protected boolean previousRenderChunksManyForOcclusion = true;
    protected boolean nothiriumPipelineBlockFormatActive = false;
    protected boolean worldFrameActive = false;
    protected Framebuffer externalWorldFramebufferTarget = null;
    protected boolean renderingShadowMap = false;
    protected boolean renderingGui = false;
    protected long guiTargetContentFrame = Long.MIN_VALUE;
    protected boolean shadowMapPopulated = false;
    protected boolean shadowMapUsable = false;
    protected boolean shadowMapSparseForSampling = false;
    protected int shadowMapCoverageStableFrames = 0;
    protected int nothiriumShadowInvalidFrames = 0;
    protected int nothiriumShadowSuppressedFrames = 0;
    protected int nothiriumShadowSuppressionLogs = 0;
    protected int nothiriumShadowVerticalHoldFrames = 0;
    protected int nothiriumShadowVerticalHoldLogs = 0;
    protected World pendingBetterPortalsPortalBlockWorld;
    protected BlockPos pendingBetterPortalsPortalBlockPos;
    protected IBlockState pendingBetterPortalsPortalBlockOldState;
    protected IBlockState pendingBetterPortalsPortalBlockNewState;
    protected int pendingBetterPortalsPortalBlockRefreshDelay = -1;
    protected int pendingBetterPortalsPortalBlockChangeCount = 0;
    protected World lastBetterPortalsPortalBlockRefreshWorld;
    protected BlockPos lastBetterPortalsPortalBlockRefreshPos;
    protected int lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
    protected long lastBetterPortalsPortalBlockRefreshMillis = 0L;
    protected RenderGlobal activeVanillaViewFrustumRenderGlobal = null;
    protected World activeVanillaViewFrustumWorld = null;
    protected int activeVanillaViewFrustumRenderDistanceChunks = -1;
    protected boolean betterPortalsViewFrustumUpdateWarningLogged = false;
    protected int cameraFrustumSyncLogs = 0;
    protected int clientChunkRenderRefreshLogs = 0;
    protected World lastCameraFrustumSyncWorld = null;
    protected ViewFrustum lastCameraFrustumSyncViewFrustum = null;
    protected int lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
    protected int lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
    protected int lastStableMainWorldVanillaRenderDistanceChunks = -1;
    protected int lastObservedRenderDistanceChunks = -1;
    protected World lastTerrainTransitionWorld = null;
    protected int lastTerrainTransitionDimension = Integer.MIN_VALUE;
    protected long lastTerrainTransitionMillis = 0L;
    protected boolean betterPortalsChunkUpdateWarningLogged = false;
    protected boolean shadowHealthLogged = false;
    protected int shadowHealthLogAttempts = 0;
    protected int shadowMapInvalidLogs = 0;
    protected int shadowMapSuppressedLogs = 0;
    protected int guiRenderDepth = 0;
    protected int guiEntityPreviewStateDepth = 0;
    protected int guiModelStateProbeLogs = 0;
    protected int guiEntityStateProbeLogs = 0;
    protected int guiItemModelProbeLogs = 0;
    protected final Deque<String> guiItemProbeNames = new ArrayDeque<>();
    protected int waterRoutingProbeLogs = 0;
    protected int waterAttachmentDeltaProbeLogs = 0;
    protected int specialLayerProbeLogs = 0;
    protected int pipelinePassProbeLogs = 0;
    protected int layerOutputProbeLogs = 0;
    protected int shadowTargetProbeLogs = 0;
    protected final ByteBuffer[] waterAttachmentBefore = new ByteBuffer[2];
    protected final ByteBuffer[] waterAttachmentAfter = new ByteBuffer[2];
    protected final int[] waterAttachmentProbeWidths = new int[2];
    protected final int[] waterAttachmentProbeHeights = new int[2];
    protected boolean waterAttachmentDeltaProbeActive = false;
    protected int handItemDrawStateLogs = 0;
    protected int handGbufferProbeLogs = 0;
    protected int handPassBindLogs = 0;
    protected int shaderlessLightStateProbeLogs = 0;
    protected int shaderlessSkyGuiWorldProbeLogs = 0;
    protected int shaderlessSkyGuiScreenProbeLogs = 0;
    protected int astralVoidSkyProbeLogs = 0;
    protected int shaderlessAstralSkyColorLogs = 0;
    protected int freshSkyProbeLogs = 0;
    protected int freshSkyGuiProbeLogs = 0;
    protected int shaderedVoidSkyProbeLogs = 0;
    protected int shaderedVoidSkyTargetProbeLogs = 0;
    protected int shaderedVoidSkyAttachmentProbeLogs = 0;
    protected int skyPresentationRouteProbeLogs = 0;
    protected int ownedSkyBackingProbeLogs = 0;
    protected int ownedSkyBackingDecisionProbeLogs = 0;
    protected int directColorPresentLogs = 0;
    protected int directWindowPresentLogs = 0;
    protected int directF1WindowPresentLogs = 0;
    protected int presentationBoundaryLogs = 0;
    protected int skyDomeProbeLogs = 0;
    protected int skyDomeGuiProbeLogs = 0;
    protected int skyDomePauseProbeLogs = 0;
    protected int worldPassSkyDomeProbeLogs = 0;
    protected int worldPassSkyDomeGuiProbeLogs = 0;
    protected int worldPassSkyDomePauseProbeLogs = 0;
    protected int shaderlessSolidTerrainSkyProbeLogs = 0;
    protected int shaderlessSolidTerrainSkyGuiProbeLogs = 0;
    protected int shaderlessSolidTerrainSkyPauseProbeLogs = 0;
    protected int voidSkyStageProbeLogs = 0;
    protected int nothiriumFogProbeLogs = 0;
    protected int nothiriumRenderProbeLogs = 0;
    protected int nothiriumFogGuardLogs = 0;
    protected final ShaderlessNothiriumFogGuard shaderlessNothiriumFogGuard = new ShaderlessNothiriumFogGuard();
    protected int shaderlessVoidLightRepairLogs = 0;
    protected int shaderlessVoidSkyPixelProbeLogs = 0;
    protected int shaderlessVoidSkyRepairLogs = 0;
    protected int shaderlessVoidVanillaLowerSkyLogs = 0;
    protected int shaderlessWorldFramebufferHandoffLogs = 0;
    protected int shaderlessSkyRgbFillLogs = 0;
    protected int shaderlessSkyRgbFillGuiLogs = 0;
    protected int shaderlessSkyRgbFillPauseLogs = 0;
    protected int shaderlessLowerSkyMeshLogs = 0;
    protected int shaderlessLowerSkyMeshGuiLogs = 0;
    protected int shaderlessLowerSkyMeshPauseLogs = 0;
    protected int voidWorldSkyRendererChainLogs = 0;
    protected int voidWorldSkyRendererChainGuiLogs = 0;
    protected int voidWorldSkyRendererChainPauseLogs = 0;
    protected int hiddenSkyFramebufferProbeLogs = 0;
    protected int hiddenF1SkyFramebufferProbeLogs = 0;
    protected int shaderlessWorldFramebufferForUi = 0;
    protected int shaderlessWorldFramebufferWidth = 0;
    protected int shaderlessWorldFramebufferHeight = 0;
    protected long shaderlessWorldFramebufferFrame = Long.MIN_VALUE;
    protected Vec3d lastShaderlessAstralVoidSkyColor = null;
    protected boolean shaderlessTerrainLightmapCoordsSaved = false;
    protected float shaderlessTerrainPreviousLightmapX = 0.0F;
    protected float shaderlessTerrainPreviousLightmapY = 0.0F;
    protected int inactiveSkyPipelineProbeLogs = 0;
    protected int activeSkyPipelineProbeLogs = 0;
    protected int finalSkyRepairProbeLogs = 0;
    protected int vanillaRecoveryFrames = 0;
    protected int pendingBloomTerrainRefreshAttempts = 0;
    protected int pendingBloomTerrainRefreshDelay = 0;
    protected String pendingBloomTerrainRefreshReason = "";
    protected boolean runningBloomTerrainRefresh = false;
    protected int bloomZeroGeometryFrames = 0;
    protected int bloomZeroGeometryRefreshCooldown = 0;
    protected long clientRenderFrameNanos = Long.MIN_VALUE;
    protected boolean shaderlessCustomSkyBackingThisFrame = false;
    protected int currentWorldPass = 0;
    protected float currentWorldPartialTicks = 0.0F;
    protected boolean bloomLayerRenderedThisWorldPass = false;
    protected boolean bloomLayerRenderedThisWorldFrame = false;
    protected boolean shaderlessStyleBloomRenderedThisWorldPass = false;
    protected boolean shaderlessStyleBloomRenderedThisWorldFrame = false;
    protected boolean pendingDeferredNativeBloom = false;
    protected double pendingDeferredBloomPartialTicks = 0.0D;
    protected int pendingDeferredBloomPass = 0;
    protected int betterPortalsPipelineLogs = 0;
    protected int shaderlessBloomHookLogs = 0;
    protected int visibleBloomDiagLogs = 0;
    protected int worldLayerDiagLogs = 0;
    protected int externalOverlayLogs = 0;
    protected int renderGlobalLoadRendererLogs = 0;
    protected int vanillaTerrainRendererCreationLogs = 0;
    protected int inactiveBetterPortalsTerrainSkipLogs = 0;
    protected int terrainDiagnosticLogs = 0;
    protected int steadyVanillaTerrainDiagnosticLogs = 0;
    protected int shaderlessNothiriumLoadRendererReloadLogs = 0;
    protected final Set<String> decoratedLightAuditKeys = ConcurrentHashMap.newKeySet();
    protected final AtomicInteger decoratedLightAuditCount = new AtomicInteger();
    protected final Set<String> framedBlockDiagnosticKeys = ConcurrentHashMap.newKeySet();
    protected final AtomicInteger blockcrafteryDiagnosticCount = new AtomicInteger();
    protected final AtomicInteger blockcrafteryBloomDecisionProbeCount = new AtomicInteger();
    protected final AtomicInteger framedQuadMaterialProbeCount = new AtomicInteger();
    protected final AtomicInteger architectureCraftDiagnosticCount = new AtomicInteger();
    protected final AtomicInteger framedPriorityDiagnosticCount = new AtomicInteger();
    protected final Set<String> currentProblemProbeKeys = ConcurrentHashMap.newKeySet();
    protected final Set<String> softVanillaSpecialBlockProbeKeys = ConcurrentHashMap.newKeySet();
    protected final ConcurrentLongSet shaderlessBloomMetadataKnownChunkLayers = new ConcurrentLongSet();
    protected final ConcurrentLongSet shaderlessBloomMetadataChunkLayers = new ConcurrentLongSet();
    protected final AtomicInteger currentProblemProbeCount = new AtomicInteger();
    protected final AtomicInteger activeLightOrIdProbeCount = new AtomicInteger();
    protected final AtomicInteger waterLikeMaterialProbeCount = new AtomicInteger();
    protected long lastShaderlessNothiriumLoadRendererReloadMillis = 0L;
    protected int lastShaderlessNothiriumLoadRendererReloadDimension = Integer.MIN_VALUE;
    protected long nextWorldPassSerial = 0L;
    protected long currentWorldPassSerial = Long.MIN_VALUE;
    protected long nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;
    protected long nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;
    protected long nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
    protected boolean shaderlessBloomRenderedThisWorldPass = false;
    protected boolean shaderlessBloomRenderedThisWorldFrame = false;
    protected boolean shaderlessBloomVertexFormatRefreshRequested = false;
    protected boolean shaderlessBloomExtractionActive = false;
    protected boolean shaderlessBloomExtractionBootstrapActive = false;
    protected int shaderlessTerrainSolidCount = -1;
    protected int shaderlessTerrainCutoutMippedCount = -1;
    protected int shaderlessTerrainCutoutCount = -1;
    protected int shaderlessTerrainTranslucentCount = -1;
    protected int shaderlessTerrainBloomCount = -1;
    protected final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);

    protected PipelineRuntimeState() {
        registerBaseUniforms();
    }

    protected static final class PassScope {
        private final boolean bound;
        private final RenderPass previousPass;
        private final ShaderKey previousShaderKey;
        private final WorldRenderingPhase previousPhase;
        private final boolean previousProgramTessellated;
        private final boolean previousProgramGeometric;

        private PassScope(boolean bound, RenderPass previousPass, ShaderKey previousShaderKey, WorldRenderingPhase previousPhase, boolean previousProgramTessellated, boolean previousProgramGeometric) {
            this.bound = bound;
            this.previousPass = previousPass;
            this.previousShaderKey = previousShaderKey;
            this.previousPhase = previousPhase;
            this.previousProgramTessellated = previousProgramTessellated;
            this.previousProgramGeometric = previousProgramGeometric;
        }

        private boolean bound() {
            return bound;
        }

        private RenderPass previousPass() {
            return previousPass;
        }

        private ShaderKey previousShaderKey() {
            return previousShaderKey;
        }

        private WorldRenderingPhase previousPhase() {
            return previousPhase;
        }

        private boolean previousProgramTessellated() {
            return previousProgramTessellated;
        }

        private boolean previousProgramGeometric() {
            return previousProgramGeometric;
        }
    }

    protected static final class CompiledPipelineState {
        private final ShaderProgramSet programSet;
        private final ShaderMap shaderMap;
        private final Map<RenderPass, PipelineProgram> programs;
        private final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays;
        private final List<ComputeProgram> shadowComputePrograms;
        private final List<ComputeProgram> finalComputePrograms;
        private final Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms;

        private CompiledPipelineState(
                ShaderProgramSet programSet,
                ShaderMap shaderMap,
                Map<RenderPass, PipelineProgram> programs,
                Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays,
                List<ComputeProgram> shadowComputePrograms,
                List<ComputeProgram> finalComputePrograms,
                Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms
        ) {
            this.programSet = programSet;
            this.shaderMap = shaderMap;
            this.programs = new EnumMap<>(programs);
            this.computeProgramArrays = new EnumMap<>(computeProgramArrays);
            this.shadowComputePrograms = List.copyOf(shadowComputePrograms);
            this.finalComputePrograms = List.copyOf(finalComputePrograms);
            this.fullscreenArrayPrograms = new EnumMap<>(fullscreenArrayPrograms);
        }

        private void delete() {
            programs.values().forEach(PipelineProgram::delete);
            computeProgramArrays.values().stream()
                    .flatMap(List::stream)
                    .forEach(ComputeProgram::delete);
            shadowComputePrograms.forEach(ComputeProgram::delete);
            finalComputePrograms.forEach(ComputeProgram::delete);
            fullscreenArrayPrograms.values().stream()
                    .flatMap(List::stream)
                    .forEach(FullscreenArrayProgram::delete);
        }
    }

    public static PipelineContext getInstance() {
        return INSTANCE;
    }

    public void beginFramedMaterialCompileCache() {
    }

    public void endFramedMaterialCompileCache() {
    }

    protected void registerBaseUniforms() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();

        // --- 1. Global / Engine Uniforms ---
        uniformRegistry.registerInt("worldTime", () -> {
            World world = renderWorld(mc);
            return world != null ? (int) (com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L) : 0;
        });

        uniformRegistry.registerFloat("viewWidth", () -> (float) worldTargetWidth(mc));
        uniformRegistry.registerFloat("viewHeight", () -> (float) worldTargetHeight(mc));
        uniformRegistry.registerFloat("pixelSizeX", () -> 1.0f / worldTargetWidth(mc));
        uniformRegistry.registerFloat("pixelSizeY", () -> 1.0f / worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatio", () -> (float) worldTargetWidth(mc) / (float) worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatioInverse", () -> (float) worldTargetHeight(mc) / (float) worldTargetWidth(mc));
        uniformRegistry.registerFloat("screenBrightness", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.fieldFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)), 0.0F, "field_74333_Y", "gammaSetting"));
        uniformRegistry.registerInt("hideGUI", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)) ? 1 : 0);
        uniformRegistry.registerInt("ausmGuiScreen", () -> renderingGuiScreen() ? 1 : 0);
        uniformRegistry.registerInt("isRightHanded", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.field((com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)), net.minecraft.util.EnumHandSide.class, net.minecraft.util.EnumHandSide.RIGHT, "field_186715_A", "mainHand") == EnumHandSide.RIGHT ? 1 : 0);
        uniformRegistry.registerInt("firstPersonCamera", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.thirdPersonView(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)) == 0 ? 1 : 0);
        uniformRegistry.registerInt("currentColorSpace", () -> 0);
        uniformRegistry.registerFloat("near", () -> 0.05f);
        uniformRegistry.registerFloat("far", () -> shaderFarPlaneDistance(mc));
        uniformRegistry.registerFloat("fogStart", () -> effectiveFogStart(mc));
        uniformRegistry.registerFloat("fogEnd", () -> effectiveFogEnd(mc));
        uniformRegistry.registerFloat("fogDensity", () -> effectiveFogDensity(mc));
        uniformRegistry.registerFloat("iris_FogStart", () -> effectiveFogStart(mc));
        uniformRegistry.registerFloat("iris_FogEnd", () -> effectiveFogEnd(mc));
        uniformRegistry.registerFloat("iris_FogDensity", () -> Math.max(0.0f, effectiveFogDensity(mc)));
        uniformRegistry.registerInt("fogMode", () -> effectiveFogMode(mc));
        uniformRegistry.registerInt("fogShape", () -> 1);
        uniformRegistry.registerFloat("rainStrength", () -> rainStrength(mc));
        uniformRegistry.registerFloat("thunderStrength", () -> renderWorld(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldThunderStrength(renderWorld(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f);
        uniformRegistry.registerFloat("wetness", () -> wetnessSmooth);
        uniformRegistry.registerInt("biome", () -> currentBiomeExpressionId(mc));
        uniformRegistry.registerInt("biome_category", () -> currentBiomeCategory(mc));
        uniformRegistry.registerInt("biome_precipitation", () -> currentBiomePrecipitation(mc));
        uniformRegistry.registerFloat("rainfall", () -> currentBiomeRainfall(mc));
        uniformRegistry.registerFloat("temperature", () -> currentBiomeTemperature(mc));
        uniformRegistry.registerFloat("BiomeTemp", () -> currentBiomeTemperature(mc));
        uniformRegistry.registerInt("BIOME_NETHER_WASTES", () -> BIOME_NETHER_WASTES_ID);
        uniformRegistry.registerInt("BIOME_CRIMSON_FOREST", () -> BIOME_CRIMSON_FOREST_ID);
        uniformRegistry.registerInt("BIOME_WARPED_FOREST", () -> BIOME_WARPED_FOREST_ID);
        uniformRegistry.registerInt("BIOME_BASALT_DELTAS", () -> BIOME_BASALT_DELTAS_ID);
        uniformRegistry.registerInt("BIOME_SOUL_SAND_VALLEY", () -> BIOME_SOUL_SAND_VALLEY_ID);
        uniformRegistry.registerInt("BIOME_PALE_GARDEN", () -> BIOME_PALE_GARDEN_ID);
        uniformRegistry.registerFloat("blindness", () -> blindness(mc));
        uniformRegistry.registerFloat("darknessFactor", () -> 0.0f);
        uniformRegistry.registerFloat("darknessLightFactor", () -> 0.0f);
        uniformRegistry.registerInt("heavyFog", () -> blindness(mc) > 0.0f ? 1 : 0);
        uniformRegistry.registerFloat("nightVision", () -> nightVision(mc));
        uniformRegistry.registerFloat("blindFactor", () -> {
            float value = clamp01(blindness(mc) * 2.0f - 1.0f);
            return value * value;
        });
        uniformRegistry.registerInt("is_sneaking", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70093_af", "isSneaking"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_sprinting", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70051_ag", "isSprinting"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_hurt", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), 0, "field_70737_aN", "hurtTime") > 0 ? 1 : 0);
        uniformRegistry.registerInt("is_invisible", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_82150_aj", "isInvisible"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_burning", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70027_ad", "isBurning"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("is_on_ground", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), false, "field_70122_E", "onGround") ? 1 : 0);
        uniformRegistry.registerInt("isRiding", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_184218_aH", "isRiding"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("isElytraFlying", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_184613_cA", "isElytraFlying"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("feetInWater", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70090_H", "isInWater"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1 : 0);
        uniformRegistry.registerInt("inSwimmingAnimation", () -> 0);
        uniformRegistry.registerInt("vehicleInWater", () -> vehicleInWater(mc) ? 1 : 0);
        uniformRegistry.registerInt("vehicleId", () -> vehicleId(mc));
        uniformRegistry.registerFloat("sneakSmooth", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70093_af", "isSneaking"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("burningSmooth", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70027_ad", "isBurning"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("touchmybody", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), 0, "field_70737_aN", "hurtTime") > 0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("effectStrength", () -> 0.0f);
        uniformRegistry.registerFloat("playerMood", () -> 0.0f);
        uniformRegistry.registerFloat("constantMood", () -> 0.0f);
        uniformRegistry.registerFloat("starter", () -> 1.0f);
        uniformRegistry.registerFloat("eyeAltitude", () -> cameraPosition[1]);
        uniformRegistry.registerFloat("centerDepth", () -> centerDepth);
        uniformRegistry.registerFloat("centerDepthSmooth", () -> centerDepthSmooth);
        uniformRegistry.registerInt("iris_centerDepthSmooth", () -> TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        uniformRegistry.registerInt("moonPhase", () -> renderWorld(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((renderWorld(mc)), new String[] {"func_72853_d", "getMoonPhase"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0) : 0);
        uniformRegistry.registerInt("frameCounter", () -> (int) (pipelineFrameId % 720720L));
        uniformRegistry.registerInt("frameMod", () -> (int) (pipelineFrameId & 15L));
        uniformRegistry.registerFloat("framemod2", () -> (float) (pipelineFrameId & 1L));
        uniformRegistry.registerVec2("taaOffset", () -> taaOffset(mc));
        uniformRegistry.registerInt("worldDay", () -> {
            World world = renderWorld(mc);
            return world != null ? (int) (com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) / 24000L) : 0;
        });
        uniformRegistry.registerInt("isSpectator", () -> com.l.ausm.impl.util.MinecraftReflectionCompat.playerIsSpectator(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) ? 1 : 0);
        uniformRegistry.registerInt("seaLevel", () -> renderWorld(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((renderWorld(mc)), new String[] {"func_181545_F", "getSeaLevel"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 63) : 63);
        uniformRegistry.registerInt("bedrockLevel", () -> 0);
        uniformRegistry.registerInt("heightLimit", () -> renderWorld(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((renderWorld(mc)), new String[] {"func_72800_K", "getHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 256) : 256);
        uniformRegistry.registerInt("logicalHeightLimit", () -> renderWorld(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(renderWorld(mc), new String[] {"func_72940_L", "getActualHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(renderWorld(mc), new String[] {"func_72800_K", "getHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 256)) : 256);
        uniformRegistry.registerFloat("cloudHeight", () -> cloudHeight(mc));
        uniformRegistry.registerInt("hasCeiling", () -> isNetherRenderWorld(mc) ? 1 : 0);
        uniformRegistry.registerInt("hasSkylight", () -> hasSkylight(mc) ? 1 : 0);
        uniformRegistry.registerFloat("ambientLight", () -> isNetherRenderWorld(mc) ? 0.1f : 0.0f);
        uniformRegistry.registerFloat("isDry", () -> currentBiomePrecipitation(mc) == 0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isRainy", () -> currentBiomePrecipitation(mc) == 1 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isSnowy", () -> currentBiomePrecipitation(mc) == 2 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isPrecipitationRain", () -> currentBiomePrecipitation(mc) == 1 && cameraPositionUnshifted[1] < 96.0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("isEyeInCave", () -> isEyeInCave(mc) ? 1.0f : 0.0f);
        uniformRegistry.registerInt("renderStage", () -> getPhase().ordinal());
        uniformRegistry.registerFloat("mc_chunkFade", () -> ENABLE_CHUNK_FADE ? currentChunkFade : 1.0f);
        uniformRegistry.registerVec3("ausmAstralConstellationColor", () -> currentAstralConstellationColor.clone());
        uniformRegistry.registerVec3("ausmAstralTierColor", () -> currentAstralTierColor.clone());
        uniformRegistry.registerFloat("ausmAstralSolarEclipse", () -> currentAstralSolarEclipseFactor);
        uniformRegistry.registerInt("ausmSkyDetailKind", () -> currentSkyDetailKind);
        uniformRegistry.registerVec2i("ausmSkyDetailTextureSize", PipelineGlState::boundTextureSize);
        uniformRegistry.registerVec4("ausmVoidSkyParams", () -> new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        uniformRegistry.registerInt("ausmSimpleVoidWorld", () -> isSimpleVoidWorld(renderWorld(mc)) ? 1 : 0);
        uniformRegistry.registerInt("ausmSkyboxRepair", () -> shouldRepairCurrentSkybox(mc) ? 1 : 0);
        uniformRegistry.registerInt("ausmUiSkyRepair", () -> shouldForceUiSkyboxRepair(mc) ? 1 : 0);
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
        uniformRegistry.registerFloat("rainFactor", () -> rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthS", () -> rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthShiningStars", () -> rainStrength(mc));
        uniformRegistry.registerFloat("rainStrengthS2", () -> rainStrength(mc));
        uniformRegistry.registerInt("entityId", () -> currentEntityId);
        uniformRegistry.registerFloat("alphaTestRef", () -> currentAlphaTestReference);
        uniformRegistry.registerFloat("iris_currentAlphaTest", () -> currentAlphaTestReference);
        uniformRegistry.registerVec4("entityColor", () -> currentEntityColor);
        uniformRegistry.registerInt("heldItemId", () -> heldItemId(heldMainStack(mc)));
        uniformRegistry.registerInt("heldItemId2", () -> heldItemId(heldOffhandStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue", () -> heldBlockLightValue(heldMainStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue2", () -> heldBlockLightValue(heldOffhandStack(mc)));
        uniformRegistry.registerVec3("heldBlockLightColor", () -> heldBlockLightColor(heldMainStack(mc)));
        uniformRegistry.registerVec3("heldBlockLightColor2", () -> heldBlockLightColor(heldOffhandStack(mc)));
        uniformRegistry.registerInt("currentSelectedBlockId", () -> currentSelectedBlockId(mc));
        uniformRegistry.registerVec3("currentSelectedBlockPos", () -> currentSelectedBlockPos(mc, cameraPositionUnshifted));
        uniformRegistry.registerInt("isEyeInWater", () -> eyeFluidState(mc));
        uniformRegistry.registerVec2i("eyeBrightness", () -> eyeBrightness(mc));
        uniformRegistry.registerVec2i("eyeBrightnessSmooth", this::smoothedEyeBrightness);
        uniformRegistry.registerFloat("eyeBrightnessM", () -> eyeBrightness(mc)[1] / 240.0f);
        uniformRegistry.registerFloat("currentPlayerHealth", () -> currentPlayerHealth(mc));
        uniformRegistry.registerFloat("maxPlayerHealth", () -> maxPlayerHealth(mc));
        uniformRegistry.registerFloat("currentPlayerHunger", () -> currentPlayerHunger(mc));
        uniformRegistry.registerFloat("maxPlayerHunger", () -> 20.0f);
        uniformRegistry.registerFloat("currentPlayerAir", () -> currentPlayerAir(mc));
        uniformRegistry.registerFloat("maxPlayerAir", () -> maxPlayerAir(mc));
        uniformRegistry.registerFloat("currentPlayerArmor", () -> currentPlayerArmor(mc));
        uniformRegistry.registerFloat("maxPlayerArmor", () -> 50.0f);
        uniformRegistry.registerFloat("pi", () -> (float) Math.PI);
        uniformRegistry.registerInt("anisotropicFiltering", ShaderSamplerState::anisotropicFilteringUniform);
        uniformRegistry.registerInt("blockEntityId", () -> -1);
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
        uniformRegistry.registerInt("dhRenderDistance", () -> mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc) * 16 : 0);
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
        uniformRegistry.registerVec2("iris_ScreenSize", () -> new float[]{(float) worldTargetWidth(mc), (float) worldTargetHeight(mc)});
        uniformRegistry.registerVec3("iris_CameraTranslation", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("iris_ModelOffset", distantHorizonsMatrices::modelOffset);
        uniformRegistry.registerVec4("iris_ColorModulator", () -> new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        uniformRegistry.registerFloat("iris_ModelScale", () -> 1.0f);
        uniformRegistry.registerFloat("iris_TextureScale", () -> 1.0f);
        uniformRegistry.registerFloat("iris_GlintAlpha", () -> 1.0f);
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
        registerAttachmentSizeUniforms();

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
        uniformRegistry.registerInt("shadowMapResolution", this::shadowResolution);
        uniformRegistry.registerVec2i("shadowtex0Size", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowtex1Size", () -> shadowSize());
        uniformRegistry.registerVec2i("shadowSize", () -> shadowSize());
        for (int i = 0; i < ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT; i++) {
            int shadowColorIndex = i;
            uniformRegistry.registerVec2i("shadowcolor" + shadowColorIndex + "Size", () -> shadowSize());
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
        uniformRegistry.registerFloat("cloudTime", () -> cloudTime(mc));
        uniformRegistry.registerFloat("chunkFadeTimeInv", () -> 1.0f / CHUNK_FADE_DURATION_SECONDS);
        uniformRegistry.registerVec3i("currentDate", PipelineContext::currentDate);
        uniformRegistry.registerVec3i("currentTime", PipelineContext::currentTime);
        uniformRegistry.registerVec2i("currentYearTime", PipelineContext::currentYearTime);

        uniformRegistry.registerVec3("cameraPosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("previousCameraPosition", () -> previousCameraPosition.clone());
        uniformRegistry.registerVec3i("cameraPositionInt", () -> cameraPositionInt(cameraPositionUnshifted));
        uniformRegistry.registerVec3("cameraPositionFract", () -> cameraPositionFract(cameraPositionUnshifted));
        uniformRegistry.registerVec3i("previousCameraPositionInt", () -> cameraPositionInt(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("previousCameraPositionFract", () -> cameraPositionFract(previousCameraPositionUnshifted));
        uniformRegistry.registerVec3("eyePosition", () -> cameraPosition.clone());
        uniformRegistry.registerVec3("relativeEyePosition", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("playerLookVector", () -> playerLookVector(mc));
        uniformRegistry.registerVec3("playerBodyVector", () -> bodyVector(mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) : null));
        uniformRegistry.registerVec3("vehicleLookVector", () -> vehicleLookVector(mc));
        uniformRegistry.registerVec3("relativeVehiclePosition", () -> relativeVehiclePosition(mc));
        uniformRegistry.registerVec4("lightningBoltPosition", () -> lightningBoltPosition(mc));
        uniformRegistry.registerFloat("velocity", this::cameraVelocity);

        uniformRegistry.registerVec3("upPosition", PipelineContext::upPosition);
        uniformRegistry.registerVec3("skyColor", () -> shaderSkyColor(mc));
        uniformRegistry.registerVec3("fogColor", () -> effectiveFogColor(mc));
        uniformRegistry.registerVec4("iris_FogColor", () -> {
            float[] color = effectiveFogColor(mc);
            return new float[]{color[0], color[1], color[2], 1.0f};
        });

        // --- Sun & Moon Position ---
        uniformRegistry.registerFloat("sunAngle", () -> sunAngle(mc));
        uniformRegistry.registerFloat("shadowAngle", () -> shadowAngle(mc));
        uniformRegistry.registerVec3("endFlashPosition", () -> endFlashPosition.clone());
        uniformRegistry.registerFloat("endFlashIntensity", () -> endFlashIntensity);
        uniformRegistry.registerFloat("previousEndFlashIntensity", () -> previousEndFlashIntensity);
        uniformRegistry.registerVec3("sunPosition", () -> {
            if (renderWorld(mc) != null) {
                return shaderLightPosition(mc, false);
            }
            return new float[]{0, 100, 0};
        });
        uniformRegistry.registerVec3("moonPosition", () -> {
            if (renderWorld(mc) != null) {
                return shaderLightPosition(mc, true);
            }
            return new float[]{0, -100, 0};
        });
        uniformRegistry.registerVec3("shadowLightPosition", () -> {
            World world = renderWorld(mc);
            if (world != null) {
                if (useEndFlashShadowLight(world)) {
                    return endFlashPosition.clone();
                }
                float celestialAngle = com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc));
                float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
                return legacyShadowLightVector(mc, sunAngle > 0.5F);
            }
            return new float[]{0.0f, 100.0f, 0.0f};
        });
        // --- TAA / History Matrices ---
        /*uniformRegistry.registerMatrix4("gbufferPreviousModelView", MatrixState::getPreviousModelViewMatrix);
        uniformRegistry.registerMatrix4("gbufferPreviousProjection", MatrixState::getPreviousProjectionMatrix);*/
    }

    protected void registerAttachmentSizeUniforms() {
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

    protected Framebuffer currentWorldFramebufferTarget(Minecraft mc) {
        return externalWorldFramebufferTarget != null ? externalWorldFramebufferTarget : mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
    }

    protected static World renderWorld(Minecraft mc) {
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }
        return mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
    }

    protected static int[] currentDate() { return PipelineFrameValues.currentDate(); }

    protected static int[] currentTime() { return PipelineFrameValues.currentTime(); }

    protected static int[] currentYearTime() { return PipelineFrameValues.currentYearTime(); }

    protected boolean isExternalWorldFramebufferTarget(Framebuffer target) {
        return externalWorldFramebufferTarget != null && target == externalWorldFramebufferTarget;
    }

    protected boolean isBetterPortalsExternalWorldTarget() {
        return externalWorldFramebufferTarget != null && isRenderingBetterPortalsNestedView();
    }

    protected int worldTargetWidth(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target)) : Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc));
    }

    protected int worldTargetHeight(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target)) : Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int framebufferWidth(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target)) : Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc));
    }

    protected int framebufferHeight(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target)) : Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int[] attachmentSize(Attachment attachment) {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            return new int[]{worldTargetWidth(mc), worldTargetHeight(mc)};
        }
        return new int[]{
                Math.max(1, pingPongManager.attachmentWidth(attachment)),
                Math.max(1, pingPongManager.attachmentHeight(attachment))
        };
    }

    protected int[] framebufferSize() {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            return new int[]{worldTargetWidth(mc), worldTargetHeight(mc)};
        }
        return new int[]{Math.max(1, pingPongManager.width()), Math.max(1, pingPongManager.height())};
    }

    protected int shadowResolution() {
        return shadowFramebuffer != null ? Math.max(1, shadowFramebuffer.resolution()) : 1;
    }

    protected int[] shadowSize() {
        int resolution = shadowResolution();
        return new int[]{resolution, resolution};
    }

    protected static int[] eyeBrightness(Minecraft mc) {
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return new int[]{0, 0};
        }

        BlockPos pos = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity) + com.l.ausm.impl.util.MinecraftReflectionCompat.eyeHeight(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity));
        int combinedLight = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((world), new String[] {"func_175626_b", "getCombinedLight"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class, int.class}, 0, (pos), (0));
        int block = combinedLight >> 4 & 0xF;
        int sky = combinedLight >> 20 & 0xF;
        if (eyeFluidState(mc) == 1) {
            sky = underwaterSurfaceSkyLight(world, pos, sky);
        }
        return new int[]{block * 16, sky * 16};
    }

    protected static float[] skyColor(Minecraft mc) {
        Entity viewEntity = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (mc != null && world != null && viewEntity != null) {
            return vec3(com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_72833_a", "getSkyColor"},
                new Class<?>[] {net.minecraft.entity.Entity.class, float.class}, (viewEntity), (com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc))));
        }
        return new float[]{0.5f, 0.7f, 1.0f};
    }

    protected float[] shaderSkyColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (!isSimpleVoidWorld(world)) {
            return skyColor(mc);
        }
        float daylight = simpleVoidDaylight(world, mc);
        return new float[]{0.5f * daylight, 0.66275f * daylight, daylight};
    }

    protected float[] simpleVoidOverworldFogColor(Minecraft mc) {
        World world = renderWorld(mc);
        float daylight = simpleVoidDaylight(world, mc);
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
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f;
        float angle = com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks);
        return Math.max(0.0f, Math.min(1.0f,
                (float) Math.cos(angle * Math.PI * 2.0) * 2.0f + 0.5f));
    }

    protected float effectiveFogStart(Minecraft mc) {
        if (shouldUseNestedPortalFogFallback(mc)) {
            return isNetherRenderWorld(mc) ? 0.0f : portalFogFar(mc) * 0.75f;
        }
        if (isNetherRenderWorld(mc)) {
            return GL11.glIsEnabled(GL11.GL_FOG) ? GL11.glGetFloat(GL11.GL_FOG_START) : 0.0f;
        }
        return shaderFarPlaneDistance(mc) * SHADER_OVERWORLD_FOG_START_RATIO;
    }

    protected float effectiveFogEnd(Minecraft mc) {
        if (shouldUseNestedPortalFogFallback(mc)) {
            return portalFogFar(mc);
        }
        if (isNetherRenderWorld(mc)) {
            return GL11.glIsEnabled(GL11.GL_FOG)
                    ? Math.max(GL11.glGetFloat(GL11.GL_FOG_END), shaderRenderDistance(mc))
                    : shaderRenderDistance(mc);
        }
        return shaderFarPlaneDistance(mc);
    }

    protected float effectiveFogDensity(Minecraft mc) {
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return GL11.glGetFloat(GL11.GL_FOG_DENSITY);
        }
        if (isNetherRenderWorld(mc)) {
            return PORTAL_NETHER_FOG_DENSITY;
        }
        if (!shouldUseNestedPortalFogFallback(mc)) {
            return 0.0f;
        }
        return isNetherRenderWorld(mc) ? PORTAL_NETHER_FOG_DENSITY : 0.0f;
    }

    protected int effectiveFogMode(Minecraft mc) {
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return currentGlFogMode();
        }
        if (!shouldUseNestedPortalFogFallback(mc)) {
            return isNetherRenderWorld(mc) ? 2 : 0;
        }
        return isNetherRenderWorld(mc) ? 2 : 0;
    }

    protected float[] effectiveFogColor(Minecraft mc) {
        if (isNetherRenderWorld(mc)) {
            if (shouldUseNestedPortalFogFallback(mc)) {
                return netherFogColor(mc);
            }
            float[] fogColor = currentGlFogColor();
            return isProbablyUnsetFogColor(fogColor) ? netherFogColor(mc) : dampenNetherFogColor(fogColor);
        }
        if (isSimpleVoidWorld(renderWorld(mc))) {
            return simpleVoidOverworldFogColor(mc);
        }
        float[] fogColor = GL11.glIsEnabled(GL11.GL_FOG) ? currentGlFogColor() : null;
        return isProbablyUnsetFogColor(fogColor) ? overworldFogColor(mc) : fogColor;
    }

    protected float[] overworldFogColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world != null) {
            return vec3(com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_72948_g", "getFogColor", "func_72824_f"},
                new Class<?>[] {float.class}, (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f)));
        }
        return skyColor(mc);
    }

    protected float[] netherFogColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world != null) {
            return dampenNetherFogColor(vec3(com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_72948_g", "getFogColor", "func_72824_f"},
                new Class<?>[] {float.class}, (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f))));
        }
        return dampenNetherFogColor(PORTAL_NETHER_FOG_COLOR);
    }

    protected float[] dampenNetherFogColor(float[] color) {
        if (color == null || color.length < 3) {
            color = PORTAL_NETHER_FOG_COLOR;
        }
        return new float[]{
                clamp01(color[0] * NETHER_SHADER_FOG_COLOR_SCALE),
                clamp01(color[1] * NETHER_SHADER_FOG_COLOR_SCALE),
                clamp01(color[2] * NETHER_SHADER_FOG_COLOR_SCALE)
        };
    }

    protected float[] currentGlFogColor() {
        fogColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, fogColorBuffer);
        return new float[]{
                clamp01(fogColorBuffer.get(0)),
                clamp01(fogColorBuffer.get(1)),
                clamp01(fogColorBuffer.get(2))
        };
    }

    protected boolean isProbablyUnsetFogColor(float[] color) {
        return color == null
                || color.length < 3
                || (color[0] <= 0.0001f && color[1] <= 0.0001f && color[2] <= 0.0001f);
    }

    protected boolean shouldUseNestedPortalFogFallback(Minecraft mc) {
        return isBetterPortalsExternalWorldTarget()
                && !GL11.glIsEnabled(GL11.GL_FOG)
                && renderWorld(mc) != null;
    }

    protected boolean isNetherRenderWorld(Minecraft mc) {
        return safeDimensionId(renderWorld(mc)) == -1;
    }

    protected static float cloudHeight(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) == null) {
            return 128.0f;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)), new String[] {"func_76571_f", "getCloudHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 128.0F);
    }

    protected static boolean hasSkylight(Minecraft mc) {
        World world = renderWorld(mc);
        return world != null && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.providerHasSkyLight(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world));
    }

    protected boolean shouldRepairCurrentSkybox(Minecraft mc) {
        return false;
    }

    protected boolean shouldForceUiSkyboxRepair(Minecraft mc) {
        return false;
    }

    protected static float cloudTime(Minecraft mc) {
        World world = renderWorld(mc);
        Object time = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                world,
                new String[] {"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        return time instanceof Number ? (float) (((Number) time).longValue() + (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0f)) : 0.0f;
    }

    protected boolean isEyeInCave(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null || eyeFluidState(mc) != 0) {
            return false;
        }
        BlockPos pos = currentCameraBlockPos();
        return com.l.ausm.impl.util.MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos) <= 1 && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) < com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((world), new String[] {"func_181545_F", "getSeaLevel"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 63);
    }

    protected float portalFogFar(Minecraft mc) {
        return shaderFarPlaneDistance(mc);
    }

    protected static float shaderFarPlaneDistance(Minecraft mc) {
        return shaderRenderDistance(mc) * 2.0f;
    }

    protected static float shaderRenderDistance(Minecraft mc) {
        return Math.max(16.0f, mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc) * 16.0f : 16.0f);
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
        Biome biome = currentCameraBiome(mc);
        if (biome == null) {
            return -1;
        }
        int irisId = irisBiomeId(biome);
        return irisId >= 0 ? irisId : com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(net.minecraft.world.biome.Biome.class, new String[] {"func_185362_a", "getIdForBiome"},
                new Class<?>[] {net.minecraft.world.biome.Biome.class}, -1, (biome));
    }

    protected int currentBiomePrecipitation(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        if (biome == null) {
            return 0;
        }

        BlockPos pos = currentCameraBlockPos();
        boolean canRain = com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((biome), new String[] {"func_76738_d", "canRain"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false);
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((biome), new String[] {"func_76746_c", "getEnableSnow"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)
                || com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((biome), new String[] {"func_150559_j", "isSnowyBiome"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)
                || (canRain && com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((biome), new String[] {"func_180626_a", "getTemperature"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, 0.0F, (pos)) < 0.15f)) {
            return 2;
        }
        return canRain ? 1 : 0;
    }

    protected int currentBiomeCategory(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        Object category = biome != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(biome, new String[] {"func_150561_m", "getTempCategory"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS)
                : null;
        return category instanceof Enum<?> ? ((Enum<?>) category).ordinal() : -1;
    }

    protected float currentBiomeRainfall(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        return biome != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((biome), new String[] {"func_76727_i", "getRainfall"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) : 0.0f;
    }

    protected float currentBiomeTemperature(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        return biome != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((biome), new String[] {"func_180626_a", "getTemperature"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, 0.0F, (currentCameraBlockPos())) : 0.0f;
    }

    protected Biome currentCameraBiome(Minecraft mc) {
        World world = renderWorld(mc);
        if (mc == null || world == null) {
            return null;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.world.biome.Biome.class, null, new String[] {"func_180494_b", "getBiome"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, (currentCameraBlockPos()));
    }

    protected BlockPos currentCameraBlockPos() {
        return new BlockPos(cameraPositionUnshifted[0], cameraPositionUnshifted[1], cameraPositionUnshifted[2]);
    }

    protected static int irisBiomeId(Biome biome) {
        ResourceLocation name = com.l.ausm.impl.util.MinecraftReflectionCompat.call((biome), net.minecraft.util.ResourceLocation.class, null, new String[] {"getRegistryName"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        if (name == null) {
            return -1;
        }
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
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
        int maxY = Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((world), new String[] {"func_72800_K", "getHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 256), 255);
        int sky = fallbackSky;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos(eyePos);
        for (int y = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(eyePos); y <= maxY; y++) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.mutableBlockPosSet(probe, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(eyePos), y, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(eyePos));
            IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, probe);
            if (!com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsWater(state)) {
                return Math.max(sky, com.l.ausm.impl.util.MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, probe));
            }
            sky = Math.max(sky, com.l.ausm.impl.util.MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, probe));
        }
        return sky;
    }

    protected static float blindness(Minecraft mc) {
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity instanceof EntityLivingBase living && com.l.ausm.impl.util.MinecraftReflectionCompat.livingPotionActive(living, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.init.MobEffects.class, net.minecraft.potion.Potion.class, null, "field_76440_q", "BLINDNESS"))) {
            PotionEffect effect = com.l.ausm.impl.util.MinecraftReflectionCompat.livingActivePotionEffect(living, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.init.MobEffects.class, net.minecraft.potion.Potion.class, null, "field_76440_q", "BLINDNESS"));
            if (effect == null) {
                return 1.0f;
            }
            return Math.max(0.0f, Math.min(1.0f, com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((effect), new String[] {"func_76459_b", "getDuration"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0) / 20.0f));
        }
        return 0.0f;
    }

    protected static float nightVision(Minecraft mc) {
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity instanceof EntityLivingBase living && com.l.ausm.impl.util.MinecraftReflectionCompat.livingPotionActive(living, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.init.MobEffects.class, net.minecraft.potion.Potion.class, null, "field_76439_r", "NIGHT_VISION"))) {
            PotionEffect effect = com.l.ausm.impl.util.MinecraftReflectionCompat.livingActivePotionEffect(living, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.init.MobEffects.class, net.minecraft.potion.Potion.class, null, "field_76439_r", "NIGHT_VISION"));
            if (effect == null) {
                return 1.0f;
            }
            int duration = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((effect), new String[] {"func_76459_b", "getDuration"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0);
            return duration > 200 ? 1.0f : 0.7f + (float) Math.sin((duration - com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) * (float) Math.PI * 0.2f) * 0.3f;
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

    protected void updateSmoothedWetness(Minecraft mc) {
        World world = renderWorld(mc);
        float current = world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f;
        if (!wetnessSmoothInitialized) {
            wetnessSmooth = current;
            wetnessSmoothInitialized = true;
            return;
        }

        float halfLife = current > wetnessSmooth ? wetnessHalfLife : drynessHalfLife;
        wetnessSmooth += (current - wetnessSmooth) * smoothingFactor(halfLife, currentFrameTime);
    }

    protected float rainStrength(Minecraft mc) {
        World world = renderWorld(mc);
        return world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) : 0.0f;
    }

    protected void updateSmoothedFrameTime() {
        if (!frameTimeSmoothInitialized) {
            frameTimeSmooth = currentFrameTime;
            frameTimeSmoothInitialized = true;
            return;
        }
        frameTimeSmooth += (currentFrameTime - frameTimeSmooth) * smoothingFactor(5.0f, currentFrameTime);
    }

    protected static float smoothingFactor(float halfLifeDeciseconds, float frameTimeSeconds) { return PipelineFrameValues.smoothingFactor(halfLifeDeciseconds, frameTimeSeconds); }

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
        BlockPos pos = currentSelectedBlockPosition(mc);
        World world = renderWorld(mc);
        if (world == null || pos == null) {
            return 0;
        }
        return blockEntityId(com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos), world, pos);
    }

    protected static float[] currentSelectedBlockPos(Minecraft mc, double[] cameraPosition) {
        BlockPos pos = currentSelectedBlockPosition(mc);
        if (pos == null) {
            return new float[]{-256.0f, -256.0f, -256.0f};
        }
        return new float[]{
                (float) (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraPosition[0]),
                (float) (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraPosition[1]),
                (float) (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraPosition[2])
        };
    }

    protected static BlockPos currentSelectedBlockPosition(Minecraft mc) {
        RayTraceResult hit = com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), net.minecraft.util.math.RayTraceResult.class, null, "field_71476_x", "objectMouseOver");
        if (hit == null || com.l.ausm.impl.util.MinecraftReflectionCompat.field((hit), net.minecraft.util.math.RayTraceResult.Type.class, null, "field_72313_a", "typeOfHit") != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.rayTraceBlockPos(hit);
    }

    protected static boolean playerSurvivalStatsVisible(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), net.minecraft.client.multiplayer.PlayerControllerMP.class, null, "field_71442_b", "playerController") == null) {
            return false;
        }
        net.minecraft.world.GameType gameType = com.l.ausm.impl.util.MinecraftReflectionCompat.call((com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), net.minecraft.client.multiplayer.PlayerControllerMP.class, null, "field_71442_b", "playerController")), net.minecraft.world.GameType.class, null, new String[] {"func_178889_l", "getCurrentGameType"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        int id = com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(gameType, new String[] {"func_77148_a", "getID"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, -1);
        return id == 0 || id == 2;
    }

    protected float currentPlayerHealth(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        float maxHealth = Math.max(0.001f, com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_110138_aP", "getMaxHealth"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0.0F));
        return clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_110143_aJ", "getHealth"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) / maxHealth);
    }

    protected float maxPlayerHealth(Minecraft mc) {
        return playerSurvivalStatsVisible(mc) ? com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_110138_aP", "getMaxHealth"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0.0F) : -1.0f;
    }

    protected float currentPlayerHunger(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        Object foodStats = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc), new String[] {"func_71024_bL", "getFoodStats"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        return clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(foodStats, new String[] {"func_75116_a", "getFoodLevel"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0) / 20.0f);
    }

    protected float currentPlayerAir(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70086_ai", "getAir"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0) / 300.0f);
    }

    protected float maxPlayerAir(Minecraft mc) {
        return playerSurvivalStatsVisible(mc) ? 300.0f : -1.0f;
    }

    protected float currentPlayerArmor(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), new String[] {"func_70658_aO", "getTotalArmorValue"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 0) / 50.0f);
    }

    protected static float[] playerLookVector(Minecraft mc) {
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            return new float[]{0.0f, 0.0f, 1.0f};
        }
        Vec3d look = com.l.ausm.impl.util.MinecraftReflectionCompat.look(viewEntity, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc));
        return vec3(look);
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
                offset[0] / worldTargetWidth(mc),
                offset[1] / worldTargetHeight(mc)
        };
    }

    protected static float[] vec3(Vec3d vec) {
        return new float[]{
                (float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(vec),
                (float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(vec),
                (float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(vec)
        };
    }

    protected float[] viewSpaceLightVector(Minecraft mc, boolean moon) {
        float[] world = worldSpaceLightVector(mc, moon);
        return MatrixState.transformModelViewDirection(world[0], world[1], world[2]);
    }

    protected float[] shaderLightPosition(Minecraft mc, boolean moon) {
        return viewSpaceLightVector(mc, moon);
    }

    protected float[] worldSpaceLightVector(Minecraft mc, boolean moon) {
        World world = renderWorld(mc);
        if (world == null) {
            return new float[]{0.0f, moon ? -100.0f : 100.0f, 0.0f};
        }
        float skyAngle = com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) * (float) (Math.PI * 2.0);
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

    protected float sunAngle(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0f;
        }
        float angle = com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)) + 0.25f;
        if (angle >= 1.0f) {
            angle -= 1.0f;
        }
        return angle;
    }

    protected float shadowAngle(Minecraft mc) {
        if (renderWorld(mc) == null) {
            return 0.0f;
        }
        float angle = sunAngle(mc);
        return angle < 0.5f ? angle : angle - 0.5f;
    }

    protected float shadowFade(Minecraft mc, float threshold, float scale) {
        float angle = sunAngle(mc);
        return clamp01(1.0f - (Math.abs(Math.abs(angle - 0.5f) - 0.25f) - threshold) * scale);
    }

    protected float[] legacyShadowLightVector(Minecraft mc, boolean moon) {
        return viewSpaceLightVector(mc, moon);
    }

    protected float dayMoment(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.25f;
        }
        return world != null ? (float) ((com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0) : 0.25f;
    }

    protected float adjustedDayTime(Minecraft mc) {
        World world = renderWorld(mc);
        long worldTime = world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L : 0L;
        return Math.abs(((((worldTime) / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
    }

    protected float dayHelper(Minecraft mc) {
        return clamp01(5.4f - adjustedDayTime(mc));
    }

    protected float nightHelper(Minecraft mc) {
        return clamp01(adjustedDayTime(mc) - 6.0f);
    }

    protected float dayMixer(Minecraft mc) {
        float moment = dayMoment(mc) - 0.25f;
        return clamp01(-(moment * moment) * 20.0f + 1.25f);
    }

    protected float nightMixer(Minecraft mc) {
        float moment = dayMoment(mc) - 0.75f;
        return clamp01(-(moment * moment) * 50.0f + 3.125f);
    }

    protected float dayNightMix(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 1.0f;
        }
        float worldTime = com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L;
        float day = worldTime < 12485.0f || worldTime >= 23515.0f ? 1.0f : 0.0f;
        float dusk = worldTime >= 12485.0f && worldTime < 13085.0f
                ? 1.0f - ((worldTime - 12485.0f) * 0.0016666667f)
                : 0.0f;
        float dawn = worldTime >= 22915.0f && worldTime < 23515.0f
                ? (worldTime - 22915.0f) * 0.0016666667f
                : 0.0f;
        return Math.max(Math.max(day, dusk), dawn);
    }

    protected float volumetricDayMixer(Minecraft mc) {
        float moment = dayMoment(mc);
        float day = (moment * 4.0f) - 1.0f;
        float night = (moment * 4.0f) - 3.0f;
        float dayValue = clamp((-(day * day * day * day) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        float nightValue = clamp((-(night * night * night * night) + 1.0f) * 7.0f + 1.0f, 1.0f, 8.0f);
        return Math.max(dayValue, nightValue);
    }

    protected float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    protected float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public void initialize(ShaderPack pack) {
        initialize(pack, Map.of());
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides) {
        initialize(pack, optionOverrides, null);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        initialize(pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                           ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
        initializeInternal(null, pack, optionOverrides, preloadedProperties, loadingBackgroundMode);
    }

    public void initializeCached(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        initializeInternal(cacheKey, pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    protected void initializeInternal(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                                    ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
        nothiriumShadowRenderer.resetPipelineProgramState();
        terrainRebuiltDuringLastInitialization = false;
        terrainCacheReusableDuringLastInitialization = false;
        boolean wasPipelineActive = isPipelineActive;
        boolean replacingActiveCacheKey = cacheKey != null && cacheKey.equals(activeCompiledPipelineCacheKey);
        CompiledPipelineState cachedPrograms = replacingActiveCacheKey ? null : removeCachedCompiledPipeline(cacheKey);
        if (cacheKey == null) {
            cleanupRuntimeState(true, true);
        } else {
            if (replacingActiveCacheKey) {
                deleteCachedCompiledPipeline(cacheKey);
                activeCompiledPipelineCacheKey = null;
            } else {
                cacheActiveCompiledPipeline();
            }
            cleanupRuntimeState(true, false, !wasPipelineActive);
        }
        shaderProperties = emptyShaderProperties();
        activePackName = pack.getName();
        resetHardwareCompatibilityState();

        MainMod.LOGGER.info("[Pipeline] Initializing with pack: {}", pack.getName());
        logHardwareCapabilities("initialize:" + pack.getName(), preloadedProperties != null ? preloadedProperties.packDirectives() : null);

        if (pack.getName().equals("(internal)")) { // NoneShaderPack
            MainMod.LOGGER.info("[Pipeline] Internal None pack selected. Pipeline is inactive.");
            return;
        }

        releaseMouseForShaderLoad(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        boolean usingCachedPrograms = cachedPrograms != null;
        boolean restoredCachedPrograms = false;
        ShaderLoadingScreen.begin(pack.getName(), usingCachedPrograms ? 9 : 12, loadingBackgroundMode);
        try {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            ShaderLoadingScreen.step("Loading shader properties");
            ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
            logHardwareCapabilities("properties:" + pack.getName(), properties.packDirectives());
            ShaderCompileNotifications.beginReload();
            ShaderLoadingScreen.step("Scanning shader programs");
            programSet = usingCachedPrograms ? cachedPrograms.programSet : ShaderProgramSet.load(pack, properties);
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
                            .withGeometry(programSet.hasGeometrySources())
                            .withTessellation(programSet.hasTessellationSources())
                            .withExtraProgramArrayEntries(hasExtraProgramArrayEntries())
            );
            ShaderFeatureValidator.Result featureValidation = ShaderFeatureValidator.validate(packDirectives);
            for (String warning : featureValidation.warnings()) {
                MainMod.LOGGER.warn("[Pipeline] {}", warning);
            }
            if (!featureValidation.supported()) {
                String summary = featureValidation.summary();
                MainMod.LOGGER.error("[Pipeline] Shaderpack '{}' disabled: {}", pack.getName(), summary);
                ShaderCompileNotifications.reportLoadFailure(pack.getName(), summary);
                return;
            }
            ShaderLoadingScreen.setTotalSteps(usingCachedPrograms ? 9 : shaderLoadingStepCount(properties));
            ShaderLoadingMap loadingMap = usingCachedPrograms ? null : new ShaderLoadingMap();
            shaderProperties = properties;
            bloomRenderer.configure(pack, properties);
            ShaderBlockLayerOverrides.install(properties.blockIds());
            ShaderSamplerState.setBreaksAnisotropy(properties.renderSettings().breaksAnisotropy());
            shadowMapDistance = parseFloatSettingWithComment(pack, properties, "shadowDistance", "SHADOWHPL", 128.0f);
            voxelDistance = parseFloatSetting(pack, properties, "voxelDistance", 0.0f);
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
            resetShadowRenderCache();
            shadowHealthLogged = false;
            shadowHealthLogAttempts = 0;
            ShaderLoadingScreen.step("Preparing framebuffers");
            pingPongManager.initialize(com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), packDirectives.renderTargets());
            initializeBlankShadowFramebuffer(pack, properties);
            MainMod.LOGGER.debug(
                    "[Pipeline] Shadow config: framebuffer={} distance={} voxelDistance={} renderMul={} interval={} sunPathRotation={} hardwareFiltering={} tex0Nearest={} tex1Nearest={} polygonOffset={} factor={} units={}",
                    shadowFramebuffer != null ? shadowFramebuffer.resolution() : 0,
                    shadowMapDistance,
                    voxelDistance,
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
            if (packDirectives.computeDirectives().hasComputes()) {
                MainMod.LOGGER.debug(
                        "[Pipeline] Loaded compute metadata: arrays={} shadow={} final={}",
                        packDirectives.computeDirectives().computeArrays().values().stream().mapToInt(List::size).sum(),
                        packDirectives.computeDirectives().shadowComputes().size(),
                        packDirectives.computeDirectives().finalComputes().size()
                    );
            }
            ShaderLoadingScreen.step("Preparing shader resources");
            shaderImages = ShaderImageSet.load(packDirectives.images());
            shaderImages.resize(com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
            clearColoredLightImages();
            shaderStorageBuffers = ShaderStorageBufferSet.load(pack, packDirectives.storageBuffers());
            shaderStorageBuffers.resize(com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
            if (shaderStorageBuffers.active()) {
                markShaderStorageBuffersBound();
            }
            if (!usingCachedPrograms) {
                ShaderLoadingScreen.step("Compiling compute shaders");
                compileComputePrograms(pack, properties);
                setupComputePending = !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
            }
            logRequestedFeaturesAndCapabilities();
            ShaderLoadingScreen.step("Loading noise texture");
            initializeNoiseTexture(pack, properties);
            ShaderLoadingScreen.step("Loading custom textures");
            customTextures.load(pack, packDirectives.textureDirectives(), fullscreenArrayPrograms);
            lastPipelineFrameNanos = System.nanoTime() - 1_000_000_000L;
            currentFrameTime = 1.0f;

            if (usingCachedPrograms) {
                ShaderLoadingScreen.step("Restoring cached shader programs");
                restoreCompiledPipeline(cachedPrograms);
                restoredCachedPrograms = true;
                MainMod.LOGGER.info("[Pipeline] Reused cached compiled shader programs for pack: {}", pack.getName());
            } else {
                for (RenderPass pass : RenderPass.values()) {
                    PipelineProgram pipelineProgram = new PipelineProgram(pass, programSet.source(pass.programId()).directives());
                    applyFallbackDefaultDrawBuffers(pipelineProgram);
                    ShaderProgramSource source = programSet.source(pass.programId());
                    boolean hasOfficialFinalSource = pass == RenderPass.FINAL
                            && (source.fragmentPath() != null || source.fragmentSource() != null);
                    boolean enabled = properties.isProgramEnabled(pass) || hasOfficialFinalSource;
                    pipelineProgram.setEnabled(enabled);

                    if (enabled) {
                        ShaderLoadingScreen.step("Compiling " + pass.getProgramName());
                        ShaderProgram program = ShaderCompiler.compilePass(pack, pass, properties, source, packDirectives);
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
                compileFullscreenArrayPrograms(pack, properties);
                ShaderLoadingScreen.step("Building shader pipeline");
                shaderMap = new ShaderMap(loadingMap);
            }
            setupComputePending = hasSetupPrograms();

            isPipelineActive = pingPongManager.isInitialized();
            activeSkyPipelineProbeLogs = 0;
            compositeChainProbeLogs = 0;
            fullscreenSamplerProbeLogs = 0;
            resetChunkFadeState(true);
            activeCompiledPipelineCacheKey = cacheKey;
            long loadedProgramCount = programs.values().stream().filter(PipelineProgram::hasOwnProgram).count();
            long loadedArrayProgramCount = fullscreenArrayPrograms.values().stream()
                    .flatMap(List::stream)
                    .filter(FullscreenArrayProgram::hasProgram)
                    .count();
            clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad("initialize:" + pack.getName());
            applyPackStartupTerrainFallback("initialize:" + pack.getName());
            MainMod.LOGGER.info(
                    "[Pipeline] Initialization complete. Pipeline Active: {}, Loaded Programs: {} (+{} indexed fullscreen)",
                    isPipelineActive,
                    loadedProgramCount,
                    loadedArrayProgramCount
            );
            ShaderCompileNotifications.finishReload(pack.getName());
            syntheticLightCandidates.clear();
            resetColoredLightAudit();
            if (wasPipelineActive) {
                NothiriumBypass.markAllChanged();
                scheduleWorldTerrainRefresh(true, true, 0);
                ShaderLoadingScreen.step("Refreshing terrain metadata");
            } else {
                boolean nothiriumFormatChanged = updateNothiriumPipelineBlockFormatMode();
                ShaderLoadingScreen.step("Rebuilding terrain");
                rebuildTerrainRenderers(nothiriumFormatChanged, true);
                terrainRebuiltDuringLastInitialization = true;
            }
        } finally {
            if (cachedPrograms != null && !restoredCachedPrograms) {
                cachedPrograms.delete();
            }
            ShaderLoadingScreen.finish();
        }
    }

    protected void releaseMouseForShaderLoad(Minecraft mc) {
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean((mc), false, "field_71415_G", "inGameHasFocus")) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((mc), new String[] {"func_71364_i", "setIngameNotInFocus"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
        }
        try {
            if (org.lwjgl.input.Mouse.isCreated()) {
                org.lwjgl.input.Mouse.setGrabbed(false);
            }
        } catch (RuntimeException ignored) {
        }
    }

    public ShaderProperties getShaderProperties() {
        return shaderProperties;
    }

    public boolean activateCachedCompiledPipeline(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides,
                                                  ShaderProperties preloadedProperties) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return false;
        }
        if (cacheKey.equals(activeCompiledPipelineCacheKey) && programSet != null && shaderMap != null && isPipelineActive) {
            return true;
        }
        if (!pingPongManager.isInitialized()) {
            return false;
        }

        CompiledPipelineState cachedPrograms = removeCachedCompiledPipeline(cacheKey);
        if (cachedPrograms == null) {
            return false;
        }

        try {
            cacheActiveCompiledPipeline();
            ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
            programSet = cachedPrograms.programSet;
            shaderProperties = properties;
            bloomRenderer.configure(pack, properties);
            ShaderBlockLayerOverrides.install(properties.blockIds());
            ShaderSamplerState.setBreaksAnisotropy(properties.renderSettings().breaksAnisotropy());
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
                            .withGeometry(programSet.hasGeometrySources())
                            .withTessellation(programSet.hasTessellationSources())
                            .withExtraProgramArrayEntries(hasExtraProgramArrayEntries())
            );
            restoreCompiledPipeline(cachedPrograms);
            activePackName = pack.getName();
            activeCompiledPipelineCacheKey = cacheKey;
            setupComputePending = hasSetupPrograms();
            resetTransientWorldRenderState();
            isPipelineActive = true;
            activeSkyPipelineProbeLogs = 0;
            resetChunkFadeState(true);
            clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad("activate-cache:" + pack.getName());
            applyPackStartupTerrainFallback("activate-cache:" + pack.getName());
            MainMod.LOGGER.debug("[Pipeline] Activated cached compiled shader programs: {}", cacheKey);
            return true;
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[Pipeline] Failed to activate cached compiled shader programs: {}", cacheKey, e);
            cachedPrograms.delete();
            return false;
        }
    }

    public void clearCompiledPipelineCache() {
        deleteCachedCompiledPipelines();
        activeCompiledPipelineCacheKey = null;
    }

    public boolean consumeTerrainRebuiltDuringLastInitialization() {
        boolean rebuilt = terrainRebuiltDuringLastInitialization;
        terrainRebuiltDuringLastInitialization = false;
        return rebuilt;
    }

    public boolean consumeTerrainCacheReusableDuringLastInitialization() {
        boolean reusable = terrainCacheReusableDuringLastInitialization;
        terrainCacheReusableDuringLastInitialization = false;
        return reusable;
    }

    protected void cacheActiveCompiledPipeline() {
        if (activeCompiledPipelineCacheKey == null || programSet == null || shaderMap == null || isInternalPipelinePack()) {
            return;
        }

        CompiledPipelineState previous = compiledPipelineCache.put(activeCompiledPipelineCacheKey, detachCompiledPipeline());
        if (previous != null) {
            previous.delete();
        }
        MainMod.LOGGER.debug("[Pipeline] Cached compiled shader programs: {}", activeCompiledPipelineCacheKey);
        activeCompiledPipelineCacheKey = null;
    }

    protected CompiledPipelineState detachCompiledPipeline() {
        CompiledPipelineState state = new CompiledPipelineState(
                programSet,
                shaderMap,
                programs,
                computeProgramArrays,
                shadowComputePrograms,
                finalComputePrograms,
                fullscreenArrayPrograms
        );
        programs.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        fullscreenArrayPrograms.clear();
        programSet = null;
        shaderMap = null;
        setupComputePending = false;
        return state;
    }

    protected void restoreCompiledPipeline(CompiledPipelineState state) {
        programs.clear();
        programs.putAll(state.programs);
        computeProgramArrays.clear();
        computeProgramArrays.putAll(state.computeProgramArrays);
        shadowComputePrograms = state.shadowComputePrograms;
        finalComputePrograms = state.finalComputePrograms;
        fullscreenArrayPrograms.clear();
        fullscreenArrayPrograms.putAll(state.fullscreenArrayPrograms);
        programSet = state.programSet;
        shaderMap = state.shaderMap;
        applyFallbackDefaultDrawBuffers();
    }

    protected void applyFallbackDefaultDrawBuffers() {
        for (PipelineProgram program : programs.values()) {
            applyFallbackDefaultDrawBuffers(program);
        }
        for (Map.Entry<ProgramArrayId, List<FullscreenArrayProgram>> entry : fullscreenArrayPrograms.entrySet()) {
            for (FullscreenArrayProgram program : entry.getValue()) {
                applyFallbackDefaultDrawBuffers(program);
            }
        }
    }

    protected void applyFallbackDefaultDrawBuffers(PipelineProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(defaultDrawBuffers(program.stage()));
    }

    protected void applyFallbackDefaultDrawBuffers(FullscreenArrayProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(program.arrayId() == ProgramArrayId.SHADOWCOMP
                ? List.of(Attachment.COLOR)
                : List.of(fallbackColorAttachment()));
    }

    protected List<Attachment> defaultDrawBuffers(ProgramStage stage) {
        return switch (stage) {
            case PREPARE, GBUFFERS, DEFERRED, COMPOSITE -> List.of(fallbackColorAttachment());
            case SHADOW -> List.of(Attachment.COLOR);
            case FINAL, NONE -> List.of();
        };
    }

    protected Attachment fallbackColorAttachment() {
        int index = shaderProperties != null ? shaderProperties.renderSettings().fallbackTex() : 0;
        Attachment attachment = Attachment.fromColorIndex(index);
        return attachment != null ? attachment : Attachment.COLOR;
    }

    protected CompiledPipelineState removeCachedCompiledPipeline(String cacheKey) {
        return cacheKey == null ? null : compiledPipelineCache.remove(cacheKey);
    }

    protected void deleteCachedCompiledPipeline(String cacheKey) {
        CompiledPipelineState state = removeCachedCompiledPipeline(cacheKey);
        if (state != null) {
            state.delete();
        }
    }

    protected void deleteCachedCompiledPipelines() {
        compiledPipelineCache.values().forEach(CompiledPipelineState::delete);
        compiledPipelineCache.clear();
    }

    protected boolean isInternalPipelinePack() {
        return "(internal)".equals(activePackName);
    }

    protected void applyPackStartupTerrainFallback(String stage) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS || !isPipelineActive || !shouldStartWithSoftVanillaTerrain()) {
            return;
        }
        activateSoftVanillaTerrainRenderer("pack-startup-" + terrainFallbackPackKey() + ":" + stage);
    }

    protected boolean shouldStartWithSoftVanillaTerrain() {
        return ENABLE_SAFE_TERRAIN_FALLBACKS && isComplementarySoftVanillaStartupPack();
    }

    protected boolean shouldPresentPreCompositeForSoftVanillaStartupPack() {
        return ENABLE_SAFE_TERRAIN_FALLBACKS && isComplementarySoftVanillaStartupFallbackActive();
    }

    protected boolean shouldPresentPreCompositeForNothiriumCompositeLoss() {
        return false;
    }

    protected boolean shouldSuppressShadowMapForSoftVanillaStartupPack() {
        return false;
    }

    protected boolean isComplementarySoftVanillaStartupFallbackActive() {
        return isPipelineActive && softVanillaTerrainRenderer && isComplementarySoftVanillaStartupPack();
    }

    protected boolean isComplementarySoftVanillaStartupPack() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        return name.contains("complementaryunbound")
                || name.contains("complimentary entree")
                || name.contains("complementary entree");
    }

    protected String terrainFallbackPackKey() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        if (name.contains("complementaryunbound")) {
            return "unbound";
        }
        if (name.contains("complimentary entree") || name.contains("complementary entree")) {
            return "entree";
        }
        return "shaderpack";
    }

    protected void resetTransientWorldRenderState() {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        passStack.clear();
        worldPassBypassStack.clear();
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        currentWorldPassSerial = Long.MIN_VALUE;
        shaderlessWorldPassActive = false;
        worldFrameActive = false;
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        sparseStartupPresentationHoldFrames = 0;
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
    }

    protected void initializeBlankShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        if (!shouldCreateShadowFramebuffer(pack, properties)) {
            return;
        }

        String resolutionValue = settingValueWithComment(pack, properties, "shadowMapResolution", "SHADOWRES");
        int resolution = parseIntValue(resolutionValue, 1024);
        resolution = Math.max(16, Math.min(8192, resolution));
        shadowFramebuffer = new ShadowFramebuffer(resolution, packDirectives.renderTargets());
        shadowMapPopulated = false;
        shadowMapUsable = false;
        shadowMapSparseForSampling = false;
        shadowMapCoverageStableFrames = 0;
        nothiriumShadowInvalidFrames = 0;
        nothiriumShadowSuppressedFrames = 0;
        nothiriumShadowVerticalHoldFrames = 0;
        resetShadowRenderCache();
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

    protected static boolean shouldCreateShadowFramebuffer(ShaderPack pack, ShaderProperties properties) {
        return hasEffectiveShadowProgram(properties)
                || properties.options().booleanValue("SHADOW_CASTING")
                || properties.options().booleanValue("ENABLE_SHADOWS")
                || hasShadowProgramFiles(pack);
    }

    protected static boolean hasEffectiveShadowProgram(ShaderProperties properties) {
        for (RenderPass pass : RenderPass.values()) {
            if (pass.stage() == ProgramStage.SHADOW && properties.isProgramEnabled(pass)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean hasShadowProgramFiles(ShaderPack pack) {
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

    protected void rebuildFullscreenProgramArrays() {
        fullscreenProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            FullscreenProgramArray array = FullscreenProgramArray.fromProgramSet(arrayId, programSet);
            fullscreenProgramArrays.put(arrayId, array);
            if (hasUnsupportedFullscreenArrayEntries(array)) {
                MainMod.LOGGER.debug(
                        "[Pipeline] Program array {} declares {} programs; current 1.12 adapter exposes {} fixed slots and cannot run the remaining entries yet.",
                        arrayId.sourcePrefix(),
                        array.declaredProgramCount(),
                        array.fixedPasses().size()
                );
            }
        }
    }

    protected boolean hasExtraProgramArrayEntries() {
        return fullscreenProgramArrays.values().stream()
                .anyMatch(PipelineContext::hasUnsupportedFullscreenArrayEntries);
    }

    protected static boolean hasUnsupportedFullscreenArrayEntries(FullscreenProgramArray array) {
        if (!array.hasExtraPrograms()) {
            return false;
        }
        return !supportsIndexedFullscreenArray(array.arrayId());
    }

    protected int shaderLoadingStepCount(ShaderProperties properties) {
        return 9
                + computeProgramSourceCount(packDirectives.computeDirectives())
                + enabledProgramCount(properties)
                + enabledFullscreenArrayProgramSourceCount(properties);
    }

    protected static int computeProgramSourceCount(ShaderComputeDirectives directives) {
        return PipelineProgramArrayRules.computeSourceCount(directives);
    }

    protected int enabledFullscreenArrayProgramSourceCount(ShaderProperties properties) {
        if (programSet == null) {
            return 0;
        }
        int count = 0;
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            for (ShaderProgramSource source : programSet.programArray(arrayId)) {
                int index = indexForFullscreenArraySource(arrayId, source.name());
                if (source.hasAnyStage()
                        && shouldCompileIndexedFullscreenArraySource(arrayId, index)
                        && properties.isProgramArrayEnabled(arrayId, source.name())) {
                    count++;
                }
            }
        }
        return count;
    }

    protected static int enabledProgramCount(ShaderProperties properties) {
        int count = 0;
        for (RenderPass pass : RenderPass.values()) {
            if (properties.isProgramEnabled(pass)) {
                count++;
            }
        }
        return count;
    }

    protected void compileComputePrograms(ShaderPack pack, ShaderProperties properties) {
        computeProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            List<ComputeProgram> compiled = compileComputeList(
                    pack,
                    properties,
                    arrayId,
                    packDirectives.computeDirectives().computeArrays().getOrDefault(arrayId, List.of()),
                    packDirectives
            );
            if (!compiled.isEmpty()) {
                computeProgramArrays.put(arrayId, compiled);
            }
        }
        shadowComputePrograms = compileComputeList(pack, properties, null, packDirectives.computeDirectives().shadowComputes(), packDirectives);
        finalComputePrograms = compileComputeList(pack, properties, null, packDirectives.computeDirectives().finalComputes(), packDirectives);
    }

    protected void compileFullscreenArrayPrograms(ShaderPack pack, ShaderProperties properties) {
        fullscreenArrayPrograms.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            List<FullscreenArrayProgram> compiled = compileFullscreenArrayList(pack, properties, arrayId);
            if (!compiled.isEmpty()) {
                fullscreenArrayPrograms.put(arrayId, compiled);
            }
        }
    }

    protected List<FullscreenArrayProgram> compileFullscreenArrayList(
            ShaderPack pack,
            ShaderProperties properties,
            ProgramArrayId arrayId
    ) {
        List<ShaderProgramSource> sources = programSet.programArray(arrayId);
        if (sources.isEmpty()) {
            return List.of();
        }

        List<FullscreenArrayProgram> compiled = new ArrayList<>();
        RenderPass bindingPass = fullscreenArrayBindingPass(arrayId);
        for (ShaderProgramSource source : sources) {
            if (!source.hasAnyStage()) {
                continue;
            }
            if (!properties.isProgramArrayEnabled(arrayId, source.name())) {
                MainMod.LOGGER.debug("[Pipeline] Program array source disabled by properties: {}", source.name());
                continue;
            }
            int index = indexForFullscreenArraySource(arrayId, source.name());
            if (!shouldCompileIndexedFullscreenArraySource(arrayId, index)) {
                continue;
            }

            FullscreenArrayProgram arrayProgram = new FullscreenArrayProgram(
                    arrayId,
                    index,
                    source.name(),
                    bindingPass,
                    properties.directivesFor(arrayId, source.name())
            );
            applyFallbackDefaultDrawBuffers(arrayProgram);
            ShaderLoadingScreen.step("Compiling " + source.name());
            ShaderProgram shaderProgram = ShaderCompiler.compileSource(pack, properties, source, bindingPass, packDirectives);
            if (shaderProgram != null) {
                arrayProgram.setShaderProgram(shaderProgram);
                compiled.add(arrayProgram);
                MainMod.LOGGER.debug("[Pipeline] Added indexed fullscreen program: {}", source.name());
            }
        }
        return List.copyOf(compiled);
    }

    protected static int indexForFullscreenArraySource(ProgramArrayId arrayId, String sourceName) {
        return PipelineProgramArrayRules.index(arrayId, sourceName);
    }

    protected static boolean supportsIndexedFullscreenArray(ProgramArrayId arrayId) {
        return switch (arrayId) {
            case SETUP, BEGIN, PREPARE, DEFERRED, COMPOSITE, SHADOWCOMP -> true;
        };
    }

    protected static boolean shouldCompileIndexedFullscreenArraySource(ProgramArrayId arrayId, int index) {
        return PipelineProgramArrayRules.shouldCompile(arrayId, index);
    }

    protected static RenderPass fullscreenArrayBindingPass(ProgramArrayId arrayId) {
        return PipelineProgramArrayRules.bindingPass(arrayId);
    }

    protected boolean hasSetupPrograms() {
        return !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
    }

    protected static List<ComputeProgram> compileComputeList(
            ShaderPack pack,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            List<ComputeProgramSource> sources,
            ShaderPackDirectives directives
    ) {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<ComputeProgram> compiled = new ArrayList<>();
        for (ComputeProgramSource source : sources) {
            if (arrayId != null && !properties.isProgramArrayEnabled(arrayId, source.name())) {
                MainMod.LOGGER.debug("[Pipeline] Compute array source disabled by properties: {}", source.name());
                continue;
            }
            ShaderLoadingScreen.step("Compiling " + source.name());
            ComputeProgram program = ComputeProgram.compile(pack, properties, source, directives);
            if (program != null) {
                compiled.add(program);
            }
        }
        return List.copyOf(compiled);
    }

    protected void deleteComputePrograms() {
        computeProgramArrays.values().stream()
                .flatMap(List::stream)
                .forEach(ComputeProgram::delete);
        shadowComputePrograms.forEach(ComputeProgram::delete);
        finalComputePrograms.forEach(ComputeProgram::delete);
    }

    protected void deleteFullscreenArrayPrograms() {
        fullscreenArrayPrograms.values().stream()
                .flatMap(List::stream)
                .forEach(FullscreenArrayProgram::delete);
    }

    protected void logRequestedFeaturesAndCapabilities() {
        if (!packDirectives.features().required().isEmpty() || !packDirectives.features().optional().isEmpty()) {
            MainMod.LOGGER.info(
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
                || capabilities.geometry()
                || capabilities.tessellation()
                || capabilities.extraProgramArrayEntries()) {
            MainMod.LOGGER.info("[Pipeline] Pack capabilities: {}", capabilities);
        }
        if (shaderImages.active()) {
            MainMod.LOGGER.info("[Pipeline] Loaded {} Iris custom image directives", shaderImages.count());
        }
        if (shaderStorageBuffers.active()) {
            MainMod.LOGGER.info("[Pipeline] Loaded {} Iris SSBO directives", shaderStorageBuffers.count());
        }
        if (packDirectives.textureDirectives().rawTextureCount() > 0) {
            MainMod.LOGGER.info(
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

    protected void resetHardwareCompatibilityState() {
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        pipelineTerrainFormatSupported = detectPipelineTerrainFormatSupport();
        hardwareSafeVanillaTerrain = ENABLE_SAFE_TERRAIN_FALLBACKS && !pipelineTerrainFormatSupported;
        hardwareSafeVanillaTerrainReason = hardwareSafeVanillaTerrain ? "missing-pipeline-terrain-format" : "";
    }

    protected void clearShaderedTerrainFallbackState() {
        hardwareSafeVanillaTerrain = false;
        hardwareSafeVanillaTerrainReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        clearShaderedNothiriumGlobalBypassState(true);
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
    }

    protected void clearShaderedNothiriumGlobalBypassState() {
        clearShaderedNothiriumGlobalBypassState(false);
    }

    protected void clearShaderedNothiriumGlobalBypassState(boolean clearTemporaryDrawPaths) {
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        if (clearTemporaryDrawPaths) {
            nothiriumHybridVanillaMaintenanceFrames = 0;
            nothiriumHybridVanillaMaintenanceReason = "";
            nothiriumMainVanillaDrawPathFrames = 0;
            nothiriumMainVanillaDrawPathReason = "";
        }
    }

    protected void clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad(String stage) {
        if (!isPipelineActive || !pipelineTerrainFormatSupported() || !hasUsableShaderTerrainProgram()) {
            return;
        }
        boolean changed = hardwareSafeVanillaTerrain
                || !hardwareSafeVanillaTerrainReason.isEmpty()
                || hardwareSafeVanillaTerrainRefreshCooldown > 0
                || zeroOpaqueTerrainFrames != 0
                || sparseOpaqueTerrainFrames != 0
                || zeroOpaqueTerrainRecoveryRequested
                || softVanillaTerrainRenderer
                || !softVanillaTerrainRendererReason.isEmpty()
                || shaderedNothiriumGlobalBypass
                || !shaderedNothiriumGlobalBypassReason.isEmpty()
                || nothiriumHybridVanillaMaintenanceFrames != 0
                || !nothiriumHybridVanillaMaintenanceReason.isEmpty()
                || nothiriumMainVanillaDrawPathFrames != 0
                || !nothiriumMainVanillaDrawPathReason.isEmpty();
        hardwareSafeVanillaTerrain = false;
        hardwareSafeVanillaTerrainReason = "";
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        positiveNothiriumTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        if (changed) {
            NothiriumBypass.markAllChanged();
            scheduleWorldTerrainRefresh(true, true, 0);
            MainMod.LOGGER.info("[Pipeline] Cleared hardware safe vanilla terrain fallback after loading shader terrain programs: {}", stage);
        }
    }

    protected boolean hasUsableShaderTerrainProgram() {
        return hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_SOLID)
                || hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_CUTOUT)
                || hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP)
                || hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN)
                || hasUsableShaderProgram(RenderPass.GBUFFERS_TEXTURED_LIT)
                || hasUsableShaderProgram(RenderPass.GBUFFERS_TEXTURED);
    }

    protected boolean hasUsableShaderProgram(RenderPass pass) {
        PipelineProgram program = programs.get(pass);
        return program != null && program.effectiveProgram(programs) != null;
    }

    protected boolean detectPipelineTerrainFormatSupport() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS) > ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE;
    }

    protected boolean pipelineTerrainFormatSupported() {
        if (!pipelineTerrainFormatSupported) {
            pipelineTerrainFormatSupported = detectPipelineTerrainFormatSupport();
        }
        return pipelineTerrainFormatSupported;
    }

    protected void logHardwareCapabilities(String stage, ShaderPackDirectives directives) {
        if (hardwareCapabilityLogs >= MAX_HARDWARE_CAPABILITY_LOGS) {
            return;
        }
        hardwareCapabilityLogs++;

        org.lwjgl.opengl.ContextCapabilities caps = GLContext.getCapabilities();
        int maxVertexAttribs = safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        int maxDrawBuffers = caps.OpenGL20 ? safeGetInteger(GL20.GL_MAX_DRAW_BUFFERS) : 1;
        int maxColorAttachments = caps.OpenGL30 ? safeGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS) : 1;
        int maxTextureUnits = caps.OpenGL20 ? safeGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) : safeGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
        int maxImageUnits = caps.OpenGL42 ? safeGetInteger(GL42.GL_MAX_IMAGE_UNITS) : 0;
        int maxSsboBindings = caps.OpenGL43 ? safeGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) : 0;
        ShaderPipelineCapabilities requested = directives != null ? directives.capabilities() : null;
        boolean requestedCompute = requested != null && requested.compute();
        boolean requestedImages = requested != null && requested.images();
        boolean requestedSsbo = requested != null && requested.storageBuffers();
        boolean requestedGeometry = requested != null && requested.geometry();
        boolean requestedTessellation = requested != null && requested.tessellation();

        MainMod.LOGGER.info(
                "[AUSMHardware] stage={} vendor='{}' renderer='{}' version='{}' gl20={} gl30={} gl32={} gl40={} gl42={} gl43={} arbCompute={} arbImages={} arbSsbo={} arbDrawBuffersBlend={} arbTessellation={} fboEnabled={} maxAttribs={} maxDrawBuffers={} maxColorAttachments={} maxTextureUnits={} maxImageUnits={} maxSsboBindings={} requiredAttribs={} requestedCompute={} requestedImages={} requestedSsbo={} requestedGeometry={} requestedTessellation={}",
                stage,
                safeGetString(GL11.GL_VENDOR),
                safeGetString(GL11.GL_RENDERER),
                safeGetString(GL11.GL_VERSION),
                caps.OpenGL20,
                caps.OpenGL30,
                caps.OpenGL32,
                caps.OpenGL40,
                caps.OpenGL42,
                caps.OpenGL43,
                caps.GL_ARB_compute_shader,
                caps.GL_ARB_shader_image_load_store,
                caps.GL_ARB_shader_storage_buffer_object,
                caps.GL_ARB_draw_buffers_blend,
                caps.GL_ARB_tessellation_shader,
                com.l.ausm.impl.util.MinecraftReflectionCompat.isFramebufferEnabled(),
                maxVertexAttribs,
                maxDrawBuffers,
                maxColorAttachments,
                maxTextureUnits,
                maxImageUnits,
                maxSsboBindings,
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE + 1,
                requestedCompute,
                requestedImages,
                requestedSsbo,
                requestedGeometry,
                requestedTessellation
        );

        if (maxVertexAttribs <= ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE) {
            MainMod.LOGGER.warn(
                    "[AUSMHardware] GPU exposes only {} vertex attribs; pipeline terrain metadata needs attribute index {}. Nothirium shader terrain will be bypassed if terrain fails.",
                    maxVertexAttribs,
                    ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE
            );
        }
        if (requestedCompute && !caps.OpenGL43 && !caps.GL_ARB_compute_shader) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests compute programs, but OpenGL 4.3 is unavailable.");
        }
        if (requestedImages && !caps.OpenGL42 && !caps.GL_ARB_shader_image_load_store) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests custom image load/store, but OpenGL 4.2 is unavailable.");
        }
        if (requestedSsbo && !caps.OpenGL43 && !caps.GL_ARB_shader_storage_buffer_object) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests SSBOs, but OpenGL 4.3 is unavailable.");
        }
        if (requestedGeometry && !caps.OpenGL32) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests geometry shaders, but OpenGL 3.2 is unavailable.");
        }
        if (requestedTessellation && !caps.OpenGL40 && !caps.GL_ARB_tessellation_shader) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests tessellation shaders, but OpenGL 4.0 is unavailable.");
        }
    }

    public int blockEntityId(IBlockState state) {
        return blockEntityId(state, null, null);
    }

    public int blockEntityId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }

        return blockEntityIdForActualState(actualLightState(state, blockAccess, pos), blockAccess, pos);
    }

    public int blockEntityIdForActualState(IBlockState pipelineState, IBlockAccess blockAccess, BlockPos pos) {
        if (pipelineState == null) {
            return 0;
        }

        ShaderBlockIdMap.BlockIdRules blockIds = shaderProperties.blockIds();
        if (!blockIds.isEmpty()) {
            int id = blockIds.idFor(pipelineState);
            if (id != 0) {
                logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, id, "mapped");
                return id;
            }
        }

        int waterLikeFallbackId = waterLikeFluidFallbackId(pipelineState);
        if (waterLikeFallbackId != 0) {
            logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, waterLikeFallbackId, "water-like-fallback");
            return waterLikeFallbackId;
        }

        logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, 0, "unmapped");
        return 0;
    }

    public int customLiquidTintColor(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return -1;
    }

    protected void logWaterLikeMaterialProbe(IBlockState state, IBlockAccess blockAccess, BlockPos pos, int id, String source) {
        if (state == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            return;
        }

        int call = waterLikeMaterialProbeCount.incrementAndGet();
        if (call > MAX_FLUID_MATERIAL_PROBE_LOGS) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMFluidMaterialProbe] call={} source={} id={} registry={} state={} pos={} access={}",
                call,
                source,
                id,
                registryName(state),
                state,
                pos,
                blockAccess != null ? blockAccess.getClass().getName() : "null"
        );
    }

    protected static int waterLikeFluidFallbackId(IBlockState state) {
        if (state == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            return 0;
        }

        ResourceLocation name = registryName(state);
        if (name == null) {
            return 0;
        }

        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name);
        if ("minecraft".equals(namespace)) {
            return ("water".equals(path) || "flowing_water".equals(path)) ? 32000 : 0;
        }
        if ("actuallyadditions".equals(namespace)) return 32621;
        if ("buildcraftenergy".equals(namespace) || "buildcraftfactory".equals(namespace)) return 32620;
        if ("enderio".equals(namespace)) return 32622;
        if ("cyclicmagic".equals(namespace)) return 32623;
        if ("immersiveengineering".equals(namespace) || "immersivepetroleum".equals(namespace)) return 32624;
        if ("gendustry".equals(namespace) || "binniecore".equals(namespace) || "binnie-mods".equals(namespace)) return 32625;
        if ("advancedrocketry".equals(namespace)) return 32626;
        if ("abyssalcraft".equals(namespace) || "acintegration".equals(namespace)) return 32627;
        if ("bloodmagic".equals(namespace) || "bloodarsenal".equals(namespace)) return 32628;
        if ("erebus".equals(namespace)) return 32629;
        if ("thaumcraft".equals(namespace)) return 32630;
        if ("thebetweenlands".equals(namespace)) return 32631;
        if ("thermalfoundation".equals(namespace)) return 32632;
        if ("tconstruct".equals(namespace) || "plustic".equals(namespace) || "iceandfire".equals(namespace)) return 32633;
        if ("biomesoplenty".equals(namespace)) return 32634;
        if ("forestry".equals(namespace)) return 32635;
        if ("industrialforegoing".equals(namespace)) return 32636;
        if ("railcraft".equals(namespace)) return 32637;
        if ("bigreactors".equals(namespace)) return 32638;
        if ("hatchery".equals(namespace)) return 32639;
        if ("extrabotany".equals(namespace) || ("botania".equals(namespace) && path.contains("mana"))) return 32641;
        if ("integrateddynamics".equals(namespace)) return 32642;
        if ("astralsorcery".equals(namespace)) return 32643;
        if ("animus".equals(namespace)) return 32644;
        return 32645;
    }

    public int blockMetadata(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return blockMetadata(actualLightState(state, blockAccess, pos));
    }

    public int blockMetadataForActualState(IBlockState actualState) {
        return blockMetadata(actualState);
    }

    public IBlockState actualBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return actualLightState(state, blockAccess, pos);
    }

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return effectiveBlockRenderState(state, actualLightState(state, blockAccess, pos), blockAccess, pos);
    }

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockState actualState,
                                                 IBlockAccess blockAccess, BlockPos pos) {
        IBlockState inherited = inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (inherited != null) {
            return inherited;
        }
        return actualState;
    }

    public IBlockState inheritedBlockcrafteryRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return null;
        }
        GpomFramedMaterialCompat.Material material = GpomFramedMaterialCompat.material(blockAccess, pos);
        return material.present() ? material.primary() : null;
    }

    public boolean shouldProbeBlockcrafteryTransparency(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state) || blockAccess == null || pos == null) {
            return false;
        }
        return gpomFramedMaterialEmission(state, blockAccess, pos) > 0
                || gpomFramedMaterialHasBloom(state, blockAccess, pos);
    }

    public void logBlockcrafteryTransparencyProbe(String source, IBlockState state, IBlockAccess blockAccess,
                                                  BlockPos pos, BlockRenderLayer layer, Integer startVertex,
                                                  Integer endVertex, Boolean result, String detail) {
        if (!shouldProbeBlockcrafteryTransparency(state, blockAccess, pos)) {
            return;
        }
        IBlockState decoratedState = inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        int dimension = safeDimensionId(blockAccess instanceof World world ? world : null);
        int start = startVertex != null ? startVertex : -1;
        int end = endVertex != null ? endVertex : -1;
        int delta = start >= 0 && end >= 0 ? end - start : -1;
        String key = source
                + "|" + dimension
                + "|" + formatBlockPos(pos)
                + "|" + stateName(state)
                + "|" + stateName(decoratedState)
                + "|" + String.valueOf(layer)
                + "|" + String.valueOf(result)
                + "|" + start
                + "|" + end
                + "|" + String.valueOf(detail);
        if (!blockcrafteryTransparencyProbeKeys.add(key)) {
            return;
        }
        int count = blockcrafteryTransparencyProbeCount.incrementAndGet();
        if (count > MAX_BLOCKCRAFTERY_TRANSPARENCY_PROBES) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBlockcrafteryTransparencyProbe] call={} source={} pipelineActive={} shaderless={} phase={} dim={} pos={} layer={} result={} start={} end={} delta={} state={} decorated={} decoratedInfo={} detail={}",
                count,
                source,
                isPipelineActive,
                !isPipelineActive,
                getPhase(),
                dimension,
                formatBlockPos(pos),
                layer,
                result,
                start,
                end,
                delta,
                stateName(state),
                stateName(decoratedState),
                blockcrafteryTransparencyStateInfo(decoratedState, blockAccess, pos, layer),
                detail
        );
    }

    protected boolean isBlockcrafteryTransparencyProbeDecoratedState(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null || isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        ResourceLocation name = registryName(state);
        String namespace = name != null && com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name) != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(Locale.ROOT)
                : "";
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String blockClass = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state).getClass().getName().toLowerCase(Locale.ROOT);
        BlockRenderLayer naturalLayer = safeRenderLayer(state);
        boolean transparentLayer = naturalLayer == BlockRenderLayer.TRANSLUCENT
                || naturalLayer == BlockRenderLayer.CUTOUT
                || naturalLayer == BlockRenderLayer.CUTOUT_MIPPED
                || canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                || canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                || canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED);
        boolean transparentIdentity = namespace.contains("enderio")
                || path.contains("glass")
                || path.contains("clear")
                || path.contains("fused")
                || path.contains("quartz")
                || path.contains("transparent")
                || path.contains("translucent")
                || blockClass.contains("glass")
                || blockClass.contains("transparent")
                || blockClass.contains("translucent");
        return transparentLayer || transparentIdentity;
    }

    protected String blockcrafteryTransparencyStateInfo(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                      BlockRenderLayer currentLayer) {
        if (state == null) {
            return "null";
        }
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        return "{renderType=" + safeRenderType(state)
                + ", naturalLayer=" + safeRenderLayer(state)
                + ", canCurrent=" + canRenderInLayer(state, currentLayer)
                + ", canSolid=" + canRenderInLayer(state, BlockRenderLayer.SOLID)
                + ", canCutoutMipped=" + canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)
                + ", canCutout=" + canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                + ", canTranslucent=" + canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                + ", opaque=" + safeOpaqueCube(state)
                + ", full=" + safeFullCube(state)
                + ", material=" + (com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterial(state) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterial(state) : "null")
                + ", light=" + safeLightValue(state, blockAccess, pos)
                + ", class=" + (block != null ? block.getClass().getName() : "null")
                + "}";
    }

    public IBlockState inheritedBloomRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return null;
        }

        IBlockState[] inheritedStates = inheritedRenderStates(state, blockAccess, pos);
        for (IBlockState inheritedState : inheritedStates) {
            if (isBloomOrEmissiveInheritedState(inheritedState, blockAccess, pos)) {
                return inheritedState;
            }
        }
        if (inheritedStates.length > 0) {
            return inheritedStates[0];
        }
        return actualLightState(state, blockAccess, pos);
    }

    public IBlockState firstInheritedRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState[] inheritedStates = inheritedRenderStates(state, blockAccess, pos);
        return inheritedStates.length > 0 ? inheritedStates[0] : null;
    }

    public IBlockState inheritedBloomGeometryRenderState(IBlockState state, IBlockState inheritedState) {
        return inheritedState != null ? inheritedState : state;
    }

    public boolean isFramedBlockDiagnosticTarget(IBlockState state) {
        return isBlockcrafteryEditableBlock(state);
    }

    public boolean framedBlockDiagnosticsEnabled() {
        return true;
    }

    public boolean currentProblemProbesEnabled() {
        return false;
    }

    protected static boolean debugProbeLoggingEnabled() {
        return false;
    }

    public boolean isBlockcrafteryEditableState(IBlockState state) {
        return isBlockcrafteryEditableBlock(state);
    }

    public void applyFramedQuadMaterial(String spriteName) {
        if (!BlockRenderContext.hasWorldBlockContext()) {
            return;
        }
        IBlockAccess blockAccess = BlockRenderContext.blockAccess();
        BlockPos pos = BlockRenderContext.blockPos();
        IBlockState framedState = com.l.ausm.impl.util.MinecraftReflectionCompat.blockAccessBlockState(blockAccess, pos);
        if (!isBlockcrafteryEditableBlock(framedState)) {
            return;
        }

        GpomFramedMaterialCompat.Material material = GpomFramedMaterialCompat.material(blockAccess, pos);
        IBlockState ownerState = GpomFramedMaterialCompat.stateForSprite(material, spriteName);
        if (ownerState == null) {
            BlockRenderContext.clearQuadOverrides();
            BlockRenderContext.setQuadFramedBloomBoost(false);
            return;
        }

        int ownerId = blockEntityIdForActualState(ownerState, blockAccess, pos);
        int ownerEmission = blockRenderEmissionForState(ownerState, blockAccess, pos);
        BlockRenderContext.setQuadBlockMetadata(
                ownerId,
                (short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(ownerState),
                blockMetadataForActualState(ownerState),
                ownerEmission
        );
        BlockRenderContext.setQuadFramedBloomBoost(
                ownerEmission > 0 || stateHasBloomLayerGeometry(ownerState)
        );
        int probe = framedQuadMaterialProbeCount.incrementAndGet();
        if (probe <= 0) {
            MainMod.LOGGER.info("[AUSMFramedQuadMaterialProbe] call={} pos={} sprite={} primary={} secondary={} owner={} id={} emission={} layer={}",
                    probe, pos, spriteName, diagnosticStateName(material.primary()),
                    diagnosticStateName(material.secondary()), diagnosticStateName(ownerState),
                    ownerId, ownerEmission, com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
        }
    }

    public boolean shouldUseCeleritasForgeFallback(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsFire(state)
                || isCeleritasTwilightPortalState(state)) {
            return true;
        }
        // ArchitectureCraft's shape model consumes TileShape material and
        // shape data through its Forge dispatcher. Celeritas's compact model
        // encoder only sees the BlockShape host and collapses it to a cube.
        if (isArchitectureCraftShapeBlock(state)) {
            return true;
        }
        if (isArchitectureCraftSawbench(state)) {
            return true;
        }
        // Celeritas's native path correctly encodes ordinary emissive models
        // into their real terrain layer. Sending them back through Forge loses
        // the extended vertex data that the shader uses for the base image and
        // bloom. Only renderers which actually require Forge's model contract
        // stay on the compatibility path.
        // With a shader pack, Lumenized deliberately folds its BLOOM quads
        // into the normal terrain pass. Celeritas's direct model route misses
        // that second quad query, leaving only the synthetic BLOOM mesh and
        // therefore a source with no opaque/base representation.
        if (stateHasBloomLayerGeometry(state)) {
            return true;
        }
        return false;
    }

    protected static boolean isArchitectureCraftSawbench(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && "architecturecraft".equalsIgnoreCase(
                com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && "sawbench".equals(
                com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name));
    }

    public boolean shouldUseCeleritasForgeFallback(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (isBlockcrafteryEditableState(state)) {
            return true;
        }
        if (blockAccess != null
                && pos != null
                && (gpomFramedMaterialEmission(state, blockAccess, pos) > 0
                || gpomFramedMaterialHasBloom(state, blockAccess, pos))) {
            return true;
        }
        return shouldUseCeleritasForgeFallback(state);
    }

    public boolean shouldUseCeleritasLayerNeutralForgeDispatch(IBlockState state) {
        return false;
    }

    public boolean isCeleritasPortalState(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = registryName(state);
        String path = name != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name) : "";
        String blockClass = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state)
                .getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return path.contains("portal") || blockClass.contains("portal");
    }

    public boolean isCeleritasTwilightPortalState(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        return "twilightforest".equalsIgnoreCase(namespace) && path.contains("portal");
    }

    public boolean stateHasBloomLayerGeometry(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (isExplicitBloomState(state)) {
            return true;
        }
        return stateHasBloomResourceGeometry(state);
    }

    /** Allows Celeritas's BLOOM-only blocks to retain their normal base geometry. */
    public boolean shouldRenderBloomSourceInBaseLayer(IBlockState state, BlockRenderLayer layer) {
        if (state == null || layer == null
                || AusmBloomLayer.isBloomLayer(layer)) {
            return false;
        }
        // A compatibility mixin may move a BLOOM-only block back into a
        // vanilla layer. Preserve that declared layer before consulting the
        // block's original render-layer preference.
        if (canRenderInLayer(state, layer)) {
            return true;
        }
        if (isCeleritasTwilightPortalState(state)) {
            return layer == BlockRenderLayer.TRANSLUCENT;
        }
        if (!(isExplicitBloomState(state) || stateHasBloomLayerGeometry(state)
                || stateHasBloomResourceGeometry(state) || isLumenizedBloomState(state))) {
            return false;
        }
        BlockRenderLayer naturalLayer = safeRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return layer == naturalLayer;
        }
        return layer == BlockRenderLayer.CUTOUT;
    }

    public void logFramedBlockDiagnostic(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                         BlockRenderLayer layer, int startVertex, int endVertex, Boolean result,
                                         String extra) {
        if (!debugProbeLoggingEnabled()) {
            return;
        }
        if (!FRAMED_BLOCK_DIAGNOSTICS_ENABLED) {
            return;
        }
        if (!isFramedBlockDiagnosticTarget(state)) {
            return;
        }

        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedBloomState = inheritedBloomRenderState(state, blockAccess, pos);
        if (isBlockcrafteryEditableBlock(state)
                && blockcrafteryLightEmission(state) <= 0
                && !isBloomOrEmissiveInheritedState(inheritedBloomState, blockAccess, pos)) {
            return;
        }
        IBlockState inheritedGeometryState = inheritedBloomGeometryRenderState(state, inheritedBloomState);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        boolean priority = isPriorityFramedDiagnosticName(state)
                || isPriorityFramedDiagnosticState(effectiveState, blockAccess, pos, bloomLayer)
                || isPriorityFramedDiagnosticState(inheritedBloomState, blockAccess, pos, bloomLayer)
                || (inheritedGeometryState != state
                && isPriorityFramedDiagnosticState(inheritedGeometryState, blockAccess, pos, bloomLayer));

        String key = source
                + "|" + safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + formatBlockPos(pos)
                + "|" + stateName(state)
                + "|" + String.valueOf(layer)
                + "|" + stateName(effectiveState)
                + "|" + stateName(inheritedBloomState)
                + "|" + String.valueOf(priority ? extra : "");
        if (!framedBlockDiagnosticKeys.add(key)) {
            return;
        }

        int count = nextFramedDiagnosticCount(state, priority);
        if (count < 0) {
            return;
        }

        int delta = startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1;

        MainMod.LOGGER.info(
                "[AUSMFramedDiag] call={} priority={} kind={} source={} dim={} pos={} layer={} bloomLayer={} result={} start={} end={} delta={} access={} extra={} original={} effective={} inheritedBloom={} inheritedGeometry={} inheritedStates={}",
                count,
                priority,
                framedDiagnosticKind(state),
                source,
                safeDimensionId(blockAccess instanceof World world ? world : null),
                formatBlockPos(pos),
                layer,
                bloomLayer,
                result,
                startVertex,
                endVertex,
                delta,
                blockAccess != null ? blockAccess.getClass().getName() : "null",
                extra,
                framedDiagnosticState("original", state, blockAccess, pos, layer, bloomLayer),
                framedDiagnosticState("effective", effectiveState, blockAccess, pos, layer, bloomLayer),
                framedDiagnosticState("inheritedBloom", inheritedBloomState, blockAccess, pos, layer, bloomLayer),
                framedDiagnosticState("inheritedGeometry", inheritedGeometryState, blockAccess, pos, layer, bloomLayer),
                framedDiagnosticInheritedStates(state, blockAccess, pos, layer, bloomLayer)
        );
    }

    public int blockRenderEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        int emission = isPipelineActive && !shaderlessBloomExtractionActive
                ? explicitShaderedBlockEmission(state, blockAccess, pos)
                : blockRenderEmissionForState(state, blockAccess, pos);
        return isBlockcrafteryEditableBlock(state)
                ? Math.max(emission, gpomFramedMaterialEmission(state, blockAccess, pos))
                : emission;
    }

    public boolean shouldUseShaderlessBloomEmission() {
        return false;
    }

    public boolean isManualBloomExtractionEnabled() {
        return false;
    }

    public int blockShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return 0;
    }

    public boolean stateHasShaderlessBloomSource(IBlockState state) {
        if (isPipelineActive && !shaderlessBloomExtractionActive) {
            return false;
        }
        return blockShaderlessBloomEmission(state, null, null) > 0;
    }

    public boolean stateUsesTextureBloomSource(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null || isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        return stateHasBloomResourceGeometry(state) || isLumenizedBloomState(state);
    }

    /** Celeritas needs full-bright lightmap UVs for Lumenized-compatible bloom sources. */
    public boolean shouldForceCeleritasGeometryBloomFullbright(IBlockState state, BlockRenderLayer layer) {
        if (!stateHasBloomLayerGeometry(state) || stateUsesTextureBloomSource(state)) {
            return false;
        }
        return AusmBloomLayer.isBloomLayer(layer)
                || shouldRenderBloomSourceInBaseLayer(state, layer);
    }

    protected int explicitShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (stateHasBloomLayerGeometry(state) || stateHasBloomResourceGeometry(state) || isLumenizedBloomState(state)) {
            return shaderlessBloomGeometryEmission(state, blockAccess, pos);
        }
        if (shaderlessHighLightEmission(state, blockAccess, pos) > 0) {
            return SHADERLESS_BLOOM_GEOMETRY_EMISSION;
        }
        return 0;
    }

    protected int shaderlessHighLightEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        try {
            int light = blockAccess != null && pos != null
                    ? com.l.ausm.impl.util.MinecraftReflectionCompat.stateLightValue(state, blockAccess, pos)
                    : intrinsicBlockEmission(state);
            return light > 0 ? SHADERLESS_BLOOM_GEOMETRY_EMISSION : 0;
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    protected int shaderlessBloomGeometryEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (blockRenderEmissionForState(state, blockAccess, pos) > 0) {
            return SHADERLESS_LIGHT_EMITTING_BLOOM_GEOMETRY_EMISSION;
        }
        return SHADERLESS_BLOOM_GEOMETRY_EMISSION;
    }

    public int blockRenderEmissionWithFramedInheritance(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return blockRenderEmission(state, blockAccess, pos);
    }

    public int shaderlessFramedBloomExtractionEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return 0;
    }

    public int framedBloomFallbackEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return 0;
    }

    public boolean shouldInheritFramedEmissionInBasePass(IBlockState state) {
        return !isPipelineActive && isFramedBlockDiagnosticTarget(state);
    }

    public int blockRenderAlpha(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return -1;
        }
        if (isCurrentProblemProbeTarget(state) || isCurrentProblemProbeTarget(effectiveState)) {
            logCurrentProblemProbe("alpha-query", state, blockAccess, pos,
                    "effective=" + stateName(effectiveState)
                            + ", alpha=-1"
                            + ", originalOpaque=" + safeOpaqueCube(state)
                            + ", originalFull=" + safeFullCube(state)
                            + ", effectiveOpaque=" + safeOpaqueCube(effectiveState)
                            + ", effectiveFull=" + safeFullCube(effectiveState)
                            + ", originalLayer=" + safeRenderLayer(state)
                            + ", effectiveLayer=" + safeRenderLayer(effectiveState)
                            + ", layer=" + com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer());
        }
        return -1;
    }

    public void setBlockRenderDebugContext(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        BlockRenderContext.setDebugBlock(
                diagnosticBlockKind(state, effectiveState, blockAccess, pos),
                stateName(state),
                stateName(effectiveState)
        );
    }

    public String diagnosticStateName(IBlockState state) {
        return stateName(state);
    }

    public String diagnosticBlockKind(IBlockState state, IBlockState effectiveState) {
        return diagnosticBlockKind(state, effectiveState, null, null);
    }

    protected String diagnosticBlockKind(IBlockState state, IBlockState effectiveState, IBlockAccess blockAccess, BlockPos pos) {
        if (isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        if (isPriorityFramedDiagnosticName(state) || isPriorityFramedDiagnosticName(effectiveState)) {
            return "emissive-name";
        }
        if (blockRenderEmission(state, blockAccess, pos) > 0
                || blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || blockEntityId(state, blockAccess, pos) != 0
                || blockEntityId(effectiveState, blockAccess, pos) != 0) {
            return "active-light-or-id";
        }
        return "other";
    }

    public boolean isCurrentProblemProbeTarget(IBlockState state) {
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return false;
        }
        return isPriorityFramedDiagnosticName(state)
                || isAstralCrystalCluster(state)
                || stateName(state).contains("lumenized")
                || stateName(state).contains("glow")
                || stateName(state).contains("emissive")
                || stateName(state).contains("shimmer")
                || stateName(state).contains("shinyflower")
                || stateName(state).contains("nitor")
                || stateName(state).contains("crystal");
    }

    public boolean shouldProbeSoftVanillaSpecialBlock(IBlockState state, IBlockState effectiveState,
                                                      IBlockAccess blockAccess, BlockPos pos) {
        if (!isComplementarySoftVanillaStartupFallbackActive()) {
            return false;
        }
        if (softVanillaSpecialBlockProbeLogs >= MAX_SOFT_VANILLA_SPECIAL_BLOCK_PROBE_LOGS) {
            return false;
        }
        return isSoftVanillaSpecialProbeState(state)
                || isSoftVanillaSpecialProbeState(effectiveState)
                || blockRenderEmission(state, blockAccess, pos) > 0
                || blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || blockEntityId(state, blockAccess, pos) != 0
                || blockEntityId(effectiveState, blockAccess, pos) != 0;
    }

    protected boolean isSoftVanillaSpecialProbeState(IBlockState state) {
        if (state == null) {
            return false;
        }
        String name = stateName(state).toLowerCase(Locale.ROOT);
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
        return name.contains("quantumthings")
                || name.contains("lumen")
                || name.contains("portal")
                || name.contains("emissive")
                || name.contains("glow")
                || name.contains("nitor")
                || name.contains("shimmer")
                || name.contains("crystal")
                || name.contains("astral")
                || className.contains("quantumthings")
                || className.contains("lumen")
                || className.contains("portal")
                || className.contains("emissive")
                || className.contains("glow");
    }

    public void logSoftVanillaSpecialBlockProbe(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                int startVertex, int endVertex, Boolean result, String detail) {
        if (!isComplementarySoftVanillaStartupFallbackActive()) {
            return;
        }
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        if (!shouldProbeSoftVanillaSpecialBlock(state, effectiveState, blockAccess, pos)) {
            return;
        }
        String key = source
                + "|" + safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + formatBlockPos(pos)
                + "|" + String.valueOf(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer())
                + "|" + stateName(state)
                + "|" + stateName(effectiveState);
        if (!softVanillaSpecialBlockProbeKeys.add(key)) {
            return;
        }
        int count = ++softVanillaSpecialBlockProbeLogs;
        if (count > MAX_SOFT_VANILLA_SPECIAL_BLOCK_PROBE_LOGS) {
            return;
        }
        int delta = startVertex >= 0 && endVertex >= 0 ? endVertex - startVertex : -1;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaBlockProbe] call={} source={} dim={} pos={} layer={} frame={} phase={} state={} effective={} renderLayer={} effectiveRenderLayer={} emission={} effectiveEmission={} blockId={} effectiveBlockId={} start={} end={} delta={} result={} detail={}",
                count,
                source,
                safeDimensionId(blockAccess instanceof World world ? world : null),
                formatBlockPos(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                pipelineFrameId,
                getPhase(),
                stateName(state),
                stateName(effectiveState),
                safeRenderLayer(state),
                safeRenderLayer(effectiveState),
                blockRenderEmission(state, blockAccess, pos),
                blockRenderEmission(effectiveState, blockAccess, pos),
                blockEntityId(state, blockAccess, pos),
                blockEntityId(effectiveState, blockAccess, pos),
                startVertex,
                endVertex,
                delta,
                result,
                detail
        );
    }

    public void logCurrentProblemProbe(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                       String detail) {
        if (!debugProbeLoggingEnabled()) {
            return;
        }
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedState = inheritedBloomRenderState(state, blockAccess, pos);
        boolean activeLightOrId = blockRenderEmission(state, blockAccess, pos) > 0
                || blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || blockRenderEmission(inheritedState, blockAccess, pos) > 0
                || blockEntityId(state, blockAccess, pos) != 0
                || blockEntityId(effectiveState, blockAccess, pos) != 0
                || blockEntityId(inheritedState, blockAccess, pos) != 0;
        if (!activeLightOrId
                && !isCurrentProblemProbeTarget(state)
                && !isCurrentProblemProbeTarget(effectiveState)
                && !isCurrentProblemProbeTarget(inheritedState)) {
            return;
        }

        String key = source
                + "|" + safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + formatBlockPos(pos)
                + "|" + String.valueOf(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer())
                + "|" + stateName(state)
                + "|" + stateName(effectiveState)
                + "|" + stateName(inheritedState)
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        int count = activeLightOrId
                ? activeLightOrIdProbeCount.incrementAndGet()
                : currentProblemProbeCount.incrementAndGet();
        int limit = activeLightOrId ? MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS : MAX_CURRENT_PROBLEM_PROBE_LOGS;
        if (count > limit) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} activeLightOrId={} dim={} pos={} layer={} bloomLayer={} state={} effective={} inherited={} emission={} inheritedEmission={} alpha={} blockId={} inheritedBlockId={} detail={}",
                count,
                source,
                diagnosticBlockKind(state, effectiveState, blockAccess, pos),
                activeLightOrId,
                safeDimensionId(blockAccess instanceof World world ? world : null),
                formatBlockPos(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                AusmBloomLayer.layer(),
                framedDiagnosticState("state", state, blockAccess, pos, com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                framedDiagnosticState("effective", effectiveState, blockAccess, pos, com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                framedDiagnosticState("inherited", inheritedState, blockAccess, pos, com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                blockRenderEmission(state, blockAccess, pos),
                blockRenderEmissionWithFramedInheritance(state, blockAccess, pos),
                -1,
                blockEntityId(state, blockAccess, pos),
                blockEntityId(inheritedState, blockAccess, pos),
                detail
        );
    }

    public void logCurrentRenderContextProbe(String source, String detail) {
        if (!debugProbeLoggingEnabled()) {
            return;
        }
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        String kind = BlockRenderContext.debugKind();
        if (!"blockcraftery".equals(kind)
                && !"architecturecraft".equals(kind)
                && !"emissive-name".equals(kind)
                && !"active-light-or-id".equals(kind)) {
            return;
        }
        if ("blockcraftery".equals(kind) && BlockRenderContext.blockEmission() <= 0 && BlockRenderContext.blockEntityId() == 0) {
            return;
        }

        String key = source
                + "|" + kind
                + "|" + BlockRenderContext.debugState()
                + "|" + BlockRenderContext.debugEffectiveState()
                + "|" + String.valueOf(com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer())
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        boolean activeLightOrId = "active-light-or-id".equals(kind)
                || BlockRenderContext.blockEmission() > 0
                || BlockRenderContext.blockEntityId() != 0;
        int count = activeLightOrId
                ? activeLightOrIdProbeCount.incrementAndGet()
                : currentProblemProbeCount.incrementAndGet();
        int limit = activeLightOrId ? MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS : MAX_CURRENT_PROBLEM_PROBE_LOGS;
        if (count > limit) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} activeLightOrId={} layer={} state={} effective={} contextEmission={} contextAlpha={} blockId={} bloomMask={} detail={}",
                count,
                source,
                kind,
                activeLightOrId,
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer(),
                BlockRenderContext.debugState(),
                BlockRenderContext.debugEffectiveState(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.blockAlpha(),
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.bloomMaskFallback(),
                detail
        );
    }

    public void probeShaderlessLightState(String stage) {
        // Probe disabled.
    }

    protected String shaderlessWorldLightSummary(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return "none";
        }
        BlockPos feet = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        BlockPos eye = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) + com.l.ausm.impl.util.MinecraftReflectionCompat.eyeHeight(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)));
        return "feet{" + shaderlessWorldLightAt(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), feet) + "}"
                + ",eye{" + shaderlessWorldLightAt(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), eye) + "}";
    }

    protected String shaderlessWorldLightAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return "none";
        }
        try {
            boolean loaded = com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, pos);
            int combined = loaded ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((world), new String[] {"func_175626_b", "getCombinedLight"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class, int.class}, 0, (pos), (0)) : -1;
            int sky = loaded ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos) : -1;
            int block = loaded ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.BLOCK, pos) : -1;
            boolean canSeeSky = loaded && com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((world), new String[] {"func_175678_i", "canSeeSky"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, false, (pos));
            int dynamic = DynamicLightManager.lightAt(pos);
            return "pos=" + pos
                    + ",loaded=" + loaded
                    + ",sky=" + sky
                    + ",block=" + block
                    + ",combined=0x" + Integer.toHexString(combined)
                    + ",canSeeSky=" + canSeeSky
                    + ",dyn=" + dynamic;
        } catch (RuntimeException | LinkageError e) {
            return "pos=" + pos + ",error=" + e.getClass().getName();
        }
    }

    public void probeShaderlessSkyGuiState(String stage) {
        // Probe disabled.
    }

    public void freshSkyProbe(String stage, String detail) {
        // Probe disabled.
    }

    protected String freshSkySamples(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        try {
            int width = com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc);
            int height = com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc);
            return "center=" + readFramebufferPixel(width / 2, height / 2)
                    + ";upper=" + readFramebufferPixel(width / 2, Math.max(0, height * 3 / 4))
                    + ";lower=" + readFramebufferPixel(width / 2, Math.max(0, height / 4));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        }
    }

    protected boolean shouldLogShaderedVoidSkyProbe() {
        return false;
    }

    public void logVoidSkyStageProbe(String stage, String detail) {
        // Probe disabled.
    }

    protected void logShaderedVoidSkyTargetProbe(String stage, Framebuffer target) {
        if (!DEBUG_PROBES_ENABLED
                || !isPipelineActive
                || target == null
                || stage == null
                || !stage.contains("final")
                || shaderedVoidSkyTargetProbeLogs++ >= 12) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMShaderedVoidSkyTargetProbe] call={} stage={} screen={} paused={} target={} color={} depth={} drawFbo={} readFbo={} drawBuf={} readBuf={}",
                shaderedVoidSkyTargetProbeLogs,
                stage,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null
                        ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName() : "none",
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc),
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER)
        );
    }

    protected void logShaderedVoidSkyAttachmentProbe(String stage, String detail) {
        // Probe disabled.
    }

    public void probeShaderedPresentationState(String stage) {
        // Probe disabled.
    }

    protected void logSkyDomeProbe(String stage, String detail, Framebuffer target) {
        // Probe disabled.
    }

    protected void logWorldPassSkyDomeProbe(String stage) {
        // Probe disabled.
    }

    protected boolean claimSkyDomeProbeBudget(Minecraft mc) {
        String tier = skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return skyDomePauseProbeLogs++ < MAX_SKY_DOME_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return skyDomeGuiProbeLogs++ < MAX_SKY_DOME_GUI_PROBE_LOGS;
        }
        return skyDomeProbeLogs++ < MAX_SKY_DOME_WORLD_PROBE_LOGS;
    }

    protected boolean claimWorldPassSkyDomeProbeBudget(Minecraft mc) {
        String tier = skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return worldPassSkyDomePauseProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return worldPassSkyDomeGuiProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_GUI_PROBE_LOGS;
        }
        return worldPassSkyDomeProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_WORLD_PROBE_LOGS;
    }

    protected boolean claimShaderlessSolidTerrainSkyProbeBudget(Minecraft mc) {
        String tier = skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return shaderlessSolidTerrainSkyPauseProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return shaderlessSolidTerrainSkyGuiProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_GUI_PROBE_LOGS;
        }
        return shaderlessSolidTerrainSkyProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_WORLD_PROBE_LOGS;
    }

    protected String skyProbeBudgetTier(Minecraft mc) {
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc)) {
            return "pause";
        }
        if (isGuiSkyProbeState(mc)) {
            return "gui";
        }
        return "world";
    }

    protected boolean isGuiSkyProbeState(Minecraft mc) {
        return renderingGuiScreen()
                || mc != null
                && (com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)));
    }

    protected void logShaderlessSolidTerrainSkyProbe(String stage) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (isPipelineActive
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null
                || isIgnoredShaderlessSkyProbeScreen(mc)
                || !isOverworldShaderEnvironment(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc))
                || !claimShaderlessSolidTerrainSkyProbeBudget(mc)) {
            return;
        }

        String budget = skyProbeBudgetTier(mc);
        int drawFramebuffer = currentDrawFramebufferBinding();
        int readFramebuffer = currentReadFramebufferBinding();
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int readBufferId = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        MainMod.LOGGER.info("[AUSMShaderlessSolidTerrainSkyProbe] stage={} budget={} active={} shaderless={} worldFrame={} pass={} phase={} gui={} screen={} hideGUI={} paused={} world={} sky={} camera={} rays={} gl={} mcTarget={} mcColor={} mcDepth={} drawFbo={} drawBuf={} drawColor={} drawDepth={} readFbo={} readBuf={} readColor={} readDepth={} terrainCounts=solid:{},cutoutMipped:{},cutout:{},translucent:{}",
                stage,
                budget,
                isPipelineActive,
                !isPipelineActive,
                worldFrameActive,
                activePass,
                getPhase(),
                renderingGuiScreen(),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName() : "none",
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc),
                skyProbeWorldSummary(),
                skyDomeSceneSummary(mc),
                skyDomeCameraSummary(mc),
                shaderlessSolidTerrainSampleRays(mc),
                skyDomeGlStateSummary(),
                describeFramebufferTargetDetailed(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                framebufferSamples(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                framebufferDepthSamples(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                drawFramebuffer,
                drawBuffer,
                framebufferIdColorSamples(drawFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), normalizedReadBuffer(drawFramebuffer, drawBuffer)),
                framebufferIdDepthSamples(drawFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), normalizedReadBuffer(drawFramebuffer, drawBuffer)),
                readFramebuffer,
                readBufferId,
                framebufferIdColorSamples(readFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), normalizedReadBuffer(readFramebuffer, readBufferId)),
                framebufferIdDepthSamples(readFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), normalizedReadBuffer(readFramebuffer, readBufferId)),
                shaderlessTerrainSolidCount,
                shaderlessTerrainCutoutMippedCount,
                shaderlessTerrainCutoutCount,
                shaderlessTerrainTranslucentCount);
    }

    protected String shaderlessSolidTerrainSampleRays(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        Entity view = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (view == null) {
            return "view=null";
        }

        int height = com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc);
        int x = Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) / 2);
        String[] names = {"bottomSky", "lower", "center", "upper", "topDome"};
        int[] ys = {
                Math.max(0, Math.min(height - 1, height / 16)),
                Math.max(0, Math.min(height - 1, height * 3 / 16)),
                Math.max(0, Math.min(height - 1, height / 2)),
                Math.max(0, Math.min(height - 1, height * 13 / 16)),
                Math.max(0, Math.min(height - 1, height * 15 / 16))
        };
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(names[i])
                    .append('=')
                    .append(x)
                    .append(',')
                    .append(ys[i])
                    .append(',')
                    .append(shaderlessSolidTerrainRayHit(mc, view, ys[i], height));
        }
        return builder.toString();
    }

    protected String shaderlessSolidTerrainRayHit(Minecraft mc, Entity view, int y, int height) {
        try {
            float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
            double eyeX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(view), partialTicks);
            double eyeY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(view), partialTicks) + com.l.ausm.impl.util.MinecraftReflectionCompat.eyeHeight(view);
            double eyeZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(view), partialTicks);
            Vec3d start = new Vec3d(eyeX, eyeY, eyeZ);
            Vec3d direction = shaderlessProbeScreenRayDirection(mc, view, y, height);
            Vec3d end = com.l.ausm.impl.util.MinecraftReflectionCompat.vecAdd(start, com.l.ausm.impl.util.MinecraftReflectionCompat.vecScale(direction, 512.0D));
            RayTraceResult hit = com.l.ausm.impl.util.MinecraftReflectionCompat.call((com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)), net.minecraft.util.math.RayTraceResult.class, null, new String[] {"func_147447_a", "rayTraceBlocks"},
                new Class<?>[] {net.minecraft.util.math.Vec3d.class, net.minecraft.util.math.Vec3d.class, boolean.class, boolean.class, boolean.class},
                (start), (end), (false), (true), (false));
            if (hit == null || com.l.ausm.impl.util.MinecraftReflectionCompat.field((hit), net.minecraft.util.math.RayTraceResult.Type.class, null, "field_72313_a", "typeOfHit") != RayTraceResult.Type.BLOCK || com.l.ausm.impl.util.MinecraftReflectionCompat.rayTraceBlockPos(hit) == null) {
                return "dir=" + formatVec3d(direction) + ",hit=miss";
            }
            BlockPos pos = com.l.ausm.impl.util.MinecraftReflectionCompat.rayTraceBlockPos(hit);
            IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), pos);
            return "dir=" + formatVec3d(direction)
                    + ",hit=" + formatBlockPos(pos)
                    + ",side=" + com.l.ausm.impl.util.MinecraftReflectionCompat.field((hit), net.minecraft.util.EnumFacing.class, null, "field_178784_b", "sideHit")
                    + ",block=" + registryName(state)
                    + ",state=" + stateName(state)
                    + ",dist=" + com.l.ausm.impl.util.MinecraftReflectionCompat.vecDistance(start, com.l.ausm.impl.util.MinecraftReflectionCompat.field((hit), net.minecraft.util.math.Vec3d.class, null, "field_72307_f", "hitVec"));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        }
    }

    protected Vec3d shaderlessProbeScreenRayDirection(Minecraft mc, Entity view, int y, int height) {
        double ndcY = height <= 1 ? 0.0D : (y / (double) (height - 1)) * 2.0D - 1.0D;
        double fov = com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.fieldFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)), 70.0F, "field_74334_X", "fovSetting") : 70.0D;
        double verticalOffset = Math.toDegrees(Math.atan(ndcY * Math.tan(Math.toRadians(fov) * 0.5D)));
        double pitch = com.l.ausm.impl.util.MinecraftReflectionCompat.rotationPitch(view) - verticalOffset;
        double yaw = com.l.ausm.impl.util.MinecraftReflectionCompat.rotationYaw(view);
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return com.l.ausm.impl.util.MinecraftReflectionCompat.vecNormalize(new Vec3d(
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch));
    }

    protected int normalizedReadBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE && framebuffer == 0) {
            return GL11.GL_BACK;
        }
        return buffer;
    }

    protected String skyDomeSceneSummary(Minecraft mc) {
        World world = renderWorld(mc);
        Entity view = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (world == null) {
            return "world=null";
        }
        float partialTicks = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0F;
        String skyColor = "none";
        try {
            Vec3d color = view != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_72833_a", "getSkyColor"},
                new Class<?>[] {net.minecraft.entity.Entity.class, float.class}, (view), (partialTicks)) : null;
            skyColor = color != null ? formatVec3d(color) : "null";
        } catch (RuntimeException | LinkageError e) {
            skyColor = "error=" + e.getClass().getSimpleName();
        }
        double horizon = Double.NaN;
        float cloudHeight = Float.NaN;
        try {
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null) {
                horizon = com.l.ausm.impl.util.MinecraftReflectionCompat.callDouble((com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)), new String[] {"func_76567_e", "getHorizon"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 63.0D);
                cloudHeight = com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)), new String[] {"func_76571_f", "getCloudHeight"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, 128.0F);
            }
        } catch (RuntimeException | LinkageError ignored) {
            horizon = Double.NaN;
        }
        return "skyColor=" + skyColor
                + ",celestialPartial=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks)
                + ",celestial0=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, 0.0F)
                + ",sunBrightness=" + com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((world), new String[] {"func_72971_b", "getSunBrightness"},
                new Class<?>[] {float.class}, 0.0F, (partialTicks))
                + ",starBrightness=" + com.l.ausm.impl.util.MinecraftReflectionCompat.callFloat((world), new String[] {"func_72880_h", "getStarBrightness"},
                new Class<?>[] {float.class}, 0.0F, (partialTicks))
                + ",rain=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, partialTicks)
                + ",thunder=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldThunderStrength(world, partialTicks)
                + ",day=" + dayHelper(mc)
                + ",night=" + nightHelper(mc)
                + ",dawnDusk=" + ((1.0F - dayHelper(mc)) - nightHelper(mc))
                + ",horizon=" + horizon
                + ",cloudHeight=" + cloudHeight;
    }

    protected String skyDomeCameraSummary(Minecraft mc) {
        Entity view = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (view == null) {
            return "view=null";
        }
        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        Vec3d look = com.l.ausm.impl.util.MinecraftReflectionCompat.look(view, partialTicks);
        double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(view), partialTicks);
        double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(view), partialTicks);
        double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(view), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(view), partialTicks);
        return "pos=" + x + "/" + y + "/" + z
                + ",eye=" + (y + com.l.ausm.impl.util.MinecraftReflectionCompat.eyeHeight(view))
                + ",yaw=" + com.l.ausm.impl.util.MinecraftReflectionCompat.rotationYaw(view)
                + ",pitch=" + com.l.ausm.impl.util.MinecraftReflectionCompat.rotationPitch(view)
                + ",prevYaw=" + com.l.ausm.impl.util.MinecraftReflectionCompat.prevRotationYaw(view)
                + ",prevPitch=" + com.l.ausm.impl.util.MinecraftReflectionCompat.prevRotationPitch(view)
                + ",look=" + formatVec3d(look)
                + ",verticalDelta=" + cameraVerticalDelta()
                + ",horizontalDelta=" + cameraHorizontalVelocityMagnitude();
    }

    protected static String formatVec3d(Vec3d value) {
        if (value == null) {
            return "null";
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(value) + "/" + com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(value) + "/" + com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(value);
    }

    protected String skyDomeGlStateSummary() {
        FloatBuffer clearColor = BufferUtils.createFloatBuffer(4);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        return skyProbeGlStateSummary()
                + ",program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                + ",viewport=" + viewport.get(0) + "/" + viewport.get(1) + "/" + viewport.get(2) + "/" + viewport.get(3)
                + ",clear=" + clearColor.get(0) + "/" + clearColor.get(1) + "/" + clearColor.get(2) + "/" + clearColor.get(3)
                + ",drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                + ",readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                + ",depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                + ",depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                + ",blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                + ",alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                + ",fog=" + GL11.glIsEnabled(GL11.GL_FOG)
                + ",cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                + ",colorMask=" + (colorMask.get(0) != 0) + "/" + (colorMask.get(1) != 0) + "/" + (colorMask.get(2) != 0) + "/" + (colorMask.get(3) != 0);
    }

    protected String framebufferSamples(Framebuffer framebuffer) {
        if (framebuffer == null || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer) <= 0) {
            return "none";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer));
            GL11.glReadBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            int width = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer);
            int height = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer);
            return "center=" + readFramebufferPixel(width / 2, height / 2)
                    + ";upper=" + readFramebufferPixel(width / 2, Math.max(0, height * 3 / 4))
                    + ";lower=" + readFramebufferPixel(width / 2, Math.max(0, height / 4));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String currentDrawFramebufferColorSamples(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        int drawFramebuffer = currentDrawFramebufferBinding();
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        if (drawFramebuffer == 0 && drawBuffer == GL11.GL_NONE) {
            drawBuffer = GL11.GL_BACK;
        }
        return framebufferIdColorSamples(drawFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), drawBuffer);
    }

    protected String framebufferIdColorSamples(int framebuffer, int width, int height, int readBuffer) {
        if (width <= 0 || height <= 0 || readBuffer == GL11.GL_NONE) {
            return "invalid";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            return sampleBoundReadFramebuffer(width, height, false);
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String framebufferDepthSamples(Framebuffer framebuffer) {
        if (framebuffer == null || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer) <= 0) {
            return "none";
        }
        return framebufferIdDepthSamples(
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
    }

    protected String currentFramebufferDepthSamples(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) <= 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        int readFramebuffer = currentReadFramebufferBinding();
        int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        if (readFramebuffer == 0 && readBuffer == GL11.GL_NONE) {
            readBuffer = GL11.GL_BACK;
        }
        return framebufferIdDepthSamples(readFramebuffer, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), readBuffer);
    }

    protected String deferredFramebufferColorSamples(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            int texture = framebuffer.getReadTexture(attachment);
            if (texture <= 0) {
                return "no-texture";
            }
            int probeFbo = GL30.glGenFramebuffers();
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, probeFbo);
                GL30.glFramebufferTexture2D(
                        GL30.GL_READ_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0,
                        GL11.GL_TEXTURE_2D,
                        texture,
                        0
                );
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
                return sampleBoundReadFramebuffer(
                        Math.max(1, framebuffer.getAttachmentWidth(attachment)),
                        Math.max(1, framebuffer.getAttachmentHeight(attachment)),
                        false);
            } finally {
                GL30.glDeleteFramebuffers(probeFbo);
            }
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(previousTexture);
        }
    }

    protected String deferredFramebufferRecoveryColorSamples(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        if (!framebuffer.hasRecoveryColorSnapshot()) {
            return "no-snapshot";
        }
        int width = Math.max(1, framebuffer.getRecoveryColorWidth());
        int height = Math.max(1, framebuffer.getRecoveryColorHeight());
        int x = Math.max(0, Math.min(width - 1, width / 2));
        int bottomSkyY = Math.max(0, Math.min(height - 1, height / 16));
        int lowerY = Math.max(0, Math.min(height - 1, height * 3 / 16));
        int centerY = Math.max(0, Math.min(height - 1, height / 2));
        int upperY = Math.max(0, Math.min(height - 1, height * 13 / 16));
        int topDomeY = Math.max(0, Math.min(height - 1, height * 15 / 16));
        return "bottomSky=" + recoveryColorPixelSummary(framebuffer, x, bottomSkyY)
                + ";lower=" + recoveryColorPixelSummary(framebuffer, x, lowerY)
                + ";center=" + recoveryColorPixelSummary(framebuffer, x, centerY)
                + ";upper=" + recoveryColorPixelSummary(framebuffer, x, upperY)
                + ";topDome=" + recoveryColorPixelSummary(framebuffer, x, topDomeY);
    }

    protected String recoveryColorPixelSummary(DeferredFramebuffer framebuffer, int x, int y) {
        try {
            float[] color = framebuffer.readRecoveryColorAt(x, y);
            if (!isFiniteColor(color)) {
                return x + "," + y + "=rgba(nan,nan,nan,nan)";
            }
            return x + "," + y + "=rgba("
                    + recoveryColorByte(color[0]) + ','
                    + recoveryColorByte(color[1]) + ','
                    + recoveryColorByte(color[2]) + ','
                    + recoveryColorByte(color[3]) + ')';
        } catch (RuntimeException | LinkageError e) {
            return x + "," + y + "=error=" + e.getClass().getSimpleName();
        }
    }

    protected static int recoveryColorByte(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(255, Math.round(value * 255.0f)));
    }

    protected String deferredFramebufferAttachmentSamples(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (Attachment attachment : Attachment.values()) {
            if (summary.length() > 0) {
                summary.append('|');
            }
            summary.append(attachment.name())
                    .append('=')
                    .append(deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return summary.toString();
    }

    protected String shaderedVoidSkyProgramSummary() {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        return "compositeFixed=" + fullscreenProgramsSummary(ProgramArrayId.COMPOSITE)
                + ", compositeIndexed=" + fullscreenArrayProgramsSummary(ProgramArrayId.COMPOSITE)
                + ", final=" + describePipelineProgram(finalProgram)
                + ", finalDrawBuffers=" + (finalProgram != null ? finalProgram.drawBuffers() : "none")
                + ", finalComputes=" + finalComputePrograms.size();
    }

    protected String fullscreenProgramsSummary(ProgramArrayId arrayId) {
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        if (array == null || array.fixedPasses().isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (RenderPass pass : array.fixedPasses()) {
            PipelineProgram program = programs.get(pass);
            if (program == null || !program.hasOwnProgram()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(pass).append(program.drawBuffers());
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }

    protected String fullscreenArrayProgramsSummary(ProgramArrayId arrayId) {
        List<FullscreenArrayProgram> arrayPrograms = fullscreenArrayPrograms.getOrDefault(arrayId, List.of());
        if (arrayPrograms.isEmpty()) {
            return "none";
        }
        StringBuilder summary = new StringBuilder();
        for (FullscreenArrayProgram program : arrayPrograms) {
            if (program == null || !program.hasProgram()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(',');
            }
            summary.append(program.name()).append(program.drawBuffers());
        }
        return summary.length() == 0 ? "none" : summary.toString();
    }

    protected String framebufferIdDepthSamples(int framebuffer, int width, int height, int readBuffer) {
        if (width <= 0 || height <= 0) {
            return "invalid-size";
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            if (readBuffer != GL11.GL_NONE) {
                GL11.glReadBuffer(readBuffer);
            }
            return sampleBoundReadFramebuffer(width, height, true);
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected String sampleBoundReadFramebuffer(int width, int height, boolean includeDepth) {
        int x = Math.max(0, width / 2);
        int bottomSkyY = Math.max(0, Math.min(height - 1, height / 16));
        int lowerY = Math.max(0, Math.min(height - 1, height * 3 / 16));
        int centerY = Math.max(0, Math.min(height - 1, height / 2));
        int upperY = Math.max(0, Math.min(height - 1, height * 13 / 16));
        int topDomeY = Math.max(0, Math.min(height - 1, height * 15 / 16));
        return "bottomSky=" + readFramebufferPixelSummary(x, bottomSkyY, includeDepth)
                + ";lower=" + readFramebufferPixelSummary(x, lowerY, includeDepth)
                + ";center=" + readFramebufferPixelSummary(x, centerY, includeDepth)
                + ";upper=" + readFramebufferPixelSummary(x, upperY, includeDepth)
                + ";topDome=" + readFramebufferPixelSummary(x, topDomeY, includeDepth);
    }

    protected String readFramebufferPixel(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return (pixel.get(0) & 0xFF) + "/" + (pixel.get(1) & 0xFF) + "/" + (pixel.get(2) & 0xFF) + "/" + (pixel.get(3) & 0xFF);
    }

    protected int currentReadFramebufferBinding() {
        return GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    protected boolean isIgnoredShaderlessSkyProbeScreen(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null) {
            return false;
        }
        String screenClass = com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName();
        return "net.minecraft.client.gui.GuiChat".equals(screenClass);
    }

    protected int currentDrawFramebufferBinding() {
        return GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    protected void restoreReadBufferForFramebuffer(int framebuffer, int readBuffer) {
        GL11.glReadBuffer(safeBufferForFramebuffer(framebuffer, readBuffer));
    }

    protected void restoreDrawBufferForFramebuffer(int framebuffer, int drawBuffer) {
        GL11.glDrawBuffer(safeBufferForFramebuffer(framebuffer, drawBuffer));
    }

    protected int safeBufferForFramebuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE) {
            return GL11.GL_NONE;
        }
        boolean attachmentBuffer = buffer >= GL30.GL_COLOR_ATTACHMENT0 && buffer < GL30.GL_COLOR_ATTACHMENT0 + maxDrawBuffers();
        return framebuffer == 0
                ? attachmentBuffer ? GL11.GL_BACK : buffer
                : attachmentBuffer ? buffer : GL30.GL_COLOR_ATTACHMENT0;
    }

    protected void bindMinecraftFramebufferForGui(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }
        Framebuffer framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int framebufferObject = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
        restoreDrawBufferForFramebuffer(framebufferObject, GL30.GL_COLOR_ATTACHMENT0);
        restoreReadBufferForFramebuffer(framebufferObject, GL30.GL_COLOR_ATTACHMENT0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(
                0,
                0,
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc),
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
    }

    protected int boundTexture2D(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(textureUnit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(previousActiveTexture);
        }
    }

    protected boolean texture2DEnabled(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(previousActiveTexture);
        }
    }

    protected boolean textureCoordArrayEnabled(int textureUnit) {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
        }
    }

    protected String fogColorSummary() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    protected String currentColorSummary() {
        FloatBuffer color = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    protected String colorMaskSummary() {
        ByteBuffer colorMask = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        return (colorMask.get(0) != 0)
                + "/"
                + (colorMask.get(1) != 0)
                + "/"
                + (colorMask.get(2) != 0)
                + "/"
                + (colorMask.get(3) != 0);
    }

    protected String viewportSummary() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        return viewportBuffer.get(0)
                + ","
                + viewportBuffer.get(1)
                + ","
                + viewportBuffer.get(2)
                + "x"
                + viewportBuffer.get(3);
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state) {
        return isAstralCrystalCluster(state);
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return shouldUseCrystalOnlyEmission(actualLightState(state, blockAccess, pos));
    }

    protected int blockRenderEmissionForState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int explicit = explicitShaderedBlockEmission(state, blockAccess, pos);
        if (explicit > 0) {
            return explicit;
        }
        return intrinsicBlockEmission(state);
    }

    protected int explicitShaderedBlockEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        int astralEmission = astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        return 0;
    }

    protected int inheritedBlockRenderEmission(IBlockState state) {
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        int astralEmission = astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        try {
            return clampLightValue(com.l.ausm.impl.util.MinecraftReflectionCompat.stateLightValue(state));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public int blockIntrinsicEmission(IBlockState state) {
        return state != null ? inheritedBlockRenderEmission(state) : 0;
    }

    protected int blockcrafteryLightEmission(IBlockState state) {
        return 0;
    }

    protected IBlockState[] inheritedRenderStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return new IBlockState[0];
        }
        GpomFramedMaterialCompat.Material material = GpomFramedMaterialCompat.material(blockAccess, pos);
        if (!material.present() || material.primary() == null) {
            return new IBlockState[0];
        }
        if (material.secondary() == null || material.secondary() == material.primary()) {
            return new IBlockState[] {material.primary()};
        }
        return new IBlockState[] {material.primary(), material.secondary()};
    }

    protected int gpomFramedMaterialEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return isBlockcrafteryEditableBlock(state)
                ? GpomFramedMaterialCompat.material(blockAccess, pos).emission()
                : 0;
    }

    protected boolean gpomFramedMaterialHasBloom(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        GpomFramedMaterialCompat.Material material = GpomFramedMaterialCompat.material(blockAccess, pos);
        if (!material.bloom() && material.emission() <= 0) {
            return false;
        }
        int probe = blockcrafteryBloomDecisionProbeCount.incrementAndGet();
        if (probe <= 64) {
            MainMod.LOGGER.info("[AUSMBlockcrafteryBloomProbe] call={} thread={} pos={} state={} access={} present={} emission={} bloom={} primary={} secondary={} layer={} bloomLayer={}",
                    probe, Thread.currentThread().getName(), pos, state,
                    blockAccess != null ? blockAccess.getClass().getName() : "null",
                    material.present(), material.emission(), material.bloom(),
                    diagnosticStateName(material.primary()), diagnosticStateName(material.secondary()),
                    MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer());
        }
        return material.bloom();
    }

    public boolean gpomFramedMaterialHasBloom(IBlockAccess blockAccess, BlockPos pos) {
        return GpomFramedMaterialCompat.material(blockAccess, pos).bloom();
    }

    public int gpomFramedMaterialEmission(IBlockAccess blockAccess, BlockPos pos) {
        return GpomFramedMaterialCompat.material(blockAccess, pos).emission();
    }

    public BlockRenderLayer gpomFramedMaterialBaseLayer(IBlockAccess blockAccess, BlockPos pos) {
        IBlockState primary = GpomFramedMaterialCompat.material(blockAccess, pos).primary();
        BlockRenderLayer layer = safeRenderLayer(primary);
        return layer != null && !AusmBloomLayer.isBloomLayer(layer) ? layer : BlockRenderLayer.SOLID;
    }

    public boolean shouldForceCeleritasGeometryBloomFullbright(IBlockState state, IBlockAccess blockAccess,
                                                                BlockPos pos, BlockRenderLayer layer) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return shouldForceCeleritasGeometryBloomFullbright(state, layer);
        }
        GpomFramedMaterialCompat.Material material = GpomFramedMaterialCompat.material(blockAccess, pos);
        if (material.present() && (material.bloom() || material.emission() > 0)) {
            return AusmBloomLayer.isBloomLayer(layer)
                    || (!AusmBloomLayer.isBloomLayer(layer) && material.emission() > 0);
        }
        return shouldForceCeleritasGeometryBloomFullbright(state, layer);
    }

    protected boolean isBloomOrEmissiveInheritedState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        return blockShaderlessBloomEmission(state, blockAccess, pos) > 0;
    }

    protected boolean stateHasBloomResourceGeometry(IBlockState state) {
        return bloomResourceClassifier.hasBloomResourceGeometry(state);
    }

    protected boolean isExplicitBloomState(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(java.util.Locale.ROOT) : "";
        String blockClass = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state).getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return namespace.contains("lumenized")
                || path.contains("lumenized")
                || blockClass.contains("lumenized");
    }

    protected boolean isLumenizedBloomState(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(java.util.Locale.ROOT) : "";
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        String blockClass = block != null
                ? block.getClass().getName().toLowerCase(java.util.Locale.ROOT)
                : "";
        return namespace.contains("lumenized") || path.contains("lumenized") || blockClass.contains("lumenized");
    }

    protected int nextFramedDiagnosticCount(IBlockState state, boolean priority) {
        if (priority) {
            int count = framedPriorityDiagnosticCount.incrementAndGet();
            return count <= MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS ? count : -1;
        }
        if (isArchitectureCraftShapeBlock(state)) {
            int count = architectureCraftDiagnosticCount.incrementAndGet();
            return count <= MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS ? count : -1;
        }
        int count = blockcrafteryDiagnosticCount.incrementAndGet();
        return count <= MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS ? count : -1;
    }

    protected String framedDiagnosticKind(IBlockState state) {
        if (isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        return "unknown";
    }

    protected boolean isPriorityFramedDiagnosticState(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                   BlockRenderLayer bloomLayer) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (blockRenderEmissionForState(state, blockAccess, pos) > 0 || blockEntityId(state, blockAccess, pos) != 0) {
            return true;
        }
        if (bloomLayer != null && canRenderInLayer(state, bloomLayer)) {
            return true;
        }
        if (stateHasBloomLayerGeometry(state)) {
            return true;
        }
        return isPriorityFramedDiagnosticName(state);
    }

    protected boolean isPriorityFramedDiagnosticName(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = registryName(state);
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = name != null && com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(java.util.Locale.ROOT) : "";
        return namespace.contains("lumenized") || path.contains("lumenized");
    }

    protected String framedDiagnosticInheritedStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                   BlockRenderLayer currentLayer, BlockRenderLayer bloomLayer) {
        IBlockState[] inheritedStates = inheritedRenderStates(state, blockAccess, pos);
        if (inheritedStates.length == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < inheritedStates.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(framedDiagnosticState("inherited" + i, inheritedStates[i], blockAccess, pos,
                    currentLayer, bloomLayer));
        }
        return builder.append(']').toString();
    }

    protected String framedDiagnosticState(String label, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                         BlockRenderLayer currentLayer, BlockRenderLayer bloomLayer) {
        if (state == null) {
            return label + "{state=null}";
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        return label + "{"
                + "name=" + stateName(state)
                + ", state=" + state
                + ", class=" + (block != null ? block.getClass().getName() : "null")
                + ", renderType=" + safeRenderType(state)
                + ", naturalLayer=" + safeRenderLayer(state)
                + ", canCurrent=" + canRenderInLayer(state, currentLayer)
                + ", canSolid=" + canRenderInLayer(state, BlockRenderLayer.SOLID)
                + ", canCutoutMipped=" + canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)
                + ", canCutout=" + canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                + ", canTranslucent=" + canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                + ", canBloom=" + (bloomLayer != null && canRenderInLayer(state, bloomLayer))
                + ", emission=" + blockRenderEmissionForState(state, blockAccess, pos)
                + ", lightAccess=" + safeLightValue(state, blockAccess, pos)
                + ", lightRaw=" + safeLightValue(state, null, null)
                + ", blockId=" + blockEntityId(state, blockAccess, pos)
                + ", metadata=" + blockMetadata(state)
                + ", opaque=" + safeOpaqueCube(state)
                + ", full=" + safeFullCube(state)
                + ", material=" + (com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterial(state) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterial(state) : "null")
                + "}";
    }

    protected static EnumBlockRenderType safeRenderType(IBlockState state) {
        return PipelineBlockRenderProperties.renderType(state);
    }

    protected static BlockRenderLayer safeRenderLayer(IBlockState state) {
        return PipelineBlockRenderProperties.renderLayer(state);
    }

    protected static int safeLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return PipelineBlockRenderProperties.lightValue(state, blockAccess, pos);
    }

    protected static boolean safeOpaqueCube(IBlockState state) {
        return PipelineBlockRenderProperties.opaqueCube(state);
    }

    protected static boolean safeFullCube(IBlockState state) {
        return PipelineBlockRenderProperties.fullCube(state);
    }

    protected static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return PipelineBlockRenderProperties.canRenderInLayer(state, layer);
    }

    protected static int blockMetadata(IBlockState state) {
        return PipelineBlockRenderProperties.metadata(state);
    }

    protected static int intrinsicBlockEmission(IBlockState state) {
        return PipelineBlockEmission.intrinsicEmission(state);
    }

    protected static int astralCrystalEmission(IBlockState state) {
        return PipelineBlockEmission.astralCrystalEmission(state);
    }

    protected static boolean isAstralCrystalCluster(IBlockState state) {
        return PipelineBlockEmission.isAstralCrystalCluster(state);
    }

    protected static int astralCrystalVoxelId(IBlockState state) {
        return localActVoxelId(PipelineBlockEmission.astralCrystalMaterialId(state));
    }

    protected static boolean containsIgnoreCase(String value, String needle) {
        return PipelineBlockEmission.containsIgnoreCase(value, needle);
    }

    protected static int clampLightValue(int value) {
        return Math.max(0, Math.min(15, value));
    }

    public void recordSyntheticLightCandidate(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (isBetterPortalsExternalWorldTarget()) {
            return;
        }
        if (!canTrackSyntheticLights() || state == null || blockAccess == null) {
            syntheticLightCandidates.remove(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToLong(pos));
            return;
        }
        SyntheticLightInfo lightInfo = syntheticLightInfo(state, blockAccess, pos);
        if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
            if (recordProjectRedSyntheticLightCandidate(blockAccess, pos, "block_render_te")) {
                return;
            }
            auditSyntheticLight("block_render", pos, lightInfo, "skip:" + lightInfo.reason);
            return;
        }
        putSyntheticLightCandidate(pos, false);
        auditSyntheticLight("block_render", pos, lightInfo, "recorded");
    }

    public void refreshSyntheticLightCandidate(BlockPos pos) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        refreshSyntheticLightCandidate(mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null, pos);
    }

    public void refreshSyntheticLightCandidate(World world, BlockPos pos) {
        if (pos == null) {
            return;
        }
        long key = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToLong(pos);
        syntheticLightCandidates.remove(key);
        if (!canTrackSyntheticLights() || world == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
            return;
        }
        IBlockState state;
        try {
            state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos);
        } catch (RuntimeException ignored) {
            return;
        }
        SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
        if (lightInfo.voxelId > 0 && lightInfo.emission > 0) {
            putSyntheticLightCandidate(pos, true);
            auditSyntheticLight("world_update", pos, lightInfo, "recorded");
        } else {
            auditSyntheticLight("world_update", pos, lightInfo, "skip:" + lightInfo.reason);
        }
        if (shouldProbeColoredLightTileEntity(state, lightInfo)) {
            auditProjectRedTileEntity(world, pos, "world_update_te");
        }
    }

    public void refreshSyntheticLightCandidates(World world, BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return;
        }
        int minX = Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(to)) - 1;
        int minY = Math.max(0, Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(to)) - 1);
        int minZ = Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(to)) - 1;
        int maxX = Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(to)) + 1;
        int maxY = Math.min(255, Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(to)) + 1);
        int maxZ = Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(from), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(to)) + 1;
        long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        if (volume > MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME) {
            removeSyntheticLightCandidatesInRange(minX, minY, minZ, maxX, maxY, maxZ);
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    refreshSyntheticLightCandidate(world, new BlockPos(x, y, z));
                }
            }
        }
    }

    public void removeSyntheticLightCandidate(BlockPos pos) {
        if (pos != null) {
            syntheticLightCandidates.remove(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToLong(pos));
        }
    }

    protected boolean canTrackSyntheticLights() {
        return ENABLE_CPU_LIGHT_INJECTION
                && ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION
                && isPipelineActive
                && shaderImages.active()
                && !shaderProperties.blockIds().isEmpty();
    }

    protected int syntheticLightVoxelId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return syntheticLightInfo(state, blockAccess, pos).voxelId;
    }

    protected SyntheticLightInfo syntheticLightInfo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "missing_input");
        }
        if (shaderProperties.blockIds().isEmpty()) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "no_block_ids");
        }
        IBlockState actualState = actualLightState(state, blockAccess, pos);
        int shaderBlockId = shaderProperties.blockIds().idFor(actualState);
        int voxelId = localActVoxelId(shaderBlockId);
        if (voxelId <= 0) {
            voxelId = compatSyntheticLightVoxelId(actualState);
        }
        int emission = blockRenderEmissionForState(actualState, null, null);
        if (voxelId <= 0) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, 0, emission, "no_colored_voxel_mapping");
        }
        if (emission <= 0) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "not_emissive");
        }
        return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "ok");
    }

    protected void putSyntheticLightCandidate(BlockPos pos, boolean force) {
        long key = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToLong(pos);
        if (syntheticLightCandidates.size() >= MAX_SYNTHETIC_LIGHT_CANDIDATES
                && !syntheticLightCandidates.containsKey(key)) {
            if (!force) {
                return;
            }
            for (Long staleKey : syntheticLightCandidates.keySet()) {
                syntheticLightCandidates.remove(staleKey);
                break;
            }
        }
        syntheticLightCandidates.put(key, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToImmutable(pos));
    }

    protected void removeSyntheticLightCandidatesInRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (Map.Entry<Long, BlockPos> entry : syntheticLightCandidates.entrySet()) {
            BlockPos pos = entry.getValue();
            if (pos == null) {
                syntheticLightCandidates.remove(entry.getKey());
                continue;
            }
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) >= minX && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) <= maxX
                    && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) >= minY && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) <= maxY
                    && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) >= minZ && com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) <= maxZ) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
            }
        }
    }

    protected void auditSyntheticLight(String source, BlockPos pos, SyntheticLightInfo lightInfo, String result) {
        if (MAX_COLORED_LIGHT_AUDIT_LOGS <= 0) {
            return;
        }
        if (lightInfo == null || !shouldAuditSyntheticLight(lightInfo)) {
            return;
        }
        String key = source + "|" + result + "|" + pos + "|" + stateName(lightInfo.originalState)
                + "|" + stateName(lightInfo.actualState) + "|" + lightInfo.shaderBlockId
                + "|" + lightInfo.voxelId + "|" + lightInfo.emission;
        if (!coloredLightAuditKeys.add(key)) {
            return;
        }
        int count = coloredLightAuditCount.incrementAndGet();
        if (count > MAX_COLORED_LIGHT_AUDIT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[ColoredLightAudit] source={} pos={} state={} actual={} shaderBlockId={} voxel={} emission={} result={} candidates={}",
                source,
                formatBlockPos(pos),
                stateName(lightInfo.originalState),
                stateName(lightInfo.actualState),
                lightInfo.shaderBlockId,
                lightInfo.voxelId,
                lightInfo.emission,
                result,
                syntheticLightCandidates.size()
        );
        if (count == MAX_COLORED_LIGHT_AUDIT_LOGS) {
            MainMod.LOGGER.info("[ColoredLightAudit] Reached log cap {}; suppressing further colored-light audit lines.", MAX_COLORED_LIGHT_AUDIT_LOGS);
        }
    }

    protected void logDecoratedLightEmission(IBlockState originalState, IBlockState decoratedState,
                                           IBlockAccess blockAccess, BlockPos pos, int emission) {
        if (MAX_DECORATED_LIGHT_AUDIT_LOGS <= 0) {
            return;
        }
        String key = safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + formatBlockPos(pos)
                + "|" + stateName(originalState)
                + "|" + stateName(decoratedState)
                + "|" + emission;
        if (!decoratedLightAuditKeys.add(key)) {
            return;
        }

        int count = decoratedLightAuditCount.incrementAndGet();
        if (count > MAX_DECORATED_LIGHT_AUDIT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDecoratedLight] call={} pos={} state={} decorated={} emission={} access={}",
                count,
                formatBlockPos(pos),
                stateName(originalState),
                stateName(decoratedState),
                emission,
                blockAccess != null ? blockAccess.getClass().getName() : "null"
        );
    }

    protected void auditProjectRedLight(TileEntity tileEntity, int[] voxelIds, int count, String result) {
        String diagnosis = ProjectRedIlluminationCompat.diagnose(tileEntity);
        if (diagnosis == null) {
            return;
        }
        auditProjectRedDiagnosis(tileEntity, voxelIds, count, result, diagnosis);
    }

    protected void auditProjectRedDiagnosis(TileEntity tileEntity, int[] voxelIds, int count, String result, String diagnosis) {
        if (MAX_COLORED_LIGHT_AUDIT_LOGS <= 0) {
            return;
        }
        BlockPos pos = tileEntity != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityPos(tileEntity) : null;
        String key = "projectred|" + result + "|" + formatBlockPos(pos) + "|" + diagnosis;
        if (!coloredLightAuditKeys.add(key)) {
            return;
        }
        int logCount = coloredLightAuditCount.incrementAndGet();
        if (logCount > MAX_COLORED_LIGHT_AUDIT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[ColoredLightAudit] source=projectred pos={} count={} voxels={} result={} {}",
                formatBlockPos(pos),
                count,
                formatVoxelIds(voxelIds, count),
                result,
                diagnosis
        );
        if (logCount == MAX_COLORED_LIGHT_AUDIT_LOGS) {
            MainMod.LOGGER.info("[ColoredLightAudit] Reached log cap {}; suppressing further colored-light audit lines.", MAX_COLORED_LIGHT_AUDIT_LOGS);
        }
    }

    protected void auditProjectRedTileEntity(World world, BlockPos pos, String result) {
        if (world == null || pos == null) {
            return;
        }
        TileEntity tileEntity;
        try {
            tileEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.tileentity.TileEntity.class, null, new String[] {"func_175625_s", "getTileEntity"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, (pos));
        } catch (RuntimeException ignored) {
            return;
        }
        if (tileEntity == null) {
            return;
        }
        int[] voxelIds = new int[8];
        int count = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, voxelIds);
        if (count > 0) {
            putSyntheticLightCandidate(pos, true);
        }
        String diagnosis = ProjectRedIlluminationCompat.diagnoseHost(tileEntity);
        if (diagnosis != null) {
            auditProjectRedDiagnosis(tileEntity, voxelIds, count, result, diagnosis);
        }
    }

    protected boolean recordProjectRedSyntheticLightCandidate(IBlockAccess blockAccess, BlockPos pos, String result) {
        TileEntity tileEntity = tileEntityAt(blockAccess, pos);
        if (tileEntity == null) {
            return false;
        }

        int[] voxelIds = new int[8];
        int count = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, voxelIds);
        if (count <= 0) {
            return false;
        }

        putSyntheticLightCandidate(pos, true);
        auditProjectRedLight(tileEntity, voxelIds, count, result);
        return true;
    }

    protected TileEntity tileEntityAt(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess == null || pos == null) {
            return null;
        }
        try {
            return com.l.ausm.impl.util.MinecraftReflectionCompat.blockAccessTileEntity(blockAccess, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    protected void resetColoredLightAudit() {
        coloredLightAuditKeys.clear();
        coloredLightAuditCount.set(0);
    }

    protected boolean shouldAuditSyntheticLight(SyntheticLightInfo lightInfo) {
        return lightInfo.voxelId > 0
                || isKnownColoredLightAuditTarget(lightInfo.originalState)
                || isKnownColoredLightAuditTarget(lightInfo.actualState);
    }

    protected boolean shouldProbeColoredLightTileEntity(IBlockState state, SyntheticLightInfo lightInfo) {
        return isProjectRedTileHost(state)
                || lightInfo != null && isProjectRedTileHost(lightInfo.originalState)
                || lightInfo != null && isProjectRedTileHost(lightInfo.actualState);
    }

    protected boolean isProjectRedTileHost(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && (("projectred-illumination".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name)))
                || ("forgemultipartcbe".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name)) && "multipart_block".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name))));
    }

    protected boolean isKnownColoredLightAuditTarget(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }

        String namespace = com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name);
        String path = com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name);
        if ("projectred-illumination".equals(namespace)
                || ("forgemultipartcbe".equals(namespace) && "multipart_block".equals(path))) {
            return true;
        }
        if ("thaumcraft".equals(namespace)) {
            return path.startsWith("candle_") || path.startsWith("nitor_");
        }
        if ("bewitchment".equals(namespace)) {
            return path.endsWith("_candle");
        }
        if ("tconstruct".equals(namespace)) {
            return "seared_furnace_controller".equals(path);
        }
        if ("astralsorcery".equals(namespace)) {
            return "blockcelestialcrystals".equalsIgnoreCase(path) || "blockgemcrystals".equalsIgnoreCase(path);
        }
        return false;
    }

    protected static ResourceLocation registryName(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        return block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    protected static String stateName(IBlockState state) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.stateString(state);
    }

    protected static String formatBlockPos(BlockPos pos) {
        return pos != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + "," + com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + "," + com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) : "null";
    }

    protected static String formatVoxelIds(int[] voxelIds, int count) {
        if (voxelIds == null || count <= 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(count, voxelIds.length);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(voxelIds[i]);
        }
        return builder.append(']').toString();
    }

    protected IBlockState actualLightState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return state == null || blockAccess == null || pos == null
                ? state
                : com.l.ausm.impl.util.MinecraftReflectionCompat.actualState(state, blockAccess, pos);
    }

    protected static boolean isBlockcrafteryEditableBlock(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null && "blockcraftery".equalsIgnoreCase(
                com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name));
    }

    protected static boolean isArchitectureCraftShapeBlock(IBlockState state) {
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return false;
        }
        ResourceLocation name = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(block);
        return "architecturecraft".equalsIgnoreCase(
                com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && "com.elytradev.architecture.common.block.BlockShape".equals(block.getClass().getName());
    }

    public boolean shouldSeparateBlockAo(IBlockState state) {
        if (!shouldSeparateAo() || state == null) {
            return false;
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        return block != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.blockRenderLayer(block) == BlockRenderLayer.SOLID;
    }

    public boolean shouldSeparateBlockAo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return shouldSeparateBlockAo(actualLightState(state, blockAccess, pos));
    }

    public boolean shouldSeparateAo() {
        return isPipelineActive && shaderProperties.renderSettings().separateAo();
    }

    public boolean shouldSeparateEntityDraws() {
        return isPipelineActive && shaderProperties.renderSettings().separateEntityDraws();
    }

    public float ambientOcclusionLevel() {
        return isPipelineActive ? shaderProperties.renderSettings().ambientOcclusionLevel() : 1.0f;
    }

    public boolean shouldDisableDirectionalShading() {
        return isPipelineActive && !shaderProperties.renderSettings().oldLighting();
    }

    public boolean shouldRenderWeather() {
        if (isPipelineActive && ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            return false;
        }
        return !isPipelineActive || !shouldSkipAllMainGbufferRendering() && shaderProperties.renderSettings().weather();
    }

    public boolean shouldRenderWeatherParticles() {
        if (isPipelineActive && ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            return false;
        }
        return !isPipelineActive || shaderProperties.renderSettings().weatherParticles();
    }

    public boolean shouldRenderVignette() {
        return !isPipelineActive || shaderProperties.renderSettings().vignette();
    }

    public boolean shouldRenderUnderwaterOverlay() {
        if (!isPipelineActive) {
            return true;
        }
        return shaderProperties.renderSettings().underwaterOverlay()
                || eyeFluidState(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) == 1;
    }

    public boolean shouldRenderSkyDisc() {
        return !isPipelineActive || shaderProperties.renderSettings().sky();
    }

    public boolean shouldUseCompleteOwnedSkyOverride() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        return mc != null
                && world != null
                && !isPipelineActive
                && isSimpleVoidWorld(world)
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public void renderCompleteOwnedVoidSkyDetails(float partialTicks, WorldClient world, Minecraft mc) {
        if (mc == null
                || world == null
                || !shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        Object screen = com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        if (screen != null || com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc)) {
            return;
        }
        Object renderer = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProviderSkyRenderer(
                com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world));
        if (renderer == null
                || (!isActualBotaniaVoidWorld(world)
                && !ASTRAL_SKYBOX_CLASS.equals(renderer.getClass().getName()))) {
            return;
        }
        try {
            if (ASTRAL_SKYBOX_CLASS.equals(renderer.getClass().getName())) {
                // Keep Astral's wrapper intact. Its own compatibility mixin
                // routes the delegated Botania renderer and constellation
                // pass, while avoiding the recursive vanilla sky branch for
                // dimensions that require Astral's sky handling.
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                        renderer,
                        new String[] {"render"},
                        new Class<?>[] {float.class, WorldClient.class, Minecraft.class},
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                    renderer,
                    new String[] {"render"},
                    new Class<?>[] {float.class, WorldClient.class, Minecraft.class},
                    partialTicks,
                    world,
                    mc);
        } catch (RuntimeException | LinkageError ignored) {
            // Optional detail path; the owned gradient remains authoritative.
        }
    }

    public void prepareShaderlessHiddenGuiFramebufferPresentation() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc);
        if (mc == null
                || !shouldUseShaderlessOwnedSky(mc)
                || (!hideGui && !paused && screen == null)) {
            return;
        }
        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target != null
                && GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                == com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target)) {
            // Framebuffer.framebufferRenderExt draws the attached texture into
            // the currently bound draw target. Ensure shaderless GUI/F1
            // presentation cannot sample and overwrite its own source FBO.
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void restoreShaderlessHiddenGuiFramebufferTarget() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc);
        if (mc == null
                || !shouldUseShaderlessOwnedSky(mc)
                || (!hideGui && !paused && screen == null)) {
            return;
        }
        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target == null) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0
                ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
    }

    public boolean shouldUseShaderlessHiddenGuiPresentation() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc);
        return mc != null
                && shouldUseShaderlessOwnedSky(mc)
                && (hideGui || paused || screen != null);
    }

    public Object detachNonVanillaSkyRendererForVanillaSky() {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = minecraft == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft);
        if (world == null || isRenderingBetterPortalsNestedView() || isRenderingBetterPortalsRenderPass()) {
            return null;
        }
        net.minecraft.world.WorldProvider provider = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world);
        Object renderer = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProviderSkyRenderer(provider);
        if (renderer == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.setWorldProviderSkyRenderer(provider, null)) {
            return null;
        }
        return renderer;
    }

    public void restoreNonVanillaSkyRenderer(Object renderer) {
        if (renderer == null) {
            return;
        }
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = minecraft == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft);
        if (world == null) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.setWorldProviderSkyRenderer(
                com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world), renderer);
    }

    public boolean shouldSuppressVanillaUpperSkyGeometry() {
        return shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressVanillaSunGeometry() {
        return shouldSuppressShaderedVoidCelestialGeometry();
    }

    public boolean shouldSuppressVanillaMoonGeometry() {
        return shouldSuppressShaderedVoidCelestialGeometry();
    }

    protected boolean shouldSuppressShaderedVoidCelestialGeometry() {
        return shouldSuppressShaderedSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressVanillaStarsGeometry() {
        return isPipelineActive && !shaderProperties.renderSettings().stars();
    }

    public boolean shouldSuppressVanillaLowerSkyGeometry() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        boolean result = world != null
                && ((isCustomVoidWorldSkyEnabled(world)
                    || (isSimpleVoidWorld(world) && shouldUseShaderlessOwnedSky(mc)))
                || shouldUseShaderedF1LowerSkyRepair(mc, world))
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
        logSkySuppressionDecision("vanilla-lower", mc, world, result);
        return result;
    }

    protected boolean areShaderpacksEnabled() {
        return MainMod.getShaderPackManager() != null
                && MainMod.getShaderPackManager().areShadersEnabled();
    }

    protected boolean shouldUseShaderlessOwnedSky(Minecraft mc) {
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        // External sky renderers are detached for the compatibility route. Keep
        // both dome hemispheres under one AUSM backing so F1 and GUI renders do
        // not inherit the Void World's dark vanilla lower hemisphere.
        return !isPipelineActive
                && world != null
                && isSimpleVoidWorld(world)
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public boolean shouldSuppressShaderlessOwnedSkyBaseGeometry() {
        return shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressBotaniaVoidSkyBaseGeometry() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        return world != null
                && isSimpleVoidWorld(world)
                && (shouldUseShaderlessOwnedSky(mc)
                    || isPipelineActive && isCustomVoidWorldSkyEnabled(world))
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public boolean shouldSuppressVanillaSunsetGeometry() {
        return shouldUseShaderlessOwnedSky(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
    }

    public boolean shouldSuppressVoidWorldCustomSkyRenderer(Object skyRenderer, WorldClient world) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return skyRenderer != null
                && world != null
                && shouldUseShaderlessOwnedSky(mc)
                && shouldUseOwnedSkyOverrideWorld(world)
                && isSimpleVoidWorld(world)
                && !isAstralSkyRenderer(skyRenderer);
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
                || !isSimpleVoidWorld(world)
                || !isAstralSkyRenderer(skyRenderer)) {
            return false;
        }
        Object delegated = com.l.ausm.impl.util.MinecraftReflectionCompat.field(
                skyRenderer, Object.class, null, "otherSkyRenderer");
        if (delegated == null
                || !"vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(delegated.getClass().getName())) {
            return false;
        }
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                    delegated,
                    new String[] {"render"},
                    new Class<?>[] {float.class, WorldClient.class, Minecraft.class},
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
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        WorldClient world = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        if (mc == null || world == null || !shouldUseShaderlessOwnedSky(mc)) {
            return;
        }

        // Optional Botania/Astral decoration is disabled here for the same
        // reason as the shaderless compatibility path above: it is not
        // contained by the world depth buffer in the current presentation.
    }

    protected boolean isShaderlessSkyDecorationSuppressedForGui(Minecraft mc) {
        return mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null;
    }

    protected boolean isActualBotaniaVoidWorld(World world) {
        if (!isSimpleVoidWorld(world)) {
            return false;
        }
        Object renderer = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProviderSkyRenderer(
                com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world));
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
        Object delegated = com.l.ausm.impl.util.MinecraftReflectionCompat.field(
                renderer, Object.class, null, "otherSkyRenderer");
        return delegated != null
                && "vazkii.botania.client.render.world.SkyblockSkyRenderer".equals(delegated.getClass().getName());
    }

    public boolean shouldSuppressShaderedAstralLowerSky() {
        return shouldSuppressShaderedSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressShaderedAstralStars() {
        return isPipelineActive
                && !optionBoolean(shaderProperties, ASTRAL_NATIVE_STARS_OPTION, true);
    }

    public boolean shouldSuppressShaderedAstralConstellations() {
        return isPipelineActive
                && !optionBoolean(shaderProperties, ASTRAL_NATIVE_CONSTELLATIONS_OPTION, true);
    }

    public boolean shouldSuppressShaderedAstralStarsAndConstellations() {
        return shouldSuppressShaderedAstralStars() || shouldSuppressShaderedAstralConstellations();
    }

    public boolean shouldSuppressAstralUpperSkyGeometry() {
        return shouldSuppressShaderedAstralLowerSky() || shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    public boolean shouldSuppressAstralLowerSkyGeometry() {
        return shouldSuppressShaderedAstralLowerSky() || shouldSuppressShaderlessSimpleVoidSkyBaseGeometry();
    }

    protected boolean shouldSuppressShaderlessSimpleVoidSkyBaseGeometry() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        return shouldUseShaderlessOwnedSky(mc) && isSimpleVoidWorld(world);
    }

    protected boolean shouldSuppressShaderedSimpleVoidSkyBaseGeometry() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        return isPipelineActive
                && world != null
                && isSimpleVoidWorld(world)
                && isCustomVoidWorldSkyEnabled(world)
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    protected void logSkySuppressionDecision(String route, Minecraft mc, World world, boolean result) {
        if (mc == null
                || !com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc))
                || ownedSkyBackingDecisionProbeLogs++ >= 36) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMSkySuppressionProbe] route={} result={} active={} world={} dim={} simpleVoid={} customVoid={} owned={} shaderlessOwned={} bpNested={} bpPass={} screen={} hideGui={} paused={}",
                route,
                result,
                isPipelineActive,
                world == null ? "null" : world.getClass().getName(),
                world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) == null
                        ? Integer.MIN_VALUE
                        : com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(
                        com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)),
                isSimpleVoidWorld(world),
                isCustomVoidWorldSkyEnabled(world),
                shouldUseOwnedSkyOverrideWorld(world),
                shouldUseShaderlessOwnedSky(mc),
                isRenderingBetterPortalsNestedView(),
                isRenderingBetterPortalsRenderPass(),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc)
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
        double time = (com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0D;
        double dayFactor = (Math.cos((time - 0.25D) * Math.PI * 2.0D) + 1.0D) * 0.5D;
        dayFactor = Math.max(0.0D, Math.min(1.0D, dayFactor));
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
                com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(value),
                com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(value),
                com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(value));
    }

    protected void logAstralVoidSkyProbe(String stage, WorldClient world, double originalHorizon, double adjustedHorizon, float partialTicks) {
        // Probe disabled.
}

    public boolean shouldSanitizeShaderlessNothiriumFog() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return !isPipelineActive
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public void beginShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        shaderlessNothiriumFogGuard.begin(shouldDisableShaderlessNothiriumTerrainFog());
    }

    public void endShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        shaderlessNothiriumFogGuard.end();
    }

    public void logNothiriumRenderProbe(String renderer, String stage, Object pass) {
        if (nothiriumRenderProbeLogs >= MAX_NOTHIRIUM_RENDER_PROBE_LOGS) {
            return;
        }
        nothiriumRenderProbeLogs++;
        MainMod.LOGGER.info("[AUSMNothiriumRender] renderer={} stage={} pass={} active={} bpPass={} gl={}",
                renderer,
                stage,
                String.valueOf(pass),
                isPipelineActive,
                BetterPortalsCompat.isRenderingRenderPass(),
                glStateSummary());
    }

    public void logNothiriumFogProbe(String stage, boolean enabled, int mode, float start, float end, float density,
                                     float[] original, float[] adjusted) {
        if (nothiriumFogProbeLogs >= MAX_NOTHIRIUM_FOG_PROBE_LOGS) {
            return;
        }
        nothiriumFogProbeLogs++;
        MainMod.LOGGER.info("[AUSMNothiriumFog] stage={} enabled={} mode={} start={} end={} density={} original={} adjusted={}",
                stage,
                enabled,
                mode,
                formatProbeFloat(start),
                formatProbeFloat(end),
                formatProbeFloat(density),
                formatNothiriumProbeColor(original),
                formatNothiriumProbeColor(adjusted));
    }

    private static String formatNothiriumProbeColor(float[] color) {
        if (color == null || color.length < 4) {
            return "(nan,nan,nan,nan)";
        }
        return "("
                + formatProbeFloat(color[0]) + ','
                + formatProbeFloat(color[1]) + ','
                + formatProbeFloat(color[2]) + ','
                + formatProbeFloat(color[3]) + ')';
    }

    protected boolean shouldDisableShaderlessNothiriumTerrainFog() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return !isPipelineActive
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && isOverworldShaderEnvironment(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc))
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public int repairShaderlessVoidWorldPackedLight(IBlockAccess blockAccess, BlockPos pos, int packedLight) {
        if (!shouldRepairShaderlessVoidWorldSkyLight(pos)) {
            return packedLight;
        }
        int skyLight = packedLight >> 20 & 15;
        if (skyLight >= 15) {
            return packedLight;
        }
        int repaired = packedLight | 0x00F00000;
        logShaderlessVoidLightRepair("packed", blockAccess, pos, packedLight, repaired, 0);
        return repaired;
    }

    public int repairShaderlessVoidWorldCombinedLight(BlockPos pos, int lightValue, int packedLight) {
        if (!shouldRepairShaderlessVoidWorldSkyLight(pos)) {
            return packedLight;
        }
        int skyLight = packedLight >> 20 & 15;
        int blockLight = packedLight >> 4 & 15;
        int requestedBlockLight = clampInt(lightValue, 0, 15);
        if (skyLight >= 15 && blockLight >= requestedBlockLight) {
            return packedLight;
        }
        int repaired = packedLight | 0x00F00000;
        if (requestedBlockLight > blockLight) {
            repaired = (repaired & ~0xF0) | (requestedBlockLight << 4);
        }
        logShaderlessVoidLightRepair("combined", null, pos, packedLight, repaired, lightValue);
        return repaired;
    }

    protected boolean shouldRepairShaderlessVoidWorldSkyLight(BlockPos pos) {
        if (isPipelineActive
                || pos == null
                || !shaderlessVoidWorldSkyLightEligible
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return false;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        if (world == null) {
            return false;
        }
        try {
            BlockPos skyProbePos = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosUp(pos);
            return com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, skyProbePos)
                    && com.l.ausm.impl.util.MinecraftReflectionCompat.worldCanSeeSky(world, skyProbePos);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    protected void refreshShaderlessVoidWorldSkyLightEligibility() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        shaderlessVoidWorldSkyLightEligible = !isPipelineActive
                && world != null
                && isOverworldShaderEnvironment(world);
    }

    protected void logShaderlessVoidLightRepair(String source, IBlockAccess blockAccess, BlockPos pos, int before, int after, int lightValue) {
        // Probe disabled.
    }

    public void probeShaderlessVoidSkyFramebufferPixels(String stage) {
        // Probe disabled.
    }

    public void probeWorldPassSkyDome(String stage) {
        logWorldPassSkyDomeProbe(stage);
    }

    public void probeShaderlessSolidTerrainSky(String stage) {
        // Probe disabled.
    }

    public void captureShaderlessWorldFramebufferForUi() {
        if (isPipelineActive || !shaderlessWorldPassActive || isRenderingBetterPortalsNestedView() || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            shaderlessWorldFramebufferForUi = 0;
            shaderlessWorldFramebufferFrame = Long.MIN_VALUE;
            return;
        }

        int drawFramebuffer = currentDrawFramebufferBinding();
        if (drawFramebuffer <= 0) {
            return;
        }

        shaderlessWorldFramebufferForUi = drawFramebuffer;
        shaderlessWorldFramebufferWidth = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc));
        shaderlessWorldFramebufferHeight = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
        shaderlessWorldFramebufferFrame = clientRenderFrameNanos;
        logShaderlessWorldFramebufferHandoff(
                "capture",
                "drawFramebuffer=" + drawFramebuffer
                        + ", mcFramebuffer=" + describeFramebufferTarget(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)));
    }

    public void syncShaderlessWorldFramebufferBeforeGui() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (isPipelineActive
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || shaderlessWorldFramebufferForUi <= 0
                || shaderlessWorldFramebufferFrame != clientRenderFrameNanos) {
            return;
        }

        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == shaderlessWorldFramebufferForUi) {
            logShaderlessWorldFramebufferHandoff(
                    "sync-skip-same-target",
                    "target=" + describeFramebufferTarget(target));
            return;
        }

        logShaderlessWorldFramebufferHandoff(
                "sync-before-blit",
                "source=" + shaderlessWorldFramebufferForUi
                        + ", target=" + describeFramebufferTarget(target));
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, shaderlessWorldFramebufferForUi);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    shaderlessWorldFramebufferWidth,
                    shaderlessWorldFramebufferHeight,
                    0,
                    0,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target),
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            logShaderlessWorldFramebufferHandoff(
                    "sync-after-blit-bound",
                    "source=" + shaderlessWorldFramebufferForUi
                            + ", target=" + describeFramebufferTarget(target));
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
        }
        logShaderlessWorldFramebufferHandoff(
                "sync-after-blit-restored",
                "source=" + shaderlessWorldFramebufferForUi
                        + ", target=" + describeFramebufferTarget(target));
    }

    protected void logShaderlessWorldFramebufferHandoff(String stage, String detail) {
        // Probe disabled.
    }

    protected String sampleFramebufferForHandoff(int framebuffer, int width, int height) {
        if (framebuffer <= 0 || width <= 0 || height <= 0) {
            return "invalid";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return sampleBoundReadFramebuffer(width, height, true);
        } catch (RuntimeException | LinkageError ignored) {
            return "unreadable";
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    public void repairShaderlessVoidSkyBeforeGui(float partialTicks) {
        // Old sky repair/probe path intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    protected void renderShaderlessVoidSkyRepair(Minecraft mc, float partialTicks) {
        // Probe disabled.
    }

    protected VoidSkyRepairSamples sampleVoidSkyRepairPixels(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new VoidSkyRepairSamples(false, "invalid-size");
        }
        int[] xs = new int[]{width / 4, width / 2, Math.max(0, width * 3 / 4)};
        int[] ys = new int[]{height / 4, height / 2, Math.max(0, height * 3 / 4)};
        StringBuilder summary = new StringBuilder();
        boolean needsRepair = false;
        for (int y : ys) {
            for (int x : xs) {
                VoidSkyRepairPixel pixel = readFramebufferRepairPixel(x, y);
                if (summary.length() > 0) {
                    summary.append(';');
                }
                summary.append(pixel.summary(x, y));
                if (pixel.skyDepth() && pixel.brightness() <= 12) {
                    needsRepair = true;
                }
            }
        }
        return new VoidSkyRepairSamples(needsRepair, summary.toString());
    }

    protected VoidSkyRepairPixel readFramebufferRepairPixel(int x, int y) {
        try {
            IntBuffer color = BufferUtils.createIntBuffer(1);
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            int rgba = color.get(0);
            int r = rgba & 0xFF;
            int g = rgba >> 8 & 0xFF;
            int b = rgba >> 16 & 0xFF;
            int a = rgba >> 24 & 0xFF;
            float z = depth.get(0);
            return new VoidSkyRepairPixel(r, g, b, a, z);
        } catch (RuntimeException | LinkageError ignored) {
            return new VoidSkyRepairPixel(-1, -1, -1, -1, -1.0F);
        }
    }

    protected void logShaderlessVoidSkyRepair(String stage, String detail) {
        // Diagnostic disabled.
}

    protected record VoidSkyRepairSamples(boolean needsRepair, String summary) {
    }

    protected record VoidSkyRepairPixel(int r, int g, int b, int a, float depth) {
        private boolean skyDepth() {
            return depth >= 0.999F;
        }

        private int brightness() {
            return Math.max(r, Math.max(g, b));
        }

        private String summary(int x, int y) {
            return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ") depth=" + depth;
        }
    }

    protected String readFramebufferPixelSummary(int x, int y) {
        return readFramebufferPixelSummary(x, y, true);
    }

    protected String readFramebufferPixelSummary(int x, int y, boolean includeDepth) {
        try {
            IntBuffer color = BufferUtils.createIntBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            int rgba = color.get(0);
            int r = rgba & 0xFF;
            int g = rgba >> 8 & 0xFF;
            int b = rgba >> 16 & 0xFF;
            int a = rgba >> 24 & 0xFF;
            if (!includeDepth) {
                return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ")";
            }
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ") depth=" + depth.get(0);
        } catch (RuntimeException | LinkageError ignored) {
            return x + "," + y + "=unreadable";
        }
    }

    protected String formatFloatArray(float[] values) {
        if (values == null || values.length < 4) {
            return "null";
        }
        return values[0] + "," + values[1] + "," + values[2] + "," + values[3];
    }

    public boolean shouldRenderClouds() {
        return !isPipelineActive || !shouldSkipAllMainGbufferRendering() && !"off".equals(shaderProperties.renderSettings().clouds());
    }

    public boolean shouldSkipAllMainGbufferRendering() {
        return isPipelineActive
                && !renderingShadowMap
                && shaderProperties.renderSettings().skipAllRendering();
    }

    public void applyTerrainOcclusionCullingSetting() {
        if (!isPipelineActive
                || terrainOcclusionOverrideActive
                || shaderProperties.renderSettings().occlusionCulling()) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        previousRenderChunksManyForOcclusion = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean((mc), false, "field_175612_E", "renderChunksMany");
        terrainOcclusionOverrideActive = true;
        com.l.ausm.impl.util.MinecraftReflectionCompat.setRenderChunksMany(mc, false);
    }

    public void restoreTerrainOcclusionCullingSetting() {
        if (!terrainOcclusionOverrideActive) {
            return;
        }
        terrainOcclusionOverrideActive = false;
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setRenderChunksMany(mc, previousRenderChunksManyForOcclusion);
        }
    }

    public ICamera mainFrustumCullingCamera(ICamera camera) {
        if (!isPipelineActive || shaderProperties.renderSettings().frustumCulling()) {
            return camera;
        }
        return ALWAYS_VISIBLE_CAMERA;
    }

    public boolean shouldCullShadowTerrain() {
        return !isPipelineActive || shaderProperties.renderSettings().shadowCulling();
    }

    public void applySkySunPathRotation() {
        if (isPipelineActive && sunPathRotation != 0.0f) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179114_b", "rotate"},
                new Class<?>[] {float.class, float.class, float.class, float.class}, (sunPathRotation), (0.0F), (0.0F), (1.0F));;
        }
    }

    public void applyTerrainCulling(WorldRenderingPhase phase) {
        if (!isPipelineActive || terrainCullOverrideActive || !shouldDisableCullForPhase(phase)) {
            return;
        }
        previousTerrainCullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        terrainCullOverrideActive = true;
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
    }

    public void restoreTerrainCulling() {
        if (!terrainCullOverrideActive) {
            return;
        }
        terrainCullOverrideActive = false;
        if (previousTerrainCullEnabled) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        }
    }

    public boolean shouldDisableNothiriumChunkCulling(BlockRenderLayer layer) {
        if (!isPipelineActive || renderingShadowMap || layer == null) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase == WorldRenderingPhase.TERRAIN_SOLID
                || phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                || phase == WorldRenderingPhase.TERRAIN_CUTOUT
                || phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT;
    }

    protected boolean shouldDisableCullForPhase(WorldRenderingPhase phase) {
        if (phase == WorldRenderingPhase.TERRAIN_SOLID) {
            return shaderProperties.renderSettings().backFaceSolid();
        }
        if (phase == WorldRenderingPhase.TERRAIN_CUTOUT) {
            return shaderProperties.renderSettings().backFaceCutout();
        }
        if (phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED) {
            return shaderProperties.renderSettings().backFaceCutoutMipped();
        }
        return phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                && shaderProperties.renderSettings().backFaceTranslucent();
    }

    public boolean shouldUsePipelineEntityFormat() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((mc), new String[] {"func_152345_ab", "isCallingFromMinecraftThread"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            return false;
        }
        RenderPass pass = activePass;
        if (!isPipelineActive || !worldFrameActive || pass == null || renderingGuiScreen()) {
            return false;
        }
        if (isBetweenlandsEntity(currentEntityKey) || currentEntityKey == null && isBetweenlandsRenderStack()) {
            return false;
        }
        if (pass.stage() == ProgramStage.SHADOW) {
            return true;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesEntityFormat();
        }
        return pass.stage() == ProgramStage.SHADOW
                || pass == RenderPass.GBUFFERS_ITEM
                || pass == RenderPass.GBUFFERS_ENTITIES
                || pass == RenderPass.GBUFFERS_ENTITIES_GLOWING
                || pass == RenderPass.GBUFFERS_HAND
                || pass == RenderPass.GBUFFERS_HAND_WATER
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT;
    }

    public boolean shouldUsePipelineBlockFormat() {
        return pipelineTerrainFormatSupported();
    }

    public boolean isPipelineActive() {
        return isPipelineActive;
    }

    protected boolean shouldUseShaderlessBloomVertexMetadata() {
        return shouldUsePipelineBlockFormat()
                && !isPipelineActive
                && !AusmBloomLayer.shouldUseShaderlessNativeHook()
                && bloomRenderer.hasBloomResources();
    }

    public boolean isShadowPassActive() {
        return isPipelineActive && (renderingShadowMap || activePass != null && activePass.stage() == ProgramStage.SHADOW);
    }

    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : activePhase;
    }

    public void logSkyPipelineProbe(String stage) {
        freshSkyProbe("sky-" + stage, "");
    }

    protected String skyProbeWorldSummary() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        Entity view = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (world == null) {
            return "null";
        }
        return "dim=" + safeDimensionId(world)
                + ",time=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world)
                + ",celestial=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, 0.0f)
                + ",rain=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, 0.0f)
                + ",thunder=" + com.l.ausm.impl.util.MinecraftReflectionCompat.worldThunderStrength(world, 0.0f)
                + ",viewYaw=" + (view != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.rotationYaw(view) : Float.NaN)
                + ",viewPitch=" + (view != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.rotationPitch(view) : Float.NaN);
    }

    protected static String skyProbeGlStateSummary() {
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int activeTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture0Binding = activeTextureBinding;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0Binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } finally {
            GL13.glActiveTexture(activeTexture);
        }
        return glStateSummary()
                + ",texActiveBinding=" + activeTextureBinding
                + ",tex0Binding=" + texture0Binding;
    }

    public int renderNothiriumTerrainLayer(BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        int visibleCount = renderNothiriumVisibleTerrainLayer(layer, partialTicks, viewEntity);
        if (!shouldRetrySparseNothiriumTerrainAfterSetup(layer, visibleCount) || viewEntity == null) {
            return visibleCount;
        }

        // Nothirium's normal RenderGlobal hook has already populated the lists
        // for the current frame. Only rebuild its camera/frustum lists after a
        // sparse draw; doing setup before every layer made shadered terrain pay
        // the full renderer update cost even when the existing lists were valid.
        if (!setupNothiriumShaderedMainTerrainLists(false)) {
            return visibleCount;
        }

        return renderNothiriumVisibleTerrainLayer(layer, partialTicks, viewEntity);
    }

    protected int renderNothiriumVisibleTerrainLayer(BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (viewEntity == null || !shouldUseNothiriumMainTerrainBridge()) {
            return -1;
        }
        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        int rendererDrawn = nothiriumShadowRenderer.renderVisibleLayerAllowingVanillaStride(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                nothiriumFallbackBlockEntityId(layer),
                nothiriumFallbackRenderType(layer)
        );
        if (rendererDrawn > 0 || !shouldSupplementSparseNothiriumTerrainFromProvider(layer, rendererDrawn)) {
            return rendererDrawn;
        }

        // Nothirium can finish a chunk compile after its renderer-owned setup
        // pass has already built empty visibility lists. Draw the same ready
        // provider chunks directly until a later setup pass repopulates them.
        return nothiriumShadowRenderer.renderProviderLayerSchedulingCompiles(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                nothiriumProviderSparseTerrainDistance(layer),
                nothiriumFallbackBlockEntityId(layer),
                nothiriumFallbackRenderType(layer),
                false
        );
    }

    protected void beginWaterAttachmentDeltaProbe(BlockRenderLayer layer) {
        waterAttachmentDeltaProbeActive = false;
        if (layer != BlockRenderLayer.TRANSLUCENT
                || activePass != RenderPass.GBUFFERS_WATER
                || waterAttachmentDeltaProbeLogs >= MAX_WATER_ATTACHMENT_DELTA_PROBE_LOGS
                || !pingPongManager.isInitialized()) {
            return;
        }
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        PipelineProgram program = programs.get(RenderPass.GBUFFERS_WATER);
        if (framebuffer == null || program == null) {
            return;
        }
        List<Attachment> attachments = effectiveDrawBuffersForCurrentPhase(program);
        int count = Math.min(waterAttachmentBefore.length, attachments.size());
        for (int slot = count; slot < waterAttachmentBefore.length; slot++) {
            waterAttachmentProbeWidths[slot] = 0;
            waterAttachmentProbeHeights[slot] = 0;
        }
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            for (int slot = 0; slot < count; slot++) {
                Attachment attachment = attachments.get(slot);
                int width = framebuffer.getAttachmentWidth(attachment);
                int height = framebuffer.getAttachmentHeight(attachment);
                int bytes = width * height * 4;
                ByteBuffer buffer = waterAttachmentBefore[slot];
                if (buffer == null || buffer.capacity() < bytes) {
                    buffer = BufferUtils.createByteBuffer(bytes);
                    waterAttachmentBefore[slot] = buffer;
                }
                buffer.clear();
                buffer.limit(bytes);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + slot);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
                waterAttachmentProbeWidths[slot] = width;
                waterAttachmentProbeHeights[slot] = height;
            }
            waterAttachmentDeltaProbeActive = count > 0;
        } finally {
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected void finishWaterAttachmentDeltaProbe() {
        if (!waterAttachmentDeltaProbeActive) {
            return;
        }
        waterAttachmentDeltaProbeActive = false;
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        StringBuilder result = new StringBuilder();
        try {
            for (int slot = 0; slot < waterAttachmentBefore.length; slot++) {
                ByteBuffer before = waterAttachmentBefore[slot];
                int width = waterAttachmentProbeWidths[slot];
                int height = waterAttachmentProbeHeights[slot];
                if (before == null || width <= 0 || height <= 0) {
                    continue;
                }
                int bytes = width * height * 4;
                ByteBuffer after = waterAttachmentAfter[slot];
                if (after == null || after.capacity() < bytes) {
                    after = BufferUtils.createByteBuffer(bytes);
                    waterAttachmentAfter[slot] = after;
                }
                after.clear();
                after.limit(bytes);
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0 + slot);
                GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, after);
                long changedPixels = 0L;
                long totalDelta = 0L;
                int maxDelta = 0;
                for (int pixel = 0; pixel < width * height; pixel++) {
                    boolean changed = false;
                    int base = pixel * 4;
                    for (int channel = 0; channel < 4; channel++) {
                        int delta = Math.abs((before.get(base + channel) & 0xFF) - (after.get(base + channel) & 0xFF));
                        if (delta != 0) {
                            changed = true;
                            totalDelta += delta;
                            maxDelta = Math.max(maxDelta, delta);
                        }
                    }
                    if (changed) {
                        changedPixels++;
                    }
                }
                if (result.length() > 0) {
                    result.append(';');
                }
                result.append("slot").append(slot)
                        .append('=').append(width).append('x').append(height)
                        .append(",changedPixels=").append(changedPixels)
                        .append(",totalDelta=").append(totalDelta)
                        .append(",maxDelta=").append(maxDelta);
            }
        } finally {
            GL11.glReadBuffer(previousReadBuffer);
        }
        waterAttachmentDeltaProbeLogs++;
        MainMod.LOGGER.warn("[AUSMWaterAttachmentDelta] call={} {} gl={}",
                waterAttachmentDeltaProbeLogs, result, glStateSummary());
    }

    protected boolean shouldRefreshNothiriumNonSolidListsBeforeDraw(BlockRenderLayer layer) {
        return isNothiriumNonSolidTerrainLayer(layer)
                && isNothiriumNonSolidMainTerrainPass(layer)
                && nothiriumShaderedMainPostCompileSetupFrame != pipelineFrameId;
    }

    protected boolean shouldRetrySparseNothiriumTerrainAfterSetup(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected boolean shouldRepairSparseNothiriumMainTerrain(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected boolean isNothiriumSparseMainTerrainRepairPass(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                && getPhase() == WorldRenderingPhase.TERRAIN_SOLID
                && (activePass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || activePass == RenderPass.GBUFFERS_TERRAIN);
    }

    protected boolean shouldDrawSparseNothiriumMainLayerFromProvider(BlockRenderLayer layer, int visibleCount) {
        return visibleCount >= 0
                && visibleCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                && isNothiriumSparseMainProviderDrawPass(layer);
    }

    protected boolean isNothiriumSparseMainProviderDrawPass(BlockRenderLayer layer) {
        WorldRenderingPhase phase = getPhase();
        if (layer == BlockRenderLayer.SOLID) {
            return phase == WorldRenderingPhase.TERRAIN_SOLID
                    && (activePass == RenderPass.GBUFFERS_TERRAIN_SOLID
                    || activePass == RenderPass.GBUFFERS_TERRAIN);
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT;
        }
        return false;
    }

    protected void enableNothiriumSparseMainProviderDraw() {
        nothiriumSparseMainProviderDrawUntilFrame = Math.max(
                nothiriumSparseMainProviderDrawUntilFrame,
                pipelineFrameId + NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_FRAMES
        );
    }

    protected double nothiriumSparseMainProviderDrawDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                ? NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_DISTANCE
                : NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_DISTANCE;
    }

    protected int nothiriumSparseMainProviderDrawMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return NOTHIRIUM_SPARSE_MAIN_PROVIDER_SOLID_MAX_CHUNKS;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return NOTHIRIUM_SPARSE_MAIN_PROVIDER_CUTOUT_MAX_CHUNKS;
        }
        return 0;
    }

    protected NothiriumSparseMainRepairResult repairSparseNothiriumMainTerrain(int visibleCount,
                                                                             double cameraX,
                                                                             double cameraY,
                                                                             double cameraZ) {
        nothiriumSparseMainRepairFrame = pipelineFrameId;
        int solid = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.SOLID,
                cameraX,
                cameraY,
                cameraZ,
                192.0D,
                96
        );
        int cutoutMipped = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.CUTOUT_MIPPED,
                cameraX,
                cameraY,
                cameraZ,
                160.0D,
                64
        );
        int cutout = nothiriumShadowRenderer.scheduleNearestLayerCompiles(
                BlockRenderLayer.CUTOUT,
                cameraX,
                cameraY,
                cameraZ,
                160.0D,
                64
        );
        nothiriumShadowRenderer.drainUploads();
        boolean setup = forceSetupNothiriumShaderedMainTerrainListsAfterRepair();
        NothiriumSparseMainRepairResult result = new NothiriumSparseMainRepairResult(solid, cutoutMipped, cutout, setup);
        logNothiriumSparseMainRepair(visibleCount, result, cameraX, cameraY, cameraZ);
        return result;
    }

    protected boolean shouldSupplementSparseNothiriumTerrainFromProvider(BlockRenderLayer layer, int visibleCount) {
        return false;
    }

    protected double nothiriumProviderSparseTerrainDistance(BlockRenderLayer layer) {
        // Nothirium's provider fallback must not hide a ready VBO behind a
        // second AUSM distance cap. Nothirium has already validated the chunk
        // and the draw path still rejects invalid buffers and ranges.
        return -1.0D;
    }

    protected int nothiriumProviderSparseTerrainMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 128;
        }
        if (layer == BlockRenderLayer.SOLID) {
            return 384;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return 256;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return 192;
        }
        return 0;
    }

    protected boolean shouldScheduleNothiriumProviderSupplementCompiles(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID
                || layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT
                || layer == BlockRenderLayer.TRANSLUCENT;
    }

    protected boolean shouldRepairEmptyNothiriumNonSolidLayer(BlockRenderLayer layer, int visibleCount) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || visibleCount != 0
                || !isNothiriumNonSolidTerrainLayer(layer)
                || !isNothiriumNonSolidMainTerrainPass(layer)) {
            return false;
        }
        long lastFrame = nothiriumNonSolidRepairFrame(layer);
        return lastFrame == Long.MIN_VALUE
                || pipelineFrameId - lastFrame >= NOTHIRIUM_NON_SOLID_REPAIR_COOLDOWN_FRAMES;
    }

    protected static boolean isNothiriumNonSolidTerrainLayer(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT
                || layer == BlockRenderLayer.TRANSLUCENT;
    }

    protected boolean isNothiriumNonSolidMainTerrainPass(BlockRenderLayer layer) {
        WorldRenderingPhase phase = getPhase();
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return phase == WorldRenderingPhase.TERRAIN_CUTOUT
                    && activePass == RenderPass.GBUFFERS_TERRAIN_CUTOUT;
        }
        return layer == BlockRenderLayer.TRANSLUCENT
                && phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT
                && activePass == RenderPass.GBUFFERS_WATER;
    }

    protected long nothiriumNonSolidRepairFrame(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return nothiriumNonSolidRepairCutoutMippedFrame;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return nothiriumNonSolidRepairCutoutFrame;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return nothiriumNonSolidRepairTranslucentFrame;
        }
        return Long.MIN_VALUE;
    }

    protected void markNothiriumNonSolidRepairAttempt(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            nothiriumNonSolidRepairCutoutMippedFrame = pipelineFrameId;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            nothiriumNonSolidRepairCutoutFrame = pipelineFrameId;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            nothiriumNonSolidRepairTranslucentFrame = pipelineFrameId;
        }
    }

    protected void enableNothiriumNonSolidProviderDraw(BlockRenderLayer layer) {
        long untilFrame = pipelineFrameId + NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_FRAMES;
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            nothiriumNonSolidProviderDrawCutoutMippedUntilFrame = untilFrame;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            nothiriumNonSolidProviderDrawCutoutUntilFrame = untilFrame;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            nothiriumNonSolidProviderDrawTranslucentUntilFrame = untilFrame;
        }
    }

    protected boolean shouldDrawEmptyNothiriumNonSolidLayerFromProvider(BlockRenderLayer layer, int visibleCount) {
        return visibleCount == 0
                && isNothiriumNonSolidTerrainLayer(layer)
                && isNothiriumNonSolidMainTerrainPass(layer);
    }

    protected long nothiriumNonSolidProviderDrawUntilFrame(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return nothiriumNonSolidProviderDrawCutoutMippedUntilFrame;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return nothiriumNonSolidProviderDrawCutoutUntilFrame;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return nothiriumNonSolidProviderDrawTranslucentUntilFrame;
        }
        return Long.MIN_VALUE;
    }

    protected double nothiriumNonSolidRepairDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT ? 128.0D : 160.0D;
    }

    protected int nothiriumNonSolidRepairMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 64;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return 96;
        }
        return 0;
    }

    protected double nothiriumNonSolidProviderDrawDistance(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT ? 96.0D : 128.0D;
    }

    protected int nothiriumNonSolidProviderDrawMaxChunks(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return 96;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.CUTOUT) {
            return 96;
        }
        return 0;
    }

    protected void logNothiriumSparseMainRepair(int visibleCount, NothiriumSparseMainRepairResult repair,
                                              double cameraX, double cameraY, double cameraZ) {
        if (nothiriumSparseMainRepairLogs++ >= MAX_NOTHIRIUM_SPARSE_MAIN_REPAIR_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumSparseMainRepair] call={} visible={} solidWork={} cutoutMippedWork={} cutoutWork={} setup={} frame={} activePass={} phase={} camera={}/{}/{} gl={}",
                nothiriumSparseMainRepairLogs,
                visibleCount,
                repair.solidWork,
                repair.cutoutMippedWork,
                repair.cutoutWork,
                repair.setup,
                pipelineFrameId,
                String.valueOf(activePass),
                getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                glStateSummary()
        );
    }

    protected void logNothiriumNonSolidRepair(BlockRenderLayer layer, int scheduled, boolean setup,
                                            double cameraX, double cameraY, double cameraZ) {
        if (nothiriumNonSolidRepairLogs++ >= MAX_NOTHIRIUM_NON_SOLID_REPAIR_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumNonSolidRepair] call={} layer={} scheduled={} setup={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} gl={}",
                nothiriumNonSolidRepairLogs,
                layer,
                scheduled,
                setup,
                pipelineFrameId,
                String.valueOf(activePass),
                getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                nothiriumNonSolidRepairMaxChunks(layer),
                nothiriumNonSolidRepairDistance(layer),
                glStateSummary()
        );
    }

    protected void logNothiriumNonSolidProviderDraw(BlockRenderLayer layer, int providerCount,
                                                  double cameraX, double cameraY, double cameraZ) {
        if (nothiriumNonSolidProviderDrawLogs++ >= MAX_NOTHIRIUM_NON_SOLID_PROVIDER_DRAW_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumNonSolidProviderDraw] call={} layer={} providerCount={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} untilFrame={} gl={}",
                nothiriumNonSolidProviderDrawLogs,
                layer,
                providerCount,
                pipelineFrameId,
                String.valueOf(activePass),
                getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                nothiriumNonSolidProviderDrawMaxChunks(layer),
                nothiriumNonSolidProviderDrawDistance(layer),
                nothiriumNonSolidProviderDrawUntilFrame(layer),
                glStateSummary()
        );
    }

    protected void logNothiriumSparseMainProviderDraw(BlockRenderLayer layer, int visibleCount, int providerCount,
                                                    double cameraX, double cameraY, double cameraZ) {
        if (nothiriumSparseMainProviderDrawLogs++ >= MAX_NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumSparseMainProviderDraw] call={} layer={} visible={} providerCount={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} untilFrame={} gl={}",
                nothiriumSparseMainProviderDrawLogs,
                layer,
                visibleCount,
                providerCount,
                pipelineFrameId,
                String.valueOf(activePass),
                getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                nothiriumSparseMainProviderDrawMaxChunks(layer),
                nothiriumSparseMainProviderDrawDistance(layer),
                nothiriumSparseMainProviderDrawUntilFrame,
                glStateSummary()
        );
    }

    protected void logNothiriumMainSetupBridge(BlockRenderLayer layer, boolean setup, double cameraX, double cameraY, double cameraZ) {
        if (!setup || nothiriumMainSetupBridgeLogs >= MAX_NOTHIRIUM_MAIN_SETUP_BRIDGE_LOGS) {
            return;
        }
        nothiriumMainSetupBridgeLogs++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumMainSetupBridge] call={} setup={} layer={} frame={} activePass={} phase={} camera={}/{}/{} gl={}",
                nothiriumMainSetupBridgeLogs,
                setup,
                layer,
                pipelineFrameId,
                String.valueOf(activePass),
                getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                glStateSummary()
        );
    }

    protected boolean setupNothiriumShaderedMainTerrainLists(boolean afterCompileUpload) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass == null
                || !shouldUseNothiriumMainTerrainBridge()) {
            return false;
        }
        if (afterCompileUpload) {
            if (nothiriumShaderedMainPostCompileSetupFrame == pipelineFrameId) {
                return true;
            }
        } else if (nothiriumShaderedMainSetupFrame == pipelineFrameId) {
            return true;
        }

        maintainNothiriumShaderedMainTerrainChunks();
        // Nothirium completes chunk compilation asynchronously and queues the
        // VBO upload on its render thread. Drain that queue before setup builds
        // the visibility lists, otherwise freshly compiled chunks remain absent
        // until a later renderer-owned setup pass.
        nothiriumShadowRenderer.drainUploads();
        boolean setup = NothiriumBypass.setupForShaderedMainTerrainBridge();
        if (setup) {
            if (afterCompileUpload) {
                nothiriumShaderedMainPostCompileSetupFrame = pipelineFrameId;
            } else {
                nothiriumShaderedMainSetupFrame = pipelineFrameId;
            }
        }
        return setup;
    }

    private void maintainNothiriumShaderedMainTerrainChunks() {
        if (nothiriumShaderedMainSetupFrame == pipelineFrameId) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        RenderGlobal renderGlobal = mc == null
                ? null
                : com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
        if (renderGlobal == null) {
            return;
        }
        try {
            // Shaderless Nothirium receives this maintenance from EntityRenderer.
            // Shadered terrain replaces that draw sequence, so keep the same
            // dirty-section queue and dispatcher upload phase alive here.
            com.l.ausm.impl.util.MinecraftReflectionCompat.updateChunks(
                    renderGlobal,
                    System.nanoTime() + 4_000_000L
            );
        } catch (RuntimeException | LinkageError ignored) {
            // The normal Nothirium setup remains usable if another renderer owns
            // the update hook for this frame.
        }
    }

    /**
     * RenderLib refreshes Nothirium's camera/frustum snapshot from the vanilla
     * RenderGlobal setup hook. Shadered world rendering can reach the Nothirium
     * bridge without traversing that hook, leaving visibility traversal rooted
     * at the current section only.
     */
    private void prepareNothiriumShaderedMainTerrainCamera() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        net.minecraft.client.renderer.RenderGlobal renderGlobal =
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (renderGlobal == null || viewEntity == null) {
            return;
        }
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setupTerrain(
                    renderGlobal,
                    viewEntity,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc),
                    ALWAYS_VISIBLE_CAMERA,
                    (int) pipelineFrameId,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.playerIsSpectator(
                            com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc))
            );
        } catch (RuntimeException | LinkageError ignored) {
            // Nothirium's own setup remains the fallback if another renderer
            // owns the vanilla setup hook for this frame.
        }
    }

    protected boolean forceSetupNothiriumShaderedMainTerrainListsAfterRepair() {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass == null
                || !shouldUseNothiriumMainTerrainBridge()) {
            return false;
        }

        boolean setup = NothiriumBypass.setupForShaderedMainTerrainBridge();
        if (setup) {
            nothiriumShaderedMainPostCompileSetupFrame = pipelineFrameId;
            nothiriumShaderedMainSetupFrame = pipelineFrameId;
        }
        return setup;
    }

    protected static final class NothiriumSparseMainRepairResult {
        private final int solidWork;
        private final int cutoutMippedWork;
        private final int cutoutWork;
        private final boolean setup;

        private NothiriumSparseMainRepairResult(int solidWork, int cutoutMippedWork, int cutoutWork, boolean setup) {
            this.solidWork = solidWork;
            this.cutoutMippedWork = cutoutMippedWork;
            this.cutoutWork = cutoutWork;
            this.setup = setup;
        }

        private int totalWork() {
            return Math.max(0, solidWork) + Math.max(0, cutoutMippedWork) + Math.max(0, cutoutWork);
        }
    }

    protected double nothiriumMainTerrainFallbackDistance() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) == null) {
            return -1.0D;
        }
        int chunks = Math.max(2, com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc));
        return chunks * 16.0D + 32.0D;
    }

    public boolean renderNothiriumRendererPass(Object chunkRenderPass) {
        if (!isPipelineActive
                || renderingShadowMap
                || !shouldUseNothiriumMainTerrainBridge()
                || !isNothiriumTranslucentPass(chunkRenderPass)) {
            return false;
        }
        return shouldCancelDuplicateNothiriumTranslucentPass(true);
    }

    protected boolean shouldCancelDuplicateNothiriumTranslucentPass(boolean translucentPass) {
        return translucentPass && shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer.TRANSLUCENT);
    }

    protected static boolean isNothiriumTranslucentPass(Object chunkRenderPass) {
        return chunkRenderPass instanceof Enum<?> pass && "TRANSLUCENT".equals(pass.name());
    }

    protected int nothiriumFallbackBlockEntityId(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        int stillWater = blockEntityId(nothiriumFallbackWaterState("field_150355_j", "WATER"));
        if (stillWater != 0) {
            return stillWater;
        }
        return blockEntityId(nothiriumFallbackWaterState("field_150358_i", "FLOWING_WATER"));
    }

    protected short nothiriumFallbackRenderType(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        return (short) com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderTypeOrdinal(nothiriumFallbackWaterState("field_150355_j", "WATER"));
    }

    protected IBlockState nothiriumFallbackWaterState(String srgName, String mcpName) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.blockDefaultState(com.l.ausm.impl.util.MinecraftReflectionCompat.field(Blocks.class, Block.class, null, srgName, mcpName));
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

        ResourceLocation entityKey = com.l.ausm.impl.util.MinecraftReflectionCompat.entityKey(entity);
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

    protected int vehicleId(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) == null) {
            return 0;
        }
        return entityId(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)));
    }

    protected boolean vehicleInWater(Minecraft mc) {
        return mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc))), new String[] {"func_70090_H", "isInWater"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    protected float[] vehicleLookVector(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return vec3(com.l.ausm.impl.util.MinecraftReflectionCompat.look(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)), com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc)));
    }

    protected float[] relativeVehiclePosition(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)) == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        Entity vehicle = com.l.ausm.impl.util.MinecraftReflectionCompat.entityRidingEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosX(vehicle), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(vehicle), partialTicks);
        double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosY(vehicle), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(vehicle), partialTicks);
        double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosZ(vehicle), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(vehicle), partialTicks);
        return new float[]{
                (float) (cameraPositionUnshifted[0] - x),
                (float) (cameraPositionUnshifted[1] - y),
                (float) (cameraPositionUnshifted[2] - z)
        };
    }

    protected static float[] bodyVector(Entity entity) {
        if (entity == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return vec3(com.l.ausm.impl.util.MinecraftReflectionCompat.call((entity), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_189651_aD", "getForward", "func_70040_Z", "getLookVec"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS));
    }

    protected float[] lightningBoltPosition(Minecraft mc) {
        World world = renderWorld(mc);
        if (mc == null || world == null) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }
        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        for (Entity entity : com.l.ausm.impl.util.MinecraftReflectionCompat.loadedEntityList(world)) {
            if (entity instanceof EntityLightningBolt) {
                double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosX(entity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(entity), partialTicks);
                double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosY(entity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(entity), partialTicks);
                double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosZ(entity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(entity), partialTicks);
                return new float[]{
                        (float) (x - cameraPositionUnshifted[0]),
                        (float) (y - cameraPositionUnshifted[1]),
                        (float) (z - cameraPositionUnshifted[2]),
                        1.0f
                };
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    protected void updateEndFlashState(Minecraft mc) {
        previousEndFlashIntensity = endFlashIntensity;
        endFlashIntensity = 0.0f;
        endFlashPosition[0] = 0.0f;
        endFlashPosition[1] = 0.0f;
        endFlashPosition[2] = 0.0f;

        if (!shaderProperties.renderSettings().supportsEndFlash()) {
            return;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null) {
            return;
        }
        if (!isEndWorld(world)) {
            return;
        }

        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        EntityDragon strongestDragon = null;
        float strongestIntensity = 0.0f;
        for (Entity entity : com.l.ausm.impl.util.MinecraftReflectionCompat.loadedEntityList(world)) {
            if (!(entity instanceof EntityDragon dragon) || com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((dragon), 0, "field_70995_bG", "deathTicks") <= 0) {
                continue;
            }
            float intensity = clamp01((com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((dragon), 0, "field_70995_bG", "deathTicks") + partialTicks) / 200.0f);
            if (intensity > strongestIntensity) {
                strongestIntensity = intensity;
                strongestDragon = dragon;
            }
        }
        if (strongestDragon == null) {
            return;
        }

        double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosX(strongestDragon), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(strongestDragon), partialTicks) - cameraPositionUnshifted[0];
        double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosY(strongestDragon), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(strongestDragon), partialTicks) - cameraPositionUnshifted[1];
        double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.prevPosZ(strongestDragon), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(strongestDragon), partialTicks) - cameraPositionUnshifted[2];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0e-4) {
            double scale = 100.0 / length;
            endFlashPosition[0] = (float) (x * scale);
            endFlashPosition[1] = (float) (y * scale);
            endFlashPosition[2] = (float) (z * scale);
        } else {
            endFlashPosition[1] = 100.0f;
        }
        endFlashYawDegrees = (float) Math.toDegrees(Math.atan2(x, z));
        endFlashPitchDegrees = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
        endFlashIntensity = strongestIntensity;
    }

    protected void resetEndFlashState() {
        endFlashPosition[0] = 0.0f;
        endFlashPosition[1] = 0.0f;
        endFlashPosition[2] = 0.0f;
        endFlashIntensity = 0.0f;
        previousEndFlashIntensity = 0.0f;
        endFlashYawDegrees = 0.0f;
        endFlashPitchDegrees = 0.0f;
    }

    protected boolean useEndFlashShadowLight(World world) {
        return shaderProperties.renderSettings().supportsEndFlash()
                && isEndWorld(world)
                && endFlashIntensity > 0.0f;
    }

    protected static boolean isEndWorld(World world) {
        return world != null && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)) == 1;
    }

    public void beginRenderedItem(ItemStack stack) {
        renderedItemIdStack.push(currentRenderedItemId);
        currentRenderedItemId = currentRenderedItemId(stack);
        currentRenderedItemDebugName = renderedItemDebugName(stack);
        uploadCurrentRenderedItemId();
    }

    public void endRenderedItem() {
        currentRenderedItemId = renderedItemIdStack.isEmpty() ? -1 : renderedItemIdStack.pop();
        currentRenderedItemDebugName = "";
        uploadCurrentRenderedItemId();
    }

    protected String renderedItemDebugName(ItemStack stack) {
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return "empty";
        }
        Item item = com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackItem(stack);
        ResourceLocation key = item != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.call((item), net.minecraft.util.ResourceLocation.class, null, new String[] {"getRegistryName"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS) : null;
        return (key != null ? key.toString() : item != null ? item.getClass().getName() : "null")
                + ":" + com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackMetadata(stack);
    }

    protected void uploadCurrentRenderedItemId() {
        ShaderProgram program = activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "currentRenderedItemId");
        }
    }

    public boolean shouldRenderEntityWithVanillaProgram(Entity entity) {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        if (activePass.stage() != ProgramStage.GBUFFERS) {
            return false;
        }
        return isBetweenlandsEntity(com.l.ausm.impl.util.MinecraftReflectionCompat.entityKey(entity));
    }

    protected static boolean isBetweenlandsEntity(ResourceLocation entityKey) {
        return entityKey != null && "thebetweenlands".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(entityKey));
    }

    protected static boolean isBetweenlandsRenderStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("thebetweenlands.client.render.entity.")
                    || className.startsWith("thebetweenlands.client.render.model.entity.")) {
                return true;
            }
        }
        return false;
    }

    protected int heldItemId(ItemStack stack) {
        return shaderProperties.itemIds().idFor(stack);
    }

    protected int currentRenderedItemId(ItemStack stack) {
        Integer explicitItemId = shaderProperties.itemIds().explicitIdFor(stack);
        if (explicitItemId != null) {
            return explicitItemId;
        }
        int blockItemId = currentRenderedBlockItemId(stack);
        return blockItemId != 0 ? blockItemId : 0;
    }

    protected int currentRenderedBlockItemId(ItemStack stack) {
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return 0;
        }
        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.call(net.minecraft.block.Block.class, net.minecraft.block.Block.class, null, new String[] {"func_149634_a", "getBlockFromItem"},
                new Class<?>[] {net.minecraft.item.Item.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackItem(stack)));
        if (block == null || block == com.l.ausm.impl.util.MinecraftReflectionCompat.field(Blocks.class, Block.class, null, "field_150350_a", "AIR")) {
            return 0;
        }
        try {
            int metadata = com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackMetadata(stack);
            IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.blockStateFromMeta(block, metadata);
            if (state != null) {
                return shaderProperties.blockIds().idFor(state);
            }
        } catch (RuntimeException ignored) {
        }
        return shaderProperties.blockIds().idFor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockDefaultState(block));
    }

    protected ItemStack heldMainStack(Minecraft mc) {
        EntityLivingBase player = com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc);
        if (player == null) {
            return null;
        }

        ItemStack mainHand = com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemMainhand(player);
        if (!shaderProperties.renderSettings().oldHandLight()) {
            return mainHand;
        }

        ItemStack offHand = com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemOffhand(player);
        return heldBlockLightValue(offHand) > heldBlockLightValue(mainHand) ? offHand : mainHand;
    }

    protected ItemStack heldOffhandStack(Minecraft mc) {
        EntityLivingBase player = com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc);
        return player != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.heldItemOffhand(player) : null;
    }

    protected int heldBlockLightValue(ItemStack stack) {
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return 0;
        }

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        if (shaderItemId > 44000 && shaderItemId < 44100) {
            return 15;
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.call(net.minecraft.block.Block.class, net.minecraft.block.Block.class, null, new String[] {"func_149634_a", "getBlockFromItem"},
                new Class<?>[] {net.minecraft.item.Item.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackItem(stack)));
        int blockLight = block != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.callInt((block), new String[] {"getLightValue", "func_149750_m"},
                new Class<?>[] {net.minecraft.block.state.IBlockState.class}, 0, (com.l.ausm.impl.util.MinecraftReflectionCompat.blockDefaultState(block))) : 0;
        if (blockLight > 0) {
            return blockLight;
        }

        return 0;
    }

    protected float[] heldBlockLightColor(ItemStack stack) {
        int lightValue = heldBlockLightValue(stack);
        if (lightValue <= 0) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        float[] itemColor = compatLightColorForVoxelId(localActItemVoxelId(shaderItemId));
        if (itemColor != null) {
            return itemColor;
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.call(net.minecraft.block.Block.class, net.minecraft.block.Block.class, null, new String[] {"func_149634_a", "getBlockFromItem"},
                new Class<?>[] {net.minecraft.item.Item.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.itemStackItem(stack)));
        if (block != null) {
            int shaderBlockId = currentRenderedBlockItemId(stack);
            float[] blockColor = compatLightColorForVoxelId(localActVoxelId(shaderBlockId));
            if (blockColor != null) {
                return blockColor;
            }
        }

        return new float[]{1.0f, 1.0f, 1.0f};
    }

    protected void logHeldColoredLightProbe(Minecraft mc) {
        if (heldColoredLightProbeLogs >= 16 || mc == null || shaderProperties == null) {
            return;
        }
        ItemStack main = heldMainStack(mc);
        ItemStack off = heldOffhandStack(mc);
        int mainLight = heldBlockLightValue(main);
        int offLight = heldBlockLightValue(off);
        if (mainLight <= 0 && offLight <= 0) {
            return;
        }
        float[] mainColor = heldBlockLightColor(main);
        float[] offColor = heldBlockLightColor(off);
        heldColoredLightProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMHeldColoredLight] probe={} frame={} mainId={} mainLight={} mainColor={}/{}/{} offId={} offLight={} offColor={}/{}/{}",
                heldColoredLightProbeLogs,
                pipelineFrameId,
                heldItemId(main),
                mainLight,
                mainColor[0],
                mainColor[1],
                mainColor[2],
                heldItemId(off),
                offLight,
                offColor[0],
                offColor[1],
                offColor[2]
        );
    }

    protected static int localActItemVoxelId(int itemId) {
        if (itemId == 44024) {
            return 24;
        }
        if (itemId >= 44070 && itemId <= 44080) {
            return itemId - 44000;
        }
        return 0;
    }

    protected static float[] compatLightColorForVoxelId(int voxelId) {
        return switch (voxelId) {
            case 24 -> new float[]{1.0f, 1.0f, 1.0f};
            case 70 -> new float[]{1.0f, 0.12f, 0.08f};
            case 71 -> new float[]{1.0f, 0.46f, 0.08f};
            case 72 -> new float[]{1.0f, 0.88f, 0.16f};
            case 73 -> new float[]{0.48f, 1.0f, 0.12f};
            case 74 -> new float[]{0.12f, 0.80f, 0.20f};
            case 75 -> new float[]{0.08f, 0.88f, 1.0f};
            case 76 -> new float[]{0.36f, 0.66f, 1.0f};
            case 77 -> new float[]{0.14f, 0.24f, 1.0f};
            case 78 -> new float[]{0.58f, 0.20f, 1.0f};
            case 79 -> new float[]{1.0f, 0.16f, 0.90f};
            case 80 -> new float[]{1.0f, 0.48f, 0.74f};
            case 110 -> new float[]{1.0f, 0.18f, 0.14f};
            case 111 -> new float[]{1.0f, 0.48f, 0.16f};
            case 112 -> new float[]{1.0f, 0.88f, 0.18f};
            case 113 -> new float[]{0.46f, 1.0f, 0.18f};
            case 114 -> new float[]{0.18f, 0.95f, 0.28f};
            case 115 -> new float[]{0.12f, 0.9f, 1.0f};
            case 116 -> new float[]{0.42f, 0.7f, 1.0f};
            case 117 -> new float[]{0.18f, 0.3f, 1.0f};
            case 118 -> new float[]{0.62f, 0.24f, 1.0f};
            case 119 -> new float[]{1.0f, 0.2f, 0.92f};
            case 120 -> new float[]{1.0f, 0.52f, 0.78f};
            default -> null;
        };
    }

    protected float[] entityColor(Entity entity) {
        if (entity instanceof EntityLivingBase living) {
            int hurtTime = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((living), 0, "field_70737_aN", "hurtTime");
            int deathTime = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((living), 0, "field_70725_aQ", "deathTime");
            if (hurtTime > 0 || deathTime > 0) {
                float hurtRatio = hurtTime / Math.max(1.0f, com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((living), 0, "field_70738_aO", "maxHurtTime"));
                float deathRatio = Math.min(1.0f, deathTime / 20.0f);
                float alpha = Math.max(hurtRatio, deathRatio) * 0.25f;
                return new float[]{1.0f, 0.0f, 0.0f, alpha};
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    public void setCurrentEntity(Entity entity) {
        currentEntityKey = com.l.ausm.impl.util.MinecraftReflectionCompat.entityKey(entity);
        currentEntityId = entityId(entity);
        currentEntityColor = entityColor(entity);
        uploadEntityUniforms();
    }

    public void clearCurrentEntity() {
        currentEntityKey = null;
        currentEntityId = 0;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        uploadEntityUniforms();
    }

    public void applyWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void restoreWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void applyWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        setIndexedBlend(Attachment.COLOR.getIndex(), true);
        setIndexedBlend(Attachment.DEPTH.getIndex(), true);
        setIndexedBlend(Attachment.NORMAL.getIndex(), false);
        setIndexedBlend(Attachment.COMPOSITE.getIndex(), false);
        setIndexedBlend(Attachment.AUX1.getIndex(), false);
        setIndexedBlend(Attachment.AUX2.getIndex(), false);
        setIndexedBlend(Attachment.AUX3.getIndex(), false);
        setIndexedBlend(Attachment.AUX4.getIndex(), false);
        // Final passes reconstruct the current water pixel from depthtex0, so
        // water must update the live depth buffer.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void restoreWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        resetIndexedBlendState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
    }

    protected void uploadEntityUniforms() {
        ShaderProgram program = activeProgram();
        if (program == null) {
            return;
        }

        uniformRegistry.upload(program, "entityId");
        uniformRegistry.upload(program, "entityColor");
    }

    public void applyChunkFade(RenderChunk renderChunk, BlockRenderLayer layer) {
        if (renderChunk == null) {
            return;
        }
        if (!ENABLE_CHUNK_FADE) {
            resetChunkFadeUniform();
            return;
        }
        if (shouldSuppressChunkFadeForBetterPortals()) {
            resetChunkFadeUniform();
            return;
        }
        if (!shouldUploadChunkFade(layer)) {
            return;
        }

        BlockPos position = com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkPosition(renderChunk);
        if (position == null) {
            resetChunkFadeUniform();
            return;
        }

        int dimensionId = safeDimensionId(renderChunkWorld(renderChunk));
        if (dimensionId == Integer.MIN_VALUE) {
            dimensionId = safeDimensionId(renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()));
        }
        applyChunkFade(dimensionId, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(position), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(position), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(position));
    }

    public void applyChunkFade(int blockX, int blockY, int blockZ) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = renderWorld(mc);
        applyChunkFade(safeDimensionId(world), blockX, blockY, blockZ);
    }

    protected void applyChunkFade(int dimensionId, int blockX, int blockY, int blockZ) {
        if (!ENABLE_CHUNK_FADE) {
            resetChunkFadeUniform();
            return;
        }
        if (shouldSuppressChunkFadeForBetterPortals()) {
            resetChunkFadeUniform();
            return;
        }
        if (!shouldUploadChunkFade(null)) {
            return;
        }

        currentChunkFade = chunkFadeValue(dimensionId, blockX, blockY, blockZ);
        uploadChunkFadeUniform();
    }

    public void resetChunkFadeUniform() {
        if (currentChunkFade == 1.0f) {
            return;
        }
        currentChunkFade = 1.0f;
        uploadChunkFadeUniform();
    }

    protected boolean shouldUploadChunkFade(BlockRenderLayer layer) {
        if (!isPipelineActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS || renderingShadowMap) {
            return false;
        }
        if (layer != null && layer != BlockRenderLayer.SOLID
                && layer != BlockRenderLayer.CUTOUT
                && layer != BlockRenderLayer.CUTOUT_MIPPED
                && layer != BlockRenderLayer.TRANSLUCENT) {
            return false;
        }
        return isChunkFadePass(activePass);
    }

    protected boolean shouldSuppressChunkFadeForBetterPortals() {
        return BetterPortalsCompat.isInstalled()
                && (isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive());
    }

    protected static boolean isChunkFadePass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WATER;
    }

    protected float chunkFadeValue(int dimensionId, int blockX, int blockY, int blockZ) {
        if (dimensionId == Integer.MIN_VALUE) {
            return 1.0f;
        }

        ChunkFadeKey key = new ChunkFadeKey(
                dimensionId,
                Math.floorDiv(blockX, 16),
                // Vertical flight should not fade every newly-entered section of an already visible column.
                0,
                Math.floorDiv(blockZ, 16)
        );
        ChunkFadeState state = chunkFadeStates.get(key);
        if (state == null) {
            float initial = pipelineFrameId <= chunkFadeWarmupUntilFrame ? 1.0f : 0.0f;
            state = new ChunkFadeState(initial, pipelineFrameId);
            chunkFadeStates.put(key, state);
            pruneChunkFadeStates();
            return state.value;
        }

        if (state.lastFrameSeen != pipelineFrameId) {
            state.value = clamp01(state.value + currentFrameTime / CHUNK_FADE_DURATION_SECONDS);
            state.lastFrameSeen = pipelineFrameId;
        }
        return state.value;
    }

    protected void uploadChunkFadeUniform() {
        ShaderProgram program = activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "mc_chunkFade");
        }
    }

    protected void resetChunkFadeState(boolean warmExistingChunks) {
        chunkFadeStates.clear();
        currentChunkFade = 1.0f;
        chunkFadeWarmupUntilFrame = warmExistingChunks ? pipelineFrameId + CHUNK_FADE_WARMUP_FRAMES : pipelineFrameId;
    }

    protected void pruneChunkFadeStates() {
        if (chunkFadeStates.size() <= MAX_CHUNK_FADE_STATES) {
            return;
        }

        long staleBefore = pipelineFrameId - CHUNK_FADE_STALE_FRAMES;
        Iterator<Map.Entry<ChunkFadeKey, ChunkFadeState>> iterator = chunkFadeStates.entrySet().iterator();
        while (iterator.hasNext() && chunkFadeStates.size() > MAX_CHUNK_FADE_STATES) {
            if (iterator.next().getValue().lastFrameSeen < staleBefore) {
                iterator.remove();
            }
        }
        iterator = chunkFadeStates.entrySet().iterator();
        while (iterator.hasNext() && chunkFadeStates.size() > MAX_CHUNK_FADE_STATES) {
            iterator.next();
            iterator.remove();
        }
    }

    public void beginPass(RenderPass pass) {
        beginPass(pass, WorldRenderingPhase.NONE);
    }

    protected void beginPass(RenderPass pass, WorldRenderingPhase phase) {
        if (!isPipelineActive || !worldFrameActive) {
            return;
        }

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        activePhase = phase;
        boolean bound = bindPass(pass);
        logPipelinePassProbe(pass, phase, bound);
        passStack.push(new PassScope(bound, previousPass, previousShaderKey, previousPhase, previousProgramTessellated, previousProgramGeometric));
    }

    protected void logPipelinePassProbe(RenderPass pass, WorldRenderingPhase phase, boolean bound) {
        if (pipelinePassProbeLogs >= MAX_PIPELINE_PASS_PROBE_LOGS) {
            return;
        }
        pipelinePassProbeLogs++;
        PipelineProgram declared = pass != null ? programs.get(pass) : null;
        PipelineProgram effective = pass != null ? effectivePipelineProgram(pass) : null;
        ShaderProgram shader = effective != null ? effective.shaderProgram() : null;
        MainMod.LOGGER.info(
                "[AUSMPassProbe] call={} pass={} phase={} bound={} active={} worldFrame={} shadow={} declared={} effective={} shader={} declaredBuffers={} effectiveBuffers={}",
                pipelinePassProbeLogs,
                pass,
                phase,
                bound,
                isPipelineActive,
                worldFrameActive,
                renderingShadowMap,
                declared != null && declared.enabled(),
                effective != null ? effective.pass() : "none",
                shader != null ? shader.getId() : -1,
                declared != null ? declared.drawBuffers() : "none",
                effective != null ? effective.drawBuffers() : "none"
        );
    }

    public boolean beginPhaseIfActive(WorldRenderingPhase phase) {
        if (renderingGuiScreen()) {
            return false;
        }
        RenderPass pass = passForPhase(phase);
        if (pass == null) {
            return false;
        }
        beginPass(pass, phase);
        return true;
    }

    public void beginPhase(WorldRenderingPhase phase) {
        beginPhaseIfActive(phase);
    }

    public WorldRenderingPhase blockEntityPhaseForCurrentForgePass() {
        if (renderingShadowMap) {
            return WorldRenderingPhase.BLOCK_ENTITIES;
        }
        return MinecraftReflectionCompat.forgeRenderPass() == 1
                ? WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT
                : WorldRenderingPhase.BLOCK_ENTITIES;
    }

    public void beginAstralConstellationPhase(Object constellation, WorldRenderingPhase phase) {
        setAstralConstellationColors(constellation);
        currentSkyDetailKind = 5;
        beginPhase(phase);
    }

    public void setAstralSolarEclipseFactor(float factor) {
        currentAstralSolarEclipseFactor = Math.max(0.0f, Math.min(1.0f, factor));
    }

    public void endAstralConstellationPhase() {
        endPass();
        currentSkyDetailKind = 0;
        resetAstralConstellationColors();
    }

    /**
     * Publishes the detail currently being submitted by a compatibility renderer.
     * Values are intentionally numeric so shaderpacks do not need string handling.
     * 1 Botania planet, 2 Botania ribbon/skybox, 3 Botania rainbow,
     * 4 Astral stars, 5 Astral constellation, 6 Astral sun/moon.
     */
    public void setSkyDetailAsset(String resourceName) {
        currentSkyDetailKind = skyDetailKind(resourceName);
    }

    public void setSkyDetailKind(int kind) {
        currentSkyDetailKind = Math.max(0, Math.min(6, kind));
    }

    public void clearSkyDetailAsset() {
        currentSkyDetailKind = 0;
    }

    /** Uploads per-draw detail state after a renderer changes its bound texture. */
    public void uploadSkyDetailUniforms() {
        if (!isPipelineActive || activePass == null) {
            return;
        }
        PipelineProgram pipelineProgram = effectivePipelineProgram(activePass);
        ShaderProgram program = pipelineProgram != null ? pipelineProgram.shaderProgram() : null;
        if (program == null || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.getId()) {
            return;
        }
        uniformRegistry.upload(program, "ausmSkyDetailKind");
        uniformRegistry.upload(program, "ausmSkyDetailTextureSize");
        uniformRegistry.upload(program, "ausmAstralConstellationColor");
        uniformRegistry.upload(program, "ausmAstralTierColor");
        uniformRegistry.upload(program, "ausmAstralSolarEclipse");
    }

    protected static int skyDetailKind(String resourceName) {
        if (resourceName == null) {
            return 0;
        }
        String name = resourceName.toLowerCase(java.util.Locale.ROOT);
        if (!name.contains("botania:")) {
            return 0;
        }
        if (name.contains("planet")) {
            return 1;
        }
        if (name.contains("rainbow")) {
            return 3;
        }
        if (name.contains("skybox") || name.contains("ribbon")) {
            return 2;
        }
        return 0;
    }

    protected void setAstralConstellationColors(Object constellation) {
        java.awt.Color tierColor = astralColor(constellation, "getTierRenderColor", java.awt.Color.WHITE);
        java.awt.Color constellationColor = astralColor(constellation, "getConstellationColor", tierColor);
        setColor(currentAstralConstellationColor, constellationColor);
        setColor(currentAstralTierColor, tierColor);
    }

    protected void resetAstralConstellationColors() {
        setColor(currentAstralConstellationColor, null);
        setColor(currentAstralTierColor, null);
    }

    protected static java.awt.Color astralColor(Object constellation, String methodName, java.awt.Color fallback) {
        if (constellation != null) {
            try {
                Method method = constellation.getClass().getMethod(methodName);
                Object result = method.invoke(constellation);
                if (result instanceof java.awt.Color color) {
                    return color;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return fallback;
    }

    protected static void setColor(float[] target, java.awt.Color color) {
        if (color == null) {
            target[0] = 1.0f;
            target[1] = 1.0f;
            target[2] = 1.0f;
            return;
        }
        target[0] = color.getRed() / 255.0f;
        target[1] = color.getGreen() / 255.0f;
        target[2] = color.getBlue() / 255.0f;
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

    public void prepareHandItemRenderState() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.HAND_SOLID) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        resetIndexedBlendState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareVanillaHandRenderState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        restoreVanillaFixedFunctionTextureState(mc);
        if (isPipelineActive && worldFrameActive && activePass != null
                && (getPhase() == WorldRenderingPhase.HAND_SOLID
                || getPhase() == WorldRenderingPhase.HAND_TRANSLUCENT)) {
            bindPass(activePass);
        }
    }

    public void prepareUntexturedEmissiveWorldRenderState() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }
        TextureBinder.bindFallbackWhiteTexture();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareGuiItemRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null && !renderingGuiScreen()) {
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        if (!shaderlessBloomExtractionActive) {
            unbindShaderStorageBuffers();
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareFlatGuiBackgroundRenderState() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        GL11.glDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareGuiEntityPreviewRenderState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null && !renderingGuiScreen()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_SCISSOR_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_VIEWPORT_BIT);
        guiEntityPreviewStateDepth++;

        // Entity models use Minecraft's normal counter-clockwise winding even
        // inside GuiInventory's mirrored transform.
        GL11.glFrontFace(GL11.GL_CCW);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateCullFaceBack();
        // This basic preview state is required with and without a shader pack.
        // Use real driver calls because the world/UI transition can desync the
        // GlStateManager cache from OpenGL.
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        if (!isPipelineActive) {
            return;
        }

        bindMinecraftFramebufferForGui(mc);
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.disableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        GL11.glFrontFace(GL11.GL_CCW);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateCullFaceBack();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        probeGuiEntityState("entity-prepared");
    }

    public void finishGuiEntityPreviewRenderState() {
        if (guiEntityPreviewStateDepth <= 0) {
            return;
        }
        probeGuiEntityState("entity-return");
        guiEntityPreviewStateDepth--;
        GL11.glPopAttrib();
        if (!isPipelineActive) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (isPipelineActive && isRenderingGuiScreen()) {
            prepareGuiState();
        } else {
            restoreGuiSafeRenderState("gui-entity-preview");
        }
    }

    public void probeGuiModelState(String stage) {
        if (!isPipelineActive || guiModelStateProbeLogs >= MAX_GUI_MODEL_STATE_PROBE_LOGS) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) : null;
        if (!(screen instanceof net.minecraft.client.gui.inventory.GuiContainer)) {
            return;
        }
        guiModelStateProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMGuiModelState] call={} stage={} screen={} guiDepth={} entityDepth={} frontFace={} cullFace={} cullEnabled={} matrix={} gl={}",
                guiModelStateProbeLogs,
                stage,
                screen != null ? screen.getClass().getName() : "null",
                guiRenderDepth,
                guiEntityPreviewStateDepth,
                GL11.glGetInteger(GL11.GL_FRONT_FACE),
                GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                guiModelMatrixSummary(),
                glStateSummary()
        );
    }

    public void beginGuiItemModelProbe(ItemStack stack, net.minecraft.client.renderer.block.model.IBakedModel model) {
        String name = renderedItemDebugName(stack);
        guiItemProbeNames.push(name);
        probeGuiItemModel("item-scope", name, model);
    }

    public void endGuiItemModelProbe() {
        if (!guiItemProbeNames.isEmpty()) {
            guiItemProbeNames.pop();
        }
    }

    public void probeGuiItemModel(String stage, ItemStack stack, net.minecraft.client.renderer.block.model.IBakedModel model) {
        probeGuiItemModel(stage, renderedItemDebugName(stack), model);
    }

    protected void probeGuiItemModel(String stage, String name, net.minecraft.client.renderer.block.model.IBakedModel model) {
        if (!isPipelineActive
                || !renderingGuiScreen()
                || guiItemModelProbeLogs >= MAX_GUI_ITEM_MODEL_PROBE_LOGS
                || name == null
                || !name.startsWith("bloodmagic:")) {
            return;
        }

        int quads = 0;
        int diffuseQuads = 0;
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        if (model != null) {
            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.values()) {
                List<net.minecraft.client.renderer.block.model.BakedQuad> faceQuads = com.l.ausm.impl.util.MinecraftReflectionCompat.bakedModelQuads(model, null, face, 0L);
                quads += faceQuads.size();
                for (net.minecraft.client.renderer.block.model.BakedQuad quad : faceQuads) {
                    if (com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadApplyDiffuseLighting(quad)) {
                        diffuseQuads++;
                    }
                    formats.add(guiProbeFormatSummary(com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadFormat(quad)));
                }
            }
            List<net.minecraft.client.renderer.block.model.BakedQuad> generalQuads = com.l.ausm.impl.util.MinecraftReflectionCompat.bakedModelQuads(model, null, null, 0L);
            quads += generalQuads.size();
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : generalQuads) {
                if (com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadApplyDiffuseLighting(quad)) {
                    diffuseQuads++;
                }
                formats.add(guiProbeFormatSummary(com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadFormat(quad)));
            }
        }

        guiItemModelProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMGuiItemModel] call={} stage={} item={} model={} builtIn={} gui3d={} quads={} diffuseQuads={} formats={} lighting={} rescale={} normalize={} twoSided={} shadeModel={} frontFace={} cull={} gl={}",
                guiItemModelProbeLogs,
                stage,
                name,
                model != null ? model.getClass().getName() : "null",
                model != null && com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean(model, new String[] {"func_188618_c", "isBuiltInRenderer"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false),
                model != null && com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean(model, new String[] {"func_177555_b", "isGui3d"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false),
                quads,
                diffuseQuads,
                formats,
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL),
                GL11.glIsEnabled(GL11.GL_NORMALIZE),
                GL11.glGetBoolean(GL11.GL_LIGHT_MODEL_TWO_SIDE),
                GL11.glGetInteger(GL11.GL_SHADE_MODEL),
                GL11.glGetInteger(GL11.GL_FRONT_FACE),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                glStateSummary()
        );
    }

    public void probeGuiItemBufferDraw(BufferBuilder buffer, net.minecraft.client.renderer.vertex.VertexFormat format) {
        String name = guiItemProbeNames.peek();
        if (!isPipelineActive
                || !renderingGuiScreen()
                || guiItemModelProbeLogs >= MAX_GUI_ITEM_MODEL_PROBE_LOGS
                || name == null
                || !name.startsWith("bloodmagic:")) {
            return;
        }
        guiItemModelProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMGuiItemDraw] call={} item={} vertices={} drawMode={} format={} vertexArray={} colorArray={} normalArray={} texCoordArray={} lighting={} rescale={} frontFace={} cull={} gl={}",
                guiItemModelProbeLogs,
                name,
                com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(buffer),
                com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(buffer, -1, "field_179006_k", "drawMode"),
                guiProbeFormatSummary(format),
                GL11.glIsEnabled(GL11.GL_VERTEX_ARRAY),
                GL11.glIsEnabled(GL11.GL_COLOR_ARRAY),
                GL11.glIsEnabled(GL11.GL_NORMAL_ARRAY),
                GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY),
                GL11.glIsEnabled(GL11.GL_LIGHTING),
                GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL),
                GL11.glGetInteger(GL11.GL_FRONT_FACE),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                glStateSummary()
        );
    }

    protected static String guiProbeFormatSummary(net.minecraft.client.renderer.vertex.VertexFormat format) {
        if (format == null) {
            return "null";
        }
        return "size=" + ExtendedVertexFormats.size(format)
                + ",elements=" + com.l.ausm.impl.util.MinecraftReflectionCompat.callInt(format,
                new String[] {"func_177345_h", "getElementCount"},
                com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS,
                -1)
                + ",normal=" + ExtendedVertexFormats.hasNormal(format)
                + ",uv1=" + ExtendedVertexFormats.hasUvOffset(format, 1);
    }

    protected String guiModelMatrixSummary() {
        guiModelMatrixProbe.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, guiModelMatrixProbe);
        float m00 = guiModelMatrixProbe.get(0);
        float m01 = guiModelMatrixProbe.get(1);
        float m02 = guiModelMatrixProbe.get(2);
        float m10 = guiModelMatrixProbe.get(4);
        float m11 = guiModelMatrixProbe.get(5);
        float m12 = guiModelMatrixProbe.get(6);
        float m20 = guiModelMatrixProbe.get(8);
        float m21 = guiModelMatrixProbe.get(9);
        float m22 = guiModelMatrixProbe.get(10);
        float determinant = m00 * (m11 * m22 - m12 * m21)
                - m10 * (m01 * m22 - m02 * m21)
                + m20 * (m01 * m12 - m02 * m11);
        return "diag=" + formatProbeFloat(m00) + '/' + formatProbeFloat(m11) + '/' + formatProbeFloat(m22)
                + ",det=" + formatProbeFloat(determinant)
                + ",translation=" + formatProbeFloat(guiModelMatrixProbe.get(12)) + '/'
                + formatProbeFloat(guiModelMatrixProbe.get(13)) + '/'
                + formatProbeFloat(guiModelMatrixProbe.get(14));
    }

    public void probeGuiEntityState(String stage) {
        if (guiEntityStateProbeLogs >= MAX_GUI_ENTITY_STATE_PROBE_LOGS) {
            return;
        }
        guiEntityStateProbeLogs++;
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) : null;
        MainMod.LOGGER.info(
                "[AUSMGuiEntityState] call={} stage={} screen={} guiDepth={} entityDepth={} frontFace={} cullFace={} cullEnabled={} matrix={} gl={}",
                guiEntityStateProbeLogs,
                stage,
                screen != null ? screen.getClass().getName() : "null",
                guiRenderDepth,
                guiEntityPreviewStateDepth,
                GL11.glGetInteger(GL11.GL_FRONT_FACE),
                GL11.glGetInteger(GL11.GL_CULL_FACE_MODE),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                guiModelMatrixSummary(),
                glStateSummary()
        );
    }

    public boolean isInventoryEntityPreview(net.minecraft.entity.Entity entity, double x, double y, double z) {
        if (entity == null) {
            return false;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) : null;
        return screen != null
                && "net.minecraft.client.gui.inventory.GuiInventory".equals(screen.getClass().getName())
                && entity == com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc);
    }

    public boolean beginGuiItemStateScope() {
        if (!isPipelineActive) {
            return false;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null && !renderingGuiScreen()) {
            return false;
        }
        GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_POLYGON_BIT);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glFrontFace(GL11.GL_CW);
        return true;
    }

    public void endGuiItemStateScope() {
        GL11.glPopAttrib();
    }

    public boolean beginGuiBuiltInItemStateScope() {
        if (!isPipelineActive || !renderingGuiScreen()) {
            return false;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_POLYGON_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glFrontFace(GL11.GL_CW);
        return true;
    }

    public void endGuiBuiltInItemStateScope() {
        GL11.glPopAttrib();
    }

    public void prepareGuiItemGlintRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null && !renderingGuiScreen()) {
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareHandItemDrawState(String source) {
        prepareHandItemRenderState();
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen() || getPhase() != WorldRenderingPhase.HAND_SOLID) {
            return;
        }
        uploadCurrentRenderedItemId();
    }

    public void probeHandGbufferAfterRender() {
        // Disabled: this probe performs framebuffer readbacks and is too costly
        // for regular gameplay.
    }

    protected boolean shouldRouteRenderItemThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase != WorldRenderingPhase.HAND_SOLID
                && phase != WorldRenderingPhase.HAND_TRANSLUCENT
                && phase != WorldRenderingPhase.ARMOR_GLINT
                && phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    protected boolean shouldRouteItemGlintThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    protected boolean renderingGuiScreen() {
        return renderingGui || guiRenderDepth > 0;
    }

    public boolean isRenderingGuiScreen() {
        return renderingGuiScreen();
    }

    public boolean shouldDrawActiveProgramAsPatches() {
        return hasBoundPipelineProgram()
                && activeProgramTessellated
                && (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_tessellation_shader);
    }

    public int drawModeForActiveProgram(int drawMode) {
        if (!shouldDrawActiveProgramAsPatches()) {
            return drawMode;
        }
        if (drawMode == GL11.GL_QUADS || drawMode == GL40.GL_PATCHES) {
            setPatchVertices(4);
            return GL40.GL_PATCHES;
        }
        return drawMode;
    }

    public boolean shouldDrawFullscreenAsTriangles() {
        return hasBoundPipelineProgram() && activeProgramGeometric && !shouldDrawActiveProgramAsPatches();
    }

    protected boolean hasBoundPipelineProgram() {
        if (!isPipelineActive || renderingGuiScreen() || activePass == null) {
            return false;
        }
        PipelineProgram pipelineProgram = effectivePipelineProgram(activePass);
        ShaderProgram shaderProgram = pipelineProgram != null ? pipelineProgram.shaderProgram() : null;
        return shaderProgram != null && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == shaderProgram.getId();
    }

    protected static void setPatchVertices(int vertices) {
        if (GLContext.getCapabilities().OpenGL40) {
            GL40.glPatchParameteri(GL40.GL_PATCH_VERTICES, vertices);
        } else if (GLContext.getCapabilities().GL_ARB_tessellation_shader) {
            ARBTessellationShader.glPatchParameteri(ARBTessellationShader.GL_PATCH_VERTICES, vertices);
        }
    }

    protected RenderPass passForPhase(WorldRenderingPhase phase) {
        return renderingShadowMap ? phase.shadowPass() : phase.mainPass();
    }

    protected boolean bindPass(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        PipelineProgram bindingProgram = effectivePipelineProgram(pass);
        if (bindingProgram == null) {
            return false;
        }

        ShaderProgram program = bindingProgram.shaderProgram();
        if (program == null) {
            return false;
        }

        activeShaderKey = ShaderKey.fromRenderPass(pass);
        activeProgramTessellated = program.isTessellated();
        activeProgramGeometric = program.isGeometric();
        applyAlphaTest(pass);
        List<Attachment> drawBuffers = effectiveDrawBuffersForCurrentPhase(bindingProgram);
        applyBlendMode(pass, drawBuffers);
        applyOitDepthState(pass);
        applyGbufferDepthState(pass);
        applySkyDepthState(pass);
        applyHandRenderState(pass);
        applyBeaconBeamDepthState(pass);
        applyBlockEntityTranslucentDepthState(pass);
        configureGbufferDrawBuffers(bindingProgram, drawBuffers);
        configureShadowDrawBuffers(bindingProgram, drawBuffers);
        if (bindingProgram.stage() == ProgramStage.GBUFFERS) {
            restoreVanillaWorldTextureBindings();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
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
        if (bindingProgram.stage().readsDeferredTextures()) {
            TextureBinder.bindDeferredTextures();
        } else {
            TextureBinder.bindNoiseTexture();
        }
        if (bindingProgram.stage() != ProgramStage.SHADOW) {
            TextureBinder.bindShadowTextures(bindingProgram.pass());
        }
        if (bindingProgram.stage() == ProgramStage.GBUFFERS) {
            TextureBinder.bindMaterialFallbackTextures();
        }

        program.bind();
        bindProgramResources(bindingProgram.pass(), program);
        if (bindingProgram.stage() == ProgramStage.SHADOW && getPhase().usesBlockAtlas()) {
            bindBlockAtlas();
        }
        activePass = pass;
        if (pass == RenderPass.GBUFFERS_WATER) {
            logWaterRoutingProbe("after-bind", bindingProgram, drawBuffers);
        }
        return true;
    }

    protected PipelineProgram effectivePipelineProgram(RenderPass pass) {
        RenderPass current = pass;
        while (current != null) {
            PipelineProgram program = programs.get(current);
            if (program != null && program.enabled() && program.shaderProgram() != null) {
                return program;
            }
            current = current.fallback();
        }
        return null;
    }

    protected void bindProgramResources(RenderPass pass, ShaderProgram program) {
        customTextures.bind(pass, program);
        shaderImages.bind(program);
        shaderStorageBuffers.bind();
        if (shaderStorageBuffers.active()) {
            markShaderStorageBuffersBound();
        }
        uniformRegistry.uploadAll(program);
        if (!packDirectives.customUniforms().isEmpty()) {
            packDirectives.customUniforms().upload(program, uniformRegistry.scalarValuesInto(customUniformScalarScratch));
        }
    }

    protected List<Attachment> effectiveDrawBuffersForCurrentPhase(PipelineProgram pipelineProgram) {
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

    protected boolean usesBlockAtlas(RenderPass pass) {
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesBlockAtlas();
        }
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_WATER
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.DH_TERRAIN
                || pass == RenderPass.DH_WATER;
    }

    protected void bindBlockAtlas() {
        TextureBinder.restoreDefaultTextureUnit();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        TextureManager textureManager = com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null) {
            return;
        }
        ITextureObject texture = com.l.ausm.impl.util.MinecraftReflectionCompat.texture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
        if (texture == null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
            texture = com.l.ausm.impl.util.MinecraftReflectionCompat.texture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
        }
        if (texture != null) {
            int textureId = com.l.ausm.impl.util.MinecraftReflectionCompat.glTextureId(texture);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
        }
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
    }

    protected void applyAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderAlphaTest alphaTest = pipelineProgram == null ? null : pipelineProgram.directives().alphaTestOverride();
        if (alphaTest == null) {
            alphaTest = defaultAlphaTest(pass);
        }

        currentAlphaTestReference = alphaTest.reference();
        if (alphaTest.function() == GL11.GL_ALWAYS) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());
    }

    protected static ShaderAlphaTest defaultAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = INSTANCE.programs.get(pass);
        ShaderKey key = pipelineProgram == null ? ShaderKey.fromRenderPass(pass) : pipelineProgram.shaderKey();
        return key == null ? ShaderAlphaTest.ALWAYS : key.alphaTest();
    }

    public void applyNonZeroAlphaTestForCurrentPass() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }

        ShaderAlphaTest alphaTest = ShaderAlphaTest.NON_ZERO_ALPHA;
        currentAlphaTestReference = alphaTest.reference();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());

        ShaderProgram program = activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "alphaTestRef");
            uniformRegistry.upload(program, "iris_currentAlphaTest");
        }
    }

    protected void applyBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        if (applyOitBlendMode(pass, drawBuffers)) {
            return;
        }

        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderBlendMode blendMode = pipelineProgram == null ? null : pipelineProgram.directives().blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = attachmentBlendModesFor(pass);
        if (pass == RenderPass.GBUFFERS_WATER || pass == RenderPass.DH_WATER) {
            applyWaterBlendMode(drawBuffers, blendMode == null ? WATER_BLEND_MODE : blendMode, attachmentModes);
            return;
        }
        if (pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT) {
            applyWaterBlendMode(drawBuffers, blendMode == null ? BLOCK_ENTITY_TRANSLUCENT_BLEND : blendMode, attachmentModes);
            return;
        }
        if (blendMode == null) {
            blendMode = defaultBlendMode(pass);
        }
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }

        if (blendMode != null && !blendMode.enabled()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        if (blendMode != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
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

    protected void applyWaterBlendMode(List<Attachment> drawBuffers, ShaderBlendMode blendMode, Map<Attachment, ShaderBlendMode> attachmentModes) {
        if (!blendMode.enabled()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                blendMode.srcRgb(),
                blendMode.dstRgb(),
                blendMode.srcAlpha(),
                blendMode.dstAlpha()
        );
        resetIndexedBlendState();

        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            ShaderBlendMode attachmentMode = attachmentModes.get(attachment);
            if (attachmentMode != null) {
                applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            } else if (defaultWaterBlendTarget(attachment)) {
                applyIndexedBlendMode(drawBufferIndex, blendMode);
            }
        }
    }

    protected static boolean defaultWaterBlendTarget(Attachment attachment) {
        return PipelineRenderPassRules.defaultWaterBlendTarget(attachment);
    }

    protected static ShaderBlendMode defaultBlendMode(RenderPass pass) {
        return PipelineRenderPassRules.defaultBlendMode(pass);
    }

    protected Map<Attachment, ShaderBlendMode> attachmentBlendModesFor(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        Map<Attachment, ShaderBlendMode> attachmentModes = pipelineProgram == null ? null : pipelineProgram.directives().attachmentBlendModes();
        return attachmentModes == null ? Map.of() : attachmentModes;
    }

    protected boolean applyOitBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        if (!isOitGbufferPass(pass) || drawBuffers.isEmpty()) {
            return false;
        }

        ShaderOitSettings oitSettings = shaderProperties.oitSettings();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            if (oitSettings.coefficientBuffer(attachment)) {
                applyIndexedBlendMode(drawBufferIndex, OIT_COEFFICIENT_BLEND);
            } else {
                setIndexedBlend(drawBufferIndex, false);
            }
        }
        return true;
    }

    protected void applyHandRenderState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_HAND && pass != RenderPass.GBUFFERS_HAND_WATER) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        resetIndexedBlendState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void applyOitDepthState(RenderPass pass) {
        if (!isOitGbufferPass(pass)) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
    }

    protected void applyGbufferDepthState(RenderPass pass) {
        if (!isOpaqueTerrainPass(pass) && pass != RenderPass.GBUFFERS_WATER && pass != RenderPass.DH_WATER) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glColorMask(true, true, true, true);
    }

    protected void applySkyDepthState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_SKYBASIC && pass != RenderPass.GBUFFERS_SKYTEXTURED) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
    }

    protected void applyBeaconBeamDepthState(RenderPass pass) {
        if (shaderProperties.renderSettings().beaconBeamDepth()) {
            return;
        }
        if (pass != RenderPass.GBUFFERS_BEACONBEAM && getPhase() != WorldRenderingPhase.BEACON_BEAM) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
    }

    protected void applyBlockEntityTranslucentDepthState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_BLOCK_TRANSLUCENT || isOitGbufferPass(pass)) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glColorMask(true, true, true, true);
    }

    protected static boolean isOpaqueTerrainPass(RenderPass pass) {
        return PipelineRenderPassRules.isOpaqueTerrainPass(pass);
    }

    protected boolean isOitGbufferPass(RenderPass pass) {
        if (pass == null || pass.stage() != ProgramStage.GBUFFERS || !shaderProperties.oitSettings().activeForGbuffers()) {
            return false;
        }

        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return isOitPhase(phase);
        }
        return pass == RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_PARTICLES_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WEATHER
                || pass == RenderPass.GBUFFERS_CLOUDS
                || pass == RenderPass.GBUFFERS_LIGHTNING
                || pass == RenderPass.GBUFFERS_BEACONBEAM;
    }

    protected static boolean isOitPhase(WorldRenderingPhase phase) {
        return PipelineRenderPassRules.isOitPhase(phase);
    }

    protected void applyIndexedBlendMode(int drawBufferIndex, ShaderBlendMode blendMode) {
        if (drawBufferIndex < 0 || drawBufferIndex >= maxDrawBuffers()) {
            return;
        }
        if (!blendMode.enabled()) {
            setIndexedBlend(drawBufferIndex, false);
            return;
        }

        setIndexedBlend(drawBufferIndex, true);
        if (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_draw_buffers_blend) {
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(
                    drawBufferIndex,
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
    }

    protected void configureGbufferDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (!pingPongManager.isInitialized() || pipelineProgram.stage() != ProgramStage.GBUFFERS) {
            return;
        }

        if (!drawBuffers.isEmpty()) {
            pingPongManager.bindForGbuffers(drawBuffers.toArray(new Attachment[0]));
        }
    }

    public void restoreActiveGbufferRenderState() {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || activePass == null) {
            return;
        }
        PipelineProgram pipelineProgram = programs.get(activePass);
        if (pipelineProgram == null || pipelineProgram.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        List<Attachment> drawBuffers = effectiveDrawBuffersForCurrentPhase(pipelineProgram);
        boolean valid = !drawBuffers.isEmpty();
        for (int slot = 0; valid && slot < drawBuffers.size(); slot++) {
            valid = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot) == GL30.GL_COLOR_ATTACHMENT0 + slot;
        }
        if (!valid) {
            pingPongManager.forceGbufferDrawBuffers(drawBuffers.toArray(new Attachment[0]));
        }
        applyAlphaTest(activePass);
        applyBlendMode(activePass, drawBuffers);
        applyOitDepthState(activePass);
        applyGbufferDepthState(activePass);
    }

    /**
     * Celeritas binds its native chunk shader in begin() before configuring
     * the batch vertex state. Restore AUSM after that setup point so the batch
     * draws into the shaderpack MRTs without rebinding once per chunk.
     */
    public void rebindActivePipelinePassAfterRendererSetup() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        bindPass(activePass);
    }

    protected void logWaterRoutingProbe(String stage, PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || waterRoutingProbeLogs >= MAX_WATER_ROUTING_PROBE_LOGS) {
            return;
        }
        waterRoutingProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        StringBuilder slots = new StringBuilder();
        int slotCount = Math.min(8, Math.max(1, drawBuffers != null ? drawBuffers.size() : 0));
        for (int slot = 0; slot < slotCount; slot++) {
            if (slot > 0) {
                slots.append(';');
            }
            int drawBuffer = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot);
            int texture = 0;
            try {
                texture = GL30.glGetFramebufferAttachmentParameteri(
                        GL30.GL_DRAW_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0 + slot,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                );
            } catch (RuntimeException | LinkageError ignored) {
            }
            slots.append(slot).append("=draw:").append(drawBuffer).append(",tex:").append(texture);
        }
        int depthTexture = 0;
        try {
            depthTexture = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
        MainMod.LOGGER.info(
                "[AUSMWaterRouting] call={} stage={} program={} declared={} effective={} fbo={} slots={} depthAttachment={} textures={} colors={} depth0={} depth1={} gl={}",
                waterRoutingProbeLogs,
                stage,
                pipelineProgram != null ? describePipelineProgram(pipelineProgram) : "null",
                pipelineProgram != null ? pipelineProgram.drawBuffers() : "none",
                drawBuffers,
                framebuffer != null ? framebuffer.getFramebufferId() : -1,
                slots,
                depthTexture,
                deferredBoundaryTextureSummary(framebuffer),
                deferredBoundaryColorSummary(framebuffer),
                deferredDepthSampleSummary(framebuffer, -1),
                deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                glStateSummary()
        );
    }

    public void logSpecialLayerProbe(String stage) {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || specialLayerProbeLogs >= MAX_SPECIAL_LAYER_PROBE_LOGS) {
            return;
        }
        specialLayerProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        StringBuilder drawBuffers = new StringBuilder();
        for (int slot = 0; slot < 5; slot++) {
            if (slot > 0) {
                drawBuffers.append(';');
            }
            drawBuffers.append(slot).append('=').append(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot));
        }
        MainMod.LOGGER.info(
                "[AUSMSpecialLayerProbe] call={} stage={} activePass={} phase={} worldFrame={} skip={} drawFbo={} drawBuffers={} program={} color={} composite={} normal={} depth={} gl={}",
                specialLayerProbeLogs,
                stage,
                activePass,
                getPhase(),
                worldFrameActive,
                shouldSkipAllMainGbufferRendering(),
                drawFramebuffer,
                drawBuffers,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                deferredFramebufferColorSamples(framebuffer, Attachment.COLOR),
                deferredFramebufferColorSamples(framebuffer, Attachment.COMPOSITE),
                deferredFramebufferColorSamples(framebuffer, Attachment.NORMAL),
                framebuffer != null && framebuffer.isUsable()
                        ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                        : "none",
                glStateSummary());
    }

    protected void configureShadowDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (shadowFramebuffer == null || pipelineProgram.stage() != ProgramStage.SHADOW) {
            return;
        }
        shadowFramebuffer.bindForProgramWrite(drawBuffers.toArray(new Attachment[0]));
    }

    public void endPass() {
        if (!isPipelineActive || passStack.isEmpty()) {
            return;
        }

        PassScope scope = passStack.pop();
        activePhase = scope.previousPhase();
        if (!scope.bound()) {
            activeShaderKey = scope.previousShaderKey();
            activeProgramTessellated = scope.previousProgramTessellated();
            activeProgramGeometric = scope.previousProgramGeometric();
            return;
        }

        logLayerOutputProbe(activePass, activePhase);
        PipelineProgram pipelineProgram = programs.get(activePass);
        ShaderProgram program = pipelineProgram != null ? pipelineProgram.effectiveProgram(programs) : null;
        if (program != null) {
            program.unbind();
        }

        if (isOitGbufferPass(activePass)) {
            resetOitRenderState();
        }

        activePass = null;
        activeShaderKey = null;
        activeProgramTessellated = false;
        activeProgramGeometric = false;
        if (scope.previousPass() != null) {
            bindPass(scope.previousPass());
        } else {
            activeShaderKey = scope.previousShaderKey();
            activeProgramTessellated = scope.previousProgramTessellated();
            activeProgramGeometric = scope.previousProgramGeometric();
        }
    }

    protected void logLayerOutputProbe(RenderPass pass, WorldRenderingPhase phase) {
        if (pass == null
                || layerOutputProbeLogs >= MAX_LAYER_OUTPUT_PROBE_LOGS
                || !(pass.stage() == ProgramStage.GBUFFERS
                || pass.stage() == ProgramStage.SHADOW)) {
            return;
        }
        layerOutputProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        String color = deferredFramebufferColorSamples(framebuffer, Attachment.COLOR);
        String composite = deferredFramebufferColorSamples(framebuffer, Attachment.COMPOSITE);
        String normal = deferredFramebufferColorSamples(framebuffer, Attachment.NORMAL);
        String depth = framebuffer != null && framebuffer.isUsable()
                ? deferredDepthSampleSummary(framebuffer, -1)
                : "none";
        int glError = GL11.glGetError();
        MainMod.LOGGER.info(
                "[AUSMLayerOutputProbe] call={} pass={} phase={} color={} composite={} normal={} depth={} fbo={} glProgram={} glError={}",
                layerOutputProbeLogs,
                pass,
                phase,
                color,
                composite,
                normal,
                depth,
                framebuffer != null ? framebuffer.getFramebufferId() : -1,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                glError
        );
    }

    public void resize(int width, int height) {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        resizeFramebuffer(width, height, true);
    }

    protected abstract void activateSoftVanillaTerrainRenderer(String reason);

    protected abstract ShaderProgram activeProgram();

    protected abstract float cameraHorizontalVelocityMagnitude();

    protected abstract float cameraVerticalDelta();

    protected abstract void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms);

    protected abstract void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms,
                                                boolean deleteVanillaTerrainRenderers);

    protected abstract void clearColoredLightImages();

    protected abstract void clearNothiriumPipelineTranslucentBridge();

    protected abstract String deferredBoundaryColorSummary(DeferredFramebuffer framebuffer);

    protected abstract String deferredBoundaryTextureSummary(DeferredFramebuffer framebuffer);

    protected abstract String deferredDepthSampleSummary(DeferredFramebuffer framebuffer, int snapshotIndex);

    protected abstract String describeFramebufferTarget(Framebuffer framebuffer);

    protected abstract String describeFramebufferTargetDetailed(Framebuffer framebuffer);

    protected abstract String describePipelineProgram(PipelineProgram program);

    protected abstract void initializeNoiseTexture(ShaderPack pack, ShaderProperties properties);

    protected abstract void markNothiriumPipelineTranslucentBridge(BlockRenderLayer layer);

    protected abstract void prepareGuiState();

    protected abstract void recordTerrainLayerCount(BlockRenderLayer layer, int count);

    protected abstract World renderChunkWorld(RenderChunk chunk);

    protected abstract void resizeFramebuffer(int width, int height, boolean preservePersistentAttachments);

    protected abstract void restoreGuiSafeRenderState(String source);

    protected abstract void restoreVanillaFixedFunctionTextureState(Minecraft mc);

    protected abstract void restoreVanillaWorldTextureBindings();

    public abstract boolean isCustomVoidWorldSkyEnabled(World world);

    protected abstract boolean isFiniteColor(float[] color);

    protected abstract boolean isOverworldShaderEnvironment(World world);

    public abstract boolean isRenderingBetterPortalsNestedView();

    public abstract boolean isRenderingBetterPortalsRenderPass();

    public abstract boolean shouldBypassWorldPassRendering();

    protected abstract boolean isSimpleVoidWorld(World world);

    protected abstract void rebuildTerrainRenderers(boolean recreateNothiriumRenderer, boolean reloadVanillaRenderGlobal);

    protected abstract void renderShaderlessBotaniaVoidDetails(float partialTicks, WorldClient world, Minecraft mc);

    protected abstract void resetShadowRenderCache();

    protected abstract int safeDimensionId(World world);

    protected abstract void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay);

    protected abstract boolean shouldUseNothiriumMainTerrainBridge();

    protected abstract boolean shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer layer);

    public abstract boolean shouldUseOwnedSkyOverrideWorld(World world);

    protected abstract boolean shouldUseShaderedF1LowerSkyRepair(Minecraft mc, World world);

    protected abstract boolean updateNothiriumPipelineBlockFormatMode();

    protected abstract void unbindShaderStorageBuffers();

    protected static int localActVoxelId(int materialId) {
        if (materialId == 12003 || materialId == 12283) {
            return 3;
        }
        if (materialId == 10900 || materialId == 12024) {
            return 24;
        }
        if (materialId >= 10902 && materialId <= 10922 && (materialId & 1) == 0) {
            return 69 + (materialId - 10900) / 2;
        }
        if (materialId >= 12070 && materialId <= 12080) {
            return materialId - 12000;
        }
        if (materialId >= 12270 && materialId <= 12280) {
            return materialId - 12160;
        }
        return 0;
    }

    protected static int compatSyntheticLightVoxelId(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return 0;
        }
        if ("tconstruct".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "seared_furnace_controller".equals(MinecraftReflectionCompat.resourcePath(name))
                && stateName(state).contains("active=true")) {
            return 71;
        }
        if ("aether_legacy".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "aether_portal".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return localActVoxelId(10914);
        }
        int astralVoxel = astralCrystalVoxelId(state);
        return astralVoxel > 0 ? astralVoxel : 0;
    }

    protected static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    protected static int eyeFluidState(Minecraft mc) {
        if (mc == null) {
            return 0;
        }
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return 0;
        }
        IBlockState cameraState = MinecraftReflectionCompat.blockStateAtEntityViewpoint(
                world, viewEntity, MinecraftReflectionCompat.renderPartialTicks(mc));
        if (MinecraftReflectionCompat.stateMaterialIsWater(cameraState)) {
            return 1;
        }
        if (MinecraftReflectionCompat.stateMaterial(cameraState)
                == MinecraftReflectionCompat.field(net.minecraft.block.material.Material.class,
                net.minecraft.block.material.Material.class, null, "field_151587_i", "LAVA")
                && !MinecraftReflectionCompat.playerIsSpectator(MinecraftReflectionCompat.player(mc))) {
            return 2;
        }
        return 0;
    }

    protected static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    protected static String glStateSummary() {
        return FixedFunctionGlState.summary();
    }

    protected static String formatProbeFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    protected static ShaderProperties emptyShaderProperties() {
        return PipelineContext.emptyShaderProperties();
    }

}
