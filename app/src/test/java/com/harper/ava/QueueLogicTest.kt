package com.harper.ava

import com.harper.ava.core.util.dragTargetIndex
import com.harper.ava.core.util.insertPlayNext
import com.harper.ava.core.util.moveItem
import com.harper.ava.core.util.planReorderMoves
import com.harper.ava.core.util.QueueMove
import com.harper.ava.core.util.removeItemAt
import com.harper.ava.core.util.shuffleKeepingFirst
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueLogicTest {

    @Test
    fun `moveItem moves forward and backward`() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveItem(list, 0, 2))
        assertEquals(listOf("a", "d", "b", "c"), moveItem(list, 3, 1))
    }

    @Test
    fun `moveItem with out-of-range indices returns the list unchanged`() {
        val list = listOf("a", "b")
        assertEquals(list, moveItem(list, 5, 0))
        assertEquals(list, moveItem(list, 0, 5))
        assertEquals(list, moveItem(list, 1, 1))
    }

    @Test
    fun `removeItemAt removes only the requested index`() {
        assertEquals(listOf("a", "c"), removeItemAt(listOf("a", "b", "c"), 1))
        assertEquals(listOf("a", "b"), removeItemAt(listOf("a", "b"), 9))
    }

    @Test
    fun `insertPlayNext inserts right after the current index`() {
        assertEquals(listOf("a", "x", "b"), insertPlayNext(listOf("a", "b"), 0, "x"))
        assertEquals(listOf("a", "b", "x"), insertPlayNext(listOf("a", "b"), 1, "x"))
        assertEquals(listOf("x"), insertPlayNext(emptyList(), -1, "x"))
    }

    @Test
    fun `shuffleKeepingFirst keeps the playing song first and the same set`() {
        val list = listOf("now", "a", "b", "c", "d")
        val shuffled = shuffleKeepingFirst(list, Random(42))
        assertEquals("now", shuffled.first())
        assertEquals(list.sorted(), shuffled.sorted())
        assertEquals(list.size, shuffled.size)
    }

    @Test
    fun `shuffleKeepingFirst leaves tiny lists alone`() {
        assertEquals(listOf("only"), shuffleKeepingFirst(listOf("only"), Random(1)))
        assertEquals(emptyList<String>(), shuffleKeepingFirst(emptyList<String>(), Random(1)))
    }

    @Test
    fun `dragTargetIndex converts drag distance to row steps`() {
        // 72dp rows: dragging 1.5 rows down from index 1 lands on index 3 (rounded).
        assertEquals(3, dragTargetIndex(1, 108f, 72f, 5))
        // Dragging up past the top clamps to 0.
        assertEquals(0, dragTargetIndex(1, -500f, 72f, 5))
        // Dragging down past the end clamps to the last index.
        assertEquals(4, dragTargetIndex(3, 500f, 72f, 5))
        // No drag keeps the index.
        assertEquals(2, dragTargetIndex(2, 0f, 72f, 5))
        // A small drag (less than half a row) does not move.
        assertEquals(2, dragTargetIndex(2, 20f, 72f, 5))
    }

    @Test
    fun `dragTargetIndex with an empty list stays put`() {
        assertEquals(0, dragTargetIndex(0, 100f, 72f, 0))
        assertTrue(dragTargetIndex(0, 100f, 72f, 0) >= 0)
    }

    // Improve loop, run 15: the queue may hold the same song twice, and
    // a bare indexOf finds the first copy — which can be the playing
    // song sitting in the untouched head. planReorderMoves must only
    // move items at or after fromPosition.

    @Test
    fun `planReorderMoves turns a shuffled tail into the target order`() {
        val current = listOf("a", "b", "c", "d")
        val target = listOf("a", "b", "d", "c")
        val moves = planReorderMoves(current, target, fromPosition = 2)
        assertEquals(listOf(QueueMove(3, 2)), moves)
        assertEquals(target, applyMoves(current, moves))
    }

    @Test
    fun `planReorderMoves never moves the playing song when a duplicate sits in the head`() {
        // The exact run-15 bug: queue [A, B, A, C], B playing at 1, tail
        // shuffled to [C, A]. A bare indexOf(A) finds the A at 0 — in
        // the untouched head — and would move the playing song's
        // neighbour out of place; the plan must only move (3, 2).
        val current = listOf("A", "B", "A", "C")
        val target = listOf("A", "B", "C", "A")
        val moves = planReorderMoves(current, target, fromPosition = 2)
        assertEquals(listOf(QueueMove(3, 2)), moves)
        assertTrue(moves.all { it.fromIndex >= 2 && it.toIndex >= 2 })
        assertEquals(target, applyMoves(current, moves))
    }

    @Test
    fun `planReorderMoves picks the unfixed copy when a duplicate is already fixed`() {
        // Queue [A, B, A], A playing at 0, tail shuffled to [A, B]:
        // position 1 already wants A, but the A at 0 is the head — the
        // unfixed copy at 2 is the one that must move.
        val current = listOf("A", "B", "A")
        val target = listOf("A", "A", "B")
        val moves = planReorderMoves(current, target, fromPosition = 1)
        assertEquals(listOf(QueueMove(2, 1)), moves)
        assertTrue(moves.all { it.fromIndex >= 1 })
        assertEquals(target, applyMoves(current, moves))
    }

    @Test
    fun `planReorderMoves is a no-op when the order already matches`() {
        val current = listOf("a", "b", "c")
        assertEquals(emptyList<QueueMove>(), planReorderMoves(current, current, fromPosition = 1))
    }

    @Test
    fun `planReorderMoves with mismatched sizes plans nothing`() {
        assertEquals(
            emptyList<QueueMove>(),
            planReorderMoves(listOf("a", "b"), listOf("a"), fromPosition = 0),
        )
    }

    private fun <T> applyMoves(list: List<T>, moves: List<QueueMove>): List<T> {
        var working = list
        for (move in moves) working = moveItem(working, move.fromIndex, move.toIndex)
        return working
    }
}
