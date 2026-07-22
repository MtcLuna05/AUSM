package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class RenderingRegressionProbes {
    private static final int MAX_TERRAIN_LOGS = 160;
    private static final AtomicInteger TERRAIN_LOGS = new AtomicInteger();
    private static final AtomicInteger INACTIVE_TERRAIN_LOGS = new AtomicInteger();
    private static final ThreadLocal<ProbeBuffers> BUFFERS = ThreadLocal.withInitial(ProbeBuffers::new);

    private RenderingRegressionProbes() {
    }

    public static void celeritas(String stage, Object layer, double x, double y, double z, Object matricesOrPass) {
        boolean pipelineActive = PipelineContext.getInstance().isPipelineActive();
        if (!pipelineActive && INACTIVE_TERRAIN_LOGS.incrementAndGet() > 16) {
            return;
        }
        int call = TERRAIN_LOGS.incrementAndGet();
        if (pipelineActive && call > MAX_TERRAIN_LOGS + 16) {
            return;
        }
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        MainMod.LOGGER.info(
                "[AUSMCeleritasDeepProbe] call={} stage={} pipeline={} layer={} xyz={}/{}/{} object={} objectMatrices={} currentModel={} currentProjection={} uniforms={} attribs={} program={} fbo={} vao={} array={} element={} gl={}",
                call,
                stage,
                pipelineActive,
                String.valueOf(layer),
                x, y, z,
                matricesOrPass != null ? matricesOrPass.getClass().getName() : "null",
                celeritasMatricesSummary(matricesOrPass),
                matrixSummary(GL11.GL_MODELVIEW_MATRIX),
                matrixSummary(GL11.GL_PROJECTION_MATRIX),
                uniformSummary(program),
                vertexAttribSummary(),
                program,
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING),
                FixedFunctionGlState.summary());
    }

    private static String celeritasMatricesSummary(Object matrices) {
        if (matrices == null || !matrices.getClass().getName().endsWith("ChunkRenderMatrices")) {
            return "n/a";
        }
        try {
            Method projection = matrices.getClass().getMethod("projection");
            Method modelView = matrices.getClass().getMethod("modelView");
            return "model=" + foreignMatrixSummary(modelView.invoke(matrices))
                    + ",projection=" + foreignMatrixSummary(projection.invoke(matrices));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    private static String foreignMatrixSummary(Object matrix) throws ReflectiveOperationException {
        if (matrix == null) {
            return "null";
        }
        return "diag=" + foreignFloat(matrix, "m00") + '/' + foreignFloat(matrix, "m11") + '/'
                + foreignFloat(matrix, "m22") + '/' + foreignFloat(matrix, "m33")
                + ",translation=" + foreignFloat(matrix, "m30") + '/' + foreignFloat(matrix, "m31") + '/'
                + foreignFloat(matrix, "m32");
    }

    private static String foreignFloat(Object matrix, String name) throws ReflectiveOperationException {
        Object value = matrix.getClass().getMethod(name).invoke(matrix);
        return value instanceof Number ? format(((Number) value).floatValue()) : "?";
    }

    private static String matrixSummary(int matrixName) {
        FloatBuffer matrix = BUFFERS.get().matrix;
        matrix.clear();
        GL11.glGetFloat(matrixName, matrix);
        float m00 = matrix.get(0);
        float m01 = matrix.get(1);
        float m02 = matrix.get(2);
        float m10 = matrix.get(4);
        float m11 = matrix.get(5);
        float m12 = matrix.get(6);
        float m20 = matrix.get(8);
        float m21 = matrix.get(9);
        float m22 = matrix.get(10);
        float determinant = m00 * (m11 * m22 - m12 * m21)
                - m10 * (m01 * m22 - m02 * m21)
                + m20 * (m01 * m12 - m02 * m11);
        return "diag=" + format(m00) + '/' + format(m11) + '/' + format(m22) + '/' + format(matrix.get(15))
                + ",det=" + format(determinant)
                + ",translation=" + format(matrix.get(12)) + '/' + format(matrix.get(13)) + '/' + format(matrix.get(14));
    }

    private static String uniformSummary(int program) {
        if (program <= 0) {
            return "none";
        }
        return "model=" + uniformMatrixSummary(program, "u_ModelViewMatrix")
                + ",projection=" + uniformMatrixSummary(program, "u_ProjectionMatrix")
                + ",region=" + uniformVectorSummary(program, "u_RegionOffset");
    }

    private static String uniformMatrixSummary(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            return "missing";
        }
        FloatBuffer values = BUFFERS.get().uniform;
        values.clear();
        GL20.glGetUniform(program, location, values);
        return "loc" + location + ":diag=" + format(values.get(0)) + '/' + format(values.get(5)) + '/'
                + format(values.get(10)) + '/' + format(values.get(15)) + ",translation="
                + format(values.get(12)) + '/' + format(values.get(13)) + '/' + format(values.get(14));
    }

    private static String uniformVectorSummary(int program, String name) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) {
            return "missing";
        }
        FloatBuffer values = BUFFERS.get().uniform;
        values.clear();
        GL20.glGetUniform(program, location, values);
        return "loc" + location + ':' + format(values.get(0)) + '/' + format(values.get(1)) + '/' + format(values.get(2));
    }

    private static String vertexAttribSummary() {
        StringBuilder result = new StringBuilder(96);
        IntBuffer value = BUFFERS.get().integer;
        for (int index = 0; index < 8; index++) {
            value.clear();
            GL20.glGetVertexAttrib(index, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, value);
            if (value.get(0) == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(';');
            }
            result.append(index).append('=');
            result.append(vertexAttribValue(index, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE)).append('x');
            result.append(vertexAttribValue(index, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE)).append('@');
            result.append(vertexAttribValue(index, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE)).append("/b");
            result.append(vertexAttribValue(index, 0x889F)); // GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING
        }
        return result.length() > 0 ? result.toString() : "none";
    }

    private static int vertexAttribValue(int index, int property) {
        IntBuffer value = BUFFERS.get().integer;
        value.clear();
        GL20.glGetVertexAttrib(index, property, value);
        return value.get(0);
    }

    private static String format(float value) {
        return Float.isFinite(value) ? String.format(Locale.ROOT, "%.4f", value) : "nan";
    }

    private static final class ProbeBuffers {
        private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer uniform = BufferUtils.createFloatBuffer(16);
        private final IntBuffer integer = BufferUtils.createIntBuffer(16);
    }
}
