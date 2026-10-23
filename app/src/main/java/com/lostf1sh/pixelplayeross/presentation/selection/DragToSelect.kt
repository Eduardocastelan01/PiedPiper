package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputEventHandler
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

private const val LogTag = "DragToSelect"

/**
 * Edge zones of the visible list in which a drag-select auto-scrolls. Kept below a song row's
 * height so the last row above the mini player can still be selected without scrolling past it.
 */
private val AutoScrollEdgeZone = 56.dp

/** Auto-scroll speed per second with the finger at the very edge of the visible list. */
private val MaxAutoScrollSpeed = 1000.dp

/**
 * Links a list's rows to its [dragToSelect] modifier. A row calls [onItemLongPressed] from its
 * long press, after toggling itself, to let the finger that is still down go on selecting rows.
 */
internal class DragToSelectState {
    internal var isPointerDown = false
    internal var isArmRequested = false

    /**
     * Arms drag-to-select for the finger that is holding the long-pressed row. A long press that
     * does not come from a finger on the list (an accessibility action, or a tap in selection
     * mode, which is reported on release) is ignored.
     */
    fun onItemLongPressed() {
        if (isPointerDown) isArmRequested = true
    }
}

@Composable
internal fun rememberDragToSelectState(): DragToSelectState = remember { DragToSelectState() }

/**
 * Drag-to-select for a [LazyColumn][androidx.compose.foundation.lazy.LazyColumn] of selectable
 * rows. Once a row's long press arms [state], the list stops scrolling (and a parent pager stops
 * swiping) until the finger lifts; every row the finger is dragged over takes the long-pressed
 * row's new state, and the list auto-scrolls while the finger rests near its top or bottom edge.
 *
 * Append it last to the list's modifier chain so pointer positions line up with the list's items.
 *
 * @param itemAt The selectable item at a list index, or null for rows that cannot be selected
 *   (headers, folders) or are not loaded yet.
 * @param isSelected The item's current selection state. Read it from the selection holder
 *   itself, not from composed state, which lags a frame behind the long press.
 * @param onSelectionChange Applies one drag step; songs in `select` are in drag order.
 * @param topInset Height of what overlays the top of the list's on-screen part, such as a
 *   collapsing top bar.
 * @param bottomInset Height of what overlays the bottom of the list's on-screen part, such as
 *   the navigation bar and mini player.
 */
internal fun <T : Any> Modifier.dragToSelect(
    state: DragToSelectState,
    listState: LazyListState,
    itemAt: (index: Int) -> T?,
    isSelected: (T) -> Boolean,
    onSelectionChange: (select: List<T>, deselect: List<T>) -> Unit,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
): Modifier = this then DragToSelectElement(
    state = state,
    listState = listState,
    itemAt = itemAt,
    isSelected = isSelected,
    onSelectionChange = onSelectionChange,
    topInset = topInset,
    bottomInset = bottomInset,
)

private data class DragToSelectElement<T : Any>(
    val state: DragToSelectState,
    val listState: LazyListState,
    val itemAt: (Int) -> T?,
    val isSelected: (T) -> Boolean,
    val onSelectionChange: (List<T>, List<T>) -> Unit,
    val topInset: Dp,
    val bottomInset: Dp,
) : ModifierNodeElement<DragToSelectNode<T>>() {
    override fun create() = DragToSelectNode(
        state = state,
        listState = listState,
        itemAt = itemAt,
        isSelected = isSelected,
        onSelectionChange = onSelectionChange,
        topInset = topInset,
        bottomInset = bottomInset,
    )

    override fun update(node: DragToSelectNode<T>) {
        node.update(state, listState, itemAt, isSelected, onSelectionChange, topInset, bottomInset)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "dragToSelect"
        properties["topInset"] = topInset
        properties["bottomInset"] = bottomInset
    }
}

private class DragToSelectNode<T : Any>(
    private var state: DragToSelectState,
    private var listState: LazyListState,
    private var itemAt: (Int) -> T?,
    private var isSelected: (T) -> Boolean,
    private var onSelectionChange: (List<T>, List<T>) -> Unit,
    private var topInset: Dp,
    private var bottomInset: Dp,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {

    // Keyed on nothing that changes with selection: entering selection mode must not restart
    // the gesture the long press is part of.
    private val pointerInputNode = delegate(
        SuspendingPointerInputModifierNode(PointerInputEventHandler { detectDragToSelect() })
    )

    private var tracker: DragSelectionTracker<T>? = null
    private var edgeGate: AutoScrollEdgeGate? = null
    private var pointerY = 0f
    private var bandTop = 0f
    private var bandBottom = 0f
    private var autoScrollVelocity = 0f
    private var autoScrollJob: Job? = null

    fun update(
        state: DragToSelectState,
        listState: LazyListState,
        itemAt: (Int) -> T?,
        isSelected: (T) -> Boolean,
        onSelectionChange: (List<T>, List<T>) -> Unit,
        topInset: Dp,
        bottomInset: Dp,
    ) {
        this.itemAt = itemAt
        this.isSelected = isSelected
        this.onSelectionChange = onSelectionChange
        this.topInset = topInset
        this.bottomInset = bottomInset
        if (this.state !== state || this.listState !== listState) {
            this.state = state
            this.listState = listState
            pointerInputNode.resetPointerInputHandler()
        }
    }

    private suspend fun PointerInputScope.detectDragToSelect() = coroutineScope {
        val edgeZone = AutoScrollEdgeZone.toPx()
        val maxVelocity = MaxAutoScrollSpeed.toPx()
        awaitEachGesture {
            // The Initial pass runs before the rows, the list's own scrolling and any parent
            // pager, so consuming there once armed keeps all of them still until the finger lifts.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val gestureState = state
            gestureState.isPointerDown = true
            gestureState.isArmRequested = false
            pointerY = down.position.y
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    if (tracker == null && gestureState.isArmRequested) {
                        gestureState.isArmRequested = false
                        // The long press fired between events, with the finger still on the row
                        // at its last position; this event may already have moved it off the row.
                        startSelection(anchorY = pointerY, touchSlop = viewConfiguration.touchSlop)
                    }
                    pointerY = change.position.y
                    if (tracker == null) continue
                    event.changes.forEach { it.consume() }
                    selectUnderPointer()
                    updateAutoScroll(edgeZone, maxVelocity)
                }
            } finally {
                autoScrollVelocity = 0f
                autoScrollJob?.cancel()
                autoScrollJob = null
                tracker?.let { Timber.tag(LogTag).d("Drag-select ended on row %d", it.currentIndex) }
                tracker = null
                edgeGate = null
                gestureState.isPointerDown = false
                gestureState.isArmRequested = false
            }
        }
    }

    private fun startSelection(anchorY: Float, touchSlop: Float) {
        val layoutInfo = listState.layoutInfo
        val anchor = itemIndexAtY(anchorY, layoutInfo.viewportStartOffset, layoutInfo.visibleItemsInfo)
        val anchorItem = anchor?.let(itemAt)
        if (anchor == null || anchorItem == null) {
            Timber.tag(LogTag).w("Drag-select not started: no selectable row at y=%.1f", anchorY)
            return
        }
        // The long press has already toggled the anchor, so its state now is the drag's target.
        val selecting = isSelected(anchorItem)
        tracker = DragSelectionTracker(anchor, selecting)
        edgeGate = AutoScrollEdgeGate(startY = anchorY, touchSlop = touchSlop)
        Timber.tag(LogTag).d("Drag-select started on row %d, selecting=%b", anchor, selecting)
    }

    /** Extends the drag range to the row under the finger, limited to the rows on screen. */
    private fun selectUnderPointer() {
        val tracker = tracker ?: return
        updateVisibleBand()
        val y = if (bandBottom > bandTop) pointerY.coerceIn(bandTop, bandBottom - 1f) else pointerY
        val layoutInfo = listState.layoutInfo
        val index = itemIndexAtY(y, layoutInfo.viewportStartOffset, layoutInfo.visibleItemsInfo)
            ?: tracker.currentIndex
        val change = tracker.moveTo(index, itemAt, isSelected)
        if (change.isEmpty) return
        onSelectionChange(change.select, change.deselect)
        currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    /** The part of the list that is on screen and not covered by its insets. */
    private fun updateVisibleBand() {
        val coordinates = requireLayoutCoordinates()
        val density = requireDensity()
        var top = 0f
        var bottom = coordinates.size.height.toFloat()
        // Genre detail pushes its list down while the header is expanded, so the bottom of the
        // list is off screen; its overlays are laid out against the screen edge instead.
        val onScreen = coordinates.boundsInWindow()
        if (!onScreen.isEmpty) {
            top = maxOf(top, coordinates.windowToLocal(onScreen.topLeft).y)
            bottom = minOf(bottom, coordinates.windowToLocal(onScreen.bottomLeft).y)
        }
        bandTop = top + with(density) { topInset.toPx() }
        bandBottom = bottom - with(density) { bottomInset.toPx() }
    }

    private fun CoroutineScope.updateAutoScroll(edgeZone: Float, maxVelocity: Float) {
        val gate = edgeGate ?: return
        val velocity = dragSelectAutoScrollVelocity(pointerY, bandTop, bandBottom, edgeZone, maxVelocity)
        autoScrollVelocity = gate.filter(velocity, pointerY)
        if (autoScrollVelocity != 0f && autoScrollJob?.isActive != true) {
            autoScrollJob = launch { autoScroll() }
        }
    }

    /** Scrolls while the finger rests in an edge zone, selecting the rows that scroll under it. */
    private suspend fun autoScroll() {
        listState.scroll {
            var previousFrameNanos = withFrameNanos { it }
            while (autoScrollVelocity != 0f) {
                val frameNanos = withFrameNanos { it }
                val seconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                previousFrameNanos = frameNanos
                scrollBy(autoScrollVelocity * seconds)
                selectUnderPointer()
            }
        }
    }
}
