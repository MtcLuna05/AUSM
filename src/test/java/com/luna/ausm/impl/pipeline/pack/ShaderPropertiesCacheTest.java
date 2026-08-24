package com.luna.ausm.impl.pipeline.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShaderPropertiesCacheTest {
    @Test
    void cacheKeysDoNotDependOnOptionInsertionOrder() {
        Map<String, String> forward = new LinkedHashMap<>();
        forward.put("A", "1");
        forward.put("B", "2");
        Map<String, String> reverse = new LinkedHashMap<>();
        reverse.put("B", "2");
        reverse.put("A", "1");

        assertEquals(
                ShaderPropertiesCache.key("pack", forward, 7, "fingerprint"),
                ShaderPropertiesCache.key("pack", reverse, 7, "fingerprint"));
    }
}
