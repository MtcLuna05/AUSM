package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderSkybox", remap = false)
public class AstralSorceryRenderSkyboxMixin {
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
    private static Boolean ausm$lastCustomVoidSky;

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean ausm$scopeFullAstralSky(List<Integer> whitelist, Object dimension) {
        if (dimension instanceof Number && ((Number) dimension).intValue() == SIMPLE_VOID_WORLD_DIMENSION_ID) {
            ausm$logVoidSkyMode();
            // Botania supplies the sky dome and textured quads in both modes.
            // The shader option decides whether those inputs remain Botania-style
            // or are transformed into the custom Void sky.
            return true;
        }
        return whitelist != null && whitelist.contains(dimension);
    }

    private static void ausm$logVoidSkyMode() {
        Minecraft minecraft = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        World world = minecraft != null
                ? com.l.ausm.impl.util.MinecraftReflectionCompat.world(minecraft)
                : null;
        boolean customSky = PipelineContext.getInstance().isCustomVoidWorldSkyEnabled(world);
        if (ausm$lastCustomVoidSky == null || ausm$lastCustomVoidSky != customSky) {
            ausm$lastCustomVoidSky = customSky;
            MainMod.LOGGER.info("[AUSMVoidSkyMode] dimension=43 route={}",
                    customSky ? "shaderpack-custom" : "botania-through-pipeline");
        }
    }
}
