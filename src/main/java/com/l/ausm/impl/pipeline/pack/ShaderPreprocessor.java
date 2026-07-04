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
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

public class ShaderPreprocessor {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*(?://\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\b.*$");
    private static final Pattern CONST_PATTERN = Pattern.compile("^(\\s*const\\s+\\w+\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*=\\s*)([^;]+)(;.*)$");
    private static final Pattern CONDITION_DIRECTIVE_PATTERN = Pattern.compile("^(\\s*#(?:if|elif)\\s+)(.*)$");
    private static final Pattern INFIX_IN_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_\\.]*|[-+]?\\d+(?:\\.\\d+)?)\\s+in\\s*\\(([^()]*)\\)");
    private static final Pattern FUNCTION_IN_PATTERN = Pattern.compile("\\bin\\s*\\(([^()]*)\\)");
    private static final String DISTANT_LIGHT_BOKEH_FUNCTION = "DoDistantLightBokehMaterial";
    private static final String DISTANT_LIGHT_BOKEH_FALLBACK = """
            #ifndef AUSM_DISTANT_LIGHT_BOKEH_FALLBACK
            #define AUSM_DISTANT_LIGHT_BOKEH_FALLBACK
            void DoDistantLightBokehMaterial(inout vec4 color, vec4 distantColor, inout float emission, float distantEmission, float lViewPos) {
                float dlbMix = clamp(0.005 * (lViewPos - 60.0), 0.0, 1.0);
                color = mix(color, distantColor, dlbMix);
                emission = mix(emission, distantEmission, dlbMix);
            }
            void DoDistantLightBokehMaterial(inout float emission, float distantEmission, float lViewPos) {
                float dlbMix = clamp(0.005 * (lViewPos - 60.0), 0.0, 1.0);
                emission = mix(emission, distantEmission, dlbMix);
            }
            #endif
            """;

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
        return processShaderSource(pack, resourcePath, options, pass, shaderType, null);
    }

    public static String processShaderSource(
            ShaderPack pack,
            String resourcePath,
            ShaderOptions options,
            RenderPass pass,
            int shaderType,
            String programName
    ) throws IOException {
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
        versionLine = ensureStageMinimumVersion(versionLine, shaderType);
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
        out.append(getOptiFineEnvironmentDefines(options, glslVersion)).append("\n");
        out.append(getRenderStageDefines()).append("\n");
        out.append(getProgramDefines(pass, programName)).append("\n");
        out.append(getOptiFineMetadataDefines()).append("\n");
        out.append(finalSource);

        String source = addMissingDistantLightBokehFallback(out.toString());
        return ShaderTransformPipeline.transform(source, shaderType, pass);
    }

    private static String addMissingDistantLightBokehFallback(String source) {
        if (!source.contains(DISTANT_LIGHT_BOKEH_FUNCTION + "(")
                || source.contains("void " + DISTANT_LIGHT_BOKEH_FUNCTION)) {
            return source;
        }

        int insert = source.indexOf("//Program//");
        if (insert < 0) {
            insert = source.indexOf("void main");
        }
        if (insert < 0) {
            return source + "\n" + DISTANT_LIGHT_BOKEH_FALLBACK;
        }
        return source.substring(0, insert) + DISTANT_LIGHT_BOKEH_FALLBACK + "\n" + source.substring(insert);
    }

    private static String getProgramDefines(RenderPass pass) {
        return getProgramDefines(pass, null);
    }

    private static String getProgramDefines(RenderPass pass, String programName) {
        StringBuilder defines = new StringBuilder();
        if (pass == null) {
            appendComputeProgramDefines(defines, programName);
            return defines.toString();
        }

        appendProgramDefine(defines, pass);
        if (isTerrainAlias(pass)) {
            appendProgramDefine(defines, RenderPass.GBUFFERS_TERRAIN);
        }
        appendComputeProgramDefines(defines, programName);
        return defines.toString();
    }

    private static void appendComputeProgramDefines(StringBuilder defines, String programName) {
        if (programName == null || programName.isBlank()) {
            return;
        }

        String normalized = programName.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]", "_");
        if (normalized.isBlank()) {
            return;
        }
        appendProgramDefine(defines, normalized);

        for (String family : new String[]{"SHADOWCOMP", "COMPOSITE", "DEFERRED", "PREPARE", "BEGIN", "SETUP", "SHADOW", "FINAL"}) {
            if (normalized.startsWith(family) && !normalized.equals(family)) {
                appendProgramDefine(defines, family);
                break;
            }
        }
    }

    private static void appendProgramDefine(StringBuilder defines, String name) {
        String define = "#define " + name + '\n';
        if (defines.indexOf(define) < 0) {
            defines.append(define);
        }
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
                    GBUFFERS_DAMAGEDBLOCK -> true;
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
            String name = matcher.group(1);
            if (isGeneratedPreludeDefine(name)) {
                return "// AUSM generated prelude define already supplied: #define " + name;
            }

            ShaderOption option = options.get(name);
            if (option == null || !option.changed()) {
                return line;
            }

            return "// AUSM option overridden by prelude: #define " + option.name();
        }

        matcher = CONST_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return line;
        }

        ShaderOption option = options.get(matcher.group(2));
        if (option == null || !option.changed()) {
            return line;
        }

        return "// AUSM option overridden by prelude: const " + option.name();
    }

    private static boolean isGeneratedPreludeDefine(String name) {
        if (name == null) {
            return false;
        }
        if (name.equals("OVERWORLD") || name.equals("NETHER") || name.equals("THE_END")) {
            return true;
        }
        if (name.startsWith("MC_RENDER_STAGE_")) {
            return true;
        }
        if (name.startsWith("GBUFFERS_") || name.startsWith("GBUFFER_")) {
            return true;
        }
        return switch (name) {
            case "PREPARE", "SHADOW", "DEFERRED", "COMPOSITE", "FINAL" -> true;
            default -> false;
        };
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

    private static String ensureStageMinimumVersion(String versionLine, int shaderType) {
        int minimumVersion = minimumGlslVersion(shaderType);
        if (minimumVersion <= 0 || parseGlslVersion(versionLine) >= minimumVersion) {
            return versionLine;
        }

        return "#version " + minimumVersion + stageProfileSuffix(versionLine, shaderType, minimumVersion);
    }

    private static int minimumGlslVersion(int shaderType) {
        if (shaderType == GL32.GL_GEOMETRY_SHADER) {
            return 150;
        }
        if (shaderType == GL40.GL_TESS_CONTROL_SHADER || shaderType == GL40.GL_TESS_EVALUATION_SHADER) {
            return 400;
        }
        if (shaderType == GL43.GL_COMPUTE_SHADER) {
            return 430;
        }
        return 0;
    }

    private static String stageProfileSuffix(String versionLine, int shaderType, int minimumVersion) {
        if (shaderType == GL43.GL_COMPUTE_SHADER) {
            return "";
        }

        String[] parts = versionLine.trim().split("\\s+");
        if (parts.length > 2) {
            String profile = parts[2].trim();
            if ("core".equals(profile) || "compatibility".equals(profile)) {
                return " " + profile;
            }
        }
        return minimumVersion >= 150 ? " compatibility" : "";
    }

    private static String normalizeLegacyGlsl(String line, int glslVersion) {
        if (glslVersion >= 130) {
            return line;
        }

        return line.replaceFirst("^(\\s*)flat\\s+varying\\b", "$1varying");
    }

    private static String normalizeUnsupportedPreprocessor(String line) {
        Matcher directiveMatcher = CONDITION_DIRECTIVE_PATTERN.matcher(line);
        if (!directiveMatcher.matches()) {
            return line;
        }

        String expression = directiveMatcher.group(2);
        String comment = "";
        int lineComment = expression.indexOf("//");
        if (lineComment >= 0) {
            comment = expression.substring(lineComment);
            expression = expression.substring(0, lineComment);
        }

        String normalized = normalizeInExpression(expression);
        return directiveMatcher.group(1) + normalized + (comment.isEmpty() ? "" : " " + comment);
    }

    private static String normalizeInExpression(String expression) {
        String normalized = replaceInfixInExpressions(expression);
        normalized = replaceFunctionInExpressions(normalized);
        return normalized;
    }

    private static String replaceInfixInExpressions(String expression) {
        Matcher matcher = INFIX_IN_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(inExpression(matcher.group(1), matcher.group(2))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String replaceFunctionInExpressions(String expression) {
        Matcher matcher = FUNCTION_IN_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String[] values = matcher.group(1).split(",");
            if (values.length < 2) {
                matcher.appendReplacement(buffer, "0");
                continue;
            }
            String left = values[0].trim();
            StringBuilder rightValues = new StringBuilder();
            for (int i = 1; i < values.length; i++) {
                if (!rightValues.isEmpty()) {
                    rightValues.append(',');
                }
                rightValues.append(values[i]);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(inExpression(left, rightValues.toString())));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String inExpression(String left, String commaSeparatedValues) {
        String[] values = commaSeparatedValues.split(",");
        StringBuilder expression = new StringBuilder("(");
        int comparisons = 0;
        for (String rawValue : values) {
            String value = rawValue.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (comparisons++ > 0) {
                expression.append(" || ");
            }
            expression.append(left.trim()).append(" == ").append(value);
        }
        if (comparisons == 0) {
            return "0";
        }
        expression.append(')');
        return expression.toString();
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
     * Generates standard OptiFine hidden variables expected by advanced packs.
     */
    private static String getOptiFineEnvironmentDefines(ShaderOptions options, int glslVersion) {
        return ShaderEnvironmentDefines.shaderSourceDefines(options, glslVersion);
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
