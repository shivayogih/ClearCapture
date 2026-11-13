package com.clearcapture.clearcapture.api.spi

/** Receives the SDK's log messages. Messages never contain coordinates or file contents. */
public interface CaptureLogger {
    /** Debug detail. */
    public fun debug(tag: String, message: String)

    /** A recoverable problem. */
    public fun warn(tag: String, message: String, error: Throwable?)

    /** An error. */
    public fun error(tag: String, message: String, error: Throwable?)
}
