package com.l.ausm.impl.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.color.BlockColors;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import org.lwjgl.opengl.GL13;

abstract class MinecraftReflectionCompatBase {
    protected static final ConcurrentMap<MethodKey, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    protected static final Set<MethodKey> MISSING_METHODS = ConcurrentHashMap.newKeySet();

    protected static final ThreadLocal<MethodLookupCache> THREAD_METHOD_LOOKUP_CACHE =
            ThreadLocal.withInitial(MethodLookupCache::new);

    protected static final ThreadLocal<FieldLookupCache> THREAD_FIELD_LOOKUP_CACHE =
            ThreadLocal.withInitial(FieldLookupCache::new);

    protected static final ThreadLocal<BufferBuilder> THREAD_FLUID_STAGING_BUFFER =
            ThreadLocal.withInitial(() -> new BufferBuilder(512));

    protected static final ConcurrentMap<FieldKey, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    protected static final Set<FieldKey> MISSING_FIELDS = ConcurrentHashMap.newKeySet();

    protected static final ConcurrentMap<StateValueMethodKey, Method> STATE_VALUE_METHOD_CACHE = new ConcurrentHashMap<>();

    protected static final Set<StateValueMethodKey> MISSING_STATE_VALUE_METHODS = ConcurrentHashMap.newKeySet();

    protected static final ConcurrentMap<IProperty<?>, String> PROPERTY_NAME_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<PropertyValueNameKey, String> PROPERTY_VALUE_NAME_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<Block, ResourceLocation> BLOCK_REGISTRY_NAME_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<Class<?>, MethodHandle> BLOCK_LAYER_METHOD_HANDLES = new ConcurrentHashMap<>();

    protected static final Set<Class<?>> MISSING_BLOCK_LAYER_METHODS = ConcurrentHashMap.newKeySet();

    protected static final AtomicInteger BLOCK_LAYER_FAILURE_PROBES = new AtomicInteger();

    protected static final ConcurrentMap<ResourceLocation, String> RESOURCE_NAMESPACE_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<ResourceLocation, String> RESOURCE_PATH_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<ResourceLocation, String> RESOURCE_STRING_CACHE = new ConcurrentHashMap<>();

    protected static final ConcurrentMap<ResourceLocation, String> RESOURCE_PATH_LOWER_CACHE = new ConcurrentHashMap<>();

    protected static final ClassValue<String> LOWER_CLASS_NAME_CACHE = new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> type) {
            return type.getName().toLowerCase(Locale.ROOT);
        }
    };

    protected static final int HOT_IDENTITY_CACHE_LIMIT = 4096;

    protected static final ThreadLocal<IdentityHashMap<IBlockState, String>> THREAD_STATE_STRING_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    /**
     * Material and render type are immutable properties of an IBlockState.
     * Keep their high-frequency terrain answers local to the compiling/render
     * thread so a cached state never crosses threads or needs a global lock.
     */
    protected static final ThreadLocal<IdentityHashMap<IBlockState, Material>> THREAD_STATE_MATERIAL_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    protected static final ThreadLocal<IdentityHashMap<IBlockState, EnumBlockRenderType>> THREAD_STATE_RENDER_TYPE_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    protected static final ThreadLocal<IdentityHashMap<IBlockState, Block>> THREAD_STATE_BLOCK_CACHE =
            ThreadLocal.withInitial(IdentityHashMap::new);

    public static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    protected static final String[] PROVIDER_DIMENSION_NAMES = {"getDimension"};

    protected static final String[] PROVIDER_DIMENSION_TYPE_NAMES = {"func_186058_p", "getDimensionType"};

    protected static final String[] ITEM_STACK_ITEM_NAMES = {"func_77973_b", "getItem"};

    protected static final String[] BLOCK_RENDER_LAYER_NAMES = {"func_180664_k", "getRenderLayer"};

    protected static final String[] RENDER_PARTIAL_TICKS_NAMES = {"func_184121_ak", "getRenderPartialTicks"};

    protected static final String[] GL_UNIFORM_1I_NAMES = {"func_153163_f", "glUniform1i"};

    protected static final String[] GL_DISABLE_LIGHTING_NAMES = {"func_179140_f", "disableLighting"};

    protected static final String[] BUFFER_VERTEX_FORMAT_NAMES = {"func_178973_g", "getVertexFormat"};

    protected static final String[] TILE_ENTITY_POS_NAMES = {"func_174877_v", "getPos"};

    protected static final String[] TILE_ENTITY_INVALID_NAMES = {"func_145837_r", "isInvalid"};

    protected static final String[] TILE_ENTITY_RENDER_NAMES = {"func_192854_a", "render"};

    protected static final String[] CAMERA_FRUSTUM_NAMES = {"func_78546_a", "isBoundingBoxInFrustum"};

    protected static final Class<?>[] INT_INT_PARAMETERS = {int.class, int.class};

    protected static final Class<?>[] TILE_ENTITY_RENDER_PARAMETERS = {
            TileEntity.class, double.class, double.class, double.class, float.class, int.class, float.class
    };

    protected static final Class<?>[] AXIS_ALIGNED_BB_PARAMETERS = {AxisAlignedBB.class};

    protected static final MethodHandle MINECRAFT_INSTANCE_HANDLE = MinecraftReflectionCompat.staticMethodHandle(
            Minecraft.class, new String[]{"func_71410_x", "getMinecraft"}, NO_PARAMETERS
    );

    protected static final MethodHandle RESOURCE_NAMESPACE_HANDLE = MinecraftReflectionCompat.methodHandle(
            ResourceLocation.class,
            new String[]{"func_110624_b", "getResourceDomain", "getNamespace"},
            NO_PARAMETERS
    );

    protected static final MethodHandle RESOURCE_PATH_HANDLE = MinecraftReflectionCompat.methodHandle(
            ResourceLocation.class,
            new String[]{"func_110623_a", "getResourcePath", "getPath"},
            NO_PARAMETERS
    );

    protected static final Field MINECRAFT_CURRENT_SCREEN_FIELD = MinecraftReflectionCompat.firstField(
            Minecraft.class, "field_71462_r", "currentScreen"
    );

    protected static final Field MINECRAFT_WORLD_FIELD = MinecraftReflectionCompat.firstField(
            Minecraft.class, "field_71441_e", "world"
    );

    protected static final Field MINECRAFT_PLAYER_FIELD = MinecraftReflectionCompat.firstField(
            Minecraft.class, "field_71439_g", "player"
    );

    protected static final Field MINECRAFT_RENDER_GLOBAL_FIELD = MinecraftReflectionCompat.firstField(
            Minecraft.class, "field_71438_f", "renderGlobal"
    );

    protected static final Field MINECRAFT_GAME_SETTINGS_FIELD = MinecraftReflectionCompat.firstField(
            Minecraft.class, "field_71474_y", "gameSettings"
    );

    protected static final Field WORLD_REMOTE_FIELD = MinecraftReflectionCompat.firstField(World.class, "field_72995_K", "isRemote");

    protected static final Field WORLD_PROVIDER_FIELD = MinecraftReflectionCompat.firstField(World.class, "field_73011_w", "provider");

    protected static final Field VEC_X_FIELD = MinecraftReflectionCompat.firstField(Vec3d.class, "field_72450_a", "x");

    protected static final Field VEC_Y_FIELD = MinecraftReflectionCompat.firstField(Vec3d.class, "field_72448_b", "y");

    protected static final Field VEC_Z_FIELD = MinecraftReflectionCompat.firstField(Vec3d.class, "field_72449_c", "z");

    protected static final MethodHandle CURRENT_RENDER_LAYER_HANDLE = MinecraftReflectionCompat.staticMethodHandle(
            MinecraftForgeClient.class,
            new String[]{"getRenderLayer"},
            NO_PARAMETERS
    );

    protected static final MethodHandle FORGE_RENDER_PASS_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            MinecraftForgeClient.class,
            new String[]{"getRenderPass"},
            NO_PARAMETERS,
            MethodType.methodType(int.class)
    );

    protected static final MethodHandle PROVIDER_DIMENSION_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            WorldProvider.class, PROVIDER_DIMENSION_NAMES, NO_PARAMETERS,
            MethodType.methodType(int.class, WorldProvider.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_SIZE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177338_f", "getSize"}, NO_PARAMETERS,
            MethodType.methodType(int.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_INTEGER_SIZE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_181719_f", "getIntegerSize"}, NO_PARAMETERS,
            MethodType.methodType(int.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_ELEMENT_COUNT_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177345_h", "getElementCount"}, NO_PARAMETERS,
            MethodType.methodType(int.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_ELEMENT_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177348_c", "getElement"}, new Class<?>[]{int.class},
            MethodType.methodType(VertexFormatElement.class, VertexFormat.class, int.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_OFFSET_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_181720_d", "getOffset"}, new Class<?>[]{int.class},
            MethodType.methodType(int.class, VertexFormat.class, int.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_HAS_COLOR_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177346_d", "hasColor"}, NO_PARAMETERS,
            MethodType.methodType(boolean.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_COLOR_OFFSET_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177340_e", "getColorOffset"}, NO_PARAMETERS,
            MethodType.methodType(int.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_HAS_NORMAL_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177350_b", "hasNormal"}, NO_PARAMETERS,
            MethodType.methodType(boolean.class, VertexFormat.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_HAS_UV_OFFSET_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177347_a", "hasUvOffset"}, new Class<?>[]{int.class},
            MethodType.methodType(boolean.class, VertexFormat.class, int.class)
    );

    protected static final MethodHandle VERTEX_FORMAT_UV_OFFSET_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            VertexFormat.class, new String[]{"func_177344_b", "getUvOffsetById"}, new Class<?>[]{int.class},
            MethodType.methodType(int.class, VertexFormat.class, int.class)
    );

    protected static final MethodHandle BLOCK_FROM_STATE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            IBlockState.class, new String[]{"func_177230_c", "getBlock"}, NO_PARAMETERS,
            MethodType.methodType(Block.class, IBlockState.class)
    );

    protected static final MethodHandle STATE_ACTUAL_STATE_HANDLE = MinecraftReflectionCompat.methodHandle(
            IBlockState.class,
            new String[]{"func_185899_b", "getActualState"},
            new Class<?>[]{IBlockAccess.class, BlockPos.class}
    );

    protected static final MethodHandle STATE_MATERIAL_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            IBlockState.class, new String[]{"func_185904_a", "getMaterial"}, NO_PARAMETERS,
            MethodType.methodType(Material.class, IBlockState.class)
    );

    protected static final MethodHandle MATERIAL_IS_LIQUID_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            Material.class, new String[]{"func_76224_d", "isLiquid"}, NO_PARAMETERS,
            MethodType.methodType(boolean.class, Material.class)
    );

    protected static final MethodHandle STATE_RENDER_TYPE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            IBlockState.class,
            // getRenderType is func_185901_i in 1.12. func_185911_a is a
            // three-argument side-rendering method; using it made the hot
            // handle miss on SRG runtimes and forced reflective fallback.
            new String[]{"func_185901_i", "getRenderType"},
            NO_PARAMETERS,
            MethodType.methodType(EnumBlockRenderType.class, IBlockState.class)
    );

    protected static final Material MATERIAL_FIRE = MinecraftReflectionCompat.field(
            Material.class, Material.class, null, "field_151581_o", "FIRE"
    );

    protected static final Material MATERIAL_WATER = MinecraftReflectionCompat.field(
            Material.class, Material.class, null, "field_151586_h", "WATER"
    );

    protected static final MethodHandle STATE_LIGHT_VALUE_HANDLE = MinecraftReflectionCompat.methodHandle(
            IBlockState.class, new String[]{"func_185906_d", "getLightValue"}, NO_PARAMETERS
    );

    protected static final MethodHandle STATE_PACKED_LIGHTMAP_HANDLE = MinecraftReflectionCompat.methodHandle(
            IBlockState.class,
            new String[]{"func_185889_a", "getPackedLightmapCoords"},
            new Class<?>[]{IBlockAccess.class, BlockPos.class}
    );

    protected static final MethodHandle BAKED_QUAD_SPRITE_HANDLE = MinecraftReflectionCompat.methodHandle(
            BakedQuad.class, new String[]{"func_187508_a", "getSprite"}, NO_PARAMETERS
    );

    protected static final MethodHandle BAKED_QUAD_VERTEX_DATA_HANDLE = MinecraftReflectionCompat.methodHandle(
            BakedQuad.class, new String[]{"func_178209_a", "getVertexData"}, NO_PARAMETERS
    );

    protected static final MethodHandle BAKED_QUAD_PIPE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            BakedQuad.class, new String[]{"pipe"}, new Class<?>[]{IVertexConsumer.class},
            MethodType.methodType(void.class, BakedQuad.class, IVertexConsumer.class)
    );

    protected static final MethodHandle BLOCK_COLOR_MULTIPLIER_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockColors.class,
            new String[]{"func_186724_a", "func_189991_a", "colorMultiplier"},
            new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class, int.class}
    );

    protected static final MethodHandle BLOCK_POS_X_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockPos.class, new String[]{"func_177958_n", "getX"}, NO_PARAMETERS
    );

    protected static final MethodHandle BLOCK_POS_Y_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockPos.class, new String[]{"func_177956_o", "getY"}, NO_PARAMETERS
    );

    protected static final MethodHandle BLOCK_POS_Z_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockPos.class, new String[]{"func_177952_p", "getZ"}, NO_PARAMETERS
    );

    protected static final MethodHandle TILE_ENTITY_POS_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            TileEntity.class, TILE_ENTITY_POS_NAMES, NO_PARAMETERS,
            MethodType.methodType(BlockPos.class, TileEntity.class)
    );

    protected static final MethodHandle TILE_ENTITY_INVALID_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            TileEntity.class, TILE_ENTITY_INVALID_NAMES, NO_PARAMETERS,
            MethodType.methodType(boolean.class, TileEntity.class)
    );

    protected static final Field BLOCK_POS_X_FIELD = MinecraftReflectionCompat.firstField(BlockPos.class, "field_177962_a", "x");

    protected static final Field BLOCK_POS_Y_FIELD = MinecraftReflectionCompat.firstField(BlockPos.class, "field_177960_b", "y");

    protected static final Field BLOCK_POS_Z_FIELD = MinecraftReflectionCompat.firstField(BlockPos.class, "field_177961_c", "z");

    protected static final MethodHandle WORLD_CAN_SEE_SKY_HANDLE = MinecraftReflectionCompat.methodHandle(
            World.class, new String[]{"func_175678_i", "canSeeSky"}, new Class<?>[]{BlockPos.class}
    );

    protected static final MethodHandle WORLD_IS_BLOCK_LOADED_HANDLE = MinecraftReflectionCompat.methodHandle(
            World.class, new String[]{"func_175667_e", "isBlockLoaded"}, new Class<?>[]{BlockPos.class}
    );

    protected static final MethodHandle WORLD_IS_BLOCK_LOADED_ALLOW_EMPTY_HANDLE = MinecraftReflectionCompat.methodHandle(
            World.class, new String[]{"func_175668_a", "isBlockLoaded"},
            new Class<?>[]{BlockPos.class, boolean.class}
    );

    protected static final MethodHandle BLOCK_META_FROM_STATE_HANDLE = MinecraftReflectionCompat.methodHandle(
            Block.class, new String[]{"func_176201_c", "getMetaFromState"},
            new Class<?>[]{IBlockState.class}
    );

    protected static final MethodHandle BLOCK_RENDER_LAYER_HANDLE = MinecraftReflectionCompat.methodHandle(
            Block.class, BLOCK_RENDER_LAYER_NAMES, NO_PARAMETERS
    );

    protected static final MethodHandle RENDER_BLOCK_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockRendererDispatcher.class, new String[]{"func_175018_a", "renderBlock"},
            new Class<?>[]{IBlockState.class, BlockPos.class, IBlockAccess.class, BufferBuilder.class}
    );

    protected static final MethodHandle REGION_BUFFER_FOR_LAYER_HANDLE = MinecraftReflectionCompat.methodHandle(
            RegionRenderCacheBuilder.class, new String[]{"func_179038_a", "getWorldRendererByLayer"},
            new Class<?>[]{BlockRenderLayer.class}
    );

    protected static final MethodHandle REGION_BUFFER_FOR_LAYER_ID_HANDLE = MinecraftReflectionCompat.methodHandle(
            RegionRenderCacheBuilder.class, new String[]{"func_179039_a", "getWorldRendererByLayerId"},
            new Class<?>[]{int.class}
    );

    protected static final MethodHandle BLOCK_ACCESS_TILE_ENTITY_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            IBlockAccess.class,
            new String[]{"func_175625_s", "getTileEntity"},
            new Class<?>[]{BlockPos.class},
            MethodType.methodType(TileEntity.class, IBlockAccess.class, BlockPos.class)
    );

    protected static final MethodHandle CLIENT_ACTIVE_TEXTURE_HANDLE = MinecraftReflectionCompat.exactMethodHandle(
            OpenGlHelper.class,
            new String[]{"func_77472_b", "setClientActiveTexture"},
            new Class<?>[]{int.class},
            MethodType.methodType(void.class, int.class)
    );

    protected static final int DEFAULT_TEX_UNIT = MinecraftReflectionCompat.staticIntField(
            OpenGlHelper.class, GL13.GL_TEXTURE0, "field_77478_a", "defaultTexUnit"
    );

    protected static final int LIGHTMAP_TEX_UNIT = MinecraftReflectionCompat.staticIntField(
            OpenGlHelper.class, GL13.GL_TEXTURE1, "field_77476_b", "lightmapTexUnit"
    );

    protected static final MethodHandle BLOCK_POS_UP_HANDLE = MinecraftReflectionCompat.methodHandle(
            BlockPos.class, new String[]{"func_177984_a", "up"}, NO_PARAMETERS
    );

    protected static final MethodHandle HELD_ITEM_MAINHAND_HANDLE = MinecraftReflectionCompat.methodHandle(
            EntityLivingBase.class, new String[]{"func_184614_ca", "getHeldItemMainhand"}, NO_PARAMETERS
    );

    protected static final MethodHandle HELD_ITEM_OFFHAND_HANDLE = MinecraftReflectionCompat.methodHandle(
            EntityLivingBase.class, new String[]{"func_184592_cb", "getHeldItemOffhand"}, NO_PARAMETERS
    );

    protected static final Field ENTITY_DEAD_FIELD = MinecraftReflectionCompat.firstField(Entity.class, "field_70128_L", "isDead");

    protected static final ClassValue<Field> AMBIENT_OCCLUSION_MULTIPLIER_FIELDS = new ClassValue<Field>() {
        @Override
        protected Field computeValue(Class<?> type) {
            return MinecraftReflectionCompat.firstField(type, "field_178206_b", "vertexColorMultiplier");
        }
    };

    protected static final class CullFaceBackCall {
        static final Method METHOD = findMethod();
        static final Object BACK = findBackValue(METHOD);

        static boolean invoke() {
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

        static Method findMethod() {
            for (String name : new String[]{"func_187407_a", "cullFace"}) {
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

        static Object findBackValue(Method method) {
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

    public record OpenBlocksTankFluidInfo(boolean present, int amount, int color, String name) {
        protected static final OpenBlocksTankFluidInfo EMPTY =
                new OpenBlocksTankFluidInfo(false, -1, -1, "not-openblocks-tank");
    }

    protected static final Set<String> FAILED_HOT_PATH_HANDLES = ConcurrentHashMap.newKeySet();

    protected static final class MethodKey {
        final Class<?> owner;
        final String name;
        final Class<?>[] parameterTypes;
        final int hash;

        MethodKey(Class<?> owner, String name, Class<?>[] parameterTypes) {
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

    protected static final class MethodLookupCache {
        static final int SIZE = 64;
        final MethodLookupEntry[] entries = new MethodLookupEntry[SIZE];
        boolean hit;

        MethodLookupCache() {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new MethodLookupEntry();
            }
        }

        Method lookup(Class<?> owner, String name, Class<?>[] parameterTypes) {
            MethodLookupEntry entry = entries[index(owner, name, parameterTypes)];
            hit = entry.matches(owner, name, parameterTypes);
            return hit ? entry.method : null;
        }

        void store(Class<?> owner, String name, Class<?>[] parameterTypes, Method method) {
            entries[index(owner, name, parameterTypes)].set(owner, name, parameterTypes, method);
        }

        static int index(Class<?> owner, String name, Class<?>[] parameterTypes) {
            int hash = System.identityHashCode(owner);
            hash = 31 * hash + (name != null ? name.hashCode() : 0);
            hash = 31 * hash + Arrays.hashCode(parameterTypes);
            return hash & (SIZE - 1);
        }
    }

    protected static final class MethodLookupEntry {
        Class<?> owner;
        String name;
        Class<?>[] parameterTypes;
        Method method;

        boolean matches(Class<?> owner, String name, Class<?>[] parameterTypes) {
            return this.owner == owner
                    && (this.name == name || this.name != null && this.name.equals(name))
                    && Arrays.equals(this.parameterTypes, parameterTypes);
        }

        void set(Class<?> owner, String name, Class<?>[] parameterTypes, Method method) {
            this.owner = owner;
            this.name = name;
            this.parameterTypes = parameterTypes;
            this.method = method;
        }
    }

    protected static final class FieldLookupCache {
        static final int SIZE = 64;
        final FieldLookupEntry[] entries = new FieldLookupEntry[SIZE];
        boolean hit;

        FieldLookupCache() {
            for (int i = 0; i < entries.length; i++) {
                entries[i] = new FieldLookupEntry();
            }
        }

        Field lookup(Class<?> owner, String name) {
            FieldLookupEntry entry = entries[index(owner, name)];
            hit = entry.matches(owner, name);
            return hit ? entry.field : null;
        }

        void store(Class<?> owner, String name, Field field) {
            entries[index(owner, name)].set(owner, name, field);
        }

        static int index(Class<?> owner, String name) {
            int hash = 31 * System.identityHashCode(owner) + name.hashCode();
            return hash & (SIZE - 1);
        }
    }

    protected static final class FieldLookupEntry {
        Class<?> owner;
        String name;
        Field field;

        boolean matches(Class<?> owner, String name) {
            return this.owner == owner && (this.name == name || this.name != null && this.name.equals(name));
        }

        void set(Class<?> owner, String name, Field field) {
            this.owner = owner;
            this.name = name;
            this.field = field;
        }
    }

    protected static final class StateValueMethodKey {
        final Class<?> stateClass;
        final Class<?> propertyClass;
        final int hash;

        StateValueMethodKey(Class<?> stateClass, Class<?> propertyClass) {
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

    protected static final class PropertyValueNameKey {
        final IProperty<?> property;
        final Comparable<?> value;
        final int hash;

        PropertyValueNameKey(IProperty<?> property, Comparable<?> value) {
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

    protected static final class FieldKey {
        final Class<?> owner;
        final String name;
        final int hash;

        FieldKey(Class<?> owner, String name) {
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

    protected final MinecraftReflectionCompat self() {
        return (MinecraftReflectionCompat) this;
    }
}
