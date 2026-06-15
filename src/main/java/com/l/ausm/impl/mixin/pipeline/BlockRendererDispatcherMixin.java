package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(BlockRendererDispatcher.class)
public class BlockRendererDispatcherMixin {
    @Unique
    private static final ThreadLocal<Integer> ausm$probeStartVertex = new ThreadLocal<>();

    @Unique
    private static final Set<String> ausm$probeLogged = ConcurrentHashMap.newKeySet();

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void ausm$beforeRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        if (BetterPortalsCompat.shouldSuppressOriginalPortalBlock(state)) {
            cir.setReturnValue(false);
            return;
        }

        PipelineContext pipeline = PipelineContext.getInstance();
        int blockEntityId = pipeline.blockEntityId(state, blockAccess, pos);
        int blockEmission = pipeline.blockRenderEmission(state, blockAccess, pos);
        BlockRenderContext.setBlockEntityId(blockEntityId);
        BlockRenderContext.setRenderType((short) state.getRenderType().ordinal());
        BlockRenderContext.setMetadata(pipeline.blockMetadata(state, blockAccess, pos));
        BlockRenderContext.setLocalBlockPos(pos.getX(), pos.getY(), pos.getZ());
        BlockRenderContext.setBlockEmission(blockEmission);
        BlockRenderContext.setCrystalOnlyEmission(pipeline.shouldUseCrystalOnlyEmission(state));
        BlockRenderContext.setSeparateAoEligible(pipeline.shouldSeparateBlockAo(state));
        pipeline.recordSyntheticLightCandidate(state, blockAccess, pos);
        if (ausm$isRenderProbeTarget(state) && bufferBuilder != null) {
            ausm$probeStartVertex.set(bufferBuilder.getVertexCount());
        } else {
            ausm$probeStartVertex.remove();
        }
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void ausm$afterRenderBlock(IBlockState state, BlockPos pos, IBlockAccess blockAccess, BufferBuilder bufferBuilder, CallbackInfoReturnable<Boolean> cir) {
        ausm$logRenderProbe(state, pos, bufferBuilder, cir.getReturnValue());
        ausm$probeStartVertex.remove();
        BlockRenderContext.clear();
    }

    @Unique
    private static boolean ausm$isRenderProbeTarget(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (name == null) {
            return false;
        }
        String namespace = name.getNamespace();
        String path = name.getPath() != null ? name.getPath().toLowerCase(java.util.Locale.ROOT) : "";
        Block block = state.getBlock();
        String className = block != null ? block.getClass().getName().toLowerCase(java.util.Locale.ROOT) : "";
        return "minecraft".equals(namespace) && "fire".equals(path)
                || path.contains("fire")
                || path.contains("luminous")
                || className.endsWith(".blockfire")
                || className.contains(".blockfire")
                || state.getMaterial() == Material.FIRE;
    }

    @Unique
    private static void ausm$logRenderProbe(IBlockState state, BlockPos pos, BufferBuilder bufferBuilder, Boolean result) {
        Integer start = ausm$probeStartVertex.get();
        if (start == null || bufferBuilder == null || !ausm$isRenderProbeTarget(state)) {
            return;
        }

        ResourceLocation name = ausm$registryName(state);
        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        String key = String.valueOf(name) + "|" + String.valueOf(layer);
        if (!ausm$probeLogged.add(key)) {
            return;
        }

        int end = bufferBuilder.getVertexCount();
        int delta = end - start;
        VertexFormat format = bufferBuilder.getVertexFormat();
        int stride = format != null ? format.getSize() : -1;
        int color = 0;
        int alpha = -1;
        int lightU = -1;
        int lightV = -1;
        float x = Float.NaN;
        float y = Float.NaN;
        float z = Float.NaN;
        int blockEntity = 0;
        int renderType = 0;
        int midBlock = 0;

        if (delta > 0 && format != null && stride > 0) {
            ByteBuffer bytes = bufferBuilder.getByteBuffer();
            int base = start * stride;
            if (base >= 0 && base + stride <= bytes.capacity()) {
                x = bytes.getFloat(base);
                y = bytes.getFloat(base + 4);
                z = bytes.getFloat(base + 8);
                if (format.hasColor()) {
                    int offset = base + format.getColorOffset();
                    if (offset >= 0 && offset + 4 <= bytes.capacity()) {
                        color = bytes.getInt(offset);
                        alpha = (color >>> 24) & 0xFF;
                    }
                }
                if (format.hasUvOffset(1)) {
                    int offset = base + format.getUvOffsetById(1);
                    if (offset >= 0 && offset + 4 <= bytes.capacity()) {
                        lightU = bytes.getShort(offset) & 0xFFFF;
                        lightV = bytes.getShort(offset + 2) & 0xFFFF;
                    }
                }
                if (ExtendedVertexFormats.isPipelineBlock(format)) {
                    int entityOffset = base + ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET;
                    int midBlockOffset = base + ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET;
                    if (entityOffset >= 0 && entityOffset + 8 <= bytes.capacity()) {
                        blockEntity = bytes.getShort(entityOffset) & 0xFFFF;
                        renderType = bytes.getShort(entityOffset + 2);
                    }
                    if (midBlockOffset >= 0 && midBlockOffset + 4 <= bytes.capacity()) {
                        midBlock = bytes.getInt(midBlockOffset);
                    }
                }
            }
        }

        System.out.println("[AUSM-BlockProbe] block=" + name
                + " state=" + state
                + " pos=" + (pos != null ? pos.getX() + "," + pos.getY() + "," + pos.getZ() : "null")
                + " layer=" + layer
                + " result=" + result
                + " vertices=" + delta
                + " stride=" + stride
                + " color=0x" + Integer.toHexString(color)
                + " alpha=" + alpha
                + " light=" + lightU + "/" + lightV
                + " xyz=" + x + "," + y + "," + z
                + " mcEntity=" + blockEntity
                + " renderType=" + renderType
                + " midBlock=0x" + Integer.toHexString(midBlock));
    }

    @Unique
    private static ResourceLocation ausm$registryName(IBlockState state) {
        return state != null && state.getBlock() != null ? state.getBlock().getRegistryName() : null;
    }
}
