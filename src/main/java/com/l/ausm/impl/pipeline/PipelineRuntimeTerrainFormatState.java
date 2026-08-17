package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.compat.BlockcrafteryContainedStateCompat;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.pack.ShaderBlockIdMap;
import com.l.ausm.impl.pipeline.pack.ShaderPackDirectives;
import com.l.ausm.impl.pipeline.pack.ShaderPipelineCapabilities;
import com.l.ausm.impl.pipeline.shader.PipelineProgram;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

import static com.l.ausm.impl.pipeline.PipelineGlState.maxDrawBuffers;
import static com.l.ausm.impl.pipeline.PipelineGlState.safeGetInteger;
import static com.l.ausm.impl.pipeline.PipelineGlState.safeGetString;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.DEBUG_PROBES_ENABLED;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_BLOCKCRAFTERY_TRANSPARENCY_PROBES;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_FLUID_MATERIAL_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_HARDWARE_CAPABILITY_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_LILY_PAD_ROUTE_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.pack.PipelineShaderSettings.optionValue;

abstract class PipelineRuntimeTerrainFormatState extends PipelineRuntimeFramebufferState {
    protected void clearHardwareSafeVanillaTerrainAfterSuccessfulProgramLoad(String stage) {
        if (!isPipelineActive || !self().pipelineTerrainFormatSupported() || !self().hasUsableShaderTerrainProgram()) {
            return;
        }
        boolean changed = hardwareSafeVanillaTerrain
                || !hardwareSafeVanillaTerrainReason.isEmpty()
                || hardwareSafeVanillaTerrainRefreshCooldown > 0
                || zeroOpaqueTerrainFrames != 0
                || sparseOpaqueTerrainFrames != 0
                || zeroOpaqueTerrainRecoveryRequested
                || softVanillaTerrainRenderer
                || !softVanillaTerrainRendererReason.isEmpty()
                || shaderedNothiriumGlobalBypass
                || !shaderedNothiriumGlobalBypassReason.isEmpty()
                || nothiriumHybridVanillaMaintenanceFrames != 0
                || !nothiriumHybridVanillaMaintenanceReason.isEmpty()
                || nothiriumMainVanillaDrawPathFrames != 0
                || !nothiriumMainVanillaDrawPathReason.isEmpty();
        hardwareSafeVanillaTerrain = false;
        hardwareSafeVanillaTerrainReason = "";
        softVanillaTerrainRenderer = false;
        softVanillaTerrainRendererReason = "";
        shaderedNothiriumGlobalBypass = false;
        shaderedNothiriumGlobalBypassReason = "";
        shaderedNothiriumGlobalBypassPrimedWorld = null;
        shaderedNothiriumGlobalBypassPrimedRenderGlobal = null;
        positiveVanillaTerrainProbeLogs = 0;
        positiveNothiriumTerrainProbeLogs = 0;
        terrainGridProbeLogs = 0;
        nothiriumHybridVanillaMaintenanceFrames = 0;
        nothiriumHybridVanillaMaintenanceReason = "";
        nothiriumMainVanillaDrawPathFrames = 0;
        nothiriumMainVanillaDrawPathReason = "";
        hardwareSafeVanillaTerrainRefreshCooldown = 0;
        zeroOpaqueTerrainFrames = 0;
        sparseOpaqueTerrainFrames = 0;
        zeroOpaqueTerrainRecoveryRequested = false;
        if (changed) {
            NothiriumBypass.markAllChanged();
            self().scheduleWorldTerrainRefresh(true, true, 0);
            MainMod.LOGGER.info("[Pipeline] Cleared hardware safe vanilla terrain fallback after loading shader terrain programs: {}", stage);
        }
    }

    protected boolean hasUsableShaderTerrainProgram() {
        return self().hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_SOLID)
                || self().hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_CUTOUT)
                || self().hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP)
                || self().hasUsableShaderProgram(RenderPass.GBUFFERS_TERRAIN)
                || self().hasUsableShaderProgram(RenderPass.GBUFFERS_TEXTURED_LIT)
                || self().hasUsableShaderProgram(RenderPass.GBUFFERS_TEXTURED);
    }

    protected boolean hasUsableShaderProgram(RenderPass pass) {
        PipelineProgram program = programs.get(pass);
        return program != null && program.effectiveProgram(programs) != null;
    }

    protected boolean detectPipelineTerrainFormatSupport() {
        if (ExtendedVertexFormats.PIPELINE_BLOCK == null) {
            ExtendedVertexFormats.initialize();
        }
        return ExtendedVertexFormats.PIPELINE_BLOCK != null
                && safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS) > ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE;
    }

    protected boolean pipelineTerrainFormatSupported() {
        if (!pipelineTerrainFormatSupported) {
            pipelineTerrainFormatSupported = self().detectPipelineTerrainFormatSupport();
        }
        return pipelineTerrainFormatSupported;
    }

    protected void logHardwareCapabilities(String stage, ShaderPackDirectives directives) {
        if (hardwareCapabilityLogs >= MAX_HARDWARE_CAPABILITY_LOGS) {
            return;
        }
        hardwareCapabilityLogs++;

        ContextCapabilities caps = GLContext.getCapabilities();
        int maxVertexAttribs = safeGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        int maxDrawBuffers = caps.OpenGL20 ? safeGetInteger(GL20.GL_MAX_DRAW_BUFFERS) : 1;
        int maxColorAttachments = caps.OpenGL30 ? safeGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS) : 1;
        int maxTextureUnits = caps.OpenGL20 ? safeGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS) : safeGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
        int maxImageUnits = caps.OpenGL42 ? safeGetInteger(GL42.GL_MAX_IMAGE_UNITS) : 0;
        int maxSsboBindings = caps.OpenGL43 ? safeGetInteger(GL43.GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS) : 0;
        ShaderPipelineCapabilities requested = directives != null ? directives.capabilities() : null;
        boolean requestedCompute = requested != null && requested.compute();
        boolean requestedImages = requested != null && requested.images();
        boolean requestedSsbo = requested != null && requested.storageBuffers();
        boolean requestedGeometry = requested != null && requested.geometry();
        boolean requestedTessellation = requested != null && requested.tessellation();

        MainMod.LOGGER.info(
                "[AUSMHardware] stage={} vendor='{}' renderer='{}' version='{}' gl20={} gl30={} gl32={} gl40={} gl42={} gl43={} arbCompute={} arbImages={} arbSsbo={} arbDrawBuffersBlend={} arbTessellation={} fboEnabled={} maxAttribs={} maxDrawBuffers={} maxColorAttachments={} maxTextureUnits={} maxImageUnits={} maxSsboBindings={} requiredAttribs={} requestedCompute={} requestedImages={} requestedSsbo={} requestedGeometry={} requestedTessellation={}",
                stage,
                safeGetString(GL11.GL_VENDOR),
                safeGetString(GL11.GL_RENDERER),
                safeGetString(GL11.GL_VERSION),
                caps.OpenGL20,
                caps.OpenGL30,
                caps.OpenGL32,
                caps.OpenGL40,
                caps.OpenGL42,
                caps.OpenGL43,
                caps.GL_ARB_compute_shader,
                caps.GL_ARB_shader_image_load_store,
                caps.GL_ARB_shader_storage_buffer_object,
                caps.GL_ARB_draw_buffers_blend,
                caps.GL_ARB_tessellation_shader,
                MinecraftReflectionCompat.isFramebufferEnabled(),
                maxVertexAttribs,
                maxDrawBuffers,
                maxColorAttachments,
                maxTextureUnits,
                maxImageUnits,
                maxSsboBindings,
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE + 1,
                requestedCompute,
                requestedImages,
                requestedSsbo,
                requestedGeometry,
                requestedTessellation
        );

        if (maxVertexAttribs <= ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE) {
            MainMod.LOGGER.warn(
                    "[AUSMHardware] GPU exposes only {} vertex attribs; pipeline terrain metadata needs attribute index {}. Nothirium shader terrain will be bypassed if terrain fails.",
                    maxVertexAttribs,
                    ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE
            );
        }
        if (requestedCompute && !caps.OpenGL43 && !caps.GL_ARB_compute_shader) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests compute programs, but OpenGL 4.3 is unavailable.");
        }
        if (requestedImages && !caps.OpenGL42 && !caps.GL_ARB_shader_image_load_store) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests custom image load/store, but OpenGL 4.2 is unavailable.");
        }
        if (requestedSsbo && !caps.OpenGL43 && !caps.GL_ARB_shader_storage_buffer_object) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests SSBOs, but OpenGL 4.3 is unavailable.");
        }
        if (requestedGeometry && !caps.OpenGL32) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests geometry shaders, but OpenGL 3.2 is unavailable.");
        }
        if (requestedTessellation && !caps.OpenGL40 && !caps.GL_ARB_tessellation_shader) {
            MainMod.LOGGER.warn("[AUSMHardware] Shaderpack requests tessellation shaders, but OpenGL 4.0 is unavailable.");
        }
    }

    public int blockEntityId(IBlockState state) {
        return self().blockEntityId(state, null, null);
    }

    public int blockEntityId(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return 0;
        }

        return self().blockEntityIdForActualState(self().actualLightState(state, blockAccess, pos), blockAccess, pos);
    }

    public int blockEntityIdForActualState(IBlockState pipelineState, IBlockAccess blockAccess, BlockPos pos) {
        if (pipelineState == null) {
            return 0;
        }

        ShaderBlockIdMap.BlockIdRules blockIds = shaderProperties.blockIds();
        int mappedId = 0;
        if (!blockIds.isEmpty()) {
            mappedId = blockIds.idFor(pipelineState);
            if (mappedId != 0) {
                self().logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, mappedId, "mapped");
                self().logLilyPadRouteProbe(pipelineState, blockAccess, pos, mappedId, "mapped");
                return mappedId;
            }
        }

        int waterLikeFallbackId = PipelineRuntimeState.waterLikeFluidFallbackId(pipelineState);
        if (waterLikeFallbackId != 0) {
            self().logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, waterLikeFallbackId, "water-like-fallback");
            self().logLilyPadRouteProbe(pipelineState, blockAccess, pos, waterLikeFallbackId, "water-like-fallback");
            return waterLikeFallbackId;
        }

        self().logWaterLikeMaterialProbe(pipelineState, blockAccess, pos, 0, "unmapped");
        return 0;
    }

    protected void resetFramedMaterialProbes() {
        blockcrafteryRouteProbeCount.set(0);
        framedBloomQuadGateProbeCount.set(0);
        framedQuadMaterialProbeKeys.clear();
    }

    public int customLiquidTintColor(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return -1;
    }

    protected void logWaterLikeMaterialProbe(IBlockState state, IBlockAccess blockAccess, BlockPos pos, int id, String source) {
        if (state == null || !MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            return;
        }

        int call = waterLikeMaterialProbeCount.incrementAndGet();
        if (call > MAX_FLUID_MATERIAL_PROBE_LOGS) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMFluidMaterialProbe] call={} source={} id={} registry={} state={} pos={} access={}",
                call,
                source,
                id,
                PipelineRuntimeState.registryName(state),
                state,
                pos,
                blockAccess != null ? blockAccess.getClass().getName() : "null"
        );
    }

    protected void logLilyPadRouteProbe(IBlockState state, IBlockAccess blockAccess, BlockPos pos, int id, String source) {
        if (state == null || id != 10489) {
            return;
        }

        if (pos != null) {
            lilyPadShadowProbeChunks.add(PipelineRuntimeState.lilyPadShadowProbeChunkKey(
                    MinecraftReflectionCompat.blockPosX(pos),
                    MinecraftReflectionCompat.blockPosY(pos),
                    MinecraftReflectionCompat.blockPosZ(pos)
            ));
        }
        self().forensicTrace("lily-material-route", "source=" + source + ", id=" + id + ", pos=" + pos
                + ", layer=" + PipelineRuntimeState.safeRenderLayer(state) + ", renderType=" + PipelineRuntimeState.safeRenderType(state));

        int call = lilyPadRouteProbeCount.incrementAndGet();
        if (call > MAX_LILY_PAD_ROUTE_PROBE_LOGS) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMLilyPadRouteProbe] call={} source={} id={} registry={} state={} pos={} layer={} renderType={} material={} light={} emission={} wavingLilyPad={} active={} pass={} phase={}",
                call,
                source,
                id,
                PipelineRuntimeState.registryName(state),
                state,
                pos,
                PipelineRuntimeState.safeRenderLayer(state),
                PipelineRuntimeState.safeRenderType(state),
                MinecraftReflectionCompat.stateMaterial(state),
                PipelineRuntimeState.safeLightValue(state, blockAccess, pos),
                self().blockRenderEmissionForState(state, blockAccess, pos),
                optionValue(shaderProperties, "WAVING_LILY_PAD"),
                isPipelineActive,
                activePass,
                self().getPhase()
        );
    }

    public boolean isKnownLilyPadShadowProbeChunk(int chunkX, int chunkY, int chunkZ) {
        // Nothirium exposes section origins (already in block units), whereas
        // the material route records a BlockPos. Do not shift the section
        // origin a second time or the exact shadow VBO probe can never match.
        return lilyPadShadowProbeChunks.contains(PipelineRuntimeState.lilyPadShadowProbeChunkOriginKey(chunkX, chunkY, chunkZ));
    }

    protected static long lilyPadShadowProbeChunkKey(int blockX, int blockY, int blockZ) {
        return PipelineRuntimeState.lilyPadShadowProbeChunkOriginKey(blockX >> 4 << 4, blockY >> 4 << 4, blockZ >> 4 << 4);
    }

    protected static long lilyPadShadowProbeChunkOriginKey(int chunkOriginX, int chunkOriginY, int chunkOriginZ) {
        long x = (long) (chunkOriginX >> 4) & 0x3FFFFFL;
        long y = (long) (chunkOriginY >> 4) & 0xFFFFFL;
        long z = (long) (chunkOriginZ >> 4) & 0x3FFFFFL;
        return x << 42 | y << 22 | z;
    }

    protected static int waterLikeFluidFallbackId(IBlockState state) {
        if (state == null || !MinecraftReflectionCompat.stateIsLiquidOrWater(state)) {
            return 0;
        }

        ResourceLocation name = PipelineRuntimeState.registryName(state);
        if (name == null) {
            return 0;
        }

        String namespace = MinecraftReflectionCompat.resourceNamespace(name);
        String path = MinecraftReflectionCompat.resourcePath(name);
        if ("minecraft".equals(namespace)) {
            return ("water".equals(path) || "flowing_water".equals(path)) ? 32000 : 0;
        }
        if ("actuallyadditions".equals(namespace)) return 32621;
        if ("buildcraftenergy".equals(namespace) || "buildcraftfactory".equals(namespace)) return 32620;
        if ("enderio".equals(namespace)) return 32622;
        if ("cyclicmagic".equals(namespace)) return 32623;
        if ("immersiveengineering".equals(namespace) || "immersivepetroleum".equals(namespace)) return 32624;
        if ("gendustry".equals(namespace) || "binniecore".equals(namespace) || "binnie-mods".equals(namespace))
            return 32625;
        if ("advancedrocketry".equals(namespace)) return 32626;
        if ("abyssalcraft".equals(namespace) || "acintegration".equals(namespace)) return 32627;
        if ("bloodmagic".equals(namespace) || "bloodarsenal".equals(namespace)) return 32628;
        if ("erebus".equals(namespace)) return 32629;
        if ("thaumcraft".equals(namespace)) return 32630;
        if ("thebetweenlands".equals(namespace)) return 32631;
        if ("thermalfoundation".equals(namespace)) return 32632;
        if ("tconstruct".equals(namespace) || "plustic".equals(namespace) || "iceandfire".equals(namespace))
            return 32633;
        if ("biomesoplenty".equals(namespace)) return 32634;
        if ("forestry".equals(namespace)) return 32635;
        if ("industrialforegoing".equals(namespace)) return 32636;
        if ("railcraft".equals(namespace)) return 32637;
        if ("bigreactors".equals(namespace)) return 32638;
        if ("hatchery".equals(namespace)) return 32639;
        if ("extrabotany".equals(namespace) || ("botania".equals(namespace) && path.contains("mana"))) return 32641;
        if ("integrateddynamics".equals(namespace)) return 32642;
        if ("astralsorcery".equals(namespace)) return 32643;
        if ("animus".equals(namespace)) return 32644;
        return 32645;
    }

    public int blockMetadata(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return PipelineRuntimeState.blockMetadata(self().actualLightState(state, blockAccess, pos));
    }

    public int blockMetadataForActualState(IBlockState actualState) {
        return PipelineRuntimeState.blockMetadata(actualState);
    }

    public IBlockState actualBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().actualLightState(state, blockAccess, pos);
    }

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().effectiveBlockRenderState(state, self().actualLightState(state, blockAccess, pos), blockAccess, pos);
    }

    public IBlockState effectiveBlockRenderState(IBlockState state, IBlockState actualState,
                                                 IBlockAccess blockAccess, BlockPos pos) {
        IBlockState inherited = self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        if (inherited != null) {
            return inherited;
        }
        return actualState;
    }

    public IBlockState inheritedBlockcrafteryRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return BlockcrafteryContainedStateCompat.containedState(state, blockAccess, pos);
    }

    /**
     * Filled Blockcraftery frames are compiled as the contained block itself.
     * This is deliberately independent of every GPOM visual/material field.
     */
    public boolean shouldReplaceFilledBlockcrafteryFrame(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().inheritedBlockcrafteryRenderState(state, blockAccess, pos) != null;
    }

    public boolean shouldProbeBlockcrafteryTransparency(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (!PipelineRuntimeState.isBlockcrafteryEditableBlock(state) || blockAccess == null || pos == null) {
            return false;
        }
        return self().containedFrameEmission(state, blockAccess, pos) > 0
                || self().containedFrameHasBloom(state, blockAccess, pos);
    }

    public void logBlockcrafteryTransparencyProbe(String source, IBlockState state, IBlockAccess blockAccess,
                                                  BlockPos pos, BlockRenderLayer layer, Integer startVertex,
                                                  Integer endVertex, Boolean result, String detail) {
        if (!self().shouldProbeBlockcrafteryTransparency(state, blockAccess, pos)) {
            return;
        }
        IBlockState decoratedState = self().inheritedBlockcrafteryRenderState(state, blockAccess, pos);
        int dimension = self().safeDimensionId(blockAccess instanceof World world ? world : null);
        int start = startVertex != null ? startVertex : -1;
        int end = endVertex != null ? endVertex : -1;
        int delta = start >= 0 && end >= 0 ? end - start : -1;
        String key = source
                + "|" + dimension
                + "|" + PipelineRuntimeState.formatBlockPos(pos)
                + "|" + PipelineRuntimeState.stateName(state)
                + "|" + PipelineRuntimeState.stateName(decoratedState)
                + "|" + String.valueOf(layer)
                + "|" + String.valueOf(result)
                + "|" + start
                + "|" + end
                + "|" + String.valueOf(detail);
        if (!blockcrafteryTransparencyProbeKeys.add(key)) {
            return;
        }
        int count = blockcrafteryTransparencyProbeCount.incrementAndGet();
        if (count > MAX_BLOCKCRAFTERY_TRANSPARENCY_PROBES) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBlockcrafteryTransparencyProbe] call={} source={} pipelineActive={} shaderless={} phase={} dim={} pos={} layer={} result={} start={} end={} delta={} state={} decorated={} decoratedInfo={} detail={}",
                count,
                source,
                isPipelineActive,
                !isPipelineActive,
                self().getPhase(),
                dimension,
                PipelineRuntimeState.formatBlockPos(pos),
                layer,
                result,
                start,
                end,
                delta,
                PipelineRuntimeState.stateName(state),
                PipelineRuntimeState.stateName(decoratedState),
                self().blockcrafteryTransparencyStateInfo(decoratedState, blockAccess, pos, layer),
                detail
        );
    }

    protected boolean isBlockcrafteryTransparencyProbeDecoratedState(IBlockState state) {
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null || PipelineRuntimeState.isBlockcrafteryEditableBlock(state)) {
            return false;
        }
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        String namespace = name != null && MinecraftReflectionCompat.resourceNamespace(name) != null
                ? MinecraftReflectionCompat.resourceNamespace(name).toLowerCase(Locale.ROOT)
                : "";
        String path = MinecraftReflectionCompat.resourcePathLower(name);
        String blockClass = MinecraftReflectionCompat.blockFromState(state).getClass().getName().toLowerCase(Locale.ROOT);
        BlockRenderLayer naturalLayer = PipelineRuntimeState.safeRenderLayer(state);
        boolean transparentLayer = naturalLayer == BlockRenderLayer.TRANSLUCENT
                || naturalLayer == BlockRenderLayer.CUTOUT
                || naturalLayer == BlockRenderLayer.CUTOUT_MIPPED
                || PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                || PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                || PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED);
        boolean transparentIdentity = namespace.contains("enderio")
                || path.contains("glass")
                || path.contains("clear")
                || path.contains("fused")
                || path.contains("quartz")
                || path.contains("transparent")
                || path.contains("translucent")
                || blockClass.contains("glass")
                || blockClass.contains("transparent")
                || blockClass.contains("translucent");
        return transparentLayer || transparentIdentity;
    }

    protected String blockcrafteryTransparencyStateInfo(IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                                        BlockRenderLayer currentLayer) {
        if (state == null) {
            return "null";
        }
        Block block = MinecraftReflectionCompat.blockFromState(state);
        return "{renderType=" + PipelineRuntimeState.safeRenderType(state)
                + ", naturalLayer=" + PipelineRuntimeState.safeRenderLayer(state)
                + ", canCurrent=" + PipelineRuntimeState.canRenderInLayer(state, currentLayer)
                + ", canSolid=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.SOLID)
                + ", canCutoutMipped=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT_MIPPED)
                + ", canCutout=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.CUTOUT)
                + ", canTranslucent=" + PipelineRuntimeState.canRenderInLayer(state, BlockRenderLayer.TRANSLUCENT)
                + ", opaque=" + PipelineRuntimeState.safeOpaqueCube(state)
                + ", full=" + PipelineRuntimeState.safeFullCube(state)
                + ", material=" + (MinecraftReflectionCompat.stateMaterial(state) != null ? MinecraftReflectionCompat.stateMaterial(state) : "null")
                + ", light=" + PipelineRuntimeState.safeLightValue(state, blockAccess, pos)
                + ", class=" + (block != null ? block.getClass().getName() : "null")
                + "}";
    }

    public IBlockState inheritedBloomRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        if (state == null) {
            return null;
        }

        IBlockState inheritedState = self().inheritedRenderState(state, blockAccess, pos);
        if (inheritedState != null) {
            return inheritedState;
        }
        return self().actualLightState(state, blockAccess, pos);
    }

    public IBlockState firstInheritedRenderState(IBlockState state, IBlockAccess blockAccess, BlockPos pos) {
        return self().inheritedRenderState(state, blockAccess, pos);
    }

    public IBlockState inheritedBloomGeometryRenderState(IBlockState state, IBlockState inheritedState) {
        return inheritedState != null ? inheritedState : state;
    }

    public boolean isFramedBlockDiagnosticTarget(IBlockState state) {
        return PipelineRuntimeState.isBlockcrafteryEditableBlock(state);
    }

    public boolean framedBlockDiagnosticsEnabled() {
        return false;
    }

    public boolean currentProblemProbesEnabled() {
        return CURRENT_PROBLEM_PROBES_ENABLED;
    }

    protected static boolean debugProbeLoggingEnabled() {
        return DEBUG_PROBES_ENABLED;
    }

    public boolean isBlockcrafteryEditableState(IBlockState state) {
        return PipelineRuntimeState.isBlockcrafteryEditableBlock(state);
    }
}
