package com.luna.ausm.api.pipeline.pack;

import java.util.Objects;

public final class ShaderTextureScale {
    private final String widthValue;
    private final String heightValue;

    public ShaderTextureScale(String widthValue, String heightValue) {
        this.widthValue = widthValue;
        this.heightValue = heightValue;
    }

    public String widthValue() {
        return widthValue;
    }

    public String heightValue() {
        return heightValue;
    }

    public int width(int baseWidth) {
        return dimension(widthValue, baseWidth);
    }

    public int height(int baseHeight) {
        return dimension(heightValue, baseHeight);
    }

    private static int dimension(String value, int base) {
        try {
            if (value.contains(".")) {
                return Math.max(1, (int) (base * Float.parseFloat(value)));
            }
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return Math.max(1, base);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShaderTextureScale)) {
            return false;
        }
        ShaderTextureScale scale = (ShaderTextureScale) other;
        return Objects.equals(widthValue, scale.widthValue) && Objects.equals(heightValue, scale.heightValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(widthValue, heightValue);
    }

    @Override
    public String toString() {
        return "ShaderTextureScale[widthValue=" + widthValue + ", heightValue=" + heightValue + ']';
    }
}
