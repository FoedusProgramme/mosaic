package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.PointerButton
import org.unitedsoftware.mosaic.layout.PointerEventType
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.layout.focusTarget
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.onPasteEvent
import org.unitedsoftware.mosaic.layout.onPointerEvent
import org.unitedsoftware.mosaic.layout.rememberFocusTarget
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.text.TextRow
import org.unitedsoftware.mosaic.text.columnWidth
import org.unitedsoftware.mosaic.text.forEachCodePointWithWidth
import org.unitedsoftware.mosaic.text.wrap

/**
 * The editable text of a [TextField] and the position of its cursor.
 *
 * The cursor is an index into [text] between two characters, where 0 is before the first one and
 * the length of [text] is after the last one.
 */
@Stable
public class TextFieldState(initialText: String = "") {
	private var _text by mutableStateOf(initialText)
	private var _cursor by mutableIntStateOf(initialText.length)

	/** Setting the text moves the cursor to its end. */
	public var text: String
		get() = _text
		set(value) {
			_text = value
			_cursor = value.length
		}

	/** Setting the cursor moves it to the closest character boundary within the text. */
	public var cursor: Int
		get() = _cursor
		set(value) {
			_cursor = _text.characterBoundaryAtOrBefore(value.coerceIn(0, _text.length))
		}

	/** Replaces any text by [text] at the cursor, and moves the cursor after it. */
	public fun insert(text: String) {
		_text = _text.substring(0, _cursor) + text + _text.substring(_cursor)
		_cursor += text.length
	}

	public fun clear() {
		text = ""
	}

	internal fun deleteBackward() {
		if (_cursor == 0) return
		val start = _text.previousCharacterBoundary(_cursor)
		_text = _text.removeRange(start, _cursor)
		_cursor = start
	}

	internal fun deleteForward() {
		if (_cursor == _text.length) return
		_text = _text.removeRange(_cursor, _text.nextCharacterBoundary(_cursor))
	}

	internal fun deleteWordBackward() {
		var start = _cursor
		while (start > 0 && _text[start - 1].isWhitespace()) start--
		while (start > 0 && !_text[start - 1].isWhitespace()) start--
		_text = _text.removeRange(start, _cursor)
		_cursor = start
	}

	internal fun deleteToLineStart() {
		val start = lineStart()
		_text = _text.removeRange(start, _cursor)
		_cursor = start
	}

	internal fun deleteToLineEnd() {
		_text = _text.removeRange(_cursor, lineEnd())
	}

	internal fun moveLeft() {
		_cursor = _text.previousCharacterBoundary(_cursor)
	}

	internal fun moveRight() {
		_cursor = _text.nextCharacterBoundary(_cursor)
	}

	internal fun moveToLineStart() {
		_cursor = lineStart()
	}

	internal fun moveToLineEnd() {
		_cursor = lineEnd()
	}

	private fun lineStart() = _text.lastIndexOf('\n', _cursor - 1) + 1

	private fun lineEnd() = _text.indexOf('\n', _cursor).let { if (it == -1) _text.length else it }
}

/**
 * An editable text field.
 *
 * The field is [focusable]. While focused, key presses and pastes edit [state], and the terminal's
 * cursor is placed at its cursor so that input methods show their composition there. Use
 * [focusRequester] to focus it when there are several focusable nodes. Text is wrapped to the width of the
 * field, at spaces where possible, and at most [maxLines] lines are shown, scrolled to keep the
 * cursor visible.
 *
 * When [onSubmit] is set, Enter calls it with the text. Otherwise, and with Shift+Enter or
 * Alt+Enter, it inserts a line break unless this is [singleLine]. Pasted line breaks become spaces
 * in a [singleLine] field.
 *
 * The usual editing keys work, along with Ctrl+A and Ctrl+E for the start and end of the line,
 * Ctrl+U and Ctrl+K to delete to them, and Ctrl+W to delete the previous word.
 */
@Composable
public fun TextField(
	state: TextFieldState,
	modifier: Modifier = Modifier,
	singleLine: Boolean = false,
	maxLines: Int = Int.MAX_VALUE,
	placeholder: String = "",
	color: Color = Color.Unspecified,
	placeholderColor: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	onSubmit: ((text: String) -> Unit)? = null,
) {
	require(maxLines >= 1) { "maxLines must be at least 1: $maxLines" }
	val maxRows = if (singleLine) 1 else maxLines

	// Read in composition so that edits trigger a new layout.
	val text = state.text
	val cursor = state.cursor
	var rows = emptyList<TextRow>()
	var firstRow = 0
	var cursorRow = 0
	var cursorColumn = 0
	val focus = rememberFocusTarget()

	Layout(
		content = {},
		modifier = modifier
			.focusTarget(focus)
			.onPointerEvent { event ->
				// A click focuses the field, and moves the cursor to where it was.
				if (event.type != PointerEventType.Press || event.button != PointerButton.Left) return@onPointerEvent false
				val row = firstRow + event.y
				if (row in rows.indices) state.cursor = rows.indexAt(row, event.x, text)
				true
			}
			.onKeyEvent { focus.isFocused && state.onKeyEvent(it, singleLine, onSubmit) }
			.onPasteEvent {
				if (!focus.isFocused) return@onPasteEvent false
				state.insert(if (singleLine) it.text.replace('\n', ' ') else it.text)
				true
			}
			.drawBehind {
				if (text.isEmpty()) {
					drawText(0, 0, placeholder, foreground = placeholderColor, textStyle = TextStyle.Dim)
				}
				for (row in firstRow until minOf(rows.size, firstRow + height)) {
					val line = rows[row]
					drawText(row - firstRow, 0, text.substring(line.start, line.end), color, textStyle = textStyle)
				}
				if (focus.isFocused) {
					drawCursor(cursorRow - firstRow, cursorColumn)
				}
			},
		debugInfo = { "TextField(\"$text\")" },
	) { _, constraints ->
		val wrapWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
		rows = text.wrap(wrapWidth.coerceAtLeast(1))
		val position = rows.positionOf(cursor, text)
		cursorRow = position.first
		cursorColumn = position.second
		// A cursor after the last column of a full row is shown at the start of an extra row.
		if (cursorColumn >= wrapWidth) {
			cursorRow++
			cursorColumn = 0
			rows = rows.toMutableList().apply { add(cursorRow, TextRow(cursor, cursor, 0)) }
		}

		val visibleRows = rows.size.coerceAtMost(maxRows)
		firstRow = (cursorRow - visibleRows + 1).coerceIn(0, rows.size - visibleRows)

		val placeholderWidth = if (text.isEmpty()) placeholder.columnWidth() else 0
		val contentWidth = maxOf(rows.maxOf { it.columns }, placeholderWidth, 1)
		val width = if (constraints.hasBoundedWidth) constraints.maxWidth else contentWidth
		layout(
			width.coerceIn(constraints.minWidth, constraints.maxWidth),
			visibleRows.coerceIn(constraints.minHeight, constraints.maxHeight),
		) {}
	}
}

private fun TextFieldState.onKeyEvent(
	event: KeyEvent,
	singleLine: Boolean,
	onSubmit: ((String) -> Unit)?,
): Boolean {
	val key = event.key
	when {
		key == "Enter" -> {
			val lineBreak = event.shift || event.alt || onSubmit == null
			when {
				lineBreak && !singleLine -> insert("\n")
				onSubmit != null -> onSubmit(text)
				else -> return false
			}
		}

		event.ctrl -> when (key) {
			"a" -> moveToLineStart()
			"e" -> moveToLineEnd()
			"u" -> deleteToLineStart()
			"k" -> deleteToLineEnd()
			"w" -> deleteWordBackward()
			else -> return false
		}

		key == "Backspace" -> deleteBackward()

		key == "Delete" -> deleteForward()

		key == "ArrowLeft" -> moveLeft()

		key == "ArrowRight" -> moveRight()

		key == "Home" -> moveToLineStart()

		key == "End" -> moveToLineEnd()

		// Keys with names, like Tab or F1, are not text. Neither are combinations with Alt.
		!key.isSingleCodePoint() || event.alt -> return false

		else -> insert(key)
	}
	return true
}

/** The index in [text] of the cursor at [row] and [column], or the end of the row past it. */
internal fun List<TextRow>.indexAt(row: Int, column: Int, text: String): Int {
	val line = this[row]
	var columns = 0
	text.subSequence(line.start, line.end).forEachCodePointWithWidth { start, _, _, width ->
		// Clicks on the right half of a wide character go after it.
		if (width > 0 && columns + (width + 1) / 2 > column) return line.start + start
		columns += width
	}
	return line.end
}

/** The row and column of the cursor at [index] of [text]. */
internal fun List<TextRow>.positionOf(index: Int, text: String): Pair<Int, Int> {
	for ((row, line) in withIndex()) {
		// A cursor at the boundary between two wrapped rows belongs to the second one.
		val isLast = row == lastIndex || this[row + 1].start != line.end
		if (index >= line.start && (index < line.end || (index == line.end && isLast))) {
			var columns = 0
			text.subSequence(line.start, index).forEachCodePointWithWidth { _, _, _, width -> columns += width }
			return row to columns
		}
	}
	return lastIndex to last().columns
}

/** The boundary of the character, including any zero width code points, before [index]. */
internal fun String.previousCharacterBoundary(index: Int): Int {
	var boundary = 0
	forEachCodePointWithWidth { start, _, _, width ->
		if (start >= index) return boundary
		if (width > 0) boundary = start
	}
	return boundary
}

/** The boundary of the character, including any zero width code points, after [index]. */
internal fun String.nextCharacterBoundary(index: Int): Int {
	forEachCodePointWithWidth { start, _, _, width ->
		if (start > index && width > 0) return start
	}
	return length
}

private fun String.characterBoundaryAtOrBefore(index: Int): Int {
	if (index == length) return index
	return if (nextCharacterBoundary(previousCharacterBoundary(index)) == index) index else previousCharacterBoundary(index)
}

private fun String.isSingleCodePoint() = length == 1 || (length == 2 && this[0].isHighSurrogate())
