package com.aesthetic.gym.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit schema migrations so user data is preserved across app updates.
 * The SQL matches Room's exported schema exactly (see app/schemas).
 * IMPORTANT: whenever the DB version is bumped, add a new MIGRATION_x_y here.
 */

// v1 -> v2: added the `goals` table.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `type` TEXT NOT NULL, `targetValue` REAL NOT NULL, " +
                "`exerciseId` TEXT, `manualCurrent` REAL NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`deadline` INTEGER, `done` INTEGER NOT NULL)"
        )
    }
}

// v2 -> v3: added last used weight/reps per exercise.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `lastWeightKg` REAL")
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `lastReps` INTEGER")
    }
}

// v3 -> v4: added measurement type (reps/seconds) to exercises and set logs.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `exercises` ADD COLUMN `measure` TEXT NOT NULL DEFAULT 'REPS'")
        db.execSQL("ALTER TABLE `set_logs` ADD COLUMN `measure` TEXT NOT NULL DEFAULT 'REPS'")
    }
}

val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
