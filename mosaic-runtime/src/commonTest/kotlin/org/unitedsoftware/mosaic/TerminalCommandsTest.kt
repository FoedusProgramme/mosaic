package org.unitedsoftware.mosaic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.testing.TestTerminal
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.text.buildAnnotatedString
import org.unitedsoftware.mosaic.text.withLink
import org.unitedsoftware.mosaic.ui.Box
import org.unitedsoftware.mosaic.ui.Text

class TerminalCommandsTest {
	@Test fun windowTitleIsSetOnceAndRestoredOnFinish() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var count by mutableIntStateOf(0)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				WindowTitle("Title")
				Text("count $count")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"$windowTitlePush${OSC}2;Title${ST}count 0\r\n".wrapWithAnsiSynchronizedUpdate(),
			)

			count = 1
			// The unchanged title is not written again.
			assertThat(awaitSnapshot()).isEqualTo(
				"${CSI}1F${clearLine}count 1\r\n".wrapWithAnsiSynchronizedUpdate(),
			)
		}
		assertThat(rendering.finish().toString()).isEqualTo(windowTitlePop)
	}

	@Test fun pointerShapeIsPushedChangedAndPopped() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var shape by mutableStateOf<PointerShape?>(PointerShape.Wait)
		var count by mutableIntStateOf(0)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				shape?.let { WindowPointerShape(it) }
				Text("count $count")
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"${OSC}22;>wait${ST}count 0\r\n".wrapWithAnsiSynchronizedUpdate(),
			)

			shape = PointerShape.Text
			count = 1
			assertThat(awaitSnapshot()).isEqualTo(
				"${OSC}22;=text$ST${CSI}1F${clearLine}count 1\r\n".wrapWithAnsiSynchronizedUpdate(),
			)

			shape = null
			count = 2
			assertThat(awaitSnapshot()).isEqualTo(
				"$pointerShapePop${CSI}1F${clearLine}count 2\r\n".wrapWithAnsiSynchronizedUpdate(),
			)
		}
		assertThat(rendering.finish().toString()).isEqualTo("")
	}

	@Test fun pointerShapeNeedsSupport() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities(kittyPointerShape = false))
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				WindowPointerShape(PointerShape.Wait)
				Text("hi")
			}
			assertThat(awaitSnapshot()).isEqualTo("hi\r\n".wrapWithAnsiSynchronizedUpdate())
		}
		assertThat(rendering.finish().toString()).isEqualTo("")
	}

	@Test fun innermostWindowTitleWins() = runTest {
		@Composable
		fun Nested() {
			WindowTitle("Inner")
		}

		val rendering = AnsiRendering(TestTerminal.Capabilities())
		var showInner by mutableStateOf(true)
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				WindowTitle("Outer")
				Box {
					if (showInner) Nested()
					Text("hi")
				}
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"$windowTitlePush${OSC}2;Inner${ST}hi\r\n".wrapWithAnsiSynchronizedUpdate(),
			)

			showInner = false
			assertThat(awaitSnapshot()).isEqualTo("${OSC}2;Outer$ST".wrapWithAnsiSynchronizedUpdate())
		}
	}

	@Test fun clipboard() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				val clipboard = LocalClipboard.current
				Text(
					"copy",
					modifier = Modifier.onKeyEvent {
						clipboard.setText("中文")
						true
					},
				)
			}
			awaitSnapshot()

			sendKeyEvent(KeyboardEvent('c'.code))
			// "中文" encoded as UTF-8 and then Base64.
			assertThat(awaitSnapshot()).isEqualTo("${OSC}52;c;5Lit5paH$ST".wrapWithAnsiSynchronizedUpdate())
		}
	}

	@Test fun notifications() = runTest {
		val kitty = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(kitty)) {
			setContent {
				val notifier = LocalNotifier.current
				Text(
					"notify",
					modifier = Modifier.onKeyEvent {
						notifier.notify("Done", "Build passed")
						true
					},
				)
			}
			awaitSnapshot()

			sendKeyEvent(KeyboardEvent('n'.code))
			assertThat(awaitSnapshot()).isEqualTo(
				"${OSC}99;i=1:d=0;Done$ST${OSC}99;i=1:p=body;Build passed$ST".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}

	@Test fun notificationsWithoutKittyUseOsc9() = runTest {
		val capabilities = TestTerminal.Capabilities(kittyNotifications = false)
		val rendering = AnsiRendering(capabilities)
		runMosaicTest(RenderingSnapshots(rendering), capabilities = capabilities) {
			setContent {
				val notifier = LocalNotifier.current
				Text(
					"notify",
					modifier = Modifier.onKeyEvent {
						notifier.notify("Done", "Build\npassed")
						true
					},
				)
			}
			awaitSnapshot()

			sendKeyEvent(KeyboardEvent('n'.code))
			// Control characters would end the sequence, so they are dropped.
			assertThat(awaitSnapshot()).isEqualTo("${OSC}9;Done: Buildpassed$ST".wrapWithAnsiSynchronizedUpdate())
		}
	}

	@Test fun hyperlinks() = runTest {
		val rendering = AnsiRendering(TestTerminal.Capabilities())
		runMosaicTest(RenderingSnapshots(rendering)) {
			setContent {
				Text(
					buildAnnotatedString {
						append("see ")
						withLink("https://example.com") { append("docs") }
						append("!")
					},
				)
			}
			assertThat(awaitSnapshot()).isEqualTo(
				"see ${OSC}8;;https://example.com${ST}docs${OSC}8;;$ST!\r\n".wrapWithAnsiSynchronizedUpdate(),
			)
		}
	}
}
