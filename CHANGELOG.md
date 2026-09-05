# 1.0.4 (Draft)

- Fixed displaced shaderless bloom after enabling and disabling shaders.
- Fixed first-person hand transparency and effects leaking through the hand with shaders enabled.
- Made texture-defined bloom use the dedicated bloom layer, including Random Things luminous blocks without shaders. Removed material-brightness extraction as an alternate bloom source.
- Fixed excess emission on GPOM double slopes and improved contained-block emission handling.
- Improved LittleTiles and CreativeCore vertex-buffer compatibility, including extended vertex layouts and cached render layers.
- Improved Global Facades rendering integration and texture-based bloom routing.
- Gave the shader menu a dedicated settings-page presentation and consistent top and bottom dirt bars, including when opened in-world.
- Improved Complementary patch selection and luminous-block material configuration.
- Improved Botania sky-rendering compatibility.
- Added terrain/shadow draw batching and optional GPU timing diagnostics.

## Technical Changes

- Resolve chunk-offset uniforms and attributes from the live OpenGL program instead of trusting cached numeric program IDs across shader transitions.
- Keep Minecraft's texture-unit cache synchronized with OpenGL during bloom passes and restore the block-atlas binding afterward.
- Correct hand depth compression, depth-snapshot ownership, and alpha blend state before deferred composition.
- Keep shader-property evaluation used by terrain compilation away from worker-thread OpenGL capability queries.
- Add regression coverage for texture-only bloom routing, shader menus, terrain transforms, compatibility hooks, and hand rendering.

Release notes are still being compiled. Java 8 runtime validation is separate from the confirmed Java 25 in-game fixes.

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
