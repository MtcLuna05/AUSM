package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

@Mixin(targets = "vazkii.botania.client.render.world.SkyblockSkyRenderer", remap = false)
public class BotaniaSkyblockSkyRendererMixin {
    private static boolean ausm$loggedBaseSuppression;
    private static int ausm$normalStateProbeCalls;
    private static int ausm$hiddenStateProbeCalls;

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$probeF1Entry(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        // This renderer is also reached through Astral's delegated
        // compatibility branch, which can bypass RenderGlobal's owned-sky
        // entry point. Establish the authoritative backing at the renderer
        // boundary before Botania's base geometry is suppressed.
        PipelineContext.getInstance().renderShaderlessBotaniaSkyBacking(
                partialTicks, world, minecraft);
        ausm$probeF1State("entry", minecraft);
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$probeF1Exit(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        ausm$probeF1State("exit", minecraft);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179147_l()V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$enableRealBotaniaBlend() {
        MinecraftReflectionCompat.invoke(
                GlStateManager.class,
                new String[] {"func_179147_l", "enableBlend"},
                MinecraftReflectionCompat.NO_PARAMETERS);
        GL11.glEnable(GL11.GL_BLEND);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_187428_a(Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;Lnet/minecraft/client/renderer/GlStateManager$DestFactor;Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;Lnet/minecraft/client/renderer/GlStateManager$DestFactor;)V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void ausm$forceInitialBotaniaBlend(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        ausm$forceRealBlend(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_187428_a(Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;Lnet/minecraft/client/renderer/GlStateManager$DestFactor;Lnet/minecraft/client/renderer/GlStateManager$SourceFactor;Lnet/minecraft/client/renderer/GlStateManager$DestFactor;)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void ausm$forceAdditiveBotaniaBlend(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        ausm$forceRealBlend(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;func_179120_a(IIII)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$forceIntegerBotaniaBlend(int sourceFactor, int destFactor,
                                               int sourceFactorAlpha, int destFactorAlpha) {
        MinecraftReflectionCompat.invoke(
                GlStateManager.class,
                new String[] {"func_179120_a", "tryBlendFuncSeparate"},
                new Class<?>[] {int.class, int.class, int.class, int.class},
                sourceFactor, destFactor, sourceFactorAlpha, destFactorAlpha);
        ausm$forceRealBlend(sourceFactor, destFactor, sourceFactorAlpha, destFactorAlpha);
    }

    private static void ausm$forceRealBlend(int sourceFactor, int destFactor,
                                            int sourceFactorAlpha, int destFactorAlpha) {
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(sourceFactor, destFactor, sourceFactorAlpha, destFactorAlpha);
    }

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
            MinecraftReflectionCompat.forceResetBufferDrawingState(
                    MinecraftReflectionCompat.tessellatorBuffer(tessellator));
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

    private static void ausm$probeF1State(String stage, Minecraft minecraft) {
        boolean hideGui = minecraft != null
                && MinecraftReflectionCompat.hideGui(MinecraftReflectionCompat.gameSettings(minecraft));
        if (!hideGui) {
            return;
        }
        int probe = ++ausm$hiddenStateProbeCalls;
        // F1 corruption is intermittent, so retain a bounded sparse sample after
        // the startup calls rather than only logging the first rendered frame.
        if (probe > 960 || probe > 8 && probe % 120 != 0) {
            return;
        }
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        float[] center = new float[] {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        float depthValue = Float.NaN;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            FloatBuffer color = BufferUtils.createFloatBuffer(4);
            GL11.glReadPixels(Math.max(0, MinecraftReflectionCompat.displayWidth(minecraft) / 2),
                    Math.max(0, MinecraftReflectionCompat.displayHeight(minecraft) / 2), 1, 1,
                    GL11.GL_RGBA, GL11.GL_FLOAT, color);
            for (int i = 0; i < 4; i++) {
                center[i] = color.get(i);
            }
            FloatBuffer depth = BufferUtils.createFloatBuffer(1);
            GL11.glReadPixels(Math.max(0, MinecraftReflectionCompat.displayWidth(minecraft) / 2),
                    Math.max(0, MinecraftReflectionCompat.displayHeight(minecraft) / 2), 1, 1,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            depthValue = depth.get(0);
        } catch (RuntimeException | LinkageError ignored) {
            // Probe-only; some drivers do not expose depth reads for the active target.
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        }
        MainMod.LOGGER.info("[AUSMBotaniaSkyProbe] stage={} call={} hideGui={} screen={} paused={} program={} depth={} depthMask={} blend={} texture={} matrix={} drawFbo={} center={}/{}/{}/{} centerDepth={}",
                stage,
                probe,
                hideGui,
                minecraft != null && MinecraftReflectionCompat.currentScreen(minecraft) != null
                        ? MinecraftReflectionCompat.currentScreen(minecraft).getClass().getName() : "none",
                minecraft != null && MinecraftReflectionCompat.isGamePaused(minecraft),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                GL11.glGetInteger(GL11.GL_MATRIX_MODE),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                center[0], center[1], center[2], center[3], depthValue);
    }
}
