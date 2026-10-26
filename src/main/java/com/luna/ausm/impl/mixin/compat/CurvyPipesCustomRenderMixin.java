package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The custom fragment shader draws editing previews, not world pipe meshes. */
@Mixin(targets = "cyb0124.curvy_pipes.client.CustomRender", remap = false)
public abstract class CurvyPipesCustomRenderMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void ausm$skipShadowPreview(ByteBuffer p, ByteBuffer q, ByteBuffer vertices,
                                       ByteBuffer indices, CallbackInfo ci) {
        if (PipelineContext.getInstance().isShadowPassActive()) ci.cancel();
    }
}
