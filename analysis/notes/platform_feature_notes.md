# Platform / Launcher Feature Notes

These functions are part of the "old PC build compatibility" side of `ZolikaPatch.asi`, not the gameplay/vehicle/rendering side.

Raw decompile:

- `platform_feature_funcs_decompile.txt`

## Recovered Semantics

- `0x10022f60` -> `InstallOverrideGTADatPathPatch`
  - Pattern-scans a code site that pushes a `gta.dat` path.
  - Replaces the immediate pointer with `s_common__data_gta_dat_10158068`.
  - This is the core hook behind `OverrideGTADatPath`, and it likely works together with the string override configured by `OverrideGTADatPathTo`.

- `0x10005500` -> `InstallLegacyModCheckOrGFWLPopupHook`
  - Shared hook used by both `Patch4RemoveModChecks` and `RemoveGFWLUpdatePopup`.
  - Installs a jump to custom code at `UNK_100054db`.
  - The exact runtime behavior is controlled by global flags set by the caller before this hook runs.
  - This is not just a single NOP patch; it is a shared trampoline for launcher-era checks.

- `0x10045490` -> `InstallRemoveRGSCLoginRequirementPatch`
  - Installs a jump to custom code at `UNK_1003854b`.
  - Has a pattern-based path and a version-9 fallback using fixed offsets.
  - Strong indicator this bypasses a Rockstar Games Social Club login gate in older PC builds.

- `0x100441b0` -> `RedirectLauncherTargetToPlayGTAIV`
  - Chooses version-specific offsets based on `DAT_1015be04`.
  - Overwrites embedded strings with `PlayGTAIV.exe`.
  - This is pure launcher/process-target redirection, not an engine behavior patch.

- `0x10045ce0` -> `InstallSkipLauncherPatch`
  - Large multi-site patch set.
  - Applies many NOPs, short-jump rewrites, and a few hardcoded instruction block replacements.
  - Clearly aimed at removing launcher flow and related validation/setup steps.
  - This is one of the most "legacy PC stack" oriented features in the mod.

- `0x10046b30` -> `InstallTryToSkipAllErrorsPatch`
  - Pattern-scans a function and changes its first byte to `RET`.
  - Simple hard bypass for an error path.

- `0x100467a0` -> `InstallSMPA60FixPatch`
  - Hooks two distinct code sites with jumps into custom code (`UNK_1004677b` and `0x1004674b`).
  - Stores nearby addresses in globals before redirecting execution.
  - This is a true custom-behavior patch, not just a constant tweak.
  - The exact semantic name is still tentative because the decompile shows the control-flow redirection but not yet the custom handler behavior.

## Why This Matters

This cluster confirms that a significant slice of ZolikaPatch is specifically about legacy GTA IV PC launch/runtime plumbing:

- old launcher flow
- Social Club / RGSC gating
- GFWL-era popup and mod-check behavior
- hardcoded `PlayGTAIV.exe` process names
- path indirection for `gta.dat`

That means these features should be tracked separately from:

- gameplay fixes
- streaming/memory limits
- rendering/shadow fixes
- episode/vehicle content support

## RE Follow-up

Still worth doing on this cluster:

- identify what `UNK_100054db`, `UNK_1003854b`, `UNK_1004677b`, and `0x1004674b` actually do
- map `DAT_1015be04` to concrete GTA IV build numbers in a formal note
- verify how `OverrideGTADatPathTo` feeds the path string used by `InstallOverrideGTADatPathPatch`
