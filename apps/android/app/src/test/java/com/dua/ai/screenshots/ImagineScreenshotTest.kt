package com.dua.ai.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dua.ai.files.FileRef
import com.dua.ai.files.PendingUpload
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.ImagineCodec
import com.dua.ai.imagine.ImagineInfo
import com.dua.ai.imagine.ImagineOptions
import com.dua.ai.imagine.ImagineUsage
import com.dua.ai.imagine.MediaLoader
import com.dua.ai.model.ChatMessage
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.chat.ChatActions
import com.dua.ai.ui.chat.ChatScreen
import com.dua.ai.ui.chat.ChatSheet
import com.dua.ai.ui.chat.ChatUiState
import com.dua.ai.ui.chat.ImagineActions
import com.dua.ai.ui.chat.LocalMediaLoader
import com.dua.ai.ui.chat.MediaViewerContent
import com.dua.ai.ui.settings.ImagineSettingsContent
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 3 Imagine surfaces. Record with ./gradlew :app:recordRoborazziDebug -Pdua.screenshots */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ImagineScreenshotTest(private val dark: Boolean) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun modes() = listOf(arrayOf<Any>(true), arrayOf<Any>(false))
    }

    @get:Rule val compose = createComposeRule()
    private val mode get() = if (dark) "dark" else "light"
    private fun path(name: String) = "build/outputs/roborazzi/${name}_$mode.png"

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalMediaLoader provides ImagineData.loader) {
            DuaTheme(LocalPreferences(theme = mode)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(path(name))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun screen(name: String, content: @Composable () -> Unit) {
        compose.setContent { Themed(content) }
        compose.waitForIdle()
        captureScreenRoboImage(path(name))
    }

    @Test fun imagineComposer() = shot("19_imagine_composer") {
        ChatScreen(ImagineData.composer, ChatActions())
    }

    @Test fun imagineImageOptions() = screen("20_imagine_options_image") {
        ChatScreen(ImagineData.composer, ChatActions(), initialSheet = ChatSheet.Imagine)
    }

    @Test fun imagineVideoOptions() = screen("21_imagine_options_video") {
        ChatScreen(ImagineData.composer.copy(draft = "", imagineOptions = ImagineOptions(kind = "video", aspectRatio = "auto", duration = 8, resolution = "720p"),
            uploads = ImagineData.sourceUpload), ChatActions(), initialSheet = ChatSheet.Imagine)
    }

    @Test fun imageGrid() = shot("22_image_grid") {
        ChatScreen(ImagineData.images, ChatActions())
    }

    @Test fun fullscreenViewer() = shot("23_image_viewer") {
        MediaViewerContent(ImagineData.four, startIndex = 1, actions = ImagineActions(), videoAvailable = true, onDismiss = {})
    }

    @Test fun videoInProgress() = shot("24_video_progress") {
        ChatScreen(ImagineData.videoPending, ChatActions())
    }

    @Test fun videoPlayerCard() = shot("25_video_card") {
        ChatScreen(ImagineData.videoDone, ChatActions())
    }

    @Test fun imagineSettings() = shot("26_imagine_settings") {
        ImagineSettingsContent(ImagineData.config, ImagineUsage(7, 20, 18, 60, 1791590400), signedIn = true, provider = "", aspect = "1:1", status = "",
            onBack = {}, onProvider = {}, onAspect = {})
    }
}

private object ImagineData {
    private const val UID = "11111111-2222-4333-8444-555555555555"
    private fun path(n: Int, ext: String) = "$UID/${"%08x".format(n)}-bbbb-4ccc-8ddd-eeeeeeeeeeee.$ext"

    val config = ImagineCodec.config(JSONObject("""{"enabled":true,"maxSourceBytes":15728640,
        "image":{"enabled":true,"providers":[{"id":"xai","name":"Grok Imagine","model":"grok-imagine-image-2.0"},{"id":"gemini","name":"Gemini (Nano Banana)","model":"gemini-nano-banana-2.1"}],
          "defaultProvider":"xai","aspectRatios":["auto","1:1","3:4","4:3","9:16","16:9"],"maxImages":4,"maxSourceImages":3,"dailyLimit":20},
        "video":{"enabled":true,"model":"grok-imagine-video-1.5","aspectRatios":["auto","1:1","3:4","4:3","9:16","16:9"],"durations":[5,8,10,15],"defaultSeconds":5,
          "resolutions":["480p","720p"],"defaultResolution":"720p","dailyLimit":60,"pollAfterSeconds":5}}"""))!!

    private val palettes = listOf(
        intArrayOf(0xFF1D2671.toInt(), 0xFFC33764.toInt()), intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        intArrayOf(0xFFF7971E.toInt(), 0xFFFFD200.toInt()), intArrayOf(0xFF134E5E.toInt(), 0xFF71B280.toInt()), intArrayOf(0xFF41295A.toInt(), 0xFF2F0743.toInt()))

    /** Deterministic stand-ins for generated pictures: gradient sky, sun and hills. */
    private fun art(seed: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = palettes[seed % palettes.size]
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP) })
        canvas.drawCircle(width * (.3f + .1f * (seed % 4)), height * .38f, minOf(width, height) * .14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF3C4.toInt(); alpha = 230 })
        val hill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1D26.toInt(); alpha = 200 }
        canvas.drawCircle(width * .2f, height * 1.05f, width * .55f, hill)
        canvas.drawCircle(width * .85f, height * 1.1f, width * .5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF08141B.toInt(); alpha = 230 })
        return bitmap
    }

    val loader = object : MediaLoader {
        private val cache = mutableMapOf<String, Bitmap>()
        private fun bitmap(media: GeneratedMedia): Bitmap = cache.getOrPut(media.key) {
            val seed = media.key.substringAfter('/').take(8).toIntOrNull(16) ?: 0
            if (media.isVideo) art(seed, 640, 360) else if (seed == 9) art(seed, 480, 640) else art(seed, 512, 512)
        }
        override fun cached(media: GeneratedMedia, maxPx: Int) = bitmap(media)
        override fun cachedPoster(media: GeneratedMedia, maxPx: Int) = bitmap(media)
        override suspend fun image(media: GeneratedMedia, maxPx: Int) = bitmap(media)
        override suspend fun poster(media: GeneratedMedia, maxPx: Int) = bitmap(media)
    }

    private val base = ChatUiState(greeting = "Good evening, Fareed", webSearchConfigured = true, pageReadingAvailable = true, storedFiles = true,
        imagineConfig = config, imagineUsage = ImagineUsage(7, 20, 18, 60, 1791590400))

    val composer = base.copy(imagine = true, draft = "A watercolor fox curled up in a misty pine forest at dawn",
        imagineOptions = ImagineOptions(kind = "image", aspectRatio = "3:4", count = 2))

    val sourceUpload = listOf(PendingUpload("s1", "Beach.jpg", "image/jpeg", 2_400_000, sent = 2_400_000, state = PendingUpload.DONE,
        ref = FileRef(path(7, "jpg"), "Beach.jpg", "image/jpeg", 2_400_000)))

    val four = (1..4).map { GeneratedMedia(path(it, "jpg"), "image/jpeg", 180_000) }

    val images = base.copy(messages = listOf(
        ChatMessage(id = "u1", text = "A misty mountain lake at night, portrait", isUser = true),
        ChatMessage(id = "m1", text = "Image created with Gemini (Nano Banana)", isUser = false, media = listOf(GeneratedMedia(path(9, "jpg"), "image/jpeg", 210_000)),
            imagine = ImagineInfo("image", "A misty mountain lake at night, portrait", "3:4", 1, "Gemini (Nano Banana)", "gemini-nano-banana-2.1")),
        ChatMessage(id = "u2", text = "Four takes on a cozy cabin in the mountains at golden hour", isUser = true),
        ChatMessage(id = "m2", text = "4 images created with Grok Imagine", isUser = false, media = four,
            imagine = ImagineInfo("image", "Four takes on a cozy cabin in the mountains at golden hour", "1:1", 4, "Grok Imagine", "grok-imagine-image-2.0")),
    ))

    val videoPending = base.copy(imagine = true, imagineOptions = ImagineOptions(kind = "video", aspectRatio = "16:9", duration = 8), messages = listOf(
        ChatMessage(id = "u1", text = "Slow drone shot over a turquoise lagoon at sunrise", isUser = true),
        ChatMessage(id = "v1", text = "Creating an 8-second video", isUser = false, imagine = ImagineInfo("video", "Slow drone shot over a turquoise lagoon at sunrise", "16:9", 1,
            "Grok Imagine", "grok-imagine-video-1.5", 8, "720p", jobId = "j".repeat(40), status = ImagineInfo.PENDING, progress = 42, startedAt = System.currentTimeMillis() - 83_000)),
    ))

    val videoDone = base.copy(messages = listOf(
        ChatMessage(id = "u1", text = "Slow drone shot over a turquoise lagoon at sunrise", isUser = true),
        ChatMessage(id = "v1", text = "Video (8 s, 720p) created with Grok Imagine", isUser = false, media = listOf(GeneratedMedia(path(3, "mp4"), "video/mp4", 6_800_000, 8)),
            imagine = ImagineInfo("video", "Slow drone shot over a turquoise lagoon at sunrise", "16:9", 1, "Grok Imagine", "grok-imagine-video-1.5", 8, "720p")),
        ChatMessage(id = "u2", text = "Now a vertical one of a city street in the rain", isUser = true),
        ChatMessage(id = "v2", text = "Video not created", isUser = false, imagine = ImagineInfo("video", "Now a vertical one of a city street in the rain", "9:16", 1,
            "Grok Imagine", seconds = 5, resolution = "720p", status = ImagineInfo.FAILED, error = "The provider declined this request under its content policy. Try a different description.")),
    ))
}
