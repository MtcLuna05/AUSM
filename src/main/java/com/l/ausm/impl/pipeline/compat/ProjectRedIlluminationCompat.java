package com.l.ausm.impl.pipeline.compat;

import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ProjectRedIlluminationCompat {
    private static final String TILE_LAMP = "mrtjp.projectred.illumination.TileLamp";
    private static final String TILE_MULTIPART = "codechicken.multipart.TileMultipart";
    private static final String I_LIGHT = "mrtjp.projectred.illumination.ILight";

    private static volatile boolean initialized;
    private static boolean available;
    private static Class<?> tileLampClass;
    private static Class<?> tileMultipartClass;
    private static Class<?> iLightClass;
    private static Method tileMultipartJPartList;
    private static Method tileMultipartPartList;
    private static Method iLightGetColor;
    private static Method iLightIsOn;
    private static final Map<Class<?>, Optional<Method>> LIGHT_VALUE_METHODS = new ConcurrentHashMap<>();

    private ProjectRedIlluminationCompat() {
    }

    public static int collectVoxelIds(TileEntity tileEntity, int[] output) {
        if (tileEntity == null || output == null || output.length == 0) {
            return 0;
        }

        ensureInitialized();
        if (!available) {
            return 0;
        }

        try {
            if (tileLampClass != null && tileLampClass.isInstance(tileEntity)) {
                return collectLight(tileEntity, output, 0);
            }

            if (tileMultipartClass != null && tileMultipartClass.isInstance(tileEntity)) {
                Object parts = null;
                if (tileMultipartJPartList != null) {
                    parts = tileMultipartJPartList.invoke(tileEntity);
                } else if (tileMultipartPartList != null) {
                    parts = tileMultipartPartList.invoke(tileEntity);
                }
                return collectParts(parts, output);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }

        return 0;
    }

    public static String diagnose(TileEntity tileEntity) {
        return diagnose(tileEntity, false);
    }

    public static String diagnoseHost(TileEntity tileEntity) {
        return diagnose(tileEntity, true);
    }

    private static String diagnose(TileEntity tileEntity, boolean includeNonMatching) {
        if (tileEntity == null) {
            return null;
        }

        ensureInitialized();
        if (!available) {
            return null;
        }

        try {
            StringBuilder builder = new StringBuilder();
            builder.append("tile=").append(tileEntity.getClass().getName());
            builder.append(" available=").append(available);
            builder.append(" tileLamp=").append(tileLampClass != null && tileLampClass.isInstance(tileEntity));
            builder.append(" tileMultipart=").append(tileMultipartClass != null && tileMultipartClass.isInstance(tileEntity));

            if (tileLampClass != null && tileLampClass.isInstance(tileEntity)) {
                appendLightDiagnosis(builder, tileEntity, "lamp");
                return builder.toString();
            }

            if (tileMultipartClass != null && tileMultipartClass.isInstance(tileEntity)) {
                Object parts = null;
                String partsMethod = "none";
                if (tileMultipartJPartList != null) {
                    parts = tileMultipartJPartList.invoke(tileEntity);
                    partsMethod = "jPartList";
                } else if (tileMultipartPartList != null) {
                    parts = tileMultipartPartList.invoke(tileEntity);
                    partsMethod = "partList";
                }
                builder.append(" partsMethod=").append(partsMethod);
                appendPartsDiagnosis(builder, parts);
                String diagnosis = builder.toString();
                return includeNonMatching || isRelevantMultipartDiagnosis(diagnosis) ? diagnosis : null;
            }

            return includeNonMatching ? builder.append(" recognized=false").toString() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "tile=" + tileEntity.getClass().getName() + " error=" + e.getClass().getSimpleName() + ":" + e.getMessage();
        }
    }

    private static boolean isRelevantMultipartDiagnosis(String diagnosis) {
        return diagnosis != null
                && (diagnosis.contains(":isLight=true")
                || diagnosis.contains("mrtjp.projectred.illumination")
                || diagnosis.contains("projectred"));
    }

    private static int collectParts(Object parts, int[] output) throws ReflectiveOperationException {
        if (parts == null) {
            return 0;
        }

        int count = 0;
        if (parts instanceof Iterable<?> iterable) {
            for (Object part : iterable) {
                count = collectPart(part, output, count);
                if (count >= output.length) {
                    break;
                }
            }
            return count;
        }

        Class<?> partsClass = parts.getClass();
        if (partsClass.isArray()) {
            int length = Array.getLength(parts);
            for (int i = 0; i < length && count < output.length; i++) {
                count = collectPart(Array.get(parts, i), output, count);
            }
            return count;
        }

        Method iteratorMethod = partsClass.getMethod("iterator");
        Object iterator = iteratorMethod.invoke(parts);
        if (iterator == null) {
            return 0;
        }
        Method hasNext = iterator.getClass().getMethod("hasNext");
        Method next = iterator.getClass().getMethod("next");
        while (asBoolean(hasNext.invoke(iterator)) && count < output.length) {
            count = collectPart(next.invoke(iterator), output, count);
        }
        return count;
    }

    private static void appendPartsDiagnosis(StringBuilder builder, Object parts) throws ReflectiveOperationException {
        if (parts == null) {
            builder.append(" parts=null");
            return;
        }

        builder.append(" partsClass=").append(parts.getClass().getName());
        int[] index = new int[]{0};
        int[] accepted = new int[]{0};
        if (parts instanceof Iterable<?> iterable) {
            for (Object part : iterable) {
                if (index[0] >= 12) {
                    builder.append(" partsTruncated=true");
                    break;
                }
                if (appendPartDiagnosis(builder, part, index[0])) {
                    accepted[0]++;
                }
                index[0]++;
            }
            builder.append(" partCount=").append(index[0]).append(" accepted=").append(accepted[0]);
            return;
        }

        Class<?> partsClass = parts.getClass();
        if (partsClass.isArray()) {
            int length = Array.getLength(parts);
            for (int i = 0; i < length && i < 12; i++) {
                if (appendPartDiagnosis(builder, Array.get(parts, i), i)) {
                    accepted[0]++;
                }
            }
            if (length > 12) {
                builder.append(" partsTruncated=true");
            }
            builder.append(" partCount=").append(length).append(" accepted=").append(accepted[0]);
            return;
        }

        Method iteratorMethod = partsClass.getMethod("iterator");
        Object iterator = iteratorMethod.invoke(parts);
        if (iterator == null) {
            builder.append(" iterator=null");
            return;
        }
        Method hasNext = iterator.getClass().getMethod("hasNext");
        Method next = iterator.getClass().getMethod("next");
        while (asBoolean(hasNext.invoke(iterator)) && index[0] < 12) {
            if (appendPartDiagnosis(builder, next.invoke(iterator), index[0])) {
                accepted[0]++;
            }
            index[0]++;
        }
        if (asBoolean(hasNext.invoke(iterator))) {
            builder.append(" partsTruncated=true");
        }
        builder.append(" partCountAtLeast=").append(index[0]).append(" accepted=").append(accepted[0]);
    }

    private static boolean appendPartDiagnosis(StringBuilder builder, Object part, int index) throws ReflectiveOperationException {
        builder.append(" part").append(index).append("=");
        if (part == null) {
            builder.append("null");
            return false;
        }
        builder.append(part.getClass().getName());
        if (!iLightClass.isInstance(part)) {
            builder.append(":notILight");
            return false;
        }
        return appendLightDiagnosis(builder, part, "part" + index);
    }

    private static boolean appendLightDiagnosis(StringBuilder builder, Object light, String label) throws ReflectiveOperationException {
        boolean on = asBoolean(iLightIsOn.invoke(light));
        int lightValue = readLightValue(light);
        int color = asInt(iLightGetColor.invoke(light));
        int voxelId = dyeColorToVoxelId(color);
        boolean accepted = on && lightValue > 0 && voxelId > 0;
        builder.append(" ").append(label)
                .append(":isLight=true")
                .append(",on=").append(on)
                .append(",light=").append(lightValue)
                .append(",color=").append(color)
                .append(",voxel=").append(voxelId)
                .append(",accepted=").append(accepted);
        return accepted;
    }

    private static int collectPart(Object part, int[] output, int count) throws ReflectiveOperationException {
        if (part == null || !iLightClass.isInstance(part)) {
            return count;
        }

        return collectLight(part, output, count);
    }

    private static int collectLight(Object light, int[] output, int count) throws ReflectiveOperationException {
        if (count >= output.length || !iLightClass.isInstance(light) || !asBoolean(iLightIsOn.invoke(light))) {
            return count;
        }

        int lightValue = readLightValue(light);
        if (lightValue <= 0) {
            return count;
        }

        int voxelId = dyeColorToVoxelId(asInt(iLightGetColor.invoke(light)));
        if (voxelId <= 0) {
            return count;
        }

        output[count] = voxelId;
        return count + 1;
    }

    private static int readLightValue(Object light) throws ReflectiveOperationException {
        Optional<Method> method = LIGHT_VALUE_METHODS.computeIfAbsent(light.getClass(), ProjectRedIlluminationCompat::findLightValueMethod);
        if (method.isPresent()) {
            return asInt(method.get().invoke(light));
        }
        return asBoolean(iLightIsOn.invoke(light)) ? 15 : 0;
    }

    private static Optional<Method> findLightValueMethod(Class<?> lightClass) {
        try {
            return Optional.of(lightClass.getMethod("getLightValue"));
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return Optional.empty();
        }
    }

    private static int dyeColorToVoxelId(int color) {
        return switch (color & 15) {
            case 1, 12 -> 71;
            case 2 -> 79;
            case 3 -> 76;
            case 4 -> 72;
            case 5 -> 73;
            case 6 -> 80;
            case 9 -> 75;
            case 10 -> 78;
            case 11 -> 77;
            case 13 -> 74;
            case 14 -> 70;
            default -> 3;
        };
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        synchronized (ProjectRedIlluminationCompat.class) {
            if (initialized) {
                return;
            }

            try {
                ClassLoader loader = ProjectRedIlluminationCompat.class.getClassLoader();
                iLightClass = Class.forName(I_LIGHT, false, loader);
                tileLampClass = loadClass(loader, TILE_LAMP);
                tileMultipartClass = loadClass(loader, TILE_MULTIPART);

                if (tileMultipartClass != null) {
                    tileMultipartJPartList = findMethod(tileMultipartClass, "jPartList");
                    tileMultipartPartList = findMethod(tileMultipartClass, "partList");
                }
                iLightGetColor = iLightClass.getMethod("getColor");
                iLightIsOn = iLightClass.getMethod("isOn");
                available = tileLampClass != null || tileMultipartClass != null;
            } catch (ReflectiveOperationException | LinkageError ignored) {
                available = false;
            }

            initialized = true;
        }
    }

    private static Class<?> loadClass(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }
}
