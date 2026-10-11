# Version Notes

Per-Minecraft-version notes: exact dependency versions, API differences, and quirks. Read this before porting to a new version or changing a version-specific condition. Build and release steps are in [WORKFLOW.md](WORKFLOW.md); player-facing changes go in [../CHANGELOG.md](../CHANGELOG.md).

Unlike Handycam, which uses official Mojang mappings, this project uses **Yarn** mappings. Class and method names in snippets copied between the two repositories differ. Yarn has no mappings for the unobfuscated Minecraft releases (26.1 and later), so a port to those versions means moving to Mojang mappings first.

## How to use and fill this file

**When to write here**

- You add a target to `settings.gradle.kts` → add an entry (copy the template below).
- You bump a dependency of an existing target → update that entry's dependency list.
- You hit an API difference, a quirk, or a bug specific to one version → record it in that version's entry.
- You finish testing a target's packaged JAR → update its `Verified` line.

**What to put in an entry**

- *Dependencies:* the exact values from `gradle.properties` (copy, do not paraphrase) plus the Java version.
- *API differences:* only what differs from the baseline ([MC 1.21.1](#mc-1211--fabric) below). Give the Minecraft signature, the file that adapts to it, and the Stonecutter condition used (syntax: Stonecutter docs; keep conditions narrow).
- *Quirks:* anything that cost you time — crashes, mixin targets that moved, loader behavior.
- *Verified:* date, mod version, and which checklist items you ran on the packaged JAR.

**What not to put here**

- General rules and architecture → `CLAUDE.md` / `AGENTS.md`.
- Anything players should read → `CHANGELOG.md`.
- Step-by-step commands → `WORKFLOW.md`.

**Conventions**

- Newest Minecraft version first; one `##` entry per Minecraft version (loaders as sub-notes).
- Never delete an entry when a target is dropped: move it to an "Archived ports" section.
- Keep entries factual and short; link to commits or tags instead of pasting logs.

### Template

```md
## MC X.Y.Z — Fabric

Targets: `X.Y.Z-fabric` · first mod version: A.B.C · date: YYYY-MM-DD

### Dependencies
- java_version = 21
- fabric_loader_version = …
- fabric_loader_min_version = …
- yarn_mappings = …

### API differences (vs. baseline)
| Area | Baseline (1.21.1) | X.Y.Z | Where / condition |
|---|---|---|---|
| `PackScreen` fields and `initTabNavigation()` | see baseline | … | `mixin/PackScreenMixin.java`, `//? if …` |
| `Screen.render()` / `keyPressed()` | see baseline | … | `mixin/ScreenMixin.java` |
| `Mouse.onMouseButton()` | see baseline | … | `mixin/MouseMixin.java` |
| `PackListWidget` / `ResourcePackEntry.render()` | see baseline | … | `mixin/ResourcePackEntryMixin.java` |
| `ResourcePackOrganizer` fields | see baseline | … | `mixin/accessor/` |

### Quirks
- …

### Verified
- YYYY-MM-DD, mod A.B.C: Fabric ☐ (checklist in WORKFLOW.md → Packaged-JAR validation)
```

## Porting checklist

For each new version, after adding the target:

- [ ] Add the target to `settings.gradle.kts`; configure its dependencies (build script, `gradle.properties`, or a Stonecutter property table once the matrix grows).
- [ ] `PackScreen`: the `availablePackList`, `selectedPackList`, and `organizer` fields, `initTabNavigation()`, and `close()`.
- [ ] `ResourcePackOrganizer`: `enabledPacks`, `disabledPacks`, `updateCallback`, `refreshEnabledProfiles()`, and the `Pack` methods (`isPinned`, `canBeEnabled`, `canBeDisabled`, `enable`, `disable`, `getCompatibility`).
- [ ] `PackListWidget` and `ResourcePackEntry`: the `render` signature, `drawSelectionHighlight`, the `screen`, `pack`, and `widget` fields, `getScrollbarX`.
- [ ] `EntryListWidget`: `itemHeight`, `headerHeight`, `isScrollbarVisible`, and the scroll API used by auto-scroll.
- [ ] `Mouse.onMouseButton` signature and the `x` / `y` fields; `Screen.render` and `Screen.keyPressed` signatures.
- [ ] `DrawContext` drawing calls (`fill`, scissor, `getMatrices()` type: `MatrixStack` vs. a 2D stack) and `Text.translatableWithFallback`.
- [ ] Express differences with narrow Stonecutter conditions, not copied files.
- [ ] Mixin config registered in the packaged JAR (`verifyJar` passes).
- [ ] Run the whole packaged-JAR checklist, including the data pack screen.
- [ ] Add the target to CI artifact validation (it counts targets automatically).
- [ ] Fill this file's entry for the version; add a `CHANGELOG.md` line under `Compatibility`.

Likely trouble spots, in order: `ResourcePackEntry.render` and `PackListWidget` (the list widgets were reworked in several releases), then `ResourcePackOrganizer` internals, then `DrawContext`.

## Target entries

### MC 1.21.1 — Fabric

Targets: `1.21.1-fabric` · first mod version: 0.0.1-alpha · date: 2026-10-11 · **baseline for all diffs**

#### Dependencies
```properties
java_version=21
fabric_loader_version=0.19.5        # min 0.16.4
yarn_mappings=1.21.1+build.3
```
`fabric.mod.json` pins Minecraft to exactly `1.21.1`. No Fabric API, no other libraries: MixinExtras (`@WrapMethod`) ships with Fabric Loader.

#### API shapes (as used by the source tree, Yarn names)

| Area | Signature / note |
|---|---|
| `PackScreen` | Fields `availablePackList`, `selectedPackList` (`PackListWidget`) and `organizer` (`ResourcePackOrganizer`) via `@Accessor`. `initTabNavigation()` injected at `TAIL` (first init, resize, return from a dialog); `close()` at `HEAD`. Data pack screens use the same class |
| `Screen.render` / `Screen.keyPressed` | `(DrawContext, int, int, float)` and `(int, int, int) → boolean`. `PackScreen` declares neither and Mixin cannot inject into inherited methods, so the hooks sit on `Screen` and bail out for anything that is not a `PackScreen` |
| `Mouse.onMouseButton` | `(long window, int button, int action, int mods)` injected at `HEAD`, cancellable; the one place that sees every press and release and can hold a press back from vanilla. Fields `client`, `x`, `y` are shadowed (`x`/`y` are physical window pixels) |
| `PackListWidget.ResourcePackEntry.render` | `(DrawContext, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta)`, wrapped with `@WrapMethod`; fields `pack` and `widget` via `@Accessor` |
| `PackListWidget.drawSelectionHighlight` | `(DrawContext, int y, int entryWidth, int entryHeight, int borderColor, int fillColor)`, wrapped; skipped because the mod draws the fading highlight itself. Field `screen`, invoker `getScrollbarX` |
| `EntryListWidget` | Fields `itemHeight`, `headerHeight`, invoker `isScrollbarVisible`; `getScrollAmount()` / `setScrollAmount()`, `getRowLeft()`, `getX()`, `getRight()`, `getBottom()` used directly |
| `ResourcePackOrganizer` | Fields `enabledPacks`, `disabledPacks` (`List<ResourcePackProfile>`), `updateCallback` (`Runnable`, rebuilds both widget lists); invoker `refreshEnabledProfiles()`. Vanilla has no "insert at index", so the mod edits the lists directly |
| `ResourcePackOrganizer.Pack` | `isPinned()`, `canBeEnabled()`, `canBeDisabled()`, `enable()`, `disable()`, `getCompatibility()` |
| `ResourcePackProfile` | `getId()`, `isPinned()`, `isRequired()`, `getCompatibility()`, `getInitialPosition()` |
| `DrawContext` | `fill`, `enableScissor` / `disableScissor`, `drawTextWithShadow`, `getMatrices()` is a `MatrixStack` (`push()` / `pop()`, 3D `translate`) |
| Drop confirmation | `ConfirmScreen` with `Text.translatable("pack.incompatible.confirm.title")` and `getCompatibility().getConfirmMessage()` |

#### Loader notes
- Fabric only. The Mixin config is registered in `fabric.mod.json` (`"mixins"`); the mod has no entry points and no Mod Menu integration.
- Fabric Loader 0.16.4 is the version the mod was first developed and tested against, hence the minimum; `@WrapMethod` needs the MixinExtras that Fabric Loader bundles. Development runs on the newer version in `gradle.properties`.
- Stonecutter conditions are not used yet; the tree is identical for the only target.

#### Quirks
- Loom 1.18 inlines the Yarn → intermediary remap into the mixin classes, so the packaged JAR has no `refmap`. This is expected; `verifyJar` only checks a refmap if the config names one.
- The mod's lang files are only loaded when Fabric API is present, so `DragRenderer` uses `Text.translatableWithFallback` and picks the fallback text from the selected language. Test the packaged JAR both with and without Fabric API.
- The built-in Default pack is *required* but not *pinned* in 1.21.1: it can be re-ordered like any pack and never disabled. Only pinned packs block drops.
- Vanilla re-sorts the available list when the screen reopens, so only the enabled order persists.

#### Verified
- Builds in CI-equivalent conditions locally (`clean buildAndCollect` plus `verifyJar`) as of 2026-10-11.
- Manual packaged-JAR test of 0.0.1-alpha: not recorded here — fill in on the release run.
