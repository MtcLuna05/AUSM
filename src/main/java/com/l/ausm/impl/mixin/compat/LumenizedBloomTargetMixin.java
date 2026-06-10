package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(targets = "gregtech.client.utils.BloomEffectUtil", remap = false)
public class LumenizedBloomTargetMixin {
    private static final String DEPTH_TEXTURE_UTIL = "gregtech.client.utils.DepthTextureUtil";

    @Redirect(
            method = "renderBloomBlockLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_147110_a()Lnet/minecraft/client/shader/Framebuffer;"),
            remap = false
    )
    private static Framebuffer ausm$usePipelineFramebufferForBloom(Minecraft minecraft) {
        Framebuffer framebuffer = PipelineContext.getInstance().lumenizedBloomTargetFramebuffer();
        return framebuffer != null ? framebuffer : minecraft.getFramebuffer();
    }

    @Redirect(
            method = "renderBloomBlockLayer",
            at = @At(value = "INVOKE", target = "Lgregtech/client/utils/DepthTextureUtil;isLastBind()Z"),
            remap = false
    )
    private static boolean ausm$useCopiedDepthBufferForBloom() {
        if (PipelineContext.getInstance().isLumenizedBloomTargetActive()) {
            return false;
        }
        return ausm$invokeDepthTextureBoolean("isLastBind");
    }

    @Inject(
            method = "renderBloomBlockLayer",
            at = @At("RETURN"),
            remap = false
    )
    private static void ausm$mergePipelineBloom(RenderGlobal renderGlobal, BlockRenderLayer layer, double partialTicks,
                                               int pass, Entity entity, CallbackInfoReturnable<Integer> cir) {
        PipelineContext.getInstance().finishLumenizedBloomTarget();
    }

    private static boolean ausm$invokeDepthTextureBoolean(String methodName) {
        try {
            Class<?> depthTextureUtil = Class.forName(DEPTH_TEXTURE_UTIL, false, Thread.currentThread().getContextClassLoader());
            Method method = depthTextureUtil.getMethod(methodName);
            Object result = method.invoke(null);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
