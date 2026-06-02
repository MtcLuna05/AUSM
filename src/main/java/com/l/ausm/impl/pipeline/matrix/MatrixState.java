package com.l.ausm.impl.pipeline.matrix;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public final class MatrixState {
    private static final int MATRIX_SIZE = 16;
    private static final int MATRIX3_SIZE = 9;

    private static final float[] IDENTITY = {
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    };

    private static final float[] MODEL_VIEW = IDENTITY.clone();
    private static final float[] MODEL_VIEW_INVERSE = IDENTITY.clone();
    private static final float[] NORMAL_MATRIX = {
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
    };
    private static final float[] PREVIOUS_MODEL_VIEW = IDENTITY.clone();
    private static final float[] PROJECTION = IDENTITY.clone();
    private static final float[] PROJECTION_INVERSE = IDENTITY.clone();
    private static final float[] PREVIOUS_PROJECTION = IDENTITY.clone();
    private static final float[] SHADOW_MODEL_VIEW = IDENTITY.clone();
    private static final float[] SHADOW_MODEL_VIEW_INVERSE = IDENTITY.clone();
    private static final float[] SHADOW_PROJECTION = IDENTITY.clone();
    private static final float[] SHADOW_PROJECTION_INVERSE = IDENTITY.clone();
    private static final float[] CAMERA_POSITION = {0.0f, 0.0f, 0.0f};
    private static final float[] PREVIOUS_CAMERA_POSITION = {0.0f, 0.0f, 0.0f};

    private static final FloatBuffer GL_BUFFER = BufferUtils.createFloatBuffer(MATRIX_SIZE);

    private MatrixState() {
    }

    public static void captureGbufferMatrices() {
        copy(MODEL_VIEW, PREVIOUS_MODEL_VIEW);
        copy(PROJECTION, PREVIOUS_PROJECTION);
        copyVec3(CAMERA_POSITION, PREVIOUS_CAMERA_POSITION);

        readGlMatrix(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
        readGlMatrix(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        invert(MODEL_VIEW, MODEL_VIEW_INVERSE);
        invert(PROJECTION, PROJECTION_INVERSE);
        updateNormalMatrix();
        CAMERA_POSITION[0] = MODEL_VIEW_INVERSE[12];
        CAMERA_POSITION[1] = MODEL_VIEW_INVERSE[13];
        CAMERA_POSITION[2] = MODEL_VIEW_INVERSE[14];
    }

    public static void captureShadowMatrices() {
        readGlMatrix(GL11.GL_MODELVIEW_MATRIX, SHADOW_MODEL_VIEW);
        readGlMatrix(GL11.GL_PROJECTION_MATRIX, SHADOW_PROJECTION);
        invert(SHADOW_MODEL_VIEW, SHADOW_MODEL_VIEW_INVERSE);
        invert(SHADOW_PROJECTION, SHADOW_PROJECTION_INVERSE);
    }

    public static FloatBuffer modelView() {
        return buffer(MODEL_VIEW);
    }

    public static FloatBuffer identity() {
        return buffer(IDENTITY);
    }

    public static FloatBuffer modelViewInverse() {
        return buffer(MODEL_VIEW_INVERSE);
    }

    public static FloatBuffer normalMatrix() {
        return buffer(NORMAL_MATRIX, MATRIX3_SIZE);
    }

    public static FloatBuffer previousModelView() {
        return buffer(PREVIOUS_MODEL_VIEW);
    }

    public static FloatBuffer projection() {
        return buffer(PROJECTION);
    }

    public static FloatBuffer projectionInverse() {
        return buffer(PROJECTION_INVERSE);
    }

    public static FloatBuffer previousProjection() {
        return buffer(PREVIOUS_PROJECTION);
    }

    public static FloatBuffer shadowModelView() {
        return buffer(SHADOW_MODEL_VIEW);
    }

    public static float[] shadowModelViewValues() {
        return SHADOW_MODEL_VIEW.clone();
    }

    public static FloatBuffer shadowModelViewInverse() {
        return buffer(SHADOW_MODEL_VIEW_INVERSE);
    }

    public static float[] shadowModelViewInverseValues() {
        return SHADOW_MODEL_VIEW_INVERSE.clone();
    }

    public static FloatBuffer shadowProjection() {
        return buffer(SHADOW_PROJECTION);
    }

    public static float[] shadowProjectionValues() {
        return SHADOW_PROJECTION.clone();
    }

    public static FloatBuffer shadowProjectionInverse() {
        return buffer(SHADOW_PROJECTION_INVERSE);
    }

    public static float[] shadowProjectionInverseValues() {
        return SHADOW_PROJECTION_INVERSE.clone();
    }

    public static float[] cameraPosition() {
        return CAMERA_POSITION.clone();
    }

    public static float[] previousCameraPosition() {
        return PREVIOUS_CAMERA_POSITION.clone();
    }

    public static float[] transformModelViewDirection(float x, float y, float z) {
        return new float[]{
                MODEL_VIEW[0] * x + MODEL_VIEW[4] * y + MODEL_VIEW[8] * z,
                MODEL_VIEW[1] * x + MODEL_VIEW[5] * y + MODEL_VIEW[9] * z,
                MODEL_VIEW[2] * x + MODEL_VIEW[6] * y + MODEL_VIEW[10] * z
        };
    }

    private static void readGlMatrix(int glMatrixName, float[] target) {
        GL_BUFFER.clear();
        GL11.glGetFloat(glMatrixName, GL_BUFFER);
        GL_BUFFER.position(0);
        GL_BUFFER.get(target);
    }

    private static FloatBuffer buffer(float[] matrix) {
        return buffer(matrix, MATRIX_SIZE);
    }

    private static FloatBuffer buffer(float[] matrix, int size) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(size);
        buffer.put(matrix);
        buffer.flip();
        return buffer;
    }

    private static void updateNormalMatrix() {
        NORMAL_MATRIX[0] = MODEL_VIEW_INVERSE[0];
        NORMAL_MATRIX[1] = MODEL_VIEW_INVERSE[4];
        NORMAL_MATRIX[2] = MODEL_VIEW_INVERSE[8];
        NORMAL_MATRIX[3] = MODEL_VIEW_INVERSE[1];
        NORMAL_MATRIX[4] = MODEL_VIEW_INVERSE[5];
        NORMAL_MATRIX[5] = MODEL_VIEW_INVERSE[9];
        NORMAL_MATRIX[6] = MODEL_VIEW_INVERSE[2];
        NORMAL_MATRIX[7] = MODEL_VIEW_INVERSE[6];
        NORMAL_MATRIX[8] = MODEL_VIEW_INVERSE[10];
    }

    private static void copy(float[] source, float[] target) {
        System.arraycopy(source, 0, target, 0, MATRIX_SIZE);
    }

    private static void copyVec3(float[] source, float[] target) {
        System.arraycopy(source, 0, target, 0, 3);
    }

    private static void invert(float[] source, float[] target) {
        float[] inv = new float[MATRIX_SIZE];

        inv[0] = source[5] * source[10] * source[15] - source[5] * source[11] * source[14] - source[9] * source[6] * source[15]
                + source[9] * source[7] * source[14] + source[13] * source[6] * source[11] - source[13] * source[7] * source[10];
        inv[4] = -source[4] * source[10] * source[15] + source[4] * source[11] * source[14] + source[8] * source[6] * source[15]
                - source[8] * source[7] * source[14] - source[12] * source[6] * source[11] + source[12] * source[7] * source[10];
        inv[8] = source[4] * source[9] * source[15] - source[4] * source[11] * source[13] - source[8] * source[5] * source[15]
                + source[8] * source[7] * source[13] + source[12] * source[5] * source[11] - source[12] * source[7] * source[9];
        inv[12] = -source[4] * source[9] * source[14] + source[4] * source[10] * source[13] + source[8] * source[5] * source[14]
                - source[8] * source[6] * source[13] - source[12] * source[5] * source[10] + source[12] * source[6] * source[9];
        inv[1] = -source[1] * source[10] * source[15] + source[1] * source[11] * source[14] + source[9] * source[2] * source[15]
                - source[9] * source[3] * source[14] - source[13] * source[2] * source[11] + source[13] * source[3] * source[10];
        inv[5] = source[0] * source[10] * source[15] - source[0] * source[11] * source[14] - source[8] * source[2] * source[15]
                + source[8] * source[3] * source[14] + source[12] * source[2] * source[11] - source[12] * source[3] * source[10];
        inv[9] = -source[0] * source[9] * source[15] + source[0] * source[11] * source[13] + source[8] * source[1] * source[15]
                - source[8] * source[3] * source[13] - source[12] * source[1] * source[11] + source[12] * source[3] * source[9];
        inv[13] = source[0] * source[9] * source[14] - source[0] * source[10] * source[13] - source[8] * source[1] * source[14]
                + source[8] * source[2] * source[13] + source[12] * source[1] * source[10] - source[12] * source[2] * source[9];
        inv[2] = source[1] * source[6] * source[15] - source[1] * source[7] * source[14] - source[5] * source[2] * source[15]
                + source[5] * source[3] * source[14] + source[13] * source[2] * source[7] - source[13] * source[3] * source[6];
        inv[6] = -source[0] * source[6] * source[15] + source[0] * source[7] * source[14] + source[4] * source[2] * source[15]
                - source[4] * source[3] * source[14] - source[12] * source[2] * source[7] + source[12] * source[3] * source[6];
        inv[10] = source[0] * source[5] * source[15] - source[0] * source[7] * source[13] - source[4] * source[1] * source[15]
                + source[4] * source[3] * source[13] + source[12] * source[1] * source[7] - source[12] * source[3] * source[5];
        inv[14] = -source[0] * source[5] * source[14] + source[0] * source[6] * source[13] + source[4] * source[1] * source[14]
                - source[4] * source[2] * source[13] - source[12] * source[1] * source[6] + source[12] * source[2] * source[5];
        inv[3] = -source[1] * source[6] * source[11] + source[1] * source[7] * source[10] + source[5] * source[2] * source[11]
                - source[5] * source[3] * source[10] - source[9] * source[2] * source[7] + source[9] * source[3] * source[6];
        inv[7] = source[0] * source[6] * source[11] - source[0] * source[7] * source[10] - source[4] * source[2] * source[11]
                + source[4] * source[3] * source[10] + source[8] * source[2] * source[7] - source[8] * source[3] * source[6];
        inv[11] = -source[0] * source[5] * source[11] + source[0] * source[7] * source[9] + source[4] * source[1] * source[11]
                - source[4] * source[3] * source[9] - source[8] * source[1] * source[7] + source[8] * source[3] * source[5];
        inv[15] = source[0] * source[5] * source[10] - source[0] * source[6] * source[9] - source[4] * source[1] * source[10]
                + source[4] * source[2] * source[9] + source[8] * source[1] * source[6] - source[8] * source[2] * source[5];

        float determinant = source[0] * inv[0] + source[1] * inv[4] + source[2] * inv[8] + source[3] * inv[12];
        if (Math.abs(determinant) < 1.0E-12f) {
            copy(IDENTITY, target);
            return;
        }

        float invDeterminant = 1.0f / determinant;
        for (int i = 0; i < MATRIX_SIZE; i++) {
            target[i] = inv[i] * invDeterminant;
        }
    }
}
