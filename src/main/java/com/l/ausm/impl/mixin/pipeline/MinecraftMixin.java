package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
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
        if (PipelineContext.getInstance().isActive()) {
            PipelineContext.getInstance().resize(width, height);
        }
    }

    @Inject(
            method = "runGameLoop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V")
    )
    private void ausm$beforeFramebufferPresentation(CallbackInfo ci) {
        PipelineContext.getInstance().prepareFramebufferPresentation();
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void ausm$runScheduledWork(CallbackInfo ci) {
        BetterPortalsCompat.tickMainViewSwapRecovery();
        MainMod.getShaderPackManager().runPendingBetterPortalsDimensionCompile();
        PipelineContext.getInstance().runPendingBetterPortalsPortalBlockRefresh();
        PipelineContext.getInstance().runPendingShaderChunkRefreshes();
        PipelineContext.getInstance().runScheduledBloomTerrainRefresh();
        PipelineContext.getInstance().runScheduledWorldLoadLightRecalculation();
    }

    @Inject(
            method = "runGameLoop",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/Framebuffer;framebufferRender(II)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterFramebufferPresentation(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        Minecraft mc = (Minecraft) (Object) this;
        if (context.shouldDirectPresentFramebuffer()) {
            context.presentFramebufferDirectly(mc.getFramebuffer(), mc.displayWidth, mc.displayHeight);
            if (mc.currentScreen == null) {
                context.beginDeferredIngameHud();
                mc.ingameGUI.renderGameOverlay(mc.getRenderPartialTicks());
                context.endDeferredIngameHud();
            }
        }
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void ausm$captureWorldBeforeLoad(WorldClient worldClient, String loadingMessage, CallbackInfo ci) {
        WorldClient currentWorld = ((Minecraft) (Object) this).world;
        ausm$hadWorldBeforeLoad = currentWorld != null;
        ausm$previousWorldDimensionId = ausm$dimensionId(currentWorld);
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("RETURN"))
    private void ausm$scheduleLightRefreshAfterWorldLoad(WorldClient worldClient, String loadingMessage, CallbackInfo ci) {
        if (worldClient == null) {
            PipelineContext.getInstance().clearPendingShaderChunkRefreshes();
            PipelineContext.getInstance().clearScheduledWorldLoadLightRecalculation();
            PipelineContext.getInstance().clearScheduledBloomTerrainRefresh();
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        context.clearPendingShaderChunkRefreshes();
        context.scheduleBloomTerrainRefresh("world load");
        if (MainMod.getShaderPackManager() != null) {
            int dimensionId = ausm$dimensionId(worldClient);
            boolean dimensionSwitch = ausm$isDimensionSwitch(dimensionId);
            if (!dimensionSwitch) {
                MainMod.getShaderPackManager().preparePipelineForWorldLoad(dimensionId);
                context.scheduleWorldLoadLightRecalculation();
            } else {
                context.clearScheduledWorldLoadLightRecalculation();
            }
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
        return worldClient != null && worldClient.provider != null
                ? worldClient.provider.getDimension()
                : Integer.MIN_VALUE;
    }
}
