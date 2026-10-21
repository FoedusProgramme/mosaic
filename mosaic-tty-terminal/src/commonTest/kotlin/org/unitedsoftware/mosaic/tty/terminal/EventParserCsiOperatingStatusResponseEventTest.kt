package org.unitedsoftware.mosaic.tty.terminal

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.unitedsoftware.mosaic.terminal.OperatingStatusResponseEvent
import org.unitedsoftware.mosaic.terminal.UnknownEvent

class EventParserCsiOperatingStatusResponseEventTest : BaseEventParserTest() {
	@Test fun ok() {
		testTerminal.write("${CSI}0n")
		assertThat(parser.next()).isEqualTo(OperatingStatusResponseEvent(ok = true))
	}

	@Test fun notOk() {
		testTerminal.write("${CSI}3n")
		assertThat(parser.next()).isEqualTo(OperatingStatusResponseEvent(ok = false))
	}

	@Test fun unknownP1() {
		testTerminal.write("${CSI}1n")
		assertThat(parser.next()).isEqualTo(UnknownEvent("1b5b316e".hexToByteArray()))
	}

	@Test fun nonDigitP1() {
		testTerminal.write("$CSI+n")
		assertThat(parser.next()).isEqualTo(UnknownEvent("1b5b2b6e".hexToByteArray()))
	}
}
