package com.l.ausm.impl.pipeline.compat;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;

import java.lang.reflect.Method;

/** Optional, reflection-only bridge to GPOM's per-quad framed material data. */
public final class GpomFramedQuadMetadata {
    private static final String PROVENANCE = "com.l.gpom.compat.framed.FramedQuadProvenance";

    private static volatile boolean resolved;
    private static Method dataMethod;
    private static Method materialIndexMethod;
    private static Method materialStateMethod;
    private static Method materialIdMethod;
    private static Method materialMetaMethod;
    private static Method emissionMethod;
    private static Method bloomMethod;

    private GpomFramedQuadMetadata() {
    }

    public static Metadata get(BakedQuad quad) {
        if (quad == null || !resolve()) {
            return null;
        }
        try {
            Object data = dataMethod.invoke(null, quad);
            if (data == null) {
                return null;
            }
            Object state = materialStateMethod.invoke(data);
            if (!(state instanceof IBlockState)) {
                return null;
            }
            return new Metadata(
                    ((Number) materialIndexMethod.invoke(data)).intValue(),
                    (IBlockState) state,
                    String.valueOf(materialIdMethod.invoke(data)),
                    ((Number) materialMetaMethod.invoke(data)).intValue(),
                    ((Number) emissionMethod.invoke(data)).intValue(),
                    Boolean.TRUE.equals(bloomMethod.invoke(data))
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return dataMethod != null;
        }
        synchronized (GpomFramedQuadMetadata.class) {
            if (resolved) {
                return dataMethod != null;
            }
            resolved = true;
            try {
                Class<?> provenance = Class.forName(PROVENANCE, false,
                        GpomFramedQuadMetadata.class.getClassLoader());
                dataMethod = provenance.getMethod("data", BakedQuad.class);
                Class<?> data = Class.forName(PROVENANCE + "$QuadData", false,
                        GpomFramedQuadMetadata.class.getClassLoader());
                materialIndexMethod = data.getMethod("materialIndex");
                materialStateMethod = data.getMethod("materialState");
                materialIdMethod = data.getMethod("materialId");
                materialMetaMethod = data.getMethod("materialMeta");
                emissionMethod = data.getMethod("emission");
                bloomMethod = data.getMethod("bloom");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                dataMethod = null;
            }
            return dataMethod != null;
        }
    }

    public record Metadata(int materialIndex, IBlockState materialState, String materialId,
                           int materialMeta, int emission, boolean bloom) {
    }
}
