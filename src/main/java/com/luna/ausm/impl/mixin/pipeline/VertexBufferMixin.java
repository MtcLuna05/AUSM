package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleanroom's transformed VboRenderList bypasses its named mixin injection
 * points. VertexBuffer.drawArrays is the final common terrain draw boundary.
 */
@Mixin(VertexBuffer.class)
public class VertexBufferMixin {

    @Inject(method = "func_177358_a", at = @At("HEAD"), remap = false)
    private void ausm$configurePipelineBlockPointers(int mode, CallbackInfo ci) {
        VertexFormat format = MinecraftReflectionCompat.field(
                this, VertexFormat.class, null, "field_177363_b", "vertexFormat");
        if (!ExtendedVertexFormats.isPipelineBlock(format)) {
            return;
        }

        int stride = ExtendedVertexFormats.size(format);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);

        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187420_d", "glVertexPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 3, GL11.GL_FLOAT, stride, 0);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187406_e", "glColorPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 4, GL11.GL_UNSIGNED_BYTE, stride, 12);
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 2, GL11.GL_FLOAT,
                false, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 4, GL11.GL_BYTE,
                true, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, 4, GL11.GL_SHORT,
                false, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ExtendedVertexFormats.vertexAttribPointer(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 4, GL11.GL_BYTE,
                false, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET);
    }
}
