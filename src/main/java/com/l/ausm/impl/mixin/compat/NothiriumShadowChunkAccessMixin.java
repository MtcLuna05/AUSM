package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.compat.NothiriumShadowChunkAccess;
import com.l.ausm.impl.pipeline.compat.NothiriumVisibleTerrainCache;
import meldexun.nothirium.api.renderer.IVBOPart;
import meldexun.nothirium.api.renderer.chunk.ChunkRenderPass;
import meldexun.nothirium.util.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;

/**
 * Exposes the already-resident section position and VBO-part lookup to AUSM's
 * shadow bridge. This keeps the hot shadow selection loop out of reflective
 * dispatch without changing Nothirium's renderer, culling, or VBO ownership.
 */
@Mixin(targets = "meldexun.nothirium.renderer.chunk.AbstractRenderChunk", remap = false)
public abstract class NothiriumShadowChunkAccessMixin implements NothiriumShadowChunkAccess {
    @Shadow(remap = false)
    private SectionPos pos;

    @Shadow(remap = false)
    public abstract IVBOPart getVBOPart(ChunkRenderPass pass);

    @Shadow(remap = false)
    private CompletableFuture<?> lastCompileTaskResult;

    @Shadow(remap = false)
    public abstract boolean isDirty();

    /**
     * Publish invalidation after the RenderChunk owns the new part. Hooking
     * DynamicVBO.buffer() was too early and missed the direct null publication
     * used when a compiled layer becomes empty.
     */
    @Inject(method = "setVBOPart", at = @At("RETURN"), require = 0, remap = false)
    private void ausm$invalidatePublishedTerrainPart(ChunkRenderPass pass, IVBOPart part, CallbackInfo ci) {
        NothiriumVisibleTerrainCache.markVboUpload();
    }

    @Override
    public int ausm$blockX() {
        return pos.getBlockX();
    }

    @Override
    public int ausm$blockY() {
        return pos.getBlockY();
    }

    @Override
    public int ausm$blockZ() {
        return pos.getBlockZ();
    }

    @Override
    public Object ausm$vboPart(Object pass) {
        return pass instanceof ChunkRenderPass ? getVBOPart((ChunkRenderPass) pass) : null;
    }

    @Override
    public Object ausm$lastCompileTaskResult() {
        return lastCompileTaskResult;
    }

    @Override
    public boolean ausm$isDirty() {
        return isDirty();
    }
}
