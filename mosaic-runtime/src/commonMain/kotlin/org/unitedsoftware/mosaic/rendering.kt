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

	/**
	 * Output which hands the terminal over to other programs, like [finish] does, after which
	 * [reacquire] starts over since the screen shows whatever those programs left.
	 */
	fun release(): CharSequence = finish()

	fun reacquire() {}
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

/** Maps rows of the screen to rows of the drawn surface, for mouse events. */
internal interface PointerMapping {
	/** The row of the surface shown in the 0-based row [screenRow] of the screen, or null if unknown. */
	fun surfaceRow(screenRow: Int): Int?

	/** Receives the answer to a cursor position request, with 1-based [row] and [column]. */
	fun onCursorPosition(row: Int, column: Int) {}

	/** The surface fills the screen from its top, like on the alternate screen. */
	object Identity : PointerMapping {
		override fun surfaceRow(screenRow: Int) = screenRow
	}
}

/** Writes the window title and other terminal commands ahead of frames. */
internal class TerminalCommandWriter(
	private val capabilities: Terminal.Capabilities,
	/** Whether to enable mouse tracking with the first frame, and disable it when done. */
	private val mouse: Boolean = false,
) {
	private var mouseEnabled = false
	private var lastTitle: String? = null
	private var titlePushed = false
	private var lastPointerShape: String? = null
	private var pointerShapePushed = false

	fun appendTo(builder: StringBuilder, mosaic: Mosaic) {
		if (mouse && !mouseEnabled) {
			builder.append(mouseTrackingEnable)
			mouseEnabled = true
		}
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

	/** Disables what other programs should not get while the terminal is released, until the next frame. */
	fun appendReleaseTo(builder: StringBuilder) {
		if (mouseEnabled) {
			builder.append(mouseTrackingDisable)
			mouseEnabled = false
		}
	}

	/** Sends the title and pointer shape again, which other programs may have changed. */
	fun reacquire() {
		lastTitle = null
		lastPointerShape = null
	}

	fun appendFinishTo(builder: StringBuilder) {
		if (mouseEnabled) {
			builder.append(mouseTrackingDisable)
		}
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
	/**
	 * Whether to report mouse events. The output can start anywhere on the screen, so its position
	 * is asked for with a cursor position request whenever it may have moved.
	 */
	private val mouse: Boolean = false,
) : Rendering,
	PointerMapping {
	/** The row of the output the cursor was on, and the first row of the surface shown, per request. */
	private val positionRequests = ArrayDeque<Pair<Int, Int>>()

	/** The screen row of the first row of the output, once known. */
	private var outputTop: Int? = null
	private var outputFirstRow = 0

	override fun surfaceRow(screenRow: Int): Int? {
		val top = outputTop ?: return null
		return screenRow - top + outputFirstRow
	}

	override fun onCursorPosition(row: Int, column: Int) {
		val (cursorRow, firstRow) = positionRequests.removeFirstOrNull() ?: return
		outputTop = row - 1 - cursorRow
		outputFirstRow = firstRow
	}

	private val stringBuilder = StringBuilder(100)

	/**
	 * The surface of the last output, compared with the next one to only write rows which changed,
	 * and which rows of it were shown.
	 */
	private var lastSurface: TextSurface? = null
	private var lastFirstRow = 0
	private var lastRowCount = 0
	private var lastSize: Terminal.Size? = null

	/**
	 * The row of the last output the cursor was left on, which is its last row unless it was placed
	 * with [DrawScope.drawCursor].
	 */
	private var cursorRow = 0

	/** Whether the cursor was shown at a position placed with [DrawScope.drawCursor]. */
	private var cursorShown = false

	/** Whether the cursor is in the first column. Output is assumed to start on a new line. */
	private var atLineStart = true

	/** The graphics of the last output, in its rows. */
	private var lastGraphics = emptyList<GraphicPlacement>()

	private val commandWriter = TerminalCommandWriter(capabilities, mouse)

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
			var previousRowCount = lastRowCount
			val static = mosaic.static()
			if (static != null || (size != lastSize && previousRowCount > 0)) {
				// Static output goes above the output, and a resized terminal may have reflowed the
				// previous output, so start over from the top.
				if (previousRowCount > 0) {
					moveTo(0)
					append(clearDisplay)
				}
				if (static != null) {
					append(static)
					append("\r\n")
				}
				previousRowCount = 0
				current = 0
			}

			// Rows which do not fit are cut from the top so the latest output stays visible. The output
			// cannot be taller than the terminal since the cursor could not move back up to its start.
			val maxRows = size?.let { it.rows.coerceAtLeast(1) } ?: Int.MAX_VALUE
			val firstRow = (surface.height - maxRows).coerceAtLeast(0)
			val rowCount = surface.height - firstRow
			val textSurface = surface as? TextSurface
			val previousSurface = lastSurface

			val graphics = (surface as? TextSurface)?.graphics.orEmpty().mapNotNull { graphic ->
				if (graphic.y < firstRow) return@mapNotNull null
				GraphicPlacement(graphic.x, graphic.y - firstRow, graphic.columns, graphic.rows, graphic.data)
			}
			// Rows of images which went away are written again, which erases their pixels.
			val rewrite = BooleanArray(rowCount)
			if (previousRowCount > 0) {
				for (graphic in lastGraphics) {
					if (graphic in graphics) continue
					for (row in graphic.y until minOf(graphic.y + graphic.rows, rowCount)) rewrite[row] = true
				}
			}

			for (index in 0 until rowCount) {
				// Only rows which changed are written, without building the others.
				if (
					!rewrite[index] &&
					index < previousRowCount &&
					textSurface != null &&
					previousSurface != null &&
					textSurface.rowEquals(firstRow + index, previousSurface, lastFirstRow + index)
				) {
					continue
				}
				rewrite[index] = true
				moveTo(index)
				if (index < previousRowCount) {
					append(clearLine)
				}
				val start = length
				surface.appendRowTo(this, firstRow + index, capabilities.ansiLevel, capabilities.kittyUnderline)
				atLineStart = length == start
			}

			// Images go over the rows, again whenever one of their rows was written, which erases them.
			for (graphic in graphics) {
				val end = minOf(graphic.y + graphic.rows, rowCount)
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

			if (rowCount < previousRowCount) {
				// Clear the rows of the last output beyond the new one.
				moveTo(rowCount)
				append(clearDisplay)
			}
			// Rest on the last row rather than below it, since a line feed after output as tall as the
			// terminal would scroll its first row out of reach.
			val lastRow = (rowCount - 1).coerceAtLeast(0)
			if (current != lastRow) moveTo(lastRow)

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

			// The output moves on the screen when it grows past the bottom of the screen, when static
			// output is written above it, and when the terminal is resized.
			if (mouse && (outputTop == null || rowCount != lastRowCount || static != null || size != lastSize)) {
				append(cursorPositionRequest)
				positionRequests.addLast(current to firstRow)
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			cursorRow = current
			lastSurface = textSurface
			lastFirstRow = firstRow
			lastRowCount = rowCount
			lastSize = size
		}
	}

	override fun finish(): CharSequence = buildString {
		// Move below the output so whatever is printed next does not overwrite it.
		repeat(lastRowCount - cursorRow) { append("\r\n") }
		if (cursorShown) {
			append(cursorHide)
		}
		commandWriter.appendFinishTo(this)
	}

	override fun release(): CharSequence = buildString {
		// Other programs write below the output.
		repeat(lastRowCount - cursorRow) { append("\r\n") }
		commandWriter.appendReleaseTo(this)
	}

	override fun reacquire() {
		commandWriter.reacquire()
		// The output starts over below whatever the other programs printed.
		lastSurface = null
		lastRowCount = 0
		lastGraphics = emptyList()
		cursorRow = 0
		cursorShown = false
		atLineStart = true
		outputTop = null
		positionRequests.clear()
	}
}

/**
 * Renders frames on the terminal's alternate screen, which is restored to what it showed before
 * when finished. Static output is written to the regular screen once finished.
 */
internal class AlternateScreenRendering(
	private val capabilities: Terminal.Capabilities,
	private val terminalSize: () -> Terminal.Size?,
	/** Whether to report mouse events. */
	mouse: Boolean = false,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private val pendingStatic = StringBuilder()

	private val commandWriter = TerminalCommandWriter(capabilities, mouse)

	private var started = false

	/** The surface of the last output, compared with the next one to only write rows which changed. */
	private var lastSurface: TextSurface? = null
	private var lastRowCount = 0
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
			var previousRowCount = lastRowCount
			if (size != lastSize) {
				append(CSI)
				append("2J")
				previousRowCount = 0
			}

			val rowCount = size?.rows?.let { minOf(it, surface.height) } ?: surface.height
			val textSurface = surface as? TextSurface
			val previousSurface = lastSurface

			val graphics = (surface as? TextSurface)?.graphics.orEmpty().filter { it.y + it.rows <= rowCount }
			// Rows of images which went away are written again, which erases their pixels.
			val rewrite = BooleanArray(rowCount)
			if (previousRowCount > 0) {
				for (graphic in lastGraphics) {
					if (graphic in graphics) continue
					for (row in graphic.y until minOf(graphic.y + graphic.rows, rowCount)) rewrite[row] = true
				}
			}

			for (index in 0 until rowCount) {
				// Only rows which changed are written, without building the others.
				if (
					!rewrite[index] &&
					index < previousRowCount &&
					textSurface != null &&
					previousSurface != null &&
					textSurface.rowEquals(index, previousSurface, index)
				) {
					continue
				}
				rewrite[index] = true
				appendCursorPosition(index, 0)
				append(clearLine)
				surface.appendRowTo(this, index, capabilities.ansiLevel, capabilities.kittyUnderline)
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
			if (rowCount < previousRowCount) {
				appendCursorPosition(rowCount, 0)
				append(clearDisplay)
			}

			val cursor = (surface as? TextSurface)?.cursor?.takeIf { it.y < rowCount }
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

			lastSurface = textSurface
			lastRowCount = rowCount
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

	override fun release(): CharSequence = buildString {
		// Other programs get the regular screen.
		if (started) {
			append(alternateScreenDisable)
		}
		commandWriter.appendReleaseTo(this)
	}

	override fun reacquire() {
		commandWriter.reacquire()
		// The next frame enters the alternate screen again and draws everything.
		started = false
		lastSurface = null
		lastRowCount = 0
		lastSize = null
		lastGraphics = emptyList()
		cursorShown = false
	}
}
