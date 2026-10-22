package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.unit.Constraints

/** The scroll position of [verticalScroll] or [horizontalScroll]. */
@Stable
public class ScrollState(initial: Int = 0) {
	/** How many rows or columns the content is scrolled by. */
	public var value: Int by mutableIntStateOf(initial)
		private set

	/** The largest [value], which shows the end of the content. */
	public var maxValue: Int by mutableIntStateOf(Int.MAX_VALUE)
		internal set

	/** The number of rows or columns visible at once. */
	public var viewportSize: Int by mutableIntStateOf(0)
		internal set

	public val canScrollBackward: Boolean get() = value > 0
	public val canScrollForward: Boolean get() = value < maxValue

	public fun scrollTo(value: Int) {
		this.value = value.coerceIn(0, maxValue)
	}

	public fun scrollBy(delta: Int) {
		scrollTo(value + delta)
	}

	/** Scrolls to the end, also once the content becomes longer during the next layout. */
	public fun scrollToEnd() {
		value = Int.MAX_VALUE
	}
}

@Composable
public fun rememberScrollState(initial: Int = 0): ScrollState = remember { ScrollState(initial) }

/**
 * Lets the content be taller than this node, showing the part [state] is scrolled to and clipping
 * the rest. Scroll by changing [state], for example in response to key events.
 */
public fun Modifier.verticalScroll(state: ScrollState): Modifier = clipToBounds() then ScrollModifier(state, vertical = true)

/**
 * Lets the content be wider than this node, showing the part [state] is scrolled to and clipping
 * the rest. Scroll by changing [state], for example in response to key events.
 */
public fun Modifier.horizontalScroll(state: ScrollState): Modifier = clipToBounds() then ScrollModifier(state, vertical = false)

private class ScrollModifier(
	private val state: ScrollState,
	private val vertical: Boolean,
) : LayoutModifier {
	override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
		val childConstraints = if (vertical) {
			constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
		} else {
			constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
		}
		val placeable = measurable.measure(childConstraints)
		val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
		val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)

		val viewport = if (vertical) height else width
		val content = if (vertical) placeable.height else placeable.width
		val maxValue = (content - viewport).coerceAtLeast(0)
		if (state.maxValue != maxValue) state.maxValue = maxValue
		if (state.viewportSize != viewport) state.viewportSize = viewport
		val scroll = state.value.coerceIn(0, maxValue)

		return layout(width, height) {
			if (vertical) {
				placeable.place(0, -scroll)
			} else {
				placeable.place(-scroll, 0)
			}
		}
	}

	override fun toString() = if (vertical) "VerticalScroll" else "HorizontalScroll"
}
