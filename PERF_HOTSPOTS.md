# AUSM Runtime Hotspots

## 2026-06-29 GPOM/DH Idle Lag Sample

Source sample: GPOM-side JVM/JFR capture in `/tmp/gpom-hotspots-20260629-094524` against the running MeatballCraft client.
The player was mostly idle, but the JVM still used several cores.

Main runtime signals:

- Client thread was near one full core in `RenderGlobal` / BetterPortals render flow.
- Integrated server thread was frequently busy because Distant Horizons requested chunks through `InternalServerGenerator`.
- JFR GC pauses were not the direct stutter source: total ZGC pause time was about `3.54 ms` over the 20 second recording.
- Allocation pressure was dominated by render/chunk work: `BufferBuilderMixin.ausm$expandVanillaQuadData` accounted for roughly `60%` of sampled allocation pressure, mostly `int[]`; `Entity.getBrightness`/`MutableBlockPos` accounted for roughly `33%`.

AUSM-relevant hotspots found in local source:

- `src/main/java/com/l/ausm/impl/mixin/pipeline/BufferBuilderMixin.java`
  - `ausm$expandVanillaQuadData` allocates a fresh `int[] expandedData` for each expanded vanilla quad batch, then copies it into the raw buffer.
  - JFR top allocation site: `net.minecraft.client.renderer.BufferBuilder.handler$...$ausm$expandVanillaQuadData`.
  - Candidate optimization: avoid per-call temporary arrays by writing expanded vertices directly into `rawIntBuffer` after ensuring capacity, or reuse a bounded thread-local scratch buffer. Be careful with nested render calls and chunk worker threads.

- `src/main/java/com/l/ausm/impl/pipeline/compat/NothiriumBypass.java`
  - `shouldBypass()` calls `shouldUseVanillaRenderGlobalForCurrentPass()`, which performs ThreadLocal reads and BetterPortals/Nothirium state checks inside hot render paths.
  - JFR/thread dump hit `ThreadLocal$ThreadLocalMap.getEntryAfterMiss` from `NothiriumBypass.shouldUseVanillaRenderGlobalForCurrentPass` during `RenderGlobal.renderBlockLayer`.
  - Candidate optimization: cache the bypass decision once per world render phase/pass/frame in `PipelineContext`, and have injected hot paths read a primitive cached flag instead of repeatedly walking ThreadLocals and compat checks.

- `src/main/java/com/l/ausm/impl/pipeline/vertex/BlockRenderContext.java`
  - Many per-block/per-quad render fields are stored as individual `ThreadLocal` values.
  - These are convenient for compatibility mixins, but repeated access can show up in chunk rebuild/render hot paths. If optimizing, consider a single thread-local mutable context object with primitive fields to reduce ThreadLocal map lookups and boxing.

- `src/main/java/com/l/ausm/impl/pipeline/PipelineContext.java`
  - `registryName(IBlockState)` is cheap by itself but appears repeatedly in block-layer/bloom/fallback predicates.
  - JFR sampled `PipelineContext.registryName` and related checks during heavy chunk rebuild/render work.
  - Candidate optimization: avoid repeated registry/path/lowercase checks inside per-quad/per-layer loops; cache per-block or per-state classification for bloom/emissive/fallback decisions.

- `src/main/java/com/l/ausm/impl/mixin/compat/BlockcrafteryBakedModelEditableMixin.java`
  - Thread dumps hit Blockcraftery `BakedModelEditable.getQuads` under AUSM/Forge chunk rebuilds.
  - The mixin performs inherited bloom source checks and optional diagnostic string construction paths around Blockcraftery models.
  - Candidate optimization: ensure diagnostics are fully gated before state/string work, and cache Blockcraftery bloom/fallback classification by effective inherited block state where safe.

Important non-AUSM driver:

- Distant Horizons was configured with `numberOfThreads=20`, `threadRunTimeRatio=1.0`, `enableDistantGeneration=true`, and `maxHorizontalResolution=BLOCK` during the sample.
- That caused DH to continue LOD generation/loading while idle, feeding both integrated server chunk work and AUSM/Forge render rebuild work.
- GPOM live config was tuned after the sample to reduce pressure: `numberOfThreads=4`, `threadRunTimeRatio=0.25`, `distantGeneratorMode=PRE_EXISTING_ONLY`, `enableDistantGeneration=false`, `enableMultiLayerClouds=false`, and `maxHorizontalResolution=TWO_BLOCKS`.

Do not treat the AUSM hotspots as proof of an AUSM bug by themselves. The strongest causal driver in this sample was DH producing work while idle; AUSM is where much of that render work becomes expensive and allocates.
