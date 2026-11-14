package com.clearcapture.clearcapture.api.internal

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.clearcapture.clearcapture.api.CaptureConfig
import com.clearcapture.clearcapture.api.CaptureEvent
import com.clearcapture.clearcapture.api.CaptureResult
import com.clearcapture.clearcapture.api.ClearCapture
import com.clearcapture.clearcapture.api.internal.flow.FlowGraph
import com.clearcapture.clearcapture.api.internal.flow.FlowViewModel
import com.clearcapture.clearcapture.api.internal.flow.SdkFlowHost
import com.clearcapture.clearcapture.api.internal.flow.StartDestination
import com.clearcapture.clearcapture.api.internal.mapping.AppearanceMapper
import com.clearcapture.clearcapture.api.internal.mapping.FlowOutcomeMapper
import com.clearcapture.clearcapture.designsystem.theme.ClearCaptureTheme
import com.clearcapture.clearcapture.domain.model.FlowOutcome
import com.clearcapture.clearcapture.ui.navigation.CaptureNavGraph

/**
 * The SDK's own screen, launched by [com.clearcapture.clearcapture.api.EvidenceCaptureContract]. Not exported,
 * own theme, handles rotation itself so the camera session is not torn down (LLD 7).
 */
internal class CaptureActivity : ComponentActivity() {

    private val config: CaptureConfig by lazy { readConfig(intent) ?: CaptureConfig() }

    private val flow: FlowViewModel by viewModels {
        viewModelFactory {
            initializer {
                val runtime = ClearCapture.runtime(applicationContext)
                FlowViewModel(FlowGraph(runtime, config, ::emitEvent))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val runtime = ClearCapture.runtime(applicationContext)
        if (savedInstanceState == null) emitEvent(CaptureEvent.FlowOpened(config.requestId, config.profileId))
        val start = StartDestination.of(runtime.readinessChecker.check(config.profileId), config.profileId)
        val host = SdkFlowHost(flow.graph, start, runtime::profilesBlocking, ::finishWith)
        // Without the permission step the session starts at once, so location and OpenCV warm up immediately.
        if (savedInstanceState == null && start !is com.clearcapture.clearcapture.ui.navigation.CaptureDestination.Permission) {
            host.onPermissionsGranted()
        }
        val appearance = AppearanceMapper.map(runtime.config.appearance)
        setContent {
            ClearCaptureTheme(
                appearance = appearance,
                onBrandColorRejected = { runtime.logger.error(TAG, "Brand colours rejected: ${it.reason}, contrast ${it.contrastRatio}") },
            ) {
                CaptureNavGraph(host)
            }
        }
    }

    private fun finishWith(outcome: FlowOutcome) {
        val result = FlowOutcomeMapper.map(outcome, config.requestId)
        emitEvent(CaptureEvent.FlowClosed(config.requestId, result))
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RESULT, result))
        finish()
    }

    private fun emitEvent(event: CaptureEvent) {
        val listener = ClearCapture.runtime(applicationContext).config.eventListener ?: return
        runOnUiThread { listener.onEvent(event) }
    }

    internal companion object {
        const val EXTRA_CONFIG: String = "com.clearcapture.clearcapture.CONFIG"
        const val EXTRA_RESULT: String = "com.clearcapture.clearcapture.RESULT"
        private const val TAG = "CcFlow"

        fun readConfig(intent: Intent?): CaptureConfig? = intent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_CONFIG, CaptureConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_CONFIG)
            }
        }

        fun readResult(intent: Intent?): CaptureResult? = intent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(EXTRA_RESULT, CaptureResult::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra(EXTRA_RESULT)
            }
        }
    }
}
