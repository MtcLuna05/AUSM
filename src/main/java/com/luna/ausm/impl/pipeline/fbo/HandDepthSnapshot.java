package com.luna.ausm.impl.pipeline.fbo;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/** Adds the late solid hand to depthtex1 without copying translucent world depth. */
public final class HandDepthSnapshot {
    private static int program;
    private static int framebuffer;

    private HandDepthSnapshot() { }

    public static void merge(DeferredFramebuffer target) {
        if (target == null) return;
        ensureProgram();
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int sampler = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, target.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT), 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete hand depth snapshot framebuffer");
            }
            GL11.glViewport(0, 0, target.getWidth(), target.getHeight());
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDepthMask(true);
            GL11.glDepthRange(0.0D, 1.0D);
            GL11.glColorMask(false, false, false, false);
            GL33.glBindSampler(0, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getDepthTexture());
            GL20.glUseProgram(program);
            GL20.glUniform1i(GL20.glGetUniformLocation(program, "liveDepth"), 0);
            GL20.glUniform2f(GL20.glGetUniformLocation(program, "inverseSize"),
                    1.0F / target.getWidth(), 1.0F / target.getHeight());
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex2f(-1, -1);
            GL11.glVertex2f(1, -1);
            GL11.glVertex2f(1, 1);
            GL11.glVertex2f(-1, 1);
            GL11.glEnd();
        } finally {
            GL33.glBindSampler(0, sampler);
            GL20.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL11.glPopAttrib();
            GL13.glActiveTexture(activeTexture);
        }
    }

    private static void ensureProgram() {
        if (program != 0) return;
        int vertex = compile(GL20.GL_VERTEX_SHADER, "#version 120\nvoid main(){gl_Position=gl_Vertex;}");
        int fragment = compile(GL20.GL_FRAGMENT_SHADER, """
                #version 120
                uniform sampler2D liveDepth;
                uniform vec2 inverseSize;
                void main() {
                    float depth = texture2D(liveDepth, gl_FragCoord.xy * inverseSize).r;
                    if (depth < 0.4375 || depth > 0.5625) discard;
                    gl_FragDepth = depth;
                }
                """);
        int linked = GL20.glCreateProgram();
        try {
            GL20.glAttachShader(linked, vertex);
            GL20.glAttachShader(linked, fragment);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, 4096));
            }
            framebuffer = GL30.glGenFramebuffers();
            program = linked;
        } finally {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            if (program == 0) GL20.glDeleteProgram(linked);
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(error);
        }
        return shader;
    }

    public static void delete() {
        if (program != 0) GL20.glDeleteProgram(program);
        if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
        program = 0;
        framebuffer = 0;
    }
}
