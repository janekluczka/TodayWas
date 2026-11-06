plugins {
    alias(libs.plugins.agenttoolkit.android.library)
}

android {
    namespace = "pl.luczka.todaywas.core.testing"
}

dependencies {
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":core:domain"))
}
