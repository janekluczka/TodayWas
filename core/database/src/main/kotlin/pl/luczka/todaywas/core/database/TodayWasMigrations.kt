package pl.luczka.todaywas.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration

// Every version bump adds its Migration here (or an AutoMigration on TodayWasDatabase). There is no
// blanket destructive fallback: a missing migration fails at startup instead of deleting local data
// (PRD guardrail), and TodayWasMigrationsTest fails first. Rebuilding journal_entries or
// habit_check_ins drops their partial unique indexes; the open callback recreates them, but the
// migration must leave no duplicate active rows behind or that recreation fails.
internal val todayWasMigrations: Array<Migration> = emptyArray()

// v1–v5 shipped before schemas were exported, so no migration path exists from them; the few dev-era
// installs still on one are recreated. A downgrade still fails rather than wiping data.
internal val preSchemaExportVersions: IntArray = intArrayOf(1, 2, 3, 4, 5)

fun RoomDatabase.Builder<TodayWasDatabase>.withTodayWasSchemaHistory(): RoomDatabase.Builder<TodayWasDatabase> = this
    .addMigrations(*todayWasMigrations)
    .fallbackToDestructiveMigrationFrom(true, *preSchemaExportVersions)
    .addCallback(todayWasDatabaseCallbacks())
