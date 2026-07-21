package com.l.ausm.impl.pipeline.dh;

/** Built-in shader sources used only by the retained Distant Horizons fallback path. */
public final class DistantHorizonsInternalShaders {
    public static final String FALLBACK_VERTEX = """
            #version 150 core

            in uvec4 vPosition;
            in vec4 color;
            in uvec4 dhMaterialData;

            uniform mat4 uCombinedMatrix;
            uniform vec3 uModelOffset;
            uniform float uWorldYOffset;
            uniform float uMircoOffset;
            uniform float uEarthRadius;

            out vec4 vertexColor;
            out vec3 vertexWorldPos;

            void main() {
                uint meta = vPosition.a;
                uint mirco = (meta & 0xFF00u) >> 8u;
                float mx = (mirco & 1u) != 0u ? uMircoOffset : 0.0;
                mx = (mirco & 2u) != 0u ? -mx : mx;
                float mz = (mirco & 16u) != 0u ? uMircoOffset : 0.0;
                mz = (mirco & 32u) != 0u ? -mz : mz;
                uint lights = meta & 0xFFu;
                float skyLight = (float(lights / 16u) + 0.5) / 16.0;
                float blockLight = (float(lights & 15u) + 0.5) / 16.0;
                float light = clamp(max(blockLight, skyLight * 0.75) * 0.9 + 0.1, 0.0, 1.0);
                vec3 worldPos = vec3(vPosition.xyz) + uModelOffset;
                worldPos.x += mx;
                worldPos.z += mz;
                float vertexYPos = float(vPosition.y) + uWorldYOffset;
                if (uEarthRadius < -1.0 || uEarthRadius > 1.0) {
                    float localRadius = uEarthRadius + vertexYPos;
                    float phi = length(worldPos.xz) / localRadius;
                    worldPos.y += (cos(phi) - 1.0) * localRadius;
                    worldPos.xz = worldPos.xz * sin(phi) / phi;
                }
                vertexWorldPos = worldPos;
                vertexColor = vec4(color.rgb * light, color.a);
                gl_Position = uCombinedMatrix * vec4(worldPos, 1.0);
            }
            """;

    public static final String FALLBACK_FRAGMENT = """
            #version 150 core

            in vec4 vertexColor;
            in vec3 vertexWorldPos;
            out vec4 fragColor;

            void main() {
                fragColor = vertexColor;
            }
            """;

    public static final String COMPOSITE_VERTEX = """
            #version 120
            varying vec2 textureCoords;
            void main() {
                textureCoords = gl_MultiTexCoord0.st;
                gl_Position = vec4(textureCoords * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    public static final String COMPOSITE_FRAGMENT = """
            #version 120
            uniform sampler2D dhColor;
            uniform sampler2D dhDepth;
            varying vec2 textureCoords;
            void main() {
                vec4 color = texture2D(dhColor, textureCoords);
                if (color.a <= 0.001 || max(max(color.r, color.g), color.b) <= 0.001) {
                    discard;
                }
                float depth = texture2D(dhDepth, textureCoords).r;
                gl_FragDepth = depth < 0.999999 ? depth : 0.999998;
                gl_FragColor = vec4(color.rgb, 1.0);
            }
            """;

    private DistantHorizonsInternalShaders() {
    }
}
