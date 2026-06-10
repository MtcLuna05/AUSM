package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(WorldVertexBufferUploader.class)
public class WorldVertexBufferUploaderMixin {

    @Inject(method = "draw", at = @At("HEAD"))
    private void ausm$unbindArrayBufferForClientDraw(BufferBuilder bufferBuilder, CallbackInfo ci) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V", shift = At.Shift.BEFORE)
    )
    private void ausm$enablePipelineAttributes(BufferBuilder bufferBuilder, CallbackInfo ci) {
        VertexFormat format = bufferBuilder.getVertexFormat();
        if (ExtendedVertexFormats.isPipelineEntity(format)) {
            ausm$enablePipelineEntityAttributes(bufferBuilder, format);
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(format)) {
            return;
        }

        ByteBuffer byteBuffer = bufferBuilder.getByteBuffer();
        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, format.getSize(), byteBuffer);

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                format.getSize(),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                format.getSize(),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                format.getSize(),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                format.getSize(),
                byteBuffer
        );
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V", shift = At.Shift.AFTER)
    )
    private void ausm$disablePipelineAttributes(BufferBuilder bufferBuilder, CallbackInfo ci) {
        if (ExtendedVertexFormats.isPipelineBlock(bufferBuilder.getVertexFormat()) || ExtendedVertexFormats.isPipelineEntity(bufferBuilder.getVertexFormat())) {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        }
    }

    private void ausm$enablePipelineEntityAttributes(BufferBuilder bufferBuilder, VertexFormat format) {
        ByteBuffer byteBuffer = bufferBuilder.getByteBuffer();
        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, format.getSize(), byteBuffer);

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                format.getSize(),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                format.getSize(),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                format.getSize(),
                byteBuffer
        );
    }
}
