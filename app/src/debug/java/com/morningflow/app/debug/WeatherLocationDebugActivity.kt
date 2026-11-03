package com.morningflow.app.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.morningflow.app.app.MorningFlowApplication
import com.morningflow.app.core.designsystem.theme.MorningFlowTheme

/** Debug-only entry point for inspecting the weather-location domain output. */
class WeatherLocationDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coordinator = (application as MorningFlowApplication)
            .appContainer
            .weatherLocationCoordinator

        setContent {
            MorningFlowTheme {
                WeatherLocationDebugScreen(coordinator = coordinator)
            }
        }
    }
}
