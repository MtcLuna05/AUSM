# Iris Porting Log

This file tracks AUSM changes intentionally derived from, aligned with, or
validated against Iris. It is meant to make the Iris compatibility work auditable
and to help preserve license attribution as the renderer is migrated toward an
Iris-style pipeline.

Reference source used: local checkout at `/tmp/iris-ausm-ref`.

## 2026-05-30

- Added an Iris-style world rendering phase model in
  `src/main/java/com/l/ausm/pipeline/shader/WorldRenderingPhase.java`.
  - The standard phase names and ordering are aligned with Iris'
    `net.irisshaders.iris.pipeline.WorldRenderingPhase` so shader-facing
    `renderStage` values match Iris ordinals where possible.
  - 1.12-specific and OptiFine program mapping remains AUSM-specific through
    `RenderPass`.
- Migrated pipeline render hooks to phase-based entry points.
  - `PipelineContext.beginPhase(WorldRenderingPhase)` now maps the active phase
    to the current main or shadow pass.
  - Main world hooks in `EntityRendererMixin`, sky hooks in `RenderSkyMixin`,
    entity/block-entity hooks, item hooks, armor glint, spider eyes, and beacon
    beam hooks now select phases instead of directly selecting raw render passes.
- Added shader-facing render-stage compatibility.
  - `PipelineContext` now uploads the `renderStage` uniform as the current
    `WorldRenderingPhase.ordinal()`, matching Iris' exposed uniform behavior.
  - `ShaderPreprocessor` now emits `MC_RENDER_STAGE_*` macros for every AUSM
    phase, matching the Iris macro naming convention.
- Reworked phase-dependent state selection.
  - Phase tracking is now independent of whether a shader program successfully
    binds, matching Iris' separation between current render phase and concrete
    program availability.
  - Added an AUSM `GbufferPrograms` facade shaped after Iris'
    `net.irisshaders.iris.layer.GbufferPrograms`, so future hooks can move
    toward phase updates that are independent from concrete 1.12 pass binding.
  - Added `overridePhase` support to `PipelineContext`, matching Iris'
    distinction between the underlying phase and the shader-visible effective
    phase.
  - Added an AUSM `ShaderOverrides` predicate facade shaped after Iris'
    `net.irisshaders.iris.pipeline.programs.ShaderOverrides` for future
    phase-based override routing.
- Began replacing broad 1.12 sky phases with Iris sky sub-phases.
  - `RenderSkyMixin` now exposes SUN, MOON, and STARS phases around the
    corresponding vanilla 1.12 sky draws instead of keeping all textured sky
    rendering under one broad phase.
  - This is behaviorally matched to Iris' shader-visible `renderStage` model;
    the hook points are 1.12 bytecode-specific.
- Corrected `WorldRenderingPhase` ordering to preserve Iris' official
  `renderStage` ordinals.
  - AUSM-only refinement phases are appended after Iris' HAND_TRANSLUCENT phase
    so shaderpacks comparing `renderStage` against Iris macros see the same
    values for Iris-defined stages.
- Split shaderpack program identity from 1.12 render-pass binding.
  - Added AUSM `ProgramGroup` and `ProgramId` types shaped after Iris'
    `net.irisshaders.iris.shaderpack.loading.ProgramId`.
  - `RenderPass` now carries a `ProgramId`; existing 1.12 pass binding remains
    in place, but shader source names now come from the Iris-shaped program id
    layer.
  - `ProgramSourceSet` and `ShaderPackLayout` now expose/resolve program
    sources through `ProgramId` where possible, leaving `RenderPass` as a
    binding/backport detail.
  - `program.*.enabled` directives now resolve to a typed `ProgramKey`
    (`ProgramId` plus optional dimension id) instead of raw source-name strings.
  - Deferred, composite, and prepare program ids are AUSM backport extensions
    because OptiFine shaderpacks expose those source programs directly.
  - Extended entity vertex format selection now prefers the active phase when
    available.
  - Block atlas rebinding now prefers phase metadata when available.
  - Pipeline reset, cleanup, and shadow-render teardown now reset both active
    pass and active phase.
- Added an Iris-style shader-key metadata layer.
  - Added AUSM `ShaderKey`, `FogMode`, and `LightingModel` types shaped after
    Iris' `net.irisshaders.iris.pipeline.programs.ShaderKey`.
  - Default alpha-test state now comes from the Iris-style shader key instead
    of a raw `RenderPass` switch.
  - `PipelineProgram` now owns its `ShaderKey`, and `PipelineContext` tracks
    the active key alongside the active pass/phase so future fog, lighting, and
    transform work can use Iris-style metadata.
  - Added Iris alpha-test constants to `ShaderAlphaTest`.
  - 1.12's mipped cutout terrain layer now uses Iris' `gbuffers_terrain_cutout`
    program identity, with `gbuffers_terrain_cutout_mip` retained as a
    backport parser alias.
- Moved shader source lookup further toward Iris' ProgramId-first model.
  - `ProgramSourceResolver` can now resolve directly by `ProgramId`, and
    dimension-program detection scans program identities instead of concrete
    1.12 render passes.
  - `ShaderPackLayout` now exposes `ProgramId` program-base enumeration.
- Moved more fallback logic from 1.12 `RenderPass` chains to Iris-style
  `ProgramId.fallback()` chains.
  - Program fallback, inherited draw buffers, alpha-test overrides, blend
    overrides, and attachment blend overrides now consult program identity
    fallback where possible.
- Added Iris' legacy fragment-only program compatibility behavior.
  - If a shaderpack declares a fragment shader without a vertex shader, AUSM now
    synthesizes a GLSL 1.20 passthrough vertex shader matching Iris'
    `ProgramSet.readProgramSource` behavior.
- Added an Iris-style per-program directive bundle.
  - `ShaderProgramDirectives` groups draw buffers, viewport scale, alpha test,
    blend state, attachment blend state, mipmapped buffers, and explicit flips
    by `ProgramId`, mirroring Iris' `ProgramDirectives` responsibility.
  - Clear-disabled buffers are now included in the directive bundle so
    fullscreen pass clearing can move to program-owned metadata without changing
    `clear.*` behavior.
  - Duplicate 1.12 render passes that map to the same Iris `ProgramId` merge
    directive data so aliases such as cutout/cutout-mipped do not hide each
    other.
  - `PipelineProgram` now owns the directive bundle, and alpha/blend state
    application reads from it before falling back to legacy maps.
  - Fullscreen pass clear, flip, mipmap, and viewport-scale handling now reads
    from `PipelineProgram.directives()` instead of directly querying global
    pass-keyed property maps.
- Added Iris' `gbuffers_lightning` program identity and a 1.12 lightning render
  hook.
  - `RenderLightningBoltMixin` routes lightning bolts through the new
    LIGHTNING phase/program while preserving 1.12 render-loop structure.
- Moved inline and property-defined draw-buffer discovery to an Iris-style
  `ProgramId` key path.
  - `ShaderDrawBuffersScanner` now scans shader sources by program identity and
    only adapts back to `RenderPass` for current 1.12 hook compatibility.
- Extended the vegetation/portal diagnostic shaderpack to exercise Iris/OptiFine
  draw-buffer routing more directly.
  - Its water/translucent program can now write to `DRAWBUFFERS:1`, matching
    packs such as MakeUp that emit translucent color through `colortex1`.
  - Its final pass can switch between `colortex0` and `colortex1`, which keeps
    the gbuffer input probes separate from attachment routing and blending
    diagnostics.
  - The probe now keeps draw-buffer declarations in top-level program files
    rather than inside shared includes, avoiding ambiguous fallback selection
    while debugging attachment routing.
  - Added a synthetic `prepare` pass that writes an obvious fog auxiliary color
    into `gaux4`, plus probe modes for fog sampling and fog-mixed translucent
    output. This targets MakeUp-style `finalcolor.glsl` behavior without
    modifying the shaderpack under test.
- Added `run/client/shaderpacks/PortalMegaProbe`, a broad diagnostic pack for
  visual parity work.
  - It probes block ids, render stages, vertex color/alpha, atlas alpha, light
    coordinates, sampled lightmap, normals, mid-block data, fog distance,
    screen coordinates, world position, tangent data, depth textures,
    `colortex0`, `colortex1`, and `gaux4`.
  - It intentionally uses opaque-to-`colortex0`, translucent/water-to-`colortex1`,
    and prepare-to-`gaux4` routing to mirror the attachment families used by
    Iris/OptiFine shaderpacks such as MakeUp.
- Added built-in TAA timing compatibility uniforms.
  - `frameMod` is now uploaded as `frameCounter % 16`, matching the legacy
    OptiFine/MakeUp 1.12 TAA path.
  - `taaOffset` is now uploaded as the same 16-step pixel-scaled jitter vector
    used by MakeUp's newer path, giving Iris-style packs a stable built-in even
    when the shaderpack declares it through unsupported custom uniform syntax.
- Added OptiFine indexed fullscreen filename aliases while keeping Iris-style
  program identities.
  - `deferred_1`/`composite_1` style program ids now resolve shader files named
    `deferred1`/`composite1`, including dimension-local `world*/` variants.
  - Draw-buffer and render-target scanners also see these compact aliases, so
    later fullscreen stages such as MakeUp's TAA/exposure composites are not
    silently skipped during the 1.12 backport.
  - `ShaderProperties` keeps draw-buffer directives keyed by `ProgramId` while
    building `ShaderProgramDirectives`, reducing one more dependency on
    pass-keyed metadata.
- Collapsed several remaining non-visual migration steps into an Iris-style
  shader loading scaffold.
  - Added `ShaderProgramSource`, `ShaderProgramSet`, `ComputeProgramSource`,
    and `ProgramArrayId` to mirror Iris' separation of source inventory,
    indexed program families, and compute source discovery.
  - `PipelineContext` now creates a `ShaderProgramSet` during pack load, and
    `ShaderCompiler` compiles from the source object instead of resolving paths
    directly from a render pass.
  - Added `ShaderLoadingMap` and `ShaderMap` as transitional equivalents of
    Iris' shader-keyed runtime lookup tables.
  - Added `ShaderPackDirectives` and moved alpha-test, blend, viewport-scale,
    explicit-flip, and draw-buffer directive construction to `ProgramId` keyed
    maps, leaving pass-keyed maps as compatibility adapters for current 1.12
    hooks.
  - Added `ShaderBindingLayout` so sampler unit choices have a central Iris-like
    owner instead of being raw constants on the texture binder.
  - Added `ShaderTransformPipeline` as the future replacement point for Iris'
    transform patcher while preserving the current 1.12 compatibility
    transformer behavior.
  - Added `CustomUniformSet` to retain custom uniform/custom variable
    expressions from `shaders.properties` for the next uniform-system port.
- Moved another low-risk runtime slice behind Iris-shaped directive objects.
  - `ShaderPackDirectives` now owns render target directives, render settings,
    per-program directives, and parsed custom uniform/custom variable
    expressions.
  - Per-program directives now inherit from `ProgramId.fallback()` during pack
    load, so runtime alpha, blend, attachment blend, draw-buffer, viewport,
    flip, and mipmap state can be read from `PipelineProgram.directives()`
    without consulting legacy `RenderPass` override maps.
  - `PipelineContext` now uses pack directives for render target state and no
    longer falls back to pass-keyed alpha/blend maps during draw binding.
- Moved custom texture directives toward Iris-style program identity ownership.
  - Added `ShaderTextureDirectives`, owned by `ShaderPackDirectives`, to keep
    global and per-program custom texture bindings keyed by `ProgramId`.
  - `ShaderProperties` now parses texture scopes into program identities first
    and only adapts back to pass-keyed texture maps for compatibility.
  - `PipelineContext` now loads custom textures through
    `packDirectives().textureDirectives()` instead of direct pass-keyed
    property access.
- Added the first Iris-style custom uniform upload path.
  - `CustomUniformSet` now parses `uniform.<type>.<name>` and
    `variable.<type>.<name>` entries from `shaders.properties`, preserving all
    expressions while immediately supporting literal bool/int/float/vec2/vec3/
    vec4 uniforms.
  - `PipelineContext` uploads parsed custom uniforms after the built-in uniform
    registry, matching Iris' model where pack-defined uniforms are attached to
    every active shader program.
  - Full expression evaluation and dependency sorting remain a later backport
    of Iris' `CustomUniforms`/stareval path.
- Added Iris-style metadata parsing for compute, image, and SSBO directives.
  - `ComputeProgramSource` now records `workGroups` and `workGroupsRender`
    directives from `.csh` sources, following Iris' `ComputeSource` metadata.
  - Added `ShaderImageDirective` and `ShaderStorageBufferDirective` for
    `image.*` and `bufferObject.*` properties; these are parsed into
    `ShaderPackDirectives` but are not yet bound to GL images/SSBOs.
  - Added `ShaderComputeDirectives` as the pack-level compute metadata holder.
    Actual compute compilation/dispatch remains a later rendering-facing port.
- Replaced the monolithic AUSM shader compatibility transformer with an
  Iris-style transform pipeline.
  - `ShaderTransformPipeline` now runs explicit transform stages for fragment
    output compatibility, legacy sampler aliases, compatibility-profile texture
    function rewrites, and mid-texcoord aliasing.
  - Removed the old catch-all `ShaderCompatibilityTransformer` so future
    shader-source changes can be ported stage-by-stage.
- Connected source-discovered compute metadata to the active runtime pack
  directives.
  - `ShaderProgramSet` now exposes `ShaderComputeDirectives`, and
    `PipelineContext` enriches its active `ShaderPackDirectives` with compute
    arrays plus shadow/final compute lists after shader sources are loaded.
  - Runtime code now reads render target, custom texture, and custom uniform
    state from the active pack directive object instead of repeatedly reaching
    back through immutable `ShaderProperties`.
  - Compute execution is still intentionally inactive; this change makes the
    Iris-shaped metadata visible to the next rendering-facing port.
- Added an Iris-style fullscreen program-array adapter.
  - `FullscreenProgramArray` maps Iris indexed program families such as
    prepare, deferred, and composite to the current fixed 1.12 `RenderPass`
    slots.
  - `PipelineContext` now runs prepare/deferred/composite fullscreen passes via
    `ProgramArrayId`, keeping current behavior while making array expansion a
    localized future change.
- Tightened custom image directive metadata to match Iris' parser.
  - `ShaderImageDirective` now records the Iris texture target family for
    `image.*` declarations (`TEXTURE_1D`, `TEXTURE_2D`, or `TEXTURE_3D`).
  - Relative images are parsed as 2D relative-size images, and fixed-size
    declarations now require the same dimension count used by Iris.
  - GL image allocation/binding is still a later rendering-facing port; this
    change preserves the shaderpack metadata needed for that implementation.
- Added a wider Iris resource-metadata scaffold.
  - `ShaderTextureDirectives` now preserves raw custom texture declarations
    alongside PNG custom texture bindings, including generated replacement
    sampler names for `texture.<stage>.<sampler>` directives.
  - Added metadata-only `ShaderImageSet` and `ShaderStorageBufferSet` holders
    shaped after Iris' custom image and SSBO ownership model.
  - SSBO directives can now preload optional initial buffer content from the
    shaderpack, matching Iris' `ShaderPack` load-time data path.
  - Pipeline load/resize/cleanup now owns these transitional holders and logs
    unsupported image, raw texture, and SSBO declarations explicitly.
  - GL raw texture upload, image unit binding, and SSBO binding remain later
    rendering-facing ports.
- Added first runtime support for Iris raw custom textures.
  - `ShaderTextureLoader` can upload raw 1D, 2D, and 3D texture declarations
    using Iris-style internal format, pixel format, and pixel type names.
  - Runtime custom texture binding now tracks the GL texture target, so raw
    1D/3D textures are not incorrectly rebound as 2D textures.
  - Raw custom texture samplers are uploaded to shader programs through the
    same custom texture binding path as PNG textures.
  - Added `run/client/shaderpacks/RawTextureDirectiveTest` to validate raw
    texture declaration parsing, upload, and sampler binding.
- Added runtime resource ownership for the remaining Iris resource directives.
  - Stage-scoped raw texture declarations now stay scoped to their target
    program family instead of being loaded globally for every pass.
  - `ShaderImageSet` now allocates custom image textures, resizes relative
    images with the main framebuffer, binds image units, and uploads image
    uniform indices to active programs.
  - `ShaderStorageBufferSet` now allocates GL shader storage buffers, handles
    static initial data and relative resize sizing, binds buffers by index, and
    releases them on pipeline cleanup.
  - The implementation keeps Iris' ownership model but uses LWJGL2/1.12 GL
    entry points for buffer and image binding.
- Added the first active compute shader execution path.
  - `.csh` sources are now compiled and linked into `ComputeProgram` runtime
    programs using the existing shaderpack preprocessor.
  - Compute arrays are dispatched at the matching Iris-style prepare,
    deferred, composite, shadowcomp, shadow, and final points.
  - Fixed `workGroups` and render-relative `workGroupsRender` metadata are
    honored when calculating dispatch group counts.
  - Compute dispatch binds deferred textures, shadow textures, custom textures,
    custom images, SSBOs, built-in uniforms, and custom uniforms before launch.
  - Broad image, SSBO, texture-fetch, and framebuffer memory barriers are
    issued after each dispatch.
  - Custom image allocation now honors the `clear` flag by initializing texture
    storage with zero data where feasible.
  - Raw custom texture bindings upload both the original sampler name and the
    Iris generated replacement sampler name so packs are compatible whether a
    later source transform rewrites the sampler or leaves it unchanged.
  - Packs declaring more array entries than the current fixed adapter exposes
    are logged as metadata, not silently hidden in the loader.
- Added Iris-style feature flag and capability metadata.
  - `ShaderFeatureSet` parses `iris.features.required` and
    `iris.features.optional` into normalized pack directive metadata.
  - `ShaderPipelineCapabilities` summarizes whether the active pack uses
    compute sources, images, SSBO directives, custom uniforms, custom textures,
    or extra fullscreen program-array entries.
  - `PipelineContext` logs requested features and capability metadata during
    pipeline initialization so unsupported rendering-facing work is visible
    before visual debugging starts.
- Added or previously aligned several Iris/OptiFine-compatible pipeline
  features that should remain documented during the migration:
  - Iris-style ping-pong color attachment model and explicit per-pass flips.
  - Iris-style extended terrain/entity vertex payload concepts, including
    `iris_Entity`, mid texture coordinate, tangent, and mid-block style data.
  - `iris_currentAlphaTest`, `iris_overlay`, and other Iris-compatible uniform
    aliases.
  - `clouds`, `sky`, `underwaterOverlay`, `oldHandLight`, back-face directives,
    shadow render toggles, shadow texture filtering flags, and high colortex
    bindings, implemented as compatibility support for Iris/OptiFine packs.
- Added a small 1.12-compatible shader compatibility transform inspired by Iris'
  shader source transformation responsibilities.
  - The implementation is AUSM-specific and intentionally limited to 1.12-era
    GLSL/backport needs.
- Finished the current non-visual Iris metadata migration pass.
  - Expanded `CustomUniformSet` from literal-only upload to a small
    dependency-aware evaluator for pack custom uniforms and variables. It now
    supports literals, booleans, arithmetic, parentheses, `vec2`/`vec3`/`vec4`
    constructors, and variables declared through `variable.<type>.<name>`.
    This behaviorally follows Iris' custom-uniform ownership model while still
    using a compact 1.12 backport evaluator instead of copying Iris' full
    expression engine.
  - Added explicit `sourceName` metadata to `ShaderProgramSource` so indexed
    Iris program arrays can carry their source identity without pretending to
    have a fixed `ProgramId`.
  - Removed an unused pass-keyed custom texture adapter from `ShaderProperties`;
    runtime texture lookup now stays on pack directives keyed by `ProgramId`.
  - At this point the remaining Iris delta is mostly rendering-facing rather
    than loader/metadata plumbing: compute dispatch, image and SSBO binding,
    full Iris expression parity, and visual pass behavior still need direct
    rendering work.
- Started the rendering-facing Iris parity pass after the API/impl split.
  - Block-entity phases now declare block-atlas usage in the central
    `WorldRenderingPhase` metadata, matching the Iris-style rule that phase
    state drives texture restoration before a gbuffer bind.
  - Tightened the AO separation backport so it only stores AO in alpha when an
    ambient-occlusion face actually provided AO values. This avoids treating
    biome tint colors from cutout vegetation as AO, while preserving the
    Iris/OptiFine `separateAo` behavior for real AO quads.
  - Added `VegetationPortalProbe`, a standalone local diagnostic shaderpack for
    comparing Iris-style gbuffer inputs on terrain, cutout vegetation, water,
    and block entities without modifying compatibility test packs.
  - Updated that probe to use Makeup-style high block alias ids and direct-light
    debug views so compatibility issues can be separated into alias, normal, and
    shader-lighting categories.
  - Added a narrow 1.12 compatibility alias for `minecraft:portal` when a pack
    defines the legacy Makeup emissive/portal id `10090` but omits the 1.12
    portal block from `block.properties`. This is a backport compatibility
    deviation; modern Iris packs normally name the block directly for their
    target Minecraft version.
  - Restored the full OptiFine/Iris-facing `mc_Entity` block payload. Terrain
    vertices now write `blockId, renderType, metadata, 0` instead of the older
    AUSM-only `blockId, liquidFlag, 0, 0` approximation, using 1.12
    `IBlockState#getRenderType().ordinal()` and `Block#getMetaFromState` as the
    backport source for OptiFine's documented `mc_Entity.yz` fields.
  - Aligned inline `DRAWBUFFERS`/`RENDERTARGETS` discovery with the active
    program source resolver. The scanner now checks the current dimension's
    resolved program sources, then root fallbacks, and preserves declaration
    order instead of selecting the shortest unordered fallback. This mirrors the
    Iris-style rule that directives belong to the concrete selected program
    source and fixes Makeup-style `prepare` shaders that actively declare
    `DRAWBUFFERS:17` for fog auxiliary output.
  - Tightened the inline directive preprocessor used by draw-buffer discovery.
    Changed shader options are still seeded as global overrides, but unchanged
    default options are no longer leaked into every scanned source file; local
    `#define`s now come from the selected shader source/includes themselves.
    This matches Iris/OptiFine shader compilation behavior more closely and
    avoids water-only macros such as `WATER_F` affecting unrelated programs
    such as `prepare`.
  - Added a 1.12 sky-phase backport guard for star rendering. Makeup-style
    `gbuffers_skybasic` can declare `DRAWBUFFERS:17` so the sky background
    fills both scene color and `gaux4` fog auxiliary output, but 1.12 stars are
    sparse screen-space geometry rendered through the same program. During the
    STARS phase AUSM now keeps the visible color target while suppressing the
    `AUX4` write, avoiding stale bright star pixels in the fog texture sampled
    later by translucent blocks such as portals.
  - Tightened 1.12 translucent/water blend state for Iris-style MRT output.
    The water phase now explicitly enables blending for the actual translucent
    color target (`colortex1`/`gdepth` in legacy naming) and disables blending
    for unrelated MRT attachments while the layer is drawn. This removes
    dependence on whatever indexed blend state a prior sky/entity phase left
    behind.
  - Expanded the local `PortalMegaProbe` diagnostic shaderpack for portal/fog
    parity work. It now mirrors Makeup's `prepare` routing by writing both
    `colortex1` and `gaux4`, adds a `gbuffers_skybasic` probe that can mark
    sky/stars writes into `gaux4`, and adds final-pass modes for isolating raw
    translucent output, raw fog auxiliary output, fog sampled only under
    translucent pixels, and time/frame markers.
  - Extended `PortalMegaProbe` with Makeup-like portal branch diagnostics:
    portal atlas-only, alpha, day/night inputs, approximated portal light,
    lit portal color, lit-plus-fog portal color, and light-coordinate views.
    These are local test-shader additions only; they do not alter production
    shaderpacks.
  - Added, then superseded, a targeted 1.12 foliage normal experiment for
    Makeup-style vegetation ids. This did not match Iris behavior and was
    removed in the next vertex-parity pass.
  - Ported the vanilla BufferBuilder extended-vertex math from Iris'
    `MixinBufferBuilder`, `NormalHelper`, and `NormI8` behavior into
    `IrisVertexMath`. Terrain/entity quads now use Iris' face-normal formula,
    Iris' snorm byte packing semantics, and Iris' smooth tangent path for
    triangles instead of the older AUSM-local implementation.
  - Tried re-aligning terrain `mc_Entity.y` with Iris' modern terrain/fluid
    flag, but reverted it immediately because 1.12 OptiFine-era packs still
    depend on the older render-type-style payload. This remains a deliberate
    1.12 backport deviation from modern Iris.
  - Reworked celestial light vectors toward the OptiFine/Iris convention.
    `sunPosition`/`moonPosition` now use the shifted sun angle (`celestialAngle
    + 0.25` wrapping like OptiFine shadow setup) so daytime terrain normals dot
    against an above-horizon light vector. `shadowLightPosition` was then
    decoupled back to the previous 1.12 shadow-camera-compatible vector because
    Makeup's shadow bias path became too harsh when it reused the direct-light
    vector.
  - Ported Iris-style `eyeBrightnessSmooth` behavior from Iris'
    `CommonUniforms`/`SmoothedVec2f`/`SmoothedFloat` path. AUSM now parses
    `eyeBrightnessHalflife` and applies the same exponential half-life
    smoothing in frame time instead of aliasing `eyeBrightnessSmooth` directly
    to raw `eyeBrightness`.
  - Added Iris' built-in replacement `iris_LightmapTextureMatrix` uniform from
    `BuiltinReplacementUniforms`. This is behaviorally copied for shaderpack
    compatibility, while AUSM still keeps the 1.12 fixed-function lightmap
    texture bindings for legacy programs.
  - Ported another CommonUniforms compatibility batch from Iris. AUSM now
    exposes Iris-style player state uniforms (`isRightHanded`, sneaking,
    sprinting, hurt, invisible, burning, on-ground), 1.12-backed blindness and
    night-vision values, dummy modern-only darkness/mood uniforms, dynamic
    bound texture metadata (`gtextureId`, `gtextureSize`, `atlasSize`),
    `blendFunc`, and smoothed `wetness` using `wetnessHalflife` /
    `drynessHalflife`.
  - Added matrix3 uniform upload support and Iris normal-matrix aliases
    (`iris_NormalMat`, `iris_NormalMatrix`, plus legacy `normalMatrix` /
    `gl_NormalMatrix`). This follows Iris' transformer naming expectations,
    using the inverse-transpose of the captured 1.12 model-view matrix as the
    backport source.
  - Added additional Iris/transformer matrix aliases for modern shader source
    compatibility: `modelViewMatrix`, `iris_ModelViewMatrix`,
    `iris_ModelViewMat`, inverse variants, `projectionMatrix`,
    `iris_ProjectionMatrix`, `iris_ProjMat`, inverse variants, and identity
    `textureMatrix` / `iris_TextureMat`. Iris normally rewrites many of these
    through AST transformers; AUSM currently provides uniform aliases as the
    1.12 backport bridge until the transformer layer is made fully AST-based.
  - Added a conservative fullscreen/composite built-in transform stage based on
    Iris' `CompositeTransformer` / `CompositeCoreTransformer`: fullscreen
    passes now rewrite legacy texture matrices to identity, fixed-function
    matrix built-ins to the Iris fullscreen equivalents, `gl_NormalMatrix` to
    identity, `gl_Color` to white, and vertex `gl_Normal` to the default normal.
    This remains a text transform rather than Iris' AST transform, so it is a
    transitional 1.12 backport.
  - Replaced the old fixed-factor `centerDepthSmooth` lerp with Iris/OptiFine
    half-life smoothing, parsing `centerDepthHalflife` and reusing the same
    exponential decay model as Iris' `CenterDepthSampler`. AUSM still exposes
    the legacy float uniform directly; Iris' `iris_centerDepthSmooth` 1x1
    sampler path is not ported yet.
  - Added Iris internal fog uniform aliases from `FogUniforms` /
    `IrisInternalUniforms`: `iris_FogStart`, `iris_FogEnd`,
    `iris_FogDensity`, `iris_FogColor`, and `fogShape`. These are backed by
    1.12 GL/world fog state instead of Iris' modern captured Sodium fog
    storage.
  - Added render-target size compatibility uniforms for colortex, legacy gaux,
    and depth samplers. Iris normally derives these from `RenderTargets`; AUSM
    now exposes the active per-attachment dimensions from `DeferredFramebuffer`,
    including scaled buffers from `size.buffer.*` directives.
  - Added AUSM GUI-only option adjustment controls. Hovered shader options can
    now be adjusted with the mouse wheel and left/right/up/down arrow keys;
    open dropdowns keep the wheel for menu scrolling, and Shift still resets
    sliders to defaults. This is intentionally outside the Iris rendering port
    and keeps AUSM's separate GUI behavior.
  - Ported Iris' `CenterDepthSampler` / `CompositeDepthTransformer` behavior
    for `centerDepthSmooth` consumers. AUSM now maintains a 1x1 floating-point
    `iris_centerDepthSmooth` texture, binds it as an internal sampler, and
    rewrites fullscreen shader declarations of `uniform float centerDepthSmooth`
    into samples from that texture. The smoothing source is still the 1.12
    copied terrain depth value, so this is a behavioral backport rather than a
    direct copy of Iris' modern render-target plumbing.
  - Ported a broader CommonUniforms/SystemTime/CameraUniforms compatibility
    batch from Iris. `frameCounter` now follows Iris' 720720-frame wrap,
    `frameTimeCounter` is accumulated and reset hourly instead of being raw
    monotonic wall time, and AUSM exposes `lastFrameTime` plus a smoothed
    `frameTimeSmooth` helper. Camera uniforms now include Iris' shifted
    `cameraPosition` / `previousCameraPosition` model with 30000-block
    precision shifts, while also exposing unshifted `cameraPositionInt`,
    `cameraPositionFract`, `previousCameraPositionInt`, and
    `previousCameraPositionFract`. Added Iris-style celestial compatibility
    uniforms `sunAngle`, `shadowAngle`, `endFlashPosition`, and changed
    `upPosition` to use the captured model-view orientation instead of a raw
    world-space unit vector. The implementation is adapted to 1.12's camera and
    celestial APIs.
  - Added Iris-style generated `noisetex` availability. AUSM now parses
    `noiseTextureResolution`, creates a deterministic generated RGBA noise
    texture when a pack does not provide `texture.noise`, binds it on the
    reserved `noisetex` unit for every shader pass, and lets custom texture
    directives override it per pass. This behavior is based on Iris'
    `CustomTextureManager` / `NativeImageBackedNoiseTexture` contract, but the
    pixel generation is a small 1.12/LWJGL backport rather than copied native
    image code.
  - Added additional Iris hardcoded/common compatibility uniforms used by
    several shaderpacks: `blindFactor`, `timeAngle`, `timeBrightness`,
    `moonBrightness`, `shadowFade`, `day`, `night`, `dawnDusk`, `shdFade`,
    `rainFactor`, `rainStrengthS`, `rainStrengthShiningStars`,
    `rainStrengthS2`, `eyeBrightnessM`, `velocity`, `pi`,
    `anisotropicFiltering`, `blockEntityId`, and `currentRenderedItemId`.
    Values are backed by 1.12 world/player state where possible; modern-only
    managed ids and anisotropy remain conservative placeholders.
  - Ported Iris render-target clear color semantics from `ClearPassCreator` /
    `PackRenderTargetDirectives`. AUSM now uses Iris-compatible default clear
    colors (`colortex1`/legacy `gdepth` clears white, auxiliary buffers clear
    transparent black, `colortex0` remains opaque black as the 1.12 fog-color
    backport fallback) and scans `const vec4 <buffer>ClearColor = vec4(...)`
    directives from shader sources. This fixes another class of history/fog/
    bloom assumptions without changing render pass ordering.
  - Expanded render target directive parity with Iris' `PackRenderTargetDirectives`.
    AUSM now recognizes additional Iris internal formats including `RGBA`,
    `RG8`, `R16`, `RG16`, `RGB16`, `RG16F`, `R32F`, `RG32F`, `RGB32F`, and
    `RGBA32F`; strips comments before matching const directives; and upgrades
    legacy `gdepth` / `colortex1` to `RGBA32F` by default when a shader declares
    `uniform ... gdepth`, matching Iris' special gdepth compatibility rule
    unless the pack explicitly requested a different format.
  - Added more shadow compatibility metadata from Iris' shadow directives:
    `shadowMapResolution`, `shadowtex0Size`, `shadowtex1Size`, `shadowSize`,
    `shadowcolor0Size`, and `shadowcolor1Size` uniforms now reflect the active
    shadow framebuffer. AUSM also reads legacy comment directives
    `/* SHADOWRES:... */` and `/* SHADOWHPL:... */` as fallbacks for
    `shadowMapResolution` and `shadowDistance`.
  - Expanded custom texture stage handling toward Iris' `ShaderProperties` /
    `TextureStage` model. AUSM now accepts `texture.gbuffers.*` for both
    gbuffers and shadow passes, `texture.shadow.*`, `texture.prepare.*`,
    `texture.deferred.*`, and `texture.composite.*` / `texture.composite_all.*`
    for composite plus final. It also accepts global `customTexture.<sampler>`
    PNG bindings. Custom sampler names are no longer discarded when they do not
    match a built-in OptiFine sampler; AUSM assigns them custom texture units
    and uploads their sampler uniforms explicitly for each pass. Raw 1D/2D/3D
    custom texture definitions are still not ported.
  - Started the MakeUp No Effects parity pass by aligning celestial transforms
    with Iris' `CelestialUniforms` model. AUSM now injects the shaderpack
    `sunPathRotation` into vanilla sky rendering after Minecraft's base
    -90-degree Y rotation, and `sunPosition` / `moonPosition` /
    `shadowLightPosition` now share the same backported Iris transform instead
    of AUSM's previous OptiFine-derived vector math. This is behaviorally
    matched to Iris and adapted to 1.12.2's `RenderGlobal.renderSky` matrix
    order.
    Follow-up: the initial 1.12 mixin ordinal hit the sunrise-gradient
    rotation block; it was corrected to the later sun/moon push-matrix rotation
    so `sunPathRotation` affects the visible celestial bodies.
  - Continued the MakeUp No Effects parity pass by aligning `cameraPosition` /
    `previousCameraPosition` with Iris' `CameraUniforms` source. AUSM now uses
    the 1.12 camera eye position (`Entity#getPositionEyes(partialTicks)`) for
    shifted and unshifted camera uniforms instead of the render entity base
    position. This is a 1.12 backport of Iris' main-camera position contract and
    should improve screen/world reconstruction consumers such as water
    refraction and temporal reprojection.
    The shadow camera grid origin was moved to the same eye position so shadow
    matrices use the same camera-space source as Iris' `ShadowRenderer`.
  - Ported Iris' gbuffers render-target sampler exposure rule from
    `IrisSamplers.addRenderTargetSamplers`. AUSM now binds `colortex4+`
    / `gaux1+`, depth snapshots, center-depth, and noise samplers for gbuffers
    programs while deliberately leaving `colortex0..3` alone so the 1.12 block
    atlas, lightmap, and fixed-function texture units remain intact. This is a
    behavioral backport rather than copied code, and targets packs like MakeUp
    that sample `gaux1` or `depthtex1` from gbuffers water programs.
  - Started replacing AUSM's shadow matrix setup with Iris'
    `ShadowMatrices` behavior. The shadow projection now uses Iris' orthographic
    depth range (`-100.05` to `156.0`) instead of AUSM's older `0.05` to
    `256.0` plus model-view `-100` translation. The shadow model-view now uses
    the Iris baseline rotation order (`X 90`, celestial Z rotation, then
    `sunPathRotation` around X) and keeps the existing 1.12 grid snapping.
    The 1.12 celestial angle is converted into the Iris-style shadow angle in
    place because modern Iris reads this from camera environment attributes.
  - Aligned `shadowtex1` snapshot timing with Iris'
    `ShadowRenderTargets.copyPreTranslucentDepth`. AUSM now copies the shadow
    depth snapshot immediately after solid/cutout shadow terrain and before
    translucent terrain/entities, instead of copying only at the end of the
    whole shadow render. This gives `shadowtex1` the Iris "no translucents"
    meaning while `shadowtex0` remains the live full shadow depth texture.
    Follow-up: the snapshot point was moved again to Iris' actual ordering:
    solid/cutout terrain, entities/block entities, `shadowtex1` copy, then
    translucent terrain.
  - Added a first minimal `shadowcolor0` target to the 1.12 shadow framebuffer,
    matching Iris' `ShadowRenderTargets` support for shadow color draw buffers.
    AUSM now attaches a color texture during shadow rendering, allows shadow
    shaders to write color, clears it to lit white with the depth texture, and
    binds it for `shadowcolor` / `shadowcolor0` samplers. This is an initial
    backport for packs such as MakeUp that use colored shadow logic; higher
    shadow color buffers and per-shadow-buffer clear directives are still not
    fully ported.
  - Tightened celestial uniform parity with Iris' `CelestialUniforms`: AUSM's
    `sunPosition`, `moonPosition`, and `shadowLightPosition` world-vector path
    now uses the same +90-degree sun-angle convention already exposed through
    `sunAngle` / `shadowAngle` before applying the Iris sky transform. This is
    a behavioral backport for 1.12.2's celestial angle source, intended to
    remove the 90-degree direct-light rotation mismatch seen in parity probes.
    Follow-up: an attempted X-sign flip was tested because an intermediate
    probe mode appeared closer in one scene, but later MakeUp parity testing
    showed it inverted the actual shadow orientation. The formula was restored
    to Iris' effective `CelestialUniforms` transform: rotating `(0, 100, 0)`
    by positive `sunPathRotation` around Z yields a negative X component under
    the matrix order being backported.
  - Matched Iris' shadow sampler edge behavior for AUSM's shadow framebuffer.
    Shadow depth and shadow color textures now use `GL_CLAMP_TO_EDGE`, matching
    Iris' `GlSampler` setup, instead of AUSM's earlier white `CLAMP_TO_BORDER`
    behavior. This is adapted to 1.12.2 fixed-function texture setup; AUSM
    still mutates texture parameters directly rather than using modern sampler
    objects.
  - Investigated replacing the temporary always-visible shadow terrain camera
    with an Iris-style bounded culler. The raw shadow clip matrix test culled
    everything except the local chunk because 1.12 chunk rendering applies
    additional camera-relative translations after `setupTerrain`; a
    camera-centered safe-zone cube still produced incorrect terrain coverage in
    MakeUp parity tests. AUSM has been restored to the temporary always-visible
    shadow camera while the full Iris `BoxCuller` / chunk-coordinate equivalent
    is ported. This entry documents the failed 1.12 backport path so it is not
    mistaken for Iris behavior.
  - Ported AUSM shadow depth allocation closer to Iris'
    `ShadowRenderTargets` / `TextureFormat.DEPTH32` behavior by using
    `GL_DEPTH_COMPONENT32` instead of an unsized depth component format. This
    keeps the current 1.12.2 FBO path but avoids driver-dependent shadow depth
    precision during MakeUp/Iris softness parity testing.
  - Aligned the 1.12 shadow pass alpha-test state with Iris' effective cutout
    behavior. AUSM was disabling alpha test before rendering shadow terrain,
    while packs such as MakeUp write texture alpha from `shadow.fsh` instead of
    explicitly discarding transparent texels. The shadow pass now keeps
    `GL_GREATER, 0.1` alpha testing enabled so cutout textures do not write
    solid depth into `shadowtex0/1`.
  - Added an Iris-style gbuffers vertex built-in rewrite for lightmap
    coordinates. Iris' `VanillaTransformer` replaces `gl_TextureMatrix[1]`
    with the `iris_LightmapTextureMatrix` uniform; AUSM now performs the same
    behavioral rewrite for gbuffers vertex shaders using the existing
    fixed-function GLSL path, while leaving fullscreen and shadow stages
    untouched. This is adapted to 1.12.2 text transforms rather than copied
    AST-transform code.
  - Corrected the 1.12 celestial vector backport against Iris'
    `CelestialUniforms`. Iris builds `sunPosition`, `moonPosition`, and
    `shadowLightPosition` by applying the sky transform order
    `Y(-90) -> Z(sunPathRotation) -> X(celestial angle)` to `(0, 100, 0)`.
    AUSM's previous approximation put the time-varying component on the wrong
    axis and left X effectively fixed by `sunPathRotation`. The Java-side
    formula now matches that Iris transform order while still sourcing the
    angle from Minecraft 1.12.2's celestial angle. Follow-up: the first patch
    of this item accidentally put the celestial sine on X instead of leaving X
    fixed by `sunPathRotation`; the closed-form vector has been corrected to
    the literal Iris transform order. Follow-up: parity probe modes that render
    `sunPosition` / `shadowLightPosition` against Iris showed the corrected
    form was still 90 degrees off in screen-space. The 1.12 closed form now
    preserves Iris' effective `Y(-90) -> Z(sunPathRotation) -> X(angle)`
    behavior by making X time-varying from the celestial angle and Z the
    sun-path-tilted component. Follow-up: the corrected axis parity exposed a
    180-degree sign mismatch. Iris exposes the transformed celestial position,
    while the 1.12 backport had effectively emitted the opposite light
    direction, so AUSM now flips the closed-form vector sign to match Iris'
    position-uniform convention. Follow-up: visual parity testing showed that
    sign flip moved the vector into a different color basis instead of matching
    Iris, so it was reverted. A shader-side candidate probe was added to find
    the correct 1.12 angle mapping before the runtime path is changed again.
    Follow-up: parity probe candidate `36` matched Iris' `sunPosition` /
    `shadowLightPosition` view, so AUSM now derives the celestial vector angle
    from linear 1.12 world time plus a `+270` degree offset instead of
    `World#getCelestialAngle` plus `+90`. This matches the Iris
    `EnvironmentAttributes.SUN_ANGLE` basis more closely for shader uniforms;
    the vanilla-smoothed celestial angle remains used only where separately
    required.
  - Expanded the gbuffers vertex built-in rewrite toward Iris'
    `VanillaTransformer`. In addition to `gl_TextureMatrix[1]`, AUSM now
    rewrites `gl_TextureMatrix[0]`, `gl_NormalMatrix`,
    `gl_ModelViewMatrixInverse`, and `gl_ProjectionMatrixInverse` to the
    corresponding Iris-managed uniforms for gbuffers vertex shaders. This is a
    behaviorally matched 1.12.2 text-transform backport, not a copy of Iris'
    AST transform implementation.
  - Follow-up: reverted the `gl_NormalMatrix` rewrite for gbuffers vertex
    shaders after `EntityLightingProbe` showed entity normals did not follow
    entity rotation. On 1.12.2's fixed-function path, leaving the builtin in
    place is closer to Iris' per-draw transform state than AUSM's
    once-per-camera `iris_NormalMat` uniform.
  - Matched Iris' vanilla entity-shadow suppression behavior. Iris disables
    vanilla entity blob shadows when its shader shadow renderer exists; AUSM
    now cancels `Render#doRenderShadowAndFire` while the shader pipeline has an
    active shadow framebuffer/program. This is a 1.12.2 mixin adaptation of
    Iris' `EntityRenderDispatcher` suppression hook.
  - Began porting Iris' shadow entity submission shape. Iris' `ShadowRenderer`
    extracts visible entities and submits them directly while shadow rendering,
    instead of relying on the normal world entity render loop as the owner of
    shadow-map entity drawing. AUSM now adds a direct 1.12.2
    `RenderManager.renderEntityStatic` pass for shadow entities after the
    existing `RenderGlobal.renderEntities` call. The old call remains for now
    to preserve 1.12 tile-entity/block-entity rendering until that path is
    split cleanly.
  - Matched Iris' `isEyeInWater` intent more closely. Iris sources this from
    the active camera fluid state; AUSM now samples 1.12.2's
    `ActiveRenderInfo.getBlockStateAtEntityViewpoint` for the uniform instead
    of `Entity#isInsideOfMaterial`, which can disagree with the camera point
    used by first-person overlays.
  - Matched Iris' player inclusion in shadow entity submission. Iris'
    shadow renderer includes the camera player when normal shadow entities are
    enabled, skipping only spectator players. AUSM no longer requires the
    separate `shadowPlayer` switch for the view entity when `shadowEntities`
    is enabled; `shadowPlayer` remains a fallback for packs that disable
    general entity shadows but explicitly want player-only shadows.
  - Matched Iris' `eyeAltitude` uniform basis. Iris exposes the current camera
    Y position; AUSM now uses its tracked camera/eye Y instead of the 1.12
    view entity feet position.
  - Temporarily added and then removed an AUSM-authored
    `UnderwaterStateProbe` shaderpack. It exposed `isEyeInWater`,
    raw/smoothed eye brightness, depth, and MakeUp-style underwater
    brightness/absorption terms. The retained engine change from that probe
    work is the underwater eye-brightness correction below; the probe pack and
    temporary MakeUp shader edits were removed after testing.
  - Adjusted the 1.12 eye-brightness backport for underwater camera states.
    Iris exposes block and sky light at the camera eye block, but modern water
    behavior does not attenuate the sky component in the same visible way as
    1.12 water columns. While `isEyeInWater == 1`, AUSM now keeps block light
    local but derives the sky component from the water surface column, so
    shaderpacks using `eyeBrightnessSmooth.y` for underwater tint do not vary
    just because the camera is deeper in water.
  - Added an AUSM-authored compatibility source transform for the exact
    OptiFine/MakeUp fullscreen underwater fog idiom
    `pow(1.001 - linearDepth, 5.0 + 4.0 * WATER_ABSORPTION)`. The transform is
    scoped to composite fragment shaders that also contain the MakeUp-style
    `isEyeInWater == 1`, `WATER_COLOR`, and `mix(blockColor.rgb, ...)`
    signature. It scales that local absorption depth term for the 1.12.2
    backport instead of changing global depth textures or editing the
    shaderpack. This is not Iris code; it is a narrow backport compatibility
    shim based on the temporary underwater probe results.
  - Matched Iris' default render-target clear colors for `colortex1`. Iris'
    `ClearPassCreator` clears `colortex0` to the fog/default color with alpha
    one, `colortex1` to solid white with alpha one, and all later color targets
    to transparent black unless a shaderpack overrides the clear color. AUSM
    previously treated `colortex1` like the later auxiliary targets. This can
    affect packs such as MakeUp that compute exposure from `colortex1` mip
    samples, so the 1.12 backport now clears color target index 1 to white by
    default.
  - Matched Iris' render-target clear timing more closely. Iris creates clear
    passes for both main and alternate textures at frame setup time, then
    composite/deferred passes write to the current target pair and flip without
    clearing or copying draw buffers immediately before each pass. AUSM
    previously cleared and sometimes copied fullscreen draw targets per pass.
    The 1.12 backport now clears both read/main and write/alt textures once at
    frame begin for targets whose `*Clear` directive allows clearing, then lets
    fullscreen passes behave like Iris' `CompositeRenderer` / `BufferFlipper`
    model.
  - Audited the current rendering baseline against Iris after the MakeUp water
    and exposure fixes. The active rendering code is now expected to fall into
    one of three buckets:
    1. direct Iris-shaped implementation, such as program ids/stages,
       render-target clear colors, render-target flipping, sampler aliases,
       common uniforms, phase routing, and extended terrain/entity vertex
       payloads;
    2. behaviorally equivalent 1.12.2 backport, such as text-based source
       transforms standing in for Iris' AST transformers, fixed-function
       matrix/lightmap capture, 1.12 render hook routing, and Cleanroom/OpenGL
       compatibility glue;
    3. narrowly documented compatibility shim required because 1.12 behaves
       differently from modern Iris, currently the MakeUp/OptiFine underwater
       absorption source transform and the underwater eye-brightness surface
       sampling correction.
    Anything outside those buckets should be treated as technical debt before
    new visual parity work. The leftover local `EntityLightingProbe`
    shaderpack was removed from `run/client/shaderpacks`; historical probe
    notes remain in this log only for traceability.
  - Paused world frames now freeze shader frame time, frame counters, smoothed
    uniforms, and previous camera position. This behaviorally follows Iris'
    use of captured/fixed tick state while paused and avoids feeding stale
    movement deltas to temporal or distortion code while a pause GUI is open.
  - The shader list and shader options screens no longer pause the world.
    Iris' shader configuration UI keeps the preview/render loop alive, which
    lets temporal feedback such as MakeUp's exposure buffer settle while the
    screen is open. AUSM keeps this as GUI behavior, not rendering logic, but
    it affects shader feedback passes because Minecraft reports paused frames
    to the pipeline.
  - Shader option profile labels are now derived from the effective option
    values. When the current settings exactly match a declared
    `profile.<name>` override set, the GUI reports that profile; otherwise it
    reports `Custom`. This mirrors Iris' profile behavior and avoids leaving
    the profile as custom after a reset restores a profile-equivalent option
    set.
  - Refined profile GUI state to preserve an explicitly selected real profile
    across Apply and screen reopen. Manual option changes still re-evaluate
    whether the settings match a known profile, but applying a profile no
    longer immediately collapses back to `Custom` because of 1.12/default
    option differences.
  - GUI screen item rendering is no longer routed through the gbuffers item
    program. Iris keeps GUI item stacks in the GUI render path; AUSM now skips
    the `RenderItem` item/glint phase hooks while a GUI screen or deferred HUD
    render is active, and it also preserves the hand phase when first-person
    held items internally call `RenderItem`. This is an Iris-equivalent routing
    fix for inventory item overlays, potion icons, chest item models, and
    enchant glint behavior.
  - Tuned the documented MakeUp/OptiFine underwater absorption compatibility
    shim to use a smaller local depth scale. This remains a 1.12.2 backport
    deviation, scoped only to the exact MakeUp-style composite underwater fog
    expression, and should be revisited if a more Iris-native depth/fog parity
    fix replaces it.
  - Tightened the 1.12 gbuffers atlas binding adapter around Iris' render-target
    sampler rule. Iris exposes gbuffers render-target samplers without taking
    over texture unit 0, leaving the vanilla atlas sampler available for
    atlas-backed draws. AUSM now marks the 1.12 lit-particle phase as
    atlas-backed because `ParticleManager.renderLitParticles` does not bind its
    own texture, and it reasserts the block atlas on unit 0 after binding
    gbuffers colortex/depth samplers. This is a 1.12 fixed-function state
    backport, not a rendering behavior deviation from Iris.
  - Ported Iris' `oldLighting=false` directional-shading behavior into the
    1.12 block model renderer. Iris sets
    `WorldRenderingSettings.disableDirectionalShading` when old lighting is
    disabled, causing vanilla face diffuse shading to become neutral while the
    shaderpack computes its own lighting from normals. AUSM now redirects the
    1.12 `LightUtil.diffuseLight(EnumFacing)` calls in smooth and flat block
    quad rendering to `1.0` under the same condition.
  - Tightened the sampler binding backport so AUSM's runtime texture binds
    update Minecraft's `GlStateManager` cache where 1.12 can safely track it.
    Iris owns sampler state through its rendering backend; the 1.12 backport
    previously used raw `glActiveTexture` / `glBindTexture` for every shader
    sampler, which could leave Minecraft's cached active texture or bound
    texture stale before later atlas-backed hand, portal, or particle draws.
    AUSM now uses `GlStateManager` for texture units 0-7, keeps raw GL binding
    for higher shader units because 1.12's texture-state cache only has eight
    entries, and force-rebinds the block atlas on unit 0 when returning to
    vanilla atlas-backed gbuffers phases.
  - Added `LightingParityProbe`, a local diagnostic shaderpack that does not
    port Iris code but exposes the Iris/OptiFine terrain inputs currently under
    investigation: atlas color, vertex color, sky/block lightmap coordinates,
    view-space normal, sun dot, and MakeUp-style omni strength. It is intended
    for side-by-side AUSM/Iris comparison before touching lighting code.
  - Corrected the direct celestial light vector exposed through `sunPosition`
    and `moonPosition` after `LightingParityProbe` showed AUSM's MakeUp-style
    sun-dot and omni-strength probes were exact inverses of Iris. The shadow
    camera vector `shadowLightPosition` was intentionally left on the existing
    path because it drives shadow-map projection and had separate parity fixes.
    Follow-up: `LightingParityProbe` now includes foliage-specific views for
    MakeUp-style `mc_Entity` classification and foliage direct/omni lighting,
    because correcting the global direct-light vector exposed a foliage lighting
    regression that should be diagnosed against Iris before adding a 1.12 shim.
    The probe also includes a minimal MakeUp-compatible `block.properties` so
    Iris and AUSM both receive real foliage ids instead of the default block id.
    Follow-up: added candidate sky-light transform views to compare AUSM's
    darker `lmcoord.y` against Iris before changing the shared lightmap matrix.
    Follow-up: extended the probe with manual Iris lightmap-matrix
    reconstruction, matrix-delta, MakeUp shadow-distance foliage direct-light
    blending, and MakeUp visible-sky views. This keeps the next decision tied
    to an Iris/MakeUp-observable mismatch instead of applying a 1.12-only
    vertex light workaround.
    Follow-up: added sampled lightmap color and approximate MakeUp real-light
    views after the coordinate/strength probes matched Iris. MakeUp's terrain
    shader defines `USE_BASIC_SH`, so the sampled `lightmap` color is part of
    the actual direct-light path and must be compared separately.
  - Aligned the gbuffers `lightmap` sampler with Iris' level sampler layout.
    Iris reserves world texture units as atlas=0, overlay=1, lightmap=2; AUSM
    still exposed `lightmap` on 1 because that is where Minecraft 1.12's
    fixed-function lightmap client state lives. The 1.12 backport now mirrors
    the vanilla lightmap texture binding from unit 1 onto unit 2 before
    gbuffers draws, while preserving vanilla's client active lightmap unit for
    `gl_MultiTexCoord1`.
    Follow-up: replaced the GL-state readback mirror with an explicit
    `EntityRenderer.lightmapTexture` accessor so unit 2 receives the actual
    1.12 dynamic lightmap texture. This keeps the Iris sampler layout while
    avoiding stale or unrelated unit-1 bindings from Minecraft's fixed-function
    render path.
    Follow-up: added a shader-facing compatibility lightmap texture. Vanilla
    1.12's dynamic lightmap is still used by Minecraft on its fixed-function
    unit, but shaderpacks now sample an AUSM-owned unit-2 copy with a small
    brightness curve to approximate the modern/Iris lightmap content seen by
    packs such as MakeUp. This is an intentional 1.12 backport adapter, not an
    Iris behavior change.
  - Moved custom uniforms closer to Iris' runtime expression model.
    Iris evaluates `uniform.*` and `variable.*` expressions from
    `shaders.properties` every frame and lets those pack-defined uniforms
    override built-in compatibility uniforms with the same names. AUSM
    previously only resolved literal/static expressions at pack load, so
    MakeUp-style formulas using `worldTime`, `if`, `max`, `clamp`, and `fmod`
    silently fell back to AUSM's generic built-ins. The backport now evaluates
    scalar custom variables/uniforms at upload time against current scalar
    built-ins, with support for the arithmetic, comparison, boolean, and common
    math-function subset used by MakeUp's day/night mixer definitions.
  - Corrected shader-facing celestial positions to match Iris'
    `CelestialUniforms`.
    Iris exposes `sunPosition` and `moonPosition` as the celestial position
    transformed by the current gbuffer model-view matrix. AUSM had kept a
    negated direction in this shader-facing path from an earlier 1.12 lighting
    adapter, which inverted MakeUp's `dot(normal, sunPosition)` direct-light
    strength once the pack-defined day/night uniforms were correctly evaluated.
    `shadowLightPosition` remains on the existing shadow-camera adapter path
    because it drives shadow projection separately.
  - Tightened two gbuffers/shadow state adapters exposed by MakeUp Medium.
    The shadow color attachment is now cleared to transparent white instead of
    opaque white, matching the alpha-sensitive colored-shadow convention used
    by OptiFine/Iris shaderpacks where unwritten shadowcolor texels should not
    contribute tint. The Iris-style `ITEM` phase is also marked atlas-backed in
    the 1.12 phase metadata so dropped items and items held by entities rebind
    the block/item atlas after entity textures have been active.
  - Narrowed vanilla entity shadow suppression to the actual 1.12 shadow draw.
    The previous mixin cancelled `Render.doRenderShadowAndFire` entirely when
    shaderpack shadows were active, which also skipped vanilla's entity fire
    overlay. AUSM now redirects only the private `renderShadow` call and leaves
    `renderEntityOnFire` running, matching Iris' separation between shader
    shadows and ordinary entity overlay rendering.
  - Added `ColoredShadowProbe`, a local diagnostic shaderpack for raw shadow
    texture inspection. It renders a basic shadow pass and exposes scene,
    `shadowtex0`, `shadowtex1`, `shadowcolor0.rgb`, and `shadowcolor0.a` views
    so colored-shadow instability can be isolated to shadow-map payloads versus
    shaderpack reconstruction.
    Follow-up: tested restricting shadow color writes to the translucent shadow
    pass, but reverted it after it diverged from Iris' normal shadow pass flow
    and made MakeUp classify the whole world as shadowed. Keep diagnosing the
    colored-shadow payload with probes before changing shared shadow state.
    Follow-up: expanded the probe with projected `shadowtex0`, `shadowtex1`,
    pre/post-translucent delta, and projected `shadowcolor0` views. These are
    AUSM-authored diagnostics only; they are meant to compare the same
    shadow-coordinate reconstruction path shaderpacks use before touching the
    runtime shadow pass again.
    Follow-up: corrected those projected probe modes to use the MakeUp/Iris
    shadow-coordinate convention: diagonal shadow projection, radial shadow
    distortion, and the `z *= 0.2` depth scale used by MakeUp's own
    `shadow_vertex.glsl`. The previous raw clip-space projection produced all
    black compare views and was not a valid runtime diagnostic.
    Follow-up: moved the primary projected probe path from final-pass depth
    reconstruction to gbuffers varyings. MakeUp computes shadow coordinates in
    the terrain/water vertex shader and interpolates them, so the probe now
    writes that coordinate to `colortex1` during gbuffers and uses it for modes
    5-10. The older depth-reconstructed path remains in modes 11-12 only as a
    contrast view.
    Follow-up: aligned the probe shadow vertex shader with the same distorted
    shadow projection used by its gbuffers sampling path. Without this, the
    probe rendered an undistorted shadow map but sampled it with MakeUp-style
    distorted coordinates, making every projected compare view look fully
    shadowed.
    Follow-up: enabled `shadowHardwareFiltering` in the probe GLSL source. AUSM
    currently scans this as a shader-source `const bool` like OptiFine/Iris
    packs, not as a `shaders.properties` assignment. The probe also gained
    raw-depth contrast modes 13 and 14, compiled separately from
    `sampler2DShadow`, so compare-state failures can be separated from empty
    shadow depth textures.
    Follow-up: added fixed-reference compare sweeps in modes 15 and 16. They
    sample `shadowtex0` / `shadowtex1` at the projected XY with reference depths
    0.0, 0.5, and 1.0, which distinguishes bad projected Z from missing or
    unconfigured shadow compare samplers.
    Follow-up: added gbuffers-side compare modes 17-19. These sample
    `shadowtex0` / `shadowtex1` directly from the terrain and water fragment
    shaders using the interpolated shadow coordinates, matching MakeUp's actual
    stage usage and separating gbuffers shadow binding from final-pass shadow
    binding.
    Follow-up: changed final presentation for modes 17-19 to display the
    gbuffers result directly instead of the probe's default quadrant layout.
    Follow-up: added temporary runtime audits for `ColoredShadowProbe`. The
    sampler audit verifies AUSM binds `shadowtex0`, `shadowtex1`, and
    `shadowcolor0` to the Iris/OptiFine-compatible shadow texture units with
    hardware compare state enabled. The new depth audit reads the shadow FBO
    after the opaque/entity snapshot and again after translucent shadow
    rendering, so compare failures can be separated from empty or badly written
    shadow depth contents.
    Follow-up: reset the shadow sampler/depth audits per pipeline
    initialization and log the first few shadow terrain layer return counts.
    This separates a blank shadow map caused by no rendered chunk layers from
    one caused by projection, depth, or texture compare state.
    Follow-up: pipeline sampler bindings now bypass Minecraft's
    `GlStateManager.bindTexture` cache and call `glActiveTexture` /
    `glBindTexture` directly. Reloads can recreate FBO textures with the same
    numeric GL id while Minecraft's per-unit cache still believes that id is
    bound; direct binding matches Iris' explicit sampler binding model and
    fixes stale shadow texture units after shader reload.
    Follow-up: expanded `ColoredShadowProbe` with modes 20-22 to display
    gbuffers-side shadow coordinates, in-bounds state, and distance from the
    shadow center. The shadow depth audit now also samples a dense local window
    around the center, since a coarse 16x16 grid missed the narrow populated
    region visible at the center pixel.
    Follow-up: added mode 23 to `ColoredShadowProbe`. In that mode the shadow
    pass writes normalized shadow-map pixel coordinates and depth into
    `shadowcolor0`, then terrain/water sample it through their interpolated
    MakeUp-style receiver shadow coordinates. This isolates whether AUSM is
    sampling the populated part of the shadow map before changing shared shadow
    camera or compare logic.
    Follow-up: forced mode 23 to present an opaque sampled payload and added
    mode 24 as a red/green/blue coverage mask. Red means the receiver sampled
    the clear shadow-color value, green means it sampled geometry-written
    shadow-color payload, and blue means the receiver XY coordinate is inside
    the shadow texture range.
    Follow-up: made `ColoredShadowProbe`'s shadow fragment shader always write
    the coordinate payload. This removes shader-option propagation as a
    variable while testing whether shadow terrain writes ever reach
    `shadowcolor0`.
    Follow-up: aligned two shadow backport details with OptiFine's 1.12 shadow
    path: `shadowcolor0` now clears to alpha 1 instead of alpha 0, and the
    shadow camera uses base interpolated entity position, an orthographic
    `0.05..256` depth range, and the `z=-100` modelview translation used before
    the light rotations. The probe payload now writes alpha 0.25 so mode 24 can
    still distinguish geometry-written shadow color from the OptiFine-style
    clear value.
    Follow-up: added a `RenderGlobal` accessor and mark
    `displayListEntitiesDirty` before shadow `setupTerrain`. Vanilla 1.12
    reuses the previous terrain render list unless that flag is set; Iris owns
    a separate shadow terrain pass, while this backport must force a distinct
    shadow render-list rebuild before feeding the shadow camera.
    Follow-up: added `PROBE_SHADOW_VERTEX_MODE` to `ColoredShadowProbe`. Mode 0
    keeps the pack-style shadow vertex path, mode 1 bypasses
    `shadowModelViewInverse`/`shadowProjectionInverse` and uses `ftransform()`
    directly, and mode 2 also disables the MakeUp-style shadow distortion. This
    separates inverse-uniform failures from shadow-camera coverage failures.
    Follow-up: global pipeline settings now accept ordinary
    `shaders.properties` values in addition to scanned shader options and
    OptiFine comment directives. This matches Iris/OptiFine pack semantics for
    settings such as `shadowMapResolution`, `shadowDistance`,
    `shadowIntervalSize`, `sunPathRotation`, shadow half-lives, and shadow
    polygon offset. The immediate reason was `ColoredShadowProbe` declaring
    `shadowMapResolution=512` and `shadowDistance=75.0` while AUSM still logged
    the fallback `1024` / `128.0`, which made shadow coverage tests run against
    the wrong pack configuration.
    Follow-up: during the 1.12 shadow pass, AUSM now temporarily disables
    `Minecraft.renderChunksMany` while `RenderGlobal.setupTerrain` builds and
    renders the shadow terrain list. Iris disables `client.smartCull` in
    `ShadowRenderer.renderShadows` for the same reason: normal camera chunk
    occlusion is not valid for light-space shadow rendering and can leave holes
    or stale chunk visibility in the shadow map. The previous value is restored
    immediately after the shadow pass.
    Follow-up: tightened `ColoredShadowProbe` mode 24 so out-of-range receiver
    coordinates no longer count clamped edge samples as shadow payload. This is
    a diagnostic-only change; valid written shadowcolor coverage remains teal,
    in-range clear samples remain magenta, and out-of-range terrain now shows
    as red instead of false green.
    Follow-up: added Iris-compatible shadowcolor sampler directives to the
    1.12 render-target settings path. AUSM now scans
    `generateShadowColorMipmap`, `shadowcolor0Mipmap`,
    `shadowColor0Mipmap`, `shadowcolor0Nearest`, `shadowColor0Nearest`, and
    `shadowColor0MinMagNearest`, applies the requested shadowcolor filtering at
    texture allocation time, and generates `shadowcolor0` mipmaps after the
    shadow terrain/translucent pass when requested. This mirrors Iris'
    `PackShadowDirectives` color sampling settings for packs that use colored
    shadows.
    Follow-up: added Iris-compatible shadow clear directives for
    `shadowcolor0Clear` and `shadowcolor1Clear`. MakeUp declares these false
    while colored shadows are active; AUSM previously cleared shadowcolor every
    shadow pass regardless. The FBO is still initialized to the default clear
    values when created, but per-frame shadowcolor clears now respect the pack
    directives. Iris still clears shadow depth every shadow frame before
    rendering shadows, so AUSM deliberately ignores the MakeUp
    `shadowtex0Clear` / `shadowtex1Clear` constants in the Iris-port path.
    Follow-up: added temporary low-frequency MakeUp shadow audits. Every 120
    pipeline frames while a MakeUp pack is active, AUSM logs shadow render
    layer counts plus depth and shadowcolor summaries after the opaque/entity
    snapshot and after translucent shadow rendering. This is diagnostic-only
    and intended to identify whether the remaining colored-shadow instability
    comes from `shadowtex0`, `shadowtex1`, or `shadowcolor0` changing.
    Follow-up: fixed AUSM's Iris-style custom uniform expression evaluator so
    logical `&&` / `||` always consume the right-hand expression instead of
    Java-short-circuiting the parser. MakeUp's `dayNightMix` chain was being
    deferred every frame because expressions such as
    `worldTime >= 12485 && worldTime < 13085` left unparsed tokens whenever the
    first comparison determined the boolean result. The evaluator also now
    accepts OptiFine-style chained `if(condition, value, ..., fallback)` plus
    `log` and `atan`, covering more of MakeUp's `shaders.properties` custom
    uniform set.
    Follow-up: aligned shadow depth texture component swizzling with Iris'
    `ShadowRenderer.configureDepthSampler` behavior when
    `GL_ARB_texture_swizzle` is available. AUSM now exposes shadow depth as
    red/red/red/one for legacy shader code that samples depth through ordinary
    `sampler2D` channels, while preserving the existing compare-mode path for
    `sampler2DShadow`.
    Follow-up: added a built-in `fovYInverse` uniform computed from the current
    gbuffer projection matrix. MakeUp declares this through a custom uniform
    expression using `gbufferProjection.1.1`; the built-in keeps MakeUp
    AO/blur code supplied even if that custom expression is deferred.
    Follow-up: reasserted shadow terrain GL state immediately before the
    translucent shadow terrain layer. MakeUp's colored-shadow branch expects
    final `shadowtex0` to include translucent casters while pre-translucent
    `shadowtex1` excludes them; the audit logs showed `shadowcolor0` changing
    after translucent rendering while sampled depth summaries stayed nearly
    identical. AUSM now forces depth writes, color writes, alpha test, no blend,
    and no cull again directly before rendering `BlockRenderLayer.TRANSLUCENT`
    in the shadow pass.
    Follow-up: added a low-frequency MakeUp-only
    `[ShadowDepthDeltaAudit]` that compares final shadow depth against the
    pre-translucent snapshot texture. This directly reports whether
    translucent shadow rendering made `shadowtex0` diverge from `shadowtex1`.
    Follow-up: added OptiFine-style scalar matrix component names to the custom
    uniform input map, such as `gbufferProjection.1.1`, and allowed dotted
    identifiers in the scalar expression parser. This removes the remaining
    MakeUp `fovYInverse` custom uniform deferral while retaining the built-in
    fallback above.
    Follow-up: fixed CPU-side shader constant resolution for settings such as
    `shadowMapResolution` and `shadowDistance`. AUSM's option scanner records
    constants before GLSL preprocessing, so MakeUp's first inactive branch
    (`shadowMapResolution = 300`) was being used for the framebuffer even when
    the active options selected `SHADOW_DISTANCE_SLIDER=1` and
    `SHADOW_QTY_SLIDER=2` (`shadowMapResolution = 840`). The pipeline now scans
    included shader sources with a small active-branch evaluator before falling
    back to the raw discovered option value, so CPU framebuffer sizing follows
    the same option-conditioned constants as the compiled shader.
    Follow-up: fixed shadow projection matrix inversion for larger orthographic
    half-plane values. The MakeUp medium branch uses `shadowDistance = 105.0`;
    its valid orthographic projection has a determinant below the previous
    `1.0E-6` singularity cutoff, causing AUSM to upload identity
    `shadowProjectionInverse` and leaving the shadow map empty. The cutoff is
    now small enough for valid shadow orthographic matrices while still guarding
    genuinely singular matrices.
    Follow-up: added producer-side MakeUp shadow diagnostics after the map
    became completely clear at the active 840x840 / 105.0 settings. AUSM now
    logs `[ShadowMatrixAudit]` for the light projection/model-view and their
    inverses, plus `[ShadowProducerAudit]` terrain layer counts before the
    depth snapshot. This is diagnostic-only and should distinguish "no terrain
    submitted" from "terrain submitted but clipped or depth writes suppressed".
    Follow-up: added a 1.12 adapter fallback for empty shadow terrain
    submission. If vanilla `RenderGlobal.renderBlockLayer` returns zero during
    the shadow pass, AUSM now submits non-empty compiled chunks directly from
    the active `ViewFrustum` into the existing `ChunkRenderContainer`, bounded
    around the camera by the active shadow distance. This keeps the normal
    vanilla terrain traversal when it succeeds, but gives the shadow pass an
    Iris-like independent chunk submission path when the vanilla render-info
    list is empty.
    Follow-up: routed shadow terrain layers through their matching Iris-style
    phases (`TERRAIN_SOLID`, `TERRAIN_CUTOUT_MIPPED`, `TERRAIN_CUTOUT`,
    `TERRAIN_TRANSLUCENT`) instead of drawing all opaque shadow terrain under
    `TERRAIN_CUTOUT`. Added `[ShadowLayerAudit]` so MakeUp logs show the
    bound shadow pass/program and depth-write state per terrain layer.
    Follow-up: corrected the phase map so `TERRAIN_SOLID` targets
    `SHADOW_SOLID`, not `SHADOW_CUTOUT`. MakeUp currently falls back to the
    base `shadow` program for both, but the wrong pass was visible in
    `[ShadowLayerAudit]` and would break packs that provide separate
    `shadow_solid` handling.
    Follow-up: added OptiFine-compatible singular gbuffer program defines.
    AUSM already emitted Iris-style names such as `GBUFFERS_TERRAIN`, but
    MakeUp tests names such as `GBUFFER_TERRAIN`, `GBUFFER_ENTITIES`, and
    `GBUFFER_ENTITY_GLOW`. The preprocessor now emits both forms for
    `gbuffers_*` passes, with aliases for OptiFine's non-mechanical names
    (`GBUFFER_DAMAGE`, `GBUFFER_ENTITY_GLOW`). This restores MakeUp receiver
    branches that were silently compiled out despite valid shadow maps.
    Follow-up: changed the shadow terrain depth function from `GL_LESS` to
    `GL_LEQUAL`, matching OptiFine's shadow pass setup. MakeUp's colored
    shadows depend on tiny final-vs-snapshot shadow depth differences from
    translucent casters; strict less rejects equal/coplanar translucent
    fragments and made colored shadows flicker between colored, opaque, and
    absent states.
    Follow-up: delayed shadow framebuffer clear until after shadow terrain
    setup and added a compiled-chunk preflight for the 1.12 adapter path. When
    the shadow pass has no compiled terrain candidates, AUSM now skips the pass
    instead of clearing a previously populated shadow map; this targets the
    observed frame-0/zero-count MakeUp flashes to "no shadow" while keeping the
    initial blank texture behavior before the first populated pass.
    Follow-up: disabled polygon offset only for the translucent shadow terrain
    layer and added the offset state to `[ShadowLayerAudit]`. Opaque/cutout
    terrain still uses the pack's shadow polygon offset, but translucent
    colored casters render at their un-offset depth so final `shadowtex0` can
    diverge from the pre-translucent `shadowtex1` snapshot more reliably, which
    is the MakeUp colored-shadow branch condition.
    Follow-up: made the post-snapshot translucent shadow terrain layer an
    explicit 1.12 colored-shadow overlay pass: alpha blending is enabled and
    the depth function is temporarily `GL_ALWAYS` only while
    `TERRAIN_TRANSLUCENT` renders. Opaque/cutout terrain and the
    pre-translucent `shadowtex1` snapshot remain unchanged. This backport
    deviation targets the observed MakeUp state where translucent chunks were
    submitted after the snapshot but neither `shadowtex0` nor `shadowcolor0`
    changed, leaving the colored-shadow branch with no final-vs-snapshot
    difference to detect.
    Follow-up: extended the local `ColoredShadowProbe` diagnostic pack with
    receiver-side MakeUp colored-shadow modes. `DEBUG_VIEW=25` shows the gate
    inputs as RGB (`in bounds`, `shadowtex0 != shadowtex1`, `shadowcolor0`
    payload), `26` shows a simplified MakeUp colored-shadow result, `27`
    shows `shadowtex0`/`shadowtex1` compare values plus amplified difference,
    `28` shows sampled `shadowcolor0`, and `29` shows shadow UV coverage. This
    moves the next diagnosis out of low-frequency logs and into an in-game
    visual probe.
    Follow-up: added `PROBE_SHADOW_PAYLOAD_MODE` to `ColoredShadowProbe`.
    The shadow pass can now write coordinate/depth payload, atlas texture
    color, atlas texture times vertex tint, or raw vertex tint into
    `shadowcolor0`. `DEBUG_VIEW=26` now applies MakeUp's single-sample
    colored-shadow formula to that payload, so glass color/tint issues can be
    separated from the already-working `shadowtex0` versus `shadowtex1` gate.
    Follow-up: reasserted the block atlas binding after shadow program
    resource binding for block-atlas shadow phases. Gbuffers already restored
    the atlas around render-target sampler binding, but the shadow-stage path
    only bound it once before all shadow terrain layers. The color-payload
    probe showed valid glass colors sometimes changing into vertical bands,
    consistent with `tex` on unit 0 drifting away from the atlas while
    `shadowcolor0` was being written.
    Follow-up: disabled the temporary shadow audit logging and sampler audit
    logging used during colored-shadow diagnosis. Those audits include GL
    readbacks/state queries and produced visible client stalls once the feature
    was stable. The diagnostic methods remain as no-op placeholders for future
    local debugging.
    Follow-up: kept the post-snapshot translucent shadow depth override but
    stopped alpha-blending the translucent shadow color payload over the
    previous `shadowcolor0` contents. With `GL_ALWAYS`, translucent casters can
    still open the MakeUp colored-shadow gate, while direct payload writes avoid
    washing glass tint toward the opaque/clear color already in the attachment.
    Follow-up: fixed MakeUp Extreme+ DOF activation by supporting OptiFine-style
    commented toggle options such as `// #define DOF`. The option scanner now
    discovers them as disabled toggles, the shader preprocessor can turn them
    into live `#define`s when profiles enable them, and the render-target
    metadata scanner now respects active `#ifdef/#else` branches while still
    reading OptiFine metadata inside block comments. This should let MakeUp's
    DOF path allocate `colortex1`/`colortex3` as `RGBA16F` for alpha depth
    payloads and enable the `colortex1` mipmaps sampled by `noisedBlur`.
    Follow-up: investigated a MakeUp `VOL_LIGHT=2` leak through opaque walls.
    A first compatibility transform that made the volumetric endpoint prefer
    `depthtex1` caused black composite output when volumetric lighting was
    enabled, so it was removed. The next diagnosis should visually inspect
    `depthtex0`/`depthtex1` at composite time before changing the raymarch
    endpoint again.
    Follow-up: repurposed MakeUp's local `DEBUG_MODE` final view as a visual
    depth probe. With `DEBUG_MODE` enabled, bottom-left shows `depthtex0`,
    top-left shows `depthtex1`, bottom-right shows `shadowtex1`, and top-right
    shows an amplified absolute `depthtex0-depthtex1` difference. This is a
    shaderpack-local diagnostic to inspect the volumetric wall leak without
    adding runtime readback logs.
    Follow-up: patched MakeUp's local `VOL_LIGHT=2` composite path for testing.
    The visual probe showed `depthtex0` was effectively far/cleared by final
    while `depthtex1` retained pre-hand world depth. The composite pass now
    declares `depthtex1`, caps the volumetric ray distance with valid snapshot
    depth, and changes the final volumetric mix factor so zero shadow samples
    do not darken the entire scene. This is currently a shaderpack-local test
    patch, not a general source transform.
    Follow-up: promoted the working MakeUp `VOL_LIGHT=2` test patch into
    `MakeUpVolumetricLightTransformStage` and reverted the temporary edits in
    MakeUp's shader files. The transform matches MakeUp's composite
    volumetric-light path when `VOL_LIGHT=2`, injects `depthtex1` into that
    active block, caps ray distance using valid snapshot depth, and removes
    the half-strength base mix that caused zero volumetric samples to darken
    the whole scene. Java compile passes; runtime reload should validate the
    transformed GLSL path.
    Follow-up: made `MakeUpVolumetricLightTransformStage` regex-based instead
    of exact-string based and added a one-line shader reload marker:
    `[ShaderTransform] Applied MakeUp VOL_LIGHT=2 depth compatibility to ...`.
    If volumetric still leaks and the marker is absent, the transform did not
    match the compiled source. If the marker is present, the remaining issue is
    the runtime depth cap rather than transform activation.
    Follow-up: added first-pass BSL v10 multi-colored blocklight compatibility.
    BSL gates its voxel light propagation through dimension-qualified program
    expressions such as `program.world0/shadowcomp.enabled` and ships the
    compute source as `world0/shadowcomp.csh`. AUSM now preserves enabled
    expressions for Iris program arrays, evaluates them before loading compute
    array entries, and searches the current dimension's compute path before
    root compute paths. This should let BSL's `shadowcomp` compute program and
    custom image directives activate when `MULTICOLORED_BLOCKLIGHT=true`
    instead of partially enabling the shader code without the voxel propagation
    pass. Java compile passes; runtime validation should confirm
    `compute=true`, `images=true`, and a compiled `world0/shadowcomp.csh`.
    Follow-up: fixed custom image binding for shaderpacks that use the same
    image texture as both an image uniform and a sampler, as BSL does with
    `voxelimg`/`voxeltex` and `lightimg*`/`lighttex*`. AUSM now uploads image
    uniforms to image units, binds the backing textures to high sampler units,
    uploads sampler uniforms to those units, and restores the default texture
    unit afterwards. This avoids stealing texture unit 0 from the block atlas
    when custom images are active and lets compute shaders actually sample the
    custom image textures they update.
    Follow-up: fixed the next BSL MCBL reload failure. Once MCBL was enabled,
    BSL injected integer modulo expressions into GLSL 1.20 gbuffers programs
    (`heldItemId % 100`, `frameCounter % 2`), which Mesa rejects and caused
    the terrain/textured programs to be disabled, producing white/fallback
    blocks. Added a compatibility-profile transform that rewrites simple
    identifier modulo integer literals to `int(mod(float(...), ...))`.
    Also removed the compute-source fallback to `program/*.glsl`; only actual
    `.csh` files are treated as compute programs, so normal deferred/composite
    GLSL files are no longer compiled as bogus compute shaders. Java compile
    passes; runtime reload should confirm gbuffers terrain/textured compile.
    Follow-up: fixed BSL `shadowcomp` dispatch sizing. BSL's dimension compute
    files are thin `.csh` wrappers that include `program/shadowcomp.glsl`, so
    parsing `workGroups` from the raw wrapper produced no directive and AUSM
    dispatched only the fallback `1x1x1` group. Compute programs now parse
    `workGroups`/`workGroupsRender` from the fully preprocessed source after
    includes and option branches are resolved, with the raw source metadata only
    as a fallback. Runtime logs now include the parsed work group dimensions
    beside the successful compute compile line.
    Follow-up: added the missing `framemod2` uniform and a pre-dispatch shader
    memory barrier for compute programs. BSL `shadowcomp` alternates between
    `lightimg0` and `lightimg1` using `framemod2`, while its terrain/shadow
    code samples with `frameCounter % 2`; leaving `framemod2` unset could keep
    one side of the ping-pong light volume stale. The barrier is needed because
    BSL writes `voxelimg` from the shadow vertex pass and immediately samples
    the same backing texture as `voxeltex` in compute.
    Follow-up: added a non-compute image-store compatibility transform after
    the BSL voxel audit showed `voxelimg` remained completely zero after the
    shadow terrain pass. BSL writes `voxelimg` from `shadow.vsh`, but that
    program is declared as GLSL 130 and its image uniforms do not include
    explicit layout formats. AUSM now upgrades image-store shader sources to
    `#version 430 compatibility` and adds the BSL image layouts
    (`r8ui voxelimg`, `rgba16f lightimg0/1`) before compilation.
    Follow-up: fixed `block.properties` conditional parsing for `#elif`.
    BSL's 1.8-1.12 mapping sits under `#elif MC_VERSION >= 10800`; AUSM was
    treating `#elif` like `#else`, then also parsing the later 1.7 fallback.
    The fallback overwrote the temporary colored emissive IDs with unsuffixed
    IDs like `15000`, so BSL saw `mc_Entity.x % 100 == 0` and skipped every
    real voxel write. Conditional block parsing now tracks whether a previous
    branch has already matched, and the 1.12-era `MC_VERSION >= 10800`
    condition now evaluates true.
    Follow-up: compute shader sources now pass through the shader transform
    pipeline too. The stages are shader-type guarded, but this lets the
    image-store compatibility transform add explicit layouts to BSL's
    `shadowcomp.csh` `lightimg0/1` image uniforms instead of only fixing the
    vertex/fragment shader sources.
    Follow-up: fixed compute `workGroups` metadata parsing for shader sources
    with conditional branches. BSL declares many `workGroups` constants under
    `#ifdef MCBL_HALF_HEIGHT` and `#if MCBL_DISTANCE == ...`; AUSM's regex
    parser was grabbing the first inactive branch (`16,8,16`) even when the
    active 256-distance full-height branch should dispatch `32,32,32`. Compute
    metadata parsing now filters inactive `#if/#elif/#else/#endif` branches
    before matching `workGroups`.
    Follow-up: hardened generated tangent-space attributes after Pastel showed
    triangle-local black artifacts in advanced material paths. Degenerate UVs
    or geometry could produce zero/non-finite tangents, and packs such as
    Pastel normalize `at_tangent` directly before building a TBN matrix. Tangent
    generation now rejects invalid denominators, orthogonalizes against the
    normal, clamps packed SNORM bytes, and falls back to a stable orthonormal
    tangent when needed.
    Follow-up: bound neutral `normals` and `specular` fallback textures during
    gbuffers passes. Pastel's `ADVANCED_MATERIALS`/Reflective Surfaces path
    samples those atlas samplers directly; AUSM exposed the uniforms but left
    them stale or overwritten by shadow texture bindings. The fallback uses a
    flat normal `(0.5, 0.5, 1.0, 1.0)` and black specular data, matching the
    safe no-PBR-resourcepack behavior until real normal/specular atlas support
    is implemented.
    Follow-up: kept Pastel's reflective entity shaders from reading stale
    advanced-material attributes. Many 1.12 entity renderers start from
    `POSITION_TEX_COLOR_NORMAL`, whose incremental write order does not match
    AUSM's current pipeline entity format. Instead of force-swapping that
    layout, entity-like gbuffers vertex shaders now fall back to
    `mc_midTexCoord = gl_MultiTexCoord0` and a neutral tangent. This preserves
    stable entity rendering until a dedicated extended entity layout exists for
    each vanilla source order.

Future entries should document:

- The Iris reference class or subsystem consulted.
- Whether code was copied, adapted, or only behaviorally matched.
- Any 1.12.2 backport deviations.

Temporary local shaderpack test patches:

- BSL_v10.1.3 `block.properties`: the 1.8-1.12 branch normally maps vanilla
  emissive blocks to `*00`, which gives BSL's multi-colored blocklight voxel
  path no colored emission data. For MCBL pipeline testing only, several
  1.12 blocks were remapped with color data:
  `lit_pumpkin -> 15002`, `ender_chest -> 15010`, `end_rod -> 15011`,
  redstone emitters/torch -> `15001`, `magma -> 15103`,
  `glowstone/lit_redstone_lamp -> 15105`, `sea_lantern -> 15109`,
  `lava -> 15302`, `fire -> 15402`, `beacon -> 15519`.
  Remove this patch after the MCBL test pass.
