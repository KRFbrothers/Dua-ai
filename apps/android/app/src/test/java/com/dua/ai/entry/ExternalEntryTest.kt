package com.dua.ai.entry

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.dua.ai.widget.DuaToolbarWidgetReceiver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
class ExternalEntryTest {
    private val pkg = "com.dua.ai"
    @Test fun allWidgetActionsAreRecognizedAndUnknownExtrasAreIgnored() {
        for (action in EntryAction.widget) assertEquals(action, ExternalEntry.parse(Intent().putExtra(DuaToolbarWidgetReceiver.EXTRA_WIDGET_ACTION, action.wire), pkg)!!.action)
        assertNull(ExternalEntry.parse(Intent().putExtra(DuaToolbarWidgetReceiver.EXTRA_WIDGET_ACTION, "bogus"), pkg))
        assertNull(ExternalEntry.parse(Intent(Intent.ACTION_MAIN), pkg))
    }
    @Test fun sharesMergeDraftsAndCollectDeduplicatedStreamsAndClipDataWithoutReadingThem() {
        val a = Uri.parse("content://source/a"); val b = Uri.parse("content://source/b")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png").putExtra(Intent.EXTRA_TEXT, "Shared note")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        intent.clipData = ClipData.newRawUri("photo", a)
        val result = ExternalEntry.parse(intent, pkg)!!
        assertEquals("Shared note", result.text); assertEquals(listOf(a, b), result.uris)
        assertEquals("Existing draft\n\nShared note", ExternalEntry.mergedDraft("Existing draft", result.text))
        assertEquals("Existing", ExternalEntry.mergedDraft("Existing", ""))
        assertEquals("Shared note", ExternalEntry.mergedDraft("", result.text))
        val single = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, a)
        assertEquals(listOf(a), ExternalEntry.parse(single, pkg)!!.uris)
    }
    @Test fun textOnlyClipDataIsAcceptedWithoutOpeningAContentProvider() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
        intent.clipData = ClipData.newPlainText("note", "A shared note")
        assertEquals("A shared note", ExternalEntry.parse(intent, pkg)!!.text)
    }
    @Test fun intakeBoundsTextFilesAndRejectsPrivatePathsAndOurOwnFileProvider() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_TEXT, "x".repeat(ExternalEntry.MAX_TEXT + 10))
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(Uri.parse("file:///data/private"), Uri.parse("content://com.dua.ai.camera/private"), Uri.parse("https://example.com/file"), Uri.parse("content://source/a")))
        val result = ExternalEntry.parse(intent, pkg)!!
        assertEquals(ExternalEntry.MAX_TEXT, result.text.length); assertEquals(listOf(Uri.parse("content://source/a")), result.uris)
        assertTrue(result.notice.contains("could not be accepted")); assertTrue(result.notice.contains("shortened"))
        val ownUserUri = Intent(Intent.ACTION_SEND).setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://0@com.dua.ai.camera/private"))
        assertTrue(ExternalEntry.parse(ownUserUri, pkg)!!.uris.isEmpty())
    }
    @Test fun rememberAliasProducesConfirmationRequestInsteadOfChatOrAutomaticSave() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setComponent(ComponentName(pkg, "$pkg.RememberThisShareTargetActivity")).putExtra(Intent.EXTRA_TEXT, "I prefer Hindi")
        val result = ExternalEntry.parse(intent, pkg)!!
        assertEquals(EntryAction.Remember, result.action); assertEquals("I prefer Hindi", result.text); assertTrue(result.uris.isEmpty())
        assertEquals(EntryAction.Remember, ExternalEntry.parse(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(ExternalEntry.IS_REMEMBER_THIS, true), pkg)!!.action)
    }
    @Test fun savedQueueSurvivesRecreationAndConsumedRequestsDoNotReturn() {
        val saved = SavedStateHandle(); val queue = EntryRequests(saved)
        val a = ExternalEntry(action = EntryAction.Camera); val b = ExternalEntry(action = EntryAction.Share, text = "Hello", uris = listOf(Uri.parse("content://source/file")))
        queue.offer(a); queue.offer(b); queue.consume(a.id)
        val recreated = EntryRequests(saved)
        assertEquals(listOf(b), recreated.pending)
        recreated.consume(b.id); assertTrue(EntryRequests(saved).pending.isEmpty())
        repeat(4) { assertTrue(recreated.offer(ExternalEntry(action = EntryAction.Query))) }
        val original = recreated.pending.toList()
        assertFalse(recreated.offer(a)); assertEquals(original, recreated.pending)
    }
    @Test fun restoreSkipsBadBundlesWithoutDroppingGoodSavedEntries() {
        assertNull(ExternalEntry.restore(android.os.Bundle().apply { putString("action", EntryAction.Query.wire) }))
        assertNull(ExternalEntry.restore(ExternalEntry(action = EntryAction.Query).bundle().apply { putString("action", "bogus") }))
        val good = ExternalEntry(action = EntryAction.Camera)
        val bad = android.os.Bundle().apply { putString("action", "bogus") }
        val saved = SavedStateHandle().apply { set("entries", arrayListOf(good.bundle(), bad)) }
        assertEquals(listOf(good), EntryRequests(saved).pending)
    }
}
