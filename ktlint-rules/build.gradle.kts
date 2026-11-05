import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared custom ktlint rules (agent-toolkit), loaded into every Kotlin module through `ktlintRuleset`.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Provided by the ktlint runtime that loads this rule set.
    compileOnly(libs.ktlint.cli.ruleset.core)
    compileOnly(libs.ktlint.rule.engine.core)

    testImplementation(libs.ktlint.cli.ruleset.core)
    testImplementation(libs.ktlint.rule.engine.core)
    testImplementation(libs.ktlint.test)
    testImplementation(libs.junit)
    // ktlint-test logs through SLF4J but leaves the binding to the caller.
    testRuntimeOnly(libs.slf4j.simple)
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}
