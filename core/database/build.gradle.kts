plugins {
    alias(libs.plugins.agenttoolkit.android.library)
    alias(libs.plugins.agenttoolkit.android.hilt)
    alias(libs.plugins.agenttoolkit.android.room)
}

android {
    namespace = "pl.luczka.todaywas.core.database"
    // Robolectric keeps each test's database under <tmp>/robolectric-<test name>/<test package>-dataDir/.
    // With the default test package (namespace + ".test") the long test names push that past Windows'
    // 260-character path limit, so SQLite can't open the file there.
    defaultConfig.testApplicationId = "todaywas.db.test"
}

dependencies {
    // The database, DAOs and entities are this module's API; their Room types come with them.
    api(libs.androidx.room.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
}
