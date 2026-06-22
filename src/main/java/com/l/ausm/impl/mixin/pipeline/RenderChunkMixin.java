package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IPipelineRenderChunk;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.World;

import java.util.Arrays;

@Mixin(RenderChunk.class)
public class RenderChunkMixin implements IPipelineRenderChunk {
    @Shadow
    private World world;

    @Unique
    private boolean ausm$pipelineVertexFormat;

    @Unique
    private boolean ausm$pendingPipelineVertexFormat;

    @Unique
    private boolean[] ausm$pipelineVertexFormatByLayer;

    @Unique
    private boolean[] ausm$shaderlessBloomMetadataByLayer;

    @Unique
    private static boolean ausm$loggedNullWorldRepair;

    @Inject(method = "rebuildWorldView", at = @At("HEAD"))
    private void ausm$repairNullWorldBeforeRebuild(CallbackInfo ci) {
        if (world != null) {
            return;
        }

        World fallback = PipelineContext.getInstance().betterPortalsRenderChunkFallbackWorld();
        if (fallback == null) {
            return;
        }

        world = fallback;
        if (!ausm$loggedNullWorldRepair) {
            ausm$loggedNullWorldRepair = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Repaired null RenderChunk world before chunk rebuild: world={}", ausm$dimensionId(fallback));
        }
    }

    @ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;<init>(Lnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 0
    )
    private VertexFormat ausm$usePipelineVertexBufferFormat(VertexFormat original) {
        boolean pipelineFormat = PipelineContext.getInstance().shouldUsePipelineBlockFormat()
                && ExtendedVertexFormats.PIPELINE_BLOCK != null;
        ausm$pipelineVertexFormat = pipelineFormat;
        ausm$pendingPipelineVertexFormat = pipelineFormat;
        Arrays.fill(ausm$pipelineVertexFormatByLayer(), pipelineFormat);
        return pipelineFormat ? ExtendedVertexFormats.PIPELINE_BLOCK : original;
    }

    @ModifyArg(
            method = "preRenderBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V"),
            index = 1
    )
    private VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        boolean pipelineFormat = PipelineContext.getInstance().shouldUsePipelineBlockFormat()
                && ExtendedVertexFormats.PIPELINE_BLOCK != null;
        ausm$pipelineVertexFormat = pipelineFormat;
        ausm$pendingPipelineVertexFormat = pipelineFormat;
        return pipelineFormat ? ExtendedVertexFormats.PIPELINE_BLOCK : original;
    }

    @Inject(method = "postRenderBlocks", at = @At("HEAD"))
    private void ausm$recordLayerVertexFormat(BlockRenderLayer layer, float x, float y, float z,
                                              BufferBuilder bufferBuilder, CompiledChunk compiledChunk, CallbackInfo ci) {
        int index = ausm$layerIndex(layer);
        if (index >= 0) {
            ausm$pipelineVertexFormatByLayer()[index] = ausm$pendingPipelineVertexFormat;
            boolean hasBloomMetadata = bufferBuilder instanceof com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension extension
                    && extension.ausm$hasShaderlessBloomMetadata();
            ausm$shaderlessBloomMetadataByLayer()[index] = hasBloomMetadata;
            PipelineContext.getInstance().recordShaderlessBloomMetadata(
                    ((RenderChunk) (Object) this).getPosition(),
                    layer,
                    hasBloomMetadata
            );
            if (bufferBuilder instanceof com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension extension) {
                extension.ausm$resetShaderlessBloomMetadata();
            }
        }
    }

    @Override
    public boolean ausm$usesPipelineVertexFormat(BlockRenderLayer layer) {
        int index = ausm$layerIndex(layer);
        return index >= 0 ? ausm$pipelineVertexFormatByLayer()[index] : ausm$pipelineVertexFormat;
    }

    @Override
    public boolean ausm$usesPipelineVertexFormat() {
        return ausm$pipelineVertexFormat;
    }

    @Override
    public boolean ausm$hasShaderlessBloomMetadata(BlockRenderLayer layer) {
        int index = ausm$layerIndex(layer);
        return index < 0 || ausm$shaderlessBloomMetadataByLayer()[index];
    }

    @Unique
    private int ausm$layerIndex(BlockRenderLayer layer) {
        if (layer == null) {
            return -1;
        }
        int ordinal = layer.ordinal();
        return ordinal >= 0 && ordinal < BlockRenderLayer.values().length ? ordinal : -1;
    }

    @Unique
    private boolean[] ausm$pipelineVertexFormatByLayer() {
        if (ausm$pipelineVertexFormatByLayer == null) {
            ausm$pipelineVertexFormatByLayer = new boolean[BlockRenderLayer.values().length];
            Arrays.fill(ausm$pipelineVertexFormatByLayer, ausm$pipelineVertexFormat);
        }
        return ausm$pipelineVertexFormatByLayer;
    }

    @Unique
    private boolean[] ausm$shaderlessBloomMetadataByLayer() {
        if (ausm$shaderlessBloomMetadataByLayer == null) {
            ausm$shaderlessBloomMetadataByLayer = new boolean[BlockRenderLayer.values().length];
        }
        return ausm$shaderlessBloomMetadataByLayer;
    }

    @Unique
    private static int ausm$dimensionId(World world) {
        return world != null && world.provider != null ? world.provider.getDimension() : Integer.MIN_VALUE;
    }
}
