package com.clearcapture.clearcapture.domain.model

/** Width and height in pixels. */
data class Size(val width: Int, val height: Int) {
    /** The longer side. */
    val longSide: Int get() = maxOf(width, height)

    /** The shorter side. */
    val shortSide: Int get() = minOf(width, height)
}

/** What the bound camera can do and what it actually selected. */
data class CameraCapabilities(
    val cameraId: String,
    val previewSize: Size?,
    val analysisSize: Size?,
    val captureSize: Size?,
    val hasFlash: Boolean,
    val hasAutoFocus: Boolean,
    val hardwareLevel: String,
    val supportedCaptureSizes: List<Size> = emptyList(),
) {
    /** The traits used to match device overrides. */
    val traits: CameraTraits get() = CameraTraits(fixedFocus = !hasAutoFocus, hasFlash = hasFlash, hardwareLevel = hardwareLevel)
}

/** Lifecycle of the camera for one capture flow. */
sealed interface CameraState {
    /** Not bound yet. */
    data object Idle : CameraState

    /** Binding in progress. */
    data object Starting : CameraState

    /** Bound and showing frames. [analysisAvailable] is false when ImageAnalysis could not be bound. */
    data class Ready(val capabilities: CameraCapabilities, val analysisAvailable: Boolean = true) : CameraState

    /** The camera could not be opened or used. */
    data class Error(val error: CaptureError) : CameraState
}
