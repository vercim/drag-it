## [1.0.1] - 17-10-2026

### Fixes
- Improved display of the mod version in-game (from 1.0.0+26.3-fabric to 1.0.0)

## [1.0.0]

### Added

- Support for Minecraft 1.20.1 (Java 17), 1.21.4, 1.21.10, 1.21.11 (Java 21) and 26.1, 26.1.2, 26.2, 26.3 (Java 25) on Fabric and NeoForge (Forge).
- The lifted stack turn red when you hold the built-in Default pack over the available list, or incompatible packs over the enabled list.
- The outline of a multi-selection now glides instead of fading in all at once.
### Removed
- The "Fixed position" hint over a pinned pack; the red marker stays. The mod no longer shows any text.
### Fixed

- Selecting a pack in one list no longer leaves a highlighted pack behind in the other.
- Sending a group to the other list: packs that cannot go (required or incompatible ones) stay where they are and leave the selection.
- Dropping a group now glides every card into its place; before, only the card under the cursor did and the others jumped.
- Space sends the selected pack to the other list, as Enter does (From Minecraft 1.21.10 vanilla only reacts to Enter).
- Enter and Space now work right after Ctrl+click or Shift+click even if the search box still had the keyboard focus (1.21.10 and newer).
- Drag & drop works while the search box is in use (1.21.10 and newer)
- The drop placeholder keeps the same two-pixel margin from the list frame on every version; before 1.21.10 it touched the frame.
## [0.0.1-alpha] - 2026-10-11

### Overall
- First public pre-release: drag & drop for the vanilla resource pack and data pack selection screens.
  
### Compatibility
- Minecraft 1.21.1 on Fabric (Fabric Loader 0.16.4 or newer) and NeoForge (21.1.259 or newer), Java 21. Client side only.
