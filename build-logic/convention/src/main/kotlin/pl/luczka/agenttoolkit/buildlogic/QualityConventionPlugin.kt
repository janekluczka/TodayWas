package pl.luczka.agenttoolkit.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * ktlint (with the shared `:ktlint-rules`) and detekt on the shared config. Every other convention applies
 * it, so a module built from them can't miss a gate.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")
            pluginManager.apply("dev.detekt")

            extensions.configure<KtlintExtension> {
                version.set(libs.version("ktlintEngine"))
            }
            extensions.configure<DetektExtension> {
                buildUponDefaultConfig.set(true)
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
            }
            dependencies.add("ktlintRuleset", project(":ktlint-rules"))
        }
    }
}
