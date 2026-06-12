package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderDrawBuffersScanner {

    private static final Pattern DRAWBUFFERS_PATTERN = Pattern.compile(".*DRAWBUFFERS\\s*:\\s*([0-9]+).*");
    private static final Pattern RENDERTARGETS_PATTERN = Pattern.compile(".*RENDERTARGETS\\s*:\\s*([0-9]+(?:\\s*,\\s*[0-9]+)*).*");

    private ShaderDrawBuffersScanner() {
    }

    public static Map<RenderPass, List<Attachment>> scan(ShaderPack pack, ShaderPackLayout layout) {
        return scan(pack, layout, ShaderOptions.empty());
    }

    public static Map<RenderPass, List<Attachment>> scan(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options) {
        Map<ProgramId, List<Attachment>> programDrawBuffers = scanProgramIds(pack, layout, options);
        Map<RenderPass, List<Attachment>> result = new EnumMap<>(RenderPass.class);

        for (RenderPass pass : RenderPass.values()) {
            List<Attachment> attachments = programDrawBuffers.get(pass.programId());
            if (attachments != null && !attachments.isEmpty()) {
                result.put(pass, attachments);
            }
        }

        return result;
    }

    public static Map<ProgramId, List<Attachment>> scanProgramIds(ShaderPack pack, ShaderPackLayout layout) {
        return scanProgramIds(pack, layout, ShaderOptions.empty());
    }

    public static Map<ProgramId, List<Attachment>> scanProgramIds(ShaderPack pack, ShaderPackLayout layout, ShaderOptions options) {
        Map<ProgramId, List<Attachment>> result = new EnumMap<>(ProgramId.class);
        int dimensionId = ShaderDimensionContext.currentDimensionId();

        for (ProgramId programId : ProgramId.values()) {
            List<String> values = scanProgram(pack, layout, dimensionId, programId, options);
            if (values.size() == 1) {
                String value = values.get(0);
                List<Attachment> attachments = parseDrawBuffers(value);
                if (!attachments.isEmpty()) {
                    result.put(programId, attachments);
                }
            } else if (values.size() > 1) {
                String value = selectFallbackValue(values);
                List<Attachment> attachments = parseDrawBuffers(value);
                if (!attachments.isEmpty()) {
                    result.put(programId, attachments);
                    MainMod.LOGGER.debug(
                            "[ShaderDrawBuffers] Using fallback inline DRAWBUFFERS for {}: {} from {}",
                            programId.sourceName(),
                            value,
                            values
                    );
                }
            }
        }

        if (!result.isEmpty()) {
            MainMod.LOGGER.debug("[ShaderDrawBuffers] Loaded inline draw buffers: {}", result);
        }
        return result;
    }

    private static List<String> scanProgram(ShaderPack pack, ShaderPackLayout layout, int dimensionId, ProgramId programId, ShaderOptions options) {
        List<String> values = new ArrayList<>();
        scanResolvedStage(pack, layout, dimensionId, programId, ".vsh", values, options);
        scanResolvedStage(pack, layout, dimensionId, programId, ".gsh", values, options);
        scanResolvedStage(pack, layout, dimensionId, programId, ".fsh", values, options);
        return values;
    }

    private static void scanResolvedStage(
            ShaderPack pack,
            ShaderPackLayout layout,
            int dimensionId,
            ProgramId programId,
            String extension,
            List<String> values,
            ShaderOptions options
    ) {
        String path = resolveStage(pack, layout, dimensionId, programId, extension);
        if (path != null) {
            scanFile(pack, path, values, new HashSet<>(), new ScanState(options));
        }
    }

    private static String resolveStage(ShaderPack pack, ShaderPackLayout layout, int dimensionId, ProgramId programId, String extension) {
        for (int candidateDimensionId : dimensionFallbackOrder(dimensionId)) {
            for (String dimensionBase : layout.dimensionProgramBaseAliases(candidateDimensionId, programId)) {
                String path = dimensionBase + extension;
                if (pack.hasResource(path)) {
                    return path;
                }
            }
        }

        for (String rootBase : layout.programBaseAliases(programId)) {
            String path = rootBase + extension;
            if (pack.hasResource(path)) {
                return path;
            }
        }
        return null;
    }

    private static int[] dimensionFallbackOrder(int dimensionId) {
        if (dimensionId == 0) {
            return new int[]{0};
        }
        return new int[]{dimensionId, 0};
    }

    private static void scanFile(ShaderPack pack, String path, List<String> values, Set<String> visited, ScanState state) {
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
                    if (state.handleDirective(trimmed)) {
                        continue;
                    }

                    if (state.active() && trimmed.startsWith("#include ")) {
                        String includePath = ShaderPreprocessor.extractIncludePath(trimmed, path);
                        if (includePath != null) {
                            scanFile(pack, includePath, values, visited, state);
                        }
                        continue;
                    }

                    if (!state.active()) {
                        continue;
                    }

                    Matcher matcher = DRAWBUFFERS_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        addValue(values, matcher.group(1));
                        continue;
                    }

                    matcher = RENDERTARGETS_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        addValue(values, matcher.group(1));
                    }
                }
            }
        } catch (IOException e) {
            MainMod.LOGGER.warn("[ShaderDrawBuffers] Failed to scan {}", path, e);
        } finally {
            visited.remove(path);
        }
    }

    private static void addValue(List<String> values, String value) {
        if (values.isEmpty() || !values.get(values.size() - 1).equals(value)) {
            values.add(value);
        }
    }

    private static List<Attachment> parseDrawBuffers(String value) {
        List<Attachment> attachments = new ArrayList<>();
        if (value.contains(",")) {
            for (String token : value.split(",")) {
                parseAttachmentToken(token.trim(), attachments);
            }
            return List.copyOf(attachments);
        }

        String normalized = value.replaceAll("\\s+", "");
        for (int i = 0; i < normalized.length(); i++) {
            parseAttachmentToken(String.valueOf(normalized.charAt(i)), attachments);
        }
        return List.copyOf(attachments);
    }

    private static String selectFallbackValue(List<String> values) {
        LinkedHashSet<String> uniqueValues = new LinkedHashSet<>(values);
        if (uniqueValues.size() == 1) {
            return uniqueValues.iterator().next();
        }
        return values.get(values.size() - 1);
    }

    private static void parseAttachmentToken(String token, List<Attachment> attachments) {
        if (token.isBlank()) {
            return;
        }

        try {
            Attachment attachment = Attachment.fromColorIndex(Integer.parseInt(token));
            if (attachment != null) {
                attachments.add(attachment);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private record ConditionFrame(boolean parentActive, boolean branchTaken) {
    }

    private static final class ScanState {
        private final ShaderOptions options;
        private final Map<String, String> defines = new HashMap<>();
        private final ArrayDeque<ConditionFrame> conditions = new ArrayDeque<>();
        private boolean active = true;

        private ScanState(ShaderOptions options) {
            this.options = options;
            defines.put("MC_VERSION", "11202");
            for (ShaderOption option : options.all().values()) {
                if (!option.changed()) {
                    continue;
                }
                if (option.toggle()) {
                    if (option.asBoolean()) {
                        defines.put(option.name(), "1");
                    }
                } else {
                    defines.put(option.name(), option.value());
                }
            }
        }

        private boolean active() {
            return active;
        }

        private boolean handleDirective(String trimmed) {
            if (!trimmed.startsWith("#")) {
                return false;
            }

            String directive = stripLineComment(trimmed);
            if (directive.startsWith("#ifdef ")) {
                pushCondition(isDefined(directive.substring("#ifdef ".length()).trim()));
                return true;
            }
            if (directive.startsWith("#ifndef ")) {
                pushCondition(!isDefined(directive.substring("#ifndef ".length()).trim()));
                return true;
            }
            if (directive.startsWith("#if ")) {
                pushCondition(new Expression(directive.substring("#if ".length()), defines).parse());
                return true;
            }
            if (directive.startsWith("#elif ")) {
                replaceCondition(new Expression(directive.substring("#elif ".length()), defines).parse());
                return true;
            }
            if (directive.equals("#else")) {
                elseCondition();
                return true;
            }
            if (directive.equals("#endif")) {
                popCondition();
                return true;
            }
            if (active && directive.startsWith("#define ")) {
                define(directive.substring("#define ".length()).trim());
                return true;
            }
            if (active && directive.startsWith("#undef ")) {
                undefine(directive.substring("#undef ".length()).trim());
                return true;
            }
            return false;
        }

        private boolean isDefined(String name) {
            return defines.containsKey(name);
        }

        private void define(String body) {
            if (body.isBlank()) {
                return;
            }

            String[] parts = body.split("\\s+", 2);
            String name = parts[0];
            if (name.contains("(")) {
                return;
            }

            ShaderOption option = options.get(name);
            if (option != null) {
                if (option.toggle()) {
                    if (option.asBoolean()) {
                        defines.put(name, "1");
                    } else {
                        defines.remove(name);
                    }
                } else {
                    defines.put(name, option.value());
                }
                return;
            }

            defines.put(name, parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : "1");
        }

        private void undefine(String body) {
            String name = body.split("\\s+", 2)[0];
            defines.remove(name);
        }

        private void pushCondition(boolean condition) {
            conditions.push(new ConditionFrame(active, condition));
            active = active && condition;
        }

        private void replaceCondition(boolean condition) {
            if (conditions.isEmpty()) {
                return;
            }

            ConditionFrame previous = conditions.pop();
            boolean take = previous.parentActive() && !previous.branchTaken() && condition;
            conditions.push(new ConditionFrame(previous.parentActive(), previous.branchTaken() || take));
            active = take;
        }

        private void elseCondition() {
            if (conditions.isEmpty()) {
                return;
            }

            ConditionFrame previous = conditions.pop();
            boolean take = previous.parentActive() && !previous.branchTaken();
            conditions.push(new ConditionFrame(previous.parentActive(), true));
            active = take;
        }

        private void popCondition() {
            if (conditions.isEmpty()) {
                active = true;
                return;
            }

            ConditionFrame previous = conditions.pop();
            active = previous.parentActive();
        }

        private static String stripLineComment(String line) {
            int comment = line.indexOf("//");
            return comment >= 0 ? line.substring(0, comment).trim() : line;
        }
    }

    private static final class Expression {
        private final String input;
        private final Map<String, String> defines;
        private int position;

        private Expression(String input, Map<String, String> defines) {
            this.input = input;
            this.defines = defines;
        }

        private boolean parse() {
            try {
                boolean value = parseOr();
                skipWhitespace();
                return value;
            } catch (RuntimeException ignored) {
                return false;
            }
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
            boolean value = parseNot();
            while (true) {
                skipWhitespace();
                if (!consume("&&")) {
                    return value;
                }
                boolean right = parseNot();
                value = value && right;
            }
        }

        private boolean parseNot() {
            skipWhitespace();
            if (consume("!")) {
                return !parseNot();
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            long left = parseValue();
            skipWhitespace();
            if (consume(">=")) {
                return left >= parseValue();
            }
            if (consume("<=")) {
                return left <= parseValue();
            }
            if (consume("==")) {
                return left == parseValue();
            }
            if (consume("!=")) {
                return left != parseValue();
            }
            if (consume(">")) {
                return left > parseValue();
            }
            if (consume("<")) {
                return left < parseValue();
            }
            return left != 0;
        }

        private long parseValue() {
            skipWhitespace();
            if (consume("(")) {
                boolean value = parseOr();
                consume(")");
                return value ? 1 : 0;
            }
            if (consume("defined")) {
                skipWhitespace();
                String name;
                if (consume("(")) {
                    name = readIdentifier();
                    consume(")");
                } else {
                    name = readIdentifier();
                }
                return defines.containsKey(name) ? 1 : 0;
            }

            if (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '-')) {
                return readNumber();
            }

            String name = readIdentifier();
            if (name.isEmpty()) {
                return 0;
            }
            String value = defines.get(name);
            if (value == null) {
                return 0;
            }
            return parseNumericDefine(value);
        }

        private long parseNumericDefine(String value) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                return 1;
            }
            try {
                return Long.decode(trimmed.split("\\s+", 2)[0]);
            } catch (NumberFormatException ignored) {
                return switch (trimmed.toLowerCase()) {
                    case "true", "on" -> 1;
                    case "false", "off" -> 0;
                    default -> defines.containsKey(trimmed) ? parseNumericDefine(defines.get(trimmed)) : 0;
                };
            }
        }

        private long readNumber() {
            int start = position;
            if (position < input.length() && input.charAt(position) == '-') {
                position++;
            }
            while (position < input.length() && (Character.isDigit(input.charAt(position)) || input.charAt(position) == 'x' || input.charAt(position) == 'X' || isHex(input.charAt(position)))) {
                position++;
            }
            return Long.decode(input.substring(start, position));
        }

        private String readIdentifier() {
            skipWhitespace();
            int start = position;
            while (position < input.length()) {
                char c = input.charAt(position);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    break;
                }
                position++;
            }
            return input.substring(start, position);
        }

        private boolean consume(String token) {
            skipWhitespace();
            if (!input.startsWith(token, position)) {
                return false;
            }
            position += token.length();
            return true;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private boolean isHex(char c) {
            return c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
        }
    }
}
