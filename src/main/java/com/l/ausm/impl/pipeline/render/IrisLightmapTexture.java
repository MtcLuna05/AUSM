package com.l.ausm.impl.pipeline.render;

import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Shader-facing lightmap adapter.
 *
 * <p>Minecraft 1.12 keeps its fixed-function lightmap on texture unit 1. Iris
 * exposes a shader lightmap on unit 2, backed by modern Minecraft's lightmap
 * contents. The 1.12 lightmap is consistently darker in shaderpacks that sample
 * the sky column directly, so AUSM keeps vanilla's texture untouched and uploads
 * a compatibility copy for shader sampling.</p>
 */
public final class IrisLightmapTexture {
    private static final int SIZE = 16;
    private static final float CURVE_EXPONENT = 0.65F;
    private static final float CURVE_BLEND = 0.70F;

    private final DynamicTexture texture = new DynamicTexture(SIZE, SIZE);
    private final int[] pixels = texture.getTextureData();

    public int updateFrom(DynamicTexture vanillaLightmap) {
        if (vanillaLightmap == null) {
            return -1;
        }

        int[] source = vanillaLightmap.getTextureData();
        int count = Math.min(source.length, pixels.length);
        for (int i = 0; i < count; i++) {
            pixels[i] = adaptPixel(source[i]);
        }
        texture.updateDynamicTexture();
        return texture.getGlTextureId();
    }

    private static int adaptPixel(int argb) {
        int alpha = argb & 0xFF000000;
        int red = adaptChannel((argb >>> 16) & 0xFF);
        int green = adaptChannel((argb >>> 8) & 0xFF);
        int blue = adaptChannel(argb & 0xFF);
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int adaptChannel(int channel) {
        float value = channel / 255.0F;
        float curved = (float) Math.pow(value, CURVE_EXPONENT);
        float adapted = value * (1.0F - CURVE_BLEND) + curved * CURVE_BLEND;
        return Math.max(0, Math.min(255, Math.round(adapted * 255.0F)));
    }
}
