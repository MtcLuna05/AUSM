package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(World.class)
public class BetterPortalsPortalBlockChangeMixin {
    @Unique
    private IBlockState ausm$previousPortalState;
    @Unique
    private BlockPos ausm$previousPortalPos;

    @ModifyVariable(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("HEAD"),
            argsOnly = true
    )
    private BlockPos ausm$capturePortalStateBeforeSetBlock(BlockPos capturedPos, BlockPos pos,
                                                           IBlockState newState, int flags) {
        World world = (World) (Object) this;
        if (!MinecraftReflectionCompat.worldIsRemote(world) || !BetterPortalsCompat.isInstalled() || pos == null) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return capturedPos;
        }

        IBlockState oldState = MinecraftReflectionCompat.worldBlockState(world, pos);
        if (!ausm$isPortalState(oldState) && !ausm$isPortalState(newState)) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return capturedPos;
        }

        ausm$previousPortalState = oldState;
        ausm$previousPortalPos = MinecraftReflectionCompat.blockPosToImmutable(pos);
        return capturedPos;
    }

    @ModifyReturnValue(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("RETURN")
    )
    private boolean ausm$refreshPortalTerrainAfterSetBlock(boolean changed, BlockPos pos,
                                                           IBlockState newState, int flags) {
        try {
            boolean oldPortal = ausm$isPortalState(ausm$previousPortalState);
            boolean newPortal = ausm$isPortalState(newState);
            if (!changed
                    || ausm$previousPortalPos == null
                    || !ausm$previousPortalPos.equals(pos)
                    || oldPortal == newPortal) {
                return changed;
            }

            World world = (World) (Object) this;
            IBlockState oldState = ausm$previousPortalState;
            BlockPos changedPos = ausm$previousPortalPos;
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            if (mc == null) {
                return changed;
            }
            MinecraftReflectionCompat.addScheduledTask(mc, () -> PipelineContext.getInstance()
                    .queueBetterPortalsPortalBlockChanged(world, changedPos, oldState, newState));
            return changed;
        } finally {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
        }
    }

    @Unique
    private boolean ausm$isPortalState(IBlockState state) {
        if (state == null) {
            return false;
        }

        Block block = MinecraftReflectionCompat.blockFromState(state);
        return BetterPortalsCompat.isBetterPortalsPortalBlock(block);
    }
}
