package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderOptionScannerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversOptionsDeclaredInSharedProgramGlslSources() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("shaders/program"));
        Files.writeString(temporaryDirectory.resolve("shaders/shaders.properties"), "sliders=GLOWING_ORE_MULT\n");
        Files.writeString(temporaryDirectory.resolve("shaders/program/deferred1.glsl"), """
                #define GLOWING_ORE_MULT 1.00 //[0.50 1.00 2.00]
                """);
        Properties properties = new Properties();
        properties.setProperty("sliders", "GLOWING_ORE_MULT");

        ShaderOptions options = ShaderOptionScanner.scan(
                new FolderShaderPack(temporaryDirectory), properties, Map.of("GLOWING_ORE_MULT", "2.00"));

        assertTrue(options.contains("GLOWING_ORE_MULT"));
        assertEquals("2.00", options.get("GLOWING_ORE_MULT").value());
    }

}
