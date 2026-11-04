import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// House-style architecture tests (Konsist). They read every module's Kotlin sources, so they live in
// a plain JVM module with test sources only and run through `:architecture-tests:test`.
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
    testImplementation(libs.konsist)
    testImplementation(libs.junit)
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}

tasks.test {
    // Konsist parses the other modules' sources from disk, which Gradle doesn't see as inputs; declare
    // them so the task reruns when they change instead of being skipped as up to date.
    inputs
        .files(
            rootProject.fileTree(rootDir) {
                include("**/src/**/*.kt")
                exclude("**/build/**")
            },
        ).withPathSensitivity(PathSensitivity.RELATIVE)
}
