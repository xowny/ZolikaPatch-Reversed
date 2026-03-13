# Remaining Feature Matrix Cleanup Notes

This pass was used to separate true remaining feature installers from heuristic misattributions in the raw feature matrix.

Raw decompile:

- `remaining_feature_funcs_decompile.txt`

## Newly Recovered Real Feature Installers

- `0x10005800` -> `InstallBetterMPSyncPatch`
  - Rewrites one call site into custom code at `UNK_100057eb`.

- `0x1001ede0` -> `InstallDoNotPauseOnMinimizePatch`
  - Calls a helper setup routine, then changes a hardcoded byte from `0x0A` to `0x06`.
  - This is a small config/behavior tweak rather than a hook.

- `0x1001efa0` -> `InstallHighFPSBikePhysicsFixPatch`
  - Hooks a bike-physics related floating-point path into `UNK_1001ef5b`.

- `0x10022d80` -> `InstallInfiniteCarBottomDistancePatch`
  - Changes a conditional branch to unconditional flow and adjusts the next byte to `0x2a`.
  - This is a localized threshold/gating patch.

- `0x10023080` -> `InstallIncreasePtrNodesPatch`
  - Rewrites three allocation/count immediates:
    - `64000`
    - `320000`
    - `64000`
  - This is a clean capacity expansion patch.

- `0x10023b30` -> `InstallHighFPSSpeedupFixPatch`
  - Large branch/NOP patch around repeated floating-point compare paths.
  - This is a broad high-FPS behavior normalization patch, not a hook.

- `0x10023dd0` -> `InstallMiscFixesPatch`
  - Large bucket patch set with many branch flips, NOPs, early-return forcing, and value rewrites.
  - Guarded by `DAT_10159b84 == 0`.
  - This is exactly the kind of grouped "misc fixes" feature the INI label suggested.

- `0x1003d9c0` -> `InstallPlayerAsPedComponentsFixPatch`
  - Hooks one matched site into `UNK_1003d99b`.

- `0x10044bd0` -> `InstallRemoveUselessChecksPatch`
  - Large version-specific patch bucket.
  - Applies many NOP blocks, unconditional branch rewrites, forced-return byte sequences, and table-driven patch loops.
  - This is a real grouped patch feature, not helper noise.

- `0x10052380` -> `EnsureIVMenuApiLoaded`
  - Helper that checks whether `IVMenuAPI.asi` is already loaded and loads it on demand.
  - This is not the real `RestoreDeathMusic` patch body.

- `0x10050bd0` -> `InstallRestoreDeathMusicPatch`
  - Real installer behind the `RestoreDeathMusic` config key.
  - Rewrites three matched call sites into local helper code.
  - Uses `DAT_10159b86` as the feature state flag and exposes that state through IV menu callbacks.

## Raw Matrix Misattributions Confirmed

These are not feature installers and should not be treated as unresolved patch functions:

- `0x10003e20` -> `HasFusionFixConflictForLegacyPatch`
  - conflict detector used by `SkipIntro`, `SkipMenu`, and `BorderlessWindowed`
- `0x1004ac90` -> `ClearManagedString`
  - string helper, not `Patch7RemoveModChecks`
- `0x1005c160` -> `GetConfigValueStringOrDefault`
  - config helper, not `FreezeCarFix` or `Patch6and7RemoveModChecks`

## Blob Helper Progress

The major custom blob targets are no longer opaque address stubs.

- `UNK_100057eb` resolves to a real helper at `0x100057f0`
- `UNK_1001ef5b` resolves to the bike-physics clamp helper at `0x1001ef60`
- `UNK_1003d99b` resolves to the player-component trampoline at `0x1003d9a0`
- `UNK_1004aaab` resolves to the VRAM clamp helper at `0x1004aab0`
- `UNK_1005048b` resolves to the wanted-level crash guard at `0x10050490`
- `UNK_100515fb` resolves to the new-files callback wrapper at `0x10051600`
- `UNK_100521db` resolves to the new-saves continuation helper at `0x100521e0`
- the secondary helper layer beneath those blobs is now tighter too:
  - `RegisterDetectedEpisodeContentRoots`
  - `ClampBikePhysicsDeltaFloor`
  - `UpdateIsPlayerModelSelectionFlag`
  - `FlagInvalidReportedVramState`
  - `RebuildNewSaveCompatibilityPath`
  - `EnsurePlayerUsesPedModel`
  - `ApplyLegacyModCheckAndPopupBypasses`

See `custom_blob_helper_notes.md` for the full breakdown.

## What Is Actually Still Missing

At this point, the remaining hard RE work is mostly:

- deeper recovery of the IVMenuAPI cache/bootstrap state behind `PopulateIVMenuRegistrationCaches`
- tighter naming around the XML/menu-definition patch helper now isolated at `LoadAndPatchIVMenuDefinition`
- deeper data-structure recovery for the few helper names in `custom_blob_helper_notes.md` that are still behavioral rather than source-equivalent
- engine-side recovery of the reflection context object rooted at `0x10190ac0` beyond the now-resolved resolver/trampoline/reset slots


