package com.dua.ai.model

import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.ReplyEvent
import com.dua.ai.network.StreamAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dua Agent tool events: generate_image ("media") and remember ("memory"). Older engines never send them. */
class AgentToolEventsTest {
    private val path = "11111111-2222-4333-8444-555555555555/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png"

    @Test fun mediaEventCarriesStoredImageAndSignedLink() {
        val event = ChatStreamParser.decode("""{"type":"media","model":"gemini-3.1-flash-lite-image","images":[{"path":"$path","mimeType":"image/png","size":10,"url":"https://proj.supabase.co/s"}]}""")
        event as ReplyEvent.Media
        assertEquals(path, event.media.single().path)
        assertEquals(mapOf(path to "https://proj.supabase.co/s"), event.links)
    }

    @Test fun badMediaIsIgnored() {
        assertNull(ChatStreamParser.decode("""{"type":"media","images":[{"path":"../etc/passwd","mimeType":"image/png"}]}"""))
        assertNull(ChatStreamParser.decode("""{"type":"media","images":[]}"""))
        assertNull(ChatStreamParser.decode("""{"type":"media","images":[{"path":"$path","mimeType":"text/html"}]}"""))
    }

    @Test fun memoryEventIsCleanedAndCapped() {
        assertEquals(ReplyEvent.Memory("Fareed likes cricket"), ChatStreamParser.decode("""{"type":"memory","fact":"  Fareed   likes cricket "}"""))
        assertNull(ChatStreamParser.decode("""{"type":"memory","fact":"   "}"""))
        assertNull(ChatStreamParser.decode("""{"type":"memory","fact":"${"x".repeat(201)}"}"""))
    }

    @Test fun accumulatorKeepsImageAndShowsMemoryNotice() {
        val acc = StreamAccumulator(0L) { 1000L }
        val media = ChatStreamParser.decode("""{"type":"media","images":[{"path":"$path","mimeType":"image/png","url":"https://proj.supabase.co/s"}]}""")!!
        acc.apply(media); acc.apply(media)
        acc.apply(ReplyEvent.Memory("Likes tea"))
        acc.apply(ReplyEvent.Text("Here is your image."))
        val snap = acc.snapshot()
        assertEquals(1, snap.media.size)
        assertEquals(ChatStreamParser.MEMORY_NOTICE, snap.notice)
        assertTrue(snap.text.startsWith("Here"))
    }
}
