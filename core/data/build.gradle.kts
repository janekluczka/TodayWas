plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
}

android {
    namespace = "pl.luczka.todaywas.core.data"
}

dependencies {
    implementation(project(":domain:habit"))
    implementation(project(":domain:journal"))
    implementation(project(":domain:aiassist"))
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":domain:entry"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.room.testing)
}
