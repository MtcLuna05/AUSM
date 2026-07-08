package com.l.ausm.impl.pipeline.render;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * Utility for drawing a fullscreen quad.
 * Used during the Deferred, Composite, and Final passes.
 */
public class FullscreenQuad {

    public static void draw() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glBindBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, "field_176089_P", "GL_ARRAY_BUFFER"), 0);

        PipelineContext context = PipelineContext.getInstance();
        Tessellator tessellator = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        if (context.shouldDrawFullscreenAsTriangles()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_TRIANGLES, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.client.renderer.vertex.DefaultVertexFormats.class, net.minecraft.client.renderer.vertex.VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, context.drawModeForActiveProgram(GL11.GL_QUADS), com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.client.renderer.vertex.DefaultVertexFormats.class, net.minecraft.client.renderer.vertex.VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }
}
