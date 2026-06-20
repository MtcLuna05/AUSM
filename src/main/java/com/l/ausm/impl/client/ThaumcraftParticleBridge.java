package com.l.ausm.impl.client;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ThaumcraftParticleBridge {
    private static final String ENGINE_CLASS = "thaumcraft.client.fx.ParticleEngine";
    private static final String ENGINE_RESOURCE = "thaumcraft/client/fx/ParticleEngine.class";
    private static final int MAX_LOGS = 8;
    private static final ThaumcraftParticleBridge INSTANCE = new ThaumcraftParticleBridge();

    private static boolean registered;
    private static Class<?> engineClass;
    private static Method renderTickMethod;
    private static Method renderWorldLastMethod;
    private static Method updateParticlesMethod;
    private static int resolveLogs;
    private static int invokeFailureLogs;

    private ThaumcraftParticleBridge() {
    }

    public static void init() {
        if (registered || !Loader.isModLoaded("thaumcraft") || !Loader.isModLoaded("gpom")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
        MainMod.LOGGER.info("[AUSMThaumcraftParticles] Installed reflective ParticleEngine bridge for GPOM subscriber skip");
    }

    public static boolean isParticleEngineAvailable() {
        return INSTANCE.resolveEngine();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!resolveEngine()) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        context.prepareExternalWorldOverlayRender();
        try {
            invoke(renderWorldLastMethod, event, "RenderWorldLastEvent");
        } finally {
            context.finishExternalWorldOverlayRender("Thaumcraft particles");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!resolveEngine()) {
            return;
        }
        PipelineContext context = PipelineContext.getInstance();
        context.prepareExternalOverlayRender("Thaumcraft particles");
        try {
            invoke(renderTickMethod, event, "RenderTickEvent");
        } finally {
            context.finishExternalOverlayRender("Thaumcraft particles");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!resolveEngine()) {
            return;
        }
        invoke(updateParticlesMethod, event, "ClientTickEvent");
    }

    private boolean resolveEngine() {
        if (engineClass != null) {
            return true;
        }

        Throwable lastFailure = null;
        for (ClassLoader loader : candidateClassLoaders()) {
            if (loader == null) {
                continue;
            }
            try {
                Class<?> resolved = Class.forName(ENGINE_CLASS, true, loader);
                renderTickMethod = resolved.getDeclaredMethod("renderTick", TickEvent.RenderTickEvent.class);
                renderWorldLastMethod = resolved.getDeclaredMethod("onRenderWorldLast", RenderWorldLastEvent.class);
                updateParticlesMethod = resolved.getDeclaredMethod("updateParticles", TickEvent.ClientTickEvent.class);
                renderTickMethod.setAccessible(true);
                renderWorldLastMethod.setAccessible(true);
                updateParticlesMethod.setAccessible(true);
                engineClass = resolved;
                MainMod.LOGGER.info("[AUSMThaumcraftParticles] Resolved {} with loader={} source={}",
                        ENGINE_CLASS,
                        loader.getClass().getName(),
                        resourceSource(loader));
                return true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                lastFailure = failure;
            }
        }

        if (resolveLogs < MAX_LOGS) {
            resolveLogs++;
            MainMod.LOGGER.warn("[AUSMThaumcraftParticles] Unable to resolve {} attempt={} resources={} failure={}: {}",
                    ENGINE_CLASS,
                    resolveLogs,
                    resourceSummary(),
                    lastFailure == null ? "none" : lastFailure.getClass().getName(),
                    lastFailure == null ? "none" : lastFailure.getMessage());
        }
        return false;
    }

    private static Set<ClassLoader> candidateClassLoaders() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        loaders.add(Launch.classLoader);
        try {
            loaders.add(Loader.instance().getModClassLoader());
        } catch (RuntimeException ignored) {
            // Loader may be unavailable during early client bootstrap.
        }
        loaders.add(Thread.currentThread().getContextClassLoader());
        loaders.add(ThaumcraftParticleBridge.class.getClassLoader());
        loaders.add(ClassLoader.getSystemClassLoader());
        return loaders;
    }

    private static void invoke(Method method, Object event, String source) {
        try {
            method.invoke(null, event);
        } catch (IllegalAccessException failure) {
            logInvokeFailure(source, failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            logInvokeFailure(source, cause);
        }
    }

    private static void logInvokeFailure(String source, Throwable failure) {
        if (invokeFailureLogs >= MAX_LOGS) {
            return;
        }
        invokeFailureLogs++;
        MainMod.LOGGER.warn("[AUSMThaumcraftParticles] ParticleEngine invocation failed source={} failure={}: {}",
                source,
                failure.getClass().getName(),
                failure.getMessage());
    }

    private static String resourceSummary() {
        StringBuilder builder = new StringBuilder();
        for (ClassLoader loader : candidateClassLoaders()) {
            if (loader == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(loader.getClass().getSimpleName()).append('=').append(resourceSource(loader));
        }
        return builder.toString();
    }

    private static String resourceSource(ClassLoader loader) {
        try {
            URL url = loader.getResource(ENGINE_RESOURCE);
            return url == null ? "missing" : url.toString();
        } catch (RuntimeException failure) {
            return "error:" + failure.getClass().getSimpleName();
        }
    }
}
