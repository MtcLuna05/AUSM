package com.l.ausm.impl.mixin.pipeline;

import com.l.ausm.api.pipeline.fbo.*;
import com.l.ausm.api.pipeline.shader.*;
import com.l.ausm.api.pipeline.pack.*;

import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.PipelineContext;
import com.l.ausm.impl.pipeline.render.FixedFunctionGlState;
import com.l.ausm.impl.pipeline.vertex.ExtendedVertexFormats;
import com.l.ausm.impl.pipeline.vertex.IPipelineRenderChunk;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.VboRenderList;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
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
    private static final int AUSM_TRANSLUCENT_VBO_LOG_LIMIT = 0;

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

        Minecraft mc = com.l.ausm.impl.util.MinecraftReflectionCompat.minecraft();
        boolean shaderPipelineActive = PipelineContext.getInstance().isActive();
        if (!shaderPipelineActive) {
            com.l.ausm.impl.util.MinecraftReflectionCompat.glUseProgram(0);
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
        return renderChunk.getVertexBufferByLayer(layer);
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

        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187420_d", "glVertexPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (3), (GL11.GL_FLOAT), (stride), (0));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187406_e", "glColorPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (4), (GL11.GL_UNSIGNED_BYTE), (stride), (12));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, stride, 16);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, stride, 24);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());

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

        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187420_d", "glVertexPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (3), (GL11.GL_FLOAT), (28), (0));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.invoke(net.minecraft.client.renderer.GlStateManager.class, new String[] {"func_187406_e", "glColorPointer"},
                new Class<?>[] {int.class, int.class, int.class, int.class}, (4), (GL11.GL_UNSIGNED_BYTE), (28), (12));;
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        com.l.ausm.impl.util.MinecraftReflectionCompat.glStateGlTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
    }

    @Unique
    private static void ausm$preparePipelineArrayPointerState() {
        if (ausm$arrayPointerMode == AUSM_ARRAY_POINTER_MODE_PIPELINE) {
            return;
        }

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
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
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.defaultTexUnit());
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
        // Probe disabled.
}

    @Unique
    private static void ausm$forceTranslucentFixedFunctionState() {
        FixedFunctionGlState.forceTranslucentBlockLayer();
    }

    @Unique
    private static boolean ausm$lightmapTexCoordArrayEnabled() {
        int previousClientTexture = GL11.glGetInteger(GL13.GL_CLIENT_ACTIVE_TEXTURE);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(com.l.ausm.impl.util.MinecraftReflectionCompat.lightmapTexUnit());
        boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_COORD_ARRAY);
        com.l.ausm.impl.util.MinecraftReflectionCompat.setClientActiveTexture(previousClientTexture);
        return enabled;
    }
}
