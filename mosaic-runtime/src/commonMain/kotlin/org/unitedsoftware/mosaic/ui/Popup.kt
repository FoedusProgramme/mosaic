package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import org.unitedsoftware.mosaic.LocalLayoutInvalidator
import org.unitedsoftware.mosaic.MosaicNodeApplier
import org.unitedsoftware.mosaic.TextSurface
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.MosaicNode
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntOffset
import org.unitedsoftware.mosaic.ui.unit.IntSize

/**
 * Shows [content] on top of everything else, like a completion menu or a dialog.
 *
 * The popup does not take up space in the layout it is placed in. It is positioned at the point
 * where it is placed instead, moved by [offset], with [alignment] choosing which point of the
 * popup is there: [Alignment.TopStart] extends it down and to the end, while
 * [Alignment.BottomStart] extends it up, for example above a text field at the bottom of the
 * screen. It is kept within the width of the terminal, and on the alternate screen within its
 * height too. The output grows to make room for a popup below it otherwise.
 *
 * Popups are drawn after the rest of the content and the popups composed before them, over a
 * blank area of their size, and are not clipped by the nodes around them. Key and paste events go
 * to the focused node first, and otherwise to the last popup first.
 */
@Composable
public fun Popup(
	alignment: Alignment = Alignment.TopStart,
	offset: IntOffset = IntOffset.Zero,
	content: @Composable () -> Unit,
) {
	val host = LocalPopupHost.current
	val compositionContext = rememberCompositionContext()
	val invalidateLayout = LocalLayoutInvalidator.current
	val popup = remember(host, compositionContext) {
		val applier = MosaicNodeApplier(invalidateLayout)
		PopupEntry(applier.root, Composition(applier, compositionContext))
	}
	val currentContent by rememberUpdatedState(content)
	DisposableEffect(popup) {
		popup.composition.setContent { currentContent() }
		host.add(popup)
		invalidateLayout()
		onDispose {
			host.remove(popup)
			popup.composition.dispose()
			invalidateLayout()
		}
	}

	// The anchor marks the position of the popup without taking up any space.
	ComposeNode<MosaicNode, Applier<Any>>(
		factory = NodeFactory,
		update = {
			set(popup) {
				popup.anchor = this
				measurePolicy = AnchorMeasurePolicy
				debugPolicy = { "Popup() x=$x y=$y" }
			}
			// Set on the node, so that changes to them cause another layout.
			set(alignment) { popup.alignment = it }
			set(offset) { popup.offset = it }
		},
	)
}

private object AnchorMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
		return layout(0, 0) {}
	}
}

internal class PopupEntry(
	val root: MosaicNode,
	val composition: Composition,
) {
	var anchor: MosaicNode? = null
	var alignment: Alignment = Alignment.TopStart
	var offset: IntOffset = IntOffset.Zero
}

internal val LocalPopupHost = staticCompositionLocalOf<PopupHost> {
	throw IllegalStateException("No PopupHost provided")
}

/** The popups of a composition, in the order they are drawn. */
internal class PopupHost {
	val popups = ArrayList<PopupEntry>()

	fun add(popup: PopupEntry) {
		popups += popup
	}

	fun remove(popup: PopupEntry) {
		popups -= popup
	}

	/** Measures and places the popups after [root] was laid out with [constraints]. */
	fun layout(root: MosaicNode, constraints: Constraints) {
		val popupConstraints = constraints.copy(minWidth = 0, minHeight = 0)
		val maxRight = if (constraints.hasBoundedWidth) constraints.maxWidth else root.width
		for (popup in popups) {
			val anchor = popup.anchor ?: continue
			val placeable = popup.root.measure(popupConstraints)
			val size = IntSize(placeable.width, placeable.height)
			val aligned = popup.alignment.align(IntSize.Zero, size)
			var x = anchor.x + popup.offset.x - aligned.x
			var y = anchor.y + popup.offset.y - aligned.y
			x = x.coerceAtMost(maxRight - size.width).coerceAtLeast(0)
			if (constraints.hasBoundedHeight) {
				y = y.coerceAtMost(constraints.maxHeight - size.height)
			}
			y = y.coerceAtLeast(0)
			popup.root.measureAndPlace(popupConstraints, x, y)
		}
	}

	/** Draws [root] and the popups over it, on a surface large enough for all of them. */
	fun draw(root: MosaicNode, constraints: Constraints): TextSurface {
		var width = root.width
		var height = root.height
		for (popup in popups) {
			if (popup.anchor == null) continue
			width = maxOf(width, popup.root.x + popup.root.width)
			height = maxOf(height, popup.root.y + popup.root.height)
		}
		if (constraints.hasBoundedWidth) width = width.coerceAtMost(constraints.maxWidth)
		if (constraints.hasBoundedHeight) height = height.coerceAtMost(constraints.maxHeight)

		val surface = TextSurface(width, height)
		root.drawTo(surface)
		for (popup in popups) {
			if (popup.anchor == null) continue
			val node = popup.root
			surface.clear(node.x, node.y, node.x + node.width, node.y + node.height)
			node.drawTo(surface)
		}
		return surface
	}
}
