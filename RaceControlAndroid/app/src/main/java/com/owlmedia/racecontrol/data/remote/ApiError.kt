package com.owlmedia.racecontrol.data.remote

import java.io.IOException

/**
 * Failures the UI knows how to talk about.
 *
 * Messages are resolved by [ApiErrorMessages] so the wording matches the iOS
 * `APIError.errorDescription` strings exactly, since the two apps talk to the same
 * backend and should explain the same failure the same way.
 */
sealed class ApiException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    data object InvalidResponse : ApiException() {
        private fun readResolve(): Any = InvalidResponse
    }

    data class Server(val status: Int, val detail: String?) : ApiException(detail)

    data class Decoding(val error: Throwable) : ApiException(error.message, error)

    data class Transport(val error: Throwable) : ApiException(error.message, error)

    /** True when a retry with a fresh token could plausibly succeed. */
    val isUnauthorized: Boolean get() = this is Server && status == 401
}

/** Marks an IO failure that OkHttp raised because the device is offline. */
class OfflineException(cause: Throwable? = null) : IOException("Offline", cause)
