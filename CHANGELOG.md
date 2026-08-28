# 1.0.1

- Fixed shadered liquids disappearing when Celeritas is installed.
- Fixed some entities and custom models rendering translucent with shaders enabled.
- Improved shader compatibility for framed blocks, contained shapes, and tile entities.
- Added AbyssalCraft sunlight colours and a Dreadlands red-sky treatment.
- Added an optional in-game update notification.

## Technical Changes

- Render vanilla fluid geometry directly into AUSM's extended terrain buffer, preserving the vertex metadata Celeritas and Nothirium require.
- Restore opaque depth writes for entity and block-entity G-buffer passes after translucent rendering.
- Add Abyssal dimension defines and shader transforms for sky, lighting, volumetric light, and lens-flare handling.
- Separate client and common proxy initialization so dedicated servers do not load client-only classes.
