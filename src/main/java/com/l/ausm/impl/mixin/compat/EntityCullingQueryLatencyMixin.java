package com.l.ausm.impl.mixin.compat;

import org.lwjgl.opengl.GL15;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * EntityCulling 6.5 waits for the previous timestamp query from the first
 * visibility lookup of every frame. A late query turns glGetQueryObjecti64
 * into a synchronous GPU/CPU fence on the Client thread. Preserve correctness
 * conservatively: when the result is not ready, render entities for this frame
 * and let EntityCulling submit/read a later frame normally.
 */
@Mixin(targets = "meldexun.entityculling.util.culling.CullingInstance", remap = false)
public abstract class EntityCullingQueryLatencyMixin {
    @Shadow(remap = false)
    private int syncQuery;

    @Shadow(remap = false)
    private int frame;

    @Unique
    private int ausm$deferredFrame = Integer.MIN_VALUE;

    @Inject(
            method = "isVisible(Lmeldexun/entityculling/util/ICullable$CullInfo;)Z",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void ausm$avoidBlockingQueryRead(CallbackInfoReturnable<Boolean> cir) {
        if (syncQuery < 0) {
            return;
        }
        if (ausm$deferredFrame == frame) {
            cir.setReturnValue(true);
            return;
        }
        if (GL15.glGetQueryObjecti(syncQuery, GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            return;
        }

        ausm$deferredFrame = frame;
        cir.setReturnValue(true);
    }
}
