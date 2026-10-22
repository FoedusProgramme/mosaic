package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.CSI
import org.unitedsoftware.mosaic.OSC
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.ST
import org.unitedsoftware.mosaic.cursorShow
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.onPreviewPasteEvent
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.BracketedPasteEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierAlt
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierShift
import org.unitedsoftware.mosaic.terminal.MouseEvent
import org.unitedsoftware.mosaic.testing.TestMosaic
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.text.TextRow
import org.unitedsoftware.mosaic.text.wrap
import org.unitedsoftware.mosaic.wrapWithAnsiSynchronizedUpdate

class TextFieldTest {
	private val enter = KeyboardEvent(0x0D)
	private val backspace = KeyboardEvent(0x7F)
	private val left = KeyboardEvent(57350)

	private fun TestMosaic<*>.type(text: String) {
		var index = 0
		while (index < text.length) {
			val codePoint = if (text[index].isHighSurrogate()) {
				(text[index].code - 0xD800 shl 10) + (text[index + 1].code - 0xDC00) + 0x10000
			} else {
				text[index].code
			}
			sendKeyEvent(KeyboardEvent(codePoint))
			index += if (codePoint >= 0x10000) 2 else 1
		}
	}

	private fun List<TextRow>.texts(text: String) = map { text.substring(it.start, it.end) }

	@Test fun wrapsAtSpaces() {
		val text = "hello big world"
		assertThat(text.wrap(9).texts(text)).containsExactly("hello big", "world")
		assertThat(text.wrap(7).texts(text)).containsExactly("hello ", "big ", "world")
	}

	@Test fun wrapsLongWordsAnywhere() {
		val text = "abcdefgh"
		assertThat(text.wrap(3).texts(text)).containsExactly("abc", "def", "gh")
	}

	@Test fun wrapsWideCharactersByColumns() {
		val text = "中文字符"
		assertThat(text.wrap(5).texts(text)).containsExactly("中文", "字符")
	}

	@Test fun keepsLineBreaks() {
		val text = "a\n\nb"
		assertThat(text.wrap(5).texts(text)).containsExactly("a", "", "b")
	}

	@Test fun cursorPosition() {
		val text = "ab中\ncd"
		val rows = text.wrap(10)
		assertThat(rows.positionOf(3, text)).isEqualTo(0 to 4)
		assertThat(rows.positionOf(4, text)).isEqualTo(1 to 0)
	}

	@Test fun characterBoundariesSkipCombiningMarks() {
		val text = "éx"
		assertThat(text.previousCharacterBoundary(3)).isEqualTo(2)
		assertThat(text.previousCharacterBoundary(2)).isEqualTo(0)
		assertThat(text.nextCharacterBoundary(0)).isEqualTo(2)
	}

	@Test fun typing() = runTest {
		val state = TextFieldState()
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(10), placeholder = "Say hi")
			}
			assertThat(awaitSnapshot()).isEqualTo("Say hi")

			type("hi 中文")
			assertThat(awaitSnapshot()).isEqualTo("hi 中文")
			assertThat(state.text).isEqualTo("hi 中文")

			sendKeyEvent(backspace)
			sendKeyEvent(left)
			type("!")
			assertThat(awaitSnapshot()).isEqualTo("hi !中")
		}
	}

	@Test fun enterSubmitsAndShiftEnterBreaksLines() = runTest {
		val state = TextFieldState()
		val submitted = mutableListOf<String>()
		runMosaicTest {
			setContent {
				TextField(
					state,
					modifier = Modifier.width(10),
					onSubmit = {
						submitted += it
						state.clear()
					},
				)
			}
			awaitSnapshot()
			type("one")
			sendKeyEvent(KeyboardEvent(0x0D, modifiers = ModifierShift))
			type("two")
			assertThat(awaitSnapshot()).isEqualTo("one\ntwo")

			sendKeyEvent(enter)
			assertThat(awaitSnapshot()).isEqualTo("")
			assertThat(submitted).containsExactly("one\ntwo")
		}
	}

	@Test fun readlineKeys() = runTest {
		val state = TextFieldState("hello brave world")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(20))
			}
			awaitSnapshot()
			sendKeyEvent(KeyboardEvent('w'.code, modifiers = ModifierCtrl))
			assertThat(awaitSnapshot()).isEqualTo("hello brave")
			sendKeyEvent(KeyboardEvent('a'.code, modifiers = ModifierCtrl))
			sendKeyEvent(KeyboardEvent('k'.code, modifiers = ModifierCtrl))
			assertThat(awaitSnapshot()).isEqualTo("")
		}
	}

	@Test fun pasteKeepsLineBreaks() = runTest {
		val state = TextFieldState()
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(10), onSubmit = {})
			}
			awaitSnapshot()
			sendEvent(BracketedPasteEvent(start = true))
			type("a")
			sendKeyEvent(enter)
			type("b")
			sendEvent(BracketedPasteEvent(start = false))
			assertThat(awaitSnapshot()).isEqualTo("a\nb")
		}
	}

	@Test fun singleLinePasteReplacesLineBreaks() = runTest {
		val state = TextFieldState()
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(10), singleLine = true)
			}
			awaitSnapshot()
			sendEvent(BracketedPasteEvent(start = true))
			type("a")
			sendKeyEvent(enter)
			type("b")
			sendEvent(BracketedPasteEvent(start = false))
			assertThat(awaitSnapshot()).isEqualTo("a b")
		}
	}

	@Test fun maxLinesScrollsToTheCursor() = runTest {
		val state = TextFieldState("1\n2\n3\n4")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(5), maxLines = 2)
			}
			assertThat(awaitSnapshot()).isEqualTo("3\n4")
		}
	}

	@Test fun onlyTheFocusedFieldEdits() = runTest {
		val first = TextFieldState("x")
		val second = TextFieldState("y")
		runMosaicTest {
			setContent {
				Column {
					TextField(first, modifier = Modifier.width(5))
					TextField(second, modifier = Modifier.width(5))
				}
			}
			awaitSnapshot()
			// The first field is focused, since nothing else was.
			type("1")
			assertThat(awaitSnapshot()).isEqualTo("x1\ny")

			sendKeyEvent(KeyboardEvent(0x09))
			type("2")
			assertThat(awaitSnapshot()).isEqualTo("x1\ny2")
		}
	}

	@Test fun unhandledKeysReachParent() = runTest {
		val state = TextFieldState("x")
		runMosaicTest {
			setContent {
				var keys by remember { mutableStateOf("") }
				Column(
					modifier = Modifier.onKeyEvent {
						keys += it.key
						true
					},
				) {
					TextField(state, modifier = Modifier.width(5))
					Text("keys:$keys")
				}
			}
			awaitSnapshot()
			sendKeyEvent(KeyboardEvent(57364))
			// The field does not use F1, so it went to its parent.
			assertThat(awaitSnapshot()).isEqualTo("x\nkeys:F1")
		}
	}

	@Test fun cursorIsPlacedAfterWideCharacters() = runTest {
		val state = TextFieldState("中文")
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				TextField(state, modifier = Modifier.width(6))
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"中文\r${CSI}5G$cursorShow".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	private fun key(codePoint: Int, modifiers: Int = 0) = KeyboardEvent(codePoint, modifiers = modifiers)

	private val up = key(57352)
	private val down = key(57353)

	@Test fun arrowsMoveBetweenRowsAndLeaveTheEdgesToOthers() = runTest {
		val state = TextFieldState("hello\nhi\nworld")
		runMosaicTest {
			setContent {
				var keys by remember { mutableStateOf("") }
				Column(
					modifier = Modifier.onKeyEvent {
						keys += it.key
						true
					},
				) {
					TextField(state, modifier = Modifier.width(10))
					Text("keys:$keys")
				}
			}
			awaitSnapshot()
			// The column is kept through the shorter row.
			sendKeyEvent(up)
			sendKeyEvent(up)
			type("!")
			assertThat(awaitSnapshot()).isEqualTo("hello!\nhi\nworld\nkeys:")

			sendKeyEvent(up)
			assertThat(awaitSnapshot()).isEqualTo("hello!\nhi\nworld\nkeys:ArrowUp")

			sendKeyEvent(down)
			sendKeyEvent(down)
			sendKeyEvent(down)
			assertThat(awaitSnapshot()).isEqualTo("hello!\nhi\nworld\nkeys:ArrowUpArrowDown")
			assertThat(state.cursor).isEqualTo(state.text.length)
		}
	}

	@Test fun arrowsMoveBetweenWrappedRows() = runTest {
		val state = TextFieldState("ab cdefgh")
		state.cursor = 6
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(4))
			}
			assertThat(awaitSnapshot()).isEqualTo("ab\ncdef\ngh")
			sendKeyEvent(up)
			type("!")
			// The cursor stays on the first row, before its last character, rather than at its end,
			// which is the start of the second row.
			awaitSnapshot()
			assertThat(state.text).isEqualTo("ab! cdefgh")
		}
	}

	@Test fun wordMovementAndDeletion() = runTest {
		val state = TextFieldState("one two-three four")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(30))
			}
			awaitSnapshot()
			sendKeyEvent(key('b'.code, ModifierAlt))
			sendKeyEvent(key(57350, ModifierCtrl))
			type("X")
			assertThat(awaitSnapshot()).isEqualTo("one two-Xthree four")

			sendKeyEvent(key(0x7F, ModifierAlt))
			assertThat(awaitSnapshot()).isEqualTo("one two-three four")
			sendKeyEvent(key(57356))
			sendKeyEvent(key('f'.code, ModifierAlt))
			sendKeyEvent(key('d'.code, ModifierAlt))
			assertThat(awaitSnapshot()).isEqualTo("one-three four")
		}
	}

	@Test fun shiftSelectsAndTypingReplacesTheSelection() = runTest {
		val state = TextFieldState("hello world")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(20))
			}
			awaitSnapshot()
			sendKeyEvent(key(57350, ModifierShift or ModifierAlt))
			assertThat(awaitSnapshot()).isEqualTo("hello world")
			assertThat(state.selectedText).isEqualTo("world")

			type("there")
			assertThat(awaitSnapshot()).isEqualTo("hello there")
			assertThat(state.hasSelection).isFalse()

			sendKeyEvent(key(57356, ModifierShift))
			sendKeyEvent(backspace)
			assertThat(awaitSnapshot()).isEqualTo("")
		}
	}

	@Test fun selectedTextIsInverted() = runTest {
		val state = TextFieldState("abc")
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				TextField(state, modifier = Modifier.width(6))
			}
			awaitSnapshot()
			sendKeyEvent(key(57350, ModifierShift))
			assertThat(awaitSnapshot()).contains("ab${CSI}7mc")
		}
	}

	@Test fun undoAndRedoGroupTypingByWord() = runTest {
		val state = TextFieldState()
		val undo = key('_'.code, ModifierCtrl)
		val redo = key('z'.code, ModifierCtrl or ModifierShift)
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(20))
			}
			awaitSnapshot()
			type("hello big world")
			sendKeyEvent(backspace)
			sendKeyEvent(backspace)
			assertThat(awaitSnapshot()).isEqualTo("hello big wor")

			sendKeyEvent(undo)
			assertThat(awaitSnapshot()).isEqualTo("hello big world")
			sendKeyEvent(undo)
			awaitSnapshot()
			assertThat(state.text).isEqualTo("hello big ")
			sendKeyEvent(undo)
			sendKeyEvent(undo)
			assertThat(awaitSnapshot()).isEqualTo("")
			assertThat(state.canUndo).isFalse()

			sendKeyEvent(redo)
			awaitSnapshot()
			assertThat(state.text).isEqualTo("hello ")
			assertThat(state.redo()).isTrue()
			assertThat(state.text).isEqualTo("hello big ")

			// Editing drops what could be redone.
			type("!")
			awaitSnapshot()
			assertThat(state.canRedo).isFalse()
		}
	}

	@Test fun killsAreCollectedForYanking() = runTest {
		val state = TextFieldState("one two three")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(20))
			}
			awaitSnapshot()
			sendKeyEvent(key('w'.code, ModifierCtrl))
			sendKeyEvent(key('w'.code, ModifierCtrl))
			assertThat(awaitSnapshot()).isEqualTo("one")
			sendKeyEvent(key('a'.code, ModifierCtrl))
			sendKeyEvent(key('y'.code, ModifierCtrl))
			assertThat(awaitSnapshot()).isEqualTo("two threeone")
		}
	}

	@Test fun minLines() = runTest {
		val state = TextFieldState("a")
		runMosaicTest {
			setContent {
				Column {
					TextField(state, modifier = Modifier.width(5), minLines = 3, maxLines = 4)
					Text("below")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("a\n\n\nbelow")
			state.text = "1\n2\n3\n4\n5"
			assertThat(awaitSnapshot()).isEqualTo("2\n3\n4\n5\nbelow")
		}
	}

	@Test fun mouseDragSelectsAndCopies() = runTest {
		val state = TextFieldState("hello world")
		runMosaicTest {
			setContent {
				TextField(state, modifier = Modifier.width(20))
			}
			awaitSnapshot()
			sendEvent(MouseEvent(1, 0, MouseEvent.Type.Press, MouseEvent.Button.Left))
			sendEvent(MouseEvent(4, 0, MouseEvent.Type.Drag, MouseEvent.Button.Left))
			sendEvent(MouseEvent(4, 0, MouseEvent.Type.Release, MouseEvent.Button.Left))
			awaitSnapshot()
			assertThat(state.selectedText).isEqualTo("ell")
			assertThat(terminalCommands()).isEqualTo("${OSC}52;c;ZWxs$ST")
		}
	}

	@Test fun previewPasteEventsComeBeforeTheField() = runTest {
		val state = TextFieldState()
		runMosaicTest {
			setContent {
				Column(
					modifier = Modifier.onPreviewPasteEvent {
						if (it.text.length < 5) return@onPreviewPasteEvent false
						state.insert("[${it.text.length} chars]")
						true
					},
				) {
					TextField(state, modifier = Modifier.width(20))
				}
			}
			awaitSnapshot()
			sendEvent(BracketedPasteEvent(start = true))
			type("abc")
			sendEvent(BracketedPasteEvent(start = false))
			assertThat(awaitSnapshot()).isEqualTo("abc")
			sendEvent(BracketedPasteEvent(start = true))
			type("abcdef")
			sendEvent(BracketedPasteEvent(start = false))
			assertThat(awaitSnapshot()).isEqualTo("abc[6 chars]")
		}
	}
}
