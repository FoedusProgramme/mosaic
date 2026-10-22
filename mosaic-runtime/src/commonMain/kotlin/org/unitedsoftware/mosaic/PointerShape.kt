package org.unitedsoftware.mosaic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.jvm.JvmInline

/** A shape of the mouse pointer, named like the CSS cursor it corresponds to. */
@Immutable
@JvmInline
public value class PointerShape(public val name: String) {
	override fun toString(): String = "PointerShape($name)"

	public companion object {
		public val Default: PointerShape = PointerShape("default")
		public val Text: PointerShape = PointerShape("text")
		public val Pointer: PointerShape = PointerShape("pointer")
		public val Help: PointerShape = PointerShape("help")
		public val Wait: PointerShape = PointerShape("wait")
		public val Progress: PointerShape = PointerShape("progress")
		public val Crosshair: PointerShape = PointerShape("crosshair")
		public val Cell: PointerShape = PointerShape("cell")
		public val VerticalText: PointerShape = PointerShape("vertical-text")
		public val Move: PointerShape = PointerShape("move")
		public val NotAllowed: PointerShape = PointerShape("not-allowed")
		public val Grab: PointerShape = PointerShape("grab")
		public val Grabbing: PointerShape = PointerShape("grabbing")
		public val ColumnResize: PointerShape = PointerShape("col-resize")
		public val RowResize: PointerShape = PointerShape("row-resize")
		public val ZoomIn: PointerShape = PointerShape("zoom-in")
		public val ZoomOut: PointerShape = PointerShape("zoom-out")
	}
}

/**
 * Sets the shape of the mouse pointer over the terminal window while this is in the composition,
 * in terminals which support the Kitty pointer shape protocol. Others ignore it.
 *
 * Like with [WindowTitle], the one composed last applies, and the shape from before is restored
 * once none are left or the program ends. For example, show [PointerShape.Wait] while working.
 */
@Composable
public fun WindowPointerShape(shape: PointerShape) {
	ComposedValue(LocalPointerShapes.current, shape)
}

internal val LocalPointerShapes = staticCompositionLocalOf<ComposedValues<PointerShape>> {
	throw IllegalStateException("No pointer shapes provided")
}
