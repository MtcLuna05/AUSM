package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Mixin(targets = "hellfirepvp.astralsorcery.client.effect.EffectHandler", remap = false)
public abstract class AstralSorceryEffectHandlerMixin {
    @Shadow(remap = false)
    public static Map complexEffects;

    @Unique
    private static int ausm$repairLogs;

    @Unique
    private static int ausm$overlayStateProbeLogs;

    @Unique
    private boolean ausm$externalWorldOverlay;

    @Unique
    private boolean ausm$shaderedWorldOverlay;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void ausm$repairComplexEffectBuckets(CallbackInfo ci) {
        ausm$repairComplexEffects();
    }

    @Inject(method = "onRender", at = @At("HEAD"), remap = false)
    private void ausm$repairComplexEffectBucketsBeforeWorldRender(RenderWorldLastEvent event, CallbackInfo ci) {
        ausm$repairComplexEffects();
        PipelineContext context = PipelineContext.getInstance();
        ausm$externalWorldOverlay = context.isActive();
        ausm$shaderedWorldOverlay = false;
        if (ausm$externalWorldOverlay) {
            // The trace proved the old bridge rendered program 0 to only the
            // color attachment of the deferred FBO. That leaves the effect
            // without coherent material/normal data and produces the reported
            // grain/transparency. Use the normal translucent entity MRT pass.
            ausm$shaderedWorldOverlay = context.beginAstralEffectOverlayPhase();
            if (!ausm$shaderedWorldOverlay) {
                context.prepareExternalWorldOverlayRender();
            }
            ausm$logOverlayState("prepared");
        }
    }

    @Inject(method = "onRender", at = @At("RETURN"), remap = false)
    private void ausm$finishComplexEffectsWorldRender(RenderWorldLastEvent event, CallbackInfo ci) {
        if (ausm$externalWorldOverlay) {
            PipelineContext context = PipelineContext.getInstance();
            ausm$logOverlayState("before-finish");
            if (ausm$shaderedWorldOverlay) {
                context.endAstralEffectOverlayPhase();
            }
            context.finishExternalWorldOverlayRender("Astral Sorcery effects");
            // RenderWorldLast occurs inside the world pass on this client.
            // Unlike the other external-overlay bridges, returning to vanilla
            // state here left the following first-person hand draw in program
            // 0 while it still targeted the deferred framebuffer.
            if (!ausm$shaderedWorldOverlay) {
                context.restoreActiveWorldPassAfterExternalShader();
            }
            ausm$logOverlayState("restored");
            ausm$externalWorldOverlay = false;
            ausm$shaderedWorldOverlay = false;
        }
    }

    @Inject(method = "onOverlay", at = @At("HEAD"), remap = false)
    private void ausm$repairComplexEffectBucketsBeforeOverlay(RenderGameOverlayEvent.Post event, CallbackInfo ci) {
        ausm$repairComplexEffects();
    }

    @Inject(method = "onDebugText", at = @At("HEAD"), remap = false)
    private void ausm$repairComplexEffectBucketsBeforeDebugText(RenderGameOverlayEvent.Text event, CallbackInfo ci) {
        ausm$repairComplexEffects();
    }

    @Inject(method = "registerUnsafe", at = @At("HEAD"), remap = false)
    private void ausm$repairComplexEffectBucketsBeforeRegister(@Coerce Object effect, CallbackInfo ci) {
        ausm$repairComplexEffects();
    }

    @Unique
    private static void ausm$repairComplexEffects() {
        if (complexEffects == null) {
            return;
        }

        try {
            Class<?> targetClass = Class.forName(
                    "hellfirepvp.astralsorcery.client.effect.IComplexEffect$RenderTarget",
                    false,
                    AstralSorceryEffectHandlerMixin.class.getClassLoader()
            );
            Object[] targets = targetClass.getEnumConstants();
            if (targets == null) {
                return;
            }

            boolean repaired = false;
            for (Object target : targets) {
                Object layeredObject = complexEffects.get(target);
                Map layeredEffects;
                if (layeredObject instanceof Map) {
                    layeredEffects = (Map) layeredObject;
                } else {
                    layeredEffects = new HashMap();
                    complexEffects.put(target, layeredEffects);
                    repaired = true;
                }

                for (int layer = 0; layer <= 2; layer++) {
                    Integer key = layer;
                    if (!(layeredEffects.get(key) instanceof List)) {
                        layeredEffects.put(key, new LinkedList());
                        repaired = true;
                    }
                }
            }

            if (repaired && ausm$repairLogs++ < 4) {
                MainMod.LOGGER.warn("[AstralSorceryCompat] Repaired missing EffectHandler complex effect buckets after a world/view switch.");
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (ausm$repairLogs++ < 4) {
                MainMod.LOGGER.warn("[AstralSorceryCompat] Failed to inspect EffectHandler complex effect buckets", e);
            }
        }
    }

    @Unique
    private static void ausm$logOverlayState(String stage) {
        // Probe disabled.
    }
}
