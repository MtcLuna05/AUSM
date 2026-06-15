package com.l.ausm.impl.mixin.compat;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.PipelineContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "hellfirepvp.astralsorcery.client.util.RenderConstellation", remap = false)
public class AstralSorceryConstellationRendererMixin {
    @Inject(method = "renderConstellation", at = @At("HEAD"), remap = false)
    private static void ausm$beginSkyTexturedConstellationProgram(@Coerce Object constellation,
                                                                  @Coerce Object position,
                                                                  @Coerce Object brightness,
                                                                  CallbackInfo ci) {
        PipelineContext.getInstance().beginAstralConstellationPhase(constellation, WorldRenderingPhase.SKY_TEXTURED);
    }

    @Inject(method = "renderConstellation", at = @At("RETURN"), remap = false)
    private static void ausm$endSkyTexturedConstellationProgram(@Coerce Object constellation,
                                                                @Coerce Object position,
                                                                @Coerce Object brightness,
                                                                CallbackInfo ci) {
        PipelineContext.getInstance().endAstralConstellationPhase();
    }
}
