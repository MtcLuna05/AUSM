package com.l.ausm.impl.pipeline.render;

import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

public final class FixedFunctionGlState {
    public static final float TRANSLUCENT_ALPHA_REF = 0.003921569F;
    private static final ThreadLocal<FloatBuffer> TEXTURE_MATRIX_PROBE =
            ThreadLocal.withInitial(() -> BufferUtils.createFloatBuffer(16));

    private FixedFunctionGlState() {
    }

    public static void prepareTranslucentBlockLayer(Minecraft mc) {
        if (mc != null && MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.enableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        }
        MinecraftReflectionCompat.setActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateEnableTexture2D();
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager != null) {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
        }
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        prepareTranslucentDepthBlendState();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void prepareTranslucentDepthBlendState() {
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, TRANSLUCENT_ALPHA_REF);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        MinecraftReflectionCompat.glStateDepthMask(false);
    }

    public static void forceTranslucentBlockLayer() {
        GL13.glActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL13.glClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
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
            MinecraftReflectionCompat.glUseProgram(0);
        }
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    /**
     * World particles use fixed-function texture coordinates. A GUI glint or
     * compatibility renderer that leaves a projected texture matrix behind can
     * therefore turn each particle quad into a screen/terrain-sized plane even
     * when every other GL boundary state is canonical. Only reset unit zero:
     * EntityRenderer.enableLightmap() deliberately installs a scaled/translated
     * matrix on the lightmap unit for Minecraft's packed 0..240 coordinates.
     */
    public static void resetVanillaTextureMatrices() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try {
            resetTextureMatrix(MinecraftReflectionCompat.defaultTexUnit());
        } finally {
            MinecraftReflectionCompat.setActiveTexture(previousActiveTexture);
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private static void resetTextureMatrix(int textureUnit) {
        MinecraftReflectionCompat.setActiveTexture(textureUnit);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
    }

    public static String textureMatrixSummary() {
        FloatBuffer matrix = TEXTURE_MATRIX_PROBE.get();
        matrix.clear();
        GL11.glGetFloat(GL11.GL_TEXTURE_MATRIX, matrix);
        return "textureMatrix="
                + matrix.get(0) + '/' + matrix.get(5) + '/' + matrix.get(10) + '/' + matrix.get(15)
                + ",translate=" + matrix.get(12) + '/' + matrix.get(13) + '/' + matrix.get(14);
    }

    public static String clientArraySummary() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        int defaultUnit = MinecraftReflectionCompat.defaultTexUnit();
        int lightmapUnit = MinecraftReflectionCompat.lightmapTexUnit();
        boolean defaultTexCoords;
        boolean lightmapTexCoords;
        try {
            MinecraftReflectionCompat.setClientActiveTexture(defaultUnit);
            defaultTexCoords = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
            MinecraftReflectionCompat.setClientActiveTexture(lightmapUnit);
            lightmapTexCoords = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        } finally {
            MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
        }
        int vao = GLContext.getCapabilities().OpenGL30
                ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                : 0;
        return "vao=" + vao
                + ",array=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                + ",element=" + GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
                + ",vertex=" + GL11.glIsEnabled(GL11.GL_VERTEX_ARRAY)
                + ",color=" + GL11.glIsEnabled(GL11.GL_COLOR_ARRAY)
                + ",normal=" + GL11.glIsEnabled(GL11.GL_NORMAL_ARRAY)
                + ",uv0=" + defaultTexCoords
                + ",uv1=" + lightmapTexCoords
                + ",clientTex=" + GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
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
