package com.clearcapture.clearcapture.camera

import android.content.Context
import android.util.Size as AndroidSize
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState as XCameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.domain.model.CaptureError
import com.clearcapture.clearcapture.domain.port.FrameConsumer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Binds Preview, ImageAnalysis and ImageCapture to a lifecycle as LLD section 7 specifies.
 * If all three cannot be bound, it retries without ImageAnalysis so capture still works.
 */
internal class CameraBinder(
    private val context: Context,
    private val controller: CameraXController,
    private val logger: Logger,
) {
    private var provider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private var stateObserver: Pair<androidx.lifecycle.LiveData<XCameraState>, Observer<XCameraState>>? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null

    /** Binds and returns normally; failures are reported through the controller's state. */
    suspend fun bind(
        lifecycleOwner: LifecycleOwner,
        rotation: Int,
        frameConsumer: FrameConsumer?,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
    ) {
        controller.onStarting()
        val cameraProvider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: IllegalStateException) {
            logger.error(TAG, "Camera provider unavailable", e)
            controller.onError(CaptureError.CameraUnavailable)
            return
        }
        provider = cameraProvider
        if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            controller.onError(CaptureError.CameraUnavailable)
            return
        }

        val preview = Preview.Builder()
            .setResolutionSelector(ratio43())
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider { request -> onSurfaceRequest(request) } }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(JPEG_QUALITY)
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .setTargetRotation(rotation)
            .build()
        val analysis = frameConsumer?.let { buildAnalysis(rotation, it) }

        cameraProvider.unbindAll()
        val withAnalysis = analysis?.let { tryBind(cameraProvider, lifecycleOwner, preview, capture, it) }
        val camera = withAnalysis ?: tryBind(cameraProvider, lifecycleOwner, preview, capture, null)
        if (camera == null) {
            controller.onError(CaptureError.CameraUnavailable)
            return
        }
        val analysisBound = withAnalysis != null
        if (!analysisBound) releaseAnalysis()
        imageCapture = capture
        imageAnalysis = analysis.takeIf { analysisBound }
        observeCameraState(camera.cameraInfo.cameraState, lifecycleOwner)
        val capabilities = CameraCapabilityReader.read(camera.cameraInfo, preview, imageAnalysis, capture)
        logger.debug(
            TAG,
            "Bound: preview=${capabilities.previewSize} analysis=${capabilities.analysisSize} capture=${capabilities.captureSize}",
        )
        controller.attach(camera, capture, capabilities, analysisAvailable = analysisBound || frameConsumer == null)
    }

    /** Follows the display rotation without rebinding. */
    fun setRotation(rotation: Int) {
        imageCapture?.targetRotation = rotation
        imageAnalysis?.targetRotation = rotation
    }

    /** Unbinds everything and stops the analysis thread. */
    fun unbind() {
        stateObserver?.let { (data, observer) -> data.removeObserver(observer) }
        stateObserver = null
        provider?.unbindAll()
        releaseAnalysis()
        imageCapture = null
        imageAnalysis = null
        controller.detach()
    }

    private fun buildAnalysis(rotation: Int, consumer: FrameConsumer): ImageAnalysis {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "cc-live-analysis") }
        analysisExecutor = executor
        return ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            AndroidSize(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setTargetRotation(rotation)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(executor) { image ->
                    try {
                        consumer.onFrame(FrameDataAdapter.wrap(image))
                    } finally {
                        image.close()
                    }
                }
            }
    }

    @Suppress("TooGenericExceptionCaught") // CameraX reports bind failures with several unchecked types.
    private fun tryBind(
        provider: ProcessCameraProvider,
        owner: LifecycleOwner,
        preview: Preview,
        capture: ImageCapture,
        analysis: ImageAnalysis?,
    ) = try {
        provider.unbindAll()
        val group = UseCaseGroup.Builder().apply { listOfNotNull(preview, capture, analysis).forEach(::addUseCase) }.build()
        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, group)
    } catch (e: RuntimeException) {
        logger.warn(TAG, "Binding failed (analysis=${analysis != null})", e)
        null
    }

    private fun observeCameraState(data: androidx.lifecycle.LiveData<XCameraState>, owner: LifecycleOwner) {
        val observer = Observer<XCameraState> { state ->
            val error = state.error ?: return@Observer
            val mapped = when (error.code) {
                XCameraState.ERROR_CAMERA_IN_USE, XCameraState.ERROR_MAX_CAMERAS_IN_USE -> CaptureError.CameraInUse
                XCameraState.ERROR_CAMERA_DISABLED -> CaptureError.CameraUnavailable
                XCameraState.ERROR_CAMERA_FATAL_ERROR -> CaptureError.CameraUnavailable
                else -> null
            }
            if (mapped != null && error.type == XCameraState.ErrorType.CRITICAL) controller.onError(mapped)
        }
        data.observe(owner, observer)
        stateObserver = data to observer
    }

    private fun releaseAnalysis() {
        analysisExecutor?.shutdown()
        analysisExecutor = null
    }

    private fun ratio43() = ResolutionSelector.Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
        .build()

    private companion object {
        const val TAG = "CcCamera"
        const val JPEG_QUALITY = 95
        const val ANALYSIS_WIDTH = 640
        const val ANALYSIS_HEIGHT = 480
    }
}
