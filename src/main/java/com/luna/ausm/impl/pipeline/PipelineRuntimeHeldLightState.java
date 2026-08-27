package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderKey;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineGlState.setIndexedBlend;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_LOCAL_PLAYER_ENTITY_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_PIPELINE_PASS_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CHUNK_FADE_DURATION_SECONDS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CHUNK_FADE_STALE_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.CHUNK_FADE_WARMUP_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.ENABLE_CHUNK_FADE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.MAX_CHUNK_FADE_STATES;

abstract class PipelineRuntimeDiagnosticsState6 extends PipelineRuntimeDiagnosticsState5 {
    protected ItemStack heldMainStack(Minecraft mc) {
        EntityLivingBase player = MinecraftReflectionCompat.player(mc);
        if (player == null) {
            return null;
        }

        ItemStack mainHand = MinecraftReflectionCompat.heldItemMainhand(player);
        if (!shaderProperties.renderSettings().oldHandLight()) {
            return mainHand;
        }

        ItemStack offHand = MinecraftReflectionCompat.heldItemOffhand(player);
        return self().heldBlockLightValue(offHand) > self().heldBlockLightValue(mainHand) ? offHand : mainHand;
    }

    protected ItemStack heldOffhandStack(Minecraft mc) {
        EntityLivingBase player = MinecraftReflectionCompat.player(mc);
        return player != null ? MinecraftReflectionCompat.heldItemOffhand(player) : null;
    }

    protected int heldBlockLightValue(ItemStack stack) {
        if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return 0;
        }

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        if (shaderItemId > 44000 && shaderItemId < 44100) {
            return 15;
        }

        Block block = MinecraftReflectionCompat.call(Block.class, Block.class, null, new String[]{"func_149634_a", "getBlockFromItem"},
                new Class<?>[]{Item.class}, MinecraftReflectionCompat.itemStackItem(stack));
        int blockLight = block != null ? MinecraftReflectionCompat.callInt(block, new String[]{"getLightValue", "func_149750_m"},
                new Class<?>[]{IBlockState.class}, 0, MinecraftReflectionCompat.blockDefaultState(block)) : 0;
        if (blockLight > 0) {
            return blockLight;
        }

        return 0;
    }

    protected float[] heldBlockLightColor(ItemStack stack) {
        int lightValue = self().heldBlockLightValue(stack);
        if (lightValue <= 0) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }

        int shaderItemId = shaderProperties.itemIds().idFor(stack);
        float[] itemColor = PipelineRuntimeState.compatLightColorForVoxelId(PipelineRuntimeState.localActItemVoxelId(shaderItemId));
        if (itemColor != null) {
            return itemColor;
        }

        Block block = MinecraftReflectionCompat.call(Block.class, Block.class, null, new String[]{"func_149634_a", "getBlockFromItem"},
                new Class<?>[]{Item.class}, MinecraftReflectionCompat.itemStackItem(stack));
        if (block != null) {
            int shaderBlockId = self().currentRenderedBlockItemId(stack);
            float[] blockColor = PipelineRuntimeState.compatLightColorForVoxelId(PipelineRuntimeState.localActVoxelId(shaderBlockId));
            if (blockColor != null) {
                return blockColor;
            }
        }

        return new float[]{1.0f, 1.0f, 1.0f};
    }

    protected void logHeldColoredLightProbe(Minecraft mc) {
        if (heldColoredLightProbeLogs >= 16 || mc == null || shaderProperties == null) {
            return;
        }
        ItemStack main = self().heldMainStack(mc);
        ItemStack off = self().heldOffhandStack(mc);
        int mainLight = self().heldBlockLightValue(main);
        int offLight = self().heldBlockLightValue(off);
        if (mainLight <= 0 && offLight <= 0) {
            return;
        }
        float[] mainColor = self().heldBlockLightColor(main);
        float[] offColor = self().heldBlockLightColor(off);
        heldColoredLightProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMHeldColoredLight] probe={} frame={} mainId={} mainLight={} mainColor={}/{}/{} offId={} offLight={} offColor={}/{}/{}",
                heldColoredLightProbeLogs,
                pipelineFrameId,
                self().heldItemId(main),
                mainLight,
                mainColor[0],
                mainColor[1],
                mainColor[2],
                self().heldItemId(off),
                offLight,
                offColor[0],
                offColor[1],
                offColor[2]
        );
    }

    protected static int localActItemVoxelId(int itemId) {
        if (itemId == 44024) {
            return 24;
        }
        if (itemId >= 44070 && itemId <= 44080) {
            return itemId - 44000;
        }
        return 0;
    }

    protected static float[] compatLightColorForVoxelId(int voxelId) {
        return switch (voxelId) {
            case 24 -> new float[]{1.0f, 1.0f, 1.0f};
            case 70 -> new float[]{1.0f, 0.12f, 0.08f};
            case 71 -> new float[]{1.0f, 0.46f, 0.08f};
            case 72 -> new float[]{1.0f, 0.88f, 0.16f};
            case 73 -> new float[]{0.48f, 1.0f, 0.12f};
            case 74 -> new float[]{0.12f, 0.80f, 0.20f};
            case 75 -> new float[]{0.08f, 0.88f, 1.0f};
            case 76 -> new float[]{0.36f, 0.66f, 1.0f};
            case 77 -> new float[]{0.14f, 0.24f, 1.0f};
            case 78 -> new float[]{0.58f, 0.20f, 1.0f};
            case 79 -> new float[]{1.0f, 0.16f, 0.90f};
            case 80 -> new float[]{1.0f, 0.48f, 0.74f};
            case 110 -> new float[]{1.0f, 0.18f, 0.14f};
            case 111 -> new float[]{1.0f, 0.48f, 0.16f};
            case 112 -> new float[]{1.0f, 0.88f, 0.18f};
            case 113 -> new float[]{0.46f, 1.0f, 0.18f};
            case 114 -> new float[]{0.18f, 0.95f, 0.28f};
            case 115 -> new float[]{0.12f, 0.9f, 1.0f};
            case 116 -> new float[]{0.42f, 0.7f, 1.0f};
            case 117 -> new float[]{0.18f, 0.3f, 1.0f};
            case 118 -> new float[]{0.62f, 0.24f, 1.0f};
            case 119 -> new float[]{1.0f, 0.2f, 0.92f};
            case 120 -> new float[]{1.0f, 0.52f, 0.78f};
            default -> null;
        };
    }

    protected float[] entityColor(Entity entity) {
        if (entity instanceof EntityLivingBase living) {
            int hurtTime = MinecraftReflectionCompat.fieldInt(living, 0, "field_70737_aN", "hurtTime");
            int deathTime = MinecraftReflectionCompat.fieldInt(living, 0, "field_70725_aQ", "deathTime");
            if (hurtTime > 0 || deathTime > 0) {
                float hurtRatio = hurtTime / Math.max(1.0f, MinecraftReflectionCompat.fieldInt(living, 0, "field_70738_aO", "maxHurtTime"));
                float deathRatio = Math.min(1.0f, deathTime / 20.0f);
                float alpha = Math.max(hurtRatio, deathRatio) * 0.25f;
                return new float[]{1.0f, 0.0f, 0.0f, alpha};
            }
        }
        return NO_ENTITY_COLOR;
    }

    public void setCurrentEntity(Entity entity) {
        currentEntityKey = MinecraftReflectionCompat.entityKey(entity);
        currentEntityId = playerAwareEntityId(entity, currentEntityKey);
        currentEntityColor = self().entityColor(entity);
        logLocalPlayerEntityBinding(entity);
        self().uploadEntityUniforms();
    }

    private void logLocalPlayerEntityBinding(Entity entity) {
        if (!(entity instanceof EntityPlayer) || localPlayerEntityProbeLogs >= MAX_LOCAL_PLAYER_ENTITY_PROBE_LOGS) {
            return;
        }
        localPlayerEntityProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMLocalPlayerProbe] probe={} frame={} phase={} entityKey={} entityId={} vanillaId={} type={} color={}",
                localPlayerEntityProbeLogs,
                pipelineFrameId,
                self().getPhase(),
                currentEntityKey,
                currentEntityId,
                entity.getEntityId(),
                entity.getClass().getName(),
                currentEntityColor
        );
    }

    private int playerAwareEntityId(Entity entity, ResourceLocation entityKey) {
        int configuredId = self().entityId(entityKey);
        if (!(entity instanceof EntityPlayer)) {
            return configuredId;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        // Euphoria/Complementary reserves the adjacent pair for regular and
        // current players. Entity registry lookup alone cannot identify the
        // current client player, so leaving it at zero bypasses its alpha and
        // depth safeguards for player skins.
        return mc != null && entity == MinecraftReflectionCompat.player(mc) ? 50017 : 50016;
    }

    public void clearCurrentEntity() {
        currentEntityKey = null;
        currentEntityId = 0;
        currentEntityColor = NO_ENTITY_COLOR;
        self().uploadEntityUniforms();
    }

    public void applyWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void restoreWeatherRenderState() {
        if (!isPipelineActive || shaderProperties.renderSettings().rainDepth()) {
            return;
        }
        MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void applyWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        // bindPass already applies the shaderpack's blend modes using actual
        // draw-buffer slots. Attachment ids are not draw-buffer indices, so
        // overriding them here corrupts water/ice MRT output.
        // Water and ice share Minecraft's translucent layer. Retain normal
        // depth ownership here; a layer-wide polygon offset would also bias
        // ice and cause its overlapping geometry to separate visibly.
        MinecraftReflectionCompat.glStateDepthMask(true);
    }

    public void restoreWaterRenderState() {
        if (!isPipelineActive) {
            return;
        }
        MinecraftReflectionCompat.glStateDisableBlend();
        resetIndexedBlendState();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );
        MinecraftReflectionCompat.glStateDepthMask(true);
    }

    protected void uploadEntityUniforms() {
        ShaderProgram program = self().activeProgram();
        if (program == null) {
            return;
        }

        uniformRegistry.upload(program, "entityId");
        uniformRegistry.upload(program, "entityColor");
    }

    public void applyChunkFade(RenderChunk renderChunk, BlockRenderLayer layer) {
        if (renderChunk == null) {
            return;
        }
        if (!ENABLE_CHUNK_FADE) {
            self().resetChunkFadeUniform();
            return;
        }
        if (self().shouldSuppressChunkFadeForBetterPortals()) {
            self().resetChunkFadeUniform();
            return;
        }
        if (!self().shouldUploadChunkFade(layer)) {
            return;
        }

        BlockPos position = MinecraftReflectionCompat.renderChunkPosition(renderChunk);
        if (position == null) {
            self().resetChunkFadeUniform();
            return;
        }

        int dimensionId = self().safeDimensionId(self().renderChunkWorld(renderChunk));
        if (dimensionId == Integer.MIN_VALUE) {
            dimensionId = self().safeDimensionId(PipelineRuntimeState.renderWorld(MinecraftReflectionCompat.minecraft()));
        }
        self().applyChunkFade(dimensionId, MinecraftReflectionCompat.blockPosX(position), MinecraftReflectionCompat.blockPosY(position), MinecraftReflectionCompat.blockPosZ(position));
    }

    public void applyChunkFade(int blockX, int blockY, int blockZ) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = PipelineRuntimeState.renderWorld(mc);
        self().applyChunkFade(self().safeDimensionId(world), blockX, blockY, blockZ);
    }

    protected void applyChunkFade(int dimensionId, int blockX, int blockY, int blockZ) {
        if (!ENABLE_CHUNK_FADE) {
            self().resetChunkFadeUniform();
            return;
        }
        if (self().shouldSuppressChunkFadeForBetterPortals()) {
            self().resetChunkFadeUniform();
            return;
        }
        if (!self().shouldUploadChunkFade(null)) {
            return;
        }

        currentChunkFade = self().chunkFadeValue(dimensionId, blockX, blockY, blockZ);
        self().uploadChunkFadeUniform();
    }

    public void resetChunkFadeUniform() {
        if (currentChunkFade == 1.0f) {
            return;
        }
        currentChunkFade = 1.0f;
        self().uploadChunkFadeUniform();
    }

    protected boolean shouldUploadChunkFade(BlockRenderLayer layer) {
        if (!isPipelineActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS || renderingShadowMap) {
            return false;
        }
        if (layer != null && layer != BlockRenderLayer.SOLID
                && layer != BlockRenderLayer.CUTOUT
                && layer != BlockRenderLayer.CUTOUT_MIPPED
                && layer != BlockRenderLayer.TRANSLUCENT) {
            return false;
        }
        return PipelineRuntimeState.isChunkFadePass(activePass);
    }

    protected boolean shouldSuppressChunkFadeForBetterPortals() {
        return BetterPortalsCompat.isInstalled()
                && (self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive());
    }

    protected static boolean isChunkFadePass(RenderPass pass) {
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.GBUFFERS_BLOCK
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WATER;
    }

    protected float chunkFadeValue(int dimensionId, int blockX, int blockY, int blockZ) {
        if (dimensionId == Integer.MIN_VALUE) {
            return 1.0f;
        }

        ChunkFadeKey key = new ChunkFadeKey(
                dimensionId,
                Math.floorDiv(blockX, 16),
                // Vertical flight should not fade every newly-entered section of an already visible column.
                0,
                Math.floorDiv(blockZ, 16)
        );
        ChunkFadeState state = chunkFadeStates.get(key);
        if (state == null) {
            float initial = pipelineFrameId <= chunkFadeWarmupUntilFrame ? 1.0f : 0.0f;
            state = new ChunkFadeState(initial, pipelineFrameId);
            chunkFadeStates.put(key, state);
            self().pruneChunkFadeStates();
            return state.value;
        }

        if (state.lastFrameSeen != pipelineFrameId) {
            state.value = self().clamp01(state.value + currentFrameTime / CHUNK_FADE_DURATION_SECONDS);
            state.lastFrameSeen = pipelineFrameId;
        }
        return state.value;
    }

    protected void uploadChunkFadeUniform() {
        ShaderProgram program = self().activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "mc_chunkFade");
        }
    }

    protected void resetChunkFadeState(boolean warmExistingChunks) {
        chunkFadeStates.clear();
        currentChunkFade = 1.0f;
        chunkFadeWarmupUntilFrame = warmExistingChunks ? pipelineFrameId + CHUNK_FADE_WARMUP_FRAMES : pipelineFrameId;
    }

    protected void pruneChunkFadeStates() {
        if (chunkFadeStates.size() <= MAX_CHUNK_FADE_STATES) {
            return;
        }

        long staleBefore = pipelineFrameId - CHUNK_FADE_STALE_FRAMES;
        Iterator<Map.Entry<ChunkFadeKey, ChunkFadeState>> iterator = chunkFadeStates.entrySet().iterator();
        while (iterator.hasNext() && chunkFadeStates.size() > MAX_CHUNK_FADE_STATES) {
            if (iterator.next().getValue().lastFrameSeen < staleBefore) {
                iterator.remove();
            }
        }
        iterator = chunkFadeStates.entrySet().iterator();
        while (iterator.hasNext() && chunkFadeStates.size() > MAX_CHUNK_FADE_STATES) {
            iterator.next();
            iterator.remove();
        }
    }

    public void beginPass(RenderPass pass) {
        self().beginPass(pass, WorldRenderingPhase.NONE);
    }

    protected void beginPass(RenderPass pass, WorldRenderingPhase phase) {
        if (!isPipelineActive || !worldFrameActive) {
            return;
        }

        RenderPass previousPass = activePass;
        ShaderKey previousShaderKey = activeShaderKey;
        WorldRenderingPhase previousPhase = activePhase;
        boolean previousProgramTessellated = activeProgramTessellated;
        boolean previousProgramGeometric = activeProgramGeometric;
        activePhase = phase;
        boolean bound = self().bindPass(pass);
        self().logPipelinePassProbe(pass, phase, bound);
        passStack.push(new PipelineRuntimeState.PassScope(bound, previousPass, previousShaderKey, previousPhase, previousProgramTessellated, previousProgramGeometric));
    }

    protected void logPipelinePassProbe(RenderPass pass, WorldRenderingPhase phase, boolean bound) {
        if (pipelinePassProbeLogs >= MAX_PIPELINE_PASS_PROBE_LOGS) {
            return;
        }
        pipelinePassProbeLogs++;
        PipelineProgram declared = pass != null ? programs.get(pass) : null;
        PipelineProgram effective = pass != null ? self().effectivePipelineProgram(pass) : null;
        ShaderProgram shader = effective != null ? effective.shaderProgram() : null;
        MainMod.LOGGER.info(
                "[AUSMPassProbe] call={} pass={} phase={} bound={} active={} worldFrame={} shadow={} declared={} effective={} shader={} declaredBuffers={} effectiveBuffers={}",
                pipelinePassProbeLogs,
                pass,
                phase,
                bound,
                isPipelineActive,
                worldFrameActive,
                renderingShadowMap,
                declared != null && declared.enabled(),
                effective != null ? effective.pass() : "none",
                shader != null ? shader.getId() : -1,
                declared != null ? declared.drawBuffers() : "none",
                effective != null ? effective.drawBuffers() : "none"
        );
    }

    public boolean beginPhaseIfActive(WorldRenderingPhase phase) {
        if (self().renderingGuiScreen()) {
            return false;
        }
        RenderPass pass = self().passForPhase(phase);
        if (pass == null) {
            return false;
        }
        self().beginPass(pass, phase);
        return true;
    }

    public void beginPhase(WorldRenderingPhase phase) {
        if (phase == WorldRenderingPhase.PARTICLES || phase == WorldRenderingPhase.PARTICLES_TRANSLUCENT) {
            // ParticleManager shares client array/tessellation state with terrain.
            // Restore its fixed-function baseline before binding the particle
            // G-buffer program, otherwise stale terrain state can be replayed as
            // a translucent particle draw.
            BlockRenderContext.clear();
            FixedFunctionGlState.resetClientArrayState(false);
            FixedFunctionGlState.resetVanillaTextureMatrices();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        }
        self().beginPhaseIfActive(phase);
        self().forensicGlTrace("phase-enter", "requested=" + phase);
    }

    public WorldRenderingPhase blockEntityPhaseForCurrentForgePass() {
        if (renderingShadowMap) {
            return WorldRenderingPhase.BLOCK_ENTITIES;
        }
        return MinecraftReflectionCompat.forgeRenderPass() == 1
                ? WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT
                : WorldRenderingPhase.BLOCK_ENTITIES;
    }

    public void beginAstralConstellationPhase(Object constellation, WorldRenderingPhase phase) {
        self().setAstralConstellationColors(constellation);
        currentSkyDetailKind = 5;
        self().beginPhase(phase);
    }

    public void setAstralSolarEclipseFactor(float factor) {
        currentAstralSolarEclipseFactor = Math.clamp(factor, 0.0f, 1.0f);
    }

    public void endAstralConstellationPhase() {
        self().endPass();
        currentSkyDetailKind = 0;
        self().resetAstralConstellationColors();
    }

    /**
     * Publishes the detail currently being submitted by a compatibility renderer.
     * Values are intentionally numeric so shaderpacks do not need string handling.
     * 1 Botania planet, 2 Botania ribbon/skybox, 3 Botania rainbow,
     * 4 Astral stars, 5 Astral constellation, 6 Astral sun/moon,
     * 7 AUSM's Twilight Forest celestial bridge.
     */
    public void setSkyDetailAsset(String resourceName) {
        currentSkyDetailKind = PipelineRuntimeState.skyDetailKind(resourceName);
    }

    public void setSkyDetailKind(int kind) {
        currentSkyDetailKind = Math.clamp(kind, 0, 7);
    }

    public void clearSkyDetailAsset() {
        currentSkyDetailKind = 0;
    }

    /**
     * Uploads per-draw detail state after a renderer changes its bound texture.
     */
    public void uploadSkyDetailUniforms() {
        if (!isPipelineActive || activePass == null) {
            return;
        }
        PipelineProgram pipelineProgram = self().effectivePipelineProgram(activePass);
        ShaderProgram program = pipelineProgram != null ? pipelineProgram.shaderProgram() : null;
        if (program == null || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.getId()) {
            return;
        }
        uniformRegistry.upload(program, "ausmSkyDetailKind");
        uniformRegistry.upload(program, "ausmSkyDetailTextureSize");
        uniformRegistry.upload(program, "ausmAstralConstellationColor");
        uniformRegistry.upload(program, "ausmAstralTierColor");
        uniformRegistry.upload(program, "ausmAstralSolarEclipse");
    }
}
