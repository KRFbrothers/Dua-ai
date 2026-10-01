package com.nova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.nova.app.ui.navigation.NovaApp
import com.nova.app.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            // Icons flip with system theme; NovaTheme reads the same flag.
            isAppearanceLightStatusBars = !resources.configuration.isNight()
        }
        setContent {
            val dark = isSystemInDarkTheme()
            NovaTheme(darkTheme = dark, useDynamicColor = false) {
                NovaApp()
            }
        }
    }
}

private fun android.content.res.Configuration.isNight(): Boolean =
    uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
