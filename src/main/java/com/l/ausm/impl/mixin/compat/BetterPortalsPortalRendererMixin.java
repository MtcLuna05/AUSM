package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.compat.BetterPortalsCompat;
import com.l.ausm.impl.pipeline.compat.BetterPortalsPortalSurfaceRenderer;
import com.l.ausm.impl.MainMod;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(targets = "de.johni0702.minecraft.betterportals.client.render.PortalRenderer", remap = false)
public abstract class BetterPortalsPortalRendererMixin {
    private static boolean ausm$portalSurfaceRendererUnavailableLogged;
    private static final ThreadLocal<Deque<Boolean>> AUSM$renderStateStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderPortal", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$renderPortalSurface(@Coerce Object portal, Vec3d pos, Framebuffer framebuffer, @Coerce Object renderPass, CallbackInfo ci) {
        if (!BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            return;
        }
        try {
            if (BetterPortalsPortalSurfaceRenderer.renderPortalSurface(this, portal, pos, framebuffer, renderPass)) {
                ci.cancel();
            }
        } catch (LinkageError | RuntimeException error) {
            if (!ausm$portalSurfaceRendererUnavailableLogged) {
                ausm$portalSurfaceRendererUnavailableLogged = true;
                MainMod.LOGGER.warn("[BetterPortalsCompat] AUSM portal surface replacement failed to load; falling back to Better Portals surface renderer", error);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$beforeBetterPortalsRender(@Coerce Object portal, Vec3d pos, float partialTicks, CallbackInfo ci) {
        if (!BetterPortalsCompat.isSeeThroughPortalsEnabled()) {
            AUSM$renderStateStack.get().push(false);
            return;
        }
        AUSM$renderStateStack.get().push(true);
        BetterPortalsCompat.pushPortalRendererState();
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$afterBetterPortalsRender(@Coerce Object portal, Vec3d pos, float partialTicks, CallbackInfo ci) {
        Deque<Boolean> stack = AUSM$renderStateStack.get();
        if (stack.isEmpty() || !stack.pop()) {
            return;
        }
        BetterPortalsCompat.popPortalRendererState();
        PipelineContext.getInstance().restoreActiveWorldPassAfterExternalShader();
    }
}
