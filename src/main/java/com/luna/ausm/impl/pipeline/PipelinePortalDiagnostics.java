package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramArrayId;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.compat.NothiriumBypass;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.pipeline.shader.FullscreenArrayProgram;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_BETTER_PORTALS_PIPELINE_LOGS;

abstract class PipelinePortalDiagnostics extends PipelineFullscreenPassRendering {
    public String describeBetterPortalsDiagnostics() {
        return "active=" + isPipelineActive
                + " shaderlessWorldPass=" + shaderlessWorldPassActive
                + " worldFrame=" + worldFrameActive
                + " frame=" + pipelineFrameId
                + " activePass=" + activePass
                + " phase=" + activePhase
                + " shadow=" + renderingShadowMap
                + " deferred=" + deferredPassesRenderedThisFrame
                + " passStack=" + passStack.size()
                + " bypassStack=" + worldPassBypassStack.size()
                + "/" + (worldPassBypassStack.isEmpty() ? "empty" : worldPassBypassStack.peek())
                + " bpPass=" + self().isRenderingBetterPortalsRenderPass()
                + " bpNested=" + self().isRenderingBetterPortalsNestedView()
                + " bpNestedShaders=" + self().shouldRenderBetterPortalsNestedViewWithShaders()
                + " externalTarget=" + self().describeFramebufferTarget(externalWorldFramebufferTarget)
                + " read=" + self().describeDeferredFramebuffer(pingPongManager.getReadBuffer());
    }

    protected boolean computeShouldBypassWorldPassRendering() {
        return self().shouldLeaveBetterPortalsRenderPassUntouched()
                || self().isRenderingBetterPortalsNestedView() && !self().shouldRenderBetterPortalsNestedViewWithShaders();
    }

    protected boolean shouldLeaveBetterPortalsRenderPassUntouched() {
        return BetterPortalsCompat.isInstalled()
                && self().isRenderingBetterPortalsRenderPass()
                && (!isPipelineActive
                || self().isRenderingBetterPortalsNestedView() && !self().shouldRenderBetterPortalsNestedViewWithShaders());
    }

    protected String describeDeferredFramebuffer(DeferredFramebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return framebuffer.getFramebufferId()
                + "("
                + framebuffer.getWidth()
                + "x"
                + framebuffer.getHeight()
                + ", color="
                + framebuffer.getReadTexture(Attachment.COLOR)
                + ", depth="
                + framebuffer.getDepthTexture()
                + ")";
    }

    protected String describeFramebufferTarget(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }

        return MinecraftReflectionCompat.framebufferObject(framebuffer)
                + "("
                + MinecraftReflectionCompat.framebufferWidth(framebuffer)
                + "x"
                + MinecraftReflectionCompat.framebufferHeight(framebuffer)
                + ")";
    }

    protected void logBetterPortalsPipeline(String stage) {
        self().logBetterPortalsPipeline(stage, "");
    }

    protected void logBetterPortalsPipeline(String stage, String detail) {
        if (!self().shouldLogBetterPortalsPipeline(stage)) {
            return;
        }
        if (betterPortalsPipelineLogs >= MAX_BETTER_PORTALS_PIPELINE_LOGS) {
            return;
        }
        betterPortalsPipelineLogs++;

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World renderWorld = renderWorld(mc);
        World clientWorld = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        DeferredFramebuffer readBuffer = pingPongManager.getReadBuffer();
        PipelineProgram finalProgram = programs.get(RenderPass.FINAL);
        MainMod.LOGGER.info("[BetterPortalsPipeline] stage={} detail={} active={} worldFrame={} frame={} activePass={} phase={} renderWorld={} clientWorld={} nested={} renderPass={} nestedShaders={} externalTarget={} externalStatus={} read={} pack={} finalProgram={} compositePrograms={} finalComputes={} deferred={} setupComputePending={} gl={}",
                stage,
                detail,
                isPipelineActive,
                worldFrameActive,
                pipelineFrameId,
                activePass,
                activePhase,
                self().safeDimensionId(renderWorld),
                self().safeDimensionId(clientWorld),
                self().isRenderingBetterPortalsNestedView(),
                self().isRenderingBetterPortalsRenderPass(),
                self().shouldRenderBetterPortalsNestedViewWithShaders(),
                self().describeFramebufferTargetDetailed(externalWorldFramebufferTarget),
                self().framebufferStatus(externalWorldFramebufferTarget),
                self().describeDeferredFramebuffer(readBuffer),
                self().shaderPackDiagnostics(),
                self().describePipelineProgram(finalProgram),
                self().countCompositePrograms(),
                finalComputePrograms.size(),
                deferredPassesRenderedThisFrame,
                setupComputePending,
                self().describeCurrentGlTarget());
    }

    protected boolean shouldLogBetterPortalsPipeline(String stage) {
        if (!BetterPortalsCompat.isInstalled()) {
            return false;
        }
        if (!isPipelineActive
                && self().isRenderingBetterPortalsRenderPass()
                && !self().isRenderingBetterPortalsNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && ("world-pass-begin".equals(stage) || "world-pass-finish".equals(stage))) {
            return false;
        }
        return self().isRenderingBetterPortalsNestedView()
                || self().isRenderingBetterPortalsRenderPass()
                || isBetterPortalsExternalWorldTarget()
                || BetterPortalsCompat.isMainViewSwapRecoveryActive();
    }

    protected String describeFramebufferTargetDetailed(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        return MinecraftReflectionCompat.framebufferObject(framebuffer)
                + "("
                + MinecraftReflectionCompat.framebufferWidth(framebuffer)
                + "x"
                + MinecraftReflectionCompat.framebufferHeight(framebuffer)
                + ", tex="
                + MinecraftReflectionCompat.framebufferTexture(framebuffer)
                + ", texSize="
                + MinecraftReflectionCompat.fieldInt(framebuffer, 0, "field_147622_a", "framebufferTextureWidth")
                + "x"
                + MinecraftReflectionCompat.fieldInt(framebuffer, 0, "field_147620_b", "framebufferTextureHeight")
                + ", depth="
                + MinecraftReflectionCompat.fieldInt(framebuffer, 0, "field_147624_h", "depthBuffer")
                + ")";
    }

    protected String framebufferStatus(Framebuffer framebuffer) {
        if (framebuffer == null) {
            return "null";
        }
        if (!MinecraftReflectionCompat.isFramebufferEnabled()) {
            return "disabled";
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, MinecraftReflectionCompat.framebufferObject(framebuffer));
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            return status == GL30.GL_FRAMEBUFFER_COMPLETE ? "complete" : "0x" + Integer.toHexString(status);
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    protected String describePipelineProgram(PipelineProgram program) {
        if (program == null) {
            return "null";
        }
        return "enabled=" + program.enabled() + ", own=" + program.hasOwnProgram();
    }

    protected long countCompositePrograms() {
        return fullscreenArrayPrograms
                .getOrDefault(ProgramArrayId.COMPOSITE, List.of())
                .stream()
                .filter(FullscreenArrayProgram::hasProgram)
                .count();
    }

    protected String shaderPackDiagnostics() {
        return MainMod.getShaderPackManager() != null
                ? MainMod.getShaderPackManager().describeBetterPortalsPipelineState()
                : "shaderManager=null";
    }

    protected String describeCurrentGlTarget() {
        try {
            return "fbo=" + GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                    + ", readFb=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                    + ", drawFb=" + GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                    + ", drawBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_DRAW_BUFFER))
                    + ", readBuf=0x" + Integer.toHexString(GL11.glGetInteger(GL11.GL_READ_BUFFER))
                    + ", program=" + GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
                    + ", viewport=" + self().currentViewportSummary();
        } catch (RuntimeException error) {
            return "error:" + error.getClass().getSimpleName();
        }
    }

    protected String currentViewportSummary() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        return "["
                + viewportBuffer.get(0)
                + ","
                + viewportBuffer.get(1)
                + ","
                + viewportBuffer.get(2)
                + ","
                + viewportBuffer.get(3)
                + "]";
    }

    public boolean prepareRenderGlobalChunkUpdates(RenderGlobal renderGlobal) {
        if (renderGlobal == null) {
            return false;
        }

        if (self().shouldLeaveBetterPortalsRenderPassUntouched()) {
            return true;
        }

        boolean betterPortalsProtectedPass = BetterPortalsCompat.isInstalled()
                && BetterPortalsCompat.isRenderingNestedView();
        if (!betterPortalsProtectedPass) {
            return true;
        }

        if (!(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return true;
        }

        World renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld == null) {
            Minecraft mc = MinecraftReflectionCompat.minecraft();
            renderPassWorld = mc != null ? MinecraftReflectionCompat.world(mc) : null;
        }
        if (renderPassWorld == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no active render-pass world");
            return false;
        }
        ensureVanillaTerrainRenderer(renderPassWorld, true);
        if (accessor.ausm$world() == null) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates with no RenderGlobal world after sync");
            return false;
        }
        if (accessor.ausm$world() != renderPassWorld) {
            MainMod.LOGGER.debug("[BetterPortalsCompat] Skipped chunk updates for mismatched render-pass world: renderGlobal={} pass={}",
                    self().safeDimensionId(accessor.ausm$world()),
                    self().safeDimensionId(renderPassWorld));
            return false;
        }

        return self().hasOnlyValidBetterPortalsChunkUpdates(accessor, renderPassWorld);
    }

    public boolean handleBetterPortalsChunkUpdateFailure(RenderGlobal renderGlobal, NullPointerException exception) {
        if (!BetterPortalsCompat.isInstalled()
                || !(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            return false;
        }
        if (!self().isBetterPortalsChunkUpdateNullWorldFailure(exception)
                && !BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()
                && accessor.ausm$world() != null
                && !self().hasInvalidBetterPortalsChunkUpdate(accessor)) {
            return false;
        }

        self().clearRenderGlobalChunkUpdates(accessor);
        if (!betterPortalsChunkUpdateWarningLogged) {
            betterPortalsChunkUpdateWarningLogged = true;
            MainMod.LOGGER.warn("[BetterPortalsCompat] Dropped stale nested chunk update after Better Portals exposed a RenderChunk with no world", exception);
        }
        return true;
    }

    public World betterPortalsRenderChunkFallbackWorld() {
        if (!BetterPortalsCompat.isInstalled()) {
            return null;
        }

        WorldClient renderPassWorld = BetterPortalsCompat.currentRenderPassWorld();
        if (renderPassWorld != null) {
            return renderPassWorld;
        }

        if (!BetterPortalsCompat.isRenderingNestedView()
                && !BetterPortalsCompat.isRenderingRenderPass()
                && !BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            return null;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        return mc != null ? MinecraftReflectionCompat.world(mc) : null;
    }

    protected boolean isBetterPortalsChunkUpdateNullWorldFailure(NullPointerException exception) {
        if (exception == null) {
            return false;
        }
        for (StackTraceElement frame : exception.getStackTrace()) {
            String className = frame.getClassName();
            if ("net.minecraft.world.ChunkCache".equals(className)
                    || "net.minecraft.client.renderer.chunk.RenderChunk".equals(className)
                    || "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher".equals(className)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasOnlyValidBetterPortalsChunkUpdates(RenderGlobalAccessor accessor, World allowedWorld) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (!self().isValidBetterPortalsChunkUpdate(chunk, allowedWorld)) {
                MainMod.LOGGER.debug("[BetterPortalsCompat] Deferred nested chunk updates because the queue contains work for another world");
                return false;
            }
        }
        return true;
    }

    protected boolean isValidBetterPortalsChunkUpdate(RenderChunk chunk, World allowedWorld) {
        if (chunk == null) {
            return false;
        }

        World chunkWorld = self().renderChunkWorld(chunk);
        if (chunkWorld == null) {
            return self().assignRenderChunkWorld(chunk, allowedWorld);
        }
        return chunkWorld == allowedWorld;
    }

    protected boolean hasInvalidBetterPortalsChunkUpdate(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate == null || chunksToUpdate.isEmpty()) {
            return false;
        }

        for (RenderChunk chunk : chunksToUpdate) {
            if (chunk == null || self().renderChunkWorld(chunk) == null) {
                return true;
            }
        }
        return false;
    }

    protected World renderChunkWorld(RenderChunk chunk) {
        return MinecraftReflectionCompat.renderChunkWorld(chunk);
    }

    protected boolean assignRenderChunkWorld(RenderChunk chunk, World world) {
        if (chunk == null || world == null) {
            return false;
        }
        try {
            MinecraftReflectionCompat.setField(chunk, world, "field_178588_d", "world");
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    protected void clearRenderGlobalChunkUpdates(RenderGlobalAccessor accessor) {
        Set<RenderChunk> chunksToUpdate = accessor.ausm$chunksToUpdate();
        if (chunksToUpdate != null && !chunksToUpdate.isEmpty()) {
            chunksToUpdate.clear();
        }
    }

    protected int safeDimensionId(World world) {
        WorldProvider provider =
                MinecraftReflectionCompat.worldProvider(world);
        return provider != null
                ? MinecraftReflectionCompat.providerDimension(provider)
                : Integer.MIN_VALUE;
    }

    protected boolean isOverworldShaderEnvironment(World world) {
        int dimensionId = self().safeDimensionId(world);
        return dimensionId != Integer.MIN_VALUE
                && dimensionId != -1
                && dimensionId != 1;
    }

    protected String describeWorld(World world) {
        if (world == null) {
            return "null";
        }
        return "dim=" + self().safeDimensionId(world) + ", id=" + System.identityHashCode(world);
    }

    public void prepareBypassedWorldPassRendering() {
        if (self().shouldLeaveBetterPortalsRenderPassUntouched()) {
            untouchedBetterPortalsVanillaRendererStack.push(self().prepareUntouchedBetterPortalsRenderPass());
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare untouched-bp-pass");
            return;
        }
        untouchedBetterPortalsVanillaRendererStack.push(false);
        boolean nestedBetterPortalsView = self().isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !self().shouldRenderBetterPortalsNestedViewWithShaders();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-prepare nested=" + nestedBetterPortalsView
                + " vanillaRenderer=" + useNestedVanillaRenderer);
        if (!isPipelineActive && !nestedBetterPortalsView) {
            prepareInactiveVanillaFrame();
        }
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? MinecraftReflectionCompat.world(mc) : null);
        if (useNestedVanillaRenderer || BetterPortalsCompat.isMainViewSwapRecoveryActive()) {
            ensureVanillaTerrainRenderer(targetWorld, true);
        } else if (!nestedBetterPortalsView) {
            ensureVanillaTerrainRenderer(targetWorld);
        }

        restoreVanillaWorldPassState(!nestedBetterPortalsView, !nestedBetterPortalsView);
    }

    public void finishBypassedWorldPassRendering() {
        if (self().shouldLeaveBetterPortalsRenderPassUntouched()) {
            if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()
                    && untouchedBetterPortalsVanillaRendererStack.pop()) {
                popVanillaTerrainRendererState();
            }
            BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish untouched-bp-pass");
            return;
        }
        if (!untouchedBetterPortalsVanillaRendererStack.isEmpty()) {
            untouchedBetterPortalsVanillaRendererStack.pop();
        }
        boolean nestedBetterPortalsView = self().isRenderingBetterPortalsNestedView();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-before");
        restoreVanillaWorldPassState(false, !nestedBetterPortalsView);
        popVanillaTerrainRendererState();
        shaderlessWorldPassActive = false;
        self().restoreActiveWorldPassAfterExternalShader();
        BetterPortalsCompat.logRenderStateDiagnostic("pipeline:bypass-finish-after");
    }

    protected boolean prepareUntouchedBetterPortalsRenderPass() {
        boolean nestedBetterPortalsView = self().isRenderingBetterPortalsNestedView();
        boolean useNestedVanillaRenderer = nestedBetterPortalsView
                && !self().shouldRenderBetterPortalsNestedViewWithShaders();
        boolean mustEnsureVanillaRenderer = useNestedVanillaRenderer || NothiriumBypass.shouldBypass();
        if (!mustEnsureVanillaRenderer) {
            return false;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        World world = BetterPortalsCompat.currentRenderPassWorld();
        World targetWorld = world != null ? world : (mc != null ? MinecraftReflectionCompat.world(mc) : null);
        if (useNestedVanillaRenderer) {
            pushVanillaTerrainRendererState();
        }
        ensureVanillaTerrainRenderer(targetWorld, useNestedVanillaRenderer);
        return useNestedVanillaRenderer;
    }

    protected void renderNativeBloomLayerIfNeeded() {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()
                || renderingGuiScreen()) {
            return;
        }
        if (self().isRenderingBetterPortalsRenderPass()) {
            self().requestDeferredNativeBloom(currentWorldPartialTicks, currentWorldPass);
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        self().renderAusmBloomLayer(
                MinecraftReflectionCompat.renderGlobal(mc),
                currentWorldPartialTicks,
                currentWorldPass,
                MinecraftReflectionCompat.renderViewEntity(mc)
        );
    }

    public void renderNativeAusmBloomLayerFromWorldPass(float partialTicks, int pass) {
        if (bloomLayerRenderedThisWorldPass
                || !AusmBloomLayer.shouldUseNativeHook()) {
            return;
        }
        if (self().isRenderingBetterPortalsRenderPass()) {
            self().requestDeferredNativeBloom(partialTicks, pass);
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.renderGlobal(mc) == null) {
            return;
        }

        currentWorldPass = pass;
        currentWorldPartialTicks = partialTicks;
        self().renderAusmBloomLayer(MinecraftReflectionCompat.renderGlobal(mc), partialTicks, pass, MinecraftReflectionCompat.renderViewEntity(mc));
    }
}
