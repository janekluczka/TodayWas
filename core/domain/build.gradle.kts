plugins {
    // An Android library rather than a plain Kotlin module, though it uses no Android API: Android Lint
    // checks API levels (NewApi) only in Android modules, and domain code is where java.time calls live.
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "pl.luczka.todaywas.core.domain"
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
