#ifndef AUSM_OWNED_SKY_GLSL
#define AUSM_OWNED_SKY_GLSL

// This sky is deliberately procedural.  Native Botania/Astral renderers are
// suppressed by AUSM, so Entree owns every visible planet, ribbon, rainbow,
// and Astral detail without depending on a bound mod texture.

float AusmOwnedSkyHash(vec3 value) {
    value = fract(value * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

float AusmOwnedSkyDisc(vec3 ray, vec3 direction, float radius) {
    return smoothstep(cos(radius), cos(radius * 0.72), dot(ray, direction));
}

vec3 AusmOwnedSkySunVector() {
    const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994),
                                      -sin(sunPathRotation * 0.01745329251994));
    // The shader-owned canvas is used for every world0-mapped dimension.
    // Do not gate this on OptiFine's vanilla-Overworld macro: that macro is
    // absent in some mapped worlds even though the canvas is active.
    float angle = fract(timeAngle - 0.25);
    angle = (angle + (cos(angle * 3.14159265358979) * -0.5 + 0.5 - angle) / 3.0) * 6.28318530717959;
    return normalize((gbufferModelView * vec4(vec3(-sin(angle), cos(angle) * sunRotationData) * 2000.0, 1.0)).xyz);
}

float AusmOwnedSkyStars(vec3 ray, float scale, float threshold) {
    vec3 grid = ray * scale;
    vec3 cell = floor(grid);
    vec3 local = fract(grid) - 0.5;
    float radius = length(local);
    vec3 pixelFootprint = fwidth(grid);
    float footprint = max(max(pixelFootprint.x, pixelFootprint.y), pixelFootprint.z);
    float filterWidth = clamp(footprint * 0.35, 0.015, 0.16);
    float core = 1.0 - smoothstep(0.10 - filterWidth, 0.20 + filterWidth, radius);
    float seed = AusmOwnedSkyHash(cell);
    float selected = smoothstep(threshold - 0.008, 1.0, seed);
    float resolved = 1.0 - smoothstep(0.22, 0.62, footprint);
    return core * selected * resolved;
}

// Exact Botania r1.10 image assets are shipped with Entree.  The transforms
// below reproduce SkyblockSkyRenderer's fixed planet chain while keeping the
// entire draw shader-side and independent of Botania's renderer state.
mat3 AusmBotaniaRotX(float degrees) {
    float c = cos(radians(degrees)), s = sin(radians(degrees));
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

mat3 AusmBotaniaRotY(float degrees) {
    float c = cos(radians(degrees)), s = sin(radians(degrees));
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 AusmBotaniaRotZ(float degrees) {
    float c = cos(radians(degrees)), s = sin(radians(degrees));
    return mat3(c, s, 0.0, -s, c, 0.0, 0.0, 0.0, 1.0);
}

mat3 AusmBotaniaRotAxis(vec3 axis, float degrees) {
    axis = normalize(axis);
    float c = cos(radians(degrees)), s = sin(radians(degrees)), k = 1.0 - c;
    return mat3(
        c + axis.x * axis.x * k, axis.y * axis.x * k + axis.z * s, axis.z * axis.x * k - axis.y * s,
        axis.x * axis.y * k - axis.z * s, c + axis.y * axis.y * k, axis.z * axis.y * k + axis.x * s,
        axis.x * axis.z * k + axis.y * s, axis.y * axis.z * k - axis.x * s, c + axis.z * axis.z * k
    );
}

mat3 AusmBotaniaSkyMatrix() {
    // SkyblockSkyRenderer draws planet 0 after these two exact transforms:
    // rotate(-90, 0,1,0), then rotate(90, .5,.5,0).  Each following planet
    // inherits only the rotation applied after its predecessor is drawn.
    return AusmBotaniaRotY(-90.0) * AusmBotaniaRotAxis(vec3(0.5, 0.5, 0.0), 90.0);
}

vec3 AusmBotaniaPlanetUvMask(vec3 viewRay, mat3 transform, float halfSize) {
    mat3 viewTransform = mat3(gbufferModelView) * transform;
    // Botania's quads are at local y=100, with x/z as their texture axes.
    vec3 direction = normalize(viewTransform * vec3(0.0, 1.0, 0.0));
    vec3 right = normalize(viewTransform * vec3(1.0, 0.0, 0.0));
    vec3 up = normalize(viewTransform * vec3(0.0, 0.0, 1.0));
    float forward = dot(viewRay, direction);
    float halfAngle = halfSize * 0.01;
    vec2 uv = vec2(dot(viewRay, right), dot(viewRay, up)) / max(forward * halfAngle * 2.0, 0.00001) + 0.5;
    float inside = step(0.0, forward) * step(0.0, uv.x) * step(uv.x, 1.0) * step(0.0, uv.y) * step(uv.y, 1.0);
    return vec3(uv, inside);
}

float AusmBotaniaVisibility() {
    // Entree owns these details continuously; do not fade them out during
    // either the day or night half of the world cycle.
    return 1.0 - rainFactor * 0.65;
}

vec3 AusmBotaniaSkyTexture(vec3 worldRay, float visibility) {
    vec2 uv = vec2(atan(worldRay.z, worldRay.x) * 0.1591549430919 + 0.5,
                   asin(clamp(worldRay.y, -1.0, 1.0)) * 0.3183098861838 + 0.5);
    vec4 skySample = texture2D(ausmBotaniaSkybox, uv);
    return skySample.rgb * skySample.a * visibility;
}

vec3 AusmBotaniaRainbowTexture(vec3 worldRay, float visibility) {
    vec2 uv = vec2(atan(worldRay.z, worldRay.x) * 0.1591549430919 + 0.5,
                   asin(clamp(worldRay.y, -1.0, 1.0)) * 0.3183098861838 + 0.5);
    uv.x = fract(uv.x + floor(frameTimeCounter * 0.05) * 0.0027777778);
    vec4 rainbowSample = texture2D(ausmBotaniaRainbow, uv);
    return rainbowSample.rgb * rainbowSample.a * visibility;
}

// Use four smooth, seeded segments around each ring.  This retains a calm
// broad wave while avoiding the repeated high-energy sine crests.
float AusmBotaniaLowEnergyWave(float angle, float phase) {
    float segmentPosition = (angle - phase) * 0.63661977236758; // four per turn
    float segment = mod(floor(segmentPosition) + 4.0, 4.0);
    float blend = fract(segmentPosition);
    blend = blend * blend * (3.0 - 2.0 * blend);
    float low = AusmOwnedSkyHash(vec3(segment, 17.0, 41.0)) * 2.0 - 1.0;
    float high = AusmOwnedSkyHash(vec3(mod(segment + 1.0, 4.0), 17.0, 41.0)) * 2.0 - 1.0;
    return mix(low, high, blend);
}

// SkyblockSkyRenderer builds each ribbon from 90 finite quads around a
// radius-20 ring (radius-10 for the rainbow).  Convert a canvas ray back to
// that local ring and sample only where it intersects the authored strip;
// this retains the original 256x32 assets without turning them into a dome.
vec3 AusmBotaniaStripUvMask(vec3 viewRay, mat3 transform, float radius, float height,
                             float phaseDegrees, float waveAmplitude) {
    mat3 viewTransform = mat3(gbufferModelView) * transform;
    vec3 localRay = transpose(viewTransform) * viewRay;
    float horizontal = max(length(localRay.xz), 0.00001);
    float localY = localRay.y * radius / horizontal;
    float angle = atan(localRay.z, localRay.x);
    float phase = radians(phaseDegrees);
    float centerY = waveAmplitude * AusmBotaniaLowEnergyWave(angle, phase);
    float v = (localY - centerY) / height;
    float mask = step(0.0, v) * step(v, 1.0);
    float u = fract((angle - phase) * 0.1591549430919);
    return vec3(u, v, mask);
}

vec3 AusmBotaniaFiniteSkybox(vec3 viewRay, float visibility) {
    // Botania's tick unit runs at 20 Hz. Keep the calm low-energy path, but
    // advance it fast enough for its broad motion to be visible in play.
    float ticks = frameTimeCounter * 5.0;
    vec3 color = vec3(0.0);

    // Botania rotates each complete strip ring in addition to advancing the
    // strip phase. Without this second transform the texture travels forward
    // while the ribbon itself has no lateral drift.
    vec3 uvMask = AusmBotaniaStripUvMask(viewRay, AusmBotaniaRotX(220.0) * AusmBotaniaRotY(ticks * 0.12),
                                         20.0, 2.0, ticks * 2.4, 0.90);
    vec4 strip = texture2D(ausmBotaniaSkybox, uvMask.xy);
    color += strip.rgb * strip.a * uvMask.z;

    uvMask = AusmBotaniaStripUvMask(viewRay, AusmBotaniaRotX(240.0) * AusmBotaniaRotY(ticks * 0.024),
                                    20.0, 2.0, ticks * 0.48, 0.65);
    strip = texture2D(ausmBotaniaSkybox, uvMask.xy);
    color += strip.rgb * strip.a * uvMask.z * vec3(1.0, 0.4, 0.4);

    uvMask = AusmBotaniaStripUvMask(viewRay, AusmBotaniaRotX(290.0) * AusmBotaniaRotY(ticks * 0.24),
                                    20.0, 2.0, ticks * 4.8, 1.15);
    strip = texture2D(ausmBotaniaSkybox, uvMask.xy);
    color += strip.rgb * strip.a * uvMask.z * vec3(0.4, 1.0, 0.7);
    return color * visibility;
}

vec3 AusmBotaniaFiniteRainbow(vec3 viewRay, float visibility) {
    mat3 rotation = AusmBotaniaRotY(ausmBotaniaRainbowRotation.x)
                  * AusmBotaniaRotZ(ausmBotaniaRainbowRotation.y);
    vec3 uvMask = AusmBotaniaStripUvMask(viewRay, rotation, 10.0, 2.0, 0.0, 0.0);
    vec4 rainbow = texture2D(ausmBotaniaRainbow, uvMask.xy);
    return rainbow.rgb * rainbow.a * uvMask.z * visibility;
}

void AusmCompositeBotaniaPlanet(inout vec3 color, vec3 colorWithoutStars,
                                vec3 uvMask, vec4 planet, float visibility) {
    // Every star field is intentionally omitted behind the authored planet
    // quad. The transparent border remains transparent.
    // Cut the transparent border from the original 16x16 planet texture,
    // then make every authored texel fully opaque. This preserves the planet
    // silhouette without allowing Astral stars through semitransparent pixels.
    float quadCoverage = uvMask.z;
    float coverage = quadCoverage * step(0.01, planet.a);
    // PaletteAlpha planet textures store white RGB in their transparent
    // border. Keep that border visually transparent, but restore the starless
    // sky across the whole finite quad first: Astral/base stars can never
    // show through an authored Botania planet's empty texels.
    color = mix(color, colorWithoutStars, quadCoverage);
    planet.rgb *= visibility;
    planet.a = coverage;
    color = mix(color, planet.rgb, planet.a);
}

// This is Complementary's own stylised sun/moon compositor, kept intact for
// the shader-owned canvas.  The only local helper is its existing star-space
// projection, inlined so world0-mapped dimensions do not need the full stars
// include just to render the moon texture noise.
vec2 AusmComplementaryStarCoord(vec3 viewPos) {
    vec3 wpos = normalize((gbufferModelViewInverse * vec4(viewPos * 1000.0, 1.0)).xyz);
    vec3 starCoord = wpos / (wpos.y + length(wpos.xz) * 1.0);
    starCoord.x += 0.006 * syncedTime;
    return starCoord.xz;
}

vec3 AusmApplyComplementaryCelestials(vec3 color, vec3 colorWithoutStars,
                                      vec3 viewPos, vec3 sunVec, vec3 upVec) {
    #if AUSM_VOID_CELESTIALS == 1 && SUN_MOON_STYLE >= 2
        vec3 nViewPos = normalize(viewPos);
        float VdotS = dot(nViewPos, sunVec);
        float SdotU = dot(sunVec, upVec);
        float absVdotS = abs(VdotS);
        #if SUN_MOON_STYLE == 2
            float sunSizeFactor1 = 0.9975;
            float sunSizeFactor2 = 400.0;
            float moonCrescentOffset = 0.0055;
            float moonPhaseFactor1 = 2.45;
            float moonPhaseFactor2 = 750.0;
        #else
            float sunSizeFactor1 = 0.9983;
            float sunSizeFactor2 = 588.235;
            float moonCrescentOffset = 0.0042;
            float moonPhaseFactor1 = 2.2;
            float moonPhaseFactor2 = 1000.0;
        #endif
        if (absVdotS > sunSizeFactor1) {
            float sunMoonMixer = sqrt1(sunSizeFactor2 * (absVdotS - sunSizeFactor1));

            #ifdef SUN_MOON_DURING_RAIN
                sunMoonMixer *= 1.0 - 0.4 * rainFactor2;
            #else
                sunMoonMixer *= 1.0 - rainFactor2;
            #endif

            if (VdotS > 0.0) {
                sunMoonMixer = pow2(sunMoonMixer) * GetHorizonFactor(SdotU);
                #ifdef CAVE_FOG
                    sunMoonMixer *= 1.0 - 0.65 * GetCaveFactor();
                #endif
                color = mix(mix(color, colorWithoutStars, sunMoonMixer),
                            vec3(0.9, 0.5, 0.3) * 25.0, sunMoonMixer);
            } else {
                float horizonFactor = GetHorizonFactor(-SdotU);
                sunMoonMixer = max0(sunMoonMixer - 0.25) * 1.33333 * horizonFactor;
                vec2 starCoord = AusmComplementaryStarCoord(viewPos) * 0.5 + 0.617;
                float moonNoise = texture2DLod(noisetex, starCoord, 0.0).g
                                + texture2DLod(noisetex, starCoord * 2.5, 0.0).g * 0.7
                                + texture2DLod(noisetex, starCoord * 5.0, 0.0).g * 0.5;
                moonNoise = max0(moonNoise - 0.75) * 1.7;
                vec3 moonColor = vec3(0.38, 0.4, 0.5) * (1.2 - (0.2 + 0.2 * sqrt1(nightFactor)) * moonNoise);

                if (moonPhase >= 1) {
                    float moonPhaseOffset = moonPhase != 4 ? moonCrescentOffset : 0.0;
                    if (moonPhase != 4) moonColor *= 8.5;
                    else moonColor *= 10.0;
                    if (moonPhase > 4) moonPhaseOffset = -moonPhaseOffset;

                    float ang = fract(timeAngle - (0.25 + moonPhaseOffset));
                    ang = (ang + (cos(ang * 3.14159265358979) * -0.5 + 0.5 - ang) / 3.0) * 6.28318530717959;
                    vec2 sunRotationData2 = vec2(cos(sunPathRotation * 0.01745329251994), -sin(sunPathRotation * 0.01745329251994));
                    vec3 rawSunVec2 = (gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData2) * 2000.0, 1.0)).xyz;
                    float moonPhaseVdosS = dot(nViewPos, normalize(rawSunVec2));
                    sunMoonMixer *= pow2(1.0 - min1(pow(abs(moonPhaseVdosS), moonPhaseFactor2) * moonPhaseFactor1));
                } else moonColor *= 4.0;

                #ifdef CAVE_FOG
                    sunMoonMixer *= 1.0 - 0.5 * GetCaveFactor();
                #endif
                color = mix(mix(color, colorWithoutStars, sunMoonMixer), moonColor, sunMoonMixer);
            }
        }
    #endif
    return color;
}

vec3 AusmOwnedSkyColor(vec3 viewRay, vec3 upVec, vec3 sunVec, out vec3 colorWithoutStars) {
    // The full-screen AUSM canvas has no vanilla sky vertices from which to
    // inherit a valid flat sun vector. Rebuild it from shader time uniforms
    // in the fragment stage, exactly as Entree does for normal world passes.
    vec3 celestialSunVec = sunVec;
    float sunUp = dot(celestialSunVec, upVec);
    float dayAmount = smoothstep(-0.16, 0.22, sunUp);
    float skyHeight = dot(viewRay, upVec);
    float horizon = 1.0 - smoothstep(-0.18, 0.46, abs(skyHeight));
    float upperDome = smoothstep(-0.42, 0.82, skyHeight);
    float rainFade = 1.0 - rainFactor * 0.82;

    vec3 nightLow = vec3(0.018, 0.028, 0.075);
    vec3 nightHigh = vec3(0.006, 0.012, 0.042);
    vec3 dayLow = vec3(0.19, 0.34, 0.58);
    vec3 dayHigh = vec3(0.055, 0.14, 0.34);
    vec3 color = mix(mix(nightLow, nightHigh, upperDome), mix(dayLow, dayHigh, upperDome), dayAmount);
    color += mix(vec3(0.12, 0.035, 0.16), vec3(0.34, 0.18, 0.24), dayAmount) * horizon * 0.42;

    vec3 worldRay = normalize(mat3(gbufferModelViewInverse) * viewRay);

    #if AUSM_VOID_NEBULA == 1
        vec2 nebulaCoord = vec2(atan(worldRay.z, worldRay.x) * 0.15915494 + 0.5,
                                asin(clamp(worldRay.y, -1.0, 1.0)) * 0.31830989 + 0.5);
        float nebula = texture2DLod(noisetex, nebulaCoord * 2.0, 0.0).r;
        nebula *= texture2DLod(noisetex, nebulaCoord * 5.0 + vec2(0.17, 0.41), 0.0).g;
        color += vec3(0.16, 0.08, 0.28) * smoothstep(0.18, 0.62, nebula)
                 * (1.0 - dayAmount) * rainFade * (AUSM_VOID_NEBULA_BRIGHTNESS * 0.01);
    #endif

    // Preserve the pre-star sky so opaque planet texels can occlude both the
    // base Complementary field and the Astral field below.
    colorWithoutStars = color;

    #if AUSM_VOID_STARS == 1
        float stars = AusmOwnedSkyStars(worldRay, 190.0, 0.982)
                    + AusmOwnedSkyStars(worldRay.yzx, 310.0, 0.993) * 0.75;
        stars *= (1.0 - dayAmount * 0.92) * rainFade * (AUSM_VOID_STAR_BRIGHTNESS * 0.01);
        color += mix(vec3(0.58, 0.72, 1.0), vec3(1.0, 0.72, 0.92),
                     AusmOwnedSkyHash(floor(worldRay * 190.0) + 7.0)) * stars;
    #endif

    #if AUSM_VOID_ASTRAL_STARS == 1
        float astralStars = AusmOwnedSkyStars(worldRay.zxy, 92.0, 0.974);
        color += vec3(0.34, 0.62, 1.0) * astralStars * 1.65
                 * (1.0 - dayAmount) * rainFade * (AUSM_VOID_ASTRAL_STAR_BRIGHTNESS * 0.01);
    #endif

    // These assets reproduce Botania's Void World renderer. AUSM owns the
    // sky canvas in several dimensions, but Botania detail belongs only to
    // the dedicated Void World route.
    if (ausmSimpleVoidWorld > 0) {
        #if AUSM_VOID_PLANETS == 1
            float planetBrightness = AUSM_VOID_PLANET_BRIGHTNESS * 0.01;
            float planetVisibility = AusmBotaniaVisibility() * planetBrightness;
            mat3 planet0 = AusmBotaniaSkyMatrix();
            mat3 planet1 = planet0 * AusmBotaniaRotX(70.0);
            mat3 planet2 = planet1 * AusmBotaniaRotZ(120.0);
            mat3 planet3 = planet2 * AusmBotaniaRotAxis(vec3(1.0, 0.0, 1.0), 80.0);
            mat3 planet4 = planet3 * AusmBotaniaRotZ(100.0);
            mat3 planet5 = planet4 * AusmBotaniaRotAxis(vec3(1.0, 0.0, 0.5), -60.0);
            vec3 planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet0, 20.0);
            vec4 planet = texture2D(ausmBotaniaPlanet0, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
            planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet1, 12.0);
            planet = texture2D(ausmBotaniaPlanet1, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
            planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet2, 15.0);
            planet = texture2D(ausmBotaniaPlanet2, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
            planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet3, 25.0);
            planet = texture2D(ausmBotaniaPlanet3, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
            planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet4, 10.0);
            planet = texture2D(ausmBotaniaPlanet4, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
            planetUvMask = AusmBotaniaPlanetUvMask(viewRay, planet5, 40.0);
            planet = texture2D(ausmBotaniaPlanet5, planetUvMask.xy);
            AusmCompositeBotaniaPlanet(color, colorWithoutStars, planetUvMask, planet, planetVisibility);
        #endif
    }

    if (ausmSimpleVoidWorld > 0) {
        #if AUSM_VOID_SKYBOX == 1
            float detailVisibility = AusmBotaniaVisibility() * (AUSM_VOID_SKYBOX_BRIGHTNESS * 0.01);
            color += AusmBotaniaFiniteSkybox(viewRay, detailVisibility);
            color += AusmBotaniaFiniteRainbow(viewRay, detailVisibility);
        #endif
    }

    return color * (1.0 - maxBlindnessDarkness);
}

#endif
