# Drag It!

Smooth drag & drop for the Minecraft resource pack and data pack selection screens. Grab a pack, move it where you want it, drop it. No new screens, no extra clicks on the stock arrows: everything else keeps working exactly like vanilla.

> **Alpha.** `0.0.1-alpha` is the first pre-release. It is functional, but expect rough edges. Please [report them](https://github.com/vercim/drag-it/issues).

Minecraft 1.21.1 · Fabric · Java 21 · client side only

## Features

- **Lift and drop.** A pack follows the cursor as a lifted card while the others slide aside and a placeholder marks the slot it will land in.
- **Re-order or move.** Drop inside a list to re-order, or into the other list to enable or disable the pack at that exact position.
- **Multi-selection.** Select several packs like in a file manager and drag them as one block.
- **Auto-scroll.** Drag near the top or bottom edge of a list and it scrolls.
- **Safe by design.** Pinned packs stay put, required packs are never disabled, and incompatible packs still ask for confirmation.
- **Frame-rate independent.** Animations run on real time and look the same at any FPS.

## Controls

| Gesture | Result |
|---|---|
| Press on a pack and move the cursor | Lifts the pack; it follows the cursor |
| Plain click | Exactly vanilla: the arrows enable, disable, and move, a click selects |
| Drop inside a list | Re-orders the pack to the marked slot |
| Drop into the other list | Enables or disables the pack at the marked slot |
| Drop on empty list space | Puts the pack last (above pinned packs in the enabled list) |
| Esc while dragging, or release outside both lists | The pack glides back; the screen stays open |
| Ctrl+click | Toggles a pack in the selection |
| Shift+click | Selects the range from the last clicked pack |
| Ctrl+Shift+click | Adds that range to the selection |
| Ctrl+A | Selects every movable pack of the list under the cursor |
| Drag a selected pack | Lifts the whole selection as one block |
| Enter, Space, or the arrow of a selected pack | Sends the whole selection to the other list; pressing again brings it back |
| Drag near a list edge | Auto-scrolls the list |

Pinned (fixed position) packs cannot be lifted, and nothing can be dropped across them; the blocked spot is marked in red. The built-in Default pack can be re-ordered but never disabled. Dropping a single incompatible pack into the enabled list shows vanilla's confirmation dialog.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.16.4 or newer for Minecraft 1.21.1.
2. Download the JAR from the [GitHub releases](https://github.com/vercim/drag-it/releases).
3. Put it into the `mods` folder.

Fabric API is not required. The mod works on the data pack screens (world creation and editing) too, since they share the resource pack screen.

## Known limitations

- Vanilla does not remember the order of the *available* list (it is sorted again when the screen reopens); the enabled order is kept.
- A pack lifted at the moment the folder watcher rebuilds the lists is dropped back and the gesture is cancelled.
- Dragging is mouse-only. Keyboard navigation, search mods, and the stock arrows keep working unchanged.

## Building from source

The project is a single [Stonecutter](https://stonecutter.kikugie.dev/) tree. You need JDK 25 to launch Gradle; the Java 21 toolchain is provisioned automatically.

```bash
./gradlew buildAndCollect   # release JAR in build/libs/
./gradlew runActiveClient   # development client
```

On Windows use `.\gradlew.bat`. Everything about development, CI, and releases is in [docs/WORKFLOW.md](docs/WORKFLOW.md).

## License

[LGPL-3.0](LICENSE)
