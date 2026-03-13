# ZolikaPatch-Reversed

This folder is organized as a public reverse engineering and implementation-reference workspace.

## Layout

- `config/`: runtime config files shipped with the patch
- `docs/`: upstream readme and external reference material
- `analysis/notes/`: focused notes, target lists, and new outputs from current reversing work
- `ghidra/scripts/`: small helper scripts used during reversing

## Current Targets

- `IVMenuAPI`: exported menu API surface used by other plugins
- `ZolikaPatch`: main patch module, especially menu integration and config-driven features

## Key Docs

- `analysis/notes/implementation_guide.md`: condensed implementation guide
- `analysis/notes/reimplementation_status.md`: current overall RE status
- `analysis/notes/zolikapatch_feature_matrix_verified.tsv`: verified feature-to-installer matrix
- `analysis/notes/current_build_signature_audit.md`: first-pass audit against GTA IV `1.2.0.59`
