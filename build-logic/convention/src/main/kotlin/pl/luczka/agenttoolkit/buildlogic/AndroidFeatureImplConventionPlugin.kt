package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A `:feature:<name>:impl` module: screens, ViewModels and the feature's entry builder. It gets the core
 * modules every feature uses; it declares its own `:api`, any other feature's `:api` it navigates to, and
 * the `:domain:<area>` modules whose use cases it calls. `:core:domain` is still added for a project that
 * hasn't split its domain into areas yet.
 */
class AndroidFeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("agenttoolkit.android.library")
            pluginManager.apply("agenttoolkit.android.compose")
            pluginManager.apply("agenttoolkit.android.hilt")

            addProjectIfPresent("implementation", ":core:designsystem")
            addProjectIfPresent("implementation", ":core:ui")
            addProjectIfPresent("implementation", ":core:domain")
            addProjectIfPresent("implementation", ":core:navigation")
            addProjectIfPresent("testImplementation", ":core:testing")

            dependencies.add("implementation", libs.library("androidx-compose-material3"))
            dependencies.add("implementation", libs.library("androidx-navigation3-runtime"))
            dependencies.add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            dependencies.add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            dependencies.add("testImplementation", libs.library("junit"))
            dependencies.add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
