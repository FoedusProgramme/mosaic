package org.unitedsoftware.mosaic

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.onClick
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.MouseEvent
import org.unitedsoftware.mosaic.terminal.MouseEvent.Button
import org.unitedsoftware.mosaic.terminal.MouseEvent.Type
import org.unitedsoftware.mosaic.testing.TestMosaic
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Text

class ScreenSelectionTest {
	private fun TestMosaic<*>.click(x: Int, y: Int) {
		sendEvent(MouseEvent(x, y, Type.Press, Button.Left))
		sendEvent(MouseEvent(x, y, Type.Release, Button.Left))
	}

	private fun TestMosaic<*>.drag(fromX: Int, fromY: Int, toX: Int, toY: Int) {
		sendEvent(MouseEvent(fromX, fromY, Type.Press, Button.Left))
		sendEvent(MouseEvent(toX, toY, Type.Drag, Button.Left))
		sendEvent(MouseEvent(toX, toY, Type.Release, Button.Left))
	}

	private fun copied(base64: String) = "${OSC}52;c;$base64$ST"

	@Test fun dragSelectsAcrossRowsAndCopies() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("hello world")
					Text("second line")
				}
			}
			awaitSnapshot()
			// Backwards too, from the end to the start.
			drag(4, 1, 6, 0)
			awaitSnapshot()
			assertThat(terminalCommands()).isEqualTo(copied("d29ybGQKc2Vjb24="))
		}
	}

	/** The next snapshot which [predicate] accepts, of the few frames that follow. */
	private suspend fun TestMosaic<String>.awaitSnapshot(predicate: (String) -> Boolean): String {
		repeat(4) {
			val snapshot = awaitSnapshot()
			if (predicate(snapshot)) return snapshot
		}
		return awaitSnapshot()
	}

	@Test fun aCopyOfASelectionCanBeRead() = runTest {
		runMosaicTest {
			setContent {
				Column {
					Text("hello world")
					Text(LocalClipboard.current.lastCopy?.text ?: "nothing copied")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("hello world\nnothing copied")
			drag(6, 0, 10, 0)
			assertThat(awaitSnapshot { "nothing" !in it }).isEqualTo("hello world\nworld")
		}
	}

	@Test fun eachCopyIsAValueOfItsOwn() = runTest {
		runMosaicTest {
			setContent {
				val copy = LocalClipboard.current.lastCopy
				var copies by remember { mutableIntStateOf(0) }
				LaunchedEffect(copy) { if (copy != null) copies++ }
				Text("hello world, copied $copies times")
			}
			awaitSnapshot()
			drag(6, 0, 10, 0)
			assertThat(awaitSnapshot { "1 times" in it }).isEqualTo("hello world, copied 1 times")
			drag(6, 0, 10, 0)
			assertThat(awaitSnapshot { "2 times" in it }).isEqualTo("hello world, copied 2 times")
		}
	}

	@Test fun selectionIsInvertedUntilTheNextKey() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("hello world")
			}
			awaitSnapshot()
			drag(6, 0, 10, 0)
			assertThat(awaitSnapshot()).contains("hello ${CSI}7mworld")
			sendKeyEvent(KeyboardEvent('x'.code))
			assertThat(awaitSnapshot()).doesNotContain("${CSI}7m")
		}
	}

	@Test fun doubleClickSelectsWordsAndTripleClickRows() = runTest {
		runMosaicTest {
			setContent {
				Text("hello world")
			}
			awaitSnapshot()
			click(8, 0)
			click(8, 0)
			awaitSnapshot()
			assertThat(terminalCommands()).isEqualTo(copied("d29ybGQ="))
			click(8, 0)
			awaitSnapshot()
			assertThat(terminalCommands()).isEqualTo(copied("aGVsbG8gd29ybGQ="))
		}
	}

	@Test fun wideCharactersAreSelectedWhole() = runTest {
		runMosaicTest {
			setContent {
				Text("ab中文cd")
			}
			awaitSnapshot()
			// From the second half of 中 to the first half of 文.
			drag(3, 0, 4, 0)
			awaitSnapshot()
			assertThat(terminalCommands()).isEqualTo(copied("5Lit5paH"))
		}
	}

	@Test fun pressesTakenByHandlersDoNotSelect() = runTest {
		runMosaicTest {
			setContent {
				Text("[button]", modifier = Modifier.onClick {})
			}
			awaitSnapshot()
			drag(1, 0, 6, 0)
			click(2, 0)
			assertThat(terminalCommands()).isNull()
		}
	}
}
