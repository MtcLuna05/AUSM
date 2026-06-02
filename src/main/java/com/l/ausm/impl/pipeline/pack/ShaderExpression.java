package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import java.util.function.Predicate;

final class ShaderExpression {

    private ShaderExpression() {
    }

    static boolean evaluate(String expression, Predicate<String> symbolResolver) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        return new Parser(expression, symbolResolver).parse();
    }

    private static final class Parser {
        private final String expression;
        private final Predicate<String> symbolResolver;
        private int index;

        private Parser(String expression, Predicate<String> symbolResolver) {
            this.expression = expression;
            this.symbolResolver = symbolResolver;
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
                boolean right = parseAnd();
                value = value || right;
            }
        }

        private boolean parseAnd() {
            boolean value = parseUnary();
            while (true) {
                skipWhitespace();
                if (!consume("&&")) {
                    return value;
                }
                boolean right = parseUnary();
                value = value && right;
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
            String symbol = readSymbol();
            return switch (symbol.toLowerCase()) {
                case "true", "on" -> true;
                case "false", "off", "" -> false;
                default -> symbolResolver.test(symbol);
            };
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
