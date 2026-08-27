# Actually Usable Shader Mod

AUSM is a shader pipeline for Minecraft 1.12.2 for both Forge and Cleanroom. It aims to make modern shaderpacks usable on Minecraft 1.12.2 while retaining broad compatibility with OptiFine/Iris shaderpack syntax.

## Requirements

- Minecraft 1.12.2
- Either Forge `14.23.5.2860` or Cleanroom `0.5.12-alpha`
- Java 8 at runtime for Forge.
- Java 25 at runtime for Cleanroom.
- For development builds: JDK 25 is used by Gradle; the release pipeline downgrades and shades production classes for Java 8.

## Mod Compatibility

AUSM _cannot_ be installed with OptiFine. Both mods target the same shader/rendering surface, so running both together should be treated as unsupported unless you are intentionally debugging a conflict.

You can however install mods such as (non-exhaustive list):

- Celeritas
- Nothirium
- LoliASM
- Naughtirium
- Lumenized
- Efficient Entities
- Entity Culling
- Render Lib
- Better Portals

When possible, AUSM will use their optimization paths. Keep in mind that support depends on the installed version of said mod.

## Additional Features

AUSM also comes with more than just a shader pipeline. It brings with it a fully custom shader list and shader option menu, made to prioritize player experience while leaving the same exact logic used by other mods to load settings.

The mod doesn't stop at merely supporting existing shaders, it also brings some new API hooks including:

- Fully functional LOD system, that dynamically turns off shader rendering on distant terrain. Shaderpack makers may also specify themselves what should happen at each LOD level.
- Better sky handling, supporting both Astral Sorcery and Botania's void world skybox rendering, with full shader ability to override them.

Some shaderpacks, namely Complementary Unbound / Reimagined, when installed alongside AUSM, will receive some patches to better integrate them with the 1.12.2 environment:

- Custom Astral Sorcery constellation rendering, including a setting to render them using their ritual color. (so Aevitas would render green etc)
- Colored light support for some mod specific blocks, such as Thaumcraft's Nitor or ProjectRed Illumination's blocks.
- Colored dynamic light support, for both vanilla and modded blocks.

If that's not enough to hook you in, the mod also features some improvements even when not having shaders on:

- Dynamic light support, including in game editor. (shaderless only, shaderpacks may have their own dynamic lights logic)
- Broad bloom support, using both automatic extraction through pixel luminosity but also through Lumenized-style packs. (Lumenized is not required, but it may bring performance improvements if it is)
- A number of optimizations that may increase client performance.

## Installation

1. Download the newest AUSM release:
    - `AUSM-<version>-Java8.jar` for Forge.
    - `AUSM-<version>-Java25.jar` for Cleanroom.
2. Put the jar in the instance `mods/` folder.
3. Start the game once so AUSM can create `shaderpacks/` and `config/ausm/` if they do not already exist.
4. Put shader-pack zip files or folders in `shaderpacks/`. You may also install shaderpacks through your launcher, if it supports downloading them in the folder by itself.
5. Open the AUSM shader screen in game and select a pack. You may open it both through a keybind (see below) or by going in the Pause Screen > Options > Shader Settings.

Builds:
https://github.com/MtcLuna05/AUSM/releases

## Compatibility Scope

While in a perfect world this mod would be a de-facto port of Iris for 1.12.2 with additional features, the difference between this version and modern ones makes it impossible to achieve true parity.

Even so, AUSM aims to make many modern shaderpacks usable on 1.12.2 with minimal visual compromises.

## Controls

Default keybinds are registered under the `AUSM Shaders` category:

| Key | Action |
| --- | --- |
| `O` | Open shader configuration |
| `R` | Reload the selected shader pack |
| `K` | Toggle shaders on or off |

These can be changed from Minecraft's normal controls menu.

## Development

Build the mod with:

```bash
./gradlew --no-daemon build
```

The distributable Java X jar is written to `build/libs/AUSM-<version>-JavaX.jar`, where X is either 8 or 25 depending on which version of the mod is being targeted. Intermediate development and remapped jars are also produced for debugging.
