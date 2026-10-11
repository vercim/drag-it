# CLAUDE.md

Drag It! — client-side Minecraft mod, smooth drag & drop for the resource pack and data pack selection screens. Fabric + NeoForge, Java 21, official Mojang mappings, one shared source tree built with Stonecutter. Build, CI, and release setup mirror [Handycam](https://github.com/vercim/handycam); keep the two in step.

`AGENTS.md` only points here, so there is a single copy of these rules.

## Docs map

| Document | Read it when | Update it when |
|---|---|---|
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | building, running, switching targets (commands for Windows and macOS), CI, packaged-JAR validation, releasing, build troubleshooting | commands, tasks, CI, or the release flow change |
| [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md) | porting to a new Minecraft version, or touching version-specific code and dependency versions | a target is added, a dependency is bumped, or you hit a version-specific API difference or quirk (the file explains how to fill it) |
| [CHANGELOG.md](CHANGELOG.md) | preparing a release | any player-visible change (see [Changelog](#changelog)) |
| [README.md](README.md) | checking what players are told | gestures, requirements, or limitations change |

## Current state

- Version `0.0.1-alpha`, the first pre-release (not yet published).
- Targets: `1.21.1-fabric`, `1.21.1-neoforge`, declared once in `settings.gradle.kts`.
- `vcsVersion = 1.21.1-fabric`: commit with `.sc_active_version` set to `1.21.1-fabric`.
- Mod id is `dragit` on both loaders. NeoForge only accepts `[a-z][a-z0-9_]{1,63}`, so a hyphenated id such as `drag-it` is invalid there. The repository, README links, and platform slugs keep `drag-it`.
- No Fabric API and no other libraries. MixinExtras comes with both loaders; the Mixin API is on both classpaths.
- Metadata pins Minecraft to exactly `1.21.1`.

## Rules that must not be forgotten

- IDE-only runs prove nothing about loader integration; validate the packaged JAR from `build/libs/` in a clean client profile (steps in [docs/WORKFLOW.md](docs/WORKFLOW.md)).
- Both JARs must register `dragit.mixins.json` and contain every mixin class, and loader-specific files must be absent from the other loader's JAR. `verifyJar` (part of `build`) enforces this; a missing registration makes the mod load while every injection silently does nothing.
- Mixin target strings are **not** checked at compile time: a wrong method or field name still builds and only fails when the game applies the mixin. After touching a target string or porting, check it in a running client (see "Mixin audit" in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md)).
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
    ListGeometry.java              — row geometry of vanilla's TransferableSelectionList, recomputed from live state
    ControllerHolder.java          — implemented by PackSelectionScreen (via mixin): one DragController per screen
  mixin/                           — one mixin per vanilla class it targets, named after that class
    MouseHandlerMixin.java         — MouseHandler.onPress HEAD: sees every press and release, can hold a press back
    ScreenMixin.java               — Screen.render HEAD/TAIL and Screen.keyPressed HEAD, no-ops for non-pack screens
    PackSelectionScreenMixin.java  — owns the controller; cancels the gesture on re-layout and close
    PackEntryMixin.java            — @WrapMethod render: hides the lifted row, slides rows, draws fading highlights
    TransferableSelectionListMixin.java — @WrapMethod renderSelection: skips vanilla's instant frame
    accessor/                      — accessors for PackSelectionScreen, PackSelectionModel, TransferableSelectionList,
                                     AbstractSelectionList, PackEntry
  neoforge/DragItNeoForge.java     — the @Mod class javafml requires; empty, the mod works through Mixin only
src/main/resources/
  assets/dragit/lang/              — en_us.json, ru_ru.json (keep in sync)
  fabric.mod.json, META-INF/neoforge.mods.toml, dragit.mixins.json, icon.png
```

Platform entry points: Fabric has none (the mod is pure Mixin); NeoForge needs the `@Mod` class above.

Loader-specific code is wrapped in Stonecutter conditions (`//? fabric { … //?}`, `//? neoforge { … *///?}`); the inactive loader's code appears commented out in the source tree. Syntax and rules: [docs/WORKFLOW.md](docs/WORKFLOW.md#conditional-sources). Keep a `package` declaration outside the conditional block so the inactive target still compiles an empty file. Express Minecraft-version differences with narrow local conditions, never by copying files.

Build scripts: `build.fabric.gradle.kts` (Loom via `loom-back-compat`, publishes `remapJar`), `build.neoforge.gradle.kts` (ModDevGradle, publishes `jar`), `stonecutter.gradle.kts` (defines `runActiveClient`, `buildActive`, `clean`). Each loader script defines its own `verifyJar`, `buildAndCollect`, and `publishMods`.

## Architecture

`DragController` is a state machine, one instance per `PackSelectionScreen`:

```
IDLE --press on a movable pack--> PENDING --cursor passes the threshold--> DRAGGING
PENDING --release--> IDLE (the swallowed click is replayed to vanilla)
DRAGGING --release / Esc--> SETTLING (the lifted card glides into its slot) --arrived--> IDLE
```

- The mod stores no pack state. Re-ordering edits the model's own lists and calls its change callback, so "Done" applies exactly what is shown. Moving between lists goes through vanilla `Entry.select()` / `Entry.unselect()` first, then the pack is moved to the exact index.
- Everything is driven by real time (`System.nanoTime()` per frame), never by ticks. Rates in `DragItSettings` are "per second" and applied as `1 - exp(-dt * rate)`, so motion is identical at any FPS. Do not add tick-based animation.
- A drop *gap* `g` is an index into the target list with the dragged packs removed; the pack ends up at `list.add(g, pack)`. It is the same number for "move inside a list" and "come from the other list".
- Pinned packs (fixed position) are never lifted and nothing is dropped across them. Required packs (the built-in Default pack) can be re-ordered but never disabled. Do not conflate the two.
- Any `RuntimeException` in the controller cancels the gesture and logs once (`fail()`); the screen must never be left stuck in a drag.
- Multi-selection follows file-manager rules; the doc comment on `modifierClick` is the specification. The selection lives in one list at a time.

## Mixin rules

- `PackSelectionScreen` does not declare `render` or `keyPressed`, and Mixin cannot inject into inherited methods, so those hooks live on `Screen` and return immediately for anything that is not a `PackSelectionScreen`.
- `MouseHandlerMixin` is the only place that can hold a press back from vanilla; a plain click is replayed to vanilla on release so the arrows keep working.
- Private members of vanilla classes are reached through `@Accessor` / `@Invoker` interfaces in `mixin/accessor/`; names are prefixed `dragit$`.
- Target strings use Mojang names. NeoForge applies them as they are; Fabric's Loom remaps them into the mixin classes at build time (the JAR has no refmap). A renamed method in a new Minecraft version fails at runtime, not at compile time.
- `Window.getWidth()` / `getHeight()` are the *framebuffer* size in Mojang names. Window pixels, which the mouse position is in, are `getScreenWidth()` / `getScreenHeight()`.
- Add every new mixin class to `dragit.mixins.json`; `verifyJar` fails the build if a listed class is missing from the JAR.

Exact Minecraft signatures are in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md).

## Loader integration

- **Mixins:** Fabric registers `dragit.mixins.json` in `fabric.mod.json` (`"mixins"`); NeoForge needs `[[mixins]]` in `META-INF/neoforge.mods.toml`.
- **Entry point:** NeoForge's `javafml` refuses a mod without a `@Mod` class, so `DragItNeoForge` exists and is empty. It is `Dist.CLIENT` only.
- **Metadata:** each JAR carries only its own descriptor (`fabric.mod.json` or `neoforge.mods.toml`); the build excludes the other.
- **Icon:** `icon.png` is pixel art. NeoForge sets `logoBlur = false`; Mod Menu has no option for icon filtering, so nothing is configurable on Fabric.
- **Plugins:** before changing the Gradle wrapper, check compatibility of Stonecutter, Loom Back Compat, Fabric Loom, and NeoForge ModDev.

## Localization

The only user-visible text the mod adds is the "Fixed position" hint (`dragit.hint.locked`). On Fabric the mod's lang files are only loaded when Fabric API is present, so the hint is rendered with `Component.translatableWithFallback` and an English/Russian fallback chosen by the selected language. Keep the fallback strings and both lang files in sync. Reuse vanilla translation keys where vanilla already has the text (for example the incompatible-pack dialog).

## Changelog

`CHANGELOG.md` is user-facing and is published verbatim as the release notes on GitHub, Modrinth, and CurseForge. Keep it current as part of the change, not at release time.

- Add an entry for every player-visible change: new or changed gestures, fixes, compatibility (new versions/loaders, dependency requirements), and notable architecture or documentation changes. Skip internal-only edits.
- Format: `## [<mod_version>] - YYYY-MM-DD`, newest first; group bullets under `###` headings already in use (`Overall`, `Fixes`, `Compatibility`, `Architecture`, `Documentation`); one short bullet per change.
- The version must equal `mod_version` in `gradle.properties` (including `-alpha` / `-beta`). The release workflow fails if the section is missing and publishes everything up to the next `## [` heading, so never nest another `## [` heading inside a section.
- Unreleased work may sit under `## [Unreleased]` at the top; rename it when releasing.
- Bumping `mod_version` and adding its changelog section happen together. Full rules and release steps: [docs/WORKFLOW.md](docs/WORKFLOW.md#changelog).

## Porting to a new Minecraft version

Add a target in `settings.gradle.kts` and fix API differences with narrow Stonecutter conditions — never a new long-lived branch. The checklist, the baseline API table, and the notes from every earlier port are in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md); fill its entry for the new version as you go. Start with the `PackEntry.render` wrap and the accessors.

## Common issues

Build and run problems (wrong JDK, locked files on Windows, `verifyJar` failures): [docs/WORKFLOW.md](docs/WORKFLOW.md#troubleshooting). Code-level pitfalls:

- **Mod loads but nothing is draggable in the packaged JAR** → the Mixin config is not registered, or a mixin target was renamed; check the log for Mixin errors and run `verifyJar`.
- **NeoForge reports an invalid mod id** → ids may not contain hyphens; keep `mod_id=dragit`.
- **A press on an arrow no longer works** → `MouseHandlerMixin` swallows the press until the cursor moves; the release must replay the click to vanilla (`onRelease` in `PENDING`).
- **Pointer position is off by a constant factor** → `Window.getWidth()` was used instead of `getScreenWidth()` (see Mixin rules).
- **Raw `dragit.hint.locked` shown instead of text** → the lang files are not loaded without Fabric API; keep the fallback in `DragRenderer.lockedHint()`.
