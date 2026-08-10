package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import com.l.ausm.api.pipeline.shader.RenderPass;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes fixed-function vertex position builtins accept an optional AUSM chunk
 * offset. When present, the Nothirium bridge uploads this offset per draw so
 * shaderpack world-position and projection math use the same chunk-space
 * translation source.
 */
public final class NothiriumChunkOffsetTransformStage implements ShaderTransformStage {
    private static final Pattern GL_VERTEX = Pattern.compile("\\bgl_Vertex\\b");
    private static final Pattern FTRANSFORM = Pattern.compile("\\bftransform\\s*\\(\\s*\\)");
    private static final Pattern VA_POSITION_DEFINE =
            Pattern.compile("(?m)^\\s*#\\s*define\\s+vaPosition\\b.*$");
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");
    private static final Set<RenderPass> CHUNK_OFFSET_PASSES = EnumSet.of(
            RenderPass.GBUFFERS_TERRAIN,
            RenderPass.GBUFFERS_TERRAIN_SOLID,
            RenderPass.GBUFFERS_TERRAIN_CUTOUT,
            RenderPass.GBUFFERS_TERRAIN_CUTOUT_MIP,
            RenderPass.GBUFFERS_DAMAGEDBLOCK,
            RenderPass.GBUFFERS_BLOCK,
            RenderPass.GBUFFERS_BLOCK_TRANSLUCENT,
            RenderPass.GBUFFERS_WATER,
            RenderPass.SHADOW,
            RenderPass.SHADOW_SOLID,
            RenderPass.SHADOW_CUTOUT,
            RenderPass.SHADOW_WATER
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null
                || (parameters.pass().stage() != ProgramStage.GBUFFERS
                && parameters.pass().stage() != ProgramStage.SHADOW)
                || !isTerrainOffsetPass(parameters.pass())
                || !parameters.vertexShader()
                || source.contains("ausm_ChunkVertex")) {
            return source;
        }
        boolean hasLegacyVertexReference = source.contains("gl_Vertex") || FTRANSFORM.matcher(source).find();
        if (!hasLegacyVertexReference && !VA_POSITION_DEFINE.matcher(source).find()) {
            return source;
        }

        String transformed = FTRANSFORM.matcher(source)
                .replaceAll(Matcher.quoteReplacement("(gl_ModelViewProjectionMatrix * ausm_ChunkVertex())"));
        if (source.contains("gl_Vertex")) {
            transformed = GL_VERTEX.matcher(transformed).replaceAll("ausm_ChunkVertex()");
        } else if (!VA_POSITION_DEFINE.matcher(transformed).find()) {
            return source;
        }
        transformed = VA_POSITION_DEFINE.matcher(transformed)
                .replaceAll(Matcher.quoteReplacement("#define vaPosition ausm_ChunkVertex().xyz"));
        return injectAfterVersion(transformed, declarationFor(source));
    }

    private static boolean isTerrainOffsetPass(RenderPass pass) {
        return CHUNK_OFFSET_PASSES.contains(pass);
    }

    private static String injectAfterVersion(String source, String declaration) {
        Matcher matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + declaration + source.substring(insertAt);
    }

    private static String declarationFor(String source) {
        return "uniform vec3 ausm_ChunkOffset;\n"
                + "vec4 ausm_ChunkVertex() {\n"
                + "    return gl_Vertex + vec4(ausm_ChunkOffset, 0.0);\n"
                + "}\n";
    }

    private static int glslVersion(String source) {
        Matcher matcher = Pattern.compile("(?m)^\\s*#\\s*version\\s+(\\d+)").matcher(source);
        if (!matcher.find()) {
            return 120;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 120;
        }
    }
}
