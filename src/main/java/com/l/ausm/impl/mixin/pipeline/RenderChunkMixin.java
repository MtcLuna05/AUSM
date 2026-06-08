package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IPipelineRenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderChunk.class)
public class RenderChunkMixin implements IPipelineRenderChunk {
    @Unique
    private boolean ausm$pipelineVertexFormat;

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;<init>(Lnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 0
    )
    private VertexFormat ausm$usePipelineVertexBufferFormat(VertexFormat original) {
        boolean pipelineFormat = PipelineContext.getInstance().isActive();
        ausm$pipelineVertexFormat = pipelineFormat;
        return pipelineFormat ? ExtendedVertexFormats.PIPELINE_BLOCK : original;
    }

    @ModifyArg(
            method = "preRenderBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 1
    )
    private VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        boolean pipelineFormat = PipelineContext.getInstance().isActive();
        ausm$pipelineVertexFormat = pipelineFormat;
        return pipelineFormat ? ExtendedVertexFormats.PIPELINE_BLOCK : original;
    }

    @Override
    public boolean ausm$usesPipelineVertexFormat() {
        return ausm$pipelineVertexFormat;
    }
}
