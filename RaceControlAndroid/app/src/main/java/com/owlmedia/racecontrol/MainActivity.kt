package com.owlmedia.racecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.owlmedia.racecontrol.core.design.RaceControlTheme
import com.owlmedia.racecontrol.feature.RootScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Mandatory from Android 15; every scaffold below consumes insets itself.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RaceControlTheme {
                RootScreen()
            }
        }
    }
}
