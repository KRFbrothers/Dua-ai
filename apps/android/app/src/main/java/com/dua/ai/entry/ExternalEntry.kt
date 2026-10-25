package com.dua.ai.entry

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dua.ai.widget.DuaToolbarWidgetReceiver
import java.util.UUID

enum class EntryAction(val wire: String) {
    Query("query"), Camera("camera"), Photos("photos"), Files("files"), Mic("mic"), Live("live"), Screenshare("screenshare"),
    Share("share"), Remember("remember"), NewChat("new_chat"), Imagine("imagine");
    companion object { val widget = entries.filter { it != Share && it != Remember } }
}

data class ExternalEntry(val id: String = UUID.randomUUID().toString(), val action: EntryAction, val text: String = "",
    val uris: List<Uri> = emptyList(), val notice: String = "",
    /** Direct Share target: the Dua chat the user picked in the share sheet. */
    val chatId: String? = null) {
    fun bundle() = Bundle().apply { putString("id", id); putString("action", action.wire); putString("text", text)
        putStringArrayList("uris", ArrayList(uris.map(Uri::toString))); putString("notice", notice); putString("chatId", chatId) }
    companion object {
        const val IS_REMEMBER_THIS = "is_remember_this"
        const val MAX_TEXT = 32000
        fun restore(b: Bundle): ExternalEntry? {
            val id = b.getString("id")?.takeIf { it.isNotBlank() } ?: return null
            val action = EntryAction.entries.firstOrNull { it.wire == b.getString("action") } ?: return null
            return ExternalEntry(id, action, b.getString("text").orEmpty().take(MAX_TEXT), b.getStringArrayList("uris").orEmpty().take(3).map(Uri::parse), b.getString("notice").orEmpty(), b.getString("chatId"))
        }
        fun parse(intent: Intent, packageName: String): ExternalEntry? = try {
            if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                EntryAction.widget.find { it.wire == intent.getStringExtra(DuaToolbarWidgetReceiver.EXTRA_WIDGET_ACTION) }?.let { ExternalEntry(action = it) }
            } else {
                val original = (intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text)?.toString().orEmpty()
                val text = original.take(MAX_TEXT)
                val remember = intent.type?.substringBefore(';') == "text/plain" &&
                    (intent.component?.className == "$packageName.RememberThisShareTargetActivity" || intent.getBooleanExtra(IS_REMEMBER_THIS, false))
                if (remember) ExternalEntry(action = EntryAction.Remember, text = text,
                    notice = if (original.length > MAX_TEXT) "Shared text was shortened. Review the note before saving." else "")
                else {
                    val candidates = mutableListOf<Uri>()
                    if (intent.action == Intent.ACTION_SEND_MULTIPLE) candidates += IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty().take(4)
                    else IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { candidates += it }
                    intent.clipData?.let { clip -> for (i in 0 until minOf(clip.itemCount, 8)) clip.getItemAt(i).uri?.let { candidates += it } }
                    val unique = candidates.distinct()
                    val safe = unique.filter { it.scheme == "content" && it.authority?.isNotBlank() == true && it.authority?.substringAfterLast('@') != "$packageName.camera" && it.toString().length <= 2048 }
                    val notice = listOfNotNull(
                        "Shared text was shortened to $MAX_TEXT characters.".takeIf { original.length > MAX_TEXT },
                        "Only the first 3 shared files can be attached.".takeIf { unique.size > 3 || (intent.clipData?.itemCount ?: 0) > 8 },
                        "Some shared files could not be accepted. Share them from Photos or Files with read access.".takeIf { safe.size != unique.size },
                        "Nothing was shared. Choose text or files and try again.".takeIf { text.isBlank() && unique.isEmpty() }
                    ).joinToString(" ")
                    ExternalEntry(action = EntryAction.Share, text = text, uris = safe.take(3), notice = notice,
                        chatId = ChatShortcuts.chatId(intent.getStringExtra(androidx.core.content.pm.ShortcutManagerCompat.EXTRA_SHORTCUT_ID)))
                }
            }
        } catch (_: RuntimeException) { ExternalEntry(action = EntryAction.Share, notice = "This shared item could not be read. Share it again from the source app.") }

        fun mergedDraft(existing: String, shared: String): String = if (shared.isBlank()) existing else if (existing.isBlank()) shared else "$existing\n\n$shared"
    }
}

/** Survives rotation/process recreation; consumed entries never repeat a picker or memory save. */
class EntryRequests(private val savedState: SavedStateHandle) : ViewModel() {
    var pending by mutableStateOf(runCatching { savedState.get<ArrayList<Bundle>>("entries").orEmpty().mapNotNull(ExternalEntry::restore) }.getOrDefault(emptyList()))
        private set
    fun offer(entry: ExternalEntry): Boolean {
        if (pending.size >= 4) return false
        update(pending + entry)
        return true
    }
    fun consume(id: String) { update(pending.filterNot { it.id == id }) }
    private fun update(entries: List<ExternalEntry>) {
        savedState["entries"] = ArrayList(entries.map(ExternalEntry::bundle)); pending = entries
    }
}
