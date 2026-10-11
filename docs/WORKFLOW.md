# Development, CI, and Release

How to build, run, test, and release Drag It!. For per-Minecraft-version API notes and porting, see [VERSION_NOTES.md](VERSION_NOTES.md). User-facing release notes live in [../CHANGELOG.md](../CHANGELOG.md).

Drag It! uses one [Stonecutter](https://stonecutter.kikugie.dev/) repository: a single shared source tree builds every Minecraft version and loader. Do not create long-lived per-version branches. The layout deliberately mirrors [Handycam](https://github.com/vercim/handycam), so the tooling and the release flow are the same in both projects.

## Target matrix

| Target | Loader | Java |
|---|---|---:|
| `1.21.1-fabric` | Fabric | 21 |
| `1.21.1-neoforge` | NeoForge | 21 |

The matrix is declared once in [`settings.gradle.kts`](../settings.gradle.kts). The active target for the convenience tasks is stored in `.sc_active_version`.

## Requirements

- JDK 25+ to launch Gradle (Fabric Loom 1.18 refuses to load on anything older); the Java 21 toolchain that compiles the mod is provisioned automatically. CI installs both JDKs.
- Windows: PowerShell, use `.\gradlew.bat`.
- macOS / Linux: use `./gradlew` (run `chmod +x gradlew` if it is not executable).
- To force a specific launcher JDK: macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`, Windows `$env:JAVA_HOME = "C:\path\to\jdk-25"`.

## Commands

Windows (PowerShell):

```powershell
Set-Content -NoNewline .sc_active_version "1.21.1-fabric"   # or 1.21.1-neoforge
.\gradlew.bat runActiveClient
.\gradlew.bat buildActive
.\gradlew.bat buildAndCollect
.\gradlew.bat clean buildAndCollect
.\gradlew.bat :1.21.1-neoforge:build
```

macOS / Linux:

```bash
printf '1.21.1-fabric' > .sc_active_version   # or 1.21.1-neoforge
./gradlew runActiveClient
./gradlew buildActive
./gradlew buildAndCollect
./gradlew clean buildAndCollect
./gradlew :1.21.1-neoforge:build
```

| Task | What it does |
|---|---|
| `runActiveClient` | Runs the client of the active target |
| `buildActive` | Builds the active target |
| `buildAndCollect` | Builds every target and collects release JARs in `build/libs/` |
| `:<target>:build` | Builds one target explicitly (useful when investigating loader integration) |
| `:<target>:verifyJar` | Checks the release JAR (also part of `build`, see [CI](#ci)) |
| `clean buildAndCollect` | Fresh full build; use for release verification only |

Do not use `clean` routinely: it discards Gradle and Minecraft preparation work.

Outputs: collected release JARs are in `build/libs/` (for example `dragit-0.0.1-alpha+1.21.1-fabric.jar` and `dragit-0.0.1-alpha+1.21.1-neoforge.jar`); per-target output is in `versions/<target>/build/libs/`.

After changing `.sc_active_version`, re-sync Gradle (IDE reload or any `gradlew` call) so Stonecutter rewrites the loader-specific source blocks. Commit with `1.21.1-fabric` active (the configured `vcsVersion`).

## Conditional sources

Shared code is ordinary Java. Loader-specific code uses Stonecutter conditions; the inactive loader's code appears commented out in the source tree. The only loader-specific file today is the NeoForge `@Mod` class:

```java
package dev.vercim.dragit.neoforge;

//? neoforge {

/*import net.neoforged.fml.common.Mod;

@Mod(...)
public final class DragItNeoForge {
}
*///?}
```

Keep conditions as narrow as practical. If a whole entry point is loader-specific, keep its `package` declaration outside the conditional block so the inactive target generates a valid, otherwise empty source file. Minecraft-version differences are expressed the same way, next to the affected code (`//? if >=1.21.2 { … //?} else { /* … *///?}`). Never copy files per version.

## CI

The build workflow (`.github/workflows/build.yml`) runs on pushes to `main` and pull requests only when build-relevant files change:

- `src/**`;
- Gradle, Stonecutter, wrapper, or build-script files;
- `.github/workflows/build.yml`.

Documentation-only changes (including `docs/`, `README.md`, `CHANGELOG.md`) do not start it. CI builds every target and verifies one release JAR per target.

`build` runs `verifyJar` on every target. It opens the release JAR and fails when:

- the loader descriptor (`fabric.mod.json` or `META-INF/neoforge.mods.toml`) is missing, still has `${...}` placeholders, or declares the wrong mod id;
- the descriptor does not register `dragit.mixins.json`;
- a class named in the Mixin config is missing from the JAR (or a `refmap` it names, if there is one);
- the license file is not bundled;
- the other loader's descriptor or classes leaked into the JAR, or the NeoForge `@Mod` class is missing.

This guards against the failure that broke Handycam 1.3.2: a JAR that loads while every injection silently does nothing. It does **not** check that Mixin target names exist; see [Mixin audit](VERSION_NOTES.md#mixin-audit).

A change is ready to merge when:

- every target compiles and produces its release JAR;
- `verifyJar` passes on every target;
- behavior stays compatible with the previous release.

## Packaged-JAR validation

IDE runs are not enough for loader or Mixin changes. Before releasing, with a clean client profile and only the JAR from `build/libs/`:

- **Fabric:** Fabric Loader only, without Fabric API; then once more with Fabric API.
- **NeoForge:** NeoForge only.

On each:

- open Options → Resource Packs and confirm the game starts without Mixin errors in the log;
- lift a pack, re-order it inside the enabled list, move it between lists, and drop it outside both lists;
- confirm Esc cancels a drag and the screen stays open;
- select several packs (Ctrl+click, Shift+click, Ctrl+Shift+click, Ctrl+A) and drag them as one block; send them across with Enter and with an arrow;
- drag near a list edge and confirm auto-scroll;
- if the profile has a pinned pack, confirm it cannot be lifted and that the red blocked marker with the "Fixed position" hint appears next to it;
- drop an incompatible pack into the enabled list and confirm the vanilla dialog;
- repeat the basics on the data pack screen (create a world → Data Packs);
- confirm the plain arrows and clicks still behave like vanilla, and switch GUI scale mid-screen without a crash;
- confirm the mod list shows the pixel icon sharp (NeoForge: `logoBlur = false`).

## Changelog

[`CHANGELOG.md`](../CHANGELOG.md) is user-facing and is also published as the release notes, so it must be updated together with the version.

- One section per version, newest first: `## [<mod_version>] - YYYY-MM-DD` (the version is exactly `mod_version` from `gradle.properties`, including any `-alpha` / `-beta` suffix).
- Group entries under `###` headings already in use: `Overall`, `Fixes`, `Compatibility`, `Architecture`, `Documentation`. Short bullets, one change each.
- Describe changes visible to players or integrators; skip internal-only edits (typos, CI path filters).
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
- **Transparent world in dev** → Gradle is using the wrong JDK; use a Java 21 toolchain.
- **"Cannot find symbol" in a generated target** → check the active Stonecutter target and its conditional blocks, then re-sync Gradle.
- **Wrong client runs** → update `.sc_active_version` and use `runActiveClient`.
- **`Permission denied` when moving or deleting source directories on Windows** → a Gradle daemon holds the files; run `.\gradlew.bat --stop`.
- **NeoForge says the mod id is invalid** → NeoForge ids match `[a-z][a-z0-9_]{1,63}`; hyphens are rejected. Keep `mod_id=dragit`.
- **`verifyJar` fails** → the message names the missing entry; inspect the JAR with `unzip -l`. A mod that loads but does nothing in the packaged JAR usually means the Mixin config is not registered, or a Mixin target name no longer exists.
