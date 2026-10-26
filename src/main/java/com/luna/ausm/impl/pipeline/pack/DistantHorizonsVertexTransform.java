package com.luna.ausm.impl.pipeline.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes the native DH 3.3 vertex format instead of using fixed-function attributes. */
public final class DistantHorizonsVertexTransform {
    private static final String INPUTS = """
            in uvec4 vPosition;
            in vec4 ausmDhVertexColor;
            in uvec4 dhMaterialData;
            uniform vec3 ausmDhModelOffset;
            uniform mat4 ausmDhModelView;
            uniform mat4 ausmDhModelViewInverse;
            uniform mat4 ausmDhProjection;
            uniform mat4 ausmDhProjectionInverse;
            int dhMaterialId;
            vec4 ausmDhPosition() {
                uint micro = (vPosition.w >> 8u) & 255u;
                float x = (micro & 1u) != 0u ? 0.01 : 0.0;
                float z = (micro & 16u) != 0u ? 0.01 : 0.0;
                if ((micro & 2u) != 0u) x = -x;
                if ((micro & 32u) != 0u) z = -z;
                return vec4(vec3(vPosition.xyz) + vec3(x, 0.0, z) + ausmDhModelOffset, 1.0);
            }
            vec3 ausmDhNormal() {
                uint face = dhMaterialData.y;
                if (face == 0u) return vec3(0.0, -1.0, 0.0);
                if (face == 1u) return vec3(0.0, 1.0, 0.0);
                if (face == 2u) return vec3(0.0, 0.0, -1.0);
                if (face == 3u) return vec3(0.0, 0.0, 1.0);
                if (face == 4u) return vec3(-1.0, 0.0, 0.0);
                return vec3(1.0, 0.0, 0.0);
            }
            vec4 ausmDhLightmap() {
                // DH packs sky light in the low nibble and block light in the high nibble.
                // Shaderpack light coordinates are block light, then sky light.
                uint lights = vPosition.w & 255u;
                return vec4((vec2(float(lights >> 4u), float(lights & 15u)) + 0.5) / 16.0, 0.0, 1.0);
            }
            """;

    private DistantHorizonsVertexTransform() { }

    public static String transform(String source) {
        if (source.contains("vec4 ausmDhPosition()")) return source;
        String transformed = source.replaceAll("\\b(?:attribute|in)\\s+int\\s+dhMaterialId\\s*;", "");
        Map<String, String> expressions = new LinkedHashMap<>();
        expressions.put("gl_ModelViewProjectionMatrix", "(ausmDhProjection * ausmDhModelView)");
        expressions.put("gl_ModelViewMatrixInverse", "ausmDhModelViewInverse");
        expressions.put("gl_ProjectionMatrixInverse", "ausmDhProjectionInverse");
        expressions.put("gl_ModelViewMatrix", "ausmDhModelView");
        expressions.put("gl_ProjectionMatrix", "ausmDhProjection");
        expressions.put("gl_NormalMatrix", "mat3(ausmDhModelView)");
        expressions.put("gl_Vertex", "ausmDhPosition()");
        expressions.put("gl_Normal", "ausmDhNormal()");
        expressions.put("gl_Color", "ausmDhVertexColor");
        expressions.put("gl_MultiTexCoord0", "vec4(0.0, 0.0, 0.0, 1.0)");
        expressions.put("gl_MultiTexCoord1", "ausmDhLightmap()");
        expressions.put("gl_MultiTexCoord2", "ausmDhLightmap()");
        for (Map.Entry<String, String> expression : expressions.entrySet()) {
            transformed = transformed.replaceAll("\\b" + expression.getKey() + "\\b", Matcher.quoteReplacement(expression.getValue()));
        }
        transformed = transformed.replaceAll("\\bgl_TextureMatrix\\s*\\[\\s*[01]\\s*]", "mat4(1.0)");
        transformed = transformed.replaceAll("\\bftransform\\s*\\(\\s*\\)", "(ausmDhProjection * ausmDhModelView * ausmDhPosition())");
        transformed = transformed.replaceAll("(\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{)",
                "$1\n    dhMaterialId = int(dhMaterialData.x);");
        Matcher header = Pattern.compile("(?m)^\\s*#(?:version|extension)\\b[^\\r\\n]*\\R").matcher(transformed);
        int insert = 0;
        while (header.find()) insert = header.end();
        return transformed.substring(0, insert) + INPUTS + transformed.substring(insert);
    }
}
