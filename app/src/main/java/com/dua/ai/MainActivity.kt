package com.dua.ai

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.dua.ai.ui.screens.ChatScreen

class MainActivity : ComponentActivity() {
    private var notificationRoute by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); setIntent(intent); readRoute(intent) }
    private fun readRoute(intent: android.content.Intent) { intent.getStringExtra("open_chat_id")?.let { notificationRoute = java.util.UUID.randomUUID().toString() to it } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readRoute(intent)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            ChatScreen(notificationRoute = notificationRoute)
        }
    }
}
