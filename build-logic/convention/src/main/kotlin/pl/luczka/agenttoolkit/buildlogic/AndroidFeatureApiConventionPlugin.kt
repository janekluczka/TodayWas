package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A `:feature:<name>:api` module: the feature's `@Serializable` navigation keys and its
 * `Navigator.navigateToXxx()` functions, nothing else (house style, "Modules").
 */
class AndroidFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenttoolkit.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

            addProjectIfPresent("api", ":core:navigation")
            dependencies.add("api", libs.library("androidx-navigation3-runtime"))
            dependencies.add("implementation", libs.library("kotlinx-serialization-core"))
        }
    }
}
