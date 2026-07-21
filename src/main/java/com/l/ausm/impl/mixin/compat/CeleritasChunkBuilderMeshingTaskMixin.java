package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.TerrainCompileCoordinator;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(targets = "org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask", remap = false)
public abstract class CeleritasChunkBuilderMeshingTaskMixin {
    @Inject(
            method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$beginCompile(@Coerce Object buildContext, @Coerce Object cancellationToken,
                                   CallbackInfoReturnable<Object> cir) {
        TerrainCompileCoordinator.beginSection();
        PipelineContext.getInstance().beginFramedMaterialCompileCache();
    }

    @Inject(
            method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At("RETURN"),
            remap = false
    )
    private void ausm$endCompile(@Coerce Object buildContext, @Coerce Object cancellationToken,
                                 CallbackInfoReturnable<Object> cir) {
        PipelineContext.getInstance().endFramedMaterialCompileCache();
        TerrainCompileCoordinator.endSection();
    }

    @Redirect(
            method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;canRenderInLayer(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/BlockRenderLayer;)Z",
                    remap = false
            ),
            remap = false
    )
    private boolean ausm$canRenderInCeleritasLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        return TerrainCompileCoordinator.canRenderInLayer(block, state, layer, PipelineContext.getInstance());
    }

    @Redirect(
            method = "execute(Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildContext;Lorg/embeddedt/embeddium/impl/util/task/CancellationToken;)Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildOutput;",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/taumc/celeritas/impl/render/terrain/compile/pipeline/VintageBlockRenderer;renderBlock(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lorg/taumc/celeritas/impl/world/cloned/CeleritasBlockAccess;Lnet/minecraft/util/BlockRenderLayer;)V",
                    remap = false
            ),
            remap = false
    )
    private void ausm$renderCeleritasOrForgeFallback(
            @Coerce Object renderer,
            IBlockState state,
            BlockPos pos,
            @Coerce Object blockAccess,
            BlockRenderLayer layer,
            @Coerce Object buildContext,
            @Coerce Object cancellationToken
    ) {
        if (ausm$renderForgeFallback(state, pos, blockAccess, layer, buildContext)) {
            return;
        }
        ausm$invokeNativeRenderBlock(renderer, state, pos, blockAccess, layer);
    }

    @Unique
    private static boolean ausm$renderForgeFallback(IBlockState state, BlockPos pos,
                                                    Object blockAccess,
                                                    BlockRenderLayer layer, Object buildContext) {
        if (!(blockAccess instanceof net.minecraft.world.IBlockAccess)) {
            return false;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        if (!TerrainCompileCoordinator.requiresForgeFallback(state, (net.minecraft.world.IBlockAccess) blockAccess, pos, pipeline)) {
            return false;
        }

        BufferBuilder buffer = ausm$getBufferForLayer(buildContext, layer);
        BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(
                com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        if (buffer == null || dispatcher == null) {
            return false;
        }

        BlockRenderLayer previousLayer = com.l.ausm.impl.util.MinecraftReflectionCompat.currentRenderLayer();
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(layer);
            return com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, state, pos,
                    (net.minecraft.world.IBlockAccess) blockAccess, buffer);
        } finally {
            com.l.ausm.impl.util.MinecraftReflectionCompat.setCurrentRenderLayer(previousLayer);
        }
    }

    @Unique
    private static void ausm$invokeNativeRenderBlock(Object renderer, IBlockState state, BlockPos pos,
                                                    Object blockAccess,
                                                    BlockRenderLayer layer) {
        try {
            Method method = renderer.getClass().getMethod(
                    "renderBlock",
                    IBlockState.class,
                    BlockPos.class,
                    Class.forName("org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess"),
                    BlockRenderLayer.class
            );
            method.invoke(renderer, state, pos, blockAccess, layer);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
        }
    }

    @Unique
    private static BufferBuilder ausm$getBufferForLayer(Object buildContext, BlockRenderLayer layer) {
        if (buildContext == null || layer == null) {
            return null;
        }
        try {
            Method method = buildContext.getClass().getMethod("getBufferForLayer", BlockRenderLayer.class);
            Object value = method.invoke(buildContext, layer);
            return value instanceof BufferBuilder ? (BufferBuilder) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
