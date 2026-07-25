package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "org.taumc.celeritas.impl.render.terrain.compile.task.ChunkBuilderMeshingTask", remap = false)
public abstract class CeleritasChunkBuilderMeshingTaskMixin {
    @Unique
    private static volatile Method ausm$nativeRenderBlockMethod;
    @Unique
    private static volatile MethodHandle ausm$nativeRenderBlockHandle;
    @Unique
    private static volatile Class<?> ausm$nativeRenderBlockOwner;
    @Unique
    private static volatile boolean ausm$nativeRenderBlockUnavailable;
    @Unique
    private static volatile Method ausm$getBufferForLayerMethod;
    @Unique
    private static volatile MethodHandle ausm$getBufferForLayerHandle;
    @Unique
    private static volatile Class<?> ausm$getBufferForLayerOwner;
    @Unique
    private static volatile boolean ausm$getBufferForLayerUnavailable;
    @Unique
    private static final AtomicInteger ausm$nativeRenderFailureCount = new AtomicInteger();
    @Unique
    private static final AtomicInteger ausm$blockcrafteryBloomProbeCount = new AtomicInteger();
    @Unique
    private static final AtomicInteger ausm$liquidRouteProbeCount = new AtomicInteger();
    @Unique
    private static final AtomicInteger ausm$specialLayerProbeCount = new AtomicInteger();

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
        PipelineContext pipeline = PipelineContext.getInstance();
        boolean liquid = com.l.ausm.impl.util.MinecraftReflectionCompat.stateIsLiquidOrWater(state);
        boolean forgeFallback = blockAccess instanceof net.minecraft.world.IBlockAccess
                && TerrainCompileCoordinator.requiresForgeFallback(state,
                (net.minecraft.world.IBlockAccess) blockAccess, pos, pipeline);
        if (liquid && ausm$liquidRouteProbeCount.incrementAndGet() <= 128) {
            BufferBuilder probeBuffer = ausm$getBufferForLayer(buildContext, layer);
            com.l.ausm.impl.MainMod.LOGGER.info(
                    "[AUSMCeleritasLiquidRouteProbe] stage=enter state={} layer={} forgeFallback={} pipelineActive={} buffer={} format={} vertices={}",
                    state, layer, forgeFallback, pipeline.isPipelineActive(), probeBuffer,
                    probeBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(probeBuffer) : null,
                    probeBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(probeBuffer) : -1);
        }
        boolean fire = com.l.ausm.impl.util.MinecraftReflectionCompat.stateMaterialIsFire(state);
        boolean twilightPortal = pipeline.isCeleritasTwilightPortalState(state);
        if ((fire || twilightPortal) && ausm$specialLayerProbeCount.incrementAndGet() <= 64) {
            com.l.ausm.impl.MainMod.LOGGER.info(
                    "[AUSMSpecialBlockRouteProbe] state={} kind={} layer={} forgeFallback={} renderType={} pipelineActive={} access={}",
                    state, fire ? "fire" : "twilight-portal", layer, forgeFallback,
                    com.l.ausm.impl.util.MinecraftReflectionCompat.stateRenderType(state),
                    pipeline.isPipelineActive(), blockAccess != null ? blockAccess.getClass().getName() : "null");
        }
        boolean blockcraftery = pipeline.isBlockcrafteryEditableState(state);
        boolean inheritedBloom = blockAccess instanceof net.minecraft.world.IBlockAccess
                && pipeline.gpomFramedMaterialHasBloom((net.minecraft.world.IBlockAccess) blockAccess, pos);
        int inheritedEmission = blockAccess instanceof net.minecraft.world.IBlockAccess
                ? pipeline.gpomFramedMaterialEmission((net.minecraft.world.IBlockAccess) blockAccess, pos) : 0;
        if (blockcraftery && (inheritedBloom || inheritedEmission > 0)
                && ausm$blockcrafteryBloomProbeCount.incrementAndGet() <= 64) {
            com.l.ausm.impl.MainMod.LOGGER.info(
                    "[AUSMCeleritasBlockcrafteryBloomProbe] pos={} layer={} inheritedBloom={} inheritedEmission={}",
                    pos, layer, inheritedBloom, inheritedEmission);
        }
        if (blockcraftery && AusmBloomLayer.isBloomLayer(layer)
                && (!(blockAccess instanceof net.minecraft.world.IBlockAccess)
                || !pipeline.gpomFramedMaterialHasBloom((net.minecraft.world.IBlockAccess) blockAccess, pos))) {
            return;
        }
        if (ausm$renderForgeFallback(state, pos, blockAccess, layer, buildContext)) {
            if (liquid && ausm$liquidRouteProbeCount.get() <= 128) {
                BufferBuilder probeBuffer = ausm$getBufferForLayer(buildContext, layer);
                com.l.ausm.impl.MainMod.LOGGER.info(
                        "[AUSMCeleritasLiquidRouteProbe] stage=forge-return state={} layer={} buffer={} format={} vertices={}",
                        state, layer, probeBuffer,
                        probeBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexFormat(probeBuffer) : null,
                        probeBuffer != null ? com.l.ausm.impl.util.MinecraftReflectionCompat.bufferVertexCount(probeBuffer) : -1);
            }
            return;
        }
        if (!ausm$invokeNativeRenderBlock(renderer, state, pos, blockAccess, layer)) {
            ausm$renderVanillaBlock(state, pos, blockAccess, layer, buildContext);
        }
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
    private static boolean ausm$invokeNativeRenderBlock(Object renderer, IBlockState state, BlockPos pos,
                                                       Object blockAccess,
                                                       BlockRenderLayer layer) {
        MethodHandle handle = ausm$nativeRenderBlockHandle(renderer);
        if (handle == null) {
            return false;
        }
        try {
            handle.invoke(renderer, state, pos, blockAccess, layer);
            return true;
        } catch (Throwable failure) {
            if (ausm$nativeRenderFailureCount.incrementAndGet() <= 8) {
                com.l.ausm.impl.MainMod.LOGGER.warn(
                        "[AUSMCeleritasCompileCompat] native block renderer failed; using vanilla fallback",
                        failure);
            }
            return false;
        }
    }

    @Unique
    private static void ausm$renderVanillaBlock(IBlockState state, BlockPos pos, Object blockAccess,
                                                BlockRenderLayer layer, Object buildContext) {
        if (!(blockAccess instanceof net.minecraft.world.IBlockAccess)) {
            return;
        }
        BufferBuilder buffer = ausm$getBufferForLayer(buildContext, layer);
        BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(
                com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft());
        if (buffer != null && dispatcher != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.renderBlock(dispatcher, state, pos,
                    (net.minecraft.world.IBlockAccess) blockAccess, buffer);
        }
    }

    @Unique
    private static MethodHandle ausm$nativeRenderBlockHandle(Object renderer) {
        if (renderer == null || ausm$nativeRenderBlockUnavailable) {
            return null;
        }
        Class<?> owner = renderer.getClass();
        MethodHandle cached = ausm$nativeRenderBlockHandle;
        if (cached != null && ausm$nativeRenderBlockOwner == owner) {
            return cached;
        }
        synchronized (CeleritasChunkBuilderMeshingTaskMixin.class) {
            cached = ausm$nativeRenderBlockHandle;
            if (cached != null && ausm$nativeRenderBlockOwner == owner) {
                return cached;
            }
            try {
                Class<?> blockAccessType = Class.forName(
                        "org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess",
                        false,
                        owner.getClassLoader()
                );
                Method method = owner.getMethod("renderBlock", IBlockState.class, BlockPos.class, blockAccessType,
                        BlockRenderLayer.class);
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                ausm$nativeRenderBlockOwner = owner;
                ausm$nativeRenderBlockMethod = method;
                ausm$nativeRenderBlockHandle = handle;
                ausm$nativeRenderBlockUnavailable = false;
                return handle;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                ausm$nativeRenderBlockUnavailable = true;
                return null;
            }
        }
    }

    @Unique
    private static BufferBuilder ausm$getBufferForLayer(Object buildContext, BlockRenderLayer layer) {
        if (buildContext == null || layer == null) {
            return null;
        }
        MethodHandle handle = ausm$getBufferForLayerHandle(buildContext);
        if (handle == null) {
            return null;
        }
        try {
            Object value = handle.invoke(buildContext, layer);
            return value instanceof BufferBuilder ? (BufferBuilder) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static MethodHandle ausm$getBufferForLayerHandle(Object buildContext) {
        if (buildContext == null || ausm$getBufferForLayerUnavailable) {
            return null;
        }
        Class<?> owner = buildContext.getClass();
        MethodHandle cached = ausm$getBufferForLayerHandle;
        if (cached != null && ausm$getBufferForLayerOwner == owner) {
            return cached;
        }
        synchronized (CeleritasChunkBuilderMeshingTaskMixin.class) {
            cached = ausm$getBufferForLayerHandle;
            if (cached != null && ausm$getBufferForLayerOwner == owner) {
                return cached;
            }
            try {
                Method method = owner.getMethod("getBufferForLayer", BlockRenderLayer.class);
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                ausm$getBufferForLayerOwner = owner;
                ausm$getBufferForLayerMethod = method;
                ausm$getBufferForLayerHandle = handle;
                ausm$getBufferForLayerUnavailable = false;
                return handle;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                ausm$getBufferForLayerUnavailable = true;
                return null;
            }
        }
    }
}
