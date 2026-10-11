## [0.0.1-alpha] - 2026-10-11

### Overall
- First public pre-release: drag & drop for the vanilla resource pack and data pack selection screens.
- Dragging a pack lifts it as a card that follows the cursor; the other packs slide aside and a placeholder marks the exact slot where it will land.
- Drop inside a list to re-order, or into the other list to enable or disable the pack at that exact position.
- Dropping outside both lists, or pressing Esc, glides the pack back and keeps the screen open.
- Dragging near the top or bottom edge of a list scrolls it, faster the closer the cursor is to the edge.
- A plain click behaves exactly like vanilla: the arrows enable, disable, and move packs, and a click selects.
- Multi-selection like in a file manager: Ctrl+click toggles a pack, Shift+click selects a range, Ctrl+Shift+click adds a range, and Ctrl+A selects every movable pack of a list.
- Dragging a selected pack lifts the whole selection as one block and keeps its inner order.
- Enter, Space, or the arrow of any selected pack sends the whole selection to the other list; pressing it again brings the selection back.
- Pinned packs (fixed position) are never lifted and nothing can be dropped across them; the blocked spot is marked in red with a "Fixed position" hint.
- Required packs such as the built-in Default pack can be re-ordered but never disabled.
- Dropping a single incompatible pack into the enabled list shows the same confirmation dialog as vanilla.
- Selection highlights fade in and out instead of switching instantly.
- Animations run on real time, so they look the same at 30 and at 300 FPS.
- English and Russian translations.

### Compatibility
- Minecraft 1.21.1 on Fabric (Fabric Loader 0.16.4 or newer) and NeoForge (21.1.259 or newer), Java 21. Client side only.
- Fabric API is not required.

### Architecture
- One [Stonecutter](https://stonecutter.kikugie.dev/) source tree builds the Fabric and NeoForge targets, with tag-driven publishing to GitHub, Modrinth, and CurseForge, mirroring the Handycam build and release setup.
- Official Mojang mappings on both loaders. The mod id is `dragit` (NeoForge does not allow hyphens in ids) and the code lives in the `dev.vercim.dragit` package.
- The build fails if a JAR does not register its Mixin config, misses a mixin class, or contains the other loader's files.
- Added the pixel icon and declared LGPL-3.0 in the mod metadata, matching the bundled license file.

### Documentation
- Added README, development and release guide, and version notes.
