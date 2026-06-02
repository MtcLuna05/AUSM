package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(RenderChunk.class)
public class RenderChunkMixin {

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;<init>(Lnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 0
    )
    private VertexFormat ausm$usePipelineVertexBufferFormat(VertexFormat original) {
        return ExtendedVertexFormats.PIPELINE_BLOCK;
    }

    @ModifyArg(
            method = "preRenderBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 1
    )
    private VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        return ExtendedVertexFormats.PIPELINE_BLOCK;
    }
}
