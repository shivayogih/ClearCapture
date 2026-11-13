package com.clearcapture.clearcapture.camera

import android.view.Surface
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.domain.port.FrameConsumer

/**
 * Shows the back camera and binds Preview, ImageAnalysis and ImageCapture to this screen's lifecycle.
 * It is the only place that touches the camera lifecycle; everything else goes through [controller].
 *
 * @param frameConsumer receives live frames on the analysis thread; `null` binds no analysis.
 * @param onTap called with the tapped point normalised to 0..1 in preview-surface coordinates.
 */
@Composable
fun CameraPreview(
    controller: CameraXController,
    frameConsumer: FrameConsumer?,
    onTap: ((x: Float, y: Float) -> Unit)?,
    modifier: Modifier = Modifier,
    logger: Logger = Logger.None,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val rotation = view.display?.rotation ?: Surface.ROTATION_0
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    val binder = remember(controller) { CameraBinder(context.applicationContext, controller, logger) }
    val currentConsumer by rememberUpdatedState(frameConsumer)
    val stableConsumer = remember { FrameConsumer { frame -> currentConsumer?.onFrame(frame) } }
    val wantsAnalysis = frameConsumer != null

    LaunchedEffect(lifecycleOwner, binder, wantsAnalysis) {
        binder.bind(lifecycleOwner, rotation, stableConsumer.takeIf { wantsAnalysis }) { surfaceRequest = it }
    }
    LaunchedEffect(rotation) { binder.setRotation(rotation) }
    DisposableEffect(binder) { onDispose { binder.unbind() } }

    val transformer = remember { MutableCoordinateTransformer() }
    val currentOnTap by rememberUpdatedState(onTap)
    Box(modifier) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier = Modifier.pointerInput(request) {
                    detectTapGestures { offset ->
                        val tap = currentOnTap ?: return@detectTapGestures
                        val surface = with(transformer) { offset.transform() }
                        val resolution = request.resolution
                        tap(surface.x / resolution.width, surface.y / resolution.height)
                    }
                },
            )
        }
    }
}
