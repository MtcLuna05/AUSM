package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the displayed shadered sky without allowing OpenBlocks' blitter to
 * reconfigure AUSM's draw/read framebuffer state.
 */
@Mixin(targets = "openblocks.client.renderer.SkyBlockRenderer$SkyCapture", remap = false)
public class OpenBlocksSkyCaptureMixin {
    @Inject(method = "run", at = @At("HEAD"), cancellable = true, remap = false)
    private void ausm$skipUnusedSkyCaptureWhileShadered(CallbackInfo ci) {
        if (PipelineContext.getInstance().isActive()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lopenmods/utils/render/FramebufferBlitter;blitFramebuffer(Lnet/minecraft/client/shader/Framebuffer;Lnet/minecraft/client/shader/Framebuffer;)V"
            ),
            remap = false,
            require = 0
    )
    private void ausm$captureSkyWithoutChangingPipelineTarget(@Coerce Object blitter,
                                                               Framebuffer source,
                                                               Framebuffer destination) {
        if (PipelineContext.getInstance().captureOpenBlocksSkyTexture(source, destination)) {
            return;
        }
        MinecraftReflectionCompat.invoke(
                blitter,
                new String[]{"blitFramebuffer"},
                new Class<?>[]{Framebuffer.class, Framebuffer.class},
                source,
                destination
        );
    }

    /**
     * OpenBlocks deliberately binds the source framebuffer after its copy.
     * That is correct for vanilla, but it would detach AUSM from the active
     * deferred framebuffer after the shader-safe copy above.
     */
    @Inject(method = "run", at = @At("TAIL"), remap = false, require = 0)
    private void ausm$restoreDeferredTargetAfterSkyCapture(CallbackInfo ci) {
        PipelineContext.getInstance().restorePipelineTargetAfterOpenBlocksSkyCapture();
    }
}
