package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.background
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

enum class BorderStyle(
	val topLeft: Char,
	val topRight: Char,
	val bottomLeft: Char,
	val bottomRight: Char,
	val horizontal: Char,
	val vertical: Char,
) {
	Single('┌', '┐', '└', '┘', '─', '│'),
	Rounded('╭', '╮', '╰', '╯', '─', '│'),
	Double('╔', '╗', '╚', '╝', '═', '║'),
	Heavy('┏', '┓', '┗', '┛', '━', '┃'),
	Ascii('+', '+', '+', '+', '-', '|'),
}

/**
 * Draws a one-cell box around the content and insets the content by one cell on each side.
 * The [title], if any, is drawn into the top edge and truncated to fit.
 */
fun Modifier.border(
	style: BorderStyle = BorderStyle.Rounded,
	color: Color = Color.Unspecified,
	title: String? = null,
	titleColor: Color = color,
): Modifier = drawBehind {
	if (width < 2 || height < 2) return@drawBehind
	val horizontal = style.horizontal.toString().repeat(width - 2)
	drawText(0, 0, "${style.topLeft}$horizontal${style.topRight}", foreground = color)
	for (row in 1 until height - 1) {
		drawText(row, 0, style.vertical.toString(), foreground = color)
		drawText(row, width - 1, style.vertical.toString(), foreground = color)
	}
	drawText(height - 1, 0, "${style.bottomLeft}$horizontal${style.bottomRight}", foreground = color)
	if (title != null && width > 4) {
		drawText(0, 1, " ${title.take(width - 4)} ", foreground = titleColor, textStyle = TextStyle.Bold)
	}
}.padding(1)

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
		// Text ignores incoming constraints, so size a Box around it instead.
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

private const val PartialBlocks = " ▏▎▍▌▋▊▉"

/** A determinate progress bar with 1/8th-cell precision. */
@Composable
fun ProgressBar(
	progress: Float,
	width: Int,
	modifier: Modifier = Modifier,
	color: Color = LocalPalette.current.accent,
	trackColor: Color = LocalPalette.current.surface,
) {
	val eighths = (progress.coerceIn(0f, 1f) * width * 8).roundToInt()
	val full = eighths / 8
	val remainder = eighths % 8
	val bar = buildString {
		repeat(full) { append('█') }
		if (remainder > 0) append(PartialBlocks[remainder])
		while (length < width) append(' ')
	}
	Text(bar, modifier = modifier, color = color, background = trackColor)
}

enum class SpinnerStyle(val frames: List<String>, val intervalMillis: Long) {
	Dots(listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"), 80),
	Line(listOf("-", "\\", "|", "/"), 120),
	Pulse(listOf("·", "•", "●", "•"), 150),
	Quadrant(listOf("▖", "▘", "▝", "▗"), 120),
	Arrows(listOf("←", "↖", "↑", "↗", "→", "↘", "↓", "↙"), 100),
}

@Composable
fun Spinner(
	style: SpinnerStyle,
	modifier: Modifier = Modifier,
	color: Color = LocalPalette.current.accent,
) {
	var frame by remember(style) { mutableIntStateOf(0) }
	LaunchedEffect(style) {
		while (true) {
			delay(style.intervalMillis)
			frame = (frame + 1) % style.frames.size
		}
	}
	Text(style.frames[frame], modifier = modifier, color = color)
}

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

/** State for [TextField]: the text and a cursor index into it. */
class TextFieldState(initial: String = "") {
	var text by mutableStateOf(initial)
		private set
	var cursor by mutableIntStateOf(initial.length)
		private set

	/** Applies an editing key. Returns false for keys a text field does not handle. */
	fun onKeyEvent(event: KeyEvent): Boolean {
		when {
			event.key == "Backspace" -> if (cursor > 0) {
				text = text.removeRange(cursor - 1, cursor)
				cursor--
			}

			event.key == "Delete" -> if (cursor < text.length) {
				text = text.removeRange(cursor, cursor + 1)
			}

			event.key == "ArrowLeft" -> cursor = (cursor - 1).coerceAtLeast(0)

			event.key == "ArrowRight" -> cursor = (cursor + 1).coerceAtMost(text.length)

			event.key == "Home" || (event.ctrl && event.key == "a") -> cursor = 0

			event.key == "End" || (event.ctrl && event.key == "e") -> cursor = text.length

			event.ctrl && event.key == "u" -> {
				text = text.substring(cursor)
				cursor = 0
			}

			event.key.length == 1 && !event.ctrl && !event.alt -> {
				text = text.substring(0, cursor) + event.key + text.substring(cursor)
				cursor++
			}

			else -> return false
		}
		return true
	}

	fun clear() {
		text = ""
		cursor = 0
	}
}

/**
 * A single-line text field which scrolls horizontally to keep the cursor visible. The cursor is
 * drawn as an inverted cell rather than with the real terminal cursor.
 */
@Composable
fun TextField(
	state: TextFieldState,
	focused: Boolean,
	width: Int,
	modifier: Modifier = Modifier,
	placeholder: String = "",
) {
	val palette = LocalPalette.current
	val text = state.text
	val cursor = state.cursor
	val start = (cursor - width + 1).coerceAtLeast(0)
	val annotated = buildAnnotatedString {
		if (text.isEmpty() && !focused) {
			withStyle(SpanStyle(color = palette.muted, textStyle = TextStyle.Italic)) {
				append(placeholder.take(width).padEnd(width))
			}
			return@buildAnnotatedString
		}
		for (column in 0 until width) {
			val char = text.getOrNull(start + column) ?: ' '
			if (focused && start + column == cursor) {
				withStyle(SpanStyle(textStyle = TextStyle.Invert)) { append(char) }
			} else {
				append(char)
			}
		}
	}
	Text(
		annotated,
		modifier = modifier.background(if (focused) palette.accentContainer else palette.surface),
		color = palette.text,
	)
}
