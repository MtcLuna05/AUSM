package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.l.ausm.impl.pipeline.pack.ShaderTransformPipeline;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;

import java.io.IOException;

/**
 * Responsible for loading GLSL source, compiling shaders, and linking them into a ShaderProgram.
 */
public class ShaderCompiler {
    /**
     * Attempts to build a complete ShaderProgram for a given RenderPass.
     * Searches the ShaderPack for supported shader stages and links them into one program.
     * @return The linked program, or null if no required shaders were found/compiled.
     */
    public static ShaderProgram compilePass(ShaderPack pack, RenderPass pass) {
        return compilePass(pack, pass, ShaderProperties.load(pack));
    }

    public static ShaderProgram compilePass(ShaderPack pack, RenderPass pass, ShaderProperties properties) {
        return compilePass(pack, pass, properties, null);
    }

    public static ShaderProgram compilePass(ShaderPack pack, RenderPass pass, ShaderProperties properties, ShaderProgramSource source) {
        ShaderProgramSource sources = source != null ? source : sourceFromResolver(pack, pass, properties);
        return compileSource(pack, properties, sources, pass, pass.getProgramName(), properties.packDirectives());
    }

    public static ShaderProgram compilePass(
            ShaderPack pack,
            RenderPass pass,
            ShaderProperties properties,
            ShaderProgramSource source,
            com.l.ausm.impl.pipeline.pack.ShaderPackDirectives directives
    ) {
        ShaderProgramSource sources = source != null ? source : sourceFromResolver(pack, pass, properties);
        return compileSource(pack, properties, sources, pass, pass.getProgramName(), directives);
    }

    public static ShaderProgram compileSource(
            ShaderPack pack,
            ShaderProperties properties,
            ShaderProgramSource source,
            RenderPass bindingPass
    ) {
        return compileSource(pack, properties, source, bindingPass, source.name(), properties.packDirectives());
    }

    public static ShaderProgram compileSource(
            ShaderPack pack,
            ShaderProperties properties,
            ShaderProgramSource source,
            RenderPass bindingPass,
            com.l.ausm.impl.pipeline.pack.ShaderPackDirectives directives
    ) {
        return compileSource(pack, properties, source, bindingPass, source.name(), directives);
    }

    private static ShaderProgram compileSource(
            ShaderPack pack,
            ShaderProperties properties,
            ShaderProgramSource sources,
            RenderPass bindingPass,
            String programName,
            com.l.ausm.impl.pipeline.pack.ShaderPackDirectives directives
    ) {
        boolean hasFragmentSource = sources.fragmentPath() != null || sources.fragmentSource() != null;
        boolean generatedLegacyVertex = sources.vertexPath() == null && hasFragmentSource;
        int vertexShader = generatedLegacyVertex
                ? compileInlineShaderTarget(legacyVertexSource(), programName + " legacy vertex", com.l.ausm.impl.util.MinecraftReflectionCompat.glVertexShader(), bindingPass)
                : compileShaderTarget(pack, sources.vertexPath(), sources.vertexSource(), com.l.ausm.impl.util.MinecraftReflectionCompat.glVertexShader(), properties, bindingPass, programName + " vertex", directives);
        int tessellationControlShader = compileShaderTarget(
                pack,
                sources.tessellationControlPath(),
                sources.tessellationControlSource(),
                GL40.GL_TESS_CONTROL_SHADER,
                properties,
                bindingPass,
                programName + " tessellation control",
                directives
        );
        int tessellationEvaluationShader = compileShaderTarget(
                pack,
                sources.tessellationEvaluationPath(),
                sources.tessellationEvaluationSource(),
                GL40.GL_TESS_EVALUATION_SHADER,
                properties,
                bindingPass,
                programName + " tessellation evaluation",
                directives
        );
        int fragmentShader = compileShaderTarget(pack, sources.fragmentPath(), sources.fragmentSource(), com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER, "field_153210_r", "GL_FRAGMENT_SHADER"), properties, bindingPass, programName + " fragment", directives);
        
        // Geometry shaders use GL32, ensure compatibility
        int geometryShader = compileShaderTarget(pack, sources.geometryPath(), sources.geometrySource(), GL32.GL_GEOMETRY_SHADER, properties, bindingPass, programName + " geometry", directives);

        if ((generatedLegacyVertex && vertexShader == -1)
                || failedDeclaredStage(sources.vertexPath(), sources.vertexSource(), vertexShader)
                || failedDeclaredStage(sources.tessellationControlPath(), sources.tessellationControlSource(), tessellationControlShader)
                || failedDeclaredStage(sources.tessellationEvaluationPath(), sources.tessellationEvaluationSource(), tessellationEvaluationShader)
                || failedDeclaredStage(sources.fragmentPath(), sources.fragmentSource(), fragmentShader)
                || failedDeclaredStage(sources.geometryPath(), sources.geometrySource(), geometryShader)) {
            MainMod.LOGGER.error("[ShaderCompiler] Program '{}' disabled because at least one declared stage failed to compile.", programName);
            if (vertexShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(vertexShader);
            if (tessellationControlShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(tessellationControlShader);
            if (tessellationEvaluationShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(tessellationEvaluationShader);
            if (fragmentShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(fragmentShader);
            if (geometryShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(geometryShader);
            return null;
        }

        // If neither VSH nor FSH exists, this pass is effectively disabled in the pack
        if (vertexShader == -1 && fragmentShader == -1) {
            MainMod.LOGGER.debug("[ShaderCompiler] Pass '{}' disabled (no files found).", programName);
            return null;
        }

        ShaderProgram program = new ShaderProgram(programName);

        if (vertexShader != -1) program.attachShader(vertexShader);
        if (tessellationControlShader != -1) program.attachShader(tessellationControlShader);
        if (tessellationEvaluationShader != -1) program.attachShader(tessellationEvaluationShader);
        if (fragmentShader != -1) program.attachShader(fragmentShader);
        if (geometryShader != -1) program.attachShader(geometryShader);
        program.setTessellated(tessellationControlShader != -1 || tessellationEvaluationShader != -1);
        program.setGeometric(geometryShader != -1);

        if (!program.link()) {
            program.delete();
            MainMod.LOGGER.error("[ShaderCompiler] Failed to link program '{}'", programName);
            return null;
        }

        // Cleanup the individual shader objects now that they are linked into the program
        if (vertexShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(vertexShader);
        if (tessellationControlShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(tessellationControlShader);
        if (tessellationEvaluationShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(tessellationEvaluationShader);
        if (fragmentShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(fragmentShader);
        if (geometryShader != -1) com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(geometryShader);

        MainMod.LOGGER.debug("[ShaderCompiler] Successfully compiled and linked program: {}", programName);
        return program;
    }

    private static ShaderProgramSource sourceFromResolver(ShaderPack pack, RenderPass pass, ShaderProperties properties) {
        ProgramSourceSet paths = ProgramSourceResolver.resolve(pack, pass);
        return new ShaderProgramSource(
                pass.programId(),
                pass.getProgramName(),
                paths.vertexPath(),
                null,
                paths.tessellationControlPath(),
                null,
                paths.tessellationEvaluationPath(),
                null,
                paths.geometryPath(),
                null,
                paths.fragmentPath(),
                null,
                properties.directivesFor(pass)
        );
    }

    private static int compileShaderTarget(ShaderPack pack, String resourcePath, String inlineSource, int shaderType, ShaderProperties properties, RenderPass pass, String inlineName, com.l.ausm.impl.pipeline.pack.ShaderPackDirectives directives) {
        if (resourcePath == null) {
            if (inlineSource != null && !inlineSource.isBlank()) {
                return compileInlineShaderTarget(inlineSource, inlineName, shaderType, pass);
            }
            return -1;
        }
        if (!pack.hasResource(resourcePath)) {
            MainMod.LOGGER.debug("[ShaderCompiler] File not found in pack: {}", resourcePath);
            return -1;
        }

        try {
            String source = ShaderPreprocessor.processShaderSource(
                    pack,
                    resourcePath,
                    properties.options(),
                    pass,
                    shaderType,
                    null,
                    directives
            );
            if (source == null || source.isEmpty()) {
                MainMod.LOGGER.error("[ShaderCompiler] File was empty or null: {}", resourcePath);
                return -1;
            }
            source = ShaderTransformPipeline.transform(source, shaderType, pass);
            if (shouldDumpDebugSource(resourcePath)) {
                ShaderSourceDumper.dumpDebugSource(debugDumpName(resourcePath, shaderType), source);
            }
            int shaderId = com.l.ausm.impl.util.MinecraftReflectionCompat.glCreateShader(shaderType);
            
            GL20.glShaderSource(shaderId, source);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glCompileShader(shaderId);

            if (com.l.ausm.impl.util.MinecraftReflectionCompat.glGetShaderi(shaderId, com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS, "field_153208_p", "GL_COMPILE_STATUS")) == 0) {
                String log = com.l.ausm.impl.util.MinecraftReflectionCompat.glGetShaderInfoLog(shaderId, 32768);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to compile shader '{}': {}", resourcePath, log);
                ShaderSourceDumper.dumpFailedSource(resourcePath, source);
                ShaderCompileNotifications.reportFailure(resourcePath);
                com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(shaderId);
                return -1;
            }

            MainMod.LOGGER.debug("[ShaderCompiler] Compiled shader successfully: {}", resourcePath);
            return shaderId;
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderCompiler] Error reading shader file '{}'", resourcePath, e);
            ShaderCompileNotifications.reportFailure(resourcePath);
            return -1;
        }
    }

    private static boolean shouldDumpDebugSource(String resourcePath) {
        return resourcePath != null
                && (resourcePath.contains("gbuffers_skybasic")
                || resourcePath.contains("gbuffers_skytextured")
                || resourcePath.contains("gbuffers_hand")
                || resourcePath.contains("composite1")
                || resourcePath.contains("final"));
    }

    private static String debugDumpName(String resourcePath, int shaderType) {
        String suffix = "shader";
        if (shaderType == com.l.ausm.impl.util.MinecraftReflectionCompat.glVertexShader()) {
            suffix = "vertex";
        } else if (shaderType == com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER, "field_153210_r", "GL_FRAGMENT_SHADER")) {
            suffix = "fragment";
        } else if (shaderType == GL40.GL_TESS_CONTROL_SHADER) {
            suffix = "tesscontrol";
        } else if (shaderType == GL40.GL_TESS_EVALUATION_SHADER) {
            suffix = "tesseval";
        } else if (shaderType == GL32.GL_GEOMETRY_SHADER) {
            suffix = "geometry";
        }
        return resourcePath + "." + suffix;
    }

    private static boolean failedDeclaredStage(String path, String inlineSource, int shaderId) {
        return (path != null || (inlineSource != null && !inlineSource.isBlank())) && shaderId == -1;
    }

    private static int compileInlineShaderTarget(String source, String name, int shaderType, RenderPass pass) {
        String processed = ShaderTransformPipeline.transform(source, shaderType, pass);
        int shaderId = com.l.ausm.impl.util.MinecraftReflectionCompat.glCreateShader(shaderType);

        GL20.glShaderSource(shaderId, processed);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glCompileShader(shaderId);

        if (com.l.ausm.impl.util.MinecraftReflectionCompat.glGetShaderi(shaderId, com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL20.GL_COMPILE_STATUS, "field_153208_p", "GL_COMPILE_STATUS")) == 0) {
            String log = com.l.ausm.impl.util.MinecraftReflectionCompat.glGetShaderInfoLog(shaderId, 32768);
            MainMod.LOGGER.error("[ShaderCompiler] Failed to compile inline shader '{}': {}", name, log);
            ShaderSourceDumper.dumpFailedSource(name, processed);
            ShaderCompileNotifications.reportFailure(name);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteShader(shaderId);
            return -1;
        }

        MainMod.LOGGER.debug("[ShaderCompiler] Compiled inline shader successfully: {}", name);
        return shaderId;
    }

    private static String legacyVertexSource() {
        return """
                #version 120

                varying vec4 irs_texCoords[3];
                varying vec4 irs_Color;

                void main() {
                    gl_Position = ftransform();
                    irs_texCoords[0] = gl_TextureMatrix[0] * gl_MultiTexCoord0;
                    irs_texCoords[1] = gl_TextureMatrix[1] * gl_MultiTexCoord1;
                    irs_texCoords[2] = gl_TextureMatrix[1] * gl_MultiTexCoord2;
                    irs_Color = gl_Color;
                }
                """;
    }
}
