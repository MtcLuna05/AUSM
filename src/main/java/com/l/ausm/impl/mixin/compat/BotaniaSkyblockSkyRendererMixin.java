package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "vazkii.botania.client.render.world.SkyblockSkyRenderer", remap = false)
public class BotaniaSkyblockSkyRendererMixin {
    private static boolean ausm$loggedBaseSuppression;

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;func_177358_a(I)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawOrSuppressUpperSkyVbo(VertexBuffer vertexBuffer, int mode) {
        if (ausm$shouldSuppressBase()) {
            return;
        }
        MinecraftReflectionCompat.invoke(
                vertexBuffer,
                new String[] {"func_177358_a", "drawArrays"},
                new Class<?>[] {int.class},
                mode);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179148_o(I)V",
                    ordinal = 0
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawOrSuppressUpperSkyList(int displayList) {
        if (ausm$shouldSuppressBase()) {
            return;
        }
        MinecraftReflectionCompat.invoke(
                GlStateManager.class,
                new String[] {"func_179148_o", "callList"},
                new Class<?>[] {int.class},
                displayList);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()V",
                    ordinal = 0
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawOrSuppressSunsetFan(Tessellator tessellator) {
        if (ausm$shouldSuppressBase()) {
            MinecraftReflectionCompat.invoke(
                    MinecraftReflectionCompat.tessellatorBuffer(tessellator),
                    new String[] {"func_178965_a", "reset"},
                    MinecraftReflectionCompat.NO_PARAMETERS);
            return;
        }
        MinecraftReflectionCompat.tessellatorDraw(tessellator);
    }

    private static boolean ausm$shouldSuppressBase() {
        boolean suppress = PipelineContext.getInstance().shouldSuppressBotaniaVoidSkyBaseGeometry();
        if (suppress && !ausm$loggedBaseSuppression) {
            ausm$loggedBaseSuppression = true;
            MainMod.LOGGER.info("[AUSMVoidSkyProbe] Suppressing Botania upper dome and sunset fan; owned continuous backing is active.");
        }
        return suppress;
    }
}
