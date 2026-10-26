#ifdef FRAGMENT_SHADER
uniform int ausmSimpleVoidWorld;
// The retained sky library declares finite Botania helpers even though its
// finite-mesh branches are disabled. They do not require allocated textures.
uniform sampler2D ausmBotaniaSkybox;
uniform sampler2D ausmBotaniaRainbow;
uniform sampler2D ausmBotaniaPlanet0;
uniform sampler2D ausmBotaniaPlanet1;
uniform sampler2D ausmBotaniaPlanet2;
uniform sampler2D ausmBotaniaPlanet3;
uniform sampler2D ausmBotaniaPlanet4;
uniform sampler2D ausmBotaniaPlanet5;
uniform vec2 ausmBotaniaRainbowRotation;
#include "/lib/atmospherics/ausmOwnedSky.glsl"

vec3 AusmEntreeViewRay() {
    vec4 clip = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec4 view = gbufferProjectionInverse * clip;
    return normalize(view.xyz / view.w);
}
#endif
