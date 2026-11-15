package com.clearcapture.clearcapture.camera

import android.location.Location
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.common.Outcome
import com.clearcapture.clearcapture.domain.model.CameraCapabilities
import com.clearcapture.clearcapture.domain.model.CameraState
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.model.LocationFix
import com.clearcapture.clearcapture.domain.port.CameraController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * CameraX implementation of the domain [CameraController]. One instance per capture flow.
 *
 * Binding is done by [CameraPreview], which is the only place that touches the lifecycle; it hands
 * the bound camera to this controller with [attach] and takes it back with [detach].
 */
class CameraXController(
    private val callbackExecutor: Executor,
    private val logger: Logger = Logger.None,
) : CameraController {

    private val mutableState = MutableStateFlow<CameraState>(CameraState.Idle)
    override val state: StateFlow<CameraState> = mutableState.asStateFlow()

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    internal fun onStarting() {
        mutableState.value = CameraState.Starting
    }

    internal fun attach(camera: Camera, imageCapture: ImageCapture, capabilities: CameraCapabilities, analysisAvailable: Boolean) {
        this.camera = camera
        this.imageCapture = imageCapture
        mutableState.value = CameraState.Ready(capabilities, analysisAvailable)
    }

    internal fun onError(error: CaptureError) {
        camera = null
        imageCapture = null
        mutableState.value = CameraState.Error(error)
    }

    internal fun detach() {
        camera = null
        imageCapture = null
        if (mutableState.value !is CameraState.Error) mutableState.value = CameraState.Idle
    }

    override suspend fun takePicture(target: File, location: LocationFix?): Outcome<File, CaptureError> {
        val capture = imageCapture ?: return Outcome.Failure(CaptureError.CameraUnavailable)
        val metadata = ImageCapture.Metadata().apply { this.location = location?.toAndroidLocation() }
        val options = ImageCapture.OutputFileOptions.Builder(target).setMetadata(metadata).build()
        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                options,
                callbackExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        continuation.resume(Outcome.Success(target))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        logger.warn(TAG, "takePicture failed with code ${exception.imageCaptureError}", exception)
                        val error = if (exception.imageCaptureError == ImageCapture.ERROR_FILE_IO) {
                            CaptureError.StorageFull
                        } else {
                            CaptureError.CaptureFailed("code ${exception.imageCaptureError}")
                        }
                        continuation.resume(Outcome.Failure(error))
                    }
                },
            )
        }
    }

    override suspend fun focusAt(x: Float, y: Float) {
        val bound = camera ?: return
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()
        bound.cameraControl.startFocusAndMetering(action)
    }

    override fun setTorch(enabled: Boolean) {
        val bound = camera ?: return
        if (bound.cameraInfo.hasFlashUnit()) bound.cameraControl.enableTorch(enabled)
    }

    private fun LocationFix.toAndroidLocation(): Location = Location(provider).also {
        it.latitude = latitude
        it.longitude = longitude
        altitudeMeters?.let { altitude -> it.altitude = altitude }
        it.accuracy = accuracyMeters
        it.time = fixTimeEpochMillis
    }

    private companion object {
        const val TAG = "CcCamera"
        const val FOCUS_AUTO_CANCEL_SECONDS = 3L
    }
}
