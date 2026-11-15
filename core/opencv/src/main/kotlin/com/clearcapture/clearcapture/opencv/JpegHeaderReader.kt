package com.clearcapture.clearcapture.opencv

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Facts read from a JPEG file's structure, without decoding pixels and without OpenCV.
 *
 * [width] and [height] are the stored dimensions (before EXIF orientation is applied).
 */
public data class JpegHeader(
    val width: Int,
    val height: Int,
    /** EXIF orientation as stored; 0 when the tag is absent. Values outside 1..8 are kept so they can be reported. */
    val orientation: Int,
    /** True when the entropy-coded data is followed by an end-of-image marker, so the file is not truncated. */
    val complete: Boolean,
    /** File size in bytes. */
    val fileBytes: Long,
) {
    /** Width of the upright image. */
    val uprightWidth: Int get() = if (ExifOrientation.swapsAxes(orientation)) height else width

    /** Height of the upright image. */
    val uprightHeight: Int get() = if (ExifOrientation.swapsAxes(orientation)) width else height
}

/** Reads the dimensions, the EXIF orientation and the completeness of a JPEG file. Pure Kotlin. */
public object JpegHeaderReader {

    /** The header of [file], or `null` when the file cannot be read or is not a baseline or progressive JPEG. */
    public fun read(file: File): JpegHeader? = try {
        val fileBytes = file.length()
        // Fast path: a file that ends with EOI is complete. Otherwise scan, because some writers append data after EOI.
        val scanForEoi = !endsWithEoi(file)
        file.inputStream().buffered(BUFFER_SIZE).use { input -> parse(input, fileBytes, scanForEoi) }
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    private fun parse(input: InputStream, fileBytes: Long, scanForEoi: Boolean): JpegHeader? {
        if (input.read() != MARKER_PREFIX || input.read() != SOI) return null
        val frame = FrameFacts()
        var marker = nextMarker(input)
        while (marker != null && marker !in SCAN_OR_END && frame.readable) {
            val standalone = marker in RST0..RST7 || marker == TEM
            if (!standalone) frame.readable = readSegment(input, marker, frame)
            marker = nextMarker(input)
        }
        val scanHeader = if (marker == SOS && frame.isValid) readLength(input) else null
        if (scanHeader == null) return null
        skip(input, scanHeader)
        val complete = !scanForEoi || scanToEoi(input)
        return JpegHeader(frame.width, frame.height, frame.orientation, complete, fileBytes)
    }

    /** Reads one marker segment into [frame]. False when the file ends inside the segment. */
    private fun readSegment(input: InputStream, marker: Int, frame: FrameFacts): Boolean {
        val length = readLength(input) ?: return false
        val wanted = (marker == APP1 && frame.orientation == 0) || marker in SOF_MARKERS
        if (!wanted) {
            skip(input, length)
            return true
        }
        val payload = readFully(input, length) ?: return false
        if (marker == APP1) frame.orientation = exifOrientation(payload) else frame.readStartOfFrame(payload)
        return true
    }

    /** What the segments before the first scan say about the image. */
    private class FrameFacts {
        var width = 0
        var height = 0
        var orientation = 0
        var readable = true

        val isValid: Boolean get() = readable && width > 0 && height > 0

        fun readStartOfFrame(payload: ByteArray) {
            if (payload.size < SOF_MIN_LENGTH) {
                readable = false
            } else {
                height = u16(payload, 1, bigEndian = true)
                width = u16(payload, SOF_WIDTH_OFFSET, bigEndian = true)
            }
        }
    }

    /** Reads entropy-coded data and the segments between scans until EOI. False when the file ends first. */
    private fun scanToEoi(input: InputStream): Boolean {
        var previous = input.read()
        while (previous >= 0) {
            val current = input.read()
            if (current < 0) return false
            if (previous == MARKER_PREFIX) {
                when (current) {
                    EOI -> return true
                    0x00, MARKER_PREFIX, in RST0..RST7 -> Unit
                    // A marker segment between progressive scans: skip it so its payload is not scanned.
                    else -> skip(input, readLength(input) ?: return false)
                }
                previous = if (current == MARKER_PREFIX) current else input.read()
            } else {
                previous = current
            }
        }
        return false
    }

    /** True when the last non-zero bytes of [file] are the EOI marker. */
    private fun endsWithEoi(file: File): Boolean = RandomAccessFile(file, "r").use { raf ->
        val tail = minOf(raf.length(), TAIL_BYTES.toLong()).toInt()
        if (tail < 2) return false
        val bytes = ByteArray(tail)
        raf.seek(raf.length() - tail)
        raf.readFully(bytes)
        var end = bytes.size
        while (end > 0 && bytes[end - 1].toInt() == 0) end--
        end >= 2 && bytes[end - 2].toInt() and BYTE_MASK == MARKER_PREFIX && bytes[end - 1].toInt() and BYTE_MASK == EOI
    }

    private fun nextMarker(input: InputStream): Int? {
        var byte = input.read()
        while (byte >= 0 && byte != MARKER_PREFIX) byte = input.read()
        while (byte == MARKER_PREFIX) byte = input.read()
        return if (byte < 0) null else byte
    }

    /** Length of the segment payload, excluding the two length bytes. */
    private fun readLength(input: InputStream): Int? {
        val high = input.read()
        val low = input.read()
        if (high < 0 || low < 0) return null
        val length = (high shl BITS_PER_BYTE) or low
        return if (length < 2) null else length - 2
    }

    private fun readFully(input: InputStream, length: Int): ByteArray? {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) return null
            read += count
        }
        return bytes
    }

    private fun skip(input: InputStream, length: Int) {
        var remaining = length.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() < 0) return
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    /** The orientation tag of IFD0 in an APP1 Exif payload, or 0 when absent or unreadable. */
    private fun exifOrientation(payload: ByteArray): Int {
        val tiff = EXIF_HEADER.size
        val bigEndian = byteOrder(payload) ?: return 0
        val ifd = tiff + u32(payload, tiff + IFD_OFFSET_POSITION, bigEndian)
        if (ifd < tiff || ifd + 2 > payload.size) return 0
        val entry = (0 until u16(payload, ifd, bigEndian))
            .asSequence()
            .map { index -> ifd + 2 + index * IFD_ENTRY_LENGTH }
            .takeWhile { entry -> entry + IFD_ENTRY_LENGTH <= payload.size }
            .firstOrNull { entry -> u16(payload, entry, bigEndian) == ORIENTATION_TAG }
        return entry?.let { u16(payload, it + IFD_VALUE_OFFSET, bigEndian) } ?: 0
    }

    /** True for big-endian ("MM") TIFF data, false for little-endian ("II"), `null` when this is not Exif. */
    private fun byteOrder(payload: ByteArray): Boolean? {
        val tiff = EXIF_HEADER.size
        val isExif = payload.size >= tiff + TIFF_HEADER_LENGTH && EXIF_HEADER.indices.all { payload[it] == EXIF_HEADER[it] }
        val first = if (isExif) payload[tiff].toInt().toChar() else ' '
        val second = if (isExif) payload[tiff + 1].toInt().toChar() else ' '
        return when {
            first == 'M' && second == 'M' -> true
            first == 'I' && second == 'I' -> false
            else -> null
        }
    }

    private fun u16(bytes: ByteArray, at: Int, bigEndian: Boolean): Int {
        val first = bytes[at].toInt() and BYTE_MASK
        val second = bytes[at + 1].toInt() and BYTE_MASK
        return if (bigEndian) (first shl BITS_PER_BYTE) or second else (second shl BITS_PER_BYTE) or first
    }

    private fun u32(bytes: ByteArray, at: Int, bigEndian: Boolean): Int {
        val high = u16(bytes, if (bigEndian) at else at + 2, bigEndian)
        val low = u16(bytes, if (bigEndian) at + 2 else at, bigEndian)
        return (high shl (2 * BITS_PER_BYTE)) or low
    }

    private const val BUFFER_SIZE = 64 * 1024
    private const val TAIL_BYTES = 4096
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8
    private const val MARKER_PREFIX = 0xFF
    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP1 = 0xE1
    private const val TEM = 0x01
    private const val RST0 = 0xD0
    private const val RST7 = 0xD7
    private const val SOF_MIN_LENGTH = 5
    private const val SOF_WIDTH_OFFSET = 3
    private const val TIFF_HEADER_LENGTH = 8
    private const val IFD_OFFSET_POSITION = 4
    private const val IFD_ENTRY_LENGTH = 12
    private const val IFD_VALUE_OFFSET = 8
    private const val ORIENTATION_TAG = 0x0112

    /** Start-of-frame markers: C0..CF except DHT (C4), JPG (C8) and DAC (CC). */
    private val SOF_MARKERS = (0xC0..0xCF).toSet() - setOf(0xC4, 0xC8, 0xCC)
    private val SCAN_OR_END = setOf(SOS, EOI)
    private val EXIF_HEADER = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
}
