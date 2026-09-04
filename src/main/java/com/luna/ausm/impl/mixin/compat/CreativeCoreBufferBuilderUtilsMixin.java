package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LittleTiles stores vanilla-sized terrain vertices in CreativeCore caches and
 * appends them directly to the active chunk builder.  AUSM's block builder is
 * wider, so raw copying would desynchronise every subsequent vertex.
 */
@Mixin(targets = "com.creativemd.creativecore.client.rendering.model.BufferBuilderUtils", remap = false)
public class CreativeCoreBufferBuilderUtilsMixin {
    @Inject(
            method = "addBuffer(Lnet/minecraft/client/renderer/BufferBuilder;Ljava/nio/ByteBuffer;II)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void ausm$expandCachedTerrainVertices(BufferBuilder destination, ByteBuffer source,
                                                          int length, int vertexCount, CallbackInfo ci) {
        if (destination == null || source == null || length <= 0 || vertexCount <= 0
                || !ExtendedVertexFormats.isPipelineBlock(destination.getVertexFormat())
                || length % vertexCount != 0) {
            return;
        }

        int sourceStride = length / vertexCount;
        // Only hand vanilla-shaped cache data to BufferBuilder's existing
        // pipeline expansion path.  Any foreign extended cache remains owned
        // by CreativeCore exactly as before.
        if (sourceStride != 28 && sourceStride != 32) {
            return;
        }

        ByteBuffer payload = source.duplicate();
        payload.position(0);
        payload.limit(length);
        destination.putBulkData(payload);
        ci.cancel();
    }
}
