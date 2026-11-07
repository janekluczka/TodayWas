plugins {
    alias(libs.plugins.agenttoolkit.android.feature.impl)
}

android {
    namespace = "pl.luczka.todaywas.feature.main.impl"
}

dependencies {
    implementation(project(":domain:habit"))
    implementation(project(":domain:journal"))
    implementation(project(":domain:auth"))
    implementation(project(":feature:main:api"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:habit:api"))
    implementation(project(":feature:journal:api"))
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
}
