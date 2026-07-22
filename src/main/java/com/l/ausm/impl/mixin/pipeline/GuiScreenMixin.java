package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(GuiScreen.class)
public class GuiScreenMixin {
    @Unique
    private static final int WORLD_GUI_BACKGROUND = 0x44000000;

    @Unique
    private static final AtomicInteger AUSM$GUI_BACKGROUND_PROBES = new AtomicInteger();

    @Shadow(remap = false)
    protected Minecraft field_146297_k;

    @Inject(method = "func_146270_b(I)V", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void ausm$flattenShaderlessWorldBackground(int tint, CallbackInfo ci) {
        if (ausm$drawFlatWorldBackground() || ausm$preservePresentedWorldBackground()) {
            ausm$logBackgroundCancellation("draw-world", tint);
            ci.cancel();
        }
    }

    @Inject(method = "func_146276_q_()V", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void ausm$preserveWorldBehindDefaultBackground(CallbackInfo ci) {
        if (ausm$preservePresentedWorldBackground()) {
            ausm$logBackgroundCancellation("draw-default", WORLD_GUI_BACKGROUND);
            ci.cancel();
        }
    }

    @Unique
    private boolean ausm$drawFlatWorldBackground() {
        return false;
    }

    @Unique
    private boolean ausm$preservePresentedWorldBackground() {
        if (field_146297_k == null || MinecraftReflectionCompat.world(field_146297_k) == null) {
            return false;
        }
        String className = getClass().getName();
        return "net.minecraft.client.gui.GuiIngameMenu".equals(className)
                || "net.minecraft.client.gui.GuiChat".equals(className)
                || "net.minecraft.client.gui.inventory.GuiInventory".equals(className)
                || className.startsWith("net.minecraft.client.gui.inventory.GuiContainer")
                || className.startsWith("journeymap.client.ui.");
    }

    @Unique
    private void ausm$logBackgroundCancellation(String stage, int tint) {
        int probe = AUSM$GUI_BACKGROUND_PROBES.incrementAndGet();
        if (probe > 96) {
            return;
        }
        MainMod.LOGGER.info(
                "[AUSMGuiBackgroundProbe] call={} stage={} screen={} tint=0x{} world={} pipelineActive={}",
                probe,
                stage,
                getClass().getName(),
                Integer.toHexString(tint),
                field_146297_k != null && MinecraftReflectionCompat.world(field_146297_k) != null,
                PipelineContext.getInstance().isPipelineActive()
        );
    }

    @Unique
    private boolean ausm$shouldUseVanillaWorldBackground() {
        String className = getClass().getName();
        return className.startsWith("com.l.ausm.impl.client.gui.")
                || "tinker_io.gui.GuiSmartOutput".equals(className);
    }
}
