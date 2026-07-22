package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.pack.ShaderPackManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to capture window resize events.
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private boolean ausm$hadWorldBeforeLoad;

    @Unique
    private int ausm$previousWorldDimensionId = Integer.MIN_VALUE;

    // Minecraft 1.12.2 handles resizing via 'resize(int width, int height)'
    @Inject(method = "resize(II)V", at = @At("RETURN"))
    private void onResize(int width, int height, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (!com.l.ausm.impl.util.MinecraftReflectionCompat.callBoolean((mc), new String[] {"func_152345_ab", "isCallingFromMinecraftThread"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(mc, () -> ausm$resizePipeline(width, height));
            return;
        }
        ausm$resizePipeline(width, height);
    }

    @Unique
    private static void ausm$resizePipeline(int width, int height) {
        if (PipelineContext.getInstance().isActive()) {
            PipelineContext.getInstance().resize(width, height);
        }
    }

    @Inject(
            method = "runGameLoop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V")
    )
    private void ausm$beforeFramebufferPresentation(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        Framebuffer framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int width = com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc);
        int height = com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc);
        context.logFramebufferPresentationBoundary("runGameLoop-before-prepare", framebuffer, width, height, true);
        context.clearWorldLoadWindowBackbuffer(mc);
        context.prepareFramebufferPresentation();
        context.logFramebufferPresentationBoundary("runGameLoop-after-prepare-before-vanilla", framebuffer, width, height, true);
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void ausm$runScheduledWork(CallbackInfo ci) {
        BetterPortalsCompat.tickMainViewSwapRecovery();
        ShaderPackManager shaderPackManager = MainMod.getShaderPackManager();
        if (shaderPackManager != null) {
            shaderPackManager.runPendingBetterPortalsDimensionCompile();
        }
        PipelineContext.getInstance().runPendingBetterPortalsPortalBlockRefresh();
        PipelineContext.getInstance().runPendingShaderChunkRefreshes();
        PipelineContext.getInstance().runPendingClientChunkRenderRefreshes();
        PipelineContext.getInstance().runScheduledBloomTerrainRefresh();
        PipelineContext.getInstance().runRenderDistanceChangeCheck();
        PipelineContext.getInstance().runScheduledWorldTerrainRefresh();
        PipelineContext.getInstance().runScheduledWorldLoadLightRecalculation();
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void ausm$recoverAfterResourcePackReload(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        com.l.ausm.impl.util.MinecraftReflectionCompat.addScheduledTask(mc, () -> PipelineContext.getInstance().handleResourcePackReload());
    }

    @Inject(
            method = "runGameLoop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterFramebufferPresentation(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        Minecraft mc = (Minecraft) (Object) this;
        if (com.l.ausm.impl.util.MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        Framebuffer framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int width = com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc);
        int height = com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc);
        context.logFramebufferPresentationBoundary("runGameLoop-after-vanilla-before-direct", framebuffer, width, height, true);
        if (context.isActive() && context.shouldDirectPresentFramebuffer()) {
            context.presentFramebufferDirectly(framebuffer, width, height);
            context.logFramebufferPresentationBoundary("runGameLoop-after-direct", framebuffer, width, height, true);
        }
    }

    @Inject(method = "func_175601_h()V", at = @At("HEAD"), remap = false, require = 0)
    private void ausm$presentShaderlessFramebufferBeforeDisplayUpdate(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldDirectPresentFramebuffer()) {
            Minecraft mc = (Minecraft) (Object) this;
            Framebuffer framebuffer = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(mc);
            context.logFramebufferPresentationBoundary("runGameLoop-before-shaderless-direct", framebuffer,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), true);
            context.presentFramebufferDirectly(
                    framebuffer,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc)
            );
            context.logFramebufferPresentationBoundary("runGameLoop-after-shaderless-direct", framebuffer,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(mc),
                    com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(mc), true);
        }
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void ausm$captureWorldBeforeLoad(WorldClient worldClient, String loadingMessage, CallbackInfo ci) {
        WorldClient currentWorld = com.l.ausm.impl.util.MinecraftReflectionCompat.world((Minecraft) (Object) this);
        ausm$hadWorldBeforeLoad = currentWorld != null;
        ausm$previousWorldDimensionId = ausm$dimensionId(currentWorld);
        PipelineContext.getInstance().invalidateWorldLoadPresentationState();
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("RETURN"))
    private void ausm$scheduleLightRefreshAfterWorldLoad(WorldClient worldClient, String loadingMessage, CallbackInfo ci) {
        if (worldClient == null) {
            PipelineContext context = PipelineContext.getInstance();
            context.invalidateWorldLoadPresentationState();
            context.clearClientParticles("world-unload");
            context.clearPendingShaderChunkRefreshes();
            context.clearPendingClientChunkRenderRefreshes();
            context.clearScheduledWorldLoadLightRecalculation();
            context.clearScheduledBloomTerrainRefresh();
            context.clearScheduledWorldTerrainRefresh();
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        context.invalidateWorldLoadPresentationState();
        context.clearPendingShaderChunkRefreshes();
        context.clearPendingClientChunkRenderRefreshes();
        if (MainMod.getShaderPackManager() == null) {
            context.scheduleWorldTerrainRefresh();
            context.rebuildShaderlessBloomTerrain("world load");
            return;
        }

        int dimensionId = ausm$dimensionId(worldClient);
        boolean dimensionSwitch = ausm$isDimensionSwitch(dimensionId);
        if (dimensionSwitch) {
            context.handleWorldDimensionSwitch(ausm$previousWorldDimensionId, dimensionId);
            MainMod.getShaderPackManager().compilePipelineForDimensionSwitch(dimensionId);
        } else {
            context.scheduleWorldTerrainRefresh();
            context.rebuildShaderlessBloomTerrain("world load");
            MainMod.getShaderPackManager().preparePipelineForWorldLoad(dimensionId);
            context.scheduleWorldLoadLightRecalculation();
        }
    }

    @Unique
    private boolean ausm$isDimensionSwitch(int dimensionId) {
        return ausm$hadWorldBeforeLoad
                && ausm$previousWorldDimensionId != Integer.MIN_VALUE
                && dimensionId != Integer.MIN_VALUE
                && ausm$previousWorldDimensionId != dimensionId;
    }

    @Unique
    private int ausm$dimensionId(WorldClient worldClient) {
        return worldClient != null && com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(worldClient) != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(worldClient))
                : Integer.MIN_VALUE;
    }
}
