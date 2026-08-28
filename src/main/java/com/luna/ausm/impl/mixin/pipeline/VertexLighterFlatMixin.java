package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.vertex.BlockRenderContext;
import com.luna.ausm.impl.pipeline.bloom.AusmBloomLayer;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraftforge.client.model.pipeline.VertexLighterFlat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Blockcraftery uses Forge's light pipeline for its generated host shape.
 * Luminous RandomThings models mark their BLOOM overlay as unshaded, but that
 * flag is lost when the material is projected onto the host shape. Preserve
 * that contract only for the copied overlay: the ordinary framed material
 * must retain Forge's lighting just like the direct contained block.
 */
@Mixin(value = VertexLighterFlat.class, remap = false)
public abstract class VertexLighterFlatMixin {
    private static final float FULL_BRIGHT_LIGHTMAP = 15.0F * 0x20 / 0xFFFF;

    /**
     * Blockcraftery's host state has no light value, so Forge otherwise
     * replaces the overlay's fully-lit lightmap with the host sample. The
     * base material is deliberately excluded: making it full-bright is what
     * made framed luminous blocks brighter than their native counterparts.
     */
    @Inject(
            method = "processQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/model/pipeline/VertexLighterFlat;updateLightmap([F[FFFF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false,
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void ausm$preserveFramedLuminousEmission(CallbackInfo ci,
                                                     float[][] position, float[][] normal,
                                                     float[][] lightmap, float[][] color,
                                                     int multiplier, VertexFormat format, int count, int vertex,
                                                     float x, float y, float z,
                                                     float blockLight, float skyLight) {
        if (!ausm$isFramedBloomOverlay()
                || lightmap == null || vertex < 0 || vertex >= lightmap.length
                || lightmap[vertex] == null || lightmap[vertex].length < 2) {
            return;
        }

        lightmap[vertex][0] = FULL_BRIGHT_LIGHTMAP;
        lightmap[vertex][1] = FULL_BRIGHT_LIGHTMAP;
    }

    @Redirect(
            method = "processQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/model/pipeline/LightUtil;diffuseLight(FFF)F",
                    remap = false
            ),
            remap = false
    )
    private static float ausm$preserveFramedLuminousLight(float x, float y, float z) {
        float normalDiffuse = diffuseFactor(x, y, z);
        if (!ausm$isFramedBloomOverlay()) {
            return normalDiffuse;
        }

        return 1.0F;
    }

    /**
     * Mirrors Forge LightUtil.diffuseLight(x, y, z) without a runtime member call.
     */
    private static float diffuseFactor(float x, float y, float z) {
        return Math.min(x * x * 0.6F + y * y * ((3.0F + y) * 0.25F) + z * z * 0.8F, 1.0F);
    }

    private static boolean ausm$isFramedBloomOverlay() {
        return BlockRenderContext.isFramedMaterialOwner()
                && BlockRenderContext.framedBloomBoost()
                && AusmBloomLayer.isBloomLayer(MinecraftReflectionCompat.currentRenderLayer());
    }
}
