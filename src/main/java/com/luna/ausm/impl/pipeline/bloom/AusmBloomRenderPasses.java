package com.luna.ausm.impl.pipeline.bloom;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.luna.ausm.impl.pipeline.pack.ShaderPreprocessor;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.function.IntSupplier;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

abstract class AusmBloomRenderPasses extends AusmBloomRendererBase {
    public void configure(ShaderPack pack, ShaderProperties properties) {
        self().resetShaderPackConfiguration();
        if (pack == null || properties == null) {
            return;
        }

        bloomStrength = AusmBloomRenderer.clamp(
                PipelineShaderSettings.parseFloatSetting(pack, properties, BLOOM_STRENGTH_SETTING, DEFAULT_BLOOM_STRENGTH),
                0.0F,
                8.0F
        );
        blurIterations = Math.clamp(PipelineShaderSettings.parseIntSetting(pack, properties, BLOOM_BLUR_ITERATIONS_SETTING, DEFAULT_BLUR_ITERATIONS), 0, 8);
        shaderPackCompositeReplace = PipelineShaderSettings.parseBooleanSetting(
                pack, properties, BLOOM_COMPOSITE_REPLACE_SETTING, false);

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
                    "[AUSMBloom] Using shaderpack bloom override for '{}' (strength={}, blurIterations={}, replaceComposite={}).",
                    pack.getName(), bloomStrength, blurIterations, shaderPackCompositeReplace);
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
        translucentAttenuationAvailable = false;
        pendingBloomPeakProbe = null;
        int targetWidth = self().targetWidth(pipelineDepthSource, minecraftDepthSource);
        int targetHeight = self().targetHeight(pipelineDepthSource, minecraftDepthSource);
        if (!self().ensureTargets(targetWidth, targetHeight)) {
            return 0;
        }
        RenderState state = self().captureState();
        int rendered = 0;
        boolean sharedMinecraftDepth = false;
        try {
            // In shaderless mode, keep the bloom target attached to the live
            // terrain depth renderbuffer. Blitting depth into a private target
            // raced the world-pass FBO lifecycle and allowed bloom through walls.
            sharedMinecraftDepth = pipelineDepthSource == null && self().attachMinecraftDepth(minecraftDepthSource);
            self().clearLayerTarget(sharedMinecraftDepth);
            if (!sharedMinecraftDepth) {
                self().copyDepth(pipelineDepthSource, minecraftDepthSource);
            }
            self().bindLayerTargetForGeometry();
            rendered = self().renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity,
                    !sharedMinecraftDepth, true);
            rendered += self().renderLumenizedBloomTickets(entity, (float) partialTicks);
            rendered += self().renderGlobalFacadesBloomGeometry();
            if (rendered > 0) {
                if (sharedMinecraftDepth) {
                    // Never write into Minecraft's live shaderless depth
                    // renderbuffer. Reattach AUSM's private depth and replay
                    // only the sparse bloom mesh to obtain true emitter depth.
                    self().restoreLayerDepthAttachment();
                    sharedMinecraftDepth = false;
                    self().bindLayerTargetForGeometry();
                    MinecraftReflectionCompat.glStateDepthMask(true);
                    GL11.glClearDepth(1.0D);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    self().copyDepth(pipelineDepthSource, minecraftDepthSource);
                    self().renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity, true, false);
                    self().renderLumenizedBloomTickets(entity, (float) partialTicks);
                }
                self().copyDepthTexture(bloomLayerTarget, true);
                // Nothirium's translucent replay walks the complete visible
                // mesh a second time per world pass. It is optional bloom
                // attenuation, not bloom geometry, and can monopolize the
                // client thread on large worlds.
                translucentAttenuationAvailable = !NothiriumShadowRenderer.isAvailable()
                        && self().captureTranslucentAttenuation(
                        renderGlobal, partialTicks, pass, entity, pipelineDepthSource, minecraftDepthSource);
            }
            if (bloomRenderLogs < BLOOM_RENDER_LOG_LIMIT) {
                bloomRenderLogs++;
                MainMod.LOGGER.info("[AUSMBloomDraw] layer={} rendered={} defer={} depthSource={} target={}",
                        bloomLayer,
                        rendered,
                        deferComposite,
                        pipelineDepthSource != null ? "pipeline" : (minecraftDepthSource != null ? "minecraft" : "none"),
                        MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
            }
            if (rendered > 0) {
                layerBloomPending = true;
                if (!loggedLayerRenderer) {
                    loggedLayerRenderer = true;

                }
                if (!deferComposite && minecraftDepthSource != null) {
                    self().compositePendingLayerBloom(minecraftDepthSource, false);
                }
            }
        } finally {
            if (sharedMinecraftDepth) {
                self().restoreLayerDepthAttachment();
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
        self().renderPostWorldBloom(target, 0, 0);
    }

    public void renderPostWorldBloom(Framebuffer target, int preHandDepthTexture, int postHandDepthTexture) {
        if (target == null) {
            layerBloomPending = false;
            return;
        }

        resourceIndex.scanOnce();
        boolean compositedLayerBloom = false;
        if (layerBloomPending) {
            compositedLayerBloom = self().compositePendingLayerBloom(target, true, preHandDepthTexture, postHandDepthTexture);
        }

        if (!compositedLayerBloom
                && FRAMEBUFFER_BLOOM_FALLBACK_ENABLED
                && !PipelineContext.getInstance().isActive()
                && resourceIndex.hasBloomResources()) {
            self().renderFramebufferBloom(target);
        }

        layerBloomPending = false;
    }

    public boolean renderShaderlessEmissiveTerrainBloom(Framebuffer target, IntSupplier geometryRenderer) {
        return self().renderEmissiveTerrainBloomCount(target, null, geometryRenderer, false) > 0;
    }

    public int renderEmissiveTerrainBloomCount(Framebuffer target, DeferredFramebuffer pipelineDepthSource,
                                               IntSupplier geometryRenderer, boolean allowPipelineActive) {
        translucentAttenuationAvailable = false;
        if (target == null
                || geometryRenderer == null
                || MinecraftReflectionCompat.framebufferTexture(target) <= 0
                || (!allowPipelineActive && PipelineContext.getInstance().isActive())) {
            return 0;
        }
        if (!self().ensureTargets(MinecraftReflectionCompat.framebufferWidth(target), MinecraftReflectionCompat.framebufferHeight(target))) {
            return 0;
        }

        int program = self().emissiveExtractProgram();
        if (program == -1) {
            return 0;
        }

        int rendered = 0;
        RenderState state = self().captureState();
        try {
            self().clearLayerTarget(false);
            self().copyDepth(pipelineDepthSource, target);
            self().bindLayerTargetForGeometry();
            AusmBloomRenderer.prepareShaderlessEmissiveGeometryState(program);
            rendered = geometryRenderer.getAsInt();
        } finally {
            MinecraftReflectionCompat.glUseProgram(0);
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
            AusmBloomRenderer.setUniform1f(program, "forceEmission", Math.clamp(forceEmission, 0.0F, 1.0F));
        }
    }

    public void clearPendingLayerBloom() {
        layerBloomPending = false;
        translucentAttenuationAvailable = false;
    }

    public boolean hasBloomResources() {
        resourceIndex.scanOnce();
        return resourceIndex.hasBloomResources();
    }

    public boolean hasBloomSprite(String spriteName) {
        return resourceIndex.hasBloomSprite(spriteName);
    }

    public void delete() {
        AusmBloomRenderer.deleteFramebuffer(bloomLayerTarget);
        AusmBloomRenderer.deleteFramebuffer(bloomDownsampleTarget);
        AusmBloomRenderer.deleteFramebuffer(bloomBlurTarget);
        AusmBloomRenderer.deleteFramebuffer(translucentAttenuationTarget);
        bloomLayerTarget = null;
        bloomDownsampleTarget = null;
        bloomBlurTarget = null;
        translucentAttenuationTarget = null;
        AusmBloomRenderer.deleteTexture(bloomDepthTexture);
        AusmBloomRenderer.deleteTexture(finalDepthTexture);
        AusmBloomRenderer.deleteTexture(translucentDepthTexture);
        if (bloomOcclusionQuery > 0) {
            GL15.glDeleteQueries(bloomOcclusionQuery);
            bloomOcclusionQuery = 0;
        }
        if (bloomDepthProbeFramebuffer > 0) {
            GL30.glDeleteFramebuffers(bloomDepthProbeFramebuffer);
            bloomDepthProbeFramebuffer = 0;
        }
        bloomDepthTexture = 0;
        finalDepthTexture = 0;
        translucentDepthTexture = 0;
        width = -1;
        height = -1;
        halfWidth = -1;
        halfHeight = -1;
        layerBloomPending = false;

        AusmBloomRenderer.deleteProgram(copyProgram);
        AusmBloomRenderer.deleteProgram(thresholdProgram);
        AusmBloomRenderer.deleteProgram(blurProgram);
        AusmBloomRenderer.deleteProgram(compositeProgram);
        AusmBloomRenderer.deleteProgram(nativeBloomGeometryProgram);
        AusmBloomRenderer.deleteProgram(emissiveExtractProgram);
        AusmBloomRenderer.deleteProgram(translucentAttenuationProgram);
        copyProgram = -1;
        thresholdProgram = -1;
        blurProgram = -1;
        compositeProgram = -1;
        nativeBloomGeometryProgram = -1;
        emissiveExtractProgram = -1;
        translucentAttenuationProgram = -1;
        translucentAttenuationAvailable = false;
        self().resetShaderPackConfiguration();
    }

    protected boolean compositePendingLayerBloom(Framebuffer target, boolean captureState) {
        return self().compositePendingLayerBloom(target, captureState, 0, 0);
    }

    protected boolean compositePendingLayerBloom(Framebuffer target, boolean captureState,
                                                 int preHandDepthTexture, int postHandDepthTexture) {
        if (!layerBloomPending || bloomLayerTarget == null || target == null) {
            layerBloomPending = false;
            return false;
        }

        boolean composited = false;
        RenderState state = captureState ? self().captureState() : null;
        try {
            int layerTexture = MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget);
            if (shaderPackCompositeReplace) {
                boolean useSceneDepthMask = self().copyDepthTexture(target, false);
                // Bloom Lab is a raw-layer diagnostic, not a normal visual
                // bloom presentation. Feeding it a blur makes its non-black
                // low-value halo replace the entire scene with near-black.
                // Use the unblurred parsed layer so black stays discarded and
                // the visible pixels are exactly the emitted bloom colour.
                self().compositeTexture(target, layerTexture, 1.0F,
                        preHandDepthTexture, postHandDepthTexture, useSceneDepthMask, false);
                composited = true;
            } else {
                boolean useSceneDepthMask = self().copyDepthTexture(target, false);
                if (!self().runBlurChain(layerTexture, useSceneDepthMask)) {
                    if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                        bloomCompositeLogs++;
                        MainMod.LOGGER.warn("[AUSMBloomComposite] result=blur-failed target={} layer={}",
                                MinecraftReflectionCompat.framebufferTexture(target),
                                MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
                    }
                    return false;
                }
                self().compositeBlurredBloom(target, bloomStrength, preHandDepthTexture, postHandDepthTexture,
                        useSceneDepthMask);
                composited = true;
                if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                    bloomCompositeLogs++;
                    MainMod.LOGGER.info("[AUSMBloomComposite] result=blurred target={} layer={}",
                            MinecraftReflectionCompat.framebufferTexture(target),
                            MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
                }
            }
        } finally {
            layerBloomPending = false;
            if (state != null) {
                state.restore();
            }
        }
        return composited;
    }

    protected void logBloomFrameProbe(int rendered, int passedSamples, boolean sharedMinecraftDepth, boolean deferComposite) {
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
                AusmBloomRenderer.sampleFramebufferColor(bloomLayerTarget),
                AusmBloomRenderer.sampleFramebufferColor(bloomDownsampleTarget),
                AusmBloomRenderer.sampleFramebufferColor(bloomBlurTarget),
                layerBloomPending,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetError()
        );
    }

    protected int beginBloomOcclusionProbe() {
        if (bloomFrameProbeCalls >= BLOOM_PROBE_LIMIT) {
            return 0;
        }
        try {
            if (bloomOcclusionQuery <= 0) {
                bloomOcclusionQuery = GL15.glGenQueries();
            }
            return bloomOcclusionQuery;
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    protected void logBloomCompositeProbe(Framebuffer target, boolean sceneDepthMask, boolean composited,
                                          BloomPeakProbe layerPeak, String blurredAtLayerPeak,
                                          String targetBefore, String targetAfter) {
        if (bloomCompositeProbeCalls >= BLOOM_PROBE_LIMIT) {
            return;
        }
        bloomCompositeProbeCalls++;
        MainMod.LOGGER.info(
                "[AUSMBloomCompositeProbe] call={} composited={} sceneDepthMask={} layer={} layerPeak={} sourceDepth={} blurAtLayerPeak={} targetBefore={} targetAfter={} downsample={} target={} glError={}",
                bloomCompositeProbeCalls,
                composited,
                sceneDepthMask,
                AusmBloomRenderer.sampleFramebufferColor(bloomLayerTarget),
                layerPeak,
                self().depthPairAtLayerPeak(layerPeak, sceneDepthMask),
                blurredAtLayerPeak,
                targetBefore,
                targetAfter,
                AusmBloomRenderer.sampleFramebufferColor(bloomDownsampleTarget),
                AusmBloomRenderer.sampleFramebufferColor(target),
                GL11.glGetError()
        );
    }

    /**
     * Finds a blurred-only Bloom pixel over opaque scene geometry and records
     * the exact predicate used by the composite shader. This is diagnostic
     * only: it neither changes Bloom colour nor depth state.
     */
    protected void logBloomDepthLeakProbe(boolean sceneDepthMask) {
        if (bloomDepthLeakProbeCalls >= BLOOM_PROBE_LIMIT
                || bloomDepthLeakProbeAttempts >= BLOOM_DEPTH_LEAK_PROBE_ATTEMPT_LIMIT) {
            return;
        }
        bloomDepthLeakProbeAttempts++;
        BloomLeakProbe probe = self().captureBloomLeakProbe();
        if (probe == null) {
            return;
        }
        bloomDepthLeakProbeCalls++;
        MainMod.LOGGER.warn(
                "[AUSMBloomDepthLeakProbe] call={} sceneMask={} shaderOverride={} replace={} {}",
                bloomDepthLeakProbeCalls, sceneDepthMask, shaderPackCompositeOverride,
                shaderPackCompositeReplace, probe);
    }

    protected BloomLeakProbe captureBloomLeakProbe() {
        if (bloomLayerTarget == null || bloomDownsampleTarget == null || bloomDepthTexture <= 0
                || finalDepthTexture <= 0 || width <= 0 || height <= 0) {
            return null;
        }
        int blurWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(bloomDownsampleTarget));
        int blurHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(bloomDownsampleTarget));
        ByteBuffer pixels = BufferUtils.createByteBuffer(blurWidth * blurHeight * 4);
        int framebuffer = MinecraftReflectionCompat.framebufferObject(bloomDownsampleTarget);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(0, 0, blurWidth, blurHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }

        int brightest = -1;
        int candidateX = -1;
        int candidateY = -1;
        int candidateRed = 0;
        int candidateGreen = 0;
        int candidateBlue = 0;
        for (int index = 0; index < blurWidth * blurHeight; index++) {
            int offset = index * 4;
            int red = pixels.get(offset) & 0xFF;
            int green = pixels.get(offset + 1) & 0xFF;
            int blue = pixels.get(offset + 2) & 0xFF;
            int brightness = red * 54 + green * 183 + blue * 19;
            if (brightness <= brightest || brightness < 512) {
                continue;
            }
            int x = Math.clamp((index % blurWidth) * width / blurWidth, 0, width - 1);
            int y = Math.clamp((index / blurWidth) * height / blurHeight, 0, height - 1);
            int[] raw = AusmBloomRenderer.readFramebufferRgba(bloomLayerTarget, x, y);
            if (raw == null || raw[0] + raw[1] + raw[2] > 12) {
                continue;
            }
            Float sceneDepth = self().readDepthTexture(finalDepthTexture, x, y);
            if (sceneDepth == null || sceneDepth >= 0.99999F) {
                continue;
            }
            brightest = brightness;
            candidateX = x;
            candidateY = y;
            candidateRed = red;
            candidateGreen = green;
            candidateBlue = blue;
        }
        if (candidateX < 0) {
            // Startup and GUI frames have no Bloom payload. Do not consume
            // the bounded probe budget until there is an actual blurred
            // sample over opaque scene geometry to inspect.
            return null;
        }
        int[] raw = AusmBloomRenderer.readFramebufferRgba(bloomLayerTarget, candidateX, candidateY);
        Float bloomDepth = self().readDepthTexture(bloomDepthTexture, candidateX, candidateY);
        Float sceneDepth = self().readDepthTexture(finalDepthTexture, candidateX, candidateY);
        RawBloomSource source = self().findNearbyRawBloomSource(candidateX, candidateY);
        return new BloomLeakProbe(
                "pixel=" + candidateX + "/" + candidateY
                        + ",blur=" + candidateRed + "/" + candidateGreen + "/" + candidateBlue
                        + ",raw=" + AusmBloomRenderer.rgbaSummary(raw)
                        + ",bloomDepth=" + AusmBloomRenderer.depthSummary(bloomDepth)
                        + ",sceneDepth=" + AusmBloomRenderer.depthSummary(sceneDepth)
                        + ",compositeDiscard=" + (bloomDepth != null && sceneDepth != null
                        && bloomDepth > sceneDepth + 0.000001F && sceneDepth < 0.99999F)
                        + ",source=" + source
        );
    }

    /**
     * A blurred-only receiving pixel has no useful depth of its own. Record
     * the nearby raw Bloom source that fed it and compare that source depth to
     * the receiver's scene depth; this distinguishes a shader bypass from a
     * depth-valid foreground bloom spill.
     */
    protected RawBloomSource findNearbyRawBloomSource(int receiverX, int receiverY) {
        if (bloomLayerTarget == null) {
            return RawBloomSource.NONE;
        }
        int radius = Math.max(24, blurIterations * 12);
        int startX = Math.max(0, receiverX - radius);
        int startY = Math.max(0, receiverY - radius);
        int endX = Math.min(width - 1, receiverX + radius);
        int endY = Math.min(height - 1, receiverY + radius);
        int sampleWidth = endX - startX + 1;
        int sampleHeight = endY - startY + 1;
        ByteBuffer pixels = AusmBloomRenderer.readFramebufferRgbaRegion(bloomLayerTarget, startX, startY, sampleWidth, sampleHeight);
        if (pixels == null) {
            return RawBloomSource.NONE;
        }
        int bestScore = 0;
        int sourceX = -1;
        int sourceY = -1;
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int y = 0; y < sampleHeight; y++) {
            for (int x = 0; x < sampleWidth; x++) {
                int offset = (y * sampleWidth + x) * 4;
                int candidateRed = pixels.get(offset) & 0xFF;
                int candidateGreen = pixels.get(offset + 1) & 0xFF;
                int candidateBlue = pixels.get(offset + 2) & 0xFF;
                int brightness = candidateRed * 54 + candidateGreen * 183 + candidateBlue * 19;
                int distance = Math.abs(startX + x - receiverX) + Math.abs(startY + y - receiverY);
                int score = brightness * 16 - distance;
                if (brightness <= 12 || score <= bestScore) {
                    continue;
                }
                bestScore = score;
                sourceX = startX + x;
                sourceY = startY + y;
                red = candidateRed;
                green = candidateGreen;
                blue = candidateBlue;
            }
        }
        if (sourceX < 0) {
            return RawBloomSource.NONE;
        }
        Float sourceBloomDepth = self().readDepthTexture(bloomDepthTexture, sourceX, sourceY);
        Float receiverSceneDepth = self().readDepthTexture(finalDepthTexture, receiverX, receiverY);
        return new RawBloomSource(sourceX, sourceY, red, green, blue, sourceBloomDepth, receiverSceneDepth);
    }

    protected static String rgbaSummary(int[] rgba) {
        return rgba == null ? "unavailable" : rgba[0] + "/" + rgba[1] + "/" + rgba[2] + "/" + rgba[3];
    }

    protected static String depthSummary(Float depth) {
        return depth == null ? "unavailable" : String.format(Locale.ROOT, "%.7f", depth);
    }

    protected static int[] readFramebufferRgba(Framebuffer framebuffer, int x, int y) {
        if (framebuffer == null) {
            return null;
        }
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(Math.clamp(x, 0, width - 1), Math.clamp(y, 0, height - 1),
                    1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return new int[]{pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected static ByteBuffer readFramebufferRgbaRegion(Framebuffer framebuffer, int x, int y, int width, int height) {
        if (framebuffer == null || width <= 0 || height <= 0) {
            return null;
        }
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return pixels;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected Float readDepthTexture(int texture, int x, int y) {
        if (texture <= 0) {
            return null;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        FloatBuffer pixel = BufferUtils.createFloatBuffer(1);
        try {
            if (bloomDepthProbeFramebuffer <= 0) {
                bloomDepthProbeFramebuffer = GL30.glGenFramebuffers();
            }
            if (bloomDepthProbeFramebuffer <= 0) {
                return null;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bloomDepthProbeFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, texture, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return null;
            }
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glReadPixels(Math.clamp(x, 0, width - 1), Math.clamp(y, 0, height - 1),
                    1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixel);
            float depth = pixel.get(0);
            return Float.isFinite(depth) ? depth : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected static String sampleFramebufferColor(Framebuffer framebuffer) {
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

    protected BloomPeakProbe captureBloomLayerPeakProbe() {
        if (bloomLayerTarget == null || bloomPeakProbeCalls >= 2) {
            return null;
        }
        bloomPeakProbeCalls++;
        return AusmBloomRenderer.sampleFramebufferPeak(bloomLayerTarget);
    }

    protected String depthPairAtLayerPeak(BloomPeakProbe peak, boolean sceneDepthMask) {
        if (!sceneDepthMask || peak == null || width <= 0 || height <= 0) {
            return "unavailable";
        }
        int x = Math.clamp(peak.x, 0, width - 1);
        int y = Math.clamp(peak.y, 0, height - 1);
        Float bloomDepth = self().readDepthTexture(bloomDepthTexture, x, y);
        Float sceneDepth = self().readDepthTexture(finalDepthTexture, x, y);
        if (bloomDepth == null || sceneDepth == null) {
            return "unavailable";
        }
        boolean discarded = bloomDepth > sceneDepth + 0.000001F && sceneDepth < 0.99999F;
        return AusmBloomRenderer.depthSummary(bloomDepth) + "/" + AusmBloomRenderer.depthSummary(sceneDepth) + "/discard=" + discarded;
    }

    protected static BloomPeakProbe sampleFramebufferPeak(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return null;
        }
        int width = Math.max(1, MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int height = Math.max(1, MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int framebufferId = MinecraftReflectionCompat.framebufferObject(framebuffer);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebufferId);
            GL11.glReadBuffer(framebufferId == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int bestIndex = -1;
            int bestLuminance = -1;
            int nonBlack = 0;
            for (int index = 0; index < width * height; index++) {
                int offset = index * 4;
                int red = pixels.get(offset) & 0xFF;
                int green = pixels.get(offset + 1) & 0xFF;
                int blue = pixels.get(offset + 2) & 0xFF;
                int luminance = red * 54 + green * 183 + blue * 19;
                if (luminance > 0) {
                    nonBlack++;
                }
                if (luminance > bestLuminance) {
                    bestLuminance = luminance;
                    bestIndex = index;
                }
            }
            if (bestIndex < 0) {
                return new BloomPeakProbe(framebufferId, width, height, 0, 0, 0, 0, 0, 0, 0);
            }
            int bestOffset = bestIndex * 4;
            return new BloomPeakProbe(
                    framebufferId, width, height, bestIndex % width, bestIndex / width,
                    pixels.get(bestOffset) & 0xFF,
                    pixels.get(bestOffset + 1) & 0xFF,
                    pixels.get(bestOffset + 2) & 0xFF,
                    pixels.get(bestOffset + 3) & 0xFF,
                    nonBlack
            );
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected static String sampleFramebufferColorAtScaled(Framebuffer target, BloomPeakProbe source,
                                                           Framebuffer sourceFramebuffer) {
        if (target == null || source == null || sourceFramebuffer == null) {
            return "n/a";
        }
        int sourceWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(sourceFramebuffer));
        int sourceHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(sourceFramebuffer));
        int targetWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        int x = Math.clamp(source.x * targetWidth / sourceWidth, 0, targetWidth - 1);
        int y = Math.clamp(source.y * targetHeight / sourceHeight, 0, targetHeight - 1);
        return AusmBloomRenderer.sampleFramebufferColorAt(target, x, y);
    }
}
