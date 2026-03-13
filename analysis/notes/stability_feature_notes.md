# Stability / Crash / Gameplay Fix Feature Notes

These functions are mostly crash fixes, freeze fixes, and gameplay-protection fixes.

Raw decompile:

- `stability_feature_funcs_decompile.txt`

## Recovered Semantics

- `0x100055c0` -> `InstallAimProjectileCrashFixPatch`
  - Hooks a matched site into custom code at `0x1000558b`.

- `0x10005690` -> `InstallBenchmarkFixPatch`
  - Version-gated patch.
  - Hooks two matched benchmark-related call sites into `UNK_1000564b` and `UNK_1000566b`.

- `0x10005e30` -> `InstallCollisionDeadlockFixPatch`
  - Hooks a matched function entry into custom code at `0x10005d7b`.
  - Saves one original target address into `DAT_1015bdd4` first.

- `0x10005ee0` -> `InstallCombinedRadioFreezeFixPatch`
  - Rewrites one call target to custom code at `UNK_10005ecb`.

- `0x1001b0e0` -> `InstallEpisodeSwitchFreezeFixPatch`
  - Multi-site custom hook patch.
  - Rewrites five separate code sites into helper blobs from `0x1001b03b` through `0x1001b0bb`.
  - This is a substantial episode-switch state fix.

- `0x10036f30` -> `InstallGroupHackFixPatch`
  - Hooks one site into `UNK_10036e8b`.

- `0x10036fd0` -> `InstallBlackscreenFixPatch`
  - Hooks one site into `UNK_10036edb`.
  - Stores the original fall-through and operand values in globals first.

- `0x10037070` -> `InstallFreezeGunFixPatch`
  - Hooks one site into `UNK_10036eab`.
  - Stores one nearby original address in `_DAT_1015ad64`.

- `0x10037100` -> `InstallRemoveAllWeaponsProtectionPatch`
  - NOPs a 12-byte block after a jump site.
  - This is a hard protection bypass, not a hook.

- `0x100371f0` -> `InstallCrashMsgFixPatch`
  - Rewrites a call target to `UNK_10036efb`.
  - Tracks the original target in `DAT_1015ad68`.

- `0x10037290` -> `InstallMPNikoCrashFixPatch`
  - Flips one `JZ`/`JE` style branch to unconditional `JMP`.
  - This is a small gate-removal crash fix.

- `0x10037be0` -> `InstallBadPlayerModelCrashFixPatch`
  - Hooks a function entry into `UNK_10037b9b`.

- `0x10037c70` -> `InstallDebugPrintSpawnsPatch`
  - Large instrumentation patch.
  - Replaces many call targets with custom debug/logging handlers around spawn processing.
  - This is by far the largest patch in this cluster.

- `0x1003d7d0` -> `InstallPathNodeCrashFixPatch`
  - Hooks a matched site into `UNK_1003d7ab`.
  - Stores original addresses in `_DAT_1015bdfc` and `_DAT_1015be00`.

- `0x1003d890` -> `InstallPlaneCrashFixPatch`
  - Hooks a matched site into `UNK_1003d86b`.
  - Stores original addresses in `_DAT_1015be14` and `_DAT_1015be18`.

- `0x100504b0` -> `InstallWantedLevelCrashFixPatch`
  - Hooks a matched site into `UNK_1005048b`.
  - Stores one original address in `_DAT_1015ad6c`.

## Cluster Takeaway

Most of this cluster is not "change one constant." It is custom control-flow replacement:

- crash fixes often route execution into bespoke handlers
- freeze fixes tend to preserve original addresses in globals, then redirect
- only a few are simple branch/NOP patches, such as:
  - `InstallRemoveAllWeaponsProtectionPatch`
  - `InstallMPNikoCrashFixPatch`

## RE Follow-up

- classify the custom handler blobs behind:
  - `0x1000558b`
  - `UNK_1000564b`
  - `UNK_1000566b`
  - `0x10005d7b`
  - `UNK_10005ecb`
  - `0x1001b03b` through `0x1001b0bb`
  - `UNK_10036e8b`
  - `UNK_10036edb`
  - `UNK_10036eab`
  - `UNK_10036efb`
  - `UNK_10037b9b`
  - `UNK_1003d7ab`
  - `UNK_1003d86b`
  - `UNK_1005048b`
