# Actually Usable Shader Mod

AUSM is an experimental shader pipeline for Minecraft 1.12.2 on Cleanroom. It aims to make modern shader-pack behavior usable on the 1.12 rendering stack by combining OptiFine-style shader-pack compatibility with an Iris-inspired pipeline model.

The project is not a finished OptiFine replacement yet. Shader-pack compatibility is improving, but visual parity depends on the pack and on the parts of the Iris/OptiFine feature surface that have already been ported.

## What It Does

- Loads shader packs from the normal `shaderpacks/` folder as zip files or unpacked folders.
- Provides an in-game shader-pack screen with apply, refresh, enable/disable, folder open, drag/drop import, preview hiding, and per-pack settings.
- Saves the selected pack and enabled state in `config/ausm/shaders.properties`.
- Saves per-pack option overrides in `config/ausm/shader-options/`.
- Exposes keybinds for opening the shader UI, reloading shaders, and toggling shaders.
- Backports an Iris-style render-stage model so shader-facing `renderStage` behavior can line up with modern packs where possible.
- Parses and applies a growing set of OptiFine/Iris shader-pack metadata, including options, profiles, screens, draw buffers, alpha/blend directives, custom textures, render targets, compute metadata, image declarations, and SSBO declarations.
- Keeps an auditable Iris migration trail in `IRIS_PORTING_LOG.md`.

## Requirements

- Minecraft 1.12.2
- Cleanroom Loader, currently developed against `0.5.12-alpha`
- A Java runtime compatible with the target modpack and Cleanroom setup
- For development builds: JDK 25 is used by the Gradle toolchain, with Java 21 bytecode targeting in the current build scripts

AUSM is not designed to be installed alongside OptiFine. Both mods target the same shader/rendering surface, so running both together should be treated as unsupported unless you are intentionally debugging a conflict.

## Installation

1. Download the newest `AUSM-<version>-Java25.jar` from the GitHub releases page.
2. Put the jar in the instance `mods/` folder.
3. Start the game once so AUSM can create `shaderpacks/` and `config/ausm/` if they do not already exist.
4. Put shader-pack zip files or folders in `shaderpacks/`.
5. Open the AUSM shader screen in game and select a pack.

Builds:
https://github.com/MtcLuna05/AUSM/releases

## Controls

Default keybinds are registered under the `AUSM Shaders` category:

| Key | Action |
| --- | --- |
| `O` | Open shader configuration |
| `R` | Reload the selected shader pack |
| `K` | Toggle shaders on or off |

These can be changed from Minecraft's normal controls menu.

## Shader Pack Notes

AUSM accepts both folder shader packs and `.zip` shader packs. The selected pack name is persisted, and missing packs automatically fall back to `OFF` instead of leaving the renderer in a stale state.

Per-pack settings are read from shader-pack metadata and stored separately from the pack itself. This keeps local overrides out of the shader-pack archive and allows the same pack to be reloaded with different option values.

Compatibility is still under active development. If a pack fails to compile, AUSM reports shader compile failures in chat and logs the compile details. Some modern shader-pack features are parsed before they are fully wired into rendering, so a parsed directive does not always imply complete visual support yet.

## Development

Build the mod with:

```bash
./gradlew --no-daemon build
```

The distributable remapped jar is written to `build/libs/AUSM-<version>-Java25.jar`. The development jar is also produced and uses the `-dev` classifier.

Useful Gradle tasks:

| Task | Purpose |
| --- | --- |
| `build` | Compile, remap, test, and assemble jars |
| `runClient` | Launch a development client |
| `runServer` | Launch a development server |
| `genSources` | Generate Minecraft sources for IDE navigation |

The repository uses GitHub Actions to build every push to `master`, upload an `AUSM-latest` artifact, and publish an immutable prerelease tagged as `v<mod_version>+<short_sha>` with `AUSM-<mod_version>-Java25.jar` attached.

## Repository Hygiene

This repository intentionally does not include decompiled OptiFine source or extracted OptiFine patch trees. Shader-pack compatibility work should be implemented as original AUSM code, documented porting notes, or narrow references that can be audited in `IRIS_PORTING_LOG.md`.
