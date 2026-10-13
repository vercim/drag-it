## [Unreleased]

### Added
- Minecraft 1.21.4 on Fabric (Fabric Loader 0.16.9 or newer) and NeoForge (21.4.158 or newer), Java 21. Client side only.
- Minecraft 1.21.10 on Fabric (Fabric Loader 0.17.3 or newer) and NeoForge (21.10.64 or newer), Java 21. Client side only.
- Minecraft 1.21.11 on Fabric (Fabric Loader 0.17.3 or newer) and NeoForge (21.11.45 or newer), Java 21. Client side only.
- Minecraft 26.1 on Fabric (Fabric Loader 0.18.3 or newer) and NeoForge (26.1.0.19-beta or newer), Java 25. Client side only.
- Minecraft 26.1.2 on Fabric (Fabric Loader 0.18.4 or newer) and NeoForge (26.1.2.116 or newer), Java 25. Client side only.
- Minecraft 26.2 on Fabric (Fabric Loader 0.18.4 or newer) and NeoForge (26.2.0.90 or newer), Java 25. Client side only.
- Minecraft 26.3 on Fabric (Fabric Loader 0.19.3 or newer) and NeoForge (26.3.0.76 or newer), Java 25. Client side only.
- On 1.21.10 and newer, packs are not lifted while the pack screen's search box hides some of them (the shown order would no longer match the real one); clear the search to drag again. Clicks and the arrows work as usual.
- A refused drop turns the lifted card and the list red: holding the built-in Default pack over the available list, or several incompatible packs over the enabled list.

### Changed
- The outline of a multi-selection glides: a Shift+click range grows out of the row it started at, a shorter range shrinks back, and a Ctrl+click extends or splits the outline instead of it appearing all at once.

### Removed
- The "Fixed position" hint next to the cursor over a pinned pack; the red marker stays. The mod no longer shows any text.

### Fixed
- Sending a group to the other list with Enter, Space, or an arrow leaves the packs that cannot go (required or incompatible ones) where they are and drops them from the selection.
- Only one list ever holds a selection: selecting something with the keyboard in the other list no longer leaves a highlighted pack behind in both lists.
- When a dragged group is held over a list that cannot take some of its packs, the cards of those packs in the stack turn red (the rest stay blue); on drop they stay where they are and the others move.
- Dropping a group of packs glides every card of the stack into its place; before, only the card under the cursor did and the others jumped to their new rows.

## [0.0.1-alpha] - 2026-10-11

### Overall
- First public pre-release: drag & drop for the vanilla resource pack and data pack selection screens.
  
### Compatibility
- Minecraft 1.21.1 on Fabric (Fabric Loader 0.16.4 or newer) and NeoForge (21.1.259 or newer), Java 21. Client side only.
