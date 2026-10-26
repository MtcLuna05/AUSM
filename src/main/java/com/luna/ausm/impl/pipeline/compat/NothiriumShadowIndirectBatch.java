package com.luna.ausm.impl.pipeline.compat;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;

/**
 * Owns the small, transient command streams used by the Theseus shadow path.
 * Nothirium's GL 4.3 renderer uses the same representation: one three-float
 * instance offset and one four-int DrawArraysIndirect command per section.
 */
final class NothiriumShadowIndirectBatch {
    private static final int OFFSET_FLOATS = 3;
    private static final int COMMAND_INTS = 4;
    private static final int OFFSET_STRIDE_BYTES = OFFSET_FLOATS * Float.BYTES;
    private static final int DRAW_INDIRECT_BUFFER = 0x8F3F;

    private FloatBuffer offsets = BufferUtils.createFloatBuffer(OFFSET_FLOATS * 256);
    private IntBuffer commands = BufferUtils.createIntBuffer(COMMAND_INTS * 256);
    private int offsetBuffer;
    private final Map<Object, CommandStream> commandStreams = new IdentityHashMap<>();

    private static final class CommandStream {
        int buffer;
        int[] values = new int[0];
        int count = -1;
        long vboGeneration = Long.MIN_VALUE;
    }

    void invalidateCachedCommands() {
        for (CommandStream stream : commandStreams.values()) {
            stream.count = -1;
            stream.vboGeneration = Long.MIN_VALUE;
        }
    }

    FloatBuffer beginOffsets(int sectionCapacity) {
        int required = Math.max(1, sectionCapacity) * OFFSET_FLOATS;
        if (offsets.capacity() < required) {
            offsets = BufferUtils.createFloatBuffer(grownCapacity(offsets.capacity(), required));
        }
        offsets.clear();
        return offsets;
    }

    IntBuffer beginCommands(int sectionCapacity) {
        int required = Math.max(1, sectionCapacity) * COMMAND_INTS;
        if (commands.capacity() < required) {
            commands = BufferUtils.createIntBuffer(grownCapacity(commands.capacity(), required));
        }
        commands.clear();
        return commands;
    }

    boolean draw(int vertexBuffer, int vertexStride, int offsetAttribute, int drawMode,
                 int fallbackBlockEntityId, short fallbackRenderType,
                 FloatBuffer offsetData, IntBuffer commandData, int drawCount, long vboGeneration,
                 Object passKey) {
        if (drawCount <= 0) {
            return false;
        }
        ensureOffsetBuffer();
        CommandStream stream = commandStreams.computeIfAbsent(passKey, ignored -> new CommandStream());
        boolean newCommandBuffer = ensureCommandBuffer(stream);
        offsetData.flip();
        commandData.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffer);
        NothiriumShadowRenderer.setupArrayPointers(vertexStride, fallbackBlockEntityId, fallbackRenderType);
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, offsetBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, offsetData, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(offsetAttribute);
            GL20.glVertexAttribPointer(offsetAttribute, OFFSET_FLOATS, GL11.GL_FLOAT, false, OFFSET_STRIDE_BYTES, 0L);
            GL33.glVertexAttribDivisor(offsetAttribute, 1);

            GL15.glBindBuffer(DRAW_INDIRECT_BUFFER, stream.buffer);
            boolean uploadCommands = newCommandBuffer || !commandsMatch(stream, commandData, drawCount, vboGeneration);
            if (uploadCommands) {
                GL15.glBufferData(DRAW_INDIRECT_BUFFER, commandData, GL15.GL_STREAM_DRAW);
                rememberCommands(stream, commandData, drawCount, vboGeneration);
            }
            GL43.glMultiDrawArraysIndirect(drawMode, 0L, drawCount, 0);
            return uploadCommands;
        } finally {
            GL15.glBindBuffer(DRAW_INDIRECT_BUFFER, 0);
            GL33.glVertexAttribDivisor(offsetAttribute, 0);
            GL20.glDisableVertexAttribArray(offsetAttribute);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        }
    }

    private void ensureOffsetBuffer() {
        if (offsetBuffer == 0) {
            offsetBuffer = GL15.glGenBuffers();
        }
    }

    private static boolean ensureCommandBuffer(CommandStream stream) {
        if (stream.buffer != 0) {
            return false;
        }
        stream.buffer = GL15.glGenBuffers();
        return true;
    }

    private static boolean commandsMatch(CommandStream stream, IntBuffer commandData,
                                         int drawCount, long vboGeneration) {
        if (stream.count != drawCount || stream.vboGeneration != vboGeneration
                || stream.values.length < drawCount * COMMAND_INTS) {
            return false;
        }
        int commandValues = drawCount * COMMAND_INTS;
        for (int index = 0; index < commandValues; index++) {
            if (stream.values[index] != commandData.get(index)) {
                return false;
            }
        }
        return true;
    }

    private static void rememberCommands(CommandStream stream, IntBuffer commandData,
                                         int drawCount, long vboGeneration) {
        int commandValues = drawCount * COMMAND_INTS;
        if (stream.values.length < commandValues) {
            stream.values = new int[grownCapacity(stream.values.length, commandValues)];
        }
        for (int index = 0; index < commandValues; index++) {
            stream.values[index] = commandData.get(index);
        }
        stream.count = drawCount;
        stream.vboGeneration = vboGeneration;
    }

    private static int grownCapacity(int current, int required) {
        int capacity = Math.max(1, current);
        while (capacity < required) {
            capacity = Math.multiplyExact(capacity, 2);
        }
        return capacity;
    }
}
