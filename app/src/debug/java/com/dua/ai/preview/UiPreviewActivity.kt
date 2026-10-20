package com.dua.ai.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.PromptSuggestion
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.CodeExecutionConfig
import com.dua.ai.ui.chat.*
import com.dua.ai.ui.account.OnboardingContent
import com.dua.ai.ui.theme.DuaTheme

/** Runs production UI against synthetic fixtures, without creating a ViewModel or contacting a server. */
class UiPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { UiPreview() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UiPreview() {
    var mode by rememberSaveable { mutableStateOf("Home") }
    var theme by rememberSaveable { mutableStateOf("dark") }
    var draft by rememberSaveable { mutableStateOf("") }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var project by rememberSaveable { mutableStateOf("personal") }
    var reasoning by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf(false) }
    var web by rememberSaveable { mutableStateOf<String?>(null) }
    var unavailable by remember { mutableStateOf<String?>(null) }
    val examples = remember { listOf(
        ChatMessage(id = "sample-user", text = "Show a simple Kotlin function and explain it.", isUser = true),
        ChatMessage(id = "sample-reply", text = "**Sample conversation — no AI request was made.**\n\nThis function doubles a number:\n\n```kotlin\nfun double(value: Int): Int = value * 2\n```\n\n- It accepts an integer.\n- It returns the integer multiplied by two.", isUser = false, model = "Dua"),
    ) }
    val saved = remember { listOf(SavedChat("sample-chat", "Dua", "", examples, 0L)) }
    val suggestions = remember { listOf(
        PromptSuggestion("Explain some code", "Explain this Kotlin function step by step.", "Coding"),
        PromptSuggestion("Draft a proposal", "Help me draft a short client proposal.", "Business"),
    ) }
    DuaTheme(LocalPreferences(theme = theme)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("UI preview · sample data · no backend", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Home", "Chat", "Error", "Welcome").forEach { item ->
                            TextButton(onClick = { mode = item; notice = null }) { Text(item) }
                        }
                        TextButton(onClick = { theme = if (theme == "dark") "light" else "dark" }) { Text(if (theme == "dark") "Light" else "Dark") }
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (mode == "Welcome") OnboardingContent(false, notice,
                        onSignIn = { notice = "Preview only. Open the normal Dua app to sign in with the owner's account." },
                        onSkip = { mode = "Home" })
                    else ChatScreen(ChatUiState(draft = draft, projectId = project,
                        messages = if (mode == "Chat") examples else emptyList(), savedChats = saved,
                        suggestions = suggestions, error = notice ?: if (mode == "Error") "Sample offline error. Nothing was sent; reconnect in the real app to retry." else null,
                        reasoning = reasoning, runCode = code, webMode = web,
                        webSearchConfigured = true, codeConfig = CodeExecutionConfig(true, providers = setOf("Dua"))),
                        ChatActions(onDraftChange = { draft = it }, onNewChat = { mode = "Home"; draft = ""; notice = null },
                            onReasoning = { reasoning = it }, onRunCode = { code = it }, onWebMode = { web = it },
                            onOpenChat = { mode = "Chat" }, onSelectProject = { project = it; mode = "Home"; draft = "" },
                            onSend = { notice = "Preview only: no message sent and no AI response generated." },
                            onRetry = { notice = "Preview only: no backend retry was made." },
                            onSaveProject = { _, _, _, _ -> notice = "Preview projects are fixtures; nothing was saved."; false },
                            onOpenSetup = { unavailable = "Open the normal app's Feature setup to check real backend configuration." },
                            onOpenComputer = { unavailable = "Computer · Coming soon" },
                            onOpenSettings = { unavailable = "Profile & Settings: open the normal Dua app for account and saved preferences." },
                            onOpenDestination = { unavailable = "${it.comingSoonTitle ?: it.name} · Preview only" },
                            onOpenVoiceMode = { unavailable = "Voice: use the normal app and grant microphone permission." },
                            onPickPhotos = { unavailable = "Photo picker: open the normal Dua app for real attachments." },
                            onPickFiles = { unavailable = "Document picker: open the normal Dua app for real attachments." },
                            onPickCamera = { unavailable = "Camera: open the normal Dua app for real capture." },
                            onDeleteChat = { unavailable = "Preview data is read-only. No chat was deleted." }))
                }
            }
        }
        unavailable?.let { message -> AlertDialog(onDismissRequest = { unavailable = null }, title = { Text("UI preview") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { unavailable = null }) { Text("OK") } }) }
    }
}
