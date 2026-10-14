import java.util.zip.ZipFile
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    java
    id("net.neoforged.moddev.legacyforge")
    id("me.modmuss50.mod-publish-plugin")
}

val minecraftVersion = stonecutter.current.version
val modId = project.property("mod_id") as String
val modVersion = project.property("mod_version") as String
val publicModVersion = modVersion.substringBefore('-')
val publishVersion = "$publicModVersion+$minecraftVersion"
val javaVersion = (project.property("java_version") as String).toInt()
val releaseType = providers.gradleProperty("release_type")
    .orElse(if ("-alpha" in modVersion) "alpha" else if ("-beta" in modVersion) "beta" else "release")
    .get()
val refmapName = "$modId.refmap.json"

group = project.property("mod_group") as String
version = "$modVersion+$minecraftVersion-forge"
base.archivesName = modId

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

legacyForge {
    version = project.property("forge_version") as String

    runs {
        register("client") {
            client()
            gameDirectory = file("run")
            programArgument("--username=Dev")
            ideName = "Drag It Forge Client"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

// Forge 1.20.1 runs on SRG names, so Mixin needs a refmap that turns the Mojang names of the target strings into SRG.
mixin {
    add(sourceSets["main"], refmapName)
    config("$modId.mixins.json")
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-public/") { name = "Sponge" }
}

dependencies {
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

tasks.processResources {
    val metadata = mapOf(
        "version" to project.version,
        "minecraft_version_range" to project.property("minecraft_version_range"),
        "forge_version" to project.property("forge_version").toString().substringAfter('-'),
        "forge_loader_version_range" to project.property("forge_loader_version_range"),
        "pack_format" to project.property("pack_format")
    )
    inputs.properties(metadata)
    filesMatching("META-INF/mods.toml") { expand(metadata) }
    // Forge 1.20.1 warns at startup ("failed to load a valid ResourcePackInfo") about a mod without pack.mcmeta.
    filesMatching("pack.mcmeta") { expand(metadata) }
    // The shared Mixin config targets the newest Java level of its Minecraft version and names no refmap; the refmap exists on Forge only.
    filesMatching("$modId.mixins.json") {
        filter(Transformer<String?, String> { raw ->
            val line = raw.orEmpty()
            when {
                "compatibilityLevel" in line -> line.replace("JAVA_21", "JAVA_$javaVersion")
                line.trimStart().startsWith("\"package\"") -> "$line\n  \"refmap\": \"$refmapName\","
                else -> line
            }
        })
    }
    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.named("createMinecraftArtifacts") {
    dependsOn(tasks.named("stonecutterGenerate"))
}

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    // Forge 1.20.1 has no [[mixins]] table in mods.toml: the Mixin config is found through this manifest attribute.
    manifest.attributes("MixinConfigs" to "$modId.mixins.json")
}

// MDG Legacy moves the plain `jar` (Mojang names) to build/devlibs and writes the SRG-named release JAR with `reobfJar`.
val releaseJar = tasks.named<AbstractArchiveTask>("reobfJar")

// A JAR that loads but does not register its Mixin config makes every injection silently do nothing (this broke
// Handycam 1.3.2). Fail the build instead of finding out in game.
val verifyJar = tasks.register("verifyJar") {
    group = "verification"
    description = "Checks that the release JAR registers its Mixin config and contains everything the config names."
    val archive = releaseJar.flatMap { it.archiveFile }
    dependsOn(releaseJar)
    doLast {
        ZipFile(archive.get().asFile).use { zip ->
            fun text(path: String): String {
                val entry = zip.getEntry(path) ?: error("$path is missing from ${zip.name}")
                return zip.getInputStream(entry).bufferedReader().readText()
            }

            val toml = text("META-INF/mods.toml")
            check("\${" !in toml) { "mods.toml has unexpanded placeholders" }
            check(Regex("""modId\s*=\s*"$modId"""").containsMatchIn(toml)) { "mods.toml does not declare mod $modId" }
            check(Regex("""(?m)^\s*MixinConfigs:\s*$modId\.mixins\.json\s*$""").containsMatchIn(text("META-INF/MANIFEST.MF"))) {
                "the manifest does not register $modId.mixins.json (MixinConfigs)"
            }
            check("\${" !in text("pack.mcmeta")) { "pack.mcmeta has unexpanded placeholders" }
            check(zip.getEntry("fabric.mod.json") == null) { "fabric.mod.json leaked into the Forge JAR" }
            check(zip.getEntry("META-INF/neoforge.mods.toml") == null) { "neoforge.mods.toml leaked into the Forge JAR" }
            check(zip.entries().asSequence().none { it.name.startsWith("dev/vercim/dragit/neoforge/") }) { "NeoForge classes leaked into the Forge JAR" }
            check(zip.getEntry("dev/vercim/dragit/forge/DragItForge.class") != null) { "the @Mod class is missing; javafml refuses to load the mod" }
            zip.getEntry("LICENSE_$modId") ?: error("LICENSE_$modId is missing from ${zip.name}")

            val config = groovy.json.JsonSlurper().parseText(text("$modId.mixins.json")) as Map<*, *>
            // Production runs on SRG names: without the refmap every target string stays a Mojang name and no injection applies.
            check(config["refmap"] == refmapName) { "$modId.mixins.json does not name the refmap $refmapName" }
            zip.getEntry(refmapName) ?: error("$refmapName is missing from ${zip.name}")
            val root = (config["package"] as String).replace('.', '/')
            for (section in listOf("mixins", "client", "server")) {
                for (mixin in config[section] as? List<*> ?: emptyList<Any?>()) {
                    val entry = "$root/${mixin.toString().replace('.', '/')}.class"
                    zip.getEntry(entry) ?: error("$entry named by $modId.mixins.json is missing from ${zip.name}")
                }
            }
        }
    }
}
tasks.check { dependsOn(verifyJar) }

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds Forge and copies its release JAR to the root build directory."
    dependsOn("build")
    from(releaseJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

// Modrinth and CurseForge are optional: a platform is published to only when both its project id and token are set.
// CI exposes unset variables and secrets as empty strings, so blank counts as unset.
fun Provider<String>.ifNotBlank(): Provider<String> = filter { it.isNotBlank() }

publishMods {
    file.set(releaseJar.flatMap { it.archiveFile })
    version.set(publishVersion)
    changelog.set(providers.environmentVariable("RELEASE_CHANGELOG").orElse("See the GitHub release notes."))
    type.set(when (releaseType) {
        "release" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> error("Unsupported release_type '$releaseType'")
    })
    modLoaders.add("forge")
    dryRun = providers.gradleProperty("publish_dry_run").map { it.toBoolean() }.orElse(false)

    val curseforgeProject = providers.gradleProperty("curseforge_project_id").ifNotBlank()
    val curseforgeToken = providers.environmentVariable("CURSEFORGE_TOKEN").ifNotBlank()
    if (curseforgeProject.isPresent && curseforgeToken.isPresent) {
        curseforge {
            projectId.set(curseforgeProject)
            accessToken.set(curseforgeToken)
            displayName.set(publishVersion)
            minecraftVersions.add(minecraftVersion)
            client.set(true)
            server.set(false)
        }
    }

    val modrinthProject = providers.gradleProperty("modrinth_project_id").ifNotBlank()
    val modrinthToken = providers.environmentVariable("MODRINTH_TOKEN").ifNotBlank()
    if (modrinthProject.isPresent && modrinthToken.isPresent) {
        modrinth {
            projectId.set(modrinthProject)
            accessToken.set(modrinthToken)
            displayName.set("Drag It! $publicModVersion Forge")
            minecraftVersions.add(minecraftVersion)
        }
    }
}
