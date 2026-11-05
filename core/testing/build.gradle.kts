plugins {
    alias(libs.plugins.agenttoolkit.android.library)
}

android {
    namespace = "pl.luczka.todaywas.core.testing"
}

dependencies {
    implementation(project(":core:domain"))
}
