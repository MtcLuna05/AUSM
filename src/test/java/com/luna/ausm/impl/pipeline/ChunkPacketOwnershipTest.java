package com.luna.ausm.impl.pipeline;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ChunkPacketOwnershipTest {
    @Test
    void vanillaPacketHandlerMixinIsNeitherRegisteredNorPackaged() throws Exception {
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream("/ausm.mod.mixin.json"))) {
            String config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(config.contains("NetHandlerPlayClientMixin"));
        }
        assertNull(getClass().getResource("/com/luna/ausm/impl/mixin/pipeline/NetHandlerPlayClientMixin.class"));
    }
}
