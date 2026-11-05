package pl.luczka.agenttoolkit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The `:app` module: Android defaults, `targetSdk` and the quality gates. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("agenttoolkit.quality")

            extensions.configure<ApplicationExtension> {
                configureAndroidDefaults(this)
                defaultConfig.targetSdk = libs.version("android-targetSdk").toInt()
            }
        }
    }
}
