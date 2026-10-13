# Version Notes

Per-Minecraft-version notes: exact dependency versions, API differences, and quirks. Read this before porting to a new version or changing a version-specific condition. Build and release steps are in [WORKFLOW.md](WORKFLOW.md); player-facing changes go in [../CHANGELOG.md](../CHANGELOG.md).

All names in this file and in the source are **official Mojang mappings**, the same as in Handycam, so snippets move between the two repositories unchanged. Yarn is not used (an earlier prototype was written against Yarn and migrated).

## How to use and fill this file

**When to write here**

- You add a target to `settings.gradle.kts` → add an entry (copy the template below).
- You bump a dependency of an existing target → update that entry's dependency list.
- You hit an API difference, a quirk, or a bug specific to one version → record it in that version's entry.
- You finish testing a target's packaged JAR → update its `Verified` line.

**What to put in an entry**

- *Dependencies:* the exact values from `gradle.properties` (copy, do not paraphrase) plus the Java version.
- *API differences:* only what differs from the baseline ([MC 1.21.1](#mc-1211--fabric--neoforge) below). Give the Minecraft signature, the file that adapts to it, and the Stonecutter condition used (syntax: Stonecutter docs; keep conditions narrow).
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
## MC X.Y.Z — Fabric / NeoForge

Targets: `X.Y.Z-fabric`, `X.Y.Z-neoforge` · first mod version: A.B.C · date: YYYY-MM-DD

### Dependencies
- java_version = 21
- fabric_loader_version = …
- fabric_loader_min_version = …
- neoforge_version = …
- neoforge_loader_version_range = …

### API differences (vs. baseline)
| Area | Baseline (1.21.1) | X.Y.Z | Where / condition |
|---|---|---|---|
| `PackSelectionScreen` fields and `repositionElements()` | see baseline | … | `mixin/PackSelectionScreenMixin.java`, `//? if …` |
| `Screen.render()` / `keyPressed()` | see baseline | … | `mixin/ScreenMixin.java` |
| `MouseHandler.onPress()` | see baseline | … | `mixin/MouseHandlerMixin.java` |
| `PackEntry.render()` | see baseline | … | `mixin/PackEntryMixin.java` |
| `PackSelectionModel` fields | see baseline | … | `mixin/accessor/` |

### Quirks
- …

### Verified
- YYYY-MM-DD, mod A.B.C: Fabric ☐ NeoForge ☐ (checklist in WORKFLOW.md → Packaged-JAR validation)
```

## Porting checklist

For each new version, after adding the target:

- [ ] Add the target to `settings.gradle.kts`; configure its loader dependencies (loader build script, `gradle.properties`, or a Stonecutter property table once the matrix grows).
- [ ] `PackSelectionScreen`: the `availablePackList`, `selectedPackList`, and `model` fields, `repositionElements()`, `onClose()`, `clearSelected()`.
- [ ] `PackSelectionModel`: `selected`, `unselected`, `onListChanged`, `updateRepoSelectedList()`, and the `Entry` methods (`isFixedPosition`, `canSelect`, `canUnselect`, `select`, `unselect`, `getCompatibility`).
- [ ] `TransferableSelectionList` and `PackEntry`: the `render` signature, `renderSelection`, the `screen`, `pack`, and `parent` fields, `getScrollbarPosition`, `getPackId`.
- [ ] `AbstractSelectionList`: `itemHeight`, `headerHeight`, `scrollbarVisible()` (check which class declares it), and the scroll API used by auto-scroll (`ListGeometry.scrollAmount` / `clampScroll`, `setScrollAmount`).
- [ ] `MouseHandler.onPress` signature and the `minecraft` / `xpos` / `ypos` fields; `Screen.render` and `Screen.keyPressed` signatures.
- [ ] `Window` size methods (`getScreenWidth` vs. `getWidth`), `GuiGraphics` drawing calls (`fill`, scissor, `pose()` type: `PoseStack` vs. a 2D stack).
- [ ] Express differences with narrow Stonecutter conditions, not copied files.
- [ ] Mixin config registered for both loaders in the packaged JAR (`verifyJar` passes).
- [ ] Run the [Mixin audit](#mixin-audit) on both loaders, then the whole packaged-JAR checklist, including the data pack screen.
- [ ] Add the target to CI artifact validation (it counts targets automatically) and to the release acceptance matrix.
- [ ] Fill this file's entry for the version; add a `CHANGELOG.md` line under `Compatibility`.

Likely trouble spots, in order: `PackEntry.render` / `renderContent` and `TransferableSelectionList` (the list widgets were reworked in several releases: where a row's position comes from, whether the title is a child, what `children()` and `getSelected()` return), then the input events (`MouseHandler`, `Screen.keyPressed`, `mouseClicked`), `PackSelectionModel` internals, and `GuiGraphics` (matrix stack, outlines); then loader metadata and dependency version ranges. Also check whether the pack screen gained a way to show fewer packs than the model holds (the 1.21.10 search box): the drop logic counts on the lists matching the model.

## Mixin audit

Mixin target names (`method = "..."`, `@Accessor("...")`, `@Shadow` fields) are not checked at compile time on either loader: a wrong name builds fine and fails only when the game applies the mixin. Mixin can force that to happen at startup. Add this temporarily, run the dev client, and remove it again (do not commit it):

```java
// NeoForge: in the constructor of DragItNeoForge, with `IEventBus bus` as its parameter
bus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
    System.out.println("DRAGIT_AUDIT_OK");
}));

// Fabric: a ClientModInitializer registered under "entrypoints" → "client" in fabric.mod.json
public void onInitializeClient() {
    org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
    System.out.println("DRAGIT_AUDIT_OK");
}
```

Run `runActiveClient` for each loader, wait for the main menu, and read `versions/<target>/run/logs/latest.log`. A healthy run logs `Force-loading class …` for `PackSelectionScreen`, `PackSelectionModel`, `TransferableSelectionList`, `TransferableSelectionList$PackEntry`, `AbstractSelectionList`, and `MouseHandler`, then `DRAGIT_AUDIT_OK`, with no `InvalidInjectionException`, `InvalidAccessorException`, or `Mixin apply` errors. The success line is printed even when a mixin was rejected with an `ERROR` (it is just skipped), so search the log for `ERROR` too. It proves the mixins apply; it does not exercise the gestures, which need the [scripted smoke test](#scripted-gesture-smoke-test-dev-client) or the packaged-JAR checklist.

To find a renamed member when porting, compare the old and new mapping files from the Loom cache by their official (obfuscated) names, or read `PackSelectionScreen` and friends in the generated sources (`genSources`).

## Target entries

### MC 1.21.11 — Fabric / NeoForge

Targets: `1.21.11-fabric`, `1.21.11-neoforge` · first mod version: the release after 0.0.1-alpha (see `## [Unreleased]` in the changelog) · date: 2026-10-12

#### Dependencies
```properties
java_version=21
fabric_loader_version=0.19.5        # min 0.17.3 (versions/1.21.11-fabric/gradle.properties)
neoforge_version=21.11.45           # versions/1.21.11-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[1.21.11,1.21.12)   # NeoForge descriptor
```
Official Mojang mappings on both loaders (`ResourceLocation` is called `Identifier` here; the mod does not use it). Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.17.3 minimum is an assumption taken from what Fabric API requires on 1.21.11 (not a tested floor).

#### API differences (vs. 1.21.10; everything else in the [1.21.10 entry](#mc-12110--fabric--neoforge) applies as well)
| Area | 1.21.10 | 1.21.11 | Where / condition |
|---|---|---|---|
| Dropping the vanilla single selection | `PackSelectionScreen.clearSelected()` | removed (it only did `setSelected(null)` on both lists, in every version) | `DragController.clearVanillaSelection`, no condition |
| Enable / disable arrow hit zones | measured from the entry's layout origin, `d <= 32` | measured from the icon (`getContentX/Y`), a 32×32 box with the left half for "disable" | `ListGeometry.onToggleArrow`, `//? if >=1.21.11` |

Unchanged and checked by the Mixin audit: every mixin target and accessor of the 1.21.10 entry, `MouseHandler.onButton(long, MouseButtonInfo, int)`, `Screen.render` / `keyPressed(KeyEvent)`, `PackEntry.renderContent`, `renderSelection` on `AbstractSelectionList`, the list geometry (rows, header, scrollbar).

#### Quirks
- Pack icons, names and descriptions are now `StringWidget` / `MultiLineTextWidget` children of the entry that `renderContent` positions from the entry's own layout position, so drawing the lifted card by shifting the matrix (`GuiCompat.drawEntry`) keeps working.
- The lists are added through `layout.addToContents`, and the search box takes the initial focus through a `setInitialFocus()` override. Keyboard shortcuts of the mod stay off while a text field has focus (existing check).

#### Verified
- 2026-10-12, mod 0.0.1-alpha: Mixin audit on the Fabric dev client clean (all six target classes force-loaded, no error lines); NeoForge is covered by the smoke test below (no separate audit run). Scripted gesture smoke test on the Fabric and NeoForge dev clients: see the [1.21.10 entry](#mc-12110--fabric--neoforge) for what it covers; all gesture checks passed. `clean buildAndCollect` passes for all eight targets.
- Manual packaged-JAR gesture test on 1.21.11: not done yet (Fabric ☐ NeoForge ☐).

### MC 1.21.10 — Fabric / NeoForge

Targets: `1.21.10-fabric`, `1.21.10-neoforge` · first mod version: the release after 0.0.1-alpha (see `## [Unreleased]` in the changelog) · date: 2026-10-12

1.21.5–1.21.9 were skipped. The Stonecutter condition `>=1.21.10` marks the first version looked at after 1.21.4; the changes below happened somewhere in between (input events and the GUI matrix in 1.21.6–1.21.9), so adding any of those versions needs a fresh check of each condition.

#### Dependencies
```properties
java_version=21
fabric_loader_version=0.19.5        # min 0.17.3 (versions/1.21.10-fabric/gradle.properties)
neoforge_version=21.10.64           # versions/1.21.10-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[1.21.10,1.21.11)   # NeoForge descriptor
```
Official Mojang mappings on both loaders. Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.17.3 minimum is the loader released with 1.21.10 (Fabric API requires 0.17.0 or newer there), not a tested floor. MixinExtras 0.5.5 in the Fabric and 0.5.0 in the NeoForge dev runtime.

#### API differences (vs. baseline 1.21.1; 1.21.4 changes are in its own entry)
| Area | Baseline (1.21.1) | 1.21.10 | Where / condition |
|---|---|---|---|
| Mouse button hook | `MouseHandler.onPress(long, int button, int action, int mods)` | `onButton(long, MouseButtonInfo, int action)`, button and modifiers are in the record | `mixin/MouseHandlerMixin.java`, `//? if >=1.21.10` |
| Window handle | `Window.getWindow()` | `handle()` | `GuiCompat.handle` |
| Key hook | `Screen.keyPressed(int, int, int)` | `keyPressed(KeyEvent)` | `mixin/ScreenMixin.java` |
| Replaying a held-back click | `Screen.mouseClicked(x, y, button)` / `mouseReleased(x, y, button)` | `mouseClicked(MouseButtonEvent, boolean doubleClick)` / `mouseReleased(MouseButtonEvent)` | `GuiCompat.click` / `release` |
| GUI matrix stack | `PoseStack` (3D `translate`, `scale`, `pushPose` / `popPose`, depth by Z) | `Matrix3x2fStack` (`pushMatrix` / `popMatrix`, 2D `translate` / `scale`, depth by draw order) | `GuiCompat.push/pop/translate/scale/raise` |
| Outline | `GuiGraphics.renderOutline` | `submitOutline` (1.21.10 only: deferred to the end of the frame, above everything drawn later) | `GuiCompat.outline` draws four `fill`s on every version |
| List entries | each row's position is computed by the list; the title is drawn by the list (`headerHeight`) | each `Entry` stores its own `x/y/width/height` (`getContentX/Y`, 2 px padding); the title is the first child (`TransferableSelectionList.HeaderEntry`); `children()` / `getSelected()` are typed `TransferableSelectionList.Entry` | `ListGeometry.entries`, `selected`, `headerHeight`, `//? if >=1.21.10` |
| Row pitch | field `itemHeight` | field `defaultEntryHeight` (36) | `mixin/accessor/AbstractSelectionListAccessor.java` |
| Row cell | `rowLeft - 2` to `rowLeft - 2 + rowWidth` (`getRowLeft()` includes a +2 offset) | `getRowLeft()` to `getRowLeft() + rowWidth - 6 if a scrollbar is shown`, `rowWidth = width - 4` | `ListGeometry.contentLeft/cellLeft/cardRight/frameRight` |
| Row hit test | arithmetic on `itemHeight` / `headerHeight` | `Entry.isMouseOver` | `ListGeometry.hit` |
| Row render | `PackEntry.render(GuiGraphics, index, y, x, width, height, mouseX, mouseY, hovered, delta)` | `renderContent(GuiGraphics, mouseX, mouseY, hovered, delta)` reading its position from the entry | `mixin/PackEntryMixin.java`, `GuiCompat.drawEntry` |
| Selection frame | `TransferableSelectionList.renderSelection(GuiGraphics, y, w, h, border, fill)` | `AbstractSelectionList.renderSelection(GuiGraphics, E entry, int border)` | `mixin/TransferableSelectionListMixin.java` now targets `AbstractSelectionList` |
| List-changed callback | `PackSelectionModel.onListChanged` is a `Runnable` | `Consumer<EntryBase>` (focus this entry afterwards, `null` for none) | `mixin/accessor/PackSelectionModelAccessor.java`, `DragController.refreshLists` |
| Search box | none | `PackSelectionScreen.search` filters what both lists show | `DragController.filtered` (no pack is lifted while a list shows fewer packs than the model holds) |
| Arrow hit zones | `d = mouseX - getRowLeft()`, `d <= 32` | same arithmetic, but `getRowLeft()` is now the cell edge, two pixels left of the icon | `ListGeometry.onToggleArrow` |

Unchanged, checked by the Mixin audit and the smoke test: `PackSelectionScreen` fields (`availablePackList`, `selectedPackList`, `model`), `repositionElements`, `onClose`; `PackSelectionModel` (`selected`, `unselected`, `updateRepoSelectedList`, the `Entry` methods); `PackEntry` (`pack`, `parent`, `getPackId`); `TransferableSelectionList.screen` and `scrollBarX`; `AbstractScrollArea` (`scrollAmount`, `refreshScrollAmount`, `scrollbarVisible`); `MouseHandler.xpos` / `ypos`; `Window.getScreenWidth` and friends; `GuiGraphics.fill`, `enableScissor`, `drawString`.

#### Quirks
- A `@WrapMethod` handler must repeat the target's erased parameter types exactly. `renderSelection`'s entry parameter is `AbstractSelectionList$Entry`, a protected class a mixin in another package cannot name, and `Object` is rejected at apply time. A mixin cannot extend its own target either (`Super class ... was not found in the hierarchy of target class`). The way out is an `@Inject` at `HEAD` that takes no arguments, which Mixin allows.
- The Mixin audit prints its success line even when a mixin was rejected with an `ERROR` in the log (the mixin is simply skipped). Always search the log for `ERROR` as well, not just for the success line.
- `GuiGraphics.submitOutline` defers its box to the end of the frame, so outlines drawn for the cards of a dragged group ended up above the front card's fill. Drawing outlines with plain `fill`s keeps the order.
- The first child of a pack list is now the title row, so row indices of the list's `children()` are one more than pack indices. All mod code counts packs only (`ListGeometry.entries`).
- The NeoForge dev profile starts with a built-in `mod_resources` pack in the enabled list; it is not fixed-position, so it can be re-ordered like the others.
- Test packs written for the dev client need `min_format` and `max_format` next to `pack_format` once the format is above 64; without them the pack is rejected ("missing mandatory fields").

#### Scripted gesture smoke test (dev client)
The Mixin audit proves mixins apply, not that gestures work. A temporary harness (not committed) drove the real `DragController` in the dev client of every target: it created five folder packs, opened `PackSelectionScreen` on the render thread (`Minecraft.execute`), set the pointer by writing `MouseHandler.xpos` / `ypos`, called `DragController.onMouseButton` / `onKey` / `onEscape`, took screenshots with `Screenshot.grab`, and compared the model's pack order after each gesture. Two small test mixins were needed: one makes `DragController.leftButtonHeld` return true while a scripted button is down (otherwise `beginFrame` treats the missing GLFW button as a lost release), the other drops real mouse movement and clicks while the script runs. Checked: every visible row hit-tests to itself at two scroll offsets; a pack dragged to the top, the end, and inside the enabled list lands at the exact gap; dropping outside both lists and Esc change nothing; Ctrl+click selects two packs, dragging one lifts both; Enter sends the group; a filtered list is not draggable.

#### Verified
- 2026-10-12, mod 0.0.1-alpha: Mixin audit on both dev clients (Fabric clean once the rejected `renderSelection` mixin was fixed; NeoForge: all six target classes force-loaded, no error lines). Scripted gesture smoke test on the Fabric and NeoForge dev clients: all gesture checks passed. `clean buildAndCollect` passes for all eight targets (`verifyJar` included).
- Manual packaged-JAR gesture test on 1.21.10: not done yet (Fabric ☐ NeoForge ☐).

### MC 1.21.4 — Fabric / NeoForge

Targets: `1.21.4-fabric`, `1.21.4-neoforge` · first mod version: the release after 0.0.1-alpha (see `## [Unreleased]` in the changelog) · date: 2026-10-12

#### Dependencies
```properties
java_version=21
fabric_loader_version=0.19.5        # min 0.16.9 (versions/1.21.4-fabric/gradle.properties)
neoforge_version=21.4.158           # versions/1.21.4-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[1.21.4,1.21.5)   # NeoForge descriptor
```
Official Mojang mappings on both loaders, same as the baseline. Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.16.9 minimum is an assumption (the loader generation 1.21.4 launched with), not a tested floor. MixinExtras 0.5.3 in the NeoForge dev runtime.

#### API differences (vs. baseline)
| Area | Baseline (1.21.1) | 1.21.4 | Where / condition |
|---|---|---|---|
| Scroll offset | `AbstractSelectionList.getScrollAmount()` | `scrollAmount()` (now in `AbstractScrollArea`) | `ListGeometry.scrollAmount`, `//? if >=1.21.4` |
| Re-clamp the scroll offset | `clampScrollAmount()` | `refreshScrollAmount()`; `setScrollAmount` clamps on its own | `ListGeometry.clampScroll`, `//? if >=1.21.4` |
| `scrollbarVisible()` | declared by `AbstractSelectionList` | declared by the new superclass `AbstractScrollArea` | `mixin/accessor/AbstractScrollAreaAccessor.java`, the `@Mixin` target changes, `//? if >=1.21.4` |
| Scrollbar X | `TransferableSelectionList.getScrollbarPosition()` | `scrollBarX()` | `mixin/accessor/TransferableSelectionListAccessor.java`, `//? if >=1.21.4` |

Unchanged, checked with `javap` on the 1.21.4 mapped jar and by the Mixin audit: `PackSelectionScreen` (`availablePackList`, `selectedPackList`, `model`, `repositionElements`, `onClose`, `clearSelected`), `PackSelectionModel` (`selected`, `unselected`, `onListChanged`, `updateRepoSelectedList`, the `Entry` methods), `PackEntry` (`pack`, `parent`, `render(GuiGraphics, 7 × int, boolean, float)`, `getPackId`), `TransferableSelectionList.screen` and `renderSelection`, `AbstractSelectionList.itemHeight` / `headerHeight` and its row geometry, `MouseHandler.onPress(long, int, int, int)` with `minecraft` / `xpos` / `ypos`, `Screen.render` / `keyPressed`, `Window` size methods, `GuiGraphics` (`fill`, scissor, `drawString`), `pose()` is still a `PoseStack`.

#### Quirks
- A Mixin `@Invoker` / `@Accessor` finds only members the target class declares itself. Pointing `AbstractSelectionListAccessor` at the inherited `scrollbarVisible` would build and then fail when the game applies it; that is why the scrollbar invoker has its own interface with a version-dependent target.
- Stonecutter version predicates compare against the version string of the node (`1.21.4`). The condition boundary is `>=1.21.4` because only 1.21.1 and 1.21.4 had been looked at when it was written (1.21.10 and 1.21.11 have since been added with their own conditions); if 1.21.2 / 1.21.3 are ever added, check where each rename actually happened and narrow the conditions.
- `runClient` in the dev environment exits with `0xC0000409` on Windows when the audit helper calls `System.exit` from a thread; that is the helper, not the mod.

#### Verified
- 2026-10-12, mod 0.0.1-alpha: `clean buildAndCollect` passes for all four targets (`verifyJar` included). Mixin audit on both dev clients: all mixins applied, `DRAGIT_AUDIT_OK`, no errors.
- 2026-10-12: the scripted gesture smoke test (see the 1.21.10 entry) also ran on the Fabric dev client of 1.21.4 after the list helpers were reworked for 1.21.10; all gestures behaved correctly.
- Manual packaged-JAR gesture test on 1.21.4: not done yet (Fabric ☐ NeoForge ☐).

### MC 1.21.1 — Fabric / NeoForge

Targets: `1.21.1-fabric`, `1.21.1-neoforge` · first mod version: 0.0.1-alpha · date: 2026-10-11 · **baseline for all diffs**

#### Dependencies
```properties
java_version=21
fabric_loader_version=0.19.5        # min 0.16.4
neoforge_version=21.1.259
neoforge_loader_version_range=[4,)
```
Mappings: official Mojang on both loaders. `fabric.mod.json` and `neoforge.mods.toml` pin Minecraft to exactly `1.21.1` (`[1.21.1,1.21.2)` on NeoForge). No Fabric API, no other libraries. MixinExtras (`@WrapMethod`) ships with both loaders: 0.5.5 in the dev runtimes of Fabric Loader 0.19.5 and NeoForge 21.1.259.

#### API shapes (as used by the source tree)

| Area | Signature / note |
|---|---|
| `PackSelectionScreen` | Fields `availablePackList`, `selectedPackList` (`TransferableSelectionList`) and `model` (`PackSelectionModel`) via `@Accessor`. `repositionElements()` injected at `TAIL` (first init, resize, return from a dialog); `onClose()` at `HEAD`; `clearSelected()` called directly. Data pack screens use the same class |
| `Screen.render` / `Screen.keyPressed` | `(GuiGraphics, int, int, float)` and `(int, int, int) → boolean`. `PackSelectionScreen` declares neither and Mixin cannot inject into inherited methods, so the hooks sit on `Screen` and bail out for anything that is not a `PackSelectionScreen` |
| `MouseHandler.onPress` | `(long window, int button, int action, int mods)` injected at `HEAD`, cancellable; the one place that sees every press and release and can hold a press back from vanilla. Fields `minecraft`, `xpos`, `ypos` are shadowed (`xpos`/`ypos` are physical window pixels) |
| `TransferableSelectionList.PackEntry.render` | `(GuiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float partialTick)`, wrapped with `@WrapMethod`; fields `pack` and `parent` via `@Accessor`; `getPackId()` |
| `TransferableSelectionList.renderSelection` | `(GuiGraphics, int y, int entryWidth, int entryHeight, int borderColor, int fillColor)`, wrapped; skipped because the mod draws the fading highlight itself. Field `screen`, invoker `getScrollbarPosition` |
| `AbstractSelectionList` | Fields `itemHeight`, `headerHeight`, invoker `scrollbarVisible` (own interface `AbstractScrollAreaAccessor`); `getSelected()`, `clampScrollAmount()`, `getScrollAmount()` / `setScrollAmount()`, `getRowLeft()`, `getX()`, `getRight()`, `getBottom()` used directly |
| `PackSelectionModel` | Fields `selected`, `unselected` (`List<Pack>`), `onListChanged` (`Runnable`, rebuilds both widget lists); invoker `updateRepoSelectedList()`. Vanilla has no "insert at index", so the mod edits the lists directly |
| `PackSelectionModel.Entry` | `isFixedPosition()`, `canSelect()`, `canUnselect()`, `select()`, `unselect()`, `getCompatibility()` |
| `Pack` (`server.packs.repository`) | `getId()`, `isFixedPosition()`, `isRequired()`, `getCompatibility()`, `getDefaultPosition()` (its `insert(...)` takes `Pack::selectionConfig`) |
| `Minecraft` / `Window` | `font`, `mouseHandler`, `screen`, `getWindow()`. Window pixels: `getScreenWidth()` / `getScreenHeight()`; GUI-scaled: `getGuiScaledWidth()` / `getGuiScaledHeight()`; `getGuiScale()`; `getWindow()` is the GLFW handle |
| `GuiGraphics` | `fill`, `enableScissor` / `disableScissor`, `drawString`, `renderOutline`; `pose()` is a `PoseStack` (`pushPose()` / `popPose()`, 3D `translate`) |
| Drop confirmation | `ConfirmScreen` with `Component.translatable("pack.incompatible.confirm.title")` and `getCompatibility().getConfirmation()` |

#### Loader notes
- Fabric: Mixins registered in `fabric.mod.json`; no entry point and no Mod Menu integration (Mod Menu lists the mod and its icon from the metadata alone). Built with Loom through `loom-back-compat`; the Mojang → intermediary remap is inlined into the mixin classes, so the JAR has no `refmap`.
- NeoForge: Mixins registered via `[[mixins]]` in `neoforge.mods.toml`; `javafml` needs an `@Mod` class, so `DragItNeoForge` is an empty `Dist.CLIENT` class. Runtime names are Mojang names, so the target strings are used as they are. All dependencies are `side = "CLIENT"`.
- Stonecutter conditions select loaders (`//? fabric`, `//? neoforge`) and, from the 1.21.4 port, Minecraft versions (`//? if >=1.21.4`); the 1.21.1 branch of such a condition is the baseline.

#### Quirks
- NeoForge mod ids match `[a-z][a-z0-9_]{1,63}`; the prototype's `drag-it` is invalid there, hence `dragit`.
- Yarn `Window.getWidth()` / `getHeight()` (window pixels) became Mojang `getScreenWidth()` / `getScreenHeight()`, while Mojang `getWidth()` / `getHeight()` is the framebuffer size. The old names still compile, so a mechanical rename silently gives wrong pointer coordinates under HiDPI scaling.
- The mod draws no text, so it has no lang files. On Fabric a mod's lang files are only loaded when Fabric API is present: any text added later needs `Component.translatableWithFallback` and a test of the Fabric JAR both with and without Fabric API.
- The built-in Default pack is *required* but not *fixed-position* in 1.21.1: it can be re-ordered like any pack and never disabled. Only fixed-position (pinned) packs block drops across them; a required pack is refused as a target only by the available list (shown red, `DropTarget.Block.REQUIRED`). `PackSelectionModel.Entry.canUnselect()` is `isSelected() && !isRequired()` in 1.21.1: there is no "last enabled pack" rule. In vanilla 1.21.1 the only fixed-position pack is the server's resource pack (`DownloadedPackSource`: required, `TOP`, fixed); Default (`ClientPackSource`) is required and `BOTTOM` but not fixed, and the built-in (High Contrast, Programmer Art) and folder packs are neither. A mod can register a fixed pack too.
- Vanilla re-sorts the available list when the screen reopens, so only the enabled order persists.
- The icon is 16×16 pixel art. NeoForge sets `logoBlur = false` in the descriptor; Mod Menu has no metadata option for icon filtering.

#### Verified
- 2026-10-12, mod 0.0.1-alpha: `clean buildAndCollect` passes (both `verifyJar` checks). The Mixin audit ran on both dev clients: all mixins applied, no errors (see [Mixin audit](#mixin-audit)).
- 2026-10-12: the scripted gesture smoke test (see the 1.21.10 entry) also ran on the Fabric dev client of 1.21.1 after the list helpers were reworked for 1.21.10; all checks passed.
- Manual packaged-JAR gesture test of 0.0.1-alpha: not recorded here — fill in on the release run.
