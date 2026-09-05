package com.luna.ausm.impl.pipeline.diagnostics;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.pipeline.compat.BlockRendererDispatcherHooks;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IBufferBuilderExtension;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class BlockRenderDiagnostics {
    private static final String MIXIN_CLASS_NAME =
            "com.luna.ausm.impl.mixin.pipeline.BlockRendererDispatcherMixin";

    private BlockRenderDiagnostics() {
    }

    public static String ausm$bufferDetails(BufferBuilder bufferBuilder) {
        if (bufferBuilder == null) {
            return "null";
        }
        VertexFormat format = MinecraftReflectionCompat.bufferVertexFormat(bufferBuilder);
        return Integer.toHexString(System.identityHashCode(bufferBuilder))
                + "{vertices=" + MinecraftReflectionCompat.bufferVertexCount(bufferBuilder)
                + ", drawing=" + ((IBufferBuilderExtension) bufferBuilder).ausm$isDrawing()
                + ", format=" + format
                + ", pipeline=" + ExtendedVertexFormats.isPipelineBlock(format)
                + ", stride=" + (format != null ? ExtendedVertexFormats.size(format) : -1)
                + "}";
    }

    public static boolean ausm$isRenderProbeTarget(IBlockState state) {
        if (BlockRendererDispatcherHooks.RENDER_PROBE_LOG_LIMIT <= 0
                && BlockRendererDispatcherHooks.EMISSIVE_DISPATCHER_FALLBACK_SKIP_LOG_LIMIT <= 0) {
            return false;
        }
        ResourceLocation name = ausm$registryName(state);
        if (name == null) {
            return false;
        }
        String namespace = MinecraftReflectionCompat.resourceNamespace(name);
        String resourcePath = MinecraftReflectionCompat.resourcePath(name);
        String path = resourcePath != null ? resourcePath.toLowerCase(Locale.ROOT) : "";
        Block block = MinecraftReflectionCompat.blockFromState(state);
        String className = block != null ? block.getClass().getName().toLowerCase(Locale.ROOT) : "";
        return "minecraft".equals(namespace) && "fire".equals(path)
                || "architecturecraft".equals(namespace)
                || namespace.contains("architecture")
                || path.contains("architecture")
                || path.contains("fire")
                || path.contains("glass")
                || path.contains("translucent")
                || className.contains("architecture")
                || className.endsWith(".blockfire")
                || className.contains(".blockfire")
                || className.contains("glass")
                || className.contains("translucent")
                || MinecraftReflectionCompat.stateMaterialIsFire(state);
    }

    public static boolean ausm$isEmissiveBloomFallbackTarget(IBlockState state) {
        return ausm$isEmissiveBloomFallbackSource(state);
    }

    public static boolean ausm$isEmissiveBloomFallbackSource(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        if (state == null || MinecraftReflectionCompat.blockFromState(state) == null
                || name == null || MinecraftReflectionCompat.resourcePath(name) == null) {
            return false;
        }
        PipelineContext pipeline = PipelineContext.getInstance();
        if (pipeline.isBlockcrafteryEditableState(state)) {
            return false;
        }
        return pipeline.stateUsesTextureBloomSource(state);
    }

    public static BlockRenderLayer ausm$bloomFallbackLayer(IBlockState state) {
        BlockRenderLayer naturalLayer = ausm$naturalRenderLayer(state);
        if (naturalLayer != null && !AusmBloomLayer.isBloomLayer(naturalLayer)) {
            return naturalLayer;
        }
        if (state != null && (!MinecraftReflectionCompat.callBoolean(state,
                new String[]{"func_185913_b", "isOpaqueCube"},
                MinecraftReflectionCompat.NO_PARAMETERS, false)
                || !MinecraftReflectionCompat.callBoolean(state,
                new String[]{"func_185917_h", "isFullCube"},
                MinecraftReflectionCompat.NO_PARAMETERS, false))) {
            return BlockRenderLayer.TRANSLUCENT;
        }
        return BlockRenderLayer.SOLID;
    }

    public static ResourceLocation ausm$registryName(IBlockState state) {
        Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
        return block != null ? MinecraftReflectionCompat.blockRegistryName(block) : null;
    }

    public static Block ausm$registryBlock(ResourceLocation key) {
        if (key == null) {
            return null;
        }
        Object value = MinecraftReflectionCompat.invoke(ForgeRegistries.BLOCKS,
                new String[]{"func_82594_a", "getObject", "getValue"},
                new Class<?>[]{ResourceLocation.class}, key);
        return value instanceof Block block ? block : null;
    }

    public static String ausm$stateName(IBlockState state) {
        ResourceLocation name = ausm$registryName(state);
        return name != null ? name.toString() : String.valueOf(state);
    }

    public static int ausm$dimensionId(IBlockAccess blockAccess) {
        if (blockAccess instanceof World world && MinecraftReflectionCompat.worldProvider(world) != null) {
            return MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world));
        }
        return Integer.MIN_VALUE;
    }

    public static String ausm$accessName(IBlockAccess blockAccess) {
        return blockAccess != null ? blockAccess.getClass().getName() : "null";
    }

    public static boolean ausm$canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        try {
            Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
            return block != null && layer != null
                    && MinecraftReflectionCompat.blockCanRenderInLayer(block, state, layer);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public static BlockRenderLayer ausm$naturalRenderLayer(IBlockState state) {
        try {
            Block block = state != null ? MinecraftReflectionCompat.blockFromState(state) : null;
            return block != null ? MinecraftReflectionCompat.blockRenderLayer(block) : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static String ausm$externalCaller() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(BlockRenderDiagnostics.class.getName())
                    || className.equals(MIXIN_CLASS_NAME)
                    || className.equals(BlockRendererDispatcher.class.getName())) {
                continue;
            }
            return className + "#" + frame.getMethodName() + ":" + frame.getLineNumber();
        }
        return "unknown";
    }
}
