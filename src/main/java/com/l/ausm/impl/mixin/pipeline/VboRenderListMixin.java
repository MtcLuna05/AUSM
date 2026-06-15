package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IPipelineRenderChunk;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VboRenderList.class)
public class VboRenderListMixin {
    @Unique
    private boolean ausm$currentChunkUsesPipelineVertexFormat;

    @Redirect(
            method = "renderChunkLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;getVertexBufferByLayer(I)Lnet/minecraft/client/renderer/vertex/VertexBuffer;"
            )
    )
    private VertexBuffer ausm$captureChunkVertexFormat(RenderChunk renderChunk, int layer) {
        BlockRenderLayer renderLayer = ausm$layerByOrdinal(layer);
        ausm$currentChunkUsesPipelineVertexFormat = renderChunk instanceof IPipelineRenderChunk pipelineChunk
                && pipelineChunk.ausm$usesPipelineVertexFormat(renderLayer);
        PipelineContext.getInstance().applyChunkFade(renderChunk, renderLayer);
        return renderChunk.getVertexBufferByLayer(layer);
    }

    @Redirect(
            method = "renderChunkLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/VboRenderList;setupArrayPointers()V")
    )
    private void ausm$setupArrayPointersForCurrentChunk(VboRenderList instance) {
        if (ausm$currentChunkUsesPipelineVertexFormat) {
            ausm$setupPipelineArrayPointers();
        } else {
            ausm$setupVanillaArrayPointers();
        }
    }

    @Unique
    private static void ausm$setupPipelineArrayPointers() {
        int stride = ExtendedVertexFormats.PIPELINE_BLOCK.getSize();

        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, 12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET
        );

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET
        );
    }

    @Unique
    private static void ausm$setupVanillaArrayPointers() {
        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ausm$setGenericAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 1.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1.0F, 0.0F, 0.0F, 1.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    @Unique
    private static void ausm$setGenericAttribute(int index, float x, float y, float z, float w) {
        if (index >= 0 && index < GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)) {
            GL20.glVertexAttrib4f(index, x, y, z, w);
        }
    }

    @Unique
    private static BlockRenderLayer ausm$layerByOrdinal(int ordinal) {
        BlockRenderLayer[] layers = BlockRenderLayer.values();
        return ordinal >= 0 && ordinal < layers.length ? layers[ordinal] : null;
    }

    @Inject(method = "renderChunkLayer", at = @At("RETURN"))
    private void ausm$disablePipelineAttributes(BlockRenderLayer layer, CallbackInfo ci) {
        ausm$currentChunkUsesPipelineVertexFormat = false;
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        PipelineContext.getInstance().resetChunkFadeUniform();
    }
}
