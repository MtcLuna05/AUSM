package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.l.ausm.impl.pipeline.pack.ShaderTransformPipeline;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import java.io.IOException;

/**
 * Responsible for loading GLSL source, compiling shaders, and linking them into a ShaderProgram.
 */
public class ShaderCompiler {
    /**
     * Attempts to build a complete ShaderProgram for a given RenderPass.
     * Searches the ShaderPack for .vsh (vertex), .gsh (geometry), and .fsh (fragment) files.
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

        int vertexShader = sources.vertexPath() == null && sources.fragmentPath() != null
                ? compileInlineShaderTarget(legacyVertexSource(), pass.getProgramName() + " legacy vertex", OpenGlHelper.GL_VERTEX_SHADER, pass)
                : compileShaderTarget(pack, sources.vertexPath(), OpenGlHelper.GL_VERTEX_SHADER, properties, pass);
        int fragmentShader = compileShaderTarget(pack, sources.fragmentPath(), OpenGlHelper.GL_FRAGMENT_SHADER, properties, pass);
        
        // Geometry shaders use GL32, ensure compatibility
        int geometryShader = compileShaderTarget(pack, sources.geometryPath(), GL32.GL_GEOMETRY_SHADER, properties, pass);

        if (failedExistingStage(sources.vertexPath(), vertexShader)
                || failedExistingStage(sources.fragmentPath(), fragmentShader)
                || failedExistingStage(sources.geometryPath(), geometryShader)) {
            MainMod.LOGGER.error("[ShaderCompiler] Program '{}' disabled because at least one declared stage failed to compile.", pass.getProgramName());
            if (vertexShader != -1) OpenGlHelper.glDeleteShader(vertexShader);
            if (fragmentShader != -1) OpenGlHelper.glDeleteShader(fragmentShader);
            if (geometryShader != -1) OpenGlHelper.glDeleteShader(geometryShader);
            return null;
        }

        // If neither VSH nor FSH exists, this pass is effectively disabled in the pack
        if (vertexShader == -1 && fragmentShader == -1) {
            MainMod.LOGGER.debug("[ShaderCompiler] Pass '{}' disabled (no files found).", pass.getProgramName());
            return null;
        }

        ShaderProgram program = new ShaderProgram(pass.getProgramName());

        if (vertexShader != -1) program.attachShader(vertexShader);
        if (fragmentShader != -1) program.attachShader(fragmentShader);
        if (geometryShader != -1) program.attachShader(geometryShader);

        if (!program.link()) {
            program.delete();
            MainMod.LOGGER.error("[ShaderCompiler] Failed to link program '{}'", pass.getProgramName());
            return null;
        }

        // Cleanup the individual shader objects now that they are linked into the program
        if (vertexShader != -1) OpenGlHelper.glDeleteShader(vertexShader);
        if (fragmentShader != -1) OpenGlHelper.glDeleteShader(fragmentShader);
        if (geometryShader != -1) OpenGlHelper.glDeleteShader(geometryShader);

        MainMod.LOGGER.debug("[ShaderCompiler] Successfully compiled and linked program: {}", pass.getProgramName());
        return program;
    }

    private static ShaderProgramSource sourceFromResolver(ShaderPack pack, RenderPass pass, ShaderProperties properties) {
        ProgramSourceSet paths = ProgramSourceResolver.resolve(pack, pass);
        return new ShaderProgramSource(
                pass.programId(),
                pass.getProgramName(),
                paths.vertexPath(),
                null,
                paths.geometryPath(),
                null,
                paths.fragmentPath(),
                null,
                properties.directivesFor(pass)
        );
    }

    private static int compileShaderTarget(ShaderPack pack, String resourcePath, int shaderType, ShaderProperties properties, RenderPass pass) {
        if (resourcePath == null) {
            return -1;
        }
        if (!pack.hasResource(resourcePath)) {
            MainMod.LOGGER.debug("[ShaderCompiler] File not found in pack: {}", resourcePath);
            return -1;
        }

        try {
            String source = ShaderPreprocessor.processShaderSource(pack, resourcePath, properties.options(), pass, shaderType);
            if (source == null || source.isEmpty()) {
                MainMod.LOGGER.error("[ShaderCompiler] File was empty or null: {}", resourcePath);
                return -1;
            }

            int shaderId = OpenGlHelper.glCreateShader(shaderType);
            
            GL20.glShaderSource(shaderId, source);
            OpenGlHelper.glCompileShader(shaderId);

            if (OpenGlHelper.glGetShaderi(shaderId, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
                String log = OpenGlHelper.glGetShaderInfoLog(shaderId, 32768);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to compile shader '{}': {}", resourcePath, log);
                OpenGlHelper.glDeleteShader(shaderId);
                return -1;
            }

            MainMod.LOGGER.debug("[ShaderCompiler] Compiled shader successfully: {}", resourcePath);
            return shaderId;
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderCompiler] Error reading shader file '{}'", resourcePath, e);
            return -1;
        }
    }

    private static boolean failedExistingStage(String path, int shaderId) {
        return path != null && shaderId == -1;
    }

    private static int compileInlineShaderTarget(String source, String name, int shaderType, RenderPass pass) {
        String processed = ShaderTransformPipeline.transform(source, shaderType, pass);
        int shaderId = OpenGlHelper.glCreateShader(shaderType);

        GL20.glShaderSource(shaderId, processed);
        OpenGlHelper.glCompileShader(shaderId);

        if (OpenGlHelper.glGetShaderi(shaderId, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
            String log = OpenGlHelper.glGetShaderInfoLog(shaderId, 32768);
            MainMod.LOGGER.error("[ShaderCompiler] Failed to compile inline shader '{}': {}", name, log);
            OpenGlHelper.glDeleteShader(shaderId);
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
