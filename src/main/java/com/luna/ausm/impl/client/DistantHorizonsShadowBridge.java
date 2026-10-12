package com.luna.ausm.impl.client;

import com.luna.ausm.impl.pipeline.DistantHorizonsShadowRendering;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.locks.StampedLock;
import net.minecraft.entity.Entity;

/** Installed DH 3.3.3 OpenGL boundary; never replaces its main-camera render list.
 * Internal access stays reflective because only the public DH API is a build dependency.
 */
public final class DistantHorizonsShadowBridge {
    private static final ArrayList<Object> SECTIONS = new ArrayList<>();
    private static final Access ACCESS;

    static {
        try {
            ACCESS = new Access();
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private DistantHorizonsShadowBridge() { }

    public static int render(float partialTicks) throws ReflectiveOperationException {
        Object world = ACCESS.world.invoke(null);
        Object client = ACCESS.clientGet.invoke(ACCESS.injector, ACCESS.clientType);
        Object wrapper = ACCESS.wrapper.invoke(client);
        if (world == null || wrapper == null) return 0;
        Object level = ACCESS.level.invoke(world, wrapper);
        if (level == null) return 0;
        Object handler = ACCESS.handler.invoke(level);
        if (handler == null) return 0;
        Entity camera = MinecraftReflectionCompat.renderViewEntity(MinecraftReflectionCompat.minecraft());
        if (camera == null) return 0;
        double x = interpolate(MinecraftReflectionCompat.lastTickPosX(camera), MinecraftReflectionCompat.posX(camera), partialTicks);
        double y = interpolate(MinecraftReflectionCompat.lastTickPosY(camera), MinecraftReflectionCompat.posY(camera), partialTicks);
        double z = interpolate(MinecraftReflectionCompat.lastTickPosZ(camera), MinecraftReflectionCompat.posZ(camera), partialTicks);
        int minimumY = (Integer) ACCESS.minHeight.invoke(wrapper);
        int maximumY = (Integer) ACCESS.maxHeight.invoke(wrapper);
        int draws = 0;
        try {
            ACCESS.populate.invoke(ACCESS.quadTree.get(handler), SECTIONS);
            for (Object section : SECTIONS) {
                if (section == null || !(Boolean) ACCESS.enabled.invoke(section)) continue;
                long pos = ACCESS.sectionPos.getLong(section);
                int width = (Integer) ACCESS.width.invoke(null, pos);
                if (!DistantHorizonsShadowRendering.intersects(
                        (Integer) ACCESS.minX.invoke(null, pos) - x, minimumY - y,
                        (Integer) ACCESS.minZ.invoke(null, pos) - z, width, maximumY - minimumY)) continue;
                Object container = ACCESS.container.get(section);
                if (container == null || !ACCESS.uploaded.getBoolean(container)) continue;
                Object[] buffers = (Object[]) ACCESS.opaque.get(container);
                if (buffers == null) continue;
                Object corner = ACCESS.corner.get(container);
                float dx = (float) ((Integer) ACCESS.x.invoke(corner) - x);
                float dy = (float) ((Integer) ACCESS.y.invoke(corner) - y);
                float dz = (float) ((Integer) ACCESS.z.invoke(corner) - z);
                for (Object vbo : buffers) {
                    if (vbo == null || !ACCESS.vertexType.isInstance(vbo)) continue;
                    Object ibo = ACCESS.ibo.invoke(vbo);
                    if (ibo == null) continue;
                    StampedLock vertexLock = (StampedLock) ACCESS.lock.get(vbo);
                    StampedLock indexLock = (StampedLock) ACCESS.lock.get(ibo);
                    // Match DH's locks while asynchronous uploads replace buffers.
                    long vertexStamp = vertexLock.readLock();
                    long indexStamp = indexLock.readLock();
                    try {
                        int vertices = (Integer) ACCESS.count.invoke(vbo);
                        int vertexId = (Integer) ACCESS.id.invoke(vbo);
                        int indexId = (Integer) ACCESS.id.invoke(ibo);
                        if (vertices <= 0 || vertexId == 0 || indexId == 0) continue;
                        DistantHorizonsShadowRendering.draw(vertexId, indexId,
                                (Integer) ACCESS.indexType.invoke(ibo), vertices, dx, dy, dz);
                        draws++;
                    } finally {
                        indexLock.unlockRead(indexStamp);
                        vertexLock.unlockRead(vertexStamp);
                    }
                }
            }
        } finally {
            SECTIONS.clear();
        }
        return draws;
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static final class Access {
        final Class<?> clientType = type("core.wrapperInterfaces.minecraft.IMinecraftClientWrapper");
        final Class<?> vertexType = type("common.render.openGl.glObject.buffer.GLVertexBuffer");
        final Object injector = type("core.dependencyInjection.SingletonInjector").getField("INSTANCE").get(null);
        final Method clientGet = injector.getClass().getMethod("get", Class.class);
        final Method world = type("core.api.internal.SharedApi").getMethod("tryGetDhClientWorld");
        final Method wrapper = clientType.getMethod("getWrappedClientLevel");
        final Method level = type("core.world.IDhClientWorld").getMethod("getClientLevel", type("core.wrapperInterfaces.world.ILevelWrapper"));
        final Method handler = type("core.level.IDhLevel").getMethod("getRenderBufferHandler");
        final Field quadTree = type("core.render.RenderBufferHandler").getField("lodQuadTree");
        final Method populate = quadTree.getType().getMethod("populateListWithEnabledRenderSections", ArrayList.class);
        final Class<?> section = type("core.render.QuadTree.LodRenderSection");
        final Field sectionPos = section.getField("pos");
        final Field container = section.getField("renderBufferContainer");
        final Method enabled = section.getMethod("getRenderingEnabled");
        final Class<?> sectionPositions = type("core.pos.DhSectionPos");
        final Method width = sectionPositions.getMethod("getBlockWidth", long.class);
        final Method minX = sectionPositions.getMethod("getMinCornerBlockX", long.class);
        final Method minZ = sectionPositions.getMethod("getMinCornerBlockZ", long.class);
        final Class<?> containers = container.getType();
        final Field opaque = containers.getField("vboOpaqueWrappers");
        final Field uploaded = containers.getField("buffersUploaded");
        final Field corner = containers.getField("minCornerBlockPos");
        final Method x = corner.getType().getMethod("getX");
        final Method y = corner.getType().getMethod("getY");
        final Method z = corner.getType().getMethod("getZ");
        final Class<?> levelWrapper = type("core.wrapperInterfaces.world.ILevelWrapper");
        final Method minHeight = levelWrapper.getMethod("getMinHeight");
        final Method maxHeight = levelWrapper.getMethod("getMaxHeight");
        final Field lock = vertexType.getField("renderStampLock");
        final Method id = type("common.render.openGl.glObject.buffer.GLBuffer").getMethod("getId");
        final Method count = vertexType.getMethod("getVertexCount");
        final Method ibo = vertexType.getMethod("getQuadIBO");
        final Method indexType = ibo.getReturnType().getMethod("getGlType");

        Access() throws ReflectiveOperationException { }

        private static Class<?> type(String suffix) throws ClassNotFoundException {
            return Class.forName("com.seibel.distanthorizons." + suffix);
        }
    }
}
