package com.luna.ausm.impl.pipeline;

import com.luna.ausm.api.pipeline.fbo.Attachment;
import com.luna.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.luna.ausm.api.pipeline.pack.ShaderBlendMode;
import com.luna.ausm.api.pipeline.pack.ShaderOitSettings;
import com.luna.ausm.api.pipeline.shader.ProgramStage;
import com.luna.ausm.api.pipeline.shader.RenderPass;
import com.luna.ausm.api.pipeline.shader.WorldRenderingPhase;
import com.luna.ausm.impl.MainMod;
import com.luna.ausm.impl.pipeline.render.ShaderSamplerState;
import com.luna.ausm.impl.pipeline.render.TextureBinder;
import com.luna.ausm.impl.pipeline.shader.PipelineProgram;
import com.luna.ausm.impl.pipeline.shader.ShaderKey;
import com.luna.ausm.impl.pipeline.shader.ShaderProgram;
import com.luna.ausm.impl.util.MinecraftReflectionCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBTessellationShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GLContext;

import static com.luna.ausm.impl.pipeline.PipelineGlState.markShaderStorageBuffersBound;
import static com.luna.ausm.impl.pipeline.PipelineGlState.maxDrawBuffers;
import static com.luna.ausm.impl.pipeline.PipelineGlState.resetIndexedBlendState;
import static com.luna.ausm.impl.pipeline.PipelineGlState.setIndexedBlend;
import static com.luna.ausm.impl.pipeline.PipelineProbeLimits.MAX_ENTITY_BLEND_PROBE_LOGS;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.BLOCK_ENTITY_TRANSLUCENT_BLEND;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.OIT_COEFFICIENT_BLEND;
import static com.luna.ausm.impl.pipeline.PipelineRenderConstants.WATER_BLEND_MODE;

abstract class PipelineRuntimeDiagnosticsState8 extends PipelineRuntimeDiagnosticsState7 {
    public void beginGuiItemRenderScope() {
        guiItemRenderDepth++;
    }

    public void endGuiItemRenderScope() {
        if (guiItemRenderDepth > 0) {
            guiItemRenderDepth--;
        }
    }

    public boolean isRenderingGuiItemContext() {
        return self().renderingGuiScreen() || guiItemRenderDepth > 0;
    }

    public boolean shouldDrawActiveProgramAsPatches() {
        return self().hasBoundPipelineProgram()
                && activeProgramTessellated
                && (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_tessellation_shader);
    }

    public int drawModeForActiveProgram(int drawMode) {
        if (!self().shouldDrawActiveProgramAsPatches()) {
            return drawMode;
        }
        if (drawMode == GL11.GL_QUADS || drawMode == GL40.GL_PATCHES) {
            PipelineRuntimeState.setPatchVertices(4);
            return GL40.GL_PATCHES;
        }
        return drawMode;
    }

    public boolean shouldDrawFullscreenAsTriangles() {
        return self().hasBoundPipelineProgram() && activeProgramGeometric && !self().shouldDrawActiveProgramAsPatches();
    }

    protected boolean hasBoundPipelineProgram() {
        if (!isPipelineActive || self().renderingGuiScreen() || activePass == null) {
            return false;
        }
        PipelineProgram pipelineProgram = self().effectivePipelineProgram(activePass);
        ShaderProgram shaderProgram = pipelineProgram != null ? pipelineProgram.shaderProgram() : null;
        return shaderProgram != null && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == shaderProgram.getId();
    }

    protected static void setPatchVertices(int vertices) {
        if (GLContext.getCapabilities().OpenGL40) {
            GL40.glPatchParameteri(GL40.GL_PATCH_VERTICES, vertices);
        } else if (GLContext.getCapabilities().GL_ARB_tessellation_shader) {
            ARBTessellationShader.glPatchParameteri(ARBTessellationShader.GL_PATCH_VERTICES, vertices);
        }
    }

    protected RenderPass passForPhase(WorldRenderingPhase phase) {
        return renderingShadowMap ? phase.shadowPass() : phase.mainPass();
    }

    protected boolean bindPass(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        PipelineProgram bindingProgram = self().effectivePipelineProgram(pass);
        if (bindingProgram == null) {
            return false;
        }

        ShaderProgram program = bindingProgram.shaderProgram();
        if (program == null) {
            return false;
        }

        activeShaderKey = ShaderKey.fromRenderPass(pass);
        activeProgramTessellated = program.isTessellated();
        activeProgramGeometric = program.isGeometric();
        self().applyAlphaTest(pass);
        List<Attachment> drawBuffers = self().effectiveDrawBuffersForCurrentPhase(bindingProgram);
        self().applyBlendMode(pass, drawBuffers);
        self().applyOitDepthState(pass);
        self().applyGbufferDepthState(pass);
        self().applySkyDepthState(pass);
        self().applyHandRenderState(pass);
        self().applyBeaconBeamDepthState(pass);
        self().applyBlockEntityTranslucentDepthState(pass);
        self().configureGbufferDrawBuffers(bindingProgram, drawBuffers);
        self().configureShadowDrawBuffers(bindingProgram, drawBuffers);
        if (bindingProgram.stage() == ProgramStage.GBUFFERS) {
            self().restoreVanillaWorldTextureBindings();
            MinecraftReflectionCompat.glStateColor(1.0F, 1.0F, 1.0F, 1.0F);
            MinecraftReflectionCompat.glStateEnableTexture2D();
            GL11.glColorMask(true, true, true, true);
            boolean blockAtlasPass = self().usesBlockAtlas(pass);
            if (blockAtlasPass) {
                self().bindBlockAtlas();
            }
            TextureBinder.bindGbufferRenderTargetSamplers();
            if (blockAtlasPass) {
                self().bindBlockAtlas();
            }
        }
        if (bindingProgram.stage().readsDeferredTextures()) {
            TextureBinder.bindDeferredTextures();
        } else {
            TextureBinder.bindNoiseTexture();
        }
        if (bindingProgram.stage() != ProgramStage.SHADOW) {
            TextureBinder.bindShadowTextures(bindingProgram.pass());
        }
        if (bindingProgram.stage() == ProgramStage.GBUFFERS) {
            TextureBinder.bindMaterialFallbackTextures();
            if (pass == RenderPass.GBUFFERS_ARMOR_GLINT && itemGlintMaskDepth > 0) {
                self().bindItemGlintBaseAtlas();
            }
        }

        program.bind();
        self().bindProgramResources(bindingProgram.pass(), program);
        if (bindingProgram.stage() == ProgramStage.SHADOW && self().getPhase().usesBlockAtlas()) {
            self().bindBlockAtlas();
        }
        activePass = pass;
        if (pass == RenderPass.GBUFFERS_WATER) {
            self().logWaterRoutingProbe("after-bind", bindingProgram, drawBuffers);
        }
        return true;
    }

    protected PipelineProgram effectivePipelineProgram(RenderPass pass) {
        RenderPass current = pass;
        while (current != null) {
            PipelineProgram program = programs.get(current);
            if (program != null && program.enabled() && program.shaderProgram() != null) {
                return program;
            }
            current = current.fallback();
        }
        return null;
    }

    protected void bindProgramResources(RenderPass pass, ShaderProgram program) {
        shaderImages.bind(program);
        shaderStorageBuffers.bind();
        if (shaderStorageBuffers.active()) {
            markShaderStorageBuffersBound();
        }
        uniformRegistry.uploadAll(program);
        if (!packDirectives.customUniforms().isEmpty()) {
            packDirectives.customUniforms().upload(program, uniformRegistry.scalarValuesInto(
                    customUniformScalarScratch,
                    packDirectives.customUniforms().builtinDependencies()
            ));
        }
        // Shaderpack texture directives explicitly replace named samplers.
        // They must win over the standard framebuffer bindings and any
        // resource/uniform setup above (for example,
        // texture.deferred.colortex3 in Complementary's cloud pass).
        customTextures.bind(pass, program);
    }

    protected List<Attachment> effectiveDrawBuffersForCurrentPhase(PipelineProgram pipelineProgram) {
        List<Attachment> drawBuffers = pipelineProgram.effectiveDrawBuffers(programs);
        if (pipelineProgram.stage() != ProgramStage.GBUFFERS || self().getPhase() != WorldRenderingPhase.STARS || !drawBuffers.contains(Attachment.AUX4)) {
            return drawBuffers;
        }

        List<Attachment> filtered = new ArrayList<>(drawBuffers.size());
        for (Attachment attachment : drawBuffers) {
            if (attachment != Attachment.AUX4) {
                filtered.add(attachment);
            }
        }
        return filtered.isEmpty() ? drawBuffers : List.copyOf(filtered);
    }

    protected boolean usesBlockAtlas(RenderPass pass) {
        WorldRenderingPhase phase = self().getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return phase.usesBlockAtlas();
        }
        return pass == RenderPass.GBUFFERS_TERRAIN
                || pass == RenderPass.GBUFFERS_TERRAIN_SOLID
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP
                || pass == RenderPass.GBUFFERS_TERRAIN_CUTOUT
                || pass == RenderPass.GBUFFERS_WATER
                || pass == RenderPass.GBUFFERS_DAMAGEDBLOCK
                || pass == RenderPass.DH_TERRAIN
                || pass == RenderPass.DH_WATER;
    }

    protected void bindBlockAtlas() {
        TextureBinder.restoreDefaultTextureUnit();
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null) {
            return;
        }
        ITextureObject texture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
        if (texture == null) {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
            texture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
        }
        if (texture != null) {
            int textureId = MinecraftReflectionCompat.glTextureId(texture);
            MinecraftReflectionCompat.glStateBindTexture(textureId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        } else {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
        }
        // Vanilla restores GL_NEAREST_MIPMAP_LINEAR around terrain layers. Clamp the atlas
        // to level zero as well as selecting nearest filtering so distant texels remain
        // pixel-perfect even after those per-layer filter restores run.
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        ShaderSamplerState.clampTextureAnisotropyIfNeeded(GL11.GL_TEXTURE_2D);
    }

    protected void bindItemGlintBaseAtlas() {
        Minecraft mc = MinecraftReflectionCompat.minecraft();
        TextureManager textureManager = MinecraftReflectionCompat.textureManager(mc);
        if (textureManager == null) {
            return;
        }
        ITextureObject texture = MinecraftReflectionCompat.texture(
                textureManager, MinecraftReflectionCompat.blocksTexture());
        if (texture == null) {
            MinecraftReflectionCompat.bindTexture(textureManager, MinecraftReflectionCompat.blocksTexture());
            texture = MinecraftReflectionCompat.texture(textureManager, MinecraftReflectionCompat.blocksTexture());
        }
        if (texture != null) {
            TextureBinder.bindRawTexture(TextureBinder.ITEM_GLINT_BASE_ATLAS_TEXTURE_UNIT,
                    MinecraftReflectionCompat.glTextureId(texture));
        }
        TextureBinder.restoreDefaultTextureUnit();
    }

    protected void applyAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderAlphaTest alphaTest = pipelineProgram == null ? null : pipelineProgram.directives().alphaTestOverride();
        if (alphaTest == null) {
            alphaTest = PipelineRuntimeState.defaultAlphaTest(pass);
        }

        currentAlphaTestReference = alphaTest.reference();
        if (alphaTest.function() == GL11.GL_ALWAYS) {
            MinecraftReflectionCompat.glStateDisableAlpha();
        } else {
            MinecraftReflectionCompat.glStateEnableAlpha();
        }
        MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());
    }

    protected static ShaderAlphaTest defaultAlphaTest(RenderPass pass) {
        PipelineProgram pipelineProgram = INSTANCE.programs.get(pass);
        ShaderKey key = pipelineProgram == null ? ShaderKey.fromRenderPass(pass) : pipelineProgram.shaderKey();
        return key == null ? ShaderAlphaTest.ALWAYS : key.alphaTest();
    }

    public void applyNonZeroAlphaTestForCurrentPass() {
        if (!isPipelineActive || !worldFrameActive || self().renderingGuiScreen()) {
            return;
        }

        ShaderAlphaTest alphaTest = ShaderAlphaTest.NON_ZERO_ALPHA;
        currentAlphaTestReference = alphaTest.reference();
        MinecraftReflectionCompat.glStateEnableAlpha();
        MinecraftReflectionCompat.glStateAlphaFunc(alphaTest.function(), alphaTest.reference());

        ShaderProgram program = self().activeProgram();
        if (program != null) {
            uniformRegistry.upload(program, "alphaTestRef");
            uniformRegistry.upload(program, "iris_currentAlphaTest");
        }
    }

    protected void applyBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        logEntityBlendProbe(pass, drawBuffers);
        if (self().applyOitBlendMode(pass, drawBuffers)) {
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }

        PipelineProgram pipelineProgram = programs.get(pass);
        ShaderBlendMode blendMode = pipelineProgram == null ? null : pipelineProgram.directives().blendModeOverride();
        Map<Attachment, ShaderBlendMode> attachmentModes = self().attachmentBlendModesFor(pass);
        if (pass == RenderPass.GBUFFERS_WATER || pass == RenderPass.DH_WATER) {
            self().applyWaterBlendMode(drawBuffers, blendMode == null ? WATER_BLEND_MODE : blendMode, attachmentModes);
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }
        if (pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT) {
            self().applyWaterBlendMode(drawBuffers, blendMode == null ? BLOCK_ENTITY_TRANSLUCENT_BLEND : blendMode, attachmentModes);
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }
        if (blendMode == null) {
            blendMode = PipelineRuntimeState.defaultBlendMode(pass);
        }
        if (blendMode == null && attachmentModes.isEmpty()) {
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }

        if (blendMode != null && !blendMode.enabled()) {
            MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }

        MinecraftReflectionCompat.glStateEnableBlend();
        if (blendMode != null) {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
        if (attachmentModes.isEmpty()) {
            disableColortex6MetadataBlending(pass, drawBuffers);
            return;
        }

        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            ShaderBlendMode attachmentMode = attachmentModes.get(attachment);
            if (attachmentMode != null) {
                self().applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            }
        }
        disableColortex6MetadataBlending(pass, drawBuffers);
    }

    /**
     * Complementary stores material and skylight metadata in colortex6. Its
     * own shader properties disable blending for that target on modern
     * pipeline backends; AUSM must apply the same rule on 1.12. Otherwise the
     * low lightmap alpha of dark entity and water pixels blends metadata with
     * the previous frame, which light-shaft compositing then exposes.
     */
    private void disableColortex6MetadataBlending(RenderPass pass, List<Attachment> drawBuffers) {
        if (pass == null || pass.stage() != ProgramStage.GBUFFERS) {
            return;
        }
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            if (drawBuffers.get(drawBufferIndex) == Attachment.AUX3) {
                setIndexedBlend(drawBufferIndex, false);
            }
        }
    }

    private void logEntityBlendProbe(RenderPass pass, List<Attachment> drawBuffers) {
        if ((pass != RenderPass.GBUFFERS_ENTITIES && pass != RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT)
                || currentEntityKey == null
                || entityBlendProbeLogs >= MAX_ENTITY_BLEND_PROBE_LOGS) {
            return;
        }
        boolean oit = self().isOitGbufferPass(pass);
        String key = pass + "|" + self().getPhase() + "|" + currentEntityKey + "|" + currentEntityId + "|" + oit + "|" + drawBuffers;
        if (key.equals(lastEntityBlendProbe)) {
            return;
        }
        lastEntityBlendProbe = key;
        entityBlendProbeLogs++;
        MainMod.LOGGER.info(
                "[AUSMEntityBlendProbe] probe={} frame={} pass={} phase={} entityKey={} entityId={} oit={} alphaRef={} drawBuffers={}",
                entityBlendProbeLogs,
                pipelineFrameId,
                pass,
                self().getPhase(),
                currentEntityKey,
                currentEntityId,
                oit,
                currentAlphaTestReference,
                drawBuffers
        );
    }

    protected void applyWaterBlendMode(List<Attachment> drawBuffers, ShaderBlendMode blendMode, Map<Attachment, ShaderBlendMode> attachmentModes) {
        if (!blendMode.enabled()) {
            MinecraftReflectionCompat.glStateDisableBlend();
            resetIndexedBlendState();
            return;
        }

        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                blendMode.srcRgb(),
                blendMode.dstRgb(),
                blendMode.srcAlpha(),
                blendMode.dstAlpha()
        );
        resetIndexedBlendState();

        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            ShaderBlendMode attachmentMode = attachmentModes.get(attachment);
            if (attachmentMode != null) {
                self().applyIndexedBlendMode(drawBufferIndex, attachmentMode);
            } else if (PipelineRuntimeState.defaultWaterBlendTarget(attachment)) {
                self().applyIndexedBlendMode(drawBufferIndex, blendMode);
            }
        }
    }

    protected static boolean defaultWaterBlendTarget(Attachment attachment) {
        return PipelineRenderPassRules.defaultWaterBlendTarget(attachment);
    }

    protected static ShaderBlendMode defaultBlendMode(RenderPass pass) {
        return PipelineRenderPassRules.defaultBlendMode(pass);
    }

    protected Map<Attachment, ShaderBlendMode> attachmentBlendModesFor(RenderPass pass) {
        PipelineProgram pipelineProgram = programs.get(pass);
        Map<Attachment, ShaderBlendMode> attachmentModes = pipelineProgram == null ? null : pipelineProgram.directives().attachmentBlendModes();
        return attachmentModes == null ? Map.of() : attachmentModes;
    }

    protected boolean applyOitBlendMode(RenderPass pass, List<Attachment> drawBuffers) {
        if (!self().isOitGbufferPass(pass) || drawBuffers.isEmpty()) {
            return false;
        }

        ShaderOitSettings oitSettings = shaderProperties.oitSettings();
        MinecraftReflectionCompat.glStateEnableBlend();
        MinecraftReflectionCompat.glStateTryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        for (int drawBufferIndex = 0; drawBufferIndex < drawBuffers.size(); drawBufferIndex++) {
            Attachment attachment = drawBuffers.get(drawBufferIndex);
            if (oitSettings.coefficientBuffer(attachment)) {
                self().applyIndexedBlendMode(drawBufferIndex, OIT_COEFFICIENT_BLEND);
            } else {
                setIndexedBlend(drawBufferIndex, false);
            }
        }
        return true;
    }

    protected void applyHandRenderState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_HAND && pass != RenderPass.GBUFFERS_HAND_WATER) {
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

    protected void applyOitDepthState(RenderPass pass) {
        if (!self().isOitGbufferPass(pass)) {
            return;
        }
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateDepthMask(false);
    }

    protected void applyGbufferDepthState(RenderPass pass) {
        if (!PipelineRuntimeState.isOpaqueTerrainPass(pass)
                && pass != RenderPass.GBUFFERS_WATER
                && pass != RenderPass.DH_WATER
                // TESRs and regular entities use these opaque passes. Vanilla
                // renderers may leave depth writes disabled after a translucent
                // draw; without restoring them, COMPOSITE1 sees the background
                // depth through the object and applies light shafts to it.
                && pass != RenderPass.GBUFFERS_BLOCK
                && pass != RenderPass.GBUFFERS_ENTITIES) {
            return;
        }
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        // Translucent terrain is sorted only at chunk/section granularity.
        // Without a depth write, a later water section can overwrite a nearer
        // modded-fluid surface even though it is geometrically behind it.
        // Preserve the normal translucent-terrain depth ownership; the saved
        // pre-water depth remains available through depthtex snapshots.
        MinecraftReflectionCompat.glStateDepthMask(true);
        GL11.glColorMask(true, true, true, true);
    }

    protected void applySkyDepthState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_SKYBASIC && pass != RenderPass.GBUFFERS_SKYTEXTURED) {
            return;
        }
        MinecraftReflectionCompat.glStateDisableDepth();
        MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColorMask(true, true, true, true);
    }

    protected void applyBeaconBeamDepthState(RenderPass pass) {
        if (shaderProperties.renderSettings().beaconBeamDepth()) {
            return;
        }
        if (pass != RenderPass.GBUFFERS_BEACONBEAM && self().getPhase() != WorldRenderingPhase.BEACON_BEAM) {
            return;
        }
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateDepthMask(false);
    }

    protected void applyBlockEntityTranslucentDepthState(RenderPass pass) {
        if (pass != RenderPass.GBUFFERS_BLOCK_TRANSLUCENT || self().isOitGbufferPass(pass)) {
            return;
        }
        MinecraftReflectionCompat.glStateEnableDepth();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        MinecraftReflectionCompat.glStateDepthMask(false);
        GL11.glColorMask(true, true, true, true);
    }

    protected static boolean isOpaqueTerrainPass(RenderPass pass) {
        return PipelineRenderPassRules.isOpaqueTerrainPass(pass);
    }

    protected boolean isOitGbufferPass(RenderPass pass) {
        if (pass == null || pass.stage() != ProgramStage.GBUFFERS || !shaderProperties.oitSettings().activeForGbuffers()) {
            return false;
        }

        WorldRenderingPhase phase = self().getPhase();
        if (phase != WorldRenderingPhase.NONE) {
            return PipelineRuntimeState.isOitPhase(phase);
        }
        return pass == RenderPass.GBUFFERS_ENTITIES_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_BLOCK_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_PARTICLES_TRANSLUCENT
                || pass == RenderPass.GBUFFERS_WEATHER
                || pass == RenderPass.GBUFFERS_CLOUDS
                || pass == RenderPass.GBUFFERS_LIGHTNING
                || pass == RenderPass.GBUFFERS_BEACONBEAM;
    }

    protected static boolean isOitPhase(WorldRenderingPhase phase) {
        return PipelineRenderPassRules.isOitPhase(phase);
    }

    protected void applyIndexedBlendMode(int drawBufferIndex, ShaderBlendMode blendMode) {
        if (drawBufferIndex < 0 || drawBufferIndex >= maxDrawBuffers()) {
            return;
        }
        if (!blendMode.enabled()) {
            setIndexedBlend(drawBufferIndex, false);
            return;
        }

        setIndexedBlend(drawBufferIndex, true);
        if (GLContext.getCapabilities().OpenGL40 || GLContext.getCapabilities().GL_ARB_draw_buffers_blend) {
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(
                    drawBufferIndex,
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        } else {
            MinecraftReflectionCompat.glStateTryBlendFuncSeparate(
                    blendMode.srcRgb(),
                    blendMode.dstRgb(),
                    blendMode.srcAlpha(),
                    blendMode.dstAlpha()
            );
        }
    }

    protected void configureGbufferDrawBuffers(PipelineProgram pipelineProgram, List<Attachment> drawBuffers) {
        if (!pingPongManager.isInitialized() || pipelineProgram.stage() != ProgramStage.GBUFFERS) {
            return;
        }

        if (!drawBuffers.isEmpty()) {
            pingPongManager.bindForGbuffers(drawBuffers.toArray(new Attachment[0]));
        }
    }
}
