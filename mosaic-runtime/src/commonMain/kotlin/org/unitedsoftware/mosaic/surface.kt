package org.unitedsoftware.mosaic

import de.cketti.codepoints.appendCodePoint
import org.unitedsoftware.mosaic.terminal.AnsiLevel
import org.unitedsoftware.mosaic.ui.Color
import org.unitedsoftware.mosaic.ui.TextStyle
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Bold
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Dim
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Invert
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Italic
import org.unitedsoftware.mosaic.ui.TextStyle.Companion.Strikethrough
import org.unitedsoftware.mosaic.ui.UnderlineStyle
import org.unitedsoftware.mosaic.ui.isEmptyTextStyle
import org.unitedsoftware.mosaic.ui.isNotEmptyTextStyle
import org.unitedsoftware.mosaic.ui.isSpecifiedColor
import org.unitedsoftware.mosaic.ui.isSpecifiedUnderlineStyle
import org.unitedsoftware.mosaic.ui.isUnspecifiedColor
import org.unitedsoftware.mosaic.ui.isUnspecifiedUnderlineStyle
import org.unitedsoftware.mosaic.ui.unit.IntOffset

private val blankPixel = TextPixel(SpaceCharCodePoint)

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

	private val cells = Array(width * height) { TextPixel(SpaceCharCodePoint) }

	/** Where the terminal's cursor should be placed after rendering, if anywhere. */
	var cursor: IntOffset? = null
		private set

	fun setCursor(row: Int, column: Int) {
		val x = translationX + column
		val y = row + translationY
		if (x in 0 until width && y in 0 until height) {
			cursor = IntOffset(x, y)
		}
	}

	/** The pixel at [row] and [column], or null when it is outside the surface. */
	fun getOrNull(row: Int, column: Int): TextPixel? {
		val x = translationX + column
		val y = row + translationY
		if (x !in 0 until width || y !in 0 until height) {
			return null
		}
		return cells[y * width + x]
	}

	/**
	 * Writes [codePoint], which occupies [columns] columns, at [row] and [column]. Returns the pixel
	 * written to, or null when it is outside the surface and the write was dropped.
	 *
	 * A two column code point also takes over the next pixel. When there is no next pixel, a space
	 * is written instead so that the row does not overflow. Overwriting either half of an existing
	 * two column code point turns its other half into a space.
	 */
	fun setCodePoint(row: Int, column: Int, codePoint: Int, columns: Int): TextPixel? {
		val x = translationX + column
		val y = row + translationY
		if (x !in 0 until width || y !in 0 until height) {
			return null
		}
		val index = y * width + x
		val pixel = cells[index]
		pixel.unlinkWide(index)

		if (columns == 2 && x + 1 < width) {
			val next = cells[index + 1]
			next.unlinkWide(index + 1)
			next.setCodePoint(SpaceCharCodePoint, columns = 0)
			pixel.setCodePoint(codePoint, columns = 2)
		} else {
			pixel.setCodePoint(if (columns == 2) SpaceCharCodePoint else codePoint, columns = 1)
		}
		return pixel
	}

	/** Turns the other half of the two column code point this pixel is part of into a space. */
	private fun TextPixel.unlinkWide(index: Int) {
		when (columns) {
			0 -> cells[index - 1].setCodePoint(SpaceCharCodePoint, columns = 1)
			2 -> cells[index + 1].setCodePoint(SpaceCharCodePoint, columns = 1)
		}
	}

	override fun appendRowTo(appendable: Appendable, row: Int, ansiLevel: AnsiLevel, supportsKittyUnderlines: Boolean) {
		// Reused heap allocation for building ANSI attributes inside the loop.
		val attributes = mutableListOf<String>()

		val rowStart = row * width
		var rowStop = rowStart + width

		while (rowStop > rowStart) {
			val lastIndex = rowStop - 1
			val pixel = cells[lastIndex]
			if (pixel.isEmpty()) {
				rowStop = lastIndex
			} else {
				break
			}
		}

		var lastPixel = blankPixel
		var openLink: String? = null
		for (columnIndex in rowStart until rowStop) {
			val pixel = cells[columnIndex]
			if (pixel.columns == 0) {
				// Covered by the two column code point before it.
				continue
			}

			if (ansiLevel != AnsiLevel.NONE) {
				if (pixel.foreground != lastPixel.foreground) {
					attributes.addColor(
						pixel.foreground,
						ansiLevel,
						ansiFgColorSelector,
						ansiFgColorReset,
						ansiFgColorOffset,
					)
				}
				if (pixel.background != lastPixel.background) {
					attributes.addColor(
						pixel.background,
						ansiLevel,
						ansiBgColorSelector,
						ansiBgColorReset,
						ansiBgColorOffset,
					)
				}

				fun maybeToggleStyle(style: TextStyle, on: String, off: String) {
					if (style in pixel.textStyle) {
						if (style !in lastPixel.textStyle) {
							attributes += on
						}
					} else if (style in lastPixel.textStyle) {
						attributes += off
					}
				}
				if (pixel.textStyle != lastPixel.textStyle) {
					maybeToggleStyle(Bold, "1", "22")
					maybeToggleStyle(Dim, "2", "22")
					maybeToggleStyle(Italic, "3", "23")
					maybeToggleStyle(Invert, "7", "27")
					maybeToggleStyle(Strikethrough, "9", "29")
				}
				if (pixel.underlineStyle != lastPixel.underlineStyle) {
					attributes += when (pixel.underlineStyle) {
						UnderlineStyle.Unspecified, UnderlineStyle.None -> "24"
						UnderlineStyle.Double if (supportsKittyUnderlines) -> "4:2"
						UnderlineStyle.Curly if (supportsKittyUnderlines) -> "4:3"
						UnderlineStyle.Dotted if (supportsKittyUnderlines) -> "4:4"
						UnderlineStyle.Dashed if (supportsKittyUnderlines) -> "4:5"
						else -> "4"
					}
				}
				if (pixel.underlineColor != lastPixel.underlineColor) {
					attributes.addColor(
						pixel.underlineColor,
						ansiLevel,
						ansiUnderlineColorSelector,
						ansiUnderlineColorReset,
						ansiUnderlineColorOffset,
					)
				}
				if (attributes.isNotEmpty()) {
					appendable.append(CSI)
					attributes.forEachIndexed { index, element ->
						if (index > 0) {
							appendable.append(ansiSeparator)
						}
						appendable.append(element)
					}
					appendable.append(ansiClosingCharacter)
					attributes.clear() // This list is reused!
				}
			}

			if (ansiLevel != AnsiLevel.NONE && pixel.link != openLink) {
				appendable.appendHyperlink(pixel.link)
				openLink = pixel.link
			}

			appendable.appendCodePoint(pixel.codePoint)
			pixel.combining?.let(appendable::append)
			lastPixel = pixel
		}

		if (openLink != null) {
			appendable.appendHyperlink(null)
		}

		if (
			ansiLevel != AnsiLevel.NONE &&
			(
				lastPixel.background.isSpecifiedColor ||
					lastPixel.foreground.isSpecifiedColor ||
					lastPixel.textStyle.isNotEmptyTextStyle ||
					lastPixel.underlineColor.isSpecifiedColor ||
					lastPixel.underlineStyle.isSpecifiedUnderlineStyle
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

internal class TextPixel(codePoint: Int) {
	var codePoint: Int = codePoint
		private set

	/**
	 * The number of columns [codePoint] occupies. Zero for the second half of a two column code
	 * point, whose [codePoint] is not rendered.
	 */
	var columns: Int = 1
		private set

	/** Zero width code points, such as combining marks, rendered after [codePoint]. */
	var combining: String? = null
		private set

	var background: Color = Color.Unspecified
	var foreground: Color = Color.Unspecified
	var textStyle: TextStyle = TextStyle.Empty
	var underlineStyle: UnderlineStyle = UnderlineStyle.Unspecified
	var underlineColor: Color = Color.Unspecified

	/** The URL this pixel links to, if any. */
	var link: String? = null

	fun setCodePoint(codePoint: Int, columns: Int) {
		this.codePoint = codePoint
		this.columns = columns
		combining = null
		// Text drawn over a link is not part of it, unless its own style links it again.
		link = null
	}

	fun appendCombining(codePoint: Int) {
		combining = buildString {
			combining?.let(::append)
			appendCodePoint(codePoint)
		}
	}

	fun isEmpty(): Boolean {
		return codePoint == SpaceCharCodePoint &&
			columns == 1 &&
			combining == null &&
			link == null &&
			background.isUnspecifiedColor &&
			foreground.isUnspecifiedColor &&
			textStyle.isEmptyTextStyle &&
			underlineStyle.isUnspecifiedUnderlineStyle &&
			underlineColor.isUnspecifiedColor
	}

	override fun toString() = buildString {
		append("TextPixel(\"")
		appendCodePoint(codePoint)
		combining?.let(::append)
		append("\"")
		if (background.isSpecifiedColor) {
			append(" bg=")
			append(background)
		}
		if (foreground.isSpecifiedColor) {
			append(" fg=")
			append(foreground)
		}
		// TODO style
		append(')')
	}
}
