package com.l.ausm.impl.pipeline.render;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.l.ausm.impl.pipeline.shader.ShaderBindingLayout;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Handles binding the G-Buffer textures into the correct OpenGL Texture Units
 * before executing Deferred and Composite shader passes.
 */
public class TextureBinder {
    public static final int LIGHTMAP_TEXTURE_UNIT = 2;
    public static final int SHADOWTEX0_TEXTURE_UNIT = ShaderBindingLayout.SHADOW_TEXTURE_BASE_UNIT;
    public static final int SHADOWTEX1_TEXTURE_UNIT = ShaderBindingLayout.SHADOW_TEXTURE_BASE_UNIT + 1;
    public static final int SHADOWCOLOR0_TEXTURE_UNIT = ShaderBindingLayout.SHADOW_COLOR_TEXTURE_BASE_UNIT;
    public static final int SHADOWCOLOR1_TEXTURE_UNIT = ShaderBindingLayout.SHADOW_COLOR_TEXTURE_BASE_UNIT + 1;
    public static final int SHADOWTEX0_HW_TEXTURE_UNIT = ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT - 2;
    public static final int SHADOWTEX1_HW_TEXTURE_UNIT = ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT - 1;
    public static final int DEPTHTEX0_TEXTURE_UNIT = ShaderBindingLayout.DEPTH_TEXTURE_BASE_UNIT;
    public static final int COLORTEX4_TEXTURE_UNIT = ShaderBindingLayout.HIGH_COLOR_TEXTURE_BASE_UNIT;
    public static final int COLORTEX5_TEXTURE_UNIT = ShaderBindingLayout.HIGH_COLOR_TEXTURE_BASE_UNIT + 1;
    public static final int COLORTEX6_TEXTURE_UNIT = ShaderBindingLayout.HIGH_COLOR_TEXTURE_BASE_UNIT + 2;
    public static final int COLORTEX7_TEXTURE_UNIT = ShaderBindingLayout.HIGH_COLOR_TEXTURE_BASE_UNIT + 3;
    public static final int DEPTHTEX1_TEXTURE_UNIT = ShaderBindingLayout.DEPTH_TEXTURE_BASE_UNIT + 1;
    public static final int DEPTHTEX2_TEXTURE_UNIT = ShaderBindingLayout.DEPTH_TEXTURE_BASE_UNIT + 2;
    public static final int NOISETEX_TEXTURE_UNIT = ShaderBindingLayout.NOISE_TEXTURE_UNIT;
    public static final int COLORTEX8_TEXTURE_UNIT = ShaderBindingLayout.NOISE_TEXTURE_UNIT + 1;
    public static final int COLORTEX9_TEXTURE_UNIT = ShaderBindingLayout.NOISE_TEXTURE_UNIT + 2;
    public static final int COLORTEX16_TEXTURE_UNIT = ShaderBindingLayout.NOISE_TEXTURE_UNIT + 3;
    public static final int CENTER_DEPTH_SMOOTH_TEXTURE_UNIT = ShaderBindingLayout.NOISE_TEXTURE_UNIT + 4;
    public static final int SPECULAR_TEXTURE_UNIT = ShaderBindingLayout.CUSTOM_TEXTURE_BASE_UNIT - 3;
    private static int fallbackBlackTexture = -1;
    private static int fallbackWhiteTexture = -1;
    private static int fallbackNormalTexture = -1;
    private static int fallbackSpecularTexture = -1;
    private static int neutralShadowDepthTexture = -1;
    private static int neutralShadowColorTexture = -1;
    private static int shadowDepthSampler = -1;
    private static int shadowDepthHardwareSampler = -1;
    private static int maxCombinedTextureUnits = -1;

    /**
     * Binds the multiple render targets (MRTs) from the "read" framebuffer 
     * so that the fullscreen shader pass can sample them.
     */
    public static void bindDeferredTextures() {
        DeferredFramebuffer readBuffer =
                PipelineContext.getInstance()
                        .getPingPongManager()
                        .getReadBuffer();

        for (Attachment attachment : Attachment.values()) {
            bindAttachment(readBuffer, attachment, textureUnitForAttachment(attachment));
        }

        bindRawTexture(DEPTHTEX0_TEXTURE_UNIT, readBuffer.getDepthTexture());
        bindDepthTexture(readBuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT, DEPTHTEX1_TEXTURE_UNIT);
        bindDepthTexture(readBuffer, DeferredFramebuffer.DEPTHTEX2_SNAPSHOT, DEPTHTEX2_TEXTURE_UNIT);
        bindRawTexture(COLORTEX16_TEXTURE_UNIT, fallbackBlackTexture());
        bindRawTexture(CENTER_DEPTH_SMOOTH_TEXTURE_UNIT, PipelineContext.getInstance().getCenterDepthSmoothTexture());
        bindNoiseTexture();

        restoreDefaultTextureUnit();
    }

    /**
     * Iris exposes render-target samplers to gbuffers programs, but only for
     * colortex4+ so the world texture atlas can remain bound on unit 0.
     */
    public static void bindGbufferRenderTargetSamplers() {
        DeferredFramebuffer readBuffer =
                PipelineContext.getInstance()
                        .getPingPongManager()
                        .getReadBuffer();

        bindAttachment(readBuffer, Attachment.AUX1, COLORTEX4_TEXTURE_UNIT);
        bindAttachment(readBuffer, Attachment.AUX2, COLORTEX5_TEXTURE_UNIT);
        bindAttachment(readBuffer, Attachment.AUX3, COLORTEX6_TEXTURE_UNIT);
        bindAttachment(readBuffer, Attachment.AUX4, COLORTEX7_TEXTURE_UNIT);
        bindAttachment(readBuffer, Attachment.AUX5, COLORTEX8_TEXTURE_UNIT);
        bindAttachment(readBuffer, Attachment.AUX6, COLORTEX9_TEXTURE_UNIT);

        bindRawTexture(DEPTHTEX0_TEXTURE_UNIT, readBuffer.getDepthTexture());
        bindDepthTexture(readBuffer, DeferredFramebuffer.DEPTHTEX1_SNAPSHOT, DEPTHTEX1_TEXTURE_UNIT);
        bindDepthTexture(readBuffer, DeferredFramebuffer.DEPTHTEX2_SNAPSHOT, DEPTHTEX2_TEXTURE_UNIT);
        bindRawTexture(COLORTEX16_TEXTURE_UNIT, fallbackBlackTexture());
        bindRawTexture(CENTER_DEPTH_SMOOTH_TEXTURE_UNIT, PipelineContext.getInstance().getCenterDepthSmoothTexture());
        bindNoiseTexture();

        restoreDefaultTextureUnit();
    }

    public static void bindNoiseTexture() {
        bindRawTexture(NOISETEX_TEXTURE_UNIT, PipelineContext.getInstance().getNoiseTexture());
    }

    public static void bindShadowTextures() {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldUseNeutralShadowTextures()) {
            bindNeutralShadowTextures(context.shouldUseShadowHardwareFiltering());
            return;
        }

        int shadowDepthTexture = context.getShadowDepthTexture();
        int shadowDepthSnapshotTexture = context.getShadowDepthSnapshotTexture();
        if (shadowDepthTexture == -1 || shadowDepthSnapshotTexture == -1) {
            return;
        }

        bindShadowDepthTexture(SHADOWTEX0_TEXTURE_UNIT, shadowDepthTexture, true);
        bindShadowDepthTexture(SHADOWTEX1_TEXTURE_UNIT, shadowDepthSnapshotTexture, true);
        if (supportsSamplerObjects()) {
            bindShadowDepthTexture(SHADOWTEX0_HW_TEXTURE_UNIT, shadowDepthTexture, true);
            bindShadowDepthTexture(SHADOWTEX1_HW_TEXTURE_UNIT, shadowDepthSnapshotTexture, true);
        } else {
            context.configureShadowDepthTextureCompareMode();
        }
        for (int i = 0; i < ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT; i++) {
            int shadowColorTexture = context.getShadowColorTexture(i);
            if (shadowColorTexture != -1) {
                bindRawTexture(shadowColorTextureUnit(i), shadowColorTexture);
            }
        }
        restoreDefaultTextureUnit();
    }

    public static void bindMaterialFallbackTextures() {
        bindRawTexture(textureUnitForSampler("normals"), fallbackNormalTexture());
        bindRawTexture(textureUnitForSampler("specular"), fallbackSpecularTexture());
        restoreDefaultTextureUnit();
    }

    public static void bindFallbackWhiteTexture() {
        bindRawTexture(0, fallbackWhiteTexture());
        restoreDefaultTextureUnit();
    }

    public static void mirrorVanillaLightmapToIrisUnit() {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 1);
            int lightmapTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            if (lightmapTexture > 0) {
                bindIrisLightmap(lightmapTexture);
            }
        } finally {
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    public static void bindIrisLightmap(int textureId) {
        if (textureId > 0) {
            bindRawTexture(LIGHTMAP_TEXTURE_UNIT, textureId);
        }
    }

    private static void bindDepthTexture(DeferredFramebuffer fbo, int snapshotIndex, int textureUnitIndex) {
        int depthTex = fbo.getDepthSamplerTexture(snapshotIndex);
        if (depthTex != -1) {
            bindRawTexture(textureUnitIndex, depthTex);
        }
    }

    private static void bindAttachment(DeferredFramebuffer fbo, Attachment attachment, int textureUnitIndex) {
        int texId = fbo.getTexture(attachment);
        if (texId != -1) {
            bindRawTexture(textureUnitIndex, texId);
        }
    }

    private static int textureUnitForAttachment(Attachment attachment) {
        return switch (attachment) {
            case COLOR -> 0;
            case DEPTH -> 1;
            case NORMAL -> 2;
            case COMPOSITE -> 3;
            case AUX1 -> COLORTEX4_TEXTURE_UNIT;
            case AUX2 -> COLORTEX5_TEXTURE_UNIT;
            case AUX3 -> COLORTEX6_TEXTURE_UNIT;
            case AUX4 -> COLORTEX7_TEXTURE_UNIT;
            case AUX5 -> COLORTEX8_TEXTURE_UNIT;
            case AUX6 -> COLORTEX9_TEXTURE_UNIT;
        };
    }

    public static int textureUnitForSampler(String samplerName) {
        if (samplerName.startsWith("colortex")) {
            try {
                int unit = Integer.parseInt(samplerName.substring("colortex".length()));
                return switch (unit) {
                    case 0, 1, 2, 3 -> unit;
                    case 4 -> COLORTEX4_TEXTURE_UNIT;
                    case 5 -> COLORTEX5_TEXTURE_UNIT;
                    case 6 -> COLORTEX6_TEXTURE_UNIT;
                    case 7 -> COLORTEX7_TEXTURE_UNIT;
                    case 8 -> COLORTEX8_TEXTURE_UNIT;
                    case 9 -> COLORTEX9_TEXTURE_UNIT;
                    case 16 -> COLORTEX16_TEXTURE_UNIT;
                    default -> -1;
                };
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        if (samplerName.startsWith("shadowcolor")) {
            if ("shadowcolor".equals(samplerName)) {
                return SHADOWCOLOR0_TEXTURE_UNIT;
            }
            try {
                return shadowColorTextureUnit(Integer.parseInt(samplerName.substring("shadowcolor".length())));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return switch (samplerName) {
            case "tex", "texture", "gtexture", "u_MainSampler", "gcolor" -> 0;
            case "iris_overlay" -> 1;
            case "lightmap" -> LIGHTMAP_TEXTURE_UNIT;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "normals" -> 3;
            case "composite" -> 3;
            case "specular" -> SPECULAR_TEXTURE_UNIT;
            case "shadow", "watershadow", "shadowtex0" -> SHADOWTEX0_TEXTURE_UNIT;
            case "shadowtex1" -> SHADOWTEX1_TEXTURE_UNIT;
            case "shadowtex0HW" -> supportsSamplerObjects() ? SHADOWTEX0_HW_TEXTURE_UNIT : SHADOWTEX0_TEXTURE_UNIT;
            case "shadowtex1HW" -> supportsSamplerObjects() ? SHADOWTEX1_HW_TEXTURE_UNIT : SHADOWTEX1_TEXTURE_UNIT;
            case "gaux1" -> COLORTEX4_TEXTURE_UNIT;
            case "gaux2" -> COLORTEX5_TEXTURE_UNIT;
            case "gaux3" -> COLORTEX6_TEXTURE_UNIT;
            case "gaux4" -> COLORTEX7_TEXTURE_UNIT;
            case "gdepthtex", "depthtex0", "dhDepthTex", "dhDepthTex0" -> DEPTHTEX0_TEXTURE_UNIT;
            case "depthtex1", "dhDepthTex1" -> DEPTHTEX1_TEXTURE_UNIT;
            case "depthtex2", "dhDepthTex2" -> DEPTHTEX2_TEXTURE_UNIT;
            case "noisetex" -> NOISETEX_TEXTURE_UNIT;
            case "iris_centerDepthSmooth" -> CENTER_DEPTH_SMOOTH_TEXTURE_UNIT;
            default -> -1;
        };
    }

    public static int shadowColorTextureUnit(int index) {
        if (index < 0 || index >= ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT) {
            return -1;
        }
        return ShaderBindingLayout.SHADOW_COLOR_TEXTURE_BASE_UNIT + index;
    }

    public static void bindRawTexture(int textureUnitIndex, int textureId) {
        bindTexture(GL11.GL_TEXTURE_2D, textureUnitIndex, textureId);
    }

    public static void bindTexture(int textureTarget, int textureUnitIndex, int textureId) {
        if (!isValidTextureUnit(textureUnitIndex)) {
            return;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnitIndex);
        if (supportsSamplerObjects()) {
            GL33.glBindSampler(textureUnitIndex, 0);
        }
        GL11.glBindTexture(textureTarget, textureId);
    }

    private static void bindShadowDepthTexture(int textureUnitIndex, int textureId, boolean hardwareFiltering) {
        if (!isValidTextureUnit(textureUnitIndex)) {
            return;
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnitIndex);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        if (supportsSamplerObjects()) {
            GL33.glBindSampler(textureUnitIndex, hardwareFiltering ? shadowDepthHardwareSampler() : shadowDepthSampler());
        } else {
            GL11.glTexParameteri(
                    GL11.GL_TEXTURE_2D,
                    GL14.GL_TEXTURE_COMPARE_MODE,
                    hardwareFiltering ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE
            );
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        }
    }

    public static void unbindAllTextureTargets() {
        int maxUnits = maxCombinedTextureUnits();
        for (int unit = 0; unit < maxUnits; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            if (supportsSamplerObjects()) {
                GL33.glBindSampler(unit, 0);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_1D, 0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
        }
        restoreDefaultTextureUnit();
    }

    private static boolean isValidTextureUnit(int textureUnitIndex) {
        return textureUnitIndex >= 0 && textureUnitIndex < maxCombinedTextureUnits();
    }

    private static int maxCombinedTextureUnits() {
        if (maxCombinedTextureUnits < 0) {
            maxCombinedTextureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
            if (maxCombinedTextureUnits <= 0) {
                maxCombinedTextureUnits = 32;
            }
        }
        return maxCombinedTextureUnits;
    }

    public static void restoreDefaultTextureUnit() {
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }

    private static int fallbackBlackTexture() {
        if (fallbackBlackTexture != -1) {
            return fallbackBlackTexture;
        }

        fallbackBlackTexture = createFallbackTexture((byte) 0, (byte) 0, (byte) 0, (byte) 255);
        return fallbackBlackTexture;
    }

    private static int fallbackWhiteTexture() {
        if (fallbackWhiteTexture != -1) {
            return fallbackWhiteTexture;
        }

        fallbackWhiteTexture = createFallbackTexture((byte) 255, (byte) 255, (byte) 255, (byte) 255);
        return fallbackWhiteTexture;
    }

    private static int fallbackNormalTexture() {
        if (fallbackNormalTexture != -1) {
            return fallbackNormalTexture;
        }

        fallbackNormalTexture = createFallbackTexture((byte) 128, (byte) 128, (byte) 255, (byte) 255);
        return fallbackNormalTexture;
    }

    private static int fallbackSpecularTexture() {
        if (fallbackSpecularTexture != -1) {
            return fallbackSpecularTexture;
        }

        fallbackSpecularTexture = createFallbackTexture((byte) 0, (byte) 0, (byte) 0, (byte) 255);
        return fallbackSpecularTexture;
    }

    private static void bindNeutralShadowTextures(boolean hardwareFiltering) {
        int depthTexture = neutralShadowDepthTexture(hardwareFiltering);
        int colorTexture = neutralShadowColorTexture();
        bindShadowDepthTexture(SHADOWTEX0_TEXTURE_UNIT, depthTexture, true);
        bindShadowDepthTexture(SHADOWTEX1_TEXTURE_UNIT, depthTexture, true);
        if (supportsSamplerObjects()) {
            bindShadowDepthTexture(SHADOWTEX0_HW_TEXTURE_UNIT, depthTexture, true);
            bindShadowDepthTexture(SHADOWTEX1_HW_TEXTURE_UNIT, depthTexture, true);
        }
        for (int i = 0; i < ShadowFramebuffer.SHADOW_COLOR_TARGET_COUNT; i++) {
            bindRawTexture(shadowColorTextureUnit(i), colorTexture);
        }
        restoreDefaultTextureUnit();
    }

    private static int neutralShadowDepthTexture(boolean hardwareFiltering) {
        if (neutralShadowDepthTexture == -1) {
            neutralShadowDepthTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, neutralShadowDepthTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
            FloatBuffer depth = org.lwjgl.BufferUtils.createFloatBuffer(1);
            depth.put(1.0f).flip();
            GL11.glTexImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    GL14.GL_DEPTH_COMPONENT32,
                    1,
                    1,
                    0,
                    GL11.GL_DEPTH_COMPONENT,
                    GL11.GL_FLOAT,
                    depth
            );
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, neutralShadowDepthTexture);
        }
        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL14.GL_TEXTURE_COMPARE_MODE,
                    GL14.GL_COMPARE_R_TO_TEXTURE
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        return neutralShadowDepthTexture;
    }

    public static boolean supportsSamplerObjects() {
        return GLContext.getCapabilities().OpenGL33;
    }

    private static int shadowDepthSampler() {
        if (shadowDepthSampler == -1) {
            shadowDepthSampler = createShadowDepthSampler(false);
        }
        return shadowDepthSampler;
    }

    private static int shadowDepthHardwareSampler() {
        if (shadowDepthHardwareSampler == -1) {
            shadowDepthHardwareSampler = createShadowDepthSampler(true);
        }
        return shadowDepthHardwareSampler;
    }

    private static int createShadowDepthSampler(boolean hardwareFiltering) {
        int sampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MIN_FILTER, hardwareFiltering ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_MAG_FILTER, hardwareFiltering ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(sampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(
                sampler,
                GL14.GL_TEXTURE_COMPARE_MODE,
                GL14.GL_COMPARE_R_TO_TEXTURE
        );
        GL33.glSamplerParameteri(sampler, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        return sampler;
    }

    private static int neutralShadowColorTexture() {
        if (neutralShadowColorTexture != -1) {
            return neutralShadowColorTexture;
        }

        neutralShadowColorTexture = createFallbackTexture((byte) 255, (byte) 255, (byte) 255, (byte) 255);
        return neutralShadowColorTexture;
    }

    private static int createFallbackTexture(byte r, byte g, byte b, byte a) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
        pixel.put(r).put(g).put(b).put(a).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return texture;
    }
}
