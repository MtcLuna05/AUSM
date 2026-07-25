package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.matrix.MatrixState;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject the core deferred pipeline stages into the main render loop.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    private static boolean ausm$loggedSelectionBoxStateRepair;
    private static boolean ausm$loggedEntityStateRepair;
    private static boolean ausm$loggedEntityBufferRepair;

    @Shadow(remap = false)
    protected void func_78474_d(float partialTicks) {
    }

    @Shadow(remap = false)
    protected void func_78476_b(float partialTicks, int pass) {
    }

    @Inject(method = "func_181560_a(FJ)V", at = @At("HEAD"))
    private void onUpdateCameraAndRenderHead(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$prepareNoWorldCustomMainMenu();
        PipelineContext.getInstance().beginClientRenderFrame(nanoTime);
    }

    @Inject(
            method = "func_181560_a(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78471_a(FJ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterWorldBeforeUi(float partialTicks, long nanoTime, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.captureShaderlessWorldFramebufferForUi();
        context.renderShaderlessBloomBeforeGui();
        context.prepareShaderlessUiRenderingBoundary();
        context.logHiddenSkyFramebufferProbe("post-world-before-ui");
    }

    @Inject(method = "func_181560_a(FJ)V", at = @At("RETURN"))
    private void ausm$probeHiddenSkyAtPresentation(float partialTicks, long nanoTime, CallbackInfo ci) {
        PipelineContext.getInstance().logHiddenSkyFramebufferProbe("frame-return");
    }

    @Inject(
            method = "func_181560_a(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;func_73863_a(IIF)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void onBeforeGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$beginGuiScreenRendering();
    }

    @Inject(
            method = "func_181560_a(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V",
                    shift = At.Shift.BEFORE
            ),
            require = 1
    )
    private void onBeforeForgeGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$beginGuiScreenRendering();
    }

    private void ausm$beginGuiScreenRendering() {
        PipelineContext context = PipelineContext.getInstance();
        context.logGuiBypassProbe("screen-before-routing");
        if (ausm$shouldUseVanillaGuiScreen()) {
            context.prepareBypassedGuiScreenRendering();
            context.logGuiBypassProbe("screen-after-vanilla-bypass");
            return;
        }
        if (!context.isActive()) {
            context.prepareBypassedGuiScreenRendering();
            return;
        }
        context.beginGuiScreenRendering();
    }

    @Inject(
            method = "func_181560_a(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiScreen;func_73863_a(IIF)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void onAfterGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$finishGuiScreenRendering();
    }

    @Inject(
            method = "func_181560_a(FJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V",
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void onAfterForgeGuiScreenDraw(float partialTicks, long nanoTime, CallbackInfo ci) {
        ausm$finishGuiScreenRendering();
    }

    private void ausm$finishGuiScreenRendering() {
        PipelineContext context = PipelineContext.getInstance();
        if (ausm$shouldUseVanillaGuiScreen()) {
            return;
        }
        context.finishGuiScreenRendering();
    }

    private boolean ausm$shouldUseVanillaGuiScreen() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        Object screen = minecraft != null ? MinecraftReflectionCompat.currentScreen(minecraft) : null;
        return screen == null || !ausm$shouldUseManagedAusmGuiScreen(screen);
    }

    private boolean ausm$shouldUseManagedAusmGuiScreen(Object screen) {
        String name = screen.getClass().getName();
        return name.startsWith("com.l.ausm.impl.client.gui.GuiShader")
                || "com.l.ausm.impl.client.gui.GuiDynamicLights".equals(name);
    }

    private void ausm$prepareNoWorldCustomMainMenu() {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (minecraft == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) != null || !ausm$isCustomMainMenu(minecraft)) {
            return;
        }

        if (com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(minecraft) != null) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.bindFramebuffer(com.l.ausm.impl.util.MinecraftReflectionCompat.minecraftFramebuffer(minecraft), false);
            com.l.ausm.impl.util.MinecraftReflectionCompat.glStateViewport(0, 0, com.l.ausm.impl.util.MinecraftReflectionCompat.displayWidth(minecraft), com.l.ausm.impl.util.MinecraftReflectionCompat.displayHeight(minecraft));
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateSetActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateBindTexture(0);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableTexture2D();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableAlpha();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateEnableBlend();
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateDepthMask(true);
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean ausm$isCustomMainMenu(Minecraft minecraft) {
        return com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(minecraft) != null
                && "lumien.custommainmenu.gui.GuiCustom".equals(com.l.ausm.impl.util.MinecraftReflectionCompat.currentScreen(minecraft).getClass().getName());
    }

    @Inject(method = "func_175068_a", at = @At("HEAD"), cancellable = true)
    private void onRenderWorldPassHead(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        if (minecraft == null || com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft) == null || com.l.ausm.impl.util.MinecraftReflectionCompat.renderViewEntity(minecraft) == null) {
            ci.cancel();
            return;
        }

        PipelineContext context = PipelineContext.getInstance();
        MainMod.getShaderPackManager().reloadIfDimensionChanged();
        context.beginWorldPassRendering(pass, partialTicks);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78479_a(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCameraTransform(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        MatrixState.captureGbufferMatrices();
        context.renderPreparePass();
        context.bindWorldFramebuffer();
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174970_a(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.runPendingClientChunkRenderRefreshesForCurrentRenderPass();
        context.updateShaderlessVanillaViewFrustumForCamera();
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        context.ensureRenderGlobalViewFrustum(minecraft != null ? MinecraftReflectionCompat.renderGlobal(minecraft) : null);
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.ensureVanillaTerrainRenderer();
        if (context.shouldRenderShadowMapBeforeTerrainSetup()) {
            context.renderShadowMap(partialTicks);
        }
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174970_a(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterSetupTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        if (context.shouldRenderShadowMapAfterTerrainSetup()) {
            context.renderShadowMap(partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V")
    )
    private void ausm$renderEntitiesIfGbufferRenderingEnabled(RenderGlobal renderGlobal, Entity renderViewEntity, ICamera camera, float partialTicks) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.shouldSkipAllMainGbufferRendering()) {
            ausm$repairEntityTessellatorState();
            ausm$repairEntityClientArrayState();
            com.l.ausm.impl.util.MinecraftReflectionCompat.renderEntities(renderGlobal, renderViewEntity, camera, partialTicks);
        }
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.bindWorldFramebuffer();
        context.applyTerrainCulling(WorldRenderingPhase.TERRAIN_SOLID);
        context.beginPhase(WorldRenderingPhase.TERRAIN_SOLID);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterSolidTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.applyTerrainCulling(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
        context.beginPhase(WorldRenderingPhase.TERRAIN_CUTOUT_MIPPED);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCutoutMippedTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 2,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.applyTerrainCulling(WorldRenderingPhase.TERRAIN_CUTOUT);
        context.beginPhase(WorldRenderingPhase.TERRAIN_CUTOUT);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterCutoutTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
        context.snapshotOpaqueTerrainDepth();
        if (context.shouldRenderShadowMapAfterOpaqueTerrain()) {
            context.renderShadowMap(partialTicks);
        }
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.beginPhase(WorldRenderingPhase.ENTITIES);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_180446_a(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterEntities(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.endPass();
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;func_78872_b(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("before-lit-particles");
        if (context.shouldRenderParticlesWithVanillaState()) {
            context.beginTranslucents();
            context.prepareVanillaParticleRenderingState();
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
        }
        if (context.shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase.PARTICLES)) {
            context.beginTranslucents();
        }
        context.beginPhase(WorldRenderingPhase.PARTICLES);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;func_78872_b(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterLitParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("after-lit-particles");
        if (context.shouldBypassWorldPassRendering() || context.shouldRenderParticlesWithVanillaState()) {
            return;
        }

        context.endPass();
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;func_78874_a(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("before-particles");
        if (context.shouldRenderParticlesWithVanillaState()) {
            context.prepareVanillaParticleRenderingState();
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            context.prepareVanillaParticleRendering();
            return;
        }

        if (!context.isActive()) {
            context.prepareVanillaParticleRendering();
        }
        if (context.shouldRunDeferredBeforeParticlePhase(WorldRenderingPhase.PARTICLES_TRANSLUCENT)) {
            context.beginTranslucents();
        }
        context.beginPhase(WorldRenderingPhase.PARTICLES_TRANSLUCENT);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleManager;func_78874_a(Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterParticles(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("after-particles");
        if (context.shouldBypassWorldPassRendering() || context.shouldRenderParticlesWithVanillaState()) {
            return;
        }

        context.endPass();
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;func_78872_b(Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$renderLitParticlesIfGbufferRenderingEnabled(ParticleManager particleManager, Entity entity, float partialTicks) {
        PipelineContext.getInstance().logSpecialLayerProbe("lit-particles-redirect");
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.renderLitParticles(particleManager, entity, partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;func_78874_a(Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$renderParticlesIfGbufferRenderingEnabled(ParticleManager particleManager, Entity entity, float partialTicks) {
        PipelineContext.getInstance().logSpecialLayerProbe("particles-redirect");
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.renderParticles(particleManager, entity, partialTicks);
        }
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174981_a(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.beginPhase(WorldRenderingPhase.DESTROY);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174981_a(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterBlockDamage(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.endPass();
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174981_a(Lnet/minecraft/client/renderer/Tessellator;Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$drawBlockDamageIfGbufferRenderingEnabled(RenderGlobal renderGlobal, Tessellator tessellator, BufferBuilder bufferBuilder, Entity entity, float partialTicks) {
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.drawBlockDamageTexture(renderGlobal, tessellator, bufferBuilder, entity, partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_72731_b(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V")
    )
    private void ausm$drawSelectionBoxWithRepairedGlState(RenderGlobal renderGlobal, EntityPlayer player, RayTraceResult target, int execute, float partialTicks) {
        ausm$repairSelectionBoxClientArrayState();
        com.l.ausm.impl.util.MinecraftReflectionCompat.drawSelectionBox(renderGlobal, player, target, execute, partialTicks);
    }

    private static void ausm$repairSelectionBoxClientArrayState() {
        if (!ausm$loggedSelectionBoxStateRepair) {
            ausm$loggedSelectionBoxStateRepair = true;
            MainMod.LOGGER.warn("[AUSMSelectionBox] Repairing fixed-function client-array state before vanilla selection-box draw.");
        }

        FixedFunctionGlState.resetClientArrayState(true);
    }

    private static void ausm$repairEntityClientArrayState() {
        if (!ausm$loggedEntityStateRepair) {
            ausm$loggedEntityStateRepair = true;
            MainMod.LOGGER.warn("[AUSMEntityRender] Repairing client-array/VAO state before vanilla entity rendering.");
        }

        FixedFunctionGlState.resetClientArrayState(false);
    }

    private static void ausm$repairEntityTessellatorState() {
        Tessellator tessellator = MinecraftReflectionCompat.tessellator();
        BufferBuilder buffer = MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        if (!MinecraftReflectionCompat.bufferIsDrawing(buffer)) {
            return;
        }

        if (!ausm$loggedEntityBufferRepair) {
            ausm$loggedEntityBufferRepair = true;
            MainMod.LOGGER.warn("[AUSMEntityRender] Resetting an unexpectedly open shared BufferBuilder before entity rendering.");
        }
        MinecraftReflectionCompat.forceResetBufferDrawingState(buffer);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78474_d(F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        if (!context.shouldRenderWeather()) {
            return;
        }

        context.beginTranslucents();
        context.applyWeatherRenderState();
        context.beginPhase(WorldRenderingPhase.RAIN_SNOW);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78474_d(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterWeather(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        if (!context.shouldRenderWeather()) {
            return;
        }

        context.endPass();
        context.restoreWeatherRenderState();
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78474_d(F)V")
    )
    private void ausm$renderWeatherIfEnabled(EntityRenderer renderer, float partialTicks) {
        if (PipelineContext.getInstance().shouldRenderWeather()) {
            func_78474_d(partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78476_b(FI)V")
    )
    private void ausm$renderHandIfGbufferRenderingEnabled(EntityRenderer renderer, float partialTicks, int pass) {
        PipelineContext.getInstance().logSpecialLayerProbe("before-hand-redirect");
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            PipelineContext.getInstance().prepareVanillaHandRenderState();
            func_78476_b(partialTicks, pass);
        }
        PipelineContext.getInstance().logSpecialLayerProbe("after-hand-redirect");
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174967_a(J)V")
    )
    private void ausm$skipBetterPortalsNestedChunkUpdates(RenderGlobal renderGlobal, long finishTimeNano) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.prepareRenderGlobalChunkUpdates(renderGlobal)) {
            try {
                com.l.ausm.impl.util.MinecraftReflectionCompat.updateChunks(renderGlobal, finishTimeNano);
            } catch (NullPointerException e) {
                if (!context.handleBetterPortalsChunkUpdateFailure(renderGlobal, e)) {
                    throw e;
                }
            }
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I")
    )
    private int ausm$renderWorldBlockLayer(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks, int pass, Entity viewEntity) {
        return PipelineContext.getInstance().renderWorldBlockLayer(renderGlobal, layer, partialTicks, pass, viewEntity);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 3,
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }

        context.beginTranslucents();
        context.applyWaterRenderState();
        context.applyTerrainCulling(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        context.beginPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174977_a(Lnet/minecraft/util/BlockRenderLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 3,
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterTranslucentTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldBypassWorldPassRendering()) {
            context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
            context.renderShaderlessVisibleBloomLayerFromWorldPass(partialTicks, pass);
            return;
        }

        context.endPass();
        context.restoreTerrainCulling();
        context.restoreWaterRenderState();
        context.renderNativeAusmBloomLayerFromWorldPass(partialTicks, pass);
        context.renderShaderlessVisibleBloomLayerFromWorldPass(partialTicks, pass);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78476_b(FI)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderWorldPassBeforeHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("before-hand");
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        if (!context.isActive()) {
            context.prepareVanillaHandRenderState();
            return;
        }

        context.beginHand();
        context.beginPhase(WorldRenderingPhase.HAND_SOLID);
    }

    @Inject(
            method = "func_175068_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78476_b(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void onRenderWorldPassAfterHand(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logSpecialLayerProbe("after-hand");
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        if (!context.isActive()) {
            return;
        }

        context.finishHand();
        context.endPass();
    }

    @Inject(method = "func_175068_a", at = @At("RETURN"))
    private void onRenderWorldPassReturn(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.finishWorldPassRendering();
    }
}
