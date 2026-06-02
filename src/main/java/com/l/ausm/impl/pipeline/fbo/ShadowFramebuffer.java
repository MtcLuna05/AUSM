package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Minimal shadow depth target.
 *
 * This first rebuild intentionally exposes valid OptiFine shadow textures
 * without rendering terrain into them yet. Both textures are cleared to depth
 * 1.0, which means "fully lit" for packs that sample shadowtex0/1.
 */
public final class ShadowFramebuffer {
    private int fboId = -1;
    private int depthTextureId = -1;
    private int depthSnapshotTextureId = -1;
    private int colorTextureId = -1;
    private final int resolution;
    private final ShaderRenderTargetSettings settings;
    private final IntBuffer viewportBuffer = org.lwjgl.BufferUtils.createIntBuffer(16);
    private final ByteBuffer colorMaskBuffer = org.lwjgl.BufferUtils.createByteBuffer(4);

    public ShadowFramebuffer(int resolution, ShaderRenderTargetSettings settings) {
        this.resolution = resolution;
        this.settings = settings;
        create();
    }

    private void create() {
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        fboId = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fboId);

        depthTextureId = allocateDepthTexture(0);
        depthSnapshotTextureId = allocateDepthTexture(1);
        colorTextureId = allocateColorTexture();
        OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                OpenGlHelper.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D,
                depthTextureId,
                0
        );
        OpenGlHelper.glFramebufferTexture2D(
                OpenGlHelper.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D,
                colorTextureId,
                0
        );

        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            MainMod.LOGGER.error("ShadowFramebuffer is not complete! Status: {}", status);
        }

        clearAll();
        copyDepthToSnapshot();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFramebuffer);
    }

    private int allocateDepthTexture(int index) {
        int textureId = GL11.glGenTextures();
        GlStateManager.bindTexture(textureId);
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

    private void applyDepthTextureSwizzle() {
        if (!GLContext.getCapabilities().GL_ARB_texture_swizzle) {
            return;
        }

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_R, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_G, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_B, GL11.GL_RED);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_A, GL11.GL_ONE);
    }

    private int allocateColorTexture() {
        int textureId = GL11.glGenTextures();
        GlStateManager.bindTexture(textureId);
        applyColorTextureFilters(0);
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
    }

    public void clear() {
        // Iris always clears shadow depth before rendering shadows; shadowcolor
        // uses the pack's shadowcolor*Clear directive separately.
        clear(settings.shadowColorClear(0), true);
    }

    private void clearAll() {
        clear(true, true);
    }

    private void clear(boolean clearColor, boolean clearDepth) {
        SavedFramebufferState previous = saveFramebufferState();
        bindForRendering();
        GL11.glColorMask(true, true, true, true);
        GlStateManager.clearColor(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GlStateManager.clearDepth(1.0);
        int clearMask = 0;
        if (clearColor) {
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
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fboId);
        GL11.glViewport(0, 0, resolution, resolution);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    public void copyDepthToSnapshot() {
        SavedFramebufferState previous = saveFramebufferState();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fboId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthSnapshotTextureId);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, resolution, resolution);
        generateDepthMipmap(0, depthTextureId);
        generateDepthMipmap(1, depthSnapshotTextureId);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        previous.restore();
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
        if (!settings.shadowColorMipmap(0)) {
            return;
        }
        SavedFramebufferState previous = saveFramebufferState();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        previous.restore();
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
                viewportBuffer.get(3)
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
            int viewportHeight
    ) {
        private void restore() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glDrawBuffer(drawBuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glDepthMask(depthMask);
            GL11.glColorMask(redMask, greenMask, blueMask, alphaMask);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }
    }

    public int depthTextureId() {
        return depthTextureId;
    }

    public int depthSnapshotTextureId() {
        return depthSnapshotTextureId;
    }

    public int colorTextureId() {
        return colorTextureId;
    }

    public int resolution() {
        return resolution;
    }

    public void delete() {
        if (fboId != -1) {
            OpenGlHelper.glDeleteFramebuffers(fboId);
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
        if (colorTextureId != -1) {
            GL11.glDeleteTextures(colorTextureId);
            colorTextureId = -1;
        }
    }
}
