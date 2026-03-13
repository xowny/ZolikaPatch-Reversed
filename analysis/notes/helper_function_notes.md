# Helper Function Notes

These are the first non-feature helpers in `ZolikaPatch.asi` that now have working semantic descriptions.

## Config / String Layer

- `0x10055f10` -> `SelectConfigSection`
  - Looks up a section name in the loaded config tree/map.
  - On success stores the selected section node on the config object and returns true.
- `0x1005d630` -> `LookupSelectedConfigValueOrDefault`
  - Uses the currently selected section and a key name.
  - Returns the configured value if found, otherwise copies the provided default string.
- `0x1005c160` -> `GetConfigValueStringOrDefault`
  - Thin wrapper that builds string objects for key/default and then calls the selected-section lookup helper.
- `0x100014b0` -> `ParseIntegerStringChecked`
  - Wraps `strtol`.
  - Throws on invalid or out-of-range input.
  - Used for the many boolean / numeric INI toggles.

## String Object Helpers

- `0x10058620` -> `AssignManagedString`
  - Dynamic string assign/grow routine with small-string optimization.
- `0x10057870` -> `InitManagedStringFromCString`
  - Initializes the internal string object from a plain C string.
- `0x100578b0` -> `CopyManagedString`
  - Copies one managed string to another.
- `0x10057710` -> `GetManagedStringData`
  - Returns pointer to inline buffer or heap buffer depending on small-string state.
- `0x1004ac90` -> `ClearManagedString`
  - Frees/reset helper for the same string type.
- `0x1005c150` -> `StringNotEquals`
  - Negated string-compare wrapper used in config checks such as `OverrideGTADatPathTo`.

## Patch / Environment Layer

- `0x10003830` -> `FindFirstPatternMatch`
  - Runs the internal wildcard pattern scanner and returns the first match address or `0`.
  - This is the core bridge from feature installers to concrete game patch sites.
- `0x1005b5d0` -> `WriteRelativeJumpPatchAndReturnContinuation`
  - Overwrites the target site with `E9 rel32`.
  - Returns the continuation address after the displaced bytes, which is why several hook installers save its return value into resume globals.
- `0x1005b6a0` -> `FindFirstPatternMatchInModule`
  - Thin wrapper over the bulk scanner that returns only the first match inside the supplied module.
- `0x1005b7d0` -> `FindPatternMatchesInModule`
  - Builds the wildcard-pattern payload, queries module bounds with `K32GetModuleInformation`, and returns the full match list.
  - This is the lower-level scanner used by the reflection callback bridge and other late helpers that need more than a one-shot pattern lookup.
- `0x10003e20` -> `HasFusionFixConflictForLegacyPatch`
  - Checks for `GTAIV.EFLC.FusionFix.asi`.
  - Used as a gate before enabling some legacy patches such as `SkipIntro`, `SkipMenu`, and `BorderlessWindowed`.
  - Semantics are "do not apply this old patch when FusionFix is present/incompatible".

## What This Gives Us

With these helpers understood, `InitializeConfigAndApplyPatches` is no longer a black box:

1. load config
2. select section
3. read key with default
4. parse integer / compare string
5. optionally suppress patch based on FusionFix conflict
6. install feature patch through pattern scan and code/data write
