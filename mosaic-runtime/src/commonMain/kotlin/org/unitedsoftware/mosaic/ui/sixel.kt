package org.unitedsoftware.mosaic.ui

/**
 * Encodes [pixels] of [width] by [height] as Sixel graphics, with colors reduced to a fixed
 * palette of 6 levels of red, 7 of green, and 6 of blue, which most terminals with Sixel support
 * hold in their 256 color registers. Pixels which are less than half opaque are left transparent.
 *
 * See https://vt100.net/docs/vt3xx-gp/chapter14.html.
 */
internal fun encodeSixel(pixels: IntArray, width: Int, height: Int): String = buildString {
	// P2 = 1 keeps pixels without a color as they are, which makes them transparent.
	append("\u001BP0;1;0q")
	// Square pixels, and the size of the image.
	append("\"1;1;")
	append(width)
	append(';')
	append(height)

	val indices = IntArray(pixels.size) { index ->
		val pixel = pixels[index]
		if (pixel ushr 24 < 0x80) -1 else paletteIndex(pixel)
	}
	val used = BooleanArray(PaletteSize)
	for (index in indices) {
		if (index >= 0) used[index] = true
	}
	for (color in 0 until PaletteSize) {
		if (!used[color]) continue
		val red = color / (Greens * Blues)
		val green = color / Blues % Greens
		val blue = color % Blues
		// Color components are percentages.
		append('#')
		append(color)
		append(";2;")
		append(red * 100 / (Reds - 1))
		append(';')
		append(green * 100 / (Greens - 1))
		append(';')
		append(blue * 100 / (Blues - 1))
	}

	val bandColors = BooleanArray(PaletteSize)
	for (bandTop in 0 until height step 6) {
		val bandBottom = minOf(bandTop + 6, height)
		bandColors.fill(false)
		for (y in bandTop until bandBottom) {
			for (x in 0 until width) {
				val index = indices[y * width + x]
				if (index >= 0) bandColors[index] = true
			}
		}
		var firstColor = true
		for (color in 0 until PaletteSize) {
			if (!bandColors[color]) continue
			// Each color of a band is drawn over the same six rows, going back with $.
			if (!firstColor) append('$')
			firstColor = false
			append('#')
			append(color)
			var run = 0
			var runChar = ' '
			for (x in 0 until width) {
				var bits = 0
				for (y in bandTop until bandBottom) {
					if (indices[y * width + x] == color) bits = bits or (1 shl (y - bandTop))
				}
				val char = (0x3F + bits).toChar()
				if (char == runChar) {
					run++
				} else {
					appendRun(run, runChar)
					run = 1
					runChar = char
				}
			}
			// Empty columns at the end need no drawing.
			if (runChar != '?') appendRun(run, runChar)
		}
		// The next band.
		append('-')
	}
	append("\u001B\\")
}

private fun StringBuilder.appendRun(count: Int, char: Char) {
	when {
		count == 0 -> {}

		count > 3 -> {
			append('!')
			append(count)
			append(char)
		}

		else -> repeat(count) { append(char) }
	}
}

private const val Reds = 6
private const val Greens = 7
private const val Blues = 6
private const val PaletteSize = Reds * Greens * Blues

private fun paletteIndex(pixel: Int): Int {
	val red = ((pixel shr 16 and 0xFF) * (Reds - 1) + 127) / 255
	val green = ((pixel shr 8 and 0xFF) * (Greens - 1) + 127) / 255
	val blue = ((pixel and 0xFF) * (Blues - 1) + 127) / 255
	return (red * Greens + green) * Blues + blue
}
