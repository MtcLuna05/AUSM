package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.vertex.BufferVertexDataAdapter;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BufferVertexDataAdapterTest {
    @Test
    void recognizesCommonBlockVertexStridesBeforeAmbiguousDivisibility() {
        assertEquals(7, BufferVertexDataAdapter.pipelineBlockVertexStride(new int[4 * 7], 14));
        assertEquals(8, BufferVertexDataAdapter.pipelineBlockVertexStride(new int[4 * 8], 14));
        assertEquals(14, BufferVertexDataAdapter.pipelineBlockVertexStride(new int[4 * 14], 14));
    }

    @Test
    void recognizesCommonBulkVertexStrides() {
        assertEquals(7 * Integer.BYTES, BufferVertexDataAdapter.pipelineBlockBulkStride(
                ByteBuffer.allocate(4 * 7 * Integer.BYTES), 4 * 7 * Integer.BYTES, 14 * Integer.BYTES));
        assertEquals(8 * Integer.BYTES, BufferVertexDataAdapter.pipelineBlockBulkStride(
                ByteBuffer.allocate(4 * 8 * Integer.BYTES), 4 * 8 * Integer.BYTES, 14 * Integer.BYTES));
    }

    @Test
    void copiesOnlyTheVanillaEntityPrefix() {
        int[] target = {-1, -1, -1, -1, -1, -1, -1, -1, -1};

        BufferVertexDataAdapter.copyVanillaEntityVertex(
                new int[]{10, 11, 12, 13, 14, 15, 16, 17}, 0, target, 1);

        assertArrayEquals(new int[]{-1, 10, 11, 12, 13, 14, 15, 16, -1}, target);
    }

    @Test
    void clearsOnlyTheRequestedScratchTail() {
        int[] scratch = {1, 2, 3, 4, 5};

        BufferVertexDataAdapter.clearVertexScratchTail(scratch, 2, 4);

        assertArrayEquals(new int[]{1, 2, 0, 0, 5}, scratch);
    }
}
