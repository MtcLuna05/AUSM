package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldVertexBufferUploader.class)
public class WorldVertexBufferUploaderMixin {

    @ModifyArg(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V"),
            index = 0
    )
    private int ausm$tessellatedDrawMode(int drawMode) {
        return PipelineContext.getInstance().drawModeForActiveProgram(drawMode);
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void ausm$unbindArrayBufferForClientDraw(BufferBuilder bufferBuilder, CallbackInfo ci) {
        FixedFunctionGlState.resetClientArrayState(false);
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V", shift = At.Shift.BEFORE)
    )
    private void ausm$enablePipelineAttributes(BufferBuilder bufferBuilder, CallbackInfo ci) {
        VertexFormat format = MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder);
        if (ExtendedVertexFormats.isPipelineEntity(format)) {
            ausm$enablePipelineEntityAttributes(bufferBuilder, format);
            return;
        }
        if (!ExtendedVertexFormats.isPipelineBlock(format)) {
            return;
        }

        ByteBuffer byteBuffer = MinecraftReflectionCompat.bufferByteBuffer(bufferBuilder);
        if (byteBuffer == null) {
            return;
        }
        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, ExtendedVertexFormats.size(format), byteBuffer);

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );
    }

    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;glDrawArrays(III)V", shift = At.Shift.AFTER)
    )
    private void ausm$disablePipelineAttributes(BufferBuilder bufferBuilder, CallbackInfo ci) {
        if (ExtendedVertexFormats.isPipelineBlock(MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder)) || ExtendedVertexFormats.isPipelineEntity(MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder))) {
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
            ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        }
    }

    private void ausm$enablePipelineEntityAttributes(BufferBuilder bufferBuilder, VertexFormat format) {
        ByteBuffer byteBuffer = MinecraftReflectionCompat.bufferByteBuffer(bufferBuilder);
        if (byteBuffer == null) {
            return;
        }
        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_NORMAL_OFFSET);
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, ExtendedVertexFormats.size(format), byteBuffer);

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_MC_ENTITY_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_MID_TEX_COORD_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );

        byteBuffer.position(ExtendedVertexFormats.PIPELINE_ENTITY_TANGENT_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                ExtendedVertexFormats.size(format),
                byteBuffer
        );
    }
}
