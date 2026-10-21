package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.BracketedPasteEvent

class TtyTerminalBracketedPasteTest {
	@Test fun noReply() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			// Enabled anyway since many terminals support it without reporting modes.
			assertThat(setup).contains("$CSI?2004h")
		}

		assertThat(teardown).contains("$CSI?2004l")
	}

	@Test fun replyNotRecognized() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("$CSI?2004\$p", reply = "$CSI?2004;0\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("$CSI?2004h")
		}

		assertThat(teardown).doesNotContain("$CSI?2004l")
	}

	@Test fun vt100() = terminalTest {
		// Terminals identifying as a VT100, like tmux, are not queried for modes.
		expect("${CSI}0c", reply = "$CSI?1;2c")

		val teardown = withTerminal { setup ->
			assertThat(setup).contains("$CSI?2004h")
		}

		assertThat(teardown).contains("$CSI?2004l")
	}

	@Test fun replySet() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Bracketed paste is already enabled.
		expect("$CSI?2004\$p", reply = "$CSI?2004;1\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).doesNotContain("$CSI?2004h")
		}

		// Left enabled since it was enabled before.
		assertThat(teardown).doesNotContain("$CSI?2004l")
	}

	@Test fun replyReset() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		// Bracketed paste is supported but not enabled.
		expect("$CSI?2004\$p", reply = "$CSI?2004;2\$y")
		expect("${CSI}5n", reply = "${CSI}0n")

		val teardown = withTerminal { setup ->
			assertThat(setup).contains("$CSI?2004h")

			ptyWrite("${CSI}200~hi${CSI}201~")
			assertThat(events.receive()).isEqualTo(BracketedPasteEvent(start = true))
		}

		// Disabled again since it was disabled before.
		assertThat(teardown).contains("$CSI?2004l")
	}
}
