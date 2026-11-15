package com.clearcapture.clearcapture.data.record

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The SDK's own database. Opened only when the host does not supply a repository. */
@Database(entities = [CaptureEntity::class], version = 1, exportSchema = true)
internal abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao

    companion object {
        /** File name, prefixed so it cannot clash with the host's databases. */
        const val NAME: String = "cc_captures.db"

        /** Every schema change adds a tested migration here; there is no destructive fallback. */
        val MIGRATIONS: List<androidx.room.migration.Migration> = emptyList()

        fun create(context: Context): CaptureDatabase =
            Room.databaseBuilder(context.applicationContext, CaptureDatabase::class.java, NAME)
                .apply { MIGRATIONS.forEach { addMigrations(it) } }
                .build()
    }
}
