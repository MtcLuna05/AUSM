package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.ViewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewFrustum.class)
public interface ViewFrustumAccessor {
    @Accessor("countChunksX")
    int ausm$countChunksX();

    @Accessor("countChunksY")
    int ausm$countChunksY();

    @Accessor("countChunksZ")
    int ausm$countChunksZ();
}
