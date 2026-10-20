package com.dua.ai.ui.features

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.files.StoredFileTypes
import com.dua.ai.imagine.*
import com.dua.ai.library.*
import com.dua.ai.ui.chat.*
import com.dua.ai.ui.theme.*
import com.dua.ai.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
fun ImagesScreen(viewModel: ChatViewModel, onBack: () -> Unit, onSetup: () -> Unit) = AssetFeatureRoute(viewModel, true, onBack, onSetup, {}, {})

@Composable
fun LibraryScreen(viewModel: ChatViewModel, onBack: () -> Unit, onSetup: () -> Unit, onChat: () -> Unit, onProject: (String) -> Unit) =
    AssetFeatureRoute(viewModel, false, onBack, onSetup, onChat, onProject)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssetFeatureRoute(vm: ChatViewModel, images: Boolean, onBack: () -> Unit, onSetup: () -> Unit, onChat: () -> Unit, onProject: (String) -> Unit) {
    val chats by vm.savedChats.collectAsState()
    val messages by vm.messages.collectAsState()
    val chatId by vm.activeChatId.collectAsState()
    val settings by vm.localSettings.collectAsState()
    val uploads by vm.uploads.collectAsState()
    val attachments by vm.attachments.collectAsState()
    val typing by vm.isTyping.collectAsState()
    val dataBusy by vm.dataBusy.collectAsState()
    val reading by vm.readingFiles.collectAsState()
    val preparing by vm.preparingFiles.collectAsState()
    val config by vm.imagineSettings.collectAsState()
    val error by vm.error.collectAsState()
    val fileError by vm.attachmentError.collectAsState()
    val account by vm.account.state.collectAsState()
    val integrations by vm.integrations.collectAsState()
    val cloud by vm.cloudLibrary.collectAsState()
    val cloudFiles = cloud.files.takeIf { cloud.ownerId == account.user?.id }.orEmpty()
    val items by produceState<List<LibraryItem>>(emptyList(), chats, messages, chatId, settings.projects, uploads, attachments, cloudFiles) {
        value = withContext(Dispatchers.Default) { LibraryIndex.build(chats, chatId, messages, settings.projects, uploads, attachments, cloudFiles) }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember(vm, items, account.user?.id) { LibraryMediaLoader(vm.mediaCache, context.cacheDir, items) }
    var draft by rememberSaveable { mutableStateOf("") }
    var chosenAspect by rememberSaveable { mutableStateOf<String?>(null) }
    val aspects = config?.imageAspects.orEmpty().filter { it in listOf("1:1", "16:9", "9:16") }.ifEmpty { listOf("1:1", "16:9", "9:16") }
    val aspect = (chosenAspect ?: settings.imagineAspect).takeIf { it in aspects } ?: aspects.first()
    var preview by remember { mutableStateOf<LibraryItem?>(null) }
    var detail by remember { mutableStateOf<LibraryItem?>(null) }
    var notice by rememberSaveable { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<GeneratedMedia?>(null) }
    val busy = typing || dataBusy || reading || preparing || exporting
    LaunchedEffect(Unit) { vm.loadProviderConfiguration(); if (images) vm.refreshCloudImagine() }
    LaunchedEffect(account.user?.id, integrations.filesEnabled) { vm.loadLibraryFiles() }
    // Account switches or deleted references immediately dismiss previews, never retain another owner's item.
    LaunchedEffect(account.user?.id) { preview = null; detail = null }
    LaunchedEffect(items) {
        if (preview != null && items.none { it.id == preview?.id }) preview = null
        if (detail != null && items.none { it.id == detail?.id }) detail = null
    }
    fun export(media: GeneratedMedia, save: Boolean) {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                val file = loader.file(media) ?: error("This image is unavailable. Reattach the original file.")
                if (save) { withContext(Dispatchers.IO) { MediaExport.saveToGallery(context, file, media) }; notice = "Image saved to Pictures/Dua." }
                else context.startActivity(MediaExport.shareIntent(context, listOf(file to media)))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notice = if (save) "Could not save the image. Check access and retry." else "Could not share the image. Check access and retry." }
            finally { exporting = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val media = pendingSave; pendingSave = null
        if (granted && media != null) export(media, true) else notice = "Storage permission is needed to save on this Android version."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !vm.dataBusy.value && !vm.isTyping.value) {
            vm.selectAttachments(uris); notice = "Files added to the current chat draft. Open that chat and send to keep them in history."
        }
    }
    fun open(item: LibraryItem) {
        if (busy) return
        if (item.media != null) { preview = item; return }
        detail = item
    }
    CompositionLocalProvider(LocalMediaLoader provides loader) {
        if (images) ImagesContent(items.filter { it.kind == LibraryKind.Images && it.media != null }, draft, aspect, aspects, busy,
            config?.imageEnabled == true && account.user != null, error.orEmpty(), listOf(notice, cloud.notice).filter { it.isNotBlank() }.joinToString("\n"), onBack,
            { draft = it.take(ImagineCodec.MAX_PROMPT) }, { chosenAspect = it },
            { vm.generateImageFromHub(draft, aspect) }, ::open, onSetup,
            generating = typing && messages.lastOrNull()?.imagine?.pending == true, onStop = vm::stopReply, onRefresh = vm::loadLibraryFiles)
        else LibraryContent(items, busy, listOf(notice, fileError, cloud.notice, if (cloud.loading) "Loading cloud files…" else "").filter { it.isNotBlank() }.joinToString("\n"), onBack,
            onUpload = { runCatching { picker.launch(AttachmentReader.types + StoredFileTypes.PICKER_EXTRA) }.onFailure { notice = "A file picker is unavailable on this device." } }, onOpen = ::open,
            onRefresh = vm::loadLibraryFiles)
        preview?.let { item -> MediaViewer(listOf(item.media!!), 0, ImagineActions(
            onShare = { it.firstOrNull()?.let { media -> export(media, false) } },
            onSave = { list -> list.firstOrNull()?.let { media -> if (MediaExport.needsPermission()) { pendingSave = media; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } else export(media, true) } }),
            false, onDismiss = { preview = null }, prompt = item.content.ifBlank { item.name }, showEditActions = false) }
    }
    detail?.let { item -> DuaPageTheme {
        ModalBottomSheet(onDismissRequest = { detail = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge)
                if (item.content.isNotBlank()) {
                    if (item.kind == LibraryKind.Code) CodeBlock(item.content, item.language)
                    else SelectionContainer { Text(item.content, style = MaterialTheme.typography.bodyLarge) }
                }
                if (item.status.isNotBlank()) Text(item.status, style = MaterialTheme.typography.bodyMedium)
                item.file?.let { ref -> TextButton(onClick = {
                    scope.launch {
                        try { val url = vm.storedFileUrl(ref); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); detail = null }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { notice = "File could not be opened. Check sign-in and connection, then retry."; detail = null }
                    }
                }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Open file") } }
                if (item.chatId != null) TextButton(onClick = {
                    if (item.chatId == vm.activeChatId.value) { detail = null; onChat() }
                    else if (vm.attachments.value.isNotEmpty() || vm.uploads.value.isNotEmpty()) { notice = "Send or remove current draft attachments before switching chats."; detail = null }
                    else { vm.openChat(item.chatId); detail = null; onChat() }
                }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Open conversation") }
                else item.projectId?.let { id -> TextButton(onClick = { detail = null; onProject(id) }, modifier = Modifier.heightIn(min = Sizes.touchTarget)) { Text("Open project") } }
            }
        }
    } }
}
