package org.unitedsoftware.mosaic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.AnsiRendering
import org.unitedsoftware.mosaic.CSI
import org.unitedsoftware.mosaic.RenderingSnapshots
import org.unitedsoftware.mosaic.cursorShow
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.width
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.BracketedPasteEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierShift
import org.unitedsoftware.mosaic.testing.TestMosaic
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
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

	private fun List<TextFieldRow>.texts(text: String) = map { text.substring(it.start, it.end) }

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
				"中文\r\n${CSI}1F${CSI}5G$cursorShow".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}
}
