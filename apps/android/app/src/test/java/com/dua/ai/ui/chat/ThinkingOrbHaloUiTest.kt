package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.ChatTheme
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h866dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThinkingOrbHaloUiTest(private val dark: Boolean) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun modes() = listOf(arrayOf(false), arrayOf(true))
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var previousScale = 1f
    @Before fun setup() {
        previousScale = Settings.Global.getFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }
    @After fun cleanup() {
        Settings.Global.putFloat(compose.activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previousScale)
    }

    @Test fun haloFadesOutsideTheGlyphWithoutChangingItsLayoutOrPaintingAPanel() {
        var background = 0
        compose.setContent {
            DuaTheme(LocalPreferences(theme = if (dark) "dark" else "light")) {
                background = ChatTheme.colors.background.toArgb()
                Box(Modifier.requiredSize(96.dp, 64.dp).background(ChatTheme.colors.background).testTag("orb-field")) {
                    Box(Modifier.align(Alignment.Center).size(28.dp).testTag("orb-layout")) { ThinkingOrb() }
                }
            }
        }
        compose.onNodeWithTag("orb-layout").assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(28.dp)
        val file = File("build/approved-ui/thinking-orb-halo-$dark.png")
        file.parentFile!!.mkdirs()
        compose.onNodeWithTag("orb-field").captureRoboImage(file.absolutePath)
        val pixels = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
        try {
            val cx = pixels.width / 2
            val cy = pixels.height / 2
            assertNotEquals("The soft halo must extend above the 28 dp glyph instead of ending at a square edge",
                background, pixels.getPixel(cx, cy - 18))
            assertNotEquals("The colored orb remains visible", background, pixels.getPixel(cx, cy))
            assertEquals("Far corners stay transparent over the real background", background, pixels.getPixel(2, 2))
            assertEquals("The halo fades out before the capture edge", background, pixels.getPixel(cx, 1))
        } finally { pixels.recycle() }
    }
}
