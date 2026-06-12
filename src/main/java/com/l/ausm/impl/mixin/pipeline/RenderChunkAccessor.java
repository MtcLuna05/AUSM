package com.l.ausm.impl.mixin.pipeline;

import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderChunk.class)
public interface RenderChunkAccessor {
    @Accessor("world")
    World ausm$world();

    @Accessor("world")
    void ausm$setWorld(World world);
}
