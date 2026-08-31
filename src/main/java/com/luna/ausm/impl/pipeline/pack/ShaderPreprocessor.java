package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.pack.ShaderOption;
import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

public class ShaderPreprocessor {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("^\\s*(?://\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\b.*$");
    private static final Pattern CONST_PATTERN = Pattern.compile("^(\\s*const\\s+\\w+\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*=\\s*)([^;]+)(;.*)$");
    private static final Pattern LATE_PI_DECLARATION_PATTERN = Pattern.compile("^\\s*const\\s+float\\s+pi\\s*=.*$");
    private static final Pattern CONDITION_DIRECTIVE_PATTERN = Pattern.compile("^(\\s*#(?:if|elif)\\s+)(.*)$");
    private static final Pattern INFIX_IN_PATTERN = Pattern.compile("([A-Za-z_][A-Za-z0-9_\\.]*|[-+]?\\d+(?:\\.\\d+)?)\\s+in\\s*\\(([^()]*)\\)");
    private static final Pattern FUNCTION_IN_PATTERN = Pattern.compile("\\bin\\s*\\(([^()]*)\\)");
    private static final Pattern GLSL_130_FEATURE_PATTERN = Pattern.compile("\\b(?:[iu]sampler(?:1D|2D|3D|Cube|1DArray|2DArray|CubeArray|Buffer|2DRect)|sampler(?:1D|2D)Array|samplerCubeArray|texelFetch|textureSize|isnan|isinf)\\b");
    private static final Pattern FUNCTION_DECLARATION_PATTERN = Pattern.compile("^\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\s+)*(?:void|float|int|uint|bool|[biu]?vec[234]|mat[234])\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern VOXEL_FEATURE_IFDEF_PATTERN = Pattern.compile("^(\\s*)#ifdef\\s+(PORTAL_EDGE_EFFECT|CONNECTED_GLASS_EFFECT)\\s*(?://.*)?$");
    private static final Pattern VOXEL_FEATURE_DEFINED_IF_PATTERN = Pattern.compile("^(\\s*)#if\\s+defined\\s*\\(?\\s*(PORTAL_EDGE_EFFECT|CONNECTED_GLASS_EFFECT)\\s*\\)?\\s*(?://.*)?$");
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
    private static final String DISTANT_HORIZONS_VERTEX_COMPAT = """
            #ifndef AUSM_DISTANT_HORIZONS_VERTEX_COMPAT
            #define AUSM_DISTANT_HORIZONS_VERTEX_COMPAT
            attribute int dhMaterialId;
            #endif
            """;
    private static final String DISTANT_HORIZONS_FRAGMENT_COMPAT = """
            #ifndef AUSM_DISTANT_HORIZONS_FRAGMENT_COMPAT
            #define AUSM_DISTANT_HORIZONS_FRAGMENT_COMPAT
            #ifndef DH_BLOCK_LEAVES
            #define DH_BLOCK_LEAVES 1
            #endif
            #ifndef DH_BLOCK_GRASS
            #define DH_BLOCK_GRASS 2
            #endif
            #ifndef DH_BLOCK_WATER
            #define DH_BLOCK_WATER 3
            #endif
            #ifndef DH_BLOCK_ILLUMINATED
            #define DH_BLOCK_ILLUMINATED 4
            #endif
            #ifndef DH_BLOCK_LAVA
            #define DH_BLOCK_LAVA 5
            #endif
            #ifndef DH_BLOCK_SNOW
            #define DH_BLOCK_SNOW 6
            #endif
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
        return processShaderSource(pack, resourcePath, options, pass, shaderType, null, null);
    }

    public static String processShaderSource(
            ShaderPack pack,
            String resourcePath,
            ShaderOptions options,
            RenderPass pass,
            int shaderType,
            String programName
    ) throws IOException {
        return processShaderSource(pack, resourcePath, options, pass, shaderType, programName, null);
    }

    public static String processShaderSource(
            ShaderPack pack,
            String resourcePath,
            ShaderOptions options,
            RenderPass pass,
            int shaderType,
            String programName,
            ShaderPackDirectives directives
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
        if (parseGlslVersion(versionLine) < 130 && requiresGlsl130(rawLines)) {
            versionLine = "#version 130";
        }
        versionLine = ensureStageMinimumVersion(versionLine, shaderType);
        int glslVersion = parseGlslVersion(versionLine);
        ShaderOptions sourceOptions = removeFunctionCollidingOptions(options, rawLines);
        ShaderOptions preludeOptions = removeSourceDeclaredOptions(sourceOptions, rawLines);
        boolean requiresUnboundLatePiCompatibility = rawLines.stream()
                .map(String::trim)
                .anyMatch(line -> LATE_PI_DECLARATION_PATTERN.matcher(line).matches());

        StringBuilder finalSource = new StringBuilder();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#version ") || trimmed.startsWith("#extension ")) {
                continue;
            }

            String processed = applyOptionOverride(line, sourceOptions);
            if (requiresUnboundLatePiCompatibility
                    && LATE_PI_DECLARATION_PATTERN.matcher(processed.trim()).matches()) {
                processed = "// AUSM supplies pi in the generated prelude.";
            }
            processed = guardVoxelDependentFeatures(processed);
            processed = normalizeUnsupportedPreprocessor(processed);
            String processedTrimmed = processed.trim();
            if (processedTrimmed.matches("(?i)composite\\d+ has been reserved for future use.*")) {
                processed = processed.substring(0, processed.indexOf(processedTrimmed)) + "// " + processedTrimmed;
            }
            finalSource.append(normalizeLegacyGlsl(processed, glslVersion)).append("\n");
        }

        // 3. Construct the final valid GLSL string
        StringBuilder out = new StringBuilder();
        out.append(versionLine).append("\n");
        for (String extensionLine : extensionLines) {
            out.append(extensionLine).append("\n");
        }
        out.append(getOptiFineEnvironmentDefines(preludeOptions, glslVersion, directives)).append("\n");
        out.append(getRenderStageDefines()).append("\n");
        out.append(getProgramDefines(pass, programName)).append("\n");
        out.append(getOptiFineMetadataDefines()).append("\n");
        if (requiresUnboundLatePiCompatibility) {
            out.append(getUnboundLatePiCompatibilityPrelude()).append("\n");
        }
        out.append(finalSource);

        String source = addMissingDistantHorizonsCompat(out.toString(), pass, shaderType);
        source = addMissingDistantLightBokehFallback(source);
        return ShaderTransformPipeline.transform(source, shaderType, pass);
    }

    private static ShaderOptions removeFunctionCollidingOptions(ShaderOptions options, List<String> rawLines) {
        if (options == null || options.all().isEmpty()) {
            return options;
        }

        Set<String> functionNames = new HashSet<>();
        for (String line : rawLines) {
            Matcher matcher = FUNCTION_DECLARATION_PATTERN.matcher(stripLineComment(line));
            if (matcher.find()) {
                functionNames.add(matcher.group(1));
            }
        }
        if (functionNames.isEmpty()) {
            return options;
        }

        Map<String, ShaderOption> filtered = new LinkedHashMap<>();
        boolean removed = false;
        for (ShaderOption option : options.all().values()) {
            if (functionNames.contains(option.name())) {
                removed = true;
                continue;
            }
            filtered.put(option.name(), option);
        }
        return removed ? new ShaderOptions(filtered) : options;
    }

    private static ShaderOptions removeSourceDeclaredOptions(ShaderOptions options, List<String> rawLines) {
        if (options == null || options.all().isEmpty()) {
            return options;
        }

        Set<String> declaredNames = new HashSet<>();
        for (String line : rawLines) {
            Matcher define = DEFINE_PATTERN.matcher(line);
            if (define.matches()) {
                declaredNames.add(define.group(1));
                continue;
            }
            Matcher constant = CONST_PATTERN.matcher(line);
            if (constant.matches()) {
                declaredNames.add(constant.group(2));
            }
        }
        if (declaredNames.isEmpty()) {
            return options;
        }

        Map<String, ShaderOption> filtered = new LinkedHashMap<>();
        boolean removed = false;
        for (ShaderOption option : options.all().values()) {
            if (declaredNames.contains(option.name())) {
                removed = true;
            } else {
                filtered.put(option.name(), option);
            }
        }
        return removed ? new ShaderOptions(filtered) : options;
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static String guardVoxelDependentFeatures(String line) {
        Matcher ifdef = VOXEL_FEATURE_IFDEF_PATTERN.matcher(line);
        if (ifdef.matches()) {
            String feature = ifdef.group(2);
            return ifdef.group(1) + "#if defined(" + feature + ") && defined(COLORED_LIGHTING_INTERNAL) && COLORED_LIGHTING_INTERNAL > 0";
        }

        Matcher definedIf = VOXEL_FEATURE_DEFINED_IF_PATTERN.matcher(line);
        if (definedIf.matches()) {
            String feature = definedIf.group(2);
            return definedIf.group(1) + "#if defined(" + feature + ") && defined(COLORED_LIGHTING_INTERNAL) && COLORED_LIGHTING_INTERNAL > 0";
        }
        return line;
    }

    private static String addMissingDistantHorizonsCompat(String source, RenderPass pass, int shaderType) {
        if (pass != RenderPass.DH_TERRAIN && pass != RenderPass.DH_WATER) {
            return source;
        }

        String result = source;
        if (result.contains("dhMaterialId")
                && shaderType == GL20.GL_VERTEX_SHADER
                && !declaresSymbolInStage(result, "dhMaterialId", "VERTEX_SHADER")) {
            result = insertBeforeStageProgram(result, DISTANT_HORIZONS_VERTEX_COMPAT, "VERTEX_SHADER");
        }
        if (result.contains("DH_BLOCK_")
                && shaderType == GL20.GL_FRAGMENT_SHADER) {
            result = insertBeforeProgram(result, DISTANT_HORIZONS_FRAGMENT_COMPAT);
        }
        return result;
    }

    private static boolean declaresSymbolInStage(String source, String symbol, String stageDefine) {
        Pattern declaration = Pattern.compile(
                "\\b(?:attribute|in|uniform|varying|flat\\s+in|const)\\s+\\w+\\s+" + Pattern.quote(symbol) + "\\b"
        );
        int stageStart = findPrimaryStageStart(source, stageDefine);
        if (stageStart < 0) {
            return declaration.matcher(source).find();
        }

        return declaration.matcher(source.substring(stageStart)).find();
    }

    private static String addMissingDistantLightBokehFallback(String source) {
        if (!source.contains(DISTANT_LIGHT_BOKEH_FUNCTION + "(")
                || source.contains("void " + DISTANT_LIGHT_BOKEH_FUNCTION)) {
            return source;
        }

        return insertBeforeProgram(source, DISTANT_LIGHT_BOKEH_FALLBACK);
    }

    private static String insertBeforeProgram(String source, String fallback) {
        int insert = source.indexOf("//Program//");
        if (insert < 0) {
            insert = source.indexOf("void main");
        }
        if (insert < 0) {
            return source + "\n" + fallback;
        }
        return source.substring(0, insert) + fallback + "\n" + source.substring(insert);
    }

    private static String insertBeforeStageProgram(String source, String fallback, String stageDefine) {
        int stageStart = findPrimaryStageStart(source, stageDefine);
        if (stageStart < 0) {
            return insertBeforeProgram(source, fallback);
        }

        int program = source.indexOf("//Program//", stageStart);
        int main = source.indexOf("void main", stageStart);
        int insert;
        if (program >= 0 && (main < 0 || program < main)) {
            insert = program;
        } else {
            insert = main;
        }
        if (insert < 0) {
            return source.substring(0, stageStart) + fallback + "\n" + source.substring(stageStart);
        }
        return source.substring(0, insert) + fallback + "\n" + source.substring(insert);
    }

    private static int findPrimaryStageStart(String source, String stageDefine) {
        String marker = switch (stageDefine) {
            case "VERTEX_SHADER" -> "//////////Vertex Shader";
            case "FRAGMENT_SHADER" -> "//////////Fragment Shader";
            default -> null;
        };
        if (marker != null) {
            int markerStart = source.lastIndexOf(marker);
            if (markerStart < 0) {
                return source.lastIndexOf("#ifdef " + stageDefine);
            }
            int stageStart = source.indexOf("#ifdef " + stageDefine, markerStart);
            if (stageStart >= 0) {
                return stageStart;
            }
        }
        return source.lastIndexOf("#ifdef " + stageDefine);
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
        appendDistantHorizonsPassDefines(defines, pass);
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

    private static void appendDistantHorizonsPassDefines(StringBuilder defines, RenderPass pass) {
        if (pass == RenderPass.DH_TERRAIN || pass == RenderPass.DH_WATER) {
            appendProgramDefine(defines, "DISTANT_HORIZONS 1");
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
            appendParentGbufferAliases(defines, programDefine);
        }
    }

    private static void appendParentGbufferAliases(StringBuilder defines, String programDefine) {
        switch (programDefine) {
            case "GBUFFERS_BLOCK_TRANSLUCENT" -> appendProgramDefine(defines, "GBUFFERS_BLOCK");
            case "GBUFFERS_ENTITIES_TRANSLUCENT", "GBUFFERS_ENTITIES_GLOWING", "GBUFFERS_LIGHTNING" ->
                    appendProgramDefine(defines, "GBUFFERS_ENTITIES");
            default -> {
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

    private static String getUnboundLatePiCompatibilityPrelude() {
        return """
                // Complementary Unbound declares pi after include files that use it.
                // GLSL requires the declaration before those uses.
                #define pi 3.14159265359
                #ifndef EMISSION_MULTIPLIER
                #define EMISSION_MULTIPLIER 1.0
                #endif
                #ifndef LAVA_NOISE_INTENSITY
                #define LAVA_NOISE_INTENSITY 1.0
                #endif
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
            if (option == null) {
                return line;
            }

            String indent = line.substring(0, line.indexOf(line.trim()));
            if (option.toggle()) {
                return option.asBoolean()
                        ? indent + "#define " + option.name()
                        : indent + "// AUSM option disabled: #define " + option.name();
            }
            return indent + "#define " + option.name() + " " + option.value();
        }

        matcher = CONST_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return line;
        }

        ShaderOption option = options.get(matcher.group(2));
        if (option == null) {
            return line;
        }

        return matcher.group(1)
                + option.name()
                + matcher.group(3)
                + option.value()
                + matcher.group(5);
    }

    private static boolean isGeneratedPreludeDefine(String name) {
        if (name == null) {
            return false;
        }
        if (name.equals("OVERWORLD") || name.equals("NETHER") || name.equals("THE_END")
                || name.equals("DISTANT_HORIZON")) {
            return true;
        }
        if (name.startsWith("MC_RENDER_STAGE_")) {
            return true;
        }
        if (name.startsWith("GBUFFERS_") || name.startsWith("GBUFFER_")) {
            return true;
        }
        return switch (name) {
            case "PREPARE", "SHADOW", "DEFERRED", "COMPOSITE", "FINAL",
                 "AUSM_SIMPLE_VOID_WORLD", "AUSM_ABYSSAL_WASTELAND", "AUSM_DREADLANDS" -> true;
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

    private static boolean requiresGlsl130(List<String> rawLines) {
        for (String line : rawLines) {
            if (line.contains("gl_VertexID") || GLSL_130_FEATURE_PATTERN.matcher(line).find()) {
                return true;
            }
        }
        return false;
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
    private static String getOptiFineEnvironmentDefines(ShaderOptions options, int glslVersion, ShaderPackDirectives directives) {
        return ShaderEnvironmentDefines.shaderSourceDefines(options, glslVersion, directives);
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
