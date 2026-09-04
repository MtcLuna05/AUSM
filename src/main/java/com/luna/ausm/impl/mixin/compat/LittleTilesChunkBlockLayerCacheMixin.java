package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A LittleTiles cache owns vanilla (28-byte) vertices.  Once AUSM expands a
 * cache into a terrain VBO, that VBO is no longer a valid replacement for the
 * cache: its 56-byte records must never overwrite the original data.
 */
@Mixin(targets = "com.creativemd.littletiles.client.render.cache.ChunkBlockLayerCache", remap = false)
public class LittleTilesChunkBlockLayerCacheMixin {
    @Inject(method = "uploaded", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$retainVanillaTileCacheForPipelineRebuilds(CallbackInfo ci) {
        if (PipelineContext.getInstance().shouldUsePipelineBlockFormat()) {
            ci.cancel();
        }
    }
}
