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
    private static final float[] EMPTY_VALUES = new float[0];

    private final Map<String, String> expressions;
    private final List<RawUniform> rawUniforms;
    private final Map<String, String> variables;
    private final Map<String, CompiledExpression> compiledVariables;
    private final Map<Integer, SmoothState> smoothStates = new HashMap<>();
    private final Map<String, float[]> resolvedScratch = new HashMap<>();
    private final Map<String, CompiledExpression> unresolvedScratch = new LinkedHashMap<>();
    private final Map<String, float[]> cachedUniformValues = new HashMap<>();
    private int cachedUniformFrame = Integer.MIN_VALUE;

    private CustomUniformSet(
            Map<String, String> expressions,
            List<RawUniform> rawUniforms,
            Map<String, String> variables,
            Map<String, CompiledExpression> compiledVariables
    ) {
        this.expressions = expressions;
        this.rawUniforms = rawUniforms;
        this.variables = variables;
        this.compiledVariables = compiledVariables;
    }

    public static CustomUniformSet empty() {
        return new CustomUniformSet(Map.of(), List.of(), Map.of(), Map.of());
    }

    public Map<String, String> expressions() {
        return expressions;
    }

    public List<CustomUniform> uniforms() {
        return List.of();
    }

    public Map<String, String> variables() {
        return variables;
    }

    public boolean isEmpty() {
        return expressions.isEmpty();
    }

    public static CustomUniformSet parse(Map<String, String> expressions) {
        if (expressions.isEmpty()) {
            return empty();
        }

        List<RawUniform> rawUniforms = new ArrayList<>();
        Map<String, String> variables = new LinkedHashMap<>();
        Map<String, CompiledExpression> compiledVariables = new LinkedHashMap<>();
        expressions.forEach((key, expression) -> {
            ParsedKey parsed = ParsedKey.parse(key);
            if (parsed == null) {
                return;
            }
            CompiledExpression compiled = ExpressionEvaluator.compile(expression);
            if (compiled == null) {
                MainMod.LOGGER.warn("[CustomUniforms] Ignoring malformed custom expression '{}': {}", key, expression);
                return;
            }
            if (parsed.uniform()) {
                rawUniforms.add(new RawUniform(parsed.type(), parsed.name(), expression, expectedValues(parsed.type()), compiled));
            } else {
                variables.put(parsed.name(), expression);
                compiledVariables.put(parsed.name(), compiled);
            }
        });

        return new CustomUniformSet(
                Map.copyOf(expressions),
                List.copyOf(rawUniforms),
                Map.copyOf(variables),
                Map.copyOf(compiledVariables)
        );
    }

    public void upload(ShaderProgram program, Map<String, float[]> builtins) {
        if (rawUniforms.isEmpty()) {
            return;
        }

        Map<String, float[]> valuesByName = uniformValuesForFrame(builtins);
        for (RawUniform rawUniform : rawUniforms) {
            float[] values = valuesByName.getOrDefault(rawUniform.name(), EMPTY_VALUES);
            uploadUniform(program, rawUniform, values);
        }
    }

    private Map<String, float[]> uniformValuesForFrame(Map<String, float[]> builtins) {
        int frame = scalarInt(builtins, "frameCounter", Integer.MIN_VALUE);
        if (frame != Integer.MIN_VALUE && frame == cachedUniformFrame) {
            return cachedUniformValues;
        }

        Map<String, float[]> resolved = builtins;
        if (!compiledVariables.isEmpty()) {
            resolvedScratch.clear();
            resolvedScratch.putAll(builtins);
            resolveVariablesInto(compiledVariables, resolvedScratch, unresolvedScratch, smoothStates);
            resolved = resolvedScratch;
        }

        cachedUniformValues.clear();
        for (RawUniform rawUniform : rawUniforms) {
            float[] values = rawUniform.compiledExpression().evaluate(rawUniform.expectedValues(), resolved, smoothStates);
            if (values.length != 0) {
                cachedUniformValues.put(rawUniform.name(), values);
            }
        }
        cachedUniformFrame = frame;
        return cachedUniformValues;
    }

    private static int scalarInt(Map<String, float[]> variables, String name, int fallback) {
        float[] value = variables.get(name);
        if (value == null || value.length == 0) {
            return fallback;
        }
        return Math.round(value[0]);
    }

    private record RawUniform(
            String type,
            String name,
            String expression,
            int expectedValues,
            CompiledExpression compiledExpression
    ) {
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

    private static void resolveVariablesInto(
            Map<String, CompiledExpression> variables,
            Map<String, float[]> resolved,
            Map<String, CompiledExpression> unresolved,
            Map<Integer, SmoothState> smoothStates
    ) {
        if (variables.isEmpty()) {
            return;
        }
        unresolved.clear();
        unresolved.putAll(variables);
        boolean progressed;
        do {
            progressed = false;
            var iterator = unresolved.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, CompiledExpression> entry = iterator.next();
                float[] values = entry.getValue().evaluateAny(resolved, smoothStates);
                if (values.length == 0) {
                    continue;
                }
                resolved.put(entry.getKey(), values);
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !unresolved.isEmpty());
    }

    private static void uploadUniform(ShaderProgram program, RawUniform rawUniform, float[] values) {
        if (values.length == 0) {
            return;
        }
        int expected = rawUniform.expectedValues();
        if (expected > 0 && values.length < expected) {
            MainMod.LOGGER.warn("[CustomUniforms] Ignoring custom uniform '{}' with too few values: {}",
                    rawUniform.name(),
                    rawUniform.expression());
            return;
        }

        int location = program.getUniformLocation(rawUniform.name());
        if (location == -1) {
            return;
        }

        switch (rawUniform.type()) {
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

    public record CustomUniform(String type, String name, float[] values) {
    }

    private interface CompiledExpression {
        default float[] evaluateAny(Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates) {
            return evaluate(-1, variables, smoothStates);
        }

        float[] evaluate(int expectedValues, Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates);
    }

    private static final class DirectVariableExpression implements CompiledExpression {
        private final String name;

        private DirectVariableExpression(String name) {
            this.name = name;
        }

        @Override
        public float[] evaluate(int expectedValues, Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates) {
            float[] value = variables.get(name);
            if (value == null || (expectedValues > 0 && value.length != expectedValues)) {
                return EMPTY_VALUES;
            }
            return value;
        }
    }

    private static final class ScalarExpression implements CompiledExpression {
        private final ScalarNode node;

        private ScalarExpression(ScalarNode node) {
            this.node = node;
        }

        @Override
        public float[] evaluate(int expectedValues, Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates) {
            float value = node.evaluate(new EvalContext(variables, smoothStates));
            return Float.isNaN(value) ? EMPTY_VALUES : new float[]{value};
        }
    }

    private static final class VectorExpression implements CompiledExpression {
        private final List<ScalarNode> nodes;

        private VectorExpression(List<ScalarNode> nodes) {
            this.nodes = nodes;
        }

        @Override
        public float[] evaluate(int expectedValues, Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates) {
            if (expectedValues > 0 && nodes.size() < expectedValues) {
                return EMPTY_VALUES;
            }
            EvalContext context = new EvalContext(variables, smoothStates);
            float[] values = new float[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                values[i] = nodes.get(i).evaluate(context);
                if (Float.isNaN(values[i])) {
                    return EMPTY_VALUES;
                }
            }
            return values;
        }
    }

    private static final class ExpressionEvaluator {
        private ExpressionEvaluator() {
        }

        static CompiledExpression compile(String expression) {
            if (expression == null) {
                return null;
            }
            String trimmed = expression.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (isDirectVariableName(trimmed)) {
                return new DirectVariableExpression(trimmed);
            }
            try {
                String body = constructorBody(trimmed);
                List<String> parts = body != null ? splitTopLevel(body) : splitTopLevel(trimmed);
                if (body != null || parts.size() > 1) {
                    if (parts.size() <= 1) {
                        return null;
                    }
                    List<ScalarNode> nodes = new ArrayList<>(parts.size());
                    for (String part : parts) {
                        nodes.add(new ScalarExpressionParser(part).parse());
                    }
                    return new VectorExpression(List.copyOf(nodes));
                }
                return new ScalarExpression(new ScalarExpressionParser(trimmed).parse());
            } catch (ParseException | NumberFormatException e) {
                return null;
            }
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
                    if (depth < 0) {
                        throw new ParseException();
                    }
                } else if (c == ',' && depth == 0) {
                    parts.add(expression.substring(start, i).trim());
                    start = i + 1;
                }
            }
            if (depth != 0) {
                throw new ParseException();
            }
            parts.add(expression.substring(start).trim());
            return parts;
        }

        private static boolean isDirectVariableName(String expression) {
            if ("true".equalsIgnoreCase(expression) || "false".equalsIgnoreCase(expression)) {
                return false;
            }
            if (expression.isEmpty() || !Character.isJavaIdentifierStart(expression.charAt(0))) {
                return false;
            }
            for (int i = 1; i < expression.length(); i++) {
                char c = expression.charAt(i);
                if (!Character.isJavaIdentifierPart(c) && c != '.') {
                    return false;
                }
            }
            return true;
        }
    }

    private interface ScalarNode {
        float evaluate(EvalContext context);
    }

    private record ConstantNode(float value) implements ScalarNode {
        @Override
        public float evaluate(EvalContext context) {
            return value;
        }
    }

    private record VariableNode(String name) implements ScalarNode {
        @Override
        public float evaluate(EvalContext context) {
            float[] value = context.variables().get(name);
            return value == null || value.length != 1 ? Float.NaN : value[0];
        }
    }

    private record UnaryNode(char operator, ScalarNode node) implements ScalarNode {
        @Override
        public float evaluate(EvalContext context) {
            float value = node.evaluate(context);
            if (Float.isNaN(value)) {
                return Float.NaN;
            }
            return switch (operator) {
                case '-' -> -value;
                case '!' -> truthy(value) ? 0.0f : 1.0f;
                default -> value;
            };
        }
    }

    private record BinaryNode(String operator, ScalarNode left, ScalarNode right) implements ScalarNode {
        @Override
        public float evaluate(EvalContext context) {
            float leftValue = left.evaluate(context);
            float rightValue = right.evaluate(context);
            if (Float.isNaN(leftValue) || Float.isNaN(rightValue)) {
                return Float.NaN;
            }
            return switch (operator) {
                case "||" -> truthy(leftValue) || truthy(rightValue) ? 1.0f : 0.0f;
                case "&&" -> truthy(leftValue) && truthy(rightValue) ? 1.0f : 0.0f;
                case ">=" -> leftValue >= rightValue ? 1.0f : 0.0f;
                case "<=" -> leftValue <= rightValue ? 1.0f : 0.0f;
                case "==" -> leftValue == rightValue ? 1.0f : 0.0f;
                case "!=" -> leftValue != rightValue ? 1.0f : 0.0f;
                case ">" -> leftValue > rightValue ? 1.0f : 0.0f;
                case "<" -> leftValue < rightValue ? 1.0f : 0.0f;
                case "+" -> leftValue + rightValue;
                case "-" -> leftValue - rightValue;
                case "*" -> leftValue * rightValue;
                case "/" -> leftValue / rightValue;
                case "%" -> leftValue % rightValue;
                default -> Float.NaN;
            };
        }
    }

    private record FunctionNode(String identifier, List<ScalarNode> arguments) implements ScalarNode {
        @Override
        public float evaluate(EvalContext context) {
            float[] values = new float[arguments.size()];
            for (int i = 0; i < arguments.size(); i++) {
                values[i] = arguments.get(i).evaluate(context);
                if (Float.isNaN(values[i])) {
                    return Float.NaN;
                }
            }
            return switch (identifier) {
                case "if" -> evaluateIf(values);
                case "min" -> values.length == 2 ? Math.min(values[0], values[1]) : Float.NaN;
                case "max" -> values.length == 2 ? Math.max(values[0], values[1]) : Float.NaN;
                case "clamp" -> values.length == 3 ? Math.max(values[1], Math.min(values[2], values[0])) : Float.NaN;
                case "in" -> evaluateIn(values);
                case "smooth" -> values.length == 4 ? context.smooth(values[0], values[1], values[2], values[3]) : Float.NaN;
                case "fmod", "mod" -> values.length == 2 ? values[0] % values[1] : Float.NaN;
                case "abs" -> values.length == 1 ? Math.abs(values[0]) : Float.NaN;
                case "sqrt" -> values.length == 1 ? (float) Math.sqrt(values[0]) : Float.NaN;
                case "floor" -> values.length == 1 ? (float) Math.floor(values[0]) : Float.NaN;
                case "ceil" -> values.length == 1 ? (float) Math.ceil(values[0]) : Float.NaN;
                case "round" -> values.length == 1 ? Math.round(values[0]) : Float.NaN;
                case "fract" -> values.length == 1 ? values[0] - (float) Math.floor(values[0]) : Float.NaN;
                case "sign" -> values.length == 1 ? Math.signum(values[0]) : Float.NaN;
                case "pow" -> values.length == 2 ? (float) Math.pow(values[0], values[1]) : Float.NaN;
                case "log" -> values.length == 1 ? (float) Math.log(values[0]) : Float.NaN;
                case "atan" -> values.length == 1 ? (float) Math.atan(values[0]) : Float.NaN;
                case "sin" -> values.length == 1 ? (float) Math.sin(values[0]) : Float.NaN;
                case "cos" -> values.length == 1 ? (float) Math.cos(values[0]) : Float.NaN;
                default -> Float.NaN;
            };
        }

        private static float evaluateIf(float[] values) {
            if (values.length < 3 || values.length % 2 == 0) {
                return Float.NaN;
            }
            float result = values[values.length - 1];
            for (int i = 0; i < values.length - 1; i += 2) {
                if (truthy(values[i])) {
                    result = values[i + 1];
                    break;
                }
            }
            return result;
        }

        private static float evaluateIn(float[] values) {
            if (values.length < 2) {
                return Float.NaN;
            }
            float needle = values[0];
            for (int i = 1; i < values.length; i++) {
                if (Math.abs(needle - values[i]) <= 0.000001f) {
                    return 1.0f;
                }
            }
            return 0.0f;
        }
    }

    private record EvalContext(Map<String, float[]> variables, Map<Integer, SmoothState> smoothStates) {
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
    }

    private static final class ScalarExpressionParser {
        private final String expression;
        private int index;

        private ScalarExpressionParser(String expression) {
            this.expression = expression;
        }

        private ScalarNode parse() {
            ScalarNode value = parseLogicalOr();
            skipWhitespace();
            if (index != expression.length()) {
                throw new ParseException();
            }
            return value;
        }

        private ScalarNode parseLogicalOr() {
            ScalarNode value = parseLogicalAnd();
            while (true) {
                skipWhitespace();
                if (match("||")) {
                    value = new BinaryNode("||", value, parseLogicalAnd());
                } else {
                    return value;
                }
            }
        }

        private ScalarNode parseLogicalAnd() {
            ScalarNode value = parseComparison();
            while (true) {
                skipWhitespace();
                if (match("&&")) {
                    value = new BinaryNode("&&", value, parseComparison());
                } else {
                    return value;
                }
            }
        }

        private ScalarNode parseComparison() {
            ScalarNode value = parseExpression();
            while (true) {
                skipWhitespace();
                if (match(">=")) {
                    value = new BinaryNode(">=", value, parseExpression());
                } else if (match("<=")) {
                    value = new BinaryNode("<=", value, parseExpression());
                } else if (match("==")) {
                    value = new BinaryNode("==", value, parseExpression());
                } else if (match("!=")) {
                    value = new BinaryNode("!=", value, parseExpression());
                } else if (match('>')) {
                    value = new BinaryNode(">", value, parseExpression());
                } else if (match('<')) {
                    value = new BinaryNode("<", value, parseExpression());
                } else {
                    return value;
                }
            }
        }

        private ScalarNode parseExpression() {
            ScalarNode value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value = new BinaryNode("+", value, parseTerm());
                } else if (match('-')) {
                    value = new BinaryNode("-", value, parseTerm());
                } else {
                    return value;
                }
            }
        }

        private ScalarNode parseTerm() {
            ScalarNode value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value = new BinaryNode("*", value, parseFactor());
                } else if (match('/')) {
                    value = new BinaryNode("/", value, parseFactor());
                } else if (match('%')) {
                    value = new BinaryNode("%", value, parseFactor());
                } else {
                    return value;
                }
            }
        }

        private ScalarNode parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return new UnaryNode('-', parseFactor());
            }
            if (match('!')) {
                return new UnaryNode('!', parseFactor());
            }
            if (match('(')) {
                ScalarNode value = parseLogicalOr();
                if (!match(')')) {
                    throw new ParseException();
                }
                return value;
            }
            if (index >= expression.length()) {
                throw new ParseException();
            }
            char c = expression.charAt(index);
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }
            if (Character.isJavaIdentifierStart(c)) {
                return parseIdentifierOrFunction();
            }
            throw new ParseException();
        }

        private ScalarNode parseNumber() {
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
            return new ConstantNode(Float.parseFloat(expression.substring(start, index)));
        }

        private ScalarNode parseIdentifierOrFunction() {
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
                return new FunctionNode(identifier, parseArguments());
            }
            if ("true".equalsIgnoreCase(identifier)) {
                return new ConstantNode(1.0f);
            }
            if ("false".equalsIgnoreCase(identifier)) {
                return new ConstantNode(0.0f);
            }
            return new VariableNode(identifier);
        }

        private List<ScalarNode> parseArguments() {
            List<ScalarNode> arguments = new ArrayList<>();
            skipWhitespace();
            if (match(')')) {
                return arguments;
            }
            while (true) {
                arguments.add(parseLogicalOr());
                skipWhitespace();
                if (match(')')) {
                    return List.copyOf(arguments);
                }
                if (!match(',')) {
                    throw new ParseException();
                }
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

        private void skipWhitespace() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) {
                index++;
            }
        }
    }

    private static boolean truthy(float value) {
        return Math.abs(value) > 0.000001f;
    }

    private static final class ParseException extends RuntimeException {
        private ParseException() {
            super(null, null, false, false);
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
