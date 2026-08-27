package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GLContext;

import static com.luna.ausm.impl.pipeline.PipelineLightConstants.CPU_LIGHT_TILE_ENTITY_SNAPSHOT_INTERVAL_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.ENABLE_CPU_LIGHT_INJECTION;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_CPU_LIGHT_TILE_ENTITY_SCANS_PER_FRAME;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_RENDER_GLOBAL_LOAD_RENDERER_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_REALTIME_MAX_FRAME_SECONDS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_STABLE_UPDATE_INTERVAL_TICKS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_STABLE_UPDATE_MOVEMENT_SQ;

abstract class PipelineShadowRendering extends PipelineVanillaTerrainMaintenance {
    protected void adoptCurrentRenderGlobalViewFrustum(World world) {
        if (!isPipelineActive) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null
                || world == null
                || MinecraftReflectionCompat.renderGlobal(mc) == null
                || !(MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return;
        }

        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        if (viewFrustum == null) {
            self().logTerrainDiagnostic("adopt-view-frustum:missing", world, "");
            return;
        }

        vanillaViewFrustums
                .computeIfAbsent(MinecraftReflectionCompat.renderGlobal(mc), ignored -> new IdentityHashMap<>())
                .put(world, viewFrustum);
        vanillaViewFrustumRenderDistances
                .computeIfAbsent(MinecraftReflectionCompat.renderGlobal(mc), ignored -> new IdentityHashMap<>())
                .put(world, MinecraftReflectionCompat.renderDistanceChunks(mc));
        self().rememberStableMainWorldVanillaRenderDistance(world, MinecraftReflectionCompat.renderDistanceChunks(mc));
        activeVanillaViewFrustumRenderGlobal = MinecraftReflectionCompat.renderGlobal(mc);
        activeVanillaViewFrustumWorld = world;
        activeVanillaViewFrustumRenderDistanceChunks = MinecraftReflectionCompat.renderDistanceChunks(mc);
        self().logTerrainDiagnostic("adopt-view-frustum", world, "viewFrustum=" + PipelineWorldRenderScope.viewFrustumId(viewFrustum)
                + ", renderDistance=" + MinecraftReflectionCompat.renderDistanceChunks(mc));
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
            self().logTerrainDiagnostic("sync-render-global-world", world, "previous=" + safeDimensionId(previous)
                    + ", current=" + safeDimensionId(worldClient));
            return true;
        }
        return false;
    }

    public void renderShadowMap(float partialTicks) {
        if (!isPipelineActive || shadowFramebuffer == null || lastShadowFrameId == pipelineFrameId) {
            return;
        }
        if (!self().hasActiveShadowProgram()) {
            return;
        }
        if (ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            lastShadowFrameId = pipelineFrameId;
            shadowMapPopulated = false;
            shadowMapUsable = false;
            shadowMapSparseForSampling = true;
            shadowMapCoverageStableFrames = 0;
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }
        if (invalidShadowTerrainSuppressedFrames > 0) {
            lastShadowFrameId = pipelineFrameId;
            return;
        }
        if (self().shouldSkipStationaryShadowMap(world, viewEntity, partialTicks)) {
            lastShadowFrameId = pipelineFrameId;
            return;
        }

        self().runPreparePassesBeforeShadowIfRequested();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-begin world=" + safeDimensionId(world));
        lastShadowFrameId = pipelineFrameId;
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean previousRenderChunksMany = MinecraftReflectionCompat.fieldBoolean(mc, false, "field_175612_E", "renderChunksMany");

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();

        try {
            // Mark the shadow scope before setupTerrain. The transformed
            // Nothirium handler consults this state to leave light-space setup
            // to vanilla and preserve its main-camera visibility lists.
            renderingShadowMap = true;
            self().setupShadowCamera(viewEntity, partialTicks);
            ICamera shadowCamera = self().createShadowCamera(viewEntity, partialTicks);
            // Iris disables chunk occlusion culling while building the shadow terrain list.
            // The 1.12 equivalent is renderChunksMany; leaving it enabled lets the normal
            // camera visibility graph leak into the light-space pass.
            MinecraftReflectionCompat.setRenderChunksMany(mc, false);
            boolean useNothiriumShadowBridge = self().shouldUseNothiriumShadowBridge();
            if (!useNothiriumShadowBridge) {
                self().ensureVanillaTerrainRenderer();
                MinecraftReflectionCompat.setupTerrain(
                        MinecraftReflectionCompat.renderGlobal(mc),
                        viewEntity,
                        partialTicks,
                        shadowCamera,
                        self().nextShadowFrameCount(),
                        MinecraftReflectionCompat.playerIsSpectator(MinecraftReflectionCompat.player(mc))
                );
            }

            self().clearColoredLightImages();
            // A shaderpack shadow program is meaningless without terrain depth.
            // Treat the active shadow program as an explicit terrain request;
            // stale/pack-local shadowTerrain=false must not silently produce a
            // permanently clear shadow map.
            // The candidate scan only sees vanilla RenderChunk layer buffers.
            // Celeritas/Nothirium can own those buffers while the active world
            // pass still has visible terrain, so it cannot gate shadow terrain.
            boolean renderShadowTerrain = self().hasActiveShadowProgram();
            if (useNothiriumShadowBridge) {
                nothiriumShadowRenderer.drainUploads();
                nothiriumShadowRenderer.beginShadowSelection(
                        PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks),
                        PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks),
                        PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks),
                        self().shadowRenderCullDistance()
                );
                if (shadowMapUsable && self().shouldKeepPreviousShadowMapForSparseCandidate(
                        viewEntity, partialTicks)) {
                    return;
                }
            }

            shadowFramebuffer.bindForRendering();
            shadowFramebuffer.clear();
            PipelineWorldRenderScope.configureShadowTerrainRenderState();
            TextureBinder.restoreDefaultTextureUnit();
            MinecraftReflectionCompat.bindTexture(MinecraftReflectionCompat.textureManager(mc), MinecraftReflectionCompat.blocksTexture());
            int solidCount = -1;
            int cutoutMippedCount = -1;
            int cutoutCount = -1;
            int translucentCount = -1;
            int blockEntityCount = -1;
            if (renderShadowTerrain) {
                solidCount = self().renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_SOLID, BlockRenderLayer.SOLID, partialTicks, viewEntity);
                cutoutMippedCount = self().renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED, BlockRenderLayer.CUTOUT_MIPPED, partialTicks, viewEntity);
                cutoutCount = self().renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_CUTOUT, BlockRenderLayer.CUTOUT, partialTicks, viewEntity);
            }
            if (shaderProperties.renderSettings().shadowEntities()
                    || shaderProperties.renderSettings().shadowPlayer()) {
                beginPhase(WorldRenderingPhase.ENTITIES);
                // RenderLib replaces RenderGlobal.renderEntities with a queued renderer
                // that is only prepared during the normal world pass. The shadow pass
                // has its own camera, so render entities directly here.
                self().renderShadowEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            if (shaderProperties.renderSettings().shadowBlockEntities()
                    || shaderProperties.renderSettings().shadowLightBlockEntities()) {
                beginPhase(WorldRenderingPhase.BLOCK_ENTITIES);
                blockEntityCount = self().renderShadowBlockEntitiesDirect(mc, viewEntity, shadowCamera, partialTicks);
                endPass();
            }
            shadowFramebuffer.copyDepthToSnapshot();
            if (renderShadowTerrain && shaderProperties.renderSettings().shadowTranslucent()) {
                translucentCount = self().renderShadowTerrainLayer(mc, WorldRenderingPhase.TERRAIN_TRANSLUCENT, BlockRenderLayer.TRANSLUCENT, partialTicks, viewEntity);
            }
            self().injectMappedTileEntityVoxels(mc);
            self().applyShaderImageTextureBarrier();
            shadowFramebuffer.generateShadowColorMipmaps();
            self().updateShadowMapUsability(solidCount, cutoutMippedCount, cutoutCount, translucentCount, blockEntityCount);
            self().runComputePrograms(shadowComputePrograms, RenderPass.SHADOW);
            self().runFullscreenPasses(ProgramArrayId.SHADOWCOMP);
            if (shadowMapUsable) {
                self().rememberShadowMapRender(world, viewEntity, partialTicks);
            } else {
                self().resetShadowRenderCache();
            }
        } finally {
            nothiriumShadowRenderer.endShadowSelection();
            MinecraftReflectionCompat.setRenderChunksMany(mc, previousRenderChunksMany);
            renderingShadowMap = false;
            activePass = null;
            activeShaderKey = null;
            activePhase = WorldRenderingPhase.NONE;
            overridePhase = null;
            passStack.clear();
            MinecraftReflectionCompat.glUseProgram(0);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glColorMask(true, true, true, true);
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            MinecraftReflectionCompat.glStateEnableAlpha();
            MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
            GL11.glDepthFunc(GL11.GL_LEQUAL);

            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), previousFramebuffer);
            viewportBuffer.position(0);
            GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));
            TextureBinder.restoreDefaultTextureUnit();
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:shadow-end world=" + safeDimensionId(world));
        }
    }

    protected boolean shouldSkipStationaryShadowMap(World world, Entity viewEntity, float partialTicks) {
        if (!shadowMapUsable || !shadowMapPopulated || shaderProperties == null) {
            return false;
        }
        int dimensionId = safeDimensionId(world);
        Object time = MinecraftReflectionCompat.invoke(
                world,
                new String[]{"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        long worldTime = time instanceof Number ? ((Number) time).longValue() : 0L;
        if (dimensionId != lastShadowRenderDimensionId
                || worldTime < lastShadowRenderWorldTime) {
            return false;
        }

        long framesPerShadowUpdate = currentFrameTime <= 1.0F / 60.0F ? 3L : 2L;
        boolean presentationRateLimited = currentFrameTime <= SHADOW_REALTIME_MAX_FRAME_SECONDS
                && pipelineFrameId % framesPerShadowUpdate != 0L;
        boolean sameWorldTick = worldTime - lastShadowRenderWorldTime < SHADOW_STABLE_UPDATE_INTERVAL_TICKS;
        if (!presentationRateLimited && !sameWorldTick) {
            return false;
        }
        double x = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double y = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double z = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double dx = x - lastShadowRenderX;
        double dy = y - lastShadowRenderY;
        double dz = z - lastShadowRenderZ;
        // A populated provider shadow map is expensive to rebuild: it scans
        // and draws radial terrain rather than the normal forward list. The
        // projection itself is texel-snapped, so tolerate quarter-block
        // motion before rebuilding instead of treating head bob and tiny
        // mouse movement as a full radial redraw request. At interactive
        // frame rates, update the shadow map every second or third presented
        // frame; its model-view matrix is rebased below between updates.
        boolean reuse = dx * dx + dy * dy + dz * dz < SHADOW_STABLE_UPDATE_MOVEMENT_SQ;
        if (reuse) {
            // The cached depth map was rendered in a coordinate system relative
            // to the previous camera. Rebase its matrix so current player-space
            // positions still address the same world-space shadow texels.
            MatrixState.rebaseShadowModelView(dx, dy, dz);
        }
        return reuse;
    }

    protected void rememberShadowMapRender(World world, Entity viewEntity, float partialTicks) {
        lastShadowRenderDimensionId = safeDimensionId(world);
        Object time = MinecraftReflectionCompat.invoke(
                world,
                new String[]{"func_82737_E", "getTotalWorldTime"},
                new Class<?>[0]
        );
        lastShadowRenderWorldTime = time instanceof Number ? ((Number) time).longValue() : 0L;
        lastShadowRenderX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        lastShadowRenderY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        lastShadowRenderZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
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

        List<TileEntity> loadedTileEntities = self().cpuLightTileEntitySnapshot(world);
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
            if (tileEntity == null || MinecraftReflectionCompat.tileEntityInvalid(tileEntity)) {
                continue;
            }

            BlockPos pos = MinecraftReflectionCompat.tileEntityPos(tileEntity);
            if (!self().isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                continue;
            }

            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            if (projectRedCount > 0) {
                // The loaded-tile scan is cursor-limited, while Entree clears its
                // voxel image before every shadow update. Persist a discovered
                // ProjectRed light so it is re-injected on every following frame
                // instead of appearing only when the scan cursor revisits it.
                putSyntheticLightCandidate(pos, true);
                projectRedMatches++;
                for (int i = 0; i < projectRedCount && injected < MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME; i++) {
                    if (self().injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                    }
                }
                continue;
            }

            // Generic shader block-id lights are handled by the shaderpack shadow voxelizer.
            // Keep this CPU path restricted to ProjectRed tile entities to avoid global tint leaks.
        }

        if (ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION) {
            injected += self().injectRecordedSyntheticLightVoxels(
                    world,
                    dimensions,
                    cameraFloorX,
                    cameraFloorY,
                    cameraFloorZ,
                    writtenVoxels,
                    MAX_CPU_LIGHT_VOXEL_WRITES_PER_FRAME - injected
            );

            injected += self().injectVoxelizedLightBlockVoxels(
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
            cpuLightTileEntitySnapshot = new ArrayList<>(MinecraftReflectionCompat.worldLoadedTileEntities(world));
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
        if (remainingBudget <= 0) {
            return 0;
        }
        if (world == null || dimensions == null || dimensions.length < 3) {
            return 0;
        }
        if (cpuLightBlockScanWorld != world) {
            cpuLightBlockScanWorld = world;
            cpuLightBlockScanCursor = 0;
        }

        int scanWidth = Math.clamp(dimensions[0], 1, MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH);
        int scanHeight = Math.clamp(dimensions[1], 1, MAX_CPU_LIGHT_BLOCK_SCAN_HEIGHT);
        int scanDepth = Math.clamp(dimensions[2], 1, MAX_CPU_LIGHT_BLOCK_SCAN_WIDTH);
        int scanVolume = scanWidth * scanHeight * scanDepth;
        int scanBudget = Math.min(MAX_CPU_LIGHT_BLOCK_SCANS_PER_FRAME, scanVolume);
        int injected = 0;

        for (int scan = 0; scan < scanBudget && injected < remainingBudget; scan++) {
            if (cpuLightBlockScanCursor >= scanVolume) {
                cpuLightBlockScanCursor = 0;
            }
            int logicalCursor = cpuLightBlockScanCursor++;
            int centerCursor = scanWidth / 2
                    + (scanHeight / 2) * scanWidth
                    + (scanDepth / 2) * scanWidth * scanHeight;
            int cursor = (logicalCursor + centerCursor) % scanVolume;
            int localX = cursor % scanWidth;
            int localY = (cursor / scanWidth) % scanHeight;
            int localZ = cursor / (scanWidth * scanHeight);
            BlockPos pos = new BlockPos(
                    cameraFloorX + localX - scanWidth / 2,
                    cameraFloorY + localY - scanHeight / 2,
                    cameraFloorZ + localZ - scanDepth / 2
            );
            if (MinecraftReflectionCompat.blockPosY(pos) < 0 || MinecraftReflectionCompat.blockPosY(pos) > 255 || !MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
                continue;
            }

            IBlockState state;
            try {
                state = MinecraftReflectionCompat.worldBlockState(world, pos);
            } catch (RuntimeException ignored) {
                continue;
            }
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                continue;
            }
            // Shader packs such as Complementary Entree intentionally set
            // voxelizeLightBlocks=false while performing their own shadow-vertex
            // image writes. Those writes are unreliable on some 1.12/Nothirium
            // paths, so retain every emitter found by this bounded fallback and
            // re-inject it after the voxel image is cleared on following frames.
            putSyntheticLightCandidate(pos, true);
            if (self().injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
            }
        }

        return injected;
    }
}
