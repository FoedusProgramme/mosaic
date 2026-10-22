package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.unitedsoftware.mosaic.LocalTerminalState
import org.unitedsoftware.mosaic.layout.DrawScope
import org.unitedsoftware.mosaic.layout.Measurable
import org.unitedsoftware.mosaic.layout.MeasurePolicy
import org.unitedsoftware.mosaic.layout.MeasureResult
import org.unitedsoftware.mosaic.layout.MeasureScope
import org.unitedsoftware.mosaic.layout.TextCanvasDrawScope
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.AnsiLevel
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.ui.unit.Constraints
import org.unitedsoftware.mosaic.ui.unit.constrainHeight
import org.unitedsoftware.mosaic.ui.unit.constrainWidth

/**
 * An image of [width] by [height] pixels, with the colors of its [pixels] as `0xAARRGGBB`, row by
 * row from the top left. The pixels must not change once the bitmap is shown.
 */
@Stable
public class ImageBitmap(
	public val width: Int,
	public val height: Int,
	public val pixels: IntArray,
) {
	init {
		require(width > 0 && height > 0) { "Size must be positive: ${width}x$height" }
		require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
	}

	override fun toString(): String = "ImageBitmap(${width}x$height)"
}

/**
 * Shows [bitmap], scaled to fit the incoming constraints while keeping its aspect ratio, and at
 * most as large as its pixels on the terminal when its cell size is known.
 *
 * Terminals which support the Kitty graphics protocol with Unicode placeholders, the inline images
 * of iTerm2, or Sixel show the image itself. Others show it with half block characters, two pixels
 * per cell, in the colors of the image.
 *
 * @param contentDescription What the image shows, for debug output.
 */
@Composable
public fun Image(
	bitmap: ImageBitmap,
	modifier: Modifier = Modifier,
	contentDescription: String? = null,
) {
	val size = LocalTerminalState.current.size
	val cell = size.cellSize()
	val capabilities = LocalTerminalCapabilities.current
	val send = LocalTerminalCommands.current
	val blocks = remember(bitmap) { HalfBlockCache(bitmap) }
	// Placeholder cells name their image by color, which needs all 24 bits.
	val kitty = if (
		capabilities.kittyGraphics &&
		capabilities.ansiLevel == AnsiLevel.TRUECOLOR &&
		supportsKittyPlaceholders(LocalTerminalName.current)
	) {
		remember(bitmap, send) { KittyImage(bitmap, send) }
	} else {
		null
	}
	val name = LocalTerminalName.current
	val overlay = when {
		kitty != null -> null
		supportsInlineImages(name) -> remember(bitmap) { OverlayCache(bitmap, ::encodeInlineImage) }
		capabilities.sixelGraphics -> remember(bitmap) { OverlayCache(bitmap) { pixels, width, height, _, _ -> encodeSixel(pixels, width, height) } }
		else -> null
	}
	DisposableEffect(kitty) {
		onDispose {
			kitty?.delete()
		}
	}
	Layout(
		content = {},
		modifier = modifier.drawBehind {
			if (kitty != null && width <= kittyMaxCells && height <= kittyMaxCells) {
				kitty.draw(this, width, height, cell)
			} else if (overlay != null) {
				(this as TextCanvasDrawScope).drawGraphic(0, 0, width, height, overlay.encode(width, height, cell))
			} else {
				blocks.draw(this, width, height)
			}
		},
		debugInfo = { "Image(${contentDescription ?: bitmap})" },
		measurePolicy = ImageMeasurePolicy(bitmap, cell),
	)
}

/** The size of a cell in pixels, assuming a typical one when the terminal does not report it. */
internal fun Terminal.Size.cellSize(): Pair<Double, Double> {
	if (width > 0 && height > 0 && columns > 0 && rows > 0) {
		return width.toDouble() / columns to height.toDouble() / rows
	}
	return 8.0 to 16.0
}

private class ImageMeasurePolicy(
	private val bitmap: ImageBitmap,
	private val cell: Pair<Double, Double>,
) : MeasurePolicy {
	override fun MeasureScope.measure(
		measurables: List<Measurable>,
		constraints: Constraints,
	): MeasureResult {
		val (columns, rows) = fit(bitmap, cell, constraints)
		return layout(columns, rows) {}
	}
}

/** The number of columns and rows to show [bitmap] in. */
internal fun fit(bitmap: ImageBitmap, cell: Pair<Double, Double>, constraints: Constraints): Pair<Int, Int> {
	val (cellWidth, cellHeight) = cell
	// The image in cells, as a fraction.
	var columns = bitmap.width / cellWidth
	var rows = bitmap.height / cellHeight
	val maxColumns = constraints.maxWidth.toDouble()
	val maxRows = constraints.maxHeight.toDouble()
	val scale = minOf(1.0, maxColumns / columns, maxRows / rows)
	columns *= scale
	rows *= scale
	// Fill a fixed size, as the image is centered inside its cells by the protocols anyway.
	val width = constraints.constrainWidth(ceil(columns - 0.01).roundToInt().coerceAtLeast(1))
	val height = constraints.constrainHeight(ceil(rows - 0.01).roundToInt().coerceAtLeast(1))
	return width to height
}

/**
 * The escape sequence which draws an image over its cells at the cursor, like Sixel, kept for the
 * last size it was drawn at.
 */
private class OverlayCache(
	private val bitmap: ImageBitmap,
	private val encode: (pixels: IntArray, width: Int, height: Int, columns: Int, rows: Int) -> String,
) {
	private var columns = -1
	private var rows = -1
	private var data = ""

	fun encode(columns: Int, rows: Int, cell: Pair<Double, Double>): String {
		if (columns != this.columns || rows != this.rows) {
			// As large as the cells allow while keeping the aspect ratio, so it covers no other cells.
			val scale = minOf(columns * cell.first / bitmap.width, rows * cell.second / bitmap.height)
			val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
			val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
			data = encode(bitmap.resample(width, height), width, height, columns, rows)
			this.columns = columns
			this.rows = rows
		}
		return data
	}
}

/** The colors of a half block rendering, kept for the last size it was drawn at. */
private class HalfBlockCache(private val bitmap: ImageBitmap) {
	private var columns = -1
	private var rows = -1
	private var colors = IntArray(0)

	fun draw(scope: DrawScope, columns: Int, rows: Int) {
		if (columns <= 0 || rows <= 0) return
		if (columns != this.columns || rows != this.rows) {
			colors = bitmap.resample(columns, rows * 2)
			this.columns = columns
			this.rows = rows
		}
		for (row in 0 until rows) {
			for (column in 0 until columns) {
				val top = colors[row * 2 * columns + column]
				val bottom = colors[(row * 2 + 1) * columns + column]
				val topVisible = top ushr 24 >= 0x80
				val bottomVisible = bottom ushr 24 >= 0x80
				when {
					topVisible && bottomVisible -> scope.drawText(row, column, "▀", foreground = top.toColor(), background = bottom.toColor())
					topVisible -> scope.drawText(row, column, "▀", foreground = top.toColor())
					bottomVisible -> scope.drawText(row, column, "▄", foreground = bottom.toColor())
				}
			}
		}
	}
}

private fun Int.toColor() = Color(this shr 16 and 0xFF, this shr 8 and 0xFF, this and 0xFF)

/**
 * Scales this bitmap to [width] by [height] pixels, averaging the pixels each new one covers so
 * that shrinking it does not drop detail, and weighting colors by their opacity.
 */
internal fun ImageBitmap.resample(width: Int, height: Int): IntArray {
	val result = IntArray(width * height)
	val scaleX = this.width.toDouble() / width
	val scaleY = this.height.toDouble() / height
	for (y in 0 until height) {
		val top = (y * scaleY).toInt()
		val bottom = ((y + 1) * scaleY).toInt().coerceIn(top + 1, this.height)
		for (x in 0 until width) {
			val left = (x * scaleX).toInt()
			val right = ((x + 1) * scaleX).toInt().coerceIn(left + 1, this.width)
			var alpha = 0L
			var red = 0L
			var green = 0L
			var blue = 0L
			for (sourceY in top until bottom) {
				for (sourceX in left until right) {
					val pixel = pixels[sourceY * this.width + sourceX]
					val a = (pixel ushr 24).toLong()
					alpha += a
					red += a * (pixel shr 16 and 0xFF)
					green += a * (pixel shr 8 and 0xFF)
					blue += a * (pixel and 0xFF)
				}
			}
			val count = (bottom - top) * (right - left)
			result[y * width + x] = if (alpha == 0L) {
				0
			} else {
				((alpha / count).toInt() shl 24) or
					((red / alpha).toInt() shl 16) or
					((green / alpha).toInt() shl 8) or
					(blue / alpha).toInt()
			}
		}
	}
	return result
}

/** The name and version the terminal reported, if any. */
internal val LocalTerminalName = staticCompositionLocalOf<String?> { null }

/**
 * Whether the terminal named [name] supports the Unicode placeholders of the Kitty graphics
 * protocol. Terminals like iTerm2 and WezTerm support the protocol but not placeholders, and there
 * is no way to query for them, so only terminals known to support them use them.
 */
internal fun supportsKittyPlaceholders(name: String?): Boolean {
	if (name == null) return false
	val lowercase = name.lowercase()
	return lowercase.startsWith("kitty") || lowercase.startsWith("ghostty")
}

internal val LocalTerminalCapabilities = staticCompositionLocalOf<Terminal.Capabilities> {
	throw IllegalStateException("No terminal capabilities provided")
}
