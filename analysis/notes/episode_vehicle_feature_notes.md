# Episode / Vehicle Feature Notes

These functions form a coherent "episode content unlock / compatibility" cluster inside `ZolikaPatch.asi`.

Raw decompile:

- `episode_vehicle_feature_funcs_decompile.txt`

## Recovered Semantics

- `0x1001b310` -> `InstallTBoGTHeliHeightLimitPatch`
  - Only runs when `DAT_1015be04 > 5`, so this is tied to later PC build families.
  - NOPs a short conditional branch after a compare against episode value `2`.
  - This is a tiny gate-removal patch, not a hook.

- `0x1001b380` -> `InstallEpisodicWeaponSupportPatch`
  - Large group of pattern-scanned NOP patches.
  - Almost every site matches a compare against episode value `2`, then removes the conditional branch or the whole check block.
  - This is clearly broad removal of "episode-locked weapon" logic rather than one isolated fix.

- `0x1001b580` -> `InstallEpisodicVehicleSupportPatch`
  - Same overall structure as episodic weapon support, but much larger.
  - Removes many episode checks around vehicle logic, mostly by NOPing compare-and-branch sequences keyed on `2`.
  - Also patches two version-specific global pointer writes for build ids `8` and `9`, both redirected to `DAT_10158128`.
  - This looks like the central "allow episode vehicles in non-episode contexts / shared content contexts" patch.

- `0x1001bd60` -> `InstallBikePhoneAnimsFixPatch`
  - One targeted 6-byte NOP patch.
  - The patch site is not yet semantically rich in decompile, but it is clearly a behavior gate removal rather than a data limit change.

- `0x1001bdd0` -> `InstallBikeFeetFixPatch`
  - One targeted 12-byte NOP patch.
  - This is a localized behavior fix, not a broad compatibility layer.

- `0x1001be40` -> `InstallPipeBombDropIconSupportPatch`
  - Only runs when `DAT_1015be04 > 5`.
  - NOPs a 9-byte block immediately before a compare against `0x1c`.
  - Strong indicator that it removes a hardcoded item/type restriction related to the pipe-bomb drop icon path.

- `0x1001bec0` -> `InstallPoliceEpisodicWeaponSupportPatch`
  - Only runs when `DAT_1015be04 > 5`.
  - NOPs a short branch after a global compare.
  - This is a focused companion to `InstallEpisodicWeaponSupportPatch`, likely allowing police/NPC weapon selection paths to accept episodic weapons too.

## Cluster Takeaway

This cluster is not about memory limits or launcher behavior. It is about removing hardcoded episode segregation in gameplay/content systems:

- weapon availability
- vehicle availability
- police weapon logic
- TBoGT-specific helicopter behavior
- episode-specific icon/UI or item handling

That matters because these features should probably be grouped as a single reimplementation subsystem later: "episode content compatibility / unlock patches".

## RE Follow-up

- identify what `DAT_1015be04` encodes formally, beyond "build family / version bucket"
- identify what `DAT_10158128` points to in the episodic vehicle patch
- trace the exact game subsystems behind the bike and pipe-bomb fixes instead of leaving them at patch-site level
