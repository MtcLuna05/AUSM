package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.dh.DistantHorizonsMatrixState;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.fbo.PingPongManager;
import com.luna.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.luna.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.render.IrisLightmapTexture;
import com.luna.ausm.impl.pipeline.resource.ShaderImageSet;
import com.luna.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.luna.ausm.impl.pipeline.shader.ComputeProgram;
import com.luna.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.luna.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderKey;
import com.luna.ausm.impl.pipeline.shader.ShaderMap;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderProgramSet;
import com.luna.ausm.impl.pipeline.shader.UniformRegistry;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_CURRENT_PROBLEM_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.COMPILED_PIPELINE_CACHE_LIMIT;

abstract class PipelineRuntimeStateBase {
    protected static final PipelineContext INSTANCE = new PipelineContext();

    protected static final ICamera ALWAYS_VISIBLE_CAMERA = PipelineRuntimeState.createAlwaysVisibleCamera();

    protected static final FloatBuffer IRIS_LIGHTMAP_TEXTURE_MATRIX = PipelineFrameValues.createIrisLightmapTextureMatrix();

    protected static final boolean FRAMED_BLOCK_DIAGNOSTICS_ENABLED =
            MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS > 0
                    || MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS > 0
                    || MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS > 0;

    protected static final boolean CURRENT_PROBLEM_PROBES_ENABLED = MAX_CURRENT_PROBLEM_PROBE_LOGS > 0;

    protected static boolean celeritasShadowCameraWarningLogged;

    protected volatile boolean shaderlessVoidWorldSkyLightEligible;

    protected final PingPongManager pingPongManager = new PingPongManager();

    protected final IrisLightmapTexture irisLightmapTexture = new IrisLightmapTexture();

    protected final Map<RenderPass, PipelineProgram> programs = new EnumMap<>(RenderPass.class);

    protected final PipelineCustomTextures customTextures = new PipelineCustomTextures();

    protected final UniformRegistry uniformRegistry = new UniformRegistry();

    protected final Map<String, float[]> customUniformScalarScratch = new HashMap<>();

    protected ShadowFramebuffer shadowFramebuffer;

    protected ShaderProperties shaderProperties = PipelineRuntimeState.emptyShaderProperties();

    protected ShaderPackDirectives packDirectives = PipelineRuntimeState.emptyShaderProperties().packDirectives();

    protected ShaderProgramSet programSet;

    protected ShaderMap shaderMap;

    protected ShaderImageSet shaderImages = ShaderImageSet.empty();

    protected ShaderStorageBufferSet shaderStorageBuffers = ShaderStorageBufferSet.empty();

    protected final ConcurrentMap<Long, BlockPos> syntheticLightCandidates = new ConcurrentHashMap<>();

    protected final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays = new EnumMap<>(ProgramArrayId.class);

    protected List<ComputeProgram> shadowComputePrograms = List.of();

    protected List<ComputeProgram> finalComputePrograms = List.of();

    protected final Map<ProgramArrayId, FullscreenProgramArray> fullscreenProgramArrays = new EnumMap<>(ProgramArrayId.class);

    protected final Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms = new EnumMap<>(ProgramArrayId.class);

    protected final Map<RenderGlobal, Map<World, ViewFrustum>> vanillaViewFrustums = new IdentityHashMap<>();

    protected final Map<RenderGlobal, Map<World, Integer>> vanillaViewFrustumRenderDistances = new IdentityHashMap<>();

    protected final Map<ViewFrustum, Long> vanillaViewFrustumChunkPositionKeys = new IdentityHashMap<>();

    protected final Deque<Object[]> vanillaViewFrustumStateStack = new ArrayDeque<>();

    protected final Map<String, PipelineRuntimeState.CompiledPipelineState> compiledPipelineCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PipelineRuntimeState.CompiledPipelineState> eldest) {
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

    protected final Deque<PipelineRuntimeState.PassScope> passStack = new ArrayDeque<>();

    protected final Deque<Integer> renderedItemIdStack = new ArrayDeque<>();

    protected final Deque<Integer> dynamicBlockEntityIdStack = new ArrayDeque<>();

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

    /**
     * True only while native bloom replays translucent chunk meshes into its transmission mask.
     */
    protected boolean bloomTranslucentAttenuationPass = false;

    protected int vanillaParticleRecoveryFrames = 0;

    protected String activePackName = "(internal)";

    protected float centerDepth = 1.0f;

    protected float centerDepthSmooth = 1.0f;

    protected int centerDepthSmoothTexture = -1;

    protected int noiseTexture = -1;

    protected final FloatBuffer centerDepthTextureBuffer = BufferUtils.createFloatBuffer(1);

    protected final FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(16);

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

    protected final ByteBuffer terrainProbeBooleanBuffer = BufferUtils.createByteBuffer(16);

    protected int currentEntityId = 0;

    protected int currentRenderedItemId = -1;

    /**
     * Per-draw material marker for animated TESR geometry that is not a physical block model.
     */
    protected int dynamicBlockEntityId = -1;

    protected int itemGlintMaskDepth = 0;

    protected String currentRenderedItemDebugName = "";

    protected static final float[] NO_ENTITY_COLOR = new float[]{0.0f, 0.0f, 0.0f, 0.0f};

    protected ResourceLocation currentEntityKey = null;

    protected float[] currentEntityColor = NO_ENTITY_COLOR;

    protected final float[] currentAstralConstellationColor = new float[]{1.0f, 1.0f, 1.0f};

    protected final float[] currentAstralTierColor = new float[]{1.0f, 1.0f, 1.0f};

    protected float currentAstralSolarEclipseFactor;

    /**
     * True only while Astral's legacy RenderWorldLast sprites are on the entity MRT pass.
     */
    protected boolean astralEffectOverlayActive;

    /**
     * Shader-visible kind of the currently submitted modded sky detail.
     */
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

    protected PipelineRuntimeState self() {
        return (PipelineRuntimeState) this;
    }
}
