package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public class WorldDynamicLightMixin {
    private static final IWorldEventListener AUSM_NOOP_WORLD_EVENT_LISTENER = new IWorldEventListener() {
        @Override
        public void notifyBlockUpdate(World worldIn, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        }

        @Override
        public void notifyLightSet(BlockPos pos) {
        }

        @Override
        public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        }

        @Override
        public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent soundIn, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        }

        @Override
        public void playRecord(SoundEvent soundIn, BlockPos pos) {
        }

        @Override
        public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void spawnParticle(int id, boolean ignoreRange, boolean minimiseParticleLevel, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void onEntityAdded(Entity entityIn) {
        }

        @Override
        public void onEntityRemoved(Entity entityIn) {
        }

        @Override
        public void broadcastSound(int soundID, BlockPos pos, int data) {
        }

        @Override
        public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) {
        }

        @Override
        public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
        }
    };

    @Redirect(
            method = "notifyBlockUpdate",
            at = @At(value = "INVOKE", target = "Ljava/util/List;get(I)Ljava/lang/Object;")
    )
    private Object ausm$safeNotifyBlockUpdateListenerGet(List<?> listeners, int index) {
        if (index < 0 || index >= listeners.size()) {
            return AUSM_NOOP_WORLD_EVENT_LISTENER;
        }
        return listeners.get(index);
    }

    @Inject(method = "notifyBlockUpdate", at = @At("HEAD"))
    private void ausm$invalidateShaderlessBloomMetadataOnBlockUpdate(BlockPos pos, IBlockState oldState,
                                                                     IBlockState newState, int flags,
                                                                     CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        World world = (World) (Object) this;
        context.handleShaderlessBloomBlockUpdate(world, pos, oldState, newState, flags);
    }

    /**
     * Chunk render caches already receive dynamic light while terrain is baked. Live
     * renderers (entities, block entities, and particles) query the client World
     * directly instead, so mirror the same adjustment on that rendering path.
     */
    @ModifyReturnValue(method = "getLightFor", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicWorldBlockLight(int original, EnumSkyBlock type, BlockPos pos) {
        if (type != EnumSkyBlock.BLOCK || !DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }

        int dynamicLight = DynamicLightManager.lightAt(pos);
        return Math.max(original, dynamicLight);
    }

    @ModifyReturnValue(method = "getCombinedLight", at = @At("RETURN"))
    private int ausm$applyShaderlessDynamicWorldCombinedLight(int original, BlockPos pos, int lightValue) {
        if (!DynamicLightManager.shouldApplyToBlockRenderLightQuery(pos)) {
            return original;
        }
        return DynamicLightManager.applyPackedLight(pos, original);
    }

    @Inject(method = "markBlockRangeForRenderUpdate(IIIIII)V", at = @At("HEAD"), require = 0)
    private void ausm$invalidateShaderlessBloomMetadataOnRenderUpdate(int minX, int minY, int minZ,
                                                                      int maxX, int maxY, int maxZ,
                                                                      CallbackInfo ci) {
        World world = (World) (Object) this;
        PipelineContext context = PipelineContext.getInstance();
        context.handleShaderlessBloomRenderUpdateRange(world, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
