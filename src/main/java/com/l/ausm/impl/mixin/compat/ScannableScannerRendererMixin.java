package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(targets = "li.cil.scannable.client.renderer.ScannerRenderer", remap = false)
public abstract class ScannableScannerRendererMixin {
    @Unique
    private static Field ausm$modeField;
    @Unique
    private static Field ausm$framebufferDepthTextureField;
    @Unique
    private static Method ausm$renderMethod;
    @Unique
    private static Object ausm$renderMode;
    @Unique
    private static boolean ausm$reflectionFailed;
    @Unique
    private static boolean ausm$compatLogged;

    @Inject(method = "onPreWorldRender", at = @At("HEAD"))
    private void ausm$forceRenderModeForAusm(TickEvent.RenderTickEvent event, CallbackInfo ci) {
        if (event == null
                || event.phase != TickEvent.Phase.START
                || !PipelineContext.getInstance().isActive()
                || ausm$hasDepthTexture()) {
            return;
        }

        ausm$setRenderMode();
    }

    @Inject(method = "onWorldRender", at = @At("HEAD"), cancellable = true)
    private void ausm$skipWorldPhaseScanRender(RenderWorldLastEvent event, CallbackInfo ci) {
        if (!PipelineContext.getInstance().isActive() || !ausm$hasDepthTexture()) {
            return;
        }

        ausm$logCompat("routing scanner world render to HUD overlay");
        ci.cancel();
    }

    @Inject(method = "onPreRenderGameOverlay", at = @At("HEAD"), cancellable = true)
    private void ausm$renderScanAfterPipeline(RenderGameOverlayEvent.Pre event, CallbackInfo ci) {
        if (!PipelineContext.getInstance().isActive()
                || event == null
                || event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || !ausm$hasDepthTexture()) {
            return;
        }

        PipelineContext context = PipelineContext.getInstance();
        context.prepareExternalOverlayRender("Scannable scanner");
        try {
            ausm$invokeRender(event.getPartialTicks());
        } finally {
            context.finishExternalOverlayRender("Scannable scanner");
        }
        ci.cancel();
    }

    @Inject(method = "render(F)V", at = @At("HEAD"))
    private void ausm$prepareTextureUnitForRender(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().prepareExternalOverlayRender("Scannable scanner direct");
    }

    @Inject(method = "render(F)V", at = @At("RETURN"))
    private void ausm$restoreTextureUnitAfterRender(float partialTicks, CallbackInfo ci) {
        PipelineContext.getInstance().finishExternalOverlayRender("Scannable scanner direct");
    }

    @Unique
    private boolean ausm$hasDepthTexture() {
        if (!ausm$ensureReflection()) {
            return false;
        }
        try {
            return ausm$framebufferDepthTextureField.getInt(this) != 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ausm$logReflectionFailure(e);
            return false;
        }
    }

    @Unique
    private void ausm$setRenderMode() {
        if (!ausm$ensureReflection()) {
            return;
        }
        try {
            ausm$modeField.set(this, ausm$renderMode);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ausm$logReflectionFailure(e);
        }
    }

    @Unique
    private void ausm$invokeRender(float partialTicks) {
        if (!ausm$ensureReflection()) {
            return;
        }
        try {
            ausm$renderMethod.invoke(this, partialTicks);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ausm$logReflectionFailure(e);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean ausm$ensureReflection() {
        if (ausm$reflectionFailed) {
            return false;
        }
        if (ausm$modeField != null
                && ausm$framebufferDepthTextureField != null
                && ausm$renderMethod != null
                && ausm$renderMode != null) {
            return true;
        }

        try {
            Class<?> owner = getClass();
            ausm$modeField = owner.getDeclaredField("mode");
            ausm$modeField.setAccessible(true);
            ausm$framebufferDepthTextureField = owner.getDeclaredField("framebufferDepthTexture");
            ausm$framebufferDepthTextureField.setAccessible(true);
            ausm$renderMethod = owner.getDeclaredMethod("render", float.class);
            ausm$renderMethod.setAccessible(true);
            Class<?> modeClass = Class.forName("li.cil.scannable.client.renderer.ScannerRenderer$Mode", false, owner.getClassLoader());
            ausm$renderMode = Enum.valueOf((Class<? extends Enum>) modeClass.asSubclass(Enum.class), "RENDER");
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ausm$logReflectionFailure(e);
            return false;
        }
    }

    @Unique
    private static void ausm$logCompat(String detail) {
        if (ausm$compatLogged) {
            return;
        }
        ausm$compatLogged = true;
        MainMod.LOGGER.info("[ScannableCompat] {}", detail);
    }

    @Unique
    private static void ausm$logReflectionFailure(Throwable throwable) {
        if (ausm$reflectionFailed) {
            return;
        }
        ausm$reflectionFailed = true;
        MainMod.LOGGER.warn("[ScannableCompat] Failed to wire Scannable renderer compat", throwable);
    }
}
