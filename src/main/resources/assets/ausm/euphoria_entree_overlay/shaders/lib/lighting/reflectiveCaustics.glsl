vec3 GetReflectiveCaustics(vec3 playerPos, vec3 worldGeoNormal, float skyLight) {
    if (isEyeInWater != 0 || sunVisibility < 0.01 || rainFactor > 0.98) {
        return vec3(0.0);
    }

    vec3 towardSun = normalize(ViewToPlayer(lightVec));
    vec3 reflectedTravel = normalize(reflect(-towardSun, vec3(0.0, 1.0, 0.0)));
    if (reflectedTravel.y <= 0.04) {
        return vec3(0.0);
    }

    // Reflected sunlight travels up from the water. The receiving side of a
    // wall or ceiling therefore faces back down this ray toward the water.
    vec3 towardWater = -reflectedTravel;
    float receiverFacing = max0(dot(worldGeoNormal, towardWater));
    if (receiverFacing < 0.015) {
        return vec3(0.0);
    }

    vec3 waterShadowColor = vec3(0.0);
    float rayDistance = 0.2;
    const float rayStep = 1.5;
    const int raySteps = 16;

    // shadowtex0 contains translucent casters while shadowtex1 contains only
    // opaque casters. Their comparison plus the water-only alpha marker finds
    // water without assuming a sea level, so raised and modded pools work too.
    for (int i = 0; i < raySteps; i++) {
        vec3 probePos = playerPos + towardWater * rayDistance - towardSun * 0.06;
        vec3 shadowPos = GetShadowPos(probePos);
        if (all(greaterThan(shadowPos.xy, vec2(0.001))) && all(lessThan(shadowPos.xy, vec2(0.999)))) {
            float translucentShadow = shadow2D(shadowtex0, shadowPos).x;
            float opaqueShadow = shadow2D(shadowtex1, shadowPos).x;
            vec4 shadowColorSample = texture2D(shadowcolor0, shadowPos.xy);
            float waterMarker = 1.0 - smoothstep(0.003, 0.008, abs(shadowColorSample.a - 0.03125));
            float waterHit = (1.0 - translucentShadow) * opaqueShadow * waterMarker;
            if (waterHit > 0.8) {
                waterShadowColor = shadowColorSample.rgb;
                break;
            }
        }
        rayDistance += rayStep;
    }

    float causticLuma = GetLuminance(waterShadowColor);
    float causticPattern = smoothstep(0.035, 0.42, causticLuma);
    float angularFocus = pow(receiverFacing, 0.65);
    float distanceFade = 1.0 - smoothstep(12.0, 24.0, rayDistance);
    float weatherAndSky = sunVisibility * invRainFactor * pow2(skyLight);

    return waterShadowColor * causticPattern * angularFocus * distanceFade
         * weatherAndSky * WATER_CAUSTIC_STRENGTH * 2.2;
}
