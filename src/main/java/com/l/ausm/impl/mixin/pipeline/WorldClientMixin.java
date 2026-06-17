package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldClient.class)
public class WorldClientMixin {
    @Inject(method = "doPreChunk", at = @At("RETURN"))
    private void ausm$queueClientChunkRenderRefresh(int chunkX, int chunkZ, boolean loadChunk, CallbackInfo ci) {
        if (loadChunk) {
            PipelineContext.getInstance().queueClientChunkRenderRefresh(
                    (WorldClient) (Object) this,
                    chunkX,
                    chunkZ,
                    "pre-chunk"
            );
        }
    }
}
