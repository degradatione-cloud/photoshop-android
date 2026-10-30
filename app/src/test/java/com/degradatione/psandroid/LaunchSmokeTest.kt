package com.degradatione.psandroid

import android.os.Looper
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LaunchSmokeTest {

    @Test
    fun activityComposesWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(controller.get())
    }

    @Test
    fun editorOpsWorkOffDevice() {
        val ed = Editor()
        ed.newDocument(200, 200)
        ed.addLayer()
        ed.foreground = android.graphics.Color.RED
        ed.fillAt(10, 10)
        ed.beginStroke()
        ed.strokeTo(5f, 5f, 40f, 40f, false)
        ed.endStroke(false)
        ed.invert()
        ed.undoStep()
        ed.blur()
        ed.crop(android.graphics.RectF(0f, 0f, 100f, 100f))
        val out = ed.composite()
        assertNotNull(out)
        org.junit.Assert.assertEquals(100, out.width)
    }
}
