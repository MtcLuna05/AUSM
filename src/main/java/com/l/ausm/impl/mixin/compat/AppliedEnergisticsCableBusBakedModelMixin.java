package com.l.ausm.impl.mixin.compat;

import appeng.api.util.AEColor;
import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.CableBusRenderState;
import appeng.block.networking.BlockCableBus;
import com.l.ausm.impl.pipeline.compat.AppliedEnergisticsFacadeQuadMetadata;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.property.IExtendedBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = CableBusBakedModel.class, remap = false)
public class AppliedEnergisticsCableBusBakedModelMixin {
    @Inject(method = "func_188616_a", at = @At("RETURN"))
    private void ausm$markCableBusTintedQuads(IBlockState state, EnumFacing side, long rand,
                                              CallbackInfoReturnable<List<BakedQuad>> cir) {
        CableBusRenderState renderState = ausm$renderState(state);
        if (renderState == null || renderState.getCableColor() == null) {
            return;
        }

        int[] tintColors = ausm$tintColors(renderState.getCableColor());
        if (tintColors == null) {
            return;
        }

        List<BakedQuad> quads = cir.getReturnValue();
        if (quads == null || quads.isEmpty()) {
            return;
        }
        for (BakedQuad quad : quads) {
            if (quad != null && com.l.ausm.impl.util.MinecraftReflectionCompat.bakedQuadHasTintIndex(quad)) {
                AppliedEnergisticsFacadeQuadMetadata.markCableBusTint(quad, tintColors);
            }
        }
    }

    private static CableBusRenderState ausm$renderState(IBlockState state) {
        if (!(state instanceof IExtendedBlockState extendedState)) {
            return null;
        }
        try {
            Object value = com.l.ausm.impl.util.MinecraftReflectionCompat.stateValue(extendedState, BlockCableBus.RENDER_STATE_PROPERTY);
            return value instanceof CableBusRenderState ? (CableBusRenderState) value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int[] ausm$tintColors(AEColor color) {
        int[] tintColors = new int[5];
        boolean hasColor = false;
        for (int tintIndex = 0; tintIndex < tintColors.length; tintIndex++) {
            tintColors[tintIndex] = color.getVariantByTintIndex(tintIndex);
            hasColor |= tintColors[tintIndex] >= 0 && (tintColors[tintIndex] & 0xFFFFFF) != 0xFFFFFF;
        }
        return hasColor ? tintColors : null;
    }
}
