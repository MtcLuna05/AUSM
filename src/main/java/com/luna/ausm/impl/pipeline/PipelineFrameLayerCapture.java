package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Captures one shadered frame at the boundaries needed to locate a bad layer
 * or fullscreen pass. All OpenGL reads run on the client render thread.
 */
public final class PipelineFrameLayerCapture {
    private static final DateTimeFormatter DIRECTORY_TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss_SSS");
    private static final ExecutorService IMAGE_WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AUSM frame-layer image writer");
        thread.setDaemon(true);
        return thread;
    });

    private static boolean captureRequested;
    private static CaptureSession activeCapture;
    private static int previousWindowTexture;
    private static int previousWindowFramebuffer;
    private static int previousWindowWidth;
    private static int previousWindowHeight;
    private static boolean previousWindowValid;
    private static int previousPipelineTexture;
    private static int previousPipelineFramebuffer;
    private static int previousPipelineWidth;
    private static int previousPipelineHeight;
    private static boolean previousPipelineValid;
    private static int previousPreBloomTexture;
    private static int previousPreBloomFramebuffer;
    private static int previousPreBloomWidth;
    private static int previousPreBloomHeight;
    private static boolean previousPreBloomValid;
    private static final int[] previousFinalInputTextures = new int[Attachment.values().length];
    private static final int[] previousFinalInputWidths = new int[Attachment.values().length];
    private static final int[] previousFinalInputHeights = new int[Attachment.values().length];
    private static final boolean[] previousFinalInputValid = new boolean[Attachment.values().length];
    private static int previousFinalInputReadFramebuffer;
    private static int previousFinalInputDrawFramebuffer;
    private static final int PREVIOUS_GBUFFER_HISTORY = 4;
    private static final int[][] previousGbufferTextures = new int[PREVIOUS_GBUFFER_HISTORY][2];
    private static final int[][] previousGbufferWidths = new int[PREVIOUS_GBUFFER_HISTORY][2];
    private static final int[][] previousGbufferHeights = new int[PREVIOUS_GBUFFER_HISTORY][2];
    private static final boolean[][] previousGbufferValid = new boolean[PREVIOUS_GBUFFER_HISTORY][2];
    private static int previousGbufferReadFramebuffer;
    private static int previousGbufferDrawFramebuffer;
    private static long previousGbufferFrame = Long.MIN_VALUE;
    private static final int MAX_COMPOSITE_HISTORY = 16;
    private static final int[][] compositeHistoryTextures = new int[2][MAX_COMPOSITE_HISTORY];
    private static final int[][] compositeHistoryWidths = new int[2][MAX_COMPOSITE_HISTORY];
    private static final int[][] compositeHistoryHeights = new int[2][MAX_COMPOSITE_HISTORY];
    private static final String[] compositeHistoryPassNames = new String[MAX_COMPOSITE_HISTORY];
    private static int compositeHistoryCount;
    private static long compositeHistoryFrame = Long.MIN_VALUE;
    private static int compositeHistoryReadFramebuffer;
    private static int compositeHistoryDrawFramebuffer;
    private static final int TEMPORAL_PRESENTATION_HISTORY = 12;
    private static final int[] temporalPipelineTextures = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final int[] temporalPipelineWidths = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final int[] temporalPipelineHeights = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final boolean[] temporalPipelineValid = new boolean[TEMPORAL_PRESENTATION_HISTORY];
    private static final long[] temporalPipelineFrames = new long[TEMPORAL_PRESENTATION_HISTORY];
    private static int temporalPipelineNextSlot;
    private static long lastTemporalPipelineFrame = Long.MIN_VALUE;
    private static final int[] temporalExternalTextures = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final int[] temporalExternalWidths = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final int[] temporalExternalHeights = new int[TEMPORAL_PRESENTATION_HISTORY];
    private static final boolean[] temporalExternalValid = new boolean[TEMPORAL_PRESENTATION_HISTORY];
    private static final long[] temporalExternalFrames = new long[TEMPORAL_PRESENTATION_HISTORY];
    private static int temporalExternalNextSlot;
    private static long temporalExternalSequence;
    private static int temporalCopyDrawFramebuffer;

    private PipelineFrameLayerCapture() {
    }

    public static synchronized boolean requestNextFrame() {
        if (captureRequested) {
            return false;
        }
        captureRequested = true;
        return true;
    }

    /** Dumps block types intersecting the current render frustum on the next world frame. */
    static void beginWorldIfRequested(long frameId) {
        synchronized (PipelineFrameLayerCapture.class) {
            if (!captureRequested) {
                return;
            }
            captureRequested = false;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : null;
        RenderGlobal renderGlobal = minecraft != null ? MinecraftReflectionCompat.renderGlobal(minecraft) : null;
        if (world == null || !(renderGlobal instanceof RenderGlobalAccessor accessor)) {
            MainMod.LOGGER.warn("[AUSMVisibleBlocks] F7 dump skipped: world or render frustum unavailable");
            return;
        }
        ViewFrustum viewFrustum = accessor.ausm$viewFrustum();
        RenderChunk[] renderChunks = null;
        for (String fieldName : new String[]{"field_178164_f", "renderChunks"}) {
            try {
                Field field = ViewFrustum.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(viewFrustum);
                if (value instanceof RenderChunk[] chunks) {
                    renderChunks = chunks;
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (renderChunks == null) {
            MainMod.LOGGER.warn("[AUSMVisibleBlocks] F7 dump skipped: no render chunks available");
            return;
        }

        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) {
            MainMod.LOGGER.warn("[AUSMVisibleBlocks] F7 dump skipped: camera unavailable");
            return;
        }
        double cameraX = camera.posX;
        double cameraY = camera.posY;
        double cameraZ = camera.posZ;
        ClippingHelper frustum = ClippingHelperImpl.getInstance();
        Map<String, Integer> blockCounts = new TreeMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int frustumChunks = 0;
        int frustumVoxels = 0;
        int nonAirVoxels = 0;
        for (RenderChunk renderChunk : renderChunks) {
            Object position = MinecraftReflectionCompat.invoke(renderChunk,
                    new String[]{"func_178568_j", "getPosition"}, MinecraftReflectionCompat.NO_PARAMETERS);
            BlockPos origin = position instanceof BlockPos blockPos ? blockPos : null;
            if (origin == null || !MinecraftReflectionCompat.worldIsBlockLoaded(world, origin, false)) {
                continue;
            }
            int minX = origin.getX();
            int minY = origin.getY();
            int minZ = origin.getZ();
            int maxX = minX + 16;
            int maxY = minY + 16;
            int maxZ = minZ + 16;
            if (!frustum.isBoxInFrustum(minX - cameraX, minY - cameraY, minZ - cameraZ,
                    maxX - cameraX, maxY - cameraY, maxZ - cameraZ)) {
                continue;
            }
            frustumChunks++;
            for (int y = Math.max(0, minY); y < Math.min(256, maxY); y++) {
                for (int z = minZ; z < maxZ; z++) {
                    for (int x = minX; x < maxX; x++) {
                        if (!frustum.isBoxInFrustum(x - cameraX, y - cameraY, z - cameraZ,
                                x + 1.0D - cameraX, y + 1.0D - cameraY, z + 1.0D - cameraZ)) {
                            continue;
                        }
                        frustumVoxels++;
                        MinecraftReflectionCompat.mutableBlockPosSet(pos, x, y, z);
                        IBlockState state = MinecraftReflectionCompat.worldBlockState(world, pos);
                        Block block = state != null ? state.getBlock() : Blocks.AIR;
                        if (block == Blocks.AIR) {
                            continue;
                        }
                        nonAirVoxels++;
                        String blockId = String.valueOf(Block.REGISTRY.getNameForObject(block));
                        blockCounts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
        }
        Path directory = MinecraftReflectionCompat.gameDir(minecraft).toPath().resolve("screenshots")
                .resolve("ausm-visible-blocks");
        Path dump = directory.resolve(DIRECTORY_TIMESTAMP.format(LocalDateTime.now()) + "_frame-" + frameId + ".txt");
        StringBuilder contents = new StringBuilder("frame=").append(frameId)
                .append(System.lineSeparator()).append("frustumRenderChunks=").append(frustumChunks)
                .append(System.lineSeparator()).append("frustumVoxels=").append(frustumVoxels)
                .append(System.lineSeparator()).append("nonAirVoxels=").append(nonAirVoxels)
                .append(System.lineSeparator()).append("uniqueBlockTypes=").append(blockCounts.size())
                .append(System.lineSeparator()).append(System.lineSeparator()).append("blockType\tcount").append(System.lineSeparator());
        for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
            contents.append(entry.getKey()).append('\t').append(entry.getValue()).append(System.lineSeparator());
        }
        try {
            Files.createDirectories(directory);
            Files.writeString(dump, contents);
            MainMod.LOGGER.info("[AUSMVisibleBlocks] F7 dumped {} block types from {} frustum chunks into {}",
                    blockCounts.size(), frustumChunks, dump);
        } catch (IOException error) {
            MainMod.LOGGER.error("[AUSMVisibleBlocks] Could not write F7 block dump {}", dump, error);
        }
    }

    static void beginIfRequested(long frameId, DeferredFramebuffer framebuffer) {
        beginWorldIfRequested(frameId);
        boolean captureInitialGbuffer = activeCapture != null && !activeCapture.initialGbufferCaptured;

        if (!captureInitialGbuffer) {
            return;
        }
        captureAllGbufferLayers(activeCapture, framebuffer);
        captureDepthLayers(activeCapture, framebuffer, "layers-initial-gbuffer");
        activeCapture.initialGbufferCaptured = true;
    }

    /** Captures a distinct framebuffer boundary without changing its contents. */
    static void capturePresentationBoundary(long frameId, String label, Framebuffer framebuffer) {
        CaptureSession session = sessionFor(frameId);
        if (session == null || framebuffer == null) {
            return;
        }
        captureTexture(
                MinecraftReflectionCompat.framebufferTexture(framebuffer),
                MinecraftReflectionCompat.framebufferWidth(framebuffer),
                MinecraftReflectionCompat.framebufferHeight(framebuffer),
                session.directory.resolve(label + ".png")
        );
    }

    /** Captures a distinct deferred-color boundary without a fullscreen pass. */
    static void captureDeferredBoundary(long frameId, String label, DeferredFramebuffer framebuffer) {
        CaptureSession session = sessionFor(frameId);
        if (session == null || framebuffer == null || !framebuffer.isUsable()) {
            return;
        }
        captureColortex0State(session, label, framebuffer.getReadTexture(Attachment.COLOR),
                framebuffer.getAttachmentWidth(Attachment.COLOR), framebuffer.getAttachmentHeight(Attachment.COLOR));
        captureTexture(framebuffer.getReadTexture(Attachment.AUX3), framebuffer.getAttachmentWidth(Attachment.AUX3),
                framebuffer.getAttachmentHeight(Attachment.AUX3), session.directory.resolve(label + "-colortex3.png"));
        recordForensics(session, label + "-colortex0", framebuffer.getReadTexture(Attachment.COLOR),
                framebuffer.getAttachmentWidth(Attachment.COLOR), framebuffer.getAttachmentHeight(Attachment.COLOR));
        recordForensics(session, label + "-colortex3", framebuffer.getReadTexture(Attachment.AUX3),
                framebuffer.getAttachmentWidth(Attachment.AUX3), framebuffer.getAttachmentHeight(Attachment.AUX3));
    }

    /** Captures the evolving colortex0 after a completed world G-buffer phase. */
    static void captureGbufferLayerOutput(long frameId, RenderPass pass, WorldRenderingPhase phase,
                                          DeferredFramebuffer framebuffer) {
        if (pass == null || pass.stage() != ProgramStage.GBUFFERS || phase == null || phase == WorldRenderingPhase.NONE
                || framebuffer == null || !framebuffer.isUsable()) {
            return;
        }
        // Input can be processed after beginFrame but before the first phase
        // completes; accept that request instead of waiting for composites.
        beginWorldIfRequested(frameId);
        mirrorGbufferPreCompositeOutputs(frameId, framebuffer);
        CaptureSession session = sessionFor(frameId);
        if (session == null) {
            return;
        }

        session.gbufferLayers++;
        if (session.gbufferLayers == 1) {
            captureDeferredBoundary(frameId, "92-after-first-gbuffer-phase", framebuffer);
        }
        String prefix = String.format("layers-gbuffer-%02d-after-%s", session.gbufferLayers,
                sanitizeFileComponent(phase.name().toLowerCase()));
        captureColortex0State(session, "gbuffer-" + session.gbufferLayers + "-" + phase.name().toLowerCase(),
                framebuffer.getReadTexture(Attachment.COLOR), framebuffer.getAttachmentWidth(Attachment.COLOR),
                framebuffer.getAttachmentHeight(Attachment.COLOR));
        captureDepthTexture(
                framebuffer.getDepthTexture(),
                framebuffer.getWidth(),
                framebuffer.getHeight(),
                session.directory.resolve(prefix + "-depthtex0.png")
        );
    }

    /** Starts an F7 capture at the shaderless sky's live framebuffer. */
    public static void beginShaderlessSkyIfRequested() {
        synchronized (PipelineFrameLayerCapture.class) {
            if (!captureRequested || activeCapture != null) {
                return;
            }
            captureRequested = false;
            activeCapture = createSession(System.nanoTime());
        }
        captureShaderlessSkyBoundary("00-sky-before-renderers");
    }

    /** Captures a shaderless-sky framebuffer boundary without changing GL state. */
    public static void captureShaderlessSkyBoundary(String boundary) {
        CaptureSession session;
        synchronized (PipelineFrameLayerCapture.class) {
            session = activeCapture;
        }
        if (session == null) {
            return;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Framebuffer target = MinecraftReflectionCompat.minecraftFramebuffer(minecraft);
        if (target == null) {
            return;
        }
        captureTexture(
                MinecraftReflectionCompat.framebufferTexture(target),
                MinecraftReflectionCompat.framebufferWidth(target),
                MinecraftReflectionCompat.framebufferHeight(target),
                session.directory.resolve(sanitizeFileComponent(boundary) + ".png")
        );
    }

    /** Captures the final shaderless-sky boundary and completes the F7 session. */
    public static void finishShaderlessSkyCapture(String boundary) {
        CaptureSession session;
        synchronized (PipelineFrameLayerCapture.class) {
            session = activeCapture;
        }
        if (session == null) {
            return;
        }
        captureShaderlessSkyBoundary(boundary);
        finish(session);
    }

    static void captureCompositeOutputs(long frameId, String passName, List<Attachment> drawBuffers,
                                        DeferredFramebuffer framebuffer) {
        mirrorCompositeHistory(frameId, passName, framebuffer);
        CaptureSession session = sessionFor(frameId);
        if (session == null || framebuffer == null || !framebuffer.isUsable()) {
            return;
        }
        session.compositePasses++;
        String passPrefix = String.format("layers-composite-%02d-after-%s", session.compositePasses,
                sanitizeFileComponent(passName));
        for (Attachment attachment : Attachment.values()) {
            if (attachment == Attachment.COLOR) {
                captureColortex0State(session, "composite-" + session.compositePasses + "-" + passName,
                        framebuffer.getReadTexture(attachment), framebuffer.getAttachmentWidth(attachment),
                        framebuffer.getAttachmentHeight(attachment));
                continue;
            }
            captureTexture(
                    framebuffer.getReadTexture(attachment),
                    framebuffer.getAttachmentWidth(attachment),
                    framebuffer.getAttachmentHeight(attachment),
                    session.directory.resolve(passPrefix + "-colortex" + attachment.getIndex() + ".png")
            );
        }
        activeCapture.initialGbufferCaptured = true;
        captureDepthLayers(session, framebuffer, passPrefix);
        recordForensics(session, "composite-" + session.compositePasses + "-" + passName + "-colortex0",
                framebuffer.getReadTexture(Attachment.COLOR), framebuffer.getAttachmentWidth(Attachment.COLOR),
                framebuffer.getAttachmentHeight(Attachment.COLOR));
        recordForensics(session, "composite-" + session.compositePasses + "-" + passName + "-colortex3",
                framebuffer.getReadTexture(Attachment.AUX3), framebuffer.getAttachmentWidth(Attachment.AUX3),
                framebuffer.getAttachmentHeight(Attachment.AUX3));
    }

    private static void mirrorCompositeHistory(long frameId, String passName, DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) return;
        if (compositeHistoryFrame != frameId) { compositeHistoryFrame = frameId; compositeHistoryCount = 0; }
        if (compositeHistoryCount >= MAX_COMPOSITE_HISTORY) return;
        int slot = compositeHistoryCount++;
        compositeHistoryPassNames[slot] = sanitizeFileComponent(passName);
        mirrorCompositeAttachment(framebuffer, Attachment.COLOR, 0, slot);
        mirrorCompositeAttachment(framebuffer, Attachment.AUX3, 1, slot);
    }

    private static void mirrorCompositeAttachment(DeferredFramebuffer framebuffer, Attachment attachment, int channel, int slot) {
        int texture = framebuffer.getReadTexture(attachment), width = framebuffer.getAttachmentWidth(attachment), height = framebuffer.getAttachmentHeight(attachment);
        if (texture <= 0 || width <= 0 || height <= 0) return;
        if (compositeHistoryReadFramebuffer <= 0) compositeHistoryReadFramebuffer = GL30.glGenFramebuffers();
        if (compositeHistoryDrawFramebuffer <= 0) compositeHistoryDrawFramebuffer = GL30.glGenFramebuffers();
        if (compositeHistoryTextures[channel][slot] <= 0) compositeHistoryTextures[channel][slot] = GL11.glGenTextures();
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, compositeHistoryTextures[channel][slot]);
            if (compositeHistoryWidths[channel][slot] != width || compositeHistoryHeights[channel][slot] != height) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                compositeHistoryWidths[channel][slot] = width;
                compositeHistoryHeights[channel][slot] = height;
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, compositeHistoryReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, compositeHistoryDrawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, compositeHistoryTextures[channel][slot], 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void captureCompositeHistory(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null) return;
        for (int slot = 0; slot < compositeHistoryCount; slot++) for (int channel = 0; channel < 2; channel++)
            captureTexture(compositeHistoryTextures[channel][slot], compositeHistoryWidths[channel][slot], compositeHistoryHeights[channel][slot],
                    session.directory.resolve(String.format("history-composite-%02d-after-%s-colortex%d.png", slot + 1,
                            compositeHistoryPassNames[slot], channel == 0 ? 0 : 3)));
    }

    /**
     * Keeps the latest completed G-buffer state from the previous frame. It is
     * intentionally captured before any composite program can write it, which
     * lets F7 inspect corruption that would otherwise heal during capture.
     */
    private static void mirrorGbufferPreCompositeOutputs(long frameId, DeferredFramebuffer framebuffer) {
        if (previousGbufferFrame != frameId) {
            advancePreviousGbufferHistory(frameId);
        }
        mirrorPreviousGbufferAttachment(framebuffer, Attachment.COLOR, 0);
        mirrorPreviousGbufferAttachment(framebuffer, Attachment.AUX3, 1);
    }

    /** Moves the completed prior frame to 93 and reuses the oldest snapshot for the new frame. */
    private static void advancePreviousGbufferHistory(long frameId) {
        int[] recycledTextures = previousGbufferTextures[PREVIOUS_GBUFFER_HISTORY - 1];
        int[] recycledWidths = previousGbufferWidths[PREVIOUS_GBUFFER_HISTORY - 1];
        int[] recycledHeights = previousGbufferHeights[PREVIOUS_GBUFFER_HISTORY - 1];
        boolean[] recycledValid = previousGbufferValid[PREVIOUS_GBUFFER_HISTORY - 1];
        for (int history = PREVIOUS_GBUFFER_HISTORY - 1; history > 0; history--) {
            previousGbufferTextures[history] = previousGbufferTextures[history - 1];
            previousGbufferWidths[history] = previousGbufferWidths[history - 1];
            previousGbufferHeights[history] = previousGbufferHeights[history - 1];
            previousGbufferValid[history] = previousGbufferValid[history - 1];
        }
        previousGbufferTextures[0] = recycledTextures;
        previousGbufferWidths[0] = recycledWidths;
        previousGbufferHeights[0] = recycledHeights;
        previousGbufferValid[0] = recycledValid;
        recycledValid[0] = false;
        recycledValid[1] = false;
        previousGbufferFrame = frameId;
    }

    private static void mirrorPreviousGbufferAttachment(DeferredFramebuffer framebuffer, Attachment attachment, int channel) {
        int sourceTexture = framebuffer.getReadTexture(attachment);
        int width = framebuffer.getAttachmentWidth(attachment);
        int height = framebuffer.getAttachmentHeight(attachment);
        if (sourceTexture <= 0 || width <= 0 || height <= 0 || !ensurePreviousGbufferTarget(0, channel, width, height)) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousGbufferReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, sourceTexture, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousGbufferDrawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, previousGbufferTextures[0][channel], 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            previousGbufferValid[0][channel] = true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    private static boolean ensurePreviousGbufferTarget(int history, int channel, int width, int height) {
        if (previousGbufferReadFramebuffer <= 0) previousGbufferReadFramebuffer = GL30.glGenFramebuffers();
        if (previousGbufferDrawFramebuffer <= 0) previousGbufferDrawFramebuffer = GL30.glGenFramebuffers();
        if (previousGbufferTextures[history][channel] <= 0) previousGbufferTextures[history][channel] = GL11.glGenTextures();
        if (previousGbufferReadFramebuffer <= 0 || previousGbufferDrawFramebuffer <= 0 || previousGbufferTextures[history][channel] <= 0) return false;
        if (previousGbufferWidths[history][channel] == width && previousGbufferHeights[history][channel] == height) return true;
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousGbufferTextures[history][channel]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            previousGbufferWidths[history][channel] = width;
            previousGbufferHeights[history][channel] = height;
            previousGbufferValid[history][channel] = false;
            return true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void capturePreviousGbufferOutputs(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null) return;
        for (int history = 0; history < PREVIOUS_GBUFFER_HISTORY; history++) {
            for (int channel = 0; channel < previousGbufferTextures[history].length; channel++) {
                if (previousGbufferValid[history][channel]) {
                    captureTexture(previousGbufferTextures[history][channel], previousGbufferWidths[history][channel], previousGbufferHeights[history][channel],
                            session.directory.resolve("history-gbuffer-" + (history + 1) + "-before-composite-colortex"
                                    + (channel == 0 ? 0 : 3) + ".png"));
                }
            }
        }
    }

    static void captureFinalPresentation(long frameId, Framebuffer target) {
        mirrorPipelinePresentation(target);
        mirrorTemporalPipelinePresentation(frameId, target);
        CaptureSession session = sessionFor(frameId);
        if (session == null) {
            return;
        }
        if (target != null) {
            recordForensics(session, "99-final-presentation", target);
            captureTexture(
                    MinecraftReflectionCompat.framebufferTexture(target),
                    MinecraftReflectionCompat.framebufferWidth(target),
                    MinecraftReflectionCompat.framebufferHeight(target),
                    session.directory.resolve("99-final-presentation-before-window-blit.png")
            );
        }
    }

    /**
     * Mirrors the Minecraft framebuffer immediately before its final upload.
     * This includes HUD and third-party overlays, unlike the earlier pipeline
     * final target snapshot.
     */
    public static void mirrorExternalPresentation(Framebuffer target) {
        if (target == null) {
            return;
        }
        mirrorTemporalPresentation(target, temporalExternalTextures, temporalExternalWidths, temporalExternalHeights,
                temporalExternalValid, temporalExternalFrames, temporalExternalNextSlot, temporalExternalSequence++);
        temporalExternalNextSlot = (temporalExternalNextSlot + 1) % TEMPORAL_PRESENTATION_HISTORY;
        CaptureSession session = activeSession();
        if (session != null) {
            recordForensics(session, "external-before-vanilla-presentation", target);
        }
    }

    /** Records a third-party framebuffer boundary during an active F7 forensic capture. */
    public static void recordExternalFramebufferForensics(String label, Framebuffer framebuffer) {
        CaptureSession session = activeSession();
        if (session != null) {
            recordForensics(session, label, framebuffer);
        }
    }

    /** Mirrors the final target immediately before AUSM applies post-world bloom. */
    static void capturePreBloomPresentation(Framebuffer target) {
        mirrorPresentationTarget(target, PreviousPresentationTarget.PRE_BLOOM);
    }

    static void captureFinalInputs(DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            return;
        }
        for (Attachment attachment : Attachment.values()) {
            mirrorFinalInput(framebuffer, attachment);
        }
        CaptureSession session = activeSession();
        if (session != null && !session.finalInputsCaptured) {
            captureColortex0State(session, "final-input-before-final", framebuffer.getReadTexture(Attachment.COLOR),
                    framebuffer.getAttachmentWidth(Attachment.COLOR), framebuffer.getAttachmentHeight(Attachment.COLOR));
            session.finalInputsCaptured = true;
        }
    }

    /** Captures the window backbuffer after the final framebuffer-to-window blit. */
    static void captureFinalWindowPresentation(long frameId, int width, int height) {
        CaptureSession session = sessionFor(frameId);
        if (session == null) {
            return;
        }
        try {
            if (width > 0 && height > 0) {
                writeColorTextureAsync(readWindowPixels(width, height), width, height,
                        session.directory.resolve("100-window-after-direct-present.png"));
            }
        } catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not capture the final window presentation", e);
        } finally {
            finish(session);
        }
    }

    /** Completes a capture after vanilla uploads the Minecraft framebuffer. */
    public static void captureVanillaWindowPresentation(int width, int height) {
        CaptureSession session = activeSession();
        if (session == null) {
            return;
        }
        try {
            if (width > 0 && height > 0) {
                session.forensicLines.add("100-window-after-vanilla-present uses a window backbuffer; numeric texture readback unavailable");
                writeColorTextureAsync(readWindowPixels(width, height), width, height,
                        session.directory.resolve("100-window-after-vanilla-present.png"));
            }
        } catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not capture the vanilla window presentation", e);
        } finally {
            finish(session);
        }
    }

    /** Mirrors the completed window backbuffer without a CPU readback. */
    static void mirrorWindowPresentation(int width, int height) {
        if (width <= 0 || height <= 0 || !ensurePreviousWindowTarget(width, height)) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousWindowFramebuffer);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            previousWindowValid = true;
        } catch (RuntimeException | LinkageError e) {
            previousWindowValid = false;
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not mirror the window presentation", e);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    private static void capturePreviousWindowPresentation(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null || !previousWindowValid || previousWindowTexture <= 0) {
            return;
        }
        captureTexture(previousWindowTexture, previousWindowWidth, previousWindowHeight,
                session.directory.resolve("history-window-before-f7.png"));

        // The automatic presentation detector compares this frame's preserved
        // pipeline image with GL_FRONT at the next frame start. F7 must retain
        // that exact displayed input rather than only the old back-buffer
        // mirror, otherwise a post-present corruption can disappear while the
        // capture is being armed.
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(previousWindowWidth, previousWindowHeight), 4));
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_FRONT);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, previousWindowWidth, previousWindowHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            session.forensicLines.add("detector-input-window-front-before-f7=GL_FRONT from the previously displayed frame");
            writeColorTextureAsync(pixels, previousWindowWidth, previousWindowHeight,
                    session.directory.resolve("detector-input-window-front-before-f7.png"));
        } catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not capture detector GL_FRONT input", e);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    private static void capturePreviousPipelinePresentation(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null || !previousPipelineValid || previousPipelineTexture <= 0) {
            return;
        }
        session.forensicLines.add("detector-input-direct-pipeline-before-f7=previous final pipeline presentation");
        captureTexture(previousPipelineTexture, previousPipelineWidth, previousPipelineHeight,
                session.directory.resolve("detector-input-direct-pipeline-before-f7.png"));
    }

    private static void capturePreviousFinalInputs(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null) {
            return;
        }
        for (Attachment attachment : Attachment.values()) {
            int index = attachment.getIndex();
            if (previousFinalInputValid[index]) {
                captureTexture(previousFinalInputTextures[index], previousFinalInputWidths[index], previousFinalInputHeights[index],
                        session.directory.resolve("history-final-input-colortex" + index + ".png"));
            }
        }
    }

    private static void mirrorFinalInput(DeferredFramebuffer framebuffer, Attachment attachment) {
        int index = attachment.getIndex();
        int sourceTexture = framebuffer.getReadTexture(attachment);
        int width = framebuffer.getAttachmentWidth(attachment);
        int height = framebuffer.getAttachmentHeight(attachment);
        if (sourceTexture <= 0 || width <= 0 || height <= 0 || !ensurePreviousFinalInputTarget(index, width, height)) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousFinalInputReadFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, sourceTexture, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousFinalInputDrawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, previousFinalInputTextures[index], 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            previousFinalInputValid[index] = true;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    private static boolean ensurePreviousFinalInputTarget(int index, int width, int height) {
        if (previousFinalInputReadFramebuffer <= 0) previousFinalInputReadFramebuffer = GL30.glGenFramebuffers();
        if (previousFinalInputDrawFramebuffer <= 0) previousFinalInputDrawFramebuffer = GL30.glGenFramebuffers();
        if (previousFinalInputTextures[index] <= 0) previousFinalInputTextures[index] = GL11.glGenTextures();
        if (previousFinalInputReadFramebuffer <= 0 || previousFinalInputDrawFramebuffer <= 0 || previousFinalInputTextures[index] <= 0) return false;
        if (previousFinalInputWidths[index] == width && previousFinalInputHeights[index] == height) return true;
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousFinalInputTextures[index]);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        previousFinalInputWidths[index] = width;
        previousFinalInputHeights[index] = height;
        previousFinalInputValid[index] = false;
        return true;
    }

    private static void capturePreviousPreBloomPresentation(long frameId) {
        CaptureSession session = sessionFor(frameId);
        if (session == null || !previousPreBloomValid || previousPreBloomTexture <= 0) {
            return;
        }
        captureTexture(previousPreBloomTexture, previousPreBloomWidth, previousPreBloomHeight,
                session.directory.resolve("history-final-before-bloom-before-f7.png"));
    }

    private static void mirrorPipelinePresentation(Framebuffer target) {
        mirrorPresentationTarget(target, PreviousPresentationTarget.POST_BLOOM);
    }

    private static void mirrorTemporalPipelinePresentation(long frameId, Framebuffer target) {
        if (frameId == lastTemporalPipelineFrame) {
            return;
        }
        if (mirrorTemporalPresentation(target, temporalPipelineTextures, temporalPipelineWidths, temporalPipelineHeights,
                temporalPipelineValid, temporalPipelineFrames, temporalPipelineNextSlot, frameId)) {
            temporalPipelineNextSlot = (temporalPipelineNextSlot + 1) % TEMPORAL_PRESENTATION_HISTORY;
            lastTemporalPipelineFrame = frameId;
        }
    }

    private static boolean mirrorTemporalPresentation(Framebuffer target, int[] textures, int[] widths, int[] heights,
                                                       boolean[] valid, long[] frames, int slot, long frame) {
        int sourceFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int width = MinecraftReflectionCompat.framebufferWidth(target);
        int height = MinecraftReflectionCompat.framebufferHeight(target);
        if (sourceFramebuffer <= 0 || width <= 0 || height <= 0 || !ensureTemporalTarget(textures, widths, heights, valid, slot, width, height)) {
            return false;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, temporalCopyDrawFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, textures[slot], 0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            valid[slot] = true;
            frames[slot] = frame;
            return true;
        } catch (RuntimeException | LinkageError e) {
            valid[slot] = false;
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not mirror temporal presentation", e);
            return false;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    private static boolean ensureTemporalTarget(int[] textures, int[] widths, int[] heights, boolean[] valid,
                                                int slot, int width, int height) {
        if (temporalCopyDrawFramebuffer <= 0) {
            temporalCopyDrawFramebuffer = GL30.glGenFramebuffers();
        }
        if (textures[slot] <= 0) {
            textures[slot] = GL11.glGenTextures();
        }
        if (temporalCopyDrawFramebuffer <= 0 || textures[slot] <= 0) {
            return false;
        }
        if (widths[slot] == width && heights[slot] == height) {
            return true;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[slot]);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            widths[slot] = width;
            heights[slot] = height;
            valid[slot] = false;
            return true;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static void captureTemporalPresentationHistory(CaptureSession session) {
        captureTemporalPresentationHistory(session, "temporal-pipeline", temporalPipelineTextures, temporalPipelineWidths,
                temporalPipelineHeights, temporalPipelineValid, temporalPipelineFrames, temporalPipelineNextSlot);
        captureTemporalPresentationHistory(session, "temporal-external", temporalExternalTextures, temporalExternalWidths,
                temporalExternalHeights, temporalExternalValid, temporalExternalFrames, temporalExternalNextSlot);
    }

    private static void captureTemporalPresentationHistory(CaptureSession session, String label, int[] textures, int[] widths,
                                                           int[] heights, boolean[] valid, long[] frames, int nextSlot) {
        int imageIndex = 0;
        for (int offset = 0; offset < TEMPORAL_PRESENTATION_HISTORY; offset++) {
            int slot = (nextSlot + offset) % TEMPORAL_PRESENTATION_HISTORY;
            if (!valid[slot]) {
                continue;
            }
            imageIndex++;
            captureTexture(textures[slot], widths[slot], heights[slot], session.directory.resolve(String.format(
                    "%s-%02d-frame-%d.png", label, imageIndex, frames[slot])));
        }
    }

    private static void mirrorPresentationTarget(Framebuffer target, PreviousPresentationTarget destination) {
        if (target == null) {
            return;
        }
        int sourceFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int width = MinecraftReflectionCompat.framebufferWidth(target);
        int height = MinecraftReflectionCompat.framebufferHeight(target);
        if (width <= 0 || height <= 0 || !destination.ensureTarget(width, height)) {
            return;
        }
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.framebuffer());
            GL11.glReadBuffer(sourceFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            destination.markValid();
        } catch (RuntimeException | LinkageError e) {
            destination.markInvalid();
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not mirror a pipeline presentation boundary", e);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    private static CaptureSession createSession(long frameId) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Path gameDirectory = MinecraftReflectionCompat.gameDir(minecraft).toPath();
        Path directory = gameDirectory.resolve("screenshots").resolve("ausm-frame-layers")
                .resolve(DIRECTORY_TIMESTAMP.format(LocalDateTime.now()) + "_frame-" + frameId);
        try {
            Files.createDirectories(directory);
            MainMod.LOGGER.info("[AUSMFrameLayerCapture] Capturing frame {} into {}", frameId, directory);
            return new CaptureSession(frameId, directory);
        } catch (IOException e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not create capture directory {}", directory, e);
            return null;
        }
    }

    private static void captureAllGbufferLayers(CaptureSession session, DeferredFramebuffer framebuffer) {
        if (framebuffer == null || !framebuffer.isUsable()) {
            MainMod.LOGGER.warn("[AUSMFrameLayerCapture] Skipping requested capture because the deferred framebuffer is unavailable");
            finish(activeCapture);
            return;
        }
        for (Attachment attachment : Attachment.values()) {
            if (attachment == Attachment.COLOR) {
                captureColortex0State(session, "initial-gbuffer-before-world-render",
                        framebuffer.getReadTexture(attachment), framebuffer.getAttachmentWidth(attachment),
                        framebuffer.getAttachmentHeight(attachment));
                continue;
            }
            captureTexture(
                    framebuffer.getReadTexture(attachment),
                    framebuffer.getAttachmentWidth(attachment),
                    framebuffer.getAttachmentHeight(attachment),
                    session.directory.resolve("layers-initial-gbuffer-colortex" + attachment.getIndex() + ".png")
            );
        }
    }

    private static void captureDepthLayers(CaptureSession session, DeferredFramebuffer framebuffer, String prefix) {
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();
        captureDepthTexture(framebuffer.getDepthTexture(), width, height, session.directory.resolve(prefix + "-depthtex0.png"));
        captureDepthTexture(
                framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX1_SNAPSHOT),
                width,
                height,
                session.directory.resolve(prefix + "-depthtex1.png")
        );
        captureDepthTexture(
                framebuffer.getDepthSamplerTexture(DeferredFramebuffer.DEPTHTEX2_SNAPSHOT),
                width,
                height,
                session.directory.resolve(prefix + "-depthtex2.png")
        );
    }

    private static CaptureSession sessionFor(long frameId) {
        synchronized (PipelineFrameLayerCapture.class) {
            return activeCapture != null && activeCapture.frameId == frameId ? activeCapture : null;
        }
    }

    private static CaptureSession activeSession() {
        synchronized (PipelineFrameLayerCapture.class) {
            return activeCapture;
        }
    }

    /**
     * Writes the authoritative current-frame colortex0 timeline. The numeric
     * prefix is chronological, while the suffix identifies the exact producer
     * boundary; only the window image keeps the terminal 100 prefix.
     */
    private static void captureColortex0State(CaptureSession session, String boundary, int texture, int width, int height) {
        if (session == null) {
            return;
        }
        session.colortex0States++;
        String prefix = String.format("%02d", session.colortex0States);
        captureTexture(texture, width, height, session.directory.resolve(prefix + "-colortex0-" + sanitizeFileComponent(boundary) + ".png"));
    }

    private static void recordForensics(CaptureSession session, String label, int texture, int width, int height) {
        session.forensicLines.add(PipelineFrameForensics.describeTexture(label, texture, width, height));
    }

    private static void recordForensics(CaptureSession session, String label, Framebuffer framebuffer) {
        session.forensicLines.add(PipelineFrameForensics.describeFramebuffer(label, framebuffer));
    }

    private static void captureTexture(int texture, int width, int height, Path destination) {
        if (texture <= 0 || width <= 0 || height <= 0) {
            MainMod.LOGGER.warn("[AUSMFrameLayerCapture] Skipping {} because texture={} size={}x{}", destination.getFileName(), texture, width, height);
            return;
        }
        try {
            ByteBuffer pixels = readColorPixels(texture, width, height);
            writeColorTextureAsync(pixels, width, height, destination);
        } catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not write {}", destination, e);
        }
    }

    private static ByteBuffer readColorPixels(int texture, int width, int height) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }

        return pixels;
    }

    private static ByteBuffer readWindowPixels(int width, int height) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GL11.glReadBuffer(GL11.GL_BACK);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
        }
        return pixels;
    }

    private static boolean ensurePreviousWindowTarget(int width, int height) {
        if (previousWindowTexture <= 0) {
            previousWindowTexture = GL11.glGenTextures();
        }
        if (previousWindowFramebuffer <= 0) {
            previousWindowFramebuffer = GL30.glGenFramebuffers();
        }
        if (previousWindowTexture <= 0 || previousWindowFramebuffer <= 0) {
            return false;
        }
        if (previousWindowWidth == width && previousWindowHeight == height) {
            return true;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousWindowTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousWindowFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, previousWindowTexture, 0);
            previousWindowWidth = width;
            previousWindowHeight = height;
            previousWindowValid = false;
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static boolean ensurePreviousPipelineTarget(int width, int height) {
        if (previousPipelineTexture <= 0) {
            previousPipelineTexture = GL11.glGenTextures();
        }
        if (previousPipelineFramebuffer <= 0) {
            previousPipelineFramebuffer = GL30.glGenFramebuffers();
        }
        if (previousPipelineTexture <= 0 || previousPipelineFramebuffer <= 0) {
            return false;
        }
        if (previousPipelineWidth == width && previousPipelineHeight == height) {
            return true;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousPipelineTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousPipelineFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, previousPipelineTexture, 0);
            previousPipelineWidth = width;
            previousPipelineHeight = height;
            previousPipelineValid = false;
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static boolean ensurePreviousPreBloomTarget(int width, int height) {
        if (previousPreBloomTexture <= 0) {
            previousPreBloomTexture = GL11.glGenTextures();
        }
        if (previousPreBloomFramebuffer <= 0) {
            previousPreBloomFramebuffer = GL30.glGenFramebuffers();
        }
        if (previousPreBloomTexture <= 0 || previousPreBloomFramebuffer <= 0) {
            return false;
        }
        if (previousPreBloomWidth == width && previousPreBloomHeight == height) {
            return true;
        }

        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousPreBloomTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousPreBloomFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, previousPreBloomTexture, 0);
            previousPreBloomWidth = width;
            previousPreBloomHeight = height;
            previousPreBloomValid = false;
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        }
    }

    private static void restoreReadBuffer(int framebuffer, int readBuffer) {
        if (framebuffer == 0) {
            GL11.glReadBuffer(readBuffer == 0 ? GL11.GL_BACK : readBuffer);
        } else if (readBuffer != 0) {
            GL11.glReadBuffer(readBuffer);
        }
    }

    private static void restoreDrawBuffer(int framebuffer, int drawBuffer) {
        if (framebuffer == 0) {
            GL11.glDrawBuffer(drawBuffer == 0 ? GL11.GL_BACK : drawBuffer);
        } else if (drawBuffer != 0) {
            GL11.glDrawBuffer(drawBuffer);
        }
    }

    private static void writeColorTextureAsync(ByteBuffer pixels, int width, int height, Path destination) {
        IMAGE_WRITER.execute(() -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                int imageY = height - y - 1;
                for (int x = 0; x < width; x++) {
                    int offset = (y * width + x) * 4;
                    int red = pixels.get(offset) & 0xFF;
                    int green = pixels.get(offset + 1) & 0xFF;
                    int blue = pixels.get(offset + 2) & 0xFF;
                    image.setRGB(x, imageY, red << 16 | green << 8 | blue);
                }
            }
            try {
                ImageIO.write(image, "png", destination.toFile());
            } catch (IOException | RuntimeException | LinkageError e) {
                MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not write {}", destination, e);
            }
        });
    }

    private static void captureDepthTexture(int texture, int width, int height, Path destination) {
        if (texture <= 0 || width <= 0 || height <= 0) {
            return;
        }
        try {
            ByteBuffer pixels = readDepthPixels(texture, width, height);
            writeDepthTextureAsync(pixels, width, height, destination);
        } catch (RuntimeException | LinkageError e) {
            MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not write {}", destination, e);
        }
    }

    private static ByteBuffer readDepthPixels(int texture, int width, int height) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), Float.BYTES));
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, pixels);
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }

        return pixels;
    }

    private static void writeDepthTextureAsync(ByteBuffer pixels, int width, int height, Path destination) {
        IMAGE_WRITER.execute(() -> {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                int imageY = height - y - 1;
                for (int x = 0; x < width; x++) {
                    int depth = Math.round(Math.max(0.0f, Math.min(1.0f, pixels.getFloat((y * width + x) * Float.BYTES))) * 255.0f);
                    image.setRGB(x, imageY, depth << 16 | depth << 8 | depth);
                }
            }
            try {
                ImageIO.write(image, "png", destination.toFile());
            } catch (IOException | RuntimeException | LinkageError e) {
                MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not write {}", destination, e);
            }
        });
    }

    private static String sanitizeFileComponent(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void finish(CaptureSession session) {
        if (session == null) {
            return;
        }
        synchronized (PipelineFrameLayerCapture.class) {
            if (activeCapture == session) {
                activeCapture = null;
                writeForensicsAsync(session);
                MainMod.LOGGER.info("[AUSMFrameLayerCapture] Queued image writes for frame {} into {}", session.frameId, session.directory);
            }
        }
    }

    private static void writeForensicsAsync(CaptureSession session) {
        if (session.forensicLines.isEmpty()) {
            return;
        }
        List<String> lines = List.copyOf(session.forensicLines);
        Path report = session.directory.resolve("forensics.txt");
        IMAGE_WRITER.execute(() -> {
            try {
                Files.write(report, lines);
            } catch (IOException e) {
                MainMod.LOGGER.error("[AUSMFrameLayerCapture] Could not write {}", report, e);
            }
        });
    }

    private static final class CaptureSession {
        private final long frameId;
        private final Path directory;
        private boolean initialGbufferCaptured;
        private boolean finalInputsCaptured;
        private int colortex0States;
        private int gbufferLayers;
        private int compositePasses;
        private final List<String> forensicLines = new ArrayList<>();

        private CaptureSession(long frameId, Path directory) {
            this.frameId = frameId;
            this.directory = directory;
        }
    }

    private enum PreviousPresentationTarget {
        PRE_BLOOM {
            @Override
            boolean ensureTarget(int width, int height) {
                return ensurePreviousPreBloomTarget(width, height);
            }

            @Override
            int framebuffer() {
                return previousPreBloomFramebuffer;
            }

            @Override
            void markValid() {
                previousPreBloomValid = true;
            }

            @Override
            void markInvalid() {
                previousPreBloomValid = false;
            }
        },
        POST_BLOOM {
            @Override
            boolean ensureTarget(int width, int height) {
                return ensurePreviousPipelineTarget(width, height);
            }

            @Override
            int framebuffer() {
                return previousPipelineFramebuffer;
            }

            @Override
            void markValid() {
                previousPipelineValid = true;
            }

            @Override
            void markInvalid() {
                previousPipelineValid = false;
            }
        };

        abstract boolean ensureTarget(int width, int height);

        abstract int framebuffer();

        abstract void markValid();

        abstract void markInvalid();
    }
}
