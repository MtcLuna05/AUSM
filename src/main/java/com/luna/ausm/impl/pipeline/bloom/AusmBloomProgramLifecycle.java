package com.luna.ausm.impl.pipeline.bloom;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

abstract class AusmBloomProgramLifecycle extends AusmBloomFramebufferProcessing {
    protected int copyProgram() {
        if (copyProgram == -1) {
            copyProgram = self().createProgram("copy", COPY_FRAGMENT_SHADER);
        }
        return copyProgram;
    }

    protected int thresholdProgram() {
        if (thresholdProgram == -1) {
            thresholdProgram = self().createProgram("threshold", THRESHOLD_FRAGMENT_SHADER);
        }
        return thresholdProgram;
    }

    protected int blurProgram() {
        if (blurProgram == -1) {
            blurProgram = self().createProgram("blur", BLUR_FRAGMENT_SHADER);
        }
        return blurProgram;
    }

    protected int compositeProgram() {
        if (compositeProgram == -1) {
            compositeProgram = self().createProgram(
                    shaderPackCompositeOverride ? "shaderpack-composite" : "composite",
                    compositeVertexSource,
                    compositeFragmentSource,
                    false
            );
            if (compositeProgram == -1 && shaderPackCompositeOverride) {
                MainMod.LOGGER.warn("[AUSMBloom] Shaderpack bloom override failed; using built-in bloom composite.");
                compositeProgram = self().createProgram("composite-fallback", VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER, false);
            }
        }
        return compositeProgram;
    }

    protected void resetShaderPackConfiguration() {
        AusmBloomRenderer.deleteProgram(compositeProgram);
        compositeProgram = -1;
        compositeVertexSource = VERTEX_SHADER;
        compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;
        bloomStrength = DEFAULT_BLOOM_STRENGTH;
        blurIterations = DEFAULT_BLUR_ITERATIONS;
        shaderPackCompositeOverride = false;
        shaderPackCompositeReplace = false;
        loggedProgramFailure = false;
        // The Bloom Lab is intentionally reloaded often.  Its bounded source,
        // blur, and composite probes must describe the newly selected pack.
        bloomFrameProbeCalls = 0;
        bloomCompositeProbeCalls = 0;
        bloomPeakProbeCalls = 0;
        bloomDepthLeakProbeCalls = 0;
        bloomDepthLeakProbeAttempts = 0;
        pendingBloomPeakProbe = null;
    }

    protected static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.clamp(value, minimum, maximum);
    }

    protected int emissiveExtractProgram() {
        if (emissiveExtractProgram == -1) {
            emissiveExtractProgram = self().createProgram(
                    "shaderless-emissive-extract",
                    EMISSIVE_EXTRACT_VERTEX_SHADER,
                    EMISSIVE_EXTRACT_FRAGMENT_SHADER,
                    true
            );
        }
        return emissiveExtractProgram;
    }

    protected int nativeBloomGeometryProgram() {
        if (nativeBloomGeometryProgram == -1) {
            nativeBloomGeometryProgram = self().createProgram(
                    "native-bloom-geometry",
                    NATIVE_BLOOM_GEOMETRY_VERTEX_SHADER,
                    NATIVE_BLOOM_GEOMETRY_FRAGMENT_SHADER,
                    true
            );
        }
        return nativeBloomGeometryProgram;
    }

    protected int translucentAttenuationProgram() {
        if (translucentAttenuationProgram == -1) {
            translucentAttenuationProgram = self().createProgram(
                    "translucent-bloom-attenuation",
                    TRANSLUCENT_ATTENUATION_VERTEX_SHADER,
                    TRANSLUCENT_ATTENUATION_FRAGMENT_SHADER,
                    false
            );
        }
        return translucentAttenuationProgram;
    }

    protected int createProgram(String name, String fragmentSource) {
        return self().createProgram(name, VERTEX_SHADER, fragmentSource, false);
    }

    protected int createProgram(String name, String vertexSource, String fragmentSource, boolean bindPipelineAttributes) {
        if (!MinecraftReflectionCompat.fieldBoolean(OpenGlHelper.class, false, "field_148824_g", "shadersSupported")) {
            return -1;
        }

        int vertex = self().compileShader(name + ":vertex", GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = self().compileShader(name + ":fragment", GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == -1 || fragment == -1) {
            AusmBloomRenderer.deleteShader(vertex);
            AusmBloomRenderer.deleteShader(fragment);
            return -1;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        if (bindPipelineAttributes) {
            GL20.glBindAttribLocation(
                    program,
                    ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                    "mc_Entity"
            );
            GL20.glBindAttribLocation(
                    program,
                    ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                    "at_midBlock"
            );
        }
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            self().logProgramFailure("Failed to link " + name + " bloom program: " + GL20.glGetProgramInfoLog(program, 4096));
            AusmBloomRenderer.deleteProgram(program);
            return -1;
        }
        return program;
    }

    protected int compileShader(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            self().logProgramFailure("Failed to compile " + name + " bloom shader: " + GL20.glGetShaderInfoLog(shader, 4096));
            AusmBloomRenderer.deleteShader(shader);
            return -1;
        }
        return shader;
    }

    protected void bindTextureUniform(int program, String name, int texture, int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        MinecraftReflectionCompat.glStateBindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, unit);
        }
    }

    protected static void bindSamplerUniform(int program, String name, int unit) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, unit);
        }
    }

    protected static void prepareShaderlessEmissiveGeometryState(int program) {
        AusmBloomRenderer.bindBlockAtlasOnDefaultTextureUnit();

        MinecraftReflectionCompat.glUseProgram(program);
        AusmBloomRenderer.bindSamplerUniform(program, "terrain", 0);
        AusmBloomRenderer.setUniform1f(program, "forceEmission", 0.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR, SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected static void bindBlockAtlasOnDefaultTextureUnit() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        MinecraftReflectionCompat.glStateSetActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.textureManager(mc) != null) {
            MinecraftReflectionCompat.bindTexture(MinecraftReflectionCompat.textureManager(mc), MinecraftReflectionCompat.blocksTexture());
        }
    }

    protected static void setUniform1f(int program, String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    protected static void setUniform1i(int program, String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    protected static void setUniform2f(int program, String name, float x, float y) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    protected static void drawFullscreenQuad() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    protected RenderState captureState() {
        return new RenderState();
    }

    protected LumenizedTicketBridge lumenizedTickets() {
        if (lumenizedTickets == null) {
            lumenizedTickets = new LumenizedTicketBridge();
        }
        return lumenizedTickets;
    }

    /**
     * Renders GregTech/Lumenized custom bloom effects into AUSM's bloom source
     * target. Their callbacks establish their own render state, so they must
     * not be submitted through the terrain BLOOM layer's vertex program.
     */
    protected int renderLumenizedBloomTickets(Entity entity, float partialTicks) {
        if (entity == null) {
            return 0;
        }
        MinecraftReflectionCompat.glUseProgram(0);
        return lumenizedTickets().draw(entity, partialTicks);
    }

    protected void logProgramFailure(String message) {
        if (!loggedProgramFailure) {
            loggedProgramFailure = true;
            MainMod.LOGGER.warn("[AUSMBloom] {}", message);
        }
    }

    protected static void deleteFramebuffer(Framebuffer framebuffer) {
        if (framebuffer != null) {
            MinecraftReflectionCompat.deleteFramebuffer(framebuffer);
        }
    }

    protected static void deleteProgram(int program) {
        if (program > 0) {
            GL20.glDeleteProgram(program);
        }
    }

    protected static void deleteTexture(int texture) {
        if (texture > 0) {
            GL11.glDeleteTextures(texture);
        }
    }

    protected static void deleteShader(int shader) {
        if (shader > 0) {
            GL20.glDeleteShader(shader);
        }
    }
}
