package com.luna.ausm.impl.pipeline.shader;

import com.luna.ausm.api.pipeline.shader.ProgramId;
import com.luna.ausm.api.pipeline.shader.ProgramSourceSet;
import com.luna.ausm.impl.pipeline.pack.FolderShaderPack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ProgramSourceResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void usesStageGuardedProgramGlslForBothMissingGraphicsStages() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("shaders/program"));
        Files.writeString(temporaryDirectory.resolve("shaders/shaders.properties"), "");
        Files.writeString(temporaryDirectory.resolve("shaders/program/deferred1.glsl"), """
                #ifdef VERTEX_SHADER
                void main() { }
                #endif
                #ifdef FRAGMENT_SHADER
                void main() { }
                #endif
                """);

        FolderShaderPack pack = new FolderShaderPack(temporaryDirectory);
        ProgramSourceSet source = ProgramSourceResolver.resolve(pack, ProgramId.DEFERRED1, 0);

        assertEquals("shaders/program/deferred1.glsl", source.vertexPath());
        assertEquals("shaders/program/deferred1.glsl", source.fragmentPath());
    }

    @Test
    void keepsUnguardedProgramGlslFragmentOnly() throws IOException {
        Files.createDirectories(temporaryDirectory.resolve("shaders/program"));
        Files.writeString(temporaryDirectory.resolve("shaders/shaders.properties"), "");
        Files.writeString(temporaryDirectory.resolve("shaders/program/deferred1.glsl"), "void main() { }\n");

        FolderShaderPack pack = new FolderShaderPack(temporaryDirectory);
        ProgramSourceSet source = ProgramSourceResolver.resolve(pack, ProgramId.DEFERRED1, 0);

        assertNull(source.vertexPath());
        assertEquals("shaders/program/deferred1.glsl", source.fragmentPath());
    }
}
