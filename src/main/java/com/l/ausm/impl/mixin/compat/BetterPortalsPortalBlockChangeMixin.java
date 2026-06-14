package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import net.minecraft.block.Block;
import net.minecraft.block.BlockPortal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

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
        if (!world.isRemote || !BetterPortalsCompat.isInstalled() || pos == null) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return;
        }

        IBlockState oldState = world.getBlockState(pos);
        if (!ausm$isPortalState(oldState) && !ausm$isPortalState(newState)) {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
            return;
        }

        ausm$previousPortalState = oldState;
        ausm$previousPortalPos = pos.toImmutable();
    }

    @Inject(
            method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("RETURN")
    )
    private void ausm$refreshPortalTerrainAfterSetBlock(BlockPos pos, IBlockState newState, int flags,
                                                        CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValueZ()
                    || ausm$previousPortalPos == null
                    || !ausm$previousPortalPos.equals(pos)
                    || (!ausm$isPortalState(ausm$previousPortalState) && !ausm$isPortalState(newState))) {
                return;
            }

            World world = (World) (Object) this;
            IBlockState oldState = ausm$previousPortalState;
            BlockPos changedPos = ausm$previousPortalPos;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) {
                return;
            }
            mc.addScheduledTask(() -> PipelineContext.getInstance()
                    .queueBetterPortalsPortalBlockChanged(world, changedPos, oldState, newState));
        } finally {
            ausm$previousPortalState = null;
            ausm$previousPortalPos = null;
        }
    }

    @Unique
    private boolean ausm$isPortalState(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }

        Block block = state.getBlock();
        if (block instanceof BlockPortal) {
            return true;
        }

        String className = block.getClass().getName().toLowerCase(Locale.ROOT);
        return className.contains("portal");
    }
}
