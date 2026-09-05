package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.client.AusmGuiRenderController;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.GlobalFacadesTerrainBridge;
import com.luna.ausm.impl.pipeline.matrix.MatrixState;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to inject the core deferred pipeline stages into the main render loop.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    private static final int AUSM_WEATHER_RENDER_RADIUS = Math.clamp(
            Integer.getInteger("ausm.weatherRenderRadius", 7), 5, 10);
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
        AusmGuiRenderController.beginFrame(nanoTime);
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
        AusmGuiRenderController.completeWorldBeforeGui();
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
        AusmGuiRenderController.beginScreen();
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
        AusmGuiRenderController.endScreen();
    }

    @Inject(method = "func_175068_a", at = @At("HEAD"), cancellable = true)
    private void onRenderWorldPassHead(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft == null || MinecraftReflectionCompat.world(minecraft) == null || MinecraftReflectionCompat.renderViewEntity(minecraft) == null) {
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
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78466_h(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void ausm$syncShaderlessDriverClearColorAfterFogUpdate(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isActive()) {
            return;
        }

        // Vanilla has just updated GlStateManager's cached fog/clear colour.
        // Shaderless post-world passes use raw GL11 clears; with F1 the skipped
        // HUD path can leave the driver clear colour stale even though that cache
        // remains correct. Align the driver before vanilla clears this frame.
        GL11.glClearColor(
                MinecraftReflectionCompat.fieldFloat(this, 0.0F, "field_175080_Q", "fogColorRed"),
                MinecraftReflectionCompat.fieldFloat(this, 0.0F, "field_175082_R", "fogColorGreen"),
                MinecraftReflectionCompat.fieldFloat(this, 0.0F, "field_175081_S", "fogColorBlue"),
                0.0F
        );
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
            MinecraftReflectionCompat.renderEntities(renderGlobal, renderViewEntity, camera, partialTicks);
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
        // Facades are surface replacements, not a late translucent overlay.
        // Submit them before this terrain pass closes so shadered facades write
        // the same depth attachment that is snapshotted for later particles,
        // screen-space effects, and deferred passes. Shaderless rendering uses
        // the same fixed-function boundary.
        GlobalFacadesTerrainBridge.render(partialTicks);
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
            MinecraftReflectionCompat.renderLitParticles(particleManager, entity, partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleManager;func_78874_a(Lnet/minecraft/entity/Entity;F)V")
    )
    private void ausm$renderParticlesIfGbufferRenderingEnabled(ParticleManager particleManager, Entity entity, float partialTicks) {
        PipelineContext.getInstance().logSpecialLayerProbe("particles-redirect");
        if (!PipelineContext.getInstance().shouldSkipAllMainGbufferRendering()) {
            MinecraftReflectionCompat.renderParticles(particleManager, entity, partialTicks);
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
            MinecraftReflectionCompat.drawBlockDamageTexture(renderGlobal, tessellator, bufferBuilder, entity, partialTicks);
        }
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_72731_b(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/math/RayTraceResult;IF)V")
    )
    private void ausm$drawSelectionBoxWithRepairedGlState(RenderGlobal renderGlobal, EntityPlayer player, RayTraceResult target, int execute, float partialTicks) {
        ausm$repairSelectionBoxClientArrayState();
        MinecraftReflectionCompat.drawSelectionBox(renderGlobal, player, target, execute, partialTicks);
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
        if (!context.isActive()) {
            return;
        }
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
        if (!context.isActive()) {
            return;
        }
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

    @Inject(method = "func_78484_h", at = @At("HEAD"), cancellable = true)
    private void ausm$skipDisabledWeatherParticles(CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderWeatherParticles()) {
            ci.cancel();
        }
    }

    /**
     * Fancy vanilla weather performs biome and precipitation-height lookups
     * for a 21-by-21 column square every rendered frame. A radius of seven
     * keeps dense nearby rain while reducing that hot loop from 441 to 225
     * columns. The JVM property allows packs to restore ten or choose any
     * value from five through ten without another mixin/config fork.
     */
    @ModifyConstant(
            method = "func_78474_d",
            constant = @Constant(intValue = 10, ordinal = 0)
    )
    private int ausm$capFancyWeatherRenderRadius(int vanillaRadius) {
        return Math.min(vanillaRadius, AUSM_WEATHER_RENDER_RADIUS);
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;func_78476_b(FI)V")
    )
    private void ausm$renderHandIfGbufferRenderingEnabled(EntityRenderer renderer, float partialTicks, int pass) {
        PipelineContext context = PipelineContext.getInstance();
        context.logHandGbufferProbe("before-hand-redirect");
        if (!context.shouldSkipAllMainGbufferRendering()) {
            context.prepareVanillaHandRenderState();
            context.logHandGbufferProbe("hand-bound");
            func_78476_b(partialTicks, pass);
        }
        context.logHandGbufferProbe("after-hand-redirect");
    }

    @Redirect(
            method = "func_175068_a",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderGlobal;func_174967_a(J)V")
    )
    private void ausm$skipBetterPortalsNestedChunkUpdates(RenderGlobal renderGlobal, long finishTimeNano) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.prepareRenderGlobalChunkUpdates(renderGlobal)) {
            try {
                MinecraftReflectionCompat.updateChunks(renderGlobal, finishTimeNano);
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
                    // Celeritas-compatible render transforms may replace the
                    // RenderGlobal invocation itself. The TRANSLUCENT layer
                    // constant remains immediately before that draw, so use it
                    // as the stable phase boundary instead of the fourth call.
                    value = "FIELD",
                    target = "Lnet/minecraft/util/BlockRenderLayer;TRANSLUCENT:Lnet/minecraft/util/BlockRenderLayer;",
                    opcode = Opcodes.GETSTATIC,
                    shift = At.Shift.BEFORE
            ),
            require = 1
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

    private void ausm$finishTranslucentTerrain(int pass, float partialTicks) {
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
        // Celeritas replaces the final RenderGlobal layer invocation, so an
        // AFTER hook on vanilla's fourth call is never reached. The hand is
        // the next stable world-pass boundary: close the water program here
        // before it can leak into hand, weather, or presentation rendering.
        if (context.getPhase() == WorldRenderingPhase.TERRAIN_TRANSLUCENT) {
            ausm$finishTranslucentTerrain(pass, partialTicks);
        }
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
        context.logHandGbufferProbe("before-hand");
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
