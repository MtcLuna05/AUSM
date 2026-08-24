package com.luna.ausm.api.pipeline.fbo;

public enum ColorBufferFormat {
    RGBA(0x8058, 0x1908, 0x1401),
    RGBA8(0x8058, 0x1908, 0x1401),
    RGBA8_SNORM(0x8F97, 0x1908, 0x1400),
    RG8(0x822B, 0x8227, 0x1401),
    RGB8(0x8051, 0x1907, 0x1401),
    R16(0x822A, 0x1903, 0x1403),
    RG16(0x822C, 0x8227, 0x1403),
    RGB16(0x8054, 0x1907, 0x1403),
    RGBA16(0x805B, 0x1908, 0x1403),
    RGBA16F(0x881A, 0x1908, 0x1406),
    RG16F(0x822F, 0x8227, 0x1406),
    RGB16F(0x881B, 0x1907, 0x1406),
    R8(0x8229, 0x1903, 0x1401),
    R16F(0x822D, 0x1903, 0x1406),
    R32F(0x822E, 0x1903, 0x1406),
    RG32F(0x8230, 0x8227, 0x1406),
    RGB32F(0x8815, 0x1907, 0x1406),
    RGBA32F(0x8814, 0x1908, 0x1406),
    RGB10_A2(0x8059, 0x1908, 0x8368),
    R11F_G11F_B10F(0x8C3A, 0x1907, 0x1406);

    private final int internalFormat;
    private final int pixelFormat;
    private final int pixelType;

    ColorBufferFormat(int internalFormat, int pixelFormat, int pixelType) {
        this.internalFormat = internalFormat;
        this.pixelFormat = pixelFormat;
        this.pixelType = pixelType;
    }

    public int internalFormat() {
        return internalFormat;
    }

    public int pixelFormat() {
        return pixelFormat;
    }

    public int pixelType() {
        return pixelType;
    }

    public static ColorBufferFormat fromName(String name) {
        for (ColorBufferFormat format : values()) {
            if (format.name().equals(name)) {
                return format;
            }
        }
        return null;
    }
}
