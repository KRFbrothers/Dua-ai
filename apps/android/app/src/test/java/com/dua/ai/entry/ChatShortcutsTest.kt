package com.dua.ai.entry

import android.content.Intent
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.widget.DuaToolbarWidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatShortcutsTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private fun chat(id: String, at: Long, text: String = "Hello $id", pinned: Boolean = false) =
        SavedChat(id, "dua", "", listOf(ChatMessage(text = text, isUser = true)), at, pinned = pinned)

    @Test fun recentKeepsFourNewestNonEmptyChatsPinnedFirst() {
        val chats = (1..6).map { chat("c$it", it.toLong()) } + chat("pin", 0, pinned = true) +
            SavedChat("empty", "dua", "draft only", emptyList(), 99)
        assertEquals(listOf("pin", "c6", "c5", "c4"), ChatShortcuts.recent(chats).map { it.chatId })
    }

    @Test fun labelsAreSingleLineAndShort() {
        val label = ChatShortcuts.recent(listOf(chat("a", 1, "Line one\n\nline two " + "x".repeat(80)))).single().label
        assertFalse(label.contains('\n')); assertTrue(label.length <= 40)
    }

    @Test fun shortcutIdsRoundTripAndForeignIdsAreIgnored() {
        assertEquals("abc", ChatShortcuts.chatId(ChatShortcuts.shortcutId("abc")))
        assertNull(ChatShortcuts.chatId("dua-project-abc")); assertNull(ChatShortcuts.chatId("dua-chat-")); assertNull(ChatShortcuts.chatId(null))
    }

    @Test fun publishAddsLauncherActionsAndDropsDeletedChats() {
        ChatShortcuts.publish(context, ChatShortcuts.recent(listOf(chat("a", 2), chat("b", 1))))
        val first = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(listOf("dua-new-chat", "dua-voice", "dua-imagine", "dua-chat-a", "dua-chat-b"), first.sortedBy { it.rank }.map { it.id })
        assertTrue(first.first { it.id == "dua-chat-a" }.categories!!.contains(ChatShortcuts.SHARE_CATEGORY))
        ChatShortcuts.publish(context, ChatShortcuts.recent(listOf(chat("b", 1))))
        assertEquals(setOf("dua-new-chat", "dua-voice", "dua-imagine", "dua-chat-b"), ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.toSet())
    }

    @Test fun launcherActionsOpenDuaEntriesWithoutSending() {
        for (action in ChatShortcuts.actions) {
            val intent = ChatShortcuts.launchIntent(context, action.action)
            assertEquals(action.action.wire, intent.getStringExtra(DuaToolbarWidgetReceiver.EXTRA_WIDGET_ACTION))
            assertEquals(action.action, ExternalEntry.parse(intent, "com.dua.ai")!!.action)
        }
    }

    @Test fun directShareCarriesTheChatAndSurvivesRecreation() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Look at this")
            .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, ChatShortcuts.shortcutId("chat-1"))
        val entry = ExternalEntry.parse(intent, "com.dua.ai")!!
        assertEquals(EntryAction.Share, entry.action); assertEquals("chat-1", entry.chatId); assertEquals("Look at this", entry.text)
        assertEquals(entry, ExternalEntry.restore(entry.bundle()))
        val plain = ExternalEntry.parse(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x"), "com.dua.ai")!!
        assertNull(plain.chatId)
    }
}
