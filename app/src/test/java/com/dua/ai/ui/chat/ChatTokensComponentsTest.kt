package com.dua.ai.ui.chat

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dua.ai.files.FileRef
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.MediaLoader
import com.dua.ai.model.ReplySource
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatDarkColors
import com.dua.ai.ui.theme.ChatLightColors
import com.dua.ai.ui.theme.DuaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Chat components on ChatTokens: zip palette, light+dark, labels, handlers and 48 dp targets. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
class ChatTokensComponentsTest {
    @get:Rule val compose = createComposeRule()

    private fun show(theme: String, content: @Composable () -> Unit) = compose.setContent { DuaTheme(LocalPreferences(theme = theme)) { content() } }
    private fun assert48dp() = compose.onAllNodes(hasClickAction() and isEnabled()).fetchSemanticsNodes().forEach {
        assertTrue("touch target ${it.size}", it.size.height >= 48 * 2 - 1 && it.size.width >= 48 * 2 - 1)
    }

    @Test fun lightTokensMatchZipPalette() {
        val c = ChatLightColors
        assertEquals(Color(0xFFFFFFFF), c.background)
        assertEquals(Color(0xFFF3F4F6), c.chipFill)
        assertEquals(Color(0xFFE5E7EB), c.chipBorder)
        assertEquals(Color(0xFF111827), c.text)
        assertEquals(Color(0xFF8B5CF6), c.accent)
    }

    @Test fun accentSharedWithOtherTokens() {
        listOf(ChatLightColors to com.dua.ai.ui.theme.PageLightColors, ChatDarkColors to com.dua.ai.ui.theme.PageDarkColors).forEach { (c, p) ->
            assertEquals(Color(0xFF8B5CF6), c.accent)
            assertEquals(com.dua.ai.ui.theme.SheetLightColors.accent, c.accent)
            assertEquals(com.dua.ai.ui.theme.HomeLightColors.voice, c.accent)
            assertEquals(p.accentContainer, c.accentSoft)
            assertEquals(p.onAccentContainer, c.onAccentSoft)
        }
        assertEquals(Color(0xFFEDE9FE), ChatLightColors.accentSoft); assertEquals(Color(0xFF4C1D95), ChatLightColors.onAccentSoft)
        assertEquals(Color(0xFF2E1065), ChatDarkColors.accentSoft); assertEquals(Color(0xFFEDE9FE), ChatDarkColors.onAccentSoft)
    }

    @Test fun darkTokensDifferFromLight() {
        val l = ChatLightColors; val d = ChatDarkColors
        listOf(l.chipFill to d.chipFill, l.chipBorder to d.chipBorder, l.text to d.text, l.mutedText to d.mutedText, l.cardFill to d.cardFill,
            l.accentSoft to d.accentSoft, l.onAccentSoft to d.onAccentSoft, l.errorFill to d.errorFill, l.onErrorFill to d.onErrorFill).forEach { (a, b) -> assertNotEquals(a, b) }
    }

    private fun toggle(theme: String, on: Boolean, calls: MutableList<Boolean>) = show(theme) {
        HomeActionButton(ModeToggle("Search", "Search the web", Icons.Outlined.Search, on) { calls += it })
    }

    @Test fun homeActionButtonLightOff() {
        val calls = mutableListOf<Boolean>()
        toggle("light", on = false, calls)
        compose.onNodeWithContentDescription("Search the web").assertIsOff().performClick()
        compose.runOnIdle { assertEquals(listOf(true), calls) }
        assertEquals(Role.Switch, compose.onNodeWithContentDescription("Search the web").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role))
        assert48dp()
    }

    @Test fun homeActionButtonDarkOn() {
        val calls = mutableListOf<Boolean>()
        toggle("dark", on = true, calls)
        compose.onNodeWithContentDescription("Search the web").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(listOf(false), calls) }
        assert48dp()
    }

    @Test fun fileChipsOpenStoredFiles() {
        val theme = "light"
        val opened = mutableListOf<String>()
        show(theme) { MessageFileChips(listOf("a.pdf", "b.txt"), listOf(FileRef("p/a", "a.pdf", "application/pdf", 2048))) { opened += it.name } }
        compose.onNodeWithContentDescription("Open a.pdf").performClick()
        compose.runOnIdle { assertEquals(listOf("a.pdf"), opened) }
        compose.onNodeWithText("b.txt").assertExists()
        assert48dp()
    }

    @Test fun userMessageDarkShowsChipsAndBody() {
        show("dark") { UserMessage("Hello\nFiles: a.pdf, b.png") }
        compose.onNodeWithText("Hello").assertExists()
        compose.onNodeWithText("a.pdf").assertExists()
        compose.onNodeWithText("b.png").assertExists()
    }

    @Test fun sourceChipsLabelled() {
        show("light") { SourceChips(listOf(ReplySource(1, "Kotlin docs", "https://kotlinlang.org/docs"))) }
        compose.onNodeWithContentDescription("Source 1: Kotlin docs, kotlinlang.org. Opens in browser").assertHasClickAction()
        assert48dp()
    }

    @Test fun noticesButtonsWork() {
        var stop = 0; var device = 0; var setup = 0
        show("dark") {
            ChatNotices(ChatUiState(generatedVoice = true, generatedVoiceActive = true, error = "Finish setup first"),
                ChatActions(onStopSpeech = { stop++ }, onUseDeviceVoice = { device++ }, onOpenSetup = { setup++ }))
        }
        compose.onNodeWithText("Stop").performClick(); compose.onNodeWithText("Use device voice").performClick(); compose.onNodeWithText("Setup").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), listOf(stop, device, setup)) }
        compose.onNodeWithText("Finish setup first").assertExists()
        assert48dp()
    }

    @Test fun typingIndicatorAndSummaryDivider() {
        show("dark") { androidx.compose.foundation.layout.Column { TypingIndicator(); SummaryDivider() } }
        compose.onNodeWithContentDescription("Dua is thinking").assertExists()
        compose.onNodeWithText("Earlier messages summarised", useUnmergedTree = true).assertExists()
    }

    @Test fun loadedGeneratedImageHasTalkBackLabel() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val loader = object : MediaLoader { override fun cached(media: GeneratedMedia, maxPx: Int) = bmp }
        show("light") {
            CompositionLocalProvider(LocalMediaLoader provides loader) { MediaBitmap(GeneratedMedia("x.png", "image/png"), Modifier.size(48.dp)) }
        }
        compose.onNodeWithContentDescription(GENERATED_IMAGE_DESCRIPTION).assertExists()
    }

    @Test fun imageGridTilesLabelledOnce() {
        val bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val loader = object : MediaLoader { override fun cached(media: GeneratedMedia, maxPx: Int) = bmp }
        var opened = -1
        show("dark") {
            CompositionLocalProvider(LocalMediaLoader provides loader) {
                ImageGrid(listOf(GeneratedMedia("a.png", "image/png"), GeneratedMedia("b.png", "image/png")), "1:1", onOpen = { opened = it })
            }
        }
        compose.onNodeWithContentDescription("Generated image 2 of 2. Tap to open").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun chatTopBarButtonsLabelled() {
        var menu = 0; var newChat = 0; var search = 0
        show("dark") { ChatTopBar(onMenu = { menu++ }, onNewChat = { newChat++ }, onSearch = { search++ }) }
        listOf("Open chat history", "Find in chat", "New chat").forEach { compose.onNodeWithContentDescription(it).assertHasClickAction().performClick() }
        compose.runOnIdle { assertEquals(listOf(1, 1, 1), listOf(menu, search, newChat)) }
        assert48dp()
    }
}
