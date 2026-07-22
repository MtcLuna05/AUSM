package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Reads only GPOM's persisted framed-material contract. */
public final class GpomFramedMaterialCompat {
    private static final String FRAMED_DATA_CLASS = "com.l.gpom.compat.framed.FramedMaterialData";
    private static final String EFFECTIVE_STATE_CLASS = "com.l.gpom.compat.framed.FramedBlockEffectiveState";
    private static final String FRAMED_DATA_TAG = "gpom:material_state";
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];
    private static final Material EMPTY = new Material(false, 0, false, null, null);

    private GpomFramedMaterialCompat() {
    }

    public static Material material(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess == null || pos == null) {
            return EMPTY;
        }
        TileEntity tile = MinecraftReflectionCompat.blockAccessTileEntity(blockAccess, pos);
        Material material = material(tile);
        if (material.present()) {
            return material;
        }
        material = persistedMaterial(tile);
        if (material.present()) {
            return material;
        }

        // Celeritas compiles against a cloned WorldSlice. Its block-entity
        // snapshot can omit GPOM's runtime attachment, while the backing world
        // owns the authoritative persisted material data.
        IBlockAccess backingWorld = MinecraftReflectionCompat.call(blockAccess, IBlockAccess.class, null,
                new String[] {"getWorld"}, NO_PARAMETERS);
        if (backingWorld != null && backingWorld != blockAccess) {
            Material liveMaterial = material(MinecraftReflectionCompat.blockAccessTileEntity(backingWorld, pos));
            if (liveMaterial.present()) {
                return liveMaterial;
            }
            liveMaterial = persistedMaterial(MinecraftReflectionCompat.blockAccessTileEntity(backingWorld, pos));
            if (liveMaterial.present()) {
                return liveMaterial;
            }
            Material effective = effectiveMaterial(backingWorld, pos);
            if (effective.present()) {
                return effective;
            }
        }
        return effectiveMaterial(blockAccess, pos);
    }

    public static Material material(Object tile) {
        if (tile == null) {
            return EMPTY;
        }
        Object rawData = MinecraftReflectionCompat.invoke(tile,
                new String[] {"gpom$getFramedMaterialData"}, NO_PARAMETERS);
        if (!(rawData instanceof NBTTagCompound)) {
            return EMPTY;
        }

        NBTTagCompound data = (NBTTagCompound) rawData;
        return materialFromData(tile, data);
    }

    private static Material materialFromData(Object tile, NBTTagCompound data) {
        String source = string(data, "source");
        if (!"architecturecraft".equals(source) && !"blockcraftery".equals(source)) {
            return EMPTY;
        }
        Object states = states(tile, source);
        boolean present = booleanValue(MinecraftReflectionCompat.invoke(states, new String[] {"present"}, NO_PARAMETERS));
        IBlockState primary = present
                ? MinecraftReflectionCompat.call(states, IBlockState.class, null, new String[] {"primary"}, NO_PARAMETERS)
                : savedState(data, "primary");
        IBlockState secondary = present
                ? MinecraftReflectionCompat.call(states, IBlockState.class, null, new String[] {"secondary"}, NO_PARAMETERS)
                : savedState(data, "secondary");
        if (primary == null && secondary == null) {
            return EMPTY;
        }
        NBTTagCompound features = compound(data, "features");
        int emission = integer(features, "emission");
        boolean bloom = booleanValue(features, "bloom");
        if (emission <= 0) {
            emission = Math.max(stateVisualEmission(primary), stateVisualEmission(secondary));
        }
        boolean bloomFeaturePresent = features != null
                && MinecraftReflectionCompat.callBoolean(features, new String[] {"hasKey"},
                new Class<?>[] {String.class}, false, "bloom");
        if (!bloom && !bloomFeaturePresent) {
            bloom = stateHasBloomLayer(primary) || stateHasBloomLayer(secondary);
        }
        return new Material(true, emission, bloom, primary, secondary);
    }

    private static Material persistedMaterial(TileEntity tile) {
        NBTTagCompound root = persistedTileData(tile);
        NBTTagCompound data = compound(root, FRAMED_DATA_TAG);
        return data != null ? materialFromData(tile, data) : EMPTY;
    }

    private static NBTTagCompound persistedTileData(TileEntity tile) {
        if (tile == null) {
            return null;
        }
        Object result = MinecraftReflectionCompat.invoke(tile,
                new String[] {"func_189515_b", "writeToNBT"}, new Class<?>[] {NBTTagCompound.class}, new NBTTagCompound());
        return result instanceof NBTTagCompound ? (NBTTagCompound) result : null;
    }

    private static IBlockState savedState(NBTTagCompound data, String key) {
        NBTTagCompound saved = compound(data, key);
        String id = string(saved, "id");
        if (id.isEmpty()) {
            return null;
        }
        ResourceLocation name;
        try {
            name = new ResourceLocation(id);
        } catch (RuntimeException ignored) {
            return null;
        }
        Object rawBlock = MinecraftReflectionCompat.invoke(ForgeRegistries.BLOCKS,
                new String[] {"func_82594_a", "getObject", "getValue"},
                new Class<?>[] {ResourceLocation.class}, name);
        if (!(rawBlock instanceof Block)) {
            return null;
        }
        return MinecraftReflectionCompat.blockStateFromMeta((Block) rawBlock, integer(saved, "meta"));
    }

    private static int stateVisualEmission(IBlockState state) {
        if (state == null) {
            return 0;
        }
        return Math.max(0, MinecraftReflectionCompat.stateLightValue(state));
    }

    private static boolean stateHasBloomLayer(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return false;
        }
        for (net.minecraft.util.BlockRenderLayer layer : net.minecraft.util.BlockRenderLayer.values()) {
            if ("BLOOM".equals(layer.name()) && MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer)) {
                return true;
            }
        }
        return false;
    }

    private static Object states(Object tile, String source) {
        try {
            Class<?> framedData = Class.forName(FRAMED_DATA_CLASS, false, GpomFramedMaterialCompat.class.getClassLoader());
            return MinecraftReflectionCompat.invoke(framedData, new String[] {"states"},
                    new Class<?>[] {Object.class, String.class}, tile, source);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    private static Material effectiveMaterial(IBlockAccess blockAccess, BlockPos pos) {
        try {
            Class<?> effectiveState = Class.forName(EFFECTIVE_STATE_CLASS, false,
                    GpomFramedMaterialCompat.class.getClassLoader());
            IBlockState primary = MinecraftReflectionCompat.call(effectiveState, IBlockState.class, null,
                    new String[] {"state"}, new Class<?>[] {IBlockAccess.class, BlockPos.class}, blockAccess, pos);
            if (primary == null) {
                return EMPTY;
            }
            int emission = Math.max(0, MinecraftReflectionCompat.stateLightValue(primary));
            boolean bloom = false;
            net.minecraft.block.Block block = MinecraftReflectionCompat.blockFromState(primary);
            if (block != null) {
                for (net.minecraft.util.BlockRenderLayer layer : net.minecraft.util.BlockRenderLayer.values()) {
                    if ("BLOOM".equals(layer.name())
                            && MinecraftReflectionCompat.blockCanRenderInLayer(block, primary, layer)) {
                        bloom = true;
                        break;
                    }
                }
            }
            return new Material(true, emission, bloom, primary, null);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return EMPTY;
        }
    }

    private static NBTTagCompound compound(Object data, String key) {
        Object result = MinecraftReflectionCompat.invoke(data,
                new String[] {"func_74775_l", "getCompoundTag"}, new Class<?>[] {String.class}, key);
        return result instanceof NBTTagCompound ? (NBTTagCompound) result : null;
    }

    private static String string(Object data, String key) {
        Object result = MinecraftReflectionCompat.invoke(data,
                new String[] {"func_74779_i", "getString"}, new Class<?>[] {String.class}, key);
        return result instanceof String ? (String) result : "";
    }

    private static int integer(Object data, String key) {
        Object result = MinecraftReflectionCompat.invoke(data,
                new String[] {"func_74762_e", "getInteger"}, new Class<?>[] {String.class}, key);
        return result instanceof Number ? Math.max(0, ((Number) result).intValue()) : 0;
    }

    private static boolean booleanValue(Object data, String key) {
        Object result = MinecraftReflectionCompat.invoke(data,
                new String[] {"func_74767_n", "getBoolean"}, new Class<?>[] {String.class}, key);
        return booleanValue(result);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    public static final class Material {
        private final boolean present;
        private final int emission;
        private final boolean bloom;
        private final IBlockState primary;
        private final IBlockState secondary;

        private Material(boolean present, int emission, boolean bloom, IBlockState primary, IBlockState secondary) {
            this.present = present;
            this.emission = emission;
            this.bloom = bloom;
            this.primary = primary;
            this.secondary = secondary;
        }

        public boolean present() {
            return present;
        }

        public int emission() {
            return emission;
        }

        public boolean bloom() {
            return bloom;
        }

        public IBlockState primary() {
            return primary;
        }

        public IBlockState secondary() {
            return secondary;
        }
    }
}
