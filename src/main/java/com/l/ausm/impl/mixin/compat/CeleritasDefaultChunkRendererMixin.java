package com.l.ausm.impl.mixin.compat;

import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.MainMod;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(targets = "org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class CeleritasDefaultChunkRendererMixin {
    @Unique
    private static final AtomicInteger ausm$renderBoundaryProbeCount = new AtomicInteger();

    @Inject(
            method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void ausm$probeRenderHead(
            @Coerce Object matrices,
            @Coerce Object commandList,
            @Coerce Object renderLists,
            @Coerce Object renderPass,
            @Coerce Object cameraTransform,
            @Coerce Object previousCameraTransform,
            CallbackInfo ci
    ) {
        ausm$logRenderBoundary("head", renderPass);
    }

    @Inject(
            method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void ausm$probeRenderReturn(
            @Coerce Object matrices,
            @Coerce Object commandList,
            @Coerce Object renderLists,
            @Coerce Object renderPass,
            @Coerce Object cameraTransform,
            @Coerce Object previousCameraTransform,
            CallbackInfo ci
    ) {
        ausm$logRenderBoundary("return", renderPass);
    }

    @Unique
    private static void ausm$logRenderBoundary(String stage, Object renderPass) {
        int call = ausm$renderBoundaryProbeCount.incrementAndGet();
        if (call > 48 || !PipelineContext.getInstance().isPipelineActive()) {
            return;
        }
        com.l.ausm.impl.MainMod.LOGGER.info(
                "[AUSMCeleritasRendererBoundaryProbe] call={} stage={} pass={} program={} drawFbo={} readFbo={} glError={}",
                call,
                stage,
                renderPass,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                GL11.glGetError()
        );
    }

    @Inject(
            method = "render(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkRenderMatrices;Lorg/embeddedt/embeddium/impl/gl/device/CommandList;Lorg/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderListIterable;Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/embeddedt/embeddium/impl/render/chunk/ShaderChunkRenderer;begin(Lorg/embeddedt/embeddium/impl/render/chunk/terrain/TerrainRenderPass;)V",
                    shift = At.Shift.AFTER
            ),
            remap = false
    )
    private void ausm$restorePipelineAfterNativeBegin(
            @Coerce Object matrices,
            @Coerce Object commandList,
            @Coerce Object renderLists,
            @Coerce Object renderPass,
            @Coerce Object cameraTransform,
            @Coerce Object previousCameraTransform,
            CallbackInfo ci
    ) {
        PipelineContext.getInstance().rebindActivePipelinePassAfterRendererSetup();
        MainMod.LOGGER.info(
                "[AUSMCeleritasShaderBindProbe] stage=after-native-begin renderPass={} glProgram={} glError={}",
                renderPass,
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetError()
        );
    }
}
