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
import com.l.ausm.impl.pipeline.render.TextureBinder;
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
import java.nio.ByteBuffer;
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
    /** Lets a small experimental pack inspect its parsed bloom RGB without
     * screen blending it into the already-coloured world target. */
    private static final String BLOOM_COMPOSITE_REPLACE_SETTING = "ausmBloomCompositeReplace";
    private static final float FRAMEBUFFER_BLOOM_STRENGTH = 0.525F;
    private static final float FRAMEBUFFER_BLOOM_THRESHOLD = 0.86F;
    private static final boolean FRAMEBUFFER_BLOOM_FALLBACK_ENABLED = false;
    private static final int BLOOM_RENDER_LOG_LIMIT = 8;
    private static final int BLOOM_ZERO_RENDER_LOG_LIMIT = 8;
    private static final int BLOOM_PROBE_LIMIT = 0;
    private static final int BLOOM_DEPTH_LEAK_PROBE_ATTEMPT_LIMIT = 0;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR = -1.0F;
    private static final float SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS = -4.0F;
    private static final float SHADERED_FRAMED_BLOOM_SOURCE_SCALE = 0.35F;
    private final AusmBloomResourceIndex resourceIndex = new AusmBloomResourceIndex();
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private Framebuffer bloomLayerTarget;
    private Framebuffer bloomDownsampleTarget;
    private Framebuffer bloomBlurTarget;
    private Framebuffer translucentAttenuationTarget;
    private int bloomDepthTexture;
    private int finalDepthTexture;
    private int translucentDepthTexture;
    private int width = -1;
    private int height = -1;
    private int halfWidth = -1;
    private int halfHeight = -1;
    private int copyProgram = -1;
    private int thresholdProgram = -1;
    private int blurProgram = -1;
    private int compositeProgram = -1;
    private int nativeBloomGeometryProgram = -1;
    private int emissiveExtractProgram = -1;
    private int translucentAttenuationProgram = -1;
    private String compositeVertexSource = VERTEX_SHADER;
    private String compositeFragmentSource = COMPOSITE_FRAGMENT_SHADER;
    private float bloomStrength = DEFAULT_BLOOM_STRENGTH;
    private int blurIterations = DEFAULT_BLUR_ITERATIONS;
    private boolean shaderPackCompositeOverride;
    private boolean shaderPackCompositeReplace;
    private boolean layerBloomPending;
    private boolean translucentAttenuationAvailable;
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
    private int bloomPeakProbeCalls;
    private int bloomOcclusionQuery;
    private int bloomDepthProbeFramebuffer;
    private int bloomDepthLeakProbeCalls;
    private int bloomDepthLeakProbeAttempts;
    private BloomPeakProbe pendingBloomPeakProbe;
    private int depthAttachmentProbeLogs;
    private LumenizedTicketBridge lumenizedTickets;
    private boolean globalFacadesBloomResolved;
    private Method globalFacadesBloomMethod;
    private Method globalFacadesTranslucentAttenuationMethod;
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
        int targetWidth = targetWidth(pipelineDepthSource, minecraftDepthSource);
        int targetHeight = targetHeight(pipelineDepthSource, minecraftDepthSource);
        if (!ensureTargets(targetWidth, targetHeight)) {
            return 0;
        }
        RenderState state = captureState();
        int rendered = 0;
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
            bindLayerTargetForGeometry();
            rendered = renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity,
                    !sharedMinecraftDepth, true);
            rendered += renderGlobalFacadesBloomGeometry();
            if (rendered > 0) {
                if (sharedMinecraftDepth) {
                    // Never write into Minecraft's live shaderless depth
                    // renderbuffer. Reattach AUSM's private depth and replay
                    // only the sparse bloom mesh to obtain true emitter depth.
                    restoreLayerDepthAttachment();
                    sharedMinecraftDepth = false;
                    bindLayerTargetForGeometry();
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
                    GL11.glClearDepth(1.0D);
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                    copyDepth(pipelineDepthSource, minecraftDepthSource);
                    renderBloomGeometry(renderGlobal, bloomLayer, partialTicks, pass, entity, true, false);
                }
                copyDepthTexture(bloomLayerTarget, true);
                translucentAttenuationAvailable = captureTranslucentAttenuation(
                        renderGlobal, partialTicks, pass, entity, pipelineDepthSource, minecraftDepthSource);
            }
            if (bloomRenderLogs < BLOOM_RENDER_LOG_LIMIT) {
                bloomRenderLogs++;
                MainMod.LOGGER.info("[AUSMBloomDraw] layer={} rendered={} defer={} depthSource={} target={}",
                        bloomLayer,
                        rendered,
                        deferComposite,
                        pipelineDepthSource != null ? "pipeline" : (minecraftDepthSource != null ? "minecraft" : "none"),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
            }
            if (rendered > 0) {
                layerBloomPending = true;
                if (!loggedLayerRenderer) {
                    loggedLayerRenderer = true;
                    
                }
                if (!deferComposite && minecraftDepthSource != null) {
                    compositePendingLayerBloom(minecraftDepthSource, false);
                }
            }
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
        translucentAttenuationAvailable = false;
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
        deleteFramebuffer(bloomLayerTarget);
        deleteFramebuffer(bloomDownsampleTarget);
        deleteFramebuffer(bloomBlurTarget);
        deleteFramebuffer(translucentAttenuationTarget);
        bloomLayerTarget = null;
        bloomDownsampleTarget = null;
        bloomBlurTarget = null;
        translucentAttenuationTarget = null;
        deleteTexture(bloomDepthTexture);
        deleteTexture(finalDepthTexture);
        deleteTexture(translucentDepthTexture);
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

        deleteProgram(copyProgram);
        deleteProgram(thresholdProgram);
        deleteProgram(blurProgram);
        deleteProgram(compositeProgram);
        deleteProgram(nativeBloomGeometryProgram);
        deleteProgram(emissiveExtractProgram);
        deleteProgram(translucentAttenuationProgram);
        copyProgram = -1;
        thresholdProgram = -1;
        blurProgram = -1;
        compositeProgram = -1;
        nativeBloomGeometryProgram = -1;
        emissiveExtractProgram = -1;
        translucentAttenuationProgram = -1;
        translucentAttenuationAvailable = false;
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
            int layerTexture = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget);
            if (shaderPackCompositeReplace) {
                boolean useSceneDepthMask = copyDepthTexture(target, false);
                // Bloom Lab is a raw-layer diagnostic, not a normal visual
                // bloom presentation. Feeding it a blur makes its non-black
                // low-value halo replace the entire scene with near-black.
                // Use the unblurred parsed layer so black stays discarded and
                // the visible pixels are exactly the emitted bloom colour.
                compositeTexture(target, layerTexture, 1.0F,
                        preHandDepthTexture, postHandDepthTexture, useSceneDepthMask, false);
                composited = true;
            } else {
                boolean useSceneDepthMask = copyDepthTexture(target, false);
                if (!runBlurChain(layerTexture, useSceneDepthMask)) {
                    if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                        bloomCompositeLogs++;
                        MainMod.LOGGER.warn("[AUSMBloomComposite] result=blur-failed target={} layer={}",
                                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target),
                                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
                    }
                    return false;
                }
                compositeBlurredBloom(target, bloomStrength, preHandDepthTexture, postHandDepthTexture,
                        useSceneDepthMask);
                composited = true;
                if (bloomCompositeLogs < BLOOM_RENDER_LOG_LIMIT) {
                    bloomCompositeLogs++;
                    MainMod.LOGGER.info("[AUSMBloomComposite] result=blurred target={} layer={}",
                            com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(target),
                            com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomLayerTarget));
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

    private int beginBloomOcclusionProbe() {
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

    private void logBloomCompositeProbe(Framebuffer target, boolean sceneDepthMask, boolean composited,
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
                sampleFramebufferColor(bloomLayerTarget),
                layerPeak,
                depthPairAtLayerPeak(layerPeak, sceneDepthMask),
                blurredAtLayerPeak,
                targetBefore,
                targetAfter,
                sampleFramebufferColor(bloomDownsampleTarget),
                sampleFramebufferColor(target),
                GL11.glGetError()
        );
    }

    /**
     * Finds a blurred-only Bloom pixel over opaque scene geometry and records
     * the exact predicate used by the composite shader. This is diagnostic
     * only: it neither changes Bloom colour nor depth state.
     */
    private void logBloomDepthLeakProbe(boolean sceneDepthMask) {
        if (bloomDepthLeakProbeCalls >= BLOOM_PROBE_LIMIT
                || bloomDepthLeakProbeAttempts >= BLOOM_DEPTH_LEAK_PROBE_ATTEMPT_LIMIT) {
            return;
        }
        bloomDepthLeakProbeAttempts++;
        BloomLeakProbe probe = captureBloomLeakProbe();
        if (probe == null) {
            return;
        }
        bloomDepthLeakProbeCalls++;
        MainMod.LOGGER.warn(
                "[AUSMBloomDepthLeakProbe] call={} sceneMask={} shaderOverride={} replace={} {}",
                bloomDepthLeakProbeCalls, sceneDepthMask, shaderPackCompositeOverride,
                shaderPackCompositeReplace, probe);
    }

    private BloomLeakProbe captureBloomLeakProbe() {
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
            int x = Math.min(width - 1, Math.max(0, (index % blurWidth) * width / blurWidth));
            int y = Math.min(height - 1, Math.max(0, (index / blurWidth) * height / blurHeight));
            int[] raw = readFramebufferRgba(bloomLayerTarget, x, y);
            if (raw == null || raw[0] + raw[1] + raw[2] > 12) {
                continue;
            }
            Float sceneDepth = readDepthTexture(finalDepthTexture, x, y);
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
        int[] raw = readFramebufferRgba(bloomLayerTarget, candidateX, candidateY);
        Float bloomDepth = readDepthTexture(bloomDepthTexture, candidateX, candidateY);
        Float sceneDepth = readDepthTexture(finalDepthTexture, candidateX, candidateY);
        RawBloomSource source = findNearbyRawBloomSource(candidateX, candidateY);
        return new BloomLeakProbe(
                "pixel=" + candidateX + "/" + candidateY
                        + ",blur=" + candidateRed + "/" + candidateGreen + "/" + candidateBlue
                        + ",raw=" + rgbaSummary(raw)
                        + ",bloomDepth=" + depthSummary(bloomDepth)
                        + ",sceneDepth=" + depthSummary(sceneDepth)
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
    private RawBloomSource findNearbyRawBloomSource(int receiverX, int receiverY) {
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
        ByteBuffer pixels = readFramebufferRgbaRegion(bloomLayerTarget, startX, startY, sampleWidth, sampleHeight);
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
        Float sourceBloomDepth = readDepthTexture(bloomDepthTexture, sourceX, sourceY);
        Float receiverSceneDepth = readDepthTexture(finalDepthTexture, receiverX, receiverY);
        return new RawBloomSource(sourceX, sourceY, red, green, blue, sourceBloomDepth, receiverSceneDepth);
    }

    private static String rgbaSummary(int[] rgba) {
        return rgba == null ? "unavailable" : rgba[0] + "/" + rgba[1] + "/" + rgba[2] + "/" + rgba[3];
    }

    private static String depthSummary(Float depth) {
        return depth == null ? "unavailable" : String.format(java.util.Locale.ROOT, "%.7f", depth);
    }

    private static int[] readFramebufferRgba(Framebuffer framebuffer, int x, int y) {
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
            GL11.glReadPixels(Math.max(0, Math.min(width - 1, x)), Math.max(0, Math.min(height - 1, y)),
                    1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            return new int[] {pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    private static ByteBuffer readFramebufferRgbaRegion(Framebuffer framebuffer, int x, int y, int width, int height) {
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

    private Float readDepthTexture(int texture, int x, int y) {
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
            GL11.glReadPixels(Math.max(0, Math.min(width - 1, x)), Math.max(0, Math.min(height - 1, y)),
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

    private BloomPeakProbe captureBloomLayerPeakProbe() {
        if (bloomLayerTarget == null || bloomPeakProbeCalls >= 2) {
            return null;
        }
        bloomPeakProbeCalls++;
        return sampleFramebufferPeak(bloomLayerTarget);
    }

    private String depthPairAtLayerPeak(BloomPeakProbe peak, boolean sceneDepthMask) {
        if (!sceneDepthMask || peak == null || width <= 0 || height <= 0) {
            return "unavailable";
        }
        int x = Math.max(0, Math.min(width - 1, peak.x));
        int y = Math.max(0, Math.min(height - 1, peak.y));
        Float bloomDepth = readDepthTexture(bloomDepthTexture, x, y);
        Float sceneDepth = readDepthTexture(finalDepthTexture, x, y);
        if (bloomDepth == null || sceneDepth == null) {
            return "unavailable";
        }
        boolean discarded = bloomDepth > sceneDepth + 0.000001F && sceneDepth < 0.99999F;
        return depthSummary(bloomDepth) + "/" + depthSummary(sceneDepth) + "/discard=" + discarded;
    }

    private static BloomPeakProbe sampleFramebufferPeak(Framebuffer framebuffer) {
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

    private static String sampleFramebufferColorAtScaled(Framebuffer target, BloomPeakProbe source,
                                                         Framebuffer sourceFramebuffer) {
        if (target == null || source == null || sourceFramebuffer == null) {
            return "n/a";
        }
        int sourceWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(sourceFramebuffer));
        int sourceHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(sourceFramebuffer));
        int targetWidth = Math.max(1, MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, MinecraftReflectionCompat.framebufferHeight(target));
        int x = Math.min(targetWidth - 1, Math.max(0, source.x * targetWidth / sourceWidth));
        int y = Math.min(targetHeight - 1, Math.max(0, source.y * targetHeight / sourceHeight));
        return sampleFramebufferColorAt(target, x, y);
    }

    private static String sampleFramebufferColorAtPixel(Framebuffer framebuffer, int x, int y) {
        if (framebuffer == null) {
            return "n/a";
        }
        int width = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer));
        int height = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer));
        int sampleX = Math.max(0, Math.min(width - 1, x));
        int sampleY = Math.max(0, Math.min(height - 1, y));
        int framebufferId = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer);
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

    private static String sampleFramebufferColorAt(Framebuffer framebuffer, int x, int y) {
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

    private static final class BloomLeakProbe {
        private final String summary;

        private BloomLeakProbe(String summary) {
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    private static final class RawBloomSource {
        private static final RawBloomSource NONE = new RawBloomSource(-1, -1, 0, 0, 0, null, null);

        private final int x;
        private final int y;
        private final int red;
        private final int green;
        private final int blue;
        private final Float bloomDepth;
        private final Float receiverSceneDepth;

        private RawBloomSource(int x, int y, int red, int green, int blue,
                               Float bloomDepth, Float receiverSceneDepth) {
            this.x = x;
            this.y = y;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.bloomDepth = bloomDepth;
            this.receiverSceneDepth = receiverSceneDepth;
        }

        @Override
        public String toString() {
            if (x < 0) {
                return "none";
            }
            boolean rejected = bloomDepth != null && receiverSceneDepth != null
                    && bloomDepth > receiverSceneDepth + 0.000001F && receiverSceneDepth < 0.99999F;
            return x + "/" + y + ":" + red + "/" + green + "/" + blue
                    + ",depth=" + depthSummary(bloomDepth)
                    + ",receiverDepth=" + depthSummary(receiverSceneDepth)
                    + ",blurReject=" + rejected;
        }
    }

    private static final class BloomPeakProbe {
        private final int framebuffer;
        private final int width;
        private final int height;
        private final int x;
        private final int y;
        private final int red;
        private final int green;
        private final int blue;
        private final int alpha;
        private final int nonBlack;

        private BloomPeakProbe(int framebuffer, int width, int height, int x, int y,
                               int red, int green, int blue, int alpha, int nonBlack) {
            this.framebuffer = framebuffer;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.nonBlack = nonBlack;
        }

        @Override
        public String toString() {
            return framebuffer + "@" + width + "x" + height + ":" + x + "/" + y
                    + "=" + red + "/" + green + "/" + blue + "/" + alpha
                    + ",nonBlack=" + nonBlack;
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

    private boolean runBlurChain(int sourceTexture, boolean depthAware) {
        if (sourceTexture <= 0 || !ensureTargets(width, height)) {
            return false;
        }
        if (copyProgram() == -1 || blurProgram() == -1) {
            return false;
        }

        bindHalfTarget(bloomDownsampleTarget);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(copyProgram);
        bindTextureUniform(copyProgram, "source", sourceTexture, 0);
        bindDepthAwareCopyUniforms(depthAware);
        drawFullscreenQuad();

        for (int i = 0; i < blurIterations; i++) {
            bindHalfTarget(bloomBlurTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomDownsampleTarget), 0);
            bindDepthAwareBlurUniforms(depthAware);
            setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            drawFullscreenQuad();

            bindHalfTarget(bloomDownsampleTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            bindDepthAwareBlurUniforms(depthAware);
            setUniform2f(blurProgram, "direction", 0.0F, 1.0F / Math.max(1, halfHeight));
            drawFullscreenQuad();
        }
        return true;
    }

    private void bindDepthAwareBlurUniforms(boolean depthAware) {
        boolean enabled = depthAware && bloomDepthTexture > 0 && finalDepthTexture > 0;
        if (enabled) {
            bindTextureUniform(blurProgram, "bloomDepth", bloomDepthTexture, 1);
            bindTextureUniform(blurProgram, "sceneDepth", finalDepthTexture, 2);
        }
        setUniform1i(blurProgram, "depthAware", enabled ? 1 : 0);
    }

    private void bindDepthAwareCopyUniforms(boolean depthAware) {
        boolean enabled = depthAware && bloomDepthTexture > 0 && finalDepthTexture > 0;
        if (enabled) {
            bindTextureUniform(copyProgram, "bloomDepth", bloomDepthTexture, 1);
            bindTextureUniform(copyProgram, "sceneDepth", finalDepthTexture, 2);
        }
        setUniform2f(copyProgram, "sourceTexel", 1.0F / Math.max(1, width), 1.0F / Math.max(1, height));
        setUniform1i(copyProgram, "depthAware", enabled ? 1 : 0);
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
            bindDepthAwareBlurUniforms(false);
            setUniform2f(blurProgram, "direction", 1.0F / Math.max(1, halfWidth), 0.0F);
            drawFullscreenQuad();

            bindHalfTarget(bloomDownsampleTarget);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(blurProgram);
            bindTextureUniform(blurProgram, "source", com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(bloomBlurTarget), 0);
            bindDepthAwareBlurUniforms(false);
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
                preHandDepthTexture, postHandDepthTexture, useSceneDepthMask, true);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength) {
        compositeTexture(target, texture, strength, 0, 0, false, false);
    }

    private void compositeTexture(Framebuffer target, int texture, float strength,
                                  int preHandDepthTexture, int postHandDepthTexture, boolean useSceneDepthMask,
                                  boolean bloomTextureCarriesDepth) {
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
        // A shader pass may leave any sampler unit active. Enabling fixed-
        // function texturing before returning to unit zero enables that
        // auxiliary unit as a legacy texture stage; once shaders are disabled,
        // particles and terrain then combine it with their atlas coordinates.
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        if (shaderPackCompositeOverride && shaderPackCompositeReplace) {
            // The Lab shader discards black pixels.  Its remaining pixels
            // replace the world target so a white destination cannot hide the
            // parsed bloom hue through screen blending.
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            // Additive ONE+ONE blending clips the first channel that reaches one,
            // turning saturated colored bloom into white.  Screen composition
            // is source + destination * (1 - source); the previous reversed
            // factors instead multiplied Bloom by (1 - destination), making
            // it almost invisible over the bright framed materials it needs to
            // preserve.
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ONE);
        }
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
        if (translucentAttenuationAvailable && translucentAttenuationTarget != null) {
            bindTextureUniform(compositeProgram, "translucentTransmission",
                    MinecraftReflectionCompat.framebufferTexture(translucentAttenuationTarget), 5);
            bindTextureUniform(compositeProgram, "translucentDepth", translucentDepthTexture, 6);
        }
        setUniform1f(compositeProgram, "strength", strength);
        setUniform1i(compositeProgram, "useHandMask", useHandMask ? 1 : 0);
        setUniform1i(compositeProgram, "useSceneDepthMask", useSceneDepthMask ? 1 : 0);
        setUniform1i(compositeProgram, "useBloomTextureDepth", bloomTextureCarriesDepth ? 1 : 0);
        setUniform1i(compositeProgram, "useTranslucentDampening", translucentAttenuationAvailable ? 1 : 0);
        drawFullscreenQuad();
    }

    private int renderBloomGeometry(RenderGlobal renderGlobal, BlockRenderLayer bloomLayer,
                                    double partialTicks, int pass, Entity entity,
                                    boolean writeDepth, boolean writeColor) {
        bindBlockAtlasOnDefaultTextureUnit();

        int geometryProgram = nativeBloomGeometryProgram();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(Math.max(geometryProgram, 0));
        if (geometryProgram > 0) {
            bindSamplerUniform(geometryProgram, "terrain", 0);
            setUniform1f(geometryProgram, "framedBloomScale",
                    PipelineContext.getInstance().isActive() ? SHADERED_FRAMED_BLOOM_SOURCE_SCALE : 1.0F);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        // Source faces start against copied terrain depth. Private targets also
        // record the winning bloom fragments' real depth; shaderless mode uses
        // a private depth-only replay so Minecraft's live depth is never changed.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(writeDepth);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(SHADERLESS_EMISSIVE_DEPTH_BIAS_FACTOR, SHADERLESS_EMISSIVE_DEPTH_BIAS_UNITS);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(
                writeColor, writeColor, writeColor, writeColor);
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        }
    }

    private boolean captureTranslucentAttenuation(RenderGlobal renderGlobal, double partialTicks, int pass,
                                                   Entity entity, DeferredFramebuffer pipelineDepthSource,
                                                   Framebuffer minecraftDepthSource) {
        int program = translucentAttenuationProgram();
        if (program == -1 || translucentAttenuationTarget == null || translucentDepthTexture <= 0) {
            return false;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(translucentAttenuationTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glClearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glClearDepth(1.0D);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        copyDepth(pipelineDepthSource, minecraftDepthSource, translucentAttenuationTarget);

        bindBlockAtlasOnDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(program);
        bindTextureUniform(program, "terrain", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.001F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        // Each back-to-front translucent surface multiplies the accumulated
        // light transmission. Depth writes retain the nearest translucent
        // surface while still accepting every subsequently-nearer layer.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_ZERO, GL11.GL_SRC_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);

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
        bindBlockAtlasOnDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(program);
        bindTextureUniform(program, "terrain", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), 0);
        rendered += renderGlobalFacadesTranslucentAttenuationGeometry();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);

        return rendered > 0 && copyDepthTexture(translucentAttenuationTarget, translucentDepthTexture);
    }

    private int renderGlobalFacadesBloomGeometry() {
        resolveGlobalFacadesBloomBridge();
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

    private int renderGlobalFacadesTranslucentAttenuationGeometry() {
        resolveGlobalFacadesBloomBridge();
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

    private void resolveGlobalFacadesBloomBridge() {
        if (globalFacadesBloomResolved) {
            return;
        }
        globalFacadesBloomResolved = true;
        try {
            Class<?> renderer = Class.forName("com.l.globalfacades.client.render.FacadeWorldRenderer");
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

    private void clearLayerTarget(boolean preserveDepth) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(bloomLayerTarget, false);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glViewport(0, 0, width, height);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        if (!preserveDepth) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        }
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
        copyDepth(pipelineDepthSource, minecraftDepthSource, bloomLayerTarget);
    }

    private void copyDepth(DeferredFramebuffer pipelineDepthSource, Framebuffer minecraftDepthSource,
                           Framebuffer destination) {
        if (destination == null) {
            return;
        }
        if (pipelineDepthSource != null) {
            pipelineDepthSource.blitDepthTo(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(destination), width, height);
            return;
        }
        if (minecraftDepthSource == null || com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((minecraftDepthSource), 0, "field_147624_h", "depthBuffer") <= 0) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(minecraftDepthSource));
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(destination));
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
        // Shaderless Bloom captures translucent attenuation immediately before
        // this chain. That pass intentionally leaves multiplicative blending
        // active; applying it to a freshly-cleared blur target multiplies every
        // source pixel into black. Fullscreen copy/blur passes must own their
        // complete raster state instead of inheriting the preceding world pass.
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
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
        translucentAttenuationTarget = resizeFramebuffer(translucentAttenuationTarget, width, height, true);
        configureFloatingColorAttachment(bloomDownsampleTarget, halfWidth, halfHeight);
        configureFloatingColorAttachment(bloomBlurTarget, halfWidth, halfHeight);
        return bloomLayerTarget != null && bloomDownsampleTarget != null && bloomBlurTarget != null
                && translucentAttenuationTarget != null
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

    private static void configureFloatingColorAttachment(Framebuffer framebuffer, int width, int height) {
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

    private boolean ensureDepthMaskTextures() {
        bloomDepthTexture = resizeDepthTexture(bloomDepthTexture, width, height);
        finalDepthTexture = resizeDepthTexture(finalDepthTexture, width, height);
        translucentDepthTexture = resizeDepthTexture(translucentDepthTexture, width, height);
        return bloomDepthTexture > 0 && finalDepthTexture > 0 && translucentDepthTexture > 0;
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
        return copyDepthTexture(source, bloomDepth ? bloomDepthTexture : finalDepthTexture);
    }

    private boolean copyDepthTexture(Framebuffer source, int destinationTexture) {
        if (source == null || width <= 0 || height <= 0 || destinationTexture <= 0) {
            return false;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(source));
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

    private int nativeBloomGeometryProgram() {
        if (nativeBloomGeometryProgram == -1) {
            nativeBloomGeometryProgram = createProgram(
                    "native-bloom-geometry",
                    NATIVE_BLOOM_GEOMETRY_VERTEX_SHADER,
                    NATIVE_BLOOM_GEOMETRY_FRAGMENT_SHADER,
                    true
            );
        }
        return nativeBloomGeometryProgram;
    }

    private int translucentAttenuationProgram() {
        if (translucentAttenuationProgram == -1) {
            translucentAttenuationProgram = createProgram(
                    "translucent-bloom-attenuation",
                    TRANSLUCENT_ATTENUATION_VERTEX_SHADER,
                    TRANSLUCENT_ATTENUATION_FRAGMENT_SHADER,
                    false
            );
        }
        return translucentAttenuationProgram;
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
                    com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                    "mc_Entity"
            );
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
        private final int alphaFunc;
        private final float alphaRef;
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
            alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(alphaFunc, alphaRef);
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
            attribute vec4 mc_Entity;
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
                // GPOM dual slopes keep both materials in one host mesh. Its
                // per-quad provenance marks native BLOOM material with the
                // framed marker even when that material emits no block light.
                // Treat only that marker as a bloom source; ordinary frame
                // geometry and the other half remain dark.
                if (abs(mc_Entity.w - 150.0) < 0.5) {
                    metadataEmission = max(metadataEmission, 0.8);
                }
                vertexEmission = max(metadataEmission, forceEmission);
            }
            """;

    private static final String NATIVE_BLOOM_GEOMETRY_VERTEX_SHADER = """
            #version 120
            attribute vec4 mc_Entity;
            uniform vec3 ausm_ChunkOffset;
            uniform float framedBloomScale;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 position = gl_Vertex + vec4(ausm_ChunkOffset, 0.0);
                gl_Position = gl_ModelViewProjectionMatrix * position;
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
                if (abs(mc_Entity.w - 151.0) < 0.5) {
                    vertexColor.rgb *= framedBloomScale;
                }
            }
            """;

    private static final String NATIVE_BLOOM_GEOMETRY_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 color = texture2D(terrain, textureCoords) * vertexColor;
                if (color.a <= 0.1) {
                    discard;
                }
                gl_FragColor = color;
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

    private static final String TRANSLUCENT_ATTENUATION_VERTEX_SHADER = """
            #version 120
            uniform vec3 ausm_ChunkOffset;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 position = gl_Vertex + vec4(ausm_ChunkOffset, 0.0);
                gl_Position = gl_ModelViewProjectionMatrix * position;
                textureCoords = gl_MultiTexCoord0.st;
                vertexColor = gl_Color;
            }
            """;

    private static final String TRANSLUCENT_ATTENUATION_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D terrain;
            varying vec2 textureCoords;
            varying vec4 vertexColor;
            void main() {
                vec4 albedo = texture2D(terrain, textureCoords) * vertexColor;
                if (albedo.a <= 0.003921569) {
                    discard;
                }
                float opacity = clamp(albedo.a, 0.0, 1.0);
                vec3 tint = clamp(albedo.rgb, vec3(0.04), vec3(1.0));
                // White glass loses a little bloom energy; coloured glass also
                // filters the transmitted channels. Two visible faces naturally
                // compound, as light entering and leaving a block should.
                vec3 filtered = mix(vec3(0.72), tint, 0.65);
                vec3 transmission = mix(vec3(1.0), filtered, opacity);
                gl_FragColor = vec4(clamp(transmission, vec3(0.08), vec3(1.0)), 1.0);
            }
            """;

    private static final String COPY_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D source;
            uniform sampler2D bloomDepth;
            uniform sampler2D sceneDepth;
            uniform vec2 sourceTexel;
            uniform int depthAware;
            varying vec2 textureCoords;
            void main() {
                if (depthAware == 0) {
                    gl_FragColor = texture2D(source, textureCoords);
                    return;
                }
                vec2 offset = sourceTexel * 0.5;
                vec2 uv0 = textureCoords + vec2(-offset.x, -offset.y);
                vec2 uv1 = textureCoords + vec2( offset.x, -offset.y);
                vec2 uv2 = textureCoords + vec2(-offset.x,  offset.y);
                vec2 uv3 = textureCoords + vec2( offset.x,  offset.y);
                vec4 c0 = texture2D(source, uv0);
                vec4 c1 = texture2D(source, uv1);
                vec4 c2 = texture2D(source, uv2);
                vec4 c3 = texture2D(source, uv3);
                vec3 color = (c0.rgb + c1.rgb + c2.rgb + c3.rgb) * 0.25;
                float depth = 1.0;
                if (max(c0.r, max(c0.g, c0.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv0).r);
                if (max(c1.r, max(c1.g, c1.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv1).r);
                if (max(c2.r, max(c2.g, c2.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv2).r);
                if (max(c3.r, max(c3.g, c3.b)) > 0.00001) depth = min(depth, texture2D(bloomDepth, uv3).r);
                gl_FragColor = vec4(color, depth);
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
            uniform sampler2D bloomDepth;
            uniform sampler2D sceneDepth;
            uniform vec2 direction;
            uniform int depthAware;
            varying vec2 textureCoords;
            void main() {
                vec4 c0 = texture2D(source, textureCoords);
                vec4 c1 = texture2D(source, textureCoords + direction * 1.3846153846);
                vec4 c2 = texture2D(source, textureCoords - direction * 1.3846153846);
                vec4 c3 = texture2D(source, textureCoords + direction * 3.2307692308);
                vec4 c4 = texture2D(source, textureCoords - direction * 3.2307692308);
                vec3 sum = c0.rgb * 0.2270270270;
                sum += c1.rgb * 0.3162162162;
                sum += c2.rgb * 0.3162162162;
                sum += c3.rgb * 0.0702702703;
                sum += c4.rgb * 0.0702702703;
                float depth = 1.0;
                if (depthAware == 1) {
                    if (max(c0.r, max(c0.g, c0.b)) > 0.000001) depth = min(depth, c0.a);
                    if (max(c1.r, max(c1.g, c1.b)) > 0.000001) depth = min(depth, c1.a);
                    if (max(c2.r, max(c2.g, c2.b)) > 0.000001) depth = min(depth, c2.a);
                    if (max(c3.r, max(c3.g, c3.b)) > 0.000001) depth = min(depth, c3.a);
                    if (max(c4.r, max(c4.g, c4.b)) > 0.000001) depth = min(depth, c4.a);
                }
                gl_FragColor = vec4(sum, depth);
            }
            """;

    private static final String COMPOSITE_FRAGMENT_SHADER = """
            #version 120
            uniform sampler2D bloom;
            uniform sampler2D preHandDepth;
            uniform sampler2D postHandDepth;
            uniform sampler2D bloomDepth;
            uniform sampler2D finalDepth;
            uniform sampler2D translucentTransmission;
            uniform sampler2D translucentDepth;
            uniform float strength;
            uniform int useHandMask;
            uniform int useSceneDepthMask;
            uniform int useBloomTextureDepth;
            uniform int useTranslucentDampening;
            varying vec2 textureCoords;
            void main() {
                vec4 bloomSample = texture2D(bloom, textureCoords);
                float emissionDepth = useBloomTextureDepth == 1
                        ? bloomSample.a
                        : texture2D(bloomDepth, textureCoords).r;
                if (useSceneDepthMask == 1) {
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
                vec3 source = bloomSample.rgb;
                if (useTranslucentDampening == 1) {
                    float filterDepth = texture2D(translucentDepth, textureCoords).r;
                    if (filterDepth + 0.00002 < emissionDepth && filterDepth < 0.99999) {
                        source *= texture2D(translucentTransmission, textureCoords).rgb;
                    }
                }
                source = source / (1.0 + max(source, vec3(0.0)));
                gl_FragColor = vec4(source * strength, 1.0);
            }
            """;
}
