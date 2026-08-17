package com.l.ausm.impl.pipeline.pack;

import com.l.ausm.api.pipeline.shader.ProgramStage;
import java.util.regex.Pattern;

/**
 * Backports Iris' composite/fullscreen built-in rewrites with conservative text transforms.
 */
public final class FullscreenBuiltinTransformStage implements ShaderTransformStage {
    private static final Pattern GL_TEXTURE_MATRIX = Pattern.compile("\\bgl_TextureMatrix\\s*\\[\\s*\\d+\\s*\\]");
    private static final Pattern GL_MODEL_VIEW_PROJECTION_MATRIX = Pattern.compile("\\bgl_ModelViewProjectionMatrix\\b");
    private static final Pattern GL_MODEL_VIEW_MATRIX = Pattern.compile("\\bgl_ModelViewMatrix\\b");
    private static final Pattern GL_PROJECTION_MATRIX = Pattern.compile("\\bgl_ProjectionMatrix\\b");
    private static final Pattern GL_MODEL_VIEW_MATRIX_INVERSE = Pattern.compile("\\bgl_ModelViewMatrixInverse\\b");
    private static final Pattern GL_PROJECTION_MATRIX_INVERSE = Pattern.compile("\\bgl_ProjectionMatrixInverse\\b");
    private static final Pattern GL_NORMAL_MATRIX = Pattern.compile("\\bgl_NormalMatrix\\b");
    private static final Pattern GL_COLOR = Pattern.compile("\\bgl_Color\\b");
    private static final Pattern GL_NORMAL = Pattern.compile("\\bgl_Normal\\b");

    @Override
    public String apply(String source, ShaderTransformParameters parameters) {
        if (parameters.pass() == null || !parameters.pass().stage().isFullscreenStage()) {
            return source;
        }
        if (parameters.pass().stage() == ProgramStage.PREPARE || parameters.pass().stage() == ProgramStage.SHADOW) {
            return source;
        }

        String transformed = GL_TEXTURE_MATRIX.matcher(source).replaceAll("mat4(1.0)");
        transformed = GL_MODEL_VIEW_PROJECTION_MATRIX.matcher(transformed).replaceAll("(gl_ProjectionMatrix * gl_ModelViewMatrix)");
        transformed = GL_MODEL_VIEW_MATRIX.matcher(transformed).replaceAll("mat4(1.0)");
        transformed = GL_PROJECTION_MATRIX.matcher(transformed).replaceAll(
                "mat4(vec4(2.0, 0.0, 0.0, 0.0), vec4(0.0, 2.0, 0.0, 0.0), vec4(0.0), vec4(-1.0, -1.0, 0.0, 1.0))"
        );
        transformed = GL_MODEL_VIEW_MATRIX_INVERSE.matcher(transformed).replaceAll("mat4(1.0)");
        transformed = GL_PROJECTION_MATRIX_INVERSE.matcher(transformed).replaceAll(
                "inverse(mat4(vec4(2.0, 0.0, 0.0, 0.0), vec4(0.0, 2.0, 0.0, 0.0), vec4(0.0), vec4(-1.0, -1.0, 0.0, 1.0)))"
        );
        transformed = GL_NORMAL_MATRIX.matcher(transformed).replaceAll("mat3(1.0)");

        if (parameters.vertexShader()) {
            transformed = GL_NORMAL.matcher(transformed).replaceAll("vec3(0.0, 0.0, 1.0)");
        }
        return GL_COLOR.matcher(transformed).replaceAll("vec4(1.0, 1.0, 1.0, 1.0)");
    }
}
