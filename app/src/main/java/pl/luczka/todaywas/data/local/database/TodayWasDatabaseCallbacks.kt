package pl.luczka.todaywas.data.local.database

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

// Partial unique indexes: SQLite supports `CREATE UNIQUE INDEX ... WHERE ...` but Room's @Index
// annotation doesn't, so these are created here instead, as raw SQL. Without the WHERE clause, a
// soft-deleted (tombstoned) row would keep blocking a fresh insert for the same slot forever.
// They aren't part of the exported schema, so Room never creates or validates them. They're ensured
// on every open, which runs after a fresh create, a migration and a destructive recreation alike —
// Room calls onDestructiveMigration before recreating the tables and never calls onCreate there.
internal val partialUniqueIndexStatements = listOf(
    "CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_date ON journal_entries(date) WHERE deletedAt IS NULL",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_habit_check_ins_habitId_date " +
        "ON habit_check_ins(habitId, date) WHERE deletedAt IS NULL",
)

private const val TAG = "TodayWasDatabase"

fun todayWasDatabaseCallbacks(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        partialUniqueIndexStatements.forEach { statement ->
            try {
                db.execSQL(statement)
            } catch (e: SQLiteConstraintException) {
                // Databases recreated destructively before this callback existed never got these
                // indexes and may already hold duplicate active rows. Keep the data and open
                // without the index rather than fail every launch.
                Log.w(TAG, "Skipped a partial unique index: duplicate active rows exist", e)
            }
        }
    }
}
