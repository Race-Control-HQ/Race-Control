package com.owlmedia.racecontrol.core.ui

/**
 * A generic async-loading phase, mirroring the iOS `Loadable<Value>` enum.
 *
 * Every screen that fetches data models its state with this, so loading, error
 * (with retry) and empty states are handled identically everywhere.
 */
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Loaded<T>(val value: T, val fromCache: Boolean = false) : UiState<T>
    data class Failed(val message: String) : UiState<Nothing>
}

val <T> UiState<T>.valueOrNull: T?
    get() = (this as? UiState.Loaded)?.value

val <T> UiState<T>.isLoaded: Boolean
    get() = this is UiState.Loaded
