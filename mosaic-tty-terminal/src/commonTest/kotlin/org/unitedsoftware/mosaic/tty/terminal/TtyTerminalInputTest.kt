package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.unitedsoftware.mosaic.terminal.KeyboardEvent

class TtyTerminalInputTest {
	/**
	 * Input methods commit text, and terminals without bracketed paste send pasted text, as many
	 * keys at once, which the program may only take after the terminal read them all.
	 */
	@Test fun keysWhichArriveAtOnceAreAllKept() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?1;2c")

		withTerminal {
			val text = "abcdefghij ".repeat(12) + "end, 你好世界"
			ptyWrite(text)
			// Let the terminal read every key before the program takes any.
			delay(500.milliseconds)

			val received = buildString {
				while (true) {
					val event = withTimeoutOrNull(1.seconds) { events.receive() } ?: break
					appendCodePoint((event as KeyboardEvent).codepoint)
				}
			}
			assertThat(received).isEqualTo(text)
		}
	}
}
