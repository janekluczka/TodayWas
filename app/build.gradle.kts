plugins {
    alias(libs.plugins.agenttoolkit.android.application)
    alias(libs.plugins.agenttoolkit.android.compose)
    alias(libs.plugins.agenttoolkit.android.hilt)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "pl.luczka.todaywas"

    defaultConfig {
        applicationId = "pl.luczka.todaywas"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:navigation"))
    implementation(project(":core:ui"))
    implementation(project(":feature:account:api"))
    implementation(project(":feature:habit:api"))
    implementation(project(":feature:journal:api"))
    implementation(project(":feature:main:api"))
    implementation(project(":feature:onboarding:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
