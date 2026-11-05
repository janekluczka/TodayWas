package pl.luczka.agenttoolkit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Compose in an app or library module: the compiler plugin, the BOM, previews and compose-rules. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { buildFeatures.compose = true }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            }

            val bom = dependencies.platform(libs.library("androidx-compose-bom"))
            dependencies.add("implementation", bom)
            dependencies.add("androidTestImplementation", bom)
            dependencies.add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            dependencies.add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            dependencies.add("detektPlugins", libs.library("compose-rules-detekt"))
        }
    }
}
