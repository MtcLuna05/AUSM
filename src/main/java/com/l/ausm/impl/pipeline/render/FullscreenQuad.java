package com.l.ausm.impl.pipeline.render;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Utility for drawing a fullscreen quad.
 * Used during the Deferred, Composite, and Final passes.
 */
public class FullscreenQuad {

    public static void draw() {
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0);

        PipelineContext context = PipelineContext.getInstance();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        if (context.shouldDrawFullscreenAsTriangles()) {
            buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
            buffer.pos(1.0D, 0.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
            buffer.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
            buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
            buffer.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
            buffer.pos(0.0D, 1.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
            tessellator.draw();
            return;
        }

        buffer.begin(context.drawModeForActiveProgram(GL11.GL_QUADS), DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        buffer.pos(1.0D, 0.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(0.0D, 1.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
    }
}
