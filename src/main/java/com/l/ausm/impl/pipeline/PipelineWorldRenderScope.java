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
import net.minecraft.util.math.MathHelper;
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
abstract class PipelineWorldRenderScope extends PipelineRuntimeState {
    public void beginFrame() {
        if (!isPipelineActive) {
            externalWorldFramebufferTarget = null;
            return;
        }
        currentWorldFrameStartNanos = System.nanoTime();
        currentWorldFrameReadyNanos = Long.MIN_VALUE;
        currentWorldFrameFinishStartNanos = Long.MIN_VALUE;
        currentWorldFrameAfterNativeBloomNanos = Long.MIN_VALUE;
        currentWorldFrameBlitStartNanos = Long.MIN_VALUE;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            externalWorldFramebufferTarget = null;
            return;
        }
        worldFrameActive = true;
        externalWorldFramebufferTarget = BetterPortalsCompat.currentShaderRenderPassFramebuffer();
        boolean betterPortalsExternalTarget = isBetterPortalsExternalWorldTarget();
        int targetWidth = worldTargetWidth(mc);
        int targetHeight = worldTargetHeight(mc);
        clearWorldLoadPresentationFramebuffer(mc);
        logBetterPortalsPipeline("begin-frame:target", "target=" + targetWidth + "x" + targetHeight
                + ", external=" + betterPortalsExternalTarget);
        if (pingPongManager.width() != targetWidth || pingPongManager.height() != targetHeight) {
            logBetterPortalsPipeline("begin-frame:resize", "old=" + pingPongManager.width() + "x" + pingPongManager.height()
                    + ", new=" + targetWidth + "x" + targetHeight);
            resizeFramebuffer(targetWidth, targetHeight, true);
        }

        if (betterPortalsExternalTarget) {
            currentFrameTime = 0.0f;
        } else {
            long now = System.nanoTime();
            currentFrameTime = Math.min(Math.max((now - lastPipelineFrameNanos) / 1_000_000_000.0f, 0.001f), 1.0f);
            lastPipelineFrameNanos = now;
            pipelineFrameId++;
            frameTimeCounter += currentFrameTime;
            if (frameTimeCounter >= 3600.0f) {
                frameTimeCounter = 0.0f;
            }
        }
        deferredPassesRenderedThisFrame = false;
        preparePassesRenderedBeforeShadowThisFrame = false;
        preTranslucentDepthCopiedThisFrame = false;
        preHandDepthCopiedThisFrame = false;
        clearDirectRecoveredWindowSource();
        if (nothiriumShadowSuppressedFrames > 0) {
            nothiriumShadowSuppressedFrames--;
        }
        clearShaderedNothiriumGlobalBypassState(false);
        updateCameraPosition(mc);
        logHeldColoredLightProbe(mc);
        refreshHardwareSafeVanillaTerrainForCamera(mc);
        boolean resetTemporalHistory = shouldResetTemporalHistory(mc, betterPortalsExternalTarget);
        if (betterPortalsExternalTarget) {
            System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
            System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);
        } else {
            updateSmoothedFrameTime();
            updateSmoothedEyeBrightness(mc);
            updateSmoothedWetness(mc);
            updateEndFlashState(mc);
        }
        pingPongManager.beginFrameWithInitialTarget(fallbackColorAttachment(), frameClearAttachments(resetTemporalHistory));
        logTemporalHistoryResetIfNeeded(resetTemporalHistory);
        runSetupComputesIfNeeded();
        runFullscreenPasses(ProgramArrayId.BEGIN);
        bindWorldFramebuffer();
        currentWorldFrameReadyNanos = System.nanoTime();
        logBetterPortalsPipeline("begin-frame:ready");
    }

    public void beginClientRenderFrame(long frameNanos) {
        boolean newFrame = frameNanos != clientRenderFrameNanos;
        if (newFrame) {
            clientRenderFrameNanos = frameNanos;
            if (!isPipelineActive) {
                pipelineFrameId++;
            }
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            shaderlessCustomSkyBackingThisFrame = shouldRenderShaderlessCustomSkyBackingNow(mc);
            bloomLayerRenderedThisWorldFrame = false;
            shaderlessStyleBloomRenderedThisWorldFrame = false;
            shaderlessBloomRenderedThisWorldFrame = false;
            resetShaderlessTerrainLayerCounts();
            if (vanillaParticleRecoveryFrames > 0) {
                vanillaParticleRecoveryFrames--;
            }
            if (nothiriumHybridVanillaMaintenanceFrames > 0) {
                nothiriumHybridVanillaMaintenanceFrames--;
                if (nothiriumHybridVanillaMaintenanceFrames == 0) {
                    nothiriumHybridVanillaMaintenanceReason = "";
                }
            }
            if (nothiriumMainVanillaDrawPathFrames > 0) {
                nothiriumMainVanillaDrawPathFrames--;
                if (nothiriumMainVanillaDrawPathFrames == 0) {
                    nothiriumMainVanillaDrawPathReason = "";
                }
            }
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
        int targetFramebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(target, false);
            GL11.glDrawBuffer(targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
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

    public void beginWorldPassRendering(int pass, float partialTicks) {
        if (clientRenderFrameNanos == Long.MIN_VALUE) {
            beginClientRenderFrame(System.nanoTime());
        }
        refreshShaderlessVoidWorldSkyLightEligibility();
        beginWorldPassDuplicateTracking();
        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        bloomLayerRenderedThisWorldPass = bloomLayerRenderedThisWorldFrame;
        shaderlessStyleBloomRenderedThisWorldPass = shaderlessStyleBloomRenderedThisWorldFrame;
        shaderlessBloomRenderedThisWorldPass = shaderlessBloomRenderedThisWorldFrame;
        boolean bypass = computeShouldBypassWorldPassRendering();
        worldPassBypassStack.push(bypass);
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-begin bypass=" + bypass);
        logBetterPortalsPipeline("world-pass-begin", "pass=" + pass + ", bypass=" + bypass);
        if (bypass) {
            prepareBypassedWorldPassRendering();
            return;
        }

        if (!isPipelineActive) {
            beginShaderlessWorldPassRendering();
            return;
        }

        beginFrame();
    }

    public void finishWorldPassRendering() {
        boolean bypass = worldPassBypassStack.isEmpty()
                ? computeShouldBypassWorldPassRendering()
                : worldPassBypassStack.pop();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:world-pass-finish bypass=" + bypass);
        logBetterPortalsPipeline("world-pass-finish", "bypass=" + bypass);
        try {
            if (bypass) {
                finishBypassedWorldPassRendering();
                return;
            }

            if (!isPipelineActive) {
                finishShaderlessWorldPassRendering();
                return;
            }

            currentWorldFrameFinishStartNanos = System.nanoTime();
            renderNativeBloomLayerIfNeeded();
            currentWorldFrameAfterNativeBloomNanos = System.nanoTime();
            blitWorldFramebufferToMinecraft();
        } finally {
            finishWorldPassDuplicateTracking();
        }
    }

    protected void beginShaderlessWorldPassRendering() {
        prepareInactiveVanillaFrame();
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        clearWorldLoadPresentationFramebuffer(mc);
        shaderlessWorldPassActive = true;
        restoreVanillaWorldPassState(true, true);
    }

    protected void finishShaderlessWorldPassRendering() {
        // Shaderless frames have no deferred-frame finish path. Render the
        // resource-pack BLOOM VBO before restoring vanilla world state.
        renderNativeBloomLayerIfNeeded();
        sealShaderlessWorldFramebufferAlpha("shaderless-world-pass-finish");
        restoreVanillaWorldPassState(false, true);
        shaderlessWorldPassActive = false;
        if (worldLoadPresentationGuardFrames > 0) {
            worldLoadPresentationGuardFrames--;
        }
    }

    protected void sealShaderlessWorldFramebufferAlpha(String stage) {
    }

    protected void updateCameraPosition(Minecraft mc) {
        System.arraycopy(cameraPosition, 0, previousCameraPosition, 0, 3);
        System.arraycopy(cameraPositionUnshifted, 0, previousCameraPositionUnshifted, 0, 3);

        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            cameraPosition[0] = 0.0f;
            cameraPosition[1] = 0.0f;
            cameraPosition[2] = 0.0f;
            cameraPositionUnshifted[0] = 0.0;
            cameraPositionUnshifted[1] = 0.0;
            cameraPositionUnshifted[2] = 0.0;
            return;
        }

        float partialTicks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc);
        Vec3d eyePosition = com.l.ausm.impl.util.MinecraftReflectionCompat.positionEyes(viewEntity, partialTicks);
        double x = com.l.ausm.impl.util.MinecraftReflectionCompat.vecX(eyePosition);
        double y = com.l.ausm.impl.util.MinecraftReflectionCompat.vecY(eyePosition);
        double z = com.l.ausm.impl.util.MinecraftReflectionCompat.vecZ(eyePosition);
        cameraPositionUnshifted[0] = x;
        cameraPositionUnshifted[1] = y;
        cameraPositionUnshifted[2] = z;
        updateCameraOffset(viewEntity, x, y, z);

        cameraPosition[0] = (float) (x + cameraShiftX);
        cameraPosition[1] = (float) y;
        cameraPosition[2] = (float) (z + cameraShiftZ);
    }

    protected void updateCameraOffset(Entity viewEntity, double x, double y, double z) {
        double adjustedX = x + cameraShiftX;
        double adjustedZ = z + cameraShiftZ;
        double adx = Math.abs(adjustedX - previousCameraPosition[0]);
        double adz = Math.abs(adjustedZ - previousCameraPosition[2]);
        double apx = Math.abs(adjustedX);
        double apz = Math.abs(adjustedZ);
        double shiftX = irisCameraShift(adjustedX, adx, apx);
        double shiftZ = irisCameraShift(adjustedZ, adz, apz);
        if (shiftX != 0.0 || shiftZ != 0.0) {
            cameraShiftX += shiftX;
            cameraShiftZ += shiftZ;
            previousCameraPosition[0] += (float) shiftX;
            previousCameraPosition[2] += (float) shiftZ;
        }
        if (Math.abs(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity) - x) > 1000.0 || Math.abs(com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity) - z) > 1000.0) {
            previousCameraPosition[0] = (float) (x + cameraShiftX);
            previousCameraPosition[1] = (float) y;
            previousCameraPosition[2] = (float) (z + cameraShiftZ);
        }
    }

    public void prepareInactiveVanillaFrame() {
        if (isPipelineActive || vanillaRecoveryFrames <= 0) {
            return;
        }
        vanillaRecoveryFrames--;
        resetPipelineState();
    }

    protected void scheduleInactiveVanillaRecoveryFrame() {
        if (!isPipelineActive) {
            vanillaRecoveryFrames = Math.max(vanillaRecoveryFrames, 1);
        }
    }

    protected void restoreVanillaWorldPassState(boolean bindMinecraftFramebuffer, boolean resetPortalMasks) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (bindMinecraftFramebuffer && mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc) != null) {
            bindMinecraftFramebufferForGui(mc);
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();

        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);

        if (resetPortalMasks) {
            resetPortalMaskState();
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        restoreVanillaFixedFunctionTextureState(mc);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
    }

    public void prepareVanillaParticleRendering() {
        if (isPipelineActive && !shouldBypassWorldPassRendering()) {
            return;
        }
        prepareVanillaParticleRenderingState();
    }

    public boolean shouldRenderParticlesWithVanillaState() {
        return vanillaParticleRecoveryFrames > 0;
    }

    public void clearClientParticles(String reason) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), Object.class, null, "field_71452_i", "effectRenderer") == null) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), Object.class, null, "field_71452_i", "effectRenderer")), new String[] {"func_78870_a", "clearEffects"}, new Class<?>[] {net.minecraft.world.World.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)));;
        ThaumcraftParticleBridge.clearParticles(reason);
        vanillaParticleRecoveryFrames = 0;
        logTerrainDiagnostic("particles:clear", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "reason=" + reason);
    }

    public void prepareVanillaParticleRenderingState() {
        // Probe disabled.
}

    protected void startVanillaParticleRecovery() {
        vanillaParticleRecoveryFrames = Math.max(vanillaParticleRecoveryFrames, PARTICLE_DIMENSION_RECOVERY_FRAMES);
    }

    protected void restoreVanillaFixedFunctionTextureState(Minecraft mc) {
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.enableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        } else {
            TextureBinder.restoreDefaultTextureUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        bindBlockAtlas();
        TextureBinder.restoreDefaultTextureUnit();
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
    }

    protected static void restoreShaderlessTerrainClientTextureArrays() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
    }

    protected static double irisCameraShift(double adjusted, double delta, double absoluteAdjusted) {
        return PipelineFrameValues.irisCameraShift(adjusted, delta, absoluteAdjusted);
    }

    protected void resizeFramebuffer(int width, int height, boolean preservePersistentAttachments) {
        if (width <= 0 || height <= 0) {
            return;
        }

        clearCompositeInvalidFallbackSnapshot();
        if (preservePersistentAttachments) {
            pingPongManager.resize(width, height, packDirectives.renderTargets().clearDisabled());
        } else {
            pingPongManager.resize(width, height);
        }
        shaderImages.resize(width, height);
        shaderStorageBuffers.resize(width, height);
        setupComputePending = true;
    }

    protected Attachment[] frameClearAttachments(boolean forcePersistentClear) {
        java.util.Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : Attachment.values()) {
            if (forcePersistentClear || !clearDisabled.contains(attachment)) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    protected boolean shouldResetTemporalHistory(Minecraft mc, boolean betterPortalsExternalTarget) {
        temporalHistoryResetReason = "";
        temporalHistoryResetVelocity = 0.0f;
        temporalHistoryResetYaw = 0.0f;
        temporalHistoryResetPitch = 0.0f;
        if (betterPortalsExternalTarget || mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || !pingPongManager.isInitialized()) {
            return false;
        }

        World world = renderWorld(mc);
        int dimensionId = safeDimensionId(world);
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            resetTemporalHistoryTracking(dimensionId);
            temporalHistoryResetReason = "missing-view-entity";
            return true;
        }

        float yaw = interpolateAngle(com.l.ausm.impl.util.MinecraftReflectionCompat.prevRotationYaw(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.rotationYaw(viewEntity), currentWorldPartialTicks);
        float pitch = com.l.ausm.impl.util.MinecraftReflectionCompat.prevRotationPitch(viewEntity) + (com.l.ausm.impl.util.MinecraftReflectionCompat.rotationPitch(viewEntity) - com.l.ausm.impl.util.MinecraftReflectionCompat.prevRotationPitch(viewEntity)) * currentWorldPartialTicks;
        float velocity = cameraVelocityMagnitude();

        if (!temporalHistoryInitialized) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "initial";
            return true;
        }

        float yawDelta = Math.abs(wrapDegrees(yaw - previousTemporalYaw));
        float pitchDelta = Math.abs(pitch - previousTemporalPitch);
        accumulatedTemporalYaw += yawDelta;
        accumulatedTemporalPitch += pitchDelta;

        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        temporalHistoryResetVelocity = velocity;
        temporalHistoryResetYaw = accumulatedTemporalYaw;
        temporalHistoryResetPitch = accumulatedTemporalPitch;

        if (dimensionId != temporalHistoryDimensionId) {
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            clearCompositeInvalidFallbackSnapshot();
            temporalHistoryResetReason = "dimension";
            return true;
        }
        int recoveryDimensionId = BetterPortalsCompat.mainViewSwapRecoveryDimensionId();
        if (recoveryDimensionId != Integer.MIN_VALUE
                && recoveryDimensionId != mainViewSwapTemporalResetDimensionId) {
            mainViewSwapTemporalResetDimensionId = recoveryDimensionId;
            resetTemporalHistoryTracking(dimensionId, yaw, pitch);
            temporalHistoryResetReason = "betterportals-main-view-recovery";
            return true;
        }
        if (recoveryDimensionId == Integer.MIN_VALUE) {
            mainViewSwapTemporalResetDimensionId = Integer.MIN_VALUE;
        }
        if (accumulatedTemporalYaw > TEMPORAL_HISTORY_ACCUMULATED_YAW_RESET
                || accumulatedTemporalPitch > TEMPORAL_HISTORY_ACCUMULATED_PITCH_RESET) {
            // Normal mouse-look should not clear persistent shader history.
            // Clearing temporal/deferred attachments during rotation looks like
            // distant terrain/chunk flicker on packs that keep persistent history.
            accumulatedTemporalYaw = 0.0f;
            accumulatedTemporalPitch = 0.0f;
        }
        return false;
    }

    protected void resetTemporalHistoryTracking(int dimensionId) {
        resetTemporalHistoryTracking(dimensionId, 0.0f, 0.0f);
    }

    protected void resetTemporalHistoryTracking(int dimensionId, float yaw, float pitch) {
        temporalHistoryInitialized = true;
        temporalHistoryDimensionId = dimensionId;
        previousTemporalYaw = yaw;
        previousTemporalPitch = pitch;
        accumulatedTemporalYaw = 0.0f;
        accumulatedTemporalPitch = 0.0f;
    }

    protected float cameraVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float y = cameraPosition[1] - previousCameraPosition[1];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    protected float cameraVerticalDelta() {
        return cameraPosition[1] - previousCameraPosition[1];
    }

    protected float cameraHorizontalVelocityMagnitude() {
        float x = cameraPosition[0] - previousCameraPosition[0];
        float z = cameraPosition[2] - previousCameraPosition[2];
        return (float) Math.sqrt(x * x + z * z);
    }

    protected static float interpolateAngle(float previous, float current, float partialTicks) {
        return PipelineTemporalMath.interpolateAngle(previous, current, partialTicks);
    }

    protected static float wrapDegrees(float value) {
        return PipelineTemporalMath.wrapDegrees(value);
    }

    protected void logTemporalHistoryResetIfNeeded(boolean resetTemporalHistory) {
        if (!resetTemporalHistory || temporalHistoryResetLogs >= MAX_TEMPORAL_HISTORY_RESET_LOGS) {
            return;
        }
        temporalHistoryResetLogs++;
        MainMod.LOGGER.info("[Pipeline] Reset temporal history: reason={} dimension={} velocity={} accumulatedYaw={} accumulatedPitch={} persistentAttachments={}",
                temporalHistoryResetReason,
                temporalHistoryDimensionId,
                temporalHistoryResetVelocity,
                temporalHistoryResetYaw,
                temporalHistoryResetPitch,
                packDirectives.renderTargets().clearDisabled());
    }

    protected void requestPersistentHistoryClear(String reason) {
        if (packDirectives.renderTargets().clearDisabled().isEmpty()) {
            return;
        }
        pendingPersistentHistoryClear = true;
        pendingPersistentHistoryClearReason = reason == null || reason.isBlank() ? "unspecified" : reason;
    }

    protected void clearPendingPersistentHistoryIfNeeded() {
        if (!pendingPersistentHistoryClear || !pingPongManager.isInitialized()) {
            return;
        }

        Attachment[] attachments = persistentHistoryAttachments();
        pendingPersistentHistoryClear = false;
        String reason = pendingPersistentHistoryClearReason;
        pendingPersistentHistoryClearReason = "";
        if (attachments.length == 0) {
            return;
        }

        pingPongManager.clear(attachments);
        pingPongManager.clearWrite(attachments);
        bindWorldFramebuffer();
        if (persistentHistoryClearLogs < MAX_TERRAIN_HISTORY_CLEAR_LOGS) {
            persistentHistoryClearLogs++;
            MainMod.LOGGER.info("[Pipeline] Cleared persistent history before deferred passes: reason={} attachments={}",
                    reason,
                    java.util.Arrays.toString(attachments));
        }
    }

    protected Attachment[] persistentHistoryAttachments() {
        java.util.Set<Attachment> clearDisabled = packDirectives.renderTargets().clearDisabled();
        if (clearDisabled.isEmpty()) {
            return new Attachment[0];
        }

        List<Attachment> attachments = new ArrayList<>();
        for (Attachment attachment : clearDisabled) {
            // COLOR contains the current gbuffer terrain for this frame; clearing it mid-frame
            // causes the white/blank terrain this recovery is meant to avoid.
            if (attachment != Attachment.COLOR) {
                attachments.add(attachment);
            }
        }
        return attachments.toArray(new Attachment[0]);
    }

    protected boolean hasActiveShadowProgram() {
        Boolean shadowEnabled = shaderProperties.renderSettings().shadowEnabled();
        if (shadowEnabled != null) {
            return shadowEnabled;
        }
        for (PipelineProgram program : programs.values()) {
            if (program.stage() == ProgramStage.SHADOW && program.effectiveProgram(programs) != null) {
                return true;
            }
        }
        if (!shadowComputePrograms.isEmpty()
                || !computeProgramArrays.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()
                || !fullscreenArrayPrograms.getOrDefault(ProgramArrayId.SHADOWCOMP, List.of()).isEmpty()) {
            return true;
        }
        return false;
    }

    public boolean shouldDisableVanillaEntityShadows() {
        return isPipelineActive && shadowFramebuffer != null && hasActiveShadowProgram();
    }

    public boolean shouldRenderShadowMapBeforeTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        if ((ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                || shouldSuppressShadowMapForSoftVanillaStartupPack()) {
            return false;
        }
        return !shouldUseNothiriumShadowBridge();
    }

    public boolean shouldRenderShadowMapAfterTerrainSetup() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        return false;
    }

    public boolean shouldRenderShadowMapAfterOpaqueTerrain() {
        if (isBetterPortalsExternalWorldTarget() || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return false;
        }
        if (shouldSuppressShadowMapForSoftVanillaStartupPack()) {
            return false;
        }
        return shouldUseNothiriumShadowBridge();
    }

    protected boolean shouldUseNothiriumShadowBridge() {
        return isPipelineActive
                && worldFrameActive
                && isNothiriumLoaded()
                && NothiriumShadowRenderer.isAvailable()
                && !shouldForceVanillaTerrainRenderer()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected boolean shouldUseNothiriumMainTerrainBridge() {
        return isPipelineActive
                && worldFrameActive
                && isNothiriumLoaded()
                && NothiriumShadowRenderer.isAvailable()
                && !shouldForceVanillaTerrainRenderer()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected boolean shouldSuppressNothiriumShadowTerrain() {
        return false;
    }

    protected boolean shouldReuseMainTerrainForShadowMap() {
        return false;
    }

    public void ensureVanillaTerrainRenderer() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        ensureVanillaTerrainRenderer(
                world != null ? world : (mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null),
                hardwareSafeVanillaTerrain || isPipelineActive
        );
    }

    protected void pushVanillaTerrainRendererState() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null || !(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            vanillaViewFrustumStateStack.push(new Object[]{null, null});
            return;
        }

        vanillaViewFrustumStateStack.push(new Object[]{com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), renderGlobal.ausm$viewFrustum()});
    }

    protected void popVanillaTerrainRendererState() {
        Object[] state = vanillaViewFrustumStateStack.poll();
        if (state == null || state.length < 2 || !(state[0] instanceof RenderGlobal savedRenderGlobal)
                || !(savedRenderGlobal instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }
        ViewFrustum savedViewFrustum = state[1] instanceof ViewFrustum viewFrustum ? viewFrustum : null;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && !BetterPortalsCompat.isRenderingNestedView()
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null) {
            ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (savedViewFrustum == null) {
            if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null && renderGlobal.ausm$viewFrustum() == null) {
                ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
            }
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            return;
        }

        if (renderGlobal.ausm$viewFrustum() != savedViewFrustum) {
            renderGlobal.ausm$setViewFrustum(savedViewFrustum);
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
    }

    public void ensureVanillaTerrainRenderer(World world) {
        ensureVanillaTerrainRenderer(world, false);
    }

    public void ensureRenderGlobalViewFrustum(RenderGlobal renderGlobal) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || accessor.ausm$viewFrustum() != null) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal) {
            return;
        }

        logTerrainDiagnostic("ensure-render-global-view-frustum", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "missing-view-frustum=true");
        ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
    }

    public void updateShaderlessVanillaViewFrustumForCamera() {
        if (!shouldSyncShaderlessVanillaViewFrustumForCamera()) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null
                || !(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null && renderPassWorld != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) {
            return;
        }

        boolean worldChanged = false;
        World renderGlobalWorld = renderGlobal.ausm$world();
        if (renderGlobalWorld != null && renderGlobalWorld != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) {
            worldChanged = syncRenderGlobalWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
        }
        ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), false);

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
            viewFrustum = renderGlobal.ausm$viewFrustum();
        }
        if (viewFrustum == null) {
            return;
        }

        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        updateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity);
        logCameraFrustumSyncIfChanged(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), viewFrustum, viewEntity, renderPassWorld != null, worldChanged);
    }

    protected boolean shouldSyncShaderlessVanillaViewFrustumForCamera() {
        return (BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass())
                || (isPipelineActive
                && ENABLE_SAFE_TERRAIN_FALLBACKS
                && (hardwareSafeVanillaTerrain || softVanillaTerrainRenderer));
    }

    protected void logCameraFrustumSyncIfChanged(World world, ViewFrustum viewFrustum, Entity viewEntity,
                                               boolean renderPass, boolean worldChanged) {
        if (world == null || viewFrustum == null || viewEntity == null) {
            return;
        }

        int chunkX = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity)) >> 4;
        int chunkZ = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity)) >> 4;
        if (lastCameraFrustumSyncWorld == world
                && lastCameraFrustumSyncViewFrustum == viewFrustum
                && lastCameraFrustumSyncChunkX == chunkX
                && lastCameraFrustumSyncChunkZ == chunkZ
                && !worldChanged) {
            return;
        }

        lastCameraFrustumSyncWorld = world;
        lastCameraFrustumSyncViewFrustum = viewFrustum;
        lastCameraFrustumSyncChunkX = chunkX;
        lastCameraFrustumSyncChunkZ = chunkZ;
        if (cameraFrustumSyncLogs >= MAX_CAMERA_FRUSTUM_SYNC_LOGS) {
            return;
        }

        cameraFrustumSyncLogs++;
        MainMod.LOGGER.info(
                "[AUSMFrustumSync] call={} world={} chunk={},{} viewFrustum={} renderPass={} worldChanged={} bp={}",
                cameraFrustumSyncLogs,
                safeDimensionId(world),
                chunkX,
                chunkZ,
                viewFrustumId(viewFrustum),
                renderPass,
                worldChanged,
                BetterPortalsCompat.describeTransitionState()
        );
    }

    public void handleBetterPortalsMainViewSwap() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        logTerrainDiagnostic("bp-main-view-swap:start", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
        startVanillaParticleRecovery();
        if (!isPipelineActive) {
            BetterPortalsCompat.clearMainViewSwapTransientState();
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            clearScheduledWorldTerrainRefresh();
            recoverShaderlessMainWorldTerrain(mc, "bp-main-view-swap");
            logInactiveBetterPortalsTerrainSkip("main-view-swap", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            return;
        }

        boolean terrainTransition = beginTerrainTransition(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
        logTerrainDiagnostic("bp-main-view-swap:transition", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "accepted=" + terrainTransition);
        clearClientParticles("bp-main-view-swap");
        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            BetterPortalsCompat.startMainViewSwapRecovery(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            BetterPortalsCompat.logMainViewSwapRecoveryIfNeeded(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "bp-main-view-swap");
            resetCameraFrustumSyncState();
            scheduleDimensionSwitchTerrainRefresh();
            scheduleBloomTerrainRefresh("bp-main-view-swap");
            scheduleInactiveVanillaRecoveryFrame();
            scheduleWorldLoadLightRecalculation();
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
        logTerrainDiagnostic("bp-main-view-swap:end", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "accepted=" + terrainTransition);
    }

    public void handleWorldDimensionSwitch(int previousDimensionId, int dimensionId) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        logTerrainDiagnostic("dimension-switch:start", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId);
        clearClientParticles("dimension-switch");
        startVanillaParticleRecovery();
        BetterPortalsCompat.clearMainViewSwapTransientState();
        if (!isPipelineActive) {
            BetterPortalsCompat.cancelMainViewSwapRecovery();
            clearPendingShaderChunkRefreshes();
            clearShaderlessBloomMetadata();
            clearPendingBetterPortalsPortalBlockRefresh();
            clearScheduledWorldTerrainRefresh();
            clearScheduledBloomTerrainRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            recoverShaderlessMainWorldTerrain(mc, "dimension-switch");
            logInactiveBetterPortalsTerrainSkip("dimension-switch", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            return;
        }

        boolean terrainTransition = beginTerrainTransition(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
        if (!terrainTransition) {
            clearPendingShaderChunkRefreshes();
            clearPendingBetterPortalsPortalBlockRefresh();
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic("dimension-switch:debounced", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        clearPendingShaderChunkRefreshes();
        clearPendingBetterPortalsPortalBlockRefresh();
        boolean betterPortalsRecovery = BetterPortalsCompat.isMainViewSwapRecoveryActive();
        if (betterPortalsRecovery) {
            rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "dimension-switch-bp-recovery");
            resetCameraFrustumSyncState();
            scheduleDimensionSwitchTerrainRefresh();
            scheduleBloomTerrainRefresh("dimension-switch-bp-recovery");
            scheduleInactiveVanillaRecoveryFrame();
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic("dimension-switch:bp-recovery-deferred", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                    "previous=" + previousDimensionId + ", current=" + dimensionId);
            return;
        }

        clearShaderlessBloomMetadata();
        resetPipelineState(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc));
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "dimension-switch");
        resetCameraFrustumSyncState();
        scheduleDimensionSwitchTerrainRefresh();
        scheduleBloomTerrainRefresh("dimension switch");
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        logTerrainDiagnostic("dimension-switch:scheduled", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "previous=" + previousDimensionId + ", current=" + dimensionId
                + ", bpRecoveryWasActive=" + betterPortalsRecovery);
    }

    public void handleClientTeleportResync(int previousDimensionId, int currentDimensionId, double distanceSq, double horizontalDistanceSq) {
        boolean dimensionChanged = previousDimensionId != Integer.MIN_VALUE
                && currentDimensionId != Integer.MIN_VALUE
                && previousDimensionId != currentDimensionId;
        boolean longTeleport = horizontalDistanceSq >= CLIENT_TELEPORT_TERRAIN_REFRESH_DISTANCE_SQ;
        if (!dimensionChanged && !longTeleport) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        String reason = dimensionChanged ? "client-teleport-dimension" : "client-teleport";
        logTerrainDiagnostic(reason + ":start", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                "previous=" + previousDimensionId + ", current=" + currentDimensionId
                        + ", distanceSq=" + distanceSq + ", horizontalDistanceSq=" + horizontalDistanceSq);
        if (dimensionChanged) {
            clearClientParticles(reason);
        }
        startVanillaParticleRecovery();

        if (!dimensionChanged) {
            clearPendingShaderChunkRefreshes();
            clearPendingBetterPortalsPortalBlockRefresh();
            currentWorldPass = 0;
            currentWorldPartialTicks = 0.0F;
            resetCameraFrustumSyncState();
            scheduleWorldTerrainRefresh();
            scheduleWorldLoadLightRecalculation();
            if (!isPipelineActive) {
                recoverShaderlessMainWorldTerrain(mc, reason);
            } else {
                scheduleInactiveVanillaRecoveryFrame();
            }
            logTerrainDiagnostic(reason + ":scheduled", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "preservedClientChunkQueue=true");
            return;
        }

        BetterPortalsCompat.clearMainViewSwapTransientState();
        BetterPortalsCompat.cancelMainViewSwapRecovery();
        clearPendingShaderChunkRefreshes();
        clearPendingBetterPortalsPortalBlockRefresh();
        clearShaderlessBloomMetadata();
        clearScheduledWorldTerrainRefresh();
        clearScheduledBloomTerrainRefresh();
        currentWorldPass = 0;
        currentWorldPartialTicks = 0.0F;

        if (!isPipelineActive) {
            recoverShaderlessMainWorldTerrain(mc, reason);
            scheduleWorldLoadLightRecalculation();
            logTerrainDiagnostic(reason + ":shaderless", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
            return;
        }

        resetPipelineState(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc));
        rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), reason);
        resetCameraFrustumSyncState();
        scheduleFullWorldTerrainRefresh();
        scheduleBloomTerrainRefresh(reason);
        scheduleInactiveVanillaRecoveryFrame();
        scheduleWorldLoadLightRecalculation();
        logTerrainDiagnostic(reason + ":scheduled", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
    }

    protected void recoverShaderlessMainWorldTerrain(Minecraft mc, String reason) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            recoverShaderlessVanillaOwnerTerrain(mc, reason);
            return;
        }

        boolean hardReset = shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            clearCachedVanillaTerrainRendererReferences();
        }

        boolean ready = NothiriumBypass.ensureRendererReady();
        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && (marked || ready) && NothiriumBypass.setupForIsolatedShaderlessMainPass();

        if (com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null) {
            adoptMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), reason);
        }

        if (marked || ready || setup) {
            scheduleInactiveVanillaRecoveryFrame();
        }
        scheduleWorldLoadLightRecalculation();
        logShaderlessNothiriumLoadRendererReload(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), marked, reason);
        logTerrainDiagnostic(reason + ":shaderless-recover", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                "ready=" + ready + ", marked=" + marked + ", setup=" + setup + ", hardReset=" + hardReset);
    }

    protected void recoverShaderlessVanillaOwnerTerrain(Minecraft mc, String reason) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }

        boolean transitionReset = shouldHardResetShaderlessNothirium(reason);
        if (transitionReset && com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null) {
            rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), reason + "-vanilla-owner");
            resetCameraFrustumSyncState();
            scheduleInactiveVanillaRecoveryFrame();
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-rebuild", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
        } else if (com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null) {
            adoptMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), reason + "-vanilla-owner");
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-adopt", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
        } else {
            logTerrainDiagnostic(reason + ":shaderless-vanilla-owner-missing-render-global", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
        }

        scheduleWorldLoadLightRecalculation();
    }

    protected void resetCameraFrustumSyncState() {
        lastCameraFrustumSyncWorld = null;
        lastCameraFrustumSyncViewFrustum = null;
        lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;
        lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshWorld = null;
        lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
    }

    protected boolean beginTerrainTransition(World world) {
        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        long elapsed = now - lastTerrainTransitionMillis;
        if (world != null
                && lastTerrainTransitionDimension == dimension
                && elapsed >= 0L
                && elapsed < WORLD_TERRAIN_TRANSITION_DEBOUNCE_MS) {
            logTerrainDiagnostic("terrain-transition:debounced", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
            return false;
        }

        lastTerrainTransitionWorld = world;
        lastTerrainTransitionDimension = dimension;
        lastTerrainTransitionMillis = now;
        logTerrainDiagnostic("terrain-transition:accepted", world, "elapsedMs=" + elapsed + ", lastDim=" + lastTerrainTransitionDimension);
        return true;
    }

    public void queueBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }
        if (sameBlockState(oldState, newState)) {
            return;
        }
        if (shouldDebounceBetterPortalsPortalBlockRefresh(world, pos)) {
            logTerrainDiagnostic("bp-portal-block:debounced", world, "pos=" + pos
                    + ", old=" + blockName(oldState)
                    + ", new=" + blockName(newState));
            return;
        }

        pendingBetterPortalsPortalBlockWorld = world;
        pendingBetterPortalsPortalBlockPos = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToImmutable(pos);
        pendingBetterPortalsPortalBlockOldState = oldState;
        pendingBetterPortalsPortalBlockNewState = newState;
        pendingBetterPortalsPortalBlockChangeCount++;
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            pendingBetterPortalsPortalBlockRefreshDelay = 3;
        }
        logTerrainDiagnostic("bp-portal-block:queued", world, "pos=" + pos
                + ", count=" + pendingBetterPortalsPortalBlockChangeCount
                + ", old=" + blockName(oldState)
                + ", new=" + blockName(newState));
    }

    protected void clearPendingBetterPortalsPortalBlockRefresh() {
        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;
        lastBetterPortalsPortalBlockRefreshWorld = null;
        lastBetterPortalsPortalBlockRefreshPos = null;
        lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;
        lastBetterPortalsPortalBlockRefreshMillis = 0L;
    }

    public void runPendingBetterPortalsPortalBlockRefresh() {
        if (pendingBetterPortalsPortalBlockRefreshDelay < 0) {
            return;
        }
        if (pendingBetterPortalsPortalBlockRefreshDelay > 0) {
            pendingBetterPortalsPortalBlockRefreshDelay--;
            return;
        }

        World world = pendingBetterPortalsPortalBlockWorld;
        BlockPos pos = pendingBetterPortalsPortalBlockPos;
        IBlockState oldState = pendingBetterPortalsPortalBlockOldState;
        IBlockState newState = pendingBetterPortalsPortalBlockNewState;
        int changeCount = pendingBetterPortalsPortalBlockChangeCount;

        pendingBetterPortalsPortalBlockWorld = null;
        pendingBetterPortalsPortalBlockPos = null;
        pendingBetterPortalsPortalBlockOldState = null;
        pendingBetterPortalsPortalBlockNewState = null;
        pendingBetterPortalsPortalBlockChangeCount = 0;
        pendingBetterPortalsPortalBlockRefreshDelay = -1;

        handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, changeCount);
    }

    public void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        handleBetterPortalsPortalBlockChanged(world, pos, oldState, newState, 1);
    }

    protected void handleBetterPortalsPortalBlockChanged(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int changeCount) {
        if (!BetterPortalsCompat.isInstalled() || world == null || pos == null) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        BetterPortalsCompat.beginMainViewSwapHandling();
        try {
            rememberBetterPortalsPortalBlockRefresh(world, pos);
            markPortalChangeRenderRegion(world, pos);
            logTerrainDiagnostic("bp-portal-block:refresh", world, "pos=" + pos
                    + ", count=" + Math.max(1, changeCount)
                    + ", old=" + blockName(oldState)
                    + ", new=" + blockName(newState));
            MainMod.LOGGER.debug("[BetterPortalsCompat] Refreshed portal terrain after {} coalesced block change(s): world={} pos={} old={} new={}",
                    Math.max(1, changeCount),
                    safeDimensionId(world),
                    pos,
                    oldState != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(oldState)) : "null",
                    newState != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(newState)) : "null");
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh portal terrain after block change", e);
        } finally {
            BetterPortalsCompat.endMainViewSwapHandling();
        }
    }

    protected boolean sameBlockState(IBlockState oldState, IBlockState newState) {
        return oldState == newState || (oldState != null && oldState.equals(newState));
    }

    protected boolean shouldDebounceBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        long now = System.currentTimeMillis();
        return lastBetterPortalsPortalBlockRefreshWorld == world
                && lastBetterPortalsPortalBlockRefreshDimension == safeDimensionId(world)
                && pos.equals(lastBetterPortalsPortalBlockRefreshPos)
                && now - lastBetterPortalsPortalBlockRefreshMillis >= 0L
                && now - lastBetterPortalsPortalBlockRefreshMillis < BETTER_PORTALS_PORTAL_BLOCK_REFRESH_DEBOUNCE_MS;
    }

    protected void rememberBetterPortalsPortalBlockRefresh(World world, BlockPos pos) {
        lastBetterPortalsPortalBlockRefreshWorld = world;
        lastBetterPortalsPortalBlockRefreshPos = pos != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosToImmutable(pos) : null;
        lastBetterPortalsPortalBlockRefreshDimension = safeDimensionId(world);
        lastBetterPortalsPortalBlockRefreshMillis = System.currentTimeMillis();
    }

    protected void markPortalChangeRenderRegion(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }

        int radius = 8;
        com.l.ausm.impl.util.MinecraftReflectionCompat.worldMarkBlockRangeForRenderUpdate(world,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) - radius,
                Math.max(0, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) - radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) - radius,
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + radius,
                Math.min(255, com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + radius),
                com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + radius
        );
    }

    protected void ensureVanillaTerrainRenderer(World world, boolean force) {
        boolean bypass = NothiriumBypass.shouldBypass();
        if (!force && !bypass) {
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:skip", world, "force=false, nothiriumBypass=false");
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        logSteadyVanillaTerrainDiagnostic("ensure-vanilla:start", world, "force=" + force + ", nothiriumBypass=" + bypass);
        RenderGlobal currentRenderGlobal = com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
        RenderGlobalAccessor renderGlobal = (RenderGlobalAccessor) currentRenderGlobal;
        int requestedRenderDistanceChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc);
        Integer activeRenderDistanceChunks = activeVanillaViewFrustumRenderDistanceChunks > 0
                ? activeVanillaViewFrustumRenderDistanceChunks
                : null;
        int expectedRenderDistanceChunks = vanillaTerrainRenderDistanceChunks(
                world,
                activeRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (canReuseActiveVanillaTerrainRenderer(renderGlobal, currentRenderGlobal, world, expectedRenderDistanceChunks)) {
            updateVanillaViewFrustumChunkPositions(renderGlobal.ausm$viewFrustum(), com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc));
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:reuse-active", world,
                    "renderDistance=" + expectedRenderDistanceChunks + ", force=" + force);
            return;
        }

        boolean rendererStateChanged = syncRenderGlobalWorld(currentRenderGlobal, world);
        ViewFrustum activeViewFrustum = renderGlobal.ausm$viewFrustum();
        pruneBetterPortalsVanillaViewFrustumCache(currentRenderGlobal, world);

        boolean useVbo = com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean(net.minecraft.client.renderer.OpenGlHelper.class, new String[] {"func_176075_f", "useVbo"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, true);
        if (renderGlobal.ausm$renderDispatcher() == null) {
            logVanillaTerrainRendererCreation(world, force, "missing-dispatcher");
            renderGlobal.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
            rendererStateChanged = true;
        }
        IRenderChunkFactory renderChunkFactory = renderGlobal.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            renderGlobal.ausm$setRenderChunkFactory(renderChunkFactory);
            rendererStateChanged = true;
        }
        if (renderGlobal.ausm$renderContainer() == null) {
            renderGlobal.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
            rendererStateChanged = true;
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        Map<World, Integer> rendererViewFrustumDistances = vanillaViewFrustumRenderDistances.computeIfAbsent(
                currentRenderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum viewFrustum = rendererViewFrustums.get(world);
        Integer cachedRenderDistanceChunks = rendererViewFrustumDistances.get(world);
        int renderDistanceChunks = vanillaTerrainRenderDistanceChunks(
                world,
                cachedRenderDistanceChunks,
                requestedRenderDistanceChunks
        );
        if (viewFrustum != null && cachedRenderDistanceChunks != null && cachedRenderDistanceChunks != renderDistanceChunks) {
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(viewFrustum);
            clearQueuedUpdatesForViewFrustums(renderGlobal, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
            com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(viewFrustum);
            if (viewFrustum == activeViewFrustum) {
                activeViewFrustum = null;
            }
            rendererViewFrustums.remove(world);
            rendererViewFrustumDistances.remove(world);
            viewFrustum = null;
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal && activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            rendererStateChanged = true;
            MainMod.LOGGER.info("[Pipeline] Rebuilt vanilla terrain renderer for render distance change: world={} old={} new={} requested={}",
                    safeDimensionId(world),
                    cachedRenderDistanceChunks,
                    renderDistanceChunks,
                    requestedRenderDistanceChunks);
        }
        if (viewFrustum == null) {
            if (activeVanillaViewFrustumRenderGlobal == currentRenderGlobal
                    && activeVanillaViewFrustumWorld == world
                    && activeViewFrustum != null) {
                viewFrustum = activeViewFrustum;
            } else {
                viewFrustum = new ViewFrustum(
                        world,
                        renderDistanceChunks,
                        com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc),
                        renderChunkFactory
                );
            }
            rendererViewFrustums.put(world, viewFrustum);
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
            rendererStateChanged = true;
        } else if (cachedRenderDistanceChunks == null) {
            rendererViewFrustumDistances.put(world, renderDistanceChunks);
        }

        updateVanillaViewFrustumChunkPositions(viewFrustum, com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc));
        if (activeViewFrustum != viewFrustum) {
            renderGlobal.ausm$setViewFrustum(viewFrustum);
            rendererStateChanged = true;
        }
        activeVanillaViewFrustumRenderGlobal = currentRenderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        if (rendererStateChanged) {
            renderGlobal.ausm$setDisplayListEntitiesDirty(true);
        }
        String detail = "force=" + force
                + ", activeViewBefore=" + viewFrustumId(activeViewFrustum)
                + ", activeViewAfter=" + viewFrustumId(renderGlobal.ausm$viewFrustum())
                + ", cachedView=" + viewFrustumId(viewFrustum)
                + ", renderDistance=" + renderDistanceChunks
                + (requestedRenderDistanceChunks != renderDistanceChunks
                        ? ", requestedRenderDistance=" + requestedRenderDistanceChunks
                        : "");
        if (rendererStateChanged) {
            logTerrainDiagnostic("ensure-vanilla:changed", world, detail);
        } else {
            logSteadyVanillaTerrainDiagnostic("ensure-vanilla:unchanged", world, detail);
        }
    }

    protected int vanillaTerrainRenderDistanceChunks(World world, Integer cachedRenderDistanceChunks,
                                                   int requestedRenderDistanceChunks) {
        if (shouldUseBetterPortalsPortalRenderDistance(world)) {
            return Math.min(requestedRenderDistanceChunks, BETTER_PORTALS_VANILLA_RENDER_DISTANCE_CAP);
        }
        if (shouldUseStableMainWorldRenderDistance(world)) {
            if (cachedRenderDistanceChunks != null && cachedRenderDistanceChunks > 0) {
                return cachedRenderDistanceChunks;
            }
            if (lastStableMainWorldVanillaRenderDistanceChunks > 0) {
                return lastStableMainWorldVanillaRenderDistanceChunks;
            }
        }
        return requestedRenderDistanceChunks;
    }

    protected boolean shouldUseBetterPortalsPortalRenderDistance(World world) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        return BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingRenderPass()
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && world != null
                && world != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
    }

    protected boolean canReuseActiveVanillaTerrainRenderer(RenderGlobalAccessor renderGlobal,
                                                         RenderGlobal currentRenderGlobal,
                                                         World world,
                                                         int renderDistanceChunks) {
        if (renderGlobal == null
                || currentRenderGlobal == null
                || world == null
                || renderDistanceChunks <= 0
                || activeVanillaViewFrustumRenderGlobal != currentRenderGlobal
                || activeVanillaViewFrustumWorld != world
                || activeVanillaViewFrustumRenderDistanceChunks != renderDistanceChunks) {
            return false;
        }
        if (countCachedVanillaViewFrustums() > 2) {
            return false;
        }
        return renderGlobal.ausm$world() == world
                && renderGlobal.ausm$viewFrustum() != null
                && renderGlobal.ausm$renderDispatcher() != null
                && renderGlobal.ausm$renderChunkFactory() != null
                && renderGlobal.ausm$renderContainer() != null;
    }

    protected boolean shouldUseStableMainWorldRenderDistance(World world) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && mc != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null
                && renderPassWorld == com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)
                && world == com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
    }

    protected void rememberStableMainWorldVanillaRenderDistance(World world, int renderDistanceChunks) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || world != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) || renderDistanceChunks <= 0) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }
        lastStableMainWorldVanillaRenderDistanceChunks = renderDistanceChunks;
    }

    public void handleRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        handleShaderlessMainWorldNothiriumReload(renderGlobal);
        logRenderGlobalLoadRenderers(renderGlobal);
    }

    public void handleRenderGlobalLoadRenderersComplete(RenderGlobal renderGlobal) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        String caller = externalRenderCaller();
        boolean manualChunkReload = isManualChunkReloadCaller(caller);
        if (renderGlobal == null
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal
                || !isStableMainWorldLoadRenderersCaller(caller)
                || isPipelineActive
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) {
            return;
        }

        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            if (manualChunkReload) {
                rebuildMainWorldVanillaViewFrustum(renderGlobal, com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "manual-reload-vanilla-owner");
            } else {
                adoptMainWorldVanillaViewFrustum(renderGlobal, com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "main-load-vanilla-owner");
            }
            clearShaderlessBloomMetadata();
            scheduleWorldLoadLightRecalculation();
            return;
        }

        adoptMainWorldVanillaViewFrustum(renderGlobal, com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), manualChunkReload ? "manual-reload" : "main-load");
        markShaderlessMainWorldNothiriumReload(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), manualChunkReload ? "manual-load-renderers" : "main-load-renderers");
        clearShaderlessBloomMetadata();
        scheduleInactiveVanillaRecoveryFrame();
    }

    protected void handleShaderlessMainWorldNothiriumReload(RenderGlobal renderGlobal) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        String caller = externalRenderCaller();
        if (renderGlobal == null
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != renderGlobal
                || !isManualChunkReloadCaller(caller)
                || isPipelineActive
                || NothiriumBypass.shouldBypass()
                || BetterPortalsCompat.isRenderingRenderPass()
                || BetterPortalsCompat.isRenderingNestedView()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return;
        }

        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        if (renderGlobalWorld != null && renderGlobalWorld != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) {
            return;
        }

        markShaderlessMainWorldNothiriumReload(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "manual-load-renderers");
    }

    protected void markShaderlessMainWorldNothiriumReload(World world, String reason) {
        if (world == null) {
            return;
        }
        if (shouldLeaveShaderlessVanillaTerrainUntouched()) {
            return;
        }

        int dimension = safeDimensionId(world);
        long now = System.currentTimeMillis();
        boolean debounced = dimension == lastShaderlessNothiriumLoadRendererReloadDimension
                && now - lastShaderlessNothiriumLoadRendererReloadMillis < 1000L;
        if (debounced) {
            logShaderlessNothiriumLoadRendererReload(world, false, "debounced");
            return;
        }

        lastShaderlessNothiriumLoadRendererReloadDimension = dimension;
        lastShaderlessNothiriumLoadRendererReloadMillis = now;
        boolean hardReset = shouldHardResetShaderlessNothirium(reason);
        if (hardReset) {
            clearCachedVanillaTerrainRendererReferences();
        }

        boolean marked = hardReset ? NothiriumBypass.recreateRenderer() : NothiriumBypass.markAllChanged();
        boolean setup = hardReset && marked && NothiriumBypass.setupForIsolatedShaderlessMainPass();
        if (marked || setup) {
            scheduleInactiveVanillaRecoveryFrame();
        }
        logShaderlessNothiriumLoadRendererReload(world, marked, reason);
        if (setup) {
            logTerrainDiagnostic(reason + ":shaderless-reload-setup", world, "marked=" + marked);
        }
    }

    protected boolean shouldHardResetShaderlessNothirium(String reason) {
        if (!BetterPortalsCompat.isInstalled() || isPipelineActive || reason == null) {
            return false;
        }
        return "dimension-switch".equals(reason)
                || "bp-main-view-swap".equals(reason)
                || "manual-load-renderers".equals(reason);
    }

    protected boolean shouldLeaveShaderlessVanillaTerrainUntouched() {
        return BetterPortalsCompat.isInstalled()
                && !isPipelineActive
                && NothiriumBypass.shouldBypass()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected static boolean isManualChunkReloadCaller(String caller) {
        return caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_184122_c:");
    }

    protected static boolean isStableMainWorldLoadRenderersCaller(String caller) {
        return isManualChunkReloadCaller(caller)
                || caller != null && caller.startsWith("net.minecraft.client.Minecraft#func_71353_a:");
    }

    protected void adoptMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int renderDistanceChunks = mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc) : -1;
        ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
        if (viewFrustum == null) {
            ensureVanillaTerrainRenderer(world, true);
            viewFrustum = accessor.ausm$viewFrustum();
            if (viewFrustum == null) {
                deleteCachedVanillaTerrainRenderer(world);
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
                logTerrainDiagnostic(stagePrefix + ":missing-view-frustum", world, "");
                return;
            }
            logTerrainDiagnostic(stagePrefix + ":created-view-frustum", world,
                    "current=" + viewFrustumId(viewFrustum)
                            + ", renderDistance=" + renderDistanceChunks);
        }

        Map<World, ViewFrustum> rendererViewFrustums = vanillaViewFrustums.computeIfAbsent(
                renderGlobal,
                ignored -> new IdentityHashMap<>()
        );
        ViewFrustum previous = rendererViewFrustums.put(world, viewFrustum);
        if (previous != null && previous != viewFrustum) {
            Set<ViewFrustum> removedViewFrustums = new HashSet<>();
            removedViewFrustums.add(previous);
            clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
            vanillaViewFrustumChunkPositionKeys.remove(previous);
            com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(previous);
        }

        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;
        if (mc != null) {
            updateVanillaViewFrustumChunkPositions(viewFrustum, com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc));
        }
        accessor.ausm$setDisplayListEntitiesDirty(true);
        logTerrainDiagnostic(stagePrefix + ":adopt-view-frustum", world,
                "previous=" + viewFrustumId(previous)
                        + ", current=" + viewFrustumId(viewFrustum)
                        + ", renderDistance=" + renderDistanceChunks);
    }

    protected void rebuildMainWorldVanillaViewFrustum(RenderGlobal renderGlobal, World world, String stagePrefix) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || world == null) {
            return;
        }

        pruneBetterPortalsVanillaViewFrustumCache(renderGlobal, world);
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc) == null) {
            return;
        }

        boolean worldChanged = syncRenderGlobalWorld(renderGlobal, world);
        boolean useVbo = com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean(net.minecraft.client.renderer.OpenGlHelper.class, new String[] {"func_176075_f", "useVbo"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, true);
        if (accessor.ausm$renderDispatcher() == null) {
            accessor.ausm$setRenderDispatcher(new ChunkRenderDispatcher());
        }
        IRenderChunkFactory renderChunkFactory = accessor.ausm$renderChunkFactory();
        if (renderChunkFactory == null) {
            renderChunkFactory = useVbo ? new VboChunkFactory() : new ListChunkFactory();
            accessor.ausm$setRenderChunkFactory(renderChunkFactory);
        }
        if (accessor.ausm$renderContainer() == null) {
            accessor.ausm$setRenderContainer(useVbo ? new VboRenderList() : new RenderList());
        }

        int renderDistanceChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc);
        ViewFrustum previousActive = accessor.ausm$viewFrustum();
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();
        if (previousActive != null) {
            removedViewFrustums.add(previousActive);
        }
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                removedViewFrustums.add(removed);
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }

        ViewFrustum freshViewFrustum = new ViewFrustum(
                world,
                renderDistanceChunks,
                renderGlobal,
                renderChunkFactory
        );
        accessor.ausm$setViewFrustum(freshViewFrustum);

        vanillaViewFrustums
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, freshViewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(renderGlobal, ignored -> new IdentityHashMap<>())
                .put(world, renderDistanceChunks);
        rememberStableMainWorldVanillaRenderDistance(world, renderDistanceChunks);
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = renderGlobal;
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = renderDistanceChunks;

        int scheduledChunks = scheduleAllFreshViewFrustumChunks(accessor, freshViewFrustum, world);
        forceUpdateVanillaViewFrustumChunkPositions(freshViewFrustum, com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc), world, stagePrefix);
        accessor.ausm$setDisplayListEntitiesDirty(true);

        clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            if (removedViewFrustum != null && removedViewFrustum != freshViewFrustum) {
                vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
                com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(removedViewFrustum);
            }
        }

        logTerrainDiagnostic(stagePrefix + ":rebuild-view-frustum", world,
                "previous=" + viewFrustumId(previousActive)
                        + ", current=" + viewFrustumId(freshViewFrustum)
                        + ", renderDistance=" + renderDistanceChunks
                        + ", scheduledChunks=" + scheduledChunks
                        + ", worldChanged=" + worldChanged);
    }

    protected int scheduleAllFreshViewFrustumChunks(RenderGlobalAccessor renderGlobal, ViewFrustum viewFrustum, World world) {
        RenderChunk[] renderChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderGlobal == null || renderChunks == null) {
            return 0;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null) {
            return 0;
        }

        chunksToUpdate.clear();
        int scheduled = 0;
        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            assignRenderChunkWorld(renderChunk, world);
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((renderChunk), new String[] {"func_178575_a", "setNeedsUpdate"}, new Class<?>[] {boolean.class}, (true));;
            chunksToUpdate.add(renderChunk);
            scheduled++;
        }
        return scheduled;
    }

    protected void clearQueuedUpdatesForViewFrustums(RenderGlobalAccessor renderGlobal, Set<ViewFrustum> viewFrustums) {
        if (renderGlobal == null || viewFrustums == null || viewFrustums.isEmpty()) {
            return;
        }

        Set<RenderChunk> chunksToUpdate = renderGlobal.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return;
        }

        Set<RenderChunk> removedChunks = new HashSet<>();
        for (ViewFrustum viewFrustum : viewFrustums) {
            RenderChunk[] renderChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
            if (renderChunks == null) {
                continue;
            }
            for (RenderChunk renderChunk : renderChunks) {
                if (renderChunk != null) {
                    removedChunks.add(renderChunk);
                }
            }
        }
        if (!removedChunks.isEmpty()) {
            chunksToUpdate.removeAll(removedChunks);
        }
    }

    protected void forceUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity, World world, String stagePrefix) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((viewFrustum), new String[] {"func_178163_a", "updateChunkPositions"},
                new Class<?>[] {double.class, double.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity)), (com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity)));;
            rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            logTerrainDiagnostic(stagePrefix + ":deferred-chunk-positions", world, e.getClass().getSimpleName());
        }
    }

    protected void logSteadyVanillaTerrainDiagnostic(String stage, World world, String detail) {
        if (steadyVanillaTerrainDiagnosticLogs >= MAX_STEADY_VANILLA_TERRAIN_DIAGNOSTIC_LOGS) {
            return;
        }
        steadyVanillaTerrainDiagnosticLogs++;
        logTerrainDiagnostic(stage, world, detail);
    }

    protected void logShaderlessNothiriumLoadRendererReload(World world, boolean marked, String reason) {
        if (shaderlessNothiriumLoadRendererReloadLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        shaderlessNothiriumLoadRendererReloadLogs++;

        MainMod.LOGGER.info(
                "[AUSMNothiriumReload] loadRenderers bridge call={} reason={} world={} marked={} active={} bypass={} nested={} renderPass={} caller={}",
                shaderlessNothiriumLoadRendererReloadLogs,
                reason,
                safeDimensionId(world),
                marked,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                externalRenderCaller()
        );
    }

    public void logRenderGlobalLoadRenderers(RenderGlobal renderGlobal) {
        if (renderGlobalLoadRendererLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        renderGlobalLoadRendererLogs++;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World renderGlobalWorld = renderGlobal instanceof RenderGlobalAccessor accessor ? accessor.ausm$world() : null;
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] loadRenderers call={} frame={} renderGlobalWorld={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} pendingAttempts={} pendingDelay={} pendingDim={} pendingReset={} pendingFullReset={} pendingVanillaReload={} bpState={} caller={}",
                renderGlobalLoadRendererLogs,
                pipelineFrameId,
                safeDimensionId(renderGlobalWorld),
                mc != null ? safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                pendingWorldTerrainRefreshAttempts,
                pendingWorldTerrainRefreshDelay,
                pendingWorldTerrainRefreshDimension,
                pendingWorldTerrainRendererReset,
                pendingWorldTerrainFullRendererReset,
                pendingWorldTerrainVanillaReload,
                BetterPortalsCompat.describeTransitionState(),
                externalRenderCaller()
        );
    }

    protected void logTerrainDiagnostic(String stage, World world, String detail) {
        // Diagnostic disabled.
}

    protected void logVanillaTerrainRendererCreation(World world, boolean force, String reason) {
        if (vanillaTerrainRendererCreationLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        vanillaTerrainRendererCreationLogs++;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMRenderGlobal] created vanilla ChunkRenderDispatcher call={} reason={} force={} world={} clientWorld={} active={} bypass={} nested={} renderPass={} recovery={} caller={}",
                vanillaTerrainRendererCreationLogs,
                reason,
                force,
                safeDimensionId(world),
                mc != null ? safeDimensionId(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)) : Integer.MIN_VALUE,
                isPipelineActive,
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isRenderingNestedView(),
                BetterPortalsCompat.isRenderingRenderPass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive(),
                externalRenderCaller()
        );
    }

    protected static String viewFrustumId(ViewFrustum viewFrustum) {
        return viewFrustum != null ? Integer.toHexString(System.identityHashCode(viewFrustum)) : "null";
    }

    protected static String blockName(IBlockState state) {
        return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state) != null ? String.valueOf(com.l.ausm.impl.util.MinecraftReflectionCompat.blockRegistryName(com.l.ausm.impl.util.MinecraftReflectionCompat.blockFromState(state))) : "null";
    }

    protected String externalRenderCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(PipelineContext.class.getName())
                    || className.equals("com.l.ausm.impl.mixin.pipeline.RenderSkyMixin")
                    || className.equals("net.minecraft.client.renderer.RenderGlobal")) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }

    protected void updateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }

        if (!shouldUpdateVanillaViewFrustumChunkPositions(viewFrustum, viewEntity)) {
            return;
        }

        if (BetterPortalsCompat.isMainViewSwapHandling()) {
            return;
        }

        if (BetterPortalsCompat.isInstalled() && !BetterPortalsCompat.isRenderingRenderPass()) {
            return;
        }

        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((viewFrustum), new String[] {"func_178163_a", "updateChunkPositions"},
                new Class<?>[] {double.class, double.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity)), (com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity)));;
            rememberVanillaViewFrustumChunkPosition(viewFrustum, viewEntity);
        } catch (NullPointerException e) {
            if (!BetterPortalsCompat.isInstalled()) {
                throw e;
            }
            if (!betterPortalsViewFrustumUpdateWarningLogged) {
                betterPortalsViewFrustumUpdateWarningLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] Deferred vanilla ViewFrustum chunk-position update because Better Portals has no active render pass", e);
            }
        }
    }

    protected boolean shouldUpdateVanillaViewFrustumChunkPositions(ViewFrustum viewFrustum, Entity viewEntity) {
        Long previous = vanillaViewFrustumChunkPositionKeys.get(viewFrustum);
        if (previous == null) {
            return true;
        }
        return previous.longValue() != vanillaViewFrustumChunkPositionKey(viewEntity);
    }

    protected void rememberVanillaViewFrustumChunkPosition(ViewFrustum viewFrustum, Entity viewEntity) {
        if (viewFrustum == null || viewEntity == null) {
            return;
        }
        vanillaViewFrustumChunkPositionKeys.put(viewFrustum, vanillaViewFrustumChunkPositionKey(viewEntity));
    }

    protected long vanillaViewFrustumChunkPositionKey(Entity viewEntity) {
        int chunkX = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity)) >> 4;
        int chunkZ = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity)) >> 4;
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    protected void deleteCachedVanillaTerrainRenderers() {
        if (vanillaViewFrustums.isEmpty()) {
            vanillaViewFrustumRenderDistances.clear();
            vanillaViewFrustumChunkPositionKeys.clear();
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
            lastStableMainWorldVanillaRenderDistanceChunks = -1;
            return;
        }

        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor && rendererViewFrustums != null) {
                clearQueuedUpdatesForViewFrustums(accessor, new HashSet<>(rendererViewFrustums.values()));
            }
            if (rendererViewFrustums == null) {
                continue;
            }
            uniqueViewFrustums.addAll(rendererViewFrustums.values());
        }
        for (ViewFrustum viewFrustum : uniqueViewFrustums) {
            if (viewFrustum != null) {
                vanillaViewFrustumChunkPositionKeys.remove(viewFrustum);
                com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(viewFrustum);
            }
        }
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
        lastStableMainWorldVanillaRenderDistanceChunks = -1;
    }

    protected void clearCachedVanillaTerrainRendererReferences() {
        vanillaViewFrustums.clear();
        vanillaViewFrustumRenderDistances.clear();
        vanillaViewFrustumChunkPositionKeys.clear();
        clearShaderlessBloomMetadata();
        vanillaViewFrustumStateStack.clear();
        activeVanillaViewFrustumRenderGlobal = null;
        activeVanillaViewFrustumWorld = null;
        activeVanillaViewFrustumRenderDistanceChunks = -1;
    }

    protected void deleteCachedVanillaTerrainRenderer(World world) {
        if (world == null || vanillaViewFrustums.isEmpty()) {
            if (world == null) {
                vanillaViewFrustumRenderDistances.clear();
            }
            if (activeVanillaViewFrustumWorld == world) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            return;
        }

        for (Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry : vanillaViewFrustums.entrySet()) {
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null) {
                continue;
            }
            ViewFrustum removed = rendererViewFrustums.remove(world);
            if (removed != null) {
                if (rendererEntry.getKey() instanceof RenderGlobalAccessor accessor) {
                    Set<ViewFrustum> removedViewFrustums = new HashSet<>();
                    removedViewFrustums.add(removed);
                    clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
                }
                vanillaViewFrustumChunkPositionKeys.remove(removed);
                com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(removed);
            }
        }
        for (Map<World, Integer> rendererViewFrustumDistances : vanillaViewFrustumRenderDistances.values()) {
            rendererViewFrustumDistances.remove(world);
        }
        if (activeVanillaViewFrustumWorld == world) {
            activeVanillaViewFrustumRenderGlobal = null;
            activeVanillaViewFrustumWorld = null;
            activeVanillaViewFrustumRenderDistanceChunks = -1;
        }
    }

    protected void pruneBetterPortalsVanillaViewFrustumCache(RenderGlobal currentRenderGlobal, World primaryWorld) {
        if (!BetterPortalsCompat.isInstalled()
                || currentRenderGlobal == null
                || primaryWorld == null
                || vanillaViewFrustums.isEmpty()) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World mainWorld = mc != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) : null;
        if (countCachedVanillaViewFrustums() <= 2) {
            return;
        }
        if (BetterPortalsCompat.isRenderingRenderPass() || BetterPortalsCompat.isRenderingNestedView()) {
            return;
        }

        ViewFrustum activeViewFrustum = currentRenderGlobal instanceof RenderGlobalAccessor accessor
                ? accessor.ausm$viewFrustum()
                : null;
        Set<ViewFrustum> removedViewFrustums = new HashSet<>();

        Iterator<Map.Entry<RenderGlobal, Map<World, ViewFrustum>>> rendererIterator =
                vanillaViewFrustums.entrySet().iterator();
        while (rendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, ViewFrustum>> rendererEntry = rendererIterator.next();
            Map<World, ViewFrustum> rendererViewFrustums = rendererEntry.getValue();
            if (rendererViewFrustums == null || rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
                continue;
            }

            Iterator<Map.Entry<World, ViewFrustum>> worldIterator = rendererViewFrustums.entrySet().iterator();
            while (worldIterator.hasNext()) {
                Map.Entry<World, ViewFrustum> worldEntry = worldIterator.next();
                World cachedWorld = worldEntry.getKey();
                if (cachedWorld == primaryWorld
                        || cachedWorld == mainWorld
                        || cachedWorld == activeVanillaViewFrustumWorld) {
                    continue;
                }

                ViewFrustum removed = worldEntry.getValue();
                if (removed != null && removed != activeViewFrustum) {
                    removedViewFrustums.add(removed);
                }
                worldIterator.remove();
            }

            if (rendererViewFrustums.isEmpty()) {
                rendererIterator.remove();
            }
        }

        Iterator<Map.Entry<RenderGlobal, Map<World, Integer>>> distanceRendererIterator =
                vanillaViewFrustumRenderDistances.entrySet().iterator();
        while (distanceRendererIterator.hasNext()) {
            Map.Entry<RenderGlobal, Map<World, Integer>> rendererEntry = distanceRendererIterator.next();
            Map<World, Integer> rendererViewFrustumDistances = rendererEntry.getValue();
            if (rendererViewFrustumDistances == null || rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
                continue;
            }

            rendererViewFrustumDistances.keySet().removeIf(cachedWorld ->
                    cachedWorld != primaryWorld
                            && cachedWorld != mainWorld
                            && cachedWorld != activeVanillaViewFrustumWorld
            );
            if (rendererViewFrustumDistances.isEmpty()) {
                distanceRendererIterator.remove();
            }
        }

        if (currentRenderGlobal instanceof RenderGlobalAccessor accessor) {
            clearQueuedUpdatesForViewFrustums(accessor, removedViewFrustums);
        }
        for (ViewFrustum removedViewFrustum : removedViewFrustums) {
            vanillaViewFrustumChunkPositionKeys.remove(removedViewFrustum);
            com.l.ausm.impl.util.MinecraftReflectionCompat.deleteViewFrustumGlResources(removedViewFrustum);
        }

        if (!removedViewFrustums.isEmpty()) {
            if (activeVanillaViewFrustumWorld != primaryWorld
                    && activeVanillaViewFrustumRenderGlobal != currentRenderGlobal) {
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
            }
            logTerrainDiagnostic("prune-vanilla-frustums", primaryWorld,
                    "removed=" + removedViewFrustums.size()
                            + ", mainWorld=" + safeDimensionId(mainWorld)
                            + ", primaryWorld=" + safeDimensionId(primaryWorld));
        }
    }

    protected int countCachedVanillaViewFrustums() {
        Set<ViewFrustum> uniqueViewFrustums = new HashSet<>();
        for (Map<World, ViewFrustum> rendererViewFrustums : vanillaViewFrustums.values()) {
            if (rendererViewFrustums != null) {
                uniqueViewFrustums.addAll(rendererViewFrustums.values());
            }
        }
        return uniqueViewFrustums.size();
    }

    protected void refreshBetterPortalsMainViewTerrain(Minecraft mc, String reason) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        if (!isPipelineActive) {
            logInactiveBetterPortalsTerrainSkip("refresh-main-view-terrain", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            return;
        }

        try {
            logTerrainDiagnostic("bp-refresh-main-view:start", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "");
            boolean worldChanged = syncRenderGlobalWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc));
            adoptMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), reason);
            resetCameraFrustumSyncState();
            logTerrainDiagnostic("bp-refresh-main-view:end", com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "worldChanged=" + worldChanged);
        } catch (RuntimeException e) {
            MainMod.LOGGER.warn("[BetterPortalsCompat] Failed to refresh terrain after main view swap", e);
        }
    }

    protected void adoptCurrentRenderGlobalViewFrustum(World world) {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null
                || world == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null
                || !(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            logTerrainDiagnostic("adopt-view-frustum:missing", world, "");
            return;
        }

        vanillaViewFrustums
                .computeIfAbsent(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), ignored -> new IdentityHashMap<>())
                .put(world, viewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), ignored -> new IdentityHashMap<>())
                .put(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc));
        rememberStableMainWorldVanillaRenderDistance(world, com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc));
        activeVanillaViewFrustumRenderGlobal = com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc);
        logTerrainDiagnostic("adopt-view-frustum", world, "viewFrustum=" + viewFrustumId(viewFrustum)
                + ", renderDistance=" + com.l.ausm.impl.util.MinecraftReflectionCompat.renderDistanceChunks(mc));
    }

    protected void logInactiveBetterPortalsTerrainSkip(String reason, World world) {
        if (inactiveBetterPortalsTerrainSkipLogs >= MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS) {
            return;
        }
        inactiveBetterPortalsTerrainSkipLogs++;
        MainMod.LOGGER.info("[AUSMShaderless] Skipping AUSM Better Portals terrain recovery reason={} world={} nothiriumBypass={} recovery={}",
                reason,
                safeDimensionId(world),
                NothiriumBypass.shouldBypass(),
                BetterPortalsCompat.isMainViewSwapRecoveryActive());
    }

    protected boolean syncRenderGlobalWorld(RenderGlobal renderGlobal, World world) {
        if (!(renderGlobal instanceof RenderGlobalAccessor accessor) || !(world instanceof WorldClient worldClient)) {
            return false;
        }

        if (accessor.ausm$world() != worldClient) {
            World previous = accessor.ausm$world();
            accessor.ausm$setWorld(worldClient);
            accessor.ausm$setDisplayListEntitiesDirty(true);
            logTerrainDiagnostic("sync-render-global-world", world, "previous=" + safeDimensionId(previous)
                    + ", current=" + safeDimensionId(worldClient));
            return true;
        }
        return false;
    }

    public void renderShadowMap(float partialTicks) {
        if (!isPipelineActive || shadowFramebuffer == null || lastShadowFrameId == pipelineFrameId) {
            return;
        }
        if (!hasActiveShadowProgram()) {
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            lastShadowFrameId = pipelineFrameId;
            shadowMapPopulated = false;
            shadowMapUsable = false;
            shadowMapSparseForSampling = true;
            shadowMapCoverageStableFrames = 0;
            if (shadowMapSuppressedLogs < 16) {
                shadowMapSuppressedLogs++;
                MainMod.LOGGER.info(
                        "[ShadowHealth] Skipping shadow terrain setup while hardware-safe terrain fallback is active. reason={} frame={}",
                        hardwareSafeVanillaTerrainReason,
                        pipelineFrameId
                );
            }
            return;
        }
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        if (shouldSkipStationaryShadowMap(world, viewEntity, partialTicks)) {
            lastShadowFrameId = pipelineFrameId;
            return;
        }

        runPreparePassesBeforeShadowIfRequested();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-begin world=" + safeDimensionId(world));
        lastShadowFrameId = pipelineFrameId;
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousRenderChunksMany = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldBoolean((mc), false, "field_175612_E", "renderChunksMany");

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        try {
            setupShadowCamera(viewEntity, partialTicks);
            ICamera shadowCamera = createShadowCamera(viewEntity, partialTicks);
            // Iris disables chunk occlusion culling while building the shadow terrain list.
            // The 1.12 equivalent is renderChunksMany; leaving it enabled lets the normal
            // camera visibility graph leak into the light-space pass.
            com.l.ausm.impl.util.MinecraftReflectionCompat.setRenderChunksMany(mc, false);
            boolean useNothiriumShadowBridge = shouldUseNothiriumShadowBridge();
            if (!useNothiriumShadowBridge) {
                ensureVanillaTerrainRenderer();
                com.l.ausm.impl.util.MinecraftReflectionCompat.setupTerrain(
                        com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc),
                        viewEntity,
                        partialTicks,
                        shadowCamera,
                        nextShadowFrameCount(),
                        com.l.ausm.impl.util.MinecraftReflectionCompat.playerIsSpectator(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc))
                );
            }

            clearColoredLightImages();
            boolean renderShadowTerrain = shaderProperties.renderSettings().shadowTerrain()
                    && hasShadowTerrainCandidates(mc, viewEntity, partialTicks);
            if (useNothiriumShadowBridge) {
                nothiriumShadowRenderer.drainUploads();
            }

            shadowFramebuffer.bindForRendering();
            shadowFramebuffer.clear();
            configureShadowTerrainRenderState();
            if (shadowPolygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
                GL11.glPolygonOffset(shadowPolygonOffsetFactor, shadowPolygonOffsetUnits);
            }
            TextureBinder.restoreDefaultTextureUnit();
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.blocksTexture());

            renderingShadowMap = true;
            int solidCount = -1;
            int cutoutMippedCount = -1;
            int cutoutCount = -1;
            int translucentCount = -1;
            int blockEntityCount = -1;
            if (renderShadowTerrain) {
                solidCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
                cutoutMippedCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
                cutoutCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            }
            if (shaderProperties.renderSettings().shadowEntities()
                    || shaderProperties.renderSettings().shadowPlayer()) {
                beginPhase(WorldRenderingPhase.ENTITIES);
                // RenderLib replaces RenderGlobal.renderEntities with a queued renderer
                // that is only prepared during the normal world pass. The shadow pass
                // has its own camera, so render entities directly here.
                renderShadowEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            if (shaderProperties.renderSettings().shadowBlockEntities()
                    || shaderProperties.renderSettings().shadowLightBlockEntities()) {
                beginPhase(WorldRenderingPhase.BLOCK_ENTITIES);
                blockEntityCount = renderShadowBlockEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            shadowFramebuffer.copyDepthToSnapshot();
            if (renderShadowTerrain && shaderProperties.renderSettings().shadowTranslucent()) {
                translucentCount = renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            }
            injectMappedTileEntityVoxels(mc);
            applyShaderImageTextureBarrier();
            shadowFramebuffer.generateShadowColorMipmaps();
            updateShadowMapUsability(solidCount, cutoutMippedCount, cutoutCount, translucentCount, blockEntityCount);
            runComputePrograms(shadowComputePrograms, RenderPass.SHADOW);
            runFullscreenPasses(ProgramArrayId.SHADOWCOMP);
            if (shadowMapUsable) {
                rememberShadowMapRender(world, viewEntity, partialTicks);
            } else {
                resetShadowRenderCache();
            }
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setRenderChunksMany(mc, previousRenderChunksMany);
            renderingShadowMap = false;
            activePass = null;
            activeShaderKey = null;
            activePhase = WorldRenderingPhase.NONE;
            overridePhase = null;
            passStack.clear();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(true, true, true, true);
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), previousFramebuffer);
            viewportBuffer.position(0);
            GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));
            TextureBinder.restoreDefaultTextureUnit();
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-end world=" + safeDimensionId(world));
        }
    }

    protected boolean shouldSkipStationaryShadowMap(World world, Entity viewEntity, float partialTicks) {
        if (!shadowMapPopulated || shaderProperties == null
                || shaderProperties.renderSettings().shadowEntities()
                || shaderProperties.renderSettings().shadowPlayer()) {
            return false;
        }
        int dimensionId = safeDimensionId(world);
        Object time = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                world,
                new String[] {"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        long worldTime = time instanceof Number ? ((Number) time).longValue() : 0L;
        if (dimensionId != lastShadowRenderDimensionId || worldTime != lastShadowRenderWorldTime) {
            return false;
        }
        double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double dx = x - lastShadowRenderX;
        double dy = y - lastShadowRenderY;
        double dz = z - lastShadowRenderZ;
        return dx * dx + dy * dy + dz * dz < 0.0001D;
    }

    protected void rememberShadowMapRender(World world, Entity viewEntity, float partialTicks) {
        lastShadowRenderDimensionId = safeDimensionId(world);
        Object time = com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(
                world,
                new String[] {"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        lastShadowRenderWorldTime = time instanceof Number ? ((Number) time).longValue() : 0L;
        lastShadowRenderX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        lastShadowRenderY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        lastShadowRenderZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
    }

    protected void resetShadowRenderCache() {
        lastShadowRenderDimensionId = Integer.MIN_VALUE;
        lastShadowRenderWorldTime = Long.MIN_VALUE;
        lastShadowRenderX = Double.NaN;
        lastShadowRenderY = Double.NaN;
        lastShadowRenderZ = Double.NaN;
    }

    protected int positiveShadowCount(int count) {
        return Math.max(0, count);
    }

    protected void injectMappedTileEntityVoxels(Minecraft mc) {
        World world = renderWorld(mc);
        if (!ENABLE_CPU_LIGHT_INJECTION || !shaderImages.active() || world == null) {
            return;
        }

        int[] dimensions = shaderImages.dimensions("voxel_img", "voxelimg", "voxel_sampler", "voxeltex");
        if (dimensions == null) {
            return;
        }

        int cameraFloorX = (int) Math.floor(cameraPositionUnshifted[0]);
        int cameraFloorY = (int) Math.floor(cameraPositionUnshifted[1]);
        int cameraFloorZ = (int) Math.floor(cameraPositionUnshifted[2]);
        int injected = 0;
        int[] projectRedVoxelIds = cpuLightProjectRedVoxelIds;
        Set<Long> writtenVoxels = cpuLightWrittenVoxels;
        writtenVoxels.clear();

        List<TileEntity> loadedTileEntities = cpuLightTileEntitySnapshot(world);
        int tileEntityCount = loadedTileEntities.size();
        int scanCount = Math.min(tileEntityCount, MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME);
        int projectRedMatches = 0;
        for (int scan = 0; scan < scanCount; scan++) {
            if (injected >= MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME) {
                break;
            }
            if (tileEntityCount <= 0) {
                break;
            }
            if (cpuLightTileEntityScanCursor >= tileEntityCount) {
                cpuLightTileEntityScanCursor = 0;
            }
            TileEntity tileEntity = loadedTileEntities.get(cpuLightTileEntityScanCursor++);
            if (tileEntity == null || com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityInvalid(tileEntity)) {
                continue;
            }

            BlockPos pos = com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityPos(tileEntity);
            if (!isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                continue;
            }

            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "scan");
            if (projectRedCount > 0) {
                projectRedMatches++;
                for (int i = 0; i < projectRedCount && injected < MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME; i++) {
                    if (injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "injected:" + projectRedVoxelIds[i]);
                    } else {
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "write_failed:" + projectRedVoxelIds[i]);
                    }
                }
                continue;
            }

            // Generic shader block-id lights are handled by the shaderpack shadow voxelizer.
            // Keep this CPU path restricted to ProjectRed tile entities to avoid global tint leaks.
        }

        if (ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION) {
            injected += injectRecordedSyntheticLightVoxels(
                    world,
                    dimensions,
                    cameraFloorX,
                    cameraFloorY,
                    cameraFloorZ,
                    writtenVoxels,
                    MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
            );

            injected += injectVoxelizedLightBlockVoxels(
                    world,
                    dimensions,
                    cameraFloorX,
                    cameraFloorY,
                    cameraFloorZ,
                    writtenVoxels,
                    MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
            );
        }

        if (injected > 0 && GLContext.getCapabilities().OpenGL42) {
            GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL42.GL_TEXTURE_FETCH_BARRIER_BIT);
        }
        if (coloredLightInjectionProbeLogs < 24) {
            coloredLightInjectionProbeLogs++;
            MainMod.LOGGER.info(
                    "[AUSMColoredLightInjection] probe={} frame={} dimension={} volume={}x{}x{} tiles={}/{} projectRedMatches={} injected={} candidates={} glError={}",
                    coloredLightInjectionProbeLogs,
                    pipelineFrameId,
                    safeDimensionId(world),
                    dimensions[0],
                    dimensions[1],
                    dimensions[2],
                    scanCount,
                    tileEntityCount,
                    projectRedMatches,
                    injected,
                    syntheticLightCandidates.size(),
                    GL11.glGetError()
            );
        }
    }

    protected List<TileEntity> cpuLightTileEntitySnapshot(World world) {
        if (world == null) {
            cpuLightTileEntitySnapshotWorld = null;
            cpuLightTileEntitySnapshot = Collections.emptyList();
            cpuLightTileEntitySnapshotFrame = Long.MIN_VALUE;
            cpuLightTileEntityScanCursor = 0;
            return cpuLightTileEntitySnapshot;
        }

        boolean worldChanged = cpuLightTileEntitySnapshotWorld != world;
        boolean refresh = worldChanged
                || cpuLightTileEntitySnapshotFrame == Long.MIN_VALUE
                || pipelineFrameId - cpuLightTileEntitySnapshotFrame >= CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES;
        if (refresh) {
            cpuLightTileEntitySnapshotWorld = world;
            cpuLightTileEntitySnapshotFrame = pipelineFrameId;
            cpuLightTileEntitySnapshot = new ArrayList<>(com.l.ausm.impl.util.MinecraftReflectionCompat.worldLoadedTileEntities(world));
            if (worldChanged || cpuLightTileEntitySnapshot.isEmpty()) {
                cpuLightTileEntityScanCursor = 0;
            } else {
                cpuLightTileEntityScanCursor = Math.floorMod(cpuLightTileEntityScanCursor, cpuLightTileEntitySnapshot.size());
            }
        }
        return cpuLightTileEntitySnapshot;
    }

    protected int injectVoxelizedLightBlockVoxels(World world, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                                Set<Long> writtenVoxels, int remainingBudget) {
        if (remainingBudget <= 0 || !shaderProperties.renderSettings().voxelizeLightBlocks()) {
            return 0;
        }
        if (world == null || dimensions == null || dimensions.length < 3) {
            return 0;
        }
        if (cpuLightBlockScanWorld != world) {
            cpuLightBlockScanWorld = world;
            cpuLightBlockScanCursor = 0;
        }

        int scanWidth = Math.max(1, Math.min(dimensions[0], MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH));
        int scanHeight = Math.max(1, Math.min(dimensions[1], MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT));
        int scanDepth = Math.max(1, Math.min(dimensions[2], MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH));
        int scanVolume = scanWidth * scanHeight * scanDepth;
        int scanBudget = Math.min(MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME, scanVolume);
        int injected = 0;

        for (int scan = 0; scan < scanBudget && injected < remainingBudget; scan++) {
            if (cpuLightBlockScanCursor >= scanVolume) {
                cpuLightBlockScanCursor = 0;
            }
            int cursor = cpuLightBlockScanCursor++;
            int localX = cursor % scanWidth;
            int localY = (cursor / scanWidth) % scanHeight;
            int localZ = cursor / (scanWidth * scanHeight);
            BlockPos pos = new BlockPos(
                    cameraFloorX + localX - scanWidth / 2,
                    cameraFloorY + localY - scanHeight / 2,
                    cameraFloorZ + localZ - scanDepth / 2
            );
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) < 0 || com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) > 255 || !com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
                continue;
            }

            IBlockState state;
            try {
                state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos);
            } catch (RuntimeException ignored) {
                continue;
            }
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                continue;
            }
            if (injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
                auditSyntheticLight("voxelize_light_blocks", pos, lightInfo, "injected");
            }
        }

        return injected;
    }

    protected int injectRecordedSyntheticLightVoxels(World world, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                                   Set<Long> writtenVoxels, int remainingBudget) {
        if (remainingBudget <= 0 || syntheticLightCandidates.isEmpty()) {
            return 0;
        }

        int injected = 0;
        for (Map.Entry<Long, BlockPos> entry : syntheticLightCandidates.entrySet()) {
            if (injected >= remainingBudget) {
                break;
            }
            BlockPos pos = entry.getValue();
            if (pos == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
                if (isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }
            if (!isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                if (isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }

            TileEntity tileEntity;
            try {
                tileEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.call((world), net.minecraft.tileentity.TileEntity.class, null, new String[] {"func_175625_s", "getTileEntity"},
                new Class<?>[] {net.minecraft.util.math.BlockPos.class}, (pos));
            } catch (RuntimeException ignored) {
                tileEntity = null;
            }
            int[] projectRedVoxelIds = new int[8];
            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            if (projectRedCount > 0) {
                for (int i = 0; i < projectRedCount && injected < remainingBudget; i++) {
                    if (injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "candidate_injected:" + projectRedVoxelIds[i]);
                    } else {
                        auditProjectRedLight(tileEntity, projectRedVoxelIds, projectRedCount, "candidate_write_failed:" + projectRedVoxelIds[i]);
                    }
                }
                continue;
            }

            IBlockState state = actualLightState(com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos), world, pos);
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
                auditSyntheticLight("cpu_inject", pos, lightInfo, "drop:" + lightInfo.reason);
                continue;
            }

            if (injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
                auditSyntheticLight("cpu_inject", pos, lightInfo, "injected");
            } else {
                auditSyntheticLight("cpu_inject", pos, lightInfo, "write_failed");
            }
        }
        return injected;
    }

    protected boolean isWellOutsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        return Math.abs(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) - cameraFloorX) > dimensions[0]
                || Math.abs(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) - cameraFloorY) > dimensions[1]
                || Math.abs(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) - cameraFloorZ) > dimensions[2];
    }

    protected boolean isInsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        int x = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        return x >= 0 && y >= 0 && z >= 0
                && x < dimensions[0] && y < dimensions[1] && z < dimensions[2];
    }

    protected boolean injectVoxelAt(BlockPos pos, int voxelId, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                  Set<Long> writtenVoxels) {
        if (voxelId <= 0) {
            return false;
        }

        int x = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        if (x < 0 || y < 0 || z < 0 || x >= dimensions[0] || y >= dimensions[1] || z >= dimensions[2]) {
            return false;
        }
        if (writtenVoxels != null) {
            writtenVoxels.add(packedVoxelKey(x, y, z));
        }
        return shaderImages.writeRedInteger3D(x, y, z, voxelId, "voxel_img", "voxelimg", "voxel_sampler", "voxeltex");
    }

    protected static long packedVoxelKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ z;
    }

    protected static int localActVoxelId(int materialId) {
        if (materialId == 12003 || materialId == 12283) {
            return 3;
        }
        if (materialId == 10900 || materialId == 12024) {
            return 24;
        }
        if (materialId >= 10902 && materialId <= 10922 && (materialId & 1) == 0) {
            return 69 + (materialId - 10900) / 2;
        }
        if (materialId >= 12070 && materialId <= 12080) {
            return materialId - 12000;
        }
        if (materialId >= 12270 && materialId <= 12280) {
            return materialId - 12160;
        }
        return 0;
    }

    protected static int compatSyntheticLightVoxelId(IBlockState state) {
        ResourceLocation name = registryName(state);
        if (name == null) {
            return 0;
        }
        if ("tconstruct".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && "seared_furnace_controller".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name))
                && stateName(state).contains("active=true")) {
            return 71;
        }
        if ("aether_legacy".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourceNamespace(name))
                && "aether_portal".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.resourcePath(name))) {
            return localActVoxelId(10914); // Aether sky blue.
        }
        int astralVoxel = astralCrystalVoxelId(state);
        if (astralVoxel > 0) {
            return astralVoxel;
        }
        return 0;
    }

    protected void clearColoredLightImages() {
        shaderImages.clearSmallImages();
        shaderImages.clearNamedImages(
                "voxel_img", "voxelimg", "voxel_sampler", "voxeltex"
        );
    }

    protected boolean hasShadowTerrainCandidates(Minecraft mc, Entity viewEntity, float partialTicks) {
        if (shouldUseNothiriumShadowBridge()) {
            return true;
        }

        if (mc == null || viewEntity == null || !(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return true;
        }
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        RenderChunk[] renderChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderChunks == null) {
            return true;
        }

        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            BlockPos position = com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkPosition(renderChunk);
            double dx = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(position) + 8.0D - cameraX;
            double dy = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(position) + 8.0D - cameraY;
            double dz = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(position) + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            if (!com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.SOLID)
                    || !com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.CUTOUT_MIPPED)
                    || !com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.CUTOUT)
                    || (shaderProperties.renderSettings().shadowTranslucent()
                    && !com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.TRANSLUCENT))) {
                return true;
            }
        }
        return false;
    }

    protected static void configureShadowTerrainRenderState() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        resetPortalMaskState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
    }

    protected static void resetPortalMaskState() {
        GL11.glStencilMask(0xFF);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0, 0xFF);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        for (int i = 0; i < 6; i++) {
            GL11.glDisable(GL11.GL_CLIP_PLANE0 + i);
        }
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    protected int renderShadowTerrainLayer(Minecraft mc, WorldRenderingPhase phase, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        beginPhase(phase);
        configureShadowTerrainRenderState();
        boolean previousPolygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT && previousPolygonOffset) {
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        }
        if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            GL11.glDepthFunc(GL11.GL_ALWAYS);
        }
        try {
            int count = renderShadowBlockLayer(mc, layer, partialTicks, viewEntity);
            return count;
        } finally {
            GL11.glDepthFunc(previousDepthFunc);
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousPolygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            endPass();
        }
    }

    protected int renderShadowBlockLayer(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (mc == null || viewEntity == null) {
            return 0;
        }
        if (shouldUseNothiriumShadowBridge()) {
            double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
            double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
            double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
            return nothiriumShadowRenderer.renderLayer(layer, cameraX, cameraY, cameraZ, shadowRenderCullDistance());
        }

        RenderGlobal renderGlobal = com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc);
        if (renderGlobal == null) {
            return 0;
        }
        int count = com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, 2, viewEntity);
        if (count != 0) {
            return count;
        }
        return renderShadowBlockLayerFromViewFrustum(mc, layer, partialTicks, viewEntity);
    }

    protected void updateShadowMapUsability(int solidCount, int cutoutMippedCount, int cutoutCount, int translucentCount, int blockEntityCount) {
        if (shadowFramebuffer == null) {
            shadowMapPopulated = false;
            shadowMapUsable = false;
            shadowMapSparseForSampling = false;
            shadowMapCoverageStableFrames = 0;
            return;
        }
        ShadowFramebuffer.DepthStats stats = shadowFramebuffer.readDepthStats(4);
        boolean terrainPopulated = solidCount > 0
                || cutoutMippedCount > 0
                || cutoutCount > 0
                || translucentCount > 0;
        boolean drawPopulated = terrainPopulated || blockEntityCount > 0;
        boolean populated = terrainPopulated
                || (!shouldUseNothiriumShadowBridge() && stats.nonClear() > 0);
        shadowMapPopulated = populated || drawPopulated;
        int terrainDrawCount = positiveShadowCount(solidCount)
                + positiveShadowCount(cutoutMippedCount)
                + positiveShadowCount(cutoutCount)
                + positiveShadowCount(translucentCount);
        World renderWorld = renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        int dimensionId = safeDimensionId(renderWorld);
        float verticalDelta = cameraVerticalDelta();
        boolean upwardMotion = verticalDelta > SHADOW_UPWARD_CAMERA_DELTA_SUPPRESSION;
        boolean useNothiriumShadowBridge = shouldUseNothiriumShadowBridge();
        boolean nothiriumTerrainCoverageReady = !useNothiriumShadowBridge
                || (terrainDrawCount >= SPARSE_SHADOW_MIN_TERRAIN_DRAWS
                && stats.nonClear() >= SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES);
        if (nothiriumTerrainCoverageReady) {
            shadowMapCoverageStableFrames = Math.min(SPARSE_SHADOW_STABLE_FRAMES, shadowMapCoverageStableFrames + 1);
        } else {
            shadowMapCoverageStableFrames = 0;
        }
        boolean nothiriumTerrainStable = !useNothiriumShadowBridge
                || shadowMapCoverageStableFrames >= SPARSE_SHADOW_STABLE_FRAMES;
        boolean sparseNothiriumShadow = !nothiriumTerrainCoverageReady || !nothiriumTerrainStable;
        boolean unstableSparseShadow = sparseNothiriumShadow && upwardMotion;
        shadowMapSparseForSampling = sparseNothiriumShadow;
        shadowMapUsable = stats.nonClear() > 0
                && !sparseNothiriumShadow
                && !unstableSparseShadow;
        if (useNothiriumShadowBridge && !shadowMapUsable && drawPopulated) {
            nothiriumShadowInvalidFrames++;
            if (nothiriumShadowInvalidFrames >= NOTHIRIUM_SHADOW_SUPPRESS_AFTER_INVALID_FRAMES) {
                nothiriumShadowInvalidFrames = 0;
                nothiriumShadowSuppressedFrames = Math.max(nothiriumShadowSuppressedFrames, NOTHIRIUM_SHADOW_SUPPRESS_FRAMES);
                if (nothiriumShadowSuppressionLogs < 0) {
                    nothiriumShadowSuppressionLogs++;
                    MainMod.LOGGER.info(
                            "[ShadowHealth] Suppressing Nothirium shadow terrain after repeated invalid output. nonClear={}/{} terrainDraws={} blockEntities={} suppressFrames={} dim={}",
                            stats.nonClear(),
                            stats.total(),
                            terrainDrawCount,
                            blockEntityCount,
                            nothiriumShadowSuppressedFrames,
                            dimensionId
                    );
                }
            }
        } else if (shadowMapUsable) {
            nothiriumShadowInvalidFrames = 0;
            nothiriumShadowSuppressedFrames = 0;
        }

        if (!shadowHealthLogged && shadowHealthLogAttempts < 0) {
            shadowHealthLogAttempts++;
            shadowHealthLogged = populated && shadowMapUsable;
            MainMod.LOGGER.info(
                    "[ShadowHealth] depth center={} min={} max={} nonClear={}/{} terrainCounts solid={} cutoutMipped={} cutout={} translucent={} terrainDraws={} minTerrainDraws={} minNonClear={} stableFrames={}/{} blockEntities={} dim={} sparseNothirium={} verticalDelta={} usable={}",
                    stats.center(),
                    stats.min(),
                    stats.max(),
                    stats.nonClear(),
                    stats.total(),
                    solidCount,
                    cutoutMippedCount,
                    cutoutCount,
                    translucentCount,
                    terrainDrawCount,
                    SPARSE_SHADOW_MIN_TERRAIN_DRAWS,
                    SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES,
                    shadowMapCoverageStableFrames,
                    SPARSE_SHADOW_STABLE_FRAMES,
                    blockEntityCount,
                    dimensionId,
                    sparseNothiriumShadow,
                    verticalDelta,
                    shadowMapUsable
            );
        }
        if (stats.nonClear() > 0
                && (sparseNothiriumShadow || unstableSparseShadow)
                && shadowMapSuppressedLogs < 0) {
            shadowMapSuppressedLogs++;
            MainMod.LOGGER.info(
                    "[ShadowHealth] Sparse Nothirium shadow map observed; keeping real shadow textures bound. reason={} dim={} nonClear={}/{} minNonClear={} terrainDraws={} minTerrainDraws={} stableFrames={}/{} verticalDelta={} upwardThreshold={} terrainCounts solid={} cutoutMipped={} cutout={} translucent={} blockEntities={}",
                    nothiriumTerrainCoverageReady ? "warming-up" : (unstableSparseShadow ? "sparse-terrain-upward-camera-motion" : "sparse-terrain"),
                    dimensionId,
                    stats.nonClear(),
                    stats.total(),
                    SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES,
                    terrainDrawCount,
                    SPARSE_SHADOW_MIN_TERRAIN_DRAWS,
                    shadowMapCoverageStableFrames,
                    SPARSE_SHADOW_STABLE_FRAMES,
                    verticalDelta,
                    SHADOW_UPWARD_CAMERA_DELTA_SUPPRESSION,
                    solidCount,
                    cutoutMippedCount,
                    cutoutCount,
                    translucentCount,
                    blockEntityCount
            );
        }
        if (drawPopulated && !shadowMapUsable && shadowMapInvalidLogs < 0) {
            shadowMapInvalidLogs++;
            MainMod.LOGGER.info(
                    "[ShadowHealth] Shadow map draw produced clear/sparse depth; keeping real shadow textures bound. nonClear={}/{} terrainCounts solid={} cutoutMipped={} cutout={} translucent={} blockEntities={}",
                    stats.nonClear(),
                    stats.total(),
                    solidCount,
                    cutoutMippedCount,
                    cutoutCount,
                    translucentCount,
                    blockEntityCount
            );
        }
    }

    protected int renderShadowBlockLayerFromViewFrustum(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (mc == null || viewEntity == null || !(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return 0;
        }
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        ChunkRenderContainer renderContainer = renderGlobal.ausm$renderContainer();
        RenderChunk[] renderChunks = com.l.ausm.impl.util.MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderChunks == null || renderContainer == null) {
            return 0;
        }

        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        renderContainer.initialize(cameraX, cameraY, cameraZ);
        int fallbackCount = 0;
        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, layer)) {
                continue;
            }
            BlockPos position = com.l.ausm.impl.util.MinecraftReflectionCompat.renderChunkPosition(renderChunk);
            double dx = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(position) + 8.0D - cameraX;
            double dy = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(position) + 8.0D - cameraY;
            double dz = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(position) + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            renderContainer.addRenderChunk(renderChunk, layer);
            fallbackCount++;
        }

        if (fallbackCount > 0) {
            renderContainer.renderChunkLayer(layer);
        }
        return fallbackCount;
    }

    protected double shadowRenderCullDistance() {
        if (!shouldCullShadowTerrain()) {
            return -1.0D;
        }
        if (shaderProperties.renderSettings().shadowCullingReversed()) {
            return Math.max(32.0D, Math.max(shadowMapDistance, voxelDistance));
        }
        if (shadowDistanceRenderMul < 0.0f) {
            return -1.0D;
        }
        return Math.max(32.0D, shadowMapDistance * shadowDistanceRenderMul + 32.0D);
    }

    protected int nextShadowFrameCount() {
        if (shadowFrameCount == Integer.MAX_VALUE) {
            shadowFrameCount = 1_000_000;
        }
        return shadowFrameCount++;
    }

    protected void setupShadowCamera(Entity viewEntity, float partialTicks) {
        double x = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double y = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double z = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        float shadowDepthRange = Math.max(256.0F, shadowMapDistance * 2.0F);
        float shadowDepthCenter = shadowDepthRange * 0.5F;
        GL11.glOrtho(
                -shadowMapDistance,
                shadowMapDistance,
                -shadowMapDistance,
                shadowMapDistance,
                0.05F,
                shadowDepthRange
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -shadowDepthCenter);

        World world = renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        if (world != null && useEndFlashShadowLight(world)) {
            GL11.glRotatef(90.0F - endFlashPitchDegrees, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(endFlashYawDegrees, 0.0F, 1.0F, 0.0F);
        } else {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
            float celestialAngle = world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks) : 0.0F;
            float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
            float angle = celestialAngle * -360.0F;
            if (sunAngle <= 0.5F) {
                GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
            } else {
                GL11.glRotatef(angle + 180.0F, 0.0F, 0.0F, 1.0F);
            }
            GL11.glRotatef(sunPathRotation, 1.0F, 0.0F, 0.0F);
        }

        double interval = Math.max(0.001F, shadowIntervalSize);
        double snapX = centeredRemainder(x, interval);
        double snapY = centeredRemainder(y, interval);
        double snapZ = centeredRemainder(z, interval);
        GL11.glTranslatef((float) snapX, (float) snapY, (float) snapZ);
        MatrixState.captureShadowMatrices();
    }

    protected static float shadowAngle(float partialTicks) {
        World world = renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        float celestialAngle = world != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks) : 0.0F;
        float angle = celestialAngle + 0.25F;
        if (angle >= 1.0F) {
            angle -= 1.0F;
        }
        return angle;
    }

    protected ICamera createShadowCamera(Entity viewEntity, float partialTicks) {
        ICamera celeritasCamera = createCeleritasShadowCamera(viewEntity, partialTicks);
        if (celeritasCamera != null) {
            return celeritasCamera;
        }
        return createVanillaShadowCamera();
    }

    protected ICamera createVanillaShadowCamera() {
        return ALWAYS_VISIBLE_CAMERA;
    }

    protected ICamera createCeleritasShadowCamera(Entity viewEntity, float partialTicks) {
        try {
            if (!resolveCeleritasShadowCameraReflection()) {
                return null;
            }

            double[] position = {
                    interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks),
                    interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks),
                    interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks)
            };
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("sodium$createViewport".equals(name)) {
                    Object cameraPosition = celeritasVectorConstructor.newInstance(position[0], position[1], position[2]);
                    return celeritasViewportConstructor.newInstance(celeritasAlwaysVisibleFrustum, cameraPosition);
                }
                if ("isBoundingBoxInFrustum".equals(name) || "func_78546_a".equals(name)) {
                    return true;
                }
                if ("setPosition".equals(name) || "func_78547_a".equals(name)) {
                    if (args != null && args.length == 3) {
                        position[0] = ((Number) args[0]).doubleValue();
                        position[1] = ((Number) args[1]).doubleValue();
                        position[2] = ((Number) args[2]).doubleValue();
                    }
                    return null;
                }
                if ("toString".equals(name)) {
                    return "AUSM Celeritas shadow camera";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return args != null && args.length == 1 && proxy == args[0];
                }
                return null;
            };

            return (ICamera) Proxy.newProxyInstance(
                    PipelineContext.class.getClassLoader(),
                    new Class<?>[]{ICamera.class, celeritasViewportProviderClass},
                    handler
            );
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            if (!celeritasShadowCameraWarningLogged) {
                celeritasShadowCameraWarningLogged = true;
                MainMod.LOGGER.warn("[Pipeline] Failed to create Celeritas-compatible shadow camera; falling back to vanilla camera", e);
            }
            return null;
        }
    }

    protected boolean resolveCeleritasShadowCameraReflection() throws ReflectiveOperationException {
        if (celeritasShadowCameraResolved) {
            return celeritasViewportProviderClass != null;
        }
        celeritasShadowCameraResolved = true;

        ClassLoader loader = PipelineContext.class.getClassLoader();
        try {
            celeritasViewportProviderClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.ViewportProvider", false, loader);
            Class<?> viewportClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.Viewport", false, loader);
            Class<?> frustumClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum", false, loader);
            Class<?> vector3dClass = Class.forName(
                    "org.embeddedt.embeddium.impl.shadow.joml.Vector3d", false, loader);
            celeritasViewportConstructor = viewportClass.getConstructor(frustumClass, vector3dClass);
            celeritasVectorConstructor = vector3dClass.getConstructor(double.class, double.class, double.class);
            celeritasAlwaysVisibleFrustum = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{frustumClass},
                    (proxy, method, args) -> boolean.class.equals(method.getReturnType()) ? Boolean.TRUE : null
            );
            return true;
        } catch (ClassNotFoundException e) {
            celeritasViewportProviderClass = null;
            celeritasViewportConstructor = null;
            celeritasVectorConstructor = null;
            celeritasAlwaysVisibleFrustum = null;
            return false;
        }
    }

    protected void renderShadowEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowEntities() && !shaderProperties.renderSettings().shadowPlayer()) {
            return;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) == null) {
            return;
        }
        RenderManager renderManager = com.l.ausm.impl.util.MinecraftReflectionCompat.renderManager(mc);
        if (renderManager == null) {
            return;
        }
        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);

        com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerCacheActiveRenderInfo(renderManager, world, com.l.ausm.impl.util.MinecraftReflectionCompat.fontRenderer(mc), viewEntity, com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), net.minecraft.entity.Entity.class, null, "field_147125_j", "pointedEntity"), com.l.ausm.impl.util.MinecraftReflectionCompat.gameSettings(mc), partialTicks);
        com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerSetRenderPosition(renderManager, cameraX, cameraY, cameraZ);
        com.l.ausm.impl.util.MinecraftReflectionCompat.enableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        com.l.ausm.impl.util.MinecraftReflectionCompat.enableStandardItemLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);

        List<Entity> loadedEntities = com.l.ausm.impl.util.MinecraftReflectionCompat.loadedEntityList(world);
        if (loadedEntities == null) {
            return;
        }
        for (Entity entity : loadedEntities) {
            if (!shouldRenderEntityInShadowMap(mc, world, renderManager, entity, viewEntity, shadowCamera, cameraX, cameraY, cameraZ)) {
                continue;
            }

            com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerRenderEntityStatic(renderManager, entity, partialTicks, false);
            if (com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerIsRenderMultipass(renderManager, entity)) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerRenderMultipass(renderManager, entity, partialTicks);
            }
        }
    }

    protected int renderShadowBlockEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !shaderProperties.renderSettings().shadowLightBlockEntities()) {
            return 0;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null) {
            return 0;
        }

        TileEntityRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityRendererDispatcher();
        if (dispatcher == null) {
            return 0;
        }

        double cameraX = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosX(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosY(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = interpolate(com.l.ausm.impl.util.MinecraftReflectionCompat.lastTickPosZ(viewEntity), com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;
        List<TileEntity> tileEntities = cpuLightTileEntitySnapshot(world);
        refreshShadowBlockEntityBoundsCache(world, tileEntities);

        com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityRendererPrepare(
                dispatcher,
                world,
                com.l.ausm.impl.util.MinecraftReflectionCompat.textureManager(mc),
                com.l.ausm.impl.util.MinecraftReflectionCompat.fontRenderer(mc),
                viewEntity,
                com.l.ausm.impl.util.MinecraftReflectionCompat.field((mc), net.minecraft.util.math.RayTraceResult.class, null, "field_71476_x", "objectMouseOver"),
                partialTicks
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.enableLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc));
        com.l.ausm.impl.util.MinecraftReflectionCompat.enableStandardItemLighting();
        configureShadowTerrainRenderState();

        int rendered = 0;
        for (TileEntity tileEntity : tileEntities) {
            BlockPos pos = com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityPos(tileEntity);
            if (!shouldRenderBlockEntityInShadowMap(
                    world, tileEntity, pos, shadowCamera, cameraX, cameraY, cameraZ, maxDistanceSquared)) {
                continue;
            }

            com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityRendererRender(
                    dispatcher,
                    tileEntity,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) - cameraX,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) - cameraY,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) - cameraZ,
                    partialTicks,
                    -1,
                    1.0F
            );
            rendered++;
        }
        return rendered;
    }

    protected boolean shouldRenderBlockEntityInShadowMap(World world, TileEntity tileEntity, BlockPos pos, ICamera shadowCamera,
                                                       double cameraX, double cameraY, double cameraZ,
                                                       double maxDistanceSquared) {
        if (world == null || tileEntity == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.tileEntityInvalid(tileEntity)) {
            return false;
        }

        if (pos == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
            return false;
        }
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !isLightEmittingBlockEntity(world, tileEntity, pos)) {
            return false;
        }

        double dx = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos) + 0.5D - cameraX;
        double dy = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos) + 0.5D - cameraY;
        double dz = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos) + 0.5D - cameraZ;
        if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
            return false;
        }

        AxisAlignedBB box = cachedShadowBlockEntityFrustumBox(tileEntity, pos);
        return box == null || com.l.ausm.impl.util.MinecraftReflectionCompat.cameraIsBoundingBoxInFrustum(shadowCamera, box);
    }

    protected void refreshShadowBlockEntityBoundsCache(World world, List<TileEntity> tileEntities) {
        if (shadowBlockEntityBoundsCacheWorld != world) {
            shadowBlockEntityBoundsCacheWorld = world;
            shadowBlockEntityBoundsCache.clear();
            return;
        }
        int expectedSize = tileEntities != null ? tileEntities.size() : 0;
        if (shadowBlockEntityBoundsCache.size() > expectedSize * 2 + 256) {
            shadowBlockEntityBoundsCache.clear();
        }
    }

    protected AxisAlignedBB cachedShadowBlockEntityFrustumBox(TileEntity tileEntity, BlockPos pos) {
        if (tileEntity == null || pos == null) {
            return null;
        }
        int x = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosX(pos);
        int y = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosY(pos);
        int z = com.l.ausm.impl.util.MinecraftReflectionCompat.blockPosZ(pos);
        ShadowBlockEntityBounds cached = shadowBlockEntityBoundsCache.get(tileEntity);
        if (cached != null && cached.x() == x && cached.y() == y && cached.z() == z) {
            return cached.bounds();
        }

        AxisAlignedBB bounds = new AxisAlignedBB(x - 1.0D, y - 1.0D, z - 1.0D, x + 2.0D, y + 2.0D, z + 2.0D);
        shadowBlockEntityBoundsCache.put(tileEntity, new ShadowBlockEntityBounds(x, y, z, bounds));
        return bounds;
    }

    protected static boolean isLightEmittingBlockEntity(World world, TileEntity tileEntity, BlockPos pos) {
        try {
            IBlockState state = com.l.ausm.impl.util.MinecraftReflectionCompat.worldBlockState(world, pos);
            return state != null && com.l.ausm.impl.util.MinecraftReflectionCompat.stateLightValue(state, world, pos) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    protected boolean shouldRenderEntityInShadowMap(Minecraft mc, World world, RenderManager renderManager, Entity entity, Entity viewEntity,
                                                  ICamera shadowCamera, double cameraX, double cameraY, double cameraZ) {
        if (mc == null || world == null || renderManager == null || entity == null || com.l.ausm.impl.util.MinecraftReflectionCompat.entityIsDead(entity) || !com.l.ausm.impl.util.MinecraftReflectionCompat.shouldRenderInPass(entity, 0)) {
            return false;
        }
        if (BetterPortalsCompat.isPortalEntity(entity)) {
            return false;
        }
        if (entity instanceof AbstractClientPlayer player && com.l.ausm.impl.util.MinecraftReflectionCompat.playerIsSpectator(player)) {
            return false;
        }
        if (entity == viewEntity
                && !shaderProperties.renderSettings().shadowEntities()
                && !shaderProperties.renderSettings().shadowPlayer()) {
            return false;
        }
        if (entity != viewEntity && !shaderProperties.renderSettings().shadowEntities()) {
            return false;
        }
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.renderManagerShouldRender(renderManager, entity, shadowCamera, cameraX, cameraY, cameraZ)
                && (com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null || !com.l.ausm.impl.util.MinecraftReflectionCompat.entityIsRidingOrBeingRiddenBy(entity, com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)))) {
            return false;
        }
        double entityY = com.l.ausm.impl.util.MinecraftReflectionCompat.posY(entity);
        if (entityY >= 0.0D && entityY < 256.0D && !com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(world, new BlockPos(
                com.l.ausm.impl.util.MinecraftReflectionCompat.posX(entity),
                entityY,
                com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(entity)))) {
            return false;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.entityIsInRangeToRender3d(entity, cameraX, cameraY, cameraZ);
    }

    protected static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    protected static int eyeFluidState(Minecraft mc) {
        if (mc == null) {
            return 0;
        }
        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return 0;
        }

        IBlockState cameraState = com.l.ausm.impl.util.MinecraftReflectionCompat.blockStateAtEntityViewpoint(world, viewEntity, com.l.ausm.impl.util.MinecraftReflectionCompat.renderPartialTicks(mc));
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsWater(cameraState)) {
            return 1;
        }
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterial(cameraState) == com.l.ausm.impl.util.MinecraftReflectionCompat.field(net.minecraft.block.material.Material.class, net.minecraft.block.material.Material.class, null, "field_151587_i", "LAVA") && !com.l.ausm.impl.util.MinecraftReflectionCompat.playerIsSpectator(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc))) {
            return 2;
        }
        return 0;
    }

    protected static double centeredRemainder(double value, double interval) {
        if (!Double.isFinite(value) || !Double.isFinite(interval) || interval <= 0.0D) {
            return 0.0D;
        }
        double remainder = value % interval;
        if (remainder > interval * 0.5D) {
            remainder -= interval;
        }
        if (remainder < -interval * 0.5D) {
            remainder += interval;
        }
        return remainder;
    }

    public void bindWorldFramebuffer() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        restoreVanillaWorldTextureBindings();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        resetPortalMaskState();
    }

    protected int currentPipelineWorldFramebufferId() {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        return framebuffer != null ? framebuffer.getFramebufferId() : 0;
    }

    public boolean shouldUseDistantHorizonsFramebufferOverride() {
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

    protected boolean shouldCompositeDistantHorizonsFramebuffer() {
        return isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) != null;
    }

    public boolean shouldSuppressDistantHorizonsMinecraftApply() {
        return shouldUseDistantHorizonsFramebufferOverride() || shouldProtectDistantHorizonsNativeApply();
    }

    protected boolean shouldProtectDistantHorizonsNativeApply() {
        return MainMod.getShaderPackManager() != null
                && MainMod.getShaderPackManager().shouldProtectDistantHorizonsNativeApply()
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null
                && com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) != null;
    }

    public void logDistantHorizonsApiCallback(String method, String detail) {
        logDistantHorizonsDiagnostic("api-" + method, detail + ", " + distantHorizonsProbeState(null));
    }

    public void logDistantHorizonsHook(String stage, Object renderParam) {
        updateDistantHorizonsRenderPass(renderParam);
        logDistantHorizonsDiagnostic(stage, distantHorizonsProbeState(renderParam));
    }

    public void renderDistantHorizonsLods(float partialTicks) {
    }

    public void resetDistantHorizonsDiagnostics(String reason) {
        distantHorizonsDiagnosticLogs = 0;
        logDistantHorizonsDiagnostic("probe-reset", reason + ", " + distantHorizonsProbeState(null));
    }

    public boolean applyDistantHorizonsToPipeline(Object renderParam) {
        if (shouldUseDistantHorizonsFramebufferOverride() || !shouldCompositeDistantHorizonsFramebuffer()) {
            logDistantHorizonsDiagnostic("native-apply-bypass", distantHorizonsProbeState(renderParam));
            return false;
        }

        updateDistantHorizonsRenderPass(renderParam);
        int colorTexture = activeDistantHorizonsTextureId("getActiveColorTextureId");
        int depthTexture = activeDistantHorizonsTextureId("getActiveDepthTextureId");
        if (colorTexture <= 0 || depthTexture <= 0) {
            logDistantHorizonsDiagnostic("native-apply-skip", "color=" + colorTexture + ", depth=" + depthTexture);
            return false;
        }

        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = colorTexture;
        distantHorizonsDepthTextureId = depthTexture;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        return true;
    }

    protected void updateDistantHorizonsRenderPass(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            currentDistantHorizonsPass = renderPass != null && "TRANSPARENT".equals(renderPass.toString())
                    ? RenderPass.DH_WATER
                    : RenderPass.DH_TERRAIN;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    protected int activeDistantHorizonsTextureId(String getterName) {
        try {
            Class<?> metaRendererClass = Class.forName("com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer");
            Object instance = metaRendererClass.getField("INSTANCE").get(null);
            Object value = metaRendererClass.getMethod(getterName).invoke(instance);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public int distantHorizonsFramebufferId() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(distantHorizonsPresentationTarget);
        }
        return shouldUseDistantHorizonsFramebufferOverride() ? currentPipelineWorldFramebufferId() : 0;
    }

    public int distantHorizonsFramebufferStatus() {
        if (!shouldUseDistantHorizonsFramebufferOverride()) {
            return GL30.GL_FRAMEBUFFER_COMPLETE;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            bindDistantHorizonsFramebuffer();
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    public void bindDistantHorizonsFramebuffer() {
        if (!shouldUseDistantHorizonsFramebufferOverride()) {
            return;
        }

        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            Framebuffer target = distantHorizonsPresentationTarget;
            com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
            GL11.glDrawBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()), framebufferHeight(target, com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()));
            GL11.glColorMask(true, true, true, true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            logDistantHorizonsDiagnostic("bind-presentation", distantHorizonsProbeState(null));
            return;
        }

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        restoreVanillaWorldTextureBindings();
        GL11.glColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        distantHorizonsFramebufferPendingComposite = false;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        logDistantHorizonsDiagnostic("bind-world", distantHorizonsProbeState(null));
    }

    public void compositeDistantHorizonsFramebuffer() {
        compositeDistantHorizonsFramebuffer(null);
    }

    protected void compositeDistantHorizonsFramebuffer(Framebuffer target) {
        if (!distantHorizonsFramebufferPendingComposite
                || !shouldCompositeDistantHorizonsFramebuffer()
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDepthTextureId == 0
                || !ensureDistantHorizonsCompositeProgram()) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            String sample = sampleDistantHorizonsColorTexture();
            if (target != null) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target));
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()), framebufferHeight(target, com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()));
            } else {
                pingPongManager.bindForGbuffers(fallbackColorAttachment());
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, pingPongManager.width(), pingPongManager.height());
            }
            GL11.glDrawBuffer(target != null && com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(target != null && com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(false);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(distantHorizonsCompositeProgramId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(distantHorizonsColorTextureId);
            if (distantHorizonsCompositeTextureUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeTextureUniform, 0);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(distantHorizonsDepthTextureId);
            if (distantHorizonsCompositeDepthUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeDepthUniform, 1);
            }
            drawDistantHorizonsCompositeQuad();
            String targetSample = sampleDistantHorizonsCompositeTarget(target);
            distantHorizonsFramebufferPendingComposite = false;
            logDistantHorizonsDiagnostic("composite", "pass=" + currentDistantHorizonsPass
                    + ", texture=" + distantHorizonsColorTextureId
                    + ", sample=" + sample
                    + ", targetSample=" + targetSample);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            if (previousDepthTest) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableDepth();
            }
            GL11.glDepthFunc(previousDepthFunc);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            if (previousBlend) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousAlpha) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (previousCull) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableCull();
            } else {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableCull();
            }
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void drawDistantHorizonsCompositeQuad() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glBindBuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(net.minecraft.client.renderer.OpenGlHelper.class, org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, "field_176089_P", "GL_ARRAY_BUFFER"), 0);
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        for (int attribute = 0; attribute < 16; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
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
    }

    protected boolean ensureDistantHorizonsFramebuffer() {
        int width = Math.max(1, pingPongManager.width());
        int height = Math.max(1, pingPongManager.height());
        if (distantHorizonsFramebufferId != 0
                && distantHorizonsFramebufferWidth == width
                && distantHorizonsFramebufferHeight == height) {
            return true;
        }

        distantHorizonsFramebufferWidth = width;
        distantHorizonsFramebufferHeight = height;
        distantHorizonsFramebufferId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        distantHorizonsColorTextureId = GL11.glGenTextures();
        distantHorizonsDepthTextureId = GL11.glGenTextures();
        distantHorizonsTexturesOwned = true;

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(distantHorizonsColorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(distantHorizonsDepthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);

        com.l.ausm.impl.util.MinecraftReflectionCompat.glBindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), distantHorizonsFramebufferId);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebuffer(), GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, distantHorizonsDepthTextureId, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        if (!complete) {
            MainMod.LOGGER.warn("[DistantHorizons] AUSM intermediate framebuffer is incomplete.");
            }
        TextureBinder.restoreDefaultTextureUnit();
        return complete;
    }

    protected void clearDistantHorizonsFramebuffer() {
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateClearDepth(1.0D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    protected String sampleDistantHorizonsColorTexture() {
        if (distantHorizonsFramebufferWidth <= 0
                || distantHorizonsFramebufferHeight <= 0
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            int readFramebuffer = distantHorizonsFramebufferId;
            if (readFramebuffer == 0) {
                readFramebuffer = ensureDistantHorizonsTextureReadbackFramebuffer();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                com.l.ausm.impl.util.MinecraftReflectionCompat.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            }
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return "read-fbo-incomplete:" + status;
            }
            int[][] points = new int[][]{
                    {distantHorizonsFramebufferWidth / 2, distantHorizonsFramebufferHeight / 2},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight / 4)},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected int ensureDistantHorizonsTextureReadbackFramebuffer() {
        if (distantHorizonsTextureReadbackFramebufferId == 0) {
            distantHorizonsTextureReadbackFramebufferId = com.l.ausm.impl.util.MinecraftReflectionCompat.glGenFramebuffers();
        }
        return distantHorizonsTextureReadbackFramebufferId;
    }

    protected String sampleDistantHorizonsCompositeTarget(Framebuffer target) {
        if (distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        int framebuffer = target != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) : GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readBuffer = target != null && com.l.ausm.impl.util.MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0;
        int width = target != null ? framebufferWidth(target, mc) : pingPongManager.width();
        int height = target != null ? framebufferHeight(target, mc) : pingPongManager.height();
        if (width <= 0 || height <= 0) {
            return "invalid-size";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            int[][] points = new int[][]{
                    {width / 2, height / 2},
                    {width / 2, Math.max(0, height / 4)},
                    {width / 2, Math.max(0, height * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected boolean clearDistantHorizonsFramebufferIfNeeded() {
        long frameKey = currentDistantHorizonsFrameKey();
        if (distantHorizonsFramebufferClearFrame == frameKey) {
            return false;
        }

        clearDistantHorizonsFramebuffer();
        distantHorizonsFramebufferClearFrame = frameKey;
        return true;
    }

    protected long currentDistantHorizonsFrameKey() {
        if (clientRenderFrameNanos != Long.MIN_VALUE) {
            return clientRenderFrameNanos;
        }
        return pipelineFrameId;
    }

    protected String distantHorizonsProbeState(Object renderParam) {
        String renderParamSummary = distantHorizonsRenderParamSummary(renderParam);
        return "pass=" + currentDistantHorizonsPass
                + ", override=" + shouldUseDistantHorizonsFramebufferOverride()
                + ", suppressApply=" + shouldSuppressDistantHorizonsMinecraftApply()
                + ", active=" + isPipelineActive
                + ", worldFrame=" + worldFrameActive
                + ", shadow=" + renderingShadowMap
                + ", gui=" + renderingGuiScreen()
                + ", pingpong=" + pingPongManager.isInitialized()
                + ", ausmFbo=" + currentPipelineWorldFramebufferId()
                + ", fallbackAttachment=" + fallbackColorAttachment()
                + ", size=" + pingPongManager.width() + "x" + pingPongManager.height()
                + ", storedColorTex=" + distantHorizonsColorTextureId
                + ", storedDepthTex=" + distantHorizonsDepthTextureId
                + ", activeColorTex=" + activeDistantHorizonsTextureId("getActiveColorTextureId")
                + ", activeDepthTex=" + activeDistantHorizonsTextureId("getActiveDepthTextureId")
                + ", pendingComposite=" + distantHorizonsFramebufferPendingComposite
                + ", frame=" + currentDistantHorizonsFrameKey()
                + ", renderParam=" + renderParamSummary
                + ", gl={" + distantHorizonsGlStateSummary() + "}";
    }

    protected String distantHorizonsRenderParamSummary(Object renderParam) {
        if (renderParam == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(renderParam.getClass().getName());
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            builder.append(":renderPass=").append(renderPass);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            Object worldYOffset = renderParam.getClass().getField("worldYOffset").get(renderParam);
            builder.append(":worldYOffset=").append(worldYOffset);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return builder.toString();
    }

    protected String distantHorizonsGlStateSummary() {
        try {
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            int viewportX = viewportBuffer.get(0);
            int viewportY = viewportBuffer.get(1);
            int viewportWidth = viewportBuffer.get(2);
            int viewportHeight = viewportBuffer.get(3);
            return "drawFbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", readFbo=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                    + ", readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", vao=" + GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                    + ", arrayBuffer=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    + ", depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + ", depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + ", viewport=" + viewportX + "/" + viewportY + "/" + viewportWidth + "/" + viewportHeight;
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    protected void logDistantHorizonsDiagnostic(String stage, String detail) {
        // Diagnostic disabled.
    }

    public void prepareExternalWorldOverlayRender() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (worldFrameActive) {
            pingPongManager.bindForGbuffers(fallbackColorAttachment());
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        resetIndexedBlendState();
        disablePipelineVertexAttributes();
        unbindShaderStorageBuffers();
        TextureBinder.restoreDefaultTextureUnit();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    protected void restoreVanillaWorldTextureBindings() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc) != null) {
            DynamicTexture lightmapTexture = ((EntityRendererAccessor) com.l.ausm.impl.util.MinecraftReflectionCompat.entityRenderer(mc)).ausm$getLightmapTexture();
            restoreVanillaLightmapTexture(mc);
            if (lightmapTexture != null) {
                int irisLightmapTextureId = irisLightmapTexture.updateFrom(lightmapTexture);
                if (irisLightmapTextureId > 0) {
                    TextureBinder.bindIrisLightmap(irisLightmapTextureId);
                } else {
                    TextureBinder.bindIrisLightmap(com.l.ausm.impl.util.MinecraftReflectionCompat.glTextureId(lightmapTexture));
                }
            } else {
                TextureBinder.mirrorVanillaLightmapToIrisUnit();
            }
        } else {
            TextureBinder.mirrorVanillaLightmapToIrisUnit();
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void renderPreparePass() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        if (shaderProperties.renderSettings().prepareBeforeShadow()) {
            runPreparePassesBeforeShadowIfRequested();
            return;
        }

        runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    protected void runPreparePassesBeforeShadowIfRequested() {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || !shaderProperties.renderSettings().prepareBeforeShadow()
                || preparePassesRenderedBeforeShadowThisFrame) {
            return;
        }

        preparePassesRenderedBeforeShadowThisFrame = true;
        runFullscreenPasses(ProgramArrayId.PREPARE);
    }

    public void snapshotOpaqueTerrainDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        logColorBufferProbe("after-opaque-terrain");
        logDeferredBoundaryProbe("after-opaque-terrain", "beforeDepthSnapshot=true");
        copyPreTranslucentDepth();
        logDeferredBoundaryProbe("after-pre-translucent-depth-copy", "preDepthCopied=" + preTranslucentDepthCopiedThisFrame);
        if (!ENABLE_SYNCHRONOUS_CENTER_DEPTH_READBACK) {
            return;
        }

        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        if (framebuffer == null) {
            return;
        }
        centerDepth = framebuffer.readCenterDepth();
        if (Float.isFinite(centerDepth)) {
            centerDepthSmooth += (centerDepth - centerDepthSmooth) * smoothingFactor(centerDepthHalfLife, currentFrameTime);
            if (Math.abs(centerDepth - centerDepthSmooth) < 0.00001f) {
                centerDepthSmooth = centerDepth;
            }
            updateCenterDepthSmoothTexture();
        }
    }

    protected void snapshotCompositeInvalidFallbackSource() {
        clearCompositeInvalidFallbackSnapshot();
    }

    protected boolean snapshotCompositeInvalidFallbackSource(DeferredFramebuffer framebuffer,
                                                           Attachment attachment,
                                                           boolean allowColorOnly,
                                                           String stage) {
        clearCompositeInvalidFallbackSnapshot();
        return false;
    }

    protected boolean hasCompositeInvalidFallbackSnapshot(DeferredFramebuffer framebuffer) {
        return false;
    }

    protected boolean recoveryColorSnapshotHasPresentableContent(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.hasRecoveryColorSnapshot()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getRecoveryColorWidth());
        int height = Math.max(1, framebuffer.getRecoveryColorHeight());
        int presentable = 0;
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float[] color = safeReadRecoveryColor(framebuffer, point[0], point[1]);
            if (isRecoverableColorOnlySceneColor(color)) {
                presentable++;
            }
        }
        return presentable >= 2;
    }

    protected boolean isCompositeInvalidFallbackSnapshotRecent() {
        if (!compositeInvalidFallbackSnapshotHasScene) {
            return false;
        }
        long age = pipelineFrameId - compositeInvalidFallbackSnapshotFrame;
        if (age < 0L || age > COMPOSITE_INVALID_FALLBACK_MAX_SNAPSHOT_AGE_FRAMES) {
            return false;
        }
        return true;
    }

    protected void clearCompositeInvalidFallbackSnapshot() {
        compositeInvalidFallbackFrames = 0;
        compositeInvalidFallbackSnapshotFrame = Long.MIN_VALUE;
        compositeInvalidFallbackSnapshotHasScene = false;
        DeferredFramebuffer framebuffer = pingPongManager != null ? pingPongManager.getReadBuffer() : null;
        if (framebuffer != null) {
            framebuffer.clearRecoveryColorSnapshot();
        }
    }

    protected boolean restoreCompositeInvalidSnapshotToPresentationAttachment(DeferredFramebuffer framebuffer,
                                                                            Attachment attachment,
                                                                            String reason) {
        clearCompositeInvalidFallbackSnapshot();
        return false;
    }

    protected boolean shouldForceCompositeInvalidPresentation(String reason) {
        return reason != null
                && !shouldSuppressCompositeRecoveryForSparseNothiriumTerrain()
                && reason.contains("after-composite")
                && terrainOpaqueDrawCount >= HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
    }

    protected boolean shouldSuppressCompositeRecoveryForSparseNothiriumTerrain() {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || !isNothiriumLoaded()) {
            return false;
        }
        if (hasSparseNothiriumMainTerrainEvidence()) {
            return true;
        }
        return shouldUseNothiriumMainTerrainBridge()
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS;
    }

    protected boolean hasSparseNothiriumMainTerrainEvidence() {
        return isCurrentOrRecentSparseNothiriumMainTerrainFrame()
                || (terrainLayerCountFrame == pipelineFrameId
                && terrainOpaqueLayerCount > 0
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS);
    }

    protected boolean isCurrentOrRecentSparseNothiriumMainTerrainFrame() {
        if (nothiriumSparseMainTerrainFrame == Long.MIN_VALUE) {
            return false;
        }
        long age = pipelineFrameId - nothiriumSparseMainTerrainFrame;
        return age >= 0L && age <= 2L;
    }

    protected void markSparseNothiriumMainTerrainFrame(boolean nothiriumMainTerrain) {
        if (nothiriumMainTerrain
                && !softVanillaTerrainRenderer
                && worldFrameActive
                && !renderingShadowMap
                && isNothiriumLoaded()
                && terrainLayerCountFrame == pipelineFrameId
                && terrainOpaqueLayerCount > 0
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS) {
            nothiriumSparseMainTerrainFrame = pipelineFrameId;
            clearCompositeInvalidFallbackSnapshot();
        }
    }

    protected boolean restoreCompositeInvalidFinalSourceAttachment(DeferredFramebuffer framebuffer,
                                                                 Attachment primaryAttachment,
                                                                 String reason) {
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        if (framebuffer == null
                || primaryAttachment == Attachment.COMPOSITE
                || finalProgram == null
                || !finalProgram.hasOwnProgram()
                || reason == null
                || !reason.contains("before-final")) {
            return false;
        }
        // Complementary's final pass reads colortex3/COMPOSITE. If composite
        // flattened that buffer, restoring only colortex0 leaves final sampling
        // the flat neutral/white buffer even though COLOR was recovered.
        if (deferredBufferHasSceneContent(framebuffer, Attachment.COMPOSITE)) {
            return false;
        }
        return pingPongManager.restoreRecoveryColorToReadAttachment(Attachment.COMPOSITE);
    }

    protected boolean shouldRestoreCompositeInvalidDepth(DeferredFramebuffer framebuffer) {
        return framebuffer != null
                && !deferredLiveDepthHasSceneContent(framebuffer)
                && deferredDepthSnapshotHasSceneContent(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT);
    }

    protected boolean deferredLiveDepthHasSceneContent(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float depth = safeReadDeferredDepth(framebuffer, point[0], point[1], width, height);
            if (Float.isFinite(depth) && depth < 0.99999f) {
                return true;
            }
        }
        return false;
    }

    protected boolean deferredDepthSnapshotHasSceneContent(DeferredFramebuffer framebuffer, int snapshotIndex) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return false;
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            float depth = safeReadDeferredDepthSnapshot(framebuffer, snapshotIndex, point[0], point[1]);
            if (Float.isFinite(depth) && depth < 0.99999f) {
                return true;
            }
        }
        return false;
    }

    protected void logCompositeInvalidRestore(DeferredFramebuffer framebuffer, Attachment attachment, String reason, boolean depthRestored) {
        if (compositeInvalidRestoreLogs++ >= MAX_COMPOSITE_INVALID_RESTORE_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMCompositeRecovery] action=restore-cached-scene reason={} source={} target={} depthRestored={} currentColor={} preservedColor={} depth={} depthtex1={}",
                reason,
                COMPOSITE_INVALID_FALLBACK_SOURCE,
                attachment,
                depthRestored,
                deferredFramebufferColorSamples(framebuffer, attachment),
                deferredFramebufferRecoveryColorSamples(framebuffer),
                framebuffer != null ? framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0) : "none",
                deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT)
        );
    }

    protected void logColorBufferProbe(String stage) {
        logColorBufferProbe(stage, false);
    }

    protected void logColorBufferProbe(String stage, boolean force) {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        boolean finalStage = stage != null && stage.contains("final");
        if (!force) {
            if (finalStage) {
                if (finalColorProbeLogs >= MAX_FINAL_COLOR_PROBE_LOGS) {
                    return;
                }
                finalColorProbeLogs++;
            } else {
                if (terrainColorProbeLogs >= MAX_TERRAIN_COLOR_PROBE_LOGS) {
                    return;
                }
                terrainColorProbeLogs++;
            }
        } else if (finalStage) {
            if (finalColorProbeLogs >= MAX_FINAL_COLOR_PROBE_LOGS + MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS) {
                return;
            }
            finalColorProbeLogs++;
        } else {
            if (terrainColorProbeLogs >= MAX_TERRAIN_COLOR_PROBE_LOGS + MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS) {
                return;
            }
            terrainColorProbeLogs++;
        }

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        String color = deferredFramebufferColorSamples(readBuffer, fallbackColorAttachment());
        String normal = deferredFramebufferColorSamples(readBuffer, Attachment.NORMAL);
        String depth = readBuffer != null && readBuffer.isUsable()
                ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                : "none";
        MainMod.LOGGER.info(
                "[AUSMColorProbe] stage={} color={} normal={} depth={} activePass={} phase={} safeVanilla={} reason='{}' read={} gl={}",
                stage,
                color,
                normal,
                depth,
                activePass,
                getPhase(),
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                describeDeferredFramebuffer(readBuffer),
                glStateSummary()
        );
        logTerrainGridProbe(stage, readBuffer);
    }

    protected void logCompositeChainProbe(String stage, String detail) {
        if (!isPipelineActive || !pingPongManager.isInitialized()
                || compositeChainProbeLogs >= MAX_COMPOSITE_CHAIN_PROBE_LOGS) {
            return;
        }
        compositeChainProbeLogs++;

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        MainMod.LOGGER.info(
                "[AUSMCompositeChainProbe] call={} stage={} detail={} read={} textures={} samples={} depth={} gl={}",
                compositeChainProbeLogs,
                stage,
                detail,
                describeDeferredFramebuffer(readBuffer),
                compositeChainTextureSummary(readBuffer),
                compositeChainSampleSummary(readBuffer),
                readBuffer != null && readBuffer.isUsable()
                        ? framebufferIdDepthSamples(readBuffer.getFramebufferId(), readBuffer.getWidth(), readBuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                        : "none",
                glStateSummary()
        );
    }

    protected void logDeferredBoundaryProbe(String stage, String detail) {
        if (!isPipelineActive || !pingPongManager.isInitialized()
                || deferredBoundaryProbeLogs >= MAX_DEFERRED_BOUNDARY_PROBE_LOGS) {
            return;
        }
        deferredBoundaryProbeLogs++;

        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        MainMod.LOGGER.info(
                "[AUSMDeferredBoundaryProbe] call={} stage={} detail={} frame={} deferredRendered={} preDepthCopied={} activePass={} phase={} terrainCounts=opaque:{}/draw:{} read={} textures={} colors={} depth0={} depth1={} depth2={} gl={}",
                deferredBoundaryProbeLogs,
                stage,
                detail,
                pipelineFrameId,
                deferredPassesRenderedThisFrame,
                preTranslucentDepthCopiedThisFrame,
                activePass,
                getPhase(),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                describeDeferredFramebuffer(readBuffer),
                deferredBoundaryTextureSummary(readBuffer),
                deferredBoundaryColorSummary(readBuffer),
                deferredDepthSampleSummary(readBuffer, -1),
                deferredDepthSampleSummary(readBuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                deferredDepthSampleSummary(readBuffer, DeferredFramebuffer.DEPTHTEX2_SNAPSHOT),
                glStateSummary()
        );
    }

    protected String deferredBoundaryTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        return "COLOR=" + framebuffer.getReadTexture(Attachment.COLOR) + "/" + framebuffer.getWriteTexture(Attachment.COLOR)
                + ";NORMAL=" + framebuffer.getReadTexture(Attachment.NORMAL) + "/" + framebuffer.getWriteTexture(Attachment.NORMAL)
                + ";COMPOSITE=" + framebuffer.getReadTexture(Attachment.COMPOSITE) + "/" + framebuffer.getWriteTexture(Attachment.COMPOSITE)
                + ";AUX2=" + framebuffer.getReadTexture(Attachment.AUX2) + "/" + framebuffer.getWriteTexture(Attachment.AUX2)
                + ";AUX6=" + framebuffer.getReadTexture(Attachment.AUX6) + "/" + framebuffer.getWriteTexture(Attachment.AUX6)
                + ";depth=" + framebuffer.getDepthTexture()
                + ";depth1=" + framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT)
                + ";depth2=" + framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT);
    }

    protected String deferredBoundaryColorSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : deferredBoundaryProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("colortex")
                    .append(attachment.getIndex())
                    .append('=')
                    .append(deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return builder.toString();
    }

    protected List<Attachment> deferredBoundaryProbeAttachments() {
        return List.of(
                Attachment.COLOR,
                Attachment.NORMAL,
                Attachment.COMPOSITE,
                Attachment.AUX2,
                Attachment.AUX6
        );
    }

    protected String deferredDepthSampleSummary(DeferredFramebuffer framebuffer, int snapshotIndex) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        int width = Math.max(1, framebuffer.getWidth());
        int height = Math.max(1, framebuffer.getHeight());
        int total = 0;
        int filled = 0;
        float minDepth = 1.0F;
        float maxDepth = 0.0F;
        StringBuilder samples = new StringBuilder();
        for (int[] point : compositeFallbackProbePoints(width, height)) {
            total++;
            float depth = snapshotIndex < 0
                    ? safeReadDeferredDepth(framebuffer, point[0], point[1], width, height)
                    : safeReadDeferredDepthSnapshot(framebuffer, snapshotIndex, point[0], point[1]);
            if (!Float.isFinite(depth)) {
                continue;
            }
            minDepth = Math.min(minDepth, depth);
            maxDepth = Math.max(maxDepth, depth);
            if (depth < 0.999F) {
                filled++;
            }
            if (samples.length() < 180) {
                if (samples.length() > 0) {
                    samples.append(';');
                }
                samples.append(point[0]).append(',').append(point[1]).append('=').append(formatProbeFloat(depth));
            }
        }
        return "filled=" + filled + "/" + total
                + ",min=" + formatProbeFloat(minDepth)
                + ",max=" + formatProbeFloat(maxDepth)
                + ",samples=" + samples;
    }

    protected String compositeChainTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : compositeChainProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(attachment)
                    .append('=')
                    .append(framebuffer.getReadTexture(attachment))
                    .append('/')
                    .append(framebuffer.getWriteTexture(attachment));
        }
        return builder.toString();
    }

    protected String compositeChainSampleSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Attachment attachment : compositeChainProbeAttachments()) {
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append("colortex")
                    .append(attachment.getIndex())
                    .append('=')
                    .append(deferredFramebufferColorSamples(framebuffer, attachment));
        }
        return builder.toString();
    }

    protected List<Attachment> compositeChainProbeAttachments() {
        return List.of(
                Attachment.COLOR,
                Attachment.COMPOSITE,
                Attachment.AUX3,
                Attachment.AUX4,
                Attachment.AUX5,
                Attachment.AUX6
        );
    }

    protected void logTerrainGridProbe(String stage, DeferredFramebuffer readBuffer) {
        if (terrainGridProbeLogs >= MAX_TERRAIN_GRID_PROBE_LOGS || readBuffer == null || !readBuffer.isUsable()) {
            return;
        }
        terrainGridProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMTerrainGridProbe] stage={} activePass={} phase={} safeVanilla={} reason='{}' read={} textures={} gl={} grid={}",
                stage,
                activePass,
                getPhase(),
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                describeDeferredFramebuffer(readBuffer),
                terrainProbeTextureSummary(readBuffer),
                terrainProbeGlStateSummary(),
                terrainGridProbeSummary(readBuffer)
        );
    }

    protected String terrainProbeTextureSummary(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return "none";
        }
        Attachment colorAttachment = fallbackColorAttachment();
        return "color=" + colorAttachment
                + ":" + framebuffer.getReadTexture(colorAttachment) + "/" + framebuffer.getWriteTexture(colorAttachment)
                + ", normal=" + framebuffer.getReadTexture(Attachment.NORMAL) + "/" + framebuffer.getWriteTexture(Attachment.NORMAL)
                + ", depth=" + framebuffer.getDepthTexture();
    }

    protected String terrainGridProbeSummary(DeferredFramebuffer framebuffer) {
        String errorBefore = drainGlErrorsForProbe();
        StringBuilder builder = new StringBuilder();
        builder.append("logical");
        Set<Attachment> attachments = new LinkedHashSet<>();
        attachments.add(fallbackColorAttachment());
        attachments.add(Attachment.AUX3);
        attachments.add(Attachment.AUX1);
        attachments.add(Attachment.NORMAL);
        attachments.add(Attachment.COMPOSITE);
        for (Attachment attachment : attachments) {
            builder.append(' ').append(attachment).append('=')
                    .append(terrainGridReadAttachmentSummary(framebuffer, attachment, attachment == fallbackColorAttachment()));
        }
        builder.append(",probeErr=").append(errorBefore).append('/').append(drainGlErrorsForProbe());
        return builder.toString();
    }

    protected String terrainGridReadAttachmentSummary(DeferredFramebuffer framebuffer, Attachment attachment, boolean includeDepth) {
        if (framebuffer == null || attachment == null) {
            return "invalid";
        }
        int width = Math.max(1, framebuffer.getAttachmentWidth(attachment));
        int height = Math.max(1, framebuffer.getAttachmentHeight(attachment));
        int colorNonZero = 0;
        int alphaNonZero = 0;
        int depthFilled = 0;
        float minDepth = 1.0F;
        float maxDepth = 0.0F;
        StringBuilder examples = new StringBuilder();
        int sampleCount = 0;
        for (int row = 0; row < TERRAIN_GRID_PROBE_ROWS; row++) {
            int y = gridProbeCoordinate(row, TERRAIN_GRID_PROBE_ROWS, height);
            for (int column = 0; column < TERRAIN_GRID_PROBE_COLUMNS; column++) {
                int x = gridProbeCoordinate(column, TERRAIN_GRID_PROBE_COLUMNS, width);
                sampleCount++;
                float[] color = safeReadDeferredColor(framebuffer, attachment, x, y);
                if (!isFiniteColor(color)) {
                    continue;
                }
                int r = probeColorByte(color[0]);
                int g = probeColorByte(color[1]);
                int b = probeColorByte(color[2]);
                int a = probeColorByte(color[3]);
                if (r != 0 || g != 0 || b != 0) {
                    colorNonZero++;
                }
                if (a != 0) {
                    alphaNonZero++;
                }
                float depth = 1.0F;
                boolean filledDepth = false;
                if (includeDepth) {
                    depth = safeReadDeferredDepth(framebuffer, x, y, width, height);
                    if (Float.isFinite(depth)) {
                        minDepth = Math.min(minDepth, depth);
                        maxDepth = Math.max(maxDepth, depth);
                        filledDepth = depth < 0.999F;
                        if (filledDepth) {
                            depthFilled++;
                        }
                    }
                }
                if ((filledDepth || examples.length() == 0) && examples.length() < 160) {
                    if (examples.length() > 0) {
                        examples.append('|');
                    }
                    examples.append(x).append(',').append(y)
                            .append(":rgba(").append(r).append('/').append(g).append('/').append(b).append('/').append(a).append(')');
                    if (includeDepth) {
                        examples.append(":d=").append(formatProbeFloat(depth));
                    }
                }
            }
        }
        return "nz=" + colorNonZero + "/" + sampleCount
                + ",a=" + alphaNonZero + "/" + sampleCount
                + (includeDepth
                ? ",depthFilled=" + depthFilled + "/" + sampleCount
                + ",depthRange=" + formatProbeFloat(minDepth) + ".." + formatProbeFloat(maxDepth)
                : "")
                + ",examples=" + (examples.length() > 0 ? examples : "none");
    }

    protected static int probeColorByte(float value) {
        if (!Float.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    protected String drainGlErrorsForProbe() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR && count < 8) {
            if (builder.length() > 0) {
                builder.append('+');
            }
            builder.append(glErrorName(error));
            count++;
        }
        if (error != GL11.GL_NO_ERROR) {
            builder.append("+more");
        }
        return builder.length() > 0 ? builder.toString() : "ok";
    }

    protected void drainPausedPostRenderGlErrors(String stage) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (!shouldDrainPausedPostRenderGlErrors(mc)) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        int count = 0;
        int error;
        while ((error = GL11.glGetError()) != GL11.GL_NO_ERROR && count < 16) {
            if (builder.length() > 0) {
                builder.append('+');
            }
            builder.append(glErrorName(error));
            count++;
        }
        if (error != GL11.GL_NO_ERROR) {
            builder.append("+more");
        }
        if (count > 0 && pausedPostRenderGlErrorLogs < 8) {
            pausedPostRenderGlErrorLogs++;
            MainMod.LOGGER.info("[AUSMPausedGlDrain] stage={} errors={} screen={}",
                    stage,
                    builder,
                    pausedScreenName(mc));
        }
    }

    protected boolean shouldDrainPausedPostRenderGlErrors(Minecraft mc) {
        if (mc == null) {
            return false;
        }
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.isGamePaused(mc)) {
            return true;
        }
        net.minecraft.client.gui.GuiScreen screen = com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc);
        return screen != null || renderingGui;
    }

    protected String pausedScreenName(Minecraft mc) {
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc) == null) {
            return "none";
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(mc).getClass().getName();
    }

    protected int gridProbeCoordinate(int index, int count, int size) {
        if (size <= 1) {
            return 0;
        }
        int value = (int) (((long) (index + 1) * size) / (count + 1));
        return Math.max(0, Math.min(size - 1, value));
    }

    protected String terrainProbeGlStateSummary() {
        try {
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
            int viewportX = viewportBuffer.get(0);
            int viewportY = viewportBuffer.get(1);
            int viewportWidth = viewportBuffer.get(2);
            int viewportHeight = viewportBuffer.get(3);
            viewportBuffer.clear();
            GL11.glGetInteger(GL11.GL_SCISSOR_BOX, viewportBuffer);
            int scissorX = viewportBuffer.get(0);
            int scissorY = viewportBuffer.get(1);
            int scissorWidth = viewportBuffer.get(2);
            int scissorHeight = viewportBuffer.get(3);
            terrainProbeBooleanBuffer.clear();
            GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, terrainProbeBooleanBuffer);
            return "drawFbo=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", readFbo=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                    + ", readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                    + ", drawBuffers=" + drawBuffersProbeSummary()
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", vao=" + GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
                    + ", arrayBuffer=" + GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
                    + ", elementArrayBuffer=" + GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)
                    + ", activeTex=" + GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
                    + ", tex2d=" + GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                    + ", blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                    + ", alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                    + ", depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                    + ", depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                    + ", depthFunc=" + GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
                    + ", colorMask=" + (terrainProbeBooleanBuffer.get(0) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(1) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(2) != 0)
                    + "/" + (terrainProbeBooleanBuffer.get(3) != 0)
                    + ", cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                    + ", frontFace=" + GL11.glGetInteger(GL11.GL_FRONT_FACE)
                    + ", scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
                    + ":" + scissorX + "/" + scissorY + "/" + scissorWidth + "/" + scissorHeight
                    + ", polygonOffset=" + GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL)
                    + ", viewport=" + viewportX + "/" + viewportY + "/" + viewportWidth + "/" + viewportHeight;
        } catch (RuntimeException | LinkageError exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    protected String drawBuffersProbeSummary() {
        if (!GLContext.getCapabilities().OpenGL20) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        int slots = Math.min(4, maxDrawBuffers());
        for (int i = 0; i < slots; i++) {
            if (i > 0) {
                builder.append('/');
            }
            builder.append(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i));
        }
        return builder.toString();
    }

    protected static String framebufferStatusName(int status) {
        if (status == GL30.GL_FRAMEBUFFER_COMPLETE) {
            return "complete";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
            return "incomplete-attachment";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
            return "missing-attachment";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_DRAW_BUFFER) {
            return "incomplete-draw-buffer";
        }
        if (status == GL30.GL_FRAMEBUFFER_INCOMPLETE_READ_BUFFER) {
            return "incomplete-read-buffer";
        }
        if (status == GL30.GL_FRAMEBUFFER_UNSUPPORTED) {
            return "unsupported";
        }
        return String.valueOf(status);
    }

    protected static String glErrorName(int error) {
        if (error == GL11.GL_NO_ERROR) {
            return "ok";
        }
        if (error == GL11.GL_INVALID_ENUM) {
            return "invalid-enum";
        }
        if (error == GL11.GL_INVALID_VALUE) {
            return "invalid-value";
        }
        if (error == GL11.GL_INVALID_OPERATION) {
            return "invalid-operation";
        }
        if (error == GL11.GL_STACK_OVERFLOW) {
            return "stack-overflow";
        }
        if (error == GL11.GL_STACK_UNDERFLOW) {
            return "stack-underflow";
        }
        if (error == GL11.GL_OUT_OF_MEMORY) {
            return "out-of-memory";
        }
        return String.valueOf(error);
    }

    protected void logDistantHorizonsColorProbe(String stage) {
        // Probe disabled.
    }

    protected void logDistantHorizonsPassColorProbe(String stage, RenderPass pass) {
        // Probe disabled.
    }

    protected static boolean isDistantHorizonsProbeMarker(float[] aux3) {
        return aux3 != null
                && aux3.length >= 3
                && aux3[2] > 0.5f
                && aux3[0] < 0.2f
                && aux3[1] < 0.2f;
    }

    protected static String formatProbeColor(float[] color) {
        if (color == null || color.length < 4) {
            return "(nan,nan,nan,nan)";
        }
        return "("
                + formatProbeFloat(color[0]) + ','
                + formatProbeFloat(color[1]) + ','
                + formatProbeFloat(color[2]) + ','
                + formatProbeFloat(color[3]) + ')';
    }

    protected static String formatProbeFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    public int renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        if (renderGlobal == null) {
            logWorldLayerDiag("skip-null-render-global", layer, pass, 0, viewEntity);
            return 0;
        }
        if (shouldSkipAllMainGbufferRendering()) {
            recordTerrainLayerCount(layer, 0);
            logWorldLayerDiag("skip-all-rendering", layer, pass, 0, viewEntity);
            return 0;
        }
        if (shouldSuppressDuplicatePipelineTranslucentLayer(layer)) {
            logWorldLayerDiag("skip-duplicate-translucent", layer, pass, 0, viewEntity);
            return 0;
        }

        boolean prepareVanillaState = shouldPrepareShaderlessBlockLayerState();
        if (prepareVanillaState) {
            prepareShaderlessBlockLayerState(layer);
        }

        try {
            boolean forceVanillaRenderer = shouldForceVanillaTerrainRenderer();
            int nothiriumCount = forceVanillaRenderer
                    ? -1
                    : renderNothiriumTerrainLayer(layer, (float) partialTicks, viewEntity);
            if (nothiriumCount >= 0) {
                if (nothiriumCount > 0) {
                    markNothiriumPipelineTranslucentBridge(layer);
                }
                recordTerrainLayerCount(layer, nothiriumCount, true);
                recordShaderlessTerrainLayerCount(layer, nothiriumCount);
                probePositiveNothiriumTerrainDraw(layer, nothiriumCount);
                logWorldLayerDiag("nothirium", layer, pass, nothiriumCount, viewEntity);
                return nothiriumCount;
            }
            boolean forceVanillaFallback = isPipelineActive
                    && (forceVanillaRenderer || NothiriumBypass.shouldBypass());
            if (forceVanillaFallback) {
                int count = renderForcedVanillaTerrainLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
                recordTerrainLayerCount(layer, count);
                recordShaderlessTerrainLayerCount(layer, count);
                probePositiveVanillaTerrainDraw(layer, count);
                logWorldLayerDiag("vanilla-forced-bypass", layer, pass, count, viewEntity);
                return count;
            }
            int count = NothiriumBypass.shouldBypass()
                    ? renderForcedVanillaTerrainLayer(renderGlobal, layer, partialTicks, pass, viewEntity)
                    : com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
            recordTerrainLayerCount(layer, count);
            recordShaderlessTerrainLayerCount(layer, count);
            logWorldLayerDiag("vanilla", layer, pass, count, viewEntity);
            return count;
        } finally {
            if (prepareVanillaState) {
                finishShaderlessBlockLayerState(layer);
            }
        }
    }

    protected int renderForcedVanillaTerrainLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks,
                                                int pass, Entity viewEntity) {
        boolean timingProbe = isComplementarySoftVanillaStartupFallbackActive();
        long startNanos = timingProbe ? System.nanoTime() : 0L;
        long afterEnsureNanos = startNanos;
        long afterRebindNanos = startNanos;
        int count = Integer.MIN_VALUE;
        ensureVanillaTerrainRenderer(renderWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()), false);
        if (timingProbe) {
            afterEnsureNanos = System.nanoTime();
        }
        if (!shouldUseHardwareSafeVanillaBlockLayerState()) {
            rebindActiveTerrainPassForForcedVanillaFallback();
        }
        if (timingProbe) {
            afterRebindNanos = System.nanoTime();
        }
        NothiriumBypass.pushForcedBypass();
        try {
            count = com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
            return count;
        } finally {
            NothiriumBypass.popForcedBypass();
            if (timingProbe) {
                logSoftVanillaLayerTiming(layer, pass, count, startNanos, afterEnsureNanos, afterRebindNanos, System.nanoTime(), viewEntity);
            }
        }
    }

    protected void logSoftVanillaLayerTiming(BlockRenderLayer layer, int pass, int count, long startNanos,
                                           long afterEnsureNanos, long afterRebindNanos, long endNanos,
                                           Entity viewEntity) {
        if (!isComplementarySoftVanillaStartupFallbackActive()
                || softVanillaLayerTimingLogs >= MAX_SOFT_VANILLA_LAYER_TIMING_LOGS) {
            return;
        }
        double totalMs = nanosToMillis(endNanos - startNanos);
        if (softVanillaLayerTimingLogs >= 32 && totalMs < 8.0D) {
            return;
        }
        softVanillaLayerTimingLogs++;
        MainMod.LOGGER.info(
                "[AUSMSoftVanillaTiming] call={} stage=renderBlockLayer layer={} pass={} count={} totalMs={} ensureMs={} rebindMs={} drawMs={} frame={} frameTime={} opaqueLayers={} opaqueDraws={} view={} glProgram={}",
                softVanillaLayerTimingLogs,
                layer,
                pass,
                count,
                formatMillis(totalMs),
                formatMillis(nanosToMillis(afterEnsureNanos - startNanos)),
                formatMillis(nanosToMillis(afterRebindNanos - afterEnsureNanos)),
                formatMillis(nanosToMillis(endNanos - afterRebindNanos)),
                pipelineFrameId,
                formatMillis(currentFrameTime * 1000.0D),
                terrainOpaqueLayerCount,
                terrainOpaqueDrawCount,
                viewEntity != null ? viewEntity.getClass().getName() : "null",
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        );
    }

    protected void probePositiveVanillaTerrainDraw(BlockRenderLayer layer, int count) {
        if (!(shaderedNothiriumGlobalBypass || nothiriumMainVanillaDrawPathFrames > 0)
                || count <= 0
                || positiveVanillaTerrainProbeLogs >= MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS
                || layer == null
                || layer == BlockRenderLayer.TRANSLUCENT) {
            return;
        }
        positiveVanillaTerrainProbeLogs++;
        logColorBufferProbe("after-positive-vanilla-" + layer, true);
    }

    protected void probePositiveNothiriumTerrainDraw(BlockRenderLayer layer, int count) {
        if (!isPipelineActive
                || count <= 0
                || positiveNothiriumTerrainProbeLogs >= MAX_POSITIVE_VANILLA_TERRAIN_PROBE_LOGS
                || layer == null
                || layer == BlockRenderLayer.TRANSLUCENT) {
            return;
        }
        positiveNothiriumTerrainProbeLogs++;
        logColorBufferProbe("after-positive-nothirium-" + layer, true);
    }

    protected void rebindActiveTerrainPassForForcedVanillaFallback() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        WorldRenderingPhase phase = getPhase();
        if (phase == WorldRenderingPhase.NONE || !phase.usesBlockAtlas()) {
            return;
        }
        bindPass(activePass);
    }

    protected void rebindActiveTerrainPassAfterNothiriumNativeDraw() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        bindPass(activePass);
    }

    protected void logWorldLayerDiag(String stage, BlockRenderLayer layer, int pass, int count, Entity viewEntity) {
        if (worldLayerDiagLogs >= MAX_WORLD_LAYER_DIAG_LOGS) {
            return;
        }
        if (!isPipelineActive && !"skip-null-render-global".equals(stage)) {
            return;
        }
        if (!stage.startsWith("vanilla") && !"nothirium".equals(stage) && !"skip-all-rendering".equals(stage)) {
            return;
        }
        worldLayerDiagLogs++;
        MainMod.LOGGER.info(
                "[AUSMWorldLayer] call={} stage={} layer={} pass={} count={} active={} safeVanilla={} reason='{}' nothiriumBypass={} activePass={} phase={} frame={} worldFrame={} view={} gl={}",
                worldLayerDiagLogs,
                stage,
                layer,
                pass,
                count,
                isPipelineActive,
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason,
                NothiriumBypass.shouldBypass(),
                activePass,
                getPhase(),
                pipelineFrameId,
                worldFrameActive,
                viewEntity != null ? viewEntity.getClass().getName() : "null",
                glStateSummary()
        );
    }

    protected void markNothiriumPipelineTranslucentBridge(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || !isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass != RenderPass.GBUFFERS_WATER
                || getPhase() != WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            return;
        }

        nothiriumPipelineTranslucentFrame = pipelineFrameId;
        nothiriumPipelineTranslucentWorldPassSerial = currentWorldPassSerial;
        nothiriumPipelineTranslucentDrawnFrame = pipelineFrameId;
    }

    protected boolean shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer layer) {
        boolean sameWorldPass = currentWorldPassSerial != Long.MIN_VALUE
                && nothiriumPipelineTranslucentWorldPassSerial == currentWorldPassSerial
                && nothiriumPipelineTranslucentFrame == pipelineFrameId;
        boolean samePipelineFrame = nothiriumPipelineTranslucentDrawnFrame == pipelineFrameId;
        return layer == BlockRenderLayer.TRANSLUCENT
                && isPipelineActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && (worldFrameActive || samePipelineFrame)
                && (sameWorldPass || samePipelineFrame)
                && !isPipelineTranslucentTerrainPhase();
    }

    protected boolean isPipelineTranslucentTerrainPhase() {
        return activePass == RenderPass.GBUFFERS_WATER
                && getPhase() == WorldRenderingPhase.TERRAIN_TRANSLUCENT;
    }

    protected void clearNothiriumPipelineTranslucentBridge() {
        nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;
        nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;
    }

    protected void beginWorldPassDuplicateTracking() {
        worldPassSerialStack.push(currentWorldPassSerial);
        nothiriumPipelineTranslucentFrameStack.push(nothiriumPipelineTranslucentFrame);
        nothiriumPipelineTranslucentWorldPassSerialStack.push(nothiriumPipelineTranslucentWorldPassSerial);
        currentWorldPassSerial = ++nextWorldPassSerial;
        clearNothiriumPipelineTranslucentBridge();
    }

    protected void finishWorldPassDuplicateTracking() {
        currentWorldPassSerial = worldPassSerialStack.isEmpty() ? Long.MIN_VALUE : worldPassSerialStack.pop();
        nothiriumPipelineTranslucentFrame = nothiriumPipelineTranslucentFrameStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentFrameStack.pop();
        nothiriumPipelineTranslucentWorldPassSerial = nothiriumPipelineTranslucentWorldPassSerialStack.isEmpty()
                ? Long.MIN_VALUE
                : nothiriumPipelineTranslucentWorldPassSerialStack.pop();
    }

    protected boolean shouldPrepareShaderlessBlockLayerState() {
        return !isPipelineActive || shouldBypassWorldPassRendering() || shouldUseHardwareSafeVanillaBlockLayerState();
    }

    protected boolean shouldUseHardwareSafeVanillaBlockLayerState() {
        return isPipelineActive
                && ENABLE_SAFE_TERRAIN_FALLBACKS
                && hardwareSafeVanillaTerrain
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen();
    }

    protected void prepareShaderlessBlockLayerState(BlockRenderLayer layer) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (!shaderlessBloomExtractionActive) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        }
        if (shouldUseHardwareSafeVanillaBlockLayerState() && pingPongManager.isInitialized()) {
            pingPongManager.bindForGbuffers(fallbackColorAttachment());
        }
        TextureBinder.restoreDefaultTextureUnit();
        resetIndexedBlendState();
        if (!shaderlessBloomExtractionActive) {
            disablePipelineVertexAttributes();
        }
        unbindShaderStorageBuffers();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableLighting();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableColorMaterial();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        restoreVanillaFixedFunctionTextureState(mc);
        restoreShaderlessTerrainClientTextureArrays();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();

        if (shouldRenderLayerWithTranslucentState(layer)) {
            FixedFunctionGlState.prepareTranslucentDepthBlendState();
            FixedFunctionGlState.forceTranslucentBlockLayer();
            return;
        }

        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
        if (layer == BlockRenderLayer.SOLID) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableAlpha();
        } else {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    protected void finishShaderlessBlockLayerState(BlockRenderLayer layer) {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        if (shouldRenderLayerWithTranslucentState(layer)) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDisableBlend();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        }
    }

    protected void beginShaderlessTerrainLightmapCoords() {
        if (isPipelineActive || shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        shaderlessTerrainPreviousLightmapX = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldFloat(net.minecraft.client.renderer.OpenGlHelper.class, 0.0F, "lastBrightnessX", "lastBrightnessX");
        shaderlessTerrainPreviousLightmapY = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldFloat(net.minecraft.client.renderer.OpenGlHelper.class, 0.0F, "lastBrightnessY", "lastBrightnessY");
        shaderlessTerrainLightmapCoordsSaved = true;
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.OpenGlHelper.class, new String[] {"func_77475_a", "setLightmapTextureCoords"},
                new Class<?>[] {int.class, float.class, float.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit()), (0.0F), (240.0F));;
    }

    protected void restoreShaderlessTerrainLightmapCoords() {
        if (!shaderlessTerrainLightmapCoordsSaved) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.OpenGlHelper.class, new String[] {"func_77475_a", "setLightmapTextureCoords"},
                new Class<?>[] {int.class, float.class, float.class}, (com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit()), (shaderlessTerrainPreviousLightmapX), (shaderlessTerrainPreviousLightmapY));;
        shaderlessTerrainLightmapCoordsSaved = false;
    }

    protected static boolean shouldRenderLayerWithTranslucentState(BlockRenderLayer layer) {
        return layer == BlockRenderLayer.TRANSLUCENT || AusmBloomLayer.isBloomLayer(layer);
    }

    protected void recordTerrainLayerCount(BlockRenderLayer layer, int count) {
        recordTerrainLayerCount(layer, count, false);
    }

    protected void recordTerrainLayerCount(BlockRenderLayer layer, int count, boolean nothiriumMainTerrain) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || layer == null
                || isRenderingBetterPortalsRenderPass()) {
            return;
        }

        if (terrainLayerCountFrame != pipelineFrameId) {
            terrainLayerCountFrame = pipelineFrameId;
            terrainOpaqueLayerCount = 0;
            terrainOpaqueDrawCount = 0;
        }

        if (layer == BlockRenderLayer.SOLID
                || layer == BlockRenderLayer.CUTOUT_MIPPED
                || layer == BlockRenderLayer.CUTOUT) {
            terrainOpaqueLayerCount++;
            terrainOpaqueDrawCount += Math.max(0, count);
            if (count > 0) {
                zeroOpaqueTerrainFrames = 0;
                zeroOpaqueTerrainRecoveryRequested = false;
            }
        }

        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            zeroOpaqueTerrainFrames = 0;
            sparseOpaqueTerrainFrames = 0;
            return;
        }

        if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && terrainOpaqueDrawCount == 0) {
            if (hasLoadedTerrainNearPlayer()) {
                markSparseNothiriumMainTerrainFrame(nothiriumMainTerrain);
                zeroOpaqueTerrainFrames++;
                logHardwareTerrainFallback(
                        "zero-opaque-frame",
                        "frames=" + zeroOpaqueTerrainFrames
                                + ", activePass=" + activePass
                                + ", phase=" + getPhase()
                                + ", bypass=" + NothiriumBypass.shouldBypass()
                );
                if (zeroOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_ZERO_FRAMES) {
                    zeroOpaqueTerrainFrames = 0;
                    if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
                        zeroOpaqueTerrainRecoveryRequested = true;
                        logHardwareTerrainFallback(
                                "zero-opaque-nothirium-only",
                                "safe terrain fallback disabled; keeping Nothirium-only terrain path"
                        );
                        return;
                    }
                    if (softVanillaTerrainRenderer) {
                        logHardwareTerrainFallback(
                                "zero-opaque-soft-vanilla-failed",
                                "soft vanilla terrain still produced zero opaque draws; escalating to hardware-safe vanilla terrain"
                        );
                        activateHardwareSafeVanillaTerrain("soft-vanilla-zero-opaque");
                        return;
                    }
                    zeroOpaqueTerrainRecoveryRequested = true;
                    logHardwareTerrainFallback(
                            "zero-opaque-soft-vanilla",
                            "switching main terrain away from Nothirium after repeated zero opaque shader frames"
                    );
                    activateSoftVanillaTerrainRenderer("zero-opaque-nothirium-main");
                    return;
                }
                if (nothiriumMainTerrain && !softVanillaTerrainRenderer) {
                    sparseOpaqueTerrainFrames++;
                    if (sparseOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES) {
                        logHardwareTerrainFallback(
                                "zero-sparse-opaque-soft-vanilla",
                                "switching main terrain away from zero/sparse Nothirium after weak frames="
                                        + sparseOpaqueTerrainFrames
                        );
                        sparseOpaqueTerrainFrames = 0;
                        activateSoftVanillaTerrainRenderer("zero-sparse-opaque-nothirium-main");
                        return;
                    }
                } else if (!softVanillaTerrainRenderer) {
                    sparseOpaqueTerrainFrames = 0;
                }
            } else {
                sparseOpaqueTerrainFrames = 0;
                logHardwareTerrainFallback("zero-opaque-no-loaded-terrain", "world=" + describeWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) : null));
            }
        } else if (layer == BlockRenderLayer.CUTOUT
                && terrainOpaqueLayerCount >= 3
                && (nothiriumMainTerrain || softVanillaTerrainRenderer)
                && terrainOpaqueDrawCount < HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                && hasLoadedTerrainNearPlayer()) {
            markSparseNothiriumMainTerrainFrame(nothiriumMainTerrain);
            if (softVanillaTerrainRenderer
                    && isComplementarySoftVanillaStartupPack()
                    && terrainOpaqueDrawCount > 0) {
                sparseOpaqueTerrainFrames = 0;
                return;
            }
            sparseOpaqueTerrainFrames++;
            logHardwareTerrainFallback(
                    softVanillaTerrainRenderer ? "sparse-opaque-soft-vanilla-frame" : "sparse-opaque-frame",
                    "frames=" + sparseOpaqueTerrainFrames
                            + ", opaqueDraws=" + terrainOpaqueDrawCount
                            + ", minOpaqueDraws=" + HARDWARE_TERRAIN_FALLBACK_SPARSE_OPAQUE_DRAWS
                            + ", activePass=" + activePass
                            + ", phase=" + getPhase()
                            + ", bypass=" + NothiriumBypass.shouldBypass()
            );
            if (sparseOpaqueTerrainFrames >= HARDWARE_TERRAIN_FALLBACK_SPARSE_FRAMES) {
                if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
                    sparseOpaqueTerrainFrames = 0;
                    logHardwareTerrainFallback(
                            "sparse-opaque-nothirium-only",
                            "safe terrain fallback disabled; keeping Nothirium-only terrain path"
                    );
                    return;
                }
                if (softVanillaTerrainRenderer) {
                    logHardwareTerrainFallback(
                            "sparse-opaque-soft-vanilla-failed",
                            "soft vanilla terrain stayed sparse after frames="
                                    + sparseOpaqueTerrainFrames
                                    + ", opaqueDraws=" + terrainOpaqueDrawCount
                                    + "; escalating to hardware-safe vanilla terrain"
                    );
                    sparseOpaqueTerrainFrames = 0;
                    activateHardwareSafeVanillaTerrain("soft-vanilla-sparse-opaque");
                    return;
                }
                logHardwareTerrainFallback(
                        "sparse-opaque-soft-vanilla",
                        "switching main terrain away from sparse Nothirium after frames="
                                + sparseOpaqueTerrainFrames
                                + ", opaqueDraws=" + terrainOpaqueDrawCount
                );
                sparseOpaqueTerrainFrames = 0;
                activateSoftVanillaTerrainRenderer("sparse-opaque-nothirium-main");
            }
        } else if (layer == BlockRenderLayer.CUTOUT && terrainOpaqueLayerCount >= 3) {
            sparseOpaqueTerrainFrames = 0;
        }
    }

    public boolean shouldUseNothiriumHybridVanillaMaintenance() {
        return isPipelineActive
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && softVanillaTerrainRenderer)
                && nothiriumHybridVanillaMaintenanceFrames > 0;
    }

    public String nothiriumHybridVanillaMaintenanceReason() {
        return nothiriumHybridVanillaMaintenanceReason;
    }

    protected void startNothiriumHybridVanillaMaintenance(String reason) {
        if (!isPipelineActive
                || renderingShadowMap
                || (ENABLE_SAFE_TERRAIN_FALLBACKS && (hardwareSafeVanillaTerrain || softVanillaTerrainRenderer))) {
            return;
        }
        int previous = nothiriumHybridVanillaMaintenanceFrames;
        nothiriumHybridVanillaMaintenanceFrames = Math.max(
                nothiriumHybridVanillaMaintenanceFrames,
                NOTHIRIUM_HYBRID_VANILLA_MAINTENANCE_FRAMES
        );
        nothiriumHybridVanillaMaintenanceReason = reason != null ? reason : "";
        if (previous > 0 || nothiriumHybridVanillaMaintenanceLogs >= MAX_NOTHIRIUM_HYBRID_MAINTENANCE_LOGS) {
            return;
        }
        nothiriumHybridVanillaMaintenanceLogs++;
        MainMod.LOGGER.warn(
                "[AUSMNothiriumHybrid] stage=activate-maintenance frames={} reason='{}' frame={} world={} gl={}",
                nothiriumHybridVanillaMaintenanceFrames,
                nothiriumHybridVanillaMaintenanceReason,
                pipelineFrameId,
                describeWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) : null),
                glStateSummary()
        );
    }

    protected boolean shouldUseNothiriumMainVanillaDrawPath(BlockRenderLayer layer) {
        return false;
    }

    protected boolean shouldPreferShaderedVanillaMainTerrain() {
        return false;
    }

    protected void startNothiriumMainVanillaDrawPath(String reason) {
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
    }

    protected void primeNothiriumMainVanillaDrawPath(String reason) {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) == null) {
            ensureVanillaTerrainRenderer();
            return;
        }
        rebuildMainWorldVanillaViewFrustum(
                com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc),
                com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                "nothirium-main-vanilla-draw"
        );
        ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
        NothiriumBypass.markAllChanged();
        scheduleWorldTerrainRefresh(true, true, 0);
        scheduleInactiveVanillaRecoveryFrame();
        logHardwareTerrainFallback("prime-main-vanilla-draw", reason);
    }

    protected void activateShaderedNothiriumGlobalBypass(String reason) {
        clearShaderedNothiriumGlobalBypassState(true);
    }

    protected void resetShaderlessTerrainLayerCounts() {
        shaderlessTerrainSolidCount = -1;
        shaderlessTerrainCutoutMippedCount = -1;
        shaderlessTerrainCutoutCount = -1;
        shaderlessTerrainTranslucentCount = -1;
        shaderlessTerrainBloomCount = -1;
    }

    protected void recordShaderlessTerrainLayerCount(BlockRenderLayer layer, int count) {
        if (isPipelineActive || layer == null || renderingShadowMap || renderingGuiScreen()) {
            return;
        }
        int safeCount = Math.max(0, count);
        if (layer == BlockRenderLayer.SOLID) {
            shaderlessTerrainSolidCount = safeCount;
        } else if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            shaderlessTerrainCutoutMippedCount = safeCount;
        } else if (layer == BlockRenderLayer.CUTOUT) {
            shaderlessTerrainCutoutCount = safeCount;
        } else if (layer == BlockRenderLayer.TRANSLUCENT) {
            shaderlessTerrainTranslucentCount = safeCount;
        } else if (AusmBloomLayer.isBloomLayer(layer)) {
            shaderlessTerrainBloomCount = safeCount;
        }
    }

    protected boolean shouldRenderShaderlessExtractionLayer(BlockRenderLayer layer) {
        return true;
    }

    protected int shaderlessTerrainLayerCount(BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID) {
            return shaderlessTerrainSolidCount;
        }
        if (layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return shaderlessTerrainCutoutMippedCount;
        }
        if (layer == BlockRenderLayer.CUTOUT) {
            return shaderlessTerrainCutoutCount;
        }
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return shaderlessTerrainTranslucentCount;
        }
        if (AusmBloomLayer.isBloomLayer(layer)) {
            return shaderlessTerrainBloomCount;
        }
        return -1;
    }

    protected boolean hasLoadedTerrainNearPlayer() {
        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        int playerChunkX = ((int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)))) >> 4;
        int playerChunkZ = ((int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc)))) >> 4;
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.call((com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)), net.minecraft.client.multiplayer.ChunkProviderClient.class, null, new String[] {"func_72863_F", "getChunkProvider"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS) instanceof ChunkProviderClient provider) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk chunk = com.l.ausm.impl.util.MinecraftReflectionCompat.call((provider), net.minecraft.world.chunk.Chunk.class, null, new String[] {"func_186026_b", "getLoadedChunk"},
                new Class<?>[] {int.class, int.class}, (playerChunkX + dx), (playerChunkZ + dz));
                    if (chunk != null && !com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((chunk), new String[] {"func_76621_g", "isEmpty"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return com.l.ausm.impl.util.MinecraftReflectionCompat.worldIsBlockLoaded(
                com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc),
                new BlockPos(com.l.ausm.impl.util.MinecraftReflectionCompat.player(mc))
        );
    }

    protected void activateHardwareSafeVanillaTerrain(String reason) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
            logHardwareTerrainFallback("skip-hardware-safe-disabled", reason);
            return;
        }
        if (hardwareSafeVanillaTerrain) {
            refreshHardwareSafeVanillaTerrain(reason, true);
            return;
        }
        hardwareSafeVanillaTerrain = true;
        hardwareSafeVanillaTerrainReason = reason;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        logHardwareTerrainFallback(
                "activate",
                reason + ", maxAttribs=" + safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)
                        + ", renderer='" + safeGetString(GL11.GL_RENDERER) + "'"
        );
        updateNothiriumPipelineBlockFormatMode();
        refreshHardwareSafeVanillaTerrain(reason, true);
        scheduleInactiveVanillaRecoveryFrame();
    }

    protected void activateSoftVanillaTerrainRenderer(String reason) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
            logHardwareTerrainFallback("skip-soft-vanilla-disabled", reason);
            return;
        }
        if (softVanillaTerrainRenderer) {
            return;
        }
        softVanillaTerrainRenderer = true;
        softVanillaTerrainRendererReason = reason;
        ensureVanillaTerrainRenderer();
        NothiriumBypass.markAllChanged();
        scheduleWorldTerrainRefresh(true, true, 0);
        scheduleInactiveVanillaRecoveryFrame();
        logHardwareTerrainFallback(
                "activate-soft-vanilla",
                reason + ", shaderBlockLayerOverrides=true"
        );
    }

    protected void refreshHardwareSafeVanillaTerrainForCamera(Minecraft mc) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS
                || !isPipelineActive
                || (!hardwareSafeVanillaTerrain && !softVanillaTerrainRenderer)
                || mc == null
                || com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            lastHardwareSafeVanillaTerrainRefreshWorld = null;
            lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;
            lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;
            lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;
            hardwareSafeVanillaTerrainRefreshCooldown = 0;
            return;
        }
        if (hardwareSafeVanillaTerrainRefreshCooldown > 0) {
            hardwareSafeVanillaTerrainRefreshCooldown--;
        }

        Entity viewEntity = com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            return;
        }
        int chunkX = ((int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posX(viewEntity))) >> 4;
        int chunkZ = ((int) Math.floor(com.l.ausm.impl.util.MinecraftReflectionCompat.posZ(viewEntity))) >> 4;
        boolean loadedNearPlayer = hasLoadedTerrainNearPlayer();
        boolean changed = lastHardwareSafeVanillaTerrainRefreshWorld != com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc)
                || lastHardwareSafeVanillaTerrainRefreshChunkX != chunkX
                || lastHardwareSafeVanillaTerrainRefreshChunkZ != chunkZ
                || (loadedNearPlayer && !lastHardwareSafeVanillaTerrainLoadedNearPlayer);

        lastHardwareSafeVanillaTerrainRefreshWorld = com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc);
        lastHardwareSafeVanillaTerrainRefreshChunkX = chunkX;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = chunkZ;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = loadedNearPlayer;

        if (changed && loadedNearPlayer) {
            refreshHardwareSafeVanillaTerrain(
                    hardwareSafeVanillaTerrain ? "camera-frustum-change" : "soft-vanilla-camera-frustum-change",
                    false
            );
        }
    }

    protected void refreshHardwareSafeVanillaTerrain(String reason, boolean hardReset) {
        if (!hardReset && hardwareSafeVanillaTerrainRefreshCooldown > 0) {
            return;
        }
        hardwareSafeVanillaTerrainRefreshCooldown = HARDWARE_TERRAIN_FALLBACK_REFRESH_COOLDOWN_FRAMES;

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (mc != null && com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) != null && com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc) != null) {
            if (hardReset) {
                deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
                rebuildMainWorldVanillaViewFrustum(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc), com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), "hardware-safe-vanilla");
            }
            ensureVanillaTerrainRenderer(com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc), true);
            com.l.ausm.impl.util.MinecraftReflectionCompat.loadRenderers(com.l.ausm.impl.util.MinecraftReflectionCompat.renderGlobal(mc));
        } else {
            ensureVanillaTerrainRenderer();
        }
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        scheduleInactiveVanillaRecoveryFrame();
        logHardwareTerrainFallback(
                "refresh",
                reason + ", hardReset=" + hardReset
                        + ", cooldown=" + hardwareSafeVanillaTerrainRefreshCooldown
        );
    }

    protected void logHardwareTerrainFallback(String stage, String detail) {
        if (hardwareTerrainFallbackLogs >= MAX_HARDWARE_TERRAIN_FALLBACK_LOGS) {
            return;
        }
        hardwareTerrainFallbackLogs++;
        MainMod.LOGGER.warn(
                "[AUSMHardwareTerrainFallback] call={} stage={} active={} safeVanilla={} reason='{}' detail={} frame={} worldFrame={} world={} gl={}",
                hardwareTerrainFallbackLogs,
                stage,
                isPipelineActive,
                hardwareSafeVanillaTerrain,
                hardwareSafeVanillaTerrainReason
                        + (softVanillaTerrainRenderer ? ", softVanilla='" + softVanillaTerrainRendererReason + "'" : "")
                        + (shaderedNothiriumGlobalBypass ? ", shaderedNothiriumBypass='" + shaderedNothiriumGlobalBypassReason + "'" : ""),
                detail,
                pipelineFrameId,
                worldFrameActive,
                describeWorld(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft() != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft()) : null),
                glStateSummary()
        );
    }

    public int getCenterDepthSmoothTexture() {
        ensureCenterDepthSmoothTexture();
        return centerDepthSmoothTexture;
    }

    protected void ensureCenterDepthSmoothTexture() {
        if (centerDepthSmoothTexture != -1) {
            return;
        }

        centerDepthSmoothTexture = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, centerDepthSmoothTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        centerDepthTextureBuffer.clear();
        centerDepthTextureBuffer.put(centerDepthSmooth).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, 1, 1, 0, GL11.GL_RED, GL11.GL_FLOAT, centerDepthTextureBuffer);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void updateCenterDepthSmoothTexture() {
        ensureCenterDepthSmoothTexture();
        centerDepthTextureBuffer.clear();
        centerDepthTextureBuffer.put(centerDepthSmooth).flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + TextureBinder.CENTER_DEPTH_SMOOTH_TEXTURE_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, centerDepthSmoothTexture);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 1, 1, GL11.GL_RED, GL11.GL_FLOAT, centerDepthTextureBuffer);
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void deleteCenterDepthSmoothTexture() {
        if (centerDepthSmoothTexture != -1) {
            GL11.glDeleteTextures(centerDepthSmoothTexture);
            centerDepthSmoothTexture = -1;
        }
    }

    public int getNoiseTexture() {
        if (noiseTexture == -1) {
            noiseTexture = ShaderTextureLoader.createNoiseTexture(256);
        }
        return noiseTexture;
    }

    protected void initializeNoiseTexture(ShaderPack pack, ShaderProperties properties) {
        ShaderCustomTextureBinding customNoise = packDirectives.noiseTexture();
        if (customNoise != null) {
            try {
                noiseTexture = ShaderTextureLoader.loadTexture(
                        pack,
                        customNoise.resourcePath(),
                        customNoise.blur(),
                        customNoise.clamp()
                );
                MainMod.LOGGER.debug("[ShaderTextures] Loaded custom noisetex from {}", customNoise.resourcePath());
                return;
            } catch (IOException e) {
                MainMod.LOGGER.warn("[ShaderTextures] Failed to load custom noisetex {}, using generated noise", customNoise.resourcePath(), e);
            }
        }

        int resolution = parseIntSetting(pack, properties, "noiseTextureResolution", packDirectives.noiseTextureResolution());
        noiseTexture = ShaderTextureLoader.createNoiseTexture(resolution);
    }

    protected void deleteNoiseTexture() {
        if (noiseTexture != -1) {
            GL11.glDeleteTextures(noiseTexture);
            noiseTexture = -1;
        }
    }

    protected void copyPreTranslucentDepth() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }
        if (!preTranslucentDepthCopiedThisFrame) {
            pingPongManager.copyPreTranslucentDepth();
            preTranslucentDepthCopiedThisFrame = true;
        }
    }

    protected abstract void applyShaderImageTextureBarrier();

    protected abstract boolean assignRenderChunkWorld(RenderChunk chunk, World world);

    public abstract void blitWorldFramebufferToMinecraft();

    protected abstract void clearDirectRecoveredWindowSource();

    public abstract void clearPendingShaderChunkRefreshes();

    public abstract void clearScheduledBloomTerrainRefresh();

    public abstract void clearScheduledWorldTerrainRefresh();

    public abstract void clearShaderlessBloomMetadata();

    protected abstract int[][] compositeFallbackProbePoints(int width, int height);

    protected abstract boolean deferredBufferHasSceneContent(DeferredFramebuffer framebuffer, Attachment attachment);

    protected abstract String describeDeferredFramebuffer(DeferredFramebuffer framebuffer);

    protected abstract String describeWorld(World world);

    protected abstract boolean computeShouldBypassWorldPassRendering();

    protected abstract boolean ensureDistantHorizonsCompositeProgram();

    public abstract void finishBypassedWorldPassRendering();

    protected abstract boolean isRecoverableColorOnlySceneColor(float[] color);

    protected abstract void logBetterPortalsPipeline(String stage);

    protected abstract void logBetterPortalsPipeline(String stage, String detail);

    public abstract void prepareBypassedWorldPassRendering();

    protected abstract void renderNativeBloomLayerIfNeeded();

    protected abstract void resetPipelineState();

    protected abstract void resetPipelineState(Framebuffer preferredTarget);

    protected abstract void restoreVanillaLightmapTexture(Minecraft mc);

    protected abstract void runComputePrograms(List<ComputeProgram> computes, RenderPass bindingPass);

    protected abstract void runFullscreenPasses(ProgramArrayId arrayId);

    protected abstract void runSetupComputesIfNeeded();

    protected abstract float[] safeReadRecoveryColor(DeferredFramebuffer framebuffer, int x, int y);

    protected abstract float[] safeReadDeferredColor(DeferredFramebuffer framebuffer, Attachment attachment, int x, int y);

    protected abstract float safeReadDeferredDepth(DeferredFramebuffer framebuffer, int x, int y,
                                                   int colorWidth, int colorHeight);

    protected abstract float safeReadDeferredDepthSnapshot(DeferredFramebuffer framebuffer, int snapshotIndex,
                                                           int x, int y);

    public abstract void scheduleBloomTerrainRefresh(String reason);

    protected abstract void scheduleDimensionSwitchTerrainRefresh();

    public abstract void scheduleFullWorldTerrainRefresh();

    public abstract void scheduleWorldTerrainRefresh();

    public abstract void scheduleWorldLoadLightRecalculation();

    protected abstract boolean shouldRenderShaderlessCustomSkyBackingNow(Minecraft mc);

    public abstract boolean shouldForceVanillaTerrainRenderer();

    protected static boolean isNothiriumLoaded() {
        return Loader.isModLoaded(NOTHIRIUM_MOD_ID) || Loader.isModLoaded(NAUGHTHIRIUM_MOD_ID);
    }

    protected static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    protected static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }

}
