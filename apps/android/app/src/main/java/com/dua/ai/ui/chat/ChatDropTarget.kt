/*
 * Portions adapted from Androidify's DropBehaviourFactory.kt and PhotoPrompt.kt
 * (https://github.com/android/androidify), Copyright 2025 The Android Open Source Project,
 * licensed under the Apache License, Version 2.0. See THIRD_PARTY_NOTICES.md.
 * Changes: drops files and text onto the whole chat, copies through DroppedFileStore,
 * rejects Dua's own provider URIs and shows a Dua-styled hover overlay.
 */
package com.dua.ai.ui.chat

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import com.dua.ai.attachments.DropItem
import com.dua.ai.attachments.DropResult
import com.dua.ai.attachments.DroppedFileStore
import com.dua.ai.ui.theme.ChatSizes
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaMotion
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Files copied from a drop (as Dua's own content URIs), dropped text, and a notice to show. */
data class ChatDrop(val uris: List<Uri> = emptyList(), val text: String = "", val notice: String = "")

/** Default size cap for a dropped file when the backend does not report a stored-file limit. */
internal const val DEFAULT_DROP_MAX_BYTES = 50L * 1024 * 1024

/**
 * Lets photos, files and text be dragged into the chat from another app (split screen, tablets,
 * foldables, desktop mode). [onHover] reports whether a drag is over the chat. Disabled when [enabled] is false.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.chatDropTarget(enabled: Boolean, maxBytes: Long, onHover: (Boolean) -> Unit, onDrop: (ChatDrop) -> Unit): Modifier {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDrop by rememberUpdatedState(onDrop)
    val currentHover by rememberUpdatedState(onHover)
    val limit by rememberUpdatedState(maxBytes.takeIf { it > 0 } ?: DEFAULT_DROP_MAX_BYTES)
    val target = remember(context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { currentHover(true) }
            override fun onExited(event: DragAndDropEvent) { currentHover(false) }
            override fun onEnded(event: DragAndDropEvent) { currentHover(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentHover(false)
                val dragEvent = event.toAndroidDragEvent()
                val items = clipItems(dragEvent.clipData, context.packageName)
                if (items.isEmpty()) { currentDrop(ChatDrop(notice = "Nothing to attach here. Drop photos or files.")); return false }
                // Read access to dropped files lasts only while this permission is held.
                val permission = if (items.any { it.uri != null }) context.findActivity()?.let { runCatching { it.requestDragAndDropPermissions(dragEvent) }.getOrNull() } else null
                val appContext = context.applicationContext
                scope.launch {
                    val result = try {
                        withContext(Dispatchers.IO) { receiveDrop(appContext, items, limit) }
                    } finally { permission?.release() }
                    currentDrop(result.toChatDrop(appContext))
                }
                return true
            }
        }
    }
    return if (!enabled) this else dragAndDropTarget(shouldStartDragAndDrop = { it.mimeTypes().isNotEmpty() }, target = target)
}

/** Overlay shown while something is dragged over the chat. */
@Composable
internal fun BoxScope.ChatDropOverlay(visible: Boolean, motion: Boolean) {
    AnimatedVisibility(visible, Modifier.matchParentSize(),
        enter = if (motion) fadeIn(tween(DuaMotion.DurationFast)) else EnterTransition.None,
        exit = if (motion) fadeOut(tween(DuaMotion.DurationFast)) else ExitTransition.None) {
        val c = ChatTheme.colors
        Box(Modifier.fillMaxSize().background(c.background.copy(alpha = .86f)).padding(Spacing.lg)
            .border(BorderStroke(ChatSizes.border * 2, c.accent), DuaShape.card).testTag("chat-drop-overlay"),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = c.accent, modifier = Modifier.size(Sizes.touchTarget))
                Text("Drop to attach", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text("Photos and files go to the composer. Text goes into your message.",
                    style = MaterialTheme.typography.bodySmall, color = c.mutedText)
            }
        }
    }
}

/**
 * Items of a clip that Dua may read: `content://` URIs from other apps (never Dua's own provider, the
 * same rule as Share), and plain text. Anything else is ignored.
 */
internal fun clipItems(clip: ClipData?, packageName: String): List<DropItem> {
    if (clip == null) return emptyList()
    return (0 until clip.itemCount).mapNotNull { index ->
        val item = clip.getItemAt(index)
        val uri = item.uri
        when {
            uri != null -> uri.takeIf { droppable(it, packageName) }?.let { DropItem(uri = it) }
            !item.text.isNullOrBlank() -> DropItem(text = item.text.toString())
            else -> null
        }
    }
}

internal fun droppable(uri: Uri, packageName: String): Boolean {
    val authority = uri.authority?.substringAfterLast('@').orEmpty()
    return uri.scheme == "content" && authority.isNotBlank() && !authority.startsWith("$packageName.") && authority != packageName &&
        uri.toString().length <= 2048
}

private fun receiveDrop(context: Context, items: List<DropItem>, maxBytes: Long): DropResult {
    val resolver = context.contentResolver
    return DroppedFileStore(context.cacheDir).receive(items, maxBytes,
        nameOf = { uri -> resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } },
        typeOf = { uri -> resolver.getType(uri) },
        open = { uri -> resolver.openInputStream(uri) })
}

internal fun DropResult.toChatDrop(context: Context): ChatDrop =
    ChatDrop(files.map { FileProvider.getUriForFile(context, "${context.packageName}.camera", it) }, text, notice)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
