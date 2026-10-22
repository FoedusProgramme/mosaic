package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlin.math.max
import kotlin.math.min
import org.unitedsoftware.mosaic.LocalLayoutInvalidator
import org.unitedsoftware.mosaic.MosaicNodeApplier
import org.unitedsoftware.mosaic.layout.DebugPolicy
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.layout.Placeable
import org.unitedsoftware.mosaic.layout.clipToBounds
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.modifier.materialize
import org.unitedsoftware.mosaic.ui.unit.Constraints

/** Receives the items of a [LazyColumn]. */
@LayoutScopeMarker
public interface LazyListScope {
	/**
	 * Adds a single item.
	 *
	 * @param key A key which is unique among the items of the list. The state remembered by an item
	 * stays with its key, and the list keeps showing the same item when others are added or removed
	 * before it. Without a key, the position of the item is its key.
	 */
	public fun item(key: Any? = null, content: @Composable () -> Unit)

	/**
	 * Adds [count] items.
	 *
	 * @param key Returns the unique key of the item at an index, see [item].
	 */
	public fun items(
		count: Int,
		key: ((index: Int) -> Any)? = null,
		itemContent: @Composable (index: Int) -> Unit,
	)
}

/** Adds an item for each of [items]. */
public inline fun <T> LazyListScope.items(
	items: List<T>,
	noinline key: ((item: T) -> Any)? = null,
	crossinline itemContent: @Composable (item: T) -> Unit,
) {
	items(items.size, if (key != null) { index: Int -> key(items[index]) } else null) {
		itemContent(items[it])
	}
}

/** Adds an item for each of [items], passing its index as well. */
public inline fun <T> LazyListScope.itemsIndexed(
	items: List<T>,
	noinline key: ((index: Int, item: T) -> Any)? = null,
	crossinline itemContent: @Composable (index: Int, item: T) -> Unit,
) {
	items(items.size, if (key != null) { index: Int -> key(index, items[index]) } else null) {
		itemContent(it, items[it])
	}
}

/** The scroll position of a [LazyColumn]. */
@Stable
public class LazyListState(
	firstVisibleItemIndex: Int = 0,
	firstVisibleItemScrollOffset: Int = 0,
) {
	/** The index of the first item which is at least partly visible. */
	public var firstVisibleItemIndex: Int by mutableIntStateOf(firstVisibleItemIndex)
		private set

	/** How many rows of the first visible item are scrolled above the top of the list. */
	public var firstVisibleItemScrollOffset: Int by mutableIntStateOf(firstVisibleItemScrollOffset)
		private set

	/** The index of the last item which is at least partly visible, or -1 without items. */
	public var lastVisibleItemIndex: Int by mutableIntStateOf(-1)
		private set

	/** The number of items as of the last layout. */
	public var totalItemsCount: Int by mutableIntStateOf(0)
		private set

	/** The number of rows the list showed in the last layout. */
	public var viewportSize: Int by mutableIntStateOf(0)
		private set

	public var canScrollBackward: Boolean by mutableStateOf(false)
		private set

	public var canScrollForward: Boolean by mutableStateOf(false)
		private set

	/** Rows to scroll by during the next layout, which knows the heights of the items. */
	private var pendingScrollDelta by mutableIntStateOf(0)

	/** The key of the first visible item, to find it again once items before it change. */
	internal var firstVisibleItemKey: Any? = null

	/** Scrolls so that the item at [index] is at the top, with [scrollOffset] of its rows above it. */
	public fun scrollToItem(index: Int, scrollOffset: Int = 0) {
		firstVisibleItemIndex = index.coerceAtLeast(0)
		firstVisibleItemScrollOffset = scrollOffset.coerceAtLeast(0)
		firstVisibleItemKey = null
		pendingScrollDelta = 0
	}

	/** Scrolls by [delta] rows, towards the end when positive. Applied during the next layout. */
	public fun scrollBy(delta: Int) {
		pendingScrollDelta = (pendingScrollDelta.toLong() + delta)
			.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
			.toInt()
	}

	/** Scrolls to the end of the list as of the next layout. */
	public fun scrollToEnd() {
		scrollToItem(Int.MAX_VALUE, Int.MAX_VALUE)
	}

	internal fun consumeScrollDelta(): Int {
		val delta = pendingScrollDelta
		if (delta != 0) pendingScrollDelta = 0
		return delta
	}

	internal fun update(
		firstIndex: Int,
		firstOffset: Int,
		firstKey: Any?,
		lastIndex: Int,
		count: Int,
		viewport: Int,
		canScrollForward: Boolean,
	) {
		firstVisibleItemIndex = firstIndex
		firstVisibleItemScrollOffset = firstOffset
		firstVisibleItemKey = firstKey
		lastVisibleItemIndex = lastIndex
		totalItemsCount = count
		viewportSize = viewport
		canScrollBackward = firstIndex > 0 || firstOffset > 0
		this.canScrollForward = canScrollForward
	}
}

@Composable
public fun rememberLazyListState(
	firstVisibleItemIndex: Int = 0,
	firstVisibleItemScrollOffset: Int = 0,
): LazyListState = remember {
	LazyListState(firstVisibleItemIndex, firstVisibleItemScrollOffset)
}

/**
 * A column which only composes and lays out the items which are visible, so that it can hold more
 * items than would be practical to compose at once, like the messages of a long conversation.
 * Content outside of its bounds is clipped.
 *
 * The column is as tall as its items, up to the maximum height its constraints allow. Scroll it
 * with [state], for example in response to key events.
 */
@Composable
public fun LazyColumn(
	modifier: Modifier = Modifier,
	state: LazyListState = rememberLazyListState(),
	content: LazyListScope.() -> Unit,
) {
	val compositionContext = rememberCompositionContext()
	val invalidateLayout = LocalLayoutInvalidator.current
	val items = LazyListItems().apply(content)
	val policy = remember(state, compositionContext, invalidateLayout) {
		LazyColumnMeasurePolicy(state, compositionContext, invalidateLayout)
	}
	DisposableEffect(policy) {
		onDispose {
			policy.dispose()
		}
	}

	val materializedModifier = currentComposer.materialize(modifier.clipToBounds())
	ComposeNode<MosaicNode, Applier<Any>>(
		factory = NodeFactory,
		update = {
			set(policy) {
				policy.node = this
				measurePolicy = policy
				debugPolicy = policy
			}
			set(items) { policy.items = it }
			set(materializedModifier, SetModifier)
		},
	)
}

internal class LazyListItems : LazyListScope {
	private class Interval(
		val start: Int,
		val count: Int,
		val key: ((Int) -> Any)?,
		val content: @Composable (Int) -> Unit,
	)

	private val intervals = ArrayList<Interval>()

	var count = 0
		private set

	override fun item(key: Any?, content: @Composable () -> Unit) {
		items(1, if (key != null) { _ -> key } else null) { content() }
	}

	override fun items(count: Int, key: ((index: Int) -> Any)?, itemContent: @Composable (index: Int) -> Unit) {
		require(count >= 0) { "count must not be negative: $count" }
		if (count == 0) return
		intervals += Interval(this.count, count, key, itemContent)
		this.count += count
	}

	private fun intervalOf(index: Int): Interval {
		var low = 0
		var high = intervals.lastIndex
		while (low < high) {
			val middle = (low + high + 1) / 2
			if (intervals[middle].start <= index) low = middle else high = middle - 1
		}
		return intervals[low]
	}

	fun keyOf(index: Int): Any {
		val interval = intervalOf(index)
		return interval.key?.invoke(index - interval.start) ?: DefaultKey(index)
	}

	fun contentOf(index: Int): @Composable () -> Unit {
		val interval = intervalOf(index)
		val localIndex = index - interval.start
		return { interval.content(localIndex) }
	}

	fun indexOf(key: Any): Int {
		if (key is DefaultKey) return key.index.takeIf { it < count } ?: -1
		for (interval in intervals) {
			val keyOf = interval.key ?: continue
			for (localIndex in 0 until interval.count) {
				if (keyOf(localIndex) == key) return interval.start + localIndex
			}
		}
		return -1
	}

	/** Items without a key use their index, which no key passed in can be equal to. */
	private data class DefaultKey(val index: Int)
}

private class LazyColumnMeasurePolicy(
	private val state: LazyListState,
	private val compositionContext: CompositionContext,
	private val invalidateLayout: () -> Unit,
) : MeasurePolicy,
	DebugPolicy {
	lateinit var node: MosaicNode

	var items = LazyListItems()
		set(value) {
			field = value
			for (item in composedItems.values) {
				item.stale = true
			}
		}

	private class ComposedItem(val root: MosaicNode, val composition: Composition) {
		var stale = true
	}

	private val composedItems = LinkedHashMap<Any, ComposedItem>()

	/** Changes which subcomposing itself applies are part of the current layout already. */
	private var measuring = false

	private fun subcompose(index: Int): MosaicNode {
		val key = items.keyOf(index)
		val item = composedItems.getOrPut(key) {
			val applier = MosaicNodeApplier {
				if (!measuring) invalidateLayout()
			}
			ComposedItem(applier.root, Composition(applier, compositionContext))
		}
		if (item.stale) {
			item.stale = false
			// Reads while composing the item belong to its own composition, not to this layout.
			Snapshot.withoutReadObservation {
				item.composition.setContent(items.contentOf(index))
			}
		}
		return item.root
	}

	override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
		measuring = true
		try {
			return measureItems(constraints)
		} finally {
			measuring = false
		}
	}

	private fun MeasureScope.measureItems(constraints: Constraints): MeasureResult {
		val count = items.count
		val viewport = constraints.maxHeight.toLong()
		val itemConstraints = Constraints(maxWidth = constraints.maxWidth)
		val measured = HashMap<Int, Placeable>()
		fun measureItem(index: Int): Placeable = measured.getOrPut(index) {
			subcompose(index).measure(itemConstraints)
		}

		val delta = state.consumeScrollDelta()
		var first = state.firstVisibleItemIndex
		// Keep showing the same item when items before it were added or removed.
		val firstKey = state.firstVisibleItemKey
		if (firstKey != null && (first >= count || items.keyOf(first) != firstKey)) {
			val index = items.indexOf(firstKey)
			if (index != -1) first = index
		}
		first = first.coerceIn(0, max(count - 1, 0))
		var offset = state.firstVisibleItemScrollOffset.toLong() + delta

		val visible = ArrayList<Placeable>()
		var top = 0L
		var lastIndex = -1
		var canScrollForward = false
		if (count > 0) {
			// Move the first item back while scrolled above it, then forward past hidden items.
			while (offset < 0 && first > 0) {
				first--
				offset += measureItem(first).height
			}
			if (offset < 0) offset = 0
			while (first < count - 1 && offset >= measureItem(first).height) {
				offset -= measureItem(first).height
				first++
			}

			// At the end of the list, scroll back to fill the viewport.
			var bottom = -offset
			var index = first
			while (index < count && bottom < viewport) {
				bottom += measureItem(index).height
				index++
			}
			var gap = viewport - bottom
			if (index == count && gap > 0 && viewport != Constraints.Infinity.toLong()) {
				val fromOffset = min(gap, offset)
				offset -= fromOffset
				gap -= fromOffset
				while (gap > 0 && first > 0) {
					first--
					val height = measureItem(first).height.toLong()
					val take = min(gap, height)
					offset = height - take
					gap -= take
				}
			}

			top = -offset
			bottom = top
			index = first
			while (index < count && bottom < viewport) {
				val placeable = measureItem(index)
				visible += placeable
				bottom += placeable.height
				index++
			}
			lastIndex = index - 1
			canScrollForward = index < count || bottom > viewport
		}

		// Release the items which are no longer visible.
		val visibleKeys = (first..lastIndex).mapTo(HashSet()) { items.keyOf(it) }
		val iterator = composedItems.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			if (entry.key !in visibleKeys) {
				entry.value.composition.dispose()
				iterator.remove()
			}
		}
		node.children.clear()
		for (index in first..lastIndex) {
			node.children += composedItems.getValue(items.keyOf(index)).root
		}

		val contentHeight = (top + visible.sumOf { it.height.toLong() }).coerceAtMost(viewport)
		val height = contentHeight.toInt().coerceIn(constraints.minHeight, constraints.maxHeight)
		val width = (visible.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)

		state.update(
			firstIndex = first,
			firstOffset = offset.toInt(),
			firstKey = if (count > 0) items.keyOf(first) else null,
			lastIndex = lastIndex,
			count = count,
			viewport = height,
			canScrollForward = canScrollForward,
		)

		return layout(width, height) {
			var y = top.toInt()
			for (placeable in visible) {
				placeable.place(0, y)
				y += placeable.height
			}
		}
	}

	fun dispose() {
		for (item in composedItems.values) {
			item.composition.dispose()
		}
		composedItems.clear()
	}

	override fun MosaicNode.renderDebug(): String = buildString {
		append("LazyColumn() x=$x y=$y w=$width h=$height")
		children.joinTo(this, separator = "") {
			"\n" + it.toString().prependIndent("  ")
		}
	}
}
