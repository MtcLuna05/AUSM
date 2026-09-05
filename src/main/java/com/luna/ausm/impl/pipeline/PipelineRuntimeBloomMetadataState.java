package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.compat.ProjectRedIlluminationCompat;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import static com.luna.ausm.impl.pipeline.PipelineLightConstants.ENABLE_CPU_LIGHT_INJECTION;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_SYNTHETIC_LIGHT_CANDIDATES;
import static com.luna.ausm.impl.pipeline.PipelineLightConstants.MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_DECORATED_LIGHT_AUDIT_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_SAFE_TERRAIN_FALLBACKS;

abstract class PipelineRuntimeDiagnosticsState1 extends PipelineRuntimeDiagnosticsState0 {
    protected boolean stateHasBloomResourceGeometry(IBlockState state) {
        return bloomResourceClassifier.hasBloomResourceGeometry(state);
    }

    protected boolean isExplicitBloomState(IBlockState state) {
        return self().stateHasBloomResourceGeometry(state);
    }

    protected boolean isLumenizedBloomState(IBlockState state) {
        return self().isExplicitBloomState(state);
    }

    protected int nextFramedDiagnosticCount(IBlockState state, boolean priority) {
        if (priority) {
            int count = framedPriorityDiagnosticCount.incrementAndGet();
            return count <= MAX_FRAMED_PRIORITY_DIAGNOSTIC_LOGS ? count : -1;
        }
        if (PipelineRuntimeState.isArchitectureCraftShapeBlock(state)) {
            int count = architectureCraftDiagnosticCount.incrementAndGet();
            return count <= MAX_ARCHITECTURECRAFT_DIAGNOSTIC_LOGS ? count : -1;
        }
        int count = blockcrafteryDiagnosticCount.incrementAndGet();
        return count <= MAX_BLOCKCRAFTERY_DIAGNOSTIC_LOGS ? count : -1;
    }

    protected String framedDiagnosticKind(IBlockState state) {
        if (PipelineRuntimeState.isArchitectureCraftShapeBlock(state)) {
            return "architecturecraft";
        }
        if (PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return "blockcraftery";
        }
        return "unknown";
    }

    protected boolean isPriorityFramedDiagnosticState(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                      BlockRenderLayer bloomLayer) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        if (self().blockRenderEmissionForState(state, blockAccess, pos) > 0 || self().blockEntityId(state, blockAccess, pos) != 0) {
            return true;
        }
        if (bloomLayer != null && PipelineRuntimeState.canRenderInLayer(state, bloomLayer)) {
            return true;
        }
        if (self().stateHasBloomLayerGeometry(state)) {
            return true;
        }
        return self().isPriorityFramedDiagnosticName(state);
    }

    protected boolean isPriorityFramedDiagnosticName(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null) {
            return false;
        }
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        String namespace = name != null && MinecraftReflectionCompat.resourceNamespace(name) != null ? MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(Locale.ROOT) : "";
        return namespace.contains("lumenized") || path.contains("lumenized");
    }

    protected String framedDiagnosticInheritedStates(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                     BlockRenderLayer currentLayer, BlockRenderLayer bloomLayer) {
        IBlockState inheritedState = self().inheritedRenderState(state, blockAccess, pos);
        if (inheritedState == null) {
            return "[]";
        }
        return '[' + self().framedDiagnosticState("inherited0", inheritedState, blockAccess, pos,
                currentLayer, bloomLayer) + ']';
    }

    protected String framedDiagnosticState(String label, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                           BlockRenderLayer currentLayer, BlockRenderLayer bloomLayer) {
        if (state == null) {
            return label + "{state=null}";
        }

        Block block = MinecraftReflectionCompat.blockFromState(state);
        return label + "{"
                + "name=" + PipelineRuntimeState.stateName(state)
                + ", state=" + state
                + ", class=" + (block != null ? block.getClass().getName() : "null")
                + ", renderType=" + PipelineRuntimeState.safeRenderType(state)
                + ", naturalLayer=" + PipelineRuntimeState.safeRenderLayer(state)
                + ", canCurrent=" + PipelineRuntimeState.canRenderInLayer(state, currentLayer)
                + ", canSolid=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.SOLID)
                + ", canCutoutMipped=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)
                + ", canCutout=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                + ", canTranslucent=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                + ", canBloom=" + (bloomLayer != null && PipelineRuntimeState.canRenderInLayer(state, bloomLayer))
                + ", emission=" + self().blockRenderEmissionForState(state, blockAccess, pos)
                + ", lightAccess=" + PipelineRuntimeState.safeLightValue(state, blockAccess, pos)
                + ", lightRaw=" + PipelineRuntimeState.safeLightValue(state, null, null)
                + ", blockId=" + self().blockEntityId(state, blockAccess, pos)
                + ", metadata=" + PipelineRuntimeState.blockMetadata(state)
                + ", opaque=" + PipelineRuntimeState.safeOpaqueCube(state)
                + ", full=" + PipelineRuntimeState.safeFullCube(state)
                + ", material=" + (MinecraftReflectionCompat.stateMaterial(state) != null ? MinecraftReflectionCompat.stateMaterial(state) : "null")
                + "}";
    }

    protected static EnumBlockRenderType safeRenderType(IBlockState state) {
        return PipelineBlockRenderProperties.renderType(state);
    }

    protected static BlockRenderLayer safeRenderLayer(IBlockState state) {
        return PipelineBlockRenderProperties.renderLayer(state);
    }

    protected static int safeLightValue(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return PipelineBlockRenderProperties.lightValue(state, blockAccess, pos);
    }

    protected static boolean safeOpaqueCube(IBlockState state) {
        return PipelineBlockRenderProperties.opaqueCube(state);
    }

    protected static boolean safeFullCube(IBlockState state) {
        return PipelineBlockRenderProperties.fullCube(state);
    }

    protected static boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return PipelineBlockRenderProperties.canRenderInLayer(state, layer);
    }

    protected static int blockMetadata(IBlockState state) {
        return PipelineBlockRenderProperties.metadata(state);
    }

    protected static int intrinsicBlockEmission(IBlockState state) {
        return PipelineBlockEmission.intrinsicEmission(state);
    }

    protected static int astralCrystalEmission(IBlockState state) {
        return PipelineBlockEmission.astralCrystalEmission(state);
    }

    protected static boolean isAstralCrystalCluster(IBlockState state) {
        return PipelineBlockEmission.isAstralCrystalCluster(state);
    }

    protected static int astralCrystalVoxelId(IBlockState state) {
        return PipelineRuntimeState.localActVoxelId(PipelineBlockEmission.astralCrystalMaterialId(state));
    }

    protected static boolean containsIgnoreCase(String value, String needle) {
        return PipelineBlockEmission.containsIgnoreCase(value, needle);
    }

    protected static int clampLightValue(int value) {
        return Math.clamp(value, 0, 15);
    }

    public void recordSyntheticLightCandidate(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (self().isBetterPortalsExternalWorldTarget()) {
            return;
        }
        if (!self().canTrackSyntheticLights() || state == null || blockAccess == null) {
            syntheticLightCandidates.remove(MinecraftReflectionCompat.blockPosToLong(pos));
            return;
        }
        SyntheticLightInfo lightInfo = self().syntheticLightInfo(state, blockAccess, pos);
        if (lightInfo.voxelId <= 0 || lightInfo.emission <= 0) {
            if (self().recordProjectRedSyntheticLightCandidate(blockAccess, pos, "block_render_te")) {
                return;
            }
            return;
        }
        self().putSyntheticLightCandidate(pos, false);
    }

    public void refreshSyntheticLightCandidate(BlockPos pos) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        self().refreshSyntheticLightCandidate(mc != null ? MinecraftReflectionCompat.world(mc) : null, pos);
    }

    public void refreshSyntheticLightCandidate(World world, BlockPos pos) {
        if (pos == null) {
            return;
        }
        long key = MinecraftReflectionCompat.blockPosToLong(pos);
        syntheticLightCandidates.remove(key);
        if (!self().canTrackSyntheticLights() || world == null || !MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
            return;
        }
        IBlockState state;
        try {
            state = MinecraftReflectionCompat.worldBlockState(world, pos);
        } catch (RuntimeException ignored) {
            return;
        }
        SyntheticLightInfo lightInfo = self().syntheticLightInfo(state, world, pos);
        if (lightInfo.voxelId > 0 && lightInfo.emission > 0) {
            self().putSyntheticLightCandidate(pos, true);
        }
        if (self().shouldProbeColoredLightTileEntity(state, lightInfo)) {
            self().auditProjectRedTileEntity(world, pos, "world_update_te");
        }
    }

    public void refreshSyntheticLightCandidates(World world, BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return;
        }
        int minX = Math.min(MinecraftReflectionCompat.blockPosX(from), MinecraftReflectionCompat.blockPosX(to)) - 1;
        int minY = Math.max(0, Math.min(MinecraftReflectionCompat.blockPosY(from), MinecraftReflectionCompat.blockPosY(to)) - 1);
        int minZ = Math.min(MinecraftReflectionCompat.blockPosZ(from), MinecraftReflectionCompat.blockPosZ(to)) - 1;
        int maxX = Math.max(MinecraftReflectionCompat.blockPosX(from), MinecraftReflectionCompat.blockPosX(to)) + 1;
        int maxY = Math.min(255, Math.max(MinecraftReflectionCompat.blockPosY(from), MinecraftReflectionCompat.blockPosY(to)) + 1);
        int maxZ = Math.max(MinecraftReflectionCompat.blockPosZ(from), MinecraftReflectionCompat.blockPosZ(to)) + 1;
        long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);
        if (volume > MAX_SYNTHETIC_LIGHT_RANGE_REFRESH_VOLUME) {
            self().removeSyntheticLightCandidatesInRange(minX, minY, minZ, maxX, maxY, maxZ);
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    self().refreshSyntheticLightCandidate(world, new BlockPos(x, y, z));
                }
            }
        }
    }

    public void removeSyntheticLightCandidate(BlockPos pos) {
        if (pos != null) {
            syntheticLightCandidates.remove(MinecraftReflectionCompat.blockPosToLong(pos));
        }
    }

    protected boolean canTrackSyntheticLights() {
        return ENABLE_CPU_LIGHT_INJECTION
                && ENABLE_GENERIC_CPU_SHADER_BLOCK_LIGHT_INJECTION
                && isPipelineActive
                && shaderImages.active()
                && !shaderProperties.blockIds().isEmpty();
    }

    protected int syntheticLightVoxelId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().syntheticLightInfo(state, blockAccess, pos).voxelId;
    }

    protected SyntheticLightInfo syntheticLightInfo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null || blockAccess == null || pos == null) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "missing_input");
        }
        if (shaderProperties.blockIds().isEmpty()) {
            return new SyntheticLightInfo(state, state, 0, 0, 0, "no_block_ids");
        }
        IBlockState actualState = self().actualLightState(state, blockAccess, pos);
        int shaderBlockId = shaderProperties.blockIds().idFor(actualState);
        int voxelId = PipelineRuntimeState.localActVoxelId(shaderBlockId);
        if (voxelId <= 0) {
            voxelId = PipelineRuntimeState.compatSyntheticLightVoxelId(actualState);
        }
        int emission = self().blockRenderEmissionForState(actualState, null, null);
        if (voxelId <= 0) {
            return new SyntheticLightInfo(state, actualState, shaderBlockId, 0, emission, "no_colored_voxel_mapping");
        }
        if (emission <= 0) {
            // A positive ACT voxel mapping is itself an explicit declaration
            // that this material is a colored emitter. Several 1.12 mod blocks
            // (Aether portals and texture-driven luminous blocks in particular)
            // report zero through vanilla getLightValue even though the shader
            // material computes emission from its texture. Emission is only an
            // eligibility gate here; intensity/color remain shader-owned.
            emission = 1;
            return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "ok:mapped_emission_fallback");
        }
        return new SyntheticLightInfo(state, actualState, shaderBlockId, voxelId, emission, "ok");
    }

    protected void putSyntheticLightCandidate(BlockPos pos, boolean force) {
        long key = MinecraftReflectionCompat.blockPosToLong(pos);
        if (syntheticLightCandidates.size() >= MAX_SYNTHETIC_LIGHT_CANDIDATES
                && !syntheticLightCandidates.containsKey(key)) {
            if (!force) {
                return;
            }
            for (Long staleKey : syntheticLightCandidates.keySet()) {
                syntheticLightCandidates.remove(staleKey);
                break;
            }
        }
        syntheticLightCandidates.put(key, MinecraftReflectionCompat.blockPosToImmutable(pos));
    }

    protected void removeSyntheticLightCandidatesInRange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (Map.Entry<Long, BlockPos> entry : syntheticLightCandidates.entrySet()) {
            BlockPos pos = entry.getValue();
            if (pos == null) {
                syntheticLightCandidates.remove(entry.getKey());
                continue;
            }
            if (MinecraftReflectionCompat.blockPosX(pos) >= minX && MinecraftReflectionCompat.blockPosX(pos) <= maxX
                    && MinecraftReflectionCompat.blockPosY(pos) >= minY && MinecraftReflectionCompat.blockPosY(pos) <= maxY
                    && MinecraftReflectionCompat.blockPosZ(pos) >= minZ && MinecraftReflectionCompat.blockPosZ(pos) <= maxZ) {
                syntheticLightCandidates.remove(entry.getKey(), pos);
            }
        }
    }

    protected void logDecoratedLightEmission(IBlockState originalState, IBlockState decoratedState,
                                             IBlockAccess blockAccess, BlockPos pos, int emission) {
        if (MAX_DECORATED_LIGHT_AUDIT_LOGS <= 0) {
            return;
        }
        String key = self().safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + PipelineRuntimeState.formatBlockPos(pos)
                + "|" + PipelineRuntimeState.stateName(originalState)
                + "|" + PipelineRuntimeState.stateName(decoratedState)
                + "|" + emission;
        if (!decoratedLightAuditKeys.add(key)) {
            return;
        }

        int count = decoratedLightAuditCount.incrementAndGet();
        if (count > MAX_DECORATED_LIGHT_AUDIT_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMDecoratedLight] call={} pos={} state={} decorated={} emission={} access={}",
                count,
                PipelineRuntimeState.formatBlockPos(pos),
                PipelineRuntimeState.stateName(originalState),
                PipelineRuntimeState.stateName(decoratedState),
                emission,
                blockAccess != null ? blockAccess.getClass().getName() : "null"
        );
    }

    protected void auditProjectRedTileEntity(World world, BlockPos pos, String result) {
        if (world == null || pos == null) {
            return;
        }
        TileEntity tileEntity;
        try {
            tileEntity = MinecraftReflectionCompat.call(world, TileEntity.class, null, new String[]{"func_175625_s", "getTileEntity"},
                    new Class<?>[]{BlockPos.class}, pos);
        } catch (RuntimeException ignored) {
            return;
        }
        if (tileEntity == null) {
            return;
        }
        int[] voxelIds = new int[8];
        int count = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, voxelIds);
        if (count > 0) {
            self().putSyntheticLightCandidate(pos, true);
        }
    }

    protected boolean recordProjectRedSyntheticLightCandidate(IBlockAccess blockAccess, BlockPos pos, String result) {
        TileEntity tileEntity = self().tileEntityAt(blockAccess, pos);
        if (tileEntity == null) {
            return false;
        }

        int[] voxelIds = new int[8];
        int count = ProjectRedIlluminationCompat.collectVoxelIds(tileEntity, voxelIds);
        if (count <= 0) {
            return false;
        }

        self().putSyntheticLightCandidate(pos, true);
        return true;
    }

    protected TileEntity tileEntityAt(IBlockAccess blockAccess, BlockPos pos) {
        if (blockAccess == null || pos == null) {
            return null;
        }
        try {
            return MinecraftReflectionCompat.blockAccessTileEntity(blockAccess, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    protected boolean shouldProbeColoredLightTileEntity(IBlockState state, SyntheticLightInfo lightInfo) {
        return self().isProjectRedTileHost(state)
                || lightInfo != null && self().isProjectRedTileHost(lightInfo.originalState)
                || lightInfo != null && self().isProjectRedTileHost(lightInfo.actualState);
    }

    protected boolean isProjectRedTileHost(IBlockState state) {
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        return name != null
                && (("projectred-illumination".equals(MinecraftReflectionCompat.resourceNamespace(name)))
                || ("forgemultipartcbe".equals(MinecraftReflectionCompat.resourceNamespace(name)) && "multipart_block".equals(MinecraftReflectionCompat.resourcePath(name))));
    }

    protected static ResourceLocation registryName(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = MinecraftReflectionCompat.blockFromState(state);
        return block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    protected static String stateName(IBlockState state) {
        return MinecraftReflectionCompat.stateString(state);
    }

    protected static String formatBlockPos(BlockPos pos) {
        return pos != null ? MinecraftReflectionCompat.blockPosX(pos) + "," + MinecraftReflectionCompat.blockPosY(pos) + "," + MinecraftReflectionCompat.blockPosZ(pos) : "null";
    }

    protected static String formatVoxelIds(int[] voxelIds, int count) {
        if (voxelIds == null || count <= 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(count, voxelIds.length);
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(voxelIds[i]);
        }
        return builder.append(']').toString();
    }

    protected IBlockState actualLightState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return state == null || blockAccess == null || pos == null
                ? state
                : MinecraftReflectionCompat.actualState(state, blockAccess, pos);
    }

    protected static boolean isBlockcrafteryEditableBlock(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return false;
        }
        // Blockcraftery ships its editable hosts under this stable, unobfuscated
        // class prefix.  Class names are immutable and avoid a registry lookup
        // for every unrelated terrain state during compilation.
        return block.getClass().getName().startsWith("epicsquid.blockcraftery.block.BlockEditable");
    }

    protected static boolean isArchitectureCraftShapeBlock(IBlockState state) {
        Block block = MinecraftReflectionCompat.blockFromState(state);
        if (block == null) {
            return false;
        }
        ResourceLocation name = MinecraftReflectionCompat.blockRegistryName(block);
        return "architecturecraft".equalsIgnoreCase(
                MinecraftReflectionCompat.resourceNamespace(name))
                && "com.elytradev.architecture.common.block.BlockShape".equals(block.getClass().getName());
    }

    public boolean shouldSeparateBlockAo(IBlockState state) {
        if (!self().shouldSeparateAo() || state == null) {
            return false;
        }

        Block block = MinecraftReflectionCompat.blockFromState(state);
        return block != null
                && MinecraftReflectionCompat.blockRenderLayer(block) == BlockRenderLayer.SOLID;
    }

    public boolean shouldSeparateBlockAo(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().shouldSeparateBlockAo(self().actualLightState(state, blockAccess, pos));
    }

    public boolean shouldSeparateAo() {
        return isPipelineActive && shaderProperties.renderSettings().separateAo();
    }

    public boolean shouldSeparateEntityDraws() {
        return isPipelineActive && shaderProperties.renderSettings().separateEntityDraws();
    }

    public float ambientOcclusionLevel() {
        return isPipelineActive ? shaderProperties.renderSettings().ambientOcclusionLevel() : 1.0f;
    }

    public boolean shouldDisableDirectionalShading() {
        return isPipelineActive && !shaderProperties.renderSettings().oldLighting();
    }

    public boolean shouldRenderWeather() {
        if (isPipelineActive && ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            return false;
        }
        return !isPipelineActive || !self().shouldSkipAllMainGbufferRendering() && shaderProperties.renderSettings().weather();
    }

    public boolean shouldRenderWeatherParticles() {
        if (isPipelineActive && ENABLE_SAFE_TERRAIN_FALLBACKS && hardwareSafeVanillaTerrain) {
            return false;
        }
        return !isPipelineActive || shaderProperties.renderSettings().weatherParticles();
    }

    public boolean shouldRenderVignette() {
        return !isPipelineActive || shaderProperties.renderSettings().vignette();
    }

    public boolean shouldRenderUnderwaterOverlay() {
        if (!isPipelineActive) {
            return true;
        }
        return shaderProperties.renderSettings().underwaterOverlay()
                || PipelineRuntimeState.eyeFluidState(MinecraftReflectionCompat.minecraft()) == 1;
    }
}
