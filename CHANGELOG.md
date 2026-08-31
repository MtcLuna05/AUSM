# 1.0.3

- Fixed Complementary's lower sky flickering after terrain finishes loading.
- Fixed corrupted terrain geometry and unreliable chunk rendering on Cleanroom.
- Fixed Complementary Unbound shader compilation in affected configurations.
- Fixed the player’s initial chunk occasionally failing to render after joining a world.
- Improved Complementary patch generation so water, cloud, and material options remain available.

## Technical Changes

- Clamp the Complementary lower-horizon sky input and route shadered lower-sky geometry through the sky pass without applying shaderless fog smoothing.
- Restore each terrain VBO’s own extended vertex layout immediately before it is drawn, including Cleanroom paths that bypass the normal terrain hook.
- Preprocess Complementary Unbound’s late `pi` declaration before dependent includes, and retain direct Complementary program sources rather than overlaying incompatible Euphoria programs.
- Queue a one-time client chunk refresh after the player’s first position packet.
- Keep the Java 8 and Java 25 artifacts aligned for these rendering and shader-compatibility fixes.
