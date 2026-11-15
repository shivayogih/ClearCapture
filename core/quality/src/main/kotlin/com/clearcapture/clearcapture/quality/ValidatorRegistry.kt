package com.clearcapture.clearcapture.quality

import com.clearcapture.clearcapture.opencv.MatPool
import com.clearcapture.clearcapture.quality.validator.BlurValidator
import com.clearcapture.clearcapture.quality.validator.ExposureValidator
import com.clearcapture.clearcapture.quality.validator.FramingValidator
import com.clearcapture.clearcapture.quality.validator.GlareValidator
import com.clearcapture.clearcapture.quality.validator.ObstructionValidator
import com.clearcapture.clearcapture.quality.validator.ResolutionValidator

/** Every validator, in report order. A new check is one class plus one line here (LLD 10). */
internal object ValidatorRegistry {
    /** Creates the validators; they take their working mats from [pool]. Creating them needs no OpenCV. */
    fun create(pool: MatPool): List<QualityValidator> = listOf(
        BlurValidator(pool),
        ExposureValidator(pool),
        GlareValidator(pool),
        FramingValidator(pool),
        ResolutionValidator(),
        ObstructionValidator(pool),
    )
}
