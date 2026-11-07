plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
}

android {
    namespace = "pl.luczka.todaywas.data.auth"
}

dependencies {
    implementation(project(":domain:auth"))
    implementation(project(":core:network"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
