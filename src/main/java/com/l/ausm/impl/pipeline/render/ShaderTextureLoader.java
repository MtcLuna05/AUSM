package com.l.ausm.impl.pipeline.render;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.pack.ShaderPack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ShaderTextureLoader {
    private ShaderTextureLoader() {
    }

    public static int loadTexture(ShaderPack pack, String resourcePath) throws IOException {
        return loadTexture(pack, resourcePath, false, false);
    }

    public static int loadTexture(ShaderPack pack, String resourcePath, boolean blur, boolean clamp) throws IOException {
        byte[] data;
        try (var stream = pack.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Missing shader texture: " + resourcePath);
            }
            data = stream.readAllBytes();
        }

        BufferedImage image = decodeImage(data);
        MainMod.LOGGER.debug("[ShaderTextures] Decoded {} as {}x{}", resourcePath, image.getWidth(), image.getHeight());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        applyFiltering(GL11.GL_TEXTURE_2D, blur, clamp);

        ByteBuffer pixels = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xFF));
                pixels.put((byte) ((argb >> 8) & 0xFF));
                pixels.put((byte) (argb & 0xFF));
                pixels.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        pixels.flip();

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                image.getWidth(),
                image.getHeight(),
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                pixels
        );
        logGlError("glTexImage2D", resourcePath);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        TextureBinder.restoreDefaultTextureUnit();
        MainMod.LOGGER.debug("[ShaderTextures] Loaded {} as GL texture {}", resourcePath, textureId);
        return textureId;
    }

    public static RawTexture loadRawTexture(ShaderPack pack, ShaderRawTextureDirective directive) throws IOException {
        byte[] data;
        try (var stream = pack.getResourceAsStream(directive.resourcePath())) {
            if (stream == null) {
                throw new IOException("Missing raw shader texture: " + directive.resourcePath());
            }
            data = stream.readAllBytes();
        }

        int textureTarget = textureTarget(directive.target());
        int internalFormat = internalFormat(directive.internalFormat());
        int pixelFormat = pixelFormat(directive.pixelFormat());
        int pixelType = pixelType(directive.pixelType());
        if (textureTarget == 0 || internalFormat == 0 || pixelFormat == 0 || pixelType == 0) {
            throw new IOException("Unsupported raw shader texture format: " + directive);
        }

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(textureBinding(textureTarget));

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(textureTarget, textureId);
        boolean integerFormat = isIntegerPixelFormat(pixelFormat);
        applyFiltering(textureTarget, !integerFormat && directive.blur(), directive.clamp());

        ByteBuffer pixels = BufferUtils.createByteBuffer(data.length);
        pixels.put(data);
        pixels.flip();
        int previousAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        switch (directive.target()) {
            case TEXTURE_1D -> GL11.glTexImage1D(
                    textureTarget,
                    0,
                    internalFormat,
                    directive.width(),
                    0,
                    pixelFormat,
                    pixelType,
                    pixels
            );
            case TEXTURE_2D -> GL11.glTexImage2D(
                    textureTarget,
                    0,
                    internalFormat,
                    directive.width(),
                    directive.height(),
                    0,
                    pixelFormat,
                    pixelType,
                    pixels
            );
            case TEXTURE_3D -> GL12.glTexImage3D(
                    textureTarget,
                    0,
                    internalFormat,
                    directive.width(),
                    directive.height(),
                    directive.depth(),
                    0,
                    pixelFormat,
                    pixelType,
                    pixels
            );
        }
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, previousAlignment);
        logGlError("raw texture upload", directive.resourcePath());
        GL11.glBindTexture(textureTarget, previousTexture);
        TextureBinder.restoreDefaultTextureUnit();
        MainMod.LOGGER.debug(
                "[ShaderTextures] Loaded raw {} texture {} as GL texture {} for sampler '{}'",
                directive.target(),
                directive.resourcePath(),
                textureId,
                directive.samplerName()
        );
        return new RawTexture(textureId, textureTarget);
    }

    private static void applyFiltering(int textureTarget, boolean blur, boolean clamp) {
        int filter = blur ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        int wrap = clamp ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
        GL11.glTexParameteri(textureTarget, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(textureTarget, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(textureTarget, GL11.GL_TEXTURE_WRAP_S, wrap);
        if (textureTarget != GL11.GL_TEXTURE_1D) {
            GL11.glTexParameteri(textureTarget, GL11.GL_TEXTURE_WRAP_T, wrap);
        }
        if (textureTarget == GL12.GL_TEXTURE_3D) {
            GL11.glTexParameteri(textureTarget, GL12.GL_TEXTURE_WRAP_R, wrap);
        }
        GL11.glTexParameteri(textureTarget, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(textureTarget, GL12.GL_TEXTURE_MIN_LOD, 0);
        GL11.glTexParameteri(textureTarget, GL12.GL_TEXTURE_MAX_LOD, 0);
        GL11.glTexParameterf(textureTarget, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(textureTarget);
    }

    public static int createNoiseTexture(int requestedResolution) {
        int resolution = Math.max(1, Math.min(4096, requestedResolution));
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);

        ByteBuffer pixels = BufferUtils.createByteBuffer(resolution * resolution * 4);
        Random random = new Random(0x1A15115EEDL);
        for (int i = 0; i < resolution * resolution; i++) {
            pixels.put((byte) random.nextInt(256));
            pixels.put((byte) random.nextInt(256));
            pixels.put((byte) random.nextInt(256));
            pixels.put((byte) 255);
        }
        pixels.flip();

        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                resolution,
                resolution,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                pixels
        );
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        TextureBinder.restoreDefaultTextureUnit();
        MainMod.LOGGER.debug("[ShaderTextures] Created generated noisetex {}x{} as GL texture {}", resolution, resolution, textureId);
        return textureId;
    }

    private static BufferedImage decodeImage(byte[] data) throws IOException {
        if (startsWithPpmHeader(data)) {
            return decodeTextPpm(data);
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Unsupported shader texture image format");
        }
        return image;
    }

    private static boolean startsWithPpmHeader(byte[] data) {
        return data.length >= 2 && data[0] == 'P' && data[1] == '3';
    }

    private static BufferedImage decodeTextPpm(byte[] data) throws IOException {
        String text = new String(data, StandardCharsets.US_ASCII);
        List<String> tokens = new ArrayList<>();
        for (String line : text.split("\\R")) {
            int comment = line.indexOf('#');
            String withoutComment = comment >= 0 ? line.substring(0, comment) : line;
            for (String token : withoutComment.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    tokens.add(token);
                }
            }
        }

        if (tokens.size() < 4 || !"P3".equals(tokens.get(0))) {
            throw new IOException("Invalid P3 PPM texture");
        }

        int width = Integer.parseInt(tokens.get(1));
        int height = Integer.parseInt(tokens.get(2));
        int maxValue = Integer.parseInt(tokens.get(3));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int index = 4;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (index + 2 >= tokens.size()) {
                    throw new IOException("PPM texture ended before all pixels were read");
                }
                int r = scale(Integer.parseInt(tokens.get(index++)), maxValue);
                int g = scale(Integer.parseInt(tokens.get(index++)), maxValue);
                int b = scale(Integer.parseInt(tokens.get(index++)), maxValue);
                image.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    private static int scale(int value, int maxValue) {
        if (maxValue <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(255, value * 255 / maxValue));
    }

    public static int textureTarget(ShaderImageTarget target) {
        return switch (target) {
            case TEXTURE_1D -> GL11.GL_TEXTURE_1D;
            case TEXTURE_2D -> GL11.GL_TEXTURE_2D;
            case TEXTURE_3D -> GL12.GL_TEXTURE_3D;
        };
    }

    private static int textureBinding(int textureTarget) {
        if (textureTarget == GL11.GL_TEXTURE_1D) {
            return GL11.GL_TEXTURE_BINDING_1D;
        }
        if (textureTarget == GL12.GL_TEXTURE_3D) {
            return GL12.GL_TEXTURE_BINDING_3D;
        }
        return GL11.GL_TEXTURE_BINDING_2D;
    }

    public static int internalFormat(String value) {
        return switch (normalize(value)) {
            case "RGBA" -> GL11.GL_RGBA8;
            case "R8" -> GL30.GL_R8;
            case "RG8" -> GL30.GL_RG8;
            case "RGB8" -> GL11.GL_RGB8;
            case "RGBA8" -> GL11.GL_RGBA8;
            case "R8_SNORM" -> GL31.GL_R8_SNORM;
            case "RG8_SNORM" -> GL31.GL_RG8_SNORM;
            case "RGB8_SNORM" -> GL31.GL_RGB8_SNORM;
            case "RGBA8_SNORM" -> GL31.GL_RGBA8_SNORM;
            case "R16" -> GL30.GL_R16;
            case "RG16" -> GL30.GL_RG16;
            case "RGB16" -> GL11.GL_RGB16;
            case "RGBA16" -> GL11.GL_RGBA16;
            case "R16F" -> GL30.GL_R16F;
            case "RG16F" -> GL30.GL_RG16F;
            case "RGB16F" -> GL30.GL_RGB16F;
            case "RGBA16F" -> GL30.GL_RGBA16F;
            case "R32F" -> GL30.GL_R32F;
            case "RG32F" -> GL30.GL_RG32F;
            case "RGB32F" -> GL30.GL_RGB32F;
            case "RGBA32F" -> GL30.GL_RGBA32F;
            case "R8I" -> GL30.GL_R8I;
            case "RG8I" -> GL30.GL_RG8I;
            case "RGB8I" -> GL30.GL_RGB8I;
            case "RGBA8I" -> GL30.GL_RGBA8I;
            case "R8UI" -> GL30.GL_R8UI;
            case "RG8UI" -> GL30.GL_RG8UI;
            case "RGB8UI" -> GL30.GL_RGB8UI;
            case "RGBA8UI" -> GL30.GL_RGBA8UI;
            case "R16I" -> GL30.GL_R16I;
            case "RG16I" -> GL30.GL_RG16I;
            case "RGB16I" -> GL30.GL_RGB16I;
            case "RGBA16I" -> GL30.GL_RGBA16I;
            case "R16UI" -> GL30.GL_R16UI;
            case "RG16UI" -> GL30.GL_RG16UI;
            case "RGB16UI" -> GL30.GL_RGB16UI;
            case "RGBA16UI" -> GL30.GL_RGBA16UI;
            case "R32I" -> GL30.GL_R32I;
            case "RG32I" -> GL30.GL_RG32I;
            case "RGB32I" -> GL30.GL_RGB32I;
            case "RGBA32I" -> GL30.GL_RGBA32I;
            case "R32UI" -> GL30.GL_R32UI;
            case "RG32UI" -> GL30.GL_RG32UI;
            case "RGB32UI" -> GL30.GL_RGB32UI;
            case "RGBA32UI" -> GL30.GL_RGBA32UI;
            case "RGB10_A2" -> GL11.GL_RGB10_A2;
            case "RGB10_A2UI" -> GL33.GL_RGB10_A2UI;
            case "R11F_G11F_B10F" -> GL30.GL_R11F_G11F_B10F;
            case "RGB9_E5" -> GL30.GL_RGB9_E5;
            default -> 0;
        };
    }

    public static int pixelFormat(String value) {
        return switch (normalize(value)) {
            case "RED" -> GL11.GL_RED;
            case "RG" -> GL30.GL_RG;
            case "RGB" -> GL11.GL_RGB;
            case "BGR" -> GL12.GL_BGR;
            case "RGBA" -> GL11.GL_RGBA;
            case "BGRA" -> GL12.GL_BGRA;
            case "RED_INTEGER" -> GL30.GL_RED_INTEGER;
            case "RG_INTEGER" -> GL30.GL_RG_INTEGER;
            case "RGB_INTEGER" -> GL30.GL_RGB_INTEGER;
            case "BGR_INTEGER" -> GL30.GL_BGR_INTEGER;
            case "RGBA_INTEGER" -> GL30.GL_RGBA_INTEGER;
            case "BGRA_INTEGER" -> GL30.GL_BGRA_INTEGER;
            default -> 0;
        };
    }

    public static int pixelType(String value) {
        return switch (normalize(value)) {
            case "BYTE" -> GL11.GL_BYTE;
            case "SHORT" -> GL11.GL_SHORT;
            case "INT" -> GL11.GL_INT;
            case "HALF_FLOAT" -> GL30.GL_HALF_FLOAT;
            case "FLOAT" -> GL11.GL_FLOAT;
            case "UNSIGNED_BYTE" -> GL11.GL_UNSIGNED_BYTE;
            case "UNSIGNED_BYTE_3_3_2" -> GL12.GL_UNSIGNED_BYTE_3_3_2;
            case "UNSIGNED_BYTE_2_3_3_REV" -> GL12.GL_UNSIGNED_BYTE_2_3_3_REV;
            case "UNSIGNED_SHORT" -> GL11.GL_UNSIGNED_SHORT;
            case "UNSIGNED_SHORT_5_6_5" -> GL12.GL_UNSIGNED_SHORT_5_6_5;
            case "UNSIGNED_SHORT_5_6_5_REV" -> GL12.GL_UNSIGNED_SHORT_5_6_5_REV;
            case "UNSIGNED_SHORT_4_4_4_4" -> GL12.GL_UNSIGNED_SHORT_4_4_4_4;
            case "UNSIGNED_SHORT_4_4_4_4_REV" -> GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV;
            case "UNSIGNED_SHORT_5_5_5_1" -> GL12.GL_UNSIGNED_SHORT_5_5_5_1;
            case "UNSIGNED_SHORT_1_5_5_5_REV" -> GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV;
            case "UNSIGNED_INT" -> GL11.GL_UNSIGNED_INT;
            case "UNSIGNED_INT_8_8_8_8" -> GL12.GL_UNSIGNED_INT_8_8_8_8;
            case "UNSIGNED_INT_8_8_8_8_REV" -> GL12.GL_UNSIGNED_INT_8_8_8_8_REV;
            case "UNSIGNED_INT_10_10_10_2" -> GL12.GL_UNSIGNED_INT_10_10_10_2;
            case "UNSIGNED_INT_2_10_10_10_REV" -> GL12.GL_UNSIGNED_INT_2_10_10_10_REV;
            case "UNSIGNED_INT_10F_11F_11F_REV" -> GL30.GL_UNSIGNED_INT_10F_11F_11F_REV;
            case "UNSIGNED_INT_5_9_9_9_REV" -> GL30.GL_UNSIGNED_INT_5_9_9_9_REV;
            default -> 0;
        };
    }

    public static boolean isIntegerPixelFormat(int pixelFormat) {
        return pixelFormat == GL30.GL_RED_INTEGER
                || pixelFormat == GL30.GL_RG_INTEGER
                || pixelFormat == GL30.GL_RGB_INTEGER
                || pixelFormat == GL30.GL_BGR_INTEGER
                || pixelFormat == GL30.GL_RGBA_INTEGER
                || pixelFormat == GL30.GL_BGRA_INTEGER;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void logGlError(String operation, String resourcePath) {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            MainMod.LOGGER.warn("[ShaderTextures] GL error after {} for {}: 0x{}", operation, resourcePath, Integer.toHexString(error));
        }
    }

    public record RawTexture(int textureId, int textureTarget) {
    }
}
