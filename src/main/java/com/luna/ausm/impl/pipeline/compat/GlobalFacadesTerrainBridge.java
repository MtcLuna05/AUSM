package com.luna.ausm.impl.pipeline.compat;

import java.lang.reflect.Method;

/**
 * Optional bridge used while AUSM's translucent terrain shader is still bound.
 */
public final class GlobalFacadesTerrainBridge {
    private static volatile boolean resolved;
    private static Method renderMethod;

    private GlobalFacadesTerrainBridge() {
    }

    public static void render(float partialTicks) {
        Method method = resolve();
        if (method == null) {
            return;
        }
        try {
            method.invoke(null, partialTicks);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            renderMethod = null;
        }
    }

    private static Method resolve() {
        if (resolved) {
            return renderMethod;
        }
        synchronized (GlobalFacadesTerrainBridge.class) {
            if (!resolved) {
                resolved = true;
                try {
                    Class<?> renderer = Class.forName(
		                    "com.luna.globalfacades.client.render.FacadeWorldRenderer", false,
                            GlobalFacadesTerrainBridge.class.getClassLoader());
                    renderMethod = renderer.getMethod("renderDuringWorldPass", float.class);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    renderMethod = null;
                }
            }
            return renderMethod;
        }
    }
}
