pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Today Was"
include(":app")
include(":core:designsystem")
include(":core:data")
include(":core:database")
include(":core:navigation")
include(":core:network")
include(":core:testing")
include(":core:ui")
include(":data:aiassist")
include(":data:auth")
include(":data:habit")
include(":data:journal")
include(":data:onboarding")
include(":domain:aiassist")
include(":domain:auth")
include(":domain:entry")
include(":domain:habit")
include(":domain:journal")
include(":domain:onboarding")
include(":domain:sync")
include(":feature:account:api")
include(":feature:account:impl")
include(":feature:habit:api")
include(":feature:habit:impl")
include(":feature:journal:api")
include(":feature:journal:impl")
include(":feature:main:api")
include(":feature:main:impl")
include(":feature:onboarding:api")
include(":feature:onboarding:impl")
include(":ktlint-rules")
include(":architecture-tests")
