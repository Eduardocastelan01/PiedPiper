package com.lostf1sh.pixelplayeross.presentation.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.presentation.components.subcomps.EnhancedSongListItem
import com.lostf1sh.pixelplayeross.presentation.viewmodel.MultiSelectionStateHolder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holding a song and dragging selects every song the finger passes over, and the list stays
 * still until the finger lifts. Taps and plain swipes keep their usual meaning.
 */
@RunWith(AndroidJUnit4::class)
class DragToSelectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val holder = MultiSelectionStateHolder()
    private val songs = List(40) { index ->
        Song.emptySong().copy(id = "song-$index", title = "Song $index", artist = "Artist", album = "Album")
    }
    private lateinit var listState: LazyListState

    private fun setList() {
        composeRule.setContent {
            MaterialTheme {
                val selectedIds by holder.selectedSongIds.collectAsState()
                val isSelectionMode by holder.isSelectionMode.collectAsState()
                val dragSelectState = rememberDragToSelectState()
                listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(LIST_TAG)
                        .dragToSelect(
                            state = dragSelectState,
                            listState = listState,
                            itemAt = { index -> songs.getOrNull(index) },
                            isSelected = { song -> holder.isSelected(song.id) },
                            onSelectionChange = holder::applyDragSelection,
                        ),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(songs, key = { it.id }) { song ->
                        EnhancedSongListItem(
                            modifier = Modifier.testTag(song.id),
                            song = song,
                            isPlaying = false,
                            isSelected = song.id in selectedIds,
                            selectionIndex = holder.getSelectionIndex(song.id),
                            isSelectionMode = isSelectionMode,
                            onLongPress = {
                                holder.toggleSelection(song)
                                dragSelectState.onItemLongPressed()
                            },
                            onMoreOptionsClick = {},
                            onClick = {},
                        )
                    }
                }
            }
        }
    }

    // Row positions in the list's own coordinates; read them before a selection scales the rows.
    private fun rowCenterY(index: Int): Float {
        val list = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(songs[index].id).fetchSemanticsNode().boundsInRoot
        return row.center.y - list.top
    }

    private fun gapBelowRowY(index: Int): Float {
        val list = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag(songs[index].id).fetchSemanticsNode().boundsInRoot
        val next = composeRule.onNodeWithTag(songs[index + 1].id).fetchSemanticsNode().boundsInRoot
        return (row.bottom + next.top) / 2f - list.top
    }

    private fun selectedIds() = holder.selectedSongs.value.map { it.id }

    @Test
    fun longPressThenDragSelectsTheRowsPassedOverInOrderWithoutScrolling() {
        setList()
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val row0 = rowCenterY(0)
        val gap = gapBelowRowY(0)
        val row4 = rowCenterY(4)

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, row0))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            // A fast first move that lands between two rows must not lose the drag.
            moveTo(Offset(x, gap))
            moveTo(Offset(x, (gap + row4) / 2f))
            moveTo(Offset(x, row4))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-1", "song-2", "song-3", "song-4").inOrder()
            assertThat(listState.firstVisibleItemIndex).isEqualTo(0)
            assertThat(listState.firstVisibleItemScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun draggingBackGivesRowsBackTheirStateAndPlaceInTheOrder() {
        setList()
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val rows = (0..3).map(::rowCenterY)
        composeRule.runOnIdle { (0..3).forEach { holder.toggleSelection(songs[it]) } }

        // Holding the selected song 0 deselects it, so the drag deselects too.
        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, rows[0]))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[2]))
        }
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-3") }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            moveTo(Offset(x, rows[1]))
            moveTo(Offset(x, rows[0]))
            up()
        }
        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-1", "song-2", "song-3").inOrder()
        }
    }

    @Test
    fun tapsAndPlainSwipesInSelectionModeDoNotDragSelect() {
        setList()
        composeRule.runOnIdle { holder.toggleSelection(songs[0]) }
        val x = composeRule.onNodeWithTag(LIST_TAG).fetchSemanticsNode().size.width / 2f
        val row2 = rowCenterY(2)
        val row8 = rowCenterY(8)

        composeRule.onNodeWithTag(songs[2].id).performTouchInput { click() }
        composeRule.runOnIdle { assertThat(selectedIds()).containsExactly("song-0", "song-2").inOrder() }

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput {
            down(Offset(x, row8))
            moveTo(Offset(x, (row8 + row2) / 2f))
            moveTo(Offset(x, row2))
            up()
        }

        composeRule.runOnIdle {
            assertThat(selectedIds()).containsExactly("song-0", "song-2").inOrder()
            assertThat(listState.firstVisibleItemIndex).isGreaterThan(0)
        }
    }

    private companion object {
        const val LIST_TAG = "drag_select_list"
    }
}
