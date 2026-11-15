package com.clearcapture.clearcapture.api.internal.files

import androidx.core.content.FileProvider

/**
 * The SDK's own file provider (authority `${applicationId}.clearcapture.files`). A subclass so its manifest entry
 * never merges with a host's own `FileProvider`.
 */
internal class ClearCaptureFileProvider : FileProvider()
