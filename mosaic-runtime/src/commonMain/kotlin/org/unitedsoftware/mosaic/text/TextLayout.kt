package org.unitedsoftware.mosaic.text

import org.unitedsoftware.mosaic.ui.TextOverflow
import org.unitedsoftware.mosaic.ui.unit.Constraints

internal abstract class TextLayout<T : CharSequence>(initialValue: T) {

	var value: T = initialValue
		set(value) {
			if (value != field) {
				dirty = true
				field = value
			}
		}

	var width: Int = -1
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	var height: Int = -1
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	var lines: List<T> = emptyList()
		private set
		get() {
			check(!dirty) { "Missing call to measure()" }
			return field
		}

	private var dirty = true
	private var maxWidth = Constraints.Infinity
	private var softWrap = true
	private var maxLines = Int.MAX_VALUE
	private var overflow = TextOverflow.Clip

	/**
	 * Lays out the text in lines at most [maxWidth] columns wide when [softWrap] is set, and at
	 * most [maxLines] of them. With [TextOverflow.Ellipsis], text which does not fit ends in an
	 * ellipsis.
	 */
	fun measure(
		maxWidth: Int = Constraints.Infinity,
		softWrap: Boolean = true,
		maxLines: Int = Int.MAX_VALUE,
		overflow: TextOverflow = TextOverflow.Clip,
	) {
		if (!dirty &&
			maxWidth == this.maxWidth &&
			softWrap == this.softWrap &&
			maxLines == this.maxLines &&
			overflow == this.overflow
		) {
			return
		}
		this.maxWidth = maxWidth
		this.softWrap = softWrap
		this.maxLines = maxLines
		this.overflow = overflow

		val value = value
		val bounded = maxWidth != Constraints.Infinity
		val wrapWidth = if (softWrap && bounded) maxWidth.coerceAtLeast(1) else Int.MAX_VALUE
		val rows = value.wrap(wrapWidth)
		val visible = rows.size.coerceAtMost(maxLines.coerceAtLeast(1))
		val lines = ArrayList<T>(visible)
		var width = 0
		for (index in 0 until visible) {
			val row = rows[index]
			var end = row.end
			// The wrapping already counted the columns, which spares counting them again.
			var columns = row.columns
			// Spaces where a line was wrapped are not part of either row.
			if (row.end < value.length && value[row.end] != '\n') {
				while (end > row.start && value[end - 1] == ' ') {
					end--
					columns--
				}
			}
			var line = value.slice(row.start, end)
			if (overflow == TextOverflow.Ellipsis) {
				val truncated = index == visible - 1 && visible < rows.size
				if (truncated || (bounded && columns > maxWidth)) {
					line = line.ellipsized(if (bounded) maxWidth else Int.MAX_VALUE)
					columns = line.columnWidth()
				}
			}
			lines += line
			if (columns > width) width = columns
		}

		this.width = width
		height = lines.size
		this.lines = lines
		dirty = false
	}

	/** Ends this line with an ellipsis, cutting it so that both take at most [columns] columns. */
	private fun T.ellipsized(columns: Int): T {
		var end = if (columns == Int.MAX_VALUE) length else prefixEnd((columns - 1).coerceAtLeast(0))
		while (end > 0 && this[end - 1] == ' ') end--
		return slice(0, end).withEllipsis()
	}

	protected abstract fun T.slice(start: Int, end: Int): T

	protected abstract fun T.withEllipsis(): T
}

private const val Ellipsis = "…"

internal class StringTextLayout : TextLayout<String>(initialValue = "") {
	override fun String.slice(start: Int, end: Int) = substring(start, end)

	override fun String.withEllipsis() = this + Ellipsis
}

internal class AnnotatedStringTextLayout :
	TextLayout<AnnotatedString>(
		initialValue = emptyAnnotatedString(),
	) {
	override fun AnnotatedString.slice(start: Int, end: Int) = subSequence(start, end)

	override fun AnnotatedString.withEllipsis() = this + AnnotatedString(Ellipsis)
}

/** The width of the longest word, which is as narrow as wrapped text gets without breaking words. */
internal fun CharSequence.longestWordWidth(): Int {
	var longest = 0
	var current = 0
	forEachCodePointWithWidth { _, _, codePoint, width ->
		if (codePoint == ' '.code || codePoint == '\n'.code) {
			current = 0
		} else {
			current += width
			if (current > longest) longest = current
		}
	}
	return longest
}
