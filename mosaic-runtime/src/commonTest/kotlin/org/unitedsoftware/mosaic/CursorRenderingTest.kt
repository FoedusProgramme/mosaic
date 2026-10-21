package org.unitedsoftware.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.drawBehind
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Text

class CursorRenderingTest {
	@Test fun placedCursorIsMovedToAndShown() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("ab")
					Text("cd", modifier = Modifier.drawBehind { drawCursor(0, 1) })
				}
			}

			assertThat(awaitSnapshot()).isEqualTo(
				"ab\r\ncd\r\n${CSI}1F${CSI}2G$cursorShow".wrapWithAnsiSynchronizedUpdate(),
			)
		}

		// On exit, the cursor moves below the output and is hidden again.
		assertThat(rendering.finish().toString()).isEqualTo("${CSI}1E$cursorHide")
	}

	@Test fun nextFrameStartsFromTheCursorRow() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var text by mutableStateOf("cd")
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					Text("ab")
					Text(text, modifier = Modifier.drawBehind { drawCursor(0, 0) })
				}
			}
			awaitSnapshot()

			text = "ef"
			assertThat(awaitSnapshot()).isEqualTo(
				"$cursorHide${CSI}1F${clearLine}ab\r\n${clearLine}ef\r\n${CSI}1F$cursorShow"
					.wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun withoutCursorItStaysBelowTheOutput() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("ab")
			}

			assertThat(awaitSnapshot()).isEqualTo("ab\r\n".wrapWithAnsiSynchronizedUpdate())
		}
		assertThat(rendering.finish().toString()).isEqualTo("")
	}

	@Test fun cursorIsOnlyPositionedWhenItCannotBeHidden() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities(cursorVisibility = false))
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text("ab", modifier = Modifier.drawBehind { drawCursor(0, 1) })
			}

			assertThat(awaitSnapshot()).isEqualTo("ab\r\n${CSI}1F${CSI}2G".wrapWithAnsiSynchronizedUpdate())
		}
		assertThat(rendering.finish().toString()).isEqualTo("${CSI}1E")
	}
}
