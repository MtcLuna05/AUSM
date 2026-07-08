package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.IRenderChunkFactory;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor(value = "field_147595_R", remap = false)
    void ausm$setDisplayListEntitiesDirty(boolean dirty);

    @Accessor(value = "field_175008_n", remap = false)
    ViewFrustum ausm$viewFrustum();

    @Accessor(value = "field_175008_n", remap = false)
    void ausm$setViewFrustum(ViewFrustum viewFrustum);

    @Accessor(value = "field_174996_N", remap = false)
    ChunkRenderContainer ausm$renderContainer();

    @Accessor(value = "field_174996_N", remap = false)
    void ausm$setRenderContainer(ChunkRenderContainer renderContainer);

    @Accessor(value = "field_175007_a", remap = false)
    IRenderChunkFactory ausm$renderChunkFactory();

    @Accessor(value = "field_175007_a", remap = false)
    void ausm$setRenderChunkFactory(IRenderChunkFactory renderChunkFactory);

    @Accessor(value = "field_174995_M", remap = false)
    ChunkRenderDispatcher ausm$renderDispatcher();

    @Accessor(value = "field_174995_M", remap = false)
    void ausm$setRenderDispatcher(ChunkRenderDispatcher renderDispatcher);

    @Accessor(value = "field_72769_h", remap = false)
    WorldClient ausm$world();

    @Accessor(value = "field_72769_h", remap = false)
    void ausm$setWorld(WorldClient world);

    @Accessor(value = "field_175009_l", remap = false)
    Set<RenderChunk> ausm$chunksToUpdate();

    @Accessor(value = "field_175005_X", remap = false)
    boolean ausm$vboEnabled();

    @Accessor(value = "field_175011_u", remap = false)
    VertexBuffer ausm$sky2VBO();

    @Accessor(value = "field_72781_x", remap = false)
    int ausm$glSkyList2();
}
