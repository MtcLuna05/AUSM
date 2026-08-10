package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.EfficientEntitiesChestCompat;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Binds the OptiFine block-entity program while tile entities render.
 */
@Mixin(TileEntityRendererDispatcher.class)
public class TileEntityRendererDispatcherMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$tileEntityPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "drawBatch(I)V", at = @At("HEAD"), remap = false)
    private void ausm$repairBatchTextureBinding(int pass, CallbackInfo ci) {
        MinecraftReflectionCompat.glStateSetActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateBindTexture(0);
        TextureManager textureManager = MinecraftReflectionCompat.firstInstanceFieldOfType(this, TextureManager.class);
        MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("HEAD"))
    private void onRenderTileEntityHead(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        EfficientEntitiesChestCompat.beginTileEntity(tileEntity);
        PipelineContext context = PipelineContext.getInstance();
        WorldRenderingPhase phase = context.getPhase();
        boolean shouldBind = phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
        AUSM$tileEntityPhaseStack.get().push(shouldBind);
        if (shouldBind) {
            context.beginPhase(context.blockEntityPhaseForCurrentForgePass());
        }
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At("RETURN"))
    private void onRenderTileEntityReturn(TileEntity tileEntity, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        try {
            Deque<Boolean> stack = AUSM$tileEntityPhaseStack.get();
            if (!stack.isEmpty() && stack.pop()) {
                PipelineContext.getInstance().endPass();
            }
        } finally {
            EfficientEntitiesChestCompat.endTileEntity();
        }
    }

}
