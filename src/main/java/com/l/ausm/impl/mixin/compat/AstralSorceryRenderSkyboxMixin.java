package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderSkybox", remap = false)
public class AstralSorceryRenderSkyboxMixin {
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean ausm$scopeFullAstralSky(List<Integer> whitelist, Object dimension) {
        if (dimension instanceof Number && ausm$usesSimpleVoidOwnedSky(((Number) dimension).intValue())) {
            return false;
        }
        return whitelist != null && whitelist.contains(dimension);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/IRenderHandler;render(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V"
            ),
            remap = false,
            require = 0
    )
    private void ausm$replaceWeakSimpleVoidSkyRenderer(IRenderHandler renderer, float partialTicks,
                                                       WorldClient world, Minecraft minecraft) {
        if (ausm$usesSimpleVoidOwnedSky(world)) {
            PipelineContext.getInstance().renderOwnedSkyBackingBeforeSky(partialTicks);
            return;
        }
        renderer.render(partialTicks, world, minecraft);
    }

    private static boolean ausm$usesSimpleVoidOwnedSky(WorldClient world) {
        if (world == null || com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world) == null) {
            return false;
        }
        return ausm$usesSimpleVoidOwnedSky(com.l.ausm.impl.util.MinecraftReflectionCompat.providerDimension(
                com.l.ausm.impl.util.MinecraftReflectionCompat.worldProvider(world)));
    }

    private static boolean ausm$usesSimpleVoidOwnedSky(int dimensionId) {
        return dimensionId == SIMPLE_VOID_WORLD_DIMENSION_ID;
    }
}
