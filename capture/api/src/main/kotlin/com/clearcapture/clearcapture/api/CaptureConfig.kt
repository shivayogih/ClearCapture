package com.clearcapture.clearcapture.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * What the host asks for when it launches a capture session (SDK_REQUIREMENTS 7). One screen, one or more photos.
 * Kotlin hosts use named arguments; Java hosts use [Builder].
 *
 * @property shots what each photo is, in order: its subject profile and an optional label such as "Bin front".
 *   Photos beyond the list use [defaultProfileId].
 * @property defaultProfileId the subject when no shot is planned; `null` uses `general`.
 * @property allowSubjectChange true lets the worker switch the subject on the capture screen.
 * @property minPhotos photos needed before the worker can finish (at least 1).
 * @property maxPhotos the shutter stops here; the session finishes on its own at this count.
 * @property storage where kept photos are saved.
 * @property locationMode whether photos carry location; `null` lets each subject profile decide.
 * @property references the host's identifiers, written into every photo (at most 16 entries of 128 characters).
 * @property output optional size and quality limits for the saved JPEG.
 * @property allowAcceptOnWarning overrides the profiles' rule for this launch; `null` keeps each profile's value.
 * @property allowOverrideOnFail overrides the profiles' rule for this launch; `null` keeps each profile's value.
 * @throws IllegalArgumentException when the counts or references are out of range.
 */
@Parcelize
public data class CaptureConfig @JvmOverloads constructor(
    val shots: List<Shot> = emptyList(),
    val defaultProfileId: String? = null,
    val allowSubjectChange: Boolean = true,
    val minPhotos: Int = 1,
    val maxPhotos: Int = maxOf(minPhotos, shots.size, 1),
    val storage: StorageTarget = StorageTarget.AppPrivate(),
    val locationMode: LocationMode? = null,
    val references: Map<String, String> = emptyMap(),
    val output: OutputOptions = OutputOptions(),
    val requestId: String = UUID.randomUUID().toString(),
    val allowAcceptOnWarning: Boolean? = null,
    val allowOverrideOnFail: Boolean? = null,
) : Parcelable {

    init {
        require(minPhotos >= 1) { "minPhotos must be at least 1, was $minPhotos" }
        require(maxPhotos >= minPhotos) { "maxPhotos ($maxPhotos) must not be below minPhotos ($minPhotos)" }
        require(maxPhotos <= MAX_PHOTOS) { "maxPhotos must not exceed $MAX_PHOTOS, was $maxPhotos" }
        require(references.size <= MAX_REFERENCES) { "At most $MAX_REFERENCES references are allowed" }
        require(references.all { (k, v) -> k.length <= MAX_REFERENCE_LENGTH && v.length <= MAX_REFERENCE_LENGTH }) {
            "Reference keys and values must not exceed $MAX_REFERENCE_LENGTH characters"
        }
    }

    /** Builds a [CaptureConfig] from Java. Every setter is optional. */
    public class Builder {
        private var shots: List<Shot> = emptyList()
        private var defaultProfileId: String? = null
        private var allowSubjectChange = true
        private var minPhotos = 1
        private var maxPhotos: Int? = null
        private var storage: StorageTarget = StorageTarget.AppPrivate()
        private var locationMode: LocationMode? = null
        private var references: Map<String, String> = emptyMap()
        private var output = OutputOptions()
        private var requestId = UUID.randomUUID().toString()

        /** See [CaptureConfig.shots]. */
        public fun shots(shots: List<Shot>): Builder = apply { this.shots = shots }

        /** See [CaptureConfig.defaultProfileId]. */
        public fun defaultProfileId(id: String?): Builder = apply { defaultProfileId = id }

        /** See [CaptureConfig.allowSubjectChange]. */
        public fun allowSubjectChange(allow: Boolean): Builder = apply { allowSubjectChange = allow }

        /** See [CaptureConfig.minPhotos] and [CaptureConfig.maxPhotos]. */
        public fun photos(min: Int, max: Int): Builder = apply {
            minPhotos = min
            maxPhotos = max
        }

        /** See [CaptureConfig.storage]. */
        public fun storage(storage: StorageTarget): Builder = apply { this.storage = storage }

        /** See [CaptureConfig.locationMode]. */
        public fun locationMode(mode: LocationMode?): Builder = apply { locationMode = mode }

        /** See [CaptureConfig.references]. */
        public fun references(references: Map<String, String>): Builder = apply { this.references = references }

        /** See [CaptureConfig.output]. */
        public fun output(output: OutputOptions): Builder = apply { this.output = output }

        /** See [CaptureConfig.requestId]. */
        public fun requestId(id: String): Builder = apply { requestId = id }

        /** Builds the configuration. @throws IllegalArgumentException when values are out of range. */
        public fun build(): CaptureConfig = CaptureConfig(
            shots = shots,
            defaultProfileId = defaultProfileId,
            allowSubjectChange = allowSubjectChange,
            minPhotos = minPhotos,
            maxPhotos = maxPhotos ?: maxOf(minPhotos, shots.size, 1),
            storage = storage,
            locationMode = locationMode,
            references = references,
            output = output,
            requestId = requestId,
        )
    }

    /** Limits. */
    public companion object {
        /** The most photos one session can take. */
        public const val MAX_PHOTOS: Int = 50

        /** The most host references one session can carry. */
        public const val MAX_REFERENCES: Int = 16

        /** The longest reference key or value. */
        public const val MAX_REFERENCE_LENGTH: Int = 128
    }
}

/** One planned photo: its subject profile (for example `container`) and an optional label shown to the worker. */
@Parcelize
public data class Shot @JvmOverloads constructor(val profileId: String, val label: String? = null) : Parcelable

/** Whether photos carry location (SDK_REQUIREMENTS 7.1a). */
public enum class LocationMode {
    /** Asked for; capture blocked without a usable fix; GPS tags always written and verified. */
    REQUIRED,

    /** Asked for once; capture continues without it; each image says why location is missing. */
    OPTIONAL,

    /** Never asked for; no GPS tags. */
    OFF,
}

/** Where kept photos are saved. The result gives a readable URI for each one. */
public sealed interface StorageTarget : Parcelable {

    /** The app's private storage (the default): `files/cc_captures/yyyy/MM/dd`, or [subdirectory] inside `files`. */
    @Parcelize
    public data class AppPrivate @JvmOverloads constructor(val subdirectory: String? = null) : StorageTarget

    /**
     * The host's own folder, created if missing. It must be writable by the app without extra permissions (its
     * internal or external files directories). A folder that cannot be written is reported when the session opens.
     */
    @Parcelize
    public data class Directory(val path: String) : StorageTarget

    /** The shared gallery: `Pictures/<album>`, visible to other apps. Android 10 and later need no permission. */
    @Parcelize
    public data class SharedPictures(val album: String) : StorageTarget
}

/**
 * Size limits for the saved JPEG, to keep uploads small. Quality checks always run on the original; EXIF is copied
 * and verified after any re-encode.
 *
 * @property maxLongSide the longest side in pixels; `null` keeps the camera's size.
 * @property jpegQuality 1..100 used when the photo is re-encoded; `null` keeps the camera's file as it is.
 */
@Parcelize
public data class OutputOptions @JvmOverloads constructor(
    val maxLongSide: Int? = null,
    val jpegQuality: Int? = null,
) : Parcelable {
    init {
        require(maxLongSide == null || maxLongSide >= MIN_LONG_SIDE) { "maxLongSide must be at least $MIN_LONG_SIDE" }
        require(jpegQuality == null || jpegQuality in 1..100) { "jpegQuality must be within 1..100" }
    }

    private companion object {
        const val MIN_LONG_SIDE = 640
    }
}
