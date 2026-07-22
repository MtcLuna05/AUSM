package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.RenderingRegressionProbes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class CeleritasDefaultChunkRendererMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/embeddedt/embeddium/impl/render/chunk/DefaultChunkRenderer;configureShaderInterface(Lorg/embeddedt/embeddium/impl/render/chunk/shader/ChunkShaderInterface;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void ausm$probeConfiguredChunkDraw(@Coerce Object matrices, @Coerce Object commandList,
                                                @Coerce Object renderLists, @Coerce Object renderPass,
                                                @Coerce Object camera, @Coerce Object sectionCamera,
                                                CallbackInfo ci) {
        RenderingRegressionProbes.celeritas("renderer-configured", renderPass, 0.0D, 0.0D, 0.0D, matrices);
    }
}
