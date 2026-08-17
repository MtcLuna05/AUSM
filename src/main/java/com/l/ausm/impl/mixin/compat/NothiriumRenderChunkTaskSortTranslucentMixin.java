package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import meldexun.memoryutil.UnsafeByteBuffer;
import meldexun.nothirium.api.renderer.IVBOPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.RenderChunkTaskSortTranslucent", remap = false)
public class NothiriumRenderChunkTaskSortTranslucentMixin {
    @Shadow(remap = false)
    @Final
    private IVBOPart vboPart;

    @Shadow(remap = false)
    @Final
    private UnsafeByteBuffer vertexData;

    @ModifyArg(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lmeldexun/nothirium/util/VertexSortUtil;sortVertexData(Lmeldexun/memoryutil/MemoryAccess;IIIFFF)V",
                    remap = false
            ),
            index = 2,
            remap = false
    )
    private int ausm$usePipelineSortStride(int original) {
        return ausm$effectiveVertexStride(original);
    }

    @ModifyArg(
            method = "lambda$run$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL15;glBufferSubData(IJLjava/nio/ByteBuffer;)V",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private long ausm$usePipelineUploadOffset(long original) {
        if (vboPart == null || !vboPart.isValid()) {
            return original;
        }
        int stride = ausm$effectiveVertexStride(ExtendedVertexFormats.size(MinecraftReflectionCompat.blockFormat()));
        if (stride <= 0 || !ausm$vertexDataFitsStride(stride)) {
            return original;
        }
        return vboPart.getOffset();
    }

    @Unique
    private int ausm$effectiveVertexStride(int fallback) {
        int pipelineStride = NothiriumPipelineCompat.pipelineBlockStride(fallback);
        if (vboPart == null || !vboPart.isValid()) {
            return fallback;
        }

        int count = vboPart.getCount();
        int size = vboPart.getSize();
        if (count <= 0 || size <= 0 || size % count != 0) {
            return fallback;
        }

        int stride = size / count;
        if (stride == fallback || stride == pipelineStride) {
            return ausm$vertexDataFitsStride(stride) ? stride : fallback;
        }
        return fallback;
    }

    @Unique
    private boolean ausm$vertexDataFitsStride(int stride) {
        if (vboPart == null || vertexData == null || stride <= 0) {
            return false;
        }
        int count = vboPart.getCount();
        if (count < 0) {
            return false;
        }
        long requiredBytes = (long) count * (long) stride;
        return requiredBytes >= 0L && vertexData.getByteCapacity() >= requiredBytes;
    }
}
