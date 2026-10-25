package com.dua.ai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dua.ai.ui.DuaApp
import androidx.activity.viewModels
import com.dua.ai.entry.EntryAction
import com.dua.ai.entry.EntryRequests
import com.dua.ai.entry.ExternalEntry

class MainActivity : ComponentActivity() {
    private val entries: EntryRequests by viewModels()
    private var notificationRoute by androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null)
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readRoute(intent); readEntry(intent) }
    private fun readEntry(intent: Intent) {
        ExternalEntry.parse(intent, packageName)?.let { entry ->
            if (entry.action == EntryAction.Remember) intent.putExtra(ExternalEntry.IS_REMEMBER_THIS, true)
            if (!entries.offer(entry)) {
                Toast.makeText(this, "Finish the pending shared items before sharing another.", Toast.LENGTH_LONG).show()
                return@let
            }
            if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 && intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                entry.uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        }
    }
    private fun readRoute(intent: Intent) {
        val projectId = intent.getStringExtra("open_project_id")
        if (projectId != null && com.dua.ai.projects.ProjectCodec.validId(projectId)) notificationRoute = "project:${java.util.UUID.randomUUID()}" to projectId
        else intent.getStringExtra("open_chat_id")?.let { notificationRoute = java.util.UUID.randomUUID().toString() to it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded launch splash (Theme.App.Starting); it hands over to Theme.Dua once the first frame is drawn.
        installSplashScreen()
        // Fresh launch: read and decode saved chats in the background while the UI starts (ChatViewModel takes the result).
        if (savedInstanceState == null) com.dua.ai.history.ChatHistoryPreload.start(getSharedPreferences("dua_chat_history", MODE_PRIVATE))
        // Same for the signed-in session (Keystore decrypt), so the ViewModel does not block the first frame on it.
        if (savedInstanceState == null) com.dua.ai.account.AccountSessionPreload.start(com.dua.ai.account.AccountSessionStore(applicationContext))
        super.onCreate(savedInstanceState)
        readRoute(intent)
        if (savedInstanceState == null) readEntry(intent)
        // Transparent system bars; DuaTheme sets light or dark bar icons to match the theme.
        enableEdgeToEdge()
        setContent {
            DuaApp(notificationRoute = notificationRoute, externalEntry = entries.pending.firstOrNull(), onEntryConsumed = entries::consume)
        }
    }
}
