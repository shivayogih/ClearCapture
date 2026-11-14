package com.clearcapture.clearcapture.data.record

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One capture attempt (LLD section 9). Location is stored in columns so it can be queried without the file. */
@Entity(
    tableName = "captures",
    indices = [Index("capturedAtMillis"), Index("status")],
)
internal data class CaptureEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val profileId: String,
    val status: String,
    val verdict: String?,
    val score: Int?,
    @ColumnInfo(name = "checksJson") val checksJson: String,
    val overrideReason: String?,
    val filePath: String?,
    val width: Int?,
    val height: Int?,
    val fileBytes: Long?,
    val orientation: Int?,
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val fixTimeMillis: Long?,
    val locationProvider: String?,
    val isMock: Boolean,
    val capturedAtMillis: Long,
    val analysisMillis: Long?,
    val shutterMillis: Long,
    val deviceModel: String,
    val cameraId: String,
    val osVersion: Int,
    val profileSchemaVersion: Int,
)
