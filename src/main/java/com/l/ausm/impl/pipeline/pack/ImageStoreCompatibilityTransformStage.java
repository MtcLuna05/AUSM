package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageStoreCompatibilityTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)(?:\\s+\\w+)?\\s*$");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!usesImageStore(source)) {
            return source;
        }

        String transformed = bumpVersion(source);
        transformed = transformed.replace(
                "writeonly uniform uimage3D voxelimg;",
                "layout(r8ui) writeonly uniform uimage3D voxelimg;"
        );
        transformed = transformed.replace(
                "writeonly uniform uimage2D puddle_img;",
                "layout(r8ui) writeonly uniform uimage2D puddle_img;"
        );
        transformed = transformed.replace(
                "writeonly uniform image3D lightimg0;",
                "layout(rgba16f) writeonly uniform image3D lightimg0;"
        );
        transformed = transformed.replace(
                "writeonly uniform image3D lightimg1;",
                "layout(rgba16f) writeonly uniform image3D lightimg1;"
        );
        MainMod.LOGGER.debug("[ShaderTransform] Applied image-store compatibility transform");
        return transformed;
    }

    private static boolean usesImageStore(String source) {
        return source.contains("imageStore(")
                || source.contains("uimage2D")
                || source.contains("uimage3D")
                || source.contains("image2D")
                || source.contains("image3D");
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
