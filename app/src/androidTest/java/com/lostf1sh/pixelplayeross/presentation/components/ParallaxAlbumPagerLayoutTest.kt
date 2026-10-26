package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.preferences.CarouselStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [parallaxItemInfo] mirrors its mask for RTL on the assumption that pager page offsets count
 * from the start edge in either direction. These tests pin that assumption to the real pager.
 */
@RunWith(AndroidJUnit4::class)
class ParallaxAlbumPagerLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var pagerState: PagerState

    private fun setPager(layoutDirection: LayoutDirection) {
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                pagerState = rememberPagerState { 3 }
                ParallaxAlbumPager(
                    state = pagerState,
                    carouselStyle = CarouselStyle.NO_PEEK,
                    carouselWidth = 300.dp,
                    modifier = Modifier.size(300.dp),
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Scrolls a third of a page forward and returns the next page's offset over the page size. */
    private fun nextPageOffsetFractionAfterPartialScroll(): Float {
        val pageSize = pagerState.layoutInfo.pageSize
        runBlocking(Dispatchers.Main) {
            pagerState.scroll { scrollBy(pageSize / 3f) }
        }
        composeRule.waitForIdle()
        val nextPage = pagerState.layoutInfo.visiblePagesInfo.first { it.index == 1 }
        return nextPage.offset.toFloat() / pageSize
    }

    @Test
    fun pageOffsetsCountFromTheStartEdgeInLtr() {
        setPager(LayoutDirection.Ltr)

        assertThat(nextPageOffsetFractionAfterPartialScroll()).isWithin(0.02f).of(2f / 3f)
    }

    @Test
    fun pageOffsetsCountFromTheStartEdgeInRtl() {
        setPager(LayoutDirection.Rtl)

        // Same start-relative offset as LTR, even though the page is drawn on the left.
        assertThat(nextPageOffsetFractionAfterPartialScroll()).isWithin(0.02f).of(2f / 3f)
    }
}
