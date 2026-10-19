package com.dua.ai.motion

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.dua.ai.ui.theme.rememberMotionAllowed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotionPreferenceUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun animatorSettingChangesReachTheMountedScreenWithoutRecreatingIt() {
        val resolver = compose.activity.contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        try {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            compose.setContent { Text(if (rememberMotionAllowed()) "Motion on" else "Motion off") }
            compose.onNodeWithText("Motion on").assertExists()
            for (scale in listOf(0f, 0.5f, 0f, 1f)) {
                compose.runOnIdle { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale) }
                ShadowLooper.idleMainLooper()
                compose.onNodeWithText(if (scale > 0f) "Motion on" else "Motion off").assertExists()
            }
        } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous) }
    }
}
