package com.clearcapture.clearcapture.location

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.ImageInfo
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.port.ExifStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Reads, completes and verifies the GPS and time tags of a saved JPEG (LLD section 8.2).
 * After every capture each tag in the table is read and any that is absent is written from the fix.
 */
class AndroidExifStore(
    private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger = Logger.None,
) : ExifStore {

    override suspend fun writeLocation(file: File, fix: LocationFix): Outcome<Unit, CaptureError> = withContext(ioDispatcher) {
        try {
            val exif = ExifInterface(file)
            if (exif.latLong == null) exif.setLatLong(fix.latitude, fix.longitude)
            if (exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) == null) fix.altitudeMeters?.let(exif::setAltitude)
            if (exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP) == null || exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP) == null) {
                exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, utc("yyyy:MM:dd", fix.fixTimeEpochMillis))
                exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, utc("HH:mm:ss", fix.fixTimeEpochMillis))
            }
            if (exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD) == null) {
                exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, fix.provider.uppercase(Locale.ROOT))
            }
            // CameraX does not write the accuracy tag; it is always ours.
            exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, rational(fix.accuracyMeters.toDouble()))
            val now = System.currentTimeMillis()
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) == null) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, local("yyyy:MM:dd HH:mm:ss", now))
            }
            // CameraX writes DateTimeOriginal but not its UTC offset, so the offset is checked on its own.
            if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL) == null) {
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, local("XXX", now))
            }
            exif.saveAttributes()
            Outcome.Success(Unit)
        } catch (e: IOException) {
            logger.error(TAG, "Could not write location tags", e)
            Outcome.Failure(CaptureError.FileUnreadable(file.name))
        }
    }

    override suspend fun readLocation(file: File): LocationFix? = withContext(ioDispatcher) {
        try {
            val exif = ExifInterface(file)
            val latLong = exif.latLong ?: return@withContext null
            val altitude = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() }
            LocationFix(
                latitude = latLong[0],
                longitude = latLong[1],
                altitudeMeters = altitude,
                accuracyMeters = exif.getAttributeDouble(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, -1.0).toFloat(),
                fixTimeEpochMillis = gpsTimeMillis(exif) ?: 0L,
                provider = exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD).orEmpty(),
                isMock = false,
            )
        } catch (e: IOException) {
            logger.warn(TAG, "Could not read location tags", e)
            null
        }
    }

    override suspend fun copyTags(from: File, to: File) = withContext(ioDispatcher) {
        try {
            val source = ExifInterface(from)
            val target = ExifInterface(to)
            COPIED_TAGS.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
            target.saveAttributes()
        } catch (e: IOException) {
            logger.error(TAG, "Could not copy tags", e)
        }
    }

    override suspend fun readImageInfo(file: File): ImageInfo? = withContext(ioDispatcher) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
        val exif = try {
            ExifInterface(file)
        } catch (_: IOException) {
            null
        }
        ImageInfo(
            width = options.outWidth,
            height = options.outHeight,
            fileBytes = file.length(),
            orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) ?: 0,
            captureTimeEpochMillis = exif?.let(::originalTime),
        )
    }

    /** True when [found] matches [expected] within 0.00001 degrees (LLD 8.2 verification). */
    fun matches(found: LocationFix, expected: LocationFix): Boolean =
        abs(found.latitude - expected.latitude) <= COORDINATE_TOLERANCE && abs(found.longitude - expected.longitude) <= COORDINATE_TOLERANCE

    /** Parses GPSDateStamp (yyyy:MM:dd) and GPSTimeStamp (HH:mm:ss as ExifInterface returns it, UTC); null when absent or malformed. */
    private fun gpsTimeMillis(exif: ExifInterface): Long? {
        val date = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)
        val time = exif.getAttribute(ExifInterface.TAG_GPS_TIMESTAMP)
        if (date == null || time == null || !GPS_TIME.matches(time)) return null
        val text = "$date $time"
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(text)?.time
        } catch (_: java.text.ParseException) {
            null
        }
    }

    /** Parses DateTimeOriginal with its offset; null when absent or malformed. */
    private fun originalTime(exif: ExifInterface): Long? {
        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
        val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
        val pattern = if (offset != null) "yyyy:MM:dd HH:mm:ssXXX" else "yyyy:MM:dd HH:mm:ss"
        return try {
            SimpleDateFormat(pattern, Locale.US).parse(dateTime + offset.orEmpty())?.time
        } catch (_: java.text.ParseException) {
            null
        }
    }

    private fun rational(value: Double): String = "${(value * RATIONAL_DENOMINATOR).roundToLong()}/$RATIONAL_DENOMINATOR"

    private fun utc(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    private fun local(pattern: String, millis: Long): String = SimpleDateFormat(pattern, Locale.US).format(Date(millis))

    /** Constants. */
    companion object {
        /** Tags carried across a re-encode. */
        val COPIED_TAGS: List<String> = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
            ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_ORIENTATION,
        )

        private const val TAG = "CcExif"
        private const val RATIONAL_DENOMINATOR = 10
        private const val COORDINATE_TOLERANCE = 0.00001
        private val GPS_TIME = Regex("""\d{1,2}:\d{2}:\d{2}""")
    }
}
