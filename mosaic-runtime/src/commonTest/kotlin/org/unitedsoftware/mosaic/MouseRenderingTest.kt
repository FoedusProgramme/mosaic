package org.unitedsoftware.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.terminal.Terminal
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Column
import org.unitedsoftware.mosaic.ui.Text

class MouseRenderingTest {
	@Test fun trackingIsEnabledWithTheFirstFrameAndDisabledOnFinish() = runTest {
		val size = Terminal.Size(10, 5)
		val rendering = AlternateScreenRendering(TestTerminal.Capabilities(), { size }, mouse = true)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("hi") }
			assertThat(awaitSnapshot()).contains(mouseTrackingEnable)
		}
		assertThat(rendering.finish().toString()).contains(mouseTrackingDisable)
	}

	@Test fun noTrackingByDefault() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent { Text("hi") }
			assertThat(awaitSnapshot()).doesNotContain(mouseTrackingEnable)
		}
		assertThat(rendering.finish().toString()).isEqualTo("\r\n")
	}

	@Test fun inlineOutputIsLocatedWithCursorPositionRequests() = runTest {
		val size = Terminal.Size(10, 20)
		val rendering = AnsiRendering(TestTerminal.Capabilities(), { size }, mouse = true)
		var lines by mutableStateOf(listOf("a", "b"))
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Column {
					for (line in lines) Text(line)
				}
			}
			assertThat(awaitSnapshot()).contains("a\r\nb$cursorPositionRequest")
			assertThat(rendering.surfaceRow(5)).isNull()

			// The cursor rests on the last row of the output, which the terminal says is row 11.
			rendering.onCursorPosition(11, 2)
			assertThat(rendering.surfaceRow(9)).isEqualTo(0)
			assertThat(rendering.surfaceRow(10)).isEqualTo(1)

			// The output is asked for again only once it may have moved.
			lines = listOf("a", "c")
			assertThat(awaitSnapshot()).doesNotContain(cursorPositionRequest)
			lines = listOf("a", "c", "d")
			assertThat(awaitSnapshot()).contains(cursorPositionRequest)
			// Row 20 is the third row of the output, which grew to the bottom of the screen.
			rendering.onCursorPosition(20, 1)
			assertThat(rendering.surfaceRow(17)).isEqualTo(0)
		}
	}
}
