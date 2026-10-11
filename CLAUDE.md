# CLAUDE.md

Drag It! — client-side Minecraft mod, smooth drag & drop for the resource pack and data pack selection screens. Fabric, Java 21, Yarn mappings, one shared source tree built with Stonecutter. Build, CI, and release setup mirror [Handycam](https://github.com/vercim/handycam); keep the two in step.

`AGENTS.md` only points here, so there is a single copy of these rules.

## Docs map

| Document | Read it when | Update it when |
|---|---|---|
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | building, running, switching targets (commands for Windows and macOS), CI, packaged-JAR validation, releasing, build troubleshooting | commands, tasks, CI, or the release flow change |
| [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md) | porting to a new Minecraft version, or touching version-specific code and dependency versions | a target is added, a dependency is bumped, or you hit a version-specific API difference or quirk (the file explains how to fill it) |
| [CHANGELOG.md](CHANGELOG.md) | preparing a release | any player-visible change (see [Changelog](#changelog)) |
| [README.md](README.md) | checking what players are told | gestures, requirements, or limitations change |

## Current state

- Version `0.0.1-alpha`, the first pre-release.
- Target: `1.21.1-fabric`, declared once in `settings.gradle.kts`. The `-fabric` suffix is kept even though Fabric is the only loader, so another loader or Minecraft version is added exactly as in Handycam.
- `vcsVersion = 1.21.1-fabric`: commit with `.sc_active_version` set to `1.21.1-fabric`.
- No Fabric API and no other libraries; MixinExtras comes with Fabric Loader. Metadata pins Minecraft to exactly `1.21.1`.

## Rules that must not be forgotten

- IDE-only runs prove nothing about packaging; validate the JAR from `build/libs/` in a clean client profile (steps in [docs/WORKFLOW.md](docs/WORKFLOW.md)).
- The JAR must register `drag-it.mixins.json` in `fabric.mod.json` and contain every mixin class. `verifyJar` (part of `build`) enforces this; a missing registration makes the mod load while every injection silently does nothing.
- Build commands differ per OS (`.\gradlew.bat` on Windows, `./gradlew` on macOS/Linux); take them from [docs/WORKFLOW.md](docs/WORKFLOW.md) rather than guessing.
- Gradle needs JDK 25+ to launch (Fabric Loom 1.18); the mod itself compiles with a Java 21 toolchain.
- A pushed `v<mod_version>` tag starts the release pipeline and publishes irreversibly; create and push it only as the last step of a release.

## Structure

```
src/main/java/dev/vercim/dragit/
  DragIt.java                      — mod id and logger
  DragItSettings.java              — every tunable (rates per second, colors, thresholds, auto-scroll)
  drag/
    DragController.java            — per-screen state machine: input, multi-selection, per-frame update, drop
    DragRenderer.java              — draws the placeholder, the blocked marker and the lifted card; pure drawing
    DropTarget.java                — works out which list and which gap a drop lands in; pinned-pack zones
    EntryAnimator.java             — smooth per-pack vertical offsets that survive vanilla rebuilding the rows
    ListGeometry.java              — row geometry of vanilla's PackListWidget, recomputed from live state
    ControllerHolder.java          — implemented by PackScreen (via mixin): one DragController per screen
  mixin/
    MouseMixin.java                — Mouse.onMouseButton HEAD: sees every press and release, can hold a press back
    ScreenMixin.java               — Screen.render HEAD/TAIL and Screen.keyPressed HEAD, no-ops for non-PackScreens
    PackScreenMixin.java           — owns the controller; cancels the gesture on re-layout and close
    ResourcePackEntryMixin.java    — @WrapMethod render: hides the lifted row, slides rows, draws fading highlights
    PackListWidgetMixin.java       — @WrapMethod drawSelectionHighlight: skips vanilla's instant frame
    accessor/                      — accessors for PackScreen, ResourcePackOrganizer, PackListWidget, EntryListWidget, ResourcePackEntry
src/main/resources/
  assets/drag-it/lang/             — en_us.json, ru_ru.json (keep in sync)
  fabric.mod.json, drag-it.mixins.json
```

Build scripts: `build.fabric.gradle.kts` (Loom via `loom-back-compat`, `verifyJar`, `buildAndCollect`, `publishMods`), `stonecutter.gradle.kts` (defines `runActiveClient`, `buildActive`, `clean`), `settings.gradle.kts` (the target matrix and plugin versions).

Loader- or version-specific code, if it ever appears, is wrapped in Stonecutter conditions; syntax and rules: [docs/WORKFLOW.md](docs/WORKFLOW.md#conditional-sources). Express Minecraft-version differences with narrow local conditions, never by copying files.

## Architecture

`DragController` is a state machine, one instance per `PackScreen`:

```
IDLE --press on a movable pack--> PENDING --cursor passes the threshold--> DRAGGING
PENDING --release--> IDLE (the swallowed click is replayed to vanilla)
DRAGGING --release / Esc--> SETTLING (the lifted card glides into its slot) --arrived--> IDLE
```

- The mod stores no pack state. Re-ordering edits the organizer's own lists and calls its update callback, so "Done" applies exactly what is shown. Moving between lists goes through vanilla `Pack.enable()` / `Pack.disable()` first, then the pack is moved to the exact index.
- Everything is driven by real time (`System.nanoTime()` per frame), never by ticks. Rates in `DragItSettings` are "per second" and applied as `1 - exp(-dt * rate)`, so motion is identical at any FPS. Do not add tick-based animation.
- A drop *gap* `g` is an index into the target list with the dragged packs removed; the pack ends up at `list.add(g, pack)`. It is the same number for "move inside a list" and "come from the other list".
- Pinned packs are never lifted and nothing is dropped across them. Required packs (the built-in Default pack) can be re-ordered but never disabled. Do not conflate the two.
- Any `RuntimeException` in the controller cancels the gesture and logs once (`fail()`); the screen must never be left stuck in a drag.
- Multi-selection follows file-manager rules; the doc comment on `modifierClick` is the specification. The selection lives in one list at a time.

## Mixin rules

- `PackScreen` does not declare `render` or `keyPressed`, and Mixin cannot inject into inherited methods, so those hooks live on `Screen` and return immediately for anything that is not a `PackScreen`.
- `MouseMixin` is the only place that can hold a press back from vanilla; a plain click is replayed to vanilla on release so the arrows keep working.
- Private members of vanilla classes are reached through `@Accessor` / `@Invoker` interfaces in `mixin/accessor/`; names are prefixed `dragit$`.
- Mixin target strings use Yarn names. Loom remaps them into the mixin classes at build time (the JAR has no refmap). A renamed method in a new Minecraft version fails at runtime, not at compile time, so test the packaged JAR.
- Add every new mixin class to `drag-it.mixins.json`; `verifyJar` fails the build if a listed class is missing from the JAR.

Exact Minecraft signatures are in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md).

## Localization

The only user-visible text the mod adds is the "Fixed position" hint (`drag-it.hint.locked`). The mod's lang files are only loaded when Fabric API is present, so the hint is rendered with `Text.translatableWithFallback` and an English/Russian fallback chosen by the selected language. Keep the fallback strings and both lang files in sync. Reuse vanilla translation keys where vanilla already has the text (for example the incompatible-pack dialog).

## Changelog

`CHANGELOG.md` is user-facing and is published verbatim as the release notes on GitHub, Modrinth, and CurseForge. Keep it current as part of the change, not at release time.

- Add an entry for every player-visible change: new or changed gestures, fixes, compatibility (new versions/loaders, dependency requirements), and notable architecture or documentation changes. Skip internal-only edits.
- Format: `## [<mod_version>] - YYYY-MM-DD`, newest first; group bullets under `###` headings already in use (`Overall`, `Fixes`, `Compatibility`, `Architecture`, `Documentation`); one short bullet per change.
- The version must equal `mod_version` in `gradle.properties` (including `-alpha` / `-beta`). The release workflow fails if the section is missing and publishes everything up to the next `## [` heading, so never nest another `## [` heading inside a section.
- Unreleased work may sit under `## [Unreleased]` at the top; rename it when releasing.
- Bumping `mod_version` and adding its changelog section happen together. Full rules and release steps: [docs/WORKFLOW.md](docs/WORKFLOW.md#changelog).

## Porting to a new Minecraft version

Add a target in `settings.gradle.kts` and fix API differences with narrow Stonecutter conditions — never a new long-lived branch. The checklist, the baseline API table, and the notes from every earlier port are in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md); fill its entry for the new version as you go. Start with the `ResourcePackEntry.render` wrap and the accessors.

## Common issues

Build and run problems (wrong JDK, locked files on Windows, `verifyJar` failures): [docs/WORKFLOW.md](docs/WORKFLOW.md#troubleshooting). Code-level pitfalls:

- **Mod loads but nothing is draggable in the packaged JAR** → the Mixin config is not registered, or a mixin target was renamed; check the log for Mixin errors and run `verifyJar`.
- **A press on an arrow no longer works** → `MouseMixin` swallows the press until the cursor moves; the release must replay the click to vanilla (`onRelease` in `PENDING`).
- **Raw `drag-it.hint.locked` shown instead of text** → the lang files are not loaded without Fabric API; keep the fallback in `DragRenderer.lockedHint()`.
