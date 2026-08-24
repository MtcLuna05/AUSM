package com.luna.ausm.impl.pipeline.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPipelineWorldLoadGateTest {
    @Test
    void keepsOnlyTheLatestQueuedWorldDimension() {
        ShaderPipelineWorldLoadGate gate = new ShaderPipelineWorldLoadGate();

        gate.queue(0);
        gate.queue(-7);

        assertTrue(gate.isPending());
        assertEquals(-7, gate.pendingDimensionId());
    }

    @Test
    void clearDropsTheDeferredCompileRequest() {
        ShaderPipelineWorldLoadGate gate = new ShaderPipelineWorldLoadGate();
        gate.queue(1);

        gate.clear();

        assertFalse(gate.isPending());
        assertEquals(Integer.MIN_VALUE, gate.pendingDimensionId());
    }
}
