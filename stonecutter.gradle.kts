@file:OptIn(dev.kikugie.stonecutter.StonecutterExperimentalAPI::class)

plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.18.4" apply false
}

stonecutter active file(".sc_active_version")

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "forge")

    // Minecraft 26.1 renamed GuiGraphics to GuiGraphicsExtractor and "render" to "extract" in the GUI hooks the mod uses.
    // These are pure renames, so the source keeps the older names and they are swapped in for 26.1 and newer.
    replacements.string(current.parsed >= "26.1") {
        replace("GuiGraphics", "GuiGraphicsExtractor")
        replace("renderContent", "extractContent")
        replace("renderSelection", "extractSelection")
        replace("\"render\"", "\"extractRenderState\"")
    }
}

tasks.register<org.gradle.api.tasks.Delete>("clean") {
    group = "build"
    description = "Cleans collected artifacts and every selected target when used as an unqualified task."
    delete(layout.buildDirectory)
}

tasks.register("runActiveClient") {
    group = "stonecutter"
    description = "Runs the client for the active Stonecutter target."
    dependsOn(stonecutter.current!!.project + ":runClient")
}

tasks.register("buildActive") {
    group = "stonecutter"
    description = "Builds the active Stonecutter target."
    dependsOn(stonecutter.current!!.project + ":build")
}
