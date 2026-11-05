package pl.luczka.agenttoolkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** Hilt in an Android module that declares `@Module`s, `@HiltViewModel`s or other Hilt entry points. */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies.add("implementation", libs.library("hilt-android"))
            dependencies.add("ksp", libs.library("hilt-compiler"))
        }
    }
}
