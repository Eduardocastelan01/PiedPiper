package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.data.preferences.CarouselStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalMaterial3Api::class)
class ParallaxAlbumPagerTest {

    private val page = 300f

    private fun info(offset: Float, isRtl: Boolean = false, viewportEnd: Float = 400f) = parallaxItemInfo(
        pageOffset = offset,
        pageSize = page,
        crossAxisSize = page,
        viewportStart = 0f,
        viewportEnd = viewportEnd,
        isRtl = isRtl,
    )

    @Test
    fun `each carousel style keeps its page width and padding`() {
        val width = 400.dp

        val noPeek = parallaxPageLayout(CarouselStyle.NO_PEEK, width)
        assertNull(noPeek.pageWidth)
        assertEquals(0.dp, noPeek.sidePadding)

        val onePeek = parallaxPageLayout(CarouselStyle.ONE_PEEK, width)
        assertEquals(320f, onePeek.pageWidth!!.value, 0.01f)
        assertEquals(0.dp, onePeek.sidePadding)

        // The 60% page sits centred, with the neighbours peeking through the padding.
        val twoPeek = parallaxPageLayout(CarouselStyle.TWO_PEEK, width)
        assertEquals(240f, twoPeek.pageWidth!!.value, 0.01f)
        assertEquals(80f, twoPeek.sidePadding.value, 0.01f)
        assertEquals(width.value, (twoPeek.pageWidth!! + twoPeek.sidePadding * 2).value, 0.01f)
    }

    @Test
    fun `a page fully inside the carousel is neither masked nor shifted`() {
        val settled = info(offset = 0f)

        assertEquals(0f, settled.maskStart)
        assertEquals(page, settled.maskEnd)
        assertEquals(0f, settled.parallax)
        assertEquals(0f, settled.maskProgress)
    }

    @Test
    fun `a page leaving past the start edge is masked at the visible edge`() {
        val leaving = info(offset = -120f)

        // The rounded mask edge sits on the carousel edge, 120 px into the page.
        assertEquals(120f, leaving.maskStart)
        assertEquals(page, leaving.maskEnd)
        assertEquals(0.4f, leaving.maskProgress, 0.0001f)
        assertTrue(leaving.parallax > 0f, "art shifts towards the end, trailing the page")
    }

    @Test
    fun `a page entering past the end edge is masked from its end side`() {
        // With a 400 px viewport, a page at 250 has 150 px past the end edge.
        val entering = info(offset = 250f)

        assertEquals(0f, entering.maskStart)
        assertEquals(150f, entering.maskEnd)
        assertEquals(0.5f, entering.maskProgress, 0.0001f)
        assertTrue(entering.parallax < 0f, "art shifts towards the start, trailing the page")
    }

    @Test
    fun `in RTL the start edge is the right side, so the mask and parallax are mirrored`() {
        val ltr = info(offset = -120f)
        val rtl = info(offset = -120f, isRtl = true)

        // Offsets still count from the start edge, which is now the physical right of the page.
        assertEquals(0f, rtl.maskStart)
        assertEquals(page - 120f, rtl.maskEnd)
        assertEquals(-ltr.parallax, rtl.parallax)
        assertEquals(ltr.maskProgress, rtl.maskProgress)

        val rtlEntering = info(offset = 250f, isRtl = true)
        assertEquals(page - 150f, rtlEntering.maskStart)
        assertEquals(page, rtlEntering.maskEnd)
        assertTrue(rtlEntering.parallax > 0f)
    }

    @Test
    fun `an unmeasured page is left unclipped`() {
        val unmeasured = parallaxItemInfo(
            pageOffset = 0f,
            pageSize = 0f,
            crossAxisSize = 0f,
            viewportStart = 0f,
            viewportEnd = 400f,
            isRtl = false,
        )

        assertEquals(UnmaskedItemInfo, unmeasured)
    }
}
