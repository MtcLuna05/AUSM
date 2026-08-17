package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.ShaderSamplerState;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Shaderpack-compatible shadow depth/color target.
 */
public final class ShadowFramebuffer {
    public static final int SHADOW_COLOR_TARGET_COUNT = ShaderRenderTargetSettings.SHADOW_COLOR_TARGET_COUNT;
    private int fboId = -1;
    private int depthCopyFboId = -1;
    private int depthTextureId = -1;
    private int depthSnapshotTextureId = -1;
    private int rawDepthTextureId = -1;
    private final int[] colorTextureIds;
    private final int resolution;
    private final ShaderRenderTargetSettings settings;
    private final IntBuffer drawBufferList;
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
    private final ByteBuffer colorMaskBuffer = BufferUtils.createByteBuffer(4);
    private final FloatBuffer clearColorBuffer = BufferUtils.createFloatBuffer(4);
    private final FloatBuffer depthReadBuffer = BufferUtils.createFloatBuffer(1);
    private int depthCopyProbeCount;

    public ShadowFramebuffer(int resolution, ShaderRenderTargetSettings settings) {
        this.resolution = resolution;
        this.settings = settings;
        this.colorTextureIds = new int[supportedShadowColorTargetCount()];
        this.drawBufferList = BufferUtils.createIntBuffer(colorTextureIds.length);
        Arrays.fill(colorTextureIds, -1);
        create();
    }

    private static int supportedShadowColorTargetCount() {
        int maxDrawBuffers = GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
        int maxColorAttachments = GL11.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
        int hardwareLimit = Math.min(maxDrawBuffers, maxColorAttachments);
        if (hardwareLimit <= 0) {
            return 1;
        }
        return Math.min(SHADOW_COLOR_TARGET_COUNT, hardwareLimit);
    }

    private void create() {
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        fboId = MinecraftReflectionCompat.glGenFramebuffers();
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), fboId);

        depthTextureId = allocateDepthTexture(0);
        depthSnapshotTextureId = allocateDepthTexture(1);
        rawDepthTextureId = allocateRawDepthTexture();
        for (int i = 0; i < colorTextureIds.length; i++) {
            colorTextureIds[i] = allocateColorTexture(i);
        }

        depthCopyFboId = MinecraftReflectionCompat.glGenFramebuffers();
        MinecraftReflectionCompat.glBindFramebuffer(
                MinecraftReflectionCompat.glFramebuffer(), depthCopyFboId);
        MinecraftReflectionCompat.glFramebufferTexture2D(
                MinecraftReflectionCompat.glFramebuffer(),
                MinecraftReflectionCompat.glDepthAttachment(),
                GL11.GL_TEXTURE_2D, depthSnapshotTextureId, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int copyStatus = MinecraftReflectionCompat.glCheckFramebufferStatus(
                MinecraftReflectionCompat.glFramebuffer());
        if (copyStatus != MinecraftReflectionCompat.fieldInt(
                OpenGlHelper.class,
                GL30.GL_FRAMEBUFFER_COMPLETE, "field_153202_i", "GL_FRAMEBUFFER_COMPLETE")) {
            MainMod.LOGGER.error("Shadow depth-copy framebuffer is not complete! Status: {}", copyStatus);
        }
        MinecraftReflectionCompat.glBindFramebuffer(
                MinecraftReflectionCompat.glFramebuffer(), fboId);
        MinecraftReflectionCompat.glFramebufferTexture2D(
                MinecraftReflectionCompat.glFramebuffer(),
                MinecraftReflectionCompat.glDepthAttachment(),
                GL11.GL_TEXTURE_2D,
                depthTextureId,
                0
        );
        for (int i = 0; i < colorTextureIds.length; i++) {
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    MinecraftReflectionCompat.glFramebuffer(),
                    GL30.GL_COLOR_ATTACHMENT0 + i,
                    GL11.GL_TEXTURE_2D,
                    colorTextureIds[i],
                    0
            );
        }

        setDrawBuffers(Attachment.COLOR);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        int status = MinecraftReflectionCompat.glCheckFramebufferStatus(MinecraftReflectionCompat.glFramebuffer());
        if (status != MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL30.GL_FRAMEBUFFER_COMPLETE, "field_153202_i", "GL_FRAMEBUFFER_COMPLETE")) {
            MainMod.LOGGER.error("ShadowFramebuffer is not complete! Status: {}", status);
        }

        clearAll();
        copyDepthToSnapshot();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), previousFramebuffer);
    }

    private int allocateDepthTexture(int index) {
        int textureId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(textureId);
        applyDepthTextureFilters(index);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL14.GL_TEXTURE_COMPARE_MODE,
                settings.shadowHardwareFiltering() ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        applyDepthTextureSwizzle();
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL14.GL_DEPTH_COMPONENT32,
                resolution,
                resolution,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT,
                (FloatBuffer) null
        );
        return textureId;
    }

    private int allocateRawDepthTexture() {
        int textureId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(textureId);
        applyDepthTextureFilters(0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_DEPTH_TEXTURE_MODE, GL11.GL_LUMINANCE);
        applyDepthTextureSwizzle();
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL14.GL_DEPTH_COMPONENT32,
                resolution,
                resolution,
                0,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT,
                (FloatBuffer) null
        );
        return textureId;
    }

    private void applyDepthTextureSwizzle() {
        if (!GLContext.getCapabilities().GL_ARB_texture_swizzle) {
            return;
        }

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_R, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_G, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_B, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_A, GL11.GL_ONE);
    }

    private int allocateColorTexture(int index) {
        int textureId = GL11.glGenTextures();
        MinecraftReflectionCompat.glStateBindTexture(textureId);
        applyColorTextureFilters(index);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGBA8,
                resolution,
                resolution,
                0,
                GL11.GL_RGBA,
                GL11.GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );
        return textureId;
    }

    private void applyColorTextureFilters(int index) {
        boolean nearest = settings.shadowColorNearest(index);
        boolean mipmap = settings.shadowColorMipmap(index);
        int magFilter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        int minFilter;
        if (mipmap) {
            minFilter = nearest ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
        } else {
            minFilter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
    }

    private void applyDepthTextureFilters(int index) {
        boolean nearest = settings.shadowDepthNearest(index);
        boolean mipmap = settings.shadowDepthMipmap(index);
        int minFilter;
        int magFilter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        if (mipmap) {
            minFilter = nearest ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
        } else {
            minFilter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        }
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
    }

    public void clear() {
        // Iris always clears shadow depth before rendering shadows; shadowcolor
        // uses the pack's shadowcolor*Clear directive separately.
        boolean[] clearColors = new boolean[colorTextureIds.length];
        for (int i = 0; i < clearColors.length; i++) {
            clearColors[i] = settings.shadowColorClear(i);
        }
        clear(clearColors, true);
    }

    private void clearAll() {
        boolean[] clearColors = new boolean[colorTextureIds.length];
        Arrays.fill(clearColors, true);
        clear(clearColors, true);
    }

    private void clear(boolean[] clearColors, boolean clearDepth) {
        SavedFramebufferState previous = saveFramebufferState();
        bindForRendering(clearColors);
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179082_a", "clearColor"},
                new Class<?>[]{float.class, float.class, float.class, float.class},
                1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        MinecraftReflectionCompat.glStateClearDepth(1.0);
        int clearMask = 0;
        if (hasAnyClearColor(clearColors)) {
            clearMask |= GL11.GL_COLOR_BUFFER_BIT;
        }
        if (clearDepth) {
            clearMask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        if (clearMask != 0) {
            GL11.glClear(clearMask);
        }
        previous.restore();
    }

    public void bindForRendering() {
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), fboId);
        GL11.glViewport(0, 0, resolution, resolution);
        setDrawBuffers(0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    public int framebufferId() {
        return fboId;
    }

    private void bindForRendering(boolean[] writeColors) {
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), fboId);
        GL11.glViewport(0, 0, resolution, resolution);
        setDrawBuffers(writeColors);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    public void bindForProgramWrite(Attachment... drawTargets) {
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), fboId);
        GL11.glViewport(0, 0, resolution, resolution);
        setDrawBuffers(drawTargets);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    private void setDrawBuffers(boolean[] writeColors) {
        drawBufferList.clear();
        if (writeColors != null) {
            for (int i = 0; i < Math.min(writeColors.length, colorTextureIds.length); i++) {
                if (writeColors[i]) {
                    drawBufferList.put(GL30.GL_COLOR_ATTACHMENT0 + i);
                }
            }
        }
        uploadDrawBuffers();
    }

    private void setDrawBuffers(int... colorIndices) {
        drawBufferList.clear();
        for (int colorIndex : colorIndices) {
            if (colorIndex < 0 || colorIndex >= colorTextureIds.length) {
                continue;
            }
            drawBufferList.put(GL30.GL_COLOR_ATTACHMENT0 + colorIndex);
        }
        uploadDrawBuffers();
    }

    private void setDrawBuffers(Attachment... drawTargets) {
        drawBufferList.clear();
        for (Attachment attachment : drawTargets) {
            if (attachment == null || attachment.getIndex() < 0 || attachment.getIndex() >= colorTextureIds.length) {
                continue;
            }
            drawBufferList.put(GL30.GL_COLOR_ATTACHMENT0 + attachment.getIndex());
        }
        uploadDrawBuffers();
    }

    private void uploadDrawBuffers() {
        drawBufferList.flip();
        if (drawBufferList.hasRemaining()) {
            GL20.glDrawBuffers(drawBufferList);
            return;
        }
        GL11.glDrawBuffer(GL11.GL_NONE);
    }

    private static boolean hasAnyClearColor(boolean[] clearColors) {
        if (clearColors == null) {
            return false;
        }
        for (boolean clearColor : clearColors) {
            if (clearColor) {
                return true;
            }
        }
        return false;
    }

    public void copyDepthToSnapshot() {
        SavedFramebufferState previous = saveFramebufferState();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        blitDepthToTexture(depthSnapshotTextureId);
        blitDepthToTexture(rawDepthTextureId);
        if (depthCopyProbeCount < 0) {
            depthCopyProbeCount++;
            DepthStats source = readDepthStats(2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthSnapshotTextureId);
            String snapshot = textureDepthSummary();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, rawDepthTextureId);
            String raw = textureDepthSummary();
            MainMod.LOGGER.info(
                    "[AUSMShadowDepthCopyProbe] call={} fbo={} depthTex={} snapshotTex={} rawTex={} source={} snapshot={} raw={} drawFbo={} readFbo={} readBuffer={} glError={}",
                    depthCopyProbeCount, fboId, depthTextureId, depthSnapshotTextureId, rawDepthTextureId,
                    source.nonClear() + "/" + source.total(), snapshot, raw,
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL11.GL_READ_BUFFER), GL11.glGetError());
        }
        generateDepthMipmap(0, depthTextureId);
        generateDepthMipmap(1, depthSnapshotTextureId);
        generateDepthMipmap(0, rawDepthTextureId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        previous.restore();
    }

    private void blitDepthToTexture(int targetTexture) {
        if (depthCopyFboId == -1 || targetTexture == -1) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            MinecraftReflectionCompat.glBindFramebuffer(
                    MinecraftReflectionCompat.glFramebuffer(), fboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    MinecraftReflectionCompat.glFramebuffer(),
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D, depthTextureId, 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, depthCopyFboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    GL30.GL_DRAW_FRAMEBUFFER,
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D, targetTexture, 0);
            GL11.glReadBuffer(GL11.GL_NONE);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL30.glBlitFramebuffer(
                    0, 0, resolution, resolution,
                    0, 0, resolution, resolution,
                    GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            MinecraftReflectionCompat.glBindFramebuffer(
                    MinecraftReflectionCompat.glFramebuffer(), fboId);
            MinecraftReflectionCompat.glFramebufferTexture2D(
                    MinecraftReflectionCompat.glFramebuffer(),
                    MinecraftReflectionCompat.glDepthAttachment(),
                    GL11.GL_TEXTURE_2D, depthTextureId, 0);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11.glReadBuffer(previousReadBuffer);
            GL11.glDrawBuffer(previousDrawBuffer);
        }
    }

    private String textureDepthSummary() {
        int pixelCount = resolution * resolution;
        FloatBuffer values = BufferUtils.createFloatBuffer(pixelCount);
        try {
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, values);
            int stride = Math.max(1, pixelCount / 1024);
            float min = 1.0f;
            float max = 0.0f;
            int nonClear = 0;
            for (int i = 0; i < pixelCount; i += stride) {
                float value = values.get(i);
                min = Math.min(min, value);
                max = Math.max(max, value);
                if (value < 0.9999f) {
                    nonClear++;
                }
            }
            return nonClear + "/" + ((pixelCount + stride - 1) / stride)
                    + ",min=" + min + ",max=" + max;
        } catch (RuntimeException | LinkageError failure) {
            return "error=" + failure.getClass().getSimpleName();
        }
    }

    public void configureDepthTextureCompareMode() {
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        configureDepthTextureCompareMode(depthTextureId);
        configureDepthTextureCompareMode(depthSnapshotTextureId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
    }

    private void configureDepthTextureCompareMode(int textureId) {
        if (textureId == -1) {
            return;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(
                GL11.GL_TEXTURE_2D,
                GL14.GL_TEXTURE_COMPARE_MODE,
                settings.shadowHardwareFiltering() ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE
        );
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
    }

    public void generateShadowColorMipmaps() {
        SavedFramebufferState previous = saveFramebufferState();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        for (int i = 0; i < colorTextureIds.length; i++) {
            if (!settings.shadowColorMipmap(i) || colorTextureIds[i] == -1) {
                continue;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureIds[i]);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        previous.restore();
    }

    public DepthStats readDepthStats(int samplesPerAxis) {
        int samples = Math.max(1, samplesPerAxis);
        SavedFramebufferState previous = saveFramebufferState();
        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), fboId);

        float center = readDepthAt(resolution / 2, resolution / 2);
        float min = center;
        float max = center;
        int nonClear = center < 0.9999f ? 1 : 0;
        int total = samples * samples + 1;

        for (int y = 0; y < samples; y++) {
            int pixelY = samples == 1 ? resolution / 2 : Math.round((resolution - 1) * (y / (float) (samples - 1)));
            for (int x = 0; x < samples; x++) {
                int pixelX = samples == 1 ? resolution / 2 : Math.round((resolution - 1) * (x / (float) (samples - 1)));
                float depth = readDepthAt(pixelX, pixelY);
                min = Math.min(min, depth);
                max = Math.max(max, depth);
                if (depth < 0.9999f) {
                    nonClear++;
                }
            }
        }

        previous.restore();
        return new DepthStats(center, min, max, nonClear, total);
    }

    private float readDepthAt(int x, int y) {
        depthReadBuffer.clear();
        GL11.glReadPixels(
                Math.clamp(x, 0, resolution - 1),
                Math.clamp(y, 0, resolution - 1),
                1,
                1,
                GL11.GL_DEPTH_COMPONENT,
                GL11.GL_FLOAT,
                depthReadBuffer
        );
        return depthReadBuffer.get(0);
    }

    private void generateDepthMipmap(int index, int textureId) {
        if (!settings.shadowDepthMipmap(index)) {
            return;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
    }

    private SavedFramebufferState saveFramebufferState() {
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);
        colorMaskBuffer.clear();
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer);
        clearColorBuffer.clear();
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColorBuffer);
        return new SavedFramebufferState(
                GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER),
                GL11.glGetInteger(GL11.GL_READ_BUFFER),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                colorMaskBuffer.get(0) != 0,
                colorMaskBuffer.get(1) != 0,
                colorMaskBuffer.get(2) != 0,
                colorMaskBuffer.get(3) != 0,
                viewportBuffer.get(0),
                viewportBuffer.get(1),
                viewportBuffer.get(2),
                viewportBuffer.get(3),
                clearColorBuffer.get(0),
                clearColorBuffer.get(1),
                clearColorBuffer.get(2),
                clearColorBuffer.get(3)
        );
    }

    private record SavedFramebufferState(
            int framebuffer,
            int drawBuffer,
            int readBuffer,
            boolean depthMask,
            boolean redMask,
            boolean greenMask,
            boolean blueMask,
            boolean alphaMask,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight,
            float clearRed,
            float clearGreen,
            float clearBlue,
            float clearAlpha
    ) {
        private void restore() {
            MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), framebuffer);
            GL11.glDrawBuffer(drawBuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glDepthMask(depthMask);
            GL11.glColorMask(redMask, greenMask, blueMask, alphaMask);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_179082_a", "clearColor"},
                    new Class<?>[]{float.class, float.class, float.class, float.class},
                    clearRed, clearGreen, clearBlue, clearAlpha);
        }
    }

    public int depthTextureId() {
        return depthTextureId;
    }

    public int depthSnapshotTextureId() {
        return depthSnapshotTextureId;
    }

    public int rawDepthTextureId() {
        return rawDepthTextureId;
    }

    public int colorTextureId() {
        return colorTextureId(0);
    }

    public int colorTextureId(int index) {
        return index >= 0 && index < colorTextureIds.length ? colorTextureIds[index] : -1;
    }

    public int resolution() {
        return resolution;
    }

    public record DepthStats(float center, float min, float max, int nonClear, int total) {
    }

    public void delete() {
        if (depthCopyFboId != -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(depthCopyFboId);
            depthCopyFboId = -1;
        }
        if (fboId != -1) {
            MinecraftReflectionCompat.glDeleteFramebuffers(fboId);
            fboId = -1;
        }
        if (depthTextureId != -1) {
            GL11.glDeleteTextures(depthTextureId);
            depthTextureId = -1;
        }
        if (depthSnapshotTextureId != -1) {
            GL11.glDeleteTextures(depthSnapshotTextureId);
            depthSnapshotTextureId = -1;
        }
        if (rawDepthTextureId != -1) {
            GL11.glDeleteTextures(rawDepthTextureId);
            rawDepthTextureId = -1;
        }
        for (int i = 0; i < colorTextureIds.length; i++) {
            if (colorTextureIds[i] != -1) {
                GL11.glDeleteTextures(colorTextureIds[i]);
                colorTextureIds[i] = -1;
            }
        }
    }
}
