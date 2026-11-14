package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.common.Logger
import com.clearcapture.clearcapture.domain.port.QualityAnalyzer
import com.clearcapture.clearcapture.opencv.AndroidImageCodec
import com.clearcapture.clearcapture.opencv.JpegDecoder
import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.opencv.OpenCvInitializer
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Creates the SDK's [QualityAnalyzer] for one capture flow, with every validator of the registry, its own
 * `Mat` pool and the Android JPEG decoder.
 *
 * [initializer] is process-scoped and shared between flows, so OpenCV loads once. [dispatcher] runs
 * `warmUp` and `analyzeFile` (the Default dispatcher in production, LLD 12). `analyzeLive` runs on the caller's
 * thread. Building the analyzer does not touch OpenCV.
 */
public fun createQualityAnalyzer(
    initializer: OpenCvInitializer,
    dispatcher: CoroutineDispatcher,
    logger: Logger = Logger.None,
): QualityAnalyzer {
    val pool = MatPool()
    return OpenCvQualityAnalyzer(
        initializer = initializer,
        validators = ValidatorRegistry.create(pool),
        pool = pool,
        decoder = JpegDecoder(AndroidImageCodec()),
        dispatcher = dispatcher,
        logger = logger,
    )
}
