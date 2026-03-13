# Custom Blob Helper Notes

This pass focused on the custom code targets that installer functions jump into.

Important finding:

- many `UNK_*` hook targets were not true function starts
- they were `INT3` padding bytes immediately before the real helper body
- the real helper code usually starts at the next aligned address

Raw artifacts from this pass:

- `custom_blob_target_functions_work.tsv`
- `custom_blob_orphan_asm_work.txt`
- `custom_blob_real_func_addrs.txt`
- `custom_blob_real_funcs_decompile_work.txt`
- `remaining_helper_xrefs.txt`
- `remaining_helper_owner_funcs_decompile.txt`

## Resolved Hook Targets

- `UNK_100057eb` -> `0x100057f0`
  - trivial hook stub that always returns `0`
  - used by `InstallBetterMPSyncPatch`

- `UNK_1001ef5b` -> `0x1001ef60`
  - stores the incoming float argument into a global scratch slot
  - calls `ClampBikePhysicsDeltaFloor`, which clamps the value against a fixed minimum timestep
  - restores the possibly-adjusted float argument and resumes original execution
  - this is the core helper behind `InstallHighFPSBikePhysicsFixPatch`

- `UNK_1003d99b` -> `0x1003d9a0`
  - saves `EAX` into `DAT_10159d28`
  - calls `UpdateIsPlayerModelSelectionFlag`
  - resumes through the saved original continuation in `DAT_10159d2c`
  - this is the trampoline body for `InstallPlayerAsPedComponentsFixPatch`

- `UNK_1004aaab` -> `0x1004aab0`
  - captures two stack arguments into globals
  - calls `FlagInvalidReportedVramState`
  - if the helper marked the result invalid, forces the returned value to `0x7fffffff`
  - this is the custom logic core of `InstallVRAMFixPatch`

- `UNK_1005048b` -> `0x10050490`
  - null-checks the object pointer
  - calls the virtual method at vtable offset `0x128` when present
  - resumes through the saved continuation target in `DAT_1015ad6c`
  - this is the hook body for `InstallWantedLevelCrashFixPatch`

- `UNK_100515fb` -> `0x10051600`
  - thin wrapper around `FUN_100515e0`
  - `FUN_100515e0` registers callback `FUN_100515d0` under hash `0x2c89a1f9`
  - used by `InstallNewFilesCompatibilityPatch`

- `UNK_100521db` -> `0x100521e0`
  - calls `RebuildNewSaveCompatibilityPath`
  - resumes through the saved continuation in `DAT_1015ad88`
  - used by `InstallNewSavesCompatibilityPatch`

## Compatibility Shim Helpers

These look like support shims rather than game-behavior patches:

- `0x10045580`
  - closes a `HANDLE` and returns `0`

- `0x100455a0`
  - copies a source buffer into an output buffer if the destination is large enough
  - otherwise writes the required size and returns `0x8007007a`

- `0x100455e0`
  - creates an anonymous mutex and writes the handle through an out-parameter

These helpers are used by `InstallSavegameFixPatch` and appear to emulate or replace expected platform/runtime behavior.

## Small Trampoline Helpers

- `0x10036f00`
  - fetches a string from a saved callback
  - replaces all `~` characters with spaces in-place
  - resumes no caller directly; this is a full helper routine

- `0x10046780`
  - copies a field from `object+0x10` into `object->child+0x10` when the child pointer is valid
  - resumes through `DAT_10159c6c`
  - used by the launcher/platform patch cluster

- `0x100054e0`
  - calls `ApplyLegacyModCheckAndPopupBypasses`
  - adjusts the `EAX:EDX` result pair against preserved registers
  - resumes through `DAT_10159c18`
  - tied to `InstallLegacyModCheckOrGFWLPopupHook`

- `0x10005650`
  - guarded callback/trampoline helper
  - if `EAX != 0`, calls the callback in `ESI` and resumes through `DAT_1015ad50`
  - otherwise returns `0`

- `0x10005670`
  - same shape as `0x10005650`, but resumes through `DAT_1015ad54`
  - both are used by `InstallBenchmarkFixPatch`

## Xref-Based Helper Classification

After materializing the unresolved blob bodies in the main project and dumping their callers, the remaining anonymous helpers now map cleanly to installer clusters:

- `0x10005af0` -> `RouteBuildingShadowContinuationByState`
  - used by `InstallBuildingDynamicShadowsPatch`
  - compares the tracked state value against two captured shadow-state constants and routes to one of two saved continuations

- `0x1001b060` -> `ZeroOutputByteIfEpisodeSwitchContextMissing`
  - used by `InstallEpisodeSwitchFreezeFixPatch`
  - if the episode-switch context is absent, writes zero to the output byte instead of resuming the original path

- `0x1001b080` -> `SumEpisodeRangeFieldsOrZero`
  - used by `InstallEpisodeSwitchFreezeFixPatch`
  - returns the sum of two structure fields when the episode data pointer is valid, otherwise returns zero

- `0x1001b0a0` -> `CallEpisodeResourceVirtual0x30IfPresent`
  - used by `InstallEpisodeSwitchFreezeFixPatch`
  - null-checks the wrapper object, then calls the nested virtual method at vtable offset `0x30`

- `0x1001b0c0` -> `ResumeEpisodeSwitchCheckIfContextPresent`
  - used by `InstallEpisodeSwitchFreezeFixPatch`
  - resumes the saved continuation only when the context pointer is non-null, otherwise returns zero

- `0x10023480` -> `ReturnTrueForLoadDlcsCheck`
  - used by `InstallLoadDLCsPatch`
  - trivial helper that always returns true

- `0x10023490` -> `ClearDlcSelectionSlotsAndMarkReloadState`
  - used by `InstallLoadDLCsPatch`
  - clears several slot bytes spaced `0x40` apart, then either calls the saved continuation and `RegisterDetectedEpisodeContentRoots` or marks a deferred reload flag

- `0x10036e90` -> `SuppressGroupHackCaseEight`
  - used by `InstallGroupHackFixPatch`
  - suppresses the original continuation when the incoming case value is exactly `8`

- `0x10036eb0` -> `ReturnZeroForInvalidFreezeGunOwner`
  - used by `InstallFreezeGunFixPatch`
  - checks the owner/state field at offset `0x40` for invalid sentinel values and returns zero instead of continuing on bad state

- `0x10036ee0` -> `SkipBlackscreenContinuationForTrackedRange`
  - used by `InstallBlackscreenFixPatch`
  - suppresses the original continuation when the tracked operand value falls within the patched range `8..10`

- `0x10037ba0` -> `PreservePlayerModelFieldsAndResume`
  - used by `InstallBadPlayerModelCrashFixPatch`
  - preserves two stack-frame model fields across `EnsurePlayerUsesPedModel`, restores one field, then resumes the original path

- `0x1003d7b0` -> `RoutePathNodeSentinelCaseAndResume`
  - used by `InstallPathNodeCrashFixPatch`
  - checks the candidate value against sentinel `0x1fffdf` and routes to one of two saved continuations

- `0x1003d870` -> `WritePlaneFlagByteIfObjectPresent`
  - used by `InstallPlaneCrashFixPatch`
  - writes the incoming flag byte to offset `0x26` when the object pointer is valid, otherwise branches to the fallback continuation

- `0x10050870` -> `ComparePedModelIdAgainstPlayerLookup`
  - used by `InstallPedModelsInCutscenesFixPatch`
  - resolves the `"player"` lookup through the saved callback and compares the returned id against the model id at offset `0x2e`

## Remaining Ambiguity

Additional callee recovery from this pass:

- `0x10005340` -> `ApplyLegacyModCheckAndPopupBypasses`
  - zeroes one pattern-resolved mod-check operand and rewrites four fixed popup/mod-check immediates when the related feature flags are enabled
- `0x1001ef30` -> `ClampBikePhysicsDeltaFloor`
  - forces the captured bike-physics timestep up to the minimum constant `0.006666667`
- `0x10023150` -> `RegisterDetectedEpisodeContentRoots`
  - scans for installed `TLAD`, `TBoGT`, and generic `Episode%d` content roots under the game directory and registers each discovered root through saved callbacks
- `0x10037ac0` -> `EnsurePlayerUsesPedModel`
  - logs the non-ped case, forces the fallback `"m_y_multiplayer"` model when needed, and refreshes the cached player-model index
- `0x1003d930` -> `UpdateIsPlayerModelSelectionFlag`
  - compares the current ped model id against the `"player"` lookup and sets `DAT_10159d20` accordingly
- `0x1004aa80` -> `FlagInvalidReportedVramState`
  - marks the current VRAM report invalid when the saved values are negative, explicitly flagged invalid, or below the accepted minimum
- `0x10051fd0` -> `RebuildNewSaveCompatibilityPath`
  - lazily resolves one callback, then rebuilds the compatibility path by appending the discovered suffix in `DAT_1015ad9c` onto the managed base path before handing it to the downstream save helper

The helper layer is now largely named, but a few names still remain behavioral rather than source-equivalent:

- `RouteBuildingShadowContinuationByState`
- `SumEpisodeRangeFieldsOrZero`
- `CallEpisodeResourceVirtual0x30IfPresent`
- `ResumeEpisodeSwitchCheckIfContextPresent`

Those helpers are no longer opaque, but they still need deeper data-structure recovery if we want names that match the original game semantics instead of the observed control flow.

Fresh headless re-decompilation of these helpers in the current project state did not reveal any additional hidden calls, fields, or preserved-register usage beyond what is already captured above. The remaining ambiguity is therefore game-side structure meaning, not missing ASI-side helper recovery.

