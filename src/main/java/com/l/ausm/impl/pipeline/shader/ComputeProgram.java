package com.l.ausm.impl.pipeline.shader;

import com.l.ausm.api.pipeline.shader.ComputeProgramSource;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderExpressionEvaluator;
import com.l.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ComputeProgram {
    private static final Pattern WORK_GROUPS_PATTERN = Pattern.compile("\\bconst\\s+ivec3\\s+workGroups\\s*=\\s*ivec3\\s*\\(([^)]*)\\)\\s*;.*");
    private static final Pattern WORK_GROUPS_RENDER_PATTERN = Pattern.compile("\\bconst\\s+vec2\\s+workGroupsRender\\s*=\\s*vec2\\s*\\(([^)]*)\\)\\s*;.*");

    private final ComputeProgramSource source;
    private final ShaderProgram program;
    private final int[] workGroups;
    private final float[] workGroupRelative;

    private ComputeProgram(ComputeProgramSource source, ShaderProgram program, int[] workGroups, float[] workGroupRelative) {
        this.source = source;
        this.program = program;
        this.workGroups = workGroups;
        this.workGroupRelative = workGroupRelative;
    }

    public static ComputeProgram compile(ShaderPack pack, ShaderProperties properties, ComputeProgramSource source) {
        if (source.path() == null) {
            return null;
        }

        try {
            String processed = ShaderPreprocessor.processShaderSource(pack, source.path(), properties.options(), null, GL43.GL_COMPUTE_SHADER);
            if (processed == null || processed.isBlank()) {
                return null;
            }

            int shader = OpenGlHelper.glCreateShader(GL43.GL_COMPUTE_SHADER);
            GL20.glShaderSource(shader, processed);
            OpenGlHelper.glCompileShader(shader);
            if (OpenGlHelper.glGetShaderi(shader, OpenGlHelper.GL_COMPILE_STATUS) == 0) {
                String log = OpenGlHelper.glGetShaderInfoLog(shader, 32768);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to compile compute shader '{}': {}", source.path(), log);
                ShaderSourceDumper.dumpFailedSource(source.path(), processed);
                ShaderCompileNotifications.reportFailure(source.path());
                OpenGlHelper.glDeleteShader(shader);
                return null;
            }

            ShaderProgram program = new ShaderProgram(source.name());
            program.attachShader(shader);
            if (!program.link()) {
                program.delete();
                OpenGlHelper.glDeleteShader(shader);
                MainMod.LOGGER.error("[ShaderCompiler] Failed to link compute program '{}'", source.name());
                ShaderCompileNotifications.reportFailure(source.path());
                return null;
            }

            OpenGlHelper.glDeleteShader(shader);
            int[] workGroups = parseWorkGroups(processed);
            float[] workGroupRelative = parseWorkGroupRelative(processed);
            if (workGroups == null && source.hasFixedWorkGroups()) {
                workGroups = source.workGroups();
            }
            if (workGroupRelative == null && source.hasRelativeWorkGroups()) {
                workGroupRelative = source.workGroupRelative();
            }
            MainMod.LOGGER.debug(
                    "[ShaderCompiler] Successfully compiled compute program: {} workGroups={} workGroupsRender={}",
                    source.name(),
                    workGroups == null ? "auto" : java.util.Arrays.toString(workGroups),
                    workGroupRelative == null ? "none" : java.util.Arrays.toString(workGroupRelative)
            );
            return new ComputeProgram(source, program, workGroups, workGroupRelative);
        } catch (IOException e) {
            MainMod.LOGGER.error("[ShaderCompiler] Error reading compute shader '{}'", source.path(), e);
            ShaderCompileNotifications.reportFailure(source.path());
            return null;
        }
    }

    public void bind() {
        program.bind();
    }

    public ShaderProgram program() {
        return program;
    }

    public String name() {
        return source.name();
    }

    public int arrayIndex() {
        return Math.max(0, source.arrayIndex());
    }

    public boolean hasIndirectPointer() {
        return source.hasIndirectPointer();
    }

    public int indirectBuffer() {
        return source.indirectPointer() == null ? -1 : source.indirectPointer().buffer();
    }

    public long indirectOffset() {
        return source.indirectPointer() == null ? 0L : Math.max(0L, source.indirectPointer().offset());
    }

    public int[] workGroups(int renderWidth, int renderHeight) {
        if (workGroups != null) {
            int[] fixed = workGroups;
            return new int[]{Math.max(1, fixed[0]), Math.max(1, fixed[1]), Math.max(1, fixed[2])};
        }
        if (workGroupRelative != null) {
            float[] relative = workGroupRelative;
            return new int[]{
                    Math.max(1, (int) Math.ceil(renderWidth * relative[0])),
                    Math.max(1, (int) Math.ceil(renderHeight * relative[1])),
                    1
            };
        }
        return new int[]{1, 1, 1};
    }

    public void delete() {
        program.delete();
    }

    private static int[] parseWorkGroups(String source) {
        Matcher matcher = WORK_GROUPS_PATTERN.matcher(activeMetadataSource(source));
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 3) {
            MainMod.LOGGER.warn("[ShaderCompiler] Ignoring malformed compute workGroups directive: {}", matcher.group(0));
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderCompiler] Ignoring malformed compute workGroups directive: {}", matcher.group(0));
            return null;
        }
    }

    private static float[] parseWorkGroupRelative(String source) {
        Matcher matcher = WORK_GROUPS_RENDER_PATTERN.matcher(activeMetadataSource(source));
        if (!matcher.find()) {
            return null;
        }
        String[] parts = matcher.group(1).split(",");
        if (parts.length != 2) {
            MainMod.LOGGER.warn("[ShaderCompiler] Ignoring malformed compute workGroupsRender directive: {}", matcher.group(0));
            return null;
        }
        try {
            return new float[]{
                    Float.parseFloat(parts[0].trim()),
                    Float.parseFloat(parts[1].trim())
            };
        } catch (NumberFormatException e) {
            MainMod.LOGGER.warn("[ShaderCompiler] Ignoring malformed compute workGroupsRender directive: {}", matcher.group(0));
            return null;
        }
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
