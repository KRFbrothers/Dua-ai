package com.dua.ai.ui.chat

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.dua.ai.model.FreeModel
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.setup.ProviderCapability
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelPickerMotionUiTest(private val dark: Boolean, private val motion: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},motion={1}")
        fun modes() = listOf(arrayOf(false, false), arrayOf(true, false), arrayOf(false, true), arrayOf(true, true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun badgeMotionKeepsAvailabilitySelectionAndCallbacksWithAnimationsOnOrOff() {
        val resolver = compose.activity.contentResolver
        val prior = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (motion) 1f else 0f)
            val current = mutableStateOf("Dua")
            val free = mutableStateOf<String?>(null)
            val selected = mutableListOf<String>()
            var dismissals = 0
            var haptics = 0
            compose.setContent {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    CompositionLocalProvider(LocalChatHaptic provides { haptics++ }) {
                        ModelPickerSheet(current.value, listOf(ProviderCapability("Dua", true, "configured-dua", true),
                            ProviderCapability("Gemini", true, "configured-gemini", true)), onSelect = {
                            selected += it; current.value = it; free.value = null
                        }, onOpenSetup = {}, onDismiss = { dismissals++ },
                            freeModels = listOf(FreeModel("vendor/free", "Available free model")), currentFree = free.value,
                            onSelectFree = { selected += it; free.value = it })
                    }
                }
            }
            compose.onNodeWithTag("model-row-Dua").assertIsSelected()
            compose.onNodeWithTag("model-row-ChatGPT").assertIsNotEnabled().performClick()
            assertEquals(emptyList<String>(), selected)
            compose.onNodeWithTag("model-row-Gemini").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("model-row-Dua").assertIsNotSelected()
            assertEquals(listOf("Gemini"), selected)
            assertEquals(1, haptics)
            val file = File("build/approved-ui/model-picker-motion-$dark-$motion.png")
            file.parentFile!!.mkdirs(); compose.onNodeWithTag("model-picker-sheet").captureRoboImage(file.absolutePath)
            compose.onNodeWithTag("free-model-vendor/free").performScrollTo().performClick().assertIsSelected()
            assertEquals(listOf("Gemini", "vendor/free"), selected)
            assertEquals(2, haptics)
            assertEquals(1, dismissals)
        } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prior) }
    }
}
