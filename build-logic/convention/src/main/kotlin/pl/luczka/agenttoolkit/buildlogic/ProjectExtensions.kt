package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias)
    .orElseThrow { IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml.") }
    .requiredVersion

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias)
    .orElseThrow { IllegalStateException("Missing library '$alias' in gradle/libs.versions.toml.") }

/** The Java/JVM target every module compiles to: `javaVersion` in the catalog, 17 when it's absent. */
internal val Project.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(libs.findVersion("javaVersion").map { it.requiredVersion }.orElse("17"))

/**
 * Adds [path] as a dependency only when the project has that module, so a convention can name the
 * house-style core modules without breaking a project that hasn't split them out yet.
 */
internal fun Project.addProjectIfPresent(
    configuration: String,
    path: String,
) {
    if (path != this.path && rootProject.findProject(path) != null) {
        dependencies.add(configuration, project(path))
    }
}
