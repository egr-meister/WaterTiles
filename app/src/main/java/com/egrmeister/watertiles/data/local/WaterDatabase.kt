package com.egrmeister.watertiles.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction

@Database(
    entities = [WaterEntryEntity::class, GoalChangeEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WaterDatabase : RoomDatabase() {
    abstract fun entryDao(): WaterEntryDao
    abstract fun goalDao(): GoalChangeDao

    companion object {
        const val NAME = "watertiles.db"

        /**
         * Explicit migrations for every schema bump. Destructive migration is never enabled, so a
         * missing migration fails loudly instead of wiping user data.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): WaterDatabase =
            Room.databaseBuilder(context.applicationContext, WaterDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}

/** Abstraction over Room transactions so repositories are unit-testable. */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(private val db: WaterDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
