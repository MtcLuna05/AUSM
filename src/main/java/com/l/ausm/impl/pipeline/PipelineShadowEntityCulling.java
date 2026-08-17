package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.fbo.DeferredFramebuffer;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import static com.l.ausm.impl.pipeline.PipelineDistantHorizonsConstants.ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS;

abstract class PipelineShadowEntityCulling extends PipelineShadowCamera {
    protected boolean shouldRenderBlockEntityInShadowMap(World world, TileEntity tileEntity, BlockPos pos, ICamera shadowCamera,
                                                         double cameraX, double cameraY, double cameraZ,
                                                         double maxDistanceSquared) {
        if (world == null || tileEntity == null
                || MinecraftReflectionCompat.tileEntityInvalid(tileEntity)) {
            return false;
        }

        if (pos == null || !MinecraftReflectionCompat.worldIsBlockLoaded(world, pos, false)) {
            return false;
        }
        if (!shaderProperties.renderSettings().shadowBlockEntities()
                && !PipelineWorldRenderScope.isLightEmittingBlockEntity(world, tileEntity, pos)) {
            return false;
        }

        double dx = MinecraftReflectionCompat.blockPosX(pos) + 0.5D - cameraX;
        double dy = MinecraftReflectionCompat.blockPosY(pos) + 0.5D - cameraY;
        double dz = MinecraftReflectionCompat.blockPosZ(pos) + 0.5D - cameraZ;
        if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
            return false;
        }

        AxisAlignedBB box = self().cachedShadowBlockEntityFrustumBox(tileEntity, pos);
        return box == null || MinecraftReflectionCompat.cameraIsBoundingBoxInFrustum(shadowCamera, box);
    }

    protected void refreshShadowBlockEntityBoundsCache(World world, List<TileEntity> tileEntities) {
        if (shadowBlockEntityBoundsCacheWorld != world) {
            shadowBlockEntityBoundsCacheWorld = world;
            shadowBlockEntityBoundsCache.clear();
            return;
        }
        int expectedSize = tileEntities != null ? tileEntities.size() : 0;
        if (shadowBlockEntityBoundsCache.size() > expectedSize * 2 + 256) {
            shadowBlockEntityBoundsCache.clear();
        }
    }

    protected AxisAlignedBB cachedShadowBlockEntityFrustumBox(TileEntity tileEntity, BlockPos pos) {
        if (tileEntity == null || pos == null) {
            return null;
        }
        int x = MinecraftReflectionCompat.blockPosX(pos);
        int y = MinecraftReflectionCompat.blockPosY(pos);
        int z = MinecraftReflectionCompat.blockPosZ(pos);
        ShadowBlockEntityBounds cached = shadowBlockEntityBoundsCache.get(tileEntity);
        if (cached != null && cached.x() == x && cached.y() == y && cached.z() == z) {
            return cached.bounds();
        }

        AxisAlignedBB bounds = new AxisAlignedBB(x - 1.0D, y - 1.0D, z - 1.0D, x + 2.0D, y + 2.0D, z + 2.0D);
        shadowBlockEntityBoundsCache.put(tileEntity, new ShadowBlockEntityBounds(x, y, z, bounds));
        return bounds;
    }

    protected static boolean isLightEmittingBlockEntity(World world, TileEntity tileEntity, BlockPos pos) {
        try {
            IBlockState state = MinecraftReflectionCompat.worldBlockState(world, pos);
            return state != null && MinecraftReflectionCompat.stateLightValue(state, world, pos) > 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    protected boolean shouldRenderEntityInShadowMap(Minecraft mc, World world, RenderManager renderManager, Entity entity, Entity viewEntity,
                                                    ICamera shadowCamera, double cameraX, double cameraY, double cameraZ,
                                                    double maxDistanceSquared) {
        if (mc == null || world == null || renderManager == null || entity == null || MinecraftReflectionCompat.entityIsDead(entity) || !MinecraftReflectionCompat.shouldRenderInPass(entity, 0)) {
            return false;
        }
        if (BetterPortalsCompat.isPortalEntity(entity)) {
            return false;
        }
        if (entity instanceof AbstractClientPlayer player && MinecraftReflectionCompat.playerIsSpectator(player)) {
            return false;
        }
        if (entity == viewEntity
                && !shaderProperties.renderSettings().shadowEntities()
                && !shaderProperties.renderSettings().shadowPlayer()) {
            return false;
        }
        if (entity != viewEntity && !shaderProperties.renderSettings().shadowEntities()) {
            return false;
        }
        if (!MinecraftReflectionCompat.renderManagerShouldRender(renderManager, entity, shadowCamera, cameraX, cameraY, cameraZ)
                && (MinecraftReflectionCompat.player(mc) == null || !MinecraftReflectionCompat.entityIsRidingOrBeingRiddenBy(entity, MinecraftReflectionCompat.player(mc)))) {
            return false;
        }
        double entityY = MinecraftReflectionCompat.posY(entity);
        if (entityY >= 0.0D && entityY < 256.0D && !MinecraftReflectionCompat.worldIsBlockLoaded(world, new BlockPos(
                MinecraftReflectionCompat.posX(entity),
                entityY,
                MinecraftReflectionCompat.posZ(entity)))) {
            return false;
        }
        double dx = MinecraftReflectionCompat.posX(entity) - cameraX;
        double dy = entityY - cameraY;
        double dz = MinecraftReflectionCompat.posZ(entity) - cameraZ;
        if (maxDistanceSquared >= 0.0D && dx * dx + dy * dy + dz * dz > maxDistanceSquared) {
            return false;
        }
        return MinecraftReflectionCompat.entityIsInRangeToRender3d(entity, cameraX, cameraY, cameraZ);
    }

    protected static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    protected static int eyeFluidState(Minecraft mc) {
        if (mc == null) {
            return 0;
        }
        Entity viewEntity = MinecraftReflectionCompat.renderViewEntity(mc);
        World world = renderWorld(mc);
        if (world == null || viewEntity == null) {
            return 0;
        }

        IBlockState cameraState = MinecraftReflectionCompat.blockStateAtEntityViewpoint(world, viewEntity, MinecraftReflectionCompat.renderPartialTicks(mc));
        if (MinecraftReflectionCompat.stateMaterialIsWater(cameraState)) {
            return 1;
        }
        if (MinecraftReflectionCompat.stateMaterial(cameraState) == MinecraftReflectionCompat.field(Material.class, Material.class, null, "field_151587_i", "LAVA") && !MinecraftReflectionCompat.playerIsSpectator(MinecraftReflectionCompat.player(mc))) {
            return 2;
        }
        return 0;
    }

    public void bindWorldFramebuffer() {
        if (!isPipelineActive || !pingPongManager.isInitialized()) {
            return;
        }

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        self().restoreVanillaWorldTextureBindings();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        PipelineWorldRenderScope.resetPortalMaskState();
    }

    protected int currentPipelineWorldFramebufferId() {
        DeferredFramebuffer framebuffer = pingPongManager.getReadBuffer();
        return framebuffer != null ? framebuffer.getFramebufferId() : 0;
    }

    public boolean shouldUseDistantHorizonsFramebufferOverride() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return true;
        }
        return ENABLE_DISTANT_HORIZONS_DIRECT_SHADER_RENDER
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && MinecraftReflectionCompat.minecraft() != null
                && MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) != null;
    }

    protected boolean shouldCompositeDistantHorizonsFramebuffer() {
        return isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && !renderingShadowMap
                && !renderingGuiScreen()
                && MinecraftReflectionCompat.minecraft() != null
                && MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) != null;
    }

    public boolean shouldSuppressDistantHorizonsMinecraftApply() {
        return self().shouldUseDistantHorizonsFramebufferOverride() || self().shouldProtectDistantHorizonsNativeApply();
    }

    protected boolean shouldProtectDistantHorizonsNativeApply() {
        return MainMod.getShaderPackManager() != null
                && MainMod.getShaderPackManager().shouldProtectDistantHorizonsNativeApply()
                && isPipelineActive
                && worldFrameActive
                && pingPongManager.isInitialized()
                && MinecraftReflectionCompat.minecraft() != null
                && MinecraftReflectionCompat.world(MinecraftReflectionCompat.minecraft()) != null;
    }

    public void logDistantHorizonsApiCallback(String method, String detail) {
        self().logDistantHorizonsDiagnostic("api-" + method, detail + ", " + self().distantHorizonsProbeState(null));
    }

    public void logDistantHorizonsHook(String stage, Object renderParam) {
        self().updateDistantHorizonsRenderPass(renderParam);
        self().logDistantHorizonsDiagnostic(stage, self().distantHorizonsProbeState(renderParam));
    }

    public void renderDistantHorizonsLods(float partialTicks) {
    }

    public void resetDistantHorizonsDiagnostics(String reason) {
        distantHorizonsDiagnosticLogs = 0;
        self().logDistantHorizonsDiagnostic("probe-reset", reason + ", " + self().distantHorizonsProbeState(null));
    }

    public boolean applyDistantHorizonsToPipeline(Object renderParam) {
        if (self().shouldUseDistantHorizonsFramebufferOverride() || !self().shouldCompositeDistantHorizonsFramebuffer()) {
            self().logDistantHorizonsDiagnostic("native-apply-bypass", self().distantHorizonsProbeState(renderParam));
            return false;
        }

        self().updateDistantHorizonsRenderPass(renderParam);
        int colorTexture = self().activeDistantHorizonsTextureId("getActiveColorTextureId");
        int depthTexture = self().activeDistantHorizonsTextureId("getActiveDepthTextureId");
        if (colorTexture <= 0 || depthTexture <= 0) {
            self().logDistantHorizonsDiagnostic("native-apply-skip", "color=" + colorTexture + ", depth=" + depthTexture);
            return false;
        }

        distantHorizonsFramebufferId = 0;
        distantHorizonsColorTextureId = colorTexture;
        distantHorizonsDepthTextureId = depthTexture;
        distantHorizonsTexturesOwned = false;
        distantHorizonsFramebufferWidth = Math.max(1, pingPongManager.width());
        distantHorizonsFramebufferHeight = Math.max(1, pingPongManager.height());
        distantHorizonsFramebufferPendingComposite = true;
        return true;
    }

    protected void updateDistantHorizonsRenderPass(Object renderParam) {
        if (renderParam == null) {
            return;
        }
        try {
            Object renderPass = renderParam.getClass().getField("renderPass").get(renderParam);
            currentDistantHorizonsPass = renderPass != null && "TRANSPARENT".equals(renderPass.toString())
                    ? RenderPass.DH_WATER
                    : RenderPass.DH_TERRAIN;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    protected int activeDistantHorizonsTextureId(String getterName) {
        try {
            Class<?> metaRendererClass = Class.forName("com.seibel.distanthorizons.common.render.openGl.GlDhMetaRenderer");
            Object instance = metaRendererClass.getField("INSTANCE").get(null);
            Object value = metaRendererClass.getMethod(getterName).invoke(instance);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    public int distantHorizonsFramebufferId() {
        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            return MinecraftReflectionCompat.framebufferObject(distantHorizonsPresentationTarget);
        }
        return self().shouldUseDistantHorizonsFramebufferOverride() ? self().currentPipelineWorldFramebufferId() : 0;
    }

    public int distantHorizonsFramebufferStatus() {
        if (!self().shouldUseDistantHorizonsFramebufferOverride()) {
            return GL30.GL_FRAMEBUFFER_COMPLETE;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            self().bindDistantHorizonsFramebuffer();
            return GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
        }
    }

    public void bindDistantHorizonsFramebuffer() {
        if (!self().shouldUseDistantHorizonsFramebufferOverride()) {
            return;
        }

        if (renderingDistantHorizonsPresentation && distantHorizonsPresentationTarget != null) {
            Framebuffer target = distantHorizonsPresentationTarget;
            MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), MinecraftReflectionCompat.framebufferObject(target));
            GL11.glDrawBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, MinecraftReflectionCompat.minecraft()), framebufferHeight(target, MinecraftReflectionCompat.minecraft()));
            GL11.glColorMask(true, true, true, true);
            MinecraftReflectionCompat.glStateEnableDepth();
            MinecraftReflectionCompat.glStateDepthMask(false);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            MinecraftReflectionCompat.glStateDisableBlend();
            MinecraftReflectionCompat.glStateDisableCull();
            self().logDistantHorizonsDiagnostic("bind-presentation", self().distantHorizonsProbeState(null));
            return;
        }

        pingPongManager.bindForGbuffers(fallbackColorAttachment());
        self().restoreVanillaWorldTextureBindings();
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        distantHorizonsFramebufferPendingComposite = false;
        distantHorizonsColorTextureId = 0;
        distantHorizonsDepthTextureId = 0;
        self().logDistantHorizonsDiagnostic("bind-world", self().distantHorizonsProbeState(null));
    }

    public void compositeDistantHorizonsFramebuffer() {
        self().compositeDistantHorizonsFramebuffer(null);
    }

    protected void compositeDistantHorizonsFramebuffer(Framebuffer target) {
        if (!distantHorizonsFramebufferPendingComposite
                || !self().shouldCompositeDistantHorizonsFramebuffer()
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDepthTextureId == 0
                || !self().ensureDistantHorizonsCompositeProgram()) {
            return;
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean previousAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean previousCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        try {
            String sample = self().sampleDistantHorizonsColorTexture();
            if (target != null) {
                MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), MinecraftReflectionCompat.framebufferObject(target));
                MinecraftReflectionCompat.glStateViewport(0, 0, framebufferWidth(target, MinecraftReflectionCompat.minecraft()), framebufferHeight(target, MinecraftReflectionCompat.minecraft()));
            } else {
                pingPongManager.bindForGbuffers(fallbackColorAttachment());
                MinecraftReflectionCompat.glStateViewport(0, 0, pingPongManager.width(), pingPongManager.height());
            }
            GL11.glDrawBuffer(target != null && MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glReadBuffer(target != null && MinecraftReflectionCompat.framebufferObject(target) == 0 ? GL11.GL_BACK : GL30.GL_COLOR_ATTACHMENT0);
            GL11.glColorMask(true, true, true, true);
            MinecraftReflectionCompat.glStateEnableDepth();
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            MinecraftReflectionCompat.glStateDepthMask(false);
            MinecraftReflectionCompat.glStateDisableCull();
            MinecraftReflectionCompat.glStateEnableBlend();
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA
            );
            MinecraftReflectionCompat.glUseProgram(distantHorizonsCompositeProgramId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            MinecraftReflectionCompat.glStateBindTexture(distantHorizonsColorTextureId);
            if (distantHorizonsCompositeTextureUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeTextureUniform, 0);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            MinecraftReflectionCompat.glStateBindTexture(distantHorizonsDepthTextureId);
            if (distantHorizonsCompositeDepthUniform >= 0) {
                GL20.glUniform1i(distantHorizonsCompositeDepthUniform, 1);
            }
            self().drawDistantHorizonsCompositeQuad();
            String targetSample = self().sampleDistantHorizonsCompositeTarget(target);
            distantHorizonsFramebufferPendingComposite = false;
            self().logDistantHorizonsDiagnostic("composite", "pass=" + currentDistantHorizonsPass
                    + ", texture=" + distantHorizonsColorTextureId
                    + ", sample=" + sample
                    + ", targetSample=" + targetSample);
        } finally {
            MinecraftReflectionCompat.glUseProgram(previousProgram);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            if (previousDepthTest) {
                MinecraftReflectionCompat.glStateEnableDepth();
            } else {
                MinecraftReflectionCompat.glStateDisableDepth();
            }
            GL11.glDepthFunc(previousDepthFunc);
            MinecraftReflectionCompat.glStateDepthMask(previousDepthMask);
            if (previousBlend) {
                MinecraftReflectionCompat.glStateEnableBlend();
            } else {
                MinecraftReflectionCompat.glStateDisableBlend();
            }
            if (previousAlpha) {
                MinecraftReflectionCompat.glStateEnableAlpha();
            } else {
                MinecraftReflectionCompat.glStateDisableAlpha();
            }
            if (previousCull) {
                MinecraftReflectionCompat.glStateEnableCull();
            } else {
                MinecraftReflectionCompat.glStateDisableCull();
            }
            TextureBinder.restoreDefaultTextureUnit();
        }
    }

    protected void drawDistantHorizonsCompositeQuad() {
        MinecraftReflectionCompat.glBindBuffer(MinecraftReflectionCompat.fieldInt(OpenGlHelper.class, GL15.GL_ARRAY_BUFFER, "field_176089_P", "GL_ARRAY_BUFFER"), 0);
        if (GLContext.getCapabilities().OpenGL30) {
            GL30.glBindVertexArray(0);
        }
        for (int attribute = 0; attribute < 16; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(-1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, -1.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(-1.0F, 1.0F);
        GL11.glEnd();
    }

    protected boolean ensureDistantHorizonsFramebuffer() {
        int width = Math.max(1, pingPongManager.width());
        int height = Math.max(1, pingPongManager.height());
        if (distantHorizonsFramebufferId != 0
                && distantHorizonsFramebufferWidth == width
                && distantHorizonsFramebufferHeight == height) {
            return true;
        }

        distantHorizonsFramebufferWidth = width;
        distantHorizonsFramebufferHeight = height;
        distantHorizonsFramebufferId = MinecraftReflectionCompat.glGenFramebuffers();
        distantHorizonsColorTextureId = GL11.glGenTextures();
        distantHorizonsDepthTextureId = GL11.glGenTextures();
        distantHorizonsTexturesOwned = true;

        MinecraftReflectionCompat.glStateBindTexture(distantHorizonsColorTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);

        MinecraftReflectionCompat.glStateBindTexture(distantHorizonsDepthTextureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (FloatBuffer) null);

        MinecraftReflectionCompat.glBindFramebuffer(MinecraftReflectionCompat.glFramebuffer(), distantHorizonsFramebufferId);
        MinecraftReflectionCompat.glFramebufferTexture2D(MinecraftReflectionCompat.glFramebuffer(), GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
        MinecraftReflectionCompat.glFramebufferTexture2D(MinecraftReflectionCompat.glFramebuffer(), GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, distantHorizonsDepthTextureId, 0);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        if (!complete) {
            MainMod.LOGGER.warn("[DistantHorizons] AUSM intermediate framebuffer is incomplete.");
        }
        TextureBinder.restoreDefaultTextureUnit();
        return complete;
    }

    protected void clearDistantHorizonsFramebuffer() {
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        MinecraftReflectionCompat.glStateClearDepth(1.0D);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    protected String sampleDistantHorizonsColorTexture() {
        if (distantHorizonsFramebufferWidth <= 0
                || distantHorizonsFramebufferHeight <= 0
                || distantHorizonsColorTextureId == 0
                || distantHorizonsDiagnosticLogs >= MAX_DISTANT_HORIZONS_DIAGNOSTIC_LOGS) {
            return "skipped";
        }

        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousReadBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            int readFramebuffer = distantHorizonsFramebufferId;
            if (readFramebuffer == 0) {
                readFramebuffer = self().ensureDistantHorizonsTextureReadbackFramebuffer();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                MinecraftReflectionCompat.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, distantHorizonsColorTextureId, 0);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            }
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                return "read-fbo-incomplete:" + status;
            }
            int[][] points = new int[][]{
                    {distantHorizonsFramebufferWidth / 2, distantHorizonsFramebufferHeight / 2},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight / 4)},
                    {distantHorizonsFramebufferWidth / 2, Math.max(0, distantHorizonsFramebufferHeight * 3 / 4)}
            };
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                distantHorizonsReadbackPixel.clear();
                GL11.glReadPixels(points[i][0], points[i][1], 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, distantHorizonsReadbackPixel);
                int r = distantHorizonsReadbackPixel.get(0) & 0xFF;
                int g = distantHorizonsReadbackPixel.get(1) & 0xFF;
                int b = distantHorizonsReadbackPixel.get(2) & 0xFF;
                int a = distantHorizonsReadbackPixel.get(3) & 0xFF;
                if (i > 0) {
                    builder.append(';');
                }
                builder.append(points[i][0]).append(',').append(points[i][1])
                        .append('=').append(r).append('/').append(g).append('/').append(b).append('/').append(a);
            }
            return builder.toString();
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
            restoreReadBufferForFramebuffer(previousReadFramebuffer, previousReadBuffer);
        }
    }

    protected int ensureDistantHorizonsTextureReadbackFramebuffer() {
        if (distantHorizonsTextureReadbackFramebufferId == 0) {
            distantHorizonsTextureReadbackFramebufferId = MinecraftReflectionCompat.glGenFramebuffers();
        }
        return distantHorizonsTextureReadbackFramebufferId;
    }
}
