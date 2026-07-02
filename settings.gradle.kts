plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "RvMirror-Flixclusive"

include("RvMirrorFresh")

rootProject.children.forEach {
    it.projectDir = if (it.name == "RvMirrorFresh") {
        file("providers/RvMirror")
    } else {
        file("providers/${it.name}")
    }
}