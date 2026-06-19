package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.client.ShaderLoadingScreen;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.l.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.fbo.PingPongManager;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.mixin.pipeline.RenderChunkAccessor;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderItemIdMap;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.pack.ShaderFeatureValidator;
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
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.ListChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.VboChunkFactory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The central hub for the active render pipeline.
 * Replaces the monolithic Shaders class with a cleaner context object.
 */
public class PipelineContext {

    private static final PipelineContext INSTANCE = new PipelineContext();
    private static final FloatBuffer IRIS_LIGHTMAP_TEXTURE_MATRIX = createIrisLightmapTextureMatrix();
    private static final Pattern CONST_SETTING_PATTERN = Pattern.compile("^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+).*$");
    private static final Pattern DEFINE_SETTING_PATTERN = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+([^/\\s]+))?.*$");
    private static final boolean ENABLE_CPU_LIGHT_INJECTION = true;
    private static final int MAX_SYNTHETIC_LIGHT_CANDIDATES = 2048;
    private static final int MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME = 4096;
    private static final int MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME = 128;
    private static final int MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME = 512;
    private static final int CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES = 20;
    private static final int MAX_COLORED_LIGHT_AUDIT_LOGS = 0;
    private static final int BIOME_NETHER_WASTES_ID = 100_000;
    private static final int BIOME_CRIMSON_FOREST_ID = 100_001;
    private static final int BIOME_WARPED_FOREST_ID = 100_002;
    private static final int BIOME_BASALT_DELTAS_ID = 100_003;
    private static final int BIOME_SOUL_SAND_VALLEY_ID = 100_004;
    private static final int BIOME_PALE_GARDEN_ID = 100_005;
    private static final int FORCE_LIGHT_RECALC_MIN_RADIUS = 16;
    private static final int FORCE_LIGHT_RECALC_MAX_RADIUS = 32;
    private static final int WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS = 12;
    private static final int WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES = 10;
    private static final int WORLD_LOAD_LIGHT_REFRESH_RADIUS = 16;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS = 1;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES = 2;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES = 8;
    private static final int MAX_PENDING_SHADER_CHUNK_REFRESHES = 2048;
    private static final int MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES = 4096;
    private static final int MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME = 24;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS = 8;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES = 1;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES = 4;
    private static final int MAX_CHUNK_FADE_STATES = 8192;
    private static final int CHUNK_FADE_STALE_FRAMES = 600;
    private static final int CHUNK_FADE_WARMUP_FRAMES = 20;
    private static final float CHUNK_FADE_DURATION_SECONDS = 0.45f;
    private static final int MAX_SHADER_CHUNK_REFRESHES_PER_FRAME = 8;
    private static final int COMPILED_PIPELINE_CACHE_LIMIT = 3;
    private static final int MAX_BETTER_PORTALS_PIPELINE_LOGS = 0;
    private static final int MAX_SHADERLESS_BLOOM_HOOK_LOGS = 0;
    private static final int MAX_VISIBLE_BLOOM_DIAG_LOGS = 0;
    private static final int MAX_WORLD_LAYER_DIAG_LOGS = 0;
    private static final int MAX_EXTERNAL_OVERLAY_LOGS = 0;
    private static final int MAX_TEMPORAL_HISTORY_RESET_LOGS = 8;
    private static final int MAX_TERRAIN_HISTORY_CLEAR_LOGS = 8;
    private static final int MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS = 0;
    private static final int MAX_TERRAIN_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_CAMERA_FRUSTUM_SYNC_LOGS = 0;
    private static final int MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS = 0;
    private static final int MAX_DECORATED_LIGHT_AUDIT_LOGS = 0;
    private static final int MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_CURRENT_PROBLEM_PROBE_LOGS = 0;
    private static final boolean ENABLE_CHUNK_FADE = false;
    private static final long WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS = 750L;
    private static final long BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS = 1000L;
    private static final String RANDOM_THINGS_LUMINOUS_BLOCK_CLASS = "lumien.randomthings.lib.ILuminousBlock";
    private static final String BLOCKCRAFTERY_TILE_EDITABLE_BLOCK_CLASS = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String ARCHITECTURECRAFT_TILE_SHAPE_CLASS = "com.elytradev.architecture.common.tile.TileShape";
    private static final String ARCHITECTURECRAFT_BLOCK_PACKAGE = "com.elytradev.architecture.common.block.";
    private static final int RANDOM_THINGS_TRANSLUCENT_LUMINOUS_ALPHA = 160;
    private static final float TEMPORAL_HISTORY_CAMERA_DELTA_RESET = 0.85f;
    private static final float TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET = 35.0f;
    private static final float TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET = 25.0f;
    private static final ShaderBlendMode OIT_COEFFICIENT_BLEND = new ShaderBlendMode(true, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
    private static final ShaderBlendMode WATER_BLEND_MODE = new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    private static final float PORTAL_NETHER_FOG_DENSITY = 0.08f;
    private static final float[] PORTAL_NETHER_FOG_COLOR = {0.20f, 0.03f, 0.03f};
    private static final float NETHER_SHADER_FOG_COLOR_SCALE = 0.25f;
    private static final float SHADER_OVERWORLD_FOG_START_RATIO = 0.85f;
    private static int maxDrawBuffers = -1;
    private static boolean celeritasShadowCameraWarningLogged;

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
    private final ConcurrentMap<Long, BlockPos> syntheticLightCandidates = new ConcurrentHashMap<>();
    private final Set<String> coloredLightAuditKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger coloredLightAuditCount = new AtomicInteger();
    private final Map<ProgramArrayId, List<ComputeProgram>> computeProgramArrays = new EnumMap<>(ProgramArrayId.class);
    private List<ComputeProgram> shadowComputePrograms = List.of();
    private List<ComputeProgram> finalComputePrograms = List.of();
    private final Map<ProgramArrayId, FullscreenProgramArray> fullscreenProgramArrays = new EnumMap<>(ProgramArrayId.class);
    private final Map<ProgramArrayId, List<FullscreenArrayProgram>> fullscreenArrayPrograms = new EnumMap<>(ProgramArrayId.class);
    private final Map<RenderGlobal, Map<World, ViewFrustum>> vanillaViewFrustums = new IdentityHashMap<>();
    private final Map<RenderGlobal, Map<World, Integer>> vanillaViewFrustumRenderDistances = new IdentityHashMap<>();
    private final Deque<Object[]> vanillaViewFrustumStateStack = new ArrayDeque<>();
    private final Map<String, CompiledPipelineState> compiledPipelineCache = new LinkedHashMap<>() {
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
    private final NothiriumShadowRenderer nothiriumShadowRenderer = new NothiriumShadowRenderer();
    private final AusmBloomRenderer bloomRenderer = new AusmBloomRenderer();
    private final Set<ShaderChunkRefresh> pendingShaderChunkRefreshes = new LinkedHashSet<>();
    private final Set<ClientChunkRenderRefresh> pendingClientChunkRenderRefreshes = new LinkedHashSet<>();
    private int pendingWorldLoadLightRecalculationAttempts = 0;
    private int pendingWorldLoadLightRecalculationDelay = 0;
    private int pendingWorldTerrainRefreshAttempts = 0;
    private int pendingWorldTerrainRefreshDelay = 0;
    private int pendingWorldTerrainRefreshDimension = Integer.MIN_VALUE;
    private boolean pendingWorldTerrainRendererReset = false;
    private boolean pendingWorldTerrainFullRendererReset = false;
    private boolean pendingWorldTerrainVanillaReload = false;
    private String activeCompiledPipelineCacheKey;
    private boolean randomThingsLuminousBlockResolved;
    private Class<?> randomThingsLuminousBlockClass;
    private Method randomThingsShouldGlowMethod;
    private boolean blockcrafteryTileResolved;
    private Class<?> blockcrafteryTileClass;
    private Field blockcrafteryTileStateField;
    private final ConcurrentMap<Class<?>, Method> blockcrafteryStatePropertyMethods = new ConcurrentHashMap<>();
    private final Set<Class<?>> blockcrafteryMissingStatePropertyMethods = ConcurrentHashMap.newKeySet();
    private boolean architectureCraftTileResolved;
    private Class<?> architectureCraftTileClass;
    private Method architectureCraftGetTileMethod;
    private Method architectureCraftBaseStateMethod;
    private Method architectureCraftSecondaryStateMethod;

    private final Deque<PassScope> passStack = new ArrayDeque<>();
    private final Deque<Boolean> worldPassBypassStack = new ArrayDeque<>();
    private final Deque<Long> worldPassSerialStack = new ArrayDeque<>();
    private final Deque<Long> nothiriumPipelineTranslucentFrameStack = new ArrayDeque<>();
    private final Deque<Long> nothiriumPipelineTranslucentWorldPassSerialStack = new ArrayDeque<>();
    private final Deque<Boolean> untouchedBetterPortalsVanillaRendererStack = new ArrayDeque<>();
    private RenderPass activePass = null;
    private ShaderKey activeShaderKey = null;
    private WorldRenderingPhase activePhase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase = null;
    private volatile boolean isPipelineActive = false;
    private boolean shaderlessWorldPassActive = false;
    private String activePackName = "(internal)";
    private float centerDepth = 1.0f;
    private float centerDepthSmooth = 1.0f;
    private int centerDepthSmoothTexture = -1;
    private int noiseTexture = -1;
    private final FloatBuffer centerDepthTextureBuffer = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private final FloatBuffer fogColorBuffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
    private int currentEntityId = 0;
    private ResourceLocation currentEntityKey = null;
    private float[] currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] currentAstralConstellationColor = new float[]{1.0f, 1.0f, 1.0f};
    private final float[] currentAstralTierColor = new float[]{1.0f, 1.0f, 1.0f};
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
    private World cpuLightTileEntitySnapshotWorld = null;
    private long cpuLightTileEntitySnapshotFrame = Long.MIN_VALUE;
    private List<TileEntity> cpuLightTileEntitySnapshot = java.util.Collections.emptyList();
    private int cpuLightTileEntityScanCursor = 0;
    private final long pipelineStartNanos = System.nanoTime();
    private long lastPipelineFrameNanos = pipelineStartNanos;
    private float currentFrameTime = 0.016f;
    private float currentChunkFade = 1.0f;
    private long chunkFadeWarmupUntilFrame = 0L;
    private final Map<ChunkFadeKey, ChunkFadeState> chunkFadeStates = new LinkedHashMap<>();
    private float frameTimeCounter = 0.0f;
    private float frameTimeSmooth = 0.016f;
    private boolean frameTimeSmoothInitialized = false;
    private final float[] cameraPosition = {0.0f, 0.0f, 0.0f};
    private final float[] previousCameraPosition = {0.0f, 0.0f, 0.0f};
    private final double[] cameraPositionUnshifted = {0.0, 0.0, 0.0};
    private final double[] previousCameraPositionUnshifted = {0.0, 0.0, 0.0};
    private double cameraShiftX = 0.0;
    private double cameraShiftZ = 0.0;
    private boolean temporalHistoryInitialized = false;
    private int temporalHistoryDimensionId = Integer.MIN_VALUE;
    private float previousTemporalYaw = 0.0f;
    private float previousTemporalPitch = 0.0f;
    private float accumulatedTemporalYaw = 0.0f;
    private float accumulatedTemporalPitch = 0.0f;
    private int temporalHistoryResetLogs = 0;
    private String temporalHistoryResetReason = "";
    private float temporalHistoryResetVelocity = 0.0f;
    private float temporalHistoryResetYaw = 0.0f;
    private float temporalHistoryResetPitch = 0.0f;
    private int mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
    private boolean pendingPersistentHistoryClear = false;
    private String pendingPersistentHistoryClearReason = "";
    private int persistentHistoryClearLogs = 0;
    private long terrainLayerCountFrame = Long.MIN_VALUE;
    private int terrainOpaqueLayerCount = 0;
    private int terrainOpaqueDrawCount = 0;
    private boolean deferredPassesRenderedThisFrame = false;
    private boolean preTranslucentDepthCopiedThisFrame = false;
    private boolean preHandDepthCopiedThisFrame = false;
    private boolean setupComputePending = false;
    private boolean terrainCullOverrideActive = false;
    private boolean previousTerrainCullEnabled = true;
    private boolean nothiriumPipelineBlockFormatActive = false;
    private boolean worldFrameActive = false;
    private Framebuffer externalWorldFramebufferTarget = null;
    private boolean renderingShadowMap = false;
    private boolean renderingDeferredIngameHud = false;
    private boolean renderingGui = false;
    private boolean shadowMapPopulated = false;
    private World pendingBetterPortalsPortalBlockWorld;
    private BlockPos pendingBetterPortalsPortalBlockPos;
    private IBlockState pendingBetterPortalsPortalBlockOldState;
    private IBlockState pendingBetterPortalsPortalBlockNewState;
    private int pendingBetterPortalsPortalBlockRefreshDelay = -1;
    private int pendingBetterPortalsPortalBlockChangeCount = 0;
    private World lastBetterPortalsPortalBlockRefreshWorld;
    private BlockPos lastBetterPortalsPortalBlockRefreshPos;
    private int lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
    private long lastBetterPortalsPortalBlockRefreshMillis = 0L;
    private RenderGlobal activeVanillaViewFrustumRenderGlobal = null;
    private World activeVanillaViewFrustumWorld = null;
    private boolean betterPortalsViewFrustumUpdateWarningLogged = false;
    private int cameraFrustumSyncLogs = 0;
    private int clientChunkRenderRefreshLogs = 0;
    private World lastCameraFrustumSyncWorld = null;
    private ViewFrustum lastCameraFrustumSyncViewFrustum = null;
    private int lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
    private int lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
    private int lastStableMainWorldVanillaRenderDistanceChunks = -1;
    private World lastTerrainTransitionWorld = null;
    private int lastTerrainTransitionDimension = Integer.MIN_VALUE;
    private long lastTerrainTransitionMillis = 0L;
    private boolean betterPortalsChunkUpdateWarningLogged = false;
    private boolean shadowHealthLogged = false;
    private int shadowHealthLogAttempts = 0;
    private int guiRenderDepth = 0;
    private int vanillaRecoveryFrames = 0;
    private int pendingBloomTerrainRefreshAttempts = 0;
    private int pendingBloomTerrainRefreshDelay = 0;
    private String pendingBloomTerrainRefreshReason = "";
    private int bloomZeroGeometryFrames = 0;
    private int bloomZeroGeometryRefreshCooldown = 0;
    private long clientRenderFrameNanos = Long.MIN_VALUE;
    private int currentWorldPass = 0;
    private float currentWorldPartialTicks = 0.0F;
    private boolean bloomLayerRenderedThisWorldPass = false;
    private boolean bloomLayerRenderedThisWorldFrame = false;
    private boolean pendingDeferredNativeBloom = false;
    private double pendingDeferredBloomPartialTicks = 0.0D;
    private int pendingDeferredBloomPass = 0;
    private int betterPortalsPipelineLogs = 0;
    private int shaderlessBloomHookLogs = 0;
    private int visibleBloomDiagLogs = 0;
    private int worldLayerDiagLogs = 0;
    private int externalOverlayLogs = 0;
    private int renderGlobalLoadRendererLogs = 0;
    private int vanillaTerrainRendererCreationLogs = 0;
    private int inactiveBetterPortalsTerrainSkipLogs = 0;
    private int terrainDiagnosticLogs = 0;
    private int steadyVanillaTerrainDiagnosticLogs = 0;
    private int shaderlessNothiriumLoadRendererReloadLogs = 0;
    private final Set<String> decoratedLightAuditKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger decoratedLightAuditCount = new AtomicInteger();
    private final Set<String> framedBlockDiagnosticKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger blockcrafteryDiagnosticCount = new AtomicInteger();
    private final AtomicInteger architectureCraftDiagnosticCount = new AtomicInteger();
    private final AtomicInteger framedPriorityDiagnosticCount = new AtomicInteger();
    private final Set<String> currentProblemProbeKeys = ConcurrentHashMap.newKeySet();
    private final AtomicInteger currentProblemProbeCount = new AtomicInteger();
    private long lastShaderlessNothiriumLoadRendererReloadMillis = 0L;
    private int lastShaderlessNothiriumLoadRendererReloadDimension = Integer.MIN_VALUE;
    private long nextWorldPassSerial = 0L;
    private long currentWorldPassSerial = Long.MIN_VALUE;
    private long nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;
    private long nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;
    private long nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
    private boolean shaderlessBloomRenderedThisWorldPass = false;
    private boolean shaderlessBloomRenderedThisWorldFrame = false;
    private boolean shaderlessBloomVertexFormatRefreshRequested = false;
    private final IntBuffer viewportBuffer = org.lwjgl.BufferUtils.createIntBuffer(16);

    private PipelineContext() {
        registerBaseUniforms();
    }

    private static final class PassScope {
        private final boolean bound;
        private final RenderPass previousPass;
        private final ShaderKey previousShaderKey;
        private final WorldRenderingPhase previousPhase;

        private PassScope(boolean bound, RenderPass previousPass, ShaderKey previousShaderKey, WorldRenderingPhase previousPhase) {
            this.bound = bound;
            this.previousPass = previousPass;
            this.previousShaderKey = previousShaderKey;
            this.previousPhase = previousPhase;
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
    }

    private static final class CompiledPipelineState {
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

    private void registerBaseUniforms() {
        Minecraft mc = Minecraft.getMinecraft();

        // --- 1. Global / Engine Uniforms ---
        uniformRegistry.registerInt("worldTime", () -> {
            World world = renderWorld(mc);
            if (world != null) {
                return (int) (world.getWorldTime() % 24000L);
            }
            return 0;
        });

        uniformRegistry.registerFloat("viewWidth", () -> (float) worldTargetWidth(mc));
        uniformRegistry.registerFloat("viewHeight", () -> (float) worldTargetHeight(mc));
        uniformRegistry.registerFloat("pixelSizeX", () -> 1.0f / worldTargetWidth(mc));
        uniformRegistry.registerFloat("pixelSizeY", () -> 1.0f / worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatio", () -> (float) worldTargetWidth(mc) / (float) worldTargetHeight(mc));
        uniformRegistry.registerFloat("aspectRatioInverse", () -> (float) worldTargetHeight(mc) / (float) worldTargetWidth(mc));
        uniformRegistry.registerFloat("screenBrightness", () -> mc.gameSettings.gammaSetting);
        uniformRegistry.registerInt("hideGUI", () -> mc.gameSettings.hideGUI ? 1 : 0);
        uniformRegistry.registerInt("isRightHanded", () -> mc.gameSettings.mainHand == EnumHandSide.RIGHT ? 1 : 0);
        uniformRegistry.registerInt("firstPersonCamera", () -> mc.gameSettings.thirdPersonView == 0 ? 1 : 0);
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
        uniformRegistry.registerFloat("rainStrength", () -> renderWorld(mc) != null ? renderWorld(mc).getRainStrength(mc.getRenderPartialTicks()) : 0.0f);
        uniformRegistry.registerFloat("thunderStrength", () -> renderWorld(mc) != null ? renderWorld(mc).getThunderStrength(mc.getRenderPartialTicks()) : 0.0f);
        uniformRegistry.registerFloat("wetness", () -> wetnessSmooth);
        uniformRegistry.registerInt("biome", () -> currentBiomeExpressionId(mc));
        uniformRegistry.registerInt("biome_precipitation", () -> currentBiomePrecipitation(mc));
        uniformRegistry.registerInt("BIOME_NETHER_WASTES", () -> BIOME_NETHER_WASTES_ID);
        uniformRegistry.registerInt("BIOME_CRIMSON_FOREST", () -> BIOME_CRIMSON_FOREST_ID);
        uniformRegistry.registerInt("BIOME_WARPED_FOREST", () -> BIOME_WARPED_FOREST_ID);
        uniformRegistry.registerInt("BIOME_BASALT_DELTAS", () -> BIOME_BASALT_DELTAS_ID);
        uniformRegistry.registerInt("BIOME_SOUL_SAND_VALLEY", () -> BIOME_SOUL_SAND_VALLEY_ID);
        uniformRegistry.registerInt("BIOME_PALE_GARDEN", () -> BIOME_PALE_GARDEN_ID);
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
        uniformRegistry.registerInt("moonPhase", () -> renderWorld(mc) != null ? renderWorld(mc).getMoonPhase() : 0);
        uniformRegistry.registerInt("frameCounter", () -> (int) (pipelineFrameId % 720720L));
        uniformRegistry.registerInt("frameMod", () -> (int) (pipelineFrameId & 15L));
        uniformRegistry.registerFloat("framemod2", () -> (float) (pipelineFrameId & 1L));
        uniformRegistry.registerVec2("taaOffset", () -> taaOffset(mc));
        uniformRegistry.registerInt("worldDay", () -> renderWorld(mc) != null ? (int) (renderWorld(mc).getWorldTime() / 24000L) : 0);
        uniformRegistry.registerInt("isSpectator", () -> mc.player != null && mc.player.isSpectator() ? 1 : 0);
        uniformRegistry.registerInt("seaLevel", () -> renderWorld(mc) != null ? renderWorld(mc).getSeaLevel() : 63);
        uniformRegistry.registerInt("renderStage", () -> getPhase().ordinal());
        uniformRegistry.registerFloat("mc_chunkFade", () -> ENABLE_CHUNK_FADE ? currentChunkFade : 1.0f);
        uniformRegistry.registerVec3("ausmAstralConstellationColor", () -> currentAstralConstellationColor.clone());
        uniformRegistry.registerVec3("ausmAstralTierColor", () -> currentAstralTierColor.clone());
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
        uniformRegistry.registerVec3("skyColor", () -> skyColor(mc));
        uniformRegistry.registerVec3("fogColor", () -> effectiveFogColor(mc));
        uniformRegistry.registerVec4("iris_FogColor", () -> {
            float[] color = effectiveFogColor(mc);
            return new float[]{color[0], color[1], color[2], 1.0f};
        });

        // --- Sun & Moon Position ---
        uniformRegistry.registerFloat("sunAngle", () -> sunAngle(mc));
        uniformRegistry.registerFloat("shadowAngle", () -> shadowAngle(mc));
        uniformRegistry.registerVec3("endFlashPosition", () -> new float[]{0.0f, 0.0f, 0.0f});
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
                float celestialAngle = world.getCelestialAngle(mc.getRenderPartialTicks());
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

    private Framebuffer currentWorldFramebufferTarget(Minecraft mc) {
        return externalWorldFramebufferTarget != null ? externalWorldFramebufferTarget : mc != null ? mc.getFramebuffer() : null;
    }

    private static World renderWorld(Minecraft mc) {
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }
        return mc != null ? mc.world : null;
    }

    private boolean isExternalWorldFramebufferTarget(Framebuffer target) {
        return externalWorldFramebufferTarget != null && target == externalWorldFramebufferTarget;
    }

    private boolean isBetterPortalsExternalWorldTarget() {
        return externalWorldFramebufferTarget != null && isRenderingBetterPortalsNestedView();
    }

    private int worldTargetWidth(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, target.framebufferWidth) : Math.max(1, mc.displayWidth);
    }

    private int worldTargetHeight(Minecraft mc) {
        Framebuffer target = externalWorldFramebufferTarget;
        return target != null ? Math.max(1, target.framebufferHeight) : Math.max(1, mc.displayHeight);
    }

    private int framebufferWidth(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, target.framebufferWidth) : Math.max(1, mc.displayWidth);
    }

    private int framebufferHeight(Framebuffer target, Minecraft mc) {
        return target != null ? Math.max(1, target.framebufferHeight) : Math.max(1, mc.displayHeight);
    }

    private int[] attachmentSize(Attachment attachment) {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = Minecraft.getMinecraft();
            return new int[]{worldTargetWidth(mc), worldTargetHeight(mc)};
        }
        return new int[]{
                Math.max(1, pingPongManager.attachmentWidth(attachment)),
                Math.max(1, pingPongManager.attachmentHeight(attachment))
        };
    }

    private int[] framebufferSize() {
        if (!pingPongManager.isInitialized()) {
            Minecraft mc = Minecraft.getMinecraft();
            return new int[]{worldTargetWidth(mc), worldTargetHeight(mc)};
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
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return new int[]{0, 0};
        }

        BlockPos pos = new BlockPos(viewEntity.posX, viewEntity.posY + viewEntity.getEyeHeight(), viewEntity.posZ);
        int combinedLight = world.getCombinedLight(pos, 0);
        int block = combinedLight >> 4 & 0xF;
        int sky = combinedLight >> 20 & 0xF;
        if (eyeFluidState(mc) == 1) {
            sky = underwaterSurfaceSkyLight(world, pos, sky);
        }
        return new int[]{block * 16, sky * 16};
    }

    private static float[] skyColor(Minecraft mc) {
        Entity viewEntity = mc == null ? null : mc.getRenderViewEntity();
        World world = renderWorld(mc);
        if (mc != null && world != null && viewEntity != null) {
            return vec3(world.getSkyColor(viewEntity, mc.getRenderPartialTicks()));
        }
        return new float[]{0.5f, 0.7f, 1.0f};
    }

    private float effectiveFogStart(Minecraft mc) {
        if (shouldUseNestedPortalFogFallback(mc)) {
            return isNetherRenderWorld(mc) ? 0.0f : portalFogFar(mc) * 0.75f;
        }
        if (isNetherRenderWorld(mc)) {
            return GL11.glIsEnabled(GL11.GL_FOG) ? GL11.glGetFloat(GL11.GL_FOG_START) : 0.0f;
        }
        return shaderFarPlaneDistance(mc) * SHADER_OVERWORLD_FOG_START_RATIO;
    }

    private float effectiveFogEnd(Minecraft mc) {
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

    private float effectiveFogDensity(Minecraft mc) {
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

    private int effectiveFogMode(Minecraft mc) {
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return currentGlFogMode();
        }
        if (!shouldUseNestedPortalFogFallback(mc)) {
            return isNetherRenderWorld(mc) ? 2 : 0;
        }
        return isNetherRenderWorld(mc) ? 2 : 0;
    }

    private float[] effectiveFogColor(Minecraft mc) {
        if (isNetherRenderWorld(mc)) {
            if (shouldUseNestedPortalFogFallback(mc)) {
                return netherFogColor(mc);
            }
            float[] fogColor = currentGlFogColor();
            return isProbablyUnsetFogColor(fogColor) ? netherFogColor(mc) : dampenNetherFogColor(fogColor);
        }
        if (GL11.glIsEnabled(GL11.GL_FOG)) {
            return currentGlFogColor();
        }
        float[] fogColor = currentGlFogColor();
        return isProbablyUnsetFogColor(fogColor) ? new float[]{0.0f, 0.0f, 0.0f} : fogColor;
    }

    private float[] netherFogColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world != null) {
            return dampenNetherFogColor(vec3(world.getFogColor(mc != null ? mc.getRenderPartialTicks() : 0.0f)));
        }
        return dampenNetherFogColor(PORTAL_NETHER_FOG_COLOR);
    }

    private float[] dampenNetherFogColor(float[] color) {
        if (color == null || color.length < 3) {
            color = PORTAL_NETHER_FOG_COLOR;
        }
        return new float[]{
                clamp01(color[0] * NETHER_SHADER_FOG_COLOR_SCALE),
                clamp01(color[1] * NETHER_SHADER_FOG_COLOR_SCALE),
                clamp01(color[2] * NETHER_SHADER_FOG_COLOR_SCALE)
        };
    }

    private float[] currentGlFogColor() {
        fogColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, fogColorBuffer);
        return new float[]{
                clamp01(fogColorBuffer.get(0)),
                clamp01(fogColorBuffer.get(1)),
                clamp01(fogColorBuffer.get(2))
        };
    }

    private boolean isProbablyUnsetFogColor(float[] color) {
        return color == null
                || color.length < 3
                || (color[0] <= 0.0001f && color[1] <= 0.0001f && color[2] <= 0.0001f);
    }

    private boolean shouldUseNestedPortalFogFallback(Minecraft mc) {
        return isBetterPortalsExternalWorldTarget()
                && !GL11.glIsEnabled(GL11.GL_FOG)
                && renderWorld(mc) != null;
    }

    private boolean isNetherRenderWorld(Minecraft mc) {
        return safeDimensionId(renderWorld(mc)) == -1;
    }

    private float portalFogFar(Minecraft mc) {
        return shaderFarPlaneDistance(mc);
    }

    private static float shaderFarPlaneDistance(Minecraft mc) {
        return shaderRenderDistance(mc) * 2.0f;
    }

    private static float shaderRenderDistance(Minecraft mc) {
        return Math.max(16.0f, mc != null ? mc.gameSettings.renderDistanceChunks * 16.0f : 16.0f);
    }

    private static int currentGlFogMode() {
        return switch (GL11.glGetInteger(GL11.GL_FOG_MODE)) {
            case GL11.GL_LINEAR -> 0;
            case GL11.GL_EXP -> 1;
            case GL11.GL_EXP2 -> 2;
            default -> -1;
        };
    }

    private int currentBiomeExpressionId(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        if (biome == null) {
            return -1;
        }
        int irisId = irisBiomeId(biome);
        return irisId >= 0 ? irisId : Biome.getIdForBiome(biome);
    }

    private int currentBiomePrecipitation(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        if (biome == null) {
            return 0;
        }

        BlockPos pos = currentCameraBlockPos();
        if (biome.getEnableSnow() || biome.isSnowyBiome() || (biome.canRain() && biome.getTemperature(pos) < 0.15f)) {
            return 2;
        }
        return biome.canRain() ? 1 : 0;
    }

    private Biome currentCameraBiome(Minecraft mc) {
        World world = renderWorld(mc);
        if (mc == null || world == null) {
            return null;
        }
        return world.getBiome(currentCameraBlockPos());
    }

    private BlockPos currentCameraBlockPos() {
        return new BlockPos(cameraPositionUnshifted[0], cameraPositionUnshifted[1], cameraPositionUnshifted[2]);
    }

    private static int irisBiomeId(Biome biome) {
        ResourceLocation name = biome.getRegistryName();
        if (name == null) {
            return -1;
        }
        String path = name.getPath().toLowerCase(java.util.Locale.ROOT);
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
        World world = renderWorld(mc);
        float current = world != null ? world.getRainStrength(mc.getRenderPartialTicks()) : 0.0f;
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
        World world = renderWorld(mc);
        if (world == null || pos == null) {
            return 0;
        }
        return blockEntityId(world.getBlockState(pos), world, pos);
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
                offset[0] / worldTargetWidth(mc),
                offset[1] / worldTargetHeight(mc)
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
        World world = renderWorld(mc);
        if (world == null) {
            return new float[]{0.0f, moon ? -100.0f : 100.0f, 0.0f};
        }
        float skyAngle = world.getCelestialAngle(mc.getRenderPartialTicks()) * (float) (Math.PI * 2.0);
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
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0f;
        }
        float angle = world.getCelestialAngle(mc.getRenderPartialTicks()) + 0.25f;
        if (angle >= 1.0f) {
            angle -= 1.0f;
        }
        return angle;
    }

    private float shadowAngle(Minecraft mc) {
        if (renderWorld(mc) == null) {
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
        World world = renderWorld(mc);
        if (world == null) {
            return 0.25f;
        }
        return (float) ((world.getWorldTime() % 24000L) / 24000.0);
    }

    private float adjustedDayTime(Minecraft mc) {
        World world = renderWorld(mc);
        return Math.abs(((((world != null ? world.getWorldTime() % 24000L : 0L) / 1000.0f) + 6.0f) % 24.0f) - 12.0f);
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
        World world = renderWorld(mc);
        if (world == null) {
            return 1.0f;
        }
        float worldTime = world.getWorldTime() % 24000L;
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
        initialize(pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    public void initialize(ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                           ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
        initializeInternal(null, pack, optionOverrides, preloadedProperties, loadingBackgroundMode);
    }

    public void initializeCached(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties) {
        initializeInternal(cacheKey, pack, optionOverrides, preloadedProperties, ShaderLoadingScreen.BackgroundMode.SNAPSHOT);
    }

    private void initializeInternal(String cacheKey, ShaderPack pack, Map<String, String> optionOverrides, ShaderProperties preloadedProperties,
                                    ShaderLoadingScreen.BackgroundMode loadingBackgroundMode) {
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

        MainMod.LOGGER.info("[Pipeline] Initializing with pack: {}", pack.getName());

        if (pack.getName().equals("(internal)")) { // NoneShaderPack
            MainMod.LOGGER.info("[Pipeline] Internal None pack selected. Pipeline is inactive.");
            return;
        }

        boolean usingCachedPrograms = cachedPrograms != null;
        boolean restoredCachedPrograms = false;
        ShaderLoadingScreen.begin(pack.getName(), usingCachedPrograms ? 9 : 12, loadingBackgroundMode);
        try {
            Minecraft mc = Minecraft.getMinecraft();
            ShaderLoadingScreen.step("Loading shader properties");
            ShaderProperties properties = preloadedProperties != null ? preloadedProperties : ShaderProperties.load(pack, optionOverrides);
            ShaderCompileNotifications.beginReload();
            ShaderLoadingScreen.step("Scanning shader programs");
            programSet = usingCachedPrograms ? cachedPrograms.programSet : ShaderProgramSet.load(pack, properties);
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
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
            shadowHealthLogged = false;
            shadowHealthLogAttempts = 0;
            ShaderLoadingScreen.step("Preparing framebuffers");
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
            ShaderLoadingScreen.step("Preparing shader resources");
            shaderImages = ShaderImageSet.load(packDirectives.images());
            shaderImages.resize(mc.displayWidth, mc.displayHeight);
            clearColoredLightImages();
            shaderStorageBuffers = ShaderStorageBufferSet.load(pack, packDirectives.storageBuffers());
            shaderStorageBuffers.resize(mc.displayWidth, mc.displayHeight);
            if (!usingCachedPrograms) {
                ShaderLoadingScreen.step("Compiling compute shaders");
                compileComputePrograms(pack, properties);
                setupComputePending = !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
            }
            logRequestedFeaturesAndCapabilities();
            ShaderLoadingScreen.step("Loading noise texture");
            initializeNoiseTexture(properties);
            ShaderLoadingScreen.step("Loading custom textures");
            loadCustomTextures(pack, properties);
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
                    boolean enabled = properties.isProgramEnabled(pass);
                    pipelineProgram.setEnabled(enabled);

                    if (enabled) {
                        ShaderLoadingScreen.step("Compiling " + pass.getProgramName());
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
                compileFullscreenArrayPrograms(pack, properties);
                ShaderLoadingScreen.step("Building shader pipeline");
                shaderMap = new ShaderMap(loadingMap);
            }
            setupComputePending = hasSetupPrograms();

            isPipelineActive = pingPongManager.isInitialized();
            resetChunkFadeState(true);
            activeCompiledPipelineCacheKey = cacheKey;
            long loadedProgramCount = programs.values().stream().filter(PipelineProgram::hasOwnProgram).count();
            long loadedArrayProgramCount = fullscreenArrayPrograms.values().stream()
                    .flatMap(List::stream)
                    .filter(FullscreenArrayProgram::hasProgram)
                    .count();
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
                ShaderLoadingScreen.step("Keeping terrain cache");
            } else {
                ShaderLoadingScreen.step("Rebuilding terrain");
                rebuildTerrainRenderers();
            }
        } finally {
            if (cachedPrograms != null && !restoredCachedPrograms) {
                cachedPrograms.delete();
            }
            ShaderLoadingScreen.finish();
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
            packDirectives = properties.packDirectives().withComputeDirectives(programSet.computeDirectives());
            rebuildFullscreenProgramArrays();
            packDirectives = packDirectives.withCapabilities(
                    ShaderPipelineCapabilities.from(packDirectives)
                            .withExtraProgramArrayEntries(hasExtraProgramArrayEntries())
            );
            restoreCompiledPipeline(cachedPrograms);
            activePackName = pack.getName();
            activeCompiledPipelineCacheKey = cacheKey;
            setupComputePending = hasSetupPrograms();
            resetTransientWorldRenderState();
            isPipelineActive = true;
            resetChunkFadeState(true);
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

    private void cacheActiveCompiledPipeline() {
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

    private CompiledPipelineState detachCompiledPipeline() {
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

    private void restoreCompiledPipeline(CompiledPipelineState state) {
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
    }

    private CompiledPipelineState removeCachedCompiledPipeline(String cacheKey) {
        return cacheKey == null ? null : compiledPipelineCache.remove(cacheKey);
    }

    private void deleteCachedCompiledPipeline(String cacheKey) {
        CompiledPipelineState state = removeCachedCompiledPipeline(cacheKey);
        if (state != null) {
            state.delete();
        }
    }

    private void deleteCachedCompiledPipelines() {
        compiledPipelineCache.values().forEach(CompiledPipelineState::delete);
        compiledPipelineCache.clear();
    }

    private boolean isInternalPipelinePack() {
        return "(internal)".equals(activePackName);
    }

    private void resetTransientWorldRenderState() {
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
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        renderingDeferredIngameHud = false;
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
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

    private static final class ConditionFrame {
        private final boolean parentActive;
        private final boolean active;
        private final boolean branchMatched;

        private ConditionFrame(boolean parentActive, boolean active, boolean branchMatched) {
            this.parentActive = parentActive;
            this.active = active;
            this.branchMatched = branchMatched;
        }

        private boolean parentActive() {
            return parentActive;
        }

        private boolean active() {
            return active;
        }

        private boolean branchMatched() {
            return branchMatched;
        }
    }

    private void rebuildFullscreenProgramArrays() {
        fullscreenProgramArrays.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            FullscreenProgramArray array = FullscreenProgramArray.fromProgramSet(arrayId, programSet);
            fullscreenProgramArrays.put(arrayId, array);
            if (!supportsIndexedFullscreenArray(arrayId) && array.hasExtraPrograms()) {
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
        return fullscreenProgramArrays.values().stream()
                .anyMatch(array -> !supportsIndexedFullscreenArray(array.arrayId()) && array.hasExtraPrograms());
    }

    private int shaderLoadingStepCount(ShaderProperties properties) {
        return 9
                + computeProgramSourceCount(packDirectives.computeDirectives())
                + enabledProgramCount(properties)
                + enabledFullscreenArrayProgramSourceCount(properties);
    }

    private static int computeProgramSourceCount(ShaderComputeDirectives directives) {
        if (directives == null) {
            return 0;
        }
        int count = directives.shadowComputes().size() + directives.finalComputes().size();
        for (List<ComputeProgramSource> sources : directives.computeArrays().values()) {
            count += sources.size();
        }
        return count;
    }

    private int enabledFullscreenArrayProgramSourceCount(ShaderProperties properties) {
        if (programSet == null) {
            return 0;
        }
        int count = 0;
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            if (!supportsIndexedFullscreenArray(arrayId)) {
                continue;
            }
            for (ShaderProgramSource source : programSet.programArray(arrayId)) {
                if (source.hasAnyStage() && properties.isProgramArrayEnabled(arrayId, source.name())) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int enabledProgramCount(ShaderProperties properties) {
        int count = 0;
        for (RenderPass pass : RenderPass.values()) {
            if (properties.isProgramEnabled(pass)) {
                count++;
            }
        }
        return count;
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

    private void compileFullscreenArrayPrograms(ShaderPack pack, ShaderProperties properties) {
        fullscreenArrayPrograms.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
            if (!supportsIndexedFullscreenArray(arrayId)) {
                continue;
            }

            List<FullscreenArrayProgram> compiled = compileFullscreenArrayList(pack, properties, arrayId);
            if (!compiled.isEmpty()) {
                fullscreenArrayPrograms.put(arrayId, compiled);
            }
        }
    }

    private List<FullscreenArrayProgram> compileFullscreenArrayList(
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

            FullscreenArrayProgram arrayProgram = new FullscreenArrayProgram(
                    arrayId,
                    indexForFullscreenArraySource(arrayId, source.name()),
                    source.name(),
                    bindingPass,
                    properties.directivesFor(arrayId, source.name())
            );
            ShaderLoadingScreen.step("Compiling " + source.name());
            ShaderProgram shaderProgram = ShaderCompiler.compileSource(pack, properties, source, bindingPass);
            if (shaderProgram != null) {
                arrayProgram.setShaderProgram(shaderProgram);
                compiled.add(arrayProgram);
                MainMod.LOGGER.debug("[Pipeline] Added indexed fullscreen program: {}", source.name());
            }
        }
        return List.copyOf(compiled);
    }

    private static int indexForFullscreenArraySource(ProgramArrayId arrayId, String sourceName) {
        ShaderProperties.ProgramArrayKey key = ShaderProperties.ProgramArrayKey.parse(sourceName);
        if (key == null || key.arrayId() != arrayId) {
            return 0;
        }
        return key.index();
    }

    private static boolean supportsIndexedFullscreenArray(ProgramArrayId arrayId) {
        return arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN;
    }

    private static RenderPass fullscreenArrayBindingPass(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN || arrayId == ProgramArrayId.PREPARE) {
            return RenderPass.PREPARE;
        }
        if (arrayId == ProgramArrayId.DEFERRED) {
            return RenderPass.DEFERRED;
        }
        if (arrayId == ProgramArrayId.COMPOSITE) {
            return RenderPass.COMPOSITE;
        }
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            return RenderPass.SHADOW;
        }
        return RenderPass.FINAL;
    }

    private boolean hasSetupPrograms() {
        return !computeProgramArrays.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SETUP, List.of()).isEmpty();
    }

    private static List<ComputeProgram> compileComputeList(ShaderPack pack, ShaderProperties properties, List<ComputeProgramSource> sources) {
        if (sources.isEmpty()) {
            return List.of();
        }
        List<ComputeProgram> compiled = new ArrayList<>();
        for (ComputeProgramSource source : sources) {
            ShaderLoadingScreen.step("Compiling " + source.name());
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

    private void deleteFullscreenArrayPrograms() {
        fullscreenArrayPrograms.values().stream()
                .flatMap(List::stream)
                .forEach(FullscreenArrayProgram::delete);
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
        return blockEntityId(state, null, null);
    }

    public int blockEntityId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }

        IBlockState pipelineState = actualLightState(state, blockAccess, pos);
        if (isRandomThingsLuminousColoredLightDisabled(pipelineState)) {
            return 0;
        }

        ShaderBlockIdMap.BlockIdRules blockIds = shaderProperties.blockIds();
        if (!blockIds.isEmpty()) {
            int id = blockIds.idFor(pipelineState);
            if (id != 0) {
                return id;
            }
        }

        if (isComplementaryStylePack() && pipelineState.getMaterial() == Material.WATER) {
            return 32000;
        }
        return 0;
    }

    public int blockMetadata(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return blockMetadata(actualLightState(state, blockAccess, pos));
    }

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return actualLightState(state, blockAccess, pos);
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
        if (isBlockcrafteryEditableBlock(state)) {
            return null;
        }
        if (inheritedStates.length > 0) {
            return inheritedStates[0];
        }
        return actualLightState(state, blockAccess, pos);
    }

    public IBlockState inheritedBloomGeometryRenderState(IBlockState state, IBlockState inheritedState) {
        if (state != null && inheritedState != null
                && (isArchitectureCraftShapeBlock(state) || isBlockcrafteryEditableBlock(state))) {
            return state;
        }
        return inheritedState != null ? inheritedState : state;
    }

    public boolean isFramedBlockDiagnosticTarget(IBlockState state) {
        return isBlockcrafteryEditableBlock(state) || isArchitectureCraftShapeBlock(state);
    }

    public boolean isBlockcrafteryEditableState(IBlockState state) {
        return isBlockcrafteryEditableBlock(state);
    }

    public void logFramedBlockDiagnostic(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                         BlockRenderLayer layer, int startVertex, int endVertex, Boolean result,
                                         String extra) {
        if (!isFramedBlockDiagnosticTarget(state)) {
            return;
        }

        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedBloomState = inheritedBloomRenderState(state, blockAccess, pos);
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
        return blockRenderEmissionForState(state, blockAccess, pos);
    }

    public int blockRenderEmissionWithFramedInheritance(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int emission = blockRenderEmission(state, blockAccess, pos);
        if (!isFramedBlockDiagnosticTarget(state)) {
            return emission;
        }
        for (IBlockState inheritedState : inheritedRenderStates(state, blockAccess, pos)) {
            emission = Math.max(emission, blockRenderEmissionForState(inheritedState, blockAccess, pos));
        }
        return emission;
    }

    public boolean shouldInheritFramedEmissionInBasePass(IBlockState state) {
        return isFramedBlockDiagnosticTarget(state);
    }

    public int blockRenderAlpha(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
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
                            + ", layer=" + MinecraftForgeClient.getRenderLayer());
        }
        return -1;
    }

    public void setBlockRenderDebugContext(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        BlockRenderContext.setDebugBlock(
                diagnosticBlockKind(state, effectiveState),
                stateName(state),
                stateName(effectiveState)
        );
    }

    public String diagnosticStateName(IBlockState state) {
        return stateName(state);
    }

    public String diagnosticBlockKind(IBlockState state, IBlockState effectiveState) {
        if (isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        if (isRandomThingsLuminousBlock(state) || isRandomThingsLuminousBlock(effectiveState)) {
            return "randomthings-luminous";
        }
        if (isPriorityFramedDiagnosticName(state) || isPriorityFramedDiagnosticName(effectiveState)) {
            return "emissive-name";
        }
        return "other";
    }

    public boolean isCurrentProblemProbeTarget(IBlockState state) {
        return isRandomThingsLuminousBlock(state)
                || isPriorityFramedDiagnosticName(state);
    }

    public void logCurrentProblemProbe(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                       String detail) {
        IBlockState effectiveState = effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedState = inheritedBloomRenderState(state, blockAccess, pos);
        if (!isCurrentProblemProbeTarget(state)
                && !isCurrentProblemProbeTarget(effectiveState)
                && !isCurrentProblemProbeTarget(inheritedState)) {
            return;
        }

        String key = source
                + "|" + safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + formatBlockPos(pos)
                + "|" + String.valueOf(MinecraftForgeClient.getRenderLayer())
                + "|" + stateName(state)
                + "|" + stateName(effectiveState)
                + "|" + stateName(inheritedState)
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        int count = currentProblemProbeCount.incrementAndGet();
        if (count > MAX_CURRENT_PROBLEM_PROBE_LOGS) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} dim={} pos={} layer={} bloomLayer={} state={} effective={} inherited={} emission={} inheritedEmission={} alpha={} blockId={} inheritedBlockId={} detail={}",
                count,
                source,
                diagnosticBlockKind(state, effectiveState),
                safeDimensionId(blockAccess instanceof World world ? world : null),
                formatBlockPos(pos),
                MinecraftForgeClient.getRenderLayer(),
                AusmBloomLayer.layer(),
                framedDiagnosticState("state", state, blockAccess, pos, MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer()),
                framedDiagnosticState("effective", effectiveState, blockAccess, pos, MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer()),
                framedDiagnosticState("inherited", inheritedState, blockAccess, pos, MinecraftForgeClient.getRenderLayer(), AusmBloomLayer.layer()),
                blockRenderEmission(state, blockAccess, pos),
                blockRenderEmissionWithFramedInheritance(state, blockAccess, pos),
                -1,
                blockEntityId(state, blockAccess, pos),
                blockEntityId(inheritedState, blockAccess, pos),
                detail
        );
    }

    public void logCurrentRenderContextProbe(String source, String detail) {
        String kind = BlockRenderContext.debugKind();
        if (!"blockcraftery".equals(kind)
                && !"architecturecraft".equals(kind)
                && !"randomthings-luminous".equals(kind)
                && !"emissive-name".equals(kind)) {
            return;
        }

        String key = source
                + "|" + kind
                + "|" + BlockRenderContext.debugState()
                + "|" + BlockRenderContext.debugEffectiveState()
                + "|" + String.valueOf(MinecraftForgeClient.getRenderLayer())
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        int count = currentProblemProbeCount.incrementAndGet();
        if (count > MAX_CURRENT_PROBLEM_PROBE_LOGS) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} layer={} state={} effective={} contextEmission={} contextAlpha={} bloomMask={} bloomMaskColor=0x{} detail={}",
                count,
                source,
                kind,
                MinecraftForgeClient.getRenderLayer(),
                BlockRenderContext.debugState(),
                BlockRenderContext.debugEffectiveState(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.blockAlpha(),
                BlockRenderContext.bloomMaskFallback(),
                Integer.toHexString(BlockRenderContext.bloomMaskColor()),
                detail
        );
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state) {
        return isAstralCrystalCluster(state);
    }

    public boolean shouldUseCrystalOnlyEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return shouldUseCrystalOnlyEmission(actualLightState(state, blockAccess, pos));
    }

    private int blockRenderEmissionForState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int luminousEmission = randomThingsLuminousEmission(state);
        if (luminousEmission > 0) {
            return luminousEmission;
        }
        int astralEmission = astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        try {
            if (blockAccess != null && pos != null) {
                return clampLightValue(state.getLightValue(blockAccess, pos));
            }
        } catch (RuntimeException ignored) {
        }
        try {
            return clampLightValue(state.getLightValue());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private IBlockState[] inheritedRenderStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return new IBlockState[0];
        }

        IBlockState blockcrafteryState = actualState(blockcrafteryDecoratedState(state, blockAccess, pos), blockAccess, pos);
        if (blockcrafteryState != null) {
            return new IBlockState[]{blockcrafteryState};
        }

        IBlockState architectureBase = actualState(architectureCraftBaseState(state, blockAccess, pos), blockAccess, pos);
        IBlockState architectureSecondary = actualState(architectureCraftSecondaryState(state, blockAccess, pos), blockAccess, pos);
        if (architectureBase != null && architectureSecondary != null && architectureSecondary != architectureBase) {
            return new IBlockState[]{architectureBase, architectureSecondary};
        }
        if (architectureBase != null) {
            return new IBlockState[]{architectureBase};
        }
        if (architectureSecondary != null) {
            return new IBlockState[]{architectureSecondary};
        }

        IBlockState pipelineState = actualLightState(state, blockAccess, pos);
        return pipelineState != null && pipelineState != state ? new IBlockState[]{pipelineState} : new IBlockState[0];
    }

    private boolean isBloomOrEmissiveInheritedState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        return blockRenderEmissionForState(state, blockAccess, pos) > 0
                || (bloomLayer != null && canRenderInLayer(state, bloomLayer));
    }

    private int nextFramedDiagnosticCount(IBlockState state, boolean priority) {
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

    private String framedDiagnosticKind(IBlockState state) {
        if (isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        return "unknown";
    }

    private boolean isPriorityFramedDiagnosticState(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                   BlockRenderLayer bloomLayer) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        if (blockRenderEmissionForState(state, blockAccess, pos) > 0 || blockEntityId(state, blockAccess, pos) != 0) {
            return true;
        }
        if (bloomLayer != null && canRenderInLayer(state, bloomLayer)) {
            return true;
        }
        return isPriorityFramedDiagnosticName(state);
    }

    private boolean isPriorityFramedDiagnosticName(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        ResourceLocation name = registryName(state);
        String path = name != null && name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        String namespace = name != null && name.getNamespace() != null ? name.getNamespace().toLowerCase(java.util.Locale.ROOT) : "";
        return path.contains("luminous")
                || path.contains("emissive")
                || path.contains("bloom")
                || namespace.contains("randomthings")
                || namespace.contains("lumenized");
    }

    private String framedDiagnosticInheritedStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
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

    private String framedDiagnosticState(String label, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                         BlockRenderLayer currentLayer, BlockRenderLayer bloomLayer) {
        if (state == null) {
            return label + "{state=null}";
        }

        Block block = state.getBlock();
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
                + ", material=" + (state.getMaterial() != null ? state.getMaterial() : "null")
                + "}";
    }

    private static EnumBlockRenderType safeRenderType(IBlockState state) {
        try {
            return state != null ? state.getRenderType() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static BlockRenderLayer safeRenderLayer(IBlockState state) {
        try {
            return state != null && state.getBlock() != null ? state.getBlock().getRenderLayer() : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static int safeLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        try {
            if (state == null) {
                return 0;
            }
            if (blockAccess != null && pos != null) {
                return state.getLightValue(blockAccess, pos);
            }
            return state.getLightValue();
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    private static boolean safeOpaqueCube(IBlockState state) {
        try {
            return state != null && state.isOpaqueCube();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean safeFullCube(IBlockState state) {
        try {
            return state != null && state.isFullCube();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            return state != null && state.getBlock() != null && layer != null && state.getBlock().canRenderInLayer(state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static int blockMetadata(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return 0;
        }
        try {
            return state.getBlock().getMetaFromState(state);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private int randomThingsLuminousEmission(IBlockState state) {
        if (state == null) {
            return 0;
        }

        Block block = state.getBlock();
        if (!isRandomThingsLuminousBlock(state)) {
            return 0;
        }

        Class<?> luminousBlockClass = randomThingsLuminousBlockClass();
        if (block == null || luminousBlockClass == null || !luminousBlockClass.isInstance(block)) {
            return 0;
        }

        Method shouldGlow = randomThingsShouldGlowMethod;
        if (shouldGlow == null) {
            return 0;
        }

        int metadata = 0;
        try {
            metadata = block.getMetaFromState(state);
        } catch (RuntimeException | LinkageError ignored) {
        }

        try {
            Object result = shouldGlow.invoke(block, state, metadata);
            return Boolean.TRUE.equals(result) ? 15 : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    private Class<?> randomThingsLuminousBlockClass() {
        if (!randomThingsLuminousBlockResolved) {
            randomThingsLuminousBlockResolved = true;
            try {
                randomThingsLuminousBlockClass = Class.forName(RANDOM_THINGS_LUMINOUS_BLOCK_CLASS, false, PipelineContext.class.getClassLoader());
                randomThingsShouldGlowMethod = randomThingsLuminousBlockClass.getMethod("shouldGlow", IBlockState.class, int.class);
                randomThingsShouldGlowMethod.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                randomThingsLuminousBlockClass = null;
                randomThingsShouldGlowMethod = null;
            }
        }
        return randomThingsLuminousBlockClass;
    }

    private static boolean isRandomThingsLuminousBlock(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && "randomthings".equals(name.getNamespace())
                && containsIgnoreCase(name.getPath(), "luminous");
    }

    private static boolean isRandomThingsTranslucentLuminousBlock(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && "randomthings".equals(name.getNamespace())
                && containsIgnoreCase(name.getPath(), "translucent")
                && containsIgnoreCase(name.getPath(), "luminous");
    }

    private static int astralCrystalEmission(IBlockState state) {
        if (!isAstralCrystalCluster(state)) {
            return 0;
        }
        ResourceLocation name = registryName(state);
        String path = name.getPath();
        if ("blockcelestialcrystals".equalsIgnoreCase(path)) {
            int stage = parseIntProperty(state, "stage", 2);
            return clampLightValue(6 + Math.max(0, Math.min(4, stage)));
        }
        if ("blockgemcrystals".equalsIgnoreCase(path)) {
            String stage = propertyValue(state, "stage");
            if ("stage_2_day".equalsIgnoreCase(stage)
                    || "stage_2_night".equalsIgnoreCase(stage)
                    || "stage_2_sky".equalsIgnoreCase(stage)) {
                return 10;
            }
            if ("stage_1".equalsIgnoreCase(stage)) {
                return 8;
            }
            return 6;
        }
        return 0;
    }

    private static boolean isAstralCrystalCluster(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null || !"astralsorcery".equals(name.getNamespace())) {
            return false;
        }
        String path = name.getPath();
        return "blockcelestialcrystals".equalsIgnoreCase(path)
                || "blockgemcrystals".equalsIgnoreCase(path);
    }

    private static int astralCrystalMaterialId(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null || !"astralsorcery".equals(name.getNamespace())) {
            return 0;
        }

        String path = name.getPath();
        if ("blockcelestialcrystals".equalsIgnoreCase(path)) {
            return 10914; // cool light blue
        }
        if ("blockgemcrystals".equalsIgnoreCase(path)) {
            String stage = propertyValue(state, "stage");
            if ("stage_2_day".equalsIgnoreCase(stage)) {
                return 10904; // warm orange
            }
            if ("stage_2_night".equalsIgnoreCase(stage)) {
                return 10916; // blue
            }
            return 10912; // cyan/sky
        }
        return 0;
    }

    private static int astralCrystalVoxelId(IBlockState state) {
        return localActVoxelId(astralCrystalMaterialId(state));
    }

    private static int parseIntProperty(IBlockState state, String propertyName, int fallback) {
        String value = propertyValue(state, propertyName);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(IBlockState state, String propertyName) {
        if (state == null || propertyName == null) {
            return null;
        }
        for (Map.Entry<net.minecraft.block.properties.IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            net.minecraft.block.properties.IProperty property = entry.getKey();
            if (property != null && property.getName().equals(propertyName)) {
                return property.getName(entry.getValue());
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (value == null || needle == null) {
            return false;
        }
        int max = value.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            if (value.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }

    private static int clampLightValue(int value) {
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
            syntheticLightCandidates.remove(pos.toLong());
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
        Minecraft mc = Minecraft.getMinecraft();
        refreshSyntheticLightCandidate(mc != null ? mc.world : null, pos);
    }

    public void refreshSyntheticLightCandidate(World world, BlockPos pos) {
        if (pos == null) {
            return;
        }
        long key = pos.toLong();
        syntheticLightCandidates.remove(key);
        if (!canTrackSyntheticLights() || world == null || !world.isBlockLoaded(pos, false)) {
            return;
        }
        IBlockState state;
        try {
            state = world.getBlockState(pos);
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
        int minX = Math.min(from.getX(), to.getX()) - 1;
        int minY = Math.max(0, Math.min(from.getY(), to.getY()) - 1);
        int minZ = Math.min(from.getZ(), to.getZ()) - 1;
        int maxX = Math.max(from.getX(), to.getX()) + 1;
        int maxY = Math.min(255, Math.max(from.getY(), to.getY()) + 1);
        int maxZ = Math.max(from.getZ(), to.getZ()) + 1;
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
            syntheticLightCandidates.remove(pos.toLong());
        }
    }

    private boolean canTrackSyntheticLights() {
        return ENABLE_CPU_LIGHT_INJECTION && isPipelineActive && shaderImages.active() && !shaderProperties.blockIds().isEmpty();
    }

    private int syntheticLightVoxelId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return syntheticLightInfo(state, blockAccess, pos).voxelId;
    }

    private SyntheticLightInfo syntheticLightInfo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "missing_input");
        }
        if (shaderProperties.blockIds().isEmpty()) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "no_block_ids");
        }
        IBlockState actualState = actualLightState(state, blockAccess, pos);
        int shaderBlockId = shaderProperties.blockIds().idFor(actualState);
        if (isRandomThingsLuminousColoredLightDisabled(actualState)) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, 0, randomThingsLuminousEmission(actualState), "colored_light_disabled");
        }
        int voxelId = localActVoxelId(shaderBlockId);
        if (voxelId <= 0) {
            voxelId = compatSyntheticLightVoxelId(actualState);
        }
        int emission = 0;
        try {
            emission = actualState.getLightValue(blockAccess, pos);
        } catch (RuntimeException ignored) {
            emission = Math.max(randomThingsLuminousEmission(actualState), astralCrystalEmission(actualState));
            if (emission <= 0) {
                return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, 0, "light_value_error");
            }
        }
        emission = Math.max(emission, randomThingsLuminousEmission(actualState));
        emission = Math.max(emission, astralCrystalEmission(actualState));
        if (voxelId <= 0) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, 0, emission, "no_colored_voxel_mapping");
        }
        if (emission <= 0) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "not_emissive");
        }
        return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "ok");
    }

    private void putSyntheticLightCandidate(BlockPos pos, boolean force) {
        long key = pos.toLong();
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
        syntheticLightCandidates.put(key, pos.toImmutable());
    }

    private void removeSyntheticLightCandidatesInRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (Map.Entry<Long, BlockPos> entry : syntheticLightCandidates.entrySet()) {
            BlockPos pos = entry.getValue();
            if (pos == null) {
                syntheticLightCandidates.remove(entry.getKey());
                continue;
            }
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
            }
        }
    }

    private void auditSyntheticLight(String source, BlockPos pos, SyntheticLightInfo lightInfo, String result) {
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

    private void logDecoratedLightEmission(IBlockState originalState, IBlockState decoratedState,
                                           IBlockAccess blockAccess, BlockPos pos, int emission) {
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

    private void auditProjectRedLight(TileEntity tileEntity, int[] voxelIds, int count, String result) {
        String diagnosis = ProjectRedIlluminationCompat.diagnose(tileEntity);
        if (diagnosis == null) {
            return;
        }
        auditProjectRedDiagnosis(tileEntity, voxelIds, count, result, diagnosis);
    }

    private void auditProjectRedDiagnosis(TileEntity tileEntity, int[] voxelIds, int count, String result, String diagnosis) {
        BlockPos pos = tileEntity != null ? tileEntity.getPos() : null;
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

    private void auditProjectRedTileEntity(World world, BlockPos pos, String result) {
        if (world == null || pos == null) {
            return;
        }
        TileEntity tileEntity;
        try {
            tileEntity = world.getTileEntity(pos);
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

    private boolean recordProjectRedSyntheticLightCandidate(IBlockAccess blockAccess, BlockPos pos, String result) {
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

    private TileEntity tileEntityAt(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess == null || pos == null) {
            return null;
        }
        try {
            return blockAccess.getTileEntity(pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void resetColoredLightAudit() {
        coloredLightAuditKeys.clear();
        coloredLightAuditCount.set(0);
    }

    private boolean shouldAuditSyntheticLight(SyntheticLightInfo lightInfo) {
        return lightInfo.voxelId > 0
                || isKnownColoredLightAuditTarget(lightInfo.originalState)
                || isKnownColoredLightAuditTarget(lightInfo.actualState);
    }

    private boolean shouldProbeColoredLightTileEntity(IBlockState state, SyntheticLightInfo lightInfo) {
        return isProjectRedTileHost(state)
                || lightInfo != null && isProjectRedTileHost(lightInfo.originalState)
                || lightInfo != null && isProjectRedTileHost(lightInfo.actualState);
    }

    private boolean isProjectRedTileHost(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && (("projectred-illumination".equals(name.getNamespace()))
                || ("forgemultipartcbe".equals(name.getNamespace()) && "multipart_block".equals(name.getPath())));
    }

    private boolean isKnownColoredLightAuditTarget(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }

        String namespace = name.getNamespace();
        String path = name.getPath();
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
        if ("randomthings".equals(namespace)) {
            return containsIgnoreCase(path, "luminous") || containsIgnoreCase(path, "runic");
        }
        if ("astralsorcery".equals(namespace)) {
            return "blockcelestialcrystals".equalsIgnoreCase(path) || "blockgemcrystals".equalsIgnoreCase(path);
        }
        return false;
    }

    private static ResourceLocation registryName(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return null;
        }
        return state.getBlock().getRegistryName();
    }

    private static String stateName(IBlockState state) {
        return state != null ? state.toString() : "null";
    }

    private static String formatBlockPos(BlockPos pos) {
        return pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "null";
    }

    private static String formatVoxelIds(int[] voxelIds, int count) {
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

    private IBlockState actualLightState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return state;
        }
        IBlockState decoratedState = blockcrafteryDecoratedState(state, blockAccess, pos);
        if (decoratedState == null) {
            decoratedState = architectureCraftBaseState(state, blockAccess, pos);
        }
        IBlockState renderState = decoratedState != null ? decoratedState : state;
        if (blockAccess == null || pos == null) {
            return renderState;
        }
        return actualState(renderState, blockAccess, pos);
    }

    private IBlockState actualState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return state;
        }
        try {
            return state.getActualState(blockAccess, pos);
        } catch (RuntimeException ignored) {
            return state;
        }
    }

    private IBlockState blockcrafteryDecoratedState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return null;
        }

        IBlockState extendedDecoratedState = blockcrafteryExtendedDecoratedState(state);
        if (isValidBlockcrafteryDecoratedState(extendedDecoratedState)) {
            return extendedDecoratedState;
        }

        if (blockAccess == null || pos == null) {
            return null;
        }

        Class<?> tileClass = blockcrafteryTileClass();
        Field stateField = blockcrafteryTileStateField;
        if (tileClass == null || stateField == null) {
            return null;
        }

        TileEntity tile;
        try {
            tile = blockAccess.getTileEntity(pos);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }

        if (tile == null || !tileClass.isInstance(tile)) {
            return null;
        }

        try {
            Object value = stateField.get(tile);
            if (!(value instanceof IBlockState decoratedState)
                    || !isValidBlockcrafteryDecoratedState(decoratedState)) {
                return null;
            }
            return decoratedState;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private IBlockState blockcrafteryExtendedDecoratedState(IBlockState state) {
        if (!(state instanceof IExtendedBlockState extendedState) || state.getBlock() == null) {
            return null;
        }

        Method method = blockcrafteryStatePropertyMethod(state.getBlock().getClass());
        if (method == null) {
            return null;
        }

        try {
            Object property = method.invoke(state.getBlock());
            if (!(property instanceof IUnlistedProperty)) {
                return null;
            }
            Object value = extendedState.getValue((IUnlistedProperty) property);
            return value instanceof IBlockState decoratedState ? decoratedState : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private boolean isValidBlockcrafteryDecoratedState(IBlockState decoratedState) {
        return decoratedState != null
                && decoratedState.getBlock() != null
                && decoratedState.getBlock() != Blocks.AIR
                && !isBlockcrafteryEditableBlock(decoratedState);
    }

    private IBlockState architectureCraftBaseState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return architectureCraftMaterialState(state, blockAccess, pos, false);
    }

    private IBlockState architectureCraftSecondaryState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return architectureCraftMaterialState(state, blockAccess, pos, true);
    }

    private IBlockState architectureCraftMaterialState(IBlockState state, IBlockAccess blockAccess, BlockPos pos, boolean secondary) {
        if (!isArchitectureCraftShapeBlock(state) || blockAccess == null || pos == null) {
            return null;
        }

        Class<?> tileClass = architectureCraftTileClass();
        Method method = secondary ? architectureCraftSecondaryStateMethod : architectureCraftBaseStateMethod;
        if (tileClass == null || method == null) {
            return null;
        }

        Object tile = architectureCraftTile(blockAccess, pos);
        if (tile == null || !tileClass.isInstance(tile)) {
            return null;
        }

        try {
            Object value = method.invoke(tile);
            if (!(value instanceof IBlockState materialState)
                    || !isValidArchitectureCraftMaterialState(materialState)) {
                return null;
            }
            return materialState;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private boolean isValidArchitectureCraftMaterialState(IBlockState materialState) {
        return materialState != null
                && materialState.getBlock() != null
                && materialState.getBlock() != Blocks.AIR
                && !isBlockcrafteryEditableBlock(materialState)
                && !isArchitectureCraftShapeBlock(materialState);
    }

    public String describeBlockcrafteryDecoratedLayer(IBlockState state, IBlockAccess blockAccess, BlockPos pos, BlockRenderLayer layer) {
        IBlockState decoratedState = blockcrafteryDecoratedState(state, blockAccess, pos);
        if (decoratedState == null || decoratedState.getBlock() == null) {
            return " decorated=null";
        }

        Block decoratedBlock = decoratedState.getBlock();
        boolean canCurrentLayer = false;
        try {
            canCurrentLayer = layer != null && decoratedBlock.canRenderInLayer(decoratedState, layer);
        } catch (RuntimeException | LinkageError ignored) {
        }

        return " decorated=" + registryName(decoratedState)
                + " decoratedState=" + stateName(decoratedState)
                + " decoratedLayer=" + decoratedBlock.getRenderLayer()
                + " decoratedCanCurrent=" + canCurrentLayer
                + " decoratedCanLayers=" + describeRenderableLayers(decoratedState);
    }

    private static String describeRenderableLayers(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            boolean canRender = false;
            try {
                canRender = state.getBlock().canRenderInLayer(state, layer);
            } catch (RuntimeException | LinkageError ignored) {
            }
            if (canRender) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(layer.name());
                first = false;
            }
        }
        return builder.append(']').toString();
    }

    private Class<?> blockcrafteryTileClass() {
        if (!blockcrafteryTileResolved) {
            blockcrafteryTileResolved = true;
            try {
                blockcrafteryTileClass = Class.forName(BLOCKCRAFTERY_TILE_EDITABLE_BLOCK_CLASS, false, PipelineContext.class.getClassLoader());
                blockcrafteryTileStateField = blockcrafteryTileClass.getField("state");
                blockcrafteryTileStateField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                blockcrafteryTileClass = null;
                blockcrafteryTileStateField = null;
            }
        }
        return blockcrafteryTileClass;
    }

    private Method blockcrafteryStatePropertyMethod(Class<?> blockClass) {
        if (blockClass == null) {
            return null;
        }

        Method cached = blockcrafteryStatePropertyMethods.get(blockClass);
        if (cached != null) {
            return cached;
        }
        if (blockcrafteryMissingStatePropertyMethods.contains(blockClass)) {
            return null;
        }

        try {
            Method method = blockClass.getMethod("getStateProperty");
            method.setAccessible(true);
            Method previous = blockcrafteryStatePropertyMethods.putIfAbsent(blockClass, method);
            return previous != null ? previous : method;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            blockcrafteryMissingStatePropertyMethods.add(blockClass);
            return null;
        }
    }

    private Class<?> architectureCraftTileClass() {
        if (!architectureCraftTileResolved) {
            architectureCraftTileResolved = true;
            try {
                architectureCraftTileClass = Class.forName(ARCHITECTURECRAFT_TILE_SHAPE_CLASS, false, PipelineContext.class.getClassLoader());
                architectureCraftGetTileMethod = architectureCraftTileClass.getMethod("get", IBlockAccess.class, BlockPos.class);
                architectureCraftGetTileMethod.setAccessible(true);
                architectureCraftBaseStateMethod = architectureCraftTileClass.getMethod("getBaseBlockState");
                architectureCraftBaseStateMethod.setAccessible(true);
                architectureCraftSecondaryStateMethod = architectureCraftTileClass.getMethod("getSecondaryBlockState");
                architectureCraftSecondaryStateMethod.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                architectureCraftTileClass = null;
                architectureCraftGetTileMethod = null;
                architectureCraftBaseStateMethod = null;
                architectureCraftSecondaryStateMethod = null;
            }
        }
        return architectureCraftTileClass;
    }

    private Object architectureCraftTile(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess == null || pos == null) {
            return null;
        }

        try {
            TileEntity tile = blockAccess.getTileEntity(pos);
            if (tile != null && architectureCraftTileClass != null && architectureCraftTileClass.isInstance(tile)) {
                return tile;
            }
        } catch (RuntimeException | LinkageError ignored) {
        }

        Method method = architectureCraftGetTileMethod;
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, blockAccess, pos);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isBlockcrafteryEditableBlock(IBlockState state) {
        ResourceLocation name = registryName(state);
        return name != null
                && "blockcraftery".equals(name.getNamespace())
                && name.getPath().startsWith("editable_");
    }

    private static boolean isArchitectureCraftShapeBlock(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        ResourceLocation name = registryName(state);
        if (name != null && "architecturecraft".equals(name.getNamespace())) {
            return true;
        }
        return state.getBlock().getClass().getName().startsWith(ARCHITECTURECRAFT_BLOCK_PACKAGE);
    }

    public boolean shouldSeparateBlockAo(IBlockState state) {
        if (!shouldSeparateAo() || state == null) {
            return false;
        }

        Block block = state.getBlock();
        return block != null
                && block.getRenderLayer() == BlockRenderLayer.SOLID
                && !isRandomThingsLuminousBlock(state);
    }

    public boolean shouldSeparateBlockAo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return shouldSeparateBlockAo(actualLightState(state, blockAccess, pos));
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
        if (!isPipelineActive) {
            return true;
        }
        return shaderProperties.renderSettings().underwaterOverlay()
                || eyeFluidState(Minecraft.getMinecraft()) == 1;
    }

    public boolean shouldRenderSkyDisc() {
        return !isPipelineActive || shaderProperties.renderSettings().sky();
    }

    public boolean shouldSuppressVanillaSunGeometry() {
        return isPipelineActive && (!shaderProperties.renderSettings().sun() || shouldSuppressComplementaryVanillaCelestialGeometry());
    }

    public boolean shouldSuppressVanillaMoonGeometry() {
        return isPipelineActive && (!shaderProperties.renderSettings().moon() || shouldSuppressComplementaryVanillaCelestialGeometry());
    }

    private boolean shouldSuppressComplementaryVanillaCelestialGeometry() {
        return isComplementaryStylePack();
    }

    private boolean isComplementaryStylePack() {
        String packName = activePackName == null ? "" : activePackName.toLowerCase(java.util.Locale.ROOT);
        return packName.contains("complementary") || packName.contains("complimentary");
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
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingGuiScreen()) {
            return false;
        }
        if (isBetweenlandsEntity(currentEntityKey) || currentEntityKey == null && isBetweenlandsRenderStack()) {
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
                || activePass == RenderPass.GBUFFERS_ITEM
                || activePass == RenderPass.GBUFFERS_ENTITIES
                || activePass == RenderPass.GBUFFERS_ENTITIES_GLOWING
                || activePass == RenderPass.GBUFFERS_HAND
                || activePass == RenderPass.GBUFFERS_HAND_WATER
                || activePass == RenderPass.GBUFFERS_BLOCK
                || activePass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || activePass == RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT;
    }

    public boolean shouldUsePipelineBlockFormat() {
        return isPipelineActive || shouldUseShaderlessBloomVertexMetadata();
    }

    private boolean shouldUseShaderlessBloomVertexMetadata() {
        return !isPipelineActive
                && !AusmBloomLayer.shouldUseNativeHook()
                && bloomRenderer.hasBloomResources();
    }

    public boolean isShadowPassActive() {
        return isPipelineActive && (renderingShadowMap || activePass != null && activePass.stage() == ProgramStage.SHADOW);
    }

    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : activePhase;
    }

    public int renderNothiriumTerrainLayer(BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || activePass == null || viewEntity == null) {
            return -1;
        }
        if (NothiriumBypass.shouldBypass()) {
            return -1;
        }

        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.TERRAIN_SOLID
                && phase != WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED
                && phase != WorldRenderingPhase.TERRAIN_CUTOUT
                && phase != WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            return -1;
        }
        if (!shouldUseNothiriumShadowBridge()) {
            return -1;
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        nothiriumShadowRenderer.drainUploads();
        return nothiriumShadowRenderer.renderVisibleLayer(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                nothiriumFallbackBlockEntityId(layer),
                nothiriumFallbackRenderType(layer)
        );
    }

    public boolean renderNothiriumRendererPass(Object chunkRenderPass) {
        boolean translucentPass = isNothiriumTranslucentPass(chunkRenderPass);
        if (shouldCancelDuplicateNothiriumTranslucentPass(translucentPass)) {
            return true;
        }
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass != RenderPass.GBUFFERS_WATER
                || getPhase() != WorldRenderingPhase.TERRAIN_TRANSLUCENT
                || renderingGuiScreen()
                || !translucentPass) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return false;
        }

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            return false;
        }

        int count = renderNothiriumTerrainLayer(
                BlockRenderLayer.TRANSLUCENT,
                mc.getRenderPartialTicks(),
                viewEntity
        );
        if (count < 0) {
            return false;
        }

        markNothiriumPipelineTranslucentBridge(BlockRenderLayer.TRANSLUCENT);
        recordTerrainLayerCount(BlockRenderLayer.TRANSLUCENT, count);
        return true;
    }

    private boolean shouldCancelDuplicateNothiriumTranslucentPass(boolean translucentPass) {
        return translucentPass && shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer.TRANSLUCENT);
    }

    private static boolean isNothiriumTranslucentPass(Object chunkRenderPass) {
        return chunkRenderPass instanceof Enum<?> pass && "TRANSLUCENT".equals(pass.name());
    }

    private int nothiriumFallbackBlockEntityId(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        int stillWater = blockEntityId(Blocks.WATER.getDefaultState());
        if (stillWater != 0) {
            return stillWater;
        }
        return blockEntityId(Blocks.FLOWING_WATER.getDefaultState());
    }

    private short nothiriumFallbackRenderType(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        return (short) Blocks.WATER.getDefaultState().getRenderType().ordinal();
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

    public boolean shouldRenderEntityWithVanillaProgram(Entity entity) {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        if (activePass.stage() != ProgramStage.GBUFFERS) {
            return false;
        }
        return isBetweenlandsEntity(EntityList.getKey(entity));
    }

    private static boolean isBetweenlandsEntity(ResourceLocation entityKey) {
        return entityKey != null && "thebetweenlands".equals(entityKey.getNamespace());
    }

    private static boolean isBetweenlandsRenderStack() {
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

    private int heldItemId(ItemStack stack) {
        return shaderProperties.itemIds().idFor(stack);
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

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        if (shaderItemId > 44000 && shaderItemId < 44100) {
            return 15;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        int blockLight = block != null ? block.getLightValue(block.getDefaultState()) : 0;
        if (blockLight > 0) {
            return blockLight;
        }

        return 0;
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
        currentEntityKey = entity != null ? EntityList.getKey(entity) : null;
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
        GlStateManager.depthMask(true);
    }

    public void restoreWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        GlStateManager.disableBlend();
        resetIndexedBlendState();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
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

        BlockPos position = renderChunk.getPosition();
        if (position == null) {
            resetChunkFadeUniform();
            return;
        }

        int dimensionId = safeDimensionId(renderChunk.getWorld());
        if (dimensionId == Integer.MIN_VALUE) {
            dimensionId = safeDimensionId(renderWorld(Minecraft.getMinecraft()));
        }
        applyChunkFade(dimensionId, position.getX(), position.getY(), position.getZ());
    }

    public void applyChunkFade(int blockX, int blockY, int blockZ) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = renderWorld(mc);
        applyChunkFade(safeDimensionId(world), blockX, blockY, blockZ);
    }

    private void applyChunkFade(int dimensionId, int blockX, int blockY, int blockZ) {
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

    private boolean shouldUploadChunkFade(BlockRenderLayer layer) {
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

    private boolean shouldSuppressChunkFadeForBetterPortals() {
        return BetterPortalsCompat.isInstalled()
                && (isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive());
    }

    private static boolean isChunkFadePass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WATER;
    }

    private float chunkFadeValue(int dimensionId, int blockX, int blockY, int blockZ) {
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

    private void uploadChunkFadeUniform() {
        ShaderProgram program = activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "mc_chunkFade");
        }
    }

    private void resetChunkFadeState(boolean warmExistingChunks) {
        chunkFadeStates.clear();
        currentChunkFade = 1.0f;
        chunkFadeWarmupUntilFrame = warmExistingChunks ? pipelineFrameId + CHUNK_FADE_WARMUP_FRAMES : pipelineFrameId;
    }

    private void pruneChunkFadeStates() {
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

    public void beginAstralConstellationPhase(Object constellation, WorldRenderingPhase phase) {
        setAstralConstellationColors(constellation);
        beginPhase(phase);
    }

    public void endAstralConstellationPhase() {
        endPass();
        resetAstralConstellationColors();
    }

    private void setAstralConstellationColors(Object constellation) {
        java.awt.Color tierColor = astralColor(constellation, "getTierRenderColor", java.awt.Color.WHITE);
        java.awt.Color constellationColor = astralColor(constellation, "getConstellationColor", tierColor);
        setColor(currentAstralConstellationColor, constellationColor);
        setColor(currentAstralTierColor, tierColor);
    }

    private void resetAstralConstellationColors() {
        setColor(currentAstralConstellationColor, null);
        setColor(currentAstralTierColor, null);
    }

    private static java.awt.Color astralColor(Object constellation, String methodName, java.awt.Color fallback) {
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

    private static void setColor(float[] target, java.awt.Color color) {
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
        applyOitDepthState(pass);
        applyGbufferDepthState(pass);
        applyHandRenderState(pass);
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
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_WATER
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK;
    }

    private boolean isMakeUpPack() {
        return activePackName.toLowerCase(java.util.Locale.ROOT).contains("makeup");
    }

    private void bindBlockAtlas() {
        TextureBinder.restoreDefaultTextureUnit();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getTextureManager() == null) {
            return;
        }
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

    public void applyNonZeroAlphaTestForCurrentPass() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }

        ShaderAlphaTest alphaTest = ShaderAlphaTest.NON_ZERO_ALPHA;
        currentAlphaTestReference = alphaTest.reference();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(alphaTest.function(), alphaTest.reference());

        ShaderProgram program = activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "alphaTestRef");
            uniformRegistry.upload(program, "iris_currentAlphaTest");
        }
    }

    private void applyBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        if (applyOitBlendMode(pass, drawBuffers)) {
            return;
        }

        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderBlendMode blendMode = pipelineProgram == null ? null : pipelineProgram.directives().blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = attachmentBlendModesFor(pass);
        if (pass == RenderPass.GBUFFERS_WATER) {
            applyWaterBlendMode(drawBuffers, blendMode == null ? WATER_BLEND_MODE : blendMode, attachmentModes);
            return;
        }
        if (blendMode == null) {
            blendMode = defaultBlendMode(pass);
        }
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }

        if (blendMode != null && !blendMode.enabled()) {
            GlStateManager.disableBlend();
            resetIndexedBlendState();
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

    private void applyWaterBlendMode(List<Attachment> drawBuffers, ShaderBlendMode blendMode, Map<Attachment, ShaderBlendMode> attachmentModes) {
        if (!blendMode.enabled()) {
            GlStateManager.disableBlend();
            resetIndexedBlendState();
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
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

    private static boolean defaultWaterBlendTarget(Attachment attachment) {
        return attachment == Attachment.COLOR || attachment == Attachment.COMPOSITE;
    }

    private static ShaderBlendMode defaultBlendMode(RenderPass pass) {
        if (pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT) {
            return ShaderBlendMode.OFF;
        }
        if (pass == RenderPass.SHADOW
                || pass == RenderPass.SHADOW_SOLID
                || pass == RenderPass.SHADOW_CUTOUT
                || pass == RenderPass.SHADOW_WATER
                || pass == RenderPass.SHADOW_ENTITIES
                || pass == RenderPass.SHADOW_LIGHTNING
                || pass == RenderPass.SHADOW_BLOCK) {
            return ShaderBlendMode.OFF;
        }
        if (pass == RenderPass.GBUFFERS_SPIDEREYES) {
            return new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        }
        return null;
    }

    private Map<Attachment, ShaderBlendMode> attachmentBlendModesFor(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        Map<Attachment, ShaderBlendMode> attachmentModes = pipelineProgram == null ? null : pipelineProgram.directives().attachmentBlendModes();
        return attachmentModes == null ? Map.of() : attachmentModes;
    }

    private boolean applyOitBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        if (!isOitGbufferPass(pass) || drawBuffers.isEmpty()) {
            return false;
        }

        ShaderOitSettings oitSettings = shaderProperties.oitSettings();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
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

    private void applyHandRenderState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_HAND) {
            return;
        }
        GlStateManager.disableBlend();
        resetIndexedBlendState();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
    }

    private void applyOitDepthState(RenderPass pass) {
        if (!isOitGbufferPass(pass)) {
            return;
        }
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
    }

    private void applyGbufferDepthState(RenderPass pass) {
        if (!isOpaqueTerrainPass(pass) && pass != RenderPass.GBUFFERS_WATER) {
            return;
        }
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GL11.glColorMask(true, true, true, true);
    }

    private static boolean isOpaqueTerrainPass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT;
    }

    private boolean isOitGbufferPass(RenderPass pass) {
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

    private static boolean isOitPhase(WorldRenderingPhase phase) {
        return switch (phase) {
            case TRIPWIRE, ENTITIES_TRANSLUCENT, BLOCK_ENTITIES_TRANSLUCENT,
                    PARTICLES_TRANSLUCENT, RAIN_SNOW, CLOUDS, LIGHTNING, BEACON_BEAM -> true;
            default -> false;
        };
    }

    private void applyIndexedBlendMode(int drawBufferIndex, ShaderBlendMode blendMode) {
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

        if (isOitGbufferPass(activePass)) {
            resetOitRenderState();
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
        if (mc == null || mc.world == null) {
            return;
        }
        resizeFramebuffer(width, height, true);
    }

    public void beginFrame() {
        if (!isPipelineActive) {
            externalWorldFramebufferTarget = null;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            externalWorldFramebufferTarget = null;
            return;
        }
        worldFrameActive = true;
        externalWorldFramebufferTarget = BetterPortalsCompat.currentShaderRenderPassFramebuffer();
        boolean betterPortalsExternalTarget = isBetterPortalsExternalWorldTarget();
        int targetWidth = worldTargetWidth(mc);
        int targetHeight = worldTargetHeight(mc);
        logBetterPortalsPipeline("begin-frame:target", "target=" + targetWidth + "x" + targetHeight
                + ", external=" + betterPortalsExternalTarget);
        if (pingPongManager.width() != targetWidth || pingPongManager.height() != targetHeight) {
            logBetterPortalsPipeline("begin-frame:resize", "old=" + pingPongManager.width() + "x" + pingPongManager.height()
                    + ", new=" + targetWidth + "x" + targetHeight);
            resizeFramebuffer(targetWidth, targetHeight, true);
        }

        boolean paused = mc.isGamePaused();
        if (betterPortalsExternalTarget) {
            currentFrameTime = 0.0f;
        } else {
            long now = System.nanoTime();
            currentFrameTime = paused ? 0.0f : Math.min(Math.max((now - lastPipelineFrameNanos) / 1_000_000_000.0f, 0.001f), 1.0f);
            lastPipelineFrameNanos = now;
            if (!paused) {
                pipelineFrameId++;
                frameTimeCounter += currentFrameTime;
                if (frameTimeCounter >= 3600.0f) {
                    frameTimeCounter = 0.0f;
                }
            }
        }
        deferredPassesRenderedThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        updateCameraPosition(mc);
        boolean resetTemporalHistory = shouldResetTemporalHistory(mc, paused, betterPortalsExternalTarget);
        if (paused || betterPortalsExternalTarget) {
            System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
            System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);
        } else {
            updateSmoothedFrameTime();
            updateSmoothedEyeBrightness(mc);
            updateSmoothedWetness(mc);
        }
        pingPongManager.beginFrame(frameClearAttachments(resetTemporalHistory));
        logTemporalHistoryResetIfNeeded(resetTemporalHistory);
        runSetupComputesIfNeeded();
        runFullscreenPasses(ProgramArrayId.BEGIN);
        bindWorldFramebuffer();
        logBetterPortalsPipeline("begin-frame:ready");
    }

    public void beginClientRenderFrame(long frameNanos) {
        boolean newFrame = frameNanos != clientRenderFrameNanos;
        if (newFrame) {
            clientRenderFrameNanos = frameNanos;
            bloomLayerRenderedThisWorldFrame = false;
            shaderlessBloomRenderedThisWorldFrame = false;
        }
    }

    public void beginWorldPassRendering(int pass, float partialTicks) {
        if (clientRenderFrameNanos == Long.MIN_VALUE) {
            beginClientRenderFrame(System.nanoTime());
        }
        beginWorldPassDuplicateTracking();
        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        bloomLayerRenderedThisWorldPass = bloomLayerRenderedThisWorldFrame;
        shaderlessBloomRenderedThisWorldPass = shaderlessBloomRenderedThisWorldFrame;
        boolean bypass = computeShouldBypassWorldPassRendering();
        worldPassBypassStack.push(bypass);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-begin bypass=" + bypass);
        logBetterPortalsPipeline("world-pass-begin", "pass=" + pass + ", bypass=" + bypass);
        if (bypass) {
            prepareBypassedWorldPassRendering();
            return;
        }

        if (!isPipelineActive) {
            beginShaderlessWorldPassRendering();
            return;
        }

        beginFrame();
    }

    public void finishWorldPassRendering() {
        boolean bypass = worldPassBypassStack.isEmpty()
                ? computeShouldBypassWorldPassRendering()
                : worldPassBypassStack.pop();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-finish bypass=" + bypass);
        logBetterPortalsPipeline("world-pass-finish", "bypass=" + bypass);
        try {
            if (bypass) {
                finishBypassedWorldPassRendering();
                return;
            }

            if (!isPipelineActive) {
                finishShaderlessWorldPassRendering();
                return;
            }

            renderNativeBloomLayerIfNeeded();
            blitWorldFramebufferToMinecraft();
        } finally {
            finishWorldPassDuplicateTracking();
        }
    }

    private void beginShaderlessWorldPassRendering() {
        prepareInactiveVanillaFrame();
        shaderlessWorldPassActive = true;
        restoreVanillaWorldPassState(true, true);
    }

    private void finishShaderlessWorldPassRendering() {
        restoreVanillaWorldPassState(false, true);
        shaderlessWorldPassActive = false;
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

    public void prepareInactiveVanillaFrame() {
        if (isPipelineActive || vanillaRecoveryFrames <= 0) {
            return;
        }
        vanillaRecoveryFrames--;
        resetPipelineState();
    }

    private void scheduleInactiveVanillaRecoveryFrame() {
        if (!isPipelineActive) {
            vanillaRecoveryFrames = Math.max(vanillaRecoveryFrames, 1);
        }
    }

    private void restoreVanillaWorldPassState(boolean bindMinecraftFramebuffer, boolean resetPortalMasks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (bindMinecraftFramebuffer && mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        }

        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();

        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        if (resetPortalMasks) {
            resetPortalMaskState();
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        restoreVanillaFixedFunctionTextureState(mc);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableBlend();
    }

    public void prepareVanillaParticleRendering() {
        if (isPipelineActive && !shouldBypassWorldPassRendering()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        restoreVanillaFixedFunctionTextureState(mc);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
    }

    private void restoreVanillaFixedFunctionTextureState(Minecraft mc) {
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.enableLightmap();
        } else {
            TextureBinder.restoreDefaultTextureUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        bindBlockAtlas();
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
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
        setupComputePending = true;
    }

    private Attachment[] frameClearAttachments(boolean forcePersistentClear) {
        if (forcePersistentClear) {
            return Attachment.values();
        }
        java.util.Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            if (!clearDisabled.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    private boolean shouldResetTemporalHistory(Minecraft mc, boolean paused, boolean betterPortalsExternalTarget) {
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        if (paused || betterPortalsExternalTarget || mc == null || mc.world == null || !pingPongManager.isInitialized()) {
            return false;
        }

        World world = renderWorld(mc);
        int dimensionId = safeDimensionId(world);
        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            resetTemporalHistoryTracking(dimensionId);
            temporalHistoryResetReason = "missing-view-entity";
            return true;
        }

        float yaw = interpolateAngle(viewEntity.prevRotationYaw, viewEntity.rotationYaw, currentWorldPartialTicks);
        float pitch = viewEntity.prevRotationPitch + (viewEntity.rotationPitch - viewEntity.prevRotationPitch) * currentWorldPartialTicks;
        float velocity = cameraVelocityMagnitude();

        if (!temporalHistoryInitialized) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "initial";
            return true;
        }

        float yawDelta = Math.abs(wrapDegrees(yaw - previousTemporalYaw));
        float pitchDelta = Math.abs(pitch - previousTemporalPitch);
        accumulatedTemporalYaw += yawDelta;
        accumulatedTemporalPitch += pitchDelta;

        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        temporalHistoryResetVelocity = velocity;
        temporalHistoryResetYaw = accumulatedTemporalYaw;
        temporalHistoryResetPitch = accumulatedTemporalPitch;

        if (dimensionId != temporalHistoryDimensionId) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "dimension";
            return true;
        }
        int recoveryDimensionId = BetterPortalsCompat.mainViewSwapRecoveryDimensionId();
        if (recoveryDimensionId != Integer.MIN_VALUE
                && recoveryDimensionId != mainViewSwapTemporalResetDimensionId) {
            mainViewSwapTemporalResetDimensionId = recoveryDimensionId;
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "betterportals-main-view-recovery";
            return true;
        }
        if (recoveryDimensionId == Integer.MIN_VALUE) {
            mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        }
        if (velocity > TEMPORAL_HISTORY_CAMERA_DELTA_RESET) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "camera-delta";
            return true;
        }
        if (accumulatedTemporalYaw > TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET
                || accumulatedTemporalPitch > TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "camera-rotation";
            return true;
        }
        return false;
    }

    private void resetTemporalHistoryTracking(int dimensionId) {
        resetTemporalHistoryTracking(dimensionId, 0.0f, 0.0f);
    }

    private void resetTemporalHistoryTracking(int dimensionId, float yaw, float pitch) {
        temporalHistoryInitialized = true;
        temporalHistoryDimensionId = dimensionId;
        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
    }

    private float cameraVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float y = cameraPosition[1] - previousCameraPosition[1];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    private static float interpolateAngle(float previous, float current, float partialTicks) {
        return previous + wrapDegrees(current - previous) * partialTicks;
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) {
            value -= 360.0f;
        }
        if (value < -180.0f) {
            value += 360.0f;
        }
        return value;
    }

    private void logTemporalHistoryResetIfNeeded(boolean resetTemporalHistory) {
        if (!resetTemporalHistory || temporalHistoryResetLogs >= MAX_TEMPORAL_HISTORY_RESET_LOGS) {
            return;
        }
        temporalHistoryResetLogs++;
        MainMod.LOGGER.info("[Pipeline] Reset temporal history: reason={} dimension={} velocity={} accumulatedYaw={} accumulatedPitch={} persistentAttachments={}",
                temporalHistoryResetReason,
                temporalHistoryDimensionId,
                temporalHistoryResetVelocity,
                temporalHistoryResetYaw,
                temporalHistoryResetPitch,
                packDirectives.renderTargets().clearDisabled());
    }

    private void requestPersistentHistoryClear(String reason) {
        if (packDirectives.renderTargets().clearDisabled().isEmpty()) {
            return;
        }
        pendingPersistentHistoryClear = true;
        pendingPersistentHistoryClearReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    private void clearPendingPersistentHistoryIfNeeded() {
        if (!pendingPersistentHistoryClear || !pingPongManager.isInitialized()) {
            return;
        }

        Attachment[] attachments = persistentHistoryAttachments();
        pendingPersistentHistoryClear = false;
        String reason = pendingPersistentHistoryClearReason;
        pendingPersistentHistoryClearReason = "";
        if (attachments.length == 0) {
            return;
        }

        pingPongManager.clear(attachments);
        pingPongManager.clearWrite(attachments);
        bindWorldFramebuffer();
        if (persistentHistoryClearLogs < MAX_TERRAIN_HISTORY_CLEAR_LOGS) {
            persistentHistoryClearLogs++;
            MainMod.LOGGER.info("[Pipeline] Cleared persistent history before deferred passes: reason={} attachments={}",
                    reason,
                    java.util.Arrays.toString(attachments));
        }
    }

    private Attachment[] persistentHistoryAttachments() {
        java.util.Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        if (clearDisabled.isEmpty()) {
            return new Attachment[0];
        }

        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : clearDisabled) {
            // COLOR contains the current gbuffer terrain for this frame; clearing it mid-frame
            // causes the white/blank terrain this recovery is meant to avoid.
            if (attachment != Attachment.COLOR) {
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

    public boolean shouldRenderShadowMapBeforeTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return !shouldUseNothiriumShadowBridge() && !shouldReuseMainTerrainForShadowMap();
    }

    public boolean shouldRenderShadowMapAfterTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return shouldReuseMainTerrainForShadowMap();
    }

    public boolean shouldRenderShadowMapAfterOpaqueTerrain() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return shouldUseNothiriumShadowBridge();
    }

    private boolean shouldUseNothiriumShadowBridge() {
        return NothiriumShadowRenderer.isAvailable() && !NothiriumBypass.shouldBypass();
    }

    private boolean shouldReuseMainTerrainForShadowMap() {
        return NothiriumShadowRenderer.isAvailable() && NothiriumBypass.shouldBypass();
    }

    public void ensureVanillaTerrainRenderer() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        ensureVanillaTerrainRenderer(world != null ? world : (mc != null ? mc.world : null));
    }

    private void pushVanillaTerrainRendererState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null || !(mc.renderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            vanillaViewFrustumStateStack.push(new Object[]{null, null});
            return;
        }

        vanillaViewFrustumStateStack.push(new Object[]{mc.renderGlobal, renderGlobal.ausm$viewFrustum()});
    }

    private void popVanillaTerrainRendererState() {
        Object[] state = vanillaViewFrustumStateStack.poll();
        if (state == null || state.length < 2 || !(state[0] instanceof RenderGlobal savedRenderGlobal)
                || !(savedRenderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }
        ViewFrustum savedViewFrustum = state[1] instanceof ViewFrustum viewFrustum ? viewFrustum : null;

        Minecraft mc = Minecraft.getMinecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive() && mc != null && mc.world != null) {
            ensureVanillaTerrainRenderer(mc.world, true);
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            return;
        }

        if (savedViewFrustum == null) {
            if (mc != null && mc.world != null && renderGlobal.ausm$viewFrustum() == null) {
                ensureVanillaTerrainRenderer(mc.world, true);
            }
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            return;
        }

        if (renderGlobal.ausm$viewFrustum() != savedViewFrustum) {
            renderGlobal.ausm$setViewFrustum(savedViewFrustum);
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
    }

    public void ensureVanillaTerrainRenderer(World world) {
        ensureVanillaTerrainRenderer(world, false);
    }

    public void ensureRenderGlobalViewFrustum(RenderGlobal renderGlobal) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || accessor.ausm$viewFrustum() != null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.renderGlobal != renderGlobal) {
            return;
        }

        logTerrainDiagnostic("ensure-render-global-view-frustum", mc.world, "missing-view-frustum=true");
        ensureVanillaTerrainRenderer(mc.world, true);
    }

    public void updateShaderlessVanillaViewFrustumForCamera() {
        if (!shouldSyncShaderlessVanillaViewFrustumForCamera()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null
                || mc.world == null
                || mc.renderGlobal == null
                || !(mc.renderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null && renderPassWorld != mc.world) {
            return;
        }

        boolean worldChanged = false;
        World renderGlobalWorld = renderGlobal.ausm$world();
        if (renderGlobalWorld != null && renderGlobalWorld != mc.world) {
            worldChanged = syncRenderGlobalWorld(mc.renderGlobal, mc.world);
        }
        ensureVanillaTerrainRenderer(mc.world, false);

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            ensureVanillaTerrainRenderer(mc.world, true);
            viewFrustum = renderGlobal.ausm$viewFrustum();
        }
        if (viewFrustum == null) {
            return;
        }

        Entity viewEntity = mc.getRenderViewEntity();
        forceUpdateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity, mc.world, "camera-frustum-sync");
        logCameraFrustumSyncIfChanged(mc.world, viewFrustum, viewEntity, renderPassWorld != null, worldChanged);
    }

    private boolean shouldSyncShaderlessVanillaViewFrustumForCamera() {
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    private void logCameraFrustumSyncIfChanged(World world, ViewFrustum viewFrustum, Entity viewEntity,
                                               boolean renderPass, boolean worldChanged) {
        if (world == null || viewFrustum == null || viewEntity == null) {
            return;
        }

        int chunkX = (int) Math.floor(viewEntity.posX) >> 4;
        int chunkZ = (int) Math.floor(viewEntity.posZ) >> 4;
        if (lastCameraFrustumSyncWorld == world
                && lastCameraFrustumSyncViewFrustum == viewFrustum
                && lastCameraFrustumSyncChunkX == chunkX
                && lastCameraFrustumSyncChunkZ == chunkZ
                && !worldChanged) {
            return;
        }

        lastCameraFrustumSyncWorld = world;
        lastCameraFrustumSyncViewFrustum = viewFrustum;
        lastCameraFrustumSyncChunkX = chunkX;
        lastCameraFrustumSyncChunkZ = chunkZ;
        if (cameraFrustumSyncLogs >= MAX_CAMERA_FRUSTUM_SYNC_LOGS) {
            return;
        }

        cameraFrustumSyncLogs++;
        MainMod.LOGGER.info(
                "[AUSMFrustumSync] call={} world={} chunk={},{} viewFrustum={} renderPass={} worldChanged={} bp={}",
                cameraFrustumSyncLogs,
                safeDimensionId(world),
                chunkX,
                chunkZ,
                viewFrustumId(viewFrustum),
                renderPass,
                worldChanged,
                BetterPortalsCompat.describeTransitionState()
        );
    }

    public void handleBetterPortalsMainViewSwap() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }

        logTerrainDiagnostic("bp-main-view-swap:start", mc.world, "");
        if (!isPipelineActive) {
            BetterPortalsCompat.clearMainViewSwapTransientState();
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            clearScheduledWorldTerrainRefresh();
            recoverShaderlessMainWorldTerrain(mc, "bp-main-view-swap");
            logInactiveBetterPortalsTerrainSkip("main-view-swap", mc.world);
            return;
        }

        boolean terrainTransition = beginTerrainTransition(mc.world);
        logTerrainDiagnostic("bp-main-view-swap:transition", mc.world, "accepted=" + terrainTransition);
        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            refreshBetterPortalsMainViewTerrain(mc);
            if (terrainTransition) {
                BetterPortalsCompat.startMainViewSwapRecovery(mc.world);
                BetterPortalsCompat.logMainViewSwapRecoveryIfNeeded(mc.world);
                ensureVanillaTerrainRenderer(mc.world, false);
            }
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
        logTerrainDiagnostic("bp-main-view-swap:end", mc.world, "accepted=" + terrainTransition);
    }

    public void handleWorldDimensionSwitch(int previousDimensionId, int dimensionId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }

        logTerrainDiagnostic("dimension-switch:start", mc.world, "previous=" + previousDimensionId + ", current=" + dimensionId);
        BetterPortalsCompat.clearMainViewSwapTransientState();
        if (!isPipelineActive) {
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            clearPendingShaderChunkRefreshes();
            clearPendingClientChunkRenderRefreshes();
            clearPendingBetterPortalsPortalBlockRefresh();
            clearScheduledWorldTerrainRefresh();
            clearScheduledBloomTerrainRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            recoverShaderlessMainWorldTerrain(mc, "dimension-switch");
            logInactiveBetterPortalsTerrainSkip("dimension-switch", mc.world);
            return;
        }

        boolean terrainTransition = beginTerrainTransition(mc.world);
        if (!terrainTransition) {
            clearPendingShaderChunkRefreshes();
            clearPendingBetterPortalsPortalBlockRefresh();
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic("dimension-switch:debounced", mc.world, "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        clearPendingBetterPortalsPortalBlockRefresh();
        resetPipelineState(mc.getFramebuffer());
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;
        if (mc.renderGlobal instanceof RenderGlobalAccessor accessor) {
            clearRenderGlobalChunkUpdates(accessor);
        }

        boolean betterPortalsRecovery = BetterPortalsCompat.isMainViewSwapRecoveryActive();
        if (betterPortalsRecovery) {
            refreshBetterPortalsMainViewTerrain(mc);
        } else {
            BetterPortalsCompat.startMainViewSwapRecovery(mc.world);
        }
        scheduleDimensionSwitchTerrainRefresh();
        scheduleBloomTerrainRefresh("dimension switch");
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        logTerrainDiagnostic("dimension-switch:scheduled", mc.world, "previous=" + previousDimensionId + ", current=" + dimensionId
                + ", bpRecoveryWasActive=" + betterPortalsRecovery);
    }

    private void recoverShaderlessMainWorldTerrain(Minecraft mc, String reason) {
        if (mc == null || mc.world == null) {
            return;
        }
        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            recoverShaderlessVanillaOwnerTerrain(mc, reason);
            return;
        }

        boolean hardReset = shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            clearCachedVanillaTerrainRendererReferences();
        }

        boolean ready = NothiriumBypass.ensureRendererReady();
        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && (marked || ready) && NothiriumBypass.setupForIsolatedShaderlessMainPass();

        if (mc.renderGlobal != null) {
            adoptMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, reason);
        }

        if (marked || ready || setup) {
            scheduleInactiveVanillaRecoveryFrame();
        }
        scheduleWorldLoadLightRecalculation();
        logShaderlessNothiriumLoadRendererReload(mc.world, marked, reason);
        logTerrainDiagnostic(reason + ":shaderless-recover", mc.world,
                "ready=" + ready + ", marked=" + marked + ", setup=" + setup + ", hardReset=" + hardReset);
    }

    private void recoverShaderlessVanillaOwnerTerrain(Minecraft mc, String reason) {
        if (mc == null || mc.world == null) {
            return;
        }

        boolean transitionReset = shouldHardResetShaderlessNothirium(reason);
        if (transitionReset && mc.renderGlobal != null) {
            rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, reason + "-vanilla-owner");
            resetCameraFrustumSyncState();
            scheduleInactiveVanillaRecoveryFrame();
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-rebuild", mc.world, "");
        } else if (mc.renderGlobal != null) {
            adoptMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, reason + "-vanilla-owner");
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-adopt", mc.world, "");
        } else {
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-missing-render-global", mc.world, "");
        }

        scheduleWorldLoadLightRecalculation();
    }

    private void resetCameraFrustumSyncState() {
        lastCameraFrustumSyncWorld = null;
        lastCameraFrustumSyncViewFrustum = null;
        lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
        lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
    }

    private boolean beginTerrainTransition(World world) {
        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        long elapsed = now - lastTerrainTransitionMillis;
        if (world != null
                && lastTerrainTransitionDimension == dimension
                && elapsed >= 0L
                && elapsed < WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS) {
            logTerrainDiagnostic("terrain-transition:debounced", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
            return false;
        }

        lastTerrainTransitionWorld = world;
        lastTerrainTransitionDimension = dimension;
        lastTerrainTransitionMillis = now;
        logTerrainDiagnostic("terrain-transition:accepted", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
        return true;
    }

    public void queueBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }
        if (sameBlockState(oldState, newState)) {
            return;
        }
        if (shouldDebounceBetterPortalsPortalBlockRefresh(world, pos)) {
            logTerrainDiagnostic("bp-portal-block:debounced", world, "pos=" + pos
                    + ", old=" + blockName(oldState)
                    + ", new=" + blockName(newState));
            return;
        }

        pendingBetterPortalsPortalBlockWorld = world;
        pendingBetterPortalsPortalBlockPos = pos.toImmutable();
        pendingBetterPortalsPortalBlockOldState = oldState;
        pendingBetterPortalsPortalBlockNewState = newState;
        pendingBetterPortalsPortalBlockChangeCount++;
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            pendingBetterPortalsPortalBlockRefreshDelay = 3;
        }
        logTerrainDiagnostic("bp-portal-block:queued", world, "pos=" + pos
                + ", count=" + pendingBetterPortalsPortalBlockChangeCount
                + ", old=" + blockName(oldState)
                + ", new=" + blockName(newState));
    }

    private void clearPendingBetterPortalsPortalBlockRefresh() {
        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
    }

    public void runPendingBetterPortalsPortalBlockRefresh() {
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            return;
        }
        if (pendingBetterPortalsPortalBlockRefreshDelay > 0) {
            pendingBetterPortalsPortalBlockRefreshDelay--;
            return;
        }

        World world = pendingBetterPortalsPortalBlockWorld;
        BlockPos pos = pendingBetterPortalsPortalBlockPos;
        IBlockState oldState = pendingBetterPortalsPortalBlockOldState;
        IBlockState newState = pendingBetterPortalsPortalBlockNewState;
        int changeCount = pendingBetterPortalsPortalBlockChangeCount;

        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;

        handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, changeCount);
    }

    public void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, 1);
    }

    private void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int changeCount) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.renderGlobal == null) {
            return;
        }

        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            rememberBetterPortalsPortalBlockRefresh(world, pos);
            markPortalChangeRenderRegion(world, pos);
            logTerrainDiagnostic("bp-portal-block:refresh", world, "pos=" + pos
                    + ", count=" + Math.max(1, changeCount)
                    + ", old=" + blockName(oldState)
                    + ", new=" + blockName(newState));
            MainMod.LOGGER.debug("[BetterPortalsCompat] Refreshed portal terrain after {} coalesced block change(s): world={} pos={} old={} new={}",
                    Math.max(1, changeCount),
                    safeDimensionId(world),
                    pos,
                    oldState != null ? oldState.getBlock().getRegistryName() : "null",
                    newState != null ? newState.getBlock().getRegistryName() : "null");
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh portal terrain after block change", e);
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
    }

    private boolean sameBlockState(IBlockState oldState, IBlockState newState) {
        return oldState == newState || (oldState != null && oldState.equals(newState));
    }

    private boolean shouldDebounceBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        return lastBetterPortalsPortalBlockRefreshWorld == world
                && lastBetterPortalsPortalBlockRefreshDimension == safeDimensionId(world)
                && pos.equals(lastBetterPortalsPortalBlockRefreshPos)
                && now - lastBetterPortalsPortalBlockRefreshMillis >= 0L
                && now - lastBetterPortalsPortalBlockRefreshMillis < BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS;
    }

    private void rememberBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        lastBetterPortalsPortalBlockRefreshWorld = world;
        lastBetterPortalsPortalBlockRefreshPos = pos != null ? pos.toImmutable() : null;
        lastBetterPortalsPortalBlockRefreshDimension = safeDimensionId(world);
        lastBetterPortalsPortalBlockRefreshMillis = System.currentTimeMillis();
    }

    private void markPortalChangeRenderRegion(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }

        int radius = 8;
        world.markBlockRangeForRenderUpdate(
                pos.getX() - radius,
                Math.max(0, pos.getY() - radius),
                pos.getZ() - radius,
                pos.getX() + radius,
                Math.min(255, pos.getY() + radius),
                pos.getZ() + radius
        );
    }

    private void ensureVanillaTerrainRenderer(World world, boolean force) {
        boolean bypass = NothiriumBypass.shouldBypass();
        if (!force && !bypass) {
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:skip", world, "force=false, nothiriumBypass=false");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || world == null || mc.renderGlobal == null) {
            return;
        }

        logSteadyVanillaTerrainDiagnostic("ensure-vanilla:start", world, "force=" + force + ", nothiriumBypass=" + bypass);
        RenderGlobal currentRenderGlobal = mc.renderGlobal;
        RenderGlobalAccessor renderGlobal = (RenderGlobalAccessor) currentRenderGlobal;
        boolean rendererStateChanged = syncRenderGlobalWorld(currentRenderGlobal, world);
        ViewFrustum activeViewFrustum = renderGlobal.ausm$viewFrustum();

        boolean useVbo = OpenGlHelper.useVbo();
        if (renderGlobal.ausm$renderDispatcher() == null) {
            logVanillaTerrainRendererCreation(world, force, "missing-dispatcher");
            renderGlobal.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
            rendererStateChanged = true;
        }
        IRenderChunkFactory renderChunkFactory = renderGlobal.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            renderGlobal.ausm$setRenderChunkFactory(renderChunkFactory);
            rendererStateChanged = true;
        }
        if (renderGlobal.ausm$renderContainer() == null) {
            renderGlobal.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
            rendererStateChanged = true;
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        Map<World, Integer> rendererViewFrustumDistances = vanillaViewFrustumRenderDistances.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum viewFrustum = rendererViewFrustums.get(world);
        Integer cachedRenderDistanceChunks = rendererViewFrustumDistances.get(world);
        int requestedRenderDistanceChunks = mc.gameSettings.renderDistanceChunks;
        int renderDistanceChunks = vanillaTerrainRenderDistanceChunks(
                world,
                cachedRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (viewFrustum != null && cachedRenderDistanceChunks != null && cachedRenderDistanceChunks != renderDistanceChunks) {
            viewFrustum.deleteGlResources();
            rendererViewFrustums.remove(world);
            rendererViewFrustumDistances.remove(world);
            viewFrustum = null;
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal && activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
            }
            rendererStateChanged = true;
            MainMod.LOGGER.info("[Pipeline] Rebuilt vanilla terrain renderer for render distance change: world={} old={} new={} requested={}",
                    safeDimensionId(world),
                    cachedRenderDistanceChunks,
                    renderDistanceChunks,
                    requestedRenderDistanceChunks);
        }
        if (viewFrustum == null) {
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal
                    && activeVanillaViewFrustumWorld == world
                    && activeViewFrustum != null) {
                viewFrustum = activeViewFrustum;
            } else {
                viewFrustum = new ViewFrustum(
                        world,
                        renderDistanceChunks,
                        mc.renderGlobal,
                        renderChunkFactory
                );
            }
            rendererViewFrustums.put(world, viewFrustum);
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
            rendererStateChanged = true;
        } else if (cachedRenderDistanceChunks == null) {
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
        }

        updateVanillaViewFrustumChunkPositions(viewFrustum, mc.getRenderViewEntity());
        if (activeViewFrustum != viewFrustum) {
            renderGlobal.ausm$setViewFrustum(viewFrustum);
            rendererStateChanged = true;
        }
        activeVanillaViewFrustumRenderGlobal = currentRenderGlobal;
        activeVanillaViewFrustumWorld = world;
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        if (rendererStateChanged) {
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        String detail = "force=" + force
                + ", activeViewBefore=" + viewFrustumId(activeViewFrustum)
                + ", activeViewAfter=" + viewFrustumId(renderGlobal.ausm$viewFrustum())
                + ", cachedView=" + viewFrustumId(viewFrustum)
                + ", renderDistance=" + renderDistanceChunks
                + (requestedRenderDistanceChunks != renderDistanceChunks
                        ? ", requestedRenderDistance=" + requestedRenderDistanceChunks
                        : "");
        if (rendererStateChanged) {
            logTerrainDiagnostic("ensure-vanilla:changed", world, detail);
        } else {
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:unchanged", world, detail);
        }
    }

    private int vanillaTerrainRenderDistanceChunks(World world, Integer cachedRenderDistanceChunks,
                                                   int requestedRenderDistanceChunks) {
        if (shouldUseStableMainWorldRenderDistance(world)) {
            if (cachedRenderDistanceChunks != null && cachedRenderDistanceChunks > 0) {
                return cachedRenderDistanceChunks;
            }
            if (lastStableMainWorldVanillaRenderDistanceChunks > 0) {
                return lastStableMainWorldVanillaRenderDistanceChunks;
            }
        }
        return requestedRenderDistanceChunks;
    }

    private boolean shouldUseStableMainWorldRenderDistance(World world) {
        Minecraft mc = Minecraft.getMinecraft();
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && mc != null
                && mc.world != null
                && world == mc.world;
    }

    private void rememberStableMainWorldVanillaRenderDistance(World world, int renderDistanceChunks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || world != mc.world || renderDistanceChunks <= 0) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }
        lastStableMainWorldVanillaRenderDistanceChunks = renderDistanceChunks;
    }

    public void handleRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        handleShaderlessMainWorldNothiriumReload(renderGlobal);
        logRenderGlobalLoadRenderers(renderGlobal);
    }

    public void handleRenderGlobalLoadRenderersComplete(RenderGlobal renderGlobal) {
        Minecraft mc = Minecraft.getMinecraft();
        String caller = externalRenderCaller();
        boolean manualChunkReload = isManualChunkReloadCaller(caller);
        if (renderGlobal == null
                || mc == null
                || mc.world == null
                || mc.renderGlobal != renderGlobal
                || !isStableMainWorldLoadRenderersCaller(caller)
                || isPipelineActive
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != mc.world) {
            return;
        }

        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            if (manualChunkReload) {
                rebuildMainWorldVanillaViewFrustum(renderGlobal, mc.world, "manual-reload-vanilla-owner");
            } else {
                adoptMainWorldVanillaViewFrustum(renderGlobal, mc.world, "main-load-vanilla-owner");
            }
            scheduleWorldLoadLightRecalculation();
            return;
        }

        adoptMainWorldVanillaViewFrustum(renderGlobal, mc.world, manualChunkReload ? "manual-reload" : "main-load");
        markShaderlessMainWorldNothiriumReload(mc.world, manualChunkReload ? "manual-load-renderers" : "main-load-renderers");
        scheduleInactiveVanillaRecoveryFrame();
    }

    private void handleShaderlessMainWorldNothiriumReload(RenderGlobal renderGlobal) {
        Minecraft mc = Minecraft.getMinecraft();
        String caller = externalRenderCaller();
        if (renderGlobal == null
                || mc == null
                || mc.world == null
                || mc.renderGlobal != renderGlobal
                || !isManualChunkReloadCaller(caller)
                || isPipelineActive
                || NothiriumBypass.shouldBypass()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != mc.world) {
            return;
        }

        markShaderlessMainWorldNothiriumReload(mc.world, "manual-load-renderers");
    }

    private void markShaderlessMainWorldNothiriumReload(World world, String reason) {
        if (world == null) {
            return;
        }
        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            return;
        }

        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        boolean debounced = dimension == lastShaderlessNothiriumLoadRendererReloadDimension
                && now - lastShaderlessNothiriumLoadRendererReloadMillis < 1000L;
        if (debounced) {
            logShaderlessNothiriumLoadRendererReload(world, false, "debounced");
            return;
        }

        lastShaderlessNothiriumLoadRendererReloadDimension = dimension;
        lastShaderlessNothiriumLoadRendererReloadMillis = now;
        boolean hardReset = shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            clearCachedVanillaTerrainRendererReferences();
        }

        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && marked && NothiriumBypass.setupForIsolatedShaderlessMainPass();
        if (marked || setup) {
            scheduleInactiveVanillaRecoveryFrame();
        }
        logShaderlessNothiriumLoadRendererReload(world, marked, reason);
        if (setup) {
            logTerrainDiagnostic(reason + ":shaderless-reload-setup", world, "marked=" + marked);
        }
    }

    private boolean shouldHardResetShaderlessNothirium(String reason) {
        if (!BetterPortalsCompat.isInstalled() || isPipelineActive || reason == null) {
            return false;
        }
        return "dimension-switch".equals(reason)
                || "bp-main-view-swap".equals(reason)
                || "manual-load-renderers".equals(reason);
    }

    private boolean shouldLeaveShaderlessVanillaTerrainUntouched() {
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    private static boolean isManualChunkReloadCaller(String caller) {
        return caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_184122_c:");
    }

    private static boolean isStableMainWorldLoadRenderersCaller(String caller) {
        return isManualChunkReloadCaller(caller)
                || caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_71353_a:");
    }

    private void adoptMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int renderDistanceChunks = mc != null && mc.gameSettings != null ? mc.gameSettings.renderDistanceChunks : -1;
        ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
        if (viewFrustum == null) {
            ensureVanillaTerrainRenderer(world, true);
            viewFrustum = accessor.ausm$viewFrustum();
            if (viewFrustum == null) {
                deleteCachedVanillaTerrainRenderer(world);
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                logTerrainDiagnostic(stagePrefix + ":missing-view-frustum", world, "");
                return;
            }
            logTerrainDiagnostic(stagePrefix + ":created-view-frustum", world,
                    "current=" + viewFrustumId(viewFrustum)
                            + ", renderDistance=" + renderDistanceChunks);
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                renderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum previous = rendererViewFrustums.put(world, viewFrustum);
        if (previous != null && previous != viewFrustum) {
            previous.deleteGlResources();
        }

        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        if (mc != null) {
            updateVanillaViewFrustumChunkPositions(viewFrustum, mc.getRenderViewEntity());
        }
        accessor.ausm$setDisplayListEntitiesDirty(true);
        logTerrainDiagnostic(stagePrefix + ":adopt-view-frustum", world,
                "previous=" + viewFrustumId(previous)
                        + ", current=" + viewFrustumId(viewFrustum)
                        + ", renderDistance=" + renderDistanceChunks);
    }

    private void rebuildMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }

        boolean worldChanged = syncRenderGlobalWorld(renderGlobal, world);
        boolean useVbo = OpenGlHelper.useVbo();
        if (accessor.ausm$renderDispatcher() == null) {
            accessor.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
        }
        IRenderChunkFactory renderChunkFactory = accessor.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            accessor.ausm$setRenderChunkFactory(renderChunkFactory);
        }
        if (accessor.ausm$renderContainer() == null) {
            accessor.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
        }

        int renderDistanceChunks = mc.gameSettings.renderDistanceChunks;
        ViewFrustum previousActive = accessor.ausm$viewFrustum();
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();
        if (previousActive != null) {
            removedViewFrustums.add(previousActive);
        }
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                removedViewFrustums.add(removed);
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }

        ViewFrustum freshViewFrustum = new ViewFrustum(
                world,
                renderDistanceChunks,
                renderGlobal,
                renderChunkFactory
        );
        accessor.ausm$setViewFrustum(freshViewFrustum);

        vanillaViewFrustums
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, freshViewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;

        int scheduledChunks = scheduleAllFreshViewFrustumChunks(accessor, freshViewFrustum, world);
        forceUpdateVanillaViewFrustumChunkPositions(freshViewFrustum, mc.getRenderViewEntity(), world, stagePrefix);
        accessor.ausm$setDisplayListEntitiesDirty(true);

        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            if (removedViewFrustum != null && removedViewFrustum != freshViewFrustum) {
                removedViewFrustum.deleteGlResources();
            }
        }

        logTerrainDiagnostic(stagePrefix + ":rebuild-view-frustum", world,
                "previous=" + viewFrustumId(previousActive)
                        + ", current=" + viewFrustumId(freshViewFrustum)
                        + ", renderDistance=" + renderDistanceChunks
                        + ", scheduledChunks=" + scheduledChunks
                        + ", worldChanged=" + worldChanged);
    }

    private int scheduleAllFreshViewFrustumChunks(RenderGlobalAccessor renderGlobal, ViewFrustum viewFrustum, World world) {
        if (renderGlobal == null || viewFrustum == null || viewFrustum.renderChunks == null) {
            return 0;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return 0;
        }

        chunksToUpdate.clear();
        int scheduled = 0;
        for (RenderChunk renderChunk : viewFrustum.renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            assignRenderChunkWorld(renderChunk, world);
            renderChunk.setNeedsUpdate(true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
        }
        return scheduled;
    }

    private void forceUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity, World world, String stagePrefix) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        try {
            viewFrustum.updateChunkPositions(viewEntity.posX, viewEntity.posZ);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            logTerrainDiagnostic(stagePrefix + ":deferred-chunk-positions", world, e.getClass().getSimpleName());
        }
    }

    private void logSteadyVanillaTerrainDiagnostic(String stage, World world, String detail) {
        if (steadyVanillaTerrainDiagnosticLogs >= MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS) {
            return;
        }
        steadyVanillaTerrainDiagnosticLogs++;
        logTerrainDiagnostic(stage, world, detail);
    }

    private void logShaderlessNothiriumLoadRendererReload(World world, boolean marked, String reason) {
        if (shaderlessNothiriumLoadRendererReloadLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        shaderlessNothiriumLoadRendererReloadLogs++;

        MainMod.LOGGER.info(
                "[AUSMNothiriumReload] loadRenderers bridge call={} reason={} world={} marked={} active={} bypass={} nested={} renderPass={} caller={}",
                shaderlessNothiriumLoadRendererReloadLogs,
                reason,
                safeDimensionId(world),
                marked,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                externalRenderCaller()
        );
    }

    public void logRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        if (renderGlobalLoadRendererLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        renderGlobalLoadRendererLogs++;

        Minecraft mc = Minecraft.getMinecraft();
        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] loadRenderers call={} frame={} renderGlobalWorld={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} pendingAttempts={} pendingDelay={} pendingDim={} pendingReset={} pendingFullReset={} pendingVanillaReload={} bpState={} caller={}",
                renderGlobalLoadRendererLogs,
                pipelineFrameId,
                safeDimensionId(renderGlobalWorld),
                mc != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                pendingWorldTerrainRefreshAttempts,
                pendingWorldTerrainRefreshDelay,
                pendingWorldTerrainRefreshDimension,
                pendingWorldTerrainRendererReset,
                pendingWorldTerrainFullRendererReset,
                pendingWorldTerrainVanillaReload,
                BetterPortalsCompat.describeTransitionState(),
                externalRenderCaller()
        );
    }

    private void logTerrainDiagnostic(String stage, World world, String detail) {
        if (terrainDiagnosticLogs >= MAX_TERRAIN_DIAGNOSTIC_LOGS) {
            return;
        }
        terrainDiagnosticLogs++;

        Minecraft mc = Minecraft.getMinecraft();
        World clientWorld = mc != null ? mc.world : null;
        RenderGlobal renderGlobalInstance = mc != null ? mc.renderGlobal : null;
        World renderGlobalWorld = renderGlobalInstance instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        ViewFrustum viewFrustum = renderGlobalInstance instanceof RenderGlobalAccessor accessor ? accessor.ausm$viewFrustum() : null;
        MainMod.LOGGER.info(
                "[AUSMTerrainDiag] call={} stage={} detail={} frame={} world={} clientWorld={} renderGlobalWorld={} active={} pendingAttempts={} pendingDelay={} pendingDim={} pendingReset={} pendingFullReset={} pendingVanillaReload={} viewFrustum={} activeCachedWorld={} bypass={} bp={} caller={}",
                terrainDiagnosticLogs,
                stage,
                detail != null ? detail : "",
                pipelineFrameId,
                safeDimensionId(world),
                safeDimensionId(clientWorld),
                safeDimensionId(renderGlobalWorld),
                isPipelineActive,
                pendingWorldTerrainRefreshAttempts,
                pendingWorldTerrainRefreshDelay,
                pendingWorldTerrainRefreshDimension,
                pendingWorldTerrainRendererReset,
                pendingWorldTerrainFullRendererReset,
                pendingWorldTerrainVanillaReload,
                viewFrustumId(viewFrustum),
                safeDimensionId(activeVanillaViewFrustumWorld),
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.describeTransitionState(),
                externalRenderCaller()
        );
    }

    private void logVanillaTerrainRendererCreation(World world, boolean force, String reason) {
        if (vanillaTerrainRendererCreationLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        vanillaTerrainRendererCreationLogs++;

        Minecraft mc = Minecraft.getMinecraft();
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] created vanilla ChunkRenderDispatcher call={} reason={} force={} world={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} caller={}",
                vanillaTerrainRendererCreationLogs,
                reason,
                force,
                safeDimensionId(world),
                mc != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                externalRenderCaller()
        );
    }

    private static String viewFrustumId(ViewFrustum viewFrustum) {
        return viewFrustum != null ? Integer.toHexString(System.identityHashCode(viewFrustum)) : "null";
    }

    private static String blockName(IBlockState state) {
        return state != null && state.getBlock() != null ? String.valueOf(state.getBlock().getRegistryName()) : "null";
    }

    private String externalRenderCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(PipelineContext.class.getName())
                    || className.equals("com.l.ausm.impl.mixin.pipeline.RenderSkyMixin")
                    || className.equals("net.minecraft.client.renderer.RenderGlobal")) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    private void updateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        if (BetterPortalsCompat.isMainViewSwapHandling()) {
            return;
        }

        if (BetterPortalsCompat.isInstalled() && !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        try {
            viewFrustum.updateChunkPositions(viewEntity.posX, viewEntity.posZ);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            if (!betterPortalsViewFrustumUpdateWarningLogged) {
                betterPortalsViewFrustumUpdateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Deferred vanilla ViewFrustum chunk-position update because Better Portals has no active render pass", e);
            }
        }
    }

    private void deleteCachedVanillaTerrainRenderers() {
        if (vanillaViewFrustums.isEmpty()) {
            vanillaViewFrustumRenderDistances.clear();
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            return;
        }

        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            uniqueViewFrustums.addAll(rendererViewFrustums.values());
        }
        for (ViewFrustum viewFrustum : uniqueViewFrustums) {
            if (viewFrustum != null) {
                viewFrustum.deleteGlResources();
            }
        }
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
    }

    private void clearCachedVanillaTerrainRendererReferences() {
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
    }

    private void deleteCachedVanillaTerrainRenderer(World world) {
        if (world == null || vanillaViewFrustums.isEmpty()) {
            if (world == null) {
                vanillaViewFrustumRenderDistances.clear();
            }
            if (activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
            }
            return;
        }

        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                removed.deleteGlResources();
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }
        if (activeVanillaViewFrustumWorld == world) {
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
        }
    }

    private void refreshBetterPortalsMainViewTerrain(Minecraft mc) {
        if (mc == null || mc.world == null || mc.renderGlobal == null) {
            return;
        }
        if (!isPipelineActive) {
            logInactiveBetterPortalsTerrainSkip("refresh-main-view-terrain", mc.world);
            return;
        }

        try {
            logTerrainDiagnostic("bp-refresh-main-view:start", mc.world, "");
            boolean worldChanged = syncRenderGlobalWorld(mc.renderGlobal, mc.world);
            adoptCurrentRenderGlobalViewFrustum(mc.world);
            logTerrainDiagnostic("bp-refresh-main-view:end", mc.world, "worldChanged=" + worldChanged);
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh terrain after main view swap", e);
        }
    }

    private void adoptCurrentRenderGlobalViewFrustum(World world) {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null
                || world == null
                || mc.renderGlobal == null
                || !(mc.renderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            logTerrainDiagnostic("adopt-view-frustum:missing", world, "");
            return;
        }

        vanillaViewFrustums
                .computeIfAbsent(mc.renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, viewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(mc.renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, mc.gameSettings.renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, mc.gameSettings.renderDistanceChunks);
        activeVanillaViewFrustumRenderGlobal = mc.renderGlobal;
        activeVanillaViewFrustumWorld = world;
        logTerrainDiagnostic("adopt-view-frustum", world, "viewFrustum=" + viewFrustumId(viewFrustum)
                + ", renderDistance=" + mc.gameSettings.renderDistanceChunks);
    }

    private void logInactiveBetterPortalsTerrainSkip(String reason, World world) {
        if (inactiveBetterPortalsTerrainSkipLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        inactiveBetterPortalsTerrainSkipLogs++;
        MainMod.LOGGER.info("[AUSMShaderless] Skipping AUSM Better Portals terrain recovery reason={} world={} nothiriumBypass={} recovery={}",
                reason,
                safeDimensionId(world),
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive());
    }

    private boolean syncRenderGlobalWorld(RenderGlobal renderGlobal, World world) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || !(world instanceof WorldClient worldClient)) {
            return false;
        }

        if (accessor.ausm$world() != worldClient) {
            World previous = accessor.ausm$world();
            accessor.ausm$setWorld(worldClient);
            accessor.ausm$setDisplayListEntitiesDirty(true);
            logTerrainDiagnostic("sync-render-global-world", world, "previous=" + safeDimensionId(previous)
                    + ", current=" + safeDimensionId(worldClient));
            return true;
        }
        return false;
    }

    public void renderShadowMap(float partialTicks) {
        if (!isPipelineActive || shadowFramebuffer == null || lastShadowFrameId == pipelineFrameId) {
            return;
        }
        if (!hasActiveShadowProgram()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        Entity viewEntity = mc.getRenderViewEntity();
        World world = renderWorld(mc);
        if (world == null || viewEntity == null || mc.renderGlobal == null) {
            return;
        }

        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-begin world=" + safeDimensionId(world));
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
            // Iris disables chunk occlusion culling while building the shadow terrain list.
            // The 1.12 equivalent is renderChunksMany; leaving it enabled lets the normal
            // camera visibility graph leak into the light-space pass.
            mc.renderChunksMany = false;
            boolean useNothiriumShadowBridge = shouldUseNothiriumShadowBridge();
            if (!useNothiriumShadowBridge && !shouldReuseMainTerrainForShadowMap()) {
                ensureVanillaTerrainRenderer();
                mc.renderGlobal.setupTerrain(
                        viewEntity,
                        partialTicks,
                        shadowCamera,
                        nextShadowFrameCount(),
                        mc.player != null && mc.player.isSpectator()
                );
            }

            clearColoredLightImages();
            boolean renderShadowTerrain = shaderProperties.renderSettings().shadowTerrain()
                    && hasShadowTerrainCandidates(mc, viewEntity, partialTicks);
            if (useNothiriumShadowBridge) {
                nothiriumShadowRenderer.drainUploads();
            }

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
            if (renderShadowTerrain) {
                solidCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
                cutoutMippedCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
                cutoutCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            }
            if (shaderProperties.renderSettings().shadowEntities()
                    || shaderProperties.renderSettings().shadowPlayer()
                    || shaderProperties.renderSettings().shadowBlockEntities()
                    || shaderProperties.renderSettings().shadowLightBlockEntities()) {
                beginPhase(WorldRenderingPhase.ENTITIES);
                // RenderLib replaces RenderGlobal.renderEntities with a queued renderer
                // that is only prepared during the normal world pass. The shadow pass
                // has its own camera, so render entities directly here.
                renderShadowEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            shadowFramebuffer.copyDepthToSnapshot();
            if (renderShadowTerrain && shaderProperties.renderSettings().shadowTranslucent()) {
                translucentCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            }
            injectMappedTileEntityVoxels(mc);
            if (solidCount > 0 || cutoutMippedCount > 0 || cutoutCount > 0 || translucentCount > 0) {
                shadowMapPopulated = true;
            }
            shadowFramebuffer.generateShadowColorMipmaps();
            logShadowHealth(solidCount, cutoutMippedCount, cutoutCount, translucentCount);
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
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-end world=" + safeDimensionId(world));
        }
    }

    private void injectMappedTileEntityVoxels(Minecraft mc) {
        World world = renderWorld(mc);
        if (!ENABLE_CPU_LIGHT_INJECTION || !shaderImages.active() || world == null) {
            return;
        }

        int[] dimensions = shaderImages.dimensions("voxel_img", "voxelimg", "voxel_sampler", "voxeltex");
        if (dimensions == null) {
            return;
        }

        int cameraFloorX = (int) Math.floor(cameraPositionUnshifted[0]);
        int cameraFloorY = (int) Math.floor(cameraPositionUnshifted[1]);
        int cameraFloorZ = (int) Math.floor(cameraPositionUnshifted[2]);
        int injected = 0;
        int[] projectRedVoxelIds = new int[8];
        Set<Long> writtenVoxels = new HashSet<>();

        List<TileEntity> loadedTileEntities = cpuLightTileEntitySnapshot(world);
        int tileEntityCount = loadedTileEntities.size();
        int scanCount = Math.min(tileEntityCount, MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME);
        for (int scan = 0; scan < scanCount; scan++) {
            if (injected >= MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME) {
                break;
            }
            if (tileEntityCount <= 0) {
                break;
            }
            if (cpuLightTileEntityScanCursor >= tileEntityCount) {
                cpuLightTileEntityScanCursor = 0;
            }
            TileEntity tileEntity = loadedTileEntities.get(cpuLightTileEntityScanCursor++);
            if (tileEntity == null || tileEntity.isInvalid()) {
                continue;
            }

            BlockPos pos = tileEntity.getPos();
            if (!isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                continue;
            }

            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "scan");
            if (projectRedCount > 0) {
                for (int i = 0; i < projectRedCount && injected < MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME; i++) {
                    if (injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "injected:" + projectRedVoxelIds[i]);
                    } else {
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "write_failed:" + projectRedVoxelIds[i]);
                    }
                }
                continue;
            }

            IBlockState state = actualLightState(world.getBlockState(pos), world, pos);
            if (state == null || state.getRenderType() != EnumBlockRenderType.INVISIBLE || state.getLightValue(world, pos) <= 0) {
                continue;
            }

            int voxelId = localActVoxelId(shaderProperties.blockIds().idFor(state));
            if (voxelId <= 0) {
                continue;
            }

            if (injectVoxelAt(pos, voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
                auditSyntheticLight("tile_entity", pos, new SyntheticLightInfo(state, state, shaderProperties.blockIds().idFor(state), voxelId, state.getLightValue(world, pos), "ok"), "injected");
            } else {
                auditSyntheticLight("tile_entity", pos, new SyntheticLightInfo(state, state, shaderProperties.blockIds().idFor(state), voxelId, state.getLightValue(world, pos), "ok"), "write_failed");
            }
        }

        injected += injectRecordedSyntheticLightVoxels(
                world,
                dimensions,
                cameraFloorX,
                cameraFloorY,
                cameraFloorZ,
                writtenVoxels,
                MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
        );

        if (injected > 0 && GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
    }

    private List<TileEntity> cpuLightTileEntitySnapshot(World world) {
        if (world == null) {
            cpuLightTileEntitySnapshotWorld = null;
            cpuLightTileEntitySnapshot = java.util.Collections.emptyList();
            cpuLightTileEntitySnapshotFrame = Long.MIN_VALUE;
            cpuLightTileEntityScanCursor = 0;
            return cpuLightTileEntitySnapshot;
        }

        boolean worldChanged = cpuLightTileEntitySnapshotWorld != world;
        boolean refresh = worldChanged
                || cpuLightTileEntitySnapshotFrame == Long.MIN_VALUE
                || pipelineFrameId - cpuLightTileEntitySnapshotFrame >= CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES;
        if (refresh) {
            cpuLightTileEntitySnapshotWorld = world;
            cpuLightTileEntitySnapshotFrame = pipelineFrameId;
            cpuLightTileEntitySnapshot = new ArrayList<>(world.loadedTileEntityList);
            if (worldChanged || cpuLightTileEntitySnapshot.isEmpty()) {
                cpuLightTileEntityScanCursor = 0;
            } else {
                cpuLightTileEntityScanCursor = Math.floorMod(cpuLightTileEntityScanCursor, cpuLightTileEntitySnapshot.size());
            }
        }
        return cpuLightTileEntitySnapshot;
    }

    private int injectRecordedSyntheticLightVoxels(World world, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                                   Set<Long> writtenVoxels, int remainingBudget) {
        if (remainingBudget <= 0 || syntheticLightCandidates.isEmpty()) {
            return 0;
        }

        int injected = 0;
        for (Map.Entry<Long, BlockPos> entry : syntheticLightCandidates.entrySet()) {
            if (injected >= remainingBudget) {
                break;
            }
            BlockPos pos = entry.getValue();
            if (pos == null || !world.isBlockLoaded(pos, false)) {
                if (isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }
            if (!isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                if (isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }

            TileEntity tileEntity;
            try {
                tileEntity = world.getTileEntity(pos);
            } catch (RuntimeException ignored) {
                tileEntity = null;
            }
            int[] projectRedVoxelIds = new int[8];
            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            if (projectRedCount > 0) {
                for (int i = 0; i < projectRedCount && injected < remainingBudget; i++) {
                    if (injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "candidate_injected:" + projectRedVoxelIds[i]);
                    } else {
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "candidate_write_failed:" + projectRedVoxelIds[i]);
                    }
                }
                continue;
            }

            IBlockState state = actualLightState(world.getBlockState(pos), world, pos);
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
                auditSyntheticLight("cpu_inject", pos, lightInfo, "drop:" + lightInfo.reason);
                continue;
            }

            if (injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
                auditSyntheticLight("cpu_inject", pos, lightInfo, "injected");
            } else {
                auditSyntheticLight("cpu_inject", pos, lightInfo, "write_failed");
            }
        }
        return injected;
    }

    private boolean isWellOutsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        return Math.abs(pos.getX() - cameraFloorX) > dimensions[0]
                || Math.abs(pos.getY() - cameraFloorY) > dimensions[1]
                || Math.abs(pos.getZ() - cameraFloorZ) > dimensions[2];
    }

    private boolean isInsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        int x = (int) Math.floor(pos.getX() + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(pos.getY() + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(pos.getZ() + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        return x >= 0 && y >= 0 && z >= 0
                && x < dimensions[0] && y < dimensions[1] && z < dimensions[2];
    }

    private boolean injectVoxelAt(BlockPos pos, int voxelId, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                  Set<Long> writtenVoxels) {
        if (voxelId <= 0) {
            return false;
        }

        int x = (int) Math.floor(pos.getX() + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(pos.getY() + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(pos.getZ() + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        if (x < 0 || y < 0 || z < 0 || x >= dimensions[0] || y >= dimensions[1] || z >= dimensions[2]) {
            return false;
        }
        if (writtenVoxels != null) {
            writtenVoxels.add(packedVoxelKey(x, y, z));
        }
        return shaderImages.writeRedInteger3D(x, y, z, voxelId, "voxel_img", "voxelimg", "voxel_sampler", "voxeltex");
    }

    private static long packedVoxelKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ z;
    }

    private static int localActVoxelId(int materialId) {
        if (materialId == 12003) {
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
        return 0;
    }

    private static int compatSyntheticLightVoxelId(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return 0;
        }
        if ("tconstruct".equals(name.getNamespace())
                && "seared_furnace_controller".equals(name.getPath())
                && stateName(state).contains("active=true")) {
            return 71;
        }
        int astralVoxel = astralCrystalVoxelId(state);
        if (astralVoxel > 0) {
            return astralVoxel;
        }
        return 0;
    }

    private static boolean isRandomThingsLuminousColoredLightDisabled(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null || !"randomthings".equals(name.getNamespace())) {
            return false;
        }
        String path = name.getPath();
        return "luminousblock".equalsIgnoreCase(path)
                || "translucentluminousblock".equalsIgnoreCase(path)
                || "luminousstainedbrick".equalsIgnoreCase(path);
    }

    private void clearColoredLightImages() {
        shaderImages.clearSmallImages();
        shaderImages.clearNamedImages(
                "voxel_img", "voxelimg", "voxel_sampler", "voxeltex"
        );
    }

    private boolean hasShadowTerrainCandidates(Minecraft mc, Entity viewEntity, float partialTicks) {
        if (shouldUseNothiriumShadowBridge()) {
            return true;
        }

        if (mc == null || viewEntity == null || !(mc.renderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return true;
        }
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
        resetPortalMaskState();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    }

    private static void resetPortalMaskState() {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        for (int i = 0; i < 6; i++) {
            GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
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
        if (mc == null || viewEntity == null) {
            return 0;
        }
        if (shouldUseNothiriumShadowBridge()) {
            double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
            double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
            double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
            return nothiriumShadowRenderer.renderLayer(layer, cameraX, cameraY, cameraZ, shadowRenderCullDistance());
        }

        if (mc.renderGlobal == null) {
            return 0;
        }
        int count = mc.renderGlobal.renderBlockLayer(layer, partialTicks, 2, viewEntity);
        if (count != 0) {
            return count;
        }
        return renderShadowBlockLayerFromViewFrustum(mc, layer, partialTicks, viewEntity);
    }

    private void logShadowHealth(int solidCount, int cutoutMippedCount, int cutoutCount, int translucentCount) {
        if (shadowHealthLogged || shadowFramebuffer == null) {
            return;
        }
        if (shadowHealthLogAttempts >= 6) {
            return;
        }
        shadowHealthLogAttempts++;
        shadowHealthLogged = true;
        ShadowFramebuffer.DepthStats stats = shadowFramebuffer.readDepthStats(4);
        boolean terrainPopulated = solidCount > 0
                || cutoutMippedCount > 0
                || cutoutCount > 0
                || translucentCount > 0;
        boolean populated = terrainPopulated
                || (!shouldUseNothiriumShadowBridge() && stats.nonClear() > 0);
        shadowHealthLogged = populated;
        MainMod.LOGGER.info(
                "[ShadowHealth] depth center={} min={} max={} nonClear={}/{} terrainCounts solid={} cutoutMipped={} cutout={} translucent={}",
                stats.center(),
                stats.min(),
                stats.max(),
                stats.nonClear(),
                stats.total(),
                solidCount,
                cutoutMippedCount,
                cutoutCount,
                translucentCount
        );
    }

    private int renderShadowBlockLayerFromViewFrustum(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (mc == null || viewEntity == null || !(mc.renderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return 0;
        }
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
        float shadowDepthRange = Math.max(256.0F, shadowMapDistance * 2.0F);
        float shadowDepthCenter = shadowDepthRange * 0.5F;
        GL11.glOrtho(
                -shadowMapDistance,
                shadowMapDistance,
                -shadowMapDistance,
                shadowMapDistance,
                0.05F,
                shadowDepthRange
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -shadowDepthCenter);
        GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);

        World world = renderWorld(Minecraft.getMinecraft());
        float celestialAngle = world != null ? world.getCelestialAngle(partialTicks) : 0.0F;
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
        World world = renderWorld(Minecraft.getMinecraft());
        float celestialAngle = world != null ? world.getCelestialAngle(partialTicks) : 0.0F;
        float angle = celestialAngle + 0.25F;
        if (angle >= 1.0F) {
            angle -= 1.0F;
        }
        return angle;
    }

    private ICamera createShadowCamera(Entity viewEntity, float partialTicks) {
        ICamera celeritasCamera = createCeleritasShadowCamera(viewEntity, partialTicks);
        if (celeritasCamera != null) {
            return celeritasCamera;
        }
        return createVanillaShadowCamera();
    }

    private ICamera createVanillaShadowCamera() {
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

    private ICamera createCeleritasShadowCamera(Entity viewEntity, float partialTicks) {
        try {
            ClassLoader loader = PipelineContext.class.getClassLoader();
            Class<?> viewportProviderClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.ViewportProvider", false, loader);
            Class<?> viewportClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.Viewport", false, loader);
            Class<?> frustumClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum", false, loader);
            Class<?> vector3dClass = Class.forName(
                    "org.embeddedt.embeddium.impl.shadow.joml.Vector3d", false, loader);

            Constructor<?> viewportConstructor = viewportClass.getConstructor(frustumClass, vector3dClass);
            Constructor<?> vectorConstructor = vector3dClass.getConstructor(double.class, double.class, double.class);
            Object frustum = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{frustumClass},
                    (proxy, method, args) -> boolean.class.equals(method.getReturnType()) ? Boolean.TRUE : null
            );

            double[] position = {
                    interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks),
                    interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks),
                    interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks)
            };
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("sodium$createViewport".equals(name)) {
                    Object cameraPosition = vectorConstructor.newInstance(position[0], position[1], position[2]);
                    return viewportConstructor.newInstance(frustum, cameraPosition);
                }
                if ("isBoundingBoxInFrustum".equals(name) || "func_78546_a".equals(name)) {
                    return true;
                }
                if ("setPosition".equals(name) || "func_78547_a".equals(name)) {
                    if (args != null && args.length == 3) {
                        position[0] = ((Number) args[0]).doubleValue();
                        position[1] = ((Number) args[1]).doubleValue();
                        position[2] = ((Number) args[2]).doubleValue();
                    }
                    return null;
                }
                if ("toString".equals(name)) {
                    return "AUSM Celeritas shadow camera";
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
                    loader,
                    new Class<?>[]{ICamera.class, viewportProviderClass},
                    handler
            );
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            if (!celeritasShadowCameraWarningLogged) {
                celeritasShadowCameraWarningLogged = true;
                MainMod.LOGGER.warn("[Pipeline] Failed to create Celeritas-compatible shadow camera; falling back to vanilla camera", e);
            }
            return null;
        }
    }

    private void renderShadowEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowEntities() && !shaderProperties.renderSettings().shadowPlayer()) {
            return;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null || mc.entityRenderer == null) {
            return;
        }
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }
        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);

        renderManager.cacheActiveRenderInfo(world, mc.fontRenderer, viewEntity, mc.pointedEntity, mc.gameSettings, partialTicks);
        renderManager.setRenderPosition(cameraX, cameraY, cameraZ);
        mc.entityRenderer.enableLightmap();
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        List<Entity> loadedEntities = world.getLoadedEntityList();
        if (loadedEntities == null) {
            return;
        }
        for (Entity entity : loadedEntities) {
            if (!shouldRenderEntityInShadowMap(mc, world, renderManager, entity, viewEntity, shadowCamera, cameraX, cameraY, cameraZ)) {
                continue;
            }

            renderManager.renderEntityStatic(entity, partialTicks, false);
            if (renderManager.isRenderMultipass(entity)) {
                renderManager.renderMultipass(entity, partialTicks);
            }
        }
    }

    private boolean shouldRenderEntityInShadowMap(Minecraft mc, World world, RenderManager renderManager, Entity entity, Entity viewEntity,
                                                  ICamera shadowCamera, double cameraX, double cameraY, double cameraZ) {
        if (mc == null || world == null || renderManager == null || entity == null || entity.isDead || !entity.shouldRenderInPass(0)) {
            return false;
        }
        if (BetterPortalsCompat.isPortalEntity(entity)) {
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
        if (entity.posY >= 0.0D && entity.posY < 256.0D && !world.isBlockLoaded(new BlockPos(entity))) {
            return false;
        }
        return entity.isInRangeToRender3d(cameraX, cameraY, cameraZ);
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static int eyeFluidState(Minecraft mc) {
        if (mc == null) {
            return 0;
        }
        Entity viewEntity = mc.getRenderViewEntity();
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return 0;
        }

        Material cameraMaterial = ActiveRenderInfo
                .getBlockStateAtEntityViewpoint(world, viewEntity, mc.getRenderPartialTicks())
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
        resetPortalMaskState();
    }

    public void prepareExternalWorldOverlayRender() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (worldFrameActive) {
            pingPongManager.bindForGbuffers(Attachment.COLOR);
        }
        OpenGlHelper.glUseProgram(0);
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void restoreVanillaWorldTextureBindings() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.entityRenderer != null) {
            DynamicTexture lightmapTexture = ((EntityRendererAccessor) mc.entityRenderer).ausm$getLightmapTexture();
            restoreVanillaLightmapTexture(mc);
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

        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        centerDepth = framebuffer.readCenterDepth();
        if (Float.isFinite(centerDepth)) {
            centerDepthSmooth += (centerDepth - centerDepthSmooth) * smoothingFactor(centerDepthHalfLife, currentFrameTime);
            if (Math.abs(centerDepth - centerDepthSmooth) < 0.00001f) {
                centerDepthSmooth = centerDepth;
            }
            updateCenterDepthSmoothTexture();
        }
    }

    public int renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        if (renderGlobal == null) {
            logWorldLayerDiag("skip-null-render-global", layer, pass, 0, viewEntity);
            return 0;
        }
        if (shouldSuppressDuplicatePipelineTranslucentLayer(layer)) {
            logWorldLayerDiag("skip-duplicate-translucent", layer, pass, 0, viewEntity);
            return 0;
        }

        boolean prepareVanillaState = shouldPrepareShaderlessBlockLayerState();
        if (prepareVanillaState) {
            prepareShaderlessBlockLayerState(layer);
        }

        try {
            int nothiriumCount = renderNothiriumTerrainLayer(layer, (float) partialTicks, viewEntity);
            if (nothiriumCount >= 0) {
                markNothiriumPipelineTranslucentBridge(layer);
                recordTerrainLayerCount(layer, nothiriumCount);
                logWorldLayerDiag("nothirium", layer, pass, nothiriumCount, viewEntity);
                return nothiriumCount;
            }

            boolean forceVanillaFallback = isPipelineActive && !NothiriumBypass.shouldBypass();
            if (forceVanillaFallback) {
                NothiriumBypass.pushForcedBypass();
            }
            int count;
            try {
                count = renderGlobal.renderBlockLayer(layer, partialTicks, pass, viewEntity);
            } finally {
                if (forceVanillaFallback) {
                    NothiriumBypass.popForcedBypass();
                }
            }
            recordTerrainLayerCount(layer, count);
            logWorldLayerDiag(forceVanillaFallback ? "vanilla-forced-bypass" : "vanilla", layer, pass, count, viewEntity);
            return count;
        } finally {
            if (prepareVanillaState) {
                finishShaderlessBlockLayerState(layer);
            }
        }
    }

    private void logWorldLayerDiag(String stage, BlockRenderLayer layer, int pass, int count, Entity viewEntity) {
        if (worldLayerDiagLogs >= MAX_WORLD_LAYER_DIAG_LOGS
                || (layer != BlockRenderLayer.TRANSLUCENT && !AusmBloomLayer.isBloomLayer(layer))) {
            return;
        }

        worldLayerDiagLogs++;
        Minecraft mc = Minecraft.getMinecraft();
        MainMod.LOGGER.info(
                "[AUSMTranslucentDiag] source=render-world-layer call={} stage={} layer={} pass={} count={} active={} shaderlessWorldPass={} worldFrame={} bypass={} bpPass={} bpNested={} renderGlobalWorld={} clientWorld={} entity={} gl={}",
                worldLayerDiagLogs,
                stage,
                layer,
                pass,
                count,
                isPipelineActive,
                shaderlessWorldPassActive,
                worldFrameActive,
                NothiriumBypass.shouldBypass(),
                isRenderingBetterPortalsRenderPass(),
                isRenderingBetterPortalsNestedView(),
                safeDimensionId(renderWorld(mc)),
                mc != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE,
                viewEntity != null ? viewEntity.getClass().getName() : "null",
                glStateSummary()
        );
    }

    private void markNothiriumPipelineTranslucentBridge(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || !isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass != RenderPass.GBUFFERS_WATER
                || getPhase() != WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            return;
        }

        nothiriumPipelineTranslucentFrame = pipelineFrameId;
        nothiriumPipelineTranslucentWorldPassSerial = currentWorldPassSerial;
        nothiriumPipelineTranslucentDrawnFrame = pipelineFrameId;
    }

    private boolean shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer layer) {
        boolean sameWorldPass = currentWorldPassSerial != Long.MIN_VALUE
                && nothiriumPipelineTranslucentWorldPassSerial == currentWorldPassSerial
                && nothiriumPipelineTranslucentFrame == pipelineFrameId;
        boolean samePipelineFrame = nothiriumPipelineTranslucentDrawnFrame == pipelineFrameId;
        return layer == BlockRenderLayer.TRANSLUCENT
                && isPipelineActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && (worldFrameActive || samePipelineFrame)
                && (sameWorldPass || samePipelineFrame)
                && !isPipelineTranslucentTerrainPhase();
    }

    private boolean isPipelineTranslucentTerrainPhase() {
        return activePass == RenderPass.GBUFFERS_WATER
                && getPhase() == WorldRenderingPhase.TERRAIN_TRANSLUCENT;
    }

    private void clearNothiriumPipelineTranslucentBridge() {
        nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;
        nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;
    }

    private void beginWorldPassDuplicateTracking() {
        worldPassSerialStack.push(currentWorldPassSerial);
        nothiriumPipelineTranslucentFrameStack.push(nothiriumPipelineTranslucentFrame);
        nothiriumPipelineTranslucentWorldPassSerialStack.push(nothiriumPipelineTranslucentWorldPassSerial);
        currentWorldPassSerial = ++nextWorldPassSerial;
        clearNothiriumPipelineTranslucentBridge();
    }

    private void finishWorldPassDuplicateTracking() {
        currentWorldPassSerial = worldPassSerialStack.isEmpty() ? Long.MIN_VALUE : worldPassSerialStack.pop();
        nothiriumPipelineTranslucentFrame = nothiriumPipelineTranslucentFrameStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentFrameStack.pop();
        nothiriumPipelineTranslucentWorldPassSerial = nothiriumPipelineTranslucentWorldPassSerialStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentWorldPassSerialStack.pop();
    }

    private boolean shouldPrepareShaderlessBlockLayerState() {
        return !isPipelineActive || shouldBypassWorldPassRendering();
    }

    private void prepareShaderlessBlockLayerState(BlockRenderLayer layer) {
        Minecraft mc = Minecraft.getMinecraft();
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableTexture2D();
        restoreVanillaFixedFunctionTextureState(mc);
        GlStateManager.enableDepth();

        if (shouldRenderLayerWithTranslucentState(layer)) {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ZERO
            );
            GlStateManager.depthMask(false);
            forceTranslucentFixedFunctionState();
            return;
        }

        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        if (layer == BlockRenderLayer.SOLID) {
            GlStateManager.disableAlpha();
        } else {
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    private void finishShaderlessBlockLayerState(BlockRenderLayer layer) {
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        if (shouldRenderLayerWithTranslucentState(layer)) {
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    private static boolean shouldRenderLayerWithTranslucentState(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT || AusmBloomLayer.isBloomLayer(layer);
    }

    private static void forceTranslucentFixedFunctionState() {
        GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL13.glClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL11.glDepthMask(false);
    }

    private void recordTerrainLayerCount(BlockRenderLayer layer, int count) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || layer == null
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        if (terrainLayerCountFrame != pipelineFrameId) {
            terrainLayerCountFrame = pipelineFrameId;
            terrainOpaqueLayerCount = 0;
            terrainOpaqueDrawCount = 0;
        }

        if (layer == BlockRenderLayer.SOLID
                || layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT) {
            terrainOpaqueLayerCount++;
            terrainOpaqueDrawCount += Math.max(0, count);
        }

        if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount == 0) {
            requestPersistentHistoryClear("zero-opaque-terrain");
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
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        if (deferredPassesRenderedThisFrame) {
            return;
        }

        clearPendingPersistentHistoryIfNeeded();
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
        if (!isPipelineActive || !pingPongManager.isInitialized() || !worldFrameActive) {
            return;
        }

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            resetPipelineState();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer target = currentWorldFramebufferTarget(mc);
        if (target == null) {
            resetPipelineState();
            return;
        }
        boolean externalTarget = isExternalWorldFramebufferTarget(target);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-blit-start external=" + externalTarget
                + " target=" + describeFramebufferTarget(target)
                + " read=" + describeDeferredFramebuffer(readBuffer));
        logBetterPortalsPipeline("blit-start", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target));
        beginTranslucents();
        logBetterPortalsPipeline("after-translucents");
        if (externalTarget) {
            runFullscreenPasses(ProgramArrayId.COMPOSITE);
            logBetterPortalsPipeline("after-external-composite");
            readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer == null) {
                logBetterPortalsPipeline("abort-null-read-after-external-composite");
                resetPipelineState(target);
                return;
            }

            runComputePrograms(finalComputePrograms, RenderPass.FINAL);
            logBetterPortalsPipeline("after-external-final-compute");

            PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
            if (finalProgram != null && finalProgram.hasOwnProgram()) {
                logBetterPortalsPipeline("choose-external-final-pass");
                renderFinalPass(target);
                finishWorldFramebuffer(target, true);
                return;
            }

            logBetterPortalsPipeline("choose-external-composite-blit");
            readBuffer.blitTo(
                    target.framebufferObject,
                    target.framebufferWidth,
                    target.framebufferHeight
            );
            target.bindFramebuffer(false);
            GlStateManager.viewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
            finishWorldFramebuffer(target, true);
            return;
        }

        runFullscreenPasses(ProgramArrayId.COMPOSITE);
        logBetterPortalsPipeline("after-composite");
        readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            logBetterPortalsPipeline("abort-null-read-after-composite");
            resetPipelineState(target);
            return;
        }
        runComputePrograms(finalComputePrograms, RenderPass.FINAL);
        logBetterPortalsPipeline("after-final-compute");

        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram != null && finalProgram.hasOwnProgram()) {
            logBetterPortalsPipeline("choose-final-pass");
            renderFinalPass(target);
            finishWorldFramebuffer(target, externalTarget);
            return;
        }

        logBetterPortalsPipeline("choose-direct-blit");
        readBuffer.blitTo(
                target.framebufferObject,
                target.framebufferWidth,
                target.framebufferHeight
        );

        target.bindFramebuffer(false);
        GlStateManager.viewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        finishWorldFramebuffer(target, externalTarget);
    }

    private void finishWorldFramebuffer(Framebuffer target, boolean externalTarget) {
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-before-reset external=" + externalTarget
                + " target=" + describeFramebufferTarget(target));
        logBetterPortalsPipeline("finish-before-reset", "external=" + externalTarget
                + ", target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target));
        target.bindFramebuffer(false);
        renderPostWorldBloom(target, externalTarget);
        if (!externalTarget) {
            GlStateManager.clearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        resetPipelineState(target);
        worldFrameActive = false;
        logBetterPortalsPipeline("finish-after-reset", "external=" + externalTarget);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-after-reset external=" + externalTarget);
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
        for (FullscreenArrayProgram program : fullscreenArrayPrograms.getOrDefault(arrayId, List.of())) {
            if (program.hasProgram()) {
                runFullscreenArrayProgram(program);
            }
        }
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

    private void runSetupComputesIfNeeded() {
        if (!setupComputePending) {
            return;
        }
        setupComputePending = false;
        runFullscreenPasses(ProgramArrayId.SETUP);
    }

    private RenderPass computeBindingPass(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN || arrayId == ProgramArrayId.PREPARE) {
            return RenderPass.PREPARE;
        }
        if (arrayId == ProgramArrayId.DEFERRED) {
            return RenderPass.DEFERRED;
        }
        if (arrayId == ProgramArrayId.COMPOSITE) {
            return RenderPass.COMPOSITE;
        }
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            return RenderPass.SHADOW;
        }
        return RenderPass.FINAL;
    }

    private void runFullscreenArrayProgram(FullscreenArrayProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        generateReadMipmaps(program.directives());

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        setupFullscreenState();
        try {
            applyFullscreenViewport(program.name(), program.directives(), drawBuffers);
            applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            bindFullscreenArrayProgram(program);
            FullscreenQuad.draw();
        } finally {
            if (program.shaderProgram() != null) {
                program.shaderProgram().unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        generateWrittenMipmaps(program.directives(), flippedAttachments);
    }

    private void bindFullscreenArrayProgram(FullscreenArrayProgram program) {
        ShaderProgram shaderProgram = program.shaderProgram();
        if (shaderProgram == null) {
            return;
        }

        RenderPass bindingPass = program.bindingPass();
        activePass = bindingPass;
        activeShaderKey = ShaderKey.fromRenderPass(bindingPass);
        activePhase = WorldRenderingPhase.NONE;
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures();
        shaderProgram.bind();
        bindProgramResources(bindingPass, shaderProgram);
    }

    private void applyFullscreenArrayRenderState(ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        ShaderAlphaTest alphaTest = directives.alphaTestOverride();
        if (alphaTest != null) {
            currentAlphaTestReference = alphaTest.reference();
            if (alphaTest.function() == GL11.GL_ALWAYS) {
                GlStateManager.disableAlpha();
            } else {
                GlStateManager.enableAlpha();
            }
            GlStateManager.alphaFunc(alphaTest.function(), alphaTest.reference());
        }

        ShaderBlendMode blendMode = directives.blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = directives.attachmentBlendModes();
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }
        if (blendMode != null && !blendMode.enabled()) {
            GlStateManager.disableBlend();
            resetIndexedBlendState();
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
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            ShaderBlendMode attachmentMode = attachmentModes.get(drawBuffers.get(drawBufferIndex));
            if (attachmentMode != null) {
                applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            }
        }
    }

    private void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        applyShaderMemoryBarrier();
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        Minecraft mc = Minecraft.getMinecraft();
        int width = framebuffer != null ? framebuffer.getWidth() : mc != null ? mc.displayWidth : 1;
        int height = framebuffer != null ? framebuffer.getHeight() : mc != null ? mc.displayHeight : 1;
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
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

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        generateWrittenMipmaps(program, flippedAttachments);
    }

    private void applyViewportScale(PipelineProgram program, int width, int height) {
        applyViewportScale(program.directives().viewportScale(), width, height);
    }

    private void applyViewportScale(ShaderViewportScale scale, int width, int height) {
        GlStateManager.viewport(scale.x(width), scale.y(height), scale.width(width), scale.height(height));
    }

    private void applyFullscreenViewport(PipelineProgram program, List<Attachment> drawBuffers) {
        applyFullscreenViewport(program.pass().getProgramName(), program.directives(), drawBuffers);
    }

    private void applyFullscreenViewport(String programName, ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();
        if (!drawBuffers.isEmpty()) {
            Attachment first = drawBuffers.get(0);
            width = framebuffer.getAttachmentWidth(first);
            height = framebuffer.getAttachmentHeight(first);
            for (Attachment attachment : drawBuffers) {
                if (framebuffer.getAttachmentWidth(attachment) != width || framebuffer.getAttachmentHeight(attachment) != height) {
                    MainMod.LOGGER.warn("[Pipeline] Pass {} writes differently sized buffers; using {} size {}x{} for viewport",
                            programName, first, width, height);
                    break;
                }
            }
        }
        applyViewportScale(directives.viewportScale(), width, height);
    }

    private void renderFinalPass(Framebuffer target) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (target == null || readBuffer == null || finalProgram == null) {
            logBetterPortalsPipeline("final-pass-skip", "target=" + describeFramebufferTargetDetailed(target)
                    + ", read=" + describeDeferredFramebuffer(readBuffer)
                    + ", final=" + describePipelineProgram(finalProgram));
            return;
        }

        logBetterPortalsPipeline("final-pass-start", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target)
                + ", read=" + describeDeferredFramebuffer(readBuffer)
                + ", final=" + describePipelineProgram(finalProgram));
        readBuffer.blitDepthTo(
                target.framebufferObject,
                target.framebufferWidth,
                target.framebufferHeight
        );

        target.bindFramebuffer(false);
        GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);
        generateReadMipmaps(finalProgram);

        setupFullscreenState();
        applyViewportScale(finalProgram, target.framebufferWidth, target.framebufferHeight);
        beginPass(RenderPass.FINAL);
        FullscreenQuad.draw();
        endPass();
        restoreFullscreenState();

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);
        logBetterPortalsPipeline("final-pass-end", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target));
    }

    private void generateReadMipmaps(PipelineProgram program) {
        if (program != null) {
            generateReadMipmaps(program.directives());
        }
    }

    private void generateReadMipmaps(ShaderProgramDirectives directives) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (directives != null && readBuffer != null && !directives.mipmappedBuffers().isEmpty()) {
            readBuffer.generateMipmaps(directives.mipmappedBuffers());
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    private void generateWrittenMipmaps(PipelineProgram program, Attachment[] flippedAttachments) {
        if (program == null) {
            return;
        }
        generateWrittenMipmaps(program.directives(), flippedAttachments);
    }

    private void generateWrittenMipmaps(ShaderProgramDirectives directives, Attachment[] flippedAttachments) {
        if (directives == null || flippedAttachments.length == 0 || directives.mipmappedBuffers().isEmpty()) {
            return;
        }
        EnumSet<Attachment> mipmappedWrittenAttachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : flippedAttachments) {
            if (directives.mipmappedBuffers().contains(attachment)) {
                mipmappedWrittenAttachments.add(attachment);
            }
        }
        if (!mipmappedWrittenAttachments.isEmpty()) {
            DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer != null) {
                readBuffer.generateMipmaps(mipmappedWrittenAttachments);
            }
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
        cleanupRuntimeState(true, true);
    }

    private void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms) {
        cleanupRuntimeState(deleteActiveCompiledPrograms, deleteCachedCompiledPrograms, true);
    }

    private void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms, boolean deleteVanillaTerrainRenderers) {
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
        bloomRenderer.delete();
        deleteCustomTextures();
        if (deleteVanillaTerrainRenderers) {
            deleteCachedVanillaTerrainRenderers();
            vanillaViewFrustumStateStack.clear();
        }
        shaderImages.delete();
        shaderImages = ShaderImageSet.empty();
        shaderStorageBuffers.delete();
        shaderStorageBuffers = ShaderStorageBufferSet.empty();
        if (deleteActiveCompiledPrograms) {
            deleteComputePrograms();
            deleteFullscreenArrayPrograms();
            for (PipelineProgram program : programs.values()) {
                program.delete();
            }
        }
        if (deleteCachedCompiledPrograms) {
            deleteCachedCompiledPipelines();
        }
        programs.clear();
        programSet = null;
        shaderMap = null;
        shaderProperties = emptyShaderProperties();
        fullscreenProgramArrays.clear();
        fullscreenArrayPrograms.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        setupComputePending = false;
        syntheticLightCandidates.clear();
        resetColoredLightAudit();
        packDirectives = emptyShaderProperties().packDirectives();
        isPipelineActive = false;
        activePackName = "(internal)";
        activePass = null;
        activeShaderKey = null;
        activeCompiledPipelineCacheKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        currentAlphaTestReference = 0.1f;
        centerDepthHalfLife = 1.0f;
        centerDepth = 1.0f;
        centerDepthSmooth = 1.0f;
        pipelineFrameId = 0L;
        nextWorldPassSerial = 0L;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        resetChunkFadeState(false);
        frameTimeCounter = 0.0f;
        currentFrameTime = 0.016f;
        frameTimeSmooth = 0.016f;
        frameTimeSmoothInitialized = false;
        cameraShiftX = 0.0;
        cameraShiftZ = 0.0;
        temporalHistoryInitialized = false;
        temporalHistoryDimensionId = Integer.MIN_VALUE;
        previousTemporalYaw = 0.0f;
        previousTemporalPitch = 0.0f;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
        mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        pendingPersistentHistoryClear = false;
        pendingPersistentHistoryClearReason = "";
        terrainLayerCountFrame = Long.MIN_VALUE;
        terrainOpaqueLayerCount = 0;
        terrainOpaqueDrawCount = 0;
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
        worldPassBypassStack.clear();
        clientRenderFrameNanos = Long.MIN_VALUE;
        bloomLayerRenderedThisWorldFrame = false;
        shaderlessBloomRenderedThisWorldFrame = false;
        shaderlessWorldPassActive = false;
        shaderlessBloomRenderedThisWorldPass = false;
        lastTerrainTransitionWorld = null;
        lastTerrainTransitionDimension = Integer.MIN_VALUE;
        lastTerrainTransitionMillis = 0L;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
        scheduleInactiveVanillaRecoveryFrame();
    }


    public boolean isActive() {
        return isPipelineActive;
    }

    public boolean isRenderingBetterPortalsExternalWorldFrame() {
        return BetterPortalsCompat.isInstalled()
                && worldFrameActive
                && externalWorldFramebufferTarget != null;
    }

    public boolean isRenderingBetterPortalsNestedView() {
        return BetterPortalsCompat.isRenderingNestedView();
    }

    public boolean isRenderingBetterPortalsRenderPass() {
        return BetterPortalsCompat.isRenderingRenderPass();
    }

    public boolean shouldRenderBetterPortalsNestedViewWithShaders() {
        return isPipelineActive
                && BetterPortalsCompat.shouldRenderNestedViewWithShaders()
                && BetterPortalsCompat.currentShaderRenderPassFramebuffer() != null;
    }

    public boolean shouldBypassWorldPassRendering() {
        if (!worldPassBypassStack.isEmpty()) {
            return worldPassBypassStack.peek();
        }
        return computeShouldBypassWorldPassRendering();
    }

    public String describeBetterPortalsDiagnostics() {
        return "active=" + isPipelineActive
                + " shaderlessWorldPass=" + shaderlessWorldPassActive
                + " worldFrame=" + worldFrameActive
                + " frame=" + pipelineFrameId
                + " activePass=" + activePass
                + " phase=" + activePhase
                + " shadow=" + renderingShadowMap
                + " deferred=" + deferredPassesRenderedThisFrame
                + " passStack=" + passStack.size()
                + " bypassStack=" + worldPassBypassStack.size()
                + "/" + (worldPassBypassStack.isEmpty() ? "empty" : worldPassBypassStack.peek())
                + " bpPass=" + isRenderingBetterPortalsRenderPass()
                + " bpNested=" + isRenderingBetterPortalsNestedView()
                + " bpNestedShaders=" + shouldRenderBetterPortalsNestedViewWithShaders()
                + " externalTarget=" + describeFramebufferTarget(externalWorldFramebufferTarget)
                + " read=" + describeDeferredFramebuffer(pingPongManager.getReadBuffer());
    }

    private boolean computeShouldBypassWorldPassRendering() {
        return shouldLeaveBetterPortalsRenderPassUntouched()
                || isRenderingBetterPortalsNestedView() && !shouldRenderBetterPortalsNestedViewWithShaders();
    }

    private boolean shouldLeaveBetterPortalsRenderPassUntouched() {
        return BetterPortalsCompat.isInstalled()
                && isRenderingBetterPortalsRenderPass()
                && (!isPipelineActive
                || isRenderingBetterPortalsNestedView() && !shouldRenderBetterPortalsNestedViewWithShaders());
    }

    private String describeDeferredFramebuffer(DeferredFramebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return framebuffer.getFramebufferId()
                + "("
                + framebuffer.getWidth()
                + "x"
                + framebuffer.getHeight()
                + ", color="
                + framebuffer.getReadTexture(Attachment.COLOR)
                + ", depth="
                + framebuffer.getDepthTexture()
                + ")";
    }

    private String describeFramebufferTarget(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return framebuffer.framebufferObject
                + "("
                + framebuffer.framebufferWidth
                + "x"
                + framebuffer.framebufferHeight
                + ")";
    }

    private void logBetterPortalsPipeline(String stage) {
        logBetterPortalsPipeline(stage, "");
    }

    private void logBetterPortalsPipeline(String stage, String detail) {
        if (!shouldLogBetterPortalsPipeline(stage)) {
            return;
        }
        if (betterPortalsPipelineLogs >= MAX_BETTER_PORTALS_PIPELINE_LOGS) {
            return;
        }
        betterPortalsPipelineLogs++;

        Minecraft mc = Minecraft.getMinecraft();
        World renderWorld = renderWorld(mc);
        World clientWorld = mc != null ? mc.world : null;
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        MainMod.LOGGER.info("[BetterPortalsPipeline] stage={} detail={} active={} worldFrame={} frame={} activePass={} phase={} renderWorld={} clientWorld={} nested={} renderPass={} nestedShaders={} externalTarget={} externalStatus={} read={} pack={} finalProgram={} compositePrograms={} finalComputes={} deferred={} setupComputePending={} gl={}",
                stage,
                detail,
                isPipelineActive,
                worldFrameActive,
                pipelineFrameId,
                activePass,
                activePhase,
                safeDimensionId(renderWorld),
                safeDimensionId(clientWorld),
                isRenderingBetterPortalsNestedView(),
                isRenderingBetterPortalsRenderPass(),
                shouldRenderBetterPortalsNestedViewWithShaders(),
                describeFramebufferTargetDetailed(externalWorldFramebufferTarget),
                framebufferStatus(externalWorldFramebufferTarget),
                describeDeferredFramebuffer(readBuffer),
                shaderPackDiagnostics(),
                describePipelineProgram(finalProgram),
                countCompositePrograms(),
                finalComputePrograms.size(),
                deferredPassesRenderedThisFrame,
                setupComputePending,
                describeCurrentGlTarget());
    }

    private boolean shouldLogBetterPortalsPipeline(String stage) {
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        if (!isPipelineActive
                && isRenderingBetterPortalsRenderPass()
                && !isRenderingBetterPortalsNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && ("world-pass-begin".equals(stage) || "world-pass-finish".equals(stage))) {
            return false;
        }
        return isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || isBetterPortalsExternalWorldTarget()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    private String describeFramebufferTargetDetailed(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        return framebuffer.framebufferObject
                + "("
                + framebuffer.framebufferWidth
                + "x"
                + framebuffer.framebufferHeight
                + ", tex="
                + framebuffer.framebufferTexture
                + ", texSize="
                + framebuffer.framebufferTextureWidth
                + "x"
                + framebuffer.framebufferTextureHeight
                + ", depth="
                + framebuffer.depthBuffer
                + ")";
    }

    private String framebufferStatus(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        if (!OpenGlHelper.isFramebufferEnabled()) {
            return "disabled";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.framebufferObject);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            return status == GL30.GL_FRAMEBUFFER_COMPLETE ? "complete" : "0x" + Integer.toHexString(status);
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private String describePipelineProgram(PipelineProgram program) {
        if (program == null) {
            return "null";
        }
        return "enabled=" + program.enabled() + ", own=" + program.hasOwnProgram();
    }

    private long countCompositePrograms() {
        return fullscreenArrayPrograms
                .getOrDefault(ProgramArrayId.COMPOSITE, List.of())
                .stream()
                .filter(FullscreenArrayProgram::hasProgram)
                .count();
    }

    private String shaderPackDiagnostics() {
        return MainMod.getShaderPackManager() != null
                ? MainMod.getShaderPackManager().describeBetterPortalsPipelineState()
                : "shaderManager=null";
    }

    private String describeCurrentGlTarget() {
        try {
            return "fbo=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                    + ", readFb=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawFb=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", drawBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_DRAW_BUFFER))
                    + ", readBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_READ_BUFFER))
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", viewport=" + currentViewportSummary();
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    private String currentViewportSummary() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        return "["
                + viewportBuffer.get(0)
                + ","
                + viewportBuffer.get(1)
                + ","
                + viewportBuffer.get(2)
                + ","
                + viewportBuffer.get(3)
                + "]";
    }

    public boolean prepareRenderGlobalChunkUpdates(RenderGlobal renderGlobal) {
        if (renderGlobal == null) {
            return false;
        }

        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            return true;
        }

        boolean betterPortalsProtectedPass = BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingNestedView();
        if (!betterPortalsProtectedPass) {
            return true;
        }

        if (!(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return true;
        }

        World renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld == null) {
            Minecraft mc = Minecraft.getMinecraft();
            renderPassWorld = mc != null ? mc.world : null;
        }
        if (renderPassWorld == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no active render-pass world");
            return false;
        }
        ensureVanillaTerrainRenderer(renderPassWorld, true);
        if (accessor.ausm$world() == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no RenderGlobal world after sync");
            return false;
        }
        if (accessor.ausm$world() != renderPassWorld) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates for mismatched render-pass world: renderGlobal={} pass={}",
                    safeDimensionId(accessor.ausm$world()),
                    safeDimensionId(renderPassWorld));
            clearRenderGlobalChunkUpdates(accessor);
            return false;
        }

        return filterBetterPortalsChunkUpdates(accessor, renderPassWorld);
    }

    public boolean handleBetterPortalsChunkUpdateFailure(RenderGlobal renderGlobal, NullPointerException exception) {
        if (!BetterPortalsCompat.isInstalled()
                || !(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return false;
        }
        if (!isBetterPortalsChunkUpdateNullWorldFailure(exception)
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && accessor.ausm$world() != null
                && !hasInvalidBetterPortalsChunkUpdate(accessor)) {
            return false;
        }

        clearRenderGlobalChunkUpdates(accessor);
        if (!betterPortalsChunkUpdateWarningLogged) {
            betterPortalsChunkUpdateWarningLogged = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Dropped stale nested chunk update after Better Portals exposed a RenderChunk with no world", exception);
        }
        return true;
    }

    public World betterPortalsRenderChunkFallbackWorld() {
        if (!BetterPortalsCompat.isInstalled()) {
            return null;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }

        if (!BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return null;
        }

        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.world : null;
    }

    private boolean isBetterPortalsChunkUpdateNullWorldFailure(NullPointerException exception) {
        if (exception == null) {
            return false;
        }
        for (StackTraceElement frame : exception.getStackTrace()) {
            String className = frame.getClassName();
            if ("net.minecraft.world.ChunkCache".equals(className)
                    || "net.minecraft.client.renderer.chunk.RenderChunk".equals(className)
                    || "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher".equals(className)) {
                return true;
            }
        }
        return false;
    }

    private boolean filterBetterPortalsChunkUpdates(RenderGlobalAccessor accessor, World allowedWorld) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        int before = chunksToUpdate.size();
        chunksToUpdate.removeIf(chunk -> !isValidBetterPortalsChunkUpdate(chunk, allowedWorld));
        int removed = before - chunksToUpdate.size();
        if (removed > 0) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Removed {} stale chunk update(s) from Better Portals render pass", removed);
        }
        return !chunksToUpdate.isEmpty();
    }

    private boolean isValidBetterPortalsChunkUpdate(RenderChunk chunk, World allowedWorld) {
        if (chunk == null) {
            return false;
        }

        World chunkWorld = renderChunkWorld(chunk);
        if (chunkWorld == null) {
            return assignRenderChunkWorld(chunk, allowedWorld);
        }
        return chunkWorld == allowedWorld;
    }

    private boolean hasInvalidBetterPortalsChunkUpdate(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (chunk == null || renderChunkWorld(chunk) == null) {
                return true;
            }
        }
        return false;
    }

    private World renderChunkWorld(RenderChunk chunk) {
        if (chunk instanceof RenderChunkAccessor accessor) {
            return accessor.ausm$world();
        }
        return chunk.getWorld();
    }

    private boolean assignRenderChunkWorld(RenderChunk chunk, World world) {
        if (chunk == null || world == null || !(chunk instanceof RenderChunkAccessor accessor)) {
            return false;
        }
        try {
            accessor.ausm$setWorld(world);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void clearRenderGlobalChunkUpdates(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate != null && !chunksToUpdate.isEmpty()) {
            chunksToUpdate.clear();
        }
    }

    private int safeDimensionId(World world) {
        return world != null && world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
    }

    public void prepareBypassedWorldPassRendering() {
        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            untouchedBetterPortalsVanillaRendererStack.push(prepareUntouchedBetterPortalsRenderPass());
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare untouched-bp-pass");
            return;
        }
        untouchedBetterPortalsVanillaRendererStack.push(false);
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !shouldRenderBetterPortalsNestedViewWithShaders();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare nested=" + nestedBetterPortalsView
                + " vanillaRenderer=" + useNestedVanillaRenderer);
        if (!isPipelineActive && !nestedBetterPortalsView) {
            prepareInactiveVanillaFrame();
        }
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? mc.world : null);
        if (useNestedVanillaRenderer || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            ensureVanillaTerrainRenderer(targetWorld, true);
        } else if (!nestedBetterPortalsView) {
            ensureVanillaTerrainRenderer(targetWorld);
        }

        restoreVanillaWorldPassState(!nestedBetterPortalsView, !nestedBetterPortalsView);
    }

    public void finishBypassedWorldPassRendering() {
        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()
                    && untouchedBetterPortalsVanillaRendererStack.pop()) {
                popVanillaTerrainRendererState();
            }
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish untouched-bp-pass");
            return;
        }
        if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()) {
            untouchedBetterPortalsVanillaRendererStack.pop();
        }
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-before");
        restoreVanillaWorldPassState(false, true);
        popVanillaTerrainRendererState();
        shaderlessWorldPassActive = false;
        restoreActiveWorldPassAfterExternalShader();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-after");
    }

    private boolean prepareUntouchedBetterPortalsRenderPass() {
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !shouldRenderBetterPortalsNestedViewWithShaders();
        boolean mustEnsureVanillaRenderer = useNestedVanillaRenderer || NothiriumBypass.shouldBypass();
        if (!mustEnsureVanillaRenderer) {
            return false;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? mc.world : null);
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        ensureVanillaTerrainRenderer(targetWorld, useNestedVanillaRenderer);
        return useNestedVanillaRenderer;
    }

    private void renderNativeBloomLayerIfNeeded() {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()) {
            return;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(currentWorldPartialTicks, currentWorldPass);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }

        renderAusmBloomLayer(
                mc.renderGlobal,
                currentWorldPartialTicks,
                currentWorldPass,
                mc.getRenderViewEntity()
        );
    }

    public void renderNativeAusmBloomLayerFromWorldPass(float partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || !isPipelineActive) {
            return;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(partialTicks, pass);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }

        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        renderAusmBloomLayer(mc.renderGlobal, partialTicks, pass, mc.getRenderViewEntity());
    }

    public int renderShaderlessVisibleBloomLayerFromWorldPass(float partialTicks, int pass) {
        boolean nativeHook = AusmBloomLayer.shouldUseNativeHook();
        boolean nestedBetterPortalsPass = isRenderingBetterPortalsRenderPass() && isRenderingBetterPortalsNestedView();
        if (isPipelineActive
                || nativeHook
                || bloomLayerRenderedThisWorldPass
                || renderingGuiScreen()
                || renderingShadowMap
                || nestedBetterPortalsPass) {
            logVisibleBloomDiag("skip-guard", pass, 0, "active=" + isPipelineActive
                    + ", nativeHook=" + nativeHook
                    + ", bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                    + ", gui=" + renderingGuiScreen()
                    + ", shadow=" + renderingShadowMap
                    + ", bpPass=" + isRenderingBetterPortalsRenderPass()
                    + ", bpNested=" + isRenderingBetterPortalsNestedView());
            return 0;
        }

        Minecraft mc = Minecraft.getMinecraft();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (mc == null || mc.renderGlobal == null || bloomLayer == null) {
            logVisibleBloomDiag("skip-missing-state", pass, 0, "mc=" + (mc != null)
                    + ", renderGlobal=" + (mc != null && mc.renderGlobal != null)
                    + ", bloomLayer=" + bloomLayer);
            return 0;
        }

        Framebuffer bloomTarget = isRenderingBetterPortalsRenderPass()
                ? BetterPortalsCompat.currentRenderPassFramebuffer()
                : null;
        if (bloomTarget == null) {
            bloomTarget = mc.getFramebuffer();
        }

        int bloomRendered = 0;
        if (bloomTarget != null) {
            bloomRendered = bloomRenderer.renderBloomLayer(
                    mc.renderGlobal,
                    partialTicks,
                    pass,
                    mc.getRenderViewEntity(),
                    null,
                    bloomTarget,
                    false
            );
        }

        int rendered = bloomRendered;
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
            logVisibleBloomDiag("rendered", pass, rendered, "layer=" + bloomLayer
                    + ", bloomRendered=" + bloomRendered
                    + ", visibleRendered=0"
                    + ", target=" + describeFramebufferTarget(bloomTarget)
                    + ", renderGlobalWorld=" + safeDimensionId(renderWorld(mc))
                    + ", clientWorld=" + safeDimensionId(mc.world)
                    + ", gl=" + glStateSummary());
            logShaderlessBloomHook("captured-bloom-layer rendered=" + rendered + " pass=" + pass);
        }

        return rendered;
    }

    private void logVisibleBloomDiag(String stage, int pass, int rendered, String detail) {
        if (visibleBloomDiagLogs >= MAX_VISIBLE_BLOOM_DIAG_LOGS) {
            return;
        }
        visibleBloomDiagLogs++;
        MainMod.LOGGER.info(
                "[AUSMVisibleBloomDiag] call={} stage={} pass={} rendered={} active={} shaderlessWorldPass={} worldFrame={} bypass={} bp={} detail={}",
                visibleBloomDiagLogs,
                stage,
                pass,
                rendered,
                isPipelineActive,
                shaderlessWorldPassActive,
                worldFrameActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.describeTransitionState(),
                detail
        );
    }

    public int renderAusmBloomLayer(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity) {
        if (renderingGuiScreen() || renderingShadowMap) {
            return 0;
        }
        if (!AusmBloomLayer.shouldUseNativeHook()) {
            return 0;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(partialTicks, pass);
            return 0;
        }
        if (bloomLayerRenderedThisWorldPass) {
            return 0;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return 0;
        }

        pendingDeferredNativeBloom = false;
        Entity renderEntity = entity != null ? entity : mc.getRenderViewEntity();
        Framebuffer minecraftTarget = mc.getFramebuffer();
        DeferredFramebuffer pipelineDepthSource = isPipelineActive && worldFrameActive && pingPongManager.isInitialized()
                ? pingPongManager.getReadBuffer()
                : null;
        boolean deferComposite = pipelineDepthSource != null;

        int rendered = bloomRenderer.renderBloomLayer(
                renderGlobal,
                partialTicks,
                pass,
                renderEntity,
                pipelineDepthSource,
                minecraftTarget,
                deferComposite
        );
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        }
        recordBloomRenderResult(rendered);
        return rendered;
    }

    private void requestDeferredNativeBloom(double partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        pendingDeferredNativeBloom = true;
        pendingDeferredBloomPartialTicks = partialTicks;
        pendingDeferredBloomPass = pass;
    }

    private void renderDeferredNativeBloomIfNeeded() {
        if (!pendingDeferredNativeBloom
                || bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }

        double partialTicks = pendingDeferredBloomPartialTicks;
        int pass = pendingDeferredBloomPass;
        pendingDeferredNativeBloom = false;
        renderAusmBloomLayer(mc.renderGlobal, partialTicks, pass, mc.getRenderViewEntity());
    }

    private void recordBloomRenderResult(int rendered) {
        if (rendered > 0) {
            bloomZeroGeometryFrames = 0;
            return;
        }
        if (!bloomRenderer.hasBloomResources()) {
            return;
        }

        bloomZeroGeometryFrames++;
        if (bloomZeroGeometryFrames < 20 || bloomZeroGeometryRefreshCooldown > 0) {
            return;
        }

        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 240;
        MainMod.LOGGER.info("[AUSMBloom] BLOOM resources are present, but no BLOOM geometry was produced; skipping terrain refresh to avoid chunk rebuild flashes.");
    }

    private void renderPostWorldBloom(Framebuffer target, boolean externalTarget) {
        Minecraft mc = Minecraft.getMinecraft();
        if (target == null
                || mc == null
                || mc.world == null
                || mc.getRenderViewEntity() == null
                || externalTarget
                || renderingGuiScreen()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        renderDeferredNativeBloomIfNeeded();
        if (bloomLayerRenderedThisWorldPass || bloomLayerRenderedThisWorldFrame) {
            return;
        }
        bloomRenderer.renderPostWorldBloom(target);
    }

    public void renderShaderlessBloomBeforeGui() {
        if (isPipelineActive) {
            return;
        }
        if (shaderlessBloomRenderedThisWorldPass) {
            logShaderlessBloomHook("skip already-rendered");
            return;
        }
        if (externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || renderingGuiScreen()) {
            logShaderlessBloomHook("skip state external=" + describeFramebufferTarget(externalWorldFramebufferTarget)
                    + " nested=" + isRenderingBetterPortalsNestedView()
                    + " renderPass=" + isRenderingBetterPortalsRenderPass()
                    + " gui=" + renderingGuiScreen());
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderViewEntity() == null || mc.getFramebuffer() == null) {
            logShaderlessBloomHook("skip missing-minecraft-state mc=" + (mc != null)
                    + " world=" + (mc != null && mc.world != null)
                    + " entity=" + (mc != null && mc.getRenderViewEntity() != null)
                    + " framebuffer=" + (mc != null && mc.getFramebuffer() != null));
            return;
        }

        logShaderlessBloomHook("render target=" + describeFramebufferTarget(mc.getFramebuffer())
                + " bloomResources=" + bloomRenderer.hasBloomResources()
                + " nativeBloom=" + AusmBloomLayer.shouldUseNativeHook()
                + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                + " renderPass=" + isRenderingBetterPortalsRenderPass());
        refreshShaderlessBloomVertexFormatIfNeeded();
        renderNativeBloomLayerIfNeeded();
        boolean shaderlessExtractRendered = false;
        if (!AusmBloomLayer.shouldUseNativeHook()) {
            shaderlessExtractRendered = bloomRenderer.renderShaderlessEmissiveTerrainBloom(
                    mc.getFramebuffer(),
                    () -> renderShaderlessNothiriumEmissiveTerrain(mc.getRenderPartialTicks(), mc.getRenderViewEntity())
            );
            logShaderlessBloomHook("extract-rendered=" + shaderlessExtractRendered
                    + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                    + " bypass=" + NothiriumBypass.shouldBypass());
        }
        if (shaderlessExtractRendered) {
            bloomRenderer.renderPostWorldBloom(mc.getFramebuffer());
            logShaderlessBloomHook("extract-composited");
        } else {
            renderPostWorldBloom(mc.getFramebuffer(), false);
        }
        shaderlessBloomRenderedThisWorldPass = true;
        shaderlessBloomRenderedThisWorldFrame = true;
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);
    }

    private void refreshShaderlessBloomVertexFormatIfNeeded() {
        if (isPipelineActive
                || shaderlessBloomVertexFormatRefreshRequested
                || AusmBloomLayer.shouldUseNativeHook()
                || !bloomRenderer.hasBloomResources()) {
            return;
        }

        shaderlessBloomVertexFormatRefreshRequested = true;
        boolean recreateNothirium = updateNothiriumPipelineBlockFormatMode();
        rebuildTerrainRenderers(recreateNothirium, false);
    }

    private int renderShaderlessNothiriumEmissiveTerrain(float partialTicks, Entity viewEntity) {
        if (isPipelineActive || viewEntity == null) {
            return 0;
        }
        if (!NothiriumShadowRenderer.isAvailable() || NothiriumBypass.shouldBypass()) {
            return renderShaderlessVanillaEmissiveTerrain(partialTicks, viewEntity);
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        nothiriumShadowRenderer.drainUploads();

        int solid = positiveCount(nothiriumShadowRenderer.renderVisibleLayer(BlockRenderLayer.SOLID, cameraX, cameraY, cameraZ, 0, (short) 0));
        int cutoutMipped = positiveCount(nothiriumShadowRenderer.renderVisibleLayer(BlockRenderLayer.CUTOUT_MIPPED, cameraX, cameraY, cameraZ, 0, (short) 0));
        int cutout = positiveCount(nothiriumShadowRenderer.renderVisibleLayer(BlockRenderLayer.CUTOUT, cameraX, cameraY, cameraZ, 0, (short) 0));
        int translucent = positiveCount(nothiriumShadowRenderer.renderVisibleLayer(BlockRenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ, 0, (short) 0));
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        int bloom = 0;
        if (bloomLayer != null) {
            bloom = positiveCount(nothiriumShadowRenderer.renderVisibleLayer(bloomLayer, cameraX, cameraY, cameraZ, 0, (short) 0));
        }
        int rendered = solid + cutoutMipped + cutout + translucent + bloom;
        logShaderlessBloomHook("nothirium-extract-count solid=" + solid
                + " cutoutMipped=" + cutoutMipped
                + " cutout=" + cutout
                + " translucent=" + translucent
                + " bloom=" + bloom
                + " total=" + rendered
                + " bloomLayer=" + bloomLayer
                + " bypass=" + NothiriumBypass.shouldBypass());
        return rendered;
    }

    private int renderShaderlessVanillaEmissiveTerrain(float partialTicks, Entity viewEntity) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null || mc.world == null || viewEntity == null) {
            return 0;
        }

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        shaderlessWorldPassActive = true;
        try {
            int solid = renderShaderlessVanillaEmissiveLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
            int cutoutMipped = renderShaderlessVanillaEmissiveLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
            int cutout = renderShaderlessVanillaEmissiveLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            int translucent = renderShaderlessVanillaEmissiveLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            int bloom = bloomLayer != null
                    ? renderShaderlessVanillaEmissiveLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            int rendered = solid + cutoutMipped + cutout + translucent + bloom;
            logShaderlessBloomHook("vanilla-extract-count solid=" + solid
                    + " cutoutMipped=" + cutoutMipped
                    + " cutout=" + cutout
                    + " translucent=" + translucent
                    + " bloom=" + bloom
                    + " total=" + rendered
                    + " bypass=" + NothiriumBypass.shouldBypass());
            return rendered;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    private int renderShaderlessVanillaEmissiveLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                     float partialTicks, Entity viewEntity) {
        activePhase = phase;
        prepareShaderlessBlockLayerState(layer);
        try {
            return positiveCount(mc.renderGlobal.renderBlockLayer(layer, partialTicks, 2, viewEntity));
        } finally {
            finishShaderlessBlockLayerState(layer);
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    private static int positiveCount(int count) {
        return Math.max(0, count);
    }

    private void logShaderlessBloomHook(String detail) {
        if (shaderlessBloomHookLogs >= MAX_SHADERLESS_BLOOM_HOOK_LOGS) {
            return;
        }
        shaderlessBloomHookLogs++;
        MainMod.LOGGER.info("[AUSMBloom] Shaderless pre-GUI hook {}", detail);
    }

    private static String glStateSummary() {
        return "program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                + ",activeTex=" + GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                + ",clientTex=" + GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE)
                + ",tex=" + GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                + ",blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                + ",blendFunc=" + GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
                + ",alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                + ",alphaFunc=" + GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)
                + ",alphaRef=" + GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)
                + ",depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                + ",depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                + ",depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }

    public void prepareExternalOverlayRender(String source) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getFramebuffer() == null) {
            return;
        }

        if (externalOverlayLogs < MAX_EXTERNAL_OVERLAY_LOGS) {
            externalOverlayLogs++;
            MainMod.LOGGER.info("[PipelineCompat] Preparing external overlay renderer: {} active={} worldFrame={} gui={} framebuffer={}",
                    source,
                    isPipelineActive,
                    worldFrameActive,
                    renderingGuiScreen(),
                    describeFramebufferTarget(mc.getFramebuffer()));
        }

        if (isPipelineActive
                && worldFrameActive
                && externalWorldFramebufferTarget == null
                && !isRenderingBetterPortalsNestedView()) {
            prepareFramebufferPresentation();
        }

        mc.getFramebuffer().bindFramebuffer(false);
        GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        if (mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.colorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    public void finishExternalOverlayRender(String source) {
        restoreGuiSafeRenderState(source);
    }

    public void finishExternalWorldOverlayRender(String source) {
        restoreWorldSafeRenderState(source);
    }

    private void restoreGuiSafeRenderState(String source) {
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
    }

    private void restoreWorldSafeRenderState(String source) {
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture2D();
        bindBlockAtlas();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableBlend();
    }

    public void restoreActiveWorldPassAfterExternalShader() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingGuiScreen()) {
            return;
        }

        RenderPass pass = activePass;
        WorldRenderingPhase phase = activePhase;
        bindWorldFramebuffer();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GlStateManager.colorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        bindPass(pass);
        activePhase = phase;
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:restore-active-after-external pass=" + pass + " phase=" + phase);
    }

    public void prepareFramebufferPresentation() {
        if (!isPipelineActive) {
            if (externalWorldFramebufferTarget == null && !isRenderingBetterPortalsNestedView()) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc != null && mc.world != null && mc.getRenderViewEntity() != null && mc.currentScreen == null) {
                    OpenGlHelper.glUseProgram(0);
                    TextureBinder.restoreDefaultTextureUnit();
                    GlStateManager.bindTexture(0);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.enableTexture2D();
                    GlStateManager.colorMask(true, true, true, true);
                }
            }
            return;
        }

        if (externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        if (worldFrameActive) {
            renderDeferredNativeBloomIfNeeded();
            blitWorldFramebufferToMinecraft();
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.colorMask(true, true, true, true);
    }

    public void prepareGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        renderingGui = true;
        bindGuiTarget();
        prepareGuiState();
    }

    public void prepareGuiFramebuffer() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        bindGuiTarget();
        prepareGuiState();
    }

    public void beginGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
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
            restoreGuiSafeRenderState("gui-finish");
        }
    }

    private void bindGuiTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
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
        if (mc != null && mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
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
        setIndexedBlend(0, true);
    }

    public boolean shouldDirectPresentFramebuffer() {
        Minecraft mc = Minecraft.getMinecraft();
        return isPipelineActive
                && mc != null
                && mc.gameSettings != null
                && externalWorldFramebufferTarget == null
                && !isRenderingBetterPortalsNestedView()
                && mc.gameSettings.thirdPersonView != 0;
    }

    public boolean shouldDeferIngameHud() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && shouldDirectPresentFramebuffer() && mc.currentScreen == null && !renderingDeferredIngameHud;
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
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || target == null || target != mc.getFramebuffer()) {
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

    public boolean shouldUseNeutralShadowTextures() {
        return isBetterPortalsExternalWorldTarget();
    }

    public boolean shouldUseShadowHardwareFiltering() {
        return packDirectives.renderTargets().shadowHardwareFiltering();
    }

    public void configureShadowDepthTextureCompareMode() {
        if (shadowFramebuffer != null) {
            shadowFramebuffer.configureDepthTextureCompareMode();
        }
    }

    public void setActive(boolean active) {
        boolean wasPipelineActive = isPipelineActive;
        isPipelineActive = active && pingPongManager.isInitialized();
        if (isPipelineActive) {
            betterPortalsPipelineLogs = 0;
            BetterPortalsCompat.resetRenderStateDiagnostics();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.world != null) {
                resizeFramebuffer(mc.displayWidth, mc.displayHeight, true);
            }
        } else {
            clearPendingShaderChunkRefreshes();
            scheduleInactiveVanillaRecoveryFrame();
            resetPipelineState();
        }
        boolean nothiriumFormatChanged = updateNothiriumPipelineBlockFormatMode();
        if (wasPipelineActive != isPipelineActive || nothiriumFormatChanged) {
            rebuildTerrainRenderers(nothiriumFormatChanged);
        }
    }

    public void rebuildTerrainRenderers() {
        rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode());
    }

    private void rebuildTerrainRenderers(boolean recreateNothiriumRenderer) {
        rebuildTerrainRenderers(recreateNothiriumRenderer, true);
    }

    private void rebuildTerrainRenderers(boolean recreateNothiriumRenderer, boolean reloadVanillaRenderGlobal) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }
        logTerrainDiagnostic("rebuild-terrain-renderers", mc.world, "recreateNothirium=" + recreateNothiriumRenderer
                + ", reloadVanilla=" + reloadVanillaRenderGlobal);
        if (isPipelineActive) {
            ensureVanillaTerrainRenderer();
        }
        if (recreateNothiriumRenderer) {
            NothiriumBypass.recreateRenderer();
        } else {
            NothiriumBypass.markAllChanged();
        }
        if (reloadVanillaRenderGlobal) {
            mc.renderGlobal.loadRenderers();
        }
    }

    private boolean updateNothiriumPipelineBlockFormatMode() {
        boolean active = shouldUsePipelineBlockFormat();
        if (nothiriumPipelineBlockFormatActive == active) {
            return false;
        }
        nothiriumPipelineBlockFormatActive = active;
        return true;
    }

    public int[] forceLightRecalculation() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return new int[]{0, 0, 0, 0};
        }

        World world = mc.world;
        BlockPos center = new BlockPos(mc.player);
        int horizontalRadius = Math.min(
                FORCE_LIGHT_RECALC_MAX_RADIUS,
                Math.max(FORCE_LIGHT_RECALC_MIN_RADIUS, mc.gameSettings.renderDistanceChunks * 16)
        );
        int verticalRadius = horizontalRadius;
        int minX = center.getX() - horizontalRadius;
        int maxX = center.getX() + horizontalRadius;
        int minY = Math.max(0, center.getY() - verticalRadius);
        int maxY = Math.min(255, center.getY() + verticalRadius);
        int minZ = center.getZ() - horizontalRadius;
        int maxZ = center.getZ() + horizontalRadius;

        syntheticLightCandidates.clear();
        if (shaderImages.active()) {
            clearColoredLightImages();
        }
        resetColoredLightAudit();

        int chunkCount = forceChunkLightingRefresh(world, minX, maxX, minZ, maxZ);
        int blockChecks = forceBlockLightingRefresh(world, minX, minY, minZ, maxX, maxY, maxZ);

        world.markBlockRangeForRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ);
        refreshVanillaLightmap(mc);
        rebuildTerrainRenderers();

        MainMod.LOGGER.info(
                "[Lighting] Forced nearby light recalculation radius={} verticalRadius={} chunks={} blockChecks={} shadersActive={}",
                horizontalRadius,
                verticalRadius,
                chunkCount,
                blockChecks,
                isPipelineActive
        );
        return new int[]{horizontalRadius, chunkCount, blockChecks, isPipelineActive ? 1 : 0};
    }

    public void scheduleWorldLoadLightRecalculation() {
        pendingWorldLoadLightRecalculationAttempts = WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
    }

    public void clearScheduledWorldLoadLightRecalculation() {
        pendingWorldLoadLightRecalculationAttempts = 0;
        pendingWorldLoadLightRecalculationDelay = 0;
    }

    public void scheduleWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(false);
    }

    public void scheduleFullWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true);
    }

    private void scheduleDimensionSwitchTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, false);
    }

    private void scheduleWorldTerrainRefresh(boolean fullRendererReset) {
        scheduleWorldTerrainRefresh(fullRendererReset, fullRendererReset);
    }

    private void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload) {
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc != null && mc.world != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE;
        if (pendingWorldTerrainRefreshAttempts > 0 && pendingWorldTerrainRefreshDimension == dimension) {
            logTerrainDiagnostic("schedule-world-terrain:coalesce",
                    mc != null ? mc.world : null,
                    "fullReset=" + fullRendererReset
                            + ", vanillaReload=" + vanillaReload
                            + ", oldAttempts=" + pendingWorldTerrainRefreshAttempts
                            + ", oldDelay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshAttempts = Math.max(pendingWorldTerrainRefreshAttempts, WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS);
            pendingWorldTerrainRefreshDelay = Math.min(pendingWorldTerrainRefreshDelay, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES);
            pendingWorldTerrainRendererReset |= fullRendererReset;
            pendingWorldTerrainFullRendererReset |= fullRendererReset;
            pendingWorldTerrainVanillaReload |= vanillaReload;
            return;
        }

        pendingWorldTerrainRefreshAttempts = WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS;
        pendingWorldTerrainRefreshDelay = WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES;
        pendingWorldTerrainRefreshDimension = dimension;
        pendingWorldTerrainRendererReset = fullRendererReset;
        pendingWorldTerrainFullRendererReset = fullRendererReset;
        pendingWorldTerrainVanillaReload = vanillaReload;
        logTerrainDiagnostic("schedule-world-terrain:new",
                mc != null ? mc.world : null,
                "fullReset=" + fullRendererReset + ", vanillaReload=" + vanillaReload);
    }

    public void clearScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts > 0) {
            Minecraft mc = Minecraft.getMinecraft();
            logTerrainDiagnostic("schedule-world-terrain:clear",
                    mc != null ? mc.world : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts
                            + ", delay=" + pendingWorldTerrainRefreshDelay
                            + ", dim=" + pendingWorldTerrainRefreshDimension
                            + ", rendererReset=" + pendingWorldTerrainRendererReset
                            + ", fullReset=" + pendingWorldTerrainFullRendererReset
                            + ", vanillaReload=" + pendingWorldTerrainVanillaReload);
        }
        pendingWorldTerrainRefreshAttempts = 0;
        pendingWorldTerrainRefreshDelay = 0;
        pendingWorldTerrainRefreshDimension = Integer.MIN_VALUE;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainFullRendererReset = false;
        pendingWorldTerrainVanillaReload = false;
    }

    public void queueShaderChunkRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null || !isPipelineActive) {
            return;
        }

        synchronized (pendingShaderChunkRefreshes) {
            if (pendingShaderChunkRefreshes.size() >= MAX_PENDING_SHADER_CHUNK_REFRESHES) {
                ShaderChunkRefresh oldest = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(oldest);
            }
            pendingShaderChunkRefreshes.add(new ShaderChunkRefresh(world, chunkX, chunkZ));
        }
    }

    public void queueClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason) {
        if (world == null) {
            return;
        }

        ClientChunkRenderRefresh refresh = new ClientChunkRenderRefresh(
                world,
                chunkX,
                chunkZ,
                reason != null ? reason : "unknown",
                CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS,
                CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES
        );
        synchronized (pendingClientChunkRenderRefreshes) {
            if (pendingClientChunkRenderRefreshes.size() >= MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES) {
                ClientChunkRenderRefresh oldest = pendingClientChunkRenderRefreshes.iterator().next();
                pendingClientChunkRenderRefreshes.remove(oldest);
            }
            pendingClientChunkRenderRefreshes.remove(refresh);
            pendingClientChunkRenderRefreshes.add(refresh);
        }
    }

    public void clearPendingShaderChunkRefreshes() {
        synchronized (pendingShaderChunkRefreshes) {
            pendingShaderChunkRefreshes.clear();
        }
    }

    public void clearPendingClientChunkRenderRefreshes() {
        synchronized (pendingClientChunkRenderRefreshes) {
            pendingClientChunkRenderRefreshes.clear();
        }
    }

    public void runPendingShaderChunkRefreshes() {
        if (!isPipelineActive) {
            clearPendingShaderChunkRefreshes();
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }

        for (int i = 0; i < MAX_SHADER_CHUNK_REFRESHES_PER_FRAME; i++) {
            ShaderChunkRefresh refresh;
            synchronized (pendingShaderChunkRefreshes) {
                if (pendingShaderChunkRefreshes.isEmpty()) {
                    return;
                }
                refresh = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(refresh);
            }

            refreshShaderChunk(mc, refresh);
        }
    }

    public void runPendingClientChunkRenderRefreshes() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null) {
            return;
        }

        for (int i = 0; i < MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME; i++) {
            ClientChunkRenderRefresh refresh = pollDueClientChunkRenderRefresh();
            if (refresh == null) {
                return;
            }
            if (refresh.world != mc.world) {
                continue;
            }

            refreshClientChunkRender(mc, refresh);
            refresh.attemptsRemaining--;
            if (refresh.attemptsRemaining > 0 && refresh.world == mc.world) {
                refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                synchronized (pendingClientChunkRenderRefreshes) {
                    pendingClientChunkRenderRefreshes.add(refresh);
                }
            }
        }
    }

    private ClientChunkRenderRefresh pollDueClientChunkRenderRefresh() {
        synchronized (pendingClientChunkRenderRefreshes) {
            Iterator<ClientChunkRenderRefresh> iterator = pendingClientChunkRenderRefreshes.iterator();
            while (iterator.hasNext()) {
                ClientChunkRenderRefresh refresh = iterator.next();
                if (refresh.delayFrames > 0) {
                    refresh.delayFrames--;
                    continue;
                }
                iterator.remove();
                return refresh;
            }
        }
        return null;
    }

    private void refreshClientChunkRender(Minecraft mc, ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || mc.world != refresh.world || mc.renderGlobal == null) {
            return;
        }

        ChunkProviderClient chunkProvider = refresh.world.getChunkProvider();
        Chunk chunk = chunkProvider != null ? chunkProvider.getLoadedChunk(refresh.chunkX, refresh.chunkZ) : null;
        boolean loaded = chunk != null;
        int scheduledChunks = 0;
        if (loaded) {
            ensureVanillaTerrainRenderer(refresh.world, false);
            int minX = refresh.chunkX << 4;
            int minZ = refresh.chunkZ << 4;
            int maxX = minX + 15;
            int maxZ = minZ + 15;
            refresh.world.markBlockRangeForRenderUpdate(minX, 0, minZ, maxX, 255, maxZ);

            if (mc.renderGlobal instanceof RenderGlobalAccessor accessor) {
                ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
                forceUpdateVanillaViewFrustumChunkPositions(
                        viewFrustum,
                        mc.getRenderViewEntity(),
                        refresh.world,
                        "client-chunk-render-refresh"
                );
                scheduledChunks = scheduleLoadedClientChunkRenderChunks(
                        accessor,
                        viewFrustum,
                        refresh.world,
                        refresh.chunkX,
                        refresh.chunkZ
                );
                accessor.ausm$setDisplayListEntitiesDirty(true);
            }

            if (isPipelineActive && !NothiriumBypass.shouldBypass()) {
                nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
            }
        }

        logClientChunkRenderRefresh(refresh, loaded, scheduledChunks);
    }

    private int scheduleLoadedClientChunkRenderChunks(RenderGlobalAccessor renderGlobal, ViewFrustum viewFrustum,
                                                     World world, int chunkX, int chunkZ) {
        if (renderGlobal == null || viewFrustum == null || viewFrustum.renderChunks == null) {
            return 0;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return 0;
        }

        int scheduled = 0;
        for (RenderChunk renderChunk : viewFrustum.renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            BlockPos position = renderChunk.getPosition();
            if (position == null || (position.getX() >> 4) != chunkX || (position.getZ() >> 4) != chunkZ) {
                continue;
            }
            assignRenderChunkWorld(renderChunk, world);
            renderChunk.setNeedsUpdate(true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
        }
        return scheduled;
    }

    private void logClientChunkRenderRefresh(ClientChunkRenderRefresh refresh, boolean loaded, int scheduledChunks) {
        if (clientChunkRenderRefreshLogs >= MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS) {
            return;
        }
        clientChunkRenderRefreshLogs++;
        MainMod.LOGGER.info(
                "[AUSMClientChunkRefresh] call={} reason={} world={} chunk={},{} loaded={} scheduledChunks={} attemptsLeft={} active={} bypass={} bp={}",
                clientChunkRenderRefreshLogs,
                refresh.reason,
                safeDimensionId(refresh.world),
                refresh.chunkX,
                refresh.chunkZ,
                loaded,
                scheduledChunks,
                refresh.attemptsRemaining,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.describeTransitionState()
        );
    }

    private void refreshShaderChunk(Minecraft mc, ShaderChunkRefresh refresh) {
        if (refresh == null || refresh.world == null || refresh.world.provider == null) {
            return;
        }

        if (mc.world != refresh.world) {
            return;
        }

        if (!NothiriumBypass.shouldBypass()) {
            nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
        }
    }

    public void scheduleBloomTerrainRefresh(String reason) {
        // Rebuilding chunks as a bloom recovery mechanism causes visible F3+A-like terrain flashes,
        // especially with Nothirium. Bloom must be produced by the render hooks, not by repeated
        // global terrain invalidation.
    }

    public void clearScheduledBloomTerrainRefresh() {
        pendingBloomTerrainRefreshAttempts = 0;
        pendingBloomTerrainRefreshDelay = 0;
        pendingBloomTerrainRefreshReason = "";
        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 0;
    }

    public void runScheduledBloomTerrainRefresh() {
        if (bloomZeroGeometryRefreshCooldown > 0) {
            bloomZeroGeometryRefreshCooldown--;
        }
        if (pendingBloomTerrainRefreshAttempts <= 0) {
            return;
        }
        if (pendingBloomTerrainRefreshDelay > 0) {
            pendingBloomTerrainRefreshDelay--;
            return;
        }

        pendingBloomTerrainRefreshAttempts--;
        pendingBloomTerrainRefreshDelay = 20;
        if (refreshBloomTerrainState(pendingBloomTerrainRefreshReason)
                && pendingBloomTerrainRefreshAttempts <= 0) {
            pendingBloomTerrainRefreshReason = "";
        }
    }

    public void runScheduledWorldLoadLightRecalculation() {
        if (pendingWorldLoadLightRecalculationAttempts <= 0) {
            return;
        }
        if (pendingWorldLoadLightRecalculationDelay > 0) {
            pendingWorldLoadLightRecalculationDelay--;
            return;
        }

        pendingWorldLoadLightRecalculationAttempts--;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        if (refreshWorldLoadLightState()) {
            pendingWorldLoadLightRecalculationAttempts = 0;
            pendingWorldLoadLightRecalculationDelay = 0;
            MainMod.LOGGER.info("[Lighting] Refreshed scheduled world-load light state.");
        }
    }

    public void runScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts <= 0) {
            return;
        }
        if (pendingWorldTerrainRefreshDelay > 0) {
            logTerrainDiagnostic("run-world-terrain:delay",
                    Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshDelay--;
            return;
        }

        logTerrainDiagnostic("run-world-terrain:start",
                Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null,
                "attempts=" + pendingWorldTerrainRefreshAttempts);
        if (refreshWorldTerrainState()) {
            pendingWorldTerrainRefreshAttempts--;
        }

        if (pendingWorldTerrainRefreshAttempts <= 0) {
            logTerrainDiagnostic("run-world-terrain:done",
                    Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null,
                    "");
            clearScheduledWorldTerrainRefresh();
        } else {
            pendingWorldTerrainRefreshDelay = WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES;
            logTerrainDiagnostic("run-world-terrain:reschedule",
                    Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
        }
    }

    private boolean refreshBloomTerrainState(String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return false;
        }
        if (!AusmBloomLayer.isAvailable() || !bloomRenderer.hasBloomResources()) {
            return false;
        }

        BlockPos center = new BlockPos(mc.player);
        int radius = Math.max(32, Math.min(128, mc.gameSettings.renderDistanceChunks * 16));
        mc.world.markBlockRangeForRenderUpdate(
                center.getX() - radius,
                Math.max(0, center.getY() - radius),
                center.getZ() - radius,
                center.getX() + radius,
                Math.min(255, center.getY() + radius),
                center.getZ() + radius
        );
        boolean nothiriumDirty = NothiriumBypass.markAllChanged();
        MainMod.LOGGER.info("[AUSMBloom] Refreshed bloom terrain state reason={} world={} radius={} nothiriumDirty={} remainingAttempts={}",
                reason,
                safeDimensionId(mc.world),
                radius,
                nothiriumDirty,
                pendingBloomTerrainRefreshAttempts);
        return true;
    }

    private boolean refreshWorldTerrainState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return false;
        }

        int dimension = safeDimensionId(mc.world);
        if (pendingWorldTerrainRefreshDimension != Integer.MIN_VALUE
                && pendingWorldTerrainRefreshDimension != dimension) {
            logTerrainDiagnostic("refresh-world-terrain:dimension-mismatch", mc.world,
                    "pendingDim=" + pendingWorldTerrainRefreshDimension + ", currentDim=" + dimension);
            clearScheduledWorldTerrainRefresh();
            return false;
        }

        boolean rendererReset = pendingWorldTerrainRendererReset;
        boolean vanillaReload = pendingWorldTerrainVanillaReload;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainVanillaReload = false;

        if (pendingWorldTerrainFullRendererReset) {
            pendingWorldTerrainFullRendererReset = false;
            logTerrainDiagnostic("refresh-world-terrain:full-reset", mc.world,
                    "rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
            if (rendererReset) {
                deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
            }
            rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode(), vanillaReload);
            scheduleInactiveVanillaRecoveryFrame();
            return true;
        }

        ensureVanillaTerrainRenderer(mc.world, false);
        BlockPos center = new BlockPos(mc.player);
        int radius = Math.max(32, Math.min(128, mc.gameSettings.renderDistanceChunks * 16));
        logTerrainDiagnostic("refresh-world-terrain:range", mc.world,
                "center=" + center + ", radius=" + radius + ", rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
        mc.world.markBlockRangeForRenderUpdate(
                center.getX() - radius,
                0,
                center.getZ() - radius,
                center.getX() + radius,
                255,
                center.getZ() + radius
        );
        if (isPipelineActive || NothiriumBypass.shouldBypass()) {
            NothiriumBypass.markAllChanged();
            scheduleInactiveVanillaRecoveryFrame();
        }
        return true;
    }

    private boolean refreshWorldLoadLightState() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return false;
        }

        refreshVanillaLightmap(mc);
        if (!isPipelineActive) {
            return true;
        }
        BlockPos center = new BlockPos(mc.player);
        int radius = WORLD_LOAD_LIGHT_REFRESH_RADIUS;
        mc.world.markBlockRangeForRenderUpdate(
                center.getX() - radius,
                Math.max(0, center.getY() - radius),
                center.getZ() - radius,
                center.getX() + radius,
                Math.min(255, center.getY() + radius),
                center.getZ() + radius
        );
        return true;
    }

    private int forceChunkLightingRefresh(World world, int minX, int maxX, int minZ, int maxZ) {
        if (!(world instanceof WorldClient worldClient)) {
            return 0;
        }
        ChunkProviderClient chunkProvider = worldClient.getChunkProvider();
        if (chunkProvider == null) {
            return 0;
        }

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;
        int refreshed = 0;
        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                Chunk chunk = chunkProvider.getLoadedChunk(chunkX, chunkZ);
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                try {
                    if (world.provider.hasSkyLight()) {
                        chunk.generateSkylightMap();
                    }
                    chunk.resetRelightChecks();
                    chunk.enqueueRelightChecks();
                    chunk.checkLight();
                    refreshed++;
                } catch (RuntimeException ignored) {
                }
            }
        }
        return refreshed;
    }

    private int forceBlockLightingRefresh(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int checks = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    mutablePos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mutablePos, false)) {
                        continue;
                    }
                    IBlockState state;
                    int sourceLight;
                    int storedBlockLight;
                    try {
                        state = world.getBlockState(mutablePos);
                        sourceLight = state.getLightValue(world, mutablePos);
                        storedBlockLight = world.getLightFor(EnumSkyBlock.BLOCK, mutablePos);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    if (sourceLight <= 0 && storedBlockLight <= 0) {
                        continue;
                    }
                    try {
                        world.checkLightFor(EnumSkyBlock.BLOCK, mutablePos);
                        if (world.provider.hasSkyLight()) {
                            world.checkLightFor(EnumSkyBlock.SKY, mutablePos);
                        }
                        checks++;
                    } catch (RuntimeException ignored) {
                    }
                    if (sourceLight > 0) {
                        refreshSyntheticLightCandidate(world, mutablePos.toImmutable());
                    }
                }
            }
        }
        return checks;
    }

    private void resetPipelineState() {
        resetPipelineState(null);
    }

    private void resetPipelineState(Framebuffer preferredTarget) {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        shaderlessWorldPassActive = false;
        deferredPassesRenderedThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        renderingDeferredIngameHud = false;
        renderingGui = false;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        guiRenderDepth = 0;
        bloomLayerRenderedThisWorldPass = false;
        pendingDeferredNativeBloom = false;
        bloomRenderer.clearPendingLayerBloom();
        passStack.clear();
        worldPassBypassStack.clear();
        untouchedBetterPortalsVanillaRendererStack.clear();
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        restoreTerrainCulling();
        OpenGlHelper.glUseProgram(0);
        resetShaderResourceBindings();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        resetPortalMaskState();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        for (int i = 0; i < maxDrawBuffers(); i++) {
            setIndexedBlend(i, false);
        }
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);

        Minecraft mc = Minecraft.getMinecraft();
        Framebuffer target = preferredTarget != null ? preferredTarget : mc != null ? mc.getFramebuffer() : null;
        if (target != null) {
            target.bindFramebuffer(false);
            GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.viewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        } else {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glReadBuffer(GL11.GL_BACK);
        }
        externalWorldFramebufferTarget = null;
        restoreVanillaTextureBindingsAfterPipeline();
        refreshVanillaLightmap(mc);
        disableVanillaLightmap(mc);
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void resetShaderResourceBindings() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        TextureBinder.unbindAllTextureTargets();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        disablePipelineVertexAttributes();
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void setIndexedBlend(int drawBufferIndex, boolean enabled) {
        if (!GLContext.getCapabilities().OpenGL30 || drawBufferIndex < 0 || drawBufferIndex >= maxDrawBuffers()) {
            return;
        }
        if (enabled) {
            GL30.glEnablei(GL11.GL_BLEND, drawBufferIndex);
        } else {
            GL30.glDisablei(GL11.GL_BLEND, drawBufferIndex);
        }
    }

    private static void resetIndexedBlendState() {
        for (int i = 0; i < maxDrawBuffers(); i++) {
            setIndexedBlend(i, false);
        }
    }

    private static void resetOitRenderState() {
        GlStateManager.depthMask(true);
        resetIndexedBlendState();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
    }

    private static int maxDrawBuffers() {
        if (maxDrawBuffers < 0) {
            maxDrawBuffers = GLContext.getCapabilities().OpenGL20
                    ? Math.max(1, GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS))
                    : 1;
        }
        return maxDrawBuffers;
    }

    private void restoreVanillaTextureBindingsAfterPipeline() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            TextureBinder.restoreDefaultTextureUnit();
            GlStateManager.bindTexture(0);
            return;
        }

        restoreVanillaLightmapTexture(mc);

        TextureBinder.restoreDefaultTextureUnit();
        if (mc.getTextureManager() != null) {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            ITextureObject atlasTexture = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            if (atlasTexture != null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlasTexture.getGlTextureId());
            }
        } else {
            GlStateManager.bindTexture(0);
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void restoreVanillaLightmapTexture(Minecraft mc) {
        if (mc == null || mc.entityRenderer == null) {
            return;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        DynamicTexture lightmapTexture = ((EntityRendererAccessor) mc.entityRenderer).ausm$getLightmapTexture();
        try {
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            boolean lightmapTextureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            int textureId = lightmapTexture != null ? lightmapTexture.getGlTextureId() : 0;
            GlStateManager.bindTexture(textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            if (lightmapTextureEnabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
        } finally {
            GL13.glActiveTexture(previousActiveTexture);
            TextureBinder.restoreDefaultTextureUnit();
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }

    private void refreshVanillaLightmap(Minecraft mc) {
        if (mc == null || mc.world == null || mc.player == null || mc.entityRenderer == null) {
            return;
        }
        EntityRendererAccessor accessor = (EntityRendererAccessor) mc.entityRenderer;
        accessor.ausm$setLightmapUpdateNeeded(true);
        accessor.ausm$updateLightmap(mc.getRenderPartialTicks());
        restoreVanillaLightmapTexture(mc);
    }

    private void disableVanillaLightmap(Minecraft mc) {
        if (mc == null || mc.entityRenderer == null) {
            return;
        }
        mc.entityRenderer.disableLightmap();
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
    }

    private static void unbindShaderImages() {
        if (!GLContext.getCapabilities().OpenGL42) {
            return;
        }

        int maxImageUnits = Math.max(0, GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS));
        for (int unit = 0; unit < maxImageUnits; unit++) {
            GL42.glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, GL11.GL_RGBA8);
        }
    }

    private static void unbindShaderStorageBuffers() {
        if (!GLContext.getCapabilities().OpenGL43) {
            return;
        }

        int maxBindings = Math.max(0, GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
        for (int index = 0; index < maxBindings; index++) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static void disablePipelineVertexAttributes() {
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    private void loadCustomTextures(ShaderPack pack, ShaderProperties properties) {
        Map<String, Integer> loadedByPath = new HashMap<>();
        Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures = new HashMap<>();
        Map<String, Integer> customUnitsBySampler = new HashMap<>();
        Set<String> failedTexturePaths = new HashSet<>();
        int[] nextCustomUnit = {com.l.ausm.impl.pipeline.shader.ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT};
        Minecraft mc = Minecraft.getMinecraft();

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
                            rawTexture.textureTarget(),
                            true
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

                int atlasTexture = minecraftBlockAtlasTexture(mc, binding.resourcePath());
                if (atlasTexture > 0) {
                    textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, atlasTexture, GL11.GL_TEXTURE_2D, false));
                    MainMod.LOGGER.debug(
                            "[ShaderTextures] Prepared Minecraft block atlas for sampler '{}' on unit {} in pass {} as texture {}",
                            binding.samplerName(),
                            textureUnit,
                            pass.getProgramName(),
                            atlasTexture
                    );
                    continue;
                }

                try {
                    int textureId = loadedByPath.computeIfAbsent(binding.resourcePath(), path -> {
                        try {
                            return ShaderTextureLoader.loadTexture(pack, path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, textureId, GL11.GL_TEXTURE_2D, true));
                    MainMod.LOGGER.debug(
                            "[ShaderTextures] Prepared {} for sampler '{}' on unit {} in pass {} as texture {}",
                            binding.resourcePath(),
                            binding.samplerName(),
                            textureUnit,
                            pass.getProgramName(),
                            textureId
                    );
                } catch (UncheckedIOException e) {
                    if (failedTexturePaths.add(binding.resourcePath())) {
                        MainMod.LOGGER.warn("[ShaderTextures] Failed to load {}", binding.resourcePath(), e.getCause());
                    }
                }
            }
            if (!textures.isEmpty()) {
                customTextures.put(pass, List.copyOf(textures));
            }
        }
    }

    private int minecraftBlockAtlasTexture(Minecraft mc, String resourcePath) {
        if (mc == null || mc.getTextureManager() == null || !isMinecraftBlockAtlasPath(resourcePath)) {
            return -1;
        }

        ITextureObject texture = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        if (texture == null) {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            texture = mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        }
        return texture != null ? texture.getGlTextureId() : -1;
    }

    private static boolean isMinecraftBlockAtlasPath(String resourcePath) {
        return "minecraft:textures/atlas/blocks.png".equals(resourcePath)
                || "shaders/minecraft:textures/atlas/blocks.png".equals(resourcePath);
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
                .filter(LoadedCustomTexture::deleteOnCleanup)
                .mapToInt(LoadedCustomTexture::textureId)
                .distinct()
                .forEach(GL11::glDeleteTextures);
        customTextures.clear();
    }

    private static final class LoadedCustomTexture {
        private final String samplerName;
        private final String replacementSamplerName;
        private final String resourcePath;
        private final int textureUnit;
        private final int textureId;
        private final int textureTarget;
        private final boolean deleteOnCleanup;

        private LoadedCustomTexture(String samplerName, String replacementSamplerName, String resourcePath, int textureUnit, int textureId, int textureTarget, boolean deleteOnCleanup) {
            this.samplerName = samplerName;
            this.replacementSamplerName = replacementSamplerName;
            this.resourcePath = resourcePath;
            this.textureUnit = textureUnit;
            this.textureId = textureId;
            this.textureTarget = textureTarget;
            this.deleteOnCleanup = deleteOnCleanup;
        }

        private String samplerName() {
            return samplerName;
        }

        private String replacementSamplerName() {
            return replacementSamplerName;
        }

        private String resourcePath() {
            return resourcePath;
        }

        private int textureUnit() {
            return textureUnit;
        }

        private int textureId() {
            return textureId;
        }

        private int textureTarget() {
            return textureTarget;
        }

        private boolean deleteOnCleanup() {
            return deleteOnCleanup;
        }
    }

    private static final class ShaderChunkRefresh {
        private final WorldClient world;
        private final int chunkX;
        private final int chunkZ;

        private ShaderChunkRefresh(WorldClient world, int chunkX, int chunkZ) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ShaderChunkRefresh refresh)) {
                return false;
            }
            return world == refresh.world && chunkX == refresh.chunkX && chunkZ == refresh.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(world);
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }

    private static final class ClientChunkRenderRefresh {
        private final WorldClient world;
        private final int chunkX;
        private final int chunkZ;
        private final String reason;
        private int attemptsRemaining;
        private int delayFrames;

        private ClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason,
                                         int attemptsRemaining, int delayFrames) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.reason = reason;
            this.attemptsRemaining = attemptsRemaining;
            this.delayFrames = delayFrames;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ClientChunkRenderRefresh refresh)) {
                return false;
            }
            return world == refresh.world && chunkX == refresh.chunkX && chunkZ == refresh.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(world);
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            return result;
        }
    }

    private static final class ChunkFadeKey {
        private final int dimensionId;
        private final int chunkX;
        private final int chunkY;
        private final int chunkZ;

        private ChunkFadeKey(int dimensionId, int chunkX, int chunkY, int chunkZ) {
            this.dimensionId = dimensionId;
            this.chunkX = chunkX;
            this.chunkY = chunkY;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ChunkFadeKey key)) {
                return false;
            }
            return dimensionId == key.dimensionId
                    && chunkX == key.chunkX
                    && chunkY == key.chunkY
                    && chunkZ == key.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = dimensionId;
            result = 31 * result + chunkX;
            result = 31 * result + chunkY;
            result = 31 * result + chunkZ;
            return result;
        }
    }

    private static final class ChunkFadeState {
        private float value;
        private long lastFrameSeen;

        private ChunkFadeState(float value, long lastFrameSeen) {
            this.value = value;
            this.lastFrameSeen = lastFrameSeen;
        }
    }

    private static final class SyntheticLightInfo {
        private final IBlockState originalState;
        private final IBlockState actualState;
        private final int shaderBlockId;
        private final int voxelId;
        private final int emission;
        private final String reason;

        private SyntheticLightInfo(IBlockState originalState, IBlockState actualState, int shaderBlockId, int voxelId, int emission, String reason) {
            this.originalState = originalState;
            this.actualState = actualState;
            this.shaderBlockId = shaderBlockId;
            this.voxelId = voxelId;
            this.emission = emission;
            this.reason = reason;
        }
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
                new ShaderItemIdMap.ItemIdRules(Map.of(), Map.of()),
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
                ShaderOitSettings.empty(),
                Map.of(),
                Map.of()
        );
    }
}
