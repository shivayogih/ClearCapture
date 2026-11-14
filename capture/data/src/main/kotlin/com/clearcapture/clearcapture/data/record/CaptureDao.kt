package com.clearcapture.clearcapture.data.record

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries on the capture log. */
@Dao
internal interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CaptureEntity)

    @Query(
        "UPDATE captures SET status = :status, filePath = COALESCE(:filePath, filePath), " +
            "overrideReason = :overrideReason WHERE id = :id",
    )
    suspend fun updateStatus(id: String, status: String, filePath: String?, overrideReason: String?)

    @Query("UPDATE captures SET status = :status WHERE id = :id")
    suspend fun updateStatusOnly(id: String, status: String)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM captures WHERE capturedAtMillis < :epochMillis")
    suspend fun deleteOlderThan(epochMillis: Long): Int

    @Query("SELECT * FROM captures ORDER BY capturedAtMillis DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun byId(id: String): CaptureEntity?
}
