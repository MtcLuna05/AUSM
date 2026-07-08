package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.vertex.SeparateAoColorWriter;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraftforge.client.model.pipeline.VertexBufferConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VertexBufferConsumer.class)
public class VertexBufferConsumerMixin {

    @Shadow(remap = false)
    private int v;

    @Redirect(
            method = "put",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/BufferBuilder;addVertexData([I)V")
    )
    private void ausm$rewriteForgeSeparateAo(BufferBuilder bufferBuilder, int[] data) {
        SeparateAoColorWriter.rewriteForgeQuadData(bufferBuilder, data);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke((bufferBuilder), new String[] {"func_178981_a", "addVertexData"}, new Class<?>[] {int[].class}, (data));;
        } finally {
            // Forge leaves v at 4 if addVertexData throws, causing index-56 spam on the next quad.
            v = 0;
        }
    }
}
