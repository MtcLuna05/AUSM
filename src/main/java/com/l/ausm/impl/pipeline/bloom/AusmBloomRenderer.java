package com.l.ausm.impl.pipeline.bloom;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
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
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

public final class AusmBloomRenderer {
    private static final int HALF_RESOLUTION_DIVISOR = 2;
    private static final int BLUR_ITERATIONS = 2;
    private static final float BLOOM_STRENGTH = 1.65F;
    private static final float BLOOM_DIRECT_DEBUG_STRENGTH = 0.0F;
    private static final float FRAMEBUFFER_BLOOM_STRENGTH = 1.05F;
    private static final float FRAMEBUFFER_BLOOM_THRESHOLD = 0.86F;
    private static final boolean FRAMEBUFFER_BLOOM_FALLBACK_ENABLED = false;
    private static final int BLOOM_RENDER_LOG_LIMIT = 0;
    private static final int BLOOM_ZERO_RENDER_LOG_LIMIT = 0;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR = -1.0F;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS = -4.0F;

    private final AusmBloomResourceIndex resourceIndex = new AusmBloomResourceIndex();
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private Framebuffer bloomLayerTarget;
    private Framebuffer bloomDownsampleTarget;
    private Framebuffer bloomBlurTarget;
    private int width = -1;
    private int height = -1;
    private int halfWidth = -1;
    private int halfHeight = -1;
    private int copyProgram = -1;
    private int thresholdProgram = -1;
    private int blurProgram = -1;
    private int compositeProgram = -1;
    private int emissiveExtractProgram = -1;
    private boolean layerBloomPending;
    private boolean loggedLayerRenderer;
    private boolean loggedShaderlessEmissiveRenderer;
    private boolean loggedProgramFailure;
    private int bloomCompositeLogs;
    private int framebufferBloomLogs;
    private int bloomRenderLogs;
    private int zeroBloomRenderLogs;
    private LumenizedTicketBridge lumenizedTickets;

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
        try {
            clearLayerTarget();
            copyDepth(pipelineDepthSource, minecraftDepthSource);
            bindLayerTargetForGeometry();
            rendered = renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity);
            if (rendered > 0) {
                layerBloomPending = true;
                if (bloomRenderLogs < BLOOM_RENDER_LOG_LIMIT) {
                    bloomRenderLogs++;
                    
                }
                if (!loggedLayerRenderer) {
                    loggedLayerRenderer = true;
                    
                }
                if (!deferComposite && minecraftDepthSource != null) {
                    compositePendingLayerBloom(minecraftDepthSource, false);
                }
            }
        } finally {
            state.restore();
        }

        if (rendered <= 0 && resourceIndex.hasBloomResources() && zeroBloomRenderLogs < BLOOM_ZERO_RENDER_LOG_LIMIT) {
            zeroBloomRenderLogs++;
            
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
            clearLayerTarget();
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
                compositeBlurredBloom(target, BLOOM_STRENGTH, preHandDepthTexture, postHandDepthTexture);
                if (BLOOM_DIRECT_DEBUG_STRENGTH > 0.0F) {
                    compositeTexture(target, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget), BLOOM_DIRECT_DEBUG_STRENGTH,
                            preHandDepthTexture, postHandDepthTexture);
                }
                composited = true;
                if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                    bloomCompositeLogs++;
                    
                }
            } else if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                bloomCompositeLogs++;
                
            }
        } finally {
            layerBloomPending = false;
            if (state != null) {
                state.restore();
            }
        }
        return composited;
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

        for (int i = 0; i < BLUR_ITERATIONS; i++) {
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

        for (int i = 0; i < BLUR_ITERATIONS; i++) {
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

    private void compositeBlurredBloom(Framebuffer target, float strength, int preHandDepthTexture, int postHandDepthTexture) {
        compositeTexture(target, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), strength, preHandDepthTexture, postHandDepthTexture);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength) {
        compositeTexture(target, texture, strength, 0, 0);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength,
                                  int preHandDepthTexture, int postHandDepthTexture) {
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
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(compositeProgram);
        bindTextureUniform(compositeProgram, "bloom", texture, 0);
        boolean useHandMask = preHandDepthTexture > 0 && postHandDepthTexture > 0;
        if (useHandMask) {
            bindTextureUniform(compositeProgram, "preHandDepth", preHandDepthTexture, 1);
            bindTextureUniform(compositeProgram, "postHandDepth", postHandDepthTexture, 2);
        }
        setUniform1f(compositeProgram, "strength", strength * configuredBloomIntensity());
        setUniform1f(compositeProgram, "bloomChroma", configuredBloomChroma());
        setUniform1i(compositeProgram, "useHandMask", useHandMask ? 1 : 0);
        drawFullscreenQuad();
    }

    private int renderBloomGeometry(RenderGlobal renderGlobal, BlockRenderLayer bloomLayer,
                                    double partialTicks, int pass, Entity entity) {
        bindBlockAtlasOnDefaultTextureUnit();

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-4.0F, -8.0F);

        boolean forcedNothiriumBypass = false;
        try {
            NothiriumBypass.pushForcedBypass();
            forcedNothiriumBypass = true;
            int rendered = com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, bloomLayer, partialTicks, pass, entity);
            rendered += lumenizedTickets().draw(entity, (float) partialTicks);
            return rendered;
        } finally {
            if (forcedNothiriumBypass) {
                NothiriumBypass.popForcedBypass();
            }
            GL11.glPolygonOffset(0.0F, 0.0F);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
    }

    private void clearLayerTarget() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClearDepth(1.0D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
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
        return bloomLayerTarget != null && bloomDownsampleTarget != null && bloomBlurTarget != null;
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
            compositeProgram = createProgram("composite", COMPOSITE_FRAGMENT_SHADER);
        }
        return compositeProgram;
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

    private static float configuredBloomChroma() {
        com.l.ausm.impl.client.ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        return config != null ? config.shaderlessBloomChroma() : 1.0F;
    }

    private static float configuredBloomIntensity() {
        com.l.ausm.impl.client.ClientSettingsConfig config = MainMod.getClientSettingsConfig();
        return config != null ? config.shaderlessBloomIntensity() : 0.85F;
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
                vec3 dyeColor = clamp(albedo.rgb, 0.0, 1.0);
                float emissionScale = (0.45 + vertexEmission * 0.55) * emissionMask;
                vec3 bloom = dyeColor * emissionScale;
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
            uniform float strength;
            uniform float bloomChroma;
            uniform int useHandMask;
            varying vec2 textureCoords;
            void main() {
                if (useHandMask == 1) {
                    float preHand = texture2D(preHandDepth, textureCoords).r;
                    float postHand = texture2D(postHandDepth, textureCoords).r;
                    if (postHand < preHand - 0.00005 && postHand < 0.99999) {
                        discard;
                    }
                }
                vec3 source = texture2D(bloom, textureCoords).rgb;
                float luminance = dot(source, vec3(0.2126, 0.7152, 0.0722));
                source = max(mix(vec3(luminance), source, clamp(bloomChroma, 0.0, 2.0)), vec3(0.0));
                gl_FragColor = vec4(source * strength, 1.0);
            }
            """;
}
