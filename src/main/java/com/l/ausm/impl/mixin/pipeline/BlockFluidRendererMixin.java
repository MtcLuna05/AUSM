package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockFluidRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(BlockFluidRenderer.class)
public class BlockFluidRendererMixin {
    @Unique
    private static final AtomicInteger AUSM$PROBE_COUNT = new AtomicInteger();

    @Inject(method = "func_178270_a(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("HEAD"), remap = false, require = 0)
    private void ausm$probeFluidRenderHead(IBlockAccess access, IBlockState state, BlockPos pos,
                                           BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        ausm$probeFluidRender("head", access, state, pos, buffer, null);
    }

    @Inject(method = "func_178270_a(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/renderer/BufferBuilder;)Z", at = @At("RETURN"), remap = false, require = 0)
    private void ausm$probeFluidRenderReturn(IBlockAccess access, IBlockState state, BlockPos pos,
                                             BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        ausm$probeFluidRender("return", access, state, pos, buffer, cir.getReturnValue());
    }

    @Unique
    private static void ausm$probeFluidRender(String stage, IBlockAccess access, IBlockState state,
                                              BlockPos pos, BufferBuilder buffer, Boolean result) {
        if (!MinecraftReflectionCompat.stateIsLiquid(state)) {
            return;
        }
        int call = AUSM$PROBE_COUNT.incrementAndGet();
        if (call > 48) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMFluidProbe] call={} stage={} thread={} pos={} state={} access={} buffer={} vertices={} drawing={} format={} layer={} liquidFlag={}",
                call,
                stage,
                Thread.currentThread().getName(),
                pos,
                state,
                access != null ? access.getClass().getName() : "null",
                buffer != null ? Integer.toHexString(System.identityHashCode(buffer)) : "null",
                buffer != null ? MinecraftReflectionCompat.bufferVertexCount(buffer) : -1,
                buffer instanceof IBufferBuilderExtension extension && extension.ausm$isDrawing(),
                buffer != null ? MinecraftReflectionCompat.bufferVertexFormat(buffer) : null,
                MinecraftReflectionCompat.currentRenderLayer(),
                BlockRendererDispatcherHooks.LIQUID_RENDER.get(),
                result
        );
    }
}
