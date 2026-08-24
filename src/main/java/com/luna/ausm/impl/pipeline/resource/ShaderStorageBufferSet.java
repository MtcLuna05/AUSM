package com.luna.ausm.impl.pipeline.resource;

import com.luna.ausm.api.pipeline.pack.ShaderStorageBufferDirective;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

public final class ShaderStorageBufferSet {
    private final Map<Integer, BuiltShaderStorageBuffer> buffers;
    private final Map<Integer, Integer> glBuffers = new TreeMap<>();

    private ShaderStorageBufferSet(Map<Integer, BuiltShaderStorageBuffer> buffers) {
        this.buffers = Map.copyOf(buffers);
    }

    public static ShaderStorageBufferSet empty() {
        return new ShaderStorageBufferSet(Map.of());
    }

    public static ShaderStorageBufferSet load(ShaderPack pack, Map<Integer, ShaderStorageBufferDirective> directives) {
        if (directives.isEmpty()) {
            return empty();
        }

        Map<Integer, BuiltShaderStorageBuffer> loaded = new TreeMap<>();
        for (ShaderStorageBufferDirective directive : directives.values()) {
            byte[] content = loadInitialContent(pack, directive);
            if (content != null && content.length > directive.size()) {
                MainMod.LOGGER.warn(
                        "[ShaderStorageBuffers] Ignoring initial data for buffer {} because {} bytes exceed declared size {}",
                        directive.index(),
                        content.length,
                        directive.size()
                );
                content = null;
            }
            loaded.put(directive.index(), BuiltShaderStorageBuffer.from(directive, content));
        }
        ShaderStorageBufferSet set = new ShaderStorageBufferSet(loaded);
        set.createStaticBuffers();
        return set;
    }

    public boolean active() {
        return !buffers.isEmpty();
    }

    public int count() {
        return buffers.size();
    }

    public Iterable<Integer> bindingIndices() {
        return glBuffers.keySet();
    }

    public int glBufferId(int bindingIndex) {
        return glBuffers.getOrDefault(bindingIndex, 0);
    }

    public void resize(int width, int height) {
        for (BuiltShaderStorageBuffer buffer : buffers.values()) {
            if (!buffer.relative()) {
                continue;
            }
            int id = glBuffers.getOrDefault(buffer.index(), 0);
            if (id == 0) {
                continue;
            }
            long relativeWidth = Math.max(1L, (long) (width * buffer.scaleX()));
            long relativeHeight = Math.max(1L, (long) (height * buffer.scaleY()));
            allocateBuffer(buffer.index(), id, Math.max(1L, relativeWidth * relativeHeight * buffer.size()), null);
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, buffer.index(), id);
        }
    }

    public void delete() {
        for (Map.Entry<Integer, Integer> entry : glBuffers.entrySet()) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, entry.getKey(), 0);
            GL15.glDeleteBuffers(entry.getValue());
        }
        glBuffers.clear();
    }

    public void bind() {
        for (Map.Entry<Integer, Integer> entry : glBuffers.entrySet()) {
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, entry.getKey(), entry.getValue());
        }
    }

    private void createStaticBuffers() {
        for (BuiltShaderStorageBuffer buffer : buffers.values()) {
            int id = GL15.glGenBuffers();
            glBuffers.put(buffer.index(), id);
            if (buffer.relative()) {
                allocateBuffer(buffer.index(), id, Math.max(1L, buffer.size()), null);
            } else {
                allocateBuffer(buffer.index(), id, Math.max(1L, buffer.size()), buffer.initialContent());
            }
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, buffer.index(), id);
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        if (!glBuffers.isEmpty()) {
            MainMod.LOGGER.debug("[ShaderStorageBuffers] Allocated {} shader storage buffers", glBuffers.size());
        }
    }

    private static void allocateBuffer(int index, int id, long size, byte[] initialContent) {
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
        if (initialContent != null && initialContent.length > 0) {
            ByteBuffer content = BufferUtils.createByteBuffer(initialContent.length);
            content.put(initialContent);
            content.flip();
            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, 0L, content);
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            MainMod.LOGGER.warn("[ShaderStorageBuffers] GL error allocating SSBO {}: 0x{}", index, Integer.toHexString(error));
        }
    }

    private static byte[] loadInitialContent(ShaderPack pack, ShaderStorageBufferDirective directive) {
        if (directive.name() == null || directive.name().isBlank()) {
            return null;
        }

        String path = directive.name().startsWith("/") ? directive.name().substring(1) : directive.name();
        try (var stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                MainMod.LOGGER.warn("[ShaderStorageBuffers] Initial data '{}' for buffer {} was not found", path, directive.index());
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderStorageBuffers] Failed to read initial data '{}' for buffer {}", path, directive.index(), e);
            return null;
        }
    }
}
