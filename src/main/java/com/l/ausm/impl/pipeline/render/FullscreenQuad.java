package com.l.ausm.impl.pipeline.render;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
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

        GL11.glBegin(GL11.GL_QUADS);

        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, 0f);

        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(1f, 0f);

        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(1f, 1f);

        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(0f, 1f);

        GL11.glEnd();
    }
}
