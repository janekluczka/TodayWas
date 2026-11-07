import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

fun optionalLocalProperty(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "pl.luczka.todaywas.core.ui"

    defaultConfig {
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${optionalLocalProperty("GOOGLE_WEB_CLIENT_ID")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain:sync"))
    implementation(project(":domain:habit"))
    implementation(project(":domain:journal"))
    implementation(project(":domain:auth"))
    implementation(project(":domain:entry"))
    // Shared composables take and render Ds components and theme types.
    api(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
}
