package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.shader.ComputeProgramSource;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

public final class ComputeProgram {
    private final ComputeProgramSource source;
    private final ShaderProgram program;
    private final int[] workGroups;
    private final float[] workGroupRelative;

    private ComputeProgram(ComputeProgramSource source, ShaderProgram program, int[] workGroups, float[] workGroupRelative) {
        this.source = source;
        this.program = program;
        this.workGroups = workGroups;
        this.workGroupRelative = workGroupRelative;
    }

    public static ComputeProgram compile(ShaderPack pack, ShaderProperties properties, ComputeProgramSource source) {
        return compile(pack, properties, source, properties.packDirectives());
    }

    public static ComputeProgram compile(ShaderPack pack, ShaderProperties properties, ComputeProgramSource source, ShaderPackDirectives directives) {
        if (source.path() == null) {
            return null;
        }

        try {
            String processed = ShaderPreprocessor.processShaderSource(
                    pack,
                    source.path(),
                    properties.options(),
                    null,
                    GL43.GL_COMPUTE_SHADER,
                    source.name(),
                    directives
            );
            if (processed == null || processed.isBlank()) {
                return null;
            }

            int shader = MinecraftReflectionCompat.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL20.glShaderSource(shader, processed);
            MinecraftReflectionCompat.glCompileShader(shader);
            if (MinecraftReflectionCompat.glGetShaderi(shader, MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL20.GL_COMPILE_STATUS, "field_153208_p", "GL_COMPILE_STATUS")) == 0) {
                String log = MinecraftReflectionCompat.glGetShaderInfoLog(shader, 32768);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to compile compute shader '{}': {}", source.path(), log);
                ShaderSourceDumper.dumpFailedSource(source.path(), processed);
                ShaderCompileNotifications.reportFailure(source.path());
                MinecraftReflectionCompat.glDeleteShader(shader);
                return null;
            }

            ShaderProgram program = new ShaderProgram(source.name());
            program.attachShader(shader);
            if (!program.link()) {
                program.delete();
                MinecraftReflectionCompat.glDeleteShader(shader);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to link compute program '{}'", source.name());
                ShaderCompileNotifications.reportFailure(source.path());
                return null;
            }

            MinecraftReflectionCompat.glDeleteShader(shader);
            int[] workGroups = parseWorkGroups(processed);
            float[] workGroupRelative = parseWorkGroupRelative(processed);
            if (workGroups == null && source.hasFixedWorkGroups()) {
                workGroups = source.workGroups();
            }
            if (workGroupRelative == null && source.hasRelativeWorkGroups()) {
                workGroupRelative = source.workGroupRelative();
            }
            MainMod.LOGGER.debug(
                    "[ShaderCompiler] Successfully compiled compute program: {} workGroups={} workGroupsRender={}",
                    source.name(),
                    workGroups == null ? "auto" : Arrays.toString(workGroups),
                    workGroupRelative == null ? "none" : Arrays.toString(workGroupRelative)
            );
            return new ComputeProgram(source, program, workGroups, workGroupRelative);
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderCompiler] Error reading compute shader '{}'", source.path(), e);
            ShaderCompileNotifications.reportFailure(source.path());
            return null;
        }
    }

    public void bind() {
        program.bind();
    }

    public ShaderProgram program() {
        return program;
    }

    public String name() {
        return source.name();
    }

    public int arrayIndex() {
        return Math.max(0, source.arrayIndex());
    }

    public boolean hasIndirectPointer() {
        return source.hasIndirectPointer();
    }

    public int indirectBuffer() {
        return source.indirectPointer() == null ? -1 : source.indirectPointer().buffer();
    }

    public long indirectOffset() {
        return source.indirectPointer() == null ? 0L : Math.max(0L, source.indirectPointer().offset());
    }

    public int[] workGroups(int renderWidth, int renderHeight) {
        if (workGroups != null) {
            int[] fixed = workGroups;
            return new int[]{Math.max(1, fixed[0]), Math.max(1, fixed[1]), Math.max(1, fixed[2])};
        }
        if (workGroupRelative != null) {
            float[] relative = workGroupRelative;
            return new int[]{
                    Math.max(1, (int) Math.ceil(renderWidth * relative[0])),
                    Math.max(1, (int) Math.ceil(renderHeight * relative[1])),
                    1
            };
        }
        return new int[]{1, 1, 1};
    }

    public void delete() {
        program.delete();
    }

    private static int[] parseWorkGroups(String source) {
        return ComputeDirectiveParser.parseWorkGroups(source, true, "[ShaderCompiler]", "compute workGroups");
    }

    private static float[] parseWorkGroupRelative(String source) {
        return ComputeDirectiveParser.parseWorkGroupRelative(source, true, "[ShaderCompiler]", "compute workGroupsRender");
    }
}
