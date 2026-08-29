# 1.0.2

- Fixed liquids disappearing in Complementary when Celeritas or GregTech changes the terrain render path.
- Fixed Complementary Reimagined volumetric clouds rendering incorrectly.

## Technical Changes

- Route GregTech's replaced translucent terrain draw through AUSM's water pass instead of suppressing it as an extra bloom draw.
- Keep Celeritas' native terrain setup from replacing AUSM's active G-buffer program.
- Preserve modern Complementary common shader sources and support stage-guarded `.glsl` programs and their options.

# 1.0.1

- Fixed shadered liquids disappearing when Celeritas is installed.
- Fixed some entities and custom models rendering translucent with shaders enabled.
- Improved shader compatibility for framed blocks, contained shapes, and tile entities.
- Added AbyssalCraft sunlight colours and a Dreadlands red-sky treatment.
- Added an optional in-game update notification.

## Technical Changes

- Render vanilla fluid geometry directly into AUSM's extended terrain buffer, preserving the vertex metadata Celeritas and Nothirium require.
- Restore opaque depth writes for entity and block-entity G-buffer passes after translucent rendering.
- Keep the Java 8 and Java 25 builds aligned for the fluid, depth-state, shader-transform, and compatibility fixes.
- Add Abyssal dimension defines and shader transforms for sky, lighting, volumetric light, and lens-flare handling.
- Separate client and common proxy initialization so dedicated servers do not load client-only classes.
