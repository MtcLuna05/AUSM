package com.l.ausm.impl.pipeline.compat;

import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BloomMaskColor {
    private static final ConcurrentMap<String, Integer> SPRITE_COLORS = new ConcurrentHashMap<>();
    private static final int DEFAULT_EMISSIVE_COLOR = 0xFFE0A8;

    private BloomMaskColor() {
    }

    public static int colorForState(IBlockState state) {
        int dyeColor = dyeColorForState(state);
        if (dyeColor != -1) {
            return dyeColor;
        }

        int textureColor = textureColorForState(state);
        if (textureColor != -1) {
            return textureColor;
        }

        return packColor(DEFAULT_EMISSIVE_COLOR);
    }

    private static int dyeColorForState(IBlockState state) {
        if (state == null) {
            return -1;
        }
        String color = statePropertyValue(state, "color");
        if (color == null) {
            color = statePropertyValue(state, "colour");
        }
        return dyeMaskColor(color);
    }

    public static int textureColorForState(IBlockState state) {
        if (state == null) {
            return -1;
        }
        try {
            Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
            BlockRendererDispatcher dispatcher = com.l.ausm.impl.util.MinecraftReflectionCompat.blockRendererDispatcher(mc);
            if (dispatcher == null) {
                return -1;
            }
            IBakedModel model = com.l.ausm.impl.util.MinecraftReflectionCompat.call((dispatcher), net.minecraft.client.renderer.block.model.IBakedModel.class, null, new String[] {"func_184389_a", "getModelForState"},
                new Class<?>[] {net.minecraft.block.state.IBlockState.class}, (state));
            if (model == null) {
                return -1;
            }

            int particleColor = spriteAverageMaskColor(com.l.ausm.impl.util.MinecraftReflectionCompat.call((model), net.minecraft.client.renderer.texture.TextureAtlasSprite.class, null, new String[] {"func_177554_e", "getParticleTexture"}, com.l.ausm.impl.util.MinecraftReflectionCompat.NO_PARAMETERS));
            if (particleColor != -1) {
                return particleColor;
            }

            int quadColor = averageQuadSpriteColor(com.l.ausm.impl.util.MinecraftReflectionCompat.bakedModelQuads(model, state, null, 0L));
            if (quadColor != -1) {
                return quadColor;
            }
            for (EnumFacing facing : EnumFacing.values()) {
                quadColor = averageQuadSpriteColor(com.l.ausm.impl.util.MinecraftReflectionCompat.bakedModelQuads(model, state, facing, 0L));
                if (quadColor != -1) {
                    return quadColor;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return -1;
    }

    private static int averageQuadSpriteColor(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return -1;
        }
        long red = 0L;
        long green = 0L;
        long blue = 0L;
        int count = 0;
        for (BakedQuad quad : quads) {
            int color = quad != null ? spriteAverageMaskColor(com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadSprite(quad)) : -1;
            if (color == -1) {
                continue;
            }
            int rgb = unpackPackedRgb(color);
            red += (rgb >> 16) & 0xFF;
            green += (rgb >> 8) & 0xFF;
            blue += rgb & 0xFF;
            count++;
        }
        if (count <= 0) {
            return -1;
        }
        return packColor(((int) (red / count) << 16) | ((int) (green / count) << 8) | (int) (blue / count));
    }

    private static int spriteAverageMaskColor(TextureAtlasSprite sprite) {
        if (sprite == null || com.l.ausm.impl.util.MinecraftReflectionCompat.spriteIconName(sprite) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.spriteIconName(sprite).contains("missingno")) {
            return -1;
        }
        return SPRITE_COLORS.computeIfAbsent(com.l.ausm.impl.util.MinecraftReflectionCompat.spriteIconName(sprite), ignored -> averageSpriteMaskColor(sprite));
    }

    public static int averageSpriteMaskColor(TextureAtlasSprite sprite) {
        try {
            int[][] frames = com.l.ausm.impl.util.MinecraftReflectionCompat.call((sprite), int[][].class, null, new String[] {"func_147965_a", "getFrameTextureData"},
                new Class<?>[] {int.class}, (0));
            if (frames == null || frames.length == 0 || frames[0] == null) {
                return -1;
            }
            long red = 0L;
            long green = 0L;
            long blue = 0L;
            long weight = 0L;
            for (int pixel : frames[0]) {
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha <= 16) {
                    continue;
                }
                red += ((pixel >>> 16) & 0xFF) * (long) alpha;
                green += ((pixel >>> 8) & 0xFF) * (long) alpha;
                blue += (pixel & 0xFF) * (long) alpha;
                weight += alpha;
            }
            if (weight <= 0L) {
                return -1;
            }
            return packColor(((int) (red / weight) << 16) | ((int) (green / weight) << 8) | (int) (blue / weight));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String statePropertyValue(IBlockState state, String propertyName) {
        try {
            for (IProperty<?> property : com.l.ausm.impl.util.MinecraftReflectionCompat.stateProperties(state).keySet()) {
                if (property != null && propertyName.equalsIgnoreCase(com.l.ausm.impl.util.MinecraftReflectionCompat.propertyName(property))) {
                    Object value = com.l.ausm.impl.util.MinecraftReflectionCompat.statePropertyValue(state, property);
                    return value != null ? value.toString().toLowerCase(java.util.Locale.ROOT) : null;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static int dyeMaskColor(String color) {
        String normalized = color == null ? "" : color.toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "red" -> packColor(0xD81818);
            case "orange", "brown" -> packColor(0xD06818);
            case "yellow" -> packColor(0xD0B018);
            case "lime" -> packColor(0x78C818);
            case "green" -> packColor(0x18C840);
            case "cyan" -> packColor(0x18C8C8);
            case "light_blue", "lightblue" -> packColor(0x4098D8);
            case "blue" -> packColor(0x3048D8);
            case "purple" -> packColor(0x8830D0);
            case "magenta" -> packColor(0xD030C8);
            case "pink" -> packColor(0xD85088);
            default -> -1;
        };
    }

    private static int unpackPackedRgb(int packedColor) {
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            int red = packedColor & 0xFF;
            int green = (packedColor >>> 8) & 0xFF;
            int blue = (packedColor >>> 16) & 0xFF;
            return (red << 16) | (green << 8) | blue;
        }
        int red = (packedColor >>> 24) & 0xFF;
        int green = (packedColor >>> 16) & 0xFF;
        int blue = (packedColor >>> 8) & 0xFF;
        return (red << 16) | (green << 8) | blue;
    }

    public static int packColor(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? (0xFF << 24) | (blue << 16) | (green << 8) | red
                : (red << 24) | (green << 16) | (blue << 8) | 0xFF;
    }
}
