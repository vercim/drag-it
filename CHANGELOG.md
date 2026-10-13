## [Unreleased]

### Added
- Support for Minecraft 1.21.4, 1.21.10, 1.21.11 (Java 21) and 26.1, 26.1.2, 26.2, 26.3 (Java 25) on Fabric and NeoForge, client side only. Minimum loader versions:
  - 1.21.4: Fabric Loader 0.16.9, NeoForge 21.4.158
  - 1.21.10: Fabric Loader 0.17.3, NeoForge 21.10.64
  - 1.21.11: Fabric Loader 0.17.3, NeoForge 21.11.45
  - 26.1: Fabric Loader 0.18.3, NeoForge 26.1.0.19-beta
  - 26.1.2: Fabric Loader 0.18.4, NeoForge 26.1.2.116
  - 26.2: Fabric Loader 0.18.4, NeoForge 26.2.0.90
  - 26.3: Fabric Loader 0.19.3, NeoForge 26.3.0.76
- Drag & drop works while the search box is in use (1.21.10 and newer); a pack lands next to the packs you see.
- A refused drop is shown in red: the lifted card and the list turn red when you hold the built-in Default pack over the available list, or incompatible packs over the enabled list. In a dragged group, only the cards of the refused packs turn red.

### Changed
- The outline of a multi-selection now glides when you Shift+click or Ctrl+click, instead of fading in all at once.

### Removed
- The "Fixed position" hint over a pinned pack; the red marker stays. The mod no longer shows any text, so its English and Russian translations are gone.

### Fixed
- Selecting a pack with the keyboard in one list no longer leaves a highlighted pack behind in the other.
- Sending a group to the other list (Enter, Space, an arrow, or a drop): packs that cannot go (required or incompatible ones) stay where they are and leave the selection.
- Dropping a group now glides every card into its place; before, only the card under the cursor did and the others jumped.

## [0.0.1-alpha] - 2026-10-11

### Overall
- First public pre-release: drag & drop for the vanilla resource pack and data pack selection screens.
  
### Compatibility
- Minecraft 1.21.1 on Fabric (Fabric Loader 0.16.4 or newer) and NeoForge (21.1.259 or newer), Java 21. Client side only.
