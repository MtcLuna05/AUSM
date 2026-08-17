package com.l.ausm.impl.pipeline.render;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Shader-facing lightmap adapter.
 *
 * <p>Minecraft 1.12 keeps its fixed-function lightmap on texture unit 1. Iris
 * exposes a shader lightmap on unit 2, backed by modern Minecraft's lightmap
 * contents. The 1.12 lightmap is consistently darker in shaderpacks that sample
 * the sky column directly. Keep the copy available for shader sampling, but do
 * not brighten it here; shaderpacks already apply their own exposure curves.</p>
 */
public final class IrisLightmapTexture {
    private static final int SIZE = 16;
    private static final float CURVE_EXPONENT = 1.0F;
    private static final float CURVE_BLEND = 0.0F;

    private final DynamicTexture texture = new DynamicTexture(SIZE, SIZE);
    private final int[] pixels = MinecraftReflectionCompat.dynamicTextureData(texture);

    public IrisLightmapTexture() {
        // DynamicTexture does not expose whether its CPU buffer has ever been
        // uploaded. Seed the mirror with an impossible lightmap value so the
        // first bind always publishes real contents.
        Arrays.fill(pixels, 0x01000000);
    }

    public int updateFrom(DynamicTexture vanillaLightmap) {
        if (vanillaLightmap == null) {
            return -1;
        }

        int[] source = MinecraftReflectionCompat.dynamicTextureData(vanillaLightmap);
        int count = Math.min(source.length, pixels.length);
        boolean changed = source.length != pixels.length;
        for (int i = 0; i < count; i++) {
            int adapted = adaptPixel(source[i]);
            if (pixels[i] != adapted) {
                pixels[i] = adapted;
                changed = true;
            }
        }
        if (changed) {
            MinecraftReflectionCompat.invoke(texture, new String[]{"func_110564_a", "updateDynamicTexture"}, MinecraftReflectionCompat.NO_PARAMETERS);
        }
        return MinecraftReflectionCompat.glTextureId(texture);
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
        return Math.clamp(Math.round(adapted * 255.0F), 0, 255);
    }
}
