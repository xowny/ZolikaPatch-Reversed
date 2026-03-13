# IVMenuAPI Internal Notes

This pass focused on the internal routines called by `AddIVMenuEnum`, `AddIVMenuOption`, and `ReloadIVMenus`.

Raw artifacts:

- `ivmenuapi_internal_funcs_decompile.txt`
- `ivmenuapi_internal_xrefs.txt`

## Recovered Internal Roles

- `0x100098b0`
  - thin initialization wrapper that invokes the large cache/bootstrap body at `0x10005630`
  - this is the clearest recovered entry point into the IVMenu registration-cache setup path

- `0x10005630`
  - large cache/bootstrap routine behind the registration bridge
  - clones existing option and enum descriptor pairs into local arrays
  - mirrors the live option table into `DAT_1002cce0` / `DAT_1002cce4`
  - mirrors the live enum table into `DAT_1002ece0` / `DAT_1002ece4`
  - writes `DAT_100300e4` and `DAT_100300ec`, which are the active published option and enum counts later consumed by `RebuildAndPublishIVMenuRegistrations`
  - also seeds the local next-slot counters used by the exports:
    - `DAT_100300e8` for option-descriptor storage
    - `DAT_100300f0` for enum-descriptor storage
    - `DAT_100300e0` for the next enum numeric id
  - this is the main reason the API still looks stateful internally: it mirrors cached registration state before publishing it back through callback slots

- `0x10006a60` -> `LoadAndPatchIVMenuDefinition`
  - takes a menu-definition path, stores it, parses the source, and walks the cached enum/option registrations
  - looks for `</sMenuDisplayValue>` and `enum="%s"` markers while iterating the cached enum table at `DAT_10030120`
  - injects registered enum display values and registered options into a rebuilt in-memory menu definition
  - ends by formatting a `memory:$%p,%d,%d:%d` style descriptor, which is strong evidence this helper prepares a patched menu definition blob for the host menu system to consume
  - this is a real hidden integration point inside `IVMenuAPI.asi`, but it is still a menu-definition patch/load helper rather than a standalone renderer

- `0x10006940` -> `LoadAndPatchIVMenuDefinitionAndResume`
  - pushad/popad stub that calls `LoadAndPatchIVMenuDefinition` on the intercepted menu-definition pointer and then resumes through a resolved continuation
  - this is the actual patch-entry trampoline that lets the host menu system consume the rebuilt in-memory definition blob

- `0x10006900` -> `InvokeResolvedMenuDefinitionCallback`
  - tiny callback bridge that forwards the current menu-definition pointer into the callback resolved in `DAT_1002c998`

- `0x10007d80` -> `RebuildAndPublishIVMenuRegistrations`
  - called after both `AddIVMenuEnum` and `AddIVMenuOption`
  - reads the current option count from `DAT_100300e4`
  - reads the current enum count from `DAT_100300ec`
  - resolves three callback targets through `FUN_10002f80`
  - publishes the counts through those callbacks
  - this is the real menu refresh/rebuild bridge used after registration

- `0x10007f40` -> `InvokeReloadIVMenusCallback`
  - used only by the `ReloadIVMenus` export
  - resolves one callback via `FUN_10002f80` and invokes it directly
  - this is a thin reload bridge, not a local rebuild implementation

- `0x10009cc0` -> `CopyAnsiMenuLabelIntoEntry`
  - unwraps a string object and forwards it to `FUN_100046c0`
  - used by `AddIVMenuEnum` while storing enum display labels

- `0x10009cf0` -> `CopyWideMenuLabelIntoEntry`
  - wide-string companion to `CopyAnsiMenuLabelIntoEntry`
  - used by `AddIVMenuEnum` for localized label text

- `0x10005230` -> `AssignEnumIdentifierString`
  - forwards to a string append/copy helper
  - used when `AddIVMenuEnum` stores the enum identifier/key

- `0x10004af0` -> `ConstructAnsiMenuString`
  - constructs a local ANSI string object from the source pointer
  - used by `AddIVMenuEnum` before copying label text into the enum entry

- `0x10004c30` -> `ConstructWideMenuString`
  - constructs a local wide-string object from the source pointer
  - used by `AddIVMenuEnum` before copying localized text into the enum entry

- `0x10004a40` -> `ConstructAnsiMenuStringFromStringVal`
  - constructs a transient ANSI string object from an existing string value
  - used when `LoadAndPatchIVMenuDefinition` turns a parsed menu node into a searchable ANSI string

- `0x100046c0` -> `AppendAnsiMenuLabelToVector`
  - append helper used by `CopyAnsiMenuLabelIntoEntry`
  - confirms enum descriptor offset `+0x18` is a growable ANSI string collection rather than a single string slot
  - the nested collection is a standard vector-like header:
    - `+0x00` begin pointer
    - `+0x04` end pointer
    - `+0x08` capacity pointer
  - when `end == capacity`, the helper dispatches to the reallocation path at `FUN_10003900`
  - otherwise it appends in place through `FUN_100037e0`

- `0x10004730` -> `AppendWideMenuLabelToVector`
  - wide-string companion to `AppendAnsiMenuLabelToVector`
  - confirms enum descriptor offset `+0x24` is the growable localized/wide string collection
  - this collection uses the same `begin/end/capacity` triple
  - the grow path is `FUN_10003af0`, while the in-place append path is `FUN_10003870`

- `0x10004f60` -> `ConstructCachedEnumDescriptor`
  - single-record constructor for the `0x34`-byte cached enum descriptor
  - initializes:
    - identifier string at offset `+0x00`
    - ANSI label vector at offset `+0x18`
    - wide label vector at offset `+0x24`
    - trailing match flag byte at offset `+0x30`
  - this constructor is fed to `_eh_vector_constructor_iterator_`, which means `DAT_10030120` is a fixed statically allocated array of `0x7f` enum descriptors rather than a heap vector of descriptors

- `0x10006250` -> `SerializeOptionRegistrationToMenuXml`
  - serializes one cached option descriptor into an XML fragment
  - confirmed emitted attributes:
    - `action="MENUOPT_ADJUST"`
    - `label=` from the cached string at option offset `+0x08`
    - `value=` from the same cached string at option offset `+0x08`
    - `scaler=` from the integer at option offset `+0x18`, incremented by one
    - `displayValue=` from the cached string at option offset `+0x10`

- `0x10007030` -> `SerializeEnumRegistrationToMenuXml`
  - serializes one cached enum descriptor into a `<menupc enum="...">` block
  - uses the enum identifier at offset `+0x00`
  - iterates a display-value collection rooted at offset `+0x18`
  - emits `<options text="..." action="ACTION_NONE" value="..."/>` entries for each cached display label

- `0x10007290` -> `ResolveMenuDisplayValueToken`
  - resolves a menu token string back into a registered display value or option-linked value
  - first scans cached option descriptors and compares against the identifier/key string at option offset `+0x08`
  - returns the dword at option offset `+0x0c` for option-token matches
  - then scans cached enum display labels and returns the localized/wide label selected from enum offset `+0x24`
  - this is the clearest confirmation so far that the cached descriptors are used as a live token-resolution layer, not just a one-way serializer

- `0x10006960` -> `MenuNodeContainsValueAttribute`
  - checks whether a parsed menu-node string contains `value="..."` for the supplied filter string
  - this is the helper that proves option descriptor offset `+0x14` is a value-attribute filter, not generic mode metadata

- `0x10007c30` -> `InvokeCachedOptionGetterByPublishedIndex`
  - for legacy published options, reads through the mirrored game table rooted at `DAT_100300f4`
  - for mod-added options, calls the getter callback at option offset `+0x00` with the callback context at `+0x1c`
  - stores the resolved current value in `DAT_1002c990`

- `0x10007c90` -> `InvokeCachedOptionSetterByPublishedIndex`
  - for legacy published options, writes back through the mirrored game table rooted at `DAT_100300f4`
  - for mod-added options, calls the setter callback at option offset `+0x04` with the callback context at `+0x1c`

- `0x100052c0` -> `GetStringVectorElementAtIndex`
  - returns one `0x18`-byte string element from a vector-like container
  - used by `ResolveMenuDisplayValueToken` to fetch the localized/wide enum label at descriptor offset `+0x24`

- `0x10009690` -> `GetWideStringData`
  - wide-string companion to `GetAnsiStringData`
  - used after `GetStringVectorElementAtIndex` on the enum descriptor's wide-label collection

- `0x10009db0` -> `GetEnumDisplayValueCount`
  - returns `(end - begin) / 0x18` for the collection rooted at enum offset `+0x18`
  - this confirms the enum display-value list stores 0x18-byte string elements

- `0x10002a30` / `0x1001ba10`
  - constructor/destructor pair for the cached enum descriptor table at `DAT_10030120`

- `0x10002a60` / `0x1001ba30`
  - constructor/destructor pair for the patched menu XML buffer at `DAT_10031b18`

- `0x10007780`
  - bootstrap routine that creates a background thread, runs one-time setup helpers, and then calls `InitializeIVMenuRegistrationCaches` when the required callback state is present
  - this is the clearest recovered lifecycle entry for the local cache/bootstrap state outside the exports themselves
  - this also explains why `InitializeIVMenuRegistrationCaches` itself has only one direct caller in the recovered binary

- `0x10006820` -> `DetectSupportedMenuHostVariant`
  - probes the surrounding menu host and maps the detected result into the local variant byte `DAT_1002cbac`
  - currently observed supported cases are `0x42e -> 1` and `0x438 -> 2`

- `0x10007760` -> `InitializeMenuHostState`
  - runs host-variant detection, stores the main module handle in `DAT_1002cbb0`, and marks the one-time init flag at `DAT_1002cbb4`

- `0x100077d0` -> `ResolveAndInstallMenuHostCallbacks`
  - resolves the host callback entry points with `FUN_10002f80`
  - wraps each resolved callback through `ResolveMenuHostCallbackThunk`
  - populates the local callback slots that the rest of `IVMenuAPI.asi` uses for publication, reload, and menu-definition interception

- `0x10006870` -> `ResolveMenuHostCallbackThunk`
  - takes one resolved host callback and one local stub entry
  - materializes a callable thunk used by the local callback slots
  - this is the common glue between the host callback resolver and the local patch/bridge stubs

- `0x10007000` -> `GetCurrentModuleHandle`
  - obtains the module handle for `IVMenuAPI.asi` itself with `GetModuleHandleExA`

- `0x10006920` -> `KeepModuleResidentThreadProc`
  - dormant thread procedure that just sleeps forever
  - likely exists to keep a resident thread or satisfy a host expectation after startup

- `0x1000a140` -> `IVMenuApiDllMain`
  - process-attach entry point that invokes `InitializeIVMenuApiRuntime`

## Inferred Cache Layout

These layouts are now supported by the export decompiles plus the serializer helpers above.

- Option publication table:
  - `DAT_1002cce0` / `DAT_1002cce4`
  - 8-byte pairs of:
    - published option ordinal
    - option identifier pointer

- Enum publication table:
  - `DAT_1002ece0` / `DAT_1002ece4`
  - 8-byte pairs of:
    - published enum numeric id
    - enum identifier pointer

- Cached option descriptor table:
  - base `DAT_10024000`
  - stride `0x20`
  - currently defended fields:
    - `+0x00` getter callback
    - `+0x04` setter callback
    - `+0x08` option identifier or key string
    - `+0x0c` option-linked token/value returned by `ResolveMenuDisplayValueToken`
    - `+0x10` display-value string
    - `+0x14` required `value="..."` attribute filter used while injecting the option into a matching menu node
    - `+0x18` scaler/range integer used as `scaler + 1` during XML emission
    - `+0x1c` callback context passed to the getter/setter callbacks
  - this is enough to treat the option record as a real callback-backed registration descriptor

- Cached enum descriptor table:
  - base `DAT_10030120`
  - stride `0x34`
  - currently defended fields:
    - `+0x00` enum identifier string
    - `+0x18` ANSI label vector header with `begin/end/capacity` pointers
    - `+0x24` localized/wide label vector header with `begin/end/capacity` pointers used for token-to-display resolution
    - `+0x30` match/enabled flag used while patching the menu definition
  - each string element in those nested vectors is still `0x18` bytes, as shown by `GetStringVectorElementAtIndex` and `GetEnumDisplayValueCount`

- Active counters:
  - `DAT_100300e4` active published option count
  - `DAT_100300ec` active published enum count
  - `DAT_100300e8` next cached option slot
  - `DAT_100300f0` next cached enum slot
  - `DAT_100300e0` next enum numeric id

## Architectural Takeaway

`IVMenuAPI.asi` is more of a registration-and-publication bridge than a full menu implementation:

- it stores enum and option descriptors in local arrays
- it mirrors incoming ANSI and wide strings into those arrays
- it can rebuild a patched menu-definition blob from a source definition plus the cached registrations
- it publishes updated counts through resolved callback targets after each registration
- the `ReloadIVMenus` export just triggers one callback-based reload entry point

This means an open replacement only needs to preserve:

- the registration data layout/limits
- the count publication behavior
- the export surface expected by `ZolikaPatch.asi`
- the host-callback resolution and menu-definition interception behavior around `LoadAndPatchIVMenuDefinitionAndResume`

It probably does not need to recreate a complex internal renderer unless later RE shows hidden stateful UI logic outside this registration path.

The remaining uncertainty here is narrower now:

- there is local cached registration state
- the counts at `DAT_100300e4` and `DAT_100300ec` are part of that cache/bootstrap path
- there is an internal menu-definition patch/load helper in addition to the registration bridge
- the nested enum string collections are now firmly identified as `begin/end/capacity` vector headers over `0x18`-byte string elements
- the enum descriptor table itself is a fixed `0x7f`-entry static array, not a dynamically resized descriptor container
- there is still no evidence of a large separate renderer or deep UI state machine inside the recovered `IVMenuAPI.asi` internals

