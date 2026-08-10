package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.client.EuphoriaEntreePackGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the Entree-native output available before Euphoria scans shaderpacks. */
@Mixin(targets = "com.euphoriapatches.euphoria_patcher.EuphoriaPatcher", remap = false)
public abstract class EuphoriaPatcherEntreeMixin {

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/euphoriapatches/euphoria_patcher/services/ShaderDetector;detectInstalledShaders(Lcom/euphoriapatches/euphoria_patcher/services/ShaderNamingService;)Lcom/euphoriapatches/euphoria_patcher/services/ShaderDetector$ShaderInfo;",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void ausm$generateEntreeEuphoriaBeforeDetection(CallbackInfo callbackInfo) {
        EuphoriaEntreePackGenerator.generateNow();
    }
}
