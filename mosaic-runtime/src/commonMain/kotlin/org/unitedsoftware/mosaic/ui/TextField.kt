package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.unitedsoftware.mosaic.Clipboard
import org.unitedsoftware.mosaic.LocalClipboard
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
 * The editable text of a [TextField], the position of its cursor, and its selection.
 *
 * The cursor is an index into [text] between two characters, where 0 is before the first one and
 * the length of [text] is after the last one. The selection reaches from where it started, its
 * anchor, to the cursor. Without a selection, the anchor is at the cursor.
 *
 * Edits are recorded so that [undo] and [redo] can step through them, with typed characters
 * grouped by word.
 */
@Stable
public class TextFieldState(initialText: String = "") {
	private var _text by mutableStateOf(initialText)
	private var _cursor by mutableIntStateOf(initialText.length)
	private var selectionAnchor by mutableIntStateOf(initialText.length)

	/** Setting the text moves the cursor to its end, and is recorded like other edits. */
	public var text: String
		get() = _text
		set(value) {
			if (value == _text) return
			record(Edit.Other)
			_text = value
			collapse(value.length)
		}

	/**
	 * Setting the cursor moves it to the closest character boundary within the text, and removes
	 * the selection.
	 */
	public var cursor: Int
		get() = _cursor
		set(value) {
			collapse(_text.characterBoundaryAtOrBefore(value.coerceIn(0, _text.length)))
			lastEdit = null
		}

	/** The start of the selection, at most [selectionEnd]. Without a selection, the cursor. */
	public val selectionStart: Int get() = minOf(selectionAnchor, _cursor)

	/** The end of the selection. Without a selection, the cursor. */
	public val selectionEnd: Int get() = maxOf(selectionAnchor, _cursor)

	public val hasSelection: Boolean get() = selectionAnchor != _cursor

	public val selectedText: String get() = _text.substring(selectionStart, selectionEnd)

	/** Selects from [anchor] to [cursor], where the cursor is placed. */
	public fun select(anchor: Int, cursor: Int) {
		selectionAnchor = _text.characterBoundaryAtOrBefore(anchor.coerceIn(0, _text.length))
		_cursor = _text.characterBoundaryAtOrBefore(cursor.coerceIn(0, _text.length))
		lastEdit = null
	}

	public fun selectAll() {
		select(0, _text.length)
	}

	/** Replaces the selection by [text], or inserts it at the cursor, and moves the cursor after it. */
	public fun insert(text: String) {
		replaceSelection(text, Edit.Other)
	}

	public fun clear() {
		text = ""
	}

	private var historyVersion by mutableIntStateOf(0)
	private val undoStack = ArrayList<Entry>()
	private val redoStack = ArrayList<Entry>()
	private var lastEdit: Edit? = null

	public val canUndo: Boolean get() = historyVersion.let { undoStack.isNotEmpty() }
	public val canRedo: Boolean get() = historyVersion.let { redoStack.isNotEmpty() }

	/** Reverts the last edit, and returns false when there is none. */
	public fun undo(): Boolean = step(from = undoStack, to = redoStack)

	/** Repeats the last edit which was undone, and returns false when there is none. */
	public fun redo(): Boolean = step(from = redoStack, to = undoStack)

	private fun step(from: ArrayList<Entry>, to: ArrayList<Entry>): Boolean {
		val entry = from.removeLastOrNull() ?: return false
		to += Entry(_text, _cursor, selectionAnchor)
		_text = entry.text
		_cursor = entry.cursor
		selectionAnchor = entry.anchor
		lastEdit = null
		historyVersion++
		return true
	}

	/** Remembers the text before an edit, unless it continues the last one, like typing a word. */
	private fun record(edit: Edit, startsGroup: Boolean = false) {
		if (startsGroup || edit == Edit.Other || edit != lastEdit) {
			undoStack += Entry(_text, _cursor, selectionAnchor)
			if (undoStack.size > MaxUndoEntries) undoStack.removeAt(0)
			redoStack.clear()
			historyVersion++
		}
		lastEdit = edit
	}

	private fun collapse(index: Int) {
		_cursor = index
		selectionAnchor = index
	}

	private fun replaceSelection(text: String, edit: Edit) {
		val start = selectionStart
		val end = selectionEnd
		if (start == end && text.isEmpty()) return
		// Typing after a space starts the next word, which is undone separately.
		val newWord = start > 0 && _text[start - 1].isWhitespace() && text.isNotEmpty() && !text[0].isWhitespace()
		record(edit, startsGroup = start != end || newWord)
		killing = false
		_text = _text.substring(0, start) + text + _text.substring(end)
		collapse(start + text.length)
	}

	internal fun type(text: String) {
		replaceSelection(text, Edit.Typing)
	}

	/** Deletes the selection, or else the text from the cursor to [to]. */
	private fun delete(to: Int, edit: Edit = Edit.Deleting): String {
		val start = if (hasSelection) selectionStart else minOf(_cursor, to)
		val end = if (hasSelection) selectionEnd else maxOf(_cursor, to)
		if (start == end) return ""
		record(if (hasSelection) Edit.Other else edit)
		val deleted = _text.substring(start, end)
		_text = _text.removeRange(start, end)
		collapse(start)
		return deleted
	}

	internal fun deleteBackward() {
		delete(_text.previousCharacterBoundary(_cursor))
	}

	internal fun deleteForward() {
		delete(_text.nextCharacterBoundary(_cursor))
	}

	/** Text deleted by the kill commands, like Ctrl+W, which Ctrl+Y inserts again. */
	private var killed = ""

	/** Consecutive kills collect their text together, like in readline. */
	internal var killing = false

	private fun kill(to: Int) {
		val backward = to < _cursor
		val continuing = killing && !hasSelection
		val deleted = delete(to, Edit.Other)
		if (deleted.isEmpty()) return
		killed = when {
			!continuing -> deleted
			backward -> deleted + killed
			else -> killed + deleted
		}
		killing = true
	}

	internal fun yank() {
		if (killed.isNotEmpty()) replaceSelection(killed, Edit.Other)
	}

	internal fun killWordBackward() {
		var start = _cursor
		while (start > 0 && _text[start - 1].isWhitespace()) start--
		while (start > 0 && !_text[start - 1].isWhitespace()) start--
		kill(start)
	}

	internal fun killPreviousWord() {
		kill(_text.previousWordStart(_cursor))
	}

	internal fun killNextWord() {
		kill(_text.nextWordEnd(_cursor))
	}

	internal fun killToLineStart() {
		kill(lineStart())
	}

	internal fun killToLineEnd() {
		kill(lineEnd())
	}

	/** Moves the cursor to [index], and selects from the anchor when [extend] is true. */
	internal fun moveTo(index: Int, extend: Boolean) {
		_cursor = index
		if (!extend) selectionAnchor = index
		lastEdit = null
		killing = false
	}

	internal fun moveLeft(extend: Boolean) {
		if (hasSelection && !extend) {
			moveTo(selectionStart, extend = false)
		} else {
			moveTo(_text.previousCharacterBoundary(_cursor), extend)
		}
	}

	internal fun moveRight(extend: Boolean) {
		if (hasSelection && !extend) {
			moveTo(selectionEnd, extend = false)
		} else {
			moveTo(_text.nextCharacterBoundary(_cursor), extend)
		}
	}

	internal fun moveWordLeft(extend: Boolean) {
		moveTo(_text.previousWordStart(_cursor), extend)
	}

	internal fun moveWordRight(extend: Boolean) {
		moveTo(_text.nextWordEnd(_cursor), extend)
	}

	internal fun moveToLineStart(extend: Boolean) {
		moveTo(lineStart(), extend)
	}

	internal fun moveToLineEnd(extend: Boolean) {
		moveTo(lineEnd(), extend)
	}

	private fun lineStart() = _text.lastIndexOf('\n', _cursor - 1) + 1

	private fun lineEnd() = _text.indexOf('\n', _cursor).let { if (it == -1) _text.length else it }

	private class Entry(val text: String, val cursor: Int, val anchor: Int)

	private enum class Edit { Typing, Deleting, Other }

	private companion object {
		const val MaxUndoEntries = 200
	}
}

/** What the measure policy of a [TextField] found, for the key and pointer handlers. */
private class TextFieldLayout {
	var wrapWidth = Int.MAX_VALUE
	var rows = emptyList<TextRow>()
	var firstRow = 0

	/** The column which vertical movement stays in, even through shorter rows, or -1. */
	var goalColumn = -1

	/** Whether the left button was pressed in the field, and is selecting while dragged. */
	var selecting = false

	/** The index of the text at [x] and [y] in the field, coerced to the closest one. */
	fun indexAt(x: Int, y: Int, text: String): Int {
		if (rows.isEmpty()) return 0
		val row = (firstRow + y).coerceIn(rows.indices)
		return rows.cursorIndexAt(row, x.coerceAtLeast(0), text)
	}
}

/**
 * An editable text field.
 *
 * The field is [focusable]. While focused, key presses and pastes edit [state], and the terminal's
 * cursor is placed at its cursor so that input methods show their composition there. Use
 * [focusRequester] to focus it when there are several focusable nodes. Text is wrapped to the width
 * of the field, at spaces where possible. The field is at least [minLines] and at most [maxLines]
 * lines high, and scrolls to keep the cursor visible.
 *
 * When [onSubmit] is set, Enter calls it with the text. Otherwise, and with Shift+Enter or
 * Alt+Enter, it inserts a line break unless this is [singleLine]. Pasted line breaks become spaces
 * in a [singleLine] field.
 *
 * The usual editing keys work, along with these:
 * - Alt or Ctrl with ← and →, or Alt+B and Alt+F, move by words. Alt+Backspace and Alt+D delete
 *   them.
 * - ↑ and ↓ move between rows. On the first and last row, they are left for other handlers, for
 *   example to recall earlier input.
 * - Shift with the keys which move the cursor selects text, as does dragging the mouse, which
 *   copies the selection to the clipboard once released. Typing replaces the selection.
 * - Ctrl+A and Ctrl+E move to the start and end of the line. Ctrl+U and Ctrl+K delete to them, and
 *   Ctrl+W deletes the previous word, so that Ctrl+Y inserts it again, like in a shell.
 * - Ctrl+_ (or Ctrl+/) undoes the last edit, and Ctrl+Shift+Z redoes it where the terminal tells
 *   them apart from Ctrl+Z.
 */
@Composable
public fun TextField(
	state: TextFieldState,
	modifier: Modifier = Modifier,
	singleLine: Boolean = false,
	maxLines: Int = Int.MAX_VALUE,
	minLines: Int = 1,
	placeholder: String = "",
	color: Color = Color.Unspecified,
	placeholderColor: Color = Color.Unspecified,
	textStyle: TextStyle = TextStyle.Unspecified,
	onSubmit: ((text: String) -> Unit)? = null,
) {
	require(maxLines >= 1) { "maxLines must be at least 1: $maxLines" }
	require(minLines in 1..maxLines) { "minLines must be from 1 to maxLines ($maxLines): $minLines" }
	val maxRows = if (singleLine) 1 else maxLines
	val minRows = minOf(minLines, maxRows)

	// Read in composition so that edits trigger a new layout.
	val text = state.text
	val cursor = state.cursor
	val selectionStart = state.selectionStart
	val selectionEnd = state.selectionEnd
	var cursorRow = 0
	var cursorColumn = 0
	val focus = rememberFocusTarget()
	val layout = remember { TextFieldLayout() }
	val clipboard = LocalClipboard.current

	Layout(
		content = {},
		modifier = modifier
			.focusTarget(focus)
			.onPointerEvent { event ->
				// A click focuses the field and moves the cursor to where it was, and a drag selects.
				when (event.type) {
					PointerEventType.Press -> {
						if (event.button != PointerButton.Left) return@onPointerEvent false
						state.moveTo(layout.indexAt(event.x, event.y, state.text), extend = event.shift)
						layout.goalColumn = -1
						layout.selecting = true
					}

					PointerEventType.Drag -> {
						if (!layout.selecting) return@onPointerEvent false
						state.moveTo(layout.indexAt(event.x, event.y, state.text), extend = true)
					}

					PointerEventType.Release -> {
						if (!layout.selecting) return@onPointerEvent false
						layout.selecting = false
						if (state.hasSelection) clipboard.setText(state.selectedText)
					}

					else -> return@onPointerEvent false
				}
				true
			}
			.onKeyEvent { focus.isFocused && state.onKeyEvent(it, layout, singleLine, onSubmit, clipboard) }
			.onPasteEvent {
				if (!focus.isFocused) return@onPasteEvent false
				state.insert(if (singleLine) it.text.replace('\n', ' ') else it.text)
				true
			}
			.drawBehind {
				if (text.isEmpty()) {
					drawText(0, 0, placeholder, foreground = placeholderColor, textStyle = TextStyle.Dim)
				}
				val rows = layout.rows
				val firstRow = layout.firstRow
				// The selection is only shown while focused, like the cursor.
				val selecting = focus.isFocused && selectionStart != selectionEnd
				for (row in firstRow until minOf(rows.size, firstRow + height)) {
					val line = rows[row]
					val y = row - firstRow
					if (!selecting || selectionEnd < line.start || selectionStart > line.end) {
						drawText(y, 0, text.substring(line.start, line.end), color, textStyle = textStyle)
						continue
					}
					val start = selectionStart.coerceIn(line.start, line.end)
					val end = selectionEnd.coerceIn(line.start, line.end)
					val before = text.substring(line.start, start)
					val selected = text.substring(start, end)
					drawText(y, 0, before, color, textStyle = textStyle)
					drawText(y, before.columnWidth(), selected, color, textStyle = textStyle + TextStyle.Invert)
					val x = before.columnWidth() + selected.columnWidth()
					drawText(y, x, text.substring(end, line.end), color, textStyle = textStyle)
					// A selected line break shows as a selected space at the end of its row.
					if (selectionEnd > line.end && line.end < text.length && text[line.end] == '\n') {
						drawText(y, line.columns, " ", color, textStyle = textStyle + TextStyle.Invert)
					}
				}
				if (focus.isFocused) {
					drawCursor(cursorRow - firstRow, cursorColumn)
				}
			},
		debugInfo = { "TextField(\"$text\")" },
	) { _, constraints ->
		val wrapWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
		val fieldRows = text.fieldRows(cursor, wrapWidth)
		val rows = fieldRows.rows
		cursorRow = fieldRows.cursorRow
		cursorColumn = fieldRows.cursorColumn

		val visibleRows = rows.size.coerceIn(minRows, maxRows)
		val firstRow = (cursorRow - visibleRows + 1).coerceIn(0, maxOf(0, rows.size - visibleRows))
		layout.wrapWidth = wrapWidth
		layout.rows = rows
		layout.firstRow = firstRow

		val placeholderWidth = if (text.isEmpty()) placeholder.columnWidth() else 0
		val contentWidth = maxOf(rows.maxOf { it.columns }, placeholderWidth, 1)
		val width = if (constraints.hasBoundedWidth) constraints.maxWidth else contentWidth
		layout(
			width.coerceIn(constraints.minWidth, constraints.maxWidth),
			visibleRows.coerceIn(constraints.minHeight, constraints.maxHeight),
		) {}
	}
}

/** The rows of the text of a field, and where its cursor is on them. */
private class FieldRows(val rows: List<TextRow>, val cursorRow: Int, val cursorColumn: Int)

private fun String.fieldRows(cursor: Int, wrapWidth: Int): FieldRows {
	var rows = wrap(wrapWidth.coerceAtLeast(1))
	var (cursorRow, cursorColumn) = rows.positionOf(cursor, this)
	// A cursor after the last column of a full row is shown at the start of an extra row.
	if (cursorColumn >= wrapWidth) {
		cursorRow++
		cursorColumn = 0
		rows = rows.toMutableList().apply { add(cursorRow, TextRow(cursor, cursor, 0)) }
	}
	return FieldRows(rows, cursorRow, cursorColumn)
}

private fun TextFieldState.onKeyEvent(
	event: KeyEvent,
	layout: TextFieldLayout,
	singleLine: Boolean,
	onSubmit: ((String) -> Unit)?,
	clipboard: Clipboard,
): Boolean {
	val key = event.key
	val shift = event.shift
	val vertical = key == "ArrowUp" || key == "ArrowDown"
	if (!vertical) layout.goalColumn = -1
	val wasKilling = killing
	killing = false
	when {
		key == "Enter" -> {
			val lineBreak = event.shift || event.alt || onSubmit == null
			when {
				lineBreak && !singleLine -> type("\n")
				onSubmit != null -> onSubmit(text)
				else -> return false
			}
		}

		vertical -> return moveVertically(if (key == "ArrowUp") -1 else 1, layout, shift)

		key == "ArrowLeft" -> if (event.alt || event.ctrl) moveWordLeft(shift) else moveLeft(shift)

		key == "ArrowRight" -> if (event.alt || event.ctrl) moveWordRight(shift) else moveRight(shift)

		key == "Home" -> if (event.ctrl) moveTo(0, shift) else moveToLineStart(shift)

		key == "End" -> if (event.ctrl) moveTo(text.length, shift) else moveToLineEnd(shift)

		key == "Backspace" -> if (event.alt || event.ctrl) {
			killing = wasKilling
			killPreviousWord()
		} else {
			deleteBackward()
		}

		key == "Delete" -> if (event.alt || event.ctrl) {
			killing = wasKilling
			killNextWord()
		} else {
			deleteForward()
		}

		key == "Escape" -> if (hasSelection) moveTo(cursor, extend = false) else return false

		event.ctrl && event.shift && (key == "z" || key == "Z") -> redo()

		event.ctrl -> {
			// Only the kill commands continue a series of kills.
			if (key == "u" || key == "k" || key == "w") killing = wasKilling
			when (key) {
				"a" -> moveToLineStart(extend = false)
				"e" -> moveToLineEnd(extend = false)
				"u" -> killToLineStart()
				"k" -> killToLineEnd()
				"w" -> killWordBackward()
				"y" -> yank()
				"_", "/", "-" -> undo()
				else -> return false
			}
		}

		event.alt -> when (key) {
			"b" -> moveWordLeft(extend = false)

			"f" -> moveWordRight(extend = false)

			"d" -> {
				killing = wasKilling
				killNextWord()
			}

			else -> return false
		}

		// Keys with names, like Tab or F1, are not text.
		!key.isSingleCodePoint() -> return false

		else -> type(key)
	}
	return true
}

/** Moves the cursor [delta] rows up or down, staying in the same column as far as possible. */
private fun TextFieldState.moveVertically(delta: Int, layout: TextFieldLayout, extend: Boolean): Boolean {
	// The rows are laid out anew, as several keys may arrive before the next layout.
	val fieldRows = text.fieldRows(cursor, layout.wrapWidth)
	val row = fieldRows.cursorRow + delta
	if (row !in fieldRows.rows.indices) {
		// Leave it to other handlers, unless selecting, which goes to the start or end instead.
		if (!extend) return false
		moveTo(if (delta < 0) 0 else text.length, extend = true)
		return true
	}
	val column = if (layout.goalColumn >= 0) layout.goalColumn else fieldRows.cursorColumn
	moveTo(fieldRows.rows.cursorIndexAt(row, column, text), extend)
	layout.goalColumn = column
	return true
}

/**
 * The index for the cursor at [row] and [column], like [indexAt], but kept on [row] rather than
 * at the end of a row which continues on the next one, which shows on the next one.
 */
internal fun List<TextRow>.cursorIndexAt(row: Int, column: Int, text: String): Int {
	val index = indexAt(row, column, text)
	val line = this[row]
	if (index == line.end && row < lastIndex && this[row + 1].start == line.end && line.end > line.start) {
		return text.previousCharacterBoundary(index)
	}
	return index
}

/** Letters, digits, and the marks combined with them make up words. */
private fun Char.isWordPart() = isLetterOrDigit() || this == '_' || category.let {
	it == CharCategory.NON_SPACING_MARK || it == CharCategory.COMBINING_SPACING_MARK || it == CharCategory.ENCLOSING_MARK
}

/** The start of the word before [index], skipping what is between words. */
internal fun String.previousWordStart(index: Int): Int {
	var start = index
	while (start > 0 && !this[start - 1].isWordPart()) start--
	while (start > 0 && this[start - 1].isWordPart()) start--
	return start
}

/** The end of the word after [index], skipping what is between words. */
internal fun String.nextWordEnd(index: Int): Int {
	var end = index
	while (end < length && !this[end].isWordPart()) end++
	while (end < length && this[end].isWordPart()) end++
	return end
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
