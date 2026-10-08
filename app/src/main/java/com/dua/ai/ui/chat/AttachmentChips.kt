package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.ui.theme.DuaShape
import com.dua.ai.ui.theme.Sizes
import com.dua.ai.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Selected files above the input: image thumbnails or file icons, plus one remove-all button. */
@Composable
fun AttachmentChips(attachments: List<ChatAttachment>, status: String, removeEnabled: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (attachments.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                attachments.forEach { AttachmentChip(it) }
            }
            IconButton(onClick = onClear, enabled = removeEnabled) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove attachments", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun AttachmentChip(file: ChatAttachment) {
    val image = file.mimeType.startsWith("image/")
    val thumbnail by produceState<ImageBitmap?>(null, file.data) {
        if (image) value = withContext(Dispatchers.Default) { decodeThumbnail(file.data) }
    }
    Surface(shape = DuaShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(Spacing.xs).padding(end = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(DuaShape.codeBlock), contentAlignment = Alignment.Center) {
                val bitmap = thumbnail
                if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(40.dp))
                else Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (file.mimeType == "application/pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.Description, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Sizes.iconSmall))
                    }
                }
            }
            Column(Modifier.padding(start = Spacing.sm).widthIn(max = 140.dp)) {
                Text(file.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(typeLabel(file.mimeType), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun typeLabel(mime: String) = when (mime) {
    "application/pdf" -> "PDF"; "text/plain" -> "Text"; "image/png" -> "PNG"; "image/webp" -> "WebP"; "image/jpeg" -> "JPEG"; else -> "File"
}

private fun decodeThumbnail(data: String): ImageBitmap? = runCatching {
    val bytes = java.util.Base64.getDecoder().decode(data)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= 160 && bounds.outHeight / (sample * 2) >= 160) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
