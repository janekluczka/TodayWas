plugins {
    alias(libs.plugins.agenttoolkit.android.library)
}

android {
    namespace = "pl.luczka.todaywas.core.testing"
}

dependencies {
    // The fakes implement these areas' repository interfaces, so tests using a fake see the interface type.
    api(project(":domain:aiassist"))
    api(project(":domain:auth"))
    api(project(":domain:habit"))
    api(project(":domain:journal"))
    api(project(":domain:onboarding"))
}
