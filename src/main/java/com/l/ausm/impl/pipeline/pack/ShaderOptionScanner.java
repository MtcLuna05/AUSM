package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.pack.ShaderOption;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String AUSM_CUSTOM_VOID_WORLD = "AUSM_CUSTOM_VOID_WORLD";

    private ShaderOptionScanner() {
    }

    public static ShaderOptions scan(ShaderPack pack, Properties properties, Map<String, String> overrides) {
        Set<String> sliders = parseList(properties.getProperty("sliders"));
        Set<String> propertyOptions = parsePropertyOptionNames(properties, sliders);
        Map<String, ShaderOption> options = new LinkedHashMap<>();
        Set<String> visitedFiles = new HashSet<>();
        ShaderPackLayout layout = ShaderPackLayout.detect(pack);

        for (RenderPass pass : RenderPass.values()) {
            for (String base : layout.programBases(pass)) {
                scanShaderFile(pack, base + ".vsh", sliders, propertyOptions, options, visitedFiles);
                scanShaderFile(pack, base + ".fsh", sliders, propertyOptions, options, visitedFiles);
                scanShaderFile(pack, base + ".gsh", sliders, propertyOptions, options, visitedFiles);
            }
        }
        scanShaderFile(pack, layout.rootPath("shader.h"), sliders, propertyOptions, options, visitedFiles);
        addFallbackBinaryOptionIfReferenced(properties, sliders, options, AUSM_CUSTOM_VOID_WORLD, "1");

        overrides.forEach((name, value) -> {
            ShaderOption option = options.get(name);
            if (option != null) {
                options.put(name, option.withValue(value));
            }
        });

        MainMod.LOGGER.debug("[ShaderOptions] Discovered {} shader options", options.size());
        return new ShaderOptions(options);
    }

    private static void addFallbackBinaryOptionIfReferenced(Properties properties, Set<String> sliders,
                                                            Map<String, ShaderOption> options, String name,
                                                            String defaultValue) {
        if (options.containsKey(name) || !propertiesMention(properties, name)) {
            return;
        }

        ShaderOption option = new ShaderOption(
                name,
                defaultValue,
                defaultValue,
                List.of("0", "1"),
                sliders.contains(name),
                false
        );
        options.put(name, option);
        MainMod.LOGGER.warn("[ShaderOptions] Added fallback binary option {} because shaders.properties references it but shader source scanning did not discover it", name);
    }

    private static boolean propertiesMention(Properties properties, String token) {
        if (properties == null || token == null || token.isBlank()) {
            return false;
        }
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if ((key != null && key.contains(token)) || (value != null && value.contains(token))) {
                return true;
            }
        }
        return false;
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

    private static Set<String> parsePropertyOptionNames(Properties properties, Set<String> sliders) {
        Set<String> result = new HashSet<>(sliders);
        if (properties == null) {
            return result;
        }

        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            addSymbols(result, key);
            addSymbols(result, value);
        }
        result.remove("screen");
        result.remove("profile");
        result.remove("program");
        result.remove("sliders");
        result.remove("true");
        result.remove("false");
        return result;
    }

    private static void addSymbols(Set<String> result, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Matcher matcher = SYMBOL_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = matcher.group();
            if (!token.equals("empty")) {
                result.add(token);
            }
        }
    }

    private static void scanShaderFile(
            ShaderPack pack,
            String path,
            Set<String> sliders,
            Set<String> propertyOptions,
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
                            scanShaderFile(pack, includePath, sliders, propertyOptions, options, visitedFiles);
                        }
                        continue;
                    }

                    DefineOption define = parseDefine(line);
                    if (define != null) {
                        addOption(define, sliders, propertyOptions, options);
                        continue;
                    }

                    DefineOption constant = parseConst(line);
                    if (constant != null) {
                        addOption(constant, sliders, propertyOptions, options);
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderOptions] Failed to scan shader options in {}", path, e);
        }
    }

    private static void addOption(DefineOption define, Set<String> sliders, Set<String> propertyOptions, Map<String, ShaderOption> options) {
        String name = define.name();
        if (define.choices() == null && !propertyOptions.contains(name)) {
            return;
        }
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

        String name = tokens[1];
        if (!SYMBOL_PATTERN.matcher(name).matches()) {
            return null;
        }

        String value = tokens.length >= 3 && !tokens[2].isBlank() ? tokens[2].trim().split("\\s+")[0] : null;
        return new DefineOption(name, value, choices, !commented);
    }

    private static DefineOption parseConst(String line) {
        String choices = parseChoiceComment(line);

        int commentStart = line.indexOf("//");
        String constPart = commentStart >= 0 ? line.substring(0, commentStart) : line;
        Matcher matcher = Pattern
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
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private record DefineOption(String name, String value, String choices, boolean enabled) {
    }
}
