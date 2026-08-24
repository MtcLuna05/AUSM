package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.compat.NothiriumShadowRenderer;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.shader.ComputeProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineGlState.safeGetInteger;
import static com.luna.ausm.impl.pipeline.PipelineGlState.safeGetString;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_HARDWARE_TERRAIN_FALLBACK_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_HYBRID_MAINTENANCE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.HARDWARE_TERRAIN_FALLBACK_REFRESH_COOLDOWN_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_HYBRID_VANILLA_MAINTENANCE_FRAMES;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.parseIntSetting;

abstract class PipelineFrameLifecycle2 extends PipelineFrameLifecycle1 {
    public boolean shouldUseNothiriumHybridVanillaMaintenance() {
        return isPipelineActive
                && worldFrameActive
                && !renderingShadowMap
                && !renderingGuiScreen()
                && nothiriumHybridVanillaMaintenanceFrames > 0
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain)
                && !(ENABLE_SAFE_TERRAIN_FALLBACKS && softVanillaTerrainRenderer)
                && PipelineWorldRenderScope.isNothiriumLoaded()
                && NothiriumShadowRenderer.isAvailable();
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
                self().describeWorld(MinecraftReflectionCompat.minecraft() != null ? MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) : null),
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
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            self().ensureVanillaTerrainRenderer();
            return;
        }
        self().rebuildMainWorldVanillaViewFrustum(
                MinecraftReflectionCompat.renderGlobal(mc),
                MinecraftReflectionCompat.world(mc),
                "nothirium-main-vanilla-draw"
        );
        self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
        NothiriumBypass.markAllChanged();
        self().scheduleWorldTerrainRefresh(true, true, 0);
        self().scheduleInactiveVanillaRecoveryFrame();
        self().logHardwareTerrainFallback("prime-main-vanilla-draw", reason);
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
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return false;
        }

        int playerChunkX = ((int) Math.floor(MinecraftReflectionCompat.posX(MinecraftReflectionCompat.player(mc)))) >> 4;
        int playerChunkZ = ((int) Math.floor(MinecraftReflectionCompat.posZ(MinecraftReflectionCompat.player(mc)))) >> 4;
        if (MinecraftReflectionCompat.call(MinecraftReflectionCompat.world(mc), ChunkProviderClient.class, null, new String[]{"func_72863_F", "getChunkProvider"}, MinecraftReflectionCompat.NO_PARAMETERS) instanceof ChunkProviderClient provider) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    Chunk chunk = MinecraftReflectionCompat.call(provider, Chunk.class, null, new String[]{"func_186026_b", "getLoadedChunk"},
                            new Class<?>[]{int.class, int.class}, playerChunkX + dx, playerChunkZ + dz);
                    if (chunk != null && !MinecraftReflectionCompat.callBoolean(chunk, new String[]{"func_76621_g", "isEmpty"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return MinecraftReflectionCompat.worldIsBlockLoaded(
                MinecraftReflectionCompat.world(mc),
                new BlockPos(MinecraftReflectionCompat.player(mc))
        );
    }

    protected void activateHardwareSafeVanillaTerrain(String reason) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
            self().logHardwareTerrainFallback("skip-hardware-safe-disabled", reason);
            return;
        }
        if (hardwareSafeVanillaTerrain) {
            self().refreshHardwareSafeVanillaTerrain(reason, true);
            return;
        }
        hardwareSafeVanillaTerrain = true;
        hardwareSafeVanillaTerrainReason = reason;
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        self().logHardwareTerrainFallback(
                "activate",
                reason + ", maxAttribs=" + safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)
                        + ", renderer='" + safeGetString(GL11.GL_RENDERER) + "'"
        );
        updateNothiriumPipelineBlockFormatMode();
        self().refreshHardwareSafeVanillaTerrain(reason, true);
        self().scheduleInactiveVanillaRecoveryFrame();
    }

    protected void activateSoftVanillaTerrainRenderer(String reason) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS) {
            self().logHardwareTerrainFallback("skip-soft-vanilla-disabled", reason);
            return;
        }
        if (softVanillaTerrainRenderer) {
            return;
        }
        softVanillaTerrainRenderer = true;
        softVanillaTerrainRendererReason = reason;
        self().ensureVanillaTerrainRenderer();
        NothiriumBypass.markAllChanged();
        self().scheduleWorldTerrainRefresh(true, true, 0);
        self().scheduleInactiveVanillaRecoveryFrame();
        self().logHardwareTerrainFallback(
                "activate-soft-vanilla",
                reason + ", shaderBlockLayerOverrides=true"
        );
    }

    protected void refreshHardwareSafeVanillaTerrainForCamera(Minecraft mc) {
        if (!ENABLE_SAFE_TERRAIN_FALLBACKS
                || !isPipelineActive
                || (!hardwareSafeVanillaTerrain && !softVanillaTerrainRenderer)
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null) {
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

        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (viewEntity == null) {
            return;
        }
        int chunkX = ((int) Math.floor(MinecraftReflectionCompat.posX(viewEntity))) >> 4;
        int chunkZ = ((int) Math.floor(MinecraftReflectionCompat.posZ(viewEntity))) >> 4;
        boolean loadedNearPlayer = self().hasLoadedTerrainNearPlayer();
        boolean changed = lastHardwareSafeVanillaTerrainRefreshWorld != MinecraftReflectionCompat.world(mc)
                || lastHardwareSafeVanillaTerrainRefreshChunkX != chunkX
                || lastHardwareSafeVanillaTerrainRefreshChunkZ != chunkZ
                || (loadedNearPlayer && !lastHardwareSafeVanillaTerrainLoadedNearPlayer);

        lastHardwareSafeVanillaTerrainRefreshWorld = MinecraftReflectionCompat.world(mc);
        lastHardwareSafeVanillaTerrainRefreshChunkX = chunkX;
        lastHardwareSafeVanillaTerrainRefreshChunkZ = chunkZ;
        lastHardwareSafeVanillaTerrainLoadedNearPlayer = loadedNearPlayer;

        if (changed && loadedNearPlayer) {
            self().refreshHardwareSafeVanillaTerrain(
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

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc != null && MinecraftReflectionCompat.world(mc) != null && MinecraftReflectionCompat.renderGlobal(mc) != null) {
            if (hardReset) {
                self().deleteCachedVanillaTerrainRenderers();
                vanillaViewFrustumStateStack.clear();
                activeVanillaViewFrustumRenderGlobal = null;
                activeVanillaViewFrustumWorld = null;
                activeVanillaViewFrustumRenderDistanceChunks = -1;
                self().rebuildMainWorldVanillaViewFrustum(MinecraftReflectionCompat.renderGlobal(mc), MinecraftReflectionCompat.world(mc), "hardware-safe-vanilla");
            }
            self().ensureVanillaTerrainRenderer(MinecraftReflectionCompat.world(mc), true);
            MinecraftReflectionCompat.loadRenderers(MinecraftReflectionCompat.renderGlobal(mc));
        } else {
            self().ensureVanillaTerrainRenderer();
        }
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        self().scheduleInactiveVanillaRecoveryFrame();
        self().logHardwareTerrainFallback(
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
                self().describeWorld(MinecraftReflectionCompat.minecraft() != null ? MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) : null),
                glStateSummary()
        );
    }

    public int getCenterDepthSmoothTexture() {
        self().ensureCenterDepthSmoothTexture();
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
        self().ensureCenterDepthSmoothTexture();
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
        return PipelineCompatConstants.isNothiriumLoadedCached();
    }

    protected static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0D;
    }

    protected static String formatMillis(double millis) {
        return String.format(Locale.ROOT, "%.3f", millis);
    }
}
