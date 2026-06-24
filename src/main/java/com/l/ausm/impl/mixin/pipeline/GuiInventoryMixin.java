package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiInventory.class)
public class GuiInventoryMixin {
    @Inject(method = "drawEntityOnScreen", at = @At("HEAD"))
    private static void ausm$beforeInventoryEntityPreview(int posX, int posY, int scale,
                                                          float mouseX, float mouseY,
                                                          EntityLivingBase entity,
                                                          CallbackInfo ci) {
        PipelineContext.getInstance().prepareGuiEntityPreviewRenderState();
    }

    @Inject(method = "drawEntityOnScreen", at = @At("RETURN"))
    private static void ausm$afterInventoryEntityPreview(int posX, int posY, int scale,
                                                         float mouseX, float mouseY,
                                                         EntityLivingBase entity,
                                                         CallbackInfo ci) {
        PipelineContext.getInstance().finishGuiEntityPreviewRenderState();
    }
}
