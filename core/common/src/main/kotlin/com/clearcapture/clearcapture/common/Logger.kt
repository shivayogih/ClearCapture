package com.clearcapture.clearcapture.common

/**
 * Internal logging port. Implementations must never receive coordinates or file contents;
 * callers log events and sizes only.
 */
interface Logger {
    /** Logs a debug message. */
    fun debug(tag: String, message: String)

    /** Logs a recoverable problem. */
    fun warn(tag: String, message: String, error: Throwable? = null)

    /** Logs an error. */
    fun error(tag: String, message: String, error: Throwable? = null)

    /** Logger that discards everything; the default in release builds. */
    object None : Logger {
        override fun debug(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String, error: Throwable?) = Unit
        override fun error(tag: String, message: String, error: Throwable?) = Unit
    }
}
