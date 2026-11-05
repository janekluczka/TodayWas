// Convention plugins shared by every module (agent-toolkit). Included from the root settings through
// `pluginManagement { includeBuild("build-logic") }`; modules apply them by id.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
