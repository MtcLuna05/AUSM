package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.RenderingRegressionProbes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {
        "org.embeddedt.embeddium.impl.render.chunk.multidraw.DirectMultiDrawEmitter",
        "org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter"
}, remap = false)
public abstract class CeleritasMultiDrawEmitterMixin {
    @Inject(
            method = "executeBatch",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/embeddedt/embeddium/impl/gl/device/DrawCommandList;multiDrawElementsBaseVertex(Lorg/embeddedt/embeddium/impl/gl/device/MultiDrawBatch;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlPrimitiveType;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlIndexType;)V",
                    shift = At.Shift.BEFORE
            ),
            remap = false,
            require = 0
    )
    private void ausm$probeDirectBatch(@Coerce Object commandList, @Coerce Object tessellation,
                                       @Coerce Object primitiveType, CallbackInfo ci) {
        RenderingRegressionProbes.celeritas("direct-batch-bound", primitiveType, 0.0D, 0.0D, 0.0D,
                tessellation);
    }

    @Inject(
            method = "executeBatch",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/embeddedt/embeddium/impl/gl/device/DrawCommandList;multiDrawElementsIndirect(Lorg/embeddedt/embeddium/impl/gl/buffer/GlBuffer;ILorg/embeddedt/embeddium/impl/gl/tessellation/GlPrimitiveType;Lorg/embeddedt/embeddium/impl/gl/tessellation/GlIndexType;)V",
                    shift = At.Shift.BEFORE
            ),
            remap = false,
            require = 0
    )
    private void ausm$probeIndirectBatch(@Coerce Object commandList, @Coerce Object tessellation,
                                         @Coerce Object primitiveType, CallbackInfo ci) {
        RenderingRegressionProbes.celeritas("indirect-batch-bound", primitiveType, 0.0D, 0.0D, 0.0D,
                tessellation);
    }
}
