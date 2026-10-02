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

private val Background = Color(0xFF141218)
private val Container = Color(0xFF211F26)
private val Highest = Color(0xFF36343B)
private val Secondary = Color(0xFF4A4458)
private val Primary = Color(0xFFD0BCFF)
private val Ink = Color(0xFFE6E0E9)
private val Muted = Color(0xFFCAC4D0)
private enum class Screen { Home, Chat, Settings, Attachments, Voice }

/** Native Compose counterpart of web/dist; no WebView or hosted page dependency. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.Home.name) }
    var draft by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("Dua") }
    var picker by rememberSaveable { mutableStateOf(false) }
    val screen = Screen.valueOf(screenName)
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.isTyping.collectAsState()
    val error by viewModel.error.collectAsState()
    val failedPrompt by viewModel.failedPrompt.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    fun navigate(next: Screen) { screenName = next.name }
    fun settings() { settingsReturn = screenName; navigate(Screen.Settings) }
    fun back() {
        picker = false
        navigate(when (screen) {
            Screen.Settings -> Screen.valueOf(settingsReturn)
            Screen.Attachments, Screen.Voice -> Screen.Chat
            else -> Screen.Home
        })
    }
    fun send() {
        if (draft.isNotBlank() && !typing) {
            viewModel.sendMessage(draft)
            if (draft.length <= 8000) draft = ""
        }
    }
    BackHandler(enabled = picker || screen != Screen.Home) {
        if (picker) picker = false else back()
    }
    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LaunchedEffect(failedPrompt) {
        failedPrompt?.let { draft = it; viewModel.consumeFailedPrompt() }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Primary, onPrimary = Color(0xFF381E72),
        secondaryContainer = Secondary, onSecondaryContainer = Color(0xFFE8DEF8),
        background = Background, surface = Container, onSurface = Ink,
        onBackground = Ink, onSurfaceVariant = Muted, outline = Color(0xFF938F99)
    )) {
        Surface(color = Background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
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
                                viewModel.startNewChat(); draft = ""; navigate(Screen.Chat)
                            })
                        }
                        if (searchVisible) OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Search chats") }, shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        )
                        if ("I am Dua".contains(query, ignoreCase = true)) {
                            Row(Modifier.fillMaxWidth().padding(20.dp).clickable { navigate(Screen.Chat) }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Cube(Modifier.size(48.dp))
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("I am Dua", fontSize = 20.sp)
                                    Text("Ask anything...", color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                                }
                                Text("Yesterday", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                            }
                        }
                    }
                    Screen.Chat -> {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                            RoundAction(Icons.Default.ArrowBack, "Back to home", modifier = Modifier.align(Alignment.CenterStart),
                                onClick = { navigate(Screen.Home) })
                            Text("I am Dua", fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                            RoundAction(Icons.Default.DesktopWindows, "Computer mode coming soon", enabled = false,
                                modifier = Modifier.align(Alignment.CenterEnd), onClick = {})
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (messages.isEmpty() && error == null) {
                                Image(painterResource(R.drawable.dua_logo), "DUA logo",
                                    modifier = Modifier.align(Alignment.Center).size(width = 156.dp, height = 273.dp))
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
                        error?.let { message ->
                            Text(message, color = Muted, fontSize = 14.sp, maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                        }
                        Surface(shape = RoundedCornerShape(28.dp), color = Container,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(16.dp)) {
                            Column(Modifier.padding(20.dp)) {
                                BasicTextField(
                                    value = draft, onValueChange = { draft = it }, enabled = !typing,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontSize = 20.sp, lineHeight = 28.sp),
                                    cursorBrush = SolidColor(Primary), maxLines = 4,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { send() }),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).semantics { contentDescription = "Message" },
                                    decorationBox = { inner -> Box { if (draft.isEmpty()) Text("Ask anything...", fontSize = 20.sp, color = Muted); inner() } }
                                )
                                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RoundAction(Icons.Default.Add, "Add to chat", color = Highest, onClick = { navigate(Screen.Attachments) })
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalButton(onClick = { picker = true }, contentPadding = PaddingValues(horizontal = 20.dp),
                                        modifier = Modifier.height(40.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Secondary)) {
                                        Text(if (model == "Dua") "Model" else model)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    RoundAction(Icons.Default.Mic, "Open voice mode", color = Color.Transparent,
                                        onClick = { navigate(Screen.Voice) })
                                    Spacer(Modifier.width(8.dp))
                                    Surface(color = if (draft.isBlank()) Secondary else Primary, shape = RoundedCornerShape(16.dp)) {
                                        IconButton(onClick = { if (draft.isBlank()) navigate(Screen.Voice) else send() },
                                            enabled = !typing, modifier = Modifier.size(48.dp).semantics { contentDescription = if (draft.isBlank()) "Start voice mode" else "Send message" }) {
                                            if (draft.isBlank()) Cube(Modifier.size(34.dp))
                                            else Icon(Icons.Default.ArrowUpward, "Send message", tint = Color(0xFF381E72))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.Settings -> SettingsScreen(onBack = ::back)
                    Screen.Attachments -> AttachmentsScreen(onBack = ::back)
                    Screen.Voice -> VoiceScreen(onBack = ::back, onSettings = ::settings)
                }
            }
        }
        if (picker) ModalBottomSheet(onDismissRequest = { picker = false }, containerColor = Container) {
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                Text("Choose a model", fontSize = 22.sp)
                Text("Pick who responds in this chat", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                listOf("Dua", "GPT", "Gemini", "Claude", "Grok", "Kimi").forEach { name ->
                    val available = name == "Dua" || name == "Gemini"
                    Row(Modifier.fillMaxWidth().clickable(enabled = available) {
                        model = name; viewModel.setModel(name); picker = false
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = model == name, enabled = available, onClick = {
                            model = name; viewModel.setModel(name); picker = false
                        })
                        Column {
                            Text(name, color = if (available) Ink else Muted.copy(alpha = .5f))
                            Text(if (available) "Gemini" else "Coming soon", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
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
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val top = Offset(w * .5f, h * .08f); val left = Offset(w * .1f, h * .3f)
        val right = Offset(w * .9f, h * .3f); val center = Offset(w * .5f, h * .52f)
        val bottom = Offset(w * .5f, h * .94f)
        fun face(a: Offset, b: Offset, c: Offset, d: Offset, fill: Color) {
            val path = Path().apply { moveTo(a.x,a.y); lineTo(b.x,b.y); lineTo(c.x,c.y); lineTo(d.x,d.y); close() }
            drawPath(path, fill); drawPath(path, Muted, style = Stroke(width = 1.dp.toPx()))
        }
        face(top, right, center, left, Color(0xFF626066))
        face(left, center, bottom, Offset(w * .1f,h * .72f), Color(0xFF333137))
        face(center, right, Offset(w * .9f,h * .72f), bottom, Color(0xFF454349))
    }
}

@Composable
private fun PageHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundAction(Icons.Default.ArrowBack, "Back", color = Color.Transparent, onClick = back)
        Text(title, fontSize = 22.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    PageHeader("Settings", onBack)
    var haptic by rememberSaveable { mutableStateOf(false) }
    val feedback = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        listOf(
            listOf("Profile", "Computer mode"),
            listOf("Data controls"),
            listOf("Capabilities", "Plugins", "Permissions"),
            listOf("Color mode", "Font style", "Voice"),
            listOf("Haptic feedback", "Notifications", "Privacy", "Sharing"),
            listOf("Log out")
        ).forEach { group ->
            Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column {
                    group.forEachIndexed { index, title ->
                        if (index > 0) HorizontalDivider(color = Highest)
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(when(title) {
                                "Profile" -> Icons.Default.Person
                                "Computer mode" -> Icons.Default.DesktopWindows
                                "Voice" -> Icons.Default.Mic
                                "Privacy" -> Icons.Default.Lock
                                "Notifications" -> Icons.Default.Notifications
                                "Log out" -> Icons.Default.Logout
                                else -> Icons.Default.Settings
                            }, null, tint = Muted)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(title, fontSize = 16.sp)
                                if (title != "Haptic feedback") Text(when(title) {
                                    "Color mode" -> "Dark"
                                    "Font style" -> "Default"
                                    "Profile" -> "Dua AI account"
                                    else -> "Coming soon"
                                }, color = Muted, fontSize = 12.sp)
                            }
                            if (title == "Haptic feedback") Switch(checked = haptic, onCheckedChange = {
                                haptic = it
                                if (it) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            })
                            else Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentsScreen(onBack: () -> Unit) {
    var selection by rememberSaveable { mutableStateOf("") }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selection = if (uris.isEmpty()) "" else "${uris.size} file(s) selected"
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        selection = if (bitmap == null) "" else "Camera image selected"
    }
    PageHeader("Add to chat", onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Camera" to Icons.Default.PhotoCamera, "Photos" to Icons.Default.Photo, "Files" to Icons.Default.UploadFile).forEach { (title, icon) ->
                Surface(color = Container, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                    Column(Modifier.clickable {
                        try {
                            when(title) {
                                "Camera" -> camera.launch(null)
                                "Photos" -> files.launch(arrayOf("image/*"))
                                else -> files.launch(arrayOf("*/*"))
                            }
                        } catch (_: android.content.ActivityNotFoundException) {
                            selection = "No app available for this action"
                        }
                    }.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, title, tint = Muted)
                        Text(title, modifier = Modifier.padding(top = 12.dp), fontSize = 16.sp)
                    }
                }
            }
        }
        Text(if (selection.isEmpty()) "Files are selected locally; sending attachments is coming soon" else selection,
            color = Muted, fontSize = 12.sp)
        var webSearch by rememberSaveable { mutableStateOf(true) }
        var memory by rememberSaveable { mutableStateOf(true) }
        listOf("Add to project", "Web search", "Connectors", "Memory").forEach { title ->
            Surface(color = Container, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when(title) { "Web search" -> Icons.Default.Public; "Memory" -> Icons.Default.History; "Connectors" -> Icons.Default.AttachFile; else -> Icons.Default.Folder }, null)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(title, fontSize = 18.sp)
                        Text(if (title == "Add to project") "None" else "Coming soon", fontSize = 12.sp, color = Muted)
                    }
                    when(title) {
                        "Web search" -> Switch(webSearch, { webSearch = it })
                        "Memory" -> Switch(memory, { memory = it })
                        else -> Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    var reaction by rememberSaveable { mutableStateOf(0) }
    val greeting = "Hello! Aur bataye, aaj kya plan hai aur kya madad chahiye aapko?"
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundAction(Icons.Default.Menu, "Settings", color = Color.Transparent, onClick = onSettings)
        Spacer(Modifier.weight(1f))
        RoundAction(Icons.Default.VideocamOff, "Camera coming soon", enabled = false, onClick = {})
        Box {
            RoundAction(Icons.Default.MoreHoriz, "More options", color = Color.Transparent, onClick = { more = true })
            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                DropdownMenuItem(text = { Text("Back to chat") }, onClick = { more = false; onBack() })
                DropdownMenuItem(text = { Text("Settings") }, onClick = { more = false; onSettings() })
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Cube(Modifier.size(28.dp))
        Text(greeting,
            fontSize = 26.sp, lineHeight = 33.sp, modifier = Modifier.padding(top = 12.dp))
        Row(Modifier.padding(top = 12.dp)) {
            IconButton(onClick = { reaction = if (reaction == 1) 0 else 1 }) {
                Icon(Icons.Default.ThumbUp, "Like response", tint = if (reaction == 1) Primary else Muted)
            }
            IconButton(onClick = { reaction = if (reaction == -1) 0 else -1 }) {
                Icon(Icons.Default.ThumbDown, "Dislike response", tint = if (reaction == -1) Primary else Muted)
            }
            IconButton(onClick = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, greeting)
                }, "Share response"))
            }) { Icon(Icons.Default.IosShare, "Share response") }
            IconButton(onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Dua response", greeting))
            }) { Icon(Icons.Default.ContentCopy, "Copy response") }
            IconButton(onClick = { more = true }) { Icon(Icons.Default.MoreHoriz, "More response options") }
        }
        Spacer(Modifier.weight(1f))
        Text("Voice conversation coming soon", color = Muted, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Default.VideocamOff, "Video coming soon", enabled = false, onClick = {})
            RoundAction(Icons.Default.ScreenShare, "Screen sharing coming soon", enabled = false, onClick = {})
            Surface(color = Container, shape = RoundedCornerShape(30.dp), modifier = Modifier.size(80.dp, 58.dp)) {}
            RoundAction(Icons.Default.MicOff, "Microphone coming soon", enabled = false, onClick = {})
            RoundAction(Icons.Default.Close, "Exit voice mode", onClick = onBack)
        }
    }
}
