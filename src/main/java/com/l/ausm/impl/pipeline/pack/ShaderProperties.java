package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.fbo.Attachment;
import com.l.ausm.api.pipeline.pack.ShaderAlphaTest;
import com.l.ausm.api.pipeline.pack.ShaderBlendMode;
import com.l.ausm.api.pipeline.pack.ShaderCustomTextureBinding;
import com.l.ausm.api.pipeline.pack.ShaderOitSettings;
import com.l.ausm.api.pipeline.pack.ShaderOptions;
import com.l.ausm.api.pipeline.pack.ShaderProgramDirectives;
import com.l.ausm.api.pipeline.pack.ShaderRenderSettings;
import com.l.ausm.api.pipeline.pack.ShaderRenderTargetSettings;
import com.l.ausm.api.pipeline.pack.ShaderScreen;
import com.l.ausm.api.pipeline.pack.ShaderTextureDirectives;
import com.l.ausm.api.pipeline.pack.ShaderViewportScale;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderIndirectPointer;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

public final class ShaderProperties extends ShaderPropertiesTextureDirectives {
    public ShaderProperties(
            Map<RenderPass, List<Attachment>> drawBuffers,
            Map<ProgramKey, String> programEnabledExpressions,
            ShaderOptions options,
            Map<String, ShaderScreen> screens,
            Map<String, String> profiles,
            ShaderRenderTargetSettings renderTargets,
            List<ShaderCustomTextureBinding> globalTextures,
            Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures,
            Map<RenderPass, Map<Attachment, Boolean>> explicitFlips,
            Map<RenderPass, ShaderViewportScale> viewportScales,
            Map<String, String> translations,
            ShaderBlockIdMap.BlockIdRules blockIds,
            Map<ResourceLocation, Integer> entityIds,
            ShaderItemIdMap.ItemIdRules itemIds,
            ShaderRenderSettings renderSettings,
            Map<RenderPass, ShaderAlphaTest> alphaTests,
            Map<RenderPass, ShaderBlendMode> blendModes,
            Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentBlendModes,
            Map<ProgramId, ShaderProgramDirectives> programDirectives,
            ShaderTextureDirectives textureDirectives,
            CustomUniformSet customUniforms,
            ShaderPackDirectives packDirectives,
            ShaderOitSettings oitSettings,
            Map<ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives,
            Map<ProgramArrayKey, String> programArrayEnabledExpressions,
            Map<String, ShaderIndirectPointer> indirectPointers) {
        this.drawBuffers = drawBuffers;
        this.programEnabledExpressions = programEnabledExpressions;
        this.options = options;
        this.screens = screens;
        this.profiles = profiles;
        this.renderTargets = renderTargets;
        this.globalTextures = globalTextures;
        this.passTextures = passTextures;
        this.explicitFlips = explicitFlips;
        this.viewportScales = viewportScales;
        this.translations = translations;
        this.blockIds = blockIds;
        this.entityIds = entityIds;
        this.itemIds = itemIds;
        this.renderSettings = renderSettings;
        this.alphaTests = alphaTests;
        this.blendModes = blendModes;
        this.attachmentBlendModes = attachmentBlendModes;
        this.programDirectives = programDirectives;
        this.textureDirectives = textureDirectives;
        this.customUniforms = customUniforms;
        this.packDirectives = packDirectives;
        this.oitSettings = oitSettings;
        this.programArrayDirectives = programArrayDirectives;
        this.programArrayEnabledExpressions = programArrayEnabledExpressions;
        this.indirectPointers = indirectPointers;
    }
}
