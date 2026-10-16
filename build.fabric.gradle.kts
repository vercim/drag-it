import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    java
    id("dev.kikugie.loom-back-compat")
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
version = "$modVersion+$minecraftVersion-fabric"
base.archivesName = modId

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

loom {
    runs.named("client") {
        client()
        ideConfigGenerated(true)
        runDir = "run"
        environment = "client"
        programArgs("--username=Dev")
        configName = "Drag It Fabric Client"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
        content { includeGroup("com.terraformersmc") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // From 26.1 Minecraft ships unobfuscated: there are no mappings to apply (the call returns null) and Loom does not remap.
    loomx.applyMojangMappings()
    // No Fabric API: the mod only needs Fabric Loader and Mixin.
    modImplementation("net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
    // Development-only: shows the mod in the dev client's mod list; never reaches the compile classpath or the JAR.
    // Mod Menu needs Fabric API modules, and Loom drops the ones nested in its JAR, so Fabric API is added next to it.
    val versionKey = minecraftVersion.replace('.', '_')
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version_$versionKey")}")
    modLocalRuntime("com.terraformersmc:modmenu:${project.property("modmenu_version_$versionKey")}")
}

tasks.processResources {
    val metadata = mapOf(
        "version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "fabric_loader_min_version" to project.property("fabric_loader_min_version"),
        "java_version" to javaVersion
    )
    inputs.properties(metadata)
    filesMatching("fabric.mod.json") { expand(metadata) }
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "pack.mcmeta")
    // Java 17 for the 1.20.1 target: the shared Mixin config names the level of the newer ones.
    if (javaVersion < 21) {
        filesMatching("$modId.mixins.json") {
            filter(Transformer<String?, String> { line -> line.orEmpty().let { if ("compatibilityLevel" in it) it.replace("JAVA_21", "JAVA_$javaVersion") else it } })
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.jar {
    from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
}

// A JAR that loads but does not register its Mixin config makes every injection silently do nothing (this broke
// Handycam 1.3.2). Fail the build instead of finding out in game.
val verifyJar = tasks.register("verifyJar") {
    group = "verification"
    description = "Checks that the release JAR registers its Mixin config and contains everything the config names."
    val releaseJar = loomx.modJar.flatMap { it.archiveFile }
    dependsOn(loomx.modJar)
    doLast {
        ZipFile(releaseJar.get().asFile).use { zip ->
            fun text(path: String): String {
                val entry = zip.getEntry(path) ?: error("$path is missing from ${zip.name}")
                return zip.getInputStream(entry).bufferedReader().readText()
            }
            fun json(path: String) = JsonSlurper().parseText(text(path)) as Map<*, *>

            check("\${" !in text("fabric.mod.json")) { "fabric.mod.json has unexpanded placeholders" }
            val mod = json("fabric.mod.json")
            check(mod["id"] == modId) { "fabric.mod.json id is ${mod["id"]}, expected $modId" }
            val configs = mod["mixins"] as? List<*> ?: error("fabric.mod.json registers no Mixin config")
            check("$modId.mixins.json" in configs) { "fabric.mod.json does not register $modId.mixins.json" }
            zip.getEntry("LICENSE_$modId") ?: error("LICENSE_$modId is missing from ${zip.name}")
            check(zip.getEntry("META-INF/neoforge.mods.toml") == null) { "neoforge.mods.toml leaked into the Fabric JAR" }
            check(zip.getEntry("META-INF/mods.toml") == null) { "mods.toml leaked into the Fabric JAR" }
            check(zip.entries().asSequence().none { it.name.startsWith("dev/vercim/dragit/neoforge/") }) { "NeoForge classes leaked into the Fabric JAR" }
            check(zip.entries().asSequence().none { it.name.startsWith("dev/vercim/dragit/forge/") }) { "Forge classes leaked into the Fabric JAR" }

            for (path in configs.map { it.toString() }) {
                val config = json(path)
                // Loom inlines the Yarn to intermediary remap into the mixin classes, so a refmap is optional.
                (config["refmap"] as? String)?.let { zip.getEntry(it) ?: error("$it named by $path is missing from ${zip.name}") }
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
    description = "Builds Fabric and copies its release JAR to the root build directory."
    val releaseJar = loomx.modJar
    dependsOn("build")
    from(releaseJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}

// Modrinth and CurseForge are optional: a platform is published to only when both its project id and token are set.
// CI exposes unset variables and secrets as empty strings, so blank counts as unset.
fun Provider<String>.ifNotBlank(): Provider<String> = filter { it.isNotBlank() }

publishMods {
    val releaseJar = loomx.modJar
    file.set(releaseJar.flatMap { it.archiveFile })
    version.set(publishVersion)
    changelog.set(providers.environmentVariable("RELEASE_CHANGELOG").orElse("See the GitHub release notes."))
    type.set(when (releaseType) {
        "release" -> STABLE
        "beta" -> BETA
        "alpha" -> ALPHA
        else -> error("Unsupported release_type '$releaseType'")
    })
    modLoaders.add("fabric")
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
            displayName.set("Drag It! $publicModVersion Fabric")
            minecraftVersions.add(minecraftVersion)
        }
    }
}
