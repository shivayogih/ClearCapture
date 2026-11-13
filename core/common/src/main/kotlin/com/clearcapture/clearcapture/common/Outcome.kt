package com.clearcapture.clearcapture.common

/**
 * Result of an operation that can fail in an expected way. Errors are typed values, never
 * exceptions crossing a layer and never nulls with a hidden meaning.
 */
sealed interface Outcome<out T, out E> {
    /** The operation produced [value]. */
    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    /** The operation failed with [error]. */
    data class Failure<out E>(val error: E) : Outcome<Nothing, E>
}

/** Transforms the success value, leaving a failure untouched. */
inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** Chains another fallible operation on the success value. */
inline fun <T, E, R> Outcome<T, E>.flatMap(transform: (T) -> Outcome<R, E>): Outcome<R, E> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/** The success value, or `null` when this is a failure. */
fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** The error, or `null` when this is a success. */
fun <T, E> Outcome<T, E>.errorOrNull(): E? = (this as? Outcome.Failure)?.error
