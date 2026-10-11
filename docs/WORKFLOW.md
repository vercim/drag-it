# Development, CI, and Release

How to build, run, test, and release Drag It!. For per-Minecraft-version API notes and porting, see [VERSION_NOTES.md](VERSION_NOTES.md). User-facing release notes live in [../CHANGELOG.md](../CHANGELOG.md).

Drag It! uses one [Stonecutter](https://stonecutter.kikugie.dev/) repository: a single shared source tree builds every Minecraft version and loader. Do not create long-lived per-version branches. The layout deliberately mirrors [Handycam](https://github.com/vercim/handycam), so the tooling and the release flow are the same in both projects.

## Target matrix

| Target | Loader | Java |
|---|---|---:|
| `1.21.1-fabric` | Fabric | 21 |

The matrix is declared once in [`settings.gradle.kts`](../settings.gradle.kts). The active target for the convenience tasks is stored in `.sc_active_version`. The `-fabric` suffix is kept even though Fabric is the only loader, so a second loader or Minecraft version slots in the same way it does in Handycam.

## Requirements

- JDK 25+ to launch Gradle (Fabric Loom 1.18 refuses to load on anything older); the Java 21 toolchain that compiles the mod is provisioned automatically. CI installs both JDKs.
- Windows: PowerShell, use `.\gradlew.bat`.
- macOS / Linux: use `./gradlew` (run `chmod +x gradlew` if it is not executable).
- To force a specific launcher JDK: macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`, Windows `$env:JAVA_HOME = "C:\path\to\jdk-25"`.

## Commands

Windows (PowerShell):

```powershell
Set-Content -NoNewline .sc_active_version "1.21.1-fabric"
.\gradlew.bat runActiveClient
.\gradlew.bat buildActive
.\gradlew.bat buildAndCollect
.\gradlew.bat clean buildAndCollect
.\gradlew.bat :1.21.1-fabric:build
```

macOS / Linux:

```bash
printf '1.21.1-fabric' > .sc_active_version
./gradlew runActiveClient
./gradlew buildActive
./gradlew buildAndCollect
./gradlew clean buildAndCollect
./gradlew :1.21.1-fabric:build
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

Outputs: collected release JARs are in `build/libs/` (for example `drag-it-0.0.1-alpha+1.21.1-fabric.jar`); per-target output is in `versions/<target>/build/libs/`.

After changing `.sc_active_version`, re-sync Gradle (IDE reload or any `gradlew` call) so Stonecutter regenerates the target. Commit with `1.21.1-fabric` active (the configured `vcsVersion`).

## Conditional sources

Shared code is ordinary Java. Today there is no loader- or version-specific code, so the tree has no Stonecutter conditions. When a second target needs them, keep them as narrow as practical and next to the affected code:

```java
//? if >=1.21.2 {
newCall();
//?} else {
/*oldCall();
*///?}
```

Never copy files per version. The inactive branch of a condition appears commented out in the source tree.

## CI

The build workflow (`.github/workflows/build.yml`) runs on pushes to `main` and pull requests only when build-relevant files change:

- `src/**`;
- Gradle, Stonecutter, wrapper, or build-script files;
- `.github/workflows/build.yml`.

Documentation-only changes (including `docs/`, `README.md`, `CHANGELOG.md`) do not start it. CI builds every target and verifies one release JAR per target.

`build` runs `verifyJar` on every target. It opens the release JAR and fails when:

- `fabric.mod.json` is missing, still has `${...}` placeholders, or has the wrong id;
- `fabric.mod.json` does not register `drag-it.mixins.json`;
- a class named in the Mixin config is missing from the JAR (or a `refmap` it names, if there is one);
- the license file is not bundled.

This guards against the failure that broke Handycam 1.3.2: a JAR that loads while every injection silently does nothing.

A change is ready to merge when:

- every target compiles and produces its release JAR;
- `verifyJar` passes;
- behavior stays compatible with the previous release.

## Packaged-JAR validation

IDE runs are not enough for loader or Mixin changes. Before releasing, with a clean client profile and only the JAR from `build/libs/` plus Fabric Loader (no Fabric API, then once more with it):

- open Options → Resource Packs and confirm the game starts without Mixin errors in the log;
- lift a pack, re-order it inside the enabled list, move it between lists, and drop it outside both lists;
- confirm Esc cancels a drag and the screen stays open;
- select several packs (Ctrl+click, Shift+click, Ctrl+Shift+click, Ctrl+A) and drag them as one block; send them across with Enter and with an arrow;
- drag near a list edge and confirm auto-scroll;
- if the profile has a pinned pack, confirm it cannot be lifted and that the red blocked marker with the "Fixed position" hint appears next to it;
- drop an incompatible pack into the enabled list and confirm the vanilla dialog;
- repeat the basics on the data pack screen (create a world → Data Packs);
- confirm the plain arrows and clicks still behave like vanilla, and switch GUI scale mid-screen without a crash.

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
- publishes every target to Modrinth and CurseForge, then creates the GitHub release with the changelog section as notes;
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
- **`Permission denied` when moving or deleting source directories on Windows** → a Gradle daemon holds the files; run `.\gradlew.bat --stop`.
- **`verifyJar` fails** → the message names the missing entry; inspect the JAR with `unzip -l`. A mod that loads but does nothing in the packaged JAR usually means the Mixin config is not registered.
