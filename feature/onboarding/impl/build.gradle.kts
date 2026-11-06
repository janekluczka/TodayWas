plugins {
    alias(libs.plugins.agenttoolkit.android.feature.impl)
}

android {
    namespace = "pl.luczka.todaywas.feature.onboarding.impl"
}

dependencies {
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":feature:onboarding:api"))
    implementation(project(":feature:main:api"))
    implementation(libs.androidx.compose.material.icons.core)
}
