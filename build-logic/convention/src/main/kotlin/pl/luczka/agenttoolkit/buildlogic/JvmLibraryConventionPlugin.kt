package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** A plain Kotlin module (no Android resources or framework), such as `:core:domain`. */
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
