package org.unitedsoftware.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.layout.onPasteEvent
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.BracketedPasteEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
import org.unitedsoftware.mosaic.testing.TestMosaic
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Text

class PasteEventTest {
	private fun TestMosaic<*>.paste(vararg keys: KeyboardEvent) {
		sendEvent(BracketedPasteEvent(start = true))
		keys.forEach(::sendEvent)
		sendEvent(BracketedPasteEvent(start = false))
	}

	@Test fun pasteIsDeliveredAsText() = runTest {
		runMosaicTest {
			setContent {
				var log by remember { mutableStateOf("") }
				Text(
					"log:$log",
					modifier = Modifier
						.onPasteEvent {
							log += "[paste ${it.text.replace("\n", "\\n").replace("\t", "\\t")}]"
							true
						}
						.onKeyEvent {
							log += "[key ${it.key}]"
							true
						},
				)
			}
			assertThat(awaitSnapshot()).isEqualTo("log:")

			paste(
				KeyboardEvent('a'.code),
				KeyboardEvent(0x0D),
				KeyboardEvent(0x09),
				KeyboardEvent(0x4E2D),
			)
			assertThat(awaitSnapshot()).isEqualTo("log:[paste a\\n\\t中]")
		}
	}

	@Test fun unhandledPasteIsDeliveredAsKeys() = runTest {
		runMosaicTest {
			setContent {
				var log by remember { mutableStateOf("") }
				Text(
					"log:$log",
					modifier = Modifier.onKeyEvent {
						log += "[${it.key}]"
						true
					},
				)
			}
			assertThat(awaitSnapshot()).isEqualTo("log:")

			paste(KeyboardEvent('a'.code), KeyboardEvent(0x0D), KeyboardEvent('b'.code))
			assertThat(awaitSnapshot()).isEqualTo("log:[a][Enter][b]")
		}
	}

	@Test fun controlCharactersInPasteAreNotText() = runTest {
		runMosaicTest {
			setContent {
				var log by remember { mutableStateOf("") }
				Text(
					"log:$log",
					modifier = Modifier.onPasteEvent {
						log += it.text
						true
					},
				)
			}
			assertThat(awaitSnapshot()).isEqualTo("log:")

			// Ctrl+C inside of a paste must not be treated as an interrupt.
			paste(KeyboardEvent('x'.code), KeyboardEvent('c'.code, modifiers = ModifierCtrl), KeyboardEvent('y'.code))
			assertThat(awaitSnapshot()).isEqualTo("log:xy")
		}
	}
}
