package com.dua.ai.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.model.ChatMessage
import com.dua.ai.model.ReplySource
import com.dua.ai.model.ReplySources
import com.dua.ai.network.ChatStreamParser
import com.dua.ai.network.ReplyEvent
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RichSourceCardsTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private val first = ReplySource(1, "Kotlin documentation and practical guides for building accessible Android applications", "https://kotlinlang.org/docs")
    private val second = ReplySource(2, "Dua AI repository", "https://github.com/KRFbrothers/Dua-ai")
    private fun label(source: ReplySource) = "Source ${source.number}: ${source.pageTitle ?: source.domain}, ${source.domain}. Opens in browser"
    private fun show(large: Boolean = false, content: @Composable () -> Unit) = compose.setContent {
        context = LocalContext.current
        val density = LocalDensity.current
        CompositionLocalProvider(LocalFaviconLoader provides null,
            LocalDensity provides Density(density.density, if (large) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                Box(Modifier.requiredSize(320.dp, 568.dp).background(ChatTheme.colors.background)) { content() }
            }
        }
    }
    private fun layout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
    private fun activity(): Activity {
        var base = context
        while (base is ContextWrapper && base !is Activity) base = base.baseContext
        return base as Activity
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/sources-$name-${if (dark) "dark" else "light"}.png")
            file.parentFile!!.mkdirs()
            compose.onRoot().captureRoboImage(file.absolutePath)
        }
    }

    @Test fun cardsShowDomainInitialTitleAndIndexWithTheOriginalAccessibleAction() {
        show { SourceChips(listOf(first, second)) }
        val card = compose.onNodeWithContentDescription(label(first))
            .assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        val semantics = card.fetchSemanticsNode().config
        assertEquals(Role.Button, semantics[SemanticsProperties.Role])
        assertEquals("Open source", semantics[SemanticsActions.OnClick].label)
        compose.onNodeWithText("K", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("kotlinlang.org", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("[1]", useUnmergedTree = true).assertExists()
        assertEquals(2, layout(first.title).layoutInput.maxLines)
        assertEquals(2, layout(first.title).lineCount)
        capture("cards")
    }

    @Test fun sourcesScrollHorizontallyAndTapOpensTheExactVerifiedUrl() {
        val third = ReplySource(3, "Android reference", "https://developer.android.com/reference")
        show { SourceChips(listOf(first, second, third)) }
        val before = compose.onNodeWithContentDescription(label(first)).fetchSemanticsNode().positionInRoot
        val last = compose.onNodeWithContentDescription(label(third)).performScrollTo().assertIsDisplayed()
        val after = compose.onNodeWithContentDescription(label(first)).fetchSemanticsNode().positionInRoot
        assertTrue(after.x < before.x)
        assertEquals(before.y, after.y, 1f)
        last.performTouchInput { click() }
        compose.runOnIdle {
            val intent = Shadows.shadowOf(activity()).nextStartedActivity
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(third.url, intent.dataString)
        }
    }

    @Test fun missingPageTitleUsesTheDomainAndKeepsItsLegacyLabel() {
        val source = ReplySource(4, "", "https://example.org/docs")
        show { SourceChips(listOf(source)) }
        compose.onNodeWithContentDescription("Source 4: example.org, example.org. Opens in browser").assertHasClickAction()
        compose.onAllNodesWithText("example.org", useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("[4]", useUnmergedTree = true).assertExists()
    }

    @Test fun replacingCitationsUpdatesTheDomainInitialAndAccessibleLinkTarget() {
        val sources = mutableStateOf(listOf(first))
        val replacement = ReplySource(1, "New release", "https://news.example.org/release?version=2")
        show { SourceChips(sources.value) }
        compose.onNodeWithText("K", useUnmergedTree = true).assertExists()
        compose.runOnIdle { sources.value = listOf(replacement) }
        compose.onNodeWithContentDescription(label(first)).assertDoesNotExist()
        compose.onNodeWithText("K", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("N", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(label(replacement)).performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(replacement.url, Shadows.shadowOf(activity()).nextStartedActivity.dataString)
        }
    }

    @Test fun largeTextKeepsTwoLineTitlesAndAnUnclippedIndexOnASmallPhone() {
        show(large = true) { SourceChips(listOf(first, second)) }
        val card = compose.onNodeWithContentDescription(label(first)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val title = layout(first.title)
        assertEquals(2f, title.layoutInput.density.fontScale)
        assertEquals(2, title.layoutInput.maxLines)
        assertEquals(2, title.lineCount)
        val badge = layout("[1]")
        // Compose's paragraph retains the row's max width even when this label wraps to its
        // intrinsic width. Check the drawn line bounds rather than didOverflowWidth.
        assertEquals(1, badge.lineCount)
        assertFalse(badge.isLineEllipsized(0))
        assertTrue(badge.getLineLeft(0) >= 0f)
        assertTrue(badge.getLineRight(0) <= badge.size.width + 1f)
        assertFalse(badge.didOverflowHeight)
        val bounds = compose.onNodeWithText("[1]", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= card.left && bounds.right <= card.right)
        assertTrue(bounds.top >= card.top && bounds.bottom <= card.bottom)
        capture("font2")
    }

    @Test fun structuredCitationsPreserveDomainAndOpenTheProviderVerifiedRedirect() {
        val url = "https://vertexaisearch.cloud.google.com/grounding-api-redirect/verified"
        val event = ChatStreamParser.decode("""{"type":"sources","sources":[{"id":2,"title":"Release notes","domain":"github.com","url":"$url"}]}""") as ReplyEvent.Sources
        val source = event.sources.single()
        show { SourceChips(event.sources) }
        compose.onNodeWithText("github.com", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription(label(source)).performClick()
        compose.runOnIdle { assertEquals(url, Shadows.shadowOf(activity()).nextStartedActivity.dataString) }
    }

    @Test fun unsafeUrlsAreStillBlockedByTheExistingBrowserHandler() {
        val unsafe = ReplySource(1, "Unsafe", "https://user:password@example.org/private")
        show { SourceChips(listOf(unsafe)) }
        compose.onNodeWithContentDescription(label(unsafe)).performClick()
        compose.runOnIdle { assertNull(Shadows.shadowOf(activity()).nextStartedActivity) }
    }

    @Test fun completedAssistantFooterShowsCardsBelowItsBodyAndStreamingHidesThem() {
        val text = "Grounded answer.\n\nSources:\n[1] Kotlin docs\nhttps://kotlinlang.org/docs"
        val message = mutableStateOf(ChatMessage(text = text, isUser = false, state = ChatMessage.STREAMING))
        show {
            AssistantMessage(message.value, isLast = true, typing = message.value.streaming,
                speaking = false, speakEnabled = false, generatedVoice = false, retryWebMode = null,
                onRetry = {}, onSpeak = {}, showActions = false)
        }
        val source = ReplySources.split(text).sources.single()
        compose.onNodeWithContentDescription(label(source)).assertDoesNotExist()
        compose.runOnIdle { message.value = message.value.copy(state = "complete") }
        val body = compose.onNodeWithText("Grounded answer.", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithContentDescription(label(source)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(body.bottom <= card.top)
    }
}
