package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.pipeline.compat.ShaderlessNothiriumFogGuard;
import com.luna.ausm.impl.util.ConcurrentLongSet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;

abstract class PipelineRuntimeDiagnosticsState extends PipelineRuntimeStateBase {
    protected int positiveNothiriumTerrainProbeLogs = 0;

    protected boolean zeroOpaqueTerrainRecoveryRequested = false;

    protected int hardwareSafeVanillaTerrainRefreshCooldown = 0;

    protected int compositeInvalidFallbackFrames = 0;

    protected long compositeInvalidFallbackSnapshotFrame = Long.MIN_VALUE;

    protected boolean compositeInvalidFallbackSnapshotHasScene = false;

    protected int compositeInvalidFallbackLogs = 0;

    protected int compositeInvalidRestoreLogs = 0;

    protected int sparseStartupPresentationHoldLogs = 0;

    protected int sparseStartupPresentationHoldFrames = 0;

    protected int softVanillaPresentationProbeLogs = 0;

    protected int softVanillaLayerTimingLogs = 0;

    protected int softVanillaSpecialBlockProbeLogs = 0;

    protected int softVanillaFrameTimingLogs = 0;

    protected long currentWorldFrameStartNanos = Long.MIN_VALUE;

    protected long currentWorldFrameReadyNanos = Long.MIN_VALUE;

    protected long currentWorldFrameFinishStartNanos = Long.MIN_VALUE;

    protected long currentWorldFrameAfterNativeBloomNanos = Long.MIN_VALUE;

    protected long currentWorldFrameBlitStartNanos = Long.MIN_VALUE;

    protected int nothiriumHybridVanillaMaintenanceFrames = 0;

    protected int nothiriumHybridVanillaMaintenanceLogs = 0;

    protected String nothiriumHybridVanillaMaintenanceReason = "";

    protected int nothiriumMainVanillaDrawPathFrames = 0;

    protected int nothiriumMainVanillaDrawPathLogs = 0;

    protected String nothiriumMainVanillaDrawPathReason = "";

    protected int nothiriumMainSetupBridgeLogs = 0;

    protected long nothiriumShaderedMainSetupFrame = Long.MIN_VALUE;

    protected long nothiriumSparseMainTerrainFrame = Long.MIN_VALUE;

    protected long nothiriumShaderedMainPostCompileSetupFrame = Long.MIN_VALUE;

    protected long nothiriumProviderSupplementCompileFrame = Long.MIN_VALUE;

    protected int nothiriumProviderSupplementCompileLayerMask = 0;

    protected long nothiriumNonSolidRepairCutoutMippedFrame = Long.MIN_VALUE;

    protected long nothiriumNonSolidRepairCutoutFrame = Long.MIN_VALUE;

    protected long nothiriumNonSolidRepairTranslucentFrame = Long.MIN_VALUE;

    protected long nothiriumSparseMainRepairFrame = Long.MIN_VALUE;

    protected long nothiriumNonSolidProviderDrawCutoutMippedUntilFrame = Long.MIN_VALUE;

    protected long nothiriumNonSolidProviderDrawCutoutUntilFrame = Long.MIN_VALUE;

    protected long nothiriumNonSolidProviderDrawTranslucentUntilFrame = Long.MIN_VALUE;

    protected long nothiriumSparseMainProviderDrawUntilFrame = Long.MIN_VALUE;

    protected int nothiriumNonSolidRepairLogs = 0;

    protected int nothiriumSparseMainRepairLogs = 0;

    protected int nothiriumNonSolidProviderDrawLogs = 0;

    protected int nothiriumSparseMainProviderDrawLogs = 0;

    protected World lastHardwareSafeVanillaTerrainRefreshWorld = null;

    protected int lastHardwareSafeVanillaTerrainRefreshChunkX = Integer.MIN_VALUE;

    protected int lastHardwareSafeVanillaTerrainRefreshChunkZ = Integer.MIN_VALUE;

    protected boolean lastHardwareSafeVanillaTerrainLoadedNearPlayer = false;

    protected boolean pipelineTerrainFormatSupported = false;

    protected boolean deferredPassesRenderedThisFrame = false;

    protected boolean preparePassesRenderedBeforeShadowThisFrame = false;

    protected boolean preTranslucentDepthCopiedThisFrame = false;

    protected boolean preHandDepthCopiedThisFrame = false;

    protected boolean setupComputePending = false;

    protected boolean terrainCullOverrideActive = false;

    protected boolean previousTerrainCullEnabled = true;

    protected boolean terrainOcclusionOverrideActive = false;

    protected boolean previousRenderChunksManyForOcclusion = true;

    protected boolean nothiriumPipelineBlockFormatActive = false;

    protected boolean worldFrameActive = false;

    protected Framebuffer externalWorldFramebufferTarget = null;

    protected boolean renderingShadowMap = false;

    protected boolean renderingGui = false;

    /**
     * HUD and bypassed vanilla GuiScreen item draws that must never enter world/hand passes.
     */
    protected int guiItemRenderDepth = 0;

    protected long guiTargetContentFrame = Long.MIN_VALUE;

    protected int causticRuntimeProbeLogs = 0;

    protected int lightShaftInputProbeLogs = 0;

    protected String lastCausticRuntimeProbe = "";

    protected int entityBlendProbeLogs = 0;

    protected String lastEntityBlendProbe = "";

    protected int localPlayerEntityProbeLogs = 0;

    protected int openBlocksSkyCaptureProbeLogs = 0;

    protected int openBlocksSkyProjectionProbeLogs = 0;

    protected boolean shadowMapPopulated = false;

    protected boolean shadowMapUsable = false;

    protected boolean shadowMapSparseForSampling = false;

    protected int shadowMapCoverageStableFrames = 0;

    protected int shadowMapCoverageRegressionLogs = 0;

    protected int invalidShadowTerrainFrames = 0;

    protected int invalidShadowTerrainSuppressedFrames = 0;

    protected int invalidShadowTerrainSuppressionLogs = 0;

    protected int nothiriumShadowInvalidFrames = 0;

    protected int nothiriumShadowSuppressedFrames = 0;

    protected int nothiriumShadowSuppressionLogs = 0;

    protected World pendingBetterPortalsPortalBlockWorld;

    protected BlockPos pendingBetterPortalsPortalBlockPos;

    protected IBlockState pendingBetterPortalsPortalBlockOldState;

    protected IBlockState pendingBetterPortalsPortalBlockNewState;

    protected int pendingBetterPortalsPortalBlockRefreshDelay = -1;

    protected int pendingBetterPortalsPortalBlockChangeCount = 0;

    protected World lastBetterPortalsPortalBlockRefreshWorld;

    protected BlockPos lastBetterPortalsPortalBlockRefreshPos;

    protected int lastBetterPortalsPortalBlockRefreshDimension = Integer.MIN_VALUE;

    protected long lastBetterPortalsPortalBlockRefreshMillis = 0L;

    protected RenderGlobal activeVanillaViewFrustumRenderGlobal = null;

    protected World activeVanillaViewFrustumWorld = null;

    protected int activeVanillaViewFrustumRenderDistanceChunks = -1;

    protected boolean betterPortalsViewFrustumUpdateWarningLogged = false;

    protected int cameraFrustumSyncLogs = 0;

    protected int clientChunkRenderRefreshLogs = 0;

    protected World lastCameraFrustumSyncWorld = null;

    protected ViewFrustum lastCameraFrustumSyncViewFrustum = null;

    protected int lastCameraFrustumSyncChunkX = Integer.MIN_VALUE;

    protected int lastCameraFrustumSyncChunkZ = Integer.MIN_VALUE;

    protected int lastStableMainWorldVanillaRenderDistanceChunks = -1;

    protected int lastObservedRenderDistanceChunks = -1;

    protected World lastTerrainTransitionWorld = null;

    protected int lastTerrainTransitionDimension = Integer.MIN_VALUE;

    protected long lastTerrainTransitionMillis = 0L;

    protected boolean betterPortalsChunkUpdateWarningLogged = false;

    protected boolean shadowHealthLogged = false;

    protected int shadowHealthLogAttempts = 0;

    protected int shadowMapInvalidLogs = 0;

    protected int shadowMapSuppressedLogs = 0;

    protected int guiRenderDepth = 0;

    protected int guiEntityPreviewStateDepth = 0;

    protected int guiModelStateProbeLogs = 0;

    protected int waterRoutingProbeLogs = 0;

    protected int waterDuplicateProbeLogs = 0;

    protected int waterAttachmentDeltaProbeLogs = 0;

    protected int specialLayerProbeLogs = 0;

    protected int pipelinePassProbeLogs = 0;

    protected int layerOutputProbeLogs = 0;

    protected int shadowTargetProbeLogs = 0;

    protected final ByteBuffer[] waterAttachmentBefore = new ByteBuffer[2];

    protected final ByteBuffer[] waterAttachmentAfter = new ByteBuffer[2];

    protected final int[] waterAttachmentProbeWidths = new int[2];

    protected final int[] waterAttachmentProbeHeights = new int[2];

    protected final int[] waterAttachmentProbeIndices = new int[2];

    protected boolean waterAttachmentDeltaProbeActive = false;

    protected int handItemDrawStateLogs = 0;

    protected int handGbufferProbeLogs = 0;

    protected int handPassBindLogs = 0;

    /**
     * Deliberately broad forensic trace for unresolved sky/hand/lily defects.
     */
    protected int forensicTraceEvents = 0;

    protected final Set<Long> lilyPadShadowProbeChunks = ConcurrentHashMap.newKeySet();

    protected int shaderlessLightStateProbeLogs = 0;

    protected int shaderlessSkyGuiWorldProbeLogs = 0;

    protected int shaderlessSkyGuiScreenProbeLogs = 0;

    protected int astralVoidSkyProbeLogs = 0;

    protected int shaderlessAstralSkyColorLogs = 0;

    protected int freshSkyProbeLogs = 0;

    protected int freshSkyGuiProbeLogs = 0;

    protected int shaderedVoidSkyProbeLogs = 0;

    protected int shaderedVoidSkyTargetProbeLogs = 0;

    protected int shaderedVoidSkyAttachmentProbeLogs = 0;

    protected int skyPresentationRouteProbeLogs = 0;

    protected int ownedSkyBackingProbeLogs = 0;

    protected int ownedSkyBackingDecisionProbeLogs = 0;

    protected int directColorPresentLogs = 0;

    protected int directWindowPresentLogs = 0;

    protected int directF1WindowPresentLogs = 0;

    protected int presentationBoundaryLogs = 0;

    protected int skyDomeProbeLogs = 0;

    protected int skyDomeGuiProbeLogs = 0;

    protected int skyDomePauseProbeLogs = 0;

    protected int worldPassSkyDomeProbeLogs = 0;

    protected int worldPassSkyDomeGuiProbeLogs = 0;

    protected int worldPassSkyDomePauseProbeLogs = 0;

    protected int shaderlessSolidTerrainSkyProbeLogs = 0;

    protected int shaderlessSolidTerrainSkyGuiProbeLogs = 0;

    protected int shaderlessSolidTerrainSkyPauseProbeLogs = 0;

    protected int voidSkyStageProbeLogs = 0;

    protected int nothiriumFogProbeLogs = 0;

    protected int nothiriumRenderProbeLogs = 0;

    protected int nothiriumFogGuardLogs = 0;

    protected final ShaderlessNothiriumFogGuard shaderlessNothiriumFogGuard = new ShaderlessNothiriumFogGuard();

    protected int shaderlessVoidLightRepairLogs = 0;

    protected int shaderlessVoidSkyPixelProbeLogs = 0;

    protected int shaderlessVoidSkyRepairLogs = 0;

    protected int shaderlessVoidVanillaLowerSkyLogs = 0;

    protected int shaderlessWorldFramebufferHandoffLogs = 0;

    protected int shaderlessSkyRgbFillLogs = 0;

    protected int shaderlessSkyRgbFillGuiLogs = 0;

    protected int shaderlessSkyRgbFillPauseLogs = 0;

    protected int shaderlessLowerSkyMeshLogs = 0;

    protected int shaderlessLowerSkyMeshGuiLogs = 0;

    protected int shaderlessLowerSkyMeshPauseLogs = 0;

    protected int voidWorldSkyRendererChainLogs = 0;

    protected int voidWorldSkyRendererChainGuiLogs = 0;

    protected int voidWorldSkyRendererChainPauseLogs = 0;

    protected int shaderlessWorldFramebufferForUi = 0;

    protected int shaderlessWorldFramebufferWidth = 0;

    protected int shaderlessWorldFramebufferHeight = 0;

    protected long shaderlessWorldFramebufferFrame = Long.MIN_VALUE;

    protected Vec3d lastShaderlessAstralVoidSkyColor = null;

    protected boolean shaderlessTerrainLightmapCoordsSaved = false;

    protected float shaderlessTerrainPreviousLightmapX = 0.0F;

    protected float shaderlessTerrainPreviousLightmapY = 0.0F;

    protected int inactiveSkyPipelineProbeLogs = 0;

    protected int activeSkyPipelineProbeLogs = 0;

    protected int finalSkyRepairProbeLogs = 0;

    protected int vanillaRecoveryFrames = 0;

    protected int pendingBloomTerrainRefreshAttempts = 0;

    protected int pendingBloomTerrainRefreshDelay = 0;

    protected String pendingBloomTerrainRefreshReason = "";

    protected boolean runningBloomTerrainRefresh = false;

    protected int bloomZeroGeometryFrames = 0;

    protected int bloomZeroGeometryRefreshCooldown = 0;

    protected long clientRenderFrameNanos = Long.MIN_VALUE;

    protected boolean shaderlessCustomSkyBackingThisFrame = false;

    protected int currentWorldPass = 0;

    protected float currentWorldPartialTicks = 0.0F;

    protected boolean bloomLayerRenderedThisWorldPass = false;

    protected boolean bloomLayerRenderedThisWorldFrame = false;

    protected boolean shaderlessStyleBloomRenderedThisWorldPass = false;

    protected boolean shaderlessStyleBloomRenderedThisWorldFrame = false;

    protected boolean pendingDeferredNativeBloom = false;

    protected double pendingDeferredBloomPartialTicks = 0.0D;

    protected int pendingDeferredBloomPass = 0;

    protected int betterPortalsPipelineLogs = 0;

    protected int shaderlessBloomHookLogs = 0;

    protected int worldLayerDiagLogs = 0;

    protected int externalOverlayLogs = 0;

    protected int renderGlobalLoadRendererLogs = 0;

    protected int vanillaTerrainRendererCreationLogs = 0;

    protected int inactiveBetterPortalsTerrainSkipLogs = 0;

    protected int terrainDiagnosticLogs = 0;

    protected int steadyVanillaTerrainDiagnosticLogs = 0;

    protected int shaderlessNothiriumLoadRendererReloadLogs = 0;

    protected final Set<String> decoratedLightAuditKeys = ConcurrentHashMap.newKeySet();

    protected final AtomicInteger decoratedLightAuditCount = new AtomicInteger();

    protected final Set<String> framedBlockDiagnosticKeys = ConcurrentHashMap.newKeySet();

    protected final AtomicInteger blockcrafteryDiagnosticCount = new AtomicInteger();

    protected final AtomicInteger blockcrafteryBloomDecisionProbeCount = new AtomicInteger();

    protected final AtomicInteger blockcrafteryRouteProbeCount = new AtomicInteger();

    protected final Set<String> framedQuadMaterialProbeKeys = ConcurrentHashMap.newKeySet();

    protected final AtomicInteger framedBloomQuadGateProbeCount = new AtomicInteger();

    protected final AtomicInteger architectureCraftDiagnosticCount = new AtomicInteger();

    protected final AtomicInteger framedPriorityDiagnosticCount = new AtomicInteger();

    protected final Set<String> currentProblemProbeKeys = ConcurrentHashMap.newKeySet();

    protected final Set<String> softVanillaSpecialBlockProbeKeys = ConcurrentHashMap.newKeySet();

    protected final ConcurrentLongSet shaderlessBloomMetadataKnownChunkLayers = new ConcurrentLongSet();

    protected final ConcurrentLongSet shaderlessBloomMetadataChunkLayers = new ConcurrentLongSet();

    protected final AtomicInteger currentProblemProbeCount = new AtomicInteger();

    protected final AtomicInteger activeLightOrIdProbeCount = new AtomicInteger();

    protected final AtomicInteger waterLikeMaterialProbeCount = new AtomicInteger();

    protected final AtomicInteger lilyPadRouteProbeCount = new AtomicInteger();

    protected long lastShaderlessNothiriumLoadRendererReloadMillis = 0L;

    protected int lastShaderlessNothiriumLoadRendererReloadDimension = Integer.MIN_VALUE;

    protected long nextWorldPassSerial = 0L;

    protected long currentWorldPassSerial = Long.MIN_VALUE;

    protected long nothiriumPipelineTranslucentFrame = Long.MIN_VALUE;

    protected long nothiriumPipelineTranslucentWorldPassSerial = Long.MIN_VALUE;

    protected long nothiriumPipelineTranslucentDrawnFrame = Long.MIN_VALUE;

    protected boolean shaderlessBloomRenderedThisWorldPass = false;

    protected boolean shaderlessBloomRenderedThisWorldFrame = false;

    protected boolean shaderlessBloomVertexFormatRefreshRequested = false;

    protected boolean shaderlessBloomExtractionActive = false;

    protected boolean shaderlessBloomExtractionBootstrapActive = false;

    protected int shaderlessTerrainSolidCount = -1;

    protected int shaderlessTerrainCutoutMippedCount = -1;

    protected int shaderlessTerrainCutoutCount = -1;

    protected int shaderlessTerrainTranslucentCount = -1;

    protected int shaderlessTerrainBloomCount = -1;

    protected final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);
}
