package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VboRenderList.class)
public class VboRenderListMixin {

    @Inject(method = "setupArrayPointers", at = @At("HEAD"), cancellable = true)
    private void ausm$setupPipelineArrayPointers(CallbackInfo ci) {
        int stride = ExtendedVertexFormats.PIPELINE_BLOCK.getSize();

        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, stride, 0);
        GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, stride, 12);
        GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        GL20.glEnableVertexAttribArray(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        GL20.glVertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET
        );

        GL20.glEnableVertexAttribArray(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        GL20.glVertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET
        );

        GL20.glEnableVertexAttribArray(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        GL20.glVertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET
        );

        GL20.glEnableVertexAttribArray(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        GL20.glVertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET
        );

        ci.cancel();
    }

    @Inject(method = "renderChunkLayer", at = @At("RETURN"))
    private void ausm$disablePipelineAttributes(BlockRenderLayer layer, CallbackInfo ci) {
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL20.glDisableVertexAttribArray(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        GL20.glDisableVertexAttribArray(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        GL20.glDisableVertexAttribArray(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        GL20.glDisableVertexAttribArray(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }
}
