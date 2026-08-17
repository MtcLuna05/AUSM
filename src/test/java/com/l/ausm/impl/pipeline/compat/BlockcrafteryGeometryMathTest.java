package com.l.ausm.impl.pipeline.compat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlockcrafteryGeometryMathTest {
    @Test
    void integerPayloadRoundTripsInEitherByteOrder() {
        int[] values = {0x01020304, 0x7f00ff80, -1};

        assertArrayEquals(values, BlockcrafteryGeometryMath.integers(
                BlockcrafteryGeometryMath.bytes(values, ByteOrder.LITTLE_ENDIAN), ByteOrder.LITTLE_ENDIAN));
        assertArrayEquals(values, BlockcrafteryGeometryMath.integers(
                BlockcrafteryGeometryMath.bytes(values, ByteOrder.BIG_ENDIAN), ByteOrder.BIG_ENDIAN));
    }

    @Test
    void derivesNormalizedQuadDirectionAndArea() {
        byte[] quad = quad(
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f);

        assertArrayEquals(new float[]{0.0f, 0.0f, 1.0f},
                BlockcrafteryGeometryMath.normal(quad, 0, 3 * Float.BYTES, ByteOrder.nativeOrder()), 0.0001f);
        assertEquals(1.0f, BlockcrafteryGeometryMath.area(
                quad, 0, 3 * Float.BYTES, ByteOrder.nativeOrder()), 0.0001f);
    }

    @Test
    void replacesTheRequestedLogicalByteInEitherByteOrder() {
        assertEquals(0x0102aa04, BlockcrafteryGeometryMath.replaceByte(
                0x01020304, 1, 0xaa, ByteOrder.LITTLE_ENDIAN));
        assertEquals(0x01aa0304, BlockcrafteryGeometryMath.replaceByte(
                0x01020304, 1, 0xaa, ByteOrder.BIG_ENDIAN));
    }

    private static byte[] quad(float... positions) {
        ByteBuffer result = ByteBuffer.allocate(positions.length * Float.BYTES).order(ByteOrder.nativeOrder());
        for (float position : positions) {
            result.putFloat(position);
        }
        return result.array();
    }
}
