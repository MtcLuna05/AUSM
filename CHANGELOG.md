# Unreleased

- Corrected Distant Horizons packed-light channel handling and matched its target sizing and color upscale to the world framebuffer while keeping depth resampling nearest-neighbor.
- Cached block-layer and block-state lookups more safely, and hardened Bloom layer discovery across render-layer implementations.
- Added bounded CPU timing diagnostics for Nothirium shadow chunk compilation and selection.
- Fixed sky flicker and missing ordinary terrain in SRG-only runtimes by correcting sky and cloud mixin targets.
- Added Distant Horizons 3.3.3 shaderpack terrain and water rendering for Complementary Unbound and Reimagined, with saved terrain and hand depth and render-state restoration.
- Fixed black DH terrain by bypassing native DH antialiasing while shaderpack framebuffer overrides are active; shaderpack temporal antialiasing handles the merged terrain.
- Corrected DH block material IDs and invoked deferred DH water rendering in the AUSM pipeline.
- Fixed deferred DH depth sampling so shaderpack clouds and fog respect opaque DH terrain before water renders.
- Added a horizontal terrain coverage transition for Complementary DH rendering, including when temporal antialiasing is disabled, while preserving foreground depth and terrain where DH is missing.
- Kept shader skies rendering below vanilla's four-chunk sky threshold, without changing the terrain render distance.
- Made Complementary rainbows respect opaque DH terrain and enabled shadow-map reception on DH terrain and water within the existing shadow-map range.
- Added opaque Distant Horizons terrain shadow casting for Complementary, using a separate caster shader and light-space selection in the existing shadow map.
- Removed the automated test suite and its JUnit configuration after in-game DH shader verification.
- Ignore Complementary's reserved unused `composite2` placeholder instead of compiling it as an active shader pass.

# 1.0.8

# 1.0.7

- Fixed frozen and opaque enchantment glint, and corrected held-item glint depth and hand sway with Complementary shaders.
- Restored block-texture animation with Nothirium and Naughthirium when LoliASM's on-demand animation is enabled, while retaining Celeritas compatibility safeguards.
- Fixed frozen entity-fire textures with LoliASM's on-demand animation.
- Removed forced full skylight coordinates from shaderless terrain rendering.
- Added synthetic skylight for Abyssal Wasteland terrain with Complementary shaders while preserving block light and shadow processing.

# 1.0.6

- Restored Advanced Rocketry space-station planet and sky textures with Complementary shaders, and disabled terrestrial light shafts in space to remove the white haze.
- Fixed missing shadows on small stations and islands by scaling shadow coverage requirements to nearby terrain and checking depth missed by sparse samples.
- Excluded Alfheim light-only sections from shadow terrain coverage counts.

- Removed AUSM's vanilla client packet-handler mixin, including packet-cancellation guards and chunk-triggered tile-entity reconstruction.
- Made AUSM explicitly client-only and disabled its coremod and mixin hooks on dedicated servers. Servers do not need AUSM installed.
- Removed the Better Portals server-world repair.

# 1.0.5

- Restored in-world menu backgrounds and corrected indexed attachment blending.
- Fixed shaderless terrain, the first-person arm, and held items becoming fully bright when lightmap texturing was disabled.
- Fixed the missing crosshair with shaders and Gnetum by restoring the world image before uncached HUD elements render.

# 1.0.4

- Fixed displaced shaderless bloom after enabling and disabling shaders.
- Fixed first-person hand transparency and effects leaking through the hand with shaders enabled.
- Made texture-defined bloom use the dedicated bloom layer, including Random Things luminous blocks without shaders. Removed material-brightness extraction as an alternate bloom source.
- Fixed excess emission on GPOM double slopes and improved contained-block emission handling.
- Improved LittleTiles and CreativeCore vertex-buffer compatibility, including extended vertex layouts and cached render layers.
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
