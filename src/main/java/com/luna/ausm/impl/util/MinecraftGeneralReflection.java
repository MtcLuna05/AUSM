package com.luna.ausm.impl.util;

import com.luna.ausm.impl.MainMod;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.fluids.FluidStack;

abstract class MinecraftGeneralReflection extends MinecraftGlStateReflection {
    public static Object invoke(Object target, String[] names, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Method method = MinecraftReflectionCompat.findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    public static <T> T call(Object target, Class<T> type, T fallback, String[] names,
                             Class<?>[] parameterTypes, Object... args) {
        Object value = MinecraftReflectionCompat.invoke(target, names, parameterTypes, args);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static int callInt(Object target, String[] names, Class<?>[] parameterTypes, int fallback, Object... args) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.invoke(target, names, parameterTypes, args), fallback);
    }

    public static long callLong(Object target, String[] names, Class<?>[] parameterTypes, long fallback, Object... args) {
        return MinecraftReflectionCompat.longValue(MinecraftReflectionCompat.invoke(target, names, parameterTypes, args), fallback);
    }

    public static float callFloat(Object target, String[] names, Class<?>[] parameterTypes, float fallback, Object... args) {
        return MinecraftReflectionCompat.floatValue(MinecraftReflectionCompat.invoke(target, names, parameterTypes, args), fallback);
    }

    public static double callDouble(Object target, String[] names, Class<?>[] parameterTypes, double fallback, Object... args) {
        return MinecraftReflectionCompat.doubleValue(MinecraftReflectionCompat.invoke(target, names, parameterTypes, args), fallback);
    }

    public static boolean callBoolean(Object target, String[] names, Class<?>[] parameterTypes,
                                      boolean fallback, Object... args) {
        return MinecraftReflectionCompat.booleanValue(MinecraftReflectionCompat.invoke(target, names, parameterTypes, args), fallback);
    }

    public static <T> T callStatic(Class<?> owner, Class<T> type, T fallback, String[] names,
                                   Class<?>[] parameterTypes, Object... args) {
        Object value = MinecraftReflectionCompat.invokeStatic(owner, names, parameterTypes, args);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static <T> T field(Object target, Class<T> type, T fallback, String... names) {
        Object value = MinecraftReflectionCompat.getField(target, names);
        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    public static <T> T firstInstanceFieldOfType(Object target, Class<T> type) {
        if (target == null || type == null) {
            return null;
        }
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !type.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    if (type.isInstance(value)) {
                        return type.cast(value);
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                }
            }
        }
        return null;
    }

    public static OpenBlocksTankFluidInfo openBlocksTankFluidInfo(TileEntity tileEntity) {
        if (tileEntity == null
                || !"openblocks.common.tileentity.TileEntityTank".equals(tileEntity.getClass().getName())) {
            return OpenBlocksTankFluidInfo.EMPTY;
        }
        Object renderData = MinecraftReflectionCompat.invoke(tileEntity, new String[]{"getRenderFluidData"}, NO_PARAMETERS);
        Object stack = MinecraftReflectionCompat.invoke(renderData, new String[]{"getFluid"}, NO_PARAMETERS);
        if (!(stack instanceof FluidStack)) {
            return new OpenBlocksTankFluidInfo(false, -1, -1, "none");
        }
        Object fluid = MinecraftReflectionCompat.invoke(stack, new String[]{"getFluid"}, NO_PARAMETERS);
        int color = MinecraftReflectionCompat.callInt(fluid, new String[]{"getColor"},
                new Class<?>[]{FluidStack.class}, -1, stack);
        if (color < 0) {
            color = MinecraftReflectionCompat.callInt(fluid, new String[]{"getColor"}, NO_PARAMETERS, -1);
        }
        String name = MinecraftReflectionCompat.call(fluid, String.class,
                fluid != null ? fluid.getClass().getName() : "unknown",
                new String[]{"getName"}, NO_PARAMETERS);
        return new OpenBlocksTankFluidInfo(true, MinecraftReflectionCompat.fieldInt(stack, -1, "amount"), color, name);
    }

    public static Object worldProviderSkyRenderer(WorldProvider provider) {
        if (provider == null) {
            return null;
        }
        Object renderer = MinecraftReflectionCompat.invoke(provider, new String[]{"getSkyRenderer"}, NO_PARAMETERS);
        return renderer != null ? renderer : MinecraftReflectionCompat.field(provider, Object.class, null, "skyRenderer", "field_76579_a");
    }

    public static boolean setWorldProviderSkyRenderer(WorldProvider provider, Object renderer) {
        if (provider == null) {
            return false;
        }
        MinecraftReflectionCompat.setField(provider, renderer, "skyRenderer", "field_76579_a");
        if (MinecraftReflectionCompat.worldProviderSkyRenderer(provider) == renderer) {
            return true;
        }
        MinecraftReflectionCompat.invoke(provider, new String[]{"setSkyRenderer"}, new Class<?>[]{IRenderHandler.class}, renderer);
        return MinecraftReflectionCompat.worldProviderSkyRenderer(provider) == renderer;
    }

    public static int fieldInt(Object target, int fallback, String... names) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.getField(target, names), fallback);
    }

    public static float fieldFloat(Object target, float fallback, String... names) {
        return MinecraftReflectionCompat.floatValue(MinecraftReflectionCompat.getField(target, names), fallback);
    }

    public static double fieldDouble(Object target, double fallback, String... names) {
        return MinecraftReflectionCompat.doubleValue(MinecraftReflectionCompat.getField(target, names), fallback);
    }

    public static boolean fieldBoolean(Object target, boolean fallback, String... names) {
        return MinecraftReflectionCompat.booleanValue(MinecraftReflectionCompat.getField(target, names), fallback);
    }

    protected static Object invokePropagating(Object target, String[] names, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Method method = MinecraftReflectionCompat.findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                return null;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    protected static Object invokeStatic(Class<?> owner, String[] names, Class<?>[] parameterTypes, Object... args) {
        for (String name : names) {
            Method method = MinecraftReflectionCompat.findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(null, args);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    protected static Method findMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        Class<?>[] parameters = parameterTypes != null ? parameterTypes : NO_PARAMETERS;
        MethodLookupCache localCache = THREAD_METHOD_LOOKUP_CACHE.get();
        Method local = localCache.lookup(owner, name, parameters);
        if (localCache.hit) {
            return local;
        }
        MethodKey key = new MethodKey(owner, name, parameters);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            localCache.store(owner, name, parameters, cached);
            return cached;
        }
        if (MISSING_METHODS.contains(key)) {
            localCache.store(owner, name, parameters, null);
            return null;
        }
        Method found = LinkageSafeMethodLookup.find(owner, name, parameters);
        if (found != null) {
            try {
                found.setAccessible(true);
            } catch (SecurityException ignored) {
            }
            Method existing = METHOD_CACHE.putIfAbsent(key, found);
            Method resolved = existing != null ? existing : found;
            localCache.store(owner, name, parameters, resolved);
            return resolved;
        }
        MISSING_METHODS.add(key);
        localCache.store(owner, name, parameters, null);
        return null;
    }

    protected static Method findCompatibleDeclaredMethod(Class<?> owner, String name, Class<?> propertyClass, Set<Class<?>> visited) {
        if (owner == null || propertyClass == null) {
            return null;
        }
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            if (!visited.add(current)) {
                continue;
            }
            try {
                for (Method method : current.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(name) && parameters.length == 1
                            && parameters[0].isAssignableFrom(propertyClass)) {
                        return method;
                    }
                }
            } catch (SecurityException | LinkageError ignored) {
            }
            Method interfaceMethod = MinecraftReflectionCompat.findCompatibleInterfaceMethod(current, name, propertyClass, visited);
            if (interfaceMethod != null) {
                return interfaceMethod;
            }
        }
        return null;
    }

    protected static Method findCompatibleInterfaceMethod(Class<?> owner, String name, Class<?> propertyClass, Set<Class<?>> visited) {
        for (Class<?> interfaceClass : owner.getInterfaces()) {
            if (!visited.add(interfaceClass)) {
                continue;
            }
            try {
                for (Method method : interfaceClass.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (method.getName().equals(name) && parameters.length == 1
                            && parameters[0].isAssignableFrom(propertyClass)) {
                        return method;
                    }
                }
            } catch (SecurityException | LinkageError ignored) {
            }
            Method nested = MinecraftReflectionCompat.findCompatibleInterfaceMethod(interfaceClass, name, propertyClass, visited);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    protected static MethodHandle methodHandle(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
        if (owner == null) {
            return null;
        }
        for (String name : names) {
            Method method = MinecraftReflectionCompat.findMethod(owner, name, parameterTypes);
            if (method == null) {
                continue;
            }
            try {
                return MethodHandles.lookup().unreflect(method);
            } catch (IllegalAccessException ignored) {
            }
        }
        return null;
    }

    protected static MethodHandle exactMethodHandle(Class<?> owner, String[] names, Class<?>[] parameterTypes,
                                                    MethodType exactType) {
        MethodHandle handle = MinecraftReflectionCompat.methodHandle(owner, names, parameterTypes);
        if (handle == null) {
            return null;
        }
        try {
            return handle.asType(exactType);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    protected static void logHotPathHandleFailure(String route, Throwable failure) {
        if (FAILED_HOT_PATH_HANDLES.add(route)) {
            MainMod.LOGGER.warn(
                    "[AUSMPerformanceRouteProbe] route={} exact-handle invocation failed; using compatibility fallback: {}",
                    route, failure.toString());
        }
    }

    protected static MethodHandle staticMethodHandle(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
        return MinecraftReflectionCompat.methodHandle(owner, names, parameterTypes);
    }

    protected static int invokeInt(MethodHandle handle, Object target, int fallback) {
        if (target == null || handle == null) {
            return fallback;
        }
        try {
            return (int) handle.invoke(target);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    protected static Object invokeReference(MethodHandle handle, Object target) {
        if (target == null || handle == null) {
            return null;
        }
        try {
            return handle.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    protected static int invokeInt2(MethodHandle handle, Object target, Object first, Object second, int fallback) {
        if (target == null || first == null || second == null || handle == null) {
            return fallback;
        }
        try {
            return (int) handle.invoke(target, first, second);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    protected static int invokeInt4(MethodHandle handle, Object target, Object first, Object second, Object third,
                                    int fourth, int fallback) {
        if (target == null || handle == null) {
            return fallback;
        }
        try {
            return (int) handle.invoke(target, first, second, third, fourth);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    protected static int blockPosFieldInt(Field field, BlockPos pos, int fallback) {
        if (field == null || pos == null) {
            return fallback;
        }
        try {
            return field.getInt(pos);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            return fallback;
        }
    }

    protected static Object getField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Field field = MinecraftReflectionCompat.findField(owner, name);
            if (field != null) {
                try {
                    return field.get(target instanceof Class<?> ? null : target);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    public static void setField(Object target, Object value, String... names) {
        if (target == null) {
            return;
        }
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (String name : names) {
            Field field = MinecraftReflectionCompat.findField(owner, name);
            if (field != null) {
                try {
                    field.set(target instanceof Class<?> ? null : target, value);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
    }

    protected static Object getStaticField(Class<?> owner, String srgName, String mcpName) {
        for (String name : new String[]{srgName, mcpName}) {
            Field field = MinecraftReflectionCompat.findField(owner, name);
            if (field != null) {
                try {
                    return field.get(null);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    protected static int staticIntField(Class<?> owner, int fallback, String... names) {
        for (String name : names) {
            Field field = MinecraftReflectionCompat.findField(owner, name);
            if (field != null) {
                try {
                    return field.getInt(null);
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
        }
        return fallback;
    }

    public static Field findField(Class<?> owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        FieldLookupCache localCache = THREAD_FIELD_LOOKUP_CACHE.get();
        Field local = localCache.lookup(owner, name);
        if (localCache.hit) {
            return local;
        }
        FieldKey key = new FieldKey(owner, name);
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            localCache.store(owner, name, cached);
            return cached;
        }
        if (MISSING_FIELDS.contains(key)) {
            localCache.store(owner, name, null);
            return null;
        }
        try {
            Field publicField = owner.getField(name);
            publicField.setAccessible(true);
            Field existing = FIELD_CACHE.putIfAbsent(key, publicField);
            Field resolved = existing != null ? existing : publicField;
            localCache.store(owner, name, resolved);
            return resolved;
        } catch (NoSuchFieldException | SecurityException ignored) {
        }
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                Field existing = FIELD_CACHE.putIfAbsent(key, field);
                Field resolved = existing != null ? existing : field;
                localCache.store(owner, name, resolved);
                return resolved;
            } catch (NoSuchFieldException | SecurityException ignored) {
            }
        }
        MISSING_FIELDS.add(key);
        localCache.store(owner, name, null);
        return null;
    }

    protected static Field firstField(Class<?> owner, String... names) {
        for (String name : names) {
            Field field = MinecraftReflectionCompat.findField(owner, name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    protected static int openGlHelperInt(String srgName, String mcpName, int fallback) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.getStaticField(OpenGlHelper.class, srgName, mcpName), fallback);
    }

    protected static int framebufferInt(Framebuffer framebuffer, String srgName, String mcpName) {
        return MinecraftReflectionCompat.intValue(MinecraftReflectionCompat.getField(framebuffer, srgName, mcpName), 0);
    }

    protected static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    protected static long longValue(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }

    protected static float floatValue(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    protected static double doubleValue(Object value, double fallback) {
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    protected static boolean booleanValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    protected static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
