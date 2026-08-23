package com.owlmedia.racecontrol.util

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * Holds the screen awake while [enabled].
 *
 * Used by the race replay, which the user watches rather than touches; the
 * display timing out halfway through is precisely the wrong behaviour. The flag
 * is always cleared on dispose so it can never leak past the screen.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(enabled, view) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
