package com.luna.ausm.impl.client;

import java.io.IOException;

/** Shared D3Q19-style colored-light diffusion with family-specific shader syntax. */
final class IsotropicLightDiffusionPatch {
    private static final String MARKER = "AUSM_ISOTROPIC_ACT_DIFFUSION";
    private static final int[][] FACES = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[][] EDGES = {
            {1, 1, 0}, {1, -1, 0}, {-1, 1, 0}, {-1, -1, 0},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
            {0, 1, 1}, {0, 1, -1}, {0, -1, 1}, {0, -1, -1}
    };

    private IsotropicLightDiffusionPatch() {
    }

    static String patchEuphoria(String source) throws IOException {
        source = normalize(source);
        if (source.contains(MARKER)) return source;
        String signature = "vec4 GetLightCalculated(sampler3D lightSampler, ivec3 pos, ivec3 voxelVolumeSize, uint voxel) {";
        int function = source.indexOf(signature);
        if (function < 0) return source;
        int body = function + signature.length();
        int tint = source.indexOf("\n\tif (voxel >= 200u)", body);
        if (tint < 0) throw new IOException("Unsupported Euphoria ACT diffusion layout");
        String sample = "GetLightSample(lightSampler, clamp(pos + %s, ivec3(0), voxelVolumeSize - 1))";
        String diffusion = stencil("vec4", sample, "25.68",
                "24 / 25.68 retains Euphoria's original 6 / 6.42 attenuation");
        return source.substring(0, body) + diffusion + source.substring(tint);
    }

    static String patchBsl(String source) throws IOException {
        source = normalize(source);
        if (source.contains(MARKER)) return source;
        String signature = "vec3 GetLightFromNeighbors(sampler3D lighttex, ivec3 pos) {";
        int function = source.indexOf(signature);
        if (function < 0) return source;
        int end = functionEnd(source, function + signature.length());
        String sample = "texelFetch(lighttex, clamp(pos + %s, ivec3(0), textureSize(lighttex, 0) - 1), 0).rgb";
        String replacement = signature + stencil("vec3", sample, "(24.0 / 0.995)",
                "24 / (24 / 0.995) retains BSL's original 0.995 attenuation")
                + "\n\treturn light;\n}";
        return source.substring(0, function) + replacement + source.substring(end);
    }

    private static String stencil(String vectorType, String sampleFormat, String denominator, String attenuation) {
        return "\n\n\t// " + MARKER + ": shared face-and-edge stencil.\n"
                + "\t// Faces carry twice the edge weight; " + attenuation + ".\n"
                + sum(vectorType, "faceLight", sampleFormat, FACES)
                + sum(vectorType, "edgeLight", sampleFormat, EDGES)
                + "\t" + vectorType + " light = (faceLight * 2.0 + edgeLight) / " + denominator + ";\n";
    }

    private static String sum(String vectorType, String name, String sampleFormat, int[][] offsets) {
        StringBuilder result = new StringBuilder("\t").append(vectorType).append(' ').append(name).append(" =\n");
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            String vector = String.format("ivec3(%2d, %2d, %2d)", offset[0], offset[1], offset[2]);
            result.append(i == 0 ? "\t\t  " : "\t\t+ ")
                    .append(String.format(sampleFormat, vector))
                    .append(i + 1 == offsets.length ? ";\n" : "\n");
        }
        return result.toString();
    }

    private static int functionEnd(String source, int body) throws IOException {
        int depth = 1;
        for (int at = body; at < source.length(); at++) {
            char current = source.charAt(at);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) return at + 1;
        }
        throw new IOException("Unterminated colored-light diffusion function");
    }

    private static String normalize(String source) {
        return source.replace("\r\n", "\n");
    }
}
