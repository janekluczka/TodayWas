plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "pl.luczka.todaywas.data.journal"
}

dependencies {
    implementation(project(":domain:auth"))
    implementation(project(":domain:journal"))
    implementation(project(":domain:entry"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.serialization.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
