package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles the declaration, evaluation, and uploading of GLSL Uniforms.
 */
public class UniformRegistry {

    private final Map<String, UniformBinding<?>> bindings = new HashMap<>();

    // Pre-allocated buffers to avoid memory allocations every frame
    private static final FloatBuffer FLOAT_BUFFER_1 = BufferUtils.createFloatBuffer(1);
    private static final FloatBuffer FLOAT_BUFFER_2 = BufferUtils.createFloatBuffer(2);
    private static final FloatBuffer FLOAT_BUFFER_3 = BufferUtils.createFloatBuffer(3);
    private static final FloatBuffer FLOAT_BUFFER_4 = BufferUtils.createFloatBuffer(4);
    private static final IntBuffer INT_BUFFER_2 = BufferUtils.createIntBuffer(2);
    private static final IntBuffer INT_BUFFER_3 = BufferUtils.createIntBuffer(3);
    private static final IntBuffer INT_BUFFER_4 = BufferUtils.createIntBuffer(4);

    /**
     * Registers an integer uniform.
     */
    public void registerInt(String name, Supplier<Integer> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                OpenGlHelper.glUniform1i(location, value);
            }
        }));
    }

    /**
     * Registers a float uniform.
     */
    public void registerFloat(String name, Supplier<Float> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                FLOAT_BUFFER_1.clear();
                FLOAT_BUFFER_1.put(value);
                FLOAT_BUFFER_1.flip();
                OpenGlHelper.glUniform1(location, FLOAT_BUFFER_1);
            }
        }));
    }

    public void registerVec2i(String name, Supplier<int[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 2) {
                INT_BUFFER_2.clear();
                INT_BUFFER_2.put(value[0]);
                INT_BUFFER_2.put(value[1]);
                INT_BUFFER_2.flip();
                GL20.glUniform2(location, INT_BUFFER_2);
            }
        }));
    }

    public void registerVec3i(String name, Supplier<int[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 3) {
                INT_BUFFER_3.clear();
                INT_BUFFER_3.put(value, 0, 3);
                INT_BUFFER_3.flip();
                GL20.glUniform3(location, INT_BUFFER_3);
            }
        }));
    }

    public void registerVec4i(String name, Supplier<int[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 4) {
                INT_BUFFER_4.clear();
                INT_BUFFER_4.put(value, 0, 4);
                INT_BUFFER_4.flip();
                GL20.glUniform4(location, INT_BUFFER_4);
            }
        }));
    }

    public void registerVec2(String name, Supplier<float[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 2) {
                FLOAT_BUFFER_2.clear();
                FLOAT_BUFFER_2.put(value, 0, 2);
                FLOAT_BUFFER_2.flip();
                GL20.glUniform2(location, FLOAT_BUFFER_2);
            }
        }));
    }

    /**
     * Registers a 3-component float vector (vec3).
     */
    public void registerVec3(String name, Supplier<float[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 3) {
                FLOAT_BUFFER_3.clear();
                FLOAT_BUFFER_3.put(value);
                FLOAT_BUFFER_3.flip();
                OpenGlHelper.glUniform3(location, FLOAT_BUFFER_3);
            }
        }));
    }

    public void registerVec4(String name, Supplier<float[]> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 4) {
                FLOAT_BUFFER_4.clear();
                FLOAT_BUFFER_4.put(value, 0, 4);
                FLOAT_BUFFER_4.flip();
                GL20.glUniform4(location, FLOAT_BUFFER_4);
            }
        }));
    }

    /**
     * Registers a 4x4 matrix uniform.
     */
    public void registerMatrix4(String name, Supplier<FloatBuffer> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                OpenGlHelper.glUniformMatrix4(location, false, value);
            }
        }));
    }

    public void registerMatrix3(String name, Supplier<FloatBuffer> supplier) {
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                GL20.glUniformMatrix3(location, false, value);
            }
        }));
    }

    /**
     * Uploads all registered uniforms to the currently bound ShaderProgram.
     */
    public void uploadAll(ShaderProgram program) {
        for (UniformBinding<?> binding : bindings.values()) {
            int location = program.getUniformLocation(binding.name);
            if (location != -1) {
                binding.upload(location);
            }
        }
    }

    public void upload(ShaderProgram program, String name) {
        UniformBinding<?> binding = bindings.get(name);
        if (binding == null) {
            return;
        }

        int location = program.getUniformLocation(binding.name);
        if (location != -1) {
            binding.upload(location);
        }
    }

    public Map<String, float[]> scalarValues() {
        Map<String, float[]> values = new HashMap<>();
        for (UniformBinding<?> binding : bindings.values()) {
            Object value = binding.value();
            if (value instanceof Number number) {
                values.put(binding.name, new float[]{number.floatValue()});
            } else if (value instanceof float[] vector) {
                addVectorScalarValues(values, binding.name, vector);
            } else if (value instanceof int[] vector) {
                addVectorScalarValues(values, binding.name, vector);
            } else if (value instanceof FloatBuffer matrix) {
                addMatrixScalarValues(values, binding.name, matrix);
            }
        }
        return values;
    }

    private static void addVectorScalarValues(Map<String, float[]> values, String name, float[] vector) {
        values.put(name, vector.clone());
        int count = Math.min(vector.length, 4);
        for (int i = 0; i < count; i++) {
            addVectorComponent(values, name, i, vector[i]);
        }
    }

    private static void addVectorScalarValues(Map<String, float[]> values, String name, int[] vector) {
        float[] floatVector = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            floatVector[i] = vector[i];
        }
        addVectorScalarValues(values, name, floatVector);
    }

    private static void addVectorComponent(Map<String, float[]> values, String name, int index, float value) {
        values.put(name + "." + index, new float[]{value});
        values.put(name + "." + "xyzw".charAt(index), new float[]{value});
        values.put(name + "." + "rgba".charAt(index), new float[]{value});
    }

    private static void addMatrixScalarValues(Map<String, float[]> values, String name, FloatBuffer matrix) {
        int dimension;
        if (matrix.limit() >= 16) {
            dimension = 4;
        } else if (matrix.limit() >= 9) {
            dimension = 3;
        } else {
            return;
        }

        for (int row = 0; row < dimension; row++) {
            for (int column = 0; column < dimension; column++) {
                values.put(name + "." + row + "." + column, new float[]{matrix.get(column * dimension + row)});
            }
        }
    }

    /**
     * A generic binding linking a Uniform name, a value supplier, and a GL upload function.
     */
    private static class UniformBinding<T> {
        final String name;
        final Supplier<T> supplier;
        final UniformUploader<T> uploader;

        UniformBinding(String name, Supplier<T> supplier, UniformUploader<T> uploader) {
            this.name = name;
            this.supplier = supplier;
            this.uploader = uploader;
        }

        void upload(int location) {
            T value = value();
            if (value != null) {
                uploader.upload(location, value);
            }
        }

        T value() {
            return supplier.get();
        }
    }

    @FunctionalInterface
    private interface UniformUploader<T> {
        void upload(int location, T value);
    }
}
