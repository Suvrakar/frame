package app.frame.studio.text

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextFontsDeviceTest {

    @Before
    fun setUp() {
        TextFonts.init(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun everyBundledFontLoads() {
        TextFonts.all.filter { it.res != null }.forEach { font ->
            assertNotSame("${font.id} fell back to default", Typeface.DEFAULT, TextFonts.typeface(font.id))
        }
    }

    @Test
    fun fontSpanChangesRenderedPixels() {
        val plain = render(null)
        val script = render("pacifico")
        assertFalse("Pacifico rendered identically to the default font", plain.sameAs(script))
    }

    private fun render(fontId: String?): Bitmap {
        val text = SpannableString("Happy Birthday")
        if (fontId != null) text.setSpan(FontSpan(fontId), 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = 64f; color = Color.WHITE }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, 1000)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val bitmap = Bitmap.createBitmap(1000, 200, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        layout.draw(Canvas(bitmap))
        return bitmap
    }
}
