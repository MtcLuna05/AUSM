package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

/**
 * Stateless GL queries and global pipeline-state restoration helpers.
 */
final class PipelineGlState {
    private static int maxDrawBuffers = -1;
    private static int maxShaderStorageBufferBindings = -1;
    private static boolean shaderStorageBuffersKnownUnbound = true;

    private PipelineGlState() {
    }

    static float fovYInverse() {
        FloatBuffer projection = MatrixState.projection();
        float projectionY = projection.get(5);
        return Math.abs(projectionY) < 1.0E-6f
                ? 1.0f
                : 1.0f / (float) Math.atan(1.0f / projectionY) * 0.5f;
    }

    static int boundTexture2d() {
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    static int[] boundTextureSize() {
        if (boundTexture2d() == 0) {
            return new int[]{0, 0};
        }
        return new int[]{
                Math.max(0, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)),
                Math.max(0, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT))
        };
    }

    static int[] blendFunc() {
        if (!GL11.glIsEnabled(GL11.GL_BLEND)) {
            return new int[]{0, 0, 0, 0};
        }
        return new int[]{
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        };
    }

    static int safeGetInteger(int parameter) {
        try {
            return GL11.glGetInteger(parameter);
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    static String safeGetString(int parameter) {
        try {
            String value = GL11.glGetString(parameter);
            return value != null ? value : "unknown";
        } catch (RuntimeException | LinkageError ignored) {
            return "unavailable";
        }
    }

    static void setIndexedBlend(int drawBufferIndex, boolean enabled) {
        if (!GLContext.getCapabilities().OpenGL30 || drawBufferIndex < 0 || drawBufferIndex >= maxDrawBuffers()) {
            return;
        }
        if (enabled) {
            GL30.glEnablei(GL11.GL_BLEND, drawBufferIndex);
        } else {
            GL30.glDisablei(GL11.GL_BLEND, drawBufferIndex);
        }
    }

    static void resetIndexedBlendState() {
        for (int i = 0; i < maxDrawBuffers(); i++) {
            setIndexedBlend(i, false);
        }
    }

    static void resetOitRenderState() {
        MinecraftReflectionCompat.glStateDepthMask(true);
        resetIndexedBlendState();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO
        );
    }

    static int maxDrawBuffers() {
        if (maxDrawBuffers < 0) {
            maxDrawBuffers = GLContext.getCapabilities().OpenGL20
                    ? Math.max(1, GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS))
                    : 1;
        }
        return maxDrawBuffers;
    }

    static void unbindShaderImages() {
        if (!GLContext.getCapabilities().OpenGL42) {
            return;
        }
        int maxImageUnits = Math.max(0, GL11.glGetInteger(GL42.GL_MAX_IMAGE_UNITS));
        for (int unit = 0; unit < maxImageUnits; unit++) {
            GL42.glBindImageTexture(unit, 0, 0, false, 0, GL15.GL_READ_ONLY, GL11.GL_RGBA8);
        }
    }

    static void markShaderStorageBuffersBound() {
        if (GLContext.getCapabilities().OpenGL43) {
            shaderStorageBuffersKnownUnbound = false;
        }
    }

    static boolean shaderStorageBuffersKnownUnbound() {
        return shaderStorageBuffersKnownUnbound;
    }

    static void markShaderStorageBuffersUnbound() {
        shaderStorageBuffersKnownUnbound = true;
    }

    static int maxShaderStorageBufferBindings() {
        if (maxShaderStorageBufferBindings < 0) {
            maxShaderStorageBufferBindings = Math.max(0, GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS));
        }
        return maxShaderStorageBufferBindings;
    }

    static void disablePipelineVertexAttributes() {
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
    }

    static void restoreVanillaClientRenderState() {
        GL11.glFrontFace(GL11.GL_CCW);
        MinecraftReflectionCompat.glStateCullFaceBack();
        GL11.glDepthRange(0.0D, 1.0D);
        GL11.glClearDepth(1.0D);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        MinecraftReflectionCompat.setActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        MinecraftReflectionCompat.glStateDisableTexture2D();
        MinecraftReflectionCompat.setActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateEnableTexture2D();
    }
}
