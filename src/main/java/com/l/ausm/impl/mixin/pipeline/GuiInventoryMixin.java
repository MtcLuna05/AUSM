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
    @Inject(
            method = "func_147046_a(IIIFFLnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private static void ausm$beforeInventoryEntityPreview(int posX, int posY, int scale,
                                                          float mouseX, float mouseY,
                                                          EntityLivingBase entity,
                                                          CallbackInfo ci) {
        PipelineContext.getInstance().prepareGuiEntityPreviewRenderState();
    }

    @Inject(
            method = "func_147046_a(IIIFFLnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private static void ausm$afterInventoryEntityPreview(int posX, int posY, int scale,
                                                         float mouseX, float mouseY,
                                                         EntityLivingBase entity,
                                                         CallbackInfo ci) {
        PipelineContext.getInstance().finishGuiEntityPreviewRenderState();
    }
}
