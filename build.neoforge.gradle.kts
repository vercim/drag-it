import java.util.zip.ZipFile
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    java
    id("net.neoforged.moddev")
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

group = project.property("mod_group") as String
version = "$modVersion+$minecraftVersion-neoforge"
base.archivesName = modId

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

neoForge {
    version = project.property("neoforge_version") as String

    runs {
        register("client") {
            client()
            gameDirectory = file("run")
            programArgument("--username=Dev")
            ideName = "Drag It NeoForge Client"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

repositories {
    mavenCentral()
}

tasks.processResources {
    val metadata = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "neoforge_version" to project.property("neoforge_version"),
        "neoforge_loader_version_range" to project.property("neoforge_loader_version_range")
    )
    inputs.properties(metadata)
    filesMatching("META-INF/neoforge.mods.toml") { expand(metadata) }
    exclude("fabric.mod.json")
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
}

// A JAR that loads but does not register its Mixin config makes every injection silently do nothing (this broke
// Handycam 1.3.2). Fail the build instead of finding out in game.
val verifyJar = tasks.register("verifyJar") {
    group = "verification"
    description = "Checks that the release JAR registers its Mixin config and contains everything the config names."
    val releaseJar = tasks.named<AbstractArchiveTask>("jar").flatMap { it.archiveFile }
    dependsOn("jar")
    doLast {
        ZipFile(releaseJar.get().asFile).use { zip ->
            fun text(path: String): String {
                val entry = zip.getEntry(path) ?: error("$path is missing from ${zip.name}")
                return zip.getInputStream(entry).bufferedReader().readText()
            }

            val toml = text("META-INF/neoforge.mods.toml")
            check("\${" !in toml) { "neoforge.mods.toml has unexpanded placeholders" }
            check(Regex("""modId\s*=\s*"$modId"""").containsMatchIn(toml)) { "neoforge.mods.toml does not declare mod $modId" }
            val configs = Regex("""\[\[mixins]]\s*\n\s*config\s*=\s*"([^"]+)"""").findAll(toml).map { it.groupValues[1] }.toList()
            check("$modId.mixins.json" in configs) { "neoforge.mods.toml does not register $modId.mixins.json" }
            check(zip.getEntry("fabric.mod.json") == null) { "fabric.mod.json leaked into the NeoForge JAR" }
            check(zip.getEntry("dev/vercim/dragit/neoforge/DragItNeoForge.class") != null) { "the @Mod class is missing; javafml refuses to load the mod" }
            zip.getEntry("LICENSE_$modId") ?: error("LICENSE_$modId is missing from ${zip.name}")

            for (path in configs) {
                val config = groovy.json.JsonSlurper().parseText(text(path)) as Map<*, *>
                val root = (config["package"] as String).replace('.', '/')
                for (section in listOf("mixins", "client", "server")) {
                    for (mixin in config[section] as? List<*> ?: emptyList<Any?>()) {
                        val entry = "$root/${mixin.toString().replace('.', '/')}.class"
                        zip.getEntry(entry) ?: error("$entry named by $path is missing from ${zip.name}")
                    }
                }
            }
        }
    }
}
tasks.check { dependsOn(verifyJar) }

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    description = "Builds NeoForge and copies its release JAR to the root build directory."
    val releaseJar = tasks.named<AbstractArchiveTask>("jar")
    dependsOn("build")
    from(releaseJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

// Modrinth and CurseForge are optional: a platform is published to only when both its project id and token are set.
// CI exposes unset variables and secrets as empty strings, so blank counts as unset.
fun Provider<String>.ifNotBlank(): Provider<String> = filter { it.isNotBlank() }

publishMods {
    val releaseJar = tasks.named<AbstractArchiveTask>("jar")
    file.set(releaseJar.flatMap { it.archiveFile })
    version.set(publishVersion)
    changelog.set(providers.environmentVariable("RELEASE_CHANGELOG").orElse("See the GitHub release notes."))
    type.set(when (releaseType) {
        "release" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> error("Unsupported release_type '$releaseType'")
    })
    modLoaders.add("neoforge")
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
            displayName.set("Drag It! $publicModVersion NeoForge")
            minecraftVersions.add(minecraftVersion)
        }
    }
}
