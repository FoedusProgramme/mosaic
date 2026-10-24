package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import kotlin.math.roundToInt
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.IntOffset
import org.unitedsoftware.mosaic.ui.unit.IntSize
import org.unitedsoftware.mosaic.ui.unit.constrainHeight
import org.unitedsoftware.mosaic.ui.unit.constrainWidth

/** The width of a [ProgressBar] whose modifier does not size it. */
public const val DefaultProgressBarWidth: Int = 20

/**
 * A bar which fills from the left as [progress] goes from 0 to 1, to an eighth of a cell.
 *
 * It is one row high and [DefaultProgressBarWidth] cells wide unless [modifier] sizes it, for
 * example with `Modifier.width` or `Modifier.fillMaxWidth`. The filled part is drawn in [color],
 * and the whole bar has the background [trackColor], so that the part still to fill shows too.
 * A [progress] outside of 0 to 1 shows as the nearest end, and one which is not a number as empty.
 */
@Composable
public fun ProgressBar(
	progress: Float,
	modifier: Modifier = Modifier,
	color: Color = Color.Unspecified,
	trackColor: Color = Color.Unspecified,
) {
	Layout(
		content = EmptyProgressBarContent,
		measurePolicy = ProgressBarMeasurePolicy,
		debugInfo = { "ProgressBar($progress)" },
		modifier = modifier.drawBehind {
			if (trackColor.isSpecifiedColor) drawRect(background = trackColor)
			val filled = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
			val eighths = (filled * width * 8).roundToInt()
			val full = eighths / 8
			if (full > 0) drawRect('█', foreground = color, size = IntSize(full, height))
			val partial = eighths % 8
			if (partial > 0) {
				drawRect(
					PartialBlocks[partial],
					foreground = color,
					topLeft = IntOffset(full, 0),
					size = IntSize(1, height),
				)
			}
		},
	)
}

/** The left eighths of a cell, by how many eighths they fill. */
private const val PartialBlocks = " ▏▎▍▌▋▊▉"

private val EmptyProgressBarContent: @Composable () -> Unit = {}

private object ProgressBarMeasurePolicy : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val width = if (constraints.hasFixedWidth) {
			constraints.maxWidth
		} else {
			constraints.constrainWidth(DefaultProgressBarWidth)
		}
		val height = if (constraints.hasFixedHeight) constraints.maxHeight else constraints.constrainHeight(1)
		return layout(width, height) {}
	}
}
