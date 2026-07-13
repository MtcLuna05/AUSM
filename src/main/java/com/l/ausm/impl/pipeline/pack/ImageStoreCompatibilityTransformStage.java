package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageStoreCompatibilityTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)(?:\\s+\\w+)?\\s*$");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!usesStorageObjects(source)) {
            return source;
        }

        String transformed = bumpVersion(source);
        transformed = ensureWriteonlyImageLayout(transformed, "r8ui", "uimage3D", "voxelimg");
        transformed = ensureWriteonlyImageLayout(transformed, "r16ui", "uimage3D", "voxel_img");
        transformed = ensureWriteonlyImageLayout(transformed, "r8ui", "uimage2D", "puddle_img");
        transformed = ensureWriteonlyImageLayout(transformed, "r16ui", "uimage3D", "wsr_img");
        transformed = ensureWriteonlyImageLayout(transformed, "r8ui", "uimage3D", "wsr_lod_img");
        transformed = ensureWriteonlyImageLayout(transformed, "rgba16f", "image3D", "lightimg0");
        transformed = ensureWriteonlyImageLayout(transformed, "rgba16f", "image3D", "lightimg1");
        transformed = ensureWriteonlyImageLayout(transformed, "rgba16f", "image3D", "floodfill_img");
        transformed = ensureWriteonlyImageLayout(transformed, "rgba16f", "image3D", "floodfill_img_copy");
        transformed = ensureUniformWriteonlyImageLayout(transformed, "rgba8", "image2D", "playerAtlas_img");
        MainMod.LOGGER.debug("[ShaderTransform] Applied storage-object compatibility transform");
        return transformed;
    }

    private static String ensureWriteonlyImageLayout(String source, String format, String type, String name) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*)(?:(?:layout\\s*\\([^)]*\\)\\s+)*)writeonly\\s+uniform\\s+"
                + Pattern.quote(type) + "\\s+" + Pattern.quote(name) + "\\s*;");
        Matcher matcher = pattern.matcher(source);
        return matcher.replaceAll(result -> result.group(1) + "layout(" + format + ") writeonly uniform " + type + " " + name + ";");
    }

    private static String ensureUniformWriteonlyImageLayout(String source, String format, String type, String name) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*)(?:(?:layout\\s*\\([^)]*\\)\\s+)*)uniform\\s+writeonly\\s+"
                + Pattern.quote(type) + "\\s+" + Pattern.quote(name) + "\\s*;");
        Matcher matcher = pattern.matcher(source);
        return matcher.replaceAll(result -> result.group(1) + "layout(" + format + ") uniform writeonly " + type + " " + name + ";");
    }

    private static boolean usesStorageObjects(String source) {
        return source.contains("imageStore(")
                || source.contains("uimage2D")
                || source.contains("uimage3D")
                || source.contains("image2D")
                || source.contains("image3D")
                || source.contains("GL_ARB_shader_storage_buffer_object")
                || source.contains("layout(std430");
    }

    private static String bumpVersion(String source) {
        Matcher matcher = VERSION.matcher(source);
        if (!matcher.find()) {
            return "#version 430 compatibility\n" + source;
        }

        int version;
        try {
            version = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            version = 120;
        }

        if (version >= 420) {
            return source;
        }

        return matcher.replaceFirst("#version 430 compatibility");
    }
}
