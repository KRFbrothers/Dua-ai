package com.dua.ai.widget

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.dua.ai.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class ToolbarWidgetPreviewTest {
    @Test fun toolbarFitsMinimumWidthAndLargeTextInLightAndDark() {
        val app = RuntimeEnvironment.getApplication()
        for (night in listOf(false, true)) for (large in listOf(false, true)) {
            val config = Configuration(app.resources.configuration).apply {
                uiMode = Configuration.UI_MODE_TYPE_NORMAL or if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                fontScale = if (large) 2f else 1f
            }
            val context = app.createConfigurationContext(config)
            val width = if (large) 250 else 360
            val view = DuaToolbarWidgetReceiver.views(context, 1).apply(context, FrameLayout(context))
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(144, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, 144)
            for (id in DuaToolbarWidgetReceiver.buttons.keys) {
                val target = view.findViewById<View>(id); val rect = android.graphics.Rect()
                assertTrue(target.getGlobalVisibleRect(rect)); assertEquals(48, rect.height()); assertTrue(rect.width() >= 48)
            }
            val file = File("build/approved-ui/toolbar-${if (night) "dark" else "light"}-${if (large) "large-text" else "regular"}.png")
            file.parentFile!!.mkdirs()
            val bitmap = Bitmap.createBitmap(width, 144, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun allSixSupplied960ViewportVectorsRenderOnTheAndroidCanvas() {
        val app = RuntimeEnvironment.getApplication()
        for (id in listOf(R.drawable.ls_live_wave, R.drawable.gs_spark, R.drawable.gs_camera, R.drawable.gs_screenshare, R.drawable.gs_video_camera, R.drawable.gs_bookmark_add)) {
            val xml = app.resources.getXml(id)
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) Unit
            assertEquals(960f, xml.getAttributeFloatValue("http://schemas.android.com/apk/res/android", "viewportWidth", 0f), 0f); xml.close()
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            app.getDrawable(id)!!.apply { setBounds(0, 0, 48, 48); draw(Canvas(bitmap)) }
            assertTrue((0 until 48).any { x -> (0 until 48).any { y -> android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0 } })
        }
    }
}
