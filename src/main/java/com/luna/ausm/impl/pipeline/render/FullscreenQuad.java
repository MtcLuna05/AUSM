package com.luna.ausm.impl.pipeline.render;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * Utility for drawing a fullscreen quad.
 * Used during the Deferred, Composite, and Final passes.
 */
public class FullscreenQuad {

    public static void draw() {
        MinecraftReflectionCompat.glBindBuffer(MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL15.GL_ARRAY_BUFFER, "field_176089_P", "GL_ARRAY_BUFFER"), 0);

        PipelineContext context = PipelineContext.getInstance();
        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        if (context.shouldDrawFullscreenAsTriangles()) {
            MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_TRIANGLES, MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
            MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
            MinecraftReflectionCompat.tessellatorDraw(tessellator);
            return;
        }

        MinecraftReflectionCompat.bufferBegin(buffer, context.drawModeForActiveProgram(GL11.GL_QUADS), MinecraftReflectionCompat.field(DefaultVertexFormats.class, VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 0.0D, 0.0D, 1.0D, 0.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, 1.0D, 1.0D, 0.0D, 1.0D, 1.0D);
        MinecraftReflectionCompat.bufferPosTexEnd(buffer, 0.0D, 1.0D, 0.0D, 0.0D, 1.0D);
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }
}
