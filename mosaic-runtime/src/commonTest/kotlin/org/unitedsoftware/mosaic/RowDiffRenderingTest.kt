package org.unitedsoftware.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Text

class RowDiffRenderingTest {
	@Test fun onlyChangedRowsAreWritten() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var middle by mutableStateOf("two")
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("one")
					Text(middle)
					Text("three")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo("one\r\ntwo\r\nthree".wrapWithAnsiSynchronizedUpdate())

			middle = "TWO"
			assertThat(awaitSnapshot()).isEqualTo(
				"${CSI}1F${clearLine}TWO\r\n".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun tallOutputKeepsItsBottomVisible() = runTest {
		val size = Terminal.Size(columns = 10, rows = 3)
		val rendering = AnsiRendering(TestTerminal.Capabilities(), terminalSize = { size })
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("1")
					Text("2")
					Text("3")
					Text("4")
				}
			}
			// All three rows fit, with the cursor resting at the end of the last one.
			assertThat(awaitSnapshot()).isEqualTo("2\r\n3\r\n4".wrapWithAnsiSynchronizedUpdate())
		}
	}

	@Test fun alternateScreen() = runTest {
		val size = Terminal.Size(columns = 10, rows = 3)
		val rendering = AlternateScreenRendering(TestTerminal.Capabilities(), terminalSize = { size })
		var text by mutableStateOf("a")
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("top")
					Text(text)
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"$alternateScreenEnable${CSI}2J${CSI}1;1H${clearLine}top${CSI}2;1H${clearLine}a"
					.wrapWithAnsiSynchronizedUpdate(),
			)

			text = "b"
			assertThat(awaitSnapshot()).isEqualTo("${CSI}2;1H${clearLine}b".wrapWithAnsiSynchronizedUpdate())
		}
		assertThat(rendering.finish().toString()).isEqualTo(alternateScreenDisable)
	}
}
