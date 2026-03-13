# Reimplementation Status

Objective: recover enough of `IVMenuAPI.asi` and `ZolikaPatch.asi` to build an open replacement that can be retargeted to the current GTA IV PC build instead of the legacy version the original ASI patch expects.

## Recovered Architecture

- `IVMenuAPI.asi` exposes a small menu-registration API.
- `ZolikaPatch.asi` is a config-driven bootstrapper and patch installer.
- The main Zolika bootstrap routine now has a working descriptive name in Ghidra:
  - `0x100523c0` -> `InitializeConfigAndApplyPatches`
- The menu toggle wrapper now has a working descriptive name in Ghidra:
  - `0x100522b0` -> `RegisterMenuDisplayToggleOption`

## IVMenuAPI Findings

Recovered exports:

- `AddIVMenuEnum` at `0x10009f40`
  - Stores enum metadata and localized labels in internal arrays.
  - Hard limit observed: `0x7f` enum entries.
  - Calls an internal refresh/rebuild routine after registration.
- `AddIVMenuOption` at `0x1000a070`
  - Stores an option descriptor in an internal array.
  - Hard limit observed: `0x400` options.
  - Also triggers the same menu refresh path.
- `ReloadIVMenus` at `0x1000a160`
  - Thin wrapper over the menu rebuild function.

Recovered internal roles:

- `0x10005630` -> `PopulateIVMenuRegistrationCaches`
  - mirrors the live option table into `DAT_1002cce0` / `DAT_1002cce4`
  - mirrors the live enum table into `DAT_1002ece0` / `DAT_1002ece4`
  - seeds the active counts and next-slot counters used later by the exports
- `0x10007780` -> `InitializeIVMenuApiRuntime`
  - bootstrap path reached from `IVMenuApiDllMain`
  - initializes host state, resolves host callbacks, and then seeds the local cache layer when the supported host variant is present
- `0x10007d80` -> `RebuildAndPublishIVMenuRegistrations`
  - invoked after both enum and option registration
  - publishes the current option and enum counts through resolved callback targets
- `0x10007f40` -> `InvokeReloadIVMenusCallback`
  - reload export bridge that resolves and invokes one callback target
- `0x10006a60` -> `LoadAndPatchIVMenuDefinition`
  - parses a menu-definition source, walks the cached enum/option registrations, and emits a patched in-memory definition blob
  - this is the hidden menu-definition integration helper behind the otherwise small export surface
- `0x10006940` -> `LoadAndPatchIVMenuDefinitionAndResume`
  - pushad/popad interception stub that routes host menu-definition loads through the patch helper and then resumes host execution
- `0x10006250` -> `SerializeOptionRegistrationToMenuXml`
  - emits XML from cached option descriptors
- `0x10007030` -> `SerializeEnumRegistrationToMenuXml`
  - emits XML from cached enum descriptors
- `0x10006960` -> `MenuNodeContainsValueAttribute`
  - proves cached option descriptor offset `+0x14` is a menu-node value filter
- `0x10007290` -> `ResolveMenuDisplayValueToken`
  - resolves option and enum tokens through the cached registration tables
- `0x10007c30` / `0x10007c90`
  - callback bridges for invoking getter/setter logic on cached option descriptors by published index
- `0x100052c0` / `0x10009690`
  - helper pair for indexing the enum descriptor's localized/wide label vector and extracting the string data
- `0x100046c0` / `0x10004730`
  - append helpers that prove the enum descriptor owns growable ANSI and wide-label vectors
- `0x10004f60`
  - single-record constructor for the `0x34`-byte cached enum descriptor
- `0x10009db0` -> `GetEnumDisplayValueCount`
  - confirms the enum display-value collection at offset `+0x18`
- `0x10009cc0` / `0x10009cf0`
  - internal ANSI/wide label copy helpers used while building enum entries
- `0x10004af0` / `0x10004c30`
  - ANSI and wide-string construction helpers used before label copy
- `0x10002a30` / `0x1001ba10`
  - constructor/destructor pair for the cached enum descriptor table
- `0x10002a60` / `0x1001ba30`
  - constructor/destructor pair for the patched menu XML buffer

See `ivmenuapi_internal_notes.md` for the full breakdown.

Open-version implication:

- The original mod architecture already separates menu registration from patch logic.
- A replacement menu library can keep this API shape or provide a compatibility shim while the open patch code is rebuilt.

## ZolikaPatch Findings

`InitializeConfigAndApplyPatches`:

- Opens and parses `ZolikaPatch.ini`.
- Reads many feature toggles directly from the INI.
- Resolves game addresses with byte-pattern scans through `FUN_10003830`.
- Applies patches with `VirtualProtect` and direct instruction/data writes.
- Detects loader/runtime state such as duplicate loading and presence of helper modules like `XLivelessAddon.asi`.
- Registers menu-related options, including `AddOptionsInMenu`, and uses `IVMenuAPI.asi` dynamically when available.

`RegisterMenuDisplayToggleOption`:

- Loads `IVMenuAPI.asi` with `GetModuleHandleA`.
- Resolves `AddIVMenuOption` with `GetProcAddress`.
- Registers the `MENU_DISPLAY_ON_OFF` option dynamically.

This confirms the mod is structured as:

1. config parse
2. version/address discovery
3. per-feature patch install
4. optional menu integration

## First Named Feature Patchers

- `0x10022fc0` -> `InstallIncreaseVehicleModelLimitPatch`
  - Pattern-scans the game and overwrites a 32-bit limit with `0x400`.
- `0x10023020` -> `InstallIncreaseVehicleStructLimitPatch`
  - Pattern-scans the game and overwrites a single-byte immediate with `100`.
- `0x1001b580` -> `InstallEpisodicVehicleSupportPatch`
  - Large patch set.
  - NOPs multiple episode checks that compare against `2`.
  - Strong indicator this feature removes hardcoded episode-specific restrictions in several code paths.
- `0x10037170` -> `InstallTeleportProtectionPatch`
  - Converts a matched function into an immediate `ret 8`.
  - This is a hard bypass, not a small constant tweak.
- `0x10045c50` -> `InstallSkipIntroPatch`
  - Replaces an instruction site with a jump to custom code at `0x10045c2b`.
- `0x10046640` -> `InstallSkipMenuPatch`
  - Pattern-matches one of multiple code layouts.
  - Rewrites a call target to redirect control flow to custom code at `0x1004660b`.

Additional feature patchers now classified:

- `0x1001b310` -> `InstallTBoGTHeliHeightLimitPatch`
  - Small conditional-branch removal gated to later build buckets.
- `0x1001b380` -> `InstallEpisodicWeaponSupportPatch`
  - Removes multiple episode-locked weapon checks keyed on value `2`.
- `0x1001bd60` -> `InstallBikePhoneAnimsFixPatch`
  - Focused behavior-gate removal patch.
- `0x1001bdd0` -> `InstallBikeFeetFixPatch`
  - Focused 12-byte NOP patch for bike-specific behavior.
- `0x1001be40` -> `InstallPipeBombDropIconSupportPatch`
  - Removes a small hardcoded restriction in the pipe-bomb drop icon path.
- `0x1001bec0` -> `InstallPoliceEpisodicWeaponSupportPatch`
  - Companion patch that removes an episodic weapon restriction in a police/NPC path.

Episode / vehicle compatibility cluster summary:

- Several gameplay/content features are implemented as removal of hardcoded "episode == 2" gating.
- `InstallEpisodicVehicleSupportPatch` and `InstallEpisodicWeaponSupportPatch` are the core unlock/compatibility patchers in this cluster.
- These belong in a separate RE bucket from launcher/runtime compatibility patches because they target gameplay/content segregation, not the PC startup stack.

Graphics / render / streaming cluster summary:

- `InstallBuildingDynamicShadowsPatch`
  - shadow hook with custom code and saved original addresses
- `InstallBuildingAlphaFixPatch`
  - localized alpha gate removal and branch rewrite
- `InstallEmissiveLerpFixPatch`
  - custom floating-point/lighting hook
- `InstallForceShadowsOnObjectsPatch`
  - one-branch force-enable patch
- `InstallForceDynamicShadowsEverywherePatch`
  - short branch removal after a shadow-related flag test
- `InstallHighQualityReflectionsPatch`
  - wrapper into a callback-based reflection-hook resolver path rather than a direct byte patch
- `InstallRemoveBoundingBoxCullingPatch`
  - converts a culling branch into an unconditional path
- `InstallRemoveStaticCarShadowsPatch`
  - bypasses one static-shadow path
- `InstallRemoveStaticCutsceneCarShadowsPatch`
  - cutscene-specific companion to static car shadow removal
- `InstallImprovedShaderStreamingPatch`
  - large multi-hook render/streaming patch
- `InstallSuperLODFixPatch`
  - version-specific custom hook for build buckets `8` and `9`
- `InstallVRAMFixPatch`
  - custom hook into VRAM-related logic

Compatibility / save / startup cluster summary:

- `InstallAllowHashesForIDEPatch`
  - custom validation/rewrite hook, not a raw NOP patch
- `InstallLoadDLCsPatch`
  - one of the largest version-specific content-loading patch sets
- `InstallNoLoadingSleepPatch`
  - zeros out a hardcoded sleep delay
- `InstallSavegameFixPatch`
  - substantial multi-hook save/load compatibility patch
- `InstallSCOSignatureFixPatch`
  - localized signature/validation branch bypass
- `InstallStartupTimeFixPatch`
  - bulk pointer redirection table patch
- `InstallVSyncFixPatch`
  - redirects VSync-related operands to shared state/config globals
- `InstallSpeedupBugFixPatch`
  - custom hook with many preserved original addresses
- `InstallPedModelsInCutscenesFixPatch`
  - custom logic override for cutscene model handling
- `InstallLocalSettingsPathPatch`
  - full path redirection subsystem using current working directory
- `InstallNewFilesCompatibilityPatch`
  - compatibility shims gated against other installed mods
- `InstallNewSavesCompatibilityPatch`
  - focused save-compatibility hook

Stability / crash / gameplay-fix cluster summary:

- many of these features are true custom control-flow replacements, not constant edits
- custom-hook fixes now classified include:
  - `InstallAimProjectileCrashFixPatch`
  - `InstallBenchmarkFixPatch`
  - `InstallCollisionDeadlockFixPatch`
  - `InstallCombinedRadioFreezeFixPatch`
  - `InstallEpisodeSwitchFreezeFixPatch`
  - `InstallGroupHackFixPatch`
  - `InstallBlackscreenFixPatch`
  - `InstallFreezeGunFixPatch`
  - `InstallCrashMsgFixPatch`
  - `InstallBadPlayerModelCrashFixPatch`
  - `InstallDebugPrintSpawnsPatch`
  - `InstallPathNodeCrashFixPatch`
  - `InstallPlaneCrashFixPatch`
  - `InstallWantedLevelCrashFixPatch`
- simpler direct patches in this bucket include:
  - `InstallRemoveAllWeaponsProtectionPatch`
  - `InstallMPNikoCrashFixPatch`

Final matrix-cleanup pass summary:

- newly classified remaining real installers:
  - `InstallBetterMPSyncPatch`
  - `InstallDoNotPauseOnMinimizePatch`
  - `InstallHighFPSBikePhysicsFixPatch`
  - `InstallInfiniteCarBottomDistancePatch`
  - `InstallIncreasePtrNodesPatch`
  - `InstallHighFPSSpeedupFixPatch`
  - `InstallMiscFixesPatch`
  - `InstallPlayerAsPedComponentsFixPatch`
- `EnsureIVMenuApiLoaded` is now identified as a helper/loader function and should not be mistaken for a direct gameplay patch body.
- `RestoreDeathMusic` is now split correctly:
  - `EnsureIVMenuApiLoaded` is only the menu availability helper
  - `InstallRestoreDeathMusicPatch` at `0x10050bd0` is the real feature installer
  - `DAT_10159b86` is the feature state flag exposed to the IV menu callbacks
- `RestoreDeathMusic` now also has its local runtime helpers resolved:
  - the displaced call targets land at `0x10050a50` and `0x10050ad0`
  - `0x10050a50` is the state gate
  - `0x10050ad0` applies the variant-byte patch only when that gate passes
- `DAT_1015be04` is now mapped by direct proof, not inference:
  - `0x10003b80` reads the EXE file version resource and converts it into a separator-free decimal token
  - `0x10055c70` maps that token into the internal bucket stored in `DAT_1015be04`
  - recovered exact cases include `1040 -> 5`, `1060 -> 7`, `1070 -> 8`, `1080 -> 9`, `1130 -> 0xd`, and `12043 -> 0x12`
- the high-quality reflections feature is now better understood as a callback bridge:
  - `0x10001060` initializes the callback context by calling `AssignHighQualityReflectionCallbacks(0x10190ac0)`
  - `DAT_10192f14` at `0x10190ac0 + 0x2454` is the resolver/dispatch slot
  - `DAT_10192f18` at `0x10190ac0 + 0x2458` is the trampoline-entry slot used by `HighQualityReflectionStageTrampoline` at `0x1001f030`
  - that trampoline executes stage values `0` through `0xb` against the resolved engine target before an indirect tail call
  - `DAT_10196370` at `0x10190ac0 + 0x58b0` is the reset slot
  - `InstallHighQualityReflectionsPatch` wraps `0x10023db0`
  - that callee dispatches through `DAT_10192f14`
  - nearby code at `0x10023cc0` wires the resolver, trampoline entry, and reset stub into a render/reflection context object
  - current caller evidence only shows that setup helper being reached from the init path, which makes the record rooted at `0x10190ac0` look patch-owned rather than like a heavily shared engine callback table
  - `ResolveHighQualityReflectionHookTarget` at `0x1001f390` builds the managed payload and resolves the actual engine-side target
  - `ClearHighQualityReflectionCallbackSlots` at `0x10023d70` zeroes all three slots before the callback path is reused
  - after patching the matched site, the resolver derives:
    - `DAT_10159cfc = matched_site + 0x13`
    - `DAT_10159d04 = matched_site + *(rel32 at matched_site + 0xf) + 0x13`
  - this means the trampoline stages into the original displaced engine callee and then jumps back to the continuation immediately after the patched block
  - the resolver/trampoline pair also uses a scratch register-save block where `DAT_10159ce4`..`DAT_10159d0c` hold saved `EBP/EAX/ESI/ESP/EDX/EBX/ECX/EDI`, `DAT_10159ce0` holds the staged callback context, `DAT_10159d04` is the staged callback entry, and `DAT_10159cfc` is the post-stage continuation target
  - within the ASI itself, the shared host-state pointer `DAT_10158060` is only referenced by the trampoline stage calls
  - a fresh direct decompile now confirms that the trampoline never dereferences `DAT_10158060` or `DAT_10159ce0`; it only forwards them into the resolved game callback as `(stage_index, host_state, staged_context)`
  - the resolver decompile also confirms that the ASI's role is limited to signature construction, pattern matching, jump installation, slot reset, and reconstruction of the displaced continuation / callback targets
  - that means the remaining semantics live in the game-side callback target, not in hidden ASI-side logic
  - the callback slots at `0x10190ac0 + {0x2454,0x2458,0x58b0}` and the scratch globals around `DAT_10159ce0` now form a defensible logical callback record plus scratch-frame model in the notes, even though the original binary stores them as loose globals
  - current-build probing now has a real reflection-side semantic anchor too:
    - `0x0062ca10` registers `rage__ProceduralReflection`
    - `0x0062e410` is the registration's factory thunk
    - `0x0062d6d0` initializes the runtime `rage::ProceduralReflection` object
    - the registration record is wired into the startup chain rooted at `DAT_017acd24`
    - the startup record itself now looks like a `0x20`-byte opaque module descriptor with a registration-function field, a chain-link field, and two inline descriptor tuples
    - a fresh boundary probe now shows the startup roots themselves are populated by many tiny RET-terminated static-initializer stubs rather than by a normal recovered startup function
    - that means the startup-list construction is understood as glue code, not as a still-missing semantic function
    - like the other recovered render modules, the registration path terminates in the generic helper pair `0x0041d240 -> 0x00409110`, which now looks like a universal finalize-and-publish path for these module descriptors
    - raw assembly now also shows the publish call being made as `ECX = DAT_01bb5520 + 0x18`
    - the safest reading is that `DAT_01bb5520` is a broader engine definition / registration context, not a narrowly render-only registry manager
    - that same global also appears in setup/config resource flows that parse `extra:/setup2.xml` and look up entries like `audConfig`
    - a fresh registry-context probe now tightens that reading:
      - `0x006019e0` allocates and initializes `DAT_01bb5520` while setting up `atSingleton<class_rage::rmPtfxManager>`, then routes through `0x00407d40`
      - `0x00935350` uses the same context while processing `extra:/setup2.xml`
      - `0x006c5680` and `0x00700790` use the same context around `memory:$...` resource lookups
      - that makes `DAT_01bb5520` / `0x00407d40` look like broad setup/resource machinery rather than the missing reflection-specific runtime consumer
    - the current-build render family now has one more defended anchor:
      - `0x0062e7b0` registers `rage__ShaderFragment`
      - `0x006178f0` registers `rage__SkyhatMiniNoise`
      - `0x00617a70` registers `rage__SkyLightController`
      - `0x0065c060` registers `rage__AtmosphericScattering`
      - `0x0065c190` registers `rage__FogControl`
    - those registrations all publish through the same `0x0041d240 -> 0x00409110` path and explicitly inherit the shared `rage__ShaderFragment` descriptor through field `+0x08`
    - `rage__ProceduralReflection` and `rage__IntervalShadows` stay in that same published module-definition system, but do not appear to be simple shader-fragment children
    - `0x0062be20` now gives the first defended runtime-object semantics for `rage::ProceduralReflection` itself:
      - it restores the base `rage::datBase` vftable
      - it releases refcounted values through the tail region at `+0x50`, `+0x54`, and `+0x58` when those fields are populated
      - that makes the runtime object a real `datBase`-style container, but not all tail-slot types are fully resolved yet
    - the factory/constructor path is also tighter now:
      - `0x0062e410` allocates `0x60` bytes and tail-jumps into `InitializeProceduralReflectionDefaults`
      - the constructor seeds a small float-tunable block with defended values at `+0x14`, `+0x1c`, `+0x24`, `+0x2c`, and `+0x34`
      - the tail slots at `+0x50/+0x54/+0x58` start null and are later touched by the destructor path
    - the published definition record is also now partly recovered from `InitializeProceduralReflectionSubsystemRegistration`:
      - `+0x04` -> `"rage__ProceduralReflection"`
      - `+0x10` -> object size `0x60`
      - `+0x24` -> factory callback `0x0062e410`
      - `+0x2c` -> shared class-ops / type record `DAT_0043ead0`
      - `+0x08/+0x0c` are cleared before publish
      - `+0x1c/+0x1e` are normalized before `0x0041d240 -> 0x00409110`
    - a sibling registration comparison now clarifies the remaining gap:
      - `rage__ProceduralTextureRenderTargetDef` and `rage__IntervalShadows` publish through the same finalize/publish path but seed extra field-offset metadata before publish
      - `rage__ProceduralReflection` does not
      - that makes the reflection definition comparatively thin and pushes the remaining unknowns into later runtime consumers rather than registration-time metadata
    - a fresh published-slot xref pass tightens that conclusion:
      - `DAT_018b74bc` has no defended direct readers beyond `InitializeProceduralReflectionSubsystemRegistration` and `GetProceduralReflectionSubsystemRegistration`
      - the publish-companion pointer `DAT_018b74b4` is only referenced from the registration path itself
      - so the unresolved current-build behavior is not hiding behind a missed direct user of the published reflection global slot
      - it is more likely behind indirect generic registry-managed lookup or instantiation after the publish step
    - a direct follow-up on the local procedural-reflection family says the same thing:
      - the accessor is only a raw getter for `DAT_018b74bc`
      - `0x0062bef0` is only the deleting-destructor wrapper over the known release path
      - the nearby table entry only packages that destructor/accessor pair
      - so the remaining gap is still in the later generic runtime consumer, not in a missed local method family
    - a fresh current-build probe now splits the adjacent static globals from the live procedural-reflection object more cleanly:
      - the image block at `0x018b74bc .. 0x018b7538` is zero-initialized
      - `DAT_018b74d8`, `DAT_018b74fc`, `DAT_018b7510`, `DAT_018b7518`, and `DAT_018b751c` have only read-side xrefs, all from the same fragment/physics binding cluster:
        - `0x005f9f30`
        - `0x005fa3b0`
        - `0x005fa650`
        - `0x00612b20`
      - `0x00612b20` maps those globals directly to runtime `rage::atAny::Holder<>` slot IDs for `Matrix34 const*`, `phInst*`, `fragInst*`, `int`, and a second `phInst*`
      - that means these globals are better treated as adjacent runtime slot-ID storage, not as solved fields of the live `rage::ProceduralReflection` instance
      - the unresolved procedural-reflection tail remains the live object fields at `+0x50/+0x54/+0x58`, not the zero-initialized global slot-ID block
    - the vtable side is narrower too:
      - `rage::ProceduralReflection::vftable` currently exposes only `0x0062bef0` and `0x0062bee0`
      - the next dwords fall into adjacent data, not more virtual methods
      - that makes it more likely that the remaining runtime behavior lives in generic engine registry/runtime consumers rather than in a large reflection-specific virtual surface
    - `DAT_0043ead0` is now better classified as a shared class-ops / type record reused by many published render definitions, not as a reflection-specific hidden callback target
    - direct assembly around `0x0041d240` and `0x00409110` remains too corrupted to decompile meaningfully, but the caller-side comparison is still enough to say the shared publish contract is not where the reflection-specific semantics diverge
    - the same is now true of the shared registry environment:
      - `DAT_01bb5520` and `0x00407d40` are now defended as generic setup/resource infrastructure
      - so the remaining unknowns are pushed later into the live engine consumers of the published procedural-reflection definition and object
    - a deeper sink-cluster pass now reinforces that split:
      - `0x00409110` and `0x0041d240` are called by a broad cross-subsystem registration family spanning shader-fragment, procedural-reflection, interval-shadows, procedural-texture, fog, atmospheric-scattering, and nearby unnamed siblings
      - `0x00407d40` does not share that caller set and stays confined to generic setup/resource paths
      - that means the last reflection-specific uncertainty is downstream of the shared finalize/publish sink, not hidden in the sink helpers themselves
    - a tighter sequence-window scan also failed to surface a defended reflection-specific "read size, call factory" consumer
      - the strongest matches were unrelated runtime virtual/container paths
      - so the remaining current-build gap still points to a more indirect generic manager, not a missed local instantiation wrapper
    - a nearby modern reflection runtime/config cluster is also now identified:
      - `0x00ad1ad0` registers deferred-lighting shader handles and resolves `ReflectionParams`
      - `0x00ad61c0` seeds water-reflection tuning globals and runtime state
      - `0x00adc280` -> `UpdateWaterReflectionRuntimeParameters`
      - `0x00ad5ab0` -> `UpdateWaterReflectionClipBounds`
      - `0x00ad5cd0` -> `GetActiveWaterReflectionBucketState`
      - `0x00ad7ef0` -> `ExecuteWaterReflectionBucketPass`
      - `0x00ad88d0` -> `FlushQueuedWaterReflectionGeometry`
      - `0x00ad8960` -> `QueueWaterReflectionQuad`
      - `0x00ad9130` -> `QueueWaterReflectionGridStrip`
      - those functions all use the follow-on reflection runtime object at `DAT_01550ea4`
      - the pass structure is tighter now:
        - one helper selects the active per-bucket state block
        - one helper refreshes the active clip rectangle
        - the pass then iterates bucket ranges, emits clipped quads / grid strips, and flushes queued geometry
      - caller comparison now shows `DAT_01550ea4` is a generic mapped geometry-buffer helper:
        - virtual `+0x04` prepares writable storage
        - byte `+0x06` is the mapped/ready flag
        - dword `+0x08` is the writable base pointer
        - virtual `+0x10` finalizes or submits the queued records
      - a broader deferred-render pass now shows this lane is attached to the deferred G-buffer / stencil infrastructure:
        - `InitializeDeferredGBufferRenderTargets` creates `_DEFERRED_GBUFFER_1_`, `_DEFERRED_GBUFFER_2_`, `_DEFERRED_GBUFFER_3_`, and `_STENCIL_BUFFER_`
        - `GetDeferredGBuffer3RenderTarget` returns `DAT_0154e17c`
        - `GetDeferredStencilBufferRenderTarget` returns `DAT_0154e180`
        - `AssignDeferredRenderTargetSlot` publishes deferred targets into slot globals `DAT_0154e230 .. DAT_0154e240`
        - `InitializeDeferredRenderTargetCallbackBindings` registers callback wrappers around:
          - `InitializeDeferredLightingCompositeState`
          - `AssignDeferredRenderTargetSlot` for slots `0 .. 4`
          - `ResetDeferredLightingCompositeState`
        - `InitializeDeferredLightingShaderHandles` also resolves `ParabTexture`, `ReflectionParams`, and `depthSourceTexture`
      - `InitializeSceneRenderCallbackBindings` registers `ExecuteWaterReflectionBucketPass` as a generic 1-arg callback bound to the live bucket/pass index from `ECX + 0x938`
      - the two surrounding static records centered on `0x00ea7014` and `0x00eea1a4` still have no defended direct scalar/data readers
      - `UpdateSceneRenderBindingViewState`, `ExecuteDeferredSceneBindingRecord`, and `ExecuteDeferredRenderTargetBindingRecordIfEnabled` now make those records look like opaque phase/descriptor tables rather than loose one-off globals
      - `ExecuteDeferredSceneBindingRecord` is now better understood as the generic runtime-object materializer for those records:
        - it first builds one owner-selected object through owner virtual `+0x24` and `InitializeNewDrawListCommand` (`CNewDrawListDC`)
        - it then materializes four descriptor-owned objects from `ECX + 0x224` through `InitializeLockRenderTargetCommand` (`CLockRenderTargetDC`)
        - after owner virtual `+0x20`, it materializes four more descriptor-owned objects from `ECX + 0x230` in reverse order through `InitializeUnlockRenderTargetCommand` (`CUnLockRenderTargetDC`)
        - when the record flag at `ECX + 0x1c` is set, it also builds an extra `0x410`-byte owner-derived object from `ECX + 0x2c`
        - adjacent helpers are clearer too:
          - `InitializeSetCurrentViewportCommand` restores the active viewport
          - `InitializeEndDrawListCommand` closes the draw list
          - `InitializeDrawMobilePhoneCameraCommand` is the isolated large owner-derived command object
      - the reflection-phase records themselves are now much more explicit:
        - the water-reflection record centered on `0x00eec400` carries `DeleteWaterReflectionRenderPhase`, `ExecuteConditionalWaterReflectionScenePassBinding`, `ExecuteDeferredSceneBindingRecord`, `ExecuteWaterReflectionPhaseStateCallbacks`, `GetWaterReflectionRenderPhaseId`, and `EvaluateRenderPhaseBit9Predicate`
        - the mirror-reflection record centered on `0x00eec448` carries `DeleteMirrorReflectionRenderPhase`, `ExecuteOverrideStateWaterReflectionScenePassBinding`, `ExecuteDeferredSceneBindingRecord`, `ExecuteMirrorReflectionPhaseStateCallbacks`, `GetMirrorReflectionRenderPhaseId`, and `GetBoundPhaseRenderTargetState`
        - `InitializeRenderPhaseBase` is the shared constructor for the family:
          - it zeroes the command-entry blocks at `ECX + 0x224 .. 0x233`
          - seeds the phase-control fields at `+0x23d/+0x23e/+0x23f/+0x24e`
          - optionally binds the source object passed through `param_1 + 0x10`
        - the concrete derived constructors are now identified too:
          - `InitializeMirrorReflectionRenderPhase` seeds `CRenderPhaseMirrorReflection::vftable`, allocates and back-links a companion object through `ECX + 0x940`, publishes `DAT_0166da10 = this`, and sets phase mode `ECX + 0x40 = 2`
          - `InitializeWaterReflectionRenderPhase` seeds `CRenderPhaseWaterReflection::vftable`, sets flag block `ECX + 0x8d0 = 0x232d20`, phase kind `ECX + 0x8f4 = 3`, and phase mode `ECX + 0x40 = 2`
          - `InitializeWaterSurfaceRenderPhase` seeds `CRenderPhaseWaterSurface::vftable`, sets flag block `ECX + 0x8d0 = 0x20000`, phase kind `ECX + 0x8f4 = 3`, and clears `ECX + 0x940/+0x944`
          - `InitializeRainUpdateRenderPhase` seeds `CRenderPhaseRainUpdate::vftable`, clears `ECX + 0x8d0`, and sets phase kind `ECX + 0x8f4 = 2`
        - the mirror-phase singleton bridge is now explicit:
          - `DAT_0166da10` is only written by `InitializeMirrorReflectionRenderPhase` and `ReleaseMirrorReflectionPhaseState`
          - the only recovered read is from `ExecuteWaterReflectionPhaseStateCallbacks`
          - so the water/mirror interaction is now local to this phase family, not a broad unidentified engine global
        - the phase owner/orchestrator is now identified too:
          - `InitializeSceneRenderPhasesFromFeatureFlags` allocates and registers the concrete `CRenderPhase*` objects from the live feature-flag word at `ECX + 0x40c/0x414`
          - it directly instantiates:
            - `InitializeMirrorReflectionRenderPhase` when bit `0x40` is present
            - `InitializeWaterReflectionRenderPhase` and `InitializeWaterSurfaceRenderPhase` when bit `0x20000` is present
            - the reflection-map phase pair through `FUN_00d50ea0` and `FUN_00d50e10` when bit `0x40000` is present
            - `InitializeRainUpdateRenderPhase` near the end of the phase setup
          - it also allocates the render targets that those phases bind through `FUN_00a87db0` / `FUN_00a87dd0`
        - the follow-on publish/finalize step is now identified too:
          - `0x005d49d0` is a thin wrapper that calls `InitializeSceneRenderPhasesFromFeatureFlags` and then `FinalizeSceneRenderPhaseSlots`
          - `FinalizeSceneRenderPhaseSlots` walks the active phase-slot array, allocates one wrapper object per active slot, stores that wrapper at `phase + 8`, and publishes it through `FUN_00b007d0`
          - the remaining data xref at `0x00e93f70` is only a static table entry containing `InitializeSceneRenderPhasesFromFeatureFlags` among other generic slots
        - `ReleaseRenderPhaseBaseState` is the shared `CRenderPhase` teardown path
        - `ReleaseMirrorReflectionPhaseState` clears the mirror-specific bound target at `ECX + 0x940` before falling back to the shared base-state release
      - practical implication:
        - the runtime does already turn these typed phase descriptors into live objects
        - the object layer is generic draw-list / render-target / viewport command assembly, not a hidden reflection-only command family
        - the remaining uncertainty is no longer the phase-record layout or the concrete phase constructors either
        - the remaining gap is now only the higher-level runtime relationship between this concrete `CRenderPhase*` family and the separately registered `rage__ProceduralReflection` module, not whether the reflection-phase object system or its publish chain exists
      - the live water-reflection controller chain is now defended too:
        - `ExecuteWaterReflectionScenePass` is the common scene-pass wrapper reached from multiple opaque callback records centered on `0x00ea7000`, `0x00eea150`, `0x00eec400`, and `0x00eec448`
        - the queue-building path beneath it is now explicit:
          - `BuildWaterReflectionPassQueues`
          - `TraverseWaterReflectionVisibilityTree`
          - `QueueWaterReflectionIndexedPrimitive`
          - `ResetWaterReflectionPassQueues`
        - follow-up sibling-pass probing shows the shared helper under those callbacks is generic:
          - the paired callback at `0x00b59900` is a post-pass cleanup/state routine
          - `0x00b1dee0` is reused by several unrelated render passes with different state masks
          - the next helper down, `0x00c0fa50`, is also generic render-pass finalization/state handling rather than a reflection-specific bridge
          - so the remaining reflection-specific gap is still downstream of the wrapper family, not inside that shared helper
      - `FUN_00abd480` consumes the reflection-occlusion globals `DAT_0154e278` / `DAT_0154e27c`, but no defended direct writes or immediate/scalar references to those globals were recovered
      - non-water callers such as `0x0092f570` and `0x009303d0` still use that deferred stencil getter
      - that means the object itself is no longer the hard unknown, and the controller layer is no longer missing either
      - the remaining gap is now the engine-runtime relationship between this callback-driven deferred reflection lane and the separately registered `rage__ProceduralReflection` module
      - the newest evidence suggests both sides, plus the reflection parameter/occlusion state, are still being consumed through opaque indirect registration/table traversal rather than through missed obvious direct callers
      - `0x0065bcb0` proves the interval-shadow side exposes a `SphericalAmbientOn` capability test
      - a deeper pass over the local `0x0062d0a0 .. 0x0062e470` code island tightened the local-method boundary too:
        - `0x0062d0a0` is still the only substantial local body that looks procedural-reflection-specific
        - the larger neighboring functions in that band mostly resolve to `ProceduralTextureSkyhat` default/release plumbing, `SkyhatPerlinNoise`, minisky render-resource setup, `SkyMapTexture` passes, and `VerletWaterSimulation` type/init helpers
        - that means the missing bridge is even less likely to be hiding in the immediate local neighborhood of `InitializeProceduralReflectionDefaults`
        - a follow-up scalar/table sweep tightened it again:
          - `0x0062d0a0` has no defended scalar or defined-data hits anywhere in the image
          - the local vftable value `0x00fe24e0` is only written by the constructor/destructor pair
          - so the remaining bridge is even more likely to be late generic runtime dispatch than a missed stored local callback
  - that makes `rage__ProceduralReflection` the strongest modern-build candidate for the game-side host object or controller that the legacy trampoline forwards through `DAT_10158060`
  - current-build probing also recovered the adjacent interval-shadow path:
    - `0x0065bf20` registers `rage__IntervalShadows`
    - `0x0065de10` creates the runtime interval-shadows object
    - `0x0065b5c0` allocates the `rage::IntervalShadows::IntervalShadowVarCache`
    - `0x0065d2b0` initializes the interval-shadow / ambient-occlusion parameter indices in that cache
    - the interval-shadows registration record is instead wired into the separate startup chain rooted at `DAT_017ad1b8`
  - that interval-shadow module is useful render context, but currently looks adjacent to the reflection feature rather than identical to the host object the legacy hook drives
- the IVMenu cache layouts are now materially better pinned down:
  - cached option descriptors at `DAT_10024000` now have defended getter/setter, identifier, token/value, value-filter, display string, scaler, and callback-context fields
  - cached enum descriptors at `DAT_10030120` now have defended identifier, `begin/end/capacity` ANSI-label vector, `begin/end/capacity` localized/wide-label vector, and match-flag fields
  - `DAT_10030120` itself is a fixed `0x7f`-entry static descriptor array built by `_eh_vector_constructor_iterator_`
  - the remaining uncertainty in those records is now mostly limited to low-level string-object implementation detail, not the container semantics
  - the runtime/bootstrap side is also clearer:
    - `DetectSupportedMenuHostVariant` selects one of two observed host variants
    - `ResolveAndInstallMenuHostCallbacks` materializes the callback slots used by reload, publication, and menu-definition interception
    - `LoadAndPatchIVMenuDefinitionAndResume` is the actual interception stub that feeds patched menu definitions back into the host
- the secondary helper layer under the custom hook blobs is tighter now:
  - `RegisterDetectedEpisodeContentRoots` enumerates installed `TLAD`, `TBoGT`, and `Episode%d` content roots for the DLC loader path
  - `ClampBikePhysicsDeltaFloor` enforces the minimum timestep used by the bike-physics clamp trampoline
  - `UpdateIsPlayerModelSelectionFlag` and `EnsurePlayerUsesPedModel` now explain the player-model safety path behind the component/crash fixes
  - `FlagInvalidReportedVramState` and `RebuildNewSaveCompatibilityPath` now describe the real validation/rebuild logic under the VRAM and new-save compatibility hooks
  - `ApplyLegacyModCheckAndPopupBypasses` is no longer an anonymous helper under the legacy mod-check / GFWL popup hook
- the raw feature matrix definitely contains heuristic misattributions:
  - `HasFusionFixConflictForLegacyPatch`
  - `ClearManagedString`
  - `GetConfigValueStringOrDefault`
  are helpers, not feature installers.
- the verified semantic matrix is now in `zolikapatch_feature_matrix_verified.tsv`:
  - `70` rows are real verified installers
  - `2` rows are config-value consumers
  - `1` row is a direct external WinAPI call
  - `4` rows are confirmed helper/gate misattributions from the raw matrix
  - this means the installer-coverage problem is effectively solved at the top level

Custom blob helper pass summary:

- many `UNK_*` hook targets were actually `INT3` padding immediately before real helper code
- the real helper bodies have now been isolated and decompiled in `custom_blob_helper_notes.md`
- resolved helper mappings now include:
  - `UNK_100057eb` -> `0x100057f0`
  - `UNK_1001ef5b` -> `0x1001ef60`
  - `UNK_1003d99b` -> `0x1003d9a0`
  - `UNK_1004aaab` -> `0x1004aab0`
  - `UNK_1005048b` -> `0x10050490`
  - `UNK_100515fb` -> `0x10051600`
  - `UNK_100521db` -> `0x100521e0`
- this removes the biggest remaining source of â€œopaque hook blobâ€ uncertainty in the installer layer

- a follow-up xref pass now also classifies the remaining helper bodies behind:
  - `InstallBuildingDynamicShadowsPatch`
  - `InstallEpisodeSwitchFreezeFixPatch`
  - `InstallLoadDLCsPatch`
  - `InstallGroupHackFixPatch`
  - `InstallBlackscreenFixPatch`
  - `InstallFreezeGunFixPatch`
  - `InstallBadPlayerModelCrashFixPatch`
  - `InstallPathNodeCrashFixPatch`
  - `InstallPlaneCrashFixPatch`
  - `InstallPedModelsInCutscenesFixPatch`
- see `custom_blob_helper_notes.md` and `remaining_helper_xrefs.txt`

## Clean-Room Direction

To make this open and portable to the latest GTA IV build, the replacement should be split into:

- `loader/bootstrap`
  - config parsing
  - logging
  - version detection
- `address resolver`
  - signatures for each supported executable build
  - symbolic names for matched addresses
- `feature patch modules`
  - one module per toggle such as vehicle limits, skip intro, skip menu, teleport protection
- `menu bridge`
  - either open replacement for `IVMenuAPI` or a compatibility layer

## Current Build Progress

The current Steam build has now been imported into the same Ghidra workspace:

- `GTAIV.exe`
- version `1.2.0.59`

First confirmed current-build landmark:

- `0x00a7b080` -> `InitializeVehicleStructPool`
  - creates/initializes `VehicleStruct`
  - contains the legacy `PUSH 0x32` site matched by the old `IncreaseVehicleStructLimit` patch
  - this is the first feature with a strong direct carry-over candidate on the modern build
- the modern reflection side is now much tighter too:
  - the separately registered `rage__ProceduralReflection` module is still useful subsystem-level reflection context in the modern executable
  - the live deferred reflection lane is now defended as a phase-descriptor system built around `ExecuteWaterReflectionScenePass` plus record-local wrappers such as `ExecuteInheritedWaterReflectionScenePassBinding`, `ExecuteAngularWaterReflectionScenePassBinding`, `ExecuteConditionalWaterReflectionScenePassBinding`, and `ExecuteOverrideStateWaterReflectionScenePassBinding`
  - RTTI recovered from the record-owned data now shows those descriptors are typed callback/render-phase objects, including `.?AV?$T_CB_Generic_4Args@P6AXAAVVector4@rage@@MMM@ZV12@MMM@@`, `.?AV?$T_CB_Generic_1Arg@P6AXAAVMatrix44@rage@@@ZV12@@@`, `.?AVCRenderPhaseWaterReflection@@`, and `.?AVCRenderPhaseMirrorReflection@@`
  - base-descriptor RTTI now also confirms a shared `.?AVCRenderPhase@@` base for the mirror-reflection phase class
  - inferred water/mirror phase-vtable regions still have no defended scalar/data-table exposure, so the selector/orchestrator is not surfacing as a simple static vtable dispatch table
  - some of the record-owned code entries are now classified too:
    - `GetGenericRenderPhaseId0x1f` returns `0x1f`
    - `GetWaterReflectionRenderPhaseId` returns `0x11`
    - `GetMirrorReflectionRenderPhaseId` returns `0x24`
    - `DestroyGenericCallbackWrapper` is shared cleanup, not hidden reflection logic
  - the phase manager side is now explicit too:
    - `InitializeSceneRenderPhasesFromFeatureFlags` directly instantiates `InitializeReflectionRenderPhase` and `InitializeInteriorReflectionRenderPhase` under feature bit `0x40000`
    - `InitializeAndFinalizeSceneRenderPhases` then wraps and publishes those live phase objects through `FinalizeSceneRenderPhaseSlots`
  - practical conclusion:
    - the direct current-build runtime target for the legacy high-quality-reflections feature is now the `CRenderPhaseReflection` / `CRenderPhaseInteriorReflection` family under the scene phase manager
    - any tighter architectural relationship to `rage__ProceduralReflection` is now adjacent engine context rather than a blocker for understanding or implementation

## Remaining Current-Build Context Work

- Recover more of the surrounding engine-side semantics for the host render object referenced through `DAT_10158060`.
- Recover field-level names for the reflection callback record rooted at `0x10190ac0` and its scratch frame around `DAT_10159ce0`.





