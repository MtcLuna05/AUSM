package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.pipeline.pack.PipelineShaderSettings;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class AusmBloomRenderer {
    private static final int HALF_RESOLUTION_DIVISOR = 2;
    private static final int DEFAULT_BLUR_ITERATIONS = 2;
    private static final float DEFAULT_BLOOM_STRENGTH = 0.825F;
    private static final String BLOOM_VERTEX_PATH = "ausm/bloom.vsh";
    private static final String BLOOM_FRAGMENT_PATH = "ausm/bloom.fsh";
    private static final String BLOOM_STRENGTH_SETTING = "ausmBloomStrength";
    private static final String BLOOM_BLUR_ITERATIONS_SETTING = "ausmBloomBlurIterations";
    private static final float BLOOM_DIRECT_DEBUG_STRENGTH = 0.0F;
    private static final float FRAMEBUFFER_BLOOM_STRENGTH = 0.525F;
    private static final float FRAMEBUFFER_BLOOM_THRESHOLD = 0.86F;
    private static final boolean FRAMEBUFFER_BLOOM_FALLBACK_ENABLED = false;
    private static final int BLOOM_RENDER_LOG_LIMIT = 0;
    private static final int BLOOM_ZERO_RENDER_LOG_LIMIT = 8;
    private static final int BLOOM_PROBE_LIMIT = 0;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR = -1.0F;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS = -4.0F;

    private final AusmBloomResourceIndex resourceIndex = new AusmBloomResourceIndex();
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private Framebuffer bloomLayerTarget;
    private Framebuffer bloomDownsampleTarget;
    private Framebuffer bloomBlurTarget;
    private int bloomDepthTexture;
    private int finalDepthTexture;
    private int width = -1;
    private int height = -1;
    private int halfWidth = -1;
    private int halfHeight = -1;
    private int copyProgram = -1;
    private int thresholdProgram = -1;
    private int blurProgram = -1;
    private int compositeProgram = -1;
    private int emissiveExtractProgram = -1;
    private String compositeVertexSource = VERTEX_SHADER;
    private String compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;
    private float bloomStrength = DEFAULT_BLOOM_STRENGTH;
    private int blurIterations = DEFAULT_BLUR_ITERATIONS;
    private boolean shaderPackCompositeOverride;
    private boolean layerBloomPending;
    private boolean loggedLayerRenderer;
    private boolean loggedShaderlessEmissiveRenderer;
    private boolean loggedProgramFailure;
    private int bloomCompositeLogs;
    private int framebufferBloomLogs;
    private int bloomRenderLogs;
    private int zeroBloomRenderLogs;
    private int depthMaskProbeLogs;
    private int bloomOcclusionProbeLogs;
    private int bloomFrameProbeCalls;
    private int bloomCompositeProbeCalls;
    private int bloomOcclusionQuery;
    private int depthAttachmentProbeLogs;
    private LumenizedTicketBridge lumenizedTickets;
    private boolean globalFacadesBloomResolved;
    private Method globalFacadesBloomMethod;
    private boolean loggedGlobalFacadesBloomBridge;

    public void configure(ShaderPack pack, ShaderProperties properties) {
        resetShaderPackConfiguration();
        if (pack == null || properties == null) {
            return;
        }

        bloomStrength = clamp(
                PipelineShaderSettings.parseFloatSetting(pack, properties, BLOOM_STRENGTH_SETTING, DEFAULT_BLOOM_STRENGTH),
                0.0F,
                8.0F
        );
        blurIterations = Math.max(0, Math.min(8,
                PipelineShaderSettings.parseIntSetting(pack, properties, BLOOM_BLUR_ITERATIONS_SETTING, DEFAULT_BLUR_ITERATIONS)));

        ShaderPackLayout layout = ShaderPackLayout.detect(pack);
        String vertexPath = layout.rootPath(BLOOM_VERTEX_PATH);
        String fragmentPath = layout.rootPath(BLOOM_FRAGMENT_PATH);
        try {
            if (pack.hasResource(vertexPath)) {
                String source = ShaderPreprocessor.processShaderSource(
                        pack, vertexPath, properties.options(), null, GL20.GL_VERTEX_SHADER);
                if (source != null && !source.isBlank()) {
                    compositeVertexSource = source;
                    shaderPackCompositeOverride = true;
                }
            }
            if (pack.hasResource(fragmentPath)) {
                String source = ShaderPreprocessor.processShaderSource(
                        pack, fragmentPath, properties.options(), null, GL20.GL_FRAGMENT_SHADER);
                if (source != null && !source.isBlank()) {
                    compositeFragmentSource = source;
                    shaderPackCompositeOverride = true;
                }
            }
        } catch (Exception error) {
            compositeVertexSource = VERTEX_SHADER;
            compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;
            shaderPackCompositeOverride = false;
            MainMod.LOGGER.warn("[AUSMBloom] Failed to load shaderpack bloom override; using built-in bloom.", error);
        }

        if (shaderPackCompositeOverride) {
            MainMod.LOGGER.info(
                    "[AUSMBloom] Using shaderpack bloom override for '{}' (strength={}, blurIterations={}).",
                    pack.getName(), bloomStrength, blurIterations);
        }
    }

    public int renderBloomLayer(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity,
                                DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource,
                                boolean deferComposite) {
        resourceIndex.scanOnce();

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null || renderGlobal == null || entity == null) {
            return 0;
        }

        layerBloomPending = false;
        int targetWidth = targetWidth(pipelineDepthSource, minecraftDepthSource);
        int targetHeight = targetHeight(pipelineDepthSource, minecraftDepthSource);
        if (!ensureTargets(targetWidth, targetHeight)) {
            return 0;
        }

        RenderState state = captureState();
        int rendered = 0;
        int passedSamples = -1;
        boolean sharedMinecraftDepth = false;
        try {
            // In shaderless mode, keep the bloom target attached to the live
            // terrain depth renderbuffer. Blitting depth into a private target
            // raced the world-pass FBO lifecycle and allowed bloom through walls.
            sharedMinecraftDepth = pipelineDepthSource == null && attachMinecraftDepth(minecraftDepthSource);
            clearLayerTarget(sharedMinecraftDepth);
            if (!sharedMinecraftDepth) {
                copyDepth(pipelineDepthSource, minecraftDepthSource);
            }
            logDepthAttachmentProbe("prepared", minecraftDepthSource, sharedMinecraftDepth);
            bindLayerTargetForGeometry();
            int query = 0;
            if (query > 0) {
                GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
            }
            try {
                rendered = renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity);
                rendered += renderGlobalFacadesBloomGeometry();
            } finally {
                if (query > 0) {
                    GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
                }
            }
            if (rendered > 0) {
                copyDepthTexture(bloomLayerTarget, true);
            }
            logDepthAttachmentProbe("after-geometry", minecraftDepthSource, sharedMinecraftDepth);
            if (bloomRenderLogs < BLOOM_RENDER_LOG_LIMIT) {
                bloomRenderLogs++;
                MainMod.LOGGER.info("[AUSMBloomDraw] layer={} rendered={} defer={} depthSource={} target={}",
                        bloomLayer,
                        rendered,
                        deferComposite,
                        pipelineDepthSource != null ? "pipeline" : (minecraftDepthSource != null ? "minecraft" : "none"),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
            }
            if (rendered > 0 && passedSamples != 0) {
                layerBloomPending = true;
                if (!loggedLayerRenderer) {
                    loggedLayerRenderer = true;
                    
                }
                if (!deferComposite && minecraftDepthSource != null) {
                    compositePendingLayerBloom(minecraftDepthSource, false);
                }
            }
            logBloomFrameProbe(rendered, passedSamples, sharedMinecraftDepth, deferComposite);
        } finally {
            if (sharedMinecraftDepth) {
                restoreLayerDepthAttachment();
            }
            state.restore();
        }

        if (rendered <= 0 && zeroBloomRenderLogs < BLOOM_ZERO_RENDER_LOG_LIMIT) {
            zeroBloomRenderLogs++;
            MainMod.LOGGER.info("[AUSMBloomDraw] empty layer={} resources={} nativeHook={}",
                    bloomLayer, resourceIndex.hasBloomResources(), AusmBloomLayer.shouldUseNativeHook());
        }
        return rendered;
    }

    public void renderPostWorldBloom(Framebuffer target) {
        renderPostWorldBloom(target, 0, 0);
    }

    public void renderPostWorldBloom(Framebuffer target, int preHandDepthTexture, int postHandDepthTexture) {
        if (target == null) {
            layerBloomPending = false;
            return;
        }

        resourceIndex.scanOnce();
        boolean compositedLayerBloom = false;
        if (layerBloomPending) {
            compositedLayerBloom = compositePendingLayerBloom(target, true, preHandDepthTexture, postHandDepthTexture);
        }

        if (!compositedLayerBloom
                && FRAMEBUFFER_BLOOM_FALLBACK_ENABLED
                && !PipelineContext.getInstance().isActive()
                && resourceIndex.hasBloomResources()) {
            renderFramebufferBloom(target);
        }

        layerBloomPending = false;
    }

    public boolean renderShaderlessEmissiveTerrainBloom(Framebuffer target, IntSupplier geometryRenderer) {
        return renderEmissiveTerrainBloomCount(target, null, geometryRenderer, false) > 0;
    }

    public int renderEmissiveTerrainBloomCount(Framebuffer target, DeferredFramebuffer pipelineDepthSource,
                                               IntSupplier geometryRenderer, boolean allowPipelineActive) {
        if (target == null
                || geometryRenderer == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target) <= 0
                || (!allowPipelineActive && PipelineContext.getInstance().isActive())) {
            return 0;
        }
        if (!ensureTargets(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target))) {
            return 0;
        }

        int program = emissiveExtractProgram();
        if (program == -1) {
            return 0;
        }

        int rendered = 0;
        RenderState state = captureState();
        try {
            clearLayerTarget(false);
            copyDepth(pipelineDepthSource, target);
            bindLayerTargetForGeometry();
            prepareShaderlessEmissiveGeometryState(program);
            rendered = geometryRenderer.getAsInt();
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
            state.restore();
        }

        if (rendered <= 0) {
            layerBloomPending = false;
            return 0;
        }

        layerBloomPending = true;
        if (!loggedShaderlessEmissiveRenderer) {
            loggedShaderlessEmissiveRenderer = true;
            MainMod.LOGGER.info("[AUSMBloom] Rendering shaderless emissive terrain bloom with AUSM vertex emission metadata.");
        }
        return rendered;
    }

    public void setShaderlessForceEmission(float forceEmission) {
        int program = emissiveExtractProgram;
        if (program != -1) {
            setUniform1f(program, "forceEmission", Math.max(0.0F, Math.min(1.0F, forceEmission)));
        }
    }

    public void clearPendingLayerBloom() {
        layerBloomPending = false;
    }

    public boolean hasBloomResources() {
        resourceIndex.scanOnce();
        return resourceIndex.hasBloomResources();
    }

    public boolean hasBloomSprite(String spriteName) {
        return resourceIndex.hasBloomSprite(spriteName);
    }

    public void delete() {
        deleteFramebuffer(bloomLayerTarget);
        deleteFramebuffer(bloomDownsampleTarget);
        deleteFramebuffer(bloomBlurTarget);
        bloomLayerTarget = null;
        bloomDownsampleTarget = null;
        bloomBlurTarget = null;
        deleteTexture(bloomDepthTexture);
        deleteTexture(finalDepthTexture);
        if (bloomOcclusionQuery > 0) {
            GL15.glDeleteQueries(bloomOcclusionQuery);
            bloomOcclusionQuery = 0;
        }
        bloomDepthTexture = 0;
        finalDepthTexture = 0;
        width = -1;
        height = -1;
        halfWidth = -1;
        halfHeight = -1;
        layerBloomPending = false;

        deleteProgram(copyProgram);
        deleteProgram(thresholdProgram);
        deleteProgram(blurProgram);
        deleteProgram(compositeProgram);
        deleteProgram(emissiveExtractProgram);
        copyProgram = -1;
        thresholdProgram = -1;
        blurProgram = -1;
        compositeProgram = -1;
        emissiveExtractProgram = -1;
        resetShaderPackConfiguration();
    }

    private boolean compositePendingLayerBloom(Framebuffer target, boolean captureState) {
        return compositePendingLayerBloom(target, captureState, 0, 0);
    }

    private boolean compositePendingLayerBloom(Framebuffer target, boolean captureState,
                                               int preHandDepthTexture, int postHandDepthTexture) {
        if (!layerBloomPending || bloomLayerTarget == null || target == null) {
            layerBloomPending = false;
            return false;
        }

        boolean composited = false;
        RenderState state = captureState ? captureState() : null;
        try {
            if (runBlurChain(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget))) {
                boolean useSceneDepthMask = copyDepthTexture(target, false);
                compositeBlurredBloom(target, bloomStrength, preHandDepthTexture, postHandDepthTexture, useSceneDepthMask);
                if (BLOOM_DIRECT_DEBUG_STRENGTH > 0.0F) {
                    compositeTexture(target, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget), BLOOM_DIRECT_DEBUG_STRENGTH,
                            preHandDepthTexture, postHandDepthTexture, useSceneDepthMask);
                }
                composited = true;
                logBloomCompositeProbe(target, useSceneDepthMask, composited);
                if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                    bloomCompositeLogs++;
                    MainMod.LOGGER.info("[AUSMBloomComposite] result=blurred target={} layer={}",
                            com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target),
                            com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
                }
            } else if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                bloomCompositeLogs++;
                MainMod.LOGGER.warn("[AUSMBloomComposite] result=blur-failed target={} layer={}",
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
            }
        } finally {
            layerBloomPending = false;
            if (state != null) {
                state.restore();
            }
        }
        return composited;
    }

    private void logBloomFrameProbe(int rendered, int passedSamples, boolean sharedMinecraftDepth, boolean deferComposite) {
        if (bloomFrameProbeCalls >= BLOOM_PROBE_LIMIT) {
            return;
        }
        bloomFrameProbeCalls++;
        MainMod.LOGGER.info(
                "[AUSMBloomFrameProbe] call={} rendered={} samples={} sharedDepth={} deferred={} layer={} downsample={} blur={} pending={} glProgram={} glError={}",
                bloomFrameProbeCalls,
                rendered,
                passedSamples,
                sharedMinecraftDepth,
                deferComposite,
                sampleFramebufferColor(bloomLayerTarget),
                sampleFramebufferColor(bloomDownsampleTarget),
                sampleFramebufferColor(bloomBlurTarget),
                layerBloomPending,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetError()
        );
    }

    private void logBloomCompositeProbe(Framebuffer target, boolean sceneDepthMask, boolean composited) {
        if (bloomCompositeProbeCalls >= BLOOM_PROBE_LIMIT) {
            return;
        }
        bloomCompositeProbeCalls++;
        MainMod.LOGGER.info(
                "[AUSMBloomCompositeProbe] call={} composited={} sceneDepthMask={} layer={} downsample={} target={} glError={}",
                bloomCompositeProbeCalls,
                composited,
                sceneDepthMask,
                sampleFramebufferColor(bloomLayerTarget),
                sampleFramebufferColor(bloomDownsampleTarget),
                sampleFramebufferColor(target),
                GL11.glGetError()
        );
    }

    private static String sampleFramebufferColor(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        FloatBuffer sample = BufferUtils.createFloatBuffer(4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(width / 2, height / 2, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, sample);
            return framebufferId + "@" + width + "x" + height + "="
                    + sample.get(0) + "," + sample.get(1) + "," + sample.get(2) + "," + sample.get(3);
        } catch (RuntimeException | LinkageError error) {
            return "error=" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    private void renderFramebufferBloom(Framebuffer target) {
        if (target == null || com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target) <= 0) {
            return;
        }
        if (!ensureTargets(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target))) {
            return;
        }

        RenderState state = captureState();
        try {
            if (runThresholdBlurChain(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target))) {
                compositeBlurredBloom(target, FRAMEBUFFER_BLOOM_STRENGTH);
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

    private boolean runBlurChain(int sourceTexture) {
        if (sourceTexture <= 0 || !ensureTargets(width, height)) {
            return false;
        }
        if (copyProgram() == -1 || blurProgram() == -1) {
            return false;
        }

        bindHalfTarget(bloomDownsampleTarget);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(copyProgram);
        bindTextureUniform(copyProgram, "source", sourceTexture, 0);
        drawFullscreenQuad();

        for (int i = 0; i < blurIterations; i++) {
            bindHalfTarget(bloomBlurTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), 0);
            setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            drawFullscreenQuad();

            bindHalfTarget(bloomDownsampleTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            setUniform2f(blurProgram, "direction", 0.0F, 1.0F / Math.max(1, halfHeight));
            drawFullscreenQuad();
        }
        return true;
    }

    private boolean runThresholdBlurChain(int sourceTexture) {
        if (sourceTexture <= 0 || !ensureTargets(width, height)) {
            return false;
        }
        if (thresholdProgram() == -1 || blurProgram() == -1) {
            return false;
        }

        bindHalfTarget(bloomDownsampleTarget);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(thresholdProgram);
        bindTextureUniform(thresholdProgram, "source", sourceTexture, 0);
        setUniform1f(thresholdProgram, "threshold", FRAMEBUFFER_BLOOM_THRESHOLD);
        drawFullscreenQuad();

        for (int i = 0; i < blurIterations; i++) {
            bindHalfTarget(bloomBlurTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), 0);
            setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            drawFullscreenQuad();

            bindHalfTarget(bloomDownsampleTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            setUniform2f(blurProgram, "direction", 0.0F, 1.0F / Math.max(1, halfHeight));
            drawFullscreenQuad();
        }
        return true;
    }

    private void compositeBlurredBloom(Framebuffer target, float strength) {
        compositeTexture(target, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), strength);
    }

    private void compositeBlurredBloom(Framebuffer target, float strength, int preHandDepthTexture, int postHandDepthTexture,
                                       boolean useSceneDepthMask) {
        compositeTexture(target, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), strength,
                preHandDepthTexture, postHandDepthTexture, useSceneDepthMask);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength) {
        compositeTexture(target, texture, strength, 0, 0, false);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength,
                                  int preHandDepthTexture, int postHandDepthTexture, boolean useSceneDepthMask) {
        if (target == null || compositeProgram() == -1) {
            return;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        // Additive ONE+ONE blending clips the first channel that reaches one,
        // turning saturated colored bloom into white. Screen-style blending
        // keeps the hue while still accumulating over the scene.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(compositeProgram);
        bindTextureUniform(compositeProgram, "bloom", texture, 0);
        boolean useHandMask = preHandDepthTexture > 0 && postHandDepthTexture > 0;
        if (useHandMask) {
            bindTextureUniform(compositeProgram, "preHandDepth", preHandDepthTexture, 1);
            bindTextureUniform(compositeProgram, "postHandDepth", postHandDepthTexture, 2);
        }
        if (useSceneDepthMask) {
            bindTextureUniform(compositeProgram, "bloomDepth", bloomDepthTexture, 3);
            bindTextureUniform(compositeProgram, "finalDepth", finalDepthTexture, 4);
        }
        setUniform1f(compositeProgram, "strength", strength);
        setUniform1i(compositeProgram, "useHandMask", useHandMask ? 1 : 0);
        setUniform1i(compositeProgram, "useSceneDepthMask", useSceneDepthMask ? 1 : 0);
        drawFullscreenQuad();
    }

    private int renderBloomGeometry(RenderGlobal renderGlobal, BlockRenderLayer bloomLayer,
                                    double partialTicks, int pass, Entity entity) {
        bindBlockAtlasOnDefaultTextureUnit();

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        // The bloom target borrows the live world depth attachment in shaderless
        // mode. It must test against it without modifying the scene depth. The
        // matching source faces need a minimal bias or equal-depth rasterization
        // intermittently rejects the bloom geometry on different drivers.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR, SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
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
                rendered = com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, bloomLayer, partialTicks, pass, entity);
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
        }
    }

    private int renderGlobalFacadesBloomGeometry() {
        if (!globalFacadesBloomResolved) {
            globalFacadesBloomResolved = true;
            try {
                Class<?> renderer = Class.forName("com.l.globalfacades.client.render.FacadeWorldRenderer");
                globalFacadesBloomMethod = renderer.getMethod("renderBloomForAusm");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                globalFacadesBloomMethod = null;
            }
        }
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

    private void clearLayerTarget(boolean preserveDepth) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClearDepth(1.0D);
        GL11.glClear(preserveDepth ? GL11.GL_COLOR_BUFFER_BIT : GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private boolean attachMinecraftDepth(Framebuffer minecraftDepthSource) {
        if (minecraftDepthSource == null || bloomLayerTarget == null) {
            return false;
        }
        int sourceDepth = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(
                minecraftDepthSource, 0, "field_147624_h", "depthBuffer");
        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
        if (sourceDepth <= 0 || targetFramebuffer <= 0) {
            return false;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, sourceDepth);
        return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
    }

    private void restoreLayerDepthAttachment() {
        if (bloomLayerTarget == null) {
            return;
        }
        int layerDepth = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(
                bloomLayerTarget, 0, "field_147624_h", "depthBuffer");
        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
        if (layerDepth <= 0 || targetFramebuffer <= 0) {
            return;
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFramebuffer);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_RENDERBUFFER, layerDepth);
    }

    private void logDepthAttachmentProbe(String stage, Framebuffer minecraftDepthSource, boolean sharedDepth) {
        // Depth-attachment diagnostics are disabled outside focused F1 investigations.
        return;
        /*
        if (bloomLayerTarget == null) {
            return;
        }
        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(bloomLayerTarget);
        int sourceFramebuffer = minecraftDepthSource == null ? -1
                : com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(minecraftDepthSource);
        int sourceDepth = minecraftDepthSource == null ? -1
                : com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(minecraftDepthSource, 0, "field_147624_h", "depthBuffer");
        int layerDepth = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(bloomLayerTarget, 0,
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

    private float readCenterDepth() {
        FloatBuffer sample = BufferUtils.createFloatBuffer(1);
        GL11.glReadPixels(Math.max(0, width / 2), Math.max(0, height / 2), 1, 1,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, sample);
        return sample.get(0);
    }

    private void copyDepth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        if (pipelineDepthSource != null) {
            pipelineDepthSource.blitDepthTo(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(bloomLayerTarget), width, height);
            return;
        }
        if (minecraftDepthSource == null || com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((minecraftDepthSource), 0, "field_147624_h", "depthBuffer") <= 0) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(minecraftDepthSource));
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(bloomLayerTarget));
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(minecraftDepthSource),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(minecraftDepthSource),
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

    private void bindLayerTargetForGeometry() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
    }

    private void bindHalfTarget(Framebuffer framebuffer) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, halfWidth, halfHeight);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private boolean ensureTargets(int targetWidth, int targetHeight) {
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.isFramebufferEnabled() || targetWidth <= 0 || targetHeight <= 0) {
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
        bloomLayerTarget = resizeFramebuffer(bloomLayerTarget, width, height, true);
        bloomDownsampleTarget = resizeFramebuffer(bloomDownsampleTarget, halfWidth, halfHeight, false);
        bloomBlurTarget = resizeFramebuffer(bloomBlurTarget, halfWidth, halfHeight, false);
        return bloomLayerTarget != null && bloomDownsampleTarget != null && bloomBlurTarget != null
                && ensureDepthMaskTextures();
    }

    private static Framebuffer resizeFramebuffer(Framebuffer framebuffer, int width, int height, boolean depth) {
        if (framebuffer == null) {
            framebuffer = new Framebuffer(width, height, depth);
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((framebuffer), new String[] {"func_147604_a", "setFramebufferColor"},
                new Class<?>[] {float.class, float.class, float.class, float.class},
                (0.0F), (0.0F), (0.0F), (0.0F));;
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((framebuffer), new String[] {"func_147613_a", "createBindFramebuffer"},
                new Class<?>[] {int.class, int.class}, (width), (height));;
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((framebuffer), new String[] {"func_147604_a", "setFramebufferColor"},
                new Class<?>[] {float.class, float.class, float.class, float.class},
                (0.0F), (0.0F), (0.0F), (0.0F));;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((framebuffer), new String[] {"func_147607_a", "setFramebufferFilter"},
                new Class<?>[] {int.class}, (GL11.GL_LINEAR));;
        return framebuffer;
    }

    private boolean ensureDepthMaskTextures() {
        bloomDepthTexture = resizeDepthTexture(bloomDepthTexture, width, height);
        finalDepthTexture = resizeDepthTexture(finalDepthTexture, width, height);
        return bloomDepthTexture > 0 && finalDepthTexture > 0;
    }

    private static int resizeDepthTexture(int texture, int width, int height) {
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
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
        return texture;
    }

    private boolean copyDepthTexture(Framebuffer source, boolean bloomDepth) {
        if (source == null || width <= 0 || height <= 0 || bloomDepthTexture <= 0 || finalDepthTexture <= 0) {
            return false;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(source));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomDepth ? bloomDepthTexture : finalDepthTexture);
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

    private int targetWidth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        if (pipelineDepthSource != null) {
            return pipelineDepthSource.getAttachmentWidth(Attachment.COLOR);
        }
        return minecraftDepthSource != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(minecraftDepthSource) : 0;
    }

    private int targetHeight(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource) {
        if (pipelineDepthSource != null) {
            return pipelineDepthSource.getAttachmentHeight(Attachment.COLOR);
        }
        return minecraftDepthSource != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(minecraftDepthSource) : 0;
    }

    private int copyProgram() {
        if (copyProgram == -1) {
            copyProgram = createProgram("copy", COPY_FRAGMENT_SHADER);
        }
        return copyProgram;
    }

    private int thresholdProgram() {
        if (thresholdProgram == -1) {
            thresholdProgram = createProgram("threshold", THRESHOLD_FRAGMENT_SHADER);
        }
        return thresholdProgram;
    }

    private int blurProgram() {
        if (blurProgram == -1) {
            blurProgram = createProgram("blur", BLUR_FRAGMENT_SHADER);
        }
        return blurProgram;
    }

    private int compositeProgram() {
        if (compositeProgram == -1) {
            compositeProgram = createProgram(
                    shaderPackCompositeOverride ? "shaderpack-composite" : "composite",
                    compositeVertexSource,
                    compositeFragmentSource,
                    false
            );
            if (compositeProgram == -1 && shaderPackCompositeOverride) {
                MainMod.LOGGER.warn("[AUSMBloom] Shaderpack bloom override failed; using built-in bloom composite.");
                compositeProgram = createProgram("composite-fallback", VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER, false);
            }
        }
        return compositeProgram;
    }

    private void resetShaderPackConfiguration() {
        deleteProgram(compositeProgram);
        compositeProgram = -1;
        compositeVertexSource = VERTEX_SHADER;
        compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;
        bloomStrength = DEFAULT_BLOOM_STRENGTH;
        blurIterations = DEFAULT_BLUR_ITERATIONS;
        shaderPackCompositeOverride = false;
        loggedProgramFailure = false;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int emissiveExtractProgram() {
        if (emissiveExtractProgram == -1) {
            emissiveExtractProgram = createProgram(
                    "shaderless-emissive-extract",
                    EMISSIVE_EXTRACT_VERTEX_SHADER,
                    EMISSIVE_EXTRACT_FRAGMENT_SHADER,
                    true
            );
        }
        return emissiveExtractProgram;
    }

    private int createProgram(String name, String fragmentSource) {
        return createProgram(name, VERTEX_SHADER, fragmentSource, false);
    }

    private int createProgram(String name, String vertexSource, String fragmentSource, boolean bindPipelineAttributes) {
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean(net.minecraft.client.renderer.OpenGlHelper.class, false, "field_148824_g", "shadersSupported")) {
            return -1;
        }

        int vertex = compileShader(name + ":vertex", GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(name + ":fragment", GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == -1 || fragment == -1) {
            deleteShader(vertex);
            deleteShader(fragment);
            return -1;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        if (bindPipelineAttributes) {
            GL20.glBindAttribLocation(
                    program,
                    com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                    "at_midBlock"
            );
        }
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            logProgramFailure("Failed to link " + name + " bloom program: " + GL20.glGetProgramInfoLog(program, 4096));
            deleteProgram(program);
            return -1;
        }
        return program;
    }

    private int compileShader(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            logProgramFailure("Failed to compile " + name + " bloom shader: " + GL20.glGetShaderInfoLog(shader, 4096));
            deleteShader(shader);
            return -1;
        }
        return shader;
    }

    private void bindTextureUniform(int program, String name, int texture, int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, unit);
        }
    }

    private static void bindSamplerUniform(int program, String name, int unit) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, unit);
        }
    }

    private static void prepareShaderlessEmissiveGeometryState(int program) {
        bindBlockAtlasOnDefaultTextureUnit();

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(program);
        bindSamplerUniform(program, "terrain", 0);
        setUniform1f(program, "forceEmission", 0.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR, SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.003921569F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void bindBlockAtlasOnDefaultTextureUnit() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
        }
    }

    private static void setUniform1f(int program, String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    private static void setUniform1i(int program, String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    private static void setUniform2f(int program, String name, float x, float y) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    private static void drawFullscreenQuad() {
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

    private RenderState captureState() {
        return new RenderState();
    }

    private LumenizedTicketBridge lumenizedTickets() {
        if (lumenizedTickets == null) {
            lumenizedTickets = new LumenizedTicketBridge();
        }
        return lumenizedTickets;
    }

    private void logProgramFailure(String message) {
        if (!loggedProgramFailure) {
            loggedProgramFailure = true;
            MainMod.LOGGER.warn("[AUSMBloom] {}", message);
        }
    }

    private static void deleteFramebuffer(Framebuffer framebuffer) {
        if (framebuffer != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.deleteFramebuffer(framebuffer);
        }
    }

    private static void deleteProgram(int program) {
        if (program > 0) {
            GL20.glDeleteProgram(program);
        }
    }

    private static void deleteTexture(int texture) {
        if (texture > 0) {
            GL11.glDeleteTextures(texture);
        }
    }

    private static void deleteShader(int shader) {
        if (shader > 0) {
            GL20.glDeleteShader(shader);
        }
    }

    private final class RenderState {
        private final int readFramebuffer;
        private final int drawFramebuffer;
        private final int activeTexture;
        private final int texture;
        private final int texture0;
        private final int program;
        private final boolean blend;
        private final boolean depthTest;
        private final boolean alphaTest;
        private final boolean cull;
        private final boolean polygonOffsetFill;
        private final boolean depthMask;
        private final int depthFunc;
        private final float polygonOffsetFactor;
        private final float polygonOffsetUnits;
        private final int blendSrcRgb;
        private final int blendDstRgb;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final boolean scissorTest;
        private final int scissorX;
        private final int scissorY;
        private final int scissorWidth;
        private final int scissorHeight;

        private RenderState() {
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            polygonOffsetFill = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            polygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
            polygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            viewportX = viewportBuffer.get(0);
            viewportY = viewportBuffer.get(1);
            viewportWidth = viewportBuffer.get(2);
            viewportHeight = viewportBuffer.get(3);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, viewportBuffer);
            scissorX = viewportBuffer.get(0);
            scissorY = viewportBuffer.get(1);
            scissorWidth = viewportBuffer.get(2);
            scissorHeight = viewportBuffer.get(3);
            scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        }

        private void restore() {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
            if (scissorTest) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(program);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(GL13.GL_TEXTURE0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texture0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(activeTexture);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(texture);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
            if (polygonOffsetFill) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (blend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (depthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            if (alphaTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (cull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        }
    }

    private static final class LumenizedTicketBridge {
        private static final String BLOOM_EFFECT_UTIL = "gregtech.client.utils.BloomEffectUtil";
        private static final String EFFECT_RENDER_CONTEXT = "gregtech.client.utils.EffectRenderContext";

        private boolean resolved;
        private boolean loggedFailure;
        private Method preDraw;
        private Method draw;
        private Method postDraw;
        private Method effectContextGetInstance;
        private Method effectContextUpdate;
        private Field bloomRenders;

        private int draw(Entity entity, float partialTicks) {
            if (!resolve()) {
                return 0;
            }

            try {
                preDraw.invoke(null);
                Object context = effectContextGetInstance.invoke(null);
                effectContextUpdate.invoke(context, entity, partialTicks);
                Object mapObject = bloomRenders.get(null);
                if (!(mapObject instanceof Map<?, ?> bloomRenderMap) || bloomRenderMap.isEmpty()) {
                    return 0;
                }

                BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator());
                int rendered = 0;
                Collection<?> ticketLists = bloomRenderMap.values();
                for (Object ticketList : ticketLists) {
                    if (ticketList instanceof List<?>) {
                        draw.invoke(null, buffer, context, ticketList);
                        rendered++;
                    }
                }
                return rendered;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                logFailure("Failed to draw Lumenized custom bloom tickets", error);
                return 0;
            } finally {
                try {
                    postDraw.invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }

        private boolean resolve() {
            if (resolved) {
                return draw != null;
            }
            resolved = true;

            try {
                ClassLoader loader = LumenizedTicketBridge.class.getClassLoader();
                Class<?> bloomUtil = Class.forName(BLOOM_EFFECT_UTIL, false, loader);
                Class<?> context = Class.forName(EFFECT_RENDER_CONTEXT, false, loader);
                preDraw = accessible(bloomUtil.getDeclaredMethod("preDraw"));
                postDraw = accessible(bloomUtil.getDeclaredMethod("postDraw"));
                draw = accessible(bloomUtil.getDeclaredMethod("draw", BufferBuilder.class, context, List.class));
                bloomRenders = accessible(bloomUtil.getDeclaredField("BLOOM_RENDERS"));
                effectContextGetInstance = context.getMethod("getInstance");
                effectContextUpdate = context.getMethod("update", Entity.class, float.class);
                return true;
            } catch (ClassNotFoundException ignored) {
                return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                logFailure("Failed to resolve Lumenized custom bloom ticket bridge", error);
                return false;
            }
        }

        private void logFailure(String message, Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                MainMod.LOGGER.warn("[AUSMBloom] {}", message, throwable);
            }
        }

        private static Method accessible(Method method) {
            method.setAccessible(true);
            return method;
        }

        private static Field accessible(Field field) {
            field.setAccessible(true);
            return field;
        }
    }

    private static final String VERTEX_SHADER = """
            #version 120
            varying vec2 textureCoords;
            void main() {
                gl_Position = ftransform();
                textureCoords = gl_MultiTexCoord0.st;
            }
            """;

    private static final String EMISSIVE_EXTRACT_VERTEX_SHADER = """
            #version 120
            attribute vec4 at_midBlock;
            uniform float forceEmission;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            varying float vertexEmission;
            void main() {
                gl_Position = ftransform();
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
                float rawEmission = at_midBlock.w;
                float metadataEmission = rawEmission >= 0.5 && rawEmission <= 15.5 ? rawEmission / 15.0 : 0.0;
                vertexEmission = max(metadataEmission, forceEmission);
            }
            """;

    private static final String EMISSIVE_EXTRACT_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            varying float vertexEmission;
            void main() {
                if (vertexEmission <= 0.0) {
                    discard;
                }
            vec4 albedo = texture2D(terrain, textureCoords) * vertexColor;
            if (albedo.a <= 0.003921569) {
                discard;
            }
            float emissionMask = smoothstep(0.04, 0.45, vertexEmission);
            vec3 bloom = albedo.rgb * (1.15 + vertexEmission * 4.25) * emissionMask;
            float bloomPeak = max(bloom.r, max(bloom.g, bloom.b));
            bloom /= 1.0 + max(bloomPeak - 1.0, 0.0) * 0.5;
            gl_FragColor = vec4(bloom, albedo.a * emissionMask);
            }
            """;

    private static final String COPY_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            varying vec2 textureCoords;
            void main() {
                gl_FragColor = texture2D(source, textureCoords);
            }
            """;

    private static final String THRESHOLD_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform float threshold;
            varying vec2 textureCoords;
            void main() {
                vec3 color = texture2D(source, textureCoords).rgb;
                float brightness = dot(color, vec3(0.2126, 0.7152, 0.0722));
                float bloom = smoothstep(threshold, 1.0, brightness);
                gl_FragColor = vec4(color * bloom, 1.0);
            }
            """;

    private static final String BLUR_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform vec2 direction;
            varying vec2 textureCoords;
            void main() {
                vec3 sum = texture2D(source, textureCoords).rgb * 0.2270270270;
                sum += texture2D(source, textureCoords + direction * 1.3846153846).rgb * 0.3162162162;
                sum += texture2D(source, textureCoords - direction * 1.3846153846).rgb * 0.3162162162;
                sum += texture2D(source, textureCoords + direction * 3.2307692308).rgb * 0.0702702703;
                sum += texture2D(source, textureCoords - direction * 3.2307692308).rgb * 0.0702702703;
                gl_FragColor = vec4(sum, 1.0);
            }
            """;

    private static final String COMPOSITE_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D bloom;
            uniform sampler2D preHandDepth;
            uniform sampler2D postHandDepth;
            uniform sampler2D bloomDepth;
            uniform sampler2D finalDepth;
            uniform float strength;
            uniform int useHandMask;
            uniform int useSceneDepthMask;
            varying vec2 textureCoords;
            void main() {
                if (useSceneDepthMask == 1) {
                    float emissionDepth = texture2D(bloomDepth, textureCoords).r;
                    float sceneDepth = texture2D(finalDepth, textureCoords).r;
                    if (emissionDepth > sceneDepth + 0.00002 && sceneDepth < 0.99999) {
                        discard;
                    }
                }
                if (useHandMask == 1) {
                    float preHand = texture2D(preHandDepth, textureCoords).r;
                    float postHand = texture2D(postHandDepth, textureCoords).r;
                    if (postHand < preHand - 0.00005 && postHand < 0.99999) {
                        discard;
                    }
                }
                vec3 source = texture2D(bloom, textureCoords).rgb;
                source = source / (1.0 + max(source, vec3(0.0)));
                gl_FragColor = vec4(source * strength, 1.0);
            }
            """;
}
