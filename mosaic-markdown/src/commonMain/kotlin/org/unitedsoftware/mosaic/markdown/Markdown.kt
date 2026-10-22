package org.unitedsoftware.mosaic.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.unitedsoftware.mosaic.layout.IntrinsicMeasurable
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.fillMaxWidth
import org.unitedsoftware.mosaic.layout.height
import org.unitedsoftware.mosaic.layout.padding
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.AnnotatedString
import org.unitedsoftware.mosaic.text.SpanStyle
import org.unitedsoftware.mosaic.ui.Arrangement
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Filler
import org.unitedsoftware.mosaic.ui.Layout
import org.unitedsoftware.mosaic.ui.Row
import org.unitedsoftware.mosaic.ui.Text
import org.unitedsoftware.mosaic.ui.unit.Constraints

/**
 * Renders the Markdown [text]: headings, paragraphs with emphasis, code spans, and links (as OSC 8
 * hyperlinks in terminals which support them), lists including task lists, quotes, fenced code
 * with syntax highlighting, diffs, tables, and thematic breaks.
 *
 * [text] can be incomplete, like the response of a language model which is still streaming in.
 * Each change parses the text again, which is fast, and only the blocks which changed are composed
 * again. Text wraps to the incoming maximum width.
 */
@Composable
public fun Markdown(
	text: String,
	modifier: Modifier = Modifier,
	theme: MarkdownTheme = MarkdownTheme.Default,
) {
	val blocks = remember(text) { parseBlocks(text) }
	Blocks(blocks, theme, listDepth = 0, tight = false, base = null, modifier = modifier)
}

@Composable
private fun Blocks(
	blocks: List<Block>,
	theme: MarkdownTheme,
	listDepth: Int,
	tight: Boolean,
	base: SpanStyle?,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier,
		verticalArrangement = if (tight) Arrangement.Top else Arrangement.spacedBy(1),
	) {
		for (block in blocks) {
			BlockContent(block, theme, listDepth, base)
		}
	}
}

@Composable
private fun BlockContent(block: Block, theme: MarkdownTheme, listDepth: Int, base: SpanStyle?) {
	when (block) {
		is Heading -> {
			val style = theme.headings[block.level - 1].let { base?.merge(it) ?: it }
			val text = remember(block, theme, base) { parseInlines(block.text, theme, style) }
			Text(text)
		}

		is Paragraph -> {
			val text = remember(block, theme, base) { parseInlines(block.text, theme, base) }
			Text(text)
		}

		is CodeBlock -> {
			val code = remember(block, theme) { highlight(block.code, block.language, theme.syntax) }
			Text(code, modifier = Modifier.padding(left = 2))
		}

		is Quote -> {
			Blocks(
				blocks = block.children,
				theme = theme,
				modifier = Modifier
					.drawBehind {
						for (row in 0 until height) {
							drawText(row, 0, "│", foreground = theme.quoteBar)
						}
					}
					.padding(left = 2),
				listDepth = listDepth,
				tight = false,
				base = base?.merge(theme.quote) ?: theme.quote,
			)
		}

		is ListBlock -> ListContent(block, theme, listDepth, base)

		is Table -> TableContent(block, theme, base)

		ThematicBreak -> Filler('─', modifier = Modifier.fillMaxWidth().height(1), foreground = theme.thematicBreak)
	}
}

@Composable
private fun ListContent(list: ListBlock, theme: MarkdownTheme, listDepth: Int, base: SpanStyle?) {
	val numberWidth = if (list.start != null) "${list.start + list.items.size - 1}".length else 0
	Column(verticalArrangement = if (list.loose) Arrangement.spacedBy(1) else Arrangement.Top) {
		list.items.forEachIndexed { index, item ->
			val task = when (item.checked) {
				true -> theme.checkedTask
				false -> theme.uncheckedTask
				null -> null
			}
			// Tasks replace bullets, but numbers stay in front of them.
			val marker = when {
				list.start != null -> "${list.start + index}.".padStart(numberWidth + 1) + (task?.let { " $it" } ?: "")
				task != null -> task
				else -> theme.bullets[listDepth % theme.bullets.size]
			}
			Row {
				Text(remember(marker, theme) { AnnotatedString("$marker ", theme.listMarker) })
				Blocks(item.children, theme, listDepth + 1, tight = !list.loose, base = base)
			}
		}
	}
}

/** The column widths and row heights of the last measurement, for drawing the borders. */
private class TableGeometry {
	var widths = IntArray(0)
	var heights = IntArray(0)
}

@Composable
private fun TableContent(table: Table, theme: MarkdownTheme, base: SpanStyle?) {
	val cells = remember(table, theme, base) {
		val header = base?.merge(theme.tableHeader) ?: theme.tableHeader
		table.header.map { parseInlines(it, theme, header) } +
			table.rows.flatMap { row -> row.map { parseInlines(it, theme, base) } }
	}
	val geometry = remember { TableGeometry() }
	Layout(
		content = {
			for (cell in cells) {
				Text(cell)
			}
		},
		modifier = Modifier.drawBehind {
			val widths = geometry.widths
			val heights = geometry.heights
			if (widths.isEmpty()) return@drawBehind
			fun border(left: Char, middle: Char, right: Char) = buildString {
				append(left)
				widths.forEachIndexed { column, width ->
					if (column > 0) append(middle)
					repeat(width + 2) { append('─') }
				}
				append(right)
			}
			val color = theme.tableBorder
			var row = 0
			drawText(row++, 0, border('┌', '┬', '┐'), foreground = color)
			heights.forEachIndexed { index, height ->
				repeat(height) {
					var x = 0
					for (width in widths) {
						drawText(row, x, "│", foreground = color)
						x += width + 3
					}
					drawText(row, x, "│", foreground = color)
					row++
				}
				if (index == 0) drawText(row++, 0, border('├', '┼', '┤'), foreground = color)
			}
			drawText(row, 0, border('└', '┴', '┘'), foreground = color)
		},
		measurePolicy = TableMeasurePolicy(table.header.size, table.alignments, geometry),
	)
}

private class TableMeasurePolicy(
	private val columns: Int,
	private val alignments: List<ColumnAlignment>,
	private val geometry: TableGeometry,
) : MeasurePolicy {
	override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
		val rows = measurables.size / columns
		val natural = IntArray(columns)
		val minimum = IntArray(columns)
		for ((index, cell) in measurables.withIndex()) {
			val column = index % columns
			natural[column] = maxOf(natural[column], cell.maxIntrinsicWidth(Constraints.Infinity))
			minimum[column] = maxOf(minimum[column], cell.minIntrinsicWidth(Constraints.Infinity))
		}
		// Borders between and around columns, and a space on either side of each cell.
		val overhead = columns + 1 + columns * 2
		val widths = natural.copyOf()
		if (constraints.hasBoundedWidth) {
			val available = (constraints.maxWidth - overhead).coerceAtLeast(columns)
			// Narrow the widest column until the table fits, first down to its longest word.
			while (widths.sum() > available) {
				var widest = -1
				for (column in 0 until columns) {
					if (widths[column] > minimum[column] && (widest == -1 || widths[column] > widths[widest])) widest = column
				}
				if (widest == -1) {
					for (column in 0 until columns) {
						if (widths[column] > 1 && (widest == -1 || widths[column] > widths[widest])) widest = column
					}
				}
				if (widest == -1) break
				widths[widest]--
			}
		}
		for (column in 0 until columns) widths[column] = widths[column].coerceAtLeast(1)

		val placeables = measurables.mapIndexed { index, cell ->
			cell.measure(Constraints(maxWidth = widths[index % columns]))
		}
		val heights = IntArray(rows) { row ->
			(0 until columns).maxOf { placeables[row * columns + it].height }.coerceAtLeast(1)
		}
		geometry.widths = widths
		geometry.heights = heights

		val width = widths.sum() + overhead
		val height = heights.sum() + 3
		return layout(width, height) {
			var y = 1
			for (row in 0 until rows) {
				var x = 2
				for (column in 0 until columns) {
					val placeable = placeables[row * columns + column]
					val free = widths[column] - placeable.width
					val offset = when (alignments.getOrElse(column) { ColumnAlignment.Start }) {
						ColumnAlignment.Start -> 0
						ColumnAlignment.Center -> free / 2
						ColumnAlignment.End -> free
					}
					placeable.place(x + offset, y)
					x += widths[column] + 3
				}
				y += heights[row]
				// The separator below the header.
				if (row == 0) y++
			}
		}
	}

	override fun minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
		return columns * 4 + 1
	}
}
