package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.pipeline.PipelineContext;

import java.util.ArrayDeque;
import java.util.Deque;

/** Runtime bridge used by the lazy Draconic Evolution renderer transformer. */
public final class DraconicRenderedEmissionCompat {
    private static final int DYNAMIC_EMISSION_BLOCK_ENTITY_ID = 21024;
    private static final ThreadLocal<Deque<Boolean>> EMISSION_SCOPES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private DraconicRenderedEmissionCompat() {
    }

    public static void beginEmission() {
        EMISSION_SCOPES.get().push(PipelineContext.getInstance()
                .beginDynamicBlockEntityEmission(DYNAMIC_EMISSION_BLOCK_ENTITY_ID));
    }

    public static void endEmission() {
        Deque<Boolean> scopes = EMISSION_SCOPES.get();
        if (!scopes.isEmpty() && scopes.pop()) {
            PipelineContext.getInstance().endDynamicBlockEntityEmission();
        }
        if (scopes.isEmpty()) {
            EMISSION_SCOPES.remove();
        }
    }

    public static boolean useDraconicReactorShader(boolean requested) {
        return requested && !PipelineContext.getInstance().canRenderDynamicBlockEntityEmission();
    }
}
