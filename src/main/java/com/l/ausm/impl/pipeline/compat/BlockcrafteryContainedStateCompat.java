package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

/**
 * Reads Blockcraftery's own copied block state.  This intentionally does not
 * consume GPOM provenance, material features, quads, overlays, or shape data:
 * a filled frame is represented solely by its contained {@link IBlockState}.
 */
public final class BlockcrafteryContainedStateCompat {
    private static final String BLOCKCRAFTERY = "blockcraftery";
    private static final String EDITABLE_TILE = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String ENDERIO_GLASS_CLASS_PREFIX = "crazypants.enderio.base.material.glass.";

    private BlockcrafteryContainedStateCompat() {
    }

    public static IBlockState containedState(IBlockState hostState, IBlockAccess blockAccess, BlockPos pos) {
        if (!isEditableFrame(hostState) || blockAccess == null || pos == null) {
            return null;
        }
        IBlockState state = stateFromTile(MinecraftReflectionCompat.blockAccessTileEntity(blockAccess, pos), hostState);
        if (state != null) {
            return state;
        }

        // Asynchronous world slices can omit the tile's live state.  Their
        // backing client world remains the Blockcraftery source of truth.
        IBlockAccess backing = MinecraftReflectionCompat.call(blockAccess, IBlockAccess.class, null,
                new String[] {"getWorld"}, MinecraftReflectionCompat.NO_PARAMETERS);
        if (backing != null && backing != blockAccess) {
            state = stateFromTile(MinecraftReflectionCompat.blockAccessTileEntity(backing, pos), hostState);
            if (state != null) {
                return state;
            }
        }
        IBlockAccess clientWorld = MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft());
        if (clientWorld != null && clientWorld != blockAccess && clientWorld != backing) {
            return stateFromTile(MinecraftReflectionCompat.blockAccessTileEntity(clientWorld, pos), hostState);
        }
        return null;
    }

    public static boolean isEditableFrame(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        ResourceLocation name = block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
        return BLOCKCRAFTERY.equals(MinecraftReflectionCompat.resourceNamespace(name));
    }

    /**
     * EnderIO fused quartz and fused glass both declare SOLID at the block
     * level, while both source sprites carry binary alpha. They therefore
     * belong in CUTOUT after their connected-model payload is resolved.
     * Do not infer a layer from smart-model quads: the mapper can provide
     * generic geometry in every queried layer and would incorrectly move
     * fused quartz into a transparent pass.
     */
    public static BlockRenderLayer enderIoGlassRenderLayer(IBlockState state) {
        return isEnderIoFusedQuartzState(state)
                ? BlockRenderLayer.CUTOUT : null;
    }

    public static boolean isEnderIoGlassState(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        return block != null && block.getClass().getName().startsWith(ENDERIO_GLASS_CLASS_PREFIX);
    }

    public static boolean isEnderIoFusedQuartzState(IBlockState state) {
        return isEnderIoGlassState(state);
    }

    private static IBlockState stateFromTile(TileEntity tile, IBlockState hostState) {
        if (tile == null || !EDITABLE_TILE.equals(tile.getClass().getName())) {
            return null;
        }
        IBlockState contained = MinecraftReflectionCompat.field(tile, IBlockState.class, null, "state");
        Block containedBlock = MinecraftReflectionCompat.blockFromState(contained);
        Block hostBlock = MinecraftReflectionCompat.blockFromState(hostState);
        if (containedBlock == null || containedBlock == hostBlock || isAir(containedBlock)
                || isBlockcraftery(containedBlock)) {
            return null;
        }
        return contained;
    }

    private static boolean isAir(Block block) {
        ResourceLocation name = MinecraftReflectionCompat.blockRegistryName(block);
        return "minecraft".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "air".equals(MinecraftReflectionCompat.resourcePathLower(name));
    }

    private static boolean isBlockcraftery(Block block) {
        return BLOCKCRAFTERY.equals(MinecraftReflectionCompat.resourceNamespace(
                MinecraftReflectionCompat.blockRegistryName(block)));
    }
}
