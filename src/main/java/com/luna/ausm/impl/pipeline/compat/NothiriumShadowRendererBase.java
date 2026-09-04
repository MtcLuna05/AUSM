package com.luna.ausm.impl.pipeline.compat;

import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

abstract class NothiriumShadowRendererBase {
    // Prefer shader-visible uniform offsets for transformed fixed-function GLSL paths;
    // fall back to matrix translation only when the uniform is unavailable.
    protected static final boolean USE_CHUNK_OFFSET_UNIFORM = true;

    /**
     * Nothirium owns the VBO allocation for an IVBOPart. Asking the driver for
     * the buffer size once per bound section serializes the shadow draw loop
     * on several drivers. Keep that costly range audit behind an explicit
     * diagnostic switch; production still rejects invalid part metadata.
     */
    protected static final boolean VALIDATE_NOTHIRIUM_VBO_DRAW_RANGES = false;

    protected static final String NOTHIRIUM_MOD_ID = "nothirium";

    protected static final String[] WORLD_HEIGHT_METHODS = {"func_72800_K", "getHeight"};

    protected static final int VANILLA_BLOCK_STRIDE = 28;

    protected static final int POSITION_OFFSET = 0;

    protected static final int COLOR_OFFSET = 12;

    protected static final int TEX_COORD_OFFSET = 16;

    protected static final int LIGHT_COORD_OFFSET = 24;

    protected static final int MAX_SHADOW_COMPILES_PER_FRAME = 8;

    // The old 48/24/12 burst could enqueue 84 terrain rebuilds in a single
    // render frame. Keep the near solid terrain responsive, but leave enough
    // render-thread time for the shader pipeline and Bloom composite.
    protected static final int MAX_MAIN_TERRAIN_SOLID_COMPILES_PER_FRAME = 3;

    protected static final int MAX_MAIN_TERRAIN_CUTOUT_COMPILES_PER_FRAME = 1;

    protected static final int MAX_MAIN_TERRAIN_TRANSLUCENT_COMPILES_PER_FRAME = 1;

    /**
     * Shadow selection can contain every loaded section (7k+ in large
     * worlds). Scan a bounded rotating window per layer instead of doing the
     * full provider walk every frame; all sections remain eligible.
     */
    protected static final int MAX_MAIN_TERRAIN_COMPILE_SCAN_PER_LAYER = 192;

    /**
     * A Nothirium dispatcher update drains its entire render-thread queue.
     * Keep each frame's VBO work bounded instead of converting a completed
     * worker burst into a multi-millisecond render-thread hitch.
     */
    protected static final int MAX_UPLOAD_TASKS_PER_DRAIN = 2;

    protected static final int MAX_PENDING_SHADOW_COMPILES = 64;

    protected static final int MAX_CHUNK_REFRESH_COMPILES = 16;

    /**
     * A queued shader refresh can contain eight columns. Limit its direct
     * Nothirium submissions as one batch so it cannot flood every worker at
     * once; marked sections remain eligible for the normal dirty-chunk path.
     */
    protected static final int MAX_QUEUED_CHUNK_REFRESH_COMPILES_PER_BATCH = 32;

    protected static final int MAX_CHUNK_REFRESH_AUDIT_LOGS = 0;

    protected static final int MAX_VISIBLE_TRANSLUCENT_DIAG_LOGS = 0;

    protected static final int MAX_VISIBLE_TERRAIN_FAILURE_LOGS = 0;

    protected static final int MAX_VISIBLE_NON_SOLID_TERRAIN_FAILURE_LOGS = 0;

    protected static final int MAX_VISIBLE_TERRAIN_DRAW_PROBE_LOGS = 0;

    protected static final int MAX_LILY_SHADOW_VERTEX_SCAN_CALLS = 0;

    protected static final int MAX_LILY_SHADOW_VERTEX_PROBE_LOGS = 0;

    protected static final int LILY_SHADOW_VERTEX_SCAN_BYTES = 32 * 1024;

    protected static final int MAX_EMPTY_LIST_AUDIT_LOGS = 0;

    protected static final int MAX_SHADERED_MAIN_LIST_PROBE_LOGS = 0;

    protected static final int MAX_SHADERED_PROVIDER_STATE_PROBE_LOGS = 0;

    protected static final int MAX_SHADERED_COMPILE_GATE_PROBE_LOGS = 0;

    protected static final int MAX_SHADERED_COMPILE_CANDIDATE_PROBE_LOGS = 0;

    /* Several world/Bloom paths request upload draining in one render frame.
           Nothirium's dispatcher update is expensive enough to pace explicitly. */
    protected static final long MIN_UPLOAD_DRAIN_INTERVAL_NANOS = 2_000_000L;

    protected static final int NOTHIRIUM_OFFSET_ATTRIBUTE = 4;

    protected static final long MAIN_TERRAIN_COMPILE_RETRY_DELAY_MS = 80L;

    protected static final long MAIN_TERRAIN_COMPILE_TRACK_TTL_MS = 2000L;

    protected static final long REFLECTION_RETRY_DELAY_MS = 1000L;

    protected static NothiriumShadowRenderer.Reflection reflection;

    protected static long nextReflectionAttemptMillis;

    protected boolean disabled;

    protected boolean warned;

    protected boolean emptyAuditLogged;

    protected boolean providerAuditLogged;

    protected boolean uploadNonEmptyLogged;

    protected int uploadAuditAttempts;

    protected int compileAuditAttempts;

    protected int mainCompileAuditAttempts;

    protected int chunkRefreshAuditAttempts;

    protected int visibleTranslucentAuditAttempts;

    protected int visibleTerrainFailureAttempts;

    protected int visibleNonSolidTerrainFailureAttempts;

    protected int visibleTerrainDrawProbeAttempts;

    protected int lilyShadowVertexScanCalls;

    protected int lilyShadowVertexProbeLogs;

    protected int lilyShadowGateProbeLogs;

    protected int shaderedMainListProbeAttempts;

    protected int shaderedProviderStateProbeAttempts;

    protected int shaderedCompileGateProbeAttempts;

    protected int shaderedCompileCandidateProbeAttempts;

    protected long lastUploadDrainNanos;

    protected final ByteBuffer visibleTerrainVertexProbe = BufferUtils.createByteBuffer(128);

    protected final ByteBuffer lilyShadowVertexProbe = BufferUtils.createByteBuffer(LILY_SHADOW_VERTEX_SCAN_BYTES);

    protected final FloatBuffer visibleTerrainMatrixProbe = BufferUtils.createFloatBuffer(16);

    protected final FloatBuffer visibleTerrainProjectionProbe = BufferUtils.createFloatBuffer(16);

    protected final FloatBuffer visibleTerrainUniformProbe = BufferUtils.createFloatBuffer(16);

    protected final Map<Object, Long> mainTerrainCompileAttempts = new IdentityHashMap<>();

    protected final Map<BlockRenderLayer, Integer> mainTerrainCompileScanCursors = new EnumMap<>(BlockRenderLayer.class);

    /**
     * Reused read-only windows over the selected terrain.  The bounded compile
     * scan previously allocated and copied up to 192 chunk references for
     * every layer every frame, despite only needing a rotating view.
     */
    protected final RotatingShadowSelectionChunkList[] mainTerrainCompileScanWindows = {
            new RotatingShadowSelectionChunkList(),
            new RotatingShadowSelectionChunkList(),
            new RotatingShadowSelectionChunkList(),
            new RotatingShadowSelectionChunkList()
    };

    protected final Map<Integer, Integer> chunkOffsetUniformLocations = new HashMap<>();

    protected final Map<Integer, Integer> chunkOffsetInstanceAttributeLocations = new HashMap<>();

    protected final NothiriumShadowIndirectBatch shadowIndirectBatch = new NothiriumShadowIndirectBatch();

    protected final Map<Long, List<Object>> queuedChunkRefreshColumns = new HashMap<>();

    protected final FloatBuffer shadowSelectionModelView = BufferUtils.createFloatBuffer(16);

    protected final FloatBuffer shadowSelectionProjection = BufferUtils.createFloatBuffer(16);

    protected final float[] shadowSelectionModelViewValues = new float[16];

    protected final float[] shadowSelectionProjectionValues = new float[16];

    /**
     * Reused only within one shadow pass. This removes one candidate object
     * for every selected section and the two matrix-array allocations from the
     * render thread's per-frame selection path.
     */
    protected Object[] shadowSelectionChunks = new Object[0];

    protected double[] shadowSelectionDistances = new double[0];

    protected int shadowSelectionScratchCount;

    /**
     * The provider grid and the sun matrix change much more slowly than the
     * shadow pass itself. Keep the last conservative selection separately from
     * the per-pass scratch arrays so a small camera movement can reuse it.
     */
    protected static final double SHADOW_SELECTION_REUSE_DISTANCE_SQUARED = 0.25D;

    protected Object[] cachedShadowSelectionChunks = new Object[0];

    protected int cachedShadowSelectionCount;

    protected NothiriumShadowRenderer.Reflection cachedShadowSelectionReflection;

    protected Object cachedShadowSelectionProvider;

    protected Object[] cachedShadowSelectionSourceChunks;

    protected double cachedShadowSelectionCameraX;

    protected double cachedShadowSelectionCameraY;

    protected double cachedShadowSelectionCameraZ;

    protected int cachedShadowSelectionCameraChunkX = Integer.MIN_VALUE;

    protected int cachedShadowSelectionCameraChunkY = Integer.MIN_VALUE;

    protected int cachedShadowSelectionCameraChunkZ = Integer.MIN_VALUE;

    protected double cachedShadowSelectionMaxDistance;

    protected final Map<Object, NothiriumShadowRenderer.ChunkOrigin> shadowChunkOrigins = new IdentityHashMap<>();

    protected NothiriumShadowRenderer.ShadowSelection shadowSelection;

    protected boolean shadowSelectionActive;

    protected boolean queuedChunkRefreshBatchActive;

    protected boolean shadowIndirectBatchLogged;

    protected Object queuedChunkRefreshProvider;

    protected Object[] queuedChunkRefreshSourceChunks;

    protected int queuedChunkRefreshCompileCount;

    protected Object mainTerrainCompileScanProvider;

    protected Object[] mainTerrainCompileScanSourceChunks;

    protected static int visibleTranslucentStateLogs;

    protected static final class CompileCandidate {
        final Object chunk;
        final double distanceSquared;
        final boolean invalidPart;

        CompileCandidate(Object chunk, double distanceSquared, boolean invalidPart) {
            this.chunk = chunk;
            this.distanceSquared = distanceSquared;
            this.invalidPart = invalidPart;
        }
    }

    protected static final class ChunkOrigin {
        final int x;
        final int y;
        final int z;

        ChunkOrigin(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    protected static final class ProviderCandidate {
        final Object chunk;
        final double distanceSquared;

        ProviderCandidate(Object chunk, double distanceSquared) {
            this.chunk = chunk;
            this.distanceSquared = distanceSquared;
        }
    }

    /**
     * Read-only view over the cached selection. It is replaced only after a
     * provider/grid change or a larger camera move. VBO uploads do not alter
     * section positions, and each draw resolves the current VBO part.
     */
    protected static final class ShadowSelectionChunkList extends AbstractList<Object> {
        final Object[] chunks;
        final int size;

        ShadowSelectionChunkList(Object[] chunks, int size) {
            this.chunks = chunks;
            this.size = size;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            return chunks[index];
        }

        @Override
        public int size() {
            return size;
        }
    }

    /**
     * A non-owning, rotating slice of a selected chunk list.  It is only used
     * synchronously by the render thread, then reset for the next scan.
     */
    protected static final class RotatingShadowSelectionChunkList extends AbstractList<Object> {
        private List<Object> source;
        private int start;
        private int size;

        void reset(List<Object> source, int start, int size) {
            this.source = source;
            this.start = start;
            this.size = size;
        }

        void releaseSource() {
            source = null;
            start = 0;
            size = 0;
        }

        @Override
        public Object get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            int sourceIndex = start + index;
            if (sourceIndex >= source.size()) {
                sourceIndex -= source.size();
            }
            return source.get(sourceIndex);
        }

        @Override
        public int size() {
            return size;
        }
    }

    protected static final class ShadowSelection {
        final NothiriumShadowRenderer.Reflection reflection;
        final Object provider;
        final Object[] sourceChunks;
        final List<Object> chunks;
        final double cameraX;
        final double cameraY;
        final double cameraZ;
        final double maxDistance;

        ShadowSelection(NothiriumShadowRenderer.Reflection reflection, Object provider, Object[] sourceChunks, List<Object> chunks,
                        double cameraX, double cameraY, double cameraZ, double maxDistance) {
            this.reflection = reflection;
            this.provider = provider;
            this.sourceChunks = sourceChunks;
            this.chunks = chunks;
            this.cameraX = cameraX;
            this.cameraY = cameraY;
            this.cameraZ = cameraZ;
            this.maxDistance = maxDistance;
        }
    }

    protected static final class LayerGlState {
        final boolean texture2D;
        final boolean depthTest;
        final boolean alphaTest;
        final boolean blend;
        final boolean depthMask;
        final int depthFunc;
        final int alphaFunc;
        final float alphaRef;
        final int blendSrcRgb;
        final int blendDstRgb;
        final int blendSrcAlpha;
        final int blendDstAlpha;

        LayerGlState() {
            this.texture2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            this.depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            this.alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            this.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            this.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            this.depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            this.alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            this.alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            this.blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            this.blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            this.blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            this.blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        }

        static NothiriumShadowRenderer.LayerGlState prepare(BlockRenderLayer layer) {
            if (layer != BlockRenderLayer.TRANSLUCENT) {
                return null;
            }

            Minecraft mc = MinecraftReflectionCompat.minecraft();
            FixedFunctionGlState.prepareTranslucentBlockLayer(mc);
            NothiriumShadowRenderer.forceTranslucentFixedFunctionState();
            NothiriumShadowRenderer.logVisibleTranslucentState("prepare");
            // The legacy state capture is deliberately not restored (restore
            // is a no-op), so querying twelve GL values here only serializes
            // the translucent shadow draw path with the driver.
            return null;
        }

        void restore() {
            // Probe disabled.
        }
    }

    protected static final class CompileStats {
        int total;
        int nullChunks;
        int withinDistance;
        int distanceCulled;
        int dirty;
        int clean;
        int canCompile;
        int cannotCompile;
        int running;
        int throttled;
        int scheduled;
        String firstChunk = "n/a";

        void captureFirstChunk(int x, int y, int z) {
            if (firstChunk.equals("n/a")) {
                firstChunk = x + "," + y + "," + z;
            }
        }
    }

    protected record DrawProbe(
            int call,
            BlockRenderLayer layer,
            int chunkX,
            int chunkY,
            int chunkZ,
            double cameraX,
            double cameraY,
            double cameraZ,
            int vbo,
            int first,
            int count,
            int offset,
            int size,
            int stride,
            int vboSize,
            boolean pipelineStride,
            String vertex,
            String modelView,
            String uniforms,
            String gl) {
    }

    protected static final class DrawStats {
        int total;
        int nullChunks;
        int withinDistance;
        int distanceCulled;
        int missingPart;
        int partPresent;
        int invalidPart;
        int validPart;
        int emptyCount;
        int positiveCount;
        int badVbo;
        int positiveVbo;
        int badStride;
        int unsupportedStride;
        int invalidRange;
        int drawn;
        String firstChunk = "n/a";
        String firstPart = "n/a";
        String firstState = "n/a";
        int dirtyChunks;
        int cleanChunks;
        int emptyChunks;
        int nonEmptyChunks;
        int canCompileChunks;
        int cannotCompileChunks;
        int taskPresent;
        int futureNull;
        int futureRunning;
        int futureDone;
        int futureCancelled;
        int futureExceptional;
        int recordedChunks;
        int enqueuedChunks;
        int maxRecorded = -1;
        int maxEnqueued = -1;
        int nonemptyMaskChunks;
        final List<Object> unsupportedPipelineChunks = new ArrayList<>();

        void captureFirstChunk(int x, int y, int z) {
            if (firstChunk == "n/a") {
                firstChunk = x + "," + y + "," + z;
            }
        }

        void captureFirstPart(int vbo, int first, int count, int offset, int size, int stride, int vboSize) {
            if (firstPart == "n/a") {
                firstPart = "vbo=" + vbo
                        + " first=" + first
                        + " count=" + count
                        + " offset=" + offset
                        + " size=" + size
                        + " stride=" + stride
                        + " vboSize=" + vboSize;
            }
        }

        void captureUnsupportedPipelineChunk(Object chunk) {
            if (unsupportedPipelineChunks.size() < MAX_CHUNK_REFRESH_COMPILES) {
                unsupportedPipelineChunks.add(chunk);
            }
        }

        void captureState(NothiriumShadowRenderer.Reflection reflection, Object chunk, int x, int y, int z)
                throws ReflectiveOperationException {
            boolean dirty = reflection.isChunkDirty(chunk);
            boolean empty = reflection.isChunkEmpty(chunk);
            Boolean canCompile = reflection.canCompile(chunk);
            Object task = reflection.lastCompileTask(chunk);
            Object futureObject = reflection.lastCompileTaskResult(chunk);
            int recorded = reflection.lastTimeRecorded(chunk);
            int enqueued = reflection.lastTimeEnqueued(chunk);
            int nonemptyMask = reflection.nonemptyVboParts(chunk);

            if (dirty) {
                dirtyChunks++;
            } else {
                cleanChunks++;
            }
            if (empty) {
                emptyChunks++;
            } else {
                nonEmptyChunks++;
            }
            if (Boolean.TRUE.equals(canCompile)) {
                canCompileChunks++;
            } else if (Boolean.FALSE.equals(canCompile)) {
                cannotCompileChunks++;
            }
            if (task != null) {
                taskPresent++;
            }
            String future = futureState(futureObject);
            switch (future) {
                case "null" -> futureNull++;
                case "running" -> futureRunning++;
                case "done" -> futureDone++;
                case "cancelled" -> futureCancelled++;
                case "exceptional" -> futureExceptional++;
                default -> {
                }
            }
            if (recorded >= 0) {
                recordedChunks++;
                maxRecorded = Math.max(maxRecorded, recorded);
            }
            if (enqueued >= 0) {
                enqueuedChunks++;
                maxEnqueued = Math.max(maxEnqueued, enqueued);
            }
            if (nonemptyMask != 0) {
                nonemptyMaskChunks++;
            }
            if (firstState == "n/a") {
                firstState = "pos=" + x + "," + y + "," + z
                        + " dirty=" + dirty
                        + " empty=" + empty
                        + " canCompile=" + canCompile
                        + " task=" + (task != null)
                        + " future=" + future
                        + " recorded=" + recorded
                        + " enqueued=" + enqueued
                        + " nonemptyMask=" + nonemptyMask;
            }
        }

        String stateSummary() {
            if (firstState == "n/a") {
                return "n/a";
            }
            return "dirty=" + dirtyChunks
                    + " clean=" + cleanChunks
                    + " empty=" + emptyChunks
                    + " nonEmpty=" + nonEmptyChunks
                    + " canCompile=" + canCompileChunks
                    + " cannotCompile=" + cannotCompileChunks
                    + " task=" + taskPresent
                    + " future=null/" + futureNull
                    + ",running/" + futureRunning
                    + ",done/" + futureDone
                    + ",cancelled/" + futureCancelled
                    + ",exceptional/" + futureExceptional
                    + " recorded=" + recordedChunks + "(max=" + maxRecorded + ")"
                    + " enqueued=" + enqueuedChunks + "(max=" + maxEnqueued + ")"
                    + " nonemptyMask=" + nonemptyMaskChunks
                    + " first={" + firstState + "}";
        }

        static String futureState(Object futureObject) {
            if (!(futureObject instanceof CompletableFuture<?> future)) {
                return futureObject == null ? "null" : futureObject.getClass().getSimpleName();
            }
            if (future.isCancelled()) {
                return "cancelled";
            }
            if (future.isCompletedExceptionally()) {
                return "exceptional";
            }
            return future.isDone() ? "done" : "running";
        }
    }

    protected NothiriumShadowRenderer self() {
        return (NothiriumShadowRenderer) this;
    }
}
