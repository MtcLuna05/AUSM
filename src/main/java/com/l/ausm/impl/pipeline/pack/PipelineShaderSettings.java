package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.RenderPass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves active shader-pack constants without coupling parsing to render lifecycle state. */
public final class PipelineShaderSettings {
    private static final Pattern CONST_SETTING_PATTERN = Pattern.compile("^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+).*$");
    private static final Pattern DEFINE_SETTING_PATTERN = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\s+([^/\\s]+))?.*$");

    private PipelineShaderSettings() {
    }

    public static String optionValue(ShaderProperties properties, String name) {
        var option = properties.options().get(name);
        return option == null ? null : option.value();
    }

    public static String changedOptionValue(ShaderProperties properties, String name) {
        var option = properties.options().get(name);
        return option == null || !option.changed() ? null : option.value();
    }

    public static int parseIntOption(ShaderProperties properties, String name, int fallback) {
        return parseIntValue(optionValue(properties, name), fallback);
    }

    public static int parseIntSettingWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName, int fallback) {
        return parseIntValue(settingValueWithComment(pack, properties, optionName, commentName), fallback);
    }

    public static int parseIntSetting(ShaderPack pack, ShaderProperties properties, String name, int fallback) {
        return parseIntValue(settingValue(pack, properties, name), fallback);
    }

    public static float parseFloatOption(ShaderProperties properties, String name, float fallback) {
        return parseFloatValue(optionValue(properties, name), fallback);
    }

    public static float parseFloatSetting(ShaderPack pack, ShaderProperties properties, String name, float fallback) {
        return parseFloatValue(settingValue(pack, properties, name), fallback);
    }

    public static float parseFloatSettingWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName, float fallback) {
        return parseFloatValue(settingValueWithComment(pack, properties, optionName, commentName), fallback);
    }

    public static boolean optionBoolean(ShaderProperties properties, String name, boolean fallback) {
        var option = properties.options().get(name);
        return option == null ? fallback : option.asBoolean();
    }

    public static boolean parseBooleanSetting(ShaderPack pack, ShaderProperties properties, String name, boolean fallback) {
        String value = settingValue(pack, properties, name);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    public static String settingValueWithComment(ShaderPack pack, ShaderProperties properties, String optionName, String commentName) {
        String value = settingValue(pack, properties, optionName);
        if (value != null) {
            return value;
        }
        value = rawShaderProperty(pack, commentName);
        return value != null ? value : scanCommentDirective(pack, commentName);
    }

    private static String settingValue(ShaderPack pack, ShaderProperties properties, String name) {
        String value = changedOptionValue(properties, name);
        if (value != null) {
            return value;
        }
        value = rawShaderProperty(pack, name);
        if (value != null) {
            return value;
        }
        value = activeConstSetting(pack, properties, name);
        return value != null ? value : optionValue(properties, name);
    }

    public static int parseIntValue(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float parseFloatValue(String value, float fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String rawShaderProperty(ShaderPack pack, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        if (!pack.hasResource(layout.propertiesPath())) {
            return null;
        }
        Properties properties = new Properties();
        try (var stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream == null) {
                return null;
            }
            properties.load(stream);
        } catch (IOException ignored) {
            return null;
        }
        String value = properties.getProperty(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String scanCommentDirective(ShaderPack pack, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        String value = null;
        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                value = lastCommentDirectiveValue(pack, base + ".vsh", name, value);
                value = lastCommentDirectiveValue(pack, base + ".fsh", name, value);
                value = lastCommentDirectiveValue(pack, base + ".gsh", name, value);
            }
        }
        return lastCommentDirectiveValue(pack, layout.rootPath("shader.h"), name, value);
    }

    private static String lastCommentDirectiveValue(ShaderPack pack, String path, String name, String fallback) {
        if (!pack.hasResource(path)) {
            return fallback;
        }
        try (var stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return fallback;
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String prefix = "/* " + name + ":";
            int start = source.lastIndexOf(prefix);
            if (start < 0) {
                return fallback;
            }
            int valueStart = start + prefix.length();
            int end = source.indexOf("*/", valueStart);
            return end < 0 ? fallback : source.substring(valueStart, end).trim();
        } catch (IOException ignored) {
            return fallback;
        }
    }

    public static String activeConstSetting(ShaderPack pack, ShaderProperties properties, String name) {
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        ActiveConstScan scan = new ActiveConstScan(pack, properties, name);
        scan.scan(layout.rootPath("lib/config.glsl"));
        scan.scan(layout.rootPath("lib/settings.glsl"));
        scan.scan(layout.rootPath("settings.glsl"));
        scan.scan(layout.rootPath("shader.h"));
        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                scan.scan(base + ".vsh");
                scan.scan(base + ".fsh");
                scan.scan(base + ".gsh");
            }
        }
        return scan.value();
    }

    private static String includePath(String includeLine, String currentFile) {
        int firstQuote = includeLine.indexOf('"');
        int lastQuote = includeLine.lastIndexOf('"');
        if (firstQuote == -1 || lastQuote == -1 || firstQuote >= lastQuote) {
            return null;
        }
        String path = includeLine.substring(firstQuote + 1, lastQuote);
        if (path.startsWith("/")) {
            return currentFile.startsWith("shaders/") ? "shaders" + path : path.substring(1);
        }
        int lastSlash = currentFile.lastIndexOf('/');
        return lastSlash == -1 ? path : currentFile.substring(0, lastSlash + 1) + path;
    }

    private static final class ActiveConstScan {
        private final ShaderPack pack;
        private final ShaderProperties properties;
        private final String targetName;
        private final Map<String, String> defines = new HashMap<>();
        private final Set<String> visited = new HashSet<>();
        private final Deque<ConditionFrame> conditions = new ArrayDeque<>();
        private String value;

        private ActiveConstScan(ShaderPack pack, ShaderProperties properties, String targetName) {
            this.pack = pack;
            this.properties = properties;
            this.targetName = targetName;
            defines.putAll(ShaderEnvironmentDefines.defineMap(properties.options()));
        }

        private String value() { return value; }

        private void scan(String path) {
            if (!pack.hasResource(path) || !visited.add(path)) return;
            try (var stream = pack.getResourceAsStream(path)) {
                if (stream == null) return;
                for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R", -1)) scanLine(path, line);
            } catch (IOException ignored) {
            } finally {
                visited.remove(path);
            }
        }

        private void scanLine(String currentFile, String line) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include ")) {
                if (active()) { String path = includePath(trimmed, currentFile); if (path != null) scan(path); }
                return;
            }
            if (trimmed.startsWith("#if ")) { pushCondition(evaluateCondition(trimmed.substring(4))); return; }
            if (trimmed.startsWith("#ifdef ")) { pushCondition(defines.containsKey(trimmed.substring(7).trim())); return; }
            if (trimmed.startsWith("#ifndef ")) { pushCondition(!defines.containsKey(trimmed.substring(8).trim())); return; }
            if (trimmed.startsWith("#elif ")) { replaceCondition(evaluateCondition(trimmed.substring(6))); return; }
            if (trimmed.startsWith("#else")) { replaceCondition(true); return; }
            if (trimmed.startsWith("#endif")) { if (!conditions.isEmpty()) conditions.pop(); return; }
            if (!active()) return;
            String source = stripLineComment(line);
            Matcher define = DEFINE_SETTING_PATTERN.matcher(source);
            if (define.matches()) { applyDefine(define.group(1), define.group(2)); return; }
            Matcher constant = CONST_SETTING_PATTERN.matcher(source);
            if (constant.matches()) {
                defines.put(constant.group(1), constant.group(2));
                if (targetName.equals(constant.group(1))) value = constant.group(2);
            }
        }

        private void applyDefine(String name, String value) {
            var option = properties.options().get(name);
            if (option != null && option.toggle() && !option.asBoolean()) defines.remove(name);
            else if (option != null) defines.put(name, option.toggle() ? "1" : option.value());
            else defines.put(name, value == null ? "1" : value);
        }

        private void pushCondition(boolean condition) {
            boolean parent = active();
            conditions.push(new ConditionFrame(parent, parent && condition, condition));
        }

        private void replaceCondition(boolean condition) {
            if (conditions.isEmpty()) return;
            ConditionFrame previous = conditions.pop();
            boolean active = previous.parentActive && !previous.branchMatched && condition;
            conditions.push(new ConditionFrame(previous.parentActive, active, previous.branchMatched || condition));
        }

        private boolean active() { return conditions.isEmpty() || conditions.peek().active; }
        private boolean evaluateCondition(String expression) { return ShaderExpressionEvaluator.evaluate(stripLineComment(expression), defines); }
        private String stripLineComment(String value) { int start = value.indexOf("//"); return start < 0 ? value : value.substring(0, start); }
    }

    private static final class ConditionFrame {
        private final boolean parentActive;
        private final boolean active;
        private final boolean branchMatched;
        private ConditionFrame(boolean parentActive, boolean active, boolean branchMatched) {
            this.parentActive = parentActive;
            this.active = active;
            this.branchMatched = branchMatched;
        }
    }
}
