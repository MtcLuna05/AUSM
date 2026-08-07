package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.ResourceLocation;
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
    private static boolean ausm$loggedOwnedDetailSuppression;
    private static int ausm$normalStateProbeCalls;
    private static int ausm$hiddenStateProbeCalls;
    private boolean ausm$shaderedVoidPhase;
    private String ausm$assetTexture = "unbound";

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$probeF1Entry(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        // This renderer is also reached through Astral's delegated
        // compatibility branch, which can bypass RenderGlobal's owned-sky
        // entry point. Establish the authoritative backing at the renderer
        // boundary before Botania's base geometry is suppressed.
        PipelineContext context = PipelineContext.getInstance();
        ausm$assetTexture = "unbound";
        context.clearSkyDetailAsset();
        context.renderShaderlessBotaniaSkyBacking(
                partialTicks, world, minecraft);
        ausm$shaderedVoidPhase = context.shouldUseShaderOwnedSkyOverride(world);
        context.forensicGlTrace("botania-sky-entry", "shaderOwned=" + ausm$shaderedVoidPhase + ", partialTicks=" + partialTicks);
        if (ausm$shaderedVoidPhase) {
            context.beginPhase(WorldRenderingPhase.SKY);
            context.renderShaderedSkyBaseBacking();
        }
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$probeF1Exit(float partialTicks, WorldClient world, Minecraft minecraft, CallbackInfo ci) {
        PipelineContext context = PipelineContext.getInstance();
        if (ausm$shaderedVoidPhase) {
            context.endPass();
            ausm$shaderedVoidPhase = false;
        }
        context.forensicGlTrace("botania-sky-exit", "partialTicks=" + partialTicks);
        context.clearSkyDetailAsset();
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

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/TextureManager;func_110577_a(Lnet/minecraft/util/ResourceLocation;)V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$bindAndTrackSkyAsset(TextureManager textureManager, ResourceLocation location) {
        ausm$assetTexture = MinecraftReflectionCompat.resourceString(location);
        PipelineContext context = PipelineContext.getInstance();
        context.setSkyDetailAsset(ausm$assetTexture);
        MinecraftReflectionCompat.bindTexture(textureManager, location);
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
                    target = "Lnet/minecraft/client/renderer/Tessellator;func_78381_a()V"
            ),
            require = 0,
            remap = false
    )
    private void ausm$drawAndProbeSkyAsset(Tessellator tessellator) {
        PipelineContext.getInstance().uploadSkyDetailUniforms();
        boolean suppressBase = "unbound".equals(ausm$assetTexture) && ausm$shouldSuppressBase();
        // Entree's AUSM-owned sky shader supplies the planets, ribbons, and
        // rainbow. Suppress Botania's textured detail quads as a whole so the
        // shader route is the sole detail owner.
        if (suppressBase || ausm$shaderedVoidPhase) {
            MinecraftReflectionCompat.forceResetBufferDrawingState(
                    MinecraftReflectionCompat.tessellatorBuffer(tessellator));
        } else {
            MinecraftReflectionCompat.tessellatorDraw(tessellator);
        }
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
        // Probe disabled.
    }
}
