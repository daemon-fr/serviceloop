package com.v16studio.serviceloop

import com.v16studio.serviceloop.ui.PdfPageZoomMath
import com.v16studio.serviceloop.ui.PdfPagePanBounds
import com.v16studio.serviceloop.ui.PdfPageTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageZoomMathTest {
    @Test fun scaleIsClampedBetweenFittedAndFourTimes() {
        assertEquals(1f, PdfPageZoomMath.clampScale(0.4f))
        assertEquals(2.5f, PdfPageZoomMath.clampScale(2.5f))
        assertEquals(4f, PdfPageZoomMath.clampScale(7f))
    }

    @Test fun panBoundsComeFromRenderedPageViewportAndScale() {
        assertEquals(PdfPagePanBounds(240f, 350f), PdfPageZoomMath.translationBounds(400f, 600f, 320f, 500f, 2f))
        assertEquals(PdfPagePanBounds(0f, 0f), PdfPageZoomMath.translationBounds(400f, 600f, 320f, 500f, 1f))
        assertEquals(PdfPagePanBounds(0f, 0f), PdfPageZoomMath.translationBounds(200f, 300f, 400f, 600f, 2f))
    }

    @Test fun panIsClampedToUsefulViewportAndOneTimesRecenters() {
        val bounded = PdfPageZoomMath.clampTranslation(300f, -400f, PdfPagePanBounds(240f, 350f), 2f)
        assertEquals(PdfPageTransform(2f, 240f, -350f), bounded)
        assertEquals(PdfPageTransform(), PdfPageZoomMath.clampTranslation(80f, -90f, PdfPagePanBounds(240f, 350f), 1f))
        assertEquals(PdfPageTransform(), PdfPageZoomMath.reset())
    }
}
