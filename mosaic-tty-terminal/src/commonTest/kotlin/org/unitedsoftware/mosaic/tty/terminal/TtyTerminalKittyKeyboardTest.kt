package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierShift

class TtyTerminalKittyKeyboardTest {
	@Test fun noReply() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.kittyKeyboard).isFalse()
			assertThat(setup).doesNotContain("$CSI>1u")
		}

		assertThat(teardown).doesNotContain("$CSI<u")
	}

	@Test fun reply() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?u", reply = "$CSI?0u")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.kittyKeyboard).isTrue()
			assertThat(setup).contains("$CSI>1u")

			// Shift+Enter is now distinguishable from Enter.
			ptyWrite("${CSI}13;2u")
			assertThat(events.receive()).isEqualTo(KeyboardEvent(0x0D, modifiers = ModifierShift))
		}

		// The pushed flags are popped, restoring the previous ones.
		assertThat(teardown).contains("$CSI<u")
	}
}
