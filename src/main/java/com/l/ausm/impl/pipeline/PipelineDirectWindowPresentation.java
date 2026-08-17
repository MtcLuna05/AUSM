package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.dh.DistantHorizonsInternalShaders;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import static com.l.ausm.impl.pipeline.PipelineDistantHorizonsConstants.ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER;
import static com.l.ausm.impl.pipeline.PipelineDistantHorizonsConstants.FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_DIRECT_RECOVERED_WINDOW_REFRESH_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_DIRECT_WINDOW_PRESENT_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_PRESENTATION_BOUNDARY_LOGS;

abstract class PipelineDeferredPassOrchestration1 extends PipelineDeferredPassOrchestration0 {
    protected void logDirectF1WindowPresent(Framebuffer target, int width, int height) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null
                || MinecraftReflectionCompat.gameSettings(mc) == null
                || !MinecraftReflectionCompat.hideGui(
                MinecraftReflectionCompat.gameSettings(mc))
                || directF1WindowPresentLogs++ >= 64) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectF1WindowPresent] call={} source={} sourceColor={} backColor={} drawFbo={} readFbo={} drawBuf={} readBuf={} size={}x{} glErrors={}",
                directF1WindowPresentLogs,
                self().describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferIdColorSamples(0, Math.max(1, width), Math.max(1, height), GL11.GL_BACK),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER),
                width,
                height,
                drainGlErrorsForProbe()
        );
    }

    protected boolean refreshMinecraftFramebufferFromDirectRecoveredWindowSource(Framebuffer target) {
        if (self().refreshMinecraftFramebufferFromDirectPresentationTexture(target, false)) {
            return true;
        }
        return self().refreshMinecraftFramebufferFromCurrentRecoveredWindowSource(target);
    }

    protected boolean refreshMinecraftFramebufferFromCurrentRecoveredWindowSource(Framebuffer target) {
        if (target == null
                || directRecoveredWindowSource == null
                || !directRecoveredWindowSource.isUsable()
                || directRecoveredWindowAttachment == null
                || directRecoveredWindowFrame != pipelineFrameId) {
            return false;
        }

        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int targetWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        GL11.glColorMask(true, true, true, true);
        directRecoveredWindowSource.blitTo(directRecoveredWindowAttachment, targetFramebuffer, targetWidth, targetHeight);
        MinecraftReflectionCompat.bindFramebuffer(target, false);
        MinecraftReflectionCompat.glStateViewport(0, 0, targetWidth, targetHeight);
        self().logDirectRecoveredWindowRefresh(target, targetWidth, targetHeight);
        return true;
    }

    protected void drawDirectRecoveredWindowSourceToTarget(int targetFramebuffer, int targetWidth, int targetHeight, float colorScale) {
        int texture = directRecoveredWindowSource != null && directRecoveredWindowAttachment != null
                ? directRecoveredWindowSource.getReadTexture(directRecoveredWindowAttachment)
                : -1;
        if (texture <= 0) {
            return;
        }

        int previousFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean previousDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        boolean previousBlend = GL11.glGetBoolean(GL11.GL_BLEND);
        boolean previousTexture2d = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        IntBuffer previousViewport = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);

        try {
            MinecraftReflectionCompat.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, targetWidth, targetHeight);
            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, 1.0D, 0.0D, 1.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glColor4f(colorScale, colorScale, colorScale, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(1.0F, 0.0F);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(1.0F, 1.0F);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(0.0F, 1.0F);
            GL11.glEnd();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
        } finally {
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            if (previousTexture2d) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            } else {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            if (previousBlend) {
                GL11.glEnable(GL11.GL_BLEND);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (previousDepthTest) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
            } else {
                GL11.glDisable(GL11.GL_DEPTH_TEST);
            }
            GL11.glDepthMask(previousDepthMask);
            GL11.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3));
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFramebuffer);
            restoreDrawBufferForFramebuffer(previousFramebuffer, previousDrawBuffer);
        }
    }

    protected void logDirectRecoveredWindowRefresh(Framebuffer target, int targetWidth, int targetHeight) {
        if (directRecoveredWindowRefreshLogs++ >= MAX_DIRECT_RECOVERED_WINDOW_REFRESH_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectRecoveredWindowRefresh] source={} attachment={} scale={} sourceColor={} target={} targetColor={} targetSize={}x{} expectedSize={}x{} sourceFrame={} currentFrame={} gl={}",
                self().describeDeferredFramebuffer(directRecoveredWindowSource),
                directRecoveredWindowAttachment,
                directRecoveredWindowColorScale,
                deferredFramebufferColorSamples(directRecoveredWindowSource, directRecoveredWindowAttachment),
                self().describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                targetWidth,
                targetHeight,
                directRecoveredWindowTargetWidth,
                directRecoveredWindowTargetHeight,
                directRecoveredWindowFrame,
                pipelineFrameId,
                PipelineContext.glStateSummary()
        );
    }

    protected void logDirectWindowPresent(Framebuffer target, int width, int height, boolean recoveredRefresh) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || (!self().shouldDirectPresentFramebuffer()
                && MinecraftReflectionCompat.currentScreen(mc) == null)) {
            return;
        }
        if (directWindowPresentLogs++ >= MAX_DIRECT_WINDOW_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectWindowPresent] source={} recoveredRefresh={} sourceColor={} backColor={} size={}x{} frame={} gl={}",
                self().describeFramebufferTargetDetailed(target),
                recoveredRefresh,
                framebufferSamples(target),
                framebufferIdColorSamples(0, Math.max(1, width), Math.max(1, height), GL11.GL_BACK),
                width,
                height,
                pipelineFrameId,
                PipelineContext.glStateSummary()
        );
    }

    public void logFramebufferPresentationBoundary(String stage,
                                                   Framebuffer target,
                                                   int width,
                                                   int height,
                                                   boolean sampleWindow) {
        if (MAX_PRESENTATION_BOUNDARY_LOGS <= 0) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || (MinecraftReflectionCompat.currentScreen(mc) == null
                && !self().shouldDirectPresentFramebuffer())) {
            return;
        }
        if (presentationBoundaryLogs++ >= MAX_PRESENTATION_BOUNDARY_LOGS) {
            return;
        }
        Framebuffer minecraftTarget = mc != null ? MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        MainMod.LOGGER.info(
                "[AUSMPresentationBoundary] call={} stage={} active={} worldFrame={} direct={} gui={} screen={} hideGui={} thirdPerson={} targetIsMc={} target={} targetColor={} targetDepth={} mcTarget={} mcColor={} backColor={} frontColor={} drawFbo={} readFbo={} drawBuf={} readBuf={} viewport={}/{}/{}/{} size={}x{} display={}x{} gl={} glErrors={}",
                presentationBoundaryLogs,
                stage,
                isPipelineActive,
                worldFrameActive,
                self().shouldDirectPresentFramebuffer(),
                renderingGuiScreen(),
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null
                        ? MinecraftReflectionCompat.currentScreen(mc).getClass().getName()
                        : "none",
                mc != null
                        && MinecraftReflectionCompat.gameSettings(mc) != null
                        && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && MinecraftReflectionCompat.gameSettings(mc) != null
                        ? MinecraftReflectionCompat.thirdPersonView(MinecraftReflectionCompat.gameSettings(mc))
                        : -1,
                target != null && target == minecraftTarget,
                self().describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                self().describeFramebufferTargetDetailed(minecraftTarget),
                framebufferSamples(minecraftTarget),
                sampleWindow ? framebufferIdColorSamples(0, safeWidth, safeHeight, GL11.GL_BACK) : "skipped",
                sampleWindow ? framebufferIdColorSamples(0, safeWidth, safeHeight, GL11.GL_FRONT) : "skipped",
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER),
                viewport.get(0),
                viewport.get(1),
                viewport.get(2),
                viewport.get(3),
                safeWidth,
                safeHeight,
                mc != null ? MinecraftReflectionCompat.displayWidth(mc) : -1,
                mc != null ? MinecraftReflectionCompat.displayHeight(mc) : -1,
                PipelineContext.glStateSummary(),
                drainGlErrorsForProbe()
        );
    }

    public ShaderProgram getProgram(RenderPass pass) {
        PipelineProgram program = effectivePipelineProgram(pass);
        return program != null ? program.shaderProgram() : null;
    }

    protected PipelineProgram effectiveDistantHorizonsPipelineProgram() {
        PipelineProgram pipelineProgram = effectivePipelineProgram(currentDistantHorizonsPass);
        if (pipelineProgram == null && currentDistantHorizonsPass != RenderPass.DH_TERRAIN) {
            pipelineProgram = effectivePipelineProgram(RenderPass.DH_TERRAIN);
        }
        return pipelineProgram != null && pipelineProgram.shaderProgram() != null ? pipelineProgram : null;
    }

    public boolean shouldUseDistantHorizonsShaderProgram() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return true;
        }
        return ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && MinecraftReflectionCompat.minecraft() != null
                && MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) != null;
    }

    public int distantHorizonsShaderProgramId() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            return self().ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
        }
        PipelineProgram pipelineProgram = self().effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram != null) {
            return pipelineProgram.shaderProgram().getId();
        }
        return self().ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
    }

    public void bindDistantHorizonsShaderProgram() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            self().bindDistantHorizonsFallbackProgram();
            return;
        }
        PipelineProgram pipelineProgram = self().effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram == null) {
            self().bindDistantHorizonsFallbackProgram();
            return;
        }

        ShaderProgram program = pipelineProgram.shaderProgram();
        RenderPass pass = pipelineProgram.pass();
        currentDistantHorizonsProgram = program;
        currentDistantHorizonsFallbackProgram = false;
        self().bindDistantHorizonsVertexArray();
        self().configureDistantHorizonsShaderState(pipelineProgram);
        program.bind();
        bindProgramResources(pass, program);
    }

    public void unbindDistantHorizonsShaderProgram() {
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = false;
        MinecraftReflectionCompat.glUseProgram(0);
        GL30.glBindVertexArray(0);
    }

    protected void configureDistantHorizonsShaderState(PipelineProgram pipelineProgram) {
        RenderPass pass = pipelineProgram.pass();
        List<Attachment> drawBuffers = pipelineProgram.effectiveDrawBuffers(programs);
        if (drawBuffers.isEmpty()) {
            drawBuffers = List.of(fallbackColorAttachment());
        } else if ((pass == RenderPass.DH_TERRAIN || pass == RenderPass.DH_WATER)
                && drawBuffers.size() == 1
                && drawBuffers.get(0) == Attachment.COLOR) {
            drawBuffers = List.of(Attachment.COLOR, Attachment.AUX3);
        }
        applyAlphaTest(pass);
        applyBlendMode(pass, drawBuffers);
        applyOitDepthState(pass);
        applyGbufferDepthState(pass);
        pingPongManager.bindForGbuffers(drawBuffers.toArray(new Attachment[0]));
        restoreVanillaWorldTextureBindings();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glColorMask(true, true, true, true);
        if (usesBlockAtlas(pass)) {
            bindBlockAtlas();
        }
        TextureBinder.bindGbufferRenderTargetSamplers();
        if (usesBlockAtlas(pass)) {
            bindBlockAtlas();
        }
        TextureBinder.bindNoiseTexture();
        TextureBinder.bindShadowTextures();
        TextureBinder.bindMaterialFallbackTextures();
    }

    public void bindDistantHorizonsVertexBuffer(int bufferId) {
        self().bindDistantHorizonsVertexArray();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, bufferId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
        GL30.glVertexAttribIPointer(0, 4, GL11.GL_UNSIGNED_SHORT, 16, 0L);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 16, 8L);
        GL30.glVertexAttribIPointer(2, 4, GL11.GL_UNSIGNED_BYTE, 16, 12L);
        if (ExtendedVertexFormats.isAttributeAvailable(ExtendedVertexFormats.DH_MATERIAL_ID_ATTRIBUTE)) {
            GL20.glEnableVertexAttribArray(ExtendedVertexFormats.DH_MATERIAL_ID_ATTRIBUTE);
            GL30.glVertexAttribIPointer(
                    ExtendedVertexFormats.DH_MATERIAL_ID_ATTRIBUTE,
                    1,
                    GL11.GL_UNSIGNED_BYTE,
                    16,
                    12L
            );
        }
    }

    protected void bindDistantHorizonsVertexArray() {
        if (distantHorizonsVertexArray < 0) {
            distantHorizonsVertexArray = GL30.glGenVertexArrays();
        }
        GL30.glBindVertexArray(distantHorizonsVertexArray);
    }

    protected void bindDistantHorizonsFallbackProgram() {
        if (!self().ensureDistantHorizonsFallbackProgram()) {
            return;
        }
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = true;
        self().bindDistantHorizonsVertexArray();
        MinecraftReflectionCompat.glUseProgram(distantHorizonsFallbackProgramId);
        self().uploadDistantHorizonsFallbackMatrices();
        self().uploadDistantHorizonsFallbackModelOffset();
    }

    protected boolean ensureDistantHorizonsFallbackProgram() {
        if (distantHorizonsFallbackProgramId != 0) {
            return true;
        }
        if (distantHorizonsFallbackProgramFailed || !GLContext.getCapabilities().OpenGL30) {
            return false;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = self().compileDistantHorizonsFallbackShader(GL20.GL_VERTEX_SHADER, DistantHorizonsInternalShaders.FALLBACK_VERTEX);
            fragmentShader = self().compileDistantHorizonsFallbackShader(GL20.GL_FRAGMENT_SHADER, DistantHorizonsInternalShaders.FALLBACK_FRAGMENT);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glBindAttribLocation(program, 0, "vPosition");
            GL20.glBindAttribLocation(program, 1, "color");
            GL20.glBindAttribLocation(program, 2, "dhMaterialData");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                MainMod.LOGGER.warn("[DistantHorizons] Failed to link fallback shader: {}", GL20.glGetProgramInfoLog(program, 4096));
                GL20.glDeleteProgram(program);
                distantHorizonsFallbackProgramFailed = true;
                return false;
            }

            distantHorizonsFallbackProgramId = program;
            distantHorizonsFallbackCombinedMatrixUniform = GL20.glGetUniformLocation(program, "uCombinedMatrix");
            distantHorizonsFallbackProjectionMatrixUniform = GL20.glGetUniformLocation(program, "uProjectionMatrix");
            distantHorizonsFallbackModelViewMatrixUniform = GL20.glGetUniformLocation(program, "uModelViewMatrix");
            distantHorizonsFallbackModelOffsetUniform = GL20.glGetUniformLocation(program, "uModelOffset");
            distantHorizonsFallbackWorldYOffsetUniform = GL20.glGetUniformLocation(program, "uWorldYOffset");
            distantHorizonsFallbackMircoOffsetUniform = GL20.glGetUniformLocation(program, "uMircoOffset");
            distantHorizonsFallbackEarthRadiusUniform = GL20.glGetUniformLocation(program, "uEarthRadius");
            MainMod.LOGGER.info("[DistantHorizons] Created AUSM fallback shader program.");
            return true;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    protected int compileDistantHorizonsFallbackShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            MainMod.LOGGER.warn("[DistantHorizons] Failed to compile fallback shader stage {}: {}",
                    type,
                    GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            distantHorizonsFallbackProgramFailed = true;
            return 0;
        }
        return shader;
    }

    protected void uploadDistantHorizonsFallbackMatrices() {
        if (distantHorizonsFallbackProgramId == 0) {
            return;
        }
        if (distantHorizonsFallbackCombinedMatrixUniform >= 0) {
            FloatBuffer combinedMatrix = distantHorizonsMatrices.modelViewProjection().duplicate();
            combinedMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackCombinedMatrixUniform, false, combinedMatrix);
        }
        if (distantHorizonsFallbackProjectionMatrixUniform >= 0) {
            FloatBuffer projectionMatrix = distantHorizonsMatrices.projection().duplicate();
            projectionMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackProjectionMatrixUniform, false, projectionMatrix);
        }
        if (distantHorizonsFallbackModelViewMatrixUniform >= 0) {
            FloatBuffer modelViewMatrix = distantHorizonsMatrices.modelView().duplicate();
            modelViewMatrix.position(0);
            GL20.glUniformMatrix4(distantHorizonsFallbackModelViewMatrixUniform, false, modelViewMatrix);
        }
        if (distantHorizonsFallbackMircoOffsetUniform >= 0) {
            GL20.glUniform1f(distantHorizonsFallbackMircoOffsetUniform, 0.01F);
        }
        if (distantHorizonsFallbackEarthRadiusUniform >= 0) {
            GL20.glUniform1f(distantHorizonsFallbackEarthRadiusUniform, 0.0F);
        }
    }

    protected void uploadDistantHorizonsFallbackModelOffset() {
        if (distantHorizonsFallbackProgramId != 0 && distantHorizonsFallbackModelOffsetUniform >= 0) {
            float[] modelOffset = distantHorizonsMatrices.modelOffset();
            GL20.glUniform3f(distantHorizonsFallbackModelOffsetUniform, modelOffset[0], modelOffset[1], modelOffset[2]);
        }
    }

    protected void deleteDistantHorizonsFallbackProgram() {
        currentDistantHorizonsFallbackProgram = false;
        if (distantHorizonsFallbackProgramId != 0) {
            GL20.glDeleteProgram(distantHorizonsFallbackProgramId);
        }
        distantHorizonsFallbackProgramId = 0;
        distantHorizonsFallbackCombinedMatrixUniform = -1;
        distantHorizonsFallbackProjectionMatrixUniform = -1;
        distantHorizonsFallbackModelViewMatrixUniform = -1;
        distantHorizonsFallbackModelOffsetUniform = -1;
        distantHorizonsFallbackWorldYOffsetUniform = -1;
        distantHorizonsFallbackMircoOffsetUniform = -1;
        distantHorizonsFallbackEarthRadiusUniform = -1;
        distantHorizonsFallbackProgramFailed = false;
    }

    protected boolean ensureDistantHorizonsCompositeProgram() {
        if (distantHorizonsCompositeProgramId != 0) {
            return true;
        }
        if (distantHorizonsCompositeProgramFailed || !MinecraftReflectionCompat.fieldBoolean(OpenGlHelper.class, false, "field_148824_g", "shadersSupported")) {
            return false;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = self().compileDistantHorizonsCompositeShader(GL20.GL_VERTEX_SHADER, DistantHorizonsInternalShaders.COMPOSITE_VERTEX);
            fragmentShader = self().compileDistantHorizonsCompositeShader(GL20.GL_FRAGMENT_SHADER, DistantHorizonsInternalShaders.COMPOSITE_FRAGMENT);
            if (vertexShader == 0 || fragmentShader == 0) {
                return false;
            }

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                MainMod.LOGGER.warn("[DistantHorizons] Failed to link composite shader: {}", GL20.glGetProgramInfoLog(program, 4096));
                GL20.glDeleteProgram(program);
                distantHorizonsCompositeProgramFailed = true;
                return false;
            }

            distantHorizonsCompositeProgramId = program;
            distantHorizonsCompositeTextureUniform = GL20.glGetUniformLocation(program, "dhColor");
            distantHorizonsCompositeDepthUniform = GL20.glGetUniformLocation(program, "dhDepth");
            return true;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }
}
