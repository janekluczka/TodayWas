package pl.luczka.agenttoolkit.buildlogic

import androidx.room.gradle.RoomExtension
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The module that owns the Room database (house style, "Persistence"). */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("androidx.room")

            val schemas = "$projectDir/schemas"
            extensions.configure<RoomExtension> {
                // Exported schemas are the migration history: commit every new <version>.json.
                schemaDirectory(schemas)
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { shipSchemasInDebugAssets(schemas) }
            }
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { shipSchemasInDebugAssets(schemas) }
            }

            dependencies.add("implementation", libs.library("androidx-room-runtime"))
            dependencies.add("implementation", libs.library("androidx-room-ktx"))
            dependencies.add("ksp", libs.library("androidx-room-compiler"))
            dependencies.add("testImplementation", libs.library("androidx-room-testing"))
        }
    }
}

/**
 * MigrationTestHelper reads the exported schemas from assets. The migration tests run as Robolectric unit
 * tests (so CI covers them), which only see the tested variant's merged assets — not the test source
 * set's — so the schemas ship in debug builds only.
 */
private fun CommonExtension.shipSchemasInDebugAssets(schemas: String) {
    sourceSets.getByName("debug").assets.directories.add(schemas)
}
