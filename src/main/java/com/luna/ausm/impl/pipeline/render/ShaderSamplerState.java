package com.luna.ausm.impl.pipeline.render;

import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Global shaderpack sampler state that has to affect vanilla-owned textures too.
 */
public final class ShaderSamplerState {
    private static boolean breaksAnisotropy;
    private static Field optifineAnisotropyField;
    private static boolean optifineAnisotropyFieldResolved;

    private ShaderSamplerState() {
    }

    public static void setBreaksAnisotropy(boolean breaks) {
        breaksAnisotropy = breaks;
    }

    public static int anisotropicFilteringUniform() {
        if (breaksAnisotropy || !anisotropySupported()) {
            return 0;
        }
        return optifineAnisotropyLevel();
    }

    public static int textureFilteringModeUniform() {
        return anisotropicFilteringUniform() > 0 ? 2 : 0;
    }

    public static void clampTextureAnisotropyIfNeeded(int textureTarget) {
        if (!breaksAnisotropy || !anisotropySupported()) {
            return;
        }
        GL11.glTexParameterf(
                textureTarget,
                EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                1.0F
        );
    }

    private static boolean anisotropySupported() {
        try {
            return GLContext.getCapabilities() != null
                    && GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic;
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static int optifineAnisotropyLevel() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null || MinecraftReflectionCompat.gameSettings(minecraft) == null) {
            return 0;
        }

        Field field = optifineAnisotropyField();
        if (field == null) {
            return 0;
        }

        try {
            return Math.max(0, field.getInt(MinecraftReflectionCompat.gameSettings(minecraft)));
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
            return 0;
        }
    }

    private static Field optifineAnisotropyField() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null || MinecraftReflectionCompat.gameSettings(minecraft) == null) {
            return null;
        }
        if (optifineAnisotropyFieldResolved) {
            return optifineAnisotropyField;
        }

        optifineAnisotropyFieldResolved = true;
        try {
            Field field = MinecraftReflectionCompat.findField(
                    MinecraftReflectionCompat.gameSettings(minecraft).getClass(),
                    "ofAfLevel"
            );
            if (field == null) {
                optifineAnisotropyField = null;
                return null;
            }
            field.setAccessible(true);
            optifineAnisotropyField = field;
        } catch (SecurityException ignored) {
            optifineAnisotropyField = null;
        }
        return optifineAnisotropyField;
    }
}
