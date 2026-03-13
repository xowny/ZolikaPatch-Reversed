# Implementation Guide

This note is the shortest useful entry point for anyone implementing the recovered ZolikaPatch behavior in an open codebase.

## Short Answer

The Markdown and TSV notes in this workspace should be enough for implementation planning.

The raw Ghidra project database should not be required if the handoff is based on:

- `reimplementation_status.md`
- `zolikapatch_feature_matrix_verified.tsv`
- the per-cluster feature notes
- `current_build_signature_audit.md`

That is also the safer public-sharing route, because binary Ghidra databases can still contain analyst metadata and are not needed for implementation.

## What Is Already Recovered

- `IVMenuAPI.asi` is no longer a black box.
- Its exports, cache layout, registration flow, bootstrap path, and menu-definition patch path are documented.
- `ZolikaPatch.asi` is understood as a config-driven bootstrapper plus per-feature patch installers plus optional IV menu integration.
- The top-level feature installer map is effectively complete.
- `zolikapatch_feature_matrix_verified.tsv` currently resolves `70` verified installers, `2` config-value rows, `1` direct external WinAPI row, and `4` helper/gate misattribution rows from the raw matrix.

## Most Important Files

- `reimplementation_status.md`
  Best high-level status document. Read this first.

- `zolikapatch_feature_matrix_verified.tsv`
  Best top-level implementation backlog. Use this to map INI key -> real installer -> subsystem -> patch style.

- `helper_function_notes.md`
  Describes the common helper layer used by many installers: config reads, pattern scanning, the legacy conflict gate, and patch-writing helpers.

- `ivmenuapi_internal_notes.md`
  Documents `IVMenuAPI.asi` internals beyond exports. Important if the target project wants to preserve menu-facing behavior or expose equivalent toggles.

- `graphics_feature_notes.md`
  Important for the high-quality reflections path and the remaining engine-side reflection unknowns.

- `compat_feature_notes.md`
  Important for save/load/startup/path compatibility behavior.

- `stability_feature_notes.md`
  Important for crash-fix and gameplay-protection features implemented as custom hooks.

- `episode_vehicle_feature_notes.md`
  Important for episode-content unlock and vehicle/weapon support logic.

- `platform_feature_notes.md`
  Important for launcher/RGSC/GFWL-era behavior that likely should be omitted, replaced, or translated carefully inside the target project.

- `current_build_signature_audit.md`
  Useful reminder that this is not a simple signature-refresh job on GTA IV `1.2.0.59`.

## What An Open Port Likely Needs To Implement

- Treat the old ASI as a behavior reference, not as a patch template.
- Reimplement feature-by-feature against current GTA IV code paths.
- Prefer semantic ports over reproducing the old binary patch layout.
- Recreate menu toggles only where they still make sense inside the target project.
- Split legacy-launcher behavior from actual gameplay/render/save fixes.

## Recommended Porting Priority

- `IncreaseVehicleStructLimit`
  Strongest confirmed current-build carry-over so far.

- `IncreaseVehicleModelLimit`
  Small feature with clear intent.

- `SkipIntro`
  Straightforward user-visible behavior.

- `SkipMenu`
  User-visible, but current-build match still needs semantic confirmation.

- `HighQualityReflections`
  Worth porting, but only after the current reflection path is understood on the game side.

- Episode/content unlock features
  Useful, but they are broader multi-site behavior patches and should follow the smaller wins.

## What Is Still Not Fully Closed

- engine-side semantics of the host render/reflection object passed as `DAT_10158060`
- field-level naming for the callback/scratch structures around `0x10190ac0` and `DAT_10159ce0`
- a few helper names that are as far as ASI-only RE can take them, but still need game-side structure meaning
- full current-build semantic mapping for many features beyond the first confirmed carry-over landmark

## Practical Recommendation

If this package is being handed to implementers, ship the notes and TSV files first.

Do not make the Ghidra database a required dependency for understanding the work. The Markdown and TSV documents should stand on their own.
