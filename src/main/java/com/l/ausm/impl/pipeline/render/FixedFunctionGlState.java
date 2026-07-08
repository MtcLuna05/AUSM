package com.l.ausm.impl.pipeline.render;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

public final class FixedFunctionGlState {
    public static final float TRANSLUCENT_ALPHA_REF = 0.003921569F;

    private FixedFunctionGlState() {
    }

    public static void prepareTranslucentBlockLayer(Minecraft mc) {
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.enableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.setActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        TextureManager textureManager = com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc);
        if (textureManager != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        prepareTranslucentDepthBlendState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void prepareTranslucentDepthBlendState() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, TRANSLUCENT_ALPHA_REF);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
    }

    public static void forceTranslucentBlockLayer() {
        GL13.glActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL13.glClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, TRANSLUCENT_ALPHA_REF);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        GL11.glDepthMask(false);
    }

    public static void resetClientArrayState(boolean resetProgram) {
        if (resetProgram) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        }
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    public static String summary() {
        return "program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                + ",activeTex=" + GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                + ",clientTex=" + GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE)
                + ",tex=" + GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                + ",blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                + ",blendFunc=" + GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
                + "/" + GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
                + ",alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                + ",alphaFunc=" + GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC)
                + ",alphaRef=" + GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF)
                + ",depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                + ",depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                + ",depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    }
}
