package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.DistantHorizonsCompat;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Composes borrowed native DH output at the selected presentation boundary. Render-thread only. */
public final class NativeDistantHorizonsCompositor {
    private static final String VERTEX = """
            #version 120
            varying vec2 uv;
            void main() { gl_Position = gl_Vertex; uv = gl_Vertex.xy * 0.5 + 0.5; }
            """;
    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D lodColor;
            uniform sampler2D lodDepth;
            uniform sampler2D sceneDepth;
            uniform mat4 lodProjectionInverse;
            uniform mat4 sceneProjection;
            uniform float emptyDepth;
            varying vec2 uv;
            void main() {
                vec4 color = texture2D(lodColor, uv);
                float depth = texture2D(lodDepth, uv).r;
                if (color.a < 0.0001 || abs(depth - emptyDepth) < 0.0000001) discard;
                vec4 view = lodProjectionInverse * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 projected = sceneProjection * view;
                if (projected.w <= 0.0) discard;
                float projectedDepth = projected.z / projected.w * 0.5 + 0.5;
                float opaque = texture2D(sceneDepth, uv).r;
                // Coarse LOD surfaces can sit in front of their full block mesh.
                // The ordinary terrain and hand always own occupied pixels.
                if (projectedDepth < 0.0 || opaque < 0.999999) discard;
                gl_FragColor = color;
            }
            """;
    private static final FloatBuffer LOD_INVERSE = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer SCENE_PROJECTION = BufferUtils.createFloatBuffer(16);
    private static int program;
    private static boolean failed;
    private static boolean pending;
    private static int colorTexture;
    private static int depthTexture;
    private static float emptyDepth;
    private static boolean logged;

    private NativeDistantHorizonsCompositor() {
    }

    public static void beginFrame() {
        pending = false;
    }

    public static boolean capture(int color, int depth, float[] inverse, float[] projection, float farDepth) {
        if (failed || !GL11.glIsTexture(color) || !GL11.glIsTexture(depth) || !ensureProgram()) {
            return false;
        }
        LOD_INVERSE.clear();
        LOD_INVERSE.put(inverse).flip();
        SCENE_PROJECTION.clear();
        SCENE_PROJECTION.put(projection).flip();
        colorTexture = color;
        depthTexture = depth;
        emptyDepth = farDepth;
        pending = true;
        return true;
    }

    public static void draw(int sceneDepth, int width, int height) {
        if (!pending) {
            return;
        }
        pending = false;
        if (sceneDepth <= 0 || !GL11.glIsTexture(sceneDepth)
                || !GL11.glIsTexture(colorTexture) || !GL11.glIsTexture(depthTexture)) {
            return;
        }
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        // Raw GL throughout this scope leaves Minecraft's cached state intact.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_VIEWPORT_BIT | GL11.GL_POLYGON_BIT);
        try {
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(program);
            GL11.glViewport(0, 0, width, height);
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            texture("lodColor", 0, colorTexture);
            texture("lodDepth", 1, depthTexture);
            texture("sceneDepth", 2, sceneDepth);
            GL20.glUniformMatrix4(GL20.glGetUniformLocation(program, "lodProjectionInverse"), false, LOD_INVERSE);
            GL20.glUniformMatrix4(GL20.glGetUniformLocation(program, "sceneProjection"), false, SCENE_PROJECTION);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "emptyDepth"), emptyDepth);
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(-1.0F, -1.0F);
            GL11.glVertex2f(3.0F, -1.0F);
            GL11.glVertex2f(-1.0F, 3.0F);
            GL11.glEnd();
            if (!logged) {
                logged = true;
                MainMod.LOGGER.info("[AUSMDistantHorizons] Composed native LOD textures color={} depth={} sceneDepth={} size={}x{}.",
                        colorTexture, depthTexture, sceneDepth, width, height);
            }
        } finally {
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
        }
    }

    private static void texture(String name, int unit, int id) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, name), unit);
    }

    private static boolean ensureProgram() {
        if (program != 0) {
            return true;
        }
        int vertex = 0;
        int fragment = 0;
        int candidate = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, vertex);
            GL20.glAttachShader(candidate, fragment);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate, 4096));
            }
            program = candidate;
            candidate = 0;
            return true;
        } catch (RuntimeException failure) {
            failed = true;
            MainMod.LOGGER.error("[AUSMDistantHorizons] Could not compile native compositor.", failure);
            return false;
        } finally {
            if (candidate != 0) GL20.glDeleteProgram(candidate);
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    public static void release() {
        NativeDistantHorizonsAntialiasing.release();
        DistantHorizonsCompat.releaseRenderingSettings();
        pending = false;
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        failed = false;
        logged = false;
    }

}
