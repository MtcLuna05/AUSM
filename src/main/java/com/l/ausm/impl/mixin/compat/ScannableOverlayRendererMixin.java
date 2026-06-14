package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "li.cil.scannable.client.renderer.OverlayRenderer", remap = false)
public abstract class ScannableOverlayRendererMixin {
    @Inject(method = "onOverlayRender", at = @At("HEAD"))
    private void ausm$prepareScannableOverlay(RenderGameOverlayEvent.Post event, CallbackInfo ci) {
        if (event != null && event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            PipelineContext.getInstance().prepareExternalOverlayRender("Scannable progress");
        }
    }

    @Inject(method = "onOverlayRender", at = @At("RETURN"))
    private void ausm$restoreScannableOverlay(RenderGameOverlayEvent.Post event, CallbackInfo ci) {
        if (event != null && event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            PipelineContext.getInstance().finishExternalOverlayRender("Scannable progress");
        }
    }
}
