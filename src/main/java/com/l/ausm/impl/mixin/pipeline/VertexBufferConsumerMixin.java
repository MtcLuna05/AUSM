package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.SeparateAoColorWriter;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBufferConsumer.class)
public class VertexBufferConsumerMixin {

    @Shadow
    private BufferBuilder renderer;

    @Shadow
    private int[] quadData;

    @Inject(
            method = "put",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V", shift = At.Shift.BEFORE)
    )
    private void ausm$rewriteForgeSeparateAo(int element, float[] data, CallbackInfo ci) {
        SeparateAoColorWriter.rewriteForgeQuadData(renderer, quadData);
    }
}
