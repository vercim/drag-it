# CLAUDE.md

Drag It! — client-side Minecraft mod, smooth drag & drop for the resource pack and data pack selection screens. Fabric + NeoForge, Java 21 (Java 25 from Minecraft 26.1), Mojang names (official mappings; 26.1 is unobfuscated), one shared source tree built with Stonecutter. Build, CI, and release setup mirror [Handycam](https://github.com/vercim/handycam); keep the two in step.

`AGENTS.md` is a symlink to this file.

## Docs map

| Document | Read it when | Update it when |
|---|---|---|
| [docs/WORKFLOW.md](docs/WORKFLOW.md) | building, running, switching targets (commands for Windows and macOS), CI, packaged-JAR validation, releasing, build troubleshooting | commands, tasks, CI, or the release flow change |
| [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md) | porting to a new Minecraft version, or touching version-specific code and dependency versions | a target is added, a dependency is bumped, or you hit a version-specific API difference or quirk (the file explains how to fill it) |
| [CHANGELOG.md](CHANGELOG.md) | preparing a release | any player-visible change (see [Changelog](#changelog)) |
| [README.md](README.md) | checking what players are told | gestures, requirements, or limitations change |

## Current state

- Version `0.0.1-alpha` is published (tag `v0.0.1-alpha`, GitHub pre-release, 2026-10-11). Later work collects under `## [Unreleased]` in `CHANGELOG.md`; `mod_version` is bumped, and that section renamed, at the next release. Never edit a published version's section.
- Targets: `1.21.1`, `1.21.4`, `1.21.10`, `1.21.11`, `26.1`, `26.1.2`, `26.2`, `26.3`, each as `<version>-fabric` and `<version>-neoforge`, declared once in `settings.gradle.kts`. Values that differ per target (`fabric_loader_min_version`, `neoforge_version`, `minecraft_version_range`, and `java_version` for the 26.x targets) live in `versions/<target>/gradle.properties`; the rest is in the root `gradle.properties`.
- The 1.21.4, 1.21.10 and 1.21.11 ports are merged into `main`; the 26.1, 26.1.2, 26.2 and 26.3 ports are in progress on `feature/minecraft-26-1-support`. All of them compile, `verifyJar` passes, and the Mixin audit and a scripted gesture test on the dev clients are clean on both loaders; the manual packaged-JAR gesture test is still open for each (see [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md)). 1.21.5-1.21.9 and 26.1.1 were skipped.

- Mod id is `dragit` on both loaders. NeoForge only accepts `[a-z][a-z0-9_]{1,63}`, so a hyphenated id such as `drag-it` is invalid there. The repository, README links, and platform slugs keep `drag-it`.
- No Fabric API and no other libraries. MixinExtras comes with both loaders; the Mixin API is on both classpaths.
- Metadata pins Minecraft to exactly the target's version (one release, e.g. `[1.21.4,1.21.5)` on NeoForge); a JAR never claims a version it was not built and tested against.

## Rules that must not be forgotten

- When adding support for a new Minecraft version or loader, run the scripted gesture test on the dev client of **every** new target (both loaders), each at least once, before calling the port done. A build, `verifyJar` or the Mixin audit does not count, and a target with no run recorded in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md) is untested; say so instead of writing "all passed".
- IDE-only runs prove nothing about loader integration; validate the packaged JAR from `build/libs/` in a clean client profile (steps in [docs/WORKFLOW.md](docs/WORKFLOW.md)).
- Both JARs must register `dragit.mixins.json` and contain every mixin class, and loader-specific files must be absent from the other loader's JAR. `verifyJar` (part of `build`) enforces this; a missing registration makes the mod load while every injection silently does nothing.
- Mixin target strings are **not** checked at compile time: a wrong method or field name still builds and only fails when the game applies the mixin. After touching a target string or porting, check it in a running client (see "Mixin audit" in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md)).
- Build commands differ per OS (`.\gradlew.bat` on Windows, `./gradlew` on macOS/Linux); take them from [docs/WORKFLOW.md](docs/WORKFLOW.md) rather than guessing.
- Gradle needs JDK 25+ to launch (Fabric Loom 1.18); the mod itself compiles with a Java 21 toolchain, and a Java 25 one for the 26.x targets (Minecraft 26.1 requires Java 25).
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
    SelectionBands.java            — the multi-selection outline: one animated band per run of selected rows, tied to its packs
    ListGeometry.java              — row geometry of vanilla's TransferableSelectionList, recomputed from live state; counts pack rows only (the title row of 1.21.10+ is left out)
    GuiCompat.java                 — drawing, input and window calls whose shape differs between Minecraft versions (matrix stack, outline, text, replaying a click, window handle, the open screen, key, button and modifier codes)
    ControllerHolder.java          — implemented by PackSelectionScreen (via mixin): one DragController per screen
  mixin/                           — one mixin per vanilla class it targets, named after that class
    MouseHandlerMixin.java         — MouseHandler.onPress HEAD: sees every press and release, can hold a press back
    ScreenMixin.java               — Screen.render HEAD/TAIL and Screen.keyPressed HEAD, no-ops for non-pack screens
    PackSelectionScreenMixin.java  — owns the controller; cancels the gesture on re-layout and close
    PackEntryMixin.java            — @WrapMethod render: hides the lifted row, slides rows, draws the selection highlights
    TransferableSelectionListMixin.java — renderSelection: skips vanilla's instant frame (targets AbstractSelectionList from 1.21.10)
    accessor/                      — accessors for PackSelectionScreen, PackSelectionModel, TransferableSelectionList,
                                     AbstractSelectionList, AbstractScrollArea (scrollbar), PackEntry
  neoforge/DragItNeoForge.java     — the @Mod class javafml requires; empty, the mod works through Mixin only
src/main/resources/
  fabric.mod.json, META-INF/neoforge.mods.toml, dragit.mixins.json, icon.png
```

Platform entry points: Fabric has none (the mod is pure Mixin); NeoForge needs the `@Mod` class above.

Loader-specific code is wrapped in Stonecutter conditions (`//? fabric { … //?}`, `//? neoforge { … *///?}`); the inactive loader's code appears commented out in the source tree. Syntax and rules: [docs/WORKFLOW.md](docs/WORKFLOW.md#conditional-sources). Keep a `package` declaration outside the conditional block so the inactive target still compiles an empty file. Express Minecraft-version differences with narrow local conditions, never by copying files.

Build scripts: `build.fabric.gradle.kts` (Loom via `loom-back-compat`, which applies the remapping Loom up to 1.21.11 and the unobfuscated one from 26.1; publishes `loomx.modJar`, that is `remapJar` or `jar`), `build.neoforge.gradle.kts` (ModDevGradle, publishes `jar`), `stonecutter.gradle.kts` (defines `runActiveClient`, `buildActive`, `clean`). Each loader script defines its own `verifyJar`, `buildAndCollect`, and `publishMods`.

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
- Pinned packs (fixed position) are never lifted and nothing is dropped across them. Required packs (the built-in Default pack) can be re-ordered but never disabled. Do not conflate the two. A refused drop is shown by colour only (red card and list frame, plus a red line at a pinned boundary); the mod draws no text for it.
- Any `RuntimeException` in the controller cancels the gesture and logs once (`fail()`); the screen must never be left stuck in a drag.
- Multi-selection follows file-manager rules; the doc comment on `modifierClick` is the specification. The selection lives in one list at a time.
- The multi-selection outline is drawn by `SelectionBands`, not per pack: one band per run of neighbouring selected rows, whose edges glide (rate `SELECTION_EDGE_RATE`) when the selection is edited. A band is anchored to the first and last pack of its run, so it follows its packs when vanilla rebuilds the list and only selection edits animate. Rows draw their own slice of it from `PackEntryMixin`, inside the row's (possibly sliding) frame.

## Mixin rules

- `PackSelectionScreen` does not declare `render` or `keyPressed`, and Mixin cannot inject into inherited methods, so those hooks live on `Screen` and return immediately for anything that is not a `PackSelectionScreen`.
- `MouseHandlerMixin` is the only place that can hold a press back from vanilla; a plain click is replayed to vanilla on release so the arrows keep working.
- Private members of vanilla classes are reached through `@Accessor` / `@Invoker` interfaces in `mixin/accessor/`; names are prefixed `dragit$`. An accessor only finds members the target class declares itself, not inherited ones, so when a member moves to a superclass in a new version the `@Mixin` target changes with it (see `AbstractScrollAreaAccessor`).
- Vanilla calls whose names differ between versions are wrapped in one helper (for example `ListGeometry.scrollAmount` / `clampScroll`) with a narrow Stonecutter condition, so the rest of the code stays version-free. Drawing and input differences live in `GuiCompat`; the mixins that must differ per version (`PackEntryMixin`, `MouseHandlerMixin`, `ScreenMixin`) keep one version-neutral method and put only the hook signature under the condition.
- Plain renames between versions (26.1: `GuiGraphics` became `GuiGraphicsExtractor`, and `render` became `extract` in the GUI hooks) are not written as conditions: `stonecutter.gradle.kts` swaps those tokens for the newer versions (`replacements.string`). The source tree keeps the older names; generated 26.x sources and the active 26.x state show the new ones, so code written while a 26.x target is active uses the new names and `Reset active project` turns them back. Changed signatures or behaviour still get a local condition.
- Key, button and modifier codes come from `GuiCompat` (built on `InputConstants`), never from `GLFW.*`: Minecraft 26.3 moved from GLFW to SDL, the codes differ, and the `org.lwjgl.glfw` package is not even on its compile classpath. Where GLFW is still needed (`GuiCompat.leftButtonHeld`, the Shift bit before 26.3) it sits in a `//? if <26.3` branch. The open screen and the loading overlay are read through `GuiCompat.screen` / `overlayShown` (they moved to `Minecraft.gui` in 26.2).
- A `@WrapMethod` handler must repeat the target's erased parameter types exactly, and a mixin cannot extend its own target. When a parameter is a class the mixin cannot name (for example the protected `AbstractSelectionList.Entry`), use an `@Inject` without arguments instead (`TransferableSelectionListMixin`).
- From 1.21.10 the pack screen has a search box, so a list can show fewer packs than the model holds. A `DropTarget.Result` therefore carries two gaps: `gap` counts the shown rows (placeholder, sliding rows, marker) and `modelGap` is the index into the model's list that a drop applies. A pack lands right before the shown pack under the gap, or right after the last shown pack; hidden packs never decide the spot, but a hidden pinned pack still limits the allowed range.
- Target strings use Mojang names. NeoForge applies them as they are; Fabric's Loom remaps them into the mixin classes at build time (the JAR has no refmap; from 26.1 nothing is remapped at all). A renamed method in a new Minecraft version fails at runtime, not at compile time.
- `Window.getWidth()` / `getHeight()` are the *framebuffer* size in Mojang names. Window pixels, which the mouse position is in, are `getScreenWidth()` / `getScreenHeight()`.
- Add every new mixin class to `dragit.mixins.json`; `verifyJar` fails the build if a listed class is missing from the JAR.

Exact Minecraft signatures are in [docs/VERSION_NOTES.md](docs/VERSION_NOTES.md).

## Loader integration

- **Mixins:** Fabric registers `dragit.mixins.json` in `fabric.mod.json` (`"mixins"`); NeoForge needs `[[mixins]]` in `META-INF/neoforge.mods.toml`.
- **Entry point:** NeoForge's `javafml` refuses a mod without a `@Mod` class, so `DragItNeoForge` exists and is empty. It is `Dist.CLIENT` only.
- **Metadata:** each JAR carries only its own descriptor (`fabric.mod.json` or `neoforge.mods.toml`); the build excludes the other.
- **Icon:** `icon.png` is pixel art. NeoForge sets `iconBlur = false` (26.2+, where `logoFile` is deprecated and warns at startup unless `iconFile` is also set) and `logoBlur = false` (older versions); Mod Menu has no option for icon filtering, so nothing is configurable on Fabric.
- **Plugins:** before changing the Gradle wrapper, check compatibility of Stonecutter, Loom Back Compat, Fabric Loom, and NeoForge ModDev.

## Localization

The mod adds no user-visible text and ships no lang files; a refused drop is shown in red only. If text is ever added: on Fabric a mod's lang files are only loaded when Fabric API is present, so render it with `Component.translatableWithFallback` and an English/Russian fallback chosen by the selected language, and reuse vanilla translation keys where vanilla already has the text (for example the incompatible-pack dialog).

## Changelog

`CHANGELOG.md` is user-facing and is published verbatim as the release notes on GitHub, Modrinth, and CurseForge. Keep it current as part of the change, not at release time.

- Add an entry for every player-visible change: new or changed gestures, fixes, and compatibility (new versions/loaders, dependency requirements), plus anything a contributor must know (a changed way to build, a dropped target). Skip internal changes: build plumbing, refactors, CI tweaks, docs upkeep.
- Format: `## [<mod_version>] - YYYY-MM-DD`, newest first; group bullets under the [Keep a Changelog](https://keepachangelog.com/) headings `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security` (only those used); one short bullet per change. Support for a new Minecraft version or loader goes under `Added`. The published `0.0.1-alpha` section keeps its older headings and is not rewritten.
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
