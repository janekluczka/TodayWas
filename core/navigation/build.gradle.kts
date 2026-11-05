plugins {
    alias(libs.plugins.agenttoolkit.android.library)
}

android {
    namespace = "pl.luczka.todaywas.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
}
