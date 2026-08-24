package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import meldexun.nothirium.api.renderer.chunk.ChunkRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.MinecraftChunkRenderer", remap = false)
public class NothiriumMinecraftChunkRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$renderTranslucentWithPipeline(ChunkRenderPass pass, CallbackInfo ci) {
        if (PipelineContext.getInstance().renderNothiriumRendererPass(pass)) {
            ci.cancel();
        }
    }
}
