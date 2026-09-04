package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import java.nio.IntBuffer;
import java.util.Locale;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

/**
 * Delayed readback for sparse, shader-clock-based terrain profiling.
 *
 * <p>The instrumented fragment shader accumulates samples into one SSBO for
 * 90 frames. Three buffers are rotated so a completed window remains unused
 * for 180 frames before readback, avoiding a current-frame GPU fence.</p>
 */
public final class TerrainShaderGpuProbe {
    public static final String ENABLE_PROPERTY = "ausm.terrainShaderGpuProbe";

    private static final String[] SCOPE_NAMES = {
            "total",
            "setup",
            "baseTexture",
            "materialSetup",
            "materials",
            "lightingTotal",
            "lightingPrelude",
            "shadows",
            "blocklight",
            "lightingMix",
            "post",
            "outputs"
    };
    private static final int WORDS_PER_SCOPE = 3;
    private static final int WORD_COUNT = SCOPE_NAMES.length * WORDS_PER_SCOPE;
    private static final int BYTE_COUNT = WORD_COUNT * Integer.BYTES;
    private static final int BUFFER_COUNT = 3;
    private static final int WINDOW_FRAMES = 90;
    private static final int[] BUFFERS = new int[BUFFER_COUNT];
    private static final long[] BUFFER_WINDOWS = {-1L, -1L, -1L};

    private static boolean initialized;
    private static boolean disabled;
    private static Boolean supported;
    private static int bindingIndex = -1;
    private static int activeBuffer;
    private static int framesInWindow;
    private static long nextWindow;

    private TerrainShaderGpuProbe() {
    }

    public static boolean requestedAndSupported() {
        return Boolean.getBoolean(ENABLE_PROPERTY) && supported();
    }

    public static int bindingIndex() {
        if (bindingIndex < 0 && supported()) {
            bindingIndex = Math.max(0, GL11.glGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) - 1);
        }
        return bindingIndex;
    }

    static void beginFrame() {
        if (!ensureInitialized()) {
            return;
        }
        if (framesInWindow >= WINDOW_FRAMES) {
            rotateWindow();
        }
        framesInWindow++;
    }

    static void bindForTerrain() {
        if (!ensureInitialized()) {
            return;
        }
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, BUFFERS[activeBuffer]);
    }

    static void release() {
        if (!initialized) {
            return;
        }
        try {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, 0);
            for (int i = 0; i < BUFFERS.length; i++) {
                if (BUFFERS[i] != 0) {
                    GL15.glDeleteBuffers(BUFFERS[i]);
                    BUFFERS[i] = 0;
                }
                BUFFER_WINDOWS[i] = -1L;
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        initialized = false;
        disabled = false;
        activeBuffer = 0;
        framesInWindow = 0;
        nextWindow = 0L;
    }

    private static boolean ensureInitialized() {
        if (disabled || !requestedAndSupported()) {
            return false;
        }
        if (initialized) {
            return true;
        }
        try {
            bindingIndex = bindingIndex();
            for (int i = 0; i < BUFFERS.length; i++) {
                BUFFERS[i] = GL15.glGenBuffers();
                clearBuffer(BUFFERS[i]);
            }
            BUFFER_WINDOWS[0] = nextWindow++;
            activeBuffer = 0;
            framesInWindow = 0;
            initialized = true;
            MainMod.LOGGER.info(
                    "[AUSMTerrainShaderProbe] active=true binding={} sampleRate=1/65536 windowFrames={}",
                    bindingIndex,
                    WINDOW_FRAMES
            );
            return true;
        } catch (RuntimeException | LinkageError error) {
            disable("initialize", error);
            return false;
        }
    }

    private static void rotateWindow() {
        int next = (activeBuffer + 1) % BUFFER_COUNT;
        if (BUFFER_WINDOWS[next] >= 0L) {
            readAndLog(next, BUFFER_WINDOWS[next]);
        }
        clearBuffer(BUFFERS[next]);
        BUFFER_WINDOWS[next] = nextWindow++;
        activeBuffer = next;
        framesInWindow = 0;
        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingIndex, BUFFERS[activeBuffer]);
    }

    private static void clearBuffer(int buffer) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, BYTE_COUNT, GL15.GL_STREAM_READ);
        IntBuffer zeros = BufferUtils.createIntBuffer(WORD_COUNT);
        GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, zeros);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    private static void readAndLog(int bufferIndex, long window) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, BUFFERS[bufferIndex]);
        IntBuffer values = BufferUtils.createIntBuffer(WORD_COUNT);
        GL15.glGetBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, values);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        long totalTicks = Integer.toUnsignedLong(values.get(0));
        long totalSamples = Integer.toUnsignedLong(values.get(1));
        StringBuilder summary = new StringBuilder(320)
                .append("window=").append(window)
                .append(" samples=").append(totalSamples);
        double totalAverage = totalSamples > 0L ? totalTicks / (double) totalSamples : 0.0D;
        for (int scope = 0; scope < SCOPE_NAMES.length; scope++) {
            int base = scope * WORDS_PER_SCOPE;
            long ticks = Integer.toUnsignedLong(values.get(base));
            long samples = Integer.toUnsignedLong(values.get(base + 1));
            long maximum = Integer.toUnsignedLong(values.get(base + 2));
            double average = samples > 0L ? ticks / (double) samples : 0.0D;
            double share = totalAverage > 0.0D ? average * 100.0D / totalAverage : 0.0D;
            summary.append(' ').append(SCOPE_NAMES[scope]).append('=')
                    .append(format(average)).append("ticks/sample")
                    .append(',').append(format(share)).append("%total")
                    .append(',').append(maximum).append("max")
                    .append(',').append(samples).append("n");
        }
        MainMod.LOGGER.info("[AUSMTerrainShaderProbe] {}", summary);
    }

    private static boolean supported() {
        if (supported != null) {
            return supported;
        }
        try {
            supported = GLContext.getCapabilities().OpenGL43 && hasExtension("GL_ARB_shader_clock");
        } catch (RuntimeException | LinkageError error) {
            supported = false;
        }
        return supported;
    }

    private static boolean hasExtension(String expected) {
        int count = Math.max(0, GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS));
        for (int index = 0; index < count; index++) {
            if (expected.equals(GL30.glGetStringi(GL11.GL_EXTENSIONS, index))) {
                return true;
            }
        }
        return false;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void disable(String stage, Throwable error) {
        if (!disabled) {
            MainMod.LOGGER.warn("[AUSMTerrainShaderProbe] active=false stage={} reason={}: {}",
                    stage, error.getClass().getSimpleName(), error.getMessage());
        }
        disabled = true;
    }
}
