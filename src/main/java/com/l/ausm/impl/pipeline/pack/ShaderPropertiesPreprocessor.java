package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.impl.MainMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

final class ShaderPropertiesPreprocessor {

    private ShaderPropertiesPreprocessor() {
    }

    static Properties load(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options) {
        Properties properties = new Properties();
        StringBuilder activeProperties = new StringBuilder();
        Set<String> visited = new HashSet<>();
        appendActiveProperties(pack, layout, layout.propertiesPath(), options, activeProperties, visited);
        appendActiveProperties(pack, layout, layout.rootPath("colorwheel.properties"), options, activeProperties, visited);
        if (activeProperties.isEmpty()) {
            return properties;
        }
        try {
            properties.load(new StringReader(activeProperties.toString()));
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderProperties] Failed to parse preprocessed shader properties", e);
        }
        return properties;
    }

    private static void appendActiveProperties(
            ShaderPack pack,
            ShaderPackLayout layout,
            String path,
            ShaderOptions options,
            StringBuilder activeProperties,
            Set<String> visited
    ) {
        if (path == null || !visited.add(path) || !pack.hasResource(path)) {
            return;
        }

        try (InputStream stream = pack.getResourceAsStream(path)) {
            if (stream == null) {
                return;
            }

            Deque<ConditionFrame> conditions = new ArrayDeque<>();
            boolean enabled = true;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("#ifdef ")) {
                        boolean condition = ShaderEnvironmentDefines.isDefined(trimmed.substring("#ifdef ".length()).trim(), options);
                        conditions.push(new ConditionFrame(enabled, condition, condition));
                        enabled = enabled && condition;
                        continue;
                    }
                    if (trimmed.startsWith("#ifndef ")) {
                        boolean condition = !ShaderEnvironmentDefines.isDefined(trimmed.substring("#ifndef ".length()).trim(), options);
                        conditions.push(new ConditionFrame(enabled, condition, condition));
                        enabled = enabled && condition;
                        continue;
                    }
                    if (trimmed.startsWith("#if ")) {
                        boolean condition = evaluate(trimmed.substring("#if ".length()).trim(), options);
                        conditions.push(new ConditionFrame(enabled, condition, condition));
                        enabled = enabled && condition;
                        continue;
                    }
                    if (trimmed.startsWith("#else")) {
                        if (!conditions.isEmpty()) {
                            ConditionFrame frame = conditions.peek();
                            boolean branchEnabled = !frame.branchTaken();
                            conditions.pop();
                            conditions.push(new ConditionFrame(frame.parentEnabled(), branchEnabled, frame.branchTaken() || branchEnabled));
                            enabled = frame.parentEnabled() && branchEnabled;
                        }
                        continue;
                    }
                    if (trimmed.startsWith("#elif ")) {
                        if (!conditions.isEmpty()) {
                            ConditionFrame frame = conditions.peek();
                            boolean condition = !frame.branchTaken() && evaluate(trimmed.substring("#elif ".length()).trim(), options);
                            conditions.pop();
                            conditions.push(new ConditionFrame(frame.parentEnabled(), condition, frame.branchTaken() || condition));
                            enabled = frame.parentEnabled() && condition;
                        }
                        continue;
                    }
                    if (trimmed.startsWith("#endif")) {
                        if (!conditions.isEmpty()) {
                            enabled = conditions.pop().parentEnabled();
                        }
                        continue;
                    }

                    if (enabled && trimmed.startsWith("#include ")) {
                        String includePath = ShaderPreprocessor.extractIncludePath(trimmed, path);
                        if (includePath != null && !includePath.isBlank()) {
                            appendActiveProperties(pack, layout, includePath, options, activeProperties, visited);
                        }
                        continue;
                    }

                    if (enabled) {
                        activeProperties.append(line).append('\n');
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderProperties] Failed to read {}", path, e);
        }
    }

    private static boolean evaluate(String expression, ShaderOptions options) {
        return ShaderExpressionEvaluator.evaluate(expression, options);
    }

    private record ConditionFrame(boolean parentEnabled, boolean conditionEnabled, boolean branchTaken) {
    }
}
