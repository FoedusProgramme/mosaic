package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.remember
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.modifier.composed

/** What happened to the mouse pointer, see [onPointerEvent]. */
public enum class PointerEventType {
	Press,
	Release,

	/** The pointer moved without a button held down. */
	Move,

	/** The pointer moved with a button held down. */
	Drag,

	/** The wheel turned, by [PointerEvent.scrollDelta]. */
	Scroll,

	/** The pointer moved onto this node. */
	Enter,

	/** The pointer moved off this node. */
	Exit,
}

public enum class PointerButton {
	None,
	Left,
	Middle,
	Right,
}

/**
 * An event of the mouse pointer at [x] and [y], relative to the top left of the node which receives
 * it. Coordinates can be outside of the node for drags and releases, which go to the nodes the
 * press went to.
 */
@Poko
public class PointerEvent(
	public val type: PointerEventType,
	public val x: Int,
	public val y: Int,
	public val button: PointerButton = PointerButton.None,
	/** Rows to scroll by for [PointerEventType.Scroll]: negative upwards, positive downwards. */
	public val scrollDelta: Int = 0,
	public val shift: Boolean = false,
	public val alt: Boolean = false,
	public val ctrl: Boolean = false,
	/** Whether the pointer is over the node, which it need not be for drags and releases. */
	public val isWithinBounds: Boolean = true,
)

/**
 * Receives events of the mouse pointer over this node. Events go to the innermost node under the
 * pointer first, and to its ancestors until one returns true. Drags and releases go to the nodes
 * which the press went to.
 *
 * Mouse events are only reported once enabled with the `mouse` parameter of `runMosaic`, which is
 * the default on the alternate screen.
 */
public fun Modifier.onPointerEvent(onPointerEvent: (event: PointerEvent) -> Boolean): Modifier = this then PointerModifierElement(onPointerEvent)

/** Calls [onClick] when the left button is pressed and released over this node. */
@Suppress("ktlint:compose:modifier-composed-check") // Mosaic has no Modifier.Node to keep state.
public fun Modifier.onClick(onClick: () -> Unit): Modifier = composed {
	val click = remember { ClickState() }
	click.onClick = onClick
	onPointerEvent(click::onPointerEvent)
}

/** Calls [onHoverChange] when the pointer moves onto this node, and when it moves off it. */
public fun Modifier.onHover(onHoverChange: (hovered: Boolean) -> Unit): Modifier = onPointerEvent {
	when (it.type) {
		PointerEventType.Enter -> onHoverChange(true)
		PointerEventType.Exit -> onHoverChange(false)
		else -> return@onPointerEvent false
	}
	// Other nodes under the pointer get their own enter and exit events.
	false
}

private class ClickState {
	var onClick: () -> Unit = {}
	private var pressed = false

	fun onPointerEvent(event: PointerEvent): Boolean {
		if (event.button != PointerButton.Left) return false
		when (event.type) {
			PointerEventType.Press -> pressed = true

			PointerEventType.Release -> {
				if (!pressed) return false
				pressed = false
				// The release goes to this node even outside of it, which cancels the click.
				if (event.isWithinBounds) onClick()
			}

			else -> return false
		}
		return true
	}
}

internal class PointerModifierElement(
	val onPointerEvent: (PointerEvent) -> Boolean,
) : Modifier.Element {
	override fun toString() = "OnPointerEvent"
}

/**
 * Sends the pointer events of the terminal to the nodes of [roots], which are the root of the
 * composition followed by its popups.
 */
internal class PointerDispatcher(
	private val roots: () -> List<MosaicNode>,
	private val focusOwner: FocusOwner,
) {
	/** The nodes the last press went to, which also get drags and the release. */
	private var captured: List<MosaicNode>? = null
	private var hovered: List<MosaicNode> = emptyList()

	fun dispatch(
		type: PointerEventType,
		x: Int,
		y: Int,
		button: PointerButton,
		scrollDelta: Int = 0,
		shift: Boolean = false,
		alt: Boolean = false,
		ctrl: Boolean = false,
	): Boolean {
		val path = hitTest(x, y)
		updateHover(path, x, y)

		val targets = when (type) {
			PointerEventType.Drag, PointerEventType.Release -> captured ?: path
			else -> path
		}
		if (type == PointerEventType.Press) {
			captured = path
			if (button == PointerButton.Left) focusClicked(path)
		}
		if (type == PointerEventType.Release) captured = null

		for (node in targets.asReversed()) {
			if (node.pointerHandlers.isEmpty()) continue
			val within = x >= node.x && y >= node.y && x < node.x + node.width && y < node.y + node.height
			val event = PointerEvent(type, x - node.x, y - node.y, button, scrollDelta, shift, alt, ctrl, within)
			for (handler in node.pointerHandlers) {
				if (handler(event)) return true
			}
		}
		return false
	}

	/** Moves the focus to the innermost focusable node which was clicked. */
	private fun focusClicked(path: List<MosaicNode>) {
		for (node in path.asReversed()) {
			val target = node.focusTargets.firstOrNull { it.canFocus } ?: continue
			focusOwner.focus(target)
			return
		}
	}

	private fun updateHover(path: List<MosaicNode>, x: Int, y: Int) {
		val previous = hovered
		hovered = path.filter { it.pointerHandlers.isNotEmpty() }
		for (node in previous) {
			if (node in hovered) continue
			val event = PointerEvent(PointerEventType.Exit, x - node.x, y - node.y, isWithinBounds = false)
			node.pointerHandlers.forEach { it(event) }
		}
		for (node in hovered) {
			if (node in previous) continue
			val event = PointerEvent(PointerEventType.Enter, x - node.x, y - node.y)
			node.pointerHandlers.forEach { it(event) }
		}
	}

	/** The nodes from a root to the innermost one at [x] and [y], or an empty list. */
	private fun hitTest(x: Int, y: Int): List<MosaicNode> {
		val roots = roots()
		val path = ArrayList<MosaicNode>()
		// The last popup is drawn on top of everything else.
		for (index in roots.indices.reversed()) {
			if (roots[index].hitTest(x, y, path)) return path
		}
		return path
	}
}

/** Adds the nodes from this one to the innermost one at [x] and [y] to [path]. */
private fun MosaicNode.hitTest(x: Int, y: Int, path: MutableList<MosaicNode>): Boolean {
	val inside = width > 0 && height > 0 && x >= this.x && y >= this.y && x < this.x + width && y < this.y + height
	// Children of nodes which clip them can only be hit within the bounds of those nodes.
	if (!inside && clipsChildren) return false
	path += this
	// Later children are drawn over earlier ones.
	for (index in children.indices.reversed()) {
		if (children[index].hitTest(x, y, path)) return true
	}
	if (inside) return true
	path.removeAt(path.lastIndex)
	return false
}
