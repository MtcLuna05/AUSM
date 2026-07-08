package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class BetterPortalsPortalBlockChangeMixin {
    @Unique
    private IBlockState ausm$previousPortalState;
    @Unique
    private BlockPos ausm$previousPortalPos;

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("HEAD")
    )
    private void ausm$capturePortalStateBeforeSetBlock(BlockPos pos, IBlockState newState, int flags,
                                                       CallbackInfoReturnable<Boolean> cir) {
        World world = (World) (Object) this;
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean((world), false, "field_72995_K", "isRemote") || !BetterPortalsCompat.isInstalled() || pos == null) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return;
        }

        IBlockState oldState = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos);
        if (!ausm$isPortalState(oldState) && !ausm$isPortalState(newState)) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return;
        }

        ausm$previousPortalState = oldState;
        ausm$previousPortalPos = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToImmutable(pos);
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("RETURN")
    )
    private void ausm$refreshPortalTerrainAfterSetBlock(BlockPos pos, IBlockState newState, int flags,
                                                        CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean oldPortal = ausm$isPortalState(ausm$previousPortalState);
            boolean newPortal = ausm$isPortalState(newState);
            if (!cir.getReturnValueZ()
                    || ausm$previousPortalPos == null
                    || !ausm$previousPortalPos.equals(pos)
                    || oldPortal == newPortal) {
                return;
            }

            World world = (World) (Object) this;
            IBlockState oldState = ausm$previousPortalState;
            BlockPos changedPos = ausm$previousPortalPos;
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            if (mc == null) {
                return;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(mc, () -> PipelineContext.getInstance()
                    .queueBetterPortalsPortalBlockChanged(world, changedPos, oldState, newState));
        } finally {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
        }
    }

    @Unique
    private boolean ausm$isPortalState(IBlockState state) {
        if (state == null || com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }

        Block block = com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state);
        return BetterPortalsCompat.isBetterPortalsPortalBlock(block);
    }
}
