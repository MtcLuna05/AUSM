package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;
import net.minecraft.client.renderer.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Interface injected into BufferBuilder to avoid illegal mixin casting.
 */
public interface IBufferBuilderExtension {
    default void ausm$setEntityId(int entityId, int renderType) {
    }

    void ausm$forceResetDrawingState();

    default void ausm$truncateVertexCount(int vertexCount) {
    }

    default boolean ausm$isDrawing() {
        return false;
    }

    default void ausm$resetShaderlessBloomMetadata() {
    }

    default void ausm$markShaderlessBloomMetadata() {
    }

    default boolean ausm$hasShaderlessBloomMetadata() {
        return true;
    }

    default VertexFormat ausm$vertexFormat() {
        return null;
    }

    default ByteBuffer ausm$byteBuffer() {
        return null;
    }

    default int ausm$vertexCount() {
        return 0;
    }

    default void ausm$addVertexData(int[] vertexData) {
    }

    default void ausm$putColorMultiplier(float redMultiplier, float greenMultiplier,
                                         float blueMultiplier, int vertexIndex) {
    }
}
