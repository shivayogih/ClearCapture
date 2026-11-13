package com.clearcapture.clearcapture.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.Size

/** Reads what the bound camera can do and which resolutions CameraX actually selected. */
internal object CameraCapabilityReader {

    @OptIn(ExperimentalCamera2Interop::class)
    fun read(info: CameraInfo, preview: Preview, analysis: ImageAnalysis?, capture: ImageCapture): CameraCapabilities {
        val camera2 = Camera2CameraInfo.from(info)
        val afModes = camera2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val minFocus = camera2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val hasAutoFocus = minFocus > 0f && afModes.any { it != CameraMetadata.CONTROL_AF_MODE_OFF }
        val level = camera2.getCameraCharacteristic(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        val jpegSizes = camera2.getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            ?.map { Size(it.width, it.height) }
            ?.sortedByDescending { it.width.toLong() * it.height }
            .orEmpty()
        return CameraCapabilities(
            cameraId = camera2.cameraId,
            previewSize = preview.resolutionInfo?.resolution?.let { Size(it.width, it.height) },
            analysisSize = analysis?.resolutionInfo?.resolution?.let { Size(it.width, it.height) },
            captureSize = capture.resolutionInfo?.resolution?.let { Size(it.width, it.height) },
            hasFlash = info.hasFlashUnit(),
            hasAutoFocus = hasAutoFocus,
            hardwareLevel = hardwareLevelName(level),
            supportedCaptureSizes = jpegSizes,
        )
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }
}
