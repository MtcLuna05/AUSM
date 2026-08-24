package com.luna.ausm.impl.util;

import com.luna.ausm.impl.MainMod;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

abstract class MinecraftWorldEntityReflection extends MinecraftClientReflection {
    public static Vec3d look(Entity entity, float partialTicks) {
        Object value = MinecraftReflectionCompat.invoke(entity, new String[]{"func_70676_i", "getLook"}, new Class<?>[]{float.class}, partialTicks);
        if (value instanceof Vec3d) {
            return (Vec3d) value;
        }
        float pitch = MinecraftReflectionCompat.prevRotationPitch(entity) + (MinecraftReflectionCompat.rotationPitch(entity) - MinecraftReflectionCompat.prevRotationPitch(entity)) * partialTicks;
        float yaw = MinecraftReflectionCompat.prevRotationYaw(entity) + (MinecraftReflectionCompat.rotationYaw(entity) - MinecraftReflectionCompat.prevRotationYaw(entity)) * partialTicks;
        double yawRad = -yaw * Math.PI / 180.0D - Math.PI;
        double pitchRad = -pitch * Math.PI / 180.0D;
        double x = Math.sin(yawRad) * -Math.cos(pitchRad);
        double y = Math.sin(pitchRad);
        double z = Math.cos(yawRad) * -Math.cos(pitchRad);
        return new Vec3d(x, y, z);
    }

    public static ItemStack heldItemMainhand(EntityLivingBase entity) {
        if (entity != null && HELD_ITEM_MAINHAND_HANDLE != null) {
            try {
                Object value = HELD_ITEM_MAINHAND_HANDLE.invoke(entity);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.call(entity, ItemStack.class, null, new String[]{"func_184614_ca", "getHeldItemMainhand"}, NO_PARAMETERS);
    }

    public static ItemStack heldItemOffhand(EntityLivingBase entity) {
        if (entity != null && HELD_ITEM_OFFHAND_HANDLE != null) {
            try {
                Object value = HELD_ITEM_OFFHAND_HANDLE.invoke(entity);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.call(entity, ItemStack.class, null, new String[]{"func_184592_cb", "getHeldItemOffhand"}, NO_PARAMETERS);
    }

    public static boolean entityIsDead(Entity entity) {
        if (entity == null) {
            return true;
        }
        if (ENTITY_DEAD_FIELD != null) {
            try {
                return ENTITY_DEAD_FIELD.getBoolean(entity);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return MinecraftReflectionCompat.fieldBoolean(entity, false, "field_70128_L", "isDead");
    }

    public static boolean livingPotionActive(EntityLivingBase entity, Potion potion) {
        return potion != null && MinecraftReflectionCompat.booleanValue(MinecraftReflectionCompat.invoke(entity, new String[]{"func_70644_a", "isPotionActive"},
                new Class<?>[]{Potion.class}, potion));
    }

    public static PotionEffect livingActivePotionEffect(EntityLivingBase entity, Potion potion) {
        if (potion == null) {
            return null;
        }
        Object value = MinecraftReflectionCompat.invoke(entity, new String[]{"func_70660_b", "getActivePotionEffect"},
                new Class<?>[]{Potion.class}, potion);
        return value instanceof PotionEffect ? (PotionEffect) value : null;
    }

    public static boolean playerIsSpectator(Entity player) {
        return MinecraftReflectionCompat.callBoolean(player, new String[]{"func_175149_v", "isSpectator"}, NO_PARAMETERS, false);
    }

    public static Entity entityRidingEntity(Entity entity) {
        return MinecraftReflectionCompat.call(entity, Entity.class, null, new String[]{"func_184187_bx", "getRidingEntity"}, NO_PARAMETERS);
    }

    public static boolean itemStackIsEmpty(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        Object value = MinecraftReflectionCompat.invoke(stack, new String[]{"func_190926_b", "isEmpty"}, NO_PARAMETERS);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static Item itemStackItem(ItemStack stack) {
        return MinecraftReflectionCompat.call(stack, Item.class, null, ITEM_STACK_ITEM_NAMES, NO_PARAMETERS);
    }

    public static int itemId(Item item) {
        return MinecraftReflectionCompat.callInt(Item.class, new String[]{"func_150891_b", "getIdFromItem"},
                new Class<?>[]{Item.class}, 0, item);
    }

    public static int itemStackMetadata(ItemStack stack) {
        return MinecraftReflectionCompat.callInt(stack, new String[]{"func_77960_j", "getMetadata"}, NO_PARAMETERS, 0);
    }

    public static IBlockState blockStateFromMeta(Block block, int meta) {
        return MinecraftReflectionCompat.call(block, IBlockState.class, MinecraftReflectionCompat.blockDefaultState(block),
                new String[]{"func_176203_a", "getStateFromMeta"}, new Class<?>[]{int.class}, meta);
    }

    public static IBlockState blockDefaultState(Block block) {
        return MinecraftReflectionCompat.call(block, IBlockState.class, null, new String[]{"func_176223_P", "getDefaultState"}, NO_PARAMETERS);
    }

    public static IBlockState airDefaultState() {
        Block air = MinecraftReflectionCompat.field(Blocks.class, Block.class, null, "field_150350_a", "AIR");
        return air != null ? MinecraftReflectionCompat.blockDefaultState(air) : null;
    }

    public static int blockMetaFromState(Block block, IBlockState state) {
        if (block == null || state == null) {
            return 0;
        }
        if (BLOCK_META_FROM_STATE_HANDLE != null) {
            try {
                return (int) BLOCK_META_FROM_STATE_HANDLE.invoke(block, state);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    public static Material stateMaterial(IBlockState state) {
        if (state == null) {
            return null;
        }
        IdentityHashMap<IBlockState, Material> cache = THREAD_STATE_MATERIAL_CACHE.get();
        Material cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        Material material = null;
        if (STATE_MATERIAL_HANDLE != null) {
            try {
                material = (Material) STATE_MATERIAL_HANDLE.invokeExact(state);
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("stateMaterial", failure);
            }
        }
        if (material == null) {
            material = MinecraftReflectionCompat.call(state, Material.class, null,
                    new String[]{"func_185904_a", "getMaterial"}, NO_PARAMETERS);
        }
        if (material != null) {
            MinecraftReflectionCompat.cacheStateValue(cache);
            cache.put(state, material);
        }
        return material;
    }

    public static boolean stateMaterialIsFire(IBlockState state) {
        return MinecraftReflectionCompat.stateMaterial(state) == MATERIAL_FIRE;
    }

    public static boolean stateMaterialIsWater(IBlockState state) {
        return MinecraftReflectionCompat.stateMaterial(state) == MATERIAL_WATER;
    }

    public static EnumBlockRenderType stateRenderType(IBlockState state) {
        if (state == null) {
            return null;
        }
        IdentityHashMap<IBlockState, EnumBlockRenderType> cache = THREAD_STATE_RENDER_TYPE_CACHE.get();
        EnumBlockRenderType cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        if (STATE_RENDER_TYPE_HANDLE != null) {
            try {
                EnumBlockRenderType renderType = (EnumBlockRenderType) STATE_RENDER_TYPE_HANDLE.invokeExact(state);
                if (renderType != null) {
                    MinecraftReflectionCompat.cacheStateValue(cache);
                    cache.put(state, renderType);
                }
                return renderType;
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("stateRenderType", failure);
                // The handle was resolved and adapted at startup. An exception
                // here came from the target state itself (for example a
                // transient wrapper with no delegate); invoking the same method
                // reflectively only throws and allocates a second time.
                return null;
            }
        }
        EnumBlockRenderType renderType = MinecraftReflectionCompat.call(state, EnumBlockRenderType.class, null,
                new String[]{"func_185901_i", "getRenderType"}, NO_PARAMETERS);
        if (renderType != null) {
            MinecraftReflectionCompat.cacheStateValue(cache);
            cache.put(state, renderType);
        }
        return renderType;
    }

    protected static <T> void cacheStateValue(IdentityHashMap<IBlockState, T> cache) {
        if (cache.size() >= HOT_IDENTITY_CACHE_LIMIT) {
            cache.clear();
        }
    }

    public static int stateRenderTypeOrdinal(IBlockState state) {
        EnumBlockRenderType renderType = MinecraftReflectionCompat.stateRenderType(state);
        return renderType != null ? renderType.ordinal() : -1;
    }

    public static int stateLightValue(IBlockState state) {
        int direct = MinecraftReflectionCompat.invokeInt(STATE_LIGHT_VALUE_HANDLE, state, Integer.MIN_VALUE);
        if (direct != Integer.MIN_VALUE) {
            return direct;
        }
        return MinecraftReflectionCompat.callInt(state, new String[]{"func_185906_d", "getLightValue"}, NO_PARAMETERS, 0);
    }

    public static int stateLightValue(IBlockState state, IBlockAccess access, BlockPos pos) {
        if (state == null) {
            return 0;
        }
        Object value = access != null && pos != null
                ? MinecraftReflectionCompat.invoke(state, new String[]{"func_185906_d", "getLightValue"},
                new Class<?>[]{IBlockAccess.class, BlockPos.class}, access, pos)
                : null;
        return value instanceof Number ? ((Number) value).intValue() : MinecraftReflectionCompat.stateLightValue(state);
    }

    public static int statePackedLightmapCoords(IBlockState state, IBlockAccess access, BlockPos pos) {
        int direct = MinecraftReflectionCompat.invokeInt2(STATE_PACKED_LIGHTMAP_HANDLE, state, access, pos, Integer.MIN_VALUE);
        if (direct != Integer.MIN_VALUE) {
            return direct;
        }
        if (state != null && access != null && pos != null) {
            Object value = MinecraftReflectionCompat.invoke(state, new String[]{"func_185889_a", "getPackedLightmapCoords"},
                    new Class<?>[]{IBlockAccess.class, BlockPos.class}, access, pos);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return MinecraftReflectionCompat.blockAccessCombinedLight(access, pos, 0);
    }

    public static BlockRenderLayer currentRenderLayer() {
        if (CURRENT_RENDER_LAYER_HANDLE != null) {
            try {
                return (BlockRenderLayer) CURRENT_RENDER_LAYER_HANDLE.invokeExact();
            } catch (Throwable ignored) {
            }
        }
        return MinecraftReflectionCompat.callStatic(MinecraftForgeClient.class, BlockRenderLayer.class, null,
                new String[]{"getRenderLayer"}, NO_PARAMETERS);
    }

    public static int forgeRenderPass() {
        if (FORGE_RENDER_PASS_HANDLE != null) {
            try {
                return (int) FORGE_RENDER_PASS_HANDLE.invokeExact();
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("forgeRenderPass", failure);
                return 0;
            }
        }
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invokeStatic(MinecraftForgeClient.class,
                new String[]{"getRenderPass"}, NO_PARAMETERS), 0);
    }

    public static void setCurrentRenderLayer(BlockRenderLayer layer) {
        MinecraftReflectionCompat.invokeStatic(ForgeHooksClient.class,
                new String[]{"setRenderLayer"}, new Class<?>[]{BlockRenderLayer.class}, layer);
    }

    public static Object stateValue(Object state, Object property) {
        if (state == null || property == null) {
            return null;
        }
        Method method = MinecraftReflectionCompat.findStateValueMethod(state.getClass(), property.getClass());
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(state, property);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return null;
        }
    }

    protected static Method findStateValueMethod(Class<?> stateClass, Class<?> propertyClass) {
        StateValueMethodKey key = new StateValueMethodKey(stateClass, propertyClass);
        Method cached = STATE_VALUE_METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_STATE_VALUE_METHODS.contains(key)) {
            return null;
        }
        for (String name : new String[]{"func_177229_b", "getValue"}) {
            Method method = MinecraftReflectionCompat.findCompatibleDeclaredMethod(stateClass, name, propertyClass, new HashSet<>());
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
        Object value = MinecraftReflectionCompat.invoke(state, new String[]{"func_177228_b", "getProperties"}, NO_PARAMETERS);
        return value instanceof Map<?, ?>
                ? (Map<IProperty<?>, Comparable<?>>) value
                : Collections.emptyMap();
    }

    public static Comparable<?> statePropertyValue(IBlockState state, IProperty<?> property) {
        if (state == null || property == null) {
            return null;
        }
        Object value = MinecraftReflectionCompat.stateProperties(state).get(property);
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
        String name = MinecraftReflectionCompat.call(property, String.class, null, new String[]{"func_177701_a", "getName"}, NO_PARAMETERS);
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
        Object name = MinecraftReflectionCompat.invoke(property, new String[]{"func_177702_a", "getName"}, new Class<?>[]{Comparable.class}, value);
        String valueName = name instanceof String ? (String) name : String.valueOf(value);
        String existing = PROPERTY_VALUE_NAME_CACHE.putIfAbsent(key, valueName);
        return existing != null ? existing : valueName;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static IBlockState stateWithProperty(IBlockState state, IProperty property, Comparable value) {
        if (state == null || property == null || value == null) {
            return state;
        }
        return MinecraftReflectionCompat.call(state, IBlockState.class, state,
                new String[]{"func_177226_a", "withProperty"},
                new Class<?>[]{IProperty.class, Comparable.class}, property, value);
    }

    public static Block blockFromState(IBlockState state) {
        if (state == null) {
            return null;
        }
        IdentityHashMap<IBlockState, Block> cache = THREAD_STATE_BLOCK_CACHE.get();
        Block cached = cache.get(state);
        if (cached != null) {
            return cached;
        }
        if (BLOCK_FROM_STATE_HANDLE != null) {
            try {
                Block block = (Block) BLOCK_FROM_STATE_HANDLE.invokeExact(state);
                if (block != null) {
                    MinecraftReflectionCompat.cacheStateValue(cache);
                    cache.put(state, block);
                }
                return block;
            } catch (Throwable failure) {
                MinecraftReflectionCompat.logHotPathHandleFailure("blockFromState", failure);
                return null;
            }
        }
        Block block = MinecraftReflectionCompat.call(state, Block.class, null, new String[]{"func_177230_c", "getBlock"}, NO_PARAMETERS);
        if (block != null) {
            MinecraftReflectionCompat.cacheStateValue(cache);
            cache.put(state, block);
        }
        return block;
    }

    public static void clearHotThreadCaches() {
        THREAD_STATE_STRING_CACHE.get().clear();
        THREAD_STATE_MATERIAL_CACHE.get().clear();
        THREAD_STATE_RENDER_TYPE_CACHE.get().clear();
        THREAD_STATE_BLOCK_CACHE.get().clear();
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

    public static String lowerClassName(Object value) {
        return value != null ? LOWER_CLASS_NAME_CACHE.get(value.getClass()) : "";
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
            return MinecraftReflectionCompat.call(state, IBlockState.class, state, new String[]{"func_185899_b", "getActualState"},
                    new Class<?>[]{IBlockAccess.class, BlockPos.class}, blockAccess, pos);
        } catch (RuntimeException ignored) {
            return state;
        }
    }

    public static BlockRenderLayer blockRenderLayer(Block block) {
        if (block == null || BLOCK_RENDER_LAYER_HANDLE == null) {
            return null;
        }
        try {
            return (BlockRenderLayer) BLOCK_RENDER_LAYER_HANDLE.invoke(block);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean blockCanRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        if (block == null || state == null || layer == null) {
            return false;
        }
        MethodHandle handle = MinecraftReflectionCompat.blockLayerMethodHandle(block.getClass());
        if (handle != null) {
            try {
                return (boolean) handle.invokeExact(block, state, layer);
            } catch (Throwable failure) {
                int probe = BLOCK_LAYER_FAILURE_PROBES.incrementAndGet();
                if (probe <= 8) {
                    MainMod.LOGGER.warn(
                            "[AUSMBlockLayerCompat] invocation failed for {}: {}",
                            block.getClass().getName(), failure.toString());
                }
            }
        }
        return layer == MinecraftReflectionCompat.blockRenderLayer(block);
    }

    protected static MethodHandle blockLayerMethodHandle(Class<?> owner) {
        MethodHandle cached = BLOCK_LAYER_METHOD_HANDLES.get(owner);
        if (cached != null || MISSING_BLOCK_LAYER_METHODS.contains(owner)) {
            return cached;
        }
        Method method = MinecraftReflectionCompat.findMethod(owner, "canRenderInLayer",
                new Class<?>[]{IBlockState.class, BlockRenderLayer.class});
        if (method == null) {
            method = MinecraftReflectionCompat.findBlockLayerMethodByShape(owner);
        }
        if (method != null) {
            try {
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method).asType(
                        MethodType.methodType(boolean.class, Block.class, IBlockState.class, BlockRenderLayer.class));
                MethodHandle existing = BLOCK_LAYER_METHOD_HANDLES.putIfAbsent(owner, handle);
                return existing != null ? existing : handle;
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        MISSING_BLOCK_LAYER_METHODS.add(owner);
        int probe = BLOCK_LAYER_FAILURE_PROBES.incrementAndGet();
        if (probe <= 8) {
            MainMod.LOGGER.warn(
                    "[AUSMBlockLayerCompat] no compatible layer method for {}", owner.getName());
        }
        return null;
    }

    protected static Method findBlockLayerMethodByShape(Class<?> owner) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getReturnType() == boolean.class
                        && parameters.length == 2
                        && parameters[0] == IBlockState.class
                        && parameters[1] == BlockRenderLayer.class) {
                    return method;
                }
            }
        }
        return null;
    }

    public static ResourceLocation blockRegistryName(Block block) {
        if (block == null) {
            return null;
        }
        ResourceLocation cached = BLOCK_REGISTRY_NAME_CACHE.get(block);
        if (cached != null) {
            return cached;
        }
        ResourceLocation name = MinecraftReflectionCompat.call(block, ResourceLocation.class, null,
                new String[]{"getRegistryName"}, NO_PARAMETERS);
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
        Object direct = MinecraftReflectionCompat.invokeReference(RESOURCE_NAMESPACE_HANDLE, location);
        String namespace = direct instanceof String
                ? (String) direct
                : MinecraftReflectionCompat.call(location, String.class, "",
                new String[]{"func_110624_b", "getResourceDomain", "getNamespace"}, NO_PARAMETERS);
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
        Object direct = MinecraftReflectionCompat.invokeReference(RESOURCE_PATH_HANDLE, location);
        String path = direct instanceof String
                ? (String) direct
                : MinecraftReflectionCompat.call(location, String.class, "",
                new String[]{"func_110623_a", "getResourcePath", "getPath"}, NO_PARAMETERS);
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
        String lower = MinecraftReflectionCompat.resourcePath(location).toLowerCase(Locale.ROOT);
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
        Object value = MinecraftReflectionCompat.invoke(registry, new String[]{"func_148742_b", "getKeys"}, NO_PARAMETERS);
        return value instanceof Iterable<?> ? (Iterable<ResourceLocation>) value : Collections.emptyList();
    }

    public static boolean blockIsAir(Block block, IBlockState state, IBlockAccess access, BlockPos pos) {
        Object value = MinecraftReflectionCompat.invoke(block, new String[]{"isAir"}, new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class},
                state, access, pos);
        return value instanceof Boolean ? (Boolean) value : block == MinecraftReflectionCompat.field(Blocks.class, Block.class, null, "field_150350_a", "AIR");
    }

    public static boolean blockIsSideSolid(Block block, IBlockState state, IBlockAccess access, BlockPos pos, EnumFacing side, boolean fallback) {
        Object value = MinecraftReflectionCompat.invoke(block, new String[]{"isSideSolid"},
                new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class, EnumFacing.class},
                state, access, pos, side);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    public static boolean shouldRenderInPass(Entity entity, int pass) {
        Object value = MinecraftReflectionCompat.invoke(entity, new String[]{"shouldRenderInPass"}, new Class<?>[]{int.class}, pass);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static void setRenderChunksMany(Minecraft minecraft, boolean value) {
        MinecraftReflectionCompat.setField(minecraft, value, "field_175612_E", "renderChunksMany");
    }

    public static WorldClient world(Minecraft minecraft) {
        return MinecraftReflectionCompat.minecraftField(minecraft, MINECRAFT_WORLD_FIELD, WorldClient.class);
    }

    public static EntityPlayerSP player(Minecraft minecraft) {
        return MinecraftReflectionCompat.minecraftField(minecraft, MINECRAFT_PLAYER_FIELD, EntityPlayerSP.class);
    }

    public static RenderGlobal renderGlobal(Minecraft minecraft) {
        return MinecraftReflectionCompat.minecraftField(minecraft, MINECRAFT_RENDER_GLOBAL_FIELD, RenderGlobal.class);
    }

    protected static <T> T minecraftField(Minecraft minecraft, Field field, Class<T> type) {
        if (minecraft == null || field == null) {
            return null;
        }
        try {
            Object value = field.get(minecraft);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static GameSettings gameSettings(Minecraft minecraft) {
        if (minecraft == null || MINECRAFT_GAME_SETTINGS_FIELD == null) {
            return null;
        }
        try {
            return (GameSettings) MINECRAFT_GAME_SETTINGS_FIELD.get(minecraft);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
    }

    public static int renderDistanceChunks(Minecraft minecraft) {
        return MinecraftReflectionCompat.renderDistanceChunks(MinecraftReflectionCompat.gameSettings(minecraft), -1);
    }

    public static int renderDistanceChunks(GameSettings settings, int fallback) {
        return MinecraftReflectionCompat.fieldInt(settings, fallback, "field_151451_c", "renderDistanceChunks");
    }

    public static boolean hideGui(GameSettings settings) {
        return MinecraftReflectionCompat.fieldBoolean(settings, false, "field_74319_N", "hideGUI");
    }

    public static boolean showDebugInfo(GameSettings settings) {
        return MinecraftReflectionCompat.fieldBoolean(settings, false, "field_74330_P", "showDebugInfo");
    }

    public static int thirdPersonView(GameSettings settings) {
        return MinecraftReflectionCompat.fieldInt(settings, 0, "field_74320_O", "thirdPersonView");
    }

    public static boolean keyBindingIsPressed(KeyBinding binding) {
        return MinecraftReflectionCompat.callBoolean(binding, new String[]{"func_151468_f", "isPressed"}, NO_PARAMETERS, false);
    }

    public static FontRenderer fontRenderer(Minecraft minecraft) {
        return MinecraftReflectionCompat.field(minecraft, FontRenderer.class, null, "field_71466_p", "fontRenderer");
    }
}
