package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.impl.MainMod;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

final class CustomUniformUploader {
    private static final FloatBuffer MATRIX_BUFFER = BufferUtils.createFloatBuffer(16);

    private CustomUniformUploader() {
    }

    static void upload(ShaderProgram program, CustomUniformSet.RawUniform uniform, float[] values) {
        if (values.length == 0) {
            return;
        }
        int expected = uniform.expectedValues();
        if (expected > 0 && values.length < expected) {
            MainMod.LOGGER.warn("[CustomUniforms] Ignoring custom uniform '{}' with too few values: {}",
                    uniform.name(), uniform.expression());
            return;
        }

        int location = program.getUniformLocation(uniform.name());
        if (location == -1) {
            return;
        }

        switch (uniform.type()) {
            case "bool", "int" -> GL20.glUniform1i(location, (int) values[0]);
            case "float" -> GL20.glUniform1f(location, values[0]);
            case "vec2" -> GL20.glUniform2f(location, values[0], values[1]);
            case "vec3" -> GL20.glUniform3f(location, values[0], values[1], values[2]);
            case "vec4" -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
            case "ivec2" -> GL20.glUniform2i(location, (int) values[0], (int) values[1]);
            case "ivec3" -> GL20.glUniform3i(location, (int) values[0], (int) values[1], (int) values[2]);
            case "ivec4" ->
                    GL20.glUniform4i(location, (int) values[0], (int) values[1], (int) values[2], (int) values[3]);
            case "bvec2" -> GL20.glUniform2i(location, bool(values[0]), bool(values[1]));
            case "bvec3" -> GL20.glUniform3i(location, bool(values[0]), bool(values[1]), bool(values[2]));
            case "bvec4" ->
                    GL20.glUniform4i(location, bool(values[0]), bool(values[1]), bool(values[2]), bool(values[3]));
            case "mat2" -> uploadMatrix(location, values, 4, 2);
            case "mat3" -> uploadMatrix(location, values, 9, 3);
            case "mat4" -> uploadMatrix(location, values, 16, 4);
            default -> {
            }
        }
    }

    static int expectedValues(String type) {
        return switch (type) {
            case "bool", "int", "float" -> 1;
            case "vec2", "ivec2", "bvec2" -> 2;
            case "vec3", "ivec3", "bvec3" -> 3;
            case "vec4", "ivec4", "bvec4" -> 4;
            case "mat2" -> 4;
            case "mat3" -> 9;
            case "mat4" -> 16;
            default -> -1;
        };
    }

    private static void uploadMatrix(int location, float[] values, int count, int dimension) {
        MATRIX_BUFFER.clear();
        MATRIX_BUFFER.put(values, 0, count);
        MATRIX_BUFFER.flip();
        switch (dimension) {
            case 2 -> GL20.glUniformMatrix2(location, false, MATRIX_BUFFER);
            case 3 -> GL20.glUniformMatrix3(location, false, MATRIX_BUFFER);
            case 4 -> GL20.glUniformMatrix4(location, false, MATRIX_BUFFER);
            default -> {
            }
        }
    }

    private static int bool(float value) {
        return Math.abs(value) > 0.000001F ? 1 : 0;
    }
}
