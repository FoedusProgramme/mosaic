package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class TtyTerminalSixelTest {
	@Test fun withoutAttribute() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		withTerminal {
			assertThat(capabilities.sixelGraphics).isFalse()
		}
	}

	@Test fun attributeFour() = terminalTest {
		expect("${CSI}0c", reply = "$CSI?62;4;22c")
		expect("${CSI}5n", reply = "${CSI}0n")

		withTerminal {
			assertThat(capabilities.sixelGraphics).isTrue()
		}
	}
}
