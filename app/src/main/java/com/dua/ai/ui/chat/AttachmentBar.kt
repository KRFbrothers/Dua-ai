package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.dua.ai.attachments.AttachmentReader
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.files.PendingUpload
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/** Draft-only previews: removal reuses the upload cancellation/storage cleanup path. */
@Composable
internal fun AttachmentBar(attachments: List<ChatAttachment>, uploads: List<PendingUpload>, enabled: Boolean,
    onRemoveAttachment: (Int) -> Unit, onRemoveUpload: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("composer-attachments").horizontalScroll(rememberScrollState())
        .padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        attachments.forEachIndexed { index, file ->
            key(index, file.name) {
                AttachmentPreview(file.name, file.mimeType.startsWith("image/"), enabled, { onRemoveAttachment(index) }) {
                    InlineAttachmentImage(file)
                }
            }
        }
        uploads.forEach { upload ->
            key(upload.id) {
                Column(Modifier.widthIn(max = ComposerSizes.fileChipMaxWidth)) {
                    val image = upload.mimeType.startsWith("image/") && upload.ref != null
                    AttachmentPreview(upload.name, image, enabled, { onRemoveUpload(upload.id) }) {
                        MediaBitmap(GeneratedMedia(upload.ref!!.path, upload.mimeType), Modifier.fillMaxSize(), maxPx = 128,
                            imageDescription = upload.name)
                    }
                    when {
                        upload.uploading -> {
                            LinearProgressIndicator(progress = { upload.progress }, modifier = Modifier.fillMaxWidth()
                                .testTag("attachment-upload-progress"), color = HomeTheme.colors.voice, trackColor = HomeTheme.colors.barBorder)
                            Text("Uploading ${upload.name}", style = MaterialTheme.typography.bodySmall, color = HomeTheme.colors.modelLabel,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        upload.error != null -> Text(upload.error, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(name: String, image: Boolean, enabled: Boolean, onRemove: () -> Unit, thumbnail: @Composable () -> Unit) {
    val c = HomeTheme.colors
    Box(Modifier.then(if (image) Modifier.size(ComposerSizes.attachment) else Modifier.widthIn(max = ComposerSizes.fileChipMaxWidth)
        .heightIn(min = ComposerSizes.attachment)).clip(ComposerSizes.attachmentShape)
        .background(c.segmentSelected).border(HomeSizes.barBorder, c.barBorder, ComposerSizes.attachmentShape)) {
        if (image) thumbnail() else Row(Modifier.padding(start = Spacing.sm, end = Sizes.touchTarget)
            .heightIn(min = ComposerSizes.attachment), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, null, tint = c.barIcon, modifier = Modifier.size(Sizes.iconSmall))
            Text(name, style = MaterialTheme.typography.bodySmall, color = c.input,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = Spacing.xs))
        }
        IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(Sizes.touchTarget).align(Alignment.TopEnd)) {
            Icon(Icons.Outlined.Close, "Remove attachment $name", tint = c.barIcon,
                modifier = Modifier.clip(HomeShapes.round).background(c.bar).padding(Spacing.xs).size(Sizes.iconTiny))
        }
    }
}

@Composable
private fun InlineAttachmentImage(file: ChatAttachment) {
    val image by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                require(file.data.length <= (AttachmentReader.MAX_BYTES + 2) / 3 * 4)
                val bytes = Base64.getDecoder().decode(file.data)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 256) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = image
    if (bitmap != null) Image(bitmap, file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Icon(Icons.Outlined.Description, file.name, Modifier.fillMaxSize().padding(Spacing.md), tint = HomeTheme.colors.barIcon)
}
