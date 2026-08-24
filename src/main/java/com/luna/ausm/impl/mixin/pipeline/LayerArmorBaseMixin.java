package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.pipeline.PipelineContext;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Binds the OptiFine armor-glint program for enchanted armor layer rendering.
 */
@Mixin(LayerArmorBase.class)
public class LayerArmorBaseMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$armorGlintPhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "renderEnchantedGlint(Lnet/minecraft/client/renderer/entity/RenderLivingBase;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V", at = @At("HEAD"))
    private static void onRenderEnchantedGlintHead(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        AUSM$armorGlintPhaseStack.get().push(PipelineContext.getInstance().beginPhaseIfActive(WorldRenderingPhase.ARMOR_GLINT));
    }

    @Inject(method = "renderEnchantedGlint(Lnet/minecraft/client/renderer/entity/RenderLivingBase;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V", at = @At("RETURN"))
    private static void onRenderEnchantedGlintReturn(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        Deque<Boolean> stack = AUSM$armorGlintPhaseStack.get();
        if (!stack.isEmpty() && stack.pop()) {
            PipelineContext.getInstance().endPass();
        }
    }
}
