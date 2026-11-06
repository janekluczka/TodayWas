plugins {
    alias(libs.plugins.agenttoolkit.android.feature.impl)
}

android {
    namespace = "pl.luczka.todaywas.feature.account.impl"
}

dependencies {
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":feature:account:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
}
