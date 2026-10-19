package com.dua.ai.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelStore
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.ondevice.FakeBackgroundRemover
import com.dua.ai.ondevice.FakeTextModel
import com.dua.ai.ondevice.OnDeviceServices
import com.dua.ai.ondevice.OnDeviceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.ByteArrayOutputStream
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class OnDeviceFeaturesTest {
    private lateinit var app: Application
    private val store = ViewModelStore()
    private val text = FakeTextModel(answer = "A fluffy white cat on a red sofa, soft daylight")
    private val remover = FakeBackgroundRemover()

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        for (name in listOf("dua_chat_history", "dua_files", "dua_auto_sync", "dua_account_session", "dua_preferences"))
            app.getSharedPreferences(name, 0).edit().clear().commit()
        OnDeviceServices.textModel = { text }
        OnDeviceServices.backgroundRemover = { remover }
    }
    @After fun teardown() { store.clear(); OnDeviceServices.reset() }

    private fun model() = ChatViewModel(app).also { store.put("chat-${System.nanoTime()}", it); shadowOf(android.os.Looper.getMainLooper()).idle() }
    private fun idle() = shadowOf(android.os.Looper.getMainLooper()).idle()

    @Test fun supportedPhoneWithSettingOnPolishesTheDraftInPlace() {
        val model = model()
        assertTrue(model.onDeviceAiEnabled())
        model.updateDraft("a cat")
        model.polishImaginePrompt(); idle()
        assertEquals("A fluffy white cat on a red sofa, soft daylight", model.draft.value)
        assertEquals("Prompt polished on this phone.", model.polishNotice.value)
        assertFalse(model.polishing.value)
    }

    @Test fun settingOffOrUnsupportedPhoneLeavesTheDraftAlone() {
        val model = model()
        model.updateSettings(model.localSettings.value.copy(onDeviceAi = false)); idle()
        assertFalse(model.onDeviceAiEnabled())
        model.updateDraft("a cat"); model.polishImaginePrompt(); idle()
        assertEquals("a cat", model.draft.value)
        assertTrue(text.prompts.isEmpty())

        text.current = OnDeviceStatus.Unsupported
        model.updateSettings(model.localSettings.value.copy(onDeviceAi = true)); idle()
        val unsupported = model()
        assertFalse(unsupported.onDeviceAiEnabled())
    }

    @Test fun nothingUsefulFromTheModelKeepsTheDraftAndExplains() {
        text.answer = null
        val model = model()
        model.updateDraft("a cat"); model.polishImaginePrompt(); idle()
        assertEquals("a cat", model.draft.value)
        assertTrue(model.polishNotice.value.contains("not changed"))
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun removeBackgroundUsesTheDraftImageAndWritesAPng() = runBlocking {
        val model = model()
        assertTrue(model.backgroundRemovalAvailable)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val flow = ChatViewModel::class.java.getDeclaredField("_attachments").apply { isAccessible = true }.get(model) as MutableStateFlow<List<ChatAttachment>>
        flow.value = listOf(ChatAttachment("note.txt", "text/plain", Base64.getEncoder().encodeToString("hi".toByteArray())),
            ChatAttachment("photo.png", "image/png", Base64.getEncoder().encodeToString(png)))
        assertNull(model.attachmentImageBytes(0))
        val bytes = model.attachmentImageBytes(1)!!
        val file = model.removeBackground(bytes)
        assertTrue(file.exists() && file.name.endsWith(".png"))
        assertEquals(1, remover.calls)
    }

    @Test fun noPlayServicesHidesRemoveBackground() {
        remover.available = false
        assertFalse(model().backgroundRemovalAvailable)
    }
}
