package com.l.ausm.impl.pipeline;

import static com.l.ausm.impl.pipeline.PipelineCompatConstants.*;
import static com.l.ausm.impl.pipeline.PipelineDistantHorizonsConstants.*;
import static com.l.ausm.impl.pipeline.PipelineLightConstants.*;
import static com.l.ausm.impl.pipeline.PipelinePresentationConstants.*;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.*;
import static com.l.ausm.impl.pipeline.PipelineRenderConstants.*;
import static com.l.ausm.impl.pipeline.PipelineSkyConstants.*;
import static com.l.ausm.impl.pipeline.PipelineTerrainConstants.*;
import static com.l.ausm.impl.pipeline.PipelineGlState.*;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.*;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.ShaderCompileNotifications;
import com.l.ausm.impl.client.ShaderLoadingScreen;
import com.l.ausm.impl.client.ThaumcraftParticleBridge;
import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.bloom.BloomExtractionPlan;
import com.l.ausm.impl.pipeline.bloom.AusmBloomRenderer;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.CeleritasCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.l.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.l.ausm.impl.pipeline.compat.ShaderlessNothiriumFogGuard;
import com.l.ausm.impl.pipeline.dh.DistantHorizonsInternalShaders;
import com.l.ausm.impl.pipeline.dh.DistantHorizonsMatrixState;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.fbo.PingPongManager;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.mixin.pipeline.EntityRendererAccessor;
import com.l.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.l.ausm.impl.mixin.pipeline.ViewFrustumAccessor;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.l.ausm.api.pipeline.pack.ShaderComputeDirectives;
import com.l.ausm.api.pipeline.pack.ShaderFeatureSet;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import com.l.ausm.impl.pipeline.pack.ShaderItemIdMap;
import com.l.ausm.impl.pipeline.pack.ShaderPackLayout;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderBlockLayerOverrides;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.pack.ShaderFeatureValidator;
import com.l.ausm.impl.pipeline.pack.ShaderEnvironmentDefines;
import com.l.ausm.impl.pipeline.pack.ShaderExpressionEvaluator;
import com.l.ausm.impl.pipeline.pack.ShaderProperties;
import com.l.ausm.impl.pipeline.pack.PipelineShaderSettings;
import com.l.ausm.impl.pipeline.resource.ShaderImageSet;
import com.l.ausm.impl.pipeline.resource.ShaderStorageBufferSet;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.impl.pipeline.render.FullscreenQuad;
import com.l.ausm.impl.pipeline.render.IrisLightmapTexture;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import com.l.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.ComputeProgram;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import com.l.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.l.ausm.impl.pipeline.shader.FullscreenProgramArray;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.api.pipeline.shader.FogMode;
import com.l.ausm.api.pipeline.shader.LightingModel;
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.pipeline.shader.ShaderCompiler;
import com.l.ausm.impl.pipeline.shader.ShaderKey;
import com.l.ausm.impl.pipeline.shader.ShaderLoadingMap;
import com.l.ausm.impl.pipeline.shader.ShaderMap;
import com.l.ausm.impl.pipeline.shader.ShaderProgramSet;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.pipeline.shader.UniformRegistry;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.ConcurrentLongSet;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.RenderList;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.ListChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.chunk.VboChunkFactory;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBTessellationShader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GLContext;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
public class PipelineContext extends PipelineWorldRenderScope {
    private static boolean disableShaderlessPreGuiHooks = true;
    private static final AtomicInteger GUI_BYPASS_PROBE_LOGS = new AtomicInteger();
    private static final Set<String> GUI_BYPASS_PROBE_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SHADER_GUI_PROBE_LOGS = new AtomicInteger();
    private static final Set<String> SHADER_GUI_PROBE_KEYS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger SHADERED_SKY_GEOMETRY_PROBE_LOGS = new AtomicInteger();
    private static final Set<String> SHADERED_SKY_GEOMETRY_PROBE_KEYS = ConcurrentHashMap.newKeySet();

    public void logGuiBypassProbe(String stage) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = minecraft != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(minecraft)
                : null;
        String screenName = screen != null ? screen.getClass().getName() : "null";
        if (!GUI_BYPASS_PROBE_KEYS.add(stage + "|" + screenName)) {
            return;
        }
        int call = GUI_BYPASS_PROBE_LOGS.incrementAndGet();
        if (call > 48) {
            return;
        }
        Framebuffer framebuffer = minecraft != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(minecraft)
                : null;
        int sampleWidth = minecraft == null ? 1 : Math.max(1,
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(minecraft));
        int sampleHeight = minecraft == null ? 1 : Math.max(1,
                com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(minecraft));
        MainMod.LOGGER.info(
                "[AUSMGuiBypass] call={} stage={} active={} worldFrame={} guiDepth={} screen={} framebuffer={} targetColor={} targetDepth={} backColor={} drawFbo={} readFbo={} program={} state={}",
                call,
                stage,
                isPipelineActive,
                worldFrameActive,
                guiRenderDepth,
                screenName,
                describeFramebufferTarget(framebuffer),
                framebufferSamples(framebuffer),
                framebufferDepthSamples(framebuffer),
                framebufferIdColorSamples(0, sampleWidth, sampleHeight, GL11.GL_BACK),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                glStateSummary()
        );
    }

    public void logShaderGuiProbe(String stage) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Object screen = minecraft != null ? MinecraftReflectionCompat.currentScreen(minecraft) : null;
        if (screen == null
                || !screen.getClass().getName().startsWith("com.l.ausm.impl.client.gui.GuiShader")
                || !SHADER_GUI_PROBE_KEYS.add(stage)) {
            return;
        }
        int call = SHADER_GUI_PROBE_LOGS.incrementAndGet();
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(minecraft);
        MainMod.LOGGER.info(
                "[AUSMShaderGuiProbe] call={} stage={} active={} worldFrame={} guiDepth={} renderingGui={} screen={} target={} targetColor={} targetDepth={} drawFbo={} readFbo={} drawBuf={} readBuf={} gl={} glErrors={}",
                call,
                stage,
                isPipelineActive,
                worldFrameActive,
                guiRenderDepth,
                renderingGui,
                screen.getClass().getName(),
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER),
                glStateSummary(),
                drainGlErrorsForProbe()
        );
    }

    public void logShaderedSkyGeometryProbe(String stage) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : null;
        if (!isPipelineActive
                || minecraft == null
                || world == null
                || !isSimpleVoidWorld(world)
                || !SHADERED_SKY_GEOMETRY_PROBE_KEYS.add(stage)) {
            return;
        }
        int call = SHADERED_SKY_GEOMETRY_PROBE_LOGS.incrementAndGet();
        int width = Math.max(1, MinecraftReflectionCompat.displayWidth(minecraft));
        int height = Math.max(1, MinecraftReflectionCompat.displayHeight(minecraft));
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int sampleBuffer = normalizedReadBuffer(drawFramebuffer, drawBuffer);
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(minecraft);
        MainMod.LOGGER.info(
                "[AUSMShaderedSkyGeometryProbe] call={} stage={} pass={} phase={} world={} screen={} target={} targetColor={} targetDepth={} drawFbo={} drawBuf={} drawColor={} drawDepth={} gl={} glErrors={}",
                call,
                stage,
                activePass,
                getPhase(),
                skyProbeWorldSummary(),
                MinecraftReflectionCompat.currentScreen(minecraft) != null
                        ? MinecraftReflectionCompat.currentScreen(minecraft).getClass().getName() : "none",
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                drawFramebuffer,
                drawBuffer,
                framebufferIdColorSamples(drawFramebuffer, width, height, sampleBuffer),
                framebufferIdDepthSamples(drawFramebuffer, width, height, sampleBuffer),
                skyProbeGlStateSummary(),
                drainGlErrorsForProbe()
        );
    }

    public void beginTranslucents() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        if (deferredPassesRenderedThisFrame) {
            return;
        }

        clearPendingPersistentHistoryIfNeeded();
        logDeferredBoundaryProbe("begin-translucents-entry", "beforeDepthCopy=true");
        copyPreTranslucentDepth();
        logDeferredBoundaryProbe("before-deferred", "preDepthCopied=" + preTranslucentDepthCopiedThisFrame);
        DeferredFramebuffer preDeferredBuffer = pingPongManager.getReadBuffer();
        preDeferredColorSnapshotThisFrame = false;
        if (deferredBufferHasColorContent(preDeferredBuffer, fallbackColorAttachment())
                || deferredBufferHasSceneContent(preDeferredBuffer, fallbackColorAttachment())) {
            preDeferredColorSnapshotThisFrame = pingPongManager.snapshotReadAttachmentToRecoveryColor(fallbackColorAttachment());
        }
        runFullscreenPasses(ProgramArrayId.DEFERRED);
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        restorePreDeferredColorIfDeferredBlackened(readBuffer, fallbackColorAttachment(), "after-deferred");
        deferredPassesRenderedThisFrame = true;
        logDeferredBoundaryProbe("after-deferred", "deferredRendered=true");
        bindWorldFramebuffer();
    }

    protected void compositeLatestDistantHorizonsTexture(Framebuffer target) {
        if (shouldUseDistantHorizonsFramebufferOverride()) {
            distantHorizonsFramebufferPendingComposite = false;
            distantHorizonsColorTextureId = 0;
            distantHorizonsDepthTextureId = 0;
            return;
        }
        if (distantHorizonsColorTextureId == 0 || distantHorizonsDepthTextureId == 0) {
            return;
        }
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        compositeDistantHorizonsFramebuffer(target);
    }

    public boolean shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase phase) {
        if (!isPipelineActive || phase == null) {
            return false;
        }

        String ordering = shaderProperties.renderSettings().particlesOrdering();
        if (ordering == null || ordering.isBlank() || "auto".equalsIgnoreCase(ordering)) {
            ordering = hasDeferredPrograms() ? "after" : "mixed";
        }

        return switch (ordering.trim().toLowerCase(Locale.ROOT)) {
            case "before" -> false;
            case "mixed" -> phase == WorldRenderingPhase.PARTICLES_TRANSLUCENT;
            case "after" -> true;
            default -> hasDeferredPrograms();
        };
    }

    protected boolean hasDeferredPrograms() {
        for (RenderPass pass : RenderPass.DEFERRED_PASSES) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                return true;
            }
        }
        return !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.DEFERRED, List.of()).isEmpty();
    }

    public void beginHand() {
        beginTranslucents();
        if (!isPipelineActive || !pingPongManager.isInitialized() || preHandDepthCopiedThisFrame) {
            return;
        }

        // depthtex2 excludes both translucent and hand geometry in OptiFine/Iris.
        // The live depth buffer can already contain water here, so copy the same
        // pre-translucent snapshot instead of sampling the current depth.
        pingPongManager.copyPreTranslucentDepthToPreHandDepth();
        preHandDepthCopiedThisFrame = true;
    }

    public void finishHand() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        // Later post-processing passes use depth snapshots to decide whether a
        // pixel belongs to stable opaque scene history. Refresh depthtex2 after
        // the hand draw so held items have a current near-depth classification
        // without disturbing depthtex1's pre-translucent water/refraction role.
        pingPongManager.copyPreHandDepth();
    }

    public void blitWorldFramebufferToMinecraft() {
        if (!isPipelineActive || !pingPongManager.isInitialized() || !worldFrameActive) {
            return;
        }

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            resetPipelineState();
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Framebuffer target = currentWorldFramebufferTarget(mc);
        if (target == null) {
            resetPipelineState();
            return;
        }
        long worldBlitStartNanos = System.nanoTime();
        currentWorldFrameBlitStartNanos = worldBlitStartNanos;
        long afterTranslucentsNanos = worldBlitStartNanos;
        boolean externalTarget = isExternalWorldFramebufferTarget(target);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-blit-start external=" + externalTarget
                + " target=" + describeFramebufferTarget(target)
                + " read=" + describeDeferredFramebuffer(readBuffer));
        logSkyPresentationRouteProbe("world-blit-start", target, readBuffer, programs.get(RenderPass.FINAL));
        logBetterPortalsPipeline("blit-start", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target));
        beginTranslucents();
        afterTranslucentsNanos = System.nanoTime();
        logBetterPortalsPipeline("after-translucents");
        readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            logBetterPortalsPipeline("abort-null-read-after-translucents");
            resetPipelineState(target);
            return;
        }
        Attachment presentationAttachment = fallbackColorAttachment();
        if (!externalTarget && shouldHoldSparseNothiriumStartupPresentation(readBuffer, presentationAttachment)) {
            holdSparseStartupPresentation(target, "sparse-nothirium-startup");
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            blitReadBufferToPresentationTarget(readBuffer, target, mc,
                    externalTarget ? "choose-external-hardware-safe-pre-composite-blit" : "choose-hardware-safe-pre-composite-blit",
                    externalTarget, true, true);
            return;
        }
        if (externalTarget) {
            logDeferredBoundaryProbe("before-composite", "external=true");
            runFullscreenPasses(ProgramArrayId.COMPOSITE);
            logDeferredBoundaryProbe("after-composite", "external=true");
            logBetterPortalsPipeline("after-external-composite");
            readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer == null) {
                logBetterPortalsPipeline("abort-null-read-after-external-composite");
                resetPipelineState(target);
                return;
            }

            runComputePrograms(finalComputePrograms, RenderPass.FINAL);
            logBetterPortalsPipeline("after-external-final-compute");

            PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
            if (finalProgram != null && finalProgram.hasOwnProgram()) {
                logBetterPortalsPipeline("choose-external-final-pass");
                renderFinalPass(target);
                finishWorldFramebuffer(target, true);
                return;
            }

            blitReadBufferToPresentationTarget(readBuffer, target, mc,
                    "choose-external-composite-blit", true, false, false);
            return;
        }

        if (ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY
                && !shouldPresentPreCompositeForSoftVanillaStartupPack()
                && compositeInvalidFallbackFrames > 0) {
            compositeInvalidFallbackFrames = 0;
        }

        if (ENABLE_COMPOSITE_INVALID_PRESENTATION_RECOVERY
                && !shouldSuppressCompositeRecoveryForSparseNothiriumTerrain()) {
            snapshotCompositeInvalidFallbackSource(readBuffer, presentationAttachment, true,
                    "after-translucents-composite-invalid-snapshot");
        }

        boolean preCompositePresentation = shouldPresentPreCompositeForSoftVanillaStartupPack()
                || shouldPresentPreCompositeForNothiriumCompositeLoss();
        if (preCompositePresentation) {
            compositeInvalidFallbackFrames = COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES;
            boolean currentHasScene = deferredBufferHasSceneContent(readBuffer, presentationAttachment)
                    || deferredBufferHasColorContent(readBuffer, presentationAttachment);
            boolean cachedSnapshot = hasCompositeInvalidFallbackSnapshot(readBuffer);
            if (currentHasScene) {
                if (pingPongManager.snapshotReadAttachmentToRecoveryColor(presentationAttachment)) {
                    compositeInvalidFallbackSnapshotFrame = pipelineFrameId;
                    compositeInvalidFallbackSnapshotHasScene = true;
                }
                logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        true, true, "composite-current", worldBlitStartNanos, afterTranslucentsNanos);
                if (presentPreCompositeWithFinalPassIfNeeded(target, mc, externalTarget,
                        "choose-nothirium-pre-composite-final-pass")) {
                    return;
                }
                blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                        "choose-nothirium-pre-composite-blit", externalTarget, true, true);
                return;
            } else if (cachedSnapshot) {
                logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        false, true, "cached-no-current", worldBlitStartNanos, afterTranslucentsNanos);
                if (restoreCompositeInvalidSnapshotToPresentationAttachment(readBuffer, presentationAttachment, "soft-vanilla-cached-pre-composite")) {
                    if (presentPreCompositeWithFinalPassIfNeeded(target, mc, externalTarget,
                            "choose-soft-vanilla-cached-pre-composite-final-pass")) {
                        return;
                    }
                    blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                            "choose-soft-vanilla-cached-pre-composite-blit", externalTarget, true, true);
                    return;
                }
            } else {
                logSoftVanillaPresentationProbe("soft-branch", readBuffer, presentationAttachment,
                        false, false, "composite-no-snapshot", worldBlitStartNanos, afterTranslucentsNanos);
            }
        }

        logDeferredBoundaryProbe("before-composite", "external=false");
        runFullscreenPasses(ProgramArrayId.COMPOSITE);
        logBetterPortalsPipeline("after-composite");
        logColorBufferProbe("after-composite");
        logDeferredBoundaryProbe("after-composite", "external=false");
        logShaderedVoidSkyTargetProbe("after-composite-before-final", target);
        readBuffer = pingPongManager.getReadBuffer();
        if (readBuffer == null) {
            logBetterPortalsPipeline("abort-null-read-after-composite");
            resetPipelineState(target);
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS
                && preCompositePresentation
                && hasCompositeInvalidFallbackSnapshot(readBuffer)) {
            boolean compositeHasRenderableColor = deferredBufferHasSceneContent(readBuffer, presentationAttachment)
                    || deferredBufferHasColorContent(readBuffer, presentationAttachment);
            if (!compositeHasRenderableColor) {
                compositeInvalidFallbackFrames = COMPOSITE_INVALID_FALLBACK_HOLD_FRAMES;
                logSoftVanillaPresentationProbe("after-composite", readBuffer, presentationAttachment,
                        false, true, "cached-after-composite-lost-scene", worldBlitStartNanos, afterTranslucentsNanos);
                if (restoreCompositeInvalidSnapshotToPresentationAttachment(readBuffer, presentationAttachment, "soft-vanilla-after-composite")) {
                    if (presentPreCompositeWithFinalPassIfNeeded(target, mc, false,
                            "choose-soft-vanilla-after-composite-cached-pre-composite-final-pass")) {
                        return;
                    }
                    blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                            "choose-soft-vanilla-after-composite-cached-pre-composite-blit", false, true, true);
                    return;
                }
            } else {
                logSoftVanillaPresentationProbe("after-composite", readBuffer, presentationAttachment,
                        true, true, "composite-current-color", worldBlitStartNanos, afterTranslucentsNanos);
            }
        }
        if (ENABLE_FLAT_COMPOSITE_SKY_ONLY_FINISH
                && deferredBufferLooksFlatWhiteOrClear(readBuffer, presentationAttachment)) {
            finishFlatCompositeSkyOnlyFrame(target, "flat-clear-after-composite");
            return;
        }
        if (restorePreDeferredColorIfDeferredBlackened(readBuffer, presentationAttachment, "after-composite")) {
            blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                    "choose-pre-deferred-color-after-composite-black", false, true, true, false);
            return;
        }
        if (shouldPresentColorBeforeFinal(readBuffer, presentationAttachment)) {
            blitReadBufferAttachmentToPresentationTarget(readBuffer, presentationAttachment, target, mc,
                    "choose-color-before-final-invalid-source", false, true, true, false);
            return;
        }
        DeferredFramebuffer preFinalReadBuffer = readBuffer;
        Attachment preFinalPresentationAttachment = presentationAttachment;
        boolean canRecoverFromPreFinalColor = shouldPresentPreFinalDirectlyForNothirium(preFinalReadBuffer, preFinalPresentationAttachment, mc);
        runComputePrograms(finalComputePrograms, RenderPass.FINAL);
        logBetterPortalsPipeline("after-final-compute");
        logShaderedVoidSkyTargetProbe("after-final-compute", target);

        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram != null && finalProgram.hasOwnProgram()) {
            logSkyPresentationRouteProbe("choose-final-pass", target, readBuffer, finalProgram);
            logBetterPortalsPipeline("choose-final-pass");
            renderFinalPass(target);
            logShaderedVoidSkyTargetProbe("after-final-pass", target);
            if (canRecoverFromPreFinalColor && framebufferTargetLooksBlackOrClear(target)) {
                logPreFinalDirectPresent(preFinalReadBuffer, preFinalPresentationAttachment, target);
                blitReadBufferAttachmentToPresentationTarget(preFinalReadBuffer, preFinalPresentationAttachment, target, mc,
                        "choose-nothirium-pre-final-after-final-black", false, true, true, false);
                return;
            }
            finishWorldFramebuffer(target, externalTarget);
            return;
        }

        blitReadBufferToPresentationTarget(readBuffer, target, mc,
                "choose-direct-blit", externalTarget, true, true);
    }

    protected boolean shouldHoldSparseNothiriumStartupPresentation(DeferredFramebuffer readBuffer, Attachment attachment) {
        return readBuffer != null
                && attachment != null
                && ENABLE_SPARSE_STARTUP_PRESENTATION_HOLD
                && isPipelineActive
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && shouldUseNothiriumMainTerrainBridge()
                && sparseStartupPresentationHoldFrames < SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES
                && terrainOpaqueDrawCount < SPARSE_STARTUP_PRESENTATION_MIN_TERRAIN_DRAWS
                && hasSparseNothiriumMainTerrainEvidence();
    }

    protected void holdSparseStartupPresentation(Framebuffer target, String reason) {
        int holdFrame = ++sparseStartupPresentationHoldFrames;
        if (sparseStartupPresentationHoldLogs++ < MAX_SPARSE_STARTUP_PRESENTATION_HOLD_LOGS) {
            MainMod.LOGGER.info(
                    "[AUSMSparseStartupPresentation] action=hold reason={} hold={}/{} frame={} terrainCounts=opaque:{}/draw:{} sparseFrameAge={} target={} read={} color={} gl={}",
                    reason,
                    holdFrame,
                    SPARSE_STARTUP_PRESENTATION_HOLD_FRAMES,
                    pipelineFrameId,
                    terrainOpaqueLayerCount,
                    terrainOpaqueDrawCount,
                    nothiriumSparseMainTerrainFrame == Long.MIN_VALUE ? "none" : String.valueOf(pipelineFrameId - nothiriumSparseMainTerrainFrame),
                    describeFramebufferTargetDetailed(target),
                    describeDeferredFramebuffer(pingPongManager != null ? pingPongManager.getReadBuffer() : null),
                    deferredFramebufferColorSamples(pingPongManager != null ? pingPongManager.getReadBuffer() : null, fallbackColorAttachment()),
                    glStateSummary()
            );
        }
        compositeInvalidFallbackFrames = 0;
        clearCompositeInvalidFallbackSnapshot();
        clearSparseStartupSkyOnlyTarget(target);
        logSoftVanillaFrameTimingProbe(false);
        resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish-sparse-startup-hold");
        worldFrameActive = false;
        logBetterPortalsPipeline("finish-sparse-startup-hold", "reason=" + reason);
    }

    protected void clearSparseStartupSkyOnlyTarget(Framebuffer target) {
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        float[] color = sparseStartupSkyOnlyColor(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClearColor(clampColorChannel(color[0]), clampColorChannel(color[1]), clampColorChannel(color[2]), 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glClearColor(
                    previousClearColor.get(0),
                    previousClearColor.get(1),
                    previousClearColor.get(2),
                    previousClearColor.get(3)
            );
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
        }
    }

    protected void finishFlatCompositeSkyOnlyFrame(Framebuffer target, String reason) {
        compositeInvalidFallbackFrames = 0;
        clearCompositeInvalidFallbackSnapshot();
        clearSparseStartupSkyOnlyTarget(target);
        logSoftVanillaFrameTimingProbe(false);
        resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish-flat-composite-sky-only");
        worldFrameActive = false;
        logBetterPortalsPipeline("finish-flat-composite-sky-only", "reason=" + reason);
    }

    protected static float clampColorChannel(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    protected float[] sparseStartupSkyOnlyColor(Minecraft mc) {
        World world = renderWorld(mc);
        if (isSimpleVoidWorld(world)) {
            return new float[]{0.45F, 0.62F, 0.86F};
        }
        float[] color = skyColor(mc);
        float maxChannel = Math.max(color[0], Math.max(color[1], color[2]));
        if (maxChannel < 0.08F) {
            return new float[]{0.45F, 0.62F, 0.86F};
        }
        return color;
    }

    protected static float ausmOfficialNightFactor(World world) {
        long time = world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L : 6000L;
        float timeAngle = (float) time / 24000.0F;
        return Math.max((float) Math.sin(timeAngle * -6.28318530718F), 0.0F);
    }

    protected boolean presentPreCompositeWithFinalPassIfNeeded(Framebuffer target,
                                                            Minecraft mc,
                                                            boolean externalTarget,
                                                            String reason) {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (target == null
                || finalProgram == null
                || !finalProgram.hasOwnProgram()
                || !isSimpleVoidWorld(renderWorld(mc))) {
            return false;
        }
        logBetterPortalsPipeline(reason);
        logSkyPresentationRouteProbe(reason, target, pingPongManager.getReadBuffer(), finalProgram);
        renderFinalPass(target);
        logShaderedVoidSkyTargetProbe("after-" + reason, target);
        finishWorldFramebuffer(target, externalTarget);
        return true;
    }

    protected void blitReadBufferToPresentationTarget(DeferredFramebuffer readBuffer,
                                                    Framebuffer target,
                                                    Minecraft mc,
                                                    String reason,
                                                    boolean externalTarget,
                                                    boolean clearPresentation,
                                                    boolean probeTarget) {
        blitReadBufferAttachmentToPresentationTarget(readBuffer, fallbackColorAttachment(), target, mc, reason, externalTarget,
                clearPresentation, probeTarget);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                    Attachment sourceAttachment,
                                                    Framebuffer target,
                                                    Minecraft mc,
                                                    String reason,
                                                    boolean externalTarget,
                                                    boolean clearPresentation,
                                                    boolean probeTarget) {
        blitReadBufferAttachmentToPresentationTarget(readBuffer, sourceAttachment, target, mc, reason,
                externalTarget, clearPresentation, probeTarget, true);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                    Attachment sourceAttachment,
                                                    Framebuffer target,
                                                    Minecraft mc,
                                                    String reason,
                                                    boolean externalTarget,
                                                    boolean clearPresentation,
                                                    boolean probeTarget,
                                                    boolean renderPostBloom) {
        blitReadBufferAttachmentToPresentationTarget(readBuffer, sourceAttachment, target, mc, reason,
                externalTarget, clearPresentation, probeTarget, renderPostBloom, 1.0F);
    }

    protected void blitReadBufferAttachmentToPresentationTarget(DeferredFramebuffer readBuffer,
                                                    Attachment sourceAttachment,
                                                    Framebuffer target,
                                                    Minecraft mc,
                                                    String reason,
                                                    boolean externalTarget,
                                                    boolean clearPresentation,
                                                    boolean probeTarget,
                                                    boolean renderPostBloom,
                                                    float directPresentColorScale) {
        logBetterPortalsPipeline(reason);
        logSkyPresentationRouteProbe(reason, target, readBuffer, programs.get(RenderPass.FINAL));
        if (clearPresentation) {
            clearPresentationTarget(target, reason);
        }
        readBuffer.blitTo(
                sourceAttachment,
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target)
        );

        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        if (probeTarget) {
            logShaderedVoidSkyTargetProbe("after-" + reason, target);
        }
        if (!renderPostBloom) {
            markDirectRecoveredWindowSource(readBuffer, sourceAttachment, target, directPresentColorScale);
            logDirectColorPresent(reason, readBuffer, sourceAttachment, target);
        }
        finishWorldFramebuffer(target, externalTarget, renderPostBloom);
    }

    protected void markDirectRecoveredWindowSource(DeferredFramebuffer readBuffer,
                                                 Attachment sourceAttachment,
                                                 Framebuffer target,
                                                 float colorScale) {
        if (readBuffer == null || !readBuffer.isUsable() || sourceAttachment == null || target == null) {
            clearDirectRecoveredWindowSource();
            return;
        }
        directRecoveredWindowSource = readBuffer;
        directRecoveredWindowAttachment = sourceAttachment;
        directRecoveredWindowFrame = pipelineFrameId;
        directRecoveredWindowTargetWidth = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        directRecoveredWindowTargetHeight = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        directRecoveredWindowColorScale = Float.isFinite(colorScale) ? Math.max(0.0F, Math.min(1.0F, colorScale)) : 1.0F;
    }

    protected void clearDirectRecoveredWindowSource() {
        directRecoveredWindowSource = null;
        directRecoveredWindowAttachment = null;
        directRecoveredWindowFrame = Long.MIN_VALUE;
        directRecoveredWindowTargetWidth = 0;
        directRecoveredWindowTargetHeight = 0;
        directRecoveredWindowColorScale = 1.0F;
    }

    public void invalidateWorldLoadPresentationState() {
        clearDirectRecoveredWindowSource();
        deleteDirectPresentationSnapshot();
        worldLoadPresentationGuardFrames = Math.max(worldLoadPresentationGuardFrames, 8);
        guiTargetContentFrame = Long.MIN_VALUE;
    }

    protected void deleteDirectPresentationSnapshot() {
        directPresentationValid = false;
        directPresentationFrame = Long.MIN_VALUE;
        directPresentationReason = "";
        directPresentationWidth = 0;
        directPresentationHeight = 0;
        if (directPresentationFbo > 0) {
            GL30.glDeleteFramebuffers(directPresentationFbo);
            directPresentationFbo = -1;
        }
        if (directPresentationTexture > 0) {
            GL11.glDeleteTextures(directPresentationTexture);
            directPresentationTexture = -1;
        }
    }

    protected void logDirectColorPresent(String reason,
                                       DeferredFramebuffer readBuffer,
                                       Attachment sourceAttachment,
                                       Framebuffer target) {
        if (directColorPresentLogs++ >= MAX_DIRECT_COLOR_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectColorPresent] reason={} source={} sourceColor={} sourceDepth={} target={} targetColor={} targetDepth={} frame={} postBloomSkipped=true gl={}",
                reason,
                sourceAttachment,
                deferredFramebufferColorSamples(readBuffer, sourceAttachment),
                readBuffer != null ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                pipelineFrameId,
                glStateSummary()
        );
    }

    protected boolean deferredBufferHasSceneContent(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!isFiniteColor(color) || isClearColor(color)) {
                continue;
            }
            float depth = safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (Float.isFinite(depth) && depth < 0.99999f && !isFlatWhiteColor(color)) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredBufferHasPresentableTerrainColor(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int presentable = 0;
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (isRecoverableColorOnlySceneColor(color)) {
                presentable++;
            }
        }
        return presentable >= 2;
    }

    protected boolean deferredBufferHasColorContent(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (isRecoverableColorOnlySceneColor(color)) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredBufferLooksFlatWhiteOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int flat = 0;
        int clearDepth = 0;
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!isFiniteColor(color)) {
                continue;
            }
            if (isFlatWhiteColor(color) || isClearColor(color)) {
                flat++;
            }
            float depth = safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (depth >= 0.99999f || !Float.isFinite(depth)) {
                clearDepth++;
            }
        }
        return total > 0 && flat == total && clearDepth >= Math.max(1, total - 1);
    }

    protected boolean shouldPresentColorBeforeFinal(DeferredFramebuffer framebuffer, Attachment colorAttachment) {
        if (framebuffer == null
                || colorAttachment == null
                || !deferredBufferHasColorContent(framebuffer, colorAttachment)
                || !isComplementaryFinalColorSourceSensitivePack()) {
            return false;
        }
        return deferredBufferLooksBlackOrClear(framebuffer, Attachment.COMPOSITE)
                || deferredBufferLooksNeutralGrayOrClear(framebuffer, Attachment.COMPOSITE);
    }

    protected boolean shouldPresentPreFinalDirectlyForNothirium(DeferredFramebuffer framebuffer,
                                                              Attachment colorAttachment,
                                                              Minecraft mc) {
        if (framebuffer == null
                || colorAttachment == null
                || !framebuffer.isUsable()
                || !isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || renderingGuiScreen()
                || externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || !isComplementaryFinalColorSourceSensitivePack()
                || !shouldUseNothiriumMainTerrainBridge()
                || !deferredBufferHasColorContent(framebuffer, colorAttachment)) {
            return false;
        }
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (finalProgram == null || !finalProgram.hasOwnProgram()) {
            return false;
        }
        return hasSparseNothiriumMainTerrainEvidence();
    }

    protected void logPreFinalDirectPresent(DeferredFramebuffer framebuffer,
                                          Attachment colorAttachment,
                                          Framebuffer target) {
        if (preFinalDirectPresentLogs++ >= MAX_PRE_FINAL_DIRECT_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMPreFinalDirectPresent] reason=nothirium-presentable-color source={} attachment={} sourceColor={} target={} frame={} sparseEvidence={} simpleVoid={} terrainCounts=opaque:{}/draw:{} gl={}",
                describeDeferredFramebuffer(framebuffer),
                colorAttachment,
                deferredFramebufferColorSamples(framebuffer, colorAttachment),
                describeFramebufferTargetDetailed(target),
                pipelineFrameId,
                hasSparseNothiriumMainTerrainEvidence(),
                isSimpleVoidWorld(renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft())),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                glStateSummary()
        );
    }

    protected boolean isComplementaryFinalColorSourceSensitivePack() {
        String name = activePackName != null ? activePackName.toLowerCase(Locale.ROOT) : "";
        return name.contains("complementary")
                || name.contains("complimentary")
                || name.contains("entree")
                || name.contains("entrée");
    }

    protected boolean deferredBufferLooksNeutralGrayOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int neutralOrClear = 0;
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!isFiniteColor(color) || isClearColor(color) || isNeutralGrayColor(color)) {
                neutralOrClear++;
            }
        }
        return total > 0 && neutralOrClear == total;
    }

    protected boolean deferredBufferLooksBlackOrClear(DeferredFramebuffer framebuffer, Attachment attachment) {
        if (framebuffer == null || !framebuffer.isUsable() || attachment == null) {
            return false;
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int total = 0;
        int blackOrClear = 0;
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            total++;
            float[] color = safeReadDeferredColor(framebuffer, attachment, point[0], point[1]);
            if (!isFiniteColor(color) || isClearColor(color)) {
                blackOrClear++;
            }
        }
        return total > 0 && blackOrClear == total;
    }

    protected boolean framebufferTargetLooksBlackOrClear(Framebuffer target) {
        if (target == null) {
            return false;
        }
        int framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target);
        int width = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        int height = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        int readBuffer = framebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int total = 0;
        int blackOrClear = 0;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            for (int[] point : compositeFallbackProbePoints(width, height)) {
                total++;
                terrainProbeColorPixel.clear();
                GL11.glReadPixels(
                        Math.max(0, Math.min(width - 1, point[0])),
                        Math.max(0, Math.min(height - 1, point[1])),
                        1,
                        1,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        terrainProbeColorPixel
                );
                int r = terrainProbeColorPixel.get(0) & 0xFF;
                int g = terrainProbeColorPixel.get(1) & 0xFF;
                int b = terrainProbeColorPixel.get(2) & 0xFF;
                int a = terrainProbeColorPixel.get(3) & 0xFF;
                if (a <= 2 || (r <= 2 && g <= 2 && b <= 2)) {
                    blackOrClear++;
                }
            }
        } catch (RuntimeException | LinkageError e) {
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
        return total > 0 && blackOrClear == total;
    }

    protected boolean restorePreDeferredColorIfDeferredBlackened(DeferredFramebuffer framebuffer,
                                                               Attachment attachment,
                                                               String reason) {
        if (!preDeferredColorSnapshotThisFrame
                || framebuffer == null
                || attachment == null
                || !framebuffer.hasRecoveryColorSnapshot()
                || !recoveryColorSnapshotHasPresentableContent(framebuffer)
                || !deferredBufferLooksBlackOrClear(framebuffer, attachment)) {
            return false;
        }
        boolean restored = pingPongManager.restoreRecoveryColorToReadAttachment(attachment);
        if (restored) {
            logPreDeferredColorRestore(framebuffer, attachment, reason);
        }
        return restored;
    }

    protected void logPreDeferredColorRestore(DeferredFramebuffer framebuffer, Attachment attachment, String reason) {
        if (preDeferredColorRestoreLogs++ >= MAX_PRE_DEFERRED_COLOR_RESTORE_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMPreDeferredColorRestore] reason={} target={} currentColor={} preservedColor={} depth={} depthtex1={} frame={}",
                reason,
                attachment,
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                pipelineFrameId
        );
    }

    protected int[][] compositeFallbackProbePoints(int width, int height) {
        int maxX = Math.max(0, width - 1);
        int maxY = Math.max(0, height - 1);
        return new int[][] {
                {width / 2, height / 2},
                {width / 4, height / 2},
                {(width * 3) / 4, height / 2},
                {width / 2, height / 4},
                {width / 2, (height * 3) / 4},
                {Math.min(maxX, width / 3), Math.min(maxY, height / 3)}
        };
    }

    protected float[] safeReadDeferredColor(DeferredFramebuffer framebuffer, Attachment attachment, int x, int y) {
        try {
            return framebuffer.readColorAt(attachment, x, y);
        } catch (RuntimeException | LinkageError e) {
            return new float[] {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }
    }

    protected float[] safeReadRecoveryColor(DeferredFramebuffer framebuffer, int x, int y) {
        try {
            return framebuffer.readRecoveryColorAt(x, y);
        } catch (RuntimeException | LinkageError e) {
            return new float[] {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        }
    }

    protected float safeReadDeferredDepth(DeferredFramebuffer framebuffer, int x, int y, int colorWidth, int colorHeight) {
        try {
            int depthX = Math.max(0, Math.min(framebuffer.getWidth() - 1,
                    Math.round(x * (framebuffer.getWidth() - 1) / (float) Math.max(1, colorWidth - 1))));
            int depthY = Math.max(0, Math.min(framebuffer.getHeight() - 1,
                    Math.round(y * (framebuffer.getHeight() - 1) / (float) Math.max(1, colorHeight - 1))));
            return framebuffer.readDepthAtPixel(depthX, depthY);
        } catch (RuntimeException | LinkageError e) {
            return Float.NaN;
        }
    }

    protected float safeReadDeferredDepthSnapshot(DeferredFramebuffer framebuffer, int snapshotIndex, int x, int y) {
        try {
            return framebuffer.readDepthSamplerAtPixel(
                    snapshotIndex,
                    Math.max(0, Math.min(framebuffer.getWidth() - 1, x)),
                    Math.max(0, Math.min(framebuffer.getHeight() - 1, y))
            );
        } catch (RuntimeException | LinkageError e) {
            return Float.NaN;
        }
    }

    protected boolean isFiniteColor(float[] color) {
        return color != null
                && color.length >= 4
                && Float.isFinite(color[0])
                && Float.isFinite(color[1])
                && Float.isFinite(color[2])
                && Float.isFinite(color[3]);
    }

    protected boolean isFlatWhiteColor(float[] color) {
        return color[0] >= 0.985f && color[1] >= 0.985f && color[2] >= 0.985f && color[3] >= 0.985f;
    }

    protected boolean isNeutralGrayColor(float[] color) {
        if (!isFiniteColor(color) || color[3] <= 0.001f) {
            return false;
        }
        float max = Math.max(color[0], Math.max(color[1], color[2]));
        float min = Math.min(color[0], Math.min(color[1], color[2]));
        return max >= 0.45f && max <= 0.95f && max - min <= 0.035f;
    }

    protected boolean isRecoverableColorOnlySceneColor(float[] color) {
        if (!isFiniteColor(color) || isClearColor(color) || isFlatWhiteColor(color)) {
            return false;
        }
        float maxChannel = Math.max(color[0], Math.max(color[1], color[2]));
        float luma = color[0] * 0.2126F + color[1] * 0.7152F + color[2] * 0.0722F;
        return maxChannel >= COMPOSITE_RECOVERY_COLOR_MIN_MAX_CHANNEL
                && luma >= COMPOSITE_RECOVERY_COLOR_MIN_LUMA;
    }

    protected boolean isClearColor(float[] color) {
        return color[3] <= 0.001f
                || (Math.max(color[0], Math.max(color[1], color[2])) <= 0.001f && color[3] >= 0.999f);
    }

    protected void logSoftVanillaPresentationProbe(String stage, DeferredFramebuffer framebuffer, Attachment attachment,
                                                 boolean currentHasScene, boolean cachedSnapshot, String selected,
                                                 long worldBlitStartNanos, long afterTranslucentsNanos) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaPresentationProbeLogs >= MAX_SOFT_VANILLA_PRESENTATION_PROBE_LOGS) {
            return;
        }
        softVanillaPresentationProbeLogs++;
        long now = System.nanoTime();
        long snapshotAge = compositeInvalidFallbackSnapshotHasScene
                ? pipelineFrameId - compositeInvalidFallbackSnapshotFrame
                : Long.MAX_VALUE;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaPresentationProbe] call={} stage={} selected={} pack={} currentHasScene={} cachedSnapshot={} snapshotAge={} holdFrames={} source={} preserved={} beginTranslucentsMs={} totalBeforeDecisionMs={} frame={} frameTime={} read={} currentColor={} preservedColor={} currentDepth={} glProgram={}",
                softVanillaPresentationProbeLogs,
                stage,
                selected,
                activePackName,
                currentHasScene,
                cachedSnapshot,
                snapshotAge == Long.MAX_VALUE ? "none" : String.valueOf(snapshotAge),
                compositeInvalidFallbackFrames,
                attachment,
                COMPOSITE_INVALID_FALLBACK_SOURCE,
                formatMillis(nanosToMillis(afterTranslucentsNanos - worldBlitStartNanos)),
                formatMillis(nanosToMillis(now - worldBlitStartNanos)),
                pipelineFrameId,
                formatMillis(currentFrameTime * 1000.0D),
                describeDeferredFramebuffer(framebuffer),
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    protected static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }

    protected void finishWorldFramebuffer(Framebuffer target, boolean externalTarget) {
        finishWorldFramebuffer(target, externalTarget, true);
    }

    protected void finishWorldFramebuffer(Framebuffer target, boolean externalTarget, boolean renderPostBloom) {
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-before-reset external=" + externalTarget
                + " target=" + describeFramebufferTarget(target));
        logBetterPortalsPipeline("finish-before-reset", "external=" + externalTarget
                + ", target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target)
                + ", postBloom=" + renderPostBloom);
        logSkyPresentationRouteProbe("finish-before-reset", target,
                pingPongManager == null ? null : pingPongManager.getReadBuffer(),
                programs.get(RenderPass.FINAL));
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        if (renderPostBloom) {
            renderPostWorldBloom(target, externalTarget);
        }
        if (!externalTarget) {
            snapshotPresentationTargetForDirectPresentation(target, renderPostBloom ? "finish-world-post-bloom" : "finish-world-direct-color");
        }
        if (!externalTarget) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }
        logSoftVanillaFrameTimingProbe(externalTarget);
        resetPipelineState(target);
        drainPausedPostRenderGlErrors("world-finish");
        worldFrameActive = false;
        if (!externalTarget && worldLoadPresentationGuardFrames > 0) {
            worldLoadPresentationGuardFrames--;
        }
        logBetterPortalsPipeline("finish-after-reset", "external=" + externalTarget);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:finish-world-after-reset external=" + externalTarget);
    }

    protected void logSoftVanillaFrameTimingProbe(boolean externalTarget) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaFrameTimingLogs >= MAX_SOFT_VANILLA_FRAME_TIMING_LOGS
                || currentWorldFrameStartNanos == Long.MIN_VALUE) {
            return;
        }
        long now = System.nanoTime();
        long ready = currentWorldFrameReadyNanos != Long.MIN_VALUE ? currentWorldFrameReadyNanos : currentWorldFrameStartNanos;
        long finishStart = currentWorldFrameFinishStartNanos != Long.MIN_VALUE ? currentWorldFrameFinishStartNanos : ready;
        long afterNativeBloom = currentWorldFrameAfterNativeBloomNanos != Long.MIN_VALUE ? currentWorldFrameAfterNativeBloomNanos : finishStart;
        long blitStart = currentWorldFrameBlitStartNanos != Long.MIN_VALUE ? currentWorldFrameBlitStartNanos : afterNativeBloom;
        double totalMs = nanosToMillis(now - currentWorldFrameStartNanos);
        if (softVanillaFrameTimingLogs >= 8 && totalMs < 50.0D) {
            return;
        }
        softVanillaFrameTimingLogs++;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaFrameTiming] call={} frame={} frameTime={} totalMs={} beginMs={} worldRenderMs={} nativeBloomMs={} preBlitGapMs={} blitFinishMs={} external={} activePass={} phase={} terrainOpaqueLayers={} terrainOpaqueDraws={} presentationHold={} snapshotAge={} glProgram={}",
                softVanillaFrameTimingLogs,
                pipelineFrameId,
                formatMillis(currentFrameTime * 1000.0D),
                formatMillis(totalMs),
                formatMillis(nanosToMillis(ready - currentWorldFrameStartNanos)),
                formatMillis(nanosToMillis(finishStart - ready)),
                formatMillis(nanosToMillis(afterNativeBloom - finishStart)),
                formatMillis(nanosToMillis(blitStart - afterNativeBloom)),
                formatMillis(nanosToMillis(now - blitStart)),
                externalTarget,
                activePass,
                getPhase(),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                compositeInvalidFallbackFrames,
                compositeInvalidFallbackSnapshotHasScene ? String.valueOf(pipelineFrameId - compositeInvalidFallbackSnapshotFrame) : "none",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected void clearPresentationTarget(Framebuffer target, String reason) {
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
    }

    protected void clearWorldLoadPresentationFramebuffer(Minecraft mc) {
        if (worldLoadPresentationGuardFrames <= 0 || mc == null) {
            return;
        }
        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target == null || isExternalWorldFramebufferTarget(target)) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
    }

    public void clearWorldLoadWindowBackbuffer(Minecraft mc) {
        if (worldLoadPresentationGuardFrames <= 0 || mc == null) {
            return;
        }

        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glDepthMask(previousDepthMask);
        }
    }

    protected void runFullscreenPasses(RenderPass[] passes) {
        for (RenderPass pass : passes) {
            PipelineProgram program = programs.get(pass);
            if (program != null && program.hasOwnProgram()) {
                runFullscreenPass(program);
            }
        }
    }

    protected void runFullscreenPasses(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            runShadowCompPasses();
            return;
        }

        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(arrayId, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(arrayId, List.of());
        FullscreenProgramArray array = fullscreenProgramArrays.get(arrayId);
        List<RenderPass> fixedPasses = array == null ? List.of() : array.fixedPasses();
        int maxIndex = Math.max(maxComputeArrayIndex(computes), maxFullscreenArrayProgramIndex(indexedPrograms));
        if (!fixedPasses.isEmpty()) {
            maxIndex = Math.max(maxIndex, fixedPasses.size() - 1);
        }

        RenderPass computeBindingPass = computeBindingPass(arrayId);
        for (int index = 0; index <= maxIndex; index++) {
            runComputeProgramsForArrayIndex(computes, index, computeBindingPass);

            if (index < fixedPasses.size()) {
                PipelineProgram program = programs.get(fixedPasses.get(index));
                if (program != null && program.hasOwnProgram()) {
                    runFullscreenPass(program);
                }
            }

            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    runFullscreenArrayProgram(program);
                }
            }
        }
    }

    protected void runShadowCompPasses() {
        int size = shadowFramebuffer != null ? shadowFramebuffer.resolution() : 1;
        List<ComputeProgram> computes = computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        List<FullscreenArrayProgram> indexedPrograms = fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of());
        int maxIndex = Math.max(maxComputeArrayIndex(computes), maxFullscreenArrayProgramIndex(indexedPrograms));
        for (int index = 0; index <= maxIndex; index++) {
            runComputeProgramsForArrayIndex(computes, index, RenderPass.SHADOW, size, size);
            for (FullscreenArrayProgram program : indexedPrograms) {
                if (program.index() == index && program.hasProgram()) {
                    runShadowCompArrayProgram(program);
                }
            }
        }
    }

    protected void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass) {
        runComputeProgramsForArrayIndex(computes, index, bindingPass, -1, -1);
    }

    protected void runComputeProgramsForArrayIndex(List<ComputeProgram> computes, int index, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        List<ComputeProgram> indexedComputes = new ArrayList<>();
        for (ComputeProgram compute : computes) {
            if (compute != null && compute.arrayIndex() == index) {
                indexedComputes.add(compute);
            }
        }
        if (indexedComputes.isEmpty()) {
            return;
        }
        if (width > 0 && height > 0) {
            runComputePrograms(indexedComputes, bindingPass, width, height);
        } else {
            runComputePrograms(indexedComputes, bindingPass);
        }
    }

    protected static int maxComputeArrayIndex(List<ComputeProgram> computes) {
        int max = -1;
        if (computes != null) {
            for (ComputeProgram compute : computes) {
                if (compute != null) {
                    max = Math.max(max, compute.arrayIndex());
                }
            }
        }
        return max;
    }

    protected static int maxFullscreenArrayProgramIndex(List<FullscreenArrayProgram> programs) {
        int max = -1;
        if (programs != null) {
            for (FullscreenArrayProgram program : programs) {
                if (program != null && program.hasProgram()) {
                    max = Math.max(max, program.index());
                }
            }
        }
        return max;
    }

    protected void runSetupComputesIfNeeded() {
        if (!setupComputePending) {
            return;
        }
        setupComputePending = false;
        runFullscreenPasses(ProgramArrayId.SETUP);
    }

    protected RenderPass computeBindingPass(ProgramArrayId arrayId) {
        if (arrayId == ProgramArrayId.SETUP || arrayId == ProgramArrayId.BEGIN || arrayId == ProgramArrayId.PREPARE) {
            return RenderPass.PREPARE;
        }
        if (arrayId == ProgramArrayId.DEFERRED) {
            return RenderPass.DEFERRED;
        }
        if (arrayId == ProgramArrayId.COMPOSITE) {
            return RenderPass.COMPOSITE;
        }
        if (arrayId == ProgramArrayId.SHADOWCOMP) {
            return RenderPass.SHADOW;
        }
        return RenderPass.FINAL;
    }

    protected void runFullscreenArrayProgram(FullscreenArrayProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.copyReadToWrite(drawBufferArray);
        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        generateReadMipmaps(program.directives());

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        setupFullscreenState();
        try {
            applyFullscreenViewport(program.name(), program.directives(), drawBuffers);
            applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            bindFullscreenArrayProgram(program);
            FullscreenQuad.draw();
        } finally {
            if (program.shaderProgram() != null) {
                program.shaderProgram().unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        generateWrittenMipmaps(program.directives(), flippedAttachments);
        if (program.arrayId() == ProgramArrayId.COMPOSITE) {
            logCompositeChainProbe(
                    "after-indexed-composite-pass",
                    "name=" + program.name()
                            + ", index=" + program.index()
                            + ", drawBuffers=" + drawBuffers
                            + ", flipped=" + java.util.Arrays.toString(flippedAttachments)
                            + ", directivesDrawBuffers=" + program.directives().drawBuffers());
        }
        if (program.arrayId() == ProgramArrayId.COMPOSITE) {
            logShaderedVoidSkyAttachmentProbe(
                    "after-indexed-composite-pass",
                    "name=" + program.name()
                            + ", index=" + program.index()
                            + ", drawBuffers=" + drawBuffers
                            + ", flipped=" + java.util.Arrays.toString(flippedAttachments));
        }
    }

    protected void bindFullscreenArrayProgram(FullscreenArrayProgram program) {
        ShaderProgram shaderProgram = program.shaderProgram();
        if (shaderProgram == null) {
            return;
        }

        RenderPass bindingPass = program.bindingPass();
        activePass = bindingPass;
        activeShaderKey = ShaderKey.fromRenderPass(bindingPass);
        activePhase = WorldRenderingPhase.NONE;
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(bindingPass);
        shaderProgram.bind();
        bindProgramResources(bindingPass, shaderProgram);
        customTextures.bind(program.arrayId(), program.index(), shaderProgram);
    }

    protected void applyFullscreenArrayRenderState(ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        ShaderAlphaTest alphaTest = directives.alphaTestOverride();
        if (alphaTest != null) {
            currentAlphaTestReference = alphaTest.reference();
            if (alphaTest.function() == GL11.GL_ALWAYS) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());
        }

        ShaderBlendMode blendMode = directives.blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = directives.attachmentBlendModes();
        if (blendMode == null && attachmentModes.isEmpty()) {
            return;
        }
        if (blendMode != null && !blendMode.enabled()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        if (blendMode != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            ShaderBlendMode attachmentMode = attachmentModes.get(drawBuffers.get(drawBufferIndex));
            if (attachmentMode != null) {
                applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            }
        }
    }

    protected void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int width = framebuffer != null ? framebuffer.getWidth() : mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) : 1;
        int height = framebuffer != null ? framebuffer.getHeight() : mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) : 1;
        runComputePrograms(computes, bindingPass, width, height);
    }

    protected void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass, int width, int height) {
        if (computes == null || computes.isEmpty()) {
            return;
        }
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        for (ComputeProgram compute : computes) {
            if (compute == null) {
                continue;
            }
            applyComputeMemoryBarrier(compute.hasIndirectPointer());
            compute.bind();
            TextureBinder.bindDeferredTextures();
            TextureBinder.bindShadowTextures(bindingPass);
            bindProgramResources(bindingPass, compute.program());
            if (compute.hasIndirectPointer()) {
                int bufferId = shaderStorageBuffers.glBufferId(compute.indirectBuffer());
                if (bufferId != 0) {
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, bufferId);
                    GL43.glDispatchComputeIndirect(compute.indirectOffset());
                    GL15.glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, 0);
                } else {
                    MainMod.LOGGER.warn(
                            "[Pipeline] Skipping indirect compute '{}' because SSBO binding {} is unavailable",
                            compute.name(),
                            compute.indirectBuffer()
                    );
                }
            } else {
                int[] groups = compute.workGroups(safeWidth, safeHeight);
                GL43.glDispatchCompute(groups[0], groups[1], groups[2]);
            }
            applyComputeMemoryBarrier(false);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void applyComputeMemoryBarrier(boolean indirectDispatch) {
        int barriers = 0;
        if (shaderProperties == null || !shaderProperties.renderSettings().allowConcurrentCompute()) {
            barriers |= GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT
                    | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                    | GL42.GL_TEXTURE_FETCH_BARRIER_BIT
                    | GL42.GL_FRAMEBUFFER_BARRIER_BIT;
        }
        if (indirectDispatch) {
            barriers |= GL42.GL_COMMAND_BARRIER_BIT;
        }
        if (barriers != 0) {
            GL42.glMemoryBarrier(barriers);
        }
    }

    protected void applyShaderImageTextureBarrier() {
        if (shaderImages.active() && GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
    }

    protected void runFullscreenPass(PipelineProgram program) {
        List<Attachment> drawBuffers = program.drawBuffers();
        Attachment[] drawBufferArray = drawBuffers.toArray(new Attachment[0]);

        pingPongManager.copyReadToWrite(drawBufferArray);
        pingPongManager.bindForFullscreenWrite(drawBufferArray);
        generateReadMipmaps(program);

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        setupFullscreenState();
        try {
            applyFullscreenViewport(program, drawBuffers);
            applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            if (bindFullscreenPipelineProgram(program)) {
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = program.shaderProgram();
            if (shaderProgram != null) {
                shaderProgram.unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        Attachment[] flippedAttachments = program.directives().flippedAttachments(drawBuffers);
        pingPongManager.flipWrittenTextures(flippedAttachments);
        generateWrittenMipmaps(program, flippedAttachments);
        if (program.stage() == ProgramStage.DEFERRED) {
            logDeferredBoundaryProbe(
                    "after-deferred-pass",
                    "pass=" + program.pass()
                            + ", program=" + program.shaderProgram().getName()
                            + ", drawBuffers=" + drawBuffers
                            + ", flipped=" + java.util.Arrays.toString(flippedAttachments)
                            + ", directivesDrawBuffers=" + program.directives().drawBuffers());
        }
        if (program.stage() == ProgramStage.COMPOSITE) {
            logCompositeChainProbe(
                    "after-composite-pass",
                    "pass=" + program.pass()
                            + ", program=" + program.shaderProgram().getName()
                            + ", drawBuffers=" + drawBuffers
                            + ", flipped=" + java.util.Arrays.toString(flippedAttachments)
                            + ", directivesDrawBuffers=" + program.directives().drawBuffers());
            logShaderedVoidSkyAttachmentProbe(
                    "after-composite-pass",
                    "pass=" + program.pass()
                            + ", drawBuffers=" + drawBuffers
                            + ", flipped=" + java.util.Arrays.toString(flippedAttachments));
        }
    }

    protected boolean bindFullscreenPipelineProgram(PipelineProgram program) {
        if (program == null || program.shaderProgram() == null) {
            return false;
        }

        ShaderProgram shaderProgram = program.shaderProgram();
        activePass = program.pass();
        activeShaderKey = program.shaderKey();
        activePhase = WorldRenderingPhase.NONE;
        activeProgramTessellated = shaderProgram.isTessellated();
        activeProgramGeometric = shaderProgram.isGeometric();
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(program.pass());
        shaderProgram.bind();
        bindProgramResources(program.pass(), shaderProgram);
        // Resource and uniform uploads may touch arbitrary texture units.
        // Reassert the deferred inputs last so fullscreen passes sample the
        // current ping-pong read textures, never an output or stale binding.
        TextureBinder.bindDeferredTextures();
        TextureBinder.bindShadowTextures(program.pass());
        logFullscreenSamplerProbe(program, shaderProgram);
        return true;
    }

    protected void logFullscreenSamplerProbe(PipelineProgram program, ShaderProgram shaderProgram) {
        if (program == null || shaderProgram == null
                || fullscreenSamplerProbeLogs >= MAX_FULLSCREEN_SAMPLER_PROBE_LOGS) {
            return;
        }
        RenderPass pass = program.pass();
        if (pass != RenderPass.DEFERRED1
                && pass != RenderPass.COMPOSITE1
                && pass != RenderPass.COMPOSITE3
                && pass != RenderPass.COMPOSITE5) {
            return;
        }

        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int liveColorTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(previousActiveTexture);
        int location = shaderProgram.getUniformLocation("colortex0");
        int samplerUnit = -1;
        if (location >= 0) {
            IntBuffer samplerValue = BufferUtils.createIntBuffer(1);
            GL20.glGetUniform(shaderProgram.getId(), location, samplerValue);
            samplerUnit = samplerValue.get(0);
        }
        fullscreenSamplerProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMFullscreenSamplerProbe] call={} pass={} expectedColor={} writeColor={} liveUnit0={} samplerUnit={} program={} drawFbo={} drawBuffers={}",
                fullscreenSamplerProbeLogs,
                pass,
                framebuffer != null ? framebuffer.getReadTexture(Attachment.COLOR) : -1,
                framebuffer != null ? framebuffer.getWriteTexture(Attachment.COLOR) : -1,
                liveColorTexture,
                samplerUnit,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                drawBuffersProbeSummary()
        );
    }

    protected void applyViewportScale(PipelineProgram program, int width, int height) {
        applyViewportScale(program.directives().viewportScale(), width, height);
    }

    protected void applyViewportScale(ShaderViewportScale scale, int width, int height) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(scale.x(width), scale.y(height), scale.width(width), scale.height(height));
    }

    protected void applyFullscreenViewport(PipelineProgram program, List<Attachment> drawBuffers) {
        applyFullscreenViewport(program.pass().getProgramName(), program.directives(), drawBuffers);
    }

    protected void applyFullscreenViewport(String programName, ShaderProgramDirectives directives, List<Attachment> drawBuffers) {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();
        if (!drawBuffers.isEmpty()) {
            Attachment first = drawBuffers.get(0);
            width = framebuffer.getAttachmentWidth(first);
            height = framebuffer.getAttachmentHeight(first);
            for (Attachment attachment : drawBuffers) {
                if (framebuffer.getAttachmentWidth(attachment) != width || framebuffer.getAttachmentHeight(attachment) != height) {
                    MainMod.LOGGER.warn("[Pipeline] Pass {} writes differently sized buffers; using {} size {}x{} for viewport",
                            programName, first, width, height);
                    break;
                }
            }
        }
        applyViewportScale(directives.viewportScale(), width, height);
    }

    protected void renderFinalPass(Framebuffer target) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (target == null || readBuffer == null || finalProgram == null) {
            logBetterPortalsPipeline("final-pass-skip", "target=" + describeFramebufferTargetDetailed(target)
                    + ", read=" + describeDeferredFramebuffer(readBuffer)
                    + ", final=" + describePipelineProgram(finalProgram));
            return;
        }

        logBetterPortalsPipeline("final-pass-start", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target)
                + ", read=" + describeDeferredFramebuffer(readBuffer)
                + ", final=" + describePipelineProgram(finalProgram));
        logColorBufferProbe("before-final");
        logCompositeChainProbe("before-final-pass", "final=" + describePipelineProgram(finalProgram)
                + ", finalDrawBuffers=" + finalProgram.drawBuffers()
                + ", directivesDrawBuffers=" + finalProgram.directives().drawBuffers());
        logShaderedVoidSkyTargetProbe("final-before-clear", target);
        clearPresentationTarget(target, "final-pass");
        readBuffer.blitDepthTo(
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target)
        );

        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
        GL11.glColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        generateReadMipmaps(finalProgram);

        setupFullscreenState();
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        try {
            applyViewportScale(finalProgram, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
            applyFullscreenArrayRenderState(finalProgram.directives(), finalProgram.drawBuffers());
            if (bindFullscreenPipelineProgram(finalProgram)) {
                logFinalSkyRepairProbe(finalProgram);
                FullscreenQuad.draw();
            }
        } finally {
            ShaderProgram shaderProgram = finalProgram.shaderProgram();
            if (shaderProgram != null) {
                shaderProgram.unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        logShaderedVoidSkyTargetProbe("final-after-draw", target);
        logBetterPortalsPipeline("final-pass-end", "target=" + describeFramebufferTargetDetailed(target)
                + ", targetStatus=" + framebufferStatus(target));
    }

    protected void logFinalSkyRepairProbe(PipelineProgram finalProgram) {
        if (!DEBUG_PROBES_ENABLED || finalSkyRepairProbeLogs++ >= 48 || finalProgram == null) {
            return;
        }
        ShaderProgram shader = finalProgram.shaderProgram();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMFinalSkyRepairProbe] pass={} shader={} skybox={} ui={} simpleVoid={} screen={} hideGui={} paused={} locations={}/{}/{}/{}",
                finalProgram.pass(),
                shader == null ? "null" : shader.getName(),
                shouldRepairCurrentSkybox(mc),
                shouldForceUiSkyboxRepair(mc),
                isSimpleVoidWorld(renderWorld(mc)),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null,
                com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc),
                shader == null ? -2 : shader.getUniformLocation("ausmSkyboxRepair"),
                shader == null ? -2 : shader.getUniformLocation("ausmUiSkyRepair"),
                shader == null ? -2 : shader.getUniformLocation("depthtex0"),
                shader == null ? -2 : shader.getUniformLocation("colortex0")
        );
    }

    protected void logSkyPresentationRouteProbe(String route, Framebuffer target,
                                                DeferredFramebuffer readBuffer,
                                                PipelineProgram finalProgram) {
        if (!DEBUG_PROBES_ENABLED || skyPresentationRouteProbeLogs++ >= 64) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        ShaderProgram shader = finalProgram == null ? null : finalProgram.shaderProgram();
        MainMod.LOGGER.info(
                "[AUSMSkyRouteProbe] route={} final={} hasOwnFinal={} shader={} skybox={} ui={} simpleVoid={} screen={} hideGui={} target={} read={} locations={}/{}",
                route,
                describePipelineProgram(finalProgram),
                finalProgram != null && finalProgram.hasOwnProgram(),
                shader == null ? "null" : shader.getName(),
                shouldRepairCurrentSkybox(mc),
                shouldForceUiSkyboxRepair(mc),
                isSimpleVoidWorld(renderWorld(mc)),
                com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null,
                com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                describeFramebufferTargetDetailed(target),
                describeDeferredFramebuffer(readBuffer),
                shader == null ? -2 : shader.getUniformLocation("ausmSkyboxRepair"),
                shader == null ? -2 : shader.getUniformLocation("ausmUiSkyRepair")
        );
    }

    public void logExternalSkyPresentationRouteProbe(String route, Framebuffer target) {
        logSkyPresentationRouteProbe(route, target, pingPongManager.getReadBuffer(), programs.get(RenderPass.FINAL));
    }

    protected void generateReadMipmaps(PipelineProgram program) {
        if (program != null) {
            generateReadMipmaps(program.directives());
        }
    }

    protected void generateReadMipmaps(ShaderProgramDirectives directives) {
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        if (directives != null && readBuffer != null && !directives.mipmappedBuffers().isEmpty()) {
            readBuffer.generateMipmaps(directives.mipmappedBuffers());
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void generateWrittenMipmaps(PipelineProgram program, Attachment[] flippedAttachments) {
        if (program == null) {
            return;
        }
        generateWrittenMipmaps(program.directives(), flippedAttachments);
    }

    protected void runShadowCompArrayProgram(FullscreenArrayProgram program) {
        if (shadowFramebuffer == null) {
            return;
        }

        List<Attachment> drawBuffers = program.drawBuffers();
        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        shadowFramebuffer.bindForProgramWrite(drawBuffers.toArray(new Attachment[0]));
        setupFullscreenState();
        try {
            applyViewportScale(program.directives().viewportScale(), shadowFramebuffer.resolution(), shadowFramebuffer.resolution());
            applyFullscreenArrayRenderState(program.directives(), drawBuffers);
            bindFullscreenArrayProgram(program);
            FullscreenQuad.draw();
        } finally {
            if (program.shaderProgram() != null) {
                program.shaderProgram().unbind();
            }
            restoreFullscreenState();
            activePass = previousPass;
            activeShaderKey = previousShaderKey;
            activePhase = previousPhase;
            activeProgramTessellated = previousProgramTessellated;
            activeProgramGeometric = previousProgramGeometric;
            TextureBinder.restoreDefaultTextureUnit();
        }

        applyShaderImageTextureBarrier();
        shadowFramebuffer.generateShadowColorMipmaps();
    }

    protected void generateWrittenMipmaps(ShaderProgramDirectives directives, Attachment[] flippedAttachments) {
        if (directives == null || flippedAttachments.length == 0 || directives.mipmappedBuffers().isEmpty()) {
            return;
        }
        EnumSet<Attachment> mipmappedWrittenAttachments = EnumSet.noneOf(Attachment.class);
        for (Attachment attachment : flippedAttachments) {
            if (directives.mipmappedBuffers().contains(attachment)) {
                mipmappedWrittenAttachments.add(attachment);
            }
        }
        if (!mipmappedWrittenAttachments.isEmpty()) {
            DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
            if (readBuffer != null) {
                readBuffer.generateMipmaps(mipmappedWrittenAttachments);
            }
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void setupFullscreenState() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL13.glActiveTexture(previousActiveTexture);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glColorMask(true, true, true, true);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, 0.0, 1.0);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
    }

    protected void restoreFullscreenState() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL13.glActiveTexture(previousActiveTexture);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
    }

    public void cleanup() {
        cleanupRuntimeState(true, true);
    }

    protected void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms) {
        cleanupRuntimeState(deleteActiveCompiledPrograms, deleteCachedCompiledPrograms, true);
    }

    protected void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms, boolean deleteVanillaTerrainRenderers) {
        resetPipelineState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), 0);

        pingPongManager.cleanup();
        if (shadowFramebuffer != null) {
            shadowFramebuffer.delete();
            shadowFramebuffer = null;
        }
        shadowMapPopulated = false;
        shadowMapUsable = false;
        shadowMapSparseForSampling = false;
        shadowMapCoverageStableFrames = 0;
        nothiriumShadowInvalidFrames = 0;
        nothiriumShadowSuppressedFrames = 0;
        nothiriumShadowVerticalHoldFrames = 0;
        resetShadowRenderCache();
        deleteCenterDepthSmoothTexture();
        deleteNoiseTexture();
        bloomRenderer.delete();
        customTextures.delete();
        if (deleteVanillaTerrainRenderers) {
            deleteCachedVanillaTerrainRenderers();
            vanillaViewFrustumStateStack.clear();
        }
        shaderImages.delete();
        shaderImages = ShaderImageSet.empty();
        shaderStorageBuffers.delete();
        shaderStorageBuffers = ShaderStorageBufferSet.empty();
        if (deleteActiveCompiledPrograms) {
            deleteComputePrograms();
            deleteFullscreenArrayPrograms();
            for (PipelineProgram program : programs.values()) {
                program.delete();
            }
        }
        if (deleteCachedCompiledPrograms) {
            deleteCachedCompiledPipelines();
        }
        programs.clear();
        programSet = null;
        shaderMap = null;
        shaderProperties = emptyShaderProperties();
        ShaderBlockLayerOverrides.clear();
        ShaderSamplerState.setBreaksAnisotropy(false);
        fullscreenProgramArrays.clear();
        fullscreenArrayPrograms.clear();
        computeProgramArrays.clear();
        shadowComputePrograms = List.of();
        finalComputePrograms = List.of();
        setupComputePending = false;
        syntheticLightCandidates.clear();
        resetColoredLightAudit();
        packDirectives = emptyShaderProperties().packDirectives();
        isPipelineActive = false;
        activePackName = "(internal)";
        activePass = null;
        activeShaderKey = null;
        activeCompiledPipelineCacheKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        clearDirectRecoveredWindowSource();
        deleteDirectPresentationSnapshot();
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        currentAlphaTestReference = 0.1f;
        centerDepthHalfLife = 1.0f;
        centerDepth = 1.0f;
        centerDepthSmooth = 1.0f;
        pipelineFrameId = 0L;
        nextWorldPassSerial = 0L;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        resetChunkFadeState(false);
        frameTimeCounter = 0.0f;
        currentFrameTime = 0.016f;
        frameTimeSmooth = 0.016f;
        frameTimeSmoothInitialized = false;
        resetEndFlashState();
        cameraShiftX = 0.0;
        cameraShiftZ = 0.0;
        temporalHistoryInitialized = false;
        temporalHistoryDimensionId = Integer.MIN_VALUE;
        previousTemporalYaw = 0.0f;
        previousTemporalPitch = 0.0f;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
        mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        pendingPersistentHistoryClear = false;
        pendingPersistentHistoryClearReason = "";
        terrainLayerCountFrame = Long.MIN_VALUE;
        terrainOpaqueLayerCount = 0;
        terrainOpaqueDrawCount = 0;
        sparseStartupPresentationHoldFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        positiveNothiriumTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumSparseMainRepairFrame = Long.MIN_VALUE;
        nothiriumSparseMainRepairLogs = 0;
        nothiriumSparseMainProviderDrawUntilFrame = Long.MIN_VALUE;
        nothiriumSparseMainProviderDrawLogs = 0;
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
        for (int i = 0; i < 3; i++) {
            cameraPosition[i] = 0.0f;
            previousCameraPosition[i] = 0.0f;
            cameraPositionUnshifted[i] = 0.0;
            previousCameraPositionUnshifted[i] = 0.0;
        }
        eyeBrightnessHalfLife = 3.0f;
        wetnessHalfLife = 600.0f;
        drynessHalfLife = 200.0f;
        eyeBrightnessSmooth[0] = 0.0f;
        eyeBrightnessSmooth[1] = 0.0f;
        eyeBrightnessSmoothInitialized = false;
        wetnessSmooth = 0.0f;
        wetnessSmoothInitialized = false;
        passStack.clear();
        worldPassBypassStack.clear();
        clientRenderFrameNanos = Long.MIN_VALUE;
        shaderlessCustomSkyBackingThisFrame = false;
        bloomLayerRenderedThisWorldFrame = false;
        shaderlessStyleBloomRenderedThisWorldFrame = false;
        shaderlessBloomRenderedThisWorldFrame = false;
        shaderlessWorldPassActive = false;
        shaderlessStyleBloomRenderedThisWorldPass = false;
        shaderlessBloomRenderedThisWorldPass = false;
        lastTerrainTransitionWorld = null;
        lastTerrainTransitionDimension = Integer.MIN_VALUE;
        lastTerrainTransitionMillis = 0L;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
        scheduleInactiveVanillaRecoveryFrame();
    }


    public boolean isActive() {
        return isPipelineActive;
    }

    public boolean shouldForceVanillaTerrainRenderer() {
        return isPipelineActive
                && (!shouldUseNothiriumMainTerrainBridge()
                || (ENABLE_SAFE_TERRAIN_FALLBACKS
                && (hardwareSafeVanillaTerrain
                || softVanillaTerrainRenderer
                // Safe fallbacks remain opt-in for the normal bridge path.
                )));
    }

    public boolean shouldApplyShaderBlockLayerOverrides() {
        return isPipelineActive
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                && !shouldSkipAllMainGbufferRendering()
                && hasUsableShaderTerrainProgram();
    }

    public boolean isRenderingBetterPortalsExternalWorldFrame() {
        return BetterPortalsCompat.isInstalled()
                && worldFrameActive
                && externalWorldFramebufferTarget != null;
    }

    public boolean isRenderingBetterPortalsNestedView() {
        return BetterPortalsCompat.isRenderingNestedView();
    }

    public boolean isRenderingBetterPortalsRenderPass() {
        return BetterPortalsCompat.isRenderingRenderPass();
    }

    public boolean shouldRenderBetterPortalsNestedViewWithShaders() {
        return isPipelineActive
                && BetterPortalsCompat.shouldRenderNestedViewWithShaders()
                && BetterPortalsCompat.currentShaderRenderPassFramebuffer() != null;
    }

    public boolean shouldBypassWorldPassRendering() {
        if (!worldPassBypassStack.isEmpty()) {
            return worldPassBypassStack.peek();
        }
        return computeShouldBypassWorldPassRendering();
    }

    public String describeBetterPortalsDiagnostics() {
        return "active=" + isPipelineActive
                + " shaderlessWorldPass=" + shaderlessWorldPassActive
                + " worldFrame=" + worldFrameActive
                + " frame=" + pipelineFrameId
                + " activePass=" + activePass
                + " phase=" + activePhase
                + " shadow=" + renderingShadowMap
                + " deferred=" + deferredPassesRenderedThisFrame
                + " passStack=" + passStack.size()
                + " bypassStack=" + worldPassBypassStack.size()
                + "/" + (worldPassBypassStack.isEmpty() ? "empty" : worldPassBypassStack.peek())
                + " bpPass=" + isRenderingBetterPortalsRenderPass()
                + " bpNested=" + isRenderingBetterPortalsNestedView()
                + " bpNestedShaders=" + shouldRenderBetterPortalsNestedViewWithShaders()
                + " externalTarget=" + describeFramebufferTarget(externalWorldFramebufferTarget)
                + " read=" + describeDeferredFramebuffer(pingPongManager.getReadBuffer());
    }

    protected boolean computeShouldBypassWorldPassRendering() {
        return shouldLeaveBetterPortalsRenderPassUntouched()
                || isRenderingBetterPortalsNestedView() && !shouldRenderBetterPortalsNestedViewWithShaders();
    }

    protected boolean shouldLeaveBetterPortalsRenderPassUntouched() {
        return BetterPortalsCompat.isInstalled()
                && isRenderingBetterPortalsRenderPass()
                && (!isPipelineActive
                || isRenderingBetterPortalsNestedView() && !shouldRenderBetterPortalsNestedViewWithShaders());
    }

    protected String describeDeferredFramebuffer(DeferredFramebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return framebuffer.getFramebufferId()
                + "("
                + framebuffer.getWidth()
                + "x"
                + framebuffer.getHeight()
                + ", color="
                + framebuffer.getReadTexture(Attachment.COLOR)
                + ", depth="
                + framebuffer.getDepthTexture()
                + ")";
    }

    protected String describeFramebufferTarget(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer)
                + "("
                + com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer)
                + "x"
                + com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer)
                + ")";
    }

    protected void logBetterPortalsPipeline(String stage) {
        logBetterPortalsPipeline(stage, "");
    }

    protected void logBetterPortalsPipeline(String stage, String detail) {
        if (!shouldLogBetterPortalsPipeline(stage)) {
            return;
        }
        if (betterPortalsPipelineLogs >= MAX_BETTER_PORTALS_PIPELINE_LOGS) {
            return;
        }
        betterPortalsPipelineLogs++;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World renderWorld = renderWorld(mc);
        World clientWorld = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        MainMod.LOGGER.info("[BetterPortalsPipeline] stage={} detail={} active={} worldFrame={} frame={} activePass={} phase={} renderWorld={} clientWorld={} nested={} renderPass={} nestedShaders={} externalTarget={} externalStatus={} read={} pack={} finalProgram={} compositePrograms={} finalComputes={} deferred={} setupComputePending={} gl={}",
                stage,
                detail,
                isPipelineActive,
                worldFrameActive,
                pipelineFrameId,
                activePass,
                activePhase,
                safeDimensionId(renderWorld),
                safeDimensionId(clientWorld),
                isRenderingBetterPortalsNestedView(),
                isRenderingBetterPortalsRenderPass(),
                shouldRenderBetterPortalsNestedViewWithShaders(),
                describeFramebufferTargetDetailed(externalWorldFramebufferTarget),
                framebufferStatus(externalWorldFramebufferTarget),
                describeDeferredFramebuffer(readBuffer),
                shaderPackDiagnostics(),
                describePipelineProgram(finalProgram),
                countCompositePrograms(),
                finalComputePrograms.size(),
                deferredPassesRenderedThisFrame,
                setupComputePending,
                describeCurrentGlTarget());
    }

    protected boolean shouldLogBetterPortalsPipeline(String stage) {
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        if (!isPipelineActive
                && isRenderingBetterPortalsRenderPass()
                && !isRenderingBetterPortalsNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && ("world-pass-begin".equals(stage) || "world-pass-finish".equals(stage))) {
            return false;
        }
        return isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || isBetterPortalsExternalWorldTarget()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected String describeFramebufferTargetDetailed(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer)
                + "("
                + com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(framebuffer)
                + "x"
                + com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(framebuffer)
                + ", tex="
                + com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferTexture(framebuffer)
                + ", texSize="
                + com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((framebuffer), 0, "field_147622_a", "framebufferTextureWidth")
                + "x"
                + com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((framebuffer), 0, "field_147620_b", "framebufferTextureHeight")
                + ", depth="
                + com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt((framebuffer), 0, "field_147624_h", "depthBuffer")
                + ")";
    }

    protected String framebufferStatus(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.isFramebufferEnabled()) {
            return "disabled";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer));
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            return status == GL30.GL_FRAMEBUFFER_COMPLETE ? "complete" : "0x" + Integer.toHexString(status);
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    protected String describePipelineProgram(PipelineProgram program) {
        if (program == null) {
            return "null";
        }
        return "enabled=" + program.enabled() + ", own=" + program.hasOwnProgram();
    }

    protected long countCompositePrograms() {
        return fullscreenArrayPrograms
                .getOrDefault(ProgramArrayId.COMPOSITE, List.of())
                .stream()
                .filter(FullscreenArrayProgram::hasProgram)
                .count();
    }

    protected String shaderPackDiagnostics() {
        return MainMod.getShaderPackManager() != null
                ? MainMod.getShaderPackManager().describeBetterPortalsPipelineState()
                : "shaderManager=null";
    }

    protected String describeCurrentGlTarget() {
        try {
            return "fbo=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                    + ", readFb=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawFb=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", drawBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_DRAW_BUFFER))
                    + ", readBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_READ_BUFFER))
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", viewport=" + currentViewportSummary();
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    protected String currentViewportSummary() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        return "["
                + viewportBuffer.get(0)
                + ","
                + viewportBuffer.get(1)
                + ","
                + viewportBuffer.get(2)
                + ","
                + viewportBuffer.get(3)
                + "]";
    }

    public boolean prepareRenderGlobalChunkUpdates(RenderGlobal renderGlobal) {
        if (renderGlobal == null) {
            return false;
        }

        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            return true;
        }

        boolean betterPortalsProtectedPass = BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingNestedView();
        if (!betterPortalsProtectedPass) {
            return true;
        }

        if (!(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return true;
        }

        World renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld == null) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            renderPassWorld = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        }
        if (renderPassWorld == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no active render-pass world");
            return false;
        }
        ensureVanillaTerrainRenderer(renderPassWorld, true);
        if (accessor.ausm$world() == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no RenderGlobal world after sync");
            return false;
        }
        if (accessor.ausm$world() != renderPassWorld) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates for mismatched render-pass world: renderGlobal={} pass={}",
                    safeDimensionId(accessor.ausm$world()),
                    safeDimensionId(renderPassWorld));
            return false;
        }

        return hasOnlyValidBetterPortalsChunkUpdates(accessor, renderPassWorld);
    }

    public boolean handleBetterPortalsChunkUpdateFailure(RenderGlobal renderGlobal, NullPointerException exception) {
        if (!BetterPortalsCompat.isInstalled()
                || !(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return false;
        }
        if (!isBetterPortalsChunkUpdateNullWorldFailure(exception)
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && accessor.ausm$world() != null
                && !hasInvalidBetterPortalsChunkUpdate(accessor)) {
            return false;
        }

        clearRenderGlobalChunkUpdates(accessor);
        if (!betterPortalsChunkUpdateWarningLogged) {
            betterPortalsChunkUpdateWarningLogged = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Dropped stale nested chunk update after Better Portals exposed a RenderChunk with no world", exception);
        }
        return true;
    }

    public World betterPortalsRenderChunkFallbackWorld() {
        if (!BetterPortalsCompat.isInstalled()) {
            return null;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }

        if (!BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return null;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
    }

    protected boolean isBetterPortalsChunkUpdateNullWorldFailure(NullPointerException exception) {
        if (exception == null) {
            return false;
        }
        for (StackTraceElement frame : exception.getStackTrace()) {
            String className = frame.getClassName();
            if ("net.minecraft.world.ChunkCache".equals(className)
                    || "net.minecraft.client.renderer.chunk.RenderChunk".equals(className)
                    || "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher".equals(className)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasOnlyValidBetterPortalsChunkUpdates(RenderGlobalAccessor accessor, World allowedWorld) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (!isValidBetterPortalsChunkUpdate(chunk, allowedWorld)) {
                MainMod.LOGGER.debug("[BetterPortalsCompat] Deferred nested chunk updates because the queue contains work for another world");
                return false;
            }
        }
        return true;
    }

    protected boolean isValidBetterPortalsChunkUpdate(RenderChunk chunk, World allowedWorld) {
        if (chunk == null) {
            return false;
        }

        World chunkWorld = renderChunkWorld(chunk);
        if (chunkWorld == null) {
            return assignRenderChunkWorld(chunk, allowedWorld);
        }
        return chunkWorld == allowedWorld;
    }

    protected boolean hasInvalidBetterPortalsChunkUpdate(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (chunk == null || renderChunkWorld(chunk) == null) {
                return true;
            }
        }
        return false;
    }

    protected World renderChunkWorld(RenderChunk chunk) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkWorld(chunk);
    }

    protected boolean assignRenderChunkWorld(RenderChunk chunk, World world) {
        if (chunk == null || world == null) {
            return false;
        }
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setField(chunk, world, "field_178588_d", "world");
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    protected void clearRenderGlobalChunkUpdates(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate != null && !chunksToUpdate.isEmpty()) {
            chunksToUpdate.clear();
        }
    }

    protected int safeDimensionId(World world) {
        return world != null && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)) : Integer.MIN_VALUE;
    }

    protected boolean isOverworldShaderEnvironment(World world) {
        int dimensionId = safeDimensionId(world);
        return dimensionId != Integer.MIN_VALUE
                && dimensionId != -1
                && dimensionId != 1;
    }

    protected String describeWorld(World world) {
        if (world == null) {
            return "null";
        }
        return "dim=" + safeDimensionId(world) + ", id=" + System.identityHashCode(world);
    }

    public void prepareBypassedWorldPassRendering() {
        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            untouchedBetterPortalsVanillaRendererStack.push(prepareUntouchedBetterPortalsRenderPass());
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare untouched-bp-pass");
            return;
        }
        untouchedBetterPortalsVanillaRendererStack.push(false);
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !shouldRenderBetterPortalsNestedViewWithShaders();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare nested=" + nestedBetterPortalsView
                + " vanillaRenderer=" + useNestedVanillaRenderer);
        if (!isPipelineActive && !nestedBetterPortalsView) {
            prepareInactiveVanillaFrame();
        }
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null);
        if (useNestedVanillaRenderer || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            ensureVanillaTerrainRenderer(targetWorld, true);
        } else if (!nestedBetterPortalsView) {
            ensureVanillaTerrainRenderer(targetWorld);
        }

        restoreVanillaWorldPassState(!nestedBetterPortalsView, !nestedBetterPortalsView);
    }

    public void finishBypassedWorldPassRendering() {
        if (shouldLeaveBetterPortalsRenderPassUntouched()) {
            if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()
                    && untouchedBetterPortalsVanillaRendererStack.pop()) {
                popVanillaTerrainRendererState();
            }
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish untouched-bp-pass");
            return;
        }
        if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()) {
            untouchedBetterPortalsVanillaRendererStack.pop();
        }
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-before");
        restoreVanillaWorldPassState(false, !nestedBetterPortalsView);
        popVanillaTerrainRendererState();
        shaderlessWorldPassActive = false;
        restoreActiveWorldPassAfterExternalShader();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-after");
    }

    protected boolean prepareUntouchedBetterPortalsRenderPass() {
        boolean nestedBetterPortalsView = isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !shouldRenderBetterPortalsNestedViewWithShaders();
        boolean mustEnsureVanillaRenderer = useNestedVanillaRenderer || NothiriumBypass.shouldBypass();
        if (!mustEnsureVanillaRenderer) {
            return false;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null);
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        ensureVanillaTerrainRenderer(targetWorld, useNestedVanillaRenderer);
        return useNestedVanillaRenderer;
    }

    protected void renderNativeBloomLayerIfNeeded() {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()) {
            return;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(currentWorldPartialTicks, currentWorldPass);
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        renderAusmBloomLayer(
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc),
                currentWorldPartialTicks,
                currentWorldPass,
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc)
        );
    }

    public void renderNativeAusmBloomLayerFromWorldPass(float partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()) {
            return;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(partialTicks, pass);
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        renderAusmBloomLayer(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), partialTicks, pass, com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc));
    }

    public int renderShaderlessVisibleBloomLayerFromWorldPass(float partialTicks, int pass) {
        boolean nativeHook = AusmBloomLayer.shouldUseNativeHook();
        boolean nestedBetterPortalsPass = isRenderingBetterPortalsRenderPass() && isRenderingBetterPortalsNestedView();
        if (isPipelineActive
                || nativeHook
                || bloomLayerRenderedThisWorldPass
                || renderingGuiScreen()
                || renderingShadowMap
                || nestedBetterPortalsPass) {
            return 0;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null || bloomLayer == null) {
            return 0;
        }

        Framebuffer bloomTarget = isRenderingBetterPortalsRenderPass()
                ? BetterPortalsCompat.currentRenderPassFramebuffer()
                : null;
        if (bloomTarget == null) {
            bloomTarget = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        }

        int bloomRendered = 0;
        if (bloomTarget != null) {
            bloomRendered = bloomRenderer.renderBloomLayer(
                    com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc),
                    partialTicks,
                    pass,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc),
                    null,
                    bloomTarget,
                    false
            );
        }

        int rendered = bloomRendered;
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        }

        return rendered;
    }

    public int renderEmissiveBloomExtractionFromWorldPass(float partialTicks, int pass) {
        return 0;
    }

    protected void logVisibleBloomDiag(String stage, int pass, int rendered, String detail) {
        // Diagnostic disabled.
    }

    public int renderAusmBloomLayer(RenderGlobal renderGlobal, double partialTicks, int pass, Entity entity) {
        if (renderingGuiScreen() || renderingShadowMap) {
            return 0;
        }
        if (!AusmBloomLayer.shouldUseNativeHook()) {
            return 0;
        }
        if (isRenderingBetterPortalsRenderPass()) {
            requestDeferredNativeBloom(partialTicks, pass);
            return 0;
        }
        if (bloomLayerRenderedThisWorldPass) {
            return 0;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return 0;
        }

        pendingDeferredNativeBloom = false;
        Entity renderEntity = entity != null ? entity : com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        Framebuffer minecraftTarget = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        DeferredFramebuffer pipelineDepthSource = isPipelineActive && worldFrameActive && pingPongManager.isInitialized()
                ? pingPongManager.getReadBuffer()
                : null;
        boolean deferComposite = pipelineDepthSource != null;

        int rendered = bloomRenderer.renderBloomLayer(
                renderGlobal,
                partialTicks,
                pass,
                renderEntity,
                pipelineDepthSource,
                minecraftTarget,
                deferComposite
        );
        if (rendered > 0) {
            bloomLayerRenderedThisWorldPass = true;
            bloomLayerRenderedThisWorldFrame = true;
        }
        recordBloomRenderResult(rendered);
        return rendered;
    }

    /**
     * Emits Nothirium-owned mesh data without invoking Nothirium's renderer.
     * AUSM keeps the active program, framebuffer, and fixed-function state.
     * A negative result means the caller should use vanilla RenderGlobal data.
     */
    public int renderAusmOwnedNothiriumBloomGeometry(double partialTicks, Entity viewEntity) {
        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null
                || viewEntity == null
                || !isNothiriumLoaded()
                || CeleritasCompat.installed()
                || !NothiriumShadowRenderer.isAvailable()) {
            return -1;
        }
        if (AusmBloomLayer.consumeNothiriumRendererRecreateRequest()) {
            boolean recreated = NothiriumBypass.recreateRenderer();
            MainMod.LOGGER.info("[AUSMBloom] Recreated Nothirium mesh backend for BLOOM pass: {}", recreated);
            return 0;
        }

        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), (float) partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), (float) partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), (float) partialTicks);
        nothiriumShadowRenderer.drainUploads();

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        shaderlessWorldPassActive = true;
        activePhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
        try {
            return positiveCount(nothiriumShadowRenderer.renderVisibleLayerAllowingVanillaStride(
                    bloomLayer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    nothiriumFallbackBlockEntityId(bloomLayer),
                    nothiriumFallbackRenderType(bloomLayer)
            ));
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    protected void requestDeferredNativeBloom(double partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        pendingDeferredNativeBloom = true;
        pendingDeferredBloomPartialTicks = partialTicks;
        pendingDeferredBloomPass = pass;
    }

    protected void renderDeferredNativeBloomIfNeeded() {
        if (!pendingDeferredNativeBloom
                || bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()
                || renderingShadowMap) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        double partialTicks = pendingDeferredBloomPartialTicks;
        int pass = pendingDeferredBloomPass;
        pendingDeferredNativeBloom = false;
        renderAusmBloomLayer(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), partialTicks, pass, com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc));
    }

    protected void recordBloomRenderResult(int rendered) {
        if (rendered > 0) {
            bloomZeroGeometryFrames = 0;
            return;
        }
        if (isPipelineActive) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
            return;
        }
        if (AusmBloomLayer.shouldUseNativeHook()) {
            bloomZeroGeometryFrames = 0;
            bloomZeroGeometryRefreshCooldown = 0;
            return;
        }
        if (!bloomRenderer.hasBloomResources()) {
            return;
        }

        bloomZeroGeometryFrames++;
        if (bloomZeroGeometryFrames < 20 || bloomZeroGeometryRefreshCooldown > 0) {
            return;
        }

        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 120;
        scheduleBloomTerrainRefresh("zero-bloom-geometry");
    }

    protected void renderPostWorldBloom(Framebuffer target, boolean externalTarget) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (target == null
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null
                || externalTarget
                || renderingGuiScreen()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        renderDeferredNativeBloomIfNeeded();
        if (bloomLayerRenderedThisWorldPass || bloomLayerRenderedThisWorldFrame) {
            DeferredFramebuffer handMaskSource = isPipelineActive && pingPongManager.isInitialized()
                    ? pingPongManager.getReadBuffer()
                    : null;
            int preHandDepthTexture = handMaskSource != null
                    ? handMaskSource.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT)
                    : 0;
            int postHandDepthTexture = handMaskSource != null
                    ? handMaskSource.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT)
                    : 0;
            bloomRenderer.renderPostWorldBloom(target, preHandDepthTexture, postHandDepthTexture);
            return;
        }
        bloomRenderer.renderPostWorldBloom(target);
    }

    public void renderDepthTestedOwnedSkyRepair(Framebuffer target, Minecraft mc) {
        WorldClient world = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        if (target == null
                || mc == null
                || world == null
                || !isPipelineActive
                || !shouldUseOwnedSkyOverrideWorld(world)
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        boolean uiRepair = com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc);
        if (!isSimpleVoidWorld(world) && !uiRepair) {
            return;
        }
        logOwnedSkyBackingProbe("presentation-depth-repair", mc);

        Vec3d skyColor = null;
        try {
            skyColor = com.l.ausm.impl.util.MinecraftReflectionCompat.call(world, net.minecraft.util.math.Vec3d.class, null,
                    new String[] {"func_72833_a", "getSkyColor"},
                    new Class<?>[] {net.minecraft.entity.Entity.class, float.class},
                    com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc),
                    currentWorldPartialTicks);
        } catch (RuntimeException | LinkageError ignored) {
            skyColor = null;
        }
        drawOwnedSkyDepthRepairGradient(
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target),
                skyColor,
                mc,
                target);
    }

    public void renderShaderlessBloomBeforeGui() {
        if (disableShaderlessPreGuiHooks) {
            return;
        }
        if (isPipelineActive) {
            return;
        }
        if (shaderlessBloomRenderedThisWorldPass) {
            logShaderlessBloomHook("skip already-rendered");
            return;
        }
        if (externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()
                || renderingGuiScreen()) {
            logShaderlessBloomHook("skip state external=" + describeFramebufferTarget(externalWorldFramebufferTarget)
                    + " nested=" + isRenderingBetterPortalsNestedView()
                    + " renderPass=" + isRenderingBetterPortalsRenderPass()
                    + " gui=" + renderingGuiScreen());
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            logShaderlessBloomHook("skip missing-minecraft-state mc=" + (mc != null)
                    + " world=" + (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null)
                    + " entity=" + (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) != null)
                    + " framebuffer=" + (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) != null));
            return;
        }
        boolean hasBloomResources = bloomRenderer.hasBloomResources();
        boolean hasShaderlessBloomMetadata = hasShaderlessBloomMetadata();
        boolean framedBloomBootstrap = false;
        refreshShaderlessBloomVertexFormatIfNeeded(hasBloomResources);

        // Ordinary terrain is never re-rendered as bloom. Lumenized owns the
        // resource-pack BLOOM layer and is handled by the native layer path.
        boolean shouldExtractShaderlessBloom = false;
        boolean nativeBloom = AusmBloomLayer.shouldUseShaderlessNativeHook();
        Entity renderViewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        logShaderlessBloomHook("render target=" + describeFramebufferTarget(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc))
                + " bloomResources=" + hasBloomResources
                + " metadata=" + hasShaderlessBloomMetadata
                + " framedBootstrap=" + framedBloomBootstrap
                + " nativeBloom=" + nativeBloom
                + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                + " renderPass=" + isRenderingBetterPortalsRenderPass());
        if (!shouldExtractShaderlessBloom && !nativeBloom) {
            shaderlessBloomRenderedThisWorldPass = true;
            shaderlessBloomRenderedThisWorldFrame = true;
            sealShaderlessWorldFramebufferAlpha("no-bloom-before-gui");
            restoreShaderlessBloomExitState(mc);
            return;
        }
        renderNativeBloomLayerIfNeeded();
        boolean shaderlessExtractRendered = false;
        if (!nativeBloom && shouldExtractShaderlessBloom) {
            boolean previousShaderlessBloomExtractionActive = shaderlessBloomExtractionActive;
            boolean previousShaderlessBloomExtractionBootstrapActive = shaderlessBloomExtractionBootstrapActive;
            shaderlessBloomExtractionActive = true;
            shaderlessBloomExtractionBootstrapActive = framedBloomBootstrap && !hasShaderlessBloomMetadata;
            try {
                shaderlessExtractRendered = bloomRenderer.renderShaderlessEmissiveTerrainBloom(
                        com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc),
                        () -> renderShaderlessBloomExtractionGeometry(mc, renderViewEntity, false)
                );
            } finally {
                shaderlessBloomExtractionActive = previousShaderlessBloomExtractionActive;
                shaderlessBloomExtractionBootstrapActive = previousShaderlessBloomExtractionBootstrapActive;
            }
            logShaderlessBloomHook("extract-rendered=" + shaderlessExtractRendered
                    + " bloomLayerRendered=" + bloomLayerRenderedThisWorldPass
                    + " bypass=" + NothiriumBypass.shouldBypass());
        }
        if (shaderlessExtractRendered) {
            bloomRenderer.renderPostWorldBloom(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc));
            logShaderlessBloomHook("extract-composited");
        } else {
            renderPostWorldBloom(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc), false);
        }
        shaderlessBloomRenderedThisWorldPass = true;
        shaderlessBloomRenderedThisWorldFrame = true;
        sealShaderlessWorldFramebufferAlpha("post-bloom-before-gui");
        restoreShaderlessBloomExitState(mc);
    }

    protected boolean shouldRenderShaderlessCustomSkyBacking(Minecraft mc) {
        if (clientRenderFrameNanos != Long.MIN_VALUE) {
            return shaderlessCustomSkyBackingThisFrame;
        }
        return shouldRenderShaderlessCustomSkyBackingNow(mc);
    }

    protected boolean shouldRenderShaderlessCustomSkyBackingNow(Minecraft mc) {
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        return shouldUseShaderlessOwnedSky(mc) && isSimpleVoidWorld(world);
    }

    protected Vec3d desaturateSkyColor(Vec3d color, double saturation) {
        return PipelineSkyColorMath.desaturate(color, saturation);
    }

    protected Vec3d mixSkyColors(Vec3d from, Vec3d to, double factor) {
        return PipelineSkyColorMath.mix(from, to, factor);
    }

    protected double clamp01(double value) {
        return PipelineSkyColorMath.clamp01(value);
    }

    protected static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    protected static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }

    public void renderOwnedSkyBackingBeforeSky(float partialTicks) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        boolean external = externalWorldFramebufferTarget != null;
        boolean bpNested = isRenderingBetterPortalsNestedView();
        boolean bpPass = isRenderingBetterPortalsRenderPass();
        boolean hasView = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) != null;
        boolean hasTarget = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) != null;
        boolean owned = shouldUseOwnedSkyOverrideWorld(world);
        // Sky rendering can run before the world-frame cache is refreshed,
        // especially after F1 or GUI state changes. Evaluate this route from
        // the current world so a stale cached false cannot suppress both the
        // owned backing and Botania's intentionally disabled base dome.
        boolean shaderless = shouldRenderShaderlessCustomSkyBackingNow(mc);
        boolean shadered = shouldRenderShaderedOwnedSkyBacking(mc);
        logOwnedSkyBackingDecisionProbe("before-sky", mc, world, external, bpNested, bpPass,
                hasView, hasTarget, owned, shaderless, shadered);
        if (externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        if (mc == null
                || world == null
                || !hasView
                || !hasTarget
                || !owned
                || (!shaderless && !shadered)) {
            return;
        }

        Vec3d skyColor = null;
        try {
            skyColor = com.l.ausm.impl.util.MinecraftReflectionCompat.call((com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)), net.minecraft.util.math.Vec3d.class, null, new String[] {"func_72833_a", "getSkyColor"},
                new Class<?>[] {net.minecraft.entity.Entity.class, float.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc)), (partialTicks));
        } catch (RuntimeException | LinkageError ignored) {
            skyColor = null;
        }

        try {
            if (shaderless) {
                bindMinecraftFramebufferForGui(mc);
                logOwnedSkyBackingProbe("shaderless", mc);
                drawOwnedSkyBackingGradient(
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                        skyColor,
                        mc);
            } else {
                logOwnedSkyBackingProbe("shadered", mc);
                drawOwnedSkyBackingGradient(
                        Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc)),
                        Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc)),
                        skyColor,
                        mc);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep sky rendering on vanilla's path if the optional backing pass fails.
        } finally {
            if (shaderless) {
                restoreShaderlessBloomExitState(mc);
            }
        }
    }

    public void renderCompleteOwnedSkyOverride(float partialTicks, int pass) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || !shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        // This is intentionally the only sky draw for AUSM-owned worlds. The
        // normal RenderGlobal path is cancelled by RenderSkyMixin, so vanilla
        // lower sky, sun, moon, stars, custom sky renderers, and skybox lists
        // cannot write into the world or GUI presentation buffers.
        renderOwnedSkyBackingBeforeSky(partialTicks);
        WorldClient world = com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        renderCompleteOwnedVoidSkyDetails(partialTicks, world, mc);
        if (!isPipelineActive) {
            restoreShaderlessBloomExitState(mc);
        }
    }

    public void renderShaderlessBotaniaSkyBacking(float partialTicks, WorldClient world, Minecraft mc) {
        if (isPipelineActive || mc == null || world == null || !isSimpleVoidWorld(world)) {
            return;
        }
        Vec3d skyColor = com.l.ausm.impl.util.MinecraftReflectionCompat.call(
                world,
                Vec3d.class,
                null,
                new String[] {"func_72833_a", "getSkyColor"},
                new Class<?>[] {net.minecraft.entity.Entity.class, float.class},
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc),
                partialTicks
        );
        if (skyColor == null || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }
        bindMinecraftFramebufferForGui(mc);
        drawOwnedSkyBackingGradient(
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                skyColor,
                mc
        );
    }

    public void renderShaderedOwnedVoidSkyBase(WorldClient world, Minecraft mc) {
        if (!isPipelineActive
                || mc == null
                || world == null
                || !isSimpleVoidWorld(world)
                || !isCustomVoidWorldSkyEnabled(world)
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_ALWAYS);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(-1.0D, 1.0D, -1.0D, 1.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();

            // Keep the active GBUFFERS_SKYBASIC program. Its fragment stage
            // reconstructs the view ray from gl_FragCoord, so one clip-space
            // quad provides a continuous upper and lower dome without replacing
            // Botania's textured details or Astral's compatibility wrapper.
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-1.0F, -1.0F, 0.0F);
            GL11.glVertex3f(1.0F, -1.0F, 0.0F);
            GL11.glVertex3f(1.0F, 1.0F, 0.0F);
            GL11.glVertex3f(-1.0F, 1.0F, 0.0F);
            GL11.glEnd();
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
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousDepthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void logOwnedSkyBackingDecisionProbe(String route, Minecraft mc, World world, boolean external,
                                                   boolean bpNested, boolean bpPass, boolean hasView,
                                                   boolean hasTarget, boolean owned, boolean shaderless,
                                                   boolean shadered) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        boolean hideGui = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc));
        if (!hideGui || ownedSkyBackingDecisionProbeLogs++ >= 36) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMSkyBackingDecisionProbe] route={} active={} world={} dim={} simpleVoid={} customVoid={} owned={} shaderless={} shadered={} external={} bpNested={} bpPass={} hasView={} hasTarget={} screen={} hideGui={} paused={} drawFbo={} readFbo={} mcTarget={}",
                route,
                isPipelineActive,
                world == null ? "null" : world.getClass().getName(),
                world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) == null
                        ? Integer.MIN_VALUE
                        : com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(
                        com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)),
                isSimpleVoidWorld(world),
                isCustomVoidWorldSkyEnabled(world),
                owned,
                shaderless,
                shadered,
                external,
                bpNested,
                bpPass,
                hasView,
                hasTarget,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null,
                hideGui,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                describeFramebufferTargetDetailed(mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc))
        );
    }

    protected void logOwnedSkyBackingProbe(String route, Minecraft mc) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        if (mc == null
                || !com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc))
                || ownedSkyBackingProbeLogs++ >= 36) {
            return;
        }
        Framebuffer framebuffer = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        MainMod.LOGGER.info(
                "[AUSMSkyBackingProbe] route={} active={} simpleVoid={} owned={} screen={} hideGui={} paused={} drawFbo={} readFbo={} mcTarget={} display={}x{}",
                route,
                isPipelineActive,
                isSimpleVoidWorld(renderWorld(mc)),
                shouldUseOwnedSkyOverrideWorld(renderWorld(mc)),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                describeFramebufferTargetDetailed(framebuffer),
                mc == null ? -1 : com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc),
                mc == null ? -1 : com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc)
        );
    }

    public void renderShaderlessGuiCustomSkyBackingBeforeSky(float partialTicks) {
        renderOwnedSkyBackingBeforeSky(partialTicks);
    }

    protected boolean shouldRenderShaderedOwnedSkyBacking(Minecraft mc) {
        return false;
    }

    protected boolean shouldUseShaderedF1LowerSkyRepair(Minecraft mc, World world) {
        return false;
    }

    protected void drawOwnedSkyBackingGradient(int width, int height, Vec3d skyColor, Minecraft mc) {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean previousScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glViewport(0, 0, Math.max(1, width), Math.max(1, height));
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableTexture2D();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179106_n", "disableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
            // Keep the backing gradient depth-tested. It is drawn before normal
            // terrain, but this also protects against late/nested sky calls
            // painting Void bands over already-rendered geometry.
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, Math.max(1, width), 0.0D, Math.max(1, height), -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();
            GL11.glShadeModel(GL11.GL_SMOOTH);

            width = Math.max(1, width);
            height = Math.max(1, height);
            float[] bottom = officialOwnedSkyBackingColorAt(0.0D, height, skyColor, mc);
            float[] top = officialOwnedSkyBackingColorAt(height, height, skyColor, mc);
            // This backing is a render-boundary primitive, not terrain. Keep it
            // outside BufferBuilder so terrain vertex expansion and stale
            // per-thread compile context cannot suppress or reinterpret it.
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(bottom[0], bottom[1], bottom[2], 1.0F);
            GL11.glVertex3d(0.0D, 0.0D, -1.0D);
            GL11.glVertex3d(width, 0.0D, -1.0D);
            GL11.glColor4f(top[0], top[1], top[2], 1.0F);
            GL11.glVertex3d(width, height, -1.0D);
            GL11.glVertex3d(0.0D, height, -1.0D);
            GL11.glEnd();
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
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousFog) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179127_m", "enableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179106_n", "disableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);;
            }
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            if (previousDepthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
        }
    }

    protected void drawOwnedSkyDepthRepairGradient(int width, int height, Vec3d skyColor, Minecraft mc, Framebuffer target) {
        if (target == null || width <= 0 || height <= 0) {
            return;
        }
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0
                    ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glViewport(0, 0, width, height);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableTexture2D();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class,
                    new String[] {"func_179106_n", "disableFog"},
                    com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, Math.max(1, width), 0.0D, Math.max(1, height), -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;
            GL11.glLoadIdentity();
            GL11.glShadeModel(GL11.GL_SMOOTH);

            float[] bottom = officialOwnedSkyBackingColorAt(0.0D, height, skyColor, mc);
            float[] top = officialOwnedSkyBackingColorAt(height, height, skyColor, mc);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(bottom[0], bottom[1], bottom[2], 1.0F);
            GL11.glVertex3d(0.0D, 0.0D, -1.0D);
            GL11.glVertex3d(width, 0.0D, -1.0D);
            GL11.glColor4f(top[0], top[1], top[2], 1.0F);
            GL11.glVertex3d(width, height, -1.0D);
            GL11.glVertex3d(0.0D, height, -1.0D);
            GL11.glEnd();
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
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousFog) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class,
                        new String[] {"func_179127_m", "enableFog"},
                        com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class,
                        new String[] {"func_179106_n", "disableFog"},
                        com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            }
            if (previousDepthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            GL11.glDepthFunc(previousDepthFunc);
        }
    }

    protected void renderShaderlessBotaniaVoidDetails(float partialTicks, WorldClient world, Minecraft mc) {
        TextureManager textureManager = com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null) {
            return;
        }

        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int previousBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean previousTexture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousFog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean previousLighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean pushed = false;
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179106_n", "disableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushed = true;

            float rainFade = 1.0F - clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, partialTicks));
            float celestial = com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks);
            float dayDistance = celestial > 0.5F ? 1.0F - celestial : celestial;
            float nightAlpha = clamp01((dayDistance - 0.30F) * 5.0F) * rainFade;
            float ornamentAlpha = 1.0F;
            long time = com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world);

            drawBotaniaVoidPlanets(textureManager, time, partialTicks, ornamentAlpha);
            drawBotaniaVoidSkyBands(textureManager, time, partialTicks, ornamentAlpha);
            drawBotaniaVoidRainbow(textureManager, time, partialTicks, rainFade, celestial);
        } finally {
            if (pushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
            GL11.glShadeModel(previousShadeModel);
            if (previousTexture2D) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableTexture2D();
            }
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(previousBlendSrcRgb, previousBlendDstRgb, previousBlendSrcAlpha, previousBlendDstAlpha);
            if (previousAlpha) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (previousDepthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            if (previousLighting) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179145_e", "enableLighting"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
            }
            if (previousFog) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179127_m", "enableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179106_n", "disableFog"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
            }
            GL11.glDepthFunc(previousDepthFunc);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidPlanets(TextureManager textureManager, long time, float partialTicks, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        GL11.glPushMatrix();
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
            GL11.glRotatef(90.0F, 0.5F, 0.5F, 0.0F);
            float size = 20.0F;
            for (int i = 0; i < BOTANIA_VOID_PLANET_TEXTURES.length; i++) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_PLANET_TEXTURES[i]);
                drawBotaniaVoidBillboard(size);
                switch (i) {
                    case 0:
                        GL11.glRotatef(70.0F, 1.0F, 0.0F, 0.0F);
                        size = 12.0F;
                        break;
                    case 1:
                        GL11.glRotatef(120.0F, 0.0F, 0.0F, 1.0F);
                        size = 15.0F;
                        break;
                    case 2:
                        GL11.glRotatef(80.0F, 1.0F, 0.0F, 1.0F);
                        size = 25.0F;
                        break;
                    case 3:
                        GL11.glRotatef(100.0F, 0.0F, 0.0F, 1.0F);
                        size = 10.0F;
                        break;
                    case 4:
                        GL11.glRotatef(-60.0F, 1.0F, 0.0F, 0.5F);
                        size = 40.0F;
                        break;
                    default:
                        GL11.glRotatef(((time + (long) (partialTicks * 20.0F)) % 360L) * 0.02F, 0.0F, 1.0F, 0.0F);
                        break;
                }
            }
        } finally {
            GL11.glPopMatrix();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidSkyBands(TextureManager textureManager, long time, float partialTicks, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_SKYBOX_TEXTURE);
        GL11.glPushMatrix();
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, clamp01(alpha));
            GL11.glTranslatef(0.0F, -1.0F, 0.0F);
            GL11.glRotatef(220.0F, 1.0F, 0.0F, 0.0F);
            drawBotaniaRibbon((time + partialTicks) * 0.16F, 20.0F, 2.0F, 90);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 0.4F, 0.4F, clamp01(alpha * 0.75F));
            GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
            drawBotaniaRibbon((time + partialTicks) * 0.04F, 20.0F, 2.0F, 90);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(0.4F, 1.0F, 0.7F, clamp01(alpha * 0.75F));
            GL11.glRotatef(50.0F, 1.0F, 0.0F, 0.0F);
            drawBotaniaRibbon((time + partialTicks) * 0.40F, 20.0F, 2.0F, 90);
        } finally {
            GL11.glPopMatrix();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidRainbow(TextureManager textureManager, long time, float partialTicks, float rainFade, float celestial) {
        float daySide = celestial > 0.25F ? 1.0F - celestial : celestial;
        float alpha = clamp01((0.25F - Math.min(0.25F, daySide)) * 4.0F) * rainFade * 0.35F;
        if (alpha <= 0.01F) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, BOTANIA_VOID_RAINBOW_TEXTURE);
        GL11.glPushMatrix();
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, alpha);
            GL11.glRotatef(35.0F + ((time + partialTicks) % 24000.0F) * 0.002F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslatef(0.0F, 18.0F, 0.0F);
            drawBotaniaRibbon((time + partialTicks) * 0.02F, 30.0F, 2.5F, 96);
        } finally {
            GL11.glPopMatrix();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    protected void drawBotaniaVoidBillboard(float size) {
        Tessellator tessellator = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUADS, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.client.renderer.vertex.DefaultVertexFormats.class, net.minecraft.client.renderer.vertex.VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, -size, 100.0D, -size, 0.0D, 0.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, size, 100.0D, -size, 1.0D, 0.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, size, 100.0D, size, 1.0D, 1.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, -size, 100.0D, size, 0.0D, 1.0D);
        com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    protected void drawBotaniaRibbon(float scrollDegrees, float radius, float height, int segments) {
        Tessellator tessellator = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferBegin(buffer, GL11.GL_QUAD_STRIP, com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.client.renderer.vertex.DefaultVertexFormats.class, net.minecraft.client.renderer.vertex.VertexFormat.class, null, "field_181707_g", "POSITION_TEX"));
        double scroll = scrollDegrees / 360.0D;
        for (int i = 0; i <= segments; i++) {
            double angle = ((i / (double) segments) * Math.PI * 2.0D);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double wave = Math.sin(angle * 5.0D) * 0.75D;
            double u = (i / (double) segments) + scroll;
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, x, wave, z, u, 1.0D);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosTexEnd(buffer, x, wave + height, z, u, 0.0D);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    protected float[] officialOwnedSkyBackingColorAt(double y, int height, Vec3d skyColor, Minecraft mc) {
        double uvY = clamp01(y / Math.max(1.0D, height));
        return vec3Color(officialOwnedSkyBackingColor(uvY, skyColor, mc));
    }

    protected Vec3d officialOwnedSkyBackingColor(double uvY, Vec3d skyColor, Minecraft mc) {
        if (!isSimpleVoidWorld(renderWorld(mc))) {
            return dimensionOwnedSkyBackingColor(uvY, skyColor, mc);
        }

        double horizonY = 0.50D;
        double softness = 0.70D;
        Vec3d source = skyColor != null ? skyColor : new Vec3d(0.0D, 0.0D, 0.0D);
        Vec3d dayTop = maxSkyColor(source, new Vec3d(0.45D, 0.62D, 0.86D));
        Vec3d dayHorizon = mixSkyColors(desaturateSkyColor(dayTop, 0.35D), new Vec3d(0.84D, 0.90D, 1.0D), 0.62D);
        World world = renderWorld(mc);
        double celestial = world == null ? 0.25D
                : com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world,
                mc == null ? 0.0F : com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc));
        // This world's celestial angle is zero at midday and one-half at
        // midnight. Use cosine rather than the standard quarter-shifted sine.
        double sunHeight = Math.cos(celestial * Math.PI * 2.0D);
        double day = smoothstep(-0.12D, 0.20D, sunHeight);
        double night = 1.0D - smoothstep(-0.30D, 0.08D, sunHeight);
        double twilight = clamp01(1.0D - day - night);
        double sunsetSide = clamp01((1.0D + Math.sin(celestial * Math.PI * 2.0D)) * 0.5D);

        Vec3d nightTop = new Vec3d(0.012D, 0.021D, 0.075D);
        Vec3d nightHorizon = new Vec3d(0.042D, 0.058D, 0.125D);
        Vec3d sunrise = new Vec3d(0.96D, 0.47D, 0.32D);
        Vec3d sunset = new Vec3d(0.62D, 0.16D, 0.30D);
        Vec3d twilightColor = mixSkyColors(sunrise, sunset, sunsetSide);

        Vec3d topColor = mixSkyColors(mixSkyColors(nightTop, twilightColor, twilight), dayTop, day);
        Vec3d lowerColor = mixSkyColors(mixSkyColors(nightHorizon, twilightColor, twilight), dayHorizon, day);

        double band = (uvY - (horizonY - softness)) / (softness * 2.0D);
        Vec3d result = mixSkyColors(lowerColor, topColor, smootherstep(band));

        double rainAmount = officialSkyRainFactor(mc);
        Vec3d rainyDome = mixSkyColors(lowerColor, topColor, 0.48D);
        result = mixSkyColors(result, rainyDome, clamp01(rainAmount * 0.75D));

        Vec3d rainColor = new Vec3d(
                Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(result), 0.17D),
                Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(result), 0.185D),
                Math.min(com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(result), 0.235D)
        );
        return mixSkyColors(result, rainColor, clamp01(rainAmount * 0.50D));
    }

    protected double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / Math.max(1.0E-6D, edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    protected Vec3d dimensionOwnedSkyBackingColor(double uvY, Vec3d skyColor, Minecraft mc) {
        double horizonY = 0.50D;
        double softness = 0.70D;
        Vec3d source = skyColor != null ? skyColor : new Vec3d(0.0D, 0.0D, 0.0D);
        Vec3d topColor = scaleSkyColor(source, 1.08D);
        Vec3d horizonColor = mixSkyColors(source, desaturateSkyColor(source, 0.55D), 0.35D);
        Vec3d lowerColor = mixSkyColors(horizonColor, source, 0.20D);

        double band = (uvY - (horizonY - softness)) / (softness * 2.0D);
        Vec3d result = mixSkyColors(lowerColor, topColor, smootherstep(band));

        return result;
    }

    protected double officialSkyNightFactor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0D;
        }
        double timeAngle = ((com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world) % 24000L) / 24000.0D) % 1.0D;
        return clamp01(Math.max(Math.sin(timeAngle * -Math.PI * 2.0D), 0.0D));
    }

    protected double officialSkyRainFactor(Minecraft mc) {
        World world = renderWorld(mc);
        if (world == null) {
            return 0.0D;
        }
        float partialTicks = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0F;
        return clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, partialTicks));
    }

    protected Vec3d maxSkyColor(Vec3d left, Vec3d right) {
        return new Vec3d(
                Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(left), com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(right)),
                Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(left), com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(right)),
                Math.max(com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(left), com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(right))
        );
    }

    protected Vec3d scaleSkyColor(Vec3d color, double scale) {
        return new Vec3d(
                clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(color) * scale),
                clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(color) * scale),
                clamp01(com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(color) * scale)
        );
    }

    protected double smootherstep(double value) {
        double t = clamp01(value);
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    protected float[] vec3Color(Vec3d color) {
        return new float[] {
                clamp01((float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(color)),
                clamp01((float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(color)),
                clamp01((float) com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(color))
        };
    }

    protected void putGradientQuad(BufferBuilder buffer, double x0, double y0, double x1, double y1, float[] bottom, float[] top) {
        putGradientQuad(buffer, x0, y0, x1, y1, bottom, top, 0.0D);
    }

    protected void putGradientQuad(BufferBuilder buffer, double x0, double y0, double x1, double y1, float[] bottom, float[] top, double z) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosColorEnd(buffer, x0, y0, z, colorByte(bottom[0]), colorByte(bottom[1]), colorByte(bottom[2]), 255);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosColorEnd(buffer, x1, y0, z, colorByte(bottom[0]), colorByte(bottom[1]), colorByte(bottom[2]), 255);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosColorEnd(buffer, x1, y1, z, colorByte(top[0]), colorByte(top[1]), colorByte(top[2]), 255);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bufferPosColorEnd(buffer, x0, y1, z, colorByte(top[0]), colorByte(top[1]), colorByte(top[2]), 255);
    }

    protected int colorByte(float value) {
        return clampInt((int) (clamp01(value) * 255.0F + 0.5F), 0, 255);
    }

    protected void sealShaderlessWorldFramebufferAlpha(String stage) {
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }

        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        FloatBuffer previousClearColor = BufferUtils.createFloatBuffer(4);
        ByteBuffer previousColorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, previousClearColor);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(false, false, false, true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        } catch (RuntimeException | LinkageError ignored) {
        } finally {
            GL11.glClearColor(
                    previousClearColor.get(0),
                    previousClearColor.get(1),
                    previousClearColor.get(2),
                    previousClearColor.get(3)
            );
            GL11.glColorMask(
                    previousColorMask.get(0) != 0,
                    previousColorMask.get(1) != 0,
                    previousColorMask.get(2) != 0,
                    previousColorMask.get(3) != 0
            );
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    public void sealShaderlessSkyFramebufferAlpha() {
        sealShaderlessWorldFramebufferAlpha("post-sky");
    }

    public void logHiddenSkyFramebufferProbe(String stage) {
        if (!DEBUG_PROBES_ENABLED) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Object screen = mc == null ? null : com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        boolean hideGui = mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc));
        boolean paused = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc);
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || (!hideGui && !paused && screen == null)) {
            return;
        }
        int probeCall;
        String probeKind;
        if (hideGui) {
            if (hiddenF1SkyFramebufferProbeLogs >= 96) {
                return;
            }
            probeCall = ++hiddenF1SkyFramebufferProbeLogs;
            probeKind = "f1";
        } else {
            if (hiddenSkyFramebufferProbeLogs >= 64) {
                return;
            }
            probeCall = ++hiddenSkyFramebufferProbeLogs;
            probeKind = "gui";
        }
        Framebuffer target = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        if (target == null) {
            return;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int width = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        int height = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        FloatBuffer top = BufferUtils.createFloatBuffer(4);
        FloatBuffer center = BufferUtils.createFloatBuffer(4);
        FloatBuffer windowTop = BufferUtils.createFloatBuffer(4);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
            GL11.glReadPixels(width / 2, Math.max(0, (height * 3) / 4), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, top);
            GL11.glReadPixels(width / 2, height / 2, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, center);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glReadPixels(Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) / 2),
                    Math.max(0, (com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) * 3) / 4),
                    1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, windowTop);
            World world = com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
            float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
            float celestial = world == null ? -1.0F : com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks);
            float rain = world == null ? -1.0F : com.l.ausm.impl.util.MinecraftReflectionCompat.worldRainStrength(world, partialTicks);
            MainMod.LOGGER.info("[AUSMHiddenSkyProbe] kind={} call={} stage={} screen={} hideGui={} paused={} time={} celestial={} rain={} top={}/{}/{}/{} center={}/{}/{}/{} windowTop={}/{}/{}/{} blend={} alpha={} fbo={}",
                    probeKind,
                    probeCall,
                    stage,
                    screen == null ? "none" : screen.getClass().getName(),
                    hideGui,
                    paused,
                    world == null ? -1L : com.l.ausm.impl.util.MinecraftReflectionCompat.worldTime(world),
                    celestial,
                    rain,
                    top.get(0), top.get(1), top.get(2), top.get(3),
                    center.get(0), center.get(1), center.get(2), center.get(3),
                    windowTop.get(0), windowTop.get(1), windowTop.get(2), windowTop.get(3),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
        } catch (RuntimeException | LinkageError ignored) {
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GL11.glReadBuffer(previousReadBuffer);
        }
    }

    protected void restoreShaderlessBloomExitState(Minecraft mc) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        if (mc != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
        }
    }

    public void prepareShaderlessUiRenderingBoundary() {
        if (disableShaderlessPreGuiHooks) {
            return;
        }
        if (isPipelineActive
                || externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null) {
            return;
        }
        bindMinecraftFramebufferForGui(mc);
        restoreShaderlessBloomExitState(mc);
        sealShaderlessWorldFramebufferAlpha("ui-boundary");
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc))) {
            // F1 has no subsequent HUD draw to consume/reset GUI blending. Keep
            // the world framebuffer opaque for the final presentation blit.
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
    }

    protected void refreshShaderlessBloomVertexFormatIfNeeded() {
        refreshShaderlessBloomVertexFormatIfNeeded(bloomRenderer.hasBloomResources());
    }

    protected void refreshShaderlessBloomVertexFormatIfNeeded(boolean hasBloomResources) {
        if (isPipelineActive
                || shaderlessBloomVertexFormatRefreshRequested
                || !hasBloomResources) {
            return;
        }

        shaderlessBloomVertexFormatRefreshRequested = true;
        boolean recreateNothirium = updateNothiriumPipelineBlockFormatMode();
        rebuildTerrainRenderers(recreateNothirium, false);
    }

    protected boolean hasShaderlessFramedBloomBootstrapCandidate() {
        return false;
    }

    public void recordCurrentShaderlessBloomMetadata(BlockRenderLayer layer) {
        recordShaderlessBloomMetadata(
                BlockRenderContext.blockX(),
                BlockRenderContext.blockY(),
                BlockRenderContext.blockZ(),
                layer
        );
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer) {
        if (pos == null) {
            return;
        }
        recordShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), layer);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer) {
        recordShaderlessBloomMetadata(blockX, blockY, blockZ, layer, true);
    }

    public void recordShaderlessBloomMetadata(BlockPos pos, BlockRenderLayer layer, boolean hasBloom) {
        if (pos == null) {
            return;
        }
        recordShaderlessBloomMetadata(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos), com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos), layer, hasBloom);
    }

    public void recordShaderlessBloomMetadata(int blockX, int blockY, int blockZ, BlockRenderLayer layer, boolean hasBloom) {
        if (layer == null) {
            return;
        }
        long key = BloomExtractionPlan.metadataKey(
                currentClientDimensionId(),
                blockX >> 4,
                blockY >> 4,
                blockZ >> 4,
                layer
        );
        shaderlessBloomMetadataKnownChunkLayers.add(key);
        if (hasBloom) {
            shaderlessBloomMetadataChunkLayers.add(key);
        }
    }

    public void recordShaderlessBloomLayerSummary(BlockPos pos, BlockRenderLayer layer, boolean hasBloom) {
        if (pos == null) {
            return;
        }
        recordShaderlessBloomLayerSummary(
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos),
                layer,
                hasBloom
        );
    }

    public void recordShaderlessBloomLayerSummary(int blockX, int blockY, int blockZ, BlockRenderLayer layer, boolean hasBloom) {
        if (layer == null) {
            return;
        }
        long key = BloomExtractionPlan.metadataKey(
                currentClientDimensionId(),
                blockX >> 4,
                blockY >> 4,
                blockZ >> 4,
                layer
        );
        shaderlessBloomMetadataKnownChunkLayers.add(key);
        if (hasBloom) {
            shaderlessBloomMetadataChunkLayers.add(key);
        } else {
            shaderlessBloomMetadataChunkLayers.remove(key);
        }
    }

    public void clearShaderlessBloomMetadata() {
        shaderlessBloomMetadataKnownChunkLayers.clear();
        shaderlessBloomMetadataChunkLayers.clear();
    }

    public void rebuildShaderlessBloomTerrain(String reason) {
        clearShaderlessBloomMetadata();
        scheduleBloomTerrainRefresh(reason);
    }

    public void handleShaderlessBloomBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        if (world == null || pos == null) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        int dimension = safeDimensionId(world);
        int sectionX = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) >> 4;
        int sectionY = Math.max(0, Math.min(15, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) >> 4));
        int sectionZ = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) >> 4;
        boolean hadBloomMetadata = invalidateShaderlessBloomMetadataSection(dimension, sectionX, sectionY, sectionZ);
        boolean bloomSourceChanged = stateHasShaderlessBloomSource(oldState) || stateHasShaderlessBloomSource(newState);
        if (!hadBloomMetadata && !bloomSourceChanged) {
            return;
        }

        // Lumenized's native BLOOM layer is rebuilt by the normal world block
        // update. Scheduling our legacy shaderless extractor here recompiled
        // every populated section in the column several times and caused
        // visible flicker after ordinary block placement.
        if (AusmBloomLayer.shouldUseShaderlessNativeHook()) {
            return;
        }

        int x = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos);
        int y = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos);
        int z = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos);
        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(
                world,
                x - 1,
                Math.max(0, y - 1),
                z - 1,
                x + 1,
                Math.min(255, y + 1),
                z + 1
        );
        queueShaderlessBloomClientChunkRefresh(world, sectionX, sectionZ);
    }

    public void handleClientBlockRenderUpdate(World world, BlockPos pos) {
        if (pos == null) {
            return;
        }
        handleClientBlockRenderUpdateRange(world,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos));
    }

    public void handleClientBlockRenderUpdateRange(World world, int minX, int minY, int minZ,
                                                    int maxX, int maxY, int maxZ) {
        if (!(world instanceof WorldClient worldClient)) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        int startX = Math.min(minX, maxX) >> 4;
        int endX = Math.max(minX, maxX) >> 4;
        int startZ = Math.min(minZ, maxZ) >> 4;
        int endZ = Math.max(minZ, maxZ) >> 4;
        int queued = 0;
        for (int chunkX = startX; chunkX <= endX; chunkX++) {
            for (int chunkZ = startZ; chunkZ <= endZ; chunkZ++) {
                queueClientChunkRenderRefresh(worldClient, chunkX, chunkZ,
                        CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE);
                queued++;
            }
        }
        if (queued > 0) {
            MainMod.LOGGER.info("[AUSMClientChunkRefresh] queued render-update range chunks={}..{} x {}..{} count={} world={}",
                    startX, endX, startZ, endZ, queued, safeDimensionId(world));
        }
    }

    public void handleShaderlessBloomRenderUpdateRange(World world, int minX, int minY, int minZ,
                                                       int maxX, int maxY, int maxZ) {
        if (world == null) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != world) {
            return;
        }

        // Generic render updates are not proof that shaderless bloom sources changed.
        // Keep extraction metadata stable until the requested rebuild publishes its
        // compile summary; actual block changes invalidate through the block-update path.
    }

    protected boolean renderUpdateRangeContainsShaderlessBloomSource(World world, int minX, int minY, int minZ,
                                                                  int maxX, int maxY, int maxZ) {
        int startX = Math.min(minX, maxX);
        int endX = Math.max(minX, maxX);
        int startY = Math.max(0, Math.min(255, Math.min(minY, maxY)));
        int endY = Math.max(0, Math.min(255, Math.max(minY, maxY)));
        int startZ = Math.min(minZ, maxZ);
        int endZ = Math.max(minZ, maxZ);
        long volume = (long) (endX - startX + 1) * (long) (endY - startY + 1) * (long) (endZ - startZ + 1);
        if (volume <= 0L || volume > 4096L) {
            return false;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int y = startY; y <= endY; y++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int x = startX; x <= endX; x++) {
                    com.l.ausm.impl.util.MinecraftReflectionCompat.mutableBlockPosSet(mutablePos, x, y, z);
                    if (!com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, mutablePos, false)) {
                        continue;
                    }
                    try {
                        IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, mutablePos);
                        if (stateHasShaderlessBloomSource(state)) {
                            return true;
                        }
                    } catch (RuntimeException | LinkageError ignored) {
                    }
                }
            }
        }
        return false;
    }

    protected void queueShaderlessBloomClientChunkRefreshes(World world, int sectionMinX, int sectionMaxX,
                                                         int sectionMinZ, int sectionMaxZ) {
        int startX = Math.min(sectionMinX, sectionMaxX);
        int endX = Math.max(sectionMinX, sectionMaxX);
        int startZ = Math.min(sectionMinZ, sectionMaxZ);
        int endZ = Math.max(sectionMinZ, sectionMaxZ);
        int queued = 0;
        for (int sectionZ = startZ; sectionZ <= endZ; sectionZ++) {
            for (int sectionX = startX; sectionX <= endX; sectionX++) {
                queueShaderlessBloomClientChunkRefresh(world, sectionX, sectionZ);
                queued++;
                if (queued >= MAX_SHADERLESS_BLOOM_LOCAL_CHUNK_REFRESHES_PER_UPDATE) {
                    return;
                }
            }
        }
    }

    protected void queueShaderlessBloomClientChunkRefresh(World world, int chunkX, int chunkZ) {
        if (world instanceof WorldClient worldClient) {
            queueClientChunkRenderRefresh(worldClient, chunkX, chunkZ, CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM);
        }
    }

    protected boolean invalidateShaderlessBloomMetadataSection(int dimension, int sectionX, int sectionY, int sectionZ) {
        boolean hadBloomMetadata = false;
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null) {
                continue;
            }
            long key = BloomExtractionPlan.metadataKey(dimension, sectionX, sectionY, sectionZ, layer);
            hadBloomMetadata |= shaderlessBloomMetadataChunkLayers.remove(key);
            shaderlessBloomMetadataKnownChunkLayers.remove(key);
        }
        return hadBloomMetadata;
    }

    protected boolean hasShaderlessBloomMetadata() {
        return !shaderlessBloomMetadataChunkLayers.isEmpty();
    }

    public boolean isShaderlessBloomExtractionActive() {
        return shaderlessBloomExtractionActive;
    }

    public boolean shouldRenderShaderlessBloomChunkLayer(BlockRenderLayer layer, int chunkBlockX, int chunkBlockY, int chunkBlockZ) {
        return shouldRenderShaderlessBloomChunkLayer(
                layer,
                chunkBlockX,
                chunkBlockY,
                chunkBlockZ,
                shaderlessBloomExtractionDimensionId()
        );
    }

    public int shaderlessBloomExtractionDimensionId() {
        return shaderlessBloomExtractionActive ? currentClientDimensionId() : Integer.MIN_VALUE;
    }

    public boolean shouldRenderShaderlessBloomChunkLayer(BlockRenderLayer layer, int chunkBlockX, int chunkBlockY,
                                                          int chunkBlockZ, int dimension) {
        if (!shaderlessBloomExtractionActive) {
            return true;
        }
        if (layer == null) {
            return false;
        }
        if (AusmBloomLayer.isBloomLayer(layer) || shaderlessBloomExtractionBootstrapActive) {
            return true;
        }
        long key = BloomExtractionPlan.metadataKey(
                dimension,
                chunkBlockX >> 4,
                chunkBlockY >> 4,
                chunkBlockZ >> 4,
                layer
        );
        return shaderlessBloomMetadataChunkLayers.contains(key);
    }

    public void prepareShaderlessOptimizedBloomDraw() {
    }

    protected int currentClientDimensionId() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        WorldProvider provider = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(provider)
                : Integer.MIN_VALUE;
    }

    protected boolean isSimpleVoidWorld(World world) {
        WorldProvider provider = com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(provider) == SIMPLE_VOID_WORLD_DIMENSION_ID;
    }

    public boolean isCustomVoidWorldSkyEnabled(World world) {
        return isPipelineActive
                && isSimpleVoidWorld(world)
                && shaderProperties != null
                && optionBoolean(shaderProperties, CUSTOM_VOID_WORLD_OPTION, false);
    }

    public boolean shouldUseOwnedSkyOverrideWorld(World world) {
        return isSimpleVoidWorld(world) || isOverworldShaderEnvironment(world);
    }

    protected int renderShaderlessBloomExtractionGeometry(Minecraft mc, Entity viewEntity, boolean allowPipelineActive) {
        return renderBloomExtractionGeometry(mc, viewEntity, allowPipelineActive);
    }

    protected int renderBloomExtractionGeometry(Minecraft mc, Entity viewEntity, boolean allowPipelineActive) {
        if (mc == null || viewEntity == null) {
            return 0;
        }
        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        if (!isPipelineActive && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            ((EntityRendererAccessor) com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc)).ausm$setupCameraTransform(partialTicks, 2);
            MatrixState.captureGbufferMatrices();
        }
        return renderEmissiveExtractionTerrain(partialTicks, viewEntity, allowPipelineActive);
    }

    protected int renderEmissiveExtractionTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if ((!allowPipelineActive && isPipelineActive) || viewEntity == null) {
            return 0;
        }
        if (CeleritasCompat.installed()
                || !NothiriumShadowRenderer.isAvailable()
                || NothiriumBypass.shouldBypass()) {
            return renderVanillaEmissiveTerrain(partialTicks, viewEntity, allowPipelineActive);
        }
        return renderNothiriumEmissiveExtractionTerrain(partialTicks, viewEntity);
    }

    protected int renderNothiriumEmissiveExtractionTerrain(float partialTicks, Entity viewEntity) {
        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        nothiriumShadowRenderer.drainUploads();

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        if (!isPipelineActive) {
            shaderlessWorldPassActive = true;
        }
        try {
            activePhase = WorldRenderingPhase.TERRAIN_SOLID;
            int solid = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.SOLID, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED;
            int cutoutMipped = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT_MIPPED, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_CUTOUT;
            int cutout = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.CUTOUT, cameraX, cameraY, cameraZ);
            activePhase = WorldRenderingPhase.TERRAIN_TRANSLUCENT;
            int translucent = renderShaderlessNothiriumExtractionLayer(BlockRenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ);
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            int bloom = shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer) && mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null
                    ? renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            return solid + cutoutMipped + cutout + translucent + bloom;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    protected int renderShaderlessNothiriumExtractionLayer(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            return positiveCount(nothiriumShadowRenderer.renderVisibleLayer(layer, cameraX, cameraY, cameraZ, 0, (short) 0));
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
        }
    }

    protected int renderVanillaEmissiveTerrain(float partialTicks, Entity viewEntity, boolean allowPipelineActive) {
        if (!allowPipelineActive && isPipelineActive) {
            return 0;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || viewEntity == null) {
            return 0;
        }

        WorldRenderingPhase previousPhase = activePhase;
        boolean previousShaderlessWorldPassActive = shaderlessWorldPassActive;
        if (!isPipelineActive) {
            shaderlessWorldPassActive = true;
        }
        try {
            int rendered = 0;
            for (BlockRenderLayer layer : BloomExtractionPlan.terrainLayers()) {
                rendered += renderShaderlessVanillaEmissiveLayerIfVisible(
                        mc, BloomExtractionPlan.phaseFor(layer), layer, partialTicks, viewEntity);
            }
            BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
            int bloom = shouldRenderSyntheticBloomLayerWithRenderGlobal(bloomLayer)
                    ? renderShaderlessVanillaEmissiveLayerIfVisible(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, bloomLayer, partialTicks, viewEntity)
                    : 0;
            return rendered + bloom;
        } finally {
            activePhase = previousPhase;
            shaderlessWorldPassActive = previousShaderlessWorldPassActive;
        }
    }

    protected int renderShaderlessVanillaEmissiveLayerIfVisible(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                              float partialTicks, Entity viewEntity) {
        if (!shouldRenderShaderlessExtractionLayer(layer)) {
            return 0;
        }
        return renderShaderlessVanillaEmissiveLayer(mc, phase, layer, partialTicks, viewEntity);
    }

    protected int renderShaderlessVanillaEmissiveLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer,
                                                     float partialTicks, Entity viewEntity) {
        activePhase = phase;
        boolean forceBloomLayerEmission = AusmBloomLayer.isBloomLayer(layer);
        prepareShaderlessBlockLayerState(layer);
        bloomRenderer.setShaderlessForceEmission(forceBloomLayerEmission ? 1.0F : 0.0F);
        try {
            RenderGlobal renderGlobal = com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
            return renderGlobal != null ? positiveCount(com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, 2, viewEntity)) : 0;
        } finally {
            if (forceBloomLayerEmission) {
                bloomRenderer.setShaderlessForceEmission(0.0F);
            }
            finishShaderlessBlockLayerState(layer);
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    protected static boolean shouldRenderSyntheticBloomLayerWithRenderGlobal(BlockRenderLayer layer) {
        return BloomExtractionPlan.shouldRenderSyntheticLayer(layer, isNothiriumLoaded());
    }

    protected static boolean isNothiriumLoaded() {
        return Loader.isModLoaded(NOTHIRIUM_MOD_ID) || Loader.isModLoaded(NAUGHTHIRIUM_MOD_ID);
    }

    protected static int floorDouble(double value) {
        int truncated = (int) value;
        return value < (double) truncated ? truncated - 1 : truncated;
    }

    protected static int positiveCount(int count) {
        return Math.max(0, count);
    }

    protected void logShaderlessBloomHook(String detail) {
        if (shaderlessBloomHookLogs >= MAX_SHADERLESS_BLOOM_HOOK_LOGS) {
            return;
        }
        shaderlessBloomHookLogs++;
        MainMod.LOGGER.info("[AUSMBloom] Shaderless pre-GUI hook {}", detail);
    }

    protected String bloomMetadataSummary() {
        return "known=" + shaderlessBloomMetadataKnownChunkLayers.size()
                + ", bloom=" + shaderlessBloomMetadataChunkLayers.size()
                + ", extractionActive=" + shaderlessBloomExtractionActive
                + ", bootstrap=" + shaderlessBloomExtractionBootstrapActive;
    }

    protected static String glStateSummary() {
        return FixedFunctionGlState.summary();
    }

    public void prepareExternalOverlayRender(String source) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) == null) {
            return;
        }

        if (externalOverlayLogs < MAX_EXTERNAL_OVERLAY_LOGS) {
            externalOverlayLogs++;
            MainMod.LOGGER.info("[PipelineCompat] Preparing external overlay renderer: {} active={} worldFrame={} gui={} framebuffer={}",
                    source,
                    isPipelineActive,
                    worldFrameActive,
                    renderingGuiScreen(),
                    describeFramebufferTarget(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)));
        }

        if (isPipelineActive
                && worldFrameActive
                && externalWorldFramebufferTarget == null
                && !isRenderingBetterPortalsNestedView()) {
            prepareFramebufferPresentation();
        }

        bindMinecraftFramebufferForGui(mc);
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.disableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    public void finishExternalOverlayRender(String source) {
        restoreGuiSafeRenderState(source);
    }

    public void finishExternalWorldOverlayRender(String source) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (!isPipelineActive && (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) == null)) {
            return;
        }
        restoreWorldSafeRenderState(source);
    }

    protected void restoreGuiSafeRenderState(String source) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        bindMinecraftFramebufferForGui(mc);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        }
    }

    protected void restoreWorldSafeRenderState(String source) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        bindBlockAtlas();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
    }

    public void restoreActiveWorldPassAfterExternalShader() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingGuiScreen()) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass()
                || isRenderingBetterPortalsNestedView()) {
            return;
        }

        RenderPass pass = activePass;
        WorldRenderingPhase phase = activePhase;
        bindWorldFramebuffer();
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        bindPass(pass);
        activePhase = phase;
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:restore-active-after-external pass=" + pass + " phase=" + phase);
    }

    public void prepareFramebufferPresentation() {
        if (!isPipelineActive) {
            if (externalWorldFramebufferTarget == null && !isRenderingBetterPortalsNestedView()) {
                Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
                if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc) != null) {
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
                    TextureBinder.restoreDefaultTextureUnit();
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                            GL11.GL_SRC_ALPHA,
                            GL11.GL_ONE_MINUS_SRC_ALPHA,
                            GL11.GL_ONE,
                            GL11.GL_ZERO
                    );
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
                    GL11.glDepthMask(true);
                    GL11.glDepthFunc(GL11.GL_LEQUAL);
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                    GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
                }
            }
            return;
        }

        if (externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        if (worldFrameActive) {
            renderDeferredNativeBloomIfNeeded();
            blitWorldFramebufferToMinecraft();
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.disableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
    }

    public void prepareGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        renderingGui = true;
        bindGuiTarget();
        prepareGuiState();
    }

    public void prepareGuiFramebuffer() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        bindGuiTarget();
        prepareGuiState();
    }

    public void prepareGuiWorldBackgroundFromRecoveredSource() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Framebuffer target = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null) {
            return;
        }
        boolean refreshed = refreshMinecraftFramebufferFromDirectPresentationTexture(target, true)
                || refreshMinecraftFramebufferFromDirectRecoveredWindowSource(target);
        logGuiRecoveredBackground(refreshed, target);
        bindGuiTarget();
        prepareGuiState();
    }

    public void prepareShaderlessGuiScreenRendering() {
        if (isPipelineActive) {
            return;
        }
        prepareDirectGuiScreenRenderingState(false);
    }

    public void prepareBypassedGuiScreenRendering() {
        prepareDirectGuiScreenRenderingState(isPipelineActive);
        prepareVanillaGuiScreenOverlayState();
    }

    public void prepareBypassedGuiScreenDrawState() {
        prepareDirectGuiScreenRenderingState(false);
    }

    /**
     * Vanilla GuiScreen backgrounds and widgets are drawn over the already-presented
     * world.  Retaining the world depth buffer here rejects those flat screen quads.
     */
    private void prepareVanillaGuiScreenOverlayState() {
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
    }

    private void prepareDirectGuiScreenRenderingState(boolean flushPipelineWorld) {
        if (flushPipelineWorld && externalWorldFramebufferTarget == null && !isRenderingBetterPortalsNestedView()) {
            if (worldFrameActive) {
                renderDeferredNativeBloomIfNeeded();
                blitWorldFramebufferToMinecraft();
            }
        }
        renderingGui = false;
        guiRenderDepth = 0;
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            Framebuffer framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(framebuffer, false);
            restoreDrawBufferForFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer), GL30.GL_COLOR_ATTACHMENT0);
            restoreReadBufferForFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(framebuffer), GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc));
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
    }

    public void beginGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        boolean outermostGui = guiRenderDepth == 0;
        guiRenderDepth++;
        if (outermostGui) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            Framebuffer target = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
            if (target != null) {
                refreshMinecraftFramebufferFromDirectPresentationTexture(target, true);
            }
        }
        prepareGuiRendering();
    }

    public void beginGuiScreenRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }

        boolean preserveCompletedGui = guiRenderDepth == 0 && guiTargetContentFrame == pipelineFrameId;
        guiRenderDepth++;
        renderingGui = true;
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Framebuffer target = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target != null && !preserveCompletedGui) {
            refreshMinecraftFramebufferFromDirectPresentationTexture(target, true);
        }
        bindGuiTarget();
        prepareGuiState();
    }

    public void finishGuiScreenRendering() {
        finishGuiRendering();
    }

    public void finishGuiRendering() {
        if (!isPipelineActive || externalWorldFramebufferTarget != null || isRenderingBetterPortalsNestedView()) {
            return;
        }
        boolean completedGui = guiRenderDepth > 0;
        if (completedGui) {
            guiRenderDepth--;
        }
        if (guiRenderDepth == 0) {
            if (completedGui) {
                guiTargetContentFrame = pipelineFrameId;
            }
            renderingGui = false;
            restoreGuiSafeRenderState("gui-finish");
            drainPausedPostRenderGlErrors("gui-finish");
        }
    }

    protected void bindGuiTarget() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            bindMinecraftFramebufferForGui(mc);
        }
    }

    protected void prepareGuiState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.disableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        disablePipelineVertexAttributes();
        unbindShaderImages();
        unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        GL11.glDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        setIndexedBlend(0, true);
    }

    public boolean shouldDirectPresentFramebuffer() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return (isPipelineActive || shouldUseShaderlessHiddenGuiPresentation())
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                && externalWorldFramebufferTarget == null
                && !isRenderingBetterPortalsNestedView();
    }

    protected void snapshotPresentationTargetForDirectPresentation(Framebuffer target, String reason) {
        if (target == null) {
            directPresentationValid = false;
            return;
        }
        if (worldLoadPresentationGuardFrames > 0) {
            directPresentationValid = false;
            return;
        }
        int sourceFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target);
        int width = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        int height = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        if (!ensureDirectPresentationTexture(width, height)) {
            directPresentationValid = false;
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, directPresentationFbo);
            GL11.glReadBuffer(sourceFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            directPresentationValid = true;
            directPresentationFrame = pipelineFrameId;
            directPresentationReason = reason;
            logDirectPresentationSnapshot(reason, target);
        } catch (RuntimeException | LinkageError e) {
            directPresentationValid = false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    protected boolean ensureDirectPresentationTexture(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (directPresentationTexture <= 0) {
            directPresentationTexture = GL11.glGenTextures();
        }
        if (directPresentationFbo <= 0) {
            directPresentationFbo = GL30.glGenFramebuffers();
        }
        if (directPresentationTexture <= 0 || directPresentationFbo <= 0) {
            return false;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, directPresentationTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            if (directPresentationWidth != width || directPresentationHeight != height) {
                GL11.glTexImage2D(
                        GL11.GL_TEXTURE_2D,
                        0,
                        GL11.GL_RGBA8,
                        width,
                        height,
                        0,
                        GL11.GL_RGBA,
                        GL11.GL_UNSIGNED_BYTE,
                        (ByteBuffer) null
                );
                directPresentationWidth = width;
                directPresentationHeight = height;
                directPresentationValid = false;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, directPresentationFbo);
            GL30.glFramebufferTexture2D(
                    GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D,
                    directPresentationTexture,
                    0
            );
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    protected boolean refreshMinecraftFramebufferFromDirectPresentationTexture(Framebuffer target, boolean allowStaleForGui) {
        if (target == null
                || !directPresentationValid
                || directPresentationTexture <= 0
                || directPresentationFbo <= 0
                || directPresentationWidth <= 0
                || directPresentationHeight <= 0) {
            return false;
        }
        long age = pipelineFrameId - directPresentationFrame;
        if (directPresentationFrame == Long.MIN_VALUE || age != 0L) {
            return false;
        }

        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target);
        int targetWidth = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, directPresentationFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    directPresentationWidth,
                    directPresentationHeight,
                    0,
                    0,
                    targetWidth,
                    targetHeight,
                    GL11.GL_COLOR_BUFFER_BIT,
                    GL11.GL_NEAREST
            );
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, targetWidth, targetHeight);
            logDirectPresentationTextureRefresh(target, targetWidth, targetHeight, allowStaleForGui);
            return true;
        } catch (RuntimeException | LinkageError e) {
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
            if (previousScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    protected void logDirectPresentationSnapshot(String reason, Framebuffer target) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || (!shouldDirectPresentFramebuffer()
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null)) {
            return;
        }
        if (directPresentationSnapshotLogs++ >= MAX_DIRECT_PRESENTATION_SNAPSHOT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectPresentationSnapshot] reason={} target={} targetColor={} snapshotFbo={} texture={} size={}x{} frame={} gl={}",
                reason,
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                directPresentationFbo,
                directPresentationTexture,
                directPresentationWidth,
                directPresentationHeight,
                directPresentationFrame,
                glStateSummary()
        );
    }

    protected void logDirectPresentationTextureRefresh(Framebuffer target, int targetWidth, int targetHeight, boolean gui) {
        if (directPresentationTextureRefreshLogs++ >= MAX_DIRECT_PRESENTATION_TEXTURE_REFRESH_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectPresentationTextureRefresh] reason={} gui={} target={} targetColor={} snapshotFbo={} texture={} snapshotSize={}x{} targetSize={}x{} snapshotFrame={} currentFrame={} gl={}",
                directPresentationReason,
                gui,
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                directPresentationFbo,
                directPresentationTexture,
                directPresentationWidth,
                directPresentationHeight,
                targetWidth,
                targetHeight,
                directPresentationFrame,
                pipelineFrameId,
                glStateSummary()
        );
    }

    protected void logGuiRecoveredBackground(boolean refreshed, Framebuffer target) {
        if (guiRecoveredBackgroundLogs++ >= MAX_GUI_RECOVERED_BACKGROUND_LOGS) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMGuiRecoveredBackground] refreshed={} screen={} target={} targetColor={} snapshotValid={} snapshotReason={} snapshotFrame={} currentFrame={} rawSourceFrame={} gl={}",
                refreshed,
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null
                        ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName()
                        : "none",
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                directPresentationValid,
                directPresentationReason,
                directPresentationFrame,
                pipelineFrameId,
                directRecoveredWindowFrame,
                glStateSummary()
        );
    }

    public void presentFramebufferDirectly(Framebuffer target, int width, int height) {
        if ((!isPipelineActive && !shouldUseShaderlessHiddenGuiPresentation())
                || externalWorldFramebufferTarget != null
                || isRenderingBetterPortalsNestedView()) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || target == null || target != com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc)) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        boolean previousScissor = GL11.glGetBoolean(GL11.GL_SCISSOR_TEST);
        FixedFunctionGlState.resetClientArrayState(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, width, height);
        boolean screenOpen = com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null;
        GL11.glColorMask(true, true, true, false);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glDrawBuffer(GL11.GL_BACK);
        GL30.glBlitFramebuffer(
                0,
                0,
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target),
                com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target),
                0,
                0,
                width,
                height,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
        );
        logDirectF1WindowPresent(target, width, height);
        logDirectWindowPresent(target, width, height, false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glColorMask(true, true, true, true);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        restoreDrawBufferForFramebuffer(previousDrawFramebuffer, previousDrawBuffer);
        if (previousScissor) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        TextureBinder.restoreDefaultTextureUnit();
        FixedFunctionGlState.resetClientArrayState(true);
    }

    protected void logDirectF1WindowPresent(Framebuffer target, int width, int height) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) == null
                || !com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(
                com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc))
                || directF1WindowPresentLogs++ >= 64) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectF1WindowPresent] call={} source={} sourceColor={} backColor={} drawFbo={} readFbo={} drawBuf={} readBuf={} size={}x{} glErrors={}",
                directF1WindowPresentLogs,
                describeFramebufferTargetDetailed(target),
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
        if (refreshMinecraftFramebufferFromDirectPresentationTexture(target, false)) {
            return true;
        }
        return refreshMinecraftFramebufferFromCurrentRecoveredWindowSource(target);
    }

    protected boolean refreshMinecraftFramebufferFromCurrentRecoveredWindowSource(Framebuffer target) {
        if (target == null
                || directRecoveredWindowSource == null
                || !directRecoveredWindowSource.isUsable()
                || directRecoveredWindowAttachment == null
                || directRecoveredWindowFrame != pipelineFrameId) {
            return false;
        }

        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target);
        int targetWidth = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferWidth(target));
        int targetHeight = Math.max(1, com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferHeight(target));
        GL11.glColorMask(true, true, true, true);
        directRecoveredWindowSource.blitTo(directRecoveredWindowAttachment, targetFramebuffer, targetWidth, targetHeight);
        com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, targetWidth, targetHeight);
        logDirectRecoveredWindowRefresh(target, targetWidth, targetHeight);
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
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
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
                describeDeferredFramebuffer(directRecoveredWindowSource),
                directRecoveredWindowAttachment,
                directRecoveredWindowColorScale,
                deferredFramebufferColorSamples(directRecoveredWindowSource, directRecoveredWindowAttachment),
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                targetWidth,
                targetHeight,
                directRecoveredWindowTargetWidth,
                directRecoveredWindowTargetHeight,
                directRecoveredWindowFrame,
                pipelineFrameId,
                glStateSummary()
        );
    }

    protected void logDirectWindowPresent(Framebuffer target, int width, int height, boolean recoveredRefresh) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || (!shouldDirectPresentFramebuffer()
                && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null)) {
            return;
        }
        if (directWindowPresentLogs++ >= MAX_DIRECT_WINDOW_PRESENT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDirectWindowPresent] source={} recoveredRefresh={} sourceColor={} backColor={} size={}x{} frame={} gl={}",
                describeFramebufferTargetDetailed(target),
                recoveredRefresh,
                framebufferSamples(target),
                framebufferIdColorSamples(0, Math.max(1, width), Math.max(1, height), GL11.GL_BACK),
                width,
                height,
                pipelineFrameId,
                glStateSummary()
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
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || (com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null
                && !shouldDirectPresentFramebuffer())) {
            return;
        }
        if (presentationBoundaryLogs++ >= MAX_PRESENTATION_BOUNDARY_LOGS) {
            return;
        }
        Framebuffer minecraftTarget = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
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
                shouldDirectPresentFramebuffer(),
                renderingGuiScreen(),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) != null
                        ? com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName()
                        : "none",
                mc != null
                        && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                        && com.l.ausm.impl.util.MinecraftReflectionCompat.hideGui(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc)),
                mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null
                        ? com.l.ausm.impl.util.MinecraftReflectionCompat.thirdPersonView(com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc))
                        : -1,
                target != null && target == minecraftTarget,
                describeFramebufferTargetDetailed(target),
                framebufferSamples(target),
                framebufferDepthSamples(target),
                describeFramebufferTargetDetailed(minecraftTarget),
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
                mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc) : -1,
                mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc) : -1,
                glStateSummary(),
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
                && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) != null;
    }

    public int distantHorizonsShaderProgramId() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            return ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
        }
        PipelineProgram pipelineProgram = effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram != null) {
            return pipelineProgram.shaderProgram().getId();
        }
        return ensureDistantHorizonsFallbackProgram() ? distantHorizonsFallbackProgramId : 0;
    }

    public void bindDistantHorizonsShaderProgram() {
        if (renderingDistantHorizonsPresentation || FORCE_DISTANT_HORIZONS_FALLBACK_PROGRAM) {
            bindDistantHorizonsFallbackProgram();
            return;
        }
        PipelineProgram pipelineProgram = effectiveDistantHorizonsPipelineProgram();
        if (pipelineProgram == null) {
            bindDistantHorizonsFallbackProgram();
            return;
        }

        ShaderProgram program = pipelineProgram.shaderProgram();
        RenderPass pass = pipelineProgram.pass();
        currentDistantHorizonsProgram = program;
        currentDistantHorizonsFallbackProgram = false;
        bindDistantHorizonsVertexArray();
        configureDistantHorizonsShaderState(pipelineProgram);
        program.bind();
        bindProgramResources(pass, program);
    }

    public void unbindDistantHorizonsShaderProgram() {
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = false;
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
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
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
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
        bindDistantHorizonsVertexArray();
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
        if (!ensureDistantHorizonsFallbackProgram()) {
            return;
        }
        currentDistantHorizonsProgram = null;
        currentDistantHorizonsFallbackProgram = true;
        bindDistantHorizonsVertexArray();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(distantHorizonsFallbackProgramId);
        uploadDistantHorizonsFallbackMatrices();
        uploadDistantHorizonsFallbackModelOffset();
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
            vertexShader = compileDistantHorizonsFallbackShader(GL20.GL_VERTEX_SHADER, DistantHorizonsInternalShaders.FALLBACK_VERTEX);
            fragmentShader = compileDistantHorizonsFallbackShader(GL20.GL_FRAGMENT_SHADER, DistantHorizonsInternalShaders.FALLBACK_FRAGMENT);
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
        if (distantHorizonsCompositeProgramFailed || !com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean(net.minecraft.client.renderer.OpenGlHelper.class, false, "field_148824_g", "shadersSupported")) {
            return false;
        }

        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileDistantHorizonsCompositeShader(GL20.GL_VERTEX_SHADER, DistantHorizonsInternalShaders.COMPOSITE_VERTEX);
            fragmentShader = compileDistantHorizonsCompositeShader(GL20.GL_FRAGMENT_SHADER, DistantHorizonsInternalShaders.COMPOSITE_FRAGMENT);
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

    protected int compileDistantHorizonsCompositeShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            MainMod.LOGGER.warn("[DistantHorizons] Failed to compile composite shader stage {}: {}",
                    type,
                    GL20.glGetShaderInfoLog(shader, 4096));
            GL20.glDeleteShader(shader);
            distantHorizonsCompositeProgramFailed = true;
            return 0;
        }
        return shader;
    }

    protected void deleteDistantHorizonsCompositeProgram() {
        if (distantHorizonsCompositeProgramId != 0) {
            GL20.glDeleteProgram(distantHorizonsCompositeProgramId);
        }
        distantHorizonsCompositeProgramId = 0;
        distantHorizonsCompositeTextureUniform = -1;
        distantHorizonsCompositeDepthUniform = -1;
        distantHorizonsCompositeProgramFailed = false;
    }

    protected void deleteDistantHorizonsFramebuffer() {
        if (distantHorizonsFramebufferId != 0) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(distantHorizonsFramebufferId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsColorTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsColorTextureId);
        }
        if (distantHorizonsTexturesOwned && distantHorizonsDepthTextureId != 0) {
            GL11.glDeleteTextures(distantHorizonsDepthTextureId);
        }
        if (distantHorizonsTextureReadbackFramebufferId != 0) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glDeleteFramebuffers(distantHorizonsTextureReadbackFramebufferId);
        }
        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = 0;
        distantHorizonsFramebufferHeight = 0;
        distantHorizonsFramebufferClearFrame = Long.MIN_VALUE;
        distantHorizonsTextureReadbackFramebufferId = 0;
        distantHorizonsFramebufferPendingComposite = false;
    }

    public void setDistantHorizonsModelOffset(Object vec) {
        if (vec == null) {
            return;
        }
        try {
            distantHorizonsMatrices.updateModelOffset(vec);
            if (currentDistantHorizonsProgram != null) {
                uniformRegistry.upload(currentDistantHorizonsProgram, "dhModelOffset");
                uniformRegistry.upload(currentDistantHorizonsProgram, "iris_ModelOffset");
            } else if (currentDistantHorizonsFallbackProgram) {
                uploadDistantHorizonsFallbackModelOffset();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    public void uploadDistantHorizonsUniforms(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            updateDistantHorizonsRenderPass(renderParam);

            distantHorizonsMatrices.update(renderParam);
            bindDistantHorizonsShaderProgram();
            uploadDistantHorizonsWorldYOffset(renderParam);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    protected void uploadDistantHorizonsWorldYOffset(Object renderParam) {
        if (!currentDistantHorizonsFallbackProgram || distantHorizonsFallbackProgramId == 0 || distantHorizonsFallbackWorldYOffsetUniform < 0) {
            return;
        }
        try {
            float worldYOffset = ((Number) renderParam.getClass().getField("worldYOffset").get(renderParam)).floatValue();
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            GL20.glUniform1f(distantHorizonsFallbackWorldYOffsetUniform, 0.0F);
        }
    }

    protected ShaderProgram activeProgram() {
        if (activePass == null) {
            return null;
        }
        return getProgram(activePass);
    }

    public PingPongManager getPingPongManager() {
        return pingPongManager;
    }

    public int getShadowDepthTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthTextureId() : -1;
    }

    public int getShadowDepthSnapshotTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.depthSnapshotTextureId() : -1;
    }

    public int getRawShadowDepthTexture() {
        return shadowFramebuffer != null ? shadowFramebuffer.rawDepthTextureId() : -1;
    }

    public int getShadowColor0Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId() : -1;
    }

    public int getShadowColor1Texture() {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(1) : -1;
    }

    public int getShadowColorTexture(int index) {
        return shadowFramebuffer != null ? shadowFramebuffer.colorTextureId(index) : -1;
    }

    public boolean shouldUseNeutralShadowTextures() {
        // A clear or sparse shadow map samples as zero with Entree's
        // sampler2DShadow path, which makes the whole scene look shadowed.
        // Keep the real map bound only after the shadow health gate confirms
        // usable terrain coverage; the neutral depth texture represents a
        // fully lit frame while the map is warming up or invalid.
        return isBetterPortalsExternalWorldTarget() || !shadowMapUsable;
    }

    public boolean isShadowMapUsable() {
        return shadowMapUsable;
    }

    public boolean isShadowMapPopulated() {
        return shadowMapPopulated;
    }

    public boolean shouldUseShadowHardwareFiltering() {
        return packDirectives.renderTargets().shadowHardwareFiltering();
    }

    public void configureShadowDepthTextureCompareMode() {
        if (shadowFramebuffer != null) {
            shadowFramebuffer.configureDepthTextureCompareMode();
        }
    }

    public void setActive(boolean active) {
        boolean wasPipelineActive = isPipelineActive;
        isPipelineActive = active && pingPongManager.isInitialized();
        boolean activeStateChanged = wasPipelineActive != isPipelineActive;
        boolean shaderDisable = wasPipelineActive && !isPipelineActive;
        if (isPipelineActive) {
            zeroOpaqueTerrainFrames = 0;
            sparseOpaqueTerrainFrames = 0;
            zeroOpaqueTerrainRecoveryRequested = false;
            betterPortalsPipelineLogs = 0;
            BetterPortalsCompat.resetRenderStateDiagnostics();
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null) {
                resizeFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), true);
            }
            clearShaderedNothiriumGlobalBypassState(true);
        } else {
            clearShaderedTerrainFallbackState();
            clearPendingShaderChunkRefreshes();
            clearShaderlessBloomMetadata();
            clearScheduledWorldTerrainRefresh();
            clearScheduledBloomTerrainRefresh();
            shaderlessBloomVertexFormatRefreshRequested = false;
            scheduleInactiveVanillaRecoveryFrame();
            resetPipelineState();
        }
        boolean nothiriumFormatChanged = updateNothiriumPipelineBlockFormatMode();
        if (!shaderDisable && (activeStateChanged || nothiriumFormatChanged)) {
            rebuildTerrainRenderers(activeStateChanged || nothiriumFormatChanged, true);
        } else if (shaderDisable) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            logTerrainDiagnostic("shader-disable:skip-global-rebuild",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "formatChanged=" + nothiriumFormatChanged + ", activeStateChanged=" + activeStateChanged);
        }
    }

    public void recoverShaderlessBloomAfterShaderDisable(String reason) {
        clearShaderlessBloomMetadata();
        shaderlessBloomVertexFormatRefreshRequested = false;
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        clearScheduledWorldTerrainRefresh();
        clearScheduledBloomTerrainRefresh();
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        logTerrainDiagnostic("shader-disable:defer-shaderless-bloom-recovery",
                mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                "reason=" + reason + ", bloomResources=" + bloomRenderer.hasBloomResources());
    }

    public void rebuildTerrainRenderers() {
        rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode());
    }

    public void handleResourcePackReload() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }

        textureReloadCount++;
        resetPipelineState(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc));
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        clearScheduledWorldTerrainRefresh();
        clearScheduledBloomTerrainRefresh();
        scheduleWorldTerrainRefresh();
        scheduleBloomTerrainRefresh("resource-pack-reload");
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null) {
            scheduleWorldLoadLightRecalculation();
            rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode());
        }
        MainMod.LOGGER.info("[Pipeline] Recovered render state after resource pack reload.");
    }

    protected void rebuildTerrainRenderers(boolean recreateNothiriumRenderer) {
        rebuildTerrainRenderers(recreateNothiriumRenderer, true);
    }

    protected void rebuildTerrainRenderers(boolean recreateNothiriumRenderer, boolean reloadVanillaRenderGlobal) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        logTerrainDiagnostic("rebuild-terrain-renderers", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "recreateNothirium=" + recreateNothiriumRenderer
                + ", reloadVanilla=" + reloadVanillaRenderGlobal);
        if (recreateNothiriumRenderer) {
            NothiriumBypass.recreateRenderer();
        } else {
            NothiriumBypass.markAllChanged();
        }
        if (reloadVanillaRenderGlobal) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.loadRenderers(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc));
        }
        if (isPipelineActive && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null) {
            rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "rebuild-terrain-renderers");
            resetCameraFrustumSyncState();
        } else if (isPipelineActive) {
            ensureVanillaTerrainRenderer();
        }
    }

    protected boolean updateNothiriumPipelineBlockFormatMode() {
        boolean active = shouldUsePipelineBlockFormat();
        if (nothiriumPipelineBlockFormatActive == active) {
            return false;
        }
        nothiriumPipelineBlockFormatActive = active;
        MainMod.LOGGER.info("[AUSMNothiriumFormat] pipelineBlockFormat={} pipelineActive={} nativeBloom={} bloomResources={} terrainFormatSupported={}",
                active,
                isPipelineActive,
                AusmBloomLayer.shouldUseNativeHook(),
                bloomRenderer.hasBloomResources(),
                pipelineTerrainFormatSupported());
        return true;
    }

    public int[] forceLightRecalculation() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return new int[]{0, 0, 0, 0};
        }

        World world = com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        BlockPos center = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        int horizontalRadius = Math.min(
                FORCE_LIGHT_RECALC_MAX_RADIUS,
                Math.max(FORCE_LIGHT_RECALC_MIN_RADIUS, WORLD_LOAD_LIGHT_REFRESH_RADIUS)
        );
        int verticalRadius = Math.min(8, horizontalRadius);
        int minX = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) - horizontalRadius;
        int maxX = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) + horizontalRadius;
        int minY = Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) - verticalRadius);
        int maxY = Math.min(255, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) + verticalRadius);
        int minZ = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) - horizontalRadius;
        int maxZ = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) + horizontalRadius;

        syntheticLightCandidates.clear();
        if (shaderImages.active()) {
            clearColoredLightImages();
        }
        resetColoredLightAudit();

        int chunkCount = forceChunkLightingRefresh(world, minX, maxX, minZ, maxZ);
        int blockChecks = forceBlockLightingRefresh(world, minX, minY, minZ, maxX, maxY, maxZ);

        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(world, minX, minY, minZ, maxX, maxY, maxZ);
        refreshVanillaLightmap(mc);
        rebuildTerrainRenderers();

        MainMod.LOGGER.info(
                "[Lighting] Forced nearby light recalculation radius={} verticalRadius={} chunks={} blockChecks={} shadersActive={}",
                horizontalRadius,
                verticalRadius,
                chunkCount,
                blockChecks,
                isPipelineActive
        );
        return new int[]{horizontalRadius, chunkCount, blockChecks, isPipelineActive ? 1 : 0};
    }

    public void scheduleWorldLoadLightRecalculation() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null ? safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationAttempts > 0
                && pendingWorldLoadLightRecalculationDimension == dimension) {
            pendingWorldLoadLightRecalculationAttempts = Math.max(
                    pendingWorldLoadLightRecalculationAttempts,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS
            );
            pendingWorldLoadLightRecalculationDelay = Math.min(
                    pendingWorldLoadLightRecalculationDelay,
                    WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES
            );
            return;
        }

        pendingWorldLoadLightRecalculationAttempts = WORLD_LOAD_FORCE_LIGHT_RECALC_ATTEMPTS;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        pendingWorldLoadLightRecalculationDimension = dimension;
    }

    public void clearScheduledWorldLoadLightRecalculation() {
        pendingWorldLoadLightRecalculationAttempts = 0;
        pendingWorldLoadLightRecalculationDelay = 0;
        pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
    }

    public void scheduleWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(false);
    }

    public void scheduleFullWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true);
    }

    public void scheduleSingleFullWorldTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES, 1);
    }

    protected void scheduleDimensionSwitchTerrainRefresh() {
        scheduleWorldTerrainRefresh(true, true, 0);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset) {
        scheduleWorldTerrainRefresh(fullRendererReset, fullRendererReset);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload) {
        scheduleWorldTerrainRefresh(fullRendererReset, vanillaReload, WORLD_LOAD_TERRAIN_REFRESH_INITIAL_DELAY_FRAMES);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay) {
        scheduleWorldTerrainRefresh(fullRendererReset, vanillaReload, initialDelay, WORLD_LOAD_TERRAIN_REFRESH_ATTEMPTS);
    }

    protected void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay, int attempts) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null ? safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        int delay = Math.max(0, initialDelay);
        int refreshAttempts = Math.max(1, attempts);
        if (pendingWorldTerrainRefreshAttempts > 0 && pendingWorldTerrainRefreshDimension == dimension) {
            logTerrainDiagnostic("schedule-world-terrain:coalesce",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "fullReset=" + fullRendererReset
                            + ", vanillaReload=" + vanillaReload
                            + ", initialDelay=" + delay
                            + ", requestedAttempts=" + refreshAttempts
                            + ", oldAttempts=" + pendingWorldTerrainRefreshAttempts
                            + ", oldDelay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshAttempts = Math.max(pendingWorldTerrainRefreshAttempts, refreshAttempts);
            pendingWorldTerrainRefreshDelay = Math.min(pendingWorldTerrainRefreshDelay, delay);
            pendingWorldTerrainRendererReset |= fullRendererReset;
            pendingWorldTerrainFullRendererReset |= fullRendererReset;
            pendingWorldTerrainVanillaReload |= vanillaReload;
            return;
        }

        pendingWorldTerrainRefreshAttempts = refreshAttempts;
        pendingWorldTerrainRefreshDelay = delay;
        pendingWorldTerrainRefreshDimension = dimension;
        pendingWorldTerrainRendererReset = fullRendererReset;
        pendingWorldTerrainFullRendererReset = fullRendererReset;
        pendingWorldTerrainVanillaReload = vanillaReload;
        logTerrainDiagnostic("schedule-world-terrain:new",
                mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                "fullReset=" + fullRendererReset + ", vanillaReload=" + vanillaReload + ", initialDelay=" + delay + ", attempts=" + refreshAttempts);
    }

    public void clearScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts > 0) {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            logTerrainDiagnostic("schedule-world-terrain:clear",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts
                            + ", delay=" + pendingWorldTerrainRefreshDelay
                            + ", dim=" + pendingWorldTerrainRefreshDimension
                            + ", rendererReset=" + pendingWorldTerrainRendererReset
                            + ", fullReset=" + pendingWorldTerrainFullRendererReset
                            + ", vanillaReload=" + pendingWorldTerrainVanillaReload);
        }
        pendingWorldTerrainRefreshAttempts = 0;
        pendingWorldTerrainRefreshDelay = 0;
        pendingWorldTerrainRefreshDimension = Integer.MIN_VALUE;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainFullRendererReset = false;
        pendingWorldTerrainVanillaReload = false;
    }

    public void queueShaderChunkRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null || !isPipelineActive) {
            return;
        }

        ShaderChunkRefresh refresh = new ShaderChunkRefresh(world, chunkX, chunkZ);
        synchronized (pendingShaderChunkRefreshes) {
            if (pendingShaderChunkRefreshes.contains(refresh)) {
                return;
            }
            if (pendingShaderChunkRefreshes.size() >= MAX_PENDING_SHADER_CHUNK_REFRESHES) {
                ShaderChunkRefresh oldest = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(oldest);
            }
            pendingShaderChunkRefreshes.add(refresh);
        }
    }

    public void queueClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ, String reason) {
        String normalizedReason = reason != null ? reason : "unknown";
        if (world == null || !shouldQueueClientChunkRenderRefresh(world, normalizedReason)) {
            return;
        }

        synchronized (pendingClientChunkRenderRefreshes) {
            long chunkKey = clientChunkRenderRefreshChunkKey(chunkX, chunkZ);
            if ("chunk-data".equals(normalizedReason)
                    || CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(normalizedReason)
                    || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(normalizedReason)) {
                forgetRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey);
            } else if (isRecentlyCompletedClientChunkRenderRefreshLocked(world, chunkKey)) {
                return;
            }
            Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(world);
            ClientChunkRenderRefresh existing = worldLookup != null ? worldLookup.get(chunkKey) : null;
            if (existing != null) {
                mergeClientChunkRenderRefresh(existing, normalizedReason);
                return;
            }
            if (pendingClientChunkRenderRefreshes.size() >= MAX_PENDING_CLIENT_CHUNK_RENDER_REFRESHES) {
                ClientChunkRenderRefresh oldest = pendingClientChunkRenderRefreshes.iterator().next();
                removePendingClientChunkRenderRefreshLocked(oldest);
            }
            ClientChunkRenderRefresh refresh = new ClientChunkRenderRefresh(
                    world,
                    chunkX,
                    chunkZ,
                    normalizedReason,
                    CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS,
                    clientChunkRenderRefreshInitialDelay(normalizedReason)
            );
            addPendingClientChunkRenderRefreshLocked(refresh);
        }
    }

    protected void mergeClientChunkRenderRefresh(ClientChunkRenderRefresh existing, String reason) {
        if (existing == null) {
            return;
        }
        existing.attemptsRemaining = Math.max(existing.attemptsRemaining, CLIENT_CHUNK_RENDER_REFRESH_ATTEMPTS);
        if ("chunk-data".equals(reason)
                || CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(reason)
                || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(reason)) {
            existing.reason = reason;
            existing.delayFrames = Math.min(existing.delayFrames, clientChunkRenderRefreshInitialDelay(reason));
        } else {
            existing.delayFrames = Math.min(existing.delayFrames, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
        }
    }

    protected int clientChunkRenderRefreshInitialDelay(String reason) {
        return PipelineClientChunkRefreshPolicy.initialDelay(reason, CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE,
                CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM, CLIENT_CHUNK_RENDER_REFRESH_INITIAL_DELAY_FRAMES);
    }

    protected static long clientChunkRenderRefreshChunkKey(int chunkX, int chunkZ) {
        return PipelineClientChunkRefreshPolicy.chunkKey(chunkX, chunkZ);
    }

    public void clearPendingShaderChunkRefreshes() {
        synchronized (pendingShaderChunkRefreshes) {
            pendingShaderChunkRefreshes.clear();
        }
    }

    public void clearPendingClientChunkRenderRefreshes() {
        synchronized (pendingClientChunkRenderRefreshes) {
            pendingClientChunkRenderRefreshes.clear();
            pendingClientChunkRenderRefreshLookupByWorld.clear();
            pendingClientChunkRenderRefreshesByWorld.clear();
            recentlyCompletedClientChunkRenderRefreshes.clear();
            recentlyCompletedClientChunkRenderRefreshLastPruneFrame = Long.MIN_VALUE;
        }
    }

    public void runPendingShaderChunkRefreshes() {
        if (!isPipelineActive) {
            clearPendingShaderChunkRefreshes();
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        for (int i = 0; i < MAX_SHADER_CHUNK_REFRESHES_PER_FRAME; i++) {
            ShaderChunkRefresh refresh;
            synchronized (pendingShaderChunkRefreshes) {
                if (pendingShaderChunkRefreshes.isEmpty()) {
                    return;
                }
                refresh = pendingShaderChunkRefreshes.iterator().next();
                pendingShaderChunkRefreshes.remove(refresh);
            }

            refreshShaderChunk(mc, refresh);
        }
    }

    public void runPendingClientChunkRenderRefreshes() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        runPendingClientChunkRenderRefreshesForWorld(mc, com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
    }

    public void runPendingClientChunkRenderRefreshesForCurrentRenderPass() {
        if (!BetterPortalsCompat.isInstalled() || !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null || renderPassWorld == null) {
            return;
        }

        runPendingClientChunkRenderRefreshesForWorld(mc, renderPassWorld, false);
    }

    protected void runPendingClientChunkRenderRefreshesForWorld(Minecraft mc, WorldClient targetWorld,
                                                              boolean advanceDelays) {
        if (targetWorld == null) {
            return;
        }
        if (advanceDelays) {
            ageStaleClientChunkRenderRefreshes(targetWorld);
        }
        for (int i = 0; i < MAX_CLIENT_CHUNK_RENDER_REFRESHES_PER_FRAME; i++) {
            ClientChunkRenderRefresh refresh = pollDueClientChunkRenderRefresh(targetWorld, advanceDelays);
            if (refresh == null) {
                return;
            }

            boolean retryNeeded = refreshClientChunkRender(mc, refresh, targetWorld);
            refresh.attemptsRemaining--;
            if (retryNeeded && refresh.attemptsRemaining > 0 && refresh.world == targetWorld) {
                refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                synchronized (pendingClientChunkRenderRefreshes) {
                    addPendingClientChunkRenderRefreshLocked(refresh);
                }
            }
        }
    }

    protected ClientChunkRenderRefresh pollDueClientChunkRenderRefresh(WorldClient targetWorld, boolean advanceDelays) {
        synchronized (pendingClientChunkRenderRefreshes) {
            LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(targetWorld);
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                return null;
            }
            Iterator<ClientChunkRenderRefresh> iterator = worldRefreshes.iterator();
            while (iterator.hasNext()) {
                ClientChunkRenderRefresh refresh = iterator.next();
                if (refresh.delayFrames > 0) {
                    if (advanceDelays) {
                        refresh.delayFrames--;
                    }
                    continue;
                }
                iterator.remove();
                if (worldRefreshes.isEmpty()) {
                    pendingClientChunkRenderRefreshesByWorld.remove(targetWorld);
                }
                pendingClientChunkRenderRefreshes.remove(refresh);
                removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                return refresh;
            }
        }
        return null;
    }

    protected void ageStaleClientChunkRenderRefreshes(WorldClient activeWorld) {
        if (activeWorld == null) {
            return;
        }

        int aged = 0;
        synchronized (pendingClientChunkRenderRefreshes) {
            Iterator<Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>>> worldIterator =
                    pendingClientChunkRenderRefreshesByWorld.entrySet().iterator();
            while (worldIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                Map.Entry<WorldClient, LinkedHashSet<ClientChunkRenderRefresh>> entry = worldIterator.next();
                WorldClient refreshWorld = entry.getKey();
                LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = entry.getValue();
                if (refreshWorld == activeWorld || worldRefreshes == null || worldRefreshes.isEmpty()) {
                    if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                        worldIterator.remove();
                    }
                    continue;
                }

                Iterator<ClientChunkRenderRefresh> refreshIterator = worldRefreshes.iterator();
                while (refreshIterator.hasNext() && aged < MAX_STALE_CLIENT_CHUNK_REFRESHES_AGED_PER_FRAME) {
                    ClientChunkRenderRefresh refresh = refreshIterator.next();
                    if (refresh == null || refresh.world == null) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                        aged++;
                        continue;
                    }

                    aged++;
                    if (refresh.delayFrames > 0) {
                        refresh.delayFrames--;
                        continue;
                    }

                    refresh.attemptsRemaining--;
                    if (refresh.attemptsRemaining <= 0 || !shouldRetainOffWorldClientChunkRefresh(refresh)) {
                        refreshIterator.remove();
                        pendingClientChunkRenderRefreshes.remove(refresh);
                        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
                    } else {
                        refresh.delayFrames = CLIENT_CHUNK_RENDER_REFRESH_REPEAT_DELAY_FRAMES;
                    }
                }
                if (worldRefreshes.isEmpty()) {
                    worldIterator.remove();
                }
            }
            pruneRecentlyCompletedClientChunkRenderRefreshesLocked();
        }
    }

    protected void addPendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !pendingClientChunkRenderRefreshes.add(refresh)) {
            return;
        }
        pendingClientChunkRenderRefreshLookupByWorld
                .computeIfAbsent(refresh.world, ignored -> new HashMap<>())
                .put(clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ), refresh);
        pendingClientChunkRenderRefreshesByWorld
                .computeIfAbsent(refresh.world, ignored -> new LinkedHashSet<>())
                .add(refresh);
    }

    protected void removePendingClientChunkRenderRefreshLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null) {
            return;
        }
        pendingClientChunkRenderRefreshes.remove(refresh);
        removePendingClientChunkRenderRefreshFromLookupLocked(refresh);
        removePendingClientChunkRenderRefreshFromWorldBucketLocked(refresh);
    }

    protected void removePendingClientChunkRenderRefreshFromLookupLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        Map<Long, ClientChunkRenderRefresh> worldLookup = pendingClientChunkRenderRefreshLookupByWorld.get(refresh.world);
        if (worldLookup == null) {
            return;
        }
        worldLookup.remove(clientChunkRenderRefreshChunkKey(refresh.chunkX, refresh.chunkZ));
        if (worldLookup.isEmpty()) {
            pendingClientChunkRenderRefreshLookupByWorld.remove(refresh.world);
        }
    }

    protected void removePendingClientChunkRenderRefreshFromWorldBucketLocked(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null) {
            return;
        }
        LinkedHashSet<ClientChunkRenderRefresh> worldRefreshes = pendingClientChunkRenderRefreshesByWorld.get(refresh.world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(refresh);
        if (worldRefreshes.isEmpty()) {
            pendingClientChunkRenderRefreshesByWorld.remove(refresh.world);
        }
    }

    protected boolean shouldRetainOffWorldClientChunkRefresh(ClientChunkRenderRefresh refresh) {
        if (refresh == null || refresh.world == null || !BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    protected boolean isRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        Long expiresAt = worldRefreshes != null ? worldRefreshes.get(chunkKey) : null;
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < pipelineFrameId) {
            worldRefreshes.remove(chunkKey);
            if (worldRefreshes.isEmpty()) {
                recentlyCompletedClientChunkRenderRefreshes.remove(world);
            }
            return false;
        }
        return true;
    }

    protected void rememberCompletedClientChunkRenderRefresh(WorldClient world, int chunkX, int chunkZ) {
        if (world == null) {
            return;
        }
        synchronized (pendingClientChunkRenderRefreshes) {
            Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes
                    .computeIfAbsent(world, ignored -> new LinkedHashMap<>());
            worldRefreshes.put(
                    clientChunkRenderRefreshChunkKey(chunkX, chunkZ),
                    pipelineFrameId + CLIENT_CHUNK_RENDER_REFRESH_RECENT_TTL_FRAMES
            );
            trimRecentlyCompletedClientChunkRenderRefreshesLocked(worldRefreshes);
        }
    }

    protected void forgetRecentlyCompletedClientChunkRenderRefreshLocked(WorldClient world, long chunkKey) {
        Map<Long, Long> worldRefreshes = recentlyCompletedClientChunkRenderRefreshes.get(world);
        if (worldRefreshes == null) {
            return;
        }
        worldRefreshes.remove(chunkKey);
        if (worldRefreshes.isEmpty()) {
            recentlyCompletedClientChunkRenderRefreshes.remove(world);
        }
    }

    protected void pruneRecentlyCompletedClientChunkRenderRefreshesLocked() {
        if (recentlyCompletedClientChunkRenderRefreshLastPruneFrame != Long.MIN_VALUE
                && pipelineFrameId - recentlyCompletedClientChunkRenderRefreshLastPruneFrame < CLIENT_CHUNK_RENDER_REFRESH_RECENT_PRUNE_INTERVAL_FRAMES) {
            return;
        }
        recentlyCompletedClientChunkRenderRefreshLastPruneFrame = pipelineFrameId;
        Iterator<Map.Entry<WorldClient, Map<Long, Long>>> worldIterator =
                recentlyCompletedClientChunkRenderRefreshes.entrySet().iterator();
        while (worldIterator.hasNext()) {
            Map<Long, Long> worldRefreshes = worldIterator.next().getValue();
            if (worldRefreshes == null || worldRefreshes.isEmpty()) {
                worldIterator.remove();
                continue;
            }
            worldRefreshes.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < pipelineFrameId);
            if (worldRefreshes.isEmpty()) {
                worldIterator.remove();
            }
        }
    }

    protected void trimRecentlyCompletedClientChunkRenderRefreshesLocked(Map<Long, Long> worldRefreshes) {
        if (worldRefreshes == null || worldRefreshes.size() <= MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD) {
            return;
        }
        Iterator<Long> iterator = worldRefreshes.keySet().iterator();
        while (worldRefreshes.size() > MAX_RECENT_CLIENT_CHUNK_RENDER_REFRESHES_PER_WORLD && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    protected boolean shouldQueueClientChunkRenderRefresh(WorldClient world, String reason) {
        if ("chunk-data".equals(reason)) {
            return true;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return false;
        }
        if (CLIENT_CHUNK_RENDER_REFRESH_REASON_BLOCK_UPDATE.equals(reason)) {
            return world == com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)
                    && (isPipelineActive || pendingWorldTerrainRefreshAttempts > 0 || NothiriumBypass.shouldBypass());
        }
        if (CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(reason)) {
            return world == com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        }
        if (world == com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) && ("pre-chunk".equals(reason)
                || pendingWorldTerrainRefreshAttempts > 0
                || isPipelineActive
                || NothiriumBypass.shouldBypass())) {
            return true;
        }
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        return world != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView();
    }

    protected boolean refreshClientChunkRender(Minecraft mc, ClientChunkRenderRefresh refresh, WorldClient targetWorld) {
        if (refresh == null || refresh.world == null || targetWorld == null || refresh.world != targetWorld || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return false;
        }

        ChunkProviderClient chunkProvider = com.l.ausm.impl.util.MinecraftReflectionCompat.call((targetWorld), net.minecraft.client.multiplayer.ChunkProviderClient.class, null, new String[] {"func_72863_F", "getChunkProvider"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        Chunk chunk = chunkProvider != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.call((chunkProvider), net.minecraft.world.chunk.Chunk.class, null, new String[] {"func_186026_b", "getLoadedChunk"},
                new Class<?>[] {int.class, int.class}, (refresh.chunkX), (refresh.chunkZ)) : null;
        boolean loaded = chunk != null;
        ClientChunkRenderScheduleResult scheduleResult = ClientChunkRenderScheduleResult.empty();
        if (loaded) {
            ensureVanillaTerrainRenderer(targetWorld, true);
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor accessor) {
                ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
                updateVanillaViewFrustumChunkPositions(
                        viewFrustum,
                        com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc)
                );
                scheduleResult = scheduleLoadedClientChunkRenderChunks(
                        accessor,
                        viewFrustum,
                        targetWorld,
                        chunk,
                        refresh.chunkX,
                        refresh.chunkZ,
                        refresh.nextSectionY,
                        MAX_CLIENT_CHUNK_RENDER_REFRESH_SECTIONS_PER_FRAME
                );
                refresh.nextSectionY = scheduleResult.nextSectionY;
                refresh.coveredSections += scheduleResult.coveredSections;
                if (scheduleResult.completed && refresh.coveredSections < scheduleResult.requiredSections) {
                    refresh.nextSectionY = 0;
                    refresh.coveredSections = 0;
                }
                if (scheduleResult.scheduledChunks > 0) {
                    accessor.ausm$setDisplayListEntitiesDirty(true);
                }
            }

            if ((isPipelineActive || CLIENT_CHUNK_RENDER_REFRESH_REASON_SHADERLESS_BLOOM.equals(refresh.reason))
                    && !refresh.shadowRefreshed
                    && !NothiriumBypass.shouldBypass()) {
                nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
                refresh.shadowRefreshed = true;
            }
        }

        logClientChunkRenderRefresh(refresh, loaded, scheduleResult.scheduledChunks);
        if (loaded && scheduleResult.completed && scheduleResult.coveredSections >= scheduleResult.requiredSections) {
            rememberCompletedClientChunkRenderRefresh(refresh.world, refresh.chunkX, refresh.chunkZ);
        }
        return !loaded || !scheduleResult.completed || refresh.coveredSections < scheduleResult.requiredSections;
    }

    protected ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunks(RenderGlobalAccessor renderGlobal,
                                                                                 ViewFrustum viewFrustum,
                                                                                 World world, Chunk chunk,
                                                                                 int chunkX, int chunkZ,
                                                                                 int startSectionY,
                                                                                 int sectionBudget) {
        int requiredSections = countNonEmptyClientChunkSections(chunk);
        if (requiredSections == 0) {
            return new ClientChunkRenderScheduleResult(0, 0, 0, true, requiredSections);
        }
        RenderChunk[] renderChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderGlobal == null || renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        if (viewFrustum instanceof ViewFrustumAccessor accessor) {
            return scheduleLoadedClientChunkRenderChunksIndexed(accessor, renderChunks,
                    chunksToUpdate, world, chunk, chunkX, chunkZ, requiredSections, startSectionY, sectionBudget);
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = com.l.ausm.impl.util.MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        int sectionCount = sections != null ? sections.length : 0;
        int start = clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            RenderChunk renderChunk = findRenderChunkForSection(renderChunks, chunkX, chunkZ, sectionY);
            processed++;
            if (renderChunk == null) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            covered++;
            assignRenderChunkWorld(renderChunk, world);
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((renderChunk), new String[] {"func_178571_g", "needsUpdate"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((renderChunk), new String[] {"func_178575_a", "setNeedsUpdate"}, new Class<?>[] {boolean.class}, (true));;
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }

    protected ClientChunkRenderScheduleResult scheduleLoadedClientChunkRenderChunksIndexed(ViewFrustumAccessor viewFrustum,
                                                                                        RenderChunk[] renderChunks,
                                                                                        Set<RenderChunk> chunksToUpdate,
                                                                                        World world,
                                                                                        Chunk chunk,
                                                                                        int chunkX,
                                                                                        int chunkZ,
                                                                                        int requiredSections,
                                                                                        int startSectionY,
                                                                                        int sectionBudget) {
        int countX = viewFrustum.ausm$countChunksX();
        int countY = viewFrustum.ausm$countChunksY();
        int countZ = viewFrustum.ausm$countChunksZ();
        if (countX <= 0 || countY <= 0 || countZ <= 0 || renderChunks == null) {
            return ClientChunkRenderScheduleResult.empty();
        }

        int xIndex = floorDiv(chunkX, countX);
        xIndex = chunkX - xIndex * countX;
        if (xIndex < 0) {
            xIndex += countX;
        }
        int zIndex = floorDiv(chunkZ, countZ);
        zIndex = chunkZ - zIndex * countZ;
        if (zIndex < 0) {
            zIndex += countZ;
        }

        int scheduled = 0;
        int covered = 0;
        int processed = 0;
        int maxSections = maxClientChunkRefreshSections(sectionBudget);
        ExtendedBlockStorage[] sections = com.l.ausm.impl.util.MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        int sectionCount = Math.min(countY, sections != null ? sections.length : 0);
        int start = clampSectionCursor(startSectionY, sectionCount);
        for (int sectionY = start; sectionY < sectionCount; sectionY++) {
            if (!hasNonEmptyClientChunkSection(sections, sectionY)) {
                continue;
            }
            int index = (zIndex * countY + sectionY) * countX + xIndex;
            if (index < 0 || index >= renderChunks.length) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }

            RenderChunk renderChunk = renderChunks[index];
            BlockPos position = renderChunk != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkPosition(renderChunk) : null;
            if (renderChunk == null
                    || position == null
                    || (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(position) >> 4) != chunkX
                    || (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(position) >> 4) != chunkZ
                    || !shouldScheduleLoadedClientRenderChunk(renderChunk, chunk, position)) {
                processed++;
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            processed++;
            covered++;
            assignRenderChunkWorld(renderChunk, world);
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((renderChunk), new String[] {"func_178571_g", "needsUpdate"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false) || chunksToUpdate.contains(renderChunk)) {
                if (processed >= maxSections) {
                    return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
                }
                continue;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((renderChunk), new String[] {"func_178575_a", "setNeedsUpdate"}, new Class<?>[] {boolean.class}, (true));;
            chunksToUpdate.add(renderChunk);
            scheduled++;
            if (processed >= maxSections) {
                return new ClientChunkRenderScheduleResult(scheduled, covered, sectionY + 1, false, requiredSections);
            }
        }
        return new ClientChunkRenderScheduleResult(scheduled, covered, 0, true, requiredSections);
    }

    protected int countNonEmptyClientChunkSections(Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        ExtendedBlockStorage[] sections = com.l.ausm.impl.util.MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        if (sections == null) {
            return 0;
        }
        int count = 0;
        for (ExtendedBlockStorage section : sections) {
            if (!com.l.ausm.impl.util.MinecraftReflectionCompat.blockStorageEmpty(section)) {
                count++;
            }
        }
        return count;
    }

    protected boolean shouldScheduleLoadedClientRenderChunk(RenderChunk renderChunk, Chunk chunk, BlockPos position) {
        if (renderChunk == null || chunk == null || position == null) {
            return false;
        }

        int sectionY = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(position) >> 4;
        ExtendedBlockStorage[] sections = com.l.ausm.impl.util.MinecraftReflectionCompat.chunkBlockStorageArray(chunk);
        if (sectionY < 0 || sections == null || sectionY >= sections.length) {
            return false;
        }

        ExtendedBlockStorage section = sections[sectionY];
        return !com.l.ausm.impl.util.MinecraftReflectionCompat.blockStorageEmpty(section);
    }

    protected static int maxClientChunkRefreshSections(int sectionBudget) {
        return Math.max(1, sectionBudget);
    }

    protected static int clampSectionCursor(int sectionY, int sectionCount) {
        if (sectionY < 0 || sectionY >= sectionCount) {
            return 0;
        }
        return sectionY;
    }

    protected static boolean hasNonEmptyClientChunkSection(ExtendedBlockStorage[] sections, int sectionY) {
        return sections != null
                && sectionY >= 0
                && sectionY < sections.length
                && !com.l.ausm.impl.util.MinecraftReflectionCompat.blockStorageEmpty(sections[sectionY]);
    }

    protected RenderChunk findRenderChunkForSection(RenderChunk[] renderChunks, int chunkX, int chunkZ, int sectionY) {
        if (renderChunks == null) {
            return null;
        }
        for (RenderChunk renderChunk : renderChunks) {
            BlockPos position = renderChunk != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkPosition(renderChunk) : null;
            if (position != null
                    && (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(position) >> 4) == chunkX
                    && (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(position) >> 4) == chunkZ
                    && (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(position) >> 4) == sectionY) {
                return renderChunk;
            }
        }
        return null;
    }

    protected void logClientChunkRenderRefresh(ClientChunkRenderRefresh refresh, boolean loaded, int scheduledChunks) {
        if (clientChunkRenderRefreshLogs >= MAX_CLIENT_CHUNK_RENDER_REFRESH_LOGS) {
            return;
        }
        clientChunkRenderRefreshLogs++;
        MainMod.LOGGER.info(
                "[AUSMClientChunkRefresh] call={} reason={} world={} chunk={},{} loaded={} scheduledChunks={} attemptsLeft={} active={} bypass={} bp={}",
                clientChunkRenderRefreshLogs,
                refresh.reason,
                safeDimensionId(refresh.world),
                refresh.chunkX,
                refresh.chunkZ,
                loaded,
                scheduledChunks,
                refresh.attemptsRemaining,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.describeTransitionState()
        );
    }

    protected void refreshShaderChunk(Minecraft mc, ShaderChunkRefresh refresh) {
        if (refresh == null || refresh.world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(refresh.world) == null) {
            return;
        }

        if (com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != refresh.world) {
            return;
        }

        if (!NothiriumBypass.shouldBypass()) {
            nothiriumShadowRenderer.refreshChunkColumn(refresh.chunkX, refresh.chunkZ);
        }
    }

    public void scheduleBloomTerrainRefresh(String reason) {
        if (!AusmBloomLayer.isAvailable()) {
            return;
        }
        pendingBloomTerrainRefreshAttempts = Math.max(pendingBloomTerrainRefreshAttempts, 3);
        if (pendingBloomTerrainRefreshDelay <= 0) {
            pendingBloomTerrainRefreshDelay = 1;
        }
        pendingBloomTerrainRefreshReason = reason != null && !reason.isEmpty() ? reason : "unspecified";
    }

    public void clearScheduledBloomTerrainRefresh() {
        pendingBloomTerrainRefreshAttempts = 0;
        pendingBloomTerrainRefreshDelay = 0;
        pendingBloomTerrainRefreshReason = "";
        bloomZeroGeometryFrames = 0;
        bloomZeroGeometryRefreshCooldown = 0;
    }

    public void runScheduledBloomTerrainRefresh() {
        if (bloomZeroGeometryRefreshCooldown > 0) {
            bloomZeroGeometryRefreshCooldown--;
        }
        if (pendingBloomTerrainRefreshAttempts <= 0) {
            return;
        }
        if (pendingBloomTerrainRefreshDelay > 0) {
            pendingBloomTerrainRefreshDelay--;
            return;
        }

        pendingBloomTerrainRefreshAttempts--;
        pendingBloomTerrainRefreshDelay = 20;
        if (refreshBloomTerrainState(pendingBloomTerrainRefreshReason)
                && pendingBloomTerrainRefreshAttempts <= 0) {
            pendingBloomTerrainRefreshReason = "";
        }
    }

    public void runScheduledWorldLoadLightRecalculation() {
        if (pendingWorldLoadLightRecalculationAttempts <= 0) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int dimension = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null ? safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE;
        if (pendingWorldLoadLightRecalculationDimension != Integer.MIN_VALUE
                && pendingWorldLoadLightRecalculationDimension != dimension) {
            clearScheduledWorldLoadLightRecalculation();
            return;
        }
        if (pendingWorldLoadLightRecalculationDelay > 0) {
            pendingWorldLoadLightRecalculationDelay--;
            return;
        }

        pendingWorldLoadLightRecalculationAttempts--;
        pendingWorldLoadLightRecalculationDelay = WORLD_LOAD_FORCE_LIGHT_RECALC_DELAY_FRAMES;
        if (refreshWorldLoadLightState()) {
            pendingWorldLoadLightRecalculationAttempts = 0;
            pendingWorldLoadLightRecalculationDelay = 0;
            pendingWorldLoadLightRecalculationDimension = Integer.MIN_VALUE;
            MainMod.LOGGER.info("[Lighting] Refreshed scheduled world-load light state.");
        }
    }

    public void runRenderDistanceChangeCheck() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) == null) {
            lastObservedRenderDistanceChunks = -1;
            return;
        }

        int renderDistanceChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc);
        if (renderDistanceChunks <= 0) {
            return;
        }
        if (lastObservedRenderDistanceChunks < 0) {
            lastObservedRenderDistanceChunks = renderDistanceChunks;
            return;
        }
        if (lastObservedRenderDistanceChunks == renderDistanceChunks) {
            return;
        }

        int previousRenderDistanceChunks = lastObservedRenderDistanceChunks;
        lastObservedRenderDistanceChunks = renderDistanceChunks;
        MainMod.LOGGER.info("[Pipeline] Render distance changed: old={} new={}; forcing terrain renderer reload.",
                previousRenderDistanceChunks,
                renderDistanceChunks);
        forceRenderDistanceTerrainReload(mc, previousRenderDistanceChunks, renderDistanceChunks);
        scheduleWorldLoadLightRecalculation();
    }

    protected void forceRenderDistanceTerrainReload(Minecraft mc, int previousRenderDistanceChunks, int renderDistanceChunks) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        clearScheduledWorldTerrainRefresh();
        clearPendingShaderChunkRefreshes();
        clearPendingClientChunkRenderRefreshes();
        deleteCachedVanillaTerrainRenderers();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        resetCameraFrustumSyncState();
        boolean nothiriumRecreated = NothiriumBypass.recreateRenderer();
        com.l.ausm.impl.util.MinecraftReflectionCompat.loadRenderers(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc));
        rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "render-distance-change");
        NothiriumBypass.markAllChanged();
        scheduleInactiveVanillaRecoveryFrame();
        MainMod.LOGGER.info("[Pipeline] Forced terrain renderer reload for render distance change: world={} old={} new={} nothiriumRecreated={}",
                safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)),
                previousRenderDistanceChunks,
                renderDistanceChunks,
                nothiriumRecreated);
    }

    public void runScheduledWorldTerrainRefresh() {
        if (pendingWorldTerrainRefreshAttempts <= 0) {
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive() && mc != null) {
            BetterPortalsCompat.keepMainViewSwapRecoveryAlive(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
        }
        if (pendingWorldTerrainRefreshDelay > 0) {
            logTerrainDiagnostic("run-world-terrain:delay",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
            pendingWorldTerrainRefreshDelay--;
            return;
        }

        logTerrainDiagnostic("run-world-terrain:start",
                mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                "attempts=" + pendingWorldTerrainRefreshAttempts);
        if (refreshWorldTerrainState()) {
            pendingWorldTerrainRefreshAttempts--;
        }

        if (pendingWorldTerrainRefreshAttempts <= 0) {
            logTerrainDiagnostic("run-world-terrain:done",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "");
            clearScheduledWorldTerrainRefresh();
        } else {
            pendingWorldTerrainRefreshDelay = WORLD_LOAD_TERRAIN_REFRESH_REPEAT_DELAY_FRAMES;
            logTerrainDiagnostic("run-world-terrain:reschedule",
                    mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null,
                    "attempts=" + pendingWorldTerrainRefreshAttempts + ", delay=" + pendingWorldTerrainRefreshDelay);
        }
    }

    protected boolean refreshBloomTerrainState(String reason) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }
        if (!AusmBloomLayer.isAvailable() || !bloomRenderer.hasBloomResources()) {
            return false;
        }

        BlockPos center = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        int radius = Math.max(64, Math.min(512, (com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc) * 16) + 16));
        runningBloomTerrainRefresh = true;
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) - radius,
                    0,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) - radius,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) + radius,
                    255,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) + radius
            );
        } finally {
            runningBloomTerrainRefresh = false;
        }
        boolean nothiriumDirty = NothiriumBypass.markAllChanged();

        return true;
    }

    protected boolean refreshWorldTerrainState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        int dimension = safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
        if (pendingWorldTerrainRefreshDimension != Integer.MIN_VALUE
                && pendingWorldTerrainRefreshDimension != dimension) {
            logTerrainDiagnostic("refresh-world-terrain:dimension-mismatch", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                    "pendingDim=" + pendingWorldTerrainRefreshDimension + ", currentDim=" + dimension);
            clearScheduledWorldTerrainRefresh();
            return false;
        }

        boolean rendererReset = pendingWorldTerrainRendererReset;
        boolean vanillaReload = pendingWorldTerrainVanillaReload;
        pendingWorldTerrainRendererReset = false;
        pendingWorldTerrainVanillaReload = false;

        if (pendingWorldTerrainFullRendererReset) {
            pendingWorldTerrainFullRendererReset = false;
            logTerrainDiagnostic("refresh-world-terrain:full-reset", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                    "rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
            if (rendererReset) {
                deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
            }
            rebuildTerrainRenderers(updateNothiriumPipelineBlockFormatMode(), vanillaReload);
            scheduleInactiveVanillaRecoveryFrame();
            return true;
        }

        ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
        BlockPos center = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        int radius = Math.max(32, Math.min(128, com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc) * 16));
        logTerrainDiagnostic("refresh-world-terrain:range", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                "center=" + center + ", radius=" + radius + ", rendererReset=" + rendererReset + ", vanillaReload=" + vanillaReload);
        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) - radius,
                0,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) - radius,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) + radius,
                255,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) + radius
        );
        if (isPipelineActive || NothiriumBypass.shouldBypass()) {
            NothiriumBypass.markAllChanged();
            scheduleInactiveVanillaRecoveryFrame();
        }
        return true;
    }

    protected boolean refreshWorldLoadLightState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        refreshVanillaLightmap(mc);
        if (!isPipelineActive) {
            return true;
        }
        BlockPos center = new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc));
        int radius = WORLD_LOAD_LIGHT_REFRESH_RADIUS;
        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) - radius,
                Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) - radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) - radius,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(center) + radius,
                Math.min(255, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(center) + radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(center) + radius
        );
        return true;
    }

    protected int forceChunkLightingRefresh(World world, int minX, int maxX, int minZ, int maxZ) {
        return PipelineLightingRefresh.refreshChunks(world, minX, maxX, minZ, maxZ);
    }

    protected int forceBlockLightingRefresh(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return PipelineLightingRefresh.refreshBlocks(world, minX, minY, minZ, maxX, maxY, maxZ,
                this::refreshSyntheticLightCandidate);
    }

    protected void resetPipelineState() {
        resetPipelineState(null);
    }

    protected void resetPipelineState(Framebuffer preferredTarget) {
        activePass = null;
        activeShaderKey = null;
        activePhase = WorldRenderingPhase.NONE;
        overridePhase = null;
        worldFrameActive = false;
        shaderlessWorldPassActive = false;
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        renderingShadowMap = false;
        renderingGui = false;
        guiTargetContentFrame = Long.MIN_VALUE;
        currentWorldPassSerial = Long.MIN_VALUE;
        worldPassSerialStack.clear();
        nothiriumPipelineTranslucentFrameStack.clear();
        nothiriumPipelineTranslucentWorldPassSerialStack.clear();
        clearNothiriumPipelineTranslucentBridge();
        nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;
        guiRenderDepth = 0;
        bloomLayerRenderedThisWorldPass = false;
        shaderlessStyleBloomRenderedThisWorldPass = false;
        pendingDeferredNativeBloom = false;
        bloomRenderer.clearPendingLayerBloom();
        passStack.clear();
        worldPassBypassStack.clear();
        untouchedBetterPortalsVanillaRendererStack.clear();
        currentEntityId = 0;
        currentEntityKey = null;
        currentEntityColor = new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        restoreTerrainCulling();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        resetShaderResourceBindings();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        resetPortalMaskState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        for (int i = 0; i < maxDrawBuffers(); i++) {
            setIndexedBlend(i, false);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        Framebuffer target = preferredTarget != null ? preferredTarget : mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) : null;
        if (target != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, mc), framebufferHeight(target, mc));
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), 0);
            GL11.glDrawBuffer(GL11.GL_BACK);
            GL11.glReadBuffer(GL11.GL_BACK);
        }
        externalWorldFramebufferTarget = null;
        restoreVanillaTextureBindingsAfterPipeline();
        refreshVanillaLightmap(mc);
        disableVanillaLightmap(mc);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void resetShaderResourceBindings() {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        TextureBinder.unbindAllTextureTargets();
        unbindShaderImages();
        unbindShaderStorageBuffers(true);
        disablePipelineVertexAttributes();
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
    }

    protected void restoreVanillaTextureBindingsAfterPipeline() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            TextureBinder.restoreDefaultTextureUnit();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
            return;
        }

        restoreVanillaLightmapTexture(mc);

        TextureBinder.restoreDefaultTextureUnit();
        TextureManager textureManager = com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc);
        if (textureManager != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
            ITextureObject atlasTexture = com.l.ausm.impl.util.MinecraftReflectionCompat.texture(textureManager, com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());
            if (atlasTexture != null) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, com.l.ausm.impl.util.MinecraftReflectionCompat.glTextureId(atlasTexture));
            }
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void restoreVanillaLightmapTexture(Minecraft mc) {
        PipelineVanillaLightmapState.restore(mc);
    }

    protected void refreshVanillaLightmap(Minecraft mc) {
        PipelineVanillaLightmapState.refresh(mc);
    }

    protected void disableVanillaLightmap(Minecraft mc) {
        PipelineVanillaLightmapState.disable(mc);
    }

    protected void unbindShaderStorageBuffers() {
        unbindShaderStorageBuffers(false);
    }

    protected void unbindShaderStorageBuffers(boolean force) {
        if (!GLContext.getCapabilities().OpenGL43) {
            return;
        }
        if (!force && shaderStorageBuffersKnownUnbound()) {
            return;
        }

        if (force || !shaderStorageBuffers.active()) {
            int maxBindings = maxShaderStorageBufferBindings();
            for (int index = 0; index < maxBindings; index++) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        } else {
            for (int index : shaderStorageBuffers.bindingIndices()) {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
            }
        }
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
        markShaderStorageBuffersUnbound();
    }

    protected static ShaderProperties emptyShaderProperties() {
        return new ShaderProperties(
                Map.of(),
                Map.of(),
                com.l.ausm.api.pipeline.pack.ShaderOptions.empty(),
                Map.of(),
                Map.of(),
                ShaderRenderTargetSettings.empty(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new ShaderBlockIdMap.BlockIdRules(Map.of(), List.of(), Map.of()),
                Map.of(),
                new ShaderItemIdMap.ItemIdRules(Map.of(), Map.of()),
                com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                ShaderTextureDirectives.empty(),
                CustomUniformSet.empty(),
                new ShaderPackDirectives(
                        ShaderRenderTargetSettings.empty(),
                        com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                        ShaderTextureDirectives.empty(),
                        ShaderComputeDirectives.empty(),
                        List.of(),
                        Map.of(),
                        ShaderFeatureSet.empty(),
                        256,
                        null,
                        ShaderPipelineCapabilities.from(new ShaderPackDirectives(
                                ShaderRenderTargetSettings.empty(),
                                com.l.ausm.api.pipeline.pack.ShaderRenderSettings.defaults(),
                                ShaderTextureDirectives.empty(),
                                ShaderComputeDirectives.empty(),
                                List.of(),
                                Map.of(),
                                ShaderFeatureSet.empty(),
                                256,
                                null,
                                null,
                                Map.of(),
                                CustomUniformSet.empty()
                        )),
                        Map.of(),
                        CustomUniformSet.empty()
                ),
                ShaderOitSettings.empty(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }
}
