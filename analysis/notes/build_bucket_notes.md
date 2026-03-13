# Build Bucket Notes

This pass replaces the old inference-only mapping with direct decompiled assignment proof for `DAT_1015be04`.

## Direct Assignment Routine

- `0x10055c70` is the bootstrap routine that assigns `DAT_1015be04` before `InitializeConfigAndApplyPatches`.
- `0x10003b80` reads the current executable's version resource with:
  - `GetModuleFileNameA`
  - `GetFileVersionInfoSizeA`
  - `GetFileVersionInfoA`
  - `VerQueryValueA`
- The version reader validates `VS_FIXEDFILEINFO.dwSignature == 0xFEEF04BD`, extracts the four version words, concatenates them without separators, and parses the result as a base-10 integer.
- That version token is then mapped by `0x10055c70` into the legacy internal bucket stored in `DAT_1015be04`.

## Exact Token To Bucket Mapping

- `1000` -> file version `1.0.0.0` -> bucket `1`
- `1004` -> file version `1.0.0.4` -> bucket `6`
- `1040` -> file version `1.0.4.0` -> bucket `5`
- `1060` -> file version `1.0.6.0` -> bucket `7`
- `1061` -> file version `1.0.6.1` -> bucket `0xf`
- `1070` -> file version `1.0.7.0` -> bucket `8`
- `1080` -> file version `1.0.8.0` -> bucket `9`
- `1100` -> file version `1.1.0.0` -> bucket `0xa`
- `1110` -> file version `1.1.1.0` -> bucket `0xb`
- `1120` -> file version `1.1.2.0` -> bucket `0xc`
- `1130` -> file version `1.1.3.0` -> bucket `0xd`
- `12043` -> file version `1.2.0.43` -> bucket `0x12`
- anything else -> bucket `0`

## Practical Meaning

- `DAT_1015be04` is not computed from pattern scanning, binary fingerprints, or hashes.
- It is a hardcoded compatibility bucket derived strictly from the EXE file version resource.
- The earlier "bucket linearly tracks every GTA IV patch in order" interpretation was too loose.
- The recovered mapping is sparse and version-specific, especially once the EFLC / Complete Edition families appear.

## Newer Build Handling

- The same bootstrap routine special-cases a version-token range starting at `1201` and uses it to open the downgrading page, sleep briefly, and terminate the process.
- That explains why the legacy ASI rejects current Complete Edition executables such as `1.2.0.43` and newer `1.2.x.x` builds.
