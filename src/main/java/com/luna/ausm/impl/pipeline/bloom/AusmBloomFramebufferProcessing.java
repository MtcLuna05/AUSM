package com.luna.ausm.impl.pipeline.bloom;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

abstract class AusmBloomFramebufferProcessing extends AusmBloomRenderPasses {
    protected static String sampleFramebufferColorAtPixel(Framebuffer framebuffer, int x, int y) {
        if (framebuffer == null) {
            return "n/a";
        }
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int sampleX = Math.clamp(x, 0, width - 1);
        int sampleY = Math.clamp(y, 0, height - 1);
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        FloatBuffer sample = BufferUtils.createFloatBuffer(4);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(sampleX, sampleY, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, sample);
            return framebufferId + "@" + sampleX + "/" + sampleY + "="
                    + sample.get(0) + "," + sample.get(1) + "," + sample.get(2) + "," + sample.get(3);
        } catch (RuntimeException | LinkageError error) {
            return "error=" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        }
    }

    protected static String sampleFramebufferColorAt(Framebuffer framebuffer, int x, int y) {
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        FloatBuffer sample = BufferUtils.createFloatBuffer(4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, sample);
            return framebufferId + "@" + x + "/" + y + "="
                    + sample.get(0) + "," + sample.get(1) + "," + sample.get(2) + "," + sample.get(3);
        } catch (RuntimeException | LinkageError error) {
            return "error=" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected void renderFramebufferBloom(Framebuffer target) {
        if (target == null || MinecraftReflectionCompat.framebufferTexture(target) <= 0) {
            return;
        }
        if (!self().ensureTargets(MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target))) {
            return;
        }

        RenderState state = self().captureState();
        try {
            if (self().runThresholdBlurChain(MinecraftReflectionCompat.framebufferTexture(target))) {
                self().compositeBlurredBloom(target, FRAMEBUFFER_BLOOM_STRENGTH);
                if (framebufferBloomLogs < BLOOM_RENDER_LOG_LIMIT) {
                    framebufferBloomLogs++;

                }
            } else if (framebufferBloomLogs < BLOOM_RENDER_LOG_LIMIT) {
                framebufferBloomLogs++;

            }
        } finally {
            state.restore();
        }
    }

    protected boolean runBlurChain(int sourceTexture, boolean depthAware) {
        if (sourceTexture <= 0 || !self().ensureTargets(width, height)) {
            return false;
        }
        if (self().copyProgram() == -1 || self().blurProgram() == -1) {
            return false;
        }

        self().bindHalfTarget(bloomDownsampleTarget);
        MinecraftReflectionCompat.glUseProgram(copyProgram);
        self().bindTextureUniform(copyProgram, "source", sourceTexture, 0);
        self().bindDepthAwareCopyUniforms(depthAware);
        AusmBloomRenderer.drawFullscreenQuad();

        for (int i = 0; i < blurIterations; i++) {
            self().bindHalfTarget(bloomBlurTarget);
            MinecraftReflectionCompat.glUseProgram(blurProgram);
            self().bindTextureUniform(blurProgram, "source", MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), 0);
            self().bindDepthAwareBlurUniforms(depthAware);
            AusmBloomRenderer.setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            AusmBloomRenderer.drawFullscreenQuad();

            self().bindHalfTarget(bloomDownsampleTarget);
            MinecraftReflectionCompat.glUseProgram(blurProgram);
            self().bindTextureUniform(blurProgram, "source", MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            self().bindDepthAwareBlurUniforms(depthAware);
            AusmBloomRenderer.setUniform2f(blurProgram, "direction", 0.0F, 1.0F / Math.max(1, halfHeight));
            AusmBloomRenderer.drawFullscreenQuad();
        }
        return true;
    }

    protected void bindDepthAwareBlurUniforms(boolean depthAware) {
        boolean enabled = depthAware && bloomDepthTexture > 0 && finalDepthTexture > 0;
        if (enabled) {
            self().bindTextureUniform(blurProgram, "bloomDepth", bloomDepthTexture, 1);
            self().bindTextureUniform(blurProgram, "sceneDepth", finalDepthTexture, 2);
        }
        AusmBloomRenderer.setUniform1i(blurProgram, "depthAware", enabled ? 1 : 0);
    }

    protected void bindDepthAwareCopyUniforms(boolean depthAware) {
        boolean enabled = depthAware && bloomDepthTexture > 0 && finalDepthTexture > 0;
        if (enabled) {
            self().bindTextureUniform(copyProgram, "bloomDepth", bloomDepthTexture, 1);
            self().bindTextureUniform(copyProgram, "sceneDepth", finalDepthTexture, 2);
        }
        AusmBloomRenderer.setUniform2f(copyProgram, "sourceTexel", 1.0F / Math.max(1, width), 1.0F / Math.max(1, height));
        AusmBloomRenderer.setUniform1i(copyProgram, "depthAware", enabled ? 1 : 0);
    }

    protected boolean runThresholdBlurChain(int sourceTexture) {
        if (sourceTexture <= 0 || !self().ensureTargets(width, height)) {
            return false;
        }
        if (self().thresholdProgram() == -1 || self().blurProgram() == -1) {
            return false;
        }

        self().bindHalfTarget(bloomDownsampleTarget);
        MinecraftReflectionCompat.glUseProgram(thresholdProgram);
        self().bindTextureUniform(thresholdProgram, "source", sourceTexture, 0);
        AusmBloomRenderer.setUniform1f(thresholdProgram, "threshold", FRAMEBUFFER_BLOOM_THRESHOLD);
        AusmBloomRenderer.drawFullscreenQuad();

        for (int i = 0; i < blurIterations; i++) {
            self().bindHalfTarget(bloomBlurTarget);
            MinecraftReflectionCompat.glUseProgram(blurProgram);
            self().bindTextureUniform(blurProgram, "source", MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), 0);
            self().bindDepthAwareBlurUniforms(false);
            AusmBloomRenderer.setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            AusmBloomRenderer.drawFullscreenQuad();

            self().bindHalfTarget(bloomDownsampleTarget);
            MinecraftReflectionCompat.glUseProgram(blurProgram);
            self().bindTextureUniform(blurProgram, "source", MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            self().bindDepthAwareBlurUniforms(false);
            AusmBloomRenderer.setUniform2f(blurProgram, "direction", 0.0F, 1.0F / Math.max(1, halfHeight));
            AusmBloomRenderer.drawFullscreenQuad();
        }
        return true;
    }

    protected void compositeBlurredBloom(Framebuffer target, float strength) {
        self().compositeTexture(target, MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), strength);
    }

    protected void compositeBlurredBloom(Framebuffer target, float strength, int preHandDepthTexture, int postHandDepthTexture,
                                         boolean useSceneDepthMask) {
        self().compositeTexture(target, MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), strength,
                preHandDepthTexture, postHandDepthTexture, useSceneDepthMask, true);
    }

    protected void compositeTexture(Framebuffer target, int texture, float strength) {
        self().compositeTexture(target, texture, strength, 0, 0, false, false);
    }

    protected void compositeTexture(Framebuffer target, int texture, float strength,
                                    int preHandDepthTexture, int postHandDepthTexture, boolean useSceneDepthMask,
                                    boolean bloomTextureCarriesDepth) {
        if (target == null || self().compositeProgram() == -1) {
            return;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target));

        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateDisableAlpha();
        // A shader pass may leave any sampler unit active. Enabling fixed-
        // function texturing before returning to unit zero enables that
        // auxiliary unit as a legacy texture stage; once shaders are disabled,
        // particles and terrain then combine it with their atlas coordinates.
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (shaderPackCompositeOverride && shaderPackCompositeReplace) {
            // The Lab shader discards black pixels.  Its remaining pixels
            // replace the world target so a white destination cannot hide the
            // parsed bloom hue through screen blending.
            MinecraftReflectionCompat.glStateDisableBlend();
        } else {
            MinecraftReflectionCompat.glStateEnableBlend();
            // Additive ONE+ONE blending clips the first channel that reaches one,
            // turning saturated colored bloom into white.  Screen composition
            // is source + destination * (1 - source); the previous reversed
            // factors instead multiplied Bloom by (1 - destination), making
            // it almost invisible over the bright framed materials it needs to
            // preserve.
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE);
        }
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

        MinecraftReflectionCompat.glUseProgram(compositeProgram);
        self().bindTextureUniform(compositeProgram, "bloom", texture, 0);
        boolean useHandMask = preHandDepthTexture > 0 && postHandDepthTexture > 0;
        if (useHandMask) {
            self().bindTextureUniform(compositeProgram, "preHandDepth", preHandDepthTexture, 1);
            self().bindTextureUniform(compositeProgram, "postHandDepth", postHandDepthTexture, 2);
        }
        if (useSceneDepthMask) {
            self().bindTextureUniform(compositeProgram, "bloomDepth", bloomDepthTexture, 3);
            self().bindTextureUniform(compositeProgram, "finalDepth", finalDepthTexture, 4);
        }
        if (translucentAttenuationAvailable && translucentAttenuationTarget != null) {
            self().bindTextureUniform(compositeProgram, "translucentTransmission",
                    MinecraftReflectionCompat.framebufferTexture(translucentAttenuationTarget), 5);
            self().bindTextureUniform(compositeProgram, "translucentDepth", translucentDepthTexture, 6);
        }
        AusmBloomRenderer.setUniform1f(compositeProgram, "strength", strength);
        AusmBloomRenderer.setUniform1i(compositeProgram, "useHandMask", useHandMask ? 1 : 0);
        AusmBloomRenderer.setUniform1i(compositeProgram, "useSceneDepthMask", useSceneDepthMask ? 1 : 0);
        AusmBloomRenderer.setUniform1i(compositeProgram, "useBloomTextureDepth", bloomTextureCarriesDepth ? 1 : 0);
        AusmBloomRenderer.setUniform1i(compositeProgram, "useTranslucentDampening", translucentAttenuationAvailable ? 1 : 0);
        AusmBloomRenderer.drawFullscreenQuad();
    }

    protected int renderBloomGeometry(RenderGlobal renderGlobal, BlockRenderLayer bloomLayer,
                                      double partialTicks, int pass, Entity entity,
                                      boolean writeDepth, boolean writeColor) {
        AusmBloomRenderer.bindBlockAtlasOnDefaultTextureUnit();

        int geometryProgram = self().nativeBloomGeometryProgram();
        MinecraftReflectionCompat.glUseProgram(Math.max(geometryProgram, 0));
        if (geometryProgram > 0) {
            AusmBloomRenderer.bindSamplerUniform(geometryProgram, "terrain", 0);
            AusmBloomRenderer.setUniform1f(geometryProgram, "framedBloomScale",
                    PipelineContext.getInstance().isActive() ? SHADERED_FRAMED_BLOOM_SOURCE_SCALE : 1.0F);
        }
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        // Source faces start against copied terrain depth. Private targets also
        // record the winning bloom fragments' real depth; shaderless mode uses
        // a private depth-only replay so Minecraft's live depth is never changed.
        MinecraftReflectionCompat.glStateDepthMask(writeDepth);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR, SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateColorMask(
                writeColor, writeColor, writeColor, writeColor);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            Object entityRenderer = minecraft == null ? null : MinecraftReflectionCompat.entityRenderer(minecraft);
            if (entityRenderer instanceof EntityRendererAccessor) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                pushedProjection = true;
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                pushedModelView = true;
                ((EntityRendererAccessor) entityRenderer).ausm$setupCameraTransform((float) partialTicks, 2);
                MatrixState.captureGbufferMatrices();
            }
            // Read Nothirium VBO data directly when present. Its renderer is never
            // invoked here because it would replace AUSM's frontend program/state.
            int rendered = PipelineContext.getInstance().renderAusmOwnedNothiriumBloomGeometry(partialTicks, entity);
            if (rendered <= 0) {
                // A sparse or not-yet-uploaded Nothirium BLOOM list must not
                // suppress the normal RenderGlobal BLOOM layer.
                rendered = MinecraftReflectionCompat.renderBlockLayer(renderGlobal, bloomLayer, partialTicks, pass, entity);
            }
            return rendered;
        } finally {
            if (pushedModelView) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (pushedProjection) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            MinecraftReflectionCompat.glUseProgram(0);
        }
    }

    protected boolean captureTranslucentAttenuation(RenderGlobal renderGlobal, double partialTicks, int pass,
                                                    Entity entity, DeferredFramebuffer pipelineDepthSource,
                                                    Framebuffer minecraftDepthSource) {
        int program = self().translucentAttenuationProgram();
        if (program == -1 || translucentAttenuationTarget == null || translucentDepthTexture <= 0) {
            return false;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        MinecraftReflectionCompat.bindFramebuffer(translucentAttenuationTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glClearDepth(1.0D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        self().copyDepth(pipelineDepthSource, minecraftDepthSource, translucentAttenuationTarget);

        AusmBloomRenderer.bindBlockAtlasOnDefaultTextureUnit();
        MinecraftReflectionCompat.glUseProgram(program);
        self().bindTextureUniform(program, "terrain", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), 0);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.001F);
        MinecraftReflectionCompat.glStateEnableBlend();
        // Each back-to-front translucent surface multiplies the accumulated
        // light transmission. Depth writes retain the nearest translucent
        // surface while still accepting every subsequently-nearer layer.
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        int rendered;
        try {
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            Object entityRenderer = minecraft == null ? null : MinecraftReflectionCompat.entityRenderer(minecraft);
            if (entityRenderer instanceof EntityRendererAccessor) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPushMatrix();
                pushedProjection = true;
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPushMatrix();
                pushedModelView = true;
                ((EntityRendererAccessor) entityRenderer).ausm$setupCameraTransform((float) partialTicks, 2);
                MatrixState.captureGbufferMatrices();
            }
            rendered = PipelineContext.getInstance()
                    .renderAusmOwnedNothiriumTranslucentGeometry(partialTicks, entity);
            if (rendered <= 0) {
                rendered = MinecraftReflectionCompat.renderBlockLayer(
                        renderGlobal, BlockRenderLayer.TRANSLUCENT, partialTicks, pass, entity);
            }
        } finally {
            if (pushedModelView) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (pushedProjection) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
        }

        // GlobalFacades supplies world-space overlay vertices and applies its
        // own camera translation, so render it after restoring the matrices
        // used by the chunk-VBO replay.
        AusmBloomRenderer.bindBlockAtlasOnDefaultTextureUnit();
        MinecraftReflectionCompat.glUseProgram(program);
        self().bindTextureUniform(program, "terrain", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), 0);
        rendered += self().renderGlobalFacadesTranslucentAttenuationGeometry();
        MinecraftReflectionCompat.glUseProgram(0);

        return rendered > 0 && self().copyDepthTexture(translucentAttenuationTarget, translucentDepthTexture);
    }

    protected int renderGlobalFacadesBloomGeometry() {
        self().resolveGlobalFacadesBloomBridge();
        if (globalFacadesBloomMethod == null) {
            return 0;
        }
        try {
            Object result = globalFacadesBloomMethod.invoke(null);
            boolean rendered = result instanceof Boolean && (Boolean) result;
            if (rendered && !loggedGlobalFacadesBloomBridge) {
                loggedGlobalFacadesBloomBridge = true;
                MainMod.LOGGER.info("[AUSMGlobalFacadesBloom] Rendering facade bloom through the native AUSM depth-tested target.");
            }
            return rendered ? 1 : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    protected int renderGlobalFacadesTranslucentAttenuationGeometry() {
        self().resolveGlobalFacadesBloomBridge();
        if (globalFacadesTranslucentAttenuationMethod == null) {
            return 0;
        }
        try {
            Object result = globalFacadesTranslucentAttenuationMethod.invoke(null);
            return result instanceof Boolean && (Boolean) result ? 1 : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    protected void resolveGlobalFacadesBloomBridge() {
        if (globalFacadesBloomResolved) {
            return;
        }
        globalFacadesBloomResolved = true;
        try {
            Class<?> renderer = Class.forName("com.luna.globalfacades.client.render.FacadeWorldRenderer");
            globalFacadesBloomMethod = renderer.getMethod("renderBloomForAusm");
            try {
                globalFacadesTranslucentAttenuationMethod = renderer.getMethod(
                        "renderTranslucentAttenuationForAusm");
            } catch (NoSuchMethodException ignored) {
                globalFacadesTranslucentAttenuationMethod = null;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            globalFacadesBloomMethod = null;
            globalFacadesTranslucentAttenuationMethod = null;
        }
    }

    protected void clearLayerTarget(boolean preserveDepth) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        if (!preserveDepth) {
            MinecraftReflectionCompat.glStateDepthMask(true);
        }
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClearDepth(1.0D);
        GL11.glClear(preserveDepth ? GL11.GL_COLOR_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    protected boolean attachMinecraftDepth(Framebuffer minecraftDepthSource) {
        if (minecraftDepthSource == null || bloomLayerTarget == null) {
            return false;
        }
        int sourceDepth = MinecraftReflectionCompat.fieldInt(
                minecraftDepthSource, 0, "field_147624_h", "depthBuffer");
        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
        if (sourceDepth <= 0 || targetFramebuffer <= 0) {
            return false;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, sourceDepth);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    protected void restoreLayerDepthAttachment() {
        if (bloomLayerTarget == null) {
            return;
        }
        int layerDepth = MinecraftReflectionCompat.fieldInt(
                bloomLayerTarget, 0, "field_147624_h", "depthBuffer");
        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
        if (layerDepth <= 0 || targetFramebuffer <= 0) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, layerDepth);
    }

    protected void logDepthAttachmentProbe(String stage, Framebuffer minecraftDepthSource, boolean sharedDepth) {
        // Depth-attachment diagnostics are disabled outside focused F1 investigations.
        return;
            /*
            if (bloomLayerTarget == null) {
                return;
            }
            int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
            int sourceFramebuffer = minecraftDepthSource == null ? -1
                    : MinecraftReflectionCompat.framebufferObject(minecraftDepthSource);
            int sourceDepth = minecraftDepthSource == null ? -1
                    : MinecraftReflectionCompat.fieldInt(minecraftDepthSource, 0, "field_147624_h", "depthBuffer");
            int layerDepth = MinecraftReflectionCompat.fieldInt(bloomLayerTarget, 0,
                    "field_147624_h", "depthBuffer");
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            float bloomCenter = Float.NaN;
            float sourceCenter = Float.NaN;
            int attachedName = -1;
            int attachedType = -1;
            try {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, targetFramebuffer);
                bloomCenter = readCenterDepth();
                attachedType = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                attachedName = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER,
                        GL30.GL_DEPTH_ATTACHMENT, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                if (sourceFramebuffer >= 0) {
                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
                    sourceCenter = readCenterDepth();
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Diagnostic-only: leave rendering unaffected on unusual drivers.
            } finally {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            }
            */
    }

    protected float readCenterDepth() {
        FloatBuffer sample = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(Math.max(0, width / 2), Math.max(0, height / 2), 1, 1,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, sample);
        return sample.get(0);
    }

    protected void copyDepth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        self().copyDepth(pipelineDepthSource, minecraftDepthSource, bloomLayerTarget);
    }

    protected void copyDepth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource,
                             Framebuffer destination) {
        if (destination == null) {
            return;
        }
        if (pipelineDepthSource != null) {
            pipelineDepthSource.blitDepthTo(MinecraftReflectionCompat.framebufferObject(destination), width, height);
            return;
        }
        if (minecraftDepthSource == null || MinecraftReflectionCompat.fieldInt(minecraftDepthSource, 0, "field_147624_h", "depthBuffer") <= 0) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(minecraftDepthSource));
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(destination));
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    MinecraftReflectionCompat.framebufferWidth(minecraftDepthSource),
                    MinecraftReflectionCompat.framebufferHeight(minecraftDepthSource),
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    protected void bindLayerTargetForGeometry() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
    }

    protected void bindHalfTarget(Framebuffer framebuffer) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, halfWidth, halfHeight);
        // Shaderless Bloom captures translucent attenuation immediately before
        // this chain. That pass intentionally leaves multiplicative blending
        // active; applying it to a freshly-cleared blur target multiplies every
        // source pixel into black. Fullscreen copy/blur passes must own their
        // complete raster state instead of inheriting the preceding world pass.
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateDisableAlpha();
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    protected boolean ensureTargets(int targetWidth, int targetHeight) {
        if (!MinecraftReflectionCompat.isFramebufferEnabled() || targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }

        int newHalfWidth = Math.max(1, targetWidth / HALF_RESOLUTION_DIVISOR);
        int newHalfHeight = Math.max(1, targetHeight / HALF_RESOLUTION_DIVISOR);
        if (bloomLayerTarget != null
                && targetWidth == width
                && targetHeight == height
                && newHalfWidth == halfWidth
                && newHalfHeight == halfHeight) {
            return true;
        }

        width = targetWidth;
        height = targetHeight;
        halfWidth = newHalfWidth;
        halfHeight = newHalfHeight;
        bloomLayerTarget = AusmBloomRenderer.resizeFramebuffer(bloomLayerTarget, width, height, true);
        bloomDownsampleTarget = AusmBloomRenderer.resizeFramebuffer(bloomDownsampleTarget, halfWidth, halfHeight, false);
        bloomBlurTarget = AusmBloomRenderer.resizeFramebuffer(bloomBlurTarget, halfWidth, halfHeight, false);
        translucentAttenuationTarget = AusmBloomRenderer.resizeFramebuffer(translucentAttenuationTarget, width, height, true);
        AusmBloomRenderer.configureFloatingColorAttachment(bloomDownsampleTarget, halfWidth, halfHeight);
        AusmBloomRenderer.configureFloatingColorAttachment(bloomBlurTarget, halfWidth, halfHeight);
        return bloomLayerTarget != null && bloomDownsampleTarget != null && bloomBlurTarget != null
                && translucentAttenuationTarget != null
                && self().ensureDepthMaskTextures();
    }

    protected static Framebuffer resizeFramebuffer(Framebuffer framebuffer, int width, int height, boolean depth) {
        if (framebuffer == null) {
            framebuffer = new Framebuffer(width, height, depth);
            MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147604_a", "setFramebufferColor"},
                    new Class<?>[]{float.class, float.class, float.class, float.class},
                    0.0F, 0.0F, 0.0F, 0.0F);
        } else {
            MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147613_a", "createBindFramebuffer"},
                    new Class<?>[]{int.class, int.class}, width, height);
            MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147604_a", "setFramebufferColor"},
                    new Class<?>[]{float.class, float.class, float.class, float.class},
                    0.0F, 0.0F, 0.0F, 0.0F);
        }
        MinecraftReflectionCompat.invoke(framebuffer, new String[]{"func_147607_a", "setFramebufferFilter"},
                new Class<?>[]{int.class}, GL11.GL_LINEAR);
        return framebuffer;
    }

    protected static void configureFloatingColorAttachment(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null) {
            return;
        }
        int texture = MinecraftReflectionCompat.framebufferTexture(framebuffer);
        if (texture <= 0) {
            return;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    protected boolean ensureDepthMaskTextures() {
        bloomDepthTexture = AusmBloomRenderer.resizeDepthTexture(bloomDepthTexture, width, height);
        finalDepthTexture = AusmBloomRenderer.resizeDepthTexture(finalDepthTexture, width, height);
        translucentDepthTexture = AusmBloomRenderer.resizeDepthTexture(translucentDepthTexture, width, height);
        return bloomDepthTexture > 0 && finalDepthTexture > 0 && translucentDepthTexture > 0;
    }

    protected static int resizeDepthTexture(int texture, int width, int height) {
        if (texture <= 0) {
            texture = GL11.glGenTextures();
        }
        if (texture <= 0) {
            return 0;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, width, height, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
        return texture;
    }

    protected boolean copyDepthTexture(Framebuffer source, boolean bloomDepth) {
        if (source == null || width <= 0 || height <= 0 || bloomDepthTexture <= 0 || finalDepthTexture <= 0) {
            return false;
        }
        return self().copyDepthTexture(source, bloomDepth ? bloomDepthTexture : finalDepthTexture);
    }

    protected boolean copyDepthTexture(Framebuffer source, int destinationTexture) {
        if (source == null || width <= 0 || height <= 0 || destinationTexture <= 0) {
            return false;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(source));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, destinationTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            return GL11.glGetError() == GL11.GL_NO_ERROR;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        }
    }

    protected int targetWidth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        if (pipelineDepthSource != null) {
            return pipelineDepthSource.getAttachmentWidth(Attachment.COLOR);
        }
        return minecraftDepthSource != null ? MinecraftReflectionCompat.framebufferWidth(minecraftDepthSource) : 0;
    }

    protected int targetHeight(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        if (pipelineDepthSource != null) {
            return pipelineDepthSource.getAttachmentHeight(Attachment.COLOR);
        }
        return minecraftDepthSource != null ? MinecraftReflectionCompat.framebufferHeight(minecraftDepthSource) : 0;
    }
}
