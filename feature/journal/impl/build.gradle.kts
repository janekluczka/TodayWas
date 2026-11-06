plugins {
    alias(libs.plugins.agenttoolkit.android.feature.impl)
}

android {
    namespace = "pl.luczka.todaywas.feature.journal.impl"
}

dependencies {
    implementation(project(":domain:aiassist"))
    implementation(project(":domain:auth"))
    implementation(project(":domain:entry"))
    implementation(project(":feature:journal:api"))
    implementation(project(":feature:account:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
}
