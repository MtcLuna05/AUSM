package com.l.ausm.impl.pipeline.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Queue;
import net.minecraft.util.BlockRenderLayer;

abstract class NothiriumShadowReflectionBindings extends NothiriumShadowRendererBase {
    protected static final class Reflection {
        final Method getRenderer;
        final Method getProvider;
        final Method getTaskDispatcher;
        final Method dispatcherUpdate;
        final Method enumMapGet;
        final Method renderedChunks;
        final Method renderedSections;
        final Method renderedSectionsAll;
        final MethodHandle getVboPart;
        final MethodHandle getVbo;
        final MethodHandle getFirst;
        final MethodHandle getCount;
        final MethodHandle getOffset;
        final MethodHandle getSize;
        final MethodHandle isValid;
        final Method isDirty;
        final Method isEmpty;
        final Method markDirty;
        final Method releaseBuffers;
        final Method canCompile;
        final Method compileAsync;
        final Method worldUtilIsChunkLoaded;
        final MethodHandle getX;
        final MethodHandle getY;
        final MethodHandle getZ;
        final MethodHandle getSectionX;
        final MethodHandle getSectionY;
        final MethodHandle getSectionZ;
        final Field chunks;
        final Field providerChunks;
        final Field dispatcherQueue;
        final Field lastCompileTask;
        final Field lastCompileTaskResult;
        final Field lastTimeRecorded;
        final Field lastTimeEnqueued;
        final Field nonemptyVboParts;
        final Object solid;
        final Object cutout;
        final Object cutoutMipped;
        final Object translucent;
        final Object bloom;

        Reflection(Method getRenderer, Method getProvider, Method getTaskDispatcher, Method dispatcherUpdate,
                   Method enumMapGet, Method renderedChunks, Method renderedSections,
                   Method renderedSectionsAll, MethodHandle getVboPart, MethodHandle getVbo, MethodHandle getFirst,
                   MethodHandle getCount, MethodHandle getOffset, MethodHandle getSize, MethodHandle isValid, Method isDirty,
                   Method isEmpty, Method markDirty, Method releaseBuffers, Method canCompile,
                   Method compileAsync, Method worldUtilIsChunkLoaded, MethodHandle getX, MethodHandle getY, MethodHandle getZ,
                   MethodHandle getSectionX, MethodHandle getSectionY, MethodHandle getSectionZ, Field chunks,
                   Field providerChunks, Field dispatcherQueue, Field lastCompileTask,
                   Field lastCompileTaskResult, Field lastTimeRecorded, Field lastTimeEnqueued, Field nonemptyVboParts,
                   Object solid, Object cutout, Object cutoutMipped, Object translucent, Object bloom) {
            this.getRenderer = getRenderer;
            this.getProvider = getProvider;
            this.getTaskDispatcher = getTaskDispatcher;
            this.dispatcherUpdate = dispatcherUpdate;
            this.enumMapGet = enumMapGet;
            this.renderedChunks = renderedChunks;
            this.renderedSections = renderedSections;
            this.renderedSectionsAll = renderedSectionsAll;
            this.getVboPart = getVboPart;
            this.getVbo = getVbo;
            this.getFirst = getFirst;
            this.getCount = getCount;
            this.getOffset = getOffset;
            this.getSize = getSize;
            this.isValid = isValid;
            this.isDirty = isDirty;
            this.isEmpty = isEmpty;
            this.markDirty = markDirty;
            this.releaseBuffers = releaseBuffers;
            this.canCompile = canCompile;
            this.compileAsync = compileAsync;
            this.worldUtilIsChunkLoaded = worldUtilIsChunkLoaded;
            this.getX = getX;
            this.getY = getY;
            this.getZ = getZ;
            this.getSectionX = getSectionX;
            this.getSectionY = getSectionY;
            this.getSectionZ = getSectionZ;
            this.chunks = chunks;
            this.providerChunks = providerChunks;
            this.dispatcherQueue = dispatcherQueue;
            this.lastCompileTask = lastCompileTask;
            this.lastCompileTaskResult = lastCompileTaskResult;
            this.lastTimeRecorded = lastTimeRecorded;
            this.lastTimeEnqueued = lastTimeEnqueued;
            this.nonemptyVboParts = nonemptyVboParts;
            this.solid = solid;
            this.cutout = cutout;
            this.cutoutMipped = cutoutMipped;
            this.translucent = translucent;
            this.bloom = bloom;
        }

        int getX(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockX();
            }
            return invokeInt(getX, chunk);
        }

        Object getVboPart(Object chunk, Object pass) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$vboPart(pass);
            }
            try {
                return (Object) getVboPart.invokeExact(chunk, pass);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        boolean isValid(Object part) throws ReflectiveOperationException {
            try {
                return (boolean) isValid.invokeExact(part);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        int getY(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockY();
            }
            return invokeInt(getY, chunk);
        }

        int getZ(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$blockZ();
            }
            return invokeInt(getZ, chunk);
        }

        int getVbo(Object part) throws ReflectiveOperationException {
            return invokeInt(getVbo, part);
        }

        int getFirst(Object part) throws ReflectiveOperationException {
            return invokeInt(getFirst, part);
        }

        int getCount(Object part) throws ReflectiveOperationException {
            return invokeInt(getCount, part);
        }

        int getOffset(Object part) throws ReflectiveOperationException {
            return invokeInt(getOffset, part);
        }

        int getSize(Object part) throws ReflectiveOperationException {
            return invokeInt(getSize, part);
        }

        static int invokeInt(MethodHandle getter, Object target) throws ReflectiveOperationException {
            try {
                return (int) getter.invokeExact(target);
            } catch (RuntimeException | Error exception) {
                throw exception;
            } catch (Throwable throwable) {
                throw new ReflectiveOperationException(throwable);
            }
        }

        static MethodHandle intGetter(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(int.class, Object.class));
        }

        static MethodHandle objectMethod(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(Object.class, Object.class, Object.class));
        }

        static MethodHandle booleanGetter(Method method) throws IllegalAccessException {
            return MethodHandles.publicLookup()
                    .unreflect(method)
                    .asType(MethodType.methodType(boolean.class, Object.class));
        }

        static NothiriumShadowRenderer.Reflection load() {
            try {
                Class<?> managerClass = Class.forName("meldexun.nothirium.mc.renderer.ChunkRenderManager");
                Class<?> rendererBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractChunkRenderer");
                Class<?> providerBaseClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunkProvider");
                Class<?> abstractChunkClass = Class.forName("meldexun.nothirium.renderer.chunk.AbstractRenderChunk");
                Class<?> chunkRendererClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IChunkRenderer");
                Class<?> dispatcherClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunkDispatcher");
                Class<?> renderChunkClass = Class.forName("meldexun.nothirium.api.renderer.chunk.IRenderChunk");
                Class<?> vboPartClass = Class.forName("meldexun.nothirium.api.renderer.IVBOPart");
                Class<?> passClass = Class.forName("meldexun.nothirium.api.renderer.chunk.ChunkRenderPass");
                Class<?> enumMapClass = Class.forName("meldexun.nothirium.util.collection.Enum2ObjMap");

                Method getRenderer = managerClass.getMethod("getRenderer");
                Method getProvider = managerClass.getMethod("getProvider");
                Method getTaskDispatcher = managerClass.getMethod("getTaskDispatcher");
                Method dispatcherUpdate = dispatcherClass.getMethod("update");
                Method renderedSections = managerClass.getMethod("renderedSections", passClass);
                Method renderedSectionsAll = managerClass.getMethod("renderedSections");
                Method enumMapGet = enumMapClass.getMethod("get", Enum.class);
                Method renderedChunks = rendererBaseClass.getMethod("renderedChunks", passClass);
                Method getVboPart = renderChunkClass.getMethod("getVBOPart", passClass);
                Method getVbo = vboPartClass.getMethod("getVBO");
                Method getFirst = vboPartClass.getMethod("getFirst");
                Method getCount = vboPartClass.getMethod("getCount");
                Method getOffset = vboPartClass.getMethod("getOffset");
                Method getSize = vboPartClass.getMethod("getSize");
                Method isValid = vboPartClass.getMethod("isValid");
                Method isDirty = abstractChunkClass.getMethod("isDirty");
                Method isEmpty = renderChunkClass.getMethod("isEmpty");
                Method markDirty = abstractChunkClass.getMethod("markDirty");
                Method releaseBuffers = abstractChunkClass.getMethod("releaseBuffers");
                Method canCompile = abstractChunkClass.getDeclaredMethod("canCompile");
                canCompile.setAccessible(true);
                Method compileAsync = abstractChunkClass.getMethod("compileAsync", chunkRendererClass, dispatcherClass);
                Class<?> worldUtilClass = Class.forName("meldexun.nothirium.mc.util.WorldUtil");
                Method worldUtilIsChunkLoaded = worldUtilClass.getMethod("isChunkLoaded", int.class, int.class);
                Method getX = renderChunkClass.getMethod("getX");
                Method getY = renderChunkClass.getMethod("getY");
                Method getZ = renderChunkClass.getMethod("getZ");
                Method getSectionX = renderChunkClass.getMethod("getSectionX");
                Method getSectionY = renderChunkClass.getMethod("getSectionY");
                Method getSectionZ = renderChunkClass.getMethod("getSectionZ");
                Field chunks = findField(rendererBaseClass, "chunks");
                chunks.setAccessible(true);
                Field providerChunks = findField(providerBaseClass, "chunks");
                providerChunks.setAccessible(true);
                Field dispatcherQueue = findOptionalDispatcherQueueField();
                Field lastCompileTask = findField(abstractChunkClass, "lastCompileTask");
                lastCompileTask.setAccessible(true);
                Field lastCompileTaskResult = findField(abstractChunkClass, "lastCompileTaskResult");
                lastCompileTaskResult.setAccessible(true);
                Field lastTimeRecorded = findField(abstractChunkClass, "lastTimeRecorded");
                lastTimeRecorded.setAccessible(true);
                Field lastTimeEnqueued = findField(abstractChunkClass, "lastTimeEnqueued");
                lastTimeEnqueued.setAccessible(true);
                Field nonemptyVboParts = findField(abstractChunkClass, "nonemptyVboParts");
                nonemptyVboParts.setAccessible(true);

                Object solid = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "SOLID");
                Object cutout = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT");
                Object cutoutMipped = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "CUTOUT_MIPPED");
                Object translucent = Enum.valueOf((Class<? extends Enum>) passClass.asSubclass(Enum.class), "TRANSLUCENT");
                Object bloom = enumValueOrNull(passClass, "BLOOM");
                return new NothiriumShadowRenderer.Reflection(
                        getRenderer,
                        getProvider,
                        getTaskDispatcher,
                        dispatcherUpdate,
                        enumMapGet,
                        renderedChunks,
                        renderedSections,
                        renderedSectionsAll,
                        objectMethod(getVboPart),
                        intGetter(getVbo),
                        intGetter(getFirst),
                        intGetter(getCount),
                        intGetter(getOffset),
                        intGetter(getSize),
                        booleanGetter(isValid),
                        isDirty,
                        isEmpty,
                        markDirty,
                        releaseBuffers,
                        canCompile,
                        compileAsync,
                        worldUtilIsChunkLoaded,
                        intGetter(getX),
                        intGetter(getY),
                        intGetter(getZ),
                        intGetter(getSectionX),
                        intGetter(getSectionY),
                        intGetter(getSectionZ),
                        chunks,
                        providerChunks,
                        dispatcherQueue,
                        lastCompileTask,
                        lastCompileTaskResult,
                        lastTimeRecorded,
                        lastTimeEnqueued,
                        nonemptyVboParts,
                        solid,
                        cutout,
                        cutoutMipped,
                        translucent,
                        bloom
                );
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        boolean isChunkDirty(Object chunk) throws ReflectiveOperationException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$isDirty();
            }
            return (Boolean) isDirty.invoke(chunk);
        }

        boolean isChunkEmpty(Object chunk) throws ReflectiveOperationException {
            return (Boolean) isEmpty.invoke(chunk);
        }

        Boolean canCompile(Object chunk) throws ReflectiveOperationException {
            return (Boolean) canCompile.invoke(chunk);
        }

        int getSectionX(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionX, chunk);
        }

        int getSectionY(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionY, chunk);
        }

        int getSectionZ(Object chunk) throws ReflectiveOperationException {
            return invokeInt(getSectionZ, chunk);
        }

        Object lastCompileTask(Object chunk) throws IllegalAccessException {
            return lastCompileTask.get(chunk);
        }

        Object lastCompileTaskResult(Object chunk) throws IllegalAccessException {
            if (chunk instanceof NothiriumShadowChunkAccess access) {
                return access.ausm$lastCompileTaskResult();
            }
            return lastCompileTaskResult.get(chunk);
        }

        int lastTimeRecorded(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeRecorded.get(chunk);
        }

        int lastTimeEnqueued(Object chunk) throws IllegalAccessException {
            return (Integer) lastTimeEnqueued.get(chunk);
        }

        int nonemptyVboParts(Object chunk) throws IllegalAccessException {
            return (Integer) nonemptyVboParts.get(chunk);
        }

        int dispatcherQueueSize(Object dispatcher) throws IllegalAccessException {
            if (dispatcherQueue == null || !dispatcherQueue.getDeclaringClass().isInstance(dispatcher)) {
                return -1;
            }

            Object queue = dispatcherQueue.get(dispatcher);
            return queue instanceof Collection<?> collection ? collection.size() : -1;
        }

        int drainDispatcherQueue(Object dispatcher, int maximumTasks) throws IllegalAccessException {
            if (maximumTasks <= 0
                    || dispatcherQueue == null
                    || !dispatcherQueue.getDeclaringClass().isInstance(dispatcher)) {
                return -1;
            }
            Object queuedTasks = dispatcherQueue.get(dispatcher);
            if (!(queuedTasks instanceof Queue<?> queue)) {
                return -1;
            }
            int drained = 0;
            while (drained < maximumTasks) {
                Object task = queue.poll();
                if (!(task instanceof Runnable runnable)) {
                    break;
                }
                runnable.run();
                drained++;
            }
            return drained;
        }

        Object passFor(BlockRenderLayer layer) {
            return switch (layer) {
                case SOLID -> solid;
                case CUTOUT -> cutout;
                case CUTOUT_MIPPED -> cutoutMipped;
                case TRANSLUCENT -> translucent;
                default -> "BLOOM".equals(layer.name()) ? bloom : null;
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static Object enumValueOrNull(Class<?> enumClass, String name) {
            try {
                return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        static Field findField(Class<?> type, String name) throws NoSuchFieldException {
            Class<?> current = type;
            while (current != null) {
                try {
                    return current.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(name);
        }

        static Field findOptionalDispatcherQueueField() {
            try {
                Class<?> dispatcherImplClass = Class.forName("meldexun.nothirium.mc.renderer.chunk.RenderChunkDispatcher");
                Field field = findField(dispatcherImplClass, "taskQueue");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }
}
