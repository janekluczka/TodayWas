plugins {
    // An Android library rather than a plain Kotlin module, though it uses no Android API: Android Lint
    // checks API levels (NewApi) only in Android modules, and domain code is where java.time calls live.
    alias(libs.plugins.agenttoolkit.android.library)
}

android {
    namespace = "pl.luczka.todaywas.domain.sync"
}

dependencies {
    implementation(project(":domain:onboarding"))
    implementation(project(":domain:auth"))
    implementation(project(":domain:journal"))
    implementation(project(":domain:habit"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
