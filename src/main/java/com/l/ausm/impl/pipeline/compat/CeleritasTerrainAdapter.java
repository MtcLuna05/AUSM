package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.MainMod;

import java.lang.reflect.Constructor;

/**
 * Runtime-only Celeritas data adapter surface. The optional dependency is
 * resolved lazily so ordinary and Nothirium-only launches never link Celeritas
 * classes. It must never invoke Celeritas rendering or shader APIs.
 */
public final class CeleritasTerrainAdapter {
    private static final String VANILLA_LIKE_VERTEX_TYPE =
            "org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.VanillaLikeChunkVertex";
    private static final String CHUNK_VERTEX_TYPE =
            "org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType";
    private static final String CHUNK_RENDERER =
            "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer";

    private static volatile boolean resolved;
    private static volatile Constructor<?> vanillaLikeVertexConstructor;
    private static volatile boolean adapterSurfaceAvailable;
    private static volatile boolean logged;

    private CeleritasTerrainAdapter() {
    }

    /**
     * Celeritas can compile a float-position, vanilla-like mesh. This is the
     * only supplied format suitable for a future AUSM-owned attribute bridge;
     * its compact 20-byte format cannot be consumed by GLSL compatibility
     * shaders.
     */
    public static boolean hasVanillaLikeAdapterSurface() {
        resolve();
        return adapterSurfaceAvailable;
    }

    public static Object createVanillaLikeVertexType() {
        resolve();
        if (vanillaLikeVertexConstructor == null) {
            return null;
        }
        try {
            return vanillaLikeVertexConstructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (CeleritasTerrainAdapter.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            try {
                ClassLoader loader = CeleritasTerrainAdapter.class.getClassLoader();
                Class<?> vertexType = Class.forName(CHUNK_VERTEX_TYPE, false, loader);
                Class<?> renderer = Class.forName(CHUNK_RENDERER, false, loader);
                Class<?> vanillaLike = Class.forName(VANILLA_LIKE_VERTEX_TYPE, false, loader);
                Constructor<?> constructor = vanillaLike.getDeclaredConstructor();
                constructor.setAccessible(true);
                vanillaLikeVertexConstructor = constructor;
                adapterSurfaceAvailable = vertexType.isAssignableFrom(vanillaLike)
                        && renderer != null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                vanillaLikeVertexConstructor = null;
                adapterSurfaceAvailable = false;
            }
            if (!logged) {
                logged = true;
                MainMod.LOGGER.info("[CeleritasCompat] AUSM terrain-adapter vertex surface available={}",
                        adapterSurfaceAvailable);
            }
        }
    }
}
