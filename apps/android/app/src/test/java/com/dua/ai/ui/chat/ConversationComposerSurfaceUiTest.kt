package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Covers the conversation's four-button glass composer, which empty-Home tests omit. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConversationComposerSurfaceUiTest(private val dark: Boolean, private val large: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},large={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private val background get() = if (dark) Color(0xFF292640) else Color(0xFFE0E4FF)

    private fun capture(label: String): File = File("build/approved-ui/conversation-composer-$label-$dark-$large.png").also {
        it.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(it.absolutePath)
    }

    @Test fun chatTaskAndGeneratingKeepTheSameIdleLayoutAndAllFourActions() {
        val placeholder = mutableStateOf("Ask Dua AI...")
        val generating = mutableStateOf(false)
        val draft = mutableStateOf("")
        var image = 0; var attach = 0; var mic = 0; var stop = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (large) 2f else 1f)) {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                        Composer(homeLayout = true, glass = true, controlled = true, showModelSelector = false,
                            value = draft.value, onDraftChange = { draft.value = it },
                            placeholder = placeholder.value, typing = generating.value, onImage = { image++ },
                            onAttach = { attach++ }, onMic = { mic++ }, onStop = { stop++ })
                    }
                }
            }
        }
        val input = compose.onNodeWithContentDescription("Message")
        val inputId = input.fetchSemanticsNode().id
        val field = input.fetchSemanticsNode().boundsInRoot
        val plus = compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot
        if (large) assertTrue("Large text gets its own row", field.bottom <= plus.top)
        else assertTrue("An empty Chat with the image shortcut must remain inline", field.top < plus.bottom && plus.top < field.bottom)
        val height = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height
        capture("chat")
        compose.runOnIdle { generating.value = true }
        compose.onNodeWithContentDescription("Stop response").assertIsDisplayed().performClick()
        assertEquals(height, compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height, 1f)
        capture("generating")
        compose.runOnIdle { placeholder.value = "Describe..."; generating.value = false }
        compose.onNodeWithText("Describe...").assertIsDisplayed()
        assertEquals("Changing the idle mode must not resize the composer", height,
            compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height, 1f)
        assertEquals(inputId, input.fetchSemanticsNode().id)
        for (description in listOf("Add photos and files", "Create image", "Dictate", "Voice mode")) {
            val button = compose.onNodeWithContentDescription(description).assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            if (description != "Voice mode") button.performClick()
        }
        assertEquals(listOf(1, 1, 1, 1), listOf(image, attach, mic, stop))
        capture("task")
        input.performClick().performTextInput("First line\nSecond line")
        input.assertIsFocused()
        assertEquals("Expanding must retain the same text field", inputId, input.fetchSemanticsNode().id)
        assertTrue(input.fetchSemanticsNode().boundsInRoot.bottom <=
            compose.onNodeWithContentDescription("Add photos and files").fetchSemanticsNode().boundsInRoot.top)
        capture("multiline")
        input.performTextClearance()
        input.assertIsFocused()
        assertEquals(height, compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test fun roundedShadowCanFeatherOutsideTheAnimatedContentBounds() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
                    Composer(homeLayout = true, glass = true, controlled = true, showModelSelector = false, onImage = {})
                }
            }
        }
        val card = compose.onNodeWithTag("composer-container").fetchSemanticsNode().boundsInRoot
        val bitmap = requireNotNull(BitmapFactory.decodeFile(capture("shadow").absolutePath))
        try {
            val x = (card.left - with(compose.density) { 2.dp.toPx() }).toInt()
            val y = card.center.y.toInt()
            assertNotEquals("The resize animation must not clip the shadow into a rectangle", background.toArgb(), bitmap.getPixel(x, y))
        } finally { bitmap.recycle() }
    }

    @Test fun elevatedBackingDoesNotLeakDifferentBackgroundsIntoTheInput() {
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Column(Modifier.fillMaxSize()) {
                    for (backdrop in listOf(Color(0xFFBFD5FF), Color(0xFFFFD3C4))) {
                        Box(Modifier.weight(1f).fillMaxSize().background(backdrop), contentAlignment = Alignment.Center) {
                            Composer(homeLayout = true, glass = true, controlled = true, showModelSelector = false, onImage = {})
                        }
                    }
                }
            }
        }
        val cards = compose.onAllNodesWithTag("composer-container").fetchSemanticsNodes().map { it.boundsInRoot }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(capture("backing").absolutePath))
        try {
            // Top padding, empty writing space and bottom padding share one opaque backing.
            // The decoration's phase is the same for both cards in this single captured frame.
            for (fraction in listOf(.08f, .5f, .92f)) {
                val pixels = cards.map { card -> bitmap.getPixel(card.center.x.toInt(), (card.top + card.height * fraction).toInt()) }
                for (channel in listOf<(Int) -> Int>(android.graphics.Color::red, android.graphics.Color::green, android.graphics.Color::blue)) {
                    assertTrue("Card backing should not expose background/shadow bands", kotlin.math.abs(channel(pixels[0]) - channel(pixels[1])) <= 1)
                }
            }
        } finally { bitmap.recycle() }
    }
}
