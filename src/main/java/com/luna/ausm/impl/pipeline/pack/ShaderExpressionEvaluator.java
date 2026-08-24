package com.luna.ausm.impl.pipeline.pack;

import com.luna.ausm.api.pipeline.pack.ShaderOptions;
import java.util.Map;

public final class ShaderExpressionEvaluator {
    private ShaderExpressionEvaluator() {
    }

    public static boolean evaluate(String expression, ShaderOptions options) {
        return evaluate(expression, ShaderEnvironmentDefines.defineMap(options));
    }

    public static boolean evaluate(String expression, Map<String, String> defines) {
        try {
            Parser parser = new Parser(expression, defines);
            boolean value = parser.parseOr();
            parser.skipWhitespace();
            return value;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final class Parser {
        private final String expression;
        private final Map<String, String> defines;
        private int index;

        private Parser(String expression, Map<String, String> defines) {
            this.expression = expression == null ? "" : expression;
            this.defines = defines;
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
                    return defines.containsKey(symbol);
                }
                return defines.containsKey(readSymbol());
            }
            if (consumeWord("in")) {
                skipWhitespace();
                if (!consume("(")) {
                    return false;
                }
                String leftToken = readValueToken();
                double left = numericValue(leftToken);
                boolean value = false;
                while (consume(",")) {
                    value |= left == numericValue(readValueToken());
                }
                consume(")");
                return value;
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            String leftToken = readValueToken();
            double left = numericValue(leftToken);
            skipWhitespace();
            if (consumeWord("in")) {
                return parseInList(left);
            }
            if (consume(">=")) {
                return left >= numericValue(readValueToken());
            }
            if (consume("<=")) {
                return left <= numericValue(readValueToken());
            }
            if (consume("==")) {
                return left == numericValue(readValueToken());
            }
            if (consume("!=")) {
                return left != numericValue(readValueToken());
            }
            if (consume(">")) {
                return left > numericValue(readValueToken());
            }
            if (consume("<")) {
                return left < numericValue(readValueToken());
            }
            return truthy(leftToken, left);
        }

        private boolean parseInList(double left) {
            skipWhitespace();
            if (!consume("(")) {
                return false;
            }
            boolean value = false;
            do {
                value |= left == numericValue(readValueToken());
                skipWhitespace();
            } while (consume(","));
            consume(")");
            return value;
        }

        private double numericValue(String token) {
            if (token.isEmpty()) {
                return 0.0;
            }
            try {
                if (token.startsWith("0x") || token.startsWith("0X") || token.startsWith("-0x") || token.startsWith("-0X")) {
                    return Long.decode(token);
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored) {
                String value = defines.get(token);
                return value == null ? switch (token.toLowerCase()) {
                    case "true", "on" -> 1.0;
                    default -> 0.0;
                } : ShaderEnvironmentDefines.numericValue(value, defines);
            }
        }

        private boolean truthy(String token, double numericValue) {
            if (token.isEmpty()) {
                return false;
            }
            return switch (token.toLowerCase()) {
                case "true", "on" -> true;
                case "false", "off" -> false;
                default -> numericValue != 0.0 || defines.containsKey(token);
            };
        }

        private String readValueToken() {
            skipWhitespace();
            if (consume("(")) {
                boolean value = parseOr();
                consume(")");
                return value ? "1" : "0";
            }
            return readSymbol();
        }

        private String readSymbol() {
            skipWhitespace();
            int start = index;
            while (index < expression.length()) {
                char ch = expression.charAt(index);
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-' || ch == '+') {
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
                if (end == expression.length() || (!Character.isLetterOrDigit(expression.charAt(end)) && expression.charAt(end) != '_')) {
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
