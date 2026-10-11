package com.dua.ai.imagine

import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import com.dua.ai.setup.IntegrationCapabilities
import com.dua.ai.setup.IntegrationState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ImagineCodecTest {
    private val uid = "11111111-2222-4333-8444-555555555555"
    private val png = "$uid/aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee.png"
    private val mp4 = "$uid/bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee.mp4"

    private fun serverConfig(video: Boolean = true) = JSONObject("""{"enabled":true,"requiresSignIn":true,"maxSourceBytes":15728640,
        "image":{"enabled":true,"providers":[{"id":"xai","name":"Grok Imagine","model":"grok-imagine-image-2.0"},{"id":"gemini","name":"Gemini (Nano Banana)","model":"gemini-nano-banana-2.1"}],
          "defaultProvider":"xai","fallback":true,"aspectRatios":["auto","1:1","3:4","4:3","9:16","16:9"],"maxImages":4,"maxSourceImages":3,"dailyLimit":20},
        "video":{"enabled":$video,${if (video) "" else "\"reason\":\"Video generation is turned off (DUA_IMAGINE_VIDEO=0).\","}"provider":"xai","model":"grok-imagine-video-1.5",
          "aspectRatios":["auto","1:1","3:4","4:3","9:16","16:9"],"durations":[5,8,10,15],"maxSeconds":15,"defaultSeconds":5,"resolutions":["480p","720p"],
          "defaultResolution":"720p","dailyLimit":60,"pollAfterSeconds":5}}""")

    @Test fun olderBackendsHideImagineAndNewOnesAreParsed() {
        val old = IntegrationCapabilities.configuration(JSONObject().put("providers", JSONArray()), IntegrationState())
        assertNull(old.imagine)
        val state = IntegrationCapabilities.configuration(JSONObject().put("providers", JSONArray()).put("imagine", serverConfig(video = false)), IntegrationState())
        val config = state.imagine!!
        assertTrue(config.enabled); assertTrue(config.imageEnabled); assertFalse(config.videoEnabled)
        assertEquals(listOf("xai", "gemini"), config.providers.map { it.id })
        assertEquals("Gemini (Nano Banana)", config.providerName("gemini"))
        assertEquals("Grok Imagine", config.providerName(null))
        assertTrue(config.videoReason.contains("DUA_IMAGINE_VIDEO=0"))
        assertEquals(20, config.dailyImages)
        // Unknown provider ids and junk values are ignored.
        val odd = ImagineCodec.config(JSONObject("""{"image":{"enabled":true,"providers":[{"id":"openai","name":"X"}]},"video":{"enabled":true,"durations":[99,"x"],"resolutions":["4k"]}}"""))!!
        assertFalse(odd.imageEnabled)
        assertEquals(listOf(5, 8, 10, 15), odd.durations)
        assertEquals(listOf("480p", "720p"), odd.resolutions)
    }

    @Test fun optionsAreNormalisedToWhatTheServerOffers() {
        val config = ImagineCodec.config(serverConfig())!!
        val video = config.normalize(ImagineOptions(kind = "video", aspectRatio = "21:9", duration = 7, resolution = "1080p", count = 9, provider = "openai"))
        assertEquals(ImagineOptions(kind = "video", aspectRatio = "1:1", count = 4, duration = 5, resolution = "720p", provider = null), video)
        val noVideo = ImagineCodec.config(serverConfig(video = false))!!
        assertEquals("image", noVideo.normalize(ImagineOptions(kind = "video")).kind)
    }

    @Test fun requestBodiesMatchTheBackendContract() {
        val image = ImagineCodec.imageBody("A fox", ImagineOptions(aspectRatio = "16:9", count = 2, provider = "gemini"), listOf(png))
        assertEquals(setOf("prompt", "n", "aspectRatio", "provider", "sourcePaths"), image.keys().asSequence().toSet())
        assertEquals(2, image.getInt("n")); assertEquals(png, image.getJSONArray("sourcePaths").getString(0))
        assertFalse(ImagineCodec.imageBody("x", ImagineOptions(), emptyList()).has("sourcePaths"))
        val video = ImagineCodec.videoBody("", ImagineOptions(kind = "video", aspectRatio = "auto", duration = 8, resolution = "480p"), png)
        assertEquals(setOf("duration", "resolution", "sourcePath", "aspectRatio"), video.keys().asSequence().toSet())
        assertTrue(ImagineCodec.videoBody("Waves", ImagineOptions(kind = "video"), null).has("prompt"))
    }

    @Test fun responsesAreValidated() {
        val result = ImagineCodec.imageResult(JSONObject().put("provider", "xai").put("model", "grok-imagine-image-2.0").put("edit", false)
            .put("images", JSONArray().put(JSONObject().put("path", png).put("mimeType", "image/png").put("size", 10).put("url", "https://proj.supabase.co/x?token=1"))
                .put(JSONObject().put("path", "../etc").put("mimeType", "image/png"))))
        assertEquals(listOf(GeneratedMedia(png, "image/png", 10)), result.media)
        assertEquals("https://proj.supabase.co/x?token=1", result.links[png])
        assertTrue(runCatching { ImagineCodec.imageResult(JSONObject().put("images", JSONArray())) }.isFailure)
        val job = ImagineCodec.videoJob(JSONObject().put("jobId", "eyJ2IjoxfQ.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG").put("seconds", 8).put("resolution", "480p").put("pollAfterSeconds", 5))
        assertEquals(8, job.seconds)
        assertTrue(runCatching { ImagineCodec.videoJob(JSONObject().put("jobId", "bad token!")) }.isFailure)
        assertEquals(VideoStatus("pending", 40), ImagineCodec.videoStatus(JSONObject().put("status", "pending").put("progress", 40)))
        val done = ImagineCodec.videoStatus(JSONObject().put("status", "done").put("video", JSONObject().put("path", mp4).put("mimeType", "video/mp4").put("size", JSONObject.NULL).put("duration", 8).put("url", "https://proj.supabase.co/v")))
        assertEquals(GeneratedMedia(mp4, "video/mp4", 0, 8), done.media); assertEquals("https://proj.supabase.co/v", done.link)
        val unstored = ImagineCodec.videoStatus(JSONObject("""{"status":"done","video":{"path":null,"mimeType":"video/mp4","url":"https://vidgen.x.ai/a.mp4","stored":false},"notice":"too large"}"""))
        assertEquals("https://vidgen.x.ai/a.mp4", unstored.media!!.url); assertNull(unstored.media!!.path); assertEquals("too large", unstored.notice)
        assertEquals("failed", ImagineCodec.videoStatus(JSONObject().put("status", "failed").put("error", "blocked")).status)
        assertTrue(runCatching { ImagineCodec.videoStatus(JSONObject().put("status", "weird")) }.isFailure)
        assertEquals(ImagineUsage(3, 20, 10, 60, 99), ImagineCodec.usage(JSONObject("""{"images":{"used":3,"limit":20},"videoSeconds":{"used":10,"limit":60},"resetAt":99}""")))
    }

    @Test fun pollingBacksOffAndJobsExpire() {
        assertEquals(5000, ImagineCodec.pollDelayMs(5, 0))
        assertEquals(20000, ImagineCodec.pollDelayMs(5, 2))
        assertEquals(60000, ImagineCodec.pollDelayMs(5, 9))
        val info = ImagineInfo("video", "x", status = ImagineInfo.PENDING, jobId = "j".repeat(30), startedAt = 1000)
        assertFalse(ImagineCodec.stale(info, 1000 + ImagineCodec.JOB_MAX_AGE_MS))
        assertTrue(ImagineCodec.stale(info, 1001 + ImagineCodec.JOB_MAX_AGE_MS))
        assertFalse(ImagineCodec.stale(info.copy(status = ImagineInfo.DONE), Long.MAX_VALUE))
    }

    private fun chatWithMedia(): SavedChat {
        val job = ImagineInfo("video", "Waves", "16:9", 1, "Grok Imagine", "grok-imagine-video-1.5", 5, "720p", jobId = "t".repeat(40), status = ImagineInfo.PENDING, progress = 30, startedAt = 5)
        return SavedChat("c", "Dua", "", listOf(
            ChatMessage(id = "u", text = "A fox", isUser = true, model = "Dua"),
            ChatMessage(id = "m", text = "Image created with Grok Imagine: A fox", isUser = false, model = "Dua", media = listOf(GeneratedMedia(png, "image/png", 10)),
                imagine = ImagineInfo("image", "A fox", "1:1", 1, "Grok Imagine", "grok-imagine-image-2.0")),
            ChatMessage(id = "v", text = "Creating a 5-second video: Waves", isUser = false, model = "Dua", imagine = job),
            ChatMessage(id = "w", text = "Video", isUser = false, model = "Dua", media = listOf(GeneratedMedia(null, "video/mp4", 0, 8, "https://vidgen.x.ai/a.mp4")))), 10)
    }

    @Test fun localHistoryKeepsMediaAndJobsAndOldHistoryStillLoads() {
        val chat = chatWithMedia()
        val (_, restored) = ChatHistoryCodec.decode(ChatHistoryCodec.encode(listOf(chat), "c"))
        assertEquals(listOf(chat), restored)
        // History saved by an older app (no media fields) decodes unchanged.
        val legacy = """{"activeId":"c","chats":[{"id":"c","model":"Dua","draft":"","updatedAt":1,"messages":[{"id":"a","text":"hi","isUser":false,"model":"Dua","state":"complete"}]}]}"""
        val message = ChatHistoryCodec.decode(legacy).second.single().messages.single()
        assertTrue(message.media.isEmpty()); assertNull(message.imagine); assertFalse(message.isImagine)
        // A pending job without its token cannot resume, so it shows as failed.
        val info = ImagineCodec.infoFrom(JSONObject().put("kind", "video").put("prompt", "x").put("status", "pending"))!!
        assertEquals(ImagineInfo.FAILED, info.status)
    }

    @Test fun exportsAndCloudSnapshotsCarryStoredReferencesOnly() {
        val chat = chatWithMedia()
        val export = JSONObject(ChatHistoryCodec.export(listOf(chat)))
        val messages = export.getJSONArray("chats").getJSONObject(0).getJSONArray("messages")
        assertEquals(png, messages.getJSONObject(1).getJSONArray("media").getJSONObject(0).getString("path"))
        assertTrue(messages.getJSONObject(2).has("imagine"))
        assertFalse(messages.getJSONObject(3).has("media"))
        assertFalse(export.toString().contains("vidgen.x.ai"))
        val imported = ChatHistoryCodec.parseImport(export.toString()).single()
        assertEquals(listOf(GeneratedMedia(png, "image/png", 10)), imported.messages[1].media)
        // Cloud snapshot: same references, validated on encode.
        val snapshot = CloudSnapshotCodec.encode(CloudSnapshot(listOf(chat), emptyList()))
        assertEquals(listOf(GeneratedMedia(png, "image/png", 10)), CloudSnapshotCodec.decode(snapshot).chats.single().messages[1].media)
        // Older exports without media import as before; media on user turns is invalid.
        val userMedia = JSONObject(export.toString()).apply { getJSONArray("chats").getJSONObject(0).getJSONArray("messages").getJSONObject(0).put("media", JSONArray().put(JSONObject().put("path", png).put("mimeType", "image/png"))) }
        assertThrows(IllegalArgumentException::class.java) { ChatHistoryCodec.parseImport(userMedia.toString()) }
    }

    @Test fun imaginePreferencesAreOptionalAndValidated() {
        val old = JSONObject(PreferencesCodec.encode(LocalPreferences())).apply { remove("imagineProvider"); remove("imagineAspect") }.toString()
        val decoded = PreferencesCodec.decode(old)
        assertEquals("", decoded.imagineProvider); assertEquals("1:1", decoded.imagineAspect)
        val custom = LocalPreferences(imagineProvider = "gemini", imagineAspect = "9:16")
        assertEquals(custom, PreferencesCodec.decode(PreferencesCodec.encode(custom)))
        assertEquals("1:1", PreferencesCodec.decode(JSONObject(old).put("imagineAspect", "7:1").toString()).imagineAspect)
        assertTrue(runCatching { PreferencesCodec.encode(LocalPreferences(imagineProvider = "openai")) }.isFailure)
    }

    @Test fun replyTextDescribesTheResultForHistoryAndExports() {
        val info = ImagineInfo("image", "A fox", provider = "Grok Imagine")
        assertEquals("2 images created with Grok Imagine: A fox", ImagineCodec.replyText(info, listOf(GeneratedMedia(png, "image/png"), GeneratedMedia(png, "image/png"))))
        assertEquals("Image edited with Grok Imagine: A fox", ImagineCodec.replyText(info.copy(edit = true), listOf(GeneratedMedia(png, "image/png"))))
        assertEquals("Video (8 s, 720p) created with Grok Imagine: Waves", ImagineCodec.replyText(ImagineInfo("video", "Waves", seconds = 8, resolution = "720p", provider = "Grok Imagine"), emptyList()))
        assertEquals("Video not created: Waves", ImagineCodec.replyText(ImagineInfo("video", "Waves", status = ImagineInfo.FAILED), emptyList()))
    }
}
