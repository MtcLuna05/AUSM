package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Owns HDR DH color and forward depth, and merges color without depth feedback. */
final class DistantHorizonsRenderTarget {
    private static final String VERTEX = """
            #version 120
            varying vec2 uv;
            void main() { gl_Position = gl_Vertex; uv = gl_Vertex.xy * 0.5 + 0.5; }
            """;
    private static final String FRAGMENT = """
            #version 120
            varying vec2 uv;
            uniform sampler2D lodColor;
            uniform sampler2D lodDepth;
            uniform sampler2D sceneDepth;
            void main() {
                vec4 color = texture2D(lodColor, uv);
                // Terrain coverage transitions while drawing terrain using
                // the pack's dithering. Preserve actual foreground
                // depth here instead of blending LODs over nearby geometry.
                if (color.a < 0.00001 || texture2D(lodDepth, uv).r >= 0.999999
                        || texture2D(sceneDepth, uv).r < 0.999999) discard;
                gl_FragColor = color;
            }
            """;
    private static int mergeFramebuffer;
    private static int mergeProgram;
    final int width;
    final int height;
    final int framebuffer;
    final int color;
    final int depth;

    DistantHorizonsRenderTarget(int width, int height) {
        this.width = width;
        this.height = height;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            color = texture(GL30.GL_RGBA16F, GL11.GL_RGBA, width, height);
            depth = texture(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT, width, height);
            framebuffer = GL30.glGenFramebuffers();
            bind();
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                delete();
                throw new IllegalStateException("Incomplete DH HDR framebuffer");
            }
            clear(this);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static int texture(int format, int pixelFormat, int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, format, width, height, 0, pixelFormat, GL11.GL_FLOAT, (FloatBuffer) null);
        return texture;
    }

    void bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    static void clear(DistantHorizonsRenderTarget target) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_SCISSOR_BIT);
        try {
            target.bind();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearDepth(1.0);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glPopAttrib();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    static void copyDepth(DistantHorizonsRenderTarget source, DistantHorizonsRenderTarget target) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.framebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.framebuffer);
        GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, target.width, target.height,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
    }

    static void merge(DistantHorizonsRenderTarget target, DeferredFramebuffer scene, boolean water) {
        if (mergeProgram == 0) createMergeProgram();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_TEXTURE_BIT | GL11.GL_VIEWPORT_BIT);
        try {
            if (mergeFramebuffer == 0) mergeFramebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mergeFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, scene.getReadTexture(Attachment.COLOR), 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return;
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(mergeProgram);
            GL11.glViewport(0, 0, scene.getWidth(), scene.getHeight());
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(water ? GL11.GL_ONE : GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            bindTexture("lodColor", 0, target.color);
            bindTexture("lodDepth", 1, target.depth);
            bindTexture("sceneDepth", 2, scene.getDepthSamplerTexture(water
                    ? DeferredFramebuffer.DEPTHTEX2_SNAPSHOT : DeferredFramebuffer.DEPTHTEX1_SNAPSHOT));
            GL11.glBegin(GL11.GL_TRIANGLES);
            GL11.glVertex2f(-1, -1);
            GL11.glVertex2f(3, -1);
            GL11.glVertex2f(-1, 3);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }

    private static void bindTexture(String uniform, int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL20.glUniform1i(GL20.glGetUniformLocation(mergeProgram, uniform), unit);
    }

    private static void createMergeProgram() {
        int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
        int fragment = 0;
        int candidate = 0;
        try {
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            candidate = GL20.glCreateProgram();
            GL20.glAttachShader(candidate, vertex);
            GL20.glAttachShader(candidate, fragment);
            GL20.glLinkProgram(candidate);
            if (GL20.glGetProgrami(candidate, GL20.GL_LINK_STATUS) == 0) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(candidate, 4096));
            }
            mergeProgram = candidate;
            candidate = 0;
        } finally {
            if (candidate != 0) GL20.glDeleteProgram(candidate);
            GL20.glDeleteShader(vertex);
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

    void delete() {
        GL30.glDeleteFramebuffers(framebuffer);
        GL11.glDeleteTextures(color);
        GL11.glDeleteTextures(depth);
    }

    static void release() {
        if (mergeFramebuffer != 0) GL30.glDeleteFramebuffers(mergeFramebuffer);
        if (mergeProgram != 0) GL20.glDeleteProgram(mergeProgram);
        mergeFramebuffer = 0;
        mergeProgram = 0;
    }
}
