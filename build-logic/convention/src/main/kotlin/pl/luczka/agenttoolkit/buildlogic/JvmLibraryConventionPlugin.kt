package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin module (no Android resources or framework). Android Lint runs here but has no minSdk, so
 * it can't flag an API-level problem (NewApi) such as `LocalDate.ofInstant` on minSdk 30: code the app
 * runs on device and that touches API-levelled JDK classes (java.time, java.util) belongs in an Android
 * library instead (house style, "Modules").
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("com.android.lint")
            pluginManager.apply("agenttoolkit.quality")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
            extensions.configure<KotlinJvmProjectExtension> {
                compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
            }

            // The gates run Android task names (`testDebugUnitTest`, `lintDebug`) from the root, which
            // a plain Kotlin module doesn't have; alias them so one invocation still covers it.
            tasks.register("testDebugUnitTest") { dependsOn("test") }
            tasks.register("lintDebug") { dependsOn("lint") }
        }
    }
}
