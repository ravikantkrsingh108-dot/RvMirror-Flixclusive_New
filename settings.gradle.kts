plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "RvMirror-Flixclusive"

include("RvMirror")

rootProject.children.forEach {
    it.projectDir = file("providers/${it.name}")
}