package com.luna.ausm.impl.pipeline;

import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.mixin.pipeline.RenderGlobalAccessor;
import com.luna.ausm.impl.pipeline.fbo.ShadowFramebuffer;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SHADOW_SUPPRESS_AFTER_INVALID_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.NOTHIRIUM_SHADOW_SUPPRESS_FRAMES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_REALTIME_BLOCK_ENTITY_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_REALTIME_ENTITY_DISTANCE;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SHADOW_UPWARD_CAMERA_DELTA_SUPPRESSION;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SPARSE_SHADOW_MIN_TERRAIN_DRAWS;
import static com.luna.ausm.impl.pipeline.PipelineTerrainConstants.SPARSE_SHADOW_STABLE_FRAMES;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionBoolean;
import static com.luna.ausm.impl.pipeline.pack.PipelineShaderSettings.optionValue;

abstract class PipelineShadowCamera extends PipelineLightVoxelInjection {
    protected void updateShadowMapUsability(int solidCount, int cutoutMippedCount, int cutoutCount, int translucentCount, int blockEntityCount) {
        if (shadowFramebuffer == null) {
            shadowMapPopulated = false;
            shadowMapUsable = false;
            shadowMapSparseForSampling = false;
            shadowMapCoverageStableFrames = 0;
            shadowMapCoverageRegressionLogs = 0;
            return;
        }
        boolean terrainPopulated = solidCount > 0
                || cutoutMippedCount > 0
                || cutoutCount > 0
                || translucentCount > 0;
        boolean drawPopulated = terrainPopulated || blockEntityCount > 0;
        int terrainDrawCount = self().positiveShadowCount(solidCount)
                + self().positiveShadowCount(cutoutMippedCount)
                + self().positiveShadowCount(cutoutCount)
                + self().positiveShadowCount(translucentCount);
        boolean useNothiriumShadowBridge = self().shouldUseNothiriumShadowBridge();
        // glReadPixels synchronizes the render thread with the GPU. Validate
        // the map while it is warming up. Once accepted, keep the inexpensive
        // CPU submission check on every replacement frame: Nothirium can
        // temporarily withdraw nearly every VBO while publishing compiled
        // sections, and sampling that sparse replacement looks like z-fighting.
        if (shadowMapUsable) {
            shadowMapPopulated = drawPopulated;
            boolean currentCoverageReady = !useNothiriumShadowBridge
                    || terrainDrawCount >= SPARSE_SHADOW_MIN_TERRAIN_DRAWS;
            shadowMapSparseForSampling = !currentCoverageReady;
            if (!drawPopulated || !currentCoverageReady) {
                shadowMapUsable = false;
                shadowMapCoverageStableFrames = 0;
            }
            return;
        }
        ShadowFramebuffer.DepthStats stats = shadowFramebuffer.readDepthStats(4);
        boolean populated = terrainPopulated
                || (!self().shouldUseNothiriumShadowBridge() && stats.nonClear() > 0);
        shadowMapPopulated = populated || drawPopulated;
        World renderWorld = renderWorld(MinecraftReflectionCompat.minecraft());
        int dimensionId = safeDimensionId(renderWorld);
        float verticalDelta = self().cameraVerticalDelta();
        boolean upwardMotion = verticalDelta > SHADOW_UPWARD_CAMERA_DELTA_SUPPRESSION;
        boolean nothiriumTerrainCoverageReady = !useNothiriumShadowBridge
                || (terrainDrawCount >= SPARSE_SHADOW_MIN_TERRAIN_DRAWS
                && stats.nonClear() >= SPARSE_SHADOW_MIN_NON_CLEAR_SAMPLES);
        if (nothiriumTerrainCoverageReady) {
            shadowMapCoverageStableFrames = Math.min(SPARSE_SHADOW_STABLE_FRAMES, shadowMapCoverageStableFrames + 1);
        } else {
            shadowMapCoverageStableFrames = 0;
        }
        boolean nothiriumTerrainStable = !useNothiriumShadowBridge
                || shadowMapCoverageStableFrames >= SPARSE_SHADOW_STABLE_FRAMES;
        boolean sparseNothiriumShadow = !nothiriumTerrainCoverageReady || !nothiriumTerrainStable;
        boolean unstableSparseShadow = sparseNothiriumShadow && upwardMotion;
        shadowMapSparseForSampling = sparseNothiriumShadow;
        shadowMapUsable = stats.nonClear() > 0
                && !sparseNothiriumShadow
                && !unstableSparseShadow;
        boolean clearAfterFullTerrainSubmission = terrainDrawCount >= SPARSE_SHADOW_MIN_TERRAIN_DRAWS
                && stats.nonClear() == 0;
        if (!shadowMapUsable && drawPopulated && clearAfterFullTerrainSubmission) {
            invalidShadowTerrainFrames++;
            if (invalidShadowTerrainFrames >= 2) {
                invalidShadowTerrainFrames = 0;
                invalidShadowTerrainSuppressedFrames = Math.max(invalidShadowTerrainSuppressedFrames, 120);
            }
        } else {
            invalidShadowTerrainFrames = 0;
            if (shadowMapUsable) {
                invalidShadowTerrainSuppressedFrames = 0;
            }
        }
        if (useNothiriumShadowBridge && !shadowMapUsable && drawPopulated
                && clearAfterFullTerrainSubmission) {
            nothiriumShadowInvalidFrames++;
            if (nothiriumShadowInvalidFrames >= NOTHIRIUM_SHADOW_SUPPRESS_AFTER_INVALID_FRAMES) {
                nothiriumShadowInvalidFrames = 0;
                nothiriumShadowSuppressedFrames = Math.max(nothiriumShadowSuppressedFrames, NOTHIRIUM_SHADOW_SUPPRESS_FRAMES);
            }
        } else {
            nothiriumShadowInvalidFrames = 0;
            if (shadowMapUsable) {
                nothiriumShadowSuppressedFrames = 0;
            }
        }

    }

    protected int renderShadowBlockLayerFromViewFrustum(Minecraft mc, BlockRenderLayer layer, float partialTicks, Entity viewEntity) {
        if (mc == null || viewEntity == null || !(MinecraftReflectionCompat.renderGlobal(mc) instanceof RenderGlobalAccessor renderGlobal)) {
            return 0;
        }
        ViewFrustum viewFrustum = renderGlobal.ausm$viewFrustum();
        ChunkRenderContainer renderContainer = renderGlobal.ausm$renderContainer();
        RenderChunk[] renderChunks = MinecraftReflectionCompat.viewFrustumRenderChunks(viewFrustum);
        if (renderChunks == null || renderContainer == null) {
            return 0;
        }

        double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = self().shadowLayerCullDistance(layer);
        double maxDistanceSquared = maxDistance * maxDistance;

        MinecraftReflectionCompat.initializeChunkRenderContainer(
                renderContainer, cameraX, cameraY, cameraZ);
        int fallbackCount = 0;
        for (RenderChunk renderChunk : renderChunks) {
            if (renderChunk == null || MinecraftReflectionCompat.renderChunkLayerEmpty(renderChunk, layer)) {
                continue;
            }
            BlockPos position = MinecraftReflectionCompat.renderChunkPosition(renderChunk);
            double dx = MinecraftReflectionCompat.blockPosX(position) + 8.0D - cameraX;
            double dy = MinecraftReflectionCompat.blockPosY(position) + 8.0D - cameraY;
            double dz = MinecraftReflectionCompat.blockPosZ(position) + 8.0D - cameraZ;
            if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
                continue;
            }
            MinecraftReflectionCompat.addChunkRenderContainerChunk(
                    renderContainer, renderChunk, layer);
            fallbackCount++;
        }

        if (fallbackCount > 0) {
            MinecraftReflectionCompat.renderChunkContainerLayer(renderContainer, layer);
        }
        return fallbackCount;
    }

    protected double shadowRenderCullDistance() {
        // `shadow.culling` controls OptiFine/Iris visibility heuristics.  It
        // must not disable the hard geometric extent of the shader shadow
        // projection: the Nothirium provider contains every render-distance
        // section, and feeding it -1 here makes it inspect all of them even
        // though sections beyond shadowDistance cannot affect the map.
        if (shaderProperties.renderSettings().shadowCullingReversed()) {
            return Math.max(32.0D, Math.max(shadowMapDistance, voxelDistance));
        }
        double renderMultiplier = shadowDistanceRenderMul >= 0.0f ? shadowDistanceRenderMul : 1.0D;
        // One chunk of slack retains sections straddling the projection edge.
        return Math.max(32.0D, shadowMapDistance * renderMultiplier + 32.0D);
    }

    protected int nextShadowFrameCount() {
        if (shadowFrameCount == Integer.MAX_VALUE) {
            shadowFrameCount = 1_000_000;
        }
        return shadowFrameCount++;
    }

    protected void setupShadowCamera(Entity viewEntity, float partialTicks) {
        double x = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double y = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double z = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        float shadowDepthRange = Math.max(256.0F, shadowMapDistance * 2.0F);
        float shadowDepthCenter = shadowDepthRange * 0.5F;
        GL11.glOrtho(
                -shadowMapDistance,
                shadowMapDistance,
                -shadowMapDistance,
                shadowMapDistance,
                0.05F,
                shadowDepthRange
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -shadowDepthCenter);

        World world = renderWorld(MinecraftReflectionCompat.minecraft());
        if (world != null && useEndFlashShadowLight(world)) {
            GL11.glRotatef(90.0F - endFlashPitchDegrees, 1.0F, 0.0F, 0.0F);
            GL11.glRotatef(endFlashYawDegrees, 0.0F, 1.0F, 0.0F);
        } else {
            GL11.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
            float celestialAngle = world != null ? MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks) : 0.0F;
            float sunAngle = celestialAngle < 0.75F ? celestialAngle + 0.25F : celestialAngle - 0.75F;
            float angle = celestialAngle * -360.0F;
            if (sunAngle <= 0.5F) {
                GL11.glRotatef(angle, 0.0F, 0.0F, 1.0F);
            } else {
                GL11.glRotatef(angle + 180.0F, 0.0F, 0.0F, 1.0F);
            }
            GL11.glRotatef(sunPathRotation, 1.0F, 0.0F, 0.0F);
        }

        double shadowTexelSize = Math.max(0.001D,
                shadowMapDistance * 2.0D / Math.max(1, shadowResolution()));
        double requestedInterval = shadowIntervalSize;
        double interval = Math.abs(requestedInterval);
        double snapX = 0.0D;
        double snapY = 0.0D;
        double snapZ = 0.0D;
        if (Double.isFinite(interval) && interval > 0.0D) {
            // Match Iris/OptiFine semantics: shadowIntervalSize is a world-axis
            // grid. Applying a light-space remainder makes the grid rotate with
            // the sun and produces visible camera-coupled jumps.
            double halfInterval = interval * 0.5D;
            snapX = x % interval - halfInterval;
            snapY = y % interval - halfInterval;
            snapZ = z % interval - halfInterval;
            GL11.glTranslatef((float) snapX, (float) snapY, (float) snapZ);
        }
        String pixelatedShadows = optionValue(shaderProperties, "PIXELATED_SHADOWS");
        String pixelatedBlocklight = optionValue(shaderProperties, "PIXELATED_BLOCKLIGHT");
        String pixelatedAo = optionValue(shaderProperties, "PIXELATED_AO");
        String pixelatedMode = optionValue(shaderProperties, "PIXELATED_SHADOWS_MODE");
        String probeKey = activePackName + '|' + pixelatedShadows + '|' + pixelatedBlocklight + '|' + pixelatedAo + '|' + pixelatedMode;
        if (!probeKey.equals(shadowOriginProbeKey)) {
            shadowOriginProbeKey = probeKey;
            shadowOriginProbeLogs = 0;
        }
        boolean pixelatedLighting = optionBoolean(shaderProperties, "PIXELATED_SHADOWS", false)
                || optionBoolean(shaderProperties, "PIXELATED_BLOCKLIGHT", false)
                || optionBoolean(shaderProperties, "PIXELATED_AO", false);
        if (pixelatedLighting && shadowOriginProbeLogs < 8) {
            shadowOriginProbeLogs++;
            MainMod.LOGGER.info(
                    "[AUSMShadowOriginProbe] call={} pack={} pixelatedShadows={} shadowMode={} pixelatedBlocklight={} pixelatedAo={} "
                            + "camera={}/{}/{} texel={} requestedInterval={} interval={} "
                            + "worldGridTranslation={}/{}/{} resolution={} distance={}",
                    shadowOriginProbeLogs,
                    activePackName,
                    pixelatedShadows,
                    pixelatedMode,
                    pixelatedBlocklight,
                    pixelatedAo,
                    x, y, z,
                    shadowTexelSize, requestedInterval, interval,
                    snapX, snapY, snapZ,
                    shadowResolution(), shadowMapDistance);
        }
        MatrixState.captureShadowMatrices();
    }

    protected static float shadowAngle(float partialTicks) {
        World world = renderWorld(MinecraftReflectionCompat.minecraft());
        float celestialAngle = world != null ? MinecraftReflectionCompat.worldCelestialAngle(world, partialTicks) : 0.0F;
        float angle = celestialAngle + 0.25F;
        if (angle >= 1.0F) {
            angle -= 1.0F;
        }
        return angle;
    }

    protected ICamera createShadowCamera(Entity viewEntity, float partialTicks) {
        ICamera celeritasCamera = self().createCeleritasShadowCamera(viewEntity, partialTicks);
        if (celeritasCamera != null) {
            return celeritasCamera;
        }
        return self().createVanillaShadowCamera();
    }

    protected ICamera createVanillaShadowCamera() {
        return ALWAYS_VISIBLE_CAMERA;
    }

    protected ICamera createCeleritasShadowCamera(Entity viewEntity, float partialTicks) {
        try {
            if (!self().resolveCeleritasShadowCameraReflection()) {
                return null;
            }

            double[] position = {
                    PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks),
                    PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks),
                    PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks)
            };
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("sodium$createViewport".equals(name)) {
                    Object cameraPosition = celeritasVectorConstructor.newInstance(position[0], position[1], position[2]);
                    return celeritasViewportConstructor.newInstance(celeritasAlwaysVisibleFrustum, cameraPosition);
                }
                if ("isBoundingBoxInFrustum".equals(name) || "func_78546_a".equals(name)) {
                    return true;
                }
                if ("setPosition".equals(name) || "func_78547_a".equals(name)) {
                    if (args != null && args.length == 3) {
                        position[0] = ((Number) args[0]).doubleValue();
                        position[1] = ((Number) args[1]).doubleValue();
                        position[2] = ((Number) args[2]).doubleValue();
                    }
                    return null;
                }
                if ("toString".equals(name)) {
                    return "AUSM Celeritas shadow camera";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return args != null && args.length == 1 && proxy == args[0];
                }
                return null;
            };

            return (ICamera) Proxy.newProxyInstance(
                    PipelineContext.class.getClassLoader(),
                    new Class<?>[]{ICamera.class, celeritasViewportProviderClass},
                    handler
            );
        } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException e) {
            if (!celeritasShadowCameraWarningLogged) {
                celeritasShadowCameraWarningLogged = true;
                MainMod.LOGGER.warn("[Pipeline] Failed to create Celeritas-compatible shadow camera; falling back to vanilla camera", e);
            }
            return null;
        }
    }

    protected boolean resolveCeleritasShadowCameraReflection() throws ReflectiveOperationException {
        if (celeritasShadowCameraResolved) {
            return celeritasViewportProviderClass != null;
        }
        celeritasShadowCameraResolved = true;

        ClassLoader loader = PipelineContext.class.getClassLoader();
        try {
            celeritasViewportProviderClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.ViewportProvider", false, loader);
            Class<?> viewportClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.Viewport", false, loader);
            Class<?> frustumClass = Class.forName(
                    "org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum", false, loader);
            Class<?> vector3dClass = Class.forName(
                    "org.embeddedt.embeddium.impl.shadow.joml.Vector3d", false, loader);
            celeritasViewportConstructor = viewportClass.getConstructor(frustumClass, vector3dClass);
            celeritasVectorConstructor = vector3dClass.getConstructor(double.class, double.class, double.class);
            celeritasAlwaysVisibleFrustum = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{frustumClass},
                    (proxy, method, args) -> boolean.class.equals(method.getReturnType()) ? Boolean.TRUE : null
            );
            return true;
        } catch (ClassNotFoundException e) {
            celeritasViewportProviderClass = null;
            celeritasViewportConstructor = null;
            celeritasVectorConstructor = null;
            celeritasAlwaysVisibleFrustum = null;
            return false;
        }
    }

    protected void renderShadowEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowEntities() && !shaderProperties.renderSettings().shadowPlayer()) {
            return;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null || MinecraftReflectionCompat.entityRenderer(mc) == null) {
            return;
        }
        RenderManager renderManager = MinecraftReflectionCompat.renderManager(mc);
        if (renderManager == null) {
            return;
        }
        double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double entityDistance = Math.min(self().shadowRenderCullDistance(), SHADOW_REALTIME_ENTITY_DISTANCE);
        double entityDistanceSquared = entityDistance * entityDistance;

        MinecraftReflectionCompat.renderManagerCacheActiveRenderInfo(renderManager, world, MinecraftReflectionCompat.fontRenderer(mc), viewEntity, MinecraftReflectionCompat.field(mc, Entity.class, null, "field_147125_j", "pointedEntity"), MinecraftReflectionCompat.gameSettings(mc), partialTicks);
        MinecraftReflectionCompat.renderManagerSetRenderPosition(renderManager, cameraX, cameraY, cameraZ);
        MinecraftReflectionCompat.enableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        MinecraftReflectionCompat.enableStandardItemLighting();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);

        List<Entity> loadedEntities = MinecraftReflectionCompat.loadedEntityList(world);
        if (loadedEntities == null) {
            return;
        }
        for (Entity entity : loadedEntities) {
            if (!self().shouldRenderEntityInShadowMap(mc, world, renderManager, entity, viewEntity, shadowCamera,
                    cameraX, cameraY, cameraZ, entityDistanceSquared)) {
                continue;
            }

            MinecraftReflectionCompat.renderManagerRenderEntityStatic(renderManager, entity, partialTicks, false);
            if (MinecraftReflectionCompat.renderManagerIsRenderMultipass(renderManager, entity)) {
                MinecraftReflectionCompat.renderManagerRenderMultipass(renderManager, entity, partialTicks);
            }
        }
    }

    protected int renderShadowBlockEntitiesDirect(Minecraft mc, Entity viewEntity, ICamera shadowCamera, float partialTicks) {
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !shaderProperties.renderSettings().shadowLightBlockEntities()) {
            return 0;
        }

        World world = renderWorld(mc);
        if (mc == null || world == null || viewEntity == null || shadowCamera == null) {
            return 0;
        }

        TileEntityRendererDispatcher dispatcher = MinecraftReflectionCompat.tileEntityRendererDispatcher();
        if (dispatcher == null) {
            return 0;
        }

        double cameraX = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosX(viewEntity), MinecraftReflectionCompat.posX(viewEntity), partialTicks);
        double cameraY = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosY(viewEntity), MinecraftReflectionCompat.posY(viewEntity), partialTicks);
        double cameraZ = PipelineWorldRenderScope.interpolate(MinecraftReflectionCompat.lastTickPosZ(viewEntity), MinecraftReflectionCompat.posZ(viewEntity), partialTicks);
        double maxDistance = Math.min(self().shadowRenderCullDistance(), SHADOW_REALTIME_BLOCK_ENTITY_DISTANCE);
        double maxDistanceSquared = maxDistance * maxDistance;
        List<TileEntity> tileEntities = self().cpuLightTileEntitySnapshot(world);
        self().refreshShadowBlockEntityBoundsCache(world, tileEntities);

        MinecraftReflectionCompat.tileEntityRendererPrepare(
                dispatcher,
                world,
                MinecraftReflectionCompat.textureManager(mc),
                MinecraftReflectionCompat.fontRenderer(mc),
                viewEntity,
                MinecraftReflectionCompat.field(mc, RayTraceResult.class, null, "field_71476_x", "objectMouseOver"),
                partialTicks
        );
        MinecraftReflectionCompat.enableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        MinecraftReflectionCompat.enableStandardItemLighting();
        PipelineWorldRenderScope.configureShadowTerrainRenderState();

        int rendered = 0;
        for (TileEntity tileEntity : tileEntities) {
            BlockPos pos = MinecraftReflectionCompat.tileEntityPos(tileEntity);
            if (!self().shouldRenderBlockEntityInShadowMap(
                    world, tileEntity, pos, shadowCamera, cameraX, cameraY, cameraZ, maxDistanceSquared)) {
                continue;
            }

            MinecraftReflectionCompat.tileEntityRendererRender(
                    dispatcher,
                    tileEntity,
                    MinecraftReflectionCompat.blockPosX(pos) - cameraX,
                    MinecraftReflectionCompat.blockPosY(pos) - cameraY,
                    MinecraftReflectionCompat.blockPosZ(pos) - cameraZ,
                    partialTicks,
                    -1,
                    1.0F
            );
            rendered++;
        }
        return rendered;
    }
}
