package com.luna.ausm.impl.pipeline.vertex;

import java.nio.ByteBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;

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

    default int ausm$appendRawVertexData(int[] vertexData) {
        return 0;
    }

    default void ausm$putColorMultiplier(float redMultiplier, float greenMultiplier,
                                         float blueMultiplier, int vertexIndex) {
    }
}
