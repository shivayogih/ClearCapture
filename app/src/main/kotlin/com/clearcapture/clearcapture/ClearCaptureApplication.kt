package com.clearcapture.clearcapture

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application of the POC shell. It is the Hilt root; the SDK itself needs no Application class. */
@HiltAndroidApp
class ClearCaptureApplication : Application()
