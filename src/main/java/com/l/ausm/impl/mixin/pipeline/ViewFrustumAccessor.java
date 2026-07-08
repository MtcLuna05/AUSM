package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.ViewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewFrustum.class)
public interface ViewFrustumAccessor {
    @Accessor(value = "field_178165_d", remap = false)
    int ausm$countChunksX();

    @Accessor(value = "field_178168_c", remap = false)
    int ausm$countChunksY();

    @Accessor(value = "field_178166_e", remap = false)
    int ausm$countChunksZ();
}
