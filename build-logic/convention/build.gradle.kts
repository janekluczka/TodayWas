import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "pl.luczka.agenttoolkit.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compile against the plugins' APIs only; the root build puts the plugins themselves on the
    // classpath through its `apply false` aliases, so their versions stay in one place.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "agenttoolkit.android.application"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "agenttoolkit.android.library"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "agenttoolkit.android.compose"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeatureApi") {
            id = "agenttoolkit.android.feature.api"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidFeatureApiConventionPlugin"
        }
        register("androidFeatureImpl") {
            id = "agenttoolkit.android.feature.impl"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidFeatureImplConventionPlugin"
        }
        register("androidHilt") {
            id = "agenttoolkit.android.hilt"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "agenttoolkit.android.room"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "agenttoolkit.jvm.library"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.JvmLibraryConventionPlugin"
        }
        register("quality") {
            id = "agenttoolkit.quality"
            implementationClass = "pl.luczka.agenttoolkit.buildlogic.QualityConventionPlugin"
        }
    }
}
