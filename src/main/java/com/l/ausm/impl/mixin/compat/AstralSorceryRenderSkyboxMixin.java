package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraftforge.client.IRenderHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "hellfirepvp.astralsorcery.client.sky.RenderSkybox", remap = false)
public class AstralSorceryRenderSkyboxMixin {
    private static final int SIMPLE_VOID_WORLD_DIMENSION_ID = 43;
    private static Boolean ausm$lastCustomVoidSky;
    private static int ausm$voidSkyRouteProbes;

    @Shadow(remap = false)
    @Final
    private IRenderHandler otherSkyRenderer;

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"
            ),
            remap = false
    )
    private boolean ausm$scopeFullAstralSky(List<Integer> whitelist, Object dimension) {
        // Shaderless rendering must retain Astral's own sky route. Forcing the
        // Botania handler here leaves no lower-sky geometry in F1 and GUI frames.
        if (!PipelineContext.getInstance().isActive()) {
            return whitelist != null && whitelist.contains(dimension);
        }
        if (dimension instanceof Number && ((Number) dimension).intValue() == SIMPLE_VOID_WORLD_DIMENSION_ID) {
            Minecraft minecraft = MinecraftReflectionCompat.minecraft();
            World world = minecraft != null ? MinecraftReflectionCompat.world(minecraft) : null;
            boolean botaniaRoute = !PipelineContext.getInstance().isCustomVoidWorldSkyEnabled(world);
            return botaniaRoute;
        }
        return whitelist != null && whitelist.contains(dimension);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lhellfirepvp/astralsorcery/client/sky/RenderAstralSkybox;render(FLnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/client/Minecraft;)V"
            ),
            remap = false
    )
    private void ausm$renderCustomVoidSkyLayers(@Coerce Object astralSky, float partialTicks, WorldClient world, Minecraft minecraft) {
        if (world != null
                && MinecraftReflectionCompat.providerDimension(MinecraftReflectionCompat.worldProvider(world)) == SIMPLE_VOID_WORLD_DIMENSION_ID
                && PipelineContext.getInstance().isCustomVoidWorldSkyEnabled(world)
                && otherSkyRenderer != null) {
            MinecraftReflectionCompat.invoke(
                    otherSkyRenderer,
                    new String[]{"render"},
                    new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                    partialTicks,
                    world,
                    minecraft);
        }
        MinecraftReflectionCompat.invoke(
                astralSky,
                new String[]{"render"},
                new Class<?>[]{float.class, WorldClient.class, Minecraft.class},
                partialTicks,
                world,
                minecraft);
    }

    private static void ausm$logVoidSkyMode() {
        Minecraft minecraft = MinecraftReflectionCompat.minecraft();
        World world = minecraft != null
                ? MinecraftReflectionCompat.world(minecraft)
                : null;
        boolean customSky = PipelineContext.getInstance().isCustomVoidWorldSkyEnabled(world);
        if (ausm$lastCustomVoidSky == null || ausm$lastCustomVoidSky != customSky) {
            ausm$lastCustomVoidSky = customSky;
            MainMod.LOGGER.info("[AUSMVoidSkyMode] dimension=43 route={}",
                    customSky ? "shaderpack-custom" : "botania-through-pipeline");
        }
    }
}
