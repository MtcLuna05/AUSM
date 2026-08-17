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
import com.l.ausm.api.pipeline.shader.ProgramArrayId;
import com.l.ausm.api.pipeline.shader.ProgramId;
import com.l.ausm.api.pipeline.shader.RenderPass;
import com.l.ausm.api.pipeline.shader.ShaderIndirectPointer;
import com.l.ausm.impl.pipeline.shader.CustomUniformSet;
import java.util.List;
import java.util.Map;
import net.minecraft.util.ResourceLocation;

abstract class ShaderPropertiesBase {
    protected Map<RenderPass, List<Attachment>> drawBuffers;

    protected Map<ShaderProperties.ProgramKey, String> programEnabledExpressions;

    protected ShaderOptions options;

    protected Map<String, ShaderScreen> screens;

    protected Map<String, String> profiles;

    protected ShaderRenderTargetSettings renderTargets;

    protected List<ShaderCustomTextureBinding> globalTextures;

    protected Map<RenderPass, List<ShaderCustomTextureBinding>> passTextures;

    protected Map<RenderPass, Map<Attachment, Boolean>> explicitFlips;

    protected Map<RenderPass, ShaderViewportScale> viewportScales;

    protected Map<String, String> translations;

    protected ShaderBlockIdMap.BlockIdRules blockIds;

    protected Map<ResourceLocation, Integer> entityIds;

    protected ShaderItemIdMap.ItemIdRules itemIds;

    protected ShaderRenderSettings renderSettings;

    protected Map<RenderPass, ShaderAlphaTest> alphaTests;

    protected Map<RenderPass, ShaderBlendMode> blendModes;

    protected Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentBlendModes;

    protected Map<ProgramId, ShaderProgramDirectives> programDirectives;

    protected ShaderTextureDirectives textureDirectives;

    protected CustomUniformSet customUniforms;

    protected ShaderPackDirectives packDirectives;

    protected ShaderOitSettings oitSettings;

    protected Map<ShaderProperties.ProgramArrayKey, ShaderProgramDirectives> programArrayDirectives;

    protected Map<ShaderProperties.ProgramArrayKey, String> programArrayEnabledExpressions;

    protected Map<String, ShaderIndirectPointer> indirectPointers;

    protected record BlendModes(
            Map<RenderPass, ShaderBlendMode> passModes,
            Map<RenderPass, Map<Attachment, ShaderBlendMode>> attachmentModes,
            Map<ProgramId, ShaderBlendMode> programModes,
            Map<ProgramId, Map<Attachment, ShaderBlendMode>> programAttachmentModes,
            Map<ShaderProperties.ProgramArrayKey, ShaderBlendMode> arrayModes,
            Map<ShaderProperties.ProgramArrayKey, Map<Attachment, ShaderBlendMode>> arrayAttachmentModes
    ) {
    }

    protected record AlphaTests(
            Map<RenderPass, ShaderAlphaTest> passModes,
            Map<ProgramId, ShaderAlphaTest> programModes,
            Map<ShaderProperties.ProgramArrayKey, ShaderAlphaTest> arrayModes
    ) {
    }

    public record ProgramKey(Integer dimensionId, ProgramId programId) {
        protected static ShaderProperties.ProgramKey parse(String rawName, ProgramId fallbackProgramId) {
            int dimensionId = Integer.MIN_VALUE;
            String programName = rawName;
            if (rawName.startsWith("world")) {
                int slash = rawName.indexOf('/');
                if (slash > "world".length()) {
                    try {
                        dimensionId = Integer.parseInt(rawName.substring("world".length(), slash));
                        programName = rawName.substring(slash + 1);
                    } catch (NumberFormatException ignored) {
                        dimensionId = Integer.MIN_VALUE;
                        programName = rawName;
                    }
                }
            }

            ProgramId programId = ProgramId.fromSourceName(programName);
            if (programId == null) {
                programId = fallbackProgramId;
            }
            Integer dimension = dimensionId == Integer.MIN_VALUE ? null : dimensionId;
            return new ShaderProperties.ProgramKey(dimension, programId);
        }
    }

    public record ProgramArrayKey(Integer dimensionId, ProgramArrayId arrayId, int index) {
        public static ShaderProperties.ProgramArrayKey parse(String rawName) {
            int dimensionId = Integer.MIN_VALUE;
            String programName = rawName;
            if (rawName.startsWith("world")) {
                int slash = rawName.indexOf('/');
                if (slash > "world".length()) {
                    try {
                        dimensionId = Integer.parseInt(rawName.substring("world".length(), slash));
                        programName = rawName.substring(slash + 1);
                    } catch (NumberFormatException ignored) {
                        dimensionId = Integer.MIN_VALUE;
                        programName = rawName;
                    }
                }
            }

            for (ProgramArrayId arrayId : ProgramArrayId.values()) {
                String prefix = arrayId.sourcePrefix();
                if (programName.equals(prefix)) {
                    return new ShaderProperties.ProgramArrayKey(dimensionId == Integer.MIN_VALUE ? null : dimensionId, arrayId, 0);
                }
                if (programName.startsWith(prefix)) {
                    String suffix = programName.substring(prefix.length());
                    if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                        return new ShaderProperties.ProgramArrayKey(
                                dimensionId == Integer.MIN_VALUE ? null : dimensionId,
                                arrayId,
                                Integer.parseInt(suffix)
                        );
                    }
                }
            }
            return null;
        }
    }

    protected record TextureFiltering(boolean blur, boolean clamp) {
    }

    protected ShaderProperties self() {
        return (ShaderProperties) this;
    }
}
