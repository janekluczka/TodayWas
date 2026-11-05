package pl.luczka.agenttoolkit.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Any Android library module: Android defaults and the quality gates. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("agenttoolkit.quality")

            extensions.configure<LibraryExtension> {
                configureAndroidDefaults(this)
            }
        }
    }
}
