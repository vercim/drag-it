# Drag It!

Client-side Fabric mod (Minecraft 1.21.1, Java 21, Yarn mappings) that adds smooth drag & drop to the vanilla
resource-pack / data-pack selection screen (`PackScreen`). No screens are replaced, no libraries are needed
(Fabric Loader + Mixin only, no Fabric API).

## Build & run

```bash
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client
./gradlew runClient -Pselftest   # scripted end-to-end check, writes run/screenshots/dragit_*.png and quits
```

The build uses a Java 21 toolchain (Gradle finds a local JDK 21, e.g. in `~/.jdks`).

## Behaviour

| Gesture | Result |
|---|---|
| Press on a pack and move more than 4 px | the pack is lifted (shadow, slight scale) and follows the cursor with smoothing |
| Plain click (no movement) | exactly vanilla: arrows enable/disable/move, click selects |
| Drag inside "selected" | neighbours slide apart, a placeholder shows the exact slot, drop inserts there |
| Drag between lists | available -> selected enables the pack, selected -> available disables it, at the exact slot |
| Drop on empty list space | pack goes last (above the pinned packs, if any, in the selected list) |
| Cursor near top/bottom edge of a list | the list scrolls (speed grows towards the edge) |
| Esc while dragging, or release outside both lists | the pack glides back; the screen stays open |
| Required pack (the built-in "Default") | can be dragged up/down in the selected list like any pack, but never disabled: dropping it into "available" glides it back |
| Pinned packs (fixed position, e.g. from a server) | cannot be lifted; no pack can be dropped above/below them (red marker + "Fixed position") |
| Ctrl+click | multi-selection like in Windows Explorer: toggles the pack and makes it the anchor |
| Shift+click | selects the range anchor..clicked, replacing the selection (anchor stays); Ctrl+Shift+click adds the range to it |
| Ctrl+A | selects every movable pack of the list under the cursor |
| Plain click | outside the selection or on empty space drops it; on a selected pack it collapses the selection to that pack |
| Enter / Space, or the arrow of any selected pack | the whole selection jumps to the opposite list (enable / disable), relative order kept; the selection stays, so the same key brings it back. Pinned, required and incompatible packs are skipped |
| Incompatible pack dropped into "selected" | vanilla confirmation dialog, then the pack lands where it was dropped |

Everything is tunable in [`DragItSettings`](src/main/java/com/dragit/DragItSettings.java).
Data-pack screens (world creation / edit) use the same `PackScreen` class and get the same behaviour.

## How it hooks in

* `ScreenMixin` (`Screen.render` head/tail, `Screen.keyPressed` head) - `PackScreen` does not declare these, and Mixin
  cannot inject into inherited methods, so the hooks sit on `Screen` and return immediately for anything that is not a `PackScreen`.
* `MouseMixin` (`Mouse.onMouseButton` head) - the one place that sees every press/release and can hold a press back from vanilla
  (so arrows only fire on a real click and a drag can start on them).
* `PackScreenMixin` - owns the per-screen `DragController`, cancels gestures on re-layout / close.
* `ResourcePackEntryMixin` (`@WrapMethod render`) and `PackListWidgetMixin` (`@WrapMethod drawSelectionHighlight`) - hide the
  lifted entry, draw sliding entries through the matrix stack, drop vanilla hover decoration while things move.
* Accessors: `PackScreen` (lists, organizer), `ResourcePackOrganizer` (`enabledPacks`, `disabledPacks`, update callback,
  `refreshEnabledProfiles`), `PackListWidget` (`screen`, `getScrollbarX`), `EntryListWidget` (`itemHeight`, `headerHeight`,
  `isScrollbarVisible`), `ResourcePackEntry` (`pack`, `widget`).

No pack state is stored by the mod: reordering edits the organizer's own lists and calls its own update callback, so "Done"
applies exactly what is shown. Moving between lists goes through vanilla `Pack.enable()` / `Pack.disable()` first (so the
high-contrast option and the manager stay consistent) and then slides the pack to the exact index.

## Known limitations

* The order of the *available* list is not persisted by vanilla (it is re-sorted when the screen reopens); the enabled order is.
* A pack lifted while the folder watcher rebuilds the lists is dropped back (gesture cancelled).
* Dragging is mouse-only; keyboard (Space/Enter, Shift+arrows), search mods and the stock arrows keep working unchanged.
