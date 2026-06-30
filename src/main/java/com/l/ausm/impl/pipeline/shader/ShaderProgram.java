package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a compiled and linked OpenGL Shader Program.
 */
public class ShaderProgram {

    private final String name;
    private int programId;
    
    // Caches uniform locations to avoid costly GL lookups every frame
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private final Set<String> activeUniformNames = new HashSet<>();
    private boolean activeUniformNamesAvailable;
    private boolean tessellated;
    private boolean geometric;

    public ShaderProgram(String name) {
        this.name = name;
        this.programId = OpenGlHelper.glCreateProgram();
    }

    public void attachShader(int shaderId) {
        OpenGlHelper.glAttachShader(programId, shaderId);
    }

    public void setTessellated(boolean tessellated) {
        this.tessellated = tessellated;
    }

    public boolean isTessellated() {
        return tessellated;
    }

    public void setGeometric(boolean geometric) {
        this.geometric = geometric;
    }

    public boolean isGeometric() {
        return geometric;
    }

    public boolean link() {
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, "mc_Entity");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, "iris_Entity");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, "mc_midTexCoord");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, "at_tangent");
        GL20.glBindAttribLocation(programId, ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, "at_midBlock");
        GL20.glBindAttribLocation(programId, 0, "Position");
        GL20.glBindAttribLocation(programId, 1, "UV0");
        GL20.glBindAttribLocation(programId, 0, "vPosition");
        GL20.glBindAttribLocation(programId, 1, "color");
        GL20.glBindAttribLocation(programId, 1, "vColor");
        GL20.glBindAttribLocation(programId, 2, "dhMaterialData");
        GL20.glBindAttribLocation(programId, 2, "irisMaterialData");
        OpenGlHelper.glLinkProgram(programId);
        
        if (OpenGlHelper.glGetProgrami(programId, OpenGlHelper.GL_LINK_STATUS) == 0) {
            String log = OpenGlHelper.glGetProgramInfoLog(programId, 32768);
            MainMod.LOGGER.error("Failed to link shader program '{}': {}", name, log);
            return false;
        }
        scanActiveUniformNames();
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
        uniformLocations.clear();
        activeUniformNames.clear();
        activeUniformNamesAvailable = false;
    }

    public int getUniformLocation(String uniformName) {
        if (programId == -1 || uniformName == null || uniformName.isEmpty()) {
            return -1;
        }

        Integer cached = uniformLocations.get(uniformName);
        if (cached != null) {
            return cached;
        }

        if (activeUniformNamesAvailable && !couldBeActiveUniform(uniformName)) {
            uniformLocations.put(uniformName, -1);
            return -1;
        }

        int loc = OpenGlHelper.glGetUniformLocation(programId, uniformName);
        uniformLocations.put(uniformName, loc);
        return loc;
    }

    public String getName() {
        return name;
    }
    
    public int getId() {
        return programId;
    }

    private void scanActiveUniformNames() {
        uniformLocations.clear();
        activeUniformNames.clear();
        activeUniformNamesAvailable = false;

        try {
            int count = GL20.glGetProgrami(programId, GL20.GL_ACTIVE_UNIFORMS);
            int maxLength = Math.max(1, GL20.glGetProgrami(programId, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH));
            for (int i = 0; i < count; i++) {
                addActiveUniformName(GL20.glGetActiveUniform(programId, i, maxLength));
            }
            activeUniformNamesAvailable = true;
            MainMod.LOGGER.debug("[ShaderProgram] Program '{}' has {} active uniforms", name, activeUniformNames.size());
        } catch (RuntimeException e) {
            MainMod.LOGGER.debug(
                    "[ShaderProgram] Failed to scan active uniforms for '{}'; falling back to lazy uniform queries",
                    name,
                    e
            );
        }
    }

    private void addActiveUniformName(String activeName) {
        String normalized = normalizeActiveUniformName(activeName);
        if (normalized.isEmpty()) {
            return;
        }
        activeUniformNames.add(normalized);
        if (!normalized.equals(activeName)) {
            activeUniformNames.add(activeName);
        }
    }

    private boolean couldBeActiveUniform(String uniformName) {
        if (activeUniformNames.contains(uniformName)) {
            return true;
        }

        String normalized = normalizeActiveUniformName(uniformName);
        if (activeUniformNames.contains(normalized)) {
            return true;
        }

        int arrayIndex = uniformName.indexOf('[');
        return arrayIndex > 0 && activeUniformNames.contains(uniformName.substring(0, arrayIndex));
    }

    static String normalizeActiveUniformName(String uniformName) {
        if (uniformName == null) {
            return "";
        }

        int nullTerminator = uniformName.indexOf('\0');
        String normalized = nullTerminator >= 0 ? uniformName.substring(0, nullTerminator) : uniformName;
        normalized = normalized.trim();
        if (normalized.endsWith("[0]")) {
            return normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
}
