package com.l.ausm.impl.util;

import com.google.common.util.concurrent.ListenableFuture;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.client.renderer.color.ItemColors;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.PacketBuffer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.block.properties.IProperty;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MinecraftReflectionCompat {
    private static final ConcurrentMap<MethodKey, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<MethodKey> MISSING_METHODS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<MethodLookupCache> THREAD_METHOD_LOOKUP_CACHE =
            ThreadLocal.withInitial(MethodLookupCache::new);
    private static final ConcurrentMap<FieldKey, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Set<FieldKey> MISSING_FIELDS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<StateValueMethodKey, Method> STATE_VALUE_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<StateValueMethodKey> MISSING_STATE_VALUE_METHODS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<IdentityKey<IBlockState>, Block> STATE_BLOCK_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<IdentityKey<IBlockState>, Map<IProperty<?>, Comparable<?>>> STATE_PROPERTIES_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<IProperty<?>, String> PROPERTY_NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<PropertyValueNameKey, String> PROPERTY_VALUE_NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Block, ResourceLocation> BLOCK_REGISTRY_NAME_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, String> RESOURCE_NAMESPACE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, String> RESOURCE_PATH_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, String> RESOURCE_STRING_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<ResourceLocation, String> RESOURCE_PATH_LOWER_CACHE = new ConcurrentHashMap<>();
    private static final int HOT_IDENTITY_CACHE_LIMIT = 4096;
    private static final ThreadLocal<IdentityHashMap<IBlockState, Block>> THREAD_STATE_BLOCK_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static final ThreadLocal<IdentityHashMap<IBlockState, String>> THREAD_STATE_STRING_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    public static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final String[] PROVIDER_DIMENSION_NAMES = {"getDimension"};
    private static final String[] PROVIDER_DIMENSION_TYPE_NAMES = {"func_186058_p", "getDimensionType"};
    private static final String[] ITEM_STACK_ITEM_NAMES = {"func_77973_b", "getItem"};
    private static final String[] BLOCK_RENDER_LAYER_NAMES = {"func_180664_k", "getRenderLayer"};
    private static final String[] RENDER_PARTIAL_TICKS_NAMES = {"func_184121_ak", "getRenderPartialTicks"};
    private static final String[] GL_UNIFORM_1I_NAMES = {"func_153163_f", "glUniform1i"};
    private static final String[] GL_DISABLE_LIGHTING_NAMES = {"func_179140_f", "disableLighting"};
    private static final String[] BUFFER_VERTEX_FORMAT_NAMES = {"func_178973_g", "getVertexFormat"};
    private static final String[] TILE_ENTITY_POS_NAMES = {"func_174877_v", "getPos"};
    private static final String[] TILE_ENTITY_INVALID_NAMES = {"func_145837_r", "isInvalid"};
    private static final String[] TILE_ENTITY_RENDER_NAMES = {"func_192854_a", "render"};
    private static final String[] CAMERA_FRUSTUM_NAMES = {"func_78546_a", "isBoundingBoxInFrustum"};
    private static final Class<?>[] INT_INT_PARAMETERS = {int.class, int.class};
    private static final Class<?>[] TILE_ENTITY_RENDER_PARAMETERS = {
            TileEntity.class, double.class, double.class, double.class, float.class, int.class, float.class
    };
    private static final Class<?>[] AXIS_ALIGNED_BB_PARAMETERS = {net.minecraft.util.math.AxisAlignedBB.class};
    private static final Field VEC_X_FIELD = firstField(Vec3d.class, "field_72450_a", "x");
    private static final Field VEC_Y_FIELD = firstField(Vec3d.class, "field_72448_b", "y");
    private static final Field VEC_Z_FIELD = firstField(Vec3d.class, "field_72449_c", "z");
    private static final MethodHandle CURRENT_RENDER_LAYER_HANDLE = staticMethodHandle(
            net.minecraftforge.client.MinecraftForgeClient.class,
            new String[] {"getRenderLayer"},
            NO_PARAMETERS
    );
    private static final MethodHandle BLOCK_FROM_STATE_HANDLE = methodHandle(
            IBlockState.class,
            new String[] {"func_177230_c", "getBlock"},
            NO_PARAMETERS
    );
    private static final MethodHandle STATE_ACTUAL_STATE_HANDLE = methodHandle(
            IBlockState.class,
            new String[] {"func_185899_b", "getActualState"},
            new Class<?>[] {IBlockAccess.class, BlockPos.class}
    );

    private MinecraftReflectionCompat() {
    }

    public static Minecraft minecraft() {
        try {
            return Minecraft.getMinecraft();
        } catch (Throwable ignored) {
        }
        return callStatic(Minecraft.class, Minecraft.class, null, new String[] {"func_71410_x", "getMinecraft"}, NO_PARAMETERS);
    }

    public static File gameDir(Minecraft minecraft) {
        return field(minecraft, File.class, new File("."), "field_71412_D", "gameDir");
    }

    public static GuiScreen currentScreen(Minecraft minecraft) {
        return field(minecraft, GuiScreen.class, null, "field_71462_r", "currentScreen");
    }

    public static WorldProvider worldProvider(World world) {
        return field(world, WorldProvider.class, null, "field_73011_w", "provider");
    }

    public static int providerDimension(WorldProvider provider) {
        int direct = callInt(provider, PROVIDER_DIMENSION_NAMES, NO_PARAMETERS, Integer.MIN_VALUE);
        if (direct != Integer.MIN_VALUE) {
            return direct;
        }
        Object dimensionType = invoke(provider, PROVIDER_DIMENSION_TYPE_NAMES, NO_PARAMETERS);
        int dimensionTypeId = dimensionTypeId(dimensionType);
        if (dimensionTypeId != Integer.MIN_VALUE) {
            return dimensionTypeId;
        }
        return fieldInt(provider, Integer.MIN_VALUE, "field_76574_g", "dimension");
    }

    public static boolean providerHasSkyLight(WorldProvider provider) {
        return callBoolean(provider, new String[] {"func_191066_m", "hasSkyLight"}, NO_PARAMETERS,
                fieldBoolean(provider, false, "field_191067_f", "hasSkyLight"));
    }

    private static int dimensionTypeId(Object dimensionType) {
        if (dimensionType == null) {
            return Integer.MIN_VALUE;
        }
        int id = callInt(dimensionType, new String[] {"func_186068_a", "getId"}, NO_PARAMETERS, Integer.MIN_VALUE);
        if (id != Integer.MIN_VALUE) {
            return id;
        }
        return fieldInt(dimensionType, Integer.MIN_VALUE, "field_186074_d", "id");
    }

    public static ExtendedBlockStorage[] chunkBlockStorageArray(Chunk chunk) {
        return call(chunk, ExtendedBlockStorage[].class, null,
                new String[] {"func_76587_i", "getBlockStorageArray"}, NO_PARAMETERS);
    }

    public static boolean blockStorageEmpty(ExtendedBlockStorage section) {
        return section == null || callBoolean(section, new String[] {"func_76663_a", "isEmpty"}, NO_PARAMETERS, false);
    }

    public static boolean worldIsBlockLoaded(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        try {
            return world.isBlockLoaded(pos);
        } catch (Throwable ignored) {
            return callBoolean(world, new String[] {"func_175667_e", "isBlockLoaded"},
                    new Class<?>[] {BlockPos.class}, false, pos);
        }
    }

    public static boolean worldIsBlockLoaded(World world, BlockPos pos, boolean allowEmpty) {
        if (world == null || pos == null) {
            return false;
        }
        try {
            return world.isBlockLoaded(pos, allowEmpty);
        } catch (Throwable ignored) {
            return callBoolean(world, new String[] {"func_175668_a", "isBlockLoaded"},
                    new Class<?>[] {BlockPos.class, boolean.class}, false, pos, allowEmpty);
        }
    }

    public static boolean worldCanSeeSky(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        return callBoolean(world, new String[] {"func_175678_i", "canSeeSky"},
                new Class<?>[] {BlockPos.class}, false, pos);
    }

    public static long worldTime(World world) {
        return callLong(world, new String[] {"func_72820_D", "getWorldTime"}, NO_PARAMETERS, 0L);
    }

    public static float worldRainStrength(World world, float partialTicks) {
        return callFloat(world, new String[] {"func_72867_j", "getRainStrength"},
                new Class<?>[] {float.class}, 0.0F, partialTicks);
    }

    public static float worldThunderStrength(World world, float partialTicks) {
        return callFloat(world, new String[] {"func_72819_i", "getThunderStrength"},
                new Class<?>[] {float.class}, 0.0F, partialTicks);
    }

    public static float worldCelestialAngle(World world, float partialTicks) {
        return callFloat(world, new String[] {"func_72826_c", "getCelestialAngle"},
                new Class<?>[] {float.class}, 0.0F, partialTicks);
    }

    public static int blockAccessCombinedLight(IBlockAccess access, BlockPos pos, int lightValue) {
        return callInt(access, new String[] {"func_175626_b", "getCombinedLight"},
                new Class<?>[] {BlockPos.class, int.class}, 0, pos, lightValue);
    }

    public static IBlockState blockAccessBlockState(IBlockAccess access, BlockPos pos) {
        return call(access, IBlockState.class, airDefaultState(), new String[] {"func_180495_p", "getBlockState"},
                new Class<?>[] {BlockPos.class}, pos);
    }

    public static boolean blockAccessIsAirBlock(IBlockAccess access, BlockPos pos) {
        return callBoolean(access, new String[] {"func_175623_d", "isAirBlock"},
                new Class<?>[] {BlockPos.class}, false, pos);
    }

    public static Biome blockAccessBiome(IBlockAccess access, BlockPos pos) {
        return call(access, Biome.class, null, new String[] {"func_180494_b", "getBiome"},
                new Class<?>[] {BlockPos.class}, pos);
    }

    public static int blockAccessStrongPower(IBlockAccess access, BlockPos pos, EnumFacing direction) {
        return callInt(access, new String[] {"func_175627_a", "getStrongPower"},
                new Class<?>[] {BlockPos.class, EnumFacing.class}, 0, pos, direction);
    }

    public static boolean blockAccessIsSideSolid(IBlockAccess access, BlockPos pos, EnumFacing side, boolean fallback) {
        return callBoolean(access, new String[] {"isSideSolid"},
                new Class<?>[] {BlockPos.class, EnumFacing.class, boolean.class}, fallback, pos, side, fallback);
    }

    public static WorldType blockAccessWorldType(IBlockAccess access) {
        return call(access, WorldType.class, WorldType.DEFAULT, new String[] {"func_175624_G", "getWorldType"}, NO_PARAMETERS);
    }

    public static int worldLightFor(World world, EnumSkyBlock skyBlock, BlockPos pos) {
        return callInt(world, new String[] {"func_175642_b", "getLightFor"},
                new Class<?>[] {EnumSkyBlock.class, BlockPos.class}, 0, skyBlock, pos);
    }

    public static TileEntity blockAccessTileEntity(IBlockAccess access, BlockPos pos) {
        return call(access, TileEntity.class, null, new String[] {"func_175625_s", "getTileEntity"},
                new Class<?>[] {BlockPos.class}, pos);
    }

    @SuppressWarnings("unchecked")
    public static List<TileEntity> worldLoadedTileEntities(World world) {
        Object value = getField(world, "field_147482_g", "loadedTileEntityList");
        if (value instanceof List<?>) {
            return (List<TileEntity>) value;
        }
        return Collections.emptyList();
    }

    public static int blockPosX(BlockPos pos) {
        return pos != null ? pos.getX() : 0;
    }

    public static int blockPosY(BlockPos pos) {
        return pos != null ? pos.getY() : 0;
    }

    public static int blockPosZ(BlockPos pos) {
        return pos != null ? pos.getZ() : 0;
    }

    public static BlockPos blockPosToImmutable(BlockPos pos) {
        return pos != null ? pos.toImmutable() : BlockPos.ORIGIN;
    }

    public static long blockPosToLong(BlockPos pos) {
        if (pos == null) {
            return 0L;
        }
        try {
            return pos.toLong();
        } catch (Throwable ignored) {
        }
        return ((long) blockPosX(pos) & 0x3FFFFFFL) << 38
                | ((long) blockPosZ(pos) & 0x3FFFFFFL) << 12
                | ((long) blockPosY(pos) & 0xFFFL);
    }

    public static BlockPos blockPosUp(BlockPos pos) {
        if (pos == null) {
            return BlockPos.ORIGIN.up();
        }
        try {
            return pos.up();
        } catch (Throwable ignored) {
        }
        Object value = invoke(pos, new String[] {"func_177984_a", "up"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : new BlockPos(blockPosX(pos), blockPosY(pos) + 1, blockPosZ(pos));
    }

    public static void mutableBlockPosSet(BlockPos.MutableBlockPos pos, int x, int y, int z) {
        Object value = invoke(pos, new String[] {"func_181079_c", "setPos"},
                new Class<?>[] {int.class, int.class, int.class}, x, y, z);
        if (value == null) {
            setField(pos, x, "field_177997_b", "field_177962_a", "x");
            setField(pos, y, "field_177998_c", "field_177960_b", "y");
            setField(pos, z, "field_177996_d", "field_177961_c", "z");
        }
    }

    public static IBlockState worldBlockState(World world, BlockPos pos) {
        return call(world, IBlockState.class, null, new String[] {"func_180495_p", "getBlockState"},
                new Class<?>[] {BlockPos.class}, pos);
    }

    public static void worldMarkBlockRangeForRenderUpdate(World world, BlockPos from, BlockPos to) {
        invoke(world, new String[] {"func_175704_b", "markBlockRangeForRenderUpdate"},
                new Class<?>[] {BlockPos.class, BlockPos.class}, from, to);
    }

    public static void worldMarkBlockRangeForRenderUpdate(World world, int minX, int minY, int minZ,
                                                          int maxX, int maxY, int maxZ) {
        invoke(world, new String[] {"func_147458_c", "markBlockRangeForRenderUpdate"},
                new Class<?>[] {int.class, int.class, int.class, int.class, int.class, int.class},
                minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static int facingXOffset(EnumFacing facing) {
        return intValue(invoke(facing, new String[] {"func_82601_c", "getXOffset"}, NO_PARAMETERS), 0);
    }

    public static int facingYOffset(EnumFacing facing) {
        return intValue(invoke(facing, new String[] {"func_96559_d", "getYOffset"}, NO_PARAMETERS), 0);
    }

    public static int facingZOffset(EnumFacing facing) {
        return intValue(invoke(facing, new String[] {"func_82599_e", "getZOffset"}, NO_PARAMETERS), 0);
    }

    public static EnumFacing.Axis facingAxis(EnumFacing facing) {
        Object value = invoke(facing, new String[] {"func_176740_k", "getAxis"}, NO_PARAMETERS);
        if (value instanceof EnumFacing.Axis) {
            return (EnumFacing.Axis) value;
        }
        if (facingYOffset(facing) != 0) {
            return EnumFacing.Axis.Y;
        }
        return facingXOffset(facing) != 0 ? EnumFacing.Axis.X : EnumFacing.Axis.Z;
    }

    public static EnumFacing.AxisDirection facingAxisDirection(EnumFacing facing) {
        Object value = invoke(facing, new String[] {"func_176743_c", "getAxisDirection"}, NO_PARAMETERS);
        if (value instanceof EnumFacing.AxisDirection) {
            return (EnumFacing.AxisDirection) value;
        }
        int offset = facingXOffset(facing) + facingYOffset(facing) + facingZOffset(facing);
        return offset >= 0 ? EnumFacing.AxisDirection.POSITIVE : EnumFacing.AxisDirection.NEGATIVE;
    }

    public static EnumFacing facingRotateAround(EnumFacing facing, EnumFacing.Axis axis) {
        Object value = invoke(facing, new String[] {"func_176732_a", "rotateAround"},
                new Class<?>[] {EnumFacing.Axis.class}, axis);
        return value instanceof EnumFacing ? (EnumFacing) value : facing;
    }

    public static EnumFacing facingOpposite(EnumFacing facing) {
        Object value = invoke(facing, new String[] {"func_176734_d", "getOpposite"}, NO_PARAMETERS);
        if (value instanceof EnumFacing) {
            return (EnumFacing) value;
        }
        if (facing == EnumFacing.DOWN) return EnumFacing.UP;
        if (facing == EnumFacing.UP) return EnumFacing.DOWN;
        if (facing == EnumFacing.NORTH) return EnumFacing.SOUTH;
        if (facing == EnumFacing.SOUTH) return EnumFacing.NORTH;
        if (facing == EnumFacing.WEST) return EnumFacing.EAST;
        return EnumFacing.WEST;
    }

    public static IBlockState blockStateAtEntityViewpoint(World world, Entity entity, float partialTicks) {
        Object value = invoke(ActiveRenderInfo.class, new String[] {"func_186703_a", "getBlockStateAtEntityViewpoint"},
                new Class<?>[] {World.class, Entity.class, float.class}, world, entity, partialTicks);
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Entity> loadedEntityList(World world) {
        Object value = getField(world, "field_72996_f", "loadedEntityList");
        return value instanceof List<?> ? (List<Entity>) value : Collections.emptyList();
    }

    public static int entityId(Entity entity) {
        return intValue(invoke(entity, new String[] {"func_145782_y", "getEntityId"}, NO_PARAMETERS),
                intValue(getField(entity, "field_145783_c", "entityId"), 0));
    }

    public static ResourceLocation entityKey(Entity entity) {
        Object value = invokeStatic(EntityList.class, new String[] {"func_191301_a", "getKey"}, new Class<?>[] {Entity.class}, entity);
        return value instanceof ResourceLocation ? (ResourceLocation) value : null;
    }

    public static double posX(Entity entity) {
        return fieldDouble(entity, 0.0D, "field_70165_t", "posX");
    }

    public static double posY(Entity entity) {
        return fieldDouble(entity, 0.0D, "field_70163_u", "posY");
    }

    public static double posZ(Entity entity) {
        return fieldDouble(entity, 0.0D, "field_70161_v", "posZ");
    }

    public static double lastTickPosX(Entity entity) {
        return fieldDouble(entity, posX(entity), "field_70142_S", "lastTickPosX");
    }

    public static double lastTickPosY(Entity entity) {
        return fieldDouble(entity, posY(entity), "field_70137_T", "lastTickPosY");
    }

    public static double lastTickPosZ(Entity entity) {
        return fieldDouble(entity, posZ(entity), "field_70136_U", "lastTickPosZ");
    }

    public static double prevPosX(Entity entity) {
        return fieldDouble(entity, posX(entity), "field_70169_q", "prevPosX");
    }

    public static double prevPosY(Entity entity) {
        return fieldDouble(entity, posY(entity), "field_70167_r", "prevPosY");
    }

    public static double prevPosZ(Entity entity) {
        return fieldDouble(entity, posZ(entity), "field_70166_s", "prevPosZ");
    }

    public static float eyeHeight(Entity entity) {
        return callFloat(entity, new String[] {"func_70047_e", "getEyeHeight"}, NO_PARAMETERS, 0.0F);
    }

    public static Vec3d positionEyes(Entity entity, float partialTicks) {
        Object value = invoke(entity, new String[] {"func_174824_e", "getPositionEyes"}, new Class<?>[] {float.class}, partialTicks);
        if (value instanceof Vec3d) {
            return (Vec3d) value;
        }
        double x = prevPosX(entity) + (posX(entity) - prevPosX(entity)) * partialTicks;
        double y = prevPosY(entity) + (posY(entity) - prevPosY(entity)) * partialTicks + eyeHeight(entity);
        double z = prevPosZ(entity) + (posZ(entity) - prevPosZ(entity)) * partialTicks;
        return new Vec3d(x, y, z);
    }

    public static double vecX(Vec3d vec) {
        return vecField(vec, VEC_X_FIELD, 0.0D, "field_72450_a", "x");
    }

    public static double vecY(Vec3d vec) {
        return vecField(vec, VEC_Y_FIELD, 0.0D, "field_72448_b", "y");
    }

    public static double vecZ(Vec3d vec) {
        return vecField(vec, VEC_Z_FIELD, 0.0D, "field_72449_c", "z");
    }

    private static double vecField(Vec3d vec, Field field, double fallback, String srgName, String mcpName) {
        if (vec == null) {
            return fallback;
        }
        if (field != null) {
            try {
                return field.getDouble(vec);
            } catch (IllegalAccessException ignored) {
            }
        }
        return fieldDouble(vec, fallback, srgName, mcpName);
    }

    public static Vec3d vecAdd(Vec3d vec, Vec3d other) {
        return new Vec3d(vecX(vec) + vecX(other), vecY(vec) + vecY(other), vecZ(vec) + vecZ(other));
    }

    public static Vec3d vecSubtract(Vec3d vec, double x, double y, double z) {
        return new Vec3d(vecX(vec) - x, vecY(vec) - y, vecZ(vec) - z);
    }

    public static Vec3d vecScale(Vec3d vec, double scale) {
        return new Vec3d(vecX(vec) * scale, vecY(vec) * scale, vecZ(vec) * scale);
    }

    public static Vec3d vecNormalize(Vec3d vec) {
        double x = vecX(vec);
        double y = vecY(vec);
        double z = vecZ(vec);
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-4D ? new Vec3d(0.0D, 0.0D, 0.0D) : new Vec3d(x / length, y / length, z / length);
    }

    public static double vecDistance(Vec3d from, Vec3d to) {
        double x = vecX(from) - vecX(to);
        double y = vecY(from) - vecY(to);
        double z = vecZ(from) - vecZ(to);
        return Math.sqrt(x * x + y * y + z * z);
    }

    public static float rotationYaw(Entity entity) {
        return fieldFloat(entity, 0.0F, "field_70177_z", "rotationYaw");
    }

    public static float prevRotationYaw(Entity entity) {
        return fieldFloat(entity, rotationYaw(entity), "field_70126_B", "prevRotationYaw");
    }

    public static float rotationPitch(Entity entity) {
        return fieldFloat(entity, 0.0F, "field_70125_A", "rotationPitch");
    }

    public static float prevRotationPitch(Entity entity) {
        return fieldFloat(entity, rotationPitch(entity), "field_70127_C", "prevRotationPitch");
    }

    public static Vec3d look(Entity entity, float partialTicks) {
        Object value = invoke(entity, new String[] {"func_70676_i", "getLook"}, new Class<?>[] {float.class}, partialTicks);
        if (value instanceof Vec3d) {
            return (Vec3d) value;
        }
        float pitch = prevRotationPitch(entity) + (rotationPitch(entity) - prevRotationPitch(entity)) * partialTicks;
        float yaw = prevRotationYaw(entity) + (rotationYaw(entity) - prevRotationYaw(entity)) * partialTicks;
        double yawRad = -yaw * Math.PI / 180.0D - Math.PI;
        double pitchRad = -pitch * Math.PI / 180.0D;
        double x = Math.sin(yawRad) * -Math.cos(pitchRad);
        double y = Math.sin(pitchRad);
        double z = Math.cos(yawRad) * -Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }

    public static ItemStack heldItemMainhand(EntityLivingBase entity) {
        return call(entity, ItemStack.class, null, new String[] {"func_184614_ca", "getHeldItemMainhand"}, NO_PARAMETERS);
    }

    public static ItemStack heldItemOffhand(EntityLivingBase entity) {
        return call(entity, ItemStack.class, null, new String[] {"func_184592_cb", "getHeldItemOffhand"}, NO_PARAMETERS);
    }

    public static boolean livingPotionActive(EntityLivingBase entity, Potion potion) {
        return potion != null && booleanValue(invoke(entity, new String[] {"func_70644_a", "isPotionActive"},
                new Class<?>[] {Potion.class}, potion));
    }

    public static PotionEffect livingActivePotionEffect(EntityLivingBase entity, Potion potion) {
        if (potion == null) {
            return null;
        }
        Object value = invoke(entity, new String[] {"func_70660_b", "getActivePotionEffect"},
                new Class<?>[] {Potion.class}, potion);
        return value instanceof PotionEffect ? (PotionEffect) value : null;
    }

    public static boolean playerIsSpectator(Entity player) {
        return callBoolean(player, new String[] {"func_175149_v", "isSpectator"}, NO_PARAMETERS, false);
    }

    public static Entity entityRidingEntity(Entity entity) {
        return call(entity, Entity.class, null, new String[] {"func_184187_bx", "getRidingEntity"}, NO_PARAMETERS);
    }

    public static boolean itemStackIsEmpty(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        Object value = invoke(stack, new String[] {"func_190926_b", "isEmpty"}, NO_PARAMETERS);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static Item itemStackItem(ItemStack stack) {
        return call(stack, Item.class, null, ITEM_STACK_ITEM_NAMES, NO_PARAMETERS);
    }

    public static int itemId(Item item) {
        return callInt(Item.class, new String[] {"func_150891_b", "getIdFromItem"},
                new Class<?>[] {Item.class}, 0, item);
    }

    public static int itemStackMetadata(ItemStack stack) {
        return callInt(stack, new String[] {"func_77960_j", "getMetadata"}, NO_PARAMETERS, 0);
    }

    public static IBlockState blockStateFromMeta(Block block, int meta) {
        return call(block, IBlockState.class, blockDefaultState(block),
                new String[] {"func_176203_a", "getStateFromMeta"}, new Class<?>[] {int.class}, meta);
    }

    public static IBlockState blockDefaultState(Block block) {
        return call(block, IBlockState.class, null, new String[] {"func_176223_P", "getDefaultState"}, NO_PARAMETERS);
    }

    public static IBlockState airDefaultState() {
        Block air = field(Blocks.class, Block.class, null, "field_150350_a", "AIR");
        return air != null ? blockDefaultState(air) : null;
    }

    public static int blockMetaFromState(Block block, IBlockState state) {
        if (block == null || state == null) {
            return 0;
        }
        try {
            return block.getMetaFromState(state);
        } catch (Throwable ignored) {
        }
        return callInt(block, new String[] {"func_176201_c", "getMetaFromState"},
                new Class<?>[] {IBlockState.class}, 0, state);
    }

    public static Material stateMaterial(IBlockState state) {
        return state != null ? state.getMaterial() : null;
    }

    public static boolean stateMaterialIsFire(IBlockState state) {
        return stateMaterial(state) == Material.FIRE;
    }

    public static boolean stateMaterialIsWater(IBlockState state) {
        return stateMaterial(state) == Material.WATER;
    }

    public static EnumBlockRenderType stateRenderType(IBlockState state) {
        return state != null ? state.getRenderType() : null;
    }

    public static int stateRenderTypeOrdinal(IBlockState state) {
        EnumBlockRenderType renderType = stateRenderType(state);
        return renderType != null ? renderType.ordinal() : -1;
    }

    public static int stateLightValue(IBlockState state) {
        if (state == null) {
            return 0;
        }
        try {
            return state.getLightValue();
        } catch (Throwable ignored) {
        }
        return callInt(state, new String[] {"func_185906_d", "getLightValue"}, NO_PARAMETERS, 0);
    }

    public static int stateLightValue(IBlockState state, IBlockAccess access, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        if (access != null && pos != null) {
            try {
                return state.getLightValue(access, pos);
            } catch (Throwable ignored) {
            }
        }
        Object value = access != null && pos != null
                ? invoke(state, new String[] {"func_185906_d", "getLightValue"},
                new Class<?>[] {IBlockAccess.class, BlockPos.class}, access, pos)
                : null;
        return value instanceof Number ? ((Number) value).intValue() : stateLightValue(state);
    }

    public static int statePackedLightmapCoords(IBlockState state, IBlockAccess access, BlockPos pos) {
        if (state != null && access != null && pos != null) {
            try {
                return state.getPackedLightmapCoords(access, pos);
            } catch (Throwable ignored) {
                Object value = invoke(state, new String[] {"func_185889_a", "getPackedLightmapCoords"},
                        new Class<?>[] {IBlockAccess.class, BlockPos.class}, access, pos);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
        }
        return blockAccessCombinedLight(access, pos, 0);
    }

    public static BlockRenderLayer currentRenderLayer() {
        if (CURRENT_RENDER_LAYER_HANDLE != null) {
            try {
                return (BlockRenderLayer) CURRENT_RENDER_LAYER_HANDLE.invokeExact();
            } catch (Throwable ignored) {
            }
        }
        return callStatic(net.minecraftforge.client.MinecraftForgeClient.class, BlockRenderLayer.class, null,
                new String[] {"getRenderLayer"}, NO_PARAMETERS);
    }

    public static int forgeRenderPass() {
        return intValue(invokeStatic(net.minecraftforge.client.MinecraftForgeClient.class,
                new String[] {"getRenderPass"}, NO_PARAMETERS), 0);
    }

    public static void setCurrentRenderLayer(BlockRenderLayer layer) {
        invokeStatic(net.minecraftforge.client.ForgeHooksClient.class,
                new String[] {"setRenderLayer"}, new Class<?>[] {BlockRenderLayer.class}, layer);
    }

    public static Object stateValue(Object state, Object property) {
        if (state == null || property == null) {
            return null;
        }
        Method method = findStateValueMethod(state.getClass(), property.getClass());
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(state, property);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Method findStateValueMethod(Class<?> stateClass, Class<?> propertyClass) {
        StateValueMethodKey key = new StateValueMethodKey(stateClass, propertyClass);
        Method cached = STATE_VALUE_METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_STATE_VALUE_METHODS.contains(key)) {
            return null;
        }
        for (String name : new String[] {"func_177229_b", "getValue"}) {
            Method method = findCompatibleDeclaredMethod(stateClass, name, propertyClass, new HashSet<>());
            if (method != null) {
                method.setAccessible(true);
                Method existing = STATE_VALUE_METHOD_CACHE.putIfAbsent(key, method);
                return existing != null ? existing : method;
            }
        }
        MISSING_STATE_VALUE_METHODS.add(key);
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<IProperty<?>, Comparable<?>> stateProperties(IBlockState state) {
        if (state == null) {
            return Collections.emptyMap();
        }
        IdentityKey<IBlockState> key = new IdentityKey<>(state);
        Map<IProperty<?>, Comparable<?>> cached = STATE_PROPERTIES_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Object value = invoke(state, new String[] {"func_177228_b", "getProperties"}, NO_PARAMETERS);
        Map<IProperty<?>, Comparable<?>> properties = value instanceof Map<?, ?>
                ? (Map<IProperty<?>, Comparable<?>>) value
                : Collections.emptyMap();
        Map<IProperty<?>, Comparable<?>> existing = STATE_PROPERTIES_CACHE.putIfAbsent(key, properties);
        return existing != null ? existing : properties;
    }

    public static Comparable<?> statePropertyValue(IBlockState state, IProperty<?> property) {
        if (state == null || property == null) {
            return null;
        }
        Object value = stateProperties(state).get(property);
        return value instanceof Comparable<?> ? (Comparable<?>) value : null;
    }

    public static String propertyName(IProperty<?> property) {
        if (property == null) {
            return null;
        }
        String cached = PROPERTY_NAME_CACHE.get(property);
        if (cached != null) {
            return cached;
        }
        String name = call(property, String.class, null, new String[] {"func_177701_a", "getName"}, NO_PARAMETERS);
        if (name != null) {
            String existing = PROPERTY_NAME_CACHE.putIfAbsent(property, name);
            return existing != null ? existing : name;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static String propertyValueName(IProperty property, Comparable<?> value) {
        if (property == null || value == null) {
            return null;
        }
        PropertyValueNameKey key = new PropertyValueNameKey(property, value);
        String cached = PROPERTY_VALUE_NAME_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Object name = invoke(property, new String[] {"func_177702_a", "getName"}, new Class<?>[] {Comparable.class}, value);
        String valueName = name instanceof String ? (String) name : String.valueOf(value);
        String existing = PROPERTY_VALUE_NAME_CACHE.putIfAbsent(key, valueName);
        return existing != null ? existing : valueName;
    }

    public static Block blockFromState(IBlockState state) {
        if (state == null) {
            return null;
        }
        IdentityHashMap<IBlockState, Block> hotCache = THREAD_STATE_BLOCK_CACHE.get();
        Block hotCached = hotCache.get(state);
        if (hotCached != null) {
            return hotCached;
        }
        IdentityKey<IBlockState> key = new IdentityKey<>(state);
        Block cached = STATE_BLOCK_CACHE.get(key);
        if (cached != null) {
            putThreadStateBlock(state, cached);
            return cached;
        }
        Block block = null;
        if (BLOCK_FROM_STATE_HANDLE != null) {
            try {
                block = (Block) BLOCK_FROM_STATE_HANDLE.invoke(state);
            } catch (Throwable ignored) {
            }
        }
        if (block == null) {
            block = call(state, Block.class, null, new String[] {"func_177230_c", "getBlock"}, NO_PARAMETERS);
        }
        if (block != null) {
            Block existing = STATE_BLOCK_CACHE.putIfAbsent(key, block);
            Block resolved = existing != null ? existing : block;
            putThreadStateBlock(state, resolved);
            return resolved;
        }
        return null;
    }

    private static void putThreadStateBlock(IBlockState state, Block block) {
        IdentityHashMap<IBlockState, Block> hotCache = THREAD_STATE_BLOCK_CACHE.get();
        if (hotCache.size() > HOT_IDENTITY_CACHE_LIMIT) {
            hotCache.clear();
        }
        hotCache.put(state, block);
    }

    public static void clearHotThreadCaches() {
        THREAD_STATE_BLOCK_CACHE.get().clear();
        THREAD_STATE_STRING_CACHE.get().clear();
    }

    public static String stateString(IBlockState state) {
        if (state == null) {
            return "null";
        }
        IdentityHashMap<IBlockState, String> hotCache = THREAD_STATE_STRING_CACHE.get();
        String cached = hotCache.get(state);
        if (cached != null) {
            return cached;
        }
        String value = String.valueOf(state);
        if (hotCache.size() > HOT_IDENTITY_CACHE_LIMIT) {
            hotCache.clear();
        }
        hotCache.put(state, value);
        return value;
    }

    public static IBlockState actualState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return state;
        }
        if (STATE_ACTUAL_STATE_HANDLE != null) {
            try {
                return (IBlockState) STATE_ACTUAL_STATE_HANDLE.invoke(state, blockAccess, pos);
            } catch (Throwable ignored) {
            }
        }
        try {
            return call(state, IBlockState.class, state, new String[] {"func_185899_b", "getActualState"},
                    new Class<?>[] {IBlockAccess.class, BlockPos.class}, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return state;
        }
    }

    public static BlockRenderLayer blockRenderLayer(Block block) {
        return call(block, BlockRenderLayer.class, null, BLOCK_RENDER_LAYER_NAMES, NO_PARAMETERS);
    }

    public static boolean blockCanRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        if (block == null) {
            return false;
        }
        try {
            return block.canRenderInLayer(state, layer);
        } catch (Throwable ignored) {
            Object value = invoke(block, new String[] {"canRenderInLayer"}, new Class<?>[] {IBlockState.class, BlockRenderLayer.class}, state, layer);
            return value instanceof Boolean ? (Boolean) value : layer == blockRenderLayer(block);
        }
    }

    public static ResourceLocation blockRegistryName(Block block) {
        if (block == null) {
            return null;
        }
        ResourceLocation cached = BLOCK_REGISTRY_NAME_CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        ResourceLocation name = null;
        try {
            name = block.getRegistryName();
        } catch (Throwable ignored) {
        }
        if (name == null) {
            name = call(block, ResourceLocation.class, null, new String[] {"getRegistryName"}, NO_PARAMETERS);
        }
        if (name != null) {
            ResourceLocation existing = BLOCK_REGISTRY_NAME_CACHE.putIfAbsent(block, name);
            return existing != null ? existing : name;
        }
        return null;
    }

    public static String resourceNamespace(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        String cached = RESOURCE_NAMESPACE_CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        String namespace;
        try {
            namespace = location.getNamespace();
        } catch (Throwable ignored) {
            namespace = call(location, String.class, "", new String[] {"func_110624_b", "getResourceDomain", "getNamespace"}, NO_PARAMETERS);
        }
        String existing = RESOURCE_NAMESPACE_CACHE.putIfAbsent(location, namespace);
        return existing != null ? existing : namespace;
    }

    public static String resourcePath(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        String cached = RESOURCE_PATH_CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        String path;
        try {
            path = location.getPath();
        } catch (Throwable ignored) {
            path = call(location, String.class, "", new String[] {"func_110623_a", "getResourcePath", "getPath"}, NO_PARAMETERS);
        }
        String existing = RESOURCE_PATH_CACHE.putIfAbsent(location, path);
        return existing != null ? existing : path;
    }

    public static String resourcePathLower(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        String cached = RESOURCE_PATH_LOWER_CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        String lower = resourcePath(location).toLowerCase(Locale.ROOT);
        String existing = RESOURCE_PATH_LOWER_CACHE.putIfAbsent(location, lower);
        return existing != null ? existing : lower;
    }

    public static String resourceString(ResourceLocation location) {
        if (location == null) {
            return "";
        }
        String cached = RESOURCE_STRING_CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        String value = location.toString();
        String existing = RESOURCE_STRING_CACHE.putIfAbsent(location, value);
        return existing != null ? existing : value;
    }

    @SuppressWarnings("unchecked")
    public static Iterable<ResourceLocation> registryKeys(Object registry) {
        Object value = invoke(registry, new String[] {"func_148742_b", "getKeys"}, NO_PARAMETERS);
        return value instanceof Iterable<?> ? (Iterable<ResourceLocation>) value : Collections.emptyList();
    }

    public static boolean blockIsAir(Block block, IBlockState state, IBlockAccess access, BlockPos pos) {
        Object value = invoke(block, new String[] {"isAir"}, new Class<?>[] {IBlockState.class, IBlockAccess.class, BlockPos.class},
                state, access, pos);
        return value instanceof Boolean ? (Boolean) value : block == field(Blocks.class, Block.class, null, "field_150350_a", "AIR");
    }

    public static boolean blockIsSideSolid(Block block, IBlockState state, IBlockAccess access, BlockPos pos, EnumFacing side, boolean fallback) {
        Object value = invoke(block, new String[] {"isSideSolid"},
                new Class<?>[] {IBlockState.class, IBlockAccess.class, BlockPos.class, EnumFacing.class},
                state, access, pos, side);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static boolean shouldRenderInPass(Entity entity, int pass) {
        Object value = invoke(entity, new String[] {"shouldRenderInPass"}, new Class<?>[] {int.class}, pass);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static void setRenderChunksMany(Minecraft minecraft, boolean value) {
        setField(minecraft, value, "field_175612_E", "renderChunksMany");
    }

    public static WorldClient world(Minecraft minecraft) {
        return field(minecraft, WorldClient.class, null, "field_71441_e", "world");
    }

    public static EntityPlayerSP player(Minecraft minecraft) {
        return field(minecraft, EntityPlayerSP.class, null, "field_71439_g", "player");
    }

    public static RenderGlobal renderGlobal(Minecraft minecraft) {
        return field(minecraft, RenderGlobal.class, null, "field_71438_f", "renderGlobal");
    }

    public static GameSettings gameSettings(Minecraft minecraft) {
        return field(minecraft, GameSettings.class, null, "field_71474_y", "gameSettings");
    }

    public static int renderDistanceChunks(Minecraft minecraft) {
        return renderDistanceChunks(gameSettings(minecraft), -1);
    }

    public static int renderDistanceChunks(GameSettings settings, int fallback) {
        return fieldInt(settings, fallback, "field_151451_c", "renderDistanceChunks");
    }

    public static boolean hideGui(GameSettings settings) {
        return fieldBoolean(settings, false, "field_74319_N", "hideGUI");
    }

    public static int thirdPersonView(GameSettings settings) {
        return fieldInt(settings, 0, "field_74320_O", "thirdPersonView");
    }

    public static boolean keyBindingIsPressed(KeyBinding binding) {
        return callBoolean(binding, new String[] {"func_151468_f", "isPressed"}, NO_PARAMETERS, false);
    }

    public static FontRenderer fontRenderer(Minecraft minecraft) {
        return field(minecraft, FontRenderer.class, null, "field_71466_p", "fontRenderer");
    }

    public static BlockPos rayTraceBlockPos(RayTraceResult hit) {
        Object value = getField(hit, "field_178783_e", "blockPos");
        if (value instanceof BlockPos) {
            return (BlockPos) value;
        }
        value = invoke(hit, new String[] {"func_178782_a", "getBlockPos"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static int displayWidth(Minecraft minecraft) {
        return fieldInt(minecraft, 1, "field_71443_c", "displayWidth");
    }

    public static int displayHeight(Minecraft minecraft) {
        return fieldInt(minecraft, 1, "field_71440_d", "displayHeight");
    }

    public static EntityRenderer entityRenderer(Minecraft minecraft) {
        return field(minecraft, EntityRenderer.class, null, "field_71460_t", "entityRenderer");
    }

    public static RenderManager renderManager(Minecraft minecraft) {
        return call(minecraft, RenderManager.class, null, new String[] {"func_175598_ae", "getRenderManager"}, NO_PARAMETERS);
    }

    public static Entity renderViewEntity(Minecraft minecraft) {
        return call(minecraft, Entity.class, null, new String[] {"func_175606_aa", "getRenderViewEntity"}, NO_PARAMETERS);
    }

    public static float renderPartialTicks(Minecraft minecraft) {
        return callFloat(minecraft, RENDER_PARTIAL_TICKS_NAMES, NO_PARAMETERS, 0.0F);
    }

    @SuppressWarnings("unchecked")
    public static ListenableFuture<Object> addScheduledTask(Minecraft minecraft, Runnable task) {
        Object value = invoke(minecraft, new String[] {"func_152344_a", "addScheduledTask"}, new Class<?>[] {Runnable.class}, task);
        return value instanceof ListenableFuture<?> ? (ListenableFuture<Object>) value : null;
    }

    public static void displayGuiScreen(Minecraft minecraft, GuiScreen screen) {
        invoke(minecraft, new String[] {"func_147108_a", "displayGuiScreen"}, new Class<?>[] {GuiScreen.class}, screen);
    }

    public static void renderGameOverlay(GuiIngame guiIngame, float partialTicks) {
        invokePropagating(guiIngame, new String[] {"func_175180_a", "renderGameOverlay"},
                new Class<?>[] {float.class}, partialTicks);
    }

    public static boolean isGamePaused(Minecraft minecraft) {
        return booleanValue(invoke(minecraft, new String[] {"func_147113_T", "isGamePaused"}, NO_PARAMETERS));
    }

    public static boolean guiScreenDoesPauseGame(GuiScreen screen) {
        return callBoolean(screen, new String[] {"func_73868_f", "doesGuiPauseGame"}, NO_PARAMETERS, false);
    }

    public static TextureManager textureManager(Minecraft minecraft) {
        Object value = invoke(minecraft, new String[] {"func_110434_K", "getTextureManager"}, NO_PARAMETERS);
        if (value instanceof TextureManager) {
            return (TextureManager) value;
        }
        value = getField(minecraft, "field_71446_o", "renderEngine");
        return value instanceof TextureManager ? (TextureManager) value : null;
    }

    public static Framebuffer minecraftFramebuffer(Minecraft minecraft) {
        Object value = invoke(minecraft, new String[] {"func_147110_a", "getFramebuffer"}, NO_PARAMETERS);
        return value instanceof Framebuffer ? (Framebuffer) value : null;
    }

    public static void bindFramebuffer(Framebuffer framebuffer, boolean setViewport) {
        invoke(framebuffer, new String[] {"func_147610_a", "bindFramebuffer"}, new Class<?>[] {boolean.class}, setViewport);
    }

    public static void renderSky(RenderGlobal renderGlobal, float partialTicks, int pass) {
        invokePropagating(renderGlobal, new String[] {"func_174976_a", "renderSky"},
                new Class<?>[] {float.class, int.class}, partialTicks, pass);
    }

    public static int renderBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass,
                                       Entity renderViewEntity) {
        Object value = invokePropagating(renderGlobal, new String[] {"func_174977_a", "renderBlockLayer"},
                new Class<?>[] {BlockRenderLayer.class, double.class, int.class, Entity.class},
                layer, partialTicks, pass, renderViewEntity);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static void renderEntities(RenderGlobal renderGlobal, Entity renderViewEntity, ICamera camera, float partialTicks) {
        invokePropagating(renderGlobal, new String[] {"func_180446_a", "renderEntities"},
                new Class<?>[] {Entity.class, ICamera.class, float.class}, renderViewEntity, camera, partialTicks);
    }

    public static void setupTerrain(RenderGlobal renderGlobal, Entity renderViewEntity, double partialTicks,
                                    ICamera camera, int frameCount, boolean playerSpectator) {
        invokePropagating(renderGlobal, new String[] {"func_174970_a", "setupTerrain"},
                new Class<?>[] {Entity.class, double.class, ICamera.class, int.class, boolean.class},
                renderViewEntity, partialTicks, camera, frameCount, playerSpectator);
    }

    public static void updateChunks(RenderGlobal renderGlobal, long finishTimeNano) {
        invokePropagating(renderGlobal, new String[] {"func_174967_a", "updateChunks"},
                new Class<?>[] {long.class}, finishTimeNano);
    }

    public static void loadRenderers(RenderGlobal renderGlobal) {
        invoke(renderGlobal, new String[] {"func_72712_a", "loadRenderers"}, NO_PARAMETERS);
    }

    public static void drawBlockDamageTexture(RenderGlobal renderGlobal, Tessellator tessellator,
                                              BufferBuilder bufferBuilder, Entity entity, float partialTicks) {
        invokePropagating(renderGlobal, new String[] {"func_174981_a", "drawBlockDamageTexture"},
                new Class<?>[] {Tessellator.class, BufferBuilder.class, Entity.class, float.class},
                tessellator, bufferBuilder, entity, partialTicks);
    }

    public static void drawSelectionBox(RenderGlobal renderGlobal, EntityPlayer player,
                                        RayTraceResult target, int execute, float partialTicks) {
        invokePropagating(renderGlobal, new String[] {"func_72731_b", "drawSelectionBox"},
                new Class<?>[] {EntityPlayer.class, RayTraceResult.class, int.class, float.class},
                player, target, execute, partialTicks);
    }

    public static RenderChunk[] viewFrustumRenderChunks(ViewFrustum viewFrustum) {
        Object value = getField(viewFrustum, "field_178164_f", "renderChunks");
        return value instanceof RenderChunk[] ? (RenderChunk[]) value : null;
    }

    public static void deleteViewFrustumGlResources(ViewFrustum viewFrustum) {
        invoke(viewFrustum, new String[] {"func_178160_a", "deleteGlResources"}, NO_PARAMETERS);
    }

    public static void renderLitParticles(ParticleManager particleManager, Entity entity, float partialTicks) {
        invokePropagating(particleManager, new String[] {"func_78872_b", "renderLitParticles"},
                new Class<?>[] {Entity.class, float.class}, entity, partialTicks);
    }

    public static void renderParticles(ParticleManager particleManager, Entity entity, float partialTicks) {
        invokePropagating(particleManager, new String[] {"func_78874_a", "renderParticles"},
                new Class<?>[] {Entity.class, float.class}, entity, partialTicks);
    }

    public static int framebufferObject(Framebuffer framebuffer) {
        return framebufferInt(framebuffer, "field_147616_f", "framebufferObject");
    }

    public static int framebufferTexture(Framebuffer framebuffer) {
        return framebufferInt(framebuffer, "field_147617_g", "framebufferTexture");
    }

    public static int framebufferWidth(Framebuffer framebuffer) {
        return framebufferInt(framebuffer, "field_147621_c", "framebufferWidth");
    }

    public static int framebufferHeight(Framebuffer framebuffer) {
        return framebufferInt(framebuffer, "field_147618_d", "framebufferHeight");
    }

    public static void deleteFramebuffer(Framebuffer framebuffer) {
        invoke(framebuffer, new String[] {"func_147608_a", "deleteFramebuffer"}, NO_PARAMETERS);
    }

    public static WorldClient netHandlerWorld(NetHandlerPlayClient handler) {
        Object value = getField(handler, "field_147300_g", "world", "clientWorldController");
        return value instanceof WorldClient ? (WorldClient) value : null;
    }

    public static boolean blockStateContainerRead(BlockStateContainer container, PacketBuffer buffer) {
        if (container == null || buffer == null) {
            return false;
        }
        for (String name : new String[] {"func_186010_a", "read"}) {
            try {
                Method method = container.getClass().getMethod(name, PacketBuffer.class);
                method.setAccessible(true);
                method.invoke(container, buffer);
                return true;
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                return false;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    public static boolean readChunkBlockStateContainer(PacketBuffer buffer) {
        return blockStateContainerRead(new BlockStateContainer(), buffer);
    }

    public static int[] dynamicTextureData(DynamicTexture texture) {
        Object value = invoke(texture, new String[] {"func_110565_c", "getTextureData"}, NO_PARAMETERS);
        return value instanceof int[] ? (int[]) value : new int[0];
    }

    public static void bindTexture(TextureManager textureManager, ResourceLocation location) {
        invoke(textureManager, new String[] {"func_110577_a", "bindTexture"}, new Class<?>[] {ResourceLocation.class}, location);
    }

    @SuppressWarnings("unchecked")
    public static Render<Entity> entityRenderObject(RenderManager renderManager, Entity entity) {
        Object value = invoke(renderManager, new String[] {"func_78713_a", "getEntityRenderObject"},
                new Class<?>[] {Entity.class}, entity);
        return value instanceof Render<?> ? (Render<Entity>) value : null;
    }

    public static void renderManagerCacheActiveRenderInfo(RenderManager renderManager, World world,
                                                          FontRenderer fontRenderer, Entity viewEntity,
                                                          Entity pointedEntity, GameSettings gameSettings,
                                                          float partialTicks) {
        invoke(renderManager, new String[] {"func_180597_a", "cacheActiveRenderInfo"},
                new Class<?>[] {World.class, FontRenderer.class, Entity.class, Entity.class, GameSettings.class, float.class},
                world, fontRenderer, viewEntity, pointedEntity, gameSettings, partialTicks);
    }

    public static void renderManagerSetRenderPosition(RenderManager renderManager, double x, double y, double z) {
        invoke(renderManager, new String[] {"func_78725_b", "setRenderPosition"},
                new Class<?>[] {double.class, double.class, double.class}, x, y, z);
    }

    public static boolean renderManagerShouldRender(RenderManager renderManager, Entity entity, ICamera camera,
                                                    double cameraX, double cameraY, double cameraZ) {
        return callBoolean(renderManager, new String[] {"func_178635_a", "shouldRender"},
                new Class<?>[] {Entity.class, ICamera.class, double.class, double.class, double.class},
                true, entity, camera, cameraX, cameraY, cameraZ);
    }

    public static void renderManagerRenderEntityStatic(RenderManager renderManager, Entity entity,
                                                       float partialTicks, boolean debugBoundingBox) {
        invoke(renderManager, new String[] {"func_188391_a", "renderEntityStatic"},
                new Class<?>[] {Entity.class, float.class, boolean.class}, entity, partialTicks, debugBoundingBox);
    }

    public static boolean renderManagerIsRenderMultipass(RenderManager renderManager, Entity entity) {
        return callBoolean(renderManager, new String[] {"func_178627_a", "isRenderMultipass"},
                new Class<?>[] {Entity.class}, false, entity);
    }

    public static void renderManagerRenderMultipass(RenderManager renderManager, Entity entity, float partialTicks) {
        invoke(renderManager, new String[] {"func_188389_a", "renderMultipass"},
                new Class<?>[] {Entity.class, float.class}, entity, partialTicks);
    }

    public static boolean entityIsRidingOrBeingRiddenBy(Entity entity, Entity other) {
        return callBoolean(entity, new String[] {"func_184223_x", "isRidingOrBeingRiddenBy"},
                new Class<?>[] {Entity.class}, false, other);
    }

    public static boolean entityIsInRangeToRender3d(Entity entity, double x, double y, double z) {
        return callBoolean(entity, new String[] {"func_70112_a", "isInRangeToRender3d"},
                new Class<?>[] {double.class, double.class, double.class}, true, x, y, z);
    }

    public static void enableStandardItemLighting() {
        invoke(net.minecraft.client.renderer.RenderHelper.class,
                new String[] {"func_74519_b", "enableStandardItemLighting"}, NO_PARAMETERS);
    }

    public static void disableStandardItemLighting() {
        invoke(net.minecraft.client.renderer.RenderHelper.class,
                new String[] {"func_74518_a", "disableStandardItemLighting"}, NO_PARAMETERS);
    }

    public static void enableGuiStandardItemLighting() {
        invoke(net.minecraft.client.renderer.RenderHelper.class,
                new String[] {"func_74520_c", "enableGUIStandardItemLighting"}, NO_PARAMETERS);
    }

    public static net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher tileEntityRendererDispatcher() {
        Object value = getStaticField(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class,
                "field_147556_a", "instance");
        return value instanceof net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
                ? (net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher) value
                : null;
    }

    public static void tileEntityRendererPrepare(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher,
                                                 World world, TextureManager textureManager, FontRenderer fontRenderer,
                                                 Entity viewEntity, RayTraceResult hit, float partialTicks) {
        invoke(dispatcher, new String[] {"func_190056_a", "prepare"},
                new Class<?>[] {World.class, TextureManager.class, FontRenderer.class, Entity.class, RayTraceResult.class, float.class},
                world, textureManager, fontRenderer, viewEntity, hit, partialTicks);
    }

    public static void tileEntityRendererRender(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher dispatcher,
                                                TileEntity tileEntity, double x, double y, double z,
                                                float partialTicks, int destroyStage, float alpha) {
        invoke(dispatcher, TILE_ENTITY_RENDER_NAMES, TILE_ENTITY_RENDER_PARAMETERS,
                tileEntity, x, y, z, partialTicks, destroyStage, alpha);
    }

    public static boolean cameraIsBoundingBoxInFrustum(ICamera camera, net.minecraft.util.math.AxisAlignedBB box) {
        return callBoolean(camera, CAMERA_FRUSTUM_NAMES, AXIS_ALIGNED_BB_PARAMETERS, true, box);
    }

    public static ITextureObject texture(TextureManager textureManager, ResourceLocation location) {
        Object value = invoke(textureManager, new String[] {"func_110581_b", "getTexture"}, new Class<?>[] {ResourceLocation.class}, location);
        return value instanceof ITextureObject ? (ITextureObject) value : null;
    }

    public static int glTextureId(ITextureObject texture) {
        return intValue(invoke(texture, new String[] {"func_110552_b", "getGlTextureId"}, NO_PARAMETERS), -1);
    }

    public static void enableLightmap(EntityRenderer entityRenderer) {
        invoke(entityRenderer, new String[] {"func_180436_i", "enableLightmap"}, NO_PARAMETERS);
    }

    public static void disableLightmap(EntityRenderer entityRenderer) {
        invoke(entityRenderer, new String[] {"func_175072_h", "disableLightmap"}, NO_PARAMETERS);
    }

    public static void glUseProgram(int program) {
        invoke(OpenGlHelper.class, new String[] {"func_153161_d", "glUseProgram"}, new Class<?>[] {int.class}, program);
    }

    public static int glFramebuffer() {
        return openGlHelperInt("field_153198_e", "GL_FRAMEBUFFER", GL30.GL_FRAMEBUFFER);
    }

    public static int glDepthAttachment() {
        return openGlHelperInt("field_153201_h", "GL_DEPTH_ATTACHMENT", GL30.GL_DEPTH_ATTACHMENT);
    }

    public static int glVertexShader() {
        return openGlHelperInt("field_153209_q", "GL_VERTEX_SHADER", GL20.GL_VERTEX_SHADER);
    }

    public static boolean isFramebufferEnabled() {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_148822_b", "isFramebufferEnabled"}, NO_PARAMETERS);
        return value instanceof Boolean ? (Boolean) value : true;
    }

    public static int glCreateProgram() {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153183_d", "glCreateProgram"}, NO_PARAMETERS);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glCreateProgram();
    }

    public static void glDeleteProgram(int program) {
        invoke(OpenGlHelper.class, new String[] {"func_153187_e", "glDeleteProgram"}, new Class<?>[] {int.class}, program);
    }

    public static void glAttachShader(int program, int shader) {
        invoke(OpenGlHelper.class, new String[] {"func_153178_b", "glAttachShader"},
                new Class<?>[] {int.class, int.class}, program, shader);
    }

    public static void glLinkProgram(int program) {
        invoke(OpenGlHelper.class, new String[] {"func_153179_f", "glLinkProgram"}, new Class<?>[] {int.class}, program);
    }

    public static int glGetProgrami(int program, int pname) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153175_a", "glGetProgrami"},
                new Class<?>[] {int.class, int.class}, program, pname);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetProgrami(program, pname);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153166_e", "glGetProgramInfoLog"},
                new Class<?>[] {int.class, int.class}, program, maxLength);
        return value instanceof String ? (String) value : GL20.glGetProgramInfoLog(program, maxLength);
    }

    public static int glGetUniformLocation(int program, CharSequence name) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153194_a", "glGetUniformLocation"},
                new Class<?>[] {int.class, CharSequence.class}, program, name);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetUniformLocation(program, name);
    }

    public static int glCreateShader(int shaderType) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153195_b", "glCreateShader"},
                new Class<?>[] {int.class}, shaderType);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glCreateShader(shaderType);
    }

    public static void glCompileShader(int shader) {
        invoke(OpenGlHelper.class, new String[] {"func_153170_c", "glCompileShader"}, new Class<?>[] {int.class}, shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153157_c", "glGetShaderi"},
                new Class<?>[] {int.class, int.class}, shader, pname);
        return value instanceof Number ? ((Number) value).intValue() : GL20.glGetShaderi(shader, pname);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153158_d", "glGetShaderInfoLog"},
                new Class<?>[] {int.class, int.class}, shader, maxLength);
        return value instanceof String ? (String) value : GL20.glGetShaderInfoLog(shader, maxLength);
    }

    public static void glDeleteShader(int shader) {
        invoke(OpenGlHelper.class, new String[] {"func_153180_a", "glDeleteShader"}, new Class<?>[] {int.class}, shader);
    }

    public static int glGenFramebuffers() {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153165_e", "glGenFramebuffers"}, NO_PARAMETERS);
        return value instanceof Number ? ((Number) value).intValue() : GL30.glGenFramebuffers();
    }

    public static void glBindFramebuffer(int target, int framebuffer) {
        invoke(OpenGlHelper.class, new String[] {"func_153171_g", "glBindFramebuffer"},
                new Class<?>[] {int.class, int.class}, target, framebuffer);
    }

    public static void glFramebufferTexture2D(int target, int attachment, int texTarget, int texture, int level) {
        invoke(OpenGlHelper.class, new String[] {"func_153188_a", "glFramebufferTexture2D"},
                new Class<?>[] {int.class, int.class, int.class, int.class, int.class},
                target, attachment, texTarget, texture, level);
    }

    public static int glCheckFramebufferStatus(int target) {
        Object value = invoke(OpenGlHelper.class, new String[] {"func_153167_i", "glCheckFramebufferStatus"},
                new Class<?>[] {int.class}, target);
        return value instanceof Number ? ((Number) value).intValue() : GL30.glCheckFramebufferStatus(target);
    }

    public static void glDeleteFramebuffers(int framebuffer) {
        invoke(OpenGlHelper.class, new String[] {"func_153174_h", "glDeleteFramebuffers"}, new Class<?>[] {int.class}, framebuffer);
    }

    public static void glBindBuffer(int target, int buffer) {
        invoke(OpenGlHelper.class, new String[] {"func_176072_g", "glBindBuffer"},
                new Class<?>[] {int.class, int.class}, target, buffer);
    }

    public static void glUniform1i(int location, int value) {
        invoke(OpenGlHelper.class, GL_UNIFORM_1I_NAMES, INT_INT_PARAMETERS, location, value);
    }

    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer value) {
        invoke(OpenGlHelper.class, new String[] {"func_153160_c", "glUniformMatrix4"},
                new Class<?>[] {int.class, boolean.class, FloatBuffer.class}, location, transpose, value);
    }

    public static int defaultTexUnit() {
        Object value = getStaticField(OpenGlHelper.class, "field_77478_a", "defaultTexUnit");
        return intValue(value, GL13.GL_TEXTURE0);
    }

    public static int lightmapTexUnit() {
        Object value = getStaticField(OpenGlHelper.class, "field_77476_b", "lightmapTexUnit");
        return intValue(value, GL13.GL_TEXTURE1);
    }

    public static void setActiveTexture(int textureUnit) {
        invoke(OpenGlHelper.class, new String[] {"func_77473_a", "setActiveTexture"}, new Class<?>[] {int.class}, textureUnit);
    }

    public static void glStateSetActiveTexture(int textureUnit) {
        invoke(GlStateManager.class, new String[] {"func_179138_g", "setActiveTexture"}, new Class<?>[] {int.class}, textureUnit);
    }

    public static void glStateEnableTexture2D() {
        invoke(GlStateManager.class, new String[] {"func_179098_w", "enableTexture2D"}, NO_PARAMETERS);
    }

    public static void glStateDisableTexture2D() {
        invoke(GlStateManager.class, new String[] {"func_179090_x", "disableTexture2D"}, NO_PARAMETERS);
    }

    public static void glStateEnableDepth() {
        invoke(GlStateManager.class, new String[] {"func_179126_j", "enableDepth"}, NO_PARAMETERS);
    }

    public static void glStateDisableDepth() {
        invoke(GlStateManager.class, new String[] {"func_179097_i", "disableDepth"}, NO_PARAMETERS);
    }

    public static void glStateDepthMask(boolean flag) {
        invoke(GlStateManager.class, new String[] {"func_179132_a", "depthMask"}, new Class<?>[] {boolean.class}, flag);
    }

    public static void glStateEnableAlpha() {
        invoke(GlStateManager.class, new String[] {"func_179141_d", "enableAlpha"}, NO_PARAMETERS);
    }

    public static void glStateDisableAlpha() {
        invoke(GlStateManager.class, new String[] {"func_179118_c", "disableAlpha"}, NO_PARAMETERS);
    }

    public static void glStateAlphaFunc(int func, float ref) {
        invoke(GlStateManager.class, new String[] {"func_179092_a", "alphaFunc"},
                new Class<?>[] {int.class, float.class}, func, ref);
    }

    public static void glStateEnableBlend() {
        invoke(GlStateManager.class, new String[] {"func_179147_l", "enableBlend"}, NO_PARAMETERS);
    }

    public static void glStateDisableBlend() {
        invoke(GlStateManager.class, new String[] {"func_179084_k", "disableBlend"}, NO_PARAMETERS);
    }

    public static void glStateTryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        invoke(GlStateManager.class, new String[] {"func_179120_a", "tryBlendFuncSeparate"},
                new Class<?>[] {int.class, int.class, int.class, int.class},
                srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void glStateDisableLighting() {
        invoke(GlStateManager.class, GL_DISABLE_LIGHTING_NAMES, NO_PARAMETERS);
    }

    public static void glStateDisableColorMaterial() {
        invoke(GlStateManager.class, new String[] {"func_179119_h", "disableColorMaterial"}, NO_PARAMETERS);
    }

    public static void glStateEnableCull() {
        invoke(GlStateManager.class, new String[] {"func_179089_o", "enableCull"}, NO_PARAMETERS);
    }

    public static void glStateDisableCull() {
        invoke(GlStateManager.class, new String[] {"func_179129_p", "disableCull"}, NO_PARAMETERS);
    }

    public static void glStateCullFaceBack() {
        if (!CullFaceBackCall.invoke()) {
            GL11.glCullFace(GL11.GL_BACK);
        }
    }

    public static void glStateBindTexture(int texture) {
        invoke(GlStateManager.class, new String[] {"func_179144_i", "bindTexture"}, new Class<?>[] {int.class}, texture);
    }

    private static final class CullFaceBackCall {
        private static final Method METHOD = findMethod();
        private static final Object BACK = findBackValue(METHOD);

        private static boolean invoke() {
            if (METHOD == null || BACK == null) {
                return false;
            }
            try {
                METHOD.invoke(null, BACK);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }

        private static Method findMethod() {
            for (String name : new String[] {"func_187407_a", "cullFace"}) {
                for (Method method : GlStateManager.class.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(name) && parameters.length == 1 && parameters[0].isEnum()) {
                        try {
                            method.setAccessible(true);
                            return method;
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
            }
            return null;
        }

        private static Object findBackValue(Method method) {
            if (method == null) {
                return null;
            }
            Object[] values = method.getParameterTypes()[0].getEnumConstants();
            if (values == null) {
                return null;
            }
            for (Object value : values) {
                if (value instanceof Enum<?> && "BACK".equals(((Enum<?>) value).name())) {
                    return value;
                }
            }
            return null;
        }
    }

    public static void glStateViewport(int x, int y, int width, int height) {
        invoke(GlStateManager.class, new String[] {"func_179083_b", "viewport"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, x, y, width, height);
    }

    public static void glStateColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        invoke(GlStateManager.class, new String[] {"func_179135_a", "colorMask"},
                new Class<?>[] {boolean.class, boolean.class, boolean.class, boolean.class},
                red, green, blue, alpha);
    }

    public static void glStateClearDepth(double depth) {
        invoke(GlStateManager.class, new String[] {"func_179151_a", "clearDepth"}, new Class<?>[] {double.class}, depth);
    }

    public static void glStateColor(float red, float green, float blue, float alpha) {
        invoke(GlStateManager.class, new String[] {"func_179131_c", "color"},
                new Class<?>[] {float.class, float.class, float.class, float.class},
                red, green, blue, alpha);
    }

    public static void glStateGlTexCoordPointer(int size, int type, int stride, int pointer) {
        invoke(GlStateManager.class, new String[] {"func_187405_c", "glTexCoordPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, size, type, stride, pointer);
    }

    public static void setClientActiveTexture(int textureUnit) {
        invoke(OpenGlHelper.class, new String[] {"func_77472_b", "setClientActiveTexture"}, new Class<?>[] {int.class}, textureUnit);
    }

    public static BlockRendererDispatcher blockRendererDispatcher(Minecraft minecraft) {
        return call(minecraft, BlockRendererDispatcher.class, null,
                new String[] {"func_175602_ab", "getBlockRendererDispatcher"}, NO_PARAMETERS);
    }

    public static ResourceLocation blocksTexture() {
        return field(TextureMap.class, ResourceLocation.class, new ResourceLocation("textures/atlas/blocks.png"),
                "field_110575_b", "LOCATION_BLOCKS_TEXTURE");
    }

    @SuppressWarnings("unchecked")
    public static List<BakedQuad> bakedModelQuads(IBakedModel model, IBlockState state, EnumFacing side, long rand) {
        Object value = invoke(model, new String[] {"func_188616_a", "getQuads"},
                new Class<?>[] {IBlockState.class, EnumFacing.class, long.class}, state, side, rand);
        return value instanceof List<?> ? (List<BakedQuad>) value : Collections.emptyList();
    }

    public static TextureAtlasSprite bakedQuadSprite(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"func_187508_a", "getSprite"}, NO_PARAMETERS);
        if (value instanceof TextureAtlasSprite) {
            return (TextureAtlasSprite) value;
        }
        value = getField(quad, "field_187509_d", "sprite");
        return value instanceof TextureAtlasSprite ? (TextureAtlasSprite) value : null;
    }

    public static int[] bakedQuadVertexData(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"func_178209_a", "getVertexData"}, NO_PARAMETERS);
        if (value instanceof int[]) {
            return (int[]) value;
        }
        value = getField(quad, "field_178215_a", "vertexData");
        return value instanceof int[] ? (int[]) value : null;
    }

    public static int bakedQuadTintIndex(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"func_178211_c", "getTintIndex"}, NO_PARAMETERS);
        return intValue(value, intValue(getField(quad, "field_178213_b", "tintIndex"), -1));
    }

    public static boolean bakedQuadHasTintIndex(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"hasTintIndex"}, NO_PARAMETERS);
        return value instanceof Boolean ? (Boolean) value : bakedQuadTintIndex(quad) >= 0;
    }

    public static EnumFacing bakedQuadFace(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"func_178210_d", "getFace"}, NO_PARAMETERS);
        if (value instanceof EnumFacing) {
            return (EnumFacing) value;
        }
        value = getField(quad, "field_178214_c", "face");
        return value instanceof EnumFacing ? (EnumFacing) value : null;
    }

    public static VertexFormat bakedQuadFormat(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"getFormat"}, NO_PARAMETERS);
        if (value instanceof VertexFormat) {
            return (VertexFormat) value;
        }
        value = getField(quad, "format");
        return value instanceof VertexFormat ? (VertexFormat) value : blockFormat();
    }

    public static boolean bakedQuadApplyDiffuseLighting(BakedQuad quad) {
        Object value = invoke(quad, new String[] {"shouldApplyDiffuseLighting"}, NO_PARAMETERS);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        value = getField(quad, "applyDiffuseLighting");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static String spriteIconName(TextureAtlasSprite sprite) {
        return call(sprite, String.class, null, new String[] {"func_94215_i", "getIconName"}, NO_PARAMETERS);
    }

    public static float spriteMinU(TextureAtlasSprite sprite) {
        return callFloat(sprite, new String[] {"func_94209_e", "getMinU"}, NO_PARAMETERS,
                fieldFloat(sprite, 0.0F, "field_110979_l", "minU"));
    }

    public static float spriteMaxU(TextureAtlasSprite sprite) {
        return callFloat(sprite, new String[] {"func_94212_f", "getMaxU"}, NO_PARAMETERS,
                fieldFloat(sprite, 0.0F, "field_110980_m", "maxU"));
    }

    public static float spriteMinV(TextureAtlasSprite sprite) {
        return callFloat(sprite, new String[] {"func_94206_g", "getMinV"}, NO_PARAMETERS,
                fieldFloat(sprite, 0.0F, "field_110977_n", "minV"));
    }

    public static float spriteMaxV(TextureAtlasSprite sprite) {
        return callFloat(sprite, new String[] {"func_94210_h", "getMaxV"}, NO_PARAMETERS,
                fieldFloat(sprite, 0.0F, "field_110978_o", "maxV"));
    }

    public static boolean renderBlock(BlockRendererDispatcher dispatcher, IBlockState state, BlockPos pos,
                                      IBlockAccess blockAccess, BufferBuilder buffer) {
        Object value = invoke(dispatcher, new String[] {"func_175018_a", "renderBlock"},
                new Class<?>[] {IBlockState.class, BlockPos.class, IBlockAccess.class, BufferBuilder.class},
                state, pos, blockAccess, buffer);
        return value instanceof Boolean && (Boolean) value;
    }

    public static BlockPos renderChunkPosition(Object renderChunk) {
        Object value = invoke(renderChunk, new String[] {"func_178568_j", "getPosition"}, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static World renderChunkWorld(RenderChunk renderChunk) {
        Object value = getField(renderChunk, "field_178588_d", "world");
        return value instanceof World ? (World) value : null;
    }

    public static BlockPos tileEntityPos(TileEntity tileEntity) {
        Object value = invoke(tileEntity, TILE_ENTITY_POS_NAMES, NO_PARAMETERS);
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static boolean tileEntityInvalid(TileEntity tileEntity) {
        return callBoolean(tileEntity, TILE_ENTITY_INVALID_NAMES, NO_PARAMETERS, false);
    }

    public static BufferBuilder regionBufferForLayer(RegionRenderCacheBuilder builder, BlockRenderLayer layer) {
        if (builder == null || layer == null) {
            return null;
        }
        Object value = invoke(builder, new String[] {"func_179038_a", "getWorldRendererByLayer"},
                new Class<?>[] {BlockRenderLayer.class}, layer);
        if (value instanceof BufferBuilder) {
            return (BufferBuilder) value;
        }
        value = invoke(builder, new String[] {"func_179039_a", "getWorldRendererByLayerId"},
                new Class<?>[] {int.class}, layer.ordinal());
        if (value instanceof BufferBuilder) {
            return (BufferBuilder) value;
        }
        Object worldRenderers = getField(builder, "field_179040_a", "worldRenderers");
        if (worldRenderers instanceof BufferBuilder[] buffers
                && layer.ordinal() >= 0
                && layer.ordinal() < buffers.length) {
            return buffers[layer.ordinal()];
        }
        return null;
    }

    public static int bufferVertexCount(BufferBuilder buffer) {
        return intValue(invoke(buffer, new String[] {"func_178989_h", "getVertexCount"}, NO_PARAMETERS), 0);
    }

    public static Tessellator tessellator() {
        Object value = invokeStatic(Tessellator.class, new String[] {"func_178181_a", "getInstance"}, NO_PARAMETERS);
        return value instanceof Tessellator ? (Tessellator) value : null;
    }

    public static BufferBuilder tessellatorBuffer(Tessellator tessellator) {
        Object value = invoke(tessellator, new String[] {"func_178180_c", "getBuffer"}, NO_PARAMETERS);
        return value instanceof BufferBuilder ? (BufferBuilder) value : null;
    }

    public static void tessellatorDraw(Tessellator tessellator) {
        invoke(tessellator, new String[] {"func_78381_a", "draw"}, NO_PARAMETERS);
    }

    public static boolean bufferIsDrawing(BufferBuilder buffer) {
        if (buffer instanceof IBufferBuilderExtension extension) {
            return extension.ausm$isDrawing();
        }
        return fieldBoolean(buffer, false, "field_179010_r", "isDrawing");
    }

    public static void forceResetBufferDrawingState(BufferBuilder buffer) {
        if (buffer == null) {
            return;
        }
        if (buffer instanceof IBufferBuilderExtension extension) {
            extension.ausm$forceResetDrawingState();
            return;
        }
        setField(buffer, false, "field_179010_r", "isDrawing");
        invoke(buffer, new String[] {"func_178965_a", "reset"}, NO_PARAMETERS);
    }

    public static VertexFormat bufferVertexFormat(BufferBuilder buffer) {
        Object value = invoke(buffer, BUFFER_VERTEX_FORMAT_NAMES, NO_PARAMETERS);
        return value instanceof VertexFormat ? (VertexFormat) value : null;
    }

    public static ByteBuffer bufferByteBuffer(BufferBuilder buffer) {
        Object value = invoke(buffer, new String[] {"func_178966_f", "getByteBuffer"}, NO_PARAMETERS);
        return value instanceof ByteBuffer ? (ByteBuffer) value : null;
    }

    public static void bufferBegin(BufferBuilder buffer, int drawMode, VertexFormat format) {
        invoke(buffer, new String[] {"func_181668_a", "begin"}, new Class<?>[] {int.class, VertexFormat.class}, drawMode, format);
    }

    public static void bufferSetTranslation(BufferBuilder buffer, double x, double y, double z) {
        invoke(buffer, new String[] {"func_178969_c", "setTranslation"}, new Class<?>[] {double.class, double.class, double.class}, x, y, z);
    }

    public static Object bufferPos(BufferBuilder buffer, double x, double y, double z) {
        return invoke(buffer, new String[] {"func_181662_b", "pos"},
                new Class<?>[] {double.class, double.class, double.class}, x, y, z);
    }

    public static Object bufferTex(Object target, double u, double v) {
        return invoke(target, new String[] {"func_187315_a", "tex"}, new Class<?>[] {double.class, double.class}, u, v);
    }

    public static Object bufferColor(Object target, int red, int green, int blue, int alpha) {
        return invoke(target, new String[] {"func_181669_b", "color"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, red, green, blue, alpha);
    }

    public static void bufferEndVertex(Object target) {
        invoke(target, new String[] {"func_181675_d", "endVertex"}, NO_PARAMETERS);
    }

    public static void bufferPosTexEnd(BufferBuilder buffer, double x, double y, double z, double u, double v) {
        Object positioned = bufferPos(buffer, x, y, z);
        Object textured = bufferTex(positioned != null ? positioned : buffer, u, v);
        bufferEndVertex(textured != null ? textured : positioned != null ? positioned : buffer);
    }

    public static void bufferPosColorEnd(BufferBuilder buffer, double x, double y, double z,
                                         int red, int green, int blue, int alpha) {
        Object positioned = bufferPos(buffer, x, y, z);
        Object colored = bufferColor(positioned != null ? positioned : buffer, red, green, blue, alpha);
        bufferEndVertex(colored != null ? colored : positioned != null ? positioned : buffer);
    }

    public static void bufferPosEnd(BufferBuilder buffer, double x, double y, double z) {
        Object positioned = bufferPos(buffer, x, y, z);
        bufferEndVertex(positioned != null ? positioned : buffer);
    }

    public static boolean renderChunkLayerEmpty(Object renderChunk, BlockRenderLayer layer) {
        Object compiledChunk = invoke(renderChunk, new String[] {"func_178571_g", "getCompiledChunk"}, NO_PARAMETERS);
        Object value = invoke(compiledChunk, new String[] {"func_178491_b", "isLayerEmpty"},
                new Class<?>[] {BlockRenderLayer.class}, layer);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static VertexFormat blockFormat() {
        return defaultVertexFormat("field_176600_a", "BLOCK");
    }

    private static VertexFormat defaultVertexFormat(String srgName, String mcpName) {
        Object value = getStaticField(DefaultVertexFormats.class, srgName, mcpName);
        return value instanceof VertexFormat ? (VertexFormat) value : null;
    }

    public static VertexFormat addElement(VertexFormat format, VertexFormatElement element) {
        Object value = invoke(format, new String[] {"func_181721_a", "addElement"}, new Class<?>[] {VertexFormatElement.class}, element);
        return value instanceof VertexFormat ? (VertexFormat) value : format;
    }

    public static float[] ambientOcclusionFaceVertexColorMultiplier(Object ambientOcclusionFace) {
        Object value = getField(ambientOcclusionFace, "field_178206_b", "vertexColorMultiplier");
        return value instanceof float[] ? (float[]) value : null;
    }

    public static Object invoke(Object target, String[] names, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Method method = findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static <T> T call(Object target, Class<T> type, T fallback, String[] names,
                             Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(target, names, parameterTypes, args);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static int callInt(Object target, String[] names, Class<?>[] parameterTypes, int fallback, Object... args) {
        return intValue(invoke(target, names, parameterTypes, args), fallback);
    }

    public static long callLong(Object target, String[] names, Class<?>[] parameterTypes, long fallback, Object... args) {
        return longValue(invoke(target, names, parameterTypes, args), fallback);
    }

    public static float callFloat(Object target, String[] names, Class<?>[] parameterTypes, float fallback, Object... args) {
        return floatValue(invoke(target, names, parameterTypes, args), fallback);
    }

    public static double callDouble(Object target, String[] names, Class<?>[] parameterTypes, double fallback, Object... args) {
        return doubleValue(invoke(target, names, parameterTypes, args), fallback);
    }

    public static boolean callBoolean(Object target, String[] names, Class<?>[] parameterTypes,
                                      boolean fallback, Object... args) {
        return booleanValue(invoke(target, names, parameterTypes, args), fallback);
    }

    public static <T> T callStatic(Class<?> owner, Class<T> type, T fallback, String[] names,
                                   Class<?>[] parameterTypes, Object... args) {
        Object value = invokeStatic(owner, names, parameterTypes, args);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static <T> T field(Object target, Class<T> type, T fallback, String... names) {
        Object value = getField(target, names);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static int fieldInt(Object target, int fallback, String... names) {
        return intValue(getField(target, names), fallback);
    }

    public static float fieldFloat(Object target, float fallback, String... names) {
        return floatValue(getField(target, names), fallback);
    }

    public static double fieldDouble(Object target, double fallback, String... names) {
        return doubleValue(getField(target, names), fallback);
    }

    public static boolean fieldBoolean(Object target, boolean fallback, String... names) {
        return booleanValue(getField(target, names), fallback);
    }

    private static Object invokePropagating(Object target, String[] names, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Method method = findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                return null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object invokeStatic(Class<?> owner, String[] names, Class<?>[] parameterTypes, Object... args) {
        for (String name : names) {
            Method method = findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(null, args);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        Class<?>[] parameters = parameterTypes != null ? parameterTypes : NO_PARAMETERS;
        MethodLookupCache localCache = THREAD_METHOD_LOOKUP_CACHE.get();
        Method local = localCache.lookup(owner, name, parameters);
        if (localCache.hit) {
            return local;
        }
        MethodKey key = new MethodKey(owner, name, parameters);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            localCache.store(owner, name, parameters, cached);
            return cached;
        }
        if (MISSING_METHODS.contains(key)) {
            localCache.store(owner, name, parameters, null);
            return null;
        }
        Method declared = findExactDeclaredMethod(owner, name, parameterTypes, new HashSet<>());
        if (declared != null) {
            declared.setAccessible(true);
            Method existing = METHOD_CACHE.putIfAbsent(key, declared);
            Method resolved = existing != null ? existing : declared;
            localCache.store(owner, name, parameters, resolved);
            return resolved;
        }
        try {
            Method method = owner.getMethod(name, parameters);
            method.setAccessible(true);
            Method existing = METHOD_CACHE.putIfAbsent(key, method);
            Method resolved = existing != null ? existing : method;
            localCache.store(owner, name, parameters, resolved);
            return resolved;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            MISSING_METHODS.add(key);
            localCache.store(owner, name, parameters, null);
            return null;
        }
    }

    private static Method findExactDeclaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes, Set<Class<?>> visited) {
        if (owner == null) {
            return null;
        }
        Class<?>[] parameters = parameterTypes != null ? parameterTypes : NO_PARAMETERS;
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (!visited.add(current)) {
                continue;
            }
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            Method interfaceMethod = findExactInterfaceMethod(current, name, parameters, visited);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return null;
    }

    private static Method findExactInterfaceMethod(Class<?> owner, String name, Class<?>[] parameterTypes, Set<Class<?>> visited) {
        for (Class<?> interfaceClass : owner.getInterfaces()) {
            if (!visited.add(interfaceClass)) {
                continue;
            }
            try {
                return interfaceClass.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException | SecurityException ignored) {
            }
            Method nested = findExactInterfaceMethod(interfaceClass, name, parameterTypes, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static Method findCompatibleDeclaredMethod(Class<?> owner, String name, Class<?> propertyClass, Set<Class<?>> visited) {
        if (owner == null || propertyClass == null) {
            return null;
        }
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (!visited.add(current)) {
                continue;
            }
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(name) && parameters.length == 1 && parameters[0].isAssignableFrom(propertyClass)) {
                    return method;
                }
            }
            Method interfaceMethod = findCompatibleInterfaceMethod(current, name, propertyClass, visited);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return null;
    }

    private static Method findCompatibleInterfaceMethod(Class<?> owner, String name, Class<?> propertyClass, Set<Class<?>> visited) {
        for (Class<?> interfaceClass : owner.getInterfaces()) {
            if (!visited.add(interfaceClass)) {
                continue;
            }
            for (Method method : interfaceClass.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals(name) && parameters.length == 1 && parameters[0].isAssignableFrom(propertyClass)) {
                    return method;
                }
            }
            Method nested = findCompatibleInterfaceMethod(interfaceClass, name, propertyClass, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static MethodHandle methodHandle(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            Method method = findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    private static MethodHandle staticMethodHandle(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
        return methodHandle(owner, names, parameterTypes);
    }

    private static Object getField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Field field = findField(owner, name);
            if (field != null) {
                try {
                    return field.get(target instanceof Class<?> ? null : target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    public static void setField(Object target, Object value, String... names) {
        if (target == null) {
            return;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Field field = findField(owner, name);
            if (field != null) {
                try {
                    field.set(target instanceof Class<?> ? null : target, value);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
    }

    private static Object getStaticField(Class<?> owner, String srgName, String mcpName) {
        for (String name : new String[] {srgName, mcpName}) {
            Field field = findField(owner, name);
            if (field != null) {
                try {
                    return field.get(null);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    public static Field findField(Class<?> owner, String name) {
        FieldKey key = new FieldKey(owner, name);
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_FIELDS.contains(key)) {
            return null;
        }
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                Field existing = FIELD_CACHE.putIfAbsent(key, field);
                return existing != null ? existing : field;
            } catch (NoSuchFieldException | SecurityException ignored) {
            }
        }
        MISSING_FIELDS.add(key);
        return null;
    }

    private static Field firstField(Class<?> owner, String... names) {
        for (String name : names) {
            Field field = findField(owner, name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    private static final class MethodKey {
        private final Class<?> owner;
        private final String name;
        private final Class<?>[] parameterTypes;
        private final int hash;

        private MethodKey(Class<?> owner, String name, Class<?>[] parameterTypes) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = parameterTypes != null ? parameterTypes : NO_PARAMETERS;
            int result = System.identityHashCode(owner);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            result = 31 * result + Arrays.hashCode(this.parameterTypes);
            this.hash = result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof MethodKey)) {
                return false;
            }
            MethodKey other = (MethodKey) object;
            return owner == other.owner
                    && (name == null ? other.name == null : name.equals(other.name))
                    && Arrays.equals(parameterTypes, other.parameterTypes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class MethodLookupCache {
        private static final int SIZE = 64;
        private final MethodLookupEntry[] entries = new MethodLookupEntry[SIZE];
        private boolean hit;

        private MethodLookupCache() {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new MethodLookupEntry();
            }
        }

        private Method lookup(Class<?> owner, String name, Class<?>[] parameterTypes) {
            MethodLookupEntry entry = entries[index(owner, name, parameterTypes)];
            hit = entry.matches(owner, name, parameterTypes);
            return hit ? entry.method : null;
        }

        private void store(Class<?> owner, String name, Class<?>[] parameterTypes, Method method) {
            entries[index(owner, name, parameterTypes)].set(owner, name, parameterTypes, method);
        }

        private static int index(Class<?> owner, String name, Class<?>[] parameterTypes) {
            int hash = System.identityHashCode(owner);
            hash = 31 * hash + (name != null ? name.hashCode() : 0);
            hash = 31 * hash + Arrays.hashCode(parameterTypes);
            return hash & (SIZE - 1);
        }
    }

    private static final class MethodLookupEntry {
        private Class<?> owner;
        private String name;
        private Class<?>[] parameterTypes;
        private Method method;

        private boolean matches(Class<?> owner, String name, Class<?>[] parameterTypes) {
            return this.owner == owner
                    && (this.name == name || this.name != null && this.name.equals(name))
                    && Arrays.equals(this.parameterTypes, parameterTypes);
        }

        private void set(Class<?> owner, String name, Class<?>[] parameterTypes, Method method) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = parameterTypes;
            this.method = method;
        }
    }

    private static final class StateValueMethodKey {
        private final Class<?> stateClass;
        private final Class<?> propertyClass;
        private final int hash;

        private StateValueMethodKey(Class<?> stateClass, Class<?> propertyClass) {
            this.stateClass = stateClass;
            this.propertyClass = propertyClass;
            int result = System.identityHashCode(stateClass);
            result = 31 * result + System.identityHashCode(propertyClass);
            this.hash = result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof StateValueMethodKey)) {
                return false;
            }
            StateValueMethodKey other = (StateValueMethodKey) object;
            return stateClass == other.stateClass && propertyClass == other.propertyClass;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class PropertyValueNameKey {
        private final IProperty<?> property;
        private final Comparable<?> value;
        private final int hash;

        private PropertyValueNameKey(IProperty<?> property, Comparable<?> value) {
            this.property = property;
            this.value = value;
            this.hash = 31 * System.identityHashCode(property) + System.identityHashCode(value);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj
                    || obj instanceof PropertyValueNameKey other
                    && property == other.property
                    && value == other.value;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class IdentityKey<T> {
        private final T value;
        private final int hash;

        private IdentityKey(T value) {
            this.value = value;
            this.hash = System.identityHashCode(value);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof IdentityKey<?> other)) {
                return false;
            }
            return value == other.value;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final class FieldKey {
        private final Class<?> owner;
        private final String name;
        private final int hash;

        private FieldKey(Class<?> owner, String name) {
            this.owner = owner;
            this.name = name;
            int result = System.identityHashCode(owner);
            result = 31 * result + (name != null ? name.hashCode() : 0);
            this.hash = result;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof FieldKey)) {
                return false;
            }
            FieldKey other = (FieldKey) object;
            return owner == other.owner && (name == null ? other.name == null : name.equals(other.name));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static int openGlHelperInt(String srgName, String mcpName, int fallback) {
        return intValue(getStaticField(OpenGlHelper.class, srgName, mcpName), fallback);
    }

    private static int framebufferInt(Framebuffer framebuffer, String srgName, String mcpName) {
        return intValue(getField(framebuffer, srgName, mcpName), 0);
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long longValue(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    private static float floatValue(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    private static double doubleValue(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
