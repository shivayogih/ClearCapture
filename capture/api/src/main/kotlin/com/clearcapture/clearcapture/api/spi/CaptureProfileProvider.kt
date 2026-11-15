package com.clearcapture.clearcapture.api.spi

/**
 * Supplies capture profiles as JSON in the documented profile schema (`docs/INTEGRATION.md`, LLD section 6.2).
 * Blocking and callable from Java. The SDK calls it off the main thread, except from
 * [com.clearcapture.clearcapture.api.ClearCapture.checkReadiness], which calls it on the caller's thread.
 *
 * If the JSON is invalid the SDK logs an error and uses its bundled profiles.
 */
public fun interface CaptureProfileProvider {
    /** The profile file content. */
    public fun profilesJson(): String
}
