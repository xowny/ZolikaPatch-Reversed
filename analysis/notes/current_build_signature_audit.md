# Current Build Signature Audit

Target executable:

- `GTAIV.exe`
- File version: `1.2.0.59`

Audit inputs:

- Legacy patch signatures recovered from `ZolikaPatch.asi`
- Initial feature set:
  - vehicle limit patches
  - teleport protection
  - skip intro
  - skip menu
  - episodic vehicle support

Raw audit output:

- `latest_gtaiv_signature_audit.tsv`

## Result Summary

No direct match on current build:

- `IncreaseVehicleModelLimit`
- `TeleportProtection`
- `SkipIntro`
- `SkipMenuPrimary`
- `EpisodicVehicleSupport_A`
- `EpisodicVehicleSupport_B`
- `EpisodicVehicleSupport_C`
- `EpisodicVehicleSupport_D`
- `EpisodicVehicleSupport_E`

Single strong match on current build:

- `IncreaseVehicleStructLimit` -> `00a7b107`

Fallback-only matches on current build:

- `SkipMenuFallbackA` -> `005a636c`
- `SkipMenuFallbackB` -> `0094b106`

## Hit Triage

Resolved hit sites:

- `00a7b107` is inside `FUN_00a7b080`
- `005a636c` is inside `FUN_005a3980`
- `0094b106` is inside `FUN_0094ad40`

Assembly context:

- `latest_gtaiv_hit_asm.txt`

Relevant decompilation:

- `latest_gtaiv_hit_func_decompile.txt`

Interpretation:

- `IncreaseVehicleStructLimit`
  - This one survives triage.
  - The match is inside a function that decompiles to:
    - load `vehshare`
    - allocate/init a structure named `VehicleStruct`
    - call `FUN_00c6c5f0(0x32, "VehicleStruct", 0x20c)`
  - This is a strong semantic carry-over from the legacy build.
  - It is currently the best first port candidate because the old patch also changes the immediate `0x32` count at this exact pattern shape.
- `SkipMenuFallbackA` and `SkipMenuFallbackB`
  - `SkipMenuFallbackA` lands inside a large UI/menu state function, so it is not pure garbage.
  - But both fallback matches are still too weak to use as patch locations without deeper semantic RE.
  - Treat them as investigation leads, not patch-ready sites.

## Conclusion

The old ZolikaPatch binary patch strategy does not carry forward cleanly to GTA IV `1.2.0.59`.

Practical implication:

- this is not a "refresh a few signatures" job
- this is a feature-by-feature semantic port

That matches the binary design we already recovered:

1. parse config
2. identify target code/data in the game
3. install feature-specific runtime patches
4. optionally register menu options

For the open version, the right workflow is:

1. choose one feature
2. identify what gameplay/system behavior it changes in the old build
3. find the corresponding code path in the current build
4. reimplement the patch in open source against the current executable layout

## Recommended Porting Order

Start with features that are small and easy to validate, e.g:

- `IncreaseVehicleModelLimit`
- `IncreaseVehicleStructLimit`

Defer broader multi-site patch sets until later:

- `EpisodicVehicleSupport`
- large graphics and streaming fixes
- grouped "misc" toggles that bundle multiple writes

## Reflection Follow-up

- A quick `GTAIV.exe` string sweep surfaced plausible render/probe names such as `AsyncProbeMgr`, `LightOcclusionTex`, and `lightmap`.
- The first `AsyncProbeMgr` xref and nearby-vtable pass did not produce a usable code path.
- `LightOcclusionTex` did produce one concrete current-build render landmark:
  - string xref `0065d443` lands in `0x0065d2b0`, now named `InitializeIntervalShadowAndOcclusionParameterIndices`
  - that function walks a parameter table and resolves indices for:
    - `IntervalShadowHeightShift`
    - `IntervalShadowShadowSoftness`
    - `IntervalShadowHeightMapProjectionX`
    - `IntervalShadowHeightMapProjectionY`
    - `AmbientOccTex`
    - `IntervalTex`
    - `LightOcclusionTex`
    - `PRTOccTex`
    - `PRTon`
    - `IntervalShadowsOn`
    - `SunLightIntensity`
  - useful conclusion:
    - this is a real render/shader setup landmark in the modern executable
    - but it looks like interval-shadow / occlusion parameter registration, not the direct semantic target of the old high-quality-reflections hook
- A broader `rage__*` subsystem-name sweep did produce the first strong reflection-specific anchor:
  - `0x0062ca10`, now named `InitializeProceduralReflectionSubsystemRegistration`, lazily registers a subsystem named `rage__ProceduralReflection`
  - the registration publishes into the zero-initialized global storage slot `DAT_018b74bc`
  - the published module uses a factory callback thunk at `0x0062e410`
  - raw assembly at `0x0062e410` shows the thunk allocating `0x60` bytes and tail-jumping into `0x0062d6d0`
  - `0x0062d6d0`, now named `InitializeProceduralReflectionDefaults`, writes `rage::ProceduralReflection::vftable` and seeds the object's default float fields
  - a fresh runtime probe now exposes the published record layout directly from `0x0062ca10`:
    - `+0x04` name pointer -> `"rage__ProceduralReflection"`
    - `+0x10` runtime object size -> `0x60`
    - `+0x20` zero
    - `+0x24` factory callback -> `0x0062e410`
    - `+0x28` zero
    - `+0x2c` shared class-ops / type record -> `DAT_0043ead0`
    - `+0x08/+0x0c` are cleared before publish
    - `+0x1c` is masked down and `+0x1e` is cleared before finalize/publish
  - a sibling comparison now sharpens what that means:
    - `rage__ProceduralTextureRenderTargetDef` and `rage__IntervalShadows` publish through the same `0x0041d240 -> 0x00409110` sink, but they also seed extra field-offset metadata before publish
    - `rage__ProceduralReflection` does not seed that extra metadata in its registration path
    - practical reading:
      - the reflection definition record is comparatively thin
      - the missing semantics are unlikely to be encoded as rich published field metadata at registration time
      - they are more likely to appear only later in the generic runtime consumers or in the live object behavior
  - unlike neighboring `ProceduralTextureVerletWater` and `ProceduralTextureSkyhat`, the procedural-reflection registration does not chain through the common procedural-texture subsystem pointer
  - the only non-self reference to `0x0062ca10` is from data at `0x0110ec30`, which sits inside a broader static startup/registration table of procedural-render modules
  - raw assembly around `0x00e5edea` and `0x00e5ee2a` shows that table records like `0x0110ec30` and `0x0110ec10` are being spliced into two global linked chains via `DAT_017acd24` and `DAT_017ad1b8`
  - that means these procedural-render registrations are wired up by linker-style startup list construction rather than by ordinary direct calls
  - a fresh boundary probe now tightens that startup model:
    - addresses like `0x00e5cb10`, `0x00e5edea`, and `0x00e5ee2a` are not inside normal recovered functions at all
    - they sit in a dense blob of tiny `MOV old_head -> node.next ; MOV list_root -> node ; RET` stubs
    - practical reading:
      - the two startup roots really are being populated by linker-style static initializer stubs
      - there is no hidden high-level function there waiting to be named and decompiled
    - a direct xref dump on `DAT_017acd24` and `DAT_017ad1b8` stayed fully consistent with that model:
      - every defended xref is still a raw read/write pair from those same tiny stub regions
      - no normal recovered function reads either chain head directly
      - practical reading:
        - the runtime consumer of those startup-built lists is still not surfacing as a normal static xref reader of the chain heads themselves
  - the recovered procedural-render cluster splits across those two chains:
    - `rage__ProceduralReflection` and `rage__ProceduralTextureSkyhat` use the `DAT_017acd24` chain
    - `rage__IntervalShadows` and `rage__ProceduralTextureVerletWater` use the `DAT_017ad1b8` chain
  - the startup nodes also appear to share a common `0x20`-byte record shape with:
    - a registration-function field
    - a next-pointer field patched by the chain builder
    - two opaque descriptor tuples stored inline in the record
  - those inline descriptor fields currently have no independent xrefs, which suggests the runtime consumes the records as opaque module descriptors rather than by chasing each field individually
  - a direct xref pass against the tuple fields tightened that boundary further:
    - `0x00f96a28`, `0x00f96a50`, `0x00f96b00`, and `0x00f96b2c` only feed their owning startup records at `0x0110ec38`, `0x0110ec48`, `0x0110ec10`, and `0x0110ec20`
    - the paired values `0x010f0618`, `0x010d9a78`, `0x010e5bc8`, and `0x010e44e0` still have no defended xrefs at all
    - practical reading:
      - the inline tuple fields are not exposing a missed later runtime reader either
      - they still behave like opaque startup-record payload rather than a bridge into the live reflection runtime
  - two generic helpers sit immediately after descriptor population in every recovered registration routine:
    - `0x0041d240`
    - `0x00409110`
  - their disassembly is too damaged/overlapped to recover source-equivalent logic cleanly, but the caller sets match across the full recovered subsystem cluster
  - practical interpretation:
    - `0x0041d240` behaves like a descriptor-finalization helper
    - `0x00409110` behaves like the generic registration sink that publishes the completed module descriptor
  - raw caller assembly adds one more concrete constraint:
    - the caller loads `ECX` from `DAT_01bb5520`, adjusts it to `ECX + 0x18`, and then calls `0x00409110`
    - the descriptor name and descriptor-global address are also passed on the stack
    - that makes `DAT_01bb5520` the best current candidate for the shared engine definition / registration context used by these module publishes
    - separate xrefs show the same global being used in setup/config resource flows that parse files like `extra:/setup2.xml` and look up entries such as `audConfig`
    - one concrete producer is `0x006019e0`, which allocates a `0x30`-byte object for `DAT_01bb5520`, primes several internal fields/flags, and then routes into the same `0x00407d40` setup path seen from other config/resource consumers
    - a fresh registry-context probe now makes that boundary firmer:
      - `0x006019e0` allocates `DAT_01bb5520`, initializes it, and then immediately routes through `0x00407d40` while setting up `atSingleton<class_rage::rmPtfxManager>`
      - `0x00935350` wraps `extra:/setup2.xml` processing through the same context and `0x00407d40`
      - `0x006c5680` and `0x00700790` do the same around `memory:$...` resource lookups
      - practical reading:
        - `DAT_01bb5520` / `0x00407d40` belong to a generic setup/resource-lookup environment
        - this is upstream of the reflection-specific runtime behavior, not the missing procedural-reflection consumer itself
  - a shared parent/subfamily path is now visible too:
    - `0x0062e7b0`, now named `InitializeShaderFragmentSubsystemRegistration`, registers `rage__ShaderFragment`
    - `0x006178f0` (`rage__SkyhatMiniNoise`), `0x00617a70` (`rage__SkyLightController`), `0x0065c060` (`rage__AtmosphericScattering`), and `0x0065c190` (`rage__FogControl`) all publish through the same `0x0041d240 -> 0x00409110` path and explicitly inherit that shader-fragment parent through field `+0x08`
    - `rage__ProceduralReflection` and `rage__IntervalShadows` live in the same published module-definition system, but they do not look like trivial shader-fragment children
  - the first runtime-object semantics are now defended too:
    - `0x0062be20` is a destructor/reset path for the `rage::ProceduralReflection` object
    - it restores the base `rage::datBase` vftable and the decompiler models offsets `+0x50`, `+0x54`, and `+0x58` as refcounted references when they are populated
    - that proves the `0x60`-byte procedural-reflection instance is a real `datBase`-style runtime object, but the exact typing of the tail region is still mixed and not fully solved
    - the current build does not show a rich class-specific vtable for this object:
      - `rage::ProceduralReflection::vftable` currently exposes only the release/destructor wrapper at `0x0062bef0` and the singleton accessor at `0x0062bee0`
      - the next dwords after those entries fall straight into adjacent data, not more callable methods
      - practical reading:
        - the remaining live behavior is unlikely to be hidden behind a large reflection-specific virtual interface
        - it is more likely to live in the generic engine registration / runtime systems that consume the published definition record
    - `0x0062e410` is the thin factory thunk:
      - allocate `0x60` bytes
      - tail-jump into `0x0062d6d0`
    - `0x0062d6d0` seeds a small block of default float tunables and pointers:
      - `+0x14 = 1.0f`
      - `+0x1c = -15.0f`
      - `+0x24 = 15.0f`
      - `+0x2c = constructor-supplied float`
      - `+0x34 = 100000.0f`
      - `+0x50/+0x54/+0x58 = cleared tail slots`
  - a fresh probe now separates the adjacent static globals from the live procedural-reflection instance more cleanly:
    - the image block at `0x018b74bc .. 0x018b7538` is zero-initialized
    - `DAT_018b74d8`, `DAT_018b74fc`, `DAT_018b7510`, `DAT_018b7518`, and `DAT_018b751c` have only read-side xrefs, all from:
      - `0x005f9f30`
      - `0x005fa3b0`
      - `0x005fa650`
      - `0x00612b20`
    - `0x00612b20` binds those globals as runtime holder-slot IDs for:
      - `Holder<Matrix34 const*>`
      - `Holder<phInst*>`
      - `Holder<fragInst*>`
      - `Holder<int>`
      - a second `Holder<phInst*>`
    - practical reading:
      - this is an adjacent fragment/physics binding cluster, not direct proof about the live `rage::ProceduralReflection` tail fields
      - the unresolved `+0x50/+0x54/+0x58` fields in the procedural-reflection instance should stay documented separately from these zero-initialized global slot IDs
  - the shared class-ops pointer at `DAT_0043ead0` is also now less mysterious:
    - it is not reflection-specific
    - the same record is reused by many published render definitions, including `rage__ProceduralTextureRenderTargetDef`
    - practical reading:
      - `DAT_0043ead0` is a generic published-definition type record in this system, not a hidden reflection-runtime callback table
  - direct assembly dumps of `0x0041d240` and `0x00409110` remain too corrupted / overlapped to recover source-equivalent helper logic cleanly
  - a fresh decompile retry did not improve that:
    - `0x00409110` still decompiles as obvious garbage
    - `0x0041d240` still falls apart into overlapping/bad instruction data with unusable high-level output
  - but the sibling registration comparison still gives one solid conclusion:
    - the generic finalize/publish path is shared
    - the reflection-specific gap is not in that common sink's caller contract, but in what later runtime code does with the thin published record and the live `0x60`-byte object
  - fresh direct-xref probes now tighten that boundary again:
    - `DAT_018b74bc` has no defended non-registration code xrefs beyond:
      - `InitializeProceduralReflectionSubsystemRegistration`
      - `GetProceduralReflectionSubsystemRegistration`
    - the publish-companion pointer at `DAT_018b74b4` is only referenced from `InitializeProceduralReflectionSubsystemRegistration`
    - by contrast, sibling globals such as `DAT_018b74c4` (`rage__ShaderFragment`) and `DAT_018b79c4` (`rage__IntervalShadows`) do accumulate the expected registration-family xrefs from child-module setup routines
    - practical reading:
      - the current build is not reaching the procedural-reflection subsystem through direct static reads of the published record/global slot
      - the missing behavior is therefore more likely to live behind generic registry-managed lookup/instantiation than in a missed local function directly referencing `DAT_018b74bc`
    - a direct string-reference sweep now says the same thing for the subsystem names:
      - `rage__ProceduralReflection`
      - `rage__IntervalShadows`
      - `rage__ProceduralTextureSkyhat`
      - `rage__ProceduralTextureVerletWater`
      only have defended xrefs inside their own registration routines
    - practical reading:
      - the later runtime does not appear to resolve these module records through obvious static lookup-by-name code paths either
      - the remaining bridge still looks like opaque generic traversal/dispatch after publish
  - a direct follow-up on the local procedural-reflection family stayed thin too:
    - `GetProceduralReflectionSubsystemRegistration` is only a raw accessor for `DAT_018b74bc`
    - `0x0062bef0` is only the deleting-destructor wrapper over the already-known release path at `0x0062be20`
    - the nearby table entry at `0x00fe24e0/0x00fe24e4` only packages that destructor/accessor pair
    - the startup record at `0x0110ec30` is still only a linker-style registration entry pointing at `InitializeProceduralReflectionSubsystemRegistration`
    - practical reading:
      - the local `rage__ProceduralReflection` side really is just thin registration/object plumbing
      - the missing semantics remain in the later generic runtime consumer, not in an overlooked local method family
  - a deeper pass over the local `0x0062d0a0 .. 0x0062e470` code island now tightens that boundary further:
    - `0x0062d0a0` is still the only large local function that looks plausibly procedural-reflection-specific:
      - it consumes the live object transform/quaternion block at `+0x70 .. +0x80`
      - it derives a basis matrix, pushes render-state changes through `DAT_017ed8d8` / `DAT_017f5630`, and optionally calls object-owned callbacks through `+0xa0 / +0xac`
      - practical reading:
        - this is a live render-style method on the procedural-reflection object
        - but it still does not reveal the later generic engine consumer that schedules or owns that work
    - a scalar/data-use sweep tightened the same boundary again:
      - `0x0062d0a0` still has no defended scalar or defined-data hits anywhere in the image
      - the local vftable value `0x00fe24e0` is only written by `FUN_0062be20` and `InitializeProceduralReflectionDefaults`
      - the published-definition global `0x018b74bc` still only appears as the registration-time stack argument in `InitializeProceduralReflectionSubsystemRegistration`
      - practical reading:
        - none of those three local anchors expose a missed static table-driven bridge into the runtime
        - the remaining reflection-specific dispatch is still downstream of publish and outside the local reflection island
    - the larger neighboring functions mostly belong to sibling sky / water systems instead:
      - `0x0062d790`, now named `InitializeProceduralTextureSkyhatDefaults`, zeroes and seeds a `rage::ProceduralTextureSkyhat` object with default dimensions/flags
      - `0x0062d800`, now named `DeleteProceduralTextureSkyhat`, is the delete-wrapper over that sibling object's release path
      - `0x0062d830`, now named `ReleaseProceduralTextureSkyhatResources`, drops the sibling object's resource references and tails into the common release helper
      - `0x0062d8e0`, now named `InitializeSkyhatPerlinNoiseResources`, allocates `rage::SkyhatPerlinNoise` and loads `basePerlinNoise3Channel.dds`
      - `0x0062dc80`, now named `InitializeMiniSkyRenderResources`, creates `__perlinnoisert__%d`, `__miniskyrt__.dds`, `__miniskyblurredrt__.dds`, and `rage::ProceduralTextureShaderDef` instances such as `rage_perlinnoise`
      - `0x0062e150`, now named `RenderSkyMapTexturePass`, drives the small render pass that feeds the sky-map texture path
      - `0x0062e2e0`, now named `UpdateSkyMapTexturesIfEnabled`, gates that work on flags at `+0x54/+0x55` and binds the shader parameter `SkyMapTexture`
      - `0x0062e470` is an RTTI/type-init helper for `VerletWaterSimulation`, not part of the live procedural-reflection method surface
    - practical reading:
      - the bigger neighboring methods are mostly `ProceduralTextureSkyhat`, `Skyhat` / minisky, and `VerletWater` siblings
      - that local code island no longer looks like it is hiding the missing procedural-reflection bridge
    - a scalar/table sweep tightened the same boundary one step further:
      - `0x0062d0a0` has no defended scalar or defined-data hits anywhere in the image
      - `0x00fe24e0` is only written by `InitializeProceduralReflectionDefaults` and the destructor/reset path at `0x0062be20`
      - `0x00fe24e4` also has no defended scalar/data hits beyond sitting in the local vftable/table region
      - practical reading:
        - `0x0062d0a0` is not exposed through an obvious static callback/data table in the local reflection island
        - the remaining bridge looks even more like a later generic runtime dispatch path than a missed stored local callback
    - a broader displacement sweep over `+0x70..+0x80` and `+0xa0..+0xac` stayed noisy and mostly false-positive:
      - the strongest extra hits, such as `0x0059f940` and `0x005a6710`, decompile into unrelated benchmark/config runtime paths rather than render/reflection ownership
      - practical reading:
        - shared field offsets alone are not enough to tie those distant objects back to procedural reflection
        - the missing bridge still does not surface as an obvious second object family through simple displacement matching
  - the same is now true of the shared registry context:
    - `DAT_01bb5520` and `0x00407d40` are broad setup/resource machinery, not the missing reflection-only runtime path
    - so the remaining gap is pushed even later, into the engine code that consumes the published definition after this generic environment has already been set up
  - a fresh sink-cluster probe now reinforces that split:
    - `0x00409110` and `0x0041d240` are each called from a broad cross-subsystem registration family, including:
      - shader fragment
      - procedural reflection
      - procedural texture water / skyhat
      - interval shadows
      - atmospheric scattering
      - fog control
      - many neighboring unnamed render/module registrations in the same address bands
    - `0x00407d40` has a much narrower caller set rooted in generic setup/resource flows such as:
      - `0x006019e0`
      - `0x006c5680`
      - `0x00700790`
      - `0x00935350`
    - practical reading:
      - `0x00409110` / `0x0041d240` belong to the shared module-definition finalize/publish path
      - `0x00407d40` belongs to upstream setup/resource environment handling
      - the last reflection-specific gap is therefore downstream of publish, not hidden in the shared sink helpers themselves
  - a tighter sequence-window scan also came back negative for the expected direct instantiation shape:
    - no defended reflection-specific function was found that reads a published-definition `+0x10` size field and then calls a matching `+0x24` factory callback within a short instruction window
    - the strongest hits were unrelated virtual-object and gameplay/runtime container paths
    - practical reading:
      - the current build still does not expose an obvious static "read definition size, call definition factory" path for procedural reflection
      - this strengthens the conclusion that the remaining behavior is hidden behind a more indirect generic runtime manager rather than a missed local constructor wrapper
  - the adjacent modern reflection runtime is also a bit clearer now:
    - `0x00ad1ad0`, now named `InitializeDeferredLightingShaderHandles`, registers deferred-lighting shader handles and resolves a parameter named `ReflectionParams`
    - `0x00ad61c0`, now named `InitializeWaterReflectionRuntime`, seeds a small water-reflection tuning block with defaults:
      - `DAT_01550e5c = 1.0f`
      - `DAT_01550e60 = 0.1f`
      - `DAT_01550e64 = 0.01f`
      - `DAT_01550e68/+0x6c/+0x70 = 0.5f`
      - `DAT_01550e74/+0x78 = 1.0f`
    - `0x00adc280`, now named `UpdateWaterReflectionRuntimeParameters`, consumes those leading tuning globals and derives the follow-on runtime values in `DAT_015932dc .. DAT_01593300`
    - the same runtime also builds and uses `DAT_01550ea4` in a later live batching lane:
      - `0x00ad7ef0` -> `ExecuteWaterReflectionBucketPass`
      - `0x00ad88d0` -> `FlushQueuedWaterReflectionGeometry`
      - `0x00ad8960` -> `QueueWaterReflectionQuad`
      - `0x00ad9130` -> `QueueWaterReflectionGridStrip`
    - caller comparison now makes `DAT_01550ea4` much clearer even though `FUN_00430460` itself sits in an overlapped/bad-code region:
      - `FUN_00430460` is used by unrelated render/runtime systems, not just water reflection
      - every defended caller treats the returned object the same way:
        - call virtual `+0x04` before writing
        - use byte field `+0x06` as a mapped/ready flag
        - use dword field `+0x08` as the writable base pointer when mapped
        - call virtual `+0x10` after filling records
      - safest reading:
        - this is a generic mapped geometry-buffer helper
        - it is not the missing reflection-specific controller by itself
    - the water-reflection lane now looks like a real runtime batching path instead of adjacent render noise:
      - `UpdateWaterReflectionClipBounds` refreshes the active integer-space clip rectangle from the current view state and the selected water-reflection bucket state
      - `GetActiveWaterReflectionBucketState` selects the live per-bucket state block from `DAT_0154fd24` using the current bucket index in `DAT_01550df8`
      - `ExecuteWaterReflectionBucketPass` refreshes those clip bounds, begins the optional mapped-buffer path, processes several per-bucket geometry ranges from the active state block, ends the buffer path, flushes queued geometry, and closes the pass
      - `FlushQueuedWaterReflectionGeometry` submits any queued vertices through `DAT_01550ea0` and clears `DAT_01550ea8`
      - `QueueWaterReflectionQuad` clips one quad against the active bounds and appends one four-vertex / six-index style record into the mapped buffer when batching is enabled, or falls back to immediate draw calls when it is not
      - `QueueWaterReflectionGridStrip` builds larger interpolated reflection geometry and uses the same queue/flush contract around `DAT_01550ea4`
    - a broader deferred-render probe now sharpens what that lane is attached to:
      - `0x00ad1410`, now named `InitializeDeferredGBufferRenderTargets`, creates deferred render targets including:
        - `DAT_0154e170` -> `_DEFERRED_GBUFFER_1_`
        - `DAT_0154e174` -> `_DEFERRED_GBUFFER_2_`
        - `DAT_0154e178`
        - `DAT_0154e17c` -> `_DEFERRED_GBUFFER_3_`
        - `DAT_0154e180` -> `_STENCIL_BUFFER_`
      - `0x00ad1a70`, now named `GetDeferredGBuffer3RenderTarget`, returns `DAT_0154e17c`
      - `0x00ad1a80`, now named `GetDeferredStencilBufferRenderTarget`, returns `DAT_0154e180`
      - `0x00ad3040`, now named `AssignDeferredRenderTargetSlot`, publishes deferred target handles into slot globals `DAT_0154e230 .. DAT_0154e240`
      - `0x00d67a50`, now named `InitializeDeferredRenderTargetCallbackBindings`, registers generic callback wrappers around:
        - `InitializeDeferredLightingCompositeState`
        - `AssignDeferredRenderTargetSlot` for deferred target slots `0 .. 4`
        - `ResetDeferredLightingCompositeState`
      - `InitializeDeferredLightingShaderHandles` also resolves the shader parameter handles:
        - `DAT_0154e244` -> `ParabTexture`
        - `DAT_0154e248` -> `ReflectionParams`
        - `DAT_0154e24c` -> `depthSourceTexture`
      - `0x00add200`, now named `InitializeSceneRenderCallbackBindings`, is the first defended controller-level bridge for the live water/reflection lane:
        - it allocates `T_CB_Generic_1Arg<void(__cdecl*)(int),int>` callback wrappers
        - it registers `ExecuteWaterReflectionBucketPass` as one of those callbacks
        - it binds the live bucket/pass index from `ECX + 0x938` as the callback argument
        - it pairs that registration with a second bound callback at `0x00b59900`
        - its surrounding static record at `0x00ea7014` still has no defended direct scalar/data readers
        - the record-owned helpers are now clearer:
          - `0x00ade600`, now named `UpdateSceneRenderBindingViewState`, copies three live view-state values from `param_1 + 0x50c/+0x510/+0x514` into the owning context at `ECX + 0x958/+0x95c/+0x960`
          - `0x00a87680`, now named `ExecuteDeferredSceneBindingRecord`, executes a descriptor-driven sequence over callback/object arrays at `ECX + 0x224` and `ECX + 0x230`
        - the live water-reflection pass wrapper family is now tighter:
          - `0x00b1e810`, now named `ExecuteWaterReflectionScenePass`, is the common scene-pass body reached from opaque callback records centered on `0x00ea7000`, `0x00eea150`, `0x00eec400`, and `0x00eec448`
          - those wrappers all follow the same bracketed pattern:
            - allocate or refresh the active pass index through `FUN_00b05750`
            - snapshot scene state through `FUN_00b022e0`
            - execute `ExecuteWaterReflectionScenePass`
            - close or restore state through `FUN_00b022c0`
          - the record-specific entry slots are now more concrete too:
            - `0x00ade040`, now named `ExecuteInheritedWaterReflectionScenePassBinding`, either runs the common scene pass directly or inherits the active pass index from the owner at `ECX + 0x944`, then finishes through `FUN_00a9ebc0`
            - `0x00d689e0`, now named `ExecuteAngularWaterReflectionScenePassBinding`, derives an angle from the live direction vector at `DAT_012fb1b8 + 0x110/+0x114/+0x118`, stages that through `DAT_01797640` / `DAT_01797644`, runs the common scene pass, then restores the saved vector globals
            - `0x00d77550`, now named `ExecuteConditionalWaterReflectionScenePassBinding`, only runs the common scene pass when the paired global/owner-state gate allows it
            - `0x00d77430`, now named `ExecuteOverrideStateWaterReflectionScenePassBinding`, temporarily swaps owner state from the `DAT_0166da3c` / `DAT_0166da40` tables, runs the common scene pass, and then restores the original state
            - `0x00d685f0`, now named `ConfigureWaterReflectionPostPassCallbackSet`, installs a gated post-pass callback set around the active pass index and funnels the result through the generic state composer at `FUN_00b1dee0`
          - xrefs on the shared helper slots make the descriptor split clearer:
            - `ExecuteDeferredSceneBindingRecord`, `FUN_00a87b20`, `FUN_005e1380`, and `FUN_00401690` are all referenced from large numbers of unrelated data records
            - the per-record entry slots above are only referenced from their owning records
            - practical reading:
              - these callback records are phase descriptors built out of heavily reused generic helper slots plus small record-local wrapper bodies
              - the remaining reflection-specific gap is therefore not in the shared helper slots, but in the runtime that chooses and orchestrates these phase descriptors
          - the generic executor body is clearer now too:
            - `ExecuteDeferredSceneBindingRecord` allocates and materializes runtime objects from the descriptor record rather than just tail-calling callbacks
            - the reused command constructors are now identified:
              - `FUN_008dbec0` -> `InitializeNewDrawListCommand` (`CNewDrawListDC`)
              - `FUN_008dbe70` -> `InitializeLockRenderTargetCommand` (`CLockRenderTargetDC`)
              - `FUN_008dc2c0` -> `InitializeUnlockRenderTargetCommand` (`CUnLockRenderTargetDC`)
              - `FUN_008dc0d0` -> `InitializeSetCurrentViewportCommand` (`CSetCurrentViewportDC`)
              - `FUN_008dbbd0` -> `InitializeEndDrawListCommand` (`CEndDrawListDC`)
            - it first builds one owner-selected object through the owner virtual at `+0x24` and `InitializeNewDrawListCommand`
            - it then walks four descriptor entries at `ECX + 0x224`, creating objects through `InitializeLockRenderTargetCommand`
            - after owner virtual `+0x20`, it walks four more descriptor entries at `ECX + 0x230` in reverse order, creating objects through `InitializeUnlockRenderTargetCommand`
            - when the record flag at `ECX + 0x1c` is set, it also materializes an extra `0x410`-byte owner-derived object from `ECX + 0x2c`
            - the isolated large owner-derived constructor is now identified too:
              - `FUN_008daf10` -> `InitializeDrawMobilePhoneCameraCommand` (`CDrawMobilePhoneCameraDC`)
            - practical reading:
              - this layer is the generic phase/callback object materializer for these render-binding records
              - it is assembling reusable draw-list / render-target / viewport command objects rather than exposing a reflection-only local command family
              - the remaining missing bridge is narrower now:
                - not whether the runtime materializes the typed descriptors
                - but how the owner object that feeds those virtuals is selected relative to `rage__ProceduralReflection`
          - RTTI on the record-owned data tightens that model further:
            - `0x00ff0aac` is the `RTTICompleteObjectLocator` for `.?AV?$T_CB_Generic_4Args@P6AXAAVVector4@rage@@MMM@ZV12@MMM@@`
            - `0x00ffa0ac` is the `RTTICompleteObjectLocator` for `.?AV?$T_CB_Generic_1Arg@P6AXAAVMatrix44@rage@@@ZV12@@@`
            - `0x00ffa43c` is the `RTTICompleteObjectLocator` for `.?AVCRenderPhaseWaterReflection@@`
            - `0x00ffa488` is the `RTTICompleteObjectLocator` for `.?AVCRenderPhaseMirrorReflection@@`
            - a follow-up base-descriptor probe gives one clean hierarchy fact:
              - the shared base RTTI name at `0x0114ec88` is `.?AVCRenderPhase@@`
              - `CRenderPhaseMirrorReflection` sits in a simple two-entry hierarchy that includes that shared `CRenderPhase` base
              - the neighboring `CRenderPhaseWaterReflection` hierarchy block is noisier because adjacent RTTI also references `.?AVCReplayOverlay@@` and `.?AVCReplayWidget@@`, so that broader inheritance tree is not yet safe to name
            - a direct scalar/data-use sweep against the inferred phase vtables also stayed negative:
              - `0x00ffa440` (water-reflection-side vtable region) has no defended scalar/data hits
              - `0x00ffa48c` (mirror-reflection-side vtable region) has no defended scalar/data hits
              - practical reading:
                - the runtime is not exposing an obvious static table of those phase vtables either
                - the selector/orchestrator is still hidden behind more indirect generic traversal
            - the tiny record-owned code entries tied to those RTTI blocks are a little clearer too:
              - `0x005232b0`, now named `GetGenericRenderPhaseId0x1f`, returns `0x1f` and is reused across several descriptor records, including one reflection-adjacent binding record
              - `0x0053ae90`, now named `GetWaterReflectionRenderPhaseId`, returns `0x11`
              - `0x0053e300`, now named `GetMirrorReflectionRenderPhaseId`, returns `0x24`
              - `0x00ae2290`, now named `DestroyGenericCallbackWrapper`, is only the common callback-wrapper cleanup body that restores `CBaseDC::vftable` and conditionally frees the object
            - the static phase records themselves are now more explicit too:
              - the water-reflection record centered on `0x00eec400` contains:
                - `DeleteWaterReflectionRenderPhase`
                - `ExecuteConditionalWaterReflectionScenePassBinding`
                - `ExecuteDeferredSceneBindingRecord`
                - `ExecuteWaterReflectionPhaseStateCallbacks`
                - `GetWaterReflectionRenderPhaseId`
                - `EvaluateRenderPhaseBit9Predicate`
              - the mirror-reflection record centered on `0x00eec448` contains:
                - `DeleteMirrorReflectionRenderPhase`
                - `ExecuteOverrideStateWaterReflectionScenePassBinding`
                - `ExecuteDeferredSceneBindingRecord`
                - `ExecuteMirrorReflectionPhaseStateCallbacks`
                - `GetMirrorReflectionRenderPhaseId`
                - `GetBoundPhaseRenderTargetState`
              - the shared phase-owned helpers under those records are clearer:
                - `InitializeRenderPhaseBase` is the common constructor for this family:
                  - it zeroes the command-entry blocks at `ECX + 0x224 .. 0x233`
                  - seeds the phase-control fields at `+0x23d/+0x23e/+0x23f/+0x24e`
                  - optionally binds the source object passed through `param_1 + 0x10`
                - derived constructors are now identified too:
                - `InitializeMirrorReflectionRenderPhase` seeds `CRenderPhaseMirrorReflection::vftable`, stores a companion object at `ECX + 0x940`, stores `this` into that companion at `+0x50`, publishes `DAT_0166da10 = this`, and sets phase mode `ECX + 0x40 = 2`
                - `InitializeWaterReflectionRenderPhase` seeds `CRenderPhaseWaterReflection::vftable`, sets flag block `ECX + 0x8d0 = 0x232d20`, phase kind `ECX + 0x8f4 = 3`, and phase mode `ECX + 0x40 = 2`
                - `InitializeWaterSurfaceRenderPhase` seeds `CRenderPhaseWaterSurface::vftable`, sets flag block `ECX + 0x8d0 = 0x20000`, phase kind `ECX + 0x8f4 = 3`, and clears `ECX + 0x940/+0x944`
                - `InitializeRainUpdateRenderPhase` seeds `CRenderPhaseRainUpdate::vftable`, clears `ECX + 0x8d0`, and sets phase kind `ECX + 0x8f4 = 2`
                - the mirror-phase singleton bridge is now local and explicit:
                  - `DAT_0166da10` is only written by `InitializeMirrorReflectionRenderPhase` and `ReleaseMirrorReflectionPhaseState`
                  - the only recovered read is from `ExecuteWaterReflectionPhaseStateCallbacks`
                  - practical reading:
                    - this global is the local bridge from the water-reflection phase state stack into the live mirror-reflection phase object
                    - it is not a broad engine-side registry or unrelated render global
                - the missing phase owner/orchestrator is now identified too:
                  - `0x00b00b60`, now named `InitializeSceneRenderPhasesFromFeatureFlags`, allocates and registers the concrete `CRenderPhase*` objects from the live feature-flag word at `ECX + 0x40c/0x414`
                  - it directly instantiates:
                    - `InitializeMirrorReflectionRenderPhase` when bit `0x40` is present
                    - `InitializeWaterReflectionRenderPhase` and `InitializeWaterSurfaceRenderPhase` when bit `0x20000` is present
                    - `InitializeReflectionRenderPhase` and `InitializeInteriorReflectionRenderPhase` when bit `0x40000` is present
                    - `InitializeRainUpdateRenderPhase` unconditionally near the end of the pass setup
                  - it also allocates the scripted colour/depth, water-reflection colour/depth, water-surface colour, and reflection-map colour/depth render targets that those phases bind through `FUN_00a87db0` / `FUN_00a87dd0`
                  - practical reading:
                    - this is the concrete runtime phase manager that selects and materializes the reflection/water phase family
                    - the missing selector/orchestrator is no longer hypothetical
                - the follow-on publish/finalize step is now identified too:
                  - `0x005d49d0`, now named `InitializeAndFinalizeSceneRenderPhases`, is only a thin wrapper that calls `InitializeSceneRenderPhasesFromFeatureFlags` and then `FinalizeSceneRenderPhaseSlots`
                  - `FinalizeSceneRenderPhaseSlots` walks the active phase-slot array, allocates one wrapper object per active slot, stores that wrapper at `phase + 8`, and publishes it through `FUN_00b007d0`
                  - the remaining data xref at `0x00e93f70` is only a static table entry containing `InitializeSceneRenderPhasesFromFeatureFlags` among other generic function slots
                  - practical reading:
                    - the static phase-manager chain is now explicit end-to-end
                    - initialize concrete `CRenderPhase*` objects -> finalize/publish active slots
                - the reflection-map phase pair is now concrete too:
                  - `InitializeReflectionRenderPhase` seeds `CRenderPhaseReflection::vftable`, stores render mask `DAT_00450920` at `ECX + 0x8d0`, marks the phase active, and sets phase class `ECX + 0x8f4 = 3`
                  - `InitializeInteriorReflectionRenderPhase` seeds `CRenderPhaseInteriorReflection::vftable`, stores render mask `0x800880` at `ECX + 0x8d0`, allocates a `0xd0`-byte companion object through `FUN_009df700`, back-links that companion through `+0x50`, and marks the phase as the interior-reflection variant through `ECX + 0x40 = 1`
                  - practical reading:
                    - these are the direct reflection-map runtime phases selected by the live scene phase manager
                    - they are no longer just anonymous callbacks under the `0x40000` feature flag
                - `ReleaseRenderPhaseBaseState` is the common `CRenderPhase` teardown path
                - `ReleaseMirrorReflectionPhaseState` clears the mirror-specific bound target at `ECX + 0x940` and then falls back to `ReleaseRenderPhaseBaseState`
                - `ExecuteWaterReflectionPhaseStateCallbacks` builds the water-reflection state stack, including `FUN_00b1dee0(..., 0x90d, 2)` and a forced-technique push/pop pair
                - `ExecuteMirrorReflectionPhaseStateCallbacks` builds the mirror-reflection state stack, including `FUN_00b1dee0(..., 0x307, mode)` and the follow-on generic finalization path
                - `EvaluateRenderPhaseBit9Predicate` is only a flag test over bit `9` of `ECX + 0x8e8`, with optional inversion through byte `ECX + 0x1d`
            - practical reading:
              - these records are not anonymous payload blobs
              - they are typed render-phase / callback-wrapper descriptors
              - the generic runtime that selects and executes those typed phase descriptors is now concretely recovered in `InitializeSceneRenderPhasesFromFeatureFlags` plus `InitializeAndFinalizeSceneRenderPhases`
              - the live reflection-map phases are now concrete too through `InitializeReflectionRenderPhase` and `InitializeInteriorReflectionRenderPhase`
              - practical conclusion:
                - the actionable modern runtime counterpart to the legacy high-quality-reflections feature is this `CRenderPhaseReflection` / `CRenderPhaseInteriorReflection` family under the scene phase manager
                - `rage__ProceduralReflection` should now be treated as adjacent registered render-subsystem context rather than the missing direct bridge into the live phase runtime
          - the collector path beneath that pass is now defended:
            - `0x00ad7d80`, now named `BuildWaterReflectionPassQueues`, snapshots the shared queue counters, binds the pass-owned queue counters through `DAT_01550de8`, `DAT_01550dec`, and `DAT_01550df0`, clears them, and dispatches traversal
            - `0x00ad99d0`, now named `TraverseWaterReflectionVisibilityTree`, derives reflection-space clip/projection bounds from the live camera/view globals, clears visitation bits in `DAT_01550ebc` and `DAT_0154e30e`, and invokes `0x00d62dc0` with callback `0x00ad4a70`
            - `0x00ad7190`, now named `QueueWaterReflectionIndexedPrimitive`, consumes typed entries from `DAT_0154e358` / `DAT_0154e478`, marks visited quad and strip records, appends them into the active per-pass queues, and optionally forwards debug geometry into `FUN_00b03ea0`
            - `0x00ad4f20`, now named `ResetWaterReflectionPassQueues`, clears the same pass-owned queue bindings without visibility traversal
          - a sibling-pass follow-up tightens the common helper classification too:
            - the paired callback at `0x00b59900` is a gated post-pass cleanup/state routine, not the missing reflection controller
            - `0x00b1dee0` is used by the reflection-adjacent wrappers and by several unrelated render passes with different bitmasks and mode selectors
            - the downstream helper `0x00c0fa50` is broader too:
              - it is shared by the same reflection-adjacent wrapper family
              - it pushes several generic no-arg callbacks gated by render flags
              - the callback bodies hanging off it (`0x00c108e0`, `0x00c10730`, `0x009bdbe0`, `0x00c1f170`) mostly resolve to flag-conditioned render-state or technique toggles driven from the shared flag word returned by `FUN_00b00780`
            - practical reading:
              - `0x00b1dee0` is a generic render-pass state composer
              - this `0x00c0fa50` layer is also generic render-pass finalization/state handling
              - the missing reflection-specific semantics are therefore still downstream of those wrappers, not hidden inside them
      - direct callback-target decompilation now tightens the attachment model:
        - `InitializeDeferredLightingCompositeState` uses `GetDeferredGBuffer3RenderTarget`, seeds fixed shader/render state, publishes one extra deferred target slot through `DAT_0154e24c`, and then tails into `ResetDeferredLightingCompositeState`
        - non-water callers such as `0x0092f570` and `0x009303d0` still reach `GetDeferredStencilBufferRenderTarget`
      - the deferred target callback binder shows the same opaque-record behavior:
        - `InitializeDeferredRenderTargetCallbackBindings` sits in a static record centered on `0x00eea1a4`
        - that record also has no defended direct scalar/data readers
        - its record-owned helpers are clearer too:
          - `0x00d68730`, now named `ExecuteDeferredRenderTargetBindingRecordIfEnabled`, gates and then tail-calls into `ExecuteDeferredSceneBindingRecord`
          - `0x00d68bc0` is only a thunk into the shared state-copy helper used by that record
      - the reflection-parameter side is narrower too:
        - `FUN_00abd480` consumes `DAT_0154e278` and `DAT_0154e27c` through `CSetReflctOccParamsDC`
        - no defended direct writes or immediate/scalar references to `DAT_0154e278` / `DAT_0154e27c` were recovered
        - no defended direct runtime consumers of `DAT_0154e244` / `DAT_0154e248` / `DAT_0154e24c` were recovered beyond the known handle initialization and callback-driven use sites
      - practical reading:
        - the extra callers still do not bridge the water lane back to `rage__ProceduralReflection`
        - but the controller-level picture is now sufficient for implementation:
          - the water/reflection batching lane is explicitly routed through a generic scene/render callback system
          - the live reflection pass now has a defended internal collector pipeline:
            - wrapper family -> `ExecuteWaterReflectionScenePass` -> `BuildWaterReflectionPassQueues` -> `TraverseWaterReflectionVisibilityTree` -> `QueueWaterReflectionIndexedPrimitive`
          - the deferred G-buffer / stencil targets are published through that same callback ecosystem
          - both callback-binding records now look like opaque phase/descriptor tables consumed indirectly rather than through missed obvious direct callers
          - the reflection-parameter handles and reflection-occlusion state look indirect in the same way
        - practical conclusion:
          - the live modern reflection path is already defensibly recovered at the runtime level
          - any tighter architectural linkage to `rage__ProceduralReflection` is no longer blocking for understanding or reimplementation
    - `0x0065bcb0`, now named `HasSphericalAmbientOnParameter`, proves the interval-shadow side also has a boolean-style spherical-ambient capability test keyed on the shader parameter `SphericalAmbientOn`
  - useful conclusion:
    - `rage__ProceduralReflection` is being registered as its own standalone render module, not as a thin child of the broader procedural-texture path
    - the sparse direct xrefs make sense: runtime discovery is likely driven through the engine's generic module-registration machinery rather than through many explicit callers
    - the only substantial local procedural-reflection body recovered so far is `0x0062d0a0`; the rest of the big neighboring methods in that address island mostly resolve to `ProceduralTextureSkyhat`, `Skyhat`, minisky, and `VerletWater` siblings
    - `0x0062d0a0` also has no defended scalar/data-table uses, and the local vftable slot is only written by the constructor/destructor pair
    - there is now at least a weak lifecycle/grouping signal too: the engine does not throw all of these render modules into one undifferentiated startup list
    - the remaining uncertainty is now only architectural context:
      - the generic mapped geometry-buffer helper behind `DAT_01550ea4` is understood behaviorally
      - the live batching path is defended at the controller layer through `InitializeSceneRenderCallbackBindings`
      - the scene phase manager now directly materializes the reflection-map pair through `InitializeReflectionRenderPhase` / `InitializeInteriorReflectionRenderPhase`
      - any tighter relationship to the separately registered `rage__ProceduralReflection` module is useful background, but no longer necessary to understand or reimplement the live reflection feature
- The same subsystem-name sweep also clarified the neighboring shadow path:
  - `0x0065bf20`, now named `InitializeIntervalShadowsSubsystemRegistration`, registers `rage__IntervalShadows`
  - `0x0065de10` creates the runtime `rage::IntervalShadows` object
  - `0x0065b5c0` allocates `rage::IntervalShadows::IntervalShadowVarCache`
  - `0x0065d2b0` initializes the interval-shadow / occlusion parameter indices inside that cache
- Practical implication:
  - the direct current-build runtime anchor for the old high-quality-reflections feature is now the `CRenderPhaseReflection` / `CRenderPhaseInteriorReflection` family materialized by `InitializeSceneRenderPhasesFromFeatureFlags`
  - `rage__ProceduralReflection` remains useful adjacent subsystem context, but it no longer needs to be treated as the missing direct runtime bridge
  - the interval-shadow / occlusion path is still useful nearby lighting context, but it does not currently look like the direct host object for the legacy hook
