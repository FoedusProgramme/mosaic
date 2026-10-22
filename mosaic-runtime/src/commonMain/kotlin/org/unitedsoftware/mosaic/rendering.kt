package org.unitedsoftware.mosaic

import kotlin.time.TimeMark
import kotlin.time.TimeSource
import org.unitedsoftware.mosaic.terminal.Terminal

internal interface Rendering {
	/**
	 * Render [node] to a single string for display.
	 *
	 * Note: The returned [CharSequence] is only valid until the next call to this function,
	 * as implementations are free to reuse buffers across invocations.
	 */
	fun render(mosaic: Mosaic): CharSequence

	/** Output which restores the terminal after the last [render], such as moving the cursor below. */
	fun finish(): CharSequence = ""
}

internal class DebugRendering(
	private val capabilities: Terminal.Capabilities,
	private val systemClock: TimeSource = TimeSource.Monotonic,
) : Rendering {
	private var lastRender: TimeMark? = null

	fun StringBuilder.appendSurface(canvas: TextCanvas) {
		for (row in 0 until canvas.height) {
			canvas.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
			append("\r\n")
		}
	}

	override fun render(mosaic: Mosaic): CharSequence {
		var failed = false
		val output = buildString {
			lastRender?.let { lastRender ->
				repeat(50) { append('~') }
				append(" +")
				append(lastRender.elapsedNow())
				append("\r\n")
			}
			lastRender = systemClock.markNow()

			mosaic.title()?.let { title ->
				append("TITLE: ")
				append(title)
				append("\r\n")
			}
			mosaic.pointerShape()?.let { shape ->
				append("POINTER: ")
				append(shape)
				append("\r\n")
			}

			append("NODES:\r\n")
			append(mosaic.dumpNodes().replace("\n", "\r\n"))
			append("\r\n\r\n")

			try {
				mosaic.static()?.let { static ->
					append("STATIC:\r\n")
					append(static)
					append("\r\n\r\n")
				}
			} catch (t: Throwable) {
				failed = true
				append(t.stackTraceToString().replace("\n", "\r\n"))
				append("\r\n")
			}

			append("OUTPUT:\r\n")
			try {
				appendSurface(mosaic.draw())
			} catch (t: Throwable) {
				failed = true
				append(t.stackTraceToString().replace("\n", "\r\n"))
			}
		}
		if (failed) {
			throw RuntimeException("Failed\r\n\r\n$output")
		}
		return output
	}
}

/** Writes the window title and other terminal commands ahead of frames. */
internal class TerminalCommandWriter(private val capabilities: Terminal.Capabilities) {
	private var lastTitle: String? = null
	private var titlePushed = false
	private var lastPointerShape: String? = null
	private var pointerShapePushed = false

	fun appendTo(builder: StringBuilder, mosaic: Mosaic) {
		val title = mosaic.title()
		if (title != lastTitle) {
			if (title == null) {
				// Restore the title from before the first one was set.
				builder.append(windowTitlePop)
				titlePushed = false
			} else {
				if (!titlePushed) {
					builder.append(windowTitlePush)
					titlePushed = true
				}
				builder.append(OSC)
				builder.append("2;")
				builder.append(title)
				builder.append(ST)
			}
			lastTitle = title
		}

		val pointerShape = mosaic.pointerShape()
		if (pointerShape != lastPointerShape && capabilities.kittyPointerShape) {
			when {
				// Restore the shape from before the first one was set.
				pointerShape == null -> {
					builder.append(pointerShapePop)
					pointerShapePushed = false
				}

				pointerShapePushed -> builder.append("${OSC}22;=$pointerShape$ST")

				else -> {
					builder.append("${OSC}22;>$pointerShape$ST")
					pointerShapePushed = true
				}
			}
			lastPointerShape = pointerShape
		}

		mosaic.terminalCommands()?.let(builder::append)
	}

	fun appendFinishTo(builder: StringBuilder) {
		if (titlePushed) {
			builder.append(windowTitlePop)
		}
		if (pointerShapePushed) {
			builder.append(pointerShapePop)
		}
	}
}

/**
 * Renders frames inline, below whatever the terminal showed before, like most command line programs.
 * Rows which did not change since the last frame are not written again.
 */
internal class AnsiRendering(
	private val capabilities: Terminal.Capabilities,
	/** The size of the terminal, which limits the height of the output, or null when unknown. */
	private val terminalSize: () -> Terminal.Size? = { null },
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private val rowBuilder = StringBuilder(100)

	/** The rows of the last output, as they were written. */
	private var lastRows = emptyList<String>()
	private var lastSize: Terminal.Size? = null

	/**
	 * The row of the last output the cursor was left on. The size of [lastRows] when it rests on the
	 * line below the output.
	 */
	private var cursorRow = 0

	/** Whether the cursor was shown at a position placed with [DrawScope.drawCursor]. */
	private var cursorShown = false

	/** Whether the cursor is in the first column. Output is assumed to start on a new line. */
	private var atLineStart = true

	/** The graphics of the last output, in its rows. */
	private var lastGraphics = emptyList<GraphicPlacement>()

	private val commandWriter = TerminalCommandWriter(capabilities)

	override fun render(mosaic: Mosaic): CharSequence {
		return stringBuilder.apply {
			clear()

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputEnable)
			}
			if (cursorShown) {
				// Avoid the cursor flickering across the screen while the frame is written.
				append(cursorHide)
			}
			// Drawing first lets it request commands, like sending an image, for this frame.
			val surface = mosaic.draw()
			commandWriter.appendTo(this, mosaic)

			var current = cursorRow
			fun moveTo(row: Int) {
				if (row < current) {
					// Previous line, which also moves to the first column.
					append(CSI)
					append(current - row)
					append('F')
				} else if (row > current) {
					// Line feeds also create the line when the output reaches the bottom of the screen.
					repeat(row - current) { append("\r\n") }
				} else if (!atLineStart) {
					append('\r')
				}
				current = row
				atLineStart = true
			}

			val size = terminalSize()
			var previousRows = lastRows
			val static = mosaic.static()
			if (static != null || (size != lastSize && previousRows.isNotEmpty())) {
				// Static output goes above the output, and a resized terminal may have reflowed the
				// previous output, so start over from the top.
				if (previousRows.isNotEmpty()) {
					moveTo(0)
					append(clearDisplay)
				}
				if (static != null) {
					append(static)
					append("\r\n")
				}
				previousRows = emptyList()
				current = 0
			}

			// Rows which do not fit are cut from the top so the latest output stays visible. The output
			// cannot be taller than the terminal since the cursor could not move back up to its start.
			// One line below the output is left for the cursor.
			val maxRows = size?.let { (it.rows - 1).coerceAtLeast(1) } ?: Int.MAX_VALUE
			val firstRow = (surface.height - maxRows).coerceAtLeast(0)
			val rows = List(surface.height - firstRow) { index ->
				rowBuilder.setLength(0)
				surface.appendRowTo(rowBuilder, firstRow + index, capabilities.ansiLevel, capabilities.kittyUnderline)
				rowBuilder.toString()
			}

			val graphics = (surface as? TextSurface)?.graphics.orEmpty().mapNotNull { graphic ->
				if (graphic.y < firstRow) return@mapNotNull null
				GraphicPlacement(graphic.x, graphic.y - firstRow, graphic.columns, graphic.rows, graphic.data)
			}
			// Rows of images which went away are written again, which erases their pixels.
			val rewrite = BooleanArray(rows.size)
			if (previousRows.isNotEmpty()) {
				for (graphic in lastGraphics) {
					if (graphic in graphics) continue
					for (row in graphic.y until minOf(graphic.y + graphic.rows, rows.size)) rewrite[row] = true
				}
			}

			for ((index, row) in rows.withIndex()) {
				if (!rewrite[index] && index < previousRows.size && previousRows[index] == row) continue
				rewrite[index] = true
				moveTo(index)
				if (index < previousRows.size) {
					append(clearLine)
				}
				append(row)
				atLineStart = row.isEmpty()
			}

			// Images go over the rows, again whenever one of their rows was written, which erases them.
			for (graphic in graphics) {
				val end = minOf(graphic.y + graphic.rows, rows.size)
				if (graphic in lastGraphics && (graphic.y until end).none { rewrite[it] }) continue
				moveTo(graphic.y)
				if (graphic.x > 0) {
					append(CSI)
					append(graphic.x + 1)
					append('G')
				}
				// Where the cursor ends up after an image differs between terminals.
				append(cursorSave)
				append(graphic.data)
				append(cursorRestore)
				atLineStart = graphic.x == 0
			}
			lastGraphics = graphics

			moveTo(rows.size)
			if (rows.size < previousRows.size) {
				// Clear the rows of the last output beyond the new one.
				append(clearDisplay)
			}

			val cursor = (surface as? TextSurface)?.cursor?.takeIf { it.y >= firstRow }
			if (cursor != null) {
				moveTo(cursor.y - firstRow)
				if (cursor.x > 0) {
					append(CSI)
					append(cursor.x + 1)
					append('G')
					atLineStart = false
				}
				// When the terminal cannot hide its cursor, it is visible anyway.
				cursorShown = capabilities.cursorVisibility
				if (cursorShown) {
					append(cursorShow)
				}
			} else {
				cursorShown = false
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			cursorRow = current
			lastRows = rows
			lastSize = size
		}
	}

	override fun finish(): CharSequence = buildString {
		// Move below the output so whatever is printed next does not overwrite it.
		repeat(lastRows.size - cursorRow) { append("\r\n") }
		if (cursorShown) {
			append(cursorHide)
		}
		commandWriter.appendFinishTo(this)
	}
}

/**
 * Renders frames on the terminal's alternate screen, which is restored to what it showed before
 * when finished. Static output is written to the regular screen once finished.
 */
internal class AlternateScreenRendering(
	private val capabilities: Terminal.Capabilities,
	private val terminalSize: () -> Terminal.Size?,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private val rowBuilder = StringBuilder(100)
	private val pendingStatic = StringBuilder()

	private val commandWriter = TerminalCommandWriter(capabilities)

	private var started = false
	private var lastRows = emptyList<String>()
	private var lastSize: Terminal.Size? = null
	private var cursorShown = false
	private var lastGraphics = emptyList<GraphicPlacement>()

	override fun render(mosaic: Mosaic): CharSequence {
		return stringBuilder.apply {
			clear()

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputEnable)
			}
			if (!started) {
				append(alternateScreenEnable)
				started = true
			}
			if (cursorShown) {
				append(cursorHide)
			}
			// Drawing first lets it request commands, like sending an image, for this frame.
			val surface = mosaic.draw()
			commandWriter.appendTo(this, mosaic)

			mosaic.static()?.let { static ->
				pendingStatic.append(static)
				pendingStatic.append("\r\n")
			}

			val size = terminalSize()
			var previousRows = lastRows
			if (size != lastSize) {
				append(CSI)
				append("2J")
				previousRows = emptyList()
			}

			val rowCount = size?.rows?.let { minOf(it, surface.height) } ?: surface.height
			val rows = List(rowCount) { row ->
				rowBuilder.setLength(0)
				surface.appendRowTo(rowBuilder, row, capabilities.ansiLevel, capabilities.kittyUnderline)
				rowBuilder.toString()
			}

			val graphics = (surface as? TextSurface)?.graphics.orEmpty().filter { it.y + it.rows <= rows.size }
			// Rows of images which went away are written again, which erases their pixels.
			val rewrite = BooleanArray(rows.size)
			if (previousRows.isNotEmpty()) {
				for (graphic in lastGraphics) {
					if (graphic in graphics) continue
					for (row in graphic.y until minOf(graphic.y + graphic.rows, rows.size)) rewrite[row] = true
				}
			}

			for ((index, row) in rows.withIndex()) {
				if (!rewrite[index] && index < previousRows.size && previousRows[index] == row) continue
				rewrite[index] = true
				appendCursorPosition(index, 0)
				append(clearLine)
				append(row)
			}

			// Images go over the rows, again whenever one of their rows was written, which erases them.
			for (graphic in graphics) {
				if (graphic in lastGraphics && (graphic.y until graphic.y + graphic.rows).none { rewrite[it] }) continue
				appendCursorPosition(graphic.y, graphic.x)
				append(cursorSave)
				append(graphic.data)
				append(cursorRestore)
			}
			lastGraphics = graphics
			if (rows.size < previousRows.size) {
				appendCursorPosition(rows.size, 0)
				append(clearDisplay)
			}

			val cursor = (surface as? TextSurface)?.cursor?.takeIf { it.y < rows.size }
			if (cursor != null) {
				appendCursorPosition(cursor.y, cursor.x)
				cursorShown = capabilities.cursorVisibility
				if (cursorShown) {
					append(cursorShow)
				}
			} else {
				cursorShown = false
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			lastRows = rows
			lastSize = size
		}
	}

	private fun StringBuilder.appendCursorPosition(row: Int, column: Int) {
		append(CSI)
		append(row + 1)
		append(';')
		append(column + 1)
		append('H')
	}

	override fun finish(): CharSequence = buildString {
		if (cursorShown) {
			append(cursorHide)
		}
		if (started) {
			append(alternateScreenDisable)
		}
		commandWriter.appendFinishTo(this)
		append(pendingStatic)
	}
}
