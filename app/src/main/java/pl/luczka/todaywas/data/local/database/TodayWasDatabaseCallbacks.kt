package pl.luczka.todaywas.data.local.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

// Partial unique indexes: SQLite supports `CREATE UNIQUE INDEX ... WHERE ...` but Room's @Index
// annotation doesn't, so these are created here instead - as raw SQL run once per DB (re)creation,
// compatible with the destructive-fallback flow already in use. Without the WHERE clause, a
// soft-deleted (tombstoned) row would keep blocking a fresh insert for the same slot forever.
fun todayWasDatabaseCallbacks(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.execSQL(
            "CREATE UNIQUE INDEX index_journal_entries_date ON journal_entries(date) WHERE deletedAt IS NULL",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX index_habit_check_ins_habitId_date ON habit_check_ins(habitId, date) WHERE deletedAt IS NULL",
        )
    }
}
