package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.luna.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_REALTIME_CUTOUT_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_REALTIME_TRANSLUCENT_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SPARSE_SHADOW_MIN_TERRAIN_DRAWS;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionBoolean;

abstract class PipelineLightVoxelInjection extends PipelineShadowRendering {
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
            if (pos == null || !MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
                if (self().isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }
            if (!self().isInsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                if (self().isWellOutsideVoxelVolume(pos, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ)) {
                    syntheticLightCandidates.remove(entry.getKey(), pos);
                }
                continue;
            }

            TileEntity tileEntity;
            try {
                tileEntity = MinecraftReflectionCompat.call(world, TileEntity.class, null, new String[]{"func_175625_s", "getTileEntity"},
                        new Class<?>[]{BlockPos.class}, pos);
            } catch (RuntimeException ignored) {
                tileEntity = null;
            }
            int[] projectRedVoxelIds = new int[8];
            int projectRedCount = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, projectRedVoxelIds);
            if (projectRedCount > 0) {
                for (int i = 0; i < projectRedCount && injected < remainingBudget; i++) {
                    if (self().injectVoxelAt(pos, projectRedVoxelIds[i], dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                        injected++;
                    }
                }
                continue;
            }

            IBlockState state = actualLightState(MinecraftReflectionCompat.worldBlockState(world, pos), world, pos);
            SyntheticLightInfo lightInfo = syntheticLightInfo(state, world, pos);
            if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
                continue;
            }

            if (self().injectVoxelAt(pos, lightInfo.voxelId, dimensions, cameraFloorX, cameraFloorY, cameraFloorZ, writtenVoxels)) {
                injected++;
            }
        }
        return injected;
    }

    protected boolean isWellOutsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        return Math.abs(MinecraftReflectionCompat.blockPosX(pos) - cameraFloorX) > dimensions[0]
                || Math.abs(MinecraftReflectionCompat.blockPosY(pos) - cameraFloorY) > dimensions[1]
                || Math.abs(MinecraftReflectionCompat.blockPosZ(pos) - cameraFloorZ) > dimensions[2];
    }

    protected boolean isInsideVoxelVolume(BlockPos pos, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ) {
        if (pos == null || dimensions == null || dimensions.length < 3) {
            return false;
        }
        int x = (int) Math.floor(MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        return x >= 0 && y >= 0 && z >= 0
                && x < dimensions[0] && y < dimensions[1] && z < dimensions[2];
    }

    protected boolean injectVoxelAt(BlockPos pos, int voxelId, int[] dimensions, int cameraFloorX, int cameraFloorY, int cameraFloorZ,
                                    Set<Long> writtenVoxels) {
        if (voxelId <= 0) {
            return false;
        }

        int x = (int) Math.floor(MinecraftReflectionCompat.blockPosX(pos) + 0.5 - cameraFloorX + dimensions[0] * 0.5);
        int y = (int) Math.floor(MinecraftReflectionCompat.blockPosY(pos) + 0.5 - cameraFloorY + dimensions[1] * 0.5);
        int z = (int) Math.floor(MinecraftReflectionCompat.blockPosZ(pos) + 0.5 - cameraFloorZ + dimensions[2] * 0.5);
        if (x < 0 || y < 0 || z < 0 || x >= dimensions[0] || y >= dimensions[1] || z >= dimensions[2]) {
            return false;
        }
        if (writtenVoxels != null) {
            writtenVoxels.add(PipelineWorldRenderScope.packedVoxelKey(x, y, z));
        }
        return shaderImages.writeRedInteger3D(x, y, z, voxelId, "voxel_img", "voxelimg", "voxel_sampler", "voxeltex");
    }

    protected static long packedVoxelKey(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ z;
    }

    protected static int localActVoxelId(int materialId) {
        int standardVoxelId = switch (materialId) {
            case 10056 -> 14; // Lava cauldron
            case 10068 -> 13; // Lava
            case 10072 -> 5;  // Fire
            case 10076 -> 27; // Soul fire
            case 10216 -> 62; // Crimson stem / hyphae
            case 10224 -> 63; // Warped stem / hyphae
            case 10332 -> 36; // Amethyst
            case 10396 -> 11; // Jack o'lantern
            case 10404 -> 6;  // Sea pickle
            case 10412 -> 10; // Glowstone
            case 10448 -> 18; // Sea lantern
            case 10452 -> 37; // Magma block
            case 10476 -> 26; // Crying obsidian
            case 10496 -> 2;  // Torch
            case 10500 -> 3;  // End rod
            case 10508, 10512 -> 39; // Chorus flower
            case 10516 -> 21; // Lit furnace
            case 10528 -> 28; // Soul torch
            case 10544 -> 34; // Glow lichen
            case 10548 -> 33; // Enchanting table
            case 10556 -> 58; // Active end portal frame
            case 10560 -> 12; // Lantern
            case 10564 -> 29; // Soul lantern
            case 10572 -> 38; // Dragon egg
            case 10576 -> 22; // Lit smoker
            case 10580 -> 23; // Lit blast furnace
            case 10592 -> 17; // Lit respawn anchor
            case 10596 -> 66; // Lit redstone wire
            case 10604 -> 35; // Redstone torch
            case 10616, 10624 -> 31; // Lit redstone ore
            case 10632 -> 20; // Glow-berry cave vines
            case 10640 -> 16; // Lit redstone lamp
            case 10644 -> 67; // Lit repeater / comparator
            case 10648 -> 19; // Shroomlight
            case 10652 -> 15; // Campfire
            case 10656 -> 30; // Soul campfire
            case 10680 -> 7;  // Ochre froglight
            case 10684 -> 8;  // Verdant froglight
            case 10688 -> 9;  // Pearlescent froglight
            case 10704 -> 57; // Active sculk sensor
            case 10788 -> 36; // Active calibrated sculk sensor
            case 10852 -> 55; // Bright lit copper bulb
            case 10856 -> 56; // Dim lit copper bulb
            case 10868 -> 54; // Active trial spawner / vault
            case 10876 -> 69; // Active ominous trial spawner / vault
            case 10948 -> 82; // Active creaking heart
            case 10972 -> 83; // Firefly bush
            case 10976, 10980 -> 81; // Open eyeblossom
            case 10984, 10986, 10988 -> 84; // Copper torch / lantern
            case 30020 -> 25; // Nether portal
            case 32016 -> 4;  // Beacon
            default -> 0;
        };
        if (standardVoxelId > 0) {
            return standardVoxelId;
        }
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
        if ("tconstruct".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "seared_furnace_controller".equals(MinecraftReflectionCompat.resourcePath(name))
                && stateName(state).contains("active=true")) {
            return 71;
        }
        if ("aether_legacy".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "aether_portal".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return PipelineWorldRenderScope.localActVoxelId(10914); // Aether sky blue.
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
        if (self().shouldUseNothiriumShadowBridge()) {
            return true;
        }

        if (mc == null || viewEntity == null || !(MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return true;
        }
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        RenderChunk[] renderChunks = MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderChunks == null) {
            return true;
        }

        double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = self().shadowRenderCullDistance();
        double maxDistanceSquared = maxDistance * maxDistance;

        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null) {
                continue;
            }
            BlockPos position = MinecraftReflectionCompat.renderChunkPosition(renderChunk);
            double dx = MinecraftReflectionCompat.blockPosX(position) + 8.0D - cameraX;
            double dy = MinecraftReflectionCompat.blockPosY(position) + 8.0D - cameraY;
            double dz = MinecraftReflectionCompat.blockPosZ(position) + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            if (!MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.SOLID)
                    || !MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.CUTOUT_MIPPED)
                    || !MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.CUTOUT)
                    || (shaderProperties.renderSettings().shadowTranslucent()
                    && !MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, BlockRenderLayer.TRANSLUCENT))) {
                return true;
            }
        }
        return false;
    }

    protected static void configureShadowTerrainRenderState() {
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        PipelineWorldRenderScope.resetPortalMaskState();
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
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
        boolean previousPolygonOffset = GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        float previousPolygonOffsetFactor = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR);
        float previousPolygonOffsetUnits = GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        PipelineWorldRenderScope.configureShadowTerrainRenderState();
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        if (phase != WorldRenderingPhase.TERRAIN_TRANSLUCENT && shadowPolygonOffset) {
            // configureShadowTerrainRenderState resets portal state, including
            // polygon offset. Reapply the pack's shadow bias per terrain layer.
            // Flat/crossed cutout models need a larger constant bias than solid
            // terrain because their shadow depth is frequently coplanar with
            // the supporting block or a second crossed quad.
            float polygonOffsetFactor = shadowPolygonOffsetFactor;
            float polygonOffsetUnits = shadowPolygonOffsetUnits;
            boolean pixelatedShadows = optionBoolean(shaderProperties, "PIXELATED_SHADOWS", false);
            if ((layer == BlockRenderLayer.SOLID && pixelatedShadows)
                    || layer == BlockRenderLayer.CUTOUT
                    || layer == BlockRenderLayer.CUTOUT_MIPPED) {
                polygonOffsetFactor = Math.max(polygonOffsetFactor, 2.0F);
                polygonOffsetUnits = Math.max(polygonOffsetUnits, 8.0F);
            }
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
        }
        if (phase == WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            MinecraftReflectionCompat.glStateDisableBlend();
            GL11.glDepthFunc(GL11.GL_ALWAYS);
        }
        try {
            int count = self().renderShadowBlockLayer(mc, layer, partialTicks, viewEntity);
            return count;
        } finally {
            GL11.glDepthFunc(previousDepthFunc);
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousPolygonOffset) {
                GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            } else {
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            }
            GL11.glPolygonOffset(previousPolygonOffsetFactor, previousPolygonOffsetUnits);
            endPass();
        }
    }

    protected int renderShadowBlockLayer(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (mc == null || viewEntity == null) {
            return 0;
        }
        if (self().shouldUseNothiriumShadowBridge()) {
            double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
            double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
            double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
            // Nothirium's view list cannot represent the light-space frustum.
            // Draw every prepared provider chunk inside the shaderpack's
            // shadow distance; compile admission stays bounded separately.
            return Math.max(0, nothiriumShadowRenderer.renderProviderLayerSchedulingCompiles(
                    layer,
                    cameraX,
                    cameraY,
                    cameraZ,
                    self().shadowLayerCullDistance(layer),
                    nothiriumFallbackBlockEntityId(layer),
                    nothiriumFallbackRenderType(layer),
                    false
            ));
        }

        // RenderGlobal's pass-2 container can report a nonzero chunk count
        // without submitting geometry to the shader-owned shadow FBO. Build
        // and draw the view-frustum container first while the shadow target
        // and camera are active.
        int beforeFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        ShadowFramebuffer.DepthStats beforeDepth = shadowTargetProbeLogs < 0
                ? shadowFramebuffer.readDepthStats(1) : null;
        int directCount = self().renderShadowBlockLayerFromViewFrustum(mc, layer, partialTicks, viewEntity);
        if (shadowTargetProbeLogs < 0) {
            shadowTargetProbeLogs++;
            ShadowFramebuffer.DepthStats afterDepth = shadowFramebuffer.readDepthStats(1);
            MainMod.LOGGER.info(
                    "[AUSMShadowTargetRouteProbe] call={} layer={} targetFbo={} beforeFbo={} afterFbo={} directCount={} beforeDepth={} afterDepth={} viewport={}x{}",
                    shadowTargetProbeLogs, layer, shadowFramebuffer.framebufferId(), beforeFbo,
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), directCount,
                    beforeDepth != null ? beforeDepth.nonClear() + "/" + beforeDepth.total() : "disabled",
                    afterDepth.nonClear() + "/" + afterDepth.total(),
                    shadowFramebuffer.resolution(), shadowFramebuffer.resolution());
        }
        if (directCount > 0) {
            return directCount;
        }

        RenderGlobal renderGlobal = MinecraftReflectionCompat.renderGlobal(mc);
        if (renderGlobal == null) {
            return 0;
        }
        int count = MinecraftReflectionCompat.renderBlockLayer(renderGlobal, layer, partialTicks, 2, viewEntity);
        if (count > 0) {
            return count;
        }
        return 0;
    }

    protected double shadowLayerCullDistance(BlockRenderLayer layer) {
        double fullDistance = self().shadowRenderCullDistance();
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            return Math.min(fullDistance, SHADOW_REALTIME_TRANSLUCENT_DISTANCE);
        }
        if (layer == BlockRenderLayer.CUTOUT || layer == BlockRenderLayer.CUTOUT_MIPPED) {
            return Math.min(fullDistance, SHADOW_REALTIME_CUTOUT_DISTANCE);
        }
        return fullDistance;
    }

    protected boolean shouldKeepPreviousShadowMapForSparseCandidate(Entity viewEntity, float partialTicks) {
        if (viewEntity == null) {
            return false;
        }
        double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity),
                MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity),
                MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity),
                MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        int remaining = SPARSE_SHADOW_MIN_TERRAIN_DRAWS;
        int ready = nothiriumShadowRenderer.countRenderableShadowLayer(
                BlockRenderLayer.SOLID, cameraX, cameraY, cameraZ,
                self().shadowLayerCullDistance(BlockRenderLayer.SOLID), remaining);
        if (ready < 0) {
            return false;
        }
        remaining -= ready;
        if (remaining > 0) {
            int count = nothiriumShadowRenderer.countRenderableShadowLayer(
                    BlockRenderLayer.CUTOUT_MIPPED, cameraX, cameraY, cameraZ,
                    self().shadowLayerCullDistance(BlockRenderLayer.CUTOUT_MIPPED), remaining);
            if (count < 0) {
                return false;
            }
            ready += count;
            remaining -= count;
        }
        if (remaining > 0) {
            int count = nothiriumShadowRenderer.countRenderableShadowLayer(
                    BlockRenderLayer.CUTOUT, cameraX, cameraY, cameraZ,
                    self().shadowLayerCullDistance(BlockRenderLayer.CUTOUT), remaining);
            if (count < 0) {
                return false;
            }
            ready += count;
            remaining -= count;
        }
        if (remaining > 0 && shaderProperties.renderSettings().shadowTranslucent()) {
            int count = nothiriumShadowRenderer.countRenderableShadowLayer(
                    BlockRenderLayer.TRANSLUCENT, cameraX, cameraY, cameraZ,
                    self().shadowLayerCullDistance(BlockRenderLayer.TRANSLUCENT), remaining);
            if (count < 0) {
                return false;
            }
            ready += count;
        }
        if (ready >= SPARSE_SHADOW_MIN_TERRAIN_DRAWS) {
            return false;
        }
        if (shadowMapCoverageRegressionLogs < 8) {
            shadowMapCoverageRegressionLogs++;
            MainMod.LOGGER.info(
                    "[ShadowHealth] Keeping previous shadow map; sparse candidate rejected before clear. terrainDraws={} minTerrainDraws={} frame={}",
                    ready,
                    SPARSE_SHADOW_MIN_TERRAIN_DRAWS,
                    pipelineFrameId
            );
        }
        return true;
    }
}
