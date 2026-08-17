package com.l.ausm.impl.mixin.compat;

import appeng.client.render.cablebus.CableBusRenderState;
import appeng.client.render.cablebus.FacadeBuilder;
import appeng.client.render.cablebus.FacadeRenderState;
import appeng.thirdparty.codechicken.lib.model.Quad;
import com.l.ausm.impl.pipeline.compat.AppliedEnergisticsFacadeQuadMetadata;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.util.List;
import java.util.function.Function;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FacadeBuilder.class, remap = false)
public class AppliedEnergisticsFacadeBuilderMixin {
    @Unique
    private static final ThreadLocal<IBlockState> ausm$currentFacadeState = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<IBlockAccess> ausm$currentFacadeWorld = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<BlockPos> ausm$currentFacadePos = new ThreadLocal<>();

    @Shadow(remap = false)
    private static List<BakedQuad> gatherQuads(IBakedModel model, IBlockState state, long rand) {
        throw new AssertionError();
    }

    @Inject(method = "buildFacadeQuads", at = @At("HEAD"))
    private void ausm$beginFacadeMetadata(BlockRenderLayer layer, CableBusRenderState renderState, long rand,
                                          List<BakedQuad> quads,
                                          Function<ResourceLocation, IBakedModel> modelLookup,
                                          CallbackInfo ci) {
        Object world = MinecraftReflectionCompat.invoke(renderState, new String[]{"getWorld"}, new Class<?>[0]);
        Object pos = MinecraftReflectionCompat.invoke(renderState, new String[]{"getPos"}, new Class<?>[0]);
        ausm$currentFacadeWorld.set(world instanceof IBlockAccess ? (IBlockAccess) world : null);
        ausm$currentFacadePos.set(pos instanceof BlockPos ? (BlockPos) pos : null);
    }

    @Inject(method = "buildFacadeQuads", at = @At("RETURN"))
    private void ausm$endFacadeMetadata(BlockRenderLayer layer, CableBusRenderState renderState, long rand,
                                        List<BakedQuad> quads,
                                        Function<ResourceLocation, IBakedModel> modelLookup,
                                        CallbackInfo ci) {
        ausm$currentFacadeState.remove();
        ausm$currentFacadeWorld.remove();
        ausm$currentFacadePos.remove();
    }

    @Redirect(
            method = "buildFacadeQuads",
            at = @At(value = "INVOKE", target = "Lappeng/client/render/cablebus/FacadeRenderState;getSourceBlock()Lnet/minecraft/block/state/IBlockState;")
    )
    private IBlockState ausm$captureFacadeSourceState(FacadeRenderState facade) {
        IBlockState state = facade.getSourceBlock();
        ausm$currentFacadeState.set(state);
        return state;
    }

    @Redirect(
            method = "buildFacadeQuads",
            at = @At(value = "INVOKE", target = "Lappeng/client/render/cablebus/FacadeBuilder;gatherQuads(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/block/state/IBlockState;J)Ljava/util/List;")
    )
    private List<BakedQuad> ausm$captureGatheredFacadeState(IBakedModel model, IBlockState state, long rand) {
        if (state != null) {
            ausm$currentFacadeState.set(state);
        }
        return gatherQuads(model, state, rand);
    }

    @Redirect(
            method = "buildFacadeQuads",
            at = @At(value = "INVOKE", target = "Lappeng/thirdparty/codechicken/lib/model/Quad;bake()Lnet/minecraft/client/renderer/block/model/BakedQuad;")
    )
    private BakedQuad ausm$markFacadeQuad(Quad quad) {
        BakedQuad bakedQuad = quad.bake();
        AppliedEnergisticsFacadeQuadMetadata.mark(
                bakedQuad,
                ausm$currentFacadeState.get(),
                ausm$currentFacadeWorld.get(),
                ausm$currentFacadePos.get()
        );
        return bakedQuad;
    }
}
