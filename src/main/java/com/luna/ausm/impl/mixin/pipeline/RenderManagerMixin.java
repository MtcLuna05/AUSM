package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderManager.class)
public class RenderManagerMixin {
    private static final int AUSM_MAX_BETWEENLANDS_RENDER_LOGS = 0;
    private static int ausm$betweenlandsRenderLogCount;

    @Inject(
            method = "renderEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.BEFORE)
    )
    private void ausm$beforeRenderEntity(Entity entity, double x, double y, double z, float entityYaw, float partialTicks, boolean debugBoundingBox, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isInventoryEntityPreview(entity, x, y, z)) {
            context.prepareGuiEntityPreviewRenderState();
            return;
        }
        if (context.isRenderingGuiScreen()) {
            return;
        }
        if (!context.isActive()) {
            return;
        }
        ausm$normalizeEntityFaceCulling();
        if (!context.shouldBypassWorldPassRendering()) {
            if (BetterPortalsCompat.isPortalEntity(entity)) {
                context.prepareExternalWorldOverlayRender();
                return;
            }
            context.setCurrentEntity(entity);
            boolean betweenlandsVanillaProgram = context.shouldRenderEntityWithVanillaProgram(entity);
            if (betweenlandsVanillaProgram) {
                context.prepareExternalWorldOverlayRender();
            }
            ausm$logBetweenlandsRender("renderEntity", context, entity, betweenlandsVanillaProgram);
        }
    }

    @Inject(
            method = "renderEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterRenderEntity(Entity entity, double x, double y, double z, float entityYaw, float partialTicks, boolean debugBoundingBox, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isInventoryEntityPreview(entity, x, y, z)) {
            context.finishGuiEntityPreviewRenderState();
            return;
        }
        if (!context.isActive()) {
            return;
        }
        if (context.isRenderingGuiScreen()) {
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        boolean portalEntity = BetterPortalsCompat.isPortalEntity(entity);
        boolean vanillaProgramEntity = context.shouldRenderEntityWithVanillaProgram(entity);
        if (vanillaProgramEntity) {
            context.finishExternalWorldOverlayRender("Betweenlands entity");
        }
        if (portalEntity || vanillaProgramEntity) {
            context.restoreActiveWorldPassAfterExternalShader();
        }
        context.clearCurrentEntity();
    }

    @Inject(
            method = "renderMultipass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;renderMultipass(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.BEFORE)
    )
    private void ausm$beforeRenderMultipass(Entity entity, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiScreen()) {
            return;
        }
        if (!context.isActive()) {
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        ausm$normalizeEntityFaceCulling();
        if (BetterPortalsCompat.isPortalEntity(entity)) {
            context.prepareExternalWorldOverlayRender();
            return;
        }
        context.setCurrentEntity(entity);
        if (context.shouldSeparateEntityDraws()) {
            context.beginTranslucents();
        }
        context.beginPhase(WorldRenderingPhase.ENTITIES_TRANSLUCENT);
        if (context.shouldRenderEntityWithVanillaProgram(entity)) {
            context.prepareExternalWorldOverlayRender();
        }
        ausm$logBetweenlandsRender("renderMultipass", context, entity, context.shouldRenderEntityWithVanillaProgram(entity));
    }

    @Inject(
            method = "renderMultipass",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;renderMultipass(Lnet/minecraft/entity/Entity;DDDFF)V", shift = At.Shift.AFTER)
    )
    private void ausm$afterRenderMultipass(Entity entity, float partialTicks, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.isRenderingGuiScreen()) {
            return;
        }
        if (!context.isActive()) {
            return;
        }
        if (context.shouldBypassWorldPassRendering()) {
            return;
        }
        boolean portalEntity = BetterPortalsCompat.isPortalEntity(entity);
        if (portalEntity) {
            context.restoreActiveWorldPassAfterExternalShader();
            context.clearCurrentEntity();
            return;
        }
        if (context.shouldRenderEntityWithVanillaProgram(entity)) {
            context.finishExternalWorldOverlayRender("Betweenlands entity multipass");
            context.restoreActiveWorldPassAfterExternalShader();
        }
        context.endPass();
        context.clearCurrentEntity();
    }

    private static void ausm$logBetweenlandsRender(String stage, PipelineContext context, Entity entity, boolean vanillaProgram) {
        ResourceLocation key = entity != null ? MinecraftReflectionCompat.entityKey(entity) : null;
        if (key == null || !"thebetweenlands".equals(MinecraftReflectionCompat.resourceNamespace(key)) || ausm$betweenlandsRenderLogCount++ >= AUSM_MAX_BETWEENLANDS_RENDER_LOGS) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMBetweenlandsEntity] render-manager stage={} phase={} active={} bypass={} vanillaProgram={} entity={} class={} pos={},{},{}",
                stage,
                context.getPhase(),
                context.isActive(),
                context.shouldBypassWorldPassRendering(),
                vanillaProgram,
                key,
                entity.getClass().getName(),
                Math.round(MinecraftReflectionCompat.posX(entity) * 10.0D) / 10.0D,
                Math.round(MinecraftReflectionCompat.posY(entity) * 10.0D) / 10.0D,
                Math.round(MinecraftReflectionCompat.posZ(entity) * 10.0D) / 10.0D
        );
    }

    private static void ausm$normalizeEntityFaceCulling() {
        GL11.glFrontFace(GL11.GL_CCW);
        MinecraftReflectionCompat.glStateCullFaceBack();
    }

}
