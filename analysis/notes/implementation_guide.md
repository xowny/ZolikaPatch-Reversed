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
