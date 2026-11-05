package pl.luczka.agenttoolkit.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/**
 * SDK levels and the Java target shared by every Android module. The levels come from the catalog
 * (`android-compileSdk`, optional `android-compileSdkMinor`, `android-minSdk`) so a project bumps them
 * in one place.
 */
internal fun Project.configureAndroidDefaults(android: CommonExtension) {
    val compileSdkLevel = libs.version("android-compileSdk").toInt()
    val compileSdkMinor = libs.findVersion("android-compileSdkMinor").map { it.requiredVersion.toInt() }.orElse(0)
    android.apply {
        compileSdk {
            version = release(compileSdkLevel) {
                minorApiLevel = compileSdkMinor
            }
        }
        defaultConfig.minSdk = libs.version("android-minSdk").toInt()
        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion
        // Robolectric tests (Room migrations, anything reading resources) need the merged resources.
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}
