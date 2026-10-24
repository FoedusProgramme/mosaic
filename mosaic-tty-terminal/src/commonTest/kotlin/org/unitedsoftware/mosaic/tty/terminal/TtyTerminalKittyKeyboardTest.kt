package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.KeyboardEvent
import org.unitedsoftware.mosaic.terminal.KeyboardEvent.Companion.ModifierCtrl
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

	@Test fun modifyOtherKeysWithoutKittyKeyboard() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI>0q", reply = "${ESC}P>|iTerm2 3.7.3$ESC\\")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(capabilities.kittyKeyboard).isFalse()
			assertThat(setup).contains("$CSI>4;2m")

			// Shift+Enter is distinguishable from Enter all the same, and Ctrl+C is what it was.
			ptyWrite("${CSI}27;2;13~")
			assertThat(events.receive()).isEqualTo(KeyboardEvent(0x0D, modifiers = ModifierShift))
			ptyWrite("${CSI}27;5;99~")
			assertThat(events.receive()).isEqualTo(KeyboardEvent('c'.code, modifiers = ModifierCtrl))
		}

		assertThat(teardown).contains("$CSI>4m")
	}

	@Test fun noModifyOtherKeysForUnknownTerminals() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Its name, rather than the TERM_PROGRAM of whoever runs the test, tells what it supports.
		expect("$CSI>0q", reply = "${ESC}P>|SomeTerm 1.0$ESC\\")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("$CSI>4;2m")
		}

		assertThat(teardown).doesNotContain("$CSI>4m")
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
