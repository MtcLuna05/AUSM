package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.RenderPass;
import net.minecraft.client.renderer.OpenGlHelper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ShaderTransformParameters(
        int shaderType,
        RenderPass pass,
        int glslVersion
) {
    private static final Pattern GLSL_VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)\\b");

    public static ShaderTransformParameters fromSource(String source, int shaderType, RenderPass pass) {
        return new ShaderTransformParameters(shaderType, pass, parseGlslVersion(source));
    }

    public boolean fragmentShader() {
        return shaderType == com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER, "field_153210_r", "GL_FRAGMENT_SHADER");
    }

    public boolean vertexShader() {
        return shaderType == com.l.ausm.impl.util.MinecraftReflectionCompat.glVertexShader();
    }

    public boolean compatibilityProfile() {
        return glslVersion < 130;
    }

    private static int parseGlslVersion(String source) {
        Matcher matcher = GLSL_VERSION.matcher(source);
        if (!matcher.find()) {
            return 120;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 120;
        }
    }
}
