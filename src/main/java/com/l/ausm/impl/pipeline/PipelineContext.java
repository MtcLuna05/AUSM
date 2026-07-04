package com.l.ausm.impl.pipeline;

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
import com.l.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.BloomMaskColor;
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
import com.l.ausm.impl.pipeline.resource.ShaderImageSet;
import com.l.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.impl.pipeline.render.FullscreenQuad;
import com.l.ausm.impl.pipeline.render.IrisLightmapTexture;
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
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityLightningBolt;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
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
import java.util.Locale;
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
    private static final String NOTHIRIUM_MOD_ID = "nothirium";
    private static final String NAUGHTHIRIUM_MOD_ID = "naughthirium";

    private static final PipelineContext INSTANCE = new PipelineContext();
    private static final ICamera ALWAYS_VISIBLE_CAMERA = new ICamera() {
        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB box) {
            return true;
        }

        @Override
        public void setPosition(double x, double y, double z) {
        }
    };
    private static final FloatBuffer IRIS_LIGHTMAP_TEXTURE_MATRIX = createIrisLightmapTextureMatrix();
    private static final Pattern CONST_SETTING_PATTERN = Pattern.compile("^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+).*$");
    private static final Pattern DEFINE_SETTING_PATTERN = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+([^/\\s]+))?.*$");
    private static final boolean ENABLE_CPU_LIGHT_INJECTION = true;
    private static final boolean ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION = false;
    private static final int MAX_SYNTHETIC_LIGHT_CANDIDATES = 2048;
    private static final int MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME = 4096;
    private static final int MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME = 128;
    private static final int MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME = 128;
    private static final int MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME = 384;
    private static final int MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH = 48;
    private static final int MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT = 32;
    private static final int CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES = 20;
    private static final int MAX_COLORED_LIGHT_AUDIT_LOGS = 0;
    private static final int BIOME_NETHER_WASTES_ID = 100_000;
    private static final int BIOME_CRIMSON_FOREST_ID = 100_001;
    private static final int BIOME_WARPED_FOREST_ID = 100_002;
    private static final int BIOME_BASALT_DELTAS_ID = 100_003;
    private static final int BIOME_SOUL_SAND_VALLEY_ID = 100_004;
    private static final int BIOME_PALE_GARDEN_ID = 100_005;
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
    private static final int FORCE_LIGHT_RECALC_MIN_RADIUS = 16;
    private static final int FORCE_LIGHT_RECALC_MAX_RADIUS = 32;
    private static final int WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS = 2;
    private static final int WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES = 8;
    private static final int WORLD_LOAD_LIGHT_REFRESH_RADIUS = 16;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS = 4;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES = 4;
    private static final int WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES = 6;
    private static final double CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ = 64.0 * 64.0;
    private static final int PARTICLE_DIMENSION_RECOVERY_FRAMES = 80;
    private static final int MAX_PENDING_SHADER_CHUNK_REFRESHES = 2048;
    private static final int MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES = 1024;
    private static final int MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME = 8;
    private static final int MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME = 32;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES = 12;
    private static final int MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME = 32;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS = 8;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES = 1;
    private static final int CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES = 1;
    private static final int BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP = 4;
    private static final int MAX_CHUNK_FADE_STATES = 8192;
    private static final int CHUNK_FADE_STALE_FRAMES = 600;
    private static final int CHUNK_FADE_WARMUP_FRAMES = 20;
    private static final float CHUNK_FADE_DURATION_SECONDS = 0.45f;
    private static final int MAX_SHADER_CHUNK_REFRESHES_PER_FRAME = 8;
    private static final int COMPILED_PIPELINE_CACHE_LIMIT = 4;
    private static final int MAX_BETTER_PORTALS_PIPELINE_LOGS = 0;
    private static final int MAX_SHADERLESS_BLOOM_HOOK_LOGS = 128;
    private static final int MAX_VISIBLE_BLOOM_DIAG_LOGS = 128;
    private static final int MAX_WORLD_LAYER_DIAG_LOGS = 32;
    private static final int MAX_EXTERNAL_OVERLAY_LOGS = 0;
    private static final int MAX_TEMPORAL_HISTORY_RESET_LOGS = 8;
    private static final int MAX_TERRAIN_HISTORY_CLEAR_LOGS = 8;
    private static final int MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS = 0;
    private static final int MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS = 320;
    private static final int MAX_TERRAIN_COLOR_PROBE_LOGS = 10;
    private static final int MAX_FINAL_COLOR_PROBE_LOGS = 10;
    private static final int MAX_DH_PASS_COLOR_PROBE_LOGS = 96;
    private static final String DISTANT_HORIZONS_FALLBACK_VERTEX_SHADER = """
            #version 150 core

            in uvec4 vPosition;
            in vec4 color;
            in uvec4 dhMaterialData;

            uniform mat4 uCombinedMatrix;
            uniform vec3 uModelOffset;
            uniform float uWorldYOffset;
            uniform float uMircoOffset;
            uniform float uEarthRadius;

            out vec4 vertexColor;
            out vec3 vertexWorldPos;

            void main() {
                uint meta = vPosition.a;
                uint mirco = (meta & 0xFF00u) >> 8u;
                float mx = (mirco & 1u) != 0u ? uMircoOffset : 0.0;
                mx = (mirco & 2u) != 0u ? -mx : mx;
                float mz = (mirco & 16u) != 0u ? uMircoOffset : 0.0;
                mz = (mirco & 32u) != 0u ? -mz : mz;
                uint lights = meta & 0xFFu;
                float skyLight = (float(lights / 16u) + 0.5) / 16.0;
                float blockLight = (float(lights & 15u) + 0.5) / 16.0;
                float light = clamp(max(blockLight, skyLight * 0.75) * 0.9 + 0.1, 0.0, 1.0);
                vec3 worldPos = vec3(vPosition.xyz) + uModelOffset;
                worldPos.x += mx;
                worldPos.z += mz;
                float vertexYPos = float(vPosition.y) + uWorldYOffset;
                if (uEarthRadius < -1.0 || uEarthRadius > 1.0) {
                    float localRadius = uEarthRadius + vertexYPos;
                    float phi = length(worldPos.xz) / localRadius;
                    worldPos.y += (cos(phi) - 1.0) * localRadius;
                    worldPos.xz = worldPos.xz * sin(phi) / phi;
                }
                vertexWorldPos = worldPos;
                vertexColor = vec4(color.rgb * light, color.a);
                gl_Position = uCombinedMatrix * vec4(worldPos, 1.0);
            }
            """;
    private static final String DISTANT_HORIZONS_FALLBACK_FRAGMENT_SHADER = """
            #version 150 core

            in vec4 vertexColor;
            in vec3 vertexWorldPos;
            out vec4 fragColor;

            void main() {
                fragColor = vertexColor;
            }
            """;
    private static final String DISTANT_HORIZONS_COMPOSITE_VERTEX_SHADER = """
            #version 120
            varying vec2 textureCoords;
            void main() {
                textureCoords = gl_MultiTexCoord0.st;
                gl_Position = vec4(textureCoords * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String DISTANT_HORIZONS_COMPOSITE_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D dhColor;
            uniform sampler2D dhDepth;
            varying vec2 textureCoords;
            void main() {
                vec4 color = texture2D(dhColor, textureCoords);
                if (color.a <= 0.001 || max(max(color.r, color.g), color.b) <= 0.001) {
                    discard;
                }
                float depth = texture2D(dhDepth, textureCoords).r;
                gl_FragDepth = depth < 0.999999 ? depth : 0.999998;
                gl_FragColor = vec4(color.rgb, 1.0);
            }
            """;
    private static final int MAX_TERRAIN_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_CAMERA_FRUSTUM_SYNC_LOGS = 0;
    private static final int MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS = 0;
    private static final int MAX_DECORATED_LIGHT_AUDIT_LOGS = 0;
    private static final boolean DEBUG_PROBES_ENABLED = Boolean.getBoolean("ausm.debugProbes");
    private static final int MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS = DEBUG_PROBES_ENABLED ? 96 : 0;
    private static final int MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS = 0;
    private static final int MAX_CURRENT_PROBLEM_PROBE_LOGS = DEBUG_PROBES_ENABLED ? 128 : 0;
    private static final int MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS = DEBUG_PROBES_ENABLED ? 256 : 0;
    private static final int MAX_INACTIVE_SKY_PIPELINE_PROBE_LOGS = 0;
    private static final int MAX_ACTIVE_SKY_PIPELINE_PROBE_LOGS = 0;
    private static final int MAX_HAND_ITEM_DRAW_STATE_LOGS = 0;
    private static final int MAX_HAND_GBUFFER_PROBE_LOGS = 0;
    private static final int MAX_HAND_PASS_BIND_LOGS = 2;
    private static final int SHADERLESS_BLOOM_GEOMETRY_EMISSION = 15;
    private static final int SHADERLESS_LIGHT_EMITTING_BLOOM_GEOMETRY_EMISSION = 5;
    private static final int MAX_SHADERLESS_LIGHT_STATE_PROBE_LOGS = 96;
    private static final int MAX_SHADERLESS_SKY_GUI_WORLD_PROBE_LOGS = 48;
    private static final int MAX_SHADERLESS_SKY_GUI_SCREEN_PROBE_LOGS = 1024;
    private static final int MAX_ASTRAL_VOID_SKY_PROBE_LOGS = 768;
    private static final int MAX_SHADERLESS_ASTRAL_SKY_COLOR_LOGS = 96;
    private static final int MAX_FRESH_SKY_PROBE_LOGS = 512;
    private static final int MAX_FRESH_SKY_GUI_PROBE_LOGS = 1024;
    private static final int MAX_NOTHIRIUM_FOG_PROBE_LOGS = 96;
    private static final int MAX_NOTHIRIUM_RENDER_PROBE_LOGS = 96;
    private static final int MAX_NOTHIRIUM_FOG_GUARD_LOGS = 96;
    private static final int MAX_SHADERLESS_VOID_LIGHT_REPAIR_LOGS = 128;
    private static final int MAX_SHADERLESS_VOID_SKY_PIXEL_PROBE_LOGS = 96;
    private static final int MAX_SHADERLESS_VOID_SKY_REPAIR_LOGS = 192;
    private static final int MAX_SHADERLESS_VOID_VANILLA_LOWER_SKY_LOGS = 192;
    private static final int MAX_SHADERLESS_WORLD_FRAMEBUFFER_HANDOFF_LOGS = 192;
    private static final boolean FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM = false;
    private static final boolean ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER = false;
    private static final boolean ENABLE_DIRECT_DISTANT_HORIZONS_SHADER_MRT = Boolean.getBoolean("ausm.dhDirectShaderMrt");
    private static final boolean FRAMED_BLOCK_DIAGNOSTICS_ENABLED =
            MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS > 0
                    || MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS > 0
                    || MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS > 0;
    private static final boolean CURRENT_PROBLEM_PROBES_ENABLED = MAX_CURRENT_PROBLEM_PROBE_LOGS > 0;
    private static final int MAX_HARDWARE_CAPABILITY_LOGS = 4;
    private static final int MAX_HARDWARE_TERRAIN_FALLBACK_LOGS = 12;
    private static final int HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES = 5;
    private static final int HARDWARE_TERRAIN_FALLBACK_REFRESH_COOLDOWN_FRAMES = 12;
    private static final boolean ENABLE_CHUNK_FADE = false;
    private static final boolean ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK = false;
    private static final long WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS = 750L;
    private static final long BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS = 1000L;
    private static final String RANDOM_THINGS_LUMINOUS_BLOCK_CLASS = "lumien.randomthings.lib.ILuminousBlock";
    private static final String BLOCKCRAFTERY_TILE_EDITABLE_BLOCK_CLASS = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String ARCHITECTURECRAFT_TILE_SHAPE_CLASS = "com.elytradev.architecture.common.tile.TileShape";
    private static final String ARCHITECTURECRAFT_BLOCK_PACKAGE = "com.elytradev.architecture.common.block.";
    private static final int RANDOM_THINGS_TRANSLUCENT_LUMINOUS_ALPHA = 160;
    private static final float TEMPORAL_HISTORY_CAMERA_DELTA_RESET = 0.85f;
    private static final float TEMPORAL_HISTORY_VERTICAL_CAMERA_DELTA_RESET = 4.0f;
    private static final float TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET = 35.0f;
    private static final float TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET = 25.0f;
    private static final ShaderBlendMode OIT_COEFFICIENT_BLEND = new ShaderBlendMode(true, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
    private static final ShaderBlendMode WATER_BLEND_MODE = new ShaderBlendMode(true, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    private static final float PORTAL_NETHER_FOG_DENSITY = 0.08f;
    private static final float[] PORTAL_NETHER_FOG_COLOR = {0.20f, 0.03f, 0.03f};
    private static final float NETHER_SHADER_FOG_COLOR_SCALE = 0.25f;
    private static final float SHADER_OVERWORLD_FOG_START_RATIO = 0.85f;
    private static int maxDrawBuffers = -1;
    private static int maxShaderStorageBufferBindings = -1;
    private static boolean shaderStorageBuffersKnownUnbound = true;
    private static boolean celeritasShadowCameraWarningLogged;

    private final PingPongManager pingPongManager = new PingPongManager();
    private final IrisLightmapTexture irisLightmapTexture = new IrisLightmapTexture();
    private final Map<RenderPass, PipelineProgram> programs = new EnumMap<>(RenderPass.class);
    private final Map<RenderPass, List<LoadedCustomTexture>> customTextures = new EnumMap<>(RenderPass.class);
    private final Map<ShaderProgramArrayKey, List<LoadedCustomTexture>> customArrayTextures = new HashMap<>();
    private final UniformRegistry uniformRegistry = new UniformRegistry();
    private final Map<String, float[]> customUniformScalarScratch = new HashMap<>();
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
    private final Map<ViewFrustum, Long> vanillaViewFrustumChunkPositionKeys = new IdentityHashMap<>();
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
    private final Map<WorldClient, Map<Long, ClientChunkRenderRefresh>> pendingClientChunkRenderRefreshLookupByWorld = new IdentityHashMap<>();
    private final Map<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>> pendingClientChunkRenderRefreshesByWorld = new IdentityHashMap<>();
    private final Map<WorldClient, Map<Long, Long>> recentlyCompletedClientChunkRenderRefreshes = new IdentityHashMap<>();
    private final ConcurrentMap<String, Boolean> bloomResourceGeometryStateCache = new ConcurrentHashMap<>();
    private final Set<String> bloomResourceGeometryScansInProgress = ConcurrentHashMap.newKeySet();
    private int pendingWorldLoadLightRecalculationAttempts = 0;
    private int pendingWorldLoadLightRecalculationDelay = 0;
    private int pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
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
    private boolean celeritasShadowCameraResolved;
    private Class<?> celeritasViewportProviderClass;
    private Constructor<?> celeritasViewportConstructor;
    private Constructor<?> celeritasVectorConstructor;
    private Object celeritasAlwaysVisibleFrustum;

    private final Deque<PassScope> passStack = new ArrayDeque<>();
    private final Deque<Integer> renderedItemIdStack = new ArrayDeque<>();
    private final Deque<Boolean> worldPassBypassStack = new ArrayDeque<>();
    private final Deque<Long> worldPassSerialStack = new ArrayDeque<>();
    private final Deque<Long> nothiriumPipelineTranslucentFrameStack = new ArrayDeque<>();
    private final Deque<Long> nothiriumPipelineTranslucentWorldPassSerialStack = new ArrayDeque<>();
    private final Deque<Boolean> untouchedBetterPortalsVanillaRendererStack = new ArrayDeque<>();
    private RenderPass activePass = null;
    private ShaderKey activeShaderKey = null;
    private WorldRenderingPhase activePhase = WorldRenderingPhase.NONE;
    private boolean activeProgramTessellated = false;
    private boolean activeProgramGeometric = false;
    private WorldRenderingPhase overridePhase = null;
    private volatile boolean isPipelineActive = false;
    private boolean shaderlessWorldPassActive = false;
    private int vanillaParticleRecoveryFrames = 0;
    private String activePackName = "(internal)";
    private float centerDepth = 1.0f;
    private float centerDepthSmooth = 1.0f;
    private int centerDepthSmoothTexture = -1;
    private int noiseTexture = -1;
    private final FloatBuffer centerDepthTextureBuffer = org.lwjgl.BufferUtils.createFloatBuffer(1);
    private final FloatBuffer fogColorBuffer = org.lwjgl.BufferUtils.createFloatBuffer(4);
    private final FloatBuffer dhProjectionBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final FloatBuffer dhProjectionInverseBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final FloatBuffer dhModelViewBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final FloatBuffer dhModelViewProjectionBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private final float[] dhMatrixScratch = new float[16];
    private final float[] dhModelOffset = new float[]{0.0f, 0.0f, 0.0f};
    private RenderPass currentDistantHorizonsPass = RenderPass.DH_TERRAIN;
    private ShaderProgram currentDistantHorizonsProgram = null;
    private boolean currentDistantHorizonsFallbackProgram = false;
    private boolean renderingDistantHorizonsPresentation = false;
    private Framebuffer distantHorizonsPresentationTarget = null;
    private float latestDistantHorizonsPartialTicks = 0.0F;
    private int distantHorizonsVertexArray = -1;
    private int distantHorizonsFallbackProgramId = 0;
    private int distantHorizonsFallbackCombinedMatrixUniform = -1;
    private int distantHorizonsFallbackProjectionMatrixUniform = -1;
    private int distantHorizonsFallbackModelViewMatrixUniform = -1;
    private int distantHorizonsFallbackModelOffsetUniform = -1;
    private int distantHorizonsFallbackWorldYOffsetUniform = -1;
    private int distantHorizonsFallbackMircoOffsetUniform = -1;
    private int distantHorizonsFallbackEarthRadiusUniform = -1;
    private boolean distantHorizonsFallbackProgramFailed = false;
    private int distantHorizonsFramebufferId = 0;
    private int distantHorizonsColorTextureId = 0;
    private int distantHorizonsDepthTextureId = 0;
    private boolean distantHorizonsTexturesOwned = false;
    private int distantHorizonsFramebufferWidth = 0;
    private int distantHorizonsFramebufferHeight = 0;
    private long distantHorizonsFramebufferClearFrame = Long.MIN_VALUE;
    private int distantHorizonsTextureReadbackFramebufferId = 0;
    private int distantHorizonsCompositeProgramId = 0;
    private int distantHorizonsCompositeTextureUniform = -1;
    private int distantHorizonsCompositeDepthUniform = -1;
    private boolean distantHorizonsCompositeProgramFailed = false;
    private boolean distantHorizonsFramebufferPendingComposite = false;
    private int distantHorizonsDiagnosticLogs = 0;
    private int terrainColorProbeLogs = 0;
    private int finalColorProbeLogs = 0;
    private int distantHorizonsColorProbeLogs = 0;
    private int distantHorizonsPassColorProbeLogs = 0;
    private final java.nio.ByteBuffer distantHorizonsReadbackPixel = org.lwjgl.BufferUtils.createByteBuffer(4);
    private int currentEntityId = 0;
    private int currentRenderedItemId = -1;
    private String currentRenderedItemDebugName = "";
    private ResourceLocation currentEntityKey = null;
    private float[] currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] currentAstralConstellationColor = new float[]{1.0f, 1.0f, 1.0f};
    private final float[] currentAstralTierColor = new float[]{1.0f, 1.0f, 1.0f};
    private float currentAstralSolarEclipseFactor;
    private float currentAlphaTestReference = 0.1f;
    private float shadowMapDistance = 128.0f;
    private float voxelDistance = 0.0f;
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
    private final float[] endFlashPosition = {0.0f, 0.0f, 0.0f};
    private float endFlashIntensity = 0.0f;
    private float previousEndFlashIntensity = 0.0f;
    private float endFlashYawDegrees = 0.0f;
    private float endFlashPitchDegrees = 0.0f;
    private boolean shadowPolygonOffset = true;
    private float shadowPolygonOffsetFactor = 1.1f;
    private float shadowPolygonOffsetUnits = 4.0f;
    private int shadowFrameCount = 1_000_000;
    private long lastShadowFrameId = -1L;
    private int lastShadowRenderDimensionId = Integer.MIN_VALUE;
    private long lastShadowRenderWorldTime = Long.MIN_VALUE;
    private double lastShadowRenderX = Double.NaN;
    private double lastShadowRenderY = Double.NaN;
    private double lastShadowRenderZ = Double.NaN;
    private long pipelineFrameId = 0L;
    private World cpuLightTileEntitySnapshotWorld = null;
    private long cpuLightTileEntitySnapshotFrame = Long.MIN_VALUE;
    private List<TileEntity> cpuLightTileEntitySnapshot = java.util.Collections.emptyList();
    private int cpuLightTileEntityScanCursor = 0;
    private World cpuLightBlockScanWorld = null;
    private int cpuLightBlockScanCursor = 0;
    private final long pipelineStartNanos = System.nanoTime();
    private long lastPipelineFrameNanos = pipelineStartNanos;
    private float currentFrameTime = 0.016f;
    private int textureReloadCount = 0;
    private float currentChunkFade = 1.0f;
    private long chunkFadeWarmupUntilFrame = 0L;
    private final Map<ChunkFadeKey, ChunkFadeState> chunkFadeStates = new LinkedHashMap<>();
    private boolean terrainRebuiltDuringLastInitialization = false;
    private boolean terrainCacheReusableDuringLastInitialization = false;
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
    private int hardwareCapabilityLogs = 0;
    private int hardwareTerrainFallbackLogs = 0;
    private int zeroOpaqueTerrainFrames = 0;
    private boolean hardwareSafeVanillaTerrain = false;
    private String hardwareSafeVanillaTerrainReason = "";
    private boolean zeroOpaqueTerrainRecoveryRequested = false;
    private int hardwareSafeVanillaTerrainRefreshCooldown = 0;
    private World lastHardwareSafeVanillaTerrainRefreshWorld = null;
    private int lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
    private int lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
    private boolean lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
    private boolean pipelineTerrainFormatSupported = false;
    private boolean deferredPassesRenderedThisFrame = false;
    private boolean preparePassesRenderedBeforeShadowThisFrame = false;
    private boolean preTranslucentDepthCopiedThisFrame = false;
    private boolean preHandDepthCopiedThisFrame = false;
    private boolean setupComputePending = false;
    private boolean terrainCullOverrideActive = false;
    private boolean previousTerrainCullEnabled = true;
    private boolean terrainOcclusionOverrideActive = false;
    private boolean previousRenderChunksManyForOcclusion = true;
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
    private int activeVanillaViewFrustumRenderDistanceChunks = -1;
    private boolean betterPortalsViewFrustumUpdateWarningLogged = false;
    private int cameraFrustumSyncLogs = 0;
    private int clientChunkRenderRefreshLogs = 0;
    private World lastCameraFrustumSyncWorld = null;
    private ViewFrustum lastCameraFrustumSyncViewFrustum = null;
    private int lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
    private int lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
    private int lastStableMainWorldVanillaRenderDistanceChunks = -1;
    private int lastObservedRenderDistanceChunks = -1;
    private World lastTerrainTransitionWorld = null;
    private int lastTerrainTransitionDimension = Integer.MIN_VALUE;
    private long lastTerrainTransitionMillis = 0L;
    private boolean betterPortalsChunkUpdateWarningLogged = false;
    private boolean shadowHealthLogged = false;
    private int shadowHealthLogAttempts = 0;
    private int guiRenderDepth = 0;
    private int guiEntityPreviewStateDepth = 0;
    private int handItemDrawStateLogs = 0;
    private int handGbufferProbeLogs = 0;
    private int handPassBindLogs = 0;
    private int shaderlessLightStateProbeLogs = 0;
    private int shaderlessSkyGuiWorldProbeLogs = 0;
    private int shaderlessSkyGuiScreenProbeLogs = 0;
    private int astralVoidSkyProbeLogs = 0;
    private int shaderlessAstralSkyColorLogs = 0;
    private int freshSkyProbeLogs = 0;
    private int freshSkyGuiProbeLogs = 0;
    private int nothiriumFogProbeLogs = 0;
    private int nothiriumRenderProbeLogs = 0;
    private int nothiriumFogGuardLogs = 0;
    private int shaderlessNothiriumTerrainFogGuardDepth = 0;
    private boolean shaderlessNothiriumTerrainFogPreviouslyEnabled = false;
    private int shaderlessVoidLightRepairLogs = 0;
    private int shaderlessVoidSkyPixelProbeLogs = 0;
    private int shaderlessVoidSkyRepairLogs = 0;
    private int shaderlessVoidVanillaLowerSkyLogs = 0;
    private int shaderlessWorldFramebufferHandoffLogs = 0;
    private int shaderlessWorldFramebufferForUi = 0;
    private int shaderlessWorldFramebufferWidth = 0;
    private int shaderlessWorldFramebufferHeight = 0;
    private long shaderlessWorldFramebufferFrame = Long.MIN_VALUE;
    private Vec3d lastShaderlessAstralVoidSkyColor = new Vec3d(0.718D, 0.824D, 1.0D);
    private boolean shaderlessTerrainLightmapCoordsSaved = false;
    private float shaderlessTerrainPreviousLightmapX = 0.0F;
    private float shaderlessTerrainPreviousLightmapY = 0.0F;
    private int inactiveSkyPipelineProbeLogs = 0;
    private int activeSkyPipelineProbeLogs = 0;
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
    private final Set<Long> shaderlessBloomMetadataKnownChunkLayers = ConcurrentHashMap.newKeySet();
    private final Set<Long> shaderlessBloomMetadataChunkLayers = ConcurrentHashMap.newKeySet();
    private final AtomicInteger currentProblemProbeCount = new AtomicInteger();
    private final AtomicInteger activeLightOrIdProbeCount = new AtomicInteger();
    private final AtomicInteger waterLikeMaterialProbeCount = new AtomicInteger();
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
    private boolean shaderlessBloomExtractionActive = false;
    private boolean shaderlessBloomExtractionBootstrapActive = false;
    private int shaderlessTerrainSolidCount = -1;
    private int shaderlessTerrainCutoutMippedCount = -1;
    private int shaderlessTerrainCutoutCount = -1;
    private int shaderlessTerrainTranslucentCount = -1;
    private int shaderlessTerrainBloomCount = -1;
    private final IntBuffer viewportBuffer = org.lwjgl.BufferUtils.createIntBuffer(16);

    private PipelineContext() {
        registerBaseUniforms();
    }

    private static final class PassScope {
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
        uniformRegistry.registerFloat("thunderStrength", () -> renderWorld(mc) != null ? renderWorld(mc).getThunderStrength(mc.getRenderPartialTicks()) : 0.0f);
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
        uniformRegistry.registerInt("is_sneaking", () -> mc.player != null && mc.player.isSneaking() ? 1 : 0);
        uniformRegistry.registerInt("is_sprinting", () -> mc.player != null && mc.player.isSprinting() ? 1 : 0);
        uniformRegistry.registerInt("is_hurt", () -> mc.player != null && mc.player.hurtTime > 0 ? 1 : 0);
        uniformRegistry.registerInt("is_invisible", () -> mc.player != null && mc.player.isInvisible() ? 1 : 0);
        uniformRegistry.registerInt("is_burning", () -> mc.player != null && mc.player.isBurning() ? 1 : 0);
        uniformRegistry.registerInt("is_on_ground", () -> mc.player != null && mc.player.onGround ? 1 : 0);
        uniformRegistry.registerInt("isRiding", () -> mc.player != null && mc.player.isRiding() ? 1 : 0);
        uniformRegistry.registerInt("isElytraFlying", () -> mc.player != null && mc.player.isElytraFlying() ? 1 : 0);
        uniformRegistry.registerInt("feetInWater", () -> mc.player != null && mc.player.isInWater() ? 1 : 0);
        uniformRegistry.registerInt("inSwimmingAnimation", () -> 0);
        uniformRegistry.registerInt("vehicleInWater", () -> vehicleInWater(mc) ? 1 : 0);
        uniformRegistry.registerInt("vehicleId", () -> vehicleId(mc));
        uniformRegistry.registerFloat("sneakSmooth", () -> mc.player != null && mc.player.isSneaking() ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("burningSmooth", () -> mc.player != null && mc.player.isBurning() ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("touchmybody", () -> mc.player != null && mc.player.hurtTime > 0 ? 1.0f : 0.0f);
        uniformRegistry.registerFloat("effectStrength", () -> 0.0f);
        uniformRegistry.registerFloat("playerMood", () -> 0.0f);
        uniformRegistry.registerFloat("constantMood", () -> 0.0f);
        uniformRegistry.registerFloat("starter", () -> 1.0f);
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
        uniformRegistry.registerInt("bedrockLevel", () -> 0);
        uniformRegistry.registerInt("heightLimit", () -> renderWorld(mc) != null ? renderWorld(mc).getHeight() : 256);
        uniformRegistry.registerInt("logicalHeightLimit", () -> renderWorld(mc) != null ? renderWorld(mc).getActualHeight() : 256);
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
        uniformRegistry.registerVec4("ausmVoidSkyParams", () -> new float[]{1.0f, 1.0f, 1.0f, 1.0f});
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
        uniformRegistry.registerInt("heldItemId2", () -> heldItemId(mc.player != null ? mc.player.getHeldItemOffhand() : ItemStack.EMPTY));
        uniformRegistry.registerInt("heldBlockLightValue", () -> heldBlockLightValue(heldMainStack(mc)));
        uniformRegistry.registerInt("heldBlockLightValue2", () -> heldBlockLightValue(mc.player != null ? mc.player.getHeldItemOffhand() : ItemStack.EMPTY));
        uniformRegistry.registerVec3("heldBlockLightColor", () -> heldBlockLightColor(heldMainStack(mc)));
        uniformRegistry.registerVec3("heldBlockLightColor2", () -> heldBlockLightColor(mc.player != null ? mc.player.getHeldItemOffhand() : ItemStack.EMPTY));
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
        uniformRegistry.registerMatrix4("dhProjection", () -> dhProjectionBuffer);
        uniformRegistry.registerMatrix4("dhProjectionInverse", () -> dhProjectionInverseBuffer);
        uniformRegistry.registerMatrix4("dhPreviousProjection", () -> dhProjectionBuffer);
        uniformRegistry.registerMatrix4("dhModelView", () -> dhModelViewBuffer);
        uniformRegistry.registerMatrix4("dhModelViewProjection", () -> dhModelViewProjectionBuffer);
        uniformRegistry.registerVec3("dhModelOffset", () -> dhModelOffset);
        uniformRegistry.registerInt("dhMaterialId", () -> 0);
        uniformRegistry.registerInt("dhRenderDistance", () -> mc != null && mc.gameSettings != null ? mc.gameSettings.renderDistanceChunks * 16 : 0);
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
        uniformRegistry.registerInt("textureReloadCount", () -> textureReloadCount);
        uniformRegistry.registerInt("textureFilteringMode", ShaderSamplerState::textureFilteringModeUniform);
        uniformRegistry.registerVec2i("atlasSize", PipelineContext::boundTextureSize);
        uniformRegistry.registerVec2i("gtextureSize", PipelineContext::boundTextureSize);
        uniformRegistry.registerVec4i("blendFunc", PipelineContext::blendFunc);
        uniformRegistry.registerVec2("iris_ScreenSize", () -> new float[]{(float) worldTargetWidth(mc), (float) worldTargetHeight(mc)});
        uniformRegistry.registerVec3("iris_CameraTranslation", () -> new float[]{0.0f, 0.0f, 0.0f});
        uniformRegistry.registerVec3("iris_ModelOffset", () -> dhModelOffset);
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
        uniformRegistry.registerVec3("playerBodyVector", () -> bodyVector(mc != null ? mc.getRenderViewEntity() : null));
        uniformRegistry.registerVec3("vehicleLookVector", () -> vehicleLookVector(mc));
        uniformRegistry.registerVec3("relativeVehiclePosition", () -> relativeVehiclePosition(mc));
        uniformRegistry.registerVec4("lightningBoltPosition", () -> lightningBoltPosition(mc));
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

    private static int[] currentDate() {
        LocalDateTime now = LocalDateTime.now();
        return new int[]{now.getYear(), now.getMonthValue(), now.getDayOfMonth()};
    }

    private static int[] currentTime() {
        LocalDateTime now = LocalDateTime.now();
        return new int[]{now.getHour(), now.getMinute(), now.getSecond()};
    }

    private static int[] currentYearTime() {
        LocalDateTime now = LocalDateTime.now();
        int elapsedSeconds = (now.getDayOfYear() - 1) * 86400
                + now.getHour() * 3600
                + now.getMinute() * 60
                + now.getSecond();
        int yearSeconds = now.toLocalDate().lengthOfYear() * 86400;
        return new int[]{elapsedSeconds, yearSeconds - elapsedSeconds};
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
        float[] fogColor = GL11.glIsEnabled(GL11.GL_FOG) ? currentGlFogColor() : null;
        return isProbablyUnsetFogColor(fogColor) ? overworldFogColor(mc) : fogColor;
    }

    private float[] overworldFogColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world != null) {
            return vec3(world.getFogColor(mc != null ? mc.getRenderPartialTicks() : 0.0f));
        }
        return skyColor(mc);
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

    private static float cloudHeight(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null || world.provider == null) {
            return 128.0f;
        }
        return world.provider.getCloudHeight();
    }

    private static boolean hasSkylight(Minecraft mc) {
        World world = renderWorld(mc);
        return world != null && world.provider != null && world.provider.hasSkyLight();
    }

    private static float cloudTime(Minecraft mc) {
        World world = renderWorld(mc);
        return world != null ? (float) (world.getTotalWorldTime() + (mc != null ? mc.getRenderPartialTicks() : 0.0f)) : 0.0f;
    }

    private boolean isEyeInCave(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null || eyeFluidState(mc) != 0) {
            return false;
        }
        BlockPos pos = currentCameraBlockPos();
        return world.getLightFor(EnumSkyBlock.SKY, pos) <= 1 && pos.getY() < world.getSeaLevel();
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

    private int currentBiomeCategory(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        return biome != null ? biome.getTempCategory().ordinal() : -1;
    }

    private float currentBiomeRainfall(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        return biome != null ? biome.getRainfall() : 0.0f;
    }

    private float currentBiomeTemperature(Minecraft mc) {
        Biome biome = currentCameraBiome(mc);
        return biome != null ? biome.getTemperature(currentCameraBlockPos()) : 0.0f;
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

    private float rainStrength(Minecraft mc) {
        World world = renderWorld(mc);
        return world != null ? world.getRainStrength(mc.getRenderPartialTicks()) : 0.0f;
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

    private static float[] currentSelectedBlockPos(Minecraft mc, double[] cameraPosition) {
        BlockPos pos = currentSelectedBlockPosition(mc);
        if (pos == null) {
            return new float[]{-256.0f, -256.0f, -256.0f};
        }
        return new float[]{
                (float) (pos.getX() + 0.5 - cameraPosition[0]),
                (float) (pos.getY() + 0.5 - cameraPosition[1]),
                (float) (pos.getZ() + 0.5 - cameraPosition[2])
        };
    }

    private static BlockPos currentSelectedBlockPosition(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos();
    }

    private static boolean playerSurvivalStatsVisible(Minecraft mc) {
        if (mc == null || mc.player == null || mc.playerController == null) {
            return false;
        }
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType != null && gameType.isSurvivalOrAdventure();
    }

    private float currentPlayerHealth(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        float maxHealth = Math.max(0.001f, mc.player.getMaxHealth());
        return clamp01(mc.player.getHealth() / maxHealth);
    }

    private float maxPlayerHealth(Minecraft mc) {
        return playerSurvivalStatsVisible(mc) ? mc.player.getMaxHealth() : -1.0f;
    }

    private float currentPlayerHunger(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return clamp01(mc.player.getFoodStats().getFoodLevel() / 20.0f);
    }

    private float currentPlayerAir(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return clamp01(mc.player.getAir() / 300.0f);
    }

    private float maxPlayerAir(Minecraft mc) {
        return playerSurvivalStatsVisible(mc) ? 300.0f : -1.0f;
    }

    private float currentPlayerArmor(Minecraft mc) {
        if (!playerSurvivalStatsVisible(mc)) {
            return -1.0f;
        }
        return clamp01(mc.player.getTotalArmorValue() / 50.0f);
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

        releaseMouseForShaderLoad(Minecraft.getMinecraft());
        boolean usingCachedPrograms = cachedPrograms != null;
        boolean restoredCachedPrograms = false;
        ShaderLoadingScreen.begin(pack.getName(), usingCachedPrograms ? 9 : 12, loadingBackgroundMode);
        try {
            Minecraft mc = Minecraft.getMinecraft();
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
            pingPongManager.initialize(mc.displayWidth, mc.displayHeight, packDirectives.renderTargets());
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
            shaderImages.resize(mc.displayWidth, mc.displayHeight);
            clearColoredLightImages();
            shaderStorageBuffers = ShaderStorageBufferSet.load(pack, packDirectives.storageBuffers());
            shaderStorageBuffers.resize(mc.displayWidth, mc.displayHeight);
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
                    applyFallbackDefaultDrawBuffers(pipelineProgram);
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
            activeSkyPipelineProbeLogs = 0;
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

    private void releaseMouseForShaderLoad(Minecraft mc) {
        if (mc != null && mc.inGameHasFocus) {
            mc.setIngameNotInFocus();
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
        applyFallbackDefaultDrawBuffers();
    }

    private void applyFallbackDefaultDrawBuffers() {
        for (PipelineProgram program : programs.values()) {
            applyFallbackDefaultDrawBuffers(program);
        }
        for (Map.Entry<ProgramArrayId, List<FullscreenArrayProgram>> entry : fullscreenArrayPrograms.entrySet()) {
            for (FullscreenArrayProgram program : entry.getValue()) {
                applyFallbackDefaultDrawBuffers(program);
            }
        }
    }

    private void applyFallbackDefaultDrawBuffers(PipelineProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(defaultDrawBuffers(program.stage()));
    }

    private void applyFallbackDefaultDrawBuffers(FullscreenArrayProgram program) {
        if (program == null || !program.directives().drawBuffers().isEmpty()) {
            return;
        }
        program.setDrawBuffers(program.arrayId() == ProgramArrayId.SHADOWCOMP
                ? List.of(Attachment.COLOR)
                : List.of(fallbackColorAttachment()));
    }

    private List<Attachment> defaultDrawBuffers(ProgramStage stage) {
        return switch (stage) {
            case PREPARE, GBUFFERS, DEFERRED, COMPOSITE -> List.of(fallbackColorAttachment());
            case SHADOW -> List.of(Attachment.COLOR);
            case FINAL, NONE -> List.of();
        };
    }

    private Attachment fallbackColorAttachment() {
        int index = shaderProperties != null ? shaderProperties.renderSettings().fallbackTex() : 0;
        Attachment attachment = Attachment.fromColorIndex(index);
        return attachment != null ? attachment : Attachment.COLOR;
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
        preparePassesRenderedBeforeShadowThisFrame = false;
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

    private static int parseIntSetting(ShaderPack pack, ShaderProperties properties, String name, int fallback) {
        return parseIntValue(settingValue(pack, properties, name), fallback);
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
            defines.putAll(ShaderEnvironmentDefines.defineMap(properties.options()));
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
            return ShaderExpressionEvaluator.evaluate(stripLineComment(expression), defines);
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

    private boolean hasExtraProgramArrayEntries() {
        return fullscreenProgramArrays.values().stream()
                .anyMatch(PipelineContext::hasUnsupportedFullscreenArrayEntries);
    }

    private static boolean hasUnsupportedFullscreenArrayEntries(FullscreenProgramArray array) {
        if (!array.hasExtraPrograms()) {
            return false;
        }
        return !supportsIndexedFullscreenArray(array.arrayId());
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
            List<ComputeProgram> compiled = compileComputeList(
                    pack,
                    properties,
                    arrayId,
                    packDirectives.computeDirectives().computeArrays().getOrDefault(arrayId, List.of())
            );
            if (!compiled.isEmpty()) {
                computeProgramArrays.put(arrayId, compiled);
            }
        }
        shadowComputePrograms = compileComputeList(pack, properties, null, packDirectives.computeDirectives().shadowComputes());
        finalComputePrograms = compileComputeList(pack, properties, null, packDirectives.computeDirectives().finalComputes());
    }

    private void compileFullscreenArrayPrograms(ShaderPack pack, ShaderProperties properties) {
        fullscreenArrayPrograms.clear();
        for (ProgramArrayId arrayId : ProgramArrayId.values()) {
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
        return switch (arrayId) {
            case SETUP, BEGIN, PREPARE, DEFERRED, COMPOSITE, SHADOWCOMP -> true;
        };
    }

    private static boolean shouldCompileIndexedFullscreenArraySource(ProgramArrayId arrayId, int index) {
        return switch (arrayId) {
            case SETUP, BEGIN -> true;
            case PREPARE -> index >= 1;
            case DEFERRED -> index >= RenderPass.DEFERRED_PASSES.length;
            case COMPOSITE -> index >= RenderPass.COMPOSITE_PASSES.length;
            case SHADOWCOMP -> true;
        };
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

    private static List<ComputeProgram> compileComputeList(
            ShaderPack pack,
            ShaderProperties properties,
            ProgramArrayId arrayId,
            List<ComputeProgramSource> sources
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

    private void resetHardwareCompatibilityState() {
        zeroOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        pipelineTerrainFormatSupported = detectPipelineTerrainFormatSupport();
        hardwareSafeVanillaTerrain = !pipelineTerrainFormatSupported;
        hardwareSafeVanillaTerrainReason = hardwareSafeVanillaTerrain ? "missing-pipeline-terrain-format" : "";
    }

    private boolean detectPipelineTerrainFormatSupport() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS) > ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE;
    }

    private boolean pipelineTerrainFormatSupported() {
        if (!pipelineTerrainFormatSupported) {
            pipelineTerrainFormatSupported = detectPipelineTerrainFormatSupport();
        }
        return pipelineTerrainFormatSupported;
    }

    private void logHardwareCapabilities(String stage, ShaderPackDirectives directives) {
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
                OpenGlHelper.isFramebufferEnabled(),
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

    private static int safeGetInteger(int parameter) {
        try {
            return GL11.glGetInteger(parameter);
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    private static String safeGetString(int parameter) {
        try {
            String value = GL11.glGetString(parameter);
            return value != null ? value : "unknown";
        } catch (RuntimeException | LinkageError ignored) {
            return "unavailable";
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
        if (state == null) {
            return -1;
        }
        IBlockState pipelineState = actualLightState(state, blockAccess, pos);
        int fallbackId = waterLikeFluidFallbackId(pipelineState);
        if (fallbackId < 32620) {
            return -1;
        }
        return BloomMaskColor.textureColorForState(pipelineState);
    }

    private void logWaterLikeMaterialProbe(IBlockState state, IBlockAccess blockAccess, BlockPos pos, int id, String source) {
        if (!DEBUG_PROBES_ENABLED || state == null || state.getMaterial() != Material.WATER) {
            return;
        }

        int call = waterLikeMaterialProbeCount.incrementAndGet();
        if (call > 96) {
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

    private static int waterLikeFluidFallbackId(IBlockState state) {
        if (state == null || state.getMaterial() != Material.WATER) {
            return 0;
        }

        ResourceLocation name = registryName(state);
        if (name == null) {
            return 0;
        }

        String namespace = name.getNamespace();
        String path = name.getPath();
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

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState inheritedBlockcrafteryState = inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (inheritedBlockcrafteryState != null) {
            return inheritedBlockcrafteryState;
        }
        return actualLightState(state, blockAccess, pos);
    }

    public IBlockState inheritedBlockcrafteryRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return null;
        }
        IBlockState inheritedState = firstInheritedRenderState(state, blockAccess, pos);
        if (inheritedState != null
                && inheritedState != state
                && inheritedState.getBlock() != null
                && !isBlockcrafteryEditableBlock(inheritedState)) {
            return inheritedState;
        }
        if (blockAccess != null || pos != null) {
            inheritedState = firstInheritedRenderState(state, null, null);
            if (inheritedState != null
                    && inheritedState != state
                    && inheritedState.getBlock() != null
                    && !isBlockcrafteryEditableBlock(inheritedState)) {
                return inheritedState;
            }
        }
        return null;
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

    public IBlockState firstInheritedRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        IBlockState[] inheritedStates = inheritedRenderStates(state, blockAccess, pos);
        return inheritedStates.length > 0 ? inheritedStates[0] : null;
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

    public boolean framedBlockDiagnosticsEnabled() {
        return false;
    }

    public boolean currentProblemProbesEnabled() {
        return false;
    }

    private static boolean debugProbeLoggingEnabled() {
        return false;
    }

    public boolean isBlockcrafteryEditableState(IBlockState state) {
        return isBlockcrafteryEditableBlock(state);
    }

    public boolean stateHasBloomLayerGeometry(IBlockState state) {
        if (state == null || state.getBlock() == null || isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        if (isExplicitBloomState(state)) {
            return true;
        }
        return stateHasBloomResourceGeometry(state);
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
        return blockRenderEmissionForState(state, blockAccess, pos);
    }

    public boolean shouldUseShaderlessBloomEmission() {
        return !isPipelineActive && !AusmBloomLayer.shouldUseShaderlessNativeHook();
    }

    public int blockShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        int explicit = explicitShaderlessBloomEmission(state, blockAccess, pos);
        if (explicit > 0) {
            return explicit;
        }

        IBlockState effectiveState = actualLightState(state, blockAccess, pos);
        return effectiveState != null && effectiveState != state
                ? explicitShaderlessBloomEmission(effectiveState, blockAccess, pos)
                : 0;
    }

    public boolean stateHasShaderlessBloomSource(IBlockState state) {
        return blockShaderlessBloomEmission(state, null, null) > 0;
    }

    public boolean stateUsesTextureBloomSource(IBlockState state) {
        if (state == null || state.getBlock() == null || isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        return stateHasBloomResourceGeometry(state) || isLumenizedBloomState(state);
    }

    private int explicitShaderlessBloomEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return 0;
        }
        int luminousEmission = randomThingsLuminousEmission(state);
        if (luminousEmission > 0) {
            return luminousEmission;
        }
        int astralEmission = astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        if (stateHasBloomLayerGeometry(state) || stateHasBloomResourceGeometry(state) || isLumenizedBloomState(state)) {
            return shaderlessBloomGeometryEmission(state, blockAccess, pos);
        }
        return 0;
    }

    private int shaderlessBloomGeometryEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (blockRenderEmissionForState(state, blockAccess, pos) > 0) {
            return SHADERLESS_LIGHT_EMITTING_BLOOM_GEOMETRY_EMISSION;
        }
        return SHADERLESS_BLOOM_GEOMETRY_EMISSION;
    }

    public int blockRenderEmissionWithFramedInheritance(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int emission = blockRenderEmission(state, blockAccess, pos);
        if (!isFramedBlockDiagnosticTarget(state)) {
            return emission;
        }
        for (IBlockState inheritedState : inheritedRenderStates(state, blockAccess, pos)) {
            if (isBloomOrEmissiveInheritedState(inheritedState, blockAccess, pos)) {
                emission = Math.max(emission, inheritedBlockRenderEmission(inheritedState));
            }
        }
        return emission;
    }

    public int shaderlessFramedBloomExtractionEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (isPipelineActive || !isFramedBlockDiagnosticTarget(state)) {
            return 0;
        }
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        IBlockState inheritedState = inheritedBloomRenderState(state, blockAccess, pos);
        if (inheritedState == null || inheritedState == state || inheritedState.getBlock() == null) {
            return 0;
        }
        int inheritedEmission = blockShaderlessBloomEmission(inheritedState, blockAccess, pos);
        if (inheritedEmission > 0) {
            return inheritedEmission;
        }
        return isBloomOrEmissiveInheritedState(inheritedState, blockAccess, pos) ? 15 : 0;
    }

    public int framedBloomFallbackEmission(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isFramedBlockDiagnosticTarget(state)) {
            return 0;
        }
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        IBlockState inheritedState = inheritedBloomRenderState(state, blockAccess, pos);
        if (inheritedState == null || inheritedState == state || inheritedState.getBlock() == null) {
            return 0;
        }
        int inheritedEmission = blockShaderlessBloomEmission(inheritedState, blockAccess, pos);
        if (inheritedEmission > 0) {
            return inheritedEmission;
        }
        return isBloomOrEmissiveInheritedState(inheritedState, blockAccess, pos) ? 15 : 0;
    }

    public boolean shouldInheritFramedEmissionInBasePass(IBlockState state) {
        return isFramedBlockDiagnosticTarget(state);
    }

    public int blockRenderAlpha(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return -1;
        }
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

    private String diagnosticBlockKind(IBlockState state, IBlockState effectiveState, IBlockAccess blockAccess, BlockPos pos) {
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
        return isRandomThingsLuminousBlock(state)
                || isPriorityFramedDiagnosticName(state)
                || isAstralCrystalCluster(state)
                || stateName(state).contains("lumenized")
                || stateName(state).contains("glow")
                || stateName(state).contains("emissive")
                || stateName(state).contains("shimmer")
                || stateName(state).contains("shinyflower")
                || stateName(state).contains("nitor")
                || stateName(state).contains("crystal");
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
                + "|" + String.valueOf(MinecraftForgeClient.getRenderLayer())
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
        if (!debugProbeLoggingEnabled()) {
            return;
        }
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        String kind = BlockRenderContext.debugKind();
        if (!"blockcraftery".equals(kind)
                && !"architecturecraft".equals(kind)
                && !"randomthings-luminous".equals(kind)
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
                + "|" + String.valueOf(MinecraftForgeClient.getRenderLayer())
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
                "[AUSMCurrentProblemProbe] call={} source={} kind={} activeLightOrId={} layer={} state={} effective={} contextEmission={} contextAlpha={} blockId={} bloomMask={} bloomMaskColor=0x{} detail={}",
                count,
                source,
                kind,
                activeLightOrId,
                MinecraftForgeClient.getRenderLayer(),
                BlockRenderContext.debugState(),
                BlockRenderContext.debugEffectiveState(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.blockAlpha(),
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.bloomMaskFallback(),
                Integer.toHexString(BlockRenderContext.bloomMaskColor()),
                detail
        );
    }

    public void probeShaderlessLightState(String stage) {
        // Probe disabled.
    }

    private String shaderlessWorldLightSummary(Minecraft mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            return "none";
        }
        BlockPos feet = new BlockPos(mc.player);
        BlockPos eye = new BlockPos(mc.player.posX, mc.player.posY + mc.player.getEyeHeight(), mc.player.posZ);
        return "feet{" + shaderlessWorldLightAt(mc.world, feet) + "}"
                + ",eye{" + shaderlessWorldLightAt(mc.world, eye) + "}";
    }

    private String shaderlessWorldLightAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return "none";
        }
        try {
            boolean loaded = world.isBlockLoaded(pos);
            int combined = loaded ? world.getCombinedLight(pos, 0) : -1;
            int sky = loaded ? world.getLightFor(EnumSkyBlock.SKY, pos) : -1;
            int block = loaded ? world.getLightFor(EnumSkyBlock.BLOCK, pos) : -1;
            boolean canSeeSky = loaded && world.canSeeSky(pos);
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
        // Old sky GUI probe intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    public void freshSkyProbe(String stage, String detail) {
        // Probe disabled.
    }

    private String freshSkySamples(Minecraft mc) {
        if (mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return "none";
        }
        try {
            int width = mc.displayWidth;
            int height = mc.displayHeight;
            return "center=" + readFramebufferPixel(width / 2, height / 2)
                    + ";upper=" + readFramebufferPixel(width / 2, Math.max(0, height * 3 / 4))
                    + ";lower=" + readFramebufferPixel(width / 2, Math.max(0, height / 4));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        }
    }

    private String readFramebufferPixel(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return (pixel.get(0) & 0xFF) + "/" + (pixel.get(1) & 0xFF) + "/" + (pixel.get(2) & 0xFF) + "/" + (pixel.get(3) & 0xFF);
    }

    private boolean isIgnoredShaderlessSkyProbeScreen(Minecraft mc) {
        if (mc == null || mc.currentScreen == null) {
            return false;
        }
        String screenClass = mc.currentScreen.getClass().getName();
        return "net.minecraft.client.gui.GuiChat".equals(screenClass);
    }

    private String skyRendererName(World world) {
        if (world == null || world.provider == null) {
            return "none";
        }
        try {
            Object skyRenderer = world.provider.getSkyRenderer();
            return skyRenderer != null ? skyRenderer.getClass().getName() : "none";
        } catch (RuntimeException ignored) {
            return "error";
        }
    }

    private int currentDrawFramebufferBinding() {
        return GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                : GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
    }

    private int boundTexture2D(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GlStateManager.setActiveTexture(textureUnit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        } finally {
            GlStateManager.setActiveTexture(previousActiveTexture);
        }
    }

    private boolean texture2DEnabled(int textureUnit) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GlStateManager.setActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        } finally {
            GlStateManager.setActiveTexture(previousActiveTexture);
        }
    }

    private boolean textureCoordArrayEnabled(int textureUnit) {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        try {
            OpenGlHelper.setClientActiveTexture(textureUnit);
            return GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        } finally {
            OpenGlHelper.setClientActiveTexture(previousClientTexture);
        }
    }

    private String fogColorSummary() {
        java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_FOG_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    private String currentColorSummary() {
        java.nio.FloatBuffer color = org.lwjgl.BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, color);
        return color.get(0) + "/" + color.get(1) + "/" + color.get(2) + "/" + color.get(3);
    }

    private String colorMaskSummary() {
        java.nio.ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        return (colorMask.get(0) != 0)
                + "/"
                + (colorMask.get(1) != 0)
                + "/"
                + (colorMask.get(2) != 0)
                + "/"
                + (colorMask.get(3) != 0);
    }

    private String viewportSummary() {
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

    private int blockRenderEmissionForState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
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

    private int inheritedBlockRenderEmission(IBlockState state) {
        int blockcrafteryEmission = blockcrafteryLightEmission(state);
        if (blockcrafteryEmission > 0) {
            return blockcrafteryEmission;
        }
        int luminousEmission = randomThingsLuminousEmission(state);
        if (luminousEmission > 0) {
            return luminousEmission;
        }
        int astralEmission = astralCrystalEmission(state);
        if (astralEmission > 0) {
            return astralEmission;
        }
        try {
            return clampLightValue(state.getLightValue());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public int blockIntrinsicEmission(IBlockState state) {
        return state != null ? inheritedBlockRenderEmission(state) : 0;
    }

    private int blockcrafteryLightEmission(IBlockState state) {
        if (!isBlockcrafteryEditableBlock(state)) {
            return 0;
        }
        try {
            for (Map.Entry<net.minecraft.block.properties.IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                net.minecraft.block.properties.IProperty<?> property = entry.getKey();
                if (property != null
                        && "light".equalsIgnoreCase(property.getName())
                        && Boolean.TRUE.equals(entry.getValue())) {
                    return 15;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return 0;
    }

    private IBlockState[] inheritedRenderStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return new IBlockState[0];
        }

        IBlockState blockcrafteryDecoratedState = blockcrafteryDecoratedState(state, blockAccess, pos);
        IBlockState blockcrafteryState = actualState(
                blockcrafteryDecoratedState,
                blockcrafteryDecoratedBlockAccess(blockAccess),
                pos
        );
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
        return blockShaderlessBloomEmission(state, blockAccess, pos) > 0;
    }

    private boolean stateHasBloomResourceGeometry(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        if (isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        String key = stateName(state);
        Boolean cached = bloomResourceGeometryStateCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (!bloomResourceGeometryScansInProgress.add(key)) {
            return false;
        }
        try {
            boolean result = scanStateForBloomResourceGeometry(state);
            bloomResourceGeometryStateCache.putIfAbsent(key, result);
            return result;
        } finally {
            bloomResourceGeometryScansInProgress.remove(key);
        }
    }

    private boolean scanStateForBloomResourceGeometry(IBlockState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getBlockRendererDispatcher() == null) {
            return false;
        }

        net.minecraft.client.renderer.block.model.IBakedModel model;
        try {
            model = mc.getBlockRendererDispatcher().getModelForState(state);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        if (model == null) {
            return false;
        }

        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                if (AusmBloomLayer.isBloomLayer(layer) || !canRenderInLayer(state, layer)) {
                    continue;
                }
                net.minecraftforge.client.ForgeHooksClient.setRenderLayer(layer);
                if (modelQuadsHaveBloomSprite(model, state, null)) {
                    return true;
                }
                for (EnumFacing side : EnumFacing.values()) {
                    if (modelQuadsHaveBloomSprite(model, state, side)) {
                        return true;
                    }
                }
            }
        } finally {
            net.minecraftforge.client.ForgeHooksClient.setRenderLayer(previousLayer);
        }
        return false;
    }

    private boolean modelQuadsHaveBloomSprite(net.minecraft.client.renderer.block.model.IBakedModel model,
                                              IBlockState state,
                                              EnumFacing side) {
        List<BakedQuad> quads;
        try {
            quads = model.getQuads(state, side, 0L);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
        if (quads == null || quads.isEmpty()) {
            return false;
        }
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad != null ? quad.getSprite() : null;
            if (sprite != null && (isEmissiveSpriteName(sprite.getIconName()) || bloomRenderer.hasBloomSprite(sprite.getIconName()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmissiveSpriteName(String spriteName) {
        if (spriteName == null) {
            return false;
        }
        String normalized = spriteName.toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("_e")
                || normalized.contains("_e/")
                || normalized.contains("/emissive")
                || normalized.contains("_emissive")
                || normalized.contains("/glow")
                || normalized.contains("_glow")
                || normalized.contains("/bloom")
                || normalized.contains("_bloom");
    }

    private boolean isExplicitBloomState(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }
        String path = name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        String namespace = name.getNamespace() != null ? name.getNamespace().toLowerCase(java.util.Locale.ROOT) : "";
        String blockClass = state.getBlock().getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return namespace.contains("lumenized")
                || path.contains("lumenized")
                || path.contains("luminous")
                || path.contains("emissive")
                || path.contains("bloom")
                || blockClass.contains("lumenized");
    }

    private boolean isLumenizedBloomState(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return false;
        }
        String path = name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        String namespace = name.getNamespace() != null ? name.getNamespace().toLowerCase(java.util.Locale.ROOT) : "";
        String blockClass = state.getBlock() != null
                ? state.getBlock().getClass().getName().toLowerCase(java.util.Locale.ROOT)
                : "";
        return namespace.contains("lumenized") || path.contains("lumenized") || blockClass.contains("lumenized");
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
        if (stateHasBloomLayerGeometry(state)) {
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
        return ENABLE_CPU_LIGHT_INJECTION
                && ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION
                && isPipelineActive
                && shaderImages.active()
                && !shaderProperties.blockIds().isEmpty();
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

    private void logDecoratedLightEmission(IBlockState originalState, IBlockState decoratedState,
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

    private void auditProjectRedLight(TileEntity tileEntity, int[] voxelIds, int count, String result) {
        String diagnosis = ProjectRedIlluminationCompat.diagnose(tileEntity);
        if (diagnosis == null) {
            return;
        }
        auditProjectRedDiagnosis(tileEntity, voxelIds, count, result, diagnosis);
    }

    private void auditProjectRedDiagnosis(TileEntity tileEntity, int[] voxelIds, int count, String result, String diagnosis) {
        if (MAX_COLORED_LIGHT_AUDIT_LOGS <= 0) {
            return;
        }
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

        IBlockState tileDecoratedState = blockcrafteryTileDecoratedState(state, blockAccess, pos);
        if (isValidBlockcrafteryDecoratedState(tileDecoratedState)) {
            return tileDecoratedState;
        }

        IBlockState extendedDecoratedState = blockcrafteryExtendedDecoratedState(state);
        if (isValidBlockcrafteryDecoratedState(extendedDecoratedState)) {
            return extendedDecoratedState;
        }

        return null;
    }

    private IBlockAccess blockcrafteryDecoratedBlockAccess(IBlockAccess blockAccess) {
        return blockAccess != null ? new BlockcrafteryDecoratedBlockAccess(blockAccess) : null;
    }

    private IBlockState blockcrafteryTileDecoratedState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!isBlockcrafteryEditableBlock(state) || blockAccess == null || pos == null) {
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

    private final class BlockcrafteryDecoratedBlockAccess implements IBlockAccess {
        private final IBlockAccess delegate;

        private BlockcrafteryDecoratedBlockAccess(IBlockAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return delegate.getTileEntity(pos);
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return delegate.getCombinedLight(pos, lightValue);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            IBlockState state = delegate.getBlockState(pos);
            IBlockState decoratedState = blockcrafteryDecoratedState(state, delegate, pos);
            return decoratedState != null ? decoratedState : state;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            IBlockState state = getBlockState(pos);
            try {
                return state == null || state.getBlock() == null || state.getBlock().isAir(state, this, pos);
            } catch (RuntimeException | LinkageError ignored) {
                return delegate.isAirBlock(pos);
            }
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return delegate.getBiome(pos);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return delegate.getStrongPower(pos, direction);
        }

        @Override
        public WorldType getWorldType() {
            return delegate.getWorldType();
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            IBlockState state = getBlockState(pos);
            try {
                return state != null && state.getBlock() != null
                        ? state.getBlock().isSideSolid(state, this, pos, side)
                        : defaultValue;
            } catch (RuntimeException | LinkageError ignored) {
                return delegate.isSideSolid(pos, side, defaultValue);
            }
        }
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
        return !isPipelineActive || !shouldSkipAllMainGbufferRendering() && shaderProperties.renderSettings().weather();
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
        return isPipelineActive && !shaderProperties.renderSettings().sun();
    }

    public boolean shouldSuppressVanillaMoonGeometry() {
        return isPipelineActive && !shaderProperties.renderSettings().moon();
    }

    public boolean shouldSuppressVanillaStarsGeometry() {
        return isPipelineActive && !shaderProperties.renderSettings().stars();
    }

    public boolean shouldSuppressVanillaSunsetGeometry() {
        return false;
    }

    public boolean shouldSuppressVoidWorldCustomSkyRenderer(Object skyRenderer, WorldClient world) {
        return false;
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

    public Vec3d shaderlessAstralVoidSkyColor(WorldClient world, Entity entity, float partialTicks, Vec3d originalSkyColor) {
        return originalSkyColor;
    }

    public Vec3d forcedShaderlessAstralVoidBaseSkyColor() {
        return null;
    }

    private Vec3d forcedShaderlessAstralVoidBaseSkyColor(WorldClient world) {
        if (world == null) {
            return null;
        }
        double time = (world.getWorldTime() % 24000L) / 24000.0D;
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

    private void logShaderlessAstralSkyColor(String stage, WorldClient world, Entity entity, float partialTicks, Vec3d originalSkyColor, Vec3d effectiveSkyColor, double originalMax, boolean guiWorldRender) {
        // Old sky probe intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    private static String formatVec3(Vec3d value) {
        if (value == null) {
            return "null";
        }
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", value.x, value.y, value.z);
    }

    private void logAstralVoidSkyProbe(String stage, WorldClient world, double originalHorizon, double adjustedHorizon, float partialTicks) {
        // Probe disabled.
}

    public boolean shouldSanitizeShaderlessNothiriumFog() {
        Minecraft mc = Minecraft.getMinecraft();
        return !isPipelineActive
                && mc != null
                && mc.world != null
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    public void beginShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        if (!shouldDisableShaderlessNothiriumTerrainFog()) {
            return;
        }

        if (shaderlessNothiriumTerrainFogGuardDepth++ == 0) {
            shaderlessNothiriumTerrainFogPreviouslyEnabled = GL11.glIsEnabled(GL11.GL_FOG);
            if (shaderlessNothiriumTerrainFogPreviouslyEnabled) {
                GL11.glDisable(GL11.GL_FOG);
            }
            logNothiriumFogGuard(renderer, "begin", pass, shaderlessNothiriumTerrainFogPreviouslyEnabled);
        }
    }

    public void endShaderlessNothiriumTerrainFogGuard(String renderer, Object pass) {
        if (shaderlessNothiriumTerrainFogGuardDepth <= 0) {
            return;
        }

        boolean restoreFog = shaderlessNothiriumTerrainFogPreviouslyEnabled;
        shaderlessNothiriumTerrainFogGuardDepth--;
        if (shaderlessNothiriumTerrainFogGuardDepth == 0) {
            if (restoreFog) {
                GL11.glEnable(GL11.GL_FOG);
            }
            logNothiriumFogGuard(renderer, "end", pass, restoreFog);
            shaderlessNothiriumTerrainFogPreviouslyEnabled = false;
        }
    }

    private boolean shouldDisableShaderlessNothiriumTerrainFog() {
        Minecraft mc = Minecraft.getMinecraft();
        return !isPipelineActive
                && mc != null
                && mc.world != null
                && safeDimensionId(mc.world) == SIMPLE_VOID_WORLD_DIMENSION_ID
                && !isRenderingBetterPortalsNestedView()
                && !isRenderingBetterPortalsRenderPass();
    }

    private void logNothiriumFogGuard(String renderer, String stage, Object pass, boolean restoredOrDisabled) {
        // Probe disabled.
    }

    public void logNothiriumFogProbe(
            String stage,
            boolean fogEnabled,
            int fogMode,
            float fogStart,
            float fogEnd,
            float fogDensity,
            float[] originalColor,
            float[] adjustedColor
    ) {
        // Probe disabled.
    }

    public void logNothiriumRenderProbe(String renderer, String stage, Object pass) {
        // Probe disabled.
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
        int requestedBlockLight = MathHelper.clamp(lightValue, 0, 15);
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

    private boolean shouldRepairShaderlessVoidWorldSkyLight(BlockPos pos) {
        if (isPipelineActive
                || pos == null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc != null ? mc.world : null;
        if (world == null || safeDimensionId(world) != SIMPLE_VOID_WORLD_DIMENSION_ID) {
            return false;
        }
        try {
            BlockPos skyProbePos = pos.up();
            return world.isBlockLoaded(skyProbePos) && world.canSeeSky(skyProbePos);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private void logShaderlessVoidLightRepair(String source, IBlockAccess blockAccess, BlockPos pos, int before, int after, int lightValue) {
        // Probe disabled.
    }

    public void probeShaderlessVoidSkyFramebufferPixels(String stage) {
        // Probe disabled.
    }

    public void captureShaderlessWorldFramebufferForUi() {
        if (isPipelineActive || !shaderlessWorldPassActive || isRenderingBetterPortalsNestedView() || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            shaderlessWorldFramebufferForUi = 0;
            shaderlessWorldFramebufferFrame = Long.MIN_VALUE;
            return;
        }

        int drawFramebuffer = currentDrawFramebufferBinding();
        if (drawFramebuffer <= 0) {
            return;
        }

        shaderlessWorldFramebufferForUi = drawFramebuffer;
        shaderlessWorldFramebufferWidth = Math.max(1, mc.displayWidth);
        shaderlessWorldFramebufferHeight = Math.max(1, mc.displayHeight);
        shaderlessWorldFramebufferFrame = clientRenderFrameNanos;
    }

    public void syncShaderlessWorldFramebufferBeforeGui() {
        Minecraft mc = Minecraft.getMinecraft();
        if (isPipelineActive
                || mc == null
                || mc.world == null
                || mc.getFramebuffer() == null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || shaderlessWorldFramebufferForUi <= 0
                || shaderlessWorldFramebufferFrame != clientRenderFrameNanos) {
            return;
        }

        Framebuffer target = mc.getFramebuffer();
        if (target.framebufferObject == shaderlessWorldFramebufferForUi) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, shaderlessWorldFramebufferForUi);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.framebufferObject);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    shaderlessWorldFramebufferWidth,
                    shaderlessWorldFramebufferHeight,
                    0,
                    0,
                    target.framebufferWidth,
                    target.framebufferHeight,
                    GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
            GL11.glDrawBuffer(previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
        }
    }

    private void logShaderlessWorldFramebufferHandoff(String stage, String detail) {
        // Probe disabled.
    }

    private String sampleFramebufferForHandoff(int framebuffer, int width, int height) {
        if (framebuffer <= 0 || width <= 0 || height <= 0) {
            return "invalid";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int x = Math.max(0, width / 2);
            int lowerY = Math.max(0, height / 4);
            int centerY = Math.max(0, height / 2);
            int upperY = Math.max(0, height * 3 / 4);
            return "lower=" + readFramebufferPixelSummary(x, lowerY)
                    + ", center=" + readFramebufferPixelSummary(x, centerY)
                    + ", upper=" + readFramebufferPixelSummary(x, upperY);
        } catch (RuntimeException | LinkageError ignored) {
            return "unreadable";
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    public void repairShaderlessVoidSkyBeforeGui(float partialTicks) {
        // Old sky repair/probe path intentionally disabled; use AUSMFreshSkyProbe instead.
    }

    private void renderShaderlessVoidSkyRepair(Minecraft mc, float partialTicks) {
        if (mc == null || mc.renderGlobal == null || mc.getFramebuffer() == null) {
            logShaderlessVoidSkyRepair("skip-render-no-renderglobal", null);
            return;
        }

        int previousFramebuffer = currentDrawFramebufferBinding();
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean previousDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousTexture2d = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
        // Probe disabled.
} catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.warn("[AUSMVoidSkyRepair] Failed to re-render shaderless void sky before GUI", e);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            GL11.glDrawBuffer(previousDrawBuffer);
            OpenGlHelper.glUseProgram(previousProgram);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glDepthMask(previousDepthMask);
            if (previousDepth) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            if (previousBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (previousAlpha) {
                GL11.glEnable(GL11.GL_ALPHA_TEST);
            } else {
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }
            if (previousTexture2d) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            if (previousCull) {
                GL11.glEnable(GL11.GL_CULL_FACE);
            } else {
                GL11.glDisable(GL11.GL_CULL_FACE);
            }
            if (previousFog) {
                GL11.glEnable(GL11.GL_FOG);
            } else {
                GL11.glDisable(GL11.GL_FOG);
            }
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    private VoidSkyRepairSamples sampleVoidSkyRepairPixels(int width, int height) {
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

    private VoidSkyRepairPixel readFramebufferRepairPixel(int x, int y) {
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

    private void logShaderlessVoidSkyRepair(String stage, String detail) {
        // Diagnostic disabled.
}

    private record VoidSkyRepairSamples(boolean needsRepair, String summary) {
    }

    private record VoidSkyRepairPixel(int r, int g, int b, int a, float depth) {
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

    private String readFramebufferPixelSummary(int x, int y) {
        try {
            IntBuffer color = BufferUtils.createIntBuffer(1);
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            int rgba = color.get(0);
            float z = depth.get(0);
            int r = rgba & 0xFF;
            int g = rgba >> 8 & 0xFF;
            int b = rgba >> 16 & 0xFF;
            int a = rgba >> 24 & 0xFF;
            return x + "," + y + "=rgba(" + r + "," + g + "," + b + "," + a + ") depth=" + z;
        } catch (RuntimeException | LinkageError ignored) {
            return x + "," + y + "=unreadable";
        }
    }

    private String formatFloatArray(float[] values) {
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }
        previousRenderChunksManyForOcclusion = mc.renderChunksMany;
        terrainOcclusionOverrideActive = true;
        mc.renderChunksMany = false;
    }

    public void restoreTerrainOcclusionCullingSetting() {
        if (!terrainOcclusionOverrideActive) {
            return;
        }
        terrainOcclusionOverrideActive = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null) {
            mc.renderChunksMany = previousRenderChunksManyForOcclusion;
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
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || !mc.isCallingFromMinecraftThread()) {
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

    private boolean shouldUseShaderlessBloomVertexMetadata() {
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
        // Intentionally disabled in normal builds; keep the hook as a cheap
        // no-op so old probes can be re-enabled locally without touching mixins.
    }

    private String skyProbeWorldSummary() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc != null ? mc.world : null;
        Entity view = mc != null ? mc.getRenderViewEntity() : null;
        if (world == null) {
            return "null";
        }
        return "dim=" + safeDimensionId(world)
                + ",time=" + world.getWorldTime()
                + ",celestial=" + world.getCelestialAngle(0.0f)
                + ",rain=" + world.getRainStrength(0.0f)
                + ",thunder=" + world.getThunderStrength(0.0f)
                + ",viewYaw=" + (view != null ? view.rotationYaw : Float.NaN)
                + ",viewPitch=" + (view != null ? view.rotationPitch : Float.NaN);
    }

    private static String skyProbeGlStateSummary() {
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
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || activePass == null || viewEntity == null) {
            return -1;
        }
        if (hardwareSafeVanillaTerrain) {
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
        double fallbackDistance = nothiriumMainTerrainFallbackDistance();
        nothiriumShadowRenderer.scheduleLayerCompiles(layer, cameraX, cameraY, cameraZ, fallbackDistance);
        int visibleCount = nothiriumShadowRenderer.renderVisibleLayer(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                nothiriumFallbackBlockEntityId(layer),
                nothiriumFallbackRenderType(layer)
        );
        if (visibleCount > 0) {
            return visibleCount;
        }

        return nothiriumShadowRenderer.renderLayerSchedulingCompiles(
                layer,
                cameraX,
                cameraY,
                cameraZ,
                fallbackDistance
        );
    }

    private double nothiriumMainTerrainFallbackDistance() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return -1.0D;
        }
        int chunks = Math.max(2, mc.gameSettings.renderDistanceChunks);
        return chunks * 16.0D + 32.0D;
    }

    public boolean renderNothiriumRendererPass(Object chunkRenderPass) {
        if (shouldBypassWorldPassRendering()
                || BetterPortalsCompat.shouldUseVanillaRenderGlobalForNestedView()) {
            return false;
        }
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

    private int vehicleId(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.getRidingEntity() == null) {
            return 0;
        }
        return entityId(mc.player.getRidingEntity());
    }

    private boolean vehicleInWater(Minecraft mc) {
        return mc != null
                && mc.player != null
                && mc.player.getRidingEntity() != null
                && mc.player.getRidingEntity().isInWater();
    }

    private float[] vehicleLookVector(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.getRidingEntity() == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return vec3(mc.player.getRidingEntity().getLookVec());
    }

    private float[] relativeVehiclePosition(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.getRidingEntity() == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        Entity vehicle = mc.player.getRidingEntity();
        float partialTicks = mc.getRenderPartialTicks();
        double x = interpolate(vehicle.prevPosX, vehicle.posX, partialTicks);
        double y = interpolate(vehicle.prevPosY, vehicle.posY, partialTicks);
        double z = interpolate(vehicle.prevPosZ, vehicle.posZ, partialTicks);
        return new float[]{
                (float) (cameraPositionUnshifted[0] - x),
                (float) (cameraPositionUnshifted[1] - y),
                (float) (cameraPositionUnshifted[2] - z)
        };
    }

    private static float[] bodyVector(Entity entity) {
        if (entity == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return vec3(entity.getForward());
    }

    private float[] lightningBoltPosition(Minecraft mc) {
        World world = renderWorld(mc);
        if (mc == null || world == null) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }
        float partialTicks = mc.getRenderPartialTicks();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLightningBolt) {
                double x = interpolate(entity.prevPosX, entity.posX, partialTicks);
                double y = interpolate(entity.prevPosY, entity.posY, partialTicks);
                double z = interpolate(entity.prevPosZ, entity.posZ, partialTicks);
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

    private void updateEndFlashState(Minecraft mc) {
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

        float partialTicks = mc.getRenderPartialTicks();
        EntityDragon strongestDragon = null;
        float strongestIntensity = 0.0f;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityDragon dragon) || dragon.deathTicks <= 0) {
                continue;
            }
            float intensity = clamp01((dragon.deathTicks + partialTicks) / 200.0f);
            if (intensity > strongestIntensity) {
                strongestIntensity = intensity;
                strongestDragon = dragon;
            }
        }
        if (strongestDragon == null) {
            return;
        }

        double x = interpolate(strongestDragon.prevPosX, strongestDragon.posX, partialTicks) - cameraPositionUnshifted[0];
        double y = interpolate(strongestDragon.prevPosY, strongestDragon.posY, partialTicks) - cameraPositionUnshifted[1];
        double z = interpolate(strongestDragon.prevPosZ, strongestDragon.posZ, partialTicks) - cameraPositionUnshifted[2];
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

    private void resetEndFlashState() {
        endFlashPosition[0] = 0.0f;
        endFlashPosition[1] = 0.0f;
        endFlashPosition[2] = 0.0f;
        endFlashIntensity = 0.0f;
        previousEndFlashIntensity = 0.0f;
        endFlashYawDegrees = 0.0f;
        endFlashPitchDegrees = 0.0f;
    }

    private boolean useEndFlashShadowLight(World world) {
        return shaderProperties.renderSettings().supportsEndFlash()
                && isEndWorld(world)
                && endFlashIntensity > 0.0f;
    }

    private static boolean isEndWorld(World world) {
        return world != null && world.provider != null && world.provider.getDimension() == 1;
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

    private String renderedItemDebugName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        ResourceLocation key = stack.getItem().getRegistryName();
        return (key != null ? key.toString() : stack.getItem().getClass().getName())
                + ":" + stack.getMetadata();
    }

    private void uploadCurrentRenderedItemId() {
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

    private int currentRenderedItemId(ItemStack stack) {
        Integer explicitItemId = shaderProperties.itemIds().explicitIdFor(stack);
        if (explicitItemId != null) {
            return explicitItemId;
        }
        int blockItemId = currentRenderedBlockItemId(stack);
        return blockItemId != 0 ? blockItemId : 0;
    }

    private int currentRenderedBlockItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == null || block == Blocks.AIR) {
            return 0;
        }
        try {
            int metadata = stack.getMetadata();
            IBlockState state = block.getStateFromMeta(metadata);
            if (state != null) {
                return shaderProperties.blockIds().idFor(state);
            }
        } catch (RuntimeException ignored) {
        }
        return shaderProperties.blockIds().idFor(block.getDefaultState());
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

    private float[] heldBlockLightColor(ItemStack stack) {
        int lightValue = heldBlockLightValue(stack);
        if (lightValue <= 0) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        float[] itemColor = compatLightColorForVoxelId(localActItemVoxelId(shaderItemId));
        if (itemColor != null) {
            return itemColor;
        }

        Block block = Block.getBlockFromItem(stack.getItem());
        if (block != null) {
            int shaderBlockId = currentRenderedBlockItemId(stack);
            float[] blockColor = compatLightColorForVoxelId(localActVoxelId(shaderBlockId));
            if (blockColor != null) {
                return blockColor;
            }
        }

        return new float[]{1.0f, 1.0f, 1.0f};
    }

    private static int localActItemVoxelId(int itemId) {
        if (itemId == 44024) {
            return 24;
        }
        if (itemId >= 44070 && itemId <= 44080) {
            return itemId - 44000;
        }
        return 0;
    }

    private static float[] compatLightColorForVoxelId(int voxelId) {
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
                GL11.GL_ZERO,
                GL11.GL_ONE
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
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        activePhase = phase;
        boolean bound = bindPass(pass);
        passStack.push(new PassScope(bound, previousPass, previousShaderKey, previousPhase, previousProgramTessellated, previousProgramGeometric));
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

    public void beginAstralConstellationPhase(Object constellation, WorldRenderingPhase phase) {
        setAstralConstellationColors(constellation);
        beginPhase(phase);
    }

    public void setAstralSolarEclipseFactor(float factor) {
        currentAstralSolarEclipseFactor = Math.max(0.0f, Math.min(1.0f, factor));
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

    public void prepareHandItemRenderState() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase != WorldRenderingPhase.HAND_SOLID) {
            return;
        }
        GlStateManager.disableBlend();
        resetIndexedBlendState();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareUntexturedEmissiveWorldRenderState() {
        if (!isPipelineActive || !worldFrameActive || renderingGuiScreen()) {
            return;
        }
        TextureBinder.bindFallbackWhiteTexture();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareGuiItemRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null && !renderingGuiScreen()) {
            return;
        }

        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        if (!shaderlessBloomExtractionActive) {
            unbindShaderStorageBuffers();
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableCull();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareFlatGuiBackgroundRenderState() {
        probeShaderlessSkyGuiState("flat-gui-background-before");
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableDepth();
        GL11.glDepthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        probeShaderlessSkyGuiState("flat-gui-background-after");
    }

    public void prepareGuiEntityPreviewRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null && !renderingGuiScreen()) {
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

        if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        if (mc.entityRenderer != null) {
            mc.entityRenderer.disableLightmap();
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
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
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.disableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.disableCull();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void finishGuiEntityPreviewRenderState() {
        if (guiEntityPreviewStateDepth <= 0) {
            return;
        }
        guiEntityPreviewStateDepth--;
        GL11.glPopAttrib();
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
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

    public boolean beginGuiItemStateScope() {
        if (!isPipelineActive) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null && !renderingGuiScreen()) {
            return false;
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
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
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
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    public void endGuiItemStateScope() {
        GL11.glPopAttrib();
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
    }

    public void prepareGuiItemGlintRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen == null && !renderingGuiScreen()) {
            return;
        }

        OpenGlHelper.glUseProgram(0);
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
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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

    private boolean shouldRouteRenderItemThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase != WorldRenderingPhase.HAND_SOLID
                && phase != WorldRenderingPhase.HAND_TRANSLUCENT
                && phase != WorldRenderingPhase.ARMOR_GLINT
                && phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    private boolean shouldRouteItemGlintThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || renderingGuiScreen()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen != null) {
            return false;
        }
        WorldRenderingPhase phase = getPhase();
        return phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    private boolean renderingGuiScreen() {
        return renderingGui || guiRenderDepth > 0 || renderingDeferredIngameHud;
    }

    public boolean isRenderingGuiScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        return renderingGuiScreen() || mc != null && mc.currentScreen != null;
    }

    public boolean shouldDrawActiveProgramAsPatches() {
        return isPipelineActive && activeProgramTessellated && (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_tessellation_shader);
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
        return isPipelineActive && activeProgramGeometric && !shouldDrawActiveProgramAsPatches();
    }

    private static void setPatchVertices(int vertices) {
        if (GLContext.getCapabilities().OpenGL40) {
            GL40.glPatchParameteri(GL40.GL_PATCH_VERTICES, vertices);
        } else if (GLContext.getCapabilities().GL_ARB_tessellation_shader) {
            ARBTessellationShader.glPatchParameteri(ARBTessellationShader.GL_PATCH_VERTICES, vertices);
        }
    }

    private RenderPass passForPhase(WorldRenderingPhase phase) {
        return renderingShadowMap ? phase.shadowPass() : phase.mainPass();
    }

    private boolean bindPass(RenderPass pass) {
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
        configureGbufferDrawBuffers(bindingProgram, drawBuffers);
        configureShadowDrawBuffers(bindingProgram, drawBuffers);
        if (bindingProgram.stage() == ProgramStage.GBUFFERS) {
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
        if (bindingProgram.stage().readsDeferredTextures()) {
            TextureBinder.bindDeferredTextures();
        } else {
            TextureBinder.bindNoiseTexture();
        }
        if (bindingProgram.stage() != ProgramStage.SHADOW) {
            TextureBinder.bindShadowTextures();
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
        return true;
    }

    private PipelineProgram effectivePipelineProgram(RenderPass pass) {
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

    private void bindProgramResources(RenderPass pass, ShaderProgram program) {
        bindCustomTextures(pass, program);
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
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.DH_TERRAIN
                || pass == RenderPass.DH_WATER;
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
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
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
        if (pass == RenderPass.GBUFFERS_WATER || pass == RenderPass.DH_WATER) {
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
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.DH_TERRAIN) {
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
        if (pass != RenderPass.GBUFFERS_HAND && pass != RenderPass.GBUFFERS_HAND_WATER) {
            return;
        }
        GlStateManager.disableBlend();
        resetIndexedBlendState();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
        if (!isOpaqueTerrainPass(pass) && pass != RenderPass.GBUFFERS_WATER && pass != RenderPass.DH_WATER) {
            return;
        }
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GL11.glColorMask(true, true, true, true);
    }

    private void applySkyDepthState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_SKYBASIC && pass != RenderPass.GBUFFERS_SKYTEXTURED) {
            return;
        }
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
    }

    private void applyBeaconBeamDepthState(RenderPass pass) {
        if (shaderProperties.renderSettings().beaconBeamDepth()) {
            return;
        }
        if (pass != RenderPass.GBUFFERS_BEACONBEAM && getPhase() != WorldRenderingPhase.BEACON_BEAM) {
            return;
        }
        GlStateManager.enableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(false);
    }

    private static boolean isOpaqueTerrainPass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.DH_TERRAIN;
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

    private void configureShadowDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
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
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        updateCameraPosition(mc);
        refreshHardwareSafeVanillaTerrainForCamera(mc);
        boolean resetTemporalHistory = shouldResetTemporalHistory(mc, paused, betterPortalsExternalTarget);
        if (paused || betterPortalsExternalTarget) {
            System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
            System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);
        } else {
            updateSmoothedFrameTime();
            updateSmoothedEyeBrightness(mc);
            updateSmoothedWetness(mc);
            updateEndFlashState(mc);
        }
        pingPongManager.beginFrameWithInitialTarget(fallbackColorAttachment(), frameClearAttachments(resetTemporalHistory));
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
            resetShaderlessTerrainLayerCounts();
            if (vanillaParticleRecoveryFrames > 0) {
                vanillaParticleRecoveryFrames--;
            }
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
        prepareVanillaParticleRenderingState();
    }

    public boolean shouldRenderParticlesWithVanillaState() {
        return vanillaParticleRecoveryFrames > 0;
    }

    public void clearClientParticles(String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.effectRenderer == null) {
            return;
        }
        mc.effectRenderer.clearEffects(mc.world);
        ThaumcraftParticleBridge.clearParticles(reason);
        vanillaParticleRecoveryFrames = 0;
        logTerrainDiagnostic("particles:clear", mc.world, "reason=" + reason);
    }

    public void prepareVanillaParticleRenderingState() {
        // Probe disabled.
}

    private void startVanillaParticleRecovery() {
        vanillaParticleRecoveryFrames = Math.max(vanillaParticleRecoveryFrames, PARTICLE_DIMENSION_RECOVERY_FRAMES);
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

    private static void restoreShaderlessTerrainClientTextureArrays() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(previousClientTexture);
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
        float horizontalVelocity = cameraHorizontalVelocityMagnitude();
        float verticalVelocity = Math.abs(cameraPosition[1] - previousCameraPosition[1]);

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
        if (horizontalVelocity > TEMPORAL_HISTORY_CAMERA_DELTA_RESET
                || verticalVelocity > TEMPORAL_HISTORY_VERTICAL_CAMERA_DELTA_RESET) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "camera-delta";
            return true;
        }
        if (accumulatedTemporalYaw > TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET
                || accumulatedTemporalPitch > TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET) {
            // Normal mouse-look should not clear persistent shader history.
            // Clearing temporal/deferred attachments during rotation looks like
            // distant terrain/chunk flicker on packs that keep persistent history.
            accumulatedTemporalYaw = 0.0f;
            accumulatedTemporalPitch = 0.0f;
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

    private float cameraHorizontalVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + z * z);
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
        Boolean shadowEnabled = shaderProperties.renderSettings().shadowEnabled();
        if (shadowEnabled != null) {
            return shadowEnabled;
        }
        for (PipelineProgram program : programs.values()) {
            if (program.stage() == ProgramStage.SHADOW && program.effectiveProgram(programs) != null) {
                return true;
            }
        }
        if (!shadowComputePrograms.isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()) {
            return true;
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
        return !shouldUseNothiriumShadowBridge();
    }

    public boolean shouldRenderShadowMapAfterTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return false;
    }

    public boolean shouldRenderShadowMapAfterOpaqueTerrain() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return shouldUseNothiriumShadowBridge();
    }

    private boolean shouldUseNothiriumShadowBridge() {
        return NothiriumShadowRenderer.isAvailable()
                && !hardwareSafeVanillaTerrain
                && !NothiriumBypass.shouldBypass();
    }

    private boolean shouldReuseMainTerrainForShadowMap() {
        return false;
    }

    public void ensureVanillaTerrainRenderer() {
        Minecraft mc = Minecraft.getMinecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        ensureVanillaTerrainRenderer(
                world != null ? world : (mc != null ? mc.world : null),
                hardwareSafeVanillaTerrain || isPipelineActive
        );
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
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && !BetterPortalsCompat.isRenderingNestedView()
                && mc != null
                && mc.world != null) {
            ensureVanillaTerrainRenderer(mc.world, true);
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (savedViewFrustum == null) {
            if (mc != null && mc.world != null && renderGlobal.ausm$viewFrustum() == null) {
                ensureVanillaTerrainRenderer(mc.world, true);
            }
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (renderGlobal.ausm$viewFrustum() != savedViewFrustum) {
            renderGlobal.ausm$setViewFrustum(savedViewFrustum);
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
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
        updateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity);
        logCameraFrustumSyncIfChanged(mc.world, viewFrustum, viewEntity, renderPassWorld != null, worldChanged);
    }

    private boolean shouldSyncShaderlessVanillaViewFrustumForCamera() {
        return (BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass())
                || (isPipelineActive && hardwareSafeVanillaTerrain);
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
        startVanillaParticleRecovery();
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
        clearClientParticles("bp-main-view-swap");
        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            BetterPortalsCompat.startMainViewSwapRecovery(mc.world);
            BetterPortalsCompat.logMainViewSwapRecoveryIfNeeded(mc.world);
            rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "bp-main-view-swap");
            resetCameraFrustumSyncState();
            scheduleDimensionSwitchTerrainRefresh();
            scheduleBloomTerrainRefresh("bp-main-view-swap");
            scheduleInactiveVanillaRecoveryFrame();
            scheduleWorldLoadLightRecalculation();
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
        clearClientParticles("dimension-switch");
        startVanillaParticleRecovery();
        BetterPortalsCompat.clearMainViewSwapTransientState();
        if (!isPipelineActive) {
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            clearPendingShaderChunkRefreshes();
            clearShaderlessBloomMetadata();
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
        clearPendingBetterPortalsPortalBlockRefresh();
        boolean betterPortalsRecovery = BetterPortalsCompat.isMainViewSwapRecoveryActive();
        if (betterPortalsRecovery) {
            rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "dimension-switch-bp-recovery");
            resetCameraFrustumSyncState();
            scheduleDimensionSwitchTerrainRefresh();
            scheduleBloomTerrainRefresh("dimension-switch-bp-recovery");
            scheduleInactiveVanillaRecoveryFrame();
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic("dimension-switch:bp-recovery-deferred", mc.world,
                    "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        clearShaderlessBloomMetadata();
        resetPipelineState(mc.getFramebuffer());
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "dimension-switch");
        resetCameraFrustumSyncState();
        scheduleDimensionSwitchTerrainRefresh();
        scheduleBloomTerrainRefresh("dimension switch");
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        logTerrainDiagnostic("dimension-switch:scheduled", mc.world, "previous=" + previousDimensionId + ", current=" + dimensionId
                + ", bpRecoveryWasActive=" + betterPortalsRecovery);
    }

    public void handleClientTeleportResync(int previousDimensionId, int currentDimensionId, double distanceSq, double horizontalDistanceSq) {
        boolean dimensionChanged = previousDimensionId != Integer.MIN_VALUE
                && currentDimensionId != Integer.MIN_VALUE
                && previousDimensionId != currentDimensionId;
        boolean longTeleport = horizontalDistanceSq >= CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ;
        if (!dimensionChanged && !longTeleport) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }

        String reason = dimensionChanged ? "client-teleport-dimension" : "client-teleport";
        logTerrainDiagnostic(reason + ":start", mc.world,
                "previous=" + previousDimensionId + ", current=" + currentDimensionId
                        + ", distanceSq=" + distanceSq + ", horizontalDistanceSq=" + horizontalDistanceSq);
        if (dimensionChanged) {
            clearClientParticles(reason);
        }
        startVanillaParticleRecovery();

        if (!dimensionChanged) {
            clearPendingShaderChunkRefreshes();
            clearPendingBetterPortalsPortalBlockRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            resetCameraFrustumSyncState();
            scheduleWorldTerrainRefresh();
            scheduleWorldLoadLightRecalculation();
            if (!isPipelineActive) {
                recoverShaderlessMainWorldTerrain(mc, reason);
            } else {
                scheduleInactiveVanillaRecoveryFrame();
            }
            logTerrainDiagnostic(reason + ":scheduled", mc.world, "preservedClientChunkQueue=true");
            return;
        }

        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.cancelMainViewSwapRecovery();
        clearPendingShaderChunkRefreshes();
        clearPendingBetterPortalsPortalBlockRefresh();
        clearShaderlessBloomMetadata();
        clearScheduledWorldTerrainRefresh();
        clearScheduledBloomTerrainRefresh();
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        if (!isPipelineActive) {
            recoverShaderlessMainWorldTerrain(mc, reason);
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic(reason + ":shaderless", mc.world, "");
            return;
        }

        resetPipelineState(mc.getFramebuffer());
        rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, reason);
        resetCameraFrustumSyncState();
        scheduleFullWorldTerrainRefresh();
        scheduleBloomTerrainRefresh(reason);
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        logTerrainDiagnostic(reason + ":scheduled", mc.world, "");
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
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
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
        int requestedRenderDistanceChunks = mc.gameSettings.renderDistanceChunks;
        Integer activeRenderDistanceChunks = activeVanillaViewFrustumRenderDistanceChunks > 0
                ? activeVanillaViewFrustumRenderDistanceChunks
                : null;
        int expectedRenderDistanceChunks = vanillaTerrainRenderDistanceChunks(
                world,
                activeRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (canReuseActiveVanillaTerrainRenderer(renderGlobal, currentRenderGlobal, world, expectedRenderDistanceChunks)) {
            updateVanillaViewFrustumChunkPositions(renderGlobal.ausm$viewFrustum(), mc.getRenderViewEntity());
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:reuse-active", world,
                    "renderDistance=" + expectedRenderDistanceChunks + ", force=" + force);
            return;
        }

        boolean rendererStateChanged = syncRenderGlobalWorld(currentRenderGlobal, world);
        ViewFrustum activeViewFrustum = renderGlobal.ausm$viewFrustum();
        pruneBetterPortalsVanillaViewFrustumCache(currentRenderGlobal, world);

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
        int renderDistanceChunks = vanillaTerrainRenderDistanceChunks(
                world,
                cachedRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (viewFrustum != null && cachedRenderDistanceChunks != null && cachedRenderDistanceChunks != renderDistanceChunks) {
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(viewFrustum);
            clearQueuedUpdatesForViewFrustums(renderGlobal, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
            viewFrustum.deleteGlResources();
            if (viewFrustum == activeViewFrustum) {
                activeViewFrustum = null;
            }
            rendererViewFrustums.remove(world);
            rendererViewFrustumDistances.remove(world);
            viewFrustum = null;
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal && activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
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
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
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
        if (shouldUseBetterPortalsPortalRenderDistance(world)) {
            return Math.min(requestedRenderDistanceChunks, BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP);
        }
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

    private boolean shouldUseBetterPortalsPortalRenderDistance(World world) {
        Minecraft mc = Minecraft.getMinecraft();
        return BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingRenderPass()
                && mc != null
                && mc.world != null
                && world != null
                && world != mc.world;
    }

    private boolean canReuseActiveVanillaTerrainRenderer(RenderGlobalAccessor renderGlobal,
                                                         RenderGlobal currentRenderGlobal,
                                                         World world,
                                                         int renderDistanceChunks) {
        if (renderGlobal == null
                || currentRenderGlobal == null
                || world == null
                || renderDistanceChunks <= 0
                || activeVanillaViewFrustumRenderGlobal != currentRenderGlobal
                || activeVanillaViewFrustumWorld != world
                || activeVanillaViewFrustumRenderDistanceChunks != renderDistanceChunks) {
            return false;
        }
        if (countCachedVanillaViewFrustums() > 2) {
            return false;
        }
        return renderGlobal.ausm$world() == world
                && renderGlobal.ausm$viewFrustum() != null
                && renderGlobal.ausm$renderDispatcher() != null
                && renderGlobal.ausm$renderChunkFactory() != null
                && renderGlobal.ausm$renderContainer() != null;
    }

    private boolean shouldUseStableMainWorldRenderDistance(World world) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && mc != null
                && mc.world != null
                && renderPassWorld == mc.world
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

        pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
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
                activeVanillaViewFrustumRenderDistanceChunks = -1;
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
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(previous);
            clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(previous);
            previous.deleteGlResources();
        }

        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
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

        pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
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
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;

        int scheduledChunks = scheduleAllFreshViewFrustumChunks(accessor, freshViewFrustum, world);
        forceUpdateVanillaViewFrustumChunkPositions(freshViewFrustum, mc.getRenderViewEntity(), world, stagePrefix);
        accessor.ausm$setDisplayListEntitiesDirty(true);

        clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            if (removedViewFrustum != null && removedViewFrustum != freshViewFrustum) {
                vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
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

    private void clearQueuedUpdatesForViewFrustums(RenderGlobalAccessor renderGlobal, Set<ViewFrustum> viewFrustums) {
        if (renderGlobal == null || viewFrustums == null || viewFrustums.isEmpty()) {
            return;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return;
        }

        Set<RenderChunk> removedChunks = new HashSet<>();
        for (ViewFrustum viewFrustum : viewFrustums) {
            if (viewFrustum == null || viewFrustum.renderChunks == null) {
                continue;
            }
            for (RenderChunk renderChunk : viewFrustum.renderChunks) {
                if (renderChunk != null) {
                    removedChunks.add(renderChunk);
                }
            }
        }
        if (!removedChunks.isEmpty()) {
            chunksToUpdate.removeAll(removedChunks);
        }
    }

    private void forceUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity, World world, String stagePrefix) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        try {
            viewFrustum.updateChunkPositions(viewEntity.posX, viewEntity.posZ);
            rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
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
        // Diagnostic disabled.
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

        if (!shouldUpdateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity)) {
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
            rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
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

    private boolean shouldUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        Long previous = vanillaViewFrustumChunkPositionKeys.get(viewFrustum);
        if (previous == null) {
            return true;
        }
        return previous.longValue() != vanillaViewFrustumChunkPositionKey(viewEntity);
    }

    private void rememberVanillaViewFrustumChunkPosition(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }
        vanillaViewFrustumChunkPositionKeys.put(viewFrustum, vanillaViewFrustumChunkPositionKey(viewEntity));
    }

    private long vanillaViewFrustumChunkPositionKey(Entity viewEntity) {
        int chunkX = (int) Math.floor(viewEntity.posX) >> 4;
        int chunkZ = (int) Math.floor(viewEntity.posZ) >> 4;
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private void deleteCachedVanillaTerrainRenderers() {
        if (vanillaViewFrustums.isEmpty()) {
            vanillaViewFrustumRenderDistances.clear();
            vanillaViewFrustumChunkPositionKeys.clear();
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            lastStableMainWorldVanillaRenderDistanceChunks = -1;
            return;
        }

        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor && rendererViewFrustums != null) {
                clearQueuedUpdatesForViewFrustums(accessor, new HashSet<>(rendererViewFrustums.values()));
            }
            if (rendererViewFrustums == null) {
                continue;
            }
            uniqueViewFrustums.addAll(rendererViewFrustums.values());
        }
        for (ViewFrustum viewFrustum : uniqueViewFrustums) {
            if (viewFrustum != null) {
                vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
                viewFrustum.deleteGlResources();
            }
        }
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        lastStableMainWorldVanillaRenderDistanceChunks = -1;
    }

    private void clearCachedVanillaTerrainRendererReferences() {
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        clearShaderlessBloomMetadata();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
    }

    private void deleteCachedVanillaTerrainRenderer(World world) {
        if (world == null || vanillaViewFrustums.isEmpty()) {
            if (world == null) {
                vanillaViewFrustumRenderDistances.clear();
            }
            if (activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            return;
        }

        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null) {
                continue;
            }
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor) {
                    Set<ViewFrustum> removedViewFrustums = new HashSet<>();
                    removedViewFrustums.add(removed);
                    clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
                }
                vanillaViewFrustumChunkPositionKeys.remove(removed);
                removed.deleteGlResources();
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }
        if (activeVanillaViewFrustumWorld == world) {
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
        }
    }

    private void pruneBetterPortalsVanillaViewFrustumCache(RenderGlobal currentRenderGlobal, World primaryWorld) {
        if (!BetterPortalsCompat.isInstalled()
                || currentRenderGlobal == null
                || primaryWorld == null
                || vanillaViewFrustums.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        World mainWorld = mc != null ? mc.world : null;
        if (countCachedVanillaViewFrustums() <= 2) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }

        ViewFrustum activeViewFrustum = currentRenderGlobal instanceof RenderGlobalAccessor accessor
                ? accessor.ausm$viewFrustum()
                : null;
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();

        Iterator<Map.Entry<RenderGlobal, Map<World, ViewFrustum>>> rendererIterator =
                vanillaViewFrustums.entrySet().iterator();
        while (rendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry = rendererIterator.next();
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null || rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
                continue;
            }

            Iterator<Map.Entry<World, ViewFrustum>> worldIterator = rendererViewFrustums.entrySet().iterator();
            while (worldIterator.hasNext()) {
                Map.Entry<World, ViewFrustum> worldEntry = worldIterator.next();
                World cachedWorld = worldEntry.getKey();
                if (cachedWorld == primaryWorld
                        || cachedWorld == mainWorld
                        || cachedWorld == activeVanillaViewFrustumWorld) {
                    continue;
                }

                ViewFrustum removed = worldEntry.getValue();
                if (removed != null && removed != activeViewFrustum) {
                    removedViewFrustums.add(removed);
                }
                worldIterator.remove();
            }

            if (rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
            }
        }

        Iterator<Map.Entry<RenderGlobal, Map<World, Integer>>> distanceRendererIterator =
                vanillaViewFrustumRenderDistances.entrySet().iterator();
        while (distanceRendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, Integer>> rendererEntry = distanceRendererIterator.next();
            Map<World, Integer> rendererViewFrustumDistances = rendererEntry.getValue();
            if (rendererViewFrustumDistances == null || rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
                continue;
            }

            rendererViewFrustumDistances.keySet().removeIf(cachedWorld ->
                    cachedWorld != primaryWorld
                            && cachedWorld != mainWorld
                            && cachedWorld != activeVanillaViewFrustumWorld
            );
            if (rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
            }
        }

        if (currentRenderGlobal instanceof RenderGlobalAccessor accessor) {
            clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        }
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
            removedViewFrustum.deleteGlResources();
        }

        if (!removedViewFrustums.isEmpty()) {
            if (activeVanillaViewFrustumWorld != primaryWorld
                    && activeVanillaViewFrustumRenderGlobal != currentRenderGlobal) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            logTerrainDiagnostic("prune-vanilla-frustums", primaryWorld,
                    "removed=" + removedViewFrustums.size()
                            + ", mainWorld=" + safeDimensionId(mainWorld)
                            + ", primaryWorld=" + safeDimensionId(primaryWorld));
        }
    }

    private int countCachedVanillaViewFrustums() {
        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            if (rendererViewFrustums != null) {
                uniqueViewFrustums.addAll(rendererViewFrustums.values());
            }
        }
        return uniqueViewFrustums.size();
    }

    private void refreshBetterPortalsMainViewTerrain(Minecraft mc, String reason) {
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
            adoptMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, reason);
            resetCameraFrustumSyncState();
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
        activeVanillaViewFrustumRenderDistanceChunks = mc.gameSettings.renderDistanceChunks;
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
        if (shouldSkipStationaryShadowMap(world, viewEntity, partialTicks)) {
            lastShadowFrameId = pipelineFrameId;
            return;
        }

        runPreparePassesBeforeShadowIfRequested();
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
            if (!useNothiriumShadowBridge) {
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
            int blockEntityCount = -1;
            if (renderShadowTerrain) {
                solidCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
                cutoutMippedCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
                cutoutCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            }
            if (shaderProperties.renderSettings().shadowEntities()
                    || shaderProperties.renderSettings().shadowPlayer()) {
                beginPhase(WorldRenderingPhase.ENTITIES);
                // RenderLib replaces RenderGlobal.renderEntities with a queued renderer
                // that is only prepared during the normal world pass. The shadow pass
                // has its own camera, so render entities directly here.
                renderShadowEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            if (shaderProperties.renderSettings().shadowBlockEntities()
                    || shaderProperties.renderSettings().shadowLightBlockEntities()) {
                beginPhase(WorldRenderingPhase.BLOCK_ENTITIES);
                blockEntityCount = renderShadowBlockEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            shadowFramebuffer.copyDepthToSnapshot();
            if (renderShadowTerrain && shaderProperties.renderSettings().shadowTranslucent()) {
                translucentCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            }
            injectMappedTileEntityVoxels(mc);
            applyShaderImageTextureBarrier();
            if (solidCount > 0 || cutoutMippedCount > 0 || cutoutCount > 0 || translucentCount > 0 || blockEntityCount > 0) {
                shadowMapPopulated = true;
            }
            shadowFramebuffer.generateShadowColorMipmaps();
            logShadowHealth(solidCount, cutoutMippedCount, cutoutCount, translucentCount);
            runComputePrograms(shadowComputePrograms, RenderPass.SHADOW);
            runFullscreenPasses(ProgramArrayId.SHADOWCOMP);
            rememberShadowMapRender(world, viewEntity, partialTicks);
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

    private boolean shouldSkipStationaryShadowMap(World world, Entity viewEntity, float partialTicks) {
        if (!shadowMapPopulated || shaderProperties == null
                || shaderProperties.renderSettings().shadowEntities()
                || shaderProperties.renderSettings().shadowPlayer()) {
            return false;
        }
        int dimensionId = safeDimensionId(world);
        long worldTime = world.getTotalWorldTime();
        if (dimensionId != lastShadowRenderDimensionId || worldTime != lastShadowRenderWorldTime) {
            return false;
        }
        double x = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double y = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double z = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        double dx = x - lastShadowRenderX;
        double dy = y - lastShadowRenderY;
        double dz = z - lastShadowRenderZ;
        return dx * dx + dy * dy + dz * dz < 0.0001D;
    }

    private void rememberShadowMapRender(World world, Entity viewEntity, float partialTicks) {
        lastShadowRenderDimensionId = safeDimensionId(world);
        lastShadowRenderWorldTime = world.getTotalWorldTime();
        lastShadowRenderX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        lastShadowRenderY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        lastShadowRenderZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
    }

    private void resetShadowRenderCache() {
        lastShadowRenderDimensionId = Integer.MIN_VALUE;
        lastShadowRenderWorldTime = Long.MIN_VALUE;
        lastShadowRenderX = Double.NaN;
        lastShadowRenderY = Double.NaN;
        lastShadowRenderZ = Double.NaN;
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

            // Generic shader block-id lights are handled by the shaderpack shadow voxelizer.
            // Keep this CPU path restricted to ProjectRed tile entities to avoid global tint leaks.
        }

        if (ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION) {
            injected += injectRecordedSyntheticLightVoxels(
                    world,
                    dimensions,
                    cameraFloorX,
                    cameraFloorY,
                    cameraFloorZ,
                    writtenVoxels,
                    MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
            );

            injected += injectVoxelizedLightBlockVoxels(
                    world,
                    dimensions,
                    cameraFloorX,
                    cameraFloorY,
                    cameraFloorZ,
                    writtenVoxels,
                    MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
            );
        }

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

    private int injectVoxelizedLightBlockVoxels(World world, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                                Set<Long> writtenVoxels, int remainingBudget) {
        if (remainingBudget <= 0 || !shaderProperties.renderSettings().voxelizeLightBlocks()) {
            return 0;
        }
        if (world == null || dimensions == null || dimensions.length < 3) {
            return 0;
        }
        if (cpuLightBlockScanWorld != world) {
            cpuLightBlockScanWorld = world;
            cpuLightBlockScanCursor = 0;
        }

        int scanWidth = Math.max(1, Math.min(dimensions[0], MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH));
        int scanHeight = Math.max(1, Math.min(dimensions[1], MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT));
        int scanDepth = Math.max(1, Math.min(dimensions[2], MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH));
        int scanVolume = scanWidth * scanHeight * scanDepth;
        int scanBudget = Math.min(MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME, scanVolume);
        int injected = 0;

        for (int scan = 0; scan < scanBudget && injected < remainingBudget; scan++) {
            if (cpuLightBlockScanCursor >= scanVolume) {
                cpuLightBlockScanCursor = 0;
            }
            int cursor = cpuLightBlockScanCursor++;
            int localX = cursor % scanWidth;
            int localY = (cursor / scanWidth) % scanHeight;
            int localZ = cursor / (scanWidth * scanHeight);
            BlockPos pos = new BlockPos(
                    cameraFloorX + localX - scanWidth / 2,
                    cameraFloorY + localY - scanHeight / 2,
                    cameraFloorZ + localZ - scanDepth / 2
            );
            if (pos.getY() < 0 || pos.getY() > 255 || !world.isBlockLoaded(pos, false)) {
                continue;
            }

            IBlockState state;
            try {
                state = world.getBlockState(pos);
            } catch (RuntimeException ignored) {
                continue;
            }
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                continue;
            }
            if (injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
                auditSyntheticLight("voxelize_light_blocks", pos, lightInfo, "injected");
            }
        }

        return injected;
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
        if ("aether_legacy".equals(name.getNamespace())
                && "aether_portal".equals(name.getPath())) {
            return localActVoxelId(10914); // Aether sky blue.
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
        if (!shouldCullShadowTerrain()) {
            return -1.0D;
        }
        if (shaderProperties.renderSettings().shadowCullingReversed()) {
            return Math.max(32.0D, Math.max(shadowMapDistance, voxelDistance));
        }
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

        World world = renderWorld(Minecraft.getMinecraft());
        if (world != null && useEndFlashShadowLight(world)) {
            GL11.glRotatef(90.0F - endFlashPitchDegrees, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(endFlashYawDegrees, 0.0F, 1.0F, 0.0F);
        } else {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
            float celestialAngle = world != null ? world.getCelestialAngle(partialTicks) : 0.0F;
            float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
            float angle = celestialAngle * -360.0F;
            if (sunAngle <= 0.5F) {
                GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
            } else {
                GL11.glRotatef(angle + 180.0F, 0.0F, 0.0F, 1.0F);
            }
            GL11.glRotatef(sunPathRotation, 1.0F, 0.0F, 0.0F);
        }

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
        return ALWAYS_VISIBLE_CAMERA;
    }

    private ICamera createCeleritasShadowCamera(Entity viewEntity, float partialTicks) {
        try {
            if (!resolveCeleritasShadowCameraReflection()) {
                return null;
            }

            double[] position = {
                    interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks),
                    interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks),
                    interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks)
            };
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("sodium$createViewport".equals(name)) {
                    Object cameraPosition = celeritasVectorConstructor.newInstance(position[0], position[1], position[2]);
                    return celeritasViewportConstructor.newInstance(celeritasAlwaysVisibleFrustum, cameraPosition);
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
                    PipelineContext.class.getClassLoader(),
                    new Class<?>[]{ICamera.class, celeritasViewportProviderClass},
                    handler
            );
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            if (!celeritasShadowCameraWarningLogged) {
                celeritasShadowCameraWarningLogged = true;
                MainMod.LOGGER.warn("[Pipeline] Failed to create Celeritas-compatible shadow camera; falling back to vanilla camera", e);
            }
            return null;
        }
    }

    private boolean resolveCeleritasShadowCameraReflection() throws ReflectiveOperationException {
        if (celeritasShadowCameraResolved) {
            return celeritasViewportProviderClass != null;
        }
        celeritasShadowCameraResolved = true;

        ClassLoader loader = PipelineContext.class.getClassLoader();
        try {
            celeritasViewportProviderClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.ViewportProvider", false, loader);
            Class<?> viewportClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.Viewport", false, loader);
            Class<?> frustumClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum", false, loader);
            Class<?> vector3dClass = Class.forName(
                    "org.embeddedt.embeddium.impl.shadow.joml.Vector3d", false, loader);
            celeritasViewportConstructor = viewportClass.getConstructor(frustumClass, vector3dClass);
            celeritasVectorConstructor = vector3dClass.getConstructor(double.class, double.class, double.class);
            celeritasAlwaysVisibleFrustum = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{frustumClass},
                    (proxy, method, args) -> boolean.class.equals(method.getReturnType()) ? Boolean.TRUE : null
            );
            return true;
        } catch (ClassNotFoundException e) {
            celeritasViewportProviderClass = null;
            celeritasViewportConstructor = null;
            celeritasVectorConstructor = null;
            celeritasAlwaysVisibleFrustum = null;
            return false;
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

    private int renderShadowBlockEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !shaderProperties.renderSettings().shadowLightBlockEntities()) {
            return 0;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null) {
            return 0;
        }

        TileEntityRendererDispatcher dispatcher = TileEntityRendererDispatcher.instance;
        if (dispatcher == null) {
            return 0;
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        dispatcher.prepare(
                world,
                mc.getTextureManager(),
                mc.fontRenderer,
                viewEntity,
                mc.objectMouseOver,
                partialTicks
        );
        mc.entityRenderer.enableLightmap();
        RenderHelper.enableStandardItemLighting();
        configureShadowTerrainRenderState();

        int rendered = 0;
        for (TileEntity tileEntity : cpuLightTileEntitySnapshot(world)) {
            if (!shouldRenderBlockEntityInShadowMap(world, tileEntity, shadowCamera, cameraX, cameraY, cameraZ, maxDistanceSquared)) {
                continue;
            }

            BlockPos pos = tileEntity.getPos();
            dispatcher.render(
                    tileEntity,
                    pos.getX() - cameraX,
                    pos.getY() - cameraY,
                    pos.getZ() - cameraZ,
                    partialTicks,
                    -1,
                    1.0F
            );
            rendered++;
        }
        return rendered;
    }

    private boolean shouldRenderBlockEntityInShadowMap(World world, TileEntity tileEntity, ICamera shadowCamera,
                                                       double cameraX, double cameraY, double cameraZ,
                                                       double maxDistanceSquared) {
        if (world == null || tileEntity == null || tileEntity.isInvalid()) {
            return false;
        }

        BlockPos pos = tileEntity.getPos();
        if (pos == null || !world.isBlockLoaded(pos, false)) {
            return false;
        }
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !isLightEmittingBlockEntity(world, tileEntity, pos)) {
            return false;
        }

        double dx = pos.getX() + 0.5D - cameraX;
        double dy = pos.getY() + 0.5D - cameraY;
        double dz = pos.getZ() + 0.5D - cameraZ;
        if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
            return false;
        }

        AxisAlignedBB box = tileEntity.getRenderBoundingBox();
        return box == null || shadowCamera.isBoundingBoxInFrustum(box);
    }

    private static boolean isLightEmittingBlockEntity(World world, TileEntity tileEntity, BlockPos pos) {
        try {
            IBlockState state = world.getBlockState(pos);
            return state != null && state.getLightValue(world, pos) > 0;
        } catch (RuntimeException ignored) {
            return false;
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

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        restoreVanillaWorldTextureBindings();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        resetPortalMaskState();
    }

    private int currentPipelineWorldFramebufferId() {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        return framebuffer != null ? framebuffer.getFramebufferId() : 0;
    }

    public boolean shouldUseDistantHorizonsFramebufferOverride() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return true;
        }
        return ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().world != null;
    }

    private boolean shouldCompositeDistantHorizonsFramebuffer() {
        return isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().world != null;
    }

    public boolean shouldSuppressDistantHorizonsMinecraftApply() {
        return shouldUseDistantHorizonsFramebufferOverride() || shouldProtectDistantHorizonsNativeApply();
    }

    private boolean shouldProtectDistantHorizonsNativeApply() {
        return MainMod.getShaderPackManager() != null
                && MainMod.getShaderPackManager().shouldProtectDistantHorizonsNativeApply()
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().world != null;
    }

    public void logDistantHorizonsApiCallback(String method, String detail) {
        logDistantHorizonsDiagnostic("api-" + method, detail + ", " + distantHorizonsProbeState(null));
    }

    public void logDistantHorizonsHook(String stage, Object renderParam) {
        updateDistantHorizonsRenderPass(renderParam);
        logDistantHorizonsDiagnostic(stage, distantHorizonsProbeState(renderParam));
    }

    public void renderDistantHorizonsLods(float partialTicks) {
    }

    public void resetDistantHorizonsDiagnostics(String reason) {
        distantHorizonsDiagnosticLogs = 0;
        logDistantHorizonsDiagnostic("probe-reset", reason + ", " + distantHorizonsProbeState(null));
    }

    public boolean applyDistantHorizonsToPipeline(Object renderParam) {
        if (shouldUseDistantHorizonsFramebufferOverride() || !shouldCompositeDistantHorizonsFramebuffer()) {
            logDistantHorizonsDiagnostic("native-apply-bypass", distantHorizonsProbeState(renderParam));
            return false;
        }

        updateDistantHorizonsRenderPass(renderParam);
        int colorTexture = activeDistantHorizonsTextureId("getActiveColorTextureId");
        int depthTexture = activeDistantHorizonsTextureId("getActiveDepthTextureId");
        if (colorTexture <= 0 || depthTexture <= 0) {
            logDistantHorizonsDiagnostic("native-apply-skip", "color=" + colorTexture + ", depth=" + depthTexture);
            return false;
        }

        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = colorTexture;
        distantHorizonsDepthTextureId = depthTexture;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        return true;
    }

    private void updateDistantHorizonsRenderPass(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            currentDistantHorizonsPass = renderPass != null && "TRANSPARENT".equals(renderPass.toString())
                    ? RenderPass.DH_WATER
                    : RenderPass.DH_TERRAIN;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private int activeDistantHorizonsTextureId(String getterName) {
        try {
            Class<?> metaRendererClass = Class.forName("com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer");
            Object instance = metaRendererClass.getField("INSTANCE").get(null);
            Object value = metaRendererClass.getMethod(getterName).invoke(instance);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public int distantHorizonsFramebufferId() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return distantHorizonsPresentationTarget.framebufferObject;
        }
        return shouldUseDistantHorizonsFramebufferOverride() ? currentPipelineWorldFramebufferId() : 0;
    }

    public int distantHorizonsFramebufferStatus() {
        if (!shouldUseDistantHorizonsFramebufferOverride()) {
            return GL30.GL_FRAMEBUFFER_COMPLETE;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            bindDistantHorizonsFramebuffer();
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    public void bindDistantHorizonsFramebuffer() {
        if (!shouldUseDistantHorizonsFramebufferOverride()) {
            return;
        }

        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            Framebuffer target = distantHorizonsPresentationTarget;
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target.framebufferObject);
            GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager.viewport(0, 0, framebufferWidth(target, Minecraft.getMinecraft()), framebufferHeight(target, Minecraft.getMinecraft()));
            GL11.glColorMask(true, true, true, true);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GlStateManager.disableBlend();
            GlStateManager.disableCull();
            logDistantHorizonsDiagnostic("bind-presentation", distantHorizonsProbeState(null));
            return;
        }

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        restoreVanillaWorldTextureBindings();
        GL11.glColorMask(true, true, true, true);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        distantHorizonsFramebufferPendingComposite = false;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        logDistantHorizonsDiagnostic("bind-world", distantHorizonsProbeState(null));
    }

    public void compositeDistantHorizonsFramebuffer() {
        compositeDistantHorizonsFramebuffer(null);
    }

    private void compositeDistantHorizonsFramebuffer(Framebuffer target) {
        if (!distantHorizonsFramebufferPendingComposite
                || !shouldCompositeDistantHorizonsFramebuffer()
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDepthTextureId == 0
                || !ensureDistantHorizonsCompositeProgram()) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            String sample = sampleDistantHorizonsColorTexture();
            if (target != null) {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target.framebufferObject);
                GlStateManager.viewport(0, 0, framebufferWidth(target, Minecraft.getMinecraft()), framebufferHeight(target, Minecraft.getMinecraft()));
            } else {
                pingPongManager.bindForGbuffers(fallbackColorAttachment());
                GlStateManager.viewport(0, 0, pingPongManager.width(), pingPongManager.height());
            }
            GL11.glDrawBuffer(target != null && target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(target != null && target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GlStateManager.enableDepth();
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(false);
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );
            OpenGlHelper.glUseProgram(distantHorizonsCompositeProgramId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(distantHorizonsColorTextureId);
            if (distantHorizonsCompositeTextureUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeTextureUniform, 0);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GlStateManager.bindTexture(distantHorizonsDepthTextureId);
            if (distantHorizonsCompositeDepthUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeDepthUniform, 1);
            }
            drawDistantHorizonsCompositeQuad();
            String targetSample = sampleDistantHorizonsCompositeTarget(target);
            distantHorizonsFramebufferPendingComposite = false;
            logDistantHorizonsDiagnostic("composite", "pass=" + currentDistantHorizonsPass
                    + ", texture=" + distantHorizonsColorTextureId
                    + ", sample=" + sample
                    + ", targetSample=" + targetSample);
        } finally {
            OpenGlHelper.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            if (previousDepthTest) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            GL11.glDepthFunc(previousDepthFunc);
            GlStateManager.depthMask(previousDepthMask);
            if (previousBlend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (previousAlpha) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (previousCull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    private void drawDistantHorizonsCompositeQuad() {
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        for (int attribute = 0; attribute < 16; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glEnd();
    }

    private boolean ensureDistantHorizonsFramebuffer() {
        int width = Math.max(1, pingPongManager.width());
        int height = Math.max(1, pingPongManager.height());
        if (distantHorizonsFramebufferId != 0
                && distantHorizonsFramebufferWidth == width
                && distantHorizonsFramebufferHeight == height) {
            return true;
        }

        distantHorizonsFramebufferWidth = width;
        distantHorizonsFramebufferHeight = height;
        distantHorizonsFramebufferId = OpenGlHelper.glGenFramebuffers();
        distantHorizonsColorTextureId = GL11.glGenTextures();
        distantHorizonsDepthTextureId = GL11.glGenTextures();
        distantHorizonsTexturesOwned = true;

        GlStateManager.bindTexture(distantHorizonsColorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);

        GlStateManager.bindTexture(distantHorizonsDepthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, distantHorizonsFramebufferId);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, distantHorizonsDepthTextureId, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        if (!complete) {
            MainMod.LOGGER.warn("[DistantHorizons] AUSM intermediate framebuffer is incomplete.");
            }
        TextureBinder.restoreDefaultTextureUnit();
        return complete;
    }

    private void clearDistantHorizonsFramebuffer() {
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager.clearDepth(1.0D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private String sampleDistantHorizonsColorTexture() {
        if (distantHorizonsFramebufferWidth <= 0
                || distantHorizonsFramebufferHeight <= 0
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            int readFramebuffer = distantHorizonsFramebufferId;
            if (readFramebuffer == 0) {
                readFramebuffer = ensureDistantHorizonsTextureReadbackFramebuffer();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                OpenGlHelper.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            }
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return "read-fbo-incomplete:" + status;
            }
            int[][] points = new int[][]{
                    {distantHorizonsFramebufferWidth / 2, distantHorizonsFramebufferHeight / 2},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight / 4)},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    private int ensureDistantHorizonsTextureReadbackFramebuffer() {
        if (distantHorizonsTextureReadbackFramebufferId == 0) {
            distantHorizonsTextureReadbackFramebufferId = OpenGlHelper.glGenFramebuffers();
        }
        return distantHorizonsTextureReadbackFramebufferId;
    }

    private String sampleDistantHorizonsCompositeTarget(Framebuffer target) {
        if (distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        Minecraft mc = Minecraft.getMinecraft();
        int framebuffer = target != null ? target.framebufferObject : GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readBuffer = target != null && target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
        int width = target != null ? framebufferWidth(target, mc) : pingPongManager.width();
        int height = target != null ? framebufferHeight(target, mc) : pingPongManager.height();
        if (width <= 0 || height <= 0) {
            return "invalid-size";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            int[][] points = new int[][]{
                    {width / 2, height / 2},
                    {width / 2, Math.max(0, height / 4)},
                    {width / 2, Math.max(0, height * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    private boolean clearDistantHorizonsFramebufferIfNeeded() {
        long frameKey = currentDistantHorizonsFrameKey();
        if (distantHorizonsFramebufferClearFrame == frameKey) {
            return false;
        }

        clearDistantHorizonsFramebuffer();
        distantHorizonsFramebufferClearFrame = frameKey;
        return true;
    }

    private long currentDistantHorizonsFrameKey() {
        if (clientRenderFrameNanos != Long.MIN_VALUE) {
            return clientRenderFrameNanos;
        }
        return pipelineFrameId;
    }

    private String distantHorizonsProbeState(Object renderParam) {
        String renderParamSummary = distantHorizonsRenderParamSummary(renderParam);
        return "pass=" + currentDistantHorizonsPass
                + ", override=" + shouldUseDistantHorizonsFramebufferOverride()
                + ", suppressApply=" + shouldSuppressDistantHorizonsMinecraftApply()
                + ", active=" + isPipelineActive
                + ", worldFrame=" + worldFrameActive
                + ", shadow=" + renderingShadowMap
                + ", gui=" + renderingGuiScreen()
                + ", pingpong=" + pingPongManager.isInitialized()
                + ", ausmFbo=" + currentPipelineWorldFramebufferId()
                + ", fallbackAttachment=" + fallbackColorAttachment()
                + ", size=" + pingPongManager.width() + "x" + pingPongManager.height()
                + ", storedColorTex=" + distantHorizonsColorTextureId
                + ", storedDepthTex=" + distantHorizonsDepthTextureId
                + ", activeColorTex=" + activeDistantHorizonsTextureId("getActiveColorTextureId")
                + ", activeDepthTex=" + activeDistantHorizonsTextureId("getActiveDepthTextureId")
                + ", pendingComposite=" + distantHorizonsFramebufferPendingComposite
                + ", frame=" + currentDistantHorizonsFrameKey()
                + ", renderParam=" + renderParamSummary
                + ", gl={" + distantHorizonsGlStateSummary() + "}";
    }

    private String distantHorizonsRenderParamSummary(Object renderParam) {
        if (renderParam == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(renderParam.getClass().getName());
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            builder.append(":renderPass=").append(renderPass);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Object worldYOffset = renderParam.getClass().getField("worldYOffset").get(renderParam);
            builder.append(":worldYOffset=").append(worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return builder.toString();
    }

    private String distantHorizonsGlStateSummary() {
        try {
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            int viewportX = viewportBuffer.get(0);
            int viewportY = viewportBuffer.get(1);
            int viewportWidth = viewportBuffer.get(2);
            int viewportHeight = viewportBuffer.get(3);
            return "drawFbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", readFbo=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                    + ", readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", vao=" + GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                    + ", arrayBuffer=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    + ", depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + ", depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + ", viewport=" + viewportX + "/" + viewportY + "/" + viewportWidth + "/" + viewportHeight;
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private void logDistantHorizonsDiagnostic(String stage, String detail) {
        // Diagnostic disabled.
    }

    public void prepareExternalWorldOverlayRender() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (worldFrameActive) {
            pingPongManager.bindForGbuffers(fallbackColorAttachment());
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

        if (shaderProperties.renderSettings().prepareBeforeShadow()) {
            runPreparePassesBeforeShadowIfRequested();
            return;
        }

        runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    private void runPreparePassesBeforeShadowIfRequested() {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || !shaderProperties.renderSettings().prepareBeforeShadow()
                || preparePassesRenderedBeforeShadowThisFrame) {
            return;
        }

        preparePassesRenderedBeforeShadowThisFrame = true;
        runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    public void snapshotOpaqueTerrainDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        logColorBufferProbe("after-opaque-terrain");
        if (!ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK) {
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
        copyPreTranslucentDepth();
    }

    private void logColorBufferProbe(String stage) {
        // Probe disabled.
    }

    private void logDistantHorizonsColorProbe(String stage) {
        // Probe disabled.
    }

    private void logDistantHorizonsPassColorProbe(String stage, RenderPass pass) {
        // Probe disabled.
    }

    private static boolean isDistantHorizonsProbeMarker(float[] aux3) {
        return aux3 != null
                && aux3.length >= 3
                && aux3[2] > 0.5f
                && aux3[0] < 0.2f
                && aux3[1] < 0.2f;
    }

    private static String formatProbeColor(float[] color) {
        if (color == null || color.length < 4) {
            return "(nan,nan,nan,nan)";
        }
        return "("
                + formatProbeFloat(color[0]) + ','
                + formatProbeFloat(color[1]) + ','
                + formatProbeFloat(color[2]) + ','
                + formatProbeFloat(color[3]) + ')';
    }

    private static String formatProbeFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public int renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        if (renderGlobal == null) {
            logWorldLayerDiag("skip-null-render-global", layer, pass, 0, viewEntity);
            return 0;
        }
        if (shouldSkipAllMainGbufferRendering()) {
            recordTerrainLayerCount(layer, 0);
            logWorldLayerDiag("skip-all-rendering", layer, pass, 0, viewEntity);
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
            boolean forceVanillaAfterEmptyNothirium = nothiriumCount == 0;
            if (nothiriumCount > 0) {
                markNothiriumPipelineTranslucentBridge(layer);
                recordTerrainLayerCount(layer, nothiriumCount);
                recordShaderlessTerrainLayerCount(layer, nothiriumCount);
                logWorldLayerDiag("nothirium", layer, pass, nothiriumCount, viewEntity);
                return nothiriumCount;
            }

            boolean forceVanillaFallback = isPipelineActive
                    && (hardwareSafeVanillaTerrain || forceVanillaAfterEmptyNothirium || NothiriumBypass.shouldBypass());
            if (forceVanillaFallback) {
                ensureVanillaTerrainRenderer(renderWorld(Minecraft.getMinecraft()), true);
                rebindActiveTerrainPassForForcedVanillaFallback();
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
            recordShaderlessTerrainLayerCount(layer, count);
            logWorldLayerDiag(forceVanillaFallback
                    ? (forceVanillaAfterEmptyNothirium ? "vanilla-after-empty-nothirium" : "vanilla-forced-bypass")
                    : "vanilla", layer, pass, count, viewEntity);
            return count;
        } finally {
            if (prepareVanillaState) {
                finishShaderlessBlockLayerState(layer);
            }
        }
    }

    private void rebindActiveTerrainPassForForcedVanillaFallback() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase == WorldRenderingPhase.NONE || !phase.usesBlockAtlas()) {
            return;
        }
        bindPass(activePass);
    }

    private void logWorldLayerDiag(String stage, BlockRenderLayer layer, int pass, int count, Entity viewEntity) {
        // Diagnostic disabled.
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
        if (!shaderlessBloomExtractionActive) {
            OpenGlHelper.glUseProgram(0);
        }
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        if (!shaderlessBloomExtractionActive) {
            disablePipelineVertexAttributes();
        }
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
        restoreShaderlessTerrainClientTextureArrays();
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

    private void beginShaderlessTerrainLightmapCoords() {
        if (isPipelineActive || shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        shaderlessTerrainPreviousLightmapX = OpenGlHelper.lastBrightnessX;
        shaderlessTerrainPreviousLightmapY = OpenGlHelper.lastBrightnessY;
        shaderlessTerrainLightmapCoordsSaved = true;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 0.0F, 240.0F);
        probeShaderlessLightState("shaderless-terrain-lightmap-stable");
    }

    private void restoreShaderlessTerrainLightmapCoords() {
        if (!shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        OpenGlHelper.setLightmapTextureCoords(
                OpenGlHelper.lightmapTexUnit,
                shaderlessTerrainPreviousLightmapX,
                shaderlessTerrainPreviousLightmapY
        );
        shaderlessTerrainLightmapCoordsSaved = false;
        probeShaderlessLightState("shaderless-terrain-lightmap-restore");
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
            if (count > 0) {
                zeroOpaqueTerrainFrames = 0;
                zeroOpaqueTerrainRecoveryRequested = false;
            }
        }

        if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount == 0) {
            requestPersistentHistoryClear("zero-opaque-terrain");
            if (hasLoadedTerrainNearPlayer()) {
                zeroOpaqueTerrainFrames++;
                logHardwareTerrainFallback(
                        "zero-opaque-frame",
                        "frames=" + zeroOpaqueTerrainFrames
                                + ", activePass=" + activePass
                                + ", phase=" + getPhase()
                                + ", bypass=" + NothiriumBypass.shouldBypass()
                );
                if (zeroOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES) {
                    if (!zeroOpaqueTerrainRecoveryRequested) {
                        zeroOpaqueTerrainRecoveryRequested = true;
                        zeroOpaqueTerrainFrames = 0;
                        Minecraft mc = Minecraft.getMinecraft();
                        logHardwareTerrainFallback(
                                "zero-opaque-rebuild",
                                "rebuilding stale shader terrain before hardware fallback"
                        );
                        if (mc != null && mc.world != null && mc.renderGlobal != null) {
                            rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "zero-opaque-recovery");
                        }
                        rebuildTerrainRenderers(true, true);
                        scheduleWorldTerrainRefresh(true, true, 0);
                        return;
                    }
                    activateHardwareSafeVanillaTerrain("zero opaque shader terrain for " + zeroOpaqueTerrainFrames + " consecutive frames");
                }
            } else {
                logHardwareTerrainFallback("zero-opaque-no-loaded-terrain", "world=" + describeWorld(Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null));
            }
        }
    }

    private void resetShaderlessTerrainLayerCounts() {
        shaderlessTerrainSolidCount = -1;
        shaderlessTerrainCutoutMippedCount = -1;
        shaderlessTerrainCutoutCount = -1;
        shaderlessTerrainTranslucentCount = -1;
        shaderlessTerrainBloomCount = -1;
    }

    private void recordShaderlessTerrainLayerCount(BlockRenderLayer layer, int count) {
        if (isPipelineActive || layer == null || renderingShadowMap || renderingGuiScreen()) {
            return;
        }
        int safeCount = Math.max(0, count);
        if (layer == BlockRenderLayer.SOLID) {
            shaderlessTerrainSolidCount = safeCount;
        } else if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            shaderlessTerrainCutoutMippedCount = safeCount;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            shaderlessTerrainCutoutCount = safeCount;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            shaderlessTerrainTranslucentCount = safeCount;
        } else if (AusmBloomLayer.isBloomLayer(layer)) {
            shaderlessTerrainBloomCount = safeCount;
        }
    }

    private boolean shouldRenderShaderlessExtractionLayer(BlockRenderLayer layer) {
        return true;
    }

    private int shaderlessTerrainLayerCount(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return shaderlessTerrainSolidCount;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return shaderlessTerrainCutoutMippedCount;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return shaderlessTerrainCutoutCount;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return shaderlessTerrainTranslucentCount;
        }
        if (AusmBloomLayer.isBloomLayer(layer)) {
            return shaderlessTerrainBloomCount;
        }
        return -1;
    }

    private boolean hasLoadedTerrainNearPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.player == null) {
            return false;
        }

        int playerChunkX = ((int) Math.floor(mc.player.posX)) >> 4;
        int playerChunkZ = ((int) Math.floor(mc.player.posZ)) >> 4;
        if (mc.world.getChunkProvider() instanceof ChunkProviderClient provider) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk chunk = provider.getLoadedChunk(playerChunkX + dx, playerChunkZ + dz);
                    if (chunk != null && !chunk.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
        return mc.world.isBlockLoaded(new BlockPos(mc.player));
    }

    private void activateHardwareSafeVanillaTerrain(String reason) {
        if (hardwareSafeVanillaTerrain) {
            refreshHardwareSafeVanillaTerrain(reason, true);
            return;
        }
        hardwareSafeVanillaTerrain = true;
        hardwareSafeVanillaTerrainReason = reason;
        logHardwareTerrainFallback(
                "activate",
                reason + ", maxAttribs=" + safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)
                        + ", renderer='" + safeGetString(GL11.GL_RENDERER) + "'"
        );
        boolean formatChanged = updateNothiriumPipelineBlockFormatMode();
        refreshHardwareSafeVanillaTerrain(reason, true);
        if (formatChanged) {
            NothiriumBypass.recreateRenderer();
        }
        scheduleInactiveVanillaRecoveryFrame();
    }

    private void refreshHardwareSafeVanillaTerrainForCamera(Minecraft mc) {
        if (!isPipelineActive || !hardwareSafeVanillaTerrain || mc == null || mc.world == null) {
            lastHardwareSafeVanillaTerrainRefreshWorld = null;
            lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
            lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
            lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
            hardwareSafeVanillaTerrainRefreshCooldown = 0;
            return;
        }
        if (hardwareSafeVanillaTerrainRefreshCooldown > 0) {
            hardwareSafeVanillaTerrainRefreshCooldown--;
        }

        Entity viewEntity = mc.getRenderViewEntity();
        if (viewEntity == null) {
            return;
        }
        int chunkX = ((int) Math.floor(viewEntity.posX)) >> 4;
        int chunkZ = ((int) Math.floor(viewEntity.posZ)) >> 4;
        boolean loadedNearPlayer = hasLoadedTerrainNearPlayer();
        boolean changed = lastHardwareSafeVanillaTerrainRefreshWorld != mc.world
                || lastHardwareSafeVanillaTerrainRefreshChunkX != chunkX
                || lastHardwareSafeVanillaTerrainRefreshChunkZ != chunkZ
                || (loadedNearPlayer && !lastHardwareSafeVanillaTerrainLoadedNearPlayer);

        lastHardwareSafeVanillaTerrainRefreshWorld = mc.world;
        lastHardwareSafeVanillaTerrainRefreshChunkX = chunkX;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = chunkZ;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = loadedNearPlayer;

        if (changed && loadedNearPlayer) {
            refreshHardwareSafeVanillaTerrain("camera-frustum-change", false);
        }
    }

    private void refreshHardwareSafeVanillaTerrain(String reason, boolean hardReset) {
        if (!hardReset && hardwareSafeVanillaTerrainRefreshCooldown > 0) {
            return;
        }
        hardwareSafeVanillaTerrainRefreshCooldown = HARDWARE_TERRAIN_FALLBACK_REFRESH_COOLDOWN_FRAMES;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.world != null && mc.renderGlobal != null) {
            if (hardReset) {
                deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
                rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "hardware-safe-vanilla");
            }
            ensureVanillaTerrainRenderer(mc.world, true);
            mc.renderGlobal.loadRenderers();
        } else {
            ensureVanillaTerrainRenderer();
        }
        NothiriumBypass.markAllChanged();
        zeroOpaqueTerrainRecoveryRequested = false;
        scheduleWorldTerrainRefresh(true, true, 0);
        scheduleInactiveVanillaRecoveryFrame();
        logHardwareTerrainFallback(
                "refresh",
                reason + ", hardReset=" + hardReset
                        + ", cooldown=" + hardwareSafeVanillaTerrainRefreshCooldown
        );
    }

    private void logHardwareTerrainFallback(String stage, String detail) {
        if (hardwareTerrainFallbackLogs >= MAX_HARDWARE_TERRAIN_FALLBACK_LOGS) {
            return;
        }
        hardwareTerrainFallbackLogs++;
        MainMod.LOGGER.warn(
                "[AUSMHardwareTerrainFallback] call={} stage={} active={} safeVanilla={} reason='{}' detail={} frame={} worldFrame={} world={} gl={}",
                hardwareTerrainFallbackLogs,
                stage,
                isPipelineActive,
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                detail,
                pipelineFrameId,
                worldFrameActive,
                describeWorld(Minecraft.getMinecraft() != null ? Minecraft.getMinecraft().world : null),
                glStateSummary()
        );
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

    private void initializeNoiseTexture(ShaderPack pack, ShaderProperties properties) {
        ShaderCustomTextureBinding customNoise = packDirectives.noiseTexture();
        if (customNoise != null) {
            try {
                noiseTexture = ShaderTextureLoader.loadTexture(
                        pack,
                        customNoise.resourcePath(),
                        customNoise.blur(),
                        customNoise.clamp()
                );
                MainMod.LOGGER.debug("[ShaderTextures] Loaded custom noisetex from {}", customNoise.resourcePath());
                return;
            } catch (IOException e) {
                MainMod.LOGGER.warn("[ShaderTextures] Failed to load custom noisetex {}, using generated noise", customNoise.resourcePath(), e);
            }
        }

        int resolution = parseIntSetting(pack, properties, "noiseTextureResolution", packDirectives.noiseTextureResolution());
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

    private void compositeLatestDistantHorizonsTexture(Framebuffer target) {
        if (shouldUseDistantHorizonsFramebufferOverride()) {
            distantHorizonsFramebufferPendingComposite = false;
            distantHorizonsColorTextureId = 0;
            distantHorizonsDepthTextureId = 0;
            return;
        }
        if (distantHorizonsColorTextureId == 0 || distantHorizonsDepthTextureId == 0) {
            return;
        }
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        compositeDistantHorizonsFramebuffer(target);
    }

    public boolean shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase phase) {
        if (!isPipelineActive || phase == null) {
            return false;
        }

        String ordering = shaderProperties.renderSettings().particlesOrdering();
        if (ordering == null || ordering.isBlank() || "auto".equalsIgnoreCase(ordering)) {
            ordering = hasDeferredPrograms() ? "after" : "mixed";
        }

        return switch (ordering.trim().toLowerCase(Locale.ROOT)) {
            case "before" -> false;
            case "mixed" -> phase == WorldRenderingPhase.PARTICLES_TRANSLUCENT;
            case "after" -> true;
            default -> hasDeferredPrograms();
        };
    }

    private boolean hasDeferredPrograms() {
        for (RenderPass pass : RenderPass.DEFERRED_PASSES) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                return true;
            }
        }
        return !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty();
    }

    public void beginHand() {
        beginTranslucents();
        if (!isPipelineActive || !pingPongManager.isInitialized() || preHandDepthCopiedThisFrame) {
            return;
        }

        // depthtex2 excludes both translucent and hand geometry in OptiFine/Iris.
        // The live depth buffer can already contain water here, so copy the same
        // pre-translucent snapshot instead of sampling the current depth.
        pingPongManager.copyPreTranslucentDepthToPreHandDepth();
        preHandDepthCopiedThisFrame = true;
    }

    public void finishHand() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        // Later post-processing passes use depth snapshots to decide whether a
        // pixel belongs to stable opaque scene history. Refresh depthtex2 after
        // the hand draw so held items have a current near-depth classification
        // without disturbing depthtex1's pre-translucent water/refraction role.
        pingPongManager.copyPreHandDepth();
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
                    fallbackColorAttachment(),
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
        clearPresentationTarget(target, "direct-blit");
        readBuffer.blitTo(
                fallbackColorAttachment(),
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

    private void clearPresentationTarget(Framebuffer target, String reason) {
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            target.bindFramebuffer(false);
            GL11.glDrawBuffer(target.framebufferObject == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glDrawBuffer(previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
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
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            runShadowCompPasses();
            return;
        }

        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(arrayId, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(arrayId, List.of());
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        List<RenderPass> fixedPasses = array == null ? List.of() : array.fixedPasses();
        int maxIndex = Math.max(maxComputeArrayIndex(computes), maxFullscreenArrayProgramIndex(indexedPrograms));
        if (!fixedPasses.isEmpty()) {
            maxIndex = Math.max(maxIndex, fixedPasses.size() - 1);
        }

        RenderPass computeBindingPass = computeBindingPass(arrayId);
        for (int index = 0; index <= maxIndex; index++) {
            runComputeProgramsForArrayIndex(computes, index, computeBindingPass);

            if (index < fixedPasses.size()) {
                PipelineProgram program = programs.get(fixedPasses.get(index));
                if (program != null && program.hasOwnProgram()) {
                    runFullscreenPass(program);
                }
            }

            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    runFullscreenArrayProgram(program);
                }
            }
        }
    }

    private void runShadowCompPasses() {
        int size = shadowFramebuffer != null ? shadowFramebuffer.resolution() : 1;
        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        int maxIndex = Math.max(maxComputeArrayIndex(computes), maxFullscreenArrayProgramIndex(indexedPrograms));
        for (int index = 0; index <= maxIndex; index++) {
            runComputeProgramsForArrayIndex(computes, index, RenderPass.SHADOW, size, size);
            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    runShadowCompArrayProgram(program);
                }
            }
        }
    }

    private void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass) {
        runComputeProgramsForArrayIndex(computes, index, bindingPass, -1, -1);
    }

    private void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        List<ComputeProgram> indexedComputes = new ArrayList<>();
        for (ComputeProgram compute : computes) {
            if (compute != null && compute.arrayIndex() == index) {
                indexedComputes.add(compute);
            }
        }
        if (indexedComputes.isEmpty()) {
            return;
        }
        if (width > 0 && height > 0) {
            runComputePrograms(indexedComputes, bindingPass, width, height);
        } else {
            runComputePrograms(indexedComputes, bindingPass);
        }
    }

    private static int maxComputeArrayIndex(List<ComputeProgram> computes) {
        int max = -1;
        if (computes != null) {
            for (ComputeProgram compute : computes) {
                if (compute != null) {
                    max = Math.max(max, compute.arrayIndex());
                }
            }
        }
        return max;
    }

    private static int maxFullscreenArrayProgramIndex(List<FullscreenArrayProgram> programs) {
        int max = -1;
        if (programs != null) {
            for (FullscreenArrayProgram program : programs) {
                if (program != null && program.hasProgram()) {
                    max = Math.max(max, program.index());
                }
            }
        }
        return max;
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
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
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
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
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
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures();
        shaderProgram.bind();
        bindProgramResources(bindingPass, shaderProgram);
        bindCustomTextures(program.arrayId(), program.index(), shaderProgram);
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
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        Minecraft mc = Minecraft.getMinecraft();
        int width = framebuffer != null ? framebuffer.getWidth() : mc != null ? mc.displayWidth : 1;
        int height = framebuffer != null ? framebuffer.getHeight() : mc != null ? mc.displayHeight : 1;
        runComputePrograms(computes, bindingPass, width, height);
    }

    private void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
            applyComputeMemoryBarrier(compute.hasIndirectPointer());
            compute.bind();
            TextureBinder.bindDeferredTextures();
            TextureBinder.bindShadowTextures();
            bindProgramResources(bindingPass, compute.program());
            if (compute.hasIndirectPointer()) {
                int bufferId = shaderStorageBuffers.glBufferId(compute.indirectBuffer());
                if (bufferId != 0) {
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, bufferId);
                    GL43.glDispatchComputeIndirect(compute.indirectOffset());
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 0);
                } else {
                    MainMod.LOGGER.warn(
                            "[Pipeline] Skipping indirect compute '{}' because SSBO binding {} is unavailable",
                            compute.name(),
                            compute.indirectBuffer()
                    );
                }
            } else {
                int[] groups = compute.workGroups(safeWidth, safeHeight);
                GL43.glDispatchCompute(groups[0], groups[1], groups[2]);
            }
            applyComputeMemoryBarrier(false);
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    private void applyComputeMemoryBarrier(boolean indirectDispatch) {
        int barriers = 0;
        if (shaderProperties == null || !shaderProperties.renderSettings().allowConcurrentCompute()) {
            barriers |= GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                    | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                    | GL42.GL_TEXTURE_FETCH_BARRIER_BIT
                    | GL42.GL_FRAMEBUFFER_BARRIER_BIT;
        }
        if (indirectDispatch) {
            barriers |= GL42.GL_COMMAND_BARRIER_BIT;
        }
        if (barriers != 0) {
            GL42.glMemoryBarrier(barriers);
        }
    }

    private void applyShaderImageTextureBarrier() {
        if (shaderImages.active() && GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
    }

    private void runFullscreenPass(PipelineProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        generateReadMipmaps(program);

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        setupFullscreenState();
        try {
            applyFullscreenViewport(program, drawBuffers);
            applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            if (bindFullscreenPipelineProgram(program)) {
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = program.shaderProgram();
            if (shaderProgram != null) {
                shaderProgram.unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        generateWrittenMipmaps(program, flippedAttachments);
    }

    private boolean bindFullscreenPipelineProgram(PipelineProgram program) {
        if (program == null || program.shaderProgram() == null) {
            return false;
        }

        ShaderProgram shaderProgram = program.shaderProgram();
        activePass = program.pass();
        activeShaderKey = program.shaderKey();
        activePhase = WorldRenderingPhase.NONE;
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures();
        shaderProgram.bind();
        bindProgramResources(program.pass(), shaderProgram);
        return true;
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
        logColorBufferProbe("before-final");
        clearPresentationTarget(target, "final-pass");
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
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        try {
            applyViewportScale(finalProgram, target.framebufferWidth, target.framebufferHeight);
            applyFullscreenArrayRenderState(finalProgram.directives(), finalProgram.drawBuffers());
            if (bindFullscreenPipelineProgram(finalProgram)) {
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = finalProgram.shaderProgram();
            if (shaderProgram != null) {
                shaderProgram.unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

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

    private void runShadowCompArrayProgram(FullscreenArrayProgram program) {
        if (shadowFramebuffer == null) {
            return;
        }

        List<Attachment> drawBuffers = program.drawBuffers();
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        shadowFramebuffer.bindForProgramWrite(drawBuffers.toArray(new Attachment[0]));
        setupFullscreenState();
        try {
            applyViewportScale(program.directives().viewportScale(), shadowFramebuffer.resolution(), shadowFramebuffer.resolution());
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
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        applyShaderImageTextureBarrier();
        shadowFramebuffer.generateShadowColorMipmaps();
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
        resetShadowRenderCache();
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
        ShaderBlockLayerOverrides.clear();
        ShaderSamplerState.setBreaksAnisotropy(false);
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
        resetEndFlashState();
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
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
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

    public boolean shouldForceVanillaTerrainRenderer() {
        return isPipelineActive && hardwareSafeVanillaTerrain;
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
            return false;
        }

        return hasOnlyValidBetterPortalsChunkUpdates(accessor, renderPassWorld);
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

    private boolean hasOnlyValidBetterPortalsChunkUpdates(RenderGlobalAccessor accessor, World allowedWorld) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (!isValidBetterPortalsChunkUpdate(chunk, allowedWorld)) {
                MainMod.LOGGER.debug("[BetterPortalsCompat] Deferred nested chunk updates because the queue contains work for another world");
                return false;
            }
        }
        return true;
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

    private String describeWorld(World world) {
        if (world == null) {
            return "null";
        }
        return "dim=" + safeDimensionId(world) + ", id=" + System.identityHashCode(world);
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
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-before");
        restoreVanillaWorldPassState(false, !nestedBetterPortalsView);
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
            return 0;
        }

        Minecraft mc = Minecraft.getMinecraft();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (mc == null || mc.renderGlobal == null || bloomLayer == null) {
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
        }

        return rendered;
    }

    public int renderEmissiveBloomExtractionFromWorldPass(float partialTicks, int pass) {
        boolean pipelineActive = isPipelineActive;
        if (renderingGuiScreen()
                || renderingShadowMap
                || isRenderingBetterPortalsRenderPass()
                || isRenderingBetterPortalsNestedView()
                || (!pipelineActive && shaderlessBloomRenderedThisWorldPass)) {
            return 0;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.renderGlobal == null || mc.getRenderViewEntity() == null || mc.getFramebuffer() == null) {
            return 0;
        }

        boolean hasBloomResources = bloomRenderer.hasBloomResources();
        boolean hasShaderlessBloomMetadata = hasShaderlessBloomMetadata();
        boolean framedBloomBootstrap = !pipelineActive
                && !shaderlessBloomVertexFormatRefreshRequested
                && hasShaderlessFramedBloomBootstrapCandidate();
        refreshShaderlessBloomVertexFormatIfNeeded(hasBloomResources);

        boolean shouldExtractBloom = hasShaderlessBloomMetadata || framedBloomBootstrap;
        if (!shouldExtractBloom) {
            return 0;
        }
        if (!pipelineActive && AusmBloomLayer.shouldUseShaderlessNativeHook()) {
            return 0;
        }

        Entity renderViewEntity = mc.getRenderViewEntity();
        boolean previousShaderlessBloomExtractionActive = shaderlessBloomExtractionActive;
        boolean previousShaderlessBloomExtractionBootstrapActive = shaderlessBloomExtractionBootstrapActive;
        DeferredFramebuffer pipelineDepthSource = pipelineActive && worldFrameActive && pingPongManager.isInitialized()
                ? pingPongManager.getReadBuffer()
                : null;
        int rendered;
        shaderlessBloomExtractionActive = true;
        shaderlessBloomExtractionBootstrapActive = framedBloomBootstrap && !hasShaderlessBloomMetadata;
        try {
            rendered = bloomRenderer.renderEmissiveTerrainBloomCount(
                    mc.getFramebuffer(),
                    pipelineDepthSource,
                    () -> renderBloomExtractionGeometry(mc, renderViewEntity, true),
                    true
            );
        } finally {
            shaderlessBloomExtractionActive = previousShaderlessBloomExtractionActive;
            shaderlessBloomExtractionBootstrapActive = previousShaderlessBloomExtractionBootstrapActive;
        }

        if (rendered <= 0) {
            if (!pipelineActive) {
                shaderlessBloomRenderedThisWorldPass = true;
                shaderlessBloomRenderedThisWorldFrame = true;
            }
            return 0;
        }

        if (pipelineActive) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        } else {
            bloomRenderer.renderPostWorldBloom(mc.getFramebuffer());
            shaderlessBloomRenderedThisWorldPass = true;
            shaderlessBloomRenderedThisWorldFrame = true;
        }
        return rendered;
    }

    private void logVisibleBloomDiag(String stage, int pass, int rendered, String detail) {
        // Diagnostic disabled.
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
        if (isPipelineActive) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
            return;
        }
        if (AusmBloomLayer.shouldUseNativeHook()) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
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
        bloomZeroGeometryRefreshCooldown = 120;
        scheduleBloomTerrainRefresh("zero-bloom-geometry");
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
            bloomRenderer.renderPostWorldBloom(target);
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
        boolean hasBloomResources = bloomRenderer.hasBloomResources();
        boolean hasShaderlessBloomMetadata = hasShaderlessBloomMetadata();
        boolean framedBloomBootstrap = !shaderlessBloomVertexFormatRefreshRequested
                && hasShaderlessFramedBloomBootstrapCandidate();
        refreshShaderlessBloomVertexFormatIfNeeded(hasBloomResources);

        boolean shouldExtractShaderlessBloom = hasShaderlessBloomMetadata || framedBloomBootstrap;
        boolean nativeBloom = AusmBloomLayer.shouldUseShaderlessNativeHook();
        Entity renderViewEntity = mc.getRenderViewEntity();
        logShaderlessBloomHook("render target=" + describeFramebufferTarget(mc.getFramebuffer())
                + " bloomResources=" + hasBloomResources
                + " metadata=" + hasShaderlessBloomMetadata
                + " framedBootstrap=" + framedBloomBootstrap
                + " nativeBloom=" + nativeBloom
                + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                + " renderPass=" + isRenderingBetterPortalsRenderPass());
        if (!shouldExtractShaderlessBloom && !nativeBloom) {
            shaderlessBloomRenderedThisWorldPass = true;
            shaderlessBloomRenderedThisWorldFrame = true;
            restoreShaderlessBloomExitState(mc);
            return;
        }
        renderNativeBloomLayerIfNeeded();
        boolean shaderlessExtractRendered = false;
        if (!nativeBloom && shouldExtractShaderlessBloom) {
            boolean previousShaderlessBloomExtractionActive = shaderlessBloomExtractionActive;
            boolean previousShaderlessBloomExtractionBootstrapActive = shaderlessBloomExtractionBootstrapActive;
            shaderlessBloomExtractionActive = true;
            shaderlessBloomExtractionBootstrapActive = framedBloomBootstrap && !hasShaderlessBloomMetadata;
            try {
                shaderlessExtractRendered = bloomRenderer.renderShaderlessEmissiveTerrainBloom(
                        mc.getFramebuffer(),
                        () -> renderShaderlessBloomExtractionGeometry(mc, renderViewEntity)
                );
            } finally {
                shaderlessBloomExtractionActive = previousShaderlessBloomExtractionActive;
                shaderlessBloomExtractionBootstrapActive = previousShaderlessBloomExtractionBootstrapActive;
            }
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
        restoreShaderlessBloomExitState(mc);
    }

    private void restoreShaderlessBloomExitState(Minecraft mc) {
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        GlStateManager.bindTexture(0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GlStateManager.colorMask(true, true, true, true);
        if (mc != null) {
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
    }

    public void prepareShaderlessUiRenderingBoundary() {
        probeShaderlessSkyGuiState("shaderless-ui-boundary-enter");
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            probeShaderlessSkyGuiState("shaderless-ui-boundary-skip");
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.getRenderViewEntity() == null) {
            probeShaderlessSkyGuiState("shaderless-ui-boundary-no-world");
            return;
        }
        if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        }
        restoreShaderlessBloomExitState(mc);
        if (mc.currentScreen != null) {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        probeShaderlessSkyGuiState("shaderless-ui-boundary-exit");
        probeShaderlessLightState("shaderless-ui-boundary-exit");
    }

    private void refreshShaderlessBloomVertexFormatIfNeeded() {
        refreshShaderlessBloomVertexFormatIfNeeded(bloomRenderer.hasBloomResources());
    }

    private void refreshShaderlessBloomVertexFormatIfNeeded(boolean hasBloomResources) {
        if (isPipelineActive
                || shaderlessBloomVertexFormatRefreshRequested
                || AusmBloomLayer.shouldUseShaderlessNativeHook()
                || !hasBloomResources) {
            return;
        }

        shaderlessBloomVertexFormatRefreshRequested = true;
        boolean recreateNothirium = updateNothiriumPipelineBlockFormatMode();
        rebuildTerrainRenderers(recreateNothirium, false);
    }

    private boolean hasShaderlessFramedBloomBootstrapCandidate() {
        return blockcrafteryTileClass() != null;
    }

    public void recordCurrentShaderlessBloomMetadata(BlockRenderLayer layer) {
        recordShaderlessBloomMetadata(
                BlockRenderContext.blockX(),
                BlockRenderContext.blockY(),
                BlockRenderContext.blockZ(),
                layer
        );
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer) {
        if (pos == null) {
            return;
        }
        recordShaderlessBloomMetadata(pos.getX(), pos.getY(), pos.getZ(), layer);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer) {
        recordShaderlessBloomMetadata(blockX, blockY, blockZ, layer, true);
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer, boolean hasBloom) {
        if (pos == null) {
            return;
        }
        recordShaderlessBloomMetadata(pos.getX(), pos.getY(), pos.getZ(), layer, hasBloom);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer, boolean hasBloom) {
        if (layer == null) {
            return;
        }
        long key = shaderlessBloomMetadataKey(
                currentClientDimensionId(),
                blockX >> 4,
                blockY >> 4,
                blockZ >> 4,
                layer
        );
        shaderlessBloomMetadataKnownChunkLayers.add(key);
        if (hasBloom) {
            shaderlessBloomMetadataChunkLayers.add(key);
        } else {
            shaderlessBloomMetadataChunkLayers.remove(key);
        }
    }

    public void clearShaderlessBloomMetadata() {
        shaderlessBloomMetadataKnownChunkLayers.clear();
        shaderlessBloomMetadataChunkLayers.clear();
    }

    private boolean hasShaderlessBloomMetadata() {
        return !shaderlessBloomMetadataChunkLayers.isEmpty();
    }

    public boolean isShaderlessBloomExtractionActive() {
        return shaderlessBloomExtractionActive;
    }

    public boolean shouldRenderShaderlessBloomChunkLayer(BlockRenderLayer layer, int chunkBlockX, int chunkBlockY, int chunkBlockZ) {
        if (!shaderlessBloomExtractionActive) {
            return true;
        }
        if (layer == null) {
            return false;
        }
        if (AusmBloomLayer.isBloomLayer(layer) || shaderlessBloomExtractionBootstrapActive) {
            return true;
        }
        long key = shaderlessBloomMetadataKey(
                currentClientDimensionId(),
                chunkBlockX >> 4,
                chunkBlockY >> 4,
                chunkBlockZ >> 4,
                layer
        );
        return shaderlessBloomMetadataChunkLayers.contains(key);
    }

    private int currentClientDimensionId() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null && mc.world != null && mc.world.provider != null
                ? mc.world.provider.getDimension()
                : Integer.MIN_VALUE;
    }

    private static long shaderlessBloomMetadataKey(int dimension, int sectionX, int sectionY, int sectionZ, BlockRenderLayer layer) {
        return ((long) (dimension & 0x3FF) << 54)
                | ((long) (layer.ordinal() & 0xF) << 50)
                | ((long) (sectionY & 0x3FF) << 40)
                | ((long) (sectionX & 0xFFFFF) << 20)
                | (long) (sectionZ & 0xFFFFF);
    }

    private int renderShaderlessBloomExtractionGeometry(Minecraft mc, Entity viewEntity) {
        return renderBloomExtractionGeometry(mc, viewEntity, false);
    }

    private int renderBloomExtractionGeometry(Minecraft mc, Entity viewEntity, boolean allowPipelineActive) {
        if (mc == null || viewEntity == null) {
            return 0;
        }
        float partialTicks = mc.getRenderPartialTicks();
        if (!isPipelineActive && mc.entityRenderer != null) {
            ((EntityRendererAccessor) mc.entityRenderer).ausm$setupCameraTransform(partialTicks, 2);
            MatrixState.captureGbufferMatrices();
        }
        return renderEmissiveExtractionTerrain(partialTicks, viewEntity, allowPipelineActive);
    }

    private int renderShaderlessNothiriumEmissiveTerrain(float partialTicks, Entity viewEntity) {
        return renderEmissiveExtractionTerrain(partialTicks, viewEntity, false);
    }

    private int renderEmissiveExtractionTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if ((!allowPipelineActive && isPipelineActive) || viewEntity == null) {
            return 0;
        }
        if (!NothiriumShadowRenderer.isAvailable() || NothiriumBypass.shouldBypass()) {
            return renderVanillaEmissiveTerrain(partialTicks, viewEntity, allowPipelineActive);
        }

        double cameraX = interpolate(viewEntity.lastTickPosX, viewEntity.posX, partialTicks);
        double cameraY = interpolate(viewEntity.lastTickPosY, viewEntity.posY, partialTicks);
        double cameraZ = interpolate(viewEntity.lastTickPosZ, viewEntity.posZ, partialTicks);
        nothiriumShadowRenderer.drainUploads();

        int solid = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.SOLID, cameraX, cameraY, cameraZ);
        int cutoutMipped = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT_MIPPED, cameraX, cameraY, cameraZ);
        int cutout = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT, cameraX, cameraY, cameraZ);
        int translucent = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ);
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        Minecraft mc = Minecraft.getMinecraft();
        int bloom = shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer) && mc != null && mc.renderGlobal != null
                ? renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT,
                        bloomLayer, partialTicks, viewEntity)
                : 0;
        int rendered = solid + cutoutMipped + cutout + translucent + bloom;
        return rendered;
    }

    private int renderShaderlessNothiriumExtractionLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            return positiveCount(nothiriumShadowRenderer.renderVisibleLayer(layer, cameraX, cameraY, cameraZ, 0, (short) 0));
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
        }
    }

    private int renderShaderlessVanillaEmissiveTerrain(float partialTicks, Entity viewEntity) {
        return renderVanillaEmissiveTerrain(partialTicks, viewEntity, false);
    }

    private int renderVanillaEmissiveTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if (!allowPipelineActive && isPipelineActive) {
            return 0;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.renderGlobal == null || mc.world == null || viewEntity == null) {
            return 0;
        }

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        if (!isPipelineActive) {
            shaderlessWorldPassActive = true;
        }
        try {
            int solid = renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
            int cutoutMipped = renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
            int cutout = renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            int translucent = renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            int bloom = shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer)
                    ? renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            int rendered = solid + cutoutMipped + cutout + translucent + bloom;
            return rendered;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    private int renderShaderlessVanillaEmissiveLayerIfVisible(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                              float partialTicks, Entity viewEntity) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        return renderShaderlessVanillaEmissiveLayer(mc, phase, layer, partialTicks, viewEntity);
    }

    private int renderShaderlessVanillaEmissiveLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                     float partialTicks, Entity viewEntity) {
        activePhase = phase;
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        prepareShaderlessBlockLayerState(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            return positiveCount(mc.renderGlobal.renderBlockLayer(layer, partialTicks, 2, viewEntity));
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
            finishShaderlessBlockLayerState(layer);
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    private static boolean shouldRenderSyntheticBloomLayerWithRenderGlobal(BlockRenderLayer layer) {
        return layer != null && (!AusmBloomLayer.isBloomLayer(layer) || !isNothiriumLoaded());
    }

    private static boolean isNothiriumLoaded() {
        return Loader.isModLoaded(NOTHIRIUM_MOD_ID) || Loader.isModLoaded(NAUGHTHIRIUM_MOD_ID);
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
        Minecraft mc = Minecraft.getMinecraft();
        if (!isPipelineActive && (mc == null || mc.world == null || mc.getRenderViewEntity() == null)) {
            return;
        }
        restoreWorldSafeRenderState(source);
    }

    private void restoreGuiSafeRenderState(String source) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderImages();
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
        if (BetterPortalsCompat.isRenderingRenderPass()
                || isRenderingBetterPortalsNestedView()) {
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
                if (mc != null && mc.world != null && mc.getRenderViewEntity() != null) {
                    OpenGlHelper.glUseProgram(0);
                    TextureBinder.restoreDefaultTextureUnit();
                    GlStateManager.bindTexture(0);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.enableTexture2D();
                    GlStateManager.enableAlpha();
                    GlStateManager.enableBlend();
                    GlStateManager.tryBlendFuncSeparate(
                            GL11.GL_SRC_ALPHA,
                            GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ONE,
                            GL11.GL_ZERO
                    );
                    GlStateManager.enableDepth();
                    GL11.glDepthMask(true);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
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

    public void prepareShaderlessGuiScreenRendering() {
        probeShaderlessSkyGuiState("shaderless-gui-screen-before");
        if (isPipelineActive) {
            return;
        }
        renderingGui = false;
        guiRenderDepth = 0;
        OpenGlHelper.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
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
        probeShaderlessSkyGuiState("shaderless-gui-screen-after");
    }

    public void beginGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        guiRenderDepth++;
        prepareGuiRendering();
    }

    public void finishGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }
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
        GlStateManager.disableDepth();
        GL11.glDepthMask(false);
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
                && mc.world != null
                && mc.currentScreen == null
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
        PipelineProgram program = effectivePipelineProgram(pass);
        return program != null ? program.shaderProgram() : null;
    }

    private PipelineProgram effectiveDistantHorizonsPipelineProgram() {
        PipelineProgram pipelineProgram = effectivePipelineProgram(currentDistantHorizonsPass);
        if (pipelineProgram == null && currentDistantHorizonsPass != RenderPass.DH_TERRAIN) {
            pipelineProgram = effectivePipelineProgram(RenderPass.DH_TERRAIN);
        }
        return pipelineProgram != null && pipelineProgram.shaderProgram() != null ? pipelineProgram : null;
    }

    public boolean shouldUseDistantHorizonsShaderProgram() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return true;
        }
        return ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().world != null;
    }

    public int distantHorizonsShaderProgramId() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            return ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
        }
        PipelineProgram pipelineProgram = effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram != null) {
            return pipelineProgram.shaderProgram().getId();
        }
        return ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
    }

    public void bindDistantHorizonsShaderProgram() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            bindDistantHorizonsFallbackProgram();
            return;
        }
        PipelineProgram pipelineProgram = effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram == null) {
            bindDistantHorizonsFallbackProgram();
            return;
        }

        ShaderProgram program = pipelineProgram.shaderProgram();
        RenderPass pass = pipelineProgram.pass();
        currentDistantHorizonsProgram = program;
        currentDistantHorizonsFallbackProgram = false;
        bindDistantHorizonsVertexArray();
        configureDistantHorizonsShaderState(pipelineProgram);
        program.bind();
        bindProgramResources(pass, program);
    }

    public void unbindDistantHorizonsShaderProgram() {
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = false;
        OpenGlHelper.glUseProgram(0);
        GL30.glBindVertexArray(0);
    }

    private void configureDistantHorizonsShaderState(PipelineProgram pipelineProgram) {
        RenderPass pass = pipelineProgram.pass();
        List<Attachment> drawBuffers = pipelineProgram.effectiveDrawBuffers(programs);
        if (drawBuffers.isEmpty()) {
            drawBuffers = List.of(fallbackColorAttachment());
        } else if ((pass == RenderPass.DH_TERRAIN || pass == RenderPass.DH_WATER)
                && drawBuffers.size() == 1
                && drawBuffers.get(0) == Attachment.COLOR) {
            drawBuffers = List.of(Attachment.COLOR, Attachment.AUX3);
        }
        applyAlphaTest(pass);
        applyBlendMode(pass, drawBuffers);
        applyOitDepthState(pass);
        applyGbufferDepthState(pass);
        pingPongManager.bindForGbuffers(drawBuffers.toArray(new Attachment[0]));
        restoreVanillaWorldTextureBindings();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GL11.glColorMask(true, true, true, true);
        if (usesBlockAtlas(pass)) {
            bindBlockAtlas();
        }
        TextureBinder.bindGbufferRenderTargetSamplers();
        if (usesBlockAtlas(pass)) {
            bindBlockAtlas();
        }
        TextureBinder.bindNoiseTexture();
        TextureBinder.bindShadowTextures();
        TextureBinder.bindMaterialFallbackTextures();
    }

    public void bindDistantHorizonsVertexBuffer(int bufferId) {
        bindDistantHorizonsVertexArray();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        GL30.glVertexAttribIPointer(0, 4, GL11.GL_UNSIGNED_SHORT, 16, 0L);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 16, 8L);
        GL30.glVertexAttribIPointer(2, 4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
    }

    private void bindDistantHorizonsVertexArray() {
        if (distantHorizonsVertexArray < 0) {
            distantHorizonsVertexArray = GL30.glGenVertexArrays();
        }
        GL30.glBindVertexArray(distantHorizonsVertexArray);
    }

    private void bindDistantHorizonsFallbackProgram() {
        if (!ensureDistantHorizonsFallbackProgram()) {
            return;
        }
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = true;
        bindDistantHorizonsVertexArray();
        OpenGlHelper.glUseProgram(distantHorizonsFallbackProgramId);
        uploadDistantHorizonsFallbackMatrices();
        uploadDistantHorizonsFallbackModelOffset();
    }

    private boolean ensureDistantHorizonsFallbackProgram() {
        if (distantHorizonsFallbackProgramId != 0) {
            return true;
        }
        if (distantHorizonsFallbackProgramFailed || !GLContext.getCapabilities().OpenGL30) {
            return false;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileDistantHorizonsFallbackShader(GL20.GL_VERTEX_SHADER, DISTANT_HORIZONS_FALLBACK_VERTEX_SHADER);
            fragmentShader = compileDistantHorizonsFallbackShader(GL20.GL_FRAGMENT_SHADER, DISTANT_HORIZONS_FALLBACK_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glBindAttribLocation(program, 0, "vPosition");
            GL20.glBindAttribLocation(program, 1, "color");
            GL20.glBindAttribLocation(program, 2, "dhMaterialData");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                MainMod.LOGGER.warn("[DistantHorizons] Failed to link fallback shader: {}", GL20.glGetProgramInfoLog(program, 4096));
                GL20.glDeleteProgram(program);
                distantHorizonsFallbackProgramFailed = true;
                return false;
            }

            distantHorizonsFallbackProgramId = program;
            distantHorizonsFallbackCombinedMatrixUniform = GL20.glGetUniformLocation(program, "uCombinedMatrix");
            distantHorizonsFallbackProjectionMatrixUniform = GL20.glGetUniformLocation(program, "uProjectionMatrix");
            distantHorizonsFallbackModelViewMatrixUniform = GL20.glGetUniformLocation(program, "uModelViewMatrix");
            distantHorizonsFallbackModelOffsetUniform = GL20.glGetUniformLocation(program, "uModelOffset");
            distantHorizonsFallbackWorldYOffsetUniform = GL20.glGetUniformLocation(program, "uWorldYOffset");
            distantHorizonsFallbackMircoOffsetUniform = GL20.glGetUniformLocation(program, "uMircoOffset");
            distantHorizonsFallbackEarthRadiusUniform = GL20.glGetUniformLocation(program, "uEarthRadius");
            MainMod.LOGGER.info("[DistantHorizons] Created AUSM fallback shader program.");
            return true;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    private int compileDistantHorizonsFallbackShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            MainMod.LOGGER.warn("[DistantHorizons] Failed to compile fallback shader stage {}: {}",
                    type,
                    GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            distantHorizonsFallbackProgramFailed = true;
            return 0;
        }
        return shader;
    }

    private void uploadDistantHorizonsFallbackMatrices() {
        if (distantHorizonsFallbackProgramId == 0) {
            return;
        }
        if (distantHorizonsFallbackCombinedMatrixUniform >= 0) {
            FloatBuffer combinedMatrix = dhModelViewProjectionBuffer.duplicate();
            combinedMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackCombinedMatrixUniform, false, combinedMatrix);
        }
        if (distantHorizonsFallbackProjectionMatrixUniform >= 0) {
            FloatBuffer projectionMatrix = dhProjectionBuffer.duplicate();
            projectionMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackProjectionMatrixUniform, false, projectionMatrix);
        }
        if (distantHorizonsFallbackModelViewMatrixUniform >= 0) {
            FloatBuffer modelViewMatrix = dhModelViewBuffer.duplicate();
            modelViewMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackModelViewMatrixUniform, false, modelViewMatrix);
        }
        if (distantHorizonsFallbackMircoOffsetUniform >= 0) {
            GL20.glUniform1f(distantHorizonsFallbackMircoOffsetUniform, 0.01F);
        }
        if (distantHorizonsFallbackEarthRadiusUniform >= 0) {
            GL20.glUniform1f(distantHorizonsFallbackEarthRadiusUniform, 0.0F);
        }
    }

    private void uploadDistantHorizonsFallbackModelOffset() {
        if (distantHorizonsFallbackProgramId != 0 && distantHorizonsFallbackModelOffsetUniform >= 0) {
            GL20.glUniform3f(distantHorizonsFallbackModelOffsetUniform, dhModelOffset[0], dhModelOffset[1], dhModelOffset[2]);
        }
    }

    private void deleteDistantHorizonsFallbackProgram() {
        currentDistantHorizonsFallbackProgram = false;
        if (distantHorizonsFallbackProgramId != 0) {
            GL20.glDeleteProgram(distantHorizonsFallbackProgramId);
        }
        distantHorizonsFallbackProgramId = 0;
        distantHorizonsFallbackCombinedMatrixUniform = -1;
        distantHorizonsFallbackProjectionMatrixUniform = -1;
        distantHorizonsFallbackModelViewMatrixUniform = -1;
        distantHorizonsFallbackModelOffsetUniform = -1;
        distantHorizonsFallbackWorldYOffsetUniform = -1;
        distantHorizonsFallbackMircoOffsetUniform = -1;
        distantHorizonsFallbackEarthRadiusUniform = -1;
        distantHorizonsFallbackProgramFailed = false;
    }

    private boolean ensureDistantHorizonsCompositeProgram() {
        if (distantHorizonsCompositeProgramId != 0) {
            return true;
        }
        if (distantHorizonsCompositeProgramFailed || !OpenGlHelper.shadersSupported) {
            return false;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileDistantHorizonsCompositeShader(GL20.GL_VERTEX_SHADER, DISTANT_HORIZONS_COMPOSITE_VERTEX_SHADER);
            fragmentShader = compileDistantHorizonsCompositeShader(GL20.GL_FRAGMENT_SHADER, DISTANT_HORIZONS_COMPOSITE_FRAGMENT_SHADER);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                MainMod.LOGGER.warn("[DistantHorizons] Failed to link composite shader: {}", GL20.glGetProgramInfoLog(program, 4096));
                GL20.glDeleteProgram(program);
                distantHorizonsCompositeProgramFailed = true;
                return false;
            }

            distantHorizonsCompositeProgramId = program;
            distantHorizonsCompositeTextureUniform = GL20.glGetUniformLocation(program, "dhColor");
            distantHorizonsCompositeDepthUniform = GL20.glGetUniformLocation(program, "dhDepth");
            return true;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    private int compileDistantHorizonsCompositeShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            MainMod.LOGGER.warn("[DistantHorizons] Failed to compile composite shader stage {}: {}",
                    type,
                    GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            distantHorizonsCompositeProgramFailed = true;
            return 0;
        }
        return shader;
    }

    private void deleteDistantHorizonsCompositeProgram() {
        if (distantHorizonsCompositeProgramId != 0) {
            GL20.glDeleteProgram(distantHorizonsCompositeProgramId);
        }
        distantHorizonsCompositeProgramId = 0;
        distantHorizonsCompositeTextureUniform = -1;
        distantHorizonsCompositeDepthUniform = -1;
        distantHorizonsCompositeProgramFailed = false;
    }

    private void deleteDistantHorizonsFramebuffer() {
        if (distantHorizonsFramebufferId != 0) {
            OpenGlHelper.glDeleteFramebuffers(distantHorizonsFramebufferId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsColorTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsColorTextureId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsDepthTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsDepthTextureId);
        }
        if (distantHorizonsTextureReadbackFramebufferId != 0) {
            OpenGlHelper.glDeleteFramebuffers(distantHorizonsTextureReadbackFramebufferId);
        }
        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = 0;
        distantHorizonsFramebufferHeight = 0;
        distantHorizonsFramebufferClearFrame = Long.MIN_VALUE;
        distantHorizonsTextureReadbackFramebufferId = 0;
        distantHorizonsFramebufferPendingComposite = false;
    }

    public void setDistantHorizonsModelOffset(Object vec) {
        if (vec == null) {
            return;
        }
        try {
            dhModelOffset[0] = ((Number) vec.getClass().getField("x").get(vec)).floatValue();
            dhModelOffset[1] = ((Number) vec.getClass().getField("y").get(vec)).floatValue();
            dhModelOffset[2] = ((Number) vec.getClass().getField("z").get(vec)).floatValue();
            if (currentDistantHorizonsProgram != null) {
                uniformRegistry.upload(currentDistantHorizonsProgram, "dhModelOffset");
                uniformRegistry.upload(currentDistantHorizonsProgram, "iris_ModelOffset");
            } else if (currentDistantHorizonsFallbackProgram) {
                uploadDistantHorizonsFallbackModelOffset();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public void uploadDistantHorizonsUniforms(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            updateDistantHorizonsRenderPass(renderParam);

            copyDistantHorizonsMatrix(renderParam, "dhProjectionMatrix", dhProjectionBuffer);
            copyAndInvertDistantHorizonsMatrix(renderParam, "dhProjectionMatrix", dhProjectionInverseBuffer);
            copyDistantHorizonsMatrix(renderParam, "dhModelViewMatrix", dhModelViewBuffer);
            copyDistantHorizonsMatrix(renderParam, "dhMvmProjMatrix", dhModelViewProjectionBuffer);
            bindDistantHorizonsShaderProgram();
            uploadDistantHorizonsWorldYOffset(renderParam);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private void uploadDistantHorizonsWorldYOffset(Object renderParam) {
        if (!currentDistantHorizonsFallbackProgram || distantHorizonsFallbackProgramId == 0 || distantHorizonsFallbackWorldYOffsetUniform < 0) {
            return;
        }
        try {
            float worldYOffset = ((Number) renderParam.getClass().getField("worldYOffset").get(renderParam)).floatValue();
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, 0.0F);
        }
    }

    private void copyDistantHorizonsMatrix(Object renderParam, String fieldName, FloatBuffer target) throws ReflectiveOperationException {
        Object matrix = renderParam.getClass().getField(fieldName).get(renderParam);
        if (matrix == null) {
            return;
        }
        copyDistantHorizonsMatrixValues(matrix);
        target.clear();
        target.put(dhMatrixScratch);
        target.flip();
    }

    private void copyAndInvertDistantHorizonsMatrix(Object renderParam, String fieldName, FloatBuffer target) throws ReflectiveOperationException {
        Object matrix = renderParam.getClass().getField(fieldName).get(renderParam);
        if (matrix == null) {
            return;
        }
        Object copy = matrix.getClass().getMethod("copy").invoke(matrix);
        if (Boolean.FALSE.equals(copy.getClass().getMethod("canInvert").invoke(copy))) {
            return;
        }
        copy.getClass().getMethod("invert").invoke(copy);
        copyDistantHorizonsMatrixValues(copy);
        target.clear();
        target.put(dhMatrixScratch);
        target.flip();
    }

    private void copyDistantHorizonsMatrixValues(Object matrix) throws ReflectiveOperationException {
        Class<?> type = matrix.getClass();
        dhMatrixScratch[0] = ((Number) type.getField("m00").get(matrix)).floatValue();
        dhMatrixScratch[1] = ((Number) type.getField("m10").get(matrix)).floatValue();
        dhMatrixScratch[2] = ((Number) type.getField("m20").get(matrix)).floatValue();
        dhMatrixScratch[3] = ((Number) type.getField("m30").get(matrix)).floatValue();
        dhMatrixScratch[4] = ((Number) type.getField("m01").get(matrix)).floatValue();
        dhMatrixScratch[5] = ((Number) type.getField("m11").get(matrix)).floatValue();
        dhMatrixScratch[6] = ((Number) type.getField("m21").get(matrix)).floatValue();
        dhMatrixScratch[7] = ((Number) type.getField("m31").get(matrix)).floatValue();
        dhMatrixScratch[8] = ((Number) type.getField("m02").get(matrix)).floatValue();
        dhMatrixScratch[9] = ((Number) type.getField("m12").get(matrix)).floatValue();
        dhMatrixScratch[10] = ((Number) type.getField("m22").get(matrix)).floatValue();
        dhMatrixScratch[11] = ((Number) type.getField("m32").get(matrix)).floatValue();
        dhMatrixScratch[12] = ((Number) type.getField("m03").get(matrix)).floatValue();
        dhMatrixScratch[13] = ((Number) type.getField("m13").get(matrix)).floatValue();
        dhMatrixScratch[14] = ((Number) type.getField("m23").get(matrix)).floatValue();
        dhMatrixScratch[15] = ((Number) type.getField("m33").get(matrix)).floatValue();
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

    public int getShadowColor1Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(1) : -1;
    }

    public int getShadowColorTexture(int index) {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(index) : -1;
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
        boolean activeStateChanged = wasPipelineActive != isPipelineActive;
        if (isPipelineActive) {
            zeroOpaqueTerrainFrames = 0;
            zeroOpaqueTerrainRecoveryRequested = false;
            betterPortalsPipelineLogs = 0;
            BetterPortalsCompat.resetRenderStateDiagnostics();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null && mc.world != null) {
                resizeFramebuffer(mc.displayWidth, mc.displayHeight, true);
            }
        } else {
            clearPendingShaderChunkRefreshes();
            clearShaderlessBloomMetadata();
            shaderlessBloomVertexFormatRefreshRequested = false;
            scheduleInactiveVanillaRecoveryFrame();
            resetPipelineState();
        }
        boolean nothiriumFormatChanged = updateNothiriumPipelineBlockFormatMode();
        boolean forceShaderlessBloomRebuild = wasPipelineActive
                && !isPipelineActive
                && bloomRenderer.hasBloomResources();
        if (activeStateChanged || nothiriumFormatChanged || forceShaderlessBloomRebuild) {
            rebuildTerrainRenderers(activeStateChanged || nothiriumFormatChanged, true);
        }
    }

    public void recoverShaderlessBloomAfterShaderDisable(String reason) {
        clearShaderlessBloomMetadata();
        shaderlessBloomVertexFormatRefreshRequested = false;
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        clearScheduledBloomTerrainRefresh();
        scheduleFullWorldTerrainRefresh();
        scheduleBloomTerrainRefresh(reason);
        rebuildTerrainRenderers(true, true);
    }

    public void rebuildTerrainRenderers() {
        rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode());
    }

    public void handleResourcePackReload() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return;
        }

        textureReloadCount++;
        resetPipelineState(mc.getFramebuffer());
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        clearScheduledWorldTerrainRefresh();
        clearScheduledBloomTerrainRefresh();
        scheduleWorldTerrainRefresh();
        scheduleBloomTerrainRefresh("resource-pack-reload");
        if (mc.world != null) {
            scheduleWorldLoadLightRecalculation();
            rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode());
        }
        MainMod.LOGGER.info("[Pipeline] Recovered render state after resource pack reload.");
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
        if (recreateNothiriumRenderer) {
            NothiriumBypass.recreateRenderer();
        } else {
            NothiriumBypass.markAllChanged();
        }
        if (reloadVanillaRenderGlobal) {
            mc.renderGlobal.loadRenderers();
        }
        if (isPipelineActive && mc.world != null) {
            rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "rebuild-terrain-renderers");
            resetCameraFrustumSyncState();
        } else if (isPipelineActive) {
            ensureVanillaTerrainRenderer();
        }
    }

    private boolean updateNothiriumPipelineBlockFormatMode() {
        boolean active = shouldUsePipelineBlockFormat();
        if (nothiriumPipelineBlockFormatActive == active) {
            return false;
        }
        nothiriumPipelineBlockFormatActive = active;
        MainMod.LOGGER.info("[AUSMNothiriumFormat] pipelineBlockFormat={} pipelineActive={} nativeBloom={} bloomResources={} terrainFormatSupported={}",
                active,
                isPipelineActive,
                AusmBloomLayer.shouldUseNativeHook(),
                bloomRenderer.hasBloomResources(),
                pipelineTerrainFormatSupported());
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
                Math.max(FORCE_LIGHT_RECALC_MIN_RADIUS, WORLD_LOAD_LIGHT_REFRESH_RADIUS)
        );
        int verticalRadius = Math.min(8, horizontalRadius);
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
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc != null && mc.world != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationAttempts > 0
                && pendingWorldLoadLightRecalculationDimension == dimension) {
            pendingWorldLoadLightRecalculationAttempts = Math.max(
                    pendingWorldLoadLightRecalculationAttempts,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS
            );
            pendingWorldLoadLightRecalculationDelay = Math.min(
                    pendingWorldLoadLightRecalculationDelay,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES
            );
            return;
        }

        pendingWorldLoadLightRecalculationAttempts = WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        pendingWorldLoadLightRecalculationDimension = dimension;
    }

    public void clearScheduledWorldLoadLightRecalculation() {
        pendingWorldLoadLightRecalculationAttempts = 0;
        pendingWorldLoadLightRecalculationDelay = 0;
        pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
    }

    public void scheduleWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(false);
    }

    public void scheduleFullWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true);
    }

    private void scheduleDimensionSwitchTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true, 0);
    }

    private void scheduleWorldTerrainRefresh(boolean fullRendererReset) {
        scheduleWorldTerrainRefresh(fullRendererReset, fullRendererReset);
    }

    private void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload) {
        scheduleWorldTerrainRefresh(fullRendererReset, vanillaReload, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES);
    }

    private void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay) {
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc != null && mc.world != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE;
        int delay = Math.max(0, initialDelay);
        if (pendingWorldTerrainRefreshAttempts > 0 && pendingWorldTerrainRefreshDimension == dimension) {
            logTerrainDiagnostic("schedule-world-terrain:coalesce",
                    mc != null ? mc.world : null,
                    "fullReset=" + fullRendererReset
                            + ", vanillaReload=" + vanillaReload
                            + ", initialDelay=" + delay
                            + ", oldAttempts=" + pendingWorldTerrainRefreshAttempts
                            + ", oldDelay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshAttempts = Math.max(pendingWorldTerrainRefreshAttempts, WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS);
            pendingWorldTerrainRefreshDelay = Math.min(pendingWorldTerrainRefreshDelay, delay);
            pendingWorldTerrainRendererReset |= fullRendererReset;
            pendingWorldTerrainFullRendererReset |= fullRendererReset;
            pendingWorldTerrainVanillaReload |= vanillaReload;
            return;
        }

        pendingWorldTerrainRefreshAttempts = WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS;
        pendingWorldTerrainRefreshDelay = delay;
        pendingWorldTerrainRefreshDimension = dimension;
        pendingWorldTerrainRendererReset = fullRendererReset;
        pendingWorldTerrainFullRendererReset = fullRendererReset;
        pendingWorldTerrainVanillaReload = vanillaReload;
        logTerrainDiagnostic("schedule-world-terrain:new",
                mc != null ? mc.world : null,
                "fullReset=" + fullRendererReset + ", vanillaReload=" + vanillaReload + ", initialDelay=" + delay);
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

        ShaderChunkRefresh refresh = new ShaderChunkRefresh(world, chunkX, chunkZ);
        synchronized (pendingShaderChunkRefreshes) {
            if (pendingShaderChunkRefreshes.contains(refresh)) {
                return;
            }
            if (pendingShaderChunkRefreshes.size() >= MAX_PENDING_SHADER_CHUNK_REFRESHES) {
                ShaderChunkRefresh oldest = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(oldest);
            }
            pendingShaderChunkRefreshes.add(refresh);
        }
    }

    public void queueClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason) {
        String normalizedReason = reason != null ? reason : "unknown";
        if (world == null || !shouldQueueClientChunkRenderRefresh(world, normalizedReason)) {
            return;
        }

        synchronized (pendingClientChunkRenderRefreshes) {
            long chunkKey = clientChunkRenderRefreshChunkKey(chunkX, chunkZ);
            if ("chunk-data".equals(normalizedReason)) {
                forgetRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey);
            } else if (isRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey)) {
                return;
            }
            Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(world);
            ClientChunkRenderRefresh existing = worldLookup != null ? worldLookup.get(chunkKey) : null;
            if (existing != null) {
                mergeClientChunkRenderRefresh(existing, normalizedReason);
                return;
            }
            if (pendingClientChunkRenderRefreshes.size() >= MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES) {
                ClientChunkRenderRefresh oldest = pendingClientChunkRenderRefreshes.iterator().next();
                removePendingClientChunkRenderRefreshLocked(oldest);
            }
            ClientChunkRenderRefresh refresh = new ClientChunkRenderRefresh(
                    world,
                    chunkX,
                    chunkZ,
                    normalizedReason,
                    CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS,
                    clientChunkRenderRefreshInitialDelay(normalizedReason)
            );
            addPendingClientChunkRenderRefreshLocked(refresh);
        }
    }

    private void mergeClientChunkRenderRefresh(ClientChunkRenderRefresh existing, String reason) {
        if (existing == null) {
            return;
        }
        existing.attemptsRemaining = Math.max(existing.attemptsRemaining, CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS);
        if ("chunk-data".equals(reason)) {
            existing.reason = reason;
            existing.delayFrames = Math.min(existing.delayFrames, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
        } else {
            existing.delayFrames = Math.min(existing.delayFrames, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
        }
    }

    private int clientChunkRenderRefreshInitialDelay(String reason) {
        return CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES;
    }

    private static long clientChunkRenderRefreshChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public void clearPendingShaderChunkRefreshes() {
        synchronized (pendingShaderChunkRefreshes) {
            pendingShaderChunkRefreshes.clear();
        }
    }

    public void clearPendingClientChunkRenderRefreshes() {
        synchronized (pendingClientChunkRenderRefreshes) {
            pendingClientChunkRenderRefreshes.clear();
            pendingClientChunkRenderRefreshLookupByWorld.clear();
            pendingClientChunkRenderRefreshesByWorld.clear();
            recentlyCompletedClientChunkRenderRefreshes.clear();
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
        runPendingClientChunkRenderRefreshesForWorld(mc, mc.world, true);
    }

    public void runPendingClientChunkRenderRefreshesForCurrentRenderPass() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (mc == null || mc.renderGlobal == null || renderPassWorld == null) {
            return;
        }

        runPendingClientChunkRenderRefreshesForWorld(mc, renderPassWorld, false);
    }

    private void runPendingClientChunkRenderRefreshesForWorld(Minecraft mc, WorldClient targetWorld,
                                                              boolean advanceDelays) {
        if (targetWorld == null) {
            return;
        }
        if (advanceDelays) {
            ageStaleClientChunkRenderRefreshes(targetWorld);
        }
        for (int i = 0; i < MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME; i++) {
            ClientChunkRenderRefresh refresh = pollDueClientChunkRenderRefresh(targetWorld, advanceDelays);
            if (refresh == null) {
                return;
            }

            boolean retryNeeded = refreshClientChunkRender(mc, refresh, targetWorld);
            refresh.attemptsRemaining--;
            if (retryNeeded && refresh.attemptsRemaining > 0 && refresh.world == targetWorld) {
                refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                synchronized (pendingClientChunkRenderRefreshes) {
                    addPendingClientChunkRenderRefreshLocked(refresh);
                }
            }
        }
    }

    private ClientChunkRenderRefresh pollDueClientChunkRenderRefresh(WorldClient targetWorld, boolean advanceDelays) {
        synchronized (pendingClientChunkRenderRefreshes) {
            LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(targetWorld);
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                return null;
            }
            Iterator<ClientChunkRenderRefresh> iterator = worldRefreshes.iterator();
            while (iterator.hasNext()) {
                ClientChunkRenderRefresh refresh = iterator.next();
                if (refresh.delayFrames > 0) {
                    if (advanceDelays) {
                        refresh.delayFrames--;
                    }
                    continue;
                }
                iterator.remove();
                if (worldRefreshes.isEmpty()) {
                    pendingClientChunkRenderRefreshesByWorld.remove(targetWorld);
                }
                pendingClientChunkRenderRefreshes.remove(refresh);
                removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                return refresh;
            }
        }
        return null;
    }

    private void ageStaleClientChunkRenderRefreshes(WorldClient activeWorld) {
        if (activeWorld == null) {
            return;
        }

        int aged = 0;
        synchronized (pendingClientChunkRenderRefreshes) {
            Iterator<Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>>> worldIterator =
                    pendingClientChunkRenderRefreshesByWorld.entrySet().iterator();
            while (worldIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>> entry = worldIterator.next();
                WorldClient refreshWorld = entry.getKey();
                LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = entry.getValue();
                if (refreshWorld == activeWorld || worldRefreshes == null || worldRefreshes.isEmpty()) {
                    if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                        worldIterator.remove();
                    }
                    continue;
                }

                Iterator<ClientChunkRenderRefresh> refreshIterator = worldRefreshes.iterator();
                while (refreshIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                    ClientChunkRenderRefresh refresh = refreshIterator.next();
                    if (refresh == null || refresh.world == null) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                        aged++;
                        continue;
                    }

                    aged++;
                    if (refresh.delayFrames > 0) {
                        refresh.delayFrames--;
                        continue;
                    }

                    refresh.attemptsRemaining--;
                    if (refresh.attemptsRemaining <= 0 || !shouldRetainOffWorldClientChunkRefresh(refresh)) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                    } else {
                        refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                    }
                }
                if (worldRefreshes.isEmpty()) {
                    worldIterator.remove();
                }
            }
            pruneRecentlyCompletedClientChunkRenderRefreshesLocked();
        }
    }

    private void addPendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !pendingClientChunkRenderRefreshes.add(refresh)) {
            return;
        }
        pendingClientChunkRenderRefreshLookupByWorld
                .computeIfAbsent(refresh.world, ignored -> new HashMap<>())
                .put(clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ), refresh);
        pendingClientChunkRenderRefreshesByWorld
                .computeIfAbsent(refresh.world, ignored -> new LinkedHashSet<>())
                .add(refresh);
    }

    private void removePendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null) {
            return;
        }
        pendingClientChunkRenderRefreshes.remove(refresh);
        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
        removePendingClientChunkRenderRefreshFromWorldBucketLocked(refresh);
    }

    private void removePendingClientChunkRenderRefreshFromLookupLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(refresh.world);
        if (worldLookup == null) {
            return;
        }
        worldLookup.remove(clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ));
        if (worldLookup.isEmpty()) {
            pendingClientChunkRenderRefreshLookupByWorld.remove(refresh.world);
        }
    }

    private void removePendingClientChunkRenderRefreshFromWorldBucketLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(refresh.world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(refresh);
        if (worldRefreshes.isEmpty()) {
            pendingClientChunkRenderRefreshesByWorld.remove(refresh.world);
        }
    }

    private boolean shouldRetainOffWorldClientChunkRefresh(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    private boolean isRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        Long expiresAt = worldRefreshes != null ? worldRefreshes.get(chunkKey) : null;
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < pipelineFrameId) {
            worldRefreshes.remove(chunkKey);
            if (worldRefreshes.isEmpty()) {
                recentlyCompletedClientChunkRenderRefreshes.remove(world);
            }
            return false;
        }
        return true;
    }

    private void rememberCompletedClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        synchronized (pendingClientChunkRenderRefreshes) {
            recentlyCompletedClientChunkRenderRefreshes
                    .computeIfAbsent(world, ignored -> new HashMap<>())
                    .put(clientChunkRenderRefreshChunkKey(chunkX, chunkZ),
                            pipelineFrameId + CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES);
            pruneRecentlyCompletedClientChunkRenderRefreshesLocked();
        }
    }

    private void forgetRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(chunkKey);
        if (worldRefreshes.isEmpty()) {
            recentlyCompletedClientChunkRenderRefreshes.remove(world);
        }
    }

    private void pruneRecentlyCompletedClientChunkRenderRefreshesLocked() {
        Iterator<Map.Entry<WorldClient, Map<Long, Long>>> worldIterator =
                recentlyCompletedClientChunkRenderRefreshes.entrySet().iterator();
        while (worldIterator.hasNext()) {
            Map<Long, Long> worldRefreshes = worldIterator.next().getValue();
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                worldIterator.remove();
                continue;
            }
            worldRefreshes.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < pipelineFrameId);
            if (worldRefreshes.isEmpty()) {
                worldIterator.remove();
            }
        }
    }

    private boolean shouldQueueClientChunkRenderRefresh(WorldClient world, String reason) {
        if ("chunk-data".equals(reason)) {
            return true;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return false;
        }
        if (world == mc.world && ("pre-chunk".equals(reason)
                || pendingWorldTerrainRefreshAttempts > 0
                || isPipelineActive
                || NothiriumBypass.shouldBypass())) {
            return true;
        }
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return world != mc.world
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    private boolean refreshClientChunkRender(Minecraft mc, ClientChunkRenderRefresh refresh, WorldClient targetWorld) {
        if (refresh == null || refresh.world == null || targetWorld == null || refresh.world != targetWorld || mc.renderGlobal == null) {
            return false;
        }

        ChunkProviderClient chunkProvider = targetWorld.getChunkProvider();
        Chunk chunk = chunkProvider != null ? chunkProvider.getLoadedChunk(refresh.chunkX, refresh.chunkZ) : null;
        boolean loaded = chunk != null;
        ClientChunkRenderScheduleResult scheduleResult = ClientChunkRenderScheduleResult.empty();
        if (loaded) {
            ensureVanillaTerrainRenderer(targetWorld, true);
            if (mc.renderGlobal instanceof RenderGlobalAccessor accessor) {
                ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
                updateVanillaViewFrustumChunkPositions(
                        viewFrustum,
                        mc.getRenderViewEntity()
                );
                scheduleResult = scheduleLoadedClientChunkRenderChunks(
                        accessor,
                        viewFrustum,
                        targetWorld,
                        chunk,
                        refresh.chunkX,
                        refresh.chunkZ,
                        refresh.nextSectionY,
                        MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME
                );
                refresh.nextSectionY = scheduleResult.nextSectionY;
                refresh.coveredSections += scheduleResult.coveredSections;
                if (scheduleResult.completed && refresh.coveredSections < scheduleResult.requiredSections) {
                    refresh.nextSectionY = 0;
                    refresh.coveredSections = 0;
                }
                if (scheduleResult.scheduledChunks > 0) {
                    accessor.ausm$setDisplayListEntitiesDirty(true);
                }
            }

            if (isPipelineActive && !refresh.shadowRefreshed && !NothiriumBypass.shouldBypass()) {
                nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
                refresh.shadowRefreshed = true;
            }
        }

        logClientChunkRenderRefresh(refresh, loaded, scheduleResult.scheduledChunks);
        if (loaded && scheduleResult.completed && scheduleResult.coveredSections >= scheduleResult.requiredSections) {
            rememberCompletedClientChunkRenderRefresh(refresh.world, refresh.chunkX, refresh.chunkZ);
        }
        return !loaded || !scheduleResult.completed || refresh.coveredSections < scheduleResult.requiredSections;
    }

    private ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunks(RenderGlobalAccessor renderGlobal,
                                                                                 ViewFrustum viewFrustum,
                                                                                 World world, Chunk chunk,
                                                                                 int chunkX, int chunkZ,
                                                                                 int startSectionY,
                                                                                 int sectionBudget) {
        int requiredSections = countNonEmptyClientChunkSections(chunk);
        if (requiredSections == 0) {
            return new ClientChunkRenderScheduleResult(0, 0, 0, true, requiredSections);
        }
        if (renderGlobal == null || viewFrustum == null || viewFrustum.renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        if (viewFrustum instanceof ViewFrustumAccessor accessor) {
            return scheduleLoadedClientChunkRenderChunksIndexed(accessor, viewFrustum.renderChunks,
                    chunksToUpdate, world, chunk, chunkX, chunkZ, requiredSections, startSectionY, sectionBudget);
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int sectionCount = sections != null ? sections.length : 0;
        int start = clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            RenderChunk renderChunk = findRenderChunkForSection(viewFrustum.renderChunks, chunkX, chunkZ, sectionY);
            processed++;
            if (renderChunk == null) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            covered++;
            assignRenderChunkWorld(renderChunk, world);
            if (renderChunk.needsUpdate() || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            renderChunk.setNeedsUpdate(true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }

    private ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunksIndexed(ViewFrustumAccessor viewFrustum,
                                                                                        RenderChunk[] renderChunks,
                                                                                        Set<RenderChunk> chunksToUpdate,
                                                                                        World world,
                                                                                        Chunk chunk,
                                                                                        int chunkX,
                                                                                        int chunkZ,
                                                                                        int requiredSections,
                                                                                        int startSectionY,
                                                                                        int sectionBudget) {
        int countX = viewFrustum.ausm$countChunksX();
        int countY = viewFrustum.ausm$countChunksY();
        int countZ = viewFrustum.ausm$countChunksZ();
        if (countX <= 0 || countY <= 0 || countZ <= 0 || renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        int xIndex = MathHelper.intFloorDiv(chunkX, countX);
        xIndex = chunkX - xIndex * countX;
        if (xIndex < 0) {
            xIndex += countX;
        }
        int zIndex = MathHelper.intFloorDiv(chunkZ, countZ);
        zIndex = chunkZ - zIndex * countZ;
        if (zIndex < 0) {
            zIndex += countZ;
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        int sectionCount = Math.min(countY, sections != null ? sections.length : 0);
        int start = clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            int index = (zIndex * countY + sectionY) * countX + xIndex;
            if (index < 0 || index >= renderChunks.length) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }

            RenderChunk renderChunk = renderChunks[index];
            BlockPos position = renderChunk != null ? renderChunk.getPosition() : null;
            if (renderChunk == null
                    || position == null
                    || (position.getX() >> 4) != chunkX
                    || (position.getZ() >> 4) != chunkZ
                    || !shouldScheduleLoadedClientRenderChunk(renderChunk, chunk, position)) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            processed++;
            covered++;
            assignRenderChunkWorld(renderChunk, world);
            if (renderChunk.needsUpdate() || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            renderChunk.setNeedsUpdate(true);
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }

    private int countNonEmptyClientChunkSections(Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        if (sections == null) {
            return 0;
        }
        int count = 0;
        for (ExtendedBlockStorage section : sections) {
            if (section != null && !section.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean shouldScheduleLoadedClientRenderChunk(RenderChunk renderChunk, Chunk chunk, BlockPos position) {
        if (renderChunk == null || chunk == null || position == null) {
            return false;
        }

        int sectionY = position.getY() >> 4;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        if (sectionY < 0 || sections == null || sectionY >= sections.length) {
            return false;
        }

        ExtendedBlockStorage section = sections[sectionY];
        return section != null && !section.isEmpty();
    }

    private static int maxClientChunkRefreshSections(int sectionBudget) {
        return Math.max(1, sectionBudget);
    }

    private static int clampSectionCursor(int sectionY, int sectionCount) {
        if (sectionY < 0 || sectionY >= sectionCount) {
            return 0;
        }
        return sectionY;
    }

    private static boolean hasNonEmptyClientChunkSection(ExtendedBlockStorage[] sections, int sectionY) {
        return sections != null
                && sectionY >= 0
                && sectionY < sections.length
                && sections[sectionY] != null
                && !sections[sectionY].isEmpty();
    }

    private RenderChunk findRenderChunkForSection(RenderChunk[] renderChunks, int chunkX, int chunkZ, int sectionY) {
        if (renderChunks == null) {
            return null;
        }
        for (RenderChunk renderChunk : renderChunks) {
            BlockPos position = renderChunk != null ? renderChunk.getPosition() : null;
            if (position != null
                    && (position.getX() >> 4) == chunkX
                    && (position.getZ() >> 4) == chunkZ
                    && (position.getY() >> 4) == sectionY) {
                return renderChunk;
            }
        }
        return null;
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
        if (!AusmBloomLayer.isAvailable()) {
            return;
        }
        pendingBloomTerrainRefreshAttempts = Math.max(pendingBloomTerrainRefreshAttempts, 3);
        if (pendingBloomTerrainRefreshDelay <= 0) {
            pendingBloomTerrainRefreshDelay = 1;
        }
        pendingBloomTerrainRefreshReason = reason != null && !reason.isEmpty() ? reason : "unspecified";
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
        Minecraft mc = Minecraft.getMinecraft();
        int dimension = mc != null && mc.world != null ? safeDimensionId(mc.world) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationDimension != Integer.MIN_VALUE
                && pendingWorldLoadLightRecalculationDimension != dimension) {
            clearScheduledWorldLoadLightRecalculation();
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
            pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
            MainMod.LOGGER.info("[Lighting] Refreshed scheduled world-load light state.");
        }
    }

    public void runRenderDistanceChangeCheck() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null || mc.gameSettings == null) {
            lastObservedRenderDistanceChunks = -1;
            return;
        }

        int renderDistanceChunks = mc.gameSettings.renderDistanceChunks;
        if (renderDistanceChunks <= 0) {
            return;
        }
        if (lastObservedRenderDistanceChunks < 0) {
            lastObservedRenderDistanceChunks = renderDistanceChunks;
            return;
        }
        if (lastObservedRenderDistanceChunks == renderDistanceChunks) {
            return;
        }

        int previousRenderDistanceChunks = lastObservedRenderDistanceChunks;
        lastObservedRenderDistanceChunks = renderDistanceChunks;
        MainMod.LOGGER.info("[Pipeline] Render distance changed: old={} new={}; forcing terrain renderer reload.",
                previousRenderDistanceChunks,
                renderDistanceChunks);
        forceRenderDistanceTerrainReload(mc, previousRenderDistanceChunks, renderDistanceChunks);
        scheduleWorldLoadLightRecalculation();
    }

    private void forceRenderDistanceTerrainReload(Minecraft mc, int previousRenderDistanceChunks, int renderDistanceChunks) {
        if (mc == null || mc.world == null || mc.renderGlobal == null) {
            return;
        }

        clearScheduledWorldTerrainRefresh();
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        deleteCachedVanillaTerrainRenderers();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        resetCameraFrustumSyncState();
        boolean nothiriumRecreated = NothiriumBypass.recreateRenderer();
        mc.renderGlobal.loadRenderers();
        rebuildMainWorldVanillaViewFrustum(mc.renderGlobal, mc.world, "render-distance-change");
        NothiriumBypass.markAllChanged();
        scheduleInactiveVanillaRecoveryFrame();
        MainMod.LOGGER.info("[Pipeline] Forced terrain renderer reload for render distance change: world={} old={} new={} nothiriumRecreated={}",
                safeDimensionId(mc.world),
                previousRenderDistanceChunks,
                renderDistanceChunks,
                nothiriumRecreated);
    }

    public void runScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive() && mc != null) {
            BetterPortalsCompat.keepMainViewSwapRecoveryAlive(mc.world);
        }
        if (pendingWorldTerrainRefreshDelay > 0) {
            logTerrainDiagnostic("run-world-terrain:delay",
                    mc != null ? mc.world : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshDelay--;
            return;
        }

        logTerrainDiagnostic("run-world-terrain:start",
                mc != null ? mc.world : null,
                "attempts=" + pendingWorldTerrainRefreshAttempts);
        if (refreshWorldTerrainState()) {
            pendingWorldTerrainRefreshAttempts--;
        }

        if (pendingWorldTerrainRefreshAttempts <= 0) {
            logTerrainDiagnostic("run-world-terrain:done",
                    mc != null ? mc.world : null,
                    "");
            clearScheduledWorldTerrainRefresh();
        } else {
            pendingWorldTerrainRefreshDelay = WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES;
            logTerrainDiagnostic("run-world-terrain:reschedule",
                    mc != null ? mc.world : null,
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
        int radius = Math.max(64, Math.min(512, (mc.gameSettings.renderDistanceChunks * 16) + 16));
        mc.world.markBlockRangeForRenderUpdate(
                center.getX() - radius,
                0,
                center.getZ() - radius,
                center.getX() + radius,
                255,
                center.getZ() + radius
        );
        boolean nothiriumDirty = NothiriumBypass.markAllChanged();
        
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

        ensureVanillaTerrainRenderer(mc.world, true);
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
        preparePassesRenderedBeforeShadowThisFrame = false;
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
        unbindShaderStorageBuffers(true);
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
        probeShaderlessLightState("restore-vanilla-lightmap-texture");
    }

    private void refreshVanillaLightmap(Minecraft mc) {
        if (mc == null || mc.world == null || mc.player == null || mc.entityRenderer == null) {
            return;
        }
        probeShaderlessLightState("refresh-vanilla-lightmap-before");
        EntityRendererAccessor accessor = (EntityRendererAccessor) mc.entityRenderer;
        accessor.ausm$setLightmapUpdateNeeded(true);
        accessor.ausm$updateLightmap(mc.getRenderPartialTicks());
        restoreVanillaLightmapTexture(mc);
        probeShaderlessLightState("refresh-vanilla-lightmap-after");
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

    private static void markShaderStorageBuffersBound() {
        if (GLContext.getCapabilities().OpenGL43) {
            shaderStorageBuffersKnownUnbound = false;
        }
    }

    private void unbindShaderStorageBuffers() {
        unbindShaderStorageBuffers(false);
    }

    private void unbindShaderStorageBuffers(boolean force) {
        if (!GLContext.getCapabilities().OpenGL43) {
            return;
        }
        if (!force && shaderStorageBuffersKnownUnbound) {
            return;
        }

        if (force || !shaderStorageBuffers.active()) {
            int maxBindings = maxShaderStorageBufferBindings();
            for (int index = 0; index < maxBindings; index++) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        } else {
            for (int index : shaderStorageBuffers.bindingIndices()) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        shaderStorageBuffersKnownUnbound = true;
    }

    private static int maxShaderStorageBufferBindings() {
        if (maxShaderStorageBufferBindings < 0) {
            maxShaderStorageBufferBindings = Math.max(0, GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
        }
        return maxShaderStorageBufferBindings;
    }

    private static void disablePipelineVertexAttributes() {
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    private static void restoreVanillaClientRenderState() {
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glDepthRange(0.0D, 1.0D);
        GL11.glClearDepth(1.0D);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);

        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
    }

    private void loadCustomTextures(ShaderPack pack, ShaderProperties properties) {
        Map<String, Integer> loadedByPath = new HashMap<>();
        Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures = new HashMap<>();
        Map<String, Integer> customUnitsBySampler = new HashMap<>();
        Set<String> failedTexturePaths = new HashSet<>();
        int[] nextCustomUnit = {com.l.ausm.impl.pipeline.shader.ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT};
        Minecraft mc = Minecraft.getMinecraft();

        for (RenderPass pass : RenderPass.values()) {
            List<LoadedCustomTexture> textures = loadCustomTextureList(
                    pack,
                    mc,
                    packDirectives.textureDirectives().rawTexturesFor(pass.programId()),
                    packDirectives.textureDirectives().texturesFor(pass.programId()),
                    loadedByPath,
                    loadedRawTextures,
                    customUnitsBySampler,
                    failedTexturePaths,
                    nextCustomUnit,
                    "pass " + pass.getProgramName()
            );
            if (!textures.isEmpty()) {
                customTextures.put(pass, List.copyOf(textures));
            }
        }

        java.util.LinkedHashSet<ShaderProgramArrayKey> arrayTextureKeys = new java.util.LinkedHashSet<>();
        arrayTextureKeys.addAll(packDirectives.textureDirectives().programArrayRawTextures().keySet());
        arrayTextureKeys.addAll(packDirectives.textureDirectives().programArrayTextures().keySet());
        for (Map.Entry<ProgramArrayId, List<FullscreenArrayProgram>> entry : fullscreenArrayPrograms.entrySet()) {
            for (FullscreenArrayProgram program : entry.getValue()) {
                arrayTextureKeys.add(new ShaderProgramArrayKey(entry.getKey(), program.index()));
            }
        }
        for (ShaderProgramArrayKey key : arrayTextureKeys) {
            List<LoadedCustomTexture> textures = loadCustomTextureList(
                    pack,
                    mc,
                    packDirectives.textureDirectives().rawTexturesFor(key.arrayId(), key.index()),
                    packDirectives.textureDirectives().texturesFor(key.arrayId(), key.index()),
                    loadedByPath,
                    loadedRawTextures,
                    customUnitsBySampler,
                    failedTexturePaths,
                    nextCustomUnit,
                    "program array " + key.arrayId().sourcePrefix() + (key.index() == 0 ? "" : key.index())
            );
            if (!textures.isEmpty()) {
                customArrayTextures.put(key, List.copyOf(textures));
            }
        }
    }

    private List<LoadedCustomTexture> loadCustomTextureList(
            ShaderPack pack,
            Minecraft mc,
            List<ShaderRawTextureDirective> rawDirectives,
            List<ShaderCustomTextureBinding> bindings,
            Map<String, Integer> loadedByPath,
            Map<ShaderRawTextureDirective, ShaderTextureLoader.RawTexture> loadedRawTextures,
            Map<String, Integer> customUnitsBySampler,
            Set<String> failedTexturePaths,
            int[] nextCustomUnit,
            String owner
    ) {
        List<LoadedCustomTexture> textures = new ArrayList<>();
        for (var directive : rawDirectives) {
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
        for (var binding : bindings) {
            int textureUnit = TextureBinder.textureUnitForSampler(binding.samplerName());
            if (textureUnit < 0) {
                textureUnit = customUnitsBySampler.computeIfAbsent(binding.samplerName(), ignored -> nextCustomUnit[0]++);
            }

            int atlasTexture = minecraftBlockAtlasTexture(mc, binding.resourcePath());
            if (atlasTexture > 0) {
                textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, atlasTexture, GL11.GL_TEXTURE_2D, false));
                MainMod.LOGGER.debug(
                        "[ShaderTextures] Prepared Minecraft block atlas for sampler '{}' on unit {} in {} as texture {}",
                        binding.samplerName(),
                        textureUnit,
                        owner,
                        atlasTexture
                );
                continue;
            }

            try {
                String textureCacheKey = binding.resourcePath() + "|" + binding.blur() + "|" + binding.clamp();
                int textureId = loadedByPath.computeIfAbsent(textureCacheKey, ignored -> {
                    try {
                        return ShaderTextureLoader.loadTexture(pack, binding.resourcePath(), binding.blur(), binding.clamp());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
                textures.add(new LoadedCustomTexture(binding.samplerName(), binding.samplerName(), binding.resourcePath(), textureUnit, textureId, GL11.GL_TEXTURE_2D, true));
                MainMod.LOGGER.debug(
                        "[ShaderTextures] Prepared {} for sampler '{}' on unit {} in {} as texture {}",
                        binding.resourcePath(),
                        binding.samplerName(),
                        textureUnit,
                        owner,
                        textureId
                );
            } catch (UncheckedIOException e) {
                if (failedTexturePaths.add(binding.resourcePath())) {
                    MainMod.LOGGER.warn("[ShaderTextures] Failed to load {}", binding.resourcePath(), e.getCause());
                }
            }
        }
        return textures;
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
        bindCustomTextures(textures, program);
    }

    private void bindCustomTextures(ProgramArrayId arrayId, int index, ShaderProgram program) {
        List<LoadedCustomTexture> textures = customArrayTextures.get(new ShaderProgramArrayKey(arrayId, index));
        bindCustomTextures(textures, program);
    }

    private void bindCustomTextures(List<LoadedCustomTexture> textures, ShaderProgram program) {
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
        Stream.concat(customTextures.values().stream(), customArrayTextures.values().stream())
                .flatMap(List::stream)
                .filter(LoadedCustomTexture::deleteOnCleanup)
                .mapToInt(LoadedCustomTexture::textureId)
                .distinct()
                .forEach(GL11::glDeleteTextures);
        customTextures.clear();
        customArrayTextures.clear();
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
        private String reason;
        private int attemptsRemaining;
        private int delayFrames;
        private int nextSectionY;
        private int coveredSections;
        private boolean shadowRefreshed;

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

    private static final class ClientChunkRenderScheduleResult {
        private final int scheduledChunks;
        private final int coveredSections;
        private final int nextSectionY;
        private final boolean completed;
        private final int requiredSections;

        private ClientChunkRenderScheduleResult(int scheduledChunks, int coveredSections, int nextSectionY,
                                                boolean completed, int requiredSections) {
            this.scheduledChunks = scheduledChunks;
            this.coveredSections = coveredSections;
            this.nextSectionY = nextSectionY;
            this.completed = completed;
            this.requiredSections = requiredSections;
        }

        private static ClientChunkRenderScheduleResult empty() {
            return new ClientChunkRenderScheduleResult(0, 0, 0, false, 1);
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
                new ShaderBlockIdMap.BlockIdRules(Map.of(), List.of(), Map.of()),
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
                        null,
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
                                null,
                                Map.of(),
                                CustomUniformSet.empty()
                        )),
                        Map.of(),
                        CustomUniformSet.empty()
                ),
                ShaderOitSettings.empty(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }
}
