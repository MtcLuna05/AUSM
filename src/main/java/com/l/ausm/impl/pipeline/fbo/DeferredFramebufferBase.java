package com.l.ausm.impl.pipeline.fbo;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.fbo.ColorBufferFormat;
import com.l.ausm.api.pipeline.pack.ShaderTextureScale;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.BufferUtils;

abstract class DeferredFramebufferBase {
    public static final int DEPTHTEX1_SNAPSHOT = 0;

    public static final int DEPTHTEX2_SNAPSHOT = 1;

    protected static final int DEPTH_SNAPSHOT_COUNT = 2;

    protected static final int COLOR_ATTACHMENT_SLOTS = 8;

    protected static final int READ_TEXTURE_INDEX = 0;

    protected static final int WRITE_TEXTURE_INDEX = 1;

    protected static final int MAX_FRAMEBUFFER_STATUS_LOGS = 16;

    protected static final int UNKNOWN_ATTACHMENT_TEXTURE = Integer.MIN_VALUE;

    protected static int maxDrawBufferSlots = -1;

    protected static int framebufferStatusLogs;

    protected int fboId = -1;

    protected int fullscreenFboId = -1;

    protected int readFboId = -1;

    protected int depthCopyFboId = -1;

    protected int depthTextureId = -1;

    protected final int[] depthSnapshotTextureIds = {-1, -1};

    protected int recoveryColorTextureId = -1;

    protected int recoveryColorWidth;

    protected int recoveryColorHeight;

    protected boolean recoveryColorValid;

    protected int width;

    protected int height;

    // Iris keeps a main/alt texture pair per color attachment and flips only
    // attachments written by the current fullscreen program.
    protected final Map<Attachment, int[]> colorTextures = new EnumMap<>(Attachment.class);

    protected final Map<Attachment, Integer> colorWidths = new EnumMap<>(Attachment.class);

    protected final Map<Attachment, Integer> colorHeights = new EnumMap<>(Attachment.class);

    protected final Map<Attachment, Boolean> flippedTextures = new EnumMap<>(Attachment.class);

    // The GL_COLOR_ATTACHMENTx constants for currently active draw buffers
    protected IntBuffer drawBuffers;

    protected Map<Attachment, ColorBufferFormat> formats;

    protected Map<Attachment, ShaderTextureScale> textureScales;

    protected Map<Attachment, float[]> clearColors;

    protected final FloatBuffer depthReadBuffer = BufferUtils.createFloatBuffer(1);

    protected final FloatBuffer colorReadBuffer = BufferUtils.createFloatBuffer(4);

    protected final FloatBuffer clearColorBuffer = BufferUtils.createFloatBuffer(4);

    protected final Map<Integer, int[]> attachedColorTexturesByFramebuffer = new HashMap<>();

    protected final Map<Integer, Integer> attachedDepthTexturesByFramebuffer = new HashMap<>();

    protected final Map<Integer, int[]> drawBuffersByFramebuffer = new HashMap<>();

    protected int currentFramebufferId = -1;

    protected boolean usable = true;

    protected final DeferredFramebuffer self() {
        return (DeferredFramebuffer) this;
    }
}
