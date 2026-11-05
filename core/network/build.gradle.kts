import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

fun requiredLocalProperty(key: String): String = localProperties.getProperty(key)
    ?: error("Missing '$key' in local.properties — see local.properties for the expected keys.")

android {
    namespace = "pl.luczka.todaywas.core.network"

    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${requiredLocalProperty("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${requiredLocalProperty("SUPABASE_ANON_KEY")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // The data layer talks to SupabaseClient (auth, functions) directly, so the client is this module's API.
    api(platform(libs.supabase.bom))
    api(libs.supabase.auth.kt)
    api(libs.supabase.postgrest.kt)
    api(libs.supabase.functions.kt)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.core)
}
