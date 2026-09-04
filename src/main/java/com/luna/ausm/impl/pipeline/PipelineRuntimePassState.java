package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.pack.ShaderPack;
import com.luna.ausm.impl.pipeline.pack.ShaderProperties;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineGlState.resetOitRenderState;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_LAYER_OUTPUT_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_SPECIAL_LAYER_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_WATER_ROUTING_PROBE_LOGS;

abstract class PipelineRuntimeDiagnosticsState9 extends PipelineRuntimeDiagnosticsState8 {
    public void restoreActiveGbufferRenderState() {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || activePass == null) {
            return;
        }
        PipelineProgram pipelineProgram = programs.get(activePass);
        if (pipelineProgram == null || pipelineProgram.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        List<Attachment> drawBuffers = self().effectiveDrawBuffersForCurrentPhase(pipelineProgram);
        boolean valid = !drawBuffers.isEmpty();
        for (int slot = 0; valid && slot < drawBuffers.size(); slot++) {
            valid = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot) == GL30.GL_COLOR_ATTACHMENT0 + slot;
        }
        if (!valid) {
            pingPongManager.forceGbufferDrawBuffers(drawBuffers.toArray(new Attachment[0]));
        }
        self().applyAlphaTest(activePass);
        self().applyBlendMode(activePass, drawBuffers);
        self().applyOitDepthState(activePass);
        self().applyGbufferDepthState(activePass);
    }

    /**
     * Celeritas binds its native chunk shader in begin() before configuring
     * the batch vertex state. Restore AUSM after that setup point so the batch
     * draws into the shaderpack MRTs without rebinding once per chunk.
     */
    public void rebindActivePipelinePassAfterRendererSetup() {
        if (!isPipelineActive || !worldFrameActive || activePass == null || activePass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        self().bindPass(activePass);
    }

    protected void logWaterRoutingProbe(String stage, PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || waterRoutingProbeLogs >= MAX_WATER_ROUTING_PROBE_LOGS) {
            return;
        }
        waterRoutingProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        StringBuilder slots = new StringBuilder();
        int slotCount = Math.clamp(drawBuffers != null ? drawBuffers.size() : 0, 1, 8);
        for (int slot = 0; slot < slotCount; slot++) {
            if (slot > 0) {
                slots.append(';');
            }
            int drawBuffer = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot);
            int texture = 0;
            try {
                texture = GL30.glGetFramebufferAttachmentParameteri(
                        GL30.GL_DRAW_FRAMEBUFFER,
                        GL30.GL_COLOR_ATTACHMENT0 + slot,
                        GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
                );
            } catch (RuntimeException | LinkageError ignored) {
            }
            slots.append(slot).append("=draw:").append(drawBuffer).append(",tex:").append(texture);
        }
        int depthTexture = 0;
        try {
            depthTexture = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
            );
        } catch (RuntimeException | LinkageError ignored) {
        }
        MainMod.LOGGER.info(
                "[AUSMWaterRouting] call={} stage={} program={} declared={} effective={} fbo={} slots={} depthAttachment={} textures={} colors={} depth0={} depth1={} gl={}",
                waterRoutingProbeLogs,
                stage,
                pipelineProgram != null ? self().describePipelineProgram(pipelineProgram) : "null",
                pipelineProgram != null ? pipelineProgram.drawBuffers() : "none",
                drawBuffers,
                framebuffer != null ? framebuffer.getFramebufferId() : -1,
                slots,
                depthTexture,
                self().deferredBoundaryTextureSummary(framebuffer),
                self().deferredBoundaryColorSummary(framebuffer),
                self().deferredDepthSampleSummary(framebuffer, -1),
                self().deferredDepthSampleSummary(framebuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                PipelineRuntimeState.glStateSummary()
        );
    }

    public void logSpecialLayerProbe(String stage) {
        if (!isPipelineActive
                || !pingPongManager.isInitialized()
                || specialLayerProbeLogs >= MAX_SPECIAL_LAYER_PROBE_LOGS) {
            return;
        }
        specialLayerProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        StringBuilder drawBuffers = new StringBuilder();
        for (int slot = 0; slot < 5; slot++) {
            if (slot > 0) {
                drawBuffers.append(';');
            }
            drawBuffers.append(slot).append('=').append(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot));
        }
        MainMod.LOGGER.info(
                "[AUSMSpecialLayerProbe] call={} stage={} activePass={} phase={} worldFrame={} skip={} drawFbo={} drawBuffers={} program={} color={} composite={} normal={} depth={} gl={}",
                specialLayerProbeLogs,
                stage,
                activePass,
                self().getPhase(),
                worldFrameActive,
                self().shouldSkipAllMainGbufferRendering(),
                drawFramebuffer,
                drawBuffers,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                self().deferredFramebufferColorSamples(framebuffer, Attachment.COLOR),
                self().deferredFramebufferColorSamples(framebuffer, Attachment.COMPOSITE),
                self().deferredFramebufferColorSamples(framebuffer, Attachment.NORMAL),
                framebuffer != null && framebuffer.isUsable()
                        ? self().framebufferIdDepthSamples(framebuffer.getFramebufferId(), framebuffer.getWidth(), framebuffer.getHeight(), GL30.GL_COLOR_ATTACHMENT0)
                        : "none",
                PipelineRuntimeState.glStateSummary());
    }

    protected void configureShadowDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (shadowFramebuffer == null || pipelineProgram.stage() != ProgramStage.SHADOW) {
            return;
        }
        shadowFramebuffer.bindForProgramWrite(drawBuffers.toArray(new Attachment[0]));
    }

    public void endPass() {
        if (!isPipelineActive || passStack.isEmpty()) {
            return;
        }

        self().forensicGlTrace("phase-exit-before", "stackDepth=" + passStack.size());
        PipelineFrameLayerCapture.captureGbufferLayerOutput(
                pipelineFrameId,
                activePass,
                activePhase,
                pingPongManager.getReadBuffer()
        );
        PipelineGpuTiming.endPass();
        PipelineRuntimeState.PassScope scope = passStack.pop();
        activePhase = scope.previousPhase();
        if (!scope.bound()) {
            activeShaderKey = scope.previousShaderKey();
            activeProgramTessellated = scope.previousProgramTessellated();
            activeProgramGeometric = scope.previousProgramGeometric();
            return;
        }

        self().logLayerOutputProbe(activePass, activePhase);
        PipelineProgram pipelineProgram = programs.get(activePass);
        ShaderProgram program = pipelineProgram != null ? pipelineProgram.effectiveProgram(programs) : null;
        if (program != null) {
            program.unbind();
        }

        if (self().isOitGbufferPass(activePass)) {
            resetOitRenderState();
        }

        activePass = null;
        activeShaderKey = null;
        activeProgramTessellated = false;
        activeProgramGeometric = false;
        if (scope.previousPass() != null) {
            self().bindPass(scope.previousPass());
        } else {
            activeShaderKey = scope.previousShaderKey();
            activeProgramTessellated = scope.previousProgramTessellated();
            activeProgramGeometric = scope.previousProgramGeometric();
        }
        self().forensicGlTrace("phase-exit-after", "restoredPass=" + activePass + ", stackDepth=" + passStack.size());
    }

    protected void logLayerOutputProbe(RenderPass pass, WorldRenderingPhase phase) {
        if (pass == null
                || layerOutputProbeLogs >= MAX_LAYER_OUTPUT_PROBE_LOGS
                || !(pass.stage() == ProgramStage.GBUFFERS
                || pass.stage() == ProgramStage.SHADOW)) {
            return;
        }
        layerOutputProbeLogs++;
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        String color = self().deferredFramebufferColorSamples(framebuffer, Attachment.COLOR);
        String composite = self().deferredFramebufferColorSamples(framebuffer, Attachment.COMPOSITE);
        String normal = self().deferredFramebufferColorSamples(framebuffer, Attachment.NORMAL);
        String depth = framebuffer != null && framebuffer.isUsable()
                ? self().deferredDepthSampleSummary(framebuffer, -1)
                : "none";
        int glError = GL11.glGetError();
        MainMod.LOGGER.info(
                "[AUSMLayerOutputProbe] call={} pass={} phase={} color={} composite={} normal={} depth={} fbo={} glProgram={} glError={}",
                layerOutputProbeLogs,
                pass,
                phase,
                color,
                composite,
                normal,
                depth,
                framebuffer != null ? framebuffer.getFramebufferId() : -1,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                glError
        );
    }

    public void resize(int width, int height) {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        self().resizeFramebuffer(width, height, true);
    }

    protected abstract void activateSoftVanillaTerrainRenderer(String reason);

    protected abstract ShaderProgram activeProgram();

    protected abstract float cameraHorizontalVelocityMagnitude();

    protected abstract float cameraVerticalDelta();

    protected abstract void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms);

    protected abstract void cleanupRuntimeState(boolean deleteActiveCompiledPrograms, boolean deleteCachedCompiledPrograms,
                                                boolean deleteVanillaTerrainRenderers);

    protected abstract void clearColoredLightImages();

    protected abstract void clearNothiriumPipelineTranslucentBridge();

    protected abstract String deferredBoundaryColorSummary(DeferredFramebuffer framebuffer);

    protected abstract String deferredBoundaryTextureSummary(DeferredFramebuffer framebuffer);

    protected abstract String deferredDepthSampleSummary(DeferredFramebuffer framebuffer, int snapshotIndex);

    protected abstract String describeFramebufferTarget(Framebuffer framebuffer);

    protected abstract String describeFramebufferTargetDetailed(Framebuffer framebuffer);

    protected abstract String describePipelineProgram(PipelineProgram program);

    protected abstract void initializeNoiseTexture(ShaderPack pack, ShaderProperties properties);

    protected abstract void markNothiriumPipelineTranslucentBridge(BlockRenderLayer layer);

    protected abstract void prepareGuiState();

    protected abstract void recordTerrainLayerCount(BlockRenderLayer layer, int count);

    protected abstract World renderChunkWorld(RenderChunk chunk);

    protected abstract void resizeFramebuffer(int width, int height, boolean preservePersistentAttachments);

    protected abstract void restoreGuiSafeRenderState(String source);

    protected abstract void restoreVanillaFixedFunctionTextureState(Minecraft mc);

    protected abstract void restoreVanillaWorldTextureBindings();

    public abstract boolean isCustomVoidWorldSkyEnabled(World world);

    protected abstract boolean isFiniteColor(float[] color);

    protected abstract boolean isOverworldShaderEnvironment(World world);

    public abstract boolean isRenderingBetterPortalsNestedView();

    public abstract boolean isRenderingBetterPortalsRenderPass();

    public abstract boolean shouldBypassWorldPassRendering();

    protected abstract boolean isSimpleVoidWorld(World world);

    protected abstract void rebuildTerrainRenderers(boolean recreateNothiriumRenderer, boolean reloadVanillaRenderGlobal);

    protected abstract void renderShaderlessBotaniaVoidDetails(float partialTicks, WorldClient world, Minecraft mc);

    protected abstract void resetShadowRenderCache();

    protected abstract int safeDimensionId(World world);

    protected abstract void scheduleBloomTerrainRefresh(String reason);

    protected abstract void scheduleWorldTerrainRefresh(boolean fullRendererReset, boolean vanillaReload, int initialDelay);

    protected abstract boolean shouldUseNothiriumMainTerrainBridge();

    protected abstract boolean shouldSuppressDuplicatePipelineTranslucentLayer(BlockRenderLayer layer);

    public abstract boolean shouldUseOwnedSkyOverrideWorld(World world);

    protected abstract boolean shouldUseShaderedF1LowerSkyRepair(Minecraft mc, World world);

    protected abstract boolean updateNothiriumPipelineBlockFormatMode();

    protected abstract void unbindShaderStorageBuffers();

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
        ResourceLocation name = PipelineRuntimeState.registryName(state);
        if (name == null) {
            return 0;
        }
        if ("tconstruct".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "seared_furnace_controller".equals(MinecraftReflectionCompat.resourcePath(name))
                && PipelineRuntimeState.stateName(state).contains("active=true")) {
            return 71;
        }
        if ("aether_legacy".equals(MinecraftReflectionCompat.resourceNamespace(name))
                && "aether_portal".equals(MinecraftReflectionCompat.resourcePath(name))) {
            return PipelineRuntimeState.localActVoxelId(10914);
        }
        int astralVoxel = PipelineRuntimeState.astralCrystalVoxelId(state);
        return astralVoxel > 0 ? astralVoxel : 0;
    }

    protected static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    protected static int eyeFluidState(Minecraft mc) {
        if (mc == null) {
            return 0;
        }
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        World world = PipelineRuntimeState.renderWorld(mc);
        if (world == null || viewEntity == null) {
            return 0;
        }
        IBlockState cameraState = MinecraftReflectionCompat.blockStateAtEntityViewpoint(
                world, viewEntity, MinecraftReflectionCompat.renderPartialTicks(mc));
        if (MinecraftReflectionCompat.stateMaterialIsWater(cameraState)) {
            return 1;
        }
        if (MinecraftReflectionCompat.stateMaterial(cameraState)
                == MinecraftReflectionCompat.field(Material.class,
                Material.class, null, "field_151587_i", "LAVA")
                && !MinecraftReflectionCompat.playerIsSpectator(MinecraftReflectionCompat.player(mc))) {
            return 2;
        }
        return 0;
    }

    protected static int clampInt(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    protected static String glStateSummary() {
        return FixedFunctionGlState.summary();
    }

    protected static String formatProbeFloat(float value) {
        if (!Float.isFinite(value)) {
            return "nan";
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    protected static ShaderProperties emptyShaderProperties() {
        return PipelineContext.emptyShaderProperties();
    }
}
