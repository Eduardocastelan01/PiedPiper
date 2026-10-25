package com.lostf1sh.pixelplayeross.presentation.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselParallaxScrollEffectItemInfo
import androidx.compose.material3.carousel.CarouselParallaxScrollEffectState
import androidx.compose.material3.carousel.carouselParallaxScrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.lostf1sh.pixelplayeross.data.preferences.CarouselStyle

/**
 * Album-art pager with the Material 3 parallax carousel effect: pages crossing the edge of the
 * carousel are clipped to a rounded mask and their content slides at a slower rate.
 *
 * [carouselStyle] picks the page width: [CarouselStyle.NO_PEEK] fills the carousel,
 * [CarouselStyle.ONE_PEEK] leaves the next page peeking at the end, and [CarouselStyle.TWO_PEEK]
 * centres a smaller page between both neighbours. Each page is as tall as it is wide when the
 * caller sizes the carousel height to match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParallaxAlbumPager(
    state: PagerState,
    carouselStyle: String,
    carouselWidth: Dp,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    itemCornerRadius: Dp = 16.dp,
    itemKey: ((page: Int) -> Any)? = null,
    content: @Composable (page: Int) -> Unit,
) {
    val pageWidth = when (carouselStyle) {
        CarouselStyle.NO_PEEK -> carouselWidth
        CarouselStyle.TWO_PEEK -> carouselWidth * 0.6f
        else -> carouselWidth * 0.8f
    }
    val contentPadding = if (carouselStyle == CarouselStyle.TWO_PEEK) {
        PaddingValues(horizontal = (carouselWidth - pageWidth) / 2)
    } else {
        PaddingValues(0.dp)
    }
    val parallaxState = remember(state) { PagerParallaxScrollEffectState(state) }
    val shape = remember(itemCornerRadius) { RoundedCornerShape(itemCornerRadius) }

    HorizontalPager(
        state = state,
        modifier = modifier.semantics { role = Role.Carousel },
        contentPadding = contentPadding,
        pageSize = if (carouselStyle == CarouselStyle.NO_PEEK) PageSize.Fill else PageSize.Fixed(pageWidth),
        pageSpacing = itemSpacing,
        beyondViewportPageCount = 1,
        flingBehavior = PagerDefaults.flingBehavior(
            state = state,
            pagerSnapDistance = PagerSnapDistance.atMost(1)
        ),
        key = itemKey,
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .carouselParallaxScrollEffect(index = page, state = parallaxState, shape = shape)
        ) {
            content(page)
        }
    }
}

/**
 * Feeds [carouselParallaxScrollEffect] from a [PagerState], which Material 3 has no state for. The
 * parallax distance follows Material's lazy-list state. The mask differs: Material leaves its
 * rounded edge just past the viewport, which suits an edge-to-edge row, but the player's carousel
 * is inset, so the mask edge tracks the visible edge and pages keep their rounded corners as they
 * slide out.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class PagerParallaxScrollEffectState(
    private val pagerState: PagerState,
) : CarouselParallaxScrollEffectState {

    override val orientation: Orientation
        get() = pagerState.layoutInfo.orientation

    override fun calculateItemInfo(index: Int): CarouselParallaxScrollEffectItemInfo {
        val layoutInfo = pagerState.layoutInfo
        val page = layoutInfo.visiblePagesInfo.fastFirstOrNull { it.index == index }
        val pageSize = layoutInfo.pageSize.toFloat()
        if (page == null || pageSize == 0f) return UnmaskedItemInfo

        val crossAxisSize = layoutInfo.viewportSize.height.toFloat()
        val intensity = maskIntensity(pageSize, crossAxisSize)
        val parallaxDistance = pageSize * intensity
        val offset = page.offset.toFloat()
        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

        return when {
            // Leaving or entering past the start edge.
            offset < viewportStart -> {
                val hidden = (viewportStart - offset).coerceIn(0f, pageSize)
                val progress = hidden / pageSize
                CarouselParallaxScrollEffectItemInfo(
                    maskStart = hidden,
                    maskEnd = pageSize,
                    parallax = lerp(0f, parallaxDistance, progress),
                    maskProgress = progress,
                )
            }
            // Leaving or entering past the end edge.
            offset > viewportEnd - pageSize -> {
                val hidden = (offset + pageSize - viewportEnd).coerceIn(0f, pageSize)
                val progress = hidden / pageSize
                CarouselParallaxScrollEffectItemInfo(
                    maskStart = 0f,
                    maskEnd = pageSize - hidden,
                    parallax = -lerp(0f, parallaxDistance, progress),
                    maskProgress = progress,
                )
            }
            else -> CarouselParallaxScrollEffectItemInfo(
                maskStart = 0f,
                maskEnd = pageSize,
                parallax = 0f,
                maskProgress = 0f,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val UnmaskedItemInfo = CarouselParallaxScrollEffectItemInfo(
    maskStart = 0f,
    maskEnd = 0f,
    parallax = 0f,
    maskProgress = 0f,
)

/** Wider pages are masked and shifted further: from 1/4 of the page when tall to 1/2 when 16:9. */
private fun maskIntensity(mainAxisSize: Float, crossAxisSize: Float): Float {
    if (crossAxisSize == 0f) return 1 / 4f
    val aspectRatio = mainAxisSize / crossAxisSize
    return when {
        aspectRatio >= 16 / 9f -> 1 / 2f
        aspectRatio > 1f -> lerp(1 / 3f, 1 / 2f, (aspectRatio - 1f) / (16 / 9f - 1f))
        aspectRatio > 9 / 16f -> lerp(1 / 4f, 1 / 3f, (aspectRatio - 9 / 16f) / (1f - 9 / 16f))
        else -> 1 / 4f
    }
}
