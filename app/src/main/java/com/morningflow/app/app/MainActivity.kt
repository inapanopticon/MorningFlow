package com.morningflow.app.app

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.morningflow.app.core.designsystem.time.ClockFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Bar icons follow the system theme, as the app does. MorningFlowApp pads its
        // content clear of the bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        ClockFormat.is24Hour = DateFormat.is24HourFormat(this)
        setContent {
            MorningFlowApp()
        }
    }

    override fun onResume() {
        super.onResume()
        ClockFormat.is24Hour = DateFormat.is24HourFormat(this)
    }
}
