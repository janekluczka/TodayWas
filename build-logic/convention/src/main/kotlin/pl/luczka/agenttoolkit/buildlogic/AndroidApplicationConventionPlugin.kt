package pl.luczka.agenttoolkit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The `:app` module: Android defaults, `targetSdk`, the quality gates, and lint over every dependency. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("agenttoolkit.quality")

            extensions.configure<ApplicationExtension> {
                configureAndroidDefaults(this)
                defaultConfig.targetSdk = libs.version("android-targetSdk").toInt()
                // Plain Kotlin modules have no minSdk of their own; checking them from the app is what
                // catches an API-level problem (NewApi) in code they share with it.
                lint.checkDependencies = true
            }
        }
    }
}
