package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backports Iris' vanilla gbuffers built-in rewrites that are required before
 * shader pack code receives Minecraft vertex attributes.
 */
public final class GbuffersBuiltinTransformStage implements ShaderTransformStage {
    private static final Pattern GL_TEXTURE_MATRIX_0 = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*0\\s*]");
    private static final Pattern GL_LIGHTMAP_TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*1\\s*]");
    private static final Pattern GL_MODEL_VIEW_MATRIX_INVERSE = Pattern.compile("\\bgl_ModelViewMatrixInverse\\b");
    private static final Pattern GL_PROJECTION_MATRIX_INVERSE = Pattern.compile("\\bgl_ProjectionMatrixInverse\\b");
    private static final Pattern PROJECTION_MATRIX_DECLARATION =
            Pattern.compile("(?m)^\\s*uniform\\s+mat4\\s+projectionMatrix\\s*;");
    private static final Pattern MODEL_VIEW_MATRIX_DECLARATION =
            Pattern.compile("(?m)^\\s*uniform\\s+mat4\\s+modelViewMatrix\\s*;");
    private static final Pattern VA_POSITION_DECLARATION =
            Pattern.compile("(?m)^\\s*(?:in|attribute)\\s+vec3\\s+vaPosition\\s*;");
    private static final Pattern VA_NORMAL_DECLARATION =
            Pattern.compile("(?m)^\\s*(?:in|attribute)\\s+vec3\\s+vaNormal\\s*;");
    private static final Pattern VERSION_OR_EXTENSION = Pattern.compile("(?m)^(\\s*(?:#version\\b.*|#extension\\b.*)\\R)");
    private static final Pattern ADVANCED_TANGENT_GUARD = Pattern.compile(
            "(#if\\s+(?=[^\\r\\n]*\\bdefined\\s+GENERATED_NORMALS\\b)(?=[^\\r\\n]*\\bdefined\\s+CUSTOM_PBR\\b)(?![^\\r\\n]*\\bdefined\\s+POM\\b)[^\\r\\n]*)"
    );

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null
                || parameters.pass().stage() != ProgramStage.GBUFFERS
                || !parameters.vertexShader()) {
            return source;
        }

        String transformed = source;
        transformed = replaceAndInject(transformed, GL_TEXTURE_MATRIX_0, "iris_TextureMat", "uniform mat4 iris_TextureMat;\n");
        transformed = replaceAndInject(transformed, GL_LIGHTMAP_TEXTURE_MATRIX, "iris_LightmapTextureMatrix", "uniform mat4 iris_LightmapTextureMatrix;\n");
        transformed = replaceAndInject(transformed, GL_MODEL_VIEW_MATRIX_INVERSE, "iris_ModelViewMatInverse", "uniform mat4 iris_ModelViewMatInverse;\n");
        transformed = replaceAndInject(transformed, GL_PROJECTION_MATRIX_INVERSE, "iris_ProjMatInverse", "uniform mat4 iris_ProjMatInverse;\n");
        transformed = defineIfUndeclared(transformed, "projectionMatrix", PROJECTION_MATRIX_DECLARATION, "#define projectionMatrix gl_ProjectionMatrix\n");
        transformed = defineIfUndeclared(transformed, "modelViewMatrix", MODEL_VIEW_MATRIX_DECLARATION, "#define modelViewMatrix gl_ModelViewMatrix\n");
        transformed = defineIfUndeclared(transformed, "vaPosition", VA_POSITION_DECLARATION, "#define vaPosition gl_Vertex.xyz\n");
        transformed = defineIfUndeclared(transformed, "vaNormal", VA_NORMAL_DECLARATION, "#define vaNormal gl_Normal\n");
        transformed = ensurePomTangentGuard(transformed);
        return transformed;
    }

    private static String ensurePomTangentGuard(String source) {
        if (!source.contains("#ifdef POM") || !source.contains("at_tangent")) {
            return source;
        }
        return ADVANCED_TANGENT_GUARD.matcher(source)
                .replaceAll("$1 || defined POM");
    }

    private static String replaceAndInject(String source, Pattern pattern, String replacement, String uniformDeclaration) {
        String transformed = pattern.matcher(source).replaceAll(replacement);
        if (transformed.equals(source) || transformed.contains(uniformDeclaration.trim())) {
            return transformed;
        }
        return injectUniform(transformed, uniformDeclaration);
    }

    private static String injectUniform(String source, String uniformDeclaration) {
        return injectAfterVersion(source, uniformDeclaration);
    }

    private static String defineIfUndeclared(String source, String symbol, Pattern declaration, String define) {
        if (!source.matches("(?s).*\\b" + Pattern.quote(symbol) + "\\b.*")
                || declaration.matcher(source).find()
                || source.contains(define.trim())) {
            return source;
        }
        return injectAfterVersion(source, define);
    }

    private static String injectAfterVersion(String source, String declaration) {
        Matcher matcher = VERSION_OR_EXTENSION.matcher(source);
        int insertAt = 0;
        while (matcher.find()) {
            insertAt = matcher.end();
        }
        return source.substring(0, insertAt) + declaration + source.substring(insertAt);
    }
}
