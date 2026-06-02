package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.ViewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor("displayListEntitiesDirty")
    void ausm$setDisplayListEntitiesDirty(boolean dirty);

    @Accessor("viewFrustum")
    ViewFrustum ausm$viewFrustum();

    @Accessor("renderContainer")
    ChunkRenderContainer ausm$renderContainer();
}
