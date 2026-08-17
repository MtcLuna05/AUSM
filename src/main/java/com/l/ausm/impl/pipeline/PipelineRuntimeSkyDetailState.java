package com.l.ausm.impl.pipeline;

import com.l.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.l.ausm.impl.MainMod;
import com.l.ausm.impl.pipeline.render.TextureBinder;
import com.l.ausm.impl.pipeline.shader.ShaderProgram;
import com.l.ausm.impl.util.MinecraftReflectionCompat;
import java.awt.Color;
import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import static com.l.ausm.impl.pipeline.PipelineGlState.disablePipelineVertexAttributes;
import static com.l.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.l.ausm.impl.pipeline.PipelineGlState.restoreVanillaClientRenderState;
import static com.l.ausm.impl.pipeline.PipelineGlState.unbindShaderImages;
import static com.l.ausm.impl.pipeline.PipelineProbeLimits.MAX_HAND_GBUFFER_PROBE_LOGS;

abstract class PipelineRuntimeDiagnosticsState7 extends PipelineRuntimeDiagnosticsState6 {
    protected static int skyDetailKind(String resourceName) {
        if (resourceName == null) {
            return 0;
        }
        String name = resourceName.toLowerCase(Locale.ROOT);
        if (!name.contains("botania:")) {
            return 0;
        }
        if (name.contains("planet")) {
            return 1;
        }
        if (name.contains("rainbow")) {
            return 3;
        }
        if (name.contains("skybox") || name.contains("ribbon")) {
            return 2;
        }
        return 0;
    }

    protected void setAstralConstellationColors(Object constellation) {
        Color tierColor = PipelineRuntimeState.astralColor(constellation, "getTierRenderColor", Color.WHITE);
        Color constellationColor = PipelineRuntimeState.astralColor(constellation, "getConstellationColor", tierColor);
        PipelineRuntimeState.setColor(currentAstralConstellationColor, constellationColor);
        PipelineRuntimeState.setColor(currentAstralTierColor, tierColor);
    }

    protected void resetAstralConstellationColors() {
        PipelineRuntimeState.setColor(currentAstralConstellationColor, null);
        PipelineRuntimeState.setColor(currentAstralTierColor, null);
    }

    protected static Color astralColor(Object constellation, String methodName, Color fallback) {
        if (constellation != null) {
            try {
                Method method = constellation.getClass().getMethod(methodName);
                Object result = method.invoke(constellation);
                if (result instanceof Color color) {
                    return color;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return fallback;
    }

    protected static void setColor(float[] target, Color color) {
        if (color == null) {
            target[0] = 1.0f;
            target[1] = 1.0f;
            target[2] = 1.0f;
            return;
        }
        target[0] = color.getRed() / 255.0f;
        target[1] = color.getGreen() / 255.0f;
        target[2] = color.getBlue() / 255.0f;
    }

    public boolean beginItemRenderPhase() {
        if (!self().shouldRouteRenderItemThroughPipeline()) {
            return false;
        }
        self().beginPhase(WorldRenderingPhase.ITEM);
        return true;
    }

    public boolean beginItemGlintPhase() {
        if (!self().shouldRouteItemGlintThroughPipeline()) {
            return false;
        }
        itemGlintMaskDepth++;
        self().beginPhase(WorldRenderingPhase.ARMOR_GLINT);
        return true;
    }

    public void endItemGlintPhase() {
        try {
            self().endPass();
        } finally {
            if (itemGlintMaskDepth > 0) {
                itemGlintMaskDepth--;
            }
        }
    }

    /**
     * Vanilla selects GL_EQUAL before both item-glint model submissions.  Use
     * direct driver calls at the draw boundary as well: GUI/world transitions
     * can leave GlStateManager's cached depth function out of sync, causing the
     * opaque glint texture to cover the item's complete baked quad.
     */
    public void prepareItemGlintDrawState() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_EQUAL);
    }

    /**
     * Astral's RenderWorldLast particles use the regular fixed-function entity
     * vertex layout. Route that layout through Entree's translucent entity MRT
     * program instead of writing program-0 colour into only attachment 0.
     */
    public boolean beginAstralEffectOverlayPhase() {
        astralEffectOverlayActive = true;
        if (!self().beginPhaseIfActive(WorldRenderingPhase.ENTITIES_TRANSLUCENT)) {
            astralEffectOverlayActive = false;
            return false;
        }
        disablePipelineVertexAttributes();
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        ShaderProgram program = self().activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "ausmAstralEffectOverlay");
        }
        self().forensicGlTrace("astral-overlay-shader-pass", "pass=" + activePass);
        return activePass != null;
    }

    /**
     * Clears the unlit Astral flag before the next normal shader bind.
     */
    public void endAstralEffectOverlayPhase() {
        astralEffectOverlayActive = false;
        self().endPass();
    }

    public void prepareHandItemRenderState() {
        if (!isPipelineActive || !worldFrameActive || self().renderingGuiScreen()) {
            return;
        }
        WorldRenderingPhase phase = self().getPhase();
        if (phase != WorldRenderingPhase.HAND_SOLID) {
            return;
        }
        MinecraftReflectionCompat.glStateDisableBlend();
        resetIndexedBlendState();
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void logHandGbufferProbe(String stage) {
        self().forensicGlTrace("hand-" + stage, "activePass=" + activePass + ", phase=" + self().getPhase());
        if (!isPipelineActive || !pingPongManager.isInitialized()
                || handGbufferProbeLogs >= MAX_HAND_GBUFFER_PROBE_LOGS) {
            return;
        }
        handGbufferProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMHandGbufferProbe] call={} stage={} activePass={} phase={} worldFrame={} drawFbo={} draw0={} draw1={} program={} blend={} alpha={} depth={} depthMask={}",
                handGbufferProbeLogs,
                stage,
                activePass,
                self().getPhase(),
                worldFrameActive,
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL20.GL_DRAW_BUFFER0),
                GL11.glGetInteger(GL20.GL_DRAW_BUFFER1),
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glIsEnabled(GL11.GL_ALPHA_TEST),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        );
    }

    public void prepareVanillaHandRenderState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateEnableCull();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
        self().restoreVanillaFixedFunctionTextureState(mc);
        if (isPipelineActive && worldFrameActive && activePass != null
                && (self().getPhase() == WorldRenderingPhase.HAND_SOLID
                || self().getPhase() == WorldRenderingPhase.HAND_TRANSLUCENT)) {
            self().bindPass(activePass);
        }
    }

    public void prepareUntexturedEmissiveWorldRenderState() {
        if (!isPipelineActive || !worldFrameActive || self().renderingGuiScreen()) {
            return;
        }
        TextureBinder.bindFallbackWhiteTexture();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.0F);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE,
                GL11.GL_ONE,
                GL11.GL_ZERO
        );
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateDepthMask(false);
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareGuiItemRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null && !self().renderingGuiScreen()) {
            return;
        }

        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        if (!shaderlessBloomExtractionActive) {
            self().unbindShaderStorageBuffers();
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareFlatGuiBackgroundRenderState() {
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        self().unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateDisableDepth();
        GL11.glDepthMask(false);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ZERO,
                GL11.GL_ONE
        );
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareGuiEntityPreviewRenderState() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null && !self().renderingGuiScreen()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_SCISSOR_BIT
                | GL11.GL_POLYGON_BIT
                | GL11.GL_TEXTURE_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_VIEWPORT_BIT);
        guiEntityPreviewStateDepth++;

        // Entity models use Minecraft's normal counter-clockwise winding even
        // inside GuiInventory's mirrored transform.
        GL11.glFrontFace(GL11.GL_CCW);
        MinecraftReflectionCompat.glStateCullFaceBack();
        // This basic preview state is required with and without a shader pack.
        // Use real driver calls because the world/UI transition can desync the
        // GlStateManager cache from OpenGL.
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        if (!isPipelineActive) {
            return;
        }

        self().bindMinecraftFramebufferForGui(mc);
        if (MinecraftReflectionCompat.entityRenderer(mc) != null) {
            MinecraftReflectionCompat.disableLightmap(MinecraftReflectionCompat.entityRenderer(mc));
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        MinecraftReflectionCompat.glStateDisableBlend();
        MinecraftReflectionCompat.glStateDisableLighting();
        MinecraftReflectionCompat.glStateDisableColorMaterial();
        MinecraftReflectionCompat.glStateDisableCull();
        GL11.glFrontFace(GL11.GL_CCW);
        MinecraftReflectionCompat.glStateCullFaceBack();
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void finishGuiEntityPreviewRenderState() {
        if (guiEntityPreviewStateDepth <= 0) {
            return;
        }
        guiEntityPreviewStateDepth--;
        GL11.glPopAttrib();
        if (!isPipelineActive) {
            return;
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.setClientActiveTexture(MinecraftReflectionCompat.defaultTexUnit());
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        self().unbindShaderStorageBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        if (isPipelineActive && self().isRenderingGuiScreen()) {
            self().prepareGuiState();
        } else {
            self().restoreGuiSafeRenderState("gui-entity-preview");
        }
    }

    public boolean isInventoryEntityPreview(Entity entity, double x, double y, double z) {
        if (entity == null) {
            return false;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        Object screen = mc != null ? MinecraftReflectionCompat.currentScreen(mc) : null;
        return screen != null
                && "net.minecraft.client.gui.inventory.GuiInventory".equals(screen.getClass().getName())
                && entity == MinecraftReflectionCompat.player(mc);
    }

    public boolean beginGuiItemStateScope() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null && !self().renderingGuiScreen()) {
            return false;
        }
        GL11.glPushAttrib(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_ENABLE_BIT | GL11.GL_POLYGON_BIT);
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        // GlStateManager's cache can survive a shader/fixed-function boundary
        // while the driver state does not. The GUI item's alpha coverage must
        // be real before it writes the depth mask consumed by GL_EQUAL glint.
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glFrontFace(GL11.GL_CW);
        return true;
    }

    public void endGuiItemStateScope() {
        GL11.glPopAttrib();
    }

    /**
     * Reassert the base item's coverage state at the actual Tessellator draw.
     * Forge/custom item paths may touch alpha or depth state after the outer
     * RenderItem scope began; if transparent texels write depth here, the
     * following GL_EQUAL glint necessarily becomes a rectangular quad.
     */
    public void prepareGuiItemBaseDrawState() {
        if (!self().isRenderingGuiItemContext()) {
            return;
        }
        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(true);
    }

    public boolean beginGuiBuiltInItemStateScope() {
        if (!isPipelineActive || !self().renderingGuiScreen()) {
            return false;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_POLYGON_BIT);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glFrontFace(GL11.GL_CW);
        return true;
    }

    public void endGuiBuiltInItemStateScope() {
        GL11.glPopAttrib();
    }

    public void prepareGuiItemGlintRenderState() {
        if (!isPipelineActive) {
            return;
        }
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        if (mc == null || MinecraftReflectionCompat.currentScreen(mc) == null && !self().renderingGuiScreen()) {
            return;
        }

        MinecraftReflectionCompat.glUseProgram(0);
        TextureBinder.restoreDefaultTextureUnit();
        disablePipelineVertexAttributes();
        restoreVanillaClientRenderState();
        unbindShaderImages();
        self().unbindShaderStorageBuffers();
        resetIndexedBlendState();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(0.0F, 0.0F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateEnableTexture2D();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(GL11.GL_GREATER, 0.1F);
        MinecraftReflectionCompat.glStateEnableDepth();
        MinecraftReflectionCompat.glStateDepthMask(true);
        MinecraftReflectionCompat.glStateDisableCull();
        MinecraftReflectionCompat.glStateColorMask(true, true, true, true);
        MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public void prepareHandItemDrawState(String source) {
        self().prepareHandItemRenderState();
        if (!isPipelineActive || !worldFrameActive || self().renderingGuiScreen() || self().getPhase() != WorldRenderingPhase.HAND_SOLID) {
            return;
        }
        self().uploadCurrentRenderedItemId();
    }

    public void probeHandGbufferAfterRender() {
        // Disabled: this probe performs framebuffer readbacks and is too costly
        // for regular gameplay.
    }

    protected boolean shouldRouteRenderItemThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || self().renderingGuiScreen()) {
            return false;
        }
        WorldRenderingPhase phase = self().getPhase();
        return phase != WorldRenderingPhase.HAND_SOLID
                && phase != WorldRenderingPhase.HAND_TRANSLUCENT
                && phase != WorldRenderingPhase.ARMOR_GLINT
                && phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    protected boolean shouldRouteItemGlintThroughPipeline() {
        if (!isPipelineActive || !worldFrameActive || renderingShadowMap || self().renderingGuiScreen()) {
            return false;
        }
        WorldRenderingPhase phase = self().getPhase();
        return phase != WorldRenderingPhase.BLOCK_ENTITIES
                && phase != WorldRenderingPhase.BLOCK_ENTITIES_TRANSLUCENT;
    }

    protected boolean renderingGuiScreen() {
        return renderingGui || guiRenderDepth > 0;
    }

    public boolean isRenderingGuiScreen() {
        return self().renderingGuiScreen();
    }
}
