# RestoreDeathMusic Notes

This pass resolved the long-standing ambiguity around the `RestoreDeathMusic` config key.

Raw artifacts:

- `restore_deathmusic_funcs_decompile.txt`
- `restore_deathmusic_10050a30_asm.txt`

## Resolved Call Context

The raw feature matrix entry was misleading:

- `RestoreDeathMusic` was heuristically mapped to `FUN_10052380`
- `FUN_10052380` is only an IVMenu loader/helper
- it checks whether `IVMenuAPI.asi` is available and optionally loads it

The actual `RestoreDeathMusic` flow is:

1. read config key `RestoreDeathMusic`
2. if enabled, set `DAT_10159b86 = 1`
3. if disabled, still check whether `IVMenuAPI.asi` is available
4. call `FUN_10050bd0`
5. if menu API is available, register menu option `PREF_DEATHMUSIC`

## Recovered Semantics

- `0x10050bd0` -> `InstallRestoreDeathMusicPatch`
  - scans for three patterns
  - rewrites three game call sites into two local helper bodies at `0x10050a50` and `0x10050ad0`
  - this is the real gameplay/audio patch installer for the feature

- `0x10050a30`
  - getter callback used by the IV menu binding
  - returns `DAT_10159b86`

- `0x10050a40` -> `SetRestoreDeathMusicEnabledFromMenu`
  - setter callback used by the IV menu binding
  - stores `DAT_10159b86 = (param_2 != 0)`

- `0x10050a50`
  - real boolean gate behind the two helpers that were previously described as "near `0x10050a4b`"
  - returns false immediately when `DAT_10159b86 == 0`
  - otherwise checks runtime state through existing game wrappers and returns true only in the allowed state combination

- `0x10050ad0`
  - runtime patch applicator behind the helper that was previously described as "near `0x10050acb`"
  - calls `0x10050a50` first and aborts when the current state is not eligible
  - synchronizes cached context values, computes `rand() % 5 + 1`, and writes that byte to a cached game site under `VirtualProtect`
  - this strongly suggests the feature restores legacy death-music selection by nudging the game's own variant/index byte, not by replacing the whole audio pipeline

## Takeaway

`RestoreDeathMusic` is a real patch feature with optional menu integration.

It should no longer be treated as:

- `FUN_10052380`
- an `IVMenuAPI` loader side effect

The corrected split is:

- `EnsureIVMenuApiLoaded`
  - menu availability helper
- `InstallRestoreDeathMusicPatch`
  - actual feature installer
