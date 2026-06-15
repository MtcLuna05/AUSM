package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.l.ausm.impl.pipeline.compat.NothiriumPipelineCompat;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.RegionRenderCacheBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import meldexun.nothirium.util.VisibilityGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "meldexun.nothirium.mc.renderer.chunk.RenderChunkTaskCompile", remap = false)
public class NothiriumRenderChunkTaskCompileMixin {
    @Shadow(remap = false)
    private IBlockAccess chunkCache;

    @Unique
    private static final Set<String> ausm$compileProbeLogged = ConcurrentHashMap.newKeySet();

    @Unique
    private static final AtomicInteger ausm$compileProbeSamples = new AtomicInteger();

    @Unique
    private static final Set<String> ausm$bloomFallbackLogged = ConcurrentHashMap.newKeySet();

    @Unique
    private static final int ausm$COMPILE_PROBE_SAMPLE_LIMIT = 32;

    @ModifyArg(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;func_181668_a(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
                    remap = false
            ),
            index = 1,
            remap = false
    )
    private VertexFormat ausm$usePipelineBlockFormat(VertexFormat original) {
        return NothiriumPipelineCompat.pipelineBlockFormat(original);
    }

    @Inject(method = "renderBlockState", at = @At("HEAD"), remap = false)
    private void ausm$probeCompileTarget(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                         RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        boolean target = ausm$isProbeTarget(state);
        if (!target && !ausm$isProbeSample(state, pos)) {
            return;
        }
        if (!target && ausm$compileProbeSamples.incrementAndGet() > ausm$COMPILE_PROBE_SAMPLE_LIMIT) {
            return;
        }

        ResourceLocation name = ausm$registryName(state);
        String layers = ausm$renderableLayers(state);
        String key = (target ? "target" : "sample") + "|" + String.valueOf(name) + "|" + layers;
        if (!ausm$compileProbeLogged.add(key)) {
            return;
        }

        Block block = state.getBlock();
        EnumBlockRenderType renderType = state.getRenderType();
        System.out.println("[AUSM-CompileProbe] kind=" + (target ? "target" : "sample")
                + " block=" + name
                + " class=" + (block != null ? block.getClass().getName() : "null")
                + " state=" + state
                + " pos=" + (pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "null")
                + " renderType=" + renderType
                + " material=" + state.getMaterial()
                + " blockLayer=" + (block != null ? block.getRenderLayer() : null)
                + " canLayers=" + layers
                + " light=" + ausm$lightValue(state, pos)
                + " opaque=" + state.isOpaqueCube()
                + " fullCube=" + state.isFullCube());
    }

    @Inject(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
                    shift = At.Shift.BEFORE,
                    remap = false
            ),
            remap = false
    )
    private void ausm$setPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                              RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        PipelineContext pipeline = PipelineContext.getInstance();
        BlockRenderContext.setBlockEntityId(pipeline.blockEntityId(state, chunkCache, pos));
        BlockRenderContext.setRenderType((short) state.getRenderType().ordinal());
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, chunkCache, pos));
        BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockRenderContext.setBlockEmission(pipeline.blockRenderEmission(state, chunkCache, pos));
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(state));
        pipeline.recordSyntheticLightCandidate(state, chunkCache, pos);
    }

    @Inject(
            method = "renderBlockState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;func_175018_a(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/client/renderer/BufferBuilder;)Z",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false
    )
    private void ausm$clearPipelineBlockContext(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                                RegionRenderCacheBuilder bufferBuilder, CallbackInfo ci) {
        BlockRenderContext.clear();
    }

    @Inject(method = "renderBlockState", at = @At("RETURN"), remap = false)
    private void ausm$renderBloomOnlyFallback(IBlockState state, BlockPos pos, VisibilityGraph visibilityGraph,
                                              RegionRenderCacheBuilder regionBuffers, CallbackInfo ci) {
        if (!ausm$isBloomOnlyModelBlock(state) || pos == null || regionBuffers == null) {
            return;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        BlockRenderLayer fallbackLayer = ausm$bloomFallbackLayer(state);
        BufferBuilder buffer = regionBuffers.getWorldRendererByLayer(fallbackLayer);
        if (buffer == null) {
            return;
        }

        if (!((IBufferBuilderExtension) buffer).ausm$isDrawing()) {
            buffer.begin(7, NothiriumPipelineCompat.pipelineBlockFormat(DefaultVertexFormats.BLOCK));
            int originX = Math.floorDiv(pos.getX(), 16) * 16;
            int originY = Math.floorDiv(pos.getY(), 16) * 16;
            int originZ = Math.floorDiv(pos.getZ(), 16) * 16;
            buffer.setTranslation(-originX, -originY, -originZ);
        }

        BlockRenderLayer previousLayer = MinecraftForgeClient.getRenderLayer();
        int start = buffer.getVertexCount();
        boolean rendered = false;
        try {
            // Keep the model in its native BLOOM render layer while storing the
            // resulting geometry in a vanilla Nothirium pass.
            ForgeHooksClient.setRenderLayer(bloomLayer);
            rendered = Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, chunkCache, buffer);
        } finally {
            ForgeHooksClient.setRenderLayer(previousLayer);
        }

        ResourceLocation name = ausm$registryName(state);
        String key = String.valueOf(name) + "|" + fallbackLayer;
        if (ausm$bloomFallbackLogged.add(key)) {
            System.out.println("[AUSM-BloomFallback] block=" + name
                    + " state=" + state
                    + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + " fallbackLayer=" + fallbackLayer
                    + " rendered=" + rendered
                    + " vertices=" + (buffer.getVertexCount() - start));
        }
    }

    @Unique
    private static boolean ausm$isProbeTarget(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (name == null) {
            return false;
        }
        String path = name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        Block block = state.getBlock();
        String className = block != null ? block.getClass().getName().toLowerCase(java.util.Locale.ROOT) : "";
        return path.contains("fire")
                || path.contains("luminous")
                || className.endsWith(".blockfire")
                || className.contains(".blockfire")
                || state.getMaterial() == Material.FIRE;
    }

    @Unique
    private static boolean ausm$isBloomOnlyModelBlock(IBlockState state) {
        if (state == null || state.getBlock() == null || state.getRenderType() == EnumBlockRenderType.INVISIBLE) {
            return false;
        }

        BlockRenderLayer bloomLayer = AusmBloomLayer.layer();
        if (bloomLayer == null || !ausm$canRenderInLayer(state.getBlock(), state, bloomLayer)) {
            return false;
        }

        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            if (layer == null || AusmBloomLayer.isBloomLayer(layer)) {
                continue;
            }
            if (ausm$canRenderInLayer(state.getBlock(), state, layer)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        String path = name != null && name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        if (path.contains("fire") || path.contains("luminous") || state.getMaterial() == Material.FIRE) {
            return BlockRenderLayer.CUTOUT;
        }
        if (path.contains("translucent") || !state.isOpaqueCube() || !state.isFullCube()) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        return BlockRenderLayer.SOLID;
    }

    @Unique
    private boolean ausm$isProbeSample(IBlockState state, BlockPos pos) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        if (ausm$lightValue(state, pos) > 0) {
            return true;
        }
        if (state.isFullCube() && state.isOpaqueCube()) {
            return false;
        }
        Block block = state.getBlock();
        return ausm$canRenderInLayer(block, state, BlockRenderLayer.CUTOUT)
                || ausm$canRenderInLayer(block, state, BlockRenderLayer.CUTOUT_MIPPED)
                || ausm$canRenderInLayer(block, state, BlockRenderLayer.TRANSLUCENT);
    }

    @Unique
    private static String ausm$renderableLayers(IBlockState state) {
        if (state == null || state.getBlock() == null) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (BlockRenderLayer layer : BlockRenderLayer.values()) {
            boolean canRender = false;
            try {
                canRender = ausm$canRenderInLayer(state.getBlock(), state, layer);
            } catch (RuntimeException | LinkageError ignored) {
            }
            if (canRender) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(layer.name());
                first = false;
            }
        }
        return builder.append(']').toString();
    }

    @Unique
    private static boolean ausm$canRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        return block != null && layer != null && block.canRenderInLayer(state, layer);
    }

    @Unique
    private int ausm$lightValue(IBlockState state, BlockPos pos) {
        try {
            return state.getLightValue(chunkCache, pos);
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            return state.getLightValue();
        } catch (RuntimeException | LinkageError ignored) {
            return -1;
        }
    }

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        return state != null && state.getBlock() != null ? state.getBlock().getRegistryName() : null;
    }
}
