#ifndef AUSM_ENTREE_DISTANT_LOD
#define AUSM_ENTREE_DISTANT_LOD

#define AUSM_LOD_FALLBACK 1 // [0 1]

// Euphoria expands this include before the regular uniform header in several
// 1.12.2 terrain and water stages, so it must carry every symbol it uses.
uniform int ausmLodFallbackEnabled;
uniform float ausmLod1RadiusBlocks;
uniform float ausmLod2RadiusBlocks;
uniform float ausmLod3RadiusBlocks;
uniform float ausmLod4RadiusBlocks;

float ausmLodTransition(float playerDistance) {
    return smoothstep(ausmLod1RadiusBlocks, ausmLod2RadiusBlocks, playerDistance);
}

float ausmEntreeDetailWeight(vec3 playerPosition) {
    return AUSM_LOD_FALLBACK == 1 && ausmLodFallbackEnabled > 0
            ? 1.0 - ausmLodTransition(length(playerPosition))
            : 1.0;
}

float ausmEntreeWaterDetailWeight(vec3 playerPosition) {
    return ausmEntreeDetailWeight(playerPosition);
}

// Foliage is the first feature that becomes unstable at distance. Keep it
// fully enabled through LOD 1, then disable it exactly at the LOD 2 boundary.
float ausmEntreeFoliageWaveWeight(vec3 playerPosition) {
    return AUSM_LOD_FALLBACK == 1 && ausmLodFallbackEnabled > 0 && length(playerPosition) < ausmLod2RadiusBlocks
            ? 1.0
            : 0.0;
}

// Detail is gradually made coarser instead of being removed at the first
// distant-LOD boundary. The scale is 1x, 2x, 4x, and 8x before features fade
// out beyond the final tier.
float ausmEntreeLodResolutionScale(float playerDistance) {
    if (AUSM_LOD_FALLBACK == 0 || ausmLodFallbackEnabled <= 0) {
        return 1.0;
    }
    float lodLevel = 0.0;
    if (playerDistance >= ausmLod1RadiusBlocks) lodLevel = 1.0;
    if (playerDistance >= ausmLod2RadiusBlocks) lodLevel = 2.0;
    if (playerDistance >= ausmLod3RadiusBlocks) lodLevel = 3.0;
    return exp2(lodLevel);
}

float ausmEntreeLodFeatureWeight(float playerDistance) {
    return AUSM_LOD_FALLBACK == 1 && ausmLodFallbackEnabled > 0
            ? 1.0 - smoothstep(ausmLod4RadiusBlocks, ausmLod4RadiusBlocks + 48.0, playerDistance)
            : 1.0;
}

int ausmEntreeLodSampleCount(int fullResolutionSamples, float resolutionScale) {
    return max(1, int(floor(float(fullResolutionSamples) / resolutionScale + 0.5)));
}

float ausmEntreeReflectionMipBias(float reflectionDistance) {
    return log2(ausmEntreeLodResolutionScale(reflectionDistance));
}

#endif
