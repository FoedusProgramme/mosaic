package org.unitedsoftware.mosaic.layout

import androidx.compose.runtime.Immutable
import dev.drewhamilton.poko.Poko
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.columnWidth
import org.unitedsoftware.mosaic.text.prefixEnd
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.constrainHeight
import org.unitedsoftware.mosaic.ui.unit.constrainWidth
import org.unitedsoftware.mosaic.ui.unit.offset

/** The characters of a [border]. */
@Immutable
@Poko
public class BorderStyle(
	public val topLeft: Char,
	public val topRight: Char,
	public val bottomLeft: Char,
	public val bottomRight: Char,
	public val horizontal: Char,
	public val vertical: Char,
) {
	public companion object {
		public val Single: BorderStyle = BorderStyle('┌', '┐', '└', '┘', '─', '│')
		public val Rounded: BorderStyle = BorderStyle('╭', '╮', '╰', '╯', '─', '│')
		public val Double: BorderStyle = BorderStyle('╔', '╗', '╚', '╝', '═', '║')
		public val Heavy: BorderStyle = BorderStyle('┏', '┓', '┗', '┛', '━', '┃')
		public val Ascii: BorderStyle = BorderStyle('+', '+', '+', '+', '-', '|')
	}
}

/**
 * Draws a box of [style] around the content, which is inset by one cell on each side to make
 * room for it. A [title] is drawn into the top edge, cut off to fit.
 */
public fun Modifier.border(
	style: BorderStyle = BorderStyle.Rounded,
	color: Color = Color.Unspecified,
	title: String? = null,
	titleColor: Color = color,
	titleStyle: TextStyle = TextStyle.Bold,
): Modifier = this then BorderModifier(style, color, title, titleColor, titleStyle)

private class BorderModifier(
	private val style: BorderStyle,
	private val color: Color,
	private val title: String?,
	private val titleColor: Color,
	private val titleStyle: TextStyle,
) : LayoutModifier,
	DrawModifier {
	override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
		val placeable = measurable.measure(constraints.offset(-2, -2))
		val width = constraints.constrainWidth(placeable.width + 2)
		val height = constraints.constrainHeight(placeable.height + 2)
		return layout(width, height) {
			placeable.place(1, 1)
		}
	}

	override fun minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.minIntrinsicWidth(height.inset()) + 2

	override fun maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int) = measurable.maxIntrinsicWidth(height.inset()) + 2

	override fun minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.minIntrinsicHeight(width.inset()) + 2

	override fun maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int) = measurable.maxIntrinsicHeight(width.inset()) + 2

	/** The size left for the content, keeping an unbounded size unbounded. */
	private fun Int.inset() = if (this == Constraints.Infinity) this else (this - 2).coerceAtLeast(0)

	override fun ContentDrawScope.draw() {
		drawContent()
		if (width < 2 || height < 2) return
		val horizontal = style.horizontal.toString().repeat(width - 2)
		drawText(0, 0, "${style.topLeft}$horizontal${style.topRight}", foreground = color)
		val vertical = style.vertical.toString()
		for (row in 1 until height - 1) {
			drawText(row, 0, vertical, foreground = color)
			drawText(row, width - 1, vertical, foreground = color)
		}
		drawText(height - 1, 0, "${style.bottomLeft}$horizontal${style.bottomRight}", foreground = color)
		if (title != null && width > 4) {
			val fitted = title.substring(0, title.prefixEnd(width - 4))
			if (fitted.columnWidth() > 0) {
				drawText(0, 1, " $fitted ", foreground = titleColor, textStyle = titleStyle)
			}
		}
	}

	override fun toString() = "Border($style)"
}
