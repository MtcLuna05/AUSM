package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderOptionScanner {

    private static final Pattern CHOICES_PATTERN = Pattern.compile("//\\s*\\[([^]]+)]");
    private static final Pattern COMMENTED_DEFINE_PATTERN = Pattern.compile("^\\s*//\\s*#define\\s+.*$");

    private ShaderOptionScanner() {
    }

    public static ShaderOptions scan(ShaderPack pack, Properties properties, Map<String, String> overrides) {
        Set<String> sliders = parseList(properties.getProperty("sliders"));
        Map<String, ShaderOption> options = new LinkedHashMap<>();
        Set<String> visitedFiles = new HashSet<>();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);

        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                scanShaderFile(pack, base + ".vsh", sliders, options, visitedFiles);
                scanShaderFile(pack, base + ".fsh", sliders, options, visitedFiles);
                scanShaderFile(pack, base + ".gsh", sliders, options, visitedFiles);
            }
        }
        scanShaderFile(pack, layout.rootPath("shader.h"), sliders, options, visitedFiles);

        overrides.forEach((name, value) -> {
            ShaderOption option = options.get(name);
            if (option != null) {
                options.put(name, option.withValue(value));
            }
        });

        MainMod.LOGGER.debug("[ShaderOptions] Discovered {} shader options", options.size());
        return new ShaderOptions(options);
    }

    private static Set<String> parseList(String value) {
        Set<String> result = new HashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String token : value.trim().split("\\s+")) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private static void scanShaderFile(
            ShaderPack pack,
            String path,
            Set<String> sliders,
            Map<String, ShaderOption> options,
            Set<String> visitedFiles
    ) {
        if (!visitedFiles.add(path) || !pack.hasResource(path)) {
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
                    if (trimmed.startsWith("#include ")) {
                        String includePath = ShaderPreprocessor.extractIncludePath(trimmed, path);
                        if (includePath != null) {
                            scanShaderFile(pack, includePath, sliders, options, visitedFiles);
                        }
                        continue;
                    }

                    DefineOption define = parseDefine(line);
                    if (define != null) {
                        addOption(define, sliders, options);
                        continue;
                    }

                    DefineOption constant = parseConst(line);
                    if (constant != null) {
                        addOption(constant, sliders, options);
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderOptions] Failed to scan shader options in {}", path, e);
        }
    }

    private static void addOption(DefineOption define, Set<String> sliders, Map<String, ShaderOption> options) {
        String name = define.name();
        String rawValue = define.value();
        String value = rawValue == null ? Boolean.toString(define.enabled()) : rawValue;
        List<String> choices = parseChoices(define.choices());
        boolean booleanLiteral = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
        if (!choices.isEmpty() && !choices.contains(value)) {
            choices = new ArrayList<>(choices);
            choices.add(0, value);
            choices = List.copyOf(choices);
        }
        options.putIfAbsent(name, new ShaderOption(
                name,
                value,
                value,
                choices,
                sliders.contains(name),
                rawValue == null || booleanLiteral && choices.isEmpty()
        ));
    }

    private static DefineOption parseDefine(String line) {
        String trimmed = line.trim();
        boolean commented = COMMENTED_DEFINE_PATTERN.matcher(line).matches();
        if (!trimmed.startsWith("#define ") && !commented) {
            return null;
        }

        String choices = parseChoiceComment(line);

        String definePart;
        if (commented) {
            definePart = line.substring(line.indexOf("#define"));
            int inlineComment = definePart.indexOf("//");
            if (inlineComment >= 0) {
                definePart = definePart.substring(0, inlineComment);
            }
        } else {
            int commentStart = line.indexOf("//");
            definePart = commentStart >= 0 ? line.substring(0, commentStart) : line;
        }
        String[] tokens = definePart.trim().split("\\s+", 3);
        if (tokens.length < 2 || !tokens[0].equals("#define")) {
            return null;
        }

        String value = tokens.length >= 3 && !tokens[2].isBlank() ? tokens[2].trim().split("\\s+")[0] : null;
        return new DefineOption(tokens[1], value, choices, !commented);
    }

    private static DefineOption parseConst(String line) {
        String choices = parseChoiceComment(line);

        int commentStart = line.indexOf("//");
        String constPart = commentStart >= 0 ? line.substring(0, commentStart) : line;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\s*const\\s+\\w+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*([^;\\s]+)")
                .matcher(constPart);
        if (!matcher.find()) {
            return null;
        }

        return new DefineOption(matcher.group(1), matcher.group(2), choices, true);
    }

    private static String parseChoiceComment(String line) {
        Matcher matcher = CHOICES_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> parseChoices(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> choices = new ArrayList<>();
        for (String token : value.trim().split("\\s+")) {
            if (!token.isBlank()) {
                choices.add(token);
            }
        }
        choices = expandRange(choices);
        choices.removeIf("..."::equals);
        return List.copyOf(choices);
    }

    private static List<String> expandRange(List<String> choices) {
        int ellipsis = choices.indexOf("...");
        if (ellipsis < 2 || ellipsis > choices.size() - 3) {
            return choices;
        }

        try {
            BigDecimal first = new BigDecimal(choices.get(ellipsis - 2));
            BigDecimal second = new BigDecimal(choices.get(ellipsis - 1));
            BigDecimal beforeLast = new BigDecimal(choices.get(ellipsis + 1));
            BigDecimal last = new BigDecimal(choices.get(ellipsis + 2));
            BigDecimal step = second.subtract(first);
            if (step.signum() == 0) {
                return choices;
            }

            List<String> expanded = new ArrayList<>(choices.subList(0, ellipsis - 2));
            for (BigDecimal current = first; current.compareTo(last) <= 0; current = current.add(step)) {
                expanded.add(formatDecimal(current, choices.get(ellipsis - 2)));
                if (current.compareTo(beforeLast) > 0) {
                    break;
                }
            }
            if (!expanded.get(expanded.size() - 1).equals(choices.get(ellipsis + 2))) {
                expanded.add(choices.get(ellipsis + 2));
            }
            expanded.addAll(choices.subList(ellipsis + 3, choices.size()));
            return expanded;
        } catch (NumberFormatException e) {
            return choices;
        }
    }

    private static String formatDecimal(BigDecimal value, String sample) {
        int scale = Math.max(0, sample.indexOf('.') >= 0 ? sample.length() - sample.indexOf('.') - 1 : 0);
        return value.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private record DefineOption(String name, String value, String choices, boolean enabled) {
    }
}
