package org.unitedsoftware.mosaic.layout

import org.unitedsoftware.mosaic.TextCanvas
import org.unitedsoftware.mosaic.TextSurface
import org.unitedsoftware.mosaic.layout.Placeable.PlacementScope
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.unit.Constraints

internal fun interface DebugPolicy {
	fun MosaicNode.renderDebug(): String
}

internal abstract class MosaicNodeLayer :
	Placeable(),
	Measurable,
	PlacementScope,
	MeasureScope {
	abstract val next: MosaicNodeLayer?

	private var measureResult: MeasureResult = NotMeasured

	final override var parentData: Any? = null

	final override val width get() = measureResult.width
	final override val height get() = measureResult.height

	override fun measure(constraints: Constraints): Placeable = apply {
		measureResult = doMeasure(constraints)
	}

	protected open fun doMeasure(constraints: Constraints): MeasureResult {
		val placeable = next!!.measure(constraints)
		return object : MeasureResult {
			override val width: Int get() = placeable.width
			override val height: Int get() = placeable.height

			override fun placeChildren() {
				placeable.place(0, 0)
			}
		}
	}

	final override var x = 0
		private set
	final override var y = 0
		private set

	final override fun placeAt(x: Int, y: Int) {
		this.x = x
		this.y = y
		measureResult.placeChildren()
	}

	open fun drawTo(canvas: TextSurface) {
		next?.drawTo(canvas)
	}

	open fun sendKeyEvent(keyEvent: KeyEvent, phase: DispatchPhase): Boolean {
		return next?.sendKeyEvent(keyEvent, phase) ?: false
	}

	open fun sendPasteEvent(pasteEvent: PasteEvent, phase: DispatchPhase): Boolean {
		return next?.sendPasteEvent(pasteEvent, phase) ?: false
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return next?.minIntrinsicWidth(height) ?: 0
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return next?.maxIntrinsicWidth(height) ?: 0
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return next?.minIntrinsicHeight(width) ?: 0
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return next?.maxIntrinsicHeight(width) ?: 0
	}
}

private object RootPlacementScope : PlacementScope {
	override val x get() = 0
	override val y get() = 0
}

internal object NotMeasured : MeasureResult {
	override val width get() = 0
	override val height get() = 0
	override fun placeChildren() = throw UnsupportedOperationException("Not measured")
}

internal class MosaicNode(
	var measurePolicy: MeasurePolicy,
	var debugPolicy: DebugPolicy,
	val isStatic: Boolean,
) : Measurable {
	val children = ArrayList<MosaicNode>()

	private val bottomLayer: MosaicNodeLayer = BottomLayer(this)
	var topLayer: MosaicNodeLayer = bottomLayer
		private set

	override var parentData: Any? = null
		private set

	var testTag: String? = null
		private set

	val focusTargets = ArrayList<FocusTarget>(0)
	val pointerHandlers = ArrayList<(PointerEvent) -> Boolean>(0)

	/** Whether children are clipped to the bounds of this node, and so cannot be hit outside of it. */
	var clipsChildren = false
		private set
	val focusObservers = ArrayList<FocusObserver>(0)

	/** Whether this node is or contains the focused node, as of the last event dispatch. */
	var containsFocus = false

	fun setModifier(modifier: Modifier) {
		focusTargets.clear()
		focusObservers.clear()
		pointerHandlers.clear()
		clipsChildren = false
		topLayer = modifier.foldOut(bottomLayer) { element, nextLayer ->
			var nextLayer = nextLayer
			// The Modifier class can inherit from several key Modifier types
			// with different processing logic.
			if (element is LayoutModifier) {
				nextLayer = LayoutLayer(element, nextLayer)
			}
			if (element is DrawModifier) {
				nextLayer = DrawLayer(element, nextLayer)
			}
			if (element is KeyModifier) {
				nextLayer = KeyLayer(this, element, nextLayer)
			}
			if (element is PasteModifier) {
				nextLayer = PasteLayer(this, element, nextLayer)
			}
			if (element is PointerModifierElement) {
				// Folding out starts at the last, innermost element, which handles events first like
				// children do before their parents.
				pointerHandlers += element.onPointerEvent
			}
			if (element === ClipToBoundsModifier) {
				clipsChildren = true
			}
			if (element is FocusTargetElement) {
				focusTargets += element.target
			}
			if (element is FocusObserverElement) {
				focusObservers += element.observer
			}
			if (element is FocusRequesterElement) {
				element.requester.attach(this)
			}
			if (element is ParentDataModifier) {
				parentData = element.modifyParentData(parentData)
			}
			if (element is TestTagModifier) {
				testTag = element.tag
			}
			nextLayer
		}
	}

	override fun measure(constraints: Constraints): Placeable = topLayer.apply { measure(constraints) }

	val width: Int get() = topLayer.width
	val height: Int get() = topLayer.height
	val x: Int get() = topLayer.x
	val y: Int get() = topLayer.y

	fun measureAndPlace(constraints: Constraints = Constraints(), x: Int = 0, y: Int = 0) {
		val placeable = measure(constraints)
		RootPlacementScope.run { placeable.place(x, y) }
	}

	/**
	 * Draw this node to a [TextSurface].
	 * A call to [measureAndPlace] must precede calls to this function.
	 */
	fun draw(): TextCanvas {
		val surface = TextSurface(width, height)
		topLayer.drawTo(surface)
		return surface
	}

	fun drawTo(surface: TextSurface) {
		topLayer.drawTo(surface)
	}

	fun sendKeyEvent(keyEvent: KeyEvent, phase: DispatchPhase = DispatchPhase.All): Boolean {
		return topLayer.sendKeyEvent(keyEvent, phase)
	}

	fun sendPasteEvent(pasteEvent: PasteEvent, phase: DispatchPhase = DispatchPhase.All): Boolean {
		return topLayer.sendPasteEvent(pasteEvent, phase)
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return topLayer.minIntrinsicWidth(height)
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return topLayer.maxIntrinsicWidth(height)
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return topLayer.minIntrinsicHeight(width)
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return topLayer.maxIntrinsicHeight(width)
	}

	override fun toString() = debugPolicy.run { renderDebug() }
}

private class BottomLayer(
	private val node: MosaicNode,
) : MosaicNodeLayer() {
	override val next: MosaicNodeLayer? get() = null

	override fun doMeasure(constraints: Constraints): MeasureResult {
		return node.measurePolicy.run { measure(node.children, constraints) }
	}

	override fun drawTo(canvas: TextSurface) {
		for (child in node.children) {
			if (child.width != 0 && child.height != 0) {
				child.topLayer.drawTo(canvas)
			}
		}
	}

	override fun sendKeyEvent(keyEvent: KeyEvent, phase: DispatchPhase): Boolean {
		return sendToChildren(phase) { child, childPhase -> child.sendKeyEvent(keyEvent, childPhase) }
	}

	override fun sendPasteEvent(pasteEvent: PasteEvent, phase: DispatchPhase): Boolean {
		return sendToChildren(phase) { child, childPhase -> child.sendPasteEvent(pasteEvent, childPhase) }
	}

	private inline fun sendToChildren(
		phase: DispatchPhase,
		send: (child: MosaicNode, phase: DispatchPhase) -> Boolean,
	): Boolean {
		for (child in node.children) {
			val childPhase = when (phase) {
				DispatchPhase.All -> DispatchPhase.All

				DispatchPhase.FocusedPath -> if (child.containsFocus) phase else continue

				// Children off the focused path did not get the event yet, so they get all of it.
				DispatchPhase.Rest -> if (child.containsFocus) phase else DispatchPhase.All
			}
			if (send(child, childPhase)) {
				return true
			}
		}
		return false
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return node.measurePolicy.run { minIntrinsicWidth(node.children, height) }
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return node.measurePolicy.run { maxIntrinsicWidth(node.children, height) }
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return node.measurePolicy.run { minIntrinsicHeight(node.children, width) }
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return node.measurePolicy.run { maxIntrinsicHeight(node.children, width) }
	}
}

private class LayoutLayer(
	private val element: LayoutModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun doMeasure(constraints: Constraints): MeasureResult {
		return element.run { measure(next, constraints) }
	}

	override fun minIntrinsicWidth(height: Int): Int {
		return element.minIntrinsicWidth(next, height)
	}

	override fun maxIntrinsicWidth(height: Int): Int {
		return element.maxIntrinsicWidth(next, height)
	}

	override fun minIntrinsicHeight(width: Int): Int {
		return element.minIntrinsicHeight(next, width)
	}

	override fun maxIntrinsicHeight(width: Int): Int {
		return element.maxIntrinsicHeight(next, width)
	}
}

private class DrawLayer(
	private val element: DrawModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	private var constraints = Constraints()

	override fun doMeasure(constraints: Constraints): MeasureResult {
		this.constraints = constraints
		return super.doMeasure(constraints)
	}

	override fun drawTo(canvas: TextSurface) {
		val oldX = canvas.translationX
		val oldY = canvas.translationY
		canvas.translationX = x
		canvas.translationY = y
		// Content like text can be larger than its constraints, but drawing, and especially
		// clipping, should stay within the bounds the parent allowed.
		val scope = object :
			TextCanvasDrawScope(
				canvas,
				width.coerceIn(constraints.minWidth, constraints.maxWidth),
				height.coerceIn(constraints.minHeight, constraints.maxHeight),
			),
			ContentDrawScope {
			override fun drawContent() {
				next.drawTo(canvas)
			}
		}
		element.run { scope.draw() }
		canvas.translationX = oldX
		canvas.translationY = oldY
	}
}

private class PasteLayer(
	private val node: MosaicNode,
	private val element: PasteModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun sendPasteEvent(pasteEvent: PasteEvent, phase: DispatchPhase): Boolean {
		// Nodes on the focused path already got the event, and only pass it on now.
		if (phase == DispatchPhase.Rest && node.containsFocus) {
			return next.sendPasteEvent(pasteEvent, phase)
		}
		return element.onPrePasteEvent(pasteEvent) ||
			next.sendPasteEvent(pasteEvent, phase) ||
			element.onPasteEvent(pasteEvent)
	}
}

private class KeyLayer(
	private val node: MosaicNode,
	private val element: KeyModifier,
	override val next: MosaicNodeLayer,
) : MosaicNodeLayer() {
	override fun sendKeyEvent(keyEvent: KeyEvent, phase: DispatchPhase): Boolean {
		// Nodes on the focused path already got the event, and only pass it on now.
		if (phase == DispatchPhase.Rest && node.containsFocus) {
			return next.sendKeyEvent(keyEvent, phase)
		}
		return element.onPreKeyEvent(keyEvent) ||
			next.sendKeyEvent(keyEvent, phase) ||
			element.onKeyEvent(keyEvent)
	}
}
