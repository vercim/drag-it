# Development, CI, and Release

How to build, run, test, and release Drag It!. For per-Minecraft-version API notes and porting, see [VERSION_NOTES.md](VERSION_NOTES.md). User-facing release notes live in [../CHANGELOG.md](../CHANGELOG.md).

Drag It! uses one [Stonecutter](https://stonecutter.kikugie.dev/) repository: a single shared source tree builds every Minecraft version and loader. Do not create long-lived per-version branches. The layout deliberately mirrors [Handycam](https://github.com/vercim/handycam), so the tooling and the release flow are the same in both projects.

## Target matrix

| Target | Loader | Java |
|---|---|---:|
| `1.20.1-fabric` | Fabric | 17 |
| `1.20.1-forge` | Forge | 17 |
| `1.21.1-fabric`, `1.21.4-fabric`, `1.21.10-fabric`, `1.21.11-fabric` | Fabric | 21 |
| `1.21.1-neoforge`, `1.21.4-neoforge`, `1.21.10-neoforge`, `1.21.11-neoforge` | NeoForge | 21 |
| `26.1-fabric`, `26.1.2-fabric`, `26.2-fabric`, `26.3-fabric` | Fabric | 25 |
| `26.1-neoforge`, `26.1.2-neoforge`, `26.2-neoforge`, `26.3-neoforge` | NeoForge | 25 |

The matrix is declared once in [`settings.gradle.kts`](../settings.gradle.kts). The active target for the convenience tasks is stored in `.sc_active_version`. Dependency values shared by all targets are in the root [`gradle.properties`](../gradle.properties); values that differ per target (`fabric_loader_min_version` for Fabric, `neoforge_version` and `minecraft_version_range` for NeoForge, `forge_version`, `forge_loader_version_range`, `minecraft_version_range` and `pack_format` for Forge, `java_version=17` for the 1.20.1 targets and `java_version=25` for the 26.x ones) are in `versions/<target>/gradle.properties`, which Gradle reads for that target only. Dependency versions per target: [VERSION_NOTES.md](VERSION_NOTES.md).

### Default target

The default Stonecutter target is always the **latest Minecraft version**, Fabric build: currently `26.3-fabric`. It lives in two places that must stay equal: `vcsVersion` in [`settings.gradle.kts`](../settings.gradle.kts) and the committed `.sc_active_version`. The committed `src/` is the source as that target sees it, so it shows the 26.x names (`GuiGraphicsExtractor`, `extract…`).

- Switching to another target for local work is fine, but run `Reset active project` before committing: the diff must not contain comment flips or a changed `.sc_active_version`.
- When a new Minecraft version is added, move `vcsVersion` and `.sc_active_version` to its Fabric target in the same change: set `vcsVersion`, then run `Set active project to <target>`.

## Requirements

- JDK 25+ to launch Gradle (Fabric Loom 1.18 refuses to load on anything older). The Java 17, 21, and 25 toolchains that compile the targets are provisioned automatically (Foojay resolver); CI installs all three explicitly.
- Windows: PowerShell, use `.\gradlew.bat`.
- macOS / Linux: use `./gradlew`. If it is not executable:

  ```bash
  chmod +x gradlew
  ```

- To force a specific launcher JDK, set `JAVA_HOME` before running Gradle. macOS:

  ```bash
  export JAVA_HOME=$(/usr/libexec/java_home -v 25)
  ```

  Windows:

  ```powershell
  $env:JAVA_HOME = "C:\path\to\jdk-25"
  ```

## Commands

Each command is in its own block so it can be copied on its own.

### Windows (PowerShell)

Select the active target (any target name from the matrix instead of `1.21.4-fabric`; see [Switching the target](#switching-the-target)):

```powershell
.\gradlew.bat "Set active project to 1.21.4-fabric"
```

Back to the [default target](#default-target):

```powershell
.\gradlew.bat "Reset active project"
```

```powershell
.\gradlew.bat runActiveClient
```

```powershell
.\gradlew.bat buildActive
```

```powershell
.\gradlew.bat build
```

```powershell
.\gradlew.bat buildAndCollect
```

```powershell
.\gradlew.bat :1.21.1-neoforge:build
```

### macOS / Linux

Select the active target (any target name from the matrix instead of `1.21.4-fabric`; see [Switching the target](#switching-the-target)):

```bash
./gradlew "Set active project to 1.21.4-fabric"
```

Back to the [default target](#default-target):

```bash
./gradlew "Reset active project"
```

```bash
./gradlew runActiveClient
```

```bash
./gradlew buildActive
```

```bash
./gradlew build
```

```bash
./gradlew buildAndCollect
```

```bash
./gradlew :1.21.1-neoforge:build
```

### Tasks

| Task | What it does |
|---|---|
| `Set active project to <target>` | Makes a target active and rewrites the version and loader comment blocks in `src/` for it |
| `Reset active project` | Back to the [default target](#default-target); run it before every commit |
| `Refresh active project` | Repairs comment blocks left in a wrong state for the active target |
| `runActiveClient` | Runs the client of the active target |
| `buildActive` | Builds the active target |
| `build` | Builds every target and runs `verifyJar` on each (what CI runs) |
| `buildAndCollect` | Builds every target and collects release JARs in `build/libs/` |
| `:<target>:build` | Builds one target explicitly (useful when investigating loader integration) |
| `:<target>:verifyJar` | Checks the release JAR (also part of `build`, see [CI](#ci)) |
| `clean buildAndCollect` | Fresh full build; use for release verification only |
| `publishMods` | Publishes every target to Modrinth and CurseForge (run by the release workflow, not by hand) |

Unqualified task names are run by Gradle in every target project, so there is no target list to maintain: adding a target in `settings.gradle.kts` includes it everywhere.

Do not use `clean` routinely: it discards Gradle and Minecraft preparation work.

Outputs: collected release JARs are in `build/libs/` (for example `dragit-<mod_version>+1.21.1-fabric.jar` and `dragit-<mod_version>+1.21.1-neoforge.jar`); per-target output is in `versions/<target>/build/libs/`. Forge 1.20.1 also leaves a Mojang-named `-dev` JAR in `versions/1.20.1-forge/build/devlibs/`; the release JAR is the reobfuscated one in `build/libs/`.

### Switching the target

- Switch the active target only with the `Set active project to <target>` task (all of them are listed by `gradlew tasks --group stonecutter`). It updates `.sc_active_version` **and** rewrites the version and loader comment blocks in `src/`.
- Editing `.sc_active_version` by hand is not enough: the next build compiles `src/` as it is and fails with errors such as "cannot find symbol `getScrollAmount()`".
- `Refresh active project` repairs comments left in a wrong state.
- Before committing, run `Reset active project`; the diff must not contain comment flips or a changed `.sc_active_version`.

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

Rules:

- A loader condition is `//? fabric {`, `//? neoforge {` or `//? forge {`; the block closes with `//?}` when its code is active and with `*///?}` when it is stored commented out.
- A Minecraft-version difference is written next to the affected code: `//? if >=1.21.4 { … //?} else { /* … *///?}` (`ListGeometry.scrollAmount` is a small real example).
- Plain renames between versions are not conditions: `stonecutter.gradle.kts` swaps those tokens for 26.1 and newer (`replacements.string`), and the source tree shows the older names whenever an older target is active. A changed signature or behavior still gets a local condition.
- If a whole class is loader-specific, keep its `package` declaration outside the block so the inactive target generates a valid, otherwise empty source file.
- Keep conditions as narrow as practical. Never copy files per version.

## CI

The build workflow (`.github/workflows/build.yml`) runs on pushes to `main` and pull requests only when build-relevant files change:

- `src/**`;
- `build.*.gradle.kts`, `settings.gradle.kts`, `stonecutter.gradle.kts`, `gradle.properties`, `versions/*/gradle.properties`, `gradle/wrapper/**`, the wrapper scripts;
- `.github/workflows/build.yml`.

Documentation-only changes (including `docs/`, `README.md`, `CHANGELOG.md`, `CLAUDE.md`) do not start it. CI runs `./gradlew build` (compile + `verifyJar` for every target) and then verifies exactly one release JAR per target. It sets up JDK 17, 21, and 25 (25 launches Gradle) and uses the Gradle action for caching.

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
- `.sc_active_version` and `vcsVersion` name the [default target](#default-target) and `src/` has no comment flips;
- behavior stays compatible with the previous release.

## Scripted gesture test

A Mixin audit proves the mixins apply, not that gestures work. `tools/smoke/` holds a harness that starts the real dev client, opens the resource pack screen, plays gestures through the real input path (drag, Ctrl+click groups, Enter back and forth, Space, Ctrl+A, Esc, the search box from 1.21.10) and compares the pack order after each one. It is not part of the mod: `run-smoke.ps1` copies it into `src/` for a run and removes it again, so it never reaches a JAR.

Every target in `versions/`, one after the other:

```powershell
.\tools\smoke\run-smoke.ps1
```

Chosen targets only (comma-separated: a space makes PowerShell bind the second name to another parameter):

```powershell
.\tools\smoke\run-smoke.ps1 -Targets 1.21.4-neoforge,26.3-fabric
```

One section only (`K` = a group sent back and forth with Enter, `A` = a pack sent across with a click on its arrow, `V` = screenshots of the selection outlines in motion, saved in `run\screenshots`):

```powershell
.\tools\smoke\run-smoke.ps1 -Only K -Targets 26.3-fabric
```

Clean up after a run that was killed:

```powershell
.\tools\smoke\run-smoke.ps1 -Uninstall
```

- Close every running dev client and Gradle run first; a running one holds the Gradle lock.
- The checks assume the pack lists the harness starts from. A `resourcePacks` line left in `versions/<target>/run/options.txt` by an earlier run changes the start state and makes the first checks (S*, T*) fail on it; delete that line (or the profile) before a full run.
- Each target takes a few minutes. The script prints one verdict per target (`OK`, `FAIL`, `NO-LOG`, `INCOMPLETE`, `TIMEOUT`, `MIXIN-ERROR`, ...), writes `build/smoke/summary.txt`, and exits non-zero unless every target is `OK`. Details are in `versions/<target>/run/smoke.log` and `build/smoke/<target>.*.log`.
- A new target needs its `pack_format` in the table at the top of `run-smoke.ps1`; the script refuses to run a target it has no format for.
- Without PowerShell (macOS / Linux) the same steps work by hand: `Set active project to <target>`, copy the four harness files into `src/` and list the three test mixins in `dragit.mixins.json` after `MouseHandlerMixin`, write the target's `pack_format` into `versions/<target>/run/smoke.format` and `onboardAccessibility:false` and `soundCategory_master:0.0` (the script runs the client muted) into its `options.txt`, run `:<target>:runClient`, read `run/smoke.log` and `run/logs/latest.log`, then remove the harness and `Reset active project`. This is how 1.20.1 was run.
- When adding a Minecraft version or loader, run it for **every** new target and record the result in [VERSION_NOTES.md](VERSION_NOTES.md). The script is Windows-only (like the `.\gradlew.bat` commands here).

## Packaged-JAR validation

IDE runs are not enough for loader or Mixin changes. Before releasing (and after any change to mixins, metadata, or the build scripts), with a clean client profile and only the JAR from `build/libs/`:

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

Use a clean client profile each time, and record the outcome in the target's `Verified` line in [VERSION_NOTES.md](VERSION_NOTES.md).

## Changelog

[`CHANGELOG.md`](../CHANGELOG.md) is user-facing and is also published as the release notes, so it must be updated together with the version.

- One section per version, newest first: `## [<mod_version>] - YYYY-MM-DD` (the version is exactly `mod_version` from `gradle.properties`, including any `-alpha` / `-beta` suffix).
- Group entries under the [Keep a Changelog](https://keepachangelog.com/) headings `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`; use only the ones a version needs. Support for a new Minecraft version or loader goes under `Added`. Short bullets, one change each. The published `0.0.1-alpha` section keeps its older headings (`Overall`, `Compatibility`, ...) and is not rewritten.
- Describe changes that matter to players or contributors; skip internal changes (build plumbing, refactors, docs upkeep, typos, CI path filters).
- Do not put other `## [` headings inside a version's section: the release workflow publishes everything between its heading and the next `## [`.
- Work not yet released is collected under `## [Unreleased]` at the top and renamed to the version when releasing.

## Release

1. Set `mod_version` in `gradle.properties` and rename `## [Unreleased]` in `CHANGELOG.md` to `## [<mod_version>] - YYYY-MM-DD`:
   - stable: `1.2.3`
   - beta: `1.2.3-beta` (or `1.2.3-beta.1`)
   - alpha: `1.2.3-alpha` (or `1.2.3-alpha.1`)
2. Run a fresh full build; the eighteen JARs are collected in `build/libs/`. Windows:

   ```powershell
   .\gradlew.bat clean buildAndCollect
   ```

   macOS / Linux:

   ```bash
   ./gradlew clean buildAndCollect
   ```

3. Validate the packaged JARs (see above).
4. Run `Reset active project`, commit and push the release to `main`, then create and push the matching `v<mod_version>` tag. Pushing the tag starts the release pipeline and publishes irreversibly, so do it last:

   ```bash
   git tag v<mod_version>
   ```

   ```bash
   git push origin v<mod_version>
   ```

   The tag must be exactly `v<mod_version>`, including any `-alpha` or `-beta` suffix.
5. Wait for **Actions > Release** to succeed, then verify the release on GitHub, and on Modrinth and CurseForge where they are configured.

The tag workflow (`.github/workflows/release.yml`):

- validates that the tag matches `mod_version` and that the changelog has a section for it;
- rejects pre-release suffixes other than `-alpha` / `-beta` (they become alpha / beta releases on the platforms);
- builds every target with `clean build` (including `verifyJar`) and requires exactly one release JAR per target;
- reports which platforms are configured (an unconfigured one is skipped with a warning) and refuses to overwrite an existing GitHub release;
- publishes every target to Modrinth and CurseForge with the changelog section as the changelog, then creates the GitHub release with the same text as notes and every JAR attached (a pre-release for alpha / beta);
- posts the changelog to Discord through a webhook and pings the role from `DISCORD_ROLE_ID` (best effort: skipped when `DISCORD_WEBHOOK_URL` is unset, and a failure does not fail the run).

Platform display values omit the pre-release suffix: CurseForge file names and Modrinth version numbers use `<numeric mod version>+<Minecraft version>` (for example `1.0.0+1.21.1`), and Modrinth version titles use `Drag It! <numeric mod version> <Loader>` (for example `Drag It! 1.0.0 NeoForge`); the pre-release status is carried by the release type.

Unlike Handycam, Modrinth and CurseForge are optional here, so a release can go out before those project pages exist. A platform is published to only when both its variable and its secret are set; otherwise the run prints a warning and skips it. The GitHub release is always created.

| Kind | Name | Enables |
|---|---|---|
| Variable | `MODRINTH_PROJECT_ID` | Modrinth (together with `MODRINTH_TOKEN`) |
| Secret | `MODRINTH_TOKEN` | Modrinth |
| Variable | `CURSEFORGE_PROJECT_ID` | CurseForge (together with `CURSEFORGE_TOKEN`) |
| Secret | `CURSEFORGE_TOKEN` | CurseForge |
| Secret | `DISCORD_WEBHOOK_URL` | Discord announcement |
| Variable | `DISCORD_ROLE_ID` | Optional role ping in the Discord announcement |

Never put tokens or webhook URLs in repository files.

To check the publishing setup without uploading anything, run it locally with `-Ppublish_dry_run=true`:

```powershell
$env:MODRINTH_TOKEN = "x"
```

```powershell
.\gradlew.bat publishMods -Ppublish_dry_run=true -Pmodrinth_project_id=abc
```

### If publishing fails

- Nothing uploaded: fix the problem and rerun the failed workflow.
- Anything uploaded: increment `mod_version`, add its changelog section, and publish a new tag; do not reuse or move the old tag. A published version cannot be re-released.

## Troubleshooting

- **"Dependency requires at least JVM runtime version 25"** → Gradle was launched with an older JDK; launch it with JDK 25+ (`JAVA_HOME`, or `setup-java` in CI).
- **"No matching toolchain found"** → the Foojay resolver could not download the JDK (offline or blocked); install JDK 17, 21, and 25 locally.
- **Transparent world in dev** → Gradle is using the wrong JDK; use the target's own toolchain (Java 21; 17 for 1.20.1; 25 for 26.x).
- **"Cannot find symbol" in a generated target** → check the conditional blocks of that file; if `src/` was not rewritten for the active target, see the next entry.
- **Wrong client runs, or "cannot find symbol" in `src/` right after switching target** → switch with `Set active project to <target>` (see [Switching the target](#switching-the-target)), not by editing `.sc_active_version`.
- **`Permission denied` when moving or deleting source directories on Windows** → a Gradle daemon holds the files; run `.\gradlew.bat --stop`.
- **NeoForge says the mod id is invalid** → NeoForge ids match `[a-z][a-z0-9_]{1,63}`; hyphens are rejected. Keep `mod_id=dragit`.
- **A NeoForge dev client hangs at startup on macOS** (the log stops after the mod list; a thread dump shows the main thread waiting in `LoadingScreenRenderer` and `fml-loadingscreen` inside `SDL_GL_SwapWindow`) → the early loading screen blocks while its window is covered or the screen is not active. Set `earlyWindowControl = false` in `versions/<target>/run/config/fml.toml`; the dev client does not need the screen.
- **`verifyJar` fails** → the message names the missing entry; inspect the JAR with `unzip -l`. A mod that loads but does nothing in the packaged JAR usually means the Mixin config is not registered, or a Mixin target name no longer exists.
