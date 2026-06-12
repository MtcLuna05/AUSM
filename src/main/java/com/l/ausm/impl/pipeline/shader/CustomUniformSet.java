package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small Iris-style custom uniform container.
 *
 * <p>Iris evaluates these through its expression system. This 1.12 backport
 * supports the scalar/vector expression subset needed by most pack metadata:
 * literals, arithmetic, parentheses, booleans, vec constructors, and variables
 * declared with {@code variable.<type>.<name>}.</p>
 */
public final class CustomUniformSet {
    private final Map<String, String> expressions;
    private final List<CustomUniform> uniforms;
    private final Map<String, String> variables;
    private final Map<Integer, SmoothState> smoothStates = new HashMap<>();

    public CustomUniformSet(
            Map<String, String> expressions,
            List<CustomUniform> uniforms,
            Map<String, String> variables
    ) {
        this.expressions = expressions;
        this.uniforms = uniforms;
        this.variables = variables;
    }

    public static CustomUniformSet empty() {
        return new CustomUniformSet(Map.of(), List.of(), Map.of());
    }

    public Map<String, String> expressions() {
        return expressions;
    }

    public List<CustomUniform> uniforms() {
        return uniforms;
    }

    public Map<String, String> variables() {
        return variables;
    }

    public static CustomUniformSet parse(Map<String, String> expressions) {
        if (expressions.isEmpty()) {
            return empty();
        }

        Map<String, String> variables = new LinkedHashMap<>();
        expressions.forEach((key, expression) -> {
            ParsedKey parsed = ParsedKey.parse(key);
            if (parsed == null) {
                return;
            }
            if (!parsed.uniform()) {
                variables.put(parsed.name(), expression);
            }
        });

        return new CustomUniformSet(Map.copyOf(expressions), List.of(), Map.copyOf(variables));
    }

    public void upload(ShaderProgram program, Map<String, float[]> builtins) {
        if (expressions.isEmpty()) {
            return;
        }

        List<RawUniform> rawUniforms = new ArrayList<>();
        Map<String, String> variableExpressions = new LinkedHashMap<>();
        expressions.forEach((key, expression) -> {
            ParsedKey parsed = ParsedKey.parse(key);
            if (parsed == null) {
                return;
            }
            if (parsed.uniform()) {
                rawUniforms.add(new RawUniform(parsed.type(), parsed.name(), expression));
            } else {
                variableExpressions.put(parsed.name(), expression);
            }
        });

        Map<String, float[]> resolved = new LinkedHashMap<>(builtins);
        resolved.putAll(resolveVariables(variableExpressions, resolved, smoothStates));
        for (RawUniform rawUniform : rawUniforms) {
            CustomUniform uniform = CustomUniform.parse(rawUniform.type(), rawUniform.name(), rawUniform.expression(), resolved, smoothStates);
            if (uniform != null) {
                uniform.upload(program);
            }
        }
    }

    private record RawUniform(String type, String name, String expression) {
    }

    private record ParsedKey(boolean uniform, String type, String name) {
        private static ParsedKey parse(String key) {
            String prefix;
            boolean uniform;
            if (key.startsWith("uniform.")) {
                prefix = "uniform.";
                uniform = true;
            } else if (key.startsWith("variable.")) {
                prefix = "variable.";
                uniform = false;
            } else {
                return null;
            }

            String suffix = key.substring(prefix.length());
            int separator = suffix.indexOf('.');
            if (separator <= 0 || separator >= suffix.length() - 1) {
                MainMod.LOGGER.warn("[CustomUniforms] Ignoring malformed custom uniform key: {}", key);
                return null;
            }
            return new ParsedKey(uniform, suffix.substring(0, separator), suffix.substring(separator + 1));
        }
    }

    private static Map<String, float[]> resolveVariables(
            Map<String, String> variables,
            Map<String, float[]> baseVariables,
            Map<Integer, SmoothState> smoothStates
    ) {
        if (variables.isEmpty()) {
            return Map.of();
        }
        Map<String, float[]> resolved = new LinkedHashMap<>(baseVariables);
        Map<String, String> unresolved = new LinkedHashMap<>(variables);
        boolean progressed;
        do {
            progressed = false;
            var iterator = unresolved.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                float[] values = ExpressionEvaluator.tryEvaluateAny(entry.getValue(), resolved, smoothStates);
                if (values.length == 0) {
                    continue;
                }
                resolved.put(entry.getKey(), values);
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !unresolved.isEmpty());

        Map<String, float[]> ownVariables = new LinkedHashMap<>(resolved);
        baseVariables.keySet().forEach(ownVariables::remove);
        return Map.copyOf(ownVariables);
    }

    public record CustomUniform(String type, String name, float[] values) {
        private static CustomUniform parse(
                String type,
                String name,
                String expression,
                Map<String, float[]> variables,
                Map<Integer, SmoothState> smoothStates
        ) {
            int expected = expectedValues(type);
            float[] values = ExpressionEvaluator.tryEvaluate(expression, expected, variables, smoothStates);
            if (values.length == 0) {
                return null;
            }
            if (expected > 0 && values.length < expected) {
                MainMod.LOGGER.warn("[CustomUniforms] Ignoring custom uniform '{}' with too few values: {}", name, expression);
                return null;
            }
            return new CustomUniform(type, name, values);
        }

        private void upload(ShaderProgram program) {
            int location = program.getUniformLocation(name);
            if (location == -1) {
                return;
            }

            switch (type) {
                case "bool", "int" -> GL20.glUniform1i(location, (int) values[0]);
                case "float" -> GL20.glUniform1f(location, values[0]);
                case "vec2" -> GL20.glUniform2f(location, values[0], values[1]);
                case "vec3" -> GL20.glUniform3f(location, values[0], values[1], values[2]);
                case "vec4" -> GL20.glUniform4f(location, values[0], values[1], values[2], values[3]);
                default -> {
                }
            }
        }

        private static int expectedValues(String type) {
            return switch (type) {
                case "bool", "int", "float" -> 1;
                case "vec2" -> 2;
                case "vec3" -> 3;
                case "vec4" -> 4;
                default -> -1;
            };
        }
    }

    private static final class ExpressionEvaluator {
        private ExpressionEvaluator() {
        }

        static float[] tryEvaluateAny(String expression, Map<String, float[]> variables) {
            return tryEvaluate(expression, -1, variables, new HashMap<>());
        }

        static float[] tryEvaluateAny(
                String expression,
                Map<String, float[]> variables,
                Map<Integer, SmoothState> smoothStates
        ) {
            return tryEvaluate(expression, -1, variables, smoothStates);
        }

        static float[] tryEvaluate(String expression, int expectedValues, Map<String, float[]> variables) {
            return tryEvaluate(expression, expectedValues, variables, new HashMap<>());
        }

        static float[] tryEvaluate(
                String expression,
                int expectedValues,
                Map<String, float[]> variables,
                Map<Integer, SmoothState> smoothStates
        ) {
            if (expression == null) {
                return new float[0];
            }
            String trimmed = expression.trim();
            if (trimmed.isEmpty()) {
                return new float[0];
            }

            float[] directVariable = variables.get(trimmed);
            if (directVariable != null && (expectedValues <= 0 || directVariable.length == expectedValues)) {
                return directVariable.clone();
            }

            float[] vector = tryEvaluateVector(trimmed, variables, smoothStates);
            if (vector.length > 0) {
                return expectedValues <= 0 || vector.length >= expectedValues ? vector : new float[0];
            }

            try {
                return new float[]{new ScalarParser(trimmed, variables, smoothStates).parse()};
            } catch (IllegalArgumentException e) {
                return new float[0];
            }
        }

        private static float[] tryEvaluateVector(
                String expression,
                Map<String, float[]> variables,
                Map<Integer, SmoothState> smoothStates
        ) {
            String body = constructorBody(expression);
            if (body == null && expression.indexOf(',') < 0) {
                return new float[0];
            }

            List<String> parts = splitTopLevel(body == null ? expression : body);
            if (parts.size() <= 1) {
                return new float[0];
            }
            float[] values = new float[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                try {
                    values[i] = new ScalarParser(parts.get(i), variables, smoothStates).parse();
                } catch (IllegalArgumentException e) {
                    return new float[0];
                }
            }
            return values;
        }

        private static String constructorBody(String expression) {
            for (String prefix : List.of("vec2", "vec3", "vec4")) {
                if (expression.startsWith(prefix + "(") && expression.endsWith(")")) {
                    return expression.substring(prefix.length() + 1, expression.length() - 1);
                }
            }
            return null;
        }

        private static List<String> splitTopLevel(String expression) {
            List<String> parts = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for (int i = 0; i < expression.length(); i++) {
                char c = expression.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 0) {
                    parts.add(expression.substring(start, i).trim());
                    start = i + 1;
                }
            }
            parts.add(expression.substring(start).trim());
            return parts;
        }
    }

    private static final class ScalarParser {
        private final String expression;
        private final Map<String, float[]> variables;
        private final Map<Integer, SmoothState> smoothStates;
        private int index;

        private ScalarParser(
                String expression,
                Map<String, float[]> variables,
                Map<Integer, SmoothState> smoothStates
        ) {
            this.expression = expression;
            this.variables = variables;
            this.smoothStates = smoothStates;
        }

        private float parse() {
            float value = parseLogicalOr();
            skipWhitespace();
            if (index != expression.length()) {
                throw new IllegalArgumentException("Unexpected token");
            }
            return value;
        }

        private float parseLogicalOr() {
            float value = parseLogicalAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    float right = parseLogicalAnd();
                    value = truthy(value) || truthy(right) ? 1.0f : 0.0f;
                } else {
                    return value;
                }
            }
        }

        private float parseLogicalAnd() {
            float value = parseComparison();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    float right = parseComparison();
                    value = truthy(value) && truthy(right) ? 1.0f : 0.0f;
                } else {
                    return value;
                }
            }
        }

        private float parseComparison() {
            float value = parseExpression();
            while (true) {
                skipWhitespace();
                if (match(">=")) {
                    value = value >= parseExpression() ? 1.0f : 0.0f;
                } else if (match("<=")) {
                    value = value <= parseExpression() ? 1.0f : 0.0f;
                } else if (match("==")) {
                    value = value == parseExpression() ? 1.0f : 0.0f;
                } else if (match("!=")) {
                    value = value != parseExpression() ? 1.0f : 0.0f;
                } else if (match('>')) {
                    value = value > parseExpression() ? 1.0f : 0.0f;
                } else if (match('<')) {
                    value = value < parseExpression() ? 1.0f : 0.0f;
                } else {
                    return value;
                }
            }
        }

        private float parseExpression() {
            float value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private float parseTerm() {
            float value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    value /= parseFactor();
                } else if (match('%')) {
                    value %= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private float parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('!')) {
                return truthy(parseFactor()) ? 0.0f : 1.0f;
            }
            if (match('(')) {
                float value = parseLogicalOr();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return value;
            }
            if (index >= expression.length()) {
                throw new IllegalArgumentException("Unexpected end of expression");
            }
            char c = expression.charAt(index);
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }
            if (Character.isJavaIdentifierStart(c)) {
                return parseIdentifierOrFunction();
            }
            throw new IllegalArgumentException("Unexpected token");
        }

        private float parseNumber() {
            int start = index;
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (Character.isDigit(c) || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    if ((c == '+' || c == '-') && index > start) {
                        char previous = expression.charAt(index - 1);
                        if (previous != 'e' && previous != 'E') {
                            break;
                        }
                    }
                    index++;
                } else {
                    break;
                }
            }
            return Float.parseFloat(expression.substring(start, index));
        }

        private float parseIdentifierOrFunction() {
            int start = index;
            index++;
            while (index < expression.length()) {
                char c = expression.charAt(index);
                if (!Character.isJavaIdentifierPart(c) && c != '.') {
                    break;
                }
                index++;
            }
            String identifier = expression.substring(start, index);
            skipWhitespace();
            if (match('(')) {
                List<Float> arguments = parseArguments();
                return evaluateFunction(identifier, arguments);
            }
            if ("true".equalsIgnoreCase(identifier)) {
                return 1.0f;
            }
            if ("false".equalsIgnoreCase(identifier)) {
                return 0.0f;
            }
            float[] value = variables.get(identifier);
            if (value == null || value.length != 1) {
                throw new IllegalArgumentException("Unknown scalar variable");
            }
            return value[0];
        }

        private List<Float> parseArguments() {
            List<Float> arguments = new ArrayList<>();
            skipWhitespace();
            if (match(')')) {
                return arguments;
            }
            while (true) {
                arguments.add(parseLogicalOr());
                skipWhitespace();
                if (match(')')) {
                    return arguments;
                }
                if (!match(',')) {
                    throw new IllegalArgumentException("Missing function argument separator");
                }
            }
        }

        private float evaluateFunction(String identifier, List<Float> arguments) {
            return switch (identifier) {
                case "if" -> {
                    if (arguments.size() < 3 || arguments.size() % 2 == 0) {
                        throw new IllegalArgumentException("Function if expects condition/value pairs plus a fallback");
                    }
                    float result = arguments.get(arguments.size() - 1);
                    for (int i = 0; i < arguments.size() - 1; i += 2) {
                        if (truthy(arguments.get(i))) {
                            result = arguments.get(i + 1);
                            break;
                        }
                    }
                    yield result;
                }
                case "min" -> {
                    requireArguments(identifier, arguments, 2);
                    yield Math.min(arguments.get(0), arguments.get(1));
                }
                case "max" -> {
                    requireArguments(identifier, arguments, 2);
                    yield Math.max(arguments.get(0), arguments.get(1));
                }
                case "clamp" -> {
                    requireArguments(identifier, arguments, 3);
                    yield Math.max(arguments.get(1), Math.min(arguments.get(2), arguments.get(0)));
                }
                case "in" -> {
                    if (arguments.size() < 2) {
                        throw new IllegalArgumentException("Function in expects at least two arguments");
                    }
                    float needle = arguments.get(0);
                    boolean found = false;
                    for (int i = 1; i < arguments.size(); i++) {
                        if (Math.abs(needle - arguments.get(i)) <= 0.000001f) {
                            found = true;
                            break;
                        }
                    }
                    yield found ? 1.0f : 0.0f;
                }
                case "smooth" -> {
                    if (arguments.size() != 4) {
                        throw new IllegalArgumentException("Function smooth expects id, value, fadeUp, fadeDown");
                    }
                    yield smooth(arguments.get(0), arguments.get(1), arguments.get(2), arguments.get(3));
                }
                case "fmod", "mod" -> {
                    requireArguments(identifier, arguments, 2);
                    yield arguments.get(0) % arguments.get(1);
                }
                case "abs" -> {
                    requireArguments(identifier, arguments, 1);
                    yield Math.abs(arguments.get(0));
                }
                case "sqrt" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.sqrt(arguments.get(0));
                }
                case "floor" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.floor(arguments.get(0));
                }
                case "ceil" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.ceil(arguments.get(0));
                }
                case "round" -> {
                    requireArguments(identifier, arguments, 1);
                    yield Math.round(arguments.get(0));
                }
                case "fract" -> {
                    requireArguments(identifier, arguments, 1);
                    float value = arguments.get(0);
                    yield value - (float) Math.floor(value);
                }
                case "sign" -> {
                    requireArguments(identifier, arguments, 1);
                    yield Math.signum(arguments.get(0));
                }
                case "pow" -> {
                    requireArguments(identifier, arguments, 2);
                    yield (float) Math.pow(arguments.get(0), arguments.get(1));
                }
                case "log" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.log(arguments.get(0));
                }
                case "atan" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.atan(arguments.get(0));
                }
                case "sin" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.sin(arguments.get(0));
                }
                case "cos" -> {
                    requireArguments(identifier, arguments, 1);
                    yield (float) Math.cos(arguments.get(0));
                }
                default -> throw new IllegalArgumentException("Unknown function");
            };
        }

        private float smooth(float rawId, float target, float fadeUp, float fadeDown) {
            int id = Math.round(rawId);
            float currentFrame = scalarVariable("frameCounter", 0.0f);
            float frameTime = scalarVariable("frameTime", 0.05f);
            SmoothState state = smoothStates.get(id);
            if (state == null) {
                smoothStates.put(id, new SmoothState(target, currentFrame));
                return target;
            }

            if (state.frame == currentFrame) {
                return state.value;
            }

            float fade = target > state.value ? fadeUp : fadeDown;
            float next;
            if (fade <= 0.0f || frameTime <= 0.0f) {
                next = target;
            } else {
                float alpha = Math.min(1.0f, frameTime / fade);
                next = state.value + (target - state.value) * alpha;
            }
            state.value = next;
            state.frame = currentFrame;
            return next;
        }

        private float scalarVariable(String name, float fallback) {
            float[] value = variables.get(name);
            if (value == null || value.length == 0) {
                return fallback;
            }
            return value[0];
        }

        private void requireArguments(String identifier, List<Float> arguments, int count) {
            if (arguments.size() != count) {
                throw new IllegalArgumentException("Function " + identifier + " expects " + count + " arguments");
            }
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (index < expression.length() && expression.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private boolean match(String expected) {
            skipWhitespace();
            if (expression.startsWith(expected, index)) {
                index += expected.length();
                return true;
            }
            return false;
        }

        private boolean truthy(float value) {
            return Math.abs(value) > 0.000001f;
        }

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }
    }

    private static final class SmoothState {
        private float value;
        private float frame;

        private SmoothState(float value, float frame) {
            this.value = value;
            this.frame = frame;
        }
    }
}
