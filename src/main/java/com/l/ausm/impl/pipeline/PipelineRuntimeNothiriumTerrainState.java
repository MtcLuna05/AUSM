package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.FogMode;
import com.l.ausm.api.pipeline.shader.LightingModel;
import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.compat.NothiriumBypass;
import com.l.ausm.impl.pipeline.shader.ShaderKey;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_MAIN_SETUP_BRIDGE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_LOGS;

abstract class PipelineRuntimeDiagnosticsState5 extends PipelineRuntimeDiagnosticsState4 {
    protected void logNothiriumSparseMainProviderDraw(BlockRenderLayer layer, int visibleCount, int providerCount,
                                                      double cameraX, double cameraY, double cameraZ) {
        if (nothiriumSparseMainProviderDrawLogs++ >= MAX_NOTHIRIUM_SPARSE_MAIN_PROVIDER_DRAW_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMNothiriumSparseMainProviderDraw] call={} layer={} visible={} providerCount={} frame={} activePass={} phase={} camera={}/{}/{} maxChunks={} distance={} untilFrame={} gl={}",
                nothiriumSparseMainProviderDrawLogs,
                layer,
                visibleCount,
                providerCount,
                pipelineFrameId,
                String.valueOf(activePass),
                self().getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                self().nothiriumSparseMainProviderDrawMaxChunks(layer),
                self().nothiriumSparseMainProviderDrawDistance(layer),
                nothiriumSparseMainProviderDrawUntilFrame,
                PipelineRuntimeState.glStateSummary()
        );
    }

    protected void logNothiriumMainSetupBridge(BlockRenderLayer layer, boolean setup, double cameraX, double cameraY, double cameraZ) {
        if (!setup || nothiriumMainSetupBridgeLogs >= MAX_NOTHIRIUM_MAIN_SETUP_BRIDGE_LOGS) {
            return;
        }
        nothiriumMainSetupBridgeLogs++;
        MainMod.LOGGER.info(
                "[AUSMNothiriumMainSetupBridge] call={} setup={} layer={} frame={} activePass={} phase={} camera={}/{}/{} gl={}",
                nothiriumMainSetupBridgeLogs,
                setup,
                layer,
                pipelineFrameId,
                String.valueOf(activePass),
                self().getPhase(),
                cameraX,
                cameraY,
                cameraZ,
                PipelineRuntimeState.glStateSummary()
        );
    }

    protected boolean setupNothiriumShaderedMainTerrainLists(boolean afterCompileUpload) {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass == null
                || !self().shouldUseNothiriumMainTerrainBridge()) {
            return false;
        }
        if (afterCompileUpload) {
            if (nothiriumShaderedMainPostCompileSetupFrame == pipelineFrameId) {
                return true;
            }
        } else if (nothiriumShaderedMainSetupFrame == pipelineFrameId) {
            return true;
        }

        self().maintainNothiriumShaderedMainTerrainChunks();
        // Nothirium completes chunk compilation asynchronously and queues the
        // VBO upload on its render thread. Drain that queue before setup builds
        // the visibility lists, otherwise freshly compiled chunks remain absent
        // until a later renderer-owned setup pass.
        nothiriumShadowRenderer.drainUploads();
        boolean setup = NothiriumBypass.setupForShaderedMainTerrainBridge();
        if (setup) {
            if (afterCompileUpload) {
                nothiriumShaderedMainPostCompileSetupFrame = pipelineFrameId;
            } else {
                nothiriumShaderedMainSetupFrame = pipelineFrameId;
            }
        }
        return setup;
    }

    protected void maintainNothiriumShaderedMainTerrainChunks() {
        if (nothiriumShaderedMainSetupFrame == pipelineFrameId) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        RenderGlobal renderGlobal = mc == null
                ? null
                : MinecraftReflectionCompat.renderGlobal(mc);
        if (renderGlobal == null) {
            return;
        }
        try {
            // Shaderless Nothirium receives this maintenance from EntityRenderer.
            // Shadered terrain replaces that draw sequence, so keep the same
            // dirty-section queue and dispatcher upload phase alive here.
            MinecraftReflectionCompat.updateChunks(
                    renderGlobal,
                    System.nanoTime() + 4_000_000L
            );
        } catch (RuntimeException | LinkageError ignored) {
            // The normal Nothirium setup remains usable if another renderer owns
            // the update hook for this frame.
        }
    }

    /**
     * RenderLib refreshes Nothirium's camera/frustum snapshot from the vanilla
     * RenderGlobal setup hook. Shadered world rendering can reach the Nothirium
     * bridge without traversing that hook, leaving visibility traversal rooted
     * at the current section only.
     */
    protected void prepareNothiriumShaderedMainTerrainCamera() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null) {
            return;
        }
        RenderGlobal renderGlobal =
                MinecraftReflectionCompat.renderGlobal(mc);
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        if (renderGlobal == null || viewEntity == null) {
            return;
        }
        try {
            MinecraftReflectionCompat.setupTerrain(
                    renderGlobal,
                    viewEntity,
                    MinecraftReflectionCompat.renderPartialTicks(mc),
                    ALWAYS_VISIBLE_CAMERA,
                    (int) pipelineFrameId,
                    MinecraftReflectionCompat.playerIsSpectator(
                            MinecraftReflectionCompat.player(mc))
            );
        } catch (RuntimeException | LinkageError ignored) {
            // Nothirium's own setup remains the fallback if another renderer
            // owns the vanilla setup hook for this frame.
        }
    }

    protected boolean forceSetupNothiriumShaderedMainTerrainListsAfterRepair() {
        if (!isPipelineActive
                || !worldFrameActive
                || renderingShadowMap
                || activePass == null
                || !self().shouldUseNothiriumMainTerrainBridge()) {
            return false;
        }

        boolean setup = NothiriumBypass.setupForShaderedMainTerrainBridge();
        if (setup) {
            nothiriumShaderedMainPostCompileSetupFrame = pipelineFrameId;
            nothiriumShaderedMainSetupFrame = pipelineFrameId;
        }
        return setup;
    }

    protected double nothiriumMainTerrainFallbackDistance() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.gameSettings(mc) == null) {
            return -1.0D;
        }
        int chunks = Math.max(2, MinecraftReflectionCompat.renderDistanceChunks(mc));
        return chunks * 16.0D + 32.0D;
    }

    public boolean renderNothiriumRendererPass(Object chunkRenderPass) {
        if (!isPipelineActive
                || renderingShadowMap
                || !self().shouldUseNothiriumMainTerrainBridge()
                || !PipelineRuntimeState.isNothiriumTranslucentPass(chunkRenderPass)) {
            return false;
        }
        return self().shouldCancelDuplicateNothiriumTranslucentPass(true);
    }

    protected boolean shouldCancelDuplicateNothiriumTranslucentPass(boolean translucentPass) {
        return translucentPass && self().shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer.TRANSLUCENT);
    }

    protected static boolean isNothiriumTranslucentPass(Object chunkRenderPass) {
        return chunkRenderPass instanceof Enum<?> pass && "TRANSLUCENT".equals(pass.name());
    }

    protected int nothiriumFallbackBlockEntityId(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        int stillWater = self().blockEntityId(self().nothiriumFallbackWaterState("field_150355_j", "WATER"));
        if (stillWater != 0) {
            return stillWater;
        }
        return self().blockEntityId(self().nothiriumFallbackWaterState("field_150358_i", "FLOWING_WATER"));
    }

    protected short nothiriumFallbackRenderType(BlockRenderLayer layer) {
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return 0;
        }
        return (short) MinecraftReflectionCompat.stateRenderTypeOrdinal(self().nothiriumFallbackWaterState("field_150355_j", "WATER"));
    }

    protected IBlockState nothiriumFallbackWaterState(String srgName, String mcpName) {
        return MinecraftReflectionCompat.blockDefaultState(MinecraftReflectionCompat.field(Blocks.class, Block.class, null, srgName, mcpName));
    }

    public ShaderKey getShaderKey() {
        return activeShaderKey;
    }

    public FogMode getFogMode() {
        return activeShaderKey == null ? FogMode.OFF : activeShaderKey.fogMode();
    }

    public LightingModel getLightingModel() {
        return activeShaderKey == null ? LightingModel.LIGHTMAP : activeShaderKey.lightingModel();
    }

    public void setPhase(WorldRenderingPhase phase) {
        activePhase = phase == null ? WorldRenderingPhase.NONE : phase;
    }

    public void clearPhase(WorldRenderingPhase expectedPhase) {
        if (expectedPhase == null || activePhase == expectedPhase) {
            activePhase = WorldRenderingPhase.NONE;
        }
    }

    public void setOverridePhase(WorldRenderingPhase phase) {
        overridePhase = phase;
    }

    public void clearOverridePhase() {
        overridePhase = null;
    }

    public int entityId(Entity entity) {
        if (entity == null) {
            return 0;
        }

        ResourceLocation entityKey = MinecraftReflectionCompat.entityKey(entity);
        return self().entityId(entityKey);
    }

    protected int entityId(ResourceLocation entityKey) {
        if (entityKey != null) {
            Integer alias = shaderProperties.entityIds().get(entityKey);
            if (alias != null) {
                return alias;
            }
        }

        return 0;
    }

    public int currentEntityId() {
        return currentEntityId;
    }

    protected int vehicleId(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.player(mc) == null || MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)) == null) {
            return 0;
        }
        return self().entityId(MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)));
    }

    protected boolean vehicleInWater(Minecraft mc) {
        return mc != null
                && MinecraftReflectionCompat.player(mc) != null
                && MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)) != null
                && MinecraftReflectionCompat.callBoolean(MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)), new String[]{"func_70090_H", "isInWater"}, MinecraftReflectionCompat.NO_PARAMETERS, false);
    }

    protected float[] vehicleLookVector(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.player(mc) == null || MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)) == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return PipelineRuntimeState.vec3(MinecraftReflectionCompat.look(MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)), MinecraftReflectionCompat.renderPartialTicks(mc)));
    }

    protected float[] relativeVehiclePosition(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.player(mc) == null || MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc)) == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        Entity vehicle = MinecraftReflectionCompat.entityRidingEntity(MinecraftReflectionCompat.player(mc));
        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        double x = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosX(vehicle), MinecraftReflectionCompat.posX(vehicle), partialTicks);
        double y = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosY(vehicle), MinecraftReflectionCompat.posY(vehicle), partialTicks);
        double z = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosZ(vehicle), MinecraftReflectionCompat.posZ(vehicle), partialTicks);
        return new float[]{
                (float) (cameraPositionUnshifted[0] - x),
                (float) (cameraPositionUnshifted[1] - y),
                (float) (cameraPositionUnshifted[2] - z)
        };
    }

    protected static float[] bodyVector(Entity entity) {
        if (entity == null) {
            return new float[]{0.0f, 0.0f, 0.0f};
        }
        return PipelineRuntimeState.vec3(MinecraftReflectionCompat.call(entity, Vec3d.class, null, new String[]{"func_189651_aD", "getForward", "func_70040_Z", "getLookVec"}, MinecraftReflectionCompat.NO_PARAMETERS));
    }

    protected float[] lightningBoltPosition(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        if (mc == null || world == null) {
            return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        }
        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        for (Entity entity : MinecraftReflectionCompat.loadedEntityList(world)) {
            if (entity instanceof EntityLightningBolt) {
                double x = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosX(entity), MinecraftReflectionCompat.posX(entity), partialTicks);
                double y = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosY(entity), MinecraftReflectionCompat.posY(entity), partialTicks);
                double z = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosZ(entity), MinecraftReflectionCompat.posZ(entity), partialTicks);
                return new float[]{
                        (float) (x - cameraPositionUnshifted[0]),
                        (float) (y - cameraPositionUnshifted[1]),
                        (float) (z - cameraPositionUnshifted[2]),
                        1.0f
                };
            }
        }
        return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
    }

    protected void updateEndFlashState(Minecraft mc) {
        previousEndFlashIntensity = endFlashIntensity;
        endFlashIntensity = 0.0f;
        endFlashPosition[0] = 0.0f;
        endFlashPosition[1] = 0.0f;
        endFlashPosition[2] = 0.0f;

        if (!shaderProperties.renderSettings().supportsEndFlash()) {
            return;
        }

        World world = PipelineRuntimeState.renderWorld(mc);
        if (mc == null || world == null) {
            return;
        }
        if (!PipelineRuntimeState.isEndWorld(world)) {
            return;
        }

        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        EntityDragon strongestDragon = null;
        float strongestIntensity = 0.0f;
        for (Entity entity : MinecraftReflectionCompat.loadedEntityList(world)) {
            if (!(entity instanceof EntityDragon dragon) || MinecraftReflectionCompat.fieldInt(dragon, 0, "field_70995_bG", "deathTicks") <= 0) {
                continue;
            }
            float intensity = self().clamp01((MinecraftReflectionCompat.fieldInt(dragon, 0, "field_70995_bG", "deathTicks") + partialTicks) / 200.0f);
            if (intensity > strongestIntensity) {
                strongestIntensity = intensity;
                strongestDragon = dragon;
            }
        }
        if (strongestDragon == null) {
            return;
        }

        double x = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosX(strongestDragon), MinecraftReflectionCompat.posX(strongestDragon), partialTicks) - cameraPositionUnshifted[0];
        double y = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosY(strongestDragon), MinecraftReflectionCompat.posY(strongestDragon), partialTicks) - cameraPositionUnshifted[1];
        double z = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.prevPosZ(strongestDragon), MinecraftReflectionCompat.posZ(strongestDragon), partialTicks) - cameraPositionUnshifted[2];
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length > 1.0e-4) {
            double scale = 100.0 / length;
            endFlashPosition[0] = (float) (x * scale);
            endFlashPosition[1] = (float) (y * scale);
            endFlashPosition[2] = (float) (z * scale);
        } else {
            endFlashPosition[1] = 100.0f;
        }
        endFlashYawDegrees = (float) Math.toDegrees(Math.atan2(x, z));
        endFlashPitchDegrees = (float) Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z)));
        endFlashIntensity = strongestIntensity;
    }

    protected void resetEndFlashState() {
        endFlashPosition[0] = 0.0f;
        endFlashPosition[1] = 0.0f;
        endFlashPosition[2] = 0.0f;
        endFlashIntensity = 0.0f;
        previousEndFlashIntensity = 0.0f;
        endFlashYawDegrees = 0.0f;
        endFlashPitchDegrees = 0.0f;
    }

    protected boolean useEndFlashShadowLight(World world) {
        return shaderProperties.renderSettings().supportsEndFlash()
                && PipelineRuntimeState.isEndWorld(world)
                && endFlashIntensity > 0.0f;
    }

    protected static boolean isEndWorld(World world) {
        return world != null && MinecraftReflectionCompat.worldProvider(world) != null && MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world)) == 1;
    }

    public void beginRenderedItem(ItemStack stack) {
        renderedItemIdStack.push(currentRenderedItemId);
        currentRenderedItemId = self().currentRenderedItemId(stack);
        currentRenderedItemDebugName = self().renderedItemDebugName(stack);
        self().uploadCurrentRenderedItemId();
    }

    public void endRenderedItem() {
        currentRenderedItemId = renderedItemIdStack.isEmpty() ? -1 : renderedItemIdStack.pop();
        currentRenderedItemDebugName = "";
        self().uploadCurrentRenderedItemId();
    }

    protected String renderedItemDebugName(ItemStack stack) {
        if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return "empty";
        }
        Item item = MinecraftReflectionCompat.itemStackItem(stack);
        ResourceLocation key = item != null ? MinecraftReflectionCompat.call(item, ResourceLocation.class, null, new String[]{"getRegistryName"}, MinecraftReflectionCompat.NO_PARAMETERS) : null;
        return (key != null ? key.toString() : item != null ? item.getClass().getName() : "null")
                + ":" + MinecraftReflectionCompat.itemStackMetadata(stack);
    }

    protected void uploadCurrentRenderedItemId() {
        ShaderProgram program = self().activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "currentRenderedItemId");
        }
    }

    /**
     * Temporarily presents immediate-mode/TESR geometry as a shaderpack block-entity material.
     * This lets compatibility renderers opt only their animated geometry into emission without
     * making the backing tile or multiblock casing emissive.
     */
    public boolean beginDynamicBlockEntityEmission(int blockEntityId) {
        if (!self().canRenderDynamicBlockEntityEmission()) {
            return false;
        }
        dynamicBlockEntityIdStack.push(dynamicBlockEntityId);
        dynamicBlockEntityId = blockEntityId;
        self().uploadDynamicBlockEntityId();
        return true;
    }

    public void endDynamicBlockEntityEmission() {
        dynamicBlockEntityId = dynamicBlockEntityIdStack.isEmpty() ? -1 : dynamicBlockEntityIdStack.pop();
        self().uploadDynamicBlockEntityId();
    }

    public boolean canRenderDynamicBlockEntityEmission() {
        return isPipelineActive
                && worldFrameActive
                && activePass != null
                && activePass.stage() == ProgramStage.GBUFFERS
                && !renderingShadowMap
                && !self().renderingGuiScreen();
    }

    protected void uploadDynamicBlockEntityId() {
        ShaderProgram program = self().activeProgram();
        if (program == null || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program.getId()) {
            return;
        }
        uniformRegistry.upload(program, "blockEntityId");
    }

    public boolean shouldRenderEntityWithVanillaProgram(Entity entity) {
        if (!isPipelineActive || !worldFrameActive || activePass == null || renderingShadowMap || self().renderingGuiScreen()) {
            return false;
        }
        if (activePass.stage() != ProgramStage.GBUFFERS) {
            return false;
        }
        return PipelineRuntimeState.isBetweenlandsEntity(MinecraftReflectionCompat.entityKey(entity));
    }

    protected static boolean isBetweenlandsEntity(ResourceLocation entityKey) {
        return entityKey != null && "thebetweenlands".equals(MinecraftReflectionCompat.resourceNamespace(entityKey));
    }

    protected static boolean isBetweenlandsRenderStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.startsWith("thebetweenlands.client.render.entity.")
                    || className.startsWith("thebetweenlands.client.render.model.entity.")) {
                return true;
            }
        }
        return false;
    }

    protected int heldItemId(ItemStack stack) {
        return shaderProperties.itemIds().idFor(stack);
    }

    protected int currentRenderedItemId(ItemStack stack) {
        Integer explicitItemId = shaderProperties.itemIds().explicitIdFor(stack);
        if (explicitItemId != null) {
            return explicitItemId;
        }
        int blockItemId = self().currentRenderedBlockItemId(stack);
        return blockItemId != 0 ? blockItemId : 0;
    }

    protected int currentRenderedBlockItemId(ItemStack stack) {
        if (MinecraftReflectionCompat.itemStackIsEmpty(stack)) {
            return 0;
        }
        Block block = MinecraftReflectionCompat.call(Block.class, Block.class, null, new String[]{"func_149634_a", "getBlockFromItem"},
                new Class<?>[]{Item.class}, MinecraftReflectionCompat.itemStackItem(stack));
        if (block == null || block == MinecraftReflectionCompat.field(Blocks.class, Block.class, null, "field_150350_a", "AIR")) {
            return 0;
        }
        try {
            int metadata = MinecraftReflectionCompat.itemStackMetadata(stack);
            IBlockState state = MinecraftReflectionCompat.blockStateFromMeta(block, metadata);
            if (state != null) {
                return shaderProperties.blockIds().idFor(state);
            }
        } catch (RuntimeException ignored) {
        }
        return shaderProperties.blockIds().idFor(MinecraftReflectionCompat.blockDefaultState(block));
    }
}
