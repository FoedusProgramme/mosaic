package org.unitedsoftware.mosaic.text

/** A row of wrapped text, spanning [start] until [end] of the text and taking [columns] columns. */
internal class TextRow(val start: Int, val end: Int, val columns: Int)

/**
 * Wraps this text into rows at most [width] columns wide, breaking after the last space of a row
 * where possible. Line breaks are not part of any row.
 */
internal fun CharSequence.wrap(width: Int): List<TextRow> {
	val rows = mutableListOf<TextRow>()
	var lineStart = 0
	while (true) {
		val lineEnd = indexOf('\n', lineStart).let { if (it == -1) length else it }
		var rowStart = lineStart
		var rowColumns = 0
		var breakAfterSpace = -1
		var breakColumns = 0
		subSequence(lineStart, lineEnd).forEachCodePointWithWidth { start, _, codePoint, columns ->
			val index = lineStart + start
			if (rowColumns + columns > width && index > rowStart) {
				if (codePoint == ' '.code) {
					// The space which does not fit becomes the break itself.
					rows += TextRow(rowStart, index, rowColumns)
					rowColumns = 0
					rowStart = index + 1
					breakAfterSpace = -1
					return@forEachCodePointWithWidth
				}
				if (breakAfterSpace > rowStart) {
					rows += TextRow(rowStart, breakAfterSpace, breakColumns)
					rowColumns -= breakColumns
					rowStart = breakAfterSpace
				} else {
					rows += TextRow(rowStart, index, rowColumns)
					rowColumns = 0
					rowStart = index
				}
				breakAfterSpace = -1
			}
			rowColumns += columns
			if (codePoint == ' '.code) {
				breakAfterSpace = index + 1
				breakColumns = rowColumns
			}
		}
		rows += TextRow(rowStart, lineEnd, rowColumns)
		if (lineEnd == length) return rows
		lineStart = lineEnd + 1
	}
}

/** The end of the longest prefix of this text which takes at most [columns] columns. */
internal fun CharSequence.prefixEnd(columns: Int): Int {
	var used = 0
	forEachCodePointWithWidth { start, _, _, width ->
		if (used + width > columns) return start
		used += width
	}
	return length
}
