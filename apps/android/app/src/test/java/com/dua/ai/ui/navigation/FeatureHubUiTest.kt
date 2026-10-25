package com.dua.ai.ui.navigation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.dua.ai.imagine.GeneratedMedia
import com.dua.ai.imagine.MediaLoader
import com.dua.ai.library.*
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.*
import com.dua.ai.ui.chat.LocalMediaLoader
import com.dua.ai.ui.chat.MediaViewer
import com.dua.ai.ui.chat.ImagineActions
import com.dua.ai.ui.features.*
import com.dua.ai.ui.theme.*
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureHubUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},compact={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createComposeRule()
    private fun show(block: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, if (compact) 2f else 1f)) {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                assertEquals(if (dark) PageDarkColors else PageLightColors, PageTheme.colors)
                Box(Modifier.requiredSize(if (compact) 320.dp else 360.dp, if (compact) 568.dp else 752.dp)) { block() }
            }
        }
    }
    private fun capture(name: String) {
        if (System.getProperty("roborazzi.test.record") == "true") {
            val file = File("build/approved-ui/hub-$name-${if (dark) "dark" else "light"}-${if (compact) "font2" else "regular"}.png")
            file.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(file.path)
        }
    }
    private fun noHome() {
        for (text in listOf("Coming soon", "Your AI, always with you", "Dua ⌄", "What can I take off")) compose.onNodeWithText(text).assertDoesNotExist()
        compose.onNode(hasText("Task") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithContentDescription("Model, Flash").assertDoesNotExist()
    }
    @Test fun explicitNavigationShowsRealFeatureHubsAndBack() {
        lateinit var navigate: (String) -> Unit
        show {
            val nav = rememberNavController()
            navigate = { nav.navigate(it) }
            NavHost(nav, startDestination = "test-home") {
                composable("test-home") { Text("Test home") }
                functionalFeaturePages(
                    images = { ImagesContent(emptyList(), "", "1:1", listOf("1:1"), false, true, "", "", { nav.popBackStack() }, {}, {}, {}, {}, {}) },
                    library = { LibraryContent(emptyList(), false, "", { nav.popBackStack() }, {}, {}) },
                    plugins = { PluginsContent(IntegrationState(), "Dua", false, false, false, false, false, "", "", { nav.popBackStack() }, {}, {}, {}, {}, {}) })
                comingSoonPages(onBack = { nav.popBackStack() })
            }
        }
        for (destination in listOf(DuaDestination.Images, DuaDestination.Library, DuaDestination.Plugins)) {
            assertNull(destination.comingSoonTitle)
            compose.runOnIdle { navigate(destination.route) }
            compose.onNodeWithTag("${destination.name.lowercase()}-feature").assertIsDisplayed()
            compose.onNode(hasText(destination.name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
            noHome(); capture(destination.name.lowercase())
            compose.onNodeWithContentDescription("Back").assertWidthIsAtLeast(48.dp).performClick()
            compose.onNodeWithText("Test home").assertIsDisplayed()
        }
    }
    @Test fun imagesStartersAspectAndGenerationUseCallbacksAndRespectBusyState() {
        val draft = mutableStateOf(""); val aspect = mutableStateOf("1:1"); val busy = mutableStateOf(false)
        var generated = ""
        show { ImagesContent(emptyList(), draft.value, aspect.value, listOf("1:1", "16:9", "9:16"), busy.value, true, "", "", {},
            { draft.value = it }, { aspect.value = it }, { generated = draft.value }, {}, {}) }
        compose.onNodeWithText(ImageStarterPrompts[0]).performScrollTo().performClick()
        assertEquals(ImageStarterPrompts[0], draft.value)
        compose.onNodeWithContentDescription("Create image").performClick()
        assertEquals(ImageStarterPrompts[0], generated)
        compose.onNodeWithText("1:1").performClick()
        compose.onNodeWithText("16:9").performClick()
        assertEquals("16:9", aspect.value)
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithContentDescription("Create image").assertIsNotEnabled()
    }
    @Test fun libraryFiltersRealItemsAndSearchesContentsWithoutSamples() {
        val items = listOf(LibraryItem("file", "Budget.csv", "Revenue 100", LibraryKind.Files),
            LibraryItem("code", "Python snippet", "profit = 42", LibraryKind.Code), LibraryItem("photo", "Mumbai.png", "Skyline", LibraryKind.Images))
        var opened = ""; var upload = 0
        show { LibraryContent(items, false, "", {}, { upload++ }, { opened = it.id }) }
        compose.onNodeWithContentDescription("Upload files").performClick(); assertEquals(1, upload)
        compose.onNodeWithText("Code", substring = false).performClick()
        compose.onNodeWithText("Budget.csv").assertDoesNotExist()
        compose.onNodeWithText("Python snippet").performScrollTo().performClick(); assertEquals("code", opened)
        compose.onNodeWithText("All", substring = false).performClick()
        compose.onNodeWithText("Search library...").performTextInput("Revenue")
        compose.onNodeWithText("Budget.csv").assertExists()
        compose.onNodeWithText("Python snippet").assertDoesNotExist()
        capture("library-search")
    }
    @Test fun imageGridOpensFullScreenPromptWithSaveAndShareActions() {
        var save = 0; var share = 0
        val item = LibraryItem("image", "Image", "Real generated prompt", LibraryKind.Images, media = GeneratedMedia("owner/image.png", "image/png"))
        show {
            var opened by remember { mutableStateOf(false) }
            val bitmap = remember { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888) }
            val loader = remember { object : MediaLoader { override fun cached(media: GeneratedMedia, maxPx: Int) = bitmap } }
            CompositionLocalProvider(LocalMediaLoader provides loader) {
                ImagesContent(listOf(item), "", "1:1", listOf("1:1"), false, true, "", "", {}, {}, {}, {}, { opened = true }, {})
                if (opened) MediaViewer(listOf(item.media!!), 0, ImagineActions(onSave = { save++ }, onShare = { share++ }), false,
                    onDismiss = { opened = false }, prompt = item.content, showEditActions = false)
            }
        }
        compose.onNodeWithContentDescription("Real generated prompt").performClick()
        compose.onNodeWithText("Save", substring = false).performClick()
        compose.onNodeWithText("Share", substring = false).performClick()
        assertEquals(1, save); assertEquals(1, share)
        compose.onNodeWithText("Edit", substring = false).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close viewer").performClick()
    }
    @Test fun pluginControlsReflectCapabilitiesAndInvokeExistingHandlers() {
        var search = false; var code = false; var setup = 0; var memory = 0
        val enabled = mutableStateOf(true)
        show { PluginsContent(if (enabled.value) IntegrationState(nativeSearch = setOf("Dua"), codeExecution = CodeExecutionConfig(true, providers = setOf("Dua"))) else IntegrationState(),
            "Dua", false, false, false, false, true, "", "", {}, { search = it }, { code = it }, { setup++ }, { memory++ }, {}) }
        compose.onNodeWithContentDescription("Google Search Grounding").performScrollTo().performClick(); assertTrue(search)
        compose.onNodeWithContentDescription("Code Runner / Sandbox").performScrollTo().performClick(); assertTrue(code)
        compose.onNodeWithTag("plugin-open-Cloud Memory & Context").performScrollTo().performClick(); assertEquals(1, memory)
        compose.onNodeWithTag("plugin-open-GitHub Repository & PR Connector").performScrollTo().performClick(); assertEquals(1, setup)
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithContentDescription("Google Search Grounding").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Code Runner / Sandbox").performScrollTo().assertIsNotEnabled()
    }
    @Test fun separateEngineLinksToAgentInsteadOfClaimingStandaloneFlagsWork() {
        var opened = 0
        show { PluginsContent(IntegrationState(nativeSearch = setOf("Dua"), codeExecution = CodeExecutionConfig(true, providers = setOf("Dua"))),
            "Dua", false, false, false, false, true, "", "", {}, {}, {}, {}, {}, {}, separateEngine = true, agentAvailable = true, onAgent = { opened++ }) }
        compose.onNodeWithContentDescription("Google Search Grounding").assertDoesNotExist()
        compose.onNodeWithContentDescription("Code Runner / Sandbox").assertDoesNotExist()
        compose.onNodeWithTag("plugin-open-Google Search Grounding").performScrollTo().performClick()
        compose.onNodeWithTag("plugin-open-Code Runner / Sandbox").performScrollTo().performClick()
        assertEquals(2, opened)
    }
}
