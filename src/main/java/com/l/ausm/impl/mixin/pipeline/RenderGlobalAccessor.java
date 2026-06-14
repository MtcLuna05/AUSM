package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor("displayListEntitiesDirty")
    void ausm$setDisplayListEntitiesDirty(boolean dirty);

    @Accessor("viewFrustum")
    ViewFrustum ausm$viewFrustum();

    @Accessor("viewFrustum")
    void ausm$setViewFrustum(ViewFrustum viewFrustum);

    @Accessor("renderContainer")
    ChunkRenderContainer ausm$renderContainer();

    @Accessor("renderContainer")
    void ausm$setRenderContainer(ChunkRenderContainer renderContainer);

    @Accessor("renderChunkFactory")
    IRenderChunkFactory ausm$renderChunkFactory();

    @Accessor("renderChunkFactory")
    void ausm$setRenderChunkFactory(IRenderChunkFactory renderChunkFactory);

    @Accessor("renderDispatcher")
    ChunkRenderDispatcher ausm$renderDispatcher();

    @Accessor("renderDispatcher")
    void ausm$setRenderDispatcher(ChunkRenderDispatcher renderDispatcher);

    @Accessor("world")
    WorldClient ausm$world();

    @Accessor("world")
    void ausm$setWorld(WorldClient world);

    @Accessor("chunksToUpdate")
    Set<RenderChunk> ausm$chunksToUpdate();
}
