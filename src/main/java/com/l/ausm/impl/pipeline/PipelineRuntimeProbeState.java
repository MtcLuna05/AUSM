package com.l.ausm.impl.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.client.dynamic.DynamicLightManager;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.l.ausm.impl.pipeline.PipelineProbeLimits.DEBUG_PROBES_ENABLED;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_CURRENT_PROBLEM_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SHADERLESS_SOLID_TERRAIN_SKY_GUI_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SHADERLESS_SOLID_TERRAIN_SKY_PAUSE_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SHADERLESS_SOLID_TERRAIN_SKY_WORLD_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SKY_DOME_GUI_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SKY_DOME_PAUSE_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_SKY_DOME_WORLD_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_WORLD_PASS_SKY_DOME_GUI_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_WORLD_PASS_SKY_DOME_PAUSE_PROBE_LOGS;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_WORLD_PASS_SKY_DOME_WORLD_PROBE_LOGS;

abstract class PipelineRuntimeProbeState extends PipelineRuntimeCompatibilityState {
    public void logCurrentProblemProbe(String source, IBlockState state, IBlockAccess blockAccess, BlockPos pos,
                                       String detail) {
        if (!PipelineRuntimeState.debugProbeLoggingEnabled()) {
            return;
        }
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        IBlockState effectiveState = self().effectiveBlockRenderState(state, blockAccess, pos);
        IBlockState inheritedState = self().inheritedBloomRenderState(state, blockAccess, pos);
        boolean activeLightOrId = self().blockRenderEmission(state, blockAccess, pos) > 0
                || self().blockRenderEmission(effectiveState, blockAccess, pos) > 0
                || self().blockRenderEmission(inheritedState, blockAccess, pos) > 0
                || self().blockEntityId(state, blockAccess, pos) != 0
                || self().blockEntityId(effectiveState, blockAccess, pos) != 0
                || self().blockEntityId(inheritedState, blockAccess, pos) != 0;
        if (!activeLightOrId
                && !self().isCurrentProblemProbeTarget(state)
                && !self().isCurrentProblemProbeTarget(effectiveState)
                && !self().isCurrentProblemProbeTarget(inheritedState)) {
            return;
        }

        String key = source
                + "|" + self().safeDimensionId(blockAccess instanceof World world ? world : null)
                + "|" + PipelineRuntimeState.formatBlockPos(pos)
                + "|" + String.valueOf(MinecraftReflectionCompat.currentRenderLayer())
                + "|" + PipelineRuntimeState.stateName(state)
                + "|" + PipelineRuntimeState.stateName(effectiveState)
                + "|" + PipelineRuntimeState.stateName(inheritedState)
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        int count = activeLightOrId
                ? activeLightOrIdProbeCount.incrementAndGet()
                : currentProblemProbeCount.incrementAndGet();
        int limit = activeLightOrId ? MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS : MAX_CURRENT_PROBLEM_PROBE_LOGS;
        if (count > limit) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} activeLightOrId={} dim={} pos={} layer={} bloomLayer={} state={} effective={} inherited={} emission={} inheritedEmission={} alpha={} blockId={} inheritedBlockId={} detail={}",
                count,
                source,
                self().diagnosticBlockKind(state, effectiveState, blockAccess, pos),
                activeLightOrId,
                self().safeDimensionId(blockAccess instanceof World world ? world : null),
                PipelineRuntimeState.formatBlockPos(pos),
                MinecraftReflectionCompat.currentRenderLayer(),
                AusmBloomLayer.layer(),
                self().framedDiagnosticState("state", state, blockAccess, pos, MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                self().framedDiagnosticState("effective", effectiveState, blockAccess, pos, MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                self().framedDiagnosticState("inherited", inheritedState, blockAccess, pos, MinecraftReflectionCompat.currentRenderLayer(), AusmBloomLayer.layer()),
                self().blockRenderEmission(state, blockAccess, pos),
                self().blockRenderEmission(state, blockAccess, pos),
                -1,
                self().blockEntityId(state, blockAccess, pos),
                self().blockEntityId(inheritedState, blockAccess, pos),
                detail
        );
    }

    public void logCurrentRenderContextProbe(String source, String detail) {
        if (!PipelineRuntimeState.debugProbeLoggingEnabled()) {
            return;
        }
        if (!CURRENT_PROBLEM_PROBES_ENABLED) {
            return;
        }
        String kind = BlockRenderContext.debugKind();
        if (!"blockcraftery".equals(kind)
                && !"architecturecraft".equals(kind)
                && !"emissive-name".equals(kind)
                && !"active-light-or-id".equals(kind)) {
            return;
        }
        if ("blockcraftery".equals(kind) && BlockRenderContext.blockEmission() <= 0 && BlockRenderContext.blockEntityId() == 0) {
            return;
        }

        String key = source
                + "|" + kind
                + "|" + BlockRenderContext.debugState()
                + "|" + BlockRenderContext.debugEffectiveState()
                + "|" + String.valueOf(MinecraftReflectionCompat.currentRenderLayer())
                + "|" + String.valueOf(detail);
        if (!currentProblemProbeKeys.add(key)) {
            return;
        }
        boolean activeLightOrId = "active-light-or-id".equals(kind)
                || BlockRenderContext.blockEmission() > 0
                || BlockRenderContext.blockEntityId() != 0;
        int count = activeLightOrId
                ? activeLightOrIdProbeCount.incrementAndGet()
                : currentProblemProbeCount.incrementAndGet();
        int limit = activeLightOrId ? MAX_ACTIVE_LIGHT_OR_ID_PROBE_LOGS : MAX_CURRENT_PROBLEM_PROBE_LOGS;
        if (count > limit) {
            return;
        }

        MainMod.LOGGER.info(
                "[AUSMCurrentProblemProbe] call={} source={} kind={} activeLightOrId={} layer={} state={} effective={} contextEmission={} contextAlpha={} blockId={} bloomMask={} detail={}",
                count,
                source,
                kind,
                activeLightOrId,
                MinecraftReflectionCompat.currentRenderLayer(),
                BlockRenderContext.debugState(),
                BlockRenderContext.debugEffectiveState(),
                BlockRenderContext.blockEmission(),
                BlockRenderContext.blockAlpha(),
                BlockRenderContext.blockEntityId(),
                BlockRenderContext.bloomMaskFallback(),
                detail
        );
    }

    public void probeShaderlessLightState(String stage) {
        // Probe disabled.
    }

    protected String shaderlessWorldLightSummary(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.player(mc) == null) {
            return "none";
        }
        BlockPos feet = new BlockPos(MinecraftReflectionCompat.player(mc));
        BlockPos eye = new BlockPos(MinecraftReflectionCompat.posX(MinecraftReflectionCompat.player(mc)), MinecraftReflectionCompat.posY(MinecraftReflectionCompat.player(mc)) + MinecraftReflectionCompat.eyeHeight(MinecraftReflectionCompat.player(mc)), MinecraftReflectionCompat.posZ(MinecraftReflectionCompat.player(mc)));
        return "feet{" + self().shaderlessWorldLightAt(MinecraftReflectionCompat.world(mc), feet) + "}"
                + ",eye{" + self().shaderlessWorldLightAt(MinecraftReflectionCompat.world(mc), eye) + "}";
    }

    protected String shaderlessWorldLightAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return "none";
        }
        try {
            boolean loaded = MinecraftReflectionCompat.worldIsBlockLoaded(world, pos);
            int combined = loaded ? MinecraftReflectionCompat.callInt(world, new String[]{"func_175626_b", "getCombinedLight"},
                    new Class<?>[]{BlockPos.class, int.class}, 0, pos, 0) : -1;
            int sky = loaded ? MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.SKY, pos) : -1;
            int block = loaded ? MinecraftReflectionCompat.worldLightFor(world, EnumSkyBlock.BLOCK, pos) : -1;
            boolean canSeeSky = loaded && MinecraftReflectionCompat.callBoolean(world, new String[]{"func_175678_i", "canSeeSky"},
                    new Class<?>[]{BlockPos.class}, false, pos);
            int dynamic = DynamicLightManager.lightAt(pos);
            return "pos=" + pos
                    + ",loaded=" + loaded
                    + ",sky=" + sky
                    + ",block=" + block
                    + ",combined=0x" + Integer.toHexString(combined)
                    + ",canSeeSky=" + canSeeSky
                    + ",dyn=" + dynamic;
        } catch (RuntimeException | LinkageError e) {
            return "pos=" + pos + ",error=" + e.getClass().getName();
        }
    }

    public void probeShaderlessSkyGuiState(String stage) {
        // Probe disabled.
    }

    public void freshSkyProbe(String stage, String detail) {
        // Probe disabled.
    }

    protected String freshSkySamples(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.displayWidth(mc) <= 0 || MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        try {
            int width = MinecraftReflectionCompat.displayWidth(mc);
            int height = MinecraftReflectionCompat.displayHeight(mc);
            return "center=" + self().readFramebufferPixel(width / 2, height / 2)
                    + ";upper=" + self().readFramebufferPixel(width / 2, Math.max(0, height * 3 / 4))
                    + ";lower=" + self().readFramebufferPixel(width / 2, Math.max(0, height / 4));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        }
    }

    protected boolean shouldLogShaderedVoidSkyProbe() {
        return false;
    }

    public void logVoidSkyStageProbe(String stage, String detail) {
        // Probe disabled.
    }

    protected void logShaderedVoidSkyTargetProbe(String stage, Framebuffer target) {
        if (!DEBUG_PROBES_ENABLED
                || !isPipelineActive
                || target == null
                || stage == null
                || !stage.contains("final")
                || shaderedVoidSkyTargetProbeLogs++ >= 12) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        MainMod.LOGGER.info(
                "[AUSMShaderedVoidSkyTargetProbe] call={} stage={} screen={} paused={} target={} color={} depth={} drawFbo={} readFbo={} drawBuf={} readBuf={}",
                shaderedVoidSkyTargetProbeLogs,
                stage,
                mc != null && MinecraftReflectionCompat.currentScreen(mc) != null
                        ? MinecraftReflectionCompat.currentScreen(mc).getClass().getName() : "none",
                mc != null && MinecraftReflectionCompat.isGamePaused(mc),
                self().describeFramebufferTargetDetailed(target),
                self().framebufferSamples(target),
                self().framebufferDepthSamples(target),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER)
        );
    }

    protected void logShaderedVoidSkyAttachmentProbe(String stage, String detail) {
        // Probe disabled.
    }

    public void probeShaderedPresentationState(String stage) {
        // Probe disabled.
    }

    protected void logSkyDomeProbe(String stage, String detail, Framebuffer target) {
        // Probe disabled.
    }

    protected void logWorldPassSkyDomeProbe(String stage) {
        // Probe disabled.
    }

    protected boolean claimSkyDomeProbeBudget(Minecraft mc) {
        String tier = self().skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return skyDomePauseProbeLogs++ < MAX_SKY_DOME_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return skyDomeGuiProbeLogs++ < MAX_SKY_DOME_GUI_PROBE_LOGS;
        }
        return skyDomeProbeLogs++ < MAX_SKY_DOME_WORLD_PROBE_LOGS;
    }

    protected boolean claimWorldPassSkyDomeProbeBudget(Minecraft mc) {
        String tier = self().skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return worldPassSkyDomePauseProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return worldPassSkyDomeGuiProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_GUI_PROBE_LOGS;
        }
        return worldPassSkyDomeProbeLogs++ < MAX_WORLD_PASS_SKY_DOME_WORLD_PROBE_LOGS;
    }

    protected boolean claimShaderlessSolidTerrainSkyProbeBudget(Minecraft mc) {
        String tier = self().skyProbeBudgetTier(mc);
        if ("pause".equals(tier)) {
            return shaderlessSolidTerrainSkyPauseProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_PAUSE_PROBE_LOGS;
        }
        if ("gui".equals(tier)) {
            return shaderlessSolidTerrainSkyGuiProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_GUI_PROBE_LOGS;
        }
        return shaderlessSolidTerrainSkyProbeLogs++ < MAX_SHADERLESS_SOLID_TERRAIN_SKY_WORLD_PROBE_LOGS;
    }

    protected String skyProbeBudgetTier(Minecraft mc) {
        if (mc != null && MinecraftReflectionCompat.isGamePaused(mc)) {
            return "pause";
        }
        if (self().isGuiSkyProbeState(mc)) {
            return "gui";
        }
        return "world";
    }

    protected boolean isGuiSkyProbeState(Minecraft mc) {
        return self().renderingGuiScreen()
                || mc != null
                && (MinecraftReflectionCompat.currentScreen(mc) != null
                || MinecraftReflectionCompat.gameSettings(mc) != null && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)));
    }

    protected void logShaderlessSolidTerrainSkyProbe(String stage) {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (isPipelineActive
                || mc == null
                || MinecraftReflectionCompat.world(mc) == null
                || MinecraftReflectionCompat.renderViewEntity(mc) == null
                || self().isIgnoredShaderlessSkyProbeScreen(mc)
                || !self().isOverworldShaderEnvironment(MinecraftReflectionCompat.world(mc))
                || !self().claimShaderlessSolidTerrainSkyProbeBudget(mc)) {
            return;
        }

        String budget = self().skyProbeBudgetTier(mc);
        int drawFramebuffer = self().currentDrawFramebufferBinding();
        int readFramebuffer = self().currentReadFramebufferBinding();
        int drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int readBufferId = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        MainMod.LOGGER.info("[AUSMShaderlessSolidTerrainSkyProbe] stage={} budget={} active={} shaderless={} worldFrame={} pass={} phase={} gui={} screen={} hideGUI={} paused={} world={} sky={} camera={} rays={} gl={} mcTarget={} mcColor={} mcDepth={} drawFbo={} drawBuf={} drawColor={} drawDepth={} readFbo={} readBuf={} readColor={} readDepth={} terrainCounts=solid:{},cutoutMipped:{},cutout:{},translucent:{}",
                stage,
                budget,
                isPipelineActive,
                !isPipelineActive,
                worldFrameActive,
                activePass,
                self().getPhase(),
                self().renderingGuiScreen(),
                MinecraftReflectionCompat.currentScreen(mc) != null ? MinecraftReflectionCompat.currentScreen(mc).getClass().getName() : "none",
                MinecraftReflectionCompat.gameSettings(mc) != null && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(mc)),
                MinecraftReflectionCompat.isGamePaused(mc),
                self().skyProbeWorldSummary(),
                self().skyDomeSceneSummary(mc),
                self().skyDomeCameraSummary(mc),
                self().shaderlessSolidTerrainSampleRays(mc),
                self().skyDomeGlStateSummary(),
                self().describeFramebufferTargetDetailed(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                self().framebufferSamples(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                self().framebufferDepthSamples(MinecraftReflectionCompat.minecraftFramebuffer(mc)),
                drawFramebuffer,
                drawBuffer,
                self().framebufferIdColorSamples(drawFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), self().normalizedReadBuffer(drawFramebuffer, drawBuffer)),
                self().framebufferIdDepthSamples(drawFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), self().normalizedReadBuffer(drawFramebuffer, drawBuffer)),
                readFramebuffer,
                readBufferId,
                self().framebufferIdColorSamples(readFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), self().normalizedReadBuffer(readFramebuffer, readBufferId)),
                self().framebufferIdDepthSamples(readFramebuffer, MinecraftReflectionCompat.displayWidth(mc), MinecraftReflectionCompat.displayHeight(mc), self().normalizedReadBuffer(readFramebuffer, readBufferId)),
                shaderlessTerrainSolidCount,
                shaderlessTerrainCutoutMippedCount,
                shaderlessTerrainCutoutCount,
                shaderlessTerrainTranslucentCount);
    }

    protected String shaderlessSolidTerrainSampleRays(Minecraft mc) {
        if (mc == null || MinecraftReflectionCompat.world(mc) == null || MinecraftReflectionCompat.displayWidth(mc) <= 0 || MinecraftReflectionCompat.displayHeight(mc) <= 0) {
            return "none";
        }
        Entity view = MinecraftReflectionCompat.renderViewEntity(mc);
        if (view == null) {
            return "view=null";
        }

        int height = MinecraftReflectionCompat.displayHeight(mc);
        int x = Math.max(0, MinecraftReflectionCompat.displayWidth(mc) / 2);
        String[] names = {"bottomSky", "lower", "center", "upper", "topDome"};
        int[] ys = {
                Math.clamp(height / 16, 0, height - 1),
                Math.clamp(height * 3 / 16, 0, height - 1),
                Math.clamp(height / 2, 0, height - 1),
                Math.clamp(height * 13 / 16, 0, height - 1),
                Math.clamp(height * 15 / 16, 0, height - 1)
        };
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(names[i])
                    .append('=')
                    .append(x)
                    .append(',')
                    .append(ys[i])
                    .append(',')
                    .append(self().shaderlessSolidTerrainRayHit(mc, view, ys[i], height));
        }
        return builder.toString();
    }

    protected String shaderlessSolidTerrainRayHit(Minecraft mc, Entity view, int y, int height) {
        try {
            float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
            double eyeX = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosX(view), MinecraftReflectionCompat.posX(view), partialTicks);
            double eyeY = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosY(view), MinecraftReflectionCompat.posY(view), partialTicks) + MinecraftReflectionCompat.eyeHeight(view);
            double eyeZ = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosZ(view), MinecraftReflectionCompat.posZ(view), partialTicks);
            Vec3d start = new Vec3d(eyeX, eyeY, eyeZ);
            Vec3d direction = self().shaderlessProbeScreenRayDirection(mc, view, y, height);
            Vec3d end = MinecraftReflectionCompat.vecAdd(start, MinecraftReflectionCompat.vecScale(direction, 512.0D));
            RayTraceResult hit = MinecraftReflectionCompat.call(MinecraftReflectionCompat.world(mc), RayTraceResult.class, null, new String[]{"func_147447_a", "rayTraceBlocks"},
                    new Class<?>[]{Vec3d.class, Vec3d.class, boolean.class, boolean.class, boolean.class},
                    start, end, false, true, false);
            if (hit == null || MinecraftReflectionCompat.field(hit, RayTraceResult.Type.class, null, "field_72313_a", "typeOfHit") != RayTraceResult.Type.BLOCK || MinecraftReflectionCompat.rayTraceBlockPos(hit) == null) {
                return "dir=" + PipelineRuntimeState.formatVec3d(direction) + ",hit=miss";
            }
            BlockPos pos = MinecraftReflectionCompat.rayTraceBlockPos(hit);
            IBlockState state = MinecraftReflectionCompat.worldBlockState(MinecraftReflectionCompat.world(mc), pos);
            return "dir=" + PipelineRuntimeState.formatVec3d(direction)
                    + ",hit=" + PipelineRuntimeState.formatBlockPos(pos)
                    + ",side=" + MinecraftReflectionCompat.field(hit, EnumFacing.class, null, "field_178784_b", "sideHit")
                    + ",block=" + PipelineRuntimeState.registryName(state)
                    + ",state=" + PipelineRuntimeState.stateName(state)
                    + ",dist=" + MinecraftReflectionCompat.vecDistance(start, MinecraftReflectionCompat.field(hit, Vec3d.class, null, "field_72307_f", "hitVec"));
        } catch (RuntimeException | LinkageError e) {
            return "error=" + e.getClass().getSimpleName();
        }
    }

    protected Vec3d shaderlessProbeScreenRayDirection(Minecraft mc, Entity view, int y, int height) {
        double ndcY = height <= 1 ? 0.0D : (y / (double) (height - 1)) * 2.0D - 1.0D;
        double fov = MinecraftReflectionCompat.gameSettings(mc) != null ? MinecraftReflectionCompat.fieldFloat(MinecraftReflectionCompat.gameSettings(mc), 70.0F, "field_74334_X", "fovSetting") : 70.0D;
        double verticalOffset = Math.toDegrees(Math.atan(ndcY * Math.tan(Math.toRadians(fov) * 0.5D)));
        double pitch = MinecraftReflectionCompat.rotationPitch(view) - verticalOffset;
        double yaw = MinecraftReflectionCompat.rotationYaw(view);
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return MinecraftReflectionCompat.vecNormalize(new Vec3d(
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch));
    }

    protected int normalizedReadBuffer(int framebuffer, int buffer) {
        if (buffer == GL11.GL_NONE && framebuffer == 0) {
            return GL11.GL_BACK;
        }
        return buffer;
    }

    protected String skyDomeSceneSummary(Minecraft mc) {
        World world = PipelineRuntimeState.renderWorld(mc);
        Entity view = mc != null ? MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (world == null) {
            return "world=null";
        }
        float partialTicks = mc != null ? MinecraftReflectionCompat.renderPartialTicks(mc) : 0.0F;
        String skyColor = "none";
        try {
            Vec3d color = view != null ? MinecraftReflectionCompat.call(world, Vec3d.class, null, new String[]{"func_72833_a", "getSkyColor"},
                    new Class<?>[]{Entity.class, float.class}, view, partialTicks) : null;
            skyColor = color != null ? PipelineRuntimeState.formatVec3d(color) : "null";
        } catch (RuntimeException | LinkageError e) {
            skyColor = "error=" + e.getClass().getSimpleName();
        }
        double horizon = Double.NaN;
        float cloudHeight = Float.NaN;
        try {
            if (MinecraftReflectionCompat.worldProvider(world) != null) {
                horizon = MinecraftReflectionCompat.callDouble(MinecraftReflectionCompat.worldProvider(world), new String[]{"func_76567_e", "getHorizon"}, MinecraftReflectionCompat.NO_PARAMETERS, 63.0D);
                cloudHeight = MinecraftReflectionCompat.callFloat(MinecraftReflectionCompat.worldProvider(world), new String[]{"func_76571_f", "getCloudHeight"}, MinecraftReflectionCompat.NO_PARAMETERS, 128.0F);
            }
        } catch (RuntimeException | LinkageError ignored) {
            horizon = Double.NaN;
        }
        return "skyColor=" + skyColor
                + ",celestialPartial=" + MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks)
                + ",celestial0=" + MinecraftReflectionCompat.worldCelestialAngle(world, 0.0F)
                + ",sunBrightness=" + MinecraftReflectionCompat.callFloat(world, new String[]{"func_72971_b", "getSunBrightness"},
                new Class<?>[]{float.class}, 0.0F, partialTicks)
                + ",starBrightness=" + MinecraftReflectionCompat.callFloat(world, new String[]{"func_72880_h", "getStarBrightness"},
                new Class<?>[]{float.class}, 0.0F, partialTicks)
                + ",rain=" + MinecraftReflectionCompat.worldRainStrength(world, partialTicks)
                + ",thunder=" + MinecraftReflectionCompat.worldThunderStrength(world, partialTicks)
                + ",day=" + self().dayHelper(mc)
                + ",night=" + self().nightHelper(mc)
                + ",dawnDusk=" + ((1.0F - self().dayHelper(mc)) - self().nightHelper(mc))
                + ",horizon=" + horizon
                + ",cloudHeight=" + cloudHeight;
    }

    protected String skyDomeCameraSummary(Minecraft mc) {
        Entity view = mc != null ? MinecraftReflectionCompat.renderViewEntity(mc) : null;
        if (view == null) {
            return "view=null";
        }
        float partialTicks = MinecraftReflectionCompat.renderPartialTicks(mc);
        Vec3d look = MinecraftReflectionCompat.look(view, partialTicks);
        double x = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosX(view), MinecraftReflectionCompat.posX(view), partialTicks);
        double y = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosY(view), MinecraftReflectionCompat.posY(view), partialTicks);
        double z = PipelineRuntimeState.interpolate(MinecraftReflectionCompat.lastTickPosZ(view), MinecraftReflectionCompat.posZ(view), partialTicks);
        return "pos=" + x + "/" + y + "/" + z
                + ",eye=" + (y + MinecraftReflectionCompat.eyeHeight(view))
                + ",yaw=" + MinecraftReflectionCompat.rotationYaw(view)
                + ",pitch=" + MinecraftReflectionCompat.rotationPitch(view)
                + ",prevYaw=" + MinecraftReflectionCompat.prevRotationYaw(view)
                + ",prevPitch=" + MinecraftReflectionCompat.prevRotationPitch(view)
                + ",look=" + PipelineRuntimeState.formatVec3d(look)
                + ",verticalDelta=" + self().cameraVerticalDelta()
                + ",horizontalDelta=" + self().cameraHorizontalVelocityMagnitude();
    }

    protected static String formatVec3d(Vec3d value) {
        if (value == null) {
            return "null";
        }
        return MinecraftReflectionCompat.vecX(value) + "/" + MinecraftReflectionCompat.vecY(value) + "/" + MinecraftReflectionCompat.vecZ(value);
    }

    protected String skyDomeGlStateSummary() {
        FloatBuffer clearColor = BufferUtils.createFloatBuffer(4);
        IntBuffer viewport = BufferUtils.createIntBuffer(4);
        ByteBuffer colorMask = BufferUtils.createByteBuffer(4);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        return PipelineRuntimeState.skyProbeGlStateSummary()
                + ",program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                + ",viewport=" + viewport.get(0) + "/" + viewport.get(1) + "/" + viewport.get(2) + "/" + viewport.get(3)
                + ",clear=" + clearColor.get(0) + "/" + clearColor.get(1) + "/" + clearColor.get(2) + "/" + clearColor.get(3)
                + ",drawBuffer=" + GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
                + ",readBuffer=" + GL11.glGetInteger(GL11.GL_READ_BUFFER)
                + ",depthTest=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
                + ",depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                + ",blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
                + ",alpha=" + GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
                + ",fog=" + GL11.glIsEnabled(GL11.GL_FOG)
                + ",cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE)
                + ",colorMask=" + (colorMask.get(0) != 0) + "/" + (colorMask.get(1) != 0) + "/" + (colorMask.get(2) != 0) + "/" + (colorMask.get(3) != 0);
    }
}
