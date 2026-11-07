plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "pl.luczka.todaywas.data.aiassist"
}

dependencies {
    implementation(project(":domain:aiassist"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
