package app.frame.studio.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFramingTest {
    // A landscape 4000x2000 photo in a square 1000x1000 frame.
    private val bw = 4000f
    private val bh = 2000f
    private val d = 1000f

    @Test fun defaultIsTheCentreCrop() {
        val s = PhotoFraming.source(bw, bh, d, d, PhotoFit.CENTER)
        assertEquals(1000f, s.left, 0.5f)
        assertEquals(0f, s.top, 0.5f)
        assertEquals(2000f, s.width, 0.5f)
        assertEquals(2000f, s.height, 0.5f)
    }

    @Test fun panningRightRevealsTheLeftSideAndStopsAtTheEdge() {
        var fit = PhotoFraming.pan(bw, bh, d, d, PhotoFit.CENTER, dx = 200f, dy = 0f)
        assertTrue(PhotoFraming.source(bw, bh, d, d, fit).left < 1000f)
        fit = PhotoFraming.pan(bw, bh, d, d, fit, dx = 99_999f, dy = 0f)
        assertEquals(0f, PhotoFraming.source(bw, bh, d, d, fit).left, 0.5f)
    }

    @Test fun cannotPanWhereThereIsNoMorePhoto() {
        // Height already fits exactly: vertical drags do nothing.
        val fit = PhotoFraming.pan(bw, bh, d, d, PhotoFit.CENTER, dx = 0f, dy = 500f)
        assertEquals(0.5f, fit.centerY, 1e-4f)
    }

    @Test fun zoomShowsLessAndIsClamped() {
        val z = PhotoFraming.zoom(bw, bh, d, d, PhotoFit.CENTER, 2f)
        assertEquals(1000f, PhotoFraming.source(bw, bh, d, d, z).width, 0.5f)
        assertEquals(PhotoFit.MAX_ZOOM, PhotoFraming.zoom(bw, bh, d, d, z, 100f).zoom, 0f)
        assertEquals(1f, PhotoFraming.zoom(bw, bh, d, d, z, 0.01f).zoom, 0f)
    }

    @Test fun zoomingOutPullsTheCentreBackInside() {
        var fit = PhotoFraming.zoom(bw, bh, d, d, PhotoFit.CENTER, 4f)
        fit = PhotoFraming.pan(bw, bh, d, d, fit, dx = 0f, dy = 99_999f) // top edge
        fit = PhotoFraming.zoom(bw, bh, d, d, fit, 0.25f)
        val s = PhotoFraming.source(bw, bh, d, d, fit)
        assertEquals(0f, s.top, 0.5f)
        assertEquals(2000f, s.bottom, 0.5f)
    }

    @Test fun sameFitWorksForAnyFrameShape() {
        val fit = PhotoFit(zoom = 1.5f, centerX = 0.3f, centerY = 0.5f)
        val tall = PhotoFraming.source(bw, bh, 500f, 1000f, fit)
        assertTrue(tall.left >= 0f && tall.right <= bw && tall.top >= 0f && tall.bottom <= bh)
    }
}
