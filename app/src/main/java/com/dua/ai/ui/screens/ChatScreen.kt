package com.dua.ai.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import kotlinx.coroutines.delay
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dua.ai.R
import com.dua.ai.viewmodel.ChatViewModel
import com.dua.ai.model.MessageReaction

private val Background: Color @Composable get() = MaterialTheme.colorScheme.background
private val Container: Color @Composable get() = MaterialTheme.colorScheme.surface
private val Highest: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
private val Secondary: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
private val Primary: Color @Composable get() = MaterialTheme.colorScheme.primary
private val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private enum class Screen { Home, Chat, Settings, Attachments, Voice, Profile, DataControls, Permissions, ColorMode, FontStyle, Notifications, Logout, ComputerMode, VoicePermission, VoiceDenied, Memory, Sharing, Privacy, HandsFree, CloudSync }

/** Native Compose counterpart of web/dist; no WebView or hosted page dependency. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel(), notificationRoute: Pair<String, String>? = null) {
    val preferences by viewModel.localSettings.collectAsState()
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val draft by viewModel.draft.collectAsState()
    val savedChats by viewModel.savedChats.collectAsState()
    val model by viewModel.selectedModel.collectAsState()
    var picker by rememberSaveable { mutableStateOf(false) }
    var detailHistory by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val attachments by viewModel.attachments.collectAsState()
    val readingFiles by viewModel.readingFiles.collectAsState()
    val fileError by viewModel.attachmentError.collectAsState()
    var deletingId by remember { mutableStateOf<String?>(null) }
    var attachmentNotice by rememberSaveable { mutableStateOf("") }
    val haptic = preferences.haptic
    val screen = Screen.valueOf(screenName)
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val error by viewModel.error.collectAsState()
    val failedPrompt by viewModel.failedPrompt.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val speaker = remember(context) { com.dua.ai.voice.NativeReplySpeaker(context.applicationContext) }
    val speech by speaker.state.collectAsState()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speaker, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { speaker.stop(); viewModel.foreground = false }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) viewModel.foreground = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speaker.close() }
    }
    val touchFeedback = LocalHapticFeedback.current
    fun pulse() { if (preferences.haptic) touchFeedback.performHapticFeedback(HapticFeedbackType.LongPress) }
    fun navigate(next: Screen) { if (viewModel.dataBusy.value) return; pulse(); speaker.stop(); screenName = next.name }
    fun settings() { settingsReturn = screenName; navigate(Screen.Settings) }
    fun back() {
        if (viewModel.dataBusy.value) return
        picker = false
        navigate(when (screen) {
            Screen.Settings -> Screen.valueOf(settingsReturn)
            Screen.HandsFree -> Screen.Voice
            Screen.Attachments, Screen.Voice -> Screen.Chat
            Screen.CloudSync, Screen.Profile, Screen.DataControls, Screen.Permissions, Screen.ColorMode,
            Screen.Memory, Screen.Sharing, Screen.Privacy, Screen.FontStyle, Screen.Notifications, Screen.Logout, Screen.ComputerMode, Screen.VoicePermission, Screen.VoiceDenied -> {
                val previous = detailHistory.lastOrNull() ?: Screen.Settings.name
                detailHistory = ArrayList(detailHistory.dropLast(1))
                Screen.valueOf(previous)
            }
            else -> Screen.Home
        })
    }
    fun detail(next: Screen, returnTo: Screen = Screen.Settings) {
        detailHistory = ArrayList(detailHistory + returnTo.name)
        navigate(next)
    }
    fun send() {
        pulse(); speaker.stop()
        if ((draft.isNotBlank() || attachments.isNotEmpty()) && !typing && !readingFiles) viewModel.sendMessage(draft)
    }
    BackHandler(enabled = picker || screen != Screen.Home) {
        if (picker) picker = false else back()
    }
    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(failedPrompt) {
        failedPrompt?.let { viewModel.consumeFailedPrompt() }
    }
    LaunchedEffect(notificationRoute) { notificationRoute?.second?.let { id ->
        if (savedChats.any { it.id == id }) { viewModel.openChat(id); navigate(Screen.Chat) }
    } }
    com.dua.ai.settings.LocalAppearance(preferences) {
        deletingId?.let { id ->
            AlertDialog(onDismissRequest = { deletingId = null }, title = { Text("Delete chat?") },
                text = { Text("Delete this chat from this device?") },
                confirmButton = { TextButton(onClick = { viewModel.deleteChat(id); deletingId = null }) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Cancel") } })
        }
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
                val compact = maxHeight < 440.dp
                val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().fillMaxHeight()) {
                when (screen) {
                    Screen.Home -> {
                        var searchVisible by rememberSaveable { mutableStateOf(false) }
                        var query by rememberSaveable { mutableStateOf("") }
                        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundAction(Icons.Default.Menu, "Settings menu", onClick = ::settings)
                            Spacer(Modifier.weight(1f))
                            RoundAction(Icons.Default.Search, "Search chats", onClick = { searchVisible = !searchVisible })
                            Spacer(Modifier.width(12.dp))
                            RoundAction(Icons.Default.Add, "New chat", enabled = !typing, onClick = {
                                viewModel.startNewChat(); viewModel.clearAttachments(); attachmentNotice = ""; navigate(Screen.Chat)
                            })
                        }
                        if (searchVisible) OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search chats") }, shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                        val filtered = savedChats.filter { (it.title + " " + it.preview).contains(query, ignoreCase = true) }
                        if (filtered.isNotEmpty()) {
                            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                                items(filtered, key = { it.id }) { chat ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                        .clickable { viewModel.openChat(chat.id); viewModel.clearAttachments(); navigate(Screen.Chat) }
                                        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Cube(Modifier.size(48.dp))
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(chat.title, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(chat.preview, color = Muted, fontSize = 16.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                        }
                                        RoundAction(Icons.Default.DeleteOutline, "Delete chat", onClick = { deletingId = chat.id })
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (query.isNotBlank()) "No chats found" else "No chats yet", fontSize = 20.sp)
                                Text(if (query.isNotBlank()) "Try a different search." else "Start a new chat with Dua.", color = Muted,
                                    modifier = Modifier.padding(top = 8.dp))
                                if (query.isNotBlank()) TextButton(onClick = { query = "" }) { Text("Clear search") }
                            }
                        }
                    }
                    Screen.Chat -> {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 16.dp)) {
                            RoundAction(Icons.Default.ArrowBack, "Back to home", modifier = Modifier.align(Alignment.CenterStart),
                                onClick = { navigate(Screen.Home) })
                            Text("I am Dua", fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                            RoundAction(Icons.Default.DesktopWindows, "Computer mode",
                                modifier = Modifier.align(Alignment.CenterEnd), onClick = { detail(Screen.ComputerMode, Screen.Chat) })
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (messages.isEmpty() && error == null) {
                                Image(painterResource(R.drawable.dua_logo), "DUA logo",
                                    modifier = Modifier.align(Alignment.Center).size(width = if (keyboardVisible) 80.dp else 156.dp, height = if (keyboardVisible) 140.dp else 273.dp))
                            } else {
                                LazyColumn(state = listState, contentPadding = PaddingValues(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxSize()) {
                                    items(messages, key = { it.id }) { message ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start) {
                                            Surface(color = if (message.isUser) Secondary else Container,
                                                shape = RoundedCornerShape(20.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                                                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                                                    Text(message.text, color = Ink, fontSize = 16.sp, lineHeight = 25.sp)
                                                    message.codeSnippet?.let { code ->
                                                        Text(code, color = Muted, fontSize = 13.sp,
                                                            modifier = Modifier.padding(top = 12.dp))
                                                    }
                                                    if (!message.isUser) {
                                                        FilledTonalButton(enabled = speech.available && message.state == "complete",
                                                            onClick = { speaker.toggle(message.id, message.text) },
                                                            shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                                                            Text(if (speech.activeId == message.id) "Stop reading" else "Read aloud", fontSize = 14.sp)
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(onClick = {
                                                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                                                    .setPrimaryClip(ClipData.newPlainText("Dua response", message.text + (message.codeSnippet?.let { "\n" + it } ?: "")))
                                                            }) { Icon(Icons.Default.ContentCopy, "Copy response", modifier = Modifier.size(18.dp)) }
                                                            IconButton(onClick = { viewModel.toggleReaction(message.id, true) }) {
                                                                Icon(Icons.Default.ThumbUp, "Like response", tint = if (message.reaction == MessageReaction.LIKED) Primary else Muted, modifier = Modifier.size(18.dp))
                                                            }
                                                            IconButton(onClick = { viewModel.toggleReaction(message.id, false) }) {
                                                                Icon(Icons.Default.ThumbDown, "Dislike response", tint = if (message.reaction == MessageReaction.DISLIKED) Primary else Muted, modifier = Modifier.size(18.dp))
                                                            }
                                                            IconButton(enabled = !typing, onClick = {
                                                                val index = messages.indexOfFirst { it.id == message.id }
                                                                messages.take(index.coerceAtLeast(0)).lastOrNull { it.isUser }?.let { viewModel.sendMessage(it.text) }
                                                            }) { Icon(Icons.Default.Refresh, "Retry this prompt", modifier = Modifier.size(18.dp)) }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (typing) item { Text("Dua is thinking…", color = Muted) }
                                }
                            }
                        }
                        if (speech.activeId != null || speech.status.isNotBlank()) Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(speech.status, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (speech.activeId != null) TextButton(onClick = { speaker.stop() }) { Text("Stop") }
                        }
                        error?.let { message ->
                            Text(message, color = Muted, fontSize = 14.sp, maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                        }
                        if (attachments.isNotEmpty()) Surface(color = Container, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(attachments.joinToString(", ") { it.name }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                    Text("Ready to send with this message", color = Muted, fontSize = 12.sp)
                                }
                                TextButton(enabled = !typing && !readingFiles, onClick = { viewModel.clearAttachments(); attachmentNotice = "" }) { Text("Remove") }
                            }
                        }
                        if (fileError.isNotEmpty() || readingFiles || attachmentNotice.isNotEmpty()) Text(if (readingFiles) "Reading files…" else fileError.ifBlank { attachmentNotice }, color = Muted, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp))
                        Surface(shape = RoundedCornerShape(28.dp), color = Container,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 12.dp else 16.dp)) {
                            Column(Modifier.padding(if (compact) 12.dp else 20.dp)) {
                                BasicTextField(
                                    value = draft, onValueChange = viewModel::updateDraft, enabled = !typing,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 20.sp, lineHeight = 28.sp, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily),
                                    cursorBrush = SolidColor(Primary), maxLines = if (compact) 2 else 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { send() }),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics { contentDescription = "Message" },
                                    decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Ask anything...", fontSize = 20.sp, color = Muted); inner() } }
                                )
                                Row(Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RoundAction(Icons.Default.Add, "Add to chat", color = Highest, onClick = { navigate(Screen.Attachments) })
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(onClick = { picker = true }, contentPadding = PaddingValues(horizontal = 20.dp),
                                        modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Secondary)) {
                                        Text(if (model == "Dua") "Model" else model)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    RoundAction(Icons.Default.Mic, "Open voice mode", color = Color.Transparent,
                                        onClick = { navigate(Screen.Voice) })
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = if (draft.isBlank() && attachments.isEmpty() && !typing) Secondary else Primary, shape = RoundedCornerShape(16.dp)) {
                                        IconButton(onClick = {
                                            when { typing -> viewModel.stopReply(); draft.isBlank() && attachments.isEmpty() -> navigate(Screen.Voice); else -> send() }
                                        }, modifier = Modifier.size(48.dp)) {
                                            when {
                                                typing -> Icon(Icons.Default.Stop, "Stop response", tint = MaterialTheme.colorScheme.onPrimary)
                                                draft.isBlank() && attachments.isEmpty() -> Cube(Modifier.size(34.dp).semantics { contentDescription = "Start voice mode" })
                                                else -> Icon(Icons.Default.ArrowUpward, "Send message", tint = MaterialTheme.colorScheme.onPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.Settings -> SettingsScreen(onBack = ::back, haptic = haptic, onHaptic = { viewModel.updateSettings(preferences.copy(haptic = it)) }, onOpen = { detail(it) }, preferences = preferences)
                    Screen.Attachments -> AttachmentsScreen(onBack = ::back, selected = attachments.map { it.name },
                        onSelected = viewModel::selectAttachments, onCamera = viewModel::selectCameraFile,
                        onClear = viewModel::clearAttachments, loading = readingFiles, error = fileError)
                    Screen.DataControls -> DataControlsScreen(viewModel, onBack = ::back)
                    Screen.HandsFree -> HandsFreeScreen(viewModel, onBack = ::back, onChat = { navigate(Screen.Chat) })
                    Screen.Voice -> VoiceScreen(onHandsFree = { navigate(Screen.HandsFree) }, onBack = ::back, onSettings = ::settings, draft = draft, onUse = {
                        viewModel.updateDraft(it); navigate(Screen.Chat)
                    })
                    Screen.Profile, Screen.Logout, Screen.CloudSync -> AccountScreen(viewModel, screen.name, onBack = ::back, onOpen = { detail(Screen.valueOf(it), screen) })
                    Screen.ColorMode, Screen.FontStyle, Screen.Permissions, Screen.Notifications, Screen.Memory, Screen.Sharing, Screen.Privacy -> DeviceSettingsScreen(viewModel, screen.name, onBack = ::back, onPage = { detail(Screen.valueOf(it), screen) }, onAttachments = { navigate(Screen.Attachments) })
                    else -> key(screen) { SettingsDetail(screen, onBack = ::back, onOpen = { detail(it, screen) }) }
                }
                }
            }
        }
        if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = Container) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Choose a model", fontSize = 22.sp)
                Text("Pick who responds in this chat", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                listOf("Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi").forEach { name ->
                    val available = name == "Dua" || name == "Gemini"
                    val provider = when (name) { "ChatGPT" -> "OpenAI"; "Claude" -> "Anthropic"; "Grok" -> "xAI"; "Kimi" -> "Moonshot"; else -> "Gemini" }
                    Surface(color = if (model == name) Highest else Color.Transparent, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).clickable(enabled = available) {
                            viewModel.setModel(name)
                        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = if (available) Secondary else Highest.copy(alpha = .45f), shape = RoundedCornerShape(12.dp)) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(name.take(1), color = if (available) Ink else Muted.copy(alpha = .45f)) }
                            }
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(name, color = if (available) Ink else Muted.copy(alpha = .45f))
                                Text(if (available) (if (name == "Dua") "Default · Gemini" else "Google · Gemini") else "$provider · Coming soon", color = Muted.copy(alpha = if (available) 1f else .45f), fontSize = 14.sp)
                            }
                            if (model == name) Icon(Icons.Default.CheckCircle, "Selected", tint = Primary)
                        }
                    }
                }
                Text("Dua and Gemini available\nOther models coming soon", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
                FilledTonalButton(onClick = { picker = false }, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, modifier: Modifier = Modifier,
    color: Color = Container, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(color = color, shape = CircleShape, modifier = modifier.size(48.dp)) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, label, tint = if (enabled) Muted else Muted.copy(alpha = .5f))
        }
    }
}

@Composable
private fun Cube(modifier: Modifier = Modifier) {
    val stroke = Muted
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val top = Offset(w * .5f, h * .08f); val left = Offset(w * .1f, h * .3f)
        val right = Offset(w * .9f, h * .3f); val center = Offset(w * .5f, h * .52f)
        val bottom = Offset(w * .5f, h * .94f)
        fun face(a: Offset, b: Offset, c: Offset, d: Offset, fill: Color) {
            val path = Path().apply { moveTo(a.x,a.y); lineTo(b.x,b.y); lineTo(c.x,c.y); lineTo(d.x,d.y); close() }
            drawPath(path, fill); drawPath(path, stroke, style = Stroke(width = 1.dp.toPx()))
        }
        face(top, right, center, left, Color(0xFF626066))
        face(left, center, bottom, Offset(w * .1f,h * .72f), Color(0xFF333137))
        face(center, right, Offset(w * .9f,h * .72f), bottom, Color(0xFF454349))
    }
}

@Composable
private fun PageHeader(title: String, back: () -> Unit, close: Boolean = false, info: Boolean = false) {
    Box(Modifier.fillMaxWidth().padding(16.dp)) {
        RoundAction(if (close) Icons.Default.Close else Icons.Default.ArrowBack, "Back",
            modifier = Modifier.align(Alignment.CenterStart), onClick = back)
        Text(title, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
        if (info) RoundAction(Icons.Default.Info, "Settings information coming soon", enabled = false,
            color = Color.Transparent, modifier = Modifier.align(Alignment.CenterEnd), onClick = {})
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, haptic: Boolean, onHaptic: (Boolean) -> Unit, onOpen: (Screen) -> Unit, preferences: com.dua.ai.settings.LocalPreferences) {
    PageHeader("Settings", onBack, info = true)
    val feedback = LocalHapticFeedback.current
    val destinations = mapOf("Profile" to Screen.Profile, "Computer mode" to Screen.ComputerMode,
        "Data controls" to Screen.DataControls, "Permissions" to Screen.Permissions,
        "Color mode" to Screen.ColorMode, "Font style" to Screen.FontStyle,
        "Notifications" to Screen.Notifications, "Memory" to Screen.Memory, "Privacy" to Screen.Privacy, "Sharing" to Screen.Sharing, "Voice" to Screen.Voice, "Log out" to Screen.Logout)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        listOf(listOf("Profile", "Computer mode"), listOf("Data controls", "Memory"),
            listOf("Capabilities", "Plugins", "Permissions"), listOf("Color mode", "Font style", "Voice"),
            listOf("Haptic feedback", "Notifications", "Privacy", "Sharing"), listOf("Log out")).forEach { group ->
            Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column {
                    group.forEach { title ->
                        val target = destinations[title]
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = target != null) { target?.let(onOpen) }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when (title) {
                                "Profile" -> Icons.Default.PersonOutline
                                "Computer mode" -> Icons.Default.DesktopWindows
                                "Data controls" -> Icons.Default.Storage
                                "Capabilities" -> Icons.Default.Tune
                                "Plugins" -> Icons.Default.Extension
                                "Permissions" -> Icons.Default.Security
                                "Color mode" -> Icons.Default.DarkMode
                                "Font style" -> Icons.Default.TextFields
                                "Voice" -> Icons.Default.GraphicEq
                                "Haptic feedback" -> Icons.Default.Vibration
                                "Notifications" -> Icons.Default.NotificationsNone
                                "Privacy" -> Icons.Default.Shield
                                "Sharing" -> Icons.Default.IosShare
                                else -> Icons.Default.Logout
                            }, null, tint = if (title == "Log out") Color(0xFFFFB1C0) else Muted)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(title, fontSize = 16.sp, color = if (title == "Log out") Color(0xFFFFB1C0) else Ink)
                                val subtitle = when (title) {
                                    "Color mode" -> preferences.theme.replaceFirstChar { it.uppercase() }; "Font style" -> preferences.font.replaceFirstChar { it.uppercase() }; "Profile" -> "Dua AI account"
                                    "Computer mode", "Capabilities", "Plugins" -> "Coming soon"; "Voice" -> "Speech and read aloud"; "Memory" -> if (preferences.memoryEnabled) "Enabled locally" else "Local · Off"
                                    else -> null
                                }
                                subtitle?.let { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp) }
                            }
                            if (title == "Haptic feedback") Switch(checked = haptic, onCheckedChange = {
                                onHaptic(it)
                                if (it) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            }) else Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentsScreen(onBack: () -> Unit, selected: List<String>, onSelected: (List<android.net.Uri>) -> Unit,
    onCamera: (java.io.File) -> Unit, onClear: () -> Unit, loading: Boolean, error: String) {
    val context = LocalContext.current
    var notice by rememberSaveable { mutableStateOf("") }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onSelected(uris)
    }
    val captures = remember(context) { com.dua.ai.attachments.CameraCaptureStore(context.cacheDir) }
    var pendingCapture by rememberSaveable { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val name = pendingCapture
        pendingCapture = ""
        if (name.isNotEmpty()) {
            val file = captures.find(name)
            if (captured && file.exists() && file.length() > 0) { notice = ""; onCamera(file) }
            else { captures.discard(name); notice = if (captured) "Camera did not save a photo. Try again." else "Capture cancelled. Previous selection kept." }
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Surface(color = Muted.copy(alpha = .6f), shape = RoundedCornerShape(2.dp), modifier = Modifier.size(44.dp, 4.dp)) {}
    }
    PageHeader("Add to chat", onBack, close = true)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Camera" to Icons.Default.PhotoCamera, "Photos" to Icons.Default.Photo, "Files" to Icons.Default.UploadFile).forEach { (title, icon) ->
                Surface(color = Container, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 120.dp).clickable(enabled = !loading && pendingCapture.isEmpty()) {
                        try {
                            when (title) { "Camera" -> {
                                val file = captures.create()
                                pendingCapture = file.name
                                camera.launch(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.camera", file))
                            }; "Photos" -> files.launch(arrayOf("image/jpeg", "image/png", "image/webp")); else -> files.launch(com.dua.ai.attachments.AttachmentReader.types) }
                        } catch (_: android.content.ActivityNotFoundException) {
                            if (pendingCapture.isNotEmpty()) captures.discard(pendingCapture)
                            pendingCapture = ""; notice = "No camera or file app available. Try Photos or Files."
                        } catch (_: SecurityException) {
                            if (pendingCapture.isNotEmpty()) captures.discard(pendingCapture)
                            pendingCapture = ""; notice = "Camera access is unavailable. Try Photos or Files."
                        } catch (_: java.io.IOException) {
                            if (pendingCapture.isNotEmpty()) captures.discard(pendingCapture)
                            pendingCapture = ""; notice = "Camera storage unavailable. Try Photos or Files."
                        }
                    }.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Surface(color = Highest, shape = CircleShape) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Muted) }
                        }
                        Text(title, modifier = Modifier.padding(top = 12.dp), fontSize = 16.sp)
                    }
                }
            }
        }
        Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(if (selected.isEmpty()) "No files selected" else selected.joinToString("\n"), fontSize = 14.sp)
                Text(if (loading) "Reading files…" else "3 files · 2 MB total · TXT 64 KB; sent with next message only", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (selected.isNotEmpty()) TextButton(enabled = !loading, onClick = { onClear(); notice = "" }) { Text("Clear selection") }
                Text("Photos up to 10 MiB each are resized when needed. Resized copies become JPEG, at most 2048 px on the longest side, with a white background and the first frame for animation. Originals stay unchanged.", color = Muted, fontSize = 12.sp)
                if (notice.isNotBlank() || error.isNotBlank()) Text(error.ifBlank { notice }, color = Muted, fontSize = 12.sp)
            }
        }
        listOf("Add to project", "Web search", "Connectors", "Memory").forEach { title ->
            Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (title) { "Web search" -> Icons.Default.Public; "Memory" -> Icons.Default.History; "Connectors" -> Icons.Default.AttachFile; else -> Icons.Default.Folder }, null, tint = Muted)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(title, fontSize = 16.sp, lineHeight = 24.sp)
                        Text("Coming soon", fontSize = 14.sp, lineHeight = 20.sp, color = Muted)
                    }
                    if (title == "Web search" || title == "Memory") Switch(checked = false, onCheckedChange = null, enabled = false)
                    else Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VoiceScreen(onHandsFree: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit, draft: String, onUse: (String) -> Unit) {
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    var reaction by rememberSaveable { mutableStateOf(0) }
    val voice = remember(context) { com.dua.ai.voice.NativeVoiceInput(context.applicationContext) }
    val input by voice.state.collectAsState()
    val listening = input.busy
    var reviewed by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(input.transcript, input.busy) {
        if (input.transcript.isNotBlank()) reviewed = input.transcript
        else if (input.busy) reviewed = ""
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) voice.start()
        else if (!granted) voice.permissionDenied()
    }
    DisposableEffect(voice, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) voice.stop()
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer); voice.close() }
    }
    var notice by remember { mutableStateOf("") }
    LaunchedEffect(notice) { if (notice.isNotBlank()) { delay(2400); notice = "" } }
    val greeting = "Tap the microphone and speak. Review your words, then use them in Chat."
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val short = maxHeight < 500.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth()) {
                RoundAction(Icons.Default.Menu, "Settings", color = Color.Transparent,
                    modifier = Modifier.align(Alignment.CenterStart), onClick = onSettings)
                Text("Voice", fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                Row(Modifier.align(Alignment.CenterEnd)) {
                    RoundAction(Icons.Default.VideocamOff, "Camera coming soon", color = Color.Transparent, enabled = false, onClick = {})
                    Box {
                        RoundAction(Icons.Default.MoreHoriz, "More options", color = Color.Transparent, onClick = { more = !more })
                        DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                            DropdownMenuItem(text = { Text("Back to chat") }, onClick = { more = false; onBack() })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { more = false; onSettings() })
                        }
                    }
                }
            }
            TextButton(onClick = onHandsFree) { Text("Hands-free conversation") }
            Text("Voice input · Send and hear replies in Chat", color = Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = if (short) 8.dp else 12.dp).align(Alignment.CenterHorizontally))
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                Text(notice, color = Primary, fontSize = 14.sp)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp).padding(top = if (short) 8.dp else 16.dp, bottom = 16.dp)) {
                Cube(Modifier.size(32.dp))
                Text(greeting, fontSize = if (short) 20.sp else 24.sp,
                    lineHeight = if (short) 28.sp else 34.sp, modifier = Modifier.padding(top = if (short) 12.dp else 20.dp))
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoundAction(Icons.Default.ThumbUp, "Like response", color = if (reaction == 1) Secondary else Color.Transparent, onClick = {
                        reaction = if (reaction == 1) 0 else 1; notice = if (reaction == 1) "Liked · Preview" else "Feedback cleared"
                    })
                    RoundAction(Icons.Default.ThumbDown, "Dislike response", color = if (reaction == -1) Secondary else Color.Transparent, onClick = {
                        reaction = if (reaction == -1) 0 else -1; notice = if (reaction == -1) "Disliked · Preview" else "Feedback cleared"
                    })
                    RoundAction(Icons.Default.IosShare, "Share response", color = Color.Transparent, onClick = {
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reviewed.ifBlank { greeting }) }, "Share response"))
                        } catch (_: android.content.ActivityNotFoundException) { notice = "Sharing is unavailable" }
                    })
                    RoundAction(Icons.Default.ContentCopy, "Copy response", color = Color.Transparent, onClick = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dua response", reviewed.ifBlank { greeting }))
                        notice = "Response copied"
                    })
                    RoundAction(Icons.Default.MoreHoriz, "More response options", color = Color.Transparent, onClick = { more = !more })
                }
            if (input.partial.isNotBlank()) Text(input.partial.take(8000), color = Muted, fontSize = 16.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
            if (input.transcript.isNotBlank() || reviewed.isNotBlank()) OutlinedTextField(value = reviewed,
                onValueChange = { if (it.length <= 8000) reviewed = it }, enabled = !listening,
                label = { Text("Review transcript") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
            if (reviewed.isNotBlank()) FilledTonalButton(enabled = !listening && reviewed.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                try { onUse(com.dua.ai.voice.VoiceDraft.merge(draft, reviewed)) }
                catch (error: IllegalArgumentException) { notice = error.message.orEmpty() }
            }) { Text("Use in chat") }
            if (input.permissionDenied) TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                try { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}"))) }
                catch (_: android.content.ActivityNotFoundException) { notice = "App Settings unavailable." }
            }) { Text("Open app Settings") }
            }
            HorizontalDivider(color = Highest)
            Text(input.status, color = Muted,
                fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (short) 8.dp else 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundAction(Icons.Default.VideocamOff, "Video coming soon", enabled = false, onClick = {})
                RoundAction(Icons.Default.ScreenShare, "Screen sharing coming soon", enabled = false, onClick = {})
                Surface(color = Container, shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.weight(1f).height(56.dp).border(1.dp, Color(0xFF938F99), RoundedCornerShape(30.dp))) {
                    Box(contentAlignment = Alignment.Center) { AnimatedVoiceCube(listening, Modifier.size(36.dp)) }
                }
                RoundAction(if (listening) Icons.Default.Mic else Icons.Default.MicOff,
                    if (listening) "Stop voice input" else "Start voice input", color = if (listening) Secondary else Container,
                    onClick = {
                        if (listening) voice.stop()
                        else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) voice.start()
                        else permission.launch(android.Manifest.permission.RECORD_AUDIO)
                    })
                RoundAction(Icons.Default.Close, "Exit voice mode", onClick = onBack)
            }
        }
    }
}
@Composable
private fun SettingsDetail(screen: Screen, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val title = when (screen) {
        Screen.DataControls -> "Data controls"; Screen.ColorMode -> "Color mode"; Screen.FontStyle -> "Font style"
        Screen.ComputerMode -> "Computer mode"; Screen.Logout -> "Account"
        Screen.VoicePermission, Screen.VoiceDenied -> "Microphone permission"
        else -> screen.name
    }
    PageHeader(title, onBack)
    var dialog by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Dua User") }
    var editedName by rememberSaveable { mutableStateOf(name) }
    var selection by rememberSaveable { mutableStateOf("Default") }
    var notifications by rememberSaveable { mutableStateOf(false) }
    var complete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (screen) {
            Screen.Profile -> {
                Surface(color = Container, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Secondary, shape = CircleShape) { Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), fontSize = 24.sp) } }
                        Column(Modifier.padding(start = 16.dp)) { Text(name, fontSize = 20.sp); Text("Sample account · Preview", color = Muted, fontSize = 14.sp) }
                    }
                }
                DetailRow("Edit display name", Icons.Default.Edit, onClick = { editedName = name; dialog = "name" })
                DetailNote("Account integration is pending. Editing this name changes only the preview.")
                DetailRow("Log out options", Icons.Default.Logout, onClick = { onOpen(Screen.Logout) })
            }
            Screen.Permissions -> {
                DetailRow("Microphone", Icons.Default.Mic, "Voice integration pending", onClick = { onOpen(Screen.VoicePermission) })
                DetailRow("Camera", Icons.Default.PhotoCamera, "Coming soon", onClick = { dialog = "Camera" })
                DetailRow("Photos", Icons.Default.Photo, "Attachment integration pending", onClick = { dialog = "Photos" })
                DetailRow("Notifications", Icons.Default.NotificationsNone, "Coming soon", onClick = { onOpen(Screen.Notifications) })
                DetailNote("These are design states, not your device's current permissions. This preview does not request microphone or camera permissions.")
            }
            Screen.ColorMode -> {
                Text("Choose appearance", fontSize = 22.sp)
                listOf("Dark", "Light", "System").forEach { choice ->
                    val chosen = if (selection == "Default") "Dark" else selection
                    Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { selection = choice }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(chosen == choice, onClick = { selection = choice })
                            Column { Text(choice); Text(when(choice) { "Dark" -> "Dua’s default appearance"; "Light" -> "A brighter appearance"; else -> "Follow your device’s color mode" }, fontSize = 14.sp, color = Muted) }
                        }
                    }
                }
                val light = selection == "Light" || (selection == "System" && !androidx.compose.foundation.isSystemInDarkTheme())
                Surface(color = if (light) Color(0xFFF7F2FA) else Background, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, Highest, RoundedCornerShape(20.dp))) {
                    Column(Modifier.padding(20.dp)) { Text("I am Dua", color = if (light) Background else Ink); Text("Hello! How can I help you today?", color = if (light) Secondary else Muted, modifier = Modifier.padding(top = 16.dp)) }
                }
                DetailNote("This changes only the design sample. Your app settings stay unchanged.")
            }
            Screen.FontStyle -> {
                Text("Choose a font", fontSize = 22.sp)
                listOf("Default", "System").forEach { choice ->
                    Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { selection = choice }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selection == choice, onClick = { selection = choice }); Text(choice)
                        }
                    }
                }
                Surface(color = Container, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("I am Dua", fontSize = 18.sp)
                        Text("Hi\nHello! I'm Dua. How can I help you today?\nAaj kya plan hai?\nआज मैं आपकी कैसे मदद करूँ?\n0123456789", fontSize = 16.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 16.dp),
                            fontFamily = if (selection == "System") androidx.compose.ui.text.font.FontFamily.Default else androidx.compose.ui.text.font.FontFamily.SansSerif)
                    }
                }
                DetailNote("This changes the sample only. Android's default font is already Roboto; these options may look similar.")
            }
            Screen.Notifications -> {
                Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Allow notifications"); Text("Receive updates from Dua", color = Muted, fontSize = 14.sp) }
                        Switch(notifications, { notifications = it })
                    }
                }
                DetailNote("Notifications integration is pending. This toggle demonstrates the design; it won't request permissions or send notifications.")
                Text("Permission required", fontSize = 22.sp)
                Text("Device permission will be needed when notifications are implemented.", color = Muted)
                FilledTonalButton(onClick = { dialog = "Notifications" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("View permission details") }
            }
            Screen.ComputerMode -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(color = Secondary, shape = RoundedCornerShape(28.dp)) { Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.DesktopWindows, null, tint = Primary, modifier = Modifier.size(44.dp)) } }
                    Text("Coming soon", color = Muted, modifier = Modifier.padding(top = 24.dp))
                    Text("Computer mode isn't ready yet", fontSize = 24.sp, modifier = Modifier.padding(top = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    DetailNote("You can continue chatting with Dua while this feature is being developed. This preview does not connect to or control a computer.")
                    FilledTonalButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
                }
            }
            Screen.Logout -> {
                DetailNote("Dua AI account\nSample account · Design preview")
                DetailRow("Log out", Icons.Default.Logout, onClick = { dialog = "logout" })
                DetailNote(if (complete) "Log out preview complete. No account was signed out." else "Account integration is pending. This preview won't change your login or remove chat history.")
            }
            Screen.VoicePermission, Screen.VoiceDenied -> {
                Icon(Icons.Default.MicOff, null, tint = Muted, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
                Text(if (screen == Screen.VoiceDenied) "Microphone access denied" else "Allow microphone access?", fontSize = 24.sp)
                DetailNote("Voice integration is pending. This screen demonstrates the permission design; no audio is recorded.")
                FilledTonalButton(onClick = { if (screen == Screen.VoiceDenied) onBack() else onOpen(Screen.VoiceDenied) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (screen == Screen.VoiceDenied) "Try again · Preview" else "Preview denied state") }
                TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            }
            else -> Unit
        }
        Spacer(Modifier.height(16.dp))
    }
    if (dialog.isNotEmpty()) AlertDialog(onDismissRequest = { dialog = "" }, containerColor = Container,
        title = { Text(when(dialog) { "name" -> "Edit display name"; "logout" -> "Log out of Dua?"; else -> "$dialog coming soon" }) },
        text = {
            if (dialog == "name") OutlinedTextField(value = editedName, onValueChange = { editedName = it.take(80) }, singleLine = true, label = { Text("Display name") })
            else Text(when(dialog) { "logout" -> "This is a design preview; no account will be signed out."; else -> "This feature and its permission flow will be connected when ready." })
        },
        confirmButton = { TextButton(enabled = dialog != "name" || editedName.isNotBlank(), onClick = {
            if (dialog == "name") name = editedName.trim() else if (dialog == "logout") complete = true
            dialog = ""
        }) { Text(if (dialog == "name") "Save" else if (dialog == "logout") "Log out · Preview" else "Got it") } },
        dismissButton = { if (dialog in listOf("name", "logout")) TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
}

@Composable
private fun DetailNote(text: String) {
    Text(text, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(8.dp))
}

@Composable
private fun DetailRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Muted)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(title, fontSize = 16.sp)
                subtitle?.let { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 20.sp) }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}
