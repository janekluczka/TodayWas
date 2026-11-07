plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
}

android {
    namespace = "pl.luczka.todaywas.data.onboarding"
}

dependencies {
    implementation(project(":domain:onboarding"))
    implementation(project(":core:database"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
