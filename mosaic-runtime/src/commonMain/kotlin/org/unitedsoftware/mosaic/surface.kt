package org.unitedsoftware.mosaic

import de.cketti.codepoints.appendCodePoint
import org.unitedsoftware.mosaic.terminal.AnsiLevel
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.EmptyTextStyle
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Bold
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Dim
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Invert
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Italic
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Strikethrough
import org.unitedsoftware.mosaic.ui.UnderlineStyle
import org.unitedsoftware.mosaic.ui.UnspecifiedColor
import org.unitedsoftware.mosaic.ui.UnspecifiedUnderlineStyle
import org.unitedsoftware.mosaic.ui.isEmptyTextStyle
import org.unitedsoftware.mosaic.ui.isNotEmptyTextStyle
import org.unitedsoftware.mosaic.ui.isSpecifiedColor
import org.unitedsoftware.mosaic.ui.isSpecifiedTextStyle
import org.unitedsoftware.mosaic.ui.isSpecifiedUnderlineStyle
import org.unitedsoftware.mosaic.ui.isUnspecifiedColor
import org.unitedsoftware.mosaic.ui.isUnspecifiedUnderlineStyle
import org.unitedsoftware.mosaic.ui.unit.IntOffset

public interface TextCanvas {
	public val height: Int
	public val width: Int

	// TODO Hey! These don't go here...
	public fun render(ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean): String
	public fun appendRowTo(appendable: Appendable, row: Int, ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean)
}

internal class TextSurface(
	override val width: Int,
	override val height: Int,
) : TextCanvas {
	var translationX = 0
	var translationY = 0

	// The pixels, as one array for each of their properties rather than an object for each pixel,
	// which makes creating and comparing surfaces much cheaper.
	private val size = width * height
	private val codePoints = IntArray(size).also { it.fill(SpaceCharCodePoint) }

	/** Columns of the code point: 0 for the second half of a two column one, which is not rendered. */
	private val columnCounts = ByteArray(size).also { it.fill(1) }
	private val foregrounds = IntArray(size).also { it.fill(UnspecifiedColor) }
	private val backgrounds = IntArray(size).also { it.fill(UnspecifiedColor) }
	private val textStyles = IntArray(size).also { it.fill(EmptyTextStyle) }
	private val underlineStyles = IntArray(size).also { it.fill(UnspecifiedUnderlineStyle) }
	private val underlineColors = IntArray(size).also { it.fill(UnspecifiedColor) }

	/** Zero width code points, such as combining marks, rendered after the code point, if any. */
	private var combinings: Array<String?>? = null

	/** The URLs pixels link to, if any. */
	private var links: Array<String?>? = null

	// Writes outside of this rectangle, in surface coordinates, are dropped.
	// Not private, since the inline withClip changes them.
	var clipLeft = 0
	var clipTop = 0
	var clipRight = width
	var clipBottom = height

	/** Runs [block] with writes limited to the intersection of the current clip and the rectangle. */
	inline fun <R> withClip(left: Int, top: Int, right: Int, bottom: Int, block: () -> R): R {
		val oldLeft = clipLeft
		val oldTop = clipTop
		val oldRight = clipRight
		val oldBottom = clipBottom
		clipLeft = maxOf(oldLeft, left)
		clipTop = maxOf(oldTop, top)
		clipRight = minOf(oldRight, right)
		clipBottom = minOf(oldBottom, bottom)
		try {
			return block()
		} finally {
			clipLeft = oldLeft
			clipTop = oldTop
			clipRight = oldRight
			clipBottom = oldBottom
		}
	}

	private fun isClipped(x: Int, y: Int) = x !in clipLeft until clipRight || y !in clipTop until clipBottom

	/** Images to write over the cells they cover once the rows are written, like Sixel. */
	val graphics = ArrayList<GraphicPlacement>(0)

	/**
	 * Places an image, as the escape sequence [data] which draws it at the cursor, covering [columns]
	 * by [rows] cells at [row] and [column]. Images which do not entirely fit within the clip are
	 * left out, since they cannot be cut.
	 */
	fun addGraphic(row: Int, column: Int, columns: Int, rows: Int, data: String) {
		val x = translationX + column
		val y = translationY + row
		if (x < clipLeft || y < clipTop || x + columns > clipRight || y + rows > clipBottom) return
		graphics += GraphicPlacement(x, y, columns, rows, data)
	}

	/**
	 * Whether [row] shows the same as [otherRow] of [other], which can be of a different width, with
	 * the missing pixels of the narrower one being empty.
	 */
	fun rowEquals(row: Int, other: TextSurface, otherRow: Int): Boolean {
		val start = row * width
		val otherStart = otherRow * other.width
		val common = minOf(width, other.width)
		for (column in 0 until common) {
			if (!pixelEquals(start + column, other, otherStart + column)) return false
		}
		for (column in common until width) {
			if (!isEmpty(start + column)) return false
		}
		for (column in common until other.width) {
			if (!other.isEmpty(otherStart + column)) return false
		}
		return true
	}

	private fun pixelEquals(index: Int, other: TextSurface, otherIndex: Int): Boolean {
		return codePoints[index] == other.codePoints[otherIndex] &&
			columnCounts[index] == other.columnCounts[otherIndex] &&
			foregrounds[index] == other.foregrounds[otherIndex] &&
			backgrounds[index] == other.backgrounds[otherIndex] &&
			textStyles[index] == other.textStyles[otherIndex] &&
			underlineStyles[index] == other.underlineStyles[otherIndex] &&
			underlineColors[index] == other.underlineColors[otherIndex] &&
			combiningAt(index) == other.combiningAt(otherIndex) &&
			linkAt(index) == other.linkAt(otherIndex)
	}

	private fun combiningAt(index: Int) = combinings?.get(index)

	private fun linkAt(index: Int) = links?.get(index)

	private fun isEmpty(index: Int): Boolean {
		return codePoints[index] == SpaceCharCodePoint &&
			columnCounts[index] == 1.toByte() &&
			foregrounds[index] == UnspecifiedColor &&
			backgrounds[index] == UnspecifiedColor &&
			textStyles[index] == EmptyTextStyle &&
			underlineStyles[index] == UnspecifiedUnderlineStyle &&
			underlineColors[index] == UnspecifiedColor &&
			combiningAt(index) == null &&
			linkAt(index) == null
	}

	/** Where the terminal's cursor should be placed after rendering, if anywhere. */
	var cursor: IntOffset? = null
		private set

	fun setCursor(row: Int, column: Int) {
		val x = translationX + column
		val y = row + translationY
		if (!isClipped(x, y)) {
			cursor = IntOffset(x, y)
		}
	}

	/** The index of the pixel at [row] and [column], or -1 when it is outside the clip. */
	fun indexOf(row: Int, column: Int): Int {
		val x = translationX + column
		val y = row + translationY
		if (isClipped(x, y)) {
			return -1
		}
		return y * width + x
	}

	/**
	 * Writes [codePoint], which occupies [columns] columns, at [row] and [column]. Returns the index
	 * of the pixel written to, or -1 when it is outside the clip and the write was dropped.
	 *
	 * A two column code point also takes over the next pixel. When there is no next pixel, a space
	 * is written instead so that the row does not overflow. Overwriting either half of an existing
	 * two column code point turns its other half into a space.
	 */
	fun setCodePoint(row: Int, column: Int, codePoint: Int, columns: Int): Int {
		val x = translationX + column
		val y = row + translationY
		if (isClipped(x, y)) {
			return -1
		}
		val index = y * width + x
		unlinkWide(index)

		if (columns == 2 && x + 1 < clipRight) {
			unlinkWide(index + 1)
			writeCodePoint(index + 1, SpaceCharCodePoint, columns = 0)
			writeCodePoint(index, codePoint, columns = 2)
		} else {
			writeCodePoint(index, if (columns == 2) SpaceCharCodePoint else codePoint, columns = 1)
		}
		return index
	}

	private fun writeCodePoint(index: Int, codePoint: Int, columns: Int) {
		codePoints[index] = codePoint
		columnCounts[index] = columns.toByte()
		combinings?.set(index, null)
		// Text drawn over a link is not part of it, unless its own style links it again.
		links?.set(index, null)
	}

	/** Applies the specified ones of the colors and styles to the pixel at [index]. */
	fun updateStyle(
		index: Int,
		foreground: Color,
		background: Color,
		textStyle: TextStyle,
		underlineStyle: UnderlineStyle,
		underlineColor: Color,
	) {
		if (foreground.isSpecifiedColor) foregrounds[index] = foreground.value
		if (background.isSpecifiedColor) backgrounds[index] = background.value
		if (textStyle.isSpecifiedTextStyle) textStyles[index] = textStyle.bits
		if (underlineStyle.isSpecifiedUnderlineStyle) underlineStyles[index] = underlineStyle.value
		if (underlineColor.isSpecifiedColor) underlineColors[index] = underlineColor.value
	}

	fun setLink(index: Int, link: String) {
		val links = links ?: arrayOfNulls<String>(size).also { links = it }
		links[index] = link
	}

	/** Adds a zero width code point, like a combining mark, to the pixel at [index]. */
	fun appendCombining(index: Int, codePoint: Int) {
		val combinings = combinings ?: arrayOfNulls<String>(size).also { combinings = it }
		combinings[index] = buildString {
			combinings[index]?.let(::append)
			appendCodePoint(codePoint)
		}
	}

	/** Resets the pixels in the rectangle, in surface coordinates, to unstyled spaces. */
	fun clear(left: Int, top: Int, right: Int, bottom: Int) {
		// Images cannot be partly cleared, so ones which overlap are removed.
		graphics.removeAll { it.x < right && it.x + it.columns > left && it.y < bottom && it.y + it.rows > top }
		for (y in maxOf(top, clipTop) until minOf(bottom, clipBottom)) {
			for (x in maxOf(left, clipLeft) until minOf(right, clipRight)) {
				val index = y * width + x
				unlinkWide(index)
				writeCodePoint(index, SpaceCharCodePoint, columns = 1)
				foregrounds[index] = UnspecifiedColor
				backgrounds[index] = UnspecifiedColor
				textStyles[index] = EmptyTextStyle
				underlineStyles[index] = UnspecifiedUnderlineStyle
				underlineColors[index] = UnspecifiedColor
			}
		}
	}

	/** Turns the other half of the two column code point the pixel at [index] is part of into a space. */
	private fun unlinkWide(index: Int) {
		when (columnCounts[index].toInt()) {
			0 -> writeCodePoint(index - 1, SpaceCharCodePoint, columns = 1)
			2 -> writeCodePoint(index + 1, SpaceCharCodePoint, columns = 1)
		}
	}

	override fun appendRowTo(appendable: Appendable, row: Int, ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean) {
		// Reused heap allocation for building ANSI attributes inside the loop.
		val attributes = mutableListOf<String>()

		val rowStart = row * width
		var rowStop = rowStart + width
		while (rowStop > rowStart && isEmpty(rowStop - 1)) {
			rowStop--
		}

		// The attributes of the previous pixel, starting from those of an empty one.
		var lastForeground = UnspecifiedColor
		var lastBackground = UnspecifiedColor
		var lastTextStyle = TextStyle(EmptyTextStyle)
		var lastUnderlineStyle = UnspecifiedUnderlineStyle
		var lastUnderlineColor = UnspecifiedColor
		var openLink: String? = null
		val links = links
		val combinings = combinings
		for (index in rowStart until rowStop) {
			if (columnCounts[index] == 0.toByte()) {
				// Covered by the two column code point before it.
				continue
			}

			if (ansiLevel != AnsiLevel.NONE) {
				val foreground = foregrounds[index]
				if (foreground != lastForeground) {
					attributes.addColor(Color(foreground), ansiLevel, ansiFgColorSelector, ansiFgColorReset, ansiFgColorOffset)
					lastForeground = foreground
				}
				val background = backgrounds[index]
				if (background != lastBackground) {
					attributes.addColor(Color(background), ansiLevel, ansiBgColorSelector, ansiBgColorReset, ansiBgColorOffset)
					lastBackground = background
				}
				val textStyle = TextStyle(textStyles[index])
				if (textStyle != lastTextStyle) {
					fun maybeToggleStyle(style: TextStyle, on: String, off: String) {
						if (style in textStyle) {
							if (style !in lastTextStyle) {
								attributes += on
							}
						} else if (style in lastTextStyle) {
							attributes += off
						}
					}
					maybeToggleStyle(Bold, "1", "22")
					maybeToggleStyle(Dim, "2", "22")
					maybeToggleStyle(Italic, "3", "23")
					maybeToggleStyle(Invert, "7", "27")
					maybeToggleStyle(Strikethrough, "9", "29")
					lastTextStyle = textStyle
				}
				val underlineStyle = underlineStyles[index]
				if (underlineStyle != lastUnderlineStyle) {
					attributes += when (UnderlineStyle(underlineStyle)) {
						UnderlineStyle.Unspecified, UnderlineStyle.None -> "24"
						UnderlineStyle.Double if (supportsKittyUnderlines) -> "4:2"
						UnderlineStyle.Curly if (supportsKittyUnderlines) -> "4:3"
						UnderlineStyle.Dotted if (supportsKittyUnderlines) -> "4:4"
						UnderlineStyle.Dashed if (supportsKittyUnderlines) -> "4:5"
						else -> "4"
					}
					lastUnderlineStyle = underlineStyle
				}
				val underlineColor = underlineColors[index]
				if (underlineColor != lastUnderlineColor) {
					attributes.addColor(
						Color(underlineColor),
						ansiLevel,
						ansiUnderlineColorSelector,
						ansiUnderlineColorReset,
						ansiUnderlineColorOffset,
					)
					lastUnderlineColor = underlineColor
				}
				if (attributes.isNotEmpty()) {
					appendable.append(CSI)
					attributes.forEachIndexed { attributeIndex, element ->
						if (attributeIndex > 0) {
							appendable.append(ansiSeparator)
						}
						appendable.append(element)
					}
					appendable.append(ansiClosingCharacter)
					attributes.clear() // This list is reused!
				}

				val link = links?.get(index)
				if (link != openLink) {
					appendable.appendHyperlink(link)
					openLink = link
				}
			}

			appendable.appendCodePoint(codePoints[index])
			combinings?.get(index)?.let(appendable::append)
		}

		if (openLink != null) {
			appendable.appendHyperlink(null)
		}

		if (
			ansiLevel != AnsiLevel.NONE &&
			(
				lastBackground != UnspecifiedColor ||
					lastForeground != UnspecifiedColor ||
					lastTextStyle.isNotEmptyTextStyle ||
					lastUnderlineColor != UnspecifiedColor ||
					lastUnderlineStyle != UnspecifiedUnderlineStyle
				)
		) {
			appendable.append(ansiReset)
			appendable.append(ansiClosingCharacter)
		}
	}

	/** Starts an OSC 8 hyperlink to [url], or ends the current one when null. */
	private fun Appendable.appendHyperlink(url: String?) {
		append(OSC)
		append("8;;")
		if (url != null) append(url)
		append(ST)
	}

	private fun MutableList<String>.addColor(
		color: Color,
		ansiLevel: AnsiLevel,
		select: Int,
		reset: Int,
		offset: Int,
	) {
		if (color.isUnspecifiedColor) {
			add(reset.toString())
			return
		}
		when (ansiLevel) {
			AnsiLevel.NONE -> add(reset.toString())

			AnsiLevel.ANSI16 -> {
				val ansi16Code = color.toAnsi16Code()
				if (ansi16Code == ansiFgColorReset || ansi16Code == ansiBgColorReset) {
					add(reset.toString())
				} else {
					add((ansi16Code + offset).toString())
				}
			}

			AnsiLevel.ANSI256 -> {
				add(select.toString())
				add(ansiSelectorColor256)
				add(color.toAnsi256Code().toString())
			}

			AnsiLevel.TRUECOLOR -> {
				add(select.toString())
				add(ansiSelectorColorRgb)
				add(color.redInt.toString())
				add(color.greenInt.toString())
				add(color.blueInt.toString())
			}
		}
	}

	override fun render(ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean): String = buildString {
		if (height > 0) {
			for (rowIndex in 0 until height) {
				appendRowTo(this, rowIndex, ansiLevel, supportsKittyUnderlines)
				append("\n")
			}
			// Remove trailing newline.
			setLength(length - 1)
		}
	}
}

/** An image drawn by [data] over the cells from [x] and [y], in surface coordinates. */
internal class GraphicPlacement(val x: Int, val y: Int, val columns: Int, val rows: Int, val data: String) {
	override fun equals(other: Any?) = other is GraphicPlacement &&
		x == other.x && y == other.y && columns == other.columns && rows == other.rows &&
		// The data is the same instance as long as the image is unchanged, which is cheaper to check.
		data === other.data

	override fun hashCode() = ((x * 31 + y) * 31 + columns) * 31 + rows
}
