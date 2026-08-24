package com.luna.ausm.impl.pipeline.shader;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.pack.ShaderExpressionEvaluator;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ComputeDirectiveParser {
    private static final Pattern WORK_GROUPS_PATTERN = Pattern.compile("\\bconst\\s+ivec3\\s+workGroups\\s*=\\s*ivec3\\s*\\(([^)]*)\\)\\s*;.*");
    private static final Pattern WORK_GROUPS_RENDER_PATTERN = Pattern.compile("\\bconst\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(([^)]*)\\)\\s*;.*");

    private ComputeDirectiveParser() {
    }

    static int[] parseWorkGroups(String source, boolean activeMetadataOnly, String logPrefix, String directiveName) {
        Matcher matcher = WORK_GROUPS_PATTERN.matcher(metadataSource(source, activeMetadataOnly));
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 3) {
            logMalformed(logPrefix, directiveName, matcher.group(0));
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            logMalformed(logPrefix, directiveName, matcher.group(0));
            return null;
        }
    }

    static float[] parseWorkGroupRelative(String source, boolean activeMetadataOnly, String logPrefix, String directiveName) {
        Matcher matcher = WORK_GROUPS_RENDER_PATTERN.matcher(metadataSource(source, activeMetadataOnly));
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 2) {
            logMalformed(logPrefix, directiveName, matcher.group(0));
            return null;
        }
        try {
            return new float[]{
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim())
            };
        } catch (NumberFormatException e) {
            logMalformed(logPrefix, directiveName, matcher.group(0));
            return null;
        }
    }

    private static void logMalformed(String logPrefix, String directiveName, String directive) {
        MainMod.LOGGER.warn("{} Ignoring malformed {} directive: {}", logPrefix, directiveName, directive);
    }

    private static String metadataSource(String source, boolean activeMetadataOnly) {
        if (!activeMetadataOnly) {
            return source == null ? "" : source;
        }
        return activeMetadataSource(source);
    }

    private static String activeMetadataSource(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }

        Map<String, String> defines = new HashMap<>();
        ArrayDeque<ConditionFrame> conditions = new ArrayDeque<>();
        StringBuilder active = new StringBuilder(source.length());
        boolean enabled = true;
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#define ")) {
                if (enabled) {
                    String[] parts = stripLineComment(trimmed).trim().split("\\s+", 3);
                    if (parts.length >= 2) {
                        defines.put(parts[1], parts.length >= 3 ? firstValueToken(parts[2]) : "1");
                    }
                    active.append(line).append('\n');
                }
                continue;
            }
            if (trimmed.startsWith("#ifdef ")) {
                boolean condition = defines.containsKey(trimmed.substring("#ifdef ".length()).trim());
                conditions.push(new ConditionFrame(enabled, enabled && condition, condition));
                enabled = enabled && condition;
                continue;
            }
            if (trimmed.startsWith("#ifndef ")) {
                boolean condition = !defines.containsKey(trimmed.substring("#ifndef ".length()).trim());
                conditions.push(new ConditionFrame(enabled, enabled && condition, condition));
                enabled = enabled && condition;
                continue;
            }
            if (trimmed.startsWith("#if ")) {
                boolean condition = evaluateCondition(trimmed.substring("#if ".length()).trim(), defines);
                conditions.push(new ConditionFrame(enabled, enabled && condition, condition));
                enabled = enabled && condition;
                continue;
            }
            if (trimmed.startsWith("#elif ")) {
                if (!conditions.isEmpty()) {
                    ConditionFrame frame = conditions.pop();
                    boolean condition = !frame.branchMatched() && evaluateCondition(trimmed.substring("#elif ".length()).trim(), defines);
                    conditions.push(new ConditionFrame(frame.parentEnabled(), frame.parentEnabled() && condition, frame.branchMatched() || condition));
                    enabled = frame.parentEnabled() && condition;
                }
                continue;
            }
            if (trimmed.startsWith("#else")) {
                if (!conditions.isEmpty()) {
                    ConditionFrame frame = conditions.pop();
                    boolean condition = !frame.branchMatched();
                    conditions.push(new ConditionFrame(frame.parentEnabled(), frame.parentEnabled() && condition, true));
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
                active.append(line).append('\n');
            }
        }
        return active.toString();
    }

    private static boolean evaluateCondition(String expression, Map<String, String> defines) {
        return ShaderExpressionEvaluator.evaluate(stripLineComment(expression), defines);
    }

    private static String stripLineComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static String firstValueToken(String value) {
        String stripped = stripLineComment(value).trim();
        if (stripped.isEmpty()) {
            return "1";
        }
        int whitespace = -1;
        for (int i = 0; i < stripped.length(); i++) {
            if (Character.isWhitespace(stripped.charAt(i))) {
                whitespace = i;
                break;
            }
        }
        return whitespace < 0 ? stripped : stripped.substring(0, whitespace);
    }

    private record ConditionFrame(boolean parentEnabled, boolean active, boolean branchMatched) {
    }
}
