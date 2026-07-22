package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.RenderingRegressionProbes;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Constructor;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer", remap = false)
public abstract class CeleritasWorldRendererMixin {
    @Unique
    private static final ThreadLocal<FloatBuffer[]> ausm$matrixBuffers = ThreadLocal.withInitial(() ->
            new FloatBuffer[] {BufferUtils.createFloatBuffer(16), BufferUtils.createFloatBuffer(16)});
    @Unique
    private static final AtomicInteger ausm$matrixProbeCount = new AtomicInteger();
    @Unique
    private static volatile Constructor<?> ausm$matrixConstructor;
    @Unique
    private static volatile boolean ausm$matrixConstructorUnavailable;

    @Inject(method = "createChunkRenderMatrices", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$useActivePipelineMatrices(CallbackInfoReturnable<Object> cir) {
        RenderingRegressionProbes.celeritas("create-matrices-head", null, 0.0D, 0.0D, 0.0D, null);
        if (!PipelineContext.getInstance().isPipelineActive()) {
            return;
        }

        Constructor<?> constructor = ausm$resolveMatrixConstructor();
        if (constructor == null) {
            return;
        }

        FloatBuffer[] buffers = ausm$matrixBuffers.get();
        FloatBuffer projection = buffers[0];
        FloatBuffer modelView = buffers[1];
        projection.clear();
        modelView.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        projection.rewind();
        modelView.rewind();

        try {
            Object matrices = constructor.newInstance(projection, modelView);
            cir.setReturnValue(matrices);
            int probe = ausm$matrixProbeCount.incrementAndGet();
            if (probe <= 24) {
                MainMod.LOGGER.info(
                        "[AUSMCeleritasMatrices] call={} projectionDiag={}/{}/{} modelDiag={}/{}/{} translation={}/{}/{}",
                        probe,
                        projection.get(0), projection.get(5), projection.get(10),
                        modelView.get(0), modelView.get(5), modelView.get(10),
                        modelView.get(12), modelView.get(13), modelView.get(14));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            ausm$matrixConstructorUnavailable = true;
            MainMod.LOGGER.warn("[CeleritasCompat] Failed to construct live AUSM chunk matrices", error);
        }
    }

    @Inject(method = "createChunkRenderMatrices", at = @At("RETURN"), remap = false)
    private void ausm$probeReturnedMatrices(CallbackInfoReturnable<Object> cir) {
        RenderingRegressionProbes.celeritas("create-matrices-return", null, 0.0D, 0.0D, 0.0D,
                cir.getReturnValue());
    }

    @Inject(
            method = "drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V",
            at = @At("HEAD"),
            remap = false
    )
    private void ausm$probeLayerHead(BlockRenderLayer layer, double x, double y, double z, CallbackInfo ci) {
        RenderingRegressionProbes.celeritas("draw-layer-head", layer, x, y, z, null);
    }

    @Inject(
            method = "drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V",
            at = @At("RETURN"),
            remap = false
    )
    private void ausm$probeLayerReturn(BlockRenderLayer layer, double x, double y, double z, CallbackInfo ci) {
        RenderingRegressionProbes.celeritas("draw-layer-return", layer, x, y, z, null);
    }

    @Unique
    private static Constructor<?> ausm$resolveMatrixConstructor() {
        Constructor<?> cached = ausm$matrixConstructor;
        if (cached != null || ausm$matrixConstructorUnavailable) {
            return cached;
        }
        synchronized (CeleritasWorldRendererMixin.class) {
            cached = ausm$matrixConstructor;
            if (cached != null || ausm$matrixConstructorUnavailable) {
                return cached;
            }
            try {
                Class<?> matrices = Class.forName(
                        "org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices",
                        false,
                        CeleritasWorldRendererMixin.class.getClassLoader());
                cached = matrices.getConstructor(FloatBuffer.class, FloatBuffer.class);
                cached.setAccessible(true);
                ausm$matrixConstructor = cached;
                return cached;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                ausm$matrixConstructorUnavailable = true;
                MainMod.LOGGER.warn("[CeleritasCompat] Live AUSM chunk matrix bridge unavailable", error);
                return null;
            }
        }
    }
}
