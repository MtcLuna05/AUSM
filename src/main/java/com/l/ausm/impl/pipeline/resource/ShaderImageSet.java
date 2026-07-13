package com.l.ausm.impl.pipeline.resource;

import com.l.ausm.api.pipeline.pack.ShaderImageDirective;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.ShaderTextureLoader;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.ShaderBindingLayout;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public final class ShaderImageSet {
    private static final long MAX_RETAINED_CLEAR_BUFFER_BYTES = 32L * 1024L * 1024L;
    private final List<ShaderImageDirective> images;
    private final List<LoadedImage> loadedImages = new ArrayList<>();
    private static final ByteBuffer ZERO_CLEAR_VALUE = org.lwjgl.BufferUtils.createByteBuffer(16);
    private final ByteBuffer singleUintPixel = org.lwjgl.BufferUtils.createByteBuffer(4).order(ByteOrder.nativeOrder());
    private int width;
    private int height;

    private ShaderImageSet(List<ShaderImageDirective> images) {
        this.images = List.copyOf(images);
    }

    public static ShaderImageSet empty() {
        return new ShaderImageSet(List.of());
    }

    public static ShaderImageSet load(List<ShaderImageDirective> directives) {
        ShaderImageSet set = directives.isEmpty() ? empty() : new ShaderImageSet(directives);
        set.allocate(1, 1);
        return set;
    }

    public boolean active() {
        return !images.isEmpty();
    }

    public int count() {
        return images.size();
    }

    public void resize(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }
        allocate(width, height);
    }

    public void delete() {
        for (LoadedImage image : loadedImages) {
            GL42.glBindImageTexture(image.unit(), 0, 0, false, 0, GL15.GL_READ_WRITE, image.internalFormat());
            GL11.glDeleteTextures(image.textureId());
        }
        loadedImages.clear();
    }

    public void bind(ShaderProgram program) {
        for (LoadedImage image : loadedImages) {
            ShaderImageDirective directive = image.directive();
            GL42.glBindImageTexture(
                    image.unit(),
                    image.textureId(),
                    0,
                    directive.depth() > 0,
                    0,
                    GL15.GL_READ_WRITE,
                    image.internalFormat()
            );
            int imageLocation = program.getUniformLocation(directive.name());
            if (imageLocation != -1) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glUniform1i(imageLocation, image.unit());
            }
            if (directive.samplerName() != null && !directive.samplerName().isBlank()) {
                TextureBinder.bindTexture(image.textureTarget(), image.samplerUnit(), image.textureId());
                int location = program.getUniformLocation(directive.samplerName());
                if (location != -1) {
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glUniform1i(location, image.samplerUnit());
                }
            }
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void bindDisabled(ShaderProgram program) {
        for (LoadedImage image : loadedImages) {
            ShaderImageDirective directive = image.directive();
            GL42.glBindImageTexture(
                    image.unit(),
                    0,
                    0,
                    false,
                    0,
                    GL15.GL_READ_WRITE,
                    image.internalFormat()
            );
            int imageLocation = program.getUniformLocation(directive.name());
            if (imageLocation != -1) {
                com.l.ausm.impl.util.MinecraftReflectionCompat.glUniform1i(imageLocation, image.unit());
            }
            if (directive.samplerName() != null && !directive.samplerName().isBlank()) {
                TextureBinder.bindTexture(image.textureTarget(), image.samplerUnit(), 0);
                int location = program.getUniformLocation(directive.samplerName());
                if (location != -1) {
                    com.l.ausm.impl.util.MinecraftReflectionCompat.glUniform1i(location, image.samplerUnit());
                }
            }
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void clearSmallImages() {
        for (LoadedImage image : loadedImages) {
            clearImage(image, false);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    public void clearNamedImages(String... names) {
        if (names == null || names.length == 0) {
            return;
        }
        for (LoadedImage image : loadedImages) {
            if (matchesName(image, names)) {
                clearImage(image, true);
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        TextureBinder.restoreDefaultTextureUnit();
    }

    public int[] dimensions(String... names) {
        LoadedImage image = findImage(names);
        if (image == null) {
            return null;
        }
        return new int[]{image.width(), image.height(), image.depth()};
    }

    public boolean writeRedInteger3D(int x, int y, int z, int value, String... names) {
        LoadedImage image = findImage(names);
        if (image == null
                || image.textureTarget() != GL12.GL_TEXTURE_3D
                || x < 0 || y < 0 || z < 0
                || x >= image.width() || y >= image.height() || z >= image.depth()) {
            return false;
        }

        ShaderImageDirective directive = image.directive();
        int pixelFormat = ShaderTextureLoader.pixelFormat(directive.format());
        int pixelType = compatiblePixelType(image.internalFormat(), pixelFormat, ShaderTextureLoader.pixelType(directive.pixelType()));
        if (pixelFormat != org.lwjgl.opengl.GL30.GL_RED_INTEGER) {
            return false;
        }

        singleUintPixel.clear();
        switch (pixelType) {
            case GL11.GL_UNSIGNED_BYTE, GL11.GL_BYTE -> singleUintPixel.put((byte) value);
            case GL11.GL_UNSIGNED_SHORT, GL11.GL_SHORT -> singleUintPixel.putShort((short) value);
            case GL11.GL_UNSIGNED_INT, GL11.GL_INT -> singleUintPixel.putInt(value);
            default -> {
                return false;
            }
        }
        singleUintPixel.flip();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(image.textureTarget(), image.textureId());
        GL12.glTexSubImage3D(image.textureTarget(), 0, x, y, z, 1, 1, 1, pixelFormat, pixelType, singleUintPixel);
        GL11.glBindTexture(image.textureTarget(), 0);
        TextureBinder.restoreDefaultTextureUnit();
        return GL11.glGetError() == GL11.GL_NO_ERROR;
    }

    private LoadedImage findImage(String... names) {
        if (names == null || names.length == 0) {
            return null;
        }
        for (LoadedImage image : loadedImages) {
            if (matchesName(image, names)) {
                return image;
            }
        }
        return null;
    }

    private static boolean matchesName(LoadedImage image, String... names) {
        ShaderImageDirective directive = image.directive();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            if (name.equals(directive.name()) || name.equals(directive.samplerName())) {
                return true;
            }
        }
        return false;
    }

    private void clearImage(LoadedImage image, boolean force) {
        ShaderImageDirective directive = image.directive();
        if (!force && !directive.clear()) {
            return;
        }

        int pixelFormat = ShaderTextureLoader.pixelFormat(directive.format());
        int pixelType = compatiblePixelType(image.internalFormat(), pixelFormat, ShaderTextureLoader.pixelType(directive.pixelType()));
        if (GLContext.getCapabilities().OpenGL44) {
            ZERO_CLEAR_VALUE.clear();
            GL44.glClearTexImage(image.textureId(), 0, pixelFormat, pixelType, ZERO_CLEAR_VALUE);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                MainMod.LOGGER.debug("[ShaderImages] GL error clearing image '{}': 0x{}", directive.name(), Integer.toHexString(error));
            }
            return;
        }

        ByteBuffer pixels = image.clearPixels();
        if (pixels == null && force) {
            pixels = zeroPixels(pixelFormat, pixelType, image.width(), image.height(), image.depth());
        }
        if (pixels == null) {
            return;
        }

        pixels.clear();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(image.textureTarget(), image.textureId());
        switch (directive.target()) {
            case TEXTURE_1D -> GL11.glTexSubImage1D(image.textureTarget(), 0, 0, image.width(), pixelFormat, pixelType, pixels);
            case TEXTURE_2D -> GL11.glTexSubImage2D(image.textureTarget(), 0, 0, 0, image.width(), image.height(), pixelFormat, pixelType, pixels);
            case TEXTURE_3D -> GL12.glTexSubImage3D(image.textureTarget(), 0, 0, 0, 0, image.width(), image.height(), image.depth(), pixelFormat, pixelType, pixels);
        }
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            MainMod.LOGGER.debug("[ShaderImages] GL error clearing image '{}': 0x{}", directive.name(), Integer.toHexString(error));
        }
    }

    private void allocate(int width, int height) {
        delete();
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        for (int i = 0; i < images.size(); i++) {
            ShaderImageDirective directive = images.get(i);
            LoadedImage loaded = createImage(i, directive);
            if (loaded != null) {
                loadedImages.add(loaded);
            }
        }
        if (!loadedImages.isEmpty()) {
            MainMod.LOGGER.debug(
                    "[ShaderImages] Allocated {} custom image textures: {}",
                    loadedImages.size(),
                    loadedImages.stream()
                            .map(image -> image.directive().name() + "=" + image.width() + "x" + image.height() + "x" + image.depth())
                            .toList()
            );
        }
    }

    private LoadedImage createImage(int unit, ShaderImageDirective directive) {
        int target = ShaderTextureLoader.textureTarget(directive.target());
        int internalFormat = ShaderTextureLoader.internalFormat(directive.internalFormat());
        int pixelFormat = ShaderTextureLoader.pixelFormat(directive.format());
        int pixelType = compatiblePixelType(internalFormat, pixelFormat, ShaderTextureLoader.pixelType(directive.pixelType()));
        if (target == 0 || internalFormat == 0 || pixelFormat == 0 || pixelType == 0) {
            MainMod.LOGGER.warn("[ShaderImages] Ignoring image with unsupported format: {}", directive);
            return null;
        }

        int imageWidth = directive.relative()
                ? Math.max(1, (int) (width * directive.relativeWidth()))
                : Math.max(1, directive.width());
        int imageHeight = directive.relative()
                ? Math.max(1, (int) (height * directive.relativeHeight()))
                : Math.max(1, directive.height());
        int imageDepth = Math.max(1, directive.depth());

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(target, textureId);
        boolean integerFormat = ShaderTextureLoader.isIntegerPixelFormat(pixelFormat);
        int filter = integerFormat ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        if (target != GL11.GL_TEXTURE_1D) {
            GL11.glTexParameteri(target, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }
        if (target == GL12.GL_TEXTURE_3D) {
            GL11.glTexParameteri(target, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        }

        switch (directive.target()) {
            case TEXTURE_1D -> GL11.glTexImage1D(target, 0, internalFormat, imageWidth, 0, pixelFormat, pixelType, (ByteBuffer) null);
            case TEXTURE_2D -> GL11.glTexImage2D(target, 0, internalFormat, imageWidth, imageHeight, 0, pixelFormat, pixelType, (ByteBuffer) null);
            case TEXTURE_3D -> GL12.glTexImage3D(target, 0, internalFormat, imageWidth, imageHeight, imageDepth, 0, pixelFormat, pixelType, (ByteBuffer) null);
        }
        ByteBuffer perFrameClearPixels = directive.clear()
                ? retainedClearBuffer(pixelFormat, pixelType, imageWidth, imageHeight, imageDepth)
                : null;
        GL11.glBindTexture(target, 0);
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            MainMod.LOGGER.warn("[ShaderImages] GL error allocating image '{}': 0x{}", directive.name(), Integer.toHexString(error));
            GL11.glDeleteTextures(textureId);
            return null;
        }
        LoadedImage image = new LoadedImage(
                directive,
                unit,
                ShaderBindingLayout.CUSTOM_IMAGE_TEXTURE_BASE_UNIT + unit,
                textureId,
                target,
                internalFormat,
                imageWidth,
                imageHeight,
                imageDepth,
                perFrameClearPixels
        );
        if (directive.clear()) {
            clearImage(image, true);
        }
        return image;
    }

    private static int compatiblePixelType(int internalFormat, int pixelFormat, int requestedPixelType) {
        if (!ShaderTextureLoader.isIntegerPixelFormat(pixelFormat)) {
            return requestedPixelType;
        }
        return switch (internalFormat) {
            case org.lwjgl.opengl.GL30.GL_R8UI,
                 org.lwjgl.opengl.GL30.GL_RG8UI,
                 org.lwjgl.opengl.GL30.GL_RGB8UI,
                 org.lwjgl.opengl.GL30.GL_RGBA8UI -> GL11.GL_UNSIGNED_BYTE;
            case org.lwjgl.opengl.GL30.GL_R8I,
                 org.lwjgl.opengl.GL30.GL_RG8I,
                 org.lwjgl.opengl.GL30.GL_RGB8I,
                 org.lwjgl.opengl.GL30.GL_RGBA8I -> GL11.GL_BYTE;
            case org.lwjgl.opengl.GL30.GL_R16UI,
                 org.lwjgl.opengl.GL30.GL_RG16UI,
                 org.lwjgl.opengl.GL30.GL_RGB16UI,
                 org.lwjgl.opengl.GL30.GL_RGBA16UI -> GL11.GL_UNSIGNED_SHORT;
            case org.lwjgl.opengl.GL30.GL_R16I,
                 org.lwjgl.opengl.GL30.GL_RG16I,
                 org.lwjgl.opengl.GL30.GL_RGB16I,
                 org.lwjgl.opengl.GL30.GL_RGBA16I -> GL11.GL_SHORT;
            case org.lwjgl.opengl.GL30.GL_R32UI,
                 org.lwjgl.opengl.GL30.GL_RG32UI,
                 org.lwjgl.opengl.GL30.GL_RGB32UI,
                 org.lwjgl.opengl.GL30.GL_RGBA32UI -> GL11.GL_UNSIGNED_INT;
            case org.lwjgl.opengl.GL30.GL_R32I,
                 org.lwjgl.opengl.GL30.GL_RG32I,
                 org.lwjgl.opengl.GL30.GL_RGB32I,
                 org.lwjgl.opengl.GL30.GL_RGBA32I -> GL11.GL_INT;
            default -> requestedPixelType;
        };
    }

    private static ByteBuffer retainedClearBuffer(int pixelFormat, int pixelType, int width, int height, int depth) {
        long size = (long) componentCount(pixelFormat) * byteSize(pixelType) * width * Math.max(1, height) * Math.max(1, depth);
        if (size <= 0 || size > MAX_RETAINED_CLEAR_BUFFER_BYTES || size > Integer.MAX_VALUE) {
            return null;
        }
        return org.lwjgl.BufferUtils.createByteBuffer((int) size);
    }

    private static ByteBuffer zeroPixels(int pixelFormat, int pixelType, int width, int height, int depth) {
        long size = (long) componentCount(pixelFormat) * byteSize(pixelType) * width * Math.max(1, height) * Math.max(1, depth);
        if (size <= 0 || size > MAX_RETAINED_CLEAR_BUFFER_BYTES || size > Integer.MAX_VALUE) {
            return null;
        }
        return org.lwjgl.BufferUtils.createByteBuffer((int) size);
    }

    private static int componentCount(int pixelFormat) {
        return switch (pixelFormat) {
            case GL11.GL_RED, org.lwjgl.opengl.GL30.GL_RED_INTEGER -> 1;
            case org.lwjgl.opengl.GL30.GL_RG, org.lwjgl.opengl.GL30.GL_RG_INTEGER -> 2;
            case GL11.GL_RGB, GL12.GL_BGR, org.lwjgl.opengl.GL30.GL_RGB_INTEGER, org.lwjgl.opengl.GL30.GL_BGR_INTEGER -> 3;
            default -> 4;
        };
    }

    private static int byteSize(int pixelType) {
        return switch (pixelType) {
            case GL11.GL_BYTE, GL11.GL_UNSIGNED_BYTE, GL12.GL_UNSIGNED_BYTE_3_3_2, GL12.GL_UNSIGNED_BYTE_2_3_3_REV -> 1;
            case GL11.GL_SHORT, GL11.GL_UNSIGNED_SHORT, GL12.GL_UNSIGNED_SHORT_5_6_5, GL12.GL_UNSIGNED_SHORT_5_6_5_REV,
                 GL12.GL_UNSIGNED_SHORT_4_4_4_4, GL12.GL_UNSIGNED_SHORT_4_4_4_4_REV, GL12.GL_UNSIGNED_SHORT_5_5_5_1,
                 GL12.GL_UNSIGNED_SHORT_1_5_5_5_REV, org.lwjgl.opengl.GL30.GL_HALF_FLOAT -> 2;
            default -> 4;
        };
    }

    private record LoadedImage(
            ShaderImageDirective directive,
            int unit,
            int samplerUnit,
            int textureId,
            int textureTarget,
            int internalFormat,
            int width,
            int height,
            int depth,
            ByteBuffer clearPixels
    ) {
    }
}
