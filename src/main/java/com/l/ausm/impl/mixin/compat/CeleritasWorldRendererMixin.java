package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "org.taumc.celeritas.impl.render.terrain.CeleritasWorldRenderer", remap = false)
public abstract class CeleritasWorldRendererMixin {
    @Unique
    private static final ThreadLocal<FloatBuffer[]> ausm$matrixBuffers = ThreadLocal.withInitial(() ->
            new FloatBuffer[] {BufferUtils.createFloatBuffer(16), BufferUtils.createFloatBuffer(16)});
    @Unique
    private static volatile Constructor<?> ausm$matrixConstructor;
    @Unique
    private static volatile boolean ausm$matrixConstructorUnavailable;
    @Unique
    private static final int AUSM_CELERITAS_DRAW_PROBE_LIMIT = 48;
    @Unique
    private static final AtomicInteger ausm$drawProbeCount = new AtomicInteger();
    @Unique
    private static final AtomicInteger ausm$trackerRepairAttempts = new AtomicInteger();
    @Unique
    private static final int AUSM_CELERITAS_TRACKER_REPAIR_LIMIT = 8;
    @Unique
    private static volatile int ausm$trackerRepairWorldIdentity;

    @Inject(method = "drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V", at = @At("HEAD"), remap = false)
    private void ausm$probeDrawChunkLayerHead(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                               CallbackInfo ci) {
        Object renderer = (Object) this;
        if (ausm$invokeInt(renderer, "getVisibleChunkCount", -1) == 0) {
            ausm$repairTrackerFromLoadedChunks(renderer);
        }
        ausm$logDrawProbe("head", layer);
    }

    @Inject(method = "drawChunkLayer(Lnet/minecraft/util/BlockRenderLayer;DDD)V", at = @At("RETURN"), remap = false)
    private void ausm$probeDrawChunkLayerReturn(BlockRenderLayer layer, double cameraX, double cameraY, double cameraZ,
                                                 CallbackInfo ci) {
        ausm$logDrawProbe("return", layer);
    }

    @Unique
    private void ausm$logDrawProbe(String stage, BlockRenderLayer layer) {
        if (!PipelineContext.getInstance().isPipelineActive()) {
            return;
        }
        int ordinal = ausm$drawProbeCount.incrementAndGet();
        if (ordinal > AUSM_CELERITAS_DRAW_PROBE_LIMIT) {
            return;
        }
        Object renderer = (Object) this;
        int visible = ausm$invokeInt(renderer, "getVisibleChunkCount", -1);
        boolean complete = ausm$invokeBoolean(renderer, "isTerrainRenderComplete", false);
        Object manager = ausm$invoke(renderer, "getRenderSectionManager");
        String debug = ausm$invokeString(renderer, "getChunksDebugString", "unknown");
        int glError = GL11.glGetError();
        com.l.ausm.impl.MainMod.LOGGER.info(
                "[AUSMCeleritasDrawProbe] call={} stage={} layer={} visible={} complete={} renderer={} manager={} chunks='{}' glProgram={} arrayBuffer={} glError={}",
                ordinal,
                stage,
                layer,
                visible,
                complete,
                renderer.getClass().getName(),
                manager != null ? manager.getClass().getName() : "null",
                debug,
                GL11.glGetInteger(org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING),
                glError
        );
    }

    @Unique
    private static void ausm$repairTrackerFromLoadedChunks(Object renderer) {
        Object worldObject = com.l.ausm.impl.util.MinecraftReflectionCompat.field(
                renderer, Object.class, null, "world");
        if (!(worldObject instanceof World world)) {
            return;
        }
        ChunkProviderClient provider = com.l.ausm.impl.util.MinecraftReflectionCompat.call(
                world,
                ChunkProviderClient.class,
                null,
                new String[] {"func_72863_F", "getChunkProvider"},
                com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS);
        Object loadedObject = com.l.ausm.impl.util.MinecraftReflectionCompat.field(
                provider, Object.class, null, "field_73236_b", "loadedChunks");
        if (!(loadedObject instanceof Map<?, ?> loaded) || loaded.isEmpty()) {
            return;
        }

        Object tracker = ausm$chunkTracker(world);
        if (tracker == null) {
            return;
        }
        Method statusAdded = ausm$method(tracker, "onChunkStatusAdded", int.class, int.class, int.class);
        if (statusAdded == null) {
            return;
        }
        int worldIdentity = System.identityHashCode(world);
        if (ausm$trackerRepairWorldIdentity != worldIdentity) {
            ausm$trackerRepairWorldIdentity = worldIdentity;
            ausm$trackerRepairAttempts.set(0);
        }
        int attempt = ausm$trackerRepairAttempts.incrementAndGet();
        if (attempt > AUSM_CELERITAS_TRACKER_REPAIR_LIMIT) {
            return;
        }
        int added = 0;
        for (Object value : loaded.values()) {
            if (!(value instanceof Chunk chunk)) {
                continue;
            }
            int chunkX = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(
                    chunk, Integer.MIN_VALUE, "field_76635_g", "x");
            int chunkZ = com.l.ausm.impl.util.MinecraftReflectionCompat.fieldInt(
                    chunk, Integer.MIN_VALUE, "field_76647_h", "z");
            if (chunkX == Integer.MIN_VALUE || chunkZ == Integer.MIN_VALUE) {
                continue;
            }
            try {
                statusAdded.invoke(tracker, chunkX, chunkZ, 3);
                added++;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                break;
            }
        }
        com.l.ausm.impl.MainMod.LOGGER.info(
                "[AUSMCeleritasTrackerRepair] attempt={} loaded={} statusAdded={} visibleBefore={}",
                attempt,
                loaded.size(),
                added,
                ausm$invokeInt(renderer, "getVisibleChunkCount", -1)
        );
    }

    @Unique
    private static Object ausm$chunkTracker(Object world) {
        try {
            Class<?> holder = Class.forName(
                    "org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder",
                    false,
                    CeleritasWorldRendererMixin.class.getClassLoader());
            Method get = holder.getMethod("get", Object.class);
            return get.invoke(null, world);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static Method ausm$method(Object target, String name, Class<?>... parameterTypes) {
        try {
            Method method = target.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static Object ausm$invoke(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Unique
    private static int ausm$invokeInt(Object target, String name, int fallback) {
        Object value = ausm$invoke(target, name);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    @Unique
    private static boolean ausm$invokeBoolean(Object target, String name, boolean fallback) {
        Object value = ausm$invoke(target, name);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    @Unique
    private static String ausm$invokeString(Object target, String name, String fallback) {
        Object value = ausm$invoke(target, name);
        return value != null ? String.valueOf(value) : fallback;
    }

    @Inject(method = "createChunkRenderMatrices", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$useActivePipelineMatrices(CallbackInfoReturnable<Object> cir) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            ausm$matrixConstructorUnavailable = true;
            com.l.ausm.impl.MainMod.LOGGER.warn("[CeleritasCompat] Failed to construct live AUSM chunk matrices", error);
        }
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
                com.l.ausm.impl.MainMod.LOGGER.warn("[CeleritasCompat] Live AUSM chunk matrix bridge unavailable", error);
                return null;
            }
        }
    }
}
