plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
}

android {
    namespace = "pl.luczka.todaywas.data.sync"
}

dependencies {
    implementation(project(":domain:sync"))
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":domain:entry"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
