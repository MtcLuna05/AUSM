package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.pipeline.pack.ShaderPackManager;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
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
        if (!MinecraftReflectionCompat.callBoolean(mc, new String[]{"func_152345_ab", "isCallingFromMinecraftThread"}, MinecraftReflectionCompat.NO_PARAMETERS, false)) {
            MinecraftReflectionCompat.addScheduledTask(mc, () -> ausm$resizePipeline(width, height));
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
        if (MinecraftReflectionCompat.world(mc) == null) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        Framebuffer framebuffer = MinecraftReflectionCompat.minecraftFramebuffer(mc);
        int width = MinecraftReflectionCompat.displayWidth(mc);
        int height = MinecraftReflectionCompat.displayHeight(mc);
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
        MinecraftReflectionCompat.addScheduledTask(mc, () -> PipelineContext.getInstance().handleResourcePackReload());
    }

    @Inject(method = "loadWorld(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V", at = @At("HEAD"))
    private void ausm$captureWorldBeforeLoad(WorldClient worldClient, String loadingMessage, CallbackInfo ci) {
        WorldClient currentWorld = MinecraftReflectionCompat.world((Minecraft) (Object) this);
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
        return worldClient != null && MinecraftReflectionCompat.worldProvider(worldClient) != null
                ? MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(worldClient))
                : Integer.MIN_VALUE;
    }
}
