package com.dua.ai

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dua.ai.ui.DuaApp

class MainActivity : ComponentActivity() {
    private var notificationRoute by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); readRoute(intent) }
    private fun readRoute(intent: android.content.Intent) { intent.getStringExtra("open_chat_id")?.let { notificationRoute = java.util.UUID.randomUUID().toString() to it } }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch splash (Theme.App.Starting); it hands over to Theme.Dua once the first frame is drawn.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        readRoute(intent)
        // Transparent system bars; DuaTheme sets light or dark bar icons to match the theme.
        enableEdgeToEdge()
        setContent {
            DuaApp(notificationRoute = notificationRoute)
        }
    }
}
