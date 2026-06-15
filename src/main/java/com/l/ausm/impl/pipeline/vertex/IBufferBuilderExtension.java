package com.l.ausm.impl.pipeline.vertex;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

/**
 * Interface injected into BufferBuilder to avoid illegal mixin casting.
 */
public interface IBufferBuilderExtension {
    default void ausm$setEntityId(int entityId, int renderType) {
    }

    void ausm$forceResetDrawingState();

    default boolean ausm$isDrawing() {
        return false;
    }
}
