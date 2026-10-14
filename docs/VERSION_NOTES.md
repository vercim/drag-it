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

### MC 26.3 — Fabric / NeoForge

Targets: `26.3-fabric`, `26.3-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-14

26.3 moved the game from GLFW to **SDL3**: windowing, keyboard and mouse. The GUI classes the mod hooks into are the same as in 26.2.

#### Dependencies
```properties
java_version=25                     # versions/26.3-*/gradle.properties
fabric_loader_version=0.19.5        # min 0.19.3 (versions/26.3-fabric/gradle.properties)
neoforge_version=26.3.0.76          # versions/26.3-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[26.3,26.3.1)   # NeoForge descriptor
```
Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.19.3 minimum is what Fabric API 0.162.0+26.3 requires, not a tested floor. Resource pack format 97.1: test packs with `pack_format`, `min_format` and `max_format` all 97 are accepted.

#### API differences (vs. 26.2; everything else in the [26.2 entry](#mc-262--fabric--neoforge) applies as well)
| Area | 26.2 | 26.3 | Where / condition |
|---|---|---|---|
| Key codes (`KeyEvent.key()`) | GLFW keys (`A` = 65, Enter = 257, Esc = 256, Space = 32, keypad Enter = 335) | SDL scancodes (`A` = 4, Enter = 40, Esc = 41, Space = 44, keypad Enter = 88) | `GuiCompat.KEY_*`, taken from `InputConstants` |
| Mouse button numbers | left = 0, right = 1, middle = 2 | left = 1, middle = 2, right = 3 | `GuiCompat.MOUSE_LEFT` (`InputConstants.MOUSE_BUTTON_LEFT`) |
| Modifier bits | GLFW (`Shift` = 1, `Ctrl` = 2) | SDL (`Shift` = 3, `Ctrl` = 192: left and right keys are separate bits) | `GuiCompat.hasControl` / `hasShift` (`>=26.3` uses `InputConstants.MOD_SHIFT`) |
| Action codes | `PRESS` = 1, `RELEASE` = 0, `REPEAT` = 2 | `REPEAT` = -1 | `GuiCompat.isPress` |
| "Is the left button down" | `GLFW.glfwGetMouseButton(window, LEFT)` | `SDLMouse.SDL_GetMouseState(null, null) & SDL_BUTTON_LMASK` | `GuiCompat.leftButtonHeld`, `//? if >=26.3` |
| `MouseHandler.onButton` | private | public, same signature `(long, MouseButtonInfo, int)` | none (the mixin target is unchanged) |
| `KeyEvent` | `key`, `scancode`, `modifiers` | `key`, `keycode`, `modifiers`; `shortcutKey()` added | unused by the mod |

Unchanged, checked with `javap` on the merged jar and by the Mixin audit: every class, field and method the mixins and accessors target (see the [26.1 entry](#mc-261--fabric--neoforge)), `Window.handle()`, `getScreenWidth()` / `getGuiScaledWidth()`, `MouseHandler.xpos` / `ypos`, `GuiGraphicsExtractor.fill` / `enableScissor` / `text` / `pose()`.

#### Quirks
- `org.lwjgl.glfw` is no longer on the compile classpath, so any `GLFW.*` reference fails the build. The mod keeps GLFW only in `GuiCompat`, inside `//? if <26.3` branches (modifier bit of Shift for versions that predate `InputConstants.MOD_SHIFT`, and the button-state query). Everything else uses `InputConstants`, whose values are the right ones for every version.
- `MouseHandler.isLeftPressed()` is **not** a replacement for the platform query: vanilla sets it inside `onButton`, which the mod's hook has already cancelled when it swallows the press, so it would read "up" during every drag and the mod would think the release was lost.
- The `SDL_GetMouseState` path was run (it reports "up" while no button is held), but the "reports down" case needs a real held button and has not been exercised.
- Test scripts (see the scripted smoke test of the [1.21.10 entry](#mc-12110--fabric--neoforge)): the held-button test mixin now targets `GuiCompat.leftButtonHeld` (it was `DragController.leftButtonHeld`); `Minecraft.getMainRenderTarget()` is gone since 26.2 (`gameRenderer.mainRenderTarget()`); the first frames run before `Minecraft.gui` exists (guard against `null`). Events can also be sent through the real input path, which exercises the mixins and the code constants of a version: `MouseHandler.onButton` (`onPress` before 1.21.10; by reflection, it is private up to 26.2; the blocking test mixin must let these calls through) and `Screen.keyPressed`, for a plain drag, Ctrl+click, Enter, Ctrl+A and Esc.

#### Verified
- 2026-10-14, mod 1.0.0: Mixin audit on both dev clients clean (Fabric: all eight target classes force-loaded, `DRAGIT_AUDIT_OK`, no mixin errors; NeoForge: the not yet loaded ones force-loaded, no errors). Scripted gesture test on both dev clients, **including the real input path** (events sent through `MouseHandler.onButton` and `Screen.keyPressed`: plain drag, Ctrl+click, Enter, Ctrl+A, Esc): all checks passed. `clean buildAndCollect` passes for all sixteen targets.
- Manual packaged-JAR gesture test on 26.3: not done yet (Fabric ☐ NeoForge ☐).

### MC 26.2 — Fabric / NeoForge

Targets: `26.2-fabric`, `26.2-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-14

#### Dependencies
```properties
java_version=25                     # versions/26.2-*/gradle.properties
fabric_loader_version=0.19.5        # min 0.18.4 (versions/26.2-fabric/gradle.properties)
neoforge_version=26.2.0.90          # versions/26.2-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[26.2,26.2.1)   # NeoForge descriptor
```
Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.18.4 minimum is what Fabric API 0.161.0+26.2 requires, not a tested floor. Resource pack format 88.

#### API differences (vs. 26.1; everything else in the [26.1 entry](#mc-261--fabric--neoforge) applies as well)
| Area | 26.1 | 26.2 | Where / condition |
|---|---|---|---|
| The open screen | `Minecraft.screen` (field) | `Minecraft.gui.screen()` | `GuiCompat.screen`, `//? if >=26.2` |
| The loading overlay | `Minecraft.getOverlay()` | `Minecraft.gui.overlay()` | `GuiCompat.overlayShown`, `//? if >=26.2` |
| Opening a screen | `Minecraft.setScreen(Screen)` | `Minecraft.gui.setScreen(Screen)` (same behaviour; `Minecraft.setScreenAndShow` is still there) | `GuiCompat.setScreen`, `//? if >=26.2` |

Unchanged, checked with `javap` on the merged jars and by the Mixin audit: `PackSelectionScreen`, `PackSelectionModel`, `TransferableSelectionList` and its entries, `AbstractSelectionList`, `AbstractScrollArea` (all identical members), `MouseHandler.onButton`, `Screen.extractRenderState` / `keyPressed`, `GuiGraphicsExtractor`, `Window.handle()` and the size methods. The pack screen's own filter code changed internally (`filterEntries` takes a focus entry) without a visible effect.

#### Verified
- 2026-10-14, mod 1.0.0: Mixin audit and scripted gesture test (with the real input path, see the 26.3 entry) on both dev clients: all checks passed. `clean buildAndCollect` passes for all sixteen targets.
- Manual packaged-JAR gesture test on 26.2: not done yet (Fabric ☐ NeoForge ☐).

### MC 26.1.2 — Fabric / NeoForge

Targets: `26.1.2-fabric`, `26.1.2-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-13

26.1.1 was skipped. Each JAR pins exactly its own Minecraft version, so the 26.1 JAR does not load on 26.1.2 and the other way round.

#### Dependencies
```properties
java_version=25                     # versions/26.1.2-*/gradle.properties
fabric_loader_version=0.19.5        # min 0.18.4 (versions/26.1.2-fabric/gradle.properties)
neoforge_version=26.1.2.116         # versions/26.1.2-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[26.1.2,26.1.3)   # NeoForge descriptor
```
Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.18.4 minimum is what Fabric API 0.155.3+26.1.2 requires, not a tested floor. NeoForge 26.1.2 builds from .71 on are releases (the earlier ones were betas).

#### API differences (vs. 26.1)
None. `javap` on the merged jars of 26.1 and 26.1.2 shows identical members for every class the mod touches (`PackSelectionScreen`, `PackSelectionModel` and its entries, `TransferableSelectionList` and its entries, `AbstractSelectionList`, `AbstractScrollArea`, `MouseHandler`, `Screen`, `GuiGraphicsExtractor`, `Window`, `Minecraft`, `Pack`). Everything in the [26.1 entry](#mc-261--fabric--neoforge) applies.

#### Verified
- 2026-10-13, mod 1.0.0: Mixin audit on the Fabric dev client clean (all eight target classes force-loaded, `DRAGIT_AUDIT_OK`, no mixin errors); on NeoForge the audit forced the classes that were not loaded yet, without errors in the log. Scripted gesture smoke test (see the [1.21.10 entry](#mc-12110--fabric--neoforge)) on the Fabric and NeoForge dev clients: all gesture checks passed (the NeoForge profile's built-in `mod_resources` pack shifts the script's expected positions, as before; one Fabric run had a single Esc check fail and passed on the repeat). `clean buildAndCollect` passes for all twelve targets.
- Manual packaged-JAR gesture test on 26.1.2: not done yet (Fabric ☐ NeoForge ☐).

### MC 26.1 — Fabric / NeoForge

Targets: `26.1-fabric`, `26.1-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-13

26.1 is the first release with the year-based version number. Minecraft ships **unobfuscated** from here on and needs **Java 25**. The GUI classes the mod hooks into were already in the 1.21.11 shape (input events, `Matrix3x2fStack`, entries that carry their own position, the search box), so the port is renames plus build setup. 26.1.1 was skipped.

#### Dependencies
```properties
java_version=25                     # versions/26.1-*/gradle.properties
fabric_loader_version=0.19.5        # min 0.18.3 (versions/26.1-fabric/gradle.properties)
neoforge_version=26.1.0.19-beta     # versions/26.1-neoforge/gradle.properties
neoforge_loader_version_range=[4,)
minecraft_version_range=[26.1,26.1.1)   # NeoForge descriptor
```
Fabric Loom 1.18.4 builds both kinds of target; `loom-back-compat` applies `net.fabricmc.fabric-loom` (no remap) from 26.1. Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.18.3 minimum is what Fabric API 0.140.0+26.1 requires, not a tested floor. NeoForge has only betas for 26.1.0 (26.1.0.19-beta is the last). MixinExtras 0.5.5 in the Fabric dev runtime.

#### API differences (vs. 1.21.11; everything else in the [1.21.11 entry](#mc-12111--fabric--neoforge) and the [1.21.10 entry](#mc-12110--fabric--neoforge) applies as well)
| Area | 1.21.11 | 26.1 | Where / condition |
|---|---|---|---|
| Game and build | Loom remaps Mojang names to intermediary (`mappings(...)`, `remapJar`), Java 21 | unobfuscated: no mappings, no remap, the release JAR is the plain `jar`; Java 25 | `build.fabric.gradle.kts` through `loomx.applyMojangMappings()` and `loomx.modJar`; `java_version=25` in the target's `gradle.properties` |
| GUI drawing class | `GuiGraphics` | `GuiGraphicsExtractor` | token swap in `stonecutter.gradle.kts` |
| Screen hook | `Screen.render(GuiGraphics, int, int, float)` | `Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)` | swap of the string `"render"`, `ScreenMixin` |
| Entry drawing | `renderContent(GuiGraphics, int, int, boolean, float)` | `extractContent(GuiGraphicsExtractor, …)` | swap, `PackEntryMixin`, `GuiCompat.drawEntry` |
| Selection frame | `AbstractSelectionList.renderSelection(GuiGraphics, E, int)` | `extractSelection(GuiGraphicsExtractor, E, int)` | swap, `TransferableSelectionListMixin` |
| Drawing text | `GuiGraphics.drawString(font, text, x, y, color, shadow)` | `GuiGraphicsExtractor.text(...)` | `GuiCompat.text`, `//? if >=26.1` |
| Scrollbar shown | `AbstractScrollArea.scrollbarVisible()` | `scrollable()` (same body: `maxScrollAmount() > 0`) | `mixin/accessor/AbstractScrollAreaAccessor.java`, `//? if >=26.1` |

Unchanged, checked with `javap` on the merged jar and by the Mixin audit: `MouseHandler.onButton(long, MouseButtonInfo, int)` with `xpos` / `ypos`, `Window.handle()`, `Screen.keyPressed(KeyEvent)`, `mouseClicked` / `mouseReleased` with the event records, `PackSelectionScreen` (`availablePackList`, `selectedPackList`, `model`, `search`, `repositionElements`, `onClose`), `PackSelectionModel` (`selected`, `unselected`, `onListChanged` as a `Consumer<EntryBase>`, `updateRepoSelectedList`), `PackEntry` (`pack`, `parent`, `getPackId`), `TransferableSelectionList.screen` and `scrollBarX`, `AbstractSelectionList.defaultEntryHeight`, `AbstractScrollArea.scrollAmount` / `refreshScrollAmount`, `GuiGraphicsExtractor.fill` / `enableScissor` / `disableScissor` and `pose()` (still a `Matrix3x2fStack`). The arrow hit zones and list geometry follow the 1.21.11 rules.

#### Quirks
- A global `replacements.string` of Stonecutter covers the whole file, comments and the inactive side of conditions included, and runs in both directions. That is why the source tree (the vcs state, `1.21.1-fabric`) keeps the old names while any 26.x state shows the new ones. Switching between 26.x targets and back to the vcs target leaves no stray text even though `GuiGraphics` is a prefix of `GuiGraphicsExtractor` (checked: the diff after `Reset active project` holds only intended changes). Code written while a 26.x target is active must use the new names.
- Run `Reset active project` before committing, as always: with a 26.x target active, the sources differ from the committed ones in dozens of lines.
- A fresh dev profile opens the accessibility onboarding screen instead of the title screen, and it overrides a screen set by a script. Put `onboardAccessibility:false` into the profile's `options.txt` before a smoke test.
- `GuiGraphicsExtractor.outline` exists, but `GuiCompat.outline` keeps drawing four `fill`s (same result, one code path for every version).

#### Verified
- 2026-10-13, mod 1.0.0: Mixin audit on the Fabric dev client clean (all eight target classes force-loaded, `DRAGIT_AUDIT_OK`, no mixin errors); on NeoForge the audit forced the classes that were not loaded yet, without errors in the log. Scripted gesture smoke test on the Fabric and NeoForge dev clients: all gesture checks passed (NeoForge: the built-in `mod_resources` pack shifts the script's expected positions, as before); screenshots show the lifted card, its count badge, the placeholder and the list frame drawn correctly. `clean buildAndCollect` passes for all twelve targets, and the 26.x JARs carry Java 25 classes and `java >=25`.
- Manual packaged-JAR gesture test on 26.1: not done yet (Fabric ☐ NeoForge ☐).

### MC 1.21.11 — Fabric / NeoForge

Targets: `1.21.11-fabric`, `1.21.11-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-12

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

Targets: `1.21.10-fabric`, `1.21.10-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-12

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
| Click on a row body | `PackEntry.mouseClicked` falls through to `ObjectSelectionList.Entry.mouseClicked` (`true`): the list selects the row | ends with `false`: nothing is selected; an `@Inject` at `RETURN` makes it `true` for the left button | `mixin/PackEntryMixin.java`, `//? if <1.21.1` |
| Row hit test | arithmetic on `itemHeight` / `headerHeight` | `Entry.isMouseOver` | `ListGeometry.hit` |
| Row render | `PackEntry.render(GuiGraphics, index, y, x, width, height, mouseX, mouseY, hovered, delta)` | `renderContent(GuiGraphics, mouseX, mouseY, hovered, delta)` reading its position from the entry | `mixin/PackEntryMixin.java`, `GuiCompat.drawEntry` |
| Selection frame | `TransferableSelectionList.renderSelection(GuiGraphics, y, w, h, border, fill)` | `AbstractSelectionList.renderSelection(GuiGraphics, E entry, int border)` | `mixin/TransferableSelectionListMixin.java` now targets `AbstractSelectionList` |
| List-changed callback | `PackSelectionModel.onListChanged` is a `Runnable` | `Consumer<EntryBase>` (focus this entry afterwards, `null` for none) | `mixin/accessor/PackSelectionModelAccessor.java`, `DragController.refreshLists` |
| Search box | none | `PackSelectionScreen.search` filters what both lists show (a case-insensitive match on title, description and id; the shown packs keep the model's order) | `DropTarget.compute` turns the gap among the shown rows into an index of the model's list (`Result.gap` / `Result.modelGap`); `DragController.apply` uses the latter |
| Arrow hit zones | `d = mouseX - getRowLeft()`, `d <= 32` | same arithmetic, but `getRowLeft()` is now the cell edge, two pixels left of the icon | `ListGeometry.onToggleArrow` |

Unchanged, checked by the Mixin audit and the smoke test: `PackSelectionScreen` fields (`availablePackList`, `selectedPackList`, `model`), `repositionElements`, `onClose`; `PackSelectionModel` (`selected`, `unselected`, `updateRepoSelectedList`, the `Entry` methods); `PackEntry` (`pack`, `parent`, `getPackId`); `TransferableSelectionList.screen` and `scrollBarX`; `AbstractScrollArea` (`scrollAmount`, `refreshScrollAmount`, `scrollbarVisible`); `MouseHandler.xpos` / `ypos`; `Window.getScreenWidth` and friends; `GuiGraphics.fill`, `enableScissor`, `drawString`.

#### Quirks
- A `@WrapMethod` handler must repeat the target's erased parameter types exactly. `renderSelection`'s entry parameter is `AbstractSelectionList$Entry`, a protected class a mixin in another package cannot name, and `Object` is rejected at apply time. A mixin cannot extend its own target either (`Super class ... was not found in the hierarchy of target class`). The way out is an `@Inject` at `HEAD` that takes no arguments, which Mixin allows.
- The Mixin audit prints its success line even when a mixin was rejected with an `ERROR` in the log (the mixin is simply skipped). Always search the log for `ERROR` as well, not just for the success line.
- `GuiGraphics.submitOutline` defers its box to the end of the frame, so outlines drawn for the cards of a dragged group ended up above the front card's fill. Drawing outlines with plain `fill`s keeps the order.
- The first child of a pack list is now the title row, so row indices of the list's `children()` are one more than pack indices. All mod code counts packs only (`ListGeometry.entries`).
- The NeoForge dev profile starts with a built-in `mod_resources` pack in the enabled list; it is not fixed-position, so it can be re-ordered like the others.
- Test packs written for the dev client need `min_format` and `max_format` next to `pack_format` once the format is above 64; without them the pack is rejected ("missing mandatory fields"). The pack format of a version is in its `version.json` (`pack_version`; 84 on 26.1).
- Selection side effect of the list-changed callback: the entry it takes is focused after the rebuild (`screen.setFocused(list)` and `list.setFocused(entry)` in `TransferableSelectionList.updateList`), and focusing a pack selects it as vanilla's single selection. `Entry.select()` / `unselect()` fire it for every moved pack, so after a group move vanilla has selected the last pack of the group. `DragController.settleSelection` reads a new single selection as a click and ends the multi-selection; `followToList` therefore clears vanilla's selection after every group move. Seen on 26.2 and 26.3: the move to the enabled list happens to rebuild the lists once more (`keepRelativeOrder`, callback without a focus entry), which wipes the selection, the move back does not, so the group dropped its selection on the second Enter. What the game believes about the last input type (`Minecraft.getLastInputType()`) changes this by version, and 26.3 has no `KEYBOARD_OTHER` any more; the scripted test's K section presses with the keyboard type in one variant and with the mouse type in the other.
- Keyboard: up to 1.21.4 `TransferableSelectionList.keyPressed` sends the selected pack across on Enter **and** Space; from 1.21.10 `PackEntry.keyPressed` checks `KeyEvent.isConfirmation()`, which is Enter and keypad Enter only. `DragController.toggleSingle` gives Space back for the single vanilla selection (it calls `PackEntry.keyboardSelection()`, as Enter does).
- The search box has the keyboard focus when the screen opens (`PackSelectionScreen.setInitialFocus`), and a click the mod swallows (Ctrl/Shift+click, a drag) never moves it. `DragController.onKey` stays silent while an `EditBox` has the focus, so a Ctrl/Shift+click selection must take the focus to its list (`focusList`), or Enter, Space and Ctrl+A would do nothing. The scripted test must not clear the focus before the key checks (R5 to R7 set it on purpose).

#### Scripted gesture smoke test (dev client)
The Mixin audit proves mixins apply, not that gestures work. A harness, now kept in `tools/smoke/` (see [WORKFLOW.md](WORKFLOW.md#scripted-gesture-test)), drove the real `DragController` in the dev client of every target: it created five folder packs, opened `PackSelectionScreen` on the render thread (`Minecraft.execute`), set the pointer by writing `MouseHandler.xpos` / `ypos`, called `DragController.onMouseButton` / `onKey` / `onEscape`, took screenshots with `Screenshot.grab`, and compared the model's pack order after each gesture. Two small test mixins were needed: one makes `DragController.leftButtonHeld` return true while a scripted button is down (otherwise `beginFrame` treats the missing GLFW button as a lost release), the other drops real mouse movement and clicks while the script runs. Checked: every visible row hit-tests to itself at two scroll offsets; a pack dragged to the top, the end, and inside the enabled list lands at the exact gap; dropping outside both lists and Esc change nothing; Ctrl+click selects two packs, dragging one lifts both; Enter sends the group; with the search box on (a text that hides the built-in packs), a pack dragged from the other list between two shown packs, re-ordered to the top, sent to the other list, dropped at the end of the shown list (it lands above the hidden Default pack, not below it) and a Ctrl+click group dragged to the top all land at the expected spot of the model's list, and clearing the search shows the same order.

#### Verified
- 2026-10-12, mod 0.0.1-alpha: Mixin audit on both dev clients (Fabric clean once the rejected `renderSelection` mixin was fixed; NeoForge: all six target classes force-loaded, no error lines). Scripted gesture smoke test on the Fabric and NeoForge dev clients: all gesture checks passed. `clean buildAndCollect` passes for all eight targets (`verifyJar` included).
- Manual packaged-JAR gesture test on 1.21.10: not done yet (Fabric ☐ NeoForge ☐).
- 2026-10-14: scripted test sections K (a group sent back and forth with Enter, six presses, with the keyboard and with the mouse input type) and V (screenshots of the outlines in motion) on the dev client of all 18 targets, K after the fix: all 52 checks pass everywhere. Before the fix K failed on 26.2 and 26.3 (Fabric) and passed on 1.20.1 to 26.1. The full run in a fresh profile is not green on 1.21.10 and newer, with or without that change: R3 fails on Fabric (R2 already leaves the available list empty, so there is nothing for Ctrl+A to select), and NeoForge fails T1, T3, T5 (the available list ends up in a different order than the checks expect) and R3, and stops at R6 with an `ArrayIndexOutOfBoundsException`; cause not investigated.
- 2026-10-14, mod 1.0.0, `run-smoke.ps1` on Windows (before the 1.0.0 release): `1.21.10-fabric` and `26.3-neoforge` in profiles left over from earlier runs fail the first checks on their start state (the `resourcePacks` line in `options.txt` lists packs from the previous run): 16 PASS / 21 FAIL and 30 PASS / 7 FAIL, neither reaches DONE. With that line removed, `1.21.10-fabric` gives **89 PASS / 1 FAIL**: R3 only, for the reason above (R2 already moved every pack, so Ctrl+A in R3 has nothing to select). `26.3-neoforge` still shows T1, T3, T5, R3 and the `ArrayIndexOutOfBoundsException` of the note above (plus S1 and S2 from the leftover `file/TestA`); it was not re-run with a clean profile. The failing checks look like harness expectations, not gestures, but this is not proven.

### MC 1.21.4 — Fabric / NeoForge

Targets: `1.21.4-fabric`, `1.21.4-neoforge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-12

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
- Fabric: Mixins registered in `fabric.mod.json`; no entry point and no Mod Menu integration (Mod Menu lists the mod, its icon and the Discord link from `custom.modmenu.links` from the metadata alone). Built with Loom through `loom-back-compat`; the Mojang → intermediary remap is inlined into the mixin classes, so the JAR has no `refmap`.
- NeoForge: Mixins registered via `[[mixins]]` in `neoforge.mods.toml`; `javafml` needs an `@Mod` class, so `DragItNeoForge` is an empty `Dist.CLIENT` class. Runtime names are Mojang names, so the target strings are used as they are. All dependencies are `side = "CLIENT"`.
- Stonecutter conditions select loaders (`//? fabric`, `//? neoforge`) and, from the 1.21.4 port, Minecraft versions (`//? if >=1.21.4`); the 1.21.1 branch of such a condition is the baseline.

#### Quirks
- NeoForge mod ids match `[a-z][a-z0-9_]{1,63}`; the prototype's `drag-it` is invalid there, hence `dragit`.
- Yarn `Window.getWidth()` / `getHeight()` (window pixels) became Mojang `getScreenWidth()` / `getScreenHeight()`, while Mojang `getWidth()` / `getHeight()` is the framebuffer size. The old names still compile, so a mechanical rename silently gives wrong pointer coordinates under HiDPI scaling.
- The mod draws no text, so it has no lang files. On Fabric a mod's lang files are only loaded when Fabric API is present: any text added later needs `Component.translatableWithFallback` and a test of the Fabric JAR both with and without Fabric API.
- The built-in Default pack is *required* but not *fixed-position* in 1.21.1: it can be re-ordered like any pack and never disabled. Only fixed-position (pinned) packs block drops across them; a required pack is refused as a target only by the available list (shown red, `DropTarget.Block.REQUIRED`). `PackSelectionModel.Entry.canUnselect()` is `isSelected() && !isRequired()` in 1.21.1: there is no "last enabled pack" rule. In vanilla 1.21.1 the only fixed-position pack is the server's resource pack (`DownloadedPackSource`: required, `TOP`, fixed); Default (`ClientPackSource`) is required and `BOTTOM` but not fixed, and the built-in (High Contrast, Programmer Art) and folder packs are neither. A mod can register a fixed pack too.
- Vanilla re-sorts the available list when the screen reopens, so only the enabled order persists.
- The icon is 16×16 pixel art. NeoForge sets `logoBlur = false` in the descriptor (26.2 deprecated `logoFile`/`logoBlur` for `iconFile`/`iconBlur`/`bannerFile` and shows a loading warning screen when only the old key is set, so the descriptor carries both pairs; older versions ignore the new keys); Mod Menu has no metadata option for icon filtering.

#### Verified
- 2026-10-12, mod 0.0.1-alpha: `clean buildAndCollect` passes (both `verifyJar` checks). The Mixin audit ran on both dev clients: all mixins applied, no errors (see [Mixin audit](#mixin-audit)).
- 2026-10-12: the scripted gesture smoke test (see the 1.21.10 entry) also ran on the Fabric dev client of 1.21.1 after the list helpers were reworked for 1.21.10; all checks passed.
- Manual packaged-JAR gesture test of 0.0.1-alpha: not recorded here — fill in on the release run.

### MC 1.20.1 — Fabric / Forge

Targets: `1.20.1-fabric`, `1.20.1-forge` · first mod version: 1.0.0 (see `## [1.0.0]` in the changelog) · date: 2026-10-14

The first target older than the baseline and the first one on **Forge** instead of NeoForge (NeoForge starts at 1.20.2). Java 17. The GUI is still the pre-1.20.2 one: lists are `AbstractSelectionList` with protected bounds fields, there is no `repositionElements`.

#### Dependencies
```properties
java_version=17                     # versions/1.20.1-*/gradle.properties
fabric_loader_version=0.19.5        # min 0.14.21 (versions/1.20.1-fabric/gradle.properties)
forge_version=1.20.1-47.4.10        # versions/1.20.1-forge/gradle.properties
forge_loader_version_range=[47,)
minecraft_version_range=[1.20.1,1.20.2)   # Forge descriptor
pack_format=15                      # the pack.mcmeta Forge wants
```
Official Mojang mappings on both loaders. Forge 1.20.1 is built with ModDevGradle Legacy (`net.neoforged.moddev.legacyforge`, same version as the regular plugin, `build.forge.gradle.kts`). Only the dev-runtime Fabric Loader 0.19.5 has been run; the 0.14.21 minimum is what Fabric API requires on 1.20.1, not a tested floor. The Forge dependency range starts at the version the JAR was built and run with (47.4.10, the recommended build at the time; 47.4.28 was the latest), not at a tested floor. Forge 1.20.1 ships Mixin 0.8.5 and **no MixinExtras** (Fabric Loader's own copy is unused here).

#### API differences (vs. baseline 1.21.1)
| Area | Baseline (1.21.1) | 1.20.1 | Where / condition |
|---|---|---|---|
| Layout hook | `PackSelectionScreen.repositionElements()` | not there; `init()` (a resize runs it again) | `mixin/PackSelectionScreenMixin.java`, `//? if >=1.21.1` |
| Selection frame | `TransferableSelectionList.renderSelection(...)`, wrapped | declared only by `AbstractSelectionList` (`(GuiGraphics, int ×5)`); plain `@Inject` at `HEAD`, the same shape as from 1.21.10 | `mixin/TransferableSelectionListMixin.java`, `//? if >=1.21.10 \|\| <1.21.1` |
| Row hook | `@WrapMethod` on `PackEntry.render` | plain Mixin: `@ModifyVariable` for the hover flag, `@Inject` at `HEAD` (can cancel for the lifted row) and at `RETURN` (pops the matrix); the pending state is kept in a `@Unique` field | `mixin/PackEntryMixin.java`, `//? if <1.21.1`; the `>=1.21.1` branches share `dragit$begin` / `dragit$end` / `dragit$hover` with it |
| List bounds | widget getters `getX/getY/getRight/getBottom/getWidth/getHeight` | protected fields `x0/y0/x1/y1/width/height`; the getters (`getLeft`, `getTop`, ...) exist **on Forge only** (its patch adds them). Both loaders read them through `getRectangle()` | `ListGeometry.left/top/right/bottom/width/height`, `//? if >=1.21.1` |
| Scrollbar shown | `scrollbarVisible()` (invoker) | `getMaxScroll() > 0` (public); the accessor interface stays empty | `ListGeometry.scrollbarVisible`, `mixin/accessor/AbstractScrollAreaAccessor.java` |
| Re-clamp the scroll offset | `clampScrollAmount()` | `setScrollAmount(getScrollAmount())` (the setter clamps) | `ListGeometry.clampScroll` |
| Row hit test | arithmetic on `itemHeight` / `headerHeight` | the same, and vanilla also refuses the scrollbar strip (`x < getScrollbarPosition()`) | `ListGeometry.hit`, `//? if <1.21.1` |
| `Pack.Position.insert(List<T>, T, Function<T, ?>, boolean)` | the function reads the pack's `PackSelectionConfig` (`Pack::selectionConfig`) | reads the `Pack` itself (identity) | `DropTarget.compute` |
| Mixin config | `compatibilityLevel` `JAVA_21` | `JAVA_17`, set by the build scripts' `processResources` filter | `build.fabric.gradle.kts`, `build.forge.gradle.kts` |

Unchanged, checked with `javap` on the Forge and the Loom jars and by the scripted test: `PackSelectionScreen` fields (`availablePackList`, `selectedPackList`, `model`), `onClose`, `clearSelected`; `PackSelectionModel` (`selected`, `unselected`, `onListChanged` as a `Runnable`, `updateRepoSelectedList`); `PackEntry` (`pack`, `parent`, `render(GuiGraphics, 7 × int, boolean, float)`, `getPackId`, `keyboardSelection`); `TransferableSelectionList.screen` and `getScrollbarPosition`; `AbstractSelectionList.itemHeight` / `headerHeight` and the row geometry (`getRowTop` = `y0 + 4 - scroll + i * itemHeight + headerHeight`, `getRowLeft`, `getRowWidth` = `width`); `MouseHandler.onPress(long, int, int, int)` with `minecraft` / `xpos` / `ypos`; `Screen.render` / `keyPressed(int, int, int)`; `Window` size methods and `getWindow()`; `GuiGraphics` (`fill`, scissor, `drawString`, `pose()` as a `PoseStack`); `InputConstants` constants.

#### Forge integration
- **Mixin config:** 1.20.1 has no `[[mixins]]` table in `mods.toml`; the JAR manifest names the config (`MixinConfigs: dragit.mixins.json`, set in `build.forge.gradle.kts`).
- **SRG names:** Forge 1.20.1 runs on SRG names, so the Mixin annotation processor (`org.spongepowered:mixin:0.8.5:processor`, configured through the `mixin { }` block of the legacy plugin) writes `dragit.refmap.json`, and the `processResources` filter adds the `"refmap"` line to the Forge copy of `dragit.mixins.json` (the shared file names none). `@Shadow` fields are not in the refmap: `reobfJar` renames them in the classes. The release JAR is the `reobfJar` output in `build/libs`; the Mojang-named `jar` goes to `build/devlibs`. `verifyJar` checks the manifest attribute, the refmap and the mixin classes.
- **`mods.toml`:** dependencies use `mandatory = true` (not `type = "required"`); `displayTest = "IGNORE_ALL_VERSION"` because the mod has no server part; `logoBlur = false` keeps the pixel icon sharp.
- **`pack.mcmeta`:** Forge 1.20.1 shows a loading-warning screen ("failed to load a valid ResourcePackInfo") for a mod without one. It is a template (`${pack_format}`) that only the Forge build expands; the other two loaders exclude it.
- **`@Mod`:** `dev.vercim.dragit.forge.DragItForge`, empty, like `DragItNeoForge`.

#### Quirks
- The dev client of Forge 1.20.1 runs on Mojang names and does not read the refmap (log: "Reference map 'dragit.refmap.json' ... could not be read. If this is a development environment you can ignore this message"). So the dev run does **not** exercise the SRG path; only the packaged JAR in a real Forge install does.
- A test mixin that targets a class of this mod needs `remap = false`, or the Forge annotation processor fails the build ("Unable to locate obfuscation mapping"; `tools/smoke/HeldTestMixin.java`).
- In the vanilla 1.20.1 pack screen `PackEntry.mouseClicked` ends with `false` for a click on the body of a row (from 1.20.2 it falls through to `ObjectSelectionList.Entry.mouseClicked`, which answers `true`), so the list never selects the row and no selection frame shows; only the keyboard selects (`setFocused`). `PackEntryMixin.dragit$selectOnClick` (`<1.21.1` branch) turns that `false` into `true` for the left button, and the list then selects and focuses the row as in newer versions. `TransferableSelectionList.keyPressed` sends the selected pack across on Space and Enter (key codes 32 and 257 only, no keypad Enter); `DragController.toggleSingle` does the same for Space, which is harmless here. The scripted test's R5 expects the click itself to select the pack, on every version.
- The Forge `AbstractSelectionList` patch adds `getLeft/getTop/getRight/getBottom/getWidth/getHeight`; code that compiles on Forge can still fail on Fabric. Test both loaders.
- The scripted test has no search box (R6 and R7, S7) on this version, as on 1.21.1 and 1.21.4.
- `PackSelectionScreen.init()` is `protected`, which does not matter to a Mixin injection. It runs on the first layout and on every resize, where newer versions run `repositionElements`; the dialog-return case of the newer hook has not been checked on 1.20.1.

#### Verified
- 2026-10-14, mod 1.0.0: `:1.20.1-fabric:build` and `:1.20.1-forge:build` pass (`verifyJar` included). Scripted gesture test on both dev clients, run by hand on macOS (the same steps as `tools/smoke/run-smoke.ps1`): **22 PASS, 0 FAIL, DONE** each; screenshots show the lifted card, its count badge, the placeholder and the list frame drawn correctly; no Mixin error in `latest.log`. The Mixin audit helper was not added: the test loads every target class (screen, model, both lists, entries, `AbstractSelectionList`, `MouseHandler`) and `defaultRequire` is 1, so a mixin that did not apply would have crashed it.
- 2026-10-14, regression check of the shared code the port touched (`PackEntryMixin` split into `dragit$begin` / `dragit$end` / `dragit$hover`, the `ListGeometry` bounds helpers), same by-hand run on macOS: `1.21.1-fabric` 22 PASS / 0 FAIL, `1.21.4-neoforge` 22 PASS / 0 FAIL. `1.21.10-fabric` (36 PASS, R3 fails) and `26.3-neoforge` (31 PASS; T1, T3, T5 and R3 fail, then the harness throws `ArrayIndexOutOfBoundsException`) fail on this Mac **exactly the same way with the unchanged `main` tree** (checked in a separate worktree), so they are not caused by the port; the cause is not found yet (the by-hand runs differ from `run-smoke.ps1` runs on Windows, where these checks passed). `clean buildAndCollect` passes for all eighteen targets.
- 2026-10-14, after the click-to-select fix (`PackEntryMixin.dragit$selectOnClick`), `run-smoke.ps1` on Windows: `1.20.1-fabric` and `1.20.1-forge` **23 PASS, 0 FAIL** each (R5 now checks that the click selects the pack).
- Manual packaged-JAR gesture test on 1.20.1: not done yet (Fabric ☐ Forge ☐). For Forge this is what covers the SRG refmap; the reobfuscated JAR was only inspected (every target string is in the refmap with its `m_` / `f_` name, shadow fields and calls carry SRG names).
