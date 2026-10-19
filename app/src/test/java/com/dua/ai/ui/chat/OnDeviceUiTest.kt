package com.dua.ai.ui.chat

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dua.ai.attachments.ChatAttachment
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.ImagineConfig
import com.dua.ai.imagine.ImagineOptions
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.settings.GeneralContent
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.SheetSizes
import com.dua.ai.ui.theme.Sizes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Base64

/** On-device features in the UI: text label "On-device" only, hidden when unsupported, 48 dp targets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h914dp-mdpi")
class OnDeviceUiTest {
    @get:Rule val compose = createComposeRule()
    private val config = ImagineConfig(imageEnabled = true, videoEnabled = true)

    @Test fun polishRowShowsOnDeviceLabelAndPolishes() {
        var polished = 0
        compose.setContent { DuaTheme(LocalPreferences()) {
            ImagineOptionsContent(ImagineOptions(), config, null, false, {}, {}, polish = PromptPolishState(), onPolish = { polished++ })
        } }
        compose.onNodeWithText("On-device").assertExists()
        compose.onNodeWithTag("imagine-polish").assertHeightIsAtLeast(SheetSizes.touch).performClick()
        assertEquals(1, polished)
    }

    @Test fun polishRowIsHiddenWhenUnsupportedAndDisabledWithoutADraftOrWhileBusy() {
        var state: PromptPolishState? by androidx.compose.runtime.mutableStateOf(null)
        compose.setContent { DuaTheme(LocalPreferences()) { ImagineOptionsContent(ImagineOptions(), config, null, false, {}, {}, polish = state) } }
        compose.onNodeWithText("Polish my prompt").assertDoesNotExist()
        compose.onNodeWithText("On-device").assertDoesNotExist()
        state = PromptPolishState(canPolish = false)
        compose.onNodeWithTag("imagine-polish").assertIsNotEnabled()
        compose.onNodeWithText("Type an idea in the message box first.").assertExists()
        state = PromptPolishState(busy = true, notice = "")
        compose.onNodeWithText("Polishing your prompt…").assertExists()
        compose.onNodeWithTag("imagine-polish").assertIsNotEnabled()
        state = PromptPolishState(notice = "Prompt polished on this phone.")
        compose.onNodeWithText("Prompt polished on this phone.").assertExists()
    }

    private fun photo(): ChatAttachment {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        return ChatAttachment("photo.png", "image/png", Base64.getEncoder().encodeToString(bytes))
    }

    @Test fun photoAttachmentOffersRemoveBackgroundAndKeepsRemoveButton() {
        var cut = -1; var removed = -1
        compose.setContent { DuaTheme(LocalPreferences()) {
            AttachmentBar(listOf(photo()), emptyList(), true, { removed = it }, {}, onRemoveBackground = { cut = it })
        } }
        compose.onNodeWithTag("attachment-image-options").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        compose.onAllNodesWithText("On-device", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("Remove background", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Remove background from photo.png, on this phone").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        assertEquals(0, cut)
        compose.onNodeWithContentDescription("Remove attachment photo.png").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        assertEquals(0, removed)
    }

    @Test fun withoutSupportPhotoAttachmentsHaveNoMenu() {
        compose.setContent { DuaTheme(LocalPreferences()) { AttachmentBar(listOf(photo()), emptyList(), true, {}, {}) } }
        compose.onNodeWithTag("attachment-image-options").assertDoesNotExist()
    }

    @Test fun viewerShowsRemoveBackgroundForImagesOnly() {
        var cut: GeneratedMedia? = null
        val image = GeneratedMedia("generated/a.png", "image/png")
        compose.setContent { DuaTheme(LocalPreferences()) {
            MediaViewerContent(listOf(image), 0, ImagineActions(onRemoveBackground = { cut = it }), false, {}, playVideo = false)
        } }
        compose.onNodeWithText("Remove background").assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        assertEquals(image, cut)
    }

    @Test fun viewerHidesRemoveBackgroundWhenUnavailable() {
        compose.setContent { DuaTheme(LocalPreferences()) {
            MediaViewerContent(listOf(GeneratedMedia("generated/a.png", "image/png")), 0, ImagineActions(), false, {}, playVideo = false)
        } }
        compose.onNodeWithText("Remove background").assertDoesNotExist()
    }

    @Test fun settingIsOnByDefaultOnlyWhenSupported() {
        var value: Boolean? = null
        var supported: Boolean? by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent { DuaTheme(LocalPreferences()) {
            GeneralContent(LocalPreferences(), "", true, {}, {}, {}, onDeviceSupported = supported, onOnDeviceAi = { value = it })
        } }
        val toggle = hasText("On-device AI") and isToggleable()
        compose.onNode(toggle).performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithText("Not available on this phone. Dua uses the server instead.").assertExists()
        supported = true
        compose.onNode(toggle).assertIsOn().assertIsEnabled().assertHeightIsAtLeast(Sizes.touchTarget).performClick()
        assertEquals(false, value)
    }
}
