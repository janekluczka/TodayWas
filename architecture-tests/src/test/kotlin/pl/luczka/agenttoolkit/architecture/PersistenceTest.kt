package pl.luczka.agenttoolkit.architecture

import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import com.lemonappdev.konsist.api.verify.assertEmpty
import org.junit.Test

/** House style, "Persistence": a schema change never wipes user data. */
class PersistenceTest {

    private val databases by lazy {
        Scopes.production
            .classes()
            .withAnnotationNamed("Database", "androidx.room.Database", "androidx.room3.Database")
    }

    @Test
    fun `should not fall back to destructive migration when a database is built`() {
        // Arrange
        val blanketFallback = Regex("""\.fallbackToDestructiveMigration(OnDowngrade)?\s*\(""")
        val destructive = Scopes.production.files.filter { blanketFallback.containsMatchIn(it.text) }

        // Assert
        destructive.assertEmpty(
            additionalMessage = "Add a migration; only fallbackToDestructiveMigrationFrom(...) is allowed.",
        )
    }

    @Test
    fun `should export its schema when a class is a Room database`() {
        // Arrange
        val exportDisabled = Regex("""exportSchema\s*=\s*false""")
        val notExported = databases.filter { database ->
            database.annotations.any { exportDisabled.containsMatchIn(it.text) }
        }

        // Assert
        notExported.assertEmpty()
    }

    @Test
    fun `should have a migration test when the project has a Room database`() {
        // Arrange
        val hasMigrationTest = Scopes.test.files.any { file ->
            file.imports.any { it.name.endsWith(".testing.MigrationTestHelper") }
        }
        val untested = if (hasMigrationTest) emptyList() else databases

        // Assert
        untested.assertEmpty(additionalMessage = "Test the migrations with MigrationTestHelper.")
    }
}
