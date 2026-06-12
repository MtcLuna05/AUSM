package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderPreprocessor {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*(?://\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\b.*$");
    private static final Pattern CONST_PATTERN = Pattern.compile("^(\\s*const\\s+\\w+\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*=\\s*)([^;]+)(;.*)$");

    /**
     * Main entry point for the compiler.
     * Extracts #version, injects OptiFine defines, and resolves all #includes.
     */
    public static String processShaderSource(ShaderPack pack, String resourcePath) throws IOException {
        return processShaderSource(pack, resourcePath, ShaderOptions.empty());
    }

    public static String processShaderSource(ShaderPack pack, String resourcePath, ShaderOptions options) throws IOException {
        return processShaderSource(pack, resourcePath, options, null);
    }

    public static String processShaderSource(ShaderPack pack, String resourcePath, ShaderOptions options, RenderPass pass) throws IOException {
        return processShaderSource(pack, resourcePath, options, pass, -1);
    }

    public static String processShaderSource(ShaderPack pack, String resourcePath, ShaderOptions options, RenderPass pass, int shaderType) throws IOException {
        List<String> rawLines = new ArrayList<>();
        Set<String> visitedFiles = new HashSet<>();

        // 1. Recursively read all files and resolve includes
        resolveIncludes(pack, resourcePath, rawLines, visitedFiles);

        if (rawLines.isEmpty()) return null;

        String versionLine = "#version 120"; // OptiFine default fallback
        Set<String> extensionLines = new LinkedHashSet<>();
        // 2. Extract the #version line so we can force it to the very top
        for (String line : rawLines) {
            if (line.trim().startsWith("#version ")) {
                versionLine = line.trim();
            } else if (line.trim().startsWith("#extension ")) {
                extensionLines.add(line.trim());
            }
        }
        if (parseGlslVersion(versionLine) < 130 && rawLines.stream().anyMatch(line -> line.contains("gl_VertexID"))) {
            versionLine = "#version 130";
        }
        int glslVersion = parseGlslVersion(versionLine);

        StringBuilder finalSource = new StringBuilder();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#version ") || trimmed.startsWith("#extension ")) {
                continue;
            }

            String processed = applyOptionOverride(line, options);
            processed = normalizeUnsupportedPreprocessor(processed);
            finalSource.append(normalizeLegacyGlsl(processed, glslVersion)).append("\n");
        }

        // 3. Construct the final valid GLSL string
        StringBuilder out = new StringBuilder();
        out.append(versionLine).append("\n");
        for (String extensionLine : extensionLines) {
            out.append(extensionLine).append("\n");
        }
        out.append(getOptiFineEnvironmentDefines()).append("\n");
        out.append(getRenderStageDefines()).append("\n");
        out.append(getProgramDefines(pass)).append("\n");
        out.append(getOptiFineMetadataDefines()).append("\n");
        out.append(finalSource);

        String source = out.toString();
        return ShaderTransformPipeline.transform(source, shaderType, pass);
    }

    private static String getProgramDefines(RenderPass pass) {
        if (pass == null) {
            return "";
        }

        StringBuilder defines = new StringBuilder();
        appendProgramDefine(defines, pass);

        if (isTerrainAlias(pass)) {
            appendProgramDefine(defines, RenderPass.GBUFFERS_TERRAIN);
        }
        return defines.toString();
    }

    private static String getRenderStageDefines() {
        StringBuilder defines = new StringBuilder();
        for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
            defines.append("#define MC_RENDER_STAGE_")
                    .append(phase.name())
                    .append(' ')
                    .append(phase.ordinal())
                    .append('\n');
        }
        return defines.toString();
    }

    private static void appendProgramDefine(StringBuilder defines, RenderPass pass) {
        String programDefine = pass.getProgramName().toUpperCase(Locale.ROOT);
        defines.append("#define ")
                .append(programDefine)
                .append('\n');
        if (programDefine.startsWith("GBUFFERS_")) {
            defines.append("#define GBUFFER_")
                    .append(programDefine.substring("GBUFFERS_".length()))
                    .append('\n');
            String optiFineAlias = optiFineGbufferAlias(programDefine);
            if (optiFineAlias != null) {
                defines.append("#define ")
                        .append(optiFineAlias)
                        .append('\n');
            }
        }
    }

    private static String optiFineGbufferAlias(String programDefine) {
        return switch (programDefine) {
            case "GBUFFERS_DAMAGEDBLOCK" -> "GBUFFER_DAMAGE";
            case "GBUFFERS_ENTITIES_GLOWING" -> "GBUFFER_ENTITY_GLOW";
            default -> null;
        };
    }

    private static boolean isTerrainAlias(RenderPass pass) {
        return switch (pass) {
            case GBUFFERS_TERRAIN_SOLID, GBUFFERS_TERRAIN_CUTOUT_MIP, GBUFFERS_TERRAIN_CUTOUT,
                    GBUFFERS_DAMAGEDBLOCK, GBUFFERS_BLOCK -> true;
            default -> false;
        };
    }

    private static String getOptiFineMetadataDefines() {
        return """
                #define R8 0
                #define RG8 0
                #define RGB8 0
                #define RGBA8 0
                #define R16 0
                #define RG16 0
                #define RGB16 0
                #define RGBA16 0
                #define R16F 0
                #define RG16F 0
                #define RGB16F 0
                #define RGBA16F 0
                #define R32F 0
                #define RG32F 0
                #define RGB32F 0
                #define RGBA32F 0
                #define RGB10_A2 0
                #define R11F_G11F_B10F 0
                """;
    }

    private static String applyOptionOverride(String line, ShaderOptions options) {
        Matcher matcher = DEFINE_PATTERN.matcher(line);
        if (matcher.matches()) {
            ShaderOption option = options.get(matcher.group(1));
            if (option == null || !option.changed()) {
                return line;
            }

            if (option.toggle() && !option.asBoolean()) {
                return "// AUSM option disabled: #define " + option.name();
            }

            if (option.toggle()) {
                return "#define " + option.name();
            }

            return "#define " + option.name() + " " + option.value();
        }

        matcher = CONST_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return line;
        }

        ShaderOption option = options.get(matcher.group(2));
        if (option == null || !option.changed()) {
            return line;
        }

        return matcher.group(1) + option.name() + matcher.group(3) + option.value() + matcher.group(5);
    }

    private static int parseGlslVersion(String versionLine) {
        String[] parts = versionLine.trim().split("\\s+");
        if (parts.length < 2) {
            return 120;
        }

        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return 120;
        }
    }

    private static String normalizeLegacyGlsl(String line, int glslVersion) {
        if (glslVersion >= 130) {
            return line;
        }

        return line.replaceFirst("^(\\s*)flat\\s+varying\\b", "$1varying");
    }

    private static String normalizeUnsupportedPreprocessor(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#if") && trimmed.contains("in(")) {
            return line.replaceFirst("#if\\b.*", "#if 0 // AUSM: disabled unsupported OptiFine biome preprocessor expression");
        }
        return line;
    }

    /**
     * Recursively reads files and processes #include directives.
     * Uses visitedFiles to prevent infinite loops (Circular Dependencies).
     */
    private static void resolveIncludes(ShaderPack pack, String currentFile, List<String> outputLines, Set<String> visitedFiles) throws IOException {
        if (visitedFiles.contains(currentFile)) {
            return;
        }
        visitedFiles.add(currentFile);

        try {
            InputStream is = pack.getResourceAsStream(currentFile);
            if (is == null) {
                outputLines.add("// ERROR: Include not found: " + currentFile);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("#include ")) {
                        String includePath = extractIncludePath(line.trim(), currentFile);
                        if (includePath != null) {
                            resolveIncludes(pack, includePath, outputLines, visitedFiles);
                        }
                    } else {
                        outputLines.add(line);
                    }
                }
            }
        } finally {
            visitedFiles.remove(currentFile);
        }
    }

    /**
     * Generates the standard OptiFine hidden variables that advanced packs like BSL require.
     */
    private static String getOptiFineEnvironmentDefines() {
        StringBuilder defines = new StringBuilder("""
                #define MC_VERSION 11202
                #define MC_GL_VERSION 320
                #define MC_GLSL_VERSION 120
                #define MC_RENDER_QUALITY 1.0
                #define MC_SHADOW_QUALITY 1.0
                #define MC_HAND_DEPTH 1.0
                #define IS_IRIS
                #define IRIS_VERSION 10800
                #define IRIS_FEATURE_CUSTOM_IMAGES
                #define IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE
                """);

        switch (ShaderDimensionContext.currentDimensionId()) {
            case -1 -> defines.append("#define NETHER\n");
            case 1 -> defines.append("#define THE_END\n");
            default -> defines.append("#define OVERWORLD\n");
        }

        return defines.toString();
    }

    static String extractIncludePath(String includeLine, String currentFile) {
        int firstQuote = includeLine.indexOf('"');
        int lastQuote = includeLine.lastIndexOf('"');
        if (firstQuote != -1 && lastQuote != -1 && firstQuote < lastQuote) {
            String path = includeLine.substring(firstQuote + 1, lastQuote);
            if (path.startsWith("/")) {
                // Absolute includes are relative to the active shader root. Some packs place
                // shader files at pack root instead of under shaders/.
                return currentFile.startsWith("shaders/") ? "shaders" + path : path.substring(1);
            } else {
                // Relative path
                int lastSlash = currentFile.lastIndexOf('/');
                if (lastSlash != -1) {
                    return currentFile.substring(0, lastSlash + 1) + path;
                }
                return path;
            }
        }
        return null;
    }
}
