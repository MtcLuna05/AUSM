package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.impl.MainMod;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImageStoreCompatibilityTransformStage implements ShaderTransformStage {
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)(?:\\s+\\w+)?\\s*$");
    private static final Pattern IMAGE_DECLARATION = Pattern.compile(
            "(?m)^(\\s*)(?:(?:layout\\s*\\([^)]*\\)\\s+)*)"
                    + "(writeonly\\s+uniform|uniform\\s+writeonly)\\s+"
                    + "(u?image(?:2D|3D))\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;"
    );
    private static final Map<String, ImageLayout> REQUIRED_IMAGE_LAYOUTS = Map.ofEntries(
            Map.entry("voxelimg", new ImageLayout("r8ui", "uimage3D")),
            Map.entry("voxel_img", new ImageLayout("r16ui", "uimage3D")),
            Map.entry("puddle_img", new ImageLayout("r8ui", "uimage2D")),
            Map.entry("wsr_img", new ImageLayout("r16ui", "uimage3D")),
            Map.entry("wsr_lod_img", new ImageLayout("r8ui", "uimage3D")),
            Map.entry("lightimg0", new ImageLayout("rgba16f", "image3D")),
            Map.entry("lightimg1", new ImageLayout("rgba16f", "image3D")),
            Map.entry("floodfill_img", new ImageLayout("rgba16f", "image3D")),
            Map.entry("floodfill_img_copy", new ImageLayout("rgba16f", "image3D")),
            Map.entry("playerAtlas_img", new ImageLayout("rgba8", "image2D"))
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (!usesStorageObjects(source)) {
            return source;
        }

        String transformed = ensureImageLayouts(bumpVersion(source));
        MainMod.LOGGER.debug("[ShaderTransform] Applied storage-object compatibility transform");
        return transformed;
    }

    private static String ensureImageLayouts(String source) {
        Matcher matcher = IMAGE_DECLARATION.matcher(source);
        StringBuffer transformed = new StringBuffer(source.length());
        boolean changed = false;
        while (matcher.find()) {
            ImageLayout layout = REQUIRED_IMAGE_LAYOUTS.get(matcher.group(4));
            if (layout == null || !layout.type().equals(matcher.group(3))) {
                continue;
            }
            String replacement = matcher.group(1) + "layout(" + layout.format() + ") "
                    + matcher.group(2) + " " + layout.type() + " " + matcher.group(4) + ";";
            matcher.appendReplacement(transformed, Matcher.quoteReplacement(replacement));
            changed = true;
        }
        if (!changed) {
            return source;
        }
        matcher.appendTail(transformed);
        return transformed.toString();
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

    private record ImageLayout(String format, String type) {
    }
}
