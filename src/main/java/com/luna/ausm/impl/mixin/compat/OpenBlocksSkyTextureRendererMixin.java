package com.luna.ausm.impl.mixin.compat;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * OpenBlocks uses a block-entity pass to write a stencil, then projects its
 * captured sky through that stencil. The projection is sky texture geometry,
 * not a block entity, and must therefore use the shaderpack sky-textured
 * program rather than the active block-entity program.
 */
@Mixin(targets = "openblocks.client.renderer.tileentity.TileEntitySkyRenderer", remap = false)
public abstract class OpenBlocksSkyTextureRendererMixin {
    @Unique
    private static final ThreadLocal<Deque<Boolean>> AUSM$skyTexturePhaseStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<Integer>> AUSM$fallbackModelPrograms = ThreadLocal.withInitial(ArrayDeque::new);

    /** The disabled path uses a cached vanilla BLOCK-format VBO, not AUSM's extended vertex format. */
    @Inject(
            method = "renderModel",
            at = @At(value = "INVOKE", target = "Lopenmods/renderer/CachedRendererFactory$CachedRenderer;render()V", shift = At.Shift.BEFORE),
            require = 0,
            remap = false
    )
    private void ausm$useDisabledModelVertexPath(double x, double y, double z, BlockPos pos, IBlockAccess access,
                                                  IBlockState state, CallbackInfo callbackInfo) {
        if (!PipelineContext.getInstance().isActive()) {
            return;
        }
        AUSM$fallbackModelPrograms.get().push(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));
        GL20.glUseProgram(0);
    }

    @Inject(
            method = "renderModel",
            at = @At(value = "INVOKE", target = "Lopenmods/renderer/CachedRendererFactory$CachedRenderer;render()V", shift = At.Shift.AFTER),
            require = 0,
            remap = false
    )
    private void ausm$restorePipelineAfterDisabledModel(double x, double y, double z, BlockPos pos, IBlockAccess access,
                                                         IBlockState state, CallbackInfo callbackInfo) {
        Deque<Integer> programs = AUSM$fallbackModelPrograms.get();
        if (!programs.isEmpty()) {
            GL20.glUseProgram(programs.pop());
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void ausm$beginSkyTextureScope(CallbackInfo ci) {
        AUSM$skyTexturePhaseStack.get().push(false);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lopenblocks/client/renderer/SkyBlockRenderer;bindSkyTexture()V"
            ),
            remap = false,
            require = 0
    )
    private void ausm$bindSkyTextureWithSkyProgram(@Coerce Object skyRenderer) {
        Deque<Boolean> stack = AUSM$skyTexturePhaseStack.get();
        PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("before-phase");
        boolean switched = PipelineContext.getInstance().beginOpenBlocksSkyTexturePhase();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        stack.push(switched);
        PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("after-phase");
        MinecraftReflectionCompat.invoke(skyRenderer, new String[]{"bindSkyTexture"}, MinecraftReflectionCompat.NO_PARAMETERS);
        PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("after-bind-texture");
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lopenmods/renderer/CachedRendererFactory$CachedRenderer;render()V",
                    ordinal = 0,
                    shift = At.Shift.BEFORE
            ),
            require = 0,
            remap = false
    )
    private void ausm$probeBeforeSkyTextureQuad(CallbackInfo ci) {
        PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("before-quad");
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lopenmods/renderer/CachedRendererFactory$CachedRenderer;render()V",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private void ausm$probeAfterSkyTextureQuad(CallbackInfo ci) {
        PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("after-quad");
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void ausm$endSkyTextureScope(CallbackInfo ci) {
        Deque<Boolean> stack = AUSM$skyTexturePhaseStack.get();
        if (!stack.isEmpty() && stack.pop()) {
            PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("before-phase-end");
            PipelineContext.getInstance().endOpenBlocksSkyTexturePhase();
            PipelineContext.getInstance().logOpenBlocksSkyProjectionProbe("after-phase-end");
        }
    }
}
