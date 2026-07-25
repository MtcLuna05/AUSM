package com.l.ausm.impl.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {
        "org.embeddedt.embeddium.impl.render.chunk.multidraw.DirectMultiDrawEmitter",
        "org.embeddedt.embeddium.impl.render.chunk.multidraw.IndirectMultiDrawEmitter"
}, remap = false)
public abstract class CeleritasMultiDrawEmitterMixin {
}
