package org.unitedsoftware.mosaic

import de.cketti.codepoints.CodePoints
import de.cketti.codepoints.appendCodePoint
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent

internal fun KeyboardEvent.toKeyEventOrNull(): KeyEvent? {
	if (eventType != KeyboardEvent.EventTypePress) {
		return null
	}

	val key = when (val codepoint = codepoint) {
		9 -> "Tab"

		13 -> "Enter"

		27 -> "Escape"

		in 32..126 -> codepoint.toChar().toString()

		127 -> "Backspace"

		57350 -> "ArrowLeft"

		57351 -> "ArrowRight"

		57352 -> "ArrowUp"

		57353 -> "ArrowDown"

		57348 -> "Insert"

		57349 -> "Delete"

		57354 -> "PageUp"

		57355 -> "PageDown"

		57356 -> "Home"

		57357 -> "End"

		in 57364..57398 -> "F" + (codepoint - 57363)

		// Other functional keys of the Kitty keyboard protocol use this private use area.
		in 57344..63743 -> return null

		// Remaining C0 and C1 control characters and surrogates are not keys.
		in 0..31, in 128..159, in 0xD800..0xDFFF -> return null

		// Everything else is text, such as CJK characters committed by an input method.
		else -> if (CodePoints.isValidCodePoint(codepoint)) {
			CodePoints.toChars(codepoint).concatToString()
		} else {
			return null
		}
	}

	return KeyEvent(
		key = key,
		alt = alt,
		ctrl = ctrl,
		shift = shift,
	)
}

/** The text of key events received during a bracketed paste, with line breaks as `\n`. */
internal fun List<KeyboardEvent>.toPastedText(): String = buildString {
	for (event in this@toPastedText) {
		if (event.eventType != KeyboardEvent.EventTypePress) continue
		event.text?.let {
			append(it)
			continue
		}
		val codepoint = event.codepoint
		when {
			codepoint == 13 -> append('\n')

			codepoint == 9 -> append('\t')

			// Other control characters are parsed into keys with modifiers, which are not text.
			event.ctrl || event.alt -> {}

			codepoint < 32 || codepoint in 127..159 || codepoint in 0xD800..0xDFFF -> {}

			// Functional keys of the Kitty keyboard protocol.
			codepoint in 57344..63743 -> {}

			CodePoints.isValidCodePoint(codepoint) -> appendCodePoint(codepoint)
		}
	}
}
