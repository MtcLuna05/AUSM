package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.luna.ausm.api.pipeline.pack.ShaderOption;
import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import com.luna.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.luna.ausm.api.pipeline.pack.ShaderTextureScale;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.MainMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderBufferFormatScanner {

    private static final Pattern FORMAT_PATTERN = Pattern.compile(
            "^\\s*const\\s+int\\s+([A-Za-z0-9_]+)Format\\s*=\\s*([A-Z0-9_]+)\\s*;.*$"
    );
    private static final Pattern CLEAR_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+([A-Za-z0-9_]+)Clear\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern CLEAR_COLOR_PATTERN = Pattern.compile(
            "^\\s*const\\s+vec4\\s+([A-Za-z0-9_]+)ClearColor\\s*=\\s*vec4\\s*\\(([^)]*)\\)\\s*;.*$"
    );
    private static final Pattern MIPMAP_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+([A-Za-z0-9_]+)MipmapEnabled\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern SHADOW_DEPTH_TEXTURE_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+shadowtex([01])(Nearest|Mipmap)\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern SHADOW_COLOR_TEXTURE_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+shadow[Cc]olor([01])?(Clear|Nearest|Mipmap|MinMagNearest)\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern GENERATE_SHADOW_COLOR_MIPMAP_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+generateShadowColorMipmap\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern SHADOW_HARDWARE_FILTERING_PATTERN = Pattern.compile(
            "^\\s*const\\s+bool\\s+shadowHardwareFiltering\\s*=\\s*(true|false)\\s*;.*$"
    );
    private static final Pattern SHADOW_SAMPLER_PATTERN = Pattern.compile(
            "^\\s*uniform\\s+sampler2DShadow\\s+shadowtex[01](?:HW)?\\s*;.*$"
    );
    private static final Pattern SIZE_BUFFER_PATTERN = Pattern.compile(
            "^\\s*size\\.buffer\\.([A-Za-z0-9_]+)\\s*=\\s*(\\S+)\\s+(\\S+)\\s*$"
    );
    private static final Pattern GDEPTH_UNIFORM_PATTERN = Pattern.compile(
            "^\\s*uniform\\s+sampler\\w*\\s+gdepth\\s*;.*$"
    );
    private static final Pattern DEFINE_PATTERN = Pattern.compile(
            "^\\s*(?://\\s*)?#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+([^/\\s]+))?.*$"
    );
    private static final Pattern CONST_PATTERN = Pattern.compile(
            "^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+).*$"
    );

    private ShaderBufferFormatScanner() {
    }

    public static ShaderRenderTargetSettings scan(ShaderPack pack) {
        return scan(pack, ShaderOptions.empty());
    }

    public static ShaderRenderTargetSettings scan(ShaderPack pack, ShaderOptions options) {
        ShaderRenderTargetSettings.Builder settings = ShaderRenderTargetSettings.builder();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        scanProperties(pack, layout, options, settings);
        scanGlobalSettings(pack, layout, options, settings);

        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                scanProgram(pack, options, pass, base, settings);
            }
        }

        ShaderRenderTargetSettings result = settings.build();
        if (!result.formats().isEmpty()) {
            MainMod.LOGGER.debug("[ShaderRenderTargets] Loaded formats: {}", result.formats());
        }
        if (!result.clearDisabled().isEmpty()) {
            MainMod.LOGGER.debug("[ShaderRenderTargets] Clear disabled for: {}", result.clearDisabled());
        }
        if (!result.mipmapEnabledByPass().isEmpty()) {
            MainMod.LOGGER.debug("[ShaderRenderTargets] Mipmaps enabled by pass: {}", result.mipmapEnabledByPass());
        }
        if (!result.textureScales().isEmpty()) {
            MainMod.LOGGER.debug("[ShaderRenderTargets] Loaded texture scales: {}", result.textureScales());
        }
        return result;
    }

    private static void scanProperties(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options, ShaderRenderTargetSettings.Builder settings) {
        if (!pack.hasResource(layout.propertiesPath())) {
            return;
        }

        try (InputStream stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = SIZE_BUFFER_PATTERN.matcher(line);
                    if (!matcher.matches()) {
                        continue;
                    }

                    Attachment attachment = attachmentFromName(matcher.group(1));
                    if (attachment != null) {
                        settings.setTextureScale(attachment, new ShaderTextureScale(
                                optionValue(options, matcher.group(2), matcher.group(2)),
                                optionValue(options, matcher.group(3), matcher.group(3))
                        ));
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderRenderTargets] Failed to scan size.buffer directives", e);
        }
    }

    private static void scanGlobalSettings(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options, ShaderRenderTargetSettings.Builder settings) {
        scanFile(pack, options, null, layout.rootPath("lib/pipelineSettings.glsl"), settings, new HashSet<>(), new HashMap<>(), new ArrayDeque<>());
    }

    private static void scanProgram(ShaderPack pack, ShaderOptions options, RenderPass pass, String basePath, ShaderRenderTargetSettings.Builder settings) {
        scanFile(pack, options, pass, basePath + ".vsh", settings, new HashSet<>(), new HashMap<>(), new ArrayDeque<>());
        scanFile(pack, options, pass, basePath + ".fsh", settings, new HashSet<>(), new HashMap<>(), new ArrayDeque<>());
        scanFile(pack, options, pass, basePath + ".gsh", settings, new HashSet<>(), new HashMap<>(), new ArrayDeque<>());
        // Modern packs such as Complementary place shared program declarations
        // (including colortexNClear persistence flags) in program/<name>.glsl.
        // Missing those flags makes a temporal buffer get cleared and then sampled
        // as unrelated current-frame data by TAA.
        scanFile(pack, options, pass, basePath + ".glsl", settings, new HashSet<>(), new HashMap<>(), new ArrayDeque<>());
    }

    private static void scanFile(
            ShaderPack pack,
            ShaderOptions options,
            RenderPass pass,
            String path,
            ShaderRenderTargetSettings.Builder settings,
            Set<String> visited,
            Map<String, String> defines,
            Deque<ConditionFrame> conditions
    ) {
        if (!visited.add(path) || !pack.hasResource(path)) {
            return;
        }

        try (InputStream stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    String withoutComment = stripLineComment(line);
                    if (trimmed.startsWith("#include ")) {
                        if (active(conditions)) {
                            String includePath = ShaderPreprocessor.extractIncludePath(trimmed, path);
                            if (includePath != null) {
                                scanFile(pack, options, pass, includePath, settings, visited, defines, conditions);
                            }
                        }
                        continue;
                    }
                    if (trimmed.startsWith("#ifdef ")) {
                        pushCondition(conditions, defines.containsKey(trimmed.substring("#ifdef ".length()).trim()));
                        continue;
                    }
                    if (trimmed.startsWith("#ifndef ")) {
                        pushCondition(conditions, !defines.containsKey(trimmed.substring("#ifndef ".length()).trim()));
                        continue;
                    }
                    if (trimmed.startsWith("#if ")) {
                        pushCondition(conditions, evaluateCondition(trimmed.substring("#if ".length()), defines));
                        continue;
                    }
                    if (trimmed.startsWith("#elif ")) {
                        replaceCondition(conditions, evaluateCondition(trimmed.substring("#elif ".length()), defines));
                        continue;
                    }
                    if (trimmed.startsWith("#else")) {
                        replaceCondition(conditions, true);
                        continue;
                    }
                    if (trimmed.startsWith("#endif")) {
                        if (!conditions.isEmpty()) {
                            conditions.pop();
                        }
                        continue;
                    }
                    if (!active(conditions)) {
                        continue;
                    }

                    Matcher defineMatcher = DEFINE_PATTERN.matcher(line);
                    if (defineMatcher.matches()) {
                        applyDefine(defines, options, defineMatcher.group(1), defineMatcher.group(2));
                        continue;
                    }

                    Matcher constMatcher = CONST_PATTERN.matcher(withoutComment);
                    if (constMatcher.matches()) {
                        defines.put(constMatcher.group(1), optionValue(options, constMatcher.group(1), constMatcher.group(2)));
                    }

                    if (GDEPTH_UNIFORM_PATTERN.matcher(withoutComment).matches()) {
                        settings.setDefaultFormat(Attachment.DEPTH, ColorBufferFormat.RGBA32F);
                    }
                    if (SHADOW_SAMPLER_PATTERN.matcher(withoutComment).matches()) {
                        settings.setShadowHardwareFiltering(true);
                    }

                    Matcher formatMatcher = FORMAT_PATTERN.matcher(withoutComment);
                    if (formatMatcher.matches()) {
                        Attachment attachment = attachmentFromName(formatMatcher.group(1));
                        ColorBufferFormat format = ColorBufferFormat.fromName(formatMatcher.group(2));
                        if (attachment != null && format != null) {
                            settings.setFormat(attachment, format);
                        }
                        continue;
                    }

                    Matcher clearMatcher = CLEAR_PATTERN.matcher(withoutComment);
                    if (clearMatcher.matches()) {
                        Attachment attachment = attachmentFromName(clearMatcher.group(1));
                        if (attachment != null) {
                            setClearEnabled(settings, pass, attachment, Boolean.parseBoolean(clearMatcher.group(2)));
                        }
                        continue;
                    }

                    Matcher clearColorMatcher = CLEAR_COLOR_PATTERN.matcher(withoutComment);
                    if (clearColorMatcher.matches()) {
                        Attachment attachment = attachmentFromName(clearColorMatcher.group(1));
                        float[] color = parseVec4(clearColorMatcher.group(2));
                        if (attachment != null && color != null) {
                            settings.setClearColor(attachment, color);
                        }
                        continue;
                    }

                    Matcher mipmapMatcher = MIPMAP_PATTERN.matcher(withoutComment);
                    if (mipmapMatcher.matches()) {
                        Attachment attachment = attachmentFromName(mipmapMatcher.group(1));
                        if (attachment != null) {
                            setMipmapEnabled(settings, pass, attachment, Boolean.parseBoolean(mipmapMatcher.group(2)));
                        }
                        continue;
                    }

                    Matcher shadowDepthTextureMatcher = SHADOW_DEPTH_TEXTURE_PATTERN.matcher(withoutComment);
                    if (shadowDepthTextureMatcher.matches()) {
                        int index = Integer.parseInt(shadowDepthTextureMatcher.group(1));
                        boolean value = Boolean.parseBoolean(shadowDepthTextureMatcher.group(3));
                        if (shadowDepthTextureMatcher.group(2).equals("Nearest")) {
                            settings.setShadowDepthNearest(index, value);
                        } else {
                            settings.setShadowDepthMipmap(index, value);
                        }
                        continue;
                    }

                    Matcher generateShadowColorMipmapMatcher = GENERATE_SHADOW_COLOR_MIPMAP_PATTERN.matcher(withoutComment);
                    if (generateShadowColorMipmapMatcher.matches()) {
                        settings.setAllShadowColorMipmap(Boolean.parseBoolean(generateShadowColorMipmapMatcher.group(1)));
                        continue;
                    }

                    Matcher shadowColorTextureMatcher = SHADOW_COLOR_TEXTURE_PATTERN.matcher(withoutComment);
                    if (shadowColorTextureMatcher.matches()) {
                        String indexGroup = shadowColorTextureMatcher.group(1);
                        int index = indexGroup == null || indexGroup.isEmpty() ? 0 : Integer.parseInt(indexGroup);
                        boolean value = Boolean.parseBoolean(shadowColorTextureMatcher.group(3));
                        switch (shadowColorTextureMatcher.group(2)) {
                            case "Clear" -> settings.setShadowColorClear(index, value);
                            case "Mipmap" -> settings.setShadowColorMipmap(index, value);
                            case "Nearest", "MinMagNearest" -> settings.setShadowColorNearest(index, value);
                        }
                        continue;
                    }

                    Matcher shadowHardwareFilteringMatcher = SHADOW_HARDWARE_FILTERING_PATTERN.matcher(withoutComment);
                    if (shadowHardwareFilteringMatcher.matches()) {
                        settings.setShadowHardwareFiltering(Boolean.parseBoolean(shadowHardwareFilteringMatcher.group(1)));
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderRenderTargets] Failed to scan {}", path, e);
        } finally {
            visited.remove(path);
        }
    }

    private static void setClearEnabled(ShaderRenderTargetSettings.Builder settings, RenderPass pass, Attachment attachment, boolean enabled) {
        if (pass != null) {
            settings.setClearEnabled(pass, attachment, enabled);
            return;
        }
        for (RenderPass renderPass : RenderPass.values()) {
            settings.setClearEnabled(renderPass, attachment, enabled);
        }
    }

    private static void setMipmapEnabled(ShaderRenderTargetSettings.Builder settings, RenderPass pass, Attachment attachment, boolean enabled) {
        if (pass != null) {
            settings.setMipmapEnabled(pass, attachment, enabled);
            return;
        }
        for (RenderPass renderPass : RenderPass.values()) {
            settings.setMipmapEnabled(renderPass, attachment, enabled);
        }
    }

    private static void applyDefine(Map<String, String> defines, ShaderOptions options, String name, String value) {
        ShaderOption option = options.get(name);
        if (option != null && option.toggle() && !option.asBoolean()) {
            defines.remove(name);
            return;
        }
        if (option != null) {
            defines.put(name, option.toggle() ? "1" : option.value());
            return;
        }
        defines.put(name, value == null || value.isBlank() ? "1" : value);
    }

    private static String optionValue(ShaderOptions options, String name, String fallback) {
        ShaderOption option = options.get(name);
        return option != null ? option.value() : fallback;
    }

    private static void pushCondition(Deque<ConditionFrame> conditions, boolean condition) {
        boolean parentActive = active(conditions);
        boolean branchActive = parentActive && condition;
        conditions.push(new ConditionFrame(parentActive, branchActive, condition));
    }

    private static void replaceCondition(Deque<ConditionFrame> conditions, boolean condition) {
        if (conditions.isEmpty()) {
            return;
        }
        ConditionFrame previous = conditions.pop();
        boolean branchActive = previous.parentActive() && !previous.branchMatched() && condition;
        conditions.push(new ConditionFrame(previous.parentActive(), branchActive, previous.branchMatched() || condition));
    }

    private static boolean active(Deque<ConditionFrame> conditions) {
        return conditions.isEmpty() || conditions.peek().active();
    }

    private static boolean evaluateCondition(String expression, Map<String, String> defines) {
        return ShaderExpressionEvaluator.evaluate(stripLineComment(expression), defines);
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private static float[] parseVec4(String args) {
        String[] parts = args.split(",");
        if (parts.length != 4) {
            return null;
        }
        try {
            return new float[]{
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim()),
                    Float.parseFloat(parts[3].trim())
            };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Attachment attachmentFromName(String name) {
        if (name.startsWith("colortex")) {
            try {
                return Attachment.fromColorIndex(Integer.parseInt(name.substring("colortex".length())));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return switch (name) {
            case "gcolor" -> Attachment.COLOR;
            case "gdepth" -> Attachment.DEPTH;
            case "gnormal" -> Attachment.NORMAL;
            case "composite" -> Attachment.COMPOSITE;
            case "gaux1" -> Attachment.AUX1;
            case "gaux2" -> Attachment.AUX2;
            case "gaux3" -> Attachment.AUX3;
            case "gaux4" -> Attachment.AUX4;
            default -> null;
        };
    }

    private record ConditionFrame(boolean parentActive, boolean active, boolean branchMatched) {
    }
}
