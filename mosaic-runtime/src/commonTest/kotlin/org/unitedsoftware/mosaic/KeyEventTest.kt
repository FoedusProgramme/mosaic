package org.unitedsoftware.mosaic

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import org.unitedsoftware.mosaic.layout.KeyEvent
import org.unitedsoftware.mosaic.layout.onKeyEvent
import org.unitedsoftware.mosaic.modifier.Modifier
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.EventTypeRelease
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierAlt
import org.unitedsoftware.mosaic.testing.runMosaicTest
import org.unitedsoftware.mosaic.ui.Text

class KeyEventTest {
	@Test fun ascii() {
		assertThat(KeyboardEvent('a'.code).toKeyEventOrNull()).isEqualTo(KeyEvent("a"))
	}

	@Test fun cjk() {
		assertThat(KeyboardEvent(0x4E2D).toKeyEventOrNull()).isEqualTo(KeyEvent("中"))
	}

	@Test fun supplementaryPlane() {
		assertThat(KeyboardEvent(0x1F600).toKeyEventOrNull()).isEqualTo(KeyEvent("😀"))
	}

	@Test fun modifiersAreKept() {
		assertThat(KeyboardEvent(0xE9, modifiers = ModifierAlt).toKeyEventOrNull())
			.isEqualTo(KeyEvent("é", alt = true))
	}

	@Test fun unknownFunctionalKeyIsIgnored() {
		// Keypad 0 in the Kitty keyboard protocol.
		assertThat(KeyboardEvent(57399).toKeyEventOrNull()).isNull()
	}

	@Test fun controlCharacterIsIgnored() {
		assertThat(KeyboardEvent(0x01).toKeyEventOrNull()).isNull()
		assertThat(KeyboardEvent(0x85).toKeyEventOrNull()).isNull()
	}

	@Test fun invalidCodepointIsIgnored() {
		assertThat(KeyboardEvent(0xD800).toKeyEventOrNull()).isNull()
		assertThat(KeyboardEvent(0x110000).toKeyEventOrNull()).isNull()
	}

	@Test fun releaseIsIgnored() {
		assertThat(KeyboardEvent('a'.code, eventType = EventTypeRelease).toKeyEventOrNull()).isNull()
	}

	@Test fun nonAsciiInputReachesComposition() = runTest {
		runMosaicTest {
			setContent {
				var typed by remember { mutableStateOf("") }
				Text(
					"typed: $typed",
					modifier = Modifier.onKeyEvent {
						typed += it.key
						true
					},
				)
			}
			assertThat(awaitSnapshot()).isEqualTo("typed:")

			sendKeyEvent(KeyboardEvent(0x4E2D))
			sendKeyEvent(KeyboardEvent(0x6587))
			assertThat(awaitSnapshot()).isEqualTo("typed: 中文")
		}
	}
}
