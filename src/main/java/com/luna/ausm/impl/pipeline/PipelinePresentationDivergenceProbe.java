package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Continuously compares the current Minecraft framebuffer against AUSM's
 * authoritative direct-presentation snapshot. The comparison is GPU-downscaled
 * before CPU readback, so it can run during normal rendering without requiring
 * an F7 capture or a debugger pause.
 */
final class PipelinePresentationDivergenceProbe {
    private static final int PROBE_WIDTH = 96;
    private static final int PROBE_HEIGHT = 64;
    private static final double DIVERGENCE_THRESHOLD = 0.12D;
    private static final double PEAK_THRESHOLD = 0.35D;
    // Small coverage is enough here: the orange/black corruption is brief and
    // often begins as sparse speckles before it affects most of the frame.
    private static final double BLACK_TRANSITION_COVERAGE_THRESHOLD = 0.015D;
    private static final double WARM_TRANSITION_COVERAGE_THRESHOLD = 0.0025D;
    private static final double BLACK_LUMINANCE_THRESHOLD = 0.12D;
    private static final double WARM_RED_DOMINANCE_THRESHOLD = 0.16D;
    private static final long CAPTURE_INTERVAL_NANOS = 1_000_000_000L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS");
    private static final ExecutorService IMAGE_WRITER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AUSM presentation divergence writer");
        thread.setDaemon(true);
        return thread;
    });

    private static int probeFramebuffer;
    private static int probeTexture;
    private static long lastDivergenceCaptureNanos;

    private PipelinePresentationDivergenceProbe() {
    }

    static void observe(String boundary, Minecraft minecraft, long pipelineFrame, Framebuffer target,
                        int snapshotFramebuffer, int snapshotTexture, int snapshotWidth, int snapshotHeight) {
        // Continuous presentation readback was useful while isolating the
        // black/orange regression, but it stalls the render thread twice per
        // frame and can itself dominate FPS.  Keep explicit F7 diagnostics;
        // this automatic probe is deliberately dormant.
        return;
        /*
        if (minecraft == null || target == null || snapshotFramebuffer <= 0 || snapshotTexture <= 0
                || snapshotWidth <= 0 || snapshotHeight <= 0) {
            return;
        }
        int targetFramebuffer = MinecraftReflectionCompat.framebufferObject(target);
        int targetTexture = MinecraftReflectionCompat.framebufferTexture(target);
        int targetWidth = MinecraftReflectionCompat.framebufferWidth(target);
        int targetHeight = MinecraftReflectionCompat.framebufferHeight(target);
        if (targetFramebuffer <= 0 || targetTexture <= 0 || targetWidth <= 0 || targetHeight <= 0 || !ensureProbeTarget()) {
            return;
        }

        try {
            ByteBuffer snapshotPixels = downsample(snapshotFramebuffer, snapshotWidth, snapshotHeight, GL30.GL_COLOR_ATTACHMENT0);
            if ("previous-window".equals(boundary)) {
                ByteBuffer windowPixels = downsample(0, targetWidth, targetHeight, GL11.GL_FRONT);
                Divergence divergence = compare(snapshotPixels, windowPixels);
                if (!divergence.looksLikeSceneCorruption() || !reserveCaptureSlot()) {
                    return;
                }
                saveWindowDivergence(minecraft, pipelineFrame, targetWidth, targetHeight,
                        snapshotTexture, snapshotWidth, snapshotHeight, divergence);
                return;
            }
            ByteBuffer targetPixels = downsample(targetFramebuffer, targetWidth, targetHeight,
                    targetFramebuffer == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            Divergence divergence = compare(snapshotPixels, targetPixels);
            if (!divergence.looksLikeSceneCorruption()) {
                return;
            }
            if (!reserveCaptureSlot()) {
                return;
            }
            saveTextureDivergence(boundary, minecraft, pipelineFrame, targetTexture, targetWidth, targetHeight,
                    snapshotTexture, snapshotWidth, snapshotHeight, divergence);
        } catch (RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not sample {}", boundary, error);
        }
        */
    }

    static void observeWindowPresentation(Minecraft minecraft, long pipelineFrame, int width, int height,
                                          int snapshotFramebuffer, int snapshotTexture, int snapshotWidth, int snapshotHeight) {
        return;
        /*
        if (minecraft == null || width <= 0 || height <= 0 || snapshotFramebuffer <= 0 || snapshotTexture <= 0
                || snapshotWidth <= 0 || snapshotHeight <= 0 || !ensureProbeTarget()) {
            return;
        }
        try {
            ByteBuffer snapshotPixels = downsample(snapshotFramebuffer, snapshotWidth, snapshotHeight, GL30.GL_COLOR_ATTACHMENT0);
            ByteBuffer windowPixels = downsample(0, width, height, GL11.GL_BACK);
            Divergence divergence = compare(snapshotPixels, windowPixels);
            if (!divergence.looksLikeSceneCorruption()) {
                return;
            }
            if (!reserveCaptureSlot()) {
                return;
            }
            saveWindowDivergence(minecraft, pipelineFrame, width, height, snapshotTexture, snapshotWidth, snapshotHeight, divergence);
        } catch (RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not sample window presentation", error);
        }
        */
    }

    private static boolean reserveCaptureSlot() {
        long now = System.nanoTime();
        if (lastDivergenceCaptureNanos != 0L && now - lastDivergenceCaptureNanos < CAPTURE_INTERVAL_NANOS) {
            return false;
        }
        lastDivergenceCaptureNanos = now;
        return true;
    }

    private static boolean ensureProbeTarget() {
        if (probeFramebuffer <= 0) {
            probeFramebuffer = GL30.glGenFramebuffers();
        }
        if (probeTexture <= 0) {
            probeTexture = GL11.glGenTextures();
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, probeTexture);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, PROBE_WIDTH, PROBE_HEIGHT, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            } finally {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            }
        }
        if (probeFramebuffer <= 0 || probeTexture <= 0) {
            return false;
        }
        int previousFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, probeFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, probeTexture, 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFramebuffer);
        }
    }

    private static ByteBuffer downsample(int sourceFramebuffer, int sourceWidth, int sourceHeight, int sourceReadBuffer) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousDrawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sourceFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, probeFramebuffer);
            GL11.glReadBuffer(sourceReadBuffer);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, sourceWidth, sourceHeight, 0, 0, PROBE_WIDTH, PROBE_HEIGHT,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            ByteBuffer pixels = BufferUtils.createByteBuffer(PROBE_WIDTH * PROBE_HEIGHT * 4);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, probeTexture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return pixels;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
            restoreDrawBuffer(previousDrawFramebuffer, previousDrawBuffer);
        }
    }

    private static Divergence compare(ByteBuffer snapshot, ByteBuffer target) {
        double total = 0.0D;
        double peak = 0.0D;
        int samples = 0;
        int blackTransitions = 0;
        int warmTransitions = 0;
        // This central, upper region limits persistent HUD influence while
        // retaining enough scene area to show a whole-frame presentation drift.
        for (int y = 6; y < PROBE_HEIGHT - 18; y++) {
            for (int x = 18; x < PROBE_WIDTH - 18; x++) {
                int offset = (y * PROBE_WIDTH + x) * 4;
                double snapshotRed = (snapshot.get(offset) & 0xFF) / 255.0D;
                double snapshotGreen = (snapshot.get(offset + 1) & 0xFF) / 255.0D;
                double snapshotBlue = (snapshot.get(offset + 2) & 0xFF) / 255.0D;
                double targetRed = (target.get(offset) & 0xFF) / 255.0D;
                double targetGreen = (target.get(offset + 1) & 0xFF) / 255.0D;
                double targetBlue = (target.get(offset + 2) & 0xFF) / 255.0D;
                double difference = (Math.abs(snapshotRed - targetRed) + Math.abs(snapshotGreen - targetGreen)
                        + Math.abs(snapshotBlue - targetBlue)) / 3.0D;
                total += difference;
                peak = Math.max(peak, difference);
                double snapshotLuminance = luminance(snapshotRed, snapshotGreen, snapshotBlue);
                double targetLuminance = luminance(targetRed, targetGreen, targetBlue);
                if (snapshotLuminance - targetLuminance >= 0.22D && targetLuminance <= BLACK_LUMINANCE_THRESHOLD) {
                    blackTransitions++;
                }
                if (difference >= 0.25D && isWarm(targetRed, targetGreen, targetBlue)
                        && !isWarm(snapshotRed, snapshotGreen, snapshotBlue)) {
                    warmTransitions++;
                }
                samples++;
            }
        }
        return new Divergence(samples == 0 ? 0.0D : total / samples, peak, samples == 0 ? 0.0D : (double) blackTransitions / samples,
                samples == 0 ? 0.0D : (double) warmTransitions / samples);
    }

    private static double luminance(double red, double green, double blue) {
        return red * 0.2126D + green * 0.7152D + blue * 0.0722D;
    }

    private static boolean isWarm(double red, double green, double blue) {
        return red >= 0.24D && red - green >= WARM_RED_DOMINANCE_THRESHOLD && red - blue >= WARM_RED_DOMINANCE_THRESHOLD;
    }

    private static void saveTextureDivergence(String boundary, Minecraft minecraft, long pipelineFrame, int targetTexture,
                                              int targetWidth, int targetHeight, int snapshotTexture, int snapshotWidth,
                                              int snapshotHeight, Divergence divergence) {
        Path directory = MinecraftReflectionCompat.gameDir(minecraft).toPath().resolve("screenshots")
                .resolve("ausm-presentation-divergence")
                .resolve(TIMESTAMP.format(LocalDateTime.now()) + "_frame-" + pipelineFrame);
        try {
            Files.createDirectories(directory);
            ByteBuffer targetPixels = readTexture(targetTexture, targetWidth, targetHeight);
            ByteBuffer snapshotPixels = readTexture(snapshotTexture, snapshotWidth, snapshotHeight);
            IMAGE_WRITER.execute(() -> writeDivergence(directory, boundary, pipelineFrame, targetPixels, targetWidth, targetHeight,
                    snapshotPixels, snapshotWidth, snapshotHeight, divergence));
            MainMod.LOGGER.warn("[AUSMPresentationDivergence] Saved divergence at {} frame={} average={} peak={} into {}",
                    boundary, pipelineFrame, divergence.average, divergence.peak, directory);
        } catch (IOException | RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not save divergence at {}", boundary, error);
        }
    }

    private static void saveWindowDivergence(Minecraft minecraft, long pipelineFrame, int windowWidth, int windowHeight,
                                             int snapshotTexture, int snapshotWidth, int snapshotHeight, Divergence divergence) {
        Path directory = MinecraftReflectionCompat.gameDir(minecraft).toPath().resolve("screenshots")
                .resolve("ausm-presentation-divergence")
                .resolve(TIMESTAMP.format(LocalDateTime.now()) + "_frame-" + pipelineFrame);
        try {
            Files.createDirectories(directory);
            ByteBuffer windowPixels = readFramebuffer(0, windowWidth, windowHeight, GL11.GL_BACK);
            ByteBuffer snapshotPixels = readTexture(snapshotTexture, snapshotWidth, snapshotHeight);
            IMAGE_WRITER.execute(() -> writeWindowDivergence(directory, pipelineFrame, windowPixels, windowWidth, windowHeight,
                    snapshotPixels, snapshotWidth, snapshotHeight, divergence));
            MainMod.LOGGER.warn("[AUSMPresentationDivergence] Saved window divergence at frame={} average={} peak={} into {}",
                    pipelineFrame, divergence.average, divergence.peak, directory);
        } catch (IOException | RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not save window divergence", error);
        }
    }

    private static ByteBuffer readTexture(int texture, int width, int height) {
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return pixels;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousActiveTexture);
        }
    }

    private static ByteBuffer readFramebuffer(int framebuffer, int width, int height, int readBuffer) {
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            ByteBuffer pixels = BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(width, height), 4));
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
            GL11.glReadBuffer(readBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            return pixels;
        } finally {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    private static void writeDivergence(Path directory, String boundary, long pipelineFrame, ByteBuffer targetPixels,
                                        int targetWidth, int targetHeight, ByteBuffer snapshotPixels, int snapshotWidth,
                                        int snapshotHeight, Divergence divergence) {
        try {
            writeImage(targetPixels, targetWidth, targetHeight, directory.resolve("target-" + boundary + ".png"));
            writeImage(snapshotPixels, snapshotWidth, snapshotHeight, directory.resolve("direct-snapshot-" + boundary + ".png"));
            Files.writeString(directory.resolve("report.txt"), "boundary=" + boundary + System.lineSeparator()
                    + "pipelineFrame=" + pipelineFrame + System.lineSeparator()
                    + "averageRgbDifference=" + divergence.average + System.lineSeparator()
                    + "peakRgbDifference=" + divergence.peak + System.lineSeparator()
                    + "blackTransitionCoverage=" + divergence.blackTransitionCoverage + System.lineSeparator()
                    + "warmTransitionCoverage=" + divergence.warmTransitionCoverage + System.lineSeparator());
        } catch (IOException | RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not write divergence images into {}", directory, error);
        }
    }

    private static void writeWindowDivergence(Path directory, long pipelineFrame, ByteBuffer windowPixels,
                                              int windowWidth, int windowHeight, ByteBuffer snapshotPixels,
                                              int snapshotWidth, int snapshotHeight, Divergence divergence) {
        try {
            writeImage(windowPixels, windowWidth, windowHeight, directory.resolve("window-after-direct-present.png"));
            writeImage(snapshotPixels, snapshotWidth, snapshotHeight, directory.resolve("direct-snapshot-window-present.png"));
            Files.writeString(directory.resolve("report.txt"), "boundary=window-after-direct-present" + System.lineSeparator()
                    + "pipelineFrame=" + pipelineFrame + System.lineSeparator()
                    + "averageRgbDifference=" + divergence.average + System.lineSeparator()
                    + "peakRgbDifference=" + divergence.peak + System.lineSeparator()
                    + "blackTransitionCoverage=" + divergence.blackTransitionCoverage + System.lineSeparator()
                    + "warmTransitionCoverage=" + divergence.warmTransitionCoverage + System.lineSeparator());
        } catch (IOException | RuntimeException | LinkageError error) {
            MainMod.LOGGER.error("[AUSMPresentationDivergence] Could not write window divergence images into {}", directory, error);
        }
    }

    private static void writeImage(ByteBuffer pixels, int width, int height, Path destination) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            int imageY = height - y - 1;
            for (int x = 0; x < width; x++) {
                int offset = (y * width + x) * 4;
                image.setRGB(x, imageY, (pixels.get(offset) & 0xFF) << 16 | (pixels.get(offset + 1) & 0xFF) << 8
                        | pixels.get(offset + 2) & 0xFF);
            }
        }
        ImageIO.write(image, "png", destination.toFile());
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

    private record Divergence(double average, double peak, double blackTransitionCoverage, double warmTransitionCoverage) {
        private boolean looksLikeSceneCorruption() {
            return average >= DIVERGENCE_THRESHOLD && peak >= PEAK_THRESHOLD
                    || peak >= 0.25D && blackTransitionCoverage >= BLACK_TRANSITION_COVERAGE_THRESHOLD
                    || peak >= 0.30D && warmTransitionCoverage >= WARM_TRANSITION_COVERAGE_THRESHOLD;
        }
    }
}
