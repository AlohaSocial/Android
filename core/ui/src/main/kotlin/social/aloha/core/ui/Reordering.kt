// SPDX-FileCopyrightText: 2026 Aloha Social contributors
// SPDX-License-Identifier: MIT

package social.aloha.core.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A list put in order by dragging. The item dragged follows the finger and changes places with a neighbour
 * once it passes half of it; the order is told when it is let go, and kept on screen until the list given
 * changes its order. Items are followed by their key, so one that changes while it is dragged, an upload
 * moving on, keeps its place. [gap] is the space between two items.
 */
@Stable
public class Reordering<T> internal constructor(internal val key: (T) -> Any, private val gap: Float) {
    internal var latest: List<T> = emptyList()
    internal var onOrder: (List<T>) -> Unit = {}
    internal val heights = HashMap<Any, Int>()
    private var moved by mutableStateOf<List<Any>?>(null)
    private var base: List<Any>? = null

    /** The items as they stand, the dragged one where it is now. */
    public val order: List<T>
        get() {
            val keys = moved?.takeIf { base == latest.map(key) } ?: return latest
            val items = latest.associateBy(key)
            return keys.mapNotNull { items[it] }
        }

    /** The key of the item being dragged; null while none is. */
    public var dragged: Any? by mutableStateOf(null)
        private set

    /** How far the dragged item is off its place. */
    public var offset: Float by mutableFloatStateOf(0f)
        private set

    public fun start(item: T): Unit = startAt(key(item))

    internal fun startAt(itemKey: Any) {
        moved = order.map(key)
        base = latest.map(key)
        dragged = itemKey
        offset = 0f
    }

    public fun drag(delta: Float) {
        val list = moved ?: return
        offset += delta
        val index = list.indexOf(dragged)
        val next = list.getOrNull(index + 1)?.let { heights[it] }
        val previous = list.getOrNull(index - 1)?.let { heights[it] }
        if (next != null && offset > next / 2f) {
            moved = list.swapped(index, index + 1)
            offset -= next + gap
        } else if (previous != null && offset < -previous / 2f) {
            moved = list.swapped(index, index - 1)
            offset += previous + gap
        }
    }

    public fun stop() {
        if (moved != null && dragged != null) onOrder(order)
        dragged = null
        offset = 0f
    }

    /** Moves the item at [from] to [to] at once, as a screen reader's action does. */
    public fun move(from: Int, to: Int) {
        moved = order.map(key).swapped(from, to)
        base = latest.map(key)
        onOrder(order)
    }

    private fun <K> List<K>.swapped(from: Int, to: Int): List<K> = toMutableList().apply { add(to, removeAt(from)) }
}

/** The order [items] are dragged into, told to [onOrder] once an item is let go. */
@Composable
public fun <T> rememberReordering(
    items: List<T>,
    key: (T) -> Any,
    gap: Dp = 0.dp,
    onOrder: (List<T>) -> Unit,
): Reordering<T> {
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    val reordering = remember(gapPx) { Reordering(key, gapPx) }
    reordering.latest = items
    reordering.onOrder = onOrder
    return reordering
}

/** Where [item] is drawn: lifted above the others, and following the finger, while it is dragged. */
public fun <T> Modifier.reorderItem(reordering: Reordering<T>, item: T): Modifier {
    val key = reordering.key(item)
    val moving = reordering.dragged == key
    return this
        .onSizeChanged { reordering.heights[key] = it.height }
        .zIndex(if (moving) 1f else 0f)
        .graphicsLayer { translationY = if (moving) reordering.offset else 0f }
}

/** A handle that drags [item] up and down as soon as it is touched. */
@Composable
public fun <T> Modifier.dragHandle(reordering: Reordering<T>, item: T): Modifier {
    val key = reordering.key(item)
    return draggable(
        rememberDraggableState { reordering.drag(it) },
        Orientation.Vertical,
        onDragStarted = { reordering.startAt(key) },
        onDragStopped = { reordering.stop() },
    )
}

/** [item] dragged up and down after a long press on it. */
public fun <T> Modifier.longPressDrag(reordering: Reordering<T>, item: T): Modifier {
    // keyed on the item's key, not the item: one changing while it is dragged must not end the gesture
    val key = reordering.key(item)
    return pointerInput(reordering, key) { press(reordering, key) }
}

private suspend fun <T> PointerInputScope.press(reordering: Reordering<T>, key: Any) {
    detectDragGesturesAfterLongPress(
        onDragStart = { reordering.startAt(key) },
        onDragEnd = reordering::stop,
        onDragCancel = reordering::stop,
    ) { change, amount ->
        change.consume()
        reordering.drag(amount.y)
    }
}

/** A screen reader's way to move the item at [index] up or down, as dragging does. */
@Composable
public fun <T> Reordering<T>.moves(index: Int): List<CustomAccessibilityAction> {
    val size = order.size
    return listOfNotNull(
        CustomAccessibilityAction(stringResource(R.string.reorder_move_up)) {
            move(index, index - 1)
            true
        }.takeIf { index > 0 },
        CustomAccessibilityAction(stringResource(R.string.reorder_move_down)) {
            move(index, index + 1)
            true
        }.takeIf { index < size - 1 },
    )
}
