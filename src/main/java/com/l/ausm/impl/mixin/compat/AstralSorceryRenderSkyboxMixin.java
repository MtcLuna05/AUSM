package com.l.ausm.impl.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderSkybox", remap = false)
public class AstralSorceryRenderSkyboxMixin {
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean ausm$alwaysUseFullAstralSky(List<Integer> whitelist, Object dimension) {
        return false;
    }
}
