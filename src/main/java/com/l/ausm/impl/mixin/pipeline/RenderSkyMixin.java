package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mixin to bind the sky shader programs.
 */
@Mixin(RenderGlobal.class)
public class RenderSkyMixin {
    private static int ausm$lowerSkyProbeCalls;
    private static final ThreadLocal<Deque<Object>> ausm$blockedSkyRenderers = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ResourceLocation ausm$moonPhasesTexture = new ResourceLocation("textures/environment/moon_phases.png");
    @Inject(method = "markBlocksForUpdate", at = @At("HEAD"))
    private void ausm$ensureViewFrustumBeforeBlockUpdate(int minX, int minY, int minZ,
                                                         int maxX, int maxY, int maxZ,
                                                         boolean updateImmediately,
                                                         CallbackInfo ci) {
        PipelineContext.getInstance().ensureRenderGlobalViewFrustum((RenderGlobal) (Object) this);
    }

    @Inject(method = "loadRenderers", at = @At("HEAD"))
    private void ausm$logLoadRenderers(CallbackInfo ci) {
        PipelineContext.getInstance().handleRenderGlobalLoadRenderers((RenderGlobal) (Object) this);
    }

    @Inject(method = "loadRenderers", at = @At("RETURN"))
    private void ausm$afterLoadRenderers(CallbackInfo ci) {
        PipelineContext.getInstance().handleRenderGlobalLoadRenderersComplete((RenderGlobal) (Object) this);
    }

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void onRenderSkyHead(float partialTicks, int pass, CallbackInfo ci) {
        // Some nested/custom sky paths can leave the shared Tessellator open.
        // Vanilla sky immediately calls BufferBuilder.begin(), which hard-crashes
        // if the previous buffer was not closed.
        ausm$forceResetTessellator();
        PipelineContext.getInstance().setAstralSolarEclipseFactor(0.0f);
        PipelineContext context = PipelineContext.getInstance();
        context.logShaderedSkyGeometryProbe("render-head-before-phase");
        context.beginPhase(WorldRenderingPhase.SKY);
        context.logShaderedSkyGeometryProbe("render-head-after-phase");
        if (context.shouldUseCompleteOwnedSkyOverride()) {
            context.renderCompleteOwnedSkyOverride(partialTicks, pass);
            context.endPass();
            ci.cancel();
            return;
        }
        context.renderOwnedSkyBackingBeforeSky(partialTicks);
        if (!context.shouldRenderSkyDisc()) {
            context.endPass();
            ci.cancel();
            return;
        }
        Object blockedRenderer = PipelineContext.getInstance().detachNonVanillaSkyRendererForVanillaSky();
        if (blockedRenderer != null) {
            ausm$blockedSkyRenderers.get().push(blockedRenderer);
        }
    }

    @Inject(method = "renderSky(FI)V", at = @At("RETURN"))
    private void onRenderSkyReturn(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.logShaderedSkyGeometryProbe("render-return-entry");
        Deque<Object> blockedRenderers = ausm$blockedSkyRenderers.get();
        if (!blockedRenderers.isEmpty()) {
            context.restoreNonVanillaSkyRenderer(blockedRenderers.pop());
        }
        context.renderShaderlessOwnedSkyDetailsAfterCelestials(partialTicks);
        context.sealShaderlessSkyFramebufferAlpha();
        context.logHiddenSkyFramebufferProbe("post-sky");
        context.logShaderedSkyGeometryProbe("render-return-before-end");
        context.endPass();
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/IRenderHandler;render(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V"
            ),
            require = 0
    )
    private void ausm$renderOrSuppressCustomSkyRenderer(IRenderHandler skyRenderer,
                                                        float partialTicks,
                                                        WorldClient world,
                                                        Minecraft minecraft) {
        PipelineContext context = PipelineContext.getInstance();
        if (!context.isActive()) {
            if (context.renderShaderlessOwnedVoidCompatibilitySky(skyRenderer, partialTicks, world, minecraft)) {
                return;
            }
            MinecraftReflectionCompat.invoke(
                    skyRenderer,
                    new String[] {"render"},
                    new Class<?>[] {float.class, WorldClient.class, Minecraft.class},
                    partialTicks,
                    world,
                    minecraft);
            return;
        }
        context.endPass();
        context.logShaderedSkyGeometryProbe("custom-before-prepare");
        context.prepareExternalWorldOverlayRender();
        context.beginPhase(WorldRenderingPhase.CUSTOM_SKY);
        context.logShaderedSkyGeometryProbe("custom-after-phase");
        try {
            if (!context.shouldSuppressVoidWorldCustomSkyRenderer(skyRenderer, world)) {
                context.logShaderedSkyGeometryProbe("custom-before-renderer");
                MinecraftReflectionCompat.invoke(
                        skyRenderer,
                        new String[] {"render"},
                        new Class<?>[] {float.class, WorldClient.class, Minecraft.class},
                        partialTicks,
                        world,
                        minecraft);
                context.logShaderedSkyGeometryProbe("custom-after-renderer");
            }
            context.renderShaderlessBotaniaVoidDetailsIfNeeded(partialTicks, world, minecraft);
        } finally {
            context.endPass();
            context.finishExternalWorldOverlayRender("custom sky renderer");
        }
    }

    @Inject(method = "renderSkyEnd()V", at = @At("HEAD"), require = 0)
    private void ausm$beforeVoidSkybox(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        context.endPass();
        context.prepareExternalWorldOverlayRender();
        context.beginPhase(WorldRenderingPhase.VOID);
    }

    @Inject(method = "renderSkyEnd()V", at = @At("RETURN"), require = 0)
    private void ausm$afterVoidSkybox(CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldUseCompleteOwnedSkyOverride()) {
            return;
        }
        context.endPass();
        context.finishExternalWorldOverlayRender("void skybox");
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/BufferBuilder;begin(ILnet/minecraft/client/renderer/vertex/VertexFormat;)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void ausm$beforeSunriseSunsetFan(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.SUNSET);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 0
            )
    )
    private void ausm$drawOrSuppressSunriseSunsetFan(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        try {
            if (context.shouldSuppressVanillaSunsetGeometry()) {
                ausm$forceResetTessellator(tessellator);
                return;
            }
            com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
        } finally {
            context.endPass();
        }
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            )
    )
    private void ausm$beforeSunTextureBind(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.SUN);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;bindTexture(Lnet/minecraft/util/ResourceLocation;)V",
                    ordinal = 1,
                    shift = At.Shift.BEFORE
            )
    )
    private void ausm$beforeMoonTextureBind(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.MOON);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaSun(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaSunGeometry()) {
            ausm$forceResetTessellator(tessellator);
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 2
            )
    )
    private void ausm$drawOrSuppressVanillaMoon(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaMoonGeometry()) {
            ausm$forceResetTessellator(tessellator);
            return;
        }
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        if (minecraft != null && MinecraftReflectionCompat.textureManager(minecraft) != null) {
            MinecraftReflectionCompat.bindTexture(MinecraftReflectionCompat.textureManager(minecraft), ausm$moonPhasesTexture);
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    private static void ausm$forceResetTessellator() {
        ausm$forceResetTessellator(com.l.ausm.impl.util.MinecraftReflectionCompat.tessellator());
    }

    private static void ausm$forceResetTessellator(Tessellator tessellator) {
        if (tessellator == null) {
            return;
        }
        BufferBuilder buffer = com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorBuffer(tessellator);
        MinecraftReflectionCompat.forceResetBufferDrawingState(buffer);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void ausm$drawOrSuppressVanillaUpperSkyVbo(VertexBuffer vertexBuffer, int mode) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaUpperSkyGeometry()) {
            context.logShaderedSkyGeometryProbe("upper-vbo-suppressed");
            return;
        }
        context.logShaderedSkyGeometryProbe("upper-vbo-before");
        MinecraftReflectionCompat.vertexBufferDrawArrays(vertexBuffer, mode);
        context.logShaderedSkyGeometryProbe("upper-vbo-after");
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void ausm$drawOrSuppressVanillaUpperSkyList(int list) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaUpperSkyGeometry()) {
            context.logShaderedSkyGeometryProbe("upper-list-suppressed");
            return;
        }
        context.logShaderedSkyGeometryProbe("upper-list-before");
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, (list));;
        context.logShaderedSkyGeometryProbe("upper-list-after");
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/WorldClient;getStarBrightness(F)F",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyBeforeStars(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        context.endPass();
        context.beginPhase(WorldRenderingPhase.STARS);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaStarsVbo(VertexBuffer vertexBuffer, int mode) {
        if (PipelineContext.getInstance().shouldSuppressVanillaStarsGeometry()) {
            return;
        }
        MinecraftReflectionCompat.vertexBufferDrawArrays(vertexBuffer, mode);
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V",
                    ordinal = 1
            )
    )
    private void ausm$drawOrSuppressVanillaStarsList(int list) {
        if (PipelineContext.getInstance().shouldSuppressVanillaStarsGeometry()) {
            return;
        }
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, (list));;
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V",
                    ordinal = 2
            ),
            require = 0
    )
    private void ausm$drawShaderedLowerSkyVbo(VertexBuffer vertexBuffer, int mode) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaLowerSkyGeometry()) {
            ausm$probeLowerSky("vbo-suppressed");
            return;
        }
        ausm$probeLowerSky("vbo-before");
        context.beginPhase(WorldRenderingPhase.SKY_GROUND);
        try {
            MinecraftReflectionCompat.vertexBufferDrawArrays(vertexBuffer, mode);
        } finally {
            context.endPass();
            ausm$probeLowerSky("vbo-after");
        }
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V",
                    ordinal = 2
            ),
            require = 0
    )
    private void ausm$drawShaderedLowerSkyList(int list) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaLowerSkyGeometry()) {
            ausm$probeLowerSky("list-suppressed");
            return;
        }
        ausm$probeLowerSky("list-before");
        context.beginPhase(WorldRenderingPhase.SKY_GROUND);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, (list));;
        } finally {
            context.endPass();
            ausm$probeLowerSky("list-after");
        }
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;callList(I)V",
                    ordinal = 3
            ),
            require = 0
    )
    private void ausm$drawShaderedLowerSkyListAfterHorizon(int list) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaLowerSkyGeometry()) {
            ausm$probeLowerSky("horizon-list-suppressed");
            return;
        }
        ausm$probeLowerSky("horizon-list-before");
        context.beginPhase(WorldRenderingPhase.SKY_GROUND);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_179148_o", "callList"}, new Class<?>[] {int.class}, (list));;
        } finally {
            context.endPass();
            ausm$probeLowerSky("horizon-list-after");
        }
    }

    @Redirect(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
                    ordinal = 3
            ),
            require = 0
    )
    private void ausm$drawShaderedLowerSkyBox(Tessellator tessellator) {
        PipelineContext context = PipelineContext.getInstance();
        if (context.shouldSuppressVanillaLowerSkyGeometry()) {
            ausm$probeLowerSky("box-suppressed");
            ausm$forceResetTessellator(tessellator);
            return;
        }
        ausm$probeLowerSky("box-before");
        context.beginPhase(WorldRenderingPhase.SKY_GROUND);
        try {
            com.l.ausm.impl.util.MinecraftReflectionCompat.tessellatorDraw(tessellator);
        } finally {
            context.endPass();
            ausm$probeLowerSky("box-after");
        }
    }

    private static void ausm$probeLowerSky(String stage) {
        PipelineContext.getInstance().logShaderedSkyGeometryProbe("lower-" + stage);
    }

    @Inject(
            method = "renderSky(FI)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableBlend()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onRenderSkyAfterStars(float partialTicks, int pass, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("HEAD"), cancellable = true)
    private void onRenderCloudsHead(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        if (!PipelineContext.getInstance().shouldRenderClouds()) {
            ci.cancel();
            return;
        }
        PipelineContext.getInstance().beginPhase(WorldRenderingPhase.CLOUDS);
    }

    @Inject(method = "renderClouds(FIDDD)V", at = @At("RETURN"))
    private void onRenderCloudsReturn(float partialTicks, int pass, double x, double y, double z, CallbackInfo ci) {
        PipelineContext.getInstance().endPass();
    }
}
