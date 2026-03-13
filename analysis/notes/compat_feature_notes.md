# Compatibility / Save / Startup Feature Notes

These functions are the file, startup, save, and compatibility-side patch installers in `ZolikaPatch.asi`.

Raw decompile:

- `compat_feature_funcs_decompile.txt`

## Recovered Semantics

- `0x10022cf0` -> `InstallAllowHashesForIDEPatch`
  - Replaces a matched site with a jump into custom code at `0x10003e4b`.
  - This is not a raw NOP patch; it is custom validation/rewrite logic.

- `0x100234e0` -> `InstallLoadDLCsPatch`
  - Large version-specific patch set for build ids `7`, `8`, and `9`.
  - Installs several hooks and NOP blocks, and stores multiple loader-related addresses in globals.
  - This is clearly one of the central content-loading compatibility patches.

- `0x1003d670` -> `InstallNoLoadingSleepPatch`
  - Replaces a hardcoded `0x1388` style sleep argument with zero.
  - This is a pure latency-removal patch.

- `0x10045610` -> `InstallSavegameFixPatch`
  - Multi-site custom hook patch.
  - Redirects several code paths into helper blobs at `0x1004557b`, `UNK_1004559b`, `UNK_100455db`, and `UNK_1003856b`.
  - This is a substantial save/load behavior fix, not a one-byte patch.

- `0x10045980` -> `InstallSCOSignatureFixPatch`
  - Converts one conditional branch into an unconditional jump.
  - This is a localized signature/validation bypass.

- `0x100468d0` -> `InstallStartupTimeFixPatch`
  - Bulk-rewrites multiple matched pointer/immediate references to `PTR_DAT_101580d8`.
  - This is a table-style startup patch rather than a single hook.

- `0x100503b0` -> `InstallVSyncFixPatch`
  - Version-gated patch that replaces two pointer/immediate operands with `DAT_10159bc0`.
  - This looks like redirection to a shared configuration/state object for VSync behavior.

- `0x100506b0` -> `InstallSpeedupBugFixPatch`
  - Hooks a large function into custom code at `0x1005053b`.
  - Captures many original immediates/addresses into globals first.
  - Strong signal this is a stateful runtime fix, not a branch patch.

- `0x100508c0` -> `InstallPedModelsInCutscenesFixPatch`
  - Rewrites a function entry into custom code at `UNK_1005086b`.
  - This is a custom logic override for cutscene model handling.

- `0x100513a0` -> `InstallLocalSettingsPathPatch`
  - Captures the current working directory in ANSI and wide forms.
  - Stores those strings in managed globals, then patches file/path call sites and redirects one final branch into custom code.
  - This is a real path redirection subsystem, not just one filename swap.

- `0x10051920` -> `InstallNewFilesCompatibilityPatch`
  - Guarded against `GTAC.dll` and `JacksIVFixes.asi`.
  - If those are absent, installs several hooks into custom code at `UNK_100515fb`, `0x1005188b`, and `0x100518cb`.
  - This is a compatibility shim intended not to overlap with other already-installed mods.

- `0x10052200` -> `InstallNewSavesCompatibilityPatch`
  - Stores one discovered game pointer in `DAT_1015ad9c`, then hooks another save-related site into `UNK_100521db`.
  - This is a focused compatibility hook around save serialization/loading.

- `0x10052380`
  - Current decompile shows a loader/helper that ensures `IVMenuAPI.asi` is present, loading it on demand if needed.
  - This does not yet match the `RestoreDeathMusic` feature name cleanly, so it should stay unrenamed until its call context is verified.

- `0x10050bd0` -> `InstallRestoreDeathMusicPatch`
  - Verified follow-up installer behind the `RestoreDeathMusic` config path.
  - Rewrites three matched call sites into local helpers at `0x10050a50` and `0x10050ad0`.
  - Uses `DAT_10159b86` as the runtime/menu state flag.
  - The helper at `0x10050a50` is the state gate, and the helper at `0x10050ad0` applies the variant-byte patch when the gate passes.

## Cluster Takeaway

This cluster contains many of the "hard" portability features because they are not just branch flips:

- DLC/content loading
- save/load compatibility
- local settings path redirection
- startup timing
- new file/save compatibility shims

## RE Follow-up

- identify the custom code behind:
  - `0x10003e4b`
  - `0x1004557b`
  - `UNK_1004559b`
  - `UNK_100455db`
  - `0x1005053b`
  - `UNK_1005086b`
  - `UNK_100515fb`
  - `0x1005188b`
  - `0x100518cb`
- recover the helper body used by `InstallRestoreDeathMusicPatch` near `0x10050a4b`
  - the displaced call targets now resolve to `0x10050a50` and `0x10050ad0`
