package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Captures the exact texture and depth state at the Test Dummy model boundary. */
@Mixin(RenderLivingBase.class)
public abstract class RenderLivingBaseMixin {
    @Unique
    private static final int AUSM_TEST_DUMMY_RENDER_PROBE_LIMIT = 0;

    @Unique
    private static int ausm$testDummyRenderProbeCount;

    @Redirect(
            method = "func_77036_a",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/Render;func_180548_c(Lnet/minecraft/entity/Entity;)Z",
                    remap = false
            ),
            remap = false
    )
    private boolean ausm$bindTestDummyTextureProbe(Render<?> renderer, Entity entity) {
        boolean bound = MinecraftReflectionCompat.callBoolean(
                renderer,
                new String[] {"func_180548_c", "bindEntityTexture"},
                new Class<?>[] {Entity.class},
                false,
                entity
        );
        if (ausm$isTestDummy(entity)) {
            ausm$logTestDummyRenderProbe(renderer, entity, bound);
        }
        return bound;
    }

    @Unique
    private static boolean ausm$isTestDummy(Entity entity) {
        ResourceLocation key = MinecraftReflectionCompat.entityKey(entity);
        return key != null && "testdummy".equals(MinecraftReflectionCompat.resourceNamespace(key));
    }

    @Unique
    private static void ausm$logTestDummyRenderProbe(Render<?> renderer, Entity entity, boolean bound) {
        if (ausm$testDummyRenderProbeCount >= AUSM_TEST_DUMMY_RENDER_PROBE_LIMIT) {
            return;
        }
        int call = ++ausm$testDummyRenderProbeCount;
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int activeTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture0Binding = activeTextureBinding;
        if (activeTexture != GL13.GL_TEXTURE0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture0Binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(activeTexture);
        }
        PipelineContext context = PipelineContext.getInstance();
        MainMod.LOGGER.info(
                "[AUSMTestDummyRenderProbe] call={} entity={} renderer={} bound={} phase={} active={} program={} drawFbo={} activeTex={} activeTexBinding={} tex0={} depth={} depthMask={} depthFunc={} blend={} alpha={} cull={} frontFace={} drawBuffer={}",
                call,
                MinecraftReflectionCompat.entityKey(entity),
                renderer != null ? renderer.getClass().getName() : "null",
                bound,
                context.getPhase(),
                context.isActive(),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                activeTexture,
                activeTextureBinding,
                texture0Binding,
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glGetInteger(GL11.GL_FRONT_FACE),
                GL11.glGetInteger(GL11.GL_DRAW_BUFFER)
        );
    }
}
