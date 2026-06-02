package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.block.state.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockStateContainer.StateImplementation.class)
public class BlockStateContainerStateImplementationMixin {

    @Inject(method = "getAmbientOcclusionLightValue", at = @At("RETURN"), cancellable = true)
    private void ausm$applyShaderAmbientOcclusionLevel(CallbackInfoReturnable<Float> cir) {
        float ambientOcclusionLevel = PipelineContext.getInstance().ambientOcclusionLevel();
        if (ambientOcclusionLevel >= 0.9999f) {
            return;
        }

        float original = cir.getReturnValueF();
        cir.setReturnValue(1.0f - ambientOcclusionLevel * (1.0f - original));
    }
}
