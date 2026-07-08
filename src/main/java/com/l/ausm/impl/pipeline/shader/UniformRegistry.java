package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Handles the declaration, evaluation, and uploading of GLSL Uniforms.
 */
public class UniformRegistry {
    private static final int MAX_PROGRAM_UNIFORM_CACHE_ENTRIES = 128;

    private final Map<String, UniformBinding<?>> bindings = new HashMap<>();
    private final Map<ShaderProgram, List<ResolvedUniformBinding>> activeBindingsByProgram = new IdentityHashMap<>();

    // Pre-allocated buffers to avoid memory allocations every frame
    private static final IntBuffer INT_BUFFER_2 = BufferUtils.createIntBuffer(2);
    private static final IntBuffer INT_BUFFER_3 = BufferUtils.createIntBuffer(3);
    private static final IntBuffer INT_BUFFER_4 = BufferUtils.createIntBuffer(4);

    /**
     * Registers an integer uniform.
     */
    public void registerInt(String name, Supplier<Integer> supplier) {
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glUniform1i(location, value);
            }
        }));
    }

    /**
     * Registers a float uniform.
     */
    public void registerFloat(String name, Supplier<Float> supplier) {
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                GL20.glUniform1f(location, value);
            }
        }));
    }

    public void registerVec2i(String name, Supplier<int[]> supplier) {
        activeBindingsByProgram.clear();
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
        activeBindingsByProgram.clear();
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
        activeBindingsByProgram.clear();
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
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 2) {
                GL20.glUniform2f(location, value[0], value[1]);
            }
        }));
    }

    /**
     * Registers a 3-component float vector (vec3).
     */
    public void registerVec3(String name, Supplier<float[]> supplier) {
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 3) {
                GL20.glUniform3f(location, value[0], value[1], value[2]);
            }
        }));
    }

    public void registerVec4(String name, Supplier<float[]> supplier) {
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1 && value.length >= 4) {
                GL20.glUniform4f(location, value[0], value[1], value[2], value[3]);
            }
        }));
    }

    /**
     * Registers a 4x4 matrix uniform.
     */
    public void registerMatrix4(String name, Supplier<FloatBuffer> supplier) {
        activeBindingsByProgram.clear();
        bindings.put(name, new UniformBinding<>(name, supplier, (location, value) -> {
            if (location != -1) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glUniformMatrix4(location, false, value);
            }
        }));
    }

    public void registerMatrix3(String name, Supplier<FloatBuffer> supplier) {
        activeBindingsByProgram.clear();
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
        for (ResolvedUniformBinding binding : activeBindingsFor(program)) {
            binding.upload();
        }
    }

    private List<ResolvedUniformBinding> activeBindingsFor(ShaderProgram program) {
        if (program == null) {
            return List.of();
        }
        List<ResolvedUniformBinding> cached = activeBindingsByProgram.get(program);
        if (cached != null) {
            return cached;
        }
        if (activeBindingsByProgram.size() > MAX_PROGRAM_UNIFORM_CACHE_ENTRIES) {
            activeBindingsByProgram.clear();
        }

        List<ResolvedUniformBinding> active = new ArrayList<>();
        for (UniformBinding<?> binding : bindings.values()) {
            int location = program.getUniformLocation(binding.name);
            if (location != -1) {
                active.add(new ResolvedUniformBinding(binding, location));
            }
        }
        active = List.copyOf(active);
        activeBindingsByProgram.put(program, active);
        return active;
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
        return scalarValuesInto(new HashMap<>());
    }

    public Map<String, float[]> scalarValuesInto(Map<String, float[]> values) {
        values.clear();
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

        @SuppressWarnings("unchecked")
        void uploadValue(int location, Object value) {
            uploader.upload(location, (T) value);
        }
    }

    private static class ResolvedUniformBinding {
        final UniformBinding<?> binding;
        final int location;
        private boolean hasUploadedValue;
        private Object lastUploadedValue;

        ResolvedUniformBinding(UniformBinding<?> binding, int location) {
            this.binding = binding;
            this.location = location;
        }

        void upload() {
            Object value = binding.value();
            if (value == null || isUnchanged(value)) {
                return;
            }
            binding.uploadValue(location, value);
            lastUploadedValue = snapshotValue(value);
            hasUploadedValue = true;
        }

        private boolean isUnchanged(Object value) {
            if (!hasUploadedValue) {
                return false;
            }
            Object previous = lastUploadedValue;
            if (previous instanceof float[] previousFloats) {
                if (value instanceof float[] currentFloats) {
                    return Arrays.equals(previousFloats, currentFloats);
                }
                if (value instanceof FloatBuffer currentMatrix) {
                    return floatBufferEquals(previousFloats, currentMatrix);
                }
                return false;
            }
            if (previous instanceof int[] previousInts && value instanceof int[] currentInts) {
                return Arrays.equals(previousInts, currentInts);
            }
            return previous.equals(value);
        }

        private static Object snapshotValue(Object value) {
            if (value instanceof float[] vector) {
                return vector.clone();
            }
            if (value instanceof int[] vector) {
                return vector.clone();
            }
            if (value instanceof FloatBuffer matrix) {
                float[] snapshot = new float[matrix.limit()];
                for (int i = 0; i < snapshot.length; i++) {
                    snapshot[i] = matrix.get(i);
                }
                return snapshot;
            }
            return value;
        }

        private static boolean floatBufferEquals(float[] previous, FloatBuffer current) {
            if (previous.length != current.limit()) {
                return false;
            }
            for (int i = 0; i < previous.length; i++) {
                if (Float.compare(previous[i], current.get(i)) != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    @FunctionalInterface
    private interface UniformUploader<T> {
        void upload(int location, T value);
    }
}
