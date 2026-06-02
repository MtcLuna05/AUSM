package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a compiled and linked OpenGL Shader Program.
 */
public class ShaderProgram {

    private final String name;
    private int programId;
    
    // Caches uniform locations to avoid costly GL lookups every frame
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public ShaderProgram(String name) {
        this.name = name;
        this.programId = OpenGlHelper.glCreateProgram();
    }

    public void attachShader(int shaderId) {
        OpenGlHelper.glAttachShader(programId, shaderId);
    }

    public boolean link() {
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, "mc_Entity");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, "iris_Entity");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, "mc_midTexCoord");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, "at_tangent");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, "at_midBlock");
        GL20.glBindAttribLocation(programId, 0, "Position");
        GL20.glBindAttribLocation(programId, 1, "UV0");
        OpenGlHelper.glLinkProgram(programId);
        
        if (OpenGlHelper.glGetProgrami(programId, OpenGlHelper.GL_LINK_STATUS) == 0) {
            String log = OpenGlHelper.glGetProgramInfoLog(programId, 32768);
            MainMod.LOGGER.error("Failed to link shader program '{}': {}", name, log);
            return false;
        }
        return true;
    }

    public void bind() {
        OpenGlHelper.glUseProgram(programId);
    }

    public void unbind() {
        OpenGlHelper.glUseProgram(0);
    }

    public void delete() {
        if (programId != -1) {
            OpenGlHelper.glDeleteProgram(programId);
            programId = -1;
        }
    }

    public int getUniformLocation(String uniformName) {
        return uniformLocations.computeIfAbsent(uniformName, key -> {
            int loc = OpenGlHelper.glGetUniformLocation(programId, key);
            if (loc == -1) {
                MainMod.LOGGER.debug("Uniform '{}' not found in program '{}'", key, name);
            }
            return loc;
        });
    }

    public String getName() {
        return name;
    }
    
    public int getId() {
        return programId;
    }
}
