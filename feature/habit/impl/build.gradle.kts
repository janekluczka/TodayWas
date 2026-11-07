plugins {
    alias(libs.plugins.agenttoolkit.android.feature.impl)
}

android {
    namespace = "pl.luczka.todaywas.feature.habit.impl"
}

dependencies {
    implementation(project(":domain:habit"))
    implementation(project(":domain:entry"))
    implementation(project(":feature:habit:api"))
    implementation(libs.androidx.compose.material.icons.core)
}
