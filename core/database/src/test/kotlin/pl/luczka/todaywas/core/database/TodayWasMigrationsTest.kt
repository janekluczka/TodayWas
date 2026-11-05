package pl.luczka.todaywas.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodayWasMigrationsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // JUnit creates a fresh instance per test, so every test gets its own database file.
    private val dbFile = context.getDatabasePath("todaywas-${System.nanoTime()}.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = AndroidSQLiteDriver(),
        databaseClass = TodayWasDatabase::class,
    )

    @Test
    fun `should keep data and partial indexes when migrating from the oldest exported schema to the current one`() {
        // Arrange
        helper.createDatabase(OLDEST_EXPORTED_VERSION).apply {
            // A real v6 database has the partial indexes from the open callback.
            partialUniqueIndexStatements.forEach(::execSQL)
            execSQL(
                "INSERT INTO journal_entries (id, date, text, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('entry-1', '2026-08-18', 'Today was good.', 1000, 1000, NULL)",
            )
            close()
        }

        // Act
        // Runs every registered migration and fails unless the result matches the current schema.
        helper.runMigrationsAndValidate(TODAY_WAS_DATABASE_VERSION, todayWasMigrations.toList()).close()
        val database = openWithSchemaHistory()
        val opened = database.openHelper.writableDatabase

        // Assert
        assertEquals(listOf("Today was good."), opened.journalTexts())
        assertEquals(PARTIAL_UNIQUE_INDEXES, opened.partialUniqueIndexes())
        database.close()
    }

    @Test
    fun `should open and keep every row when duplicate active entries block a partial index`() {
        // Arrange
        // A v6 database recreated destructively in the past: no partial indexes, so duplicates got in.
        helper.createDatabase(OLDEST_EXPORTED_VERSION).apply {
            execSQL(
                "INSERT INTO journal_entries (id, date, text, createdAt, updatedAt, deletedAt) VALUES " +
                    "('entry-1', '2026-08-18', 'First.', 1000, 1000, NULL), " +
                    "('entry-2', '2026-08-18', 'Second.', 2000, 2000, NULL)",
            )
            close()
        }

        // Act
        val database = openWithSchemaHistory()
        val opened = database.openHelper.writableDatabase

        // Assert
        assertEquals(listOf("First.", "Second."), opened.journalTexts().sorted())
        assertEquals(setOf("index_habit_check_ins_habitId_date"), opened.partialUniqueIndexes())
        database.close()
    }

    @Test
    fun `should recreate the database with its partial indexes when it predates schema export`() {
        // Arrange
        createRawDatabase(version = preSchemaExportVersions.last())

        // Act
        val database = openWithSchemaHistory()
        val opened = database.openHelper.writableDatabase

        // Assert
        assertEquals(TODAY_WAS_DATABASE_VERSION, opened.version)
        assertEquals(PARTIAL_UNIQUE_INDEXES, opened.partialUniqueIndexes())
        assertEquals(0, opened.tableCount("legacy"))
        database.close()
    }

    @Test
    fun `should fail without deleting data when the database is newer than the app`() {
        // Arrange
        createRawDatabase(version = TODAY_WAS_DATABASE_VERSION + 1)

        // Act
        val database = openWithSchemaHistory()
        val failure = runCatching { database.openHelper.writableDatabase }.exceptionOrNull()
        database.close()

        // Assert
        assertTrue(failure is IllegalStateException)
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(TODAY_WAS_DATABASE_VERSION + 1, it.version)
            it.rawQuery("SELECT COUNT(*) FROM legacy", null).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    // The production builder setup (DatabaseModule), on this test's file.
    private fun openWithSchemaHistory(): TodayWasDatabase =
        Room
            .databaseBuilder(context, TodayWasDatabase::class.java, dbFile.name)
            .allowMainThreadQueries()
            .withTodayWasSchemaHistory()
            .build()

    // A database file at [version] holding one row in a table no TodayWas schema has.
    private fun createRawDatabase(version: Int) {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use {
            it.execSQL("CREATE TABLE legacy (id INTEGER PRIMARY KEY)")
            it.execSQL("INSERT INTO legacy (id) VALUES (1)")
            it.version = version
        }
    }

    private fun SupportSQLiteDatabase.journalTexts(): List<String> =
        query("SELECT text FROM journal_entries").use {
            buildList { while (it.moveToNext()) add(it.getString(0)) }
        }

    private fun SupportSQLiteDatabase.partialUniqueIndexes(): Set<String> =
        query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql LIKE '%WHERE deletedAt IS NULL%'",
        ).use {
            buildSet { while (it.moveToNext()) add(it.getString(0)) }
        }

    private fun SupportSQLiteDatabase.tableCount(name: String): Int =
        query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(name),
        ).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val OLDEST_EXPORTED_VERSION = 6
        val PARTIAL_UNIQUE_INDEXES = setOf("index_journal_entries_date", "index_habit_check_ins_habitId_date")
    }
}
