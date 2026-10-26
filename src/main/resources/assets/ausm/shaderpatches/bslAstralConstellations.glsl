#ifndef AUSM_BSL_ASTRAL_HELPER
#define AUSM_BSL_ASTRAL_HELPER

uniform int ausmSkyDetailKind;
uniform vec3 ausmAstralConstellationColor;
uniform vec3 ausmAstralTierColor;

#ifdef FSH
uniform int isEyeInWater;

vec4 AusmBslAstralConstellationColor(vec4 detail, vec2 localCoord) {
    #if AUSM_ASTRAL_NATIVE_CONSTELLATIONS == 0
        discard;
    #endif
    #if ASTRAL_CONSTELLATION_STYLE > 0
        #if ASTRAL_CONSTELLATION_STYLE == 1
            vec3 tint = ausmAstralTierColor;
        #else
            vec3 tint = ausmAstralConstellationColor;
        #endif
        float peak = max(max(tint.r, tint.g), tint.b);
        tint = mix(vec3(1.0), tint / max(peak, 0.001), 0.82);
        vec2 p = abs(localCoord * 2.0 - 1.0);
        float radius = dot(p, p);
        float core = 1.0 - smoothstep(0.0064, 0.1156, radius);
        float horizontal = (1.0 - smoothstep(0.03, 0.12, p.y)) * (1.0 - smoothstep(0.18, 0.92, p.x));
        float vertical = (1.0 - smoothstep(0.03, 0.12, p.x)) * (1.0 - smoothstep(0.18, 0.92, p.y));
        float halo = (1.0 - smoothstep(0.0324, 0.6084, radius)) * 0.35;
        float mask = clamp(max(max(core, horizontal), vertical) + halo, 0.0, 1.0);
        detail.rgb = tint * (0.65 + mask * 0.85);
        detail.a *= mask;
    #endif
    if (detail.a <= 0.001) discard;
    if (isEyeInWater == 1) detail.rgb *= 0.25;
    return detail;
}
#endif

#ifdef VSH
vec4 AusmBslAstralConstellationPosition(mat4 ausmGbufferModelView) {
    vec4 position = gl_ModelViewMatrix * gl_Vertex;
    if (ausmSkyDetailKind == 5) {
        vec3 axis = normalize(ausmGbufferModelView[0].xyz);
        float angle = -sunPathRotation * 0.01745329251994;
        float sine = sin(angle);
        float cosine = cos(angle);
        vec3 p = position.xyz;
        position.xyz = p * cosine + cross(axis, p) * sine
                + axis * dot(axis, p) * (1.0 - cosine);
    }
    return gl_ProjectionMatrix * position;
}
#endif

#endif
