package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.layout.BorderStyle
import org.unitedsoftware.mosaic.layout.background
import org.unitedsoftware.mosaic.layout.border
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withStyle
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.SpinnerStyle
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.TextStyle

// Mosaic ships layout primitives only. Everything in this file is a recipe built on top of them.

class Palette(
	val text: Color,
	val muted: Color,
	val accent: Color,
	val accentContainer: Color,
	val surface: Color,
	val good: Color,
	val warn: Color,
	val bad: Color,
)

val DarkPalette = Palette(
	text = Color(220, 222, 230),
	muted = Color(120, 124, 140),
	accent = Color(130, 170, 255),
	accentContainer = Color(40, 58, 105),
	surface = Color(38, 40, 52),
	good = Color(120, 220, 140),
	warn = Color(240, 200, 100),
	bad = Color(240, 110, 110),
)

val LightPalette = Palette(
	text = Color(30, 32, 40),
	muted = Color(110, 114, 128),
	accent = Color(40, 90, 200),
	accentContainer = Color(205, 220, 255),
	surface = Color(232, 234, 240),
	good = Color(30, 140, 60),
	warn = Color(170, 110, 0),
	bad = Color(190, 40, 40),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun Panel(
	modifier: Modifier = Modifier,
	title: String? = null,
	style: BorderStyle = BorderStyle.Rounded,
	color: Color = LocalPalette.current.muted,
	titleColor: Color = LocalPalette.current.accent,
	content: @Composable () -> Unit,
) {
	Box(modifier.border(style, color, title, titleColor)) {
		content()
	}
}

/** A label/value line with the label in a fixed-width muted column. */
@Composable
fun Labeled(
	label: String,
	modifier: Modifier = Modifier,
	labelWidth: Int = 14,
	content: @Composable () -> Unit,
) {
	Row(modifier) {
		Box(Modifier.width(labelWidth)) {
			Text(label, color = LocalPalette.current.muted)
		}
		content()
	}
}

@Composable
fun Chip(
	label: String,
	modifier: Modifier = Modifier,
	selected: Boolean = false,
) {
	val palette = LocalPalette.current
	Text(
		" $label ",
		modifier = modifier,
		color = if (selected) palette.surface else palette.text,
		background = if (selected) palette.accent else palette.accentContainer,
	)
}

/** The spinner styles the catalog shows: Mosaic's own, and two made from frames of their own. */
val SpinnerStyles: List<Pair<String, SpinnerStyle>> = listOf(
	"Dots" to SpinnerStyle.Dots,
	"Line" to SpinnerStyle.Line,
	"Pulse" to SpinnerStyle.Pulse,
	"Quadrant" to SpinnerStyle(listOf("▖", "▘", "▝", "▗"), 120.milliseconds),
	"Arrows" to SpinnerStyle(listOf("←", "↖", "↑", "↗", "→", "↘", "↓", "↙"), 100.milliseconds),
)

@Composable
fun TabBar(
	titles: List<String>,
	selected: Int,
	modifier: Modifier = Modifier,
) {
	val palette = LocalPalette.current
	Row(modifier) {
		titles.forEachIndexed { index, title ->
			if (index == selected) {
				Text(" $title ", color = palette.surface, background = palette.accent, textStyle = TextStyle.Bold)
			} else {
				Text(" $title ", color = palette.muted)
			}
			if (index != titles.lastIndex) {
				Text("│", color = palette.muted)
			}
		}
	}
}

/**
 * A text table sized to its content. Columns listed in [alignEnd] are right-aligned, which suits
 * numbers. [highlightColumn] marks the header of (for example) the sort column.
 */
@Composable
fun Table(
	headers: List<String>,
	rows: List<List<String>>,
	modifier: Modifier = Modifier,
	alignEnd: Set<Int> = emptySet(),
	highlightColumn: Int = -1,
) {
	val palette = LocalPalette.current
	val widths = headers.indices.map { column ->
		maxOf(headers[column].length, rows.maxOfOrNull { it[column].length } ?: 0)
	}
	fun cell(column: Int, value: String): String {
		return if (column in alignEnd) value.padStart(widths[column]) else value.padEnd(widths[column])
	}
	Column(modifier) {
		Text(
			buildAnnotatedString {
				headers.forEachIndexed { column, header ->
					val style = if (column == highlightColumn) {
						SpanStyle(color = palette.accent, textStyle = TextStyle.Bold)
					} else {
						SpanStyle(textStyle = TextStyle.Bold)
					}
					withStyle(style) { append(" ${cell(column, header)} ") }
				}
			},
			background = palette.accentContainer,
		)
		rows.forEachIndexed { index, row ->
			Text(
				row.mapIndexed { column, value -> " ${cell(column, value)} " }.joinToString(""),
				background = if (index % 2 == 1) palette.surface else Color.Unspecified,
			)
		}
	}
}

/**
 * Shows a [height]-row window of [items] that keeps [selected] in view, plus a scrollbar when not
 * everything fits. Mosaic has no scrolling container or clipping, so we only emit visible rows.
 */
@Composable
fun <T> ScrollingList(
	items: List<T>,
	selected: Int,
	height: Int,
	modifier: Modifier = Modifier,
	itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
) {
	val visible = minOf(height, items.size)
	val first = (selected - visible / 2).coerceIn(0, items.size - visible)
	Row(modifier) {
		Column(Modifier.weight(1f)) {
			for (index in first until first + visible) {
				itemContent(items[index], index == selected)
			}
		}
		if (items.size > visible) {
			Scrollbar(first = first, visible = visible, total = items.size, height = visible)
		}
	}
}

@Composable
fun Scrollbar(
	first: Int,
	visible: Int,
	total: Int,
	height: Int,
	modifier: Modifier = Modifier,
) {
	val palette = LocalPalette.current
	val thumbSize = (height * visible / total).coerceAtLeast(1)
	val thumbStart = if (total == visible) 0 else (height - thumbSize) * first / (total - visible)
	Column(modifier) {
		for (row in 0 until height) {
			if (row in thumbStart until thumbStart + thumbSize) {
				Text("┃", color = palette.accent)
			} else {
				Text("│", color = palette.muted)
			}
		}
	}
}
