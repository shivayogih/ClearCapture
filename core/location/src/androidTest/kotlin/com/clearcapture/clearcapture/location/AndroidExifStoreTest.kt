package com.clearcapture.clearcapture.location

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** EXIF GPS round trip and tag copying on a real JPEG (LLD 8.2, test plan row "AndroidExifStore"). */
@RunWith(AndroidJUnit4::class)
class AndroidExifStoreTest {

    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val store = AndroidExifStore(Dispatchers.IO)
    private val fix = LocationFix(
        latitude = 12.971599, longitude = 77.594566, altitudeMeters = 920.5, accuracyMeters = 4.2f,
        fixTimeEpochMillis = 1_763_000_000_000, provider = "gps", isMock = false,
    )

    private fun jpeg(name: String): File = File(dir, name).also { file ->
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    @Test
    fun writesEveryMissingTagAndReadsThemBack() = runBlocking {
        val file = jpeg("roundtrip.jpg")
        assertThat(store.readLocation(file)).isNull()

        assertThat(store.writeLocation(file, fix)).isEqualTo(Outcome.Success(Unit))

        val read = store.readLocation(file)
        assertThat(read).isNotNull()
        assertThat(store.matches(read!!, fix)).isTrue()
        assertThat(read.altitudeMeters).isWithin(0.1).of(920.5)
        assertThat(read.accuracyMeters).isWithin(0.05f).of(4.2f)
        assertThat(read.fixTimeEpochMillis).isEqualTo(fix.fixTimeEpochMillis)
        val exif = ExifInterface(file)
        assertThat(exif.getAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD)).isEqualTo("GPS")
        assertThat(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)).isNotNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)).isNotNull()
    }

    @Test
    fun keepsTagsTheCameraAlreadyWrote() = runBlocking {
        val file = jpeg("existing.jpg")
        ExifInterface(file).apply {
            setLatLong(1.0, 2.0)
            saveAttributes()
        }
        store.writeLocation(file, fix)
        val read = store.readLocation(file)!!
        assertThat(read.latitude).isWithin(1e-6).of(1.0)
        assertThat(read.accuracyMeters).isWithin(0.05f).of(4.2f)
    }

    @Test
    fun copiesTagsToAReEncodedFile() = runBlocking {
        val original = jpeg("original.jpg")
        store.writeLocation(original, fix)
        val reEncoded = jpeg("reencoded.jpg")

        store.copyTags(original, reEncoded)

        val read = store.readLocation(reEncoded)
        assertThat(read).isNotNull()
        assertThat(store.matches(read!!, fix)).isTrue()
        assertThat(read.accuracyMeters).isWithin(0.05f).of(4.2f)
    }

    @Test
    fun readsImageInfo() = runBlocking {
        val info = store.readImageInfo(jpeg("info.jpg"))!!
        assertThat(info.width).isEqualTo(64)
        assertThat(info.height).isEqualTo(48)
        assertThat(info.fileBytes).isGreaterThan(0)
    }
}
