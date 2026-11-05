plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.compose)
}

android {
    namespace = "pl.luczka.todaywas.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
}
