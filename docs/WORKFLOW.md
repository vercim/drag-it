# Development, CI, and Release

How to build, run, test, and release Drag It!. For per-Minecraft-version API notes and porting, see [VERSION_NOTES.md](VERSION_NOTES.md). User-facing release notes live in [../CHANGELOG.md](../CHANGELOG.md).

Drag It! uses one [Stonecutter](https://stonecutter.kikugie.dev/) repository: a single shared source tree builds every Minecraft version and loader. Do not create long-lived per-version branches. The layout deliberately mirrors [Handycam](https://github.com/vercim/handycam), so the tooling and the release flow are the same in both projects.

## Target matrix

| Target | Loader | Java |
|---|---|---:|
| `1.20.1-fabric` | Fabric | 17 |
| `1.20.1-forge` | Forge | 17 |
| `1.21.1-fabric` | Fabric | 21 |
| `1.21.1-neoforge` | NeoForge | 21 |
| `1.21.4-fabric` | Fabric | 21 |
| `1.21.4-neoforge` | NeoForge | 21 |
| `1.21.10-fabric` | Fabric | 21 |
| `1.21.10-neoforge` | NeoForge | 21 |
| `1.21.11-fabric` | Fabric | 21 |
| `1.21.11-neoforge` | NeoForge | 21 |
| `26.1-fabric` | Fabric | 25 |
| `26.1-neoforge` | NeoForge | 25 |
| `26.1.2-fabric` | Fabric | 25 |
| `26.1.2-neoforge` | NeoForge | 25 |
| `26.2-fabric` | Fabric | 25 |
| `26.2-neoforge` | NeoForge | 25 |
| `26.3-fabric` | Fabric | 25 |
| `26.3-neoforge` | NeoForge | 25 |

The matrix is declared once in [`settings.gradle.kts`](../settings.gradle.kts). The active target for the convenience tasks is stored in `.sc_active_version`. Dependency values shared by all targets are in the root [`gradle.properties`](../gradle.properties); values that differ per target (`fabric_loader_min_version` for Fabric, `neoforge_version` and `minecraft_version_range` for NeoForge, `forge_version`, `forge_loader_version_range`, `minecraft_version_range` and `pack_format` for Forge, `java_version=17` for the 1.20.1 targets and `java_version=25` for the 26.x ones) are in `versions/<target>/gradle.properties`, which Gradle reads for that target only.

## Requirements

- JDK 25+ to launch Gradle (Fabric Loom 1.18 refuses to load on anything older); the toolchain that compiles the mod (Java 21, Java 17 for 1.20.1, or Java 25 for the 26.x targets) is provisioned automatically. CI installs JDK 17, 21 and 25.
- Windows: PowerShell, use `.\gradlew.bat`.
- macOS / Linux: use `./gradlew` (run `chmod +x gradlew` if it is not executable).
- To force a specific launcher JDK: macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`, Windows `$env:JAVA_HOME = "C:\path\to\jdk-25"`.

## Commands

Windows (PowerShell):

```powershell
.\gradlew.bat "Set active project to 1.21.4-fabric"   # or any other target; do this before runActiveClient
.\gradlew.bat runActiveClient
.\gradlew.bat buildActive
.\gradlew.bat buildAndCollect
.\gradlew.bat clean buildAndCollect
.\gradlew.bat :1.21.1-neoforge:build
```

macOS / Linux:

```bash
./gradlew "Set active project to 1.21.4-fabric"   # or any other target; do this before runActiveClient
./gradlew runActiveClient
./gradlew buildActive
./gradlew buildAndCollect
./gradlew clean buildAndCollect
./gradlew :1.21.1-neoforge:build
```

| Task | What it does |
|---|---|
| `Set active project to <target>` | Makes a target active and rewrites the version and loader comment blocks in `src/` for it |
| `Reset active project` | Back to `1.21.1-fabric`; run it before every commit |
| `runActiveClient` | Runs the client of the active target |
| `buildActive` | Builds the active target |
| `buildAndCollect` | Builds every target and collects release JARs in `build/libs/` |
| `:<target>:build` | Builds one target explicitly (useful when investigating loader integration) |
| `:<target>:verifyJar` | Checks the release JAR (also part of `build`, see [CI](#ci)) |
| `clean buildAndCollect` | Fresh full build; use for release verification only |

Do not use `clean` routinely: it discards Gradle and Minecraft preparation work.

Outputs: collected release JARs are in `build/libs/` (for example `dragit-0.0.1-alpha+1.21.1-fabric.jar` and `dragit-0.0.1-alpha+1.21.1-neoforge.jar`); per-target output is in `versions/<target>/build/libs/`. Forge 1.20.1 also leaves a Mojang-named `-dev` JAR in `versions/1.20.1-forge/build/devlibs/`; the release JAR is the reobfuscated one in `build/libs/`.

Switch the active target only with the `Set active project to <target>` task (all of them are listed by `gradlew tasks --group stonecutter`). It updates `.sc_active_version` **and** rewrites the version and loader comment blocks in `src/`. Editing `.sc_active_version` by hand is not enough: the next build compiles `src/` as it is and fails with errors such as "cannot find symbol `getScrollAmount()`". `Refresh active project` repairs comments left in a wrong state. Before committing, run `Reset active project` (back to `1.21.1-fabric`, the configured `vcsVersion`); the diff must not contain comment flips or a changed `.sc_active_version`.

## Conditional sources

Shared code is ordinary Java. Loader-specific code uses Stonecutter conditions; the inactive loader's code appears commented out in the source tree. The only loader-specific files today are the NeoForge and Forge `@Mod` classes (`//? forge { … *///?}` works the same way). The NeoForge one:

```java
package dev.vercim.dragit.neoforge;

//? neoforge {

/*import net.neoforged.fml.common.Mod;

@Mod(...)
public final class DragItNeoForge {
}
*///?}
```

Keep conditions as narrow as practical. If a whole entry point is loader-specific, keep its `package` declaration outside the conditional block so the inactive target generates a valid, otherwise empty source file. Minecraft-version differences are expressed the same way, next to the affected code (`//? if >=1.21.4 { … //?} else { /* … *///?}`; `ListGeometry.scrollAmount` is a small real example). Never copy files per version.

## CI

The build workflow (`.github/workflows/build.yml`) runs on pushes to `main` and pull requests only when build-relevant files change:

- `src/**`;
- Gradle, Stonecutter, wrapper, or build-script files;
- `.github/workflows/build.yml`.

Documentation-only changes (including `docs/`, `README.md`, `CHANGELOG.md`) do not start it. CI builds every target and verifies one release JAR per target.

`build` runs `verifyJar` on every target. It opens the release JAR and fails when:

- the loader descriptor (`fabric.mod.json`, `META-INF/neoforge.mods.toml` or `META-INF/mods.toml`) is missing, still has `${...}` placeholders, or declares the wrong mod id;
- the descriptor does not register `dragit.mixins.json` (Forge 1.20.1: the `MixinConfigs` manifest attribute does not);
- a class named in the Mixin config is missing from the JAR (or a `refmap` it names, if there is one; the Forge config must name its refmap, and the JAR must contain it);
- the license file is not bundled;
- another loader's descriptor or classes leaked into the JAR, or the NeoForge / Forge `@Mod` class is missing (Forge also needs its `pack.mcmeta`).

This guards against the failure that broke Handycam 1.3.2: a JAR that loads while every injection silently does nothing. It does **not** check that Mixin target names exist; see [Mixin audit](VERSION_NOTES.md#mixin-audit).

A change is ready to merge when:

- every target compiles and produces its release JAR;
- `verifyJar` passes on every target;
- behavior stays compatible with the previous release.

## Scripted gesture test

A Mixin audit proves the mixins apply, not that gestures work. `tools/smoke/` holds a harness that starts the real dev client, opens the resource pack screen, plays gestures through the real input path (drag, Ctrl+click groups, Enter, Space, Ctrl+A, Esc, the search box from 1.21.10) and compares the pack order after each one. It is not part of the mod: `run-smoke.ps1` copies it into `src/` for a run and removes it again, so it never reaches a JAR.

```powershell
.\tools\smoke\run-smoke.ps1                        # every target in versions/, one after the other
.\tools\smoke\run-smoke.ps1 -Targets 1.21.4-neoforge,26.3-fabric   # chosen targets only (comma-separated: a space makes PowerShell bind the second name to another parameter)
.\tools\smoke\run-smoke.ps1 -Uninstall            # clean up after a run that was killed
```

- Close every running dev client and Gradle run first; a running one holds the Gradle lock.
- Each target takes a few minutes. The script prints one verdict per target (`OK`, `FAIL`, `NO-LOG`, `INCOMPLETE`, `TIMEOUT`, `MIXIN-ERROR`, ...), writes `build/smoke/summary.txt`, and exits non-zero unless every target is `OK`. Details are in `versions/<target>/run/smoke.log` and `build/smoke/<target>.*.log`.
- A new target needs its `pack_format` in the table at the top of `run-smoke.ps1`; the script refuses to run a target it has no format for.
- Without PowerShell (macOS / Linux) the same steps work by hand: `Set active project to <target>`, copy the four harness files into `src/` and list the three test mixins in `dragit.mixins.json` after `MouseHandlerMixin`, write the target's `pack_format` into `versions/<target>/run/smoke.format` and `onboardAccessibility:false` and `soundCategory_master:0.0` (the script runs the client muted) into its `options.txt`, run `:<target>:runClient`, read `run/smoke.log` and `run/logs/latest.log`, then remove the harness and `Reset active project`. This is how 1.20.1 was run.
- When adding a Minecraft version or loader, run it for **every** new target and record the result in [VERSION_NOTES.md](VERSION_NOTES.md). The script is Windows-only (like the `.\gradlew.bat` commands here).

## Packaged-JAR validation

IDE runs are not enough for loader or Mixin changes. Before releasing, with a clean client profile and only the JAR from `build/libs/`:

- **Fabric:** Fabric Loader only, without Fabric API; then once more with Fabric API.
- **NeoForge:** NeoForge only.
- **Forge (1.20.1):** Forge only. This is the run that proves the SRG refmap: the dev client runs on Mojang names and never reads it.

On each:

- open Options → Resource Packs and confirm the game starts without Mixin errors in the log;
- lift a pack, re-order it inside the enabled list, move it between lists, and drop it outside both lists;
- confirm Esc cancels a drag and the screen stays open;
- select several packs (Ctrl+click, Shift+click, Ctrl+Shift+click, Ctrl+A) and drag them as one block; send them across with Enter and with an arrow;
- drag near a list edge and confirm auto-scroll;
- if the profile has a pinned pack, confirm it cannot be lifted and that the red blocked marker appears next to it;
- drag the Default pack over the available list and confirm the card and the list frame turn red, and that dropping there glides it back;
- drop an incompatible pack into the enabled list and confirm the vanilla dialog;
- repeat the basics on the data pack screen (create a world → Data Packs);
- from 1.21.10: type into the search box so that a list shows fewer packs, drag, re-order and send packs between the lists, and confirm each pack lands next to the packs you see (and that dropping at the end of a list never puts a pack below the hidden Default pack); clear the search and confirm the order is what the drops produced;
- confirm the plain arrows and clicks still behave like vanilla, and switch GUI scale mid-screen without a crash;
- confirm the mod list shows the pixel icon sharp (NeoForge: `iconBlur = false` from 26.2, `logoBlur = false` before; Forge: `logoBlur = false`), and that 26.2+ shows no "deprecated `logoFile`" loading warning and Forge 1.20.1 no "failed to load a valid ResourcePackInfo" one;
- 1.20.1: a click on a pack row selects nothing in vanilla (only the arrows react); select with Ctrl+click / Shift+click (the mod's own selection) or with the keyboard, and confirm Enter and Space send it across.

## Changelog

[`CHANGELOG.md`](../CHANGELOG.md) is user-facing and is also published as the release notes, so it must be updated together with the version.

- One section per version, newest first: `## [<mod_version>] - YYYY-MM-DD` (the version is exactly `mod_version` from `gradle.properties`, including any `-alpha` / `-beta` suffix).
- Group entries under the [Keep a Changelog](https://keepachangelog.com/) headings `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`; use only the ones a version needs. Support for a new Minecraft version or loader goes under `Added`. Short bullets, one change each. The published `0.0.1-alpha` section keeps its older headings (`Overall`, `Compatibility`, ...) and is not rewritten.
- Describe changes that matter to players or contributors; skip internal changes (build plumbing, refactors, docs upkeep, typos, CI path filters).
- Do not put other `## [` headings inside a version's section: the release workflow publishes everything between its heading and the next `## [`.
- Work not yet released may be collected under `## [Unreleased]` at the top and renamed to the version when releasing.

## Release

1. Update `mod_version` in `gradle.properties` and add its section to `CHANGELOG.md`.
2. Run `clean buildAndCollect` (commands above for your OS).
3. Validate each packaged JAR (see above).
4. Create and push the matching `v<mod_version>` tag:

   ```bash
   git tag v<mod_version> && git push origin v<mod_version>
   ```

The tag workflow (`.github/workflows/release.yml`):

- validates that the tag matches `mod_version` and that the changelog has a section for it;
- rejects pre-release suffixes other than `-alpha` / `-beta` (they become alpha / beta releases on the platforms);
- builds every target with `clean build` (including `verifyJar`) and requires exactly one release JAR per target;
- refuses to overwrite an existing GitHub release;
- publishes every target to Modrinth and CurseForge, then creates the GitHub release with the changelog section as notes and both JARs attached;
- posts the changelog to Discord through a webhook (best effort: skipped when `DISCORD_WEBHOOK_URL` is unset, and a failure does not fail the run).

Unlike Handycam, Modrinth and CurseForge are optional here, so a release can go out before those project pages exist. A platform is published to only when both its variable and its secret are set; otherwise the run prints a warning and skips it. The GitHub release is always created.

| Kind | Name | Enables |
|---|---|---|
| Variable | `MODRINTH_PROJECT_ID` | Modrinth (together with `MODRINTH_TOKEN`) |
| Secret | `MODRINTH_TOKEN` | Modrinth |
| Variable | `CURSEFORGE_PROJECT_ID` | CurseForge (together with `CURSEFORGE_TOKEN`) |
| Secret | `CURSEFORGE_TOKEN` | CurseForge |
| Secret | `DISCORD_WEBHOOK_URL` | Discord announcement |
| Variable | `DISCORD_ROLE_ID` | Optional role ping in the Discord announcement |

Never put tokens or webhook URLs in repository files. A published version cannot be re-released: fix forward with a new version and tag. To check the publishing setup without uploading anything, run it locally with `-Ppublish_dry_run=true`:

```powershell
$env:MODRINTH_TOKEN = "x"
.\gradlew.bat publishMods -Ppublish_dry_run=true -Pmodrinth_project_id=abc
```

## Troubleshooting

- **"Dependency requires at least JVM runtime version 25"** → Gradle was launched with an older JDK; launch it with JDK 25+ (`JAVA_HOME`, or `setup-java` in CI).
- **Transparent world in dev** → Gradle is using the wrong JDK; use the target's own toolchain (Java 21; 17 for 1.20.1; 25 for 26.x).
- **"Cannot find symbol" in a generated target** → check the active Stonecutter target and its conditional blocks, then re-sync Gradle.
- **Wrong client runs, or "cannot find symbol" in `src/` right after switching target** → switch with `Set active project to <target>` (see [Commands](#commands)), not by editing `.sc_active_version`.
- **`Permission denied` when moving or deleting source directories on Windows** → a Gradle daemon holds the files; run `.\gradlew.bat --stop`.
- **NeoForge says the mod id is invalid** → NeoForge ids match `[a-z][a-z0-9_]{1,63}`; hyphens are rejected. Keep `mod_id=dragit`.
- **A NeoForge dev client hangs at startup on macOS** (the log stops after the mod list; a thread dump shows the main thread waiting in `LoadingScreenRenderer` and `fml-loadingscreen` inside `SDL_GL_SwapWindow`) → the early loading screen blocks while its window is covered or the screen is not active. Set `earlyWindowControl = false` in `versions/<target>/run/config/fml.toml`; the dev client does not need the screen.
- **`verifyJar` fails** → the message names the missing entry; inspect the JAR with `unzip -l`. A mod that loads but does nothing in the packaged JAR usually means the Mixin config is not registered, or a Mixin target name no longer exists.
