package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LittleTiles maps a previously uploaded VBO back into its own cache before a
 * chunk rebuild.  Pipeline VBO records are wider than that cache, so retaining
 * the original cache is the only lossless path.
 */
@Mixin(targets = "com.creativemd.littletiles.client.render.cache.ChunkBlockLayerManager", remap = false)
public class LittleTilesChunkBlockLayerManagerMixin {
    @Shadow(remap = false)
    private VertexBuffer buffer;

    @Inject(method = "backToRAM", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$neverDownloadExpandedPipelineVertices(CallbackInfo ci) {
        VertexFormat format = MinecraftReflectionCompat.vertexBufferFormat(buffer);
        if (ExtendedVertexFormats.isPipelineBlock(format)) {
            ci.cancel();
        }
    }
}
