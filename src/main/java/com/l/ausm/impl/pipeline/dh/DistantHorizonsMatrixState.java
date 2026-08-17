package com.l.ausm.impl.pipeline.dh;

import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

/**
 * Owns Distant Horizons camera matrices and model offset independently of pipeline lifecycle.
 */
public final class DistantHorizonsMatrixState {
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projectionInverse = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer modelViewProjection = BufferUtils.createFloatBuffer(16);
    private final float[] scratch = new float[16];
    private final float[] modelOffset = {0.0F, 0.0F, 0.0F};

    public FloatBuffer projection() {
        return projection;
    }

    public FloatBuffer projectionInverse() {
        return projectionInverse;
    }

    public FloatBuffer modelView() {
        return modelView;
    }

    public FloatBuffer modelViewProjection() {
        return modelViewProjection;
    }

    public float[] modelOffset() {
        return modelOffset;
    }

    public void update(Object renderParam) throws ReflectiveOperationException {
        copy(renderParam, "dhProjectionMatrix", projection);
        copyInverse(renderParam, "dhProjectionMatrix", projectionInverse);
        copy(renderParam, "dhModelViewMatrix", modelView);
        copy(renderParam, "dhMvmProjMatrix", modelViewProjection);
    }

    public void updateModelOffset(Object vector) throws ReflectiveOperationException {
        modelOffset[0] = ((Number) vector.getClass().getField("x").get(vector)).floatValue();
        modelOffset[1] = ((Number) vector.getClass().getField("y").get(vector)).floatValue();
        modelOffset[2] = ((Number) vector.getClass().getField("z").get(vector)).floatValue();
    }

    private void copy(Object renderParam, String fieldName, FloatBuffer target) throws ReflectiveOperationException {
        Object matrix = renderParam.getClass().getField(fieldName).get(renderParam);
        if (matrix != null) {
            copyValues(matrix, target);
        }
    }

    private void copyInverse(Object renderParam, String fieldName, FloatBuffer target) throws ReflectiveOperationException {
        Object matrix = renderParam.getClass().getField(fieldName).get(renderParam);
        if (matrix == null) return;
        Object copy = matrix.getClass().getMethod("copy").invoke(matrix);
        if (Boolean.FALSE.equals(copy.getClass().getMethod("canInvert").invoke(copy))) return;
        copy.getClass().getMethod("invert").invoke(copy);
        copyValues(copy, target);
    }

    private void copyValues(Object matrix, FloatBuffer target) throws ReflectiveOperationException {
        Class<?> type = matrix.getClass();
        String[] names = {"m00", "m10", "m20", "m30", "m01", "m11", "m21", "m31",
                "m02", "m12", "m22", "m32", "m03", "m13", "m23", "m33"};
        for (int index = 0; index < names.length; index++) {
            scratch[index] = ((Number) type.getField(names[index]).get(matrix)).floatValue();
        }
        target.clear();
        target.put(scratch);
        target.flip();
    }
}
