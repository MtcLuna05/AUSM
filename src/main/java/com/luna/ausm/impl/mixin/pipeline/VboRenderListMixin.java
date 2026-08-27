package com.luna.ausm.impl.mixin.pipeline;

import com.luna.ausm.impl.pipeline.PipelineContext;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.luna.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.luna.ausm.impl.pipeline.vertex.IPipelineRenderChunk;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VboRenderList.class)
public class VboRenderListMixin {
    @Unique
    private static final int AUSM_TRANSLUCENT_VBO_LOG_LIMIT = 16;

    @Unique
    private static final int AUSM_ARRAY_POINTER_MODE_UNKNOWN = 0;

    @Unique
    private static final int AUSM_ARRAY_POINTER_MODE_VANILLA = 1;

    @Unique
    private static final int AUSM_ARRAY_POINTER_MODE_PIPELINE = 2;

    @Unique
    private static int ausm$translucentVboLogs;

    @Unique
    private static int ausm$arrayPointerMode = AUSM_ARRAY_POINTER_MODE_UNKNOWN;

    @Unique
    private boolean ausm$currentChunkUsesPipelineVertexFormat;

    @Inject(method = "renderChunkLayer", at = @At("HEAD"))
    private void ausm$prepareTranslucentChunkLayer(BlockRenderLayer layer, CallbackInfo ci) {
        ausm$arrayPointerMode = AUSM_ARRAY_POINTER_MODE_UNKNOWN;
        if (layer != BlockRenderLayer.TRANSLUCENT) {
            return;
        }

        Minecraft mc = MinecraftReflectionCompat.minecraft();
        boolean shaderPipelineActive = PipelineContext.getInstance().isActive();
        if (!shaderPipelineActive) {
            MinecraftReflectionCompat.glUseProgram(0);
        }
        FixedFunctionGlState.prepareTranslucentBlockLayer(mc);
        if (!shaderPipelineActive) {
            ausm$forceTranslucentFixedFunctionState();
        }
        ausm$logTranslucentVboState("head", layer, false);
    }

    @Redirect(
            method = "renderChunkLayer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/RenderChunk;getVertexBufferByLayer(I)Lnet/minecraft/client/renderer/vertex/VertexBuffer;"
            )
    )
    private VertexBuffer ausm$captureChunkVertexFormat(RenderChunk renderChunk, int layer) {
        BlockRenderLayer blockLayer = ausm$layerByOrdinal(layer);
        ausm$currentChunkUsesPipelineVertexFormat = renderChunk instanceof IPipelineRenderChunk pipelineRenderChunk
                && pipelineRenderChunk.ausm$usesPipelineVertexFormat(blockLayer);
        return MinecraftReflectionCompat.renderChunkVertexBuffer(renderChunk, layer);
    }

    @Redirect(
            method = "renderChunkLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/VboRenderList;setupArrayPointers()V")
    )
    private void ausm$setupArrayPointersForCurrentChunk(VboRenderList instance) {
        if (ausm$currentChunkUsesPipelineVertexFormat) {
            ausm$setupPipelineArrayPointers();
        } else {
            ausm$setupVanillaArrayPointers();
        }
    }

    @ModifyArg(
            method = "renderChunkLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/vertex/VertexBuffer;drawArrays(I)V"),
            index = 0
    )
    private int ausm$tessellatedChunkDrawMode(int drawMode) {
        return PipelineContext.getInstance().drawModeForActiveProgram(drawMode);
    }

    @Unique
    private static void ausm$setupPipelineArrayPointers() {
        int stride = ExtendedVertexFormats.size(ExtendedVertexFormats.PIPELINE_BLOCK);

        ausm$preparePipelineArrayPointerState();

        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187420_d", "glVertexPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 3, GL11.GL_FLOAT, stride, 0);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187406_e", "glColorPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 4, GL11.GL_UNSIGNED_BYTE, stride, 12);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());

        GL11.glNormalPointer(GL11.GL_BYTE, stride, (long) ExtendedVertexFormats.PIPELINE_BLOCK_NORMAL_OFFSET);

        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE,
                2,
                GL11.GL_FLOAT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_TEX_COORD_OFFSET
        );

        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                true,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_TANGENT_OFFSET
        );

        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE,
                4,
                GL11.GL_SHORT,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MC_ENTITY_OFFSET
        );

        ExtendedVertexFormats.vertexAttribPointer(
                ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE,
                4,
                GL11.GL_BYTE,
                false,
                stride,
                (long) ExtendedVertexFormats.PIPELINE_BLOCK_MID_BLOCK_OFFSET
        );
    }

    @Unique
    private static void ausm$setupVanillaArrayPointers() {
        ausm$prepareVanillaArrayPointerState();

        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187420_d", "glVertexPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 3, GL11.GL_FLOAT, 28, 0);
        MinecraftReflectionCompat.invoke(GlStateManager.class, new String[]{"func_187406_e", "glColorPointer"},
                new Class<?>[]{int.class, int.class, int.class, int.class}, 4, GL11.GL_UNSIGNED_BYTE, 28, 12);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
    }

    @Unique
    private static void ausm$preparePipelineArrayPointerState() {
        if (ausm$arrayPointerMode == AUSM_ARRAY_POINTER_MODE_PIPELINE) {
            return;
        }

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.enableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ausm$arrayPointerMode = AUSM_ARRAY_POINTER_MODE_PIPELINE;
    }

    @Unique
    private static void ausm$prepareVanillaArrayPointerState() {
        if (ausm$arrayPointerMode == AUSM_ARRAY_POINTER_MODE_VANILLA) {
            return;
        }

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        ausm$setGenericAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 1.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE, 1.0F, 0.0F, 0.0F, 1.0F);
        ausm$setGenericAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE, 0.0F, 0.0F, 0.0F, 0.0F);
        ausm$arrayPointerMode = AUSM_ARRAY_POINTER_MODE_VANILLA;
    }

    @Unique
    private static void ausm$setGenericAttribute(int index, float x, float y, float z, float w) {
        if (index >= 0 && index < GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS)) {
            GL20.glVertexAttrib4f(index, x, y, z, w);
        }
    }

    @Unique
    private static BlockRenderLayer ausm$layerByOrdinal(int ordinal) {
        BlockRenderLayer[] layers = BlockRenderLayer.values();
        return ordinal >= 0 && ordinal < layers.length ? layers[ordinal] : null;
    }

    @Inject(method = "renderChunkLayer", at = @At("RETURN"))
    private void ausm$disablePipelineAttributes(BlockRenderLayer layer, CallbackInfo ci) {
        ausm$currentChunkUsesPipelineVertexFormat = false;
        ausm$arrayPointerMode = AUSM_ARRAY_POINTER_MODE_UNKNOWN;
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_MID_TEX_COORD_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_TANGENT_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.MC_ENTITY_ATTRIBUTE);
        ExtendedVertexFormats.disableAttribute(ExtendedVertexFormats.AT_MID_BLOCK_ATTRIBUTE);
        PipelineContext.getInstance().resetChunkFadeUniform();
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            ausm$logTranslucentVboState("return", layer, false);
        }
    }

    @Unique
    private static void ausm$logTranslucentVboState(String stage, BlockRenderLayer layer, boolean pipelineFormat) {
        if (layer != BlockRenderLayer.TRANSLUCENT
                || !PipelineContext.getInstance().isActive()
                || ausm$translucentVboLogs >= AUSM_TRANSLUCENT_VBO_LOG_LIMIT) {
            return;
        }
        ausm$translucentVboLogs++;
        StringBuilder drawBuffers = new StringBuilder();
        for (int slot = 0; slot < 6; slot++) {
            if (slot > 0) {
                drawBuffers.append(';');
            }
            drawBuffers.append(slot).append('=').append(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + slot));
        }
        MainMod.LOGGER.info(
                "[AUSMWaterVbo] call={} stage={} phase={} drawFbo={} program={} drawBuffers={} blend={} depth={} depthMask={} depthFunc={} pipelineFormat={}",
                ausm$translucentVboLogs,
                stage,
                PipelineContext.getInstance().getPhase(),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                drawBuffers,
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                pipelineFormat
        );
        PipelineContext.getInstance().logSpecialLayerProbe("water-vbo-" + stage);
    }

    @Unique
    private static void ausm$forceTranslucentFixedFunctionState() {
        FixedFunctionGlState.forceTranslucentBlockLayer();
    }

    @Unique
    private static boolean ausm$lightmapTexCoordArrayEnabled() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.lightmapTexUnit());
        boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
        return enabled;
    }
}
