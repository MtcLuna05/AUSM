package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

final class ShaderPropertiesPreprocessor {

    private ShaderPropertiesPreprocessor() {
    }

    static Properties load(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options) {
        Properties properties = new Properties();
        if (!pack.hasResource(layout.propertiesPath())) {
            return properties;
        }

        try (InputStream stream = pack.getResourceAsStream(layout.propertiesPath())) {
            if (stream == null) {
                return properties;
            }

            StringBuilder activeProperties = new StringBuilder();
            Deque<ConditionFrame> conditions = new ArrayDeque<>();
            boolean enabled = true;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("#ifdef ")) {
                        boolean condition = isDefined(trimmed.substring("#ifdef ".length()).trim(), options);
                        conditions.push(new ConditionFrame(enabled, condition, condition));
                        enabled = enabled && condition;
                        continue;
                    }
                    if (trimmed.startsWith("#ifndef ")) {
                        boolean condition = !isDefined(trimmed.substring("#ifndef ".length()).trim(), options);
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

                    if (enabled) {
                        activeProperties.append(line).append('\n');
                    }
                }
            }

            properties.load(new java.io.StringReader(activeProperties.toString()));
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderProperties] Failed to read {}", layout.propertiesPath(), e);
        }
        return properties;
    }

    private static boolean evaluate(String expression, ShaderOptions options) {
        return new Parser(expression, options).parse();
    }

    private static boolean isDefined(String symbol, ShaderOptions options) {
        if (symbol.equals("MC_VERSION")
                || symbol.equals("IS_IRIS")
                || symbol.equals("IRIS_VERSION")
                || symbol.equals("IRIS_FEATURE_CUSTOM_IMAGES")
                || symbol.equals("IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE")) {
            return true;
        }
        ShaderOption option = options.get(symbol);
        return option != null && option.asBoolean();
    }

    private static double numericValue(String symbol, ShaderOptions options) {
        if (symbol.equals("MC_VERSION")) {
            return 11202.0;
        }
        if (symbol.equals("IRIS_VERSION")) {
            return 10800.0;
        }
        if (symbol.equals("IS_IRIS")
                || symbol.equals("IRIS_FEATURE_CUSTOM_IMAGES")
                || symbol.equals("IRIS_FEATURE_BLOCK_EMISSION_ATTRIBUTE")) {
            return 1.0;
        }

        ShaderOption option = options.get(symbol);
        if (option == null || option.value() == null || option.value().isBlank()) {
            return isDefined(symbol, options) ? 1.0 : 0.0;
        }

        try {
            return Double.parseDouble(option.value());
        } catch (NumberFormatException e) {
            return option.asBoolean() ? 1.0 : 0.0;
        }
    }

    private record ConditionFrame(boolean parentEnabled, boolean conditionEnabled, boolean branchTaken) {
    }

    private static final class Parser {
        private final String expression;
        private final ShaderOptions options;
        private int index;

        private Parser(String expression, ShaderOptions options) {
            this.expression = expression;
            this.options = options;
        }

        private boolean parse() {
            boolean value = parseOr();
            skipWhitespace();
            return value;
        }

        private boolean parseOr() {
            boolean value = parseAnd();
            while (true) {
                skipWhitespace();
                if (!consume("||")) {
                    return value;
                }
                value = value || parseAnd();
            }
        }

        private boolean parseAnd() {
            boolean value = parseUnary();
            while (true) {
                skipWhitespace();
                if (!consume("&&")) {
                    return value;
                }
                value = value && parseUnary();
            }
        }

        private boolean parseUnary() {
            skipWhitespace();
            if (consume("!")) {
                return !parseUnary();
            }
            if (consume("(")) {
                boolean value = parseOr();
                consume(")");
                return value;
            }
            if (consumeWord("defined")) {
                skipWhitespace();
                if (consume("(")) {
                    String symbol = readSymbol();
                    consume(")");
                    return isDefined(symbol, options);
                }
                return isDefined(readSymbol(), options);
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            String symbol = readSymbol();
            if (symbol.isEmpty()) {
                return false;
            }

            skipWhitespace();
            if (consume(">=")) {
                return numericValue(symbol, options) >= readNumberOrSymbol();
            }
            if (consume("<=")) {
                return numericValue(symbol, options) <= readNumberOrSymbol();
            }
            if (consume("==")) {
                return numericValue(symbol, options) == readNumberOrSymbol();
            }
            if (consume("!=")) {
                return numericValue(symbol, options) != readNumberOrSymbol();
            }
            if (consume(">")) {
                return numericValue(symbol, options) > readNumberOrSymbol();
            }
            if (consume("<")) {
                return numericValue(symbol, options) < readNumberOrSymbol();
            }

            return switch (symbol.toLowerCase()) {
                case "true", "on" -> true;
                case "false", "off" -> false;
                default -> isDefined(symbol, options);
            };
        }

        private double readNumberOrSymbol() {
            skipWhitespace();
            String token = readSymbol();
            if (token.isEmpty()) {
                return 0.0;
            }
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException e) {
                return numericValue(token, options);
            }
        }

        private String readSymbol() {
            skipWhitespace();
            int start = index;
            while (index < expression.length()) {
                char ch = expression.charAt(index);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-') {
                    index++;
                } else {
                    break;
                }
            }
            return expression.substring(start, index);
        }

        private boolean consumeWord(String word) {
            skipWhitespace();
            int end = index + word.length();
            if (end <= expression.length() && expression.substring(index, end).equals(word)) {
                if (end == expression.length() || !Character.isLetterOrDigit(expression.charAt(end))) {
                    index = end;
                    return true;
                }
            }
            return false;
        }

        private boolean consume(String token) {
            skipWhitespace();
            if (expression.startsWith(token, index)) {
                index += token.length();
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }
    }
}
