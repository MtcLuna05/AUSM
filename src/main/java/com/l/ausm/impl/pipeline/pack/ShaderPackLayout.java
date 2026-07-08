package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class ShaderPackLayout {

    private final String shaderRoot;
    private final String propertiesPath;
    private final Map<Integer, List<String>> dimensionFolders;
    private final List<String> wildcardDimensionFolders;

    private ShaderPackLayout(
            String shaderRoot,
            String propertiesPath,
            Map<Integer, List<String>> dimensionFolders,
            List<String> wildcardDimensionFolders
    ) {
        this.shaderRoot = shaderRoot;
        this.propertiesPath = propertiesPath;
        this.dimensionFolders = Map.copyOf(dimensionFolders);
        this.wildcardDimensionFolders = List.copyOf(wildcardDimensionFolders);
    }

    public static ShaderPackLayout detect(ShaderPack pack) {
        String shaderRoot;
        String propertiesPath;
        if (pack.hasResource("shaders/shaders.properties") || pack.hasResource("shaders/final.fsh")) {
            shaderRoot = "shaders/";
            propertiesPath = "shaders/shaders.properties";
        } else {
            shaderRoot = "";
            propertiesPath = "shaders.properties";
        }
        DimensionAliases aliases = loadDimensionAliases(pack, shaderRoot);
        return new ShaderPackLayout(shaderRoot, propertiesPath, aliases.byId(), aliases.wildcardFolders());
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
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(programBase(programId));
        aliases.add(shaderRoot + "program/" + programId.sourceName());
        String compactName = compactIndexedName(programId);
        if (compactName != null) {
            aliases.add(shaderRoot + compactName);
            aliases.add(shaderRoot + "program/" + compactName);
        }
        return List.copyOf(aliases);
    }

    public String dimensionProgramBase(int dimensionId, ProgramId programId) {
        return shaderRoot + "world" + dimensionId + "/" + programId.sourceName();
    }

    public List<String> dimensionProgramBaseAliases(int dimensionId, ProgramId programId) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String folder : dimensionFolderAliases(dimensionId)) {
            aliases.add(shaderRoot + folder + "/" + programId.sourceName());
            String compactName = compactIndexedName(programId);
            if (compactName != null) {
                aliases.add(shaderRoot + folder + "/" + compactName);
            }
        }
        aliases.add(dimensionProgramBase(dimensionId, programId));
        String compactName = compactIndexedName(programId);
        if (compactName != null) {
            aliases.add(shaderRoot + "world" + dimensionId + "/" + compactName);
        }
        return List.copyOf(aliases);
    }

    public List<String> dimensionSourceBaseAliases(int dimensionId, String sourceName) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        for (String folder : dimensionFolderAliases(dimensionId)) {
            aliases.add(shaderRoot + folder + "/" + sourceName);
        }
        aliases.add(shaderRoot + "world" + dimensionId + "/" + sourceName);
        return List.copyOf(aliases);
    }

    public List<String> programBases(RenderPass pass) {
        return programBases(pass.programId());
    }

    public List<String> programBases(ProgramId programId) {
        LinkedHashSet<String> bases = new LinkedHashSet<>();
        bases.addAll(programBaseAliases(programId));
        for (int dimensionId : allKnownDimensionIds()) {
            bases.addAll(dimensionProgramBaseAliases(dimensionId, programId));
        }
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

    private List<String> dimensionFolderAliases(int dimensionId) {
        LinkedHashSet<String> folders = new LinkedHashSet<>();
        folders.addAll(dimensionFolders.getOrDefault(dimensionId, List.of()));
        folders.addAll(wildcardDimensionFolders);
        return List.copyOf(folders);
    }

    private List<Integer> allKnownDimensionIds() {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        ids.add(-1);
        ids.add(0);
        ids.add(1);
        ids.addAll(dimensionFolders.keySet());
        return List.copyOf(ids);
    }

    private static DimensionAliases loadDimensionAliases(ShaderPack pack, String shaderRoot) {
        String path = shaderRoot + "dimension.properties";
        if (!pack.hasResource(path)) {
            return DimensionAliases.empty();
        }

        Properties properties = new Properties();
        try (InputStream stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return DimensionAliases.empty();
            }
            properties.load(stream);
        } catch (IOException ignored) {
            return DimensionAliases.empty();
        }

        Map<Integer, LinkedHashSet<String>> byId = new HashMap<>();
        LinkedHashSet<String> wildcard = new LinkedHashSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith("dimension.")) {
                continue;
            }
            String folder = key.substring("dimension.".length()).trim();
            if (folder.isEmpty()) {
                continue;
            }
            String value = properties.getProperty(key, "");
            for (String token : value.trim().split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                Integer dimensionId = dimensionIdForToken(token);
                if (dimensionId == null) {
                    if ("*".equals(token)) {
                        wildcard.add(folder);
                    }
                    continue;
                }
                byId.computeIfAbsent(dimensionId, ignored -> new LinkedHashSet<>()).add(folder);
            }
        }

        Map<Integer, List<String>> immutable = new HashMap<>();
        byId.forEach((dimensionId, folders) -> immutable.put(dimensionId, List.copyOf(folders)));
        return new DimensionAliases(Map.copyOf(immutable), List.copyOf(wildcard));
    }

    private static Integer dimensionIdForToken(String token) {
        String normalized = token.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("world") && normalized.length() > "world".length()) {
            normalized = normalized.substring("world".length());
        }
        return switch (normalized) {
            case "minecraft:overworld", "overworld" -> 0;
            case "minecraft:the_nether", "the_nether", "nether" -> -1;
            case "minecraft:the_end", "the_end", "end" -> 1;
            default -> {
                try {
                    yield Integer.parseInt(normalized);
                } catch (NumberFormatException ignored) {
                    yield null;
                }
            }
        };
    }

    private record DimensionAliases(Map<Integer, List<String>> byId, List<String> wildcardFolders) {
        private static DimensionAliases empty() {
            return new DimensionAliases(Map.of(), List.of());
        }
    }
}
