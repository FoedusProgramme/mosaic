@file:JvmName("Text")

package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.jvm.JvmName
import org.unitedsoftware.mosaic.layout.IntrinsicMeasurable
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.TextCanvasDrawScope
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.text.AnnotatedStringTextLayout
import org.unitedsoftware.mosaic.text.StringTextLayout
import org.unitedsoftware.mosaic.text.TextLayout
import org.unitedsoftware.mosaic.text.longestWordWidth
import org.unitedsoftware.mosaic.ui.unit.Constraints

/**
 * Displays [value].
 *
 * @param softWrap Whether to break lines which are wider than the maximum width of the incoming
 * constraints, after the last space which fits where possible. Lines which do not fit are clipped
 * otherwise.
 * @param maxLines The most lines to show. The rest are clipped, or end in an ellipsis with
 * [TextOverflow.Ellipsis].
 * @param overflow How to show text which does not fit.
 */
@Composable
@MosaicComposable
public fun Text(
	value: String,
	modifier: Modifier = Modifier,
	color: Color = Color.Unspecified,
	background: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
	underlineColor: Color = Color.Unspecified,
	softWrap: Boolean = true,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
) {
	require(maxLines >= 1) { "maxLines must be at least 1: $maxLines" }
	val layout = remember { StringTextLayout() }
	layout.value = value

	Layout(
		content = {},
		modifier = modifier.drawBehind {
			(this as TextCanvasDrawScope).clipToBounds {
				layout.lines.forEachIndexed { row, line ->
					drawText(row, 0, line, color, background, textStyle, underlineStyle, underlineColor)
				}
			}
		},
		debugInfo = {
			"""Text("$value")"""
		},
		measurePolicy = TextMeasurePolicy(layout, softWrap, maxLines, overflow),
	)
}

/**
 * Displays [value] with its styles.
 *
 * @param softWrap Whether to break lines which are wider than the maximum width of the incoming
 * constraints, after the last space which fits where possible. Lines which do not fit are clipped
 * otherwise.
 * @param maxLines The most lines to show. The rest are clipped, or end in an ellipsis with
 * [TextOverflow.Ellipsis].
 * @param overflow How to show text which does not fit.
 */
@Composable
@MosaicComposable
public fun Text(
	value: AnnotatedString,
	modifier: Modifier = Modifier,
	color: Color = Color.Unspecified,
	background: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified,
	underlineColor: Color = Color.Unspecified,
	softWrap: Boolean = true,
	maxLines: Int = Int.MAX_VALUE,
	overflow: TextOverflow = TextOverflow.Clip,
) {
	require(maxLines >= 1) { "maxLines must be at least 1: $maxLines" }
	val layout = remember { AnnotatedStringTextLayout() }
	layout.value = value

	Layout(
		content = {},
		modifier = modifier.drawBehind {
			(this as TextCanvasDrawScope).clipToBounds {
				layout.lines.forEachIndexed { row, line ->
					drawText(row, 0, line, color, background, textStyle, underlineStyle, underlineColor)
				}
			}
		},
		debugInfo = {
			"""Text("$value")"""
		},
		measurePolicy = TextMeasurePolicy(layout, softWrap, maxLines, overflow),
	)
}

private class TextMeasurePolicy(
	private val layout: TextLayout<*>,
	private val softWrap: Boolean,
	private val maxLines: Int,
	private val overflow: TextOverflow,
) : MeasurePolicy {
	override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
		layout.measure(constraints.maxWidth, softWrap, maxLines, overflow)
		return layout(
			layout.width.coerceIn(constraints.minWidth, constraints.maxWidth),
			layout.height.coerceIn(constraints.minHeight, constraints.maxHeight),
		) {}
	}

	override fun minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
		if (!softWrap) return maxIntrinsicWidth(measurables, height)
		return layout.value.longestWordWidth()
	}

	override fun maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
		layout.measure(Constraints.Infinity, softWrap, maxLines, overflow)
		return layout.width
	}

	override fun minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
		layout.measure(width, softWrap, maxLines, overflow)
		return layout.height
	}

	override fun maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
		return minIntrinsicHeight(measurables, width)
	}
}
