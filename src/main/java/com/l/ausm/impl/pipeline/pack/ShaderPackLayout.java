package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.ArrayList;
import java.util.List;

public final class ShaderPackLayout {

    private final String shaderRoot;
    private final String propertiesPath;

    private ShaderPackLayout(String shaderRoot, String propertiesPath) {
        this.shaderRoot = shaderRoot;
        this.propertiesPath = propertiesPath;
    }

    public static ShaderPackLayout detect(ShaderPack pack) {
        if (pack.hasResource("shaders/shaders.properties") || pack.hasResource("shaders/final.fsh")) {
            return new ShaderPackLayout("shaders/", "shaders/shaders.properties");
        }
        return new ShaderPackLayout("", "shaders.properties");
    }

    public String propertiesPath() {
        return propertiesPath;
    }

    public String langPath(String fileName) {
        return shaderRoot + "lang/" + fileName;
    }

    public String rootPath(String path) {
        return shaderRoot + path;
    }

    public String programBase(RenderPass pass) {
        return programBase(pass.programId());
    }

    public String dimensionProgramBase(int dimensionId, RenderPass pass) {
        return dimensionProgramBase(dimensionId, pass.programId());
    }

    public String programBase(ProgramId programId) {
        return shaderRoot + programId.sourceName();
    }

    public List<String> programBaseAliases(ProgramId programId) {
        List<String> aliases = new ArrayList<>(2);
        aliases.add(programBase(programId));
        String compactName = compactIndexedName(programId);
        if (compactName != null) {
            aliases.add(shaderRoot + compactName);
        }
        return List.copyOf(aliases);
    }

    public String dimensionProgramBase(int dimensionId, ProgramId programId) {
        return shaderRoot + "world" + dimensionId + "/" + programId.sourceName();
    }

    public List<String> dimensionProgramBaseAliases(int dimensionId, ProgramId programId) {
        List<String> aliases = new ArrayList<>(2);
        aliases.add(dimensionProgramBase(dimensionId, programId));
        String compactName = compactIndexedName(programId);
        if (compactName != null) {
            aliases.add(shaderRoot + "world" + dimensionId + "/" + compactName);
        }
        return List.copyOf(aliases);
    }

    public List<String> programBases(RenderPass pass) {
        return programBases(pass.programId());
    }

    public List<String> programBases(ProgramId programId) {
        List<String> bases = new ArrayList<>(5);
        bases.addAll(programBaseAliases(programId));
        bases.addAll(dimensionProgramBaseAliases(-1, programId));
        bases.addAll(dimensionProgramBaseAliases(0, programId));
        bases.addAll(dimensionProgramBaseAliases(1, programId));
        return List.copyOf(bases);
    }

    private static String compactIndexedName(ProgramId programId) {
        String sourceName = programId.sourceName();
        int underscore = sourceName.lastIndexOf('_');
        if (underscore < 0 || underscore >= sourceName.length() - 1) {
            return null;
        }

        for (int i = underscore + 1; i < sourceName.length(); i++) {
            if (!Character.isDigit(sourceName.charAt(i))) {
                return null;
            }
        }
        return sourceName.substring(0, underscore) + sourceName.substring(underscore + 1);
    }

    public String normalizeTexturePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        String normalized = path.trim();
        if (normalized.startsWith("/")) {
            return shaderRoot + normalized.substring(1);
        }
        return shaderRoot + normalized;
    }
}
