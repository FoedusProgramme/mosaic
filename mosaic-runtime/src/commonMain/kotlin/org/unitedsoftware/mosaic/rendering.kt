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

internal class AnsiRendering(
	private val capabilities: Terminal.Capabilities,
) : Rendering {
	private val stringBuilder = StringBuilder(100)
	private var lastHeight = 0

	/**
	 * The row of the last output the cursor was left on, or [lastHeight] when it rests on the line
	 * below the output.
	 */
	private var cursorRow = 0

	/** Whether the cursor was shown at a position placed with [DrawScope.drawCursor]. */
	private var cursorShown = false

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

			var staleLines = lastHeight
			if (cursorRow > 0) {
				// Move to start of previous output.
				append(CSI)
				append(cursorRow)
				append('F')
			} else if (lastHeight > 0) {
				append('\r')
			}

			fun appendSurface(canvas: TextCanvas) {
				for (row in 0 until canvas.height) {
					if (staleLines-- > 0) {
						// We have previously drawn on this line. Clear first to be safe. For terminals which
						// do not support synchronized rendering, this may allow seeing a partial row render.
						append(clearLine)
					}
					canvas.appendRowTo(this, row, capabilities.ansiLevel, capabilities.kittyUnderline)
					append("\r\n")
				}
			}

			mosaic.static()?.let { static ->
				// We don't know the width or height of static strings, and we don't want to waste time
				// parsing them to get an accurate count of each to minimally clear only the lines it will
				// write. Instead, just clear everything to ensure we neither leave old cells nor attempt
				// a clear to end-of-line when the cursor is on the last column (thus clearing that cell).
				if (staleLines > 0) {
					append(clearDisplay)
					staleLines = 0
				}
				append(static)
				append("\r\n")
			}

			val surface = mosaic.draw()
			appendSurface(surface)

			// If the new output contains fewer lines than the last output, clear those old lines.
			if (staleLines > 0) {
				append(clearDisplay)
			}

			val cursor = (surface as? TextSurface)?.cursor
			if (cursor != null) {
				append(CSI)
				append(surface.height - cursor.y)
				append('F')
				if (cursor.x > 0) {
					append(CSI)
					append(cursor.x + 1)
					append('G')
				}
				cursorRow = cursor.y
				// When the terminal cannot hide its cursor, it is visible anyway.
				cursorShown = capabilities.cursorVisibility
				if (cursorShown) {
					append(cursorShow)
				}
			} else {
				cursorRow = surface.height
				cursorShown = false
			}

			if (capabilities.synchronizedOutput) {
				append(synchronizedOutputDisable)
			}

			lastHeight = surface.height
		}
	}

	override fun finish(): CharSequence = buildString {
		if (cursorRow < lastHeight) {
			// Move below the output so whatever is printed next does not overwrite it.
			append(CSI)
			append(lastHeight - cursorRow)
			append('E')
		}
		if (cursorShown) {
			append(cursorHide)
		}
	}
}
