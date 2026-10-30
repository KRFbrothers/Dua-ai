package com.dua.ai.ui.chat

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.HomeSizes
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeWelcomeTransitionUiTest(private val dark: Boolean, private val motion: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0},motion={1}")
        fun modes() = listOf(arrayOf(false, true), arrayOf(true, true), arrayOf(false, false), arrayOf(true, false))
    }
    @get:Rule val compose = createComposeRule()

    @Test fun homeKeepsOneLogoWithoutHeadingsDuringModeSwitchesOrRapidReversals() {
        val resolver = org.robolectric.RuntimeEnvironment.getApplication().contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val task = mutableStateOf(false)
        val name = mutableStateOf("Reader")
        val taskHeading = "What can I tackle for you?"
        val chatHeading = "Hi Reader, what's on your mind?"
        val changedHeading = "Hi Tester, what's on your mind?"
        val anyHeading = hasText(taskHeading) or hasText(chatHeading) or hasText(changedHeading)
        fun advanceSwitchFrames() {
            // Flush host work between frames instead of advancing past queued recomposition.
            repeat(3) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        fun assertLogo(stage: String) {
            // Draw the Android host at this clock time before checking visibility.
            val frame = File("build/approved-ui/home-welcome-$stage-$dark-$motion.png")
            frame.parentFile!!.mkdirs(); compose.onRoot().captureRoboImage(frame.absolutePath)
            compose.onAllNodes(anyHeading, useUnmergedTree = true).assertCountEquals(0)
            compose.onAllNodesWithContentDescription("Dua AI", useUnmergedTree = true).assertCountEquals(1)
            compose.onNodeWithContentDescription("Dua AI").assertIsDisplayed().assertWidthIsEqualTo(HomeSizes.heroLogo)
        }
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (motion) 1f else 0f)
            compose.mainClock.autoAdvance = false
            compose.setContent {
                DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                    Box(Modifier.requiredSize(393.dp, 720.dp).background(ChatTheme.colors.background)) {
                        ChatContent(task.value, profileName = name.value, onTask = {})
                    }
                }
            }
            compose.mainClock.advanceTimeBy(1_000)
            assertLogo("initial")
            compose.runOnIdle { task.value = true }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(80)
            assertLogo("transition")
            // Reverse before the old fade would finish, then change the signed-in name.
            compose.runOnIdle { task.value = false }
            advanceSwitchFrames()
            assertLogo("reverse")
            compose.runOnIdle { task.value = true }
            advanceSwitchFrames()
            assertLogo("task-again")
            compose.runOnIdle { task.value = false; name.value = "Tester" }
            advanceSwitchFrames()
            assertLogo("account-change")
            compose.mainClock.advanceTimeBy(1_000)
            assertLogo("settled")
        } finally {
            compose.mainClock.autoAdvance = true
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
        }
    }
}
